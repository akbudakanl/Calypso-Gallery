package org.fossify.gallery

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import org.fossify.commons.FossifyApp
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.gallery.activities.AppUnlockActivity
import org.fossify.gallery.activities.SplashActivity
import org.fossify.gallery.activities.VaultLockActivity
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.MediaIndexCoordinator
import org.fossify.gallery.helpers.RandomOrderManager
import org.fossify.gallery.helpers.ShareMetadataSanitizer
import org.fossify.gallery.helpers.SideloadCheck
import org.fossify.gallery.helpers.VaultSessionManager
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.fossify.gallery.helpers.processPendingWipeJournal
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry
import org.fossify.gallery.helpers.operations.OperationJournal

class App : FossifyApp() {

    override val isAppLockFeatureAvailable = true
    private var startedActivities = 0
    private val pendingWipeJournalScheduled = AtomicBoolean(false)
    private val trackedActivities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())
    private val operationKeepScreenOnActivities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    companion object {
        private const val PRIVACY_CURTAIN_TAG = "vault_privacy_curtain"

        /**
         * Elapsed-realtime stamp taken when the app last went to background.
         * -1 = cold start while protected, 0 = freshly unlocked.
         */
        @Volatile
        private var appBackgroundElapsedMs = -1L

        /** True while at least one of our activities is started (app in foreground). */
        @Volatile
        var isAppInForeground: Boolean = false
            private set

        /** True while our own lock gate is drawing, so Commons must not open a second one. */
        @Volatile
        var appLockGateLaunched = false
            private set

        fun shouldSuppressAppUnlockLaunch(): Boolean = appLockGateLaunched

        fun markAppUnlocked() {
            appLockGateLaunched = false
            appBackgroundElapsedMs = 0L
        }

        fun markAppLockGateClosed() {
            appLockGateLaunched = false
        }
    }

    /**
     * True once the app-password grace window has elapsed (or the app was cold-started locked).
     * We keep Commons' 30s tolerance untouched; this only decides when the privacy curtain and
     * our zero-transition lock gate must be in place *before* any content can be drawn.
     */
    private fun isAppPasswordLockExpired(): Boolean {
        if (!config.isAppPasswordProtectionOn) return false
        if (appLockManager.isLocked()) return true
        if (appBackgroundElapsedMs == 0L) return false // just unlocked, grace window running
        if (appBackgroundElapsedMs < 0L) return true // cold start while protected
        return SystemClock.elapsedRealtime() - appBackgroundElapsedMs >= config.unlockTimeoutDurationMs
    }

    override fun onCreate() {
        super.onCreate()
        // ADR 0003: durable operation journal must be available before any file operation starts.
        OperationJournal.init(this)
        ShareMetadataSanitizer.clearTemp(this)
        val mainHandler = runCatching { Handler(Looper.getMainLooper()) }.getOrNull()
        ForegroundOperationRegistry.onStateChanged = { active ->
            mainHandler?.post { syncKeepScreenOnForTrackedActivities(active) }
        }
        // Neutralize Commons' fake-app warning before any activity can query the cached verdict.
        SideloadCheck.disable(this)
        // Commons' legacy launcher-icon migration in appLaunched() builds alias ComponentNames
        // by stripping ".debug" from the application id; with our package identity that always
        // targets a component that does not exist and throws on first launch. The Appearance
        // screen handles icon switching itself with the real package name, so mark both legacy
        // paths as already handled before MainActivity runs appLaunched().
        val appearanceConfig = config
        if (appearanceConfig.appRunCount == 0) {
            appearanceConfig.appRunCount = 1
        }
        appearanceConfig.wasOrangeIconChecked = true
        // Apply the fork's appearance palette before any activity reads theme colors.
        val palette = AppearanceManager.apply(this, appearanceConfig)
        appearanceConfig.lastIconColor = appearanceConfig.appIconColor
        AppearanceManager.applyLauncherIcon(this, palette.appIcon)
        installCodeLevelNetworkBlocker()

        runCatching {
            val screenFilter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(
                this,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent?) {
                        when (intent?.action) {
                            Intent.ACTION_SCREEN_OFF -> {
                                VaultSessionManager.onScreenOff(this@App)
                                if (config.isAppPasswordProtectionOn &&
                                    config.appLockMode == Config.APP_LOCK_MODE_EVERY_ENTRY
                                ) {
                                    appLockManager.lock()
                                }
                                syncPrivacyCurtainForTrackedActivities()
                            }
                            Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                                if (isAppInForeground) {
                                    MediaIndexCoordinator.notifyForegroundOrScreenOn()
                                }
                            }
                        }
                    }
                },
                screenFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                trackedActivities.add(activity)
                if (config.appearanceMode == Config.APPEARANCE_SYSTEM) {
                    AppearanceManager.apply(this@App, config)
                }
            }
            override fun onActivityStarted(activity: Activity) {
                trackedActivities.add(activity)
                startedActivities++
                isAppInForeground = true
                ForegroundOperationRegistry.onAppVisibilityChanged(this@App)
                if (startedActivities == 1) {
                    // Back in the foreground: expire session only if the 30 minute background deadline passed.
                    VaultSessionManager.onAppForegrounded(this@App)
                    runCatching {
                        org.fossify.gallery.fragments.VideoFragment.pruneExpiredFrameCache()
                    }
                    RandomOrderManager.onAppForegrounded()
                    MediaIndexCoordinator.notifyForegroundOrScreenOn()
                    if (pendingWipeJournalScheduled.compareAndSet(false, true)) {
                        runCatching {
                            org.fossify.gallery.helpers.CryptoManager.processPendingWipeJournal(this@App)
                        }
                    }
                }
                syncPrivacyCurtain(activity)
                syncKeepScreenOn(activity, ForegroundOperationRegistry.isActive)
                maybeLaunchVaultLockScreen(activity)
                maybeLaunchAppUnlockScreen(activity)
            }
            override fun onActivityResumed(activity: Activity) {
                if (config.appearanceMode == Config.APPEARANCE_SYSTEM) {
                    AppearanceManager.apply(this@App, config)
                }
                syncPrivacyCurtain(activity)
                syncKeepScreenOn(activity, ForegroundOperationRegistry.isActive)
                maybeLaunchVaultLockScreen(activity)
                maybeLaunchAppUnlockScreen(activity)
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities <= 0) {
                    startedActivities = 0
                    isAppInForeground = false
                    ForegroundOperationRegistry.onAppVisibilityChanged(this@App)
                    if (!activity.isChangingConfigurations) {
                        if (config.isAppPasswordProtectionOn) {
                            appBackgroundElapsedMs = SystemClock.elapsedRealtime()
                            if (config.appLockMode == Config.APP_LOCK_MODE_EVERY_ENTRY) {
                                appLockManager.lock()
                            }
                        }
                        // Immediately wipe RAM keys (unless a foreground operation is running) while keeping
                        // the UI session resumable behind VaultLockActivity for VAULT_AUTO_LOCK_TIMEOUT_MS.
                        VaultSessionManager.onAppBackgrounded(this@App)
                        RandomOrderManager.onAppBackgrounded()
                        syncPrivacyCurtainForTrackedActivities()
                    }
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                trackedActivities.remove(activity)
                operationKeepScreenOnActivities.remove(activity)
            }
        })
    }

    private fun syncKeepScreenOnForTrackedActivities(active: Boolean) {
        for (activity in trackedActivities.toList()) {
            syncKeepScreenOn(activity, active)
        }
    }

    private fun syncKeepScreenOn(activity: Activity, active: Boolean) {
        if (activity.isFinishing || activity.isDestroyed) {
            operationKeepScreenOnActivities.remove(activity)
            return
        }
        val window = activity.window ?: return
        if (active) {
            if ((window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) == 0) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                operationKeepScreenOnActivities.add(activity)
            }
        } else if (operationKeepScreenOnActivities.remove(activity)) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun syncPrivacyCurtainForTrackedActivities() {
        for (activity in trackedActivities.toList()) {
            syncPrivacyCurtain(activity)
        }
    }

    /**
     * Synchronously hides the underlying activity content and overlays an opaque curtain whenever
     * vault re-authentication is pending so no frame of vault media leaks before VaultLockActivity draws.
     */
    private fun syncPrivacyCurtain(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (activity is VaultLockActivity || activity is AppUnlockActivity || activity is SplashActivity) return

        val decorView = activity.window?.decorView as? ViewGroup ?: return
        val contentView = activity.findViewById<View>(android.R.id.content)
        val existingCurtain = decorView.findViewWithTag<View>(PRIVACY_CURTAIN_TAG)

        if (VaultSessionManager.isReauthPending() || isAppPasswordLockExpired()) {
            if (contentView != null && contentView.visibility != View.INVISIBLE) {
                contentView.visibility = View.INVISIBLE
            }
            if (existingCurtain == null) {
                val curtain = View(activity).apply {
                    tag = PRIVACY_CURTAIN_TAG
                    setBackgroundColor(activity.getProperBackgroundColor())
                    isClickable = true
                    isFocusable = true
                }
                decorView.addView(
                    curtain,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        } else {
            if (existingCurtain != null) {
                decorView.removeView(existingCurtain)
            }
            if (contentView != null && contentView.visibility != View.VISIBLE) {
                contentView.visibility = View.VISIBLE
            }
        }
    }

    private fun maybeLaunchVaultLockScreen(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (VaultSessionManager.shouldShowLockScreen(
                isLockScreenActivity = activity is VaultLockActivity,
                isSplashActivity = activity is SplashActivity
            )
        ) {
            VaultSessionManager.markLockScreenLaunching()
            val intent = Intent(activity, VaultLockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            activity.startActivity(intent)
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
    }

    /**
     * Raises the app-password gate in `onActivityStarted`, i.e. before the activity's `onResume`
     * can draw anything. The 30-second grace period is untouched: we only act once it has elapsed.
     * The vault gate wins when both are pending, so the two lock screens never stack.
     */
    private fun maybeLaunchAppUnlockScreen(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (activity is AppUnlockActivity || activity is VaultLockActivity || activity is SplashActivity) return
        if (VaultSessionManager.isReauthPending()) return
        if (appLockGateLaunched || !isAppPasswordLockExpired()) return

        appLockGateLaunched = true
        val intent = Intent(activity, AppUnlockActivity::class.java)
        activity.startActivity(intent)
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }

    /**
     * Defense-in-depth code-level network blocker complementing AndroidManifest INTERNET removal
     * and NetworkSecurityConfig lockdown. Blocks standard JVM/Android HTTP(S) and socket proxy paths.
     */
    private fun installCodeLevelNetworkBlocker() {
        val blockedSchemes = setOf("http", "https", "ftp", "ws", "wss", "gopher")

        try {
            URL.setURLStreamHandlerFactory { protocol ->
                if (protocol != null && protocol.lowercase() in blockedSchemes) {
                    object : URLStreamHandler() {
                        override fun openConnection(u: URL?): URLConnection {
                            throw SecurityException("Network access is permanently disabled in Calypso Gallery")
                        }

                        override fun openConnection(u: URL?, p: Proxy?): URLConnection {
                            throw SecurityException("Network access is permanently disabled in Calypso Gallery")
                        }
                    }
                } else {
                    null
                }
            }
        } catch (_: Throwable) {}

        try {
            ProxySelector.setDefault(object : ProxySelector() {
                override fun select(uri: URI?): MutableList<Proxy> {
                    val scheme = uri?.scheme?.lowercase()
                    if (scheme in blockedSchemes || scheme == "socket") {
                        throw SecurityException("Network access is permanently disabled in Calypso Gallery")
                    }
                    return mutableListOf(Proxy.NO_PROXY)
                }

                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
            })
        } catch (_: Throwable) {}
    }
}

package org.fossify.gallery.activities

import android.app.Activity
import android.app.Dialog
import android.app.assist.AssistContent
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.provider.MediaStore.Video
import android.view.WindowManager
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isPiePlus
import org.fossify.commons.helpers.isRPlus
import org.fossify.gallery.App
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.StoragePermissionRequiredDialog
import org.fossify.gallery.extensions.addPathToDB
import org.fossify.gallery.extensions.applyVaultScreenProtection
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.isIncludedFolderDisallowed
import org.fossify.gallery.extensions.isPathHiddenOrEncrypted
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.ScreenshotFolderObserver
import org.fossify.gallery.helpers.VaultSessionManager
import org.fossify.gallery.helpers.getPermissionsToRequest

open class SimpleActivity : BaseSimpleActivity() {

    private var dialog: Dialog? = null
    private var pendingSdk30DeleteCallback: ((Boolean) -> Unit)? = null
    private val sdk30DeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val callback = pendingSdk30DeleteCallback
        pendingSdk30DeleteCallback = null
        callback?.invoke(result.resultCode == Activity.RESULT_OK)
    }
    private val mediaObserverHandler = Handler(Looper.getMainLooper())
    private val pendingObserverFolders = LinkedHashSet<String>()
    private var hasPendingWildcardObserverChange = false
    private var screenshotFolderObserver: ScreenshotFolderObserver? = null

    private val dispatchObserverChangeRunnable = Runnable {
        val folders: Set<String>
        val isWildcard: Boolean
        synchronized(pendingObserverFolders) {
            folders = LinkedHashSet(pendingObserverFolders)
            pendingObserverFolders.clear()
            isWildcard = hasPendingWildcardObserverChange
            hasPendingWildcardObserverChange = false
        }
        if (!isDestroyed && !isFinishing && (folders.isNotEmpty() || isWildcard)) {
            onMediaStoreContentChanged(folders, isWildcard)
        }
    }

    override fun onStart() {
        super.onStart()
        refreshScreenshotFolderWatchers()
    }

    override fun onResume() {
        super.onResume()
        val isVaultActive = CryptoManager.isVaultUnlocked || VaultSessionManager.isReauthPending()
        if (isVaultActive) {
            applyVaultScreenProtection(true)
        } else {
            applyVaultScreenProtection(false)
        }
    }

    override fun onStop() {
        screenshotFolderObserver?.stop()
        super.onStop()
    }

    protected open fun isVaultAssistProtectionActive(): Boolean {
        return CryptoManager.isVaultUnlocked || VaultSessionManager.isReauthPending()
    }

    override fun onProvideAssistData(data: Bundle?) {
        if (isVaultAssistProtectionActive()) {
            data?.clear()
            return
        }
        super.onProvideAssistData(data)
    }

    override fun onProvideAssistContent(outContent: AssistContent?) {
        if (isVaultAssistProtectionActive()) {
            outContent?.apply {
                clipData = null
                intent = Intent()
                structuredData = null
                webUri = null
            }
            return
        }
        super.onProvideAssistContent(outContent)
    }

    override fun onDestroy() {
        val pendingCallback = pendingSdk30DeleteCallback
        pendingSdk30DeleteCallback = null
        pendingCallback?.invoke(false)
        dialog?.dismiss()
        dialog = null
        mediaObserverHandler.removeCallbacks(dispatchObserverChangeRunnable)
        screenshotFolderObserver?.stop()
        screenshotFolderObserver = null
        super.onDestroy()
    }

    fun launchSdk30DeleteRequest(uris: List<Uri>, callback: (Boolean) -> Unit) {
        if (uris.isEmpty()) {
            callback(true)
            return
        }
        if (!isRPlus() || isFinishing || isDestroyed) {
            callback(false)
            return
        }
        runOnUiThread {
            if (!isRPlus() || isFinishing || isDestroyed) {
                callback(false)
                return@runOnUiThread
            }
            try {
                hideKeyboard()
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                pendingSdk30DeleteCallback = callback
                sdk30DeleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } catch (e: Exception) {
                pendingSdk30DeleteCallback = null
                showErrorToast(e)
                callback(false)
            }
        }
    }

    protected open fun shouldObserveScreenshotFolders(): Boolean = false

    protected open fun getExtraScreenshotWatchPaths(): Collection<String> = emptyList()

    protected fun refreshScreenshotFolderWatchers() {
        if (!shouldObserveScreenshotFolders() || isDestroyed || isFinishing) {
            return
        }
        if (!hasAllPermissions(getPermissionsToRequest())) {
            return
        }
        val watcher = screenshotFolderObserver ?: ScreenshotFolderObserver { changedFolders ->
            if (!isDestroyed && !isFinishing && changedFolders.isNotEmpty()) {
                onMediaStoreContentChanged(changedFolders, isWildcardChange = false)
            }
        }.also { screenshotFolderObserver = it }
        watcher.start(getExtraScreenshotWatchPaths())
    }

    protected fun findRecentlyChangedMediaFolders(lookbackSeconds: Long = 15L): Set<String> {
        val cutoffSec = (System.currentTimeMillis() / 1000L) - lookbackSeconds
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val selection = buildString {
            append(
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN " +
                    "(${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
            )
            append(" AND ${MediaStore.MediaColumns.DATE_MODIFIED} >= ?")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                append(" AND ${MediaStore.MediaColumns.IS_PENDING} = 0")
            }
        }
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC, ${MediaStore.MediaColumns._ID} DESC"
        val folders = LinkedHashSet<String>()
        return try {
            contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                arrayOf(cutoffSec.toString()),
                sortOrder
            )?.use { cursor ->
                val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (dataIdx < 0) return@use
                var seen = 0
                while (cursor.moveToNext() && seen < 5) {
                    seen++
                    val path = cursor.getString(dataIdx) ?: continue
                    if (path.isBlank()) continue
                    val name = path.getFilenameFromPath()
                    if (name.startsWith(".") || isPathHiddenOrEncrypted(path)) continue
                    val parent = path.getParentPath().trimEnd('/')
                    if (parent.isNotEmpty()) {
                        folders.add(parent)
                    }
                }
            }
            folders
        } catch (_: Exception) {
            emptySet()
        }
    }

    protected open fun onMediaStoreContentChanged(changedFolderPaths: Set<String>, isWildcardChange: Boolean) {}

    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            ensureBackgroundThread {
                var insertedPath: String? = null
                val resolvedPath = if (uri != null) getRealPathFromURI(uri) else null
                if (resolvedPath != null) {
                    val fileName = resolvedPath.getFilenameFromPath()
                    if (!fileName.startsWith(".") && !isPathHiddenOrEncrypted(resolvedPath)) {
                        val parentPath = resolvedPath.getParentPath().trimEnd('/')
                        if (parentPath.isNotEmpty()) {
                            synchronized(pendingObserverFolders) {
                                pendingObserverFolders.add(parentPath)
                            }
                            insertedPath = resolvedPath
                        }
                    } else if (fileName.startsWith(".")) {
                        val recentFolders = findRecentlyChangedMediaFolders()
                        synchronized(pendingObserverFolders) {
                            if (recentFolders.isNotEmpty()) {
                                pendingObserverFolders.addAll(recentFolders)
                            } else {
                                hasPendingWildcardObserverChange = true
                            }
                        }
                    }
                } else {
                    val recentFolders = findRecentlyChangedMediaFolders()
                    synchronized(pendingObserverFolders) {
                        if (recentFolders.isNotEmpty()) {
                            pendingObserverFolders.addAll(recentFolders)
                        } else {
                            hasPendingWildcardObserverChange = true
                        }
                    }
                }

                mediaObserverHandler.removeCallbacks(dispatchObserverChangeRunnable)
                mediaObserverHandler.postDelayed(dispatchObserverChangeRunnable, 100L)
                insertedPath?.let { addPathToDB(it) }
            }
        }
    }

    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher_red,
        R.mipmap.ic_launcher_pink,
        R.mipmap.ic_launcher_purple,
        R.mipmap.ic_launcher_deep_purple,
        R.mipmap.ic_launcher_indigo,
        R.mipmap.ic_launcher_blue,
        R.mipmap.ic_launcher_light_blue,
        R.mipmap.ic_launcher_cyan,
        R.mipmap.ic_launcher_teal,
        R.mipmap.ic_launcher_green,
        R.mipmap.ic_launcher_light_green,
        R.mipmap.ic_launcher_lime,
        R.mipmap.ic_launcher_yellow,
        R.mipmap.ic_launcher_amber,
        R.mipmap.ic_launcher_orange,
        R.mipmap.ic_launcher_deep_orange,
        R.mipmap.ic_launcher_brown,
        R.mipmap.ic_launcher_blue_grey,
        R.mipmap.ic_launcher_grey_black
    )

    override fun getAppLauncherName() = getString(R.string.app_name)

    override fun getRepositoryName() = "Gallery"

    protected fun checkNotchSupport() {
        if (isPiePlus()) {
            val cutoutMode = when {
                config.showNotch -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                else -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }

            window.attributes.layoutInDisplayCutoutMode = cutoutMode
            if (config.showNotch) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            }
        }
    }

    protected fun registerFileUpdateListener() {
        try {
            contentResolver.registerContentObserver(Images.Media.EXTERNAL_CONTENT_URI, true, observer)
            contentResolver.registerContentObserver(Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        } catch (ignored: Exception) {
        }
    }

    protected fun unregisterFileUpdateListener() {
        try {
            contentResolver.unregisterContentObserver(observer)
        } catch (ignored: Exception) {
        }
    }

    protected fun showAddIncludedFolderDialog(callback: () -> Unit) {
        FilePickerDialog(this, config.lastFilepickerPath, false, config.shouldShowHidden, false, true) {
            if (isIncludedFolderDisallowed(it)) {
                toast(R.string.cannot_include_restricted_or_vault_folder)
                return@FilePickerDialog
            }
            config.lastFilepickerPath = it
            config.addIncludedFolder(it)
            callback()
            ensureBackgroundThread {
                scanPathRecursively(it)
            }
        }
    }

    protected fun requestMediaPermissions(enableRationale: Boolean = false, onGranted: () -> Unit) {
        when {
            hasAllPermissions(getPermissionsToRequest()) -> {
                dialog?.dismiss()
                dialog = null
                onGranted()
            }
            isAskingPermissions -> return
            config.showPermissionRationale -> {
                if (enableRationale) {
                    showPermissionRationale()
                } else {
                    onPermissionDenied()
                }
            }

            else -> {
                handlePartialMediaPermissions(getPermissionsToRequest(), force = true) { granted ->
                    if (granted) {
                        onGranted()
                    } else {
                        config.showPermissionRationale = true
                        showPermissionRationale()
                    }
                }
            }
        }
    }

    private fun showPermissionRationale() {
        dialog?.dismiss()
        StoragePermissionRequiredDialog(
            activity = this,
            onOkay = ::openDeviceSettings,
            onCancel = ::onPermissionDenied
        ) { dialog ->
            this.dialog = dialog
        }
    }

    private fun onPermissionDenied() {
        toast(org.fossify.commons.R.string.no_storage_permissions)
        finish()
    }

    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        if (intent.component?.className == "org.fossify.commons.activities.AppLockActivity") {
            // App.maybeLaunchAppUnlockScreen() already raised the gate before onResume drew content.
            // Commons still tries to open its own lock activity at the end of onResume; suppress it
            // so the user never sees a second prompt fade in over the first one.
            if (App.shouldSuppressAppUnlockLaunch()) return
            val replacement = Intent(intent).setClass(this, AppUnlockActivity::class.java)
            super.startActivityForResult(replacement, requestCode, options)
            return
        }
        super.startActivityForResult(intent, requestCode, options)
    }
}

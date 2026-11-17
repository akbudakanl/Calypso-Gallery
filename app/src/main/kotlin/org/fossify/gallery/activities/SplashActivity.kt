package org.fossify.gallery.activities

import android.content.Intent
import org.fossify.commons.activities.BaseSplashActivity
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.restoreFavoritesFromBackup
import org.fossify.gallery.helpers.VaultViewPolicy

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        ensureBackgroundThread {
            restoreFavoritesFromBackup()
        }
        launchActivity()
    }

    private fun launchActivity() {
        config.showAll = VaultViewPolicy.shouldShowFilesOnAppLaunch(config.homeScreenViewOnLaunch)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

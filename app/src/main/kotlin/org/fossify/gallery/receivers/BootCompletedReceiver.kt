package org.fossify.gallery.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.extensions.updateDirectoryPath
import org.fossify.gallery.helpers.MediaFetcher

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON") {
            return
        }
        val pendingResult = goAsync()
        try {
            ensureBackgroundThread {
                try {
                    MediaFetcher(context).getFoldersToScan().forEach {
                        context.updateDirectoryPath(it)
                    }
                } catch (_: Throwable) {
                } finally {
                    runCatching { pendingResult.finish() }
                }
            }
        } catch (_: Throwable) {
            runCatching { pendingResult.finish() }
        }
    }
}

package org.fossify.gallery.helpers

import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.Locale

/**
 * Watches screenshot directories at the kernel (inotify) level so new screenshots are detected
 * within ~80 ms while the app is open, without waiting seconds for Android MediaStore indexing.
 */
class ScreenshotFolderObserver(
    private val onFoldersChanged: (Set<String>) -> Unit,
) {
    companion object {
        private const val WATCH_MASK =
            FileObserver.CLOSE_WRITE or
                FileObserver.MOVED_TO or
                FileObserver.DELETE or
                FileObserver.MOVED_FROM
        private const val DEBOUNCE_MS = 80L

        private val WATCHED_MEDIA_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "webp", "heic", "heif", "avif", "bmp", "gif",
            "mp4", "mkv", "webm", "mov", "3gp", "m4v", "avi", "dng", "svg",
        )

        private val BLOCKED_TEMP_SUFFIXES = arrayOf(".tmp", ".part", ".crdownload", ".pending")

        /**
         * Returns true only for finished non-hidden media filenames; ignores Android `.pending-*`,
         * `.trashed-*`, `.nomedia`, and partial download suffixes.
         */
        fun shouldNotifyForFilename(fileName: String?): Boolean {
            if (fileName.isNullOrBlank()) {
                return false
            }
            val leaf = fileName.trim().substringAfterLast('/').substringAfterLast('\\')
            if (leaf.isEmpty() || leaf.startsWith(".")) {
                return false
            }
            val lower = leaf.lowercase(Locale.ROOT)
            if (BLOCKED_TEMP_SUFFIXES.any { lower.endsWith(it) }) {
                return false
            }
            val ext = lower.substringAfterLast('.', "")
            return ext.isNotEmpty() && ext in WATCHED_MEDIA_EXTENSIONS
        }

        /**
         * Filters candidate directories to existing, non-hidden, non-encrypted folders and
         * deduplicates them by normalized path.
         */
        fun resolveWatchDirectories(candidates: Collection<File>): List<File> {
            val result = ArrayList<File>()
            val seen = HashSet<String>()
            val secretsToken = "/${SECRETS_DIR_NAME.lowercase(Locale.ROOT)}"
            for (dir in candidates) {
                if (!dir.isDirectory) {
                    continue
                }
                val rawPath = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
                val cleanPath = rawPath.trimEnd('/', '\\')
                if (cleanPath.isEmpty()) {
                    continue
                }
                val leaf = cleanPath.substringAfterLast('/').substringAfterLast('\\')
                val lower = cleanPath.replace('\\', '/').lowercase(Locale.ROOT)
                val isHiddenOrVault = leaf.startsWith(".") ||
                    lower.contains(ENCRYPTED_FOLDER_PREFIX) ||
                    lower == secretsToken ||
                    lower.endsWith(secretsToken) ||
                    lower.contains("$secretsToken/")
                if (isHiddenOrVault) {
                    continue
                }
                if (seen.add(lower)) {
                    result.add(dir)
                }
            }
            return result
        }

        @Suppress("DEPRECATION")
        fun defaultScreenshotCandidateDirs(extraPaths: Collection<String> = emptyList()): List<File> {
            val candidates = ArrayList<File>()
            runCatching {
                val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (pictures != null) {
                    candidates.add(File(pictures, "Screenshots"))
                }
            }
            runCatching {
                val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                if (dcim != null) {
                    candidates.add(File(dcim, "Screenshots"))
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    val screenshots = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_SCREENSHOTS)
                    if (screenshots != null) {
                        candidates.add(screenshots)
                    }
                }
            }
            for (raw in extraPaths) {
                val clean = raw.trim()
                if (clean.isNotEmpty()) {
                    candidates.add(File(clean))
                }
            }
            return resolveWatchDirectories(candidates)
        }
    }

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeObservers = ArrayList<FileObserver>()
    private val watchedPathKeys = HashSet<String>()
    private val pendingFolders = LinkedHashSet<String>()

    private val flushRunnable = Runnable {
        val snapshot: Set<String>
        synchronized(lock) {
            snapshot = LinkedHashSet(pendingFolders)
            pendingFolders.clear()
        }
        if (snapshot.isNotEmpty()) {
            onFoldersChanged(snapshot)
        }
    }

    fun start(extraPaths: Collection<String> = emptyList()) {
        val dirs = defaultScreenshotCandidateDirs(extraPaths)
        val desiredKeys = dirs.mapTo(HashSet()) { dir ->
            runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
                .trimEnd('/', '\\')
                .replace('\\', '/')
                .lowercase(Locale.ROOT)
        }

        synchronized(lock) {
            if (activeObservers.isNotEmpty() && desiredKeys == watchedPathKeys) {
                return
            }
        }

        stop()
        if (dirs.isEmpty()) {
            return
        }

        val created = ArrayList<FileObserver>(dirs.size)
        for (dir in dirs) {
            val dirPath = dir.absolutePath.trimEnd('/')
            val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                object : FileObserver(dir, WATCH_MASK) {
                    override fun onEvent(event: Int, path: String?) {
                        handleEvent(dirPath, path)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                object : FileObserver(dirPath, WATCH_MASK) {
                    override fun onEvent(event: Int, path: String?) {
                        handleEvent(dirPath, path)
                    }
                }
            }
            val started = runCatching {
                observer.startWatching()
                true
            }.getOrDefault(false)
            if (started) {
                created.add(observer)
            }
        }

        synchronized(lock) {
            activeObservers.addAll(created)
            watchedPathKeys.clear()
            watchedPathKeys.addAll(desiredKeys)
        }
    }

    fun stop() {
        mainHandler.removeCallbacks(flushRunnable)
        val toStop: List<FileObserver>
        synchronized(lock) {
            pendingFolders.clear()
            toStop = activeObservers.toList()
            activeObservers.clear()
            watchedPathKeys.clear()
        }
        for (observer in toStop) {
            runCatching { observer.stopWatching() }
        }
    }

    private fun handleEvent(dirPath: String, fileName: String?) {
        if (!shouldNotifyForFilename(fileName)) {
            return
        }
        val normalizedDir = dirPath.trimEnd('/')
        if (normalizedDir.isEmpty()) {
            return
        }
        synchronized(lock) {
            pendingFolders.add(normalizedDir)
        }
        mainHandler.removeCallbacks(flushRunnable)
        mainHandler.postDelayed(flushRunnable, DEBOUNCE_MS)
    }
}

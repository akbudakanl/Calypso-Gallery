package org.fossify.gallery.helpers

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.system.Os
import com.bumptech.glide.Glide
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.deleteDBPaths
import org.fossify.gallery.extensions.deleteVirtualAlbumItemFromAllAlbums
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.mediaDB
import java.io.*
import java.security.SecureRandom
import android.media.MediaScannerConnection
import android.os.Build
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry

/**
 * Extracted from [CryptoManager]. Feature group: CryptoManagerWipe.
 */

// ==================== ANTI-RECOVERY / SECURE WIPE ====================

internal fun CryptoManager.getPendingWipeJournalFile(context: Context): File {
    return File(context.filesDir, "pending_wipe_journal.txt")
}


internal fun CryptoManager.appendPendingWipe(context: Context, path: String) {
    try {
        val file = getPendingWipeJournalFile(context)
        file.appendText(path + "\n", Charsets.UTF_8)
    } catch (_: Exception) {}
}


internal fun CryptoManager.removePendingWipe(context: Context, path: String) {
    try {
        val file = getPendingWipeJournalFile(context)
        if (!file.exists()) return
        val lines = file.readLines(Charsets.UTF_8).filter { it.trim().isNotEmpty() && it.trim() != path }
        if (lines.isEmpty()) {
            secureWipeAndDeleteFile(file, null)
        } else {
            file.writeText(lines.joinToString("\n") + "\n", Charsets.UTF_8)
        }
    } catch (_: Exception) {}
}


fun CryptoManager.processPendingWipeJournal(context: Context) {
    ensureBackgroundThread {
        ForegroundOperationRegistry.withOperation(context, "pending-wipe") {
            try {
                val file = getPendingWipeJournalFile(context)
                if (!file.exists()) return@withOperation
                val lines = file.readLines(Charsets.UTF_8).map { it.trim() }.filter { it.isNotEmpty() }
                val remaining = ArrayList<String>()
                for (i in lines.indices) {
                    if (ForegroundOperationRegistry.isCancelled) {
                        for (rem in i until lines.size) {
                            remaining.add(lines[rem])
                        }
                        break
                    }
                    val trimmed = lines[i]
                    val target = File(trimmed)
                    if (target.exists() && !secureWipeAndDeleteFile(target, context)) {
                        remaining.add(trimmed)
                    }
                }
                if (remaining.isEmpty()) {
                    secureWipeAndDeleteFile(file, null)
                } else {
                    file.writeText(remaining.joinToString("\n") + "\n", Charsets.UTF_8)
                }
            } catch (_: Exception) {}
        }
    }
}


fun CryptoManager.purgeMediaTracesPreservingReferences(context: Context, plaintextPath: String) {
    try {
        val contentResolver = context.contentResolver
        val filesUri = MediaStore.Files.getContentUri("external")

        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DATA} = ?"
        val selectionArgs = arrayOf(plaintextPath)
        var mediaId: Long? = null
        contentResolver.query(filesUri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                mediaId = cursor.getLong(0)
            }
        }

        if (mediaId != null) {
            try {
                contentResolver.delete(
                    MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Images.Thumbnails.IMAGE_ID} = ?",
                    arrayOf(mediaId.toString())
                )
            } catch (_: Exception) {}
            try {
                contentResolver.delete(
                    MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Video.Thumbnails.VIDEO_ID} = ?",
                    arrayOf(mediaId.toString())
                )
            } catch (_: Exception) {}
        }

        contentResolver.delete(filesUri, selection, selectionArgs)
        MediaScannerConnection.scanFile(context, arrayOf(plaintextPath), null, null)
    } catch (_: Exception) {}

    try {
        context.mediaDB.deleteMediumPath(plaintextPath)
        GalleryDatabase.getInstance(context.applicationContext).DateTakensDao().deleteDateTaken(plaintextPath)
    } catch (_: Exception) {}

    try {
        val fileName = plaintextPath.substringAfterLast('/')
        val parent = plaintextPath.substringBeforeLast('/')
        val dcimThumbnails = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), ".thumbnails")
        val picThumbnails = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ".thumbnails")
        val parentThumbnails = File(parent, ".thumbnails")
        listOf(dcimThumbnails, picThumbnails, parentThumbnails).forEach { tDir ->
            if (tDir.exists() && tDir.isDirectory) {
                tDir.listFiles { f -> f.name.contains(fileName) }?.forEach { thumbFile ->
                    secureWipeAndDeleteFile(thumbFile, null)
                }
            }
        }
    } catch (_: Exception) {}
}


fun CryptoManager.purgeTracesForFile(context: Context, plaintextPath: String) {
    purgeMediaTracesPreservingReferences(context, plaintextPath)

    try {
        val hadFav = context.favoritesDB.getFavorite(plaintextPath) != null
        context.favoritesDB.deleteFavoritePath(plaintextPath)
        if (hadFav) {
            ReferenceBackupManager.saveFavorites(context)
        }
        // The extension also rewrites the backup files, so they stay in sync with the database.
        context.deleteVirtualAlbumItemFromAllAlbums(plaintextPath)
    } catch (_: Exception) {}
}


fun CryptoManager.purgeFolderTraces(context: Context, folderPath: String) {
    val cleanPath = folderPath.trimEnd('/')
    try {
        context.deleteDBPaths(emptyList(), listOf(cleanPath))
        context.directoryDB.deleteDirPath(cleanPath)
        context.directoryDB.deleteDirPrefix(cleanPath)
        val db = GalleryDatabase.getInstance(context.applicationContext)
        db.DateTakensDao().deleteDateTakensFromPath(cleanPath)
        db.DateTakensDao().deleteDateTakensByPrefix(cleanPath)
        GalleryDatabase.scrubDatabase(context)
    } catch (_: Exception) {}

    try {
        context.config.scrubFolderPreferences(cleanPath)
    } catch (_: Exception) {}

    try {
        val contentResolver = context.contentResolver
        val filesUri = MediaStore.Files.getContentUri("external")
        contentResolver.delete(
            filesUri,
            "${MediaStore.Files.FileColumns.DATA} LIKE ? OR ${MediaStore.Files.FileColumns.DATA} = ?",
            arrayOf("$cleanPath/%", cleanPath)
        )
        MediaScannerConnection.scanFile(context, arrayOf(cleanPath), null, null)
    } catch (_: Exception) {}

    try {
        val recycleBin = File(context.filesDir, RECYCLE_BIN)
        if (recycleBin.exists() && recycleBin.isDirectory) {
            recycleBin.listFiles()?.forEach { binFile ->
                if (binFile.absolutePath.contains(cleanPath)) {
                    secureWipeAndDeleteFile(binFile, null)
                }
            }
        }
    } catch (_: Exception) {}
}


fun CryptoManager.clearAppImageCaches(context: Context) {
    val appContext = context.applicationContext
    val clearMemory = {
        try {
            Glide.get(appContext).clearMemory()
        } catch (_: Exception) {}
    }
    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
        clearMemory()
    } else {
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post(clearMemory)
        } catch (_: Exception) {}
    }

    ensureBackgroundThread {
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST)
        } catch (_: Exception) {}
        try {
            Glide.get(appContext).clearDiskCache()
        } catch (_: Exception) {}
        try {
            val cacheDirs = listOfNotNull(
                File(appContext.cacheDir, "image_manager_disk_cache"),
                File(appContext.cacheDir, "picasso-cache"),
                File(appContext.cacheDir, "glide"),
                appContext.externalCacheDir,
                File(appContext.cacheDir, TEMP_FOLDER_NAME)
            )
            for (dir in cacheDirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.walkBottomUp().forEach { f ->
                        if (f.isFile) {
                            secureWipeFileInternal(f)
                            f.delete()
                        } else if (f.isDirectory) {
                            f.delete()
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }
}


fun CryptoManager.secureWipeAndDeleteFile(context: Context, file: File): Boolean {
    return secureWipeAndDeleteFile(file, context)
}


fun CryptoManager.secureWipeAndDeleteFile(
    file: File,
    context: Context? = null,
    syncParentDir: Boolean = true,
    purgeTraces: Boolean = true
): Boolean {
    fun perform(): Boolean {
        if (!file.exists()) return true
        val originalPath = file.absolutePath

        val wiped = secureWipeFileInternal(file)
        if (!wiped) {
            return false
        }

        var deleted = file.delete()
        if (!deleted && context != null) {
            deleted = context.tryFastDocumentDelete(originalPath, false)
        }

        if (syncParentDir) {
            syncDir(file.parentFile)
        }

        if (purgeTraces && context != null) {
            runCatching { purgeTracesForFile(context, originalPath) }
        }

        return deleted
    }

    return if (context != null) {
        ForegroundOperationRegistry.withOperation(context, "secure-wipe") { perform() } ?: false
    } else {
        perform()
    }
}


fun CryptoManager.deleteEncryptedFile(context: Context, file: File): Boolean {
    val path = file.absolutePath
    metadataCache.remove(path)?.fileKey?.fill(0)
    if (!file.exists()) return true
    var deleted = file.delete()
    if (!deleted) {
        deleted = context.tryFastDocumentDelete(path, false)
    }
    return deleted
}


internal fun CryptoManager.secureWipeFileInternal(file: File): Boolean {
    return try {
        val length = file.length()
        if (length > 0L) {
            val bufSize = 1024 * 1024 // 1 MiB chunk
            val buf = ByteArray(bufSize)
            RandomAccessFile(file, "rw").use { raf ->
                val ch = raf.channel
                var rem = length
                ch.position(0L)
                while (rem > 0L) {
                    val toWrite = minOf(rem, bufSize.toLong()).toInt()
                    secureRandom.nextBytes(buf)
                    val byteBuf = java.nio.ByteBuffer.wrap(buf, 0, toWrite)
                    while (byteBuf.hasRemaining()) {
                        ch.write(byteBuf)
                    }
                    rem -= toWrite
                }
                ch.force(true)
                ch.truncate(0L)
                ch.force(true)
            }
        }
        true
    } catch (_: Exception) {
        false
    }
}


internal fun CryptoManager.syncDir(dir: File?) {
    if (dir == null || !dir.exists() || !dir.isDirectory) return
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val fd = android.system.Os.open(dir.absolutePath, android.system.OsConstants.O_RDONLY, 0)
            try {
                android.system.Os.fsync(fd)
            } finally {
                android.system.Os.close(fd)
            }
        }
    } catch (_: Throwable) {
    }
}

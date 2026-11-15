package org.fossify.gallery.asynctasks

import android.content.Context
import android.os.AsyncTask
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.extensions.isVirtualAlbumPath
import org.fossify.gallery.extensions.repairReferencesAfterExternalChange
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

class GetMediaAsynctask(
    val context: Context, val mPath: String, val isPickImage: Boolean = false, val isPickVideo: Boolean = false,
    val showAll: Boolean, val isSecretVaultView: Boolean = false, val callback: (media: ArrayList<ThumbnailItem>) -> Unit
) :
    AsyncTask<Void, Void, ArrayList<ThumbnailItem>>() {
    private val mediaFetcher = MediaFetcher(context, isSecretVaultView)

    override fun doInBackground(vararg params: Void): ArrayList<ThumbnailItem> {
        val pathToUse = if (showAll) SHOW_ALL else mPath
        val getProperDateTaken = true
        val getProperLastModified = true
        val getProperFileSize = true
        if (!showAll && (mPath == FAVORITES || mPath.isVirtualAlbumPath())) {
            context.repairReferencesAfterExternalChange()
        }
        val favoritePaths = context.getFavoritePaths()
        val getVideoDurations = context.config.showThumbnailVideoDuration
        val lastModifieds = when {
            showAll || mPath == FAVORITES || mPath.isVirtualAlbumPath() -> mediaFetcher.getLastModifieds()
            mPath != RECYCLE_BIN -> mediaFetcher.getFolderLastModifieds(mPath)
            else -> HashMap()
        }
        val dateTakens = when {
            showAll || mPath == FAVORITES || mPath.isVirtualAlbumPath() -> mediaFetcher.getDateTakens()
            mPath != RECYCLE_BIN -> mediaFetcher.getFolderDateTakens(mPath)
            else -> HashMap()
        }

        val media = if (showAll) {
            val android11Files = mediaFetcher.getAndroid11FolderMedia(
                isPickImage = isPickImage,
                isPickVideo = isPickVideo,
                favoritePaths = favoritePaths,
                getFavoritePathsOnly = false,
                getProperDateTaken = getProperDateTaken,
                dateTakens = dateTakens
            )
            val foldersToScan = mediaFetcher.getFoldersToScan().filter {
                it != RECYCLE_BIN &&
                    it != FAVORITES &&
                    !context.config.isFolderProtected(it) &&
                    (!CryptoManager.isVaultUnlocked || !CryptoManager.isVaultRecycleBinPath(context, it))
            }
            val media = ArrayList<Medium>()
            foldersToScan.forEach {
                val newMedia = mediaFetcher.getFilesFrom(
                    curPath = it,
                    isPickImage = isPickImage,
                    isPickVideo = isPickVideo,
                    getProperDateTaken = getProperDateTaken,
                    getProperLastModified = getProperLastModified,
                    getProperFileSize = getProperFileSize,
                    favoritePaths = favoritePaths,
                    getVideoDurations = getVideoDurations,
                    lastModifieds = lastModifieds,
                    dateTakens = dateTakens,
                    android11Files = android11Files,
                    enableRandom = false,
                    shouldSort = false
                )
                media.addAll(newMedia)
            }

            val scopeKey = if (isSecretVaultView) "vault_show_all" else SHOW_ALL
            mediaFetcher.sortMedia(media, context.config.filesSorting, enableRandom = true, scopeKey = scopeKey)
            media
        } else {
            mediaFetcher.getFilesFrom(
                curPath = mPath,
                isPickImage = isPickImage,
                isPickVideo = isPickVideo,
                getProperDateTaken = getProperDateTaken,
                getProperLastModified = getProperLastModified,
                getProperFileSize = getProperFileSize,
                favoritePaths = favoritePaths,
                getVideoDurations = getVideoDurations,
                lastModifieds = lastModifieds,
                dateTakens = dateTakens,
                android11Files = null,
                enableRandom = true
            )
        }

        return mediaFetcher.groupMedia(media, pathToUse)
    }

    override fun onPostExecute(media: ArrayList<ThumbnailItem>) {
        super.onPostExecute(media)
        callback(media)
    }

    fun stopFetching() {
        mediaFetcher.shouldStop = true
        cancel(true)
    }
}

package org.fossify.gallery.helpers

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.MediaStore.Files
import android.provider.MediaStore.Images
import android.text.format.DateFormat
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.R
import org.fossify.gallery.extensions.*
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import java.io.File
import java.util.Calendar
import java.util.Locale

class MediaFetcher(val context: Context, var isSecretVaultView: Boolean = false) {
    data class MediaStoreSnapshot(
        val lastModifieds: HashMap<String, Long>,
        val dateTakens: HashMap<String, Long>,
        val folderMedia: HashMap<String, ArrayList<Medium>>,
        val mediaFolders: LinkedHashSet<String>,
        val latestFolders: LinkedHashSet<String>
    )

    var shouldStop = false
    private val cachedFileSizes = HashMap<String, Long>()
    private val cachedVideoDurations = HashMap<String, Int>()

    /**
     * Queries MediaStore.Files once using an indexed `MEDIA_TYPE` selection and populates
     * [lastModifieds], [dateTakens], [folderMedia], [mediaFolders], and [latestFolders] in a
     * single cursor pass.
     */
    fun loadMediaStoreSnapshot(
        isPickImage: Boolean,
        isPickVideo: Boolean,
        favoritePaths: ArrayList<String>,
        getProperDateTaken: Boolean,
        filterMediaOverride: Int? = null
    ): MediaStoreSnapshot {
        cachedFileSizes.clear()
        cachedVideoDurations.clear()

        val lastModifieds = HashMap<String, Long>()
        val dateTakens = HashMap<String, Long>()
        val folderMedia = HashMap<String, ArrayList<Medium>>()
        val mediaFolders = LinkedHashSet<String>()
        val latestFolders = LinkedHashSet<String>()

        val dbDateTakens = try {
            context.dateTakensDB.getAllDateTakens()
        } catch (_: Exception) {
            emptyList()
        }
        val dbOverrideDateTakens = if (dbDateTakens.isEmpty()) {
            emptyMap()
        } else {
            HashMap<String, Long>(dbDateTakens.size).apply {
                for (item in dbDateTakens) {
                    put(item.fullPath, item.taken)
                }
            }
        }

        val filterMedia = filterMediaOverride ?: context.config.filterMedia
        if (filterMedia == 0) {
            if (dbOverrideDateTakens.isNotEmpty()) {
                dateTakens.putAll(dbOverrideDateTakens)
            }
            return MediaStoreSnapshot(lastModifieds, dateTakens, folderMedia, mediaFolders, latestFolders)
        }

        val needImageLike = !isPickVideo &&
            (filterMedia and (TYPE_IMAGES or TYPE_GIFS or TYPE_RAWS or TYPE_SVGS or TYPE_PORTRAITS) != 0)
        val needVideos = !isPickImage && (filterMedia and TYPE_VIDEOS != 0)
        if (!needImageLike && !needVideos) {
            if (dbOverrideDateTakens.isNotEmpty()) {
                dateTakens.putAll(dbOverrideDateTakens)
            }
            return MediaStoreSnapshot(lastModifieds, dateTakens, folderMedia, mediaFolders, latestFolders)
        }

        val selection = when {
            needImageLike && needVideos ->
                "${Files.FileColumns.MEDIA_TYPE} IN (${Files.FileColumns.MEDIA_TYPE_IMAGE}, ${Files.FileColumns.MEDIA_TYPE_VIDEO})"
            needImageLike ->
                "${Files.FileColumns.MEDIA_TYPE} = ${Files.FileColumns.MEDIA_TYPE_IMAGE}"
            else ->
                "${Files.FileColumns.MEDIA_TYPE} = ${Files.FileColumns.MEDIA_TYPE_VIDEO}"
        }

        val projection = arrayOf(
            Images.Media._ID,
            Images.Media.DISPLAY_NAME,
            Images.Media.DATA,
            Images.Media.DATE_MODIFIED,
            Images.Media.DATE_TAKEN,
            Images.Media.SIZE,
            MediaStore.MediaColumns.DURATION
        )

        val uri = Files.getContentUri("external")
        val showHidden = context.config.shouldShowHidden
        val favoriteSet = if (favoritePaths.isEmpty()) emptySet() else favoritePaths.toHashSet()
        val topLatest = ArrayList<Pair<Long, String>>(11)
        val defaultLocale = Locale.getDefault()

        try {
            context.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Images.Media._ID)
                val nameIdx = cursor.getColumnIndex(Images.Media.DISPLAY_NAME)
                val dataIdx = cursor.getColumnIndex(Images.Media.DATA)
                val modifiedIdx = cursor.getColumnIndex(Images.Media.DATE_MODIFIED)
                val takenIdx = cursor.getColumnIndex(Images.Media.DATE_TAKEN)
                val sizeIdx = cursor.getColumnIndex(Images.Media.SIZE)
                val durationIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)

                while (cursor.moveToNext()) {
                    if (shouldStop) {
                        break
                    }
                    try {
                        if (dataIdx < 0 || cursor.isNull(dataIdx)) continue
                        val path = cursor.getString(dataIdx) ?: continue
                        val separator = path.lastIndexOf('/')
                        if (separator <= 0) continue
                        val parentPath = path.substring(0, separator)

                        val mediaStoreId = if (idIdx >= 0 && !cursor.isNull(idIdx)) cursor.getLong(idIdx) else 0L
                        val dateModifiedSec = if (modifiedIdx >= 0 && !cursor.isNull(modifiedIdx)) cursor.getLong(modifiedIdx) else 0L
                        val lastModified = dateModifiedSec * 1000L
                        if (lastModified != 0L) {
                            lastModifieds[path] = lastModified
                        }

                        val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                        if (size > 0L) {
                            cachedFileSizes[path] = size
                        }

                        val durationMs = if (durationIdx >= 0 && !cursor.isNull(durationIdx)) cursor.getInt(durationIdx) else 0
                        val videoDuration = if (durationMs > 0) {
                            Math.round(durationMs / 1000.toDouble()).toInt().also {
                                cachedVideoDurations[path] = it
                            }
                        } else {
                            0
                        }

                        val msDateTaken = if (takenIdx >= 0 && !cursor.isNull(takenIdx)) cursor.getLong(takenIdx) else 0L
                        val overrideDateTaken = dbOverrideDateTakens[path]
                        if (overrideDateTaken != null) {
                            dateTakens[path] = overrideDateTaken
                        } else if (msDateTaken != 0L) {
                            dateTakens[path] = msDateTaken
                        }

                        if (mediaStoreId > 0L && (topLatest.size < 10 || mediaStoreId > topLatest.last().first)) {
                            var insertAt = topLatest.size
                            for (i in topLatest.indices) {
                                if (mediaStoreId > topLatest[i].first) {
                                    insertAt = i
                                    break
                                }
                            }
                            topLatest.add(insertAt, mediaStoreId to parentPath)
                            if (topLatest.size > 10) {
                                topLatest.removeAt(topLatest.lastIndex)
                            }
                        }

                        val filename = if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                            cursor.getString(nameIdx)?.takeIf { it.isNotEmpty() } ?: path.substring(separator + 1)
                        } else {
                            path.substring(separator + 1)
                        }

                        val isImage = path.isImageFast()
                        val isVideo = if (isImage) false else path.isVideoFast()
                        val isGif = if (isImage || isVideo) false else path.isGif()
                        val isRaw = if (isImage || isVideo || isGif) false else path.isRawFast()
                        val isSvg = if (isImage || isVideo || isGif || isRaw) false else path.isSvg()

                        if (!isImage && !isVideo && !isGif && !isRaw && !isSvg) {
                            continue
                        }

                        if (isVideo && (isPickImage || filterMedia and TYPE_VIDEOS == 0)) continue
                        if (isImage && (isPickVideo || filterMedia and TYPE_IMAGES == 0)) continue
                        if (isGif && filterMedia and TYPE_GIFS == 0) continue
                        if (isRaw && filterMedia and TYPE_RAWS == 0) continue
                        if (isSvg && filterMedia and TYPE_SVGS == 0) continue
                        if (!showHidden && filename.startsWith('.')) continue
                        if (size <= 0L) continue

                        val type = when {
                            isVideo -> TYPE_VIDEOS
                            isGif -> TYPE_GIFS
                            isRaw -> TYPE_RAWS
                            isSvg -> TYPE_SVGS
                            else -> TYPE_IMAGES
                        }

                        var effectiveDateTaken = if (getProperDateTaken) {
                            overrideDateTaken ?: (if (msDateTaken != 0L) msDateTaken else lastModified)
                        } else {
                            msDateTaken
                        }
                        if (effectiveDateTaken == 0L) {
                            effectiveDateTaken = lastModified
                        }

                        val isFavorite = favoriteSet.contains(path)
                        val medium = Medium(
                            id = null,
                            name = filename,
                            path = path,
                            parentPath = parentPath,
                            modified = lastModified,
                            taken = effectiveDateTaken,
                            size = size,
                            type = type,
                            videoDuration = videoDuration,
                            isFavorite = isFavorite,
                            deletedTS = 0L,
                            mediaStoreId = mediaStoreId
                        )

                        mediaFolders.add(parentPath)
                        val parentKey = parentPath.lowercase(defaultLocale)
                        var currentFolderMedia = folderMedia[parentKey]
                        if (currentFolderMedia == null) {
                            currentFolderMedia = ArrayList()
                            folderMedia[parentKey] = currentFolderMedia
                        }
                        currentFolderMedia.add(medium)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }

        if (dbOverrideDateTakens.isNotEmpty()) {
            dateTakens.putAll(dbOverrideDateTakens)
        }
        for ((_, parentPath) in topLatest) {
            latestFolders.add(parentPath)
        }

        return MediaStoreSnapshot(
            lastModifieds = lastModifieds,
            dateTakens = dateTakens,
            folderMedia = folderMedia,
            mediaFolders = mediaFolders,
            latestFolders = latestFolders
        )
    }

    // on Android 11 we fetch all files at once from MediaStore and have it split by folder, use it if available
    fun getFilesFrom(
        curPath: String, isPickImage: Boolean, isPickVideo: Boolean, getProperDateTaken: Boolean, getProperLastModified: Boolean,
        getProperFileSize: Boolean, favoritePaths: ArrayList<String>, getVideoDurations: Boolean,
        lastModifieds: HashMap<String, Long>, dateTakens: HashMap<String, Long>, android11Files: HashMap<String, ArrayList<Medium>>?,
        enableRandom: Boolean = false, shouldSort: Boolean = true, preferMediaStoreCache: Boolean = false
    ): ArrayList<Medium> {
        val filterMedia = context.config.filterMedia
        if (filterMedia == 0) {
            return ArrayList()
        }

        val curMedia = ArrayList<Medium>()
        if (context.isPathOnOTG(curPath)) {
            if (context.hasOTGConnected()) {
                val newMedia = getMediaOnOTG(curPath, isPickImage, isPickVideo, filterMedia, favoritePaths, getVideoDurations)
                curMedia.addAll(newMedia)
            }
        } else {
            val useMediaStoreCache = (isRPlus() && !isExternalStorageManager()) || preferMediaStoreCache
            if (curPath != FAVORITES && curPath != RECYCLE_BIN && !curPath.isVirtualAlbumPath() && useMediaStoreCache) {
                val folderKey = curPath.lowercase(Locale.getDefault())
                if (android11Files?.containsKey(folderKey) == true) {
                    curMedia.addAll(android11Files[folderKey]!!)
                } else if (android11Files == null) {
                    val files = getAndroid11FolderMedia(isPickImage, isPickVideo, favoritePaths, false, getProperDateTaken, dateTakens)
                    if (files.containsKey(folderKey)) {
                        curMedia.addAll(files[folderKey]!!)
                    }
                }
            }

            if (curMedia.isEmpty()) {
                val newMedia = getMediaInFolder(
                    curPath, isPickImage, isPickVideo, filterMedia, getProperDateTaken, getProperLastModified, getProperFileSize,
                    favoritePaths, getVideoDurations, lastModifieds, dateTakens, hasPreloadedSizes = android11Files != null
                )

                val isVirtualAlbum = curPath.isVirtualAlbumPath()
                if (newMedia.isNotEmpty() && (curPath == FAVORITES || isVirtualAlbum) && isRPlus() && !isExternalStorageManager()) {
                    if (android11Files != null) {
                        val defaultLocale = Locale.getDefault()
                        newMedia.forEach { newMedium ->
                            val media = android11Files[newMedium.parentPath.lowercase(defaultLocale)]
                            if (media != null) {
                                for (medium in media) {
                                    if (medium.path == newMedium.path) {
                                        newMedium.size = medium.size
                                        break
                                    }
                                }
                            }
                        }
                    } else {
                        val referencedPaths = if (isVirtualAlbum) context.getVirtualAlbumPaths(curPath) else favoritePaths
                        val files =
                            getAndroid11FolderMedia(isPickImage, isPickVideo, referencedPaths, true, getProperDateTaken, dateTakens)
                        newMedia.forEach { newMedium ->
                            for ((_, media) in files) {
                                media.forEach { medium ->
                                    if (medium.path == newMedium.path) {
                                        newMedium.size = medium.size
                                    }
                                }
                            }
                        }
                    }
                }
                curMedia.addAll(newMedia)
            }
        }

        if (shouldSort) {
            sortMedia(curMedia, context.config.getEffectiveMediaSorting(curPath), enableRandom, curPath.ifEmpty { SHOW_ALL })
        }
        return curMedia
    }

    fun getFoldersToScan(
        extraFolders: Collection<String> = emptyList(),
        preloadedMediaFolders: Collection<String>? = null,
        preloadedLatestFolders: Collection<String>? = null,
        preloadedNoMediaFolders: Collection<String>? = null
    ): ArrayList<String> {
        return try {
            val OTGPath = context.config.OTGPath
            val config = context.config
            if (isSecretVaultView) {
                val vaultFolders = LinkedHashSet<String>()
                if (CryptoManager.isVaultUnlocked && config.temporarilyShowHidden) {
                    val vaultRoot = CryptoManager.findVaultRoot(context)
                    if (vaultRoot != null && vaultRoot.exists()) {
                        vaultRoot.listFiles()?.forEach { child ->
                            if (child.isDirectory && CryptoManager.isEncryptedFolder(child)) {
                                vaultFolders.add(child.absolutePath)
                            }
                        }
                    }
                    config.encryptedFolders.forEach { encFolder ->
                        if (context.getDoesFilePathExist(encFolder, OTGPath)) {
                            vaultFolders.add(encFolder)
                        }
                    }
                }
                return vaultFolders.filter {
                    context.isEncryptedVaultAlbum(it)
                }.toMutableList() as ArrayList<String>
            }

            val folders = if (preloadedLatestFolders != null) {
                LinkedHashSet(preloadedLatestFolders)
            } else {
                getLatestFileFolders()
            }
            folders.addAll(arrayListOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString(),
                "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)}/Camera",
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
            ).filter { context.getDoesFilePathExist(it, OTGPath) })
            extraFolders.forEach { folder ->
                if (folder.isNotEmpty()) {
                    folders.add(folder)
                }
            }

            if (preloadedMediaFolders != null) {
                folders.addAll(buildFoldersToScanSet(preloadedMediaFolders, preloadedNoMediaFolders))
            } else {
                val filterMedia = context.config.filterMedia
                val uri = Files.getContentUri("external")
                val projection = arrayOf(Images.Media.DATA)
                val selection = getSelectionQuery(filterMedia)
                val selectionArgs = getSelectionArgsQuery(filterMedia).toTypedArray()
                val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                cursor?.let { folders.addAll(parseCursor(it)) }
            }

            val shouldShowHidden = config.shouldShowHidden
            val excludedPaths = if (config.temporarilyShowExcluded) {
                HashSet()
            } else {
                config.excludedFolders
            }

            val includedPaths = config.includedFolders

            val folderNoMediaStatuses = HashMap<String, Boolean>()
            val distinctPathsMap = HashMap<String, String>()
            val distinctPaths = folders.distinctBy {
                when {
                    distinctPathsMap.containsKey(it) -> distinctPathsMap[it]
                    else -> {
                        val distinct = it.getDistinctPath()
                        distinctPathsMap[it.getParentPath()] = distinct.getParentPath()
                        distinct
                    }
                }
            }

            val noMediaFolders = preloadedNoMediaFolders ?: context.getNoMediaFoldersSync()
            noMediaFolders.forEach { folder ->
                folderNoMediaStatuses["$folder/$NOMEDIA"] = true
            }

            distinctPaths.filter {
                if (CryptoManager.isEncryptedFolderPath(context, it)) {
                    config.temporarilyShowHidden && CryptoManager.isVaultUnlocked
                } else if (isSecretVaultView) {
                    context.isEncryptedVaultAlbum(it)
                } else {
                    it.shouldFolderBeVisible(excludedPaths, includedPaths, config.temporarilyShowHiddenFolders, folderNoMediaStatuses) { path, hasNoMedia ->
                        folderNoMediaStatuses[path] = hasNoMedia
                    }
                }
            }.toMutableList() as ArrayList<String>
        } catch (e: Exception) {
            ArrayList()
        }
    }

    fun getLatestFileFolders(): LinkedHashSet<String> {
        val uri = Files.getContentUri("external")
        val projection = arrayOf(Images.ImageColumns.DATA)
        val parents = LinkedHashSet<String>()
        var cursor: Cursor? = null
        try {
            if (isRPlus()) {
                val bundle = Bundle().apply {
                    putInt(ContentResolver.QUERY_ARG_LIMIT, 10)
                    putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(BaseColumns._ID))
                    putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                }

                cursor = context.contentResolver.query(uri, projection, bundle, null)
                if (cursor?.moveToFirst() == true) {
                    do {
                        val path = cursor.getStringValue(Images.ImageColumns.DATA) ?: continue
                        parents.add(path.getParentPath())
                    } while (cursor.moveToNext())
                }
            } else {
                val sorting = "${BaseColumns._ID} DESC LIMIT 10"
                cursor = context.contentResolver.query(uri, projection, null, null, sorting)
                if (cursor?.moveToFirst() == true) {
                    do {
                        val path = cursor.getStringValue(Images.ImageColumns.DATA) ?: continue
                        parents.add(path.getParentPath())
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        } finally {
            cursor?.close()
        }

        return parents
    }

    private fun getSelectionQuery(filterMedia: Int): String {
        val query = StringBuilder()
        if (filterMedia and TYPE_IMAGES != 0) {
            photoExtensions.forEach {
                query.append("${Images.Media.DATA} LIKE ? OR ")
            }
        }

        if (filterMedia and TYPE_PORTRAITS != 0) {
            query.append("${Images.Media.DATA} LIKE ? OR ")
            query.append("${Images.Media.DATA} LIKE ? OR ")
        }

        if (filterMedia and TYPE_VIDEOS != 0) {
            videoExtensions.forEach {
                query.append("${Images.Media.DATA} LIKE ? OR ")
            }
        }

        if (filterMedia and TYPE_GIFS != 0) {
            query.append("${Images.Media.DATA} LIKE ? OR ")
        }

        if (filterMedia and TYPE_RAWS != 0) {
            rawExtensions.forEach {
                query.append("${Images.Media.DATA} LIKE ? OR ")
            }
        }

        if (filterMedia and TYPE_SVGS != 0) {
            query.append("${Images.Media.DATA} LIKE ? OR ")
        }

        return query.toString().trim().removeSuffix("OR")
    }

    private fun getSelectionArgsQuery(filterMedia: Int): ArrayList<String> {
        val args = ArrayList<String>()
        if (filterMedia and TYPE_IMAGES != 0) {
            photoExtensions.forEach {
                args.add("%$it")
            }
        }

        if (filterMedia and TYPE_PORTRAITS != 0) {
            args.add("%.jpg")
            args.add("%.jpeg")
        }

        if (filterMedia and TYPE_VIDEOS != 0) {
            videoExtensions.forEach {
                args.add("%$it")
            }
        }

        if (filterMedia and TYPE_GIFS != 0) {
            args.add("%.gif")
        }

        if (filterMedia and TYPE_RAWS != 0) {
            rawExtensions.forEach {
                args.add("%$it")
            }
        }

        if (filterMedia and TYPE_SVGS != 0) {
            args.add("%.svg")
        }

        return args
    }

    private fun parseCursor(cursor: Cursor): LinkedHashSet<String> {
        val discoveredPaths = ArrayList<String>()
        cursor.use {
            if (cursor.moveToFirst()) {
                val dataIdx = cursor.getColumnIndex(Images.Media.DATA)
                do {
                    val path = if (dataIdx >= 0) cursor.getString(dataIdx) else cursor.getStringValue(Images.Media.DATA)
                    if (path == null) continue
                    val parentPath = File(path).parent ?: continue
                    discoveredPaths.add(parentPath)
                } while (cursor.moveToNext())
            }
        }
        return buildFoldersToScanSet(discoveredPaths)
    }

    private fun buildFoldersToScanSet(
        discoveredPaths: Collection<String>,
        preloadedNoMediaFolders: Collection<String>? = null
    ): LinkedHashSet<String> {
        val foldersToIgnore = arrayListOf("/storage/emulated/legacy")
        val config = context.config
        val includedFolders = config.includedFolders
        val OTGPath = config.OTGPath
        val foldersToScan = config.everShownFolders
            .filter { it == FAVORITES || it == RECYCLE_BIN || context.getDoesFilePathExist(it, OTGPath) }
            .toHashSet()

        for (parentPath in discoveredPaths) {
            if (parentPath.isNotEmpty() && !includedFolders.contains(parentPath) && !foldersToIgnore.contains(parentPath)) {
                foldersToScan.add(parentPath)
            }
        }

        includedFolders.forEach {
            addFolder(foldersToScan, it)
        }

        if (CryptoManager.isVaultUnlocked && (config.temporarilyShowHidden || isSecretVaultView)) {
            val vaultRoot = CryptoManager.findVaultRoot(context)
            if (vaultRoot != null && vaultRoot.exists()) {
                vaultRoot.listFiles()?.forEach { child ->
                    if (child.isDirectory && CryptoManager.isEncryptedFolder(child)) {
                        foldersToScan.add(child.absolutePath)
                    }
                }
            }
            config.encryptedFolders.forEach { encFolder ->
                if (context.getDoesFilePathExist(encFolder, OTGPath)) {
                    foldersToScan.add(encFolder)
                }
            }
        }

        if (config.temporarilyShowExcluded) {
            config.excludedFolders.forEach { excludedFolder ->
                val clean = excludedFolder.trimEnd('/')
                if (clean.isNotEmpty() &&
                    clean != "/" &&
                    !clean.equals(context.internalStoragePath.trimEnd('/'), ignoreCase = true) &&
                    !context.isCipherSystemPath(clean) &&
                    !HiddenFoldersPolicy.isAndroidRestrictedSubpath(clean, context.internalStoragePath) &&
                    context.getDoesFilePathExist(clean, OTGPath)
                ) {
                    addFolder(foldersToScan, clean)
                }
            }
        }

        if (config.temporarilyShowHiddenFolders) {
            val noMediaFolders = preloadedNoMediaFolders ?: context.getNoMediaFoldersSync()
            noMediaFolders.forEach { noMediaFolder ->
                if (!context.isCipherSystemPath(noMediaFolder) && context.getDoesFilePathExist(noMediaFolder, OTGPath)) {
                    foldersToScan.add(noMediaFolder)
                }
            }
            foldersToScan.addAll(context.findHiddenSubfoldersOnDisk(foldersToScan))
        }

        if (isSecretVaultView) {
            foldersToScan.retainAll { context.isEncryptedVaultAlbum(it) }
        }

        return foldersToScan.toMutableSet() as LinkedHashSet<String>
    }

    private fun addFolder(curFolders: HashSet<String>, folder: String) {
        curFolders.add(folder)
        val files = File(folder).listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                addFolder(curFolders, file.absolutePath)
            }
        }
    }

    private fun getMediaInFolder(
        folder: String, isPickImage: Boolean, isPickVideo: Boolean, filterMedia: Int, getProperDateTaken: Boolean,
        getProperLastModified: Boolean, getProperFileSize: Boolean, favoritePaths: ArrayList<String>,
        getVideoDurations: Boolean, lastModifieds: HashMap<String, Long>, dateTakens: HashMap<String, Long>,
        hasPreloadedSizes: Boolean = false
    ): ArrayList<Medium> {
        val media = ArrayList<Medium>()
        val isRecycleBin = folder == RECYCLE_BIN
        val deletedMedia = if (isRecycleBin) {
            context.getUpdatedDeletedMedia()
        } else {
            ArrayList()
        }

        val config = context.config
        val checkProperFileSize = getProperFileSize || config.fileLoadingPriority == PRIORITY_COMPROMISE
        val checkFileExistence = config.fileLoadingPriority == PRIORITY_VALIDITY
        val showHidden = config.shouldShowHidden
        val showHiddenFiles = showHidden || config.temporarilyShowHiddenFolders
        val showPortraits = filterMedia and TYPE_PORTRAITS != 0
        val fileSizes = if ((checkProperFileSize || checkFileExistence) && cachedFileSizes.isEmpty() && !hasPreloadedSizes) getFolderSizes(folder) else HashMap()
        val favoriteSet = if (favoritePaths.size > 4) favoritePaths.toHashSet() else null

        val files = when {
            folder == FAVORITES -> favoritePaths.map { File(it) }.toMutableList() as ArrayList<File>
            folder == RECYCLE_BIN -> deletedMedia.map { File(it.path) }.toMutableList() as ArrayList<File>
            folder.isVirtualAlbumPath() -> context.getVirtualAlbumPaths(folder).map { File(it) }.toMutableList() as ArrayList<File>
            else -> File(folder).listFiles()?.toMutableList() ?: return media
        }

        for (curFile in files) {
            var file = curFile
            if (shouldStop) {
                break
            }

            var path = file.absolutePath
            if (CryptoManager.isEncryptedFile(file.name)) {
                if (!showHidden || !CryptoManager.isVaultUnlocked) {
                    continue
                }
                val meta = CryptoManager.readMetadata(file) ?: continue
                val encType = meta.mediaType
                if (encType == TYPE_VIDEOS && (isPickImage || filterMedia and TYPE_VIDEOS == 0)) continue
                if (encType == TYPE_IMAGES && (isPickVideo || filterMedia and TYPE_IMAGES == 0)) continue
                if (encType == TYPE_GIFS && filterMedia and TYPE_GIFS == 0) continue
                if (encType == TYPE_RAWS && filterMedia and TYPE_RAWS == 0) continue
                if (encType == TYPE_SVGS && filterMedia and TYPE_SVGS == 0) continue

                val isFavorite = favoriteSet?.contains(path) ?: favoritePaths.contains(path)
                val medium = Medium(
                    null,
                    meta.originalName,
                    path,
                    file.parent ?: folder,
                    meta.lastModified,
                    meta.dateTaken,
                    meta.originalSize,
                    encType,
                    meta.videoDuration,
                    isFavorite,
                    0L,
                    0L
                )
                media.add(medium)
                continue
            }

            var isPortrait = false
            val isImage = path.isImageFast()
            val isVideo = if (isImage) false else path.isVideoFast()
            val isGif = if (isImage || isVideo) false else path.isGif()
            val isRaw = if (isImage || isVideo || isGif) false else path.isRawFast()
            val isSvg = if (isImage || isVideo || isGif || isRaw) false else path.isSvg()

            if (!isImage && !isVideo && !isGif && !isRaw && !isSvg) {
                if (showPortraits && file.name.startsWith("img_", true) && file.isDirectory) {
                    val portraitFiles = file.listFiles() ?: continue
                    val cover = portraitFiles.firstOrNull { it.name.contains("cover", true) } ?: portraitFiles.firstOrNull()
                    if (cover != null && !files.contains(cover)) {
                        file = cover
                        path = cover.absolutePath
                        isPortrait = true
                    } else {
                        continue
                    }
                } else {
                    continue
                }
            }

            if (isVideo && (isPickImage || filterMedia and TYPE_VIDEOS == 0))
                continue

            if (isImage && (isPickVideo || filterMedia and TYPE_IMAGES == 0))
                continue

            if (isGif && filterMedia and TYPE_GIFS == 0)
                continue

            if (isRaw && filterMedia and TYPE_RAWS == 0)
                continue

            if (isSvg && filterMedia and TYPE_SVGS == 0)
                continue

            val filename = file.name
            if (!showHiddenFiles && filename.startsWith('.'))
                continue

            var size = cachedFileSizes[path] ?: fileSizes[path] ?: 0L
            if ((checkProperFileSize || checkFileExistence) && size <= 0L) {
                size = file.length()
            }

            if ((checkProperFileSize || checkFileExistence) && size <= 0L) {
                continue
            }

            if ((checkFileExistence || folder == FAVORITES) && (!file.exists() || !file.isFile)) {
                continue
            }

            if (isRecycleBin) {
                deletedMedia.firstOrNull { it.path == path }?.apply {
                    media.add(this)
                }
            } else {
                var newLastModified = lastModifieds[path]
                if (newLastModified == null || newLastModified <= 0L) {
                    newLastModified = if (getProperLastModified) {
                        file.lastModified()
                    } else {
                        0L
                    }
                }
                val lastModified = newLastModified

                val knownDuration = if (isVideo) (cachedVideoDurations[path] ?: 0) else 0
                val videoDuration = if (knownDuration > 0) {
                    knownDuration
                } else if (getVideoDurations && isVideo) {
                    val systemDuration = context.getDuration(path) ?: 0
                    if (systemDuration > 0) {
                        systemDuration
                    } else {
                        probeContainerDurationSeconds(file).also {
                            if (it > 0) {
                                cachedVideoDurations[path] = it
                            }
                        }
                    }
                } else {
                    0
                }

                val knownDateTaken = dateTakens[path]
                val dateTaken = if (knownDateTaken != null && knownDateTaken > 0L) {
                    knownDateTaken
                } else if (getProperDateTaken) {
                    if (lastModified > 0L) lastModified else file.lastModified()
                } else {
                    lastModified
                }

                val type = when {
                    isVideo -> TYPE_VIDEOS
                    isGif -> TYPE_GIFS
                    isRaw -> TYPE_RAWS
                    isSvg -> TYPE_SVGS
                    isPortrait -> TYPE_PORTRAITS
                    else -> TYPE_IMAGES
                }

                val isFavorite = favoriteSet?.contains(path) ?: favoritePaths.contains(path)
                val medium = Medium(null, filename, path, file.parent, lastModified, dateTaken, size, type, videoDuration, isFavorite, 0L, 0L)
                media.add(medium)
            }
        }

        return media
    }

    fun getAndroid11FolderMedia(
        isPickImage: Boolean,
        isPickVideo: Boolean,
        favoritePaths: ArrayList<String>,
        getFavoritePathsOnly: Boolean,
        getProperDateTaken: Boolean,
        dateTakens: HashMap<String, Long>
    ): HashMap<String, ArrayList<Medium>> {
        val media = HashMap<String, ArrayList<Medium>>()
        if (!isRPlus() || Environment.isExternalStorageManager()) {
            return media
        }

        val filterMedia = context.config.filterMedia
        val showHidden = context.config.shouldShowHidden

        val projection = arrayOf(
            Images.Media._ID,
            Images.Media.DISPLAY_NAME,
            Images.Media.DATA,
            Images.Media.DATE_MODIFIED,
            Images.Media.DATE_TAKEN,
            Images.Media.SIZE,
            MediaStore.MediaColumns.DURATION
        )

        val uri = Files.getContentUri("external")

        context.queryCursor(uri, projection) { cursor ->
            if (shouldStop) {
                return@queryCursor
            }

            try {
                val mediaStoreId = cursor.getLongValue(Images.Media._ID)
                val path = cursor.getStringValue(Images.Media.DATA) ?: return@queryCursor
                val filename = cursor.getStringValue(Images.Media.DISPLAY_NAME)
                    ?.takeIf { it.isNotEmpty() }
                    ?: path.getFilenameFromPath()
                if (getFavoritePathsOnly && !favoritePaths.contains(path)) {
                    return@queryCursor
                }

                val isPortrait = false
                val isImage = path.isImageFast()
                val isVideo = if (isImage) false else path.isVideoFast()
                val isGif = if (isImage || isVideo) false else path.isGif()
                val isRaw = if (isImage || isVideo || isGif) false else path.isRawFast()
                val isSvg = if (isImage || isVideo || isGif || isRaw) false else path.isSvg()

                if (!isImage && !isVideo && !isGif && !isRaw && !isSvg) {
                    return@queryCursor
                }

                if (isVideo && (isPickImage || filterMedia and TYPE_VIDEOS == 0))
                    return@queryCursor

                if (isImage && (isPickVideo || filterMedia and TYPE_IMAGES == 0))
                    return@queryCursor

                if (isGif && filterMedia and TYPE_GIFS == 0)
                    return@queryCursor

                if (isRaw && filterMedia and TYPE_RAWS == 0)
                    return@queryCursor

                if (isSvg && filterMedia and TYPE_SVGS == 0)
                    return@queryCursor

                if (!showHidden && filename.startsWith('.'))
                    return@queryCursor

                val size = cursor.getLongValue(Images.Media.SIZE)
                if (size <= 0L) {
                    return@queryCursor
                }

                val type = when {
                    isVideo -> TYPE_VIDEOS
                    isGif -> TYPE_GIFS
                    isRaw -> TYPE_RAWS
                    isSvg -> TYPE_SVGS
                    isPortrait -> TYPE_PORTRAITS
                    else -> TYPE_IMAGES
                }

                val lastModified = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000
                var dateTaken = cursor.getLongValue(Images.Media.DATE_TAKEN)

                if (getProperDateTaken) {
                    dateTaken = dateTakens[path] ?: lastModified
                }

                if (dateTaken == 0L) {
                    dateTaken = lastModified
                }

                val videoDuration = Math.round(cursor.getIntValue(MediaStore.MediaColumns.DURATION) / 1000.toDouble()).toInt()
                val isFavorite = favoritePaths.contains(path)
                val medium =
                    Medium(null, filename, path, path.getParentPath(), lastModified, dateTaken, size, type, videoDuration, isFavorite, 0L, mediaStoreId)
                val parent = medium.parentPath.lowercase(Locale.getDefault())
                val currentFolderMedia = media[parent]
                if (currentFolderMedia == null) {
                    media[parent] = ArrayList<Medium>()
                }

                media[parent]?.add(medium)
            } catch (e: Exception) {
            }
        }

        return media
    }

    private fun getMediaOnOTG(
        folder: String, isPickImage: Boolean, isPickVideo: Boolean, filterMedia: Int, favoritePaths: ArrayList<String>,
        getVideoDurations: Boolean
    ): ArrayList<Medium> {
        val media = ArrayList<Medium>()
        val files = context.getDocumentFile(folder)?.listFiles() ?: return media
        val checkFileExistence = context.config.fileLoadingPriority == PRIORITY_VALIDITY
        val showHidden = context.config.shouldShowHidden
        val OTGPath = context.config.OTGPath

        for (file in files) {
            if (shouldStop) {
                break
            }

            val filename = file.name ?: continue
            val isImage = filename.isImageFast()
            val isVideo = if (isImage) false else filename.isVideoFast()
            val isGif = if (isImage || isVideo) false else filename.isGif()
            val isRaw = if (isImage || isVideo || isGif) false else filename.isRawFast()
            val isSvg = if (isImage || isVideo || isGif || isRaw) false else filename.isSvg()

            if (!isImage && !isVideo && !isGif && !isRaw && !isSvg)
                continue

            if (isVideo && (isPickImage || filterMedia and TYPE_VIDEOS == 0))
                continue

            if (isImage && (isPickVideo || filterMedia and TYPE_IMAGES == 0))
                continue

            if (isGif && filterMedia and TYPE_GIFS == 0)
                continue

            if (isRaw && filterMedia and TYPE_RAWS == 0)
                continue

            if (isSvg && filterMedia and TYPE_SVGS == 0)
                continue

            if (!showHidden && filename.startsWith('.'))
                continue

            val size = file.length()
            if (size <= 0L || (checkFileExistence && !context.getDoesFilePathExist(file.uri.toString(), OTGPath)))
                continue

            val dateTaken = file.lastModified()
            val dateModified = file.lastModified()

            val type = when {
                isVideo -> TYPE_VIDEOS
                isGif -> TYPE_GIFS
                isRaw -> TYPE_RAWS
                isSvg -> TYPE_SVGS
                else -> TYPE_IMAGES
            }

            val path = Uri.decode(
                file.uri.toString().replaceFirst("${context.config.OTGTreeUri}/document/${context.config.OTGPartition}%3A", "${context.config.OTGPath}/")
            )
            val videoDuration = if (getVideoDurations) context.getDuration(path) ?: 0 else 0
            val isFavorite = favoritePaths.contains(path)
            val medium = Medium(null, filename, path, folder, dateModified, dateTaken, size, type, videoDuration, isFavorite, 0L, 0L)
            media.add(medium)
        }

        return media
    }

    fun getFolderDateTakens(folder: String): HashMap<String, Long> {
        val dateTakens = HashMap<String, Long>()
        if (folder != FAVORITES && !folder.isVirtualAlbumPath()) {
            val projection = arrayOf(
                Images.Media.DISPLAY_NAME,
                Images.Media.DATE_TAKEN
            )

            val uri = Files.getContentUri("external")
            val selection = "${Images.Media.DATA} LIKE ? AND ${Images.Media.DATA} NOT LIKE ?"
            val selectionArgs = arrayOf("$folder/%", "$folder/%/%")

            context.queryCursor(uri, projection, selection, selectionArgs) { cursor ->
                try {
                    val dateTaken = cursor.getLongValue(Images.Media.DATE_TAKEN)
                    if (dateTaken != 0L) {
                        val name = cursor.getStringValue(Images.Media.DISPLAY_NAME)
                        dateTakens["$folder/$name"] = dateTaken
                    }
                } catch (e: Exception) {
                }
            }
        }

        val dateTakenValues = try {
            if (folder == FAVORITES || folder.isVirtualAlbumPath()) {
                context.dateTakensDB.getAllDateTakens()
            } else {
                context.dateTakensDB.getDateTakensFromPath(folder)
            }
        } catch (e: Exception) {
            return dateTakens
        }

        dateTakenValues.forEach {
            dateTakens[it.fullPath] = it.taken
        }

        return dateTakens
    }

    fun getDateTakens(): HashMap<String, Long> {
        val dateTakens = HashMap<String, Long>()
        val projection = arrayOf(
            Images.Media.DATA,
            Images.Media.DATE_TAKEN
        )

        val uri = Files.getContentUri("external")

        try {
            context.queryCursor(uri, projection) { cursor ->
                try {
                    val dateTaken = cursor.getLongValue(Images.Media.DATE_TAKEN)
                    if (dateTaken != 0L) {
                        val path = cursor.getStringValue(Images.Media.DATA)
                        dateTakens[path] = dateTaken
                    }
                } catch (e: Exception) {
                }
            }

            val dateTakenValues = context.dateTakensDB.getAllDateTakens()

            dateTakenValues.forEach {
                dateTakens[it.fullPath] = it.taken
            }
        } catch (e: Exception) {
        }

        return dateTakens
    }

    fun getFolderLastModifieds(folder: String): HashMap<String, Long> {
        val lastModifieds = HashMap<String, Long>()
        if (folder != FAVORITES && !folder.isVirtualAlbumPath()) {
            val projection = arrayOf(
                Images.Media.DISPLAY_NAME,
                Images.Media.DATE_MODIFIED,
                Images.Media.SIZE,
                MediaStore.MediaColumns.DURATION
            )

            val uri = Files.getContentUri("external")
            val selection = "${Images.Media.DATA} LIKE ? AND ${Images.Media.DATA} NOT LIKE ?"
            val selectionArgs = arrayOf("$folder/%", "$folder/%/%")

            context.queryCursor(uri, projection, selection, selectionArgs) { cursor ->
                try {
                    val name = cursor.getStringValue(Images.Media.DISPLAY_NAME)
                    val path = "$folder/$name"
                    val lastModified = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000
                    if (lastModified != 0L) {
                        lastModifieds[path] = lastModified
                    }
                    val size = cursor.getLongValue(Images.Media.SIZE)
                    if (size > 0L) {
                        cachedFileSizes[path] = size
                    }
                    val durationMs = cursor.getIntValue(MediaStore.MediaColumns.DURATION)
                    if (durationMs > 0) {
                        cachedVideoDurations[path] = Math.round(durationMs / 1000.toDouble()).toInt()
                    }
                } catch (e: Exception) {
                }
            }
        }

        return lastModifieds
    }

    fun getLastModifieds(): HashMap<String, Long> {
        cachedFileSizes.clear()
        cachedVideoDurations.clear()
        val lastModifieds = HashMap<String, Long>()
        val projection = arrayOf(
            Images.Media.DATA,
            Images.Media.DATE_MODIFIED,
            Images.Media.SIZE,
            MediaStore.MediaColumns.DURATION
        )

        val uri = Files.getContentUri("external")

        try {
            context.queryCursor(uri, projection) { cursor ->
                try {
                    val path = cursor.getStringValue(Images.Media.DATA)
                    val lastModified = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000
                    if (lastModified != 0L) {
                        lastModifieds[path] = lastModified
                    }
                    val size = cursor.getLongValue(Images.Media.SIZE)
                    if (size > 0L) {
                        cachedFileSizes[path] = size
                    }
                    val durationMs = cursor.getIntValue(MediaStore.MediaColumns.DURATION)
                    if (durationMs > 0) {
                        cachedVideoDurations[path] = Math.round(durationMs / 1000.toDouble()).toInt()
                    }
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }

        return lastModifieds
    }

    private fun getFolderSizes(folder: String): HashMap<String, Long> {
        val sizes = HashMap<String, Long>()
        if (folder != FAVORITES && !folder.isVirtualAlbumPath()) {
            val projection = arrayOf(
                Images.Media.DISPLAY_NAME,
                Images.Media.SIZE
            )

            val uri = Files.getContentUri("external")
            val selection = "${Images.Media.DATA} LIKE ? AND ${Images.Media.DATA} NOT LIKE ?"
            val selectionArgs = arrayOf("$folder/%", "$folder/%/%")

            context.queryCursor(uri, projection, selection, selectionArgs) { cursor ->
                try {
                    val size = cursor.getLongValue(Images.Media.SIZE)
                    if (size != 0L) {
                        val name = cursor.getStringValue(Images.Media.DISPLAY_NAME)
                        sizes["$folder/$name"] = size
                    }
                } catch (e: Exception) {
                }
            }
        }

        return sizes
    }

    fun sortMedia(media: ArrayList<Medium>, sorting: Int, enableRandom: Boolean = true, scopeKey: String = "") {
        if (enableRandom && sorting and SORT_BY_RANDOM != 0) {
            RandomOrderManager.sortMediaRandomly(media, scopeKey)
            return
        }

        media.sortWith { o1, o2 ->
            o1 as Medium
            o2 as Medium
            var result = when {
                sorting and SORT_BY_NAME != 0 -> {
                    if (sorting and SORT_USE_NUMERIC_VALUE != 0) {
                        AlphanumericComparator().compare(o1.name.normalizeString().lowercase(Locale.getDefault()), o2.name.normalizeString().lowercase(Locale.getDefault()))
                    } else {
                        o1.name.normalizeString().lowercase(Locale.getDefault()).compareTo(o2.name.normalizeString().lowercase(Locale.getDefault()))
                    }
                }

                sorting and SORT_BY_PATH != 0 -> {
                    if (sorting and SORT_USE_NUMERIC_VALUE != 0) {
                        AlphanumericComparator().compare(o1.path.lowercase(Locale.getDefault()), o2.path.lowercase(Locale.getDefault()))
                    } else {
                        o1.path.lowercase(Locale.getDefault()).compareTo(o2.path.lowercase(Locale.getDefault()))
                    }
                }

                sorting and SORT_BY_SIZE != 0 -> o1.size.compareTo(o2.size)
                sorting and SORT_BY_DATE_MODIFIED != 0 -> o1.modified.compareTo(o2.modified)
                else -> o1.taken.compareTo(o2.taken)
            }

            if (sorting and SORT_DESCENDING != 0) {
                result *= -1
            }

            if (result == 0) {
                result = o1.name.lowercase(Locale.getDefault()).compareTo(o2.name.lowercase(Locale.getDefault()))
            }
            if (result == 0) {
                result = o1.path.lowercase(Locale.getDefault()).compareTo(o2.path.lowercase(Locale.getDefault()))
            }
            result
        }
    }

    fun groupMedia(media: ArrayList<Medium>, path: String): ArrayList<ThumbnailItem> {
        val pathToCheck = if (path.isEmpty()) SHOW_ALL else path
        val currentGrouping = context.config.getFolderGrouping(pathToCheck)
        if (currentGrouping and GROUP_BY_NONE != 0) {
            return media as ArrayList<ThumbnailItem>
        }

        val thumbnailItems = ArrayList<ThumbnailItem>()
        if (context.config.scrollHorizontally) {
            media.mapTo(thumbnailItems) { it }
            return thumbnailItems
        }

        val mediumGroups = LinkedHashMap<String, ArrayList<Medium>>()
        media.forEach {
            val key = it.getGroupingKey(currentGrouping)
            if (!mediumGroups.containsKey(key)) {
                mediumGroups[key] = ArrayList()
            }
            mediumGroups[key]!!.add(it)
        }

        val sortDescending = currentGrouping and GROUP_DESCENDING != 0
        val sorted = if (currentGrouping and GROUP_BY_LAST_MODIFIED_DAILY != 0 || currentGrouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0 ||
            currentGrouping and GROUP_BY_DATE_TAKEN_DAILY != 0 || currentGrouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0
        ) {
            mediumGroups.toSortedMap(if (sortDescending) compareByDescending {
                it.toLongOrNull() ?: 0L
            } else {
                compareBy { it.toLongOrNull() ?: 0L }
            })
        } else {
            mediumGroups.toSortedMap(if (sortDescending) compareByDescending { it } else compareBy { it })
        }

        mediumGroups.clear()
        for ((key, value) in sorted) {
            mediumGroups[key] = value
        }

        val today = formatDate(System.currentTimeMillis().toString(), true)
        val yesterday = formatDate((System.currentTimeMillis() - DAY_SECONDS * 1000).toString(), true)
        for ((key, value) in mediumGroups) {
            var currentGridPosition = 0
            val sectionKey = getFormattedKey(key, currentGrouping, today, yesterday, value.size)
            thumbnailItems.add(ThumbnailSection(sectionKey))

            value.forEach {
                it.gridPosition = currentGridPosition++
            }

            thumbnailItems.addAll(value)
        }

        return thumbnailItems
    }

    private fun getFormattedKey(key: String, grouping: Int, today: String, yesterday: String, count: Int): String {
        var result = when {
            grouping and GROUP_BY_LAST_MODIFIED_DAILY != 0 || grouping and GROUP_BY_DATE_TAKEN_DAILY != 0 -> getFinalDate(
                formatDate(key, true),
                today,
                yesterday
            )

            grouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0 || grouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0 -> formatDate(key, false)
            grouping and GROUP_BY_FILE_TYPE != 0 -> getFileTypeString(key)
            grouping and GROUP_BY_EXTENSION != 0 -> key.uppercase(Locale.getDefault())
            grouping and GROUP_BY_FOLDER != 0 -> {
                if (CryptoManager.isEncryptedFolderPath(context, key)) {
                    context.getFolderNameFromPath(key)
                } else {
                    context.humanizePath(key)
                }
            }
            else -> key
        }

        if (result.isEmpty()) {
            result = context.getString(org.fossify.commons.R.string.unknown)
        }

        return if (grouping and GROUP_SHOW_FILE_COUNT != 0) {
            "$result ($count)"
        } else {
            result
        }
    }

    private fun getFinalDate(date: String, today: String, yesterday: String): String {
        return when (date) {
            today -> context.getString(org.fossify.commons.R.string.today)
            yesterday -> context.getString(org.fossify.commons.R.string.yesterday)
            else -> date
        }
    }

    private fun formatDate(timestamp: String, showDay: Boolean): String {
        return if (timestamp.areDigitsOnly()) {
            val cal = Calendar.getInstance(Locale.ENGLISH)
            cal.timeInMillis = timestamp.toLong()
            val format = if (showDay) context.config.dateFormat else "MMMM yyyy"
            DateFormat.format(format, cal).toString()
        } else {
            ""
        }
    }

    private fun getFileTypeString(key: String): String {
        val stringId = when (key.toInt()) {
            TYPE_IMAGES -> R.string.images
            TYPE_VIDEOS -> R.string.videos
            TYPE_GIFS -> R.string.gifs
            TYPE_RAWS -> R.string.raw_images
            TYPE_SVGS -> R.string.svgs
            else -> R.string.portraits
        }
        return context.getString(stringId)
    }
}

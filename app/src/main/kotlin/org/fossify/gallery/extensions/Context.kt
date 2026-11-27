package org.fossify.gallery.extensions

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore.Files
import android.provider.MediaStore.Images
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getDuration
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getMimeTypeFromUri
import org.fossify.commons.extensions.getOTGPublicPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.humanizePath
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isPathOnSD
import org.fossify.commons.extensions.isPortrait
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.otgPath
import org.fossify.commons.extensions.sdCardPath
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.commons.helpers.SORT_BY_COUNT
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.commons.helpers.SORT_BY_PATH
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.helpers.sumByLong
import org.fossify.commons.views.MySquareImageView
import org.fossify.gallery.R
import org.fossify.gallery.asynctasks.GetMediaAsynctask
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.EncryptedGlideOptions
import org.fossify.gallery.helpers.GROUP_BY_DATE_TAKEN_DAILY
import org.fossify.gallery.helpers.GROUP_BY_DATE_TAKEN_MONTHLY
import org.fossify.gallery.helpers.GROUP_BY_LAST_MODIFIED_DAILY
import org.fossify.gallery.helpers.GROUP_BY_LAST_MODIFIED_MONTHLY
import org.fossify.gallery.helpers.GROUP_BY_NONE
import org.fossify.gallery.helpers.IsoTypeReader
import org.fossify.gallery.helpers.LOCATION_INTERNAL
import org.fossify.gallery.helpers.LOCATION_OTG
import org.fossify.gallery.helpers.LOCATION_SD
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.MyWidgetProvider
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.ROUNDED_CORNERS_NONE
import org.fossify.gallery.helpers.ROUNDED_CORNERS_SMALL
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.THUMBNAIL_FADE_DURATION_MS
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_PORTRAITS
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.applyEncryptedCachePolicy
import org.fossify.gallery.helpers.getDecryptedFolderName
import org.fossify.gallery.helpers.getDisplayName
import org.fossify.gallery.helpers.isEncryptedFolder
import org.fossify.gallery.helpers.isEncryptedFolderPath
import org.fossify.gallery.interfaces.DateTakensDao
import org.fossify.gallery.interfaces.DirectoryDao
import org.fossify.gallery.interfaces.FavoritesDao
import org.fossify.gallery.interfaces.MediumDao
import org.fossify.gallery.interfaces.WidgetsDao
import org.fossify.gallery.models.AlbumCover
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale

val Context.audioManager get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

fun Context.getHumanizedFilename(path: String): String {
    val humanized = humanizePath(path)
    return humanized.substring(humanized.lastIndexOf("/") + 1)
}

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.widgetsDB: WidgetsDao
    get() = GalleryDatabase.getInstance(applicationContext).WidgetsDao()

val Context.mediaDB: MediumDao get() = GalleryDatabase.getInstance(applicationContext).MediumDao()

val Context.directoryDB: DirectoryDao
    get() = GalleryDatabase.getInstance(applicationContext).DirectoryDao()

val Context.favoritesDB: FavoritesDao
    get() = GalleryDatabase.getInstance(applicationContext).FavoritesDao()

val Context.dateTakensDB: DateTakensDao
    get() = GalleryDatabase.getInstance(applicationContext).DateTakensDao()

val Context.recycleBin: File get() = filesDir

fun Context.getNoMediaFolders(callback: (folders: ArrayList<String>) -> Unit) {
    ensureBackgroundThread {
        callback(getNoMediaFoldersSync())
    }
}

fun Context.getNoMediaFoldersSync(): ArrayList<String> {
    val folders = ArrayList<String>()

    val uri = Files.getContentUri("external")
    val projection = arrayOf(Files.FileColumns.DATA)
    val selection = "${Files.FileColumns.MEDIA_TYPE} = ? AND ${Files.FileColumns.TITLE} LIKE ?"
    val selectionArgs = arrayOf(Files.FileColumns.MEDIA_TYPE_NONE.toString(), "%$NOMEDIA%")
    val sortOrder = "${Files.FileColumns.DATE_MODIFIED} DESC"
    val OTGPath = config.OTGPath
    val canInspectNoMedia = !isRPlus() || isExternalStorageManager()

    var cursor: Cursor? = null
    try {
        cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        if (cursor?.moveToFirst() == true) {
            do {
                val path = cursor.getStringValue(Files.FileColumns.DATA) ?: continue
                val noMediaFile = File(path)
                if (noMediaFile.name != NOMEDIA) continue
                if (canInspectNoMedia) {
                    if (getDoesFilePathExist(noMediaFile.absolutePath, OTGPath)) {
                        noMediaFile.parent?.let { folders.add(it) }
                    } else if (!noMediaFile.exists()) {
                        try {
                            contentResolver.delete(uri, "${Files.FileColumns.DATA} = ?", arrayOf(path))
                        } catch (_: Exception) {}
                    }
                } else {
                    noMediaFile.parent?.let { parentPath ->
                        val parentDir = File(parentPath)
                        if (parentDir.exists() || getDoesFilePathExist(parentPath, OTGPath)) {
                            folders.add(parentPath)
                        }
                    }
                }
            } while (cursor.moveToNext())
        }
    } catch (ignored: Exception) {
    } finally {
        cursor?.close()
    }

    return folders
}

fun Context.rescanFolderMedia(path: String) {
    if (path.isVirtualAlbumPath()) {
        return
    }
    ensureBackgroundThread {
        rescanFolderMediaSync(path)
    }
}

fun Context.rescanFolderMediaSync(path: String) {
    if (path.isVirtualAlbumPath()) {
        return
    }
    getCachedMedia(path) { cached ->
        GetMediaAsynctask(
            context = applicationContext,
            mPath = path,
            isPickImage = false,
            isPickVideo = false,
            showAll = false
        ) { newMedia ->
            ensureBackgroundThread {
                val media = newMedia.filterIsInstance<Medium>() as ArrayList<Medium>
                try {
                    mediaDB.insertAll(media.filterNot { isPathHiddenOrEncrypted(it.path) })

                    cached.forEach { thumbnailItem ->
                        if (!newMedia.contains(thumbnailItem)) {
                            val mediumPath = (thumbnailItem as? Medium)?.path
                            if (mediumPath != null) {
                                deleteMediumWithPath(mediumPath)
                            }
                        }
                    }
                } catch (ignored: Exception) {
                }
            }
        }.execute()
    }
}

fun Context.storeDirectoryItems(items: ArrayList<Directory>) {
    ensureBackgroundThread {
        // virtual albums and encrypted vault albums are not cached in the directories table
        directoryDB.insertAll(items.filterNot { it.isVirtualAlbum() || CryptoManager.isEncryptedFolderPath(this, it.path) })
    }
}

fun Context.checkAppendingHidden(
    path: String,
    hidden: String,
    includedFolders: MutableSet<String>,
    noMediaFolders: ArrayList<String>
): String {
    return getFolderNameFromPath(path)
}

fun Context.getFolderNameFromPath(path: String): String {
    return when (path) {
        internalStoragePath -> getString(org.fossify.commons.R.string.internal)
        sdCardPath -> getString(org.fossify.commons.R.string.sd_card)
        otgPath -> getString(org.fossify.commons.R.string.usb)
        FAVORITES -> getString(org.fossify.commons.R.string.favorites)
        RECYCLE_BIN -> getString(org.fossify.commons.R.string.recycle_bin)
        else -> if (path.isVirtualAlbumPath()) {
            getVirtualAlbumName(path) ?: path
        } else {
            val folderFile = File(path)
            if (CryptoManager.isEncryptedFolder(folderFile)) {
                CryptoManager.getDecryptedFolderName(folderFile, this) ?: path.getFilenameFromPath()
            } else {
                path.getFilenameFromPath()
            }
        }
    }
}

fun Context.loadImage(
    type: Int,
    path: String,
    target: MySquareImageView,
    horizontalScroll: Boolean,
    cropThumbnails: Boolean,
    roundCorners: Int,
    signature: ObjectKey,
    skipMemoryCacheAtPaths: ArrayList<String>? = null,
    onError: (() -> Unit)? = null
) {
    target.isHorizontalScrolling = horizontalScroll
    if (type == TYPE_SVGS) {
        loadSVG(
            path = path,
            target = target,
            cropThumbnails = cropThumbnails,
            roundCorners = roundCorners,
            signature = signature
        )
    } else {
        loadImageBase(
            path = path,
            target = target,
            cropThumbnails = cropThumbnails,
            roundCorners = roundCorners,
            signature = signature,
            skipMemoryCacheAtPaths = skipMemoryCacheAtPaths,
            onError = onError
        )
    }
}

fun Context.addTempFolderIfNeeded(dirs: ArrayList<Directory>): ArrayList<Directory> {
    val tempFolderPath = config.tempFolderPath
    return if (tempFolderPath.isNotEmpty()) {
        val directories = ArrayList<Directory>()
        val newFolder = Directory(
            id = null,
            path = tempFolderPath,
            tmb = "",
            name = tempFolderPath.getFilenameFromPath(),
            mediaCnt = 0,
            modified = 0,
            taken = 0,
            size = 0L,
            location = getPathLocation(tempFolderPath),
            types = 0,
            sortValue = ""
        )
        directories.add(newFolder)
        directories.addAll(dirs)
        directories
    } else {
        dirs
    }
}

fun Context.getPathLocation(path: String): Int {
    return when {
        isPathOnSD(path) -> LOCATION_SD
        isPathOnOTG(path) -> LOCATION_OTG
        else -> LOCATION_INTERNAL
    }
}

@SuppressLint("CheckResult")
fun Context.loadImageBase(
    path: String,
    target: MySquareImageView,
    cropThumbnails: Boolean,
    roundCorners: Int,
    signature: ObjectKey,
    skipMemoryCacheAtPaths: ArrayList<String>? = null,
    crossFadeDuration: Int = THUMBNAIL_FADE_DURATION_MS,
    onError: (() -> Unit)? = null
) {
    val isEncrypted = CryptoManager.isEncryptedFile(path)
    val options = RequestOptions()
        .signature(signature)
        .skipMemoryCache(isEncrypted || skipMemoryCacheAtPaths?.contains(path) == true)
        .priority(Priority.LOW)
        .diskCacheStrategy(if (isEncrypted) DiskCacheStrategy.NONE else DiskCacheStrategy.RESOURCE)
        .format(DecodeFormat.PREFER_ARGB_8888)
        .dontAnimate()
        // Decode thumbnails statically so animated images never spawn parallel animation decoders in grids.
        .decode(Bitmap::class.java)

    if (cropThumbnails) {
        options.optionalTransform(CenterCrop())
    } else {
        options.optionalTransform(FitCenter())
    }

    if (isEncrypted) {
        options.set(EncryptedGlideOptions.PREFER_EMBEDDED_THUMBNAIL, true)
    }
    options.set(org.fossify.gallery.helpers.media.IsolatedGlideOptions.SCALE_CENTER_CROP, cropThumbnails)

    if (roundCorners != ROUNDED_CORNERS_NONE) {
        val cornerSize =
            if (roundCorners == ROUNDED_CORNERS_SMALL) {
                org.fossify.commons.R.dimen.rounded_corner_radius_small
            } else {
                org.fossify.commons.R.dimen.rounded_corner_radius_big
            }
        val cornerRadius = resources.getDimension(cornerSize).toInt()
        val roundedCornersTransform = RoundedCorners(cornerRadius)
        options.optionalTransform(MultiTransformation(CenterCrop(), roundedCornersTransform))
    }

    var builder = Glide.with(applicationContext)
        .load(path)
        .apply(options)
        .transition(getOptionalCrossFadeTransition(crossFadeDuration))

    builder = builder.listener(object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            targetBitmap: Target<Drawable>,
            isFirstResource: Boolean
        ): Boolean {
            onError?.invoke()

            return true
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            targetBitmap: Target<Drawable>,
            dataSource: DataSource,
            isFirstResource: Boolean,
        ) = false
    })

    builder.into(target)
}

fun Context.loadSVG(
    path: String,
    target: MySquareImageView,
    cropThumbnails: Boolean,
    roundCorners: Int,
    signature: ObjectKey,
    crossFadeDuration: Int = THUMBNAIL_FADE_DURATION_MS,
) {
    loadImageBase(
        path = path,
        target = target,
        cropThumbnails = cropThumbnails,
        roundCorners = roundCorners,
        signature = signature,
        crossFadeDuration = crossFadeDuration,
    )
}

fun Context.updateWidgets() {
    val widgetIDs = AppWidgetManager.getInstance(applicationContext)
        ?.getAppWidgetIds(ComponentName(applicationContext, MyWidgetProvider::class.java))
        ?: return

    if (widgetIDs.isNotEmpty()) {
        Intent(applicationContext, MyWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIDs)
            sendBroadcast(this)
        }
    }
}

// based on https://github.com/sannies/mp4parser/blob/master/examples/src/main/java/com/google/code/mp4parser/example/PrintStructure.java
fun Context.parseFileChannel(
    path: String,
    fc: FileChannel,
    level: Int,
    start: Long,
    end: Long,
    callback: () -> Unit
) {
    val fileChannelContainers = arrayListOf("moov", "trak", "mdia", "minf", "udta", "stbl")
    try {
        var iteration = 0
        var currEnd = end
        fc.position(start)
        if (currEnd <= 0) {
            currEnd = start + fc.size()
        }

        while (currEnd - fc.position() > 8) {
            // just a check to avoid deadloop at some videos
            if (iteration++ > 50) {
                return
            }

            val begin = fc.position()
            val byteBuffer = ByteBuffer.allocate(8)
            fc.read(byteBuffer)
            byteBuffer.rewind()
            val size = IsoTypeReader.readUInt32(byteBuffer)
            val type = IsoTypeReader.read4cc(byteBuffer)
            val newEnd = begin + size

            if (type == "uuid") {
                val fis = FileInputStream(File(path))
                fis.skip(begin)

                val sb = StringBuilder()
                val buffer = ByteArray(1024)
                while (sb.length < size) {
                    val n = fis.read(buffer)
                    if (n != -1) {
                        sb.append(String(buffer, 0, n))
                    } else {
                        break
                    }
                }

                val xmlString = sb.toString().lowercase(Locale.getDefault())
                if (
                    xmlString.contains("gspherical:projectiontype>equirectangular")
                    || xmlString.contains("gspherical:projectiontype=\"equirectangular\"")
                ) {
                    callback.invoke()
                }
                return
            }

            if (fileChannelContainers.contains(type)) {
                parseFileChannel(path, fc, level + 1, begin + 8, newEnd, callback)
            }

            fc.position(newEnd)
        }
    } catch (ignored: Exception) {
    }
}

fun Context.addPathToDB(path: String) {
    ensureBackgroundThread {
        if (!getDoesFilePathExist(path) || isPathHiddenOrEncrypted(path)) {
            return@ensureBackgroundThread
        }

        val type = when {
            path.isVideoFast() -> TYPE_VIDEOS
            path.isGif() -> TYPE_GIFS
            path.isRawFast() -> TYPE_RAWS
            path.isSvg() -> TYPE_SVGS
            path.isPortrait() -> TYPE_PORTRAITS
            else -> TYPE_IMAGES
        }

        try {
            val isFavorite = favoritesDB.isFavorite(path)
            val videoDuration = if (type == TYPE_VIDEOS) getDuration(path) ?: 0 else 0
            val medium = Medium(
                id = null,
                name = path.getFilenameFromPath(),
                path = path,
                parentPath = path.getParentPath(),
                modified = System.currentTimeMillis(),
                taken = System.currentTimeMillis(),
                size = File(path).length(),
                type = type,
                videoDuration = videoDuration,
                isFavorite = isFavorite,
                deletedTS = 0L,
                mediaStoreId = 0L
            )

            mediaDB.insert(medium)
        } catch (ignored: Exception) {
        }
    }
}

fun Context.createDirectoryFromMedia(
    path: String,
    curMedia: ArrayList<Medium>,
    albumCovers: ArrayList<AlbumCover>,
    hiddenString: String,
    includedFolders: MutableSet<String>,
    getProperFileSize: Boolean,
    noMediaFolders: ArrayList<String>,
): Directory {
    val OTGPath = config.OTGPath
    val pathToCheck = if (path.isEmpty()) SHOW_ALL else path
    val currentGrouping = config.getFolderGrouping(pathToCheck)
    val sortedMedia: List<Medium> = if (currentGrouping and GROUP_BY_NONE == 0 && !config.scrollHorizontally) {
        MediaFetcher(this).groupMedia(curMedia, path).filterIsInstance<Medium>()
    } else {
        curMedia
    }
    var thumbnail: String? = null

    albumCovers.forEach {
        if (it.path == path && getDoesFilePathExist(it.tmb, OTGPath)) {
            thumbnail = it.tmb
        }
    }

    if (thumbnail == null) {
        val folderReachable = sortedMedia.isNotEmpty() && (
            isPathOnOTG(path) ||
                path == FAVORITES ||
                path == RECYCLE_BIN ||
                path.isVirtualAlbumPath() ||
                getDoesFilePathExist(path, OTGPath)
            )
        thumbnail = if (folderReachable) {
            sortedMedia.firstOrNull { getDoesFilePathExist(it.path, OTGPath) }?.path ?: ""
        } else {
            ""
        }
    }

    if (config.OTGPath.isNotEmpty() && thumbnail!!.startsWith(config.OTGPath)) {
        thumbnail = thumbnail!!.getOTGPublicPath(applicationContext)
    }

    val isSortingAscending = config.directorySorting.isSortingAscending()
    val dirName = checkAppendingHidden(path, hiddenString, includedFolders, noMediaFolders)
    val lastModified = if (isSortingAscending) {
        curMedia.minOfOrNull { it.modified } ?: 0L
    } else {
        curMedia.maxOfOrNull { it.modified } ?: 0L
    }

    val dateTaken = if (isSortingAscending) {
        curMedia.minOfOrNull { it.taken } ?: 0L
    } else {
        curMedia.maxOfOrNull { it.taken } ?: 0L
    }

    val size = curMedia.sumByLong { it.size }
    val mediaTypes = curMedia.getDirMediaTypes()
    val count = curMedia.size
    val sortValue = getDirectorySortingValue(curMedia, path, dirName, size, count)
    return Directory(
        id = null,
        path = path,
        tmb = thumbnail!!,
        name = dirName,
        mediaCnt = curMedia.size,
        modified = lastModified,
        taken = dateTaken,
        size = size,
        location = getPathLocation(path),
        types = mediaTypes,
        sortValue = sortValue
    )
}

fun Context.getDirectorySortingValue(
    media: ArrayList<Medium>,
    path: String,
    name: String,
    size: Long,
    count: Int
): String {
    val sorting = config.directorySorting
    val isAscending = sorting.isSortingAscending()
    return when {
        sorting and SORT_BY_NAME != 0 -> name
        sorting and SORT_BY_PATH != 0 -> path
        sorting and SORT_BY_SIZE != 0 -> size.toString()
        sorting and SORT_BY_COUNT != 0 -> count.toString()
        sorting and SORT_BY_DATE_MODIFIED != 0 -> {
            val v = (if (isAscending) media.minOfOrNull { it.modified } else media.maxOfOrNull { it.modified }) ?: return ""
            v.toString()
        }

        sorting and SORT_BY_DATE_TAKEN != 0 -> {
            val v = (if (isAscending) media.minOfOrNull { it.taken } else media.maxOfOrNull { it.taken }) ?: return ""
            v.toString()
        }

        else -> ""
    }
}

fun Context.updateDirectoryPath(path: String) {
    if (path.isVirtualAlbumPath() || CryptoManager.isEncryptedFolderPath(this, path)) {
        return
    }
    val mediaFetcher = MediaFetcher(applicationContext)
    val getImagesOnly = false
    val getVideosOnly = false
    val hiddenString = getString(R.string.hidden)
    val albumCovers = config.parseAlbumCovers()
    val includedFolders = config.includedFolders
    val noMediaFolders = getNoMediaFoldersSync()

    val sorting = config.getFolderSorting(path)
    val grouping = config.getFolderGrouping(path)
    val getProperDateTaken = config.directorySorting and SORT_BY_DATE_TAKEN != 0
            || sorting and SORT_BY_DATE_TAKEN != 0
            || grouping and GROUP_BY_DATE_TAKEN_DAILY != 0
            || grouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0

    val getProperLastModified = config.directorySorting and SORT_BY_DATE_MODIFIED != 0
            || sorting and SORT_BY_DATE_MODIFIED != 0
            || grouping and GROUP_BY_LAST_MODIFIED_DAILY != 0
            || grouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0

    val getProperFileSize = config.directorySorting and SORT_BY_SIZE != 0

    val lastModifieds = if (getProperLastModified) {
        mediaFetcher.getFolderLastModifieds(path)
    } else {
        HashMap()
    }

    val dateTakens = mediaFetcher.getFolderDateTakens(path)
    val favoritePaths = getFavoritePaths()
    val curMedia = mediaFetcher.getFilesFrom(
        curPath = path,
        isPickImage = getImagesOnly,
        isPickVideo = getVideosOnly,
        getProperDateTaken = getProperDateTaken,
        getProperLastModified = getProperLastModified,
        getProperFileSize = getProperFileSize,
        favoritePaths = favoritePaths,
        getVideoDurations = false,
        lastModifieds = lastModifieds,
        dateTakens = dateTakens,
        android11Files = null
    )
    val directory = createDirectoryFromMedia(
        path = path,
        curMedia = curMedia,
        albumCovers = albumCovers,
        hiddenString = hiddenString,
        includedFolders = includedFolders,
        getProperFileSize = getProperFileSize,
        noMediaFolders = noMediaFolders
    )
    updateDBDirectory(directory)
}

fun Context.getFileDateTaken(path: String): Long {
    val projection = arrayOf(
        Images.Media.DATE_TAKEN
    )

    val uri = Files.getContentUri("external")
    val selection = "${Images.Media.DATA} = ?"
    val selectionArgs = arrayOf(path)

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getLongValue(Images.Media.DATE_TAKEN)
            }
        }
    } catch (ignored: Exception) {
    }

    return 0L
}

fun Context.getCompressionFormatFromUri(uri: Uri): CompressFormat {
    val type = getMimeTypeFromUri(uri)
    return when {
        type.equals("image/png", true) -> CompressFormat.PNG
        type.equals("image/webp", true) -> CompressFormat.WEBP
        else -> CompressFormat.JPEG
    }
}

fun Context.resolveUriScheme(
    uri: Uri,
    onPath: (String) -> Unit,
    onContentUri: (Uri) -> Unit,
    onUnknown: () -> Unit = { toast(R.string.unknown_file_location) }
) {
    when (uri.scheme) {
        "file" -> onPath(uri.path!!)
        "content" -> onContentUri(uri)
        else -> onUnknown()
    }
}

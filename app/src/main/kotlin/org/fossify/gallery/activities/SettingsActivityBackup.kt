package org.fossify.gallery.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.TextUtils
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.checkAppIconColor
import org.fossify.commons.extensions.getAppIconColors
import org.fossify.commons.extensions.getCurrentFormattedDateTime
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFileOutputStream
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toBoolean
import org.fossify.commons.extensions.toFileDirItem
import org.fossify.commons.extensions.toInt
import org.fossify.commons.extensions.toStringSet
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.*
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.DeleteAutoBackupsDialog
import org.fossify.gallery.dialogs.ExportFavoritesDialog
import org.fossify.gallery.dialogs.ImportFavoritesConflictDialog
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.AlbumCover
import java.io.File
import java.io.InputStream
import java.io.OutputStream

internal fun SettingsActivity.setupAutoBackupFavoritesAndVirtualAlbums() {
    binding.settingsAutoBackupFavoritesAndVirtualAlbums.isChecked = config.autoBackupFavoritesAndVirtualAlbums
    var isCheckingExistingBackups = false
    binding.settingsAutoBackupFavoritesAndVirtualAlbumsHolder.setOnClickListener {
        if (isCheckingExistingBackups) {
            return@setOnClickListener
        }

        val currentlyEnabled = binding.settingsAutoBackupFavoritesAndVirtualAlbums.isChecked
        if (!currentlyEnabled) {
            binding.settingsAutoBackupFavoritesAndVirtualAlbums.isChecked = true
            config.autoBackupFavoritesAndVirtualAlbums = true
            config.hasCompletedInitialFavoritesRestore = true
            config.hasCompletedInitialVirtualAlbumsRestore = true
            ensureBackgroundThread {
                ReferenceBackupManager.saveFavorites(this)
                VirtualAlbumSyncManager.backupAllPublicAlbums(this)
            }
            return@setOnClickListener
        }

        isCheckingExistingBackups = true
        ensureBackgroundThread {
            val hasFavoritesBackup = ReferenceBackupManager.hasValidFavoritesBackup(this)
            val hasVirtualAlbumsBackup = VirtualAlbumSyncManager.hasValidPublicVirtualAlbumBackups(this)
            runOnUiThread {
                isCheckingExistingBackups = false
                if (isDestroyed || isFinishing) {
                    return@runOnUiThread
                }

                val disableAutoBackup = {
                    binding.settingsAutoBackupFavoritesAndVirtualAlbums.isChecked = false
                    config.autoBackupFavoritesAndVirtualAlbums = false
                    config.hasCompletedInitialFavoritesRestore = true
                    config.hasCompletedInitialVirtualAlbumsRestore = true
                }

                if (!hasFavoritesBackup && !hasVirtualAlbumsBackup) {
                    disableAutoBackup()
                    return@runOnUiThread
                }

                val targetText = when {
                    hasFavoritesBackup && hasVirtualAlbumsBackup -> getString(R.string.delete_auto_backup_target_both)
                    hasFavoritesBackup -> getString(R.string.delete_auto_backup_target_favorites)
                    else -> getString(R.string.delete_auto_backup_target_virtual_albums)
                }
                val message = getString(R.string.delete_auto_backup_confirm, targetText)
                DeleteAutoBackupsDialog(
                    activity = this,
                    messageText = message,
                    onYes = {
                        disableAutoBackup()
                        ensureBackgroundThread {
                            if (hasFavoritesBackup) {
                                ReferenceBackupManager.deleteFavoritesBackup(this)
                            }
                            if (hasVirtualAlbumsBackup) {
                                VirtualAlbumSyncManager.deletePublicVirtualAlbumBackups(this)
                            }
                        }
                    },
                    onNo = {
                        disableAutoBackup()
                    }
                )
            }
        }
    }
}

internal fun SettingsActivity.setupExportFavorites() {
    binding.settingsExportFavoritesHolder.setOnClickListener {
        ensureBackgroundThread {
            val hasExportData = FavoritesAndVirtualAlbumsBackupManager.hasDataToExport(this)
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                if (!hasExportData) {
                    toast(org.fossify.commons.R.string.no_items_found)
                    return@runOnUiThread
                }
                startExportFavoritesFlow()
            }
        }
    }
}

private fun SettingsActivity.startExportFavoritesFlow() {
    if (isQPlus()) {
        ExportFavoritesDialog(this, getExportFavoritesFilename(), true) { _, filename ->
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, filename)
                addCategory(Intent.CATEGORY_OPENABLE)

                try {
                    startActivityForResult(this, SettingsActivity.SELECT_EXPORT_FAVORITES_FILE_INTENT)
                } catch (_: ActivityNotFoundException) {
                    toast(org.fossify.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        }
    } else {
        handlePermission(PERMISSION_WRITE_STORAGE) {
            if (it) {
                ExportFavoritesDialog(this, getExportFavoritesFilename(), false) { path, _ ->
                    val file = File(path)
                    getFileOutputStream(file.toFileDirItem(this), true) { stream ->
                        exportFavoritesTo(stream)
                    }
                }
            }
        }
    }
}

internal fun SettingsActivity.exportFavoritesTo(outputStream: OutputStream?) {
    if (outputStream == null) {
        toast(org.fossify.commons.R.string.unknown_error_occurred)
        return
    }

    ensureBackgroundThread {
        try {
            val exported = FavoritesAndVirtualAlbumsBackupManager.exportToStream(this, outputStream)
            toast(
                if (exported) {
                    org.fossify.commons.R.string.exporting_successful
                } else {
                    org.fossify.commons.R.string.no_items_found
                }
            )
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}

private fun SettingsActivity.getExportFavoritesFilename(): String {
    val appName = baseConfig.appId.removeSuffix(".debug").removePrefix("org.fossify.")
    return "$appName-favorites_${getCurrentFormattedDateTime()}"
}

internal fun SettingsActivity.setupImportFavorites() {
    binding.settingsImportFavoritesHolder.setOnClickListener {
        if (isQPlus()) {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream",
                        "text/plain"
                    )
                )
                try {
                    startActivityForResult(this, SettingsActivity.SELECT_IMPORT_FAVORITES_FILE_INTENT)
                } catch (_: ActivityNotFoundException) {
                    toast(org.fossify.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        } else {
            handlePermission(PERMISSION_READ_STORAGE) {
                if (it) {
                    FilePickerDialog(this) {
                        ensureBackgroundThread {
                            importFavorites(File(it).inputStream())
                        }
                    }
                }
            }
        }
    }
}

internal fun SettingsActivity.importFavorites(inputStream: InputStream?) {
    if (inputStream == null) {
        toast(org.fossify.commons.R.string.unknown_error_occurred)
        return
    }

    ensureBackgroundThread {
        val bytes = try {
            inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            showErrorToast(e)
            return@ensureBackgroundThread
        }

        val pkg = FavoritesAndVirtualAlbumsBackupManager.parseImportPackage(bytes)
        if (pkg.favorites.isEmpty() && pkg.virtualAlbums.isEmpty()) {
            toast(org.fossify.commons.R.string.no_entries_for_importing)
            return@ensureBackgroundThread
        }

        val shouldPromptConflict =
            pkg.favorites.isNotEmpty() && FavoritesAndVirtualAlbumsBackupManager.hasExistingFavorites(this)

        if (shouldPromptConflict) {
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                ImportFavoritesConflictDialog(
                    activity = this,
                    onConfirm = { mode -> applyFavoritesAndAlbumsImport(pkg, mode) }
                )
            }
        } else {
            applyFavoritesAndAlbumsImport(pkg, FavoritesImportMode.MERGE)
        }
    }
}

private fun SettingsActivity.applyFavoritesAndAlbumsImport(pkg: ParsedImportPackage, favoritesMode: FavoritesImportMode) {
    ensureBackgroundThread {
        val result = FavoritesAndVirtualAlbumsBackupManager.applyImport(this, pkg, favoritesMode)
        val hasImportedAnything =
            result.processedFavoritesCount > 0 || result.importedVirtualAlbumsCount > 0
        toast(
            if (hasImportedAnything) {
                org.fossify.commons.R.string.importing_successful
            } else {
                org.fossify.commons.R.string.no_entries_for_importing
            }
        )
    }
}

internal fun SettingsActivity.setupExportSettings() {
    binding.settingsExportHolder.setOnClickListener {
        val configItems = LinkedHashMap<String, Any>().apply {
            put(TEXT_COLOR, config.textColor)
            put(BACKGROUND_COLOR, config.backgroundColor)
            put(PRIMARY_COLOR, config.primaryColor)
            put(ACCENT_COLOR, config.accentColor)
            put(APP_ICON_COLOR, config.appIconColor)
            put(USE_ENGLISH, config.useEnglish)
            put(WAS_USE_ENGLISH_TOGGLED, config.wasUseEnglishToggled)
            put(WIDGET_BG_COLOR, config.widgetBgColor)
            put(WIDGET_TEXT_COLOR, config.widgetTextColor)
            put(DATE_FORMAT, config.dateFormat)
            put(USE_24_HOUR_FORMAT, config.use24HourFormat)
            put(INCLUDED_FOLDERS, TextUtils.join(",", config.includedFolders))
            put(EXCLUDED_FOLDERS, TextUtils.join(",", config.excludedFolders))
            put(SHOW_HIDDEN_MEDIA, config.showHiddenMedia)
            put(BLOCK_VAULT_SCREENSHOTS, config.blockVaultScreenshots)
            put(HIDE_VAULT_KEY_PRESSES, config.hideVaultKeyPresses)
            put(AUTO_BIOMETRIC_PROMPT_ON_VAULT_RETURN, config.autoBiometricPromptOnVaultReturn)
            put(UNLOCK_TARGET_MS, config.unlockTargetMs)
            put(ARGON2_CALIBRATION_CLAMPED, config.argon2CalibrationClamped)
            put(LAST_CALIBRATED_MEMORY_KB, config.lastCalibratedMemoryKb)
            put(LAST_CALIBRATED_ITERATIONS, config.lastCalibratedIterations)
            put(FILE_LOADING_PRIORITY, config.fileLoadingPriority)
            put(AUTOPLAY_VIDEOS, config.autoplayVideos)
            put(REMEMBER_LAST_VIDEO_POSITION, config.rememberLastVideoPosition)
            put(LOOP_VIDEOS, config.loopVideos)
            put(GESTURE_VIDEO_PLAYER, config.gestureVideoPlayer)
            put(VIDEO_PLAYER_TYPE, config.videoPlayerType)
            put(VIDEO_DOUBLE_TAP_FAST_FORWARD, config.videoDoubleTapFastForward)
            put(ALLOW_VIDEO_GESTURES, config.allowVideoGestures)
            put(ANIMATE_GIFS, config.animateGifs)
            put(CROP_THUMBNAILS, config.cropThumbnails)
            put(SHOW_THUMBNAIL_VIDEO_DURATION, config.showThumbnailVideoDuration)
            put(SHOW_THUMBNAIL_FILE_TYPES, config.showThumbnailFileTypes)
            put(MARK_FAVORITE_ITEMS, config.markFavoriteItems)
            put(SCROLL_HORIZONTALLY, config.scrollHorizontally)
            put(ENABLE_PULL_TO_REFRESH, config.enablePullToRefresh)
            put(MAX_BRIGHTNESS, config.maxBrightness)
            put(ULTRA_HDR_RENDERING, config.ultraHdrRendering)
            put(BLACK_BACKGROUND, config.blackBackground)
            put(HIDE_SYSTEM_UI, config.hideSystemUI)
            put(ALLOW_INSTANT_CHANGE, config.allowInstantChange)
            put(KEEP_SCREEN_ON, config.keepScreenOn)
            put(ALLOW_PHOTO_GESTURES, config.allowPhotoGestures)
            put(ALLOW_DOWN_GESTURE, config.allowDownGesture)
            put(ALLOW_ROTATING_WITH_GESTURES, config.allowRotatingWithGestures)
            put(SHOW_NOTCH, config.showNotch)
            put(SCREEN_ROTATION, config.screenRotation)
            put(ALLOW_ZOOMING_IMAGES, config.allowZoomingImages)
            put(SHOW_HIGHEST_QUALITY, config.showHighestQuality)
            put(ALLOW_ONE_TO_ONE_ZOOM, config.allowOneToOneZoom)
            put(SHOW_EXTENDED_DETAILS, config.showExtendedDetails)
            put(HIDE_EXTENDED_DETAILS, config.hideExtendedDetails)
            put(EXTENDED_DETAILS, config.extendedDetails)
            put(SHOW_EXTRA_VIEWER_ACTIONS, config.showExtraViewerActions)
            put(EXTRA_VIEWER_ACTIONS, config.extraViewerActions)
            put(DELETE_EMPTY_FOLDERS, config.deleteEmptyFolders)
            put(CONFLICT_HANDLING_MODE, config.conflictHandlingMode)
            put(FAST_LARGE_DUPLICATE_CHECK, config.fastLargeDuplicateCheck)
            put(NOTIFICATIONS_ENABLED, config.notificationsEnabled)
            put(NOTIFY_COPY_MOVE, config.notifyCopyMove)
            put(NOTIFY_CRYPTO, config.notifyCrypto)
            put(NOTIFY_DELETE, config.notifyDelete)
            put(NOTIFY_VIRTUAL_ALBUM, config.notifyVirtualAlbum)
            put(DATE_TIE_BREAK_MODE, config.dateTieBreakMode)
            put(ALBUM_CRYPTO_CONFLICT_MODE, config.albumCryptoConflictMode)
            put(SKIPPED_ENCRYPT_FILES_MODE, config.skippedEncryptFilesMode)
            put(KEEP_LAST_MODIFIED, config.keepLastModified)
            put(SKIP_DELETE_CONFIRMATION, config.skipDeleteConfirmation)
            put(DELETE_ORIGINAL_FROM_FAVORITES_AND_VIRTUAL_ALBUMS, config.deleteOriginalFromFavoritesAndVirtualAlbums)
            put(ALLOW_BATCH_RENAMING, config.allowBatchRenaming)
            put(BOTTOM_ACTIONS, config.bottomActions)
            put(VISIBLE_BOTTOM_ACTIONS, config.visibleBottomActions)
            put(SHOW_BOTTOM_ACTION_NAMES, config.showBottomActionNames)
            put(USE_RECYCLE_BIN, config.useRecycleBin)
            put(SHOW_RECYCLE_BIN_AT_FOLDERS, config.showRecycleBinAtFolders)
            put(SHOW_RECYCLE_BIN_LAST, config.showRecycleBinLast)
            put(SORT_ORDER, config.sorting)
            put(DIRECTORY_SORT_ORDER, config.directorySorting)
            put(FILES_SORT_ORDER, config.filesSorting)
            put(GROUP_BY, config.groupBy)
            put(GROUP_DIRECT_SUBFOLDERS, config.groupDirectSubfolders)
            put(PINNED_FOLDERS, TextUtils.join(",", config.pinnedFolders))
            put(DISPLAY_FILE_NAMES, config.displayFileNames)
            put(FILTER_MEDIA, config.filterMedia)
            put(DIR_COLUMN_CNT, config.dirColumnCnt)
            put(MEDIA_COLUMN_CNT, config.mediaColumnCnt)
            put(SHOW_ALL, config.showAll)
            put(SHOW_WIDGET_FOLDER_NAME, config.showWidgetFolderName)
            put(VIEW_TYPE_FILES, config.viewTypeFiles)
            put(VIEW_TYPE_FOLDERS, config.viewTypeFolders)
            put(SLIDESHOW_INTERVAL, config.slideshowInterval)
            put(SLIDESHOW_INCLUDE_VIDEOS, config.slideshowIncludeVideos)
            put(SLIDESHOW_INCLUDE_GIFS, config.slideshowIncludeGIFs)
            put(SLIDESHOW_RANDOM_ORDER, config.slideshowRandomOrder)
            put(SLIDESHOW_MOVE_BACKWARDS, config.slideshowMoveBackwards)
            put(SLIDESHOW_LOOP, config.loopSlideshow)
            put(LAST_EDITOR_CROP_ASPECT_RATIO, config.lastEditorCropAspectRatio)
            put(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_X, config.lastEditorCropOtherAspectRatioX)
            put(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_Y, config.lastEditorCropOtherAspectRatioY)
            put(LAST_CONFLICT_RESOLUTION, config.lastConflictResolution)
            put(LAST_CONFLICT_APPLY_TO_ALL, config.lastConflictApplyToAll)
            put(EDITOR_BRUSH_COLOR, config.editorBrushColor)
            put(EDITOR_BRUSH_HARDNESS, config.editorBrushHardness)
            put(EDITOR_BRUSH_SIZE, config.editorBrushSize)
            put(ALBUM_COVERS, config.albumCovers)
            put(FOLDER_THUMBNAIL_STYLE, config.folderStyle)
            put(FOLDER_MEDIA_COUNT, config.showFolderMediaCount)
            put(LIMIT_FOLDER_TITLE, config.limitFolderTitle)
            put(SHOW_VIRTUAL_ALBUM_ICON, config.showVirtualAlbumIcon)
            put(SHOW_FAVORITES_ALBUM_ICON, config.showFavoritesAlbumIcon)
            put(SHOW_EXCLUDED_ALBUM_ICON, config.showExcludedAlbumIcon)
            put(SHOW_HIDDEN_ALBUM_ICON, config.showHiddenAlbumIcon)
            put(THUMBNAIL_SPACING, config.thumbnailSpacing)
            put(FILE_ROUNDED_CORNERS, config.fileRoundedCorners)
            put(HOME_SCREEN_VIEW_ON_LAUNCH, config.homeScreenViewOnLaunch)
        }

        exportSettings(configItems)
    }
}

internal fun SettingsActivity.setupImportSettings() {
    binding.settingsImportHolder.setOnClickListener {
        if (isQPlus()) {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                startActivityForResult(this, SettingsActivity.PICK_IMPORT_SOURCE_INTENT)
            }
        } else {
            handlePermission(PERMISSION_READ_STORAGE) {
                if (it) {
                    FilePickerDialog(this) {
                        ensureBackgroundThread {
                            parseFile(File(it).inputStream())
                        }
                    }
                }
            }
        }
    }
}

internal fun SettingsActivity.parseFile(inputStream: InputStream?) {
    if (inputStream == null) {
        toast(org.fossify.commons.R.string.unknown_error_occurred)
        return
    }

    var importedItems = 0
    val configValues = LinkedHashMap<String, Any>()
    inputStream.bufferedReader().use {
        while (true) {
            try {
                val line = it.readLine() ?: break
                val split = line.split("=".toRegex(), 2)
                if (split.size == 2) {
                    configValues[split[0]] = split[1]
                }
                importedItems++
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    for ((key, value) in configValues) {
        when (key) {
            TEXT_COLOR -> config.textColor = value.toInt()
            BACKGROUND_COLOR -> config.backgroundColor = value.toInt()
            PRIMARY_COLOR -> config.primaryColor = value.toInt()
            ACCENT_COLOR -> config.accentColor = value.toInt()
            APP_ICON_COLOR -> {
                if (getAppIconColors().contains(value.toInt())) {
                    config.appIconColor = value.toInt()
                    checkAppIconColor()
                }
            }

            USE_ENGLISH -> config.useEnglish = value.toBoolean()
            WAS_USE_ENGLISH_TOGGLED -> config.wasUseEnglishToggled = value.toBoolean()
            WIDGET_BG_COLOR -> config.widgetBgColor = value.toInt()
            WIDGET_TEXT_COLOR -> config.widgetTextColor = value.toInt()
            DATE_FORMAT -> config.dateFormat = value.toString()
            USE_24_HOUR_FORMAT -> config.use24HourFormat = value.toBoolean()
            INCLUDED_FOLDERS -> config.addIncludedFolders(value.toStringSet())
            EXCLUDED_FOLDERS -> config.addExcludedFolders(value.toStringSet())
            SHOW_HIDDEN_MEDIA -> config.showHiddenMedia = value.toBoolean()
            BLOCK_VAULT_SCREENSHOTS -> config.blockVaultScreenshots = value.toBoolean()
            HIDE_VAULT_KEY_PRESSES -> config.hideVaultKeyPresses = value.toBoolean()
            AUTO_BIOMETRIC_PROMPT_ON_VAULT_RETURN -> config.autoBiometricPromptOnVaultReturn = value.toBoolean()
            UNLOCK_TARGET_MS -> config.unlockTargetMs = value.toInt()
            ARGON2_CALIBRATION_CLAMPED -> config.argon2CalibrationClamped = value.toBoolean()
            LAST_CALIBRATED_MEMORY_KB -> config.lastCalibratedMemoryKb = value.toInt()
            LAST_CALIBRATED_ITERATIONS -> config.lastCalibratedIterations = value.toInt()
            FILE_LOADING_PRIORITY -> config.fileLoadingPriority = value.toInt()
            AUTOPLAY_VIDEOS -> config.autoplayVideos = value.toBoolean()
            REMEMBER_LAST_VIDEO_POSITION -> config.rememberLastVideoPosition = value.toBoolean()
            LOOP_VIDEOS -> config.loopVideos = value.toBoolean()
            GESTURE_VIDEO_PLAYER -> config.gestureVideoPlayer = value.toBoolean()
            VIDEO_PLAYER_TYPE -> config.videoPlayerType = value.toInt()
            VIDEO_DOUBLE_TAP_FAST_FORWARD -> config.videoDoubleTapFastForward = value.toInt()
            ALLOW_VIDEO_GESTURES -> config.allowVideoGestures = value.toBoolean()
            ANIMATE_GIFS -> config.animateGifs = value.toBoolean()
            CROP_THUMBNAILS -> config.cropThumbnails = value.toBoolean()
            SHOW_THUMBNAIL_VIDEO_DURATION -> config.showThumbnailVideoDuration = value.toBoolean()
            SHOW_THUMBNAIL_FILE_TYPES -> config.showThumbnailFileTypes = value.toBoolean()
            MARK_FAVORITE_ITEMS -> config.markFavoriteItems = value.toBoolean()
            SCROLL_HORIZONTALLY -> config.scrollHorizontally = value.toBoolean()
            ENABLE_PULL_TO_REFRESH -> config.enablePullToRefresh = value.toBoolean()
            MAX_BRIGHTNESS -> config.maxBrightness = value.toBoolean()
            ULTRA_HDR_RENDERING -> config.ultraHdrRendering = value.toBoolean()
            BLACK_BACKGROUND -> config.blackBackground = value.toBoolean()
            HIDE_SYSTEM_UI -> config.hideSystemUI = value.toBoolean()
            ALLOW_INSTANT_CHANGE -> config.allowInstantChange = value.toBoolean()
            KEEP_SCREEN_ON -> config.keepScreenOn = value.toBoolean()
            ALLOW_PHOTO_GESTURES -> config.allowPhotoGestures = value.toBoolean()
            ALLOW_DOWN_GESTURE -> config.allowDownGesture = value.toBoolean()
            ALLOW_ROTATING_WITH_GESTURES -> config.allowRotatingWithGestures = value.toBoolean()
            SHOW_NOTCH -> config.showNotch = value.toBoolean()
            SCREEN_ROTATION -> config.screenRotation = value.toInt()
            ALLOW_ZOOMING_IMAGES -> config.allowZoomingImages = value.toBoolean()
            SHOW_HIGHEST_QUALITY -> config.showHighestQuality = value.toBoolean()
            ALLOW_ONE_TO_ONE_ZOOM -> config.allowOneToOneZoom = value.toBoolean()
            SHOW_EXTENDED_DETAILS -> config.showExtendedDetails = value.toBoolean()
            HIDE_EXTENDED_DETAILS -> config.hideExtendedDetails = value.toBoolean()
            EXTENDED_DETAILS -> config.extendedDetails = value.toInt()
            SHOW_EXTRA_VIEWER_ACTIONS -> config.showExtraViewerActions = value.toBoolean()
            EXTRA_VIEWER_ACTIONS -> config.extraViewerActions = value.toInt()
            DELETE_EMPTY_FOLDERS -> config.deleteEmptyFolders = value.toBoolean()
            CONFLICT_HANDLING_MODE -> config.conflictHandlingMode = value.toInt()
            FAST_LARGE_DUPLICATE_CHECK -> config.fastLargeDuplicateCheck = value.toBoolean()
            NOTIFICATIONS_ENABLED -> config.notificationsEnabled = value.toBoolean()
            NOTIFY_COPY_MOVE -> config.notifyCopyMove = value.toBoolean()
            NOTIFY_CRYPTO -> config.notifyCrypto = value.toBoolean()
            NOTIFY_DELETE -> config.notifyDelete = value.toBoolean()
            NOTIFY_VIRTUAL_ALBUM -> config.notifyVirtualAlbum = value.toBoolean()
            DATE_TIE_BREAK_MODE -> config.dateTieBreakMode = value.toInt()
            ALBUM_CRYPTO_CONFLICT_MODE -> config.albumCryptoConflictMode = value.toInt()
            SKIPPED_ENCRYPT_FILES_MODE -> config.skippedEncryptFilesMode = value.toInt()
            KEEP_LAST_MODIFIED -> config.keepLastModified = value.toBoolean()
            SKIP_DELETE_CONFIRMATION -> config.skipDeleteConfirmation = value.toBoolean()
            DELETE_ORIGINAL_FROM_FAVORITES_AND_VIRTUAL_ALBUMS -> config.deleteOriginalFromFavoritesAndVirtualAlbums = value.toBoolean()
            ALLOW_BATCH_RENAMING -> config.allowBatchRenaming = value.toBoolean()
            BOTTOM_ACTIONS -> config.bottomActions = value.toBoolean()
            VISIBLE_BOTTOM_ACTIONS -> config.visibleBottomActions = value.toInt()
            SHOW_BOTTOM_ACTION_NAMES -> config.showBottomActionNames = value.toBoolean()
            USE_RECYCLE_BIN -> config.useRecycleBin = value.toBoolean()
            SHOW_RECYCLE_BIN_AT_FOLDERS -> config.showRecycleBinAtFolders = value.toBoolean()
            SHOW_RECYCLE_BIN_LAST -> config.showRecycleBinLast = value.toBoolean()
            SORT_ORDER -> config.sorting = value.toInt()
            DIRECTORY_SORT_ORDER -> config.directorySorting = value.toInt()
            FILES_SORT_ORDER -> config.filesSorting = value.toInt()
            GROUP_BY -> config.groupBy = value.toInt()
            GROUP_DIRECT_SUBFOLDERS -> config.groupDirectSubfolders = value.toBoolean()
            PINNED_FOLDERS -> config.addPinnedFolders(value.toStringSet())
            DISPLAY_FILE_NAMES -> config.displayFileNames = value.toBoolean()
            FILTER_MEDIA -> config.filterMedia = value.toInt()
            DIR_COLUMN_CNT -> config.dirColumnCnt = value.toInt()
            MEDIA_COLUMN_CNT -> config.mediaColumnCnt = value.toInt()
            SHOW_ALL -> config.showAll = value.toBoolean()
            SHOW_WIDGET_FOLDER_NAME -> config.showWidgetFolderName = value.toBoolean()
            VIEW_TYPE_FILES -> config.viewTypeFiles = value.toInt()
            VIEW_TYPE_FOLDERS -> config.viewTypeFolders = value.toInt()
            SLIDESHOW_INTERVAL -> config.slideshowInterval = value.toInt()
            SLIDESHOW_INCLUDE_VIDEOS -> config.slideshowIncludeVideos = value.toBoolean()
            SLIDESHOW_INCLUDE_GIFS -> config.slideshowIncludeGIFs = value.toBoolean()
            SLIDESHOW_RANDOM_ORDER -> config.slideshowRandomOrder = value.toBoolean()
            SLIDESHOW_MOVE_BACKWARDS -> config.slideshowMoveBackwards = value.toBoolean()
            SLIDESHOW_LOOP -> config.loopSlideshow = value.toBoolean()
            LAST_EDITOR_CROP_ASPECT_RATIO -> config.lastEditorCropAspectRatio = value.toInt()
            LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_X -> config.lastEditorCropOtherAspectRatioX = value.toString().toFloat()
            LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_Y -> config.lastEditorCropOtherAspectRatioY = value.toString().toFloat()
            LAST_CONFLICT_RESOLUTION -> config.lastConflictResolution = value.toInt()
            LAST_CONFLICT_APPLY_TO_ALL -> config.lastConflictApplyToAll = value.toBoolean()
            EDITOR_BRUSH_COLOR -> config.editorBrushColor = value.toInt()
            EDITOR_BRUSH_HARDNESS -> config.editorBrushHardness = value.toString().toFloat()
            EDITOR_BRUSH_SIZE -> config.editorBrushSize = value.toString().toFloat()
            FOLDER_THUMBNAIL_STYLE -> config.folderStyle = value.toInt()
            FOLDER_MEDIA_COUNT -> config.showFolderMediaCount = value.toInt()
            LIMIT_FOLDER_TITLE -> config.limitFolderTitle = value.toBoolean()
            SHOW_VIRTUAL_ALBUM_ICON -> config.showVirtualAlbumIcon = value.toBoolean()
            SHOW_FAVORITES_ALBUM_ICON -> config.showFavoritesAlbumIcon = value.toBoolean()
            SHOW_EXCLUDED_ALBUM_ICON -> config.showExcludedAlbumIcon = value.toBoolean()
            SHOW_HIDDEN_ALBUM_ICON -> config.showHiddenAlbumIcon = value.toBoolean()
            THUMBNAIL_SPACING -> config.thumbnailSpacing = value.toInt()
            FILE_ROUNDED_CORNERS -> config.fileRoundedCorners = value.toBoolean()
            HOME_SCREEN_VIEW_ON_LAUNCH -> config.homeScreenViewOnLaunch = value.toInt()
            ALBUM_COVERS -> {
                val existingCovers = config.parseAlbumCovers()
                val existingCoverPaths = existingCovers.map { it.path }.toMutableList() as ArrayList<String>

                val listType = object : TypeToken<List<AlbumCover>>() {}.type
                val covers = Gson().fromJson<ArrayList<AlbumCover>>(value.toString(), listType) ?: ArrayList(1)
                covers.filter { !existingCoverPaths.contains(it.path) && getDoesFilePathExist(it.tmb) }.forEach {
                    existingCovers.add(it)
                }

                config.albumCovers = Gson().toJson(existingCovers)
            }
        }
    }

    toast(if (configValues.size > 0) org.fossify.commons.R.string.settings_imported_successfully else org.fossify.commons.R.string.no_entries_for_importing)
    runOnUiThread {
        setupSettingItems()
    }
}

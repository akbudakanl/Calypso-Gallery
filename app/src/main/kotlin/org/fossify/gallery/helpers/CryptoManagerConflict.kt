package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.updateVirtualAlbumItemPath
import java.io.*
import org.fossify.gallery.models.DateTaken
import org.fossify.gallery.helpers.CryptoManager.TargetPlainIndex
import org.fossify.gallery.helpers.CryptoManager.TargetEncryptedIndex
import org.fossify.gallery.helpers.CryptoManager.TransferConflictStatus
import org.fossify.gallery.helpers.CryptoManager.TransferConflictCheck
import org.fossify.gallery.helpers.CryptoManager.SmartConflictDifference
import org.fossify.gallery.helpers.CryptoManager.SmartConflictComparison
import org.fossify.gallery.helpers.operations.AtomicStaging
import org.fossify.gallery.helpers.operations.OperationContext
import org.fossify.gallery.helpers.operations.OperationJournal
import org.fossify.gallery.helpers.operations.TransientOperationWaiter

/**
 * Extracted from [CryptoManager]. Feature group: CryptoManagerConflict.
 */

/**
 * Classifies why two conflicting files differ for SmartFileConflictDialog:
 * byte/size mismatch takes precedence over timestamp differences.
 */
fun CryptoManager.compareFilesForSmartConflict(
    sourceFile: File,
    existingFile: File,
    fastSampling: Boolean = false,
    context: Context? = null
): SmartConflictComparison {
    val isSrcEnc = isEncryptedFile(sourceFile.name)
    val isExistEnc = isEncryptedFile(existingFile.name)
    val srcMeta = if (isSrcEnc && sourceFile.isFile) {
        readMetadata(sourceFile, includeThumbnail = false, cacheResult = false)
    } else {
        null
    }
    val existMeta = if (isExistEnc && existingFile.isFile) {
        readMetadata(existingFile, includeThumbnail = false, cacheResult = false)
    } else {
        null
    }

    val srcSize = srcMeta?.originalSize ?: if (sourceFile.isFile) sourceFile.length() else -1L
    val existSize = existMeta?.originalSize ?: if (existingFile.isFile) existingFile.length() else -1L
    val srcLastMod = srcMeta?.lastModified ?: if (sourceFile.exists()) sourceFile.lastModified() else 0L
    val existLastMod = existMeta?.lastModified ?: if (existingFile.exists()) existingFile.lastModified() else 0L
    val srcDateTaken = srcMeta?.dateTaken ?: if (sourceFile.isFile) {
        resolvePlaintextDateTaken(context, sourceFile, srcLastMod)
    } else {
        0L
    }
    val existDateTaken = existMeta?.dateTaken ?: if (existingFile.isFile) {
        resolvePlaintextDateTaken(context, existingFile, existLastMod)
    } else {
        0L
    }

    val eitherHasSeparateTaken =
        (srcDateTaken > 0L && !areTimestampsEquivalent(srcDateTaken, srcLastMod)) ||
            (existDateTaken > 0L && !areTimestampsEquivalent(existDateTaken, existLastMod))

    val bytesMatch = when {
        !sourceFile.isFile || !existingFile.isFile -> false
        isSrcEnc && srcMeta == null -> false
        isExistEnc && existMeta == null -> false
        srcSize < 0L || existSize < 0L || srcSize != existSize -> false
        isSrcEnc && isExistEnc -> areEncryptedFilesIdenticalContent(
            fileA = sourceFile,
            metaA = srcMeta!!,
            fileB = existingFile,
            metaB = existMeta!!,
            fastSampling = fastSampling
        )
        !isSrcEnc && !isExistEnc -> arePlainFilesIdentical(
            fileA = sourceFile,
            fileB = existingFile,
            fastSampling = fastSampling
        )
        isSrcEnc && !isExistEnc -> arePlainAndEncryptedIdentical(
            plainFile = existingFile,
            encryptedFile = sourceFile,
            meta = srcMeta!!,
            verifyIntegrity = false,
            fastSampling = fastSampling
        )
        else -> arePlainAndEncryptedIdentical(
            plainFile = sourceFile,
            encryptedFile = existingFile,
            meta = existMeta!!,
            verifyIntegrity = false,
            fastSampling = fastSampling
        )
    }

    val difference = if (!bytesMatch) {
        SmartConflictDifference.CONTENT_DIFFERS
    } else {
        val modifiedDiffers = !areTimestampsEquivalent(srcLastMod, existLastMod)
        val rawTakenDiffers = srcDateTaken > 0L &&
            existDateTaken > 0L &&
            !areTimestampsEquivalent(srcDateTaken, existDateTaken)
        val takenDiffers = rawTakenDiffers && eitherHasSeparateTaken
        when {
            takenDiffers && modifiedDiffers -> SmartConflictDifference.BOTH_DATES_DIFFER
            takenDiffers -> SmartConflictDifference.DATE_TAKEN_DIFFERS
            modifiedDiffers -> SmartConflictDifference.MODIFIED_DATE_DIFFERS
            else -> SmartConflictDifference.IDENTICAL_MATCH
        }
    }

    return SmartConflictComparison(
        difference = difference,
        sourceSize = srcSize,
        existingSize = existSize,
        sourceLastModified = srcLastMod,
        existingLastModified = existLastMod,
        sourceDateTaken = srcDateTaken,
        existingDateTaken = existDateTaken,
        hasDistinctDateTaken = eitherHasSeparateTaken
    )
}


internal fun CryptoManager.resolveTransferDesiredName(
    sourceFile: File,
    targetDir: File,
    desiredPlainName: String? = null
): Triple<Boolean, EncryptedFileMetadata?, String>? {
    if (!sourceFile.isFile || !targetDir.exists() || !targetDir.isDirectory) {
        return null
    }
    val srcParentCanon = runCatching { sourceFile.parentFile?.canonicalPath }.getOrNull()
    val targetCanon = runCatching { targetDir.canonicalPath }.getOrNull()
    if (srcParentCanon != null && srcParentCanon == targetCanon) {
        return null
    }

    val isSourceEncrypted = isEncryptedFile(sourceFile.name)
    val sourceMeta = if (isSourceEncrypted) {
        readMetadata(sourceFile, includeThumbnail = false, cacheResult = false)
    } else {
        null
    }
    if (isSourceEncrypted && sourceMeta == null) {
        return null
    }

    val desiredName = desiredPlainName ?: sourceMeta?.originalName ?: sourceFile.name
    return Triple(isSourceEncrypted, sourceMeta, desiredName)
}


/**
 * Checks only whether a file with the same target filename already exists at [targetDir],
 * without comparing sizes, timestamps, or contents (Fossify standard conflict mode).
 */
fun CryptoManager.inspectStandardFilenameConflict(
    sourceFile: File,
    targetDir: File,
    isTargetEncrypted: Boolean,
    plainIndex: TargetPlainIndex? = null,
    encryptedIndex: TargetEncryptedIndex? = null,
    desiredPlainName: String? = null
): String? {
    val (_, _, desiredName) = resolveTransferDesiredName(sourceFile, targetDir, desiredPlainName)
        ?: return null
    return if (isTargetEncrypted) {
        if (!isVaultUnlocked) return null
        val index = encryptedIndex ?: TargetEncryptedIndex(targetDir)
        if (index.findExactEntries(desiredName).isNotEmpty()) desiredName else null
    } else {
        val index = plainIndex ?: TargetPlainIndex(targetDir)
        if (index.findExact(desiredName) != null) desiredName else null
    }
}


fun CryptoManager.inspectTransferConflict(
    sourceFile: File,
    targetDir: File,
    isTargetEncrypted: Boolean,
    plainIndex: TargetPlainIndex? = null,
    encryptedIndex: TargetEncryptedIndex? = null,
    desiredPlainName: String? = null,
    fastSampling: Boolean = false,
    context: Context? = null
): TransferConflictCheck {
    val (isSourceEncrypted, sourceMeta, desiredName) = resolveTransferDesiredName(
        sourceFile,
        targetDir,
        desiredPlainName
    ) ?: return TransferConflictCheck(TransferConflictStatus.NO_CONFLICT)
    val sourceLastMod = sourceMeta?.lastModified ?: sourceFile.lastModified()
    val sourceOrigSize = sourceMeta?.originalSize ?: sourceFile.length()
    val sourceMediaType = sourceMeta?.mediaType ?: getMediaTypeForName(desiredName)
    var resolvedSourceDateTaken: Long? = sourceMeta?.dateTaken
    fun getSourceDateTaken(): Long {
        return resolvedSourceDateTaken ?: resolvePlaintextDateTaken(
            context = context,
            sourceFile = sourceFile,
            fallbackLastModified = sourceLastMod
        ).also { resolvedSourceDateTaken = it }
    }

    if (isTargetEncrypted) {
        if (!isVaultUnlocked) {
            return TransferConflictCheck(TransferConflictStatus.NO_CONFLICT)
        }
        val index = encryptedIndex ?: TargetEncryptedIndex(targetDir)
        val exactEntries = index.findExactEntries(desiredName)
        if (exactEntries.isEmpty()) {
            return TransferConflictCheck(TransferConflictStatus.NO_CONFLICT)
        }

        val candidates = index.findCandidates(desiredName, includeCollisionVariants = true)
        for ((existingBin, existingMeta) in candidates) {
            if (existingMeta.originalSize != sourceOrigSize || existingMeta.mediaType != sourceMediaType) {
                continue
            }
            if (context?.shouldGateDuplicatesByDate() != false &&
                !areMediaTimestampsEquivalent(
                    sourceLastMod,
                    getSourceDateTaken(),
                    existingMeta.lastModified,
                    existingMeta.dateTaken
                )
            ) {
                continue
            }
            val identical = if (isSourceEncrypted && sourceMeta != null) {
                areEncryptedFilesIdenticalContent(
                    sourceFile,
                    sourceMeta,
                    existingBin,
                    existingMeta,
                    fastSampling = fastSampling
                )
            } else {
                // Duplicate skip never deletes or trusts external data, so skip the redundant second MAC pass
                arePlainAndEncryptedIdentical(
                    sourceFile,
                    existingBin,
                    existingMeta,
                    verifyIntegrity = false,
                    fastSampling = fastSampling
                )
            }
            if (identical) {
                if (context != null) {
                    applyDuplicateDateToEncryptedFile(context, existingBin, sourceLastMod)
                }
                return TransferConflictCheck(TransferConflictStatus.IDENTICAL_SKIP, desiredName, existingBin)
            }
        }
        return TransferConflictCheck(TransferConflictStatus.NAME_CONFLICT, desiredName, exactEntries.firstOrNull()?.first)
    } else {
        val index = plainIndex ?: TargetPlainIndex(targetDir)
        val exactTarget = index.findExact(desiredName)
            ?: return TransferConflictCheck(TransferConflictStatus.NO_CONFLICT)

        val candidates = index.findCandidates(desiredName, includeCollisionVariants = true)
        for (existingPlain in candidates) {
            if (existingPlain.length() != sourceOrigSize) continue
            val existingLastMod = existingPlain.lastModified()
            val gateByDate = context?.shouldGateDuplicatesByDate() != false
            if (gateByDate && !areTimestampsEquivalent(sourceLastMod, existingLastMod)) continue
            val srcTaken = getSourceDateTaken()
            val existingTaken = resolvePlaintextDateTaken(context, existingPlain, existingLastMod)
            if (gateByDate && !areMediaTimestampsEquivalent(sourceLastMod, srcTaken, existingLastMod, existingTaken)) {
                continue
            }
            val identical = if (isSourceEncrypted && sourceMeta != null) {
                arePlainAndEncryptedIdentical(
                    existingPlain,
                    sourceFile,
                    sourceMeta,
                    verifyIntegrity = false,
                    fastSampling = fastSampling
                )
            } else {
                arePlainFilesIdentical(sourceFile, existingPlain, fastSampling = fastSampling)
            }
            if (identical) {
                if (context != null) {
                    applyDuplicateDateToPlainFile(context, existingPlain, sourceLastMod, srcTaken)
                }
                return TransferConflictCheck(TransferConflictStatus.IDENTICAL_SKIP, desiredName, existingPlain)
            }
        }
        return TransferConflictCheck(TransferConflictStatus.NAME_CONFLICT, desiredName, exactTarget)
    }
}


/**
 * Returns true if at least one (source, targetDir) pair already has a bit-identical
 * counterpart in its target directory.
 */
fun CryptoManager.hasAnyIdenticalTransferMatch(
    sourceTargetPairs: List<Pair<File, File>>,
    isTargetEncrypted: Boolean,
    fastSampling: Boolean = false,
    context: Context? = null
): Boolean {
    if (sourceTargetPairs.isEmpty()) return false
    val plainIndexes = HashMap<String, TargetPlainIndex>()
    val encIndexes = HashMap<String, TargetEncryptedIndex>()
    for ((srcFile, targetDir) in sourceTargetPairs) {
        if (!srcFile.isFile || !targetDir.exists() || !targetDir.isDirectory) continue
        val plainIndex = if (!isTargetEncrypted) {
            plainIndexes.getOrPut(targetDir.absolutePath) { TargetPlainIndex(targetDir) }
        } else {
            null
        }
        val encIndex = if (isTargetEncrypted) {
            encIndexes.getOrPut(targetDir.absolutePath) { TargetEncryptedIndex(targetDir) }
        } else {
            null
        }
        val check = inspectTransferConflict(
            sourceFile = srcFile,
            targetDir = targetDir,
            isTargetEncrypted = isTargetEncrypted,
            plainIndex = plainIndex,
            encryptedIndex = encIndex,
            fastSampling = fastSampling,
            context = context
        )
        if (check.status == TransferConflictStatus.IDENTICAL_SKIP) {
            return true
        }
    }
    return false
}


fun CryptoManager.findContentConflictInTarget(
    sourceFile: File,
    targetDir: File,
    isTargetEncrypted: Boolean,
    plainIndex: TargetPlainIndex? = null,
    encryptedIndex: TargetEncryptedIndex? = null,
    includeCollisionVariants: Boolean = true,
    context: Context? = null
): String? {
    if (!sourceFile.isFile || !targetDir.exists() || !targetDir.isDirectory) return null
    val srcParentCanon = runCatching { sourceFile.parentFile?.canonicalPath }.getOrNull()
    val targetCanon = runCatching { targetDir.canonicalPath }.getOrNull()
    if (srcParentCanon != null && srcParentCanon == targetCanon) return null

    val isSourceEncrypted = isEncryptedFile(sourceFile.name)
    val sourceMeta = if (isSourceEncrypted) {
        readMetadata(sourceFile, includeThumbnail = false, cacheResult = false)
    } else {
        null
    }
    if (isSourceEncrypted && sourceMeta == null) return null

    val desiredName = sourceMeta?.originalName ?: sourceFile.name
    val sourceLastMod = sourceMeta?.lastModified ?: sourceFile.lastModified()
    val sourceOrigSize = sourceMeta?.originalSize ?: sourceFile.length()
    val sourceMediaType = sourceMeta?.mediaType ?: getMediaTypeForName(desiredName)
    var resolvedSourceDateTaken: Long? = sourceMeta?.dateTaken
    fun getSourceDateTaken(): Long {
        return resolvedSourceDateTaken ?: resolvePlaintextDateTaken(
            context = context,
            sourceFile = sourceFile,
            fallbackLastModified = sourceLastMod
        ).also { resolvedSourceDateTaken = it }
    }

    if (isTargetEncrypted) {
        if (!isVaultUnlocked) return null
        val index = encryptedIndex ?: TargetEncryptedIndex(targetDir)
        val candidates = index.findCandidates(desiredName, includeCollisionVariants)

        for ((existingBin, existingMeta) in candidates) {
            if (existingMeta.originalSize != sourceOrigSize || existingMeta.mediaType != sourceMediaType) {
                continue
            }
            if (context?.shouldGateDuplicatesByDate() != false &&
                !areMediaTimestampsEquivalent(
                    sourceLastMod,
                    getSourceDateTaken(),
                    existingMeta.lastModified,
                    existingMeta.dateTaken
                )
            ) {
                continue
            }
            val identical = if (isSourceEncrypted && sourceMeta != null) {
                areEncryptedFilesIdenticalContent(sourceFile, sourceMeta, existingBin, existingMeta)
            } else {
                arePlainAndEncryptedIdentical(sourceFile, existingBin, existingMeta)
            }
            if (identical) return null
        }

        return if (index.findExactEntries(desiredName).isNotEmpty()) desiredName else null
    } else {
        val index = plainIndex ?: TargetPlainIndex(targetDir)
        val candidates = index.findCandidates(desiredName, includeCollisionVariants)

        for (existingPlain in candidates) {
            if (existingPlain.length() != sourceOrigSize) continue
            val existingLastMod = existingPlain.lastModified()
            val gateByDate = context?.shouldGateDuplicatesByDate() != false
            if (gateByDate && !areTimestampsEquivalent(sourceLastMod, existingLastMod)) continue
            val srcTaken = getSourceDateTaken()
            val existingTaken = resolvePlaintextDateTaken(context, existingPlain, existingLastMod)
            if (gateByDate && !areMediaTimestampsEquivalent(sourceLastMod, srcTaken, existingLastMod, existingTaken)) {
                continue
            }
            val identical = if (isSourceEncrypted && sourceMeta != null) {
                arePlainAndEncryptedIdentical(existingPlain, sourceFile, sourceMeta)
            } else {
                arePlainFilesIdentical(sourceFile, existingPlain)
            }
            if (identical) return null
        }

        return if (index.findExact(desiredName) != null) desiredName else null
    }
}


fun CryptoManager.copyOrMoveEncryptedFileToDirectory(
    context: Context,
    sourceBin: File,
    targetDir: File,
    isMove: Boolean,
    conflictResolution: Int = CONFLICT_KEEP_BOTH,
    targetIndex: TargetEncryptedIndex? = null,
    skipDuplicateCheck: Boolean = false,
    deleteSourceOnIdentical: Boolean = true,
    matchCollisionVariants: Boolean = true,
    fastSampling: Boolean = false,
    operation: OperationContext = OperationContext.NONE,
    forcedDesiredName: String? = null
): File? {
    if (!sourceBin.isFile || !isEncryptedFile(sourceBin.name)) {
        OperationJournal.markFailed(operation, "encrypted source missing")
        return null
    }
    val sourceMeta = readMetadata(sourceBin, includeThumbnail = false, cacheResult = false)
    if (sourceMeta == null) {
        OperationJournal.markFailed(operation, "could not read encrypted header")
        return null
    }
    if (sourceMeta.isMacAuthenticated && !verifyFileIntegrity(sourceBin, sourceMeta)) {
        OperationJournal.markFailed(operation, "encrypted source integrity check failed")
        return null
    }
    if (!targetDir.exists() && !targetDir.mkdirs()) {
        OperationJournal.markFailed(operation, "could not create target folder")
        return null
    }

    val srcParentCanon = runCatching { sourceBin.parentFile?.canonicalPath }.getOrNull()
    val targetCanon = runCatching { targetDir.canonicalPath }.getOrNull()
    if (srcParentCanon != null && srcParentCanon == targetCanon) {
        OperationJournal.markSkipped(operation)
        return sourceBin
    }

    val index = targetIndex ?: TargetEncryptedIndex(targetDir)

    if (!skipDuplicateCheck) {
        val candidates = index.findCandidates(sourceMeta.originalName, matchCollisionVariants)
        val gateByDate = context.shouldGateDuplicatesByDate()
        for ((existingBin, existingMeta) in candidates) {
            if ((!gateByDate || areEncryptedTimestampsEquivalent(sourceMeta, existingMeta)) &&
                areEncryptedFilesIdenticalContent(
                    sourceBin,
                    sourceMeta,
                    existingBin,
                    existingMeta,
                    fastSampling = fastSampling
                )
            ) {
                applyDuplicateDateToEncryptedFile(context, existingBin, sourceMeta.lastModified)
                val dupFingerprint = MediaIdentity.computeFingerprint(existingBin)
                if (isMove && deleteSourceOnIdentical) {
                    runCatching { context.updateVirtualAlbumItemPath(sourceBin.absolutePath, existingBin.absolutePath) }
                    runCatching { context.mediaDB.deleteMediumPath(sourceBin.absolutePath) }
                    metadataCache.remove(sourceBin.absolutePath)
                    sourceBin.delete()
                    OperationJournal.markVerified(operation, dupFingerprint, existingBin.absolutePath)
                    OperationJournal.markSourceDeleted(operation)
                } else if (!isMove && deleteSourceOnIdentical) {
                    OperationJournal.markVerified(operation, dupFingerprint, existingBin.absolutePath)
                } else {
                    OperationJournal.markSkipped(operation)
                }
                return if (!deleteSourceOnIdentical) null else existingBin
            }
        }
    }

    var desiredName = forcedDesiredName?.takeIf { it.isNotEmpty() } ?: sourceMeta.originalName
    var pendingOverwriteBins: List<File> = emptyList()
    val exactMatches = index.findExactEntries(desiredName)
    if (exactMatches.isNotEmpty()) {
        when (conflictResolution) {
            CONFLICT_SKIP -> {
                OperationJournal.markSkipped(operation)
                return null
            }
            CONFLICT_OVERWRITE -> {
                pendingOverwriteBins = index.removeExactEntries(desiredName)
            }
            else -> {
                desiredName = index.getAlternativeOriginalName(desiredName)
            }
        }
    }

    fun deletePendingOverwriteBins() {
        pendingOverwriteBins.forEach { existingBin ->
            metadataCache.remove(existingBin.absolutePath)
            runCatching { context.mediaDB.deleteMediumPath(existingBin.absolutePath) }
            runCatching { existingBin.delete() }
        }
    }

    var destBin = File(targetDir, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
    while (destBin.exists()) {
        destBin = File(targetDir, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
    }

    val needsRename = desiredName != sourceMeta.originalName
    if (!isMove || needsRename) {
        if (!TransientOperationWaiter.awaitDestinationReady(context, operation, targetDir, sourceBin.length())) {
            OperationJournal.markFailed(operation, "destination unavailable or insufficient space")
            return null
        }
    }
    val staging = AtomicStaging.fileFor(destBin, operation)
    OperationJournal.markInFlight(
        ctx = operation,
        stagingPath = staging.absolutePath,
        resolvedDestName = desiredName,
        actualDestPath = destBin.absolutePath,
        sourceFingerprint = MediaIdentity.computeFingerprint(sourceBin)
    )
    val transferred = if (isMove && !needsRename && sourceBin.renameTo(destBin)) {
        // Same-filesystem rename is already atomic — no staging involved.
        deletePendingOverwriteBins()
        OperationJournal.markVerified(operation, MediaIdentity.computeFingerprint(destBin), destBin.absolutePath)
        OperationJournal.markSourceDeleted(operation)
        true
    } else {
        try {
            TransientOperationWaiter.runWithTransientRetry(
                context = context,
                ctx = operation,
                destDir = targetDir,
                requiredBytes = sourceBin.length(),
                onDiscardAttempt = {
                    AtomicStaging.discard(staging, containsPlaintext = false, context = context)
                }
            ) {
                OperationJournal.markInFlight(
                    ctx = operation,
                    stagingPath = staging.absolutePath,
                    resolvedDestName = desiredName,
                    actualDestPath = destBin.absolutePath,
                    sourceFingerprint = MediaIdentity.computeFingerprint(sourceBin)
                )
                FileInputStream(sourceBin).use { fis ->
                    BufferedInputStream(fis, 256 * 1024).use { bis ->
                        FileOutputStream(staging).use { fos ->
                            BufferedOutputStream(fos, 256 * 1024).use { bos ->
                                bis.copyTo(bos, 256 * 1024)
                                bos.flush()
                                fos.fd.sync()
                            }
                        }
                    }
                }
                AtomicStaging.commit(staging, destBin, alreadyFileSynced = true)
            }
            if (!verifyFileIntegrity(destBin, sourceMeta)) {
                throw IOException("Copied container failed read-back verification")
            }
            if (needsRename && !renameEncryptedFile(destBin, desiredName, skipIntegrityCheck = true, syncParentDir = false)) {
                destBin.delete()
                OperationJournal.markFailed(operation, "encrypted rename failed")
                return null
            }
            deletePendingOverwriteBins()
            OperationJournal.markVerified(operation, MediaIdentity.computeFingerprint(destBin), destBin.absolutePath)
            destBin.setLastModified(sourceBin.lastModified())
            if (isMove) {
                sourceBin.delete()
                OperationJournal.markSourceDeleted(operation)
            }
            true
        } catch (e: Exception) {
            AtomicStaging.discard(staging, containsPlaintext = false, context = context)
            destBin.delete()
            OperationJournal.markFailed(operation, e.message ?: "encrypted transfer failed")
            false
        }
    }
    if (!transferred) return null

    if (isMove) {
        metadataCache.remove(sourceBin.absolutePath)
        runCatching { context.updateVirtualAlbumItemPath(sourceBin.absolutePath, destBin.absolutePath) }
        runCatching { context.mediaDB.deleteMediumPath(sourceBin.absolutePath) }
    }
    val destMeta = readMetadata(destBin, includeThumbnail = false, cacheResult = false) ?: sourceMeta.copy(originalName = desiredName)
    index.addEntry(destBin, destMeta)
    return destBin
}


fun CryptoManager.cleanupEmptyEncryptedAlbumIfNeeded(context: Context, albumDir: File): Boolean {
    if (!albumDir.exists() || !albumDir.isDirectory || !isEncryptedFolder(albumDir)) return false
    val vaultRoot = findVaultRoot(context)
    if (vaultRoot != null) {
        val rootCanon = runCatching { vaultRoot.canonicalPath }.getOrDefault(vaultRoot.absolutePath)
        val dirCanon = runCatching { albumDir.canonicalPath }.getOrDefault(albumDir.absolutePath)
        if (rootCanon == dirCanon) return false
    }
    if (isVaultRecycleBin(albumDir) || isDefaultHiddenAlbum(albumDir)) {
        return false
    }
    val metaNames = setOf(
        VAULT_SUBFOLDER_META_FILE,
        VAULT_SUBFOLDER_ORIGIN_FILE,
        NOMEDIA,
        VAULT_RESTORE_INFO_FILE
    )
    val remaining = albumDir.listFiles() ?: emptyArray()
    if (remaining.any { it.name !in metaNames }) {
        return false
    }
    val deleted = runCatching { albumDir.deleteRecursively() }.getOrDefault(false)
    if (deleted || !albumDir.exists()) {
        folderNameCache.remove(albumDir.absolutePath)
        encryptedFolderCache[albumDir.absolutePath] = false
        runCatching { context.config.removeEncryptedFolder(albumDir.absolutePath) }
        runCatching { context.directoryDB.deleteDirPath(albumDir.absolutePath) }
        return true
    }
    return false
}

package org.fossify.gallery.extensions

import android.app.Activity
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.FileConflictDialog
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.dialogs.SmartConflictItem
import org.fossify.gallery.dialogs.SmartFileConflictDialog
import org.fossify.gallery.helpers.CONFLICT_HANDLING_AUTO
import org.fossify.gallery.helpers.CONFLICT_HANDLING_SMART_DIALOG
import org.fossify.gallery.helpers.CONFLICT_HANDLING_STANDARD_DIALOG
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.getCipherRootDirectory
import org.fossify.gallery.helpers.inspectStandardFilenameConflict
import org.fossify.gallery.helpers.inspectTransferConflict
import org.fossify.gallery.helpers.isEncryptedFolder
import java.io.File

data class StandardConflictItem(
    val resolutionKey: String,
    val fileDirItem: FileDirItem
)

data class TransferPairSpec(
    val sourceFile: File,
    val targetDir: File,
    val isTargetEncrypted: Boolean,
    val desiredPlainName: String? = null
)

data class TransferConflictScanResult(
    val resolutions: LinkedHashMap<String, Int>,
    val identicalPaths: HashSet<String>,
    val nonIdenticalPaths: HashSet<String>,
    val plainIndexes: HashMap<String, CryptoManager.TargetPlainIndex>,
    val encIndexes: HashMap<String, CryptoManager.TargetEncryptedIndex>,
    val identicalTargets: HashMap<String, File> = HashMap()
)

fun Activity.findIdenticalTransferTarget(
    sourceFile: File,
    targetDir: File,
    isTargetEncrypted: Boolean,
    plainIndex: CryptoManager.TargetPlainIndex? = null,
    encryptedIndex: CryptoManager.TargetEncryptedIndex? = null,
    desiredPlainName: String? = null,
    precheckedIdenticalTargets: Map<String, File> = emptyMap(),
    precheckedIdenticalPaths: Set<String> = emptySet(),
    precheckedNonIdenticalPaths: Set<String> = emptySet(),
    allowFastSampling: Boolean = false
): File? {
    if (config.conflictHandlingMode == CONFLICT_HANDLING_STANDARD_DIALOG) return null
    val srcPath = sourceFile.absolutePath
    precheckedIdenticalTargets[srcPath]?.let { return it }
    if (srcPath in precheckedNonIdenticalPaths) return null

    val useFastSampling = allowFastSampling &&
        (CryptoManager.isEncryptedFile(sourceFile.name) == isTargetEncrypted) &&
        config.fastLargeDuplicateCheck
    val check = CryptoManager.inspectTransferConflict(
        sourceFile = sourceFile,
        targetDir = targetDir,
        isTargetEncrypted = isTargetEncrypted,
        plainIndex = plainIndex,
        encryptedIndex = encryptedIndex,
        desiredPlainName = desiredPlainName,
        fastSampling = useFastSampling,
        context = this
    )
    if (check.status == CryptoManager.TransferConflictStatus.IDENTICAL_SKIP) {
        return check.existingFile
    }
    if (srcPath in precheckedIdenticalPaths) {
        return check.existingFile ?: File(targetDir, desiredPlainName ?: sourceFile.name)
    }
    return null
}

data class SmartConflictScanResult(
    val conflicts: ArrayList<SmartConflictItem>,
    val identicalPaths: HashSet<String>,
    val nonIdenticalPaths: HashSet<String>,
    val plainIndex: CryptoManager.TargetPlainIndex?,
    val encIndex: CryptoManager.TargetEncryptedIndex?
)

fun BaseSimpleActivity.resolveStandardFilenameConflicts(
    conflicts: List<StandardConflictItem>,
    callback: (LinkedHashMap<String, Int>) -> Unit
) {
    val resolutions = LinkedHashMap<String, Int>()
    if (conflicts.isEmpty()) {
        runOnUiThread {
            callback(resolutions)
        }
        return
    }

    fun askNext(index: Int) {
        if (index >= conflicts.size || isFinishing || isDestroyed) {
            callback(resolutions)
            return
        }
        val conflict = conflicts[index]
        FileConflictDialog(
            activity = this,
            fileDirItem = conflict.fileDirItem,
            showApplyToAllCheckbox = conflicts.size > 1
        ) { resolution, applyToAll ->
            resolutions[conflict.resolutionKey] = resolution
            if (applyToAll) {
                for (i in (index + 1) until conflicts.size) {
                    resolutions[conflicts[i].resolutionKey] = resolution
                }
                callback(resolutions)
            } else {
                askNext(index + 1)
            }
        }
    }

    runOnUiThread {
        askNext(0)
    }
}

fun BaseSimpleActivity.resolveCryptoTransferConflicts(
    allowFastLargeCheck: Boolean = false,
    progressDialog: CryptoProgressDialog? = null,
    buildPairs: () -> List<TransferPairSpec>,
    onResolved: (TransferConflictScanResult, CryptoProgressDialog?) -> Unit
) {
    val mode = config.conflictHandlingMode
    if (mode == CONFLICT_HANDLING_AUTO) {
        onResolved(TransferConflictScanResult(LinkedHashMap(), HashSet(), HashSet(), HashMap(), HashMap()), progressDialog)
        return
    }

    ensureBackgroundThread {
        val pairs = buildPairs()
        if (pairs.isEmpty()) {
            runOnUiThread {
                onResolved(TransferConflictScanResult(LinkedHashMap(), HashSet(), HashSet(), HashMap(), HashMap()), progressDialog)
            }
            return@ensureBackgroundThread
        }

        val plainIndexes = HashMap<String, CryptoManager.TargetPlainIndex>()
        val encIndexes = HashMap<String, CryptoManager.TargetEncryptedIndex>()

        fun getPlainIndex(dir: File): CryptoManager.TargetPlainIndex {
            return plainIndexes.getOrPut(dir.absolutePath) { CryptoManager.TargetPlainIndex(dir) }
        }

        fun getEncIndex(dir: File): CryptoManager.TargetEncryptedIndex {
            return encIndexes.getOrPut(dir.absolutePath) { CryptoManager.TargetEncryptedIndex(dir) }
        }

        if (mode == CONFLICT_HANDLING_STANDARD_DIALOG) {
            val standardConflicts = ArrayList<StandardConflictItem>()
            for (pair in pairs) {
                val pIdx = if (!pair.isTargetEncrypted) getPlainIndex(pair.targetDir) else null
                val eIdx = if (pair.isTargetEncrypted) getEncIndex(pair.targetDir) else null
                val conflictName = CryptoManager.inspectStandardFilenameConflict(
                    sourceFile = pair.sourceFile,
                    targetDir = pair.targetDir,
                    isTargetEncrypted = pair.isTargetEncrypted,
                    plainIndex = pIdx,
                    encryptedIndex = eIdx,
                    desiredPlainName = pair.desiredPlainName
                )
                if (conflictName != null) {
                    standardConflicts.add(
                        StandardConflictItem(
                            resolutionKey = pair.sourceFile.absolutePath,
                            fileDirItem = FileDirItem(
                                path = pair.sourceFile.absolutePath,
                                name = conflictName,
                                isDirectory = false,
                                modified = pair.sourceFile.lastModified(),
                                size = pair.sourceFile.length()
                            )
                        )
                    )
                }
            }
            if (standardConflicts.isNotEmpty()) {
                runOnUiThread {
                    progressDialog?.dismiss()
                    resolveStandardFilenameConflicts(standardConflicts) { resolutions ->
                        onResolved(TransferConflictScanResult(resolutions, HashSet(), HashSet(), plainIndexes, encIndexes), null)
                    }
                }
            } else {
                runOnUiThread {
                    onResolved(TransferConflictScanResult(LinkedHashMap(), HashSet(), HashSet(), plainIndexes, encIndexes), progressDialog)
                }
            }
        } else {
            val smartConflicts = ArrayList<SmartConflictItem>()
            val identicalPaths = HashSet<String>()
            val nonIdenticalPaths = HashSet<String>()
            val identicalTargets = HashMap<String, File>()
            for (pair in pairs) {
                val pIdx = if (!pair.isTargetEncrypted) getPlainIndex(pair.targetDir) else null
                val eIdx = if (pair.isTargetEncrypted) getEncIndex(pair.targetDir) else null
                val useFastSampling = allowFastLargeCheck &&
                    (CryptoManager.isEncryptedFile(pair.sourceFile.name) == pair.isTargetEncrypted) &&
                    config.fastLargeDuplicateCheck
                val check = CryptoManager.inspectTransferConflict(
                    sourceFile = pair.sourceFile,
                    targetDir = pair.targetDir,
                    isTargetEncrypted = pair.isTargetEncrypted,
                    plainIndex = pIdx,
                    encryptedIndex = eIdx,
                    desiredPlainName = pair.desiredPlainName,
                    fastSampling = useFastSampling,
                    context = this
                )
                when (check.status) {
                    CryptoManager.TransferConflictStatus.IDENTICAL_SKIP -> {
                        identicalPaths.add(pair.sourceFile.absolutePath)
                        check.existingFile?.let { identicalTargets[pair.sourceFile.absolutePath] = it }
                    }
                    CryptoManager.TransferConflictStatus.NAME_CONFLICT -> {
                        nonIdenticalPaths.add(pair.sourceFile.absolutePath)
                        val displayName = check.displayName ?: pair.sourceFile.name
                        val existingPath = check.existingFile?.absolutePath ?: File(pair.targetDir, displayName).absolutePath
                        smartConflicts.add(
                            SmartConflictItem(
                                resolutionKey = pair.sourceFile.absolutePath,
                                displayName = displayName,
                                sourcePath = pair.sourceFile.absolutePath,
                                existingPath = existingPath,
                                useFastLargeDuplicateCheck = useFastSampling
                            )
                        )
                    }
                    CryptoManager.TransferConflictStatus.NO_CONFLICT -> Unit
                }
            }
            if (smartConflicts.isNotEmpty()) {
                runOnUiThread {
                    progressDialog?.dismiss()
                    resolveSmartContentConflicts(smartConflicts) { resolutions ->
                        onResolved(TransferConflictScanResult(resolutions, identicalPaths, nonIdenticalPaths, plainIndexes, encIndexes, identicalTargets), null)
                    }
                }
            } else {
                runOnUiThread {
                    onResolved(TransferConflictScanResult(LinkedHashMap(), identicalPaths, nonIdenticalPaths, plainIndexes, encIndexes, identicalTargets), progressDialog)
                }
            }
        }
    }
}

fun BaseSimpleActivity.scanSmartTransferConflicts(
    sourceFiles: List<File>,
    targetDir: File,
    isTargetEncrypted: Boolean,
    useFastSampling: Boolean = false,
    resolveDesiredName: ((File) -> String?)? = null
): SmartConflictScanResult {
    val plainIndex = if (!isTargetEncrypted) CryptoManager.TargetPlainIndex(targetDir) else null
    val encIndex = if (isTargetEncrypted) CryptoManager.TargetEncryptedIndex(targetDir) else null
    val conflicts = ArrayList<SmartConflictItem>()
    val identicalPaths = HashSet<String>()
    val nonIdenticalPaths = HashSet<String>()

    for (srcFile in sourceFiles) {
        val check = CryptoManager.inspectTransferConflict(
            sourceFile = srcFile,
            targetDir = targetDir,
            isTargetEncrypted = isTargetEncrypted,
            plainIndex = plainIndex,
            encryptedIndex = encIndex,
            desiredPlainName = resolveDesiredName?.invoke(srcFile),
            fastSampling = useFastSampling,
            context = this
        )
        when (check.status) {
            CryptoManager.TransferConflictStatus.IDENTICAL_SKIP -> {
                identicalPaths.add(srcFile.absolutePath)
            }
            CryptoManager.TransferConflictStatus.NAME_CONFLICT -> {
                nonIdenticalPaths.add(srcFile.absolutePath)
                val displayName = check.displayName ?: srcFile.name
                val existingPath = check.existingFile?.absolutePath ?: File(targetDir, displayName).absolutePath
                conflicts.add(
                    SmartConflictItem(
                        resolutionKey = srcFile.absolutePath,
                        displayName = displayName,
                        sourcePath = srcFile.absolutePath,
                        existingPath = existingPath,
                        useFastLargeDuplicateCheck = useFastSampling
                    )
                )
            }
            CryptoManager.TransferConflictStatus.NO_CONFLICT -> Unit
        }
    }
    return SmartConflictScanResult(conflicts, identicalPaths, nonIdenticalPaths, plainIndex, encIndex)
}

fun BaseSimpleActivity.resolveSmartContentConflicts(
    conflicts: List<SmartConflictItem>,
    callback: (LinkedHashMap<String, Int>) -> Unit
) {
    val resolutions = LinkedHashMap<String, Int>()
    if (config.conflictHandlingMode != CONFLICT_HANDLING_SMART_DIALOG || conflicts.isEmpty()) {
        runOnUiThread {
            callback(resolutions)
        }
        return
    }

    fun askNext(index: Int) {
        if (index >= conflicts.size || isFinishing || isDestroyed) {
            callback(resolutions)
            return
        }
        val conflict = conflicts[index]
        SmartFileConflictDialog(
            activity = this,
            conflict = conflict,
            currentIndex = index,
            totalCount = conflicts.size
        ) { resolution, applyToAll ->
            resolutions[conflict.resolutionKey] = resolution
            if (applyToAll) {
                for (i in (index + 1) until conflicts.size) {
                    resolutions[conflicts[i].resolutionKey] = resolution
                }
                callback(resolutions)
            } else {
                askNext(index + 1)
            }
        }
    }

    runOnUiThread {
        askNext(0)
    }
}

fun BaseSimpleActivity.cleanupEmptyPlaintextAlbumIfNeeded(
    folder: File,
    forceIfEmpty: Boolean = false
): Boolean {
    if (!folder.exists() || !folder.isDirectory) return false
    if (!forceIfEmpty && !config.deleteEmptyFolders) return false
    val cleanPath = folder.absolutePath.trimEnd('/')
    if (cleanPath.isEmpty()) return false
    if (cleanPath.equals(internalStoragePath.trimEnd('/'), ignoreCase = true)) return false
    if (cleanPath.isDownloadsFolder()) return false
    val cipherRoot = CryptoManager.getCipherRootDirectory().absolutePath.trimEnd('/')
    if (cleanPath.equals(cipherRoot, ignoreCase = true)) return false
    if (CryptoManager.isEncryptedFolder(folder)) return false

    val children = folder.listFiles() ?: return false
    if (children.any { it.isDirectory || it.name != NOMEDIA }) {
        return false
    }

    File(folder, NOMEDIA).takeIf { it.exists() }?.let { noMedia ->
        runCatching { noMedia.delete() }
    }
    val deleted = runCatching { folder.delete() }.getOrDefault(false)
    if (deleted || !folder.exists()) {
        runCatching { directoryDB.deleteDirPath(folder.absolutePath) }
        return true
    }
    return false
}

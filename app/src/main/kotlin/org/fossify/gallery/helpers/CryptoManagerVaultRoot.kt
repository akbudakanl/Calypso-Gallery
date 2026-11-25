package org.fossify.gallery.helpers

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.system.Os
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_HEADER_MAGIC
import java.io.*
import android.os.Build

/**
 * Extracted from [CryptoManager]. Feature group: CryptoManagerVaultRoot.
 */

/**
 * The file system can only be inspected when the app may read shared storage. Without that
 * permission "not found" would be meaningless, so nothing may be concluded (or wiped) from it.
 */
internal fun CryptoManager.canAccessSharedStorage(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}


internal fun CryptoManager.isValidVaultHeader(headerFile: File): Boolean {
    if (!headerFile.isFile || headerFile.length() <= VAULT_HEADER_MAGIC.length) return false
    return try {
        FileInputStream(headerFile).use { fis ->
            val magic = ByteArray(VAULT_HEADER_MAGIC.length)
            DataInputStream(fis).readFully(magic)
            String(magic, Charsets.US_ASCII) == VAULT_HEADER_MAGIC
        }
    } catch (_: Exception) {
        false
    }
}


internal fun CryptoManager.hasValidVaultHeader(dir: File?): Boolean {
    return dir != null && dir.isDirectory && isValidVaultHeader(File(dir, VAULT_HEADER_FILE))
}


/**
 * Vaults created by older versions have no .vault file: their wrapped key only lives in
 * SharedPreferences. Such a vault is recognised ONLY when that key material is still stored AND
 * the folder physically holds encrypted content. An empty folder, or one that only contains
 * .nomedia / JSON stubs, is never a vault.
 */
internal fun CryptoManager.isLegacyVaultRoot(context: Context, dir: File?): Boolean {
    if (dir == null || !dir.isDirectory) return false
    if (context.config.vaultWrappedKeyData.isEmpty()) return false
    val children = dir.listFiles() ?: return false
    return children.any { child ->
        (child.isFile && isEncryptedFile(child.name)) || (child.isDirectory && isEncryptedFolder(child))
    }
}


internal fun CryptoManager.normalizeVaultPath(path: String): String {
    return path.trim().trimEnd('/', '\\')
}


internal fun CryptoManager.isInternalStoragePath(path: String): Boolean {
    val clean = normalizeVaultPath(path)
    if (clean.isEmpty()) return false
    val internalRoot = normalizeVaultPath(getStorageRootDirectory().absolutePath)
    return clean.equals(internalRoot, ignoreCase = true) ||
        clean.startsWith("$internalRoot/", ignoreCase = true)
}


fun CryptoManager.getVaultRoot(context: Context): File? = findVaultRoot(context)


fun CryptoManager.canWriteToDirectory(dir: File): Boolean {
    if (!dir.exists() || !dir.isDirectory || !dir.canWrite()) return false
    return try {
        val probe = File(dir, ".fv_write_test_${System.nanoTime()}")
        val created = probe.createNewFile()
        if (created) {
            probe.delete()
            true
        } else {
            probe.exists()
        }
    } catch (_: Throwable) {
        false
    }
}


internal fun CryptoManager.resolveUriViaProcFd(context: Context, uri: Uri): File? {
    return runCatching {
        val resolvedPath = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            Os.readlink("/proc/self/fd/${pfd.fd}")
        }
        val file = resolvedPath
            ?.takeIf { it.startsWith("/") && !it.startsWith("/proc/") }
            ?.let { File(it) }
        if (file != null && file.exists() && file.isDirectory) {
            file
        } else {
            null
        }
    }.getOrNull()
}


internal fun CryptoManager.resolveVolumeMountPath(
    context: Context,
    partition: String,
    relativeSubpath: String
): File? {
    val rel = relativeSubpath.trim().replace('\\', '/').trim('/')
    val roots = ArrayList<File>()
    if (partition.isNotEmpty()) {
        otgMountBaseOverrideForTest?.forEach { base ->
            roots.add(File(base, partition))
        }
        roots.add(File("/storage/$partition"))
        roots.add(File("/mnt/media_rw/$partition"))
        roots.add(File("/mnt/runtime/write/$partition"))
        roots.add(File("/mnt/runtime/default/$partition"))
    }
    roots.addAll(listExternalStorageVolumeRoots(context))

    for (volRoot in roots.distinctBy { it.absolutePath }) {
        val candidate = if (rel.isEmpty()) volRoot else File(volRoot, rel)
        if (candidate.exists() && candidate.isDirectory) {
            return candidate
        }
    }
    return null
}


/**
 * Resolves a folder path returned by Fossify's [FilePickerDialog] to a real POSIX directory
 * [File]. Direct POSIX paths (including mounted `/storage/XXXX-XXXX/...` paths that Fossify's
 * `isPathOnOTG()` may flag when `config.OTGPath` shares the prefix) are returned directly;
 * `otg:` pseudo-paths and `content://` tree URIs are mapped to their POSIX mount when
 * available, or return `null` if the device only exposes virtual SAF access.
 */
fun CryptoManager.resolvePickedPathToFile(context: Context, pickedPath: String): File? {
    val trimmed = pickedPath.trim()
    if (trimmed.isEmpty()) return null

    val otgConfigPath = try {
        context.config.OTGPath.trim()
    } catch (_: Throwable) {
        ""
    }

    val isOtgPseudoPath = trimmed.startsWith("otg:", ignoreCase = true) ||
        (otgConfigPath.startsWith("otg:", ignoreCase = true) && trimmed.startsWith(otgConfigPath, ignoreCase = true))

    if (isOtgPseudoPath) {
        val partition = try {
            context.config.OTGPartition.trim()
        } catch (_: Throwable) {
            ""
        }
        val relCandidates = ArrayList<String>(2)
        if (otgConfigPath.startsWith("otg:", ignoreCase = true) && trimmed.startsWith(otgConfigPath, ignoreCase = true)) {
            relCandidates.add(trimmed.substring(otgConfigPath.length).trim('/'))
        }
        if (trimmed.startsWith("otg:", ignoreCase = true)) {
            relCandidates.add(trimmed.substring(4).trim('/'))
        }

        for (rel in relCandidates.distinct()) {
            resolveVolumeMountPath(context, partition, rel)?.let { return it }
        }

        // Fallback via SAF DocumentFile -> /proc/self/fd readlink
        runCatching {
            val docFile = context.getOTGFastDocumentFile(trimmed)
            if (docFile != null) {
                resolveUriViaProcFd(context, docFile.uri)?.let { return it }
                val childFile = docFile.listFiles().firstOrNull { it.isFile }
                if (childFile != null) {
                    val resolvedChild = context.contentResolver.openFileDescriptor(childFile.uri, "r")?.use { pfd ->
                        Os.readlink("/proc/self/fd/${pfd.fd}")
                    }
                    val parent = resolvedChild
                        ?.takeIf { it.startsWith("/") && !it.startsWith("/proc/") }
                        ?.let { File(it).parentFile }
                    if (parent != null && parent.exists() && parent.isDirectory) {
                        return parent
                    }
                }
            }
        }
        return null
    }

    if (trimmed.startsWith("content://", ignoreCase = true)) {
        val uri = Uri.parse(trimmed)
        resolveUriViaProcFd(context, uri)?.let { return it }

        val docId = runCatching {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.getDocumentId(uri)
            } else {
                DocumentsContract.getTreeDocumentId(uri)
            }
        }.getOrNull() ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()

        if (!docId.isNullOrBlank()) {
            if (docId.startsWith("raw:", ignoreCase = true)) {
                val rawDir = File(docId.substring(4))
                if (rawDir.exists() && rawDir.isDirectory) return rawDir
            }
            val volumeId = docId.substringBefore(':')
            val subPath = docId.substringAfter(':', "").trim('/')
            if (volumeId.equals("primary", ignoreCase = true)) {
                val internal = getStorageRootDirectory()
                val target = if (subPath.isEmpty()) internal else File(internal, subPath)
                if (target.exists() && target.isDirectory) return target
            } else if (volumeId.isNotEmpty()) {
                resolveVolumeMountPath(context, volumeId, subPath)?.let { return it }
            }
        }
        return null
    }

    // Case A: Direct POSIX path
    return File(trimmed)
}


fun CryptoManager.isConfiguredCustomVaultPath(context: Context): Boolean {
    val stored = normalizeVaultPath(context.config.vaultRootPath)
    if (stored.isEmpty()) return false
    val standardPath = normalizeVaultPath(getDefaultVaultRoot().absolutePath)
    return !stored.equals(standardPath, ignoreCase = true)
}


fun CryptoManager.isConfiguredExternalVaultPath(context: Context): Boolean {
    val stored = normalizeVaultPath(context.config.vaultRootPath)
    if (stored.isEmpty()) return false
    return isConfiguredCustomVaultPath(context) && !isInternalStoragePath(stored)
}


fun CryptoManager.isConfiguredVaultTemporarilyUnavailable(context: Context): Boolean {
    val stored = normalizeVaultPath(context.config.vaultRootPath)
    if (stored.isEmpty()) return false
    if (!isConfiguredCustomVaultPath(context) || isInternalStoragePath(stored)) return false
    val storedFile = File(context.config.vaultRootPath)
    return !hasValidVaultHeader(storedFile) && !isLegacyVaultRoot(context, storedFile)
}


fun CryptoManager.setVaultRootLocation(context: Context, vaultDir: File) {
    cachedVaultRoot = vaultDir
    encryptedFolderCache.clear()
    folderNameCache.clear()
    vaultRecycleBinFolder = null
    vaultRecycleBinResolved = false
    if (context.config.vaultRootPath != vaultDir.absolutePath) {
        context.config.vaultRootPath = vaultDir.absolutePath
    }
}


/**
 * Registers an existing on-disk vault as the active one WITHOUT asking for or
 * unlocking its password. Selecting a vault only needs its public header; the
 * password is required later, when the vault is actually opened. Doing the KDF
 * during selection would be pointless (and left a half-selected vault behind
 * when the credential prompt was cancelled).
 */
fun CryptoManager.selectVaultWithoutUnlock(context: Context, vaultRoot: File): Boolean {
    if (!hasValidVaultHeader(vaultRoot)) return false
    setVaultRootLocation(context, vaultRoot)
    val cfg = context.config
    cfg.isHiddenPasswordProtectionOn = true
    cfg.hiddenPasswordHash = "argon2id_aes256_gcm"
    cfg.hiddenProtectionType = getVaultProtectionType(context) ?: PROTECTION_PASSWORD
    readVaultKdfParams(context)?.let { params ->
        cfg.lastCalibratedMemoryKb = params.memoryKb
        cfg.lastCalibratedIterations = params.iterations
    }
    // PIN length is only known after a successful authentication.
    cfg.showVaultPinDots = false
    cfg.vaultPinLength = 0
    // Biometric keys and legacy wrapped-key data belong to the previously selected
    // vault; a header-based vault must never inherit them.
    cfg.vaultWrappedKeyData = ""
    cfg.isVaultBiometricEnabled = false
    cfg.vaultBiometricWrappedBundle = ""
    clearBiometricKey(context)
    return true
}


fun CryptoManager.clearCachedVaultRoot() {
    cachedVaultRoot = null
    encryptedFolderCache.clear()
    folderNameCache.clear()
    vaultRecycleBinFolder = null
    vaultRecycleBinResolved = false
}


fun CryptoManager.prepareVaultLocationSwitch(context: Context, newVaultRoot: File? = null) {
    val previousPath = normalizeVaultPath(cachedVaultRoot?.absolutePath ?: context.config.vaultRootPath)
    val targetPath = normalizeVaultPath(newVaultRoot?.absolutePath ?: "")
    val switchingToDifferentVault = previousPath.isNotEmpty() && !previousPath.equals(targetPath, ignoreCase = true)

    lockVault(context)
    cachedVaultRoot = newVaultRoot
    encryptedFolderCache.clear()
    folderNameCache.clear()
    vaultRecycleBinFolder = null
    vaultRecycleBinResolved = false
    clearBiometricKey(context)
    val cfg = context.config
    cfg.vaultRootPath = newVaultRoot?.absolutePath ?: ""
    cfg.vaultWrappedKeyData = ""
    cfg.hiddenPasswordHash = ""
    cfg.vaultUnlockMeasuredMs = 0
    cfg.showVaultPinDots = false
    cfg.isHiddenPasswordProtectionOn = false
    cfg.temporarilyShowHidden = false
    cfg.temporarilyShowVaultExcluded = false
    if (switchingToDifferentVault) {
        runCatching { VirtualAlbumSyncManager.evictHiddenVirtualAlbumsFromDbCache(context) }
    }
    try {
        clearAppImageCaches(context)
    } catch (_: Throwable) {}
}


/**
 * Checks whether [dir] directly contains a vault or hosts a Secrets folder, either directly
 * inside [dir] or nested inside `<dir>/Calypso Gallery`.
 */
fun CryptoManager.resolveExistingVaultInDirectory(dir: File): File? {
    val candidates = listOf(
        dir,
        File(dir, SECRETS_DIR_NAME),
        File(File(dir, CIPHER_APP_DIR_NAME), SECRETS_DIR_NAME)
    )
    return candidates.firstOrNull { hasValidVaultHeader(it) }
}


internal fun CryptoManager.internalVaultCandidates(): List<File> {
    return listOf(File(getCipherRootDirectory(), SECRETS_DIR_NAME))
}


internal fun CryptoManager.knownVaultRootCandidates(context: Context): List<File> {
    val candidates = ArrayList<File>(6)
    val storedPath = context.config.vaultRootPath
    if (storedPath.isNotEmpty()) {
        cachedVaultRoot?.takeIf {
            normalizeVaultPath(it.absolutePath).equals(normalizeVaultPath(storedPath), ignoreCase = true)
        }?.let { candidates.add(it) }
        candidates.add(File(storedPath))
        if (!isConfiguredCustomVaultPath(context)) {
            candidates.addAll(internalVaultCandidates())
        }
    } else {
        candidates.addAll(internalVaultCandidates())
    }
    return candidates.distinctBy { it.absolutePath }
}


internal fun CryptoManager.rememberVaultRoot(context: Context, root: File) {
    cachedVaultRoot = root
    if (context.config.vaultRootPath != root.absolutePath) {
        context.config.vaultRootPath = root.absolutePath
    }
}


fun CryptoManager.listExternalStorageVolumeRoots(context: Context): List<File> {
    val volumeRoots = LinkedHashMap<String, File>()

    try {
        context.getExternalFilesDirs(null).filterNotNull().forEach { extDir ->
            var cur: File? = extDir
            while (cur != null) {
                if (cur.name == "Android") {
                    cur.parentFile?.let { volRoot ->
                        volumeRoots.putIfAbsent(volRoot.absolutePath, volRoot)
                    }
                    break
                }
                cur = cur.parentFile
            }
        }
    } catch (_: Throwable) {}

    try {
        val storageDir = File("/storage")
        if (storageDir.isDirectory) {
            storageDir.listFiles()?.forEach { child ->
                if (
                    child.isDirectory &&
                    !child.name.equals("emulated", ignoreCase = true) &&
                    !child.name.equals("self", ignoreCase = true)
                ) {
                    volumeRoots.putIfAbsent(child.absolutePath, child)
                }
            }
        }
    } catch (_: Throwable) {}

    return volumeRoots.values.toList()
}


fun CryptoManager.findCandidateExternalVaultRoots(context: Context): List<File> {
    val results = LinkedHashMap<String, File>()
    val volumeRoots = ArrayList<File>()
    volumeRoots.addAll(listExternalStorageVolumeRoots(context))
    try {
        context.sdCardPath.takeIf { it.isNotEmpty() }?.let { volumeRoots.add(File(it)) }
    } catch (_: Throwable) {}
    try {
        context.config.OTGPath
            .takeIf { it.isNotEmpty() && !it.startsWith("otg:", ignoreCase = true) && !it.startsWith("content://", ignoreCase = true) }
            ?.let { volumeRoots.add(File(it)) }
    } catch (_: Throwable) {}

    val internalRoot = normalizeVaultPath(getStorageRootDirectory().absolutePath)
    for (root in volumeRoots.distinctBy { it.absolutePath }) {
        val normRoot = normalizeVaultPath(root.absolutePath)
        if (normRoot.equals(internalRoot, ignoreCase = true)) continue
        if (!root.exists() || !root.isDirectory) continue

        resolveExistingVaultInDirectory(root)?.let { results.putIfAbsent(it.absolutePath, it) }
        root.listFiles()?.forEach { child ->
            if (hasValidVaultHeader(child)) {
                results.putIfAbsent(child.absolutePath, child)
            }
        }
    }
    return results.values.toList()
}


/**
 * Looks for the vault root ON DISK.
 * - When [Config.vaultRootPath] is empty, ONLY the default internal vault location is checked
 *   (external USB/SD drives are NOT auto-adopted without explicit user selection).
 * - When [Config.vaultRootPath] is set, that configured location is checked directly.
 */
fun CryptoManager.locateVaultRootOnDisk(context: Context, allowDeepScan: Boolean = true): File? {
    if (isConfiguredVaultTemporarilyUnavailable(context)) {
        cachedVaultRoot = null
        return null
    }

    val known = knownVaultRootCandidates(context)

    known.firstOrNull { hasValidVaultHeader(it) }?.let {
        rememberVaultRoot(context, it)
        return it
    }
    known.firstOrNull { isLegacyVaultRoot(context, it) }?.let {
        rememberVaultRoot(context, it)
        return it
    }

    cachedVaultRoot = null
    return null
}


fun CryptoManager.vaultExistsOnDisk(context: Context): Boolean = locateVaultRootOnDisk(context) != null


fun CryptoManager.getExistingCipherSecretsDir(context: Context): File? = locateVaultRootOnDisk(context)


/** Anything that still claims a vault exists (keys in memory, prefs, biometric bundle). */
fun CryptoManager.hasStaleVaultState(context: Context): Boolean {
    val c = context.config
    return isVaultUnlocked ||
        c.vaultWrappedKeyData.isNotEmpty() ||
        c.vaultRootPath.isNotEmpty() ||
        c.hiddenPasswordHash.isNotEmpty() ||
        c.isHiddenPasswordProtectionOn ||
        c.isVaultBiometricEnabled ||
        c.vaultBiometricWrappedBundle.isNotEmpty()
}


/**
 * Forgets a vault whose folder/header no longer exists: locks it (wipes keys from memory),
 * clears every cache and removes all credentials and the biometric key.
 */
fun CryptoManager.resetStaleVaultState(context: Context) {
    lockVault(context)
    cachedVaultRoot = null
    encryptedFolderCache.clear()
    folderNameCache.clear()
    vaultRecycleBinFolder = null
    vaultRecycleBinResolved = false
    clearBiometricKey(context)
    val cfg = context.config
    cfg.vaultRootPath = ""
    cfg.vaultWrappedKeyData = ""
    cfg.hiddenPasswordHash = ""
    cfg.vaultUnlockMeasuredMs = 0
    cfg.showVaultPinDots = false
    cfg.isHiddenPasswordProtectionOn = false
    cfg.isVaultBiometricEnabled = false
    cfg.temporarilyShowHidden = false
    cfg.temporarilyShowVaultExcluded = false
}


/**
 * The check every user-facing entry point must call (open vault, hide item/album, settings):
 * actively verifies the vault on disk and, if it is gone, wipes all leftover state so that
 * neither a password prompt nor an "empty vault" option can appear for a vault that does not exist.
 */
fun CryptoManager.verifyVaultOnDisk(context: Context): Boolean {
    if (!canAccessSharedStorage(context)) {
        // The disk cannot be inspected: neither wipe anything nor trust it blindly.
        return context.config.vaultWrappedKeyData.isNotEmpty()
    }
    if (isConfiguredVaultTemporarilyUnavailable(context)) {
        lockVault(context)
        cachedVaultRoot = null
        encryptedFolderCache.clear()
        folderNameCache.clear()
        vaultRecycleBinFolder = null
    vaultRecycleBinResolved = false
        return false
    }
    val exists = vaultExistsOnDisk(context)
    if (!exists && !vaultSetupInProgress && hasStaleVaultState(context)) {
        resetStaleVaultState(context)
    }
    return exists
}


fun CryptoManager.deleteExistingVault(context: Context) {
    val targets = mutableListOf<File>()
    if (isConfiguredCustomVaultPath(context)) {
        val storedPath = context.config.vaultRootPath
        if (storedPath.isNotEmpty()) {
            targets.add(File(storedPath))
        }
        getExistingCipherSecretsDir(context)?.let { targets.add(it) }
        findVaultRoot(context)?.let { targets.add(it) }
    } else {
        targets.add(getDefaultVaultRoot())
        val storedPath = context.config.vaultRootPath
        if (storedPath.isNotEmpty()) {
            targets.add(File(storedPath))
        }
        getExistingCipherSecretsDir(context)?.let { targets.add(it) }
        findVaultRoot(context)?.let { targets.add(it) }
    }

    val uniqueTargets = targets.distinct()
    for (dir in uniqueTargets) {
        if (dir.exists()) {
            try {
                dir.walkBottomUp().forEach { file ->
                    if (file.isFile) {
                        secureWipeAndDeleteFile(file, context)
                    } else if (file.isDirectory) {
                        file.delete()
                    }
                }
                dir.delete()
            } catch (_: Exception) {}
        }
    }

    // The CLI bridges are siblings of the vault root, not part of it. Remove the
    // ones carrying a copy of this vault's header (including archived bridges)
    // while the KEK fingerprint is still available, before the keys are cleared.
    CliBridgeManager.deleteBridgesForVaultRoots(context, uniqueTargets)

    clearKeys(context)
    cachedVaultRoot = null
    encryptedFolderCache.clear()

    clearBiometricKey(context)
    context.config.vaultRootPath = ""
    context.config.vaultWrappedKeyData = ""
    context.config.hiddenPasswordHash = ""
    context.config.showVaultPinDots = false
    context.config.isHiddenPasswordProtectionOn = false
    context.config.isVaultBiometricEnabled = false
    context.config.temporarilyShowHidden = false
    context.config.temporarilyShowVaultExcluded = false
    clearAppImageCaches(context)
}


/** The vault exists if and only if its .vault header (or a legacy vault's content) is on disk. */
fun CryptoManager.hasExistingVault(context: Context): Boolean = vaultExistsOnDisk(context)


// ==================== MASTER VAULT ROOT (STEALTH CONSOLIDATED CONTAINER) ====================

/**
 * Creates the vault root folder. UI flows must go through [setupOrUpdateCredentials] so every
 * vault root immediately receives a valid `.vault` header; direct calls are intended for setup
 * and tests.
 */
fun CryptoManager.createVaultRoot(context: Context, customTargetDir: File? = null): File? {
    if (customTargetDir == null) {
        findVaultRoot(context)?.let { return it }

        ensureCipherRootDirectory()

        val newDir = getDefaultVaultRoot()
        if (!newDir.exists()) {
            newDir.mkdirs()
        }

        val noMedia = File(newDir, NOMEDIA)
        if (!noMedia.exists()) {
            try { noMedia.createNewFile() } catch (_: Exception) {}
        }

        if (!newDir.exists() || !newDir.isDirectory || !newDir.canWrite()) {
            return null
        }

        setVaultRootLocation(context, newDir)
        return newDir
    }

    val newDir = when {
        customTargetDir.name.equals(SECRETS_DIR_NAME, ignoreCase = true) ||
            isValidVaultHeader(File(customTargetDir, VAULT_HEADER_FILE)) -> customTargetDir
        else -> {
            // Reuse an existing Secrets folder on the picked drive instead of creating a second one.
            listOf(SECRETS_DIR_NAME)
                .map { File(customTargetDir, it) }
                .firstOrNull { it.isDirectory }
                ?: File(customTargetDir, SECRETS_DIR_NAME)
        }
    }

    if (!newDir.exists()) {
        newDir.mkdirs()
    }
    if (!newDir.exists() || !newDir.isDirectory || !newDir.canWrite()) {
        return null
    }

    val noMedia = File(newDir, NOMEDIA)
    if (!noMedia.exists()) {
        try { noMedia.createNewFile() } catch (_: Exception) {}
    }

    setVaultRootLocation(context, newDir)
    return newDir
}


/** Returns the vault root only if a vault really exists on disk; never creates anything. */
fun CryptoManager.findVaultRoot(context: Context): File? {
    if (isConfiguredVaultTemporarilyUnavailable(context)) {
        cachedVaultRoot = null
        return null
    }

    // Fast path: a cached root is trusted only while its .vault header is still there.
    cachedVaultRoot?.let { cached ->
        if (cached.isDirectory && File(cached, VAULT_HEADER_FILE).isFile) return cached
    }

    return locateVaultRootOnDisk(context)
}

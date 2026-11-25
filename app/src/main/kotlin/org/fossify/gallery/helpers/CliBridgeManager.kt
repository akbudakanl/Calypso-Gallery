package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.gallery.extensions.config
import java.io.File
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Manages the per-vault CLI bridge folders under `Calypso Gallery/CLI`.
 *
 * A bridge is a transfer area, not a vault: it only carries the vault header
 * (`vault`) and a small `cli_bridge` marker. The marker records the vault path,
 * a stable random id, an `active` flag and a fingerprint derived from the KEK.
 * The fingerprint (not the folder name and not any date) decides whether two
 * bridges belong to the same vault.
 */
object CliBridgeManager {

    data class Bridge(
        val dir: File,
        val vaultId: String,
        val fingerprint: String,
        val sourcePath: String,
        val active: Boolean,
    )

    sealed class PrepareResult {
        data class Created(val bridge: Bridge) : PrepareResult()
        data class AlreadyExists(val bridge: Bridge) : PrepareResult()
        object NoVault : PrepareResult()
        object Locked : PrepareResult()
    }

    private const val MARKER_VERSION = "1"
    private const val FINGERPRINT_INFO = "calypso-cli-bridge"

    fun root(context: Context): File = File(CryptoManager.getCipherRootDirectory(), CLI_STAGING_DIR_NAME)

    fun archiveRoot(context: Context): File = File(root(context), CLI_BRIDGE_ARCHIVED_DIR_NAME)

    /** KEK-derived identity. Stable across password changes; changes only with the KEK. */
    fun currentFingerprint(): String? {
        val kek = CryptoManager.unlockedKek ?: return null
        return runCatching {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(kek, "HmacSHA256"))
            mac.doFinal(FINGERPRINT_INFO.toByteArray(Charsets.UTF_8))
                .take(8).joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    fun bridges(context: Context, includeArchived: Boolean = false): List<Bridge> {
        val result = ArrayList<Bridge>()
        val roots = mutableListOf(root(context))
        if (includeArchived) roots.add(archiveRoot(context))
        for (base in roots) {
            val dirs = base.listFiles()?.filter { it.isDirectory } ?: continue
            for (dir in dirs) {
                val marker = File(dir, CLI_BRIDGE_MARKER_FILE)
                if (!marker.isFile) continue
                val values = HashMap<String, String>()
                runCatching {
                    marker.readLines(Charsets.UTF_8).forEach { line ->
                        val idx = line.indexOf('=')
                        if (idx > 0) {
                            values[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
                        }
                    }
                }.onFailure { continue }
                if (values["calypso_bridge"] != MARKER_VERSION) continue
                val id = values["vault_id"] ?: continue
                val fp = values["kek_fingerprint"] ?: continue
                result.add(
                    Bridge(
                        dir = dir,
                        vaultId = id,
                        fingerprint = fp,
                        sourcePath = values["source_path"] ?: "",
                        active = values["active"] == "1",
                    )
                )
            }
        }
        return result
    }

    fun bridgeFor(context: Context, fingerprint: String): Bridge? =
        bridges(context, includeArchived = true).firstOrNull {
            it.fingerprint.equals(fingerprint, ignoreCase = true)
        }

    /**
     * Creates or reuses the bridge for the active vault. Existing bridges with
     * the same fingerprint are reported so the caller can apply the user's
     * conflict-handling mode (skip / reuse / create a new bridge).
     */
    fun prepare(context: Context): PrepareResult {
        val fingerprint = currentFingerprint() ?: return PrepareResult.Locked
        val vaultRoot = CryptoManager.findVaultRoot(context) ?: return PrepareResult.NoVault
        bridgeFor(context, fingerprint)?.let { return PrepareResult.AlreadyExists(it) }

        val base = root(context)
        if (!base.exists() && !base.mkdirs()) return PrepareResult.NoVault
        val id = newId(base)
        val dir = File(base, id)
        if (!dir.mkdirs()) return PrepareResult.NoVault

        File(CryptoManager.getCipherRootDirectory(), ".nomedia").let { f ->
            if (!f.exists()) runCatching { f.createNewFile() }
        }
        val header = File(vaultRoot, VAULT_HEADER_FILE)
        if (header.isFile) runCatching { header.copyTo(File(dir, VAULT_HEADER_FILE), overwrite = true) }

        val bridge = Bridge(dir, id, fingerprint, vaultRoot.absolutePath, active = true)
        writeMarker(bridge, active = true)
        setActiveFor(context, fingerprint)
        return PrepareResult.Created(bridge)
    }

    /** Creates a second bridge for the same vault (the "(1)" equivalent of the automatic mode). */
    fun createAdditional(context: Context): PrepareResult {
        val fingerprint = currentFingerprint() ?: return PrepareResult.Locked
        val vaultRoot = CryptoManager.findVaultRoot(context) ?: return PrepareResult.NoVault
        val base = root(context)
        if (!base.exists() && !base.mkdirs()) return PrepareResult.NoVault
        val id = newId(base)
        val dir = File(base, id)
        if (!dir.mkdirs()) return PrepareResult.NoVault
        val header = File(vaultRoot, VAULT_HEADER_FILE)
        if (header.isFile) runCatching { header.copyTo(File(dir, VAULT_HEADER_FILE), overwrite = true) }
        val bridge = Bridge(dir, id, fingerprint, vaultRoot.absolutePath, active = false)
        writeMarker(bridge, active = false)
        return PrepareResult.Created(bridge)
    }

    /** Reuses an existing bridge and refreshes its header/marker (the "use existing" choice). */
    fun refresh(context: Context, bridge: Bridge): Boolean {
        val fingerprint = currentFingerprint() ?: return false
        val vaultRoot = CryptoManager.findVaultRoot(context) ?: return false
        val header = File(vaultRoot, VAULT_HEADER_FILE)
        if (header.isFile) {
            runCatching { header.copyTo(File(bridge.dir, VAULT_HEADER_FILE), overwrite = true) }.onFailure { return false }
        }
        // An archived bridge keeps its original source path: restoreFromArchive() uses it
        // to find the bridge when the vault is later moved back out of the archive.
        val sourcePath = if (bridge.dir.parentFile == archiveRoot(context)) {
            bridge.sourcePath
        } else {
            vaultRoot.absolutePath
        }
        val refreshed = bridge.copy(fingerprint = fingerprint, sourcePath = sourcePath)
        writeMarker(refreshed, active = true)
        setActiveFor(context, fingerprint, preferred = bridge.dir)
        return true
    }

    /** Marks exactly one bridge as active and every other bridge (archived included) as inactive. */
    fun setActiveFor(context: Context, fingerprint: String, preferred: File? = null) {
        val all = bridges(context, includeArchived = true)
        val matching = all.filter { it.fingerprint.equals(fingerprint, ignoreCase = true) }
        val activeDir = preferred?.takeIf { p -> matching.any { it.dir == p } } ?: matching.firstOrNull()?.dir
        all.forEach { writeMarker(it, active = it.dir == activeDir) }
    }

    /**
     * Marks the bridge of [vaultRoot] as the active one without moving it. Used when an
     * archived vault is activated in place: its bridge stays in CLI/Archived but must
     * still be the bridge the CLI selects automatically.
     */
    fun markActiveForVaultPath(context: Context, vaultRoot: File): Boolean {
        val target = runCatching { vaultRoot.canonicalPath }.getOrNull() ?: return false
        val bridge = bridges(context, includeArchived = true).firstOrNull { candidate ->
            candidate.sourcePath.isNotEmpty() &&
                runCatching { File(candidate.sourcePath).canonicalPath }.getOrNull()
                    ?.equals(target, ignoreCase = true) == true
        } ?: return false
        setActiveFor(context, bridge.fingerprint, preferred = bridge.dir)
        return true
    }

    /**
     * Idempotent preparation used by the import flow and by vault creation:
     * create the bridge if missing, otherwise refresh its header and make it
     * the single active bridge. Never creates a second folder for the same KEK.
     */
    fun ensureActiveBridge(context: Context): PrepareResult {
        val fingerprint = currentFingerprint() ?: return PrepareResult.Locked
        val existing = bridgeFor(context, fingerprint)
        if (existing != null) {
            refresh(context, existing)
            return PrepareResult.AlreadyExists(existing)
        }
        return prepare(context)
    }

    /**
     * Removes every CLI bridge that represents one of the deleted vault roots,
     * including the copies moved to [archiveRoot]. A bridge is matched either by
     * its recorded source path or by the fingerprint of the current vault, so
     * additional bridges for the same vault are removed as well. Bridges of a
     * different (for example still archived) vault are left untouched.
     */
    fun deleteBridgesForVaultRoots(context: Context, vaultRoots: List<File>): Int {
        val vaultPaths = vaultRoots.mapNotNull { root ->
            runCatching { root.canonicalPath }.getOrNull()
        }.toSet()
        val fingerprint = currentFingerprint()
        var deleted = 0
        for (bridge in bridges(context, includeArchived = true)) {
            val sourcePath = bridge.sourcePath.takeIf { it.isNotEmpty() }?.let {
                runCatching { File(it).canonicalPath }.getOrNull()
            }
            val representsVault =
                (sourcePath != null && vaultPaths.any { it.equals(sourcePath, ignoreCase = true) }) ||
                    (fingerprint != null && bridge.fingerprint.equals(fingerprint, ignoreCase = true))
            if (!representsVault) continue
            if (deleteBridgeDir(context, bridge.dir)) deleted++
        }
        deleteIfEmpty(archiveRoot(context))
        deleteIfEmpty(root(context))
        return deleted
    }

    private fun deleteBridgeDir(context: Context, dir: File): Boolean {
        if (!dir.exists()) return true
        return runCatching {
            dir.walkBottomUp().forEach { file ->
                if (file.isFile) CryptoManager.secureWipeAndDeleteFile(file, context)
                else if (file.isDirectory) file.delete()
            }
            !dir.exists()
        }.getOrDefault(false)
    }

    private fun deleteIfEmpty(dir: File) {
        val children = dir.listFiles() ?: return
        if (children.isEmpty()) runCatching { dir.delete() }
    }

    fun moveToArchive(context: Context, vaultRoot: File): Boolean {
        val bridge = bridges(context).firstOrNull {
            it.sourcePath.equals(vaultRoot.absolutePath, ignoreCase = true)
        } ?: return true
        val moved = moveFor(context, bridge.fingerprint, root(context), archiveRoot(context))
        // While archived, the bridge is discoverable by the CLI but is not the
        // automatically selected one (the app clears the active vault on archival).
        bridges(context, includeArchived = true)
            .firstOrNull {
                it.fingerprint.equals(bridge.fingerprint, ignoreCase = true) &&
                    it.dir.parentFile == archiveRoot(context)
            }?.let { writeMarker(it, active = false) }
        return moved
    }

    fun restoreFromArchive(context: Context, vaultRoot: File): Boolean {
        val bridge = bridges(context, includeArchived = true).firstOrNull {
            it.sourcePath.equals(vaultRoot.absolutePath, ignoreCase = true) &&
                it.dir.parentFile == archiveRoot(context)
        } ?: return true
        val moved = moveFor(context, bridge.fingerprint, archiveRoot(context), root(context))
        // The vault is active again, so its bridge is the active CLI bridge as well.
        bridges(context).firstOrNull { it.dir == File(root(context), bridge.vaultId) }
            ?.let { restored -> setActiveFor(context, restored.fingerprint, preferred = restored.dir) }
        return moved
    }

    private fun moveFor(context: Context, fingerprint: String, from: File, to: File): Boolean {
        val bridge = bridges(context, includeArchived = true).firstOrNull {
            it.fingerprint.equals(fingerprint, ignoreCase = true) && it.dir.parentFile == from
        } ?: return true
        if (!to.exists() && !to.mkdirs()) return false
        val target = File(to, bridge.vaultId)
        if (target.exists()) return true
        return runCatching { bridge.dir.renameTo(target) }.getOrDefault(false)
    }

    private fun writeMarker(bridge: Bridge, active: Boolean) {
        val marker = File(bridge.dir, CLI_BRIDGE_MARKER_FILE)
        val content = listOf(
            "calypso_bridge=$MARKER_VERSION",
            "vault_id=${bridge.vaultId}",
            "kek_fingerprint=${bridge.fingerprint}",
            "source_path=${bridge.sourcePath}",
            "active=${if (active) 1 else 0}",
        ).joinToString("\n", postfix = "\n")
        val tmp = File(bridge.dir, CLI_BRIDGE_MARKER_FILE + ".tmp")
        runCatching {
            tmp.writeText(content, Charsets.UTF_8)
            if (!tmp.renameTo(marker)) {
                tmp.copyTo(marker, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun newId(base: File): String {
        val bytes = ByteArray(12)
        while (true) {
            SecureRandom().nextBytes(bytes)
            val id = bytes.joinToString("") { "%02x".format(it) }
            if (!File(base, id).exists()) return id
        }
    }
}

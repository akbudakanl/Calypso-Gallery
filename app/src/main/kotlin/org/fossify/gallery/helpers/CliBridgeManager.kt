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
        bridges(context).firstOrNull { it.fingerprint.equals(fingerprint, ignoreCase = true) }

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

        CryptoManager.getCipherRootDirectory().let { File(it, NOMEDIA).let { f -> if (!f.exists()) runCatching { f.createNewFile() } } }
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
        val refreshed = bridge.copy(fingerprint = fingerprint, sourcePath = vaultRoot.absolutePath)
        writeMarker(refreshed, active = true)
        setActiveFor(context, fingerprint, except = bridge.dir)
        return true
    }

    /** Marks bridges of the active vault as active and all others as inactive. */
    fun setActiveFor(context: Context, fingerprint: String, except: File? = null) {
        bridges(context).forEach { bridge ->
            val isActive = bridge.fingerprint.equals(fingerprint, ignoreCase = true) && bridge.dir != except
            writeMarker(bridge, active = isActive)
        }
    }

    /** Called when a vault is created/unlocked so the CLI can auto-select the right bridge. */
    fun syncActiveFlag(context: Context) {
        currentFingerprint()?.let { setActiveFor(context, it) }
    }

    fun moveToArchive(context: Context, vaultRoot: File): Boolean {
        val fingerprint = bridges(context).firstOrNull {
            it.sourcePath.equals(vaultRoot.absolutePath, ignoreCase = true)
        }?.fingerprint ?: return true
        return moveFor(context, fingerprint, root(context), archiveRoot(context))
    }

    fun restoreFromArchive(context: Context, vaultRoot: File): Boolean {
        val fingerprint = bridges(context, includeArchived = true).firstOrNull {
            it.sourcePath.equals(vaultRoot.absolutePath, ignoreCase = true)
        }?.fingerprint ?: return true
        return moveFor(context, fingerprint, archiveRoot(context), root(context))
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

package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.gallery.extensions.config
import java.io.File
import java.security.SecureRandom

/**
 * Local vault archival: a move into `Calypso Gallery/Archive/<random-id>`.
 * Nothing is ever deleted here — see ADR 0007.
 */
object VaultArchiveManager {

    private const val MAX_ID_ATTEMPTS = 10
    private const val ID_BYTE_LENGTH = 12

    private val readmeText = """
        Warning: Do not delete this file

        Calypso Gallery — Arşiv / Archive

        Bu klasördeki alt klasörler daha önce kullanılmış şifreli kasalardır.
        Kasalar silinmemiştir. Uygulama içinden
          Ayarlar → Şifreli kasa → Kasa konumu → Arşivden kasa aç
        yoluyla her kasa kendi parolasıyla tekrar açılabilir.

        Danger: Do not delete this file. Bu dosyayı silmeyin.

        Dosyaları değiştirmeyin veya yeniden adlandırmayın. Kasa şifreli olduğu
        için başka bir uygulamayla açılamaz.

        Disk alanı kazanmak için bir kasayı kalıcı olarak silmek isterseniz,
        ilgili alt klasörü bir dosya yöneticisiyle silebilirsiniz. Bu işlem
        geri alınamaz.

        The subfolders here are encrypted vaults archived earlier. They were not
        deleted. In the app, use Settings → Encrypted vault → Vault location →
        Open from archive to reopen any of them with its own password.

        Do not modify or rename the files; other apps cannot open them.
        To free disk space, delete a vault subfolder with a file manager.
        This cannot be undone.
    """.trimIndent()

    private val infoText = """
        Warning: Do not delete this file

        Bu klasör Calypso Gallery tarafından arşivlenmiş bir şifreli kasadır.
        İçeriğini değiştirmeyin. Uygulama içinden Ayarlar → Şifreli kasa →
        Kasa konumu → Arşivden kasa aç ile açılabilir.

        Danger: Do not delete this file. Bu dosyayı silmeyin.

        This folder is an encrypted vault archived by Calypso Gallery.
        Do not modify its contents. Open it from Settings → Encrypted vault →
        Vault location → Open from archive.
    """.trimIndent()

    fun archiveRoot(): File = File(CryptoManager.getCipherRootDirectory(), ARCHIVE_DIR_NAME)

    fun isActiveVaultArchived(context: Context): Boolean {
        val path = context.config.vaultRootPath
        if (path.isEmpty()) return false
        val root = archiveRoot().absolutePath
        return path.equals(root, ignoreCase = true) ||
            path.startsWith("$root${File.separator}", ignoreCase = true)
    }

    private fun randomId(): String {
        val bytes = ByteArray(ID_BYTE_LENGTH)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun unusedId(archiveDir: File): String? {
        repeat(MAX_ID_ATTEMPTS) {
            val candidate = randomId()
            if (!File(archiveDir, candidate).exists()) return candidate
        }
        return null
    }

    /** Returns the archived entry on success, null on failure (source is left untouched). */
    fun archiveActiveVault(context: Context): ArchivedVault? {
        if (isActiveVaultArchived(context)) return null
        val source = CryptoManager.findVaultRoot(context) ?: return null

        val archiveDir = archiveRoot()
        val createdArchiveDir = !archiveDir.exists()
        if (!archiveDir.exists() && !archiveDir.mkdirs()) return null
        if (createdArchiveDir) {
            runCatching { File(archiveDir, ARCHIVE_README_FILE).writeText(readmeText) }
        }

        val id = unusedId(archiveDir) ?: return null
        val destination = File(archiveDir, id)
        val now = System.currentTimeMillis()
        val entry = ArchivedVault(id, destination.absolutePath, now, source.absolutePath)

        // Record before moving so a crash still leaves a discoverable entry.
        val entries = context.config.archivedVaults
        entries.add(entry)
        context.config.archivedVaults = entries

        if (!source.renameTo(destination)) {
            context.config.archivedVaults = ArrayList(entries.filterNot { it.id == id })
            return null
        }

        runCatching { File(destination, ARCHIVE_INFO_FILE).writeText(infoText) }
        context.config.vaultRootPath = ""
        context.config.isVaultBiometricEnabled = false
        context.config.vaultBiometricWrappedBundle = ""
        CryptoManager.clearBiometricKey(context)
        return entry
    }

    data class ArchivedEntry(val vault: ArchivedVault, val dateKnown: Boolean)

    /** Prefs first; after a reinstall the archive folder is scanned as fallback. */
    fun archivedEntries(context: Context): List<ArchivedEntry> {
        val archiveDir = archiveRoot()
        val stored = context.config.archivedVaults.associateBy { it.id }.toMutableMap()

        archiveDir.takeIf { it.isDirectory }?.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            if (stored.containsKey(dir.name)) return@forEach
            val info = File(dir, ARCHIVE_INFO_FILE)
            val date = if (info.isFile) info.lastModified() else 0L
            stored[dir.name] = ArchivedVault(dir.name, dir.absolutePath, date)
        }

        return stored.values
            .filter { File(it.path).let { f -> f.exists() && CryptoManager.hasValidVaultHeader(f) } }
            .sortedByDescending { it.archivedAt }
            .map { ArchivedEntry(it, it.archivedAt > 0L) }
    }

    fun archiveDate(entry: ArchivedVault): Long {
        if (entry.archivedAt > 0L) return entry.archivedAt
        val info = File(entry.path, ARCHIVE_INFO_FILE)
        return if (info.isFile) info.lastModified() else 0L
    }

    /** Swaps the active vault with the chosen archive, then returns the new active path. */
    @Suppress("ReturnCount")
    fun restoreArchivedVault(context: Context, entry: ArchivedVault): String? {
        val chosen = File(entry.path)
        if (!chosen.exists() || !CryptoManager.hasValidVaultHeader(chosen)) return null

        val current = CryptoManager.findVaultRoot(context)
        val archiveDir = archiveRoot()
        if (!archiveDir.exists() && !archiveDir.mkdirs()) return null

        val entries = ArrayList(context.config.archivedVaults)
        var parked: ArchivedVault? = null
        if (current != null && current.absolutePath != chosen.absolutePath) {
            val id = unusedId(archiveDir) ?: return null
            val parkedDir = File(archiveDir, id)
            if (!current.renameTo(parkedDir)) return null
            val info = File(parkedDir, ARCHIVE_INFO_FILE)
            runCatching { info.writeText(infoText) }
            parked = ArchivedVault(
                id,
                parkedDir.absolutePath,
                System.currentTimeMillis(),
                context.config.vaultRootPath,
            )
            entries.removeAll { it.id == id }
            entries.add(parked)
        }

        val targetPath = current?.absolutePath ?: CryptoManager.getDefaultVaultRoot().absolutePath
        val target = File(targetPath)
        if (!chosen.renameTo(target)) {
            parked?.let { File(it.path).renameTo(File(context.config.vaultRootPath)) }
            return null
        }

        entries.removeAll { it.id == entry.id }
        context.config.archivedVaults = entries
        context.config.vaultRootPath = target.absolutePath
        context.config.isVaultBiometricEnabled = false
        context.config.vaultBiometricWrappedBundle = ""
        CryptoManager.clearBiometricKey(context)
        return target.absolutePath
    }
}

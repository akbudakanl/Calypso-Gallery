package org.fossify.gallery.helpers.vault

import android.content.Context
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.getVaultRoot
import org.fossify.gallery.helpers.setupOrUpdateCredentials
import org.fossify.gallery.helpers.verifyFileIntegrity
import org.fossify.gallery.helpers.openDecryptedContentStream
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * v3 -> v4 kasa donusum motoru.
 *
 * Guvenlik ozellikleri (ADR 0001 §7.3):
 *  - dosya basina atomik degistirme (.tmp -> fsync -> dogrula -> rename)
 *  - dogrulamadan eski dosya asla silinmez
 *  - resumable manifest (KEK + tamamlanan yollar), her dosyadan sonra kaydedilir
 *  - idempotent: dosyalar tekrar islenmez
 */
object VaultMigration {

    private const val MANIFEST_NAME = ".vault_migration"
    private val secureRandom = SecureRandom()

    /** Service calisirken yarim kalan temp dosyalari silinmesin. */
    @Volatile
    var isRunning = false

    /** Servis sonlandirilinca bekleme dongusunu keser. */
    @Volatile
    var cancelRequested = false

    /** Uygulama acilisinda onceki oturumdan kalan .tmp_*.bin dosyalarini temizler. */
    fun cleanupStaleTempFiles(context: Context) {
        if (isRunning) return
        val root = CryptoManager.getVaultRoot(context) ?: return
        root.walkTopDown()
            .filter { it.isFile && it.name.startsWith(".tmp_") && it.extension == "bin" }
            .forEach { runCatching { it.delete() } }
    }

    data class Progress(
        val converted: Int,
        val total: Int,
        val failed: Int,
        val done: Boolean,
        val waiting: Boolean = false
    )

    /** Manifest'i VMK ile sifreleyip yazar. Icerik: KEK + tamamlanan yollar. */
    private fun saveManifest(root: File, vmk: ByteArray, kek: ByteArray, done: Set<String>) {
        val plain = buildString {
            append(Base64.getEncoder().encodeToString(kek)).append('\n')
            done.forEach { append(it).append('\n') }
        }.toByteArray(Charsets.UTF_8)
        val iv = ByteArray(VaultContainer.GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(vmk, "AES"), GCMParameterSpec(VaultContainer.GCM_TAG_BITS, iv))
        }
        val enc = cipher.doFinal(plain)
        val text = Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(enc)
        val manifest = File(root, MANIFEST_NAME)
        manifest.writeText(text)
        RandomAccessFile(manifest, "rw").use { it.fd.sync() }
        plain.fill(0)
    }

    private fun loadManifest(root: File, vmk: ByteArray): Pair<ByteArray, MutableSet<String>>? {
        val manifest = File(root, MANIFEST_NAME)
        if (!manifest.isFile) return null
        val parts = manifest.readText().split(":", limit = 2)
        if (parts.size != 2) return null
        return try {
            val iv = Base64.getDecoder().decode(parts[0])
            val enc = Base64.getDecoder().decode(parts[1])
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(vmk, "AES"), GCMParameterSpec(VaultContainer.GCM_TAG_BITS, iv))
            }
            val plain = cipher.doFinal(enc)
            val lines = String(plain, Charsets.UTF_8).lineSequence().filter { it.isNotEmpty() }.toList()
            plain.fill(0)
            val kek = Base64.getDecoder().decode(lines.first())
            kek to lines.drop(1).toMutableSet()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Tek bir v3 dosyasini kanonik formata donusturur. Dogrulama basarisizsa kaynak dosyaya
     * dokunmaz ve false dondurur (§7.5).
     */
    internal fun convertFile(
        file: File,
        kek: ByteArray,
        vmk: ByteArray,
        keyId: Short = 1
    ): Boolean {
        val meta = CryptoManager.readMetadata(file, includeThumbnail = true, cacheResult = false) ?: return false
        if (meta.isCanonicalFormat) return true
        if (meta.isMacAuthenticated && !CryptoManager.verifyFileIntegrity(file, meta)) return false

        val thumb = meta.thumbnailBytes
        val prepared = VaultContainerWriter.prepare(
            kek = kek,
            keyId = keyId,
            originalName = meta.originalName,
            lastModified = meta.lastModified,
            dateTaken = meta.dateTaken,
            originalSize = meta.originalSize,
            mediaType = meta.mediaType,
            videoDuration = meta.videoDuration,
            thumbnail = thumb
        )
        val tmp = File(file.parentFile, ".tmp_${java.util.UUID.randomUUID()}.bin")
        return try {
            // v3 payload'u once coz, ardindan duz metni kanonik akışa ver.
            // (Ciphertext'i oldugu gibi yazmak cift sifreleme olurdu.)
            val plain = CryptoManager.openDecryptedContentStream(file, 0L, meta) ?: return false
            plain.use { input ->
                VaultContainerWriter.writeContainer(target = tmp, prepared = prepared, payloadProvider = { input })
            }
            // Yeni dosyayi tam decrypt ile dogrula (Tink EOF'ta son tag'i dogrular).
            val newMeta = meta.copy(
                version = VaultContainer.VAULT_FORMAT_VERSION,
                payloadOffset = prepared.payloadOffset,
                contentIv = ByteArray(0),
                fileKey = prepared.dek.copyOf(),
                isMacAuthenticated = true,
                fileId = prepared.fileId.copyOf(),
                keyId = keyId,
                ciphertextLength = prepared.ciphertextLength,
                isCanonicalFormat = true,
                segmentSize = TinkStreamSpec.CIPHERTEXT_SEGMENT_SIZE
            )
            if (!CryptoManager.verifyFileIntegrity(tmp, newMeta)) return false
            CryptoManager.openDecryptedContentStream(tmp, 0L, newMeta)?.use { it.readBytes() } ?: return false

            java.nio.file.Files.move(
                tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
            RandomAccessFile(file, "rw").use { it.fd.sync() }
            true
        } catch (_: Exception) {
            false
        } finally {
            if (tmp.exists()) {
                runCatching { tmp.delete() }
            }
        }
    }

    /**
     * Tum dosyalar donustukten sonra .vault basligini v4 keyring ile yazar.
     * [kek] once unlockedKek'e atanmalidir; setupOrUpdateCredentials mevcut VMK'yi
     * koruyup yeni PDK ile sarar ve keyring'i VMK altina yazar.
     */
    fun finalize(context: Context, secret: String, protectionType: Int, kek: ByteArray): Boolean {
        val root = CryptoManager.getVaultRoot(context) ?: return false
        CryptoManager.unlockedKek = kek.copyOf()
        return try {
            CryptoManager.setupOrUpdateCredentials(context, secret, protectionType)
            File(root, MANIFEST_NAME).delete()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Yarida kalmis bir migrasyon varsa (done, toplam) dondurur; yoksa null.
     * Kesinti tespit edildiginda "her zaman sor" ayari yeniden acilir.
     */
    fun incompleteProgress(context: Context): Pair<Int, Int>? {
        val root = CryptoManager.getVaultRoot(context) ?: return null
        val vmk = CryptoManager.unlockedMasterKey ?: return null
        val parsed = loadManifest(root, vmk) ?: return null
        val files = root.walkTopDown()
            .filter { it.isFile && it.extension == "bin" && !it.name.startsWith(".tmp_") }
            .toList()
        val done = parsed.second.size
        return if (done < files.size) done to files.size else null
    }

    /** Kaydedilmis KEK'i manifest'ten okur (kesinti sonrasi devam icin). */
    fun savedKek(context: Context): ByteArray? {
        val root = CryptoManager.getVaultRoot(context) ?: return null
        val vmk = CryptoManager.unlockedMasterKey ?: return null
        return loadManifest(root, vmk)?.first
    }

    /** Tum kasayi kanonik formata donusturur; kesinti sonrasi manifest'ten devam eder. */
    fun migrate(context: Context, onProgress: (Progress) -> Unit = {}): Progress {
        cancelRequested = false
        val root = CryptoManager.getVaultRoot(context) ?: return Progress(0, 0, 0, true)
        val vmk = CryptoManager.unlockedMasterKey ?: return Progress(0, 0, 0, true)
        if (CryptoManager.getVaultFormat() != 3) return Progress(0, 0, 0, true)

        val existing = loadManifest(root, vmk)
        val kek = existing?.first ?: ByteArray(32).also { secureRandom.nextBytes(it) }
        val done = existing?.second ?: mutableSetOf()

        val files = root.walkTopDown()
            .filter { it.isFile && it.extension == "bin" && !it.name.startsWith(".tmp_") }
            .toList()
        var converted = done.size
        var failed = 0

        files.forEach { file ->
            val rel = file.relativeTo(root).path
            if (rel in done) return@forEach

            var success = false
            while (!cancelRequested && !success) {
                // Harici disk çikarsa bekle: anahtarlar RAM'de kalir, servis ayakta,
                // disk geri geldiginde manifest'ten kaldigimiz yerden devam ederiz.
                while (!cancelRequested && !(root.exists() && root.canWrite())) {
                    onProgress(Progress(converted, files.size, failed, false, true))
                    Thread.sleep(1000L)
                }
                if (cancelRequested) return@forEach

                success = runCatching { convertFile(file, kek, vmk) }.getOrDefault(false)
                if (!success && !(root.exists() && root.canWrite())) {
                    // Disk yazma sirasinda çikti: hata saymak yerine yeniden bekle.
                    continue
                }
                if (!success) break // gerçek doğrulama hatasi -> failed
            }

            if (success) {
                done += rel
                converted++
                runCatching { saveManifest(root, vmk, kek, done) }
            } else if (!cancelRequested) {
                failed++
            }
            onProgress(Progress(converted, files.size, failed, false))
        }

        // .vault basligi bu motor tarafindan degistirilmez: sifre gerektirir.
        // Service, tum dosyalar donusunce unlockedKek'i ayarlayip
        // setupOrUpdateCredentials() ile kanonik başlıkni yazar, ardindan manifest'i siler.
        return Progress(converted, files.size, failed, failed == 0 && done.size >= files.size)
    }
}

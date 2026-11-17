package org.fossify.gallery.helpers.vault

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.nio.channels.Channels
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * v4 (`VERSION_TINK_STREAM`) konteynerinin **tek yazma yolu**.
 *
 * Duzen:
 *   magic(8) | version=4(1) | keyId(2) | fileId(16) | headerIv(12) | N(4) |
 *   encHeader(N) | Tink ciphertext(T)
 *
 * Header, KEK'ten fileId ile turetilen bir anahtarla AES-256-GCM ile sifrelenir;
 * AAD cleartext onektir. Payload, dosyaya ozel rastgele DEK ile Tink
 * AES256_GCM_HKDF akisina sifrelenir; AAD = fileId.
 */
internal object VaultContainerWriterV4 {

    private const val BUFFER_SIZE = 256 * 1024
    private val secureRandom = SecureRandom()

    class Prepared(
        val version: Byte,
        val keyId: Short,
        val fileId: ByteArray,
        val dek: ByteArray,
        val headerIv: ByteArray,
        val encryptedHeader: ByteArray,
        val payloadOffset: Long,
        val ciphertextLength: Long
    )

    fun prepare(
        kek: ByteArray,
        keyId: Short,
        originalName: String,
        lastModified: Long,
        dateTaken: Long,
        originalSize: Long,
        mediaType: Int,
        videoDuration: Int,
        thumbnail: ByteArray?,
        version: Byte = VaultContainer.VERSION_TINK_STREAM
    ): Prepared {
        val fileId = ByteArray(VaultContainer.FILE_ID_LENGTH).also { secureRandom.nextBytes(it) }
        val dek = ByteArray(TinkStreamSpec.KEY_SIZE_BYTES).also { secureRandom.nextBytes(it) }
        val ciphertextLength = TinkStreamSpec.expectedCiphertextSize(originalSize)

        val headerPlain = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                dos.writeShort(VaultContainer.ALGORITHM_PARAMS_V4.toInt())
                dos.writeInt(TinkStreamSpec.CIPHERTEXT_SEGMENT_SIZE)
                dos.writeLong(ciphertextLength)
                dos.write(dek)
                dos.writeUTF(originalName)
                dos.writeLong(lastModified)
                dos.writeLong(dateTaken)
                dos.writeLong(originalSize)
                dos.writeInt(mediaType)
                dos.writeInt(videoDuration)
                dos.writeInt(thumbnail?.size ?: 0)
                if (thumbnail != null) dos.write(thumbnail)
            }
            baos.toByteArray()
        }

        val headerIv = ByteArray(VaultContainer.GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val encHeader = encryptHeader(kek, fileId, keyId, version, headerIv, headerPlain)
        return Prepared(
            version = version,
            keyId = keyId,
            fileId = fileId,
            dek = dek,
            headerIv = headerIv,
            encryptedHeader = encHeader,
            payloadOffset = VaultContainer.payloadOffsetV4(encHeader.size),
            ciphertextLength = ciphertextLength
        )
    }

    fun writeContainer(
        target: File,
        prepared: Prepared,
        payloadProvider: () -> InputStream,
        onPayloadBytes: ((Long) -> Unit)? = null
    ): Long {
        var written = 0L
        FileOutputStream(target).use { fos ->
            val dos = DataOutputStream(BufferedOutputStream(fos, BUFFER_SIZE))
            dos.write(VaultContainer.MAGIC_V2.toByteArray(Charsets.US_ASCII))
            dos.writeByte(prepared.version.toInt())
            dos.writeShort(prepared.keyId.toInt())
            dos.write(prepared.fileId)
            dos.write(prepared.headerIv)
            dos.writeInt(prepared.encryptedHeader.size)
            dos.write(prepared.encryptedHeader)
            dos.flush()

            val aead = TinkStreamSpec.streamingAead(prepared.dek)
            val out = aead.newEncryptingStream(fos, prepared.fileId)
            payloadProvider().use { input ->
                val buf = ByteArray(BUFFER_SIZE)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    written += read
                    onPayloadBytes?.invoke(written)
                }
            }
            out.close()
        }
        // Tink'in encrypting stream'i alttaki FileOutputStream'u da kapatir;
        // fsync icin dosyayi yeniden acip fd.sync() yapariz.
        RandomAccessFile(target, "rw").use { it.fd.sync() }
        return written
    }

    /**
     * Akis sifrelemesinde boyut sonradan belli oldugunda header'i yerinde gunceller.
     * Tum alanlar sabit uzunluklu oldugu icin payload ofseti kaymaz; fileId ve dek
     * korundugu icin Tink akisi gecerliligini korur.
     */
    fun rewriteHeaderInPlace(
        file: File,
        kek: ByteArray,
        keyId: Short,
        prepared: Prepared,
        originalName: String,
        lastModified: Long,
        dateTaken: Long,
        originalSize: Long,
        mediaType: Int,
        videoDuration: Int,
        thumbnail: ByteArray?
    ) {
        val ciphertextLength = TinkStreamSpec.expectedCiphertextSize(originalSize)
        val headerPlain = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                dos.writeShort(VaultContainer.ALGORITHM_PARAMS_V4.toInt())
                dos.writeInt(TinkStreamSpec.CIPHERTEXT_SEGMENT_SIZE)
                dos.writeLong(ciphertextLength)
                dos.write(prepared.dek)
                dos.writeUTF(originalName)
                dos.writeLong(lastModified)
                dos.writeLong(dateTaken)
                dos.writeLong(originalSize)
                dos.writeInt(mediaType)
                dos.writeInt(videoDuration)
                dos.writeInt(thumbnail?.size ?: 0)
                if (thumbnail != null) dos.write(thumbnail)
            }
            baos.toByteArray()
        }
        // Yeni header uzunlugu degisirse yerinde yazim guvenli degildir; boyut ayni
        // oldugu icin (ayni dosya adi/thumbnail) eski uzunlukla karsilastir.
        require(headerPlain.size + 16 == prepared.encryptedHeader.size) {
            "v4 header length changed; in-place rewrite is unsafe"
        }
        val newIv = ByteArray(VaultContainer.GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val encHeader = encryptHeader(kek, prepared.fileId, keyId, prepared.version, newIv, headerPlain)
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(VaultContainer.FIXED_PREFIX_LENGTH_V4.toLong())
            raf.write(newIv)
            raf.writeInt(encHeader.size)
            raf.write(encHeader)
            raf.fd.sync()
        }
    }

    private fun encryptHeader(
        kek: ByteArray,
        fileId: ByteArray,
        keyId: Short,
        version: Byte,
        headerIv: ByteArray,
        headerPlain: ByteArray
    ): ByteArray {
        val headerKey = VaultKeyDerivation.deriveFileHeaderKey(kek, fileId)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(headerKey, "AES"),
                GCMParameterSpec(VaultContainer.GCM_TAG_BITS, headerIv)
            )
            cipher.updateAAD(
                v4HeaderAad(
                    VaultContainer.MAGIC_V2.toByteArray(Charsets.US_ASCII),
                    version, keyId, fileId, headerPlain.size + 16
                )
            )
            cipher.doFinal(headerPlain)
        } finally {
            headerKey.fill(0)
        }
    }
}

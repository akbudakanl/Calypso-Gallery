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
 * Kanonik konteynerin **tek yazma yolu**.
 *
 * Duzen:
 *   magic(8) | version=1(1) | keyId(2) | fileId(16) | headerIv(12) | N(4) |
 *   encHeader(N) | Tink ciphertext(T)
 *
 * Header, KEK'ten fileId ile turetilen bir anahtarla AES-256-GCM ile sifrelenir;
 * AAD cleartext onektir. Payload, dosyaya ozel rastgele DEK ile Tink
 * AES256_GCM_HKDF akisina sifrelenir; AAD = fileId.
 */
internal object VaultContainerWriter {

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
        version: Byte = VaultContainer.VAULT_FORMAT_VERSION
    ): Prepared {
        val fileId = ByteArray(VaultContainer.FILE_ID_LENGTH).also { secureRandom.nextBytes(it) }
        val dek = ByteArray(TinkStreamSpec.KEY_SIZE_BYTES).also { secureRandom.nextBytes(it) }
        val ciphertextLength = TinkStreamSpec.expectedCiphertextSize(originalSize)

        val headerPlain = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                dos.writeShort(VaultContainer.ALGORITHM_PARAMS.toInt())
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
            payloadOffset = VaultContainer.payloadOffset(encHeader.size),
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
            dos.write(VaultContainer.VAULT_MAGIC.toByteArray(Charsets.US_ASCII))
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
                dos.writeShort(VaultContainer.ALGORITHM_PARAMS.toInt())
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
            "header length changed; in-place rewrite is unsafe"
        }
        val newIv = ByteArray(VaultContainer.GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val encHeader = encryptHeader(kek, prepared.fileId, keyId, prepared.version, newIv, headerPlain)
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(VaultContainer.FIXED_PREFIX_LENGTH.toLong())
            raf.write(newIv)
            raf.writeInt(encHeader.size)
            raf.write(encHeader)
            raf.fd.sync()
        }
    }

    /**
     * Yeniden adlandirma icin mevcut bir kanonik konteynerni yeni header ile yeniden yazar.
     *
     * Tink sifreli payload **aynen kopyalanir**; fileId ve DEK korundugu icin
     * dogrulanmis akis gecerliligini korur. Yalnizca header yeni IV ile
     * HKDF(KEK, fileId) anahtariyla yeniden sifrelenir. Dosya adi degistiginde
     * header uzunlugu degisebileceginden payload ofseti kayar; bu yuzden hedef
     * dosya bastan yazilir (atomik move cagiran tarafta yapilir).
     *
     * @return yeni payload ofseti
     */
    fun rewriteContainer(
        source: File,
        target: File,
        kek: ByteArray,
        keyId: Short,
        fileId: ByteArray,
        dek: ByteArray,
        version: Byte,
        sourcePayloadOffset: Long,
        ciphertextLength: Long,
        segmentSize: Int,
        originalName: String,
        lastModified: Long,
        dateTaken: Long,
        originalSize: Long,
        mediaType: Int,
        videoDuration: Int,
        thumbnail: ByteArray?,
        syncFile: Boolean = true
    ): Long {
        require(TinkStreamSpec.isSupportedSegmentSize(segmentSize)) {
            "Unsupported segment size: $segmentSize"
        }
        require(TinkStreamSpec.expectedCiphertextSize(originalSize, segmentSize) == ciphertextLength) {
            "Ciphertext length does not match the declared plaintext size"
        }

        val headerPlain = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                dos.writeShort(VaultContainer.ALGORITHM_PARAMS.toInt())
                dos.writeInt(segmentSize)
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
        val newPayloadOffset = VaultContainer.payloadOffset(encHeader.size)

        FileOutputStream(target).use { fos ->
            val dos = DataOutputStream(BufferedOutputStream(fos, BUFFER_SIZE))
            dos.write(VaultContainer.VAULT_MAGIC.toByteArray(Charsets.US_ASCII))
            dos.writeByte(version.toInt())
            dos.writeShort(keyId.toInt())
            dos.write(fileId)
            dos.write(headerIv)
            dos.writeInt(encHeader.size)
            dos.write(encHeader)
            dos.flush()

            RandomAccessFile(source, "r").use { raf ->
                raf.seek(sourcePayloadOffset)
                val buf = ByteArray(BUFFER_SIZE)
                var remaining = ciphertextLength
                while (remaining > 0) {
                    val chunk = minOf(buf.size.toLong(), remaining).toInt()
                    raf.readFully(buf, 0, chunk)
                    dos.write(buf, 0, chunk)
                    remaining -= chunk
                }
            }
            dos.flush()
        }
        if (syncFile) {
            RandomAccessFile(target, "rw").use { it.fd.sync() }
        }
        return newPayloadOffset
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
                headerAad(
                    VaultContainer.VAULT_MAGIC.toByteArray(Charsets.US_ASCII),
                    version, keyId, fileId, headerPlain.size + 16
                )
            )
            cipher.doFinal(headerPlain)
        } finally {
            headerKey.fill(0)
        }
    }
}

package org.fossify.gallery.helpers.vault

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import org.fossify.gallery.helpers.NativeVaultSession
import org.fossify.gallery.helpers.operations.EncryptedSizeInput

/**
 * Kanonik konteynerin **tek yazma yolu**.
 *
 * Duzen:
 *   magic(8) | version=1(1) | keyId(2) | fileId(16) | headerIv(12) | N(4) |
 *   encHeader(N) | Tink ciphertext(T)
 *
 * Header, Rust oturumundaki KEK'ten `fileId` ile turetilen bir anahtarla AES-256-GCM ile sifrelenir;
 * AAD cleartext onektir. Payload, dosyaya ozel rastgele DEK ile Tink
 * AES256_GCM_HKDF akisina sifrelenir; AAD = fileId.
 */
internal object VaultContainerWriter {

    private const val BUFFER_SIZE = 256 * 1024
    private const val MAX_ENC_HEADER_BYTES = 5 * 1024 * 1024

    class ContainerMetadataFields(
        val originalName: String,
        val lastModified: Long,
        val dateTaken: Long,
        val originalSize: Long,
        val mediaType: Int,
        val videoDuration: Int,
        val thumbnail: ByteArray?
    )

    class Prepared(
        val version: Byte,
        val keyId: Short,
        val fileId: ByteArray,
        val dek: ByteArray,
        val headerIv: ByteArray,
        val encryptedHeader: ByteArray,
        val payloadOffset: Long,
        val ciphertextLength: Long
    ) {
        fun wipe() {
            dek.fill(0)
        }
    }

    fun prepare(
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
        val ciphertextLength = TinkStreamSpec.expectedCiphertextSize(originalSize)
        val metaTail = buildMetaTail(
            originalName = originalName,
            lastModified = lastModified,
            dateTaken = dateTaken,
            originalSize = originalSize,
            mediaType = mediaType,
            videoDuration = videoDuration,
            thumbnail = thumbnail
        )
        val packed = NativeVaultSession.encryptNewFileHeaderNative(
            version = version.toInt(),
            keyId = keyId.toInt(),
            segmentSize = TinkStreamSpec.CIPHERTEXT_SEGMENT_SIZE,
            ciphertextLength = ciphertextLength,
            metaTail = metaTail
        )
        val parsed = NativeVaultSession.unpackNewFileHeader(packed)
        return Prepared(
            version = version,
            keyId = keyId,
            fileId = parsed.fileId,
            dek = parsed.dek,
            headerIv = parsed.headerIv,
            encryptedHeader = parsed.encHeader,
            payloadOffset = VaultContainer.payloadOffset(parsed.encHeader.size),
            ciphertextLength = ciphertextLength
        )
    }

    private fun buildMetaTail(
        originalName: String,
        lastModified: Long,
        dateTaken: Long,
        originalSize: Long,
        mediaType: Int,
        videoDuration: Int,
        thumbnail: ByteArray?
    ): ByteArray = ByteArrayOutputStream().use { baos ->
        DataOutputStream(baos).use { dos ->
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

    /** Single source of truth for the header layout, shared by [prepare] and the size estimator. */
    private fun writeHeaderPlain(
        dos: DataOutputStream,
        dek: ByteArray,
        ciphertextLength: Long,
        originalName: String,
        lastModified: Long,
        dateTaken: Long,
        originalSize: Long,
        mediaType: Int,
        videoDuration: Int,
        thumbnail: ByteArray?
    ) {
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

    /**
     * Exact size of the container that [prepare] + [writeContainer] would produce. Used by
     * `SpaceEstimator` (ADR 0003 §3a) before any byte is written, so low-storage decisions use the
     * same header layout as the writer.
     */
    fun estimateEncryptedSize(input: EncryptedSizeInput): Long {
        val ciphertextLength = TinkStreamSpec.expectedCiphertextSize(input.originalSize, input.segmentSize)
        val headerPlainSize = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                writeHeaderPlain(
                    dos = dos,
                    dek = ByteArray(TinkStreamSpec.KEY_SIZE_BYTES),
                    ciphertextLength = ciphertextLength,
                    originalName = input.originalName,
                    lastModified = 0L,
                    dateTaken = 0L,
                    originalSize = input.originalSize,
                    mediaType = 0,
                    videoDuration = 0,
                    thumbnail = if (input.thumbnailSize > 0) ByteArray(input.thumbnailSize) else null
                )
            }
            baos.size()
        }
        val encryptedHeaderSize = headerPlainSize + 16 // GCM tag
        return VaultContainer.payloadOffset(encryptedHeaderSize) + ciphertextLength
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
        keyId: Short,
        prepared: Prepared,
        metadata: ContainerMetadataFields
    ) {
        val ciphertextLength = TinkStreamSpec.expectedCiphertextSize(metadata.originalSize)
        val metaTail = buildMetaTail(
            originalName = metadata.originalName,
            lastModified = metadata.lastModified,
            dateTaken = metadata.dateTaken,
            originalSize = metadata.originalSize,
            mediaType = metadata.mediaType,
            videoDuration = metadata.videoDuration,
            thumbnail = metadata.thumbnail
        )
        val packed = NativeVaultSession.encryptFileHeaderWithDekNative(
            version = prepared.version.toInt(),
            keyId = keyId.toInt(),
            fileId = prepared.fileId,
            segmentSize = TinkStreamSpec.CIPHERTEXT_SEGMENT_SIZE,
            ciphertextLength = ciphertextLength,
            dek = prepared.dek,
            metaTail = metaTail
        )
        val newIv = packed.copyOfRange(0, VaultContainer.GCM_IV_LENGTH)
        val encHeader = packed.copyOfRange(VaultContainer.GCM_IV_LENGTH, packed.size)
        // Yeni header uzunlugu degisirse yerinde yazim guvenli degildir; boyut ayni
        // oldugu icin (ayni dosya adi/thumbnail) eski uzunlukla karsilastir.
        require(encHeader.size == prepared.encryptedHeader.size) {
            "header length changed; in-place rewrite is unsafe"
        }
        RandomAccessFile(file, "rw").use { raf ->
            // IV, header uzunluğu ve şifreli header sırayla 27 / 39 / 43. offset'inde başlar.
            // FIXED_PREFIX_LENGTH (43) header'ın kendisini gösterir; oraya yazmak N ve IV'yi bozar.
            raf.seek(VaultContainer.HEADER_IV_OFFSET.toLong())
            raf.write(newIv)
            raf.writeInt(encHeader.size)
            raf.write(encHeader)
            raf.fd.sync()
        }
    }

    /**
     * Yeniden adlandirma icin mevcut bir kanonik konteyneri yeni header ile yeniden yazar.
     *
     * Tink sifreli payload **aynen kopyalanir**; header Rust icinde re-encrypt edilir ve
     * DEK JVM bellegine hic cikmaz.
     *
     * @return yeni payload ofseti
     */
    fun rewriteContainer(
        source: File,
        target: File,
        keyId: Short,
        fileId: ByteArray,
        version: Byte,
        sourcePayloadOffset: Long,
        ciphertextLength: Long,
        segmentSize: Int,
        metadata: ContainerMetadataFields,
        syncFile: Boolean = true
    ): Long {
        require(TinkStreamSpec.isSupportedSegmentSize(segmentSize)) {
            "Unsupported segment size: $segmentSize"
        }
        require(TinkStreamSpec.expectedCiphertextSize(metadata.originalSize, segmentSize) == ciphertextLength) {
            "Ciphertext length does not match the declared plaintext size"
        }

        val (oldHeaderIv, oldEncHeader) = RandomAccessFile(source, "r").use { raf ->
            raf.seek(VaultContainer.HEADER_IV_OFFSET.toLong())
            val iv = ByteArray(VaultContainer.GCM_IV_LENGTH).also { raf.readFully(it) }
            val len = raf.readInt()
            require(len in 1..MAX_ENC_HEADER_BYTES) { "Invalid encrypted header length: $len" }
            val enc = ByteArray(len).also { raf.readFully(it) }
            iv to enc
        }

        val newMetaTail = buildMetaTail(
            originalName = metadata.originalName,
            lastModified = metadata.lastModified,
            dateTaken = metadata.dateTaken,
            originalSize = metadata.originalSize,
            mediaType = metadata.mediaType,
            videoDuration = metadata.videoDuration,
            thumbnail = metadata.thumbnail
        )
        val packed = NativeVaultSession.reencryptFileHeaderNative(
            version = version.toInt(),
            keyId = keyId.toInt(),
            fileId = fileId,
            oldHeaderIv = oldHeaderIv,
            oldEncHeader = oldEncHeader,
            newMetaTail = newMetaTail
        ) ?: error("Failed to re-encrypt container header")

        val headerIv = packed.copyOfRange(0, VaultContainer.GCM_IV_LENGTH)
        val encHeader = packed.copyOfRange(VaultContainer.GCM_IV_LENGTH, packed.size)
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
}

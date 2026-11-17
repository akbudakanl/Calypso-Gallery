package org.fossify.gallery.helpers.vault

import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel
import java.nio.file.StandardOpenOption

/**
 * kanonik konteyner icin Tink AES256_GCM_HKDF akis spec'i.
 *
 * Bu dosya, Android ve masaustu CLI arasinda **donmus (frozen)** olan parametrelerin
 * tek Kotlin tarafidir. Degistirilirse mevcut kasalar okunamaz hale gelir.
 * Degisiklik yalnizca yeni bir konteyner surumu ile yapilabilir.
 *
 * ```
 * ALGORITHM         = AES256_GCM_HKDF
 * KEY_SIZE          = 32
 * DERIVED_KEY_SIZE  = 32
 * HKDF_HASH         = SHA-256
 * SEGMENT_SIZE      = 65536
 * OUTPUT_PREFIX     = RAW (subtle API, keyset prefix yok)
 * TINK_AAD          = fileId (16 ham bayt)
 * ```
 */
internal object TinkStreamSpec {

    const val KEY_SIZE_BYTES = 32
    const val DERIVED_KEY_SIZE_BYTES = 32
    /**
     * Yazici varsayilani - ADR §11.1 JVM olcumu ile 256 KiB secildi
     * (610.5 MiB/s'in %10'u icinde kalan en kucuk deger). Cihaz probe'u ile
     * dogrulanmaya devam eder. Okuyucu header'daki degeri kullanir; desteklenen
     * tum degerler [isSupportedSegmentSize] ile kabul edilir.
     */
    const val CIPHERTEXT_SEGMENT_SIZE = 262_144

    val SUPPORTED_SEGMENT_SIZES = setOf(16 * 1024, 64 * 1024, 256 * 1024, 1024 * 1024)

    fun isSupportedSegmentSize(size: Int): Boolean = size in SUPPORTED_SEGMENT_SIZES
    const val TAG_SIZE_BYTES = 16
    const val HKDF_ALG = "HmacSha256"
    const val FIRST_SEGMENT_OFFSET = 0

    /** Tink akis basligi: u8 len + salt(32) + noncePrefix(7) = 40 bayt. */
    const val STREAM_HEADER_LENGTH = 1 + DERIVED_KEY_SIZE_BYTES + 7

    /** Yazici icin varsayilan segment boyutuyla primitive. AAD = fileId. */
    fun streamingAead(dek: ByteArray): StreamingAead = streamingAead(dek, CIPHERTEXT_SEGMENT_SIZE)

    /** Okuyucu icin: header'da yazan segment boyutunu kullanir. */
    fun streamingAead(dek: ByteArray, segmentSize: Int): StreamingAead {
        require(isSupportedSegmentSize(segmentSize)) { "Unsupported Tink segment size: $segmentSize" }
        return AesGcmHkdfStreaming(
            dek,
            HKDF_ALG,
            DERIVED_KEY_SIZE_BYTES,
            segmentSize,
            FIRST_SEGMENT_OFFSET
        )
    }

    /**
     * Verilen duz metin uzunlugu icin Tink sifreli akisinin tam bayt uzunlugu.
     * Tink'in `expectedCiphertextSize` hesabiyla birebir aynidir; dosya boyutu
     * kontrolu (truncation / eklenen bayt tespiti) icin kullanilir.
     */
    fun expectedCiphertextSize(plaintextSize: Long): Long =
        expectedCiphertextSize(plaintextSize, CIPHERTEXT_SEGMENT_SIZE)

    fun expectedCiphertextSize(plaintextSize: Long, segmentSize: Int): Long {
        require(isSupportedSegmentSize(segmentSize)) { "Unsupported Tink segment size: $segmentSize" }
        // Bos duz metin icin bile Tink 40 baytlik akis basligi + 16 baytlik son segment
        // tag'i yazar (toplam 56 bayt); 0 donmek yanlistir.
        val plaintextSegmentSize = (segmentSize - TAG_SIZE_BYTES).toLong()
        val offset = (STREAM_HEADER_LENGTH + FIRST_SEGMENT_OFFSET).toLong()
        val fullSegments = (plaintextSize + offset) / plaintextSegmentSize
        val lastSegmentSize = (plaintextSize + offset) % plaintextSegmentSize
        var size = fullSegments * segmentSize.toLong()
        if (lastSegmentSize > 0L) size += lastSegmentSize + TAG_SIZE_BYTES
        return size
    }
}

/**
 * `.bin` dosyasinin yalnizca Tink akisini iceren bolumune erisen ince
 * [SeekableByteChannel] adaptoru. App'in kendi basligi (43+N bayt) saklanir;
 * Tink tum pozisyonlari 0'dan baslayarak gorur.
 */
internal class VaultPayloadChannel(
    private val fileChannel: FileChannel,
    private val payloadOffset: Long,
    private val ciphertextLength: Long
) : SeekableByteChannel {

    private var position = 0L

    override fun isOpen(): Boolean = fileChannel.isOpen

    override fun close() {
        fileChannel.close()
    }

    override fun read(dst: ByteBuffer): Int {
        if (position >= ciphertextLength) return -1
        val remaining = (ciphertextLength - position).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (remaining == 0) return -1
        val limit = minOf(dst.remaining(), remaining)
        if (limit == 0) return 0
        val originalLimit = dst.limit()
        dst.limit(dst.position() + limit)
        fileChannel.position(payloadOffset + position)
        val read = try {
            fileChannel.read(dst)
        } finally {
            dst.limit(originalLimit)
        }
        if (read > 0) position += read
        return read
    }

    override fun write(src: ByteBuffer): Int = throw NonWritableChannelException()

    override fun position(): Long = position

    override fun position(newPosition: Long): SeekableByteChannel {
        position = newPosition.coerceAtLeast(0L)
        return this
    }

    override fun size(): Long = ciphertextLength

    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()
}

/**
 * kanonik konteynernin Tink akisini rastgele erisimle acar.
 * AAD immutable [fileId]'dir; [EncryptedFileMetadata.fileKey] bu dosyanin DEK'idir.
 */
internal fun openPayloadChannel(
    file: java.io.File,
    payloadOffset: Long,
    ciphertextLength: Long,
    dek: ByteArray,
    fileId: ByteArray,
    segmentSize: Int
): SeekableByteChannel {
    val fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ)
    return try {
        val payload = VaultPayloadChannel(fileChannel, payloadOffset, ciphertextLength)
        TinkStreamSpec.streamingAead(dek, segmentSize).newSeekableDecryptingChannel(payload, fileId)
    } catch (e: Exception) {
        fileChannel.close()
        throw e
    }
}

/**
 * header GCM AAD'si: magic | version | keyId | fileId | N.
 *
 * CRITICAL: N, **sifreli** header uzunlugudur (duz metin + 16 bayt GCM tag).
 * Yazici, sifrelemeden once N'yi `headerPlain.size + 16` olarak hesaplayip AAD'ye
 * koymali; okuyucu dosyadaki N degerini okur. Ikisi farkli olursa AEADBadTagException
 * alinir (bu hata Stage 0 testinde yakalandi ve duzeltildi).
 */
internal fun headerAad(
    magic: ByteArray,
    version: Byte,
    keyId: Short,
    fileId: ByteArray,
    encryptedHeaderLength: Int
): ByteArray {
    val aad = ByteBuffer.allocate(
        magic.size + 1 + VaultContainer.KEY_ID_LENGTH +
            VaultContainer.FILE_ID_LENGTH + 4
    )
    aad.put(magic)
    aad.put(version)
    aad.putShort(keyId)
    aad.put(fileId)
    aad.putInt(encryptedHeaderLength)
    return aad.array()
}

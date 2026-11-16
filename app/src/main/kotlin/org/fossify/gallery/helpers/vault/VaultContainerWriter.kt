package org.fossify.gallery.helpers.vault

import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV

/**
 * Sifreli medya konteyneri (`.bin`) **tek yazma yolu**.
 *
 * Butun format yazan fonksiyonlar buradan gecer; boylece format degisikliginde
 * tek bir dosya guncellenir. Duzen ve sifreleme semasi:
 *
 * ```
 * magic | version | headerIv | headerLen | encryptedHeader(GCM) | contentIv |
 * payload(AES-256-CTR) | Poly1305-AES MAC
 * ```
 *
 * Payload tek gecisli olarak hem sifrelenir hem MAC hesaplanir
 * (encrypt-then-MAC). Alt anahtarlar (contentEncKey / contentMacKey) kullanildiktan
 * hemen sonra sifirlanir.
 */
internal object VaultContainerWriter {

    private const val DEFAULT_PAYLOAD_BUFFER_SIZE = 256 * 1024
    private const val STREAM_BUFFER_SIZE = 64 * 1024

    private val secureRandom = SecureRandom()

    /** Yazma icin hazirlanmis (sifrelenmis) header ve payload IV'su. */
    class Prepared(
        val version: Byte,
        val headerIv: ByteArray,
        val encryptedHeader: ByteArray,
        val contentIv: ByteArray
    )

    /**
     * Header'i sifreler ve payload icin content IV uretir.
     *
     * @param headerPlainBytes duz header (dosya adi, boyut, tarih, thumbnail...)
     * @param fileKey master key
     * @param version konteyner surumu
     */
    fun prepare(headerPlainBytes: ByteArray, fileKey: ByteArray, version: Byte): Prepared {
        val headerKey = VaultKeyDerivation.deriveSubkey(fileKey, VaultKeyDerivation.DOMAIN_HEADER_KEY)
        val (headerIv, encryptedHeader) = encryptHeader(headerPlainBytes, headerKey)
        headerKey.fill(0)

        val contentIv = ByteArray(VaultContainer.CTR_IV_LENGTH).also { secureRandom.nextBytes(it) }
        return Prepared(version, headerIv, encryptedHeader, contentIv)
    }

    /**
     * Konteyneri yazar: magic + version + header + payload (MAC'li) + MAC etiketi.
     *
     * @param target yazilacak .bin dosyasi
     * @param prepared [prepare] ciktisi
     * @param fileKey master key
     * @param payloadProvider duz metin akisi (yazan taraf kapatir)
     * @param bufferSize okuma tamponu
     * @param onPayloadBytes yazilan duz metin bayt sayaci (ilerleme bildirimi icin)
     * @return yazilan duz metin bayt sayisi
     */
    fun writeContainer(
        target: File,
        prepared: Prepared,
        fileKey: ByteArray,
        payloadProvider: () -> InputStream,
        bufferSize: Int = DEFAULT_PAYLOAD_BUFFER_SIZE,
        onPayloadBytes: ((Long) -> Unit)? = null
    ): Long {
        val contentEncKey = VaultKeyDerivation.deriveSubkey(
            fileKey, VaultKeyDerivation.DOMAIN_CONTENT_ENC_KEY
        )
        val contentMacKey = VaultKeyDerivation.deriveSubkey(
            fileKey, VaultKeyDerivation.DOMAIN_CONTENT_MAC_KEY
        )

        val contentCipher = Cipher.getInstance("AES/CTR/NoPadding")
        contentCipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(contentEncKey, "AES"),
            IvParameterSpec(prepared.contentIv)
        )
        contentEncKey.fill(0)

        val poly1305 = Poly1305(AESEngine.newInstance())
        poly1305.init(ParametersWithIV(KeyParameter(contentMacKey), prepared.contentIv))
        contentMacKey.fill(0)

        var written = 0L
        writeToTarget(target, syncFile = true) { dos ->
            writeHeaderBytes(
                dos = dos,
                magic = VaultContainer.MAGIC_V2.toByteArray(Charsets.US_ASCII),
                version = prepared.version,
                headerIv = prepared.headerIv,
                encryptedHeader = prepared.encryptedHeader,
                contentIv = prepared.contentIv
            )
            written = writeEncryptedPayload(
                dos = dos,
                input = payloadProvider(),
                contentCipher = contentCipher,
                poly1305 = poly1305,
                bufferSize = bufferSize,
                onPayloadBytes = onPayloadBytes
            )
            val macTag = ByteArray(VaultContainer.MAC_TAG_LENGTH)
            poly1305.doFinal(macTag, 0)
            dos.write(macTag)
        }
        return written
    }

    /**
     * Var olan bir konteyneri **yeni bir header** ile yeniden yazar (yeniden adlandirma).
     *
     * Payload yeniden sifrelenmez, aynen kopyalanir. Dosya adi degistiginde header
     * boyutu degisebilecegi icin payload ofseti kayar; bu yuzden hedef dosya
     * bastan yazilir (atomiklik icin gecici dosya -> move).
     *
     * v5'te MAC header'i da kapsadigi icin yeniden hesaplanir; diger surumlerde
     * payload ve MAC aynen kopyalanir.
     *
     * @param source mevcut konteyner
     * @param target yeni konteyner (yazilacak)
     * @param sourcePayloadOffset mevcut konteynerde payload'in baslangic ofseti
     * @param sourcePayloadLength mevcut konteynerde payload'in bayt sayisi
     * @param sourceContentIv mevcut konteynerin content IV'si (yeniden kullanilir)
     * @param newHeaderPlainBytes yeni duz header
     * @param fileKey master key
     * @param version konteyner surumu
     * @param macCoversHeader v5 tarzi MAC (header'i da kapsar)
     * @param magic konteyner imzasi (legacy v1 icin MAGIC_V1 olabilir)
     * @param useDerivedHeaderKey false ise legacy (v1) dosyalar gibi header anahtari
     *        dogrudan master key olarak kullanilir
     * @return yeni payload ofseti
     */
    fun rewriteContainer(
        source: File,
        target: File,
        sourcePayloadOffset: Long,
        sourcePayloadLength: Long,
        sourceContentIv: ByteArray,
        newHeaderPlainBytes: ByteArray,
        fileKey: ByteArray,
        version: Byte,
        macCoversHeader: Boolean,
        magic: ByteArray = VaultContainer.MAGIC_V2.toByteArray(Charsets.US_ASCII),
        useDerivedHeaderKey: Boolean = true,
        syncFile: Boolean = true
    ): Long {
        val headerKey = if (useDerivedHeaderKey) {
            VaultKeyDerivation.deriveSubkey(fileKey, VaultKeyDerivation.DOMAIN_HEADER_KEY)
        } else {
            fileKey.copyOf()
        }
        val (headerIv, encryptedHeader) = encryptHeader(newHeaderPlainBytes, headerKey)
        headerKey.fill(0)

        val poly1305 = if (macCoversHeader) {
            initHeaderMac(fileKey, sourceContentIv, headerIv, encryptedHeader)
        } else {
            null
        }

        writeToTarget(target, syncFile = syncFile) { dos ->
            writeHeaderBytes(dos, magic, version, headerIv, encryptedHeader, sourceContentIv)
            copyPayload(
                source = source,
                sourcePayloadOffset = sourcePayloadOffset,
                sourcePayloadLength = sourcePayloadLength,
                dos = dos,
                poly1305 = poly1305
            )
        }

        return VaultContainer.payloadOffset(encryptedHeader.size)
    }

    /**
     * Header'i dosya icinde yerinde yeniden yazar.
     *
     * Yalnizca header alanlarinin **boyutu degismedigi** durumlarda guvenlidir
     * (v3 header'inda tum alanlar sabit boyutludur), aksi halde payload ofseti
     * kayar ve dosya bozulur.
     */
    fun rewriteHeaderInPlace(file: File, headerPlainBytes: ByteArray, fileKey: ByteArray) {
        val headerKey = VaultKeyDerivation.deriveSubkey(fileKey, VaultKeyDerivation.DOMAIN_HEADER_KEY)
        val (headerIv, encryptedHeader) = encryptHeader(headerPlainBytes, headerKey)
        headerKey.fill(0)

        val headerIvOffset = VaultContainer.MAGIC_V2.length + 1
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(headerIvOffset.toLong())
            raf.write(headerIv)
            raf.writeInt(encryptedHeader.size)
            raf.write(encryptedHeader)
            raf.fd.sync()
        }
    }

    private fun encryptHeader(headerPlainBytes: ByteArray, headerKey: ByteArray): Pair<ByteArray, ByteArray> {
        val headerIv = ByteArray(VaultContainer.GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val headerCipher = Cipher.getInstance("AES/GCM/NoPadding")
        headerCipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(headerKey, "AES"),
            GCMParameterSpec(VaultContainer.GCM_TAG_BITS, headerIv)
        )
        return headerIv to headerCipher.doFinal(headerPlainBytes)
    }

    private inline fun writeToTarget(target: File, syncFile: Boolean, block: (DataOutputStream) -> Unit) {
        FileOutputStream(target).use { fos ->
            DataOutputStream(BufferedOutputStream(fos, STREAM_BUFFER_SIZE)).use { dos ->
                block(dos)
                dos.flush()
                if (syncFile) {
                    fos.fd.sync()
                }
            }
        }
    }

    private fun writeHeaderBytes(
        dos: DataOutputStream,
        magic: ByteArray,
        version: Byte,
        headerIv: ByteArray,
        encryptedHeader: ByteArray,
        contentIv: ByteArray
    ) {
        dos.write(magic)
        dos.writeByte(version.toInt())
        dos.write(headerIv)
        dos.writeInt(encryptedHeader.size)
        dos.write(encryptedHeader)
        dos.write(contentIv)
    }

    private fun writeEncryptedPayload(
        dos: DataOutputStream,
        input: InputStream,
        contentCipher: Cipher,
        poly1305: Poly1305,
        bufferSize: Int,
        onPayloadBytes: ((Long) -> Unit)?
    ): Long {
        var written = 0L
        val buffer = ByteArray(bufferSize)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            val out = contentCipher.update(buffer, 0, read)
            if (out != null && out.isNotEmpty()) {
                dos.write(out)
                poly1305.update(out, 0, out.size)
            }
            written += read
            onPayloadBytes?.invoke(written)
        }
        val finalBytes = contentCipher.doFinal()
        if (finalBytes != null && finalBytes.isNotEmpty()) {
            dos.write(finalBytes)
            poly1305.update(finalBytes, 0, finalBytes.size)
        }
        return written
    }

    private fun initHeaderMac(
        fileKey: ByteArray,
        contentIv: ByteArray,
        headerIv: ByteArray,
        encryptedHeader: ByteArray
    ): Poly1305 {
        val macKey = VaultKeyDerivation.deriveSubkey(fileKey, VaultKeyDerivation.DOMAIN_CONTENT_MAC_KEY)
        return Poly1305(AESEngine.newInstance()).apply {
            init(ParametersWithIV(KeyParameter(macKey), contentIv))
            macKey.fill(0)
            update(headerIv, 0, headerIv.size)
            update(encryptedHeader, 0, encryptedHeader.size)
        }
    }

    private fun copyPayload(
        source: File,
        sourcePayloadOffset: Long,
        sourcePayloadLength: Long,
        dos: DataOutputStream,
        poly1305: Poly1305?
    ) {
        RandomAccessFile(source, "r").use { raf ->
            raf.seek(sourcePayloadOffset)
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            var remaining = if (poly1305 != null) sourcePayloadLength else source.length() - sourcePayloadOffset
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = raf.read(buffer, 0, toRead)
                if (read == -1 && poly1305 != null) throw EOFException("Unexpected EOF copying encrypted payload")
                if (read == -1) break
                dos.write(buffer, 0, read)
                poly1305?.update(buffer, 0, read)
                remaining -= read
            }
            if (poly1305 != null) {
                val newTag = ByteArray(VaultContainer.MAC_TAG_LENGTH)
                poly1305.doFinal(newTag, 0)
                dos.write(newTag)
            }
        }
    }
}

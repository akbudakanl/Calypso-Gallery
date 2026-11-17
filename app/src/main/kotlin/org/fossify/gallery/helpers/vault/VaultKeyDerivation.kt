package org.fossify.gallery.helpers.vault

import com.google.crypto.tink.subtle.Hkdf
import java.security.GeneralSecurityException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Anahtar ayrimi (key separation) sozlesmesi.
 *
 * Tek bir master key'ten, HMAC-SHA256 tabanli alan ayrimi ile birbirinden
 * bagimsiz alt anahtarlar turetilir:
 *
 * ```
 * masterKey
 *   |-- HMAC-SHA256(masterKey, DOMAIN_HEADER_KEY)     -> header (AES-256-GCM) anahtari
 *   |-- HMAC-SHA256(masterKey, DOMAIN_CONTENT_ENC_KEY) -> payload (AES-256-CTR) anahtari
 *   `-- HMAC-SHA256(masterKey, DOMAIN_CONTENT_MAC_KEY) -> payload (Poly1305-AES) anahtari
 * ```
 *
 * Alan adlari degistirilirse tum vault'lar okunamaz hale gelir; bu yuzden
 * sabit ve geriye donuk uyumlu tutulmalidir.
 */
object VaultKeyDerivation {

    /** Header (AES-256-GCM) anahtari alani. */
    const val DOMAIN_HEADER_KEY = "FossifyGallery:Header:AES256GCM"

    /** Payload (AES-256-CTR) anahtari alani. */
    const val DOMAIN_CONTENT_ENC_KEY = "FossifyGallery:Payload:AES256CTR"

    /** Payload (Poly1305-AES) anahtari alani. */
    const val DOMAIN_CONTENT_MAC_KEY = "FossifyGallery:Payload:Poly1305"

    /** v4: dosya basligi (AES-256-GCM) anahtari alani. */
    const val DOMAIN_HEADER_KEY_V4 = "FossifyGallery:Header:AES256GCM:v4"

    /** v4: metadata kaydi (AES-256-GCM) anahtari alani. */
    const val DOMAIN_METADATA_KEY_V4 = "FossifyGallery:Metadata:AESGCM:v4"

    /**
     * Master key'den alan ayrimli bir alt anahtar turetir.
     *
     * HMAC-SHA256 PRF olarak kullanilir; cikti dogrudan AES-256 anahtari
     * olarak kullanilir (onceki 16 bayt: r, sonraki 16 bayt: Poly1305 icin
     * AES-128 anahtari).
     */
    fun deriveSubkey(key: ByteArray, domain: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(domain.toByteArray(Charsets.UTF_8))
    }

    /**
     * v4 anahtar turetme: **RFC 5869 HKDF-SHA256** (Tink'in standart Hkdf sinifi).
     *
     * salt bos birakilir (RFC 5869 uyarinca hash boyutunda sifir dizisi kullanilir),
     * info = alan adi (+ dosya/context ayirici), cikti = 32 bayt.
     *
     * Dikkat: [deriveSubkey] (HMAC-SHA256 PRF) yalnizca **v3 geriye donuk okuma** icin
     * korunmustur; yeni kod asla kullanmamalidir. Kendi KDF'imizi yazmiyoruz.
     */
    fun deriveSubkeyV4(key: ByteArray, info: String): ByteArray = try {
        Hkdf.computeHkdf(
            "HmacSha256",
            key,
            ByteArray(0),
            info.toByteArray(Charsets.UTF_8),
            32
        )
    } catch (e: GeneralSecurityException) {
        throw IllegalStateException("HKDF-SHA256 (RFC 5869) unavailable", e)
    }

    /**
     * v4: dosya basligi icin dosyaya ozel anahtar.
     * fileId acik oldugu icin okuyucu basligi cozmeden once bu anahtari turetebilir.
     */
    fun deriveFileHeaderKey(kek: ByteArray, fileId: ByteArray): ByteArray =
        deriveSubkeyV4(kek, DOMAIN_HEADER_KEY_V4 + "|" + fileId.joinToString("") { "%02x".format(it.toInt() and 0xff) })

    /** v4: metadata kaydi icin kayda ozel anahtar (context ayni zamanda GCM AAD'dir). */
    fun deriveMetadataKey(kek: ByteArray, context: String): ByteArray =
        deriveSubkeyV4(kek, DOMAIN_METADATA_KEY_V4 + "|" + context)
}

package org.fossify.gallery.helpers.vault

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
}

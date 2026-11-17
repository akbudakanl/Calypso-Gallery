package org.fossify.gallery.helpers.vault

import com.google.crypto.tink.subtle.Hkdf
import java.security.GeneralSecurityException

/**
 * Anahtar ayrımı (key separation) sözleşmesi.
 *
 * KEK'ten RFC 5869 HKDF-SHA256 ile, amaç ve dosya/ kayıt kimliğine bağlı
 * bağımsız alt anahtarlar türetilir. Kendi KDF'imizi yazmıyoruz; Tink'in
 * standart `Hkdf` sınıfını kullanıyoruz.
 */
object VaultKeyDerivation {

    /** Dosya başlığı (AES-256-GCM) anahtar alanı. */
    private const val DOMAIN_HEADER_KEY = "CalypsoGallery:Header:AES256GCM"

    /** Metadata kaydı (AES-256-GCM) anahtar alanı. */
    private const val DOMAIN_METADATA_KEY = "CalypsoGallery:Metadata:AESGCM"

    private fun deriveSubkey(key: ByteArray, info: String): ByteArray = try {
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
     * Dosya başlığı için dosyaya özel anahtar.
     * fileId açık olduğu için okuyucu başlığı çözmeden önce bu anahtarı türetebilir.
     */
    fun deriveFileHeaderKey(kek: ByteArray, fileId: ByteArray): ByteArray =
        deriveSubkey(kek, DOMAIN_HEADER_KEY + "|" + fileId.joinToString("") { "%02x".format(it.toInt() and 0xff) })

    /** Metadata kaydı için kayda özel anahtar (context aynı zamanda GCM AAD'dir). */
    fun deriveMetadataKey(kek: ByteArray, context: String): ByteArray =
        deriveSubkey(kek, DOMAIN_METADATA_KEY + "|" + context)
}

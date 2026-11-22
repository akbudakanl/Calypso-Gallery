package org.fossify.gallery.helpers.vault

/**
 * Şifreli medya konteynerinin (`.bin`) format sözleşmesi.
 *
 * ```
 * offset  boyut  içerik
 * 0       8      magic "FGVLTENC"
 * 8       1      format versiyonu (1)
 * 9       2      keyId
 * 11      16     fileId
 * 27      12     header IV (AES-256-GCM)
 * 39      4      şifreli header boyutu (big-endian int)
 * 43      N      şifreli header (GCM ciphertext + 16 bayt GCM tag)
 * 43+N    T      Tink AES256_GCM_HKDF şifreli payload
 * ```
 *
 * Header, dosya adı / boyut / tarih / medya tipi / thumbnail bilgisini içerir ve
 * KEK'ten `fileId` ile türetilen anahtarla AES-256-GCM korunur. Header AAD'si
 * magic | versiyon | keyId | fileId | N. Payload, dosyaya özel rastgele DEK ile
 * Tink AES256_GCM_HKDF akışına şifrelenir; AAD = `fileId`.
 */
object VaultContainer {

    const val VAULT_MAGIC = "FGVLTENC"
    const val VAULT_FORMAT_VERSION: Byte = 1

    const val GCM_IV_LENGTH = 12
    const val GCM_TAG_BITS = 128
    const val KEY_ID_LENGTH = 2
    const val FILE_ID_LENGTH = 16

    /** Klasör metadata (`.meta`) dosyalarının imzası. */
    const val FOLDER_META_MAGIC = "FGMETA"

    /** `.vault` başlık dosyasının imzası. */
    const val VAULT_HEADER_MAGIC = "FGVLT"

    /** `.vault` içinde saklanan, amaç bazlı HKDF anahtarıyla korunan kayıt. */
    const val VAULT_STR_PREFIX = "FVSTR:"

    /** header IV'sinin dosyadaki başlangıç ofseti: magic(8) + versiyon(1) + keyId(2) + fileId(16). */
    const val HEADER_IV_OFFSET = 8 + 1 + KEY_ID_LENGTH + FILE_ID_LENGTH

    /** Sabit önek: magic(8) + versiyon(1) + keyId(2) + fileId(16) + headerIv(12) + headerLen(4). */
    const val FIXED_PREFIX_LENGTH = HEADER_IV_OFFSET + GCM_IV_LENGTH + 4

    /** Şifreli payload'ın dosya içindeki başlangıç ofseti. */
    fun payloadOffset(encryptedHeaderLength: Int): Long =
        (FIXED_PREFIX_LENGTH + encryptedHeaderLength).toLong()

    /** Header'da depolanan algoritma parametreleri kimliği. */
    const val ALGORITHM_PARAMS: Short = 0x0001
}

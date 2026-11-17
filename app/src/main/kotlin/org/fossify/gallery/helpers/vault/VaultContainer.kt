package org.fossify.gallery.helpers.vault

/**
 * Sifreli medya konteynerinin (`.bin`) format sozlesmesi.
 *
 * Bu dosya, formatla ilgili tum sabitleri ve hesaplari tek yerde toplar.
 * Format degistirilmek istendiginde (yeni surum) yalnizca bu dosya ve
 * [VaultContainerWriter] guncellenir.
 *
 * ## v3 konteyner duzeni (VERSION_SYMMETRIC_AUTH)
 *
 * ```
 * offset  boyut  icerik
 * 0       8      magic "FGVLTENC"
 * 8       1      surum (3)
 * 9       12     header IV (AES-256-GCM)
 * 21      4      sifreli header boyutu (big-endian int)
 * 25      N     sifreli header (GCM ciphertext + 16 bayt GCM tag)
 * 25+N    16     content IV (AES-256-CTR baslangic sayaci)
 * 41+N    M     sifreli payload (uzunluk = ozgun dosya boyutu)
 * 41+N+M  16     Poly1305-AES MAC etiketi
 * ```
 *
 * Header, dosya adi / boyut / tarih / medya tipi / thumbnail bilgisini icerir ve
 * AES-256-GCM ile ayrica korunur. Payload, AES-256-CTR ile sifrelenir ve
 * uzerine Poly1305-AES MAC hesaplanir (encrypt-then-MAC).
 *
 * Bu tasarim restic'in kullandigi ve yayimlanmis test vektorleriyle
 * dogrulanmis Poly1305-AES128 yapisiyla birebir aynidir.
 */
object VaultContainer {

    // ---------------- Imzalar ----------------

    const val MAGIC_V1 = "FVLTENC1"
    const val MAGIC_V2 = "FGVLTENC"

    // ---------------- Surumler ----------------

    /** Ilk surum: sadece GCM ile korunan header, payload MAC'siz. */
    const val VERSION_SYMMETRIC: Byte = 1

    /** v3: AES-256-CTR payload + Poly1305-AES MAC. Uretilen guncel format. */
    const val VERSION_SYMMETRIC_AUTH: Byte = 3

    /** v4: Tink AES256_GCM_HKDF streaming AEAD payload + per-file DEK. */
    const val VERSION_TINK_STREAM: Byte = 4

    /**
     * v5: legacy. MAC, payload'in yaninda header'i da kapsar.
     * Bu surum **yalnizca okunur** - guncel kod v5 uretmez.
     */
    const val VERSION_V5_AUTH: Byte = 5

    // ---------------- Uzunluklar ----------------

    const val GCM_IV_LENGTH = 12
    const val GCM_TAG_BITS = 128
    const val CTR_IV_LENGTH = 16
    const val MAC_TAG_LENGTH = 16

    /** v4: cleartext keyId (u16) uzunlugu. */
    const val KEY_ID_LENGTH = 2

    /** v4: immutable random fileId uzunlugu. */
    const val FILE_ID_LENGTH = 16

    // ---------------- Yardimci imzalar ----------------

    /** Klasor metadata (.meta) dosyalari. */
    const val META_MAGIC_SYM = "FVLTMETA"
    const val META_MAGIC_V2 = "FGMETA2"

    /** .vault baslik dosyasi. */
    const val VAULT_HEADER_MAGIC = "FGVLT2"

    /** .vault icinde saklanan anahtar sarmalama kaydi (v3, legacy). */
    const val VAULT_STR_PREFIX = "FVSTR1:"

    /** v4 metadata kaydi: kayda ozel HKDF anahtari + AAD. */
    const val VAULT_STR_PREFIX_V2 = "FVSTR2:"

    // ---------------- Hesaplar ----------------

    /** v3 duzeninde sabit olan onek boyutu (magic + surum + header IV + header boyutu). */
    const val FIXED_PREFIX_LENGTH = 1 + 8 + 4

    /**
     * Sifreli payload'in dosya icindeki baslangic ofseti.
     *
     * @param encryptedHeaderLength sifreli header'in bayt sayisi
     */
    fun payloadOffset(encryptedHeaderLength: Int): Long =
        (FIXED_PREFIX_LENGTH + GCM_IV_LENGTH + encryptedHeaderLength + CTR_IV_LENGTH).toLong()

    /**
     * v4 duzeninde sabit olan onek boyutu:
     * magic(8) + version(1) + keyId(2) + fileId(16) + headerIv(12) + headerLen(4) = 43.
     */
    const val FIXED_PREFIX_LENGTH_V4 = 8 + 1 + KEY_ID_LENGTH + FILE_ID_LENGTH + GCM_IV_LENGTH + 4

    /** v4 konteynerinde Tink sifreli akisinin baslangic ofseti. */
    fun payloadOffsetV4(encryptedHeaderLength: Int): Long =
        (FIXED_PREFIX_LENGTH_V4 + encryptedHeaderLength).toLong()

    /** Bir surumun desteklenip desteklenmedigini dondurur. */
    fun isKnownVersion(version: Byte): Boolean = when (version) {
        VERSION_SYMMETRIC, VERSION_SYMMETRIC_AUTH, VERSION_V5_AUTH, VERSION_TINK_STREAM -> true
        else -> false
    }

    /** Bir surumun butunluk algoritmasini dondurur. */
    fun macAlgorithmFor(version: Byte): MacAlgorithm = when (version) {
        VERSION_SYMMETRIC_AUTH, VERSION_V5_AUTH -> MacAlgorithm.POLY1305_AES
        VERSION_TINK_STREAM -> MacAlgorithm.AEAD_GCM_STREAM
        else -> MacAlgorithm.NONE
    }

    /** v4 basliginda depolanan algorithmParamsId degeri. */
    const val ALGORITHM_PARAMS_V4: Short = 0x0001

    /**
     * Content IV sifir mi diye kontrol eder.
     *
     * IV dosyanin icinde acik tutulur. Tamamen sifir bir IV, Poly1305-AES
     * icin tekrar eden bir one-time key anlamina gelir ve forgery'ye yol
     * acabilir. Rastgele uretilen IV'ler zaten sifir olamaz; bu bir savunma
     * katmanidir.
     */
    fun isValidContentIv(iv: ByteArray): Boolean = iv.any { it.toInt() != 0 }
}

/** Konteyner surumunun kullandigi bütünlük algoritmasi. */
enum class MacAlgorithm {
    /** MAC yok (v1: yalnizca GCM ile korunan header). */
    NONE,

    /** Poly1305-AES128 - BouncyCastle `Poly1305(AESEngine)` ile birebir uyumlu. */
    POLY1305_AES,

    /** v4: Tink AES-GCM-HKDF streaming AEAD (segment basina GCM tag). */
    AEAD_GCM_STREAM
}

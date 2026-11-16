package org.fossify.gallery

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Poly1305-AES (Bernstein 2005) yayimlanmis test vektorleri.
 *
 * Kaynak: restic `internal/repository/crypto/crypto_int_test.go`
 *         (asil kaynak: http://cr.yp.to/mac/poly1305-20050329.pdf)
 *
 * BouncyCastle `Poly1305(AESEngine)` anahtar duzeni:
 *   key[0:16]  -> r (Poly1305 polinom katsayisi, kutup icinde clamp edilir)
 *   key[16:32] -> K (AES-128 anahtari; s = AES-128_K(nonce))
 *
 * restic/BC ile ayni sonuc uretmek icin vektorlerde `r || k` sirasiyla birlesir.
 */
class Poly1305AesVectorsTest {

    private val vectors = listOf(
        arrayOf(
            "f3f6",
            "851fc40c3467ac0be05cc20404f3f700",
            "ec074c835580741701425b623235add6",
            "fb447350c4e868c52ac3275cf9d4327e",
            "f4c633c3044fc145f84f335cb81953de"
        ),
        arrayOf(
            "",
            "a0f3080000f46400d0c7e9076c834403",
            "75deaa25c09f208e1dc4ce6b5cad3fbf",
            "61ee09218d29b0aaed7e154a2c5509cc",
            "dd3fab2251f11ac759f0887129cc2ee7"
        ),
        arrayOf(
            "663cea190ffb83d89593f3f476b6bc24d7e679107ea26adb8caf6652d0656136",
            "48443d0bb0d21109c89a100b5ce2c208",
            "6acb5f61a7176dd320c5c1eb2edcdc74",
            "ae212a55399729595dea458bc621ff0e",
            "0ee1c16bb73f0f4fd19881753c01cdbe"
        ),
        arrayOf(
            "ab0812724a7f1e342742cbed374d94d136c6b8795d45b3819830f2c04491faf0990" +
                "c62e48b8018b2c3e4a0fa3134cb67fa83e158c994d961c4cb21095c1bf9",
            "12976a08c4426d0ce8a82407c4f48207",
            "e1a5668a4d5b66a5f68cc5424ed5982d",
            "9ae831e743978d3a23527c7128149e3a",
            "5154ad0d2cb26e01274fc51148491f1b"
        )
    )

    @Test
    fun matchesPublishedPoly1305AesVectors() {
        vectors.forEach { v ->
            val message = v[0].hexToBytes()
            val r = v[1].hexToBytes()
            val k = v[2].hexToBytes()
            val nonce = v[3].hexToBytes()
            val expected = v[4].hexToBytes()

            // Vektor butunlugu: r ve k her biri 16 bayt, birlesik anahtar 32 bayt.
            assertEquals("r 16 bayt olmali", 16, r.size)
            assertEquals("k 16 bayt olmali", 16, k.size)
            assertEquals("nonce 16 bayt olmali", 16, nonce.size)

            val mac = Poly1305(AESEngine.newInstance())
            mac.init(ParametersWithIV(KeyParameter(r + k), nonce))
            mac.update(message, 0, message.size)
            val actual = ByteArray(16)
            mac.doFinal(actual, 0)

            assertArrayEquals("Poly1305-AES vektoru eslesmedi (nonce=$nonce)", expected, actual)
        }
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

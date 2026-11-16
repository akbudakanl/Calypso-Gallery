#!/usr/bin/env python3
"""
Poly1305-AES (Bernstein 2005) yayimlanmis test vektorlari.

Kaynak: restic `internal/repository/crypto/crypto_int_test.go`
        (asil kaynak: http://cr.yp.to/mac/poly1305-20050329.pdf)

Bu test, CLI'nin MAC hesabini *bagimsiz* bir uygulamadan (pycryptodome)
dogrular. Boylece elle yazilmis bir MAC implementasyonunun hatali olmasi
testleri yaniltmaz.
"""

import unittest

from Crypto.Cipher import AES
from Crypto.Hash import Poly1305

# (mesaj, r, K, nonce, beklenen mac) - hepsi hex
VECTORS = [
    (
        "f3f6",
        "851fc40c3467ac0be05cc20404f3f700",
        "ec074c835580741701425b623235add6",
        "fb447350c4e868c52ac3275cf9d4327e",
        "f4c633c3044fc145f84f335cb81953de",
    ),
    (
        "",
        "a0f3080000f46400d0c7e9076c834403",
        "75deaa25c09f208e1dc4ce6b5cad3fbf",
        "61ee09218d29b0aaed7e154a2c5509cc",
        "dd3fab2251f11ac759f0887129cc2ee7",
    ),
    (
        "663cea190ffb83d89593f3f476b6bc24d7e679107ea26adb8caf6652d0656136",
        "48443d0bb0d21109c89a100b5ce2c208",
        "6acb5f61a7176dd320c5c1eb2edcdc74",
        "ae212a55399729595dea458bc621ff0e",
        "0ee1c16bb73f0f4fd19881753c01cdbe",
    ),
    (
        "ab0812724a7f1e342742cbed374d94d136c6b8795d45b3819830f2c04491faf0990"
        "c62e48b8018b2c3e4a0fa3134cb67fa83e158c994d961c4cb21095c1bf9",
        "12976a08c4426d0ce8a82407c4f48207",
        "e1a5668a4d5b66a5f68cc5424ed5982d",
        "9ae831e743978d3a23527c7128149e3a",
        "5154ad0d2cb26e01274fc51148491f1b",
    ),
]


def poly1305_aes_tag(mac_key: bytes, nonce: bytes, data: bytes) -> bytes:
    """
    Poly1305-AES MAC - BouncyCastle `Poly1305(AESEngine)` ile birebir uyumlu.

    mac_key duzeni (Android/BC ve restic ile ayni):
        mac_key[0:16]  -> r
        mac_key[16:32] -> K (AES-128 anahtari)

    pycryptodome duzeni: key[:16] = K, key[16:32] = r -> yarimlar swap edilir.
    """
    r_part = mac_key[:16]
    k_part = mac_key[16:32]
    mac = Poly1305.new(key=k_part + r_part, cipher=AES, nonce=nonce)
    mac.update(data)
    return mac.digest()


class Poly1305AesVectorTest(unittest.TestCase):
    def test_published_vectors(self):
        for msg_hex, r_hex, k_hex, nonce_hex, mac_hex in VECTORS:
            with self.subTest(nonce=nonce_hex):
                r = bytes.fromhex(r_hex)
                k = bytes.fromhex(k_hex)
                self.assertEqual(len(r), 16)
                self.assertEqual(len(k), 16)

                tag = poly1305_aes_tag(
                    r + k, bytes.fromhex(nonce_hex), bytes.fromhex(msg_hex)
                )
                self.assertEqual(tag.hex(), mac_hex)

    def test_streaming_matches_one_shot(self):
        """Akis (streaming) MAC, tek seferlik hesapla ile ayni sonucu vermeli."""
        msg_hex, r_hex, k_hex, nonce_hex, mac_hex = VECTORS[2]
        r = bytes.fromhex(r_hex)
        k = bytes.fromhex(k_hex)
        nonce = bytes.fromhex(nonce_hex)
        data = bytes.fromhex(msg_hex)
        mac_key = r + k

        mac = Poly1305.new(key=k + r, cipher=AES, nonce=nonce)
        for i in range(0, len(data), 7):
            mac.update(data[i:i + 7])
        self.assertEqual(mac.digest().hex(), mac_hex)
        self.assertEqual(
            poly1305_aes_tag(mac_key, nonce, data).hex(), mac_hex
        )

    def test_wrong_mac_is_detected(self):
        """Tek bir bayt degisikligi MAC'i bozmali."""
        msg_hex, r_hex, k_hex, nonce_hex, mac_hex = VECTORS[0]
        mac_key = bytes.fromhex(r_hex + k_hex)
        nonce = bytes.fromhex(nonce_hex)
        data = bytes.fromhex(msg_hex)

        tampered = bytearray(data)
        tampered[0] ^= 0x01
        self.assertNotEqual(
            poly1305_aes_tag(mac_key, nonce, bytes(tampered)).hex(), mac_hex
        )


if __name__ == "__main__":
    unittest.main()

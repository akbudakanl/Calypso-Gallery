#!/usr/bin/env python3
"""
Tests for the canonical Tink AES256_GCM_HKDF stream and FGVLTENC v1 container
format. The tool uses the REAL Tink Python library (keyset built from the raw
DEK with RAW output prefix); these tests verify byte layout, the exact
expected-ciphertext-size formula, tamper detection and the container header.

The decryption helpers below are test-only and intentionally kept out of
cipher_vault.py (which is encrypt-only).
"""

import io
import os
import struct
import tempfile
import unittest
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.exceptions import InvalidTag

import tink

import cipher_vault as cv


def reference_expected_ciphertext_size(plaintext_size: int) -> int:
    """Independent restatement of Tink's expectedCiphertextSize formula."""
    offset = 40            # stream header length
    plain_seg = 262128     # 262144 - 16
    full = (plaintext_size + offset) // plain_seg
    last = (plaintext_size + offset) % plain_seg
    return full * 262144 + (last + 16 if last > 0 else 0)


def decrypt_tink_stream(stream: bytes, dek: bytes, file_id: bytes) -> bytes:
    """Decrypt a canonical Tink stream with the real Tink library."""
    primitive = cv._tink_stream_primitive(dek)
    source = io.BytesIO(stream)
    out = io.BytesIO()
    with primitive.new_decrypting_stream(source, file_id) as dec:
        while True:
            chunk = dec.read(64 * 1024)
            if not chunk:
                break
            out.write(chunk)
    return out.getvalue()


def encrypt_tink_stream(plain: bytes, dek: bytes, file_id: bytes) -> bytes:
    """Encrypt with the real Tink library (frozen canonical parameters)."""
    primitive = cv._tink_stream_primitive(dek)
    out = cv._KeepOpenBytesIO()
    with primitive.new_encrypting_stream(out, file_id) as enc:
        enc.write(plain)
    return out.getvalue()


def parse_container(data: bytes, kek: bytes, expected_key_id: int = 1) -> dict:
    """Test-only parser/decryptor for a canonical FGVLTENC v1 container."""
    pos = 0

    def take(n: int) -> bytes:
        nonlocal pos
        if pos + n > len(data):
            raise ValueError("truncated container")
        chunk = data[pos:pos + n]
        pos += n
        return chunk

    if take(8) != cv.CONTAINER_MAGIC:
        raise ValueError("invalid container magic")
    version = take(1)[0]
    if version != cv.CONTAINER_VERSION:
        raise ValueError("invalid container version")
    key_id = struct.unpack(">H", take(2))[0]
    file_id = take(16)
    header_iv = take(12)
    n = struct.unpack(">I", take(4))[0]
    enc_header = take(n)
    stream = data[pos:]

    header_key = cv.derive_header_key(kek, file_id)
    header_aad = (
        cv.CONTAINER_MAGIC
        + bytes([version])
        + struct.pack(">H", key_id)
        + file_id
        + struct.pack(">I", n)
    )
    header_plain = AESGCM(header_key).decrypt(header_iv, enc_header, header_aad)

    hp = 0
    algorithm_params = struct.unpack(">H", header_plain[hp:hp + 2])[0]
    hp += 2
    segment_size = struct.unpack(">I", header_plain[hp:hp + 4])[0]
    hp += 4
    ciphertext_length = struct.unpack(">Q", header_plain[hp:hp + 8])[0]
    hp += 8
    dek = header_plain[hp:hp + 32]
    hp += 32
    name_len = struct.unpack(">H", header_plain[hp:hp + 2])[0]
    hp += 2
    orig_name = header_plain[hp:hp + name_len].decode("utf-8")
    hp += name_len
    last_mod, date_taken, orig_size, media_type, video_duration, thumb_len = struct.unpack(
        ">qqqiii", header_plain[hp:hp + 36]
    )
    hp += 36
    thumb_bytes = header_plain[hp:hp + thumb_len]

    assert algorithm_params == 1
    assert segment_size == 262144
    assert key_id == expected_key_id
    assert ciphertext_length == len(stream)
    assert ciphertext_length == reference_expected_ciphertext_size(orig_size)

    payload = decrypt_tink_stream(stream, dek, file_id)
    assert len(payload) == orig_size

    return {
        "orig_name": orig_name,
        "last_mod": last_mod,
        "date_taken": date_taken,
        "orig_size": orig_size,
        "media_type": media_type,
        "video_duration": video_duration,
        "thumb_bytes": thumb_bytes,
        "file_id": file_id,
        "dek": dek,
        "ciphertext_length": ciphertext_length,
        "payload": payload,
    }


class TinkStreamFormatTest(unittest.TestCase):
    def test_real_tink_is_used(self):
        self.assertTrue(cv.HAS_TINK, "the real Tink Python library must be available")

    def test_expected_ciphertext_size_values(self):
        cases = [
            (0, 56), (1, 57), (15, 71), (65535, 40 + 65535 + 16),
            (262087, 40 + 262087 + 16), (262088, 262144),
            (262089, 262144 + 1 + 16), (262128, 262144 + 40 + 16),
            (524216, 2 * 262144),
            (1_100_000, reference_expected_ciphertext_size(1_100_000)),
        ]
        for size, expected in cases:
            with self.subTest(size=size):
                self.assertEqual(cv.expected_ciphertext_size(size), expected)
                self.assertEqual(cv.expected_ciphertext_size(size), reference_expected_ciphertext_size(size))

    def test_stream_roundtrip_and_layout(self):
        for size in (0, 1, 16, 65535, 262088, 262089, 524216):
            with self.subTest(size=size):
                plain = bytes((i * 31 + 7) & 0xFF for i in range(size))
                dek = os.urandom(32)
                file_id = os.urandom(16)
                stream = encrypt_tink_stream(plain, dek, file_id)
                self.assertEqual(len(stream), cv.expected_ciphertext_size(size))
                self.assertEqual(stream[0], 40)
                self.assertEqual(decrypt_tink_stream(stream, dek, file_id), plain)

    def test_tampered_stream_is_rejected(self):
        dek = os.urandom(32)
        file_id = os.urandom(16)
        stream = bytearray(encrypt_tink_stream(b"authenticated payload", dek, file_id))
        stream[45] ^= 0x01
        with self.assertRaises(tink.TinkError):
            decrypt_tink_stream(bytes(stream), dek, file_id)

    def test_header_aad_binds_cleartext_prefix(self):
        kek = os.urandom(32)
        with tempfile.TemporaryDirectory() as td:
            source = Path(td) / "photo.jpg"
            source.write_bytes(b"jpeg-bytes")
            out = cv.encrypt_file(source, Path(td), kek)
            data = bytearray(out.read_bytes())
            data[9] ^= 0x01
            with self.assertRaises(InvalidTag):
                parse_container(bytes(data), kek)


if __name__ == "__main__":
    unittest.main(verbosity=2)

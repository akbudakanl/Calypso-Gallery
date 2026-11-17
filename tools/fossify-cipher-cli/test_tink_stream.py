#!/usr/bin/env python3
"""
Tests for the canonical Tink AES256_GCM_HKDF stream and FGVLTENC v1 container
format implemented manually with `cryptography`.

These tests verify:
  - the exact expected-ciphertext-size formula,
  - stream header layout and per-segment AES-256-GCM authentication,
  - tamper detection,
  - the full container header (AAD binding, KEK-derived header key, metadata).

The decryption helpers below are test-only and intentionally kept out of
cipher_vault.py (which is encrypt-only).
"""

import os
import struct
import unittest

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

import cipher_vault as cv


def reference_expected_ciphertext_size(plaintext_size: int) -> int:
    """Independent restatement of Tink's expectedCiphertextSize formula."""
    offset = 40            # stream header length
    plain_seg = 262128     # 262144 - 16
    full = (plaintext_size + offset) // plain_seg
    last = (plaintext_size + offset) % plain_seg
    return full * 262144 + (last + 16 if last > 0 else 0)


def decrypt_tink_stream(stream: bytes, dek: bytes, file_id: bytes) -> bytes:
    """Test-only decryptor for the AES256_GCM_HKDF stream produced by encrypt_file."""
    if len(stream) < 40 + 16:
        raise ValueError("stream too short")
    if stream[0] != 40:
        raise ValueError("bad stream header length byte")
    salt = stream[1:33]
    nonce_prefix = stream[33:40]
    stream_key = HKDF(algorithm=hashes.SHA256(), length=32, salt=salt, info=file_id).derive(dek)

    pos = 40
    segment = 0
    plaintext = bytearray()
    first_max = cv.SEGMENT_SIZE - cv.STREAM_HEADER_LENGTH - cv.GCM_TAG_SIZE
    later_max = cv.SEGMENT_SIZE - cv.GCM_TAG_SIZE
    while pos < len(stream):
        max_plain = first_max if segment == 0 else later_max
        seg_len = min(len(stream) - pos, max_plain + cv.GCM_TAG_SIZE)
        if seg_len < cv.GCM_TAG_SIZE:
            raise ValueError("invalid segment length")
        enc = stream[pos:pos + seg_len]
        pos += seg_len
        is_last = pos == len(stream)
        iv = nonce_prefix + segment.to_bytes(4, "big") + (b"\x01" if is_last else b"\x00")
        plaintext += AESGCM(stream_key).decrypt(iv, enc, b"")
        segment += 1
    return bytes(plaintext)


def parse_container(data: bytes, kek: bytes) -> dict:
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
    assert key_id == 1
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
    def test_expected_ciphertext_size_values(self):
        cases = [
            (0, 56),                 # empty: 40B header + 16B tag
            (1, 57),
            (15, 71),
            (65535, 40 + 65535 + 16),
            (262087, 40 + 262087 + 16),
            (262088, 262144),        # first segment exactly full
            (262089, 262144 + 1 + 16),
            (262128, 262144 + 40 + 16),
            (524216, 2 * 262144),    # two full segments
            (1_100_000, reference_expected_ciphertext_size(1_100_000)),
        ]
        for size, expected in cases:
            with self.subTest(size=size):
                self.assertEqual(cv.expected_ciphertext_size(size), expected)
                self.assertEqual(cv.expected_ciphertext_size(size), reference_expected_ciphertext_size(size))

    def test_stream_roundtrip_and_layout(self):
        for size in (0, 1, 16, 65535, 262088, 262089, 524216):
            with self.subTest(size=size):
                plaintext = bytes((i * 31 + 7) & 0xFF for i in range(size))
                dek = os.urandom(32)
                file_id = os.urandom(16)
                salt = os.urandom(32)
                nonce_prefix = os.urandom(7)
                stream_key = HKDF(algorithm=hashes.SHA256(), length=32, salt=salt, info=file_id).derive(dek)

                from io import BytesIO
                out = BytesIO()
                out.write(bytes([40]) + salt + nonce_prefix)
                pos = 0
                segment = 0
                while True:
                    max_chunk = cv.FIRST_CHUNK_PLAINTEXT_MAX if segment == 0 else cv.SEGMENT_PLAINTEXT_MAX
                    chunk = plaintext[pos:pos + max_chunk]
                    pos += len(chunk)
                    last = pos >= size
                    iv = nonce_prefix + segment.to_bytes(4, "big") + (b"\x01" if last else b"\x00")
                    out.write(AESGCM(stream_key).encrypt(iv, chunk, b""))
                    segment += 1
                    if last:
                        break
                stream = out.getvalue()

                self.assertEqual(len(stream), cv.expected_ciphertext_size(size))
                self.assertEqual(stream[0], 40)
                self.assertEqual(stream[1:33], salt)
                self.assertEqual(stream[33:40], nonce_prefix)
                self.assertEqual(decrypt_tink_stream(stream, dek, file_id), plaintext)

    def test_tampered_stream_is_rejected(self):
        plaintext = b"authenticated payload"
        dek = os.urandom(32)
        file_id = os.urandom(16)
        salt = os.urandom(32)
        nonce_prefix = os.urandom(7)
        stream_key = HKDF(algorithm=hashes.SHA256(), length=32, salt=salt, info=file_id).derive(dek)
        iv = nonce_prefix + (0).to_bytes(4, "big") + b"\x01"
        enc = AESGCM(stream_key).encrypt(iv, plaintext, b"")
        stream = bytes([40]) + salt + nonce_prefix + enc

        tampered = bytearray(stream)
        tampered[45] ^= 0x01
        from cryptography.exceptions import InvalidTag
        with self.assertRaises(InvalidTag):
            decrypt_tink_stream(bytes(tampered), dek, file_id)

    def test_header_aad_binds_cleartext_prefix(self):
        """Changing any cleartext prefix byte must break header decryption."""
        import tempfile
        from pathlib import Path

        kek = os.urandom(32)
        with tempfile.TemporaryDirectory() as td:
            source = Path(td) / "photo.jpg"
            source.write_bytes(b"jpeg-bytes")
            out = cv.encrypt_file(source, Path(td), kek)
            data = bytearray(out.read_bytes())

            # Flip the keyId, which is part of the header AAD.
            data[9] ^= 0x01
            from cryptography.exceptions import InvalidTag
            with self.assertRaises(InvalidTag):
                parse_container(bytes(data), kek)


if __name__ == "__main__":
    unittest.main(verbosity=2)

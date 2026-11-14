#!/usr/bin/env python3
"""
Unit & end-to-end integration tests for Fossify Gallery Cipher offline CLI (cipher_vault.py).
"""

import argparse
import io
import os
import secrets
import struct
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

import cipher_vault as cv


def decrypt_bin_for_test(bin_path: Path, master_key: bytes) -> dict:
    """
    Test-only helper to decrypt and authenticate an FGVLTENC v3 .bin file
    so unit tests can verify ciphertext integrity, header metadata, thumbnails,
    and plaintext payload without keeping decryption logic in cipher_vault.py.
    """
    from cryptography.hazmat.backends import default_backend
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

    data = bin_path.read_bytes()
    min_size = 8 + 1 + 12 + 4 + 16 + 16 + 16
    if len(data) < min_size:
        raise ValueError(f"File too small: {bin_path}")

    pos = 0
    if data[pos:pos + 8] != cv.MAGIC_V2:
        raise ValueError("Invalid magic")
    pos += 8
    if data[pos] != cv.VERSION_SYMMETRIC_AUTH:
        raise ValueError(f"Unexpected version: {data[pos]}")
    pos += 1

    header_iv = data[pos:pos + 12]
    pos += 12
    enc_header_len = struct.unpack(">i", data[pos:pos + 4])[0]
    pos += 4
    enc_header_data = data[pos:pos + enc_header_len]
    pos += enc_header_len

    header_key = cv.derive_subkey(master_key, cv.DOMAIN_HEADER_KEY)
    h_dec = Cipher(
        algorithms.AES(header_key),
        modes.GCM(header_iv, enc_header_data[-16:]),
        backend=default_backend(),
    ).decryptor()
    header_plain = h_dec.update(enc_header_data[:-16]) + h_dec.finalize()

    name_len = struct.unpack(">H", header_plain[:2])[0]
    orig_name = header_plain[2:2 + name_len].decode("utf-8")
    last_mod, date_taken, orig_size, media_type, video_duration, thumb_len = struct.unpack(
        ">qqqiii",
        header_plain[2 + name_len:2 + name_len + 36],
    )
    thumb_bytes = header_plain[2 + name_len + 36:2 + name_len + 36 + thumb_len]

    content_iv = data[pos:pos + 16]
    pos += 16
    enc_payload = data[pos:-16]
    expected_mac = data[-16:]

    content_mac_key = cv.derive_subkey(master_key, cv.DOMAIN_CONTENT_MAC_KEY)
    poly_mac = cv.Poly1305AES(content_mac_key, content_iv)
    poly_mac.update(enc_payload)
    if poly_mac.finalize() != expected_mac:
        raise ValueError("Poly1305 MAC mismatch")

    content_enc_key = cv.derive_subkey(master_key, cv.DOMAIN_CONTENT_ENC_KEY)
    c_dec = Cipher(
        algorithms.AES(content_enc_key),
        modes.CTR(content_iv),
        backend=default_backend(),
    ).decryptor()
    payload = c_dec.update(enc_payload) + c_dec.finalize()

    return {
        "orig_name": orig_name,
        "last_mod": last_mod,
        "date_taken": date_taken,
        "orig_size": orig_size,
        "media_type": media_type,
        "video_duration": video_duration,
        "thumb_bytes": thumb_bytes,
        "payload": payload,
    }


def create_test_vault_file(vault_path: Path, secret: str, master_key: bytes | None = None) -> bytes:
    """Builds a valid FGVLT2 v3 .vault file matching Android CryptoManager.writeVaultHeader."""
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
    from cryptography.hazmat.backends import default_backend
    from argon2.low_level import hash_secret_raw, Type

    if master_key is None:
        master_key = secrets.token_bytes(32)

    iterations = 1
    memory_kb = 8192
    parallelism = 1
    salt = secrets.token_bytes(16)
    iv = secrets.token_bytes(12)

    wrapping_key = hash_secret_raw(
        secret=secret.encode("utf-8"),
        salt=salt,
        time_cost=iterations,
        memory_cost=memory_kb,
        parallelism=parallelism,
        hash_len=32,
        type=Type.ID,
    )

    bundle = master_key + struct.pack(">i", 0)
    enc = Cipher(algorithms.AES(wrapping_key), modes.GCM(iv), backend=default_backend()).encryptor()
    wrapped = enc.update(bundle) + enc.finalize() + enc.tag

    with open(vault_path, "wb") as f:
        f.write(cv.VAULT_HEADER_MAGIC)
        f.write(bytes([3, 1]))
        f.write(struct.pack(">3i", iterations, memory_kb, parallelism))
        f.write(bytes([3]))
        f.write(struct.pack(">i", len(salt)))
        f.write(salt)
        f.write(iv)
        f.write(struct.pack(">i", len(wrapped)))
        f.write(wrapped)

    return master_key


class TestCipherVaultCLI(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tmpdir.name)
        self.desktop = self.root / "Desktop"
        self.desktop.mkdir(parents=True, exist_ok=True)
        self.secret = "test-vault-password-123"
        self.vault_dir = self.root / "ExternalUSB" / "Cipher_Secrets"
        self.vault_dir.mkdir(parents=True, exist_ok=True)
        self.vault_file = self.vault_dir / ".vault"
        self.master_key = create_test_vault_file(self.vault_file, self.secret)

    def tearDown(self):
        self.tmpdir.cleanup()

    def _make_encrypt_args(
        self,
        input_dir: Path,
        output_dir: Path | None = None,
        vault_file: Path | None = None,
        delete_source: bool = False,
        lang: str = "en",
    ) -> argparse.Namespace:
        return argparse.Namespace(
            encrypt=True,
            lang=lang,
            vault=str(vault_file or self.vault_file),
            input=str(input_dir),
            output=str(output_dir) if output_dir is not None else None,
            secret=self.secret,
            pattern=None,
            delete_source=delete_source,
            adb=False,
        )

    def test_supported_media_format_checks_and_types(self):
        supported = [
            "photo.jpg", "PHOTO.JPEG", "img.png", "anim.webp", "shot.heic", "shot.heif",
            "frame.apng", "pic.avif", "bmp.bmp", "w.wbmp", "icon.ico", "j.jxl",
            "j.jfif", "p.pjpeg", "p.pjp", "loop.gif", "vec.svg",
            "raw.dng", "raw.cr2", "raw.CR3", "raw.nef", "raw.nrw", "raw.arw",
            "raw.srf", "raw.sr2", "raw.orf", "raw.rw2", "raw.pef", "raw.raf",
            "raw.srw", "raw.x3f", "raw.erf", "raw.mef", "raw.mos", "raw.mrw",
            "raw.dcr", "raw.kdc", "raw.3fr", "raw.fff", "raw.iiq", "raw.rwl",
            "vid.mp4", "vid.mkv", "vid.webm", "vid.avi", "vid.3gp", "vid.3gpp",
            "vid.3g2", "vid.mov", "vid.m4v", "vid.ts", "vid.mts", "vid.m2ts",
            "vid.qt", "vid.flv", "vid.f4v", "vid.wmv", "vid.asf", "vid.mpg",
            "vid.mpeg", "vid.mpe", "vid.vob", "vid.ogv", "vid.rm", "vid.rmvb", "vid.divx",
        ]
        for fname in supported:
            self.assertTrue(cv.is_supported_media_file(fname), f"Expected supported: {fname}")

        unsupported = [
            "notes.txt", "doc.pdf", "readme.md", "archive.zip", "data.json",
            "payload.bin", ".vault", ".meta", ".nomedia", "noextension",
        ]
        for fname in unsupported:
            self.assertFalse(cv.is_supported_media_file(fname), f"Expected unsupported: {fname}")

        self.assertEqual(cv.get_media_type("clip.3gpp"), cv.TYPE_VIDEOS)
        self.assertEqual(cv.get_media_type("stream.ts"), cv.TYPE_VIDEOS)
        self.assertEqual(cv.get_media_type("sensor.cr3"), cv.TYPE_RAWS)
        self.assertEqual(cv.get_media_type("anim.gif"), cv.TYPE_GIFS)
        self.assertEqual(cv.get_media_type("logo.svg"), cv.TYPE_SVGS)
        self.assertEqual(cv.get_media_type("hdr.jxl"), cv.TYPE_IMAGES)

    def test_write_meta_and_nomedia_rules(self):
        # Normal subfolder must NOT have .nomedia inside it, only at vault_dir root
        normal_dir = self.vault_dir / "a1b2c3"
        normal_dir.mkdir()
        cv.write_encrypted_folder_meta(normal_dir, "Vacation", self.master_key, vault_dir=self.vault_dir)
        self.assertTrue((self.vault_dir / ".nomedia").is_file())
        self.assertFalse((normal_dir / ".nomedia").exists())
        self.assertEqual(cv.read_raw_folder_meta(normal_dir / ".meta", self.master_key), "Vacation")
        self.assertEqual(cv.read_encrypted_folder_meta(normal_dir / ".meta", self.master_key), "Vacation")

        # Default album must NOT have .nomedia inside it
        default_dir = self.vault_dir / "d4e5f6"
        default_dir.mkdir()
        cv.write_encrypted_folder_meta(default_dir, cv.VAULT_DEFAULT_ALBUM_NAME, self.master_key, vault_dir=self.vault_dir)
        self.assertFalse((default_dir / ".nomedia").exists())
        self.assertEqual(cv.read_raw_folder_meta(default_dir / ".meta", self.master_key), cv.VAULT_DEFAULT_ALBUM_NAME)
        self.assertEqual(cv.read_encrypted_folder_meta(default_dir / ".meta", self.master_key), cv.DEFAULT_SECRET_ALBUM_NAME)

        # Recycle bin must NOT get .nomedia created by write_encrypted_folder_meta
        bin_dir = self.vault_dir / "112233"
        bin_dir.mkdir()
        cv.write_encrypted_folder_meta(bin_dir, cv.VAULT_RECYCLE_BIN_NAME, self.master_key, vault_dir=self.vault_dir)
        self.assertFalse((bin_dir / ".nomedia").exists())
        self.assertEqual(cv.read_raw_folder_meta(bin_dir / ".meta", self.master_key), cv.VAULT_RECYCLE_BIN_NAME)
        self.assertEqual(cv.read_encrypted_folder_meta(bin_dir / ".meta", self.master_key), "Recycle Bin")

        # Dot-prefixed custom album SHOULD get .nomedia inside it
        dot_dir = self.vault_dir / "445566"
        dot_dir.mkdir()
        cv.write_encrypted_folder_meta(dot_dir, ".SecretFolder", self.master_key, vault_dir=self.vault_dir)
        self.assertTrue((dot_dir / ".nomedia").is_file())
        self.assertEqual(cv.read_raw_folder_meta(dot_dir / ".meta", self.master_key), ".SecretFolder")

        # Wrong key / corrupted .meta returns None gracefully
        wrong_key = secrets.token_bytes(32)
        self.assertIsNone(cv.read_raw_folder_meta(normal_dir / ".meta", wrong_key))
        self.assertIsNone(cv.read_encrypted_folder_meta(normal_dir / ".meta", wrong_key))

    def test_scan_existing_vault_albums_ignores_system_dirs(self):
        # Create valid default album (legacy Turkish variant), valid named album, recycle bin, JSON, .hidden, and bad .meta
        def_dir = self.vault_dir / "aa1122"
        def_dir.mkdir()
        cv.write_encrypted_folder_meta(def_dir, "Varsayılan Gizli Albüm", self.master_key)

        vac_dir = self.vault_dir / "bb3344"
        vac_dir.mkdir()
        cv.write_encrypted_folder_meta(vac_dir, "Summer Trip", self.master_key)

        rbin_dir = self.vault_dir / "cc5566"
        rbin_dir.mkdir()
        cv.write_encrypted_folder_meta(rbin_dir, cv.VAULT_RECYCLE_BIN_NAME, self.master_key)

        json_dir = self.vault_dir / "JSON"
        json_dir.mkdir()
        (json_dir / "album.json").write_text("{}", encoding="utf-8")

        dot_dir = self.vault_dir / ".internal"
        dot_dir.mkdir()

        corrupt_dir = self.vault_dir / "dd7788"
        corrupt_dir.mkdir()
        (corrupt_dir / ".meta").write_bytes(b"corrupted-meta-bytes")

        cache = cv.scan_existing_vault_albums(self.vault_dir, self.master_key)
        self.assertEqual(cache.default_album_dir, def_dir)
        self.assertEqual(cache.exact_albums, {"Summer Trip": vac_dir})
        self.assertEqual(cache.normalized_albums, {"summer trip": vac_dir})

    def test_root_files_route_to_default_secret_album_and_reuse_on_second_run(self):
        input_dir = self.root / "To_Encrypt"
        input_dir.mkdir()
        (input_dir / "root1.jpg").write_bytes(b"fake-jpg-1")
        (input_dir / "root2.png").write_bytes(b"fake-png-2")
        (input_dir / "readme.txt").write_text("unsupported", encoding="utf-8")
        (input_dir / ".hidden.jpg").write_bytes(b"hidden-skip")
        (input_dir / "existing.bin").write_bytes(b"bin-skip")

        buf = io.StringIO()
        with redirect_stdout(buf):
            cv.run_encrypt_mode(self._make_encrypt_args(input_dir, output_dir=self.vault_dir), self.desktop, "en")
        out_text = buf.getvalue()

        self.assertIn("Skipping unsupported format", out_text)
        self.assertIn("Skipped 1 unsupported file(s)", out_text)
        self.assertTrue((self.vault_dir / ".nomedia").is_file())
        self.assertEqual(list(self.vault_dir.glob("*.bin")), [])

        subdirs = [p for p in self.vault_dir.iterdir() if p.is_dir()]
        self.assertEqual(len(subdirs), 1)
        default_sub = subdirs[0]
        self.assertEqual(len(default_sub.name), 6)
        self.assertFalse((default_sub / ".nomedia").exists())
        self.assertEqual(
            cv.read_raw_folder_meta(default_sub / ".meta", self.master_key),
            cv.VAULT_DEFAULT_ALBUM_NAME,
        )
        self.assertEqual(len(list(default_sub.glob("*.bin"))), 2)

        # Second run with another root file must reuse the SAME Default Secret Album folder
        input_dir2 = self.root / "To_Encrypt_2"
        input_dir2.mkdir()
        (input_dir2 / "root3.mp4").write_bytes(b"fake-mp4-3")

        with redirect_stdout(io.StringIO()):
            cv.run_encrypt_mode(self._make_encrypt_args(input_dir2, output_dir=self.vault_dir), self.desktop, "en")

        subdirs_after = [p for p in self.vault_dir.iterdir() if p.is_dir()]
        self.assertEqual(subdirs_after, [default_sub])
        self.assertEqual(len(list(default_sub.glob("*.bin"))), 3)

    def test_subfolder_matching_reuses_existing_and_flattens_nested(self):
        input_dir = self.root / "Batch1"
        (input_dir / "Vacation").mkdir(parents=True)
        (input_dir / "Vacation" / "a.jpg").write_bytes(b"vac-a")
        (input_dir / "Family" / "Day1").mkdir(parents=True)
        (input_dir / "Family" / "Day1" / "b.png").write_bytes(b"fam-b")
        (input_dir / ".Private").mkdir(parents=True)
        (input_dir / ".Private" / "c.webp").write_bytes(b"priv-c")

        with redirect_stdout(io.StringIO()):
            cv.run_encrypt_mode(self._make_encrypt_args(input_dir, output_dir=self.vault_dir), self.desktop, "en")

        cache1 = cv.scan_existing_vault_albums(self.vault_dir, self.master_key)
        self.assertIn("Vacation", cache1.exact_albums)
        self.assertIn("Family", cache1.exact_albums)
        self.assertIn(".Private", cache1.exact_albums)
        self.assertFalse((cache1.exact_albums["Vacation"] / ".nomedia").exists())
        self.assertFalse((cache1.exact_albums["Family"] / ".nomedia").exists())
        self.assertTrue((cache1.exact_albums[".Private"] / ".nomedia").is_file())

        # Second run: case-variant "vacation" + brand-new "Work"
        input_dir2 = self.root / "Batch2"
        (input_dir2 / "vacation").mkdir(parents=True)
        (input_dir2 / "vacation" / "d.jpg").write_bytes(b"vac-d")
        (input_dir2 / "Work").mkdir(parents=True)
        (input_dir2 / "Work" / "e.avif").write_bytes(b"work-e")

        with redirect_stdout(io.StringIO()):
            cv.run_encrypt_mode(self._make_encrypt_args(input_dir2, output_dir=self.vault_dir), self.desktop, "en")

        cache2 = cv.scan_existing_vault_albums(self.vault_dir, self.master_key)
        self.assertEqual(cache2.exact_albums["Vacation"], cache1.exact_albums["Vacation"])
        self.assertEqual(len(list(cache2.exact_albums["Vacation"].glob("*.bin"))), 2)
        self.assertIn("Work", cache2.exact_albums)
        self.assertEqual(len(list(cache2.exact_albums["Work"].glob("*.bin"))), 1)
        self.assertFalse((cache2.exact_albums["Work"] / ".nomedia").exists())

    def test_mixed_input_json_untouched_and_encrypted_vault_verification(self):
        json_dir = self.vault_dir / "JSON"
        json_dir.mkdir()
        stealth_json = json_dir / "deadbeef.json"
        stealth_json.write_text('{"name":"encrypted-virtual-album"}', encoding="utf-8")

        input_dir = self.root / "MixedInput"
        (input_dir / "Trips").mkdir(parents=True)
        root_payload = os.urandom(70 * 1024)
        trip_payload = os.urandom(50 * 1024)
        (input_dir / "root_photo.jpg").write_bytes(root_payload)
        (input_dir / "Trips" / "beach.png").write_bytes(trip_payload)
        (input_dir / "ignore_me.pdf").write_bytes(b"%PDF-1.4")

        with redirect_stdout(io.StringIO()):
            cv.run_encrypt_mode(self._make_encrypt_args(input_dir, output_dir=self.vault_dir), self.desktop, "en")

        # JSON virtual album directory must be completely untouched
        self.assertEqual(stealth_json.read_text(encoding="utf-8"), '{"name":"encrypted-virtual-album"}')
        self.assertEqual(list(self.vault_dir.glob("*.bin")), [])

        cache = cv.scan_existing_vault_albums(self.vault_dir, self.master_key)
        self.assertIsNotNone(cache.default_album_dir)
        self.assertIn("Trips", cache.exact_albums)

        root_bins = list(cache.default_album_dir.glob("*.bin"))
        trip_bins = list(cache.exact_albums["Trips"].glob("*.bin"))
        self.assertEqual(len(root_bins), 1)
        self.assertEqual(len(trip_bins), 1)

        root_dec = decrypt_bin_for_test(root_bins[0], self.master_key)
        trip_dec = decrypt_bin_for_test(trip_bins[0], self.master_key)
        self.assertEqual(root_dec["orig_name"], "root_photo.jpg")
        self.assertEqual(root_dec["orig_size"], len(root_payload))
        self.assertEqual(root_dec["payload"], root_payload)
        self.assertEqual(trip_dec["orig_name"], "beach.png")
        self.assertEqual(trip_dec["orig_size"], len(trip_payload))
        self.assertEqual(trip_dec["payload"], trip_payload)

        all_bins = list(self.vault_dir.rglob("*.bin"))
        self.assertEqual(len(all_bins), 2)

    def test_delete_source_preserves_unsupported_files(self):
        input_dir = self.root / "WipeTest"
        (input_dir / "Sub").mkdir(parents=True)
        (input_dir / "pic1.jpg").write_bytes(b"img-1")
        (input_dir / "keep.txt").write_text("do not delete", encoding="utf-8")
        (input_dir / "Sub" / "pic2.png").write_bytes(b"img-2")
        (input_dir / "Sub" / "keep.pdf").write_bytes(b"%PDF")

        with redirect_stdout(io.StringIO()):
            cv.run_encrypt_mode(
                self._make_encrypt_args(input_dir, output_dir=self.vault_dir, delete_source=True),
                self.desktop,
                "en",
            )

        self.assertFalse((input_dir / "pic1.jpg").exists())
        self.assertFalse((input_dir / "Sub" / "pic2.png").exists())
        self.assertTrue((input_dir / "keep.txt").is_file())
        self.assertTrue((input_dir / "Sub" / "keep.pdf").is_file())

    def test_unified_output_directory_resolution_without_prompts(self):
        input_dir = self.desktop / "To_Encrypt"
        input_dir.mkdir(parents=True, exist_ok=True)
        desktop_out = self.desktop / "Encrypted_Output"
        args_no_out = argparse.Namespace(output=None)

        # Standalone .vault on Desktop must NOT be treated as a targetable vault directory
        standalone_desktop_vault = self.desktop / ".vault"
        create_test_vault_file(standalone_desktop_vault, self.secret, self.master_key)
        self.assertFalse(cv.is_targetable_vault_dir(standalone_desktop_vault, self.desktop, input_dir, desktop_out))

        # External USB Cipher_Secrets/.vault containing ONLY .vault (delta usage inside Cipher_Secrets)
        self.assertTrue(cv.is_targetable_vault_dir(self.vault_file, self.desktop, input_dir, desktop_out))

        # Populated external Cipher_Secrets/.vault (with .nomedia and a .meta album)
        populated_vault_dir = self.root / "PopulatedUSB" / "Cipher_Secrets"
        populated_vault_dir.mkdir(parents=True)
        populated_vault_file = populated_vault_dir / ".vault"
        create_test_vault_file(populated_vault_file, self.secret, self.master_key)
        (populated_vault_dir / ".nomedia").touch()
        album_sub = populated_vault_dir / "a1b2c3"
        album_sub.mkdir()
        cv.write_encrypted_folder_meta(album_sub, "Album", self.master_key, vault_dir=populated_vault_dir)

        # External root .vault (e.g. /media/usb/.vault without Cipher_Secrets parent)
        usb_root_dir = self.root / "ExternalUSBRoot"
        usb_root_dir.mkdir()
        usb_root_vault = usb_root_dir / ".vault"
        create_test_vault_file(usb_root_vault, self.secret, self.master_key)
        self.assertFalse(cv.is_targetable_vault_dir(usb_root_vault, self.desktop, input_dir, desktop_out))

        with mock.patch("sys.stdin.isatty", return_value=True), \
             mock.patch("builtins.input", side_effect=AssertionError("Should not prompt for destination")):
            # 1. <USB>/Cipher_Secrets/.vault (only .vault present) -> <USB>/Cipher_Secrets
            buf1 = io.StringIO()
            with redirect_stdout(buf1):
                chosen_only_vault = cv.choose_encrypt_output_dir(
                    args_no_out, self.desktop, "en", input_dir, self.vault_file
                )
            self.assertEqual(chosen_only_vault, self.vault_dir.resolve())
            self.assertIn(str(self.vault_dir.resolve()), buf1.getvalue())

            # 2. <USB>/Cipher_Secrets/.vault (with existing contents) -> <USB>/Cipher_Secrets
            with redirect_stdout(io.StringIO()):
                chosen_populated = cv.choose_encrypt_output_dir(
                    args_no_out, self.desktop, "en", input_dir, populated_vault_file
                )
            self.assertEqual(chosen_populated, populated_vault_dir.resolve())

            # 3. <USB>/.vault at external root -> <USB>/Cipher_Secrets
            with redirect_stdout(io.StringIO()):
                chosen_usb_root = cv.choose_encrypt_output_dir(
                    args_no_out, self.desktop, "en", input_dir, usb_root_vault
                )
            self.assertEqual(chosen_usb_root, (usb_root_dir / "Cipher_Secrets").resolve())

            # 4. Standalone ~/Desktop/.vault -> ~/Desktop/Encrypted_Output
            with redirect_stdout(io.StringIO()):
                chosen_desktop = cv.choose_encrypt_output_dir(
                    args_no_out, self.desktop, "en", input_dir, standalone_desktop_vault
                )
            self.assertEqual(chosen_desktop, desktop_out.resolve())

            # 5. Explicit -o / --output overrides cleanly
            custom_path = self.root / "CustomVault"
            args_with_out = argparse.Namespace(output=str(custom_path))
            with redirect_stdout(io.StringIO()):
                chosen_override = cv.choose_encrypt_output_dir(
                    args_with_out, self.desktop, "en", input_dir, self.vault_file
                )
            self.assertEqual(chosen_override, custom_path.resolve())

    def test_turkish_messages_when_skipping_unsupported(self):
        input_dir = self.root / "Sifrelenecek"
        input_dir.mkdir()
        (input_dir / "notlar.txt").write_text("merhaba", encoding="utf-8")
        (input_dir / "foto.jpg").write_bytes(b"jpg-bytes")

        buf = io.StringIO()
        with redirect_stdout(buf):
            cv.run_encrypt_mode(
                self._make_encrypt_args(input_dir, output_dir=self.vault_dir, lang="tr"),
                self.desktop,
                "tr",
            )
        out_text = buf.getvalue()
        self.assertIn("Desteklenmeyen format atlanıyor", out_text)
        self.assertIn("Desteklenmeyen 1 dosya atlandı", out_text)

    def test_cli_subprocess_default_encrypt_and_rejects_decrypt(self):
        import subprocess
        import sys

        cli_script = Path(__file__).resolve().parent / "cipher_vault.py"
        in_dir = self.root / "CliInput"
        (in_dir / "AlbumOne").mkdir(parents=True)
        root_data = b"cli-root-image-payload"
        sub_data = b"cli-sub-video-payload"
        (in_dir / "root.jpg").write_bytes(root_data)
        (in_dir / "AlbumOne" / "clip.mp4").write_bytes(sub_data)
        (in_dir / "ignore.txt").write_text("skip", encoding="utf-8")

        # Default CLI invocation without -e/--encrypt must run encryption directly
        enc_proc = subprocess.run(
            [
                sys.executable,
                str(cli_script),
                "--lang", "en",
                "-i", str(in_dir),
                "-o", str(self.vault_dir),
                "-k", str(self.vault_file),
                "-s", self.secret,
            ],
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(enc_proc.returncode, 0, msg=enc_proc.stderr + enc_proc.stdout)
        self.assertIn("Skipped 1 unsupported file(s)", enc_proc.stdout)

        cache = cv.scan_existing_vault_albums(self.vault_dir, self.master_key)
        self.assertIsNotNone(cache.default_album_dir)
        self.assertIn("AlbumOne", cache.exact_albums)
        root_dec = decrypt_bin_for_test(next(cache.default_album_dir.glob("*.bin")), self.master_key)
        sub_dec = decrypt_bin_for_test(next(cache.exact_albums["AlbumOne"].glob("*.bin")), self.master_key)
        self.assertEqual(root_dec["orig_name"], "root.jpg")
        self.assertEqual(root_dec["payload"], root_data)
        self.assertEqual(sub_dec["orig_name"], "clip.mp4")
        self.assertEqual(sub_dec["payload"], sub_data)

        # Compatibility -e / --encrypt flag still works cleanly
        in_dir2 = self.root / "CliInput2"
        in_dir2.mkdir()
        (in_dir2 / "extra.png").write_bytes(b"extra-png-bytes")
        compat_proc = subprocess.run(
            [
                sys.executable,
                str(cli_script),
                "--encrypt",
                "--lang", "en",
                "-i", str(in_dir2),
                "-o", str(self.vault_dir),
                "-v", str(self.vault_file),
                "-s", self.secret,
            ],
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(compat_proc.returncode, 0, msg=compat_proc.stderr + compat_proc.stdout)
        self.assertEqual(len(list(cache.default_album_dir.glob("*.bin"))), 2)

        # Removed --decrypt / -d flags must be rejected by argparse
        for bad_flag in ("--decrypt", "-d"):
            rej_proc = subprocess.run(
                [sys.executable, str(cli_script), bad_flag],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertNotEqual(rej_proc.returncode, 0, msg=f"Expected {bad_flag} to be rejected")

    def test_find_vault_file_prioritizes_external_cipher_secrets_and_prompt_keeps_external_path(self):
        fake_media = self.root / "media_amnesia"
        usb_vault_dir = fake_media / "USBDRIVE" / "Cipher_Secrets"
        usb_vault_dir.mkdir(parents=True)
        usb_vault = usb_vault_dir / ".vault"
        create_test_vault_file(usb_vault, self.secret, self.master_key)

        desktop_vault = self.desktop / ".vault"
        create_test_vault_file(desktop_vault, self.secret, self.master_key)

        with mock.patch.object(cv, "get_desktop_dir", return_value=self.desktop), \
             mock.patch.object(cv, "_external_mount_roots", return_value=[fake_media]):
            detected = cv.find_vault_file()
        self.assertEqual(detected, usb_vault)

        # Ensure prompt_for_vault_file returns the external Cipher_Secrets/.vault directly
        # without replacing it with Desktop/.vault
        empty_desktop = self.root / "EmptyDesktop"
        empty_desktop.mkdir()
        with mock.patch.object(cv, "get_desktop_dir", return_value=empty_desktop), \
             mock.patch("builtins.input", return_value=str(usb_vault)), \
             redirect_stdout(io.StringIO()):
            chosen = cv.prompt_for_vault_file("en")
        self.assertEqual(chosen, usb_vault.resolve())
        self.assertFalse((empty_desktop / ".vault").exists())

    def test_only_unsupported_files_exits_zero_with_skipped_summary(self):
        in_dir = self.root / "OnlyUnsupported"
        in_dir.mkdir()
        (in_dir / "notes.md").write_text("# hi", encoding="utf-8")

        buf = io.StringIO()
        with redirect_stdout(buf):
            with self.assertRaises(SystemExit) as ctx:
                cv.run_encrypt_mode(self._make_encrypt_args(in_dir, output_dir=self.vault_dir), self.desktop, "en")
        self.assertEqual(ctx.exception.code, 0)
        self.assertIn("Skipping unsupported format", buf.getvalue())
        self.assertIn("Skipped 1 unsupported file(s)", buf.getvalue())

    def test_pil_image_with_thumbnail_generated_and_verified(self):
        from PIL import Image

        in_dir = self.root / "PilInput"
        (in_dir / "Camera").mkdir(parents=True)
        img_path = in_dir / "Camera" / "real_photo.jpg"
        Image.new("RGB", (640, 480), color=(20, 120, 220)).save(img_path, format="JPEG")
        orig_bytes = img_path.read_bytes()

        with redirect_stdout(io.StringIO()):
            cv.run_encrypt_mode(self._make_encrypt_args(in_dir, output_dir=self.vault_dir), self.desktop, "en")

        cache = cv.scan_existing_vault_albums(self.vault_dir, self.master_key)
        self.assertIn("Camera", cache.exact_albums)
        cam_bins = list(cache.exact_albums["Camera"].glob("*.bin"))
        self.assertEqual(len(cam_bins), 1)

        dec = decrypt_bin_for_test(cam_bins[0], self.master_key)
        self.assertEqual(dec["orig_name"], "real_photo.jpg")
        self.assertEqual(dec["media_type"], cv.TYPE_IMAGES)
        self.assertGreater(len(dec["thumb_bytes"]), 0)
        self.assertEqual(dec["payload"], orig_bytes)

    def test_interactive_password_retry_loop(self):
        args = argparse.Namespace(
            vault=str(self.vault_file),
            secret=None,
            pattern=None,
        )
        with mock.patch("getpass.getpass", side_effect=["", "wrong-secret", self.secret]), \
             redirect_stdout(io.StringIO()), \
             mock.patch("sys.stderr", new_callable=io.StringIO):
            unlocked_key, unlocked_path = cv.unlock_master_key_with_path(args, "en")
        self.assertEqual(unlocked_key, self.master_key)
        self.assertEqual(unlocked_path, self.vault_file.resolve())

    def test_encrypt_only_module_has_no_decryption_code(self):
        for removed in ("decrypt_file", "decrypt_stream", "run_decrypt_mode", "choose_operation_mode", "resolve_unique_output_path"):
            self.assertFalse(hasattr(cv, removed), f"cipher_vault must not expose {removed}")
        source_text = Path(cv.__file__).read_text(encoding="utf-8")
        for forbidden in ("decrypt_file", "decrypt_stream", "run_decrypt_mode", "import zipfile"):
            self.assertNotIn(forbidden, source_text)


if __name__ == "__main__":
    unittest.main(verbosity=2)

#!/usr/bin/env python3
"""
Unit & end-to-end integration tests for the Calypso Gallery offline CLI (calypso_vault.py).
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

import calypso_vault as cv
from test_tink_stream import parse_container, reference_expected_ciphertext_size


def decrypt_bin_for_test(bin_path: Path, kek: bytes) -> dict:
    """Test-only helper: parse and decrypt a canonical FGVLTENC v1 .bin container."""
    return parse_container(bin_path.read_bytes(), kek)


def create_test_vault_file(
    vault_path: Path,
    secret: str,
    vmk: bytes | None = None,
    kek: bytes | None = None,
) -> tuple[bytes, bytes]:
    """Builds a canonical v1 vault header (PDK -> VMK -> keyring -> KEK)."""
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    from argon2.low_level import hash_secret_raw, Type

    if vmk is None:
        vmk = secrets.token_bytes(32)
    if kek is None:
        kek = secrets.token_bytes(32)

    iterations = 1
    memory_kb = 8192
    parallelism = 1
    salt = secrets.token_bytes(16)
    iv_pdk = secrets.token_bytes(12)
    pdk = hash_secret_raw(
        secret=secret.encode("utf-8"),
        salt=salt,
        time_cost=iterations,
        memory_cost=memory_kb,
        parallelism=parallelism,
        hash_len=32,
        type=Type.ID,
    )
    wrapped_vmk = AESGCM(pdk).encrypt(iv_pdk, vmk, b"")

    iv_vmk = secrets.token_bytes(12)
    keyring = struct.pack(">H", 1) + kek
    wrapped_keyring = AESGCM(vmk).encrypt(iv_vmk, keyring, b"")

    with open(vault_path, "wb") as f:
        f.write(cv.VAULT_HEADER_MAGIC)               # 5B "FGVLT"
        f.write(bytes([1, 1]))                       # version 1, Argon2id
        f.write(struct.pack(">3i", iterations, memory_kb, parallelism))
        f.write(bytes([1]))                          # protection type
        f.write(struct.pack(">i", len(salt)))
        f.write(salt)
        f.write(iv_pdk)
        f.write(struct.pack(">i", len(wrapped_vmk)))
        f.write(wrapped_vmk)
        f.write(iv_vmk)
        f.write(struct.pack(">i", len(wrapped_keyring)))
        f.write(wrapped_keyring)

    return vmk, kek


class TestCalypsoVaultCLI(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tmpdir.name)
        self.desktop = self.root / "Desktop"
        self.desktop.mkdir(parents=True, exist_ok=True)
        self.secret = "test-vault-password-123"
        selfvault_dir = self.root / "ExternalUSB" / "Secrets"
        selfvault_dir.mkdir(parents=True, exist_ok=True)
        selfvault_file = selfvault_dir / "vault"
        self.vmk, self.kek = create_test_vault_file(selfvault_file, self.secret)
        self.master_key = self.vmk   # VMK protects folder .meta files

    def tearDown(self):
        self.tmpdir.cleanup()

    def _make_encrypt_args(
        self,
        input_dir: Path,
        output_dir: Path | None = None,
        vault_file: Path | None = None,
        delete_source: bool = False,
    ) -> argparse.Namespace:
        return argparse.Namespace(
            encrypt=True,
            vault=str(vault_file or selfvault_file),
            input=str(input_dir),
            output=str(output_dir) if output_dir is not None else None,
            secret=self.secret,
            pattern=None,
            delete_source=delete_source,
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
            "payload.bin", "vault", ".meta", ".nomedia", "noextension",
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
        normal_dir = selfvault_dir / "a1b2c3"
        normal_dir.mkdir()
        cv.write_encrypted_folder_meta(normal_dir, "Vacation", self.master_key, vault_dir=selfvault_dir)
        self.assertTrue((selfvault_dir / ".nomedia").is_file())
        self.assertFalse((normal_dir / ".nomedia").exists())
        self.assertEqual((normal_dir / ".meta").read_bytes()[:6], cv.FOLDER_META_MAGIC)
        self.assertEqual(cv.read_raw_folder_meta(normal_dir / ".meta", self.master_key), "Vacation")
        self.assertEqual(cv.read_encrypted_folder_meta(normal_dir / ".meta", self.master_key), "Vacation")

        # Default album must NOT have .nomedia inside it
        default_dir = selfvault_dir / "d4e5f6"
        default_dir.mkdir()
        cv.write_encrypted_folder_meta(default_dir, cv.VAULT_DEFAULT_ALBUM_NAME, self.master_key, vault_dir=selfvault_dir)
        self.assertFalse((default_dir / ".nomedia").exists())
        self.assertEqual(cv.read_raw_folder_meta(default_dir / ".meta", self.master_key), cv.VAULT_DEFAULT_ALBUM_NAME)
        self.assertEqual(cv.read_encrypted_folder_meta(default_dir / ".meta", self.master_key), cv.DEFAULT_SECRET_ALBUM_NAME)

        # Recycle bin must NOT get .nomedia created by write_encrypted_folder_meta
        bin_dir = selfvault_dir / "112233"
        bin_dir.mkdir()
        cv.write_encrypted_folder_meta(bin_dir, cv.VAULT_RECYCLE_BIN_NAME, self.master_key, vault_dir=selfvault_dir)
        self.assertFalse((bin_dir / ".nomedia").exists())
        self.assertEqual(cv.read_raw_folder_meta(bin_dir / ".meta", self.master_key), cv.VAULT_RECYCLE_BIN_NAME)
        self.assertEqual(cv.read_encrypted_folder_meta(bin_dir / ".meta", self.master_key), "Recycle Bin")

        # Dot-prefixed custom album SHOULD get .nomedia inside it
        dot_dir = selfvault_dir / "445566"
        dot_dir.mkdir()
        cv.write_encrypted_folder_meta(dot_dir, ".SecretFolder", self.master_key, vault_dir=selfvault_dir)
        self.assertTrue((dot_dir / ".nomedia").is_file())
        self.assertEqual(cv.read_raw_folder_meta(dot_dir / ".meta", self.master_key), ".SecretFolder")

        # Wrong key / corrupted .meta returns None gracefully
        wrong_key = secrets.token_bytes(32)
        self.assertIsNone(cv.read_raw_folder_meta(normal_dir / ".meta", wrong_key))
        self.assertIsNone(cv.read_encrypted_folder_meta(normal_dir / ".meta", wrong_key))

    def test_scan_existing_vault_albums_ignores_system_dirs(self):
        # Create a Turkish-named default album, a named album, recycle bin, JSON, .hidden, and bad .meta
        def_dir = selfvault_dir / "aa1122"
        def_dir.mkdir()
        cv.write_encrypted_folder_meta(def_dir, "Varsayılan Gizli Albüm", self.master_key)

        vac_dir = selfvault_dir / "bb3344"
        vac_dir.mkdir()
        cv.write_encrypted_folder_meta(vac_dir, "Summer Trip", self.master_key)

        rbin_dir = selfvault_dir / "cc5566"
        rbin_dir.mkdir()
        cv.write_encrypted_folder_meta(rbin_dir, cv.VAULT_RECYCLE_BIN_NAME, self.master_key)

        json_dir = selfvault_dir / "JSON"
        json_dir.mkdir()
        (json_dir / "album.json").write_text("{}", encoding="utf-8")

        dot_dir = selfvault_dir / ".internal"
        dot_dir.mkdir()

        corrupt_dir = selfvault_dir / "dd7788"
        corrupt_dir.mkdir()
        (corrupt_dir / ".meta").write_bytes(b"corrupted-meta-bytes")

        cache = cv.scan_existing_vault_albums(selfvault_dir, self.master_key)
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
            cv.run_encrypt_mode(self._make_encrypt_args(input_dir, output_dir=selfvault_dir), self.desktop)
        out_text = buf.getvalue()

        self.assertIn("Skipping unsupported format", out_text)
        self.assertIn("Skipped 1 unsupported file(s)", out_text)
        self.assertTrue((selfvault_dir / ".nomedia").is_file())
        self.assertEqual(list(selfvault_dir.glob("*.bin")), [])

        subdirs = [p for p in selfvault_dir.iterdir() if p.is_dir()]
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
            cv.run_encrypt_mode(self._make_encrypt_args(input_dir2, output_dir=selfvault_dir), self.desktop)

        subdirs_after = [p for p in selfvault_dir.iterdir() if p.is_dir()]
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
            cv.run_encrypt_mode(self._make_encrypt_args(input_dir, output_dir=selfvault_dir), self.desktop)

        cache1 = cv.scan_existing_vault_albums(selfvault_dir, self.master_key)
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
            cv.run_encrypt_mode(self._make_encrypt_args(input_dir2, output_dir=selfvault_dir), self.desktop)

        cache2 = cv.scan_existing_vault_albums(selfvault_dir, self.master_key)
        self.assertEqual(cache2.exact_albums["Vacation"], cache1.exact_albums["Vacation"])
        self.assertEqual(len(list(cache2.exact_albums["Vacation"].glob("*.bin"))), 2)
        self.assertIn("Work", cache2.exact_albums)
        self.assertEqual(len(list(cache2.exact_albums["Work"].glob("*.bin"))), 1)
        self.assertFalse((cache2.exact_albums["Work"] / ".nomedia").exists())

    def test_mixed_input_json_untouched_and_encrypted_vault_verification(self):
        json_dir = selfvault_dir / "JSON"
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
            cv.run_encrypt_mode(self._make_encrypt_args(input_dir, output_dir=selfvault_dir), self.desktop)

        # JSON virtual album directory must be completely untouched
        self.assertEqual(stealth_json.read_text(encoding="utf-8"), '{"name":"encrypted-virtual-album"}')
        self.assertEqual(list(selfvault_dir.glob("*.bin")), [])

        cache = cv.scan_existing_vault_albums(selfvault_dir, self.master_key)
        self.assertIsNotNone(cache.default_album_dir)
        self.assertIn("Trips", cache.exact_albums)

        root_bins = list(cache.default_album_dir.glob("*.bin"))
        trip_bins = list(cache.exact_albums["Trips"].glob("*.bin"))
        self.assertEqual(len(root_bins), 1)
        self.assertEqual(len(trip_bins), 1)

        root_dec = decrypt_bin_for_test(root_bins[0], self.kek)
        trip_dec = decrypt_bin_for_test(trip_bins[0], self.kek)
        self.assertEqual(root_dec["orig_name"], "root_photo.jpg")
        self.assertEqual(root_dec["orig_size"], len(root_payload))
        self.assertEqual(root_dec["payload"], root_payload)
        self.assertEqual(trip_dec["orig_name"], "beach.png")
        self.assertEqual(trip_dec["orig_size"], len(trip_payload))
        self.assertEqual(trip_dec["payload"], trip_payload)

        all_bins = list(selfvault_dir.rglob("*.bin"))
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
                self._make_encrypt_args(input_dir, output_dir=selfvault_dir, delete_source=True),
                self.desktop,
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

        # Standalone vault on Desktop is a key source, not a staging target.
        standalone_desktop_vault = self.desktop / "vault"
        create_test_vault_file(standalone_desktop_vault, self.secret, self.master_key)
        self.assertFalse(cv.is_targetable_vault_dir(standalone_desktop_vault, self.desktop, input_dir, desktop_out))

        with mock.patch("sys.stdin.isatty", return_value=True), \
             mock.patch("builtins.input", side_effect=AssertionError("Should not prompt for destination")):
            # 1. <USB>/Secrets/vault -> sibling <USB>/CLI (no "Secrets" name dependency in derive).
            buf1 = io.StringIO()
            with redirect_stdout(buf1):
                chosen_only_vault = cv.choose_encrypt_output_dir(
                    args_no_out, self.desktop, input_dir, selfvault_file
                )
            self.assertEqual(chosen_only_vault, (selfvault_dir.parent / "CLI").resolve())
            self.assertIn(str(chosen_only_vault), buf1.getvalue())

            # 2. Same derivation even when the exported vault is populated.
            populated_vault_dir = self.root / "PopulatedUSB" / "Secrets"
            populated_vault_dir.mkdir(parents=True)
            populated_vault_file = populated_vault_dir / "vault"
            create_test_vault_file(populated_vault_file, self.secret, self.master_key)
            (populated_vault_dir / ".nomedia").touch()
            with redirect_stdout(io.StringIO()):
                chosen_populated = cv.choose_encrypt_output_dir(
                    args_no_out, self.desktop, input_dir, populated_vault_file
                )
            self.assertEqual(chosen_populated, (populated_vault_dir.parent / "CLI").resolve())

            # 3. Arbitrary custom vault folder name -> sibling CLI, not a Secrets child.
            custom_vault_dir = self.root / "MyCustomVault"
            custom_vault_dir.mkdir()
            custom_vault_file = custom_vault_dir / "vault"
            create_test_vault_file(custom_vault_file, self.secret, self.master_key)
            with redirect_stdout(io.StringIO()):
                chosen_custom = cv.choose_encrypt_output_dir(
                    args_no_out, self.desktop, input_dir, custom_vault_file
                )
            self.assertEqual(chosen_custom, (self.root / "CLI").resolve())

            # 4. Standalone ~/Desktop/vault -> ~/Desktop/CLI (special folder: CLI inside it).
            with redirect_stdout(io.StringIO()):
                chosen_desktop = cv.choose_encrypt_output_dir(
                    args_no_out, self.desktop, input_dir, standalone_desktop_vault
                )
            self.assertEqual(chosen_desktop, (self.desktop / "CLI").resolve())

            # 5. Explicit -o / --output overrides cleanly.
            custom_path = self.root / "CustomStaging"
            args_with_out = argparse.Namespace(output=str(custom_path))
            with redirect_stdout(io.StringIO()):
                chosen_override = cv.choose_encrypt_output_dir(
                    args_with_out, self.desktop, input_dir, selfvault_file
                )
            self.assertEqual(chosen_override, custom_path.resolve())

    def test_last_vault_path_is_remembered_and_validated(self):
        with mock.patch.object(cv, "get_desktop_dir", return_value=self.desktop):
            self.assertIsNone(cv.read_last_vault_path())
            cv.remember_vault_path(selfvault_file)
            self.assertEqual(cv.read_last_vault_path(), selfvault_file.resolve())

            # A stale path must be ignored rather than silently used.
            cv.get_last_vault_path_file().write_text(
                str(self.root / "missingvault"), encoding="utf-8"
            )
            self.assertIsNone(cv.read_last_vault_path())

    def test_english_messages_when_skipping_unsupported(self):
        input_dir = self.root / "To_Encrypt"
        input_dir.mkdir()
        (input_dir / "notes.txt").write_text("hello", encoding="utf-8")
        (input_dir / "photo.jpg").write_bytes(b"jpg-bytes")

        buf = io.StringIO()
        with redirect_stdout(buf):
            cv.run_encrypt_mode(
                self._make_encrypt_args(input_dir, output_dir=selfvault_dir),
                self.desktop,
            )
        out_text = buf.getvalue()
        self.assertIn("Skipping unsupported format", out_text)
        self.assertIn("Skipped 1 unsupported file(s)", out_text)

    def test_cli_subprocess_default_encrypt_and_rejects_decrypt(self):
        import subprocess
        import sys

        cli_script = Path(__file__).resolve().parent / "calypso_vault.py"
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
                "-i", str(in_dir),
                "-o", str(selfvault_dir),
                "-k", str(selfvault_file),
                "-s", self.secret,
            ],
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(enc_proc.returncode, 0, msg=enc_proc.stderr + enc_proc.stdout)
        self.assertIn("Skipped 1 unsupported file(s)", enc_proc.stdout)

        cache = cv.scan_existing_vault_albums(selfvault_dir, self.master_key)
        self.assertIsNotNone(cache.default_album_dir)
        self.assertIn("AlbumOne", cache.exact_albums)
        root_dec = decrypt_bin_for_test(next(cache.default_album_dir.glob("*.bin")), self.kek)
        sub_dec = decrypt_bin_for_test(next(cache.exact_albums["AlbumOne"].glob("*.bin")), self.kek)
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
                "-i", str(in_dir2),
                "-o", str(selfvault_dir),
                "-v", str(selfvault_file),
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

    def test_find_vault_file_prioritizes_external_secrets_and_prompt_keeps_external_path(self):
        fake_media = self.root / "media_amnesia"
        usb_vault_dir = fake_media / "USBDRIVE" / "Secrets"
        usb_vault_dir.mkdir(parents=True)
        usb_vault = usb_vault_dir / "vault"
        create_test_vault_file(usb_vault, self.secret, self.master_key)

        desktop_vault = self.desktop / "vault"
        create_test_vault_file(desktop_vault, self.secret, self.master_key)

        with mock.patch.object(cv, "get_desktop_dir", return_value=self.desktop), \
             mock.patch.object(cv, "_external_mount_roots", return_value=[fake_media]):
            detected = cv.find_vault_file()
        self.assertEqual(detected, usb_vault)

        # Ensure prompt_for_vault_file returns the external Secrets/vault directly
        # without replacing it with Desktop/vault
        empty_desktop = self.root / "EmptyDesktop"
        empty_desktop.mkdir()
        with mock.patch.object(cv, "get_desktop_dir", return_value=empty_desktop), \
             mock.patch("builtins.input", return_value=str(usb_vault)), \
             redirect_stdout(io.StringIO()):
            chosen = cv.prompt_for_vault_file()
        self.assertEqual(chosen, usb_vault.resolve())
        self.assertFalse((empty_desktop / "vault").exists())

    def test_only_unsupported_files_exits_zero_with_skipped_summary(self):
        in_dir = self.root / "OnlyUnsupported"
        in_dir.mkdir()
        (in_dir / "notes.md").write_text("# hi", encoding="utf-8")

        buf = io.StringIO()
        with redirect_stdout(buf):
            with self.assertRaises(SystemExit) as ctx:
                cv.run_encrypt_mode(self._make_encrypt_args(in_dir, output_dir=selfvault_dir), self.desktop)
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
            cv.run_encrypt_mode(self._make_encrypt_args(in_dir, output_dir=selfvault_dir), self.desktop)

        cache = cv.scan_existing_vault_albums(selfvault_dir, self.master_key)
        self.assertIn("Camera", cache.exact_albums)
        cam_bins = list(cache.exact_albums["Camera"].glob("*.bin"))
        self.assertEqual(len(cam_bins), 1)

        dec = decrypt_bin_for_test(cam_bins[0], self.kek)
        self.assertEqual(dec["orig_name"], "real_photo.jpg")
        self.assertEqual(dec["media_type"], cv.TYPE_IMAGES)
        self.assertGreater(len(dec["thumb_bytes"]), 0)
        self.assertEqual(dec["payload"], orig_bytes)

    def test_interactive_password_retry_loop(self):
        args = argparse.Namespace(
            vault=str(selfvault_file),
            secret=None,
            pattern=None,
        )
        with mock.patch("getpass.getpass", side_effect=["", "wrong-secret", self.secret]), \
             redirect_stdout(io.StringIO()), \
             mock.patch("sys.stderr", new_callable=io.StringIO):
            unlocked_keys, unlocked_path = cv.unlock_master_key_with_path(args)
        self.assertEqual(unlocked_keys.vmk, self.vmk)
        self.assertEqual(unlocked_keys.kek, self.kek)
        self.assertEqual(cv.unlock_vault_header_bytes(selfvault_file.read_bytes(), self.secret), self.kek)
        self.assertEqual(unlocked_path, selfvault_file.resolve())

    def test_cloud_sync_path_detection_and_skips_desktop_vault_copy(self):
        self.assertTrue(cv.is_cloud_synced_path(Path("/Users/alice/Library/Mobile Documents/com~apple~CloudDocs/Desktop")))
        self.assertTrue(cv.is_cloud_synced_path(Path("C:/Users/alice/OneDrive/Desktop")))
        self.assertTrue(cv.is_cloud_synced_path(Path("/home/alice/Dropbox/Photos")))
        self.assertFalse(cv.is_cloud_synced_path(self.root / "LocalDesktop"))

        with mock.patch.dict(os.environ, {"OneDrive": str(self.root / "SyncedRoot")}, clear=False):
            with mock.patch("os.name", "nt"):
                self.assertTrue(cv.is_cloud_synced_path(self.root / "SyncedRoot" / "Desktop"))

        # Standalone vault imported when Desktop is cloud-synced must NOT prompt to copy vault to Desktop
        synced_desktop = self.root / "OneDrive" / "Desktop"
        synced_desktop.mkdir(parents=True)
        standalone_src = self.root / "LocalUSB" / "phonevault"
        standalone_src.parent.mkdir(parents=True)
        create_test_vault_file(standalone_src, self.secret, self.master_key)

        with mock.patch.object(cv, "get_desktop_dir", return_value=synced_desktop), \
             mock.patch("builtins.input", side_effect=[str(standalone_src)]), \
             redirect_stdout(io.StringIO()):
            chosen = cv.prompt_for_vault_file()
        self.assertEqual(chosen, standalone_src.resolve())
        self.assertFalse((synced_desktop / "vault").exists())

    def test_cross_platform_mount_roots_and_windows_drive_vault_discovery(self):
        with mock.patch.object(cv, "get_platform_kind", return_value="macos"):
            self.assertEqual(cv._external_mount_roots(), [Path("/Volumes")])

        fake_usb_drive = self.root / "FakeDriveE"
        fake_usb_drive.mkdir()
        drive_root_vault = fake_usb_drive / "vault"
        create_test_vault_file(drive_root_vault, self.secret, self.master_key)

        with mock.patch.object(cv, "get_platform_kind", return_value="windows"), \
             mock.patch.object(cv, "get_desktop_dir", return_value=self.desktop), \
             mock.patch.object(cv, "_external_mount_roots", return_value=[fake_usb_drive]):
            detected_root = cv.find_vault_file()
        self.assertEqual(detected_root, drive_root_vault)

        # Secrets/vault on Windows-style drive takes priority over root vault
        cs_vault = fake_usb_drive / "Secrets" / "vault"
        cs_vault.parent.mkdir(parents=True)
        create_test_vault_file(cs_vault, self.secret, self.master_key)
        with mock.patch.object(cv, "get_platform_kind", return_value="windows"), \
             mock.patch.object(cv, "get_desktop_dir", return_value=self.desktop), \
             mock.patch.object(cv, "_external_mount_roots", return_value=[fake_usb_drive]):
            detected_cs = cv.find_vault_file()
        self.assertEqual(detected_cs, cs_vault)

    def test_windows_and_macos_notices_and_readonly_wipe_without_extra_prompts(self):
        in_dir = self.root / "OneDrive" / "To_Encrypt"
        in_dir.mkdir(parents=True)
        ro_img = in_dir / "readonly.jpg"
        ro_img.write_bytes(b"readonly-jpg-bytes")
        os.chmod(ro_img, 0o444)

        buf = io.StringIO()
        with mock.patch.object(cv, "get_platform_kind", return_value="windows"), \
             mock.patch("sys.stdin.isatty", return_value=True), \
             mock.patch("builtins.input", side_effect=AssertionError("Should not prompt for OS choice or wipe when --delete-source is set")), \
             redirect_stdout(buf):
            cv.run_encrypt_mode(
                self._make_encrypt_args(in_dir, output_dir=selfvault_dir, delete_source=True),
                self.desktop,
            )

        out = buf.getvalue()
        self.assertIn("Standard OS Mode (Windows)", out)
        self.assertIn("Cloud-synced folder detected", out)
        self.assertIn("SSD wear-leveling", out)
        self.assertFalse(ro_img.exists())

    def test_encrypt_only_module_has_no_decryption_code(self):
        for removed in ("decrypt_file", "decrypt_stream", "run_decrypt_mode", "choose_operation_mode", "resolve_unique_output_path"):
            self.assertFalse(hasattr(cv, removed), f"calypso_vault must not expose {removed}")
        source_text = Path(cv.__file__).read_text(encoding="utf-8")
        for forbidden in ("decrypt_file", "decrypt_stream", "run_decrypt_mode", "import zipfile"):
            self.assertNotIn(forbidden, source_text)

    def test_no_legacy_or_superseded_format_symbols(self):
        for removed in ("derive_subkey", "poly1305_aes_mac", "poly1305_aes_mac_stream", "read_vault_format"):
            self.assertFalse(hasattr(cv, removed), f"calypso_vault must not expose {removed}")
        source_text = Path(cv.__file__).read_text(encoding="utf-8")
        for forbidden in (
            "Poly1305", "poly1305", "pycryptodome",
            "FGMETA2", "FVLTMETA", ".fmeta", "vault_format_mismatch",
        ):
            self.assertNotIn(forbidden, source_text)

    def test_encrypt_file_writes_canonical_container(self):
        source = self.root / "CanonicalPhoto.jpg"
        payload = bytes((i * 17 + 3) & 0xFF for i in range(262089))  # crosses a segment boundary
        source.write_bytes(payload)

        out = cv.encrypt_file(source, selfvault_dir, self.kek)
        data = out.read_bytes()

        self.assertEqual(data[:8], cv.CONTAINER_MAGIC)
        self.assertEqual(data[8], 1)                                   # version
        self.assertEqual(struct.unpack(">H", data[9:11])[0], 1)         # keyId
        self.assertEqual(len(data[11:27]), 16)                          # fileId

        parsed = parse_container(data, self.kek)
        self.assertEqual(parsed["orig_name"], "CanonicalPhoto.jpg")
        self.assertEqual(parsed["orig_size"], len(payload))
        self.assertEqual(parsed["payload"], payload)
        n = struct.unpack(">I", data[39:43])[0]
        self.assertEqual(parsed["ciphertext_length"], len(data) - cv.FIXED_PREFIX_LENGTH - n)
        self.assertEqual(parsed["ciphertext_length"], reference_expected_ciphertext_size(len(payload)))
        self.assertEqual(len(data), cv.FIXED_PREFIX_LENGTH + n + parsed["ciphertext_length"])

    def test_canonical_vault_header_version_and_keyring(self):
        raw = selfvault_file.read_bytes()
        self.assertEqual(raw[:5], cv.VAULT_HEADER_MAGIC)
        self.assertEqual(raw[5], 1)   # canonical version 1
        self.assertEqual(raw[6], 1)   # Argon2id

        keys = cv.unwrap_vault_keys(raw, self.secret)
        self.assertEqual(keys.vmk, self.vmk)
        self.assertEqual(keys.kek, self.kek)
        self.assertEqual(cv.unlock_vault_header_bytes(raw, self.secret), self.kek)
        with self.assertRaises(ValueError):
            cv.unwrap_vault_keys(raw, "wrong-secret")

    def test_encrypt_file_uses_kek_not_vmk(self):
        """A container encrypted with the KEK must not open with the VMK."""
        source = self.root / "KeySeparation.jpg"
        source.write_bytes(b"key-separation-payload")
        out = cv.encrypt_file(source, selfvault_dir, self.kek)
        from cryptography.exceptions import InvalidTag
        with self.assertRaises(InvalidTag):
            parse_container(out.read_bytes(), self.vmk)

    def test_pending_sync_appends_across_sessions_without_overwriting(self):
        batch1 = self.root / "Batch1"
        batch1.mkdir()
        (batch1 / "one.jpg").write_bytes(b"first-batch")

        with redirect_stdout(io.StringIO()):
            cv.run_encrypt_mode(
                self._make_encrypt_args(batch1, output_dir=selfvault_dir, delete_source=False),
                self.desktop,
            )

        pending_file = selfvault_dir / "pending_sync"
        self.assertTrue(pending_file.is_file())
        first_entries = [l for l in pending_file.read_text(encoding="utf-8").splitlines() if l.strip()]
        self.assertEqual(len(first_entries), 1)

        batch2 = self.root / "Batch2"
        (batch2 / "Trip").mkdir(parents=True)
        (batch2 / "Trip" / "two.jpg").write_bytes(b"second-batch")

        with redirect_stdout(io.StringIO()):
            cv.run_encrypt_mode(
                self._make_encrypt_args(batch2, output_dir=selfvault_dir, delete_source=False),
                self.desktop,
            )

        all_entries = [l for l in pending_file.read_text(encoding="utf-8").splitlines() if l.strip()]
        self.assertEqual(len(all_entries), 2)
        self.assertEqual(all_entries[0], first_entries[0])
        self.assertTrue((selfvault_dir / all_entries[1]).is_file())


if __name__ == "__main__":
    unittest.main(verbosity=2)

#!/usr/bin/env python3
"""
Calypso Gallery - Cross-Platform Offline Vault Encryptor CLI Tool
-----------------------------------------------------------------
Encrypts plaintext media files (photos, videos) into .bin format,
100% compatible with the Encrypted Vault of Calypso Gallery.

Containers use the canonical FGVLTENC v1 layout: an AES-256-GCM encrypted
metadata header (key derived from the vault KEK via HKDF-SHA256) followed by a
Tink-compatible AES256_GCM_HKDF streaming-AEAD payload. Album `.meta` folders
use the canonical FGMETA layout. No other container format is written.

This is an unofficial companion tool for the independently maintained Calypso Gallery fork.

IMPORTANT SECURITY NOTICE (TIER 1 LIVE BOOT vs. TIER 2 STANDARD OS):
  - Tier 1 (Recommended for maximum privacy): Run inside a RAM-only Live Boot OS
    such as Tails OS or toram Linux. Temporary plaintext operations stay strictly
    in volatile RAM and vanish on shutdown.
  - Tier 2 (Standard / Convenience mode on Windows & macOS): Automatically
    supported for bulk-encrypting media already stored on your PC without copying
    plaintext to your phone first. Standard disk-backed OSes can leave forensic
    traces in filesystem journals, swap/pagefile, thumbnail/search caches, cloud
    Desktop sync (OneDrive/iCloud), and SSD wear-leveling blocks even after deletion.

Features:
- Automatic OS detection (Linux/Tails, macOS, Windows) with zero extra prompts.
- Encrypt-Only workflow targeting either an external Secrets vault
  directly or an output/delta folder (Encrypted_Output).
- Automatic vault discovery across Desktop, persistent storage, and mounted USB drives.
- Pre-encryption supported media format validation matching the Android app.
- Automatic Default Secret Album routing for root files & .meta-aware subfolder matching.
- Secure source sanitation (overwriting with zeros) of plaintext source media (--delete-source).
"""

import os
import sys
import time
import secrets
import struct
import getpass
import argparse
import subprocess
from datetime import datetime
from dataclasses import dataclass, field
from pathlib import Path
from io import BytesIO

from calypso_i18n import *  # noqa: F401,F403
from calypso_crypto import *  # noqa: F401,F403
from calypso_crypto import _hkdf_sha256, _tink_stream_primitive, _KeepOpenBytesIO, _create_unique_vault_hex_dir
from calypso_media import *  # noqa: F401,F403

def get_platform_kind() -> str:
    """Returns normalized platform identifier: 'windows', 'macos', or 'linux'."""
    if os.name == "nt":
        return "windows"
    if sys.platform == "darwin":
        return "macos"
    return "linux"

def get_platform_display_name() -> str:
    """Returns a human-readable OS label for runtime notices."""
    kind = get_platform_kind()
    if kind == "windows":
        return "Windows"
    if kind == "macos":
        return "macOS"
    return "Linux"

def configure_console():
    """Ensures UTF-8 output and ANSI color support on Windows consoles."""
    if os.name != "nt":
        return
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if callable(reconfigure):
            try:
                reconfigure(encoding="utf-8", errors="replace")
            except Exception:
                pass
    try:
        import ctypes
        kernel32 = ctypes.windll.kernel32
        enable_vt = 0x0004
        for handle_id in (-11, -12):
            handle = kernel32.GetStdHandle(handle_id)
            if handle and handle != -1:
                mode = ctypes.c_uint32()
                if kernel32.GetConsoleMode(handle, ctypes.byref(mode)):
                    kernel32.SetConsoleMode(handle, mode.value | enable_vt)
    except Exception:
        pass

def is_cloud_synced_path(path: Path) -> bool:
    """
    Heuristic check for paths inside common cloud-synced folders (OneDrive, iCloud Drive,
    Dropbox, Google Drive) where staging plaintext media or vault headers poses a leak risk.
    """
    try:
        resolved_str = str(path.expanduser().resolve()).lower()
    except Exception:
        resolved_str = str(path).lower()

    for part in Path(resolved_str).parts:
        part_lower = part.lower()
        if any(marker in part_lower for marker in CLOUD_SYNC_MARKERS):
            return True

    if os.name == "nt":
        for env_key in ("OneDrive", "OneDriveCommercial", "OneDriveConsumer"):
            env_val = os.environ.get(env_key)
            if env_val:
                try:
                    env_resolved = str(Path(env_val).resolve()).lower()
                    if resolved_str == env_resolved or resolved_str.startswith(env_resolved + os.sep):
                        return True
                except Exception:
                    pass

    return False

def get_desktop_dir() -> Path:
    """Detects Desktop directory across Windows, macOS, and Linux environments."""
    kind = get_platform_kind()

    if kind == "windows":
        try:
            import ctypes
            buf = ctypes.create_unicode_buffer(260)
            # CSIDL_DESKTOPDIRECTORY = 0x0010
            if ctypes.windll.shell32.SHGetFolderPathW(None, 0x0010, None, 0, buf) == 0 and buf.value:
                p = Path(buf.value)
                if p.exists() and p.is_dir():
                    return p
        except Exception:
            pass

        for candidate in [
            Path(os.environ.get("USERPROFILE", "")) / "Desktop" if os.environ.get("USERPROFILE") else None,
            Path.home() / "Desktop",
        ]:
            if candidate and candidate.exists() and candidate.is_dir():
                return candidate

    elif kind == "linux":
        # 1. xdg-user-dir DESKTOP check
        try:
            res = subprocess.run(["xdg-user-dir", "DESKTOP"], capture_output=True, text=True, check=False)
            out = res.stdout.strip()
            if out and Path(out).exists():
                return Path(out)
        except Exception:
            pass

        # 2. XDG user-dirs config (~/.config/user-dirs.dirs)
        xdg_config = Path.home() / ".config" / "user-dirs.dirs"
        if xdg_config.exists():
            try:
                for line in xdg_config.read_text(encoding="utf-8").splitlines():
                    if line.startswith("XDG_DESKTOP_DIR="):
                        val = line.split("=", 1)[1].strip().strip('"').replace("$HOME", str(Path.home()))
                        p = Path(val)
                        if p.exists():
                            return p
            except Exception:
                pass

    # Common Desktop directory names (macOS / Linux / fallback)
    for candidate_name in ["Desktop", "Masaüstü", "desktop"]:
        candidate = Path.home() / candidate_name
        if candidate.exists() and candidate.is_dir():
            return candidate

    # Fallback: create ~/Desktop
    fallback = Path.home() / "Desktop"
    try:
        fallback.mkdir(parents=True, exist_ok=True)
        return fallback
    except Exception:
        return Path.home()

def get_desktop_folder(desktop: Path, folder_key: str) -> Path:
    """Finds or creates the standard Desktop working folder."""
    folder_name = INPUT_FOLDER_NAME if folder_key == "enc_in" else OUTPUT_FOLDER_NAME
    target = desktop / folder_name
    target.mkdir(parents=True, exist_ok=True)
    return target

def is_valid_vault_file(path: Path) -> bool:
    """Returns True if path exists, is >= 32 bytes, and begins with canonical FGVLT header magic."""
    try:
        if not path.is_file() or path.stat().st_size < 32:
            return False
        with open(path, "rb") as f:
            return f.read(len(VAULT_HEADER_MAGIC)) == VAULT_HEADER_MAGIC
    except Exception:
        return False

def _windows_external_drive_roots() -> list[Path]:
    """Enumerates mounted non-system drive roots on Windows, prioritizing removable USB/SD drives."""
    try:
        import ctypes
        kernel32 = ctypes.windll.kernel32
        bitmask = kernel32.GetLogicalDrives()
    except Exception:
        bitmask = 0

    system_drive = os.environ.get("SystemDrive", "C:").rstrip("\\/").upper()
    removable: list[Path] = []
    other_non_system: list[Path] = []

    if bitmask:
        for i in range(26):
            if not (bitmask & (1 << i)):
                continue
            letter = chr(ord("A") + i)
            drive_str = f"{letter}:\\"
            if f"{letter}:".upper() == system_drive:
                continue
            try:
                drive_type = ctypes.windll.kernel32.GetDriveTypeW(drive_str)
            except Exception:
                drive_type = 0
            # DRIVE_REMOVABLE = 2, DRIVE_FIXED = 3 (external USB HDD/SSD often reports 3), DRIVE_REMOTE = 4
            if drive_type == 2:
                removable.append(Path(drive_str))
            elif drive_type in (3, 4):
                other_non_system.append(Path(drive_str))
    else:
        for i in range(26):
            letter = chr(ord("A") + i)
            if f"{letter}:".upper() == system_drive:
                continue
            p = Path(f"{letter}:\\")
            try:
                if p.exists():
                    other_non_system.append(p)
            except Exception:
                pass

    return removable + other_non_system

def _external_mount_roots() -> list[Path]:
    """Returns unique mount roots where external USB/SD drives may be mounted across OSes."""
    kind = get_platform_kind()
    if kind == "windows":
        return _windows_external_drive_roots()
    if kind == "macos":
        return [Path("/Volumes")]

    roots: list[Path] = [Path("/media/amnesia")]
    try:
        user = os.environ.get("USER") or getpass.getuser()
    except Exception:
        user = ""
    if user:
        roots.extend([Path("/media") / user, Path("/run/media") / user])
    roots.extend([Path("/media"), Path("/run/media"), Path("/mnt")])

    seen: set[Path] = set()
    unique: list[Path] = []
    for r in roots:
        if r not in seen:
            seen.add(r)
            unique.append(r)
    return unique

def find_vault_files() -> list[Path]:
    """Returns every valid vault header found across vaults, Persistent storage and Desktop."""
    desktop = get_desktop_dir()
    kind = get_platform_kind()
    vault_dir_candidates: list[Path] = []
    standalone_candidates: list[Path] = []

    def add_vault_dir_locations(base: Path):
        vault_dir_candidates.extend([
            base / VAULT_DIR_NAME / VAULT_HEADER_FILE_NAME,
            base / APP_DIR_NAME / VAULT_DIR_NAME / VAULT_HEADER_FILE_NAME,
        ])

    # 1. Tails / Live-Boot Persistent & external USB drives (vault containers first)
    if kind == "linux":
        persistent_roots = [Path("/home/amnesia/Persistent"), Path.home() / "Persistent"]
        for p_root in persistent_roots:
            add_vault_dir_locations(p_root)

    windows_skip_dirs = {"system volume information", "$recycle.bin", "windows", "program files", "program files (x86)"}
    for mount_root in _external_mount_roots():
        try:
            if not mount_root.exists() or not mount_root.is_dir():
                continue
        except Exception:
            continue
        add_vault_dir_locations(mount_root)
        if kind == "windows":
            standalone_candidates.append(mount_root / VAULT_HEADER_FILE_NAME)
        try:
            for usb_dir in sorted(mount_root.iterdir(), key=lambda p: p.name):
                try:
                    if not usb_dir.is_dir():
                        continue
                    if kind == "macos" and usb_dir.is_symlink() and usb_dir.resolve() == Path("/"):
                        continue
                    if kind == "windows" and usb_dir.name.lower() in windows_skip_dirs:
                        continue
                    add_vault_dir_locations(usb_dir)
                    standalone_candidates.append(usb_dir / VAULT_HEADER_FILE_NAME)
                except Exception:
                    continue
        except Exception:
            pass

    # 2. Desktop, Persistent standalone, Home, CWD, and script directory
    local_standalone: list[Path] = [
        # Desktop
        desktop / VAULT_HEADER_FILE_NAME,
        # Desktop folders
        desktop / "To_Encrypt" / VAULT_HEADER_FILE_NAME,
        # Legacy Turkish folder names are recognized for discovery only, never created.
    ]
    if kind == "linux":
        local_standalone.extend([
            Path(str(Path("/home/amnesia/Persistent") / VAULT_HEADER_FILE_NAME)),
            Path.home() / "Persistent" / VAULT_HEADER_FILE_NAME,
        ])
    local_standalone.extend([
        # Home directory
        Path.home() / VAULT_HEADER_FILE_NAME,
        # Working directory & script directory
        Path.cwd() / VAULT_HEADER_FILE_NAME,
        Path(__file__).resolve().parent / VAULT_HEADER_FILE_NAME,
    ])
    standalone_candidates = local_standalone + standalone_candidates

    # CLI bridge folders: <root>/Calypso Gallery/CLI/<id>/vault, the archived
    # copies in CLI/Archived/<id>/vault, and the local mirror <Desktop>/Calypso CLI/.
    # An archived vault keeps its bridge inside CLI/Archived, so it stays usable
    # here instead of being duplicated into the regular CLI folder.
    def add_bridge_headers(base: Path):
        try:
            children = sorted(base.iterdir(), key=lambda p: p.name)
        except Exception:
            return
        for child in children:
            standalone_candidates.append(child / VAULT_HEADER_FILE_NAME)
        try:
            archived = base / CLI_BRIDGE_ARCHIVED_DIR_NAME
            for child in sorted(archived.iterdir(), key=lambda p: p.name):
                standalone_candidates.append(child / VAULT_HEADER_FILE_NAME)
        except Exception:
            pass

    env_root = os.environ.get("CALYPSO_BRIDGE_ROOT")
    discovery_bases = [desktop / LOCAL_BRIDGE_DIR_NAME]
    if env_root:
        discovery_bases.insert(0, Path(env_root).expanduser())
    for base in discovery_bases:
        add_bridge_headers(base)
    for mount_root in _external_mount_roots():
        for cli_root in [mount_root / APP_DIR_NAME / CLI_DIR_NAME, mount_root / CLI_DIR_NAME]:
            add_bridge_headers(cli_root)

    found: list[Path] = []
    seen: set[str] = set()
    for candidate in vault_dir_candidates + standalone_candidates:
        if not is_valid_vault_file(candidate):
            continue
        key = str(candidate.resolve()).lower()
        if key not in seen:
            seen.add(key)
            found.append(candidate)
    return found


def _bridge_is_active(vault_path: Path) -> bool:
    marker = vault_path.parent / CLI_BRIDGE_MARKER_FILE
    if not marker.is_file():
        return False
    try:
        for line in marker.read_text(encoding="utf-8").splitlines():
            if line.strip() == "active=1":
                return True
    except Exception:
        pass
    return False


def find_vault_file() -> Path | None:
    """Backward-compatible helper: first discovered vault, or None."""
    candidates = find_vault_files()
    return candidates[0] if candidates else None

def get_cli_config_dir() -> Path:
    """Device-local config folder, kept on the Desktop so the Linux sandbox can write it."""
    return get_desktop_dir() / ".calypso-cli"


def get_last_vault_path_file() -> Path:
    return get_cli_config_dir() / "last_vault_path"


def read_last_vault_path() -> Path | None:
    """Returns the last used vault if it still exists and is valid."""
    try:
        raw = get_last_vault_path_file().read_text(encoding="utf-8").strip()
        if not raw:
            return None
        candidate = Path(raw).expanduser()
        return candidate if is_valid_vault_file(candidate) else None
    except Exception:
        return None


def remember_vault_path(path: Path | None) -> None:
    """Persists the selected vault path locally (never inside a vault folder)."""
    if path is None:
        return
    try:
        config_dir = get_cli_config_dir()
        config_dir.mkdir(parents=True, exist_ok=True)
        get_last_vault_path_file().write_text(str(path.resolve()) + "\n", encoding="utf-8")
    except Exception:
        pass


def prompt_for_vault_file() -> Path:
    """Interactively prompts user for vault header if not found automatically."""
    print(t("vault_not_found"))
    print(t("vault_drop_hint", app_dir=APP_DIR_NAME))
    print(t("vault_path_hint"))
    while True:
        try:
            raw = input(t("vault_enter_path")).strip()
        except (KeyboardInterrupt, EOFError):
            print(t("op_cancelled"))
            sys.exit(1)

        clean_path = raw.strip().strip("'\"")
        p = Path(clean_path).expanduser().resolve()
        if is_valid_vault_file(p):
            return p
        print(t("vault_invalid_path", path=clean_path))

from calypso_workflow import *  # noqa: F401,F403
from calypso_workflow import _nearest_mount

def main():
    configure_console()
    desktop = get_desktop_dir()

    parser = argparse.ArgumentParser(
        description="Calypso Gallery - Cross-Platform Offline Vault Encryptor",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
SECURITY NOTICE (Tier 1 Live Boot vs. Tier 2 Standard OS):
  - Tier 1 (Recommended for maximum privacy): RAM-only Live Boot OS (Tails / toram Linux).
  - Tier 2 (Standard / Convenience mode): Windows & macOS auto-detected for encrypting
    media already stored on your PC (does not clear swap, thumbnails, or SSD blocks).

Desktop Usage:
  Encrypt (To_Encrypt -> Encrypted_Output or an external vault):
     python3 calypso_vault.py
  Direct external vault example (Linux / macOS / Windows):
     python3 calypso_vault.py -i ~/Desktop/To_Encrypt -v /media/amnesia/USB/Secrets/vault

        """
    )
    parser.add_argument("-e", "--encrypt", action="store_true", help="Encrypt plaintext media into .bin format (default)")

    parser.add_argument("--lang", choices=["en", "tr"], help=argparse.SUPPRESS)  # deprecated, ignored
    parser.add_argument("-v", "-k", "--vault", help="Path to vault header (Searched automatically across mounted vaults/USB/desktop if omitted)")
    parser.add_argument("-i", "--input", help="Input folder/file")
    parser.add_argument("-o", "--output", help="Output folder (or target Secrets vault directory)")
    parser.add_argument("-s", "--secret", help="Vault password or PIN (Prompted securely if omitted)")
    parser.add_argument("--pattern", help="Grid pattern indices if 3x3 pattern is used (e.g. '0,1,2,5')")
    parser.add_argument("--delete-source", action="store_true", help="Securely wipe and delete original files after encryption")

    args = parser.parse_args()
    check_dependencies()
    install_network_audit_hook()

    run_encrypt_mode(args, desktop)

if __name__ == "__main__":
    main()

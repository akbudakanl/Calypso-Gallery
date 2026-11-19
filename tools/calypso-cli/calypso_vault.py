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
  directly or an output/delta folder (Encrypted_Output / Sifreli_Cikti).
- Automatic .vault discovery across Desktop, persistent storage, and mounted USB drives.
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
import shutil
from datetime import datetime
from dataclasses import dataclass, field
from pathlib import Path
from io import BytesIO

# On-disk names used by the Android app (Kotlin Constants.kt).
APP_DIR_NAME = "Calypso Gallery"
VAULT_DIR_NAME = "Secrets"
VAULT_DIR_NAMES = (VAULT_DIR_NAME,)

HAS_CRYPTO = True
HAS_TINK = True
try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    from cryptography.hazmat.primitives.kdf.hkdf import HKDF
    from cryptography.hazmat.primitives import hashes
    from argon2.low_level import hash_secret_raw, Type
    import tink
    from tink import streaming_aead
    from tink.proto import aes_gcm_hkdf_streaming_pb2 as _hkdf_pb
    from tink.proto import tink_pb2 as _tink_pb
    from tink._secret_key_access import SecretKeyAccess
except ImportError:
    HAS_CRYPTO = False
    HAS_TINK = False

try:
    from PIL import Image
    HAS_PIL = True
except ImportError:
    HAS_PIL = False

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

def check_dependencies():
    if not HAS_CRYPTO or not HAS_TINK:
        print("[ERROR] Required Python packages are missing or incompatible!", file=sys.stderr)
        print("Tink requires Python 3.10-3.13 (not 3.14+). Use a matching interpreter.", file=sys.stderr)
        kind = get_platform_kind()
        if kind == "linux":
            print("On Debian-based Linux (e.g., Debian, Ubuntu, Tails), run in terminal:", file=sys.stderr)
            print("  sudo apt update && sudo apt install -y python3-cryptography python3-argon2 python3-pil python3-tink", file=sys.stderr)
        print("Or install via pip (recommended inside a venv):", file=sys.stderr)
        print("  pip install -r requirements.txt", file=sys.stderr)
        sys.exit(1)

def install_network_audit_hook(allow_adb: bool):
    """
    Defense-in-depth Python-level guard: blocks socket connections and external
    network calls inside this Python process when --adb is not enabled.
    """
    if allow_adb or not hasattr(sys, "addaudithook"):
        return

    def _audit_hook(event, _args):
        if event in ("socket.connect", "socket.bind", "socket.sendto", "urllib.Request"):
            raise PermissionError(f"Network operation blocked by offline security policy: {event}")

    sys.addaudithook(_audit_hook)

# ==================== PROTOCOL CONSTANTS ====================
# Canonical FGVLTENC v1 container (Kotlin: VaultContainer / VaultContainerWriter).
CONTAINER_MAGIC = b"FGVLTENC"
CONTAINER_VERSION = 1
ACTIVE_KEY_ID = 1
VAULT_HEADER_MAGIC = b"FGVLT"          # 5 ASCII bytes (Kotlin VaultContainer.VAULT_HEADER_MAGIC)
FOLDER_META_MAGIC = b"FGMETA"          # 6 ASCII bytes (Kotlin VaultContainer.FOLDER_META_MAGIC)
VAULT_STR_PREFIX = b"FVSTR:"           # Kotlin VaultContainer.VAULT_STR_PREFIX

# HKDF-SHA256 domain separation (RFC 5869 / Tink Hkdf.computeHkdf). DO NOT CHANGE.
DOMAIN_HEADER_KEY = "CalypsoGallery:Header:AES256GCM"
DOMAIN_METADATA_KEY = "CalypsoGallery:Metadata:AESGCM"

# Tink AES256_GCM_HKDF streaming parameters (frozen for Android interop).
SEGMENT_SIZE = 262_144                              # Kotlin TinkStreamSpec.CIPHERTEXT_SEGMENT_SIZE
GCM_TAG_SIZE = 16
STREAM_HEADER_LENGTH = 40                           # u8(40) | salt(32) | noncePrefix(7)
FIRST_CHUNK_PLAINTEXT_MAX = SEGMENT_SIZE - STREAM_HEADER_LENGTH - GCM_TAG_SIZE  # 262088
SEGMENT_PLAINTEXT_MAX = SEGMENT_SIZE - GCM_TAG_SIZE                             # 262128
FIXED_PREFIX_LENGTH = 8 + 1 + 2 + 16 + 12 + 4        # 43
# Tink treats an empty HKDF salt as HashLen (32) zero bytes; pass this explicitly so
# every cryptography version derives the same header/metadata keys as the Android app.
HKDF_EMPTY_SALT = b"\x00" * 32

VAULT_DEFAULT_ALBUM_NAME = "__VAULT_DEFAULT_ALBUM__"
DEFAULT_SECRET_ALBUM_NAME = "Default Secret Album"
VAULT_RECYCLE_BIN_NAME = "$$VAULT_RECYCLE_BIN$$"
DEFAULT_ALBUM_RAW_NAMES = {
    VAULT_DEFAULT_ALBUM_NAME,
    DEFAULT_SECRET_ALBUM_NAME,
    "Varsayılan Gizli Albüm",
    "Default Hidden Album",
}

TYPE_IMAGES = 1
TYPE_VIDEOS = 2
TYPE_GIFS = 4
TYPE_RAWS = 8
TYPE_SVGS = 16

PHOTO_EXTS = {
    ".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".apng",
    ".avif", ".bmp", ".wbmp", ".ico", ".jxl", ".jfif", ".pjpeg", ".pjp",
}
GIF_EXTS = {".gif"}
SVG_EXTS = {".svg"}
RAW_EXTS = {
    ".dng", ".cr2", ".cr3", ".nef", ".nrw", ".arw", ".srf", ".sr2",
    ".orf", ".rw2", ".pef", ".raf", ".srw", ".x3f", ".erf", ".mef",
    ".mos", ".mrw", ".dcr", ".kdc", ".3fr", ".fff", ".iiq", ".rwl",
}
VIDEO_EXTS = {
    ".mp4", ".mkv", ".webm", ".avi", ".3gp", ".3gpp", ".3g2", ".mov",
    ".m4v", ".ts", ".mts", ".m2ts", ".qt", ".flv", ".f4v", ".wmv",
    ".asf", ".mpg", ".mpeg", ".mpe", ".vob", ".ogv", ".rm", ".rmvb", ".divx",
}
SUPPORTED_MEDIA_EXTENSIONS = PHOTO_EXTS | GIF_EXTS | SVG_EXTS | RAW_EXTS | VIDEO_EXTS

# ==================== LOCALIZATION (EN / TR) ====================

FOLDER_NAMES = {
    "en": {
        "enc_in": "To_Encrypt",
        "enc_out": "Encrypted_Output",
    },
    "tr": {
        "enc_in": "Sifrelenecek",
        "enc_out": "Sifreli_Cikti",
    },
}

MESSAGES = {
    "en": {
        "op_cancelled": "\nOperation cancelled.",
        "vault_not_found": "\033[1;33m[?] .vault file was not found automatically.\033[0m",
        "vault_drop_hint": "    You can drop your .vault file (from {app_dir} > Secrets > .vault)",
        "vault_path_hint": "    onto your Desktop or enter its path below.",
        "vault_enter_path": "\n\033[1;36m-> Enter path to .vault file (or drag & drop here): \033[0m",
        "vault_copy_prompt": "\033[1;32m[i] Copy .vault to Desktop for automatic detection next time? [Y/n]: \033[0m",
        "vault_copied": "    [✓] Copied to {path}.",
        "vault_invalid_path": "\033[1;31m[-] Error: Valid .vault file not found at: {path}\033[0m",
        "vault_spec_not_found": "\033[1;31m[ERROR]\033[0m Specified .vault file not found: {path}",
        "vault_auto_detected": "\033[1;32m[✓] .vault file auto-detected:\033[0m {path}",
        "enter_secret": "\033[1;33m[?] Enter Vault Password or PIN: \033[0m",
        "secret_empty": "\033[1;31m[ERROR]\033[0m Password cannot be empty!",
        "unwrapping_vault": "\033[1;34m[*] Unwrapping .vault file (Argon2id + AES-256-GCM)...\033[0m",
        "vault_unlocked": "\033[1;32m[✓] Vault unlocked successfully! Master Key loaded into memory.\033[0m\n",
        "vault_unlock_err": "\033[1;31m[ERROR]\033[0m Could not unlock vault: {err}",
        "vault_retry": "\033[1;33m[i] Please try again (Ctrl+C to cancel).\033[0m\n",
        "enc_title": "\033[1;36m      Calypso Gallery - Offline Vault Encryptor\033[0m",
        "non_ram_os_notice": "\033[1;33m[i] Standard OS Mode ({os_name}):\033[0m Running on a disk-backed OS (not RAM-only Tails/Live Boot). Swap, thumbnails, and filesystem logs are not cleared; best suited for encrypting media already on this PC.",
        "cloud_sync_warning": "\033[1;31m[WARNING]\033[0m Cloud-synced folder detected ({path})! Plaintext media or .vault here may upload to OneDrive/iCloud/Dropbox. Prefer a non-synced local folder or USB drive (-i / -o).",
        "wipe_non_ram_disclaimer": "\033[1;33m[i] Note:\033[0m On {os_name} SSDs, overwriting removes the file entry but cannot erase SSD wear-leveling blocks, NTFS/APFS journals, shadow copies/snapshots, or OS thumbnail/search caches.",
        "folders_ready": "\033[1;32m[✓] Desktop folders are ready:\033[0m",
        "input_folder": "    📥 Input Folder:   \033[1;33m{path}\033[0m",
        "output_folder": "    📤 Output Folder:  \033[1;33m{path}\033[0m",
        "enc_empty": "\n\033[1;33m[!] No photos or videos found in the input folder yet.\033[0m",
        "enc_empty_hint1": "    Place the media you want to encrypt into the input folder",
        "enc_empty_hint2": "    and run this tool again.",
        "enc_queued": "\033[1;35m[*] Total {count} file(s) ({mb:.2f} MB) queued for encryption...\033[0m",
        "enc_success": "\n\033[1;32m[✓] Success! {count} file(s) encrypted and saved:\033[0m",
        "out_dir_line": "    -> Output Directory: \033[1;36m{path}\033[0m",
        "enc_tip1": "\033[1;37m[i] Tip: Transfer the contents of the output folder into your phone's\033[0m",
        "enc_tip2": "\033[1;37m    '{app_dir}/Secrets/' directory.\033[0m",
        "wipe_prompt": "\n\033[1;33m[?] Securely wipe and delete plaintext files from input folder? [y/N]: \033[0m",
        "wiping_start": "\n\033[1;33m[*] Securely overwriting with zeros and deleting source files...\033[0m",
        "wipe_err": "    [-] Could not delete: {name} ({err})",
        "wiping_done": "\033[1;32m[✓] Plaintext files securely wiped.\033[0m",
        "adb_pushing": "\n\033[1;34m[*] Pushing to phone via ADB: {dest}\033[0m",
        "adb_no_device": "\033[1;31m[-] Error: No ADB device connected.\033[0m",
        "adb_transferring": "    -> Transferring: {name}",
        "adb_done": "\033[1;32m[✓] All files pushed to phone successfully!\033[0m",
        "files_unit": "files",
        "skip_unsupported_fmt": "\033[1;33m[!] Skipping unsupported format (not a supported gallery media file):\033[0m {name}",
        "enc_skipped_summary": "\033[1;33m[i] Skipped {count} unsupported file(s).\033[0m",
        "enc_nested_flatten_note": "\033[1;34m[i] Nested subfolders under '{album}' will be routed into top-level vault album '{album}'.\033[0m",
        "enc_input_not_found": "\033[1;31m[ERROR]\033[0m Input path does not exist: {path}",
        "enc_auto_target_vault": "\033[1;32m[✓] Encryption destination:\033[0m {path}",
        "enc_tip_direct": "\033[1;37m[i] Encrypted directly inside the Vault. Existing .meta albums were reused and new albums created.\033[0m",
    },
    "tr": {
        "op_cancelled": "\nİşlem iptal edildi.",
        "vault_not_found": "\033[1;33m[?] .vault dosyası otomatik olarak bulunamadı.\033[0m",
        "vault_drop_hint": "    .vault dosyanızı ({app_dir} > Secrets > .vault)",
        "vault_path_hint": "    Masaüstüne bırakabilir veya dosya yolunu aşağıya girebilirsiniz.",
        "vault_enter_path": "\n\033[1;36m-> .vault dosya yolunu girin (veya sürükleyip bırakın): \033[0m",
        "vault_copy_prompt": "\033[1;32m[i] Bir sonraki sefer otomatik algılama için .vault Masaüstüne kopyalansın mı? [E/h]: \033[0m",
        "vault_copied": "    [✓] Kopyalandı: {path}.",
        "vault_invalid_path": "\033[1;31m[-] Hata: Geçerli bir .vault dosyası bulunamadı: {path}\033[0m",
        "vault_spec_not_found": "\033[1;31m[HATA]\033[0m Belirtilen .vault dosyası bulunamadı: {path}",
        "vault_auto_detected": "\033[1;32m[✓] .vault dosyası otomatik algılandı:\033[0m {path}",
        "enter_secret": "\033[1;33m[?] Kasa Şifresini veya PIN'ini Girin: \033[0m",
        "secret_empty": "\033[1;31m[HATA]\033[0m Şifre boş bırakılamaz!",
        "unwrapping_vault": "\033[1;34m[*] .vault dosyası açılıyor (Argon2id + AES-256-GCM)...\033[0m",
        "vault_unlocked": "\033[1;32m[✓] Kasa başarıyla açıldı! Ana Anahtar (Master Key) belleğe alındı.\033[0m\n",
        "vault_unlock_err": "\033[1;31m[HATA]\033[0m Kasa açılamadı: {err}",
        "vault_retry": "\033[1;33m[i] Lütfen tekrar deneyin (İptal için Ctrl+C).\033[0m\n",
        "enc_title": "\033[1;36m      Calypso Gallery - Çevrimdışı Şifreleme Aracı\033[0m",
        "non_ram_os_notice": "\033[1;33m[i] Standart İşletim Sistemi Modu ({os_name}):\033[0m RAM tabanlı Tails/Live Boot yerine disk tabanlı sistemde çalışıyor. Takas alanı, küçük resim önbelleği ve dosya sistemi izleri silinmez; bilgisayarda zaten bulunan medyaları şifrelemek için uygundur.",
        "cloud_sync_warning": "\033[1;31m[UYARI]\033[0m Bulutla eşitlenen klasör algılandı ({path})! Buradaki şifresiz medyalar veya .vault dosyası OneDrive/iCloud/Dropbox'a yüklenebilir. Bulut dışı yerel bir klasör veya USB (-i / -o) kullanmanız önerilir.",
        "wipe_non_ram_disclaimer": "\033[1;33m[i] Not:\033[0m {os_name} SSD disklerde üzerine sıfır yazmak dosyayı siler; ancak SSD aşınma dengeleme bloklarını, NTFS/APFS günlüklerini, gölge kopyaları/anlık görüntüleri veya sistem önbelleklerini temizlemez.",
        "folders_ready": "\033[1;32m[✓] Masaüstü klasörleri hazır:\033[0m",
        "input_folder": "    📥 Girdi Klasörü:   \033[1;33m{path}\033[0m",
        "output_folder": "    📤 Çıktı Klasörü:   \033[1;33m{path}\033[0m",
        "enc_empty": "\n\033[1;33m[!] Girdi klasöründe henüz fotoğraf veya video bulunamadı.\033[0m",
        "enc_empty_hint1": "    Şifrelemek istediğiniz medyaları girdi klasörüne bırakıp",
        "enc_empty_hint2": "    bu aracı tekrar çalıştırın.",
        "enc_queued": "\033[1;35m[*] Toplam {count} dosya ({mb:.2f} MB) şifrelenmek üzere sıraya alındı...\033[0m",
        "enc_success": "\n\033[1;32m[✓] Başarılı! {count} dosya şifrelendi ve kaydedildi:\033[0m",
        "out_dir_line": "    -> Çıktı Klasörü: \033[1;36m{path}\033[0m",
        "enc_tip1": "\033[1;37m[i] İpucu: Çıktı klasörünün içeriğini telefonunuzdaki\033[0m",
        "enc_tip2": "\033[1;37m    '{app_dir}/Secrets/' dizinine aktarabilirsiniz.\033[0m",
        "wipe_prompt": "\n\033[1;33m[?] Girdi klasöründeki şifresiz kaynak dosyalar sıfırlanıp silinsin mi? [e/H]: \033[0m",
        "wiping_start": "\n\033[1;33m[*] Kaynak dosyalar üzerine sıfır yazılarak güvenli şekilde siliniyor...\033[0m",
        "wipe_err": "    [-] Silinemedi: {name} ({err})",
        "wiping_done": "\033[1;32m[✓] Şifresiz kaynak dosyalar güvenli şekilde temizlendi.\033[0m",
        "adb_pushing": "\n\033[1;34m[*] ADB ile telefona gönderiliyor: {dest}\033[0m",
        "adb_no_device": "\033[1;31m[-] Hata: Bağlı ADB cihazı bulunamadı.\033[0m",
        "adb_transferring": "    -> Gönderiliyor: {name}",
        "adb_done": "\033[1;32m[✓] Tüm dosyalar telefona başarıyla gönderildi!\033[0m",
        "files_unit": "dosya",
        "skip_unsupported_fmt": "\033[1;33m[!] Desteklenmeyen format atlanıyor (desteklenen bir galeri medyası değil):\033[0m {name}",
        "enc_skipped_summary": "\033[1;33m[i] Desteklenmeyen {count} dosya atlandı.\033[0m",
        "enc_nested_flatten_note": "\033[1;34m[i] '{album}' altındaki iç içe alt klasör dosyaları '{album}' kasa albümüne yönlendirilecek.\033[0m",
        "enc_input_not_found": "\033[1;31m[HATA]\033[0m Girdi yolu bulunamadı: {path}",
        "enc_auto_target_vault": "\033[1;32m[✓] Şifreleme hedefi:\033[0m {path}",
        "enc_tip_direct": "\033[1;37m[i] Doğrudan Kasa içine şifrelendi. Mevcut .meta albümleri yeniden kullanıldı ve yeni albümler oluşturuldu.\033[0m",
    },
}

def t(lang: str, key: str, **kwargs) -> str:
    msg = MESSAGES.get(lang, MESSAGES["en"]).get(key, MESSAGES["en"].get(key, key))
    return msg.format(**kwargs) if kwargs else msg

def choose_language(args) -> str:
    """Selects CLI language via --lang flag or interactive prompt."""
    if args.lang:
        return args.lang
    if not sys.stdin.isatty():
        return "en"

    print("\n" + "=" * 65)
    print("  Select Language / Dil Seçin:")
    print("    [1] English (Default / Varsayılan)")
    print("    [2] Türkçe")
    print("=" * 65)
    while True:
        try:
            choice = input("\033[1;36m[?] Choice / Seçim [1/2]: \033[0m").strip().lower()
        except (KeyboardInterrupt, EOFError):
            print("\nOperation cancelled / İşlem iptal edildi.")
            sys.exit(1)
        if choice in ("", "1", "en", "english"):
            return "en"
        if choice in ("2", "tr", "turkish", "türkçe", "turkce"):
            return "tr"
        print("\033[1;31m[-] Invalid selection / Geçersiz seçim (1 or 2).\033[0m")

# ==================== DESKTOP & DIRECTORY DISCOVERY ====================

CLOUD_SYNC_MARKERS = (
    "onedrive",
    "icloud",
    "mobile documents",
    "dropbox",
    "google drive",
    "googledrive",
    "my drive",
)

def is_cloud_synced_path(path: Path) -> bool:
    """
    Heuristic check for paths inside common cloud-synced folders (OneDrive, iCloud Drive,
    Dropbox, Google Drive) where staging plaintext media or .vault headers poses a leak risk.
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

def get_localized_desktop_dir(desktop: Path, lang: str, folder_key: str) -> Path:
    """Finds or creates the localized Desktop folder for the selected language."""
    folder_name = FOLDER_NAMES.get(lang, FOLDER_NAMES["en"])[folder_key]
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

def find_vault_file() -> Path | None:
    """Searches for .vault header file across mounted vaults, Persistent storage, and Desktop."""
    desktop = get_desktop_dir()
    kind = get_platform_kind()
    vault_dir_candidates: list[Path] = []
    standalone_candidates: list[Path] = []

    def add_vault_dir_locations(base: Path):
        vault_dir_candidates.extend([
            base / VAULT_DIR_NAME / ".vault",
            base / APP_DIR_NAME / VAULT_DIR_NAME / ".vault",
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
            standalone_candidates.append(mount_root / ".vault")
            standalone_candidates.append(mount_root / "phone.vault")
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
                    standalone_candidates.append(usb_dir / ".vault")
                    standalone_candidates.append(usb_dir / "phone.vault")
                except Exception:
                    continue
        except Exception:
            pass

    # 2. Desktop, Persistent standalone, Home, CWD, and script directory
    local_standalone: list[Path] = [
        # Desktop
        desktop / ".vault",
        desktop / "phone.vault",
        desktop / "vault.vault",
        desktop / "kasa.vault",
        # Desktop folders
        desktop / "To_Encrypt" / ".vault",
        desktop / "Encrypted_Output" / ".vault",
        desktop / "Sifrelenecek" / ".vault",
        desktop / "Sifreli_Cikti" / ".vault",
    ]
    if kind == "linux":
        local_standalone.extend([
            Path("/home/amnesia/Persistent/.vault"),
            Path("/home/amnesia/Persistent/phone.vault"),
            Path("/home/amnesia/Persistent/vault.vault"),
            Path.home() / "Persistent" / ".vault",
            Path.home() / "Persistent" / "phone.vault",
        ])
    local_standalone.extend([
        # Home directory
        Path.home() / ".vault",
        Path.home() / "phone.vault",
        # Working directory & script directory
        Path.cwd() / ".vault",
        Path.cwd() / "phone.vault",
        Path(__file__).resolve().parent / ".vault",
        Path(__file__).resolve().parent / "phone.vault",
    ])
    standalone_candidates = local_standalone + standalone_candidates

    for candidate in vault_dir_candidates + standalone_candidates:
        if is_valid_vault_file(candidate):
            return candidate

    return None

def prompt_for_vault_file(lang: str) -> Path:
    """Interactively prompts user for .vault file if not found automatically."""
    print(t(lang, "vault_not_found"))
    print(t(lang, "vault_drop_hint", app_dir=APP_DIR_NAME))
    print(t(lang, "vault_path_hint"))
    while True:
        try:
            raw = input(t(lang, "vault_enter_path")).strip()
        except (KeyboardInterrupt, EOFError):
            print(t(lang, "op_cancelled"))
            sys.exit(1)

        clean_path = raw.strip().strip("'\"")
        p = Path(clean_path).expanduser().resolve()
        if is_valid_vault_file(p):
            desktop = get_desktop_dir()
            desktop_vault = desktop / ".vault"
            in_vault_dir = p.parent.name in VAULT_DIR_NAMES or (p.parent / ".nomedia").is_file()
            if (
                not in_vault_dir
                and not desktop_vault.exists()
                and p != desktop_vault
                and not is_cloud_synced_path(desktop)
            ):
                try:
                    ans = input(t(lang, "vault_copy_prompt")).strip().lower()
                    if ans in ("", "y", "yes", "e", "evet"):
                        shutil.copy2(p, desktop_vault)
                        print(t(lang, "vault_copied", path=desktop_vault))
                        return desktop_vault
                except Exception:
                    pass
            return p
        print(t(lang, "vault_invalid_path", path=clean_path))

# ==================== CRYPTOGRAPHIC UTILITIES ====================

@dataclass
class VaultKeys:
    """The two keys held by a canonical .vault keyring."""
    vmk: bytes  # Vault Master Key - protects folder .meta files
    kek: bytes  # Key Encryption Key - protects file container headers


def _hkdf_sha256(ikm: bytes, salt: bytes, info: bytes) -> bytes:
    """RFC 5869 HKDF-SHA256, matching Tink Hkdf.computeHkdf('HmacSha256', ...)."""
    return HKDF(algorithm=hashes.SHA256(), length=32, salt=salt, info=info).derive(ikm)


def derive_header_key(kek: bytes, file_id: bytes) -> bytes:
    """Per-file AES-256-GCM header key: HKDF(KEK, empty salt, domain|fileId.hex())."""
    return _hkdf_sha256(
        kek,
        HKDF_EMPTY_SALT,
        (DOMAIN_HEADER_KEY + "|" + file_id.hex()).encode("utf-8"),
    )


def derive_metadata_key(kek: bytes, context: str) -> bytes:
    """Per-record key for FVSTR: vault strings (domain|purpose, purpose is also the AAD)."""
    return _hkdf_sha256(kek, HKDF_EMPTY_SALT, (DOMAIN_METADATA_KEY + "|" + context).encode("utf-8"))


def expected_ciphertext_size(plaintext_size: int) -> int:
    """Tink AesGcmHkdfStreaming.expectedCiphertextSize for the canonical segment size."""
    plaintext_segment_size = SEGMENT_SIZE - GCM_TAG_SIZE
    full_segments = (plaintext_size + STREAM_HEADER_LENGTH) // plaintext_segment_size
    last_segment = (plaintext_size + STREAM_HEADER_LENGTH) % plaintext_segment_size
    size = full_segments * SEGMENT_SIZE
    if last_segment > 0:
        size += last_segment + GCM_TAG_SIZE
    return size


def _tink_stream_primitive(dek: bytes):
    """Build the real Tink AES256_GCM_HKDF primitive with the frozen canonical parameters.

    The Android app constructs this primitive directly from a raw DEK. Tink Python's
    public API is keyset-based, so the equivalent RAW-prefix keyset is assembled from
    the same parameters (key_value=DEK, 256 KiB segments, SHA-256 HKDF, derived 32 B).
    The resulting bytes are byte-identical to the Kotlin writer's output.
    """
    streaming_aead.register()
    hash_number = (
        _hkdf_pb.AesGcmHkdfStreamingParams.DESCRIPTOR
        .fields_by_name["hkdf_hash_type"].enum_type.values_by_name["SHA256"].number
    )
    key = _hkdf_pb.AesGcmHkdfStreamingKey(
        version=0,
        key_value=dek,
        params=_hkdf_pb.AesGcmHkdfStreamingParams(
            ciphertext_segment_size=SEGMENT_SIZE,
            derived_key_size=32,
            hkdf_hash_type=hash_number,
        ),
    )
    kd = _tink_pb.KeyData(
        type_url="type.googleapis.com/google.crypto.tink.AesGcmHkdfStreamingKey",
        value=key.SerializeToString(),
        key_material_type=_tink_pb.KeyData.SYMMETRIC,
    )
    ks = _tink_pb.Keyset()
    entry = ks.key.add()
    entry.key_data.CopyFrom(kd)
    entry.status = _tink_pb.ENABLED
    entry.key_id = 1
    entry.output_prefix_type = _tink_pb.RAW
    ks.primary_key_id = 1
    handle = tink.proto_keyset_format.parse(ks.SerializeToString(), SecretKeyAccess())
    return handle.primitive(streaming_aead.StreamingAead)


class _KeepOpenBytesIO(BytesIO):
    """Tink closes the stream's destination on exit; keep the buffer readable."""
    def close(self):
        self.flush()


def unwrap_vault_keys(data: bytes, secret: str) -> VaultKeys:
    """
    Unwraps a canonical v1 .vault header.

    Layout: magic(5) | version(1) | kdf(1) | iterations(4) | memoryKb(4) |
    parallelism(4) | protectionType(1) | saltLen(4) | salt | ivPdk(12) |
    wrappedVmKLen(4) | wrappedVMK | ivVmk(12) | keyringLen(4) | wrappedKeyring.

    PDK = Argon2id(secret, salt, ...) unwraps the VMK; the VMK unwraps the keyring,
    which is packKeyringV4 = u16 keyId | 32-byte KEK.
    """
    if len(data) < 32:
        raise ValueError(".vault file is too small or invalid.")

    offset = 0

    def take(count: int) -> bytes:
        nonlocal offset
        if count < 0 or offset + count > len(data):
            raise ValueError("Corrupted .vault header: truncated field.")
        chunk = data[offset:offset + count]
        offset += count
        return chunk

    magic = take(len(VAULT_HEADER_MAGIC))
    if magic != VAULT_HEADER_MAGIC:
        raise ValueError(f"Invalid vault header! Expected: {VAULT_HEADER_MAGIC.decode()}, found: {magic!r}")

    take(1)  # version byte - canonical v1 keyring layout
    if take(1)[0] != 1:
        raise ValueError("Unsupported KDF algorithm (only Argon2id is supported).")

    iterations, memory_kb, parallelism = struct.unpack(">3i", take(12))
    if not (1 <= iterations <= 256 and 1024 <= memory_kb <= 1_048_576 and 1 <= parallelism <= 32):
        raise ValueError("Invalid Argon2id parameters in .vault header.")

    take(1)  # protection type (password / PIN / pattern)

    salt_len = struct.unpack(">i", take(4))[0]
    if salt_len <= 0 or salt_len > 256:
        raise ValueError("Invalid salt length.")
    salt = take(salt_len)

    iv_pdk = take(12)
    wrapped_vmk_len = struct.unpack(">i", take(4))[0]
    if wrapped_vmk_len < GCM_TAG_SIZE + 32 or wrapped_vmk_len > 65536:
        raise ValueError("Corrupted wrapped VMK data.")
    wrapped_vmk = take(wrapped_vmk_len)

    iv_vmk = take(12)
    keyring_len = struct.unpack(">i", take(4))[0]
    if keyring_len <= 0 or keyring_len > 4096:
        raise ValueError("Corrupted wrapped keyring data.")
    wrapped_keyring = take(keyring_len)

    pdk = hash_secret_raw(
        secret=secret.encode("utf-8"),
        salt=salt,
        time_cost=iterations,
        memory_cost=memory_kb,
        parallelism=parallelism,
        hash_len=32,
        type=Type.ID,
    )
    try:
        vmk = AESGCM(pdk).decrypt(iv_pdk, wrapped_vmk, b"")[:32]
    except Exception:
        raise ValueError("Incorrect password/PIN or corrupted file!")
    finally:
        pdk = bytearray(len(pdk))

    if len(vmk) < 32:
        raise ValueError("Wrapped VMK is too short.")

    try:
        keyring = AESGCM(vmk).decrypt(iv_vmk, wrapped_keyring, b"")
    except Exception:
        raise ValueError("Incorrect password/PIN or corrupted keyring!")
    if len(keyring) < 2 + 32:
        raise ValueError("Corrupted keyring: expected keyId + 32-byte KEK.")

    return VaultKeys(vmk=vmk[:32], kek=keyring[2:34])


def unlock_vault_header(vault_path: Path, secret: str) -> bytes:
    """Unwraps a .vault header and returns the KEK (the file-header key)."""
    return unlock_vault_header_bytes(vault_path.read_bytes(), secret)


def unlock_vault_header_bytes(data: bytes, secret: str) -> bytes:
    """Unwraps raw .vault header bytes and returns the KEK used for file headers."""
    return unwrap_vault_keys(data, secret).kek

def is_default_album_raw_name(raw_name: str | None) -> bool:
    """Returns True if raw_name identifies the built-in Default Secret Album."""
    return raw_name is not None and raw_name in DEFAULT_ALBUM_RAW_NAMES

def write_encrypted_folder_meta(
    folder: Path,
    original_name: str,
    master_key: bytes,
    vault_dir: Path | None = None,
):
    """
    Writes a canonical .meta (FGMETA) album file compatible with the Calypso Gallery
    Encrypted Vault. Creates .nomedia at vault_dir root, and inside folder ONLY when
    original_name is an explicitly hidden dot-folder (never for normal albums,
    default album, or recycle bin).
    """
    vault_root = vault_dir if vault_dir is not None else folder.parent
    if vault_root.is_dir():
        root_nomedia = vault_root / ".nomedia"
        if not root_nomedia.exists():
            try:
                root_nomedia.touch()
            except Exception:
                pass

    meta_file = folder / ".meta"
    iv = secrets.token_bytes(12)
    enc_name = AESGCM(master_key).encrypt(iv, original_name.encode("utf-8"), b"")

    with open(meta_file, "wb") as f:
        f.write(FOLDER_META_MAGIC)                # 6B ("FGMETA")
        f.write(iv)                               # 12B IV
        f.write(struct.pack(">i", len(enc_name))) # 4B Big-Endian int
        f.write(enc_name)                         # AES-256-GCM ciphertext + 16B tag

    if (
        original_name.startswith(".")
        and not is_default_album_raw_name(original_name)
        and original_name != VAULT_RECYCLE_BIN_NAME
    ):
        nomedia_file = folder / ".nomedia"
        if not nomedia_file.exists():
            try:
                nomedia_file.touch()
            except Exception:
                pass

def sanitize_name(name: str, fallback: str = "unnamed") -> str:
    """Strips directory components and unsafe characters to prevent path traversal."""
    clean = Path(name).name.replace("\x00", "").strip()
    if not clean or clean in (".", ".."):
        return fallback
    return clean

def read_raw_folder_meta_bytes(data: bytes, master_key: bytes) -> str | None:
    """
    Decrypts canonical raw .meta bytes (FGMETA) and returns the raw decrypted
    UTF-8 folder name without display mapping, or None if invalid.
    """
    magic_len = len(FOLDER_META_MAGIC)
    if len(data) < magic_len + 12 + 4 + 16 or data[:magic_len] != FOLDER_META_MAGIC:
        return None

    iv = data[magic_len:magic_len + 12]
    enc_len = struct.unpack(">i", data[magic_len + 12:magic_len + 16])[0]
    payload_start = magic_len + 16
    if enc_len <= 16 or payload_start + enc_len > len(data):
        return None

    enc_name = data[payload_start:payload_start + enc_len]
    try:
        plain = AESGCM(master_key).decrypt(iv, enc_name, b"")
        raw_name = plain.decode("utf-8")
        return raw_name if raw_name else None
    except Exception:
        return None

def read_raw_folder_meta(meta_file: Path, master_key: bytes) -> str | None:
    """
    Decrypts a .meta file and returns the raw folder name string,
    or None if the file is missing, unreadable, or cannot be decrypted.
    """
    try:
        return read_raw_folder_meta_bytes(meta_file.read_bytes(), master_key)
    except Exception:
        return None

def read_encrypted_folder_meta_bytes(data: bytes, fallback_name: str, master_key: bytes) -> str | None:
    """
    Decrypts raw .meta bytes (FGMETA) and returns the display album folder name,
    or None if the data is invalid.
    """
    raw = read_raw_folder_meta_bytes(data, master_key)
    if raw is None:
        return None
    if raw == VAULT_RECYCLE_BIN_NAME:
        return "Recycle Bin"
    if is_default_album_raw_name(raw):
        return DEFAULT_SECRET_ALBUM_NAME
    return sanitize_name(raw, fallback=fallback_name)

def read_encrypted_folder_meta(meta_file: Path, master_key: bytes) -> str | None:
    """
    Decrypts a .meta file (FGMETA) and returns the display album folder name,
    or None if the file is missing/invalid.
    """
    try:
        return read_encrypted_folder_meta_bytes(meta_file.read_bytes(), meta_file.parent.name, master_key)
    except Exception:
        return None

@dataclass
class VaultAlbumCache:
    """In-memory index of existing physical albums inside a vault root directory."""
    default_album_dir: Path | None = None
    exact_albums: dict[str, Path] = field(default_factory=dict)
    normalized_albums: dict[str, Path] = field(default_factory=dict)

def scan_existing_vault_albums(vault_dir: Path, master_key: bytes) -> VaultAlbumCache:
    """
    Scans immediate subdirectories of vault_dir (ignoring '.' prefixed dirs, 'JSON',
    and VAULT_RECYCLE_BIN_NAME), decrypts each subdir's .meta, and indexes the
    Default Secret Album and existing named albums.
    """
    cache = VaultAlbumCache()
    if not vault_dir.exists() or not vault_dir.is_dir():
        return cache

    try:
        subdirs = sorted(vault_dir.iterdir(), key=lambda p: p.name)
    except Exception:
        return cache

    for sub in subdirs:
        if not sub.is_dir():
            continue
        if sub.name.startswith(".") or sub.name.lower() == "json":
            continue

        meta_file = sub / ".meta"
        if not meta_file.is_file():
            continue

        raw_name = read_raw_folder_meta(meta_file, master_key)
        if raw_name is None or raw_name == VAULT_RECYCLE_BIN_NAME:
            continue

        if is_default_album_raw_name(raw_name):
            if cache.default_album_dir is None:
                cache.default_album_dir = sub
            continue

        cache.exact_albums.setdefault(raw_name, sub)
        norm_key = raw_name.strip().lower()
        if norm_key:
            cache.normalized_albums.setdefault(norm_key, sub)

    return cache

def _create_unique_vault_hex_dir(vault_dir: Path) -> Path:
    while True:
        sub_hex = secrets.token_hex(3)
        sub_target = vault_dir / sub_hex
        if not sub_target.exists():
            sub_target.mkdir(parents=True, exist_ok=True)
            return sub_target

def get_or_create_vault_album(
    vault_dir: Path,
    album_name: str | None,
    master_key: bytes,
    album_cache: VaultAlbumCache | None = None,
) -> Path:
    """
    Returns an existing or newly created 6-hex encrypted album directory inside vault_dir.
    Root files (album_name is None) or default album variants route to Default Secret Album
    (__VAULT_DEFAULT_ALBUM__). Named albums match existing .meta entries (exact then
    stripped/case-insensitive) or create a new randomized 6-hex folder with FGMETA .meta.
    """
    vault_dir.mkdir(parents=True, exist_ok=True)
    root_nomedia = vault_dir / ".nomedia"
    if not root_nomedia.exists():
        try:
            root_nomedia.touch()
        except Exception:
            pass

    if album_cache is None:
        album_cache = scan_existing_vault_albums(vault_dir, master_key)

    cleaned = album_name.strip() if album_name is not None else ""
    if (
        album_name is None
        or not cleaned
        or is_default_album_raw_name(album_name)
        or is_default_album_raw_name(cleaned)
    ):
        if album_cache.default_album_dir is not None and album_cache.default_album_dir.is_dir():
            return album_cache.default_album_dir
        sub_target = _create_unique_vault_hex_dir(vault_dir)
        write_encrypted_folder_meta(sub_target, VAULT_DEFAULT_ALBUM_NAME, master_key, vault_dir=vault_dir)
        album_cache.default_album_dir = sub_target
        return sub_target

    if album_name in album_cache.exact_albums and album_cache.exact_albums[album_name].is_dir():
        return album_cache.exact_albums[album_name]

    if cleaned in album_cache.exact_albums and album_cache.exact_albums[cleaned].is_dir():
        return album_cache.exact_albums[cleaned]

    norm_key = cleaned.lower()
    if norm_key in album_cache.normalized_albums and album_cache.normalized_albums[norm_key].is_dir():
        return album_cache.normalized_albums[norm_key]

    sub_target = _create_unique_vault_hex_dir(vault_dir)
    write_encrypted_folder_meta(sub_target, cleaned, master_key, vault_dir=vault_dir)
    album_cache.exact_albums[album_name] = sub_target
    album_cache.exact_albums[cleaned] = sub_target
    album_cache.normalized_albums.setdefault(norm_key, sub_target)
    return sub_target

# ==================== MEDIA & FILE OPERATIONS ====================

def is_supported_media_file(filename: str) -> bool:
    """Returns True if filename has a gallery-supported media extension."""
    return Path(filename).suffix.lower() in SUPPORTED_MEDIA_EXTENSIONS

def get_media_type(filename: str) -> int:
    ext = Path(filename).suffix.lower()
    if ext in VIDEO_EXTS:
        return TYPE_VIDEOS
    elif ext in GIF_EXTS:
        return TYPE_GIFS
    elif ext in RAW_EXTS:
        return TYPE_RAWS
    elif ext in SVG_EXTS:
        return TYPE_SVGS
    return TYPE_IMAGES

def generate_thumbnail_bytes(image_path: Path) -> bytes:
    """Generates 320px JPEG thumbnail for instant gallery preview on Android."""
    if not HAS_PIL:
        return b""
    try:
        with Image.open(image_path) as img:
            img.thumbnail((320, 320))
            if img.mode in ("RGBA", "P"):
                img = img.convert("RGB")
            buf = BytesIO()
            img.save(buf, format="JPEG", quality=75)
            return buf.getvalue()
    except Exception:
        return b""

# ==================== HORIZONTAL PROGRESS BAR ====================

class HorizontalProgressBar:
    """Terminal-width adaptive modern horizontal progress counter."""
    def __init__(self, total_files: int, total_bytes: int, lang: str = "en"):
        self.total_files = total_files
        self.total_bytes = max(total_bytes, 1)
        self.lang = lang
        self.processed_files = 0
        self.processed_bytes = 0
        self.start_time = time.time()
        self.last_update = 0

    def update(self, added_bytes: int, current_file_name: str = "", force: bool = False):
        self.processed_bytes += added_bytes
        now = time.time()
        if not force and (now - self.last_update < 0.08):
            return
        self.last_update = now

        try:
            term_cols = os.get_terminal_size().columns
        except Exception:
            term_cols = 80

        percent = min(100.0, (self.processed_bytes / self.total_bytes) * 100.0)
        elapsed = max(0.001, now - self.start_time)
        speed_mb = (self.processed_bytes / (1024 * 1024)) / elapsed

        mb_done = self.processed_bytes / (1024 * 1024)
        mb_total = self.total_bytes / (1024 * 1024)
        files_unit = t(self.lang, "files_unit")
        info_str = f" {percent:5.1f}% | {self.processed_files}/{self.total_files} {files_unit} | {mb_done:.1f}/{mb_total:.1f} MB | {speed_mb:.1f} MB/s"

        fname_max = max(10, term_cols - len(info_str) - 25)
        short_fname = current_file_name if len(current_file_name) <= fname_max else current_file_name[:fname_max - 3] + "..."
        prefix = f"\r\033[1;36m[{short_fname}]\033[0m "

        bar_len = max(10, term_cols - len(prefix) - len(info_str) - 4)
        filled_len = int(bar_len * (percent / 100.0))
        bar = "\033[1;32m" + "█" * filled_len + "\033[0m" + "░" * (bar_len - filled_len)

        line = f"{prefix}[{bar}]{info_str}"
        sys.stdout.write(line[:term_cols])
        sys.stdout.flush()

    def complete_file(self):
        self.processed_files += 1

    def finish(self):
        self.update(0, force=True)
        sys.stdout.write("\n")
        sys.stdout.flush()

# ==================== ENCRYPTION ENGINE ====================

def append_pending_sync_entries(output_dir: Path, created_bins: list[Path]) -> Path | None:
    """
    Appends newly encrypted .bin relative POSIX paths to <output_dir>/.pending_sync
    while preserving any existing unsynced entries from earlier CLI sessions.
    """
    if not created_bins:
        return None
    pending_file = output_dir / ".pending_sync"
    existing: list[str] = []
    seen: set[str] = set()
    if pending_file.is_file():
        try:
            for line in pending_file.read_text(encoding="utf-8").splitlines():
                clean = line.strip().replace("\\", "/")
                if clean and clean not in seen:
                    existing.append(clean)
                    seen.add(clean)
        except Exception:
            pass

    for bin_path in created_bins:
        try:
            rel = bin_path.relative_to(output_dir).as_posix()
        except ValueError:
            rel = bin_path.name
        if rel and rel not in seen:
            existing.append(rel)
            seen.add(rel)

    pending_file.write_text("\n".join(existing) + "\n", encoding="utf-8")
    return pending_file


def extract_date_taken_ms(source_path: Path, fallback_ms: int) -> int:
    """Extracts EXIF capture timestamp in milliseconds, falling back to fallback_ms."""
    if not HAS_PIL:
        return fallback_ms
    try:
        with Image.open(source_path) as img:
            exif = img.getexif()
            if not exif:
                return fallback_ms
            exif_ifd = {}
            get_ifd = getattr(exif, "get_ifd", None)
            if callable(get_ifd):
                try:
                    exif_ifd = get_ifd(0x8769) or {}
                except Exception:
                    exif_ifd = {}
            raw = (
                exif_ifd.get(36867)
                or exif.get(36867)
                or exif_ifd.get(36868)
                or exif.get(36868)
                or exif.get(306)
            )
            if isinstance(raw, bytes):
                raw = raw.decode("utf-8", errors="ignore")
            if not isinstance(raw, str):
                return fallback_ms
            text = raw.strip()
            if len(text) < 19 or text.startswith("0000"):
                return fallback_ms
            candidate = text[:19]
            normalized = (
                f"{candidate[:4]}:{candidate[5:7]}:{candidate[8:10]} {candidate[11:19]}"
            )
            parsed = datetime.strptime(normalized, "%Y:%m:%d %H:%M:%S")
            ms = int(parsed.timestamp() * 1000)
            return ms if ms > 0 else fallback_ms
    except Exception:
        return fallback_ms


def encrypt_file(
    source_path: Path,
    target_dir: Path,
    kek: bytes,
    on_chunk_processed=None
) -> Path:
    """
    Encrypts a media file into a canonical FGVLTENC v1 .bin container compatible
    with the Calypso Gallery Encrypted Vault.

    Layout: magic(8) | version(1) | keyId(2) | fileId(16) | headerIv(12) |
    N(4) | encHeader(N) | Tink AES256_GCM_HKDF stream. The header is encrypted
    with a key derived from the KEK and bound to the cleartext prefix as AAD;
    the payload uses a fresh per-file DEK and fileId as the stream AAD.
    """
    stat = source_path.stat()
    orig_name = source_path.name
    orig_size = stat.st_size
    last_mod = int(stat.st_mtime * 1000)
    media_type = get_media_type(orig_name)
    date_taken = extract_date_taken_ms(source_path, last_mod) if media_type == TYPE_IMAGES else last_mod
    video_duration = 0

    thumb_bytes = generate_thumbnail_bytes(source_path) if media_type == TYPE_IMAGES else b""

    # Fresh per-file key material and the exact stream size Tink would emit.
    file_id = secrets.token_bytes(16)
    dek = secrets.token_bytes(32)
    ciphertext_length = expected_ciphertext_size(orig_size)

    # 1. Plaintext header (Java DataOutputStream order, all big-endian).
    hdr_buf = BytesIO()
    hdr_buf.write(struct.pack(">H", 1))                  # algorithmParams = 1
    hdr_buf.write(struct.pack(">I", SEGMENT_SIZE))       # segmentSize = 262144
    hdr_buf.write(struct.pack(">Q", ciphertext_length))  # ciphertextLength
    hdr_buf.write(dek)                                   # 32-byte per-file DEK
    # DataOutputStream.writeUTF = u16 byte length + Java modified UTF-8. For ASCII
    # and BMP names this is identical to standard UTF-8 (NUL/supplementary chars differ).
    name_bytes = orig_name.encode("utf-8")
    if len(name_bytes) > 0xFFFF:
        raise ValueError("Original filename is too long for the container header.")
    hdr_buf.write(struct.pack(">H", len(name_bytes)))
    hdr_buf.write(name_bytes)
    hdr_buf.write(struct.pack(">q", last_mod))
    hdr_buf.write(struct.pack(">q", date_taken))
    hdr_buf.write(struct.pack(">q", orig_size))
    hdr_buf.write(struct.pack(">i", media_type))
    hdr_buf.write(struct.pack(">i", video_duration))
    hdr_buf.write(struct.pack(">i", len(thumb_bytes)))
    hdr_buf.write(thumb_bytes)
    header_plain = hdr_buf.getvalue()

    # 2. Header key: HKDF-SHA256(KEK, empty salt, "domain|fileId.hex()").
    header_key = derive_header_key(kek, file_id)
    header_iv = secrets.token_bytes(12)
    encrypted_header_length = len(header_plain) + GCM_TAG_SIZE
    header_aad = (
        CONTAINER_MAGIC
        + bytes([CONTAINER_VERSION])
        + struct.pack(">H", ACTIVE_KEY_ID)
        + file_id
        + struct.pack(">I", encrypted_header_length)
    )
    enc_header = AESGCM(header_key).encrypt(header_iv, header_plain, header_aad)

    # 3. Randomized .bin output filename (unchanged UX).
    while True:
        bin_name = f"{secrets.token_hex(8)}.bin"
        output_file = target_dir / bin_name
        if not output_file.exists():
            break

    # 4. Real Tink AES256_GCM_HKDF streaming-AEAD payload (AAD = fileId).
    stream_primitive = _tink_stream_primitive(dek)
    stream_buffer = _KeepOpenBytesIO()
    with stream_primitive.new_encrypting_stream(stream_buffer, file_id) as encrypting:
        with open(source_path, "rb") as fin:
            while True:
                chunk = fin.read(64 * 1024)
                if not chunk:
                    break
                encrypting.write(chunk)
                if on_chunk_processed:
                    on_chunk_processed(len(chunk))
    tink_stream = stream_buffer.getvalue()

    with open(output_file, "wb") as fout:
        fout.write(CONTAINER_MAGIC)                    # 8B "FGVLTENC"
        fout.write(bytes([CONTAINER_VERSION]))         # 1B (0x01)
        fout.write(struct.pack(">H", ACTIVE_KEY_ID))   # 2B
        fout.write(file_id)                            # 16B
        fout.write(header_iv)                          # 12B
        fout.write(struct.pack(">I", len(enc_header))) # 4B N
        fout.write(enc_header)                         # ciphertext header + tag
        fout.write(tink_stream)                        # Tink stream header + ciphertext

    written_stream = len(tink_stream)
    if written_stream != ciphertext_length:
        raise ValueError(
            f"Tink stream size mismatch: wrote {written_stream}, expected {ciphertext_length}"
        )

    header_key = bytearray(len(header_key))
    dek = bytearray(len(dek))

    try:
        os.utime(output_file, (stat.st_atime, stat.st_mtime))
    except Exception:
        pass

    return output_file

# ==================== CLI ENTRYPOINT ====================

def unlock_master_key_with_path(
    args,
    lang: str,
    extra_vault_paths: list[Path] | None = None,
) -> tuple[VaultKeys, Path | None]:
    """
    Locates .vault on disk, prompts for password/PIN (with retry on interactive input),
    and unwraps both keys in the keyring. Returns (VaultKeys, vault_path_or_none).
    """
    vault_data: bytes | None = None
    vault_path: Path | None = None

    if args.vault:
        candidate = Path(args.vault).expanduser().resolve()
        if candidate.is_dir() and (candidate / ".vault").is_file():
            candidate = candidate / ".vault"
        if not candidate.is_file():
            print(t(lang, "vault_spec_not_found", path=candidate), file=sys.stderr)
            sys.exit(1)
        vault_path = candidate
        vault_data = vault_path.read_bytes()
    else:
        if extra_vault_paths:
            for cand in extra_vault_paths:
                if is_valid_vault_file(cand):
                    vault_path = cand
                    break
        if not vault_path:
            vault_path = find_vault_file()

        if vault_path:
            print(t(lang, "vault_auto_detected", path=vault_path))
            vault_data = vault_path.read_bytes()
        else:
            vault_path = prompt_for_vault_file(lang)
            vault_data = vault_path.read_bytes()

    cli_secret = args.secret
    if not cli_secret and args.pattern:
        cli_secret = args.pattern.strip()

    if cli_secret:
        print(t(lang, "unwrapping_vault"))
        try:
            vault_keys = unwrap_vault_keys(vault_data, cli_secret)
            print(t(lang, "vault_unlocked"))
            return vault_keys, vault_path
        except Exception as e:
            print(t(lang, "vault_unlock_err", err=e), file=sys.stderr)
            sys.exit(1)

    max_attempts = None if sys.stdin.isatty() else 5
    attempt = 0
    while True:
        attempt += 1
        try:
            secret = getpass.getpass(t(lang, "enter_secret"))
        except (KeyboardInterrupt, EOFError):
            print(t(lang, "op_cancelled"))
            sys.exit(1)

        if not secret:
            print(t(lang, "secret_empty"), file=sys.stderr)
            if max_attempts is not None and attempt >= max_attempts:
                sys.exit(1)
            print(t(lang, "vault_retry"), file=sys.stderr)
            continue

        print(t(lang, "unwrapping_vault"))
        try:
            vault_keys = unwrap_vault_keys(vault_data, secret)
            print(t(lang, "vault_unlocked"))
            return vault_keys, vault_path
        except Exception as e:
            print(t(lang, "vault_unlock_err", err=e), file=sys.stderr)
            if max_attempts is not None and attempt >= max_attempts:
                sys.exit(1)
            print(t(lang, "vault_retry"), file=sys.stderr)

def unlock_master_key(
    args,
    lang: str,
    extra_vault_paths: list[Path] | None = None,
) -> VaultKeys:
    """Locates .vault on disk, prompts for password/PIN, and unwraps both vault keys."""
    vault_keys, _ = unlock_master_key_with_path(
        args,
        lang,
        extra_vault_paths=extra_vault_paths,
    )
    return vault_keys

def is_targetable_vault_dir(
    vault_file: Path | None,
    desktop: Path,
    input_path: Path,
    desktop_out_dir: Path,
) -> bool:
    """
    Returns True when vault_file.parent is a real vault directory (i.e. Secrets on an
    external USB/SD drive, or a directory containing .nomedia / .meta albums / .bin
    files) rather than a standalone .vault copied onto Desktop, Home, input_path, or
    Persistent root.
    """
    if vault_file is None:
        return False
    try:
        vdir = vault_file.parent.resolve()
        if not vdir.is_dir():
            return False

        excluded = {
            desktop.resolve(),
            input_path.resolve(),
            desktop_out_dir.resolve(),
            Path.home().resolve(),
            Path(__file__).resolve().parent.resolve(),
            Path("/home/amnesia/Persistent"),
            (Path.home() / "Persistent").resolve(),
        }
        if vdir in excluded:
            return False

        if vdir.name in VAULT_DIR_NAMES:
            return True

        if (vdir / ".nomedia").is_file():
            return True

        for child in vdir.iterdir():
            if child.is_file() and child.suffix.lower() == ".bin":
                return True
            if child.is_dir() and not child.name.startswith(".") and child.name.lower() != "json":
                if (child / ".meta").is_file():
                    return True
    except Exception:
        return False
    return False

def choose_encrypt_output_dir(
    args,
    desktop: Path,
    lang: str,
    input_path: Path,
    vault_path: Path | None,
) -> Path:
    """
    Determines the unified encryption destination directory with no interactive prompt:
    1. Explicit -o / --output path.
    2. Targetable vault directory (e.g. .../Secrets/.vault -> .../Secrets).
    3. External / non-local .vault parent (e.g. /media/usb/.vault -> /media/usb/Secrets).
    4. Fallback to localized Desktop output folder (Encrypted_Output / Sifreli_Cikti).
    """
    if args.output:
        target_dir = Path(args.output).expanduser().resolve()
        print(t(lang, "enc_auto_target_vault", path=target_dir))
        return target_dir

    desktop_out_dir = (desktop / FOLDER_NAMES.get(lang, FOLDER_NAMES["en"])["enc_out"]).resolve()
    if is_targetable_vault_dir(vault_path, desktop, input_path, desktop_out_dir):
        target_dir = vault_path.parent.resolve()
    elif vault_path is not None:
        vdir = vault_path.parent.resolve()
        standalone_excluded = {
            desktop.resolve(),
            desktop_out_dir,
            Path.home().resolve(),
            input_path.resolve(),
            Path(__file__).resolve().parent.resolve(),
        }
        if vdir not in standalone_excluded:
            existing_vault_dir = next((vdir / name for name in VAULT_DIR_NAMES if (vdir / name).is_dir()), None)
            target_dir = (existing_vault_dir or (vdir / VAULT_DIR_NAME)).resolve()
        else:
            target_dir = desktop_out_dir
    else:
        target_dir = desktop_out_dir

    target_dir.mkdir(parents=True, exist_ok=True)
    print(t(lang, "enc_auto_target_vault", path=target_dir))
    return target_dir

def run_encrypt_mode(args, desktop: Path, lang: str):
    platform_kind = get_platform_kind()
    if platform_kind in ("windows", "macos"):
        print(t(lang, "non_ram_os_notice", os_name=get_platform_display_name()))

    input_path = Path(args.input).expanduser().resolve() if args.input else get_localized_desktop_dir(desktop, lang, "enc_in")
    if args.input:
        if not input_path.exists():
            print(t(lang, "enc_input_not_found", path=input_path), file=sys.stderr)
            sys.exit(1)
    else:
        input_path.mkdir(parents=True, exist_ok=True)

    if is_cloud_synced_path(input_path):
        print(t(lang, "cloud_sync_warning", path=input_path))

    items_to_encrypt: list[tuple[Path, str | None]] = []
    skipped_unsupported = 0
    nested_subfolders_seen: set[str] = set()

    if input_path.is_file():
        fname = input_path.name
        if fname.lower().endswith(".bin") or fname.startswith("."):
            pass
        elif not is_supported_media_file(fname):
            print(t(lang, "skip_unsupported_fmt", name=fname))
            skipped_unsupported += 1
        else:
            items_to_encrypt.append((input_path, None))
    elif input_path.is_dir():
        for p in sorted(input_path.rglob("*")):
            if not p.is_file():
                continue
            if p.suffix.lower() == ".bin" or p.name.startswith("."):
                continue
            rel = p.relative_to(input_path)
            if not is_supported_media_file(p.name):
                print(t(lang, "skip_unsupported_fmt", name=str(rel)))
                skipped_unsupported += 1
                continue
            album_name = rel.parts[0] if len(rel.parts) > 1 else None
            if len(rel.parts) > 2 and album_name:
                nested_subfolders_seen.add(album_name)
            items_to_encrypt.append((p, album_name))

    if not items_to_encrypt:
        display_out = Path(args.output).expanduser().resolve() if args.output else get_localized_desktop_dir(desktop, lang, "enc_out")
        print("\n" + "=" * 65)
        print(t(lang, "enc_title"))
        print("=" * 65)
        print(t(lang, "folders_ready"))
        print(t(lang, "input_folder", path=input_path))
        print(t(lang, "output_folder", path=display_out))
        print(t(lang, "enc_empty"))
        print(t(lang, "enc_empty_hint1"))
        print(t(lang, "enc_empty_hint2"))
        if skipped_unsupported > 0:
            print(t(lang, "enc_skipped_summary", count=skipped_unsupported))
        print("=" * 65 + "\n")
        sys.exit(0)

    vault_keys, vault_path = unlock_master_key_with_path(args, lang)
    vmk = vault_keys.vmk   # protects folder .meta files
    kek = vault_keys.kek   # protects file container headers
    if vault_path is not None and is_cloud_synced_path(vault_path):
        print(t(lang, "cloud_sync_warning", path=vault_path))

    output_dir = choose_encrypt_output_dir(args, desktop, lang, input_path, vault_path)
    output_dir.mkdir(parents=True, exist_ok=True)
    if is_cloud_synced_path(output_dir):
        print(t(lang, "cloud_sync_warning", path=output_dir))

    nomedia = output_dir / ".nomedia"
    if not nomedia.exists():
        try:
            nomedia.touch()
        except Exception:
            pass

    for album in sorted(nested_subfolders_seen):
        print(t(lang, "enc_nested_flatten_note", album=album))

    total_bytes = sum(f.stat().st_size for f, _ in items_to_encrypt)
    print(t(lang, "enc_queued", count=len(items_to_encrypt), mb=total_bytes / (1024 * 1024)))

    album_cache = scan_existing_vault_albums(output_dir, vmk)
    progress = HorizontalProgressBar(len(items_to_encrypt), total_bytes, lang=lang)
    created_bins: list[Path] = []

    try:
        for src_file, album_name in items_to_encrypt:
            dest_dir = get_or_create_vault_album(
                output_dir,
                album_name,
                vmk,
                album_cache=album_cache,
            )

            out_bin = encrypt_file(
                src_file,
                dest_dir,
                kek,
                on_chunk_processed=lambda chunk_len, fn=src_file.name: progress.update(chunk_len, fn)
            )
            created_bins.append(out_bin)
            progress.complete_file()

        progress.finish()
        append_pending_sync_entries(output_dir, created_bins)
        print(t(lang, "enc_success", count=len(created_bins)))
        if skipped_unsupported > 0:
            print(t(lang, "enc_skipped_summary", count=skipped_unsupported))
        print(t(lang, "out_dir_line", path=output_dir))
        if (output_dir / ".vault").is_file() or output_dir.name in VAULT_DIR_NAMES:
            print(t(lang, "enc_tip_direct"))
        else:
            print(t(lang, "enc_tip1"))
            print(t(lang, "enc_tip2", app_dir=APP_DIR_NAME))

        delete_source = args.delete_source
        shown_wipe_disclaimer = False
        if not delete_source and sys.stdin.isatty():
            if platform_kind in ("windows", "macos"):
                print("\n" + t(lang, "wipe_non_ram_disclaimer", os_name=get_platform_display_name()))
                shown_wipe_disclaimer = True
            try:
                ans = input(t(lang, "wipe_prompt")).strip().lower()
                if ans in ("y", "yes", "e", "evet"):
                    delete_source = True
            except (KeyboardInterrupt, EOFError):
                pass

        if delete_source:
            if platform_kind in ("windows", "macos") and not shown_wipe_disclaimer:
                print("\n" + t(lang, "wipe_non_ram_disclaimer", os_name=get_platform_display_name()))
            print(t(lang, "wiping_start"))
            for src_file, _ in items_to_encrypt:
                try:
                    if not os.access(src_file, os.W_OK):
                        try:
                            os.chmod(src_file, 0o666)
                        except Exception:
                            pass
                    size = src_file.stat().st_size
                    if size > 0:
                        with open(src_file, "r+b") as wipe_f:
                            remaining = size
                            zero_block = b"\x00" * min(remaining, 1024 * 1024)
                            while remaining > 0:
                                to_write = min(remaining, len(zero_block))
                                wipe_f.write(zero_block[:to_write])
                                remaining -= to_write
                            wipe_f.flush()
                    src_file.unlink()
                except Exception as e:
                    print(t(lang, "wipe_err", name=src_file.name, err=e))

            if input_path.is_dir():
                for d in sorted(input_path.rglob("*"), key=lambda p: len(str(p)), reverse=True):
                    if d.is_dir() and d != input_path:
                        try:
                            d.rmdir()
                        except Exception:
                            pass
            print(t(lang, "wiping_done"))

        if args.adb:
            phone_container = f"/storage/emulated/0/{APP_DIR_NAME}"
            phone_dest = f"{phone_container}/{VAULT_DIR_NAME}/"
            print(t(lang, "adb_pushing", dest=phone_dest))
            res = subprocess.run(["adb", "devices"], capture_output=True, text=True)
            has_adb_device = any(
                len(line.split()) >= 2 and line.split()[1] == "device"
                for line in res.stdout.splitlines()[1:]
            )
            if not has_adb_device:
                print(t(lang, "adb_no_device"))
            else:
                pushed_dirs: set[Path] = set()
                for b in created_bins:
                    parent = b.parent
                    if parent != output_dir:
                        if parent not in pushed_dirs:
                            pushed_dirs.add(parent)
                            print(t(lang, "adb_transferring", name=parent.name))
                            subprocess.run(["adb", "push", str(parent), phone_dest], check=True)
                    else:
                        print(t(lang, "adb_transferring", name=b.name))
                        subprocess.run(["adb", "push", str(b), phone_dest], check=True)
                print(t(lang, "adb_done"))
    finally:
        vmk = bytearray(len(vmk))
        kek = bytearray(len(kek))

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
  Encrypt (English: To_Encrypt -> Vault or Encrypted_Output | Türkçe: Sifrelenecek -> Kasa veya Sifreli_Cikti):
     python3 calypso_vault.py --lang en
  Direct external vault example (Linux / macOS / Windows):
     python3 calypso_vault.py --lang en -i ~/Desktop/To_Encrypt -v /media/amnesia/USB/Secrets/.vault

        """
    )
    parser.add_argument("-e", "--encrypt", action="store_true", help="Encrypt plaintext media into .bin format (default)")

    parser.add_argument("--lang", choices=["en", "tr"], help="CLI language and Desktop folder language (en or tr)")
    parser.add_argument("-v", "-k", "--vault", help="Path to .vault file (Searched automatically across mounted vaults/USB/desktop if omitted)")
    parser.add_argument("-i", "--input", help="Input folder/file")
    parser.add_argument("-o", "--output", help="Output folder (or target Secrets vault directory)")
    parser.add_argument("-s", "--secret", help="Vault password or PIN (Prompted securely if omitted)")
    parser.add_argument("--pattern", help="Grid pattern indices if 3x3 pattern is used (e.g. '0,1,2,5')")
    parser.add_argument("--delete-source", action="store_true", help="Securely wipe and delete original files after encryption")
    parser.add_argument("--adb", action="store_true", help="Directly push encrypted files to connected phone via ADB")

    args = parser.parse_args()
    check_dependencies()
    install_network_audit_hook(allow_adb=args.adb)

    lang = choose_language(args)
    run_encrypt_mode(args, desktop, lang)

if __name__ == "__main__":
    main()

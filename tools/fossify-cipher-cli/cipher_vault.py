#!/usr/bin/env python3
"""
Fossify Gallery Cipher - Linux Offline Vault Encryptor CLI Tool
----------------------------------------------------------------
Encrypts plaintext media files (photos, videos) on Linux into .bin format,
100% compatible with Android Fossify Gallery Cipher
(AES-256-CTR + Poly1305-AES authenticated streaming encryption).

IMPORTANT SECURITY NOTICE (LIVE BOOT / RAM-ONLY EXECUTION):
  Always prefer running this tool inside a RAM-only Live Boot operating system
  such as Tails OS when handling sensitive plaintext media.
  On a standard disk-backed OS (Windows, macOS, or installed Linux on SSD/HDD),
  copying or deleting plaintext media can leave forensic traces in filesystem
  journals, swap/pagefile, thumbnail caches, and SSD wear-leveling blocks.
  A Live Boot system keeps temporary plaintext operations strictly in volatile
  RAM so traces vanish when you shut down.

Features:
- Encrypt-Only workflow targeting either an external Cipher_Secrets vault
  directly or an output/delta folder (Encrypted_Output / Sifreli_Cikti).
- Automatic .vault discovery across Desktop, persistent storage, and mounted USB drives.
- Pre-encryption supported media format validation matching the Android app.
- Automatic Default Secret Album routing for root files & .meta-aware subfolder matching.
- Secure source sanitation (overwriting with zeros) of plaintext source media (--delete-source).
"""

import os
import sys
import time
import hmac
import hashlib
import secrets
import struct
import getpass
import argparse
import subprocess
import shutil
from dataclasses import dataclass, field
from pathlib import Path
from io import BytesIO

# The standalone container of the Android app in the internal storage.
APP_DIR_NAME = "Fossify Gallery Cipher"

HAS_CRYPTO = True
try:
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
    from cryptography.hazmat.backends import default_backend
    from argon2.low_level import hash_secret_raw, Type
except ImportError:
    HAS_CRYPTO = False

try:
    from PIL import Image
    HAS_PIL = True
except ImportError:
    HAS_PIL = False

def check_dependencies():
    if not HAS_CRYPTO:
        print("[ERROR] Required Python packages are missing!", file=sys.stderr)
        print("To install on Debian-based Linux (e.g., Debian, Ubuntu, Tails), run in terminal:", file=sys.stderr)
        print("  sudo apt update && sudo apt install -y python3-cryptography python3-argon2 python3-pil", file=sys.stderr)
        print("Or via pip:", file=sys.stderr)
        print("  pip install cryptography argon2-cffi pillow", file=sys.stderr)
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
MAGIC_V2 = b"FGVLTENC"
VERSION_SYMMETRIC_AUTH = 3
VAULT_HEADER_MAGIC = b"FGVLT2"
META_MAGIC_V2 = b"FGMETA2"
META_MAGIC_SYM = b"FVLTMETA"

VAULT_DEFAULT_ALBUM_NAME = "__VAULT_DEFAULT_ALBUM__"
DEFAULT_SECRET_ALBUM_NAME = "Default Secret Album"
VAULT_RECYCLE_BIN_NAME = "$$VAULT_RECYCLE_BIN$$"
DEFAULT_ALBUM_RAW_NAMES = {
    VAULT_DEFAULT_ALBUM_NAME,
    DEFAULT_SECRET_ALBUM_NAME,
    "Varsayılan Gizli Albüm",
    "Default Hidden Album",
}

DOMAIN_HEADER_KEY = b"FossifyGallery:Header:AES256GCM"
DOMAIN_CONTENT_ENC_KEY = b"FossifyGallery:Payload:AES256CTR"
DOMAIN_CONTENT_MAC_KEY = b"FossifyGallery:Payload:Poly1305"

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
        "vault_drop_hint": "    You can drop your .vault file (from {app_dir} > Cipher_Secrets > .vault)",
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
        "enc_title": "\033[1;36m       Fossify Gallery Cipher - Linux Offline Encryptor\033[0m",
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
        "enc_tip2": "\033[1;37m    '{app_dir}/Cipher_Secrets/' directory.\033[0m",
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
        "enc_dest_title": "\n\033[1;36m[?] Select Encryption Destination:\033[0m",
        "enc_dest_opt1": "  [1] Encrypt directly into detected Vault (Recommended): \033[1;32m{path}\033[0m",
        "enc_dest_opt2": "  [2] Save to Desktop output folder: \033[1;33m{path}\033[0m",
        "enc_dest_opt3": "  [3] Specify another custom output / vault folder",
        "enc_dest_prompt": "\033[1;36m[?] Select destination [1/2/3] (Default: 1): \033[0m",
        "enc_dest_invalid": "\033[1;31m[-] Invalid selection. Enter 1, 2, or 3.\033[0m",
        "enc_dest_custom_prompt": "\033[1;36m-> Enter custom target vault/output folder path: \033[0m",
        "enc_dest_custom_invalid": "\033[1;31m[-] Invalid path or cannot create directory: {path}\033[0m",
        "enc_tip_direct": "\033[1;37m[i] Encrypted directly inside the Vault. Existing .meta albums were reused and new albums created.\033[0m",
    },
    "tr": {
        "op_cancelled": "\nİşlem iptal edildi.",
        "vault_not_found": "\033[1;33m[?] .vault dosyası otomatik olarak bulunamadı.\033[0m",
        "vault_drop_hint": "    .vault dosyanızı ({app_dir} > Cipher_Secrets > .vault)",
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
        "enc_title": "\033[1;36m      Fossify Gallery Cipher - Çevrimdışı Şifreleme Aracı\033[0m",
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
        "enc_tip2": "\033[1;37m    '{app_dir}/Cipher_Secrets/' dizinine aktarabilirsiniz.\033[0m",
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
        "enc_dest_title": "\n\033[1;36m[?] Şifreleme Hedefini Seçin:\033[0m",
        "enc_dest_opt1": "  [1] Doğrudan algılanan Kasaya şifrele (Önerilen): \033[1;32m{path}\033[0m",
        "enc_dest_opt2": "  [2] Masaüstü çıktı klasörüne kaydet: \033[1;33m{path}\033[0m",
        "enc_dest_opt3": "  [3] Başka bir özel çıktı / kasa klasörü belirt",
        "enc_dest_prompt": "\033[1;36m[?] Hedef seçin [1/2/3] (Varsayılan: 1): \033[0m",
        "enc_dest_invalid": "\033[1;31m[-] Geçersiz seçim. 1, 2 veya 3 girin.\033[0m",
        "enc_dest_custom_prompt": "\033[1;36m-> Özel hedef kasa/çıktı klasörü yolunu girin: \033[0m",
        "enc_dest_custom_invalid": "\033[1;31m[-] Geçersiz yol veya klasör oluşturulamadı: {path}\033[0m",
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

def get_desktop_dir() -> Path:
    """Detects Desktop directory on Linux environments."""
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

    # 3. Common Desktop directory names
    for candidate_name in ["Desktop", "Masaüstü", "desktop"]:
        candidate = Path.home() / candidate_name
        if candidate.exists() and candidate.is_dir():
            return candidate

    # 4. Fallback: create ~/Desktop
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
    """Returns True if path exists, is >= 32 bytes, and begins with FGVLT2 header magic."""
    try:
        if not path.is_file() or path.stat().st_size < 32:
            return False
        with open(path, "rb") as f:
            return f.read(len(VAULT_HEADER_MAGIC)) == VAULT_HEADER_MAGIC
    except Exception:
        return False

def _external_mount_roots() -> list[Path]:
    """Returns unique mount roots where external USB/SD drives may be mounted on Linux."""
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
    vault_dir_candidates: list[Path] = []
    standalone_candidates: list[Path] = []

    def add_vault_dir_locations(base: Path):
        vault_dir_candidates.extend([
            base / "Cipher_Secrets" / ".vault",
            base / APP_DIR_NAME / "Cipher_Secrets" / ".vault",
            base / "Documents" / "Cipher" / "Cipher_Secrets" / ".vault",
        ])

    # 1. Tails / Live-Boot Persistent & external USB drives (Cipher_Secrets vaults first)
    persistent_roots = [Path("/home/amnesia/Persistent"), Path.home() / "Persistent"]
    for p_root in persistent_roots:
        add_vault_dir_locations(p_root)

    for mount_root in _external_mount_roots():
        if not mount_root.exists() or not mount_root.is_dir():
            continue
        add_vault_dir_locations(mount_root)
        try:
            for usb_dir in sorted(mount_root.iterdir(), key=lambda p: p.name):
                if usb_dir.is_dir():
                    add_vault_dir_locations(usb_dir)
                    standalone_candidates.append(usb_dir / ".vault")
                    standalone_candidates.append(usb_dir / "phone.vault")
        except Exception:
            pass

    # 2. Desktop, Persistent standalone, Home, CWD, and script directory
    standalone_candidates = [
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
        # Tails Persistent Storage
        Path("/home/amnesia/Persistent/.vault"),
        Path("/home/amnesia/Persistent/phone.vault"),
        Path("/home/amnesia/Persistent/vault.vault"),
        Path.home() / "Persistent" / ".vault",
        Path.home() / "Persistent" / "phone.vault",
        # Home directory
        Path.home() / ".vault",
        Path.home() / "phone.vault",
        # Working directory & script directory
        Path.cwd() / ".vault",
        Path.cwd() / "phone.vault",
        Path(__file__).resolve().parent / ".vault",
        Path(__file__).resolve().parent / "phone.vault",
    ] + standalone_candidates

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

        clean_path = raw.strip("'\"")
        p = Path(clean_path).expanduser().resolve()
        if is_valid_vault_file(p):
            desktop = get_desktop_dir()
            desktop_vault = desktop / ".vault"
            in_vault_dir = p.parent.name == "Cipher_Secrets" or (p.parent / ".nomedia").is_file()
            if not in_vault_dir and not desktop_vault.exists() and p != desktop_vault:
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

def derive_subkey(key: bytes, domain: bytes) -> bytes:
    """HKDF-style domain separation via HMAC-SHA256."""
    return hmac.new(key, domain, hashlib.sha256).digest()

class Poly1305AES:
    """
    Streaming Poly1305-AES MAC calculator 100% compatible with Bouncy Castle Poly1305(AESEngine).
    RFC 8439 / DJB 2005 Poly1305-AES specification.
    """
    def __init__(self, key: bytes, nonce: bytes):
        if len(key) != 32 or len(nonce) != 16:
            raise ValueError("Poly1305-AES requires 32-byte key and 16-byte nonce.")

        r_bar = bytearray(key[:16])
        r_bar[3] &= 15
        r_bar[7] &= 15
        r_bar[11] &= 15
        r_bar[15] &= 15
        r_bar[4] &= 252
        r_bar[8] &= 252
        r_bar[12] &= 252
        self.r = int.from_bytes(r_bar, "little")

        k_aes = key[16:32]
        aes = Cipher(algorithms.AES(k_aes), modes.ECB(), backend=default_backend()).encryptor()
        s_bytes = aes.update(nonce) + aes.finalize()
        self.s = int.from_bytes(s_bytes, "little")

        self.p = (1 << 130) - 5
        self.accumulator = 0
        self.buffer = bytearray()

    def update(self, data: bytes):
        self.buffer.extend(data)
        buf_len = len(self.buffer)
        offset = 0
        while buf_len - offset >= 16:
            chunk = self.buffer[offset:offset + 16]
            chunk_num = int.from_bytes(chunk, "little") + (1 << 128)
            self.accumulator = ((self.accumulator + chunk_num) * self.r) % self.p
            offset += 16
        if offset > 0:
            del self.buffer[:offset]

    def finalize(self) -> bytes:
        if len(self.buffer) > 0:
            pad_bit = 1 << (len(self.buffer) * 8)
            chunk_num = int.from_bytes(self.buffer, "little") + pad_bit
            self.accumulator = ((self.accumulator + chunk_num) * self.r) % self.p
            self.buffer.clear()

        tag_num = (self.accumulator + self.s) % (1 << 128)
        return tag_num.to_bytes(16, "little")

def unlock_vault_header(vault_path: Path, secret: str) -> bytes:
    """
    Unwraps the .vault header file from Android and extracts the 256-bit Master Key.
    """
    return unlock_vault_header_bytes(vault_path.read_bytes(), secret)

def unlock_vault_header_bytes(data: bytes, secret: str) -> bytes:
    """
    Unwraps raw .vault header bytes and extracts the 256-bit Master Key.
    """
    if len(data) < 32:
        raise ValueError(".vault file is too small or invalid.")

    offset = 0
    magic = data[offset:offset + 6]
    offset += 6
    if magic != VAULT_HEADER_MAGIC:
        raise ValueError(f"Invalid vault header! Expected: {VAULT_HEADER_MAGIC.decode()}, found: {magic}")

    version = data[offset]
    offset += 1
    kdf_type = data[offset]
    offset += 1
    if kdf_type != 1:
        raise ValueError("Unsupported KDF algorithm (Only Argon2id is supported).")

    iterations, memory_kb, parallelism = struct.unpack(">3i", data[offset:offset + 12])
    offset += 12

    if version >= 3:
        _prot_type = data[offset]
        offset += 1

    salt_len = struct.unpack(">i", data[offset:offset + 4])[0]
    offset += 4
    if salt_len <= 0 or salt_len > 256:
        raise ValueError("Invalid salt length.")

    salt = data[offset:offset + salt_len]
    offset += salt_len

    iv = data[offset:offset + 12]
    offset += 12

    wrapped_len = struct.unpack(">i", data[offset:offset + 4])[0]
    offset += 4
    if wrapped_len <= 0 or offset + wrapped_len > len(data):
        raise ValueError("Corrupted wrapped bundle data.")

    wrapped = data[offset:offset + wrapped_len]

    # Argon2id key derivation
    wrapping_key = hash_secret_raw(
        secret=secret.encode("utf-8"),
        salt=salt,
        time_cost=iterations,
        memory_cost=memory_kb,
        parallelism=parallelism,
        hash_len=32,
        type=Type.ID
    )

    # AES-256-GCM authenticated unwrap
    tag = wrapped[-16:]
    ciphertext = wrapped[:-16]
    cipher = Cipher(algorithms.AES(wrapping_key), modes.GCM(iv, tag), backend=default_backend()).decryptor()
    try:
        bundle = cipher.update(ciphertext) + cipher.finalize()
    except Exception:
        raise ValueError("Incorrect password/PIN or corrupted file!")

    # Master Key is the first 32 bytes of the bundle
    master_key = bundle[:32]
    return master_key

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
    Writes .meta (FGMETA2) compatible with Android Fossify Gallery Cipher.
    Creates .nomedia at vault_dir root, and inside folder ONLY when original_name
    is an explicitly hidden dot-folder (never for normal albums, default album, or recycle bin).
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
    cipher = Cipher(algorithms.AES(master_key), modes.GCM(iv), backend=default_backend()).encryptor()
    enc_name = cipher.update(original_name.encode("utf-8")) + cipher.finalize() + cipher.tag

    with open(meta_file, "wb") as f:
        f.write(META_MAGIC_V2)                    # 7B ("FGMETA2")
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
    Decrypts raw .meta bytes (FGMETA2 or legacy FVLTMETA) and returns the raw
    decrypted UTF-8 folder name without display mapping, or None if invalid.
    """
    if len(data) >= 8 + 12 + 4 + 16 and data[:8] == META_MAGIC_SYM:
        magic_len = 8
    elif len(data) >= 7 + 12 + 4 + 16 and data[:7] == META_MAGIC_V2:
        magic_len = 7
    else:
        return None

    iv = data[magic_len:magic_len + 12]
    enc_len = struct.unpack(">i", data[magic_len + 12:magic_len + 16])[0]
    payload_start = magic_len + 16
    if enc_len <= 16 or payload_start + enc_len > len(data):
        return None

    enc_name = data[payload_start:payload_start + enc_len]
    tag = enc_name[-16:]
    ciphertext = enc_name[:-16]
    try:
        cipher = Cipher(algorithms.AES(master_key), modes.GCM(iv, tag), backend=default_backend()).decryptor()
        plain = cipher.update(ciphertext) + cipher.finalize()
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
    Decrypts raw .meta bytes (FGMETA2) and returns the display album folder name,
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
    Decrypts a .meta file (FGMETA2) and returns the display album folder name,
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
            legacy_meta = sub / ".fmeta"
            if legacy_meta.is_file():
                meta_file = legacy_meta
            else:
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
    stripped/case-insensitive) or create a new randomized 6-hex folder with FGMETA2 .meta.
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

def encrypt_file(
    source_path: Path,
    target_dir: Path,
    master_key: bytes,
    on_chunk_processed=None
) -> Path:
    """
    Encrypts a media file into a .bin file 100% compatible with Android Fossify Gallery Cipher.
    Uses chunked streaming for high-efficiency on large files.
    """
    stat = source_path.stat()
    orig_name = source_path.name
    orig_size = stat.st_size
    last_mod = int(stat.st_mtime * 1000)
    date_taken = last_mod
    media_type = get_media_type(orig_name)
    video_duration = 0

    thumb_bytes = generate_thumbnail_bytes(source_path) if media_type == TYPE_IMAGES else b""

    # 1. Plaintext Header Packet (Java DataOutputStream Big-Endian format)
    hdr_buf = BytesIO()
    name_bytes = orig_name.encode("utf-8")
    hdr_buf.write(struct.pack(">H", len(name_bytes)))
    hdr_buf.write(name_bytes)
    hdr_buf.write(struct.pack(">q", last_mod))
    hdr_buf.write(struct.pack(">q", date_taken))
    hdr_buf.write(struct.pack(">q", orig_size))
    hdr_buf.write(struct.pack(">i", media_type))
    hdr_buf.write(struct.pack(">i", video_duration))
    hdr_buf.write(struct.pack(">i", len(thumb_bytes)))
    if thumb_bytes:
        hdr_buf.write(thumb_bytes)
    header_plain = hdr_buf.getvalue()

    # 2. Key Derivation (Domain separation via HMAC-SHA256)
    header_key = derive_subkey(master_key, DOMAIN_HEADER_KEY)
    content_enc_key = derive_subkey(master_key, DOMAIN_CONTENT_ENC_KEY)
    content_mac_key = derive_subkey(master_key, DOMAIN_CONTENT_MAC_KEY)

    # 3. Header Encryption (AES-256-GCM)
    header_iv = secrets.token_bytes(12)
    h_cipher = Cipher(algorithms.AES(header_key), modes.GCM(header_iv), backend=default_backend()).encryptor()
    enc_header_data = h_cipher.update(header_plain) + h_cipher.finalize() + h_cipher.tag

    # 4. Randomized .bin Output Filename
    while True:
        bin_name = f"{secrets.token_hex(8)}.bin"
        output_file = target_dir / bin_name
        if not output_file.exists():
            break

    # 5. Payload Encryption (AES-256-CTR + Poly1305-AES Streaming)
    content_iv = secrets.token_bytes(16)
    c_cipher = Cipher(algorithms.AES(content_enc_key), modes.CTR(content_iv), backend=default_backend()).encryptor()
    poly_mac = Poly1305AES(content_mac_key, content_iv)

    with open(output_file, "wb") as fout:
        fout.write(MAGIC_V2)                                     # 8B ("FGVLTENC")
        fout.write(bytes([VERSION_SYMMETRIC_AUTH]))              # 1B (0x03)
        fout.write(header_iv)                                    # 12B
        fout.write(struct.pack(">i", len(enc_header_data)))       # 4B Big-Endian int
        fout.write(enc_header_data)                              # Encrypted Header + 16B GCM Tag
        fout.write(content_iv)                                   # 16B Content IV

        chunk_size = 64 * 1024
        with open(source_path, "rb") as fin:
            while True:
                chunk = fin.read(chunk_size)
                if not chunk:
                    break
                enc_chunk = c_cipher.update(chunk)
                if enc_chunk:
                    fout.write(enc_chunk)
                    poly_mac.update(enc_chunk)
                if on_chunk_processed:
                    on_chunk_processed(len(chunk))

            final_enc = c_cipher.finalize()
            if final_enc:
                fout.write(final_enc)
                poly_mac.update(final_enc)

        mac_tag = poly_mac.finalize()
        fout.write(mac_tag)

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
) -> tuple[bytes, Path | None]:
    """
    Locates .vault on disk, prompts for password/PIN (with retry on interactive input),
    and unwraps the Master Key. Returns (master_key, vault_path_or_none).
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
            master_key = unlock_vault_header_bytes(vault_data, cli_secret)
            print(t(lang, "vault_unlocked"))
            return master_key, vault_path
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
            master_key = unlock_vault_header_bytes(vault_data, secret)
            print(t(lang, "vault_unlocked"))
            return master_key, vault_path
        except Exception as e:
            print(t(lang, "vault_unlock_err", err=e), file=sys.stderr)
            if max_attempts is not None and attempt >= max_attempts:
                sys.exit(1)
            print(t(lang, "vault_retry"), file=sys.stderr)

def unlock_master_key(
    args,
    lang: str,
    extra_vault_paths: list[Path] | None = None,
) -> bytes:
    """Locates .vault on disk, prompts for password/PIN, and unwraps the Master Key."""
    master_key, _ = unlock_master_key_with_path(
        args,
        lang,
        extra_vault_paths=extra_vault_paths,
    )
    return master_key

def is_targetable_vault_dir(
    vault_file: Path | None,
    desktop: Path,
    input_path: Path,
    desktop_out_dir: Path,
) -> bool:
    """
    Returns True when vault_file.parent is a real vault directory (e.g. Cipher_Secrets
    on an external USB/SD drive or a directory containing .nomedia / .meta albums / .bin files)
    rather than a standalone .vault copied onto Desktop, Home, input_path, or Persistent root.
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

        if vdir.name == "Cipher_Secrets":
            return True

        if (vdir / ".nomedia").is_file():
            return True

        for child in vdir.iterdir():
            if child.is_file() and child.suffix.lower() == ".bin":
                return True
            if child.is_dir() and not child.name.startswith(".") and child.name.lower() != "json":
                if (child / ".meta").is_file() or (child / ".fmeta").is_file():
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
    Determines the encryption destination directory. When -o is omitted and an external
    or dedicated vault directory is detected in interactive mode, offers encrypting
    directly into that vault ([1]), Desktop output ([2]), or a custom directory ([3]).
    """
    if args.output:
        return Path(args.output).expanduser().resolve()

    desktop_out_dir = desktop / FOLDER_NAMES.get(lang, FOLDER_NAMES["en"])["enc_out"]
    if not sys.stdin.isatty() or not is_targetable_vault_dir(vault_path, desktop, input_path, desktop_out_dir):
        desktop_out_dir.mkdir(parents=True, exist_ok=True)
        return desktop_out_dir

    direct_vault_dir = vault_path.parent.resolve()
    print(t(lang, "enc_dest_title"))
    print(t(lang, "enc_dest_opt1", path=direct_vault_dir))
    print(t(lang, "enc_dest_opt2", path=desktop_out_dir))
    print(t(lang, "enc_dest_opt3"))

    while True:
        try:
            choice = input(t(lang, "enc_dest_prompt")).strip()
        except (KeyboardInterrupt, EOFError):
            print(t(lang, "op_cancelled"))
            sys.exit(1)

        if choice in ("", "1"):
            return direct_vault_dir
        if choice == "2":
            desktop_out_dir.mkdir(parents=True, exist_ok=True)
            return desktop_out_dir
        if choice == "3":
            while True:
                try:
                    raw = input(t(lang, "enc_dest_custom_prompt")).strip()
                except (KeyboardInterrupt, EOFError):
                    print(t(lang, "op_cancelled"))
                    sys.exit(1)
                clean = raw.strip("'\"")
                if not clean:
                    continue
                custom_dir = Path(clean).expanduser().resolve()
                try:
                    custom_dir.mkdir(parents=True, exist_ok=True)
                    return custom_dir
                except Exception:
                    print(t(lang, "enc_dest_custom_invalid", path=clean))
        print(t(lang, "enc_dest_invalid"))

def run_encrypt_mode(args, desktop: Path, lang: str):
    input_path = Path(args.input).expanduser().resolve() if args.input else get_localized_desktop_dir(desktop, lang, "enc_in")
    if args.input:
        if not input_path.exists():
            print(t(lang, "enc_input_not_found", path=input_path), file=sys.stderr)
            sys.exit(1)
    else:
        input_path.mkdir(parents=True, exist_ok=True)

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

    master_key, vault_path = unlock_master_key_with_path(args, lang)
    output_dir = choose_encrypt_output_dir(args, desktop, lang, input_path, vault_path)
    output_dir.mkdir(parents=True, exist_ok=True)

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

    album_cache = scan_existing_vault_albums(output_dir, master_key)
    progress = HorizontalProgressBar(len(items_to_encrypt), total_bytes, lang=lang)
    created_bins: list[Path] = []

    try:
        for src_file, album_name in items_to_encrypt:
            dest_dir = get_or_create_vault_album(
                output_dir,
                album_name,
                master_key,
                album_cache=album_cache,
            )

            out_bin = encrypt_file(
                src_file,
                dest_dir,
                master_key,
                on_chunk_processed=lambda chunk_len, fn=src_file.name: progress.update(chunk_len, fn)
            )
            created_bins.append(out_bin)
            progress.complete_file()

        progress.finish()
        print(t(lang, "enc_success", count=len(created_bins)))
        if skipped_unsupported > 0:
            print(t(lang, "enc_skipped_summary", count=skipped_unsupported))
        print(t(lang, "out_dir_line", path=output_dir))
        if (output_dir / ".vault").is_file() or output_dir.name == "Cipher_Secrets":
            print(t(lang, "enc_tip_direct"))
        else:
            print(t(lang, "enc_tip1"))
            print(t(lang, "enc_tip2", app_dir=APP_DIR_NAME))

        delete_source = args.delete_source
        if not delete_source and sys.stdin.isatty():
            try:
                ans = input(t(lang, "wipe_prompt")).strip().lower()
                if ans in ("y", "yes", "e", "evet"):
                    delete_source = True
            except (KeyboardInterrupt, EOFError):
                pass

        if delete_source:
            print(t(lang, "wiping_start"))
            for src_file, _ in items_to_encrypt:
                try:
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
            phone_dest = f"/storage/emulated/0/{APP_DIR_NAME}/Cipher_Secrets/"
            print(t(lang, "adb_pushing", dest=phone_dest))
            res = subprocess.run(["adb", "devices"], capture_output=True, text=True)
            if "device\n" not in res.stdout:
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
        if master_key:
            master_key = bytearray(len(master_key))

def main():
    desktop = get_desktop_dir()

    parser = argparse.ArgumentParser(
        description="Fossify Gallery Cipher - Linux Offline Vault Encryptor",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
SECURITY NOTICE (Live Boot / RAM-Only Execution):
  Always prefer a RAM-only Live Boot OS such as Tails when handling sensitive plaintext media.
  Standard disk-backed systems can leave traces of plaintext files in
  filesystem journals, swap/pagefile, and SSD blocks even after deletion.

Desktop Usage:
  Encrypt (English: To_Encrypt -> Vault or Encrypted_Output | Türkçe: Sifrelenecek -> Kasa veya Sifreli_Cikti):
     python3 cipher_vault.py --lang en
  Direct external vault example:
     python3 cipher_vault.py --lang en -i ~/Desktop/To_Encrypt -o /media/amnesia/USB/Cipher_Secrets -v /media/amnesia/USB/Cipher_Secrets/.vault
        """
    )
    parser.add_argument("-e", "--encrypt", action="store_true", help="Encrypt plaintext media into .bin format (default)")

    parser.add_argument("--lang", choices=["en", "tr"], help="CLI language and Desktop folder language (en or tr)")
    parser.add_argument("-v", "-k", "--vault", help="Path to .vault file (Searched automatically across mounted vaults/USB/desktop if omitted)")
    parser.add_argument("-i", "--input", help="Input folder/file")
    parser.add_argument("-o", "--output", help="Output folder (or target Cipher_Secrets vault directory)")
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

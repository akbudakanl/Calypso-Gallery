"""
Protocol constants, media extension sets, and English CLI messages for Calypso Vault CLI.
"""

# On-disk names used by the Android app (Kotlin Constants.kt).
APP_DIR_NAME = "Calypso Gallery"
VAULT_DIR_NAME = "Secrets"
VAULT_DIR_NAMES = (VAULT_DIR_NAME,)
CLI_DIR_NAME = "CLI"
CLI_BRIDGE_ARCHIVED_DIR_NAME = "Archived"
LOCAL_BRIDGE_DIR_NAME = "Calypso CLI"
CLI_BRIDGE_MARKER_FILE = "cli_bridge"
VAULT_HEADER_FILE_NAME = "vault"
CLI_WRITING_SENTINEL = "cli_writing"

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

# ==================== MESSAGES (ENGLISH ONLY) ====================

INPUT_FOLDER_NAME = "To_Encrypt"

MESSAGES = {
    "op_cancelled": "\nOperation cancelled.",
    "vault_not_found": "\033[1;33m[?] vault header was not found automatically.\033[0m",
    "vault_drop_hint": "    Export it from the app: {app_dir} > Secrets > vault",
    "vault_path_hint": "    then enter its path below (or pass -v on the command line).",
    "vault_enter_path": "\n\033[1;36m-> Enter path to vault header (or drag & drop here): \033[0m",
    "vault_invalid_path": "\033[1;31m[-] Error: Valid vault header not found at: {path}\033[0m",
    "vault_spec_not_found": "\033[1;31m[ERROR]\033[0m Specified vault header not found: {path}",
    "vault_auto_detected": "\033[1;32m[✓] vault header auto-detected:\033[0m {path}",
    "enter_secret": "\033[1;33m[?] Enter Vault Password or PIN: \033[0m",
    "secret_empty": "\033[1;31m[ERROR]\033[0m Password cannot be empty!",
    "unwrapping_vault": "\033[1;34m[*] Unwrapping vault header (Argon2id + AES-256-GCM)...\033[0m",
    "vault_unlocked": "\033[1;32m[✓] Vault unlocked successfully! Master Key loaded into memory.\033[0m\n",
    "vault_unlock_err": "\033[1;31m[ERROR]\033[0m Could not unlock vault: {err}",
    "vault_retry": "\033[1;33m[i] Please try again (Ctrl+C to cancel).\033[0m\n",
    "enc_title": "\033[1;36m      Calypso Gallery - Offline Vault Encryptor\033[0m",
    "non_ram_os_notice": "\033[1;33m[i] Standard OS Mode ({os_name}):\033[0m Running on a disk-backed OS (not RAM-only Tails/Live Boot). Swap, thumbnails, and filesystem logs are not cleared; best suited for encrypting media already on this PC.",
    "cloud_sync_warning": "\033[1;31m[WARNING]\033[0m Cloud-synced folder detected ({path})! Plaintext media or vault here may upload to OneDrive/iCloud/Dropbox. Prefer a non-synced local folder or USB drive (-i / -o).",
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
    "files_unit": "files",
    "skip_unsupported_fmt": "\033[1;33m[!] Skipping unsupported format (not a supported gallery media file):\033[0m {name}",
    "enc_skipped_summary": "\033[1;33m[i] Skipped {count} unsupported file(s).\033[0m",
    "enc_nested_flatten_note": "\033[1;34m[i] Nested subfolders under '{album}' will be routed into top-level vault album '{album}'.\033[0m",
    "enc_input_not_found": "\033[1;31m[ERROR]\033[0m Input path does not exist: {path}",
    "enc_auto_target_vault": "\033[1;32m[✓] Encryption destination:\033[0m {path}",
    "enc_tip_direct": "\033[1;37m[i] Encrypted directly inside the Vault. Existing .meta albums were reused and new albums created.\033[0m",
    "enc_tip_staging": "\n\033[1;37m[i] Encrypted into a staging folder. In the app use Settings > Encrypted vault >\033[0m\n"
                      "\033[1;37m    Import CLI media to move it into your active vault (or copy this folder\033[0m\n"
                      "\033[1;37m    to Calypso Gallery/CLI on the phone over USB first).\033[0m",
    "select_active_hint": "\033[1;33m[i]\033[0m Select the vault that is currently active in the app; otherwise the transfer will fail.\n",
    "vault_multiple_found": "\033[1;31m[ERROR]\033[0m Multiple vault headers found. Pass -v <path> to choose one:\n{paths}",
    "cli_writing_busy": "\033[1;31m[ERROR]\033[0m Staging folder is busy ({path}). Another encryption may be in progress.\n"
                        "    If no other run is active, delete the cli_writing file and retry.",
    "vault_last_used": "\033[1;32m[✓] Using last selected vault:\033[0m {path}",
}


def t(key: str, **kwargs) -> str:
    msg = MESSAGES.get(key, key)
    return msg.format(**kwargs) if kwargs else msg


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

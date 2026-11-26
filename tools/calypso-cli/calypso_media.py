"""
Media validation, EXIF/thumbnail generation, progress UI, and streaming file encryption.
"""

import os
import sys
import time
import secrets
import struct
from datetime import datetime
from pathlib import Path
from io import BytesIO

try:
    from PIL import Image
    HAS_PIL = True
except ImportError:
    HAS_PIL = False

from calypso_i18n import (
    CONTAINER_MAGIC,
    CONTAINER_VERSION,
    DEFAULT_ALBUM_RAW_NAMES,
    GIF_EXTS,
    PHOTO_EXTS,
    RAW_EXTS,
    SVG_EXTS,
    TYPE_GIFS,
    TYPE_IMAGES,
    TYPE_RAWS,
    TYPE_SVGS,
    TYPE_VIDEOS,
    VIDEO_EXTS,
    t,
)
from calypso_crypto import (
    VaultAlbumCache,
    VaultKeys,
    _KeepOpenBytesIO,
    _tink_stream_primitive,
    derive_header_key,
    get_or_create_vault_album,
)

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
    def __init__(self, total_files: int, total_bytes: int):
        self.total_files = total_files
        self.total_bytes = max(total_bytes, 1)
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
        files_unit = t("files_unit")
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
    Appends newly encrypted .bin relative POSIX paths to the staging manifest
    `pending_sync`. The name has no leading dot so it survives MTP transfers.
    Existing entries from earlier CLI sessions are preserved and deduplicated.
    """
    if not created_bins:
        return None
    manifest_file = output_dir / "pending_sync"

    existing: list[str] = []
    seen: set[str] = set()
    if manifest_file.is_file():
        try:
            for line in manifest_file.read_text(encoding="utf-8").splitlines():
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

    temp_file = manifest_file.with_name(manifest_file.name + f".{os.getpid()}.tmp")
    temp_file.write_text("\n".join(existing) + "\n", encoding="utf-8")
    os.replace(temp_file, manifest_file)
    return manifest_file


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

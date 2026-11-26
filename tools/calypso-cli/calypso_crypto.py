"""
Header KDF, Tink AES256_GCM_HKDF streaming primitives, and encrypted .meta album metadata helpers.
"""

import os
import sys
import secrets
import struct
from dataclasses import dataclass, field
from pathlib import Path
from io import BytesIO

from calypso_i18n import (
    ACTIVE_KEY_ID,
    CONTAINER_MAGIC,
    CONTAINER_VERSION,
    DEFAULT_ALBUM_RAW_NAMES,
    DOMAIN_HEADER_KEY,
    DOMAIN_METADATA_KEY,
    FOLDER_META_MAGIC,
    GCM_TAG_SIZE,
    HKDF_EMPTY_SALT,
    SEGMENT_SIZE,
    VAULT_DEFAULT_ALBUM_NAME,
    VAULT_HEADER_MAGIC,
    VAULT_RECYCLE_BIN_NAME,
)

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

def install_network_audit_hook():
    """
    Defense-in-depth Python-level guard: blocks network operations, subprocess
    execution and dynamic code loading inside this process. The CLI is entirely
    offline and must never spawn helper processes or open sockets.
    """
    if not hasattr(sys, "addaudithook"):
        return

    blocked_events = {
        "socket.connect", "socket.bind", "socket.sendto", "socket.getaddrinfo",
        "urllib.Request", "http.client.connect",
        "subprocess.Popen", "os.system", "os.exec", "os.spawn",
        "ctypes.dlopen", "ctypes.dlsym",
    }

    def _audit_hook(event, _args):
        if event in blocked_events or event.startswith(("socket.", "subprocess.")):
            raise PermissionError(f"Operation blocked by offline security policy: {event}")

    sys.addaudithook(_audit_hook)

class VaultKeys:
    """The two keys held by a canonical vault keyring."""
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
    """Per-record key for FVSTR: vault strings.

    Android passes an already domain-prefixed context ("domain|purpose") into
    VaultKeyDerivation.deriveMetadataKey, which prepends the domain once more. The actual HKDF
    info is therefore "domain|domain|purpose", while GCM AAD stays single-prefixed. This helper
    mirrors that behaviour; it currently has no CLI call sites (the CLI never reads FVSTR:).
    """
    info = (DOMAIN_METADATA_KEY + "|" + DOMAIN_METADATA_KEY + "|" + context).encode("utf-8")
    return _hkdf_sha256(kek, HKDF_EMPTY_SALT, info)


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
    Unwraps a canonical v1 vault header.

    Layout: magic(5) | version(1) | kdf(1) | iterations(4) | memoryKb(4) |
    parallelism(4) | protectionType(1) | saltLen(4) | salt | ivPdk(12) |
    wrappedVmKLen(4) | wrappedVMK | ivVmk(12) | keyringLen(4) | wrappedKeyring.

    PDK = Argon2id(secret, salt, ...) unwraps the VMK; the VMK unwraps the keyring,
    which is packKeyringV4 = u16 keyId | 32-byte KEK.
    """
    if len(data) < 32:
        raise ValueError("vault header is too small or invalid.")

    offset = 0

    def take(count: int) -> bytes:
        nonlocal offset
        if count < 0 or offset + count > len(data):
            raise ValueError("Corrupted vault header: truncated field.")
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
        raise ValueError("Invalid Argon2id parameters in vault header.")

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
    """Unwraps a vault header and returns the KEK (the file-header key)."""
    return unlock_vault_header_bytes(vault_path.read_bytes(), secret)


def unlock_vault_header_bytes(data: bytes, secret: str) -> bytes:
    """Unwraps raw vault header bytes and returns the KEK used for file headers."""
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

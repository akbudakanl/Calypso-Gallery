"""
Interactive/CLI encryption workflow and target vault/staging selection.
"""

import os
import sys
import secrets
import getpass
import subprocess
from pathlib import Path

from calypso_i18n import (
    APP_DIR_NAME,
    CLI_DIR_NAME,
    CLI_WRITING_SENTINEL,
    DEFAULT_SECRET_ALBUM_NAME,
    INPUT_FOLDER_NAME,
    VAULT_DEFAULT_ALBUM_NAME,
    VAULT_DIR_NAME,
    VAULT_HEADER_FILE_NAME,
    t,
)
from calypso_crypto import (
    VaultAlbumCache,
    VaultKeys,
    scan_existing_vault_albums,
    unlock_vault_header,
)
from calypso_media import (
    HorizontalProgressBar,
    append_pending_sync_entries,
    encrypt_file,
    is_supported_media_file,
)

def _load_cv():
    import calypso_vault as _cv
    return _cv


def unlock_master_key_with_path(
    args,
    extra_vault_paths: list[Path] | None = None,
) -> tuple[VaultKeys, Path | None]:
    """
    Locates vault on disk, prompts for password/PIN (with retry on interactive input),
    and unwraps both keys in the keyring. Returns (VaultKeys, vault_path_or_none).
    """
    vault_data: bytes | None = None
    vault_path: Path | None = None

    if argsvault:
        candidate = Path(argsvault).expanduser().resolve()
        if candidate.is_dir() and (candidate / VAULT_HEADER_FILE_NAME).is_file():
            candidate = candidate / VAULT_HEADER_FILE_NAME
        if not candidate.is_file():
            print(t("vault_spec_not_found", path=candidate), file=sys.stderr)
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
            vault_path = _load_cv().read_last_vault_path()
            if vault_path:
                print(t("vault_last_used", path=vault_path))

        if not vault_path:
            candidates = _load_cv().find_vault_files()
            if len(candidates) > 1:
                active = [c for c in candidates if _bridge_is_active(c)]
                if len(active) == 1:
                    candidates = active
                else:
                    listing = "\n".join(f"  - {c}" for c in candidates)
                    print(t("vault_multiple_found", paths=listing), file=sys.stderr)
                    print(t("select_active_hint"), file=sys.stderr)
                    sys.exit(1)
            vault_path = candidates[0] if candidates else None

        if vault_path:
            print(t("vault_auto_detected", path=vault_path))
            vault_data = vault_path.read_bytes()
        else:
            vault_path = _load_cv().prompt_for_vault_file()
            vault_data = vault_path.read_bytes()

    _load_cv().remember_vault_path(vault_path)

    cli_secret = args.secret
    if not cli_secret and args.pattern:
        cli_secret = args.pattern.strip()

    if cli_secret:
        print(t("unwrapping_vault"))
        try:
            vault_keys = unwrap_vault_keys(vault_data, cli_secret)
            print(t("vault_unlocked"))
            return vault_keys, vault_path
        except Exception as e:
            print(t("vault_unlock_err", err=e), file=sys.stderr)
            sys.exit(1)

    max_attempts = None if sys.stdin.isatty() else 5
    attempt = 0
    while True:
        attempt += 1
        try:
            secret = getpass.getpass(t("enter_secret"))
        except (KeyboardInterrupt, EOFError):
            print(t("op_cancelled"))
            sys.exit(1)

        if not secret:
            print(t("secret_empty"), file=sys.stderr)
            if max_attempts is not None and attempt >= max_attempts:
                sys.exit(1)
            print(t("vault_retry"), file=sys.stderr)
            continue

        print(t("unwrapping_vault"))
        try:
            vault_keys = unwrap_vault_keys(vault_data, secret)
            print(t("vault_unlocked"))
            return vault_keys, vault_path
        except Exception as e:
            print(t("vault_unlock_err", err=e), file=sys.stderr)
            if max_attempts is not None and attempt >= max_attempts:
                sys.exit(1)
            print(t("vault_retry"), file=sys.stderr)

def unlock_master_key(
    args,
    extra_vault_paths: list[Path] | None = None,
) -> VaultKeys:
    """Locates vault on disk, prompts for password/PIN, and unwraps both vault keys."""
    vault_keys, _ = unlock_master_key_with_path(
        args,
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
    files) rather than a standalone vault copied onto Desktop, Home, input_path, or
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

def _nearest_mount(path: Path) -> Path:
    """Closest mount point at or above path (drive root on Windows)."""
    cur = path
    while True:
        try:
            if cur.is_mount():
                return cur
            if cur == Path(cur.anchor):
                return cur
        except Exception:
            pass
        parent = cur.parent
        if parent == cur:
            return cur
        cur = parent


def derive_staging_dir(vault_path: Path, desktop: Path, input_path: Path) -> Path:
    """
    Derives the CLI staging folder from the *exported vault location* without
    assuming any particular vault folder name (the active phone vault may live
    at a custom path and there may be several vaults).

    Rule: put `CLI` next to the folder that holds the vault header, except when
    that folder is itself a filesystem/USB mount root, Desktop, Home, the input
    folder or the script folder — then put `CLI` inside it.

    Examples (no `Secrets` assumption):
      <USB>/Calypso Gallery/Secrets/vault  -> <USB>/Calypso Gallery/CLI
      <USB>/MyCustomVault/vault            -> <USB>/CLI
      <USB>/vault                          -> <USB>/CLI
      ~/Desktop/vault                      -> ~/Desktop/CLI
    """
    vdir = vault_path.parent.resolve()
    # A bridge folder is its own staging directory: write next to the header.
    if any(part == CLI_DIR_NAME or part == LOCAL_BRIDGE_DIR_NAME for part in vdir.parts):
        return vdir
    mount = _nearest_mount(vdir)
    special = {
        desktop.resolve(),
        Path.home().resolve(),
        input_path.resolve(),
        Path(__file__).resolve().parent.resolve(),
        mount,
    }
    for root in _load_cv()._external_mount_roots():
        try:
            special.add(root.resolve())
        except Exception:
            pass
    if vdir in special:
        return vdir / CLI_DIR_NAME
    return vdir.parent / CLI_DIR_NAME


def choose_encrypt_output_dir(
    args,
    desktop: Path,
    input_path: Path,
    vault_path: Path | None,
) -> Path:
    """
    Determines the staging directory for encrypted output:

    1. Explicit -o / --output path wins.
    2. Otherwise derive a sibling `CLI` folder from the exported vault path
       (see derive_staging_dir) — independent of the folder name.
    3. Last-resort Desktop fallback (Encrypted_Output) when no vault is known.

    Encrypted output is NEVER written into the folder that holds the vault
    header or into any vault album folder.
    """
    if args.output:
        target_dir = Path(args.output).expanduser().resolve()
    elif vault_path is not None:
        target_dir = derive_staging_dir(vault_path, desktop, input_path)
    else:
        target_dir = (desktop / OUTPUT_FOLDER_NAME).resolve()

    target_dir.mkdir(parents=True, exist_ok=True)
    print(t("enc_auto_target_vault", path=target_dir))
    return target_dir

def run_encrypt_mode(args, desktop: Path):
    platform_kind = _load_cv().get_platform_kind()
    if platform_kind in ("windows", "macos"):
        print(t("non_ram_os_notice", os_name=_load_cv().get_platform_display_name()))

    input_path = Path(args.input).expanduser().resolve() if args.input else get_desktop_folder(desktop, "enc_in")
    if args.input:
        if not input_path.exists():
            print(t("enc_input_not_found", path=input_path), file=sys.stderr)
            sys.exit(1)
    else:
        input_path.mkdir(parents=True, exist_ok=True)

    if _load_cv().is_cloud_synced_path(input_path):
        print(t("cloud_sync_warning", path=input_path))

    items_to_encrypt: list[tuple[Path, str | None]] = []
    skipped_unsupported = 0
    nested_subfolders_seen: set[str] = set()

    if input_path.is_file():
        fname = input_path.name
        if fname.lower().endswith(".bin") or fname.startswith("."):
            pass
        elif not is_supported_media_file(fname):
            print(t("skip_unsupported_fmt", name=fname))
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
                print(t("skip_unsupported_fmt", name=str(rel)))
                skipped_unsupported += 1
                continue
            album_name = rel.parts[0] if len(rel.parts) > 1 else None
            if len(rel.parts) > 2 and album_name:
                nested_subfolders_seen.add(album_name)
            items_to_encrypt.append((p, album_name))

    if not items_to_encrypt:
        display_out = Path(args.output).expanduser().resolve() if args.output else get_desktop_folder(desktop, "enc_out")
        print("\n" + "=" * 65)
        print(t("enc_title"))
        print("=" * 65)
        print(t("folders_ready"))
        print(t("input_folder", path=input_path))
        print(t("output_folder", path=display_out))
        print(t("enc_empty"))
        print(t("enc_empty_hint1"))
        print(t("enc_empty_hint2"))
        if skipped_unsupported > 0:
            print(t("enc_skipped_summary", count=skipped_unsupported))
        print("=" * 65 + "\n")
        sys.exit(0)

    vault_keys, vault_path = unlock_master_key_with_path(args)
    vmk = vault_keys.vmk   # protects folder .meta files
    kek = vault_keys.kek   # protects file container headers
    if vault_path is not None and _load_cv().is_cloud_synced_path(vault_path):
        print(t("cloud_sync_warning", path=vault_path))

    output_dir = choose_encrypt_output_dir(args, desktop, input_path, vault_path)
    output_dir.mkdir(parents=True, exist_ok=True)
    if _load_cv().is_cloud_synced_path(output_dir):
        print(t("cloud_sync_warning", path=output_dir))

    nomedia = output_dir / ".nomedia"
    if not nomedia.exists():
        try:
            nomedia.touch()
        except Exception:
            pass

    for album in sorted(nested_subfolders_seen):
        print(t("enc_nested_flatten_note", album=album))

    total_bytes = sum(f.stat().st_size for f, _ in items_to_encrypt)
    print(t("enc_queued", count=len(items_to_encrypt), mb=total_bytes / (1024 * 1024)))

    album_cache = scan_existing_vault_albums(output_dir, vmk)
    progress = HorizontalProgressBar(len(items_to_encrypt), total_bytes)
    created_bins: list[Path] = []
    sentinel = output_dir / CLI_WRITING_SENTINEL
    try:
        from datetime import datetime, timezone
        sentinel.write_text(datetime.now(timezone.utc).isoformat() + "\n", encoding="utf-8")
    except Exception:
        pass

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
        print(t("enc_success", count=len(created_bins)))
        if skipped_unsupported > 0:
            print(t("enc_skipped_summary", count=skipped_unsupported))
        print(t("out_dir_line", path=output_dir))
        print(t("enc_tip_staging"))

        delete_source = args.delete_source
        shown_wipe_disclaimer = False
        if not delete_source and sys.stdin.isatty():
            if platform_kind in ("windows", "macos"):
                print("\n" + t("wipe_non_ram_disclaimer", os_name=_load_cv().get_platform_display_name()))
                shown_wipe_disclaimer = True
            try:
                ans = input(t("wipe_prompt")).strip().lower()
                if ans in ("y", "yes"):
                    delete_source = True
            except (KeyboardInterrupt, EOFError):
                pass

        if delete_source:
            if platform_kind in ("windows", "macos") and not shown_wipe_disclaimer:
                print("\n" + t("wipe_non_ram_disclaimer", os_name=_load_cv().get_platform_display_name()))
            print(t("wiping_start"))
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
                    print(t("wipe_err", name=src_file.name, err=e))

            if input_path.is_dir():
                for d in sorted(input_path.rglob("*"), key=lambda p: len(str(p)), reverse=True):
                    if d.is_dir() and d != input_path:
                        try:
                            d.rmdir()
                        except Exception:
                            pass
            print(t("wiping_done"))

    finally:
        try:
            sentinel.unlink(missing_ok=True)
        except Exception:
            pass
        vmk = bytearray(len(vmk))
        kek = bytearray(len(kek))

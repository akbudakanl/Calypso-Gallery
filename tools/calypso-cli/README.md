# Calypso Gallery — Offline Vault Encryptor CLI

This **encrypt-only** offline CLI encrypts plaintext media (photos, GIFs, SVGs, RAW files, videos) on the computer with your phone vault's own key, so only ciphertext ever reaches the phone. It writes the canonical `FGVLTENC` v1 container: an AES-256-GCM encrypted metadata header (key = HKDF-SHA256 of the vault KEK bound to the file ID) followed by a Tink-compatible `AES256_GCM_HKDF` streaming-AEAD payload (256 KiB segments, file ID as associated data). Album `.meta` files use the canonical `FGMETA` format.

---

## 📖 User manuals — start here

The end-to-end workflow is documented in the user manuals. Read the one for your platform before running the CLI:

| Platform | English | Türkçe |
|---|---|---|
| Windows & Linux | [user-manual-EN.md](../../docs/manuals/windows-linux/user-manual-EN.md) | [kullanim-kilavuzu-TR.md](../../docs/manuals/windows-linux/kullanim-kilavuzu-TR.md) |
| macOS | [user-manual-EN.md](../../docs/manuals/macos/user-manual-EN.md) | [kullanim-kilavuzu-TR.md](../../docs/manuals/macos/kullanim-kilavuzu-TR.md) |

The rest of this file documents the container format, the bridge-folder contract and lower-level details.

---

## How the bridge works

1. When a vault is created (or when the import row is used), the app automatically creates `Calypso Gallery/CLI/<vault-id>/` for the active vault. It contains only the vault header (`vault`) and a `cli_bridge` marker with the vault path, an `active` flag and a KEK fingerprint.
2. Put the media to encrypt in `~/Desktop/To_Encrypt`.
3. On **Windows and Linux**, `run.bat` / `run.sh` copy the bridge into a temporary working directory, run the CLI there, copy the result back to the phone and delete the temporary directory. On **macOS**, the bridge folder is copied manually with OpenMTP (the CLI itself stays filesystem-based).
4. The CLI writes encrypted `.bin` files, album `.meta` files and a `pending_sync` list into the bridge folder — never into the vault.
5. In the app, **Settings → Import CLI / external encrypted media** moves all pending files into the active vault and cleans the bridge only after a completely successful import.

Bridge folder layout:

```
<vault-id>/
├── vault                  # copy of the vault key header (never modified by the CLI)
├── cli_bridge             # vault path, active flag, KEK fingerprint
├── pending_sync           # relative paths of newly encrypted .bin files
├── cli_writing           # present only while encryption is running
└── <hex>/ .meta + *.bin   # one folder per vault album
```

With more than one bridge, the one marked `active=1` is selected automatically. If the flag is missing or ambiguous, the CLI shows a numbered menu with **only the vault path** per line. `-v` skips selection entirely. Plaintext is never written to the phone.

`pending_sync` is append-only across CLI sessions: an earlier, not-yet-imported batch is preserved and imported together with new files. Files already in the vault are skipped by content comparison.

---

## Platform and privacy notes

### RAM-only Linux (maximum forensic privacy)

Running from a RAM-only live Linux distribution keeps plaintext strictly in volatile storage (`tmpfs`/`overlayfs`); internal disks and swap are not mounted, and RAM is wiped on shutdown.

**Tails** is the primary recommendation — it runs entirely in RAM, never mounts internal drives or swap automatically and wipes RAM on shutdown. It uses `apt` and supports Persistent Storage / Additional Software. Connect the phone by USB while the Tails screen is unlocked; Tails 6+ ignores USB devices plugged in while the screen is locked.

Other RAM-only Linux live ISOs can be booted with copy-to-RAM and no-swap parameters:

| Distribution | Boot parameters | Package manager |
|---|---|---|
| Debian Live | `boot=live toram noswap` | `apt` |
| Ubuntu Live | `toram noswap` | `apt` |
| Fedora Live | `rd.live.ram=1` | `dnf` |
| Arch / EndeavourOS Live | `copytoram=y` | `pacman` |
| openSUSE Live | `rd.live.ram=1` | `zypper` |

### Disk-backed Windows, macOS and Linux

Encrypting media already stored on a disk-backed computer is convenient, but deletion cannot remove forensic traces from filesystem journals, swap/pagefile, thumbnail and search caches, shadow copies/snapshots or SSD wear-leveling blocks — even with `--delete-source`. Cloud-synced Desktop folders (OneDrive, iCloud Drive, Dropbox) may also upload staged plaintext; use a non-synced local folder or USB drive for the input.

---

## Built-in per-process isolation

* **Linux (`run.sh`):** wraps **only this single `calypso_vault.py` process**. If `bwrap` is missing, the launcher first tries to install it with the detected package manager (`apt`, `dnf`, `pacman`, `zypper`) after asking for confirmation; a failed install simply leaves the next fallback in place.
  1. **`bwrap` (bubblewrap, preferred):** `--unshare-net`, `--unshare-ipc`, `--unshare-pid`, `--die-with-parent`, `--clearenv`, `--tmpfs /tmp` and **seccomp** (`--seccomp 21`). Filesystem access is limited to system libraries, the CLI directory, the Desktop, mounted drives and CLI-specified `-i` / `-v` paths.
  2. **`firejail`** (`--net=none --private-tmp`) if `bwrap` is unavailable.
  3. **`unshare --user --net`** as a kernel network-namespace fallback.
  4. **No OS sandbox available → the launcher asks for confirmation.** It prints a warning and a `y/N` prompt and exits by default:

     ```
     Running with the Python audit hook only — no OS-level filesystem or
     network sandbox. No sandbox could be started on this system.
     Continue anyway? [y/N]:
     ```

     Only an explicit `y` continues without OS-level isolation; `N` or Enter aborts. No command-line flag or environment variable is involved. If this point is reached, the launcher has already tried and failed to install `bwrap` (or the kernel/user-namespace test failed). This covers hardened kernels and containers where user namespaces are disabled.

* **All platforms (`calypso_vault.py`):** **Python `sys.addaudithook`** blocks socket operations, subprocess execution (`subprocess.Popen`, `os.system`, `os.exec*`, `os.spawn*`) and dynamic library loading (`ctypes.dlopen`/`dlsym`). On macOS and Windows this is the only process-level guard; on Linux it is defence in depth on top of the OS sandbox.

The CLI is **English-only**. It performs no decryption and needs no network connection.

---

## Supported media and album routing

Before encrypting, every file is checked against the media extensions used by the Android app (`isSupportedMediaName`):

* **Photos:** `.jpg`, `.jpeg`, `.png`, `.webp`, `.heic`, `.heif`, `.apng`, `.avif`, `.bmp`, `.wbmp`, `.ico`, `.jxl`, `.jfif`, `.pjpeg`, `.pjp`
* **GIF & SVG:** `.gif`, `.svg`
* **RAW:** `.dng`, `.cr2`, `.cr3`, `.nef`, `.nrw`, `.arw`, `.srf`, `.sr2`, `.orf`, `.rw2`, `.pef`, `.raf`, `.srw`, `.x3f`, `.erf`, `.mef`, `.mos`, `.mrw`, `.dcr`, `.kdc`, `.3fr`, `.fff`, `.iiq`, `.rwl`
* **Videos:** `.mp4`, `.mkv`, `.webm`, `.avi`, `.3gp`, `.3gpp`, `.3g2`, `.mov`, `.m4v`, `.ts`, `.mts`, `.m2ts`, `.qt`, `.flv`, `.f4v`, `.wmv`, `.asf`, `.mpg`, `.mpeg`, `.mpe`, `.vob`, `.ogv`, `.rm`, `.rmvb`, `.divx`

Unsupported files (`.txt`, `.pdf`, `.md`, `.zip`), `.bin` files and hidden dot-files are skipped, logged and counted in the summary.

* **Root files → `Default Secret Album`** (`__VAULT_DEFAULT_ALBUM__`). An existing default album in the bridge is reused; otherwise a new randomized 6-character hex folder with encrypted `.meta` is created.
* **Subfolders → `.meta`-aware matching.** Files in `To_Encrypt/Vacation/img.jpg` go to the existing "Vacation" album (exact or case-insensitive trimmed match) or create a new one. Nested subfolders flatten to the top-level album name.
* `.nomedia` is placed at the bridge root, not inside album folders. Virtual albums in the app's `JSON/` folder are never touched.

The on-disk folder name is a random hex ID; the real album name is encrypted inside `.meta` and resolved by the app during import.

---

## CLI parameters

In normal use **no parameters are needed** — the launcher handles the bridge. For manual use:

| Parameter | Meaning |
|---|---|
| `-v`, `-k`, `--vault` | Path to a `vault` header file. Skips discovery and vault selection. |
| `-i`, `--input` | Input folder (default `~/Desktop/To_Encrypt`). |
| `-s`, `--secret` | Password on the command line. **Not recommended** — stays in shell history. |
| `--pattern` | 3×3 grid pattern indices (e.g. `0,1,2,5`). |
| `--delete-source` | Securely wipe source media after encryption. |
| `-e`, `--encrypt` | Encryption (default). |

Example for a manually copied bridge folder:

```bash
./run.sh -v "$HOME/Desktop/Calypso CLI/a1b2c3d4/vault"
```

---

## Known limitations / TODO

1. **Stale `cli_writing` marker — user-visible risk.** If the CLI is killed mid-batch (power loss, forced quit, shutdown), the "writing in progress" marker can remain even though nothing is writing. The app then refuses the import with **"bridge folder is busy"**. Today the fix is manual: delete the marker from `Calypso Gallery/CLI/<vault-id>/` and tap the import row again. It is the visible `cli_writing` file and contains a UTC start timestamp; no "show hidden files" step is needed. No encrypted data is lost — `pending_sync` is authoritative.

   In the automatic Windows/Linux flow this is very unlikely: the marker lives in the temporary directory and the folder is pushed to the phone only after the CLI finishes. It can happen in the manual macOS flow if the bridge is copied while the CLI is still writing.

   Implemented: the marker is `cli_writing` (visible, MTP-safe) with a UTC start timestamp. The app treats markers older than 30 minutes as stale and offers "Remove and continue" with explicit confirmation; recent markers keep the normal "busy" message, because a genuinely slow CLI must not lose its busy flag.

Resolved by the bridge design:

* **Wrong-key / wrong-vault detection** — the `cli_bridge` marker's KEK fingerprint is compared with the active vault before import; a mismatch is rejected with a clear message.
* **MTP dot-file handling** — `vault`, `cli_bridge` and `pending_sync` have no leading dot.
* **Cleanup scope** — only manifest-listed files and their empty album folders are removed after a successful import; `vault` and `cli_bridge` are preserved.
* **Incremental batches** — an unclean bridge is fully supported: new files get fresh randomized names and are appended to `pending_sync`; already-imported files are skipped by content comparison.

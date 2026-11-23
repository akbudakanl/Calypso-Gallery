# Calypso Gallery - Cross-Platform Offline Vault Encryptor CLI

This **Encrypt-Only** offline CLI tool (supporting **Linux / Tails OS, macOS, and Windows** with automatic OS detection) encrypts plaintext media files (photos, GIFs, SVGs, RAWs, videos) into `.bin` format **100% compatible with the Android Calypso Gallery** application. It writes the canonical `FGVLTENC` v1 container: an AES-256-GCM encrypted metadata header (key = HKDF-SHA256 of the vault KEK bound to the file ID) followed by a Tink-compatible `AES256_GCM_HKDF` streaming-AEAD payload (256 KiB segments, file ID as associated data). Album `.meta` files use the canonical `FGMETA` format.

> **Format:** this tool writes only the current canonical container format. The vault folder is `Secrets/` inside the app container `Calypso Gallery/` (the `Encrypted_Output` / `Sifreli_Cikti` desktop output folders are still supported as standalone sync sources by the app).

> A more detailed walkthrough is available in the [Extended workflow guide](#-extended-workflow-guide) section below.

---

## 🎯 What this is for

The CLI encrypts media **on the computer** with your phone vault's own key, so only ciphertext crosses over to the phone. This works the same way on **Windows, macOS and Linux**: your files stay where they are, are encrypted locally, and the encrypted version is added directly to the vault the app already uses — plaintext is never written to the phone's disk.

Two ways to deliver the ciphertext:

* `--adb` — pushes the `.bin`/`.meta` files straight into `/storage/emulated/0/Calypso Gallery/Secrets/` on the connected phone (over USB); or
* an external USB/SD drive holding `Secrets/`, followed by **Settings → Encrypted vault → Sync vault from external drive** in the app.

Only the small `.vault` key header from the phone (`Calypso Gallery/Secrets/.vault`) is needed on the desktop; no existing vault media has to be copied first (Scenario 2 in the workflows below).

### Special use case: RAM-only desktop

If the source itself must never touch a disk, run the same workflow from a RAM-only live OS such as **Tails** (or a `toram` Linux ISO):

1. Download the file straight into `~/Desktop/To_Encrypt` (a `tmpfs` path).
2. Encrypt in RAM, then deliver the ciphertext through either path above.
3. Optionally use `--delete-source` to zero-wipe the staged input.

In this mode plaintext exists only in RAM and disappears on shutdown — no unencrypted copy is left on the phone, the USB drive, or the computer's disk. This guarantee applies only to the RAM-only setup: on a disk-backed Windows/macOS/Linux host, swap, journals, thumbnail caches, search indexes and cloud sync may still retain traces of the source file (see Tier 2 below). The CLI's network guard is relaxed only for `--adb`, which is intended for a USB connection.

---

## 🛡️ Recommended Operating Systems (Tier 1 vs. Tier 2)

The CLI detects your operating system automatically with **no extra CLI prompts**:

### Tier 1 — Maximum Forensic Privacy: **RAM-Only Live Boot (Tails OS / `toram` Linux)**
Handling or deleting sensitive plaintext media on a standard disk-backed OS leaves forensic traces in filesystem journals, swap/pagefile, thumbnail/search caches, and SSD wear-leveling blocks—even after deletion.
* **Primary Recommendation — Tails OS:** Everything runs strictly in volatile RAM (`tmpfs`/`overlayfs`), internal drives and swap partitions are never mounted automatically, and RAM is wiped on shutdown. Uses `apt` (supports Tails Persistent Storage + Additional Software).
* **Alternatives (`toram` Linux Live ISOs):** Boot with **copy-to-RAM** and **no-swap** parameters:

| Distribution | Boot Parameters | Package Manager (`run.sh` auto-supported) |
| :--- | :--- | :--- |
| **Debian Live** | `boot=live toram noswap` | `apt` |
| **Ubuntu Live** | `toram noswap` | `apt` |
| **Fedora Live** | `rd.live.ram=1` | `dnf` |
| **Arch / EndeavourOS Live** | `copytoram=y` | `pacman` |
| **openSUSE Live** | `rd.live.ram=1` | `zypper` |

### Tier 2 — Standard / Convenience Mode: **Windows & macOS**
If you already have photos/videos stored on a Windows or macOS computer and want to encrypt them directly onto an external USB/SD `Secrets` vault (or push via `--adb`) without copying plaintext files onto your phone first:
* **Automatic adaptation:** `calypso_vault.py` automatically detects Windows drive letters (`D:\`, `E:\`, ...) or macOS `/Volumes/<USB>` mounts, enables Windows UTF-8/ANSI console support, and uses local `.venv` via `run.bat` (Windows) or `run.sh` (macOS).
* **Important caveats (shown automatically by the CLI):**
  * Standard disk-backed mode does **not** clear OS thumbnail caches (`Thumbs.db`, QuickLook), search indexes (Windows Search / Spotlight), swap/pagefile, NTFS/APFS journals, shadow copies/Time Machine snapshots, or SSD wear-leveling blocks—even with `--delete-source`.
  * **Cloud-sync warning (OneDrive / iCloud Drive):** If your `Desktop` is synced to OneDrive or iCloud, staging plaintext files or copying `.vault` onto the Desktop may upload them to the cloud. The CLI warns when a cloud-synced path is detected and skips auto-copying `.vault` to cloud-synced Desktops—prefer `-i` / `-o` on a local non-synced folder or external USB drive.

---

## 🔒 Built-In Per-Process Isolation

* **Linux (`run.sh`):** Automatically wraps **only this single `calypso_vault.py` process** using:
  1. **`bwrap` (`bubblewrap`):** Cuts off all network access (`--unshare-net`) and restricts filesystem access strictly to system libraries, your Desktop, and mounted external drives / CLI-specified folders (blocking `~/.ssh`, `~/.gnupg`, `~/.config`, etc.).
  2. **`firejail` (`--net=none`):** Used if `bwrap` is unavailable.
  3. **`unshare --user --net`:** Kernel-level network namespace isolation fallback available on almost all Linux distributions.
* **All OSes — Linux, macOS, Windows (`calypso_vault.py`):**
  4. **Python `sys.addaudithook`:** Defense-in-depth guard inside `calypso_vault.py` that blocks socket/network operations unless `--adb` is explicitly passed. *(Note: OS-level `bwrap`/`unshare` filesystem/network sandboxing is Linux-only.)*

---

## 💾 Unified `Secrets` External-Drive Workflows (Tails OS RAM-Only)

Both workflows use **one unified folder structure (`Secrets/`)** on your external USB/SD drive, with **no interactive output destination prompt** in `calypso_vault.py`:

### Workflow 1: Direct External Vault (`Secrets` directly on USB/SD)
Use your external USB/SD drive as the vault itself from Android without copying encrypted media into your phone's internal storage:

1. **Stage plaintext media in RAM:** Download or place your plaintext photos/videos into `~/Desktop/To_Encrypt` (or `~/Desktop/Sifrelenecek`), which lives purely in volatile RAM (`tmpfs`) on Tails / `toram` Live OS.
2. **Plug in your external USB/SD drive:** Mount the drive containing `Secrets/.vault` (e.g., `/media/amnesia/USB/Secrets/.vault` or `/mnt/.../Secrets/.vault`).
3. **Run `./run.sh`:** The CLI auto-detects `.vault`, prompts for your vault password/PIN/pattern, and **automatically writes** newly encrypted `.meta` (`FGMETA`) album folders and canonical `.bin` payloads directly into `<USB>/Secrets/` (with zero output prompt — or pass `-o` / `--output` to override).
4. **Done:** Optionally zero-wipe the RAM input files (`--delete-source` or interactive prompt), then connect the USB/SD drive directly to your phone.

### Workflow 2: Phone Vault + Unified `Secrets` External Sync
Keep your primary `Secrets` vault on your phone while encrypting new media inside Tails OS onto an external USB/SD drive, then merge them inside the Android app without manually copying files with a file manager:

1. **Prepare `Secrets/` on the external USB/SD drive (pick either setup):**
   * **Scenario 1 — Full Copy of `Secrets` on USB:** Copy your phone's `Secrets` folder (`.vault` + existing encrypted `<hex>/.meta` album folders) onto the USB drive.
   * **Scenario 2 — Header-Only `.vault` on USB (No Existing Vault Media on USB):** Create `Secrets/` on the USB drive and place **only** your small `.vault` key-header file inside it (`<USB>/Secrets/.vault`), with none of your phone's media copied onto the USB drive.
     * *(Tip: Even if you place `.vault` directly at the external drive root `<USB>/.vault`, `choose_encrypt_output_dir` automatically targets `<USB>/Secrets/`! By contrast, a standalone `.vault` copied onto `~/Desktop/.vault` or `~/` falls back to `~/Desktop/Encrypted_Output` / `~/Desktop/Sifreli_Cikti`, and `-o` / `--output` always lets you specify any explicit folder.)*
2. **Stage & encrypt new media in Tails OS (`./run.sh`):**
   * Place new plaintext files in `~/Desktop/To_Encrypt` (or `~/Desktop/Sifrelenecek`): files directly in the root route to **`Default Secret Album`**, while named subfolders (e.g., `To_Encrypt/Vacation/photo.jpg`) match existing `.meta` albums or create new encrypted ones.
   * Run `./run.sh` and enter your vault password/PIN/pattern. **No output destination prompt is shown** — `calypso_vault.py` unlocks `.vault` and automatically encrypts into `<USB>/Secrets/`. Optionally zero-wipe the RAM source files (`--delete-source` or interactive prompt).
3. **Sync into your phone inside the Android app:**
   * Connect the USB/SD drive to your phone, open **Calypso Gallery**, go to **Settings > Encrypted vault > Sync vault from external drive** (*"Settings > Encrypted vault > Sync vault from external drive"*), unlock your vault if prompted, and select `<USB>/Secrets` (or the USB root).
   * **Identical handling for both scenarios:** Whether your USB's `Secrets/` holds a full vault mirror (Scenario 1) or only `.vault` plus newly encrypted additions (Scenario 2), Android sync processes it identically. In Scenario 2, once syncing completes, you can delete the synced `.bin` files and album subfolders from `<USB>/Secrets/` while keeping only `.vault` for next time.
   * **Incremental `.pending_sync` Manifest (Zero Archive Re-Scan):**
     * After encrypting new media, `calypso_vault.py` **appends** the newly created `.bin` relative paths to `<output_dir>/.pending_sync` (preserving any earlier unsynced CLI batches).
     * When you run **Sync vault from external drive** on Android, the app **only** processes entries listed in `.pending_sync`—it does not scan older archive files (avoiding wasted I/O and false `Failed` warnings from bit-rot in unrelated archive files), removes successfully synced entries, and deletes `.pending_sync` once empty. Without `.pending_sync`, sync is rejected so the same batch is not re-synced twice.
     * **Note for Future Desktop Clients:** Any separate desktop application/client must maintain its **own** independent pending-sync queue (e.g., `.pending_sync_desktop`) rather than consuming or deleting `.pending_sync`, so files already synced to the phone remain pending for the desktop client (and vice versa).
   * **4-Stage Single-Pass Sync Optimization & Duplicate Checking:**
     * Unchanged mirrored `.bin` files already in the destination album skip in `O(1)` time (checking size + canonical header version) without decrypting headers or hashing gigabytes of video.
     * Incoming `.bin` files with distinct filenames stream-copy and verify the Tink AES-GCM-HKDF stream authentication in a **single pass**, lazily matching or creating `.meta` albums and routing root files to `Default Secret Album`.
     * Same-name / same-size filename collisions verify the authenticated stream and compare decrypted content in a **single pass** (or 5 distributed `256 KB` checkpoints for `>8 MB` files when **Fast duplicate check** is enabled in Android Settings): identical plaintext (even across different random file IDs, DEKs and nonces) skips cleanly, while differing content is preserved as `<name>(1)`, `<name>(2)`, etc.

---

## 📁 Supported Media Verification & Folder Routing

### Pre-Encryption Supported Media Check
Before encrypting, `calypso_vault.py` checks every file against the exact gallery-supported media extensions used by Android `CryptoManager` (`isSupportedMediaName`):
* **Photos:** `.jpg`, `.jpeg`, `.png`, `.webp`, `.heic`, `.heif`, `.apng`, `.avif`, `.bmp`, `.wbmp`, `.ico`, `.jxl`, `.jfif`, `.pjpeg`, `.pjp`
* **GIF & SVG:** `.gif`, `.svg`
* **RAW:** `.dng`, `.cr2`, `.cr3`, `.nef`, `.nrw`, `.arw`, `.srf`, `.sr2`, `.orf`, `.rw2`, `.pef`, `.raf`, `.srw`, `.x3f`, `.erf`, `.mef`, `.mos`, `.mrw`, `.dcr`, `.kdc`, `.3fr`, `.fff`, `.iiq`, `.rwl`
* **Videos:** `.mp4`, `.mkv`, `.webm`, `.avi`, `.3gp`, `.3gpp`, `.3g2`, `.mov`, `.m4v`, `.ts`, `.mts`, `.m2ts`, `.qt`, `.flv`, `.f4v`, `.wmv`, `.asf`, `.mpg`, `.mpeg`, `.mpe`, `.vob`, `.ogv`, `.rm`, `.rmvb`, `.divx`

Unsupported files (such as `.txt`, `.pdf`, `.md`, `.zip`) as well as `.bin` files and hidden files starting with `.` are skipped before encryption, logged with a warning, and counted in the summary.

### Automatic Album Routing & `.meta` Matching
* **Root Files → `Default Secret Album`:** Files placed directly in the input folder root (no subfolder) are routed automatically into **`Default Secret Album`** (`__VAULT_DEFAULT_ALBUM__`). If your vault already has a Default Secret Album folder, it is reused; otherwise, a new randomized 6-character hex subfolder with `.meta` (`FGMETA`) is created.
* **Subfolders → `.meta`-Aware Album Matching:** For files inside subfolders (e.g., `To_Encrypt/Vacation/img.jpg`), the CLI decrypts existing `.meta` files in the target vault:
  * If an encrypted album with that name already exists (exact or case-insensitive trimmed match), new `.bin` files are placed directly into that existing hex folder.
  * If no matching album exists yet, a new randomized 6-character hex folder with encrypted `.meta` (`FGMETA`) is created.
  * `.nomedia` is placed at the vault root (`Secrets/.nomedia`), **not** inside normal album subfolders, matching Android's `ExcludedMenuPolicy` so your albums appear normally in the unlocked vault. (Only explicitly dot-prefixed hidden albums like `.Private` receive an inner `.nomedia`.)
  * Nested subfolders (e.g., `To_Encrypt/Vacation/Day1/img.jpg`) flatten cleanly into their top-level album name (`Vacation`) so all vault folders stay 1 level deep inside `Secrets/`.
* **Mixed Inputs:** Having some files in the input root and others in subfolders works seamlessly in a single pass.
* **Virtual Albums (`JSON/`) Are Independent:** Virtual Albums stored in `Secrets/JSON/` are completely untouched and disconnected from `calypso_vault.py` — they are managed solely inside the Android app.

---

## 🌐 Language & Automatic Output Routing

On interactive launch, the tool asks you to choose **English** or **Türkçe** (or pass `--lang en` / `--lang tr`), and uses matching localized folders without prompting for an output destination:

| Language Flag | Default Input Folder | Automatic Output Routing (`choose_encrypt_output_dir`) |
| :--- | :--- | :--- |
| `--lang en` | `~/Desktop/To_Encrypt` | `<USB>/Secrets` (for mounted `Secrets/.vault` or `<USB>/.vault`); falls back to `~/Desktop/Encrypted_Output` if `.vault` is standalone on Desktop/Home (`-o` overrides) |
| `--lang tr` | `~/Desktop/Sifrelenecek` | `<USB>/Secrets` (for mounted `Secrets/.vault` or `<USB>/.vault`); `~/Desktop/Sifreli_Cikti` if `.vault` is standalone in Desktop/Home (customizable with `-o`) |

---

## 🚀 Quick Start & CLI Examples

1. **Mount USB `Secrets/` (Full Vault or Header-Only `.vault`):** Plug in your external USB/SD drive containing `Secrets/.vault` (either a full vault copy or just `.vault`), or copy `.vault` to `~/Desktop/.vault` from your phone (`Internal Storage > Calypso Gallery > Secrets > .vault`).
2. **Run Launcher (auto-detects your OS with no extra prompts):**
   * **Linux / Tails / macOS:**
     ```bash
     chmod +x run.sh calypso_vault.py
     ./run.sh
     ```
     *(On Linux: auto-detects `apt`/`dnf`/`pacman`/`zypper`, creates the Desktop shortcut, and starts the sandboxed CLI. On macOS: creates a local `.venv` if needed and activates the Python network audit hook.)*
   * **Windows (double-click `run.bat` or run from Command Prompt / PowerShell):**
     ```bat
     run.bat
     ```
     *(Auto-detects Python 3.10-3.13, sets up `.venv` if dependencies are missing, scans mounted USB drive letters `D:\`–`Z:\`, and runs with UTF-8 + ANSI color support.)*

```bash
# Interactive mode (prompts for Language, .vault secret with retry, and optional wipe; no OS or output prompt)
./run.sh          # Linux / macOS
run.bat           # Windows

# English or Turkish interactive folder workflow (-e/--encrypt is optional default)
./run.sh --lang en

# Auto-target <USB>/Secrets directly (Linux / macOS / Windows)
./run.sh --lang en -i ~/Desktop/To_Encrypt -v /media/amnesia/USB/Secrets/.vault
./run.sh --lang en -i ~/Desktop/To_Encrypt -v /Volumes/USB/Secrets/.vault
run.bat  --lang en -i C:\LocalMedia\To_Encrypt -v E:\Secrets\.vault

# Explicit custom -o override (for example writing to a custom USB transfer folder)
./run.sh --lang en -i ~/Desktop/To_Encrypt -o /media/amnesia/USB/Custom_Output -v /media/amnesia/USB/Secrets/.vault

# Specify .vault (-v or -k), 3x3 pattern indices, and wipe plaintext sources after encryption
./run.sh -v ~/.vault --pattern "0,1,2,5" --delete-source

# Encrypt locally, then push only the encrypted .bin/.meta files into the phone's
# Secrets vault via ADB (USB) - no plaintext is written to the phone's disk
# (the CLI's network guard is relaxed for this mode only)
./run.sh --adb
```

---

## 📖 Extended workflow guide

This **encrypt-only (Encrypt-Only)** offline CLI tool (which automatically detects the operating system on **Linux / Tails OS, macOS, and Windows**) encrypts your media files (photos, GIFs, SVGs, RAWs, videos) into canonical `.bin` format that is **100% compatible** with the Android **Calypso Gallery** application: an AES-256-GCM encrypted header with a key derived from the vault KEK, and Tink-compatible `AES256_GCM_HKDF` stream encryption (256 KiB segments, AAD = file ID). Album `.meta` files are in the canonical `FGMETA` format.

> **Format:** this tool writes only the current canonical container format. The vault folder is the `Secrets/` directory inside the app folder `Calypso Gallery/` (the `Encrypted_Output` / `Sifreli_Cikti` folders on the Desktop continue to be supported by the app as standalone sync sources).

### 🛡️ Recommended Operating Systems (Tier 1 RAM vs. Tier 2 Standard Mode)
When the CLI runs, it automatically detects your operating system **without asking any extra questions**:

* **Tier 1 — Maximum Forensic Privacy (Tails OS / `toram` Linux):**
  * **Why Tails OS?** Everything runs in volatile RAM (`tmpfs`/`overlayfs`), internal disks and swap are not mounted automatically, and everything is wiped on shutdown.
  * **Alternatives:** Debian/Ubuntu (`toram noswap`), Fedora/openSUSE (`rd.live.ram=1`), or Arch (`copytoram=y`) Live ISOs.
* **Tier 2 — Standard / Convenience Mode (Windows & macOS):**
  * Automatically supported for encrypting media already on your computer directly into an external USB/SD `Secrets` vault (or transferring it via `--adb`) without first copying the plaintext files to your phone.
  * **Important caveats (shown automatically by the CLI):**
    * On standard disk-backed systems, even with `--delete-source`, SSD wear-leveling blocks, NTFS/APFS journals, Volume Shadow Copy / Time Machine snapshots, swap space (pagefile/swap), and thumbnail/search caches (`Thumbs.db`, QuickLook, Spotlight) are not cleared.
    * **Cloud sync (OneDrive / iCloud Drive):** If your Windows or macOS Desktop is synced with OneDrive/iCloud, plaintext media or the `.vault` file may be uploaded to the cloud. When the CLI detects a cloud-synced path, it warns you and does not offer to copy the `.vault` file to the cloud-synced Desktop; use a local folder outside the cloud or an external USB drive (`-i` / `-o`).

### 💾 Unified `Secrets` External-Drive Workflows (Tails OS RAM-Only)

Both workflows use **one unified `Secrets/` folder structure** on your external USB/SD drive, and `calypso_vault.py` does not ask you for an interactive output destination:

#### 1. Direct External Vault
Use the `Secrets` folder on your external USB/SD drive directly as the vault from the Android app, without copying anything into your phone's internal storage:
1. Place the plaintext media to be encrypted into the `~/Desktop/Sifrelenecek` (or `To_Encrypt`) folder, which resides in RAM.
2. Plug in your external USB/SD drive containing `Secrets/.vault` (e.g. `/media/amnesia/USB/Secrets/.vault`).
3. Run `./run.sh`. The tool automatically detects the `.vault` file; after unlocking it with your password/PIN/pattern, it encrypts **directly into `<USB>/Secrets/` without asking any additional destination question** (you can optionally specify a custom directory with `-o` / `--output`).
4. Once encryption is complete, you can securely zero-wipe the source files in RAM and connect the USB/SD drive directly to your phone.

#### 2. Phone Vault + Unified `Secrets` External-Drive Sync
Keep your primary vault on your phone, encrypt new media in Tails OS onto an external drive, and merge them directly inside the Android app without navigating into phone folders with a file manager:

1. **Prepare `Secrets/` on your external USB/SD drive (choose either setup):**
   * **Scenario 1 — Full copy of `Secrets` on USB:** Copy your phone's `Secrets` folder (`.vault` + existing encrypted `<hex>/.meta` album folders) onto the USB drive.
   * **Scenario 2 — No vault media on USB (only `.vault`):** Create a `Secrets/` folder on the USB drive and place **only** the small `.vault` header file from your phone (`Internal Storage > Calypso Gallery > Secrets > .vault`) inside it; no media is copied. *(Note: even if you place the `.vault` file directly at the `<USB>/.vault` root, the CLI automatically writes the output into `<USB>/Secrets/`; only a standalone `.vault` at `~/Desktop/.vault` or in the Home directory falls back to the Desktop `Sifreli_Cikti` / `Encrypted_Output` folder.)*
2. **Encrypt in Tails OS (`./run.sh`):**
   * Place the new media into `~/Desktop/Sifrelenecek` (or `To_Encrypt`) (files placed directly in the root go to `Default Secret Album`; subfolders such as `Sifrelenecek/Tatil/foto.jpg` match an existing `.meta` album or create a new one).
   * Run `./run.sh` and enter your vault password/PIN. **No output destination is requested** — as soon as the tool unlocks the vault, it writes encrypted `.meta` (`FGMETA`) folders and `.bin` files directly into `<USB>/Secrets/`. If you wish, have the source files in RAM deleted (`--delete-source` or interactive confirmation).
3. **Sync inside the app on your phone:**
   * Connect the USB/SD drive to your phone, open the **Calypso Gallery** app, tap **Settings > Encrypted vault > Sync vault from external drive** (*"Settings > Encrypted vault > Sync vault from external drive"*), unlock the vault if prompted, and select `<USB>/Secrets` (or the USB root).
   * **Both scenarios work identically in a single structure:** It does not matter whether the USB holds a full copy (Scenario 1) or only `.vault` plus newly added files (Scenario 2). In Scenario 2, after syncing completes, you can optionally delete the transferred `.bin` files/subfolders in `<USB>/Secrets/` and keep only the `.vault` file for the next transfer.
   * **4-Stage Single-Pass Optimization and Duplicate Checking:**
     * Unchanged mirrored `.bin` files already on the phone are skipped in `O(1)` time (by checking size + canonical header version) without decrypting headers or hashing gigabytes of video.
     * New `.bin` files with distinct names are transferred into matching or newly created albums (root files go to `Default Secret Album`), with the Tink AES-GCM-HKDF stream verified and copied in a **single read pass**.
     * Same-name / same-size collisions compare the decrypted stream together with authentication of the source stream in a **single read pass** (5 distributed `256 KB` checkpoint checks when **Fast duplicate check for large files (>8 MB)** is enabled in Android Settings, full bit-by-bit comparison when disabled): identical plaintext is skipped even if encrypted with different file IDs, DEKs and nonces; files with differing content are safely named `<name>(1)`, `<name>(2)`, etc. without overwriting.

### 📁 Supported Media Check and Automatic Album Routing
* **Pre-Encryption Format Validation:** Only photo, GIF, SVG, RAW and video extensions supported by the Android app are encrypted. Unsupported files such as `.txt`, `.pdf`, `.md`, `.zip`, as well as `.bin` files and hidden files starting with `.`, are skipped with a warning and noted in the summary report.
* **Root Folder Files → `Default Secret Album`:** Files placed directly in the root of the input folder (no subfolder) are automatically encrypted into the **`Default Secret Album`** (`__VAULT_DEFAULT_ALBUM__`) album. If it already exists in the vault, the existing folder is reused; otherwise, a new randomized 6-character hex folder with `.meta` (`FGMETA`) is created.
* **Subfolders → Smart Matching with `.meta`:** For files in subfolders (e.g. `Sifrelenecek/Tatil/foto.jpg`), the existing `.meta` files in the target vault are decrypted:
  * If an album with the same name already exists (exact or case-insensitive match), the files are added to the existing encrypted folder.
  * Otherwise, a new randomized 6-character hex folder and encrypted `.meta` (`FGMETA`) are created.
  * `.nomedia` is placed only at the vault root (`Secrets/.nomedia`); it is not written inside normal album folders (so the Android app does not treat the albums as hidden/excluded). An inner `.nomedia` is created only for hidden subfolders starting with `.`.
  * Nested subfolders (e.g. `Tatil/1_Gun/foto.jpg`) are routed to the top-level album name (`Tatil`), in keeping with Android's single-level vault structure.
* **Mixed Inputs:** Having files in both the root and subfolders in the same run is supported seamlessly.
* **Virtual Albums (`JSON/`):** The `JSON/` virtual albums inside the vault are completely independent of `calypso_vault.py`, are left untouched, and are managed solely inside the Android app.

### 🚀 Quick Start and CLI Examples
```bash
# Linux / Tails / macOS
chmod +x run.sh calypso_vault.py
./run.sh

# Windows (double-click or run from Command Prompt / PowerShell)
run.bat

# Interactive encryption with the Turkish interface (does not ask for the OS or output destination)
./run.sh --lang tr

# Auto-target the external USB Secrets vault (Linux / macOS / Windows)
./run.sh --lang tr -i ~/Desktop/Sifrelenecek -v /media/amnesia/USB/Secrets/.vault
./run.sh --lang tr -i ~/Desktop/Sifrelenecek -v /Volumes/USB/Secrets/.vault
run.bat  --lang tr -i C:\YerelMedya\Sifrelenecek -v E:\Secrets\.vault

# Override with an optional custom -o output folder
./run.sh --lang tr -i ~/Desktop/Sifrelenecek -o /media/amnesia/USB/Ozel_Cikti -v /media/amnesia/USB/Secrets/.vault
```

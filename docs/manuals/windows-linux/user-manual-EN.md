# Calypso Gallery — CLI Bridge User Manual (Windows / Linux)

[Türkçe sürüm](kullanim-kilavuzu-TR.md) · [macOS EN](../../macos/user-manual-EN.md)

> This guide explains, from start to finish, how to move unencrypted photos/videos from your computer into the encrypted vault of Calypso Gallery on your phone.
>
> On Windows and Linux, copying files between the phone and the computer is **fully automatic**: the launcher (`run.bat` / `run.sh`) does it. You only need to put your media in the input folder, run the command, and tap the import row in the app.

---

## 1. How the system works

The encrypted vault lives in `Calypso Gallery/Secrets` on the phone. The CLI tool **only encrypts** — it never decrypts and never writes plaintext media to the phone.

A **bridge folder** connects the phone and the computer:

```
Phone                                      Computer (only while running)
────                                      ──────────────────────────────
Calypso Gallery/                           Temporary directory (%TEMP% / /tmp)
├── Secrets/          ← real vault         └── <vault-id>/
│   ├── vault         ← vault header           ├── vault      ← copy of the header
│   └── <hex>/ (.meta + .bin)                   ├── cli_bridge ← bridge marker file
├── CLI/              ← bridges                 ├── <hex>/.meta
│   └── <vault-id>/                              ├── <hex>/*.bin
│       ├── vault                                └── pending_sync
│       ├── cli_bridge
│       └── (.bin files during transfer)
└── Archive/          ← archived vaults

Also: Desktop/To_Encrypt/  ← you put the media to encrypt here
```

**Automatic flow:**

1. The app creates `Calypso Gallery/CLI/<vault-id>/` for the active vault, containing only `vault` and `cli_bridge`. The marker stores the vault path, the active flag (`active=1`) and a fingerprint used by the app.
2. `run.bat` / `run.sh` detects the phone over MTP and copies that folder into a **temporary directory**.
3. The Python CLI writes encrypted `.bin` files, album `.meta` files and `pending_sync` there.
4. The launcher copies the folder back to the phone and deletes the temporary directory.
5. In the app, **Settings → "Import CLI / external encrypted media"** moves the files into the real vault and cleans the bridge.

> **Important:** The temporary directory is not permanent. No `Calypso CLI` folder is left on your desktop; the bridge itself lives on the phone.

---

## 2. Before you start

- Calypso Gallery installed with **at least one encrypted vault** created.
- Python 3.10–3.13 on the computer (3.14+ is not supported). The launcher installs missing packages automatically.
- A **data-capable** USB cable (some cables only charge).
- The phone must be **unlocked** when connected; choose **"File transfer / MTP"** in the USB notification. This dialog comes from Android itself.
- **Linux:** automatic copying uses `gio` (GVfs). Most GNOME distributions have it. On minimal/headless systems without `gio` or an MTP backend, the launcher falls back to manual copying (see section 9).
- **Windows:** no extra software needed; the launcher uses the built-in MTP interface.

### Tails / RAM-only Linux

The same flow applies; **use a cable, no network**:

- Connect the phone while the **Tails screen is unlocked** (Tails 6+ ignores USB devices plugged in while the screen is locked).
- On Tails the temporary directory is in `tmpfs` (RAM), and the source files are in `~/Desktop/To_Encrypt` (also RAM). No unencrypted data is ever written to disk.
- Everything disappears when you shut down; the bridge folder is on the phone anyway.

---

## 3. Initial setup

### Step 1 — Vault and default album

Create the encrypted vault in the app (password, PIN or pattern). The **Default Secret Album** is physically created at the same time, so files that are not placed in a named subfolder have a known destination.

### Step 2 — Create the bridge folder

Settings → **"Prepare CLI bridge"** (the new feature that replaces "export header only").

- It creates `Calypso Gallery/CLI/<vault-id>/` for the currently selected vault.
- It writes a copy of the `vault` header and the `cli_bridge` marker.
- If a bridge already exists for that vault, an **"folder already exists"** warning is shown; no second folder is created.

> This step can also happen automatically when the vault is first created. Use the manual action for existing vaults.

### Step 3 — Prepare the media

Put the files in `Desktop/To_Encrypt`. **Do not copy the bridge folder manually** — the launcher does it.

| Location | Destination album |
|---|---|
| `To_Encrypt/photo.jpg` (root) | **Default Secret Album** |
| `To_Encrypt/My holiday/photo.jpg` | "My holiday" album |
| `To_Encrypt/My holiday/Day1/photo.jpg` | Still "My holiday" (nested folders are flattened) |

Supported: photos (jpg, png, heic, webp, raw…), GIF, SVG, video (mp4, mkv, mov…). Other files are skipped.

> Album names are not visible on disk: the CLI creates a random 6-character folder (`a3f19c`) and encrypts the real name inside `.meta`. The app decrypts it during import, merges into an existing album or creates a new one.

---

## 4. Running the encryption

**Windows:** double-click `tools\calypso-cli\run.bat`.

**Linux:**

```bash
cd tools/calypso-cli
chmod +x run.sh calypso_vault.py
./run.sh
```

The launcher:

1. Finds the phone over MTP (on Linux the USB bus number is rediscovered each time; on Windows the portable-device interface is used).
2. Copies all bridge folders under `Calypso Gallery/CLI/` into the temporary directory.
3. Runs the Python CLI.
4. Copies the **selected** bridge folder back to the phone.
5. Deletes the temporary directory.

> **On interruption:** The launcher deletes the temporary directory **only after the push-back has completed**. If a temporary directory from an earlier run still exists, it does not pull from the phone again and overwrite it — it reuses the existing one. Re-running after a cable disconnect or an empty phone battery is therefore safe: a partial bridge on the phone cannot overwrite the good local state.

### Vault selection

- If **exactly one** bridge has `active=1`, the CLI uses it without asking (the usual case).
- If none or several are marked active, a numbered menu is shown with **only the vault path** on each line:

```
Select the vault to use:
  1) /storage/emulated/0/Calypso Gallery/Secrets
  2) /storage/emulated/0/USB/Calypso Gallery/Secrets

Select the vault that is currently active in the app; otherwise the transfer will fail.
```

- Passing `-v` with an explicit header path skips the menu entirely.

### CLI output

- It asks for the **vault password/PIN** (the one that was current when the bridge was created — see section 8).
- It encrypts the files, writes `.meta` + `.bin` into the temporary bridge and updates `pending_sync`.
- A `.cli_writing` sentinel exists while writing and is removed afterwards.

No plaintext is ever written into the bridge folder.

---

## 5. Importing in the app

1. In the app, tap **Settings → "Import CLI / external encrypted media"**.
2. A non-dismissible progress dialog is shown; you cannot leave Settings until the transfer finishes.
3. On success the encrypted files are moved into the real vault, `.bin`/`.meta`/album folders are removed from the bridge and **`pending_sync` is deleted last**. Only `vault` + `cli_bridge` remain on the phone.

**If something fails:** the failed entries stay in `pending_sync` and the bridge is not cleaned. Tap the same Settings row again to resume. If nothing is pending, you get **"no files to transfer"**.

Bit-identical files are skipped; same-name files with different content become `name(1)`, `name(2)`, etc.

---

## 6. Daily use in three steps

1. Put new media in `Desktop/To_Encrypt`.
2. Run `run.bat` / `./run.sh` and enter the password; the launcher handles everything else.
3. Tap "Import CLI / external encrypted media" in the app.

The initial bridge creation is one-time only.

---

## 7. Command parameters (quick reference)

In normal use **no parameters are needed**. If required:

| Parameter | Meaning |
|---|---|
| `-v`, `-k`, `--vault` | Path to the `vault` header file. Skips auto-discovery and vault selection. |
| `-i`, `--input` | Input folder (default `Desktop/To_Encrypt`). |
| `-s`, `--secret` | Provide the password on the command line. **Not recommended** — it stays in shell history. |
| `--pattern` | 3×3 grid pattern indices (e.g. `0,1,2,5`). |
| `--delete-source` | Securely wipe the source files after encryption. |
| `-e`, `--encrypt` | Encryption (default; no need to type it). |

Example:

```bash
./run.sh -v "/media/amnesia/USB/Calypso Gallery/CLI/a1b2c3d4/vault"
```

---

## 8. Common situations

| Situation | What happens / what to do |
|---|---|
| **I changed my password** | Nothing to do. The bridge keeps the password from when it was created and the same encryption key, so it stays valid. Keep using the **old password** in the CLI. |
| **Multiple vaults** | If exactly one is marked active it is selected automatically; otherwise a menu shows the vault paths. Selecting a non-active vault makes the import fail in the app. |
| **Second bridge for the same vault** | Blocked with a "folder already exists" warning. |
| **I archived the vault** | The bridge moves to `Calypso Gallery/CLI/Archived/<vault-id>/` and is hidden from the automatic flow. It returns when the vault is restored. |
| **I deleted the vault** | The bridge is deleted too. |
| **Backup / moving to another device** | Settings → **"Export encrypted vault"**. Only one option remains: the full vault. "Header only" has been removed. |
| **"Folder busy"** | The CLI is still writing; wait for it to finish. |
| **"Vault not found"** | Make sure the bridge was prepared in the app and the phone is in MTP mode. |
| **The bridge is not clean, files are still pending** | No problem: the CLI adds new files to the existing albums and **appends to** the manifest rather than replacing it. The app imports old and new entries in one pass; already-imported files are skipped by content comparison. You can encrypt more media without waiting for an earlier batch. |

---

## 9. If automatic copying fails (manual method)

If the launcher cannot see the phone (locked screen, cable issue, missing `gio` on Linux), it warns you and falls back:

1. Copy `Calypso Gallery/CLI/<vault-id>/` from the phone to a local folder with a file manager (e.g. `Desktop/Calypso CLI/<vault-id>/`).
2. Run the CLI with an explicit path:

```bash
./run.sh -v "$HOME/Desktop/Calypso CLI/<vault-id>/vault"
```

3. When it finishes, copy the folder back to `Calypso Gallery/CLI/<vault-id>/` on the phone.
4. Tap the import row in the app.

The result is identical to the automatic flow; you only perform the copying steps yourself. If the connection drops during a manual copy, do not delete the local folder — reconnect the phone, complete the missing files and run the CLI again against the same folder.

---

## 10. Security notes

- The CLI runs **offline**: network access and subprocesses are blocked; on Linux bubblewrap sandboxing is used as well. Automatic copying uses USB/MTP only, never the network.
- **Linux sandbox chain:** `bwrap` → `firejail` → `unshare --user --net`. If none is available (for example on a hardened kernel or inside a container), the launcher shows a warning and asks **`Continue anyway? [y/N]`**. The default `N` exits; only an explicit `y` continues with the Python audit hook alone and no OS-level isolation. There is no extra flag or setting — you decide each time. If this point is reached, the launcher has already tried and failed to install bubblewrap, so the warning does not suggest installing it again.
- Unencrypted media is **never written to the phone**; the phone only sees encrypted `.bin` files.
- For maximum privacy use Tails / `toram`: the temporary directory and source files are in RAM, wiped on shutdown.
- On disk-backed Windows the temporary directory is on disk; deletion is not forensic-grade. Swap files, thumbnail caches and NTFS journals may leave traces (Tier 2 caveat).
- The `vault` file has no leading dot and is visible in file managers. Since it sits next to ciphertext, anyone with folder access can attempt an offline password guess. Do not share the bridge folder or put it in cloud sync.

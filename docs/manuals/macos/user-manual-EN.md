# Calypso Gallery — CLI Bridge User Manual (macOS)

[Türkçe sürüm](kullanim-kilavuzu-TR.md) · [Windows / Linux EN](../windows-linux/user-manual-EN.md)

> This guide explains, from start to finish, how to move unencrypted photos/videos from your Mac into the encrypted vault of Calypso Gallery on your phone.
>
> macOS does not show Android MTP storage in Finder. For this reason **OpenMTP**, a free and open-source application, is required (installation below).
>
> **Platform difference:** On Windows and Linux, `run.bat` / `run.sh` copies files between the phone and the computer automatically using a temporary directory. On macOS, OpenMTP has no command-line interface, so copying is manual and the working folder is persistent.

---

## 1. How the system works

The encrypted vault lives in `Calypso Gallery/Secrets` on the phone. The CLI tool **only encrypts** — it never decrypts and never writes plaintext media to the phone.

```
Phone                                  Mac
────                                  ───
Calypso Gallery/                       ~/Desktop/
├── Secrets/          ← real vault     ├── To_Encrypt/     ← media to encrypt
│   ├── vault         ← vault header   └── Calypso CLI/
│   └── <hex>/ (.meta + .bin)              └── <vault-id>/
├── CLI/              ← bridges                ├── vault      ← copy of the header
│   └── <vault-id>/                            ├── cli_bridge ← bridge marker file
│       ├── vault                              ├── <hex>/.meta
│       ├── cli_bridge                         ├── <hex>/*.bin
│       └── (.bin during transfer)             └── pending_sync
└── Archive/          ← archived vaults
```

**Flow:**

1. The app creates `Calypso Gallery/CLI/<vault-id>/` for the active vault, containing `vault` and `cli_bridge`. The marker stores the vault path, the active flag (`active=1`) and a fingerprint used by the app.
2. You copy that folder to the Mac with **OpenMTP**.
3. The CLI writes encrypted `.bin`, `.meta` and `pending_sync` files into it.
4. You copy the folder back to the phone with OpenMTP.
5. **Settings → "Import CLI / external encrypted media"** moves the files into the real vault and cleans the bridge.

> The bridge is a transfer area, not a vault. After the import only `vault` + `cli_bridge` remain.

---

## 2. Before you start

### 2.1 Install OpenMTP (required)

Finder cannot browse Android MTP storage, and Google discontinued *Android File Transfer* in 2024. Therefore:

- **OpenMTP** — free, MIT-licensed, open source.
- Requires **macOS 10.14 (Mojave) or later**; separate builds for Apple Silicon and Intel.
- Installation options:
  - Official site: `openmtp.ganeshrvel.com` → *Download for Apple Silicon* / *Download for Intel*
  - GitHub: `github.com/ganeshrvel/openmtp` → Releases
  - Homebrew: `brew install --cask openmtp`

> Download only from official sources; copycat sites exist.
>
> OpenMTP is an independent third-party application, not affiliated with Calypso Gallery. Its name and link are provided for information only; its use is governed by its own license and terms.
>
> **Tip:** Only one application can use MTP at a time. If OpenMTP does not see the phone, quit Photos, Image Capture, Preview or the old Android File Transfer.

### 2.2 Other requirements

- Calypso Gallery installed with at least one encrypted vault.
- Python 3.10–3.13 on the Mac (3.14+ not supported). `run.sh` installs missing packages in a virtual environment.
- A data-capable USB cable.
- The phone must be **unlocked**; choose **"File transfer / MTP"** in the USB notification (the dialog comes from Android).

---

## 3. Initial setup

### Step 1 — Vault and default album

Create the encrypted vault in the app. The **Default Secret Album** is physically created at the same time; files not placed in a named subfolder go there.

### Step 2 — Create the bridge folder

No action is needed: the bridge is created automatically when the vault is created, or when you first tap the import row.

- Creates `Calypso Gallery/CLI/<vault-id>/` for the currently selected vault, with `vault` and `cli_bridge` inside.
- If a bridge already exists for that vault, a **"folder already exists"** warning is shown.

### Step 3 — Copy the bridge with OpenMTP

1. Connect the phone via USB; with the screen unlocked, choose **File transfer** in the USB notification.
2. Open OpenMTP. The **right pane is the phone**, the **left pane is the Mac**. Press `⌘R` or change the cable if the phone does not appear.
3. In the right pane navigate to `Internal storage → Calypso Gallery → CLI → <vault-id>`.
4. Drag the folder into the left pane at the fixed macOS working location:

```
~/Desktop/Calypso CLI/<vault-id>/
```

This folder is **persistent** on macOS (OpenMTP performs no automatic copying). On Windows/Linux the launcher uses a temporary directory and deletes it afterwards.

> **Vault selection (macOS):** If you copied several bridges, the one marked `active=1` in `cli_bridge` is selected automatically. If the flag is missing or ambiguous, a numbered menu is shown with **only the vault path** on each line. To be certain, copy only the bridge you need or pass `-v`.

The phone will never appear in Finder — use the OpenMTP window.

### Step 4 — Prepare the media

Put files in `~/Desktop/To_Encrypt`:

| Location | Destination album |
|---|---|
| `To_Encrypt/photo.jpg` (root) | **Default Secret Album** |
| `To_Encrypt/My holiday/photo.jpg` | "My holiday" album |
| `To_Encrypt/My holiday/Day1/photo.jpg` | Still "My holiday" |

Album names are not visible on disk: the CLI creates a random 6-character folder and encrypts the real name inside `.meta`; the app decrypts it during import.

---

## 4. Running the encryption

In Terminal:

```bash
cd tools/calypso-cli
chmod +x run.sh calypso_vault.py
./run.sh
```

The CLI:

- Finds local bridges; if exactly one has `active=1`, it is used without asking. Otherwise a numbered menu shows **only the vault paths**:

```
Select the vault to use:
  1) /storage/emulated/0/Calypso Gallery/Secrets
  2) /storage/emulated/0/USB/Calypso Gallery/Secrets

Select the vault that is currently active in the app; otherwise the transfer will fail.
```

- Asks for the **vault password/PIN** (the one current when the bridge was created — see section 8).
- Writes `.meta` + `.bin`, updates `pending_sync`; the `cli_writing` sentinel exists while writing and is removed afterwards.

No plaintext is ever written into the bridge folder.

---

## 5. Copy back and import in the app

1. In OpenMTP, drag `~/Desktop/Calypso CLI/<vault-id>/` from the left pane into `Internal storage → Calypso Gallery → CLI` on the right.
2. In the app, tap **Settings → "Import CLI / external encrypted media"**.
3. A non-dismissible progress dialog is shown; you cannot leave Settings until the transfer finishes.
4. On success the encrypted files are moved into the real vault, `.bin`/`.meta`/album folders are removed and **`pending_sync` is deleted last**. Only `vault` + `cli_bridge` remain on the phone.

**If something fails:** failed entries stay in `pending_sync`, the bridge is not cleaned, and tapping the same Settings row resumes. With nothing pending you get **"no files to transfer"**.

---

## 6. Daily use in five steps

1. Put new media in `~/Desktop/To_Encrypt`.
2. Copy the bridge folder to the Mac with OpenMTP (first time) or refresh it.
3. Run `./run.sh` in Terminal and enter the password.
4. Copy the folder back to the phone with OpenMTP.
5. Tap "Import CLI / external encrypted media" in the app.

---

## 7. Command parameters (quick reference)

In normal use **no parameters are needed**:

| Parameter | Meaning |
|---|---|
| `-v`, `-k`, `--vault` | Path to the `vault` header file. Skips auto-discovery and vault selection. |
| `-i`, `--input` | Input folder (default `~/Desktop/To_Encrypt`). |
| `-s`, `--secret` | Provide the password on the command line. **Not recommended** — it stays in shell history. |
| `--pattern` | 3×3 grid pattern indices (e.g. `0,1,2,5`). |
| `--delete-source` | Securely wipe source files after encryption. |
| `-e`, `--encrypt` | Encryption (default). |

Example:

```bash
./run.sh -v "$HOME/Desktop/Calypso CLI/a1b2c3d4/vault"
```

---

## 8. Common situations

| Situation | What happens / what to do |
|---|---|
| **I changed my password** | Nothing to do. The bridge keeps the password from when it was created and the same key; keep using the **old password** in the CLI. |
| **OpenMTP does not see the phone** | Phone unlocked, USB mode File transfer, data cable. Quit other MTP apps. |
| **Looking for the phone in Finder** | Finder has no MTP support; use OpenMTP. |
| **Multiple vaults** | If exactly one is marked active it is selected automatically; otherwise a menu shows the vault paths. Selecting a non-active vault makes the import fail in the app. |
| **Second bridge for the same vault** | Blocked with a "folder already exists" warning. |
| **I archived the vault** | The bridge moves to `CLI/Archived/<vault-id>/` and stays there as long as the vault is archived. The CLI discovers it there too, so you can keep encrypting files for it. It moves back to `CLI/<vault-id>/` only when you unarchive the vault. |
| **I deleted the vault** | The bridge is deleted too. |
| **Backup / moving to another device** | Settings → **"Export encrypted vault"**; only the full-vault option remains. |
| **"Folder busy"** | The CLI is still writing; wait. |
| **The bridge is not clean, files are still pending** | No problem: the CLI adds new files to the existing albums and **appends to** the manifest. The app imports old and new entries in one pass; already-imported files are skipped by content comparison. |

---

## 9. Security notes

- The CLI runs **offline**: network access and subprocesses are blocked; the Python audit hook is always active.
- Unencrypted media is **never written to the phone**; the phone only sees encrypted `.bin` files.
- On disk-backed macOS, swap files, QuickLook caches, Spotlight and APFS snapshots may leave traces. For maximum privacy use the Tails flow described in the Windows/Linux manual.
- The `vault` file has no leading dot and is visible in OpenMTP. Since it sits next to ciphertext, anyone with folder access can attempt an offline password guess. Do not share the bridge folder or put it in iCloud/Dropbox.
- Copying the vault header elsewhere carries the same risk: header + password equals vault access.

---

## 10. Optional: paid tools (not required)

The normal flow works entirely with free tools (OpenMTP + `run.sh`). If you want the phone to appear as a drive in **Finder**, paid MTP tools such as MacDroid or Commander One can be used; no CLI changes are needed. These tools are **optional extras, never part of the default flow.**

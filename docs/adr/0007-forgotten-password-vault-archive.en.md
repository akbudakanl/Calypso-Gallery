# ADR 0007 — Forgotten vault password: archive and recreate

- **Status:** Accepted — **Axiom: the app never deletes a vault without its password (2026-11-21)**
- **Date:** 2026-11-21
- **Related:** [ADR 0002](0002-operational-security-hardening.md) (operational security), [ADR 0003](0003-resumable-file-operations.md) (durable operations), [ADR 0006](0006-strong-biometrics-only.en.md) (strong-biometric decision)

> **Summary Decision.** When a user forgets the vault password, the app **never** performs a passwordless deletion. Instead it **moves** the existing vault into `Calypso Gallery/Archive/`, creates a new vault at the default location, and keeps the old one restorable with its password. The Archive folder is created only on the first archival and uses a random name with no date or counter. Freeing disk space is left to the user, with the exact path shown; there is no in-app archive deletion.

> **Rationale (short).** Security standards forbid deleting an encrypted vault from the app without knowing its password. Most users keep the vault at the default location, so a forgotten-password vault fills that slot and also prevents creating a new one. The user has two options: permanently delete the vault with a file manager (or first back it up to external storage with **Export encrypted vault** to retry the password later), or, if they want to keep it on the phone, move it into an isolated archive and create a fresh vault at the same location.

---

## 1. Axiom

**No vault may ever be deleted or invalidated inside the app without the vault password/PIN.**

This is a design axiom, not a preference:

- If someone with device access can "reset" or delete a vault without knowing the password, that is a **denial-of-service** attack. The attacker cannot read the data but can permanently destroy the user's access to it.
- The axiom also covers the existing **"Reset encrypted vault"** setting: it is reachable only **after entering the current password** and recreates the vault in place with a new one. No passwordless reset path is introduced.
- The axiom covers archived vaults as well: the app may not **delete** an archived vault without its password. Deletion is performed only by the user, manually, through a file manager (§8).

Biometric authentication does not change this axiom: a Class 3 biometric can unlock the vault but does not grant authority to delete or reset it without the password (see ADR 0006). The device-owner check before archiving (§3.3) is only an identity check for a non-destructive move; it never releases a vault key.

---

## 2. Problem

When a simple user forgets the password of the vault at the default location (`Calypso Gallery/Secrets`):

- They cannot open the vault, so the existing "Reset encrypted vault" setting is unreachable.
- To create a new vault they must free the default location, which is difficult and risky to do with a file manager.
- The first reaction must not be "delete": the user may remember the password later and want the old vault back.
- At the same time, an archive cannot live on the disk forever; the user must stay in control of storage. Therefore the user must be **asked** and their choice respected (§3.2).

---

## 3. Decision

### 3.1. Settings location and routing

The setting lives in the **Vault management** card, **above** the password-gated "Reset encrypted vault":

> **Archive encrypted vault** — *Moves the old vault to the archive and creates a new one in the default location*

It is mutually exclusive with **Unarchive vault** (shown while an archived vault is active). The `archive_vault_summary` string exists but is not rendered in the row. The **"I don't remember"** link in the password verification form (reached from the reminder dialog) opens a compact in-app recovery dialog with numbered steps and the currently selected vault path (§3.4). It does **not** open Settings. The **About → FAQ** entry *"I forgot my encrypted vault password, what can I do?"* remains available from About. Clickable links in that FAQ text are **not implemented**: `FaqActivity` renders the string as plain text.

**"Vault location"** shows three options:

1. **Create new vault**,
2. **Use existing vault**,
3. **Select from archived vaults** — visible **only when at least one archived vault exists**.

The archive list is ordered by **descending** archival time and labelled as **"1. archived vault"**, **"2. archived vault"**, etc. (§4.4); there is no secondary sort on equal timestamps. It is a single-line `setSingleChoiceItems` list: valid entries show only the label, entries with no date show *"Encrypted vault with unknown date"*, and invalid entries show *"Invalid archive entry"* (only those reveal `humanizePath` + a Copy path action). If every entry is invalid, the picker option is hidden because `promptVaultSetupChoice()` checks only `archivedEntries().isNotEmpty()`.

### 3.2. Question asked to the user

The dialog offers a single real flow:

1. **Yes** (default): the old vault is preserved and a new vault is created at the default `Secrets` location. The alternative button is **No** (`DeleteSecretsDialog`).

> The rejected "delete without archiving" option and the passwordless-reset idea are recorded in §10, not here. Because of the axiom, the app cannot perform a deletion itself: a user who still wants to delete is shown the archive path (§8) and may copy it to the clipboard; the app never performs the delete action.

### 3.3. Archival flow

Current code flow (`SettingsActivity.setupArchiveVault()` → `VaultArchiveManager.archiveActiveVault()`):

1. No explicit "vault locked / no foreground operation" precondition is checked; `lockVault()` is called partway through.
2. **Device-owner verification comes first**, then the `DeleteSecretsDialog` confirmation with **Yes/No**. Owner verification uses `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` (API 28–29: `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` because of an AndroidX restriction) and releases no vault key; devices without a secure lock screen skip it.
3. `Calypso Gallery/Archive/` and a generic `readme.txt` are created **before** the move, so a failed first archival leaves an empty `Archive/readme.txt` behind. External-storage locations are not rejected explicitly: a cross-filesystem `renameTo` simply fails with the generic `archive_vault_failed` toast (the dedicated guidance text in §6 is not shown).
4. A random 12-byte (24 hex character) destination name is generated and checked for collisions.
5. The destination path is written to private preferences (`archivedVaults`, via `.apply()`) **before** `source.renameTo(destination)`; a failed move rolls the entry back.
6. Biometric state is cleared: `isVaultBiometricEnabled = false`, `vaultBiometricWrappedBundle = ""`, `clearBiometricKey()`.
7. `config.vaultRootPath` is set to `""` and `promptWhereToCreateVault()` opens (Default vs Custom location). Because it is launched immediately after a non-blocking `VaultNoticeDialog`, the success / Copy-path dialog can be overlaid by the location picker. Cancelling the picker does **not** restore the previous `vaultRootPath`.

**No ADR 0003 journal is used** (`OperationJournal` knows only COPY/MOVE/ENCRYPT/DECRYPT). Crash safety rests solely on the write-ahead preference entry plus the single atomic `renameTo`.

### 3.4. FAQ entry and routing

The "I don't remember" link on the verification form (screen 5 of the reminder flow) opens a compact themed in-app dialog (screen 6): three numbered recovery steps, the warning paragraph, the **actual path of the currently selected vault** from `CryptoManager.getVaultRoot()` / `config.vaultRootPath`, and a single **OK** button. No `FaqActivity` extra is used for this entry point any more; the FAQ entry below still exists for users who reach it from About. New FAQ entry:

> **Question:** "I forgot my encrypted vault password, what can I do?"
>
> 1. Unlock the vault with biometrics.
> 2. **Copy (export) your encrypted data.** This does not remove the vault from its place; the original stays where it is. You can open the copy on another device with "Use existing vault".
> 3. To keep using the same location, use Settings → Vault management → **Archive encrypted vault**. This moves the old vault to `Calypso Gallery/Archive/` and creates a fresh vault at the default location with a new password. **"Reset encrypted vault" requires the current password** and is therefore not available in this situation.
>
> **Warning:** If biometrics cannot unlock it either, the vault is inaccessible. You have three options (least destructive first):
>
> - Create a new vault by **archiving** the current one — the old vault is preserved on this device; you can return to it if you remember the password.
> - **Copy (export) your encrypted data** — for use on another device; **copying is not moving**, the original stays in place.
> - **Permanently remove the vault** — delete the vault folder at *(its location)* with a file manager. This cannot be undone.

*archiving* and *copy (export)* are plain text today: `FaqActivity` uses a `MyTextView` without `SpannableString`/`ClickableSpan`, so there are no tappable links to Settings. *(its location)* is replaced with the dynamic path from §8; no hardcoded path is printed. The option order is deliberate: the least destructive one comes first. The shipped string resources (`faq_forgot_password_text`, `vault_password_reminder_forgot_steps`) now point step 3 at archiving instead of the password-gated "Reset encrypted vault".

---

## 4. Archive layout and metadata

```
Calypso Gallery/                      (default: /storage/emulated/0/Calypso Gallery)
├── Secrets/                          ← active vault
└── Archive/                          ← created only on first archival
    ├── readme.txt                    ← generic: purpose, restore, deletion warning
    └── a3f1c7d9e2b4/                  ← random name, no date/counter
        ├── info.txt                  ← generic marker note
        ├── .vault
        └── *.bin
```

- `Archive` is created under the **`Calypso Gallery` root**. The `.nomedia` file in the parent covers subdirectories, so the archive is hidden from galleries.

### 4.1. `Archive/readme.txt` — exact content

The file is written in Turkish and English. **No date, path, username or "password forgotten" phrase:**

```text
Warning: Do not delete this file

Calypso Gallery — Arşiv / Archive

Bu klasördeki alt klasörler daha önce kullanılmış şifreli kasalardır.
Kasalar silinmemiştir. Uygulama içinden
  Ayarlar → Şifreli kasa → Kasa konumu → Arşivden kasa aç
yoluyla her kasa kendi parolasıyla tekrar açılabilir.

Dosyaları değiştirmeyin veya yeniden adlandırmayın. Kasa şifreli olduğu
için başka bir uygulamayla açılamaz.

Disk alanı kazanmak için bir kasayı kalıcı olarak silmek isterseniz,
ilgili alt klasörü bir dosya yöneticisiyle silebilirsiniz. Bu işlem
geri alınamaz.

The subfolders here are encrypted vaults archived earlier. They were not
deleted. In the app, use Settings → Encrypted vault → Vault location →
Open from archive to reopen any of them with its own password.

Do not modify or rename the files; other apps cannot open them.
To free disk space, delete a vault subfolder with a file manager.
This cannot be undone.
```

### 4.2. `info.txt` — exact content

Each archived vault contains a generic note explaining what the folder is, so context is not lost if the folder is copied elsewhere. **No date, original path or label** — those live only in private preferences:

```text
Bu klasör Calypso Gallery tarafından arşivlenmiş bir şifreli kasadır.
İçeriğini değiştirmeyin. Uygulama içinden Ayarlar → Şifreli kasa →
Kasa konumu → Arşivden kasa aç ile açılabilir.

This folder is an encrypted vault archived by Calypso Gallery.
Do not modify its contents. Open it from Settings → Encrypted vault →
Vault location → Open from archive.
```
- The original vault path, archive timestamp and optional user label are stored **only in the app's private `SharedPreferences`**, keyed by the random id. A file manager cannot see them.
- **No date in the folder name**: it would be a metadata leak — "the user forgot their password on this date" is readable at a glance. The filesystem already has `mtime`; adding it to the name is unnecessary.
- **Fake names such as `.cache` are rejected**: they are deceptive and do not actually hide the existence of an archive.

### 4.3. If `readme.txt` is deleted

`readme.txt` is **informational only; it is not required by app logic.** If deleted:

- Archived vaults are still found by scanning `Archive/*` and validating the `.vault` header; **no vault is lost**.
- "Vault location → Select from archived vaults" shows the **same list**; no error is displayed.
- An optional informational note may appear: *"The readme.txt in the Archive folder is missing. It is only informational; your vaults are safe."*

The "Warning: Do not delete this file" line exists for this reason: the user loses instructions, **not data**.

### 4.4. Archive ordering and persistence

- The ordering key is **`info.txt.lastModified()`**, created at archival time; no date is written into the folder name.
- `Calypso Gallery` lives at the root of external storage (**not** under `Android/data`); uninstalling and reinstalling the app preserves both folders and `mtime` values. Therefore **"1st archived vault", "2nd archived vault" ordering survives reinstall.**
- Labels are generated in the UI only; no on-disk counterpart. Same-second archives are tie-broken by random id.
- If an archive is deleted with a file manager, the remaining ones are renumbered.
- **Date source priority** (the answer to "what happens if info.txt is deleted?"):
  1. `archivedAt` in the app's private `archivedVaults` record (always present during normal use, until reinstall).
  2. `info.txt.lastModified()` — created at archival time, survives reinstall.
  3. If neither exists, show **"date unknown"** and sort by random id. The folder's own `mtime` is **not** reliable: renaming a directory does not change its `mtime`, and while the vault is active it reflects file edits — it does not represent archival time.
- **`.vault` cannot be used:** its header format (`magic | version | algorithm | Argon2 params | salt | IVs | wrapped VMK | keyring | protection type`) has **no date field**. Its `mtime` records the last header rewrite (password change / KDF rewrap), not archival time; rename does not change it. Writing a date into `.vault` would risk corrupting a security-critical file — **rejected**.
- If `.vault` is **deleted or its header is corrupted**, `isValidVaultHeader` fails; `resolveExistingVaultInDirectory` no longer treats the folder as a vault and it disappears from the list. This is correct: a folder without a valid vault header is not a vault. If a private-preference entry exists but no valid vault is on disk, the UI shows an **"invalid archive entry"** row with the path and *"Vault header not found; you can delete this folder with a file manager."*

---

## 5. Random name and collision safety

The archive folder name is **cryptographically random**:

- `SecureRandom` produces 12 bytes → 24 hex characters (e.g. `a3f1c7d9e2b4c6d8e0f1a2b3`).
- After generation, `File.exists()` is checked; **on collision a new name is generated**. After a bounded number of attempts (e.g. 10) the operation is cancelled and an error is reported.
- `renameTo` already fails if the destination exists — a second layer of safety.
- `UUIDv4` is acceptable; **ULID / UUIDv7 are not used** because they embed a timestamp in the name.
- Sequential counters (`Secrets (1)`, `Secrets (2)`) are not used: they leak the number of archives and can collide after deletion.

Friendly names shown to the user ("Archive 1", "Archive 2") exist **only in the UI**; the on-disk name stays random. The list is sorted by file `mtime`.

---

## 6. Move, rollback and key state

- When the source vault is under `Calypso Gallery`, the destination is on the same filesystem; `File.renameTo` is **atomic and instantaneous** and copies no data. The long foreground operation from ADR 0003 is not needed.
- A vault on a **custom/external storage location** is not explicitly rejected: if it happens to be on the same filesystem it is archived normally; otherwise `File.renameTo` fails and the generic `archive_vault_failed` toast (*"Could not archive the vault. Nothing was changed."*) is shown. The dedicated external-storage guidance quoted in older revisions is not implemented.
- Implementation detail: the archive entry is written to preferences **before** the rename and rolled back if the rename fails, so a crash cannot leave an unfindable vault. There is no multi-step ADR 0003 journal; the remaining steps (clear biometrics, create new vault, switch config) happen after the single atomic rename.
- The archived vault's `.vault` header and `.bin` files are **not path-dependent**, so keys remain intact. The archived vault can later be opened **only with its own password**.

---

## 7. Restore — in-place activation + explicit "Unarchive vault"

The "vaults swap places" model was removed (`6f6268efc`): there is **no swap and no overwrite**. The shipped model has two steps:

1. **Activate in place.** "Select from archived vaults" calls `activateArchivedVault()` on tap — no folder is moved. `CryptoManager.selectVaultWithoutUnlock()` registers the vault path and the public header metadata (protection type, KDF settings) but asks for **no vault password**: entering a vault password belongs to opening the vault, not to selecting it. When another vault is currently active, the selection is preceded by the same device-owner verification (device credential / biometric, skipped on devices without a secure lock) used by archiving, because what is protected is the vault configuration, not vault content. With an empty vault slot the first selection is ungated. The `archived_vault_activated` toast says *"Use Settings → Unarchive vault to move it back"*. Backing out of a later prompt cannot leave a half-selected vault behind because selection is already complete and consistent.
2. **Explicit "Unarchive vault" row.** While an archived vault is active, Settings hides "Archive encrypted vault" and shows "Unarchive vault". `setupUnarchiveVault()` → `canUnarchive(entry)` checks `restoreTarget(entry)` (the original path, or the default `Secrets` after a reinstall): if another valid `.vault` occupies that location, `unarchive_vault_blocked` explains that the other vault must be archived first and offers **"Combine data into one vault"** with manual import steps. No swap occurs.

Otherwise `DeleteSecretsDialog` → `requireDeviceOwner()` → a single `source.renameTo(target)` moves the vault back, using the same device-owner verification as archiving (no vault password). The picker also exposes an info icon with the total size of all archived vaults.

There is no two-rename transaction and no ADR 0003 journal: `VaultArchiveManager` contains zero `OperationJournal` / `OperationRecovery` calls. If the user does not remember the archived vault's password, the "I forgot my password" flow can still be used.

---

## 8. Freeing disk space

There is **no in-app archive deletion** (axiom). The user is told:

> "Archived vaults take up disk space. To free space, you can delete the vault folder with a file manager: **/storage/emulated/0/Calypso Gallery/Archive/<id>**."

- The path is always the **actual path of the selected/archive entry**, never a hardcoded default (custom locations are supported).
- A "Copy path" button may be offered; the app **does not perform the deletion**.
- `Calypso Gallery` lives at the root of external storage, not under `Android/data`; it survives app uninstall. The user should therefore be told explicitly that cleanup is their responsibility.

### 8.1. Where the archived vault's location is shown

To delete an archived vault with a file manager, the user must see **that vault's folder** (`Archive/<random-id>`), not just the `Archive/` root. The path is shown in four places:

1. **Archival success dialog:** short confirmation + full path (selectable `TextView`) + **Copy path** + Done.
2. **Vault location → Select from archived vaults** list: each row shows a friendly name (*"1st archived vault"*), archive date (`info.txt` mtime) and a shortened path (`humanizePath`) on a second line. Tapping the path shows the full path or copies it.
3. **FAQ warning paragraph:** *(its location)* is replaced with the dynamic path from §8.
4. `readme.txt` / `info.txt` contain **no path** (metadata leak); they only contain in-app guidance.

There is **no in-app deletion**; at most:

- **Copy path** (`ClipboardManager`) — the only mechanism shipped. A SAF "Show folder" button was considered but deferred to v2; it is not part of this decision.

The path always comes from the archive entry / real folder, never from a hardcoded default. After a reinstall with lost preferences, `Archive/*` is scanned and the path is reconstructed (§4.4).

---

## 9. Archiving vs. export (other devices)

These two operations must not be confused:

| | **Archiving** | **Export encrypted vault** |
|---|---|---|
| Purpose | Keep the old vault on this device | **Copy** the vault to another device / desktop CLI |
| Location | `Calypso Gallery/Archive/<random-id>` | User-selected folder or external disk |
| Source vault | **Moved** — the original location is vacated | **Copied** — the original stays in place |
| Encryption | Preserved | Preserved |
| Opened from | "Select from archived vaults" on this device | "Use existing vault" on the target device |
| Password | Required (vault opens with its own password) | **Not required** (§9.1) |

If the user's goal is **"use the vault on another device"**, the correct path is **export**; archiving does not serve that purpose. Archiving is only for "if I remember the password later on this device, I want to go back".

FAQ step 2 ("**Copy (export)** your encrypted data") explains exactly this distinction. The archival confirmation dialog also reminds: *"If you want to use the vault on another device, use Settings → Export encrypted vault. This copies the files; your current vault stays where it is."*

So the user never has to wonder "where did my old vault go?": **same device → archive, other device → export.**

### 9.1. The export unlock gate is removed

`CryptoManager.exportEncryptedVault` is a pure file copy (`copyTo`): it **does not use any key and does not decrypt anything.** The `.vault` file is already a plain file on disk, which the user can copy with a file manager or MTP anyway. Therefore the `ensureVaultUnlocked` call in `promptExportEncryptedVault` is **not a real security boundary, only unnecessary friction**. The earlier "keep the unlocked-vault requirement" decision is **reversed** for this reason.

The **vault password / unlocked-vault** requirement for export is **removed**. The checks that stay:

- **device-owner verification** on devices with a secure lock screen (Class 3 biometric, falling back to device PIN/pattern/password — §3.3). This is a local friction, not a vault-key gate: the exported files stay encrypted and no vault password is asked.
- destination folder picker (existing `pickCustomVaultDirectory`),
- guard against exporting the vault into itself (existing canonical-path check),
- progress dialog,
- explicit confirmation text: *"Encrypted files will be copied. No password is required; data stays encrypted."*

In-app export has **the same security level as copying with a file manager**; the app merely makes it convenient. This does not violate the axiom (no passwordless deletion): export is a **copy**, not a deletion or invalidation.

> **Offline brute-force note.** Because the vault files can be copied without a password (by the app or by any file manager), confidentiality rests entirely on the Argon2id parameters stored in `.vault` and on the length/quality of the vault password. The device-owner check above only deters casual access to an unlocked phone; it does **not** change the strength of the offline attack.

Thanks to this change, FAQ step 2 ("Copy (export) your encrypted data") also works for a user who forgot the password — the vault does not need to be unlocked. The archive-vs-export distinction (§9) is preserved: same device → archive, other device → export.

### 9.2. Terminology: "export" means copying

The word "export" gives users the impression of a **move**, while the operation is `copyTo` and the source vault stays in place. To remove the ambiguity:

- Settings and FAQ use **"copy (export)"** rather than "export" alone.
- The export confirmation states explicitly: *"Files will be copied; your current vault stays where it is."*
- The comparison table (§9) has a **Source vault** row: archiving **moves**, export **copies**.
- The existing "export" term in code/CLI/docs is kept (no large rename); **user-facing text emphasizes copying**.

The three verbs are now distinct: **archive = move within this device**, **export = copy elsewhere**, **delete = permanent removal via file manager**.

---

## 10. Rejected alternatives

1. **Passwordless "Reset encrypted vault" — including biometric or device-credential authorization:** violates the axiom; denial-of-service risk. Rejected. Biometrics unlock the vault and verify device ownership for the non-destructive archive move (§3.3), but never authorize a destructive reset.
2. **In-app deletion of an archived vault:** violates the axiom. Rejected; deletion is left to the file manager.
3. **"Delete without archiving and continue" in the forgotten-password dialog:** a passwordless in-app deletion despite the second confirmation. Rejected; the dialog only offers Archive / Cancel (§3.2).
4. **Date/counter-based folder names:** metadata leak and collision risk. Rejected (§5).
5. **Fake names such as `.cache`:** deceptive. Rejected.
6. **Sending the user straight to a file manager:** hard and risky for a simple user. Rejected; archiving is automatic and in-app.
7. **Creating the new vault as `Secrets-2`:** reduces move risk but fails the "new vault at the default location" goal. Rejected.

---

## 11. Verification criteria

- With a vault at the default location, "I forgot my password" can archive it **without asking for the vault password**, but **no code path may call `delete()`/`wipe()` without a password** (static test).
- The `Archive` folder does not exist at installation; `exists() == false` before the first archival.
- No name collision in 1000 archival simulations; forced collision triggers regeneration.
- If the process is killed mid-move, the vault is accessible either at the source or from the archive — never lost.
- An archived vault opens via "Open from archive" **with its own password** and cannot be opened without it.
- Archive folder names contain no date/counter; `readme.txt`/`info.txt` contain no sensitive metadata.
- The new vault is created at the default `Secrets` location and `config.vaultRootPath` points to it.
- Biometric residue is cleared: `isVaultBiometricEnabled == false` for the new vault.

---

## 12. Next steps

After this ADR is accepted:

1. Prepare the HTML mockups (settings row, confirmation dialog, archive list, path notice).
2. Remove the `ensureVaultUnlocked` call from `promptExportEncryptedVault` (§9.1).
3. Implement the code using the ADR 0003 journal pattern.
4. Translate the new strings into 9 locales and update the `translation-blind-tests/` reports.
5. Add a release note under `FORK_CHANGELOG.md`.

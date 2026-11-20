# ADR 0007 — Forgotten vault password: archive and recreate

- **Status:** Accepted — **Axiom: the app never deletes a vault without its password (2026-11-21)**
- **Date:** 2026-11-21
- **Related:** [ADR 0002](0002-operational-security-hardening.md) (operational security), [ADR 0003](0003-resumable-file-operations.md) (durable operations), [ADR 0006](0006-strong-biometrics-only.md) (strong-biometric decision)
- **Turkish original:** [0007-forgotten-password-vault-archive.md](0007-forgotten-password-vault-archive.md)

> **Summary Decision.** When a user forgets the vault password, the app **never** performs a passwordless deletion. Instead it **moves** the existing vault into `Calypso Gallery/Archive/`, creates a new vault at the default location, and keeps the old one restorable with its password. The Archive folder is created only on the first archival and uses a random name with no date or counter. Freeing disk space is left to the user, with the exact path shown; there is no in-app archive deletion.

---

## 1. Axiom

**No vault may ever be deleted or invalidated inside the app without the vault password/PIN.**

This is a design axiom, not a preference:

- If someone with device access can "reset" or delete a vault without knowing the password, that is a **denial-of-service** attack. The attacker cannot read the data but can permanently destroy the user's access to it.
- The axiom also covers the existing **"Reset encrypted vault"** setting: it is reachable only **after entering the current password** and recreates the vault in place with a new one. No passwordless reset path is introduced.
- The axiom covers archived vaults as well: the app may not **delete** an archived vault without its password. Deletion is performed only by the user, manually, through a file manager (§8).

Biometric authentication does not change this axiom: a Class 3 biometric can unlock the vault but does not grant authority to delete it without the password (see ADR 0006).

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

A separate row is added at the bottom of the **Encrypted vault** section, below the password-gated "Reset encrypted vault":

> **Archive vault** — *Moves the old vault to the archive and creates a new one in the default location*

The row is visible **only when a vault exists**. The **"I don't remember"** button in the password reminder dialog does **not** open Settings; it opens the **About → FAQ** entry *"I forgot my encrypted vault password, what can I do?"* (§3.4). The word *archiving* in that FAQ text links back to this setting.

**"Vault location"** shows three options:

1. **Create new vault**,
2. **Use existing vault**,
3. **Select from archived vaults** — visible **only when at least one archived vault exists**.

The archive list is ordered by archival time and labelled in the UI as **"1st archived vault"**, **"2nd archived vault"**, etc. (§4.4). This order survives an app reinstall.

### 3.2. Question asked to the user

The first dialog offers two options:

1. **Archive and continue** (default, recommended): the old vault is preserved and a new vault is created at the default `Secrets` location.
2. **Delete without archiving and continue**: would require a second, explicit confirmation — but it is a passwordless in-app deletion and therefore contradicts the axiom. **It is rejected.** The options actually presented are **Archive and continue / Cancel**; archiving is the default.

> Note: §2 says the user's storage choice must be respected. Because of the axiom, the app cannot perform the deletion itself. A user who chooses deletion is shown the archive path (§8) and may copy it to the clipboard; the app never performs the delete action.

### 3.3. Archival flow

Preconditions: the vault must be locked, no foreground file operation active, and the device unlocked.

1. User confirmation uses the same `DeleteSecretsDialog` as "Reset encrypted vault": **10-second countdown + confirmation checkbox + Yes/No**. **Difference: no password is asked** — the axiom forbids passwordless deletion, while archiving is a move, not a deletion (§6). The dialog also reminds: *"If you want to use the vault on another device, use Export encrypted vault"* (§9).
2. `Calypso Gallery/Archive/` is created **if missing**, with a generic `readme.txt`. The folder **does not exist at app installation**; it is created on first archival only.
3. A random destination name is generated (§5) and checked for collisions.
4. The destination path is stored in private preferences (`archivedVaults`), and the old vault folder is **moved** there.
5. If the move fails, the preference entry is rolled back and the source stays in place.
6. Biometric vault state is cleared: `isVaultBiometricEnabled = false`, `vaultBiometricWrappedBundle = ""`, `clearBiometricKey()`. The old vault's biometric binding is never carried over to the new one.
7. The new-vault setup flow starts at the default location (`Calypso Gallery/Secrets`); the user chooses a new password/PIN.
8. If new-vault creation fails, `config.vaultRootPath` is restored to the archive path so the old vault stays accessible.
9. On success, `config.vaultRootPath` points to the new vault; the archive entry remains in private preferences.

All steps run through a **journal** following ADR 0003. If the process dies at any step, the vault is either at its source or reachable from the archive path — **never lost**.

### 3.4. FAQ entry and routing

The "I don't remember" button in the reminder dialog opens `FaqActivity` directly with an extra that highlights the relevant entry. New FAQ entry:

> **Question:** "I forgot my encrypted vault password, what can I do?"
>
> 1. Unlock the vault with biometrics.
> 2. Export your encrypted data.
> 3. Use Settings → Reset encrypted vault to set a new password and restore.
>
> **Warning:** If biometrics cannot unlock it either, the vault is inaccessible. You can create a new vault by **archiving** the current one, or permanently remove it by deleting the vault folder at *(its location)* with a file manager.

*archiving* is a clickable link (`SpannableString`/`ClickableSpan` in FaqActivity or a target extra for SettingsActivity) leading to the **Archive vault** setting. *(its location)* is replaced with the dynamic path from §8; no hardcoded path is printed.

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
- If `info.txt` is missing, the folder's own `mtime` is used as fallback.

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
- When the source is a custom/external location, the filesystems may differ. If `renameTo` fails:
  - either perform **copy → verify → remove source** with an ADR 0003 journal (no data loss, but slow),
  - or cancel automatic archival and inform the user that it is unsupported for this location.
- Journal steps: write preference → rename → clear biometrics → create new vault → switch config. Every step is reversible.
- The archived vault's `.vault` header and `.bin` files are **not path-dependent**, so keys remain intact. The archived vault can later be opened **only with its own password**.

---

## 7. Restore — the "vaults swap places" model for simple users

The user is told exactly this:

> "To use an archived vault again, go to **Settings → Encrypted vault → Vault location → Open from archive**. Pick the vault from the list. The chosen vault returns to the default location and the current vault is moved to the archive. **Nothing is deleted.**"

The user does not need to understand "active vault" or "archive" as separate concepts; their mental model is simply **"two vaults swap places"**.

Flow:

1. An **"Open from archive"** entry appears in "Vault location".
2. `archivedVaults` entries from private preferences are shown with a friendly name + `mtime`. If preferences were wiped, `Archive/*` is scanned and `CryptoManager.resolveExistingVaultInDirectory(dir)` finds a valid `.vault` header; the random name is a fallback label.
3. The user picks a vault; the confirmation dialog says only: *"This vault will move to the default location. The current vault will be moved to the archive. No data will be deleted. Continue?"*
4. On confirmation, the ADR 0003 journal performs two **atomic renames on the same filesystem**:
   - `Secrets` → `Archive/<new-random-id>` (current vault goes to the archive),
   - `Archive/<chosen-id>` → `Secrets` (chosen vault returns to the default location).
5. `config.vaultRootPath` is set to `Secrets`; biometric state is cleared.
6. `VaultSecurityDialog` asks for the **chosen vault's password**. If it is wrong, the user can retry: the vault stays at `Secrets` and remains accessible, the app does not get stuck.
7. Success message: *"The chosen vault has returned to the default location. Your previous vault is preserved in the archive."*

Why this approach:

- The user needs no folder name, random id or file manager knowledge.
- The default location always contains exactly **one** vault; `findVaultRoot` discovery stays unchanged.
- The axiom is preserved: both renames copy/delete nothing, and the operation is reversible.
- Because the restored vault lives at the default location, biometric setup and lock flows work exactly as before.

If the user does not remember the restored vault's password either, they cannot complete step 6; the "I forgot my password" flow can then be used again — it archives the current vault and creates a new one.

---

## 8. Freeing disk space

There is **no in-app archive deletion** (axiom). The user is told:

> "Archived vaults take up disk space. To free space, you can delete the vault folder with a file manager: **/storage/emulated/0/Calypso Gallery/Archive/<id>**."

- The path is always the **actual path of the selected/archive entry**, never a hardcoded default (custom locations are supported).
- A "Copy path" button may be offered; the app **does not perform the deletion**.
- `Calypso Gallery` lives at the root of external storage, not under `Android/data`; it survives app uninstall. The user should therefore be told explicitly that cleanup is their responsibility.

---

## 9. Archiving vs. export (other devices)

These two operations must not be confused:

| | **Archiving** | **Export encrypted vault** |
|---|---|---|
| Purpose | Keep the old vault on this device | Move the vault to another device / desktop CLI |
| Location | `Calypso Gallery/Archive/<random-id>` | User-selected folder or external disk |
| Encryption | Preserved | Preserved |
| Opened from | "Select from archived vaults" on this device | "Use existing vault" on the target device |
| Password | Required | Required |

If the user's goal is **"use the vault on another device"**, the correct path is **export**; archiving does not serve that purpose. Archiving is only for "if I remember the password later on this device, I want to go back".

FAQ step 2 ("Export your encrypted data") explains exactly this distinction. The archival confirmation dialog also reminds: *"If you want to use the vault on another device, use Settings → Export encrypted vault."*

So the user never has to wonder "where did my old vault go?": **same device → archive, other device → export.**

### 9.1. The export unlock gate is removed

`CryptoManager.exportEncryptedVault` is a pure file copy (`copyTo`): it **does not use any key and does not decrypt anything.** The `.vault` file is already a plain file on disk, which the user can copy with a file manager or MTP anyway. Therefore the `ensureVaultUnlocked` call in `promptExportEncryptedVault` is **not a real security boundary, only unnecessary friction**. The earlier "keep the unlocked-vault requirement" decision is **reversed** for this reason.

The password / biometric / unlocked-vault requirement for export is **removed**. The checks that stay:

- destination folder picker (existing `pickCustomVaultDirectory`),
- guard against exporting the vault into itself (existing canonical-path check),
- progress dialog,
- explicit confirmation text: *"Encrypted files will be copied. No password is required; data stays encrypted."*

In-app export has **the same security level as copying with a file manager**; the app merely makes it convenient. This does not violate the axiom (no passwordless deletion): export is a **copy**, not a deletion or invalidation.

Thanks to this change, FAQ step 2 ("Export your encrypted data") also works for a user who forgot the password — the vault does not need to be unlocked. The archive-vs-export distinction (§9) is preserved: same device → archive, other device → export.

---

## 10. Rejected alternatives

1. **Passwordless "Reset encrypted vault":** violates the axiom; denial-of-service risk. Rejected.
2. **In-app deletion of an archived vault:** violates the axiom. Rejected; deletion is left to the file manager.
3. **Date/counter-based folder names:** metadata leak and collision risk. Rejected (§5).
4. **Fake names such as `.cache`:** deceptive. Rejected.
5. **Sending the user straight to a file manager:** hard and risky for a simple user. Rejected; archiving is automatic and in-app.
6. **Creating the new vault as `Secrets-2`:** reduces move risk but fails the "new vault at the default location" goal. Rejected.

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

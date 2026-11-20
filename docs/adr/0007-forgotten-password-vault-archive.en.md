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

### 3.1. Settings location

A separate row is added at the bottom of the **Encrypted vault** section, below the password-gated "Reset encrypted vault":

> **I forgot my password** — *Archives the old vault and creates a new one in the default location*

The row is visible **only when a vault exists**. The "I don't remember" link in the password reminder dialog leads directly into this flow, so users do not have to search in Settings.

### 3.2. Question asked to the user

The first dialog offers two options:

1. **Archive and continue** (default, recommended): the old vault is preserved and a new vault is created at the default `Secrets` location.
2. **Delete without archiving and continue**: would require a second, explicit confirmation — but it is a passwordless in-app deletion and therefore contradicts the axiom. **It is rejected.** The options actually presented are **Archive and continue / Cancel**; archiving is the default.

> Note: §2 says the user's storage choice must be respected. Because of the axiom, the app cannot perform the deletion itself. A user who chooses deletion is shown the archive path (§8) and may copy it to the clipboard; the app never performs the delete action.

### 3.3. Archival flow

Preconditions: the vault must be locked, no foreground file operation active, and the device unlocked.

1. User confirmation is obtained; the dialog explains that the archive consumes disk space and can be restored later.
2. `Calypso Gallery/Archive/` is created **if missing**, with a generic `readme.txt`. The folder **does not exist at app installation**; it is created on first archival only.
3. A random destination name is generated (§5) and checked for collisions.
4. The destination path is stored in private preferences (`archivedVaults`), and the old vault folder is **moved** there.
5. If the move fails, the preference entry is rolled back and the source stays in place.
6. Biometric vault state is cleared: `isVaultBiometricEnabled = false`, `vaultBiometricWrappedBundle = ""`, `clearBiometricKey()`. The old vault's biometric binding is never carried over to the new one.
7. The new-vault setup flow starts at the default location (`Calypso Gallery/Secrets`); the user chooses a new password/PIN.
8. If new-vault creation fails, `config.vaultRootPath` is restored to the archive path so the old vault stays accessible.
9. On success, `config.vaultRootPath` points to the new vault; the archive entry remains in private preferences.

All steps run through a **journal** following ADR 0003. If the process dies at any step, the vault is either at its source or reachable from the archive path — **never lost**.

---

## 4. Archive layout and metadata

```
Calypso Gallery/                      (default: /storage/emulated/0/Calypso Gallery)
├── Secrets/                          ← active vault
└── Archive/                          ← created only on first archival
    ├── readme.txt                    ← generic: purpose, restore, deletion warning
    └── a3f1c7d9e2b4/                  ← random name, no date/counter
        ├── .vault
        └── *.bin
```

- `Archive` is created under the **`Calypso Gallery` root**. The `.nomedia` file in the parent covers subdirectories, so the archive is hidden from galleries.
- `readme.txt` and any `info.txt` are **generic**: no date, original path, username or behavioural information such as "password forgotten".
- The original vault path, archive timestamp and optional user label are stored **only in the app's private `SharedPreferences`**, keyed by the random id. A file manager cannot see them.
- **No date in the folder name**: it would be a metadata leak — "the user forgot their password on this date" is readable at a glance. The filesystem already has `mtime`; adding it to the name is unnecessary.
- **Fake names such as `.cache` are rejected**: they are deceptive and do not actually hide the existence of an archive.

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

## 7. Restore

An **"Open from archive"** entry is added to the "Vault location" setting:

- Shows the `archivedVaults` entries from private preferences (friendly name + `mtime`).
- If preferences were wiped, `Archive/*` is scanned and `CryptoManager.resolveExistingVaultInDirectory(dir)` is used to find a valid `.vault` header; the random name is shown as a fallback label.
- The selected archive is activated **in place** (`config.vaultRootPath` = archive path); the user unlocks it with the vault password.
- Moving it back to the default location is not automatic: a new vault already lives there, and doing so would mean passwordless deletion (axiom).

---

## 8. Freeing disk space

There is **no in-app archive deletion** (axiom). The user is told:

> "Archived vaults take up disk space. To free space, you can delete the vault folder with a file manager: **/storage/emulated/0/Calypso Gallery/Archive/<id>**."

- The path is always the **actual path of the selected/archive entry**, never a hardcoded default (custom locations are supported).
- A "Copy path" button may be offered; the app **does not perform the deletion**.
- `Calypso Gallery` lives at the root of external storage, not under `Android/data`; it survives app uninstall. The user should therefore be told explicitly that cleanup is their responsibility.

---

## 9. Rejected alternatives

1. **Passwordless "Reset encrypted vault":** violates the axiom; denial-of-service risk. Rejected.
2. **In-app deletion of an archived vault:** violates the axiom. Rejected; deletion is left to the file manager.
3. **Date/counter-based folder names:** metadata leak and collision risk. Rejected (§5).
4. **Fake names such as `.cache`:** deceptive. Rejected.
5. **Sending the user straight to a file manager:** hard and risky for a simple user. Rejected; archiving is automatic and in-app.
6. **Creating the new vault as `Secrets-2`:** reduces move risk but fails the "new vault at the default location" goal. Rejected.

---

## 10. Verification criteria

- With a vault at the default location, "I forgot my password" can archive it **without asking for the vault password**, but **no code path may call `delete()`/`wipe()` without a password** (static test).
- The `Archive` folder does not exist at installation; `exists() == false` before the first archival.
- No name collision in 1000 archival simulations; forced collision triggers regeneration.
- If the process is killed mid-move, the vault is accessible either at the source or from the archive — never lost.
- An archived vault opens via "Open from archive" **with its own password** and cannot be opened without it.
- Archive folder names contain no date/counter; `readme.txt`/`info.txt` contain no sensitive metadata.
- The new vault is created at the default `Secrets` location and `config.vaultRootPath` points to it.
- Biometric residue is cleared: `isVaultBiometricEnabled == false` for the new vault.

---

## 11. Next steps

After this ADR is accepted:

1. Prepare the HTML mockups (settings row, confirmation dialog, archive list, path notice).
2. Implement the code using the ADR 0003 journal pattern.
3. Translate the new strings into 9 locales and update the `translation-blind-tests/` reports.
4. Add a release note under `FORK_CHANGELOG.md`.

# ADR 0005 — KEK rotation

- **Status:** Rejected — **Not required for the personal/local usage model; kept as a reference design for enterprise needs (2026-11-18)**
- **Date:** 2026-11-18
- **Related:** [ADR 0001](0001-tink-streaming-aead-migration.md) (canonical container format), [ADR 0002](0002-operational-security-hardening.md) (operational security), [ADR 0003](0003-resumable-file-operations.md) (resumable operations), [ADR 0004](0004-argon2-memory-and-cross-device-kdf.md) (KDF calibration and `.vault` rewrapping)

> **Decision summary.** KEK (Key Encryption Key) rotation has been **rejected (Rejected)** for Calypso Gallery's personal/local mobile vault architecture. The current key hierarchy already uses unique per-file derived keys, so there is no cryptographic wear-out under a single KEK, while bulk KEK rotation would introduce a high risk of data, metadata and synchronization loss. Section 2 below documents the reasons for rejection, section 3 the forward compatibility of the current format, and sections 4–5 the reference architecture to apply if enterprise/compliance requirements (PCI DSS, ISO 27001, NIST SP 800-57) arise later.

---

## 1. Current key hierarchy

```
password / PIN ──► Argon2id(salt, params) ──► PDK   (wraps the VMK in .vault)
                                              │
                                        AES-256-GCM
                                              ▼
                                             VMK   (wraps the keyring in .vault)
                                              │
                                        AES-256-GCM
                                              ▼
                                       Keyring { keyId = 1 → KEK }
                                              │
         ┌────────────────────────────────────┴────────────────────────────────────┐
         ▼                                                                         ▼
   HKDF-SHA256(KEK[keyId],                                             HKDF-SHA256(KEK[keyId],
     "CalypsoGallery:Header:AES256GCM" ‖ hex(fileId))                   "CalypsoGallery:Metadata:AESGCM|CalypsoGallery:Metadata:AESGCM|" ‖ purpose)
         │                                                                         │
         ▼                                                                         ▼
   headerKey ──► AES-256-GCM(DEK + file header)                      metadataKey ──► AES-256-GCM(FVSTR: record)
                      │
                      ▼
         DEK ──► Tink AesGcmHkdfStreaming(DEK, AAD = fileId) ──► Encrypted media payload
```

---

## 2. Why KEK rotation is not considered necessary (rejection rationale)

### 2.1. No key wear-out / nonce reuse risk under a single KEK
- The most common cryptographic reason for KEK rotation is the growing risk of AES-GCM nonce collisions or the birthday bound ($2^{32}$) as the number of blocks/messages encrypted under the same symmetric key increases.
- In the Calypso Gallery architecture the **`KEK` is never used directly for AES-GCM encryption**:
  - Every `.bin` file is created with a random immutable 16-byte `fileId` and an independent 32-byte **`DEK`**.
  - The header key is derived per file with `HKDF-SHA256(KEK[keyId], "CalypsoGallery:Header:AES256GCM|" || hex(fileId))`, so each file gets its own unique `headerKey`.
  - The media payload is encrypted with subkeys derived from the `DEK` and a random 32-byte stream salt (`salt`) inside Tink `AesGcmHkdfStreaming`.
- Therefore, even if millions of files are encrypted under a single `KEK` (`keyId = 1`), no key/nonce wear-out occurs.

### 2.2. In the local mobile threat model `VMK` and `KEK` share one security boundary
- On multi-tenant servers or HSM (Hardware Security Module) systems, the layers of the key hierarchy live in different physical/logical security boundaries and a lower-layer key can realistically leak on its own.
- In a local mobile vault application, when the vault is unlocked the `VMK` and `KEK` **reside together in the same application process RAM** (`unlockedMasterKey` and `unlockedKek`).
- There is no realistic individual attack scenario in which only the `KEK` leaks in isolation without the `VMK` or the device being compromised.
- If the password or vault lock is suspected to have leaked, **changing the password** (or updating the KDF cost as in ADR 0004) already re-establishes access security by renewing the `PDK -> VMK` wrap in the `.vault` header with a new `salt` and `IV`.

### 2.3. It provides no protection against previously copied data
- KEK rotation only changes the `DEK` wrap in `.bin` headers; the **`DEK` and the Tink ciphertext that encrypt the file body remain bit-for-bit identical**.
- If an attacker previously copied the `.vault` file and the encrypted `.bin` files, rotating the KEK on the device does not invalidate those old copies. The only way to remedy such a leak is to re-encrypt every file from plaintext with brand-new `DEK`s (create a new vault and migrate) and destroy the old copies.

### 2.4. For personal use the risk/benefit ratio is unfavourable (data loss and broken synchronization)
For individual users, the engineering and operational risks of KEK rotation far outweigh its marginal enterprise compliance benefit:

1. **Risk of data and thumbnail loss during bulk rewriting:** In-place rewriting of the headers of every `.bin` file in the vault can be interrupted by battery depletion, OS process kill (`SIGKILL`), full storage or a dropped OTG connection, leaving partial states; it also risks accidentally dropping the embedded thumbnail bytes from the header.
2. **Risk of making metadata (`FVSTR:`) records unreadable:** The current `decryptStringFromVault` implementation does not read or use the `keyId` field in the record and always refers to the active KEK (`activeKek()`). After rotation, Room SQLite hidden virtual-album names, `JSON/` exports, restore manifests and resume records written with the old KEK become undecryptable.
3. **"Split-brain" risk with external disk / desktop CLI (`calypso_vault.py`):** The CLI is an **encrypt-only** tool; it never opens or decrypts a `.bin` file. New `.bin` files created on the PC with an old `.vault` are written with the old KEK and the fixed `ACTIVE_KEY_ID = 1` (retiring the old KEK would break synchronization). On a separate Android device, `.bin` files with the new `keyId` **cannot be opened** with the old `.vault` header. Writing the current `.vault` header into an old export folder is blocked by the `checkVaultExportTarget` (`VaultExportHeaderCheck`) byte-equality check with a `DIFFERENT_VAULT` error; a new folder is required.

---

## 3. Forward format compatibility (`1.x`, current version `1.0.0`)

Even though KEK rotation is not implemented during `1.x`, the canonical container format (`FGVLTENC` v1) is designed so it can be added later without breaking backward compatibility:

- **`.bin` headers (`FGVLTENC` v1):** already carry a 2-byte plain `keyId` field (`u16`, currently `1`) at byte offset 9.
- **`FVSTR:` metadata records:** already write a 2-byte `keyId` field (`u16`, currently `1`) immediately after the 1-byte version.
- **`.vault` keyring structure:** stored with a length prefix (`keyringLen`).
  - `keyringLen == 34` → `1.0.0` single-key format: `activeKeyId(u16) | KEK(32)`.
  - `keyringLen > 34` → future multi-key format: `activeKeyId(u16) | keyCount(u16) | [keyId(u16) | KEK(32)] × keyCount`.

---

## 4. Architectural approach if added later for enterprise needs

If this feature is requested for **enterprise compliance** rather than personal use (e.g. PCI DSS 3.6/12.3, ISO 27001 A.8.24, NIST SP 800-57 key-lifetime policies, or mandatory key renewal after staff/device handover), the following architecture should be applied:

### 4.1. Multi-keyring and `kekFor(keyId)` infrastructure (Android + Python CLI)
- `CryptoManager` and `tools/calypso-cli/calypso_vault.py` should hold an in-memory keyring map (`unlockedKeyring: Map<Short, ByteArray>`) and an `activeKeyId: Short` instead of a single `unlockedKek`.
- `.vault` readers should load `keyringLen == 34` as a single key; for `keyringLen > 34` they should read `keyCount` `(keyId, KEK)` pairs.
- **Critical prerequisite (`FVSTR:`):** `decryptStringFromVault` must read the `keyId` value from the record and derive the key directly through `kekFor(keyId)`; every newly written `FVSTR:` record must use `activeKeyId` and `activeKek()`.
- **Critical prerequisite (`VaultContainer`):** the real API is `CryptoManager.readMetadata(file, includeThumbnail, cacheResult)` + `CryptoManagerStreams.readContainerMetadata(dis, kek, version, includeThumbnail)`; there is no `VaultContainer.readMetadata` method. `kekFor(keyId)` must be selected according to the `keyId` field in the header; only new/updated headers should be written with `activeKeyId`. `renameEncryptedFile` currently uses `activeKek()` without matching `meta.keyId`.
- In `clearKeys(context)`, **all** KEK arrays in the keyring map must be zeroed with `fill(0)`.

### 4.2. Keyring-first rotation with ADR 0003 durable operation architecture
1. **Prerequisites:** the vault must be unlocked and no file operation active; all other user/synchronization file operations must be locked during rotation.
2. **New KEK generation and `.vault` write first:**
   - Generate `newId = max(existing keyId) + 1` and a random 32-byte `newKek` (checking the `keyCount <= 256` limit).
   - **Without deleting old KEKs**, add the new `(newId, newKek)` to the keyring and write the `.vault` header with `activeKeyId = newId` **atomically first** (`.vault.tmp` + `fsync` + `rename`). This keeps files with both old and new `keyId` readable even if the operation is interrupted.
3. **Header conversion with ADR 0003 foreground service + durable journal:**
   - Run the rotation as a registered **`ForegroundOperationRegistry.withOperation` + `FileOperationService`** under ADR 0003 and ADR 0002 §7 (`PARTIAL_WAKE_LOCK` + notification + automatic `clearKeys` when finishing in the background), keeping a durable `.kek_rotation_journal` file.
   - For every `.bin` file:
     - Skip if the header `keyId == newId` (fully idempotent continuation after interruption).
     - Read the existing DEK, `fileId`, metadata and **embedded thumbnail (`thumbnailBytes`) in full** via `CryptoManager.readMetadata(..., includeThumbnail = true)`.
     - Re-encrypt only the header under `newId / newKek` into a temporary `.tmp` file via `VaultContainerWriter.rewriteContainer(...)` (`DEK`, `fileId` and Tink ciphertext bytes are preserved unchanged).
     - After Tink integrity verification (`verifyFileIntegrity`) on the temporary file, atomically move it over the original and update/clear the `metadataCache` entry.
4. **Optional metadata (`FVSTR:`) re-encryption:** re-encrypt active `FVSTR:` records in Room SQLite, `JSON/` and vault recycle-bin `.restore_info` files with the new `activeKeyId` (old KEKs remain in the keyring so old backups stay readable).
5. **External vault / CLI sync notice:** when rotation completes, clearly inform the user that older `.vault` files exported for external disk / PC CLI must be updated.

### 4.3. Retiring old keys (crypto-shredding / re-encryption)
- When the keyring limit (`256` keys) is reached or enterprise policy requires a KEK to be fully disabled (crypto-shredding), header rotation alone is insufficient: the affected files must be re-encrypted from plaintext with a new `DEK`, old KEKs removed from the keyring, and old encrypted backups securely deleted.

---

## 5. Mandatory verification criteria (if implemented in the future)

Before enabling KEK rotation in an enterprise build, all of the following tests must pass:
- Payload bytes remain bit-for-bit identical before and after rotation; the new header cannot be opened with the old KEK.
- `FVSTR:` records (album names, `JSON/`, restore manifests, resume blobs) are readable across all key periods before and after rotation.
- Embedded thumbnails (`thumbnailBytes`) are preserved 100% after rotation.
- Interruption simulation: after `N` files are converted the process is killed (`SIGKILL`); on restart the remaining files continue from where they stopped and no file becomes unreadable.
- Concurrent delete/move/encrypt requests are safely blocked or queued by the rotation lock.
- Android ↔ Python CLI (`calypso_vault.py`) multi-keyring read/write compatibility is verified by tests.

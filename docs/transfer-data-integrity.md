# Data integrity during transfer — current behavior, proposed changes, and remaining risks

Scope: what happens to data when copying, moving, encryption, and decryption operations are interrupted
(external disk removal, the app being closed, reboot, or being backgrounded and the process being
killed by Android). References: `DOCS.md` §2.4/§2.5,
[ADR 0001](adr/0001-tink-streaming-aead-migration.md),
[ADR 0002](adr/0002-operational-security-hardening.md),
[ADR 0003](adr/0003-resumable-file-operations.md), and
[crypto-design-review.md](crypto-design-review.md).

> **Document status:** §1–§3 and §6.1 retain the historical analysis and design proposal of the
> pre-change (*upstream/fork baseline*) behavior. [ADR 0003](adr/0003-resumable-file-operations.md)
> has been accepted and its core phases have been implemented; **the current state actually in the
> code** is documented in §5, §8, and ADR 0003 §11. §6.4 is a design proposal; its scope is limited
> to the actual code state in §8.
>
> **No independent cryptographic audit has been performed.** The container layout is project-specific;
> the use of well-known primitives does not prove the absence of design flaws.

---

## 1. Initial app behavior (pre-change historical analysis)

### 1.1 Core security invariant

In the current code, all four operation families follow the same pattern:

```
read from intact source  →  write to a NEW destination file  →  (on success) delete the source
```

The source is never truncated in place, overwritten, or split in two. Source deletion is always
deferred until after a successful write. Therefore, the **"half source + half destination" state
does not exist in the code**, and process death does not cause permanent data loss.

### 1.2 Behavior per operation

| Operation | Write path | Destination at interruption | Source at interruption |
|---|---|---|---|
| Plain copy / stream move (SD, OTG, SAF, or copy) | `getFileOutputStreamSync(dest.path, …)` → directly to the final path (`CopyMoveHelper.kt:912-918`) | **A partial file remains**; `catch` does not roll back, `finally` only closes the stream (`:923-928`) | Intact; deletion after `transferSuccess` (`:930-945`) |
| Same-FS move | `srcFile.renameTo(dstFile)` (`CopyMoveHelper.kt:729`) | Atomic, no partial file | Atomic |
| Encrypted `.bin` copy/move | `renameTo` on the same FS, otherwise stream directly to `destBin` (`CryptoManagerConflict.kt:503-525`) | Partial `.bin` on the stream path | Intact; `sourceBin.delete()` after success (`:523-525`) |
| Encrypt | `VaultContainerWriter.writeContainer(target = randomHex.bin)` (`CryptoManagerFileCrypto.kt:246-259`, `VaultContainerWriter.kt:99-125`) | Partial `.bin` (valid header + truncated payload) | Intact; `secureWipeAndDeleteFile` after write (`:285-300`) |
| Decrypt / export | `FileOutputStream(outFile)` (`CryptoManagerFileCrypto.kt:548-562`) | Partial plain file | First `verifyFileIntegrity` (`:473`); deletion after write (`:568-578`) |
| Album encrypt | All files are encrypted with `wipeSource = false`, then sources are deleted (`CryptoManagerFolderCrypto.kt:108-191`) | Partial output + random subfolder | All sources intact during the conversion phase |
| Album decrypt | All files are decrypted, then `.bin`s are deleted (`CryptoManagerFolderCrypto.kt:399-465`) | Partial plain files | All `.bin`s intact during the conversion phase |

The difference between an exception (disk removal, write error) and **process death**: on an exception,
`catch` blocks perform partial cleanup (`encryptedFile.delete()`, `outFile.delete()`, `destBin.delete()`);
on process death, no `catch`/`finally`/rollback runs, and the partial file definitely remains.

### 1.3 The A — B — C scenario

Interruption during transfer of B:

- **A:** Completed and committed; its source may have been deleted, but its destination is complete. Unaffected.
- **C:** No I/O started; untouched.
- **B:** Half of the source does not reach the destination — the source remains intact. However,
  **a partial file is created at the destination**, and no mechanism cleans it up or retries. On a
  retry with `KEEP_BOTH`, the partial file can become permanent junk with a `(1)` suffix.

What is lost is **the progress spent on B**; not the data.

### 1.4 Detection of corruption

A partial `.bin` is not silently accepted: the `ciphertextLength` in the header reports the full size,
`verifyFileIntegrity` checks the file length and rejects it (`CryptoManagerStreams.kt:177`); the viewing
path throws the Tink segment verification error as `IOException` / `GeneralSecurityException` and
loading fails; the yellow `!` badge, `allowUnverified`, and the `vault_corruption_reports` table
**have not been implemented** (see ADR 0001 fail-closed decision). So corruption is **detected, not
shown, and no resume option is offered to the user.**

### 1.5 Real (narrow) gaps in the current code

- On the encrypt path there is **no post-write read-back verification**; after `writeContainer` returns
  and syncs, the source is secure-wiped. In the event of silent write corruption, the good source could
  be deleted based on an unverified `.bin`. This is a **silent corruption** scenario, not process death.
- In a plain copy, "verification" is only `source.size == bytesCopied` + file existence
  (`CopyMoveHelper.kt:920`); the content hash is not compared.

---

## 2. Changes already present in the fork

This project (a Fossify Gallery fork) **already includes** the following with respect to data integrity:

- **Canonical container:** `FGVLTENC` v1; authenticated header with AES-256-GCM + Tink
  `AES256_GCM_HKDF` stream with a per-file DEK (256 KiB segments), AAD = `fileId`
  (ADR 0001, DOCS §2.5, `TinkStreamSpec.kt`).
- **Fail-closed verification:** a corrupt segment also causes a load error during viewing (no zero
  substitution); dedup, conflict, and pre-wipe decisions are made over bytes verified with
  `verifyFileIntegrity()`. The tolerant-viewing design (yellow `!` badge, corruption report,
  `allowUnverified`) was **rejected** in ADR 0001 §7.3 and is not in the code (DOCS §2.4 reflects
  the current state).
- **Partially atomic write patterns:** `renameEncryptedFile` `.tmp_<rand>.bin` + `Files.move(REPLACE_EXISTING)`
  (`CryptoManagerFileCrypto.kt:640-683`); External sync `.sync_tmp_*.bin` + `verifyFileIntegrity` + move
  (`CryptoManagerExportSync.kt:276-330`); virtual album and favorites manifests `*.tmp` + `renameTo`
  (`VirtualAlbumSyncManager.kt:271-294`, `ReferenceBackupManager.kt:113`). These patterns already
  prevent partial-file creation on **some** paths — but are absent from the main copy/encrypt paths.
- **Progress notification (pre-implementation):** `OperationProgressNotifier` + `CryptoProgressDialog`
  (notification only, without a foreground *service*; for the current foreground service see §8.2).
- **Design and implementation status:** The current code counterparts of ADR 0002 and ADR 0003 are in §8.

---

## 3. Proposed changes (see §8 for implementation summary)

The following items are the goals proposed in the initial analysis; the current code status is tracked in §8 and ADR 0003 §11.

1. **Staging + atomic commit (the real fix for the partial file).** Each item is written to a
   `.stage_<operationId>_<itemIndex>` file **in the same directory** as the final file; after
   `fd.sync()` + directory fsync it is moved to the final name with `Files.move(..., ATOMIC_MOVE)`.
   The final name becomes visible only after the commit. On FS/SAF that do not support `ATOMIC_MOVE`,
   **no non-atomic fallback is performed**; the item is counted as `FAILED` (see §5, risk 1).
2. **`pending_operations` journal.** In addition to the ADR 0003 §3 schema, record `stagingPath` and
   batch information (operationId, root paths, conflict decisions, album merge target).
   `PENDING/IN_FLIGHT` is written **before** the first byte, `VERIFIED` **after** the rename+sync,
   `SOURCE_DELETED` after the source is deleted. Thus a staging file left by a dead process is an orphan by definition.
3. **Read-back verification.** After encrypt, `verifyFileIntegrity` performs a full size+AEAD check on
   the `.bin` copy. The `Activity.tryCopyMoveFilesTo` plain→plain branch and
   `OperationRecovery.resumePlainCopy` also compare source/destination size + fingerprint; on a
   mismatch the unverified destination is securely deleted and the source is preserved. A corrupt
   source `.bin` is not copied (`verifyFileIntegrity` rejects it up front); there is no corruption-reporting capability.
4. **Startup reconciliation.** When no operation is running, staging files with no journal reference
   are cleaned up. **Staging files holding plaintext are deleted with a secure wipe, not a normal
   `delete()`** (ADR 0002 §7). `VERIFIED` items are checked against disk, `IN_FLIGHT` items are
   rewritten from the source. Virtual album manifests, MediaStore, and Room DB records are also covered.
5. **Foreground operation registry.** Wake lock, single owner token, transient-wait and cancellation
   flow (ADR 0002 §7 + ADR 0003 §7). After process death, the vault is reopened for encrypted
   operations; the journal never holds keys or plaintext.
6. **Adaptation of the storage check** (see §6).

---

## 4. Data loss: the practical-zero target

Permanent data loss due to process death is **targeted at practically zero**, because the single good
copy is always either in the intact source or in the atomically committed destination. The "file B was
split into irreconcilable pieces and lost" scenario does not exist in the current code or the proposed code.

This target relies on the following assumptions:

- the staging file is kept **in the same directory** as the final destination and on the same FS,
- on `ATOMIC_MOVE` failure there is no silent fallback to a non-atomic path,
- read-back verification is actually implemented and the `VERIFIED` record comes before source deletion,
- in the event of journal loss, the worst outcome is a **duplicate/orphan file** and a name/album
  shift, not data loss,
- the message shown to the user is **not** "your data was lost", but, as in ADR 0003, "the previous
  operation was interrupted, no data loss, continue?"

---

## 5. Remaining risks (after the changes)

| # | Risk | Description / mitigation |
|---|---|---|
| 1 | **No atomic rename guarantee (partially addressed)** | Copy/move, SAF, and external sync paths now use staging + atomic commit: `Files.move(ATOMIC_MOVE)` on direct `File` destinations, `DocumentFile.renameTo()` with a sibling `DocumentFile` on SAF destinations, and `ATOMIC_MOVE` first in `CryptoManagerExportSync`; if unsupported, a rename is allowed only on the **same file store**, otherwise the item is `FAILED`. There is no cross-store copy-delete fallback. **Remaining:** some SAF providers do not support rename at all; in that case the operation is safely rejected. |
| 2 | **Verification scope/cost** | Full `verifyFileIntegrity` is performed on encrypted `.bin` copy (`copyOrMoveEncryptedFileToDirectory`), after encrypt, and on the external sync path (`allowFastSampling = true` in the conflict pre-scan is only for checking candidate dedup). On a plain copy, only size + `MediaIdentity` fingerprint (full-stream SHA-256 on SAF) is checked, and on decrypt only output-length verification is done. Full verification incurs a 2x read cost on large videos; this cost must be accepted. |
| 3 | **Journal loss / rejection** | The journal is app-private; if app data is deleted or expires (ADR §10 open question), or the user declines resume: committed items whose source was not deleted remain as **duplicates**; `resolvedDestName` is lost → `(1)/(2)` shifts; the album batch record is lost → a new random subfolder (`CryptoManagerFolderCrypto.kt`). |
| 4 | **Non-file stores are not atomic (partially addressed)** | `MetadataReconciler` at startup performs `updateDBMediaPath` for committed items (MediaStore + Room medium/date_takens/favorites/virtual albums + manifest), fixes the virtual album `parentPhysicalPath`, and rescans MediaStore for plain files. Marking is done **per item** (`operationId:itemIndex`), so a second process death during resume ensures newly completed items are also reconciled; stale keys of cleaned-up operations are pruned from prefs. **Remaining:** folder config and thumbnail cache consistency (see §8.3.2 — intentionally out of scope). |
| 5 | **Foreground service limits** | Force-stop kills without calling `onDestroy` (the journal remains `IN_FLIGHT` — resume handles it). `FileOperationService` + `ForegroundOperationRegistry` cover all operation paths and include `ACTION_CANCEL` notification cancellation and `TransientOperationWaiter` (120 s timeout); however, OEM battery optimizer / Doze can still pause the service, or the external volume may not come back. |
| 6 | **Plaintext staging trace** | Decrypt and plain-copy staging files hold plaintext; if deleted with `delete()`, a trace remains on flash. Secure wipe is required (ADR 0002 §7). For ciphertext staging, a normal `delete()` is sufficient. |
| 7 | **Per-item and batch space check (partially addressed)** | `SpaceEstimator` performs a batch check only on stream copy/move + album encrypt/decrypt paths. `TransientOperationWaiter.awaitDestinationReady` checks per item, but because the `StatFs` fallback returns `verifiable = false`, this per-item check is skipped. For mid-write `IOException`, `runWithTransientRetry` **exists** (encrypt/decrypt/.bin copy + `CopyMoveHelper` direct-file; `maxAttempts = 2`, 1 on SAF); plain→plain `tryCopyMoveFilesTo` and `resumePlainCopy` are out of scope. |
| 8 | **Concurrency (addressed)** | `ForegroundOperationRegistry` serializes operations running concurrently on different threads with a single owner token (calls within the same thread are re-entrant). Staging names are unique via `operationId + itemIndex`. |

**None** of these risks is of the form "half of the source and destination remain, causing
irreconcilable data loss"; they are all in the category of "dirty state, pending operation,
duplicate, incorrect verification, or privacy trace".

---

## 6. Low storage space and the "storage sufficiency" feature

### 6.1 Initial (pre-implementation) check

Before ADR 0003 §3a, the storage sufficiency check in the app existed **only** inside `CopyMoveHelper.prepareAndRunCopyMove`:

- It runs only if `useStreamCopy == true` (SD/OTG/SAF or copy). On a same-FS move, `renameTo` is used,
  so the check is skipped — correct, since rename requires no extra space.
- `availableSpace = destination.getAvailableStorageB()`; `sumToCopy = Σ item.size`.
- If `sumToCopy >= availableSpace`, the operation is blocked and a `no_space` toast is shown
  (equality is also blocked — current conservative behavior).
- If `getAvailableStorageB()` returns `-1` (which can happen on SAF/OTG), the check is **skipped entirely**.

**Critical finding:** a search for `getAvailableStorageB` in the code returns results only in this
file. That is, **encrypt, decrypt, album operations, and plain→encrypted vault copying are subject
to no space check at all.** The "storage setting sufficiency" feature is already incomplete in scope.

### 6.2 Does staging actually use more space?

**No — not when implemented correctly.** Peak usage comparison for a single item:

```
Direct write    : source(full) + final_file(0→size) + previous items
Staging + rename: source(full) + staging(0→size)     + previous items   → rename is free (metadata)
```

Because a rename within the same directory does not copy data, staging does **not create an
additional full copy** compared with direct writing. The "2x peak usage" statement in a previous
note is wrong; the correct picture is: both methods hold *source + one output file* during the
transfer, the only difference being whether the output is visible.

### 6.3 Remaining space risks nevertheless

1. **Ciphertext expansion.** Tink writes a 16-byte GCM tag per 256 KiB segment (`TinkStreamSpec.kt:44,78-84`)
   and the container header contains the DEK, name, date, and **a thumbnail of up to 5 MiB**. The
   payload expansion factor is ≈ `262144/262128 ≈ 1.000061` (~64 bytes per 1 MB) + header. The sum of
   source sizes **underestimates** this difference; low space with the `>=` threshold can produce a
   "sufficient" answer while the write hits ENOSPC midway. No data loss (source intact), but the operation is cancelled.
2. **Real 2x if staging is placed on a different FS.** If staging is written to the app-private cache
   and the final file to the external vault directory, the "atomic" move becomes a copy-delete and
   temporarily creates two full copies for that item. Therefore staging **must be in the final directory** (§3/1).
3. **Scope gap.** There is no check at all on encrypt/decrypt paths (§6.1). The proposed journal
   schema should also hold the space estimate: `expectedCiphertextSize(originalSize) + header + thumbnail` total.
4. **Stale check.** A single check at batch start does not see free space changing during a long
   operation (§5, risk 7).
5. **Orphan staging files.** Staging files left by previous dead operations reduce actual free
   space; the runtime check sees this correctly, but the user may perceive it as "why is there no
   space left". Startup cleanup should remove them.
6. **Overestimation.** When `copyMediaOnly` is enabled, non-media files are skipped but `sumToCopy`
   counts them too → unnecessary `no_space` warning (current behavior).

### 6.4 Implementation plan (ADR 0003 §3a — accepted)

The space check was defined as part of ADR 0003 in the **"Decision — space budgeting"** section
(§3a) and in a §9 checklist item. Summary:

1. **Estimator (limited scope):** `SpaceEstimator` + pure `VaultContainerWriter.estimateEncryptedSize(...)`;
   used only inside `CopyMoveHelper` stream copy, `encryptFolderRegistered`, and
   `decryptFolderRegistered`. `tryCopyMoveFilesTo`, single/multi-file crypto,
   `encryptStreamToDirectory`, `exportEncryptedVault`, and `syncVaultFromExternalDirectory` are out
   of scope. `forDecrypt` / `forEncryptedCopy` are not called; `decryptFolder` uses `.bin` lengths
   instead of the real `meta.originalSize`. Encrypt formula:
   `143 + UTF8(name) + thumbnail + expectedCiphertextSize(size)` (143 = 43 fixed prefix + 84 fixed header fields + 16 GCM tag).
2. **Album peak usage:** because `encryptFolder`/`decryptFolder` write all outputs and only then
   delete the sources, `peakBytes = Σ(all outputs)` — not a single file but the whole batch.
3. **Timing:** at batch start (on in-scope paths) + per-item `TransientOperationWaiter` check;
   during resume, `estimatedRequiredBytes` / `peakBytes` are **not re-checked**.
4. **`-1` / unverifiable-space policy:** `SpaceEstimator.probe` returns `verifiable = false` even in
   the `StatFs` fallback; `SpaceConfirmation` asks for explicit confirmation on every `!verifiable`
   case, and on SAF there is no automatic continuation without user approval. On verifiable
   insufficient space, still only a `no_space` toast is shown (there is no required/available dialog).
5. **Threshold:** if `required + max(64 MiB, 1%) > available`, block or transition to
   `WAITING_FOR_SPACE` (ADR §2 transient-wait layer, with notification and cancellation flow).
6. **Staging:** `AtomicStaging` + `ATOMIC_MOVE` are used on the four core write paths;
   `syncVaultFromExternalDirectory` uses `.sync_tmp_*.bin` + `renameTo` fallback;
   `exportEncryptedVault` and `encryptStreamToDirectory` do not use staging.
7. **Journal/UI:** `estimatedRequiredBytes` is written only in album encrypt/decrypt records;
   resume does not read these fields and does not show required/available information. The
   `no_space` toast prints the `peakBytes` value without margin.

Test coverage: exact threshold, N-file album peak usage, maximum thumbnail, SAF `-1`, orphan
accumulation, disk removal and reinsertion, `KEEP_BOTH`, mid-write ENOSPC, resume after force-stop.

---

## 7. Disclaimer

- This document **is not a guarantee.** The software is provided "as is"; no express or implied
  commitment is made that no data loss, corruption, or privacy breach will occur.
- This document is updated as ADR 0003 implementation progresses; if code and document conflict, **the code prevails.**
- On flash storage, CSPRNG overwrite + `fsync` is best-effort; due to FTL wear-leveling and journal
  metadata, application-level "forensically traceless deletion" is not possible (DOCS §2, §2.4.7).
- Tink/AES-256-GCM/Argon2id are well-known primitives; however, the container layout, AAD
  structure, and key hierarchy are project-specific and **have not undergone independent external
  audit.**
- Backups are the user's responsibility. The original bits of a corrupted encrypted segment
  cannot be recovered cryptographically; only an existing backup can restore them (DOCS §2.4.6).
- The phrase "practically zero data loss" is a valid target when **all** of the assumptions in §4
  are met; it is not a guaranteed outcome.

---

## 8. Implementation status (Phases 1–3) and optional real-device tests

### 8.1 Coded in Phase 1

- A separate Room DB (`pending_operations.db`), `PendingOperation` entity, and DAO (with `upsertAll`
  bulk operation and a single indexed `findItem` query).
- `OperationJournal` API (`PENDING → IN_FLIGHT → VERIFIED → SOURCE_DELETED`; `FAILED/SKIPPED`),
  batch writes within a single atomic transaction, and `requiresSourceDeletion` / `needsResume`
  rules with the `COPY` vs `MOVE/wipe/delete` distinction.
- `AtomicStaging`: `.stage_<operationId>_<index>.tmp` file in the same directory, `fd.sync()` +
  directory fsync (avoids double sync via `alreadyFileSynced = true` if the caller already synced) +
  `ATOMIC_MOVE`; `FAILED` if unsupported (no copy-delete fallback).
- Four core write paths updated with staging + verification + journal: `CopyMoveHelper.copyFile`
  (plain copy/move, 256 KiB buffer and single-pass SHA-256 during SAF streaming),
  `encryptFileToDirectory` (full `verifyFileIntegrity` after encrypt),
  `decryptFileToDirectory` (length verification), `copyOrMoveEncryptedFileToDirectory`
  (full `verifyFileIntegrity` after copy).
- Source/destination fingerprint comparison on plain copy; secure wipe of plaintext staging on errors.
- `SpaceEstimator` + pure `VaultContainerWriter.estimateEncryptedSize(...)`; plain-copy space check
  wired to the new estimator and an explanatory warning added for SAF `-1` (`space_unverified_warning`).
- Debug-only `OperationFaults` hook and pure JUnit tests (`ResumableOperationsTest`).

**Album/Activity batch hooks were also wired up:**

- `encryptFolder` / `decryptFolder`: batch journal, `peakBytes = Σ outputs` space check, per-item
  item context, fault injection, journal cleanup after success/full rollback, `SOURCE_DELETED` /
  `SKIPPED_CONFLICT` recording at the `wipeSource` / `deleteSource` source-deletion steps. Moving
  existing `.bin` files is also journaled as a separate item.
- `tryCopyMoveFilesTo` (Activity): a single journal for mixed batches inside/outside the encrypted
  vault, item context per element, duplicates `SKIPPED_CONFLICT`, staging + fingerprint verification
  in mixed plain→plain copies, journal cleanup on success.

### 8.2 Phase 2 — process-death recovery

- `OperationRecovery.reconcile()`: exits early at startup if `ForegroundOperationRegistry.isActive`;
  otherwise cleans up orphan `.stage_*` files (secure-wiping those without vault magic), runs
  `MetadataReconciler.reconcile()`, resets `IN_FLIGHT/COPIED_UNVERIFIED/WAITING_FOR_SPACE`
  records to `PENDING`, and cleans up only
  completed batches with no `needsResume` item left.
- `OperationRecovery.resume()`: uses the journal's batch flags (`isMove`, `wipeSource`,
  `deleteSource`, per-item `conflictResolution`) to rerun the four operation types item by item on a
  background thread (with `CryptoProgressDialog`); items whose copy/encryption to the destination was
  verified (`STATE_VERIFIED`) but whose source deletion was interrupted are completed with
  `completePendingSourceDeletion`, and empty album folders are cleaned up with `finalizeAlbumRootsIfEmpty` if needed.
- **Single and definitive dialog:** called in `MainActivity.onResume` (including
  `promptToResumeVault` / `enterSecretVault`) and in `MediaActivity.onResume` while the vault is
  open; `reconcile` runs on a background thread. Plain `COPY/MOVE` operations are asked about on
  app entry, while encrypted/vault operations are asked about separately only once the vault is unlocked (`selectPromptBatch`).
  - In standard encryption, if the app was closed after `.bin` verification completed
    (`STATE_VERIFIED` `KIND_ENCRYPT`) but before the original plain files were deleted, the secure
    wipe completes automatically in the background at vault open without asking the user (`resumeSilentPhase2EncryptWipes`).
  - If the app is closed while `SkippedCryptoFilesDialog` is open for files skipped during
    transfer (no decision made in `ASK` mode), the same confirmation box reopens at vault open;
    if the setting is `DELETE`, or the user approved deletion but was interrupted, deletion
    completes in the background without asking (`processPendingSkippedCryptoWork`).
  - For interrupted Phase-1 operations, the right-hand button is **Evet** (Yes — continue) and the
    left-hand button is **Hayır** (No — cancel permanently). If **Hayır** is chosen, the journal is
    permanently deleted, the skipped-file Secure Erase prompt does not open, and only the
    `<İşlem> işlemi yarıda bırakıldı` ("<Operation> was interrupted") (`Tamam` / "OK") notification is shown.
- `FileOperationService` + `ForegroundOperationRegistry`: re-entrant ownership counter within the
  same thread (`depth` + `ownerThread`), persistent notification (`FOREGROUND_SERVICE_TYPE_DATA_SYNC`),
  `ACTION_CANCEL` cancellation support, `PARTIAL_WAKE_LOCK`, and protection preventing
  `VaultSessionManager` from wiping the in-RAM vault key during an active operation. All plain
  copy/move, `resume`, `encryptFolder`/`decryptFolder`, and `Activity`/`DirectoryAdapter`/`MediaAdapter`
  entry points are registered; while an operation is active, `reconcile`/orphan cleanup is skipped.

### 8.3 SAF atomic commit (Phase 3/1)

- `CopyMoveHelper.copyFile`: a sibling `DocumentFile` staging file is created for a SAF destination
  (`.stage_<op>_<index>.tmp`), fully written, then moved to the final name with `DocumentFile.renameTo()`.
  If the rename fails, the item is `FAILED`, the staging file is deleted, and the **source is preserved** — no copy-delete fallback.
- Verification: because there is no `File` path on SAF, full-stream SHA-256 (`AtomicStaging.streamFingerprint`)
  is used; on the direct-File path `MediaIdentity` (first/last 64 KB) continues.
- `OperationRecovery.cleanupOrphanStaging`: also deletes `content://` staging URIs in the journal.
- `CryptoManagerExportSync` now also tries `ATOMIC_MOVE` first; a `renameTo` fallback is allowed
  only on the same file store; on a different store the item is `FAILED` (source preserved).
- Remaining SAF note: some providers do not support rename at all; in that case the operation is
  safely rejected and no partial file is created.

### 8.3.1 Metadata reconciliation (Phase 3/2)

- `MetadataReconciler`: at startup after process death (from within `OperationRecovery.reconcile`)
  repairs the path references of committed (`VERIFIED` / `SOURCE_DELETED`) items.
- For `MOVE/ENCRYPT/DECRYPT`, the existing `Context.updateDBMediaPath` is called: Room
  `media`/`date_takens`/`favorites`/`virtual_albums` and the related JSON manifests are updated in
  one shot. For `COPY`, since the source is preserved, only the new destination's identity is recorded.
- If the virtual album's own `parentPhysicalPath` still points to the old folder, it is corrected and
  the manifest rewritten; a MediaStore rescan is performed for plain files.
- **Idempotency and cleanup:** processed **items** (`operationId:itemIndex`) are held in
  app-private SharedPreferences; thanks to UPDATE-by-old-path semantics, a second call does not
  duplicate rows. Thus, if a second process death occurs during resume, items that were `PENDING`
  in the first pass and later completed are reconciled again at the next startup. The
  `reconciled_items_v2` key invalidates records from the old operation-based marker and
  automatically prunes records of completed/deleted operations that are no longer in
  `OperationJournal.operationIds()`.
- Reconcile covers only committed items; interrupted items are updated in their own write paths
  during resume.

### 8.3.2 Intentionally out of scope: folder config and thumbnail cache

The `included/excluded/encrypted` folder lists (`config`) and the thumbnail cache are secondary
stores updated alongside file operations. A process death may temporarily leave these lists at the
old paths; however, **no code changes for them are planned within the scope of this document.** Rationale:

- folder lists are already updated by the existing post-operation hooks
  (`config.addEncryptedFolder` / `removeEncryptedFolder` / `replaceExactExcludedFolder`) and can
  self-correct on the next successful operation;
- the thumbnail cache is already reproducible derived data, not a persistent source of truth;
- including them in the journal would add another dependency to ADR 0003's "source paths remain
  intact" security model, and the benefit is marginal in terms of data integrity.

This is a conscious limitation; ADR 0003 must be read together with 0001/0002.

### 8.3.3 Explicit space confirmation (Phase 3/3) and transient-interruption waiting

- `SpaceConfirmation.askAsync`: in the plain copy/move flow, when the result is `-1` (SAF/OTG),
  shows a dialog titled "free space could not be verified"; the required size and the reason are in
  the explanatory message. If confirmed, the operation starts; if declined, the progress dialog closes.
- `SpaceConfirmation.confirmBlocking`: obtains the same confirmation for synchronous background
  operations such as album encrypt/decrypt; never blocks on the main thread (returns `false` there).
- `TransientOperationWaiter`: on temporary destination-path unreachability or insufficient free
  space, while an active foreground operation exists, puts the item into `WAITING_FOR_SPACE` and
  waits up to 120 seconds at 1-second intervals with local notification text
  (`waiting_for_free_space_or_storage`) (can be cancelled early via `isCancelled`); additionally,
  on all four write paths, for mid-stream `IOException` errors `runWithTransientRetry` cleans up
  partial staging and retries the item once storage returns.
- New strings (space warning, waiting notification, and operation-specific cancellation texts) were
  added to **11 active locale resources** (other languages fall back to English). Phase-2 encryption
  wipes run silently; there is no such thing as a "Phase-2 secure-wipe prompt".

**Completed Phase 3/4 items:** `ForegroundOperationRegistry` registration of album and Activity
batch operations, `resolvedDestName` conflict-suffix pinning, reuse of a committed destination by
size + fingerprint, album-merge `mergeTargetRoot` / `foldedSourceRoots` tracking, one-time Yes/No
recovery decision, automatic Phase-2 encryption secure wipe, `SkippedCryptoFilesDialog`
decision persistence, and mid-stream `IOException` retry are completed.
The status summary is under
[ADR 0003 §11](adr/0003-resumable-file-operations.md#11-implementation-status).

### 8.4 Optional real-device tests (no automation)

The following cannot be simulated with JUnit; they must be verified manually on a real device:

1. **Interruption simulation — file copying:** copy 20 large files to an SD card; during the 5th file,
   go to Settings → Apps → **Force stop**. Reopen: check that the source files are intact and that no
   partial file exists at the destination (only a `.stage_*.tmp` orphan may remain).
2. **External disk removal:** during transfer, remove the SD/USB, reinsert it, and retry the
   operation; verify that the source was not deleted and the retry completed.
3. **Death during encrypt:** force-stop while encrypting a large video; verify that the plain source
   opens and that no orphan staging is left in the vault (rather than a corrupt `.bin`).
4. **Death during decrypt:** same; verify that the `.bin` source remains intact.
5. **Low space:** nearly fill the destination; observe that the margin blocks the operation or, on
   SAF, shows an explanatory warning and continues.
6. **Album operations:** kill the app while encrypting a multi-file album; check that all sources
   remain intact and that the `peakBytes = Σ outputs` rule was calculated correctly.

> `OperationFaults` reads only a JVM system property (`cipher.failAfterItem`); it cannot be set via
> Android `setprop`. On-device scenarios can be run with the manual steps above, and instrumented
> tests by setting the property in-process.

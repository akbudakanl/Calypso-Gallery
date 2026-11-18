# ADR 0003 — Resumable file operations

- **Status:** Implemented — all ADR 0003 code items complete (see §11; optional hardware tests in §8.4 of results doc)
- **Date:** 2026-11-17 (updated 2026-11-18)
- **Relates to:** ADR 0002 §7, DOCS.md §2.4/§2.5, `docs/transfer-data-integrity.md` §6/§8
- **Reading guide:** this ADR records the *decisions and rationale*. The current runtime state,
  phased implementation notes and residual risks are in `docs/transfer-data-integrity.md` §8;
  §11 below maps the checklist to that state.

## 1. Context

ADR 0002 establishes a registered-foreground-operation model with two interruption layers:
a foreground service that waits out transient failures (external disk removed, write error) and resumes from a durable journal, plus a single "interrupted, continue?" dialog when the process actually died.

The same class of failure affects ordinary file operations: copy/move (plain ↔ plain, plain ↔ encrypted, encrypted ↔ encrypted), and encrypt/decrypt of files and albums. The upstream code already protects against **data loss** (a move deletes the source only after the destination is verified — `CopyMoveHelper.copyFile`, `transferSuccess`), but it leaves a partially written destination behind and offers no resume: the user must clean up and restart manually.

This ADR specifies a unified resume framework for those operations; §9 and §11 track its current implementation state.

## 2. Decision — two interruption layers

| Layer | Trigger | Behaviour | User prompt |
|---|---|---|---|
| **Transient wait** | Process alive; external volume unmounted, write error, temporary I/O failure | Service stays alive, keys stay in RAM, notification/progress shows "waiting", polls until the resource returns, then continues from the journal | None |
| **Process death** | App killed, reboot, user force-closed, service cancelled | Journal persists; Encrypt Phase-2 wipes & approved skipped deletes finish silently on vault unlock, unanswered `ASK` skipped-crypto dialogs re-open, and incomplete Phase-1 transfers show one Yes/No prompt (plain on app start; vault/encrypted on vault unlock) | **One-time Yes/No** per eligible batch (`No` clears permanently and never re-prompts) |

The resume dialog is deliberately shown once per interrupted operation, with no persistent preference: a partially copied or encrypted file does not change the vault format, so there is no risk the user might miss a state change.

## 3. Decision — journal location and schema

- **Ordinary operations:** app-private Room table `pending_operations` (JSON payload). Rationale: device-local, survives app restart, does not pollute the vault, is not visible to the CLI, and is wiped with app data (acceptable — source files remain intact).

Per-item journal record:

```
operationId, kind (COPY|MOVE|ENCRYPT|DECRYPT), createdAt,
sourcePath, destPath, sourceSize, sourceFingerprint,
stagingPath,               // same directory as destPath; orphan after process death
batchConflictResolution (SKIP|KEEP_BOTH|OVERWRITE),
resolvedDestName,          // final name actually chosen, incl. (1)/(2) suffixes
albumConflictResolution,   // MERGE | CREATE_NEW(n) for album operations
state, updatedAt
```

Batch-level record additionally holds the source/destination roots, the conflict decisions, the
album merge target, and `estimatedRequiredBytes` / `peakBytes` from §3a so resume re-checks against
the same numbers.

Per-item state machine:

```
PENDING → IN_FLIGHT → COPIED_UNVERIFIED → VERIFIED → SOURCE_DELETED
                   ↘ FAILED           ↘ SKIPPED_CONFLICT → AWAITING_SKIP_CONFIRM → DELETE_SKIPPED_APPROVED
                   ↘ WAITING_FOR_SPACE → IN_FLIGHT
                   ↘ SPACE_CHECK_FAILED
```

`SOURCE_DELETED` is only reachable from `VERIFIED` — this is the hard invariant that guarantees no data loss.

## 3a. Decision — space budgeting (low-storage operations)

The staging protocol must not inherit the gaps of the *original* check. Before this ADR was
implemented, `getAvailableStorageB` was used only in `CopyMoveHelper.prepareAndRunCopyMove` and only
when `useStreamCopy == true`; it compared `Σ source.size` with `>= availableSpace` and skipped the
check entirely when the API returned `-1`. Encrypt, decrypt, album operations and plain → vault
copies had no space check at all. This is now implemented via `SpaceEstimator`, per-item re-checks
and an explicit confirmation dialog for the `-1` case (see §11 and the results document §8.3.3).

- **Single estimator.** One `OperationSpaceEstimate` / `SpaceEstimator` component is used by every
  path: plain copy/move, plain ↔ encrypted, encrypted `.bin` move/copy, file and album
  encrypt/decrypt, stream import, and external sync/export. Inputs are operation kind, source sizes,
  target format, conflict resolution and `resolvedDestName`; outputs are `requiredBytes`, `peakBytes`
  and `confidence`.
- **Size formulas.**
  - Plain copy: `Σ source.length()` (`getProperSize` fallback when size is 0).
  - Encrypt: `143 + UTF8(resolvedName) + thumbnailBytes + expectedCiphertextSize(originalSize, segmentSize)`
    — 143 = `FIXED_PREFIX_LENGTH` (43) + fixed header fields (84) + GCM tag (16). A **pure**
    `VaultContainerWriter.estimateEncryptedSize(...)` mirrors `prepare()` so the formula has one
    source of truth. The thumbnail header field can be up to 5 MiB.
  - Decrypt: `Σ meta.originalSize` (exact, read from the container header before writing).
  - Encrypted `.bin` copy/move: `Σ sourceBin.length()` (exact).
  - **Album operations are the dominating case:** both `encryptFolder` and `decryptFolder` write
    *all* outputs before wiping *any* source, so `peakBytes = Σ(all outputs)` while all sources are
    still present — not one item at a time. Generic batched operations commit item by item and need
    only one output at a time.
  - A small fixed margin covers journal rows, `.nomedia`, `.meta`, manifests and DB writes.
- **When to check.** Compute `peakBytes` at batch start, then re-check before each item enters
  `IN_FLIGHT` (external volume may be removed, other apps may consume space, orphans from dead
  operations may occupy it). Startup orphan cleanup runs **before** the check.
- **Unverifiable space (`-1`).** Never silently skip. Retry with `StatFs` for real `File` paths; for
  SAF/OTG Uris (no reliable API) show an explicit "space could not be verified" warning and proceed
  with per-item error handling, or let the user choose to abort.
- **Threshold.** Block/enter `WAITING_FOR_SPACE` when `requiredBytes + max(64 MiB, 1%) > availableSpace`.
  Preserve the existing conservative behaviour that an exact fit is not treated as sufficient.
- **Staging interaction.** Staging lives in the **same directory as the final destination**; rename is
  then metadata-only and adds no second full copy. The estimate still includes the staged bytes.
  If `ATOMIC_MOVE` is unsupported, the item becomes `FAILED` — never degrade to copy+delete, which
  would both reintroduce partial files and double peak usage.
- **Journal + UI.** `estimatedRequiredBytes` / `peakBytes` are stored per batch; the transient-wait
  layer (§2) shows "waiting for free space" with a cancel option, and a one-time dialog reports
  needed vs. available instead of the current bare toast.

## 4. Decision — conflict semantics on resume

- The batch's conflict choice (`CONFLICT_SKIP / KEEP_BOTH / OVERWRITE`, and `ALBUM_CRYPTO_CONFLICT_MODE`) is recorded once and **reused** on resume; the user is not re-asked for every file.
- `resolvedDestName` is recorded per file, so `KEEP_BOTH` suffixes `(1)`, `(2)` reproduce exactly the same destinations instead of colliding or shifting.
- If a destination from the first attempt already exists: compare size + fingerprint. Match → treat as `VERIFIED`, skip rewrite. Mismatch → treat as partial and rewrite from source.
- For album merge conflicts, the journal records the merge target and which source albums were already folded in; resume never merges the same album twice.

## 5. Decision — pending conflict dialogs are not "in progress"

While a standard or smart conflict dialog is still open for an item, **no I/O is in flight** for that item, so the journal must not mark it `IN_FLIGHT`. If the process dies at that moment, the batch simply stops before that item; on resume the dialog is re-asked (or the recorded batch decision is applied). This keeps the journal limited to items with real I/O and removes a whole class of ambiguous states.

## 6. Decision — skipped files, declined resume, and Phase-2 secure wipe

- A source is "skipped" only when a conflict decision was **settled** (`SKIPPED_CONFLICT`) and the source is still intact.
- A source whose destination write or verification failed is **never** eligible for secure erase, even if it resembles a duplicate.
- The resume dialog is a **one-time Yes / No decision** (plain copy/move on app start; vault/encrypted operations only upon vault unlock):
  - **Automatic Encrypt Phase-2 wipe:** if an interrupted `KIND_ENCRYPT` batch already committed and verified `.bin` files (`STATE_VERIFIED` with `wipeSource = true`), unlocking the vault silently finishes secure-wiping the plaintext originals in background without asking the user again (re-verifying each container before wiping).
  - Incomplete Phase-1 transfers / copy / move / decrypt show a single operation-typed prompt asking whether to continue.
  - Selecting **No** permanently clears those Phase-1 journal entries (never prompting again), keeps already-completed files untouched without data loss, shows `<Operation> aborted` (`OK`), and does **not** surface skipped-file Secure Erase prompts.
  - **Completed-transfer skipped crypto decisions (`SkippedCryptoFilesDialog`):** when an encrypt/decrypt operation finishes normally and enters `resolveSkippedCryptoFiles`, skipped files stay tracked in `pending_operations` as `AWAITING_SKIP_CONFIRM` or `DELETE_SKIPPED_APPROVED` until settled:
    - If `DELETE` is already chosen in settings (`CRYPTO_SKIPPED_FILES_DELETE`) or confirmed before interruption (`DELETE_SKIPPED_APPROVED`), next vault unlock silently completes deletion in background without re-prompting.
    - If `ASK` mode was active and the user hadn't answered yet when killed, next vault unlock re-opens `SkippedCryptoFilesDialog` for those exact skipped files.

## 7. Decision — foreground service and operation registry

A single foreground-operation registry (extension of ADR 0002 §7) owns:
- keys in RAM for the duration of the operation,
- notification progress,
- transient-wait state,
- cancellation (which ends waits and flushes the journal).

Two concurrent file operations are serialized through the registry so wipe/key decisions cannot race.

## 8. Safety invariants

1. No authentication tag is ever recomputed over unverifiable bytes.
2. No source is deleted unless its destination is `VERIFIED`.
3. No dedup/skip decision is made from unverified bytes.
4. Interrupted temp files are cleaned at startup only when no operation is running. Implemented by
   `OperationRecovery.cleanupOrphanStaging`, which deletes `.stage_*` orphans and securely wipes any
   staging file that does not carry the vault magic (i.e. may hold plaintext), including `content://`
   SAF staging documents. It is skipped while an operation is registered with the foreground registry.
5. The resume prompt is one-time and carries no "always ask" preference for ordinary operations.
6. No batch starts when `peakBytes` exceeds available space plus the margin (§3a), unless the user
   explicitly acknowledges that free space could not be verified.

## 9. Implementation checklist

Status markers: **[DONE]** implemented, **[PARTIAL]** partly implemented, **[PENDING]** not implemented.
Details and code pointers are in §11 and in `docs/transfer-data-integrity.md` §8.

1. **[DONE]** Room table `pending_operations` + schema registration (including `stagingPath` and batch estimates, single-transaction batch `upsertAll`, and indexed `findItem` lookup).
2. **[DONE]** `SpaceEstimator` + `VaultContainerWriter.estimateEncryptedSize(...)` wired into operation
   entry points; state machine includes `WAITING_FOR_SPACE` / `SPACE_CHECK_FAILED`, and
   `TransientOperationWaiter` handles both pre-item readiness polling (`awaitDestinationReady`) and
   mid-stream `IOException` retry (`runWithTransientRetry`, discarding partial staging and waiting
   for storage/space recovery while a foreground operation is active).
3. **[DONE]** Journal hooks in `CopyMoveHelper` (copy/move, plain and encrypted, cross-album, including
   `renameTo` fast-path, `CONFLICT_SKIP`, and non-colliding top-level vs. child `itemIndex` tracking).
4. **[DONE]** Journal hooks in encrypt/decrypt paths (file, album, and `tryCopyMoveFilesTo` / `MediaAdapter`
   batches), including per-item `kind`, `conflictResolution`, `resolvedDestName` suffix pinning,
   album `mergeTargetRoot` / `foldedSourceRoots` tracking, actual committed `destPath` on `markVerified`,
   and rollback cleanup.
5. **[DONE]** Staging + same-directory atomic commit (`ATOMIC_MOVE`, SAF `DocumentFile.renameTo`, no
   cross-store copy+delete fallback) in all write paths, including external-vault sync.
6. **[DONE]** Notification/progress state, explicit `-1` confirmation, `TransientOperationWaiter`
   pre-item + mid-stream retry loop, localized `waiting_for_free_space_or_storage` notification
   string (11 locales), and `FileOperationService` notification Cancel action (`ACTION_CANCEL`).
7. **[DONE]** One-time process-death resume dialog (`Yes` / `No`, non-cancelable, plain ops on start,
   vault ops only on vault unlock), permanent journal clear + `<Operation> aborted` notice on `No`
   (without surfacing skipped-file Secure Erase prompts), silent background Phase-2 encrypt
   secure-wipe completion on vault unlock, and persisted post-transfer `SkippedCryptoFilesDialog`
   recovery (silent background delete if setting is `DELETE` or approved; re-open dialog if `ASK` was
   unanswered).
8. **[DONE]** Startup reconciliation: orphan staging cleanup (background thread), stale active-state
   reset, non-resumable (`COPY` `VERIFIED`, `FAILED`, etc.) operation pruning, matched-destination
   size+fingerprint reuse on resume, and per-item idempotent reconciliation of MediaStore/Room/virtual-album manifests.
9. **[PARTIAL]** JVM unit tests (`ResumableOperationsTest`) cover estimator, margin, staging names,
   false-positive pruning, active-operation exclusion, foreground re-entrancy/concurrency, pinned
   `resolvedDestName` & destination reuse, folded merge skip, prompt split + silent Phase-2 encrypt
   wipe, persisted `SkippedCryptoFilesDialog` reopen/auto-delete, and transient `IOException` retry
   via an in-memory DAO; physical force-stop/remount/ENOSPC scenarios remain manual
   (`docs/transfer-data-integrity.md` §8.4).

## 10. Open questions

- **Resolved:** Should the resume prompt appear on **any** app start, or only on vault unlock? — Plain-only `COPY`/`MOVE` prompts on app start; `ENCRYPT`, `DECRYPT`, and encrypted-album transfers prompt only upon vault unlock (`OperationRecovery.selectPromptBatch`).
- **Resolved:** Should a journalled operation expire after N days, or persist until explicitly resolved? — No day-based TTL needed: each interrupted batch shows a one-time `Yes` / `No` decision; `No` permanently clears its journal rows and never re-prompts.
- Should the CLI share this journal format for external-storage transfers? (Currently out of scope.)
- **Resolved:** What exact safety margin should §3a use? — `max(64 MiB, 1%)` is implemented, and SAF `-1` uses explicit user confirmation (`docs/transfer-data-integrity.md` §8.3.3).

## 11. Implementation status

This ADR is the **decision record**; the results document
[`docs/transfer-data-integrity.md`](../transfer-data-integrity.md) §8 is the **current runtime
report**. Read both when resuming work. Statuses below are intentionally explicit so that no
separate conversation is needed to reconstruct what has been built.

| Area (ADR section)                                                        | Status      | Evidence / notes                                                                                                                                                                                                                                                                                                                                                        |
| ------------------------------------------------------------------------- | ----------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Separate `pending_operations` Room DB (§3)                                | **DONE**    | `PendingOperationsDatabase`, `PendingOperation`, `PendingOperationsDao` (`findItem`, `upsertAll`), `OperationJournal` (batch transaction + `O(1)` updates)                                                                                                                                                                                                              |
| `stagingPath`, batch flags and per-item state machine (§3)                | **DONE**    | `operationId:itemIndex` keys; `isMove`, `wipeSource`, `deleteSource`; `markVerified` records actual committed `destPath`; `needsResume` distinguishes `COPY` (`VERIFIED` terminal) from pending source deletion                                                                                                                                                         |
| Space estimator and container-size formula (§3a)                          | **DONE**    | `SpaceEstimator`, `VaultContainerWriter.estimateEncryptedSize`, wired into copy/move and album operations                                                                                                                                                                                                                                                               |
| Explicit confirmation for unverifiable free space (§3a, §10)              | **DONE**    | `SpaceConfirmation` (async + blocking variants), localized across 11 `values*` resource sets                                                                                                                                                                                                                                                                            |
| Staging + atomic commit in all write paths (§3a, §5)                      | **DONE**    | `AtomicStaging` (File + SAF, `alreadyFileSynced`), `CryptoManagerFileCrypto`, `CryptoManagerConflict`, `CopyMoveHelper`, `CryptoManagerExportSync` (`ATOMIC_MOVE`)                                                                                                                                                                                                      |
| Journal hooks in copy/move, album, and Activity/Adapter batches (§9/3-4)  | **DONE**    | `CopyMoveHelper`, `CryptoManagerFolderCrypto`, `tryCopyMoveFilesTo`, `MediaAdapter`, single/multi-file encrypt/decrypt (`Activity.kt`)                                                                                                                                                                                                                                  |
| Read-back verification before source deletion (§3, §8)                    | **DONE**    | Full `verifyFileIntegrity` after encrypt and `.bin` copy; output-length check after decrypt; fingerprint check after plain copy                                                                                                                                                                                                                                         |
| Process-death resume dialog (§2, §9/7)                                    | **DONE**    | `OperationRecovery.maybePromptResume` (type-split plain vs vault unlock, silent Phase-2 encrypt wipe, persistent `AWAITING_SKIP_CONFIRM` / `DELETE_SKIPPED_APPROVED` skipped-crypto recovery, permanent clear + aborted notice on `No`), triggered from `MainActivity.onResume`, vault unlock (`promptToResumeVault`, `enterSecretVault`), and `MediaActivity.onResume` |
| Startup reconciliation and orphan cleanup (§8, §9/8)                      | **DONE**    | `OperationRecovery.reconcile` (prunes non-resumable batches, skips when `ForegroundOperationRegistry.isActive`), `MetadataReconciler` (per-item, prunes cleared operations)                                                                                                                                                                                             |
| Foreground service and single owner token (§7)                            | **DONE**    | `FileOperationService` (`ACTION_CANCEL`) + same-thread re-entrant `ForegroundOperationRegistry` wired into all copy/move, album, Activity/MediaAdapter crypto, and resume paths; `VaultSessionManager` keeps RAM keys while `isActive`                                                                                                                                  |
| Transient-wait loop for removed volume / low space (§2, §9/6)             | **DONE**    | `TransientOperationWaiter` (`awaitDestinationReady` + mid-stream `runWithTransientRetry`), localized `waiting_for_free_space_or_storage` status string, `WAITING_FOR_SPACE`, `ACTION_CANCEL`                                                                                                                                                                            |
| Declined-resume & post-transfer skipped-crypto wipe handling (§6, §9/7)   | **DONE**    | One-time Phase-1 `Yes`/`No` clears journal permanently on `No` with aborted notice and no duplicate Secure Erase dialog; `STATE_VERIFIED` Phase-2 encrypt wipes complete silently on unlock; `SkippedCryptoFilesDialog` `ASK` re-opens if unanswered and `DELETE`/approved runs silently                                                                                |
| Re-applying conflict decisions / `resolvedDestName` / merge tracking (§4) | **DONE**    | Pinned `resolvedDestName` & `destPath` recorded in `markInFlight`, existing committed plain output reused via size+fingerprint match, per-item `conflictResolution`, `mergeTargetRoot` + `foldedSourceRoots`                                                                                                                                                            |
| Automated fault-injection / device tests (§9/9)                           | **PARTIAL** | `ResumableOperationsTest` covers JVM primitives, concurrency/re-entrancy, pinned dest names, destination reuse, folded merge skip, prompt split + silent Phase-2 encrypt wipe, deferred skipped-crypto reopen/auto-delete, and transient retry; physical device kill/remount tests remain manual (`docs/transfer-data-integrity.md` §8.4)                               |

**Remaining work:** optional on-device hardware fault-injection smoke tests (`docs/transfer-data-integrity.md` §8.4). All code items under ADR 0003 are implemented and covered by JVM tests; the data-loss invariant (§8/2) is preserved by all paths.

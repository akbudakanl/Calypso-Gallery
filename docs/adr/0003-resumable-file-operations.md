# ADR 0003 — Resumable file operations

- **Status:** Proposed (awaiting approval)
- **Date:** 2026-11-17
- **Relates to:** ADR 0002 §7, DOCS.md §2.4/§2.5, `docs/transfer-data-integrity.md` §6

## 1. Context

ADR 0002 establishes a registered-foreground-operation model with two interruption layers:
a foreground service that waits out transient failures (external disk removed, write error) and resumes from a durable journal, plus a single "interrupted, continue?" dialog when the process actually died.

The same class of failure affects ordinary file operations: copy/move (plain ↔ plain, plain ↔ encrypted, encrypted ↔ encrypted), and encrypt/decrypt of files and albums. The upstream code already protects against **data loss** (a move deletes the source only after the destination is verified — `CopyMoveHelper.copyFile`, `transferSuccess`), but it leaves a partially written destination behind and offers no resume: the user must clean up and restart manually.

This ADR specifies a unified resume framework for those operations. It is **design only**; implementation is staged after approval.

## 2. Decision — two interruption layers

| Layer | Trigger | Behaviour | User prompt |
|---|---|---|---|
| **Transient wait** | Process alive; external volume unmounted, write error, temporary I/O failure | Service stays alive, keys stay in RAM, notification/progress shows "waiting", polls until the resource returns, then continues from the journal | None |
| **Process death** | App killed, reboot, user force-closed, service cancelled | Journal persists; on next app start + vault unlock, one combined dialog explains the partial operation and asks whether to continue | **One-time** "previous operation interrupted, no data lost, continue?" — **no "always show" setting** |

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
                   ↘ FAILED           ↘ SKIPPED_CONFLICT
                   ↘ WAITING_FOR_SPACE → IN_FLIGHT
                   ↘ SPACE_CHECK_FAILED
```

`SOURCE_DELETED` is only reachable from `VERIFIED` — this is the hard invariant that guarantees no data loss.

## 3a. Decision — space budgeting (low-storage operations)

The staging protocol above must not inherit the gaps of the current check. Today `getAvailableStorageB`
is used only in `CopyMoveHelper.prepareAndRunCopyMove` and only when `useStreamCopy == true`; it
compares `Σ source.size` with `>= availableSpace`, and skips the check entirely when the API returns
`-1`. **Encrypt, decrypt, album operations and plain → vault copies have no space check at all.**

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

## 6. Decision — skipped files and secure erase

- A source is "skipped" only when a conflict decision was **settled** (`SKIPPED_CONFLICT`) and the source is still intact.
- A source whose destination write or verification failed is **never** eligible for secure erase, even if it resembles a duplicate.
- If the user declines to resume, the app offers one Secure Erase prompt listing only the eligible skipped files from the completed part of the operation ("delete these now?"). Saying yes deletes exactly those; saying no leaves everything untouched. This implements the rule agreed with the maintainer.

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
4. Interrupted temp files are cleaned at startup only when no operation is running. Note: the current
   code only *creates* `.tmp_*.bin` (`renameEncryptedFile`) and has no startup cleanup, so this must be
   implemented rather than assumed. Plaintext-bearing staging files require secure wipe, not `delete()`.
5. The resume prompt is one-time and carries no "always ask" preference for ordinary operations.
6. No batch starts when `peakBytes` exceeds available space plus the margin (§3a), unless the user
   explicitly acknowledges that free space could not be verified.

## 9. Implementation checklist (staged, not yet started)

1. Room table `pending_operations` + schema registration (including `stagingPath` and batch estimates).
2. `SpaceEstimator` + `VaultContainerWriter.estimateEncryptedSize(...)`; wire it into every operation
   entry point (§3a) and extend the state machine with `WAITING_FOR_SPACE` / `SPACE_CHECK_FAILED`.
3. Journal hooks in `CopyMoveHelper` (copy/move, plain and encrypted, cross-album).
4. Journal hooks in encrypt/decrypt paths (file and album), including album conflict resolution.
5. Staging + same-directory atomic commit (`ATOMIC_MOVE`, no non-atomic fallback) in all four write paths.
6. Transient-wait loop + notification/progress state (including "waiting for free space" and `-1` handling).
7. Process-death resume dialog + Secure Erase list prompt.
8. Startup reconciliation (orphan staging cleanup **before** the space check; verify journalled `VERIFIED` items against disk; discard stale `IN_FLIGHT`; reconcile MediaStore/Room/virtual-album manifests).
9. Tests: kill mid-copy, kill mid-encrypt, external volume removed and remounted, conflict dialog pending at kill, skipped-file erase eligibility, exact storage boundary, album peak space with N files, max-size thumbnail header, SAF `-1` path, orphan accumulation, ENOSPC mid-write.

## 10. Open questions

- Should the resume prompt appear on **any** app start, or only on vault unlock? (Proposed: only on vault unlock for encrypted operations; on app start for plain-only operations.)
- Should a journalled operation expire after N days, or persist until explicitly resolved?
- Should the CLI share this journal format for external-storage transfers? (Currently out of scope.)
- What exact safety margin should §3a use (`max(64 MiB, 1%)` proposed), and should the SAF `-1` case
  require explicit user confirmation or only a non-blocking warning?

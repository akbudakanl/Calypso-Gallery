# ADR 0003 — Resumable file operations beyond vault migration

- **Status:** Proposed (awaiting approval)
- **Date:** 2026-11-17
- **Relates to:** ADR 0001 §7, ADR 0002 §7, DOCS.md §2.4/§2.5

## 1. Context

ADR 0001/0002 deliver a resumable **vault migration** with two interruption layers:
a foreground service that waits out transient failures (external disk removed, write error) and resumes from an on-vault manifest, plus a single "interrupted, continue?" dialog when the process actually died.

The same class of failure affects ordinary file operations: copy/move (plain ↔ plain, plain ↔ encrypted, encrypted ↔ encrypted), and encrypt/decrypt of files and albums. The upstream code already protects against **data loss** (a move deletes the source only after the destination is verified — `CopyMoveHelper.copyFile`, `transferSuccess`), but it leaves a partially written destination behind and offers no resume: the user must clean up and restart manually.

This ADR specifies a unified resume framework for those operations. It is **design only**; implementation is staged after approval.

## 2. Decision — two interruption layers

| Layer | Trigger | Behaviour | User prompt |
|---|---|---|---|
| **Transient wait** | Process alive; external volume unmounted, write error, temporary I/O failure | Service stays alive, keys stay in RAM, notification/progress shows "waiting", polls until the resource returns, then continues from the journal | None |
| **Process death** | App killed, reboot, user force-closed, service cancelled | Journal persists; on next app start + vault unlock, one combined dialog explains the partial operation and asks whether to continue | **One-time** "previous operation interrupted, no data lost, continue?" — **no "always show" setting** (unlike vault migration) |

Vault migration keeps its own stricter rule (§7 of ADR 0001): its "always show" setting is forced back ON after every interruption. Ordinary file operations deliberately do **not** inherit that rule — the resume dialog is shown once per interrupted operation, with no persistent preference, because there is no "you might not notice your vault is half-upgraded" risk.

## 3. Decision — journal location and schema

- **Ordinary operations:** app-private Room table `pending_operations` (JSON payload). Rationale: device-local, survives app restart, does not pollute the vault, is not visible to the CLI, and is wiped with app data (acceptable — source files remain intact).
- **Vault migration:** keeps its on-vault `.vault_migration` manifest (already implemented). Rationale: it must follow the vault across devices and survive an app-data wipe; it is authenticated with VMK.

Per-item journal record:

```
operationId, kind (COPY|MOVE|ENCRYPT|DECRYPT), createdAt,
sourcePath, destPath, sourceSize, sourceFingerprint,
batchConflictResolution (SKIP|KEEP_BOTH|OVERWRITE),
resolvedDestName,          // final name actually chosen, incl. (1)/(2) suffixes
albumConflictResolution,   // MERGE | CREATE_NEW(n) for album operations
state, updatedAt
```

Per-item state machine:

```
PENDING → IN_FLIGHT → COPIED_UNVERIFIED → VERIFIED → SOURCE_DELETED
                   ↘ FAILED           ↘ SKIPPED_CONFLICT
```

`SOURCE_DELETED` is only reachable from `VERIFIED` — this is the hard invariant that guarantees no data loss.

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
4. Interrupted temp files are cleaned at startup only when no operation is running (already implemented for `.tmp_*.bin`).
5. The resume prompt is one-time and carries no "always ask" preference for ordinary operations.

## 9. Implementation checklist (staged, not yet started)

1. Room table `pending_operations` + migration.
2. Journal hooks in `CopyMoveHelper` (copy/move, plain and encrypted, cross-album).
3. Journal hooks in encrypt/decrypt paths (file and album), including album conflict resolution.
4. Transient-wait loop + notification/progress state.
5. Process-death resume dialog + Secure Eraselist prompt.
6. Startup reconciliation (verify journalled `VERIFIED` items against disk; discard stale `IN_FLIGHT`).
7. Tests: kill mid-copy, kill mid-encrypt, external volume removed and remounted, conflict dialog pending at kill, skipped-file erase eligibility.

## 10. Open questions

- Should the resume prompt appear on **any** app start, or only on vault unlock? (Proposed: only on vault unlock for encrypted operations; on app start for plain-only operations.)
- Should a journalled operation expire after N days, or persist until explicitly resolved?
- Should the CLI share this journal format for external-storage transfers? (Currently out of scope.)

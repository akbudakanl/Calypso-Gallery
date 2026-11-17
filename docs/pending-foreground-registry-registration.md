# Pending work: register album/Activity operations with the foreground registry

- **Status:** Not done — deliberately deferred to a later session.
- **Related ADRs:** [ADR 0002 §7](adr/0002-operational-security-hardening.md) (foreground
  operation exception), [ADR 0003 §7](adr/0003-resumable-file-operations.md) (single owner token).

## What is missing?

`ForegroundOperationRegistry` is currently wired into only two places:

- `CopyMoveHelper.executeCopyMoveWithProgress` (plain copy/move),
- `OperationRecovery.resume` (continuation after process death).

The following paths are **not registered**:

- `CryptoManager.encryptFolder`,
- `CryptoManager.decryptFolder`,
- `BaseSimpleActivity.tryCopyMoveFilesTo` (mixed batch into or out of the encrypted vault).

These paths already use staging + atomic commit + journal + verification, so there is **no data-loss
risk**. What is missing is only the foreground service / wake lock / owner-token coverage.

## Practical impact

Without registration, if the app is backgrounded or the screen turns off during one of these
operations, the ADR 0002 §7 exception ("keys remain in RAM") does not apply. The vault may lock,
the operation may be interrupted, and the user must unlock the vault again before resuming.
Interrupted items are reset to `PENDING` in the journal and rewritten from their intact source —
**no corrupted file and no data loss**, only the need for user intervention.

## Why it was deferred

All three functions contain many early `return` statements, nested callbacks and async layers.
There are two ways to register them:

1. wrap the whole body in `ForegroundOperationRegistry.withOperation(context, owner) { ... }` —
   this requires a broad control-flow change because of the return types and callbacks;
2. add `release()` to every exit point — a missed exit could leave the service running.

Both need a careful regression pass, so the change was judged "invasive".

## To do (recommended order)

1. Pick a single `owner` name for each of the three functions (e.g. `album-encrypt`,
   `album-decrypt`, `crypto-copy-move`).
2. Wrap the main working block in `withOperation`; handle early `return` points through the
   `null` result of `withOperation`.
3. Verify that if two operations are started at once, the second one returns `null` and informs
   the user.
4. Tests: force-stop during album encryption, backgrounding, a second concurrent operation,
   and dismissal of the service notification.

> This note exists to record that the gap is known and is not critical for data safety.
> Once the work is complete, update or remove this file.

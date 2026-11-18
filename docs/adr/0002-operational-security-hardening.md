# ADR 0002 — Operational security hardening: randomness, KDF profiles, trace hygiene, key zeroization and durable wipes

- **Status:** Accepted
- **Date:** 2026-11-16
- **Relates to:** DOCS.md §2, §2.4, §5; [ADR 0001](0001-tink-streaming-aead-migration.md) (canonical vault container format); ADR 0003 (resumable file operations)

> **Terminology.** User-facing text uses **"Encrypted Vault" / "Şifreli Kasa"**, as decided in ADR 0001.

---

## 1. Context

ADR 0001 defines the canonical cryptographic format. This ADR covers the *operational* guarantees around it: where randomness comes from, how KDF cost is exposed, what traces the app leaves, how long keys live in RAM, and what happens when a destructive or long-running operation is interrupted. These guarantees are independent of the container format and apply to every vault.

---

## 2. Decision — randomness source

**Use only the operating system CSPRNG. Do not add user-movement entropy collection.**

- Android: `java.security.SecureRandom` default provider (AOSP/Conscrypt/BoringSSL) backed by the Linux kernel `getrandom(2)` / `/dev/urandom`. The kernel-side implementation is **open source** (AOSP + Linux + BoringSSL). A SoC vendor's hardware RNG block may be a closed blob, but the kernel conditions and mixes multiple entropy sources, so a single bad source does not compromise the pool.
- "Truly random" in the physical sense is not the requirement; **computational indistinguishability, forward secrecy and reseeding** are, and the OS provides them.
- CLI: Python `secrets` → `os.urandom` → `getrandom()` on Linux/Tails (open source) and `BCryptGenRandom` on Windows (closed but standard, well-audited). Acceptable.

**Rejected: VeraCrypt-style mouse/touch/motion entropy gathering.**
- Mobile has no entropy starvation; this is a legacy of PC boot-time concerns.
- It creates a **new privacy leak** (motion/touch data is sensitive and needs permissions), battery drain and UX friction.
- User input carries little real min-entropy — mostly security theatre.

**Rules:** never call `SecureRandom.setSeed()` with predictable or attacker-influenced data; never use `Math.random()` for any security value; each file's `fileId`, DEK, header IV and Tink salt/nonce-prefix come from `SecureRandom` only. The device-bound Keystore key remains an additional derivation ingredient through the existing VMK wrapping, not through custom entropy mixing.

**No homegrown cryptography.** Every cipher, MAC, KDF and random source comes from Tink, JCE or BouncyCastle: AES-GCM / HMAC / PBKDF2 / SHA-256 via JCE, Argon2id via BouncyCastle, streaming AEAD and RFC 5869 HKDF via Tink. The only hand-written pieces are **format plumbing** — byte layouts, domain-label strings and header-AAD concatenation — none of which is a cryptographic primitive. All key separation uses standard HKDF-SHA256 with `CalypsoGallery:` domain labels (ADR 0001).

---

## 3. Decision — Argon2id profiles and unlock delay

### 3.1 KDF profiles (advanced setting)

Keep the current default **64 MiB / t=3 / p=2**. Expose **named profiles**, not raw sliders:

| Profile | Memory | Iterations | Parallelism | UI label |
|---|---|---|---|---|
| Low-end device | 32 MiB | 2 | 1 | "Low-end device **(not recommended)**" |
| Default | 64 MiB | 3 | 2 | "Default" |
| High security | 128 MiB | 4 | 2 | "High security" |

- Absolute minimums enforced in code: `memoryKb >= 19 * 1024`, `iterations >= 2`, `parallelism >= 1` (OWASP baseline). Values below this are rejected.
- The profile selector shows a fixed note: **"If you don't know what you're doing, it is recommended to keep the default setting."** 
- Show the estimated unlock time for the chosen profile on the device where possible.
- Parameters are stored in `.vault` (already the case). Changing a profile re-wraps the VMK under a newly derived PDK; it does not touch the keyring, headers or biometric blob.

Rationale: raw iteration/memory boxes let users pick insecure values and create false confidence. Named profiles with enforced minimums and an explicit "(not recommended)" label communicate risk without requiring cryptographic knowledge.

### 3.2 Artificial unlock delay (KeePass-style)

Optional setting: **1 s default, adjustable up to 10 s**, applied on every unlock attempt.

- Applied **equally on success and failure** — otherwise the timing difference becomes an oracle.
- Implemented as a cancellable coroutine `delay`, never `Thread.sleep()` on the main thread.
- **Document honestly in the UI:** this slows only password guessing *through this app on this device*. An attacker who copies `.vault` and runs an offline tool ignores it. It is **not** a substitute for Argon2id cost.
- Planned enhancement: additional escalating backoff after N consecutive failures.

---

## 4. Decision — trace hygiene audit

Each item below is a required audit task with an explicit mitigation and verification step. "Done" is not assumed.

| Trace                       | Risk                                                                                                                        | Mitigation                                                                                                                                                                                                               | Verification                                                                        |
| --------------------------- | --------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------- |
| **Thumbnail / image cache** | Highest risk: `EncryptedGlideLoader` passes decrypted bytes to Glide, whose default disk cache can persist decoded bitmaps. | For encrypted paths: `diskCacheStrategy(DiskCacheStrategy.NONE)`, `skipMemoryCache(true)`; audit Picasso and `MediaStore.Thumbnails` inserts; `clearAppImageCaches()` on lock must cover Glide memory **and** disk.      | Instrument cache dirs after viewing encrypted media: zero decrypted images on disk. |
| **Recents / task snapshot** | A window without `FLAG_SECURE` on top can be captured in the task switcher.                                                 | `FLAG_SECURE` on **every** activity and dialog that can display vault content (MainActivity, MediaActivity, ViewPagerActivity, VideoPlayerActivity + dialogs). Verify per-window flags, not just per-activity in theory. | Take a recents snapshot on a test device; no vault content visible.                 |
| **MediaStore copies**       | Decrypted plaintext written to shared storage gets indexed and survives manual deletion.                                    | All plaintext temp outputs live under `.nomedia` dirs; `purgeMediaTracesPreservingReferences` / `purgeTracesForFile` on every decrypt/export path; queries exclude deleted items.                                        | Query MediaStore after a decrypt+delete cycle: no matching rows.                    |
| **Temporary files**         | `.tmp_<random>.bin`, `.sync_tmp_*.bin`, export outputs deleted with plain `delete()` on error/cancel can leave data.        | Any path that held plaintext uses **secure wipe** on failure/cancel; success path uses atomic rename then wipe of the old file.                                                                                          | Fault-injection test: cancel mid-write, verify no recoverable plaintext.            |
| **Backups**                 | Room DB holds vault paths and corruption records; Auto Backup would copy app-private data off-device.                       | `android:allowBackup="false"`; `dataExtractionRules` (Android 12+) excluding `domain="root"`; review `fullBackupContent`.                                                                                                | `adb backup` / Auto Backup inspection yields no app data.                           |
| **Clipboard**               | Copied password or decrypted content persists for other apps.                                                               | Clear clipboard after password use and after "copy decrypted content"; never put vault passwords on the clipboard implicitly.                                                                                            | Clipboard inspection after a copy flow.                                             |
| **Keyboard / autofill**     | IME learns typed passwords; autofill stores them.                                                                           | Password fields: `inputType=textPassword`, `imeOptions=flagNoSuggestions`, `importantForAutofill=no`, no `autofillHints`.                                                                                                | Inspect IME suggestions and Autofill service after typing.                          |
| **Logs / diagnostics**      | Plaintext paths or keys in logcat or crash reports.                                                                         | No key material or plaintext content in logs; vault paths only where already intentional.                                                                                                                                | Logcat grep during a full encrypt/decrypt cycle.                                    |

Accessibility services and OS-level crash dumps cannot be fully prevented by an app; document as residual risk.

---

## 5. Decision — key zeroization

### 5.1 Two key lifetimes

- **Session key** — `unlockedMasterKey`, valid while the vault is unlocked.
- **Ephemeral key** — unlocked only to perform a one-off operation when the user enters a password for something other than "open the vault": CLI transfer authorization, enabling biometric unlock, encrypt/decrypt confirmation, KDF profile change, etc.

**Ephemeral keys must be wiped the moment their operation ends**, never promoted to a session key. Implement `withEphemeralVaultKey { key -> ... }`:

```
unwrap/derive key → try { operation(key) } finally { key.fill(0); clearAllFileKeyCopies() }
```

### 5.2 Centralized wipe

`CryptoManager.clearKeys(context)` wipes:

- `unlockedMasterKey`,
- every `EncryptedFileMetadata.fileKey` in `metadataCache`, then clears the cache,
- `unlockedKek` and decoded folder/name caches,
- biometric bundle byte arrays after wrapping/unwrapping,
- any clipboard content flagged as vault-derived.

### 5.3 Strings are not zeroable

Passwords are handled as `CharArray` across the UI authentication boundary (`VaultAuthInputHelper`, `VaultSecurityDialog`, `VaultLockActivity`) into `CryptoManager.unlockWithSecret(context, CharArray)`, `setupOrUpdateCredentials`, `deriveKeyArgon2id`, and `withEphemeralVaultKey`. Java/Kotlin `String` is immutable and interned — a `String`-based password cannot be reliably erased. PIN buffers use fixed `CharArray(16)` storage and password inputs are copied directly via `Editable.getChars(...)` and filled with `'\u0000'` immediately after use.

### 5.4 Triggers

- Vault closed / user "stop showing hidden items" → `clearKeys()`.
- App backgrounded or screen turned off → `clearKeys()` **immediately** (wiping RAM keys and Glide image caches right away), **except** while a registered foreground operation is active (§7). At the same time, if the user was inside a vault viewing session, the UI activity stack remains resumable behind the isolated `VaultLockActivity` re-authentication gate for the 30-minute `VAULT_AUTO_LOCK_TIMEOUT_MS` window so returning from background or screen-off resumes directly on the exact photo/video after re-entering credentials; once that 30-minute window expires, the UI session expires as well (`lockNow()`).
- Backgrounded registered operation finishes → `VaultSessionManager.onForegroundOperationEnded()` triggers `clearKeys()` immediately while keeping re-auth armed for the UI window.
- Any ephemeral operation → wipe in `finally` regardless of success/failure.
- JVM caveat: `fill(0)` / `fill('\u0000')` is best-effort (stack/register copies, GC); document in DOCS.md.

---

## 6. Decision — durable secure wipe

Interrupting a CSPRNG overwrite can leave file data unoverwritten, and unflushed page cache can lose the part that was written or leave directory entries pointing to stale contents.

Mandatory mechanism for every secure delete:

1. **Foreground service + notification** and `PowerManager.PARTIAL_WAKE_LOCK` (plus `FLAG_KEEP_SCREEN_ON` hint while active, via `ForegroundOperationRegistry` §7) so the process survives backgrounding and the CPU does not sleep.
2. **Write-ahead pending-wipe journal (`pending_wipe_journal.txt`):** before overwriting begins, `secureWipeAndDeleteFile(file, context)` appends the canonical target path idempotently to `pending_wipe_journal.txt` and flushes/syncs the journal (`fd.sync()`). Only the path is stored — **never file contents**.
3. **Chunked CSPRNG overwrite + final flush/truncate:** overwrites `0..length` in `1 MiB` `SecureRandom` chunks (zeroing the buffer in `finally`), flushes with `FileChannel.force(true)`, truncates to `0L`, flushes again with `FileChannel.force(true)`, deletes the file, and calls `fsync` on the parent directory (`syncDir`). We intentionally avoid per-chunk `fsync` and byte-offset `.wipe_progress` seeks so normal deletes do not trigger flash write-amplification stalls while foreground-service execution prevents routine background kills.
4. **Resume on startup / boot:** `App.onCreate()` and `BootCompletedReceiver` run `processPendingWipeJournal(context)` (registered in `ForegroundOperationRegistry`) to finish any pending targets cleanly from offset `0`. Cancellation preserves unprocessed entries for the next startup.
5. **Never report success early:** `removePendingWipe` removes the entry — and securely wipes `pending_wipe_journal.txt` itself once empty — **only after** `file.delete()` and parent `syncDir()` complete; if interrupted or failed, the file remains flagged for retry.
6. **Hardware caveat:** flash FTL wear-leveling means a completed overwrite is still best-effort; full physical erasure requires hardware secure erase or full-disk encryption. Keep the existing DOCS.md §2 wording.

---

## 7. Decision — foreground-service exception for **all** file operations

The background-lock rule ("on background / screen off → lock vault and wipe keys") has one exception: a **registered foreground operation**. This is no longer limited to the vault rebuild. It covers **every** long-running or destructive file operation:

- encrypt (file / album / stream),
- decrypt / export,
- copy or move **between encrypted albums**,
- **delete — both normal delete and secure wipe** (§6),
- external-vault sync.

Behaviour:

- While such an operation is registered, keys stay in RAM across backgrounding / screen-off; the app shows a persistent notification with progress where meaningful.
- Returning to the app while the operation is still running continues the operation **without a password prompt** (unless `VaultLockActivity` was already armed for a viewing session).
- On completion (or cancellation) while the app is in the background, `clearKeys()` runs immediately (`VaultSessionManager.onForegroundOperationEnded`) and `VaultLockActivity` requires re-authentication before resuming any vault viewing session.
- `FLAG_KEEP_SCREEN_ON` may be set during the operation (hint, not guarantee).
- Two concurrent foreground operations are serialized through a single owner token to avoid two independent wipe/clear decisions racing.
- If the process is killed, the durable operation journals (ADR 0003) and the secure-wipe journal (§6) allow resumption after re-authentication.

This is the single foreground-operation registry shared with the resumable-operation framework in ADR 0003.

---

## 8. Implementation checklist and tests

1. RNG: audit all `SecureRandom` / `Math.random()` usages; add a unit test that no test vector uses a fixed RNG seed in production paths.
2. KDF profiles: Settings UI with three profiles, enforced minimums, "(not recommended)" label and the advisory note; test that out-of-range values are rejected and that changing a profile preserves keyring/headers/biometric blob.
3. Unlock delay: setting (1–10 s), equal success/failure delay, cancellable; test timing and cancellation.
4. **[DONE — code & JVM regression verified]** Trace audit (§4): encrypted Glide loads disable disk + memory caching (`applyEncryptedCachePolicy`, `MyGlideImageDecoder`, `Context`, `PhotoFragment`, `VideoFragment`, `PortraitPhotosAdapter`, `MyWidgetProvider`, `MediaActivity`, `EditActivity`, `Activity`), `clearAppImageCaches()` wipes cache dirs on lock, `allowBackup="false"` / `fullBackupContent="false"` / `data_extraction_rules.xml` exclude all backup domains, `dialog_vault_security.xml` sets `inputType="textPassword|textNoSuggestions"`, `imeOptions="actionDone|flagNoPersonalizedLearning"`, and `importantForAutofill="no"`, `ViewPagerActivity` blocks vault clipboard copies and `clearKeys(context)` wipes flagged clipboard, `saveRotatedImageToFile` securely wipes plaintext temp files in vault context, and `MediaReadPatternProbe` logs numeric read counters only (no paths/keys). Regression tests are in `VaultLockPolicyTest`; device-level Recents/IME smoke tests remain manual (§9).
5. **[DONE — JVM verified]** Zeroization (§5): `withEphemeralVaultKey`, `CryptoManager.clearKeys()`, `CharArray` password boundary across UI and `CryptoManagerCredentials`, biometric buffer zeroization, plus background/screen-off immediate RAM key wipe with 30-minute resumable UI window behind `VaultLockActivity` (deferred only while a registered foreground operation is active). Verified in `VaultLockPolicyTest`.
6. **[DONE — JVM verified]** Durable wipe (§6): foreground service + wake lock (§7), idempotent write-ahead `pending_wipe_journal.txt` (`appendPendingWipe` with `fd.sync()` before overwrite, `removePendingWipe` + journal wipe only after `delete()` + parent `syncDir()`), `1 MiB` `SecureRandom` overwrite + `FileChannel.force(true)` + `truncate(0)` + `FileChannel.force(true)` + parent `fsync`, and `processPendingWipeJournal` retry on startup/boot. Verified in `VaultLockPolicyTest`; on-device power-loss / hardware wear-leveling smoke tests remain manual (§9).
7. **[DONE — JVM verified]** Foreground operation registry (§7): single owner token, re-entrancy, notification + wake lock, `FLAG_KEEP_SCREEN_ON` lifecycle hook, cancellation propagation, and background completion `clearKeys()` + re-auth work across copy/move, album/file/stream encrypt/decrypt, resume, skipped-crypto wipe, pending-wipe journal, standalone secure wipe, vault recycle bin move/restore/delete/empty, plain recycle move/restore/empty, batch delete, vault export, and external-vault sync (`VaultLockPolicyTest` & `ResumableOperationsTest`).

---

## 9. Deferred / residual risk

- Accessibility services and OS crash dumps cannot be controlled by the app.
- Exact timing of OS task snapshots is device-dependent; re-test on each supported API level.
- The unlock delay's limited value against offline attacks must be communicated in the UI, not hidden.

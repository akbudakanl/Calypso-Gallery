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
- The profile selector shows a fixed note: **"If you don't know what you're doing, it is recommended to keep the default setting."** / TR: "Ne yaptığınızı bilmiyorsanız varsayılan ayarda kalmanız önerilir."
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

`CryptoManager.clearKeys()` wipes:

- `unlockedMasterKey`,
- every `EncryptedFileMetadata.fileKey` in `metadataCache`, then clears the cache,
- all transient KEK/DEK/VMK/PDK byte arrays held in manager state,
- biometric bundle byte arrays after unwrapping,
- any clipboard content holding vault data.

### 5.3 Strings are not zeroable

Passwords must be handled as `CharArray` from the UI boundary; `unlockWithSecret(secret: String)` is changed to accept `CharArray`. Java/Kotlin `String` is immutable and interned — a `String`-based password cannot be reliably erased. EditText content is converted to `CharArray`, used, then filled with `'\u0000'`.

### 5.4 Triggers

- Vault closed / user "stop showing hidden items" → `clearKeys()`.
- App backgrounded or screen turned off → `clearKeys()` **immediately** (replacing the current 30-minute timeout policy), **except** while a registered foreground operation is active (§7).
- Any ephemeral operation → wipe in `finally` regardless of success/failure.
- JVM caveat: `fill(0)` is best-effort (stack/register copies, GC); document in DOCS.md.

---

## 6. Decision — durable secure wipe

Interrupting a chunked CSPRNG overwrite can leave the tail of a file unoverwritten, and unflushed page cache can lose the part that was written.

Mandatory mechanism for every secure delete:

1. **Foreground service + notification** and `PowerManager.PARTIAL_WAKE_LOCK` so the process survives backgrounding and the CPU does not sleep.
2. **Chunk-level durability:** after each chunk (or small group), `FileChannel.force(true)` / `fd.sync()`.
3. **Progress journal** (e.g. `.wipe_progress`): stores only the target path and bytes-overwritten-so-far — **never file contents**. Updated after each durable chunk.
4. **Resume on startup:** if a journal exists, continue from the recorded offset; only after final `truncate(0)`, `fsync`, `delete()`, and parent-directory `fsync` is the journal removed.
5. **Never report success early:** if interrupted, the file stays flagged for retry.
6. **Hardware caveat:** flash FTL wear-leveling means a completed overwrite is still best-effort; full physical erasure requires hardware secure erase or full-disk encryption. Keep the existing DOCS.md §2 wording.

Optional for highly sensitive data: verify by reading back all-zero bytes before the final delete (doubles I/O).

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
- Returning to the app continues the operation **without a password prompt**.
- On completion (or cancellation), the operation is unregistered, `CryptoManager.clearKeys()` runs, and the user must enter the password again to use the vault.
- `FLAG_KEEP_SCREEN_ON` may be set during the operation (hint, not guarantee).
- Two concurrent foreground operations are serialized through a single owner token to avoid two independent wipe/clear decisions racing.
- If the process is killed, the durable operation journals (ADR 0003) and the secure-wipe journal (§6) allow resumption after re-authentication.

This is the single foreground-operation registry shared with the resumable-operation framework in ADR 0003.

---

## 8. Implementation checklist and tests

1. RNG: audit all `SecureRandom` / `Math.random()` usages; add a unit test that no test vector uses a fixed RNG seed in production paths.
2. KDF profiles: Settings UI with three profiles, enforced minimums, "(not recommended)" label and the advisory note; test that out-of-range values are rejected and that changing a profile preserves keyring/headers/biometric blob.
3. Unlock delay: setting (1–10 s), equal success/failure delay, cancellable; test timing and cancellation.
4. Trace audit: perform every row in §4; add regression tests for cache-disable and backup exclusion.
5. Zeroization: `withEphemeralVaultKey`, `CryptoManager.clearKeys()`, CharArray password boundary; tests assert arrays are zero after each path and that backgrounding triggers a wipe except during a registered operation.
6. Durable wipe: foreground service, wake lock, journal, resume; fault-injection tests (process kill, screen off, USB removal) verify resume and no plaintext leftovers.
7. Foreground operation registry: single owner, registration/unregistration, notification, and the "re-auth after completion" rule; tests cover all operation types listed in §7.

---

## 9. Deferred / residual risk

- Accessibility services and OS crash dumps cannot be controlled by the app.
- Exact timing of OS task snapshots is device-dependent; re-test on each supported API level.
- The unlock delay's limited value against offline attacks must be communicated in the UI, not hidden.

# ADR 0004 — Argon2 memory allocation, cross-device KDF compatibility and the unlock deadlock

- **Status:** Proposed — **no implementation decision has been taken yet**
- **Date:** 2026-11-17
- **Relates to:** [ADR 0002](0002-operational-security-hardening.md) §3 (Argon2id profiles and unlock delay); `docs/argon2-calibration-methodology.md`; FORK_CHANGELOG.md ("Brute-Force direnci"); `CryptoManagerCredentials.resolveArgon2Params`
- **Decision owner:** open (see §11)

> **Terminology.** User-facing text uses **"Encrypted Vault" / "Şifreli Kasa"** and keeps the English phrase **"Brute-Force"** in Turkish strings, as decided earlier.

---

## 1. Context

The "Brute-Force direnci" setting lets a user pick a target unlock time (2.0–7.0 s). The app calibrates Argon2id parameters on the device and stores the resulting `memoryKb / iterations / parallelism` in the `.vault` header, so the KDF cost travels with the vault like KeePass stores KDF rounds in `.kdbx`.

Two concerns were raised:

1. **A user must never crash the app merely by picking a target time** in the settings dialog.
2. **A vault created on a strong device must be openable on a weaker device.** If the stored parameters cannot run there, the vault cannot be unlocked — and its parameters cannot be lowered without unlocking it first.

This ADR records the analysis, the rejected options, the remaining theoretical gap and the open decision. It does **not** change any code: the current dynamic memory scheme stays in place until a decision is made.

---

## 2. What we use today

- **Variant: Argon2id, version 1.3.** `deriveKeyArgon2id` uses `Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)` with `ARGON2_VERSION_13` (BouncyCastle). The header stores `KDF: 1 = Argon2id` and the marker is `argon2id_aes256_gcm_v2`.
- **Rationale for Argon2id:** Argon2d is stronger against GPU/ASIC but side-channel sensitive; Argon2i is the opposite; Argon2id is the hybrid and the RFC 9106 / OWASP recommended default.
- **Implementation constraint:** BouncyCastle's `Argon2BytesGenerator` allocates the whole working memory as a **single Java `int[]`** — inside the **Java heap**.

Current safety bounds (already implemented, and to be kept for now):

- iterations clamped to **2…16**,
- memory capped at `Runtime.maxMemory()/2` with a **64 MiB floor**, 128 MiB only for targets > 5 s,
- `OutOfMemoryError` during calibration falls back from 128 MiB to 64 MiB,
- a "(clamped) / (kırpıldı)" marker shows the **actually stored** parameters.

---

## 3. Root cause of the observed crash

Measured on a Redmi Note 10s (8 GB RAM, Java heap growth limit **256 MB**):

| Label | Memory / iterations / parallelism | Measured |
|---|---|---|
| LOW | 32 MiB / 2 / 1 | 904 ms |
| DEFAULT | 64 MiB / 3 / 2 | 2 410 ms |
| HIGH | 128 MiB / 4 / 2 | 6 927 ms |
| (attempted 10 s) | **256 MiB / 6 / 2** | `OutOfMemoryError` → process killed |

The failure was **`Failed to allocate ... target footprint 268435456, growth limit 268435456`** — i.e. exactly the 256 MB Java heap ceiling.

**Conclusion:** the app did not crash because "7 seconds is impossible on Android". It crashed because the Argon2 buffer was a Java heap allocation on a device whose heap limit is 256 MB. Physical RAM (8 GB) is irrelevant to that limit. The HIGH profile (128 MiB / 4) worked fine.

---

## 4. What KeePass actually does (verified, with corrections)

Sources: `keepass.info/help/base/security.html` (official KeePass Help Center) and the KeePassXC source comment in John the Ripper (`argon2d` defaults).

1. **KeePass's main desktop recommendation is the opposite of "fix memory, vary iterations".** Its procedure is: iterations = 2; memory = `min(M/2, 1 GB)` where `M` is the smallest RAM across all devices that will open the database; parallelism = minimum logical processors; then *Test*. If too slow → **decrease memory**; if too fast → **increase iterations**.
2. The **"1 Second Delay"** button uses a different strategy: "relatively low values for the memory and parallelism parameters, relatively high number of iterations", because KeePass does not know the other devices' hardware and wants compatible defaults.
3. **Mobile-specific guidance (iOS note)** matches our user's instinct: use "a relatively low value for the Argon2 memory parameter (**64 MB or less**) and a relatively high number of iterations".
4. KeePassXC 2.7.6 default (per the cited source comment): Argon2d, ~10–35 rounds aiming at 1 s, **64 MiB**, 2 threads.
5. KeePass supports both AES-KDF (iterated AES, no large allocation) and Argon2 (KDBX 4). A "10 s" KeePass database on desktop is usually AES-KDF rounds — no big memory buffer — which is why KeepassDX can open it on a phone without a Java heap OOM.

**Corrections to earlier statements in this conversation:**

- ❌ "KeePass keeps memory moderate and varies iterations." → Only true for the *1-second-delay button* and the *mobile/iOS* guidance; the main desktop procedure deliberately maximizes memory first.
- ❌ "Fixed 64 MiB loses nothing." → It preserves the *wall-clock target*, but it does **not** preserve the *hardware-attack resistance margin* (see §5).

---

## 5. Memory and iterations are not interchangeable

Official KeePass wording: *"The number of iterations scales linearly with the required time. By increasing the memory parameter, GPU/ASIC attacks become harder."*

- **Iterations (time cost):** add linear latency to every guess on every device — CPU, GPU and ASIC alike.
- **Memory (memory cost):** is Argon2's defining property. A hardware attacker needs a large SRAM block *per parallel guess*, so the area×time cost grows much faster than with iterations alone. **This cannot be substituted by iterations.**

Comparison at equal wall-clock time on the measured device:

| Configuration | Time | GPU/ASIC resistance |
|---|---|---|
| 128 MiB × 4 iterations | ~6.9 s | stronger (2× memory per guess) |
| 64 MiB × 9 iterations | ~7.2 s | slightly weaker |

So a fixed-64 MiB scheme is **safe and standard** (RFC 9106 first recommended option, OWASP baseline) but carries a **small, real security-cost trade-off**, not a free win. The user decided **not** to adopt fixed 64 MiB for now.

---

## 6. The cross-device deadlock (the paradox)

This is the central unresolved problem, and it is **fundamental, not a bug**:

- Argon2id's output depends on `m`, `t`, `p`, salt and password. Reducing `m` produces a **completely different key**; the wrapped VMK cannot be decrypted.
- Therefore there is no "derive with low memory, unlock, then rewrite the header" shortcut.
- To change the KDF parameters you need the VMK; to get the VMK you need to run the stored KDF at full memory.

**Formal consequence:** if a vault's stored parameters exceed what the opening device can allocate, the only escapes are (a) make the parameters affordable on that device, or (b) provide an alternative key path that bypasses the KDF.

---

## 7. Rejected option — fixed 64 MiB

Rejected by the user: they want a **dynamic** system that uses more memory when the device allows it. Recorded here as a valid but currently unwanted option: it would fully remove memory-related OOM and the cross-device deadlock, at the cost described in §5.

---

## 8. Rejected option — same-password low-memory fallback wrapper

Idea: at creation, also wrap the VMK with a second, cheap KDF (e.g. 64 MiB / 2) under the **same password**, and use it on weak devices.

**Rejected:** an offline attacker simply attacks the weaker wrapper. Effective security collapses to the weakest KDF (~1.6 s), making the user's chosen 7 s target cosmetic and the UI misleading. A fallback that shares the password cannot preserve security.

---

## 9. Candidate option — native Argon2 (JNI)

### 9.1 Mechanism

Move the Argon2 working buffer off the Java heap:

| | BouncyCastle today | JNI / native |
|---|---|---|
| buffer | `int[]` on **Java heap** | `malloc/calloc` on **native heap** |
| limit | `Runtime.maxMemory()` → 256 MB here | process cgroup limit → hundreds of MB to GBs |
| 128 MiB | risky / OOM | fine on any device with enough RAM |

A `calloc` failure is reported by the reference Argon2 library as `ARGON2_MEMORY_ALLOCATION_ERROR`, which the JNI wrapper can translate into a controlled, catchable error → **a dialog, not a Java crash**.

### 9.2 Cryptographic equivalence (the key point)

Native Argon2 with the **same algorithm, version 1.3 and the same `(m, t, p)`** produces the **same 32-byte key**. Therefore:

- the derived key is byte-identical to the current implementation,
- the `.vault` format is unchanged,
- this is an **allocation-location swap, not a crypto change**,
- **Argon2id is preserved** — no switch to Argon2d, which would forgo the mobile side-channel advantage and change the derived key.

### 9.3 API choice — critical

Use the reference Argon2 C library's `argon2id_hash_raw(t_cost, m_cost, parallelism, ...)` via JNI.

**Do not use libsodium's `crypto_pwhash_argon2id`**: it fixes the pass count at 3 and maps `opslimit` as a multiplier, so it cannot reproduce our exact `(m, t, p)`. The derived key would differ from the canonical one.

### 9.4 What remains theoretical after JNI

| Residual issue | Catchable? | Notes |
|---|---|---|
| Native allocation failure (cgroup / physical RAM) | ✅ Yes — error code | controlled dialog, no crash |
| 32-bit address-space fragmentation (`armeabi-v7a`) | ✅ Yes — same path | a single large contiguous `calloc` may fail despite free RAM |
| Low Memory Killer (kernel `SIGKILL`) | ❌ No | process kill, not an exception; risk reduced by smaller parameters, not eliminated; also applies to large image/video decode |
| Vault transferred to a genuinely incapable device | ⚠️ Formally unchanged | JNI makes 128 MiB affordable on virtually all current phones, but not on ~512 MB / old 32-bit hardware |

**Conclusion:** JNI fully removes the *artificial Java-heap bottleneck* (the actual cause of the observed crash) and makes "user-selected target time must not crash the app" achievable. It does not deliver a literal "never crashes" guarantee, and it does not formally solve the cross-device transfer case.

### 9.5 Cost

NDK/CMake setup, native binaries for `arm64-v8a` / `armeabi-v7a` (+ x86 if needed), APK size increase, and a vetted, reproducible build of the Argon2 reference library.

---

## 10. Candidate option — recovery key (formal escape hatch)

Generate a high-entropy random **recovery key** at vault creation. Wrap the VMK a second time under this key and store that wrapper in `.vault`; **do not store the recovery key itself in the vault** — give it to the user as an exported file/QR code.

On an incapable device:

1. enter the recovery key → unwrap the VMK directly (no memory-hard KDF needed),
2. rewrite the `.vault` header with device-appropriate parameters,
3. unlock normally with the password afterwards.

This breaks the §6 deadlock **without weakening password security**: an attacker who has the vault file but not the recovery key still faces the full Argon2id cost. It complements JNI rather than replacing it.

---

## 11. Decision status and open questions

**No decision has been taken.** The current dynamic scheme stays. Outstanding choices:

1. Implement the JNI prototype + an **equivalence test** proving BC and native Argon2 produce the identical 32-byte key for the same `(password, salt, m, t, p, version)` — the proof that both implementations derive the same key.
2. Add the recovery-key mechanism as a separate ADR / feature, for the formal cross-device case.
3. Add cheap hardening regardless of 1 and 2: `android:largeHeap="true"`, capability probing by **actual allocation attempt** instead of the `maxMemory()/2` estimate, OOM handling on the vault-creation and unlock paths, and an explanatory dialog instead of a crash when stored parameters exceed device capacity.
4. Keep the "(clamped)" UI marker and real-parameter display in all cases.

The project currently has **no** `android:largeHeap`, **no** NDK/CMake setup and **no** native Argon2 dependency (verified 2026-11-17).

---

## 12. Consequences if JNI is adopted

- No `.vault` format change, no CLI change.
- Build pipeline gains native compilation and ABI-specific artifacts; reproducibility and library provenance must be documented.
- The existing Argon2 calibration, clamping and UI marker remain valid; only the allocator changes.
- The recovery-key design remains a separate, optional layer for the residual transfer case.

---

## 13. References

- `keepass.info/help/base/security.html` — KeePass Security / Protection against Dictionary Attacks (iterations vs memory, parameter procedure, "1 Second Delay" strategy, iOS ≤64 MB note).
- KeePassXC source comment quoted in John the Ripper (`kdbx4`: Argon2d, ~10–35 rounds, 64 MiB, 2 threads).
- RFC 9106 — Argon2 (Argon2id recommended; 64 MiB and 256 MiB parameter sets).
- OWASP Password Storage Cheat Sheet — Argon2id baseline (≥19 MiB, t≥2, p≥1).
- `docs/argon2-calibration-methodology.md` — device measurements, OOM detail, derived model and current safety bounds.
- `CryptoManagerCredentials.kt` — `deriveKeyArgon2id`, `resolveArgon2Params`, `writeVaultHeaderV4`.

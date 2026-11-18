# ADR 0004 — Argon2 memory allocation, cross-device KDF compatibility and the unlock deadlock

- **Status:** Implemented (2026-11-18)
- **Date:** 2026-11-17 (implemented 2026-11-18)
- **Relates to:** [ADR 0002](0002-operational-security-hardening.md) §3 (Argon2id profiles and unlock delay); `docs/argon2-calibration-methodology.md`; FORK_CHANGELOG.md ("Brute-Force direnci"); `CryptoManagerCredentials.kt`
- **Decision owner:** Accepted and implemented (2026-11-18)

> **Terminology.** User-facing text uses **"Encrypted Vault" / "Şifreli Kasa"** and keeps the English phrase **"Brute-Force"** in Turkish strings, as decided earlier.

---

## 1. Context

The "Brute-Force direnci" setting originally let a user pick a fixed target unlock time (`2.0–7.0 s`) for all devices. Two additional requirements and two safety concerns drove the final architecture:

1. **A user must never crash the app merely by picking a target time** in the settings dialog.
2. **A vault created on a strong device must be openable on a weaker device.** If the stored parameters exceed what the weaker device can allocate (`OOM`), the vault cannot be unlocked — whereas taking longer in CPU time (e.g., `20–30 s` on a weak phone for a `7–10 s` flagship vault) is acceptable.
3. **Standard security should be measured in KDF parameters, with a "1.0 s sweet spot" default.** The safe RFC 9106 floor is `64 MiB / 3 rounds / p=2`. If this floor runs in `<= 1.0 s` on the device, the default unlock target is `1.0 s`; if the device is slower, the default equals the device's measured safe-floor duration (`T_std_64`). Users can adjust the slider in `0.1 s` increments from `T_std_64` up to `10.0 s`.
4. **Users should still be able to step up to `128 MiB` memory** when device heap permits, without risking cross-device OOM deadlocks (`256 MiB` must never be selected automatically).

---

## 2. What we use today (Post-Decision)

- **Variant: Argon2id, version 1.3.** `deriveKeyArgon2id` uses `Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)` with `ARGON2_VERSION_13` (BouncyCastle). The header stores `KDF: 1 = Argon2id` and the marker is `argon2id_aes256_gcm`.
- **Rationale for Argon2id:** Argon2d is stronger against GPU/ASIC but side-channel sensitive; Argon2i is the opposite; Argon2id is the hybrid and the RFC 9106 / OWASP recommended default.
- **Implementation constraint:** BouncyCastle's `Argon2BytesGenerator` allocates the whole working memory as a **single Java `int[]`** — inside the **Java heap**.

Current safety & standard bounds (implemented 2026-11-18):

- **RFC 9106 safe floor & "1.0 s sweet spot" default:** Minimum parameters are `64 MiB` (`65,536 KiB`) / `3` iterations / `2` parallelism (the legacy `32 MiB / 2 rounds` profile and ready-made preset modes were removed). With unlock-time calibration, `defaultUnlockTargetMs = max(1_000 ms, T_std_64)`: fast devices default to `1.0 s` (and can even step down to `T_std_64`, e.g. `0.4–0.9 s`), while slower devices default to `T_std_64`.
- **Dynamic slider range (`T_std_64 .. 10.0 s` in `0.1 s` steps):** Replaces the old hardcoded `2.0–7.0 s` bounds.
- **Tiered `64 -> 128 MiB` portable ceiling & elastic iterations (`3..100`):** Targets `>= 2 * T_std_64` step up to `128 MiB` when `Runtime.maxMemory() / 2 >= 128 MiB` (falling back to `64 MiB` on constrained heaps or `OutOfMemoryError`), and scale `iterations` in `3..100` up to `10.0 s`. Memory never exceeds `128 MiB`.
- **`android:largeHeap="true"` + OOM cache trim:** Enabled in `AndroidManifest.xml` (expanding Java heap limits from 128–256 MB to 256–512+ MB) alongside automatic Glide RAM cache clearing and GC retry on `OutOfMemoryError`.
- **`.vault` header validation & background execution:** Both Android (`CryptoManagerCredentials.kt`) and Python CLI (`cipher_vault.py`) accept `iterations in 1..256` (fixing the prior `1..10` vs `2..16` unlock deadlock), and Argon2id runs exclusively on background threads to prevent 5-second Android ANRs on 7–30s unlocks.

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

## 11. Decision (Implemented 2026-11-18)

Rather than introducing an NDK/CMake build pipeline for native C Argon2 right away, the project adopts a **Java-heap portable ceiling + RFC 9106 parameter floor + authenticated re-wrapping** architecture:

1. **RFC 9106 safe floor & "1.0 s sweet spot" default (`max(1.0 s, T_std_64)`):** The safe minimum is `64 MiB / 3 rounds / 2 parallelism`. When `T_std_64 <= 1.0 s`, the default unlock target is `1.0 s`; on slower phones where `T_std_64 > 1.0 s`, the default equals `T_std_64`. In the unlock-time dialog, users can dial in `0.1 s` increments from `T_std_64` (including `< 1.0 s` on fast phones) up to `10.0 s`.
2. **Tiered `64 -> 128 MiB` portable ceiling & elastic iterations (`3..100`):** High-target calibrations (`>= 2 * T_std_64`) step up to `128 MiB` (never `256 MiB`) when `maxMemory() / 2 >= 128 MiB`, then scale `iterations` up to `100` (reaching `10.0 s` without needing `256 MiB`). Because memory stays capped at `<= 128 MiB` and `android:largeHeap="true"` provides a 256–512+ MB heap across devices (with automatic Glide RAM cache trimming + GC retry on OOM), even a `10.0 s` flagship vault opens reliably on weaker devices instead of deadlocking.
3. **Authenticated in-place `.vault` header re-wrapping:** When changing Brute-Force resistance on an existing vault, the app prompts for the current password/PIN (biometrics disabled), unwraps `VMK` and `KEK` under the old KDF on a background thread, and atomically re-wraps them under the newly calibrated PDK (`rewrapVaultHeaderWithCurrentKdf`). No weaker fallback wrapper is ever stored.
4. **Critical hardening applied alongside the decision:**
   - Expanded `.vault` header validation from `1..10` to `1..256` iterations in both Android (`CryptoManagerCredentials.kt`) and `cipher_vault.py`.
   - Caught `OutOfMemoryError` (`java.lang.Error`) on unlock/setup/rewrap paths so allocation failure never crashes the app.
   - Moved all Argon2id work off the Android UI thread (`VaultAuthInputHelper.unlockWithSecretAsync` and `VaultSecurityDialog`) to prevent 5-second ANRs during slow unlocks.
   - Updated `completeBiometricUnlock` in `CryptoManagerBiometrics.kt` to unwrap `unlockedKek` from `.vault` using the biometric-unwrapped `VMK`, and guarded `setupOrUpdateCredentialsInternal` against replacing an unloaded `KEK`.

Deferred / future options:
- Native JNI Argon2 (`§9`) and exported Recovery Key (`§10`) remain valid future enhancements if memory requirements above `128 MiB` or sub-128 MB heap legacy hardware ever need to be supported.

---

## 12. Consequences of this decision

- No `.vault` container format break; no NDK/CMake build complexity added.
- Ready-made preset profiles (`Default` / `High security`) were removed in favor of unified unlock-time calibration (`T_std_64 .. 10.0 s` in `0.1 s` steps, defaulting to `max(1.0 s, T_std_64)`), enforcing RFC 9106 minimums and keeping cross-device compatibility intact.
- Native JNI Argon2 (§9) can still be dropped in later as a pure allocator swap without changing derived keys or `.vault` headers.

---

## 13. References

- `keepass.info/help/base/security.html` — KeePass Security / Protection against Dictionary Attacks (iterations vs memory, parameter procedure, "1 Second Delay" strategy, iOS ≤64 MB note).
- KeePassXC source comment quoted in John the Ripper (`kdbx4`: Argon2d, ~10–35 rounds, 64 MiB, 2 threads).
- RFC 9106 — Argon2 (Argon2id recommended; 64 MiB and 256 MiB parameter sets).
- OWASP Password Storage Cheat Sheet — Argon2id baseline (≥19 MiB, t≥2, p≥1).
- `docs/argon2-calibration-methodology.md` — device measurements, OOM detail, derived model and current safety bounds.
- `CryptoManagerCredentials.kt` — `deriveKeyArgon2id`, `resolveArgon2Params`, `writeVaultHeaderV4`.

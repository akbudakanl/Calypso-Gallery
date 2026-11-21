# Argon2id calibration methodology and measurements (2026-11-17)

## 1. Purpose

Calypso Gallery derives the Encrypted Vault master key with **Argon2id**. The cost parameters (memory, iterations, parallelism) are stored in the `.vault` header, so they travel with the vault to other devices — exactly like KeePass stores KDF rounds inside a `.kdbx` file. This document records how the cost profiles were measured on a real Android device and how the "vault unlock time" setting is derived from those measurements.

## 2. Historical BouncyCastle baseline — test device (2026-11-17)

| Property  | Value                                                                     |
| :-------- | :------------------------------------------------------------------------ |
| Model     | Xiaomi Redmi Note 10s                                                     |
| RAM       | 8 GB                                                                      |
| Android   | (as reported by device; MIUI)                                             |
| App build | `gallery-39-foss-debug.apk` built from this working tree                  |
| Provider  | BouncyCastle `Argon2BytesGenerator` via `CryptoManager.deriveKeyArgon2id` |

The device contains only test data. No album, virtual-album, file or vault content was inspected during measurement — only CPU timing values were read from `logcat`.

## 3. Measurement method

1. A **temporary** probe was added to `App.onCreate()`. It was triggered by the existence of a marker file `argon2_probe` in the app's private external files directory (`/sdcard/Android/data/com.calypso.gallery.debug/files/`). No marker → zero overhead.
2. On trigger, the probe deleted the marker, spawned a background thread, and for each candidate parameter set ran `deriveKeyArgon2id("probe", fixed16ByteSalt, memKb, iterations, parallelism)` **two times**, measuring wall-clock time with `System.nanoTime()` and logging the **first (warm-up-excluded) sample** in milliseconds under logcat tag `Argon2Probe`.
3. The APK was installed with `adb install -r`, the app was force-stopped, the marker created with `adb shell touch`, and the launcher activity started with `adb shell monkey`. Results were read with `adb logcat -s Argon2Probe:I -d`.
4. After measurement, the temporary probe code was **removed from the source tree** and a clean APK was rebuilt. No probe code ships in the final build.
5. For comparison, the identical operation was measured on the development machine (desktop JVM) with a temporary JUnit test, which was also removed afterwards.

This is wall-clock time for the KDF only. It excludes UI, I/O and password entry, so the real "vault unlock time" is slightly longer.

## 4. Raw results

### Android (Redmi Note 10s, 8 GB RAM)

| Label | Memory | Iterations | Parallelism | Measured |
| :--- | :--- | :--- | :--- | :--- |
| LOW | 32 MiB | 2 | 1 | **904 ms** |
| DEFAULT | 64 MiB | 3 | 2 | **2 410 ms** |
| HIGH | 128 MiB | 4 | 2 | **6 927 ms** |
| (attempted 10 s) | 256 MiB | 6 | 2 | **OutOfMemoryError → process killed** |

### Desktop JVM (same code, for comparison)

| Label | Measured |
| :--- | :--- |
| LOW | 132 ms |
| DEFAULT | 370 ms |
| HIGH | 909 ms |

The phone is roughly **6–7× slower** than the desktop, mostly due to memory-bandwidth differences.

### OOM detail (important)

The 256 MiB / 6 / 2 attempt failed with:

```
Throwing OutOfMemoryError "Failed to allocate a 1040 byte allocation with 471072 free bytes ...
target footprint 268435456, growth limit 268435456 ..."
```

The Android heap growth limit for this app was **256 MB**, so a single 256 MiB Java `int[]` Argon2 buffer could not be allocated under BouncyCastle regardless of the device's 8 GB physical RAM. Moving the Argon2 buffer off the Java heap into native Rust JNI (`rust/calypso-argon2`) lifted this restriction completely.

### Android — Rust Native JNI (2026-11-21, same Redmi Note 10s)

Measured on the same device (`arm64-v8a`, SDK 33, `ActivityManager.MemoryInfo.totalMem = 7 607 MiB`, `is64Bit = true`) via an isolated `androidTest` instrumentation run of `CryptoManager.deriveKeyArgon2id` (two consecutive runs logged per profile, no UI/vault/file access; golden vector parity against `argon2-cffi` / BouncyCastle and catchable `OutOfMemoryError` on `Int.MAX_VALUE` KiB both passed on hardware):

| Profile | Memory | Iterations | Parallelism | Run 1 (cold) | Run 2 (warm) | Speedup vs BouncyCastle |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Floor (`T_std_64`) | 64 MiB | 3 | 2 | **281 ms** | **271 ms** | **~8.6×** (was 2 410 ms) |
| High tier | 128 MiB | 3 | 2 | **632 ms** | **574 ms** | — |
| Legacy HIGH | 128 MiB | 4 | 2 | **729 ms** | **755 ms** | **~9.5×** (was 6 927 ms) |
| Ultra tier | 256 MiB | 3 | 2 | **1 291 ms** | **1 159 ms** | Previously OOM |
| Former crash case | 256 MiB | 6 | 2 | **2 263 ms** | **2 451 ms** | **Fixed** (was OOM kill) |

## 5. Derived model (updated 2026-11-21 — Rust Native Argon2id JNI, ADR 0004 §11.1)

- **Native Rust KDF engine (`rust/calypso-argon2`):** On 2026-11-21, Argon2id moved out of BouncyCastle (`Java int[]` heap allocation) into a pure-Rust JNI shared library (`libcalypso_argon2.so` using `RustCrypto/argon2` + `zeroize` + fallible `Vec::try_reserve_exact`), eliminating the artificial 256 MB Java heap limit and removing BouncyCastle from the runtime APK. `kdfEngineVersion` (`KDF_ENGINE_RUST_V1 = 1`) automatically invalidates prior BouncyCastle `cachedStdArgon2Ms` / `vaultUnlockMeasuredMs` caches on upgrade.
- At fixed memory and parallelism, Argon2id time scales approximately **linearly with iterations**. This allows calibration from a single measured standard run instead of a long search:
  1. Measure one standard run at `64 MiB / 3 iterations / p=2` on the device (`std64Ms`, cached in `cachedStdArgon2Ms`, floor `ARGON2_MIN_MEASURED_MS = 20 ms`).
  2. The default unlock target uses a **"1.0 s sweet spot"** (`max(1.0 s, std64Ms)`): fast phones default to `1.0 s` (scaling rounds/memory above the `64 MiB / 3 rounds` floor), while slower phones default to `std64Ms`.
  3. In the **"Target vault unlock time"** dialog (`BruteForceDialog`), the slider's lower bound is `max(0.1 s, ceil_tenths(std64Ms))` and the upper bound is **`10.0 s`** in `0.1 s` increments.
  4. **Three-tier physical-RAM + 64-bit scaling (`64 → 128 → 256 MiB`):**
     - `256 MiB` (`ARGON2_ULTRA_MEMORY_KB`): selected when `targetMs >= max(4 * std64Ms, 3.0 s)` (`ARGON2_ULTRA_MIN_TARGET_MS = 3_000`), the device supports 64-bit ABIs (`Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()`), and `ActivityManager.MemoryInfo.totalMem >= 5_120 MiB` (~6 GB+ RAM devices), verified via `measureUltraMemoryArgon2Ms` (falls back cleanly on `OutOfMemoryError`). When the selected target time maps to `256 MiB`, `BruteForceDialog` displays a calm portability and recovery-guidance warning (`brute_force_high_memory_warning`) for `<= 2 GB RAM` devices.
     - `128 MiB` (`ARGON2_HIGH_MEMORY_KB`): selected when `targetMs >= 2 * std64Ms` and `totalMem >= 1_536 MiB` (~2 GB+ RAM devices) — or as the automatic fallback if `256 MiB` is unavailable/OOM — verified via `measureHighMemoryArgon2Ms`.
     - `64 MiB` (`ARGON2_MIN_MEMORY_KB`): RFC 9106 floor and fallback for low-RAM devices or `OutOfMemoryError` on higher tiers.
     - On the selected memory tier, `iterations = clamp(round(targetMs / perIterMs), 3, 100)` scales up to `10.0 s`.
- The chosen target and resulting `memoryKb / iterations / parallelism` are written into `.vault`; changing settings on an existing vault re-wraps the same `VMK`/`KEK` after verifying the user's current password/PIN (`rewrapVaultHeaderWithCurrentKdf`).
- **Existing-vault time display (reinstall / device transfer):** the local `unlockTargetMs` is only a creation default and is not portable. For an existing vault the Settings/vault-setup summary shows the real unlock time **on the current device**: `estimateVaultUnlockMs(std64Ms, headerParams)` (memory scales linearly, so `128 MiB` costs `~2×` and `256 MiB` costs `~4×` per round) until a successful unlock measures the actual KDF run and caches it in `vaultUnlockMeasuredMs`. The value carries an "on this device" qualifier, and when it exceeds the `10 s` slider maximum an explicit note is shown instead of silently clamping it (sub-second values remain selectable down to the KDF floor). When the device has no baseline yet, only the stored `memory / rounds` line is shown; no time value is invented. Re-wrap, password change, vault switch/removal and reinstall reset the cached measurement so it is recalculated from the portable header cost.

## 6. Safety bounds (implemented)

To prevent crashes, ANRs, and cross-device lockouts:

- **RFC 9106 Floor:** Minimum parameters remain **`64 MiB / 3 iterations / 2 parallelism`** (`32 MiB / 2 rounds` removed).
- **Elastic Iteration Ceiling:** Calibrated iterations are clamped to **`3…100`**. `.vault` header validation on both Android and CLI accepts **`1…256`** iterations and up to `256 MiB` (`262_144 KiB`) on Android (`1 GiB` on CLI).
- **Native Heap + Fallible Allocation:** Native Argon2 working memory is allocated via `Vec::try_reserve_exact` off the Java heap (not `Runtime.maxMemory()`), converting allocation failures into catchable `OutOfMemoryError`s with Glide cache trim + GC retry and automatic tier step-down (`256 → 128 → 64 MiB`).
- **Background Thread Execution:** All Argon2id operations (`setupOrUpdateCredentials`, `unlockWithSecret`, `rewrapVaultHeaderWithCurrentKdf`, `measureStandardArgon2Ms`) run off the UI thread so slow unlocks never trigger an Android 5-second ANR.
- When the target could not be met (clamped to floor/ceiling or RAM-limited), the UI shows a **"(kırpıldı) / (clamped)"** marker together with the **actually stored parameters** (`memory MiB / rounds`).

## 7. Limitations

- One device, one build, two samples per point in initial measurements. Numbers are order-of-magnitude guidance, not a benchmark suite.
- Thermal throttling, battery saver and background load can shift results.
- Measuring `std64Ms` costs one `64 MiB / 3 rounds` run (~0.4–2.5 s depending on hardware) on background startup of Settings or first entry to the custom time dialog.
- Wall-clock target cannot be guaranteed across devices; only the stored KDF parameters are portable (by design, matching KeePass — e.g. a `7–10 s` flagship vault takes longer on a slower phone without OOM).

## 8. Status

These measurements and ADR 0004 are the basis for the "Brute-Force direnci" implementation across `BruteForceDialog`, `SettingsActivity`, `VaultSecurityDialog`, `VaultAuthInputHelper`, and `CryptoManagerCredentials`.

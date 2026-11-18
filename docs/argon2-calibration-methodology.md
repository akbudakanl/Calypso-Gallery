# Argon2id calibration methodology and measurements (2026-11-17)

## 1. Purpose

Calypso Gallery derives the Encrypted Vault master key with **Argon2id**. The cost parameters (memory, iterations, parallelism) are stored in the `.vault` header, so they travel with the vault to other devices — exactly like KeePass stores KDF rounds inside a `.kdbx` file. This document records how the cost profiles were measured on a real Android device and how the "vault unlock time" setting is derived from those measurements.

## 2. Test device

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

The Android heap growth limit for this app is **256 MB**, so a single 256 MiB Argon2 buffer cannot be allocated regardless of the device's 8 GB physical RAM. Any target time that requires more than 128 MiB of Argon2 memory is **not achievable on Android** with the current heap limit.

## 5. Derived model (updated 2026-11-18, ADR 0004 Implemented)

- At fixed memory and parallelism, Argon2id time scales approximately **linearly with iterations**. This allows calibration from a single measured standard run instead of a long search:
  1. Measure one standard run at `64 MiB / 3 iterations / p=2` on the device (`std64Ms`, cached in `cachedStdArgon2Ms`).
  2. In the default time-based mode (`bruteForceMode = 0`), the default unlock target uses a **"1.0 s sweet spot"** (`max(1.0 s, std64Ms)`): fast phones default to `1.0 s` (scaling rounds/memory above the `64 MiB / 3 rounds` floor), while slower phones default to `std64Ms`.
  3. In **"Via vault unlock time"**, the slider's lower bound is the device's measured `std64Ms` (`T_std_64`) and the upper bound is **`10.0 s`** in `0.1 s` increments.
  4. If the target time `>= 2 * std64Ms` and `Runtime.maxMemory() / 2 >= 128 MiB`, calibration measures `128 MiB / 3 iterations / p=2` (`measureHighMemoryArgon2Ms`, falling back cleanly to `64 MiB` on `OutOfMemoryError`) and scales `iterations = clamp(round(targetMs / perIterMs), 3, 100)` up to `10.0 s`. Memory never exceeds `128 MiB`.
- The chosen target and resulting `memoryKb / iterations / parallelism` are written into `.vault`; changing settings on an existing vault re-wraps the same `VMK`/`KEK` after verifying the user's current password/PIN (`rewrapVaultHeaderWithCurrentKdf`).

## 6. Safety bounds (implemented)

To prevent crashes, ANRs, and cross-device lockouts:

- **RFC 9106 Floor:** Minimum parameters are **`64 MiB / 3 iterations / 2 parallelism`** (`32 MiB / 2 rounds` removed).
- **Elastic Iteration Ceiling:** Calibrated iterations are clamped to **`3…100`** (allowing `10.0 s` targets on flagship devices at `64–128 MiB` without inflating memory to `256 MiB`). `.vault` header validation on both Android and CLI accepts **`1…256`** iterations.
- **Portable Memory Ceiling + `android:largeHeap="true"`:** Memory is capped at **`128 MiB`** (half of `Runtime.maxMemory()` check + `largeHeap="true"` in `AndroidManifest.xml`), with automatic Glide RAM cache trimming + GC retry on `OutOfMemoryError`.
- **Background Thread Execution:** All Argon2id operations (`setupOrUpdateCredentials`, `unlockWithSecret`, `rewrapVaultHeaderWithCurrentKdf`, `measureStandardArgon2Ms`) run off the UI thread so `10–30 s` unlocks never trigger an Android 5-second ANR.
- When the target could not be met (clamped to floor/ceiling or heap-limited to `64 MiB`), the UI shows a **"(kırpıldı) / (clamped)"** marker together with the **actually stored parameters** (`memory MiB / rounds`).

## 7. Limitations

- One device, one build, two samples per point in initial measurements. Numbers are order-of-magnitude guidance, not a benchmark suite.
- Thermal throttling, battery saver and background load can shift results.
- Measuring `std64Ms` costs one `64 MiB / 3 rounds` run (~0.4–2.5 s depending on hardware) on background startup of Settings or first entry to the custom time dialog.
- Wall-clock target cannot be guaranteed across devices; only the stored KDF parameters are portable (by design, matching KeePass — e.g. a `7–10 s` flagship vault takes longer on a slower phone without OOM).

## 8. Status

These measurements and ADR 0004 are the basis for the "Brute-Force direnci" implementation across `BruteForceDialog`, `SettingsActivity`, `VaultSecurityDialog`, `VaultAuthInputHelper`, and `CryptoManagerCredentials`.

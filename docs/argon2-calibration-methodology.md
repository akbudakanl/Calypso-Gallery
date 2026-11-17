# Argon2id calibration methodology and measurements (2026-11-17)

## 1. Purpose

Fossify Gallery Cipher derives the Encrypted Vault master key with **Argon2id**. The cost parameters (memory, iterations, parallelism) are stored in the `.vault` header, so they travel with the vault to other devices — exactly like KeePass stores KDF rounds inside a `.kdbx` file. This document records how the cost profiles were measured on a real Android device and how the "vault unlock time" setting is derived from those measurements.

## 2. Test device

| Property | Value |
| :--- | :--- |
| Model | Xiaomi Redmi Note 10s |
| RAM | 8 GB |
| Android | (as reported by device; MIUI) |
| App build | `gallery-39-foss-debug.apk` built from this working tree |
| Provider | BouncyCastle `Argon2BytesGenerator` via `CryptoManager.deriveKeyArgon2id` |

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

## 5. Derived model

- At fixed memory and parallelism, Argon2id time scales approximately **linearly with iterations**. This allows calibration from a single measured reference point instead of a long search:
  1. Measure one reference run at `64 MiB / 2 iterations / p=2` on the device.
  2. `msPerIteration ≈ measuredMs / 2`.
  3. `iterations = clamp(round(targetMs / msPerIteration), MIN=2, MAX=16)`.
  4. If target > 5 s and the device heap allows, prefer 128 MiB and recompute iterations accordingly.
- OWASP baseline is ≥19 MiB, ≥2 iterations, ≥1 thread. Since `iterations` is clamped to ≥2, the **minimum practical target on this device is ~1.6 s at 64 MiB**; a 1.0 s request would silently clamp to the minimum. The UI must communicate this rather than pretend a 1.0 s target is achievable.
- Practical, safe target range on this device: **~2.0 s – ~7.0 s**, default **2.4 s** (the measured DEFAULT profile).
- The chosen target is a **device-side preference used only at vault creation / password change**. The calibrated `memoryKb / iterations / parallelism` are written into `.vault`; unlocking always uses the stored values, so the vault behaves like a KeePass database — same KDF cost everywhere, different wall-clock time per device.

## 6. Safety bounds (implemented)

To prevent crashes and unusable vaults on low-end devices:

- **Iterations** are clamped to **2…16** regardless of target. A very fast device asking for 2.4 s may hit the 16-round ceiling and unlock sooner than requested; a very slow device hits the 2-round floor and cannot go weaker.
- **Memory** is capped at **half of `Runtime.maxMemory()`**, with a floor of 64 MiB. Requesting 128 MiB on a device with a 128 MiB heap therefore degrades to 64 MiB instead of crashing.
- If the 128 MiB reference allocation still throws `OutOfMemoryError`, calibration falls back to 64 MiB and retries; if even that fails, the OWASP floor is used.
- When the target could not be met (clamped at 2/16 rounds or memory reduced), the UI shows a **"(kırpıldı) / (clamped)"** marker together with the **actually stored parameters** (`memory MiB / rounds`), so the target-time label is never mistaken for a portable security guarantee.

## 7. Limitations

- One device, one build, two samples per point. Numbers are order-of-magnitude guidance, not a benchmark suite.
- Thermal throttling, battery saver and background load can shift results.
- Calibration itself costs roughly one reference run (~1.5–2.5 s on this device). It must run off the main thread with a progress indicator; a "use default" escape hatch is required.
- Wall-clock target cannot be guaranteed across devices; only the stored KDF parameters are portable (by design, matching KeePass).

## 7. Status

These measurements are the basis for the "Brute-Force direnci" setting: a mode switch between **unlock-time-based** (default) and **preset-mode-based** calibration. See the implementation in `SettingsActivity` and `CryptoManagerCredentials`.

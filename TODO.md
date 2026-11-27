# TODO List

## Encrypted Vault & Privacy

### [x] Prevent internal `Pictures/` fallback for encrypted vaults using external storage (SD card / USB OTG)

- **Status:** The silent `Pictures/` fallback was removed. When an album has no recorded `.origin` or its external SD card / USB OTG location is unavailable, the physical destination folder picker (`FilePickerDialog`) is opened directly so hidden media are never written to the internal `Pictures/` directory without the user's consent.
- **Future Improvement Idea:** Optionally add an extra restriction toggle under Settings > Encrypted Vault that allows only external storage paths in the destination picker when decrypting from an external vault.

## File Operations & Notifications

### [ ] Re-enable Android notifications for file operations

- **Current Status (Suspended):** Android file operation notifications have been temporarily suspended via `ANDROID_NOTIFICATIONS_SUSPENDED = true` in [FileOperationService.kt](app/src/main/kotlin/org/fossify/gallery/helpers/operations/FileOperationService.kt); [OperationProgressFormatter.kt](app/src/main/kotlin/org/fossify/gallery/helpers/OperationProgressFormatter.kt) skips requesting notification permission, and the Settings → Notifications section has been removed in [SettingsActivity.kt](app/src/main/kotlin/org/fossify/gallery/activities/SettingsActivity.kt). In-app progress dialogs (`CryptoProgressDialog`) and `PARTIAL_WAKE_LOCK` CPU protection remain active.
- **Preserved Infrastructure:** The layout (`activity_settings.xml` `settings_category_notifications`), `Config.kt` notification preferences (`notificationsEnabled`, `notifyCopyMove`, `notifyCrypto`, `notifyDelete`, `notifyVirtualAlbum`), `OperationNotificationCategory.kt`, and all 11 locale `strings.xml` entries were kept intact without deletion.
- **Checklist Before Re-enabling:**
  1. Verify on a physical device via `adb logcat` why `FileOperationService` / `NotificationManager` / `POST_NOTIFICATIONS` / OEM background restrictions suppressed notifications.
  2. Set `FileOperationService.ANDROID_NOTIFICATIONS_SUSPENDED = false`.
  3. Restore `CATEGORY_NOTIFICATIONS` in `SettingsActivity.kt` (`categorySpecs`, `settingsHubGeneralCard`, and `setupNotifications()`, referencing commit `9b55e8faf` if needed).
  4. Preserve the two-tier design: a minimal "in progress" notification while the app is in the foreground, a progress bar + anonymous `"current / total (%x)"` summary when backgrounded, and immediate removal (`STOP_FOREGROUND_REMOVE`) with no completion notification when finished.

## Isolated Media Decoding

### [ ] Evaluate pure-Rust AVIF decoding (`rav1d` + `mp4parse`) in `rust/calypso-media/`

- **Context:** `glide:avif-integration` (`libavif`/`libaom`) was removed (ADR 0009 Option 2) so zero third-party C/C++ image decoder `.so` binaries remain in the APK. Android 12+ (`API 31+`) decodes AVIF via OS `ImageDecoder` inside `isolatedProcess="true"`; Android 8–11 (`API 26–30`) currently has AVIF decoding disabled.
- **Future Goal (Option 3):** Evaluate adding pure-Rust `rav1d` + `mp4parse` inside `rust/calypso-media/` to restore AVIF support on Android 8–11 and add defense-in-depth on newer OS versions without reintroducing C/C++ FFI, weighing APK binary size and build complexity. See [docs/adr/0009-isolated-media-decoding.md](docs/adr/0009-isolated-media-decoding.md).

### [ ] Upgrade static WebP from Option A to Option B (100% pure-Rust static WebP) when `image-webp` gains lossy SIMD

- **Context:** `image-webp (0.2.4)` currently handles all lossless (`VP8L`) WebP, animated WebP playback, and lossy (`VP8 `) WebP up to `4 MP` (`MAX_RUST_LOSSY_WEBP_PIXELS`), while `> 4 MP` valid lossy WebP defers to isolated OS codecs to avoid a ~4–10× slowdown without NEON/SIMD.
- **Future Goal:** Once upstream `image-webp` ships NEON/SIMD support for lossy WebP, remove `MAX_RUST_LOSSY_WEBP_PIXELS` so all static WebP (`VP8L` + `VP8 `) is decoded 100% in pure Rust.

### [ ] Evaluate migrating `jxl-oxide` to `jxl-rs` once `jxl-rs` stabilizes on crates.io

- **Context:** JPEG XL currently runs via pure-Rust `jxl-oxide (0.12.6)` inside `rust/calypso-media/` capped at `32 MP` (`MAX_JXL_SOURCE_PIXELS`). Both Chromium and Firefox adopted Google's continuously fuzzed pure-Rust `jxl-rs` crate.
- **Future Goal:** Switch `rust/calypso-media/` from `jxl-oxide` to `jxl-rs` when its crates.io release and API stabilize cleanly for offline `--locked` builds.

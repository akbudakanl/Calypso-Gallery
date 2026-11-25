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

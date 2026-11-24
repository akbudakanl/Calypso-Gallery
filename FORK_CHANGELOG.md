# Changelog

User-facing release notes for Calypso Gallery. The original Fossify Gallery release history is kept in [CHANGELOG.md](CHANGELOG.md).

## [Unreleased]

### Fixed

- **Video mute state and icon consistency** — each video now keeps its own mute state, matching upstream Fossify Gallery where every page has its own player. Toggling mute on one video no longer re-applies the preference to other videos while swiping, and the speaker icon always reflects the audio the player actually produces.

## [1.0.1] - 2026-11-24

### Changed

- **Pull-to-open vault indicator** — removed the post-settle lock/disc scale pop and continuous breathing pulse animation while preserving the exact top light slit, lock descent, surface disc, rim, backlit radial glow, and haptic arming feedback.

### Fixed

- **Copy and move destination picker** — destination folder selection no longer shows "No media files have been found with the selected filters" when folders exist. `MainActivity` now persists newly discovered folders to the directory cache immediately instead of dropping them on early scan cancellation, `PickDirectoryDialog` falls back to a live MediaStore scan (including disk-only and included folders) when the cache is empty or stale (such as when launching directly into Files view), active media type filters no longer hide valid copy/move/decrypt destination folders, and copying from a virtual album now displays all valid real albums instead of excluding the stale owner folder.
- **Share metadata temp folder** — `Pictures/Calypso/Shared` now creates a `.nomedia` marker directly on all Android versions (and repairs existing folders missing `.nomedia` on startup) and is excluded as an internal path so it never appears as a gallery album.

## [1.0.0] - 2026-11-24

The first public release. The entries below summarize, by concept rather than by individual commit, the changes accumulated throughout the fork.

### Added

- **Encrypted vault** — end-to-end encryption with Tink streaming AEAD, Argon2id key derivation, biometric unlock, password changes, vault export/import, archiving and forgotten-password recovery.
- **Virtual albums and favorites** — reference media without duplicating files, path repair after folder moves or renames, automatic backups and zip export/import.
- **File operations** — a single background notification for copy, move, encrypt, decrypt and delete, with progress dialogs and resumption after interruption.
- **Smart conflict handling** — byte-by-byte comparison, side-by-side preview dialogs, automatic deduplication and album merge modes.
- **Viewer and media** — encrypted media, GIF, video and SVG playback, album shuffle, video controls and scrolling improvements.
- **Settings and interface** — in-app language selector, light/dark/custom theme palettes, settings search, category cards, Calypso overflow menu and a first-run welcome flow.
- **Tablet and large-screen support** — responsive grid columns, large-screen dialogs and landscape layout improvements.
- **Desktop tooling** — a CLI for creating and decrypting vaults, syncing an external vault and offline use with Tails OS.
- **New Appearance preset themes** — added **Velvet Violet** (`#0E0A18` background, `#1A132B` surface, `#A78BFA` primary) and **Soft Coral** (`#FFF7F5` background, `#FFFFFF` surface, `#C44047` primary), expanding the built-in presets from 5 to 7 (alongside Light, Dark, Midnight Blue, Warm Paper, and Deep Forest).

### Changed and redesigned

- **App design revised** — color palette, icon set, top and bottom bars, selection action bar, welcome cards, vault lock screen and overflow menus were redesigned around one visual language.
- **Cryptography rebuilt** — asymmetric RSA was removed and the vault container format reduced to a single version. Key derivation moved first to BouncyCastle, then to native Rust/Argon2id via JNI. The password strength estimator runs entirely on-device.
- **Storage layout** — vault data moved to the `Secrets` folder, favorites and virtual-album backups into app storage; an external vault location and phone synchronization were added.
- **Distribution and identity** — the application ID changed to `com.calypso.gallery`; the project name and icon were rebranded and GPLv3 / third-party license screens were added.
- **Pull-to-open vault indicator** — updated `VaultPullIndicatorView` with `VaultPullTonePalette` for the AD-1 sequential light handoff: an elliptical top light slit, a unified `palette.surface` circular glass disc with a backlit `primary` aura, constant-hue tonal transitions with Custom theme contrast safeguards, calmer timing, and `95dp` → `115dp` asymptotic rubber-band damping.
- **Appearance preset layout** — the dark presets (Dark, Midnight Blue, Velvet Violet, Deep Forest) are grouped in the left column and the light presets plus Custom in the right column.

### Upstream (Fossify Gallery) compatibility and fixes

- Android 15/16 runtime hardening; forced screen orientation on tablets was disabled for new API compatibility.
- Upstream donation, rating, "Thank You" and modded-app detection prompts were removed.
- Upstream crashes and regressions were fixed: vault unlock, large GIF thumbnails, video playback controls and MediaStore observer delays.
- Dependencies and documentation were updated against the upstream `v1.14.2` base.

### Security and privacy

- Screenshot blocking on vault screens, clipboard and screen-share leak protection, and RAM zeroization after locking.
- The network permission was removed entirely; the app runs offline only.
- Staging, atomic replacement, read-back verification and post-interruption recovery for file transfers.

### Translations and verification

- The new interface dialogs are translated into up to 11 locales; blind translation audits and one-to-one comparison reports are kept in `translation-blind-tests`.
- Unit tests cover crypto vectors, vault format, file identity, conflict resolution and UI behavior.
- Architecture decision records live in `docs/adr`; security audit and design documents are available in multiple languages.

### Changed

- Renaming an album is now offered only when a single album is selected.
- "Reorder folders by dragging" is shown only while the album sort order is set to Custom. With Custom sorting it is also available directly in the album list's overflow menu, above "Change view type".
- Selected virtual albums can now be copied and added to other virtual albums from the selection menu.
- "Resize" is checked by default in Settings › Media viewer › Manage extra actions; all other extra actions stay unchecked by default.
- Turning the "Automatically show biometric prompt when returning to app" setting on or off now requires re-verifying the vault with the password/PIN or biometrics, like the other vault-wide security switches.
- The per-digit PIN dot indicator now supports PINs of up to 14 digits instead of 16, so the dots keep a comfortable spacing on narrow screens. Existing 15- or 16-digit PINs turn the setting off on next unlock.

### Fixed

- Pressing Back on the "Have you saved your password?" dialog after vault creation now continues to the one-time biometric offer instead of skipping it. The Security & encrypted vault settings are also refreshed as soon as the vault is created, so the "create vault" row no longer stays visible until the settings category is reopened, regardless of how the follow-up dialogs are dismissed.
- Video playback no longer stops when swiping left or right without actually changing to the neighbouring item; the current video now pauses only once the page really changes.
- Forward/backward skips, seek bar and horizontal gesture seeks are frame-accurate again: a 10-second skip advances 10 seconds instead of snapping to a nearby keyframe.
- **Overflow menu icon tint** — `CalypsoOverflowMenuPopup` now tints all row icons consistently with `palette.secondary`, including the Settings row.
- **Font changes on already open screens** — changing the app font now refreshes the search bar and other text on the main, media and settings screens immediately, without restarting the app or switching between folder and file view.

[Unreleased]: https://github.com/akbudakanl/Calypso-Gallery/compare/1.0.1...HEAD
[1.0.0]: https://github.com/akbudakanl/Calypso-Gallery/releases/tag/1.0.0
[1.0.1]: https://github.com/akbudakanl/Calypso-Gallery/compare/1.0.0...1.0.1

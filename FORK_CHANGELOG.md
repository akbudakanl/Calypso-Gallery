# Changelog

User-facing release notes for Calypso Gallery. The original Fossify Gallery release history is kept in [CHANGELOG.md](CHANGELOG.md).

## [Unreleased]

## [1.1.1] - 2026-11-28
### Added

- **MP4/MOV video metadata stripping on share** — remuxes outgoing `.mp4` and `.mov` videos without re-encoding via `MediaExtractor` and `MediaMuxer` (`Movies/Calypso/Shared` with `.nomedia` protection), preserving orientation while stripping location (`©xyz`), device, and container metadata.
- **JPEG/PNG/WebP container-level metadata stripping** — in **All metadata** mode, removes JPEG `APP13` (IPTC) and `COM` segments, PNG `tEXt`/`iTXt`/`zTXt` chunks, and WebP `EXIF`/`XMP ` RIFF chunks (clearing `VP8X` flags) while preserving color profiles (`ICC`/`sRGB`/`gAMA`) and pixel data.

### Fixed

- **Anonymized and brand-free shared filenames** — **All metadata** mode now generates a random 8-character hex filename (`<hex>.<ext>`) without original timestamps or app names; **Location only** mode now uses `<original>_<hex>.<ext>` instead of the `calypso_shared_` prefix.

## [1.1.0] - 2026-11-27
### Added

- **Process-isolated media decoding (`isolatedProcess`)** — moved image bounds probing, static decoding, tile/region decoding, and animated playback out of the app process into dedicated sandboxed `:media_decoder` (public) and `:vault_decoder` (encrypted vault) processes with `memtagMode="async"` (MTE), input/output pixel caps (`4096` / `32 MP`), and crash quarantine.
- **Pure-Rust media decoders (`calypso-media`)** — bundled pure-Rust `gif`, `png` (including `> 32 MP` row-streamed non-interlaced PNG and APNG), `image-webp` (lossless `VP8L`, `<= 4 MP` lossy `VP8 `, and animated WebP), and `jxl-oxide` (`<= 32 MP` JPEG XL) decoders inside the isolated decoder processes.
- **Fail-closed SVG security toggle** — disabled SVG rendering by default and gated `androidsvg` behind **Settings → Media viewer → Media security → Show SVG files** with a confirmation dialog; unrecognized or disallowed SVG payloads are rejected at the isolated decoder boundary.
- **Ultra HDR (`Gainmap`), wide color gamut, and large-file FD streaming** — restored Android 14+ (`API 34+`) Ultra HDR `Gainmap` and `Display P3` / `BT2020` rendering via validated `SharedMemory` IPC, plus `ParcelFileDescriptor` streaming for `> 100 MiB` (up to `512 MiB`) RAW/TIFF/JPEG/HEIC files.
- **Expanded FAQs** — added localized FAQ entries covering vault encryption, conflict handling, and the `calypso-cli` workflow, and updated password-recovery guidance.

### Changed

- **Removed third-party C/C++ image and filter JNI libraries** — replaced `androidphotofilters` (`libNativeImageProcessor.so`) with pure-Kotlin filters in the editor, and removed `android-gif-drawable` (`giflib`), `webpdecoder` (`libwebp`), `jxl-coder-glide` (`libjxl`), `avif-integration` (`libavif`/`libaom`; AVIF now uses isolated OS `ImageDecoder` on Android 12+), `penfeizhou` animated decoders, `sanselan`, and `sephiroth` EXIF.
- **Refined theme palettes and dark-mode cards** — polished Midnight Blue, Deep Forest, and Velvet Violet color palettes and FAQ dark-mode cards.
- **Updated third-party licenses and translations** — added full MIT/Apache-2.0 notices and localized titles for the pure-Rust media crates across all 11 audited locales.

### Fixed

- **Fullscreen swipe & video thumbnails** — always show a thumbnail when swiping into unvisited videos, and optimized fullscreen swipe prefetching, pooled IPC buffers, and `:vault_decoder` lifecycle cleanup on vault lock.
- **Device-owner gate on vault unarchive** — require device credential or biometric verification before confirming vault unarchiving.

## [1.0.4] - 2026-11-25
### Fixed

- **CLI bridge cleanup on vault deletion** — deleting an encrypted vault now also removes every CLI bridge folder that carries a copy of its header, including the bridges under `Calypso Gallery/CLI/Archived/`. The copied `.vault` header is securely wiped and empty bridge folders are pruned; bridges of other vaults are preserved.
- **Archived bridges stay usable** — the CLI now discovers bridge headers in `CLI/Archived/<vault-id>/`, so an archived vault can keep receiving encrypted files. Its bridge stays in `CLI/Archived` while the vault is archived (never duplicated into `CLI/`), and moves back to `CLI/<vault-id>/` only when the vault is unarchived. The app's import flow and active-bridge markers follow the same rule.
- **No password prompt just for selecting a vault** — choosing an existing or archived vault now only registers its path and public header metadata; the password is asked when the vault is actually opened. Backing out of a later prompt no longer leaves an inconsistent "selected but unlocked with another vault's credentials" state.
- **Device-owner gate on vault location** — when a vault is already active, opening the vault-location option now immediately asks for the device credential / biometric (same check as archiving, skipped when the device has no secure lock), before another vault is selected. First-time setup into an empty vault slot stays ungated.
- **Unarchive restores the correct CLI bridge** — the archived entry now remembers its bridge id, so unarchiving moves exactly that bridge back to `CLI/` even when a different vault was created at the original path. Archived entries discovered from disk are also activatable and respect the "original location already occupied" warning.

## [1.0.3] - 2026-11-25
### Fixed

- **Vault archive dialog flow** — archiving an encrypted vault now shows only the "vault archived" notice with its storage location. The follow-up "create a new encrypted vault" dialog no longer appears.
- **File operation notifications and crash recovery** — hardened journal-based recovery after interrupted operations, posted immediate and fallback notifications when Android restricts foreground service launches, cleared stale completion notifications, and localized progress text.
- **Album decrypt destination picker** — album decryption now uses the physical folder picker and rejects encrypted vault albums as destinations.

### Changed

- **Updated translations** — added localized operation progress and notification strings across the audited locales.

## [1.0.2] - 2026-11-25
### Added

- **Configurable app lock timing** — added a "Lock timing" option in security settings to choose between locking only on app start or on every entry (after backgrounding or screen-off).

### Changed

- **Animated GIF and WebP thumbnails** — enabled animated GIF and WebP thumbnails with rounded corners in album grids, including inside the encrypted vault.
- **Themed file operation progress bars** — file operation and conflict dialogs now tint progress bars with the active theme's primary color.

### Fixed

- **Unified file operations, transactional delete, and crash recovery** — unified progress reporting across delete, recycle bin, restore, and virtual album operations; added transactional delete with journal-based crash recovery and two-tier background/completion notifications.
- **Isolated vault recycle bin emptying** — emptying the recycle bin from Settings now keeps the encrypted vault recycle bin and the normal recycle bin strictly isolated.
- **Viewer keep-screen-on management** — centralized keep-screen-on window flag handling across photo and video viewers.
- **Decrypt destination picker hidden folders** — matched hidden folder visibility in the vault decrypt destination picker to copy/move behavior.
- **Vault album shortcuts** — disabled the "Create shortcut" action for encrypted vault albums.

## [1.0.1] - 2026-11-25

### Added

- **Original-location album decryption and destination-picker fallback** — encrypting a folder now stores its encrypted parent location in `.origin` (keeping `.meta` CLI-compatible). Decrypting a single album restores it to its original location with an option to pick another destination, while multi-album decryption or unavailable/unrecorded origins open the destination picker directly instead of silently falling back to `Pictures/`. Confirmation and album conflict dialogs display destination paths, follow the active `AppearancePalette` across all themes, and include updated translations across all 11 audited locales.

### Changed

- **Pull-to-open vault indicator** — removed the post-settle lock/disc scale pop and added a calm ambient breathing pulse while armed and held (no size pop), preserving the top light slit, lock descent, surface disc, rim, backlit radial glow, and haptic arming feedback.

### Fixed

- **Video mute behaviour per album session** — mute now follows upstream Fossify Gallery: entering an album (or "show all files") starts with sound on, toggling it applies to videos that have not started yet, already playing videos keep their state until they are replayed, and the choice is kept when leaving fullscreen for the album grid. Exiting the album resets it to the default, and the speaker icon always matches the audio the player actually produces.
- **Fullscreen video thumbnails and fast targeted indexing** — stabilized fullscreen video thumbnail caching and added fast targeted/screenshot folder indexing.
- **Duplicate MediaStore entries** — deduplicated MediaStore queries by file path so devices returning duplicate rows no longer display duplicate thumbnails.
- **Copy and move destination picker** — destination folder selection no longer shows "No media files have been found with the selected filters" when folders exist. `MainActivity` now persists newly discovered folders to the directory cache immediately instead of dropping them on early scan cancellation, `PickDirectoryDialog` falls back to a live MediaStore scan (including disk-only and included folders) when the cache is empty or stale and hides empty non-encrypted folders while rendering a clean folder icon for empty vault albums, active media type filters no longer hide valid copy/move/decrypt destination folders, and copying from a virtual album now displays all valid real albums instead of excluding the stale owner folder.
- **Share metadata temp folder** — `Pictures/Calypso/Shared` now creates a `.nomedia` marker directly on all Android versions (and repairs existing folders missing `.nomedia` on startup) and is excluded as an internal path so it never appears as a gallery album.
- **Restricted included folders** — encrypted vault folders, Cipher app system directories and restricted Android storage subpaths can no longer be added to included folders or scanned through them. Existing entries are filtered out of the included-folders list and media scans, and trying to add one now shows an explanatory message (translated into the audited locales).

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

[Unreleased]: https://github.com/akbudakanl/Calypso-Gallery/compare/1.1.1...HEAD
[1.0.0]: https://github.com/akbudakanl/Calypso-Gallery/releases/tag/1.0.0
[1.0.1]: https://github.com/akbudakanl/Calypso-Gallery/compare/1.0.0...1.0.1
[1.0.2]: https://github.com/akbudakanl/Calypso-Gallery/compare/1.0.1...1.0.2
[1.0.3]: https://github.com/akbudakanl/Calypso-Gallery/compare/1.0.2...1.0.3
[1.1.0]: https://github.com/akbudakanl/Calypso-Gallery/compare/1.0.4...1.1.0
[1.1.1]: https://github.com/akbudakanl/Calypso-Gallery/compare/1.1.0...1.1.1

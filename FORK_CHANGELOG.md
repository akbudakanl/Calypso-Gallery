# Changelog

User-facing release notes for Calypso Gallery. The original Fossify Gallery release history is kept in [CHANGELOG.md](CHANGELOG.md).

## [Unreleased]

### Fixed

- Video playback no longer stops when swiping left or right without actually changing to the neighbouring item; the current video now pauses only once the page really changes.
- Forward/backward skips, seek bar and horizontal gesture seeks are frame-accurate again: a 10-second skip advances 10 seconds instead of snapping to a nearby keyframe.

## [1.0.0] - 2026-11-22

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

### Changed and redesigned

- **App design revised** — color palette, icon set, top and bottom bars, selection action bar, welcome cards, vault lock screen and overflow menus were redesigned around one visual language.
- **Cryptography rebuilt** — asymmetric RSA was removed and the vault container format reduced to a single version. Key derivation moved first to BouncyCastle, then to native Rust/Argon2id via JNI; Poly1305 verification and Tink streaming AEAD were adopted. The password strength estimator runs entirely on-device.
- **Storage layout** — vault data moved to the `Secrets` folder, favorites and virtual-album backups into app storage; an external vault location and phone synchronization were added.
- **Distribution and identity** — the application ID changed to `com.calypso.gallery`; the project name and icon were rebranded and GPLv3 / third-party license screens were added.

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

- The interface is translated into up to 34 locales; blind translation audits and one-to-one comparison reports are kept in `translation-blind-tests`.
- Unit tests cover crypto vectors, vault format, file identity, conflict resolution and UI behavior.
- Architecture decision records live in `docs/adr`; security audit and design documents are available in multiple languages.

[Unreleased]: https://github.com/OWNER/REPO/compare/1.0.0...HEAD
[1.0.0]: https://github.com/OWNER/REPO/releases/tag/1.0.0

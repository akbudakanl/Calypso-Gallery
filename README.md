# Calypso Gallery

Calypso Gallery is a security-hardened, independently maintained personal fork of [Fossify Gallery](https://github.com/FossifyOrg/Gallery). It was forked from upstream commit [`2688360242468f1d591eddf23740c879616bb828`](https://github.com/FossifyOrg/Gallery/commit/2688360242468f1d591eddf23740c879616bb828) (v1.14.2) on **07-11-2026**. Thanks to the original authors and contributors of Fossify Gallery. It is an unofficial project and is not affiliated with, endorsed by, or supported by the Fossify organization.

Unleash memories, not personal data. Calypso Gallery is a powerful and private photo and video app with no ads, no Internet permission, and no unnecessary permissions.

> **Versioning and upstream policy:** This fork uses an independent Semantic Versioning scheme (`X.Y.Z-cipher`) and was originally forked from upstream Fossify Gallery `1.14.2`. Fork versions do not map one-to-one to upstream releases. Relevant upstream fixes and improvements are selectively backported and recorded in [FORK_CHANGELOG.md](FORK_CHANGELOG.md).

## Project status and attribution

- **Fork source and corresponding source:** <https://github.com/akbudakanl/Gallery-Cipher>
- **Fork releases:** <https://github.com/akbudakanl/Gallery-Cipher/releases>
- **Fork issue tracker:** <https://github.com/akbudakanl/Gallery-Cipher/issues>
- **Upstream project:** <https://github.com/FossifyOrg/Gallery>
- **Exact upstream base commit:** [`2688360242468f1d591eddf23740c879616bb828`](https://github.com/FossifyOrg/Gallery/commit/2688360242468f1d591eddf23740c879616bb828) (`1.14.2`)

This distribution is a modified version of Fossify Gallery. The upstream project, its original authors, and its copyright holders are retained as the origin of the inherited source code. Fork-specific changes are tracked in [FORK_CHANGELOG.md](FORK_CHANGELOG.md), while [CHANGELOG.md](CHANGELOG.md) is retained as the upstream-only release history.

## License and corresponding source

Calypso Gallery is licensed under the **GNU General Public License, version 3 (GPLv3)**. The complete license text is available in [LICENSE](LICENSE), and the same text is bundled in the app and shown in **About → Read the GNU GPL v3 license**.

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 3 of the License.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

The complete corresponding source for this fork is available in the [fork repository](https://github.com/akbudakanl/Gallery-Cipher). The repository contains the Android application, CLI tooling, build scripts, dependency declarations, test fixtures, and complete fork history needed to inspect and rebuild the project. Tagged fork releases also provide matching source archives on the [releases page](https://github.com/akbudakanl/Gallery-Cipher/releases); GitHub exposes each archive under the release's **Source code** links.

The inherited upstream source remains available from [FossifyOrg/Gallery](https://github.com/FossifyOrg/Gallery). Fork changes are identified in the fork history and [FORK_CHANGELOG.md](FORK_CHANGELOG.md).

## Third-party cryptographic components

The Android application bundles [Tink Java](https://github.com/tink-crypto/tink-java) 1.24.0, and the companion CLI declares the [Tink Python](https://github.com/tink-crypto/tink-py) package (`tink>=1.16.1,<2`). Both implementations are licensed under the Apache License, Version 2.0. The bundled Android attribution and complete Apache 2.0 license text are available in [app/src/main/res/raw/tink_license.txt](app/src/main/res/raw/tink_license.txt) and in the app under **About → Read the Tink Apache 2.0 license and attribution**. Gson attribution is also included under **About → Third-party licenses**.

## Android package identity and upgrades

The release application ID is `com.calypso.gallery`; the Kotlin namespace remains `org.fossify.gallery` to retain compatibility with the inherited Fossify code and resources. This differs from the earlier `org.fossify.gallery` application ID, so Android installs Calypso Gallery as a separate application and does not offer an in-place upgrade from that package. Restoring the old ID alone would not guarantee an in-place update: Android also requires signing-key continuity with the installed build.

Existing user-created media and vault containers in ordinary shared storage remain accessible subject to Android permissions, but package-scoped locations such as `Android/data/org.fossify.gallery` remain owned by the old package. Android also does not transfer app-private data between package identities. Preferences, the Room database, saved app state, vault credentials, and Android Keystore-bound keys are package-scoped and are not migrated automatically. Users moving from `org.fossify.gallery` must recreate or separately migrate those items. Debug builds use `com.calypso.gallery.debug`.

## Features

**🖼️ PHOTO EDITING AT YOUR FINGERTIPS:**  
Enhance your photos with the built-in editor. Crop, resize, rotate, flip, draw, and apply filters without compromising your privacy.

**🌐 PRIVACY FIRST, ALWAYS:**  
The app does not request Internet access. Strip EXIF metadata such as GPS coordinates and camera details to keep your memories yours.

**🔐 HARDWARE-HARDENED CRYPTOGRAPHIC VAULT:**  
Go beyond simple folder hiding with an isolated, zero-network cryptographic vault (`Cipher_Secrets`). Media files are individually encrypted on disk using `AES-256-CTR` with `Poly1305-AES` integrity tags, metadata is protected by `AES-256-GCM`, and the master encryption key is derived using `Argon2id` and wrapped by Android Keystore (`StrongBox` / `TEE`). Pre-wipe SHA-256 verification, CSPRNG overwriting, `FLAG_SECURE` screen protection, and automatic RAM zeroization reduce the risk of exposing decrypted keys and frames.

**🔗 ZERO-FOOTPRINT VIRTUAL ALBUMS:**  
Organize media into Virtual Albums without duplicating files or consuming extra storage space. Albums created from encrypted vault items become **Stealth Virtual Albums**, with HMAC-obfuscated filenames and `AES-256-GCM` encrypted titles and paths.

**🔄 RECOVER WITH EASE:**  
Recover deleted photos and videos with the built-in recycle bin.

**🎨 YOUR GALLERY, YOUR STYLE:**  
Customize themes, colors, and behavior to match your preferences.

**📷 UNIVERSAL FORMAT FREEDOM:**  
JPEG, JPEG XL, PNG, MP4, MKV, RAW, SVG, GIF, AVIF, videos, and more.

**✨ MATERIAL DESIGN WITH DYNAMIC THEMES:**  
Use the Material Design interface with dynamic color and theme support.

## Documentation

- [DOCS.md](DOCS.md) — architecture, cryptography specifications, vault design, and user documentation
- [FORK_CHANGELOG.md](FORK_CHANGELOG.md) — fork-only change and modification record
- [CHANGELOG.md](CHANGELOG.md) — upstream-only Fossify Gallery release history
- [tools/fossify-cipher-cli/README.md](tools/fossify-cipher-cli/README.md) — command-line vault tooling

### Fork release process

The fork release workflow reads, updates, and publishes release notes exclusively from `FORK_CHANGELOG.md`; it never consumes or rewrites the upstream-only `CHANGELOG.md`. Fork release tags use the independent `X.Y.Z-cipher` version form.

Each release creates a version-specific source tag and GitHub release with the complete fork release section and a corresponding source archive. Setting `RELEASE_TARGET=gplay` additionally deploys to Google Play after the GitHub source release succeeds; otherwise the release is published to GitHub only. Fastlane changelog entries are deterministic summaries capped at Play Console's 500-character limit and link to both the complete release notes and the version-specific source archive.

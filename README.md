<p align="center">
  <img src="docs/store-screenshots/out/github-banner.png" alt="Calypso Gallery — offline-first photo management with a Tink-encrypted vault and zero-footprint virtual albums" width="100%" />
</p>

# Calypso Gallery

Calypso Gallery is a security-hardened, independently maintained personal fork of [Fossify Gallery](https://github.com/FossifyOrg/Gallery). It was forked from upstream commit [`2688360242468f1d591eddf23740c879616bb828`](https://github.com/FossifyOrg/Gallery/commit/2688360242468f1d591eddf23740c879616bb828) (v1.14.2) on **07-11-2026**. Thanks to the original authors and contributors of Fossify Gallery. It is an unofficial project and is not affiliated with, endorsed by, or supported by the Fossify organization.

Unleash memories, not personal data. Calypso Gallery is a powerful and private photo and video app with no ads, no Internet permission, and no unnecessary permissions.

> **Versioning and upstream policy:** Calypso Gallery `1.0.0` is the first release. It was originally forked from upstream Fossify Gallery `1.14.2`; this release does not map one-to-one to any upstream version. Relevant upstream fixes and improvements are selectively backported and recorded in [FORK_CHANGELOG.md](FORK_CHANGELOG.md).

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

The Android application bundles [Tink Java](https://github.com/tink-crypto/tink-java) 1.24.0 (Apache License, Version 2.0) for streaming AEAD and HKDF, and native Rust crates compiled into `libcalypso_argon2.so` via JNI ([RustCrypto `argon2`](https://github.com/RustCrypto/password-hashes) 0.5.3, [`zeroize`](https://github.com/RustCrypto/utils) 1.8.1, and [`jni`](https://github.com/jni-rs/jni-rs) 0.21.1 — dual-licensed Apache-2.0 / MIT) for `Argon2id` key derivation ([Bouncy Castle](https://www.bouncycastle.org/) `bcprov-jdk18on` 1.78.1 is used solely as a `testImplementation` cross-verification dependency and is not packaged in the APK). The companion CLI declares the [Tink Python](https://github.com/tink-crypto/tink-py) (`tink>=1.16.1,<2`, Apache 2.0), [`cryptography`](https://github.com/pyca/cryptography) (Apache 2.0 / BSD-3-Clause), [`argon2-cffi`](https://github.com/hynek/argon2-cffi) (MIT), and [`Pillow`](https://github.com/python-pillow/Pillow) (MIT-CMU) packages in [tools/calypso-cli/requirements.txt](tools/calypso-cli/requirements.txt). In-app third-party attributions and licenses (including Tink, RustCrypto Argon2 / zeroize / jni-rs, Gson, image decoders, and Material Design Icons) are shown under **About → Third-party licenses**; the full raw Tink attribution and Apache 2.0 license text also remains in [app/src/main/res/raw/tink_license.txt](app/src/main/res/raw/tink_license.txt).

## Android package identity

The release application ID is `com.calypso.gallery`; the Kotlin namespace remains `org.fossify.gallery` to retain compatibility with the inherited Fossify code and resources. Debug builds use `com.calypso.gallery.debug`.

## Features

**🖼️ PHOTO EDITING AT YOUR FINGERTIPS:**  
Enhance your photos with the built-in editor. Crop, resize, rotate, flip, draw, and apply filters without compromising your privacy.

**🌐 PRIVACY FIRST, ALWAYS:**  
The app is offline-only and does not request Internet access. Strip EXIF metadata such as GPS coordinates and camera details to keep your memories yours.

**🔐 TINK-BASED ENCRYPTED VAULT:**  
Go beyond simple folder hiding with an isolated, offline-only cryptographic vault stored in the `Secrets` folder. The canonical vault container format uses Tink `AES256_GCM_HKDF` stream encryption, `Argon2id` key derivation, randomly generated DEKs, a KEK key ring, and `CalypsoGallery` domain separation. Full bit-by-bit verification, `FLAG_SECURE` screen protection, and automatic RAM zeroization reduce the risk of exposing decrypted keys and frames.

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
- [tools/calypso-cli/README.md](tools/calypso-cli/README.md) — command-line vault tooling

### Fork release process

The release workflow reads, updates, and publishes release notes exclusively from `FORK_CHANGELOG.md`; it never consumes or rewrites the upstream-only `CHANGELOG.md`. This release is tagged `1.0.0`.

Each release creates a version-specific source tag and GitHub release with the complete fork release section and a corresponding source archive. Setting `RELEASE_TARGET=gplay` additionally deploys to Google Play after the GitHub source release succeeds; otherwise the release is published to GitHub only. Fastlane changelog entries are deterministic summaries capped at Play Console's 500-character limit and link to both the complete release notes and the version-specific source archive.

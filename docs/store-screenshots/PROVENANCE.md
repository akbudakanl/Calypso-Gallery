# Store & Promotional Asset Provenance

This document records the origin, generation parameters, and usage scope of the reference images used to produce promotional screenshots and banners for **Calypso Gallery**.

> **APK Scope:** None of the files listed below are bundled into the Android APK or distributed as runtime application resources. They exist solely for repository documentation and app store listings (`docs/store-screenshots/` and `fastlane/metadata/android/en-US/images/phoneScreenshots/`).

## Reference Photos (`docs/store-screenshots/photos/`)

All six reference photographs were generated from scratch for this repository using the built-in image generator in Google Antigravity, with no third-party stock photography, scraped images, or external photographer assets.

| File | Origin | Tool / Model | Prompt | Date (UTC) | Third-Party Attribution / Restrictions |
|---|---|---|---|---|---|
| `photos/photo-1.jpg` (Architecture, `1024×1024`) | AI-generated for project | Google Antigravity (built-in image generator) | Minimalist brutalist concrete museum interior with a sculptural curved wall and dramatic diagonal sunlight casting crisp geometric shadows. 35mm camera look, refined warm-neutral monochrome and stone tones, subtle natural film grain, ultra-clean editorial composition, no text, no watermarks, no borders. | 2026-11-19 | None |
| `photos/photo-2.jpg` (Nordic Coast, `1024×1024`) | AI-generated for project | Google Antigravity (built-in image generator) | Dramatic misty Nordic coastal basalt cliffs rising from a calm dark slate-blue ocean under soft overcast twilight, delicate sea fog drifting along the cliffside. Medium format film look, moody refined muted tones, clean editorial style, no text, no watermarks, no borders. | 2026-11-19 | None |
| `photos/photo-3.jpg` (Golden Dunes, `1024×1024`) | AI-generated for project | Google Antigravity (built-in image generator) | Sweeping minimalist desert sand dunes at golden hour, one side glowing warm amber-gold and the other side falling into deep velvety obsidian shadow along a razor-sharp curving S-ridge. 35mm film look, high-contrast editorial fine art, no text, no watermarks, no borders. | 2026-11-19 | None |
| `photos/photo-4.jpg` (Botanical, `1024×1024`) | AI-generated for project | Google Antigravity (built-in image generator) | Deep emerald-green forest fern fronds glistening with delicate morning dew droplets against a soft dark shadowy woodland backdrop. Moody natural light, crisp macro focus, serene editorial aesthetic, no text, no watermarks, no borders. | 2026-11-19 | None |
| `photos/photo-5.jpg` (Night Street, `1024×1024`) | AI-generated for project | Google Antigravity (built-in image generator) | Quiet rain-slicked alley in Kyoto at dusk with soft warm amber lantern reflections shimmering on dark wet cobblestones, subtle atmospheric mist, shallow depth of field, restrained editorial color grading, no readable signage text, no watermarks, no borders. | 2026-11-19 | None |
| `photos/photo-6.jpg` (Alpine Peak, `896×1200`) | AI-generated for project | Google Antigravity (built-in image generator) | Majestic snow-dusted mountain peak catching delicate warm alpenglow light against a deep charcoal-indigo twilight sky with wispy clouds. Minimalist framing, editorial travel photography aesthetic, no text, no watermarks, no borders. | 2026-11-19 | None |

### File Checksums (`SHA-256`)

| File | Size (bytes) | SHA-256 |
|---|---:|---|
| `photos/photo-1.jpg` | 708,967 | `c49a6dbd2ce472d39c9baa8619d3a9f1042bb18f34ef862a1a80bf15ec7ce360` |
| `photos/photo-2.jpg` | 790,411 | `38060edb97a88fa904b456a80cf664742640e26772acd186a9d46fe1e18caf8a` |
| `photos/photo-3.jpg` | 714,592 | `da1da88bb2e0710b8090206dd90f4b9a828fabeb8f72de5987a7f06c609859bf` |
| `photos/photo-4.jpg` | 756,352 | `652692e27604251b1367763931e2e235f93b366b4691fac642070d44ce177605` |
| `photos/photo-5.jpg` | 873,107 | `7624c7e4abf519c5945d701c9a2e83df95a6fc0fca367149f195ec6128dd4812` |
| `photos/photo-6.jpg` | 704,224 | `8a6d515a97890f15e27c79e5ecfd16412ec08de31d3cbf9b3151414e89a3d541` |

## Clearance & Content Verification

- **No third-party works:** None of the images were downloaded from stock libraries, social media, or external photographers.
- **No restricted subjects:** Prompts and generated outputs contain no identifiable real individuals, brand logos, trademarks, copyrighted characters, or named artist styles.
- **Reverse image search:** Generated outputs were checked via Google reverse image search by the project maintainer with no matching third-party photographs found.
- **Usage terms:** AI-generated reference images are subject to the generating platform's terms of service and are included strictly as replaceable promotional placeholders.

## Derived Promotional Exports

The following composite store graphics and banners are rendered deterministically from `index.html` and `photos/photo-1.jpg`–`photos/photo-6.jpg` via `export-pngs.ps1`:

- `docs/store-screenshots/out/01-gallery.png` → `fastlane/metadata/android/en-US/images/phoneScreenshots/1.png`
- `docs/store-screenshots/out/02-vault.png` → `fastlane/metadata/android/en-US/images/phoneScreenshots/2.png`
- `docs/store-screenshots/out/03-virtual-albums.png` → `fastlane/metadata/android/en-US/images/phoneScreenshots/3.png`
- `docs/store-screenshots/out/04-viewer.png` → `fastlane/metadata/android/en-US/images/phoneScreenshots/4.png`
- `docs/store-screenshots/out/05-appearance.png` → `fastlane/metadata/android/en-US/images/phoneScreenshots/5.png`
- `docs/store-screenshots/out/06-offline-privacy.png` → `fastlane/metadata/android/en-US/images/phoneScreenshots/6.png`
- `docs/store-screenshots/out/github-banner.png` (`1600×900`)

## Replacing Reference Photos

To swap in original photography or explicitly licensed (`CC0` / Public Domain) images:

1. Replace `photos/photo-1.jpg` through `photos/photo-6.jpg` using the same filenames.
2. Run `powershell -ExecutionPolicy Bypass -File docs/store-screenshots/export-pngs.ps1` to regenerate all PNG exports and sync `fastlane/metadata/`.
3. Update the table and SHA-256 checksums in this file accordingly.

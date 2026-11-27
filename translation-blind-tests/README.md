# Translation Blind Tests

This directory stores translation audit and blind-review comparison reports for **Calypso Gallery**. Each target language has its own dedicated subfolder so that string audits, terminology notes, and 1-to-1 English comparison tables remain cleanly organized and easy to maintain.

> [!NOTE]
> **Notes — Translation Requests & Feedback:**  
> For strings inherited from Fossify Gallery, please contribute fixes through [Fossify on Hosted Weblate](https://hosted.weblate.org/projects/fossify/gallery) or report them on the [upstream issue tracker](https://github.com/FossifyOrg/Gallery/issues); those changes flow back into this fork with upstream updates. Use this project's [GitHub Issues](https://github.com/akbudakanl/Calypso-Gallery/issues) only for strings unique to Calypso Gallery, or to request a full audit/support for a new language.

## Directory Structure

```text
translation-blind-tests/
├── README.md
├── az/
│   └── az_translation_comparison.md
├── de/
│   └── de_translation_comparison.md
├── es/
│   ├── b+es+419_translation_comparison.md
│   ├── es_translation_comparison.md
│   └── es-rUS_translation_comparison.md
├── ja/
│   └── ja_translation_comparison.md
├── no/
│   ├── nb-rNO_translation_comparison.md
│   └── nn_translation_comparison.md
└── zh-rTW/
    └── zh-rTW_translation_comparison.md
```

### Language Subfolders

| Subfolder | Android Resource Dir | Language | Audit File |
|---|---|---|---|
| `az/` | `app/src/main/res/values-az/` | Azerbaijani | [`az_translation_comparison.md`](az/az_translation_comparison.md) |
| `de/` | `app/src/main/res/values-de/` | German | [`de_translation_comparison.md`](de/de_translation_comparison.md) |
| `es/` | `app/src/main/res/values-es/` | Spanish (Standard) | [`es_translation_comparison.md`](es/es_translation_comparison.md) |
| `es/` | `app/src/main/res/values-b+es+419/` | Spanish (Latin America) | [`b+es+419_translation_comparison.md`](es/b+es+419_translation_comparison.md) |
| `es/` | `app/src/main/res/values-es-rUS/` | Spanish (United States) | [`es-rUS_translation_comparison.md`](es/es-rUS_translation_comparison.md) |
| `ja/` | `app/src/main/res/values-ja/` | Japanese | [`ja_translation_comparison.md`](ja/ja_translation_comparison.md) |
| `no/` | `app/src/main/res/values-nb-rNO/` | Norwegian Bokmål | [`nb-rNO_translation_comparison.md`](no/nb-rNO_translation_comparison.md) |
| `no/` | `app/src/main/res/values-nn/` | Norwegian Nynorsk | [`nn_translation_comparison.md`](no/nn_translation_comparison.md) |
| `zh-rTW/` | `app/src/main/res/values-zh-rTW/` | Traditional Chinese (Taiwan) | [`zh-rTW_translation_comparison.md`](zh-rTW/zh-rTW_translation_comparison.md) |

## Purpose & Methodology

1. **Dual-Source Completeness Audit (App + `fossify-commons`):** Every target `values-<lang>/strings.xml` file must be checked against **both** string sources:
   - **App-owned resources:** `app/src/main/res/values/strings.xml` (`785 <string>` (`756` translatable) and `3 <plurals>` entries = `788` resource entries, `759` translatable). Earlier fork keys — `show_bottom_action_names`, `show_albums`, the four `vault_biometric_offer_*` strings, the six `password_strength_*` strings, `vault_unlock_time_unit`, `search_in_storage`, `password_must_be_at_least_4_characters`, `biometric_disable_confirmation` and the `identical_files_skipped` plural — are documented in the "New fork strings" table at the top of each comparison report, while the most recent additions (including the minimalist Settings hub, Appearance preset themes, contrast indicator, saved theme storage backup, KDF calibration, open-source library license titles, the vault setup tutorial, the PIN-dot preview notice, and the dynamic filename toggle, album search hint, extra fullscreen viewer actions and the Secure sharing metadata-sanitization strings) are recorded in the `(this revision)` sections at the end of each report.
   - **Inherited library resources (`org.fossify:commons`):** Strings used in Settings, Options menus, About (`AboutActivity`), contextual action bars (CAB), and shared dialogs (such as `change_date_and_time_format`, `password_protect_file_deletion`, `keep_last_modified`, `clear_cache`, `export_settings`, `import_settings`, `temporarily_show_excluded`, `change_view_type`, `contributors`, `known_issues`, `donate_to_fossify`, `privacy_policy`, `create_shortcut`, `print`, and `search`) live in the `org.fossify:commons` AAR dependency (`res/values/values.xml` and `res/values-<lang>/values-<lang>.xml`) rather than `app/src/main/res/values/strings.xml`. Because several locales in `org.fossify:commons` are incomplete, any missing `fossify-commons` keys should be overridden locally in `app/src/main/res/values-<lang>/strings.xml`.
2. **Blind Translation & Consistency Review:** Missing or inaccurate strings are translated independently from the English source while matching existing Fossify Gallery terminology, UI tone, placeholders (`%s`, `%d`), HTML/escape sequences (`&gt;`, `\'`, `\"`, `\n`), and plural rules (CLDR categories per language).
3. **1-to-1 Comparison Report:** Each `<lang>_translation_comparison.md` file documents:
   - Scope and key terminology glossary.
   - Section 1: Summary of newly added or revised translations with contextual notes.
   - Section 2: Full 1-to-1 comparison table pairing every English source entry (plus any overridden `fossify-commons` keys) with its localized counterpart.

## AI-Generated Baselines and Community Review

The translations documented in this folder are produced initially with AI assistance. They are meant as a starting point for review rather than as final wording: native speakers are invited to check them against the English source and flag anything that reads unnaturally, uses the wrong register, or is simply inaccurate.

To make that review practical, every `<lang>_translation_comparison.md` report places the English source string and its localized counterpart side by side in a single table row, along with a note on where the string appears in the app. Reviewers can therefore evaluate the complete set of user-facing text at a glance, without cross-referencing XML resource files by hand, and can propose clearer or more idiomatic alternatives with minimal effort.

**Where to contribute.** The preferred route is to improve translations in **Fossify Gallery upstream**, not in this fork. Fossify manages translations in [Hosted Weblate](https://hosted.weblate.org/projects/fossify/gallery), and Weblate commits accepted translations back to the upstream repository. This fork follows upstream releases and imports their translations, so a fix made upstream benefits both projects. Please report problems with shared strings through the [upstream issue tracker](https://github.com/FossifyOrg/Gallery/issues) or submit them directly on Weblate.

**How to tell where a string belongs.** You do not have to guess from the wording: search the string key in Fossify's [`values/strings.xml`](https://github.com/FossifyOrg/Gallery/blob/main/app/src/main/res/values/strings.xml) or in the Weblate component. If the key exists there, the string is upstream and the fix belongs on Weblate; if it does not, it is Calypso-specific. As a quick rule of thumb, anything tied to the encrypted vault (`Secrets`), passwords, PINs, KDF, biometrics, virtual albums, conflict-handling settings, or Calypso branding is fork-only, while ordinary gallery features (folders, sorting, slideshow, wallpaper, editor, recycle bin) come from upstream. If you are still unsure, report the string here and it will be redirected upstream where appropriate.

Contributions here are still welcome, but only for strings that exist solely in Calypso Gallery—such as vault, encryption, and virtual-album wording—so that all shared strings remain in sync with upstream.

## Notes on Shared & Inherited Resources

- **Why `fossify-commons` must be checked alongside `values/strings.xml`:** Checking only `app/src/main/res/values/strings.xml` can leave visible UI labels in English if `org.fossify:commons`'s own `res/values-<lang>/values-<lang>.xml` is missing those keys. Adding local `<string>` overrides in `app/src/main/res/values-<lang>/strings.xml` takes precedence during Android resource merging without duplicating the entire `fossify-commons` resource table into `app/src/main/res/values/strings.xml`.
- **Plural forms (`<plurals>`):** English defines `one` and `other` quantities for `restore_confirmation`, `failed_to_resize_images` and `identical_files_skipped`. Spanish locales additionally define `many`; languages with different CLDR plural rules (for example, Japanese and Traditional Chinese using only `other`) map accordingly in XML while retaining full row-by-row parity in the comparison table.

## Terminology Note — `alphanumeric_password`

The vault credential tab uses **Alphanumeric** (not "Password" / "Şifre"): it denotes a full alphanumeric secret, as opposed to a PIN or the generic password concept used elsewhere in the app. Current translations:

| Locale | Label |
|---|---|
| English (`values/`) | Alphanumeric |
| Türkçe (`values-tr/`) | Alfanumerik |
| Azerbaijani (`values-az/`) | Alfanumerik |
| Deutsch (`values-de/`) | Alphanumerisch |
| Español — Standard / Latin America / US (`values-es/`, `values-b+es+419/`, `values-es-rUS/`) | Alfanumérico |
| 日本語 (`values-ja/`) | 英数字 |
| Norsk bokmål (`values-nb-rNO/`) | Alfanumerisk |
| Norsk nynorsk (`values-nn/`) | Alfanumerisk |
| 繁體中文 (`values-zh-rTW/`) | 英數字 |

Related password-prompt strings (`enter_password`, `repeat_password`, `wrong_password`, `enter_new_password`, `repeat_new_password`, `change_encrypted_vault_password`, `faq_2_title`, `existing_vault_detected_at_location`) consistently use the word for **password** — Türkçe `parola`, Azerbaijani `parol` — and reserve `şifre` / `şifrə` strictly for encryption ("encrypt", "encrypted vault"). The other audited locales already follow the same distinction (`Passwort`, `contraseña`, `パスワード`, `passord`, `密碼`).

### Unlock-time security levels (this revision)

The four fork-only `brute_force_level_*` keys (`fast`, `balanced`, `high`, `maximum`) label the themed unlock-time dial in both Settings and first-time vault setup, and the matching `brute_force_level_*_desc` keys explain each range under the dial. For 0–2.5 s the description is `Hızlı kasa açılışı` / `Fast vault opening`; the older `Düşük KDF maliyeti` wording is no longer used. These are Calypso-specific (KDF/unlock time), so translations belong in this fork, not upstream Weblate. All nine audited locales are documented in `(this revision)` sections at the end of their comparison reports.

## Adding a New Language Audit

1. Create a subfolder named after the Android locale code (e.g., `fr/`, `es/`, `zh-rCN/`).
2. Audit `app/src/main/res/values-<lang>/strings.xml` against **both** `app/src/main/res/values/strings.xml` **and** `org.fossify:commons`'s `res/values/values.xml` / `res/values-<lang>/values-<lang>.xml`, and add any missing entries or local `fossify-commons` overrides to `app/src/main/res/values-<lang>/strings.xml`.
3. Add `<lang>_translation_comparison.md` inside `translation-blind-tests/<lang>/` with English descriptions, a summary of added/updated keys, and the full English-vs-target comparison table.
4. Register the new subfolder in the table above.

## CLI Bridge Import Strings (this revision)

The encrypted-media import flow now uses a per-vault **bridge folder** created by the app under `Calypso Gallery/CLI`. Three keys were updated or added in all audited locales (English/Türkçe included):

| Key | Change |
|---|---|
| `sync_vault_from_external` | Settings row label ("Import CLI / external encrypted media"); unchanged in English, reworded in the nine audit locales. |
| `sync_vault_select_source_prompt` | Rewritten for the bridge workflow (prepare on phone → encrypt on computer → merge into active vault). The old "unified Secrets folder / export `.vault` header" wording is gone, and the header is now named `vault`. |
| `vault_sync_cli_busy` | **New key** in every locale, shown when the selected bridge still contains the CLI busy marker. |

Each `<lang>_translation_comparison.md` has a `## CLI bridge import strings (this revision)` section at the end with the English source and the localized value side by side.

### Bridge preparation, deduplication and recovery strings (this revision)

Fifteen more fork-only keys were added in all 11 locales (`cli_bridge_prepare_*`, `cli_bridge_conflict_*`, `cli_bridge_selection_*`, `vault_sync_stale_marker_*`), each with a `## CLI bridge UX strings (this revision)` table at the end of the audit reports.

Bridge deduplication follows the existing **`conflictHandlingMode`** setting rather than a hard-coded warning:

| Mode | Bridge behaviour |
|---|---|
| Standard dialog (default) | Ask: Skip / Use existing bridge / Create new bridge |
| Smart dialog | Silently reuse the bridge when the KEK fingerprint matches; ask only when it differs |
| Automatic (experimental) | Silently reuse when it matches; create a new bridge automatically when it does not |

The **date comparison** setting (`dateTieBreakMode`) does **not** decide which bridge folder is used — that decision is based on the marker fingerprint. It **does** apply when files from the bridge are imported into the vault, because each `.bin` header carries the original `dateTaken`/`lastModified` values. The import path must therefore honour `DateTieBreakPolicy` (currently it only copies the staged file's filesystem mtime in `CryptoManagerExportSync.kt`). The existing normalization `AUTO + ASK → KEEP_TARGET` in `Config.kt` is unchanged.

### Appearance preset theme strings (this revision)

Two fork-only Appearance preset theme keys were added next to `theme_deep_forest` across all 11 audited locales (`values/`, `values-tr/`, and the 9 blind-test locales):

| Key (`name`) | English | Turkish | Theme |
|---|---|---|---|
| `theme_velvet_violet` | Velvet Violet | Kadife Mor | Dark purple preset (`#0E0A18` bg, `#A78BFA` primary) |
| `theme_soft_coral` | Soft Coral | Yumuşak Mercan | Light coral preset (`#FFF7F5` bg, `#C44047` primary) |

Each `<lang>_translation_comparison.md` ends with an `## Appearance preset theme strings (this revision)` table pairing the English source and localized counterpart.

### Album decryption destination strings (this revision)

Six fork-only keys (`5` new + `1` updated) document the album-decryption destination flow across all 11 audited locales (`values/`, `values-tr/`, and the 9 blind-test locales):

| Key (`name`) | Change | English (`values/strings.xml`) | Turkish (`values-tr/strings.xml`) | Purpose |
|---|---|---|---|---|
| `confirm_decrypt_album` | **New** | `Decrypt \"%s\"?` | `\"%s\" albümünün şifresi çözülsün mü?` | Single encrypted album decryption confirmation (`%s` = album name) |
| `confirm_decrypt_albums` | **New** | `Decrypt %d selected albums?` | `Seçilen %d albümün şifresi çözülsün mü?` | Multi-album decryption confirmation (`%d` = count) |
| `decrypt_destination_label` | **New** | `Destination:` | `Hedef konum:` | Label above resolved destination path(s) in the decrypt dialog |
| `decrypt_destination_to_be_selected` | **New** | `Location to be selected` | `Konum seçilecek` | Fallback text when `.origin` is missing or external storage is unmounted |
| `decrypt_to_other_location` | **New** | `Other location` | `Başka yere çöz` | Concise neutral AlertDialog button to pick a custom destination |
| `decrypt_info_message` | **Updated** | Restores to original `.origin` location or opens destination picker | Orijinal konuma geri çıkarma / hedef seçici açıklaması | Keeps `formatted="false"`; separates album `.origin` restore from individual file decryption |

Each `<lang>_translation_comparison.md` ends with an `## Album decryption destination strings (this revision)` table pairing the English source and localized counterpart.

### File operation progress strings (this revision)

Four fork-only progress dialog titles were added across all 11 audited locales (`values/`, `values-tr/`, and the 9 blind-test locales):

| Key (`name`) | English (`values/strings.xml`) | Turkish (`values-tr/strings.xml`) | Purpose |
|---|---|---|---|
| `moving_to_recycle_bin_progress` | `Moving to recycle bin…` | `Geri dönüşüm kutusuna taşınıyor…` | Progress dialog title when moving media or albums to the recycle bin |
| `emptying_recycle_bin_progress` | `Emptying recycle bin…` | `Geri dönüşüm kutusu boşaltılıyor…` | Progress dialog title while emptying the normal or encrypted vault recycle bin |
| `restoring_files_progress` | `Restoring files…` | `Dosyalar geri yükleniyor…` | Progress dialog title while restoring files from the normal or vault recycle bin |
| `updating_virtual_album_progress` | `Updating virtual album…` | `Sanal albüm güncelleniyor…` | Progress dialog title when adding to or removing items from a virtual album |

Each `<lang>_translation_comparison.md` ends with a `## File operation progress strings (this revision)` table pairing the English source and localized counterpart.

### Media security & SVG toggle strings (this revision)

Four fork-only keys were added across all 11 audited locales (`values/`, `values-tr/`, and the 9 blind-test locales) for the Media security card and SVG viewing confirmation dialog:

| Key (`name`) | English (`values/strings.xml`) | Turkish (`values-tr/strings.xml`) | Purpose |
|---|---|---|---|
| `media_security` | `Media security` | `Medya güvenliği` | Eyebrow heading for the Media security card under Settings → Media viewer |
| `show_svg_files` | `Show SVG files` | `SVG dosyalarını göster` | Switch row label (default OFF) and confirmation dialog title |
| `show_svg_files_warning` | `SVG files are rendered in an isolated process using an older third-party library that no longer receives updates. Only enable this if you need to view SVG files.` | `SVG dosyaları, artık güncelleme almayan eski bir üçüncü parti kütüphane ile izole bir süreçte işlenir. Yalnızca SVG dosyalarını görüntülemeniz gerekiyorsa etkinleştirin.` | Confirmation dialog body shown before enabling `show_svg_files` |
| `enable_svg_files` | `Enable` | `Etkinleştir` | Positive button in the confirmation dialog |

Each `<lang>_translation_comparison.md` ends with a `## Media security & SVG toggle strings (this revision)` table pairing the English source and localized counterpart.

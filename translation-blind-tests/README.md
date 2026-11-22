# Translation Blind Tests

This directory stores translation audit and blind-review comparison reports for **Calypso Gallery**. Each target language has its own dedicated subfolder so that string audits, terminology notes, and 1-to-1 English comparison tables remain cleanly organized and easy to maintain.

> [!NOTE]
> **Notes — Translation Requests & Feedback:**  
> For strings inherited from Fossify Gallery, please contribute fixes through [Fossify on Hosted Weblate](https://hosted.weblate.org/projects/fossify/gallery) or report them on the [upstream issue tracker](https://github.com/FossifyOrg/Gallery/issues); those changes flow back into this fork with upstream updates. Use this project's [GitHub Issues](https://github.com/akbudakanl/Gallery-Cipher/issues) only for strings unique to Calypso Gallery, or to request a full audit/support for a new language.

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
   - **App-owned resources:** `app/src/main/res/values/strings.xml` (`706 <string>` (`690` translatable) and `3 <plurals>` entries = `709` resource entries, `693` translatable). Earlier fork keys — `show_bottom_action_names`, `show_albums`, the four `vault_biometric_offer_*` strings, the six `password_strength_*` strings, `vault_unlock_time_unit`, `search_in_storage`, `password_must_be_at_least_4_characters`, `biometric_disable_confirmation` and the `identical_files_skipped` plural — are documented in the "New fork strings" table at the top of each comparison report, while the most recent additions (including the minimalist Settings hub, Appearance preset themes, contrast indicator, saved theme storage backup, KDF calibration, open-source library license titles, the vault setup tutorial, the PIN-dot preview notice, and the dynamic filename toggle, album search hint and extra fullscreen viewer actions) are recorded in the `(this revision)` sections at the end of each report.
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

## Adding a New Language Audit

1. Create a subfolder named after the Android locale code (e.g., `fr/`, `es/`, `zh-rCN/`).
2. Audit `app/src/main/res/values-<lang>/strings.xml` against **both** `app/src/main/res/values/strings.xml` **and** `org.fossify:commons`'s `res/values/values.xml` / `res/values-<lang>/values-<lang>.xml`, and add any missing entries or local `fossify-commons` overrides to `app/src/main/res/values-<lang>/strings.xml`.
3. Add `<lang>_translation_comparison.md` inside `translation-blind-tests/<lang>/` with English descriptions, a summary of added/updated keys, and the full English-vs-target comparison table.
4. Register the new subfolder in the table above.

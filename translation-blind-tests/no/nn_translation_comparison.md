# Norwegian Nynorsk (values-nn) Translation Completion & English 1-to-1 Comparison

## New fork strings - bottom actions, view switch, biometrics and vault UI (16 keys)

Added to `values-nn/strings.xml`. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `show_bottom_action_names` | Show action names in the bottom action bar | Vis namn på handlingane i handlingslinja nedst | Fullscreen media setting, default ON |
| 2 | `show_albums` | Show albums | Vis album | Files-view overflow item that returns to the album view |
| 3 | `vault_biometric_offer_title` | Biometric access to the vault | Biometrisk tilgang til kvelvet | One-time offer after first vault creation |
| 4 | `vault_biometric_offer_message` | Open the vault with your fingerprint or face instead of a password. You don\'t need to enter your password again. | Opne kvelvet med fingeravtrykk eller andlet i staden for eit passord. Du treng ikkje skrive inn passordet på nytt. | Same |
| 5 | `vault_biometric_offer_accept` | Set up biometric access | Set opp biometrisk tilgang | Positive button |
| 6 | `vault_biometric_offer_decline` | Not now | Ikkje no | Negative button |
| 7 | `password_strength_bad` | Very weak | Svært svak | Vault password strength |
| 8 | `password_strength_poor` | Weak | Svak | Vault password strength |
| 9 | `password_strength_weak` | Fair | Middels | Vault password strength |
| 10 | `password_strength_good` | Strong | Sterk | Vault password strength |
| 11 | `password_strength_excellent` | Very strong | Svært sterk | Vault password strength |
| 12 | `password_strength_hint` | A longer password with mixed characters is harder to crack. | Eit langt passord med varierte teikn er vanskelegare å knekkje. | Shown before typing |
| 13 | `vault_unlock_time_unit` | s | s | Localized seconds unit |
| 14 | `search_in_storage` | Search in storage | Søk i lagring | Second option in unencrypted copy destination picker |
| 15 | `password_must_be_at_least_4_characters` | Password must be at least 4 characters long | Passordet må vere minst 4 teikn | Alphanumeric minimum length |
| 16 | `biometric_disable_confirmation` | Confirm your biometric identity to disable biometric vault access | Stadfest den biometriske identiteten din for å deaktivera biometrisk tilgang til kvelvet | Shown in biometric confirmation prompt |

## ADR 0003 — resumable file operations (7 new keys)

Added to `values-nn/strings.xml`. Placeholders (`%1$s`, `%2$s`, `%1$d`) are preserved exactly.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `space_unverified_warning` | Free space could not be verified (%2$s). About %1$s is required; the operation will be attempted and may stop if space runs out. | Kunne ikkje stadfeste ledig lagringsplass (%2$s). Omtrent %1$s trengst; operasjonen blir prøvd, men kan stoppe dersom plassen tek slutt. | SAF/OTG free-space warning; `%1$s` = required size, `%2$s` = reason |
| 2 | `resume_operations_title` | Interrupted operations | Avbrotne operasjonar | One-time process-death dialog title |
| 3 | `resume_operations_message` | %1$d operation(s) were interrupted. No data was lost — continue where they stopped? | %1$d operasjon(ar) vart avbrotne. Ingen data gjekk tapt – hald fram der dei stoppa? | `%1$d` = interrupted operation count |
| 4 | `resume_continue` | Continue | Hald fram | Positive dialog button |
| 5 | `resume_later` | Not now | Ikkje no | Negative dialog button |
| 6 | `resume_done` | Operations completed. | Operasjonane er fullførte. | Success toast after resume |
| 7 | `resume_failed` | Some operations could not be completed; source files were preserved. | Nokre operasjonar kunne ikkje fullførast; kjeldefilene vart behaldne. | Failure toast; no data loss |
| 8 | `space_unverified_title` | Free space unverified | Lagringsplass ikkje stadfesta | Explicit-consent dialog title for SAF/OTG free space |
| 9 | `space_continue_anyway` | Continue anyway | Hald fram likevel | Positive button: proceed without a verified space reading |

> **Scope:** `app/src/main/res/values-nn/strings.xml` was audited from scratch against `app/src/main/res/values/strings.xml`. Previously, `values-nn` contained only 23 `<string>` entries (and 0 `<plurals>`). Three inaccurate/typoed existing entries were fixed, and all 285 missing `<string>` entries plus 2 `<plurals>` entries (`restore_confirmation` and `failed_to_resize_images`) were translated into Norwegian Nynorsk, bringing `values-nn` to full parity (383 `<string>` + 2 `<plurals>` = 385 app resource entries; `search` and 18 previously overridden shared UI keys from `fossify-commons` plus 13 additional `fossify-commons` UI keys (`manage_excluded_folders`, `exclude`, `exclude_folder`, `lock_folder`, `change_cover_image`, `select_photo`, `use_default`, `column_count`, `open_the_recycle_bin`, `grid`, `list`, `resize`, `version_placeholder`) newly overridden because they are missing in `fossify-commons` `values-nn`; 374 total comparison rows with plural forms expanded). Nine keys that were missing from `values-nn/strings.xml` were translated and added (`number_of_items`, `random`, `shuffle`, `stop_showing_excluded`, `copy_to_clipboard`, `encrypt`, `decrypt`, `encrypt_album`, `decrypt_album`), and eleven existing entries were revised so that files and albums use the new \"encrypt / decrypt\" wording and so that the favorites import/export labels mention virtual albums as well. Subsequently, 22 additional keys were localized in `values-nn/strings.xml`, `hide_folder_description` was removed, and `default_hidden_album` was marked `translatable="false"` (`Default Secret Album`), bringing `values-nn/strings.xml` to 404 `<string>` + 2 `<plurals>` = 406 resource entries and 395 total comparison rows.
> **Key Terminology:** `Settings` → `Innstillingar` · `Default` → `Standard` / `Systemstandard` · `Folder` → `Mappe` · `Album` → `Album` · `Encrypted Vault` → `Kryptert kvelv` · `Hidden` → `Skjult` · `Excluded` → `Ekskludert` · `Password` → `Passord` · `Alphanumeric` → `Alfanumerisk` · `Encrypt / Decrypt` → `Kryptera / Dekryptera` · `Thumbnails` → `Minibilete` · `Recycle bin` → `Papirkorg` · `Clipboard` → `Utklippstavla`

## 1. Review of 23 Previously Existing Translations and Changes Made

| Key (`name`) | Old `values-nn` | New `values-nn` | Status / Notes |
|---|---|---|---|
| `app_launcher_name` | Galleri | Galleri | Kept |
| `edit` | Gjer om | Rediger | **Fixed** (*Gjer om* means undo/redo; standard Nynorsk for *Edit* is *Rediger*) |
| `open_camera` | Opna kameraet | Opna kameraet | Kept |
| `pin_folder` | Fest mappa | Fest mappa | Kept |
| `unpin_folder` | Løys mappa | Løys mappa | Kept |
| `pin_to_the_top` | Fest øvst | Fest øvst | Kept |
| `show_all` | Syn innhaldet i alle mapper | Syn innhaldet i alle mapper | Kept |
| `all_folders` | Alle mapper | Alle mapper | Kept |
| `folder_view` | Byt til mappesyning | Byt til mappesyning | Kept |
| `other_folder` | Anna mappe | Anna mappe | Kept |
| `show_on_map` | Syn på kartet | Syn på kartet | Kept |
| `unknown_location` | Ukjend plassering | Ukjend plassering | Kept |
| `volume` | Ljodstyrke | Ljodstyrke | Kept |
| `brightness` | Ljosstyrke | Ljosstyrke | Kept |
| `force_portrait` | Tvinga portrettmodus | Tvinga portrettmodus | Kept |
| `force_landscape` | Tvinga landskapsmodus | Tvinga landskapsmodus | Kept |
| `force_landscape_reverse` | Tvinga landskapsmodus (vrengd) | Tvinga landskapsmodus (omvend) | **Fixed** (*vrengd* means turned inside out; standard Nynorsk for *reverse* is *omvend*) |
| `fix_date_taken` | Retta dato på opptak | Retta dato på opptak | Kept |
| `fixing` | Rettar… | Rettar… | Kept |
| `language` | Språk | Språk | Kept |
| `search` | Søk | Søk | Kept (Used in ``fossify-commons`` UI) |
| `system_default` | Systemstandard | Systemstandard | Kept |
| `open_system_language_settings` | Opna språkiinnstillingane i systemet | Opna språkinnstillingane i systemet | **Fixed** (Removed double-``i`` typo in *språkiinnstillingane* → *språkinnstillingane*) |

> **Added Entries:** All remaining 285 `<string>` keys and 2 `<plurals>` blocks (4 CLDR `one`/`other` items) from `values/strings.xml` were translated following standard Norwegian Nynorsk orthography and consistent terminology with the existing entries (e.g., `syna`, `ljosstyrke`, `ljodstyrke`, `bilete`, `minibilete`). See Section 2 for the complete 1-to-1 English comparison.

### `fossify-commons` Inherited UI Overrides (31 Keys)

`org.fossify:commons:7.1.3` (`res/values-nn/values-nn.xml`) omits all 31 UI keys below (18 overridden in the previous audit and 13 added in this revision), so local overrides were added to `app/src/main/res/values-nn/strings.xml`:

| # | Key (`name`) | English (`fossify-commons`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Source / Notes |
|---:|---|---|---|---|
| 1 | `change_date_and_time_format` | Change date and time format | Endra format for dato og klokkeslett | Local override in `values-nn/strings.xml` (Settings) |
| 2 | `password_protect_file_deletion` | Password protect file deletion and moving | Passordvern for sletting og flytting av filer | Local override in `values-nn/strings.xml` (Settings) |
| 3 | `keep_last_modified` | Keep old last-modified value at file operations | Behald gammal sist-endra-verdi ved filoperasjonar | Local override in `values-nn/strings.xml` (Settings) |
| 4 | `clear_cache` | Clear cache | Tøm mellomlager | Local override in `values-nn/strings.xml` (Settings) |
| 5 | `export_settings` | Export settings | Eksporter innstillingar | Local override in `values-nn/strings.xml` (Settings) |
| 6 | `import_settings` | Import settings | Importer innstillingar | Local override in `values-nn/strings.xml` (Settings) |
| 7 | `temporarily_show_excluded` | Temporarily show excluded | Syn ekskluderte mellombels | Local override in `values-nn/strings.xml` (Options menu) |
| 8 | `change_view_type` | Change view type | Endra syningstype | Local override in `values-nn/strings.xml` (Options menu) |
| 9 | `contributors` | Contributors | Bidragsytarar | Local override in `values-nn/strings.xml` (About) |
| 10 | `known_issues` | Known issues | Kjende problem | Local override in `values-nn/strings.xml` (About) |
| 11 | `donate_to_fossify` | Donate to Fossify | Doner til Fossify | Local override in `values-nn/strings.xml` (About) |
| 12 | `privacy_policy` | Privacy policy | Personvernerklæring | Local override in `values-nn/strings.xml` (About) |
| 13 | `create_shortcut` | Create shortcut | Lag snarveg | Local override in `values-nn/strings.xml` (CAB / ViewPager menu) |
| 14 | `print` | Print | Skriv ut | Local override in `values-nn/strings.xml` (ViewPager menu) |
| 15 | `search_files` | Search files | Søk i filer | Local override in `values-nn/strings.xml` (Search bar hint) |
| 16 | `search_folders` | Search folders | Søk i mapper | Local override in `values-nn/strings.xml` (Search bar hint) |
| 17 | `skip_the_recycle_bin` | Skip the recycle bin, delete files directly | Hopp over papirkorga og slett filene direkte | Local override in `values-nn/strings.xml` (Delete confirmation dialog); key missing in `fossify-commons` `values-nn` |
| 18 | `skip_delete_confirmation` | Always skip delete confirmation dialog | Hopp alltid over dialogen for stadfesting av sletting | Local override in `values-nn/strings.xml` (Settings); key missing in `fossify-commons` `values-nn` |
| 19 | `manage_excluded_folders` | Manage excluded folders | Handter ekskluderte mapper | Local override in `values-nn/strings.xml` (Settings); key missing in `fossify-commons` `values-nn` |
| 20 | `exclude` | Exclude | Ekskluder | Local override in `values-nn/strings.xml` (Album selection / long-press menu); key missing in `fossify-commons` `values-nn` |
| 21 | `exclude_folder` | Exclude folder | Ekskluder mappe | Local override in `values-nn/strings.xml` (Options menu); key missing in `fossify-commons` `values-nn` |
| 22 | `lock_folder` | Lock folder | Lås mappe | Local override in `values-nn/strings.xml` (Album selection / long-press menu); key missing in `fossify-commons` `values-nn` |
| 23 | `change_cover_image` | Change cover image | Endra omslagsbilete | Local override in `values-nn/strings.xml` (Album selection / long-press menu); key missing in `fossify-commons` `values-nn` |
| 24 | `select_photo` | Select photo | Vel bilete | Local override in `values-nn/strings.xml` (Album selection, "Change cover image" dialog); key missing in `fossify-commons` `values-nn` |
| 25 | `use_default` | Use default | Bruk standard | Local override in `values-nn/strings.xml` (Album selection, "Change cover image" dialog); key missing in `fossify-commons` `values-nn` |
| 26 | `column_count` | Column count | Tal på kolonnar | Local override in `values-nn/strings.xml` (Main screen and in-album Options menu); key missing in `fossify-commons` `values-nn` |
| 27 | `open_the_recycle_bin` | Open the recycle bin | Opna papirkorga | Local override in `values-nn/strings.xml` (In-album Options menu); key missing in `fossify-commons` `values-nn` |
| 28 | `grid` | Grid | Rutenett | Local override in `values-nn/strings.xml` (In-album Options menu, view type); key missing in `fossify-commons` `values-nn` |
| 29 | `list` | List | Liste | Local override in `values-nn/strings.xml` (In-album Options menu, view type); key missing in `fossify-commons` `values-nn` |
| 30 | `resize` | Resize | Endra storleik | Local override in `values-nn/strings.xml` (Selection CAB menu); key missing in `fossify-commons` `values-nn` |
| 31 | `version_placeholder` | Version %s | Versjon %s | Local override in `values-nn/strings.xml` (About screen version text); key missing in `fossify-commons` `values-nn` |

### Newly Added Fork-Only Strings (66 Keys)

These 66 strings were introduced by this fork (Calypso Gallery) and do not exist in the original Fossify Gallery:

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | 1 | `video_double_tap_duration` | Double-tap seek duration |  |
| 2 | 2 | `video_seek_duration_seconds` | %d seconds |  |
| 3 | 3 | `create_virtual_album` | Create virtual album |  |
| 4 | 4 | `new_virtual_album` | New album… |  |
| 5 | 5 | `rename_virtual_album` | Rename album |  |
| 6 | 6 | `album_name` | Album name |  |
| 7 | 7 | `virtual_folder_name` | Virtual album name |  |
| 8 | 8 | `virtual_album_name_empty` | The album name cannot be empty |  |
| 9 | 9 | `virtual_album_exists` | An album with this name already exists |  |
| 10 | 10 | `add_to_virtual_album` | Add to virtual album | Legg til i virtuelt album |
| 11 | 11 | `remove_from_virtual_album` | Remove from virtual album | Fjern frå virtuelt album |
| 12 | 12 | `added_to_virtual_album` | Added to \"%s\" |  |
| 13 | 13 | `already_in_virtual_album` | Already in \"%s\" |  |
| 14 | 14 | `removed_from_virtual_album` | Removed from the album |  |
| 15 | 15 | `delete_virtual_album_confirmation` | Delete the album %s? Only the album is removed, your photos and videos stay where they are. |  |
| 16 | 16 | `virtual_album_empty` | This album is empty. Select items in any folder and use \"Add to virtual album\". | Dette albumet er tomt. Vel element i ei kva som helst mappe og bruk \"Legg til i virtuelt album\". |
| 17 | 17 | `virtual_album_cannot_be_modified` | This action is not available for virtual albums |  |
| 18 | 18 | `alphanumeric_password` | Alphanumeric | Alfanumerisk |
| 19 | 19 | `repeat_password` | Repeat password |  |
| 20 | 20 | `wrong_password` | Wrong password |  |
| 21 | 21 | `encrypting_files` | Encrypting files… |  |
| 22 | 22 | `encrypting_folder` | Encrypting folder… |  |
| 23 | 23 | `folder_encrypted_successfully` | Folder encrypted successfully |  |
| 24 | 24 | `decrypting_folder` | Decrypting folder… |  |
| 25 | 25 | `folder_decrypted_successfully` | Folder decrypted successfully |  |
| 26 | 26 | `decrypting_files` | Decrypting files… |  |
| 27 | 27 | `place_finger_prompt` | Please place your finger on the fingerprint sensor |  |
| 28 | 28 | `connect_at_least_4_dots_prompt` | Connect at least 4 dots |  |
| 29 | 29 | `access_existing_secrets_prompt` | Do you want to access your existing Secrets folder? |  |
| 30 | 30 | `delete_and_recreate_secrets_prompt` | Existing Secrets folder will be deleted and a new one will be created. Are you sure? |  |
| 31 | 31 | `confirm_checkbox_label` | I confirm |  |
| 32 | 32 | `secret_vault` | Encrypted Vault |  |
| 33 | 33 | `secret_vault_empty` | Encrypted Vault is empty |  |
| - | - | `press_back_again_to_exit_vault` | Press back again to exit | Toast shown on first back press in hidden vault |
| - | - | `block_vault_screenshots` | Block screenshots in encrypted vault | Settings switch (default ON) blocking screenshots in encrypted vault |
| 34 | 34 | `confirm_decrypt_hidden_file` | Are you sure you want to decrypt the hidden file(s)? |  |
| 35 | 35 | `copy_to_clipboard_disabled_in_vault` | Copying to clipboard is disabled while vault is unlocked for security. |  |
| 36 | 44 | `open_system_language_settings` | Open system language settings |  |
| 37 | 45 | `file_encrypted_successfully` | File encrypted successfully | Fila vart kryptert                           |
| 38 | 46 | `files_encrypted_successfully` | Files encrypted successfully | Filene vart krypterte                         |
| 39 | 47 | `file_decrypted_successfully` | File decrypted successfully | Fila vart dekryptert                         |
| 40 | 48 | `files_decrypted_successfully` | Files decrypted successfully | Filene vart dekrypterte                       |
| 41 | 49 | `access_hidden_items_with_biometrics` | Access hidden items with biometrics | Settings switch, setup prompt title, and vault security dialog button |
| 42 | 50 | `enable_hidden_protection_first_prompt` | Please enable hidden item password protection first | Toast shown when enabling biometrics before hidden-item password protection |
| 43 | 51 | `biometric_unlock_vault` | Unlock encrypted vault | BiometricPrompt title when unlocking the encrypted vault |
| 44 | 52 | `biometric_unlock_prompt` | Verify your biometric identity to access hidden items | BiometricPrompt subtitle when unlocking hidden items |
| 45 | 53 | `biometric_setup_prompt` | Confirm your biometric identity to enable biometric vault access | BiometricPrompt subtitle when enabling biometric vault access |
| 46 | 54 | `use_password_or_pin` | Use PIN / Password | BiometricPrompt negative button to fall back to PIN/password |
| 47 | 55 | `biometric_key_invalidated` | Biometric credentials have changed. Please enter your PIN or password. | Toast shown when biometric credentials changed and Keystore key invalidated |
| 48 | 56 | `no_biometric_hardware_or_enrolled` | No biometric hardware or enrolled credentials found on this device | Toast shown when no biometric hardware or enrolled credentials exist |
| 49 | 57 | `default_hidden_album` | Default Hidden Album | Settings / picker label for default hidden album |
| 50 | 58 | `select_hidden_album` | Select Encrypted Album | Vel kryptert album                                   |
| 51 | 59 | `create_new_hidden_album` | Create New Encrypted Album | Opprett nytt kryptert album                  |
| 52 | 60 | `new_hidden_album` | New encrypted album | Nytt kryptert album                                  |
| 53 | 61 | `hidden_album_name_empty` | Album name cannot be empty | Validation error when hidden album name is blank |
| 54 | 62 | `file_hidden_to_album` | File encrypted to \"%s\" | Fil kryptert i «%s»                                          |
| 55 | 63 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d filer krypterte i «%2$s»                                 |
| 56 | `pull_down_to_open_vault` | Keep pulling down for Encrypted Vault | Hald fram med å dra ned for kryptert kvelv | Vault pull gesture hint shown while the pull is below the open threshold |
| 57 | `keep_holding_to_open_vault` | Keep holding to open Encrypted Vault | Hald fram med å halde for å opne kryptert kvelv | Vault pull gesture hint shown once the pull passes the threshold, before the 2.5 s hold completes |
| 58 | `release_to_open_vault` | Release to open Encrypted Vault | Slepp for å opne kryptert kvelv | Vault pull gesture hint shown after holding the pull for 2.5 s; releasing opens the vault |


### Newly Added Missing Translations (9 Keys) & Revised Entries (11 Keys)

Nine keys that were missing from `values-nn/strings.xml` were translated and added, and eleven existing entries were revised so that files and albums use the new \"encrypt / decrypt\" wording and so that the favorites import/export labels mention virtual albums as well.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | `number_of_items` | Number of items | Tal på element | Sorting option shown in the album view only; overridden locally because it is missing in `fossify-commons` for this locale |
| 2 | `random` | Random | Tilfeldig | Sorting option offered in every sorting state; overridden locally because it is missing in `fossify-commons` for this locale |
| 3 | `shuffle` | Shuffle | Bland | Fullscreen viewer action that randomizes the media order; app owned string that was only available in English |
| 4 | `stop_showing_excluded` | Stop showing excluded | Slutt å vise ekskluderte | Main screen options-menu action that ends the temporary preview of excluded folders |
| 5 | `copy_to_clipboard` | Copy to clipboard | Kopier til utklippstavla | Fullscreen viewer action that copies a file to the clipboard |
| 6 | `encrypt` | Encrypt | Krypter | Replaces the inherited `hide` label for files; files and albums are encrypted, not hidden |
| 7 | `decrypt` | Decrypt | Dekrypter | Replaces the inherited `unhide` label for files |
| 8 | `encrypt_album` | Encrypt album | Krypter album | Album (folder) context action that encrypts the album into the vault |
| 9 | `decrypt_album` | Decrypt album | Dekrypter album | Album (folder) context action that decrypts the album back to its original location |
| 10 | `export_favorite_paths` | Export favorites and virtual albums | Eksporter favorittar og virtuelle album | Settings label; renamed because virtual albums are exported together with the favorites |
| 11 | `import_favorite_paths` | Import favorites and virtual albums | Importer favorittar og virtuelle album | Settings label; renamed because virtual albums are imported together with the favorites |
| 12 | `file_encrypted_successfully` | File encrypted successfully | Fila vart kryptert | Success toast after encrypting a single file |
| 13 | `files_encrypted_successfully` | Files encrypted successfully | Filene vart krypterte | Success toast after encrypting multiple files |
| 14 | `file_decrypted_successfully` | File decrypted successfully | Fila vart dekryptert | Success toast after decrypting a single file |
| 15 | `files_decrypted_successfully` | Files decrypted successfully | Filene vart dekrypterte | Success toast after decrypting multiple files |
| 16 | `file_hidden_to_album` | File encrypted to \"%s\" | Fil kryptert i «%s» | Success toast after encrypting a single file into album `%s` |
| 17 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d filer krypterte i «%2$s» | Success toast after encrypting `%1$d` files into album `%2$s` |
| 18 | `select_hidden_album` | Select Encrypted Album | Vel kryptert album | Dialog title for choosing the target encrypted album |
| 19 | `create_new_hidden_album` | Create New Encrypted Album | Opprett nytt kryptert album | Action label to create a new encrypted album |
| 20 | `new_hidden_album` | New encrypted album | Nytt kryptert album | Input hint / default label for a new encrypted album |

### Newly Added UI, Backup, Encryption Confirmation & Vault Strings (22 Keys)

Following the previous audit, 22 additional keys were localized in `values-nn/strings.xml` (`create_new_encrypted_virtual_album` and `new_album` for vault destination dialogs, plus 20 strings covering backup export/import, auto-backup prompt, encryption confirmations, delete-behavior options, album lock/unlock labels, and progress notifications; `hide_folder_description` was removed from the app, and `default_hidden_album` was marked `translatable="false"` as `Default Secret Album`):

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | `create_new_encrypted_virtual_album` | Create new encrypted virtual album | Opprett nytt kryptert virtuelt album | Dialog option for creating an encrypted virtual album inside the vault |
| 2 | `new_album` | New album | Nytt album | Compact neutral button label in the move destination dialog |
| 3 | `filename_without_zip` | Filename (without .zip) | Filnamn (utan .zip) | Input hint in the export favorites and virtual albums dialog |
| 4 | `favorites_already_exist` | You already have a favorites list | Du har allereie ei favorittliste | Dialog title when importing favorites while a favorites list already exists |
| 5 | `delete_existing_list` | Delete existing list | Slett eksisterande liste | Radio option to replace the existing favorites list on import |
| 6 | `merge_lists` | Merge lists | Slå saman lister | Radio option to merge imported favorites with the existing list |
| 7 | `confirm` | Confirm | Stadfest | Positive button label in the favorites import conflict dialog |
| 8 | `confirm_encrypt_album` | Are you sure you want to encrypt this album? All of its media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | Er du sikker på at du vil kryptera dette albumet? Alle mediefilene i det vert krypterte og flytte til det krypterte kvelvet. Originalfilene vert sletta. | Confirmation prompt before encrypting a single album into the vault |
| 9 | `confirm_encrypt_albums` | Are you sure you want to encrypt the selected albums? All of their media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | Er du sikker på at du vil kryptera dei valde albuma? Alle mediefilene i dei vert krypterte og flytte til det krypterte kvelvet. Originalfilene vert sletta. | Confirmation prompt before encrypting multiple selected albums into the vault |
| 10 | `confirm_encrypt_file` | Are you sure you want to encrypt this file? It will be encrypted and moved to the encrypted vault. The original file will be deleted. | Er du sikker på at du vil kryptera denne fila? Ho vert kryptert og flytt til det krypterte kvelvet. Originalfila vert sletta. | Confirmation prompt before encrypting a single file into the vault |
| 11 | `confirm_encrypt_files` | Are you sure you want to encrypt the selected files? They will be encrypted and moved to the encrypted vault. The original files will be deleted. | Er du sikker på at du vil kryptera dei valde filene? Dei vert krypterte og flytte til det krypterte kvelvet. Originalfilene vert sletta. | Confirmation prompt before encrypting multiple selected files into the vault |
| 12 | `confirm_encrypt_files_to_vault` | The selected files will be encrypted when they are copied into the encrypted vault. Do you want to continue? | Dei valde filene vert krypterte når dei vert kopierte til det krypterte kvelvet. Vil du halda fram? | Confirmation prompt when copying unencrypted files into an encrypted vault destination |
| 13 | `encrypt_decrypt_notifications` | Encrypt / Decrypt | Krypter / Dekrypter | Notification channel name for ongoing encrypt/decrypt progress notifications |
| 14 | `allow_batch_renaming` | Allow batch renaming when multiple files are selected | Tillat masseendring av namn når fleire filer er valde | Retained in values/strings.xml for parity (its Settings switch was removed in a6a78d444) |
| 15 | `keep_original_remove_from_virtual_album` | Keep the original file, remove it only from this virtual album | Behald originalfila, fjern henne berre frå dette virtuelle albumet | Checkbox in delete confirmation dialog when deleting from a virtual album |
| 16 | `keep_original_remove_from_favorites` | Keep the original file, remove it only from favorites | Behald originalfila, fjern henne berre frå favorittar | Checkbox in delete confirmation dialog when deleting from favorites |
| 17 | `delete_original_from_favorites_and_virtual_albums` | Delete the original version of a file when it is deleted from favorites and virtual albums | Slett originalversjonen av ei fil når ho vert sletta frå favorittar og virtuelle album | Settings switch controlling default delete behavior from favorites and virtual albums |
| 18 | `cannot_target_encrypted_album` | Encrypted albums cannot be targeted for this operation | Krypterte album kan ikkje veljast som mål for denne handlinga | Toast shown when an encrypted album cannot be targeted for an operation |
| 19 | `cannot_mix_encrypted_and_unencrypted` | Cannot combine encrypted and unencrypted items in this operation | Kan ikkje kombinera krypterte og ukrypterte element i denne handlinga | Toast shown when an operation attempts to combine encrypted and unencrypted items |
| 20 | `lock_album` | Lock album | Lås album | Album selection menu action replacing lock_folder |
| 21 | `unlock_album` | Unlock album | Lås opp album | Album selection menu action replacing unlock_folder |
| 22 | `auto_backup_prompt_message` | Do you want your favorites and virtual albums to be regularly written to system storage outside of app memory? This will ensure that you do not lose your favorites list and virtual albums if you uninstall the app. You can manually create a backup from app settings at any time. | Vil du at favorittane og dei virtuelle albuma dine skal lagrast regelmessig i systemlagringa utanfor appminnet? Dette sikrar at du ikkje mistar favorittlista og dei virtuelle albuma dine om du avinstallerer appen. Du kan når som helst laga ei tryggingskopi manuelt frå appinnstillingane. | Full-screen prompt asking whether to regularly back up favorites and virtual albums to shared storage |
| 23 | `auto_backup_favorites_and_virtual_albums` | Back up favorites and virtual albums to device storage | Tryggingskopier favorittar og virtuelle album til einingslagringa | Settings switch under File operations (shown after first auto-backup prompt) |

## 2. Full 1-to-1 Comparison: English vs. Norwegian Nynorsk

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) |
|---:|---|---|---|
| 1 | `app_launcher_name` | Calypso Gallery | Galleri |
| 2 | `edit` | Edit | Rediger |
| 3 | `open_camera` | Open camera | Opna kameraet |
| 4 | `hidden` | (hidden) | (skjult) |
| 5 | `excluded` | (excluded) | (ekskludert) |
| 6 | `pin_folder` | Pin folder | Fest mappa |
| 7 | `unpin_folder` | Unpin folder | Løys mappa |
| 8 | `pin_to_the_top` | Pin to the top | Fest øvst |
| 9 | `show_all` | Show all folders content | Syn innhaldet i alle mapper |
| 10 | `all_folders` | All folders | Alle mapper |
| 11 | `folder_view` | Switch to folder view | Byt til mappesyning |
| 12 | `other_folder` | Other folder | Anna mappe |
| 13 | `show_on_map` | Show on map | Syn på kartet |
| 14 | `unknown_location` | Unknown location | Ukjend plassering |
| 15 | `volume` | Volume | Ljodstyrke |
| 16 | `brightness` | Brightness | Ljosstyrke |
| 17 | `lock_orientation` | Lock orientation | Lås skjermretning |
| 18 | `unlock_orientation` | Unlock orientation | Lås opp skjermretning |
| 19 | `change_orientation` | Change orientation | Endra retning |
| 20 | `force_portrait` | Force portrait | Tvinga portrettmodus |
| 21 | `force_landscape` | Force landscape | Tvinga landskapsmodus |
| 22 | `force_landscape_reverse` | Force landscape (reverse) | Tvinga landskapsmodus (omvend) |
| 23 | `use_default_orientation` | Use default orientation | Bruk standardretning |
| 24 | `fix_date_taken` | Fix Date Taken value | Retta dato på opptak |
| 25 | `fixing` | Fixing… | Rettar… |
| 26 | `dates_fixed_successfully` | Dates fixed successfully | Datoar er retta |
| 27 | `no_date_takens_found` | No Date Taken values have been found | Fann ingen verdiar for dato på opptak |
| 28 | `share_resized` | Share a resized version | Del ei utgåve med endra storleik |
| 29 | `switch_to_file_search` | Switch to file search across all visible folders | Byt til filsøk i alle synlege mapper |
| 30 | `set_as_default_folder` | Set as default folder | Set som standardmappe |
| 31 | `unset_as_default_folder` | Unset as default folder | Ikkje bruk som standardmappe lenger |
| 32 | `reorder_by_dragging` | Reorder folders by dragging | Endra mapperekkjefølgje ved å dra |
| 33 | `reorder_by_dragging_pro` | Reorder folders by dragging (Pro) | Endra mapperekkjefølgje ved å dra (Pro) |
| 34 | `restore_to_path` | Restoring to \'%s\' | Gjenopprettar til \'%s\' |
| 35 | `full_storage_permission_required` | Calypso Gallery needs full access to display all your photos and videos. Go to Settings > Permissions > Photos and videos > Allow all. | Fossify Gallery treng full tilgang for å syna alle bileta og videoane dine. Gå til Innstillingar &gt; Løyve &gt; Bilete og videoar &gt; Tillat alle. |
| 36 | `filter_media` | Filter media | Filtrer media |
| 37 | `images` | Images | Bilete |
| 38 | `videos` | Videos | Videoar |
| 39 | `gifs` | GIFs | GIF-bilete |
| 40 | `raw_images` | RAW images | RAW-bilete |
| 41 | `svgs` | SVGs | SVG-bilete |
| 42 | `portraits` | Portraits | Portrett |
| 43 | `no_media_with_filters` | No media files have been found with the selected filters. | Fann ingen mediafiler med dei valde filtra. |
| 44 | `change_filters_underlined` | <u>Change filters</u> | <u>Endra filtre</u> |
| 45 | `exclude_folder_description` | This will exclude the selection together with its subfolders from Calypso Gallery only. You can manage excluded folders in Settings. | Dette vil ekskludera utvalet saman med undermapper berre frå Fossify Gallery. Du kan handsama ekskluderte mapper i Innstillingar. |
| 46 | `exclude_folder_parent` | Exclude a parent instead? | Ekskludera ei overordna mappe i staden? |
| 47 | `excluded_activity_placeholder` | Excluding folders will make them together with their subfolders hidden just in Calypso Gallery, they will still be visible in other applications.\n\nIf you want to hide them from other apps too, use the Encrypt function. | Ekskludering av mapper gjer dei og undermappene deira skjulte berre i Fossify Gallery; dei vil framleis vera synlege i andre appar.\n\nViss du vil skjula dei frå andre appar òg, bruk Skjul-funksjonen. |
| 48 | `hidden_folders` | Hidden folders | Skjulte mapper |
| 49 | `manage_hidden_folders` | Manage hidden folders | Handsama skjulte mapper |
| 50 | `hidden_folders_placeholder` | Seems like you don\'t have any folders hidden with a \".nomedia\" file or dot prefix. | Det ser ikkje ut til at du har nokon mapper skjulte med ei \".nomedia\"-fil. |
| 51 | `hidden_all_files` | You have to grant the app All Files access to see hidden files, else it cannot work. | Du må gje appen tilgang til alle filer for å sjå skjulte filer, elles fungerer det ikkje. |
| 52 | `cant_unhide_folder` | If a folder or one of its parent folders has a dot before its name, it is hidden and cannot be unhidden like this. You have to remove the dot by renaming it. | Viss ei mappe eller ei av dei overordna mappene har eit punktum framfor namnet, er ho skjult og kan ikkje synast att slik. Du må fjerna punktumet ved å gje henne nytt namn. |
| 53 | `include_folders` | Included folders | Inkluderte mapper |
| 54 | `manage_included_folders` | Manage included folders | Handsama inkluderte mapper |
| 55 | `add_folder` | Add folder | Legg til mappe |
| 56 | `included_activity_placeholder` | If you have some folders which contain media, but were not recognized by the app, you can add them manually here.\n\nAdding some items here will not exclude any other folder. | Viss du har mapper som inneheld media, men som ikkje vart attkjende av appen, kan du leggja dei til manuelt her.\n\nÅ leggja til element her ekskluderer ikkje andre mapper. |
| 57 | `no_media_add_included` | No media files have been found. You can solve it by adding the folders containing media files manually. | Fann ingen mediafiler. Du kan løysa det ved å leggja til mappene som inneheld mediafiler manuelt. |
| 58 | `resize_and_save` | Resize selection and save | Endra storleik på utval og lagra |
| 59 | `width` | Width | Breidd |
| 60 | `height` | Height | Høgd |
| 61 | `keep_aspect_ratio` | Keep aspect ratio | Hald sideforhold |
| 62 | `invalid_values` | Please enter a valid resolution | Ver venleg og oppgje ei gyldig oppløysing |
| 63 | `resize_multiple_images` | Resize multiple images | Endra storleik på fleire bilete |
| 64 | `resize_factor` | Resize factor | Storleiksfaktor |
| 65 | `resize_factor_info` | Resize images to the given percentage, value must be within 10 and 90. | Endra storleiken på bilete til den oppgjevne prosenten; verdien må vera mellom 10 og 90. |
| 66 | `resize_factor_error` | Enter a number between 10 and 90 | Skriv inn eit tal mellom 10 og 90 |
| 67 | `images_resized_successfully` | Images resized successfully | Storleiken på bileta vart endra |
| 68 | `editor` | Editor | Redigering |
| 69 | `basic_editor` | Basic Editor | Enkel redigering |
| 70 | `advanced_editor` | Advanced Editor | Avansert redigering |
| 71 | `rotate` | Rotate | Roter |
| 72 | `invalid_image_path` | Invalid image path | Ugyldig biletsti |
| 73 | `invalid_video_path` | Invalid video path | Ugyldig videosti |
| 74 | `image_editing_failed` | Image editing failed | Biletredigering mislukkast |
| 75 | `video_editing_failed` | Video editing failed | Videoredigering mislukkast |
| 76 | `image_editing_cancelled` | Image editing cancelled | Biletredigering avbroten |
| 77 | `video_editing_cancelled` | Video editing cancelled | Videoredigering avbroten |
| 78 | `file_edited_successfully` | File edited successfully | Fila vart redigert |
| 79 | `image_edited_successfully` | Image edited successfully | Biletet vart redigert |
| 80 | `video_edited_successfully` | Video edited successfully | Videoen vart redigert |
| 81 | `edit_image_with` | Edit image with: | Rediger bilete med: |
| 82 | `edit_video_with` | Edit video with: | Rediger video med: |
| 83 | `no_image_editor_found` | No image editor found | Fann ingen biletredigeringsapp |
| 84 | `no_video_editor_found` | No video editor found | Fann ingen videoredigeringsapp |
| 85 | `unknown_file_location` | Unknown file location | Ukjend filplassering |
| 86 | `overwrite_original` | Overwrite original | Overskriv original |
| 87 | `error_saving_file` | Could not overwrite the source file | Kunne ikkje overskriva kjeldefila |
| 88 | `rotate_left` | Rotate left | Roter mot venstre |
| 89 | `rotate_right` | Rotate right | Roter mot høgre |
| 90 | `rotate_one_eighty` | Rotate by 180º | Roter 180º |
| 91 | `transform` | Transform | Transformer |
| 92 | `crop` | Crop | Beskjer |
| 93 | `draw` | Draw | Teikn |
| 94 | `flip` | Flip | Speglvend |
| 95 | `flip_horizontally` | Flip horizontally | Speglvend vassrett |
| 96 | `flip_vertically` | Flip vertically | Speglvend loddrett |
| 97 | `free_aspect_ratio` | Free | Fri |
| 98 | `other_aspect_ratio` | Other | Anna |
| 99 | `wallpaper` | Wallpaper | Bakgrunnsbilete |
| 100 | `set_as_wallpaper` | Set as Wallpaper | Set som bakgrunnsbilete |
| 101 | `set_as_wallpaper_failed` | Setting as Wallpaper failed | Kunne ikkje setja som bakgrunnsbilete |
| 102 | `set_as_wallpaper_with` | Set as wallpaper with: | Set som bakgrunnsbilete med: |
| 103 | `setting_wallpaper` | Setting wallpaper… | Set bakgrunnsbilete… |
| 104 | `wallpaper_set_successfully` | Wallpaper set successfully | Bakgrunnsbiletet vart sett |
| 105 | `portrait_aspect_ratio` | Portrait aspect ratio | Portrett-sideforhold |
| 106 | `landscape_aspect_ratio` | Landscape aspect ratio | Landskap-sideforhold |
| 107 | `home_screen` | Home screen | Heimeskjerm |
| 108 | `lock_screen` | Lock screen | Låseskjerm |
| 109 | `home_and_lock_screen` | Home and lock screen | Heime- og låseskjerm |
| 110 | `allow_changing_aspect_ratio` | Allow changing the aspect ratio | Tillat endring av sideforholdet |
| 111 | `slideshow` | Slideshow | Lysbileteframsyning |
| 112 | `interval` | Interval | Intervall |
| 113 | `include_photos` | Include photos | Ta med foto |
| 114 | `include_videos` | Include videos | Ta med videoar |
| 115 | `include_gifs` | Include GIFs | Ta med GIF-bilete |
| 116 | `random_order` | Random order | Tilfeldig rekkjefølgje |
| 117 | `move_backwards` | Move backwards | Spel baklengs |
| 118 | `loop_slideshow` | Loop slideshow | Gjenta lysbileteframsyning |
| 119 | `animation` | Animation | Animasjon |
| 120 | `no_animation` | None | Ingen |
| 121 | `fade` | Fade | Ton ut |
| 122 | `slide` | Slide | Gliding |
| 123 | `slideshow_ended` | The slideshow ended | Lysbileteframsyninga er slutt |
| 124 | `no_media_for_slideshow` | No media for the slideshow have been found | Fann ikkje media til lysbileteframsyninga |
| 125 | `group_direct_subfolders` | Group direct subfolders | Grupper direkte undermapper |
| 126 | `group_by` | Group by | Grupper etter |
| 127 | `do_not_group_files` | Do not group files | Ikkje grupper filer |
| 128 | `by_folder` | Folder | Mappe |
| 129 | `by_last_modified` | Last modified | Sist endra |
| 130 | `by_last_modified_daily` | Last modified (daily) | Sist endra (dagleg) |
| 131 | `by_last_modified_monthly` | Last modified (monthly) | Sist endra (månadleg) |
| 132 | `by_date_taken` | Date taken | Dato teken |
| 133 | `by_date_taken_daily` | Date taken (daily) | Dato teken (dagleg) |
| 134 | `by_date_taken_monthly` | Date taken (monthly) | Dato teken (månadleg) |
| 135 | `by_file_type` | File type | Filtype |
| 136 | `by_extension` | Extension | Filending |
| 137 | `show_file_count_section_header` | Show file count at section headers | Syn filtal i seksjonsoverskrifter |
| 138 | `grouping_and_sorting` | Please note that grouping and sorting are 2 independent fields | Merk at gruppering og sortering er to uavhengige felt |
| 139 | `folder_on_widget` | Folder shown on the widget: | Mappe synt på modulen: |
| 140 | `show_folder_name` | Show folder name | Syn mappenamn |
| 141 | `autoplay_videos` | Play videos automatically | Spel av videoar automatisk |
| 142 | `remember_last_video_position` | Remember last video playback position | Hugs siste videoavspelingsposisjon |
| 143 | `loop_videos` | Loop videos | Gjenta videoar |
| 144 | `video_double_tap_duration` | Double-tap seek duration | Søketid ved dobbelttrykk |
| 145 | `video_seek_duration_seconds` | %d seconds | %d sekund |
| 146 | `animate_gifs` | Animate GIFs at thumbnails | Animer GIF-bilete i minibilete |
| 147 | `max_brightness` | Max brightness when viewing fullscreen media | Maks ljosstyrke ved fullskjermsvisning |
| 148 | `render_ultra_hdr_photos` | Show Ultra HDR photos in HDR | Syn Ultra HDR-bilete i HDR |
| 149 | `crop_thumbnails` | Crop thumbnails into squares | Beskjer minibilete til kvadrat |
| 150 | `show_thumbnail_video_duration` | Show video durations | Syn videolengd |
| 151 | `screen_rotation_by` | Rotate fullscreen media by | Roter fullskjermsmedia etter |
| 152 | `screen_rotation_system_setting` | System setting | Systeminnstilling |
| 153 | `screen_rotation_device_rotation` | Device rotation | Einingsrotasjon |
| 154 | `screen_rotation_aspect_ratio` | Aspect ratio | Sideforhold |
| 155 | `black_background_at_fullscreen` | Black background at fullscreen media | Svart bakgrunn ved fullskjermsmedia |
| 156 | `scroll_thumbnails_horizontally` | Scroll thumbnails horizontally | Rull minibilete vassrett |
| 157 | `hide_system_ui_at_fullscreen` | Automatically hide system UI at fullscreen media | Skjul systemlinjer automatisk ved fullskjermsmedia |
| 158 | `delete_empty_folders` | Delete empty folders after deleting their content | Slett tomme mapper etter sletting av innhaldet |
| 159 | `keep_screen_on_fullscreen_photos` | Keep the screen on while viewing fullscreen photos | Hald skjermen på ved syning av bilete i fullskjerm |
| 160 | `allow_photo_gestures` | Allow controlling photo brightness with vertical gestures | Tillat styring av bileteljosstyrke med loddrette rørsler |
| 161 | `allow_video_gestures` | Allow controlling video volume and brightness with vertical gestures | Tillat styring av videoljodstyrke og ljosstyrke med loddrette rørsler |
| 162 | `playback_speed` | Playback speed | Avspelingsfart |
| 163 | `show_media_count` | Show folder media count on the main view | Syn tal på mediafiler i mapper på hovudsyninga |
| 164 | `show_extended_details` | Show extended details over fullscreen media | Syn utvida detaljar over fullskjermsmedia |
| 165 | `manage_extended_details` | Manage extended details | Handsama utvida detaljar |
| 166 | `one_finger_zoom` | Allow one finger zoom at fullscreen media | Tillat éin-finger-zoom i fullskjermsmedia |
| 167 | `allow_instant_change` | Allow instantly changing media by clicking on screen sides | Tillat å byta media straks ved å trykkja på skjermkantane |
| 168 | `allow_deep_zooming_images` | Allow deep zooming images | Tillat djup zooming på bilete |
| 169 | `hide_extended_details` | Hide extended details when status bar is hidden | Skjul utvida detaljar når statuslinja er skjult |
| 170 | `show_at_bottom` | Show some action buttons at the bottom of the screen | Syn nokre handlingsknappar nedst på skjermen |
| 171 | `show_recycle_bin` | Show the recycle bin on the folders screen | Syn papirkorga på mappeskjermen |
| 172 | `deep_zoomable_images` | Deep zoomable images | Djupt zoombare bilete |
| 173 | `show_highest_quality` | Show images in the highest possible quality | Syn bilete i høgst mogleg kvalitet |
| 174 | `show_recycle_bin_last` | Show the recycle bin as the last item on the main screen | Syn papirkorga som siste element på hovudskjermen |
| 175 | `allow_down_gesture` | Allow closing the fullscreen view with a down gesture | Tillat å lukka fullskjermsvisninga med ei nedoverrørsle |
| 176 | `allow_one_to_one_zoom` | Allow 1:1 zooming in with two double taps | Tillat 1:1-zooming med to dobbelttrykk |
| 177 | `open_videos_on_separate_screen` | Play videos on a separate screen with horizontal gestures | Spel videoar på ein eigen skjerm med vassrette rørsler |
| 178 | `on_video_tap` | On video tap | Ved videotrykk |
| 179 | `open_in_app_player` | Open in-app player | Opna spelar i appen |
| 180 | `open_system_default_player` | Open in another app | Opne i ein annan app |
| 181 | `show_notch` | Show a notch if available | Syn skjermhakk viss tilgjengeleg |
| 182 | `allow_rotating_gestures` | Allow rotating images with gestures | Tillat rotering av bilete med fingerrørsler |
| 183 | `file_loading_priority` | File loading priority | Prioritet for fillasting |
| 184 | `speed` | Speed | Fart |
| 185 | `compromise` | Compromise | Kompromiss |
| 186 | `avoid_showing_invalid_files` | Avoid showing invalid files | Unngå å syna ugyldige filer |
| 187 | `show_image_file_types` | Show image file types | Syn biletfiltypar |
| 188 | `allow_zooming_videos` | Allow zooming videos with double tapping them | Tillat zooming av videoar med dobbelttrykk |
| 189 | `folder_thumbnail_style` | Folder thumbnail style | Minibiletestil for mapper |
| 190 | `file_thumbnail_style` | File thumbnail style | Minibiletestil for filer |
| 191 | `mark_favorite_items` | Mark favorite items | Marker favoritelement |
| 192 | `thumbnail_spacing` | Thumbnail spacing | Minibiletemellomrom |
| 193 | `show_file_count_line` | Show file count on a separate line | Syn filtal på ei eiga linje |
| 194 | `show_file_count_brackets` | Show file count in brackets | Syn filtal i parentes |
| 195 | `show_file_count_none` | Do not show file count | Ikkje syn filtal |
| 196 | `limit_folder_title` | Limit long folder titles to 1 line | Avgrens lange mappetitlar til 1 linje |
| 197 | `square` | Square | Firkanta |
| 198 | `rounded_corners` | Rounded corners | Avrunda hjørne |
| 199 | `export_favorite_paths` | Export favorites and virtual albums | Eksporter favorittar og virtuelle album |
| 200 | `import_favorite_paths` | Import favorites and virtual albums | Importer favorittar og virtuelle album |
| 201 | `paths_imported_successfully` | Paths imported successfully | Filstiar vart importerte |
| 202 | `media_management_prompt` | To make sure that all file operations work reliably, please make this app a Media management app in your device settings. | For å vera sikker på at alle filoperasjonar fungerer påliteleg, gjer denne appen til ein mediaadministrasjonsapp i einingsinnstillingane. |
| 203 | `password_protect_excluded` | Password protect excluded folder visibility | Passordvern for synlegheita til ekskluderte mapper |
| 204 | `media_management_manual` | Something went wrong, please go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | Noko gjekk gale. Gå til eininga sine Innstillingar – Appar – Spesiell apptilgang – Appar for mediaadministrasjon, og lat denne appen administrera media. |
| 205 | `media_management_note` | If the redirection does not work, please go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | Viss omdirigeringa ikkje fungerer, gå til eininga sine Innstillingar – Appar – Spesiell apptilgang – Appar for mediaadministrasjon, og lat denne appen administrera media. |
| 206 | `media_management_alternative` | If you do not want to do it, you can also go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | Viss du ikkje vil gjera det, kan du òg gå til eininga sine Innstillingar – Appar – Spesiell apptilgang – Appar for mediaadministrasjon, og lat denne appen administrera media. |
| 207 | `alternative_media_access` | Alternatively, you can allow accessing media files only. In that case you will not be able to work with hidden files though. | Alternativt kan du berre tillata tilgang til mediafiler. I så fall vil du ikkje kunna arbeida med skjulte filer. |
| 208 | `media_only` | Media only | Berre media |
| 209 | `all_files` | All files | Alle filer |
| 210 | `main_screen_view_on_launch` | Main screen view on launch | Hovudskjermsyning ved oppstart |
| 211 | `show_folders` | Show folders | Syn mapper |
| 212 | `show_files` | Show files | Syn filer |
| 213 | `show_all_folders` | Show a menu button for toggling Show All Folders Content quickly | Syn ein menyknapp for raskt å slå av/på \"Syn innhaldet i alle mapper\" |
| 214 | `thumbnails` | Thumbnails | Minibilete |
| 215 | `fullscreen_media` | Fullscreen media | Fullskjermsmedia |
| 216 | `extended_details` | Extended details | Utvida detaljar |
| 217 | `bottom_actions` | Bottom actions | Handlingar nedst |
| 218 | `manage_bottom_actions` | Manage visible bottom actions | Handsama synlege nedre handlingar |
| 219 | `toggle_favorite` | Toggle favorite | Slå av/på favoritt |
| 220 | `toggle_file_visibility` | Toggle file visibility | Slå av/på filsynlegheit |
| 221 | `failed_to_load_media` | Failed to load media. | Kunne ikkje lasta inn media. |
| 222 | `file_is_malformed_or_corrupted` | File is malformed or corrupted. | Fila er ugyldig eller øydelagd. |
| 223 | `media_exceeds_device_capabilities` | Media exceeds device capabilities. | Mediet overstig kapasiteten til eininga. |
| 224 | `unsupported_format` | Unsupported format. | Formatet er ikkje støtta. |
| 225 | `video_no_sound` | This video has no audio | Denne videoen har ingen ljod |
| 226 | `faq_1_title` | How can I make Calypso Gallery the default device gallery? | Korleis kan eg gjera Fossify Gallery til standardgalleriet på eininga? |
| 227 | `faq_1_text` | First you have to find the currently default gallery in the Apps section of your device settings, look for a button that says something like \"Open by default\", click on it, then select \"Clear defaults\".         The next time you will try opening an image or video you should see an app picker, where you can select Fossify Gallery and make it the default app. | Fyrst må du finna det gjeldande standardgalleriet i Appar-delen av einingsinnstillingane, sjå etter ein knapp som heiter noko slikt som \"Opna som standard\", trykkja på han og deretter velja \"Fjern standardval\".\nNeste gong du prøver å opna eit bilete eller ein video, skal du sjå ein appveljar der du kan velja Fossify Gallery og gjera han til standardapp. |
| 228 | `faq_2_title` | I locked the app with a password, but I forgot it. What can I do? | Eg låste appen med eit passord, men eg har gløymt det. Kva kan eg gjera? |
| 229 | `faq_2_text` | You can solve it in 2 ways. You can either reinstall the app, or find the app in your device settings and select \"Clear data\". It will reset all your settings, it will not remove any media files. | Du kan løysa det på to måtar. Du kan anten installera appen på nytt, eller finna appen i einingsinnstillingane og velja \"Fjern data\". Det vil nullstilla alle innstillingane dine, men fjernar ingen mediafiler. |
| 230 | `faq_3_title` | How can I make an album always appear at the top? | Korleis kan eg få eit album til alltid å synast øvst? |
| 231 | `faq_3_text` | You can long press the desired album and select the Pin icon at the actions menu, that will pin it to the top. You can pin multiple folders too, pinned items will be sorted by the default sorting method. | Du kan trykkja og halda på ønskt album og velja Fest-ikonet i handlingsmenyen for å festa det øvst. Du kan festa fleire mapper òg; festa element vert sorterte etter standard sorteringsmetode. |
| 232 | `faq_4_title` | How can I fast forward videos? | Korleis kan eg spola framover i videoar? |
| 233 | `faq_4_text` | You can do it by double tapping the side of the screen, or tapping the current or max duration texts near the seekbar. If you enable opening videos on a separate screen in the app settings, you can use horizontal gestures too. | Du kan gjera det ved å dobbelttrykkja på sida av skjermen, eller trykkja på teksten for gjeldande eller maksimal lengd nær søkelinja. Viss du slår på avspeling av videoar på ein eigen skjerm i appinnstillingane, kan du bruka vassrette rørsler òg. |
| 234 | `faq_5_title` | What is the difference between hiding and excluding a folder? | Kva er skilnaden mellom å skjula og å ekskludera ei mappe? |
| 235 | `faq_5_text` | Exclude prevents displaying the folder only in Calypso Gallery, while Hide works system-wise and it hides the folder from other galleries too. It works by creating an empty \".nomedia\" file in the given folder, which you can then remove with any file manager too. Note that some devices do not allow hiding folders like Camera, Screenshots and Downloads. | Ekskluder hindrar at mappa vert synt berre i Fossify Gallery, medan Skjul verkar på systemnivå og skjuler mappa frå andre galleri òg. Det verkar ved å laga ei tom \".nomedia\"-fil i den gjeldande mappa, som du òg kan fjerna med ein kva som helst filhandsamar. Merk at somme einingar ikkje tillèt å skjula mapper som Camera, Screenshots og Downloads. |
| 236 | `faq_6_title` | Why do folders with music cover art or stickers show up? | Kvifor dukkar det opp mapper med plateomslag eller klistremerke? |
| 237 | `faq_6_text` | It can happen that you will see some unusual albums show up. You can easily exclude them by long pressing them and selecting Exclude. In the next dialog you can then select the parent folder, chances are it will prevent the other related albums showing up too. | Det kan henda at uvanlege album dukkar opp. Du kan enkelt ekskludera dei ved å trykkja og halda på dei og velja Ekskluder. I den neste dialogen kan du velja den overordna mappa, noko som truleg hindrar at andre relaterte album vert synte òg. |
| 238 | `faq_7_title` | A folder with images isn\'t showing up, or it doesn\'t show all items.  What can I do? | Ei mappe med bilete vert ikkje synt, eller ho syner ikkje alle elementa. Kva kan eg gjera? |
| 239 | `faq_7_text` | That can have multiple reasons, but solving it is easy. Just go in Settings -&gt; Manage Included Folders, select Plus and navigate to the required folder. | Det kan ha fleire årsaker, men det er enkelt å løysa. Berre gå til Innstillingar -&gt; Handsama inkluderte mapper, vel Pluss og naviger til den ønskte mappa. |
| 240 | `faq_8_title` | What if I want just a few particular folders visible? | Kva om eg berre vil at nokre bestemte mapper skal vera synlege? |
| 241 | `faq_8_text` | Adding a folder at the Included Folders doesn\'t automatically exclude anything. What you can do is go in Settings -&gt; Manage Excluded Folders, exclude the root folder \"/\", then add the desired folders at Settings -&gt; Manage Included Folders.         That will make only the selected folders visible, as both excluding and including are recursive and if a folder is both excluded and included, it will show up. | Å leggja til ei mappe under Inkluderte mapper ekskluderer ikkje automatisk noko anna. Det du kan gjera, er å gå til Innstillingar -&gt; Handsama ekskluderte mapper, ekskludera rotmappa \"/\", og deretter leggja til dei ønskte mappene under Innstillingar -&gt; Handsama inkluderte mapper.\nDet vil gjera berre dei valde mappene synlege, sidan både ekskludering og inkludering er rekursive, og viss ei mappe både er ekskludert og inkludert, vil ho verta synt. |
| 242 | `faq_10_title` | Can I crop images with this app? | Kan eg beskjera bilete med denne appen? |
| 243 | `faq_10_text` | Yes, you can crop images in the editor, by dragging the image corners. You can get to the editor either by long pressing an image thumbnail and selecting Edit, or selecting Edit from the fullscreen view. | Ja, du kan beskjera bilete i redigeringsverktøyet ved å dra i bilethjørna. Du kjem til redigeringsverktøyet anten ved å trykkja og halda på eit minibilete og velja Rediger, eller ved å velja Rediger frå fullskjermsvisninga. |
| 244 | `faq_11_title` | Can I somehow group media file thumbnails? | Kan eg på nokon måte gruppera minibilete av mediafiler? |
| 245 | `faq_11_text` | Sure, just use the \"Group by\" menu item while at the thumbnails view. You can group files by multiple criteria, including Date Taken. If you use the \"Show all folders content\" function you can group them by folders too. | Sjølvsagt, berre bruk menyelementet \"Grupper etter\" når du er i minibiletevisninga. Du kan gruppera filer etter fleire kriterium, inkludert Dato teken. Viss du bruker funksjonen \"Syn innhaldet i alle mapper\", kan du gruppera dei etter mapper òg. |
| 246 | `faq_12_title` | Sorting by Date Taken doesn\'t seem to work properly, how can I fix it? | Sortering etter Dato teken ser ikkje ut til å fungera skikkeleg, korleis kan eg retta det? |
| 247 | `faq_12_text` | It is most likely caused by the files being copied from somewhere. You can fix it by selecting the file thumbnails and selecting \"Fix Date Taken value\". | Det kjem truleg av at filene er kopierte frå ein annan stad. Du kan retta det ved å merkja filminibileta og velja \"Retta dato på opptak\". |
| 248 | `faq_13_title` | I see some color banding on the images. How can I improve the quality? | Eg ser fargeband på bileta. Korleis kan eg forbetra kvaliteten? |
| 249 | `faq_13_text` | The current solution for displaying images works fine in the vast majority of cases, but if you want even better image quality, you can enable the \"Show images in the highest possible quality\" at the app settings, in the \"Deep zoomable images\" section. | Den gjeldande løysinga for biletsyning fungerer fint i dei aller fleste tilfelle, men viss du vil ha endå betre biletkvalitet, kan du slå på \"Syn bilete i høgst mogleg kvalitet\" i appinnstillingane under delen \"Djupt zoombare bilete\". |
| 250 | `faq_14_title` | I have hidden a file/folder. How can I unhide it? | Eg har skjult ei fil/mappe. Korleis kan eg gjera henne synleg att? |
| 251 | `faq_14_text` | You can either press the \"Temporarily show hidden items\" menu item at the main screen, or toggle \"Show hidden items\" in the app settings to see the hidden item. If you want to unhide it, just long press it and select \"Unhide\". Folders are hidden by adding a hidden \".nomedia\" file into them, you can delete the file with any file manager too. Note that hiding works recursively though, so if you hide a folder, all subfolders will become hidden too. So for unhiding the subfolders you have to unhide the parent folder. | Du kan anten trykkja på menyelementet \"Syn skjulte element mellombels\" på hovudskjermen, eller slå på \"Syn skjulte element\" i appinnstillingane for å sjå det skjulte elementet. Viss du vil gjera det synleg att, trykkjer og held du berre på det og vel \"Syn att\". Mapper vert skjulte ved å leggja ei skjult \".nomedia\"-fil i dei; du kan sletta denne fila med ein kva som helst filhandsamar òg. Merk likevel at skjuling verkar rekursivt, så viss du skjuler ei mappe, vert alle undermapper skjulte òg. For å gjera undermappene synlege att må du difor gjera den overordna mappa synleg att. |
| 252 | `faq_15_title` | Why does the app take up so much space? | Kvifor tek appen opp så mykje plass? |
| 253 | `faq_15_text` | App cache can take up to 250MB, it ensures quicker image loading. If the app is taking up even more space, it is most likely caused by you having items in the recycle bin. Those files count to the app size. You can clear the recycle bin by opening it and deleting all files, or from the app settings. Every file in the bin is deleted automatically after 30 days. | Appen sitt mellomlager kan ta opptil 250 MB; det syter for raskare lasting av bilete. Viss appen tek opp endå meir plass, kjem det truleg av at du har element i papirkorga. Desse filene tel med i appstorleiken. Du kan tøma papirkorga ved å opna henne og sletta alle filer, eller frå appinnstillingane. Kvar fil i papirkorga vert sletta automatisk etter 30 dagar. |
| 254 | `faq_16_title` | What happened to file and folder hiding and why cannot I see hidden items anymore? | Kva skjedde med skjuling av filer og mapper, og kvifor kan eg ikkje sjå skjulte element lenger? |
| 255 | `faq_16_text` | Starting with Android 11 you cannot hide or unhide files or folders anymore, you cannot see the hidden ones in gallery apps due to system restrictions either. You will have to use some file manager for that. | Frå og med Android 11 kan du ikkje lenger skjula eller syna filer eller mapper, og du kan heller ikkje sjå dei skjulte i galleriappar på grunn av systemavgrensingar. Du må bruka ein filhandsamar til det. |
| 256 | `faq_16_text_extra` | Or you can also grant this gallery access to All Files through your device settings, that will allow us showing hidden items and make file operations more reliable in general. | Eller du kan gje dette galleriet tilgang til Alle filer via einingsinnstillingane; det lèt oss syna skjulte element og gjer filoperasjonar meir pålitelege generelt. |
| 257 | `faq_17_title` | Why cannot I include missing folders anymore? | Kvifor kan eg ikkje inkludera manglande mapper lenger? |
| 258 | `faq_17_text` | That stopped working due to the system changes that came with Android 11 too, the app cannot browse real folders anymore, it relies on the so called MediaStore at fetching data. | Det slutta òg å fungera på grunn av systemendringane som kom med Android 11; appen kan ikkje bla gjennom verkelege mapper lenger, men er avhengig av såkalla MediaStore for å henta data. |
| 259 | `faq_18_title` | Why do I see ads during video playback? | Kvifor ser eg reklame under videoavspeling? |
| 260 | `faq_18_text` | Our apps have no ads whatsoever. If you see them during video playback, you must be using some other apps video player. Try finding your default video player in the device settings, then do a \"Clear defaults\" on it. The next time you invoke some video intent you will see an app picker prompt, where you can select what app you want to use. | Appane våre har inga reklame i det heile. Viss du ser reklame under videoavspeling, bruker du truleg videospelaren til ein annan app. Prøv å finna standard videospelar i einingsinnstillingane, og vel \"Fjern standardval\" på han. Neste gong du opnar ein video, vil du sjå ein appveljar der du kan velja kva app du vil bruka. |
| 261 | `create_virtual_album` | Create virtual album | Lag virtuelt album |
| 262 | `new_virtual_album` | New album… | Nytt album… |
| 263 | `rename_virtual_album` | Rename album | Gje albumet nytt namn |
| 264 | `album_name` | Album name | Albumnamn |
| 265 | `virtual_folder_name` | Virtual album name | Namn på virtuelt album |
| 266 | `virtual_album_name_empty` | The album name cannot be empty | Albumnamnet kan ikkje vera tomt |
| 267 | `virtual_album_exists` | An album with this name already exists | Eit album med dette namnet finst alt |
| 268 | `add_to_virtual_album` | Add to album | Legg til i album |
| 269 | `remove_from_virtual_album` | Remove from album | Fjern frå album |
| 270 | `added_to_virtual_album` | Added to \"%s\" | Lagt til i \"%s\" |
| 271 | `already_in_virtual_album` | Already in \"%s\" | Allereie i \"%s\" |
| 272 | `removed_from_virtual_album` | Removed from the album | Fjerna frå albumet |
| 273 | `delete_virtual_album_confirmation` | Delete the album %s? Only the album is removed, your photos and videos stay where they are. | Sletta albumet %s? Berre albumet vert fjerna, bileta og videoane dine vert verande der dei er. |
| 274 | `virtual_album_empty` | This album is empty. Select items in any folder and use \"Copy to\". | Dette albumet er tomt. Vel element i ei kva som helst mappe og bruk \"Kopier til\". |
| 275 | `virtual_album_cannot_be_modified` | This action is not available for virtual albums | Denne handlinga er ikkje tilgjengeleg for virtuelle album |
| 276 | `alphanumeric_password` | Alphanumeric | Alfanumerisk |
| 277 | `enter_password` | Enter password | Skriv inn passord |
| 278 | `repeat_password` | Repeat password | Gjenta passord |
| 279 | `wrong_password` | Wrong password | Feil passord |
| 280 | `encrypting_files` | Encrypting files… | Krypterer filer… |
| 281 | `encrypting_folder` | Encrypting folder… | Krypterer mappe… |
| 282 | `folder_encrypted_successfully` | Folder encrypted successfully | Mappa vart kryptert |
| 283 | `file_encrypted_successfully` | File encrypted successfully | Fila vart kryptert |
| 284 | `files_encrypted_successfully` | Files encrypted successfully | Filene vart krypterte |
| 285 | `decrypting_folder` | Decrypting folder… | Dekrypterer mappe… |
| 286 | `folder_decrypted_successfully` | Folder decrypted successfully | Mappa vart dekryptert |
| 287 | `file_decrypted_successfully` | File decrypted successfully | Fila vart dekryptert |
| 288 | `files_decrypted_successfully` | Files decrypted successfully | Filene vart dekrypterte |
| 289 | `decrypting_files` | Decrypting files… | Dekrypterer filer… |
| 290 | `place_finger_prompt` | Please place your finger on the fingerprint sensor | Plasser fingeren på fingeravtrykkssensoren |
| 291 | `connect_at_least_4_dots_prompt` | Connect at least 4 dots | Kopla saman minst 4 punkt |
| 292 | `access_existing_secrets_prompt` | Do you want to access your existing Secrets folder? | Vil du få tilgang til den eksisterande Secrets-mappa di? |
| 293 | `delete_and_recreate_secrets_prompt` | Existing Secrets folder will be deleted and a new one will be created. Are you sure? | Eksisterande Secrets-mappe vert sletta og ei ny vert laga. Er du sikker? |
| 294 | `confirm_checkbox_label` | I confirm | Eg stadfestar |
| 295 | `secret_vault` | Encrypted Vault | Kryptert kvelv |
| 296 | `secret_vault_empty` | Encrypted Vault is empty | Det krypterte kvelvet er tomt |
| - | `press_back_again_to_exit_vault` | Press back again to exit | Trykk tilbake igjen for å avslutta |
| - | `block_vault_screenshots` | Block screenshots in encrypted vault | Blokker skjermbilete i det krypterte kvelvet |
| 297 | `confirm_decrypt_hidden_file` | Are you sure you want to decrypt the hidden file(s)? | Er du sikker på at du vil dekryptera den/dei skjulte fila/filene? |
| 298 | `copy_to_clipboard_disabled_in_vault` | Copying to clipboard is disabled while vault is unlocked for security. | Kopiering til utklippstavla er deaktivert av tryggleiksomsyn medan kvelvet er låst opp. |
| 299 | `stop_showing_hidden` | Stop showing encrypted media | Slutt å syna krypterte medium |
| 300 | `system_default` | System default | Systemstandard |
| 301 | `language` | Language | Språk |
| 302 | `open_system_language_settings` | Open system language settings | Opna språkinnstillingane i systemet |
| 303 | `access_hidden_items_with_biometrics` | Access hidden items with biometrics | Få tilgang til skjulte element med biometri |
| 304 | `enable_hidden_protection_first_prompt` | Please enable hidden item password protection first | Ver venleg og aktiver passordvern for skjulte element fyrst |
| 305 | `biometric_unlock_vault` | Unlock encrypted vault | Lås opp kryptert kvelv |
| 306 | `biometric_unlock_prompt` | Verify your biometric identity to access hidden items | Stadfest den biometriske identiteten din for å få tilgang til skjulte element |
| 307 | `biometric_setup_prompt` | Confirm your biometric identity to enable biometric vault access | Stadfest den biometriske identiteten din for å aktivera biometrisk tilgang til kvelvet |
| 308 | `use_password_or_pin` | Use PIN / Password | Bruk PIN / Passord |
| 309 | `biometric_key_invalidated` | Biometric credentials have changed. Please enter your PIN or password. | Biometriske data er endra. Ver venleg og skriv inn PIN eller passord. |
| 310 | `no_biometric_hardware_or_enrolled` | No biometric hardware or enrolled credentials found on this device | Fann inga biometrisk maskinvare eller registrerte biometriske data på denne eininga |
| 311 | `default_hidden_album` | Default Secret Album | *(non-translatable in `values/strings.xml`)* |
| 312 | `select_hidden_album` | Select Encrypted Album | Vel kryptert album |
| 313 | `create_new_hidden_album` | Create New Encrypted Album | Opprett nytt kryptert album |
| 314 | `new_hidden_album` | New encrypted album | Nytt kryptert album |
| 315 | `hidden_album_name_empty` | Album name cannot be empty | Albumnamn kan ikkje vera tomt |
| 316 | `file_hidden_to_album` | File encrypted to \"%s\" | Fil kryptert i «%s» |
| 317 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d filer krypterte i «%2$s» |
| 318 | `restore_confirmation` (`one`) | Are you sure you want to restore %d file? | Er du sikker på at du vil gjenoppretta %d fil? |
| 319 | `restore_confirmation` (`other`) | Are you sure you want to restore %d files? | Er du sikker på at du vil gjenoppretta %d filer? |
| 320 | `failed_to_resize_images` (`one`) | Failed to resize %d image | Klarte ikkje å endra storleik på %d bilete |
| 321 | `failed_to_resize_images` (`other`) | Failed to resize %d images | Klarte ikkje å endra storleik på %d bilete |
| 322 | `search` *(fossify-commons)* | *(inherited from commons)* | Søk |
| 323 | `change_date_and_time_format` *(fossify-commons)* | Change date and time format | Endra format for dato og klokkeslett |
| 324 | `password_protect_file_deletion` *(fossify-commons)* | Password protect file deletion and moving | Passordvern for sletting og flytting av filer |
| 325 | `keep_last_modified` *(fossify-commons)* | Keep old last-modified value at file operations | Behald gammal sist-endra-verdi ved filoperasjonar |
| 326 | `clear_cache` *(fossify-commons)* | Clear cache | Tøm mellomlager |
| 327 | `export_settings` *(fossify-commons)* | Export settings | Eksporter innstillingar |
| 328 | `import_settings` *(fossify-commons)* | Import settings | Importer innstillingar |
| 329 | `temporarily_show_excluded` *(fossify-commons)* | Temporarily show excluded | Syn ekskluderte mellombels |
| 330 | `change_view_type` *(fossify-commons)* | Change view type | Endra syningstype |
| 331 | `contributors` *(fossify-commons)* | Contributors | Bidragsytarar |
| 332 | `known_issues` *(fossify-commons)* | Known issues | Kjende problem |
| 333 | `donate_to_fossify` *(fossify-commons)* | Donate to Fossify | Doner til Fossify |
| 334 | `privacy_policy` *(fossify-commons)* | Privacy policy | Personvernerklæring |
| 335 | `create_shortcut` *(fossify-commons)* | Create shortcut | Lag snarveg |
| 336 | `print` *(fossify-commons)* | Print | Skriv ut |
| 337 | `search_files` *(fossify-commons)* | Search files | Søk i filer |
| 338 | `search_folders` *(fossify-commons)* | Search folders | Søk i mapper |
| 339 | `skip_the_recycle_bin` *(fossify-commons)* | Skip the recycle bin, delete files directly | Hopp over papirkorga og slett filene direkte |
| 340 | `skip_delete_confirmation` *(fossify-commons)* | Always skip delete confirmation dialog | Hopp alltid over dialogen for stadfesting av sletting |
| 341 | `pull_down_to_open_vault` | Keep pulling down for Encrypted Vault | Hald fram med å dra ned for kryptert kvelv |
| 342 | `keep_holding_to_open_vault` | Keep holding to open Encrypted Vault | Hald fram med å halde for å opne kryptert kvelv |
| 343 | `release_to_open_vault` | Release to open Encrypted Vault | Slepp for å opne kryptert kvelv |
| 344 | `manage_excluded_folders` *(fossify-commons)* | Manage excluded folders | Handter ekskluderte mapper |
| 345 | `exclude` *(fossify-commons)* | Exclude | Ekskluder |
| 346 | `exclude_folder` *(fossify-commons)* | Exclude folder | Ekskluder mappe |
| 347 | `lock_folder` *(fossify-commons)* | Lock folder | Lås mappe |
| 348 | `change_cover_image` *(fossify-commons)* | Change cover image | Endra omslagsbilete |
| 349 | `select_photo` *(fossify-commons)* | Select photo | Vel bilete |
| 350 | `use_default` *(fossify-commons)* | Use default | Bruk standard |
| 351 | `column_count` *(fossify-commons)* | Column count | Tal på kolonnar |
| 352 | `open_the_recycle_bin` *(fossify-commons)* | Open the recycle bin | Opna papirkorga |
| 353 | `grid` *(fossify-commons)* | Grid | Rutenett |
| 354 | `list` *(fossify-commons)* | List | Liste |
| 355 | `resize` *(fossify-commons)* | Resize | Endra storleik |
| 356 | `version_placeholder` *(fossify-commons)* | Version %s | Versjon %s |
| 357 | `number_of_items` *(fossify-commons)* | Number of items | Tal på element |
| 358 | `random` *(fossify-commons)* | Random | Tilfeldig |
| 359 | `shuffle` | Shuffle | Bland |
| 360 | `stop_showing_excluded` *(fossify-commons)* | Stop showing excluded | Slutt å vise ekskluderte |
| 361 | `copy_to_clipboard` *(fossify-commons)* | Copy to clipboard | Kopier til utklippstavla |
| 362 | `encrypt` | Encrypt | Krypter |
| 363 | `decrypt` | Decrypt | Dekrypter |
| 364 | `encrypt_album` | Encrypt album | Krypter album |
| 365 | `decrypt_album` | Decrypt album | Dekrypter album |
| 366 | `create_new_encrypted_virtual_album` | Create new encrypted virtual album | Opprett nytt kryptert virtuelt album |
| 367 | `new_album` | New album | Nytt album |
| 368 | `filename_without_zip` | Filename (without .zip) | Filnamn (utan .zip) |
| 369 | `favorites_already_exist` | You already have a favorites list | Du har allereie ei favorittliste |
| 370 | `delete_existing_list` | Delete existing list | Slett eksisterande liste |
| 371 | `merge_lists` | Merge lists | Slå saman lister |
| 372 | `confirm` | Confirm | Stadfest |
| 373 | `confirm_encrypt_album` | Are you sure you want to encrypt this album? All of its media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | Er du sikker på at du vil kryptera dette albumet? Alle mediefilene i det vert krypterte og flytte til det krypterte kvelvet. Originalfilene vert sletta. |
| 374 | `confirm_encrypt_albums` | Are you sure you want to encrypt the selected albums? All of their media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | Er du sikker på at du vil kryptera dei valde albuma? Alle mediefilene i dei vert krypterte og flytte til det krypterte kvelvet. Originalfilene vert sletta. |
| 375 | `confirm_encrypt_file` | Are you sure you want to encrypt this file? It will be encrypted and moved to the encrypted vault. The original file will be deleted. | Er du sikker på at du vil kryptera denne fila? Ho vert kryptert og flytt til det krypterte kvelvet. Originalfila vert sletta. |
| 376 | `confirm_encrypt_files` | Are you sure you want to encrypt the selected files? They will be encrypted and moved to the encrypted vault. The original files will be deleted. | Er du sikker på at du vil kryptera dei valde filene? Dei vert krypterte og flytte til det krypterte kvelvet. Originalfilene vert sletta. |
| 377 | `confirm_encrypt_files_to_vault` | The selected files will be encrypted when they are copied into the encrypted vault. Do you want to continue? | Dei valde filene vert krypterte når dei vert kopierte til det krypterte kvelvet. Vil du halda fram? |
| 378 | `encrypt_decrypt_notifications` | Encrypt / Decrypt | Krypter / Dekrypter |
| 379 | `allow_batch_renaming` | Allow batch renaming when multiple files are selected | Tillat masseendring av namn når fleire filer er valde |
| 380 | `keep_original_remove_from_virtual_album` | Keep the original file, remove it only from this virtual album | Behald originalfila, fjern henne berre frå dette virtuelle albumet |
| 381 | `keep_original_remove_from_favorites` | Keep the original file, remove it only from favorites | Behald originalfila, fjern henne berre frå favorittar |
| 382 | `delete_original_from_favorites_and_virtual_albums` | Delete the original version of a file when it is deleted from favorites and virtual albums | Slett originalversjonen av ei fil når ho vert sletta frå favorittar og virtuelle album |
| 383 | `cannot_target_encrypted_album` | Encrypted albums cannot be targeted for this operation | Krypterte album kan ikkje veljast som mål for denne handlinga |
| 384 | `cannot_mix_encrypted_and_unencrypted` | Cannot combine encrypted and unencrypted items in this operation | Kan ikkje kombinera krypterte og ukrypterte element i denne handlinga |
| 385 | `lock_album` | Lock album | Lås album |
| 386 | `unlock_album` | Unlock album | Lås opp album |
| 387 | `auto_backup_prompt_message` | Do you want your favorites and virtual albums to be regularly written to system storage outside of app memory? This will ensure that you do not lose your favorites list and virtual albums if you uninstall the app. You can manually create a backup from app settings at any time. | Vil du at favorittane og dei virtuelle albuma dine skal lagrast regelmessig i systemlagringa utanfor appminnet? Dette sikrar at du ikkje mistar favorittlista og dei virtuelle albuma dine om du avinstallerer appen. Du kan når som helst laga ei tryggingskopi manuelt frå appinnstillingane. |
| 388 | `auto_backup_favorites_and_virtual_albums` | Back up favorites and virtual albums to device storage | Tryggingskopier favorittar og virtuelle album til einingslagringa |
| 389 | `access_storage_prompt` *(fossify-commons)* | Please grant our app access to all your files, it might not work well without it. | Ver venleg og gje appen vår tilgang til alle filene dine, han fungerer kanskje ikkje så godt utan dette. |
| 390 | `encrypt_info_title` | How Encryption Works | Korleis kryptering fungerer |
| 391 | `encrypt_info_message` | Folder & File Structure:\n• When an unencrypted album is encrypted, it is converted into an encrypted subfolder inside the vault (Secrets).\n• Both the folder name and all file names/contents inside it are encrypted, so nothing is readable outside the app.\n\nIf an Album with the Same Name Already Exists:\n• By default, the app asks whether to create a new album such as Album(1) or merge into the existing encrypted album (configurable in Settings).\n• When merging, you can choose whether source files verified 100% bit-identical to the destination are deleted or kept in the source folder.\n• Different files with the same name (e.g. download.jpg) are kept as download(1).jpg or handled according to your conflict resolution setting. | Mappe- og filstruktur:\n• Når eit ukryptert album blir kryptert, blir det gjort om til ei kryptert undermappe inne i kvelvet (Secrets).\n• Både mappenamnet og alle filnamn og filinnhald inni det blir krypterte, slik at ingenting er leseleg utanfor appen.\n\nViss eit album med same namn alt finst:\n• Som standard spør appen om du vil oppretta eit nytt album som Album(1) eller slå saman med det eksisterande krypterte albumet (kan konfigurerast i Innstillingar).\n• Når du slår saman, kan du velja om kjeldefiler som er stadfesta 100% bit-for-bit identiske med målet skal slettast eller behaldast i kjeldemappa.\n• Ulike filer med same namn (t.d. download.jpg) blir behaldne som download(1).jpg eller handterte i samsvar med innstillinga di for konfliktløysing. |
| 392 | `decrypt_info_title` | How Decryption Works | Korleis dekryptering fungerer |
| 393 | `decrypt_info_message` | Folder & File Structure:\n• When an encrypted album or file is decrypted, it is restored with its original name and format to the selected destination folder and removed from the encrypted vault.\n\nIf an Album with the Same Name Already Exists:\n• By default, the app asks whether to create a new folder such as Album(1) or merge into the existing destination folder (configurable in Settings).\n• When merging, you can choose whether encrypted copies verified 100% bit-identical to the destination are removed from the vault or kept.\n• Different files with the same name (e.g. download.jpg) are kept as download(1).jpg or handled according to your conflict resolution setting. | Mappe- og filstruktur:\n• Når eit kryptert album eller ei kryptert fil blir dekryptert, blir det/ho gjenoppretta med opphavleg namn og format til den valde målmappa og fjerna frå det krypterte kvelvet.\n\nViss eit album med same namn alt finst:\n• Som standard spør appen om du vil oppretta ei ny mappe som Album(1) eller slå saman med den eksisterande målmappa (kan konfigurerast i Innstillingar).\n• Når du slår saman, kan du velja om krypterte kopiar som er stadfesta 100% bit-for-bit identiske med målet skal fjernast frå kvelvet eller behaldast.\n• Ulike filer med same namn (t.d. download.jpg) blir behaldne som download(1).jpg eller handterte i samsvar med innstillinga di for konfliktløysing. |
| 394 | `vault_location` | Vault location | Kvelvplassering |
| 395 | `vault_location_standard_summary` | Standard (Internal Storage) | Standard (Intern lagring) |
| 396 | `vault_location_unavailable_summary` | External drive disconnected (%s) | Ekstern stasjon fråkopla (%s) |
| 397 | `select_vault_location` | Select vault location | Vel kvelvplassering |
| 398 | `vault_setup_action_title` | Encrypted vault | Kryptert kvelv |
| 399 | `create_new_vault_option` | Create new vault | Opprett nytt kvelv |
| 400 | `use_existing_vault_option` | Use existing vault | Bruk eksisterande kvelv |
| 401 | `where_to_create_vault_title` | Where should the vault be created? | Kvar skal kvelvet opprettast? |
| 402 | `vault_location_default_short` | Default location | Standardplassering |
| 403 | `vault_location_custom_short` | Custom Location | Eigendefinert plassering |
| 404 | `no_existing_vault_at_location` | No existing encrypted vault (.vault) was found in the selected folder. | Ingen eksisterande kryptert kvelv (.vault) vart funnen i den valde mappa. |
| 405 | `unlock_existing_vault_at_location_prompt` | An existing encrypted vault was found at %s. Do you want to unlock and use it? Tap No to pick a different folder. | Eit eksisterande kryptert kvelv vart funne på %s. Vil du låsa det opp og bruka det? Trykk på Nei for å velja ei anna mappe. |
| 406 | `vault_location_standard_option` | Standard location (Internal Storage > Calypso Gallery > Secrets) | Standardplassering (Intern lagring > Calypso Gallery > Secrets) |
| 407 | `vault_location_custom_option` | Custom / External location (USB drive, SD card, or custom folder) | Eigendefinert / Ekstern plassering (USB-stasjon, SD-kort eller eigendefinert mappe) |
| 408 | `external_vault_unavailable_title` | External vault unavailable | Eksternt kvelv utilgjengeleg |
| 409 | `external_vault_unavailable_prompt` | Your configured vault location (%s) is currently disconnected or unavailable. Plug in the drive and tap Retry, or choose another vault location. | Den konfigurerte kvelvplasseringa di (%s) er for tida fråkopla eller utilgjengeleg. Kopla til stasjonen og trykk på Prøv på nytt, eller vel ei anna kvelvplassering. |
| 410 | `retry` | Retry | Prøv på nytt |
| 411 | `change_vault_location` | Change location | Endra plassering |
| 412 | `existing_vault_detected_at_location` | Existing vault found at %s. Enter its password or PIN to unlock it. | Eksisterande kvelv funne på %s. Skriv inn passordet eller PIN-koden for å låsa det opp. |
| 413 | `create_new_vault_at_location_prompt` | No vault exists at %s. Do you want to create a new encrypted vault at this location? | Det finst ikkje noko kvelv på %s. Vil du oppretta eit nytt kryptert kvelv på denne plasseringa? |
| 414 | `usb_direct_filesystem_required` | This USB path is only exposed through Android\'s virtual OTG provider and cannot be accessed directly as a filesystem path. Please choose a mounted storage directory or a folder on internal storage / SD card. | Denne USB-stien er berre eksponert gjennom Android sin virtuelle OTG-leverandør og kan ikkje opnast direkte som ein filsystemsti. Ver venleg og vel ein montert lagringskatalog eller ei mappe på intern lagring / SD-kort. |
| 415 | `vault_location_not_writable` | The selected folder does not exist or is not writable on this device. | Den valde mappa finst ikkje eller kan ikkje skrivast til på denne eininga. |
| 416 | `vault_location_not_readable` | The selected folder does not exist or is not readable on this device. | Den valde mappa finst ikkje eller kan ikkje lesast på denne eininga. |
| 417 | `sync_vault_from_external` | Import newly encrypted media filed to encrypted section | Importer krypterte mediefiler til kryptert seksjon |
| 418 | `sync_vault_select_source_prompt` | How Encrypted Media Import Works:\n\n1. Unified Secrets Folder:\nKeep a folder named Secrets on your external drive (USB / SD). You can either keep a full backup copy of your vault there, or place only your vault\'s .vault header file inside it.\n\n2. Encrypting New Media (PC / Tails CLI):\nThe offline CLI tool automatically saves new encrypted files directly into Secrets. Files placed at the root go to Default Secret Album; files in subfolders go to matching encrypted albums (or create new ones).\n\n3. Bit-by-Bit Smart Sync:\nSelect your external drive or Secrets folder next. The app compares the external drive with your phone vault in RAM:\n• Only new files and albums are imported.\n• Bit-identical files already in your vault are skipped.\n• Different files with the same name (e.g. download.jpg) are safely imported as download(1).jpg. | Slik fungerer import av krypterte medium:\n\n1. Einskapleg Secrets-mappe:\nHa ei mappe med namnet Secrets på den eksterne stasjonen din (USB / SD). Der kan du anten ha ei fullstendig tryggingskopi av kvelvet ditt, eller berre leggja inn kvelvet si .vault-headerfil.\n\n2. Kryptering av nye medium (PC / Tails CLI):\nDet fråkopla CLI-verktøyet lagrar automatisk nye krypterte filer direkte i Secrets. Filer i rotmappa hamnar i Default Secret Album; filer i undermapper hamnar i tilsvarande krypterte album (eller opprettar nye).\n\n3. Bit-for-bit smart synkronisering:\nVel den eksterne stasjonen eller Secrets-mappa nedanfor. Appen samanliknar den eksterne stasjonen med telefonen sitt kvelv i RAM:\n• Berre nye filer og album blir importerte.\n• Bit-identiske filer som alt finst i kvelvet blir hoppa over.\n• Ulike filer med same namn (t.d. download.jpg) blir importerte trygt som download(1).jpg. |
| 419 | `conflict_handling_mode_label` | File conflict & duplicate resolution | Løysing av filkonfliktar og duplikat |
| 420 | `conflict_handling_auto_short` | Automatic (Experimental — Auto-skip identical, rename different as (1)) | Heilautomatisk (Standard — Hopp over identiske, gje ulike nytt namn som (1)) |
| 421 | `conflict_handling_smart_dialog_short` | Smart dialog (Auto-skip identical, ask when content differs) | Smart dialog (Hopp over identiske automatisk, spør når innhaldet er ulikt) |
| 422 | `album_crypto_conflict_mode_label` | Album conflict (Encrypt / Decrypt) | Albumkonflikt (Kryptera / Dekryptera) |
| 423 | `album_crypto_conflict_ask` | Always ask (Default) | Spør alltid (Standard) |
| 424 | `album_crypto_conflict_merge` | Merge into existing album | Slå saman med eksisterande album |
| 425 | `album_crypto_conflict_merge_delete_identical` | Merge into existing album and automatically delete identical sources | Slå saman med eksisterande album og slett identiske kjelder automatisk |
| 426 | `album_crypto_conflict_rename` | Create new album as (1) | Opprett nytt album som (1) |
| 427 | `album_crypto_conflict_title` | Album Already Exists | Albumet finst alt |
| 428 | `album_crypto_conflict_message` | An album named \"%s\" already exists at the destination. How would you like to proceed? | Eit album med namnet \"%s\" finst alt på målet. Korleis vil du halda fram? |
| 429 | `album_crypto_conflict_message_multiple` | %1$d selected albums already exist at the destination. How would you like to proceed?\n\nYour choice will apply to all conflicting albums. To choose separately per album, select and process them one by one. | %1$d valde album finst alt på målet. Korleis vil du halda fram?\n\nValet ditt vil gjelda for alle album med konflikt. For å velja separat per album, vel og handsam dei eitt og eitt. |
| 430 | `album_crypto_conflict_rename_option` | Create new album as \"%s\" | Opprett nytt album som \"%s\" |
| 431 | `album_crypto_conflict_rename_option_multiple` | Create new albums as (1), (2), … | Opprett nye album som (1), (2), … |
| 432 | `album_crypto_conflict_merge_option` | Merge into existing \"%s\" | Slå saman inn i eksisterande \"%s\" |
| 433 | `album_crypto_conflict_merge_option_multiple` | Merge into existing albums | Slå saman inn i eksisterande album |
| 434 | `album_crypto_delete_identical_source` | Delete identical source files (100% bit-by-bit verified) | Slett identiske kjeldefiler (100% bit-for-bit stadfesta) |
| 435 | `album_crypto_dont_ask_again` | Remember album conflict choice (merge or rename) | Hugs val for albumkonflikt (slå saman eller gje nytt namn) |
| 436 | `album_crypto_merge_identical_title` | Identical Files During Merge | Identiske filer ved samanslåing |
| 437 | `album_crypto_merge_identical_message` | When merging into \"%1$s\", if any files are already 100%% bit-identical at the destination, should their source copies be deleted or kept? | Når du slår saman inn i \"%1$s\", viss nokre filer alt er 100%% bit-identiske på målet, skal kjeldekopiane slettast eller behaldast? |
| 438 | `album_crypto_merge_identical_message_multiple` | When merging the %1$d conflicting albums, if any files are already 100%% bit-identical at the destination, should their source copies be deleted or kept? | Når du slår saman dei %1$d albuma med konflikt, viss nokre filer alt er 100%% bit-identiske på målet, skal kjeldekopiane slettast eller behaldast? |
| 439 | `delete_identical_sources` | Delete identical | Slett identiske |
| 440 | `keep_identical_sources` | Keep in source | Behald i kjelde |
| 441 | `fast_large_duplicate_check_label` | Fast duplicate check for large files (>8 MB) | Rask duplikatsjekk for store filer (>8 MB) |
| 442 | `fast_large_duplicate_check_hint` | Crucial for transfers containing large files: checks 5 blocks (1.25 MB total) per file instead of reading whole files, speeding up duplicate checks dozens of times. | Avgjerande for overføringar med store filer: sjekkar 5 blokker (1,25 MB totalt) per fil i staden for å lesa heile filer, noko som gjer duplikatsjekken titals gonger raskare. |
| 443 | `fast_large_duplicate_check_info_title` | Fast Duplicate Check (>8 MB) | Rask duplikatsjekk (>8 MB) |
| 444 | `conflict_handling_explanation_title` | File Conflict & Duplicate Resolution | Løysing av filkonfliktar og duplikat |
| 445 | `conflict_handling_explanation_message` | When copying, moving, encrypting, or decrypting files and albums (including album merges), if a file with the same name already exists at the destination:\n\n• Standard dialog (Default): Checks filename only (like the original Fossify Gallery app) without comparing file contents, and asks you to Skip / Overwrite / Keep both.\n• Smart dialog: Checks file sizes and bit-by-bit contents in RAM (encrypted files stay in memory), auto-skips identical duplicates, and asks with side-by-side previews (Skip / Overwrite / Keep both) only when content differs.\n• Automatic (Experimental): Auto-skips identical duplicates and automatically keeps both different files by naming the incoming file download(1).jpg, download(2).jpg. | Når du kopierer, flyttar, krypterer eller dekrypterer filer og album, og ei fil med same namn alt finst på målet:\n\n1. Bit-for-bit duplikatsjekk (RAM):\nAppen sjekkar filstorleikane først, og viss storleikane samsvarar, blir filinnhaldet samanlikna bit-for-bit i RAM (krypterte filer blir sjekka i minnet utan å skriva klartekst til disk). Under kopiering/flytting blir identiske filer hoppa over og originalen blir verande urørt i kjeldemappa. Når du slår saman eit album under kryptering/dekryptering, kan du velja om 100% bit-identiske kjeldefiler skal slettast eller behaldast.\n\n2. Same namn, ulikt innhald (t.d. to ulike download.jpg-filer):\n• Heilautomatisk (Standard): Behaldar begge filene utan å avbryta deg ved å gje den nye fila namnet download(1).jpg, download(2).jpg.\n• Smart dialog: Hoppar framleis stille over bit-identiske filer, og spør berre (Hopp over / Skriv over / Behald begge) når to filer har same namn, men ulikt innhald. |
| 446 | `syncing_vault_progress` | Syncing vault from external drive… | Synkroniserer kvelv frå ekstern stasjon… |
| 447 | `vault_sync_summary_message` | Vault sync complete:\n• Copied: %1$d\n• Renamed (name conflict): %2$d\n• Skipped (identical): %3$d\n• Failed: %4$d | Kvelvsynkronisering fullført:\n• Kopiert: %1$d\n• Gjeve nytt namn (namnekonflikt): %2$d\n• Hoppa over (identisk): %3$d\n• Mislykkast: %4$d |
| 448 | `vault_sync_nothing_to_sync` | No pending CLI-encrypted media (.pending_sync) was found in the selected folder. | Ingen ventande CLI-krypterte medium (.pending_sync) vart funne i den valde mappa. |
| 449 | `vault_sync_same_folder_error` | The selected source folder is the same as your active vault. | Den valde kjeldemappa er den same som det aktive kvelvet ditt. |
| 450 | `use_for_this_album` | Use for this album only | Bruk berre for dette albumet |
| 451 | `crypto_duplicate_source_mode_label` | When a file already exists in destination during encrypt/decrypt | Når ei fil alt finst i målmappa under kryptering/dekryptering |
| 452 | `crypto_duplicate_source_ask` | Always ask (Default) | Spør alltid (Standard) |
| 453 | `crypto_duplicate_source_keep` | Keep file in source folder | Behald fila i kjeldemappa |
| 454 | `crypto_duplicate_source_delete` | Delete file from source folder | Slett fila frå kjeldemappa |
| 455 | `crypto_duplicate_source_title` | File Already Exists in Destination | Fila finst alt i målmappa |
| 456 | `crypto_duplicate_source_encrypt_message` | Some of the files you are encrypting already exist in the destination folder. What should happen to the files in the source folder? | Nokre av filene du krypterer finst alt i målmappa. Kva skal skje med filene i kjeldemappa? |
| 457 | `crypto_duplicate_source_decrypt_message` | Some of the files you are decrypting already exist in the destination folder. What should happen to the files in the source folder? | Nokre av filene du dekrypterer finst alt i målmappa. Kva skal skje med filene i kjeldemappa? |
| 458 | `crypto_duplicate_source_encrypt_keep_warning` | Warning: Choosing \"Keep\" for unencrypted files will leave the source file unencrypted on disk. | Åtvaring: Om du vel «Behald» for ukrypterte filer, vil kjeldefila bli liggjande ukryptert på disken. |
| 459 | `crypto_duplicate_source_always_ask` | Always ask | Spør alltid |
| 460 | `export_encrypted_vault` | Export encrypted vault | Eksporter kryptert kvelv |
| 461 | `export_vault_only_header` | Export folder containing only .vault file | Eksporter mappe som berre inneheld .vault-fil |
| 462 | `export_vault_entire_folder` | Export entire folder | Eksporter heile mappa |
| 463 | `export_vault_only_header_info` | To add media items encrypted on other devices using the supported CLI tool to the encrypted vault on your phone, you only need to use a folder named Secrets containing the .vault file. | For å leggja til medieelement som er krypterte på andre einingar med det støtta CLI-verktøyet i det krypterte kvelvet på telefonen din, treng du berre å bruka ei mappe kalla Secrets som inneheld .vault-fila. |
| 464 | `exporting_vault_progress` | Exporting encrypted vault… | Eksporterer kryptert kvelv… |
| 465 | `vault_export_same_folder_error` | The export destination cannot be the active vault folder or inside it. | Eksportmålet kan ikkje vera den aktive kvelvmappa eller liggja inne i ho. |
| 466 | `conflict_handling_standard_dialog_summary` | Standard dialog | Standard dialog |
| 467 | `conflict_handling_smart_dialog_summary` | Smart dialog | Smart dialog |
| 468 | `conflict_handling_auto_summary` | Automatic (Experimental) | Automatisk (eksperimentelt) |
| 469 | `conflict_handling_standard_dialog_short` | Standard dialog (Default — Checks filename only, like original Fossify Gallery) | Standard dialog (Standard — Sjekkar berre filnamnet, som den opphavlege Fossify Gallery) |
| 470 | `skipped_encrypt_files_mode_label` | Manage skipped files during encryption | Handsama filer som vart hoppa over under kryptering |
| 471 | `skipped_encrypt_files_info_message` | Choose whether files skipped in the source folder during encryption should be kept or deleted for security. This choice also applies to album conflicts during album encryption (see setting below) when the source album is merged into an existing album at the destination. | Vel om filer som vart hoppa over i kjeldemappa under kryptering skal behaldast eller slettast av tryggleiksomsyn. Dette valet gjeld òg for albumkonfliktar under albumkryptering (sjå innstillinga under) når kjeldealbumet vert slått saman med eit eksisterande album på målet. |
| 472 | `skipped_crypto_files_ask` | Always ask (Default) | Spør alltid (Standard) |
| 473 | `skipped_crypto_files_keep` | Keep | Behald |
| 474 | `skipped_crypto_files_delete` | Delete | Slett |
| 475 | `skipped_crypto_files_encrypt_message` | The files you skipped were not transferred. For your privacy, you can delete these files. | Filene du hoppa over vart ikkje overførte. For personvernet ditt kan du sletta desse filene. |
| 476 | `skipped_crypto_files_decrypt_message` | The files you skipped were not transferred. You can delete these files. | Filene du hoppa over vart ikkje overførte. Du kan sletta desse filene. |
| 477 | `skipped_crypto_files_identical_encrypt_message` | Files that already exist in the destination folder were not transferred. For your privacy, you can delete these files. | Filer som alt finst i målmappa vart ikkje overførte. For personvernet ditt kan du sletta desse filene. |
| 478 | `skipped_crypto_files_identical_decrypt_message` | Files that already exist in the destination folder were not transferred. You can delete these files. | Filer som alt finst i målmappa vart ikkje overførte. Du kan sletta desse filene. |
| 479 | `skipped_crypto_files_both_encrypt_message` | The files you skipped and the files that already exist in the destination folder were not transferred. For your privacy, you can delete these files. | Filene du hoppa over og filene som alt finst i målmappa vart ikkje overførte. For personvernet ditt kan du sletta desse filene. |
| 480 | `skipped_crypto_files_both_decrypt_message` | The files you skipped and the files that already exist in the destination folder were not transferred. You can delete these files. | Filene du hoppa over og filene som alt finst i målmappa vart ikkje overførte. Du kan sletta desse filene. |
| 481 | `skipped_crypto_files_always_ask` | Always ask | Spør alltid |
| 482 | `delete_skipped_files_confirm_title` | Are you sure? | Er du sikker? |
| 483 | `delete_skipped_files_confirm_message` | Your files will be deleted! | Filene dine vert sletta! |
| 484 | `date_tie_break_label` | When content is identical but dates differ | Når innhaldet er identisk, men datoen er ulik |
| 485 | `date_tie_break_keep_target` | Keep the existing file\'s date (default) | Behald datoen til den eksisterande fila (standard) |
| 486 | `date_tie_break_use_source` | Use the source file\'s date | Bruk datoen til kjeldefila |
| 487 | `date_tie_break_ask` | Always ask | Spør alltid |
| 488 | `date_tie_break_hint` | Applies only to Smart dialog and Automatic mode. The Standard dialog is not affected. | Gjeld berre for Smart dialog og automatisk modus. Standard dialog vert ikkje påverka. |
| 489 | `date_tie_break_ask_info_title` | Why "Always ask" disables Smart dialog and Automatic mode | Kvifor \"Spør alltid\" slår av Smart dialog og automatisk modus |
| 490 | `date_tie_break_ask_info_message` | Smart dialog and Automatic mode exist for one reason: to skip files whose content is already present, so you only see the files that really need your attention — same name, different content. That is exactly the case where comparing thumbnails can reveal bit-rot.\n\nIf you choose "Always ask" here, every identical file with a different date comes back to you as a conflict. In Smart dialog you will see two thumbnails of the very same picture, with nothing to verify, and you can decide there. Automatic (Experimental) mode never shows a dialog, so "Always ask" is not available there — selecting Automatic falls back to "Keep the existing file's date".\n\nUse this option only if you deliberately want to review every date difference, for example to fix a wrong date by hand.\n\nNote: when content is bit-identical, the EXIF capture date is identical too. A date difference means one side lost its EXIF data and fell back to the file modification date (the copy date). | Smart dialog og automatisk modus finst av éin grunn: å hoppa over filer som alt er til stades, slik at du berre ser filene som verkeleg treng merksemda di — same namn, ulikt innhald. Det er nettopp i dette tilfellet at samanlikning av minibilete kan avdekkja bit-rot.\n\nViss du vel \"Spør alltid\" her, kjem kvar identisk fil med ein annan dato tilbake til deg som ein konflikt. I Smart dialog ser du to minibilete av det same biletet, utan noko å stadfesta, og kan avgjera der. Automatisk modus (eksperimentell) viser aldri dialog, så \"Spør alltid\" er ikkje tilgjengeleg der – når du vel Automatisk, blir det falla tilbake til \"Behald datoen til den eksisterande fila\".\n\nBruk dette valet berre viss du med vilje vil gå gjennom kvar einaste datoskilnad, til dømes for å retta ein feil dato for hand.\n\nMerk: når innhaldet er bit-identisk, er EXIF-opptaksdatoen òg identisk. Ein datoskilnad tyder at den eine sida mista EXIF-dataa sine og fall tilbake til endringsdatoen til fila (kopieringsdatoen). |
| 491 | `smart_conflict_counter` | %1$s (%2$d / %3$d) | %1$s (%2$d / %3$d) |
| 492 | `smart_conflict_incoming_label` | Incoming (New) | Innkommande (ny) |
| 493 | `smart_conflict_existing_label` | Existing (Target) | Eksisterande (mål) |
| 494 | `smart_conflict_corrupted_unreadable` | Corrupted or unreadable | Øydelagd eller uleseleg |
| 495 | `smart_conflict_integrity_failed` | Integrity check failed (Bit-rot / Corrupted) | Integritetssjekk feila (bit-rot / øydelagd) |
| 496 | `smart_conflict_tap_to_preview` | Tap either thumbnail to inspect full-size preview | Trykk på eit av minibileta for å sjå førehandsvisinga i full storleik |
| 497 | `smart_conflict_comparing` | Comparing file contents and dates… | Samanliknar filinnhald og datoar… |
| 498 | `smart_conflict_content_differs_warning` | File contents differ.\nIf these two files were supposed to be the same, data corruption may have occurred. Keeping both and inspecting them is recommended. | Filinnhaldet er ulikt.\nViss desse to filene skulle vore like, kan det ha oppstått dataskade. Det tilrår å behalda begge og granska dei. |
| 499 | `smart_conflict_date_taken_differs` | File contents are identical, creation (date taken) timestamps differ. | Filinnhaldet er identisk, men tidsstempla for oppretting (dato teken) er ulike. |
| 500 | `smart_conflict_modified_date_differs` | File contents are identical, modification dates differ. | Filinnhaldet er identisk, men endringsdatoane er ulike. |
| 501 | `smart_conflict_both_dates_differ` | File contents are identical, creation and modification dates differ. | Filinnhaldet er identisk, men opprettings- og endringsdatoane er ulike. |
| 502 | `smart_conflict_meta_date_taken` | Taken: %s | Teke: %s |
| 503 | `smart_conflict_meta_date_modified` | Modified: %s | Endra: %s |
| 504 | `album_type_icons` | Album type icons | Ikon for albumtype |
| 505 | `virtual_folders` | Virtual albums | Virtuelle album |
| 506 | `favorites_album` | Favorites | Favorittar |
| 507 | `excluded_folders_album` | Excluded folders | Ekskluderte mapper |
| 508 | `hidden_folders_album` | Hidden folders | Skjulte mapper |
| 509 | `temporarily_show_hidden_folders` | Temporarily show hidden folders | Syn skjulte mapper mellombels |
| 510 | `stop_showing_hidden_folders` | Stop showing hidden folders | Slutt å syna skjulte mapper |
| 511 | `unexclude` | Stop excluding | Slutt å ekskludera |
| 512 | `hide_folder` | Hide | Skjul |
| 513 | `unhide_folder` | Unhide | Syn att |
| 514 | `about_title` | About Calypso Gallery | Om Calypso Gallery |
| 515 | `about_version` | Version %1$s (%2$s) | Versjon %1$s (%2$s) |
| 516 | `about_fork_description` | Calypso Gallery is a security-hardened, independently maintained personal fork of Fossify Gallery. Thanks to the original authors and contributors of Fossify Gallery. It is an unofficial project and is not affiliated with, endorsed by, or supported by the Fossify organization. | Calypso Gallery er ein uavhengig vedlikehalden, uoffisiell personleg fork av Fossify Gallery. Han er ikkje tilknytt eller støtta av Fossify. |
| 517 | `about_modification_notice` | This distribution is a modified version of Fossify Gallery v1.14.2 (2688360, 07-11-2026). All fork-specific modifications are documented in FORK_CHANGELOG.md. | Denne distribusjonen er ein endra versjon av Fossify Gallery. Forken vart laga frå oppstraums-commiten 054228be2da275396eb03d2b9002807b5055afac (1.14.1-5-g054228be2), og alle forksspesifikke endringar er dokumenterte i FORK_CHANGELOG.md. |
| 518 | `about_license_heading` | License | Lisens |
| 519 | `about_gpl_notice` | This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 3 of the License.\n\nThis program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.\n\nYou should have received a copy of the GNU General Public License along with this program. If not, use the license action below or visit https://www.gnu.org/licenses/. | Dette programmet er fri programvare: du kan distribuera det og/eller endra det i samsvar med vilkåra i GNU General Public License som publisert av Free Software Foundation, versjon 3 av lisensen.\n\nDette programmet vert distribuert i håp om at det skal vera nyttig, men UTAN NOKON GARANTI; utan ein gitt garanti for SALJGBARHEIT eller EIGNA FOR EIT FØREMÅL. Sjå GNU General Public License for fleire detaljar.\n\nDu skulle ha motteke ein kopi av GNU General Public License saman med dette programmet. Viss ikkje, bruk lisenshandlinga under eller besøk https://www.gnu.org/licenses/. |
| 520 | `about_read_gpl_license` | Read the GNU GPL v3 license | Les GNU GPL v3-lisensen |
| 521 | `about_read_tink_license` | Read the Tink Apache 2.0 license and attribution | Les Tink Apache 2.0-lisensen og tilskrivinga |
| 522 | `about_source_heading` | Source code | Kjeldekode |
| 523 | `about_corresponding_source_notice` | The complete corresponding source for this fork is kept in the fork repository. Tagged releases also have a matching source archive. The repository contains the build scripts, dependency declarations, and complete fork history needed to inspect and rebuild the project. | Den fullstendige tilhøyrande kjeldekoden for denne forken finst i fork-repositoriet. Merkelagde utgåver har òg eit tilhøyrande kjeldearkiv. Repositoriet inneheld byggjeskripta, avhengighetserklæringane og den fullstendige fork-historikken som trengst for å granska og byggja prosjektet på nytt. |
| 524 | `about_fork_source` | Fork source code and corresponding source | Kjeldekode for forken og tilhøyrande kjelde |
| 525 | `about_release_sources` | Release source archives | Kjeldearkiv for utgåver |
| 526 | `about_fork_issues` | Fork issue tracker | Feilsporar for forken |
| 527 | `about_upstream_source` | Upstream Fossify Gallery source code | Kjeldekode for oppstraums Fossify Gallery |
| 528 | `about_upstream_project` | Upstream Fossify project | Oppstraums Fossify-prosjektet |
| 529 | `about_help_heading` | Help and legal information | Hjelp og juridisk informasjon |
| 530 | `about_faq` | Frequently asked questions | Ofte stilte spørsmål |
| 531 | `about_third_party_licenses` | Third-party licenses | Lisensar frå tredjepart |
| 532 | `gpl_license_title` | GNU General Public License v3 | GNU General Public License v3 |
| 533 | `tink_license_title` | Tink license and attribution | Tink-lisens og tilskriving |
| 534 | `fork_disclaimer_title` | Fork notice | Fork-merknad |
| 535 | `fork_disclaimer_message` | This application is not developed by the Fossify team. Calypso Gallery is an independently maintained, unofficial fork of Fossify Gallery; the original developer is referenced solely for attribution. It is not affiliated with, endorsed by, or supported by Fossify.\n\nBy continuing you acknowledge this notice. Fork-specific changes are documented in FORK_CHANGELOG.md. | Dette programmet er ikkje utvikla av Fossify-teamet. Calypso Gallery er ein uavhengig vedlikehalden, uoffisiell fork av Fossify Gallery; den opphavlege utviklaren er berre nemnd for tilskriving. Det er ikkje tilknytt, støtta eller vedlikehalde av Fossify.\n\nVed å halda fram stadfestar du denne merknaden. Forkspesifikke endringar er dokumenterte i FORK_CHANGELOG.md. |
| 536 | `fork_disclaimer_acknowledge` | I understand | Eg forstår |
| 537 | `argon2_profile_title` | Brute-Force resistance | Motstand mot brute-force |
| 538 | `brute_force_mode_title` | Calibration mode | Kalibreringsmodus |
| 539 | `brute_force_mode_time` | Via vault unlock time | Via opplåsingstid for kvelvet |
| 540 | `brute_force_mode_presets` | Via ready-made modes | Via ferdige modusar |
| 541 | `brute_force_time_title` | Targeted vault unlock time | Målsetnad for opplåsingstid for kvelvet |
| 542 | `brute_force_time_hint` | Select the time spent during opening the encrypted vault. Keeping this value high provides extra security by making brute-force attacks harder. | Vel tida som blir brukt på å opne det krypterte kvelvet. Å halde denne verdien høg gir ekstra tryggleik ved å gjere brute force-åtak vanskelegare. |
| 543 | `brute_force_time_min_hint` | The lower bound is this device's safe standard time. | Nedre grense er den trygge standardtida til denne eininga. |
| 544 | `brute_force_time_value_device` | %1$.1f s (on this device) | %1$.1f s (på denne eininga) |
| 545 | `brute_force_time_out_of_range` | This time is above the selectable maximum (10 s). Tap to change it. | Denne tida er over det valbare maksimumnivået (10 s). Trykk for å endre ho. |
| 546 | `brute_force_time_value` | %1$.1f s | %1$.1f s |
| 547 | `brute_force_actual_params` | Stored: %1$d MiB / %2$d rounds | Lagra: %1$d MiB / %2$d rundar |
| 548 | `brute_force_clamped` |  (clamped) |  (avgrensa) |
| 549 | `argon2_profile_low` | Low-end device (not recommended) | Eining med låg yting (ikkje tilrådd) |
| 550 | `argon2_profile_default` | Default | Standard |
| 551 | `argon2_profile_high` | High security | Høg tryggleik |
| 552 | `argon2_profile_hint` | If you don\'t know what you\'re doing, it is recommended to keep the default setting. This only affects newly created vaults and password changes. | Viss du ikkje veit kva du gjer, tilrår vi å behalda standardinnstillinga. Dette påverkar berre nye kvelv og passordendringar. |
| 553 | `argon2_profile_current` | Current: %s | Gjeldande: %s |
| 554 | `show_pin_dots` | Show a dot for each PIN digit | Vis ein prikk for kvart siffer når PIN-koden blir skriven inn |

## Settings Headers, Font Options & File Encryption Info

Added/overridden in `values-nn/strings.xml` for Settings section headers, font picker options (`Monospace` standardized), and file-only encryption info dialog:

| # | Key (`name`) | English | Norwegian Nynorsk (`values-nn/strings.xml`) |
|--:|---|---|---|
| 1 | `color_customization` | Look & feel | Utsjånad og kjensle |
| 2 | `application_settings` | Application | Applikasjon |
| 3 | `video_playback_settings` | Video playback | Videoavspeling |
| 4 | `thumbnails_and_scrolling_settings` | Thumbnails & scrolling | Minibilete og rulling |
| 5 | `media_viewer_settings` | Media viewer | Medievisar |
| 6 | `security_and_vault_settings` | Security & encrypted vault | Tryggleik og kryptert kvelv |
| 7 | `file_operations` | File operations | Filoperasjonar |
| 8 | `recycle_bin` | Recycle bin | Papirkorg |
| 9 | `migrating` | Migrating | Overføring |
| 10 | `font_system_default` | System default | Systemstandard |
| 11 | `font_monospace` | Monospace | Monospace |
| 12 | `app_font` | App font | App-skrifttype |
| 13 | `select_font_file` | Select font file… | Vel skrifttypefil… |
| 14 | `encrypt_files_info_message` | Folder & File Structure:\n• When an unencrypted album is encrypted, it is converted into an encrypted subfolder inside the vault (Secrets).\n• Both the folder name and all file names/contents inside it are encrypted, so nothing is readable outside the app. | Mappe- og filstruktur:\n• Når eit ukryptert album blir kryptert, blir det gjort om til ei kryptert undermappe inne i kvelvet (Secrets).\n• Både mappenamnet og alle filnamn og filinnhald inni det blir krypterte, slik at ingenting er leseleg utanfor appen. |

## Missing UI strings added (this revision)

`org.fossify:commons:7.1.3` does not provide these keys for Norwegian Nynorsk, and `unlocking_encrypted_vault` was missing from the app-owned resources, so local overrides were added to `values-nn/strings.xml`.

| Key (`name`) | English source | Norwegian Nynorsk | Notes |
|---|---|---|---|
| `search_in_placeholder` | Search in %s | Søk i %s | fossify-commons override; `%s` = album/folder name |
| `open_biometric_dialog` | Open biometric ID verification dialog | Opne dialog for biometrisk ID-verifisering | fossify-commons override; Biometrics tab of the inherited security dialog |
| `authenticate` | Authenticate | Autentiser | fossify-commons override; system biometric prompt title |
| `unlocking_encrypted_vault` | Unlocking encrypted vault… | Låser opp det krypterte hvelvet… | App-owned key; consistent with `hvelv` terminology |

## Appearance — Custom Colors & Saved Themes (9 keys)

Added to `values-nn/strings.xml` for the Appearance settings **Custom colors** section and **Saved themes** workflow (English source strings in `values/strings.xml` were also completed):

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `custom_colors` | Custom colors | Eigendefinerte fargar | Appearance settings section header |
| 2 | `saved_themes` | Saved themes | Lagra tema | Appearance settings row and dialog title |
| 3 | `save_theme` | Save theme | Lagra tema | Positive dialog button to save current custom theme |
| 4 | `theme_saved` | Theme saved | Tema lagra | Toast shown after saving a custom theme |
| 5 | `theme_already_saved` | This theme is already saved | Dette temaet er allereie lagra | Toast shown when the current palette is already saved or built-in |
| 6 | `no_saved_themes` | No saved themes yet | Ingen lagra tema enno | Empty state message in the Saved themes dialog |
| 7 | `custom_theme_name` | Custom theme | Eigendefinert tema | Base name for newly saved custom themes (suffixed with index) |
| 8 | `discard_changes_question` | Do you want to save your changes? | Vil du lagra endringane dine? | Prompt shown when switching themes with unsaved custom color changes |
| 9 | `continue_without_saving` | Continue without saving | Hald fram utan å lagra | Negative dialog button to discard unsaved custom color changes |

## Onboarding & Full-Screen Permission Flow (29 keys)

Added to `values-nn/strings.xml` for the 3-screen first-launch welcome and full-screen permission flow (unsupported system locales fall back cleanly to English in `values/strings.xml`). Also removed the retired `auto_backup_prompt_message` key since auto-backup is now enabled by default and always shown in Settings.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `onboarding_motto` | No sign-up, no cloud, just your gallery. | Inga registrering, inga sky, berre galleriet ditt. | Welcome screen tagline |
| 2 | `onboarding_offline_title` | Completely Offline | Heilt fråkopla | Welcome card 1 title |
| 3 | `onboarding_offline_desc` | Never connects to the internet. Your data belongs only to you. | Koplar seg aldri til internett. Dataa dine høyrer berre til deg. | Welcome card 1 description |
| 4 | `onboarding_vault_title` | Encrypted Vault | Kryptert kvelv | Welcome card 2 title |
| 5 | `onboarding_vault_desc` | Encrypt your photos and videos. No one else can access them. | Krypter bileta og videoane dine. Ingen andre kan få tilgang. | Welcome card 2 description |
| 6 | `onboarding_albums_title` | Playlists for your photos | Spelelister for bileta dine | Welcome card 3 title |
| 7 | `onboarding_albums_desc` | Virtual albums let you bring the files you want to see together in a single album, without moving them between folders or creating copies on disk. Unlike a regular copy, they use no extra storage because only the original file is kept on disk. You can add the same file to as many albums as you like, while your folder structure stays exactly as it is. | Virtuelle album lèt deg samle filene du vil sjå saman i eitt enkelt album, utan å flytte dei mellom mapper eller lage kopiar på disken. I motsetnad til vanleg kopiering brukar dei ikkje ekstra lagringsplass, sidan berre originalfila finst på disken. Du kan leggje same fil til så mange album du vil, medan mappe-strukturen din blir verande uendra. | Welcome card 3 description |
| 8 | `onboarding_media_permission_note` | Media access permission is required to display your photos and videos. | Tilgang til media er nødvendig for å syna bileta og videoane dine. | Welcome footer media permission note |
| 9 | `onboarding_continue` | Continue | Hald fram | Welcome primary button |
| 10 | `onboarding_media_required_title` | Full media access required | Full medietilgang krevst | Media permission screen title |
| 11 | `onboarding_media_required_desc` | Calypso Gallery needs full access to display all your photos and videos. | Calypso Gallery treng full tilgang for å syna alle bileta og videoane dine. | Media permission screen description |
| 12 | `onboarding_media_steps_header` | How to enable permission | Slik gjev du tilgang | Settings steps card header |
| 13 | `onboarding_media_step_1` | Settings | Innstillingar | Settings guide step 1 |
| 14 | `onboarding_media_step_2` | Permissions | Løyve | Settings guide step 2 |
| 15 | `onboarding_media_step_3` | Photos and videos | Bilete og videoar | Settings guide step 3 |
| 16 | `onboarding_media_step_4` | Allow all | Tillat alle | Settings guide step 4 (final) |
| 17 | `onboarding_media_required_hint` | Once access is granted, your gallery will open automatically when you return to the app. | Når tilgang er gjeven, opnar galleriet automatisk når du går attende til appen. | Media permission screen return hint |
| 18 | `onboarding_go_to_settings` | Go to settings | Gå til innstillingar | Media permission primary button |
| 19 | `onboarding_close` | Close | Lukk | Media permission secondary button |
| 20 | `onboarding_all_files_title` | All files access | Tilgang til alle filer | All files access screen title |
| 21 | `onboarding_all_files_desc` | All files access is recommended for the Encrypted Vault and seamless file management. | Tilgang til alle filer er tilrådd for kryptert kvelv og saumlaus filhandtering. | All files access screen subtitle |
| 22 | `onboarding_all_files_vault_title` | Encrypted Vault and hidden folders | Kryptert kvelv og skjulte mapper | All files card 1 title |
| 23 | `onboarding_all_files_vault_desc` | Manage your encrypted files and hidden folders without system restrictions. | Handter krypterte filer og skjulte mapper utan systemavgrensingar. | All files card 1 description |
| 24 | `onboarding_all_files_backup_title` | Don\'t lose your Favorites and Virtual Albums | Ikkje mist favorittane og dei virtuelle albuma dine | All files card 2 title |
| 25 | `onboarding_all_files_backup_desc` | Keep your favorites and virtual albums automatically backed up on your device even if you accidentally uninstall the app. You can turn off automatic backup anytime in app settings. | Sjølv om du ved eit uhell slettar appen, vert favorittane og dei virtuelle albuma dine automatisk tryggingskopierte på eininga. Du kan når som helst slå av automatisk tryggingskopiering i appinnstillingane. | All files card 2 description |
| 26 | `onboarding_all_files_offline_title` | Still completely on your device | Framleis heilt på eininga di | All files card 3 title |
| 27 | `onboarding_all_files_offline_desc` | The app stays completely offline, this permission is used only for local file operations. | Appen held fram med å verka fråkopla, dette løyvet vert berre nytta til lokale filoperasjonar. | All files card 3 description |
| 28 | `onboarding_grant_access` | Grant access | Gjev tilgang | All files primary button |
| 29 | `onboarding_not_now` | Not now | Ikkje no | All files secondary button |

## Vault Lock & Album Header Polish (4 keys)

Added to `values-nn/strings.xml` for the redesigned Vault Lock screen biometric button and album interior header.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `use_biometrics` | Use biometrics | Bruk biometri | Vault lock screen biometric button label |
| 2 | `media_header_items_count` | %d items | %d element | MediaActivity album header item count |
| 3 | `media_header_virtual_album` | Virtual album · %d items | Virtuelt album · %d element | MediaActivity virtual album header subtitle |
| 4 | `media_header_encrypted_album` | Encrypted album · %d items | Kryptert album · %d element | MediaActivity encrypted album header subtitle |
## Appearance Live Preview (4 keys)

Added to `values-nn/strings.xml` for the live Appearance preview card and WCAG contrast warning.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `appearance_preview` | Preview | Førehandsvising | Appearance live preview section header |
| 2 | `appearance_preview_text` | Your memories, your colors | Minna dine, fargane dine | Appearance preview sample headline |
| 3 | `appearance_preview_button` | Sample button | Dømeknapp | Appearance preview sample button |
| 4 | `appearance_contrast_warning` | Low contrast: text %1$.1f:1, button %2$.1f:1. Increase it for readability. | Låg kontrast: tekst %1$.1f:1, knapp %2$.1f:1. Auk kontrasten for betre lesbarheit. | WCAG contrast warning in Appearance preview |

## Vault Privacy Input Toggle (1 key)

Added to `values-nn/strings.xml` for the optional shoulder-surfing protection switch (default off).

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `hide_vault_key_presses` | Hide key presses when entering vault password | Skjul tastetrykk ved inntasting av kvelvpassord | Settings switch to hide PIN/password key press feedback |

## PIN Dot Indicator Setting (1 key)

Added to `values-nn/strings.xml` for the optional per-digit PIN dot indicator (default off; only shown for PINs of at most 16 digits).

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `show_pin_dots` | Show a dot for each PIN digit | Vis ein prikk for kvart siffer når PIN-koden blir skriven inn | Settings switch; hidden for alphanumeric vaults and PINs longer than 16 digits |

## KDF Unlock-Time Hint Revision (this revision)

Updated `brute_force_time_hint` and added `brute_force_time_min_hint` in `values-nn/strings.xml`. The lower-bound sentence is appended only when the device's measured safe floor is above 1.0 s.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_time_hint` | Select the time spent during opening the encrypted vault. Keeping this value high provides extra security by making brute-force attacks harder. | Vel tida som blir brukt på å opne det krypterte kvelvet. Å halde denne verdien høg gir ekstra tryggleik ved å gjere brute force-åtak vanskelegare. | Explanation shown in the target vault unlock time dialog |
| 2 | `brute_force_time_min_hint` | The lower bound is this device's safe standard time. | Nedre grense er den trygge standardtida til denne eininga. | Appended only when minSec > 1.0 |

## Biometric Unavailability Explanation (this revision)

Added to `values-nn/strings.xml`. The Settings row now stays visible when a vault exists; if the device only has weak (Class 2) biometrics or cannot create a hardware-backed key, the row is disabled and these strings explain why in a dialog and under the row. They replace the short `no_biometric_hardware_or_enrolled` toast. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `vault_biometric_unavailable_title` | Biometric vault access unavailable | Biometrisk tilgang til kvelvet er ikkje tilgjengeleg | Dialog title |
| 2 | `vault_biometric_unavailable_no_hardware` | This device has no strong biometric hardware (fingerprint or Class 3 face unlock). Vault biometric access is unavailable; use your PIN or password. | Denne eininga har ikkje sterk biometrisk maskinvare (fingeravtrykk eller andletsgjenkjenning i klasse 3). Biometrisk tilgang til kvelvet er ikkje tilgjengeleg; bruk PIN-koden eller passordet ditt. | `BIOMETRIC_ERROR_NO_HARDWARE` |
| 3 | `vault_biometric_unavailable_none_enrolled` | No strong biometric is enrolled. Weak face unlock is not accepted; enrol a fingerprint or a Class 3 face unlock in system settings. | Det er ikkje registrert sterk biometri. Svak andletsgjenkjenning blir ikkje godteken; registrer eit fingeravtrykk eller andletsgjenkjenning i klasse 3 i systeminnstillingane. | `BIOMETRIC_ERROR_NONE_ENROLLED`; weak face unlock rejected |
| 4 | `vault_biometric_unavailable_hw_unavailable` | The biometric sensor is temporarily unavailable. Try again later. | Den biometriske sensoren er mellombels utilgjengeleg. Prøv igjen seinare. | `BIOMETRIC_ERROR_HW_UNAVAILABLE` |
| 5 | `vault_biometric_unavailable_security_update` | Biometrics require a security update before they can be used. | Biometri krev ei tryggleiksoppdatering før ho kan brukast. | `BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED` |
| 6 | `vault_biometric_unavailable_no_secure_key` | A hardware-backed key (TEE/StrongBox) could not be created on this device. Vault biometric access is disabled for security reasons. | Det kunne ikkje opprettast ein maskinvarebeskytta nøkkel (TEE/StrongBox) på denne eininga. Biometrisk tilgang til kvelvet er deaktivert av tryggleiksgrunnar. | `canAuthenticate()` succeeded but no TEE/StrongBox key |
| 7 | `vault_biometric_unavailable_generic` | Vault biometric access is unavailable on this device. Enrol a fingerprint or a strong face unlock in system settings, or use your PIN or password. | Biometrisk tilgang til kvelvet er ikkje tilgjengeleg på denne eininga. Registrer eit fingeravtrykk eller sterk andletsgjenkjenning i systeminnstillingane, eller bruk PIN-koden eller passordet ditt. | Fallback explanation |

## Password Reminder Strings (this revision)

Added/updated in `values-nn/strings.xml`. The reminder is shown in-app when the vault is opened and no password/PIN unlock happened during the configured interval. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `vault_password_reminder` | Remind me of my password | Minn meg på passordet mitt |  |
| 2 | `vault_password_reminder_disabled` | Off | Av |  |
| 3 | `vault_password_reminder_weeks` | %d weeks | %d veker |  |
| 4 | `vault_password_reminder_months` | %d months | %d månader |  |
| 5 | `vault_password_reminder_dialog_title` | Reminder interval | Påminningsintervall |  |
| 6 | `vault_password_reminder_dialog_message` | If you do not open the encrypted vault with your password for this long, we will remind you of it. | Om du ikkje opnar det krypterte kvelvet med passordet ditt på så lang tid, minner vi deg om det. |  |
| 7 | `vault_password_reminder_setup_note` | We will send you periodic reminders so you do not forget your password. You can turn this off in Settings → Encrypted vault → Remind me of my password. | Vi sender deg jamlege påminningar slik at du ikkje gløymer passordet ditt. Du kan slå dette av under Innstillingar → Kryptert kvelv → Minn meg på passordet mitt. |  |
| 8 | `vault_biometric_setup_password_note` | If you choose to sign in with biometrics, remember that you still need to know your password. | Om du vel å logge inn med biometri, må du hugse at du framleis må kjenne passordet ditt. |  |
| 9 | `vault_password_saved_title` | Have you saved your password? | Har du lagra passordet ditt? |  |
| 10 | `vault_password_saved_message` | If you forget your password, you will permanently lose access to your encrypted vault. Make sure you have saved it somewhere before continuing. Using a password manager is recommended. | Om du gløymer passordet ditt, mister du permanent tilgang til det krypterte kvelvet ditt. Sørg for at du har lagra det ein stad før du held fram. Bruk av ein passordbehandlar blir tilrådd. |  |
| 11 | `vault_password_saved_confirm` | Yes, I saved it | Ja, eg har lagra det |  |
| 12 | `vault_password_saved_later` | I will save it | Eg skal lagre det |  |
| 13 | `vault_password_reminder_due_title` | Do you still remember your vault password? | Hugsar du framleis kvelvpassordet ditt? |  |
| 14 | `vault_password_reminder_due_message` | You have not opened the encrypted vault with your password for this long. Your password is the only way to recover the vault. | Du har ikkje opna det krypterte kvelvet med passordet ditt på så lang tid. Passordet ditt er den einaste måten å gjenopprette kvelvet på. |  |
| 15 | `vault_password_reminder_verify_title` | Confirm your password | Stadfest passordet ditt |  |
| 16 | `vault_password_reminder_verify_message` | This is not a vault unlock. We only want to confirm that you still know your password. | Dette er ikkje ei kvelvopplåsing. Vi vil berre stadfeste at du framleis kjenner passordet ditt. |  |
| 17 | `vault_password_reminder_verified` | Password confirmed | Passord stadfesta |  |
| 18 | `vault_password_reminder_not_now` | Not now | Ikkje no |  |
| 19 | `vault_password_reminder_forgot_title` | Do not remember your password? | Hugsar du ikkje passordet ditt? |  |
| 20 | `vault_password_reminder_forgot_steps` | 1. Unlock the vault with biometrics.\n2. Copy (export) your encrypted data — this does not move the vault.\n3. Settings → Reset encrypted vault to set a new password and restore.\n\nIf biometrics cannot unlock it either, the vault is inaccessible. You can archive the current vault and create a new one, copy the encrypted data for another device, or delete the vault folder at %1$s with a file manager (cannot be undone). | Lås opp kvelvet med biometri, eksporter dei krypterte dataene dine, og bruk deretter Innstillingar → Nullstill kryptert kvelv for å setje eit nytt passord og gjenopprette dataene. Om biometri heller ikkje kan låse opp kvelvet, er det utilgjengeleg. |  |
| 21 | `vault_password_reminder_verify_action` | Verify my password | Stadfest passordet mitt |  |
| 22 | `vault_password_reminder_forgot_action` | I don't remember | Eg hugsar ikkje |  |
| 23 | `vault_password_reminder_open_settings` | Open settings | Opne innstillingar |  |
| 24 | `empty_encrypted_vault` | Reset encrypted vault | Nullstill kryptert kvelv | Renamed label |

## Vault Archive & Forgot-Password Strings (this revision)

Added/updated in `values-nn/strings.xml`. Archive moves the old vault; export copies it; deletion is left to the file manager (ADR 0007). English source: `values/strings.xml`.

| # | Key (`name`) | English | Norwegian Nynorsk |
|--:|---|---|---|
| 1 | `archive_vault` | Archive encrypted vault | Arkiver kryptert kvelv |
| 2 | `archive_vault_summary` | Moves the old vault to the archive and creates a new one | Flyttar det gamle kvelvet til arkivet og lagar eit nytt |
| 3 | `archive_vault_confirm` | The current vault will be moved to the archive and a new vault will be created in the default location. Nothing is deleted. To use the vault on another device, use Export encrypted vault — it copies the files and the current vault stays where it is. | Det noverande kvelvet blir flytta til arkivet og eit nytt blir laga på standardstaden. Ingenting blir sletta. For bruk på ein annan eining, bruk Eksporter kryptert kvelv — det kopierer og kvelvet blir liggjande. |
| 4 | `archive_vault_success` | Vault archived. Location: %1$s | Kvelvet er arkivert. Plassering: %1$s |
| 5 | `archive_vault_failed` | Could not archive the vault. Nothing was changed. | Klarte ikkje å arkivere kvelvet. Ingenting vart endra. |
| 6 | `select_archived_vault` | Select from archived vaults | Vel frå arkiverte kvelv |
| 7 | `archived_vault_indexed` | %1$d. archived vault | %1$d. arkivert kvelv |
| 8 | `archived_vault_unknown_date` | Encrypted vault with unknown date | Kryptert kvelv med ukjend dato |
| 9 | `archived_vault_invalid` | Invalid archive entry | Ugyldig arkivoppføring |
| 10 | `archived_vault_invalid_message` | Vault header not found at %1$s. You can delete this folder with a file manager. | Fann ikkje kvelvheader i %1$s. Du kan slette mappa med ein filbehandlar. |
| 11 | `copy_path` | Copy path | Kopier sti |
| 12 | `faq_forgot_password_title` | I forgot my encrypted vault password, what can I do? | Eg har gløymd passordet til det krypterte kvelvet, kva kan eg gjere? |
| 13 | `faq_forgot_password_text` | 1. Unlock the vault with biometrics.\n2. Copy (export) your encrypted data — copying is not moving; the original stays in place.\n3. Settings → Reset encrypted vault to set a new password and restore.\n\nWarning: if biometrics cannot unlock it either, the vault is inaccessible. You can archive the current vault and create a new one, copy the encrypted data for another device, or delete the vault folder at %1$s with a file manager. Deletion cannot be undone. | 1. Lås opp med biometri. 2. Kopier (eksporter) dei krypterte dataene — kopiering er ikkje flytting. 3. Innstillingar → Nullstill kryptert kvelv for nytt passord.  Åtvaring: om biometri heller ikkje verkar, er kvelvet utilgjengeleg. Du kan arkivere, kopiere eller slette mappa på %1$s med ein filbehandlar. |
| 14 | `vault_password_reminder_forgot_steps` | 1. Unlock the vault with biometrics.\n2. Copy (export) your encrypted data — this does not move the vault.\n3. Settings → Reset encrypted vault to set a new password and restore.\n\nIf biometrics cannot unlock it either, the vault is inaccessible. You can archive the current vault and create a new one, copy the encrypted data for another device, or delete the vault folder at %1$s with a file manager (cannot be undone). | 1. Lås opp med biometri. 2. Kopier dei krypterte dataene. 3. Innstillingar → Nullstill kryptert kvelv.  Om biometri ikkje verkar: arkiver, kopier eller slett mappa %1$s med ein filbehandlar. |

## Unarchive Strings (this revision)

Added to `values-nn/strings.xml`. Unarchive moves the vault back to its recorded original location; no swap, no password.

| # | Key | English | Norwegian Nynorsk |
|--:|---|---|---|
| 1 | `unarchive_vault` | Unarchive vault | Ta ut av arkivet |
| 2 | `unarchive_vault_summary` | Moves the vault back to its location before archiving | Flyttar kvelvet tilbake til plasseringa før arkivering |
| 3 | `unarchive_vault_confirm` | The vault will be moved back to its original location. No password is required. Continue? | Kvelvet blir flytta tilbake til originalplasseringa. Ingen passord krevst. Halde fram? |
| 4 | `unarchive_vault_success` | The vault was moved back to its original location. | Kvelvet er flytta tilbake. |
| 5 | `unarchive_vault_blocked` | Another vault is located where this vault used to be. Archive that vault first, then unarchive this one. | På den tidlegare plasseringa ligg eit anna kvelv. Arkiver det først, og ta deretter dette ut av arkivet. |
| 6 | `unarchive_vault_merge` | Combine data into one vault | Slå saman data i eitt kvelv |
| 7 | `unarchive_vault_merge_steps` | Unlock the archived vault with its password, export the files in decrypted form, then import them into the other vault. The existing conflict-handling modes apply. | Lås opp det arkiverte kvelvet med passord, eksporter filene dekryptert og importer dei i det andre kvelvet. |
| 8 | `archived_vault_activated` | The vault is open from the archive. Use Settings → Unarchive vault to move it back to its original location. | Kvelvet er opna frå arkivet. Bruk Innstillingar → Ta ut av arkivet. |

## Export Target Conflict Strings (this revision)

Added to `values-nn/strings.xml`. Export now targets `<picked>/Calypso Gallery/Secrets` and is blocked when the target holds a different vault.

| # | Key | English | Norwegian Nynorsk |
|--:|---|---|---|
| 1 | `export_vault_different_vault` | The destination already contains a different encrypted vault. Export was cancelled to protect it. Choose an empty folder. | Målmappa inneheld allereie eit anna kryptert kvelv. Eksporten vart avbroten for å verne det. Vel ei tom mappe. |
| 2 | `export_vault_unknown_content` | The destination contains encrypted files but no vault header. Export was cancelled to avoid mixing vaults. | Målmappa inneheld krypterte filer, men ingen kvelvheader. Eksporten vart avbroten. |

## Existing Vault Time Recalculation (this revision)





Added `brute_force_time_value_device` and `brute_force_time_out_of_range` to `values-nn/strings.xml`. The Settings/vault-setup summary now shows the existing vault's real unlock time on the current device (measured on unlock, otherwise estimated from the portable `.vault` KDF cost), with a note when it exceeds the 10 s maximum, showing only the stored parameters when this device has no measurement yet.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_time_value_device` | %1$.1f s (on this device) | %1$.1f s (på denne eininga) | Value shown for an existing vault |
| 2 | `brute_force_time_out_of_range` | This time is above the selectable maximum (10 s). Tap to change it. | Denne tida er over det valbare maksimumnivået (10 s). Trykk for å endre ho. | Shown when the real time exceeds 10 s |

## Silent identical-skip toast & Automatic mode date tie-break (this revision)

Added the `identical_files_skipped` plural and revised `date_tie_break_ask_info_message` in `values-nn/strings.xml`. Smart dialog and Automatic mode now report silently skipped, byte-identical files at the end of an operation with a short toast; identical files with different dates that were reviewed in the thumbnail conflict dialog are excluded. "Always ask" can no longer be selected in Automatic (Experimental) mode and falls back to keeping the existing file's date. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `identical_files_skipped` | one: "%d identical file skipped" / other: "%d identical files skipped" | one: "%d identisk fil hoppa over" / other: "%d identiske filer hoppa over" | Short end-of-operation toast in Smart/Automatic mode |
| 2 | `date_tie_break_ask_info_message` | Smart dialog and Automatic mode exist for one reason: to skip files whose content is already present, so you only see the files that really need your attention — same name, different content. That is exactly the case where comparing thumbnails can reveal bit-rot.\n\nIn Smart dialog you will see two thumbnails of the very same picture, with nothing to verify, and you can decide there. Automatic (Experimental) mode never shows a dialog, so "Always ask" is not available there — selecting Automatic falls back to "Keep the existing file's date".\n\nUse this option only if you deliberately want to review every date difference, for example to fix a wrong date by hand.\n\nNote: when content is bit-identical, the EXIF capture date is identical too. A date difference means one side lost its EXIF data and fell back to the file modification date (the copy date). | Smart dialog og automatisk modus finst av éin grunn: å hoppa over filer som alt er til stades, slik at du berre ser filene som verkeleg treng merksemda di — same namn, ulikt innhald. Det er nettopp i dette tilfellet at samanlikning av minibilete kan avdekkja bit-rot.\n\nViss du vel \"Spør alltid\" her, kjem kvar identisk fil med ein annan dato tilbake til deg som ein konflikt. I Smart dialog ser du to minibilete av det same biletet, utan noko å stadfesta, og kan avgjera der. Automatisk modus (eksperimentell) viser aldri dialog, så \"Spør alltid\" er ikkje tilgjengeleg der – når du vel Automatisk, blir det falla tilbake til \"Behald datoen til den eksisterande fila\".\n\nBruk dette valet berre viss du med vilje vil gå gjennom kvar einaste datoskilnad, til dømes for å retta ein feil dato for hand.\n\nMerk: når innhaldet er bit-identisk, er EXIF-opptaksdatoen òg identisk. Ein datoskilnad tyder at den eine sida mista EXIF-dataa sine og fall tilbake til endringsdatoen til fila (kopieringsdatoen). | Revised: "Always ask" unavailable in Automatic; falls back to keep-target date |

## New strings (biometric setup, device-owner check, archive size)

| Key | Translation |
|---|---|
| `vault_biometric_setup_continue` | Hald fram |
| `device_owner_verify_title` | Stadfest eigar av eininga |
| `device_owner_verify_subtitle` | Stadfest med biometri eller PIN-kode for eininga for å halde fram. Kvelvpassordet er ikkje nødvendig. |
| `archive_total_size_title` | Arkivlager |
| `archive_total_size` | Samla storleik på arkiverte kvelv: %1$s |

## Weak password confirmation

| Key | Translation |
|---|---|
| `weak_password_warning_title` | Veikt passord |
| `weak_password_warning_message` | Dette passordet blir rekna som veikt. Eit lengre og meir komplekst passord er tilrådd for tryggleiken til kvelvet. Vil du halde fram likevel? |
| `weak_password_warning_continue` | Hald fram likevel |

## Minimalist Settings, Appearance Redesign & Recent Fork Strings (this revision)

| Key | English | Norwegian Nynorsk |
|---|---|---|
| `appearance_title` | Appearance | Utsjånad |
| `search_settings` | Search settings | Søk i innstillingar |
| `license_tink_title` | Google Tink (streaming AEAD and HKDF cryptography) | Google Tink (strøymande AEAD- og HKDF-kryptografi) |
| `license_rustcrypto_argon2_title` | RustCrypto Argon2 (native Argon2id key derivation) | RustCrypto Argon2 (native Argon2id-nøkkelutleiing) |
| `license_webpdecoder_title` | GlideWebpDecoder (animated WebP decoding) | GlideWebpDecoder (dekoding av animert WebP) |
| `license_jxl_coder_title` | JXL Coder Glide (JPEG XL support) | JXL Coder Glide (støtte for JPEG XL) |
| `license_androidsvg_title` | AndroidSVG (SVG rendering) | AndroidSVG (SVG-attgjeving) |
| `license_exif_extended_title` | Android-Exif-Extended (EXIF metadata handling) | Android-Exif-Extended (handtering av EXIF-metadata) |
| `license_material_icons_title` | Material Design Icons (UI icons) | Material Design Icons (grensesnittikon) |
| `checking_password` | Checking password… | Kontrollerer passord… |
| `vault_same_password_title` | Same password | Same passord |
| `vault_same_password_message` | The new password is identical to the current vault password. Please choose a different one. | Det nye passordet er likt det noverande passordet for kvelvet. Ver venleg og vel eit anna. |
| `brute_force_time_with_params` | %1$.1f s (%2$d MiB / %3$d rounds) | %1$.1f s (%2$d MiB / %3$d rundar) |
| `brute_force_high_memory_warning` | Higher security uses more memory. You may have trouble opening your vault on devices with 2 GB of RAM or less. If that happens, lower the unlock time in app settings and export the vault again. | Høgare tryggleik bruker meir minne. Du kan få problem med å opne kvelvet på einingar med 2 GB RAM eller mindre. Om dette skjer, senkar du opningstida i appinnstillingane og eksporterer kvelvet på nytt. |
| `measuring_device_kdf_speed` | Measuring standard KDF speed on this device… | Måler standard KDF-snøggleik på denne eininga… |
| `kdf_settings_updated_successfully` | Brute-Force resistance updated for encrypted vault | Motstand mot brute force oppdatert for det krypterte kvelvet |
| `appearance_automatic` | Automatic | Automatisk |
| `follow_system_theme` | Follow system theme | Fylg systemtema |
| `follow_system_theme_summary` | Automatically switches between light and dark | Byter automatisk mellom lyst og mørkt tema |
| `theme_midnight_blue` | Midnight Blue | Midnattsblå |
| `theme_warm_paper` | Warm Paper | Varmt papir |
| `theme_deep_forest` | Deep Forest | Djup skog |
| `appearance_contrast_ok` | Good contrast · Text %1$.1f:1 · Button %2$.1f:1 | God kontrast · Tekst %1$.1f:1 · Knapp %2$.1f:1 |
| `appearance_backup_section` | Backup | Tryggingskopi |
| `backup_saved_themes_to_storage` | Back up saved themes to device storage | Tryggingskopier lagra tema til einingslager |
| `settings_group_general` | General | Generelt |
| `settings_group_folders_and_loading` | Folders and loading | Mapper og lasting |
| `settings_group_security` | Security | Tryggleik |
| `settings_group_vault_management` | Vault management | Kvelvhandsaming |
| `settings_group_playback` | Playback | Avspeling |
| `settings_group_gestures_and_tap` | Gestures and tap | Rørsler og trykk |
| `settings_group_display_and_motion` | Display and motion | Vising og rørsle |
| `settings_group_thumbnail_styles` | Thumbnail styles | Minibiletestilar |
| `settings_group_general_behavior` | General behavior | Generell åtferd |
| `settings_group_conflicts_and_crypto` | Conflicts and encryption rules | Konfliktar og krypteringsreglar |
| `settings_group_cleanup` | Cleanup | Opprydding |
| `settings_group_storage` | Storage | Lagring |
| `settings_group_settings_and_favorites` | Settings and favorites | Innstillingar og favorittar |
| `settings_group_vault_transfer` | Encrypted vault transfer | Overføring av kryptert kvelv |
| `settings_search_results` | Search results | Søkjeresultat |
| `settings_no_search_results` | No matching settings found. | Fann ingen innstillingar som passar. |

## Active keys added since 2688360 (7 November) - added in this revision

The following strings were added after the base commit `2688360` (7 November) and are now documented here. English source: `values/strings.xml` (or the corresponding license/defaults resource). Target source: `values-nn/strings.xml`.

| # | Key (`name`) | English | nn (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `about_copyright_notice` | Copyright © Fossify Gallery contributors\nCopyright © 2026 Calypso Gallery contributors | *(translatable="false"; not localized)* | |
| 2 | `app_name` | Calypso Gallery | *(translatable="false"; not localized)* | |
| 3 | `auto_biometric_prompt_on_vault_return` | Automatically show biometric prompt when returning to app | Vis biometrisk stadfesting automatisk når du går tilbake til appen | |
| 4 | `biometric_access_to_encrypted_vault` | Biometric access to encrypted vault | Biometrisk tilgang til kryptert kvelv | |
| 5 | `change_encrypted_vault_password` | Change encrypted vault password | Endre passord for kryptert kvelv | |
| 6 | `create_encrypted_vault` | Create encrypted vault | Opprett kryptert kvelv | |
| 7 | `delete_auto_backup_confirm` | Delete device storage backups for the %1$s created while this setting was enabled? | Vil du slette tryggingskopiane på einingslagringa for %1$s som vart laga medan denne innstillinga var aktiv? | |
| 8 | `delete_auto_backup_target_both` | favorites and virtual albums | favorittar og virtuelle album | |
| 9 | `delete_auto_backup_target_favorites` | favorites | favorittar | |
| 10 | `delete_auto_backup_target_virtual_albums` | virtual albums | virtuelle album | |
| 11 | `empty_encrypted_vault_warning` | The encrypted vault (Secrets) and all its files will be permanently deleted. Are you sure? | Det krypterte kvelvet (Secrets) og alle filene i det vert sletta permanent. Er du sikker? | |
| 12 | `encrypted_vault` | Encrypted vault | Kryptert kvelv | |
| 13 | `encrypted_vault_emptied` | Encrypted vault emptied | Kryptert kvelv tømt | |
| 14 | `enter_new_password` | Enter new password | Skriv inn nytt passord | |
| 15 | `enter_new_pin` | Enter new PIN | Skriv inn ny PIN | |
| 16 | `fast_large_duplicate_check_info_message` | Why this matters for large copy/move transfers:\n\nWhen copying or moving large files, such as gigabytes of videos, exact bit-by-bit checking must read both the source and destination files from start to finish whenever filenames and sizes match—taking minutes on fast storage or much longer on SD cards.\n\nHow Fast Check works:\n• Files under 8 MB are ALWAYS checked 100% bit-by-bit.\n• For files over 8 MB, the app compares file size plus five 256 KB blocks distributed across the start, 25%, 50%, 75%, and end of the file (1.25 MB total per side).\n• For a 50 GB archive of 50 MB–1 GB videos, this cuts disk reads by ~40× to ~800×.\n\nWhen to enable or disable:\n• Recommended ON: If most of your gallery consists of large files (&gt;8 MB), two intact, distinct files sharing the same name, exact byte size, and all 5 sampled regions is virtually impossible in practice.\n• Recommended OFF (no speedup): If your gallery consists mostly of photos or files under 8 MB, this setting provides no significant speed gain since files under 8 MB are always checked 100% bit-by-bit.\n• Encrypt, decrypt, and external USB vault sync operations always perform full bit-by-bit verification. | Kvifor dette er viktig ved store kopierings-/flytteoverføringar:\n\nNår du kopierer eller flyttar store filer, som gigabyte med videoar, må nøyaktig bit-for-bit-sjekk lesa både kjelde- og målfila frå start til slutt kvar gong filnamn og storleikar samsvarar—noko som tek minutt på rask lagring eller mykje lenger på SD-kort.\n\nSlik fungerer rask sjekk:\n• Filer under 8 MB blir ALLTID sjekka 100% bit-for-bit.\n• For filer over 8 MB samanliknar appen filstorleik pluss fem 256 KB-blokker fordelte på start, 25%, 50%, 75% og slutt av fila (1,25 MB totalt per side).\n• For eit 50 GB-arkiv med 50 MB–1 GB-videoar reduserer dette disklesing med ~40× til ~800×.\n\nNår ho bør aktiverast eller deaktiverast:\n• Tilrådd PÅ: Viss mesteparten av galleriet ditt består av store filer (&gt;8 MB), er det i praksis nærast umogleg at to intakte, ulike filer deler same namn, nøyaktig bytestorleik og alle 5 utvalde område.\n• Tilrådd AV (ingen snøggleiksgevinst): Viss galleriet ditt hovudsakleg består av bilete eller filer under 8 MB, gjev denne innstillinga inga merkbar snøggleiksgevinst sidan filer under 8 MB alltid blir sjekka 100% bit-for-bit.\n• Synkronisering av eksternt USB-kvelv blir alltid verande fullt Tink AEAD-autentisert. | |
| 17 | `license_androidsvg_text` | Copyright 2013-2020 Paul LeBeau\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\n\nhttps://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. | *(translatable="false"; not localized)* | |
| 18 | `license_androidsvg_url` | https://github.com/BigBadaboom/androidsvg | *(translatable="false"; not localized)* | |
| 19 | `license_exif_extended_text` | Copyright 2013 Alessandro Crugnola\n\nReleased into the Public Domain, based on the jhead library and the Android Open Source Project ExifInterface.\n\nIncludes Apache Commons Lang and Apache Commons IO, Copyright The Apache Software Foundation, licensed under the Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0). | *(translatable="false"; not localized)* | |
| 20 | `license_exif_extended_url` | https://github.com/sephiroth74/Android-Exif-Extended | *(translatable="false"; not localized)* | |
| 21 | `license_fossify_commons_text` | GNU General Public License v3.0 — Copyright © Fossify Commons contributors. | *(translatable="false"; not localized)* | |
| 22 | `license_fossify_commons_title` | Fossify Commons | *(translatable="false"; not localized)* | |
| 23 | `license_fossify_commons_url` | https://github.com/FossifyOrg/Commons | *(translatable="false"; not localized)* | |
| 24 | `license_jxl_coder_text` | Copyright 2023 Radzivon Bartoshyk\n\nLicensed under the Apache License, Version 2.0 and the 3-Clause BSD License. You may obtain a copy of the License at\n\nhttps://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. | *(translatable="false"; not localized)* | |
| 25 | `license_jxl_coder_url` | https://github.com/awxkee/jxl-coder-glide | *(translatable="false"; not localized)* | |
| 26 | `license_material_icons_text` | Copyright Google LLC\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\n\nhttps://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. | *(translatable="false"; not localized)* | |
| 27 | `license_material_icons_url` | https://github.com/google/material-design-icons | *(translatable="false"; not localized)* | |
| 28 | `license_rustcrypto_argon2_text` | Bundles Rust crates argon2 (0.5.3), zeroize (1.8.1), and jni (0.21.1).\n\nCopyright (c) 2020-2024 RustCrypto Developers (argon2, zeroize)\nCopyright (c) jni-rs contributors (jni: https://github.com/jni-rs/jni-rs)\n\nLicensed under either of Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0) or MIT license (https://opensource.org/licenses/MIT), at your option.\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE. | *(translatable="false"; not localized)* | |
| 29 | `license_rustcrypto_argon2_url` | https://github.com/RustCrypto/password-hashes | *(translatable="false"; not localized)* | |
| 30 | `license_tink_text` | Copyright 2022 Google LLC\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\n\nhttps://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. | *(translatable="false"; not localized)* | |
| 31 | `license_tink_url` | https://github.com/tink-crypto/tink-java | *(translatable="false"; not localized)* | |
| 32 | `license_webpdecoder_text` | Copyright 2018 zjupure\n\nLicensed under the Apache License, Version 2.0 and the Simplified BSD License. You may obtain a copy of the License at\n\nhttps://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. | *(translatable="false"; not localized)* | |
| 33 | `license_webpdecoder_url` | https://github.com/zjupure/GlideWebpDecoder | *(translatable="false"; not localized)* | |
| 34 | `no_virtual_albums_yet` | No virtual albums yet. Tap \"New album\" to create one. | Ingen virtuelle album enno. Trykk på \"Nytt album\" for å laga eitt. | |
| 35 | `password_changed_successfully` | Password changed successfully | Passordet er endra | |
| 36 | `refresh` | Refresh | Oppdater | |
| 37 | `repeat_new_password` | Repeat new password | Gjenta nytt passord | |
| 38 | `repeat_new_pin` | Repeat new PIN | Gjenta ny PIN | |
| 39 | `resume_op_copy` | Copy | Kopiering | |
| 40 | `resume_op_decrypt` | Decryption | Dekryptering | |
| 41 | `resume_op_encrypt` | Encryption | Kryptering | |
| 42 | `resume_op_move` | Move | Flytting | |
| 43 | `resume_operation_aborted_message` | %1$s operation was aborted. Already completed files were kept. | %1$s-operasjonen vart avbroten. Filer som alt var fullførte vart behaldne. | |
| 44 | `resume_operation_interrupted_message` | %1$s operation was interrupted (%2$d remaining item(s)). No data was lost — do you want to continue? | %1$s-operasjonen vart avbroten (%2$d attståande element). Ingen data gjekk tapt – vil du halde fram? | |
| 45 | `search_virtual_albums` | Search virtual albums | Søk i virtuelle album | |
| 46 | `vault_no_recovery_warning` | Offline encryption is used. If you forget your password or PIN, there is no way to recover your vault. | Fråkopla kryptering vert nytta. Dersom du gløymer passordet eller PIN-koden, finst det ingen måte å gjenopprette kvelvet på. | |
| 47 | `vault_not_created_prompt` | You haven\'t created a vault yet. Do you want to create a vault? | Du har ikkje oppretta eit kvelv enno. Vil du opprette eit kvelv? | |
| 48 | `waiting_for_free_space_or_storage` | Waiting for free space or destination storage… | Ventar på ledig plass eller mållagring… | |

## Vault setup tutorial & PIN-dot preview notice (this revision)

Added the six strings introduced by the vault setup-tutorial and PIN-dot live-preview design updates (`dba5068f0`, `4ea5b1f8f`) to `values-nn/strings.xml`. English source: `values/strings.xml`. The `&lt;b&gt;…&lt;/b&gt;` emphasis in `vault_pull_tutorial_message` and the literal `10 %` in `pin_dots_notice_note` are preserved exactly.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `vault_pull_tutorial_title` | Your encrypted vault is ready | Det krypterte kvelvet ditt er klart | One-time tutorial shown after vault creation |
| 2 | `vault_pull_tutorial_message` | While at the top of the list on the main screen, &lt;b&gt;pull down from the top&lt;/b&gt; and hold until the lock icon settles, then release. | Når du er øvst i lista på hovudskjermen, &lt;b&gt;dra ned frå toppen&lt;/b&gt; og hald til låseikonet har stabilisert seg, slepp deretter. | `&lt;b&gt;` emphasis preserved |
| 3 | `vault_pull_tutorial_note` | For your privacy, the vault never appears as a separate folder or icon on the home screen; it only opens with this gesture. | Av personvernomsyn blir kvelvet aldri vist som ei eiga mappe eller eit ikon på heimeskjermen; det opnar berre med denne rørsla. | Privacy note |
| 4 | `vault_pull_tutorial_confirm` | Got it | Forstått | Positive button |
| 5 | `pin_dots_notice_title` | Alternative PIN appearance | Alternativ PIN-vising | One-time preview dialog before enabling PIN dots |
| 6 | `pin_dots_notice_note` | Anyone seeing your screen can count the empty rings to tell your PIN length. This reduces the number of passwords to try by about 10%. If you find having your PIN length known acceptable from a security standpoint, you can use this view. | Alle som ser skjermen din kan telje dei tomme sirklane for å finne PIN-lengda di. Dette reduserer talet på passord som må prøvast med omtrent 10 %. Om du reknar det som akseptabelt frå eit tryggleiksperspektiv at PIN-lengda di er kjend, kan du bruke denne visa. | `10 %` kept with Norwegian spacing |

## Filename toggle, album search hint & extra viewer actions (this revision)

Added the five strings introduced by the dynamic filename toggle, album search hint, and extra fullscreen viewer actions updates (`e79197d7f`) to `values-nn/strings.xml`. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `show_filenames` | Show filenames | Vis filnamn | Media-view overflow toggle label when filenames are hidden |
| 2 | `hide_filenames` | Hide filenames | Gøym filnamn | Media-view overflow toggle label when filenames are visible |
| 3 | `search_in_album` | Search in album | Søk i album | Album-view search hint |
| 4 | `show_extra_viewer_actions` | Show extra actions on fullscreen media | Vis ekstra handlingar på fullskjermsmedium | Settings switch label |
| 5 | `manage_extra_viewer_actions` | Manage extra actions | Administrer ekstra handlingar | Settings row label and extra-actions dialog title |

## Secure sharing — metadata sanitization (this revision)

Added the five strings for the new **Settings → Security → Secure sharing** three-mode setting. A sanitized temporary copy is shared; the original media file is never modified. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `settings_group_secure_sharing` | Secure sharing | Sikker deling | New eyebrow in the Security category |
| 2 | `settings_share_metadata_title` | Clear metadata before sharing | Fjern metadata før deling | Settings row label and radio dialog title |
| 3 | `share_metadata_off` | Off | Av | No sanitization |
| 4 | `share_metadata_location_only` | Location only | Berre plassering | Default mode: strips GPS tags |
| 5 | `share_metadata_all` | All metadata | Alle metadata | Strips Make/Model/Software/DateTime/XMP too |

## File operation notifications (this revision)

Added the notification settings section (category heading, per-operation toggles, permission row and progress messages) introduced by the file-operation notification work to `values-nn/strings.xml`. Translated independently from the English source in `values/strings.xml`, matching the locale's existing terminology; the `…` ellipses are preserved exactly.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `notifications_settings` | Notifications | Varsel | Settings category label |
| 2 | `settings_group_operation_notifications` | File operation notifications | Varsel om filoperasjonar | Settings subcategory eyebrow |
| 3 | `notifications_enabled` | Show operation progress | Vis framdrift for operasjonar | Settings switch label |
| 4 | `notify_copy_move` | Copy and move | Kopier og flytt | Per-operation toggle label |
| 5 | `notify_crypto` | Encrypt and decrypt | Krypter og dekrypter | Per-operation toggle label |
| 6 | `notify_delete` | Delete and recycle bin | Slett og papirkorg | Per-operation toggle label |
| 7 | `notify_virtual_album` | Virtual album updates | Oppdateringar av virtuelle album | Per-operation toggle label |
| 8 | `notifications_info` | Android requires a persistent notification while a background file operation runs. Detailed progress is only shown after you leave the app — while you are inside it, the on-screen dialog is used instead. File names, paths and vault contents are never included in notifications. | Android krev eit vedvarande varsel medan ein filoperasjon køyrer i bakgrunnen. Detaljert framdrift blir berre vist etter at du har forlate appen – medan du er i appen, blir dialogboksen på skjermen brukt i staden. Filnamn, baner og innhaldet i kvelvet blir aldri tekne med i varsel. | Explanatory text below the settings group |
| 9 | `notification_permission_label` | Notification permission | Varslingsløyve | Android notification permission label |
| 10 | `notification_permission_granted` | Granted | Gitt | Permission status / positive action |
| 11 | `notification_permission_request` | Tap to allow | Trykk for å tillate | Permission request hint |
| 12 | `file_operations_notifications` | File operations | Filoperasjonar | Notification channel / section title |
| 13 | `file_operation_copy_move` | Copying / moving files… | Kopierer / flyttar filer… | Progress notification title; `…` preserved |
| 14 | `file_operation_crypto` | Encrypting / decrypting files… | Krypterer / dekrypterer filer… | Progress notification title; `…` preserved |
| 15 | `file_operation_delete` | Deleting files… | Sletter filer… | Progress notification title; `…` preserved |
| 16 | `file_operation_virtual_album` | Updating albums… | Oppdaterer album… | Progress notification title; `…` preserved |
| 17 | `file_operation_generic` | File operation in progress… | Filoperasjon pågår… | Fallback progress notification title |
| 18 | `checking_files_before_transfer` | Checking files before transfer… | Sjekkar filer før overføring… | Pre-transfer verification progress |
| 19 | `checking_files_before_crypto` | Checking files before encryption… | Sjekkar filer før kryptering… | Pre-encryption verification progress |
| 20 | `deleting_files_progress` | Deleting files… | Sletter filer… | Deletion progress status |

## Unlock-time security levels (this revision)

Added the four fork-only `brute_force_level_*` keys used by the themed unlock-time dial in Settings and first-time vault setup. The label follows the chosen unlock time: 0–2.5 s fast, 2.5–5 s balanced, 5–7.5 s high, 7.5–10 s maximum. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_level_fast` | Fast unlock | Rask opplåsing | 0–2.5 s |
| 2 | `brute_force_level_balanced` | Balanced security | Balansert tryggleik | 2.5–5 s |
| 3 | `brute_force_level_high` | High security | Høg tryggleik | 5–7.5 s |
| 4 | `brute_force_level_maximum` | Maximum protection | Maksimalt vern | 7.5–10 s |

## Unlock-time level descriptions (this revision)

Added the four fork-only `brute_force_level_*_desc` keys shown under the themed unlock-time dial in Settings and first-time vault setup. The 0–2.5 s description is now `Hızlı kasa açılışı` / `Fast vault opening` (previously `Düşük KDF maliyeti`, which is no longer used). English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Nynorsk (`values-nn/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_level_fast_desc` | Fast vault opening | Rask opplåsing av kvelvet | 0–2.5 s |
| 2 | `brute_force_level_balanced_desc` | Recommended range | Tilrådd område | 2.5–5 s |
| 3 | `brute_force_level_high_desc` | More memory usage | Auka minnebruk | 5–7.5 s |
| 4 | `brute_force_level_maximum_desc` | Memory warning active | Minneåtvaring aktiv | 7.5–10 s |

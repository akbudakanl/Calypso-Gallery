# Azerbaijani (values-az) Translation Revision & English 1-to-1 Comparison

## New fork strings - bottom actions, view switch, biometrics and vault UI (16 keys)

Added to `values-az/strings.xml`. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `show_bottom_action_names` | Show action names in the bottom action bar | Alt əməliyyat çubuğunda ikonların adını göstər | Fullscreen media setting, default ON |
| 2 | `show_albums` | Show albums | Albomları göstər | Files-view overflow item that returns to the album view |
| 3 | `vault_biometric_offer_title` | Biometric access to the vault | Seyfə biometrik giriş | One-time offer after first vault creation |
| 4 | `vault_biometric_offer_message` | Open the vault with your fingerprint or face instead of a password. You don\'t need to enter your password again. | Seyfi parol əvəzinə barmaq izi və ya üz tanıma ilə aça bilərsiniz. Parolanızı yenidən daxil etməyinizə ehtiyac yoxdur. | Same |
| 5 | `vault_biometric_offer_accept` | Set up biometric access | Biometrik girişi qur | Positive button |
| 6 | `vault_biometric_offer_decline` | Not now | İndi deyil | Negative button |
| 7 | `password_strength_bad` | Very weak | Çox zəif | Vault password strength |
| 8 | `password_strength_poor` | Weak | Zəif | Vault password strength |
| 9 | `password_strength_weak` | Fair | Orta | Vault password strength |
| 10 | `password_strength_good` | Strong | Güclü | Vault password strength |
| 11 | `password_strength_excellent` | Very strong | Çox güclü | Vault password strength |
| 12 | `password_strength_hint` | A longer password with mixed characters is harder to crack. | Uzun və müxtəlif simvollardan ibarət parolanı sındırmaq daha çətindir. | Shown before typing |
| 13 | `vault_unlock_time_unit` | s | s | Localized seconds unit |
| 14 | `search_in_storage` | Search in storage | Yaddaşda axtar | Second option in unencrypted copy destination picker |
| 15 | `password_must_be_at_least_4_characters` | Password must be at least 4 characters long | Parol ən azı 4 simvoldan ibarət olmalıdır | Alphanumeric minimum length |
| 16 | `biometric_disable_confirmation` | Confirm your biometric identity to disable biometric vault access | Seyfə biometrik girişini söndürmək üçün biometrik kimliyinizi təsdiqləyin | Shown in biometric confirmation prompt |

## ADR 0003 — resumable file operations (7 new keys)

Added to `values-az/strings.xml`. Placeholders (`%1$s`, `%2$s`, `%1$d`) are preserved exactly.

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `space_unverified_warning` | Free space could not be verified (%2$s). About %1$s is required; the operation will be attempted and may stop if space runs out. | Boş yer yoxlanıla bilmədi (%2$s). Təxminən %1$s tələb olunur; əməliyyat cəhd ediləcək, lakin yer çatmazsa dayana bilər. | SAF/OTG free-space warning; `%1$s` = required size, `%2$s` = reason |
| 2 | `resume_operations_title` | Interrupted operations | Kəsilmiş əməliyyatlar | One-time process-death dialog title |
| 3 | `resume_operations_message` | %1$d operation(s) were interrupted. No data was lost — continue where they stopped? | %1$d əməliyyat kəsilib. Məlumat itkisi olmayıb; dayandığınız yerdən davam edilsin? | `%1$d` = interrupted operation count |
| 4 | `resume_continue` | Continue | Davam et | Positive dialog button |
| 5 | `resume_later` | Not now | İndi deyil | Negative dialog button |
| 6 | `resume_done` | Operations completed. | Əməliyyatlar tamamlandı. | Success toast after resume |
| 7 | `resume_failed` | Some operations could not be completed; source files were preserved. | Bəzi əməliyyatlar tamamlanamadı; mənbə fayllar qorunub. | Failure toast; no data loss |
| 8 | `space_unverified_title` | Free space unverified | Boş yer doğrulanamadı | Explicit-consent dialog title for SAF/OTG free space |
| 9 | `space_continue_anyway` | Continue anyway | Yine də davam et | Positive button: proceed without a verified space reading |

> **Scope:** `app/src/main/res/values-az/strings.xml` was reviewed from scratch: existing inaccurate translations were corrected, and all missing keys from `values/strings.xml` (347 `<string>` + 2 `<plurals>` = 349 app resource entries; `search` and 17 previously overridden shared UI keys from `fossify-commons` plus the `skip_the_recycle_bin` override, and 13 additional `fossify-commons` UI keys (`manage_excluded_folders`, `exclude`, `exclude_folder`, `lock_folder`, `change_cover_image`, `select_photo`, `use_default`, `column_count`, `open_the_recycle_bin`, `grid`, `list`, `resize`, `version_placeholder`) newly overridden because they are missing in `fossify-commons` `values-az`; 373 total comparison rows) were completed. Nine keys that were missing from `values-az/strings.xml` were translated and added (`number_of_items`, `random`, `shuffle`, `stop_showing_excluded`, `copy_to_clipboard`, `encrypt`, `decrypt`, `encrypt_album`, `decrypt_album`), and eleven existing entries were revised so that files and albums use the new \"encrypt / decrypt\" wording and so that the favorites import/export labels mention virtual albums as well. Subsequently, 22 additional keys were localized in `values-az/strings.xml`, `hide_folder_description` was removed, and `default_hidden_album` was marked `translatable="false"` (`Default Secret Album`), bringing `values-az/strings.xml` to 403 `<string>` + 2 `<plurals>` = 405 resource entries and 394 total comparison rows.
> **Key Terminology:** `Settings` → `Parametrlər` · `Default` → `Susmaya görə` · `Folder` → `Qovluq` · `Encrypted Vault` → `Şifrəli Seyf` · `Password` → `Parol` · `Alphanumeric` → `Alfanumerik` · `Encrypt/Decrypt` → `Şifrələ / Şifrəsini aç` · `Thumbnails` → `Miniatürlər` · `Recycle bin` → `Zibil qutusu` · `Clipboard` → `Mübadilə buferi`

## 1. Review of 11 Previously Existing Translations and Changes Made

| Key | Old `values-az` | New `values-az` | Status / Notes |
|---|---|---|---|
| `app_launcher_name` | Qalereya | Qalereya | Kept |
| `edit` | Redaktə | Redaktə | Kept |
| `open_camera` | Kameranı aç | Kameranı aç | Kept |
| `hidden` | (gizli) | (gizli) | Kept |
| `pin_folder` | Pin qovluğu | Qovluğu bərkit | **Fixed** (Removed English verb and incorrect noun phrase) |
| `unpin_folder` | Unpin qovluğu | Bərkitməni ləğv et | **Fixed** (Removed English verb and incorrect noun phrase) |
| `toggle_favorite` | Sevimlini göstər/gizlət | Sevimliyə əlavə et/çıxar | **Fixed** (Meaning is add/remove favorite, not show/hide) |
| `language` | Dil | Dil | Kept |
| `search` | Axtar | Axtar | Kept (Used in `fossify-commons` UI) |
| `system_default` | Sistem standartı | Sistem (susmaya görə) | **Improved** (Standard Azerbaijani software terminology) |
| `open_system_language_settings` | Sistem dil parametrlərini aç | Sistem dil parametrlərini aç | Kept |

### `fossify-commons` Inherited UI Overrides (30 Keys)

`org.fossify:commons:7.1.3` (`res/values-az/values-az.xml`) omitted these 30 UI strings (17 overridden in the previous audit and 13 added in this revision), so local overrides were added to `app/src/main/res/values-az/strings.xml`:

| # | Key (`name`) | English (`fossify-commons`) | Azerbaijani (`values-az/strings.xml`) | Source / Notes |
|---:|---|---|---|---|
| 1 | `change_date_and_time_format` | Change date and time format | Tarix və saat formatını dəyiş | Local override in `values-az/strings.xml` (Settings) |
| 2 | `password_protect_file_deletion` | Password protect file deletion and moving | Faylların silinməsini və köçürülməsini parolla qoru | Local override in `values-az/strings.xml` (Settings) |
| 3 | `keep_last_modified` | Keep old last-modified value at file operations | Fayl əməliyyatlarında köhnə son dəyişdirilmə dəyərini qoru | Local override in `values-az/strings.xml` (Settings) |
| 4 | `clear_cache` | Clear cache | Keşi təmizlə | Local override in `values-az/strings.xml` (Settings) |
| 5 | `export_settings` | Export settings | Parametrləri ixrac et | Local override in `values-az/strings.xml` (Settings) |
| 6 | `import_settings` | Import settings | Parametrləri idxal et | Local override in `values-az/strings.xml` (Settings) |
| 7 | `temporarily_show_excluded` | Temporarily show excluded | İstisna edilmişləri müvəqqəti göstər | Local override in `values-az/strings.xml` (Options menu) |
| 8 | `change_view_type` | Change view type | Görünüş növünü dəyiş | Local override in `values-az/strings.xml` (Options menu) |
| 9 | `contributors` | Contributors | Töhfə verənlər | Local override in `values-az/strings.xml` (About) |
| 10 | `known_issues` | Known issues | Məlum problemlər | Local override in `values-az/strings.xml` (About) |
| 11 | `donate_to_fossify` | Donate to Fossify | Fossify-a ianə et | Local override in `values-az/strings.xml` (About) |
| 12 | `privacy_policy` | Privacy policy | Məxfilik siyasəti | Local override in `values-az/strings.xml` (About) |
| 13 | `create_shortcut` | Create shortcut | Qısayol yarat | Local override in `values-az/strings.xml` (CAB / ViewPager menu) |
| 14 | `print` | Print | Çap et | Local override in `values-az/strings.xml` (ViewPager menu) |
| 15 | `search_files` | Search files | Faylları axtar | Local override in `values-az/strings.xml` (Search bar hint) |
| 16 | `search_folders` | Search folders | Qovluqları axtar | Local override in `values-az/strings.xml` (Search bar hint) |
| 17 | `skip_the_recycle_bin` | Skip the recycle bin, delete files directly | Zibil qutusunu keçin, faylları birbaşa silin | Local override in `values-az/strings.xml` (Delete confirmation dialog); key missing in `fossify-commons` `values-az` |
| 18 | `manage_excluded_folders` | Manage excluded folders | İstisna edilmiş qovluqları idarə et | Local override in `values-az/strings.xml` (Settings); key missing in `fossify-commons` `values-az` |
| 19 | `exclude` | Exclude | İstisna et | Local override in `values-az/strings.xml` (Album selection / long-press menu); key missing in `fossify-commons` `values-az` |
| 20 | `exclude_folder` | Exclude folder | Qovluğu istisna et | Local override in `values-az/strings.xml` (Options menu); key missing in `fossify-commons` `values-az` |
| 21 | `lock_folder` | Lock folder | Qovluğu kilidlə | Local override in `values-az/strings.xml` (Album selection / long-press menu); key missing in `fossify-commons` `values-az` |
| 22 | `change_cover_image` | Change cover image | Üz qabığı şəklini dəyiş | Local override in `values-az/strings.xml` (Album selection / long-press menu); key missing in `fossify-commons` `values-az` |
| 23 | `select_photo` | Select photo | Foto seç | Local override in `values-az/strings.xml` (Album selection, "Change cover image" dialog); key missing in `fossify-commons` `values-az` |
| 24 | `use_default` | Use default | Susmaya görə istifadə et | Local override in `values-az/strings.xml` (Album selection, "Change cover image" dialog); key missing in `fossify-commons` `values-az` |
| 25 | `column_count` | Column count | Sütun sayı | Local override in `values-az/strings.xml` (Main screen and in-album Options menu); key missing in `fossify-commons` `values-az` |
| 26 | `open_the_recycle_bin` | Open the recycle bin | Zibil qutusunu aç | Local override in `values-az/strings.xml` (In-album Options menu); key missing in `fossify-commons` `values-az` |
| 27 | `grid` | Grid | Tor | Local override in `values-az/strings.xml` (In-album Options menu, view type); key missing in `fossify-commons` `values-az` |
| 28 | `list` | List | Siyahı | Local override in `values-az/strings.xml` (In-album Options menu, view type); key missing in `fossify-commons` `values-az` |
| 29 | `resize` | Resize | Ölçüsünü dəyiş | Local override in `values-az/strings.xml` (Selection CAB menu); key missing in `fossify-commons` `values-az` |
| 30 | `version_placeholder` | Version %s | Versiya %s | Local override in `values-az/strings.xml` (About screen version text); key missing in `fossify-commons` `values-az` |

### Newly Added Fork-Only Strings (66 Keys)

These 66 strings were introduced by this fork (Calypso Gallery) and do not exist in the original Fossify Gallery:

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | 1 | `video_double_tap_duration` | Double-tap seek duration |  |
| 2 | 2 | `video_seek_duration_seconds` | %d seconds |  |
| 3 | 3 | `create_virtual_album` | Create virtual album |  |
| 4 | 4 | `new_virtual_album` | New album… |  |
| 5 | 5 | `rename_virtual_album` | Rename album |  |
| 6 | 6 | `album_name` | Album name |  |
| 7 | 7 | `virtual_folder_name` | Virtual folder name |  |
| 8 | 8 | `virtual_album_name_empty` | The album name cannot be empty |  |
| 9 | 9 | `virtual_album_exists` | An album with this name already exists |  |
| 10 | 10 | `add_to_virtual_album` | Add to virtual album | Virtual alboma əlavə et |
| 11 | 11 | `remove_from_virtual_album` | Remove from virtual album | Virtual albomdan çıxar |
| 12 | 12 | `added_to_virtual_album` | Added to \"%s\" |  |
| 13 | 13 | `already_in_virtual_album` | Already in \"%s\" |  |
| 14 | 14 | `removed_from_virtual_album` | Removed from the album |  |
| 15 | 15 | `delete_virtual_album_confirmation` | Delete the album %s? Only the album is removed, your photos and videos stay where they are. |  |
| 16 | 16 | `virtual_album_empty` | This album is empty. Select items in any folder and use \"Add to virtual album\". | Bu albom boşdur. İstənilən qovluqda elementləri seçin və \"Virtual alboma əlavə et\" seçimindən istifadə edin. |
| 17 | 17 | `virtual_album_cannot_be_modified` | This action is not available for virtual albums |  |
| 18 | 18 | `alphanumeric_password` | Alphanumeric | Alfanumerik |
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
| - | - | `block_vault_screenshots` | Block screenshots in hidden vault | Settings switch (default ON) blocking screenshots in hidden vault |
| 34 | 34 | `confirm_decrypt_hidden_file` | Are you sure you want to decrypt the hidden file(s)? |  |
| 35 | 35 | `copy_to_clipboard_disabled_in_vault` | Copying to clipboard is disabled while vault is unlocked for security. |  |
| 36 | 44 | `open_system_language_settings` | Open system language settings |  |
| 37 | 45 | `file_encrypted_successfully` | File encrypted successfully | Fayl uğurla şifrələndi                       |
| 38 | 46 | `files_encrypted_successfully` | Files encrypted successfully | Fayllar uğurla şifrələndi                     |
| 39 | 47 | `file_decrypted_successfully` | File decrypted successfully | Fayl uğurla şifrəsi açıldı                   |
| 40 | 48 | `files_decrypted_successfully` | Files decrypted successfully | Fayllar uğurla şifrəsi açıldı                 |
| 41 | 49 | `access_hidden_items_with_biometrics` | Access hidden items with biometrics | Settings switch, setup prompt title, and vault security dialog button |
| 42 | 50 | `enable_hidden_protection_first_prompt` | Please enable hidden item password protection first | Toast shown when enabling biometrics before hidden-item password protection |
| 43 | 51 | `biometric_unlock_vault` | Unlock encrypted vault | BiometricPrompt title when unlocking the encrypted vault |
| 44 | 52 | `biometric_unlock_prompt` | Verify your biometric identity to access hidden items | BiometricPrompt subtitle when unlocking hidden items |
| 45 | 53 | `biometric_setup_prompt` | Confirm your biometric identity to enable biometric vault access | BiometricPrompt subtitle when enabling biometric vault access |
| 46 | 54 | `use_password_or_pin` | Use PIN / Password | BiometricPrompt negative button to fall back to PIN/password |
| 47 | 55 | `biometric_key_invalidated` | Biometric credentials have changed. Please enter your PIN or password. | Toast shown when biometric credentials changed and Keystore key invalidated |
| 48 | 56 | `no_biometric_hardware_or_enrolled` | No biometric hardware or enrolled credentials found on this device | Toast shown when no biometric hardware or enrolled credentials exist |
| 49 | 57 | `default_hidden_album` | Default Hidden Album | Settings / picker label for default hidden album |
| 50 | 58 | `select_hidden_album` | Select Encrypted Album | Şifrəli Albom Seçin                                  |
| 51 | 59 | `create_new_hidden_album` | Create New Encrypted Album | Yeni Şifrəli Albom Yarat                     |
| 52 | 60 | `new_hidden_album` | New encrypted album | Yeni şifrəli albom                                   |
| 53 | 61 | `hidden_album_name_empty` | Album name cannot be empty | Validation error when hidden album name is blank |
| 54 | 62 | `file_hidden_to_album` | File encrypted to \"%s\" | Fayl \"%s\" albomuna şifrələndi                              |
| 55 | 63 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d fayl \"%2$s\" albomuna şifrələndi                        |
| 56 | `pull_down_to_open_vault` | Keep pulling down for Encrypted Vault | Şifrəli seyf üçün aşağı çəkməyə davam edin | Vault pull gesture hint shown while the pull is below the open threshold |
| 57 | `keep_holding_to_open_vault` | Keep holding to open Encrypted Vault | Şifrəli seyfi açmaq üçün basılı saxlayın | Vault pull gesture hint shown once the pull passes the threshold, before the 2.5 s hold completes |
| 58 | `release_to_open_vault` | Release to open Encrypted Vault | Şifrəli seyfi açmaq üçün buraxın | Vault pull gesture hint shown after holding the pull for 2.5 s; releasing opens the vault |


### Newly Added Missing Translations (9 Keys) & Revised Entries (11 Keys)

Nine keys that were missing from `values-az/strings.xml` were translated and added, and eleven existing entries were revised so that files and albums use the new \"encrypt / decrypt\" wording and so that the favorites import/export labels mention virtual albums as well.

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | `number_of_items` | Number of items | Element sayı | Sorting option shown in the album view only; overridden locally because it is missing in `fossify-commons` for this locale |
| 2 | `random` | Random | Təsadüfi | Sorting option offered in every sorting state; overridden locally because it is missing in `fossify-commons` for this locale |
| 3 | `shuffle` | Shuffle | Qarışdır | Fullscreen viewer action that randomizes the media order; app owned string that was only available in English |
| 4 | `stop_showing_excluded` | Stop showing excluded | İstisna edilmiş elementləri göstərməyi dayandır | Main screen options-menu action that ends the temporary preview of excluded folders |
| 5 | `copy_to_clipboard` | Copy to clipboard | Buferə kopyala | Fullscreen viewer action that copies a file to the clipboard |
| 6 | `encrypt` | Encrypt | Şifrələ | Replaces the inherited `hide` label for files; files and albums are encrypted, not hidden |
| 7 | `decrypt` | Decrypt | Şifrəni aç | Replaces the inherited `unhide` label for files |
| 8 | `encrypt_album` | Encrypt album | Albomu şifrələ | Album (folder) context action that encrypts the album into the vault |
| 9 | `decrypt_album` | Decrypt album | Albomun şifrəsini aç | Album (folder) context action that decrypts the album back to its original location |
| 10 | `export_favorite_paths` | Export favorites and virtual albums | Sevimliləri və virtual albomları ixrac et | Settings label; renamed because virtual albums are exported together with the favorites |
| 11 | `import_favorite_paths` | Import favorites and virtual albums | Sevimliləri və virtual albomları idxal et | Settings label; renamed because virtual albums are imported together with the favorites |
| 12 | `file_encrypted_successfully` | File encrypted successfully | Fayl uğurla şifrələndi | Success toast after encrypting a single file |
| 13 | `files_encrypted_successfully` | Files encrypted successfully | Fayllar uğurla şifrələndi | Success toast after encrypting multiple files |
| 14 | `file_decrypted_successfully` | File decrypted successfully | Fayl uğurla şifrəsi açıldı | Success toast after decrypting a single file |
| 15 | `files_decrypted_successfully` | Files decrypted successfully | Fayllar uğurla şifrəsi açıldı | Success toast after decrypting multiple files |
| 16 | `file_hidden_to_album` | File encrypted to \"%s\" | Fayl \"%s\" albomuna şifrələndi | Success toast after encrypting a single file into album `%s` |
| 17 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d fayl \"%2$s\" albomuna şifrələndi | Success toast after encrypting `%1$d` files into album `%2$s` |
| 18 | `select_hidden_album` | Select Encrypted Album | Şifrəli Albom Seçin | Dialog title for choosing the target encrypted album |
| 19 | `create_new_hidden_album` | Create New Encrypted Album | Yeni Şifrəli Albom Yarat | Action label to create a new encrypted album |
| 20 | `new_hidden_album` | New encrypted album | Yeni şifrəli albom | Input hint / default label for a new encrypted album |

### Newly Added UI, Backup, Encryption Confirmation & Vault Strings (22 Keys)

Following the previous audit, 22 additional keys were localized in `values-az/strings.xml` (`create_new_encrypted_virtual_album` and `new_album` for vault destination dialogs, plus 20 strings covering backup export/import, auto-backup prompt, encryption confirmations, delete-behavior options, album lock/unlock labels, and progress notifications; `hide_folder_description` was removed from the app, and `default_hidden_album` was marked `translatable="false"` as `Default Secret Album`):

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | `create_new_encrypted_virtual_album` | Create new encrypted virtual album | Yeni şifrəli virtual albom yarat | Dialog option for creating an encrypted virtual album inside the vault |
| 2 | `new_album` | New album | Yeni albom | Compact neutral button label in the move destination dialog |
| 3 | `filename_without_zip` | Filename (without .zip) | Fayl adı (.zip olmadan) | Input hint in the export favorites and virtual albums dialog |
| 4 | `favorites_already_exist` | You already have a favorites list | Artıq sevimlilər siyahınız var | Dialog title when importing favorites while a favorites list already exists |
| 5 | `delete_existing_list` | Delete existing list | Mövcud siyahını sil | Radio option to replace the existing favorites list on import |
| 6 | `merge_lists` | Merge lists | Siyahıları birləşdir | Radio option to merge imported favorites with the existing list |
| 7 | `confirm` | Confirm | Təsdiq et | Positive button label in the favorites import conflict dialog |
| 8 | `confirm_encrypt_album` | Are you sure you want to encrypt this album? All of its media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | Bu albomu şifrələmək istədiyinizə əminsiniz? Daxilindəki bütün media faylları şifrələnəcək və şifrəli seyfə köçürüləcək. Orijinal fayllar silinəcək. | Confirmation prompt before encrypting a single album into the vault |
| 9 | `confirm_encrypt_albums` | Are you sure you want to encrypt the selected albums? All of their media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | Seçilmiş albomları şifrələmək istədiyinizə əminsiniz? Daxilindəki bütün media faylları şifrələnəcək və şifrəli seyfə köçürüləcək. Orijinal fayllar silinəcək. | Confirmation prompt before encrypting multiple selected albums into the vault |
| 10 | `confirm_encrypt_file` | Are you sure you want to encrypt this file? It will be encrypted and moved to the encrypted vault. The original file will be deleted. | Bu faylı şifrələmək istədiyinizə əminsiniz? Fayl şifrələnəcək və şifrəli seyfə köçürüləcək. Orijinal fayl silinəcək. | Confirmation prompt before encrypting a single file into the vault |
| 11 | `confirm_encrypt_files` | Are you sure you want to encrypt the selected files? They will be encrypted and moved to the encrypted vault. The original files will be deleted. | Seçilmiş faylları şifrələmək istədiyinizə əminsiniz? Fayllar şifrələnəcək və şifrəli seyfə köçürüləcək. Orijinal fayllar silinəcək. | Confirmation prompt before encrypting multiple selected files into the vault |
| 12 | `confirm_encrypt_files_to_vault` | The selected files will be encrypted when they are copied into the encrypted vault. Do you want to continue? | Seçilmiş fayllar şifrəli seyfə kopyalanarkən şifrələnəcək. Davam etmək istəyirsiniz? | Confirmation prompt when copying unencrypted files into an encrypted vault destination |
| 13 | `encrypt_decrypt_notifications` | Encrypt / Decrypt | Şifrələmə / Şifrəni açma | Notification channel name for ongoing encrypt/decrypt progress notifications |
| 14 | `allow_batch_renaming` | Allow batch renaming when multiple files are selected | Birdən çox fayl seçildikdə toplu ad dəyişməyə icazə ver | Retained in values/strings.xml for parity (its Settings switch was removed in a6a78d444) |
| 15 | `keep_original_remove_from_virtual_album` | Keep the original file, remove it only from this virtual album | Orijinal faylı saxla, yalnız bu virtual albomdan çıxar | Checkbox in delete confirmation dialog when deleting from a virtual album |
| 16 | `keep_original_remove_from_favorites` | Keep the original file, remove it only from favorites | Orijinal faylı saxla, yalnız sevimlilərdən çıxar | Checkbox in delete confirmation dialog when deleting from favorites |
| 17 | `delete_original_from_favorites_and_virtual_albums` | Delete the original version of a file when it is deleted from favorites and virtual albums | Fayl sevimlilərdən və virtual albomlardan silindikdə onun orijinal versiyasını da sil | Settings switch controlling default delete behavior from favorites and virtual albums |
| 18 | `cannot_target_encrypted_album` | Encrypted albums cannot be targeted for this operation | Bu əməliyyat üçün şifrəli albomlar hədəf seçilə bilməz | Toast shown when an encrypted album cannot be targeted for an operation |
| 19 | `cannot_mix_encrypted_and_unencrypted` | Cannot combine encrypted and unencrypted items in this operation | Bu əməliyyatda şifrəli və şifrəsiz elementləri birləşdirmək olmaz | Toast shown when an operation attempts to combine encrypted and unencrypted items |
| 20 | `lock_album` | Lock album | Albomu kilidlə | Album selection menu action replacing lock_folder |
| 21 | `unlock_album` | Unlock album | Albomun kilidini aç | Album selection menu action replacing unlock_folder |
| 22 | `auto_backup_prompt_message` | Do you want your favorites and virtual albums to be regularly written to system storage outside of app memory? This will ensure that you do not lose your favorites list and virtual albums if you uninstall the app. You can manually create a backup from app settings at any time. | Sevimlilərinizin və virtual albomlarınızın tətbiq yaddaşından kənarda sistem yaddaşına müntəzəm olaraq yazılmasını istəyirsiniz? Bu, tətbiqi sildiyiniz halda sevimlilər siyahınızı və virtual albomlarınızı itirməməyinizi təmin edəcək. İstənilən vaxt tətbiq parametrlərindən əl ilə ehtiyat nüsxə yarada bilərsiniz. | Full-screen prompt asking whether to regularly back up favorites and virtual albums to shared storage |
| 23 | `auto_backup_favorites_and_virtual_albums` | Back up favorites and virtual albums to device storage | Sevimlilərin və virtual albomların cihaz yaddaşında ehtiyat nüsxəsini yarat | Settings switch under File operations (shown after first auto-backup prompt) |

## 2. Full 1-to-1 Comparison: English vs. Azerbaijani

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) |
|---:|---|---|---|
| 1 | `app_launcher_name` | Gallery | Qalereya |
| 2 | `edit` | Edit | Redaktə |
| 3 | `open_camera` | Open camera | Kameranı aç |
| 4 | `hidden` | (hidden) | (gizli) |
| 5 | `excluded` | (excluded) | (istisna edilmiş) |
| 6 | `pin_folder` | Pin folder | Qovluğu bərkit |
| 7 | `unpin_folder` | Unpin folder | Bərkitməni ləğv et |
| 8 | `pin_to_the_top` | Pin to the top | Yuxarıya bərkit |
| 9 | `show_all` | Show all folders content | Bütün qovluqların məzmununu göstər |
| 10 | `all_folders` | All folders | Bütün qovluqlar |
| 11 | `folder_view` | Switch to folder view | Qovluq görünüşünə keç |
| 12 | `other_folder` | Other folder | Digər qovluq |
| 13 | `show_on_map` | Show on map | Xəritədə göstər |
| 14 | `unknown_location` | Unknown location | Naməlum məkan |
| 15 | `volume` | Volume | Səs səviyyəsi |
| 16 | `brightness` | Brightness | Parlaqlıq |
| 17 | `lock_orientation` | Lock orientation | İstiqaməti kilidlə |
| 18 | `unlock_orientation` | Unlock orientation | İstiqamətin kilidini aç |
| 19 | `change_orientation` | Change orientation | İstiqaməti dəyiş |
| 20 | `force_portrait` | Force portrait | Şaquli rejimi məcbur et |
| 21 | `force_landscape` | Force landscape | Üfüqi rejimi məcbur et |
| 22 | `force_landscape_reverse` | Force landscape (reverse) | Üfüqi rejimi məcbur et (əks) |
| 23 | `use_default_orientation` | Use default orientation | Susmaya görə istiqaməti işlət |
| 24 | `fix_date_taken` | Fix Date Taken value | Çəkilmə tarixi dəyərini düzəlt |
| 25 | `fixing` | Fixing… | Düzəldilir… |
| 26 | `dates_fixed_successfully` | Dates fixed successfully | Tarixlər uğurla düzəldildi |
| 27 | `no_date_takens_found` | No Date Taken values have been found | Heç bir çəkilmə tarixi dəyəri tapılmadı |
| 28 | `share_resized` | Share a resized version | Ölçüsü dəyişdirilmiş versiyanı paylaş |
| 29 | `switch_to_file_search` | Switch to file search across all visible folders | Bütün görünən qovluqlar üzrə fayl axtarışına keç |
| 30 | `set_as_default_folder` | Set as default folder | Susmaya görə qovluq təyin et |
| 31 | `unset_as_default_folder` | Unset as default folder | Susmaya görə qovluq təyinatını ləğv et |
| 32 | `reorder_by_dragging` | Reorder folders by dragging | Qovluqları sürüşdürərək yenidən sırala |
| 33 | `reorder_by_dragging_pro` | Reorder folders by dragging (Pro) | Qovluqları sürüşdürərək yenidən sırala (Pro) |
| 34 | `restore_to_path` | Restoring to \'%s\' | \'%s\' yoluna bərpa edilir |
| 35 | `full_storage_permission_required` | Fossify Gallery needs full access to display all your photos and videos. Go to Settings &gt; Permissions &gt; Photos and videos &gt; Allow all. | Fossify Qalereya bütün foto və videolarınızı göstərmək üçün tam giriş icazəsinə ehtiyac duyur. Parametrlər &gt; İcazələr &gt; Foto və videolar &gt; Hamısına icazə ver bölməsinə keçin. |
| 36 | `filter_media` | Filter media | Medianı süzgəcdən keçir |
| 37 | `images` | Images | Şəkillər |
| 38 | `videos` | Videos | Videolar |
| 39 | `gifs` | GIFs | GIF-lər |
| 40 | `raw_images` | RAW images | RAW şəkillər |
| 41 | `svgs` | SVGs | SVG-lər |
| 42 | `portraits` | Portraits | Portretlər |
| 43 | `no_media_with_filters` | No media files have been found with the selected filters. | Seçilmiş süzgəclərlə heç bir media faylı tapılmadı. |
| 44 | `change_filters_underlined` | <u>Change filters</u> | <u>Süzgəcləri dəyiş</u> |
| 45 | `exclude_folder_description` | This will exclude the selection together with its subfolders from Fossify Gallery only. You can manage excluded folders in Settings. | Bu, seçilmiş qovluğu alt qovluqları ilə birlikdə yalnız Fossify Qalereyadan istisna edəcək. İstisna edilmiş qovluqları Parametrlərdən idarə edə bilərsiniz. |
| 46 | `exclude_folder_parent` | Exclude a parent instead? | Əvəzində üst qovluq istisna edilsin? |
| 47 | `excluded_activity_placeholder` | Excluding folders will make them together with their subfolders hidden just in Fossify Gallery, they will still be visible in other applications.\n\nIf you want to hide them from other apps too, use the Hide function. | Qovluqları istisna etmək onları alt qovluqları ilə birlikdə yalnız Fossify Qalereyada gizlədər, digər tətbiqlərdə görünməyə davam edər.\n\nƏgər onları digər tətbiqlərdən də gizlətmək istəyirsinizsə, Gizlət funksiyasından istifadə edin. |
| 48 | `hidden_folders` | Hidden folders | Gizli qovluqlar |
| 49 | `manage_hidden_folders` | Manage hidden folders | Gizli qovluqları idarə et |
| 50 | `hidden_folders_placeholder` | Seems like you don\'t have any folders hidden with a \".nomedia\" file. | Görünür, \".nomedia\" faylı ilə gizlədilmiş heç bir qovluğunuz yoxdur. |
| 51 | `hidden_all_files` | You have to grant the app All Files access to see hidden files, else it cannot work. | Gizli faylları görmək üçün tətbiqə Bütün Fayllara giriş icazəsi verməlisiniz, əks halda işləməyəcək. |
| 52 | `cant_unhide_folder` | If a folder or one of its parent folders has a dot before its name, it is hidden and cannot be unhidden like this. You have to remove the dot by renaming it. | Əgər qovluğun və ya onun üst qovluqlarından birinin adının əvvəlində nöqtə varsa, o gizlidir və bu yolla gizlədilməsi ləğv edilə bilməz. Adını dəyişərək nöqtəni silməlisiniz. |
| 53 | `include_folders` | Included folders | Daxil edilmiş qovluqlar |
| 54 | `manage_included_folders` | Manage included folders | Daxil edilmiş qovluqları idarə et |
| 55 | `add_folder` | Add folder | Qovluq əlavə et |
| 56 | `included_activity_placeholder` | If you have some folders which contain media, but were not recognized by the app, you can add them manually here.\n\nAdding some items here will not exclude any other folder. | Tərkibində media olan, lakin tətbiq tərəfindən tanınmayan qovluqlarınız varsa, onları bura əl ilə əlavə edə bilərsiniz.\n\nBura element əlavə etmək digər qovluqları istisna etməyəcək. |
| 57 | `no_media_add_included` | No media files have been found. You can solve it by adding the folders containing media files manually. | Heç bir media faylı tapılmadı. Media faylları olan qovluqları əl ilə əlavə etməklə bunu həll edə bilərsiniz. |
| 58 | `resize_and_save` | Resize selection and save | Seçimi yenidən ölçüləndir və saxla |
| 59 | `width` | Width | En |
| 60 | `height` | Height | Hündürlük |
| 61 | `keep_aspect_ratio` | Keep aspect ratio | Tərəf nisbətini qoru |
| 62 | `invalid_values` | Please enter a valid resolution | Zəhmət olmasa, düzgün qətnamə daxil edin |
| 63 | `resize_multiple_images` | Resize multiple images | Birdən çox şəklin ölçüsünü dəyiş |
| 64 | `resize_factor` | Resize factor | Ölçü dəyişmə əmsalı |
| 65 | `resize_factor_info` | Resize images to the given percentage, value must be within 10 and 90. | Şəkillərin ölçüsünü göstərilən faizə görə dəyişin, dəyər 10 ilə 90 arasında olmalıdır. |
| 66 | `resize_factor_error` | Enter a number between 10 and 90 | 10 ilə 90 arasında bir rəqəm daxil edin |
| 67 | `images_resized_successfully` | Images resized successfully | Şəkillərin ölçüsü uğurla dəyişdirildi |
| 68 | `editor` | Editor | Redaktor |
| 69 | `basic_editor` | Basic Editor | Sadə Redaktor |
| 70 | `advanced_editor` | Advanced Editor | Təkmil Redaktor |
| 71 | `rotate` | Rotate | Döndər |
| 72 | `invalid_image_path` | Invalid image path | Yanlış şəkil yolu |
| 73 | `invalid_video_path` | Invalid video path | Yanlış video yolu |
| 74 | `image_editing_failed` | Image editing failed | Şəklin redaktəsi uğursuz oldu |
| 75 | `video_editing_failed` | Video editing failed | Videonun redaktəsi uğursuz oldu |
| 76 | `image_editing_cancelled` | Image editing cancelled | Şəklin redaktəsi ləğv edildi |
| 77 | `video_editing_cancelled` | Video editing cancelled | Videonun redaktəsi ləğv edildi |
| 78 | `file_edited_successfully` | File edited successfully | Fayl uğurla redaktə edildi |
| 79 | `image_edited_successfully` | Image edited successfully | Şəkil uğurla redaktə edildi |
| 80 | `video_edited_successfully` | Video edited successfully | Video uğurla redaktə edildi |
| 81 | `edit_image_with` | Edit image with: | Şəkli bununla redaktə et: |
| 82 | `edit_video_with` | Edit video with: | Videonu bununla redaktə et: |
| 83 | `no_image_editor_found` | No image editor found | Şəkil redaktoru tapılmadı |
| 84 | `no_video_editor_found` | No video editor found | Video redaktoru tapılmadı |
| 85 | `unknown_file_location` | Unknown file location | Naməlum fayl yeri |
| 86 | `overwrite_original` | Overwrite original | Orijinalın üzərinə yaz |
| 87 | `error_saving_file` | Could not overwrite the source file | Mənbə faylın üzərinə yazmaq mümkün olmadı |
| 88 | `rotate_left` | Rotate left | Sola döndər |
| 89 | `rotate_right` | Rotate right | Sağa döndər |
| 90 | `rotate_one_eighty` | Rotate by 180º | 180º döndər |
| 91 | `transform` | Transform | Çevirmə |
| 92 | `crop` | Crop | Kəs |
| 93 | `draw` | Draw | Çək |
| 94 | `flip` | Flip | Əks etdir |
| 95 | `flip_horizontally` | Flip horizontally | Üfüqi əks etdir |
| 96 | `flip_vertically` | Flip vertically | Şaquli əks etdir |
| 97 | `free_aspect_ratio` | Free | Sərbəst |
| 98 | `other_aspect_ratio` | Other | Digər |
| 99 | `wallpaper` | Wallpaper | Divar kağızı |
| 100 | `set_as_wallpaper` | Set as Wallpaper | Divar kağızı kimi təyin et |
| 101 | `set_as_wallpaper_failed` | Setting as Wallpaper failed | Divar kağızı kimi təyin etmək uğursuz oldu |
| 102 | `set_as_wallpaper_with` | Set as wallpaper with: | Bununla divar kağızı kimi təyin et: |
| 103 | `setting_wallpaper` | Setting wallpaper… | Divar kağızı təyin edilir… |
| 104 | `wallpaper_set_successfully` | Wallpaper set successfully | Divar kağızı uğurla təyin edildi |
| 105 | `portrait_aspect_ratio` | Portrait aspect ratio | Şaquli tərəf nisbəti |
| 106 | `landscape_aspect_ratio` | Landscape aspect ratio | Üfüqi tərəf nisbəti |
| 107 | `home_screen` | Home screen | Əsas ekran |
| 108 | `lock_screen` | Lock screen | Kilid ekranı |
| 109 | `home_and_lock_screen` | Home and lock screen | Əsas və kilid ekranı |
| 110 | `allow_changing_aspect_ratio` | Allow changing the aspect ratio | Tərəf nisbətini dəyişməyə icazə ver |
| 111 | `slideshow` | Slideshow | Slayd-şou |
| 112 | `interval` | Interval | İnterval |
| 113 | `include_photos` | Include photos | Fotoları daxil et |
| 114 | `include_videos` | Include videos | Videoları daxil et |
| 115 | `include_gifs` | Include GIFs | GIF-ləri daxil et |
| 116 | `random_order` | Random order | Təsadüfi sıra |
| 117 | `move_backwards` | Move backwards | Geriyə get |
| 118 | `loop_slideshow` | Loop slideshow | Slayd-şounu təkrarla |
| 119 | `animation` | Animation | Animasiya |
| 120 | `no_animation` | None | Yoxdur |
| 121 | `fade` | Fade | Solğunlaşma |
| 122 | `slide` | Slide | Sürüşmə |
| 123 | `slideshow_ended` | The slideshow ended | Slayd-şou başa çatdı |
| 124 | `no_media_for_slideshow` | No media for the slideshow have been found | Slayd-şou üçün media tapılmadı |
| 125 | `group_direct_subfolders` | Group direct subfolders | Birbaşa alt qovluqları qruplaşdır |
| 126 | `group_by` | Group by | Qruplaşdırma |
| 127 | `do_not_group_files` | Do not group files | Faylları qruplaşdırma |
| 128 | `by_folder` | Folder | Qovluq |
| 129 | `by_last_modified` | Last modified | Son dəyişdirilmə |
| 130 | `by_last_modified_daily` | Last modified (daily) | Son dəyişdirilmə (gündəlik) |
| 131 | `by_last_modified_monthly` | Last modified (monthly) | Son dəyişdirilmə (aylıq) |
| 132 | `by_date_taken` | Date taken | Çəkilmə tarixi |
| 133 | `by_date_taken_daily` | Date taken (daily) | Çəkilmə tarixi (gündəlik) |
| 134 | `by_date_taken_monthly` | Date taken (monthly) | Çəkilmə tarixi (aylıq) |
| 135 | `by_file_type` | File type | Fayl növü |
| 136 | `by_extension` | Extension | Uzantı |
| 137 | `show_file_count_section_header` | Show file count at section headers | Bölmə başlıqlarında fayl sayını göstər |
| 138 | `grouping_and_sorting` | Please note that grouping and sorting are 2 independent fields | Nəzərə alın ki, qruplaşdırma və sıralama 2 müstəqil sahədir |
| 139 | `folder_on_widget` | Folder shown on the widget: | Vidcetdə göstərilən qovluq: |
| 140 | `show_folder_name` | Show folder name | Qovluq adını göstər |
| 141 | `autoplay_videos` | Play videos automatically | Videoları avtomatik oxut |
| 142 | `remember_last_video_position` | Remember last video playback position | Videonun son oxutma mövqeyini yadda saxla |
| 143 | `loop_videos` | Loop videos | Videoları təkrarla |
| 144 | `video_double_tap_duration` | Double-tap seek duration | İkiqat toxunuşla irəli/geri çəkmə müddəti |
| 145 | `video_seek_duration_seconds` | %d seconds | %d saniyə |
| 146 | `animate_gifs` | Animate GIFs at thumbnails | Miniatürlərdə GIF-ləri canlandır |
| 147 | `max_brightness` | Max brightness when viewing fullscreen media | Tam ekran mediaya baxarkən maksimum parlaqlıq |
| 148 | `render_ultra_hdr_photos` | Show Ultra HDR photos in HDR | Ultra HDR fotoları HDR rejimində göstər |
| 149 | `crop_thumbnails` | Crop thumbnails into squares | Miniatürləri kvadrat şəklində kəs |
| 150 | `show_thumbnail_video_duration` | Show video durations | Video müddətlərini göstər |
| 151 | `screen_rotation_by` | Rotate fullscreen media by | Tam ekran medianı buna görə döndər: |
| 152 | `screen_rotation_system_setting` | System setting | Sistem parametri |
| 153 | `screen_rotation_device_rotation` | Device rotation | Cihazın döndərilməsi |
| 154 | `screen_rotation_aspect_ratio` | Aspect ratio | Tərəf nisbəti |
| 155 | `black_background_at_fullscreen` | Black background at fullscreen media | Tam ekran mediada qara fon |
| 156 | `scroll_thumbnails_horizontally` | Scroll thumbnails horizontally | Miniatürləri üfüqi sürüşdür |
| 157 | `hide_system_ui_at_fullscreen` | Automatically hide system UI at fullscreen media | Tam ekran mediada sistem interfeysini avtomatik gizlət |
| 158 | `delete_empty_folders` | Delete empty folders after deleting their content | Məzmunu sildikdən sonra boş qovluqları sil |
| 159 | `keep_screen_on_fullscreen_photos` | Keep the screen on while viewing fullscreen photos | Tam ekran fotolara baxarkən ekranı açıq saxla |
| 160 | `allow_photo_gestures` | Allow controlling photo brightness with vertical gestures | Şaquli jestlərlə foto parlaqlığını idarə etməyə icazə ver |
| 161 | `allow_video_gestures` | Allow controlling video volume and brightness with vertical gestures | Şaquli jestlərlə video səsini və parlaqlığını idarə etməyə icazə ver |
| 162 | `playback_speed` | Playback speed | Oxutma sürəti |
| 163 | `show_media_count` | Show folder media count on the main view | Əsas görünüşdə qovluq media sayını göstər |
| 164 | `show_extended_details` | Show extended details over fullscreen media | Tam ekran media üzərində ətraflı məlumatı göstər |
| 165 | `manage_extended_details` | Manage extended details | Ətraflı məlumatı idarə et |
| 166 | `one_finger_zoom` | Allow one finger zoom at fullscreen media | Tam ekran mediada tək barmaqla yaxınlaşdırmaya icazə ver |
| 167 | `allow_instant_change` | Allow instantly changing media by clicking on screen sides | Ekran kənarlarına klikləməklə medianı dərhal dəyişməyə icazə ver |
| 168 | `allow_deep_zooming_images` | Allow deep zooming images | Şəkillərin dərin yaxınlaşdırılmasına icazə ver |
| 169 | `hide_extended_details` | Hide extended details when status bar is hidden | Status sətri gizlədildikdə ətraflı məlumatı gizlət |
| 170 | `show_at_bottom` | Show some action buttons at the bottom of the screen | Ekranın aşağısında bəzi əməliyyat düymələrini göstər |
| 171 | `show_recycle_bin` | Show the recycle bin on the folders screen | Qovluqlar ekranında zibil qutusunu göstər |
| 172 | `deep_zoomable_images` | Deep zoomable images | Dərin yaxınlaşdırılan şəkillər |
| 173 | `show_highest_quality` | Show images in the highest possible quality | Şəkilləri mümkün olan ən yüksək keyfiyyətdə göstər |
| 174 | `show_recycle_bin_last` | Show the recycle bin as the last item on the main screen | Zibil qutusunu əsas ekranda sonuncu element kimi göstər |
| 175 | `allow_down_gesture` | Allow closing the fullscreen view with a down gesture | Aşağıya doğru jestlə tam ekran görünüşünü bağlamağa icazə ver |
| 176 | `allow_one_to_one_zoom` | Allow 1:1 zooming in with two double taps | İki dəfə ikiqat toxunuşla 1:1 yaxınlaşdırmaya icazə ver |
| 177 | `open_videos_on_separate_screen` | Play videos on a separate screen with horizontal gestures | Videoları üfüqi jestlərlə ayrı ekranda oxut |
| 178 | `on_video_tap` | On video tap | Videoya toxunduqda |
| 179 | `open_in_app_player` | Open in-app player | Tətbiqdaxili pleyerdə aç |
| 180 | `open_system_default_player` | Open in another app | Digər tətbiqdə aç |
| 181 | `show_notch` | Show a notch if available | Varsa ekran kəsiyini göstər |
| 182 | `allow_rotating_gestures` | Allow rotating images with gestures | Şəkilləri jestlərlə döndərməyə icazə ver |
| 183 | `file_loading_priority` | File loading priority | Fayl yükləmə prioriteti |
| 184 | `speed` | Speed | Sürət |
| 185 | `compromise` | Compromise | Tarazlı |
| 186 | `avoid_showing_invalid_files` | Avoid showing invalid files | Etibarsız faylları göstərməkdən yayın |
| 187 | `show_image_file_types` | Show image file types | Şəkil faylı növlərini göstər |
| 188 | `allow_zooming_videos` | Allow zooming videos with double tapping them | İkiqat toxunuşla videoları yaxınlaşdırmağa icazə ver |
| 189 | `folder_thumbnail_style` | Folder thumbnail style | Qovluq miniatürü üslubu |
| 190 | `file_thumbnail_style` | File thumbnail style | Fayl miniatürü üslubu |
| 191 | `mark_favorite_items` | Mark favorite items | Sevimli elementləri işarələ |
| 192 | `thumbnail_spacing` | Thumbnail spacing | Miniatürlər arası məsafə |
| 193 | `show_file_count_line` | Show file count on a separate line | Fayl sayını ayrı sətirdə göstər |
| 194 | `show_file_count_brackets` | Show file count in brackets | Fayl sayını mötərizədə göstər |
| 195 | `show_file_count_none` | Do not show file count | Fayl sayını göstərmə |
| 196 | `limit_folder_title` | Limit long folder titles to 1 line | Uzun qovluq adlarını 1 sətirlə məhdudlaşdır |
| 197 | `square` | Square | Kvadrat |
| 198 | `rounded_corners` | Rounded corners | Dairəvi künclər |
| 199 | `export_favorite_paths` | Export favorites and virtual albums | Sevimliləri və virtual albomları ixrac et |
| 200 | `import_favorite_paths` | Import favorites and virtual albums | Sevimliləri və virtual albomları idxal et |
| 201 | `paths_imported_successfully` | Paths imported successfully | Yollar uğurla idxal edildi |
| 202 | `media_management_prompt` | To make sure that all file operations work reliably, please make this app a Media management app in your device settings. | Bütün fayl əməliyyatlarının etibarlı işləməsini təmin etmək üçün, zəhmət olmasa, cihaz parametrlərində bu tətbiqi Media idarəetmə tətbiqi kimi təyin edin. |
| 203 | `password_protect_excluded` | Password protect excluded folder visibility | İstisna edilmiş qovluqların görünməsini parolla qoru |
| 204 | `media_management_manual` | Something went wrong, please go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | Xəta baş verdi. Zəhmət olmasa, cihazınızın Parametrlər - Tətbiqlər - Xüsusi tətbiq girişi - Media idarəetmə tətbiqləri bölməsinə keçin və bu tətbiqə medianı idarə etməyə icazə verin. |
| 205 | `media_management_note` | If the redirection does not work, please go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | Yönləndirmə işləməzsə, zəhmət olmasa, cihazınızın Parametrlər - Tətbiqlər - Xüsusi tətbiq girişi - Media idarəetmə tətbiqləri bölməsinə keçin və bu tətbiqə medianı idarə etməyə icazə verin. |
| 206 | `media_management_alternative` | If you do not want to do it, you can also go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | Bunu etmək istəmirsinizsə, cihazınızın Parametrlər - Tətbiqlər - Xüsusi tətbiq girişi - Media idarəetmə tətbiqləri bölməsinə keçib bu tətbiqə medianı idarə etməyə icazə verə bilərsiniz. |
| 207 | `alternative_media_access` | Alternatively, you can allow accessing media files only. In that case you will not be able to work with hidden files though. | Alternativ olaraq, yalnız media fayllarına girişə icazə verə bilərsiniz. Lakin bu halda gizli fayllarla işləyə bilməyəcəksiniz. |
| 208 | `media_only` | Media only | Yalnız media |
| 209 | `all_files` | All files | Bütün fayllar |
| 210 | `main_screen_view_on_launch` | Main screen view on launch | Açılışda əsas ekran görünüşü |
| 211 | `show_folders` | Show folders | Qovluqları göstər |
| 212 | `show_files` | Show files | Faylları göstər |
| 213 | `show_all_folders` | Show a menu button for toggling Show All Folders Content quickly | Bütün qovluqların məzmununu göstər seçimini tez dəyişmək üçün menyu düyməsini göstər |
| 214 | `thumbnails` | Thumbnails | Miniatürlər |
| 215 | `fullscreen_media` | Fullscreen media | Tam ekran media |
| 216 | `extended_details` | Extended details | Ətraflı məlumat |
| 217 | `bottom_actions` | Bottom actions | Alt əməliyyatlar |
| 218 | `manage_bottom_actions` | Manage visible bottom actions | Görünən alt əməliyyatları idarə et |
| 219 | `toggle_favorite` | Toggle favorite | Sevimliyə əlavə et/çıxar |
| 220 | `toggle_file_visibility` | Toggle file visibility | Faylın görünməsini dəyiş |
| 221 | `failed_to_load_media` | Failed to load media. | Medianı yükləmək mümkün olmadı. |
| 222 | `file_is_malformed_or_corrupted` | File is malformed or corrupted. | Fayl pozulub və ya yanlış formatdadır. |
| 223 | `media_exceeds_device_capabilities` | Media exceeds device capabilities. | Media cihazın imkanlarını aşır. |
| 224 | `unsupported_format` | Unsupported format. | Dəstəklənməyən format. |
| 225 | `video_no_sound` | This video has no audio | Bu videoda səs yoxdur |
| 226 | `faq_1_title` | How can I make Fossify Gallery the default device gallery? | Fossify Qalereyanı necə susmaya görə cihaz qalereyası edə bilərəm? |
| 227 | `faq_1_text` | First you have to find the currently default gallery in the Apps section of your device settings, look for a button that says something like \"Open by default\", click on it, then select \"Clear defaults\". The next time you will try opening an image or video you should see an app picker, where you can select Fossify Gallery and make it the default app. | Əvvəlcə cihaz parametrlərinizin Tətbiqlər bölməsində hazırda susmaya görə təyin edilmiş qalereyanı tapmalı, \"Susmaya görə aç\" kimi bir düyməyə toxunub \"Susmaya görə tənzimləmələri təmizlə\" seçimini etməlisiniz. Növbəti dəfə şəkil və ya video açmağa çalışdığınızda tətbiq seçicisi görünəcək; oradan Fossify Qalereyanı seçib susmaya görə tətbiq edə bilərsiniz. |
| 228 | `faq_2_title` | I locked the app with a password, but I forgot it. What can I do? | Tətbiqi parolla kilidlədim, amma unutdum. Nə edə bilərəm? |
| 229 | `faq_2_text` | You can solve it in 2 ways. You can either reinstall the app, or find the app in your device settings and select \"Clear data\". It will reset all your settings, it will not remove any media files. | Bunu 2 yolla həll edə bilərsiniz. Ya tətbiqi yenidən quraşdırın, ya da cihaz parametrlərindən tətbiqi tapıb \"Məlumatları təmizlə\" seçin. Bu, bütün parametrlərinizi sıfırlayacaq, lakin heç bir media faylını silməyəcək. |
| 230 | `faq_3_title` | How can I make an album always appear at the top? | Bir albomun həmişə ən yuxarıda görünməsini necə təmin edə bilərəm? |
| 231 | `faq_3_text` | You can long press the desired album and select the Pin icon at the actions menu, that will pin it to the top. You can pin multiple folders too, pinned items will be sorted by the default sorting method. | İstədiyiniz albomun üzərinə uzun basıb əməliyyatlar menyusundan Bərkit işarəsini seçə bilərsiniz, bu onu yuxarıya bərkidəcək. Birdən çox qovluğu da bərkidə bilərsiniz; bərkidilmiş elementlər susmaya görə sıralama üsulu ilə düzüləcək. |
| 232 | `faq_4_title` | How can I fast forward videos? | Videoları necə sürətlə irəli çəkə bilərəm? |
| 233 | `faq_4_text` | You can do it by double tapping the side of the screen, or tapping the current or max duration texts near the seekbar. If you enable opening videos on a separate screen in the app settings, you can use horizontal gestures too. | Bunu ekranın kənarına iki dəfə toxunmaqla və ya axtarış zolağının yanındakı cari və ya maksimum müddət yazılarına toxunmaqla edə bilərsiniz. Tətbiq parametrlərində videoların ayrı ekranda açılmasını aktivləşdirsəniz, üfüqi jestlərdən də istifadə edə bilərsiniz. |
| 234 | `faq_5_title` | What is the difference between hiding and excluding a folder? | Qovluğu gizlətmək ilə istisna etmək arasında fərq nədir? |
| 235 | `faq_5_text` | Exclude prevents displaying the folder only in Fossify Gallery, while Hide works system-wise and it hides the folder from other galleries too. It works by creating an empty \".nomedia\" file in the given folder, which you can then remove with any file manager too. Note that some devices do not allow hiding folders like Camera, Screenshots and Downloads. | İstisna et qovluğun yalnız Fossify Qalereyada göstərilməsinin qarşısını alır, Gizlət isə sistem səviyyəsində işləyir və qovluğu digər qalereyalardan da gizlədir. Bu, həmin qovluqda boş \".nomedia\" faylı yaratmaqla işləyir və onu istənilən fayl meneceri ilə də silə bilərsiniz. Nəzərə alın ki, bəzi cihazlar Camera, Screenshots və Downloads kimi qovluqların gizlədilməsinə icazə vermir. |
| 236 | `faq_6_title` | Why do folders with music cover art or stickers show up? | Niyə musiqi üz qabıqları və ya stikerlər olan qovluqlar görünür? |
| 237 | `faq_6_text` | It can happen that you will see some unusual albums show up. You can easily exclude them by long pressing them and selecting Exclude. In the next dialog you can then select the parent folder, chances are it will prevent the other related albums showing up too. | Bəzən qeyri-adi albomların göründüyünü müşahidə edə bilərsiniz. Onların üzərinə uzun basıb İstisna et seçməklə asanlıqla gizlədə bilərsiniz. Növbəti pəncərədə üst qovluğu seçə bilərsiniz; çox güman ki, bu, əlaqəli digər albomların da görünməsinin qarşısını alacaq. |
| 238 | `faq_7_title` | A folder with images isn\'t showing up, or it doesn\'t show all items.  What can I do? | Şəkillər olan qovluq görünmür və ya bütün elementləri göstərmir. Nə edə bilərəm? |
| 239 | `faq_7_text` | That can have multiple reasons, but solving it is easy. Just go in Settings -&gt; Manage Included Folders, select Plus and navigate to the required folder. | Bunun bir neçə səbəbi ola bilər, lakin həlli asandır. Sadəcə Parametrlər -&gt; Daxil edilmiş qovluqları idarə et bölməsinə keçin, Üstəgəl işarəsini seçin və lazım olan qovluğa gedin. |
| 240 | `faq_8_title` | What if I want just a few particular folders visible? | Bəs yalnız bir neçə müəyyən qovluğun görünməsini istəyirəmsə nə edim? |
| 241 | `faq_8_text` | Adding a folder at the Included Folders doesn\'t automatically exclude anything. What you can do is go in Settings -&gt; Manage Excluded Folders, exclude the root folder \"/\", then add the desired folders at Settings -&gt; Manage Included Folders. That will make only the selected folders visible, as both excluding and including are recursive and if a folder is both excluded and included, it will show up. | Daxil edilmiş qovluqlara qovluq əlavə etmək avtomatik olaraq heç nəyi istisna etmir. Edə biləcəyiniz şey Parametrlər -&gt; İstisna edilmiş qovluqları idarə et bölməsinə keçib kök qovluğu \"/\" istisna etmək, sonra isə Parametrlər -&gt; Daxil edilmiş qovluqları idarə et bölməsindən istədiyiniz qovluqları əlavə etməkdir. Həm istisna etmə, həm də daxil etmə rekursiv işlədiyindən və bir qovluq həm istisna edilib, həm də daxil edildikdə göründüyündən, bu üsul yalnız seçilmiş qovluqları görünən edəcək. |
| 242 | `faq_10_title` | Can I crop images with this app? | Bu tətbiqlə şəkilləri kəsə bilərəmmi? |
| 243 | `faq_10_text` | Yes, you can crop images in the editor, by dragging the image corners. You can get to the editor either by long pressing an image thumbnail and selecting Edit, or selecting Edit from the fullscreen view. | Bəli, redaktorda şəkil künclərini sürüşdürərək şəkilləri kəsə bilərsiniz. Redaktora şəkil miniatürünə uzun basıb Redaktə seçməklə və ya tam ekran görünüşündən Redaktə seçməklə keçə bilərsiniz. |
| 244 | `faq_11_title` | Can I somehow group media file thumbnails? | Media fayllarının miniatürlərini necəsə qruplaşdıra bilərəmmi? |
| 245 | `faq_11_text` | Sure, just use the \"Group by\" menu item while at the thumbnails view. You can group files by multiple criteria, including Date Taken. If you use the \"Show all folders content\" function you can group them by folders too. | Əlbəttə, miniatürlər görünüşündə \"Qruplaşdırma\" menyu bəndindən istifadə edin. Faylları Çəkilmə tarixi daxil olmaqla bir neçə meyara görə qruplaşdıra bilərsiniz. \"Bütün qovluqların məzmununu göstər\" funksiyasından istifadə etsəniz, onları qovluqlara görə də qruplaşdıra bilərsiniz. |
| 246 | `faq_12_title` | Sorting by Date Taken doesn\'t seem to work properly, how can I fix it? | Çəkilmə tarixinə görə sıralama düzgün işləmir, necə düzəldə bilərəm? |
| 247 | `faq_12_text` | It is most likely caused by the files being copied from somewhere. You can fix it by selecting the file thumbnails and selecting \"Fix Date Taken value\". | Bu, çox güman ki, faylların haradansa köçürülməsindən qaynaqlanır. Fayl miniatürlərini seçib \"Çəkilmə tarixi dəyərini düzəlt\" seçimini etməklə bunu düzəldə bilərsiniz. |
| 248 | `faq_13_title` | I see some color banding on the images. How can I improve the quality? | Şəkillərdə rəng zolaqlanması görürəm. Keyfiyyəti necə artıra bilərəm? |
| 249 | `faq_13_text` | The current solution for displaying images works fine in the vast majority of cases, but if you want even better image quality, you can enable the \"Show images in the highest possible quality\" at the app settings, in the \"Deep zoomable images\" section. | Şəkillərin göstərilməsi üçün mövcud həll əksər hallarda yaxşı işləyir, lakin daha yüksək görüntü keyfiyyəti istəyirsinizsə, tətbiq parametrlərində \"Dərin yaxınlaşdırılan şəkillər\" bölməsində \"Şəkilləri mümkün olan ən yüksək keyfiyyətdə göstər\" seçimini aktivləşdirə bilərsiniz. |
| 250 | `faq_14_title` | I have hidden a file/folder. How can I unhide it? | Bir faylı/qovluğu gizlətmişəm. Onu necə yenidən görünən edə bilərəm? |
| 251 | `faq_14_text` | You can either press the \"Temporarily show hidden items\" menu item at the main screen, or toggle \"Show hidden items\" in the app settings to see the hidden item. If you want to unhide it, just long press it and select \"Unhide\". Folders are hidden by adding a hidden \".nomedia\" file into them, you can delete the file with any file manager too. Note that hiding works recursively though, so if you hide a folder, all subfolders will become hidden too. So for unhiding the subfolders you have to unhide the parent folder. | Gizli elementi görmək üçün əsas ekranda \"Gizli elementləri müvəqqəti göstər\" menyu bəndinə toxuna və ya tətbiq parametrlərində \"Gizli elementləri göstər\" seçimini aktivləşdirə bilərsiniz. Gizlətməni ləğv etmək üçün üzərinə uzun basıb \"Gizlətməni ləğv et\" seçin. Qovluqlar içinə gizli \".nomedia\" faylı əlavə edilərək gizlədilir, həmin faylı istənilən fayl meneceri ilə də silə bilərsiniz. Unutmayın ki, gizlətmə rekursiv işləyir; yəni bir qovluğu gizlətsəniz, bütün alt qovluqlar da gizlənir. Ona görə alt qovluqları görünən etmək üçün üst qovluğun gizlədilməsini ləğv etməlisiniz. |
| 252 | `faq_15_title` | Why does the app take up so much space? | Tətbiq niyə bu qədər çox yer tutur? |
| 253 | `faq_15_text` | App cache can take up to 250MB, it ensures quicker image loading. If the app is taking up even more space, it is most likely caused by you having items in the recycle bin. Those files count to the app size. You can clear the recycle bin by opening it and deleting all files, or from the app settings. Every file in the bin is deleted automatically after 30 days. | Tətbiq keşi 250 MB-a qədər yer tuta bilər, bu, şəkillərin daha sürətli yüklənməsini təmin edir. Tətbiq bundan daha çox yer tutursa, böyük ehtimalla zibil qutusunda elementləriniz var. Həmin fayllar tətbiqin ölçüsünə daxil edilir. Zibil qutusunu açıb bütün faylları silməklə və ya tətbiq parametrlərindən onu təmizləyə bilərsiniz. Zibil qutusundakı hər fayl 30 gündən sonra avtomatik silinir. |
| 254 | `faq_16_title` | What happened to file and folder hiding and why cannot I see hidden items anymore? | Fayl və qovluq gizlətməyə nə oldu və niyə artıq gizli elementləri görə bilmirəm? |
| 255 | `faq_16_text` | Starting with Android 11 you cannot hide or unhide files or folders anymore, you cannot see the hidden ones in gallery apps due to system restrictions either. You will have to use some file manager for that. | Android 11-dən başlayaraq sistem məhdudiyyətlərinə görə artıq fayl və ya qovluqları gizlədə, gizlətməsini ləğv edə və ya qalereya tətbiqlərində gizli olanları görə bilmirsiniz. Bunun üçün hansısa fayl menecerindən istifadə etməli olacaqsınız. |
| 256 | `faq_16_text_extra` | Or you can also grant this gallery access to All Files through your device settings, that will allow us showing hidden items and make file operations more reliable in general. | Və ya cihaz parametrləri vasitəsilə bu qalereyaya Bütün Fayllara giriş icazəsi verə bilərsiniz; bu, bizə gizli elementləri göstərməyə və ümumilikdə fayl əməliyyatlarını daha etibarlı etməyə imkan verəcək. |
| 257 | `faq_17_title` | Why cannot I include missing folders anymore? | Niyə artıq görünməyən qovluqları daxil edə bilmirəm? |
| 258 | `faq_17_text` | That stopped working due to the system changes that came with Android 11 too, the app cannot browse real folders anymore, it relies on the so called MediaStore at fetching data. | Bu da Android 11 ilə gələn sistem dəyişikliklərinə görə işləməyi dayandırdı; tətbiq artıq real qovluqlara baxa bilmir və məlumatları almaq üçün MediaStore adlanan mexanizmə əsaslanır. |
| 259 | `faq_18_title` | Why do I see ads during video playback? | Video oxudularkən niyə reklamlar görürəm? |
| 260 | `faq_18_text` | Our apps have no ads whatsoever. If you see them during video playback, you must be using some other apps video player. Try finding your default video player in the device settings, then do a \"Clear defaults\" on it. The next time you invoke some video intent you will see an app picker prompt, where you can select what app you want to use. | Tətbiqlərimizdə heç bir reklam yoxdur. Video oxudularkən reklam görürsünüzsə, başqa bir tətbiqin video pleyerindən istifadə edirsiniz. Cihaz parametrlərində susmaya görə video pleyerinizi tapıb \"Susmaya görə tənzimləmələri təmizlə\" əməliyyatını edin. Növbəti dəfə video açdıqda istifadə etmək istədiyiniz tətbiqi seçə biləcəyiniz bir tətbiq seçicisi görünəcək. |
| 261 | `create_virtual_album` | Create virtual album | Virtual albom yarat |
| 262 | `new_virtual_album` | New album… | Yeni albom… |
| 263 | `rename_virtual_album` | Rename album | Albomun adını dəyiş |
| 264 | `album_name` | Album name | Albom adı |
| 265 | `virtual_folder_name` | Virtual folder name | Virtual qovluq adı |
| 266 | `virtual_album_name_empty` | The album name cannot be empty | Albom adı boş ola bilməz |
| 267 | `virtual_album_exists` | An album with this name already exists | Bu adda albom artıq mövcuddur |
| 268 | `add_to_virtual_album` | Add to album | Alboma əlavə et |
| 269 | `remove_from_virtual_album` | Remove from album | Albomdan çıxar |
| 270 | `added_to_virtual_album` | Added to \"%s\" | \"%s\" albomuna əlavə edildi |
| 271 | `already_in_virtual_album` | Already in \"%s\" | Artıq \"%s\" albomundadır |
| 272 | `removed_from_virtual_album` | Removed from the album | Albomdan çıxarıldı |
| 273 | `delete_virtual_album_confirmation` | Delete the album %s? Only the album is removed, your photos and videos stay where they are. | %s albomu silinsin? Yalnız albom silinir, foto və videolarınız olduğu yerdə qalır. |
| 274 | `virtual_album_empty` | This album is empty. Select items in any folder and use \"Copy to\". | Bu albom boşdur. İstənilən qovluqda elementləri seçin və \"Köçür\" seçimindən istifadə edin. |
| 275 | `virtual_album_cannot_be_modified` | This action is not available for virtual albums | Bu əməliyyat virtual albomlar üçün əlçatan deyil |
| 276 | `alphanumeric_password` | Alphanumeric | Alfanumerik |
| 277 | `enter_password` | Enter password | Parolu daxil edin |
| 278 | `repeat_password` | Repeat password | Parolu təkrarlayın |
| 279 | `wrong_password` | Wrong password | Yanlış parol |
| 280 | `encrypting_files` | Encrypting files… | Fayllar şifrələnir… |
| 281 | `encrypting_folder` | Encrypting folder… | Qovluq şifrələnir… |
| 282 | `folder_encrypted_successfully` | Folder encrypted successfully | Qovluq uğurla şifrələndi |
| 283 | `file_encrypted_successfully` | File encrypted successfully | Fayl uğurla şifrələndi |
| 284 | `files_encrypted_successfully` | Files encrypted successfully | Fayllar uğurla şifrələndi |
| 285 | `decrypting_folder` | Decrypting folder… | Qovluğun şifrəsi açılır… |
| 286 | `folder_decrypted_successfully` | Folder decrypted successfully | Qovluğun şifrəsi uğurla açıldı |
| 287 | `file_decrypted_successfully` | File decrypted successfully | Fayl uğurla şifrəsi açıldı |
| 288 | `files_decrypted_successfully` | Files decrypted successfully | Fayllar uğurla şifrəsi açıldı |
| 289 | `decrypting_files` | Decrypting files… | Faylların şifrəsi açılır… |
| 290 | `place_finger_prompt` | Please place your finger on the fingerprint sensor | Zəhmət olmasa, barmağınızı barmaq izi sensoruna qoyun |
| 291 | `connect_at_least_4_dots_prompt` | Connect at least 4 dots | Ən azı 4 nöqtəni birləşdirin |
| 292 | `access_existing_secrets_prompt` | Do you want to access your existing Secrets folder? | Mövcud Secrets qovluğunuza daxil olmaq istəyirsiniz? |
| 293 | `delete_and_recreate_secrets_prompt` | Existing Secrets folder will be deleted and a new one will be created. Are you sure? | Mövcud Secrets qovluğu silinəcək və yenisi yaradılacaq. Əminsiniz? |
| 294 | `confirm_checkbox_label` | I confirm | Təsdiqləyirəm |
| 295 | `secret_vault` | Encrypted Vault | Şifrəli Seyf |
| 296 | `secret_vault_empty` | Encrypted Vault is empty | Şifrəli Seyf boşdur |
| - | `press_back_again_to_exit_vault` | Press back again to exit | Çıxmaq üçün geri düyməsini yenidən basın |
| - | `block_vault_screenshots` | Block screenshots in hidden vault | Gizli seyfdə ekran görüntüsü çəkməyi qadağan et |
| 297 | `confirm_decrypt_hidden_file` | Are you sure you want to decrypt the hidden file(s)? | Gizli faylın(ların) şifrəsini açmaq istədiyinizə əminsiniz? |
| 298 | `copy_to_clipboard_disabled_in_vault` | Copying to clipboard is disabled while vault is unlocked for security. | Təhlükəsizlik məqsədilə seyf açıq olarkən mübadilə buferinə kopyalama deaktiv edilib. |
| 299 | `stop_showing_hidden` | Stop showing encrypted media | Şifrələnmiş medianı göstərməyi dayandır |
| 300 | `system_default` | System default | Sistem (susmaya görə) |
| 301 | `language` | Language | Dil |
| 302 | `open_system_language_settings` | Open system language settings | Sistem dil parametrlərini aç |
| 303 | `access_hidden_items_with_biometrics` | Access hidden items with biometrics | Gizli elementlərə biometrika ilə daxil ol |
| 304 | `enable_hidden_protection_first_prompt` | Please enable hidden item password protection first | Zəhmət olmasa, əvvəlcə gizli elementlərin parolla qorunmasını aktivləşdirin |
| 305 | `biometric_unlock_vault` | Unlock encrypted vault | Şifrəli seyfin kilidini aç |
| 306 | `biometric_unlock_prompt` | Verify your biometric identity to access hidden items | Gizli elementlərə daxil olmaq üçün biometrik kimliyinizi təsdiqləyin |
| 307 | `biometric_setup_prompt` | Confirm your biometric identity to enable biometric vault access | Seyfə biometrik girişi aktivləşdirmək üçün biometrik kimliyinizi təsdiqləyin |
| 308 | `use_password_or_pin` | Use PIN / Password | PIN / Paroldan istifadə et |
| 309 | `biometric_key_invalidated` | Biometric credentials have changed. Please enter your PIN or password. | Biometrik məlumatlar dəyişdirilib. Zəhmət olmasa, PIN və ya parolunuzu daxil edin. |
| 310 | `no_biometric_hardware_or_enrolled` | No biometric hardware or enrolled credentials found on this device | Bu cihazda biometrik avadanlıq və ya qeydiyyatdan keçmiş məlumat tapılmadı |
| 311 | `default_hidden_album` | Default Secret Album | *(non-translatable in `values/strings.xml`)* |
| 312 | `select_hidden_album` | Select Encrypted Album | Şifrəli Albom Seçin |
| 313 | `create_new_hidden_album` | Create New Encrypted Album | Yeni Şifrəli Albom Yarat |
| 314 | `new_hidden_album` | New encrypted album | Yeni şifrəli albom |
| 315 | `hidden_album_name_empty` | Album name cannot be empty | Albom adı boş ola bilməz |
| 316 | `file_hidden_to_album` | File encrypted to \"%s\" | Fayl \"%s\" albomuna şifrələndi |
| 317 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d fayl \"%2$s\" albomuna şifrələndi |
| 318 | `restore_confirmation` (`one`) | Are you sure you want to restore %d file? | %d faylı bərpa etmək istədiyinizə əminsiniz? |
| 319 | `restore_confirmation` (`other`) | Are you sure you want to restore %d files? | %d faylı bərpa etmək istədiyinizə əminsiniz? |
| 320 | `failed_to_resize_images` (`one`) | Failed to resize %d image | %d şəklin ölçüsünü dəyişmək mümkün olmadı |
| 321 | `failed_to_resize_images` (`other`) | Failed to resize %d images | %d şəklin ölçüsünü dəyişmək mümkün olmadı |
| 322 | `search` *(fossify-commons)* | *(inherited from commons)* | Axtar |
| 323 | `change_date_and_time_format` *(fossify-commons)* | Change date and time format | Tarix və saat formatını dəyiş |
| 324 | `password_protect_file_deletion` *(fossify-commons)* | Password protect file deletion and moving | Faylların silinməsini və köçürülməsini parolla qoru |
| 325 | `keep_last_modified` *(fossify-commons)* | Keep old last-modified value at file operations | Fayl əməliyyatlarında köhnə son dəyişdirilmə dəyərini qoru |
| 326 | `clear_cache` *(fossify-commons)* | Clear cache | Keşi təmizlə |
| 327 | `export_settings` *(fossify-commons)* | Export settings | Parametrləri ixrac et |
| 328 | `import_settings` *(fossify-commons)* | Import settings | Parametrləri idxal et |
| 329 | `temporarily_show_excluded` *(fossify-commons)* | Temporarily show excluded | İstisna edilmişləri müvəqqəti göstər |
| 330 | `change_view_type` *(fossify-commons)* | Change view type | Görünüş növünü dəyiş |
| 331 | `contributors` *(fossify-commons)* | Contributors | Töhfə verənlər |
| 332 | `known_issues` *(fossify-commons)* | Known issues | Məlum problemlər |
| 333 | `donate_to_fossify` *(fossify-commons)* | Donate to Fossify | Fossify-a ianə et |
| 334 | `privacy_policy` *(fossify-commons)* | Privacy policy | Məxfilik siyasəti |
| 335 | `create_shortcut` *(fossify-commons)* | Create shortcut | Qısayol yarat |
| 336 | `print` *(fossify-commons)* | Print | Çap et |
| 337 | `search_files` *(fossify-commons)* | Search files | Faylları axtar |
| 338 | `search_folders` *(fossify-commons)* | Search folders | Qovluqları axtar |
| 339 | `skip_the_recycle_bin` *(fossify-commons)* | Skip the recycle bin, delete files directly | Zibil qutusunu keçin, faylları birbaşa silin |
| 340 | `pull_down_to_open_vault` | Keep pulling down for Encrypted Vault | Şifrəli seyf üçün aşağı çəkməyə davam edin |
| 341 | `keep_holding_to_open_vault` | Keep holding to open Encrypted Vault | Şifrəli seyfi açmaq üçün basılı saxlayın |
| 342 | `release_to_open_vault` | Release to open Encrypted Vault | Şifrəli seyfi açmaq üçün buraxın |
| 343 | `manage_excluded_folders` *(fossify-commons)* | Manage excluded folders | İstisna edilmiş qovluqları idarə et |
| 344 | `exclude` *(fossify-commons)* | Exclude | İstisna et |
| 345 | `exclude_folder` *(fossify-commons)* | Exclude folder | Qovluğu istisna et |
| 346 | `lock_folder` *(fossify-commons)* | Lock folder | Qovluğu kilidlə |
| 347 | `change_cover_image` *(fossify-commons)* | Change cover image | Üz qabığı şəklini dəyiş |
| 348 | `select_photo` *(fossify-commons)* | Select photo | Foto seç |
| 349 | `use_default` *(fossify-commons)* | Use default | Susmaya görə istifadə et |
| 350 | `column_count` *(fossify-commons)* | Column count | Sütun sayı |
| 351 | `open_the_recycle_bin` *(fossify-commons)* | Open the recycle bin | Zibil qutusunu aç |
| 352 | `grid` *(fossify-commons)* | Grid | Tor |
| 353 | `list` *(fossify-commons)* | List | Siyahı |
| 354 | `resize` *(fossify-commons)* | Resize | Ölçüsünü dəyiş |
| 355 | `version_placeholder` *(fossify-commons)* | Version %s | Versiya %s |
| 356 | `number_of_items` *(fossify-commons)* | Number of items | Element sayı |
| 357 | `random` *(fossify-commons)* | Random | Təsadüfi |
| 358 | `shuffle` | Shuffle | Qarışdır |
| 359 | `stop_showing_excluded` *(fossify-commons)* | Stop showing excluded | İstisna edilmiş elementləri göstərməyi dayandır |
| 360 | `copy_to_clipboard` *(fossify-commons)* | Copy to clipboard | Buferə kopyala |
| 361 | `encrypt` | Encrypt | Şifrələ |
| 362 | `decrypt` | Decrypt | Şifrəni aç |
| 363 | `encrypt_album` | Encrypt album | Albomu şifrələ |
| 364 | `decrypt_album` | Decrypt album | Albomun şifrəsini aç |
| 365 | `create_new_encrypted_virtual_album` | Create new encrypted virtual album | Yeni şifrəli virtual albom yarat |
| 366 | `new_album` | New album | Yeni albom |
| 367 | `filename_without_zip` | Filename (without .zip) | Fayl adı (.zip olmadan) |
| 368 | `favorites_already_exist` | You already have a favorites list | Artıq sevimlilər siyahınız var |
| 369 | `delete_existing_list` | Delete existing list | Mövcud siyahını sil |
| 370 | `merge_lists` | Merge lists | Siyahıları birləşdir |
| 371 | `confirm` | Confirm | Təsdiq et |
| 372 | `confirm_encrypt_album` | Are you sure you want to encrypt this album? All of its media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | Bu albomu şifrələmək istədiyinizə əminsiniz? Daxilindəki bütün media faylları şifrələnəcək və şifrəli seyfə köçürüləcək. Orijinal fayllar silinəcək. |
| 373 | `confirm_encrypt_albums` | Are you sure you want to encrypt the selected albums? All of their media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | Seçilmiş albomları şifrələmək istədiyinizə əminsiniz? Daxilindəki bütün media faylları şifrələnəcək və şifrəli seyfə köçürüləcək. Orijinal fayllar silinəcək. |
| 374 | `confirm_encrypt_file` | Are you sure you want to encrypt this file? It will be encrypted and moved to the encrypted vault. The original file will be deleted. | Bu faylı şifrələmək istədiyinizə əminsiniz? Fayl şifrələnəcək və şifrəli seyfə köçürüləcək. Orijinal fayl silinəcək. |
| 375 | `confirm_encrypt_files` | Are you sure you want to encrypt the selected files? They will be encrypted and moved to the encrypted vault. The original files will be deleted. | Seçilmiş faylları şifrələmək istədiyinizə əminsiniz? Fayllar şifrələnəcək və şifrəli seyfə köçürüləcək. Orijinal fayllar silinəcək. |
| 376 | `confirm_encrypt_files_to_vault` | The selected files will be encrypted when they are copied into the encrypted vault. Do you want to continue? | Seçilmiş fayllar şifrəli seyfə kopyalanarkən şifrələnəcək. Davam etmək istəyirsiniz? |
| 377 | `encrypt_decrypt_notifications` | Encrypt / Decrypt | Şifrələmə / Şifrəni açma |
| 378 | `allow_batch_renaming` | Allow batch renaming when multiple files are selected | Birdən çox fayl seçildikdə toplu ad dəyişməyə icazə ver |
| 379 | `keep_original_remove_from_virtual_album` | Keep the original file, remove it only from this virtual album | Orijinal faylı saxla, yalnız bu virtual albomdan çıxar |
| 380 | `keep_original_remove_from_favorites` | Keep the original file, remove it only from favorites | Orijinal faylı saxla, yalnız sevimlilərdən çıxar |
| 381 | `delete_original_from_favorites_and_virtual_albums` | Delete the original version of a file when it is deleted from favorites and virtual albums | Fayl sevimlilərdən və virtual albomlardan silindikdə onun orijinal versiyasını da sil |
| 382 | `cannot_target_encrypted_album` | Encrypted albums cannot be targeted for this operation | Bu əməliyyat üçün şifrəli albomlar hədəf seçilə bilməz |
| 383 | `cannot_mix_encrypted_and_unencrypted` | Cannot combine encrypted and unencrypted items in this operation | Bu əməliyyatda şifrəli və şifrəsiz elementləri birləşdirmək olmaz |
| 384 | `lock_album` | Lock album | Albomu kilidlə |
| 385 | `unlock_album` | Unlock album | Albomun kilidini aç |
| 386 | `auto_backup_prompt_message` | Do you want your favorites and virtual albums to be regularly written to system storage outside of app memory? This will ensure that you do not lose your favorites list and virtual albums if you uninstall the app. You can manually create a backup from app settings at any time. | Sevimlilərinizin və virtual albomlarınızın tətbiq yaddaşından kənarda sistem yaddaşına müntəzəm olaraq yazılmasını istəyirsiniz? Bu, tətbiqi sildiyiniz halda sevimlilər siyahınızı və virtual albomlarınızı itirməməyinizi təmin edəcək. İstənilən vaxt tətbiq parametrlərindən əl ilə ehtiyat nüsxə yarada bilərsiniz. |
| 387 | `auto_backup_favorites_and_virtual_albums` | Back up favorites and virtual albums to device storage | Sevimlilərin və virtual albomların cihaz yaddaşında ehtiyat nüsxəsini yarat |
| 388 | `access_storage_prompt` *(fossify-commons)* | Please grant our app access to all your files, it might not work well without it. | Zəhmət olmasa, tətbiqimizə bütün fayllarınıza giriş icazəsi verin, bu olmadan düzgün işləməyə bilər. |
| 389 | `encrypt_info_title` | How Encryption Works | Şifrləmə necə işləyir |
| 390 | `encrypt_info_message` | Folder & File Structure:\n• When an unencrypted album is encrypted, it is converted into an encrypted subfolder inside the vault (Secrets).\n• Both the folder name and all file names/contents inside it are encrypted, so nothing is readable outside the app.\n\nIf an Album with the Same Name Already Exists:\n• By default, the app asks whether to create a new album such as Album(1) or merge into the existing encrypted album (configurable in Settings).\n• When merging, you can choose whether source files verified 100% bit-identical to the destination are deleted or kept in the source folder.\n• Different files with the same name (e.g. download.jpg) are kept as download(1).jpg or handled according to your conflict resolution setting. | Qovluq və Fayl Strukturu:\n• Şifrələnməmiş albom şifrələndikdə, seyf daxilində (Secrets) şifrələnmiş alt qovluğa çevrilir.\n• Həm qovluq adı, həm də içindəki bütün fayl adları/məzmunu şifrələnir, buna görə tətbiqdən kənarda heç nə oxuna bilməz.\n\nEyni adda albom artıq mövcuddursa:\n• Susmaya görə, tətbiq Albom(1) kimi yeni albom yaratmağı və ya mövcud şifrələnmiş alboma birləşdirməyi soruşur (Parametrlərdə tənzimlənə bilər).\n• Birləşdirərkən təyinat yerindəki faylla 100% bit-bə-bit eyni olduğu təsdiqlənən mənbə faylların silinməsini və ya mənbə qovluğunda saxlanmasını seçə bilərsiniz.\n• Eyni adda fərqli fayllar (məs., download.jpg) download(1).jpg kimi saxlanır və ya ziddiyyət həlli parametrinizə uyğun idarə olunur. |
| 391 | `decrypt_info_title` | How Decryption Works | Şifrə açma necə işləyir |
| 392 | `decrypt_info_message` | Folder & File Structure:\n• When an encrypted album or file is decrypted, it is restored with its original name and format to the selected destination folder and removed from the encrypted vault.\n\nIf an Album with the Same Name Already Exists:\n• By default, the app asks whether to create a new folder such as Album(1) or merge into the existing destination folder (configurable in Settings).\n• When merging, you can choose whether encrypted copies verified 100% bit-identical to the destination are removed from the vault or kept.\n• Different files with the same name (e.g. download.jpg) are kept as download(1).jpg or handled according to your conflict resolution setting. | Qovluq və Fayl Strukturu:\n• Şifrələnmiş albom və ya faylın şifrəsi açıldıqda, seçilmiş təyinat qovluğuna orijinal adı və formatı ilə bərpa olunur və şifrəli seyfdən silinir.\n\nEyni adda albom artıq mövcuddursa:\n• Susmaya görə, tətbiq Albom(1) kimi yeni qovluq yaratmağı və ya mövcud təyinat qovluğuna birləşdirməyi soruşur (Parametrlərdə tənzimlənə bilər).\n• Birləşdirərkən təyinat yerindəki faylla 100% bit-bə-bit eyni olduğu təsdiqlənən şifrələnmiş nüsxələrin seyfdən silinməsini və ya saxlanmasını seçə bilərsiniz.\n• Eyni adda fərqli fayllar (məs., download.jpg) download(1).jpg kimi saxlanır və ya ziddiyyət həlli parametrinizə uyğun idarə olunur. |
| 393 | `vault_location` | Vault location | Seyf yeri |
| 394 | `vault_location_standard_summary` | Standard (Internal Storage) | Standart (Daxili yaddaş) |
| 395 | `vault_location_unavailable_summary` | External drive disconnected (%s) | Xarici disk qoşulmayıb (%s) |
| 396 | `select_vault_location` | Select vault location | Seyf yerini seçin |
| 397 | `vault_setup_action_title` | Encrypted vault | Şifrəli seyf |
| 398 | `create_new_vault_option` | Create new vault | Yeni seyf yarat |
| 399 | `use_existing_vault_option` | Use existing vault | Mövcud seyfdən istifadə et |
| 400 | `where_to_create_vault_title` | Where should the vault be created? | Seyf harada yaradılsın? |
| 401 | `vault_location_default_short` | Default location | Susmaya görə yer |
| 402 | `vault_location_custom_short` | Custom Location | Fərdi yer |
| 403 | `no_existing_vault_at_location` | No existing encrypted vault (.vault) was found in the selected folder. | Seçilmiş qovluqda mövcud şifrəli seyf (.vault) tapılmadı. |
| 404 | `unlock_existing_vault_at_location_prompt` | An existing encrypted vault was found at %s. Do you want to unlock and use it? Tap No to pick a different folder. | %s ünvanında mövcud şifrəli seyf tapıldı. Kilidini açıb ondan istifadə etmək istəyirsiniz? Başqa qovluq seçmək üçün Xeyr üzərinə toxunun. |
| 405 | `vault_location_standard_option` | Standard location (Internal Storage > Calypso Gallery > Secrets) | Standart yer (Daxili yaddaş > Calypso Gallery > Secrets) |
| 406 | `vault_location_custom_option` | Custom / External location (USB drive, SD card, or custom folder) | Fərdi / Xarici yer (USB sürücü, SD kart və ya fərdi qovluq) |
| 407 | `external_vault_unavailable_title` | External vault unavailable | Xarici seyf əlçatan deyil |
| 408 | `external_vault_unavailable_prompt` | Your configured vault location (%s) is currently disconnected or unavailable. Plug in the drive and tap Retry, or choose another vault location. | Tənzimlənmiş seyf yeriniz (%s) hazırda ayrılıb və ya əlçatan deyil. Sürücünü qoşub Yenidən cəhd et üzərinə toxunun və ya başqa seyf yeri seçin. |
| 409 | `retry` | Retry | Yenidən cəhd et |
| 410 | `change_vault_location` | Change location | Yeri dəyiş |
| 411 | `existing_vault_detected_at_location` | Existing vault found at %s. Enter its password or PIN to unlock it. | %s ünvanında mövcud seyf tapıldı. Kilidini açmaq üçün parol və ya PIN daxil edin. |
| 412 | `create_new_vault_at_location_prompt` | No vault exists at %s. Do you want to create a new encrypted vault at this location? | %s ünvanında seyf mövcud deyil. Bu yerdə yeni şifrəli seyf yaratmaq istəyirsiniz? |
| 413 | `usb_direct_filesystem_required` | This USB path is only exposed through Android\'s virtual OTG provider and cannot be accessed directly as a filesystem path. Please choose a mounted storage directory or a folder on internal storage / SD card. | Bu USB yolu yalnız Android-in virtual OTG provayderi vasitəsilə təqdim olunur və birbaşa fayl sistemi yolu kimi əlçatan deyil. Zəhmət olmasa, qoşulmuş yaddaş kataloqu və ya daxili yaddaş / SD kart üzərində bir qovluq seçin. |
| 414 | `vault_location_not_writable` | The selected folder does not exist or is not writable on this device. | Seçilmiş qovluq mövcud deyil və ya bu cihazda yazıla bilən deyil. |
| 415 | `vault_location_not_readable` | The selected folder does not exist or is not readable on this device. | Seçilmiş qovluq mövcud deyil və ya bu cihazda oxuna bilən deyil. |
| 416 | `sync_vault_from_external` | Import encrypted media files to encrypted section | Şifrələnmiş media fayllarını şifrələnmiş bölməyə idxal et |
| 417 | `sync_vault_select_source_prompt` | How Encrypted Media Import Works:\n\n1. Unified Secrets Folder:\nKeep a folder named Secrets on your external drive (USB / SD). You can either keep a full backup copy of your vault there, or place only your vault\'s .vault header file inside it.\n\n2. Encrypting New Media (PC / Tails CLI):\nThe offline CLI tool automatically saves new encrypted files directly into Secrets. Files placed at the root go to Default Secret Album; files in subfolders go to matching encrypted albums (or create new ones).\n\n3. Bit-by-Bit Smart Sync:\nSelect your external drive or Secrets folder next. The app compares the external drive with your phone vault in RAM:\n• Only new files and albums are imported.\n• Bit-identical files already in your vault are skipped.\n• Different files with the same name (e.g. download.jpg) are safely imported as download(1).jpg. | Şifrələnmiş Medianın İdxalı Necə İşləyir:\n\n1. Vahid Secrets Qovluğu:\nXarici sürücünüzdə (USB / SD) Secrets adlı qovluq saxlayın. Orada ya seyfinizin tam ehtiyat nüsxəsini saxlaya, ya da yalnız seyfinizin .vault başlıq faylını yerləşdirə bilərsiniz.\n\n2. Yeni Medianın Şifrələnməsi (PC / Tails CLI):\nOflayn CLI aləti yeni şifrələnmiş faylları avtomatik olaraq birbaşa Secrets qovluğuna saxlayır. Kök qovluğa yerləşdirilən fayllar Default Secret Album-a gedir; alt qovluqlardakı fayllar uyğun şifrələnmiş albomlara gedir (və ya yenilərini yaradır).\n\n3. Bit-bə-Bit Ağıllı Sinxronizasiya:\nNövbəti addımda xarici sürücünüzü və ya Secrets qovluğunu seçin. Tətbiq xarici sürücünü telefonunuzdakı seyflə RAM-da müqayisə edir:\n• Yalnız yeni fayllar və albomlar idxal olunur.\n• Artıq seyfinizdə olan bit-bə-bit eyni fayllar ötürülür.\n• Eyni adda fərqli fayllar (məs., download.jpg) təhlükəsiz şəkildə download(1).jpg kimi idxal olunur. |
| 418 | `conflict_handling_mode_label` | File conflict & duplicate resolution | Fayl ziddiyyəti və dublikat həlli |
| 419 | `conflict_handling_auto_short` | Full automatic (Default — Skip identical, rename different as (1)) | Tam avtomatik (Susmaya görə — Eyniləri ötür, fərqliləri (1) kimi adlandır) |
| 420 | `conflict_handling_smart_dialog_short` | Smart dialog (Auto-skip identical, ask when content differs) | Ağıllı dialoq (Eyniləri avtomatik ötür, məzmun fərqli olduqda soruş) |
| 421 | `album_crypto_conflict_mode_label` | Album conflict (Encrypt / Decrypt) | Albom ziddiyyəti (Şifrələ / Şifrəsini aç) |
| 422 | `album_crypto_conflict_ask` | Always ask (Default) | Həmişə soruş (Susmaya görə) |
| 423 | `album_crypto_conflict_merge` | Merge into existing album | Mövcud alboma birləşdir |
| 424 | `album_crypto_conflict_merge_delete_identical` | Merge into existing album and automatically delete identical sources | Mövcud alboma birləşdir və eyni mənbələri avtomatik sil |
| 425 | `album_crypto_conflict_rename` | Create new album as (1) | Yeni albomu (1) kimi yarat |
| 426 | `album_crypto_conflict_title` | Album Already Exists | Albom artıq mövcuddur |
| 427 | `album_crypto_conflict_message` | An album named \"%s\" already exists at the destination. How would you like to proceed? | Təyinat yerində \"%s\" adlı albom artıq mövcuddur. Necə davam etmək istəyirsiniz? |
| 428 | `album_crypto_conflict_message_multiple` | %1$d selected albums already exist at the destination. How would you like to proceed?\n\nYour choice will apply to all conflicting albums. To choose separately per album, select and process them one by one. | Seçilmiş %1$d albom təyinat yerində artıq mövcuddur. Necə davam etmək istəyirsiniz?\n\nSeçiminiz bütün ziddiyyətli albomlara tətbiq olunacaq. Hər albom üçün ayrıca seçim etmək üçün onları bir-bir seçib icra edin. |
| 429 | `album_crypto_conflict_rename_option` | Create new album as \"%s\" | Yeni albomu \"%s\" kimi yarat |
| 430 | `album_crypto_conflict_rename_option_multiple` | Create new albums as (1), (2), … | Yeni albomları (1), (2), … kimi yarat |
| 431 | `album_crypto_conflict_merge_option` | Merge into existing \"%s\" | Mövcud \"%s\" albomuna birləşdir |
| 432 | `album_crypto_conflict_merge_option_multiple` | Merge into existing albums | Mövcud albomlara birləşdir |
| 433 | `album_crypto_delete_identical_source` | Delete identical source files (100% bit-by-bit verified) | Eyni mənbə fayllarını sil (100% bit-bə-bit təsdiqlənmiş) |
| 434 | `album_crypto_dont_ask_again` | Remember album conflict choice (merge or rename) | Albom ziddiyyəti seçimini yadda saxla (birləşdir və ya yenidən adlandır) |
| 435 | `album_crypto_merge_identical_title` | Identical Files During Merge | Birləşdirmə Zamanı Eyni Fayllar |
| 436 | `album_crypto_merge_identical_message` | When merging into \"%1$s\", if any files are already 100%% bit-identical at the destination, should their source copies be deleted or kept? | \"%1$s\" albomuna birləşdirərkən, təyinat yerində artıq 100%% bit-bə-bit eyni olan fayllar varsa, onların mənbə nüsxələri silinsin, yoxsa saxlanılsın? |
| 437 | `album_crypto_merge_identical_message_multiple` | When merging the %1$d conflicting albums, if any files are already 100%% bit-identical at the destination, should their source copies be deleted or kept? | %1$d ziddiyyətli albomu birləşdirərkən, təyinat yerində artıq 100%% bit-bə-bit eyni olan fayllar varsa, onların mənbə nüsxələri silinsin, yoxsa saxlanılsın? |
| 438 | `delete_identical_sources` | Delete identical | Eyniləri sil |
| 439 | `keep_identical_sources` | Keep in source | Mənbədə saxla |
| 440 | `fast_large_duplicate_check_label` | Fast duplicate check for large files (>8 MB) | Böyük fayllar üçün sürətli dublikat yoxlaması (>8 MB) |
| 441 | `fast_large_duplicate_check_hint` | Crucial for transfers containing large files: checks 5 blocks (1.25 MB total) per file instead of reading whole files, speeding up duplicate checks dozens of times. | Böyük fayllar olan köçürmələr üçün vacibdir: bütün faylı oxumaq əvəzinə hər fayl üçün 5 bloku (cəmi 1.25 MB) yoxlayır və dublikat yoxlamasını onlarla dəfə sürətləndirir. |
| 442 | `fast_large_duplicate_check_info_title` | Fast Duplicate Check (>8 MB) | Sürətli Dublikat Yoxlaması (>8 MB) |
| 443 | `conflict_handling_explanation_title` | File Conflict & Duplicate Resolution | Fayl Ziddiyyəti və Dublikat Həlli |
| 444 | `conflict_handling_explanation_message` | When copying, moving, encrypting, or decrypting files and albums, if a file with the same name already exists at the destination:\n\n1. Bit-by-Bit Duplicate Check (RAM):\nThe app checks file sizes first, and if sizes match, compares the file contents bit-by-bit in RAM (encrypted files are checked in memory without writing plaintext to disk). During copy/move, identical files are skipped and the original remains untouched in the source folder. When merging an album during encrypt/decrypt, you can choose whether 100% bit-identical source files are deleted or kept.\n\n2. Same Name, Different Content (e.g. two different download.jpg files):\n• Full automatic (Default): Keeps both files without interrupting you by naming the new file download(1).jpg, download(2).jpg.\n• Smart dialog: Still skips bit-identical files silently, and only asks (Skip / Overwrite / Keep both) when two files have the same name but different content. | Faylları və albomları kopyaladıqda, köçürdükdə, şifrələdikdə və ya şifrəsini açdıqda, təyinat yerində eyni adda fayl artıq mövcuddursa:\n\n1. Bit-bə-Bit Dublikat Yoxlaması (RAM):\nTətbiq əvvəlcə fayl ölçülərini yoxlayır və ölçülər uyğun gələrsə, fayl məzmununu RAM-da bit-bə-bit müqayisə edir (şifrələnmiş fayllar diskə açıq mətn yazılmadan yaddaşda yoxlanılır). Kopyalama/köçürmə zamanı eyni fayllar ötürülür və orijinal mənbə qovluğunda toxunulmaz qalır. Şifrələmə/şifrə açma zamanı albomu birləşdirərkən 100% bit-bə-bit eyni mənbə fayllarının silinməsini və ya saxlanmasını seçə bilərsiniz.\n\n2. Eyni Ad, Fərqli Məzmun (məs., iki fərqli download.jpg faylı):\n• Tam avtomatik (Susmaya görə): Yeni faylı download(1).jpg, download(2).jpg kimi adlandıraraq sizi bölmədən hər iki faylı saxlayır.\n• Ağıllı dialoq: Yenə də bit-bə-bit eyni faylları səssizcə ötürür və yalnız iki faylın adı eyni, lakin məzmunu fərqli olduqda soruşur (Ötür / Üzərinə yaz / Hər ikisini saxla). |
| 445 | `syncing_vault_progress` | Syncing vault from external drive… | Xarici sürücüdən seyf sinxronlaşdırılır… |
| 446 | `vault_sync_summary_message` | Vault sync complete:\n• Copied: %1$d\n• Renamed (name conflict): %2$d\n• Skipped (identical): %3$d\n• Failed: %4$d | Seyf sinxronizasiyası tamamlandı:\n• Kopyalandı: %1$d\n• Yenidən adlandırıldı (ad ziddiyyəti): %2$d\n• Ötürüldü (eyni): %3$d\n• Uğursuz: %4$d |
| 447 | `vault_sync_nothing_to_sync` | No pending CLI-encrypted media (.pending_sync) was found in the selected folder. | Seçilmiş qovluqda gözləyən CLI ilə şifrələnmiş media (.pending_sync) tapılmadı. |
| 448 | `vault_sync_same_folder_error` | The selected source folder is the same as your active vault. | Seçilmiş mənbə qovluğu aktiv seyfinizlə eynidir. |
| 449 | `use_for_this_album` | Use for this album only | Yalnız bu albom üçün işlət |
| 450 | `crypto_duplicate_source_mode_label` | When a file already exists in destination during encrypt/decrypt | Şifrələmə və şifrə açma zamanı fayl hədəf qovluqda mövcud olduqda |
| 451 | `crypto_duplicate_source_ask` | Always ask (Default) | Həmişə soruş (Standart) |
| 452 | `crypto_duplicate_source_keep` | Keep file in source folder | Mənbə qovluqdakı faylı saxla |
| 453 | `crypto_duplicate_source_delete` | Delete file from source folder | Mənbə qovluqdakı faylı sil |
| 454 | `crypto_duplicate_source_title` | File Already Exists in Destination | Fayl hədəfdə artıq mövcuddur |
| 455 | `crypto_duplicate_source_encrypt_message` | Some of the files you are encrypting already exist in the destination folder. What should happen to the files in the source folder? | Şifrələdiyiniz faylların bəziləri hədəf qovluqda artıq mövcuddur. Mənbə qovluqdakı fayllarla nə edilsin? |
| 456 | `crypto_duplicate_source_decrypt_message` | Some of the files you are decrypting already exist in the destination folder. What should happen to the files in the source folder? | Şifrəsini açdığınız faylların bəziləri hədəf qovluqda artıq mövcuddur. Mənbə qovluqdakı fayllarla nə edilsin? |
| 457 | `crypto_duplicate_source_encrypt_keep_warning` | Warning: Choosing \"Keep\" for unencrypted files will leave the source file unencrypted on disk. | Xəbərdarlıq: Şifrələnməmiş fayllar üçün \"Saxla\" seçilərsə, mənbə fayl şifrələnmədən diskdə qalacaq. |
| 458 | `crypto_duplicate_source_always_ask` | Always ask | Həmişə soruş |
| 459 | `export_encrypted_vault` | Export encrypted vault | Şifrələnmiş seyfi ixrac et |
| 460 | `export_vault_only_header` | Export folder containing only .vault file | Yalnız .vault faylını ehtiva edən qovluğu ixrac et |
| 461 | `export_vault_entire_folder` | Export entire folder | Bütün qovluğu ixrac et |
| 462 | `export_vault_only_header_info` | To add media items encrypted on other devices using the supported CLI tool to the encrypted vault on your phone, you only need to use a folder named Secrets containing the .vault file. | Dəstəklənən CLI alətindən istifadə edərək digər cihazlarda şifrələdiyiniz media elementlərini telefonunuzdakı şifrələnmiş seyfə əlavə etmək üçün tərkibində .vault faylı olan Secrets adlı qovluqdan istifadə etməyiniz kifayətdir. |
| 463 | `exporting_vault_progress` | Exporting encrypted vault… | Şifrələnmiş seyf ixrac olunur… |
| 464 | `vault_export_same_folder_error` | The export destination cannot be the active vault folder or inside it. | İxrac hədəfi aktiv seyf qovluğu və ya onun alt qovluğu ola bilməz. |
| 465 | `conflict_handling_standard_dialog_summary` | Standard dialog | Standart dialoq |
| 466 | `conflict_handling_smart_dialog_summary` | Smart dialog | Ağıllı dialoq |
| 467 | `conflict_handling_auto_summary` | Automatic (Experimental) | Avtomatik (Eksperimental) |
| 468 | `conflict_handling_standard_dialog_short` | Standard dialog (Default — Checks filename only, like original Fossify Gallery) | Standart dialoq (Susmaya görə — yalnız fayl adını yoxlayır, orijinal Fossify Gallery kimi) |
| 469 | `skipped_encrypt_files_mode_label` | Manage skipped files during encryption | Şifrələmə zamanı atlanan faylları idarə et |
| 470 | `skipped_encrypt_files_info_message` | Choose whether files skipped in the source folder during encryption should be kept or deleted for security. This choice also applies to album conflicts during album encryption (see setting below) when the source album is merged into an existing album at the destination. | Şifrələmə zamanı mənbə qovluqda atlanan faylların təhlükəsizlik məqsədilə saxlanılacağını və ya silinəcəyini seçin. Bu seçim, həmçinin mənbə albomu təyinat yerində mövcud albomla birləşdirildikdə, albom şifrələməsi zamanı yaranan albom ziddiyyətlərinə də tətbiq olunur (aşağıdakı parametra baxın). |
| 471 | `skipped_crypto_files_ask` | Always ask (Default) | Həmişə soruş (Susmaya görə) |
| 472 | `skipped_crypto_files_keep` | Keep | Saxla |
| 473 | `skipped_crypto_files_delete` | Delete | Sil |
| 474 | `skipped_crypto_files_encrypt_message` | The files you skipped were not transferred. For your privacy, you can delete these files. | Atladığınız fayllar köçürülmədi. Məxfilikiniz üçün bu faylları silə bilərsiniz. |
| 475 | `skipped_crypto_files_decrypt_message` | The files you skipped were not transferred. You can delete these files. | Atladığınız fayllar köçürülmədi. Bu faylları silə bilərsiniz. |
| 476 | `skipped_crypto_files_identical_encrypt_message` | Files that already exist in the destination folder were not transferred. For your privacy, you can delete these files. | Təyinat qovluğunda artıq mövcud olan fayllar köçürülmədi. Məxfilikiniz üçün bu faylları silə bilərsiniz. |
| 477 | `skipped_crypto_files_identical_decrypt_message` | Files that already exist in the destination folder were not transferred. You can delete these files. | Təyinat qovluğunda artıq mövcud olan fayllar köçürülmədi. Bu faylları silə bilərsiniz. |
| 478 | `skipped_crypto_files_both_encrypt_message` | The files you skipped and the files that already exist in the destination folder were not transferred. For your privacy, you can delete these files. | Atladığınız fayllar və təyinat qovluğunda artıq mövcud olan fayllar köçürülmədi. Məxfilikiniz üçün bu faylları silə bilərsiniz. |
| 479 | `skipped_crypto_files_both_decrypt_message` | The files you skipped and the files that already exist in the destination folder were not transferred. You can delete these files. | Atladığınız fayllar və təyinat qovluğunda artıq mövcud olan fayllar köçürülmədi. Bu faylları silə bilərsiniz. |
| 480 | `skipped_crypto_files_always_ask` | Always ask | Həmişə soruş |
| 481 | `delete_skipped_files_confirm_title` | Are you sure? | Əminsiniz? |
| 482 | `delete_skipped_files_confirm_message` | Your files will be deleted! | Fayllarınız silinəcək! |
| 483 | `date_tie_break_label` | When content is identical but dates differ | Məzmun eyni, lakin tarixlər fərqli olduqda |
| 484 | `date_tie_break_keep_target` | Keep the existing file\'s date (default) | Mövcud faylın tarixini saxla (susmaya görə) |
| 485 | `date_tie_break_use_source` | Use the source file\'s date | Mənbə faylın tarixindən istifadə et |
| 486 | `date_tie_break_ask` | Always ask | Həmişə soruş |
| 487 | `date_tie_break_hint` | Applies only to Smart dialog and Automatic mode. The Standard dialog is not affected. | Yalnız Ağıllı dialoq və Avtomatik rejimə tətbiq olunur. Standart dialoq təsirlənmir. |
| 488 | `date_tie_break_ask_info_title` | Why "Always ask" disables Smart dialog and Automatic mode | Niyə "Həmişə soruş" Ağıllı dialoq və Avtomatik rejimi deaktiv edir |
| 489 | `date_tie_break_ask_info_message` | Smart dialog and Automatic mode exist for one reason: to skip files whose content is already present, so you only see the files that really need your attention — same name, different content. That is exactly the case where comparing thumbnails can reveal bit-rot.\n\nIf you choose "Always ask" here, every identical file with a different date comes back to you as a conflict. In Smart dialog you will see two thumbnails of the very same picture, with nothing to verify, and you can decide there. Automatic (Experimental) mode never shows a dialog, so "Always ask" is not available there — selecting Automatic falls back to "Keep the existing file's date".\n\nUse this option only if you deliberately want to review every date difference, for example to fix a wrong date by hand.\n\nNote: when content is bit-identical, the EXIF capture date is identical too. A date difference means one side lost its EXIF data and fell back to the file modification date (the copy date). | Ağıllı dialoq və Avtomatik rejim yalnız bir səbəbdən mövcuddur: məzmunu artıq mövcud olan faylları ötürmək, beləliklə yalnız həqiqətən diqqətinizi tələb edən faylları görəsiniz — eyni adda, lakin fərqli məzmunlu. Miniatürlərin müqayisəsi məhz belə hallarda bit çürüməsini (bit-rot) aşkar edə bilər.\n\nBurada "Həmişə soruş" seçsəniz, fərqli tarixli hər eyni fayl sizə ziddiyyət kimi qayıdacaq. Ağıllı dialoqda tam eyni şəklin iki miniatürünü görəcək və orada qərar verəcəksiniz. Otomatik (Eksperimental) rejim heç vaxt dialoq göstərmədiyi üçün "Həmişə soruş" bu rejimdə mövcud deyil; Otomatik rejim seçildikdə "Mövcud faylın tarixini saxla" seçənəyinə qayıdılır.\n\nBu seçimdən yalnız hər bir tarix fərqini qəsdən nəzərdən keçirmək istədiyinizdə, məsələn yanlış tarixi əl ilə düzəltmək üçün istifadə edin.\n\nQeyd: məzmun bit-bə-bit eyni olduqda, EXIF çəkilmə tarixi də eyni olur. Tarix fərqi o deməkdir ki, bir tərəf EXIF məlumatını itirib və faylın dəyişdirilmə tarixinə (köçürmə tarixinə) qayıdıb. |
| 490 | `smart_conflict_counter` | %1$s (%2$d / %3$d) | %1$s (%2$d / %3$d) |
| 491 | `smart_conflict_incoming_label` | Incoming (New) | Gələn (Yeni) |
| 492 | `smart_conflict_existing_label` | Existing (Target) | Mövcud (Hədəf) |
| 493 | `smart_conflict_corrupted_unreadable` | Corrupted or unreadable | Zədələnmiş və ya oxuna bilməyən |
| 494 | `smart_conflict_integrity_failed` | Integrity check failed (Bit-rot / Corrupted) | Bütövlük yoxlaması uğursuz oldu (Bit çürüməsi / Zədələnmiş) |
| 495 | `smart_conflict_tap_to_preview` | Tap either thumbnail to inspect full-size preview | Tam ölçülü önizləmə üçün istənilən miniatürə toxunun |
| 496 | `smart_conflict_comparing` | Comparing file contents and dates… | Fayl məzmunları və tarixləri müqayisə edilir… |
| 497 | `smart_conflict_content_differs_warning` | File contents differ.\nIf these two files were supposed to be the same, data corruption may have occurred. Keeping both and inspecting them is recommended. | Fayl məzmunları fərqlidir.\nBu iki fayl eyni olmalı idisə, məlumat zədələnməsi baş verə bilər. Hər ikisini saxlayıb yoxlamaq tövsiyə olunur. |
| 498 | `smart_conflict_date_taken_differs` | File contents are identical, creation (date taken) timestamps differ. | Fayl məzmunları eynidir, yaratma (çəkilmə tarixi) vaxt damğaları fərqlidir. |
| 499 | `smart_conflict_modified_date_differs` | File contents are identical, modification dates differ. | Fayl məzmunları eynidir, dəyişdirilmə tarixləri fərqlidir. |
| 500 | `smart_conflict_both_dates_differ` | File contents are identical, creation and modification dates differ. | Fayl məzmunları eynidir, yaratma və dəyişdirilmə tarixləri fərqlidir. |
| 501 | `smart_conflict_meta_date_taken` | Taken: %s | Çəkilib: %s |
| 502 | `smart_conflict_meta_date_modified` | Modified: %s | Dəyişdirilib: %s |
| 503 | `album_type_icons` | Album type icons | Albom növü nişanları |
| 504 | `virtual_folders` | Virtual folders | Virtual qovluqlar |
| 505 | `favorites_album` | Favorites | Sevimlilər |
| 506 | `excluded_folders_album` | Excluded folders | İstisna edilmiş qovluqlar |
| 507 | `hidden_folders_album` | Hidden folders | Gizli qovluqlar |
| 508 | `temporarily_show_hidden_folders` | Temporarily show hidden folders | Gizli qovluqları müvəqqəti göstər |
| 509 | `stop_showing_hidden_folders` | Stop showing hidden folders | Gizli qovluqları göstərməyi dayandır |
| 510 | `unexclude` | Stop excluding | İstisna etməyi dayandır |
| 511 | `hide_folder` | Hide | Gizlət |
| 512 | `unhide_folder` | Unhide | Gizlətməni ləğv et |
| 513 | `about_title` | About Calypso Gallery | Calypso Gallery haqqında |
| 514 | `about_version` | Version %1$s (%2$s) | Versiya %1$s (%2$s) |
| 515 | `about_fork_description` | Calypso Gallery is an independently maintained, unofficial personal fork of Fossify Gallery. It is not affiliated with or endorsed by Fossify. | Calypso Gallery, Fossify Gallery-nin müstəqil olaraq saxlanılan, qeyri-rəsmi şəxsi forkudur. Fossify ilə heç bir əlaqəsi yoxdur və onun tərəfindən dəstəklənmir. |
| 516 | `about_modification_notice` | This distribution is a modified version of Fossify Gallery. The fork was created from upstream commit 054228be2da275396eb03d2b9002807b5055afac (1.14.1-5-g054228be2), and all fork-specific modifications are documented in FORK_CHANGELOG.md. | Bu paylama Fossify Gallery-nin dəyişdirilmiş versiyasıdır. Fork, 054228be2da275396eb03d2b9002807b5055afac (1.14.1-5-g054228be2) upstream kommitindən yaradılmışdır və fork-a xas bütün dəyişikliklər FORK_CHANGELOG.md sənədində qeyd olunmuşdur. |
| 517 | `about_license_heading` | License | Lisenziya |
| 518 | `about_gpl_notice` | This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 3 of the License.\n\nThis program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.\n\nYou should have received a copy of the GNU General Public License along with this program. If not, use the license action below or visit https://www.gnu.org/licenses/. | Bu proqram sərbəst proqramdır: siz onu Free Software Foundation tərəfindən nəşr olunan GNU General Public License-ın (Lisenziyanın 3-cü versiyası) şərtlərinə əsasən yenidən paylaya və/və ya dəyişdirə bilərsiniz.\n\nBu proqram faydalı olacağı ümidi ilə paylanır, lakin HEÇ BİR ZƏMANƏT YOXDUR; hətta SATILA BİLƏRLİK və ya MÜƏYYƏN MƏQSƏDƏ YARARLILIQ barədə dolayı zəmanət də verilmir. Ətraflı məlumat üçün GNU General Public License-a baxın.\n\nBu proqramla birlikdə GNU General Public License-ın surətini almalısınız. Əgər almamısınızsa, aşağıdakı lisenziya əməliyyatından istifadə edin və ya https://www.gnu.org/licenses/ ünvanına baş çəkin. |
| 519 | `about_read_gpl_license` | Read the GNU GPL v3 license | GNU GPL v3 lisenziyasını oxu |
| 520 | `about_read_tink_license` | Read the Tink Apache 2.0 license and attribution | Tink Apache 2.0 lisenziyasını və müəlliflik qeydini oxu |
| 521 | `about_source_heading` | Source code | Mənbə kodu |
| 522 | `about_corresponding_source_notice` | The complete corresponding source for this fork is kept in the fork repository. Tagged releases also have a matching source archive. The repository contains the build scripts, dependency declarations, and complete fork history needed to inspect and rebuild the project. | Bu fork-un tam müvafiq mənbə kodu fork deposunda saxlanılır. Etiketli buraxılışlarda uyğun mənbə arxivi də mövcuddur. Depoda layihəni yoxlamaq və yenidən qurmaq üçün tələb olunan qurma skriptləri, asılılıq bəyannamələri və tam fork tarixçəsi var. |
| 523 | `about_fork_source` | Fork source code and corresponding source | Fork mənbə kodu və müvafiq mənbə |
| 524 | `about_release_sources` | Release source archives | Buraxılış mənbə arxivləri |
| 525 | `about_fork_issues` | Fork issue tracker | Fork problem izləyicisi |
| 526 | `about_upstream_source` | Upstream Fossify Gallery source code | Upstream Fossify Gallery mənbə kodu |
| 527 | `about_upstream_project` | Upstream Fossify project | Upstream Fossify layihəsi |
| 528 | `about_help_heading` | Help and legal information | Kömək və hüquqi məlumat |
| 529 | `about_faq` | Frequently asked questions | Tez-tez verilən suallar |
| 530 | `about_third_party_licenses` | Third-party licenses | Üçüncü tərəf lisenziyaları |
| 531 | `gpl_license_title` | GNU General Public License v3 | GNU General Public License v3 |
| 532 | `tink_license_title` | Tink license and attribution | Tink lisenziyası və müəlliflik qeydi |
| 533 | `fork_disclaimer_title` | Fork notice | Fork bildirimi |
| 534 | `fork_disclaimer_message` | This application is not developed by the Fossify team. Calypso Gallery is an independently maintained, unofficial fork of Fossify Gallery; the original developer is referenced solely for attribution. It is not affiliated with, endorsed by, or supported by Fossify.\n\nBy continuing you acknowledge this notice. Fork-specific changes are documented in FORK_CHANGELOG.md. | Bu tətbiq Fossify komandası tərəfindən hazırlanmamışdır. Calypso Gallery, Fossify Gallery-nin müstəqil olaraq saxlanılan, qeyri-rəsmi forkudur; orijinal tərtibatçı yalnız müəlliflik qeydi məqsədilə göstərilir. Fossify ilə heç bir əlaqəsi yoxdur, o tərəfindən dəstəklənmir və ya dəstək verilmir.\n\nDavam edərək bu bildirimi qəbul etdiyinizi təsdiq edirsiniz. Fork-a xas dəyişikliklər FORK_CHANGELOG.md sənədində qeyd olunmuşdur. |
| 535 | `fork_disclaimer_acknowledge` | I understand | Başa düşdüm |
| 536 | `argon2_profile_title` | Brute-Force resistance | Brute-Force direnci |
| 537 | `brute_force_mode_title` | Calibration mode | Kalibrasiya rejimi |
| 538 | `brute_force_mode_time` | Via vault unlock time | Seyfin kilidini açma vaxtı ilə |
| 539 | `brute_force_mode_presets` | Via ready-made modes | Hazır rejimlərlə |
| 540 | `brute_force_time_title` | Targeted vault unlock time | Hədəflənən seyf açılış vaxtı |
| 541 | `brute_force_time_hint` | Select the time spent during opening the encrypted vault. Keeping this value high provides extra security by making brute-force attacks harder. | Şifreli seyfin açılması zamanı sərf olunan vaxtı seçin. Bu dəyəri yüksək saxlamaq, sınaq-yanılma hücumlarını çətinləşdirərək əlavə təhlükəsizlik təmin edir. |
| 542 | `brute_force_time_min_hint` | The lower bound is this device's safe standard time. | Alt hədd bu cihazın təhlükəsiz standart vaxtıdır. |
| 543 | `brute_force_time_value_device` | %1$.1f s (on this device) | %1$.1f s (bu cihazda) |
| 544 | `brute_force_time_out_of_range` | This time is above the selectable maximum (10 s). Tap to change it. | Bu vaxt seçilə bilən yuxarı həddin (10 s) üzərindədir. Dəyişdirmək üçün toxunun. |
| 545 | `brute_force_time_value` | %1$.1f s | %1$.1f s |
| 546 | `brute_force_actual_params` | Stored: %1$d MiB / %2$d rounds | Saxlanılıb: %1$d MiB / %2$d dövr |
| 547 | `brute_force_clamped` |  (clamped) |  (məhdudlaşdırılıb) |
| 548 | `argon2_profile_low` | Low-end device (not recommended) | Aşağı səviyyəli cihaz (tövsiyə olunmur) |
| 549 | `argon2_profile_default` | Default | Susmaya görə |
| 550 | `argon2_profile_high` | High security | Yüksək təhlükəsizlik |
| 551 | `argon2_profile_hint` | If you don\'t know what you\'re doing, it is recommended to keep the default setting. This only affects newly created vaults and password changes. | Nə etdiyinizi bilmirsinizsə, susmaya görə parametri saxlamaq tövsiyə olunur. Bu, yalnız yeni yaradılan seyflərə və parol dəyişikliklərinə təsir edir. |
| 552 | `argon2_profile_current` | Current: %s | Cari: %s |
| 553 | `show_pin_dots` | Show a dot for each PIN digit | PIN daxil edərkən hər rəqəm üçün bir nöqtə göstər |

## Settings Headers, Font Options & File Encryption Info

Added/overridden in `values-az/strings.xml` for Settings section headers, font picker options (`Monospace` standardized), and file-only encryption info dialog:

| # | Key (`name`) | English | Azerbaijani (`values-az/strings.xml`) |
|--:|---|---|---|
| 1 | `color_customization` | Look & feel | Görünüş və hiss |
| 2 | `application_settings` | Application | Tətbiq |
| 3 | `video_playback_settings` | Video playback | Video oxutma |
| 4 | `thumbnails_and_scrolling_settings` | Thumbnails & scrolling | Miniatürlər və sürüşdürmə |
| 5 | `media_viewer_settings` | Media viewer | Media görüntüləyicisi |
| 6 | `security_and_vault_settings` | Security & encrypted vault | Təhlükəsizlik və şifrəli seyf |
| 7 | `migrating` | Migrating | Köçürmə |
| 8 | `font_system_default` | System default | Sistem (susmaya görə) |
| 9 | `font_monospace` | Monospace | Monospace |
| 10 | `app_font` | App font | Tətbiq şrifti |
| 11 | `select_font_file` | Select font file… | Şrift faylını seçin… |
| 12 | `encrypt_files_info_message` | Folder & File Structure:\n• When an unencrypted album is encrypted, it is converted into an encrypted subfolder inside the vault (Secrets).\n• Both the folder name and all file names/contents inside it are encrypted, so nothing is readable outside the app. | Qovluq və Fayl Strukturu:\n• Şifrələnməmiş albom şifrələndikdə, seyf daxilində (Secrets) şifrələnmiş alt qovluğa çevrilir.\n• Həm qovluq adı, həm də içindəki bütün fayl adları/məzmunu şifrələnir, buna görə tətbiqdən kənarda heç nə oxuna bilməz. |

## Missing UI strings added (this revision)

`org.fossify:commons:7.1.3` does not provide these keys for Azerbaijani, and `unlocking_encrypted_vault` was missing from the app-owned resources, so local overrides were added to `values-az/strings.xml`.

| Key (`name`) | English source | Azerbaijani | Notes |
|---|---|---|---|
| `search_in_placeholder` | Search in %s | %s daxilində axtar | fossify-commons override; `%s` = album/folder name |
| `open_biometric_dialog` | Open biometric ID verification dialog | Biometrik şəxsiyyət doğrulama pəncərəsini aç | fossify-commons override; Biometrics tab of the inherited security dialog |
| `authenticate` | Authenticate | Doğrula | fossify-commons override; system biometric prompt title |
| `unlocking_encrypted_vault` | Unlocking encrypted vault… | Şifrəli seyfin kilidi açılır… | App-owned key; consistent with `Şifrəli Seyf` terminology |

## Appearance — Custom Colors & Saved Themes (9 keys)

Added to `values-az/strings.xml` for the Appearance settings **Custom colors** section and **Saved themes** workflow (English source strings in `values/strings.xml` were also completed):

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `custom_colors` | Custom colors | Fərdi rənglər | Appearance settings section header |
| 2 | `saved_themes` | Saved themes | Saxlanılmış temalar | Appearance settings row and dialog title |
| 3 | `save_theme` | Save theme | Temanı saxla | Positive dialog button to save current custom theme |
| 4 | `theme_saved` | Theme saved | Tema saxlanıldı | Toast shown after saving a custom theme |
| 5 | `theme_already_saved` | This theme is already saved | Bu tema artıq saxlanılıb | Toast shown when the current palette is already saved or built-in |
| 6 | `no_saved_themes` | No saved themes yet | Hələ saxlanılmış tema yoxdur | Empty state message in the Saved themes dialog |
| 7 | `custom_theme_name` | Custom theme | Fərdi tema | Base name for newly saved custom themes (suffixed with index) |
| 8 | `discard_changes_question` | Do you want to save your changes? | Dəyişikliklərinizi saxlamaq istəyirsiniz? | Prompt shown when switching themes with unsaved custom color changes |
| 9 | `continue_without_saving` | Continue without saving | Saxlamadan davam et | Negative dialog button to discard unsaved custom color changes |

## Onboarding & Full-Screen Permission Flow (29 keys)

Added to `values-az/strings.xml` for the 3-screen first-launch welcome and full-screen permission flow (unsupported system locales fall back cleanly to English in `values/strings.xml`). Also removed the retired `auto_backup_prompt_message` key since auto-backup is now enabled by default and always shown in Settings.

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `onboarding_motto` | No sign-up, no cloud, just your gallery. | Qeydiyyat yoxdur, bulud yoxdur, yalnız qalereyanız. | Welcome screen tagline |
| 2 | `onboarding_offline_title` | Completely Offline | Tamamilə Oflayn | Welcome card 1 title |
| 3 | `onboarding_offline_desc` | Never connects to the internet. Your data belongs only to you. | İnternetə qoşulmur. Məlumatlarınız yalnız sizə məxsusdur. | Welcome card 1 description |
| 4 | `onboarding_vault_title` | Encrypted Vault | Şifrəli Seyf | Welcome card 2 title |
| 5 | `onboarding_vault_desc` | Encrypt your photos and videos. No one else can access them. | Foto və videolarınızı şifrələyin. Sizdən başqa heç kim daxil ola bilməz. | Welcome card 2 description |
| 6 | `onboarding_albums_title` | Playlists for your photos | Fotolarınız üçün pleylistlər | Welcome card 3 title |
| 7 | `onboarding_albums_desc` | Virtual albums let you bring the files you want to see together in a single album, without moving them between folders or creating copies on disk. Unlike a regular copy, they use no extra storage because only the original file is kept on disk. You can add the same file to as many albums as you like, while your folder structure stays exactly as it is. | Virtual albomlər birlikdə görmək istədiyiniz faylları qovluqlar arasında daşımadan və ya diskdə nüsxələrini yaratmadan tək bir albomda birləşdirməyə imkan verir. Adi kopyalamadan fərqli olaraq, diskdə yalnız original fayl olduğu üçün əlavə yer tutmur. Eyni faylı istədiyiniz qədər alboma əlavə edə bilərsiniz, qovluq quruluşunuz isə dəyişməz qalır. | Welcome card 3 description |
| 8 | `onboarding_media_permission_note` | Media access permission is required to display your photos and videos. | Foto və videolarınızı göstərmək üçün media elementlərinə giriş icazəsi tələb olunur. | Welcome footer media permission note |
| 9 | `onboarding_continue` | Continue | Davam et | Welcome primary button |
| 10 | `onboarding_media_required_title` | Full media access required | Tam media girişi tələb olunur | Media permission screen title |
| 11 | `onboarding_media_required_desc` | Calypso Gallery needs full access to display all your photos and videos. | Calypso Qalereya bütün foto və videolarınızı göstərmək üçün tam giriş icazəsinə ehtiyac duyur. | Media permission screen description |
| 12 | `onboarding_media_steps_header` | How to enable permission | İcazəni necə açmaq olar? | Settings steps card header |
| 13 | `onboarding_media_step_1` | Settings | Parametrlər | Settings guide step 1 |
| 14 | `onboarding_media_step_2` | Permissions | İcazələr | Settings guide step 2 |
| 15 | `onboarding_media_step_3` | Photos and videos | Foto və videolar | Settings guide step 3 |
| 16 | `onboarding_media_step_4` | Allow all | Hamısına icazə ver | Settings guide step 4 (final) |
| 17 | `onboarding_media_required_hint` | Once access is granted, your gallery will open automatically when you return to the app. | Giriş icazəsi verildikdən sonra tətbiqə qayıtdıqda qalereyanız avtomatik açılacaq. | Media permission screen return hint |
| 18 | `onboarding_go_to_settings` | Go to settings | Parametrlərə keç | Media permission primary button |
| 19 | `onboarding_close` | Close | Bağla | Media permission secondary button |
| 20 | `onboarding_all_files_title` | All files access | Bütün fayllara giriş | All files access screen title |
| 21 | `onboarding_all_files_desc` | All files access is recommended for the Encrypted Vault and seamless file management. | Şifrəli Seyf və fasiləsiz fayl idarəetməsi üçün bütün fayllara giriş icazəsi tövsiyə olunur. | All files access screen subtitle |
| 22 | `onboarding_all_files_vault_title` | Encrypted Vault and hidden folders | Şifrəli Seyf və gizli qovluqlar | All files card 1 title |
| 23 | `onboarding_all_files_vault_desc` | Manage your encrypted files and hidden folders without system restrictions. | Şifrələnmiş fayllarınızı və gizli qovluqları sistem məhdudiyyəti olmadan idarə edin. | All files card 1 description |
| 24 | `onboarding_all_files_backup_title` | Don\'t lose your Favorites and Virtual Albums | Sevimlilərinizi və Virtual Albomlarınızı itirməyin | All files card 2 title |
| 25 | `onboarding_all_files_backup_desc` | Keep your favorites and virtual albums automatically backed up on your device even if you accidentally uninstall the app. You can turn off automatic backup anytime in app settings. | Tətbiqi təsadüfən silsəniz belə sevimliləriniz və virtual albomlarınız cihazınızda avtomatik ehtiyat nüsxələnir. İstəsəniz tətbiq parametrlərindən avtomatik ehtiyat nüsxələməni söndürə bilərsiniz. | All files card 2 description |
| 26 | `onboarding_all_files_offline_title` | Still completely on your device | Yenə tamamilə cihazınızda | All files card 3 title |
| 27 | `onboarding_all_files_offline_desc` | The app stays completely offline, this permission is used only for local file operations. | Tətbiq oflayn işləməyə davam edir, bu icazə yalnız yerli fayl əməliyyatları üçün istifadə olunur. | All files card 3 description |
| 28 | `onboarding_grant_access` | Grant access | İcazə ver | All files primary button |
| 29 | `onboarding_not_now` | Not now | İndi deyil | All files secondary button |

## Vault Lock & Album Header Polish (4 keys)

Added to `values-az/strings.xml` for the redesigned Vault Lock screen biometric button and album interior header.

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `use_biometrics` | Use biometrics | Biometriyadan istifadə et | Vault lock screen biometric button label |
| 2 | `media_header_items_count` | %d items | %d element | MediaActivity album header item count |
| 3 | `media_header_virtual_album` | Virtual album · %d items | Virtual albom · %d element | MediaActivity virtual album header subtitle |
| 4 | `media_header_encrypted_album` | Encrypted album · %d items | Şifrəli albom · %d element | MediaActivity encrypted album header subtitle |
## Appearance Live Preview (4 keys)

Added to `values-az/strings.xml` for the live Appearance preview card and WCAG contrast warning.

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `appearance_preview` | Preview | Önizləmə | Appearance live preview section header |
| 2 | `appearance_preview_text` | Your memories, your colors | Xatirələriniz, sizin rəngləriniz | Appearance preview sample headline |
| 3 | `appearance_preview_button` | Sample button | Nümunə düymə | Appearance preview sample button |
| 4 | `appearance_contrast_warning` | Low contrast: text %1$.1f:1, button %2$.1f:1. Increase it for readability. | Aşağı kontrast: mətn %1$.1f:1, düymə %2$.1f:1. Oxunaqlılıq üçün artırın. | WCAG contrast warning in Appearance preview |

## Vault Privacy Input Toggle (1 key)

Added to `values-az/strings.xml` for the optional shoulder-surfing protection switch (default off).

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `hide_vault_key_presses` | Hide key presses when entering vault password | Kassa parolunu daxil edərkən düymə basışlarını gizlət | Settings switch to hide PIN/password key press feedback |

## PIN Dot Indicator Setting (1 key)

Added to `values-az/strings.xml` for the optional per-digit PIN dot indicator (default off; only shown for PINs of at most 16 digits).

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `show_pin_dots` | Show a dot for each PIN digit | PIN daxil edərkən hər rəqəm üçün bir nöqtə göstər | Settings switch; hidden for alphanumeric vaults and PINs longer than 16 digits |

## KDF Unlock-Time Hint Revision (this revision)

Updated `brute_force_time_hint` and added `brute_force_time_min_hint` in `values-az/strings.xml`. The lower-bound sentence is appended only when the device's measured safe floor is above 1.0 s.

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_time_hint` | Select the time spent during opening the encrypted vault. Keeping this value high provides extra security by making brute-force attacks harder. | Şifreli seyfin açılması zamanı sərf olunan vaxtı seçin. Bu dəyəri yüksək saxlamaq, sınaq-yanılma hücumlarını çətinləşdirərək əlavə təhlükəsizlik təmin edir. | Explanation shown in the target vault unlock time dialog |
| 2 | `brute_force_time_min_hint` | The lower bound is this device's safe standard time. | Alt hədd bu cihazın təhlükəsiz standart vaxtıdır. | Appended only when minSec > 1.0 |

## Biometric Unavailability Explanation (this revision)

Added to `values-az/strings.xml`. The Settings row now stays visible when a vault exists; if the device only has weak (Class 2) biometrics or cannot create a hardware-backed key, the row is disabled and these strings explain why in a dialog and under the row. They replace the short `no_biometric_hardware_or_enrolled` toast. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `vault_biometric_unavailable_title` | Biometric vault access unavailable | Şifrəli seyfə biometrik giriş mövcud deyil | Dialog title |
| 2 | `vault_biometric_unavailable_no_hardware` | This device has no strong biometric hardware (fingerprint or Class 3 face unlock). Vault biometric access is unavailable; use your PIN or password. | Bu cihazda güclü biometrik avadanlıq (barmaq izi və ya 3-cü sinif üz tanıma) yoxdur. Seyfə biometrik giriş mövcud deyil; PIN və ya parolunuzdan istifadə edin. | `BIOMETRIC_ERROR_NO_HARDWARE` |
| 3 | `vault_biometric_unavailable_none_enrolled` | No strong biometric is enrolled. Weak face unlock is not accepted; enrol a fingerprint or a Class 3 face unlock in system settings. | Güclü biometrik məlumat qeydiyyata alınmayıb. Zəif üz tanıma qəbul edilmir; sistem parametrlərində barmaq izi və ya 3-cü sinif üz tanıma qeydiyyata alın. | `BIOMETRIC_ERROR_NONE_ENROLLED`; weak face unlock rejected |
| 4 | `vault_biometric_unavailable_hw_unavailable` | The biometric sensor is temporarily unavailable. Try again later. | Biometrik sensor müvəqqəti olaraq mövcud deyil. Daha sonra yenidən cəhd edin. | `BIOMETRIC_ERROR_HW_UNAVAILABLE` |
| 5 | `vault_biometric_unavailable_security_update` | Biometrics require a security update before they can be used. | Biometrik funksiyalardan istifadə etmək üçün cihazın təhlükəsizlik yeniləməsi quraşdırılmalıdır. | `BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED` |
| 6 | `vault_biometric_unavailable_no_secure_key` | A hardware-backed key (TEE/StrongBox) could not be created on this device. Vault biometric access is disabled for security reasons. | Bu cihazda aparat dəstəkli açar (TEE/StrongBox) yaradıla bilmədi. Təhlükəsizlik səbəbindən seyfə biometrik giriş deaktiv edilib. | `canAuthenticate()` succeeded but no TEE/StrongBox key |
| 7 | `vault_biometric_unavailable_generic` | Vault biometric access is unavailable on this device. Enrol a fingerprint or a strong face unlock in system settings, or use your PIN or password. | Bu cihazda seyfə biometrik giriş mövcud deyil. Sistem parametrlərində barmaq izi və ya güclü üz tanıma qeydiyyata alın, yaxud PIN/parolunuzdan istifadə edin. | Fallback explanation |

## Password Reminder Strings (this revision)

Added/updated in `values-az/strings.xml`. The reminder is shown in-app when the vault is opened and no password/PIN unlock happened during the configured interval. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `vault_password_reminder` | Remind me of my password | Parolamı müntǙǙm xatırlat |  |
| 2 | `vault_password_reminder_disabled` | Off | Bağlı |  |
| 3 | `vault_password_reminder_weeks` | %d weeks | %d həftə |  |
| 4 | `vault_password_reminder_months` | %d months | %d ay |  |
| 5 | `vault_password_reminder_dialog_title` | Reminder interval | Xatırlatma intervalı |  |
| 6 | `vault_password_reminder_dialog_message` | If you do not open the encrypted vault with your password for this long, we will remind you of it. | Bu mýddət ərzində şifrəli seyfə parolunuzla daxil olmasanız, parolanızı xatırladacağıq. |  |
| 7 | `vault_password_reminder_setup_note` | We will send you periodic reminders so you do not forget your password. You can turn this off in Settings → Encrypted vault → Remind me of my password. | Parolanızı unutmamanız üçün müntǙǙm xatırlatmalar göndərəcəyik. Bunu Parametrlər → Şifrəli seyf → Parolamı müntǙǙm xatırlat bölməsindən bağlaya bilərsiniz. |  |
| 8 | `vault_biometric_setup_password_note` | If you choose to sign in with biometrics, remember that you still need to know your password. | Biyometrik ilə daxil olmağı seçsəniz, parolanızı hələ də bilməli olduğunuzu unutmayın. |  |
| 9 | `vault_password_saved_title` | Have you saved your password? | Parolanızı qeyd etdinizmi? |  |
| 10 | `vault_password_saved_message` | If you forget your password, you will permanently lose access to your encrypted vault. Make sure you have saved it somewhere before continuing. Using a password manager is recommended. | Parolanızı unutsanız, şifrəli seyfə girişi daimi olaraq itirəcəksiniz. Davam etməzdən əvvəl parolanızı bir yerə qeyd etdiyinizdən əmin olun. Parola menecerindən istifadə etmək tövsiyə olunur. |  |
| 11 | `vault_password_saved_confirm` | Yes, I saved it | Bəli, qeyd etdim |  |
| 12 | `vault_password_saved_later` | I will save it | Qeyd edəcəyəm |  |
| 13 | `vault_password_reminder_due_title` | Do you still remember your vault password? | Seyf parolanızı hələ də xatırlayırsınız? |  |
| 14 | `vault_password_reminder_due_message` | You have not opened the encrypted vault with your password for this long. Your password is the only way to recover the vault. | Bu mýddət ərzində şifrəli seyfə parolanızla daxil olmadınız. Parolanız seyfi bərpa etməyin yeganə yoludur. |  |
| 15 | `vault_password_reminder_verify_title` | Confirm your password | Parolanızı təsdiqləyin |  |
| 16 | `vault_password_reminder_verify_message` | This is not a vault unlock. We only want to confirm that you still know your password. | Bu, seyfin açılması deyildir. Yalnızca parolanızı hələ də bildiyinizi təsdiqləyirik. |  |
| 17 | `vault_password_reminder_verified` | Password confirmed | Parola təsdiqləndi |  |
| 18 | `vault_password_reminder_not_now` | Not now | İndi deyil |  |
| 19 | `vault_password_reminder_forgot_title` | Do not remember your password? | Parolanızı xatırlamırsınız? |  |
| 20 | `vault_password_reminder_forgot_steps` | Unlock the vault with biometrics, export your encrypted data, then use Settings → Reset encrypted vault to set a new password and restore your data. If biometrics cannot unlock it either, the vault is inaccessible. | Biyometrik ilə seyfə daxil olun, şifrəli məlumatlarınızı ixrac edin, sonra Parametrlər → Şifrəli seyfi sıfırla ilə yeni parola təyin edib məlumatları geri yükləyin. Biyometrik də açamırsa, seyf əlçatan deyildir. |  |
| 21 | `vault_password_reminder_verify_action` | Verify my password | Parolamı təsdiqlə |  |
| 22 | `vault_password_reminder_forgot_action` | I don't remember | Xatırlamıram |  |
| 23 | `vault_password_reminder_open_settings` | Open settings | Parametrlərə get |  |
| 24 | `empty_encrypted_vault` | Reset encrypted vault | Şifrəli seyfi sıfırla | Renamed label |

## Vault Archive & Forgot-Password Strings (this revision)

Added/updated in `values-az/strings.xml`. Archive moves the old vault; export copies it; deletion is left to the file manager (ADR 0007). English source: `values/strings.xml`.

| # | Key (`name`) | English | Azerbaijani |
|--:|---|---|---|
| 1 | `archive_vault` | Archive vault | Kasayı arşivle |
| 2 | `archive_vault_summary` | Moves the old vault to the archive and creates a new one | Eski kasayı arşive taşır, varsayılan konumda yeni kasa oluşturur |
| 3 | `archive_vault_confirm` | The current vault will be moved to the archive and a new vault will be created in the default location. Nothing is deleted. To use the vault on another device, use Export encrypted vault — it copies the files and the current vault stays where it is. | Mevcut kasa arşive taşınacak, varsayılan konumda yeni kasa oluşturulacak. Hiçbir şey silinmez. Kasayı başka cihazda kullanmak için Şifreli kasayı dışa aktar seçeneğini kullanın; bu kopyalar, mevcut kasa yerinde kalır. |
| 4 | `archive_vault_success` | Vault archived. Location: %1$s | Kasa arşivlendi. Konum: %1$s |
| 5 | `archive_vault_failed` | Could not archive the vault. Nothing was changed. | Kasa arşivlenemedi. Hiçbir şey değişmedi. |
| 6 | `select_archived_vault` | Select from archived vaults | Arşivlenmiş kasalardan seç |
| 7 | `archived_vault_indexed` | %1$d. archived vault | %1$d. arşivlenen kasa |
| 8 | `archived_vault_unknown_date` | Encrypted vault with unknown date | Bilinmeyen tarihli şifreli kasa |
| 9 | `archived_vault_invalid` | Invalid archive entry | Geçersiz arşiv kaydı |
| 10 | `archived_vault_invalid_message` | Vault header not found at %1$s. You can delete this folder with a file manager. | %1$s konumunda kasa başlığı bulunamadı. Bu klasörü dosya yöneticisiyle silebilirsiniz. |
| 11 | `copy_path` | Copy path | Yolu kopyala |
| 12 | `faq_forgot_password_title` | I forgot my encrypted vault password, what can I do? | Şifreli kasanın parolasını unuttum, ne yapabilirim? |
| 13 | `faq_forgot_password_text` | 1. Unlock the vault with biometrics. 2. Copy (export) your encrypted data — copying is not moving; the original stays in place. 3. Settings → Reset encrypted vault to set a new password and restore.  Warning: if biometrics cannot unlock it either, the vault is inaccessible. You can archive the current vault and create a new one, copy the encrypted data for another device, or delete the vault folder at %1$s with a file manager. Deletion cannot be undone. | 1. Biyometrik ile kasaya giriş yapın. 2. Şifreli verilerinizi kopyalayın (dışa aktarın) — kopyalama taşıma değildir. 3. Ayarlar → Şifreli kasayı sıfırla ile yeni parola oluşturun.  Uyarı: biyometrik de çalışmıyorsa kasaya erişim yoktur. Kasayı arşivleyip yeni kasa oluşturabilir, verileri kopyalayabilir veya %1$s konumundaki klasörü dosya yöneticisiyle silebilirsiniz. |
| 14 | `vault_password_reminder_forgot_steps` | 1. Unlock the vault with biometrics. 2. Copy (export) your encrypted data — this does not move the vault. 3. Settings → Reset encrypted vault to set a new password and restore.  If biometrics cannot unlock it either, the vault is inaccessible. You can archive the current vault and create a new one, copy the encrypted data for another device, or delete the vault folder at %1$s with a file manager (cannot be undone). | 1. Biyometrik ile giriş yapın. 2. Şifreli verileri kopyalayın (dışa aktarın). 3. Ayarlar → Şifreli kasayı sıfırla ile yeni parola.  Biyometrik de çalışmıyorsa kasa erişilemez; arşivleyebilir, kopyalayabilir veya %1$s konumundaki klasörü dosya yöneticisiyle silebilirsiniz. |

## Unarchive Strings (this revision)

Added to `values-az/strings.xml`. Unarchive moves the vault back to its recorded original location; no swap, no password.

| # | Key | English | Azerbaijani |
|--:|---|---|---|
| 1 | `unarchive_vault` | Unarchive vault | Arşivden çıkar |
| 2 | `unarchive_vault_summary` | Moves the vault back to its location before archiving | Kasayı arşivlenmeden önceki konumuna geri taşır |
| 3 | `unarchive_vault_confirm` | The vault will be moved back to its original location. No password is required. Continue? | Kasa eski konumuna geri taşınacak. Parola gerekmez. Devam edilsin mi? |
| 4 | `unarchive_vault_success` | The vault was moved back to its original location. | Kasa eski konumuna geri taşındı. |
| 5 | `unarchive_vault_blocked` | Another vault is located where this vault used to be. Archive that vault first, then unarchive this one. | Bu kasanın eski konumunda başka bir kasa var. Önce o kasayı arşivleyin, ardından bu kasayı arşivden çıkarın. |
| 6 | `unarchive_vault_merge` | Combine data into one vault | Verileri tek bir kasada birleştir |
| 7 | `unarchive_vault_merge_steps` | Unlock the archived vault with its password, export the files in decrypted form, then import them into the other vault. The existing conflict-handling modes apply. | Arşiv kasasını parolasıyla açın, dosyaları çözülmüş olarak dışa aktarın, ardından diğer kasaya içe aktarın. |
| 8 | `archived_vault_activated` | The vault is open from the archive. Use Settings → Unarchive vault to move it back to its original location. | Kasa arşivden açıldı. Eski konumuna taşımak için Ayarlar → Arşivden çıkar seçeneğini kullanın. |

## Export Target Conflict Strings (this revision)

Added to `values-az/strings.xml`. Export now targets `<picked>/Calypso Gallery/Secrets` and is blocked when the target holds a different vault.

| # | Key | English | Azerbaijani |
|--:|---|---|---|
| 1 | `export_vault_different_vault` | The destination already contains a different encrypted vault. Export was cancelled to protect it. Choose an empty folder. | Hedef klasörde zaten farklı bir şifreli kasa var. Mevcut kasayı korumak için dışa aktarma iptal edildi. Lütfen boş bir klasör seçin. |
| 2 | `export_vault_unknown_content` | The destination contains encrypted files but no vault header. Export was cancelled to avoid mixing vaults. | Hedef klasörde şifreli dosyalar var ancak kasa başlığı yok. Kasaların karışmaması için dışa aktarma iptal edildi. |

## Existing Vault Time Recalculation (this revision)





Added `brute_force_time_value_device` and `brute_force_time_out_of_range` to `values-az/strings.xml`. The Settings/vault-setup summary now shows the existing vault's real unlock time on the current device (measured on unlock, otherwise estimated from the portable `.vault` KDF cost), with a note when it exceeds the 10 s maximum, showing only the stored parameters when this device has no measurement yet.

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_time_value_device` | %1$.1f s (on this device) | %1$.1f s (bu cihazda) | Value shown for an existing vault |
| 2 | `brute_force_time_out_of_range` | This time is above the selectable maximum (10 s). Tap to change it. | Bu vaxt seçilə bilən yuxarı həddin (10 s) üzərindədir. Dəyişdirmək üçün toxunun. | Shown when the real time exceeds 10 s |

## Silent identical-skip toast & Automatic mode date tie-break (this revision)

Added the `identical_files_skipped` plural and revised `date_tie_break_ask_info_message` in `values-az/strings.xml`. Smart dialog and Automatic mode now report silently skipped, byte-identical files at the end of an operation with a short toast; identical files with different dates that were reviewed in the thumbnail conflict dialog are excluded. "Always ask" can no longer be selected in Automatic (Experimental) mode and falls back to keeping the existing file's date. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Azerbaijani (`values-az/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `identical_files_skipped` | one: "%d identical file skipped" / other: "%d identical files skipped" | one: "%d eyni fayl atlandı" / other: "%d eyni fayl atlandı" | Short end-of-operation toast in Smart/Automatic mode |
| 2 | `date_tie_break_ask_info_message` | Smart dialog and Automatic mode exist for one reason: to skip files whose content is already present, so you only see the files that really need your attention — same name, different content. That is exactly the case where comparing thumbnails can reveal bit-rot.\n\nIn Smart dialog you will see two thumbnails of the very same picture, with nothing to verify, and you can decide there. Automatic (Experimental) mode never shows a dialog, so "Always ask" is not available there — selecting Automatic falls back to "Keep the existing file's date".\n\nUse this option only if you deliberately want to review every date difference, for example to fix a wrong date by hand.\n\nNote: when content is bit-identical, the EXIF capture date is identical too. A date difference means one side lost its EXIF data and fell back to the file modification date (the copy date). | Ağıllı dialoq və Avtomatik rejim yalnız bir səbəbdən mövcuddur: məzmunu artıq mövcud olan faylları ötürmək, beləliklə yalnız həqiqətən diqqətinizi tələb edən faylları görəsiniz — eyni adda, lakin fərqli məzmunlu. Miniatürlərin müqayisəsi məhz belə hallarda bit çürüməsini (bit-rot) aşkar edə bilər.\n\nBurada "Həmişə soruş" seçsəniz, fərqli tarixli hər eyni fayl sizə ziddiyyət kimi qayıdacaq. Ağıllı dialoqda tam eyni şəklin iki miniatürünü görəcək və orada qərar verəcəksiniz. Otomatik (Eksperimental) rejim heç vaxt dialoq göstərmədiyi üçün "Həmişə soruş" bu rejimdə mövcud deyil; Otomatik rejim seçildikdə "Mövcud faylın tarixini saxla" seçənəyinə qayıdılır.\n\nBu seçimdən yalnız hər bir tarix fərqini qəsdən nəzərdən keçirmək istədiyinizdə, məsələn yanlış tarixi əl ilə düzəltmək üçün istifadə edin.\n\nQeyd: məzmun bit-bə-bit eyni olduqda, EXIF çəkilmə tarixi də eyni olur. Tarix fərqi o deməkdir ki, bir tərəf EXIF məlumatını itirib və faylın dəyişdirilmə tarixinə (köçürmə tarixinə) qayıdıb. | Revised: "Always ask" unavailable in Automatic; falls back to keep-target date |

## New strings (biometric setup, device-owner check, archive size)

| Key | Translation |
|---|---|
| `vault_biometric_setup_continue` | Davam et |
| `device_owner_verify_title` | Cihaz sahibini təsdiqləyin |
| `device_owner_verify_subtitle` | Davam etmək üçün biometrik məlumat və ya cihaz PIN-i ilə təsdiqləyin. Kasa parolası tələb olunmur. |
| `archive_total_size_title` | Arxiv saxlama sahəsi |
| `archive_total_size` | Arxivlənmiş kasaların ümumi ölçüsü: %1$s |

## Weak password confirmation

| Key | Translation |
|---|---|
| `weak_password_warning_title` | Zəif parol |
| `weak_password_warning_message` | Bu parola zəif hesab olunur. Kasa təhlükəsizliyi üçün daha uzun və mürəkkəb parola tövsiyə olunur. Yenə də davam etmək istəyirsiniz? |
| `weak_password_warning_continue` | Yenə də davam et |

## Minimalist Settings, Appearance Redesign & Recent Fork Strings (this revision)

| Key | English | Azerbaijani |
|---|---|---|
| `appearance_title` | Appearance | Görünüş |
| `search_settings` | Search settings | Parametrlərdə axtar |
| `license_tink_title` | Google Tink (streaming AEAD and HKDF cryptography) | Google Tink (axınlı AEAD və HKDF kriptoqrafiyası) |
| `license_rustcrypto_argon2_title` | RustCrypto Argon2 (native Argon2id key derivation) | RustCrypto Argon2 (yerli Argon2id açar törətməsi) |
| `license_webpdecoder_title` | GlideWebpDecoder (animated WebP decoding) | GlideWebpDecoder (animasiyalı WebP deşifrələməsi) |
| `license_jxl_coder_title` | JXL Coder Glide (JPEG XL support) | JXL Coder Glide (JPEG XL dəstəyi) |
| `license_androidsvg_title` | AndroidSVG (SVG rendering) | AndroidSVG (SVG göstərilməsi) |
| `license_exif_extended_title` | Android-Exif-Extended (EXIF metadata handling) | Android-Exif-Extended (EXIF metaməlumat işlənməsi) |
| `license_material_icons_title` | Material Design Icons (UI icons) | Material Design Icons (interfeys nişanları) |
| `checking_password` | Checking password… | Parol yoxlanılır… |
| `vault_same_password_title` | Same password | Eyni parol |
| `vault_same_password_message` | The new password is identical to the current vault password. Please choose a different one. | Yeni parol cari seyf parolu ilə eynidir. Zəhmət olmasa fərqli bir parol seçin. |
| `brute_force_time_with_params` | %1$.1f s (%2$d MiB / %3$d rounds) | %1$.1f s (%2$d MiB / %3$d dövr) |
| `brute_force_high_memory_warning` | Higher security uses more memory. You may have trouble opening your vault on devices with 2 GB of RAM or less. If that happens, lower the unlock time in app settings and export the vault again. | Yüksək təhlükəsizlik daha çox yaddaşdan istifadə edir. 2 GB və daha az RAM-lı cihazlarda seyfinizi açmaqda problem yaşaya bilərsiniz. Belə bir halda tətbiq parametrlərindən açılış müddətini azaldıb seyfi yenidən ixrac edin. |
| `measuring_device_kdf_speed` | Measuring standard KDF speed on this device… | Bu cihazda standart KDF sürəti ölçülür… |
| `kdf_settings_updated_successfully` | Brute-Force resistance updated for encrypted vault | Brute-Force müqaviməti şifrəli seyfə tətbiq edildi |
| `appearance_automatic` | Automatic | Avtomatik |
| `follow_system_theme` | Follow system theme | Sistem temasını izlə |
| `follow_system_theme_summary` | Automatically switches between light and dark | Açıq və tünd tema arasında avtomatik keçid edir |
| `theme_midnight_blue` | Midnight Blue | Gecə Mavisi |
| `theme_warm_paper` | Warm Paper | İsti Kağız |
| `theme_deep_forest` | Deep Forest | Dərin Meşə |
| `appearance_contrast_ok` | Good contrast · Text %1$.1f:1 · Button %2$.1f:1 | Uyğun kontrast · Mətn %1$.1f:1 · Düymə %2$.1f:1 |
| `appearance_backup_section` | Backup | Ehtiyat nüsxə |
| `backup_saved_themes_to_storage` | Back up saved themes to device storage | Saxlanılmış temaların ehtiyat nüsxəsini cihaz yaddaşına çıxar |
| `settings_group_general` | General | Ümumi |
| `settings_group_folders_and_loading` | Folders and loading | Qovluqlar və yükləmə |
| `settings_group_security` | Security | Təhlükəsizlik |
| `settings_group_vault_management` | Vault management | Seyf idarəetməsi |
| `settings_group_playback` | Playback | Oxutma |
| `settings_group_gestures_and_tap` | Gestures and tap | Jestlər və toxunuş |
| `settings_group_display_and_motion` | Display and motion | Görünüş və hərəkət |
| `settings_group_thumbnail_styles` | Thumbnail styles | Miniatür üslubları |
| `settings_group_general_behavior` | General behavior | Ümumi davranış |
| `settings_group_conflicts_and_crypto` | Conflicts and encryption rules | Toqquşma və şifrələmə qaydaları |
| `settings_group_cleanup` | Cleanup | Təmizləmə |
| `settings_group_storage` | Storage | Yaddaş |
| `settings_group_settings_and_favorites` | Settings and favorites | Parametrlər və seçilmişlər |
| `settings_group_vault_transfer` | Encrypted vault transfer | Şifrəli seyf köçürməsi |
| `settings_search_results` | Search results | Axtarış nəticələri |
| `settings_no_search_results` | No matching settings found. | Uyğun parametr tapılmadı. |

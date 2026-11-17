# Traditional Chinese (Taiwan, values-zh-rTW) Translation Audit, Revision & English 1-to-1 Comparison

> **Scope:** `app/src/main/res/values-zh-rTW/strings.xml` was audited against `app/src/main/res/values/strings.xml` and `fossify-commons` (`values-zh-rTW`). 14 existing entries with mistranslations, Mainland Chinese phrasing, terminology mismatches, or formatting bugs were revised, and all 50 missing strings were translated into Taiwanese Traditional Chinese (`zh-rTW`) and added, bringing `values-zh-rTW` to full parity (347 `<string>` + 2 `<plurals>` = 349 resource entries; 372 total comparison rows including English `one`/`other` plural forms and 29 `fossify-commons` UI keys). Nine keys that were missing from `values-zh-rTW/strings.xml` were translated and added (`number_of_items`, `random`, `shuffle`, `stop_showing_excluded`, `copy_to_clipboard`, `encrypt`, `decrypt`, `encrypt_album`, `decrypt_album`), and eleven existing entries were revised so that files and albums use the new \"encrypt / decrypt\" wording and so that the favorites import/export labels mention virtual albums as well. Subsequently, 22 additional keys were localized in `values-zh-rTW/strings.xml`, `hide_folder_description` was removed, and `default_hidden_album` was marked `translatable="false"` (`Default Secret Album`), bringing `values-zh-rTW/strings.xml` to 372 `<string>` + 2 `<plurals>` = 374 resource entries and 393 total comparison rows.
> **Key Terminology:** `Settings` → `設定` · `Default` → `預設` / `系統預設` · `Folder` → `資料夾` · `Album` → `相簿` · `Encrypted Vault` → `加密保險箱` · `Hidden` → `隱藏` · `Excluded` → `已排除` · `Password` → `密碼` · `Encrypt / Decrypt` → `加密 / 解密` · `Copy to` → `複製` (matches `fossify-commons`) · `Clipboard` → `剪貼簿` · `Search` → `搜尋` (inherited from `fossify-commons`)

## 1. Audit of Existing Translations & Newly Added Entries

### 1.1 Review of 14 Revised Existing Translations

|   # | Key (`name`)                          | Old `values-zh-rTW`                                                                          | New `values-zh-rTW`                                                                 | Reason / Notes                                                                                                            |
| --: | ------------------------------------- | -------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
|   1 | `share_resized`                       | 共享調整尺寸後的版本                                                                                   | 分享調整尺寸後的版本                                                                          | Replaced Mainland term `共享` with standard Taiwan UI term `分享`                                                             |
|   2 | `portraits`                           | 直向                                                                                           | 人像                                                                                  | **Fixed mistranslation:** `TYPE_PORTRAITS` filters Portrait mode photo folders (`IMG_*`), not vertical screen orientation |
|   3 | `other_aspect_ratio`                  | 其它                                                                                           | 其他                                                                                  | Standardized character usage with `other_folder` (`其他資料夾`)                                                                |
|   4 | `by_last_modified_daily`              | 最後修改 （按日）                                                                                    | 最後修改（按日）                                                                            | Removed stray space before `（按日）` for consistency with `最後修改（按月）`, `拍攝日期（按日）`, `拍攝日期（按月）`                                 |
|   5 | `compromise`                          | 妥協                                                                                           | 折衷                                                                                  | Improved Taiwan UI terminology for `PRIORITY_COMPROMISE` under `檔案讀取優先權`                                                  |
|   6 | `password_protect_excluded`           | 密碼保護以隱藏特定資料夾                                                                                 | 以密碼保護已排除資料夾的可見性                                                                     | **Fixed meaning:** previously confused `excluded` (`已排除`) with `hidden` (`隱藏`)                                            |
|   7 | `toggle_file_visibility`              | 檔案顯示                                                                                         | 切換檔案可見性                                                                             | Clarified toggle action to match `toggle_favorite` (`切換我的最愛`)                                                             |
|   8 | `faq_16_text_extra`                   | 或者，您也可以通過您的裝置設定授予此相簿存取所有檔案的權限，這將允許我們顯示隱藏項目並使檔案操作更為可靠。                                        | 或者，您也可以透過您的裝置設定授予此相簿存取所有檔案的權限，這將允許我們顯示隱藏項目並使檔案操作更為可靠。                               | Replaced Mainland term `通過` with Taiwanese `透過`                                                                           |
|   9 | `faq_18_text`                         | …嘗試在裝置設定中找到您的預設影片播放器，然後對其進行清除其預設值。…                                                          | …請試著在裝置設定中找到您的預設影片播放器，並清除其預設值。…                                                     | Fixed duplicated phrasing (`對其進行清除其預設值`)                                                                                  |
|  10 | `excluded_activity_placeholder`       | 排除資料夾功能僅在 Fossify 圖片庫中排除選定的資料夾及其所有子資料夾，供其他應用程式正常顯示。\n(raw newlines)\n如要同時在其他應用程式中隱藏，請使用隱藏功能。 | 排除資料夾功能僅會在 Fossify 圖庫中排除選定的資料夾及其所有子資料夾，在其他應用程式中仍會正常顯示。\n\n如要同時在其他應用程式中隱藏，請使用「隱藏」功能。 | Removed stray literal line breaks before `\n` and standardized app name to `Fossify 圖庫`                                   |
|  11 | `exclude_folder_description`          | 僅在 Fossify 圖片庫中排除選定的資料夾及其所有子資料夾。稍後可在設定中管理排除的資料夾。                                             | 僅在 Fossify 圖庫中排除選定的資料夾及其所有子資料夾。稍後可在設定中管理已排除的資料夾。                                    | Standardized app name to `Fossify 圖庫`                                                                                     |
|  12 | `faq_5_text`                          | …而隱藏則是系統級別的操作…這是通過在指定資料夾創建…像是相機、截圖和下載等資料夾。                                                   | …而隱藏則是系統層級的操作…這是透過在指定資料夾建立…像是相機、螢幕截圖和下載等資料夾。                                        | Replaced Mainland terms (`級別`, `通過`, `創建`) with Taiwanese usage (`層級`, `透過`, `建立`, `螢幕截圖`) and standardized `Fossify 圖庫`  |
|  13 | `overwrite_original`                  | 覆寫原始內容                                                                                       | 覆寫原始檔案                                                                              | Clarified that the editor overwrites the original file                                                                    |
|  14 | `on_video_tap` / `open_in_app_player` | 輕點影片 / 開啟應用程式內播放程式                                                                           | 輕觸影片 / 開啟應用程式內播放器                                                                   | Replaced Mainland `輕點` with `輕觸` (consistent with `輕觸螢幕邊緣`) and unified `播放器` with `open_system_default_player`           |

*(Note: `on_video_tap` and `open_in_app_player` are listed together above for conciseness, totaling 15 adjusted string entries across 14 audit items.)*



### 1.2 Newly Added Fork-Only Strings (66 Keys)

These 66 strings were introduced by this fork (Gallery-Cipher) and do not exist in the original Fossify Gallery:

| # | Key (`name`) | English (`values/strings.xml`) | Taiwanese (`values-zh-rTW/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | `video_double_tap_duration` | Double-tap seek duration | 點兩下跳轉時間 | Video settings: double-tap forward/rewind seek interval; uses `點兩下` matching `allow_one_to_one_zoom` |
| 2 | `video_seek_duration_seconds` | %d seconds | %d 秒 | Used with durations 3, 5, 10, 15 via `String.format`; includes Taiwan CJK-number spacing |
| 3 | `create_virtual_album` | Create virtual album | 建立虛擬相簿 | Virtual albums section action |
| 4 | `new_virtual_album` | New album… | 新增相簿… | Action label for creating a new virtual album |
| 5 | `rename_virtual_album` | Rename album | 重新命名相簿 | Action label to rename a virtual album |
| 6 | `album_name` | Album name | 相簿名稱 | Input hint / label for album name |
| 7 | `virtual_folder_name` | Virtual folder name | 虛擬資料夾名稱 | Input hint / label for virtual folder |
| 8 | `virtual_album_name_empty` | The album name cannot be empty | 相簿名稱不可為空 | Validation error when album name is empty |
| 9 | `virtual_album_exists` | An album with this name already exists | 已存在相同名稱的相簿 | Validation error when album name already exists |
| 10 | `add_to_virtual_album` | Add to album | 加入相簿 | Menu action to add items to an album |
| 11 | `remove_from_virtual_album` | Remove from album | 從相簿中移除 | Menu action to remove items from an album |
| 12 | `added_to_virtual_album` | Added to \"%s\" | 已加入「%s」 | Toast message using Taiwanese corner brackets `「%s」` |
| 13 | `already_in_virtual_album` | Already in \"%s\" | 已在「%s」中 | Toast message using Taiwanese corner brackets `「%s」` |
| 14 | `removed_from_virtual_album` | Removed from the album | 已從相簿中移除 | Toast message after removing items from an album |
| 15 | `delete_virtual_album_confirmation` | Delete the album %s? Only the album is removed, your photos and videos stay where they are. | 確定要刪除相簿 %s 嗎？僅會移除相簿本身，您的相片和影片仍會保留在原處。 | No extra quotes around `%s` (code passes quoted name or item count) |
| 16 | `virtual_album_empty` | This album is empty. Select items in any folder and use \"Copy to\". | 此相簿為空。請在任意資料夾中選取項目，並使用「複製」。 | References `fossify-commons` `copy_to` (`複製`) |
| 17 | `virtual_album_cannot_be_modified` | This action is not available for virtual albums | 此操作不適用於虛擬相簿 | Toast message when action is unsupported on virtual albums |
| 18 | `alphanumeric_password` | Password | 密碼 | Matches `fossify-commons` `password` (`密碼`) |
| 19 | `repeat_password` | Repeat password | 再次輸入密碼 | Matches `fossify-commons` `repeat_pin` (`再次輸入PIN碼`) |
| 20 | `wrong_password` | Wrong password | 密碼錯誤 | Matches `fossify-commons` `wrong_pin` (`PIN碼錯誤`) |
| 21 | `encrypting_files` | Encrypting files… | 正在加密檔案… | Progress message using `正在…` style |
| 22 | `encrypting_folder` | Encrypting folder… | 正在加密資料夾… | Progress message using `正在…` style |
| 23 | `folder_encrypted_successfully` | Folder encrypted successfully | 資料夾加密成功 | Success message matching `圖片尺寸調整成功` |
| 24 | `decrypting_folder` | Decrypting folder… | 正在解密資料夾… | Progress message using `正在…` style |
| 25 | `folder_decrypted_successfully` | Folder decrypted successfully | 資料夾解密成功 | Success message matching `圖片尺寸調整成功` |
| 26 | `decrypting_files` | Decrypting files… | 正在解密檔案… | Progress message using `正在…` style |
| 27 | `place_finger_prompt` | Please place your finger on the fingerprint sensor | 請將手指放在指紋感應器上 | Matches `fossify-commons` `place_finger` terminology (`指紋感應器`) |
| 28 | `connect_at_least_4_dots_prompt` | Connect at least 4 dots | 請至少連接 4 個點 | Pattern lock validation prompt |
| 29 | `access_existing_cipher_secrets_prompt` | Do you want to access your existing Secrets folder? | 您要存取現有的 Secrets 資料夾嗎？ | Keeps literal folder name `Secrets` |
| 30 | `delete_and_recreate_cipher_secrets_prompt` | Existing Secrets folder will be deleted and a new one will be created. Are you sure? | 現有的 Secrets 資料夾將會被刪除並建立新的資料夾。確定要繼續嗎？ | Keeps literal folder name `Secrets` |
| 31 | `confirm_checkbox_label` | I confirm | 我確認 | Checkbox label, suffixed with countdown `(10)` in dialog |
| 32 | `secret_vault` | Encrypted Vault | 加密保險箱 | Vault feature title |
| 33 | `secret_vault_empty` | Encrypted Vault is empty | 加密保險箱為空 | Empty state placeholder for Encrypted Vault |
| - | `press_back_again_to_exit_vault` | Press back again to exit | 再按一次返回鍵即可離開 | Toast shown on first back press in hidden vault |
| - | `block_vault_screenshots` | Block screenshots in hidden vault | 禁止在隱藏保險箱中擷取螢幕截圖 | Settings switch (default ON) blocking screenshots in hidden vault |
| 34 | `confirm_decrypt_hidden_file` | Are you sure you want to decrypt the hidden file(s)? | 確定要解密隱藏的檔案嗎？ | Confirmation prompt for decrypting hidden file(s) |
| 35 | `copy_to_clipboard_disabled_in_vault` | Copying to clipboard is disabled while vault is unlocked for security. | 基於安全考量，保險箱解鎖期間已停用複製到剪貼簿功能。 | Security notice using `剪貼簿` matching `fossify-commons` |
| 44 | `open_system_language_settings` | Open system language settings | 開啟系統語言設定 | Neutral dialog button in language picker |
| 45 | `file_encrypted_successfully` | File encrypted successfully | 檔案加密成功  | Success toast after encrypting a single file |
| 46 | `files_encrypted_successfully` | Files encrypted successfully | 檔案加密成功  | Success toast after encrypting multiple files |
| 47 | `file_decrypted_successfully` | File decrypted successfully | 檔案解密成功    | Success toast after decrypting a single file |
| 48 | `files_decrypted_successfully` | Files decrypted successfully | 檔案解密成功    | Success toast after decrypting multiple files |
| 49 | `access_hidden_items_with_biometrics` | Access hidden items with biometrics | 使用生物辨識存取隱藏項目 | Settings switch, setup prompt title, and vault security dialog button |
| 50 | `enable_hidden_protection_first_prompt` | Please enable hidden item password protection first | 請先啟用隱藏項目的密碼保護 | Toast shown when enabling biometrics before hidden-item password protection |
| 51 | `biometric_unlock_vault` | Unlock encrypted vault | 解鎖加密保險箱 | BiometricPrompt title when unlocking the encrypted vault |
| 52 | `biometric_unlock_prompt` | Verify your biometric identity to access hidden items | 驗證您的生物辨識身分以存取隱藏項目 | BiometricPrompt subtitle when unlocking hidden items |
| 53 | `biometric_setup_prompt` | Confirm your biometric identity to enable biometric vault access | 確認您的生物辨識身分以啟用保險箱的生物辨識存取 | BiometricPrompt subtitle when enabling biometric vault access |
| 54 | `use_password_or_pin` | Use PIN / Password | 使用 PIN 碼 / 密碼 | BiometricPrompt negative button to fall back to PIN/password |
| 55 | `biometric_key_invalidated` | Biometric credentials have changed. Please enter your PIN or password. | 生物辨識憑證已變更，請輸入您的 PIN 碼或密碼。 | Toast shown when biometric credentials changed and Keystore key invalidated |
| 56 | `no_biometric_hardware_or_enrolled` | No biometric hardware or enrolled credentials found on this device | 此裝置上找不到生物辨識硬體或已註冊的憑證 | Toast shown when no biometric hardware or enrolled credentials exist |
| 57 | `default_hidden_album` | Default Hidden Album | 預設隱藏相簿 | Settings / picker label for default hidden album |
| 58 | `select_hidden_album` | Select Encrypted Album | 選擇加密相簿 | Dialog title for choosing the target encrypted album |
| 59 | `create_new_hidden_album` | Create New Encrypted Album | 建立新加密相簿 | Action label to create a new encrypted album |
| 60 | `new_hidden_album` | New encrypted album | 新加密相簿 | Input hint / default label for a new encrypted album |
| 61 | `hidden_album_name_empty` | Album name cannot be empty | 相簿名稱不能為空 | Validation error when hidden album name is blank |
| 62 | `file_hidden_to_album` | File encrypted to \"%s\" | 檔案已加密至「%s」 | Success toast after encrypting a single file into album `%s` |
| 63 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d 個檔案已加密至「%2$s」 | Success toast after encrypting `%1$d` files into album `%2$s` |
| 64 | `pull_down_to_open_vault` | Keep pulling down for Encrypted Vault | 繼續向下拉以開啟加密保險箱 | Vault pull gesture hint shown while the pull is below the open threshold |
| 65 | `keep_holding_to_open_vault` | Keep holding to open Encrypted Vault | 按住不放以開啟加密保險箱 | Vault pull gesture hint shown once the pull passes the threshold, before the 2.5 s hold completes |
| 66 | `release_to_open_vault` | Release to open Encrypted Vault | 放開以開啟加密保險箱 | Vault pull gesture hint shown after holding the pull for 2.5 s; releasing opens the vault |

### 1.3 Newly Added Translations for Strings Already Present in the Original Fossify Gallery (3 Keys)

These 3 strings already exist in the original Fossify Gallery (upstream); only the translations below were newly added in this fork:

| # | Key (`name`) | English (`values/strings.xml`) | Taiwanese (`values-zh-rTW/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | `enter_password` | Enter password | 輸入密碼 | Matches `fossify-commons` `enter_password` (`輸入密碼`) |
| 2 | `system_default` | System default | 系統預設 | Matches `fossify-commons` `system_default` (`系統預設`) |
| 3 | `language` | Language | 語言 | Matches `fossify-commons` `language` (`語言`) |

### 1.4 `fossify-commons` Inherited UI Strings (29 Keys)

All 29 shared UI keys below are already localized in `org.fossify:commons:7.1.3` (`res/values-zh-rTW/values-zh-rTW.xml`); no local override in `values-zh-rTW/strings.xml` was required (the last 13 keys were verified as fully translated in this revision):

| # | Key (`name`) | English (`fossify-commons`) | Taiwanese (`fossify-commons`) | Source / Notes |
|---:|---|---|---|---|
| 1 | `change_date_and_time_format` | Change date and time format | 更改日期和時間格式 | Inherited from `fossify-commons` (Settings) |
| 2 | `password_protect_file_deletion` | Password protect file deletion and moving | 保護檔案刪除和移動 | Inherited from `fossify-commons` (Settings) |
| 3 | `keep_last_modified` | Keep old last-modified value at file operations | 變更檔案後仍保留原有的最後修改日期 | Inherited from `fossify-commons` (Settings) |
| 4 | `clear_cache` | Clear cache | 清除快取 | Inherited from `fossify-commons` (Settings) |
| 5 | `export_settings` | Export settings | 匯出設定 | Inherited from `fossify-commons` (Settings) |
| 6 | `import_settings` | Import settings | 匯入設定 | Inherited from `fossify-commons` (Settings) |
| 7 | `temporarily_show_excluded` | Temporarily show excluded | 暫停排除項目 | Inherited from `fossify-commons` (Options menu) |
| 8 | `change_view_type` | Change view type | 顯示方式 | Inherited from `fossify-commons` (Options menu) |
| 9 | `contributors` | Contributors | 貢獻者 | Inherited from `fossify-commons` (About) |
| 10 | `known_issues` | Known issues | 已知問題 | Inherited from `fossify-commons` (About) |
| 11 | `donate_to_fossify` | Donate to Fossify | 贊助 Fossify | Inherited from `fossify-commons` (About) |
| 12 | `privacy_policy` | Privacy policy | 隱私政策 | Inherited from `fossify-commons` (About) |
| 13 | `create_shortcut` | Create shortcut | 建立捷徑 | Inherited from `fossify-commons` (CAB / ViewPager menu) |
| 14 | `print` | Print | 列印 | Inherited from `fossify-commons` (ViewPager menu) |
| 15 | `search_files` | Search files | 搜尋檔案 | Inherited from `fossify-commons` (Search bar hint) |
| 16 | `search_folders` | Search folders | 搜尋資料夾 | Inherited from `fossify-commons` (Search bar hint) |
| 17 | `manage_excluded_folders` | Manage excluded folders | 管理排除的資料夾 | Inherited from `fossify-commons` (Settings); verified translated in this revision |
| 18 | `exclude` | Exclude | 排除 | Inherited from `fossify-commons` (Album selection / long-press menu); verified translated in this revision |
| 19 | `exclude_folder` | Exclude folder | 排除資料夾 | Inherited from `fossify-commons` (Options menu); verified translated in this revision |
| 20 | `lock_folder` | Lock folder | 上鎖資料夾 | Inherited from `fossify-commons` (Album selection / long-press menu); verified translated in this revision |
| 21 | `change_cover_image` | Change cover image | 更換封面圖片 | Inherited from `fossify-commons` (Album selection / long-press menu); verified translated in this revision |
| 22 | `select_photo` | Select photo | 選擇相片 | Inherited from `fossify-commons` (Album selection, "Change cover image" dialog); verified translated in this revision |
| 23 | `use_default` | Use default | 使用預設 | Inherited from `fossify-commons` (Album selection, "Change cover image" dialog); verified translated in this revision |
| 24 | `column_count` | Column count | 欄數 | Inherited from `fossify-commons` (Main screen and in-album Options menu); verified translated in this revision |
| 25 | `open_the_recycle_bin` | Open the recycle bin | 開啟回收桶 | Inherited from `fossify-commons` (In-album Options menu); verified translated in this revision |
| 26 | `grid` | Grid | 格狀 | Inherited from `fossify-commons` (In-album Options menu, view type); verified translated in this revision |
| 27 | `list` | List | 列表 | Inherited from `fossify-commons` (In-album Options menu, view type); verified translated in this revision |
| 28 | `resize` | Resize | 調整尺寸 | Inherited from `fossify-commons` (Selection CAB menu); verified translated in this revision |
| 29 | `version_placeholder` | Version %s | 版本 %s | Inherited from `fossify-commons` (About screen version text); verified translated in this revision |

### Newly Added Missing Translations (9 Keys) & Revised Entries (11 Keys)

Nine keys that were missing from `values-zh-rTW/strings.xml` were translated and added, and eleven existing entries were revised so that files and albums use the new \"encrypt / decrypt\" wording and so that the favorites import/export labels mention virtual albums as well.

| # | Key (`name`) | English (`values/strings.xml`) | Traditional Chinese (Taiwan) (`values-zh-rTW/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | `number_of_items` | Number of items | 項目數 | Sorting option shown in the album view only; overridden locally because it is missing in `fossify-commons` for this locale |
| 2 | `random` | Random | 隨機 | Sorting option offered in every sorting state; overridden locally because it is missing in `fossify-commons` for this locale |
| 3 | `shuffle` | Shuffle | 隨機排列 | Fullscreen viewer action that randomizes the media order; app owned string that was only available in English |
| 4 | `stop_showing_excluded` | Stop showing excluded | 停止顯示已排除的項目 | Main screen options-menu action that ends the temporary preview of excluded folders |
| 5 | `copy_to_clipboard` | Copy to clipboard | 複製到剪貼簿 | Fullscreen viewer action that copies a file to the clipboard |
| 6 | `encrypt` | Encrypt | 加密 | Replaces the inherited `hide` label for files; files and albums are encrypted, not hidden |
| 7 | `decrypt` | Decrypt | 解密 | Replaces the inherited `unhide` label for files |
| 8 | `encrypt_album` | Encrypt album | 加密相簿 | Album (folder) context action that encrypts the album into the vault |
| 9 | `decrypt_album` | Decrypt album | 解密相簿 | Album (folder) context action that decrypts the album back to its original location |
| 10 | `export_favorite_paths` | Export favorites and virtual albums | 匯出我的最愛和虛擬相簿 | Settings label; renamed because virtual albums are exported together with the favorites |
| 11 | `import_favorite_paths` | Import favorites and virtual albums | 匯入我的最愛和虛擬相簿 | Settings label; renamed because virtual albums are imported together with the favorites |
| 12 | `file_encrypted_successfully` | File encrypted successfully | 檔案加密成功 | Success toast after encrypting a single file |
| 13 | `files_encrypted_successfully` | Files encrypted successfully | 檔案加密成功 | Success toast after encrypting multiple files |
| 14 | `file_decrypted_successfully` | File decrypted successfully | 檔案解密成功 | Success toast after decrypting a single file |
| 15 | `files_decrypted_successfully` | Files decrypted successfully | 檔案解密成功 | Success toast after decrypting multiple files |
| 16 | `file_hidden_to_album` | File encrypted to \"%s\" | 檔案已加密至「%s」 | Success toast after encrypting a single file into album `%s` |
| 17 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d 個檔案已加密至「%2$s」 | Success toast after encrypting `%1$d` files into album `%2$s` |
| 18 | `select_hidden_album` | Select Encrypted Album | 選擇加密相簿 | Dialog title for choosing the target encrypted album |
| 19 | `create_new_hidden_album` | Create New Encrypted Album | 建立新加密相簿 | Action label to create a new encrypted album |
| 20 | `new_hidden_album` | New encrypted album | 新加密相簿 | Input hint / default label for a new encrypted album |

### Newly Added UI, Backup, Encryption Confirmation & Vault Strings (22 Keys)

Following the previous audit, 22 additional keys were localized in `values-zh-rTW/strings.xml` (`create_new_encrypted_virtual_album` and `new_album` for vault destination dialogs, plus 20 strings covering backup export/import, auto-backup prompt, encryption confirmations, delete-behavior options, album lock/unlock labels, and progress notifications; `hide_folder_description` was removed from the app, and `default_hidden_album` was marked `translatable="false"` as `Default Secret Album`):

| # | Key (`name`) | English (`values/strings.xml`) | Taiwanese (`values-zh-rTW/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | `create_new_encrypted_virtual_album` | Create new encrypted virtual album | 建立新的加密虛擬相簿 | Dialog option for creating an encrypted virtual album inside the vault |
| 2 | `new_album` | New album | 新增相簿 | Compact neutral button label in the move destination dialog |
| 3 | `filename_without_zip` | Filename (without .zip) | 檔案名稱（不含 .zip） | Input hint in the export favorites and virtual albums dialog |
| 4 | `favorites_already_exist` | You already have a favorites list | 您已經有一個我的最愛清單 | Dialog title when importing favorites while a favorites list already exists |
| 5 | `delete_existing_list` | Delete existing list | 刪除現有清單 | Radio option to replace the existing favorites list on import |
| 6 | `merge_lists` | Merge lists | 合併清單 | Radio option to merge imported favorites with the existing list |
| 7 | `confirm` | Confirm | 確認 | Positive button label in the favorites import conflict dialog |
| 8 | `confirm_encrypt_album` | Are you sure you want to encrypt this album? All of its media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | 確定要加密此相簿嗎？其中的所有媒體檔案都會被加密並移至加密保險箱，原始檔案將會被刪除。 | Confirmation prompt before encrypting a single album into the vault |
| 9 | `confirm_encrypt_albums` | Are you sure you want to encrypt the selected albums? All of their media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | 確定要加密所選相簿嗎？其中的所有媒體檔案都會被加密並移至加密保險箱，原始檔案將會被刪除。 | Confirmation prompt before encrypting multiple selected albums into the vault |
| 10 | `confirm_encrypt_file` | Are you sure you want to encrypt this file? It will be encrypted and moved to the encrypted vault. The original file will be deleted. | 確定要加密此檔案嗎？它將會被加密並移至加密保險箱，原始檔案將會被刪除。 | Confirmation prompt before encrypting a single file into the vault |
| 11 | `confirm_encrypt_files` | Are you sure you want to encrypt the selected files? They will be encrypted and moved to the encrypted vault. The original files will be deleted. | 確定要加密所選檔案嗎？它們將會被加密並移至加密保險箱，原始檔案將會被刪除。 | Confirmation prompt before encrypting multiple selected files into the vault |
| 12 | `confirm_encrypt_files_to_vault` | The selected files will be encrypted when they are copied into the encrypted vault. Do you want to continue? | 所選檔案複製到加密保險箱時將會被加密。是否要繼續？ | Confirmation prompt when copying unencrypted files into an encrypted vault destination |
| 13 | `encrypt_decrypt_notifications` | Encrypt / Decrypt | 加密 / 解密 | Notification channel name for ongoing encrypt/decrypt progress notifications |
| 14 | `allow_batch_renaming` | Allow batch renaming when multiple files are selected | 選取多個檔案時允許批次重新命名 | Retained in values/strings.xml for parity (its Settings switch was removed in a6a78d444) |
| 15 | `keep_original_remove_from_virtual_album` | Keep the original file, remove it only from this virtual album | 保留原始檔案，僅從此虛擬相簿中移除 | Checkbox in delete confirmation dialog when deleting from a virtual album |
| 16 | `keep_original_remove_from_favorites` | Keep the original file, remove it only from favorites | 保留原始檔案，僅從我的最愛中移除 | Checkbox in delete confirmation dialog when deleting from favorites |
| 17 | `delete_original_from_favorites_and_virtual_albums` | Delete the original version of a file when it is deleted from favorites and virtual albums | 從我的最愛和虛擬相簿刪除檔案時，一併刪除原始檔案 | Settings switch controlling default delete behavior from favorites and virtual albums |
| 18 | `cannot_target_encrypted_album` | Encrypted albums cannot be targeted for this operation | 加密相簿無法作為此操作的目標 | Toast shown when an encrypted album cannot be targeted for an operation |
| 19 | `cannot_mix_encrypted_and_unencrypted` | Cannot combine encrypted and unencrypted items in this operation | 此操作無法同時結合已加密與未加密的項目 | Toast shown when an operation attempts to combine encrypted and unencrypted items |
| 20 | `lock_album` | Lock album | 鎖定相簿 | Album selection menu action replacing lock_folder |
| 21 | `unlock_album` | Unlock album | 解鎖相簿 | Album selection menu action replacing unlock_folder |
| 22 | `auto_backup_prompt_message` | Do you want your favorites and virtual albums to be regularly written to system storage outside of app memory? This will ensure that you do not lose your favorites list and virtual albums if you uninstall the app. You can manually create a backup from app settings at any time. | 是否要定期將您的我的最愛和虛擬相簿寫入應用程式記憶體以外的系統儲存空間？這可確保您在解除安裝應用程式時不會遺失我的最愛清單和虛擬相簿。您隨時可以從應用程式設定手動建立備份。 | Full-screen prompt asking whether to regularly back up favorites and virtual albums to shared storage |

## 2. Full 1-to-1 Comparison: English vs. Traditional Chinese (Taiwan)

| # | Key (`name`) | English (`values/strings.xml`) | Taiwanese (`values-zh-rTW/strings.xml`) |
|---:|---|---|---|
| 1 | `app_launcher_name` | Gallery | 圖庫 |
| 2 | `edit` | Edit | 編輯 |
| 3 | `open_camera` | Open camera | 開啟相機 |
| 4 | `hidden` | (hidden) | （已隱藏） |
| 5 | `excluded` | (excluded) | （已排除） |
| 6 | `pin_folder` | Pin folder | 釘選資料夾 |
| 7 | `unpin_folder` | Unpin folder | 取消釘選資料夾 |
| 8 | `pin_to_the_top` | Pin to the top | 釘選至頂部 |
| 9 | `show_all` | Show all folders content | 顯示全部資料夾內容 |
| 10 | `all_folders` | All folders | 全部資料夾 |
| 11 | `folder_view` | Switch to folder view | 切換為資料夾檢視 |
| 12 | `other_folder` | Other folder | 其他資料夾 |
| 13 | `show_on_map` | Show on map | 在地圖上顯示 |
| 14 | `unknown_location` | Unknown location | 未知的位置 |
| 15 | `volume` | Volume | 音量 |
| 16 | `brightness` | Brightness | 亮度 |
| 17 | `lock_orientation` | Lock orientation | 鎖定方向 |
| 18 | `unlock_orientation` | Unlock orientation | 解除鎖定方向 |
| 19 | `change_orientation` | Change orientation | 改變方向 |
| 20 | `force_portrait` | Force portrait | 強制直向 |
| 21 | `force_landscape` | Force landscape | 強制橫向 |
| 22 | `force_landscape_reverse` | Force landscape (reverse) | 強制橫向（反轉） |
| 23 | `use_default_orientation` | Use default orientation | 使用預設方向 |
| 24 | `fix_date_taken` | Fix Date Taken value | 修復拍攝日期 |
| 25 | `fixing` | Fixing… | 正在修復… |
| 26 | `dates_fixed_successfully` | Dates fixed successfully | 日期修復完成 |
| 27 | `no_date_takens_found` | No Date Taken values have been found | 未發現拍攝日期 |
| 28 | `share_resized` | Share a resized version | 分享調整尺寸後的版本 |
| 29 | `switch_to_file_search` | Switch to file search across all visible folders | 搜尋所有可見資料夾 |
| 30 | `set_as_default_folder` | Set as default folder | 設為預設資料夾 |
| 31 | `unset_as_default_folder` | Unset as default folder | 取消設為預設資料夾 |
| 32 | `reorder_by_dragging` | Reorder folders by dragging | 拖曳以重新排序資料夾 |
| 33 | `reorder_by_dragging_pro` | Reorder folders by dragging (Pro) | 拖曳以重新排序資料夾（專業版） |
| 34 | `restore_to_path` | Restoring to \'%s\' | 還原至「%s」 |
| 35 | `full_storage_permission_required` | Fossify Gallery needs full access to display all your photos and videos. Go to Settings &gt; Permissions &gt; Photos and videos &gt; Allow all. | Fossify 圖庫需要完整存取權限才能顯示您所有的照片與影片。請到「設定」→「隱私權」→「應用程式權限」→「相片和影片」→「一律全部允許」。 |
| 36 | `filter_media` | Filter media | 篩選媒體檔案 |
| 37 | `images` | Images | 圖片 |
| 38 | `videos` | Videos | 影片 |
| 39 | `gifs` | GIFs | GIF |
| 40 | `raw_images` | RAW images | RAW 圖檔 |
| 41 | `svgs` | SVGs | SVG |
| 42 | `portraits` | Portraits | 人像 |
| 43 | `no_media_with_filters` | No media files have been found with the selected filters. | 未發現符合篩選條件的媒體檔案。 |
| 44 | `change_filters_underlined` | <u>Change filters</u> | <u>更改篩選條件</u> |
| 45 | `exclude_folder_description` | This will exclude the selection together with its subfolders from Fossify Gallery only. You can manage excluded folders in Settings. | 僅在 Fossify 圖庫中排除選定的資料夾及其所有子資料夾。稍後可在設定中管理已排除的資料夾。 |
| 46 | `exclude_folder_parent` | Exclude a parent instead? | 是否排除上層資料夾？ |
| 47 | `excluded_activity_placeholder` | Excluding folders will make them together with their subfolders hidden just in Fossify Gallery, they will still be visible in other applications.\n\nIf you want to hide them from other apps too, use the Hide function. | 排除資料夾功能僅會在 Fossify 圖庫中排除選定的資料夾及其所有子資料夾，在其他應用程式中仍會正常顯示。\n\n如要同時在其他應用程式中隱藏，請使用「隱藏」功能。 |
| 48 | `hidden_folders` | Hidden folders | 隱藏資料夾 |
| 49 | `manage_hidden_folders` | Manage hidden folders | 管理隱藏的資料夾 |
| 50 | `hidden_folders_placeholder` | Seems like you don\'t have any folders hidden with a \".nomedia\" file. | 未發現以「.nomedia」檔案來隱藏的資料夾。 |
| 51 | `hidden_all_files` | You have to grant the app All Files access to see hidden files, else it cannot work. | 請務必授予此應用程式「所有檔案存取權」以檢視隱藏的檔案。 |
| 52 | `cant_unhide_folder` | If a folder or one of its parent folders has a dot before its name, it is hidden and cannot be unhidden like this. You have to remove the dot by renaming it. | 如果資料夾或其上層資料夾的名稱開頭為「.」，該資料夾將會被隱藏且無法直接取消隱藏。您需要重新命名資料夾以移除前方的「.」。 |
| 53 | `include_folders` | Included folders | 包含資料夾 |
| 54 | `manage_included_folders` | Manage included folders | 管理包含的資料夾 |
| 55 | `add_folder` | Add folder | 新增資料夾 |
| 56 | `included_activity_placeholder` | If you have some folders which contain media, but were not recognized by the app, you can add them manually here.\n\nAdding some items here will not exclude any other folder. | 如要新增包含媒體檔案但未被應用程式辨識的資料夾，可在此處手動新增。 \n \n新增項目不會排除其他資料夾。 |
| 57 | `no_media_add_included` | No media files have been found. You can solve it by adding the folders containing media files manually. | 未發現媒體檔案。您可以手動加入包含媒體檔案的資料夾來解決。 |
| 58 | `resize_and_save` | Resize selection and save | 調整選取項目尺寸並儲存 |
| 59 | `width` | Width | 寬度 |
| 60 | `height` | Height | 高度 |
| 61 | `keep_aspect_ratio` | Keep aspect ratio | 維持長寬比 |
| 62 | `invalid_values` | Please enter a valid resolution | 請輸入有效的解析度 |
| 63 | `resize_multiple_images` | Resize multiple images | 批次調整圖片尺寸 |
| 64 | `resize_factor` | Resize factor | 調整比例 |
| 65 | `resize_factor_info` | Resize images to the given percentage, value must be within 10 and 90. | 調整圖片尺寸至指定百分比，數值範圍為 10 至 90。 |
| 66 | `resize_factor_error` | Enter a number between 10 and 90 | 請輸入 10 到 90 之間的數值 |
| 67 | `images_resized_successfully` | Images resized successfully | 圖片尺寸調整成功 |
| 68 | `editor` | Editor | 編輯工具 |
| 69 | `basic_editor` | Basic Editor | 基礎編輯工具 |
| 70 | `advanced_editor` | Advanced Editor | 進階編輯工具 |
| 71 | `rotate` | Rotate | 旋轉 |
| 72 | `invalid_image_path` | Invalid image path | 無效的圖片路徑 |
| 73 | `invalid_video_path` | Invalid video path | 無效的影片路徑 |
| 74 | `image_editing_failed` | Image editing failed | 圖片編輯失敗 |
| 75 | `video_editing_failed` | Video editing failed | 影片編輯失敗 |
| 76 | `image_editing_cancelled` | Image editing cancelled | 圖片編輯取消 |
| 77 | `video_editing_cancelled` | Video editing cancelled | 影片編輯取消 |
| 78 | `file_edited_successfully` | File edited successfully | 檔案編輯成功 |
| 79 | `image_edited_successfully` | Image edited successfully | 圖片編輯成功 |
| 80 | `video_edited_successfully` | Video edited successfully | 影片編輯成功 |
| 81 | `edit_image_with` | Edit image with: | 編輯圖片: |
| 82 | `edit_video_with` | Edit video with: | 編輯影片: |
| 83 | `no_image_editor_found` | No image editor found | 找不到圖片編輯工具 |
| 84 | `no_video_editor_found` | No video editor found | 找不到影片編輯工具 |
| 85 | `unknown_file_location` | Unknown file location | 未知的檔案位置 |
| 86 | `overwrite_original` | Overwrite original | 覆寫原始檔案 |
| 87 | `error_saving_file` | Could not overwrite the source file | 無法覆蓋原始檔案 |
| 88 | `rotate_left` | Rotate left | 向左轉 |
| 89 | `rotate_right` | Rotate right | 向右轉 |
| 90 | `rotate_one_eighty` | Rotate by 180º | 旋轉 180º |
| 91 | `transform` | Transform | 變形 |
| 92 | `crop` | Crop | 裁切 |
| 93 | `draw` | Draw | 繪畫 |
| 94 | `flip` | Flip | 翻轉 |
| 95 | `flip_horizontally` | Flip horizontally | 水平翻轉 |
| 96 | `flip_vertically` | Flip vertically | 垂直翻轉 |
| 97 | `free_aspect_ratio` | Free | 自由 |
| 98 | `other_aspect_ratio` | Other | 其他 |
| 99 | `wallpaper` | Wallpaper | 桌布 |
| 100 | `set_as_wallpaper` | Set as Wallpaper | 設為桌布 |
| 101 | `set_as_wallpaper_failed` | Setting as Wallpaper failed | 設為桌布失敗 |
| 102 | `set_as_wallpaper_with` | Set as wallpaper with: | 用其他程式設為桌布: |
| 103 | `setting_wallpaper` | Setting wallpaper… | 正在設定桌布… |
| 104 | `wallpaper_set_successfully` | Wallpaper set successfully | 成功設為桌布 |
| 105 | `portrait_aspect_ratio` | Portrait aspect ratio | 直向長寬比 |
| 106 | `landscape_aspect_ratio` | Landscape aspect ratio | 橫向長寬比 |
| 107 | `home_screen` | Home screen | 主畫面 |
| 108 | `lock_screen` | Lock screen | 鎖定畫面 |
| 109 | `home_and_lock_screen` | Home and lock screen | 主畫面及鎖定畫面 |
| 110 | `allow_changing_aspect_ratio` | Allow changing the aspect ratio | 允許變更長寬比 |
| 111 | `slideshow` | Slideshow | 投影片 |
| 112 | `interval` | Interval | 間隔 |
| 113 | `include_photos` | Include photos | 包含照片 |
| 114 | `include_videos` | Include videos | 包含影片 |
| 115 | `include_gifs` | Include GIFs | 包含 GIF |
| 116 | `random_order` | Random order | 隨機順序 |
| 117 | `move_backwards` | Move backwards | 反向播放 |
| 118 | `loop_slideshow` | Loop slideshow | 投影片循環 |
| 119 | `animation` | Animation | 動畫 |
| 120 | `no_animation` | None | 無 |
| 121 | `fade` | Fade | 漸變 |
| 122 | `slide` | Slide | 滑動 |
| 123 | `slideshow_ended` | The slideshow ended | 投影片結束 |
| 124 | `no_media_for_slideshow` | No media for the slideshow have been found | 找不到投影片的媒體檔案 |
| 125 | `group_direct_subfolders` | Group direct subfolders | 分組子資料夾 |
| 126 | `group_by` | Group by | 分組方式 |
| 127 | `do_not_group_files` | Do not group files | 關閉分組 |
| 128 | `by_folder` | Folder | 資料夾 |
| 129 | `by_last_modified` | Last modified | 最後修改 |
| 130 | `by_last_modified_daily` | Last modified (daily) | 最後修改（按日） |
| 131 | `by_last_modified_monthly` | Last modified (monthly) | 最後修改（按月） |
| 132 | `by_date_taken` | Date taken | 拍攝日期 |
| 133 | `by_date_taken_daily` | Date taken (daily) | 拍攝日期（按日） |
| 134 | `by_date_taken_monthly` | Date taken (monthly) | 拍攝日期（按月） |
| 135 | `by_file_type` | File type | 檔案類型 |
| 136 | `by_extension` | Extension | 副檔名 |
| 137 | `show_file_count_section_header` | Show file count at section headers | 於分段標題顯示檔案數量 |
| 138 | `grouping_and_sorting` | Please note that grouping and sorting are 2 independent fields | 排序方式與分組方式為獨立設定 |
| 139 | `folder_on_widget` | Folder shown on the widget: | 在小工具顯示資料夾: |
| 140 | `show_folder_name` | Show folder name | 顯示資料夾名稱 |
| 141 | `autoplay_videos` | Play videos automatically | 自動播放影片 |
| 142 | `remember_last_video_position` | Remember last video playback position | 記住影片上次播放位置 |
| 143 | `loop_videos` | Loop videos | 循環播放影片 |
| 144 | `video_double_tap_duration` | Double-tap seek duration | 點兩下跳轉時間 |
| 145 | `video_seek_duration_seconds` | %d seconds | %d 秒 |
| 146 | `animate_gifs` | Animate GIFs at thumbnails | 在縮圖顯示 GIF 動畫 |
| 147 | `max_brightness` | Max brightness when viewing fullscreen media | 檢視全螢幕媒體時將亮度設定為最高亮度 |
| 148 | `render_ultra_hdr_photos` | Show Ultra HDR photos in HDR | 以 HDR 模式顯示 Ultra HDR 照片 |
| 149 | `crop_thumbnails` | Crop thumbnails into squares | 裁剪縮圖為正方形 |
| 150 | `show_thumbnail_video_duration` | Show video durations | 顯示影片長度 |
| 151 | `screen_rotation_by` | Rotate fullscreen media by | 全螢幕時旋轉方向 |
| 152 | `screen_rotation_system_setting` | System setting | 系統設定方向 |
| 153 | `screen_rotation_device_rotation` | Device rotation | 裝置實際方向 |
| 154 | `screen_rotation_aspect_ratio` | Aspect ratio | 圖片長寬比 |
| 155 | `black_background_at_fullscreen` | Black background at fullscreen media | 全螢幕使用黑色背景 |
| 156 | `scroll_thumbnails_horizontally` | Scroll thumbnails horizontally | 使用橫向捲動 |
| 157 | `hide_system_ui_at_fullscreen` | Automatically hide system UI at fullscreen media | 全螢幕時自動隱藏系統介面 |
| 158 | `delete_empty_folders` | Delete empty folders after deleting their content | 資料夾清空後自動刪除 |
| 159 | `keep_screen_on_fullscreen_photos` | Keep the screen on while viewing fullscreen photos | 檢視全螢幕照片時維持螢幕開啟 |
| 160 | `allow_photo_gestures` | Allow controlling photo brightness with vertical gestures | 啟用相片亮度控制手勢 |
| 161 | `allow_video_gestures` | Allow controlling video volume and brightness with vertical gestures | 啟用影片音量與亮度控制手勢 |
| 162 | `playback_speed` | Playback speed | 播放速度 |
| 163 | `show_media_count` | Show folder media count on the main view | 在主畫面顯示資料夾內的媒體檔案數量 |
| 164 | `show_extended_details` | Show extended details over fullscreen media | 全螢幕時顯示詳細資訊 |
| 165 | `manage_extended_details` | Manage extended details | 管理詳細資訊 |
| 166 | `one_finger_zoom` | Allow one finger zoom at fullscreen media | 全螢幕時使用單指縮放 |
| 167 | `allow_instant_change` | Allow instantly changing media by clicking on screen sides | 輕觸螢幕邊緣來快速切換媒體檔案 |
| 168 | `allow_deep_zooming_images` | Allow deep zooming images | 啟用強制放大圖片 |
| 169 | `hide_extended_details` | Hide extended details when status bar is hidden | 狀態欄隱藏時，同時隱藏詳細資訊 |
| 170 | `show_at_bottom` | Show some action buttons at the bottom of the screen | 在螢幕底部顯示一些操作按鈕 |
| 171 | `show_recycle_bin` | Show the recycle bin on the folders screen | 在資料夾畫面顯示回收桶 |
| 172 | `deep_zoomable_images` | Deep zoomable images | 可強制放大的圖片 |
| 173 | `show_highest_quality` | Show images in the highest possible quality | 以最高品質顯示圖片 |
| 174 | `show_recycle_bin_last` | Show the recycle bin as the last item on the main screen | 回收桶顯示在主畫面最後 |
| 175 | `allow_down_gesture` | Allow closing the fullscreen view with a down gesture | 以下滑手勢關閉全螢幕檢視 |
| 176 | `allow_one_to_one_zoom` | Allow 1:1 zooming in with two double taps | 點兩下螢幕來 1:1 縮放 |
| 177 | `open_videos_on_separate_screen` | Play videos on a separate screen with horizontal gestures | 透過橫向手勢在獨立螢幕上播放影片 |
| 178 | `on_video_tap` | On video tap | 輕觸影片 |
| 179 | `open_in_app_player` | Open in-app player | 開啟應用程式內播放器 |
| 180 | `open_system_default_player` | Open default player app | 開啟預設播放器應用程式 |
| 181 | `show_notch` | Show a notch if available | 允許螢幕凹口遮擋畫面 |
| 182 | `allow_rotating_gestures` | Allow rotating images with gestures | 使用手勢來旋轉圖片 |
| 183 | `file_loading_priority` | File loading priority | 檔案讀取優先權 |
| 184 | `speed` | Speed | 速度 |
| 185 | `compromise` | Compromise | 折衷 |
| 186 | `avoid_showing_invalid_files` | Avoid showing invalid files | 避免顯示無效的檔案 |
| 187 | `show_image_file_types` | Show image file types | 顯示圖片檔案類型 |
| 188 | `allow_zooming_videos` | Allow zooming videos with double tapping them | 點兩下螢幕來縮放影片 |
| 189 | `folder_thumbnail_style` | Folder thumbnail style | 資料夾縮圖樣式 |
| 190 | `file_thumbnail_style` | File thumbnail style | 檔案縮圖樣式 |
| 191 | `mark_favorite_items` | Mark favorite items | 標記我的最愛 |
| 192 | `thumbnail_spacing` | Thumbnail spacing | 縮圖間距 |
| 193 | `show_file_count_line` | Show file count on a separate line | 獨立一行顯示檔案數量 |
| 194 | `show_file_count_brackets` | Show file count in brackets | 在括號內顯示檔案數量 |
| 195 | `show_file_count_none` | Do not show file count | 不顯示檔案數量 |
| 196 | `limit_folder_title` | Limit long folder titles to 1 line | 資料夾名稱最長只顯示一行 |
| 197 | `square` | Square | 方形 |
| 198 | `rounded_corners` | Rounded corners | 圓角 |
| 199 | `export_favorite_paths` | Export favorites and virtual albums | 匯出我的最愛和虛擬相簿 |
| 200 | `import_favorite_paths` | Import favorites and virtual albums | 匯入我的最愛和虛擬相簿 |
| 201 | `paths_imported_successfully` | Paths imported successfully | 路徑匯入成功 |
| 202 | `media_management_prompt` | To make sure that all file operations work reliably, please make this app a Media management app in your device settings. | 為了確保所有檔案操作都能可靠地運作，請將此應用程式設為裝置設定中的媒體管理應用程式。 |
| 203 | `password_protect_excluded` | Password protect excluded folder visibility | 以密碼保護已排除資料夾的可見性 |
| 204 | `media_management_manual` | Something went wrong, please go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | 出錯了，請至裝置的設定 - 應用程式（和通知） - 特殊應用程式存取權 - 媒體管理應用程式，允許本應用程式管理媒體。 |
| 205 | `media_management_note` | If the redirection does not work, please go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | 若重新導向未順利運作，請至裝置的設定 - 應用程式（和通知） - 特殊應用程式存取權 - 媒體管理應用程式，允許本應用程式管理媒體。 |
| 206 | `media_management_alternative` | If you do not want to do it, you can also go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | 如果您不想這麼做，也可以進入您的裝置設定 - 應用程式 - 特殊應用程式存取 - 媒體管理應用程式，並允許此應用程式管理媒體。 |
| 207 | `alternative_media_access` | Alternatively, you can allow accessing media files only. In that case you will not be able to work with hidden files though. | 或者，您只能允許存取媒體檔案。但在這種情況下，您將無法處理隱藏檔案。 |
| 208 | `media_only` | Media only | 僅媒體 |
| 209 | `all_files` | All files | 所有檔案 |
| 210 | `main_screen_view_on_launch` | Main screen view on launch | 啟動時的主畫面檢視 |
| 211 | `show_folders` | Show folders | 顯示資料夾 |
| 212 | `show_files` | Show files | 顯示檔案 |
| 213 | `show_all_folders` | Show a menu button for toggling Show All Folders Content quickly | 顯示一個選單按鈕，快速切換「顯示所有資料夾內容」 |
| 214 | `thumbnails` | Thumbnails | 縮圖 |
| 215 | `fullscreen_media` | Fullscreen media | 全螢幕媒體檔案 |
| 216 | `extended_details` | Extended details | 詳細資訊 |
| 217 | `bottom_actions` | Bottom actions | 底部操作 |
| 218 | `manage_bottom_actions` | Manage visible bottom actions | 管理要顯示的底部操作 |
| 219 | `toggle_favorite` | Toggle favorite | 切換我的最愛 |
| 220 | `toggle_file_visibility` | Toggle file visibility | 切換檔案可見性 |
| 221 | `failed_to_load_media` | Failed to load media. | 載入媒體失敗。 |
| 222 | `file_is_malformed_or_corrupted` | File is malformed or corrupted. | 檔案格式錯誤或損毀。 |
| 223 | `media_exceeds_device_capabilities` | Media exceeds device capabilities. | 媒體超出裝置的播放能力。 |
| 224 | `unsupported_format` | Unsupported format. | 不支援的格式。 |
| 225 | `video_no_sound` | This video has no audio | 此影片沒有音訊 |
| 226 | `faq_1_title` | How can I make Fossify Gallery the default device gallery? | 怎樣將 Fossify Gallery 設為本裝置的預設相簿？ |
| 227 | `faq_1_text` | First you have to find the currently default gallery in the Apps section of your device settings, look for a button that says something like \"Open by default\", click on it, then select \"Clear defaults\". The next time you will try opening an image or video you should see an app picker, where you can select Fossify Gallery and make it the default app. | 首先，您需要在裝置設定的應用程式部分找到目前預設的相簿，尋找一個寫著「預設開啟」之類的按鈕，點擊它，然後選擇「清除預設」。下次嘗試開啟圖片或影片時，您應該會看到一個應用程式選擇器，您可以在其中選擇 Fossify Gallery 並將其設為預設應用程式。 |
| 228 | `faq_2_title` | I locked the app with a password, but I forgot it. What can I do? | 用密碼把這個應用程式上鎖後忘記密碼該怎麼辦？ |
| 229 | `faq_2_text` | You can solve it in 2 ways. You can either reinstall the app, or find the app in your device settings and select \"Clear data\". It will reset all your settings, it will not remove any media files. | 有兩種解決方法。一是重新安裝本應用程式。一是在裝置的設定中找到本應用程式，然後按「清除資料」（或「清除儲存空間」等類似的字眼），應用程式的設定將會被重設，但不會移除任何媒體檔案。 |
| 230 | `faq_3_title` | How can I make an album always appear at the top? | 如何使某個相簿顯示在最頂端？ |
| 231 | `faq_3_text` | You can long press the desired album and select the Pin icon at the actions menu, that will pin it to the top. You can pin multiple folders too, pinned items will be sorted by the default sorting method. | 長按該相簿，接著按圖釘圖示或選單中的「釘選在頂端」。您可以同時釘選數個相簿，它們將會依照預設排序方式排序。 |
| 232 | `faq_4_title` | How can I fast forward videos? | 如何快轉影片？ |
| 233 | `faq_4_text` | You can do it by double tapping the side of the screen, or tapping the current or max duration texts near the seekbar. If you enable opening videos on a separate screen in the app settings, you can use horizontal gestures too. | 點兩下螢幕邊邊，或者按一下進度條旁邊的目前播放時間／影片時長文字。若您在設定中啟用了「總是用新的水平手勢在獨立畫面開啟影片」，則您也可以使用水平手勢快轉。 |
| 234 | `faq_5_title` | What is the difference between hiding and excluding a folder? | 「隱藏」與「排除」資料夾之間有什麼差別？ |
| 235 | `faq_5_text` | Exclude prevents displaying the folder only in Fossify Gallery, while Hide works system-wise and it hides the folder from other galleries too. It works by creating an empty \".nomedia\" file in the given folder, which you can then remove with any file manager too. Note that some devices do not allow hiding folders like Camera, Screenshots and Downloads. | 排除是指僅在 Fossify Gallery 中不顯示該資料夾，而隱藏則是系統層級的操作，它也會將資料夾隱藏於其他相簿。這是透過在指定資料夾建立一個名為「.nomedia」的空檔案來實現的，您也可以使用任何檔案管理員刪除它。請注意，有些裝置不允許隱藏像是相機、螢幕截圖和下載等資料夾。 |
| 236 | `faq_6_title` | Why do folders with music cover art or stickers show up? | 為何出現了音樂的封面圖或是貼圖的資料夾？ |
| 237 | `faq_6_text` | It can happen that you will see some unusual albums show up. You can easily exclude them by long pressing them and selecting Exclude. In the next dialog you can then select the parent folder, chances are it will prevent the other related albums showing up too. | 有時候會出現一些異常的相簿。長按並點選排除可排除它們，在接下來的對話框選擇它們的上層資料夾的話，有機會避免相關的相簿出現。 |
| 238 | `faq_7_title` | A folder with images isn\'t showing up, or it doesn\'t show all items.  What can I do? | 有個含有圖片的資料夾沒出現，抑或是沒有顯示所有項目時，該怎麼辦？ |
| 239 | `faq_7_text` | That can have multiple reasons, but solving it is easy. Just go in Settings -&gt; Manage Included Folders, select Plus and navigate to the required folder. | 可能有很多種原因，但解法很簡單。前往「設定」 -&gt; 「管理包含資料夾」、按加號（+）、選擇該資料夾即可。 |
| 240 | `faq_8_title` | What if I want just a few particular folders visible? | 如果只想顯示特定資料夾呢？ |
| 241 | `faq_8_text` | Adding a folder at the Included Folders doesn\'t automatically exclude anything. What you can do is go in Settings -&gt; Manage Excluded Folders, exclude the root folder \"/\", then add the desired folders at Settings -&gt; Manage Included Folders. That will make only the selected folders visible, as both excluding and including are recursive and if a folder is both excluded and included, it will show up. | 如果只將某資料夾加入至「包含資料夾」的話，其他的資料夾並不會被自動排除。請前往「設定」-&gt;「管理排除資料夾」、將根資料夾「/」排除，並在「設定」-&gt;「管理包含資料夾」中加入欲顯示的資料夾。 這樣就只會顯示所選的資料夾，因為排除與包含都是遞迴的，所以若某資料夾同時被包含及排除，其將會顯示。 |
| 242 | `faq_10_title` | Can I crop images with this app? | 這個應用程式可以裁剪圖片嗎？ |
| 243 | `faq_10_text` | Yes, you can crop images in the editor, by dragging the image corners. You can get to the editor either by long pressing an image thumbnail and selecting Edit, or selecting Edit from the fullscreen view. | 可以，在編輯工具中拖動圖片的角落即可裁剪圖片。對縮圖長按並選擇編輯，或者在全螢幕檢視時選擇編輯皆可進入編輯工具。 |
| 244 | `faq_11_title` | Can I somehow group media file thumbnails? | 有辦法分類縮圖嗎？ |
| 245 | `faq_11_text` | Sure, just use the \"Group by\" menu item while at the thumbnails view. You can group files by multiple criteria, including Date Taken. If you use the \"Show all folders content\" function you can group them by folders too. | 可以，在檢視縮圖時使用選單中的「分組方式」即可，有多種分類方式可供選擇，包含拍攝日期等。若使用了「資料夾內容全部顯示」功能，則也可以依資料夾來分類。 |
| 246 | `faq_12_title` | Sorting by Date Taken doesn\'t seem to work properly, how can I fix it? | 依照拍攝日期的排序好像排得不太對，要怎麼修正？ |
| 247 | `faq_12_text` | It is most likely caused by the files being copied from somewhere. You can fix it by selecting the file thumbnails and selecting \"Fix Date Taken value\". | 很有可能是因為檔案是從其他地方複製過來的。選擇縮圖並選擇「修復拍攝日期數值」即可修正。 |
| 248 | `faq_13_title` | I see some color banding on the images. How can I improve the quality? | 看到圖片上出現了色帶問題，要如何改善畫質？ |
| 249 | `faq_13_text` | The current solution for displaying images works fine in the vast majority of cases, but if you want even better image quality, you can enable the \"Show images in the highest possible quality\" at the app settings, in the \"Deep zoomable images\" section. | 目前顯示圖片的方式在大多數情況下都能正常運作，但若想要更好的畫質，可以啟用「設定」-&gt;「可強制放大的圖片」段落中的「以最高品質顯示圖片」。 |
| 250 | `faq_14_title` | I have hidden a file/folder. How can I unhide it? | 如何取消隱藏檔案／資料夾？ |
| 251 | `faq_14_text` | You can either press the \"Temporarily show hidden items\" menu item at the main screen, or toggle \"Show hidden items\" in the app settings to see the hidden item. If you want to unhide it, just long press it and select \"Unhide\". Folders are hidden by adding a hidden \".nomedia\" file into them, you can delete the file with any file manager too. Note that hiding works recursively though, so if you hide a folder, all subfolders will become hidden too. So for unhiding the subfolders you have to unhide the parent folder. | 按主畫面選單中的「暫時顯示隱藏的檔案」或設定中的「顯示隱藏的項目」就能看到隱藏的項目。若要取消隱藏，長按並選擇「取消隱藏」即可。資料夾隱藏的原理是在其內新增一個「.nomedia」檔案，因此可用任意檔案管理器將其刪除。檔案／資料夾是以遞迴的方式被隱藏的，因此若某資料夾被隱藏，其子資料夾也會被隱藏。若要取消隱藏子資料夾，其上層資料夾需先被取消隱藏。 |
| 252 | `faq_15_title` | Why does the app take up so much space? | 這個應用程式怎麼佔用了這麼多空間？ |
| 253 | `faq_15_text` | App cache can take up to 250MB, it ensures quicker image loading. If the app is taking up even more space, it is most likely caused by you having items in the recycle bin. Those files count to the app size. You can clear the recycle bin by opening it and deleting all files, or from the app settings. Every file in the bin is deleted automatically after 30 days. | 應用程式快取可協助圖片載入更快，最高僅佔 250 MB。如果此應用程式佔用超過 250 MB 空間，可能是回收桶中的檔案仍在佔用空間。您可刪除回收桶中的所有檔案，或在設定中快速清除。檔案進入回收桶的 30 天後將自動刪除。 |
| 254 | `faq_16_title` | What happened to file and folder hiding and why cannot I see hidden items anymore? | 檔案／資料夾隱藏怎麼了？為什麼我看不到隱藏的項目了？ |
| 255 | `faq_16_text` | Starting with Android 11 you cannot hide or unhide files or folders anymore, you cannot see the hidden ones in gallery apps due to system restrictions either. You will have to use some file manager for that. | 自 Android 11 開始，因系統限制，無法隱藏或解除隱藏檔案或資料夾，也無法在相簿應用程式中看到已隱藏的項目。請使用檔案管理程式達成上述操作。 |
| 256 | `faq_16_text_extra` | Or you can also grant this gallery access to All Files through your device settings, that will allow us showing hidden items and make file operations more reliable in general. | 或者，您也可以透過您的裝置設定授予此相簿存取所有檔案的權限，這將允許我們顯示隱藏項目並使檔案操作更為可靠。 |
| 257 | `faq_17_title` | Why cannot I include missing folders anymore? | 為什麼不能再包含缺漏的資料夾了？ |
| 258 | `faq_17_text` | That stopped working due to the system changes that came with Android 11 too, the app cannot browse real folders anymore, it relies on the so called MediaStore at fetching data. | 因 Android 11 的系統變更，這項功能無法再運作。應用程式沒辦法瀏覽實際的資料夾，而是透過所謂的 MediaStore 來擷取資料。 |
| 259 | `faq_18_title` | Why do I see ads during video playback? | 為什麼我在播放影片時看到廣告？ |
| 260 | `faq_18_text` | Our apps have no ads whatsoever. If you see them during video playback, you must be using some other apps video player. Try finding your default video player in the device settings, then do a \"Clear defaults\" on it. The next time you invoke some video intent you will see an app picker prompt, where you can select what app you want to use. | 我們的應用程式完全沒有廣告。如果您在播放影片時看到它們，那麼您一定是在使用其他應用程式的影片播放器。請試著在裝置設定中找到您的預設影片播放器，並清除其預設值。下次您嘗試播放影片時，系統會提示您選擇應用程式，您可以在此選擇您想要使用的影片播放器。 |
| 261 | `create_virtual_album` | Create virtual album | 建立虛擬相簿 |
| 262 | `new_virtual_album` | New album… | 新增相簿… |
| 263 | `rename_virtual_album` | Rename album | 重新命名相簿 |
| 264 | `album_name` | Album name | 相簿名稱 |
| 265 | `virtual_folder_name` | Virtual folder name | 虛擬資料夾名稱 |
| 266 | `virtual_album_name_empty` | The album name cannot be empty | 相簿名稱不可為空 |
| 267 | `virtual_album_exists` | An album with this name already exists | 已存在相同名稱的相簿 |
| 268 | `add_to_virtual_album` | Add to album | 加入相簿 |
| 269 | `remove_from_virtual_album` | Remove from album | 從相簿中移除 |
| 270 | `added_to_virtual_album` | Added to \"%s\" | 已加入「%s」 |
| 271 | `already_in_virtual_album` | Already in \"%s\" | 已在「%s」中 |
| 272 | `removed_from_virtual_album` | Removed from the album | 已從相簿中移除 |
| 273 | `delete_virtual_album_confirmation` | Delete the album %s? Only the album is removed, your photos and videos stay where they are. | 確定要刪除相簿 %s 嗎？僅會移除相簿本身，您的相片和影片仍會保留在原處。 |
| 274 | `virtual_album_empty` | This album is empty. Select items in any folder and use \"Copy to\". | 此相簿為空。請在任意資料夾中選取項目，並使用「複製」。 |
| 275 | `virtual_album_cannot_be_modified` | This action is not available for virtual albums | 此操作不適用於虛擬相簿 |
| 276 | `alphanumeric_password` | Password | 密碼 |
| 277 | `enter_password` | Enter password | 輸入密碼 |
| 278 | `repeat_password` | Repeat password | 再次輸入密碼 |
| 279 | `wrong_password` | Wrong password | 密碼錯誤 |
| 280 | `encrypting_files` | Encrypting files… | 正在加密檔案… |
| 281 | `encrypting_folder` | Encrypting folder… | 正在加密資料夾… |
| 282 | `folder_encrypted_successfully` | Folder encrypted successfully | 資料夾加密成功 |
| 283 | `file_encrypted_successfully` | File encrypted successfully | 檔案加密成功  |
| 284 | `files_encrypted_successfully` | Files encrypted successfully | 檔案加密成功  |
| 285 | `decrypting_folder` | Decrypting folder… | 正在解密資料夾… |
| 286 | `folder_decrypted_successfully` | Folder decrypted successfully | 資料夾解密成功 |
| 287 | `file_decrypted_successfully` | File decrypted successfully | 檔案解密成功    |
| 288 | `files_decrypted_successfully` | Files decrypted successfully | 檔案解密成功    |
| 289 | `decrypting_files` | Decrypting files… | 正在解密檔案… |
| 290 | `place_finger_prompt` | Please place your finger on the fingerprint sensor | 請將手指放在指紋感應器上 |
| 291 | `connect_at_least_4_dots_prompt` | Connect at least 4 dots | 請至少連接 4 個點 |
| 292 | `access_existing_cipher_secrets_prompt` | Do you want to access your existing Secrets folder? | 您要存取現有的 Secrets 資料夾嗎？ |
| 293 | `delete_and_recreate_cipher_secrets_prompt` | Existing Secrets folder will be deleted and a new one will be created. Are you sure? | 現有的 Secrets 資料夾將會被刪除並建立新的資料夾。確定要繼續嗎？ |
| 294 | `confirm_checkbox_label` | I confirm | 我確認 |
| 295 | `secret_vault` | Encrypted Vault | 加密保險箱 |
| 296 | `secret_vault_empty` | Encrypted Vault is empty | 加密保險箱為空 |
| - | `press_back_again_to_exit_vault` | Press back again to exit | 再按一次返回鍵即可離開 |
| - | `block_vault_screenshots` | Block screenshots in hidden vault | 禁止在隱藏保險箱中擷取螢幕截圖 |
| 297 | `confirm_decrypt_hidden_file` | Are you sure you want to decrypt the hidden file(s)? | 確定要解密隱藏的檔案嗎？ |
| 298 | `copy_to_clipboard_disabled_in_vault` | Copying to clipboard is disabled while vault is unlocked for security. | 基於安全考量，保險箱解鎖期間已停用複製到剪貼簿功能。 |
| 307 | `stop_showing_hidden` | Stop showing encrypted media | 停止顯示已加密媒體 |
| 308 | `system_default` | System default | 系統預設 |
| 309 | `language` | Language | 語言 |
| 310 | `open_system_language_settings` | Open system language settings | 開啟系統語言設定 |
| 311 | `access_hidden_items_with_biometrics` | Access hidden items with biometrics | 使用生物辨識存取隱藏項目 |
| 312 | `enable_hidden_protection_first_prompt` | Please enable hidden item password protection first | 請先啟用隱藏項目的密碼保護 |
| 313 | `biometric_unlock_vault` | Unlock encrypted vault | 解鎖加密保險箱 |
| 314 | `biometric_unlock_prompt` | Verify your biometric identity to access hidden items | 驗證您的生物辨識身分以存取隱藏項目 |
| 315 | `biometric_setup_prompt` | Confirm your biometric identity to enable biometric vault access | 確認您的生物辨識身分以啟用保險箱的生物辨識存取 |
| 316 | `use_password_or_pin` | Use PIN / Password | 使用 PIN 碼 / 密碼 |
| 317 | `biometric_key_invalidated` | Biometric credentials have changed. Please enter your PIN or password. | 生物辨識憑證已變更，請輸入您的 PIN 碼或密碼。 |
| 318 | `no_biometric_hardware_or_enrolled` | No biometric hardware or enrolled credentials found on this device | 此裝置上找不到生物辨識硬體或已註冊的憑證 |
| 319 | `default_hidden_album` | Default Secret Album | *(non-translatable in `values/strings.xml`)* |
| 320 | `select_hidden_album` | Select Encrypted Album | 選擇加密相簿 |
| 321 | `create_new_hidden_album` | Create New Encrypted Album | 建立新加密相簿 |
| 322 | `new_hidden_album` | New encrypted album | 新加密相簿 |
| 323 | `hidden_album_name_empty` | Album name cannot be empty | 相簿名稱不能為空 |
| 324 | `file_hidden_to_album` | File encrypted to \"%s\" | 檔案已加密至「%s」 |
| 325 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d 個檔案已加密至「%2$s」 |
| 326 | `restore_confirmation` (`one`) | Are you sure you want to restore %d file? | 您確定要還原 %d 個檔案嗎？ *(CLDR `other`)* |
| 327 | `restore_confirmation` (`other`) | Are you sure you want to restore %d files? | 您確定要還原 %d 個檔案嗎？ |
| 328 | `failed_to_resize_images` (`one`) | Failed to resize %d image | %d 張圖片尺寸調整失敗 *(CLDR `other`)* |
| 329 | `failed_to_resize_images` (`other`) | Failed to resize %d images | %d 張圖片尺寸調整失敗 |
| 330 | `search` *(fossify-commons)* | *(inherited from commons)* | 搜尋 |
| 331 | `change_date_and_time_format` *(fossify-commons)* | Change date and time format | 更改日期和時間格式 |
| 332 | `password_protect_file_deletion` *(fossify-commons)* | Password protect file deletion and moving | 保護檔案刪除和移動 |
| 333 | `keep_last_modified` *(fossify-commons)* | Keep old last-modified value at file operations | 變更檔案後仍保留原有的最後修改日期 |
| 334 | `clear_cache` *(fossify-commons)* | Clear cache | 清除快取 |
| 335 | `export_settings` *(fossify-commons)* | Export settings | 匯出設定 |
| 336 | `import_settings` *(fossify-commons)* | Import settings | 匯入設定 |
| 337 | `temporarily_show_excluded` *(fossify-commons)* | Temporarily show excluded | 暫停排除項目 |
| 338 | `change_view_type` *(fossify-commons)* | Change view type | 顯示方式 |
| 339 | `contributors` *(fossify-commons)* | Contributors | 貢獻者 |
| 340 | `known_issues` *(fossify-commons)* | Known issues | 已知問題 |
| 341 | `donate_to_fossify` *(fossify-commons)* | Donate to Fossify | 贊助 Fossify |
| 342 | `privacy_policy` *(fossify-commons)* | Privacy policy | 隱私政策 |
| 343 | `create_shortcut` *(fossify-commons)* | Create shortcut | 建立捷徑 |
| 344 | `print` *(fossify-commons)* | Print | 列印 |
| 345 | `search_files` *(fossify-commons)* | Search files | 搜尋檔案 |
| 346 | `search_folders` *(fossify-commons)* | Search folders | 搜尋資料夾 |
| 347 | `pull_down_to_open_vault` | Keep pulling down for Encrypted Vault | 繼續向下拉以開啟加密保險箱 |
| 348 | `keep_holding_to_open_vault` | Keep holding to open Encrypted Vault | 按住不放以開啟加密保險箱 |
| 349 | `release_to_open_vault` | Release to open Encrypted Vault | 放開以開啟加密保險箱 |
| 350 | `manage_excluded_folders` *(fossify-commons)* | Manage excluded folders | 管理排除的資料夾 |
| 351 | `exclude` *(fossify-commons)* | Exclude | 排除 |
| 352 | `exclude_folder` *(fossify-commons)* | Exclude folder | 排除資料夾 |
| 353 | `lock_folder` *(fossify-commons)* | Lock folder | 上鎖資料夾 |
| 354 | `change_cover_image` *(fossify-commons)* | Change cover image | 更換封面圖片 |
| 355 | `select_photo` *(fossify-commons)* | Select photo | 選擇相片 |
| 356 | `use_default` *(fossify-commons)* | Use default | 使用預設 |
| 357 | `column_count` *(fossify-commons)* | Column count | 欄數 |
| 358 | `open_the_recycle_bin` *(fossify-commons)* | Open the recycle bin | 開啟回收桶 |
| 359 | `grid` *(fossify-commons)* | Grid | 格狀 |
| 360 | `list` *(fossify-commons)* | List | 列表 |
| 361 | `resize` *(fossify-commons)* | Resize | 調整尺寸 |
| 362 | `version_placeholder` *(fossify-commons)* | Version %s | 版本 %s |
| 363 | `number_of_items` *(fossify-commons)* | Number of items | 項目數 |
| 364 | `random` *(fossify-commons)* | Random | 隨機 |
| 365 | `shuffle` | Shuffle | 隨機排列 |
| 366 | `stop_showing_excluded` *(fossify-commons)* | Stop showing excluded | 停止顯示已排除的項目 |
| 367 | `copy_to_clipboard` *(fossify-commons)* | Copy to clipboard | 複製到剪貼簿 |
| 368 | `encrypt` | Encrypt | 加密 |
| 369 | `decrypt` | Decrypt | 解密 |
| 370 | `encrypt_album` | Encrypt album | 加密相簿 |
| 371 | `decrypt_album` | Decrypt album | 解密相簿 |
| 372 | `create_new_encrypted_virtual_album` | Create new encrypted virtual album | 建立新的加密虛擬相簿 |
| 373 | `new_album` | New album | 新增相簿 |
| 374 | `filename_without_zip` | Filename (without .zip) | 檔案名稱（不含 .zip） |
| 375 | `favorites_already_exist` | You already have a favorites list | 您已經有一個我的最愛清單 |
| 376 | `delete_existing_list` | Delete existing list | 刪除現有清單 |
| 377 | `merge_lists` | Merge lists | 合併清單 |
| 378 | `confirm` | Confirm | 確認 |
| 379 | `confirm_encrypt_album` | Are you sure you want to encrypt this album? All of its media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | 確定要加密此相簿嗎？其中的所有媒體檔案都會被加密並移至加密保險箱，原始檔案將會被刪除。 |
| 380 | `confirm_encrypt_albums` | Are you sure you want to encrypt the selected albums? All of their media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | 確定要加密所選相簿嗎？其中的所有媒體檔案都會被加密並移至加密保險箱，原始檔案將會被刪除。 |
| 381 | `confirm_encrypt_file` | Are you sure you want to encrypt this file? It will be encrypted and moved to the encrypted vault. The original file will be deleted. | 確定要加密此檔案嗎？它將會被加密並移至加密保險箱，原始檔案將會被刪除。 |
| 382 | `confirm_encrypt_files` | Are you sure you want to encrypt the selected files? They will be encrypted and moved to the encrypted vault. The original files will be deleted. | 確定要加密所選檔案嗎？它們將會被加密並移至加密保險箱，原始檔案將會被刪除。 |
| 383 | `confirm_encrypt_files_to_vault` | The selected files will be encrypted when they are copied into the encrypted vault. Do you want to continue? | 所選檔案複製到加密保險箱時將會被加密。是否要繼續？ |
| 384 | `encrypt_decrypt_notifications` | Encrypt / Decrypt | 加密 / 解密 |
| 385 | `allow_batch_renaming` | Allow batch renaming when multiple files are selected | 選取多個檔案時允許批次重新命名 |
| 386 | `keep_original_remove_from_virtual_album` | Keep the original file, remove it only from this virtual album | 保留原始檔案，僅從此虛擬相簿中移除 |
| 387 | `keep_original_remove_from_favorites` | Keep the original file, remove it only from favorites | 保留原始檔案，僅從我的最愛中移除 |
| 388 | `delete_original_from_favorites_and_virtual_albums` | Delete the original version of a file when it is deleted from favorites and virtual albums | 從我的最愛和虛擬相簿刪除檔案時，一併刪除原始檔案 |
| 389 | `cannot_target_encrypted_album` | Encrypted albums cannot be targeted for this operation | 加密相簿無法作為此操作的目標 |
| 390 | `cannot_mix_encrypted_and_unencrypted` | Cannot combine encrypted and unencrypted items in this operation | 此操作無法同時結合已加密與未加密的項目 |
| 391 | `lock_album` | Lock album | 鎖定相簿 |
| 392 | `unlock_album` | Unlock album | 解鎖相簿 |
| 393 | `auto_backup_prompt_message` | Do you want your favorites and virtual albums to be regularly written to system storage outside of app memory? This will ensure that you do not lose your favorites list and virtual albums if you uninstall the app. You can manually create a backup from app settings at any time. | 是否要定期將您的我的最愛和虛擬相簿寫入應用程式記憶體以外的系統儲存空間？這可確保您在解除安裝應用程式時不會遺失我的最愛清單和虛擬相簿。您隨時可以從應用程式設定手動建立備份。 |
| 394 | `encrypt_info_title` | How Encryption Works | 加密運作方式 |
| 395 | `encrypt_info_message` | Folder & File Structure:\n• When an unencrypted album is encrypted, it is converted into an encrypted subfolder inside the vault (Secrets).\n• Both the folder name and all file names/contents inside it are encrypted, so nothing is readable outside the app.\n\nIf an Album with the Same Name Already Exists:\n• By default, the app asks whether to create a new album such as Album(1) or merge into the existing encrypted album (configurable in Settings).\n• When merging, you can choose whether source files verified 100% bit-identical to the destination are deleted or kept in the source folder.\n• Different files with the same name (e.g. download.jpg) are kept as download(1).jpg or handled according to your conflict resolution setting. | 資料夾與檔案結構：\n• 將未加密的相簿加密時，會轉換為加密保險箱（Secrets）內的加密子資料夾。\n• 資料夾名稱與內部所有檔案名稱及內容皆會加密，因此在應用程式外無法讀取任何內容。\n\n若已存在同名相簿：\n• 預設情況下，應用程式會詢問要建立如 Album(1) 的新相簿，或合併至現有的加密相簿（可在設定中調整）。\n• 合併時，您可選擇要刪除已驗證與目的地 100% 位元完全相同的來源檔案，或將其保留在來源資料夾中。\n• 同名但內容不同的檔案（例如 download.jpg）會保留為 download(1).jpg，或依您的衝突解決設定處理。 |
| 396 | `decrypt_info_title` | How Decryption Works | 解密運作方式 |
| 397 | `decrypt_info_message` | Folder & File Structure:\n• When an encrypted album or file is decrypted, it is restored with its original name and format to the selected destination folder and removed from the encrypted vault.\n\nIf an Album with the Same Name Already Exists:\n• By default, the app asks whether to create a new folder such as Album(1) or merge into the existing destination folder (configurable in Settings).\n• When merging, you can choose whether encrypted copies verified 100% bit-identical to the destination are removed from the vault or kept.\n• Different files with the same name (e.g. download.jpg) are kept as download(1).jpg or handled according to your conflict resolution setting. | 資料夾與檔案結構：\n• 將加密相簿或檔案解密時，會以原始名稱與格式還原至所選的目的地資料夾，並從加密保險箱中移除。\n\n若已存在同名相簿：\n• 預設情況下，應用程式會詢問要建立如 Album(1) 的新資料夾，或合併至現有的目的地資料夾（可在設定中調整）。\n• 合併時，您可選擇要從保險箱移除已驗證與目的地 100% 位元完全相同的加密複本，或將其保留。\n• 同名但內容不同的檔案（例如 download.jpg）會保留為 download(1).jpg，或依您的衝突解決設定處理。 |
| 398 | `vault_location` | Vault location | 保險箱位置 |
| 399 | `vault_location_standard_summary` | Standard (Internal Storage) | 標準（內部儲存空間） |
| 400 | `vault_location_unavailable_summary` | External drive disconnected (%s) | 外接磁碟機已中斷連線 (%s) |
| 401 | `select_vault_location` | Select vault location | 選擇保險箱位置 |
| 402 | `vault_setup_action_title` | Encrypted vault | 加密保險箱 |
| 403 | `create_new_vault_option` | Create new vault | 建立新保險箱 |
| 404 | `use_existing_vault_option` | Use existing vault | 使用現有保險箱 |
| 405 | `where_to_create_vault_title` | Where should the vault be created? | 要在哪裡建立保險箱？ |
| 406 | `vault_location_default_short` | Default Location (Internal > Calypso Gallery > Secrets) | 預設位置（內部 > Calypso Gallery > Secrets） |
| 407 | `vault_location_custom_short` | Custom Location | 自訂位置 |
| 408 | `no_existing_vault_at_location` | No existing encrypted vault (.vault) was found in the selected folder. | 在所選資料夾中找不到現有的加密保險箱 (.vault)。 |
| 409 | `unlock_existing_vault_at_location_prompt` | An existing encrypted vault was found at %s. Do you want to unlock and use it? Tap No to pick a different folder. | 在 %s 找到現有的加密保險箱。是否要解鎖並使用？輕觸「否」可選擇其他資料夾。 |
| 410 | `vault_location_standard_option` | Standard location (Internal Storage > Calypso Gallery > Secrets) | 標準位置（內部儲存空間 > Calypso Gallery > Secrets） |
| 411 | `vault_location_custom_option` | Custom / External location (USB drive, SD card, or custom folder) | 自訂 / 外部位置（USB 磁碟機、SD 卡或自訂資料夾） |
| 412 | `external_vault_unavailable_title` | External vault unavailable | 無法存取外部保險箱 |
| 413 | `external_vault_unavailable_prompt` | Your configured vault location (%s) is currently disconnected or unavailable. Plug in the drive and tap Retry, or choose another vault location. | 您設定的保險箱位置 (%s) 目前已中斷連線或無法使用。請接上磁碟機並輕觸「重試」，或選擇其他保險箱位置。 |
| 414 | `retry` | Retry | 重試 |
| 415 | `change_vault_location` | Change location | 變更位置 |
| 416 | `existing_vault_detected_at_location` | Existing vault found at %s. Enter its password or PIN to unlock it. | 在 %s 找到現有保險箱。請輸入密碼或 PIN 以解鎖。 |
| 417 | `create_new_vault_at_location_prompt` | No vault exists at %s. Do you want to create a new encrypted vault at this location? | %s 尚無保險箱。是否要在此位置建立新的加密保險箱？ |
| 418 | `usb_direct_filesystem_required` | This USB path is only exposed through Android\'s virtual OTG provider and cannot be accessed directly as a filesystem path. Please choose a mounted storage directory or a folder on internal storage / SD card. | 此 USB 路徑僅透過 Android 虛擬 OTG 提供者公開，無法直接作為檔案系統路徑存取。請選擇已掛載的儲存目錄，或內部儲存空間 / SD 卡上的資料夾。 |
| 419 | `vault_location_not_writable` | The selected folder does not exist or is not writable on this device. | 所選資料夾不存在，或無法在此裝置上寫入。 |
| 420 | `vault_location_not_readable` | The selected folder does not exist or is not readable on this device. | 所選資料夾不存在，或無法在此裝置上讀取。 |
| 421 | `sync_vault_from_external` | Import encrypted media files to encrypted section | 將加密的媒體檔案匯入至加密區域 |
| 422 | `sync_vault_select_source_prompt` | How Encrypted Media Import Works:\n\n1. Unified Secrets Folder:\nKeep a folder named Secrets on your external drive (USB / SD). You can either keep a full backup copy of your vault there, or place only your vault\'s .vault header file inside it.\n\n2. Encrypting New Media (PC / Tails CLI):\nThe offline CLI tool automatically saves new encrypted files directly into Secrets. Files placed at the root go to Default Secret Album; files in subfolders go to matching encrypted albums (or create new ones).\n\n3. Bit-by-Bit Smart Sync:\nSelect your external drive or Secrets folder next. The app compares the external drive with your phone vault in RAM:\n• Only new files and albums are imported.\n• Bit-identical files already in your vault are skipped.\n• Different files with the same name (e.g. download.jpg) are safely imported as download(1).jpg. | 加密媒體匯入運作方式：\n\n1. 統一的 Secrets 資料夾：\n在外接磁碟機（USB / SD）上保留名為 Secrets 的資料夾。您可以將保險箱的完整備份複本存放在該處，或僅放入保險箱的 .vault 標頭檔。\n\n2. 加密新媒體（PC / Tails CLI）：\n離線 CLI 工具會自動將新加密的檔案直接儲存至 Secrets。放在根目錄的檔案會進入 Default Secret Album；放在子資料夾中的檔案會進入對應的加密相簿（或建立新相簿）。\n\n3. 逐位元智慧同步：\n接下來請選擇您的外接磁碟機或 Secrets 資料夾。應用程式會在 RAM 中比對外接磁碟機與手機保險箱：\n• 僅匯入新的檔案與相簿。\n• 保險箱中已存在且位元完全相同的檔案將會略過。\n• 同名但內容不同的檔案（例如 download.jpg）會安全地匯入為 download(1).jpg。 |
| 423 | `conflict_handling_mode_label` | File conflict & duplicate resolution | 檔案衝突與重複處理方式 |
| 424 | `conflict_handling_auto_short` | Full automatic (Default — Skip identical, rename different as (1)) | 全自動（預設 — 略過相同檔案，不同檔案重新命名為 (1)） |
| 425 | `conflict_handling_smart_dialog_short` | Smart dialog (Auto-skip identical, ask when content differs) | 智慧對話方塊（自動略過相同檔案，僅在內容不同時詢問） |
| 426 | `album_crypto_conflict_mode_label` | Album conflict (Encrypt / Decrypt) | 相簿衝突（加密 / 解密） |
| 427 | `album_crypto_conflict_ask` | Always ask (Default) | 一律詢問（預設） |
| 428 | `album_crypto_conflict_merge` | Merge into existing album | 合併至現有相簿 |
| 429 | `album_crypto_conflict_merge_delete_identical` | Merge into existing album and automatically delete identical sources | 合併至現有相簿並自動刪除相同來源 |
| 430 | `album_crypto_conflict_rename` | Create new album as (1) | 建立新相簿為 (1) |
| 431 | `album_crypto_conflict_title` | Album Already Exists | 相簿已存在 |
| 432 | `album_crypto_conflict_message` | An album named \"%s\" already exists at the destination. How would you like to proceed? | 目的地已存在名為「%s」的相簿。您要如何處理？ |
| 433 | `album_crypto_conflict_message_multiple` | %1$d selected albums already exist at the destination. How would you like to proceed?\n\nYour choice will apply to all conflicting albums. To choose separately per album, select and process them one by one. | 所選的 %1$d 個相簿已存在於目的地。您要如何處理？\n\n您的選擇將套用至所有衝突的相簿。若要為每個相簿分別選擇，請逐一選取並處理。 |
| 434 | `album_crypto_conflict_rename_option` | Create new album as \"%s\" | 建立新相簿為「%s」 |
| 435 | `album_crypto_conflict_rename_option_multiple` | Create new albums as (1), (2), … | 建立新相簿為 (1)、(2)、… |
| 436 | `album_crypto_conflict_merge_option` | Merge into existing \"%s\" | 合併至現有的「%s」 |
| 437 | `album_crypto_conflict_merge_option_multiple` | Merge into existing albums | 合併至現有相簿 |
| 438 | `album_crypto_delete_identical_source` | Delete identical source files (100% bit-by-bit verified) | 刪除相同的來源檔案（已 100% 逐位元驗證） |
| 439 | `album_crypto_dont_ask_again` | Remember album conflict choice (merge or rename) | 記住相簿衝突選擇（合併或重新命名） |
| 440 | `album_crypto_merge_identical_title` | Identical Files During Merge | 合併期間的相同檔案 |
| 441 | `album_crypto_merge_identical_message` | When merging into \"%1$s\", if any files are already 100%% bit-identical at the destination, should their source copies be deleted or kept? | 合併至「%1$s」時，若目的地已有 100%% 位元完全相同的檔案，要刪除或保留其來源複本？ |
| 442 | `album_crypto_merge_identical_message_multiple` | When merging the %1$d conflicting albums, if any files are already 100%% bit-identical at the destination, should their source copies be deleted or kept? | 合併 %1$d 個衝突相簿時，若目的地已有 100%% 位元完全相同的檔案，要刪除或保留其來源複本？ |
| 443 | `delete_identical_sources` | Delete identical | 刪除相同檔案 |
| 444 | `keep_identical_sources` | Keep in source | 保留於來源 |
| 445 | `fast_large_duplicate_check_label` | Fast duplicate check for large files (>8 MB) | 大型檔案快速重複檢查 (>8 MB) |
| 446 | `fast_large_duplicate_check_hint` | Crucial for transfers containing large files: checks 5 blocks (1.25 MB total) per file instead of reading whole files, speeding up duplicate checks dozens of times. | 對包含大型檔案的傳輸至關重要：每個檔案僅檢查 5 個區塊（共 1.25 MB）而不必讀取整個檔案，可將重複檢查加速數十倍。 |
| 447 | `fast_large_duplicate_check_info_title` | Fast Duplicate Check (>8 MB) | 快速重複檢查 (>8 MB) |
| 448 | `fast_large_duplicate_check_info_message` | Why this matters for large copy/move transfers:\n\nWhen copying or moving large files, such as gigabytes of videos, exact bit-by-bit checking must read both the source and destination files from start to finish whenever filenames and sizes match—taking minutes on fast storage or much longer on SD cards.\n\nHow Fast Check works:\n• Files under 8 MB are ALWAYS checked 100% bit-by-bit.\n• For files over 8 MB, the app compares file size plus five 256 KB blocks distributed across the start, 25%, 50%, 75%, and end of the file (1.25 MB total per side).\n• For a 50 GB archive of 50 MB–1 GB videos, this cuts disk reads by ~40× to ~800×.\n\nWhen to enable or disable:\n• Recommended ON: If most of your gallery consists of large files (>8 MB), two intact, distinct files sharing the same name, exact byte size, and all 5 sampled regions is virtually impossible in practice.\n• Recommended OFF (no speedup): If your gallery consists mostly of photos or files under 8 MB, this setting provides no significant speed gain since files under 8 MB are always checked 100% bit-by-bit.\n• External USB vault sync always stays full Poly1305-AES authenticated. | 為何這對大型複製/移動傳輸很重要：\n\n複製或移動大型檔案（例如數 GB 的影片）時，只要檔名與大小相符，精確逐位元檢查就必須從頭到尾讀取來源與目的地檔案——在高速儲存裝置上需數分鐘，在 SD 卡上則需更久。\n\n快速檢查的運作方式：\n• 小於 8 MB 的檔案一律進行 100% 逐位元檢查。\n• 對於超過 8 MB 的檔案，應用程式會比對檔案大小以及分佈於檔案開頭、25%、50%、75% 與結尾的五個 256 KB 區塊（單側共 1.25 MB）。\n• 對於包含 50 MB–1 GB 影片的 50 GB 封存檔，這可將磁碟讀取減少約 ~40× 至 ~800×。\n\n何時啟用或停用：\n• 建議開啟：若您的圖庫大多為大型檔案 (>8 MB)，兩個未損壞且不同的檔案在實務上幾乎不可能同時擁有相同檔名、精確位元組大小與全部 5 個取樣區段。\n• 建議關閉（無加速效果）：若您的圖庫主要是相片或小於 8 MB 的檔案，由於小於 8 MB 的檔案一律進行 100% 逐位元檢查，此設定不會帶來明顯的速度提升。\n• 外部 USB 保險箱同步一律維持完整的 Poly1305-AES 驗證。 |
| 449 | `conflict_handling_explanation_title` | File Conflict & Duplicate Resolution | 檔案衝突與重複解決 |
| 450 | `conflict_handling_explanation_message` | When copying, moving, encrypting, or decrypting files and albums, if a file with the same name already exists at the destination:\n\n1. Bit-by-Bit Duplicate Check (RAM):\nThe app checks file sizes first, and if sizes match, compares the file contents bit-by-bit in RAM (encrypted files are checked in memory without writing plaintext to disk). During copy/move, identical files are skipped and the original remains untouched in the source folder. When merging an album during encrypt/decrypt, you can choose whether 100% bit-identical source files are deleted or kept.\n\n2. Same Name, Different Content (e.g. two different download.jpg files):\n• Full automatic (Default): Keeps both files without interrupting you by naming the new file download(1).jpg, download(2).jpg.\n• Smart dialog: Still skips bit-identical files silently, and only asks (Skip / Overwrite / Keep both) when two files have the same name but different content. | 複製、移動、加密或解密檔案與相簿時，若目的地已存在同名檔案：\n\n1. 逐位元重複檢查 (RAM)：\n應用程式會先檢查檔案大小，若大小相符，則在 RAM 中逐位元比對檔案內容（加密檔案會在記憶體中檢查，不會將明文寫入磁碟）。複製/移動期間，相同檔案會被略過，原始檔案在來源資料夾中保持不變。在加密/解密期間合併相簿時，您可選擇要刪除或保留 100% 位元完全相同的來源檔案。\n\n2. 同名但內容不同（例如兩個不同的 download.jpg 檔案）：\n• 全自動（預設）：將新檔案命名為 download(1).jpg、download(2).jpg，不中斷您的操作並保留兩個檔案。\n• 智慧對話方塊：仍會靜默略過位元完全相同的檔案，僅在兩個檔案同名但內容不同時詢問（略過 / 覆寫 / 兩者皆保留）。 |
| 451 | `syncing_vault_progress` | Syncing vault from external drive… | 正在從外接磁碟機同步保險箱… |
| 452 | `vault_sync_summary_message` | Vault sync complete:\n• Copied: %1$d\n• Renamed (name conflict): %2$d\n• Skipped (identical): %3$d\n• Failed: %4$d | 保險箱同步完成：\n• 已複製：%1$d\n• 已重新命名（名稱衝突）：%2$d\n• 已略過（相同）：%3$d\n• 失敗：%4$d |
| 453 | `vault_sync_nothing_to_sync` | No pending CLI-encrypted media (.pending_sync) was found in the selected folder. | 在所選資料夾中找不到待處理的 CLI 加密媒體 (.pending_sync)。 |
| 454 | `vault_sync_same_folder_error` | The selected source folder is the same as your active vault. | 所選的來源資料夾與您使用中的保險箱相同。 |
| 455 | `use_for_this_album` | Use for this album only | 僅套用於此相簿 |
| 456 | `crypto_duplicate_source_mode_label` | When a file already exists in destination during encrypt/decrypt | 加密或解密時若目標資料夾已存在相同檔案 |
| 457 | `crypto_duplicate_source_ask` | Always ask (Default) | 每次詢問（預設） |
| 458 | `crypto_duplicate_source_keep` | Keep file in source folder | 保留來源資料夾中的檔案 |
| 459 | `crypto_duplicate_source_delete` | Delete file from source folder | 刪除來源資料夾中的檔案 |
| 460 | `crypto_duplicate_source_title` | File Already Exists in Destination | 目標資料夾已存在相同檔案 |
| 461 | `crypto_duplicate_source_encrypt_message` | Some of the files you are encrypting already exist in the destination folder. What should happen to the files in the source folder? | 您要加密的部分檔案已存在於目標資料夾中。要如何處理來源資料夾中的檔案？ |
| 462 | `crypto_duplicate_source_decrypt_message` | Some of the files you are decrypting already exist in the destination folder. What should happen to the files in the source folder? | 您要解密的部分檔案已存在於目標資料夾中。要如何處理來源資料夾中的檔案？ |
| 463 | `crypto_duplicate_source_encrypt_keep_warning` | Warning: Choosing \"Keep\" for unencrypted files will leave the source file unencrypted on disk. | 警告：若對未加密的檔案選擇「保留」，來源檔案將以未加密狀態繼續留在儲存裝置上。 |
| 464 | `crypto_duplicate_source_always_ask` | Always ask | 每次詢問 |
| 465 | `export_encrypted_vault` | Export encrypted vault | 匯出加密保險箱 |
| 466 | `export_vault_only_header` | Export folder containing only .vault file | 僅匯出包含 .vault 檔案的資料夾 |
| 467 | `export_vault_entire_folder` | Export entire folder | 匯出整個資料夾 |
| 468 | `export_vault_only_header_info` | To add media items encrypted on other devices using the supported CLI tool to the encrypted vault on your phone, you only need to use a folder named Secrets containing the .vault file. | 若要將使用支援的 CLI 工具在其他裝置上加密的媒體項目新增至手機上的加密保險箱，您只需使用一個名為 Secrets 且包含 .vault 檔案的資料夾即可。 |
| 469 | `exporting_vault_progress` | Exporting encrypted vault… | 正在匯出加密保險箱… |
| 470 | `vault_export_same_folder_error` | The export destination cannot be the active vault folder or inside it. | 匯出目的地不可為使用中的保險箱資料夾或其子資料夾。 |
| 471 | `conflict_handling_standard_dialog_summary` | Standard dialog | 標準對話方塊 |
| 472 | `conflict_handling_smart_dialog_summary` | Smart dialog | 智慧對話方塊 |
| 473 | `conflict_handling_auto_summary` | Automatic (Experimental) | 全自動（實驗性） |
| 474 | `conflict_handling_standard_dialog_short` | Standard dialog (Default — Checks filename only, like original Fossify Gallery) | 標準對話方塊（預設 — 僅檢查檔名，與原始 Fossify Gallery 相同） |
| 475 | `skipped_encrypt_files_mode_label` | Manage skipped files during encryption | 管理加密期間略過的檔案 |
| 476 | `skipped_decrypt_files_mode_label` | Manage skipped files during decryption | 管理解密期間略過的檔案 |
| 477 | `skipped_encrypt_files_info_message` | Choose whether files skipped in the source folder during encryption should be kept or deleted for security. This choice also applies to album conflicts during album encryption (see setting below) when the source album is merged into an existing album at the destination. | 選擇加密期間在來源資料夾中略過的檔案要保留還是刪除，以確保安全性。當來源相簿在目的地合併至現有相簿時，此選擇也適用於相簿加密期間的相簿衝突（請參閱下方設定）。 |
| 478 | `skipped_decrypt_files_info_message` | Choose whether files skipped in the source folder during decryption should be kept or deleted. This choice also applies to album conflicts during album decryption (see setting below) when the source album is merged into an existing album at the destination. | 選擇解密期間在來源資料夾中略過的檔案要保留還是刪除。當來源相簿在目的地合併至現有相簿時，此選擇也適用於相簿解密期間的相簿衝突（請參閱下方設定）。 |
| 479 | `skipped_crypto_files_ask` | Always ask (Default) | 一律詢問（預設） |
| 480 | `skipped_crypto_files_keep` | Keep | 保留 |
| 481 | `skipped_crypto_files_delete` | Delete | 刪除 |
| 482 | `skipped_crypto_files_encrypt_message` | The files you skipped were not transferred. For your privacy, you can delete these files. | 您略過的檔案未被轉移。為了您的隱私，可以刪除這些檔案。 |
| 483 | `skipped_crypto_files_decrypt_message` | The files you skipped were not transferred. You can delete these files. | 您略過的檔案未被轉移。您可以刪除這些檔案。 |
| 484 | `skipped_crypto_files_identical_encrypt_message` | Files that already exist in the destination folder were not transferred. For your privacy, you can delete these files. | 目的地資料夾中已存在的檔案未被轉移。為了您的隱私，可以刪除這些檔案。 |
| 485 | `skipped_crypto_files_identical_decrypt_message` | Files that already exist in the destination folder were not transferred. You can delete these files. | 目的地資料夾中已存在的檔案未被轉移。您可以刪除這些檔案。 |
| 486 | `skipped_crypto_files_both_encrypt_message` | The files you skipped and the files that already exist in the destination folder were not transferred. For your privacy, you can delete these files. | 您略過的檔案以及目的地資料夾中已存在的檔案未被轉移。為了您的隱私，可以刪除這些檔案。 |
| 487 | `skipped_crypto_files_both_decrypt_message` | The files you skipped and the files that already exist in the destination folder were not transferred. You can delete these files. | 您略過的檔案以及目的地資料夾中已存在的檔案未被轉移。您可以刪除這些檔案。 |
| 488 | `skipped_crypto_files_always_ask` | Always ask | 一律詢問 |
| 489 | `delete_skipped_files_confirm_title` | Are you sure? | 確定要繼續嗎？ |
| 490 | `delete_skipped_files_confirm_message` | Your files will be deleted! | 您的檔案將會被刪除！ |
| 491 | `date_tie_break_label` | When content is identical but dates differ | 當內容相同但日期不同時 |
| 492 | `date_tie_break_keep_target` | Keep the existing file\'s date (default) | 保留現有檔案的日期（預設） |
| 493 | `date_tie_break_use_source` | Use the source file\'s date | 使用來源檔案的日期 |
| 494 | `date_tie_break_ask` | Always ask | 一律詢問 |
| 495 | `date_tie_break_hint` | Applies only to Smart dialog and Automatic mode. The Standard dialog is not affected. | 僅適用於智慧對話方塊與全自動模式。標準對話方塊不受影響。 |
| 496 | `date_tie_break_ask_info_title` | Why "Always ask" disables Smart dialog and Automatic mode | 為何「一律詢問」會停用智慧對話方塊與全自動模式 |
| 497 | `date_tie_break_ask_info_message` | Smart dialog and Automatic mode exist for one reason: to skip files whose content is already present, so you only see the files that really need your attention — same name, different content. That is exactly the case where comparing thumbnails can reveal bit-rot.\n\nIf you choose "Always ask" here, every identical file with a different date comes back to you as a conflict. In Smart dialog you will see two thumbnails of the very same picture, with nothing to verify. In Automatic mode the identical file is written again as name(1), filling your folders with pointless copies.\n\nUse this option only if you deliberately want to review every date difference, for example to fix a wrong date by hand.\n\nNote: when content is bit-identical, the EXIF capture date is identical too. A date difference means one side lost its EXIF data and fell back to the file modification date (the copy date). | 智慧對話方塊與全自動模式存在的目的只有一個：略過內容已存在的檔案，讓您只看到真正需要留意的檔案 — 名稱相同、內容不同。而這正是比較縮圖能發現位元腐壞的情況。\n\n若您在這裡選擇「一律詢問」，每個日期不同但內容相同的檔案都會再次以衝突的形式呈現。在智慧對話方塊中，您會看到同一張圖片的兩個縮圖，卻沒有任何可驗證的差異。在全自動模式中，相同的檔案會再次寫入為 name(1)，讓資料夾塞滿毫無意義的副本。\n\n請僅在您刻意想逐一檢視每個日期差異時才使用此選項，例如手動修正錯誤的日期。\n\n注意：當內容逐位元完全相同時，EXIF 拍攝日期也會相同。日期不同代表其中一側遺失了 EXIF 資料，轉而使用檔案修改日期（複製日期）。 |
| 498 | `smart_conflict_counter` | %1$s (%2$d / %3$d) | %1$s (%2$d / %3$d) |
| 499 | `smart_conflict_incoming_label` | Incoming (New) | 傳入（新） |
| 500 | `smart_conflict_existing_label` | Existing (Target) | 現有（目標） |
| 501 | `smart_conflict_corrupted_unreadable` | Corrupted or unreadable | 已損毀或無法讀取 |
| 502 | `smart_conflict_integrity_failed` | Integrity check failed (Bit-rot / Corrupted) | 完整性檢查失敗（位元腐壞／損毀） |
| 503 | `smart_conflict_tap_to_preview` | Tap either thumbnail to inspect full-size preview | 輕觸任一縮圖以檢視全尺寸預覽 |
| 504 | `smart_conflict_comparing` | Comparing file contents and dates… | 正在比較檔案內容與日期… |
| 505 | `smart_conflict_content_differs_warning` | File contents differ.\nIf these two files were supposed to be the same, data corruption may have occurred. Keeping both and inspecting them is recommended. | 檔案內容不同。\n若這兩個檔案原本應該相同，可能發生了資料損毀。建議保留兩者並加以檢視。 |
| 506 | `smart_conflict_date_taken_differs` | File contents are identical, creation (date taken) timestamps differ. | 檔案內容相同，但建立（拍攝日期）時間戳記不同。 |
| 507 | `smart_conflict_modified_date_differs` | File contents are identical, modification dates differ. | 檔案內容相同，但修改日期不同。 |
| 508 | `smart_conflict_both_dates_differ` | File contents are identical, creation and modification dates differ. | 檔案內容相同，但建立與修改日期不同。 |
| 509 | `smart_conflict_meta_date_taken` | Taken: %s | 拍攝日期：%s |
| 510 | `smart_conflict_meta_date_modified` | Modified: %s | 修改日期：%s |
| 511 | `album_type_icons` | Album type icons | 相簿類型圖示 |
| 512 | `virtual_folders` | Virtual folders | 虛擬資料夾 |
| 513 | `favorites_album` | Favorites | 我的最愛 |
| 514 | `excluded_folders_album` | Excluded folders | 已排除的資料夾 |
| 515 | `hidden_folders_album` | Hidden folders | 隱藏資料夾 |
| 516 | `temporarily_show_hidden_folders` | Temporarily show hidden folders | 暫時顯示隱藏資料夾 |
| 517 | `stop_showing_hidden_folders` | Stop showing hidden folders | 停止顯示隱藏資料夾 |
| 518 | `unexclude` | Stop excluding | 停止排除 |
| 519 | `hide_folder` | Hide | 隱藏 |
| 520 | `unhide_folder` | Unhide | 取消隱藏 |
| 521 | `about_title` | About Calypso Gallery | 關於 Calypso Gallery |
| 522 | `about_version` | Version %1$s (%2$s) | 版本 %1$s (%2$s) |
| 523 | `about_fork_description` | Calypso Gallery is an independently maintained, unofficial personal fork of Fossify Gallery. It is not affiliated with or endorsed by Fossify. | Calypso Gallery 是由個人獨立維護的非官方 Fossify Gallery 分支。它與 Fossify 無任何關聯，也未獲得 Fossify 的認可。 |
| 524 | `about_modification_notice` | This distribution is a modified version of Fossify Gallery. The fork was created from upstream commit 054228be2da275396eb03d2b9002807b5055afac (1.14.1-5-g054228be2), and all fork-specific modifications are documented in FORK_CHANGELOG.md. | 此發行版本是 Fossify Gallery 的修改版本。本分支建立自上游提交 054228be2da275396eb03d2b9002807b5055afac (1.14.1-5-g054228be2)，所有分支專屬的修改皆記錄於 FORK_CHANGELOG.md。 |
| 525 | `about_license_heading` | License | 授權條款 |
| 526 | `about_gpl_notice` | This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 3 of the License.\n\nThis program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.\n\nYou should have received a copy of the GNU General Public License along with this program. If not, use the license action below or visit https://www.gnu.org/licenses/. | 本程式為自由軟體：您可以依據自由軟體基金會所發布的 GNU General Public License 第 3 版條款，重新散佈及／或修改本程式。\n\n本程式係以「希望能有所用途」之心散佈，但「不提供任何擔保」；亦不隱含對可銷售性或適合特定用途之擔保。詳情請參閱 GNU General Public License。\n\n您應已隨本程式收到一份 GNU General Public License 的副本。若未收到，請使用下方的授權動作，或造訪 https://www.gnu.org/licenses/。 |
| 527 | `about_read_gpl_license` | Read the GNU GPL v3 license | 閱讀 GNU GPL v3 授權條款 |
| 528 | `about_read_tink_license` | Read the Tink Apache 2.0 license and attribution | 閱讀 Tink Apache 2.0 授權條款與歸屬聲明 |
| 529 | `about_source_heading` | Source code | 原始碼 |
| 530 | `about_corresponding_source_notice` | The complete corresponding source for this fork is kept in the fork repository. Tagged releases also have a matching source archive. The repository contains the build scripts, dependency declarations, and complete fork history needed to inspect and rebuild the project. | 本分支完整的對應原始碼保存在分支儲存庫中。已標記的發行版本也提供相對應的原始碼封存檔。該儲存庫包含檢視與重新建置專案所需的建置指令碼、相依性宣告及完整的分支歷史。 |
| 531 | `about_fork_source` | Fork source code and corresponding source | 分支原始碼與對應原始碼 |
| 532 | `about_release_sources` | Release source archives | 發行版本原始碼封存檔 |
| 533 | `about_fork_issues` | Fork issue tracker | 分支問題追蹤器 |
| 534 | `about_upstream_source` | Upstream Fossify Gallery source code | 上游 Fossify Gallery 原始碼 |
| 535 | `about_upstream_project` | Upstream Fossify project | 上游 Fossify 專案 |
| 536 | `about_help_heading` | Help and legal information | 說明與法律資訊 |
| 537 | `about_faq` | Frequently asked questions | 常見問題 |
| 538 | `about_third_party_licenses` | Third-party licenses | 第三方授權條款 |
| 539 | `gpl_license_title` | GNU General Public License v3 | GNU General Public License v3 |
| 540 | `tink_license_title` | Tink license and attribution | Tink 授權與歸屬聲明 |
| 541 | `fork_disclaimer_title` | Fork notice | 分支聲明 |
| 542 | `fork_disclaimer_message` | This application is not developed by the Fossify team. Calypso Gallery is an independently maintained, unofficial fork of Fossify Gallery; the original developer is referenced solely for attribution. It is not affiliated with, endorsed by, or supported by Fossify.\n\nBy continuing you acknowledge this notice. Fork-specific changes are documented in FORK_CHANGELOG.md. | 本應用程式並非由 Fossify 團隊開發。Calypso Gallery 是由個人獨立維護的非官方 Fossify Gallery 分支；提及原始開發者僅供歸屬之用。它與 Fossify 無任何關聯，也未獲得 Fossify 的認可或支援。\n\n繼續使用即表示您知悉此聲明。分支專屬的變更皆記錄於 FORK_CHANGELOG.md。 |
| 543 | `fork_disclaimer_acknowledge` | I understand | 我瞭解 |
| 544 | `vault_upgrade_entry_title` | Encrypted Vault update available | 加密保險箱更新可用 |
| 545 | `vault_upgrade_entry_message` | Your Encrypted Vault was created with an outdated version of the app and uses the old encryption algorithm. If you wish, you can recreate your Encrypted Vault according to the current version\'s encryption algorithm. | 您的加密保險箱是以舊版應用程式建立，並使用舊的加密演算法。若您願意，可以依目前版本的加密演算法重新建立加密保險箱。 |
| 546 | `vault_upgrade_always_show` | Always show | 一律顯示 |
| 547 | `vault_upgrade_settings_title` | Always show vault update warning | 一律顯示保險箱更新警告 |
| 548 | `vault_upgrade_details_title` | Before updating | 更新前 |
| 549 | `vault_upgrade_details_message` | It is recommended to back up before updating the Encrypted Vault to use the current encryption algorithm. This way, the risk of data loss due to an interrupted update is eliminated and, if you wish, you can continue using the un-updated Encrypted Vault in older versions of the app. | 建議先備份，再更新加密保險箱以使用目前的加密演算法。如此一來，即可消除因更新中斷而導致資料遺失的風險；若您願意，也可以在舊版應用程式中繼續使用未更新的加密保險箱。 |
| 550 | `vault_upgrade_low_battery` | Your battery is low, please charge your device to at least 20%. | 您的電量偏低，請將裝置充電至至少 20%。 |
| 551 | `vault_upgrade_low_space` | Not enough free space to update the Encrypted Vault. | 可用空間不足，無法更新加密保險箱。 |
| 552 | `vault_upgrade_continue` | Continue | 繼續 |
| 553 | `vault_upgrade_progress` | Converted %1$d of %2$d files | 已轉換 %1$d / %2$d 個檔案 |
| 554 | `vault_upgrade_finished` | Update complete. Reopen the Encrypted Vault to activate the new keyring. | 更新完成。請重新開啟加密保險箱以啟用新的金鑰環。 |
| 555 | `vault_upgrade_failed_count` | Update stopped: %1$d file(s) could not be verified. | 更新已停止：有 %1$d 個檔案無法驗證。 |
| 556 | `vault_upgrade_resume_title` | Vault update was interrupted | 保險箱更新已中斷 |
| 557 | `vault_upgrade_resume_message` | The Encrypted Vault update was interrupted. Some files in your Encrypted Vault were updated to the new encryption algorithm. No data was lost. Do you want to continue updating the remaining files? | 加密保險箱更新已中斷。加密保險箱中的部分檔案已更新為新的加密演算法。沒有任何資料遺失。您要繼續更新其餘的檔案嗎？ |
| 558 | `vault_upgrade_progress_warning` | Please do not exit the app or turn off the screen during the update. | 更新期間請勿離開應用程式或關閉螢幕。 |
| 559 | `vault_upgrade_waiting` | Encrypted Vault is disconnected. Waiting for it to reconnect... | 加密保險箱已中斷連線。正在等待重新連線... |
| 560 | `argon2_profile_title` | Brute-Force resistance | 暴力破解防護 |
| 561 | `brute_force_mode_title` | Calibration mode | 校正模式 |
| 562 | `brute_force_mode_time` | Via vault unlock time | 透過保險箱解鎖時間 |
| 563 | `brute_force_mode_presets` | Via ready-made modes | 透過現成模式 |
| 564 | `brute_force_time_title` | Target vault unlock time | 目標保險箱解鎖時間 |
| 565 | `brute_force_time_hint` | Seconds (2.0–7.0). The app measures this device once and stores the resulting KDF parameters in the vault; real unlock time varies on other devices. | 秒數（2.0–7.0）。應用程式會對此裝置進行一次測量，並將產生的 KDF 參數儲存在保險箱中；實際解鎖時間在其他裝置上會有所不同。 |
| 566 | `brute_force_time_value` | %1$.1f s | %1$.1f 秒 |
| 567 | `brute_force_actual_params` | Stored: %1$d MiB / %2$d rounds | 已儲存：%1$d MiB / %2$d 回合 |
| 568 | `brute_force_clamped` |  (clamped) |  （已限制） |
| 569 | `argon2_profile_low` | Low-end device (not recommended) | 低階裝置（不建議） |
| 570 | `argon2_profile_default` | Default | 預設 |
| 571 | `argon2_profile_high` | High security | 高安全性 |
| 572 | `argon2_profile_hint` | If you don\'t know what you\'re doing, it is recommended to keep the default setting. This only affects newly created vaults and password changes. | 若您不清楚自己在做什麼，建議維持預設設定。這只會影響新建立的保險箱與密碼變更。 |
| 573 | `argon2_profile_current` | Current: %s | 目前：%s |

# Japanese (values-ja) Translation Completion & English 1-to-1 Comparison

## New fork strings - bottom actions, view switch, biometrics and vault UI (16 keys)

Added to `values-ja/strings.xml`. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `show_bottom_action_names` | Show action names in the bottom action bar | 下部アクションバーにアクション名を表示 | Fullscreen media setting, default ON |
| 2 | `show_albums` | Show albums | アルバムを表示 | Files-view overflow item that returns to the album view |
| 3 | `vault_biometric_offer_title` | Biometric access to the vault | 保管庫の生体認証アクセス | One-time offer after first vault creation |
| 4 | `vault_biometric_offer_message` | Open the vault with your fingerprint or face instead of a password. You don\'t need to enter your password again. | パスワードの代わりに指紋や顔で保管庫を開けます。パスワードの再入力は不要です。 | Same |
| 5 | `vault_biometric_offer_accept` | Set up biometric access | 生体認証を設定 | Positive button |
| 6 | `vault_biometric_offer_decline` | Not now | 今はしない | Negative button |
| 7 | `password_strength_bad` | Very weak | 非常に弱い | Vault password strength |
| 8 | `password_strength_poor` | Weak | 弱い | Vault password strength |
| 9 | `password_strength_weak` | Fair | 普通 | Vault password strength |
| 10 | `password_strength_good` | Strong | 強い | Vault password strength |
| 11 | `password_strength_excellent` | Very strong | 非常に強い | Vault password strength |
| 12 | `password_strength_hint` | A longer password with mixed characters is harder to crack. | 長く様々な文字を含むパスワードほど解読が困難です。 | Shown before typing |
| 13 | `vault_unlock_time_unit` | s | 秒 | Localized seconds unit |
| 14 | `search_in_storage` | Search in storage | ストレージを検索 | Second option in unencrypted copy destination picker |
| 15 | `password_must_be_at_least_4_characters` | Password must be at least 4 characters long | パスワードは4文字以上で入力してください | Alphanumeric minimum length |
| 16 | `biometric_disable_confirmation` | Confirm your biometric identity to disable biometric vault access | 保管庫への生体認証アクセスを無効にするには生体認証で本人確認を行ってください | Shown in biometric confirmation prompt |

## ADR 0003 — resumable file operations (7 new keys)

Added to `values-ja/strings.xml`. Placeholders (`%1$s`, `%2$s`, `%1$d`) are preserved exactly.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `space_unverified_warning` | Free space could not be verified (%2$s). About %1$s is required; the operation will be attempted and may stop if space runs out. | 空き領域を確認できませんでした（%2$s）。約 %1$s が必要です。操作を試行しますが、領域が不足すると途中で停止する場合があります。 | SAF/OTG free-space warning; `%1$s` = required size, `%2$s` = reason |
| 2 | `resume_operations_title` | Interrupted operations | 中断された操作 | One-time process-death dialog title |
| 3 | `resume_operations_message` | %1$d operation(s) were interrupted. No data was lost — continue where they stopped? | %1$d 件の操作が中断されました。データの損失はありません。中断した場所から続けますか？ | `%1$d` = interrupted operation count; Japanese uses only `other` plural |
| 4 | `resume_continue` | Continue | 続ける | Positive dialog button |
| 5 | `resume_later` | Not now | 後で | Negative dialog button |
| 6 | `resume_done` | Operations completed. | 操作が完了しました。 | Success toast after resume |
| 7 | `resume_failed` | Some operations could not be completed; source files were preserved. | 一部の操作を完了できませんでした。元のファイルは保持されています。 | Failure toast; no data loss |
| 8 | `space_unverified_title` | Free space unverified | 空き領域を確認できません | Explicit-consent dialog title for SAF/OTG free space |
| 9 | `space_continue_anyway` | Continue anyway | それでも続ける | Positive button: proceed without a verified space reading |

> **Scope:** `app/src/main/res/values-ja/strings.xml` was audited against `app/src/main/res/values/strings.xml`. All 65 missing strings were translated into Japanese and added, bringing `values-ja` to full parity (347 `<string>` + 2 `<plurals>` = 349 resource entries; 372 total comparison rows including English `one`/`other` plural forms and 29 `fossify-commons` UI keys). Nine keys that were missing from `values-ja/strings.xml` were translated and added (`number_of_items`, `random`, `shuffle`, `stop_showing_excluded`, `copy_to_clipboard`, `encrypt`, `decrypt`, `encrypt_album`, `decrypt_album`), and eleven existing entries were revised so that files and albums use the new \"encrypt / decrypt\" wording and so that the favorites import/export labels mention virtual albums as well. Subsequently, 22 additional keys were localized in `values-ja/strings.xml`, `hide_folder_description` was removed, and `default_hidden_album` was marked `translatable="false"` (`Default Secret Album`), bringing `values-ja/strings.xml` to 373 `<string>` + 2 `<plurals>` = 375 resource entries and 393 total comparison rows.
> **Key Terminology:** `Settings` → `設定` · `Default` → `既定` / `デフォルト` · `Folder` → `フォルダー` · `Album` → `アルバム` · `Encrypted Vault` → `暗号化された保管庫` · `Hidden` → `非表示` · `Excluded` → `除外` · `Password` → `パスワード` · `Alphanumeric` → `英数字` · `Encrypt / Decrypt` → `暗号化 / 復号` · `Clipboard` → `クリップボード`

## 1. Summary of Newly Added Japanese Strings & Translations

### Newly Added Fork-Only Strings (66 Keys)

These 66 strings were introduced by this fork (Calypso Gallery) and do not exist in the original Fossify Gallery:

|   # | Key (`name`)                                | English (`values/strings.xml`)                                                                                                                                                                                                                                                                                                                                      | Japanese (`values-ja/strings.xml`)                                                                                                                                                                                       | Notes                                                                              |
| --: | ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------- |
| 1 | `video_double_tap_duration`                 | Double-tap seek duration                                                                                                                                                                                                                                                                                                                                            | ダブルタップ時のシーク時間                                                                                                                                                                                                            | Video settings: double-tap forward/rewind seek interval                            |
| 2 | `video_seek_duration_seconds`               | %d seconds                                                                                                                                                                                                                                                                                                                                                          | %d秒                                                                                                                                                                                                                      | Used with durations 3, 5, 10, 15 via String.format                                 |
| 3 | `create_virtual_album`                      | Create virtual album                                                                                                                                                                                                                                                                                                                                                | 仮想アルバムを作成                                                                                                                                                                                                                | Virtual albums section action                                                      |
| 4 | `new_virtual_album`                         | New album…                                                                                                                                                                                                                                                                                                                                                          | 新しいアルバム…                                                                                                                                                                                                                 | Action label for creating a new virtual album                                      |
| 5 | `rename_virtual_album`                      | Rename album                                                                                                                                                                                                                                                                                                                                                        | アルバム名を変更                                                                                                                                                                                                                 | Action label to rename a virtual album                                             |
| 6 | `album_name`                                | Album name                                                                                                                                                                                                                                                                                                                                                          | アルバム名                                                                                                                                                                                                                    | Input hint / label for album name                                                  |
| 7 | `virtual_folder_name`                       | Virtual album name                                                                                                                                                                                                                                                                                                                                                 | 仮想アルバム名                                                                                                                                                                                                                 | Input hint / label for virtual album                                              |
| 8 | `virtual_album_name_empty`                  | The album name cannot be empty                                                                                                                                                                                                                                                                                                                                      | アルバム名は空にできません                                                                                                                                                                                                            | Validation error when album name is empty                                          |
| 9 | `virtual_album_exists`                      | An album with this name already exists                                                                                                                                                                                                                                                                                                                              | この名前のアルバムはすでに存在します                                                                                                                                                                                                       | Validation error when album name already exists                                    |
| 10 | `add_to_virtual_album`                      | Add to virtual album                                                                                                                                                                                                                                                                                                                                                | 仮想アルバムに追加                                                                                                                                                                                                                | Menu action to add items to a virtual album                                        |
| 11 | `remove_from_virtual_album`                 | Remove from virtual album                                                                                                                                                                                                                                                                                                                                           | 仮想アルバムから削除                                                                                                                                                                                                              | Menu action to remove items from a virtual album                                   |
| 12 | `added_to_virtual_album`                    | Added to \"%s\"                                                                                                                                                                                                                                                                                                                                                     | 「%s」に追加しました                                                                                                                                                                                                              | Toast message using Japanese corner brackets 「%s」                                  |
| 13 | `already_in_virtual_album`                  | Already in \"%s\"                                                                                                                                                                                                                                                                                                                                                   | すでに「%s」に含まれています                                                                                                                                                                                                          | Toast message using Japanese corner brackets 「%s」                                  |
| 14 | `removed_from_virtual_album`                | Removed from the album                                                                                                                                                                                                                                                                                                                                              | アルバムから削除しました                                                                                                                                                                                                             | Toast message after removing items from an album                                   |
| 15 | `delete_virtual_album_confirmation`         | Delete the album %s? Only the album is removed, your photos and videos stay where they are.                                                                                                                                                                                                                                                                         | アルバム %s を削除しますか？アルバムのみが削除され、写真や動画はそのまま残ります。                                                                                                                                                                              | No extra quotes around %s (code passes quoted name or plural count)                |
| 16 | `virtual_album_empty`                       | This album is empty. Select items in any folder and use \"Add to virtual album\".                                                                                                                                                                                                                                                                                   | このアルバムは空です。任意のフォルダーで項目を選択し、「仮想アルバムに追加」を使用してください。                                                                                                                                                                  | Empty state placeholder referencing Add to virtual album (「仮想アルバムに追加」)         |
| 17 | `virtual_album_cannot_be_modified`          | This action is not available for virtual albums                                                                                                                                                                                                                                                                                                                     | この操作は仮想アルバムでは利用できません                                                                                                                                                                                                     | Toast message when action is unsupported on virtual albums                         |
| 18 | `alphanumeric_password`                     | Alphanumeric                                                                                                                                                                                                                                                                                                                                                            | 英数字                                                                                                                                                                                                                    | Alphanumeric input label                                                               |
| 19 | `repeat_password`                           | Repeat password                                                                                                                                                                                                                                                                                                                                                     | パスワードを再入力                                                                                                                                                                                                                | Password confirmation prompt                                                       |
| 20 | `wrong_password`                            | Wrong password                                                                                                                                                                                                                                                                                                                                                      | パスワードが間違っています                                                                                                                                                                                                            | Password validation error                                                          |
| 21 | `encrypting_files`                          | Encrypting files…                                                                                                                                                                                                                                                                                                                                                   | ファイルを暗号化しています…                                                                                                                                                                                                           | Progress message using 〜しています… style                                               |
| 22 | `encrypting_folder`                         | Encrypting folder…                                                                                                                                                                                                                                                                                                                                                  | フォルダーを暗号化しています…                                                                                                                                                                                                          | Progress message using 〜しています… style                                               |
| 23 | `folder_encrypted_successfully`             | Folder encrypted successfully                                                                                                                                                                                                                                                                                                                                       | フォルダーを暗号化しました                                                                                                                                                                                                            | Success message using 〜しました style                                                  |
| 24 | `decrypting_folder`                         | Decrypting folder…                                                                                                                                                                                                                                                                                                                                                  | フォルダーを復号しています…                                                                                                                                                                                                           | Progress message using 〜しています… style                                               |
| 25 | `folder_decrypted_successfully`             | Folder decrypted successfully                                                                                                                                                                                                                                                                                                                                       | フォルダーを復号しました                                                                                                                                                                                                             | Success message using 〜しました style                                                  |
| 26 | `decrypting_files`                          | Decrypting files…                                                                                                                                                                                                                                                                                                                                                   | ファイルを復号しています…                                                                                                                                                                                                            | Progress message using 〜しています… style                                               |
| 27 | `place_finger_prompt`                       | Please place your finger on the fingerprint sensor                                                                                                                                                                                                                                                                                                                  | 指紋センサーに指を置いてください                                                                                                                                                                                                         | Fingerprint authentication prompt                                                  |
| 28 | `connect_at_least_4_dots_prompt`            | Connect at least 4 dots                                                                                                                                                                                                                                                                                                                                             | 4つ以上の点をつなげてください                                                                                                                                                                                                          | Pattern lock validation prompt                                                     |
| 29 | `access_existing_secrets_prompt`     | Do you want to access your existing Secrets folder?                                                                                                                                                                                                                                                                                                          | 既存の Secrets フォルダーにアクセスしますか？                                                                                                                                                                                       | Keeps literal folder name Secrets                                           |
| 30 | `delete_and_recreate_secrets_prompt` | Existing Secrets folder will be deleted and a new one will be created. Are you sure?                                                                                                                                                                                                                                                                         | 既存の Secrets フォルダーは削除され、新しく作成されます。よろしいですか？                                                                                                                                                                         | Keeps literal folder name Secrets                                           |
| 31 | `confirm_checkbox_label`                    | I confirm                                                                                                                                                                                                                                                                                                                                                           | 確認しました                                                                                                                                                                                                                   | Checkbox label, suffixed with countdown (10) in dialog                             |
| 32 | `secret_vault`                              | Encrypted Vault                                                                                                                                                                                                                                                                                                                                                     | 暗号化された保管庫                                                                                                                                                                                                                | Vault feature title                                                                |
| 33 | `secret_vault_empty`                        | Encrypted Vault is empty                                                                                                                                                                                                                                                                                                                                            | 暗号化された保管庫は空です                                                                                                                                                                                                            | Empty state placeholder for Encrypted Vault                                           |
| - | `press_back_again_to_exit_vault` | Press back again to exit | 終了するにはもう一度戻るボタンを押してください | Toast shown on first back press in hidden vault |
| - | `block_vault_screenshots` | Block screenshots in encrypted vault | 暗号化保管庫でのスクリーンショットを禁止する | Settings switch (default ON) blocking screenshots in encrypted vault |
| 34 | `confirm_decrypt_hidden_file`               | Are you sure you want to decrypt the hidden file(s)?                                                                                                                                                                                                                                                                                                                | 非表示ファイルを復号してもよろしいですか？                                                                                                                                                                                                    | Confirmation prompt for decrypting hidden file(s)                                  |
| 35 | `copy_to_clipboard_disabled_in_vault`       | Copying to clipboard is disabled while vault is unlocked for security.                                                                                                                                                                                                                                                                                              | セキュリティのため、保管庫のロック解除中はクリップボードへのコピーが無効になっています。                                                                                                                                                                             | Security notice while vault is unlocked                                            |
| 36 | `open_system_language_settings`             | Open system language settings                                                                                                                                                                                                                                                                                                                                       | システムの言語設定を開く                                                                                                                                                                                                             | Neutral dialog button in language picker                                           |
| 37 | `file_encrypted_successfully`               | File encrypted successfully                                                                                                                                                                                                                                                                                                                                         | ファイルを暗号化しました                                                                                                                                                                                                             | Success toast after encrypting a single file                                |
| 38 | `files_encrypted_successfully`              | Files encrypted successfully                                                                                                                                                                                                                                                                                                                                        | ファイルを暗号化しました                                                                                                                                                                                                             | Success toast after encrypting multiple files                               |
| 39 | `file_decrypted_successfully`               | File decrypted successfully                                                                                                                                                                                                                                                                                                                                         | ファイルを復号しました                                                                                                                                                                                                              | Success toast after decrypting a single file                              |
| 40 | `files_decrypted_successfully`              | Files decrypted successfully                                                                                                                                                                                                                                                                                                                                        | ファイルを復号しました                                                                                                                                                                                                              | Success toast after decrypting multiple files                             |
| 41 | `access_hidden_items_with_biometrics`       | Access hidden items with biometrics                                                                                                                                                                                                                                                                                                                                 | 生体認証で非表示項目にアクセス                                                                                                                                                                                                          | Settings switch, setup prompt title, and vault security dialog button              |
| 42 | `enable_hidden_protection_first_prompt`     | Please enable hidden item password protection first                                                                                                                                                                                                                                                                                                                 | 先に非表示項目のパスワード保護を有効にしてください                                                                                                                                                                                                | Toast shown when enabling biometrics before hidden-item password protection        |
| 43 | `biometric_unlock_vault`                    | Unlock encrypted vault                                                                                                                                                                                                                                                                                                                                                 | 暗号化された保管庫をロック解除                                                                                                                                                                                                            | BiometricPrompt title when unlocking the encrypted vault                              |
| 44 | `biometric_unlock_prompt`                   | Verify your biometric identity to access hidden items                                                                                                                                                                                                                                                                                                               | 非表示項目にアクセスするには生体認証で本人確認を行ってください                                                                                                                                                                                          | BiometricPrompt subtitle when unlocking hidden items                               |
| 45 | `biometric_setup_prompt`                    | Confirm your biometric identity to enable biometric vault access                                                                                                                                                                                                                                                                                                    | 保管庫への生体認証アクセスを有効にするには生体認証で本人確認を行ってください                                                                                                                                                                                   | BiometricPrompt subtitle when enabling biometric vault access                      |
| 46 | `use_password_or_pin`                       | Use PIN / Password                                                                                                                                                                                                                                                                                                                                                  | PIN / パスワードを使用                                                                                                                                                                                                           | BiometricPrompt negative button to fall back to PIN/password                       |
| 47 | `biometric_key_invalidated`                 | Biometric credentials have changed. Please enter your PIN or password.                                                                                                                                                                                                                                                                                              | 生体認証情報が変更されました。PIN またはパスワードを入力してください。                                                                                                                                                                                    | Toast shown when biometric credentials changed and Keystore key invalidated        |
| 48 | `no_biometric_hardware_or_enrolled`         | No biometric hardware or enrolled credentials found on this device                                                                                                                                                                                                                                                                                                  | このデバイスに生体認証ハードウェアまたは登録済みの認証情報が見つかりません                                                                                                                                                                                    | Toast shown when no biometric hardware or enrolled credentials exist               |
| 49 | `default_hidden_album`                      | Default Secret Album | デフォルトの非表示アルバム                                                                                                                                                                                                            | Settings / picker label for default hidden album                                   |
| 50 | `select_hidden_album`                       | Select Encrypted Album                                                                                                                                                                                                                                                                                                                                              | 暗号化アルバムを選択                                                                                                                                                                                                               | Dialog title for choosing the target encrypted album                                      |
| 51 | `create_new_hidden_album`                   | Create New Encrypted Album                                                                                                                                                                                                                                                                                                                                          | 新しい暗号化アルバムを作成                                                                                                                                                                                                            | Action label to create a new encrypted album                                          |
| 52 | `new_hidden_album`                          | New encrypted album                                                                                                                                                                                                                                                                                                                                                 | 新しい暗号化アルバム                                                                                                                                                                                                               | Input hint / default label for a new encrypted album                                    |
| 53 | `hidden_album_name_empty`                   | Album name cannot be empty                                                                                                                                                                                                                                                                                                                                          | アルバム名を空にすることはできません                                                                                                                                                                                                       | Validation error when hidden album name is blank                                   |
| 54 | `file_hidden_to_album`                      | File encrypted to \"%s\"                                                                                                                                                                                                                                                                                                                                            | ファイルを「%s」に暗号化しました                                                                                                                                                                                                        | Success toast after encrypting a single file into album `%s`                           |
| 55 | `files_hidden_to_album`                     | %1$d files encrypted to \"%2$s\"                                                                                                                                                                                                                                                                                                                                    | %1$d 個のファイルを「%2$s」に暗号化しました                                                                                                                                                                                               | Success toast after encrypting `%1$d` files into album `%2$s`                          |
| 56 | `pull_down_to_open_vault` | Keep pulling down for Encrypted Vault | 暗号化された保管庫を開くには引き下げ続けてください | Vault pull gesture hint shown while the pull is below the open threshold |
| 57 | `keep_holding_to_open_vault` | Keep holding to open Encrypted Vault | 暗号化された保管庫を開くには押し続けてください | Vault pull gesture hint shown once the pull passes the threshold, before the 2.5 s hold completes |
| 58 | `release_to_open_vault` | Release to open Encrypted Vault | 指を離して暗号化された保管庫を開く | Vault pull gesture hint shown after holding the pull for 2.5 s; releasing opens the vault |

### Newly Added Translations for Strings Already Present in the Original Fossify Gallery (18 Keys)

These 18 strings already exist in the original Fossify Gallery (upstream); only the translations below were newly added in this fork:

|   # | Key (`name`)                                | English (`values/strings.xml`)                                                                                                                                                                                                                                                                                                                                      | Japanese (`values-ja/strings.xml`)                                                                                                                                                                                       | Notes                                                                              |
| --: | ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------- |
| 1 | `force_landscape_reverse`                   | Force landscape (reverse)                                                                                                                                                                                                                                                                                                                                           | 横で固定 (逆向き)                                                                                                                                                                                                               | Display rotation setting: reverse landscape orientation                            |
| 2 | `excluded_activity_placeholder`             | Excluding folders will make them together with their subfolders hidden just in Calypso Gallery, they will still be visible in other applications.\n\nIf you want to hide them from other apps too, use the Encrypt function. | フォルダーを除外すると、そのフォルダーとサブフォルダーはFossifyギャラリー上でのみ非表示になり、他のアプリでは引き続き表示されます。\n\n他のアプリからも非表示にしたい場合は、「非表示」機能を使用してください。                                                                                                            | Placeholder explanation on Excluded Folders screen; preserves \n\n paragraph break |
| 3 | `render_ultra_hdr_photos`                   | Show Ultra HDR photos in HDR                                                                                                                                                                                                                                                                                                                                        | Ultra HDR写真をHDRで表示                                                                                                                                                                                                       | Media settings: render Ultra HDR photos in HDR                                     |
| 4 | `keep_screen_on_fullscreen_photos`          | Keep the screen on while viewing fullscreen photos                                                                                                                                                                                                                                                                                                                  | 写真の全画面表示中は画面をオンのままにする                                                                                                                                                                                                    | Fullscreen media setting: keep screen awake                                        |
| 5 | `playback_speed`                            | Playback speed                                                                                                                                                                                                                                                                                                                                                      | 再生速度                                                                                                                                                                                                                     | Video player playback speed control                                                |
| 6 | `on_video_tap`                              | On video tap                                                                                                                                                                                                                                                                                                                                                        | 動画をタップしたとき                                                                                                                                                                                                               | Setting label for video tap action                                                 |
| 7 | `open_in_app_player`                        | Open in-app player                                                                                                                                                                                                                                                                                                                                                  | アプリ内プレーヤーを開く                                                                                                                                                                                                             | Radio option: open video in built-in player                                        |
| 8 | `open_system_default_player`                | Open in another app                                                                                                                                                                                                                                                                                                                                                  | 別のアプリで開く                                                                                                                                                                                                        | Radio option: open video in default external player                                |
| 9 | `file_is_malformed_or_corrupted`            | File is malformed or corrupted.                                                                                                                                                                                                                                                                                                                                     | ファイルが不正または破損しています。                                                                                                                                                                                                       | Media player error message                                                         |
| 10 | `media_exceeds_device_capabilities`         | Media exceeds device capabilities.                                                                                                                                                                                                                                                                                                                                  | メディアが端末の性能を超えています。                                                                                                                                                                                                       | Media player error message; uses 端末 for device                                     |
| 11 | `unsupported_format`                        | Unsupported format.                                                                                                                                                                                                                                                                                                                                                 | サポートされていない形式です。                                                                                                                                                                                                          | Media player error message                                                         |
| 12 | `video_no_sound`                            | This video has no audio                                                                                                                                                                                                                                                                                                                                             | この動画には音声がありません                                                                                                                                                                                                           | Video player notice when track has no audio                                        |
| 13 | `faq_1_title`                               | How can I make Calypso Gallery the default device gallery? | Fossifyギャラリーを端末のデフォルトのギャラリーにするにはどうすればよいですか？                                                                                                                                                                              | FAQ 1 question; uses Fossifyギャラリー consistently with existing strings               |
| 14 | `faq_1_text`                                | First you have to find the currently default gallery in the Apps section of your device settings, look for a button that says something like \"Open by default\", click on it, then select \"Clear defaults\". The next time you will try opening an image or video you should see an app picker, where you can select Fossify Gallery and make it the default app. | まず、端末の「設定」の「アプリ」セクションで現在デフォルトに設定されているギャラリーを見つけ、「デフォルトで開く」などのボタンをタップして「デフォルト設定をクリア」を選択します。次回、画像や動画を開こうとした際にアプリ選択画面が表示されるので、そこでFossifyギャラリーを選択してデフォルトのアプリに設定してください。                                                        | FAQ 1 answer; matches existing Android settings phrasing in values-ja              |
| 15 | `faq_5_text`                                | Exclude prevents displaying the folder only in Calypso Gallery, while Hide works system-wise and it hides the folder from other galleries too. It works by creating an empty \".nomedia\" file in the given folder, which you can then remove with any file manager too. Note that some devices do not allow hiding folders like Camera, Screenshots and Downloads. | 「除外」はFossifyギャラリー内でのみフォルダーを表示しないようにするのに対し、「非表示」はシステム全体で機能し、他のギャラリーアプリからもフォルダーを隠します。これは指定したフォルダー内に空の「.nomedia」ファイルを作成することで動作し、任意のファイルマネージャーで削除することもできます。なお、一部の端末では Camera、Screenshots、Downloads などのフォルダーを非表示にできない場合があります。 | FAQ 5 answer; keeps folder names Camera, Screenshots, Downloads literal            |
| 16 | `enter_password`                            | Enter password                                                                                                                                                                                                                                                                                                                                                      | パスワードを入力                                                                                                                                                                                                                 | Password prompt                                                                    |
| 17 | `system_default`                            | System default                                                                                                                                                                                                                                                                                                                                                      | システムのデフォルト                                                                                                                                                                                                               | Matches standard Android Japanese system default phrasing                          |
| 18 | `language`                                  | Language                                                                                                                                                                                                                                                                                                                                                            | 言語                                                                                                                                                                                                                       | Language setting label                                                             |

### `fossify-commons` Inherited UI Strings (29 Keys)

All 29 shared UI keys below are already localized in `org.fossify:commons:7.1.3` (`res/values-ja/values-ja.xml`); no local override in `values-ja/strings.xml` was required (the last 13 keys were verified as fully translated in this revision):

| # | Key (`name`) | English (`fossify-commons`) | Japanese (`fossify-commons`) | Source / Notes |
|---:|---|---|---|---|
| 1 | `change_date_and_time_format` | Change date and time format | 日付と時刻の表示形式を変更 | Inherited from `fossify-commons` (Settings) |
| 2 | `password_protect_file_deletion` | Password protect file deletion and moving | ファイルの削除と移動をパスワードで保護する | Inherited from `fossify-commons` (Settings) |
| 3 | `keep_last_modified` | Keep old last-modified value at file operations | ファイルの操作時、既存の最終更新日を保持する (更新日を更新しない) | Inherited from `fossify-commons` (Settings) |
| 4 | `clear_cache` | Clear cache | キャッシュのクリア | Inherited from `fossify-commons` (Settings) |
| 5 | `export_settings` | Export settings | 設定をエクスポート | Inherited from `fossify-commons` (Settings) |
| 6 | `import_settings` | Import settings | 設定をインポート | Inherited from `fossify-commons` (Settings) |
| 7 | `temporarily_show_excluded` | Temporarily show excluded | 除外項目を一時的に表示 | Inherited from `fossify-commons` (Options menu) |
| 8 | `change_view_type` | Change view type | 表示形式の変更 | Inherited from `fossify-commons` (Options menu) |
| 9 | `contributors` | Contributors | 貢献者 | Inherited from `fossify-commons` (About) |
| 10 | `known_issues` | Known issues | 既知の問題 | Inherited from `fossify-commons` (About) |
| 11 | `donate_to_fossify` | Donate to Fossify | Fossifyに寄付 | Inherited from `fossify-commons` (About) |
| 12 | `privacy_policy` | Privacy policy | プライバシー ポリシー | Inherited from `fossify-commons` (About) |
| 13 | `create_shortcut` | Create shortcut | ショートカットを作成 | Inherited from `fossify-commons` (CAB / ViewPager menu) |
| 14 | `print` | Print | 印刷 | Inherited from `fossify-commons` (ViewPager menu) |
| 15 | `search_files` | Search files | ファイルを検索 | Inherited from `fossify-commons` (Search bar hint) |
| 16 | `search_folders` | Search folders | フォルダーを検索 | Local override in `values-ja/strings.xml` (`fossify-commons` uses `フォルダを検索`; overridden to match `フォルダー` terminology) |
| 17 | `manage_excluded_folders` | Manage excluded folders | 除外フォルダの管理 | Inherited from `fossify-commons` (Settings); verified translated in this revision |
| 18 | `exclude` | Exclude | 除外する | Inherited from `fossify-commons` (Album selection / long-press menu); verified translated in this revision |
| 19 | `exclude_folder` | Exclude folder | フォルダを除外 | Inherited from `fossify-commons` (Options menu); verified translated in this revision |
| 20 | `lock_folder` | Lock folder | フォルダに鍵をかける | Inherited from `fossify-commons` (Album selection / long-press menu); verified translated in this revision |
| 21 | `change_cover_image` | Change cover image | カバー画像を変更 | Inherited from `fossify-commons` (Album selection / long-press menu); verified translated in this revision |
| 22 | `select_photo` | Select photo | 写真を選択 | Inherited from `fossify-commons` (Album selection, "Change cover image" dialog); verified translated in this revision |
| 23 | `use_default` | Use default | デフォルトを使用 | Inherited from `fossify-commons` (Album selection, "Change cover image" dialog); verified translated in this revision |
| 24 | `column_count` | Column count | 列数 | Inherited from `fossify-commons` (Main screen and in-album Options menu); verified translated in this revision |
| 25 | `open_the_recycle_bin` | Open the recycle bin | ごみ箱を開く | Inherited from `fossify-commons` (In-album Options menu); verified translated in this revision |
| 26 | `grid` | Grid | グリッド | Inherited from `fossify-commons` (In-album Options menu, view type); verified translated in this revision |
| 27 | `list` | List | リスト | Inherited from `fossify-commons` (In-album Options menu, view type); verified translated in this revision |
| 28 | `resize` | Resize | リサイズ | Inherited from `fossify-commons` (Selection CAB menu); verified translated in this revision |
| 29 | `version_placeholder` | Version %s | バージョン %s | Inherited from `fossify-commons` (About screen version text); verified translated in this revision |

### Newly Added Missing Translations (9 Keys) & Revised Entries (11 Keys)

Nine keys that were missing from `values-ja/strings.xml` were translated and added, and eleven existing entries were revised so that files and albums use the new \"encrypt / decrypt\" wording and so that the favorites import/export labels mention virtual albums as well.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | `number_of_items` | Number of items | 項目数 | Sorting option shown in the album view only; overridden locally because it is missing in `fossify-commons` for this locale |
| 2 | `random` | Random | ランダム | Sorting option offered in every sorting state; overridden locally because it is missing in `fossify-commons` for this locale |
| 3 | `shuffle` | Shuffle | シャッフル | Fullscreen viewer action that randomizes the media order; app owned string that was only available in English |
| 4 | `stop_showing_excluded` | Stop showing excluded | 除外した項目を表示しない | Main screen options-menu action that ends the temporary preview of excluded folders |
| 5 | `copy_to_clipboard` | Copy to clipboard | クリップボードにコピー | Fullscreen viewer action that copies a file to the clipboard |
| 6 | `encrypt` | Encrypt | 暗号化 | Replaces the inherited `hide` label for files; files and albums are encrypted, not hidden |
| 7 | `decrypt` | Decrypt | 復号 | Replaces the inherited `unhide` label for files |
| 8 | `encrypt_album` | Encrypt album | アルバムを暗号化 | Album (folder) context action that encrypts the album into the vault |
| 9 | `decrypt_album` | Decrypt album | アルバムを復号 | Album (folder) context action that decrypts the album back to its original location |
| 10 | `export_favorite_paths` | Export favorites and virtual albums | お気に入りと仮想アルバムをエクスポート | Settings label; renamed because virtual albums are exported together with the favorites |
| 11 | `import_favorite_paths` | Import favorites and virtual albums | お気に入りと仮想アルバムをインポート | Settings label; renamed because virtual albums are imported together with the favorites |
| 12 | `file_encrypted_successfully` | File encrypted successfully | ファイルを暗号化しました | Success toast after encrypting a single file |
| 13 | `files_encrypted_successfully` | Files encrypted successfully | ファイルを暗号化しました | Success toast after encrypting multiple files |
| 14 | `file_decrypted_successfully` | File decrypted successfully | ファイルを復号しました | Success toast after decrypting a single file |
| 15 | `files_decrypted_successfully` | Files decrypted successfully | ファイルを復号しました | Success toast after decrypting multiple files |
| 16 | `file_hidden_to_album` | File encrypted to \"%s\" | ファイルを「%s」に暗号化しました | Success toast after encrypting a single file into album `%s` |
| 17 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d 個のファイルを「%2$s」に暗号化しました | Success toast after encrypting `%1$d` files into album `%2$s` |
| 18 | `select_hidden_album` | Select Encrypted Album | 暗号化アルバムを選択 | Dialog title for choosing the target encrypted album |
| 19 | `create_new_hidden_album` | Create New Encrypted Album | 新しい暗号化アルバムを作成 | Action label to create a new encrypted album |
| 20 | `new_hidden_album` | New encrypted album | 新しい暗号化アルバム | Input hint / default label for a new encrypted album |

### Newly Added UI, Backup, Encryption Confirmation & Vault Strings (22 Keys)

Following the previous audit, 22 additional keys were localized in `values-ja/strings.xml` (`create_new_encrypted_virtual_album` and `new_album` for vault destination dialogs, plus 20 strings covering backup export/import, auto-backup prompt, encryption confirmations, delete-behavior options, album lock/unlock labels, and progress notifications; `hide_folder_description` was removed from the app, and `default_hidden_album` was marked `translatable="false"` as `Default Secret Album`):

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | `create_new_encrypted_virtual_album` | Create new encrypted virtual album | 新しい暗号化仮想アルバムを作成 | Dialog option for creating an encrypted virtual album inside the vault |
| 2 | `new_album` | New album | 新しいアルバム | Compact neutral button label in the move destination dialog |
| 3 | `filename_without_zip` | Filename (without .zip) | ファイル名（.zip を除く） | Input hint in the export favorites and virtual albums dialog |
| 4 | `favorites_already_exist` | You already have a favorites list | お気に入りリストはすでに存在します | Dialog title when importing favorites while a favorites list already exists |
| 5 | `delete_existing_list` | Delete existing list | 既存のリストを削除 | Radio option to replace the existing favorites list on import |
| 6 | `merge_lists` | Merge lists | リストを統合 | Radio option to merge imported favorites with the existing list |
| 7 | `confirm` | Confirm | 確認 | Positive button label in the favorites import conflict dialog |
| 8 | `confirm_encrypt_album` | Are you sure you want to encrypt this album? All of its media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | このアルバムを暗号化してもよろしいですか？中のメディアファイルはすべて暗号化され、暗号化された保管庫に移動されます。元のファイルは削除されます。 | Confirmation prompt before encrypting a single album into the vault |
| 9 | `confirm_encrypt_albums` | Are you sure you want to encrypt the selected albums? All of their media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | 選択したアルバムを暗号化してもよろしいですか？中のメディアファイルはすべて暗号化され、暗号化された保管庫に移動されます。元のファイルは削除されます。 | Confirmation prompt before encrypting multiple selected albums into the vault |
| 10 | `confirm_encrypt_file` | Are you sure you want to encrypt this file? It will be encrypted and moved to the encrypted vault. The original file will be deleted. | このファイルを暗号化してもよろしいですか？暗号化されて、暗号化された保管庫に移動されます。元のファイルは削除されます。 | Confirmation prompt before encrypting a single file into the vault |
| 11 | `confirm_encrypt_files` | Are you sure you want to encrypt the selected files? They will be encrypted and moved to the encrypted vault. The original files will be deleted. | 選択したファイルを暗号化してもよろしいですか？暗号化されて、暗号化された保管庫に移動されます。元のファイルは削除されます。 | Confirmation prompt before encrypting multiple selected files into the vault |
| 12 | `confirm_encrypt_files_to_vault` | The selected files will be encrypted when they are copied into the encrypted vault. Do you want to continue? | 選択したファイルは、暗号化された保管庫にコピーされるときに暗号化されます。続行しますか？ | Confirmation prompt when copying unencrypted files into an encrypted vault destination |
| 13 | `encrypt_decrypt_notifications` | Encrypt / Decrypt | 暗号化 / 復号 | Notification channel name for ongoing encrypt/decrypt progress notifications |
| 14 | `allow_batch_renaming` | Allow batch renaming when multiple files are selected | 複数のファイルが選択されているときに一括で名前変更を許可 | Retained in values/strings.xml for parity (its Settings switch was removed in a6a78d444) |
| 15 | `keep_original_remove_from_virtual_album` | Keep the original file, remove it only from this virtual album | 元のファイルを保持し、この仮想アルバムからのみ削除する | Checkbox in delete confirmation dialog when deleting from a virtual album |
| 16 | `keep_original_remove_from_favorites` | Keep the original file, remove it only from favorites | 元のファイルを保持し、お気に入りからのみ削除する | Checkbox in delete confirmation dialog when deleting from favorites |
| 17 | `delete_original_from_favorites_and_virtual_albums` | Delete the original version of a file when it is deleted from favorites and virtual albums | お気に入りおよび仮想アルバムから削除するときに元のファイルも削除する | Settings switch controlling default delete behavior from favorites and virtual albums |
| 18 | `cannot_target_encrypted_album` | Encrypted albums cannot be targeted for this operation | この操作では暗号化されたアルバムを対象にできません | Toast shown when an encrypted album cannot be targeted for an operation |
| 19 | `cannot_mix_encrypted_and_unencrypted` | Cannot combine encrypted and unencrypted items in this operation | この操作では暗号化された項目と暗号化されていない項目を混在させることはできません | Toast shown when an operation attempts to combine encrypted and unencrypted items |
| 20 | `lock_album` | Lock album | アルバムをロック | Album selection menu action replacing lock_folder |
| 21 | `unlock_album` | Unlock album | アルバムのロックを解除 | Album selection menu action replacing unlock_folder |
| 22 | `auto_backup_prompt_message` | Do you want your favorites and virtual albums to be regularly written to system storage outside of app memory? This will ensure that you do not lose your favorites list and virtual albums if you uninstall the app. You can manually create a backup from app settings at any time. | お気に入りと仮想アルバムを、アプリメモリ外のシステムストレージに定期的に書き出しますか？これにより、アプリをアンインストールしてもお気に入りリストや仮想アルバムを失わずに済みます。アプリの設定からいつでも手動でバックアップを作成できます。 | Full-screen prompt asking whether to regularly back up favorites and virtual albums to shared storage |
| 23 | `auto_backup_favorites_and_virtual_albums` | Back up favorites and virtual albums to device storage | お気に入りと仮想アルバムを端末のストレージにバックアップする | Settings switch under File operations (shown after first auto-backup prompt) |

## 2. Full 1-to-1 Comparison: English vs. Japanese

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) |
|---:|---|---|---|
| 1 | `app_launcher_name` | Calypso Gallery | ギャラリー |
| 2 | `edit` | Edit | 編集 |
| 3 | `open_camera` | Open camera | カメラを開く |
| 4 | `hidden` | (hidden) | (非表示) |
| 5 | `excluded` | (excluded) | (除外) |
| 6 | `pin_folder` | Pin folder | フォルダーをピン留めする |
| 7 | `unpin_folder` | Unpin folder | フォルダーのピン留めを外す |
| 8 | `pin_to_the_top` | Pin to the top | 上部にピン留めする |
| 9 | `show_all` | Show all folders content | すべてを表示 |
| 10 | `all_folders` | All folders | すべてのフォルダー |
| 11 | `folder_view` | Switch to folder view | フォルダー表示に切り替え |
| 12 | `other_folder` | Other folder | その他のフォルダー |
| 13 | `show_on_map` | Show on map | 地図上で表示 |
| 14 | `unknown_location` | Unknown location | 位置情報がありません |
| 15 | `volume` | Volume | 音量 |
| 16 | `brightness` | Brightness | 明るさ |
| 17 | `lock_orientation` | Lock orientation | 画面の向きを固定 |
| 18 | `unlock_orientation` | Unlock orientation | 固定を解除 |
| 19 | `change_orientation` | Change orientation | 向きを固定 |
| 20 | `force_portrait` | Force portrait | 縦で固定 |
| 21 | `force_landscape` | Force landscape | 横で固定 |
| 22 | `force_landscape_reverse` | Force landscape (reverse) | 横で固定 (逆向き) |
| 23 | `use_default_orientation` | Use default orientation | 既定の向きを使用 |
| 24 | `fix_date_taken` | Fix Date Taken value | 撮影日の値を修正 |
| 25 | `fixing` | Fixing… | 修正しています… |
| 26 | `dates_fixed_successfully` | Dates fixed successfully | 撮影日を修正しました |
| 27 | `no_date_takens_found` | No Date Taken values have been found | 撮影日が見つかりません |
| 28 | `share_resized` | Share a resized version | リサイズした画像を共有 |
| 29 | `switch_to_file_search` | Switch to file search across all visible folders | 表示されているすべてのフォルダーで検索に切り替え |
| 30 | `set_as_default_folder` | Set as default folder | 既定のフォルダーに設定 |
| 31 | `unset_as_default_folder` | Unset as default folder | 既定のフォルダーとして設定しない |
| 32 | `reorder_by_dragging` | Reorder folders by dragging | ドラッグによるフォルダーの並べ替え |
| 33 | `reorder_by_dragging_pro` | Reorder folders by dragging (Pro) | ドラッグによるフォルダーの並べ替え (Pro) |
| 34 | `restore_to_path` | Restoring to \'%s\' | \'%s\' に復元しています |
| 35 | `full_storage_permission_required` | Calypso Gallery needs full access to display all your photos and videos. Go to Settings > Permissions > Photos and videos > Allow all. | Fossifyギャラリーは、全ての写真と動画を表示するために、完全なアクセスを必要としています。設定から「権限」、「画像と写真」を開き、全て許可を選択してください。 |
| 36 | `filter_media` | Filter media | 表示する形式 |
| 37 | `images` | Images | 画像 |
| 38 | `videos` | Videos | 動画 |
| 39 | `gifs` | GIFs | GIF |
| 40 | `raw_images` | RAW images | RAW |
| 41 | `svgs` | SVGs | SVG |
| 42 | `portraits` | Portraits | ポートレイト |
| 43 | `no_media_with_filters` | No media files have been found with the selected filters. | 条件に該当するメディアがありません。 |
| 44 | `change_filters_underlined` | <u>Change filters</u> | <u>絞り込み条件を変更</u> |
| 45 | `exclude_folder_description` | This will exclude the selection together with its subfolders from Calypso Gallery only. You can manage excluded folders in Settings. | 選択したフォルダーとその子フォルダーをFossifyギャラリーから除外します。除外したフォルダーは設定画面で管理できます。 |
| 46 | `exclude_folder_parent` | Exclude a parent instead? | 親フォルダーを代わりに除外しますか？ |
| 47 | `excluded_activity_placeholder` | Excluding folders will make them together with their subfolders hidden just in Calypso Gallery, they will still be visible in other applications.\n\nIf you want to hide them from other apps too, use the Encrypt function. | フォルダーを除外すると、そのフォルダーとサブフォルダーはFossifyギャラリー上でのみ非表示になり、他のアプリでは引き続き表示されます。\n\n他のアプリからも非表示にしたい場合は、「非表示」機能を使用してください。 |
| 48 | `hidden_folders` | Hidden folders | 非表示フォルダー |
| 49 | `manage_hidden_folders` | Manage hidden folders | 非表示フォルダーの管理 |
| 50 | `hidden_folders_placeholder` | Seems like you don\'t have any folders hidden with a \".nomedia\" file or dot prefix. | 「.nomedia」で隠されたフォルダーはありません。 |
| 51 | `hidden_all_files` | You have to grant the app All Files access to see hidden files, else it cannot work. | 非表示項目を表示するには [すべてのファイルへのアクセス] を許可する必要があります。許可をしないと機能しません。 |
| 52 | `cant_unhide_folder` | If a folder or one of its parent folders has a dot before its name, it is hidden and cannot be unhidden like this. You have to remove the dot by renaming it. | フォルダーまたは親フォルダーの名前がドットから始まる場合、そのフォルダーは非表示となり、このような形で表示することはできません。名前からドットを削除する必要があります。 |
| 53 | `include_folders` | Included folders | 追加フォルダー |
| 54 | `manage_included_folders` | Manage included folders | 追加フォルダーの管理 |
| 55 | `add_folder` | Add folder | フォルダーを追加 |
| 56 | `included_activity_placeholder` | If you have some folders which contain media, but were not recognized by the app, you can add them manually here.\n\nAdding some items here will not exclude any other folder. | メディアを含むフォルダーがアプリに認識されない場合は、ここで手動で追加できます。\n\nここに項目を追加しても、他のフォルダーが除外されることはありません。 |
| 57 | `no_media_add_included` | No media files have been found. You can solve it by adding the folders containing media files manually. | メディアファイルが見つかりません。メディアファイルを手動で追加することにより解決できます。 |
| 58 | `resize_and_save` | Resize selection and save | 選択領域をリサイズして保存 |
| 59 | `width` | Width | 幅 |
| 60 | `height` | Height | 高さ |
| 61 | `keep_aspect_ratio` | Keep aspect ratio | 縦横比を固定 |
| 62 | `invalid_values` | Please enter a valid resolution | 解像度を正しく入力してください |
| 63 | `resize_multiple_images` | Resize multiple images | 複数の画像のサイズを変更 |
| 64 | `resize_factor` | Resize factor | 倍率を変更 |
| 65 | `resize_factor_info` | Resize images to the given percentage, value must be within 10 and 90. | 指定した割合に画像のサイズを変更。値は10から90までの間を指定してください。 |
| 66 | `resize_factor_error` | Enter a number between 10 and 90 | 値は10から90までの間を指定してください |
| 67 | `images_resized_successfully` | Images resized successfully | 画像のサイズを変更しました |
| 68 | `editor` | Editor | 編集 |
| 69 | `basic_editor` | Basic Editor | 基本的な編集 |
| 70 | `advanced_editor` | Advanced Editor | 高度な編集 |
| 71 | `rotate` | Rotate | 回転 |
| 72 | `invalid_image_path` | Invalid image path | 画像のパスが正しくありません |
| 73 | `invalid_video_path` | Invalid video path | 動画のパスが正しくありません |
| 74 | `image_editing_failed` | Image editing failed | 画像を編集できませんでした |
| 75 | `video_editing_failed` | Video editing failed | 動画を編集できませんでした |
| 76 | `image_editing_cancelled` | Image editing cancelled | 画像の編集をキャンセルしました |
| 77 | `video_editing_cancelled` | Video editing cancelled | 動画の編集をキャンセルしました |
| 78 | `file_edited_successfully` | File edited successfully | ファイルを編集しました |
| 79 | `image_edited_successfully` | Image edited successfully | 画像を編集しました |
| 80 | `video_edited_successfully` | Video edited successfully | 動画を編集しました |
| 81 | `edit_image_with` | Edit image with: | 画像を編集: |
| 82 | `edit_video_with` | Edit video with: | 動画を編集: |
| 83 | `no_image_editor_found` | No image editor found | 画像エディターが見つかりません |
| 84 | `no_video_editor_found` | No video editor found | 動画エディターが見つかりません |
| 85 | `unknown_file_location` | Unknown file location | ファイルの場所が不明です |
| 86 | `overwrite_original` | Overwrite original | 元のファイルを上書きする |
| 87 | `error_saving_file` | Could not overwrite the source file | 元のファイルを上書きできません |
| 88 | `rotate_left` | Rotate left | 左に回転 |
| 89 | `rotate_right` | Rotate right | 右に回転 |
| 90 | `rotate_one_eighty` | Rotate by 180º | 180度回転 |
| 91 | `transform` | Transform | 変形 |
| 92 | `crop` | Crop | 切り抜き |
| 93 | `draw` | Draw | 描画 |
| 94 | `flip` | Flip | 反転 |
| 95 | `flip_horizontally` | Flip horizontally | 水平方向に反転 |
| 96 | `flip_vertically` | Flip vertically | 垂直方向に反転 |
| 97 | `free_aspect_ratio` | Free | 任意 |
| 98 | `other_aspect_ratio` | Other | その他 |
| 99 | `wallpaper` | Wallpaper | 壁紙 |
| 100 | `set_as_wallpaper` | Set as Wallpaper | 壁紙に設定 |
| 101 | `set_as_wallpaper_failed` | Setting as Wallpaper failed | 壁紙に設定できませんでした |
| 102 | `set_as_wallpaper_with` | Set as wallpaper with: | 壁紙に設定: |
| 103 | `setting_wallpaper` | Setting wallpaper… | 壁紙に設定しています… |
| 104 | `wallpaper_set_successfully` | Wallpaper set successfully | 壁紙を設定しました |
| 105 | `portrait_aspect_ratio` | Portrait aspect ratio | 縦向きの縦横比 |
| 106 | `landscape_aspect_ratio` | Landscape aspect ratio | 横向きの縦横比 |
| 107 | `home_screen` | Home screen | ホーム画面 |
| 108 | `lock_screen` | Lock screen | ロック画面 |
| 109 | `home_and_lock_screen` | Home and lock screen | ホーム画面とロック画面 |
| 110 | `allow_changing_aspect_ratio` | Allow changing the aspect ratio | 縦横比の変更を許可 |
| 111 | `slideshow` | Slideshow | スライドショー |
| 112 | `interval` | Interval | 間隔 |
| 113 | `include_photos` | Include photos | 写真を含める |
| 114 | `include_videos` | Include videos | 動画を含める |
| 115 | `include_gifs` | Include GIFs | GIFを含める |
| 116 | `random_order` | Random order | ランダムな順序 |
| 117 | `move_backwards` | Move backwards | 逆方向に進む |
| 118 | `loop_slideshow` | Loop slideshow | スライドショーをリピート再生する |
| 119 | `animation` | Animation | アニメーション |
| 120 | `no_animation` | None | なし |
| 121 | `fade` | Fade | フェード |
| 122 | `slide` | Slide | スライド |
| 123 | `slideshow_ended` | The slideshow ended | スライドショーが終了しました |
| 124 | `no_media_for_slideshow` | No media for the slideshow have been found | スライドショーに表示するメディアがありません |
| 125 | `group_direct_subfolders` | Group direct subfolders | サブフォルダーでグループ化 |
| 126 | `group_by` | Group by | グループ分け |
| 127 | `do_not_group_files` | Do not group files | 何もしない |
| 128 | `by_folder` | Folder | フォルダー |
| 129 | `by_last_modified` | Last modified | 更新日時 |
| 130 | `by_last_modified_daily` | Last modified (daily) | 更新日時 (毎日) |
| 131 | `by_last_modified_monthly` | Last modified (monthly) | 更新日時 (毎月) |
| 132 | `by_date_taken` | Date taken | 撮影日時 |
| 133 | `by_date_taken_daily` | Date taken (daily) | 撮影日時 (毎日) |
| 134 | `by_date_taken_monthly` | Date taken (monthly) | 撮影日時 (毎月) |
| 135 | `by_file_type` | File type | ファイル形式 |
| 136 | `by_extension` | Extension | 拡張子 |
| 137 | `show_file_count_section_header` | Show file count at section headers | セクションの見出しにファイル数を表示 |
| 138 | `grouping_and_sorting` | Please note that grouping and sorting are 2 independent fields | 注意：グループ分けと並べ替えはそれぞれ独立したフィールドです |
| 139 | `folder_on_widget` | Folder shown on the widget: | ウィジェットに表示するフォルダー: |
| 140 | `show_folder_name` | Show folder name | フォルダー名を表示 |
| 141 | `autoplay_videos` | Play videos automatically | 動画を自動再生 |
| 142 | `remember_last_video_position` | Remember last video playback position | 動画の最後の再生位置を記憶する |
| 143 | `loop_videos` | Loop videos | 動画を繰り返し再生 |
| 144 | `video_double_tap_duration` | Double-tap seek duration | ダブルタップ時のシーク時間 |
| 145 | `video_seek_duration_seconds` | %d seconds | %d秒 |
| 146 | `animate_gifs` | Animate GIFs at thumbnails | アニメーションGIFを動かす |
| 147 | `max_brightness` | Max brightness when viewing fullscreen media | メディアの表示時に明るさを最大化 |
| 148 | `render_ultra_hdr_photos` | Show Ultra HDR photos in HDR | Ultra HDR写真をHDRで表示 |
| 149 | `crop_thumbnails` | Crop thumbnails into squares | サムネイルを正方形に切り取る |
| 150 | `show_thumbnail_video_duration` | Show video durations | 動画の長さを表示 |
| 151 | `screen_rotation_by` | Rotate fullscreen media by | 全画面表示時のメディアの回転 |
| 152 | `screen_rotation_system_setting` | System setting | システム設定に従う |
| 153 | `screen_rotation_device_rotation` | Device rotation | 端末の向きに従う |
| 154 | `screen_rotation_aspect_ratio` | Aspect ratio | メディアの縦横比に従う |
| 155 | `black_background_at_fullscreen` | Black background at fullscreen media | 全画面表示の背景色を黒にする |
| 156 | `scroll_thumbnails_horizontally` | Scroll thumbnails horizontally | サムネイル画面を横方向にスクロール |
| 157 | `hide_system_ui_at_fullscreen` | Automatically hide system UI at fullscreen media | 全画面表示時にシステムUIを自動的に隠す |
| 158 | `delete_empty_folders` | Delete empty folders after deleting their content | ファイルの削除後、空になったフォルダーを削除 |
| 159 | `keep_screen_on_fullscreen_photos` | Keep the screen on while viewing fullscreen photos | 写真の全画面表示中は画面をオンのままにする |
| 160 | `allow_photo_gestures` | Allow controlling photo brightness with vertical gestures | 写真の明るさを縦の動作で制御 |
| 161 | `allow_video_gestures` | Allow controlling video volume and brightness with vertical gestures | 音量と明るさを縦の動作で変更 |
| 162 | `playback_speed` | Playback speed | 再生速度 |
| 163 | `show_media_count` | Show folder media count on the main view | フォルダー内のメディア数を表示 |
| 164 | `show_extended_details` | Show extended details over fullscreen media | 全画面表示に詳細も表示 |
| 165 | `manage_extended_details` | Manage extended details | 表示する詳細の選択 |
| 166 | `one_finger_zoom` | Allow one finger zoom at fullscreen media | メディアを指だけでズーム |
| 167 | `allow_instant_change` | Allow instantly changing media by clicking on screen sides | 画面端を押してメディアを切り替え |
| 168 | `allow_deep_zooming_images` | Allow deep zooming images | 深いズームを許可 |
| 169 | `hide_extended_details` | Hide extended details when status bar is hidden | ステータスバーが非表示の時は詳細を隠す |
| 170 | `show_at_bottom` | Show some action buttons at the bottom of the screen | 画面の下にアクションを表示 |
| 171 | `show_recycle_bin` | Show the recycle bin on the folders screen | フォルダー画面にごみ箱を表示 |
| 172 | `deep_zoomable_images` | Deep zoomable images | 画像のズームを深くする |
| 173 | `show_highest_quality` | Show images in the highest possible quality | 可能な限り高品質で画像を表示 |
| 174 | `show_recycle_bin_last` | Show the recycle bin as the last item on the main screen | ごみ箱をメイン画面の最後に表示 |
| 175 | `allow_down_gesture` | Allow closing the fullscreen view with a down gesture | 全画面表示を下にスワイプするジェスチャーで閉じる |
| 176 | `allow_one_to_one_zoom` | Allow 1:1 zooming in with two double taps | ダブルタップして 1:1 ズームする |
| 177 | `open_videos_on_separate_screen` | Play videos on a separate screen with horizontal gestures | 左右ジェスチャー機能の切り替え (別の動画を開くor早送り/早戻し) |
| 178 | `on_video_tap` | On video tap | 動画をタップしたとき |
| 179 | `open_in_app_player` | Open in-app player | アプリ内プレーヤーを開く |
| 180 | `open_system_default_player` | Open in another app | 別のアプリで開く |
| 181 | `show_notch` | Show a notch if available | ノッチ領域にも画像を表示 |
| 182 | `allow_rotating_gestures` | Allow rotating images with gestures | ジェスチャーで画像を回転 |
| 183 | `file_loading_priority` | File loading priority | ファイルの優先ローディング |
| 184 | `speed` | Speed | スピード |
| 185 | `compromise` | Compromise | コンプロマイズ |
| 186 | `avoid_showing_invalid_files` | Avoid showing invalid files | 無効なファイルを非表示 |
| 187 | `show_image_file_types` | Show image file types | 画像ファイルの種類を表示 |
| 188 | `allow_zooming_videos` | Allow zooming videos with double tapping them | ダブルタップで動画ズームを設定 |
| 189 | `folder_thumbnail_style` | Folder thumbnail style | フォルダーサムネイルのスタイル |
| 190 | `file_thumbnail_style` | File thumbnail style | サムネイルのスタイル |
| 191 | `mark_favorite_items` | Mark favorite items | お気に入りマークを表示 |
| 192 | `thumbnail_spacing` | Thumbnail spacing | サムネイルの間隔 |
| 193 | `show_file_count_line` | Show file count on a separate line | ファイル数を別の行に表示 |
| 194 | `show_file_count_brackets` | Show file count in brackets | ファイル数を括弧内に表示 |
| 195 | `show_file_count_none` | Do not show file count | ファイル数を非表示 |
| 196 | `limit_folder_title` | Limit long folder titles to 1 line | 長いフォルダー名を1行に制限 |
| 197 | `square` | Square | 四角 |
| 198 | `rounded_corners` | Rounded corners | 角丸 |
| 199 | `export_favorite_paths` | Export favorites and virtual albums | お気に入りと仮想アルバムをエクスポート |
| 200 | `import_favorite_paths` | Import favorites and virtual albums | お気に入りと仮想アルバムをインポート |
| 201 | `paths_imported_successfully` | Paths imported successfully | パスをインポートしました |
| 202 | `media_management_prompt` | To make sure that all file operations work reliably, please make this app a Media management app in your device settings. | すべてのファイル操作を確実に行うためには、端末の設定でこのアプリをメディア管理アプリにする必要があります。 |
| 203 | `password_protect_excluded` | Password protect excluded folder visibility | 除外されたフォルダーの表示をパスワードで保護 |
| 204 | `media_management_manual` | Something went wrong, please go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | 問題が発生しました。端末の [設定] &gt; [アプリ] &gt; [特別なアプリアクセス] &gt; [メディア管理アプリ] に移動して、このアプリをメディア管理アプリに設定してください。 |
| 205 | `media_management_note` | If the redirection does not work, please go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | リダイレクトが機能しない場合、端末の [設定] &gt; [アプリ] &gt; [特別なアプリアクセス] &gt; [メディア管理アプリ] に移動して、このアプリをメディア管理アプリに設定してください。 |
| 206 | `media_management_alternative` | If you do not want to do it, you can also go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | もし、それを許可したくない場合は、端末の [設定] &gt; [アプリ] &gt; [特別なアクセス] &gt; [メディア管理アプリ] に移動して、このアプリをメディア管理アプリに設定してください。 |
| 207 | `alternative_media_access` | Alternatively, you can allow accessing media files only. In that case you will not be able to work with hidden files though. | または、メディアファイルのみアクセスを許可することもできます。ただし、この場合、隠しファイル (このアプリでは非表示項目を指します) への操作は行なえません。 |
| 208 | `media_only` | Media only | メディアのみ |
| 209 | `all_files` | All files | すべてのファイル |
| 210 | `main_screen_view_on_launch` | Main screen view on launch | 起動時のメイン画面表示 |
| 211 | `show_folders` | Show folders | フォルダーを表示 |
| 212 | `show_files` | Show files | ファイルを表示 |
| 213 | `show_all_folders` | Show a menu button for toggling Show All Folders Content quickly | 「すべてを表示」を素早く切り替えるためのメニューボタンを表示 |
| 214 | `thumbnails` | Thumbnails | サムネイル設定 |
| 215 | `fullscreen_media` | Fullscreen media | メディア設定 |
| 216 | `extended_details` | Extended details | 詳細も表示 |
| 217 | `bottom_actions` | Bottom actions | 画面下部のアクション |
| 218 | `manage_bottom_actions` | Manage visible bottom actions | 表示するアクションの選択 |
| 219 | `toggle_favorite` | Toggle favorite | お気に入り |
| 220 | `toggle_file_visibility` | Toggle file visibility | 表示/非表示の切替 |
| 221 | `failed_to_load_media` | Failed to load media. | メディアを読み込めませんでした。 |
| 222 | `file_is_malformed_or_corrupted` | File is malformed or corrupted. | ファイルが不正または破損しています。 |
| 223 | `media_exceeds_device_capabilities` | Media exceeds device capabilities. | メディアが端末の性能を超えています。 |
| 224 | `unsupported_format` | Unsupported format. | サポートされていない形式です。 |
| 225 | `video_no_sound` | This video has no audio | この動画には音声がありません |
| 226 | `faq_1_title` | How can I make Calypso Gallery the default device gallery? | Fossifyギャラリーを端末のデフォルトのギャラリーにするにはどうすればよいですか？ |
| 227 | `faq_1_text` | First you have to find the currently default gallery in the Apps section of your device settings, look for a button that says something like \"Open by default\", click on it, then select \"Clear defaults\". The next time you will try opening an image or video you should see an app picker, where you can select Fossify Gallery and make it the default app. | まず、端末の「設定」の「アプリ」セクションで現在デフォルトに設定されているギャラリーを見つけ、「デフォルトで開く」などのボタンをタップして「デフォルト設定をクリア」を選択します。次回、画像や動画を開こうとした際にアプリ選択画面が表示されるので、そこでFossifyギャラリーを選択してデフォルトのアプリに設定してください。 |
| 228 | `faq_2_title` | I locked the app with a password, but I forgot it. What can I do? | アプリをパスワードでロックしましたが、パスワードを忘れてしまいました。どうすればいいですか？ |
| 229 | `faq_2_text` | You can solve it in 2 ways. You can either reinstall the app, or find the app in your device settings and select \"Clear data\". It will reset all your settings, it will not remove any media files. | 解決策は2つあります。アプリを再インストールするか、デバイス設定からアプリを見つけて「データを消去」を選択するかです。そうすると設定がリセットされますが、メディアファイルは削除されません。 |
| 230 | `faq_3_title` | How can I make an album always appear at the top? | アルバムが常に上部に表示されるようにするにはどうすればいいですか？ |
| 231 | `faq_3_text` | You can long press the desired album and select the Pin icon at the actions menu, that will pin it to the top. You can pin multiple folders too, pinned items will be sorted by the default sorting method. | アルバムを長押しで選択し、アクションメニューからピンアイコンを選択すれば上部に固定されます。複数のフォルダーをピン留めできます。ピン留めされた項目群は、既定の並べ替え方法で並べ替えられます。 |
| 232 | `faq_4_title` | How can I fast forward videos? | 動画を早送りするにはどうすればよいですか？ |
| 233 | `faq_4_text` | You can do it by double tapping the side of the screen, or tapping the current or max duration texts near the seekbar. If you enable opening videos on a separate screen in the app settings, you can use horizontal gestures too. | 画面端をダブルタップするか、シークバーの近くにある曲の長さを示すテキストをタップすれば可能です。アプリ設定の左右ジェスチャー機能の切り替えを有効にすると、右ジェスチャーで早送りが可能です（無効にすると左右ジェスチャーで別の動画を開きます）。 |
| 234 | `faq_5_title` | What is the difference between hiding and excluding a folder? | フォルダーの非表示と除外の違いは何ですか？ |
| 235 | `faq_5_text` | Exclude prevents displaying the folder only in Calypso Gallery, while Hide works system-wise and it hides the folder from other galleries too. It works by creating an empty \".nomedia\" file in the given folder, which you can then remove with any file manager too. Note that some devices do not allow hiding folders like Camera, Screenshots and Downloads. | 「除外」はFossifyギャラリー内でのみフォルダーを表示しないようにするのに対し、「非表示」はシステム全体で機能し、他のギャラリーアプリからもフォルダーを隠します。これは指定したフォルダー内に空の「.nomedia」ファイルを作成することで動作し、任意のファイルマネージャーで削除することもできます。なお、一部の端末では Camera、Screenshots、Downloads などのフォルダーを非表示にできない場合があります。 |
| 236 | `faq_6_title` | Why do folders with music cover art or stickers show up? | 音楽のカバー画像が表示されるのはなぜですか？ |
| 237 | `faq_6_text` | It can happen that you will see some unusual albums show up. You can easily exclude them by long pressing them and selecting Exclude. In the next dialog you can then select the parent folder, chances are it will prevent the other related albums showing up too. | 埋め込みのカバー画像ではなく外部参照のカバー画像をアルバムが使用している場合、アルバムのカバー画像が表示されることがあります。長押しして除外を選択することで取り除くことができます。表示されるダイアログで親フォルダーを選択すると、他のカバー画像も表示されなくなります。 |
| 238 | `faq_7_title` | A folder with images isn\'t showing up, or it doesn\'t show all items.  What can I do? | 画像フォルダーが表示されなかったり、全てのアイテムが表示されなかったりします。どうしたらいいですか？ |
| 239 | `faq_7_text` | That can have multiple reasons, but solving it is easy. Just go in Settings -&gt; Manage Included Folders, select Plus and navigate to the required folder. | 理由はいくつかありますが、解決は簡単です。[設定] → [追加フォルダーの管理] に移動して、プラスボタン（＋ボタン）を押し、表示したいフォルダーを選択して追加します。 |
| 240 | `faq_8_title` | What if I want just a few particular folders visible? | 特定のフォルダーのみ表示にしたい場合はどうすればいいですか？ |
| 241 | `faq_8_text` | Adding a folder at the Included Folders doesn\'t automatically exclude anything. What you can do is go in Settings -&gt; Manage Excluded Folders, exclude the root folder \"/\", then add the desired folders at Settings -&gt; Manage Included Folders. That will make only the selected folders visible, as both excluding and including are recursive and if a folder is both excluded and included, it will show up. | 追加フォルダーにフォルダーを追加しても、自動的に除外されるわけではありません。[設定] → [除外フォルダーの管理] に移動し、ルートフォルダーを除外し、 [設定] → [追加フォルダーの管理] で目的のフォルダーを追加すれば可能です。除外と包含は両方再帰的なので、フォルダーが含まれており除外もされている場合、選択したフォルダーのみ表示されるようになります。 |
| 242 | `faq_10_title` | Can I crop images with this app? | このアプリで画像をトリミングできますか？ |
| 243 | `faq_10_text` | Yes, you can crop images in the editor, by dragging the image corners. You can get to the editor either by long pressing an image thumbnail and selecting Edit, or selecting Edit from the fullscreen view. | はい、エディターで画像をトリミングできます。編集画面で画像の隅をドラッグしてください。エディターは、画像のサムネイルを長押しして編集を選択するか、全画面表示で編集を選択すると開けます。 |
| 244 | `faq_11_title` | Can I somehow group media file thumbnails? | どうすればメディアファイルのサムネイルをグループ化できますか？ |
| 245 | `faq_11_text` | Sure, just use the \"Group by\" menu item while at the thumbnails view. You can group files by multiple criteria, including Date Taken. If you use the \"Show all folders content\" function you can group them by folders too. | サムネイル表示中に「グループ分け」メニューを使用してください。撮影日を含めた、複数の条件でグループ化できます。「すべてを表示」機能を使えば、フォルダーごとにグループ化することもできます。 |
| 246 | `faq_12_title` | Sorting by Date Taken doesn\'t seem to work properly, how can I fix it? | 撮影日による並べ替えがうまく機能しないようです。どうすれば直せますか？ |
| 247 | `faq_12_text` | It is most likely caused by the files being copied from somewhere. You can fix it by selecting the file thumbnails and selecting \"Fix Date Taken value\". | おそらく、ファイルがコピーされたものであることが原因でしょう。ファイルサムネイルを選択し、「撮影日の値を修正」を選択することにより直せます。 |
| 248 | `faq_13_title` | I see some color banding on the images. How can I improve the quality? | 画像にバンディング（濃淡の縞模様）が見られます。どうすれば画質を上げられますか？ |
| 249 | `faq_13_text` | The current solution for displaying images works fine in the vast majority of cases, but if you want even better image quality, you can enable the \"Show images in the highest possible quality\" at the app settings, in the \"Deep zoomable images\" section. | 現在の表示方法では大抵の場合、問題は見られませんが、より高画質で表示したい場合は、アプリ設定の「画像のズームを深くする」セクションで「可能な限り高品質で画像を表示」を有効にすると良いでしょう。 |
| 250 | `faq_14_title` | I have hidden a file/folder. How can I unhide it? | 非表示にしているファイルやフォルダーがあります。もう一度表示するにはどうすればいいですか？ |
| 251 | `faq_14_text` | You can either press the \"Temporarily show hidden items\" menu item at the main screen, or toggle \"Show hidden items\" in the app settings to see the hidden item. If you want to unhide it, just long press it and select \"Unhide\". Folders are hidden by adding a hidden \".nomedia\" file into them, you can delete the file with any file manager too. Note that hiding works recursively though, so if you hide a folder, all subfolders will become hidden too. So for unhiding the subfolders you have to unhide the parent folder. | メイン画面で「非表示項目を一時的に表示」を押すか、アプリの設定で「非表示のアイテムを表示する」を有効にすると非表示にしている項目を見ることができます。再表示したい場合は長押しして「再表示」を選択してください。フォルダーは非表示にするための「.nomedia」ファイルを追加して非表示にしています。この「.nomedia」ファイルはファイルマネージャーで削除することもできます。※非表示は再帰的に機能するため、フォルダーを非表示にすると、すべてのサブフォルダーも非表示になることに注意してください。したがって、サブフォルダーを再表示するには親フォルダーを再表示する必要があります。 |
| 252 | `faq_15_title` | Why does the app take up so much space? | アプリが多くの容量を使用するのはなぜですか？ |
| 253 | `faq_15_text` | App cache can take up to 250MB, it ensures quicker image loading. If the app is taking up even more space, it is most likely caused by you having items in the recycle bin. Those files count to the app size. You can clear the recycle bin by opening it and deleting all files, or from the app settings. Every file in the bin is deleted automatically after 30 days. | アプリのキャッシュは画像の素早い読み込みのために最大250MB使用します。もしそれ以上にアプリが容量を取るようであれば、ごみ箱内のアイテムが原因となっているはずです。ごみ箱内のファイルもアプリ容量として加算されます。ごみ箱を開いてすべてのファイルを削除するか、アプリの設定から「ごみ箱を空にする」を実行してください。なお、ごみ箱内のアイテムは30日を過ぎると自動的に削除されます。 |
| 254 | `faq_16_title` | What happened to file and folder hiding and why cannot I see hidden items anymore? | ファイルとフォルダーを非表示にする機能はどうなったのでしょうか？非表示の項目が表示されなくなったのはなぜですか？ |
| 255 | `faq_16_text` | Starting with Android 11 you cannot hide or unhide files or folders anymore, you cannot see the hidden ones in gallery apps due to system restrictions either. You will have to use some file manager for that. | Android11以降では、システムの制限により、ファイルやフォルダーの非表示と非表示解除ができなくなりました。また、ギャラリーアプリ内で非表示の項目を確認できなくなりましたので、対処可能な別のファイルマネージャーを使用する必要があります。 |
| 256 | `faq_16_text_extra` | Or you can also grant this gallery access to All Files through your device settings, that will allow us showing hidden items and make file operations more reliable in general. | あるいは、デバイスの設定でこのギャラリーに [すべてのファイル] へのアクセスを許可すると、非表示項目の表示が可能になり、確実性を高めたファイル操作を行うことができるようになります。 |
| 257 | `faq_17_title` | Why cannot I include missing folders anymore? | 見つからなくなったフォルダーを含めることができなくなったのはなぜですか？ |
| 258 | `faq_17_text` | That stopped working due to the system changes that came with Android 11 too, the app cannot browse real folders anymore, it relies on the so called MediaStore at fetching data. | これも Android 11 のシステム変更により機能しなくなりました。アプリは実際のフォルダーを参照できなくなり、システムのMediaStoreに依存するようになりました。 |
| 259 | `faq_18_title` | Why do I see ads during video playback? | 動画の再生中に広告が表示されるのはなぜですか？ |
| 260 | `faq_18_text` | Our apps have no ads whatsoever. If you see them during video playback, you must be using some other apps video player. Try finding your default video player in the device settings, then do a \"Clear defaults\" on it. The next time you invoke some video intent you will see an app picker prompt, where you can select what app you want to use. | 私たちのアプリには広告は一切表示されません。動画の再生中に広告が表示される場合、他のアプリの動画プレーヤーを使用している可能性があります。端末の設定でデフォルトの動画プレーヤーを見つけて、「デフォルトの設定をクリア」（あるいは「設定を消去」や「デフォルトをクリア」といった表記のもの）を実行してみてください。次回以降、何らかの動画を開こうとすると、使用するアプリを選択するアプリ選択ダイアログが表示されます。 |
| 261 | `create_virtual_album` | Create virtual album | 仮想アルバムを作成 |
| 262 | `new_virtual_album` | New album… | 新しいアルバム… |
| 263 | `rename_virtual_album` | Rename album | アルバム名を変更 |
| 264 | `album_name` | Album name | アルバム名 |
| 265 | `virtual_folder_name` | Virtual album name | 仮想アルバム名 |
| 266 | `virtual_album_name_empty` | The album name cannot be empty | アルバム名は空にできません |
| 267 | `virtual_album_exists` | An album with this name already exists | この名前のアルバムはすでに存在します |
| 268 | `add_to_virtual_album` | Add to album | アルバムに追加 |
| 269 | `remove_from_virtual_album` | Remove from album | アルバムから削除 |
| 270 | `added_to_virtual_album` | Added to \"%s\" | 「%s」に追加しました |
| 271 | `already_in_virtual_album` | Already in \"%s\" | すでに「%s」に含まれています |
| 272 | `removed_from_virtual_album` | Removed from the album | アルバムから削除しました |
| 273 | `delete_virtual_album_confirmation` | Delete the album %s? Only the album is removed, your photos and videos stay where they are. | アルバム %s を削除しますか？アルバムのみが削除され、写真や動画はそのまま残ります。 |
| 274 | `virtual_album_empty` | This album is empty. Select items in any folder and use \"Copy to\". | このアルバムは空です。任意のフォルダーで項目を選択し、「コピー先」を使用してください。 |
| 275 | `virtual_album_cannot_be_modified` | This action is not available for virtual albums | この操作は仮想アルバムでは利用できません |
| 276 | `alphanumeric_password` | Alphanumeric | 英数字 |
| 277 | `enter_password` | Enter password | パスワードを入力 |
| 278 | `repeat_password` | Repeat password | パスワードを再入力 |
| 279 | `wrong_password` | Wrong password | パスワードが間違っています |
| 280 | `encrypting_files` | Encrypting files… | ファイルを暗号化しています… |
| 281 | `encrypting_folder` | Encrypting folder… | フォルダーを暗号化しています… |
| 282 | `folder_encrypted_successfully` | Folder encrypted successfully | フォルダーを暗号化しました |
| 283 | `file_encrypted_successfully` | File encrypted successfully | ファイルを暗号化しました  |
| 284 | `files_encrypted_successfully` | Files encrypted successfully | ファイルを暗号化しました  |
| 285 | `decrypting_folder` | Decrypting folder… | フォルダーを復号しています… |
| 286 | `folder_decrypted_successfully` | Folder decrypted successfully | フォルダーを復号しました |
| 287 | `file_decrypted_successfully` | File decrypted successfully | ファイルを復号しました |
| 288 | `files_decrypted_successfully` | Files decrypted successfully | ファイルを復号しました |
| 289 | `decrypting_files` | Decrypting files… | ファイルを復号しています… |
| 290 | `place_finger_prompt` | Please place your finger on the fingerprint sensor | 指紋センサーに指を置いてください |
| 291 | `connect_at_least_4_dots_prompt` | Connect at least 4 dots | 4つ以上の点をつなげてください |
| 292 | `access_existing_secrets_prompt` | Do you want to access your existing Secrets folder? | 既存の Secrets フォルダーにアクセスしますか？ |
| 293 | `delete_and_recreate_secrets_prompt` | Existing Secrets folder will be deleted and a new one will be created. Are you sure? | 既存の Secrets フォルダーは削除され、新しく作成されます。よろしいですか？ |
| 294 | `confirm_checkbox_label` | I confirm | 確認しました |
| 295 | `secret_vault` | Encrypted Vault | 暗号化された保管庫 |
| 296 | `secret_vault_empty` | Encrypted Vault is empty | 暗号化された保管庫は空です |
| - | `press_back_again_to_exit_vault` | Press back again to exit | 終了するにはもう一度戻るボタンを押してください |
| - | `block_vault_screenshots` | Block screenshots in encrypted vault | 暗号化保管庫でのスクリーンショットを禁止する |
| 297 | `confirm_decrypt_hidden_file` | Are you sure you want to decrypt the hidden file(s)? | 非表示ファイルを復号してもよろしいですか？ |
| 298 | `copy_to_clipboard_disabled_in_vault` | Copying to clipboard is disabled while vault is unlocked for security. | セキュリティのため、保管庫のロック解除中はクリップボードへのコピーが無効になっています。 |
| 299 | `stop_showing_hidden` | Stop showing encrypted media | 暗号化メディアの表示を停止 |
| 300 | `system_default` | System default | システムのデフォルト |
| 301 | `language` | Language | 言語 |
| 302 | `open_system_language_settings` | Open system language settings | システムの言語設定を開く |
| 303 | `access_hidden_items_with_biometrics` | Access hidden items with biometrics | 生体認証で非表示項目にアクセス |
| 304 | `enable_hidden_protection_first_prompt` | Please enable hidden item password protection first | 先に非表示項目のパスワード保護を有効にしてください |
| 305 | `biometric_unlock_vault` | Unlock encrypted vault | 暗号化された保管庫をロック解除 |
| 306 | `biometric_unlock_prompt` | Verify your biometric identity to access hidden items | 非表示項目にアクセスするには生体認証で本人確認を行ってください |
| 307 | `biometric_setup_prompt` | Confirm your biometric identity to enable biometric vault access | 保管庫への生体認証アクセスを有効にするには生体認証で本人確認を行ってください |
| 308 | `use_password_or_pin` | Use PIN / Password | PIN / パスワードを使用 |
| 309 | `biometric_key_invalidated` | Biometric credentials have changed. Please enter your PIN or password. | 生体認証情報が変更されました。PIN またはパスワードを入力してください。 |
| 310 | `no_biometric_hardware_or_enrolled` | No biometric hardware or enrolled credentials found on this device | このデバイスに生体認証ハードウェアまたは登録済みの認証情報が見つかりません |
| 311 | `default_hidden_album` | Default Secret Album | *(non-translatable in `values/strings.xml`)* |
| 312 | `select_hidden_album` | Select Encrypted Album | 暗号化アルバムを選択 |
| 313 | `create_new_hidden_album` | Create New Encrypted Album | 新しい暗号化アルバムを作成 |
| 314 | `new_hidden_album` | New encrypted album | 新しい暗号化アルバム |
| 315 | `hidden_album_name_empty` | Album name cannot be empty | アルバム名を空にすることはできません |
| 316 | `file_hidden_to_album` | File encrypted to \"%s\" | ファイルを「%s」に暗号化しました  |
| 317 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d 個のファイルを「%2$s」に暗号化しました  |
| 318 | `restore_confirmation` (`one`) | Are you sure you want to restore %d file? | 本当に %d ファイルを復元しますか? *(CLDR `other`)* |
| 319 | `restore_confirmation` (`other`) | Are you sure you want to restore %d files? | 本当に %d ファイルを復元しますか? |
| 320 | `failed_to_resize_images` (`one`) | Failed to resize %d image | %d個の画像のサイズを変更できませんでした *(CLDR `other`)* |
| 321 | `failed_to_resize_images` (`other`) | Failed to resize %d images | %d個の画像のサイズを変更できませんでした |
| 322 | `search` *(fossify-commons)* | *(inherited from commons)* | 検索 |
| 323 | `change_date_and_time_format` *(fossify-commons)* | Change date and time format | 日付と時刻の表示形式を変更 |
| 324 | `password_protect_file_deletion` *(fossify-commons)* | Password protect file deletion and moving | ファイルの削除と移動をパスワードで保護する |
| 325 | `keep_last_modified` *(fossify-commons)* | Keep old last-modified value at file operations | ファイルの操作時、既存の最終更新日を保持する (更新日を更新しない) |
| 326 | `clear_cache` *(fossify-commons)* | Clear cache | キャッシュのクリア |
| 327 | `export_settings` *(fossify-commons)* | Export settings | 設定をエクスポート |
| 328 | `import_settings` *(fossify-commons)* | Import settings | 設定をインポート |
| 329 | `temporarily_show_excluded` *(fossify-commons)* | Temporarily show excluded | 除外項目を一時的に表示 |
| 330 | `change_view_type` *(fossify-commons)* | Change view type | 表示形式の変更 |
| 331 | `contributors` *(fossify-commons)* | Contributors | 貢献者 |
| 332 | `known_issues` *(fossify-commons)* | Known issues | 既知の問題 |
| 333 | `donate_to_fossify` *(fossify-commons)* | Donate to Fossify | Fossifyに寄付 |
| 334 | `privacy_policy` *(fossify-commons)* | Privacy policy | プライバシー ポリシー |
| 335 | `create_shortcut` *(fossify-commons)* | Create shortcut | ショートカットを作成 |
| 336 | `print` *(fossify-commons)* | Print | 印刷 |
| 337 | `search_files` *(fossify-commons)* | Search files | ファイルを検索 |
| 338 | `search_folders` *(fossify-commons)* | Search folders | フォルダーを検索 |
| 339 | `pull_down_to_open_vault` | Keep pulling down for Encrypted Vault | 暗号化された保管庫を開くには引き下げ続けてください |
| 340 | `keep_holding_to_open_vault` | Keep holding to open Encrypted Vault | 暗号化された保管庫を開くには押し続けてください |
| 341 | `release_to_open_vault` | Release to open Encrypted Vault | 指を離して暗号化された保管庫を開く |
| 342 | `manage_excluded_folders` *(fossify-commons)* | Manage excluded folders | 除外フォルダの管理 |
| 343 | `exclude` *(fossify-commons)* | Exclude | 除外する |
| 344 | `exclude_folder` *(fossify-commons)* | Exclude folder | フォルダを除外 |
| 345 | `lock_folder` *(fossify-commons)* | Lock folder | フォルダに鍵をかける |
| 346 | `change_cover_image` *(fossify-commons)* | Change cover image | カバー画像を変更 |
| 347 | `select_photo` *(fossify-commons)* | Select photo | 写真を選択 |
| 348 | `use_default` *(fossify-commons)* | Use default | デフォルトを使用 |
| 349 | `column_count` *(fossify-commons)* | Column count | 列数 |
| 350 | `open_the_recycle_bin` *(fossify-commons)* | Open the recycle bin | ごみ箱を開く |
| 351 | `grid` *(fossify-commons)* | Grid | グリッド |
| 352 | `list` *(fossify-commons)* | List | リスト |
| 353 | `resize` *(fossify-commons)* | Resize | リサイズ |
| 354 | `version_placeholder` *(fossify-commons)* | Version %s | バージョン %s |
| 355 | `number_of_items` *(fossify-commons)* | Number of items | 項目数 |
| 356 | `random` *(fossify-commons)* | Random | ランダム |
| 357 | `shuffle` | Shuffle | シャッフル |
| 358 | `stop_showing_excluded` *(fossify-commons)* | Stop showing excluded | 除外した項目を表示しない |
| 359 | `copy_to_clipboard` *(fossify-commons)* | Copy to clipboard | クリップボードにコピー |
| 360 | `encrypt` | Encrypt | 暗号化 |
| 361 | `decrypt` | Decrypt | 復号 |
| 362 | `encrypt_album` | Encrypt album | アルバムを暗号化 |
| 363 | `decrypt_album` | Decrypt album | アルバムを復号 |
| 364 | `create_new_encrypted_virtual_album` | Create new encrypted virtual album | 新しい暗号化仮想アルバムを作成 |
| 365 | `new_album` | New album | 新しいアルバム |
| 366 | `filename_without_zip` | Filename (without .zip) | ファイル名（.zip を除く） |
| 367 | `favorites_already_exist` | You already have a favorites list | お気に入りリストはすでに存在します |
| 368 | `delete_existing_list` | Delete existing list | 既存のリストを削除 |
| 369 | `merge_lists` | Merge lists | リストを統合 |
| 370 | `confirm` | Confirm | 確認 |
| 371 | `confirm_encrypt_album` | Are you sure you want to encrypt this album? All of its media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | このアルバムを暗号化してもよろしいですか？中のメディアファイルはすべて暗号化され、暗号化された保管庫に移動されます。元のファイルは削除されます。 |
| 372 | `confirm_encrypt_albums` | Are you sure you want to encrypt the selected albums? All of their media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | 選択したアルバムを暗号化してもよろしいですか？中のメディアファイルはすべて暗号化され、暗号化された保管庫に移動されます。元のファイルは削除されます。 |
| 373 | `confirm_encrypt_file` | Are you sure you want to encrypt this file? It will be encrypted and moved to the encrypted vault. The original file will be deleted. | このファイルを暗号化してもよろしいですか？暗号化されて、暗号化された保管庫に移動されます。元のファイルは削除されます。 |
| 374 | `confirm_encrypt_files` | Are you sure you want to encrypt the selected files? They will be encrypted and moved to the encrypted vault. The original files will be deleted. | 選択したファイルを暗号化してもよろしいですか？暗号化されて、暗号化された保管庫に移動されます。元のファイルは削除されます。 |
| 375 | `confirm_encrypt_files_to_vault` | The selected files will be encrypted when they are copied into the encrypted vault. Do you want to continue? | 選択したファイルは、暗号化された保管庫にコピーされるときに暗号化されます。続行しますか？ |
| 376 | `encrypt_decrypt_notifications` | Encrypt / Decrypt | 暗号化 / 復号 |
| 377 | `allow_batch_renaming` | Allow batch renaming when multiple files are selected | 複数のファイルが選択されているときに一括で名前変更を許可 |
| 378 | `keep_original_remove_from_virtual_album` | Keep the original file, remove it only from this virtual album | 元のファイルを保持し、この仮想アルバムからのみ削除する |
| 379 | `keep_original_remove_from_favorites` | Keep the original file, remove it only from favorites | 元のファイルを保持し、お気に入りからのみ削除する |
| 380 | `delete_original_from_favorites_and_virtual_albums` | Delete the original version of a file when it is deleted from favorites and virtual albums | お気に入りおよび仮想アルバムから削除するときに元のファイルも削除する |
| 381 | `cannot_target_encrypted_album` | Encrypted albums cannot be targeted for this operation | この操作では暗号化されたアルバムを対象にできません |
| 382 | `cannot_mix_encrypted_and_unencrypted` | Cannot combine encrypted and unencrypted items in this operation | この操作では暗号化された項目と暗号化されていない項目を混在させることはできません |
| 383 | `lock_album` | Lock album | アルバムをロック |
| 384 | `unlock_album` | Unlock album | アルバムのロックを解除 |
| 385 | `auto_backup_prompt_message` | Do you want your favorites and virtual albums to be regularly written to system storage outside of app memory? This will ensure that you do not lose your favorites list and virtual albums if you uninstall the app. You can manually create a backup from app settings at any time. | お気に入りと仮想アルバムを、アプリメモリ外のシステムストレージに定期的に書き出しますか？これにより、アプリをアンインストールしてもお気に入りリストや仮想アルバムを失わずに済みます。アプリの設定からいつでも手動でバックアップを作成できます。 |
| 386 | `auto_backup_favorites_and_virtual_albums` | Back up favorites and virtual albums to device storage | お気に入りと仮想アルバムを端末のストレージにバックアップする |
| 387 | `encrypt_info_title` | How Encryption Works | 暗号化の仕組み |
| 388 | `encrypt_info_message` | Folder & File Structure:\n• When an unencrypted album is encrypted, it is converted into an encrypted subfolder inside the vault (Secrets).\n• Both the folder name and all file names/contents inside it are encrypted, so nothing is readable outside the app.\n\nIf an Album with the Same Name Already Exists:\n• By default, the app asks whether to create a new album such as Album(1) or merge into the existing encrypted album (configurable in Settings).\n• When merging, you can choose whether source files verified 100% bit-identical to the destination are deleted or kept in the source folder.\n• Different files with the same name (e.g. download.jpg) are kept as download(1).jpg or handled according to your conflict resolution setting. | フォルダーとファイルの構造:\n• 未暗号化のアルバムを暗号化すると、保管庫 (Secrets) 内の暗号化されたサブフォルダーに変換されます。\n• フォルダー名と中のすべてのファイル名・内容が暗号化されるため、アプリ外からは何も読み取れません。\n\n同じ名前のアルバムが既に存在する場合:\n• 既定では、Album(1) のような新しいアルバムを作成するか、既存の暗号化アルバムに統合するかを確認します（設定で変更可能）。\n• 統合時は、宛先とビット単位で100%一致することが確認された元ファイルを削除するか、元のフォルダーに残すかを選択できます。\n• 同名で内容が異なるファイル（例: download.jpg）は download(1).jpg として保持されるか、競合解決の設定に従って処理されます。 |
| 389 | `decrypt_info_title` | How Decryption Works | 復号の仕組み |
| 390 | `decrypt_info_message` | Folder & File Structure:\n• When an encrypted album or file is decrypted, it is restored with its original name and format to the selected destination folder and removed from the encrypted vault.\n\nIf an Album with the Same Name Already Exists:\n• By default, the app asks whether to create a new folder such as Album(1) or merge into the existing destination folder (configurable in Settings).\n• When merging, you can choose whether encrypted copies verified 100% bit-identical to the destination are removed from the vault or kept.\n• Different files with the same name (e.g. download.jpg) are kept as download(1).jpg or handled according to your conflict resolution setting. | フォルダーとファイルの構造:\n• 暗号化されたアルバムまたはファイルを復号すると、元の名前と形式で選択した保存先フォルダーに復元され、暗号化された保管庫から削除されます。\n\n同じ名前のアルバムが既に存在する場合:\n• 既定では、Album(1) のような新しいフォルダーを作成するか、既存の保存先フォルダーに統合するかを確認します（設定で変更可能）。\n• 統合時は、保存先とビット単位で100%一致することが確認された暗号化コピーを保管庫から削除するか残すかを選択できます。\n• 同名で内容が異なるファイル（例: download.jpg）は download(1).jpg として保持されるか、競合解決の設定に従って処理されます。 |
| 391 | `vault_location` | Vault location | 保管庫の場所 |
| 392 | `vault_location_standard_summary` | Standard (Internal Storage) | 標準（内部ストレージ） |
| 393 | `vault_location_unavailable_summary` | External drive disconnected (%s) | 外部ドライブが切断されています (%s) |
| 394 | `select_vault_location` | Select vault location | 保管庫の場所を選択 |
| 395 | `vault_setup_action_title` | Encrypted vault | 暗号化された保管庫 |
| 396 | `create_new_vault_option` | Create new vault | 新しい保管庫を作成 |
| 397 | `use_existing_vault_option` | Use existing vault | 既存の保管庫を使用 |
| 398 | `where_to_create_vault_title` | Where should the vault be created? | 保管庫をどこに作成しますか？ |
| 399 | `vault_location_default_short` | Default location | 既定の場所 |
| 400 | `vault_location_custom_short` | Custom Location | カスタムの場所 |
| 401 | `no_existing_vault_at_location` | No existing encrypted vault (.vault) was found in the selected folder. | 選択したフォルダーに既存の暗号化された保管庫 (.vault) が見つかりませんでした。 |
| 402 | `unlock_existing_vault_at_location_prompt` | An existing encrypted vault was found at %s. Do you want to unlock and use it? Tap No to pick a different folder. | %s に既存の暗号化された保管庫が見つかりました。ロックを解除して使用しますか？別のフォルダーを選ぶには「いいえ」をタップしてください。 |
| 403 | `vault_location_standard_option` | Standard location (Internal Storage > Calypso Gallery > Secrets) | 標準の場所 (内部ストレージ > Calypso Gallery > Secrets) |
| 404 | `vault_location_custom_option` | Custom / External location (USB drive, SD card, or custom folder) | カスタム / 外部の場所（USB ドライブ、SD カード、またはカスタムフォルダー） |
| 405 | `external_vault_unavailable_title` | External vault unavailable | 外部保管庫を利用できません |
| 406 | `external_vault_unavailable_prompt` | Your configured vault location (%s) is currently disconnected or unavailable. Plug in the drive and tap Retry, or choose another vault location. | 設定された保管庫の場所 (%s) は現在切断されているか利用できません。ドライブを接続して「再試行」をタップするか、別の保管庫の場所を選択してください。 |
| 407 | `retry` | Retry | 再試行 |
| 408 | `change_vault_location` | Change location | 場所を変更 |
| 409 | `existing_vault_detected_at_location` | Existing vault found at %s. Enter its password or PIN to unlock it. | %s に既存の保管庫が見つかりました。ロックを解除するにはパスワードまたは PIN を入力してください。 |
| 410 | `create_new_vault_at_location_prompt` | No vault exists at %s. Do you want to create a new encrypted vault at this location? | %s に保管庫が存在しません。この場所に新しい暗号化された保管庫を作成しますか？ |
| 411 | `usb_direct_filesystem_required` | This USB path is only exposed through Android\'s virtual OTG provider and cannot be accessed directly as a filesystem path. Please choose a mounted storage directory or a folder on internal storage / SD card. | この USB パスは Android の仮想 OTG プロバイダー経由でのみ公開されており、ファイルシステムパスとして直接アクセスできません。マウントされたストレージディレクトリ、または内部ストレージ / SD カード上のフォルダーを選択してください。 |
| 412 | `vault_location_not_writable` | The selected folder does not exist or is not writable on this device. | 選択したフォルダーは存在しないか、このデバイスでは書き込みできません。 |
| 413 | `vault_location_not_readable` | The selected folder does not exist or is not readable on this device. | 選択したフォルダーは存在しないか、このデバイスでは読み取れません。 |
| 414 | `sync_vault_from_external` | Import newly encrypted media filed to encrypted section | CLI から暗号化メディアをインポート |
| 415 | `sync_vault_select_source_prompt` | How Encrypted Media Import Works:\n\n1. Unified Secrets Folder:\nKeep a folder named Secrets on your external drive (USB / SD). You can either keep a full backup copy of your vault there, or place only your vault\'s .vault header file inside it.\n\n2. Encrypting New Media (PC / Tails CLI):\nThe offline CLI tool automatically saves new encrypted files directly into Secrets. Files placed at the root go to Default Secret Album; files in subfolders go to matching encrypted albums (or create new ones).\n\n3. Bit-by-Bit Smart Sync:\nSelect your external drive or Secrets folder next. The app compares the external drive with your phone vault in RAM:\n• Only new files and albums are imported.\n• Bit-identical files already in your vault are skipped.\n• Different files with the same name (e.g. download.jpg) are safely imported as download(1).jpg. | 暗号化メディアのインポートの仕組み:\n\n1. 統合 Secrets フォルダー:\n外部ドライブ (USB / SD) に Secrets という名前のフォルダーを用意します。そこに保管庫の完全なバックアップコピーを置くか、保管庫の .vault ヘッダーファイルのみを配置できます。\n\n2. 新しいメディアの暗号化 (PC / Tails CLI):\nオフライン CLI ツールは、新しい暗号化ファイルを自動的に Secrets に直接保存します。ルートに置かれたファイルは Default Secret Album に、サブフォルダー内のファイルは対応する暗号化アルバムに入ります（存在しない場合は新規作成）。\n\n3. ビット単位のスマート同期:\n次に外部ドライブまたは Secrets フォルダーを選択してください。アプリは RAM 上で外部ドライブとスマートフォンの保管庫を比較します:\n• 新しいファイルとアルバムのみがインポートされます。\n• 保管庫に既にあるビット単位で同一のファイルはスキップされます。\n• 同名で内容が異なるファイル（例: download.jpg）は安全に download(1).jpg としてインポートされます。 |
| 416 | `conflict_handling_mode_label` | File conflict & duplicate resolution | ファイルの競合と重複の解決 |
| 417 | `conflict_handling_auto_short` | Automatic (Experimental — Auto-skip identical, rename different as (1)) | 完全自動（既定 — 同一はスキップ、異なる場合は (1) に名前変更） |
| 418 | `conflict_handling_smart_dialog_short` | Smart dialog (Auto-skip identical, ask when content differs) | スマートダイアログ（同一は自動スキップ、内容が異なる場合のみ確認） |
| 419 | `album_crypto_conflict_mode_label` | Album conflict (Encrypt / Decrypt) | アルバムの競合（暗号化 / 復号） |
| 420 | `album_crypto_conflict_ask` | Always ask (Default) | 毎回確認（既定） |
| 421 | `album_crypto_conflict_merge` | Merge into existing album | 既存のアルバムに統合 |
| 422 | `album_crypto_conflict_merge_delete_identical` | Merge into existing album and automatically delete identical sources | 既存のアルバムに統合して同一の元ファイルを自動削除 |
| 423 | `album_crypto_conflict_rename` | Create new album as (1) | 新しいアルバムを (1) として作成 |
| 424 | `album_crypto_conflict_title` | Album Already Exists | アルバムは既に存在します |
| 425 | `album_crypto_conflict_message` | An album named \"%s\" already exists at the destination. How would you like to proceed? | 保存先に「%s」という名前のアルバムが既に存在します。どのように処理しますか？ |
| 426 | `album_crypto_conflict_message_multiple` | %1$d selected albums already exist at the destination. How would you like to proceed?\n\nYour choice will apply to all conflicting albums. To choose separately per album, select and process them one by one. | 選択した %1$d 個のアルバムが保存先に既に存在します。どのように処理しますか？\n\nこの選択は競合するすべてのアルバムに適用されます。アルバムごとに個別に選択するには、1つずつ選択して処理してください。 |
| 427 | `album_crypto_conflict_rename_option` | Create new album as \"%s\" | 新しいアルバムを「%s」として作成 |
| 428 | `album_crypto_conflict_rename_option_multiple` | Create new albums as (1), (2), … | 新しいアルバムを (1), (2), … として作成 |
| 429 | `album_crypto_conflict_merge_option` | Merge into existing \"%s\" | 既存の「%s」に統合 |
| 430 | `album_crypto_conflict_merge_option_multiple` | Merge into existing albums | 既存のアルバムに統合 |
| 431 | `album_crypto_delete_identical_source` | Delete identical source files (100% bit-by-bit verified) | 同一の元ファイルを削除（ビット単位で100%検証済み） |
| 432 | `album_crypto_dont_ask_again` | Remember album conflict choice (merge or rename) | アルバムの競合の選択を記憶する（統合または名前変更） |
| 433 | `album_crypto_merge_identical_title` | Identical Files During Merge | 統合時の同一ファイル |
| 434 | `album_crypto_merge_identical_message` | When merging into \"%1$s\", if any files are already 100%% bit-identical at the destination, should their source copies be deleted or kept? | 「%1$s」に統合する際、保存先にビット単位で100%%同一のファイルが既にある場合、元のコピーを削除しますか、それとも保持しますか？ |
| 435 | `album_crypto_merge_identical_message_multiple` | When merging the %1$d conflicting albums, if any files are already 100%% bit-identical at the destination, should their source copies be deleted or kept? | 競合する %1$d 個のアルバムを統合する際、保存先にビット単位で100%%同一のファイルが既にある場合、元のコピーを削除しますか、それとも保持しますか？ |
| 436 | `delete_identical_sources` | Delete identical | 同一を削除 |
| 437 | `keep_identical_sources` | Keep in source | 元に保持 |
| 438 | `fast_large_duplicate_check_label` | Fast duplicate check for large files (>8 MB) | 大容量ファイルの高速重複チェック (>8 MB) |
| 439 | `fast_large_duplicate_check_hint` | Crucial for transfers containing large files: checks 5 blocks (1.25 MB total) per file instead of reading whole files, speeding up duplicate checks dozens of times. | 大容量ファイルを含む転送に重要: ファイル全体を読み取る代わりに1ファイルあたり5ブロック（合計1.25 MB）を確認し、重複チェックを数十倍高速化します。 |
| 440 | `fast_large_duplicate_check_info_title` | Fast Duplicate Check (>8 MB) | 高速重複チェック (>8 MB) |
| 441 | `conflict_handling_explanation_title` | File Conflict & Duplicate Resolution | ファイルの競合と重複の解決 |
| 442 | `conflict_handling_explanation_message` | When copying, moving, encrypting, or decrypting files and albums (including album merges), if a file with the same name already exists at the destination:\n\n• Standard dialog (Default): Checks filename only (like the original Fossify Gallery app) without comparing file contents, and asks you to Skip / Overwrite / Keep both.\n• Smart dialog: Checks file sizes and bit-by-bit contents in RAM (encrypted files stay in memory), auto-skips identical duplicates, and asks with side-by-side previews (Skip / Overwrite / Keep both) only when content differs.\n• Automatic (Experimental): Auto-skips identical duplicates and automatically keeps both different files by naming the incoming file download(1).jpg, download(2).jpg. | ファイルやアルバムのコピー、移動、暗号化、または復号時に、同じ名前のファイルが保存先に既に存在する場合:\n\n1. ビット単位の重複チェック (RAM):\nアプリは最初にファイルサイズを確認し、一致する場合は RAM 上でファイル内容をビット単位で比較します（暗号化ファイルはディスクに平文を書き込まずメモリ内で確認されます）。コピー/移動時は同一ファイルがスキップされ、元ファイルは元のフォルダーにそのまま残ります。暗号化/復号時にアルバムを統合する場合は、ビット単位で100%同一の元ファイルを削除するか残すかを選択できます。\n\n2. 同名で異なる内容（例: 2つの異なる download.jpg ファイル）:\n• 完全自動（既定）: 新しいファイルを download(1).jpg、download(2).jpg のように名前変更して、操作を中断せず両方のファイルを保持します。\n• スマートダイアログ: ビット単位で同一のファイルは引き続き静かにスキップし、名前が同じで内容が異なる場合のみ確認します（スキップ / 上書き / 両方保持）。 |
| 443 | `syncing_vault_progress` | Syncing vault from external drive… | 外部ドライブから保管庫を同期中… |
| 444 | `vault_sync_summary_message` | Vault sync complete:\n• Copied: %1$d\n• Renamed (name conflict): %2$d\n• Skipped (identical): %3$d\n• Failed: %4$d | 保管庫の同期が完了しました:\n• コピー: %1$d\n• 名前変更（名前の競合）: %2$d\n• スキップ（同一）: %3$d\n• 失敗: %4$d |
| 445 | `vault_sync_nothing_to_sync` | No pending CLI-encrypted media (.pending_sync) was found in the selected folder. | 選択したフォルダーに保留中の CLI 暗号化メディア (.pending_sync) が見つかりませんでした。 |
| 446 | `vault_sync_same_folder_error` | The selected source folder is the same as your active vault. | 選択した参照元フォルダーは、現在有効な保管庫と同じです。 |
| 447 | `use_for_this_album` | Use for this album only | このアルバムのみに適用 |
| 448 | `crypto_duplicate_source_mode_label` | When a file already exists in destination during encrypt/decrypt | 暗号化/復号時に同じファイルが保存先フォルダにすでに存在する場合 |
| 449 | `crypto_duplicate_source_ask` | Always ask (Default) | 毎回確認する（デフォルト） |
| 450 | `crypto_duplicate_source_keep` | Keep file in source folder | 元のフォルダのファイルを残す |
| 451 | `crypto_duplicate_source_delete` | Delete file from source folder | 元のフォルダのファイルを削除する |
| 452 | `crypto_duplicate_source_title` | File Already Exists in Destination | 保存先に同じファイルがすでに存在します |
| 453 | `crypto_duplicate_source_encrypt_message` | Some of the files you are encrypting already exist in the destination folder. What should happen to the files in the source folder? | 暗号化しようとしているファイルの一部が保存先フォルダにすでに存在します。元のフォルダのファイルをどうしますか？ |
| 454 | `crypto_duplicate_source_decrypt_message` | Some of the files you are decrypting already exist in the destination folder. What should happen to the files in the source folder? | 復号しようとしているファイルの一部が保存先フォルダにすでに存在します。元のフォルダのファイルをどうしますか？ |
| 455 | `crypto_duplicate_source_encrypt_keep_warning` | Warning: Choosing \"Keep\" for unencrypted files will leave the source file unencrypted on disk. | 警告：未暗号化ファイルで「残す」を選択すると、元のファイルは暗号化されないままディスク上に残ります。 |
| 456 | `crypto_duplicate_source_always_ask` | Always ask | 毎回確認する |
| 457 | `export_encrypted_vault` | Export encrypted vault | 暗号化された保管庫をエクスポート |
| 458 | `export_vault_only_header` | Export folder containing only .vault file | .vault ファイルのみを含むフォルダーをエクスポート |
| 459 | `export_vault_entire_folder` | Export entire folder | フォルダー全体をエクスポート |
| 460 | `export_vault_only_header_info` | To add media items encrypted on other devices using the supported CLI tool to the encrypted vault on your phone, you only need to use a folder named Secrets containing the .vault file. | 対応する CLI ツールを使用して他のデバイスで暗号化したメディア項目をスマートフォン上の暗号化された保管庫に追加するには、.vault ファイルを含む Secrets という名前のフォルダーを使用するだけで十分です。 |
| 461 | `exporting_vault_progress` | Exporting encrypted vault… | 暗号化された保管庫をエクスポート中… |
| 462 | `vault_export_same_folder_error` | The export destination cannot be the active vault folder or inside it. | エクスポート先に現在使用中の保管庫フォルダーまたはそのサブフォルダーを指定することはできません。 |
| 463 | `conflict_handling_standard_dialog_summary` | Standard dialog | 標準ダイアログ |
| 464 | `conflict_handling_smart_dialog_summary` | Smart dialog | スマートダイアログ |
| 465 | `conflict_handling_auto_summary` | Automatic (Experimental) | 自動（実験的） |
| 466 | `conflict_handling_standard_dialog_short` | Standard dialog (Default — Checks filename only, like original Fossify Gallery) | 標準ダイアログ（既定 — 元の Fossify Gallery と同様、ファイル名のみを確認） |
| 467 | `skipped_encrypt_files_mode_label` | Manage skipped files during encryption | 暗号化時にスキップされたファイルを管理 |
| 468 | `skipped_encrypt_files_info_message` | Choose whether files skipped in the source folder during encryption should be kept or deleted for security. This choice also applies to album conflicts during album encryption (see setting below) when the source album is merged into an existing album at the destination. | 暗号化時に送信元フォルダーでスキップされたファイルを保持するか、セキュリティのため削除するかを選択します。この選択は、アルバムの暗号化時に送信元アルバムが宛先の既存のアルバムに統合される場合のアルバムの競合（下の設定を参照）にも適用されます。 |
| 469 | `skipped_crypto_files_ask` | Always ask (Default) | 毎回確認（既定） |
| 470 | `skipped_crypto_files_keep` | Keep | 保持 |
| 471 | `skipped_crypto_files_delete` | Delete | 削除 |
| 472 | `skipped_crypto_files_encrypt_message` | The files you skipped were not transferred. For your privacy, you can delete these files. | スキップしたファイルは転送されませんでした。プライバシーのため、これらのファイルを削除できます。 |
| 473 | `skipped_crypto_files_decrypt_message` | The files you skipped were not transferred. You can delete these files. | スキップしたファイルは転送されませんでした。これらのファイルを削除できます。 |
| 474 | `skipped_crypto_files_identical_encrypt_message` | Files that already exist in the destination folder were not transferred. For your privacy, you can delete these files. | 宛先フォルダーにすでに存在するファイルは転送されませんでした。プライバシーのため、これらのファイルを削除できます。 |
| 475 | `skipped_crypto_files_identical_decrypt_message` | Files that already exist in the destination folder were not transferred. You can delete these files. | 宛先フォルダーにすでに存在するファイルは転送されませんでした。これらのファイルを削除できます。 |
| 476 | `skipped_crypto_files_both_encrypt_message` | The files you skipped and the files that already exist in the destination folder were not transferred. For your privacy, you can delete these files. | スキップしたファイルと宛先フォルダーにすでに存在するファイルは転送されませんでした。プライバシーのため、これらのファイルを削除できます。 |
| 477 | `skipped_crypto_files_both_decrypt_message` | The files you skipped and the files that already exist in the destination folder were not transferred. You can delete these files. | スキップしたファイルと宛先フォルダーにすでに存在するファイルは転送されませんでした。これらのファイルを削除できます。 |
| 478 | `skipped_crypto_files_always_ask` | Always ask | 毎回確認 |
| 479 | `delete_skipped_files_confirm_title` | Are you sure? | よろしいですか？ |
| 480 | `delete_skipped_files_confirm_message` | Your files will be deleted! | ファイルが削除されます！ |
| 481 | `date_tie_break_label` | When content is identical but dates differ | 内容は同一だが日付が異なる場合 |
| 482 | `date_tie_break_keep_target` | Keep the existing file\'s date (default) | 既存ファイルの日付を保持（既定） |
| 483 | `date_tie_break_use_source` | Use the source file\'s date | 送信元ファイルの日付を使用 |
| 484 | `date_tie_break_ask` | Always ask | 毎回確認 |
| 485 | `date_tie_break_hint` | Applies only to Smart dialog and Automatic mode. The Standard dialog is not affected. | スマートダイアログと自動モードのみに適用されます。標準ダイアログは影響を受けません。 |
| 486 | `date_tie_break_ask_info_title` | Why "Always ask" disables Smart dialog and Automatic mode | \"毎回確認\"がスマートダイアログと自動モードを無効にする理由 |
| 487 | `date_tie_break_ask_info_message` | Smart dialog and Automatic mode exist for one reason: to skip files whose content is already present, so you only see the files that really need your attention — same name, different content. That is exactly the case where comparing thumbnails can reveal bit-rot.\n\nIf you choose "Always ask" here, every identical file with a different date comes back to you as a conflict. In Smart dialog you will see two thumbnails of the very same picture, with nothing to verify, and you can decide there. Automatic (Experimental) mode never shows a dialog, so "Always ask" is not available there — selecting Automatic falls back to "Keep the existing file's date".\n\nUse this option only if you deliberately want to review every date difference, for example to fix a wrong date by hand.\n\nNote: when content is bit-identical, the EXIF capture date is identical too. A date difference means one side lost its EXIF data and fell back to the file modification date (the copy date). | スマートダイアログと自動モードは、内容がすでに存在するファイルをスキップし、本当に注意が必要なファイル（名前は同じだが内容が異なるもの）だけを表示するために存在します。まさにこのような場合に、サムネイルの比較によってビットロット（データの劣化）を検出できます。\n\nここで\"毎回確認\"を選択すると、日付が異なる同一ファイルがすべて競合として再び表示されます。スマートダイアログでは同じ画像のサムネイルが2つ表示され、確認するものが何もありませんが、その場で判断できます。自動（実験的）モードではダイアログを一切表示しないため、\"毎回確認\"は使用できません。自動モードを選択すると\"既存ファイルの日付を保持（既定）\"にフォールバックします。\n\nこのオプションは、誤った日付を手動で修正するなど、すべての日付の違いを意図的に確認したい場合にのみ使用してください。\n\n注: 内容がビット単位で同一の場合、EXIF の撮影日も同一です。日付が異なるということは、いずれかの側が EXIF データを失い、ファイルの更新日時（コピーした日時）にフォールバックしたことを意味します。 |
| 488 | `smart_conflict_counter` | %1$s (%2$d / %3$d) | %1$s (%2$d / %3$d) |
| 489 | `smart_conflict_incoming_label` | Incoming (New) | 受信（新規） |
| 490 | `smart_conflict_existing_label` | Existing (Target) | 既存（宛先） |
| 491 | `smart_conflict_corrupted_unreadable` | Corrupted or unreadable | 破損または読み取り不能 |
| 492 | `smart_conflict_integrity_failed` | Integrity check failed (Bit-rot / Corrupted) | 整合性チェックに失敗（ビットロット / 破損） |
| 493 | `smart_conflict_tap_to_preview` | Tap either thumbnail to inspect full-size preview | いずれかのサムネイルをタップして全サイズのプレビューを確認 |
| 494 | `smart_conflict_comparing` | Comparing file contents and dates… | ファイルの内容と日付を比較しています… |
| 495 | `smart_conflict_content_differs_warning` | File contents differ.\nIf these two files were supposed to be the same, data corruption may have occurred. Keeping both and inspecting them is recommended. | ファイルの内容が異なります。\nこの2つのファイルが同じであるはずだった場合、データの破損が発生している可能性があります。両方を保持して確認することをお勧めします。 |
| 496 | `smart_conflict_date_taken_differs` | File contents are identical, creation (date taken) timestamps differ. | ファイルの内容は同一ですが、作成日時（撮影日時）が異なります。 |
| 497 | `smart_conflict_modified_date_differs` | File contents are identical, modification dates differ. | ファイルの内容は同一ですが、更新日時が異なります。 |
| 498 | `smart_conflict_both_dates_differ` | File contents are identical, creation and modification dates differ. | ファイルの内容は同一ですが、作成日時と更新日時が異なります。 |
| 499 | `smart_conflict_meta_date_taken` | Taken: %s | 撮影日時: %s |
| 500 | `smart_conflict_meta_date_modified` | Modified: %s | 更新日時: %s |
| 501 | `album_type_icons` | Album type icons | アルバムの種類のアイコン |
| 502 | `virtual_folders` | Virtual albums | 仮想アルバム |
| 503 | `favorites_album` | Favorites | お気に入り |
| 504 | `excluded_folders_album` | Excluded folders | 除外されたフォルダー |
| 505 | `hidden_folders_album` | Hidden folders | 非表示フォルダー |
| 506 | `temporarily_show_hidden_folders` | Temporarily show hidden folders | 非表示フォルダーを一時的に表示 |
| 507 | `stop_showing_hidden_folders` | Stop showing hidden folders | 非表示フォルダーの表示を停止 |
| 508 | `unexclude` | Stop excluding | 除外を解除 |
| 509 | `hide_folder` | Hide | 非表示にする |
| 510 | `unhide_folder` | Unhide | 再表示 |
| 511 | `about_title` | About Calypso Gallery | Calypso Gallery について |
| 512 | `about_version` | Version %1$s (%2$s) | バージョン %1$s (%2$s) |
| 513 | `about_fork_description` | Calypso Gallery is a security-hardened, independently maintained personal fork of Fossify Gallery. Thanks to the original authors and contributors of Fossify Gallery. It is an unofficial project and is not affiliated with, endorsed by, or supported by the Fossify organization. | Calypso Gallery は、Fossify Gallery の独立して保守された非公式の個人フォークです。Fossify とは提携しておらず、Fossify によって推奨もされていません。 |
| 514 | `about_modification_notice` | This distribution is a modified version of Fossify Gallery v1.14.2 (2688360, 07-11-2026). All fork-specific modifications are documented in FORK_CHANGELOG.md. | この配布物は Fossify Gallery の改造版です。このフォークはアップストリームのコミット 054228be2da275396eb03d2b9002807b5055afac (1.14.1-5-g054228be2) から作成されており、フォーク固有のすべての変更は FORK_CHANGELOG.md に記載されています。 |
| 515 | `about_license_heading` | License | ライセンス |
| 516 | `about_gpl_notice` | This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 3 of the License.\n\nThis program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.\n\nYou should have received a copy of the GNU General Public License along with this program. If not, use the license action below or visit https://www.gnu.org/licenses/. | 本プログラムはフリーソフトウェアです。フリーソフトウェア財団が公開する GNU General Public License（バージョン3）の条件に基づき、再配布および／または改変することができます。\n\n本プログラムは有用であることを願って配布されますが、無保証です。商品性および特定目的への適合性の黙示的保証を含む、いかなる保証もありません。詳細は GNU General Public License をご覧ください。\n\n本プログラムとともに GNU General Public License の写しを受け取っているはずです。受け取っていない場合は、下のライセンスの操作を実行するか、https://www.gnu.org/licenses/ にアクセスしてください。 |
| 517 | `about_read_gpl_license` | Read the GNU GPL v3 license | GNU GPL v3 ライセンスを読む |
| 518 | `about_read_tink_license` | Read the Tink Apache 2.0 license and attribution | Tink の Apache 2.0 ライセンスと帰属表示を読む |
| 519 | `about_source_heading` | Source code | ソースコード |
| 520 | `about_corresponding_source_notice` | The complete corresponding source for this fork is kept in the fork repository. Tagged releases also have a matching source archive. The repository contains the build scripts, dependency declarations, and complete fork history needed to inspect and rebuild the project. | このフォークの完全な対応ソースはフォークのリポジトリに保管されています。タグ付けされたリリースには、対応するソースアーカイブもあります。リポジトリには、プロジェクトの調査と再ビルドに必要なビルドスクリプト、依存関係の宣言、および完全なフォーク履歴が含まれています。 |
| 521 | `about_fork_source` | Fork source code and corresponding source | フォークのソースコードと対応ソース |
| 522 | `about_release_sources` | Release source archives | リリースのソースアーカイブ |
| 523 | `about_fork_issues` | Fork issue tracker | フォークのイシュートラッカー |
| 524 | `about_upstream_source` | Upstream Fossify Gallery source code | アップストリームの Fossify Gallery ソースコード |
| 525 | `about_upstream_project` | Upstream Fossify project | アップストリームの Fossify プロジェクト |
| 526 | `about_help_heading` | Help and legal information | ヘルプと法的情報 |
| 527 | `about_faq` | Frequently asked questions | よくある質問 |
| 528 | `about_third_party_licenses` | Third-party licenses | サードパーティのライセンス |
| 529 | `gpl_license_title` | GNU General Public License v3 | GNU General Public License v3 |
| 530 | `tink_license_title` | Tink license and attribution | Tink のライセンスと帰属表示 |
| 531 | `fork_disclaimer_title` | Fork notice | フォークに関する通知 |
| 532 | `fork_disclaimer_message` | This application is not developed by the Fossify team. Calypso Gallery is an independently maintained, unofficial fork of Fossify Gallery; the original developer is referenced solely for attribution. It is not affiliated with, endorsed by, or supported by Fossify.\n\nBy continuing you acknowledge this notice. Fork-specific changes are documented in FORK_CHANGELOG.md. | このアプリケーションは Fossify チームによって開発されたものではありません。Calypso Gallery は Fossify Gallery の独立して保守された非公式フォークであり、元の開発者への言及は帰属表示のみを目的としています。Fossify とは提携、推奨、サポートの関係にはありません。\n\n続行することで、この通知を確認したものとみなされます。フォーク固有の変更は FORK_CHANGELOG.md に記載されています。 |
| 533 | `fork_disclaimer_acknowledge` | I understand | 理解しました |
| 534 | `argon2_profile_title` | Brute-Force resistance | 総当たり攻撃への耐性 |
| 535 | `brute_force_mode_title` | Calibration mode | 調整モード |
| 536 | `brute_force_mode_time` | Via vault unlock time | 保管庫のロック解除時間を使用 |
| 537 | `brute_force_mode_presets` | Via ready-made modes | 用意されたモードを使用 |
| 538 | `brute_force_time_title` | Targeted vault unlock time | 設定する保管庫のロック解除時間 |
| 539 | `brute_force_time_hint` | Select the time spent during opening the encrypted vault. Keeping this value high provides extra security by making brute-force attacks harder. | 暗号化された保管庫を開く際にかかる時間を選択してください。この値を高くすると、総当たり攻撃が難しくなり、セキュリティが強化されます。 |
| 540 | `brute_force_time_min_hint` | The lower bound is this device's safe standard time. | 下限はこの端末の安全な標準時間です。 |
| 541 | `brute_force_time_value_device` | %1$.1f s (on this device) | %1$.1f 秒（この端末） |
| 542 | `brute_force_time_out_of_range` | This time is above the selectable maximum (10 s). Tap to change it. | この時間は選択可能な上限（10 秒）を超えています。変更するにはタップしてください。 |
| 543 | `brute_force_time_value` | %1$.1f s | %1$.1f 秒 |
| 544 | `brute_force_actual_params` | Stored: %1$d MiB / %2$d rounds | 保存済み: %1$d MiB / %2$d ラウンド |
| 545 | `brute_force_clamped` |  (clamped) |  (範囲内に制限されました) |
| 546 | `argon2_profile_low` | Low-end device (not recommended) | ローエンド端末（非推奨） |
| 547 | `argon2_profile_default` | Default | 既定 |
| 548 | `argon2_profile_high` | High security | 高いセキュリティ |
| 549 | `argon2_profile_hint` | If you don\'t know what you\'re doing, it is recommended to keep the default setting. This only affects newly created vaults and password changes. | 設定内容がわからない場合は、既定の設定を保持することをお勧めします。これは新しく作成する保管庫とパスワードの変更にのみ影響します。 |
| 550 | `argon2_profile_current` | Current: %s | 現在: %s |
| 551 | `show_pin_dots` | Show a dot for each PIN digit | PIN入力時に各桁をドットで表示する |

## Settings Headers, Font Options & File Encryption Info

Added/overridden in `values-ja/strings.xml` for Settings section headers, font picker options (`Monospace` standardized), and file-only encryption info dialog:

| # | Key (`name`) | English | Japanese (`values-ja/strings.xml`) |
|--:|---|---|---|
| 1 | `application_settings` | Application | アプリケーション |
| 2 | `video_playback_settings` | Video playback | 動画再生 |
| 3 | `thumbnails_and_scrolling_settings` | Thumbnails & scrolling | サムネイルとスクロール |
| 4 | `media_viewer_settings` | Media viewer | メディアビューアー |
| 5 | `security_and_vault_settings` | Security & encrypted vault | セキュリティと暗号化された保管庫 |
| 6 | `font_system_default` | System default | システムのデフォルト |
| 7 | `font_monospace` | Monospace | Monospace |
| 8 | `encrypt_files_info_message` | Folder & File Structure:\n• When an unencrypted album is encrypted, it is converted into an encrypted subfolder inside the vault (Secrets).\n• Both the folder name and all file names/contents inside it are encrypted, so nothing is readable outside the app. | フォルダーとファイルの構造:\n• 未暗号化のアルバムを暗号化すると、保管庫 (Secrets) 内の暗号化されたサブフォルダーに変換されます。\n• フォルダー名と中のすべてのファイル名・内容が暗号化されるため、アプリ外からは何も読み取れません。 |

## Missing UI strings added (this revision)

`org.fossify:commons:7.1.3` does not provide these keys for Japanese, and `unlocking_encrypted_vault` was missing from the app-owned resources, so local overrides were added to `values-ja/strings.xml`.

| Key (`name`) | English source | Japanese | Notes |
|---|---|---|---|
| `search_in_placeholder` | Search in %s | %s 内を検索 | fossify-commons override; `%s` = album/folder name |
| `open_biometric_dialog` | Open biometric ID verification dialog | 生体認証IDの確認ダイアログを開く | fossify-commons override; Biometrics tab of the inherited security dialog |
| `authenticate` | Authenticate | 認証 | fossify-commons override; system biometric prompt title |
| `unlocking_encrypted_vault` | Unlocking encrypted vault… | 暗号化された金庫を開錠しています… | App-owned key; consistent with existing vault terminology |

## Appearance — Custom Colors & Saved Themes (9 keys)

Added to `values-ja/strings.xml` for the Appearance settings **Custom colors** section and **Saved themes** workflow (English source strings in `values/strings.xml` were also completed):

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `custom_colors` | Custom colors | カスタムカラー | Appearance settings section header |
| 2 | `saved_themes` | Saved themes | 保存済みのテーマ | Appearance settings row and dialog title |
| 3 | `save_theme` | Save theme | テーマを保存 | Positive dialog button to save current custom theme |
| 4 | `theme_saved` | Theme saved | テーマを保存しました | Toast shown after saving a custom theme |
| 5 | `theme_already_saved` | This theme is already saved | このテーマはすでに保存されています | Toast shown when the current palette is already saved or built-in |
| 6 | `no_saved_themes` | No saved themes yet | 保存済みのテーマはまだありません | Empty state message in the Saved themes dialog |
| 7 | `custom_theme_name` | Custom theme | カスタムテーマ | Base name for newly saved custom themes (suffixed with index) |
| 8 | `discard_changes_question` | Do you want to save your changes? | 変更を保存しますか？ | Prompt shown when switching themes with unsaved custom color changes |
| 9 | `continue_without_saving` | Continue without saving | 保存せずに続行 | Negative dialog button to discard unsaved custom color changes |

## Onboarding & Full-Screen Permission Flow (29 keys)

Added to `values-ja/strings.xml` for the 3-screen first-launch welcome and full-screen permission flow (unsupported system locales fall back cleanly to English in `values/strings.xml`). Also removed the retired `auto_backup_prompt_message` key since auto-backup is now enabled by default and always shown in Settings.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `onboarding_motto` | No sign-up, no cloud, just your gallery. | 登録不要、クラウドなし、あなただけのギャラリー。 | Welcome screen tagline |
| 2 | `onboarding_offline_title` | Completely Offline | 完全オフライン | Welcome card 1 title |
| 3 | `onboarding_offline_desc` | Never connects to the internet. Your data belongs only to you. | インターネットに接続しません。データはあなただけのものです。 | Welcome card 1 description |
| 4 | `onboarding_vault_title` | Encrypted Vault | 暗号化された保管庫 | Welcome card 2 title |
| 5 | `onboarding_vault_desc` | Encrypt your photos and videos. No one else can access them. | 写真や動画を暗号化します。あなた以外の誰もアクセスできません。 | Welcome card 2 description |
| 6 | `onboarding_albums_title` | Playlists for your photos | 写真のためのプレイリスト | Welcome card 3 title |
| 7 | `onboarding_albums_desc` | Virtual albums let you bring the files you want to see together in a single album, without moving them between folders or creating copies on disk. Unlike a regular copy, they use no extra storage because only the original file is kept on disk. You can add the same file to as many albums as you like, while your folder structure stays exactly as it is. | 仮想アルバムを使えば、一緒に見たいファイルをフォルダー間で移動させたり、ディスクにコピーを作ったりせずに、1つのアルバムにまとめられます。通常のコピーとは異なり、ディスクには元のファイルだけが存在するため、ストレージを消費しません。同じファイルを好きな数のアルバムに追加でき、フォルダー構成はそのまま保たれます。 | Welcome card 3 description |
| 8 | `onboarding_media_permission_note` | Media access permission is required to display your photos and videos. | 写真や動画を表示するには、メディアへのアクセス権限が必要です。 | Welcome footer media permission note |
| 9 | `onboarding_continue` | Continue | 続行 | Welcome primary button |
| 10 | `onboarding_media_required_title` | Full media access required | メディアへの完全なアクセスが必要 | Media permission screen title |
| 11 | `onboarding_media_required_desc` | Calypso Gallery needs full access to display all your photos and videos. | Calypsoギャラリーは、すべての写真と動画を表示するために完全なアクセスを必要とします。 | Media permission screen description |
| 12 | `onboarding_media_steps_header` | How to enable permission | 権限を有効にする手順 | Settings steps card header |
| 13 | `onboarding_media_step_1` | Settings | 設定 | Settings guide step 1 |
| 14 | `onboarding_media_step_2` | Permissions | 権限 | Settings guide step 2 |
| 15 | `onboarding_media_step_3` | Photos and videos | 写真と動画 | Settings guide step 3 |
| 16 | `onboarding_media_step_4` | Allow all | すべて許可 | Settings guide step 4 (final) |
| 17 | `onboarding_media_required_hint` | Once access is granted, your gallery will open automatically when you return to the app. | アクセスを許可してアプリに戻ると、ギャラリーが自動的に開きます。 | Media permission screen return hint |
| 18 | `onboarding_go_to_settings` | Go to settings | 設定を開く | Media permission primary button |
| 19 | `onboarding_close` | Close | 閉じる | Media permission secondary button |
| 20 | `onboarding_all_files_title` | All files access | すべてのファイルへのアクセス | All files access screen title |
| 21 | `onboarding_all_files_desc` | All files access is recommended for the Encrypted Vault and seamless file management. | 暗号化された保管庫やスムーズなファイル管理のために、すべてのファイルへのアクセス権限をおすすめします。 | All files access screen subtitle |
| 22 | `onboarding_all_files_vault_title` | Encrypted Vault and hidden folders | 暗号化された保管庫と非表示フォルダー | All files card 1 title |
| 23 | `onboarding_all_files_vault_desc` | Manage your encrypted files and hidden folders without system restrictions. | 暗号化されたファイルや非表示フォルダーをシステム制限なしで管理できます。 | All files card 1 description |
| 24 | `onboarding_all_files_backup_title` | Don\'t lose your Favorites and Virtual Albums | お気に入りと仮想アルバムを失わないために | All files card 2 title |
| 25 | `onboarding_all_files_backup_desc` | Keep your favorites and virtual albums automatically backed up on your device even if you accidentally uninstall the app. You can turn off automatic backup anytime in app settings. | アプリを誤ってアンインストールしても、お気に入りと仮想アルバムが端末内に自動でバックアップされます。自動バックアップはアプリ設定からいつでもオフにできます。 | All files card 2 description |
| 26 | `onboarding_all_files_offline_title` | Still completely on your device | これまで通り完全に端末内で完結 | All files card 3 title |
| 27 | `onboarding_all_files_offline_desc` | The app stays completely offline, this permission is used only for local file operations. | アプリはオフラインのまま動作し、この権限は端末内のファイル操作のみに使用されます。 | All files card 3 description |
| 28 | `onboarding_grant_access` | Grant access | 許可する | All files primary button |
| 29 | `onboarding_not_now` | Not now | 後で | All files secondary button |

## Vault Lock & Album Header Polish (4 keys)

Added to `values-ja/strings.xml` for the redesigned Vault Lock screen biometric button and album interior header.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `use_biometrics` | Use biometrics | 生体認証を使用 | Vault lock screen biometric button label |
| 2 | `media_header_items_count` | %d items | %d件 | MediaActivity album header item count |
| 3 | `media_header_virtual_album` | Virtual album · %d items | 仮想アルバム · %d件 | MediaActivity virtual album header subtitle |
| 4 | `media_header_encrypted_album` | Encrypted album · %d items | 暗号化アルバム · %d件 | MediaActivity encrypted album header subtitle |
## Appearance Live Preview (4 keys)

Added to `values-ja/strings.xml` for the live Appearance preview card and WCAG contrast warning.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `appearance_preview` | Preview | プレビュー | Appearance live preview section header |
| 2 | `appearance_preview_text` | Your memories, your colors | あなたの思い出、あなたの色 | Appearance preview sample headline |
| 3 | `appearance_preview_button` | Sample button | サンプルボタン | Appearance preview sample button |
| 4 | `appearance_contrast_warning` | Low contrast: text %1$.1f:1, button %2$.1f:1. Increase it for readability. | コントラストが低すぎます：テキスト %1$.1f:1、ボタン %2$.1f:1。読みやすくするためコントラストを上げてください。 | WCAG contrast warning in Appearance preview |

## Vault Privacy Input Toggle (1 key)

Added to `values-ja/strings.xml` for the optional shoulder-surfing protection switch (default off).

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `hide_vault_key_presses` | Hide key presses when entering vault password | 保管庫のパスワード入力時にキー押下を非表示にする | Settings switch to hide PIN/password key press feedback |

## PIN Dot Indicator Setting (1 key)

Added to `values-ja/strings.xml` for the optional per-digit PIN dot indicator (default off; only shown for PINs of at most 16 digits).

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `show_pin_dots` | Show a dot for each PIN digit | PIN入力時に各桁をドットで表示する | Settings switch; hidden for alphanumeric vaults and PINs longer than 16 digits |

## KDF Unlock-Time Hint Revision (this revision)

Updated `brute_force_time_hint` and added `brute_force_time_min_hint` in `values-ja/strings.xml`. The lower-bound sentence is appended only when the device's measured safe floor is above 1.0 s.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_time_hint` | Select the time spent during opening the encrypted vault. Keeping this value high provides extra security by making brute-force attacks harder. | 暗号化された保管庫を開く際にかかる時間を選択してください。この値を高くすると、総当たり攻撃が難しくなり、セキュリティが強化されます。 | Explanation shown in the target vault unlock time dialog |
| 2 | `brute_force_time_min_hint` | The lower bound is this device's safe standard time. | 下限はこの端末の安全な標準時間です。 | Appended only when minSec > 1.0 |

## Biometric Unavailability Explanation (this revision)

Added to `values-ja/strings.xml`. The Settings row now stays visible when a vault exists; if the device only has weak (Class 2) biometrics or cannot create a hardware-backed key, the row is disabled and these strings explain why in a dialog and under the row. They replace the short `no_biometric_hardware_or_enrolled` toast. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `vault_biometric_unavailable_title` | Biometric vault access unavailable | 保管庫の生体認証アクセスは利用できません | Dialog title |
| 2 | `vault_biometric_unavailable_no_hardware` | This device has no strong biometric hardware (fingerprint or Class 3 face unlock). Vault biometric access is unavailable; use your PIN or password. | このデバイスには強力な生体認証ハードウェア（指紋またはクラス 3 の顔認証）がありません。保管庫の生体認証アクセスは利用できません。PIN またはパスワードを使用してください。 | `BIOMETRIC_ERROR_NO_HARDWARE` |
| 3 | `vault_biometric_unavailable_none_enrolled` | No strong biometric is enrolled. Weak face unlock is not accepted; enrol a fingerprint or a Class 3 face unlock in system settings. | 強力な生体認証が登録されていません。弱い顔認証は使用できません。システム設定で指紋またはクラス 3 の顔認証を登録してください。 | `BIOMETRIC_ERROR_NONE_ENROLLED`; weak face unlock rejected |
| 4 | `vault_biometric_unavailable_hw_unavailable` | The biometric sensor is temporarily unavailable. Try again later. | 生体認証センサーは一時的に利用できません。しばらくしてからもう一度お試しください。 | `BIOMETRIC_ERROR_HW_UNAVAILABLE` |
| 5 | `vault_biometric_unavailable_security_update` | Biometrics require a security update before they can be used. | 生体認証を使用するには、先にセキュリティ更新プログラムを適用する必要があります。 | `BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED` |
| 6 | `vault_biometric_unavailable_no_secure_key` | A hardware-backed key (TEE/StrongBox) could not be created on this device. Vault biometric access is disabled for security reasons. | このデバイスではハードウェア保護キー（TEE/StrongBox）を作成できませんでした。セキュリティ上の理由により、保管庫の生体認証アクセスは無効です。 | `canAuthenticate()` succeeded but no TEE/StrongBox key |
| 7 | `vault_biometric_unavailable_generic` | Vault biometric access is unavailable on this device. Enrol a fingerprint or a strong face unlock in system settings, or use your PIN or password. | このデバイスでは保管庫の生体認証アクセスは利用できません。システム設定で指紋または強力な顔認証を登録するか、PIN またはパスワードを使用してください。 | Fallback explanation |

## Password Reminder Strings (this revision)

Added/updated in `values-ja/strings.xml`. The reminder is shown in-app when the vault is opened and no password/PIN unlock happened during the configured interval. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `vault_password_reminder` | Remind me of my password | パスワードを定期的に通知する |  |
| 2 | `vault_password_reminder_disabled` | Off | オフ |  |
| 3 | `vault_password_reminder_weeks` | %d weeks | %d 週間 |  |
| 4 | `vault_password_reminder_months` | %d months | %d か月 |  |
| 5 | `vault_password_reminder_dialog_title` | Reminder interval | 通知間隔 |  |
| 6 | `vault_password_reminder_dialog_message` | If you do not open the encrypted vault with your password for this long, we will remind you of it. | この期間中にパスワードで暗号化保管庫を開けなかった場合、パスワードを通知します。 |  |
| 7 | `vault_password_reminder_setup_note` | We will send you periodic reminders so you do not forget your password. You can turn this off in Settings → Encrypted vault → Remind me of my password. | パスワードを忘れないために定期的に通知します。設定 → 暗号化された保管庫 → パスワードを定期的に通知する から無効にできます。 |  |
| 8 | `vault_biometric_setup_password_note` | If you choose to sign in with biometrics, remember that you still need to know your password. | 生体認証でログインする場合でも、パスワードを覚えている必要があることに注意してください。 |  |
| 9 | `vault_password_saved_title` | Have you saved your password? | パスワードを保存しましたか？ |  |
| 10 | `vault_password_saved_message` | If you forget your password, you will permanently lose access to your encrypted vault. Make sure you have saved it somewhere before continuing. Using a password manager is recommended. | パスワードを忘れると、暗号化保管庫へのアクセスを完全に失います。継絊する前にパスワードをどこかに保存したことを確認してください。パスワードマネージャーの使用をお勧めします。 |  |
| 11 | `vault_password_saved_confirm` | Yes, I saved it | はい、保存しました |  |
| 12 | `vault_password_saved_later` | I will save it | 保存します |  |
| 13 | `vault_password_reminder_due_title` | Do you still remember your vault password? | 保管庫のパスワードをまだ覚えていますか？ |  |
| 14 | `vault_password_reminder_due_message` | You have not opened the encrypted vault with your password for this long. Your password is the only way to recover the vault. | この期間中にパスワードで暗号化保管庫を開けていません。パスワードは保管庫を復旧する唯一の方法です。 |  |
| 15 | `vault_password_reminder_verify_title` | Confirm your password | パスワードを確認 |  |
| 16 | `vault_password_reminder_verify_message` | This is not a vault unlock. We only want to confirm that you still know your password. | これは保管庫の開锡8ではありません。パスワードをまだ知っているかどうかを確認するだけです。 |  |
| 17 | `vault_password_reminder_verified` | Password confirmed | パスワードを確認しました |  |
| 18 | `vault_password_reminder_not_now` | Not now | 今はしない |  |
| 19 | `vault_password_reminder_forgot_title` | Do not remember your password? | パスワードを覚えていませんか？ |  |
| 20 | `vault_password_reminder_forgot_steps` | 1. Unlock the vault with biometrics.\n2. Copy (export) your encrypted data — this does not move the vault.\n3. Settings → Reset encrypted vault to set a new password and restore.\n\nIf biometrics cannot unlock it either, the vault is inaccessible. You can archive the current vault and create a new one, copy the encrypted data for another device, or delete the vault folder at %1$s with a file manager (cannot be undone). | 生体認証で保管庫を開け、暗号化データをエクスポートし、設定 → 暗号化保管庫をリセット で新しいパスワードを設定してデータを復元してください。生体認証でも開けない場合、保管庫にはアクセスできません。 |  |
| 21 | `vault_password_reminder_verify_action` | Verify my password | パスワードを確認する |  |
| 22 | `vault_password_reminder_forgot_action` | I don't remember | 覚えていない |  |
| 23 | `vault_password_reminder_open_settings` | Open settings | 設定を開く |  |
| 24 | `empty_encrypted_vault` | Reset encrypted vault | 暗号化保管庫をリセット | Renamed label |

## Vault Archive & Forgot-Password Strings (this revision)

Added/updated in `values-ja/strings.xml`. Archive moves the old vault; export copies it; deletion is left to the file manager (ADR 0007). English source: `values/strings.xml`.

| # | Key (`name`) | English | Japanese |
|--:|---|---|---|
| 1 | `archive_vault` | Archive encrypted vault | 暗号化保管庫をアーカイブ |
| 2 | `archive_vault_summary` | Moves the old vault to the archive and creates a new one | 古い保管庫をアーカイブし、新しい保管庫を作成します |
| 3 | `archive_vault_confirm` | The current vault will be moved to the archive and a new vault will be created in the default location. Nothing is deleted. To use the vault on another device, use Export encrypted vault — it copies the files and the current vault stays where it is. | 現在の保管庫はアーカイブに移動され、既定の場所に新しい保管庫が作成されます。削除はされません。別のデバイスで使う場合は「暗号化保管庫をエクスポート」を使用してください。コピーであり、元の保管庫は残ります。 |
| 4 | `archive_vault_success` | Vault archived. Location: %1$s | 保管庫をアーカイブしました。場所: %1$s |
| 5 | `archive_vault_failed` | Could not archive the vault. Nothing was changed. | 保管庫をアーカイブできませんでした。変更はありません。 |
| 6 | `select_archived_vault` | Select from archived vaults | アーカイブ済み保管庫から選択 |
| 7 | `archived_vault_indexed` | %1$d. archived vault | %1$d 番目にアーカイブした保管庫 |
| 8 | `archived_vault_unknown_date` | Encrypted vault with unknown date | 日付不明の暗号化保管庫 |
| 9 | `archived_vault_invalid` | Invalid archive entry | 無効なアーカイブ項目 |
| 10 | `archived_vault_invalid_message` | Vault header not found at %1$s. You can delete this folder with a file manager. | %1$s に保管庫ヘッダーが見つかりません。ファイルマネージャーで削除できます。 |
| 11 | `copy_path` | Copy path | パスをコピー |
| 12 | `faq_forgot_password_title` | I forgot my encrypted vault password, what can I do? | 暗号化保管庫のパスワードを忘れました。どうすればいいですか？ |
| 13 | `faq_forgot_password_text` | 1. Unlock the vault with biometrics.\n2. Copy (export) your encrypted data — copying is not moving; the original stays in place.\n3. Settings → Reset encrypted vault to set a new password and restore.\n\nWarning: if biometrics cannot unlock it either, the vault is inaccessible. You can archive the current vault and create a new one, copy the encrypted data for another device, or delete the vault folder at %1$s with a file manager. Deletion cannot be undone. | 1. 生体認証で開けます。 2. 暗号化データをコピー（エクスポート）します。コピーであり移動ではありません。 3. 設定 → 暗号化保管庫をリセット で新しいパスワードを設定します。  警告: 生体認証も使えない場合、保管庫にはアクセスできません。アーカイブ、コピー、または %1$s のフォルダーをファイルマネージャーで削除できます。 |
| 14 | `vault_password_reminder_forgot_steps` | 1. Unlock the vault with biometrics.\n2. Copy (export) your encrypted data — this does not move the vault.\n3. Settings → Reset encrypted vault to set a new password and restore.\n\nIf biometrics cannot unlock it either, the vault is inaccessible. You can archive the current vault and create a new one, copy the encrypted data for another device, or delete the vault folder at %1$s with a file manager (cannot be undone). | 1. 生体認証で開きます。 2. 暗号化データをコピーします。 3. 設定 → 暗号化保管庫をリセット。  生体認証も使えない場合は、アーカイブ、コピー、または %1$s のフォルダーを削除してください。 |

## Unarchive Strings (this revision)

Added to `values-ja/strings.xml`. Unarchive moves the vault back to its recorded original location; no swap, no password.

| # | Key | English | Japanese |
|--:|---|---|---|
| 1 | `unarchive_vault` | Unarchive vault | アーカイブから戻す |
| 2 | `unarchive_vault_summary` | Moves the vault back to its location before archiving | 保管庫をアーカイブ前の場所に戻します |
| 3 | `unarchive_vault_confirm` | The vault will be moved back to its original location. No password is required. Continue? | 保管庫を元の場所に戻します。パスワードは不要です。続けますか？ |
| 4 | `unarchive_vault_success` | The vault was moved back to its original location. | 保管庫を元の場所に戻しました。 |
| 5 | `unarchive_vault_blocked` | Another vault is located where this vault used to be. Archive that vault first, then unarchive this one. | この保管庫の元の場所には別の保管庫があります。先にその保管庫をアーカイブしてから、この保管庫を戻してください。 |
| 6 | `unarchive_vault_merge` | Combine data into one vault | データを一つの保管庫にまとめる |
| 7 | `unarchive_vault_merge_steps` | Unlock the archived vault with its password, export the files in decrypted form, then import them into the other vault. The existing conflict-handling modes apply. | アーカイブした保管庫をパスワードで開き、復号したファイルをエクスポートして別の保管庫にインポートします。 |
| 8 | `archived_vault_activated` | The vault is open from the archive. Use Settings → Unarchive vault to move it back to its original location. | アーカイブから開きました。設定 → アーカイブから戻す で元の場所に戻せます。 |

## Export Target Conflict Strings (this revision)

Added to `values-ja/strings.xml`. Export now targets `<picked>/Calypso Gallery/Secrets` and is blocked when the target holds a different vault.

| # | Key | English | Japanese |
|--:|---|---|---|
| 1 | `export_vault_different_vault` | The destination already contains a different encrypted vault. Export was cancelled to protect it. Choose an empty folder. | 移動先に別の暗号化保管庫があります。保護するためエクスポートを中止しました。空のフォルダーを選択してください。 |
| 2 | `export_vault_unknown_content` | The destination contains encrypted files but no vault header. Export was cancelled to avoid mixing vaults. | 移動先に暗号化ファイルはありますが保管庫ヘッダーがありません。エクスポートを中止しました。 |

## Existing Vault Time Recalculation (this revision)





Added `brute_force_time_value_device` and `brute_force_time_out_of_range` to `values-ja/strings.xml`. The Settings/vault-setup summary now shows the existing vault's real unlock time on the current device (measured on unlock, otherwise estimated from the portable `.vault` KDF cost), with a note when it exceeds the 10 s maximum, showing only the stored parameters when this device has no measurement yet.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_time_value_device` | %1$.1f s (on this device) | %1$.1f 秒（この端末） | Value shown for an existing vault |
| 2 | `brute_force_time_out_of_range` | This time is above the selectable maximum (10 s). Tap to change it. | この時間は選択可能な上限（10 秒）を超えています。変更するにはタップしてください。 | Shown when the real time exceeds 10 s |

## Silent identical-skip toast & Automatic mode date tie-break (this revision)

Added the `identical_files_skipped` plural and revised `date_tie_break_ask_info_message` in `values-ja/strings.xml`. Smart dialog and Automatic mode now report silently skipped, byte-identical files at the end of an operation with a short toast; identical files with different dates that were reviewed in the thumbnail conflict dialog are excluded. "Always ask" can no longer be selected in Automatic (Experimental) mode and falls back to keeping the existing file's date. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `identical_files_skipped` | one: "%d identical file skipped" / other: "%d identical files skipped" | other: "%d 個の同一ファイルをスキップしました" | Short end-of-operation toast in Smart/Automatic mode |
| 2 | `date_tie_break_ask_info_message` | Smart dialog and Automatic mode exist for one reason: to skip files whose content is already present, so you only see the files that really need your attention — same name, different content. That is exactly the case where comparing thumbnails can reveal bit-rot.\n\nIn Smart dialog you will see two thumbnails of the very same picture, with nothing to verify, and you can decide there. Automatic (Experimental) mode never shows a dialog, so "Always ask" is not available there — selecting Automatic falls back to "Keep the existing file's date".\n\nUse this option only if you deliberately want to review every date difference, for example to fix a wrong date by hand.\n\nNote: when content is bit-identical, the EXIF capture date is identical too. A date difference means one side lost its EXIF data and fell back to the file modification date (the copy date). | スマートダイアログと自動モードは、内容がすでに存在するファイルをスキップし、本当に注意が必要なファイル（名前は同じだが内容が異なるもの）だけを表示するために存在します。まさにこのような場合に、サムネイルの比較によってビットロット（データの劣化）を検出できます。\n\nここで\"毎回確認\"を選択すると、日付が異なる同一ファイルがすべて競合として再び表示されます。スマートダイアログでは同じ画像のサムネイルが2つ表示され、確認するものが何もありませんが、その場で判断できます。自動（実験的）モードではダイアログを一切表示しないため、\"毎回確認\"は使用できません。自動モードを選択すると\"既存ファイルの日付を保持（既定）\"にフォールバックします。\n\nこのオプションは、誤った日付を手動で修正するなど、すべての日付の違いを意図的に確認したい場合にのみ使用してください。\n\n注: 内容がビット単位で同一の場合、EXIF の撮影日も同一です。日付が異なるということは、いずれかの側が EXIF データを失い、ファイルの更新日時（コピーした日時）にフォールバックしたことを意味します。 | Revised: "Always ask" unavailable in Automatic; falls back to keep-target date |

## New strings (biometric setup, device-owner check, archive size)

| Key | Translation |
|---|---|
| `vault_biometric_setup_continue` | 続ける |
| `device_owner_verify_title` | 端末の所有者を確認 |
| `device_owner_verify_subtitle` | 生体認証または端末のPINで確認して続行してください。金庫のパスワードは不要です。 |
| `archive_total_size_title` | アーカイブの保存領域 |
| `archive_total_size` | アーカイブ済み金庫の合計サイズ: %1$s |

## Weak password confirmation

| Key | Translation |
|---|---|
| `weak_password_warning_title` | 弱いパスワード |
| `weak_password_warning_message` | このパスワードは弱いと判定されました。金庫の安全性のため、より長く複雑なパスワードを推奨します。それでも続けますか？ |
| `weak_password_warning_continue` | それでも続ける |

## Minimalist Settings, Appearance Redesign & Recent Fork Strings (this revision)

| Key | English | Japanese |
|---|---|---|
| `appearance_title` | Appearance | 外観 |
| `search_settings` | Search settings | 設定を検索 |
| `license_tink_title` | Google Tink (streaming AEAD and HKDF cryptography) | Google Tink（ストリーミング AEAD および HKDF 暗号） |
| `license_rustcrypto_argon2_title` | RustCrypto Argon2 (native Argon2id key derivation) | RustCrypto Argon2（ネイティブ Argon2id 鍵導出） |
| `license_webpdecoder_title` | GlideWebpDecoder (animated WebP decoding) | GlideWebpDecoder（アニメーション WebP デコード） |
| `license_jxl_coder_title` | JXL Coder Glide (JPEG XL support) | JXL Coder Glide（JPEG XL 対応） |
| `license_androidsvg_title` | AndroidSVG (SVG rendering) | AndroidSVG（SVG レンダリング） |
| `license_exif_extended_title` | Android-Exif-Extended (EXIF metadata handling) | Android-Exif-Extended（EXIF メタデータ処理） |
| `license_material_icons_title` | Material Design Icons (UI icons) | Material Design Icons（UI アイコン） |
| `checking_password` | Checking password… | パスワードを確認中… |
| `vault_same_password_title` | Same password | 同じパスワード |
| `vault_same_password_message` | The new password is identical to the current vault password. Please choose a different one. | 新しいパスワードが現在の保管庫のパスワードと同じです。別のパスワードを指定してください。 |
| `brute_force_time_with_params` | %1$.1f s (%2$d MiB / %3$d rounds) | %1$.1f 秒（%2$d MiB / %3$d ラウンド） |
| `brute_force_high_memory_warning` | Higher values use more memory. You may have trouble opening your vault on devices with 2 GB of RAM or less. If that happens, lower the unlock time in app settings and export the vault again. | 値が高いほどより多くのメモリを使用します。RAM が 2 GB 以下の端末では保管庫を開けない場合があります。その場合は、アプリの設定でロック解除時間を短くしてから保管庫を再エクスポートしてください。 |
| `measuring_device_kdf_speed` | Measuring standard KDF speed on this device… | この端末の標準 KDF 速度を測定しています… |
| `kdf_settings_updated_successfully` | Brute-Force resistance updated for encrypted vault | 暗号化された保管庫のブルートフォース耐性を更新しました |
| `appearance_automatic` | Automatic | 自動 |
| `follow_system_theme` | Follow system theme | システムのテーマに従う |
| `follow_system_theme_summary` | Automatically switches between light and dark | ライトテーマとダークテーマを自動的に切り替えます |
| `theme_midnight_blue` | Midnight Blue | ミッドナイトブルー |
| `theme_warm_paper` | Warm Paper | ウォームペーパー |
| `theme_deep_forest` | Deep Forest | ディープフォレスト |
| `appearance_contrast_ok` | Good contrast · Text %1$.1f:1 · Button %2$.1f:1 | 良好なコントラスト · テキスト %1$.1f:1 · ボタン %2$.1f:1 |
| `appearance_backup_section` | Backup | バックアップ |
| `backup_saved_themes_to_storage` | Back up saved themes to device storage | 保存済みテーマを端末ストレージにバックアップ |
| `settings_group_general` | General | 全般 |
| `settings_group_folders_and_loading` | Folders and loading | フォルダーと読み込み |
| `settings_group_security` | Security | セキュリティ |
| `settings_group_vault_management` | Vault management | 保管庫の管理 |
| `settings_group_playback` | Playback | 再生 |
| `settings_group_gestures_and_tap` | Gestures and tap | ジェスチャーとタップ |
| `settings_group_display_and_motion` | Display and motion | 表示とモーション |
| `settings_group_thumbnail_styles` | Thumbnail styles | サムネイルのスタイル |
| `settings_group_general_behavior` | General behavior | 全般の動作 |
| `settings_group_conflicts_and_crypto` | Conflicts and encryption rules | 競合と暗号化のルール |
| `settings_group_cleanup` | Cleanup | クリーンアップ |
| `settings_group_storage` | Storage | ストレージ |
| `settings_group_settings_and_favorites` | Settings and favorites | 設定とお気に入り |
| `settings_group_vault_transfer` | Encrypted vault transfer | 暗号化された保管庫の移行 |
| `settings_search_results` | Search results | 検索結果 |
| `settings_no_search_results` | No matching settings found. | 一致する設定が見つかりません。 |

## Active keys added since 2688360 (7 November) - added in this revision

The following strings were added after the base commit `2688360` (7 November) and are now documented here. English source: `values/strings.xml` (or the corresponding license/defaults resource). Target source: `values-ja/strings.xml`.

| # | Key (`name`) | English | ja (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `about_copyright_notice` | Copyright © Fossify Gallery contributors\nCopyright © 2026 Calypso Gallery contributors | *(translatable="false"; not localized)* | |
| 2 | `app_name` | Calypso Gallery | *(translatable="false"; not localized)* | |
| 3 | `auto_biometric_prompt_on_vault_return` | Automatically show biometric prompt when returning to app | アプリに戻ったときに生体認証を自動で表示する | |
| 4 | `biometric_access_to_encrypted_vault` | Biometric access to encrypted vault | 生体認証で暗号化された保管庫にアクセス | |
| 5 | `change_encrypted_vault_password` | Change encrypted vault password | 暗号化された保管庫のパスワードを変更 | |
| 6 | `create_encrypted_vault` | Create encrypted vault | 暗号化された保管庫を作成 | |
| 7 | `delete_auto_backup_confirm` | Delete device storage backups for the %1$s created while this setting was enabled? | この設定が有効な間に作成された %1$s の端末ストレージへのバックアップを削除しますか？ | |
| 8 | `delete_auto_backup_target_both` | favorites and virtual albums | お気に入りと仮想アルバム | |
| 9 | `delete_auto_backup_target_favorites` | favorites | お気に入り | |
| 10 | `delete_auto_backup_target_virtual_albums` | virtual albums | 仮想アルバム | |
| 11 | `empty_encrypted_vault_warning` | The encrypted vault (Secrets) and all its files will be permanently deleted. Are you sure? | 暗号化された保管庫（Secrets）とすべてのファイルが完全に削除されます。よろしいですか？ | |
| 12 | `encrypted_vault` | Encrypted vault | 暗号化された保管庫 | |
| 13 | `encrypted_vault_emptied` | Encrypted vault emptied | 暗号化された保管庫を空にしました | |
| 14 | `enter_new_password` | Enter new password | 新しいパスワードを入力 | |
| 15 | `enter_new_pin` | Enter new PIN | 新しいPINを入力 | |
| 16 | `fast_large_duplicate_check_info_message` | Why this matters for large copy/move transfers:\n\nWhen copying or moving large files, such as gigabytes of videos, exact bit-by-bit checking must read both the source and destination files from start to finish whenever filenames and sizes match—taking minutes on fast storage or much longer on SD cards.\n\nHow Fast Check works:\n• Files under 8 MB are ALWAYS checked 100% bit-by-bit.\n• For files over 8 MB, the app compares file size plus five 256 KB blocks distributed across the start, 25%, 50%, 75%, and end of the file (1.25 MB total per side).\n• For a 50 GB archive of 50 MB–1 GB videos, this cuts disk reads by ~40× to ~800×.\n\nWhen to enable or disable:\n• Recommended ON: If most of your gallery consists of large files (&gt;8 MB), two intact, distinct files sharing the same name, exact byte size, and all 5 sampled regions is virtually impossible in practice.\n• Recommended OFF (no speedup): If your gallery consists mostly of photos or files under 8 MB, this setting provides no significant speed gain since files under 8 MB are always checked 100% bit-by-bit.\n• Encrypt, decrypt, and external USB vault sync operations always perform full bit-by-bit verification. | 大容量のコピー/移動転送で重要な理由:\n\n数ギガバイトの動画などの大容量ファイルをコピーまたは移動する際、ファイル名とサイズが一致するたびに、厳密なビット単位チェックでは送信元と送信先の両方のファイルを先頭から末尾まで読み取る必要があり、高速ストレージでも数分、SD カードではさらに長くかかります。\n\n高速チェックの仕組み:\n• 8 MB 未満のファイルは常にビット単位で100%チェックされます。\n• 8 MB を超えるファイルでは、ファイルサイズに加えて先頭、25%、50%、75%、末尾に分散した5つの 256 KB ブロック（片側合計 1.25 MB）を比較します。\n• 50 MB〜1 GB の動画を含む 50 GB のアーカイブでは、ディスク読み取りが約 40×〜800× 削減されます。\n\n有効・無効の目安:\n• オン推奨: ギャラリーの大部分が大容量ファイル (&gt;8 MB) の場合、破損していない異なる2つのファイルが同じ名前、正確なバイトサイズ、および5つのサンプリング領域すべてで一致することは実質的に不可能です。\n• オフ推奨（高速化なし）: ギャラリーの大部分が写真または 8 MB 未満のファイルの場合、8 MB 未満のファイルは常にビット単位で100%チェックされるため、この設定による顕著な速度向上はありません。\n• 外部 USB 保管庫の同期は常に完全な Tink AEAD 認証を維持します。 | |
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
| 34 | `no_virtual_albums_yet` | No virtual albums yet. Tap \"New album\" to create one. | 仮想アルバムはまだありません。「新しいアルバム」をタップして作成してください。 | |
| 35 | `password_changed_successfully` | Password changed successfully | パスワードが正常に変更されました | |
| 36 | `refresh` | Refresh | 更新 | |
| 37 | `repeat_new_password` | Repeat new password | 新しいパスワードを再入力 | |
| 38 | `repeat_new_pin` | Repeat new PIN | 新しいPINを再入力 | |
| 39 | `resume_op_copy` | Copy | コピー | |
| 40 | `resume_op_decrypt` | Decryption | 復号 | |
| 41 | `resume_op_encrypt` | Encryption | 暗号化 | |
| 42 | `resume_op_move` | Move | 移動 | |
| 43 | `resume_operation_aborted_message` | %1$s operation was aborted. Already completed files were kept. | %1$s 操作は中止されました。完了済みのファイルは保持されています。 | |
| 44 | `resume_operation_interrupted_message` | %1$s operation was interrupted (%2$d remaining item(s)). No data was lost — do you want to continue? | %1$s 操作が中断されました（残り %2$d 件）。データは失われていません。続行しますか？ | |
| 45 | `search_virtual_albums` | Search virtual albums | 仮想アルバムを検索 | |
| 46 | `skip_delete_confirmation` | Always skip delete confirmation dialog | 削除の確認ダイアログを常にスキップする | |
| 47 | `vault_no_recovery_warning` | Offline encryption is used. If you forget your password or PIN, there is no way to recover your vault. | オフライン暗号化が使用されます。パスワードやPINを忘れた場合、保管庫を復元する方法はありません。 | |
| 48 | `vault_not_created_prompt` | You haven\'t created a vault yet. Do you want to create a vault? | まだ保管庫を作成していません。保管庫を作成しますか？ | |
| 49 | `waiting_for_free_space_or_storage` | Waiting for free space or destination storage… | 空き領域または保存先ストレージを待機しています… | |

## Vault setup tutorial & PIN-dot preview notice (this revision)

Added the six strings introduced by the vault setup-tutorial and PIN-dot live-preview design updates (`dba5068f0`, `4ea5b1f8f`) to `values-ja/strings.xml`. English source: `values/strings.xml`. The `&lt;b&gt;…&lt;/b&gt;` emphasis in `vault_pull_tutorial_message` and the literal `10%` in `pin_dots_notice_note` are preserved exactly.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `vault_pull_tutorial_title` | Your encrypted vault is ready | 暗号化保管庫の準備ができました | One-time tutorial shown after vault creation |
| 2 | `vault_pull_tutorial_message` | While at the top of the list on the main screen, &lt;b&gt;pull down from the top&lt;/b&gt; and hold until the lock icon settles, then release. | ホーム画面のリストの一番上にいるときに、&lt;b&gt;上から下へ引っ張って&lt;/b&gt;ロックアイコンが安定するまで押さえたままにし、離してください。 | `&lt;b&gt;` emphasis preserved |
| 3 | `vault_pull_tutorial_note` | For your privacy, the vault never appears as a separate folder or icon on the home screen; it only opens with this gesture. | プライバシーのため、保管庫はホーム画面に別のフォルダーやアイコンとして表示されることはなく、このジェスチャーでのみ開きます。 | Privacy note |
| 4 | `vault_pull_tutorial_confirm` | Got it | 了解しました | Positive button |
| 5 | `pin_dots_notice_title` | Alternative PIN appearance | 代替 PIN 表示 | One-time preview dialog before enabling PIN dots |
| 6 | `pin_dots_notice_note` | Anyone seeing your screen can count the empty rings to tell your PIN length. This reduces the number of passwords to try by about 10%. If you find having your PIN length known acceptable from a security standpoint, you can use this view. | 画面を見た人は、空の丸の数を数えることで PIN の長さを把握できます。これにより、試す必要のあるパスワードの数が約 10% 減少します。セキュリティ上、PIN の長さが知られることを許容できる場合は、この表示を使用できます。 | `10%` kept literal |

## Filename toggle, album search hint & extra viewer actions (this revision)

Added the five strings introduced by the dynamic filename toggle, album search hint, and extra fullscreen viewer actions updates (`e79197d7f`) to `values-ja/strings.xml`. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `show_filenames` | Show filenames | ファイル名を表示 | Media-view overflow toggle label when filenames are hidden |
| 2 | `hide_filenames` | Hide filenames | ファイル名を非表示 | Media-view overflow toggle label when filenames are visible |
| 3 | `search_in_album` | Search in album | アルバム内を検索 | Album-view search hint |
| 4 | `show_extra_viewer_actions` | Show extra actions on fullscreen media | 全画面メディアで追加の操作を表示 | Settings switch label |
| 5 | `manage_extra_viewer_actions` | Manage extra actions | 追加の操作を管理 | Settings row label and extra-actions dialog title |

## Secure sharing — metadata sanitization (this revision)

Added the six strings for the new **Settings → Security → Secure sharing** three-mode setting. A sanitized temporary copy is shared; the original media file is never modified. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `settings_group_secure_sharing` | Secure sharing | 安全な共有 | New eyebrow in the Security category |
| 2 | `settings_share_metadata_title` | Clear metadata before sharing | 共有前にメタデータを削除 | Settings row label and radio dialog title |
| 3 | `share_metadata_off` | Off | オフ | No sanitization |
| 4 | `share_metadata_location_only` | Location only | 位置情報のみ | Default mode: strips GPS tags |
| 5 | `share_metadata_all` | All metadata | すべてのメタデータ | Strips Make/Model/Software/DateTime/XMP too |
| 6 | `share_metadata_failed` | Could not create a metadata-free copy (storage may be full). Sharing was cancelled to protect your privacy. | メタデータのないコピーを作成できませんでした（ストレージが満杯の可能性があります）。プライバシーを保護するため共有をキャンセルしました。 | Shown when a clean copy cannot be made; sharing is aborted |

## File operation notifications (this revision)

Added the notification settings section (category heading, per-operation toggles, permission row and progress messages) introduced by the file-operation notification work to `values-ja/strings.xml`. Translated independently from the English source in `values/strings.xml`, matching the locale's existing terminology (`保管庫` for "Encrypted Vault"); the `…` ellipses are preserved exactly.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `notifications_settings` | Notifications | 通知 | Settings category label |
| 2 | `settings_group_operation_notifications` | File operation notifications | ファイル操作の通知 | Settings subcategory eyebrow |
| 3 | `notifications_enabled` | Show operation progress | 操作の進捗を表示 | Settings switch label |
| 4 | `notify_copy_move` | Copy and move | コピーと移動 | Per-operation toggle label |
| 5 | `notify_crypto` | Encrypt and decrypt | 暗号化と復号 | Per-operation toggle label |
| 6 | `notify_delete` | Delete and recycle bin | 削除とごみ箱 | Per-operation toggle label |
| 7 | `notify_virtual_album` | Virtual album updates | 仮想アルバムの更新 | Per-operation toggle label |
| 8 | `notifications_info` | Android requires a persistent notification while a background file operation runs. Detailed progress is only shown after you leave the app — while you are inside it, the on-screen dialog is used instead. File names, paths and vault contents are never included in notifications. | Androidでは、バックグラウンドでファイル操作中は永続的な通知が必要です。詳細な進捗はアプリを終了した後にのみ表示され、アプリ内では画面上のダイアログが代わりに使用されます。ファイル名、パス、保管庫の内容が通知に含まれることはありません。 | Explanatory text below the settings group |
| 9 | `notification_permission_label` | Notification permission | 通知の権限 | Android notification permission label |
| 10 | `notification_permission_granted` | Granted | 許可済み | Permission status / positive action |
| 11 | `notification_permission_request` | Tap to allow | タップして許可 | Permission request hint |
| 12 | `file_operations_notifications` | File operations | ファイル操作 | Notification channel / section title |
| 13 | `file_operation_copy_move` | Copying / moving files… | ファイルをコピー／移動中… | Progress notification title; `…` preserved |
| 14 | `file_operation_crypto` | Encrypting / decrypting files… | ファイルを暗号化／復号中… | Progress notification title; `…` preserved |
| 15 | `file_operation_delete` | Deleting files… | ファイルを削除中… | Progress notification title; `…` preserved |
| 16 | `file_operation_virtual_album` | Updating albums… | アルバムを更新中… | Progress notification title; `…` preserved |
| 17 | `file_operation_generic` | File operation in progress… | ファイル操作中… | Fallback progress notification title |
| 18 | `checking_files_before_transfer` | Checking files before transfer… | 転送前にファイルを確認中… | Pre-transfer verification progress |
| 19 | `checking_files_before_crypto` | Checking files before encryption… | 暗号化前にファイルを確認中… | Pre-encryption verification progress |
| 20 | `deleting_files_progress` | Deleting files… | ファイルを削除中… | Deletion progress status |

## Unlock-time security levels (this revision)

Added the four fork-only `brute_force_level_*` keys used by the themed unlock-time dial in Settings and first-time vault setup. The label follows the chosen unlock time: 0–2.5 s fast, 2.5–5 s balanced, 5–7.5 s high, 7.5–10 s maximum. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_level_fast` | Fast unlock | 高速なロック解除 | 0–2.5 s |
| 2 | `brute_force_level_balanced` | Balanced security | バランスの取れたセキュリティ | 2.5–5 s |
| 3 | `brute_force_level_high` | High security | 高いセキュリティ | 5–7.5 s |
| 4 | `brute_force_level_maximum` | Maximum protection | 最大限の保護 | 7.5–10 s |

## Unlock-time level descriptions (this revision)

Added the four fork-only `brute_force_level_*_desc` keys shown under the themed unlock-time dial in Settings and first-time vault setup. The 0–2.5 s description is now `Hızlı kasa açılışı` / `Fast vault opening` (previously `Düşük KDF maliyeti`, which is no longer used). English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_level_fast_desc` | Fast vault opening | 高速な保管庫のロック解除 | 0–2.5 s |
| 2 | `brute_force_level_balanced_desc` | Recommended range | 推奨範囲 | 2.5–5 s |
| 3 | `brute_force_level_high_desc` | More memory usage | より多くのメモリ使用量 | 5–7.5 s |
| 4 | `brute_force_level_maximum_desc` | Memory warning active | メモリ警告が有効 | 7.5–10 s |

## CLI bridge import strings (this revision)

The import flow now uses a per-vault bridge folder created by the app; the old "unified Secrets folder / export .vault" wording is replaced. Three keys were updated or added in `values-ja/strings.xml` (Japanese).

| Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|---|---|---|---|
| `sync_vault_from_external` | Import encrypted media from CLI | CLI から暗号化メディアをインポート | Settings row label; kept unchanged in English |
| `sync_vault_select_source_prompt` | How Encrypted Media Import Works: prepare on phone, encrypt on computer, merge into active vault | 暗号化メディアのインポートの仕組み:\n\n1. スマートフォンでの準備:\n設定 → CLIブリッジを準備 は、アクティブな保管庫のために Calypso Gallery/CLI の下にフォルダーを作成し、保管庫ヘッダー (vault) と cli_bridge マーカーを格納します。\n\n2. コンピューターでの暗号化:\n写真と動画を To_Encrypt フォルダーに入れてください。Windows と Linux では run.bat / run.sh を実行するとランチャーがブリッジを自動転送します。macOS では OpenMTP でフォルダーをコピーしてください。CLI は暗号化された .bin ファイル、アルバム情報、pending_sync リストをブリッジフォルダーに書き込みます。保管庫には書き込みません。ルートのファイルは Default Secret Album に、サブフォルダーは暗号化アルバムになります。\n\n3. アクティブな保管庫への統合:\n設定でこの行をタップします。保留中のすべてのファイルが、アプリで現在アクティブな保管庫にインポートされます。同一ファイルはスキップされ、同名で内容が異なるファイルは name(1) になります。ブリッジは完全に成功したインポート後のみクリーンアップされます。 | Rewritten for the bridge workflow; `\n` preserved in XML |
| `vault_sync_cli_busy` | The CLI is still writing to the bridge folder. Wait for it to finish, then try again. | CLI がブリッジフォルダーに書き込み中です。完了するまで待ってからもう一度お試しください。 | New key; shown when the selected bridge contains the busy marker |

## CLI bridge UX strings (this revision)

New fork-only strings for the bridge preparation, mode-aware deduplication (aligned with `conflictHandlingMode`), multi-bridge selection and stale marker recovery. The date comparison setting does not decide which bridge is used, but it still applies when files are imported into the vault.

| Key | English | Localized |
|---|---|---|
| `cli_bridge_prepare_label` | Prepare CLI bridge | CLIブリッジを準備 |
| `cli_bridge_prepare_info_title` | CLI bridge | CLIブリッジ |
| `cli_bridge_prepare_info_message` | Creates a folder under Calypso Gallery/CLI for the active vault, containing the vault header (vault) and a cli_bridge marker. On Windows and Linux the launcher transfers it automatically when you run run.bat / run.sh; on macOS copy it with OpenMTP. The bridge is a transfer area only - it never stores encrypted media permanently. The date comparison setting does not decide which bridge is used; it still applies when files are imported into the vault. | アクティブな保管庫のために Calypso Gallery/CLI の下に、保管庫ヘッダー (vault) と cli_bridge マーカーを含むフォルダーを作成します。Windows と Linux では run.bat / run.sh を実行するとランチャーが自動転送します。macOS では OpenMTP でフォルダーをコピーしてください。ブリッジは転送領域のみで、暗号化メディアを永続的に保存しません。日付比較の設定は使用するブリッジを決定しません。ファイルを保管庫にインポートする際には適用されます。 |
| `cli_bridge_prepare_success` | CLI bridge ready: %1$s | CLIブリッジの準備ができました: %1$s |
| `cli_bridge_conflict_title` | CLI bridge already exists | CLIブリッジはすでに存在します |
| `cli_bridge_conflict_message` | A CLI bridge already exists for this vault. What should happen?\n\nThe date comparison setting does not decide which bridge is used; it still applies when files are imported into the vault. | この保管庫のCLIブリッジはすでに存在します。どうしますか？\n\n日付比較の設定は使用するブリッジを決定しません。ファイルを保管庫にインポートする際には適用されます。 |
| `cli_bridge_conflict_skip` | Skip | スキップ |
| `cli_bridge_conflict_use_existing` | Use existing bridge | 既存のブリッジを使用 |
| `cli_bridge_conflict_create_new` | Create new bridge | 新しいブリッジを作成 |
| `cli_bridge_selection_title` | Select CLI bridge | CLIブリッジを選択 |
| `cli_bridge_selection_warning` | Select the vault that is currently active in the app; otherwise the transfer will fail. | アプリで現在アクティブな保管庫を選択してください。そうしないとインポートに失敗します。 |
| `vault_sync_stale_marker_title` | Unfinished write marker | 未完成の書き込みマーカー |
| `vault_sync_stale_marker_message` | This bridge contains a write marker from %1$s. The CLI does not appear to be running. Remove the marker and continue the transfer? | このブリッジには %1$s の書き込みマーカーがあります。CLI は動作していないようです。マーカーを削除してインポートを続けますか？ |
| `vault_sync_stale_marker_remove` | Remove and continue | 削除して続行 |
| `vault_sync_stale_marker_keep` | Keep it | 保持する |

## Appearance preset theme strings (this revision)

Added the two new built-in Appearance preset theme names next to `theme_deep_forest` in `values-ja/strings.xml` (Japanese). English source: `values/strings.xml`.

| Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|---|---|---|---|
| `theme_velvet_violet` | Velvet Violet | ベルベットバイオレット | Dark purple preset theme (`#0E0A18` bg, `#A78BFA` primary) |
| `theme_soft_coral` | Soft Coral | ソフトコーラル | Light coral preset theme (`#FFF7F5` bg, `#C44047` primary) |

## Album decryption destination strings (this revision)

Added the 5 new fork-only album decryption destination keys and updated `decrypt_info_message` in `values-ja/strings.xml` (Japanese). English source: `values/strings.xml`.

| Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|---|---|---|---|
| `confirm_decrypt_album` | Decrypt \"%s\"? | 「%s」を復号しますか？ | Confirmation dialog for decrypting a single encrypted album (`%s` = album name; Japanese corner brackets `「%s」`) |
| `confirm_decrypt_albums` | Decrypt %d selected albums? | 選択した %d 個のアルバムを復号しますか？ | Confirmation dialog for decrypting multiple encrypted albums (`%d` = count) |
| `decrypt_destination_label` | Destination: | 保存先: | Short label above resolved destination path(s) |
| `decrypt_destination_to_be_selected` | Location to be selected | 場所は後で選択 | Shown when no `.origin` is recorded or external storage is unmounted |
| `decrypt_to_other_location` | Other location | 別の場所 | Concise neutral button to pick a custom destination |
| `decrypt_info_message` | Folder & File Structure:\n• When an encrypted album is decrypted, it is restored to its original location with its current album name and removed from the encrypted vault.\n• If no original location was recorded or that storage is unavailable, or if you choose another location, the destination picker opens so you can choose where to extract it.\n• Individual decrypted files are restored with their original name and format to the selected destination folder.\n\nIf an Album with the Same Name Already Exists:\n• By default, the app asks whether to create a new folder such as Album(1) or merge into the existing destination folder (configurable in Settings).\n• When merging, you can choose whether encrypted copies verified 100% bit-identical to the destination are removed from the vault or kept.\n• Different files with the same name (e.g. download.jpg) are kept as download(1).jpg or handled according to your conflict resolution setting. | フォルダーとファイルの構造:\n• 暗号化されたアルバムを復号すると、現在のアルバム名のまま元の場所に復元され、暗号化された保管庫から削除されます。\n• 元の場所が記録されていない場合、そのストレージが利用できない場合、または別の場所を選択した場合は、保存先選択画面が開き、展開先を選択できます。\n• 個別に復号されたファイルは、元の名前と形式で選択した保存先フォルダーに復元されます。\n\n同じ名前のアルバムが既に存在する場合:\n• 既定では、Album(1) のような新しいフォルダーを作成するか、既存の保存先フォルダーに統合するかを確認します（設定で変更可能）。\n• 統合時は、保存先とビット単位で100%一致することが確認された暗号化コピーを保管庫から削除するか残すかを選択できます。\n• 同名で内容が異なるファイル（例: download.jpg）は download(1).jpg として保持されるか、競合解決の設定に従って処理されます。 | Updated (`formatted="false"` in XML); reflects automatic `.origin` restore vs. destination picker fallback |

## Included folder vault & restricted path guard (this revision)

Added `cannot_include_restricted_or_vault_folder` in `values-ja/strings.xml` (Japanese). English source: `values/strings.xml`.

| Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|---|---|---|---|
| `cannot_include_restricted_or_vault_folder` | Encrypted vault folders, app system directories, and restricted Android folders cannot be included. | 暗号化された保管庫フォルダー、アプリのシステムディレクトリ、および制限された Android フォルダーは追加できません。 | Toast shown when attempting to include a Cipher/vault system directory or restricted Android folder (`Android/data`, `Android/obb`) |

# Japanese (values-ja) Translation Completion & English 1-to-1 Comparison

## New fork strings - bottom action bar, view switch, biometrics and strength meter (12 keys)

Added to `values-ja/strings.xml`. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `show_bottom_action_names` | Show action names in the bottom action bar | 下部アクションバーにアクション名を表示 | Fullscreen media setting, default OFF |
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
| 7 | `virtual_folder_name`                       | Virtual folder name                                                                                                                                                                                                                                                                                                                                                 | 仮想フォルダー名                                                                                                                                                                                                                 | Input hint / label for virtual folder                                              |
| 8 | `virtual_album_name_empty`                  | The album name cannot be empty                                                                                                                                                                                                                                                                                                                                      | アルバム名は空にできません                                                                                                                                                                                                            | Validation error when album name is empty                                          |
| 9 | `virtual_album_exists`                      | An album with this name already exists                                                                                                                                                                                                                                                                                                                              | この名前のアルバムはすでに存在します                                                                                                                                                                                                       | Validation error when album name already exists                                    |
| 10 | `add_to_virtual_album`                      | Add to album                                                                                                                                                                                                                                                                                                                                                        | アルバムに追加                                                                                                                                                                                                                  | Menu action to add items to an album                                               |
| 11 | `remove_from_virtual_album`                 | Remove from album                                                                                                                                                                                                                                                                                                                                                   | アルバムから削除                                                                                                                                                                                                                 | Menu action to remove items from an album                                          |
| 12 | `added_to_virtual_album`                    | Added to \"%s\"                                                                                                                                                                                                                                                                                                                                                     | 「%s」に追加しました                                                                                                                                                                                                              | Toast message using Japanese corner brackets 「%s」                                  |
| 13 | `already_in_virtual_album`                  | Already in \"%s\"                                                                                                                                                                                                                                                                                                                                                   | すでに「%s」に含まれています                                                                                                                                                                                                          | Toast message using Japanese corner brackets 「%s」                                  |
| 14 | `removed_from_virtual_album`                | Removed from the album                                                                                                                                                                                                                                                                                                                                              | アルバムから削除しました                                                                                                                                                                                                             | Toast message after removing items from an album                                   |
| 15 | `delete_virtual_album_confirmation`         | Delete the album %s? Only the album is removed, your photos and videos stay where they are.                                                                                                                                                                                                                                                                         | アルバム %s を削除しますか？アルバムのみが削除され、写真や動画はそのまま残ります。                                                                                                                                                                              | No extra quotes around %s (code passes quoted name or plural count)                |
| 16 | `virtual_album_empty`                       | This album is empty. Select items in any folder and use \"Copy to\".                                                                                                                                                                                                                                                                                                | このアルバムは空です。任意のフォルダーで項目を選択し、「コピー先」を使用してください。                                                                                                                                                                              | Empty state placeholder referencing Copy to (「コピー先」)                               |
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
| - | `block_vault_screenshots` | Block screenshots in hidden vault | 隠し保管庫でのスクリーンショットを禁止する | Settings switch (default ON) blocking screenshots in hidden vault |
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
| 49 | `default_hidden_album`                      | Default Hidden Album                                                                                                                                                                                                                                                                                                                                                | デフォルトの非表示アルバム                                                                                                                                                                                                            | Settings / picker label for default hidden album                                   |
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
| 2 | `excluded_activity_placeholder`             | Excluding folders will make them together with their subfolders hidden just in Fossify Gallery, they will still be visible in other applications.\n\nIf you want to hide them from other apps too, use the Hide function.                                                                                                                                           | フォルダーを除外すると、そのフォルダーとサブフォルダーはFossifyギャラリー上でのみ非表示になり、他のアプリでは引き続き表示されます。\n\n他のアプリからも非表示にしたい場合は、「非表示」機能を使用してください。                                                                                                            | Placeholder explanation on Excluded Folders screen; preserves \n\n paragraph break |
| 3 | `render_ultra_hdr_photos`                   | Show Ultra HDR photos in HDR                                                                                                                                                                                                                                                                                                                                        | Ultra HDR写真をHDRで表示                                                                                                                                                                                                       | Media settings: render Ultra HDR photos in HDR                                     |
| 4 | `keep_screen_on_fullscreen_photos`          | Keep the screen on while viewing fullscreen photos                                                                                                                                                                                                                                                                                                                  | 写真の全画面表示中は画面をオンのままにする                                                                                                                                                                                                    | Fullscreen media setting: keep screen awake                                        |
| 5 | `playback_speed`                            | Playback speed                                                                                                                                                                                                                                                                                                                                                      | 再生速度                                                                                                                                                                                                                     | Video player playback speed control                                                |
| 6 | `on_video_tap`                              | On video tap                                                                                                                                                                                                                                                                                                                                                        | 動画をタップしたとき                                                                                                                                                                                                               | Setting label for video tap action                                                 |
| 7 | `open_in_app_player`                        | Open in-app player                                                                                                                                                                                                                                                                                                                                                  | アプリ内プレーヤーを開く                                                                                                                                                                                                             | Radio option: open video in built-in player                                        |
| 8 | `open_system_default_player`                | Open default player app                                                                                                                                                                                                                                                                                                                                             | デフォルトのプレーヤーアプリを開く                                                                                                                                                                                                        | Radio option: open video in default external player                                |
| 9 | `file_is_malformed_or_corrupted`            | File is malformed or corrupted.                                                                                                                                                                                                                                                                                                                                     | ファイルが不正または破損しています。                                                                                                                                                                                                       | Media player error message                                                         |
| 10 | `media_exceeds_device_capabilities`         | Media exceeds device capabilities.                                                                                                                                                                                                                                                                                                                                  | メディアが端末の性能を超えています。                                                                                                                                                                                                       | Media player error message; uses 端末 for device                                     |
| 11 | `unsupported_format`                        | Unsupported format.                                                                                                                                                                                                                                                                                                                                                 | サポートされていない形式です。                                                                                                                                                                                                          | Media player error message                                                         |
| 12 | `video_no_sound`                            | This video has no audio                                                                                                                                                                                                                                                                                                                                             | この動画には音声がありません                                                                                                                                                                                                           | Video player notice when track has no audio                                        |
| 13 | `faq_1_title`                               | How can I make Fossify Gallery the default device gallery?                                                                                                                                                                                                                                                                                                          | Fossifyギャラリーを端末のデフォルトのギャラリーにするにはどうすればよいですか？                                                                                                                                                                              | FAQ 1 question; uses Fossifyギャラリー consistently with existing strings               |
| 14 | `faq_1_text`                                | First you have to find the currently default gallery in the Apps section of your device settings, look for a button that says something like \"Open by default\", click on it, then select \"Clear defaults\". The next time you will try opening an image or video you should see an app picker, where you can select Fossify Gallery and make it the default app. | まず、端末の「設定」の「アプリ」セクションで現在デフォルトに設定されているギャラリーを見つけ、「デフォルトで開く」などのボタンをタップして「デフォルト設定をクリア」を選択します。次回、画像や動画を開こうとした際にアプリ選択画面が表示されるので、そこでFossifyギャラリーを選択してデフォルトのアプリに設定してください。                                                        | FAQ 1 answer; matches existing Android settings phrasing in values-ja              |
| 15 | `faq_5_text`                                | Exclude prevents displaying the folder only in Fossify Gallery, while Hide works system-wise and it hides the folder from other galleries too. It works by creating an empty \".nomedia\" file in the given folder, which you can then remove with any file manager too. Note that some devices do not allow hiding folders like Camera, Screenshots and Downloads. | 「除外」はFossifyギャラリー内でのみフォルダーを表示しないようにするのに対し、「非表示」はシステム全体で機能し、他のギャラリーアプリからもフォルダーを隠します。これは指定したフォルダー内に空の「.nomedia」ファイルを作成することで動作し、任意のファイルマネージャーで削除することもできます。なお、一部の端末では Camera、Screenshots、Downloads などのフォルダーを非表示にできない場合があります。 | FAQ 5 answer; keeps folder names Camera, Screenshots, Downloads literal            |
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
| 1 | `app_launcher_name` | Gallery | ギャラリー |
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
| 35 | `full_storage_permission_required` | Fossify Gallery needs full access to display all your photos and videos. Go to Settings &gt; Permissions &gt; Photos and videos &gt; Allow all. | Fossifyギャラリーは、全ての写真と動画を表示するために、完全なアクセスを必要としています。設定から「権限」、「画像と写真」を開き、全て許可を選択してください。 |
| 36 | `filter_media` | Filter media | 表示する形式 |
| 37 | `images` | Images | 画像 |
| 38 | `videos` | Videos | 動画 |
| 39 | `gifs` | GIFs | GIF |
| 40 | `raw_images` | RAW images | RAW |
| 41 | `svgs` | SVGs | SVG |
| 42 | `portraits` | Portraits | ポートレイト |
| 43 | `no_media_with_filters` | No media files have been found with the selected filters. | 条件に該当するメディアがありません。 |
| 44 | `change_filters_underlined` | <u>Change filters</u> | <u>絞り込み条件を変更</u> |
| 45 | `exclude_folder_description` | This will exclude the selection together with its subfolders from Fossify Gallery only. You can manage excluded folders in Settings. | 選択したフォルダーとその子フォルダーをFossifyギャラリーから除外します。除外したフォルダーは設定画面で管理できます。 |
| 46 | `exclude_folder_parent` | Exclude a parent instead? | 親フォルダーを代わりに除外しますか？ |
| 47 | `excluded_activity_placeholder` | Excluding folders will make them together with their subfolders hidden just in Fossify Gallery, they will still be visible in other applications.\n\nIf you want to hide them from other apps too, use the Hide function. | フォルダーを除外すると、そのフォルダーとサブフォルダーはFossifyギャラリー上でのみ非表示になり、他のアプリでは引き続き表示されます。\n\n他のアプリからも非表示にしたい場合は、「非表示」機能を使用してください。 |
| 48 | `hidden_folders` | Hidden folders | 非表示フォルダー |
| 49 | `manage_hidden_folders` | Manage hidden folders | 非表示フォルダーの管理 |
| 50 | `hidden_folders_placeholder` | Seems like you don\'t have any folders hidden with a \".nomedia\" file. | 「.nomedia」で隠されたフォルダーはありません。 |
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
| 180 | `open_system_default_player` | Open default player app | デフォルトのプレーヤーアプリを開く |
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
| 226 | `faq_1_title` | How can I make Fossify Gallery the default device gallery? | Fossifyギャラリーを端末のデフォルトのギャラリーにするにはどうすればよいですか？ |
| 227 | `faq_1_text` | First you have to find the currently default gallery in the Apps section of your device settings, look for a button that says something like \"Open by default\", click on it, then select \"Clear defaults\". The next time you will try opening an image or video you should see an app picker, where you can select Fossify Gallery and make it the default app. | まず、端末の「設定」の「アプリ」セクションで現在デフォルトに設定されているギャラリーを見つけ、「デフォルトで開く」などのボタンをタップして「デフォルト設定をクリア」を選択します。次回、画像や動画を開こうとした際にアプリ選択画面が表示されるので、そこでFossifyギャラリーを選択してデフォルトのアプリに設定してください。 |
| 228 | `faq_2_title` | I locked the app with a password, but I forgot it. What can I do? | アプリをパスワードでロックしましたが、パスワードを忘れてしまいました。どうすればいいですか？ |
| 229 | `faq_2_text` | You can solve it in 2 ways. You can either reinstall the app, or find the app in your device settings and select \"Clear data\". It will reset all your settings, it will not remove any media files. | 解決策は2つあります。アプリを再インストールするか、デバイス設定からアプリを見つけて「データを消去」を選択するかです。そうすると設定がリセットされますが、メディアファイルは削除されません。 |
| 230 | `faq_3_title` | How can I make an album always appear at the top? | アルバムが常に上部に表示されるようにするにはどうすればいいですか？ |
| 231 | `faq_3_text` | You can long press the desired album and select the Pin icon at the actions menu, that will pin it to the top. You can pin multiple folders too, pinned items will be sorted by the default sorting method. | アルバムを長押しで選択し、アクションメニューからピンアイコンを選択すれば上部に固定されます。複数のフォルダーをピン留めできます。ピン留めされた項目群は、既定の並べ替え方法で並べ替えられます。 |
| 232 | `faq_4_title` | How can I fast forward videos? | 動画を早送りするにはどうすればよいですか？ |
| 233 | `faq_4_text` | You can do it by double tapping the side of the screen, or tapping the current or max duration texts near the seekbar. If you enable opening videos on a separate screen in the app settings, you can use horizontal gestures too. | 画面端をダブルタップするか、シークバーの近くにある曲の長さを示すテキストをタップすれば可能です。アプリ設定の左右ジェスチャー機能の切り替えを有効にすると、右ジェスチャーで早送りが可能です（無効にすると左右ジェスチャーで別の動画を開きます）。 |
| 234 | `faq_5_title` | What is the difference between hiding and excluding a folder? | フォルダーの非表示と除外の違いは何ですか？ |
| 235 | `faq_5_text` | Exclude prevents displaying the folder only in Fossify Gallery, while Hide works system-wise and it hides the folder from other galleries too. It works by creating an empty \".nomedia\" file in the given folder, which you can then remove with any file manager too. Note that some devices do not allow hiding folders like Camera, Screenshots and Downloads. | 「除外」はFossifyギャラリー内でのみフォルダーを表示しないようにするのに対し、「非表示」はシステム全体で機能し、他のギャラリーアプリからもフォルダーを隠します。これは指定したフォルダー内に空の「.nomedia」ファイルを作成することで動作し、任意のファイルマネージャーで削除することもできます。なお、一部の端末では Camera、Screenshots、Downloads などのフォルダーを非表示にできない場合があります。 |
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
| 265 | `virtual_folder_name` | Virtual folder name | 仮想フォルダー名 |
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
| - | `block_vault_screenshots` | Block screenshots in hidden vault | 隠し保管庫でのスクリーンショットを禁止する |
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
| 414 | `sync_vault_from_external` | Import encrypted media files to encrypted section | 暗号化されたメディアファイルを暗号化セクションにインポート |
| 415 | `sync_vault_select_source_prompt` | How Encrypted Media Import Works:\n\n1. Unified Secrets Folder:\nKeep a folder named Secrets on your external drive (USB / SD). You can either keep a full backup copy of your vault there, or place only your vault\'s .vault header file inside it.\n\n2. Encrypting New Media (PC / Tails CLI):\nThe offline CLI tool automatically saves new encrypted files directly into Secrets. Files placed at the root go to Default Secret Album; files in subfolders go to matching encrypted albums (or create new ones).\n\n3. Bit-by-Bit Smart Sync:\nSelect your external drive or Secrets folder next. The app compares the external drive with your phone vault in RAM:\n• Only new files and albums are imported.\n• Bit-identical files already in your vault are skipped.\n• Different files with the same name (e.g. download.jpg) are safely imported as download(1).jpg. | 暗号化メディアのインポートの仕組み:\n\n1. 統合 Secrets フォルダー:\n外部ドライブ (USB / SD) に Secrets という名前のフォルダーを用意します。そこに保管庫の完全なバックアップコピーを置くか、保管庫の .vault ヘッダーファイルのみを配置できます。\n\n2. 新しいメディアの暗号化 (PC / Tails CLI):\nオフライン CLI ツールは、新しい暗号化ファイルを自動的に Secrets に直接保存します。ルートに置かれたファイルは Default Secret Album に、サブフォルダー内のファイルは対応する暗号化アルバムに入ります（存在しない場合は新規作成）。\n\n3. ビット単位のスマート同期:\n次に外部ドライブまたは Secrets フォルダーを選択してください。アプリは RAM 上で外部ドライブとスマートフォンの保管庫を比較します:\n• 新しいファイルとアルバムのみがインポートされます。\n• 保管庫に既にあるビット単位で同一のファイルはスキップされます。\n• 同名で内容が異なるファイル（例: download.jpg）は安全に download(1).jpg としてインポートされます。 |
| 416 | `conflict_handling_mode_label` | File conflict & duplicate resolution | ファイルの競合と重複の解決 |
| 417 | `conflict_handling_auto_short` | Full automatic (Default — Skip identical, rename different as (1)) | 完全自動（既定 — 同一はスキップ、異なる場合は (1) に名前変更） |
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
| 442 | `conflict_handling_explanation_message` | When copying, moving, encrypting, or decrypting files and albums, if a file with the same name already exists at the destination:\n\n1. Bit-by-Bit Duplicate Check (RAM):\nThe app checks file sizes first, and if sizes match, compares the file contents bit-by-bit in RAM (encrypted files are checked in memory without writing plaintext to disk). During copy/move, identical files are skipped and the original remains untouched in the source folder. When merging an album during encrypt/decrypt, you can choose whether 100% bit-identical source files are deleted or kept.\n\n2. Same Name, Different Content (e.g. two different download.jpg files):\n• Full automatic (Default): Keeps both files without interrupting you by naming the new file download(1).jpg, download(2).jpg.\n• Smart dialog: Still skips bit-identical files silently, and only asks (Skip / Overwrite / Keep both) when two files have the same name but different content. | ファイルやアルバムのコピー、移動、暗号化、または復号時に、同じ名前のファイルが保存先に既に存在する場合:\n\n1. ビット単位の重複チェック (RAM):\nアプリは最初にファイルサイズを確認し、一致する場合は RAM 上でファイル内容をビット単位で比較します（暗号化ファイルはディスクに平文を書き込まずメモリ内で確認されます）。コピー/移動時は同一ファイルがスキップされ、元ファイルは元のフォルダーにそのまま残ります。暗号化/復号時にアルバムを統合する場合は、ビット単位で100%同一の元ファイルを削除するか残すかを選択できます。\n\n2. 同名で異なる内容（例: 2つの異なる download.jpg ファイル）:\n• 完全自動（既定）: 新しいファイルを download(1).jpg、download(2).jpg のように名前変更して、操作を中断せず両方のファイルを保持します。\n• スマートダイアログ: ビット単位で同一のファイルは引き続き静かにスキップし、名前が同じで内容が異なる場合のみ確認します（スキップ / 上書き / 両方保持）。 |
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
| 487 | `date_tie_break_ask_info_message` | Smart dialog and Automatic mode exist for one reason: to skip files whose content is already present, so you only see the files that really need your attention — same name, different content. That is exactly the case where comparing thumbnails can reveal bit-rot.\n\nIf you choose "Always ask" here, every identical file with a different date comes back to you as a conflict. In Smart dialog you will see two thumbnails of the very same picture, with nothing to verify. In Automatic mode the identical file is written again as name(1), filling your folders with pointless copies.\n\nUse this option only if you deliberately want to review every date difference, for example to fix a wrong date by hand.\n\nNote: when content is bit-identical, the EXIF capture date is identical too. A date difference means one side lost its EXIF data and fell back to the file modification date (the copy date). | スマートダイアログと自動モードは、内容がすでに存在するファイルをスキップし、本当に注意が必要なファイル（名前は同じだが内容が異なるもの）だけを表示するために存在します。まさにこのような場合に、サムネイルの比較によってビットロット（データの劣化）を検出できます。\n\nここで\"毎回確認\"を選択すると、日付が異なる同一ファイルがすべて競合として再び表示されます。スマートダイアログでは同じ画像のサムネイルが2つ表示され、確認するものが何もありません。自動モードでは同一ファイルが name(1) として再度書き込まれ、フォルダーが無意味なコピーで埋まります。\n\nこのオプションは、誤った日付を手動で修正するなど、すべての日付の違いを意図的に確認したい場合にのみ使用してください。\n\n注: 内容がビット単位で同一の場合、EXIF の撮影日も同一です。日付が異なるということは、いずれかの側が EXIF データを失い、ファイルの更新日時（コピーした日時）にフォールバックしたことを意味します。 |
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
| 502 | `virtual_folders` | Virtual folders | 仮想フォルダー |
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
| 513 | `about_fork_description` | Calypso Gallery is an independently maintained, unofficial personal fork of Fossify Gallery. It is not affiliated with or endorsed by Fossify. | Calypso Gallery は、Fossify Gallery の独立して保守された非公式の個人フォークです。Fossify とは提携しておらず、Fossify によって推奨もされていません。 |
| 514 | `about_modification_notice` | This distribution is a modified version of Fossify Gallery. The fork was created from upstream commit 054228be2da275396eb03d2b9002807b5055afac (1.14.1-5-g054228be2), and all fork-specific modifications are documented in FORK_CHANGELOG.md. | この配布物は Fossify Gallery の改造版です。このフォークはアップストリームのコミット 054228be2da275396eb03d2b9002807b5055afac (1.14.1-5-g054228be2) から作成されており、フォーク固有のすべての変更は FORK_CHANGELOG.md に記載されています。 |
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
| 7 | `onboarding_albums_desc` | Virtual folders let you bring together the photos and videos you want to view in as many albums as you like, without moving them from their folders or taking up extra space with copies. | 仮想フォルダーを使えば、元のフォルダーから移動したりコピーで容量を消費したりすることなく、見たい写真や動画を好きなだけアルバムにまとめられます。 | Welcome card 3 description |
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

## Existing Vault Time Recalculation (this revision)

Added `brute_force_time_value_device` and `brute_force_time_out_of_range` to `values-ja/strings.xml`. The Settings/vault-setup summary now shows the existing vault's real unlock time on the current device (measured on unlock, otherwise estimated from the portable `.vault` KDF cost), with a note when it exceeds the 10 s maximum, showing only the stored parameters when this device has no measurement yet.

| # | Key (`name`) | English (`values/strings.xml`) | Japanese (`values-ja/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_time_value_device` | %1$.1f s (on this device) | %1$.1f 秒（この端末） | Value shown for an existing vault |
| 2 | `brute_force_time_out_of_range` | This time is above the selectable maximum (10 s). Tap to change it. | この時間は選択可能な上限（10 秒）を超えています。変更するにはタップしてください。 | Shown when the real time exceeds 10 s |

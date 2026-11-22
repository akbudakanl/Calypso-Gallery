# Spanish (Standard, values-es) Translation Audit, Revision & English 1-to-1 Comparison

## New fork strings - bottom actions, view switch, biometrics and vault UI (16 keys)

Added to `values-es/strings.xml`. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `show_bottom_action_names` | Show action names in the bottom action bar | Mostrar los nombres de las acciones en la barra de acciones inferior | Fullscreen media setting, default ON |
| 2 | `show_albums` | Show albums | Mostrar álbumes | Files-view overflow item that returns to the album view |
| 3 | `vault_biometric_offer_title` | Biometric access to the vault | Acceso biométrico a la bóveda | One-time offer after first vault creation |
| 4 | `vault_biometric_offer_message` | Open the vault with your fingerprint or face instead of a password. You don\'t need to enter your password again. | Abre la bóveda con tu huella o rostro en lugar de una contraseña. No necesitas volver a introducirla. | Same |
| 5 | `vault_biometric_offer_accept` | Set up biometric access | Configurar acceso biométrico | Positive button |
| 6 | `vault_biometric_offer_decline` | Not now | Ahora no | Negative button |
| 7 | `password_strength_bad` | Very weak | Muy débil | Vault password strength |
| 8 | `password_strength_poor` | Weak | Débil | Vault password strength |
| 9 | `password_strength_weak` | Fair | Aceptable | Vault password strength |
| 10 | `password_strength_good` | Strong | Fuerte | Vault password strength |
| 11 | `password_strength_excellent` | Very strong | Muy fuerte | Vault password strength |
| 12 | `password_strength_hint` | A longer password with mixed characters is harder to crack. | Una contraseña larga con caracteres variados es más difícil de descifrar. | Shown before typing |
| 13 | `vault_unlock_time_unit` | s | s | Localized seconds unit |
| 14 | `search_in_storage` | Search in storage | Buscar en el almacenamiento | Second option in unencrypted copy destination picker |
| 15 | `password_must_be_at_least_4_characters` | Password must be at least 4 characters long | La contraseña debe tener al menos 4 caracteres | Alphanumeric minimum length |
| 16 | `biometric_disable_confirmation` | Confirm your biometric identity to disable biometric vault access | Confirme su identidad biométrica para desactivar el acceso biométrico a la bóveda | Shown in biometric confirmation prompt |

## ADR 0003 — resumable file operations (7 new keys)

Added to `values-es/strings.xml`. Placeholders (`%1$s`, `%2$s`, `%1$d`) are preserved exactly.

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `space_unverified_warning` | Free space could not be verified (%2$s). About %1$s is required; the operation will be attempted and may stop if space runs out. | No se pudo verificar el espacio libre (%2$s). Se requieren aproximadamente %1$s; se intentará la operación y podría detenerse si se agota el espacio. | SAF/OTG free-space warning; `%1$s` = required size, `%2$s` = reason |
| 2 | `resume_operations_title` | Interrupted operations | Operaciones interrumpidas | One-time process-death dialog title |
| 3 | `resume_operations_message` | %1$d operation(s) were interrupted. No data was lost — continue where they stopped? | Se interrumpieron %1$d operación(es). No se perdió ningún dato; ¿desea continuar donde se detuvieron? | `%1$d` = interrupted operation count |
| 4 | `resume_continue` | Continue | Continuar | Positive dialog button |
| 5 | `resume_later` | Not now | Ahora no | Negative dialog button |
| 6 | `resume_done` | Operations completed. | Operaciones completadas. | Success toast after resume |
| 7 | `resume_failed` | Some operations could not be completed; source files were preserved. | No se pudieron completar algunas operaciones; los archivos de origen se conservaron. | Failure toast; no data loss |
| 8 | `space_unverified_title` | Free space unverified | Espacio libre sin verificar | Explicit-consent dialog title for SAF/OTG free space |
| 9 | `space_continue_anyway` | Continue anyway | Continuar de todos modos | Positive button: proceed without a verified space reading |

> **Scope:** `app/src/main/res/values-es/strings.xml` was audited against `app/src/main/res/values/strings.xml` and `fossify-commons` (`values-es`). Previously, `values-es` contained 257 `<string>` and 2 `<plurals>` entries (missing all 50 fork/new strings). 29 existing entries with mistranslations, typos, missing clauses, formatting bugs, or terminology inconsistencies were fixed, and all 50 missing strings were translated into Standard Spanish (`es`), bringing `values-es` to full parity (347 `<string>` + 2 `<plurals>` = 349 resource entries; `search` and 14 shared UI keys from `fossify-commons`; 361 total comparison rows with Spanish CLDR `one`/`many`/`other` plural forms expanded). Nine keys that were missing from `values-es/strings.xml` were translated and added (`number_of_items`, `random`, `shuffle`, `stop_showing_excluded`, `copy_to_clipboard`, `encrypt`, `decrypt`, `encrypt_album`, `decrypt_album`), and eleven existing entries were revised so that files and albums use the new \"encrypt / decrypt\" wording and so that the favorites import/export labels mention virtual albums as well. Subsequently, 22 additional keys were localized in `values-es/strings.xml`, `hide_folder_description` was removed, and `default_hidden_album` was marked `translatable="false"` (`Default Secret Album`), bringing `values-es/strings.xml` to 372 `<string>` + 2 `<plurals>` = 374 resource entries and 382 total comparison rows.
> **Key Terminology:** `Settings` → `Ajustes` · `Default` → `Predeterminado` / `Sistema por defecto` · `Folder` → `Carpeta` · `Album` → `Álbum` · `Encrypted Vault` → `Bóveda cifrada` · `Hidden` → `Oculto` · `Excluded` → `Excluido` · `Password` → `Contraseña` · `Alphanumeric` → `Alfanumérico` · `Encrypt / Decrypt` → `Cifrar / Descifrar` · `Copy to` → `Copiar a` (matches `fossify-commons`) · `Clipboard` → `Portapapeles` · `Search` → `Buscar` (inherited from `fossify-commons`)

## 1. Audit of Existing Translations & Newly Added Entries

### 1.1 Review of Revised Existing Translations

| # | Key (`name`) | Old `values-es` | New `values-es` | Reason / Notes |
|--:|---|---|---|---|
| 1 | `excluded` | (no incluido) | (excluido) | Aligned with `exclude_folder_description`, `faq_5_text`, `faq_8_text`, and regional Spanish variants |
| 2 | `unpin_folder` | No fijar carpeta | Desfijar carpeta | **Fixed meaning:** `No fijar` means "Do not pin"; `Desfijar` correctly expresses unpinning an already pinned folder |
| 3 | `all_folders` | Todos los medios | Todas las carpetas | **Fixed mistranslation:** English source is `All folders` (not "All media"); matches `show_all` and `values-b+es+419` |
| 4 | `force_landscape_reverse` | Fuerza apaisado (inverso) | Forzar paisaje (inverso) | Standardized infinitive verb and term `paisaje` to match `force_portrait` (`Forzar retrato`) and `force_landscape` (`Forzar paisaje`) |
| 5 | `fixing` | Fijando… | Arreglando… | **Fixed mistranslation:** *Fix* here means repair/correct (matching `Arreglar fecha de la toma`), not pin/fasten |
| 6 | `dates_fixed_successfully` | Fecha fijada correctamente | Fechas arregladas correctamente | **Fixed mistranslation & number:** Plural `Fechas` and `arregladas` instead of `fijada` |
| 7 | `no_date_takens_found` | No se han encontrado valores de fechas tomadas | No se han encontrado valores de fecha de la toma | Aligned terminology with `fix_date_taken` (`fecha de la toma`) |
| 8 | `restore_confirmation` (`many`) | ¿Confirma que quiere restaurar %d de archivos? | ¿Confirma que quiere restaurar %d archivos? | Removed ungrammatical `de` before `archivos` when `%d` renders digits |
| 9 | `full_storage_permission_required` | La galería de Fossify… videos… Configuración… | La Galería Fossify… vídeos… Ajustes… | Standardized `Galería Fossify`, `vídeos`, and `Ajustes` for `es` |
| 10 | `hide_folder_description` | …opción 'Mostrar carpetas ocultas' en los Ajustes. ¿Continuar\? | …opción \'Mostrar elementos ocultos\' en los Ajustes. ¿Continuar? | **Fixed setting name:** matches `fossify-commons` `show_hidden_items` (`Mostrar elementos ocultos`) |
| 11 | `exclude_folder_description` | …sólo de la Galería Fossify… en la Configuración. | …solo de la Galería Fossify… en los Ajustes. | Standardized `Ajustes` and modern RAE `solo` |
| 12 | `excluded_activity_placeholder` | *(literal XML newlines around `\n`)* | `…\n\n…` | Normalized multiline XML whitespace and `solo` |
| 13 | `included_activity_placeholder` | *(literal XML newlines around `\n`)* | `…\n\n…` | Normalized multiline XML whitespace |
| 14 | `cant_unhide_folder` | …estará oculta. Debes eliminar el punto… | …está oculta y no se puede volver a mostrar de esta manera. Debes eliminar el punto… | **Restored omitted clause:** `"and cannot be unhidden like this"` was missing |
| 15 | `failed_to_resize_images` | …de la imagen %d / …de las imágenes %d | …de %d imagen / …de %d imágenes | **Fixed placeholder grammar:** moved `%d` before the noun as a count instead of an index |
| 16 | `overwrite_original` | Sobreescribir original | Sobrescribir original | Fixed spelling (`sobrescribir` with single `e`, matching `error_saving_file`) |
| 17 | `crop` | Cultivos | Recortar | **Fixed critical mistranslation:** `Cultivos` means agricultural crops; image crop is `Recortar` |
| 18 | `by_last_modified_daily` / `_monthly` | Fecha de modificación (Día) / (Mes) | Fecha de modificación (día) / (mes) | Standardized lowercase `(día)` / `(mes)` to match `by_date_taken_daily` / `_monthly` |
| 19 | `by_file_type` | Tipo de arhivo | Tipo de archivo | Fixed typo (`arhivo` → `archivo`) |
| 20 | `allow_instant_change` | Desplazarse en los medios haciendo clic en los lados de la pantalla | Cambiar de medio al instante tocando los lados de la pantalla | Clarified instant media switching on tap |
| 21 | `allow_one_to_one_zoom` | Permitir zoom 1:1 con un toque doble | Permitir zoom 1:1 con dos toques dobles | **Fixed mistranslation:** English source is `two double taps`, not one double tap |
| 22 | `media_only` | Sólo archivos multimedia | Solo archivos multimedia | Standardized modern RAE orthography (`Solo`) |
| 23 | `faq_1_text` | …actualmente por defecto… Configuración… vídeo… Galería de Fossify… | …actualmente predeterminada… ajustes… vídeo… Galería Fossify… | Standardized `Galería Fossify` and natural phrasing |
| 24 | `faq_2_text` | …configuración… \"Borrar datos \". Restablecerá todas sus configuraciones… | …ajustes… \"Borrar datos\". Restablecerá todos sus ajustes… | Removed stray trailing space inside quotes and standardized `ajustes` |
| 25 | `faq_3_text` | …icono Pin… anclar varias carpetas, los artículos fijados… | …icono Fijar… fijar varias carpetas; los elementos fijados… | **Fixed mistranslation:** `artículos` (goods/articles) → `elementos`; unified `Fijar` |
| 26 | `faq_4_text` | …barra de búsqueda… configuración… | …barra de progreso… ajustes… | **Fixed mistranslation:** *seekbar* is `barra de progreso`, not `barra de búsqueda` (search bar) |
| 27 | `faq_6_text` | …presionándolos durante mucho tiempo… | …manteniéndolos pulsados… | Replaced literal phrasing with idiomatic Spanish `manteniéndolos pulsados` |
| 28 | `faq_7_text` / `faq_8_text` | Configuración -&gt; Administrar carpetas… | Ajustes -&gt; Gestionar carpetas… | Matched `fossify-commons` `values-es` (`Ajustes` and `Gestionar carpetas excluidas/incluidas`) |
| 29 | `faq_11_text` / `faq_14_text` / `faq_17_text` / `faq_18_text` | `\"Agrupar por \"` / `\"Mostrar elementos ocultos temporalmente\"` / `no puede navegar… más` / `que aplicación` | `\"Agrupar por\"` / `\"Mostrar ocultos temporalmente\"` / `ya no puede explorar…` / `qué aplicación` | Fixed stray quote space, `fossify-commons` label, broken syntax in FAQ 17, missing accent in `qué`, and mixed `video`/`vídeo` across remaining strings |



### 1.2 Newly Added Fork-Only Strings (66 Keys)

These 66 strings were introduced by this fork (Calypso Gallery) and do not exist in the original Fossify Gallery:

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `video_double_tap_duration` | Double-tap seek duration | Duración de salto con doble toque | Video double-tap forward/rewind interval |
| 2 | `video_seek_duration_seconds` | %d seconds | %d segundos | Used with 3, 5, 10, 15 via `String.format` |
| 3 | `create_virtual_album` | Create virtual album | Crear álbum virtual | Virtual albums section action |
| 4 | `new_virtual_album` | New album… | Nuevo álbum… | Action label for creating a virtual album |
| 5 | `rename_virtual_album` | Rename album | Renombrar álbum | Action label to rename a virtual album |
| 6 | `album_name` | Album name | Nombre del álbum | Input hint/label |
| 7 | `virtual_folder_name` | Virtual album name | Nombre del álbum virtual | Input hint/label |
| 8 | `virtual_album_name_empty` | The album name cannot be empty | El nombre del álbum no puede estar vacío | Validation error |
| 9 | `virtual_album_exists` | An album with this name already exists | Ya existe un álbum con este nombre | Validation error |
| 10 | `add_to_virtual_album` | Add to virtual album | Añadir al álbum virtual | Menu action (`añadir` in `es`) |
| 11 | `remove_from_virtual_album` | Remove from virtual album | Quitar del álbum virtual | Menu action |
| 12 | `added_to_virtual_album` | Added to \"%s\" | Añadido a \"%s\" | Toast confirmation |
| 13 | `already_in_virtual_album` | Already in \"%s\" | Ya está en \"%s\" | Toast message |
| 14 | `removed_from_virtual_album` | Removed from the album | Quitado del álbum | Toast message |
| 15 | `delete_virtual_album_confirmation` | Delete the album %s? Only the album is removed, your photos and videos stay where they are. | ¿Eliminar el álbum %s? Solo se eliminará el álbum; tus fotos y vídeos permanecerán donde están. | No extra quotes around `%s`; uses `vídeos` for `es` |
| 16 | `virtual_album_empty` | This album is empty. Select items in any folder and use \"Add to virtual album\". | Este álbum está vacío. Selecciona elementos en cualquier carpeta y usa \"Añadir al álbum virtual\". | References `add_to_virtual_album` (`Añadir al álbum virtual`) |
| 17 | `virtual_album_cannot_be_modified` | This action is not available for virtual albums | Esta acción no está disponible para álbumes virtuales | Toast error |
| 18 | `alphanumeric_password` | Alphanumeric | Alfanumérico | Tab label for the alphanumeric secret option; distinct from the generic password label |
| 19 | `repeat_password` | Repeat password | Repetir contraseña | Matches `fossify-commons` `repeat_pin` (`Repetir PIN`) |
| 20 | `wrong_password` | Wrong password | Contraseña incorrecta | Password verification error |
| 21 | `encrypting_files` | Encrypting files… | Cifrando archivos… | Standard Spanish crypto terminology (`cifrar`) |
| 22 | `encrypting_folder` | Encrypting folder… | Cifrando carpeta… | Progress title |
| 23 | `folder_encrypted_successfully` | Folder encrypted successfully | Carpeta cifrada correctamente | Success toast |
| 24 | `decrypting_folder` | Decrypting folder… | Descifrando carpeta… | Progress title |
| 25 | `folder_decrypted_successfully` | Folder decrypted successfully | Carpeta descifrada correctamente | Success toast |
| 26 | `decrypting_files` | Decrypting files… | Descifrando archivos… | Progress toast |
| 27 | `place_finger_prompt` | Please place your finger on the fingerprint sensor | Por favor, ponga el dedo en el sensor de huellas | Matches `usted` register and `sensor de huellas` |
| 28 | `connect_at_least_4_dots_prompt` | Connect at least 4 dots | Conecta al menos 4 puntos | Pattern lock minimum dots validation |
| 29 | `access_existing_secrets_prompt` | Do you want to access your existing Secrets folder? | ¿Desea acceder a su carpeta Secrets existente? | Keeps `Secrets` literal (`usted` register) |
| 30 | `delete_and_recreate_secrets_prompt` | Existing Secrets folder will be deleted and a new one will be created. Are you sure? | Se eliminará la carpeta Secrets existente y se creará una nueva. ¿Está seguro? | Keeps `Secrets` literal (`usted` register) |
| 31 | `confirm_checkbox_label` | I confirm | Confirmo | Checkbox label in `DeleteCipherSecretsDialog` |
| 32 | `secret_vault` | Encrypted Vault | Bóveda cifrada | Vault section title |
| 33 | `secret_vault_empty` | Encrypted Vault is empty | La bóveda cifrada está vacía | Empty state message |
| - | `press_back_again_to_exit_vault` | Press back again to exit | Pulsa atrás de nuevo para salir | Toast shown on first back press in hidden vault |
| - | `block_vault_screenshots` | Block screenshots in encrypted vault | Bloquear capturas de pantalla en la bóveda cifrada | Settings switch (default ON) blocking screenshots in encrypted vault |
| 34 | `confirm_decrypt_hidden_file` | Are you sure you want to decrypt the hidden file(s)? | ¿Está seguro de que desea descifrar los archivos ocultos? | Decryption confirmation (`usted` register) |
| 35 | `copy_to_clipboard_disabled_in_vault` | Copying to clipboard is disabled while vault is unlocked for security. | Por seguridad, copiar al portapapeles está deshabilitado mientras la bóveda está desbloqueada. | Clipboard security notice |
| 36 | `open_system_language_settings` | Open system language settings | Abrir ajustes de idioma del sistema | Uses `ajustes` for `es` |
| 37 | `file_encrypted_successfully` | File encrypted successfully | Archivo cifrado correctamente  | Success toast after encrypting a single file |
| 38 | `files_encrypted_successfully` | Files encrypted successfully | Archivos cifrados correctamente  | Success toast after encrypting multiple files |
| 39 | `file_decrypted_successfully` | File decrypted successfully | Archivo descifrado correctamente | Success toast after decrypting a single file |
| 40 | `files_decrypted_successfully` | Files decrypted successfully | Archivos descifrados correctamente | Success toast after decrypting multiple files |
| 41 | `access_hidden_items_with_biometrics` | Access hidden items with biometrics | Acceder a elementos ocultos con biometría | Settings switch, setup prompt title, and vault security dialog button |
| 42 | `enable_hidden_protection_first_prompt` | Please enable hidden item password protection first | Por favor, active primero la protección con contraseña para elementos ocultos | Toast shown when enabling biometrics before hidden-item password protection |
| 43 | `biometric_unlock_vault` | Unlock encrypted vault | Desbloquear bóveda cifrada | BiometricPrompt title when unlocking the encrypted vault |
| 44 | `biometric_unlock_prompt` | Verify your biometric identity to access hidden items | Verifique su identidad biométrica para acceder a los elementos ocultos | BiometricPrompt subtitle when unlocking hidden items |
| 45 | `biometric_setup_prompt` | Confirm your biometric identity to enable biometric vault access | Confirme su identidad biométrica para activar el acceso biométrico a la bóveda | BiometricPrompt subtitle when enabling biometric vault access |
| 46 | `use_password_or_pin` | Use PIN / Password | Usar PIN / Contraseña | BiometricPrompt negative button to fall back to PIN/password |
| 47 | `biometric_key_invalidated` | Biometric credentials have changed. Please enter your PIN or password. | Las credenciales biométricas han cambiado. Por favor, introduzca su PIN o contraseña. | Toast shown when biometric credentials changed and Keystore key invalidated |
| 48 | `no_biometric_hardware_or_enrolled` | No biometric hardware or enrolled credentials found on this device | No se ha encontrado hardware biométrico ni credenciales registradas en este dispositivo | Toast shown when no biometric hardware or enrolled credentials exist |
| 49 | `default_hidden_album` | Default Secret Album | Álbum oculto predeterminado | Settings / picker label for default hidden album |
| 50 | `select_hidden_album` | Select Encrypted Album | Seleccionar álbum cifrado | Dialog title for choosing the target encrypted album |
| 51 | `create_new_hidden_album` | Create New Encrypted Album | Crear nuevo álbum cifrado | Action label to create a new encrypted album |
| 52 | `new_hidden_album` | New encrypted album | Nuevo álbum cifrado | Input hint / default label for a new encrypted album |
| 53 | `hidden_album_name_empty` | Album name cannot be empty | El nombre del álbum no puede estar vacío | Validation error when hidden album name is blank |
| 54 | `file_hidden_to_album` | File encrypted to \"%s\" | Archivo cifrado en \"%s\"  | Success toast after encrypting a single file into album `%s` |
| 55 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d archivos cifrados en \"%2$s\"  | Success toast after encrypting `%1$d` files into album `%2$s` |
| 56 | `pull_down_to_open_vault` | Keep pulling down for Encrypted Vault | Siga deslizando hacia abajo para la bóveda | Vault pull gesture hint shown while the pull is below the open threshold |
| 57 | `keep_holding_to_open_vault` | Keep holding to open Encrypted Vault | Siga manteniendo pulsado para abrir la bóveda cifrada | Vault pull gesture hint shown once the pull passes the threshold, before the 2.5 s hold completes |
| 58 | `release_to_open_vault` | Release to open Encrypted Vault | Suelte para abrir la bóveda cifrada | Vault pull gesture hint shown after holding the pull for 2.5 s; releasing opens the vault |

### 1.3 Newly Added Translations for Strings Already Present in the Original Fossify Gallery (3 Keys)

These 3 strings already exist in the original Fossify Gallery (upstream); only the translations below were newly added in this fork:

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `enter_password` | Enter password | Introduzca la contraseña | Matches `fossify-commons` `values-es` `enter_password` |
| 2 | `system_default` | System default | Sistema por defecto | Matches `fossify-commons` `values-es` |
| 3 | `language` | Language | Idioma | Language selector title |

### 1.4 `fossify-commons` Inherited UI Strings (14 Keys)

All 14 shared UI keys below are already localized in `org.fossify:commons:7.1.3` (`res/values-es/values-es.xml`); no local override in `values-es/strings.xml` was required:

| # | Key (`name`) | English (`fossify-commons`) | Spanish (`fossify-commons`) | Source / Notes |
|---:|---|---|---|---|
| 1 | `change_date_and_time_format` | Change date and time format | Cambiar formato de fecha y hora | Inherited from `fossify-commons` (Settings) |
| 2 | `password_protect_file_deletion` | Password protect file deletion and moving | Proteger con contraseña eliminar y mover archivos | Inherited from `fossify-commons` (Settings) |
| 3 | `keep_last_modified` | Keep old last-modified value at file operations | Mantener el último valor modificado por última vez en las operaciones de archivo | Inherited from `fossify-commons` (Settings) |
| 4 | `clear_cache` | Clear cache | Limpiar cache | Inherited from `fossify-commons` (Settings) |
| 5 | `export_settings` | Export settings | Exportar ajustes | Inherited from `fossify-commons` (Settings) |
| 6 | `import_settings` | Import settings | Importar ajustes | Inherited from `fossify-commons` (Settings) |
| 7 | `temporarily_show_excluded` | Temporarily show excluded | Mostrar excluidos temporalmente | Inherited from `fossify-commons` (Options menu) |
| 8 | `change_view_type` | Change view type | Cambiar tipo de vista | Inherited from `fossify-commons` (Options menu) |
| 9 | `contributors` | Contributors | Contribuyentes | Inherited from `fossify-commons` (About) |
| 10 | `known_issues` | Known issues | Problemas conocidos | Inherited from `fossify-commons` (About) |
| 11 | `donate_to_fossify` | Donate to Fossify | Apoya a Fossify | Inherited from `fossify-commons` (About) |
| 12 | `privacy_policy` | Privacy policy | Política de privacidad | Inherited from `fossify-commons` (About) |
| 13 | `create_shortcut` | Create shortcut | Crear atajo | Inherited from `fossify-commons` (CAB / ViewPager menu) |
| 14 | `print` | Print | Imprimir | Inherited from `fossify-commons` (ViewPager menu) |
| 15 | `search_files` | Search files | Buscar archivos | Inherited from `fossify-commons` (Search bar hint) |
| 16 | `search_folders` | Search folders | Buscar carpetas | Inherited from `fossify-commons` (Search bar hint) |

### Newly Added Missing Translations (9 Keys) & Revised Entries (11 Keys)

Nine keys that were missing from `values-es/strings.xml` were translated and added, and eleven existing entries were revised so that files and albums use the new \"encrypt / decrypt\" wording and so that the favorites import/export labels mention virtual albums as well.

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (Standard) (`values-es/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | `number_of_items` | Number of items | Número de elementos | Sorting option shown in the album view only; overridden locally because it is missing in `fossify-commons` for this locale |
| 2 | `random` | Random | Aleatorio | Sorting option offered in every sorting state; overridden locally because it is missing in `fossify-commons` for this locale |
| 3 | `shuffle` | Shuffle | Mezclar | Fullscreen viewer action that randomizes the media order; app owned string that was only available in English |
| 4 | `stop_showing_excluded` | Stop showing excluded | Dejar de mostrar los excluidos | Main screen options-menu action that ends the temporary preview of excluded folders |
| 5 | `copy_to_clipboard` | Copy to clipboard | Copiar al portapapeles | Fullscreen viewer action that copies a file to the clipboard |
| 6 | `encrypt` | Encrypt | Cifrar | Replaces the inherited `hide` label for files; files and albums are encrypted, not hidden |
| 7 | `decrypt` | Decrypt | Descifrar | Replaces the inherited `unhide` label for files |
| 8 | `encrypt_album` | Encrypt album | Cifrar el álbum | Album (folder) context action that encrypts the album into the vault |
| 9 | `decrypt_album` | Decrypt album | Descifrar el álbum | Album (folder) context action that decrypts the album back to its original location |
| 10 | `export_favorite_paths` | Export favorites and virtual albums | Exportar favoritos y álbumes virtuales | Settings label; renamed because virtual albums are exported together with the favorites |
| 11 | `import_favorite_paths` | Import favorites and virtual albums | Importar favoritos y álbumes virtuales | Settings label; renamed because virtual albums are imported together with the favorites |
| 12 | `file_encrypted_successfully` | File encrypted successfully | Archivo cifrado correctamente | Success toast after encrypting a single file |
| 13 | `files_encrypted_successfully` | Files encrypted successfully | Archivos cifrados correctamente | Success toast after encrypting multiple files |
| 14 | `file_decrypted_successfully` | File decrypted successfully | Archivo descifrado correctamente | Success toast after decrypting a single file |
| 15 | `files_decrypted_successfully` | Files decrypted successfully | Archivos descifrados correctamente | Success toast after decrypting multiple files |
| 16 | `file_hidden_to_album` | File encrypted to \"%s\" | Archivo cifrado en \"%s\" | Success toast after encrypting a single file into album `%s` |
| 17 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d archivos cifrados en \"%2$s\" | Success toast after encrypting `%1$d` files into album `%2$s` |
| 18 | `select_hidden_album` | Select Encrypted Album | Seleccionar álbum cifrado | Dialog title for choosing the target encrypted album |
| 19 | `create_new_hidden_album` | Create New Encrypted Album | Crear nuevo álbum cifrado | Action label to create a new encrypted album |
| 20 | `new_hidden_album` | New encrypted album | Nuevo álbum cifrado | Input hint / default label for a new encrypted album |

### Newly Added UI, Backup, Encryption Confirmation & Vault Strings (22 Keys)

Following the previous audit, 22 additional keys were localized in `values-es/strings.xml` (`create_new_encrypted_virtual_album` and `new_album` for vault destination dialogs, plus 20 strings covering backup export/import, auto-backup prompt, encryption confirmations, delete-behavior options, album lock/unlock labels, and progress notifications; `hide_folder_description` was removed from the app, and `default_hidden_album` was marked `translatable="false"` as `Default Secret Album`):

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (`values-es/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | `create_new_encrypted_virtual_album` | Create new encrypted virtual album | Crear nuevo álbum virtual cifrado | Dialog option for creating an encrypted virtual album inside the vault |
| 2 | `new_album` | New album | Nuevo álbum | Compact neutral button label in the move destination dialog |
| 3 | `filename_without_zip` | Filename (without .zip) | Nombre de archivo (sin .zip) | Input hint in the export favorites and virtual albums dialog |
| 4 | `favorites_already_exist` | You already have a favorites list | Ya tiene una lista de favoritos | Dialog title when importing favorites while a favorites list already exists |
| 5 | `delete_existing_list` | Delete existing list | Eliminar la lista existente | Radio option to replace the existing favorites list on import |
| 6 | `merge_lists` | Merge lists | Combinar listas | Radio option to merge imported favorites with the existing list |
| 7 | `confirm` | Confirm | Confirmar | Positive button label in the favorites import conflict dialog |
| 8 | `confirm_encrypt_album` | Are you sure you want to encrypt this album? All of its media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | ¿Está seguro de que desea cifrar este álbum? Todos sus archivos multimedia se cifrarán y se moverán a la bóveda cifrada. Los archivos originales se eliminarán. | Confirmation prompt before encrypting a single album into the vault |
| 9 | `confirm_encrypt_albums` | Are you sure you want to encrypt the selected albums? All of their media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | ¿Está seguro de que desea cifrar los álbumes seleccionados? Todos sus archivos multimedia se cifrarán y se moverán a la bóveda cifrada. Los archivos originales se eliminarán. | Confirmation prompt before encrypting multiple selected albums into the vault |
| 10 | `confirm_encrypt_file` | Are you sure you want to encrypt this file? It will be encrypted and moved to the encrypted vault. The original file will be deleted. | ¿Está seguro de que desea cifrar este archivo? Se cifrará y se moverá a la bóveda cifrada. El archivo original se eliminará. | Confirmation prompt before encrypting a single file into the vault |
| 11 | `confirm_encrypt_files` | Are you sure you want to encrypt the selected files? They will be encrypted and moved to the encrypted vault. The original files will be deleted. | ¿Está seguro de que desea cifrar los archivos seleccionados? Se cifrarán y se moverán a la bóveda cifrada. Los archivos originales se eliminarán. | Confirmation prompt before encrypting multiple selected files into the vault |
| 12 | `confirm_encrypt_files_to_vault` | The selected files will be encrypted when they are copied into the encrypted vault. Do you want to continue? | Los archivos seleccionados se cifrarán al copiarse en la bóveda cifrada. ¿Desea continuar? | Confirmation prompt when copying unencrypted files into an encrypted vault destination |
| 13 | `encrypt_decrypt_notifications` | Encrypt / Decrypt | Cifrar / Descifrar | Notification channel name for ongoing encrypt/decrypt progress notifications |
| 14 | `allow_batch_renaming` | Allow batch renaming when multiple files are selected | Permitir renombrado por lotes cuando se seleccionan varios archivos | Retained in values/strings.xml for parity (its Settings switch was removed in a6a78d444) |
| 15 | `keep_original_remove_from_virtual_album` | Keep the original file, remove it only from this virtual album | Conservar el archivo original, quitarlo solo de este álbum virtual | Checkbox in delete confirmation dialog when deleting from a virtual album |
| 16 | `keep_original_remove_from_favorites` | Keep the original file, remove it only from favorites | Conservar el archivo original, quitarlo solo de favoritos | Checkbox in delete confirmation dialog when deleting from favorites |
| 17 | `delete_original_from_favorites_and_virtual_albums` | Delete the original version of a file when it is deleted from favorites and virtual albums | Eliminar la versión original de un archivo cuando se elimine de favoritos y álbumes virtuales | Settings switch controlling default delete behavior from favorites and virtual albums |
| 18 | `cannot_target_encrypted_album` | Encrypted albums cannot be targeted for this operation | No se pueden seleccionar álbumes cifrados como destino para esta operación | Toast shown when an encrypted album cannot be targeted for an operation |
| 19 | `cannot_mix_encrypted_and_unencrypted` | Cannot combine encrypted and unencrypted items in this operation | No se pueden combinar elementos cifrados y sin cifrar en esta operación | Toast shown when an operation attempts to combine encrypted and unencrypted items |
| 20 | `lock_album` | Lock album | Bloquear álbum | Album selection menu action replacing lock_folder |
| 21 | `unlock_album` | Unlock album | Desbloquear álbum | Album selection menu action replacing unlock_folder |
| 22 | `auto_backup_prompt_message` | Do you want your favorites and virtual albums to be regularly written to system storage outside of app memory? This will ensure that you do not lose your favorites list and virtual albums if you uninstall the app. You can manually create a backup from app settings at any time. | ¿Desea que sus favoritos y álbumes virtuales se guarden periódicamente en el almacenamiento del sistema fuera de la memoria de la aplicación? Esto garantizará que no pierda su lista de favoritos ni sus álbumes virtuales si desinstala la aplicación. Puede crear una copia de seguridad manualmente desde los ajustes de la aplicación en cualquier momento. | Full-screen prompt asking whether to regularly back up favorites and virtual albums to shared storage |
| 23 | `auto_backup_favorites_and_virtual_albums` | Back up favorites and virtual albums to device storage | Hacer copia de seguridad de favoritos y álbumes virtuales en el almacenamiento del dispositivo | Settings switch under File operations (shown after first auto-backup prompt) |

## 2. Full 1-to-1 Comparison: English vs. Spanish (Standard)

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (`values-es/strings.xml`) |
|--:|---|---|---|
| 1 | `app_launcher_name` | Calypso Gallery | Galería |
| 2 | `edit` | Edit | Editar |
| 3 | `open_camera` | Open camera | Abrir la cámara |
| 4 | `hidden` | (hidden) | (oculto) |
| 5 | `excluded` | (excluded) | (excluido) |
| 6 | `pin_folder` | Pin folder | Fijar carpeta |
| 7 | `unpin_folder` | Unpin folder | Desfijar carpeta |
| 8 | `pin_to_the_top` | Pin to the top | Anclar arriba |
| 9 | `show_all` | Show all folders content | Mostrar el contenido de todas las carpetas |
| 10 | `all_folders` | All folders | Todas las carpetas |
| 11 | `folder_view` | Switch to folder view | Cambiar a vista de carpeta |
| 12 | `other_folder` | Other folder | Otra carpeta |
| 13 | `show_on_map` | Show on map | Mostrar en el mapa |
| 14 | `unknown_location` | Unknown location | Ubicación desconocida |
| 15 | `volume` | Volume | Volumen |
| 16 | `brightness` | Brightness | Brillo |
| 17 | `lock_orientation` | Lock orientation | Bloquear orientación |
| 18 | `unlock_orientation` | Unlock orientation | Desbloquear orientación |
| 19 | `change_orientation` | Change orientation | Cambiar orientación |
| 20 | `force_portrait` | Force portrait | Forzar retrato |
| 21 | `force_landscape` | Force landscape | Forzar paisaje |
| 22 | `force_landscape_reverse` | Force landscape (reverse) | Forzar paisaje (inverso) |
| 23 | `use_default_orientation` | Use default orientation | Usar la orientación por defecto |
| 24 | `fix_date_taken` | Fix Date Taken value | Arreglar fecha de la toma |
| 25 | `fixing` | Fixing… | Arreglando… |
| 26 | `dates_fixed_successfully` | Dates fixed successfully | Fechas arregladas correctamente |
| 27 | `no_date_takens_found` | No Date Taken values have been found | No se han encontrado valores de fecha de la toma |
| 28 | `share_resized` | Share a resized version | Compartir una versión redimensionada |
| 29 | `switch_to_file_search` | Switch to file search across all visible folders | Cambiar a la búsqueda de archivos en todas las carpetas visibles |
| 30 | `set_as_default_folder` | Set as default folder | Poner como carpeta predeterminada |
| 31 | `unset_as_default_folder` | Unset as default folder | Quitar como carpeta predeterminada |
| 32 | `reorder_by_dragging` | Reorder folders by dragging | Reordenar carpetas arrastrándolas |
| 33 | `reorder_by_dragging_pro` | Reorder folders by dragging (Pro) | Reordenar carpetas arrastrándolas (Pro) |
| 34 | `restore_to_path` | Restoring to \'%s\' | Restaurando a \'%s\' |
| 35 | `full_storage_permission_required` | Calypso Gallery needs full access to display all your photos and videos. Go to Settings > Permissions > Photos and videos > Allow all. | La Galería Fossify necesita acceso total para mostrar todas tus fotos y vídeos. Ve a Ajustes &gt; Permisos &gt; Fotos y vídeos &gt; Permitir todo. |
| 36 | `filter_media` | Filter media | Filtro de medios |
| 37 | `images` | Images | Imágenes |
| 38 | `videos` | Videos | Vídeos |
| 39 | `gifs` | GIFs | Imágenes con animación |
| 40 | `raw_images` | RAW images | Imágenes RAW |
| 41 | `svgs` | SVGs | SVG |
| 42 | `portraits` | Portraits | Retratos |
| 43 | `no_media_with_filters` | No media files have been found with the selected filters. | No se han encontrado ficheros con los filtros seleccionados. |
| 44 | `change_filters_underlined` | <u>Change filters</u> | <u>Cambiar filtros</u> |
| 45 | `exclude_folder_description` | This will exclude the selection together with its subfolders from Calypso Gallery only. You can manage excluded folders in Settings. | Esto excluirá la selección junto con sus subcarpetas solo de la Galería Fossify. Puede gestionar las carpetas excluidas en los Ajustes. |
| 46 | `exclude_folder_parent` | Exclude a parent instead? | ¿Excluir mejor la carpeta superior? |
| 47 | `excluded_activity_placeholder` | Excluding folders will make them together with their subfolders hidden just in Calypso Gallery, they will still be visible in other applications.\n\nIf you want to hide them from other apps too, use the Encrypt function. | Al excluir las carpetas, estas y sus subcarpetas quedarán ocultas solo en la Galería Fossify, pero seguirán siendo visibles en otras aplicaciones.\n\nSi desea ocultarlas también de otras aplicaciones, utilice la función Ocultar. |
| 48 | `hidden_folders` | Hidden folders | Carpetas ocultas |
| 49 | `manage_hidden_folders` | Manage hidden folders | Gestionar carpetas ocultas |
| 50 | `hidden_folders_placeholder` | Seems like you don\'t have any folders hidden with a \".nomedia\" file or dot prefix. | Parece que no tiene carpetas ocultas con archivos \".nomedia\". |
| 51 | `hidden_all_files` | You have to grant the app All Files access to see hidden files, else it cannot work. | Tienes que conceder a la aplicación el acceso a todos los archivos para ver los archivos ocultos, de lo contrario no puede funcionar. |
| 52 | `cant_unhide_folder` | If a folder or one of its parent folders has a dot before its name, it is hidden and cannot be unhidden like this. You have to remove the dot by renaming it. | Si una carpeta o una de sus carpetas principales tiene un punto antes del nombre, está oculta y no se puede volver a mostrar de esta manera. Debes eliminar el punto cambiándole el nombre. |
| 53 | `include_folders` | Included folders | Carpetas incluidas |
| 54 | `manage_included_folders` | Manage included folders | Gestionar carpetas incluidas |
| 55 | `add_folder` | Add folder | Agregar carpeta |
| 56 | `included_activity_placeholder` | If you have some folders which contain media, but were not recognized by the app, you can add them manually here.\n\nAdding some items here will not exclude any other folder. | Si tiene algunas carpetas que contengan multimedia, pero que no fueron reconocidas por la aplicación, puede añadirlas manualmente aquí.\n\nAñadir elementos aquí no excluirá ninguna otra carpeta. |
| 57 | `no_media_add_included` | No media files have been found. You can solve it by adding the folders containing media files manually. | Ningún archivo multimedia ha sido encontrado. Puede resolverlo añadiendo las carpetas que contengan archivos multimedia manualmente. |
| 58 | `resize_and_save` | Resize selection and save | Redimensionar la selección y guardar |
| 59 | `width` | Width | Ancho |
| 60 | `height` | Height | Alto |
| 61 | `keep_aspect_ratio` | Keep aspect ratio | Mantener proporciones |
| 62 | `invalid_values` | Please enter a valid resolution | Por favor, introduzca una resolución válida |
| 63 | `resize_multiple_images` | Resize multiple images | Redimensionar varias imágenes |
| 64 | `resize_factor` | Resize factor | Factor de redimensión |
| 65 | `resize_factor_info` | Resize images to the given percentage, value must be within 10 and 90. | Redimensiona las imágenes al porcentaje dado, el valor debe estar entre 10 y 90. |
| 66 | `resize_factor_error` | Enter a number between 10 and 90 | Introduce un número entre 10 y 90 |
| 67 | `images_resized_successfully` | Images resized successfully | Imágenes redimensionadas correctamente |
| 68 | `editor` | Editor | Editor |
| 69 | `basic_editor` | Basic Editor | Editor básico |
| 70 | `advanced_editor` | Advanced Editor | Editor avanzado |
| 71 | `rotate` | Rotate | Rotar |
| 72 | `invalid_image_path` | Invalid image path | Ruta de imagen inválida |
| 73 | `invalid_video_path` | Invalid video path | Ruta de vídeo inválida |
| 74 | `image_editing_failed` | Image editing failed | Edición de imagen fallida |
| 75 | `video_editing_failed` | Video editing failed | Edición de vídeo fallida |
| 76 | `image_editing_cancelled` | Image editing cancelled | Edición de imagen cancelada |
| 77 | `video_editing_cancelled` | Video editing cancelled | Edición de vídeo cancelada |
| 78 | `file_edited_successfully` | File edited successfully | Archivo editado con éxito |
| 79 | `image_edited_successfully` | Image edited successfully | Imagen editada con éxito |
| 80 | `video_edited_successfully` | Video edited successfully | Vídeo editado con éxito |
| 81 | `edit_image_with` | Edit image with: | Editar imagen con: |
| 82 | `edit_video_with` | Edit video with: | Editar vídeo con: |
| 83 | `no_image_editor_found` | No image editor found | No se encontró editor de imágenes |
| 84 | `no_video_editor_found` | No video editor found | No se encontró editor de vídeo |
| 85 | `unknown_file_location` | Unknown file location | Ubicación del archivo desconocida |
| 86 | `overwrite_original` | Overwrite original | Sobrescribir original |
| 87 | `error_saving_file` | Could not overwrite the source file | No se ha podido sobrescribir el archivo de origen |
| 88 | `rotate_left` | Rotate left | Rotar a la izquierda |
| 89 | `rotate_right` | Rotate right | Rotar a la derecha |
| 90 | `rotate_one_eighty` | Rotate by 180º | Rotar 180º |
| 91 | `transform` | Transform | Transformar |
| 92 | `crop` | Crop | Recortar |
| 93 | `draw` | Draw | Dibujar |
| 94 | `flip` | Flip | Girar |
| 95 | `flip_horizontally` | Flip horizontally | Horizontalmente |
| 96 | `flip_vertically` | Flip vertically | Verticalmente |
| 97 | `free_aspect_ratio` | Free | Libre |
| 98 | `other_aspect_ratio` | Other | Otro |
| 99 | `wallpaper` | Wallpaper | Fondo de pantalla |
| 100 | `set_as_wallpaper` | Set as Wallpaper | Establecer como fondo de pantalla |
| 101 | `set_as_wallpaper_failed` | Setting as Wallpaper failed | Error al establecer el fondo de pantalla |
| 102 | `set_as_wallpaper_with` | Set as wallpaper with: | Establecer como fondo de pantalla con: |
| 103 | `setting_wallpaper` | Setting wallpaper… | Estableciendo fondo de pantalla… |
| 104 | `wallpaper_set_successfully` | Wallpaper set successfully | Fondo de pantalla establecido correctamente |
| 105 | `portrait_aspect_ratio` | Portrait aspect ratio | Relación de aspecto tipo retrato |
| 106 | `landscape_aspect_ratio` | Landscape aspect ratio | Relación de aspecto tipo paisaje |
| 107 | `home_screen` | Home screen | Pantalla de inicio |
| 108 | `lock_screen` | Lock screen | Pantalla de bloqueo |
| 109 | `home_and_lock_screen` | Home and lock screen | Pantalla de inicio y de bloqueo |
| 110 | `allow_changing_aspect_ratio` | Allow changing the aspect ratio | Permitir cambiar la relación de aspecto |
| 111 | `slideshow` | Slideshow | Presentación de diapositivas |
| 112 | `interval` | Interval | Intervalo |
| 113 | `include_photos` | Include photos | Incluir fotos |
| 114 | `include_videos` | Include videos | Incluir vídeos |
| 115 | `include_gifs` | Include GIFs | Incluir GIF |
| 116 | `random_order` | Random order | Orden aleatorio |
| 117 | `move_backwards` | Move backwards | Mover hacia atrás |
| 118 | `loop_slideshow` | Loop slideshow | Presentación en bucle |
| 119 | `animation` | Animation | Animación |
| 120 | `no_animation` | None | Ninguna |
| 121 | `fade` | Fade | Desvanecimiento |
| 122 | `slide` | Slide | Desplazamiento |
| 123 | `slideshow_ended` | The slideshow ended | La presentación de diapositivas terminó |
| 124 | `no_media_for_slideshow` | No media for the slideshow have been found | No se han encontrado medios para la presentación de diapositivas |
| 125 | `group_direct_subfolders` | Group direct subfolders | Agrupar subcarpetas directas |
| 126 | `group_by` | Group by | Agrupar por |
| 127 | `do_not_group_files` | Do not group files | No agrupar |
| 128 | `by_folder` | Folder | Carpeta |
| 129 | `by_last_modified` | Last modified | Fecha de modificación |
| 130 | `by_last_modified_daily` | Last modified (daily) | Fecha de modificación (día) |
| 131 | `by_last_modified_monthly` | Last modified (monthly) | Fecha de modificación (mes) |
| 132 | `by_date_taken` | Date taken | Fecha de creación |
| 133 | `by_date_taken_daily` | Date taken (daily) | Fecha de creación (día) |
| 134 | `by_date_taken_monthly` | Date taken (monthly) | Fecha de creación (mes) |
| 135 | `by_file_type` | File type | Tipo de archivo |
| 136 | `by_extension` | Extension | Extensión |
| 137 | `show_file_count_section_header` | Show file count at section headers | Mostrar recuento de archivos en los encabezados de sección |
| 138 | `grouping_and_sorting` | Please note that grouping and sorting are 2 independent fields | Tenga en cuenta que la agrupación y la clasificación son 2 campos independientes |
| 139 | `folder_on_widget` | Folder shown on the widget: | Carpeta mostrada en el widget: |
| 140 | `show_folder_name` | Show folder name | Mostrar nombre de carpeta |
| 141 | `autoplay_videos` | Play videos automatically | Reproducir vídeos automáticamente |
| 142 | `remember_last_video_position` | Remember last video playback position | Recordar la última posición de avance en la reproducción de vídeo |
| 143 | `loop_videos` | Loop videos | Repetir reproducción de vídeos en bucle |
| 144 | `video_double_tap_duration` | Double-tap seek duration | Duración de salto con doble toque |
| 145 | `video_seek_duration_seconds` | %d seconds | %d segundos |
| 146 | `animate_gifs` | Animate GIFs at thumbnails | Animar las miniaturas de GIF |
| 147 | `max_brightness` | Max brightness when viewing fullscreen media | Incrementar el brillo de la pantalla al máximo cuando se muestran los archivos multimedia |
| 148 | `render_ultra_hdr_photos` | Show Ultra HDR photos in HDR | Mostrar las fotos Ultra HDR en HDR |
| 149 | `crop_thumbnails` | Crop thumbnails into squares | Recortar miniaturas en cuadrados |
| 150 | `show_thumbnail_video_duration` | Show video durations | Mostrar la duración de los vídeos |
| 151 | `screen_rotation_by` | Rotate fullscreen media by | Rotar multimedia en pantalla completa según |
| 152 | `screen_rotation_system_setting` | System setting | Ajustes del sistema |
| 153 | `screen_rotation_device_rotation` | Device rotation | Rotación del dispositivo |
| 154 | `screen_rotation_aspect_ratio` | Aspect ratio | Relación de aspecto |
| 155 | `black_background_at_fullscreen` | Black background at fullscreen media | Fondo y barra de estado completamente oscuros en medios al estar en pantalla completa |
| 156 | `scroll_thumbnails_horizontally` | Scroll thumbnails horizontally | Desplazar miniaturas horizontalmente |
| 157 | `hide_system_ui_at_fullscreen` | Automatically hide system UI at fullscreen media | Ocultar la interfaz del sistema en pantalla completa |
| 158 | `delete_empty_folders` | Delete empty folders after deleting their content | Eliminar carpetas vacías después de borrar su contenido |
| 159 | `keep_screen_on_fullscreen_photos` | Keep the screen on while viewing fullscreen photos | Mantener pantalla encendida al ver fotos en pantalla completa |
| 160 | `allow_photo_gestures` | Allow controlling photo brightness with vertical gestures | Permitir controlar el brillo en las fotos con gestos verticales |
| 161 | `allow_video_gestures` | Allow controlling video volume and brightness with vertical gestures | Permitir controlar el volumen y el brillo del vídeo con gestos verticales |
| 162 | `playback_speed` | Playback speed | Velocidad de reproducción |
| 163 | `show_media_count` | Show folder media count on the main view | Mostrar el número de archivos dentro de las carpetas en la página principal |
| 164 | `show_extended_details` | Show extended details over fullscreen media | Mostrar información de los archivos en pantalla completa |
| 165 | `manage_extended_details` | Manage extended details | Información de los archivos a mostrar |
| 166 | `one_finger_zoom` | Allow one finger zoom at fullscreen media | Permitir zoom con un dedo en pantalla completa |
| 167 | `allow_instant_change` | Allow instantly changing media by clicking on screen sides | Cambiar de medio al instante tocando los lados de la pantalla |
| 168 | `allow_deep_zooming_images` | Allow deep zooming images | Permitir zoom profundo |
| 169 | `hide_extended_details` | Hide extended details when status bar is hidden | Ocultar detalles ampliados cuando la barra de estado está oculta |
| 170 | `show_at_bottom` | Show some action buttons at the bottom of the screen | Mostrar botones de acción en la parte inferior de la pantalla |
| 171 | `show_recycle_bin` | Show the recycle bin on the folders screen | Mostrar la papelera de reciclaje en la vista de carpetas |
| 172 | `deep_zoomable_images` | Deep zoomable images | Imágenes ampliables con zoom |
| 173 | `show_highest_quality` | Show images in the highest possible quality | Mostrar imágenes con la mayor calidad posible |
| 174 | `show_recycle_bin_last` | Show the recycle bin as the last item on the main screen | Mostrar la papelera de reciclaje como último elemento en la pantalla principal |
| 175 | `allow_down_gesture` | Allow closing the fullscreen view with a down gesture | Permitir cerrar la vista de pantalla completa con un gesto hacia abajo |
| 176 | `allow_one_to_one_zoom` | Allow 1:1 zooming in with two double taps | Permitir zoom 1:1 con dos toques dobles |
| 177 | `open_videos_on_separate_screen` | Play videos on a separate screen with horizontal gestures | Reproducir vídeos en una pantalla separada con gestos horizontales |
| 178 | `on_video_tap` | On video tap | Al tocar el vídeo |
| 179 | `open_in_app_player` | Open in-app player | Abrir el reproductor de la aplicación |
| 180 | `open_system_default_player` | Open in another app | Abrir en otra aplicación |
| 181 | `show_notch` | Show a notch if available | Mostrar una muesca si está disponible |
| 182 | `allow_rotating_gestures` | Allow rotating images with gestures | Permitir girar con gestos |
| 183 | `file_loading_priority` | File loading priority | Prioridad de carga de archivos |
| 184 | `speed` | Speed | Velocidad |
| 185 | `compromise` | Compromise | Compromiso |
| 186 | `avoid_showing_invalid_files` | Avoid showing invalid files | Evitar mostrar archivos inválidos |
| 187 | `show_image_file_types` | Show image file types | Mostrar tipos de archivo de imagen |
| 188 | `allow_zooming_videos` | Allow zooming videos with double tapping them | Permitir hacer zoom en vídeos con doble toque |
| 189 | `folder_thumbnail_style` | Folder thumbnail style | Estilo de las miniaturas de las carpetas |
| 190 | `file_thumbnail_style` | File thumbnail style | Estilo de las miniaturas de los archivos |
| 191 | `mark_favorite_items` | Mark favorite items | Marcar elementos favoritos |
| 192 | `thumbnail_spacing` | Thumbnail spacing | Espaciado de las miniaturas |
| 193 | `show_file_count_line` | Show file count on a separate line | Mostrar el recuento de archivos en una línea separada |
| 194 | `show_file_count_brackets` | Show file count in brackets | Mostrar el recuento de archivos entre paréntesis |
| 195 | `show_file_count_none` | Do not show file count | No mostrar el recuento de archivos |
| 196 | `limit_folder_title` | Limit long folder titles to 1 line | Limitar los títulos largos de las carpetas a 1 línea |
| 197 | `square` | Square | Cuadrado |
| 198 | `rounded_corners` | Rounded corners | Esquinas redondeadas |
| 199 | `export_favorite_paths` | Export favorites and virtual albums | Exportar favoritos y álbumes virtuales |
| 200 | `import_favorite_paths` | Import favorites and virtual albums | Importar favoritos y álbumes virtuales |
| 201 | `paths_imported_successfully` | Paths imported successfully | Rutas importadas correctamente |
| 202 | `media_management_prompt` | To make sure that all file operations work reliably, please make this app a Media management app in your device settings. | Para asegurarse de que todas las operaciones en ficheros funcionan de forma segura, por favor haga esta aplicación una aplicación de manejo de medios en los ajustes de su dispositivo. |
| 203 | `password_protect_excluded` | Password protect excluded folder visibility | Proteger con contraseña la visibilidad de las carpetas excluidas |
| 204 | `media_management_manual` | Something went wrong, please go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | Algo ha ido mal, por favor vaya a los Ajustes de su dispositivo - Aplicaciones - Acceso especial de aplicaciones - Aplicaciones de gestión de multimedia y permita que esta aplicación gestione los medios. |
| 205 | `media_management_note` | If the redirection does not work, please go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | Si la redirección no funciona, ve a los Ajustes de tu dispositivo - Aplicaciones - Acceso especial de aplicaciones - Aplicaciones de gestión de multimedia y permite que esta aplicación gestione los medios. |
| 206 | `media_management_alternative` | If you do not want to do it, you can also go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | Si no quieres hacerlo, también puedes ir a los Ajustes de tu dispositivo - Aplicaciones - Acceso especial de aplicaciones - Aplicaciones de gestión de multimedia y permitir que esta aplicación gestione los medios. |
| 207 | `alternative_media_access` | Alternatively, you can allow accessing media files only. In that case you will not be able to work with hidden files though. | Alternativamente, puede permitir el acceso a los archivos multimedia solamente. Sin embargo, en ese caso no podrá trabajar con los archivos ocultos. |
| 208 | `media_only` | Media only | Solo archivos multimedia |
| 209 | `all_files` | All files | Todos los archivos |
| 210 | `main_screen_view_on_launch` | Main screen view on launch | Vista de la pantalla principal al iniciar |
| 211 | `show_folders` | Show folders | Mostrar carpetas |
| 212 | `show_files` | Show files | Mostrar archivos |
| 213 | `show_all_folders` | Show a menu button for toggling Show All Folders Content quickly | Mostrar un botón de menú para ver rápidamente el contenido de todas las carpetas |
| 214 | `thumbnails` | Thumbnails | Miniaturas |
| 215 | `fullscreen_media` | Fullscreen media | Medios a pantalla completa |
| 216 | `extended_details` | Extended details | Detalles ampliados |
| 217 | `bottom_actions` | Bottom actions | Acciones en la parte inferior |
| 218 | `manage_bottom_actions` | Manage visible bottom actions | Administrar los botones de la parte inferior |
| 219 | `toggle_favorite` | Toggle favorite | Alternar favorito |
| 220 | `toggle_file_visibility` | Toggle file visibility | Alternar visibilidad del archivo |
| 221 | `failed_to_load_media` | Failed to load media. | Error al cargar el medio. |
| 222 | `file_is_malformed_or_corrupted` | File is malformed or corrupted. | El archivo está malformado o dañado. |
| 223 | `media_exceeds_device_capabilities` | Media exceeds device capabilities. | Los medios superan las capacidades del dispositivo. |
| 224 | `unsupported_format` | Unsupported format. | Formato no soportado. |
| 225 | `video_no_sound` | This video has no audio | Este vídeo no tiene audio |
| 226 | `faq_1_title` | How can I make Calypso Gallery the default device gallery? | ¿Cómo puedo hacer que la Galería Fossify sea la galería predeterminada del dispositivo? |
| 227 | `faq_1_text` | First you have to find the currently default gallery in the Apps section of your device settings, look for a button that says something like \"Open by default\", click on it, then select \"Clear defaults\". The next time you will try opening an image or video you should see an app picker, where you can select Fossify Gallery and make it the default app. | Primero tienes que encontrar la galería actualmente predeterminada en la sección de Aplicaciones de los ajustes de tu dispositivo, busca un botón que diga algo así como \"Abrir por defecto\", haz clic en él y luego selecciona \"Borrar valores predeterminados\". La próxima vez que intentes abrir una imagen o un vídeo deberías ver un selector de aplicaciones, donde puedes seleccionar Galería Fossify y convertirla en la aplicación predeterminada. |
| 228 | `faq_2_title` | I locked the app with a password, but I forgot it. What can I do? | He protegido la aplicación con una contraseña, pero la he olvidado. ¿Qué puedo hacer? |
| 229 | `faq_2_text` | You can solve it in 2 ways. You can either reinstall the app, or find the app in your device settings and select \"Clear data\". It will reset all your settings, it will not remove any media files. | Puede resolverlo de 2 maneras. Puede reinstalar la aplicación o encontrar la aplicación en los ajustes de su dispositivo y seleccionar \"Borrar datos\". Restablecerá todos sus ajustes, no eliminará ningún archivo multimedia. |
| 230 | `faq_3_title` | How can I make an album always appear at the top? | ¿Cómo puedo hacer que un álbum siempre aparezca en la parte superior? |
| 231 | `faq_3_text` | You can long press the desired album and select the Pin icon at the actions menu, that will pin it to the top. You can pin multiple folders too, pinned items will be sorted by the default sorting method. | Puede mantener pulsado el álbum deseado y seleccionar el icono Fijar en el menú de acción, que lo fijará en la parte superior. También puede fijar varias carpetas; los elementos fijados se ordenarán por el método de clasificación predeterminado. |
| 232 | `faq_4_title` | How can I fast forward videos? | ¿Cómo puedo avanzar vídeos? |
| 233 | `faq_4_text` | You can do it by double tapping the side of the screen, or tapping the current or max duration texts near the seekbar. If you enable opening videos on a separate screen in the app settings, you can use horizontal gestures too. | Puede hacerlo tocando dos veces el costado de la pantalla o tocando los textos de duración actual o máxima cerca de la barra de progreso. Si habilita la apertura de vídeos en una pantalla separada en los ajustes de la aplicación, también puede usar gestos horizontales. |
| 234 | `faq_5_title` | What is the difference between hiding and excluding a folder? | ¿Cuál es la diferencia entre ocultar y excluir una carpeta? |
| 235 | `faq_5_text` | Exclude prevents displaying the folder only in Calypso Gallery, while Hide works system-wise and it hides the folder from other galleries too. It works by creating an empty \".nomedia\" file in the given folder, which you can then remove with any file manager too. Note that some devices do not allow hiding folders like Camera, Screenshots and Downloads. | Excluir evita que la carpeta se muestre solo en la Galería Fossify, mientras que Ocultar funciona a nivel de sistema y oculta la carpeta de otras galerías también. Funciona mediante la creación de un archivo vacío \".nomedia\" en la carpeta dada, que luego se puede eliminar con cualquier gestor de archivos también. Ten en cuenta que algunos dispositivos no permiten ocultar carpetas como Cámara, Capturas de pantalla y Descargas. |
| 236 | `faq_6_title` | Why do folders with music cover art or stickers show up? | ¿Por qué aparecen carpetas con la portada de la música o pegatinas? |
| 237 | `faq_6_text` | It can happen that you will see some unusual albums show up. You can easily exclude them by long pressing them and selecting Exclude. In the next dialog you can then select the parent folder, chances are it will prevent the other related albums showing up too. | Puede suceder que vea aparecer algunos álbumes inusuales. Puede excluirlos fácilmente manteniéndolos pulsados y seleccionando Excluir. En el siguiente cuadro de diálogo, puede seleccionar la carpeta principal; lo más probable es que evite que aparezcan otros álbumes relacionados. |
| 238 | `faq_7_title` | A folder with images isn\'t showing up, or it doesn\'t show all items.  What can I do? | Una carpeta con imágenes no aparece, ¿qué puedo hacer? |
| 239 | `faq_7_text` | That can have multiple reasons, but solving it is easy. Just go in Settings -&gt; Manage Included Folders, select Plus and navigate to the required folder. | Eso puede tener múltiples razones, pero resolverlo es fácil. Simplemente vaya a Ajustes -&gt; Gestionar carpetas incluidas, seleccione Más y vaya a la carpeta requerida. |
| 240 | `faq_8_title` | What if I want just a few particular folders visible? | ¿Qué pasa si quiero solo algunas carpetas concretas visibles? |
| 241 | `faq_8_text` | Adding a folder at the Included Folders doesn\'t automatically exclude anything. What you can do is go in Settings -&gt; Manage Excluded Folders, exclude the root folder \"/\", then add the desired folders at Settings -&gt; Manage Included Folders. That will make only the selected folders visible, as both excluding and including are recursive and if a folder is both excluded and included, it will show up. | Agregar una carpeta a Carpetas incluidas no excluye automáticamente nada. Lo que puede hacer es ir a Ajustes -&gt; Gestionar carpetas excluidas, excluir la carpeta raíz \"/\", luego agregar las carpetas deseadas en Ajustes -&gt; Gestionar carpetas incluidas. Esto hará que solo las carpetas seleccionadas sean visibles, ya que tanto la exclusión como la inclusión son recursivas y si una carpeta está excluida e incluida, aparecerá. |
| 242 | `faq_10_title` | Can I crop images with this app? | ¿Puedo recortar imágenes con esta aplicación? |
| 243 | `faq_10_text` | Yes, you can crop images in the editor, by dragging the image corners. You can get to the editor either by long pressing an image thumbnail and selecting Edit, or selecting Edit from the fullscreen view. | Sí, puede recortar imágenes en el editor arrastrando las esquinas de la imagen. Puede acceder al editor pulsando prolongadamente una imagen en miniatura y seleccionando Editar, o seleccionando Editar en la vista de pantalla completa. |
| 244 | `faq_11_title` | Can I somehow group media file thumbnails? | ¿Puedo de alguna manera agrupar miniaturas de archivos multimedia? |
| 245 | `faq_11_text` | Sure, just use the \"Group by\" menu item while at the thumbnails view. You can group files by multiple criteria, including Date Taken. If you use the \"Show all folders content\" function you can group them by folders too. | Claro, solo use el elemento de menú \"Agrupar por\" mientras esté en la vista de miniaturas. Puede agrupar archivos según varios criterios, incluida la Fecha de creación. Si usa la función \"Mostrar el contenido de todas las carpetas\" también puede agruparlas por carpetas. |
| 246 | `faq_12_title` | Sorting by Date Taken doesn\'t seem to work properly, how can I fix it? | La ordenación por fecha tomada no parece funcionar correctamente, ¿cómo puedo solucionarlo? |
| 247 | `faq_12_text` | It is most likely caused by the files being copied from somewhere. You can fix it by selecting the file thumbnails and selecting \"Fix Date Taken value\". | Lo más probable es que sea causado por los archivos que se copian de algún lugar. Puedes arreglarlo seleccionando las miniaturas de archivo y seleccionando \"Arreglar fecha de la toma\". |
| 248 | `faq_13_title` | I see some color banding on the images. How can I improve the quality? | Veo algunas bandas de color en las imágenes. ¿Cómo puedo mejorar la calidad? |
| 249 | `faq_13_text` | The current solution for displaying images works fine in the vast majority of cases, but if you want even better image quality, you can enable the \"Show images in the highest possible quality\" at the app settings, in the \"Deep zoomable images\" section. | La solución actual para mostrar imágenes funciona bien en la gran mayoría de los casos, pero si desea una calidad de imagen aún mejor, puede habilitar \"Mostrar imágenes con la mayor calidad posible\" en los ajustes de la aplicación, en la sección \"Imágenes ampliables con zoom\". |
| 250 | `faq_14_title` | I have hidden a file/folder. How can I unhide it? | He ocultado un archivo/carpeta. ¿Cómo puedo mostrarlo de nuevo? |
| 251 | `faq_14_text` | You can either press the \"Temporarily show hidden items\" menu item at the main screen, or toggle \"Show hidden items\" in the app settings to see the hidden item. If you want to unhide it, just long press it and select \"Unhide\". Folders are hidden by adding a hidden \".nomedia\" file into them, you can delete the file with any file manager too. Note that hiding works recursively though, so if you hide a folder, all subfolders will become hidden too. So for unhiding the subfolders you have to unhide the parent folder. | Puede presionar el elemento de menú \"Mostrar ocultos temporalmente\" en la pantalla principal, o alternar \"Mostrar elementos ocultos\" en los ajustes de la aplicación para ver el elemento oculto. Si desea mostrarlo, simplemente manténgalo presionado y seleccione \"Mostrar\". Las carpetas se ocultan agregando un archivo \".nomedia\" oculto en ellas; también puede eliminar el archivo con cualquier administrador de archivos. Sin embargo, tenga en cuenta que la ocultación funciona de forma recursiva, por lo que si oculta una carpeta, todas las subcarpetas también se ocultarán. Así que, para mostrar las subcarpetas, debe mostrar la carpeta principal. |
| 252 | `faq_15_title` | Why does the app take up so much space? | ¿Por qué la aplicación ocupa tanto espacio? |
| 253 | `faq_15_text` | App cache can take up to 250MB, it ensures quicker image loading. If the app is taking up even more space, it is most likely caused by you having items in the recycle bin. Those files count to the app size. You can clear the recycle bin by opening it and deleting all files, or from the app settings. Every file in the bin is deleted automatically after 30 days. | La caché de la aplicación puede ocupar hasta 250 MB, lo que permite cargar las imágenes más rápido. Si la aplicación está usando aún más espacio, lo más probable es que tengas archivos en la papelera de reciclaje, ya que también cuentan dentro del tamaño de la aplicación. Puedes vaciar la papelera abriéndola y eliminando todos los archivos, o desde los ajustes de la aplicación. Cada archivo de la papelera se elimina automáticamente después de 30 días. |
| 254 | `faq_16_title` | What happened to file and folder hiding and why cannot I see hidden items anymore? | ¿Qué ha pasado con la ocultación de archivos y carpetas y por qué ya no puedo ver los elementos ocultos? |
| 255 | `faq_16_text` | Starting with Android 11 you cannot hide or unhide files or folders anymore, you cannot see the hidden ones in gallery apps due to system restrictions either. You will have to use some file manager for that. | A partir de Android 11 ya no puede ocultar o mostrar archivos o carpetas, tampoco puede ver los ocultos en aplicaciones de galería debido a restricciones del sistema. Tendrá que usar algún gestor de archivos para ello. |
| 256 | `faq_16_text_extra` | Or you can also grant this gallery access to All Files through your device settings, that will allow us showing hidden items and make file operations more reliable in general. | O también puede conceder a esta galería el acceso a todos los archivos a través de los ajustes de su dispositivo, lo que nos permitirá mostrar los elementos ocultos y hacer más fiables las operaciones con los archivos en general. |
| 257 | `faq_17_title` | Why cannot I include missing folders anymore? | ¿Por qué ya no puedo incluir las carpetas que faltan? |
| 258 | `faq_17_text` | That stopped working due to the system changes that came with Android 11 too, the app cannot browse real folders anymore, it relies on the so called MediaStore at fetching data. | Eso dejó de funcionar debido a los cambios en el sistema que vinieron con Android 11 también; la aplicación ya no puede explorar las carpetas reales, sino que se basa en el llamado MediaStore para obtener los datos. |
| 259 | `faq_18_title` | Why do I see ads during video playback? | ¿Por qué veo anuncios durante la reproducción de vídeo? |
| 260 | `faq_18_text` | Our apps have no ads whatsoever. If you see them during video playback, you must be using some other apps video player. Try finding your default video player in the device settings, then do a \"Clear defaults\" on it. The next time you invoke some video intent you will see an app picker prompt, where you can select what app you want to use. | Nuestras aplicaciones no tienen anuncios de ninguna clase. Si los ve durante la reproducción de vídeo, entonces debe estar usando el reproductor de vídeo de otra aplicación. Pruebe encontrando su reproductor de vídeo por defecto en los ajustes del dispositivo, luego realice un \"Borrar predeterminados\" en él. La siguiente vez que vaya a ver un vídeo verá un selector de aplicaciones, en el cual puede seleccionar qué aplicación quiere usar para reproducirlo. |
| 261 | `create_virtual_album` | Create virtual album | Crear álbum virtual |
| 262 | `new_virtual_album` | New album… | Nuevo álbum… |
| 263 | `rename_virtual_album` | Rename album | Renombrar álbum |
| 264 | `album_name` | Album name | Nombre del álbum |
| 265 | `virtual_folder_name` | Virtual album name | Nombre del álbum virtual |
| 266 | `virtual_album_name_empty` | The album name cannot be empty | El nombre del álbum no puede estar vacío |
| 267 | `virtual_album_exists` | An album with this name already exists | Ya existe un álbum con este nombre |
| 268 | `add_to_virtual_album` | Add to album | Añadir al álbum |
| 269 | `remove_from_virtual_album` | Remove from album | Quitar del álbum |
| 270 | `added_to_virtual_album` | Added to \"%s\" | Añadido a \"%s\" |
| 271 | `already_in_virtual_album` | Already in \"%s\" | Ya está en \"%s\" |
| 272 | `removed_from_virtual_album` | Removed from the album | Quitado del álbum |
| 273 | `delete_virtual_album_confirmation` | Delete the album %s? Only the album is removed, your photos and videos stay where they are. | ¿Eliminar el álbum %s? Solo se eliminará el álbum; tus fotos y vídeos permanecerán donde están. |
| 274 | `virtual_album_empty` | This album is empty. Select items in any folder and use \"Copy to\". | Este álbum está vacío. Selecciona elementos en cualquier carpeta y usa \"Copiar a\". |
| 275 | `virtual_album_cannot_be_modified` | This action is not available for virtual albums | Esta acción no está disponible para álbumes virtuales |
| 276 | `alphanumeric_password` | Alphanumeric | Alfanumérico |
| 277 | `enter_password` | Enter password | Introduzca la contraseña |
| 278 | `repeat_password` | Repeat password | Repetir contraseña |
| 279 | `wrong_password` | Wrong password | Contraseña incorrecta |
| 280 | `encrypting_files` | Encrypting files… | Cifrando archivos… |
| 281 | `encrypting_folder` | Encrypting folder… | Cifrando carpeta… |
| 282 | `folder_encrypted_successfully` | Folder encrypted successfully | Carpeta cifrada correctamente |
| 283 | `file_encrypted_successfully` | File encrypted successfully | Archivo cifrado correctamente  |
| 284 | `files_encrypted_successfully` | Files encrypted successfully | Archivos cifrados correctamente  |
| 285 | `decrypting_folder` | Decrypting folder… | Descifrando carpeta… |
| 286 | `folder_decrypted_successfully` | Folder decrypted successfully | Carpeta descifrada correctamente |
| 287 | `file_decrypted_successfully` | File decrypted successfully | Archivo descifrado correctamente |
| 288 | `files_decrypted_successfully` | Files decrypted successfully | Archivos descifrados correctamente |
| 289 | `decrypting_files` | Decrypting files… | Descifrando archivos… |
| 290 | `place_finger_prompt` | Please place your finger on the fingerprint sensor | Por favor, ponga el dedo en el sensor de huellas |
| 291 | `connect_at_least_4_dots_prompt` | Connect at least 4 dots | Conecta al menos 4 puntos |
| 292 | `access_existing_secrets_prompt` | Do you want to access your existing Secrets folder? | ¿Desea acceder a su carpeta Secrets existente? |
| 293 | `delete_and_recreate_secrets_prompt` | Existing Secrets folder will be deleted and a new one will be created. Are you sure? | Se eliminará la carpeta Secrets existente y se creará una nueva. ¿Está seguro? |
| 294 | `confirm_checkbox_label` | I confirm | Confirmo |
| 295 | `secret_vault` | Encrypted Vault | Bóveda cifrada |
| 296 | `secret_vault_empty` | Encrypted Vault is empty | La bóveda cifrada está vacía |
| - | `press_back_again_to_exit_vault` | Press back again to exit | Pulsa atrás de nuevo para salir |
| - | `block_vault_screenshots` | Block screenshots in encrypted vault | Bloquear capturas de pantalla en la bóveda cifrada |
| 297 | `confirm_decrypt_hidden_file` | Are you sure you want to decrypt the hidden file(s)? | ¿Está seguro de que desea descifrar los archivos ocultos? |
| 298 | `copy_to_clipboard_disabled_in_vault` | Copying to clipboard is disabled while vault is unlocked for security. | Por seguridad, copiar al portapapeles está deshabilitado mientras la bóveda está desbloqueada. |
| 299 | `stop_showing_hidden` | Stop showing encrypted media | Dejar de mostrar medios cifrados |
| 300 | `system_default` | System default | Sistema por defecto |
| 301 | `language` | Language | Idioma |
| 302 | `open_system_language_settings` | Open system language settings | Abrir ajustes de idioma del sistema |
| 303 | `access_hidden_items_with_biometrics` | Access hidden items with biometrics | Acceder a elementos ocultos con biometría |
| 304 | `enable_hidden_protection_first_prompt` | Please enable hidden item password protection first | Por favor, active primero la protección con contraseña para elementos ocultos |
| 305 | `biometric_unlock_vault` | Unlock encrypted vault | Desbloquear bóveda cifrada |
| 306 | `biometric_unlock_prompt` | Verify your biometric identity to access hidden items | Verifique su identidad biométrica para acceder a los elementos ocultos |
| 307 | `biometric_setup_prompt` | Confirm your biometric identity to enable biometric vault access | Confirme su identidad biométrica para activar el acceso biométrico a la bóveda |
| 308 | `use_password_or_pin` | Use PIN / Password | Usar PIN / Contraseña |
| 309 | `biometric_key_invalidated` | Biometric credentials have changed. Please enter your PIN or password. | Las credenciales biométricas han cambiado. Por favor, introduzca su PIN o contraseña. |
| 310 | `no_biometric_hardware_or_enrolled` | No biometric hardware or enrolled credentials found on this device | No se ha encontrado hardware biométrico ni credenciales registradas en este dispositivo |
| 311 | `default_hidden_album` | Default Secret Album | *(non-translatable in `values/strings.xml`)* |
| 312 | `select_hidden_album` | Select Encrypted Album | Seleccionar álbum cifrado |
| 313 | `create_new_hidden_album` | Create New Encrypted Album | Crear nuevo álbum cifrado |
| 314 | `new_hidden_album` | New encrypted album | Nuevo álbum cifrado |
| 315 | `hidden_album_name_empty` | Album name cannot be empty | El nombre del álbum no puede estar vacío |
| 316 | `file_hidden_to_album` | File encrypted to \"%s\" | Archivo cifrado en \"%s\"  |
| 317 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d archivos cifrados en \"%2$s\"  |
| 318 | `restore_confirmation` (`one`) | Are you sure you want to restore %d file? | ¿Confirma que quiere restaurar %d archivo? |
| 319 | `restore_confirmation` (`many`) | *(CLDR `many` — falls back to English `other`)* | ¿Confirma que quiere restaurar %d archivos? |
| 320 | `restore_confirmation` (`other`) | Are you sure you want to restore %d files? | ¿Confirma que quiere restaurar %d archivos? |
| 321 | `failed_to_resize_images` (`one`) | Failed to resize %d image | Error al cambiar el tamaño de %d imagen |
| 322 | `failed_to_resize_images` (`many`) | *(CLDR `many` — falls back to English `other`)* | Error al cambiar el tamaño de %d imágenes |
| 323 | `failed_to_resize_images` (`other`) | Failed to resize %d images | Error al cambiar el tamaño de %d imágenes |
| 324 | `search` *(fossify-commons)* | *(inherited from commons)* | Buscar |
| 325 | `change_date_and_time_format` *(fossify-commons)* | Change date and time format | Cambiar formato de fecha y hora |
| 326 | `password_protect_file_deletion` *(fossify-commons)* | Password protect file deletion and moving | Proteger con contraseña eliminar y mover archivos |
| 327 | `keep_last_modified` *(fossify-commons)* | Keep old last-modified value at file operations | Mantener el último valor modificado por última vez en las operaciones de archivo |
| 328 | `clear_cache` *(fossify-commons)* | Clear cache | Limpiar cache |
| 329 | `export_settings` *(fossify-commons)* | Export settings | Exportar ajustes |
| 330 | `import_settings` *(fossify-commons)* | Import settings | Importar ajustes |
| 331 | `temporarily_show_excluded` *(fossify-commons)* | Temporarily show excluded | Mostrar excluidos temporalmente |
| 332 | `change_view_type` *(fossify-commons)* | Change view type | Cambiar tipo de vista |
| 333 | `contributors` *(fossify-commons)* | Contributors | Contribuyentes |
| 334 | `known_issues` *(fossify-commons)* | Known issues | Problemas conocidos |
| 335 | `donate_to_fossify` *(fossify-commons)* | Donate to Fossify | Apoya a Fossify |
| 336 | `privacy_policy` *(fossify-commons)* | Privacy policy | Política de privacidad |
| 337 | `create_shortcut` *(fossify-commons)* | Create shortcut | Crear atajo |
| 338 | `print` *(fossify-commons)* | Print | Imprimir |
| 339 | `search_files` *(fossify-commons)* | Search files | Buscar archivos |
| 340 | `search_folders` *(fossify-commons)* | Search folders | Buscar carpetas |
| 341 | `pull_down_to_open_vault` | Keep pulling down for Encrypted Vault | Siga deslizando hacia abajo para la bóveda |
| 342 | `keep_holding_to_open_vault` | Keep holding to open Encrypted Vault | Siga manteniendo pulsado para abrir la bóveda cifrada |
| 343 | `release_to_open_vault` | Release to open Encrypted Vault | Suelte para abrir la bóveda cifrada |
| 344 | `number_of_items` *(fossify-commons)* | Number of items | Número de elementos |
| 345 | `random` *(fossify-commons)* | Random | Aleatorio |
| 346 | `shuffle` | Shuffle | Mezclar |
| 347 | `stop_showing_excluded` *(fossify-commons)* | Stop showing excluded | Dejar de mostrar los excluidos |
| 348 | `copy_to_clipboard` *(fossify-commons)* | Copy to clipboard | Copiar al portapapeles |
| 349 | `encrypt` | Encrypt | Cifrar |
| 350 | `decrypt` | Decrypt | Descifrar |
| 351 | `encrypt_album` | Encrypt album | Cifrar el álbum |
| 352 | `decrypt_album` | Decrypt album | Descifrar el álbum |
| 353 | `create_new_encrypted_virtual_album` | Create new encrypted virtual album | Crear nuevo álbum virtual cifrado |
| 354 | `new_album` | New album | Nuevo álbum |
| 355 | `filename_without_zip` | Filename (without .zip) | Nombre de archivo (sin .zip) |
| 356 | `favorites_already_exist` | You already have a favorites list | Ya tiene una lista de favoritos |
| 357 | `delete_existing_list` | Delete existing list | Eliminar la lista existente |
| 358 | `merge_lists` | Merge lists | Combinar listas |
| 359 | `confirm` | Confirm | Confirmar |
| 360 | `confirm_encrypt_album` | Are you sure you want to encrypt this album? All of its media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | ¿Está seguro de que desea cifrar este álbum? Todos sus archivos multimedia se cifrarán y se moverán a la bóveda cifrada. Los archivos originales se eliminarán. |
| 361 | `confirm_encrypt_albums` | Are you sure you want to encrypt the selected albums? All of their media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | ¿Está seguro de que desea cifrar los álbumes seleccionados? Todos sus archivos multimedia se cifrarán y se moverán a la bóveda cifrada. Los archivos originales se eliminarán. |
| 362 | `confirm_encrypt_file` | Are you sure you want to encrypt this file? It will be encrypted and moved to the encrypted vault. The original file will be deleted. | ¿Está seguro de que desea cifrar este archivo? Se cifrará y se moverá a la bóveda cifrada. El archivo original se eliminará. |
| 363 | `confirm_encrypt_files` | Are you sure you want to encrypt the selected files? They will be encrypted and moved to the encrypted vault. The original files will be deleted. | ¿Está seguro de que desea cifrar los archivos seleccionados? Se cifrarán y se moverán a la bóveda cifrada. Los archivos originales se eliminarán. |
| 364 | `confirm_encrypt_files_to_vault` | The selected files will be encrypted when they are copied into the encrypted vault. Do you want to continue? | Los archivos seleccionados se cifrarán al copiarse en la bóveda cifrada. ¿Desea continuar? |
| 365 | `encrypt_decrypt_notifications` | Encrypt / Decrypt | Cifrar / Descifrar |
| 366 | `allow_batch_renaming` | Allow batch renaming when multiple files are selected | Permitir renombrado por lotes cuando se seleccionan varios archivos |
| 367 | `keep_original_remove_from_virtual_album` | Keep the original file, remove it only from this virtual album | Conservar el archivo original, quitarlo solo de este álbum virtual |
| 368 | `keep_original_remove_from_favorites` | Keep the original file, remove it only from favorites | Conservar el archivo original, quitarlo solo de favoritos |
| 369 | `delete_original_from_favorites_and_virtual_albums` | Delete the original version of a file when it is deleted from favorites and virtual albums | Eliminar la versión original de un archivo cuando se elimine de favoritos y álbumes virtuales |
| 370 | `cannot_target_encrypted_album` | Encrypted albums cannot be targeted for this operation | No se pueden seleccionar álbumes cifrados como destino para esta operación |
| 371 | `cannot_mix_encrypted_and_unencrypted` | Cannot combine encrypted and unencrypted items in this operation | No se pueden combinar elementos cifrados y sin cifrar en esta operación |
| 372 | `lock_album` | Lock album | Bloquear álbum |
| 373 | `unlock_album` | Unlock album | Desbloquear álbum |
| 374 | `auto_backup_prompt_message` | Do you want your favorites and virtual albums to be regularly written to system storage outside of app memory? This will ensure that you do not lose your favorites list and virtual albums if you uninstall the app. You can manually create a backup from app settings at any time. | ¿Desea que sus favoritos y álbumes virtuales se guarden periódicamente en el almacenamiento del sistema fuera de la memoria de la aplicación? Esto garantizará que no pierda su lista de favoritos ni sus álbumes virtuales si desinstala la aplicación. Puede crear una copia de seguridad manualmente desde los ajustes de la aplicación en cualquier momento. |
| 375 | `auto_backup_favorites_and_virtual_albums` | Back up favorites and virtual albums to device storage | Hacer copia de seguridad de favoritos y álbumes virtuales en el almacenamiento del dispositivo |
| 376 | `encrypt_info_title` | How Encryption Works | Cómo funciona el cifrado |
| 377 | `encrypt_info_message` | Folder & File Structure:\n• When an unencrypted album is encrypted, it is converted into an encrypted subfolder inside the vault (Secrets).\n• Both the folder name and all file names/contents inside it are encrypted, so nothing is readable outside the app.\n\nIf an Album with the Same Name Already Exists:\n• By default, the app asks whether to create a new album such as Album(1) or merge into the existing encrypted album (configurable in Settings).\n• When merging, you can choose whether source files verified 100% bit-identical to the destination are deleted or kept in the source folder.\n• Different files with the same name (e.g. download.jpg) are kept as download(1).jpg or handled according to your conflict resolution setting. | Estructura de carpetas y archivos:\n• Cuando se cifra un álbum sin cifrar, se convierte en una subcarpeta cifrada dentro de la bóveda (Secrets).\n• Tanto el nombre de la carpeta como todos los nombres y contenidos de los archivos en su interior se cifran, por lo que nada es legible fuera de la aplicación.\n\nSi ya existe un álbum con el mismo nombre:\n• De forma predeterminada, la aplicación pregunta si crear un álbum nuevo como Álbum(1) o fusionarlo con el álbum cifrado existente (configurable en Ajustes).\n• Al fusionar, puede elegir si los archivos de origen verificados como 100% idénticos bit a bit al destino se eliminan o se conservan en la carpeta de origen.\n• Los archivos distintos con el mismo nombre (p. ej., download.jpg) se conservan como download(1).jpg o se gestionan según su ajuste de resolución de conflictos. |
| 378 | `decrypt_info_title` | How Decryption Works | Cómo funciona el descifrado |
| 379 | `decrypt_info_message` | Folder & File Structure:\n• When an encrypted album or file is decrypted, it is restored with its original name and format to the selected destination folder and removed from the encrypted vault.\n\nIf an Album with the Same Name Already Exists:\n• By default, the app asks whether to create a new folder such as Album(1) or merge into the existing destination folder (configurable in Settings).\n• When merging, you can choose whether encrypted copies verified 100% bit-identical to the destination are removed from the vault or kept.\n• Different files with the same name (e.g. download.jpg) are kept as download(1).jpg or handled according to your conflict resolution setting. | Estructura de carpetas y archivos:\n• Cuando se descifra un álbum o archivo cifrado, se restaura con su nombre y formato originales en la carpeta de destino seleccionada y se elimina de la bóveda cifrada.\n\nSi ya existe un álbum con el mismo nombre:\n• De forma predeterminada, la aplicación pregunta si crear una carpeta nueva como Álbum(1) o fusionarla con la carpeta de destino existente (configurable en Ajustes).\n• Al fusionar, puede elegir si las copias cifradas verificadas como 100% idénticas bit a bit al destino se eliminan de la bóveda o se conservan.\n• Los archivos distintos con el mismo nombre (p. ej., download.jpg) se conservan como download(1).jpg o se gestionan según su ajuste de resolución de conflictos. |
| 380 | `vault_location` | Vault location | Ubicación de la bóveda |
| 381 | `vault_location_standard_summary` | Standard (Internal Storage) | Estándar (Almacenamiento interno) |
| 382 | `vault_location_unavailable_summary` | External drive disconnected (%s) | Unidad externa desconectada (%s) |
| 383 | `select_vault_location` | Select vault location | Seleccionar ubicación de la bóveda |
| 384 | `vault_setup_action_title` | Encrypted vault | Bóveda cifrada |
| 385 | `create_new_vault_option` | Create new vault | Crear bóveda nueva |
| 386 | `use_existing_vault_option` | Use existing vault | Usar bóveda existente |
| 387 | `where_to_create_vault_title` | Where should the vault be created? | ¿Dónde debe crearse la bóveda? |
| 388 | `vault_location_default_short` | Default location | Ubicación predeterminada |
| 389 | `vault_location_custom_short` | Custom Location | Ubicación personalizada |
| 390 | `no_existing_vault_at_location` | No existing encrypted vault (.vault) was found in the selected folder. | No se encontró ninguna bóveda cifrada (.vault) en la carpeta seleccionada. |
| 391 | `unlock_existing_vault_at_location_prompt` | An existing encrypted vault was found at %s. Do you want to unlock and use it? Tap No to pick a different folder. | Se encontró una bóveda cifrada existente en %s. ¿Desea desbloquearla y usarla? Pulse No para elegir otra carpeta. |
| 392 | `vault_location_standard_option` | Standard location (Internal Storage > Calypso Gallery > Secrets) | Ubicación estándar (Almacenamiento interno > Calypso Gallery > Secrets) |
| 393 | `vault_location_custom_option` | Custom / External location (USB drive, SD card, or custom folder) | Ubicación personalizada / externa (unidad USB, tarjeta SD o carpeta personalizada) |
| 394 | `external_vault_unavailable_title` | External vault unavailable | Bóveda externa no disponible |
| 395 | `external_vault_unavailable_prompt` | Your configured vault location (%s) is currently disconnected or unavailable. Plug in the drive and tap Retry, or choose another vault location. | Su ubicación de bóveda configurada (%s) está desconectada o no disponible en este momento. Conecte la unidad y pulse Reintentar, o elija otra ubicación para la bóveda. |
| 396 | `retry` | Retry | Reintentar |
| 397 | `change_vault_location` | Change location | Cambiar ubicación |
| 398 | `existing_vault_detected_at_location` | Existing vault found at %s. Enter its password or PIN to unlock it. | Se encontró una bóveda existente en %s. Introduzca su contraseña o PIN para desbloquearla. |
| 399 | `create_new_vault_at_location_prompt` | No vault exists at %s. Do you want to create a new encrypted vault at this location? | No existe ninguna bóveda en %s. ¿Desea crear una nueva bóveda cifrada en esta ubicación? |
| 400 | `usb_direct_filesystem_required` | This USB path is only exposed through Android\'s virtual OTG provider and cannot be accessed directly as a filesystem path. Please choose a mounted storage directory or a folder on internal storage / SD card. | Esta ruta USB solo se expone a través del proveedor OTG virtual de Android y no es accesible directamente como ruta del sistema de archivos. Por favor, elija un directorio de almacenamiento montado o una carpeta en el almacenamiento interno o tarjeta SD. |
| 401 | `vault_location_not_writable` | The selected folder does not exist or is not writable on this device. | La carpeta seleccionada no existe o no se puede escribir en ella en este dispositivo. |
| 402 | `vault_location_not_readable` | The selected folder does not exist or is not readable on this device. | La carpeta seleccionada no existe o no se puede leer en este dispositivo. |
| 403 | `sync_vault_from_external` | Import newly encrypted media filed to encrypted section | Importar archivos multimedia cifrados a la sección cifrada |
| 404 | `sync_vault_select_source_prompt` | How Encrypted Media Import Works:\n\n1. Unified Secrets Folder:\nKeep a folder named Secrets on your external drive (USB / SD). You can either keep a full backup copy of your vault there, or place only your vault\'s .vault header file inside it.\n\n2. Encrypting New Media (PC / Tails CLI):\nThe offline CLI tool automatically saves new encrypted files directly into Secrets. Files placed at the root go to Default Secret Album; files in subfolders go to matching encrypted albums (or create new ones).\n\n3. Bit-by-Bit Smart Sync:\nSelect your external drive or Secrets folder next. The app compares the external drive with your phone vault in RAM:\n• Only new files and albums are imported.\n• Bit-identical files already in your vault are skipped.\n• Different files with the same name (e.g. download.jpg) are safely imported as download(1).jpg. | Cómo funciona la importación de archivos multimedia cifrados:\n\n1. Carpeta unificada Secrets:\nMantenga una carpeta llamada Secrets en su unidad externa (USB / SD). Puede guardar allí una copia de seguridad completa de su bóveda o colocar únicamente el archivo de cabecera .vault de su bóveda.\n\n2. Cifrado de nuevos archivos multimedia (PC / CLI de Tails):\nLa herramienta CLI sin conexión guarda automáticamente los nuevos archivos cifrados directamente en Secrets. Los archivos en la raíz van al Álbum secreto predeterminado; los archivos en subcarpetas van a los álbumes cifrados correspondientes (o crean otros nuevos).\n\n3. Sincronización inteligente bit a bit:\nSeleccione a continuación su unidad externa o carpeta Secrets. La aplicación compara la unidad externa con la bóveda de su teléfono en la memoria RAM:\n• Solo se importan los archivos y álbumes nuevos.\n• Los archivos idénticos bit a bit que ya están en su bóveda se omiten.\n• Los archivos distintos con el mismo nombre (p. ej., download.jpg) se importan de forma segura como download(1).jpg. |
| 405 | `conflict_handling_mode_label` | File conflict & duplicate resolution | Resolución de conflictos y duplicados de archivos |
| 406 | `conflict_handling_auto_short` | Automatic (Experimental — Auto-skip identical, rename different as (1)) | Totalmente automático (Predeterminado: omitir idénticos, renombrar distintos como (1)) |
| 407 | `conflict_handling_smart_dialog_short` | Smart dialog (Auto-skip identical, ask when content differs) | Diálogo inteligente (Omitir idénticos automáticamente, preguntar si el contenido difiere) |
| 408 | `album_crypto_conflict_mode_label` | Album conflict (Encrypt / Decrypt) | Conflicto de álbum (Cifrar / Descifrar) |
| 409 | `album_crypto_conflict_ask` | Always ask (Default) | Preguntar siempre (Predeterminado) |
| 410 | `album_crypto_conflict_merge` | Merge into existing album | Fusionar con el álbum existente |
| 411 | `album_crypto_conflict_merge_delete_identical` | Merge into existing album and automatically delete identical sources | Fusionar con el álbum existente y eliminar orígenes idénticos automáticamente |
| 412 | `album_crypto_conflict_rename` | Create new album as (1) | Crear álbum nuevo como (1) |
| 413 | `album_crypto_conflict_title` | Album Already Exists | El álbum ya existe |
| 414 | `album_crypto_conflict_message` | An album named \"%s\" already exists at the destination. How would you like to proceed? | Ya existe un álbum llamado \"%s\" en el destino. ¿Cómo desea proceder? |
| 415 | `album_crypto_conflict_message_multiple` | %1$d selected albums already exist at the destination. How would you like to proceed?\n\nYour choice will apply to all conflicting albums. To choose separately per album, select and process them one by one. | %1$d álbumes seleccionados ya existen en el destino. ¿Cómo desea proceder?\n\nSu elección se aplicará a todos los álbumes en conflicto. Para elegir por separado para cada álbum, selecciónelos y procéselos uno por uno. |
| 416 | `album_crypto_conflict_rename_option` | Create new album as \"%s\" | Crear álbum nuevo como \"%s\" |
| 417 | `album_crypto_conflict_rename_option_multiple` | Create new albums as (1), (2), … | Crear álbumes nuevos como (1), (2), … |
| 418 | `album_crypto_conflict_merge_option` | Merge into existing \"%s\" | Fusionar en el \"%s\" existente |
| 419 | `album_crypto_conflict_merge_option_multiple` | Merge into existing albums | Fusionar en los álbumes existentes |
| 420 | `album_crypto_delete_identical_source` | Delete identical source files (100% bit-by-bit verified) | Eliminar archivos de origen idénticos (100% verificados bit a bit) |
| 421 | `album_crypto_dont_ask_again` | Remember album conflict choice (merge or rename) | Recordar elección de conflicto de álbum (fusionar o renombrar) |
| 422 | `album_crypto_merge_identical_title` | Identical Files During Merge | Archivos idénticos durante la fusión |
| 423 | `album_crypto_merge_identical_message` | When merging into \"%1$s\", if any files are already 100%% bit-identical at the destination, should their source copies be deleted or kept? | Al fusionar en \"%1$s\", si algún archivo ya es 100%% idéntico bit a bit en el destino, ¿deben eliminarse o conservarse sus copias de origen? |
| 424 | `album_crypto_merge_identical_message_multiple` | When merging the %1$d conflicting albums, if any files are already 100%% bit-identical at the destination, should their source copies be deleted or kept? | Al fusionar los %1$d álbumes en conflicto, si algún archivo ya es 100%% idéntico bit a bit en el destino, ¿deben eliminarse o conservarse sus copias de origen? |
| 425 | `delete_identical_sources` | Delete identical | Eliminar idénticos |
| 426 | `keep_identical_sources` | Keep in source | Conservar en origen |
| 427 | `fast_large_duplicate_check_label` | Fast duplicate check for large files (>8 MB) | Comprobación rápida de duplicados para archivos grandes (>8 MB) |
| 428 | `fast_large_duplicate_check_hint` | Crucial for transfers containing large files: checks 5 blocks (1.25 MB total) per file instead of reading whole files, speeding up duplicate checks dozens of times. | Crucial para transferencias con archivos grandes: comprueba 5 bloques (1,25 MB en total) por archivo en lugar de leer archivos completos, acelerando decenas de veces la comprobación de duplicados. |
| 429 | `fast_large_duplicate_check_info_title` | Fast Duplicate Check (>8 MB) | Comprobación rápida de duplicados (>8 MB) |
| 430 | `conflict_handling_explanation_title` | File Conflict & Duplicate Resolution | Resolución de conflictos y duplicados de archivos |
| 431 | `conflict_handling_explanation_message` | When copying, moving, encrypting, or decrypting files and albums (including album merges), if a file with the same name already exists at the destination:\n\n• Standard dialog (Default): Checks filename only (like the original Fossify Gallery app) without comparing file contents, and asks you to Skip / Overwrite / Keep both.\n• Smart dialog: Checks file sizes and bit-by-bit contents in RAM (encrypted files stay in memory), auto-skips identical duplicates, and asks with side-by-side previews (Skip / Overwrite / Keep both) only when content differs.\n• Automatic (Experimental): Auto-skips identical duplicates and automatically keeps both different files by naming the incoming file download(1).jpg, download(2).jpg. | Al copiar, mover, cifrar o descifrar archivos y álbumes, si ya existe un archivo con el mismo nombre en el destino:\n\n1. Comprobación de duplicados bit a bit (RAM):\nLa aplicación comprueba primero los tamaños de archivo y, si coinciden, compara el contenido bit a bit en la RAM (los archivos cifrados se comprueban en memoria sin escribir texto sin cifrar en el disco). Al copiar o mover, los archivos idénticos se omiten y el original permanece intacto en la carpeta de origen. Al fusionar un álbum durante el cifrado o descifrado, puede elegir si los archivos de origen 100% idénticos bit a bit se eliminan o se conservan.\n\n2. Mismo nombre, distinto contenido (p. ej., dos archivos download.jpg diferentes):\n• Totalmente automático (Predeterminado): Conserva ambos archivos sin interrumpirle nombrando al nuevo download(1).jpg, download(2).jpg.\n• Diálogo inteligente: Sigue omitiendo en silencio los archivos idénticos bit a bit y solo pregunta (Omitir / Sobrescribir / Conservar ambos) cuando dos archivos tienen el mismo nombre pero distinto contenido. |
| 432 | `syncing_vault_progress` | Syncing vault from external drive… | Sincronizando bóveda desde unidad externa… |
| 433 | `vault_sync_summary_message` | Vault sync complete:\n• Copied: %1$d\n• Renamed (name conflict): %2$d\n• Skipped (identical): %3$d\n• Failed: %4$d | Sincronización de bóveda completada:\n• Copiados: %1$d\n• Renombrados (conflicto de nombre): %2$d\n• Omitidos (idénticos): %3$d\n• Fallidos: %4$d |
| 434 | `vault_sync_nothing_to_sync` | No pending CLI-encrypted media (.pending_sync) was found in the selected folder. | No se encontraron archivos multimedia cifrados por CLI pendientes (.pending_sync) en la carpeta seleccionada. |
| 435 | `vault_sync_same_folder_error` | The selected source folder is the same as your active vault. | La carpeta de origen seleccionada es la misma que su bóveda activa. |
| 436 | `use_for_this_album` | Use for this album only | Solo para este álbum |
| 437 | `crypto_duplicate_source_mode_label` | When a file already exists in destination during encrypt/decrypt | Cuando un archivo ya existe en el destino al cifrar o descifrar |
| 438 | `crypto_duplicate_source_ask` | Always ask (Default) | Preguntar siempre (Predeterminado) |
| 439 | `crypto_duplicate_source_keep` | Keep file in source folder | Conservar el archivo en la carpeta de origen |
| 440 | `crypto_duplicate_source_delete` | Delete file from source folder | Eliminar el archivo de la carpeta de origen |
| 441 | `crypto_duplicate_source_title` | File Already Exists in Destination | El archivo ya existe en el destino |
| 442 | `crypto_duplicate_source_encrypt_message` | Some of the files you are encrypting already exist in the destination folder. What should happen to the files in the source folder? | Algunos de los archivos que estás cifrando ya existen en la carpeta de destino. ¿Qué deseas hacer con los archivos de la carpeta de origen? |
| 443 | `crypto_duplicate_source_decrypt_message` | Some of the files you are decrypting already exist in the destination folder. What should happen to the files in the source folder? | Algunos de los archivos que estás descifrando ya existen en la carpeta de destino. ¿Qué deseas hacer con los archivos de la carpeta de origen? |
| 444 | `crypto_duplicate_source_encrypt_keep_warning` | Warning: Choosing \"Keep\" for unencrypted files will leave the source file unencrypted on disk. | Advertencia: Si eliges \"Conservar\" para archivos sin cifrar, el archivo de origen permanecerá sin cifrar en el almacenamiento. |
| 445 | `crypto_duplicate_source_always_ask` | Always ask | Preguntar siempre |
| 446 | `export_encrypted_vault` | Export encrypted vault | Exportar bóveda cifrada |
| 447 | `export_vault_only_header` | Export folder containing only .vault file | Exportar carpeta que contiene solo el archivo .vault |
| 448 | `export_vault_entire_folder` | Export entire folder | Exportar toda la carpeta |
| 449 | `export_vault_only_header_info` | To add media items encrypted on other devices using the supported CLI tool to the encrypted vault on your phone, you only need to use a folder named Secrets containing the .vault file. | Para agregar a la bóveda cifrada de tu teléfono elementos multimedia cifrados en otros dispositivos con la herramienta CLI compatible, solo necesitas usar una carpeta llamada Secrets que contenga el archivo .vault. |
| 450 | `exporting_vault_progress` | Exporting encrypted vault… | Exportando bóveda cifrada… |
| 451 | `vault_export_same_folder_error` | The export destination cannot be the active vault folder or inside it. | El destino de exportación no puede ser la carpeta de la bóveda activa ni estar dentro de ella. |
| 452 | `conflict_handling_standard_dialog_summary` | Standard dialog | Diálogo estándar |
| 453 | `conflict_handling_smart_dialog_summary` | Smart dialog | Diálogo inteligente |
| 454 | `conflict_handling_auto_summary` | Automatic (Experimental) | Automático (experimental) |
| 455 | `conflict_handling_standard_dialog_short` | Standard dialog (Default — Checks filename only, like original Fossify Gallery) | Diálogo estándar (Predeterminado — comprueba solo el nombre del archivo, como el Fossify Gallery original) |
| 456 | `skipped_encrypt_files_mode_label` | Manage skipped files during encryption | Gestionar los archivos omitidos durante el cifrado |
| 457 | `skipped_encrypt_files_info_message` | Choose whether files skipped in the source folder during encryption should be kept or deleted for security. This choice also applies to album conflicts during album encryption (see setting below) when the source album is merged into an existing album at the destination. | Elige si los archivos omitidos en la carpeta de origen durante el cifrado deben conservarse o eliminarse por seguridad. Esta elección también se aplica a los conflictos de álbum durante el cifrado de álbumes (véase el ajuste siguiente) cuando el álbum de origen se fusiona con un álbum existente en el destino. |
| 458 | `skipped_crypto_files_ask` | Always ask (Default) | Preguntar siempre (Predeterminado) |
| 459 | `skipped_crypto_files_keep` | Keep | Conservar |
| 460 | `skipped_crypto_files_delete` | Delete | Eliminar |
| 461 | `skipped_crypto_files_encrypt_message` | The files you skipped were not transferred. For your privacy, you can delete these files. | Los archivos que omitiste no se transfirieron. Por tu privacidad, puedes eliminar estos archivos. |
| 462 | `skipped_crypto_files_decrypt_message` | The files you skipped were not transferred. You can delete these files. | Los archivos que omitiste no se transfirieron. Puedes eliminar estos archivos. |
| 463 | `skipped_crypto_files_identical_encrypt_message` | Files that already exist in the destination folder were not transferred. For your privacy, you can delete these files. | Los archivos que ya existen en la carpeta de destino no se transfirieron. Por tu privacidad, puedes eliminar estos archivos. |
| 464 | `skipped_crypto_files_identical_decrypt_message` | Files that already exist in the destination folder were not transferred. You can delete these files. | Los archivos que ya existen en la carpeta de destino no se transfirieron. Puedes eliminar estos archivos. |
| 465 | `skipped_crypto_files_both_encrypt_message` | The files you skipped and the files that already exist in the destination folder were not transferred. For your privacy, you can delete these files. | Los archivos que omitiste y los archivos que ya existen en la carpeta de destino no se transfirieron. Por tu privacidad, puedes eliminar estos archivos. |
| 466 | `skipped_crypto_files_both_decrypt_message` | The files you skipped and the files that already exist in the destination folder were not transferred. You can delete these files. | Los archivos que omitiste y los archivos que ya existen en la carpeta de destino no se transfirieron. Puedes eliminar estos archivos. |
| 467 | `skipped_crypto_files_always_ask` | Always ask | Preguntar siempre |
| 468 | `delete_skipped_files_confirm_title` | Are you sure? | ¿Estás seguro? |
| 469 | `delete_skipped_files_confirm_message` | Your files will be deleted! | ¡Tus archivos se eliminarán! |
| 470 | `date_tie_break_label` | When content is identical but dates differ | Cuando el contenido es idéntico pero las fechas difieren |
| 471 | `date_tie_break_keep_target` | Keep the existing file\'s date (default) | Conservar la fecha del archivo existente (predeterminado) |
| 472 | `date_tie_break_use_source` | Use the source file\'s date | Usar la fecha del archivo de origen |
| 473 | `date_tie_break_ask` | Always ask | Preguntar siempre |
| 474 | `date_tie_break_hint` | Applies only to Smart dialog and Automatic mode. The Standard dialog is not affected. | Se aplica solo al diálogo inteligente y al modo automático. El diálogo estándar no se ve afectado. |
| 475 | `date_tie_break_ask_info_title` | Why "Always ask" disables Smart dialog and Automatic mode | Por qué "Preguntar siempre" desactiva el diálogo inteligente y el modo automático |
| 476 | `date_tie_break_ask_info_message` | Smart dialog and Automatic mode exist for one reason: to skip files whose content is already present, so you only see the files that really need your attention — same name, different content. That is exactly the case where comparing thumbnails can reveal bit-rot.\n\nIf you choose "Always ask" here, every identical file with a different date comes back to you as a conflict. In Smart dialog you will see two thumbnails of the very same picture, with nothing to verify, and you can decide there. Automatic (Experimental) mode never shows a dialog, so "Always ask" is not available there — selecting Automatic falls back to "Keep the existing file's date".\n\nUse this option only if you deliberately want to review every date difference, for example to fix a wrong date by hand.\n\nNote: when content is bit-identical, the EXIF capture date is identical too. A date difference means one side lost its EXIF data and fell back to the file modification date (the copy date). | El diálogo inteligente y el modo automático existen por una razón: omitir archivos cuyo contenido ya está presente, para que solo veas los archivos que realmente necesitan tu atención — mismo nombre, contenido distinto. Este es justamente el caso en el que comparar miniaturas puede revelar corrupción de bits.\n\nSi eliges "Preguntar siempre" aquí, todos los archivos idénticos con una fecha distinta volverán a presentarse como conflictos. En el diálogo inteligente verás dos miniaturas de la misma imagen, sin nada que verificar, y podrás decidir allí. El modo automático (experimental) nunca muestra un diálogo, por lo que "Preguntar siempre" no está disponible allí; al seleccionar Automático se vuelve a "Conservar la fecha del archivo existente".\n\nUsa esta opción solo si quieres revisar deliberadamente cada diferencia de fecha, por ejemplo para corregir una fecha equivocada a mano.\n\nNota: cuando el contenido es idéntico bit a bit, la fecha de captura EXIF también es idéntica. Una diferencia de fechas significa que un lado perdió sus datos EXIF y recurrió a la fecha de modificación del archivo (la fecha de copia). |
| 477 | `smart_conflict_counter` | %1$s (%2$d / %3$d) | %1$s (%2$d / %3$d) |
| 478 | `smart_conflict_incoming_label` | Incoming (New) | Entrante (nuevo) |
| 479 | `smart_conflict_existing_label` | Existing (Target) | Existente (destino) |
| 480 | `smart_conflict_corrupted_unreadable` | Corrupted or unreadable | Dañado o ilegible |
| 481 | `smart_conflict_integrity_failed` | Integrity check failed (Bit-rot / Corrupted) | Error en la comprobación de integridad (corrupción de bits / dañado) |
| 482 | `smart_conflict_tap_to_preview` | Tap either thumbnail to inspect full-size preview | Toca cualquiera de las miniaturas para ver la vista previa a tamaño completo |
| 483 | `smart_conflict_comparing` | Comparing file contents and dates… | Comparando el contenido y las fechas de los archivos… |
| 484 | `smart_conflict_content_differs_warning` | File contents differ.\nIf these two files were supposed to be the same, data corruption may have occurred. Keeping both and inspecting them is recommended. | El contenido de los archivos difiere.\nSi estos dos archivos debían ser iguales, puede haberse producido corrupción de datos. Se recomienda conservar ambos y examinarlos. |
| 485 | `smart_conflict_date_taken_differs` | File contents are identical, creation (date taken) timestamps differ. | El contenido de los archivos es idéntico, pero las marcas de tiempo de creación (fecha de la toma) difieren. |
| 486 | `smart_conflict_modified_date_differs` | File contents are identical, modification dates differ. | El contenido de los archivos es idéntico, pero las fechas de modificación difieren. |
| 487 | `smart_conflict_both_dates_differ` | File contents are identical, creation and modification dates differ. | El contenido de los archivos es idéntico, pero las fechas de creación y de modificación difieren. |
| 488 | `smart_conflict_meta_date_taken` | Taken: %s | Tomada: %s |
| 489 | `smart_conflict_meta_date_modified` | Modified: %s | Modificada: %s |
| 490 | `album_type_icons` | Album type icons | Iconos de tipo de álbum |
| 491 | `virtual_folders` | Virtual albums | Álbumes virtuales |
| 492 | `favorites_album` | Favorites | Favoritos |
| 493 | `excluded_folders_album` | Excluded folders | Carpetas excluidas |
| 494 | `hidden_folders_album` | Hidden folders | Carpetas ocultas |
| 495 | `temporarily_show_hidden_folders` | Temporarily show hidden folders | Mostrar carpetas ocultas temporalmente |
| 496 | `stop_showing_hidden_folders` | Stop showing hidden folders | Dejar de mostrar carpetas ocultas |
| 497 | `unexclude` | Stop excluding | Dejar de excluir |
| 498 | `hide_folder` | Hide | Ocultar |
| 499 | `unhide_folder` | Unhide | Mostrar |
| 500 | `about_title` | About Calypso Gallery | Acerca de Calypso Gallery |
| 501 | `about_version` | Version %1$s (%2$s) | Versión %1$s (%2$s) |
| 502 | `about_fork_description` | Calypso Gallery is a security-hardened, independently maintained personal fork of Fossify Gallery. Thanks to the original authors and contributors of Fossify Gallery. It is an unofficial project and is not affiliated with, endorsed by, or supported by the Fossify organization. | Calypso Gallery es un fork personal no oficial, mantenido de forma independiente, de Fossify Gallery. No está afiliado a Fossify ni cuenta con su respaldo. |
| 503 | `about_modification_notice` | This distribution is a modified version of Fossify Gallery v1.14.2 (2688360, 07-11-2026). All fork-specific modifications are documented in FORK_CHANGELOG.md. | Esta distribución es una versión modificada de Fossify Gallery. El fork se creó a partir del commit original 054228be2da275396eb03d2b9002807b5055afac (1.14.1-5-g054228be2), y todas las modificaciones específicas del fork están documentadas en FORK_CHANGELOG.md. |
| 504 | `about_license_heading` | License | Licencia |
| 505 | `about_gpl_notice` | This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 3 of the License.\n\nThis program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.\n\nYou should have received a copy of the GNU General Public License along with this program. If not, use the license action below or visit https://www.gnu.org/licenses/. | Este programa es software libre: puedes redistribuirlo y/o modificarlo bajo los términos de la GNU General Public License tal como la publica la Free Software Foundation, en su versión 3 de la licencia.\n\nEste programa se distribuye con la esperanza de que resulte útil, pero SIN NINGUNA GARANTÍA; ni siquiera la garantía implícita de COMERCIABILIDAD o de IDONEIDAD PARA UN FIN DETERMINADO. Consulta la GNU General Public License para obtener más detalles.\n\nDeberías haber recibido una copia de la GNU General Public License junto con este programa. En caso contrario, usa la acción de licencia que aparece a continuación o visita https://www.gnu.org/licenses/. |
| 506 | `about_read_gpl_license` | Read the GNU GPL v3 license | Leer la licencia GNU GPL v3 |
| 507 | `about_read_tink_license` | Read the Tink Apache 2.0 license and attribution | Leer la licencia Tink Apache 2.0 y sus atribuciones |
| 508 | `about_source_heading` | Source code | Código fuente |
| 509 | `about_corresponding_source_notice` | The complete corresponding source for this fork is kept in the fork repository. Tagged releases also have a matching source archive. The repository contains the build scripts, dependency declarations, and complete fork history needed to inspect and rebuild the project. | El código fuente completo y correspondiente de este fork se mantiene en el repositorio del fork. Las versiones etiquetadas también cuentan con un archivo de código fuente correspondiente. El repositorio contiene los scripts de compilación, las declaraciones de dependencias y el historial completo del fork necesarios para inspeccionar y reconstruir el proyecto. |
| 510 | `about_fork_source` | Fork source code and corresponding source | Código fuente del fork y código fuente correspondiente |
| 511 | `about_release_sources` | Release source archives | Archivos de código fuente de las versiones |
| 512 | `about_fork_issues` | Fork issue tracker | Rastreador de incidencias del fork |
| 513 | `about_upstream_source` | Upstream Fossify Gallery source code | Código fuente original de Fossify Gallery |
| 514 | `about_upstream_project` | Upstream Fossify project | Proyecto Fossify original |
| 515 | `about_help_heading` | Help and legal information | Ayuda e información legal |
| 516 | `about_faq` | Frequently asked questions | Preguntas frecuentes |
| 517 | `about_third_party_licenses` | Third-party licenses | Licencias de terceros |
| 518 | `gpl_license_title` | GNU General Public License v3 | GNU General Public License v3 |
| 519 | `tink_license_title` | Tink license and attribution | Licencia y atribuciones de Tink |
| 520 | `fork_disclaimer_title` | Fork notice | Aviso de fork |
| 521 | `fork_disclaimer_message` | This application is not developed by the Fossify team. Calypso Gallery is an independently maintained, unofficial fork of Fossify Gallery; the original developer is referenced solely for attribution. It is not affiliated with, endorsed by, or supported by Fossify.\n\nBy continuing you acknowledge this notice. Fork-specific changes are documented in FORK_CHANGELOG.md. | Esta aplicación no está desarrollada por el equipo de Fossify. Calypso Gallery es un fork no oficial, mantenido de forma independiente, de Fossify Gallery; el desarrollador original se menciona únicamente a modo de atribución. No está afiliado a Fossify, ni cuenta con su respaldo, ni recibe su soporte.\n\nAl continuar, aceptas este aviso. Los cambios específicos del fork están documentados en FORK_CHANGELOG.md. |
| 522 | `fork_disclaimer_acknowledge` | I understand | Entendido |
| 523 | `argon2_profile_title` | Brute-Force resistance | Resistencia a la fuerza bruta |
| 524 | `brute_force_mode_title` | Calibration mode | Modo de calibración |
| 525 | `brute_force_mode_time` | Via vault unlock time | Mediante el tiempo de desbloqueo de la bóveda |
| 526 | `brute_force_mode_presets` | Via ready-made modes | Mediante modos predefinidos |
| 527 | `brute_force_time_title` | Targeted vault unlock time | Tiempo de desbloqueo previsto de la bóveda |
| 528 | `brute_force_time_hint` | Select the time spent during opening the encrypted vault. Keeping this value high provides extra security by making brute-force attacks harder. | Selecciona el tiempo dedicado a abrir la bóveda cifrada. Mantener este valor alto proporciona seguridad adicional al dificultar los ataques de fuerza bruta. |
| 529 | `brute_force_time_min_hint` | The lower bound is this device's safe standard time. | El límite inferior es el tiempo estándar seguro de este dispositivo. |
| 530 | `brute_force_time_value_device` | %1$.1f s (on this device) | %1$.1f s (en este dispositivo) |
| 531 | `brute_force_time_out_of_range` | This time is above the selectable maximum (10 s). Tap to change it. | Este tiempo supera el máximo seleccionable (10 s). Toca para cambiarlo. |
| 532 | `brute_force_time_value` | %1$.1f s | %1$.1f s |
| 533 | `brute_force_actual_params` | Stored: %1$d MiB / %2$d rounds | Guardado: %1$d MiB / %2$d rondas |
| 534 | `brute_force_clamped` |  (clamped) |  (limitado) |
| 535 | `argon2_profile_low` | Low-end device (not recommended) | Dispositivo de gama baja (no recomendado) |
| 536 | `argon2_profile_default` | Default | Predeterminado |
| 537 | `argon2_profile_high` | High security | Alta seguridad |
| 538 | `argon2_profile_hint` | If you don\'t know what you\'re doing, it is recommended to keep the default setting. This only affects newly created vaults and password changes. | Si no sabes lo que haces, se recomienda mantener la configuración predeterminada. Esto solo afecta a las bóvedas recién creadas y a los cambios de contraseña. |
| 539 | `argon2_profile_current` | Current: %s | Actual: %s |
| 540 | `show_pin_dots` | Show a dot for each PIN digit | Mostrar un punto por cada dígito al introducir el PIN |

## Settings Headers, Font Options & File Encryption Info

Added/overridden in `values-es/strings.xml` for Settings section headers, font picker options (`Monospace` standardized), and file-only encryption info dialog:

| # | Key (`name`) | English | Spanish (Standard) (`values-es/strings.xml`) |
|--:|---|---|---|
| 1 | `application_settings` | Application | Aplicación |
| 2 | `video_playback_settings` | Video playback | Reproducción de vídeo |
| 3 | `thumbnails_and_scrolling_settings` | Thumbnails & scrolling | Miniaturas y desplazamiento |
| 4 | `media_viewer_settings` | Media viewer | Visor de medios |
| 5 | `security_and_vault_settings` | Security & encrypted vault | Seguridad y bóveda cifrada |
| 6 | `font_system_default` | System default | Sistema por defecto |
| 7 | `font_monospace` | Monospace | Monospace |
| 8 | `encrypt_files_info_message` | Folder & File Structure:\n• When an unencrypted album is encrypted, it is converted into an encrypted subfolder inside the vault (Secrets).\n• Both the folder name and all file names/contents inside it are encrypted, so nothing is readable outside the app. | Estructura de carpetas y archivos:\n• Cuando se cifra un álbum sin cifrar, se convierte en una subcarpeta cifrada dentro de la bóveda (Secrets).\n• Tanto el nombre de la carpeta como todos los nombres y contenidos de los archivos en su interior se cifran, por lo que nada es legible fuera de la aplicación. |

## Missing UI strings added (this revision)

`org.fossify:commons:7.1.3` does not provide these keys for Spanish, and `unlocking_encrypted_vault` was missing from the app-owned resources, so local overrides were added to `values-es/strings.xml`.

| Key (`name`) | English source | Spanish | Notes |
|---|---|---|---|
| `search_in_placeholder` | Search in %s | Buscar en %s | fossify-commons override; `%s` = album/folder name |
| `open_biometric_dialog` | Open biometric ID verification dialog | Abrir cuadro de verificación de ID biométrica | fossify-commons override; Biometrics tab of the inherited security dialog |
| `authenticate` | Authenticate | Autenticar | fossify-commons override; system biometric prompt title |
| `unlocking_encrypted_vault` | Unlocking encrypted vault… | Desbloqueando la bóveda cifrada… | App-owned key; consistent with `bóveda cifrada` terminology |

## Appearance — Custom Colors & Saved Themes (9 keys)

Added to `values-es/strings.xml` for the Appearance settings **Custom colors** section and **Saved themes** workflow (English source strings in `values/strings.xml` were also completed):

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (Standard) (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `custom_colors` | Custom colors | Colores personalizados | Appearance settings section header |
| 2 | `saved_themes` | Saved themes | Temas guardados | Appearance settings row and dialog title |
| 3 | `save_theme` | Save theme | Guardar tema | Positive dialog button to save current custom theme |
| 4 | `theme_saved` | Theme saved | Tema guardado | Toast shown after saving a custom theme |
| 5 | `theme_already_saved` | This theme is already saved | Este tema ya está guardado | Toast shown when the current palette is already saved or built-in |
| 6 | `no_saved_themes` | No saved themes yet | Aún no hay temas guardados | Empty state message in the Saved themes dialog |
| 7 | `custom_theme_name` | Custom theme | Tema personalizado | Base name for newly saved custom themes (suffixed with index) |
| 8 | `discard_changes_question` | Do you want to save your changes? | ¿Quieres guardar los cambios? | Prompt shown when switching themes with unsaved custom color changes |
| 9 | `continue_without_saving` | Continue without saving | Continuar sin guardar | Negative dialog button to discard unsaved custom color changes |

## Onboarding & Full-Screen Permission Flow (29 keys)

Added to `values-es/strings.xml` for the 3-screen first-launch welcome and full-screen permission flow (unsupported system locales fall back cleanly to English in `values/strings.xml`). Also removed the retired `auto_backup_prompt_message` key since auto-backup is now enabled by default and always shown in Settings.

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `onboarding_motto` | No sign-up, no cloud, just your gallery. | Sin registro, sin nube, solo tu galería. | Welcome screen tagline |
| 2 | `onboarding_offline_title` | Completely Offline | Totalmente sin conexión | Welcome card 1 title |
| 3 | `onboarding_offline_desc` | Never connects to the internet. Your data belongs only to you. | No se conecta a internet. Tus datos solo te pertenecen a ti. | Welcome card 1 description |
| 4 | `onboarding_vault_title` | Encrypted Vault | Bóveda cifrada | Welcome card 2 title |
| 5 | `onboarding_vault_desc` | Encrypt your photos and videos. No one else can access them. | Cifra tus fotos y vídeos. Nadie más podrá acceder a ellos. | Welcome card 2 description |
| 6 | `onboarding_albums_title` | Playlists for your photos | Listas de reproducción para tus fotos | Welcome card 3 title |
| 7 | `onboarding_albums_desc` | Virtual albums let you bring the files you want to see together in a single album, without moving them between folders or creating copies on disk. Unlike a regular copy, they use no extra storage because only the original file is kept on disk. You can add the same file to as many albums as you like, while your folder structure stays exactly as it is. | Los álbumes virtuales te permiten reunir los archivos que quieres ver juntos en un único álbum, sin moverlos entre carpetas ni crear copias en el disco. A diferencia de una copia normal, no consumen espacio de almacenamiento porque en el disco solo existe el archivo original. Puedes añadir el mismo archivo a todos los álbumes que quieras, mientras tu estructura de carpetas se mantiene intacta. | Welcome card 3 description |
| 8 | `onboarding_media_permission_note` | Media access permission is required to display your photos and videos. | Se necesita permiso de acceso multimedia para mostrar tus fotos y vídeos. | Welcome footer media permission note |
| 9 | `onboarding_continue` | Continue | Continuar | Welcome primary button |
| 10 | `onboarding_media_required_title` | Full media access required | Se requiere acceso multimedia completo | Media permission screen title |
| 11 | `onboarding_media_required_desc` | Calypso Gallery needs full access to display all your photos and videos. | La Galería Calypso necesita acceso total para mostrar todas tus fotos y vídeos. | Media permission screen description |
| 12 | `onboarding_media_steps_header` | How to enable permission | Cómo activar el permiso | Settings steps card header |
| 13 | `onboarding_media_step_1` | Settings | Ajustes | Settings guide step 1 |
| 14 | `onboarding_media_step_2` | Permissions | Permisos | Settings guide step 2 |
| 15 | `onboarding_media_step_3` | Photos and videos | Fotos y vídeos | Settings guide step 3 |
| 16 | `onboarding_media_step_4` | Allow all | Permitir todo | Settings guide step 4 (final) |
| 17 | `onboarding_media_required_hint` | Once access is granted, your gallery will open automatically when you return to the app. | Una vez concedido el acceso, tu galería se abrirá automáticamente al volver a la aplicación. | Media permission screen return hint |
| 18 | `onboarding_go_to_settings` | Go to settings | Ir a ajustes | Media permission primary button |
| 19 | `onboarding_close` | Close | Cerrar | Media permission secondary button |
| 20 | `onboarding_all_files_title` | All files access | Acceso a todos los archivos | All files access screen title |
| 21 | `onboarding_all_files_desc` | All files access is recommended for the Encrypted Vault and seamless file management. | Se recomienda el acceso a todos los archivos para la bóveda cifrada y una gestión de archivos fluida. | All files access screen subtitle |
| 22 | `onboarding_all_files_vault_title` | Encrypted Vault and hidden folders | Bóveda cifrada y carpetas ocultas | All files card 1 title |
| 23 | `onboarding_all_files_vault_desc` | Manage your encrypted files and hidden folders without system restrictions. | Gestiona tus archivos cifrados y carpetas ocultas sin restricciones del sistema. | All files card 1 description |
| 24 | `onboarding_all_files_backup_title` | Don\'t lose your Favorites and Virtual Albums | No pierdas tus favoritos ni álbumes virtuales | All files card 2 title |
| 25 | `onboarding_all_files_backup_desc` | Keep your favorites and virtual albums automatically backed up on your device even if you accidentally uninstall the app. You can turn off automatic backup anytime in app settings. | Aunque desinstales la aplicación por error, tus favoritos y álbumes virtuales se respaldarán automáticamente en tu dispositivo. Puedes desactivar la copia automática en los ajustes de la aplicación. | All files card 2 description |
| 26 | `onboarding_all_files_offline_title` | Still completely on your device | Sigue completamente en tu dispositivo | All files card 3 title |
| 27 | `onboarding_all_files_offline_desc` | The app stays completely offline, this permission is used only for local file operations. | La aplicación sigue funcionando sin conexión, este permiso solo se utiliza para operaciones locales de archivos. | All files card 3 description |
| 28 | `onboarding_grant_access` | Grant access | Conceder acceso | All files primary button |
| 29 | `onboarding_not_now` | Not now | Ahora no | All files secondary button |

## Vault Lock & Album Header Polish (4 keys)

Added to `values-es/strings.xml` for the redesigned Vault Lock screen biometric button and album interior header.

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `use_biometrics` | Use biometrics | Usar biometría | Vault lock screen biometric button label |
| 2 | `media_header_items_count` | %d items | %d elementos | MediaActivity album header item count |
| 3 | `media_header_virtual_album` | Virtual album · %d items | Álbum virtual · %d elementos | MediaActivity virtual album header subtitle |
| 4 | `media_header_encrypted_album` | Encrypted album · %d items | Álbum cifrado · %d elementos | MediaActivity encrypted album header subtitle |
## Appearance Live Preview (4 keys)

Added to `values-es/strings.xml` for the live Appearance preview card and WCAG contrast warning.

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `appearance_preview` | Preview | Vista previa | Appearance live preview section header |
| 2 | `appearance_preview_text` | Your memories, your colors | Tus recuerdos, tus colores | Appearance preview sample headline |
| 3 | `appearance_preview_button` | Sample button | Botón de ejemplo | Appearance preview sample button |
| 4 | `appearance_contrast_warning` | Low contrast: text %1$.1f:1, button %2$.1f:1. Increase it for readability. | Bajo contraste: texto %1$.1f:1, botón %2$.1f:1. Auméntalo para mejorar la legibilidad. | WCAG contrast warning in Appearance preview |

## Vault Privacy Input Toggle (1 key)

Added to `values-es/strings.xml` for the optional shoulder-surfing protection switch (default off).

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `hide_vault_key_presses` | Hide key presses when entering vault password | Ocultar las pulsaciones al introducir la contraseña de la bóveda | Settings switch to hide PIN/password key press feedback |

## PIN Dot Indicator Setting (1 key)

Added to `values-es/strings.xml` for the optional per-digit PIN dot indicator (default off; only shown for PINs of at most 16 digits).

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `show_pin_dots` | Show a dot for each PIN digit | Mostrar un punto por cada dígito al introducir el PIN | Settings switch; hidden for alphanumeric vaults and PINs longer than 16 digits |

## KDF Unlock-Time Hint Revision (this revision)

Updated `brute_force_time_hint` and added `brute_force_time_min_hint` in `values-es/strings.xml`. The lower-bound sentence is appended only when the device's measured safe floor is above 1.0 s.

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (Standard) (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_time_hint` | Select the time spent during opening the encrypted vault. Keeping this value high provides extra security by making brute-force attacks harder. | Selecciona el tiempo dedicado a abrir la bóveda cifrada. Mantener este valor alto proporciona seguridad adicional al dificultar los ataques de fuerza bruta. | Explanation shown in the target vault unlock time dialog |
| 2 | `brute_force_time_min_hint` | The lower bound is this device's safe standard time. | El límite inferior es el tiempo estándar seguro de este dispositivo. | Appended only when minSec > 1.0 |

## Biometric Unavailability Explanation (this revision)

Added to `values-es/strings.xml`. The Settings row now stays visible when a vault exists; if the device only has weak (Class 2) biometrics or cannot create a hardware-backed key, the row is disabled and these strings explain why in a dialog and under the row. They replace the short `no_biometric_hardware_or_enrolled` toast. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `vault_biometric_unavailable_title` | Biometric vault access unavailable | Acceso biométrico a la bóveda no disponible | Dialog title |
| 2 | `vault_biometric_unavailable_no_hardware` | This device has no strong biometric hardware (fingerprint or Class 3 face unlock). Vault biometric access is unavailable; use your PIN or password. | Este dispositivo no dispone de hardware biométrico fuerte (huella dactilar o desbloqueo facial de clase 3). El acceso biométrico a la bóveda no está disponible; use su PIN o contraseña. | `BIOMETRIC_ERROR_NO_HARDWARE` |
| 3 | `vault_biometric_unavailable_none_enrolled` | No strong biometric is enrolled. Weak face unlock is not accepted; enrol a fingerprint or a Class 3 face unlock in system settings. | No hay ninguna biometría fuerte registrada. No se acepta el desbloqueo facial débil; registre una huella dactilar o un desbloqueo facial de clase 3 en los ajustes del sistema. | `BIOMETRIC_ERROR_NONE_ENROLLED`; weak face unlock rejected |
| 4 | `vault_biometric_unavailable_hw_unavailable` | The biometric sensor is temporarily unavailable. Try again later. | El sensor biométrico no está disponible temporalmente. Inténtelo de nuevo más tarde. | `BIOMETRIC_ERROR_HW_UNAVAILABLE` |
| 5 | `vault_biometric_unavailable_security_update` | Biometrics require a security update before they can be used. | La biometría requiere una actualización de seguridad antes de poder utilizarse. | `BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED` |
| 6 | `vault_biometric_unavailable_no_secure_key` | A hardware-backed key (TEE/StrongBox) could not be created on this device. Vault biometric access is disabled for security reasons. | No se pudo crear una clave compatible con hardware (TEE/StrongBox) en este dispositivo. El acceso biométrico a la bóveda está desactivado por motivos de seguridad. | `canAuthenticate()` succeeded but no TEE/StrongBox key |
| 7 | `vault_biometric_unavailable_generic` | Vault biometric access is unavailable on this device. Enrol a fingerprint or a strong face unlock in system settings, or use your PIN or password. | El acceso biométrico a la bóveda no está disponible en este dispositivo. Registre una huella dactilar o un desbloqueo facial fuerte en los ajustes del sistema, o utilice su PIN o contraseña. | Fallback explanation |

## Password Reminder Strings (this revision)

Added/updated in `values-es/strings.xml`. The reminder is shown in-app when the vault is opened and no password/PIN unlock happened during the configured interval. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (Standard) (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `vault_password_reminder` | Remind me of my password | Recordarme mi contraseña |  |
| 2 | `vault_password_reminder_disabled` | Off | Desactivado |  |
| 3 | `vault_password_reminder_weeks` | %d weeks | %d semanas |  |
| 4 | `vault_password_reminder_months` | %d months | %d meses |  |
| 5 | `vault_password_reminder_dialog_title` | Reminder interval | Intervalo de recordatorio |  |
| 6 | `vault_password_reminder_dialog_message` | If you do not open the encrypted vault with your password for this long, we will remind you of it. | Si no abre la bóveda cifrada con su contraseña durante este tiempo, se lo recordaremos. |  |
| 7 | `vault_password_reminder_setup_note` | We will send you periodic reminders so you do not forget your password. You can turn this off in Settings → Encrypted vault → Remind me of my password. | Le enviaremos recordatorios periódicos para que no olvide su contraseña. Puede desactivarlos en Ajustes → Bóveda cifrada → Recordarme mi contraseña. |  |
| 8 | `vault_biometric_setup_password_note` | If you choose to sign in with biometrics, remember that you still need to know your password. | Si elige iniciar sesión con biometría, recuerde que debe seguir conociendo su contraseña. |  |
| 9 | `vault_password_saved_title` | Have you saved your password? | ¿Ha guardado su contraseña? |  |
| 10 | `vault_password_saved_message` | If you forget your password, you will permanently lose access to your encrypted vault. Make sure you have saved it somewhere before continuing. Using a password manager is recommended. | Si olvida su contraseña, perderá permanentemente el acceso a su bóveda cifrada. Asegúrese de guardarla en algún lugar antes de continuar. Se recomienda usar un gestor de contraseñas. |  |
| 11 | `vault_password_saved_confirm` | Yes, I saved it | Sí, la he guardado |  |
| 12 | `vault_password_saved_later` | I will save it | La guardaré |  |
| 13 | `vault_password_reminder_due_title` | Do you still remember your vault password? | ¿Recuerda aún la contraseña de su bóveda? |  |
| 14 | `vault_password_reminder_due_message` | You have not opened the encrypted vault with your password for this long. Your password is the only way to recover the vault. | No ha abierto la bóveda cifrada con su contraseña durante este tiempo. Su contraseña es la única forma de recuperar la bóveda. |  |
| 15 | `vault_password_reminder_verify_title` | Confirm your password | Confirme su contraseña |  |
| 16 | `vault_password_reminder_verify_message` | This is not a vault unlock. We only want to confirm that you still know your password. | Esto no es una apertura de la bóveda. Solo queremos confirmar que aún conoce su contraseña. |  |
| 17 | `vault_password_reminder_verified` | Password confirmed | Contraseña confirmada |  |
| 18 | `vault_password_reminder_not_now` | Not now | Ahora no |  |
| 19 | `vault_password_reminder_forgot_title` | Do not remember your password? | ¿No recuerda su contraseña? |  |
| 20 | `vault_password_reminder_forgot_steps` | 1. Unlock the vault with biometrics.\n2. Copy (export) your encrypted data — this does not move the vault.\n3. Settings → Reset encrypted vault to set a new password and restore.\n\nIf biometrics cannot unlock it either, the vault is inaccessible. You can archive the current vault and create a new one, copy the encrypted data for another device, or delete the vault folder at %1$s with a file manager (cannot be undone). | Abra la bóveda con biometría, exporte sus datos cifrados y luego use Ajustes → Restablecer bóveda cifrada para establecer una nueva contraseña y restaurar sus datos. Si la biometría tampoco puede abrirla, la bóveda es inaccesible. |  |
| 21 | `vault_password_reminder_verify_action` | Verify my password | Verificar mi contraseña |  |
| 22 | `vault_password_reminder_forgot_action` | I don't remember | No la recuerdo |  |
| 23 | `vault_password_reminder_open_settings` | Open settings | Abrir ajustes |  |
| 24 | `empty_encrypted_vault` | Reset encrypted vault | Restablecer bóveda cifrada | Renamed label |

## Vault Archive & Forgot-Password Strings (this revision)

Added/updated in `values-es/strings.xml`. Archive moves the old vault; export copies it; deletion is left to the file manager (ADR 0007). English source: `values/strings.xml`.

| # | Key (`name`) | English | Spanish (Standard) |
|--:|---|---|---|
| 1 | `archive_vault` | Archive encrypted vault | Archivar bóveda cifrada |
| 2 | `archive_vault_summary` | Moves the old vault to the archive and creates a new one | Mueve la bóveda antigua al archivo y crea una nueva |
| 3 | `archive_vault_confirm` | The current vault will be moved to the archive and a new vault will be created in the default location. Nothing is deleted. To use the vault on another device, use Export encrypted vault — it copies the files and the current vault stays where it is. | La bóveda actual se moverá al archivo y se creará una nueva en la ubicación predeterminada. No se elimina nada. Para usarla en otro dispositivo, use Exportar bóveda cifrada: copia los archivos y la bóveda actual permanece. |
| 4 | `archive_vault_success` | Vault archived. Location: %1$s | Bóveda archivada. Ubicación: %1$s |
| 5 | `archive_vault_failed` | Could not archive the vault. Nothing was changed. | No se pudo archivar la bóveda. No se cambió nada. |
| 6 | `select_archived_vault` | Select from archived vaults | Seleccionar de bóvedas archivadas |
| 7 | `archived_vault_indexed` | %1$d. archived vault | Bóveda archivada n.º %1$d |
| 8 | `archived_vault_unknown_date` | Encrypted vault with unknown date | Bóveda cifrada de fecha desconocida |
| 9 | `archived_vault_invalid` | Invalid archive entry | Entrada de archivo no válida |
| 10 | `archived_vault_invalid_message` | Vault header not found at %1$s. You can delete this folder with a file manager. | No se encontró la cabecera de la bóveda en %1$s. Puede eliminar la carpeta con un gestor de archivos. |
| 11 | `copy_path` | Copy path | Copiar ruta |
| 12 | `faq_forgot_password_title` | I forgot my encrypted vault password, what can I do? | Olvidé la contraseña de la bóveda cifrada, ¿qué puedo hacer? |
| 13 | `faq_forgot_password_text` | 1. Unlock the vault with biometrics.\n2. Copy (export) your encrypted data — copying is not moving; the original stays in place.\n3. Settings → Reset encrypted vault to set a new password and restore.\n\nWarning: if biometrics cannot unlock it either, the vault is inaccessible. You can archive the current vault and create a new one, copy the encrypted data for another device, or delete the vault folder at %1$s with a file manager. Deletion cannot be undone. | 1. Acceda con biometría. 2. Copie (exporte) sus datos cifrados; copiar no es mover. 3. Ajustes → Restablecer bóveda cifrada para crear una contraseña nueva.  Advertencia: si la biometría tampoco funciona, la bóveda es inaccesible. Puede archivarla, copiar los datos o eliminar la carpeta en %1$s con un gestor de archivos. |
| 14 | `vault_password_reminder_forgot_steps` | 1. Unlock the vault with biometrics.\n2. Copy (export) your encrypted data — this does not move the vault.\n3. Settings → Reset encrypted vault to set a new password and restore.\n\nIf biometrics cannot unlock it either, the vault is inaccessible. You can archive the current vault and create a new one, copy the encrypted data for another device, or delete the vault folder at %1$s with a file manager (cannot be undone). | 1. Acceda con biometría. 2. Copie los datos cifrados. 3. Ajustes → Restablecer bóveda cifrada.  Si la biometría tampoco funciona: archive, copie o elimine la carpeta %1$s con un gestor de archivos. |

## Unarchive Strings (this revision)

Added to `values-es/strings.xml`. Unarchive moves the vault back to its recorded original location; no swap, no password.

| # | Key | English | Spanish |
|--:|---|---|---|
| 1 | `unarchive_vault` | Unarchive vault | Sacar del archivo |
| 2 | `unarchive_vault_summary` | Moves the vault back to its location before archiving | Devuelve la bóveda a su ubicación anterior al archivado |
| 3 | `unarchive_vault_confirm` | The vault will be moved back to its original location. No password is required. Continue? | La bóveda volverá a su ubicación original. No se necesita contraseña. ¿Continuar? |
| 4 | `unarchive_vault_success` | The vault was moved back to its original location. | La bóveda volvió a su ubicación original. |
| 5 | `unarchive_vault_blocked` | Another vault is located where this vault used to be. Archive that vault first, then unarchive this one. | En la ubicación anterior de esta bóveda hay otra. Archive primero esa bóveda y luego saque esta del archivo. |
| 6 | `unarchive_vault_merge` | Combine data into one vault | Unir los datos en una sola bóveda |
| 7 | `unarchive_vault_merge_steps` | Unlock the archived vault with its password, export the files in decrypted form, then import them into the other vault. The existing conflict-handling modes apply. | Abra la bóveda archivada con su contraseña, exporte los archivos descifrados e impórtelos en la otra bóveda. |
| 8 | `archived_vault_activated` | The vault is open from the archive. Use Settings → Unarchive vault to move it back to its original location. | Bóveda abierta desde el archivo. Use Ajustes → Sacar del archivo para devolverla. |

## Export Target Conflict Strings (this revision)

Added to `values-es/strings.xml`. Export now targets `<picked>/Calypso Gallery/Secrets` and is blocked when the target holds a different vault.

| # | Key | English | Spanish |
|--:|---|---|---|
| 1 | `export_vault_different_vault` | The destination already contains a different encrypted vault. Export was cancelled to protect it. Choose an empty folder. | La carpeta de destino ya contiene otra bóveda cifrada. Se canceló la exportación para protegerla. Elija una carpeta vacía. |
| 2 | `export_vault_unknown_content` | The destination contains encrypted files but no vault header. Export was cancelled to avoid mixing vaults. | La carpeta de destino contiene archivos cifrados pero no cabecera de bóveda. Se canceló la exportación. |

## Existing Vault Time Recalculation (this revision)





Added `brute_force_time_value_device` and `brute_force_time_out_of_range` to `values-es/strings.xml`. The Settings/vault-setup summary now shows the existing vault's real unlock time on the current device (measured on unlock, otherwise estimated from the portable `.vault` KDF cost), with a note when it exceeds the 10 s maximum, showing only the stored parameters when this device has no measurement yet.

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (Standard) (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_time_value_device` | %1$.1f s (on this device) | %1$.1f s (en este dispositivo) | Value shown for an existing vault |
| 2 | `brute_force_time_out_of_range` | This time is above the selectable maximum (10 s). Tap to change it. | Este tiempo supera el máximo seleccionable (10 s). Toca para cambiarlo. | Shown when the real time exceeds 10 s |

## Silent identical-skip toast & Automatic mode date tie-break (this revision)

Added the `identical_files_skipped` plural and revised `date_tie_break_ask_info_message` in `values-es/strings.xml`. Smart dialog and Automatic mode now report silently skipped, byte-identical files at the end of an operation with a short toast; identical files with different dates that were reviewed in the thumbnail conflict dialog are excluded. "Always ask" can no longer be selected in Automatic (Experimental) mode and falls back to keeping the existing file's date. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (Standard) (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `identical_files_skipped` | one: "%d identical file skipped" / other: "%d identical files skipped" | one: "Se omitió %d archivo idéntico" / many: "Se omitieron %d archivos idénticos" / other: "Se omitieron %d archivos idénticos" | Short end-of-operation toast in Smart/Automatic mode |
| 2 | `date_tie_break_ask_info_message` | Smart dialog and Automatic mode exist for one reason: to skip files whose content is already present, so you only see the files that really need your attention — same name, different content. That is exactly the case where comparing thumbnails can reveal bit-rot.\n\nIn Smart dialog you will see two thumbnails of the very same picture, with nothing to verify, and you can decide there. Automatic (Experimental) mode never shows a dialog, so "Always ask" is not available there — selecting Automatic falls back to "Keep the existing file's date".\n\nUse this option only if you deliberately want to review every date difference, for example to fix a wrong date by hand.\n\nNote: when content is bit-identical, the EXIF capture date is identical too. A date difference means one side lost its EXIF data and fell back to the file modification date (the copy date). | El diálogo inteligente y el modo automático existen por una razón: omitir archivos cuyo contenido ya está presente, para que solo veas los archivos que realmente necesitan tu atención — mismo nombre, contenido distinto. Este es justamente el caso en el que comparar miniaturas puede revelar corrupción de bits.\n\nSi eliges "Preguntar siempre" aquí, todos los archivos idénticos con una fecha distinta volverán a presentarse como conflictos. En el diálogo inteligente verás dos miniaturas de la misma imagen, sin nada que verificar, y podrás decidir allí. El modo automático (experimental) nunca muestra un diálogo, por lo que "Preguntar siempre" no está disponible allí; al seleccionar Automático se vuelve a "Conservar la fecha del archivo existente".\n\nUsa esta opción solo si quieres revisar deliberadamente cada diferencia de fecha, por ejemplo para corregir una fecha equivocada a mano.\n\nNota: cuando el contenido es idéntico bit a bit, la fecha de captura EXIF también es idéntica. Una diferencia de fechas significa que un lado perdió sus datos EXIF y recurrió a la fecha de modificación del archivo (la fecha de copia). | Revised: "Always ask" unavailable in Automatic; falls back to keep-target date |

## New strings (biometric setup, device-owner check, archive size)

| Key | Translation |
|---|---|
| `vault_biometric_setup_continue` | Continuar |
| `device_owner_verify_title` | Verificar al propietario del dispositivo |
| `device_owner_verify_subtitle` | Confirme con su biometría o el PIN del dispositivo para continuar. No se requiere la contraseña de la bóveda. |
| `archive_total_size_title` | Almacenamiento del archivo |
| `archive_total_size` | Tamaño total de las bóvedas archivadas: %1$s |

## Weak password confirmation

| Key | Translation |
|---|---|
| `weak_password_warning_title` | Contraseña débil |
| `weak_password_warning_message` | Esta contraseña se considera débil. Se recomienda una contraseña más larga y compleja para la seguridad de la bóveda. ¿Desea continuar de todos modos? |
| `weak_password_warning_continue` | Continuar de todos modos |

## Minimalist Settings, Appearance Redesign & Recent Fork Strings (this revision)

| Key | English | Spanish (Standard) |
|---|---|---|
| `appearance_title` | Appearance | Apariencia |
| `search_settings` | Search settings | Buscar en ajustes |
| `license_tink_title` | Google Tink (streaming AEAD and HKDF cryptography) | Google Tink (criptografía AEAD en flujo y HKDF) |
| `license_rustcrypto_argon2_title` | RustCrypto Argon2 (native Argon2id key derivation) | RustCrypto Argon2 (derivación de claves Argon2id nativa) |
| `license_webpdecoder_title` | GlideWebpDecoder (animated WebP decoding) | GlideWebpDecoder (decodificación de WebP animado) |
| `license_jxl_coder_title` | JXL Coder Glide (JPEG XL support) | JXL Coder Glide (compatibilidad con JPEG XL) |
| `license_androidsvg_title` | AndroidSVG (SVG rendering) | AndroidSVG (renderizado de SVG) |
| `license_exif_extended_title` | Android-Exif-Extended (EXIF metadata handling) | Android-Exif-Extended (gestión de metadatos EXIF) |
| `license_material_icons_title` | Material Design Icons (UI icons) | Material Design Icons (iconos de interfaz) |
| `checking_password` | Checking password… | Comprobando contraseña… |
| `vault_same_password_title` | Same password | Misma contraseña |
| `vault_same_password_message` | The new password is identical to the current vault password. Please choose a different one. | La nueva contraseña es idéntica a la contraseña actual de la bóveda. Elige una diferente. |
| `brute_force_time_with_params` | %1$.1f s (%2$d MiB / %3$d rounds) | %1$.1f s (%2$d MiB / %3$d rondas) |
| `brute_force_high_memory_warning` | Higher security uses more memory. You may have trouble opening your vault on devices with 2 GB of RAM or less. If that happens, lower the unlock time in app settings and export the vault again. | Una mayor seguridad usa más memoria. Es posible que tengas problemas para abrir tu bóveda en dispositivos con 2 GB de RAM o menos. En ese caso, reduce el tiempo de apertura en los ajustes de la aplicación y vuelve a exportar la bóveda. |
| `measuring_device_kdf_speed` | Measuring standard KDF speed on this device… | Midiendo la velocidad KDF estándar en este dispositivo… |
| `kdf_settings_updated_successfully` | Brute-Force resistance updated for encrypted vault | Resistencia frente a fuerza bruta actualizada para la bóveda cifrada |
| `appearance_automatic` | Automatic | Automático |
| `follow_system_theme` | Follow system theme | Seguir el tema del sistema |
| `follow_system_theme_summary` | Automatically switches between light and dark | Cambia automáticamente entre tema claro y oscuro |
| `theme_midnight_blue` | Midnight Blue | Azul medianoche |
| `theme_warm_paper` | Warm Paper | Papel cálido |
| `theme_deep_forest` | Deep Forest | Bosque profundo |
| `appearance_contrast_ok` | Good contrast · Text %1$.1f:1 · Button %2$.1f:1 | Buen contraste · Texto %1$.1f:1 · Botón %2$.1f:1 |
| `appearance_backup_section` | Backup | Copia de seguridad |
| `backup_saved_themes_to_storage` | Back up saved themes to device storage | Hacer copia de seguridad de los temas guardados en el dispositivo |
| `settings_group_general` | General | General |
| `settings_group_folders_and_loading` | Folders and loading | Carpetas y carga |
| `settings_group_security` | Security | Seguridad |
| `settings_group_vault_management` | Vault management | Gestión de la bóveda |
| `settings_group_playback` | Playback | Reproducción |
| `settings_group_gestures_and_tap` | Gestures and tap | Gestos y toques |
| `settings_group_display_and_motion` | Display and motion | Visualización y movimiento |
| `settings_group_thumbnail_styles` | Thumbnail styles | Estilos de miniaturas |
| `settings_group_general_behavior` | General behavior | Comportamiento general |
| `settings_group_conflicts_and_crypto` | Conflicts and encryption rules | Conflictos y reglas de cifrado |
| `settings_group_cleanup` | Cleanup | Limpieza |
| `settings_group_storage` | Storage | Almacenamiento |
| `settings_group_settings_and_favorites` | Settings and favorites | Ajustes y favoritos |
| `settings_group_vault_transfer` | Encrypted vault transfer | Transferencia de la bóveda cifrada |
| `settings_search_results` | Search results | Resultados de búsqueda |
| `settings_no_search_results` | No matching settings found. | No se encontraron ajustes coincidentes. |

## Active keys added since 2688360 (7 November) - added in this revision

The following strings were added after the base commit `2688360` (7 November) and are now documented here. English source: `values/strings.xml` (or the corresponding license/defaults resource). Target source: `values-es/strings.xml`.

| # | Key (`name`) | English | es (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `about_copyright_notice` | Copyright © Fossify Gallery contributors\nCopyright © 2026 Calypso Gallery contributors | *(translatable="false"; not localized)* | |
| 2 | `app_name` | Calypso Gallery | *(translatable="false"; not localized)* | |
| 3 | `auto_biometric_prompt_on_vault_return` | Automatically show biometric prompt when returning to app | Mostrar automáticamente la solicitud biométrica al volver a la aplicación | |
| 4 | `biometric_access_to_encrypted_vault` | Biometric access to encrypted vault | Acceso biométrico a la bóveda cifrada | |
| 5 | `change_encrypted_vault_password` | Change encrypted vault password | Cambiar contraseña de la bóveda cifrada | |
| 6 | `create_encrypted_vault` | Create encrypted vault | Crear bóveda cifrada | |
| 7 | `delete_auto_backup_confirm` | Delete device storage backups for the %1$s created while this setting was enabled? | ¿Eliminar las copias de seguridad del almacenamiento del dispositivo de %1$s creadas mientras esta opción estaba activada? | |
| 8 | `delete_auto_backup_target_both` | favorites and virtual albums | favoritos y álbumes virtuales | |
| 9 | `delete_auto_backup_target_favorites` | favorites | favoritos | |
| 10 | `delete_auto_backup_target_virtual_albums` | virtual albums | álbumes virtuales | |
| 11 | `empty_encrypted_vault_warning` | The encrypted vault (Secrets) and all its files will be permanently deleted. Are you sure? | Se eliminarán de forma permanente la bóveda cifrada (Secrets) y todos sus archivos. ¿Está seguro? | |
| 12 | `encrypted_vault` | Encrypted vault | Bóveda cifrada | |
| 13 | `encrypted_vault_emptied` | Encrypted vault emptied | Bóveda cifrada vaciada | |
| 14 | `enter_new_password` | Enter new password | Ingrese la nueva contraseña | |
| 15 | `enter_new_pin` | Enter new PIN | Ingrese el nuevo PIN | |
| 16 | `fast_large_duplicate_check_info_message` | Why this matters for large copy/move transfers:\n\nWhen copying or moving large files, such as gigabytes of videos, exact bit-by-bit checking must read both the source and destination files from start to finish whenever filenames and sizes match—taking minutes on fast storage or much longer on SD cards.\n\nHow Fast Check works:\n• Files under 8 MB are ALWAYS checked 100% bit-by-bit.\n• For files over 8 MB, the app compares file size plus five 256 KB blocks distributed across the start, 25%, 50%, 75%, and end of the file (1.25 MB total per side).\n• For a 50 GB archive of 50 MB–1 GB videos, this cuts disk reads by ~40× to ~800×.\n\nWhen to enable or disable:\n• Recommended ON: If most of your gallery consists of large files (&gt;8 MB), two intact, distinct files sharing the same name, exact byte size, and all 5 sampled regions is virtually impossible in practice.\n• Recommended OFF (no speedup): If your gallery consists mostly of photos or files under 8 MB, this setting provides no significant speed gain since files under 8 MB are always checked 100% bit-by-bit.\n• Encrypt, decrypt, and external USB vault sync operations always perform full bit-by-bit verification. | Por qué esto importa en transferencias grandes de copia o movimiento:\n\nAl copiar o mover archivos grandes, como gigabytes de vídeos, la comprobación exacta bit a bit debe leer tanto el archivo de origen como el de destino de principio a fin cada vez que coinciden los nombres y tamaños, lo que lleva minutos en almacenamiento rápido o mucho más en tarjetas SD.\n\nCómo funciona la comprobación rápida:\n• Los archivos menores de 8 MB SIEMPRE se comprueban al 100% bit a bit.\n• Para archivos de más de 8 MB, la aplicación compara el tamaño del archivo más cinco bloques de 256 KB distribuidos al inicio, 25%, 50%, 75% y final del archivo (1,25 MB en total por lado).\n• Para un archivo de 50 GB con vídeos de 50 MB–1 GB, esto reduce las lecturas en disco entre ~40× y ~800×.\n\nCuándo activarla o desactivarla:\n• Recomendado ACTIVADO: Si la mayor parte de su galería consta de archivos grandes (&gt;8 MB), es prácticamente imposible que dos archivos intactos y distintos compartan el mismo nombre, el tamaño exacto en bytes y las 5 regiones muestreadas.\n• Recomendado DESACTIVADO (sin mejora de velocidad): Si su galería consta principalmente de fotos o archivos menores de 8 MB, este ajuste no ofrece una ganancia de velocidad significativa, ya que los archivos menores de 8 MB siempre se comprueban al 100% bit a bit.\n• La sincronización de bóveda USB externa siempre mantiene autenticación Tink AEAD completa. | |
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
| 34 | `no_virtual_albums_yet` | No virtual albums yet. Tap \"New album\" to create one. | Aún no hay álbumes virtuales. Toca \"Nuevo álbum\" para crear uno. | |
| 35 | `password_changed_successfully` | Password changed successfully | Contraseña cambiada con éxito | |
| 36 | `refresh` | Refresh | Actualizar | |
| 37 | `repeat_new_password` | Repeat new password | Repita la nueva contraseña | |
| 38 | `repeat_new_pin` | Repeat new PIN | Repita el nuevo PIN | |
| 39 | `resume_op_copy` | Copy | Copia | |
| 40 | `resume_op_decrypt` | Decryption | Descifrado | |
| 41 | `resume_op_encrypt` | Encryption | Cifrado | |
| 42 | `resume_op_move` | Move | Movimiento | |
| 43 | `resume_operation_aborted_message` | %1$s operation was aborted. Already completed files were kept. | La operación de %1$s fue cancelada. Se conservaron los archivos ya completados. | |
| 44 | `resume_operation_interrupted_message` | %1$s operation was interrupted (%2$d remaining item(s)). No data was lost — do you want to continue? | La operación de %1$s se interrumpió (%2$d elemento(s) pendiente(s)). No se perdió ningún dato; ¿desea continuar? | |
| 45 | `search_virtual_albums` | Search virtual albums | Buscar álbumes virtuales | |
| 46 | `skip_delete_confirmation` | Always skip delete confirmation dialog | Omitir siempre el diálogo de confirmación de eliminación | |
| 47 | `vault_no_recovery_warning` | Offline encryption is used. If you forget your password or PIN, there is no way to recover your vault. | Se utiliza cifrado sin conexión. Si olvida su contraseña o PIN, no hay forma de recuperar su bóveda. | |
| 48 | `vault_not_created_prompt` | You haven\'t created a vault yet. Do you want to create a vault? | Aún no ha creado una bóveda. ¿Desea crear una bóveda? | |
| 49 | `waiting_for_free_space_or_storage` | Waiting for free space or destination storage… | Esperando espacio libre o almacenamiento de destino… | |

## Vault setup tutorial & PIN-dot preview notice (this revision)

Added the six strings introduced by the vault setup-tutorial and PIN-dot live-preview design updates (`dba5068f0`, `4ea5b1f8f`) to `values-es/strings.xml`. English source: `values/strings.xml`. The `&lt;b&gt;…&lt;/b&gt;` emphasis in `vault_pull_tutorial_message` and the literal `10 %` in `pin_dots_notice_note` are preserved exactly.

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `vault_pull_tutorial_title` | Your encrypted vault is ready | Su bóveda cifrada está lista | One-time tutorial shown after vault creation; formal register |
| 2 | `vault_pull_tutorial_message` | While at the top of the list on the main screen, &lt;b&gt;pull down from the top&lt;/b&gt; and hold until the lock icon settles, then release. | Mientras esté en la parte superior de la lista en la pantalla principal, &lt;b&gt;deslice hacia abajo desde la parte superior&lt;/b&gt; y mantenga presionado hasta que el icono de candado se estabilice; luego suelte. | `&lt;b&gt;` emphasis preserved |
| 3 | `vault_pull_tutorial_note` | For your privacy, the vault never appears as a separate folder or icon on the home screen; it only opens with this gesture. | Por su privacidad, la bóveda nunca aparece como una carpeta o un icono independiente en la pantalla de inicio; solo se abre con este gesto. | Privacy note |
| 4 | `vault_pull_tutorial_confirm` | Got it | Entendido | Positive button |
| 5 | `pin_dots_notice_title` | Alternative PIN appearance | Apariencia alternativa del PIN | One-time preview dialog before enabling PIN dots |
| 6 | `pin_dots_notice_note` | Anyone seeing your screen can count the empty rings to tell your PIN length. This reduces the number of passwords to try by about 10%. If you find having your PIN length known acceptable from a security standpoint, you can use this view. | Cualquier persona que vea su pantalla puede contar los círculos vacíos para conocer la longitud de su PIN. Esto reduce aproximadamente en un 10 % el número de contraseñas que hay que probar. Si considera aceptable desde el punto de vista de la seguridad que se conozca la longitud de su PIN, puede usar esta vista. | `10 %` kept with Spanish spacing |

## Filename toggle, album search hint & extra viewer actions (this revision)

Added the five strings introduced by the dynamic filename toggle, album search hint, and extra fullscreen viewer actions updates (`e79197d7f`) to `values-es/strings.xml`. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `show_filenames` | Show filenames | Mostrar nombres de archivo | Media-view overflow toggle label when filenames are hidden |
| 2 | `hide_filenames` | Hide filenames | Ocultar nombres de archivo | Media-view overflow toggle label when filenames are visible |
| 3 | `search_in_album` | Search in album | Buscar en el álbum | Album-view search hint |
| 4 | `show_extra_viewer_actions` | Show extra actions on fullscreen media | Mostrar acciones adicionales en medios a pantalla completa | Settings switch label |
| 5 | `manage_extra_viewer_actions` | Manage extra actions | Administrar acciones adicionales | Settings row label and extra-actions dialog title |

## Secure sharing — metadata sanitization (this revision)

Added the five strings for the new **Settings → Security → Secure sharing** three-mode setting. A sanitized temporary copy is shared; the original media file is never modified. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `settings_group_secure_sharing` | Secure sharing | Uso compartido seguro | New eyebrow in the Security category |
| 2 | `settings_share_metadata_title` | Clear metadata before sharing | Borrar metadatos antes de compartir | Settings row label and radio dialog title |
| 3 | `share_metadata_off` | Off | Desactivado | No sanitization |
| 4 | `share_metadata_location_only` | Location only | Solo ubicación | Default mode: strips GPS tags |
| 5 | `share_metadata_all` | All metadata | Todos los metadatos | Strips Make/Model/Software/DateTime/XMP too |

## File operation notifications (this revision)

Added the notification user messages introduced by the file-operation notification work (`be45de817`, the same commit that added the secure-sharing strings above) to `values-es/strings.xml`. The blind-test reports had recorded the secure-sharing strings but omitted these notification messages, so they are audited here to keep the Spanish translation coverage complete. English source: `values/strings.xml`. The `…` ellipses are preserved exactly.

| # | Key (`name`) | English (`values/strings.xml`) | Spanish (`values-es/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `notifications_settings` | Notifications | Notificaciones | Settings category label |
| 2 | `settings_group_operation_notifications` | File operation notifications | Notificaciones de operaciones con archivos | Settings subcategory eyebrow |
| 3 | `notifications_enabled` | Show operation progress | Mostrar el progreso de las operaciones | Settings switch label |
| 4 | `notify_copy_move` | Copy and move | Copiar y mover | Per-operation toggle label |
| 5 | `notify_crypto` | Encrypt and decrypt | Cifrar y descifrar | Per-operation toggle label |
| 6 | `notify_delete` | Delete and recycle bin | Eliminar y papelera de reciclaje | Per-operation toggle label |
| 7 | `notify_virtual_album` | Virtual album updates | Actualizaciones de álbumes virtuales | Per-operation toggle label |
| 8 | `notifications_info` | Android requires a persistent notification while a background file operation runs. These settings control whether its detailed progress (file name and percentage) is shown. | Android requiere una notificación persistente mientras se ejecuta una operación de archivos en segundo plano. Estos ajustes controlan si se muestra su progreso detallado (nombre del archivo y porcentaje). | Explanatory text below the settings group |
| 9 | `notification_permission_label` | Notification permission | Permiso de notificaciones | Android notification permission label |
| 10 | `notification_permission_granted` | Granted | Concedido | Permission status / positive action |
| 11 | `notification_permission_request` | Tap to allow | Toque para permitir | Permission request hint; formal register |
| 12 | `file_operations_notifications` | File operations | Operaciones con archivos | Notification channel / section title |
| 13 | `file_operation_copy_move` | Copying / moving files… | Copiando / moviendo archivos… | Progress notification title; `…` preserved |
| 14 | `file_operation_crypto` | Encrypting / decrypting files… | Cifrando / descifrando archivos… | Progress notification title; `…` preserved |
| 15 | `file_operation_delete` | Deleting files… | Eliminando archivos… | Progress notification title; `…` preserved |
| 16 | `file_operation_virtual_album` | Updating albums… | Actualizando álbumes… | Progress notification title; `…` preserved |
| 17 | `file_operation_generic` | File operation in progress… | Operación con archivos en curso… | Fallback progress notification title |
| 18 | `checking_files_before_transfer` | Checking files before transfer… | Comprobando archivos antes de la transferencia… | Pre-transfer verification progress |
| 19 | `checking_files_before_crypto` | Checking files before encryption… | Comprobando archivos antes del cifrado… | Pre-encryption verification progress |
| 20 | `deleting_files_progress` | Deleting files… | Eliminando archivos… | Deletion progress status |

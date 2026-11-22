# Norwegian Bokmål (values-nb-rNO) Translation Completion & English 1-to-1 Comparison

## New fork strings - bottom actions, view switch, biometrics and vault UI (16 keys)

Added to `values-nb-rNO/strings.xml`. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `show_bottom_action_names` | Show action names in the bottom action bar | Vis navn på handlingene i handlingslinjen nederst | Fullscreen media setting, default ON |
| 2 | `show_albums` | Show albums | Vis album | Files-view overflow item that returns to the album view |
| 3 | `vault_biometric_offer_title` | Biometric access to the vault | Biometrisk tilgang til hvelvet | One-time offer after first vault creation |
| 4 | `vault_biometric_offer_message` | Open the vault with your fingerprint or face instead of a password. You don\'t need to enter your password again. | Åpne hvelvet med fingeravtrykk eller ansikt i stedet for et passord. Du trenger ikke å skrive inn passordet på nytt. | Same |
| 5 | `vault_biometric_offer_accept` | Set up biometric access | Sett opp biometrisk tilgang | Positive button |
| 6 | `vault_biometric_offer_decline` | Not now | Ikke nå | Negative button |
| 7 | `password_strength_bad` | Very weak | Svært svak | Vault password strength |
| 8 | `password_strength_poor` | Weak | Svak | Vault password strength |
| 9 | `password_strength_weak` | Fair | Middels | Vault password strength |
| 10 | `password_strength_good` | Strong | Sterk | Vault password strength |
| 11 | `password_strength_excellent` | Very strong | Svært sterk | Vault password strength |
| 12 | `password_strength_hint` | A longer password with mixed characters is harder to crack. | Et langt passord med varierte tegn er vanskeligere å knekke. | Shown before typing |
| 13 | `vault_unlock_time_unit` | s | s | Localized seconds unit |
| 14 | `search_in_storage` | Search in storage | Søk i lagring | Second option in unencrypted copy destination picker |
| 15 | `password_must_be_at_least_4_characters` | Password must be at least 4 characters long | Passordet må være minst 4 tegn | Alphanumeric minimum length |
| 16 | `biometric_disable_confirmation` | Confirm your biometric identity to disable biometric vault access | Bekreft din biometriske identitet for å deaktivere biometrisk tilgang til hvelvet | Shown in biometric confirmation prompt |

## ADR 0003 — resumable file operations (7 new keys)

Added to `values-nb-rNO/strings.xml`. Placeholders (`%1$s`, `%2$s`, `%1$d`) are preserved exactly.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `space_unverified_warning` | Free space could not be verified (%2$s). About %1$s is required; the operation will be attempted and may stop if space runs out. | Kunne ikke bekrefte ledig lagringsplass (%2$s). Omtrent %1$s trengs; operasjonen forsøkes, men kan stoppe hvis plassen tar slutt. | SAF/OTG free-space warning; `%1$s` = required size, `%2$s` = reason |
| 2 | `resume_operations_title` | Interrupted operations | Avbrutte operasjoner | One-time process-death dialog title |
| 3 | `resume_operations_message` | %1$d operation(s) were interrupted. No data was lost — continue where they stopped? | %1$d operasjon(er) ble avbrutt. Ingen data gikk tapt – fortsett der de stoppet? | `%1$d` = interrupted operation count |
| 4 | `resume_continue` | Continue | Fortsett | Positive dialog button |
| 5 | `resume_later` | Not now | Ikke nå | Negative dialog button |
| 6 | `resume_done` | Operations completed. | Operasjonene er fullført. | Success toast after resume |
| 7 | `resume_failed` | Some operations could not be completed; source files were preserved. | Noen operasjoner kunne ikke fullføres; kildefilene ble beholdt. | Failure toast; no data loss |
| 8 | `space_unverified_title` | Free space unverified | Lagringsplass ikke bekreftet | Explicit-consent dialog title for SAF/OTG free space |
| 9 | `space_continue_anyway` | Continue anyway | Fortsett likevel | Positive button: proceed without a verified space reading |

> **Scope:** `app/src/main/res/values-nb-rNO/strings.xml` was audited against `app/src/main/res/values/strings.xml`. Three existing errors/typos were corrected, two multiline placeholders were normalized, and all 91 missing strings were translated into Norwegian Bokmål and added, bringing `values-nb-rNO` to full parity (363 `<string>` + 2 `<plurals>` = 365 app resource entries; `search` and 17 previously overridden shared UI keys from `fossify-commons` plus 4 additional `fossify-commons` UI keys (`exclude_folder`, `column_count`, `open_the_recycle_bin`, `version_placeholder`) newly overridden because they are missing in `fossify-commons` `values-nb-rNO`; 364 total comparison rows with plural forms expanded). Nine keys that were missing from `values-nb-rNO/strings.xml` were translated and added (`number_of_items`, `random`, `shuffle`, `stop_showing_excluded`, `copy_to_clipboard`, `encrypt`, `decrypt`, `encrypt_album`, `decrypt_album`), and eleven existing entries were revised so that files and albums use the new \"encrypt / decrypt\" wording and so that the favorites import/export labels mention virtual albums as well. Subsequently, 22 additional keys were localized in `values-nb-rNO/strings.xml`, `hide_folder_description` was removed, and `default_hidden_album` was marked `translatable="false"` (`Default Secret Album`), bringing `values-nb-rNO/strings.xml` to 384 `<string>` + 2 `<plurals>` = 386 resource entries and 385 total comparison rows.
> **Key Terminology:** `Settings` → `Innstillinger` · `Default` → `Standard` / `Systemstandard` · `Folder` → `Mappe` · `Album` → `Album` · `Encrypted Vault` → `Kryptert hvelv` · `Hidden` → `Skjult` · `Excluded` → `Ekskludert` · `Password` → `Passord` · `Alphanumeric` → `Alfanumerisk` · `Encrypt / Decrypt` → `Krypter / Dekrypter` · `Thumbnails` → `Minibilder` · `Recycle bin` → `Papirkurv` · `Clipboard` → `Utklippstavlen`

## 1. Summary of Revisions & Newly Added Norwegian Bokmål Strings & Translations

### 1.1 Revisions to Previously Existing Translations

| Key (`name`) | Old `values-nb-rNO` | New `values-nb-rNO` | Status / Notes |
|---|---|---|---|
| `no_media_with_filters` | Ingen media-filer er funnet med de valgte filtrene. | Ingen mediafiler er funnet med de valgte filtrene. | **Improved** (Removed stray hyphen to match `mediafiler` in `no_media_add_included`) |
| `change_filters_underlined` | `<u>Endre filtere</u>` | `<u>Endre filtre</u>` | **Fixed** (Correct Bokmål plural of *filter* is *filtre*, not *filtere*) |
| `invalid_values` | Oppfør en gyldig oppløsning | Vennligst oppgi en gyldig oppløsning | **Fixed** (*oppfør* means behave/list; *oppgi* means enter) |
| `included_activity_placeholder` | *(literal newlines around `\n`)* | `...\n\n...` | **Normalized** (Removed stray spaces caused by literal XML newlines) |
| `excluded_activity_placeholder` | *(literal newlines around `\n`)* | `...\n\n...` | **Normalized** (Removed stray spaces caused by literal XML newlines) |

### 1.2 Newly Added Fork-Only Strings (66 Keys)

These 66 strings were introduced by this fork (Calypso Gallery) and do not exist in the original Fossify Gallery:

| 10 | 1 | `video_double_tap_duration` | Double-tap seek duration | Video settings: double-tap forward/rewind seek interval |
| 11 | 2 | `video_seek_duration_seconds` | %d seconds | Used with durations 3, 5, 10, 15 via String.format |
| 12 | 3 | `create_virtual_album` | Create virtual album | Virtual albums section action |
| 13 | 4 | `new_virtual_album` | New album… | Action label for creating a new virtual album |
| 14 | 5 | `rename_virtual_album` | Rename album | Action label to rename a virtual album |
| 15 | 6 | `album_name` | Album name | Input hint / label for album name |
| 16 | 7 | `virtual_folder_name` | Virtual album name | Input hint / label for virtual album |
| 17 | 8 | `virtual_album_name_empty` | The album name cannot be empty | Validation error when album name is empty |
| 18 | 9 | `virtual_album_exists` | An album with this name already exists | Validation error when album name already exists |
| 19 | 10 | `add_to_virtual_album` | Add to virtual album | Menu action to add items to a virtual album |
| 20 | 11 | `remove_from_virtual_album` | Remove from virtual album | Menu action to remove items from a virtual album |
| 21 | 12 | `added_to_virtual_album` | Added to \"%s\" | Toast message preserving \"%s\" |
| 22 | 13 | `already_in_virtual_album` | Already in \"%s\" | Toast message preserving \"%s\" |
| 23 | 14 | `removed_from_virtual_album` | Removed from the album | Toast message after removing items from an album |
| 24 | 15 | `delete_virtual_album_confirmation` | Delete the album %s? Only the album is removed, your photos and videos stay where they are. | No extra quotes around %s (formatted with album name or count) |
| 25 | 16 | `virtual_album_empty` | This album is empty. Select items in any folder and use \"Add to virtual album\". | Empty state placeholder referencing "Legg til i virtuelt album" (Add to virtual album) |
| 26 | 17 | `virtual_album_cannot_be_modified` | This action is not available for virtual albums | Toast message when action is unsupported on virtual albums |
| 27 | 18 | `alphanumeric_password` | Alphanumeric | Alphanumeric input label |
| 28 | 19 | `repeat_password` | Repeat password | Password confirmation prompt |
| 29 | 20 | `wrong_password` | Wrong password | Password validation error |
| 30 | 21 | `encrypting_files` | Encrypting files… | Progress message |
| 31 | 22 | `encrypting_folder` | Encrypting folder… | Progress message |
| 32 | 23 | `folder_encrypted_successfully` | Folder encrypted successfully | Success message |
| 33 | 24 | `decrypting_folder` | Decrypting folder… | Progress message |
| 34 | 25 | `folder_decrypted_successfully` | Folder decrypted successfully | Success message |
| 35 | 26 | `decrypting_files` | Decrypting files… | Progress message |
| 36 | 27 | `place_finger_prompt` | Please place your finger on the fingerprint sensor | Fingerprint authentication prompt |
| 37 | 28 | `connect_at_least_4_dots_prompt` | Connect at least 4 dots | Pattern lock validation prompt |
| 38 | 29 | `access_existing_secrets_prompt` | Do you want to access your existing Secrets folder? | Keeps literal folder name Secrets |
| 39 | 30 | `delete_and_recreate_secrets_prompt` | Existing Secrets folder will be deleted and a new one will be created. Are you sure? | Keeps literal folder name Secrets |
| 40 | 31 | `confirm_checkbox_label` | I confirm | Checkbox label, suffixed with countdown (10) in dialog |
| 41 | 32 | `secret_vault` | Encrypted Vault | Vault feature title ("Kryptert hvelv") |
| 42 | 33 | `secret_vault_empty` | Encrypted Vault is empty | Empty state placeholder for Encrypted Vault |
| - | - | `press_back_again_to_exit_vault` | Press back again to exit | Toast shown on first back press in hidden vault |
| - | - | `block_vault_screenshots` | Block screenshots in encrypted vault | Settings switch (default ON) blocking screenshots in encrypted vault |
| 43 | 34 | `confirm_decrypt_hidden_file` | Are you sure you want to decrypt the hidden file(s)? | Confirmation prompt for singular/plural file(s) |
| 44 | 35 | `copy_to_clipboard_disabled_in_vault` | Copying to clipboard is disabled while vault is unlocked for security. | Security notice while vault is unlocked |
| 45 | 63 | `open_system_language_settings` | Open system language settings | Fork-only addition: opens the device language picker (not in the original Fossify Gallery) |
| 46 | 44 | `file_encrypted_successfully` | File encrypted successfully | Filen ble kryptert                           |
| 47 | 45 | `files_encrypted_successfully` | Files encrypted successfully | Filene ble kryptert                           |
| 48 | 46 | `file_decrypted_successfully` | File decrypted successfully | Filen ble dekryptert                         |
| 49 | 47 | `files_decrypted_successfully` | Files decrypted successfully | Filene ble dekryptert                         |
| 50 | 48 | `access_hidden_items_with_biometrics` | Access hidden items with biometrics | Settings switch, setup prompt title, and vault security dialog button |
| 51 | 49 | `enable_hidden_protection_first_prompt` | Please enable hidden item password protection first | Toast shown when enabling biometrics before hidden-item password protection |
| 52 | 50 | `biometric_unlock_vault` | Unlock encrypted vault | BiometricPrompt title when unlocking the encrypted vault |
| 53 | 51 | `biometric_unlock_prompt` | Verify your biometric identity to access hidden items | BiometricPrompt subtitle when unlocking hidden items |
| 54 | 52 | `biometric_setup_prompt` | Confirm your biometric identity to enable biometric vault access | BiometricPrompt subtitle when enabling biometric vault access |
| 55 | 53 | `use_password_or_pin` | Use PIN / Password | BiometricPrompt negative button to fall back to PIN/password |
| 56 | 54 | `biometric_key_invalidated` | Biometric credentials have changed. Please enter your PIN or password. | Toast shown when biometric credentials changed and Keystore key invalidated |
| 57 | 55 | `no_biometric_hardware_or_enrolled` | No biometric hardware or enrolled credentials found on this device | Toast shown when no biometric hardware or enrolled credentials exist |
| 58 | 56 | `default_hidden_album` | Default Hidden Album | Settings / picker label for default hidden album |
| 59 | 57 | `select_hidden_album` | Select Encrypted Album | Velg kryptert album                                  |
| 60 | 58 | `create_new_hidden_album` | Create New Encrypted Album | Opprett nytt kryptert album                  |
| 61 | 59 | `new_hidden_album` | New encrypted album | Nytt kryptert album                                  |
| 62 | 60 | `hidden_album_name_empty` | Album name cannot be empty | Validation error when hidden album name is blank |
| 63 | 61 | `file_hidden_to_album` | File encrypted to \"%s\" | Fil kryptert i «%s»                                          |
| 64 | 62 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d filer kryptert i «%2$s»                                  |
| 65 | `pull_down_to_open_vault` | Keep pulling down for Encrypted Vault | Fortsett å dra ned for kryptert hvelv | Vault pull gesture hint shown while the pull is below the open threshold |
| 66 | `keep_holding_to_open_vault` | Keep holding to open Encrypted Vault | Fortsett å holde for å åpne kryptert hvelv | Vault pull gesture hint shown once the pull passes the threshold, before the 2.5 s hold completes |
| 67 | `release_to_open_vault` | Release to open Encrypted Vault | Slipp for å åpne kryptert hvelv | Vault pull gesture hint shown after holding the pull for 2.5 s; releasing opens the vault |

### 1.3 Newly Added Translations for Strings Already Present in the Original Fossify Gallery (45 Keys)

These 45 strings already exist in the original Fossify Gallery (upstream); only the translations below were newly added in this fork:

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | `cant_unhide_folder` | If a folder or one of its parent folders has a dot before its name, it is hidden and cannot be unhidden like this. You have to remove the dot by renaming it. | Hvis en mappe eller en av dens overordnede mapper har et punktum foran navnet, er den skjult og kan ikke vises igjen på denne måten. Du må fjerne punktumet ved å gi den nytt navn. | Explanation of dot-prefixed hidden folders |
| 2 | `fade` | Fade | Uttoning | Slideshow transition animation option |
| 3 | `slide` | Slide | Glidning | Slideshow transition animation option |
| 4 | `render_ultra_hdr_photos` | Show Ultra HDR photos in HDR | Vis Ultra HDR-bilder i HDR | Media settings: render Ultra HDR photos in HDR |
| 5 | `media_management_manual` | Something went wrong, please go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | Noe gikk galt. Gå til enhetens Innstillinger – Apper – Spesiell apptilgang – Apper for mediaadministrasjon, og tillat at denne appen administrerer media. | Error fallback directing user to Android Special app access settings |
| 6 | `media_management_note` | If the redirection does not work, please go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | Hvis omdirigeringen ikke fungerer, gå til enhetens Innstillinger – Apper – Spesiell apptilgang – Apper for mediaadministrasjon, og tillat at denne appen administrerer media. | Redirect fallback note for Media management apps permission |
| 7 | `media_management_alternative` | If you do not want to do it, you can also go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | Hvis du ikke vil gjøre det, kan du også gå til enhetens Innstillinger – Apper – Spesiell apptilgang – Apper for mediaadministrasjon, og tillat at denne appen administrerer media. | Alternative instructions for Media management apps permission |
| 8 | `failed_to_load_media` | Failed to load media. | Kunne ikke laste inn media. | Media viewer error message |
| 9 | `file_is_malformed_or_corrupted` | File is malformed or corrupted. | Filen er ugyldig eller ødelagt. | Media player/viewer error message |
| 10 | `media_exceeds_device_capabilities` | Media exceeds device capabilities. | Mediet overstiger enhetens kapasitet. | Media player/viewer error message |
| 11 | `faq_1_title` | How can I make Calypso Gallery the default device gallery? | Hvordan kan jeg gjøre Fossify Gallery til enhetens standardgalleri? | FAQ 1 question |
| 12 | `faq_1_text` | First you have to find the currently default gallery in the Apps section of your device settings, look for a button that says something like \"Open by default\", click on it, then select \"Clear defaults\".         The next time you will try opening an image or video you should see an app picker, where you can select Fossify Gallery and make it the default app. | Først må du finne det nåværende standardgalleriet i Apper-delen av enhetsinnstillingene, se etter en knapp som heter noe slikt som \"Åpne som standard\", trykke på den og deretter velge \"Fjern standardvalg\".\nNeste gang du prøver å åpne et bilde eller en video, skal du se en appvelger der du kan velge Fossify Gallery og gjøre den til standardapp. | FAQ 1 answer; matches standard Norwegian Android settings labels |
| 13 | `faq_2_title` | I locked the app with a password, but I forgot it. What can I do? | Jeg låste appen med et passord, men jeg har glemt det. Hva kan jeg gjøre? | FAQ 2 question |
| 14 | `faq_2_text` | You can solve it in 2 ways. You can either reinstall the app, or find the app in your device settings and select \"Clear data\". It will reset all your settings, it will not remove any media files. | Du kan løse det på to måter. Du kan enten installere appen på nytt, eller finne appen i enhetsinnstillingene og velge \"Slett data\". Det vil tilbakestille alle innstillingene dine, men det vil ikke fjerne noen mediafiler. | FAQ 2 answer |
| 15 | `faq_3_title` | How can I make an album always appear at the top? | Hvordan kan jeg få et album til å alltid vises øverst? | FAQ 3 question |
| 16 | `faq_3_text` | You can long press the desired album and select the Pin icon at the actions menu, that will pin it to the top. You can pin multiple folders too, pinned items will be sorted by the default sorting method. | Du kan trykke og holde på ønsket album og velge Fest-ikonet i handlingsmenyen for å feste det til toppen. Du kan også feste flere mapper; festede elementer blir sortert etter standard sorteringsmetode. | FAQ 3 answer; matches "Fest" terminology |
| 17 | `faq_4_title` | How can I fast forward videos? | Hvordan kan jeg spole fremover i videoer? | FAQ 4 question |
| 18 | `faq_4_text` | You can do it by double tapping the side of the screen, or tapping the current or max duration texts near the seekbar. If you enable opening videos on a separate screen in the app settings, you can use horizontal gestures too. | Du kan gjøre det ved å dobbelttrykke på siden av skjermen, eller trykke på teksten for nåværende eller maksimal varighet nær søkelinjen. Hvis du aktiverer avspilling av videoer på en separat skjerm i appinnstillingene, kan du også bruke horisontale bevegelser. | FAQ 4 answer |
| 19 | `faq_5_title` | What is the difference between hiding and excluding a folder? | Hva er forskjellen mellom å skjule og å ekskludere en mappe? | FAQ 5 question |
| 20 | `faq_5_text` | Exclude prevents displaying the folder only in Calypso Gallery, while Hide works system-wise and it hides the folder from other galleries too. It works by creating an empty \".nomedia\" file in the given folder, which you can then remove with any file manager too. Note that some devices do not allow hiding folders like Camera, Screenshots and Downloads. | Ekskluder hindrer at mappen vises kun i Fossify Gallery, mens Skjul fungerer på systemnivå og skjuler mappen også for andre gallerier. Det fungerer ved å opprette en tom \".nomedia\"-fil i den aktuelle mappen, som du også kan fjerne med en hvilken som helst filbehandler. Merk at enkelte enheter ikke tillater å skjule mapper som Camera, Screenshots og Downloads. | FAQ 5 answer; keeps folder names Camera, Screenshots, Downloads literal |
| 21 | `faq_6_title` | Why do folders with music cover art or stickers show up? | Hvorfor vises mapper med plateomslag eller klistremerker? | FAQ 6 question |
| 22 | `faq_6_text` | It can happen that you will see some unusual albums show up. You can easily exclude them by long pressing them and selecting Exclude. In the next dialog you can then select the parent folder, chances are it will prevent the other related albums showing up too. | Det kan skje at uvanlige album dukker opp. Du kan enkelt ekskludere dem ved å trykke og holde på dem og velge Ekskluder. I den neste dialogen kan du så velge den overordnede mappen, noe som sannsynligvis også vil hindre at andre relaterte album vises. | FAQ 6 answer |
| 23 | `faq_7_title` | A folder with images isn\'t showing up, or it doesn\'t show all items.  What can I do? | En mappe med bilder vises ikke, eller den viser ikke alle elementer. Hva kan jeg gjøre? | FAQ 7 question |
| 24 | `faq_7_text` | That can have multiple reasons, but solving it is easy. Just go in Settings -&gt; Manage Included Folders, select Plus and navigate to the required folder. | Det kan ha flere årsaker, men det er enkelt å løse. Bare gå til Innstillinger -&gt; Håndter inkluderte mapper, velg Pluss og naviger til ønsket mappe. | FAQ 7 answer; matches "Håndter inkluderte mapper" |
| 25 | `faq_8_title` | What if I want just a few particular folders visible? | Hva om jeg bare vil at noen bestemte mapper skal være synlige? | FAQ 8 question |
| 26 | `faq_8_text` | Adding a folder at the Included Folders doesn\'t automatically exclude anything. What you can do is go in Settings -&gt; Manage Excluded Folders, exclude the root folder \"/\", then add the desired folders at Settings -&gt; Manage Included Folders.         That will make only the selected folders visible, as both excluding and including are recursive and if a folder is both excluded and included, it will show up. | Å legge til en mappe under Inkluderte mapper ekskluderer ikke automatisk noe annet. Det du kan gjøre er å gå til Innstillinger -&gt; Håndter ekskluderte mapper, ekskludere rotmappen \"/\", og deretter legge til ønskede mapper under Innstillinger -&gt; Håndter inkluderte mapper.\nDet vil gjøre bare de valgte mappene synlige, siden både ekskludering og inkludering er rekursive, og hvis en mappe både er ekskludert og inkludert, vil den vises. | FAQ 8 answer; matches "Håndter ekskluderte/inkluderte mapper" |
| 27 | `faq_10_title` | Can I crop images with this app? | Kan jeg beskjære bilder med denne appen? | FAQ 10 question |
| 28 | `faq_10_text` | Yes, you can crop images in the editor, by dragging the image corners. You can get to the editor either by long pressing an image thumbnail and selecting Edit, or selecting Edit from the fullscreen view. | Ja, du kan beskjære bilder i redigeringsverktøyet ved å dra i bildehjørnene. Du kommer til redigeringsverktøyet enten ved å trykke og holde på et minibilde og velge Rediger, eller ved å velge Rediger fra fullskjermvisningen. | FAQ 10 answer |
| 29 | `faq_11_title` | Can I somehow group media file thumbnails? | Kan jeg på noen måte gruppere minibilder av mediafiler? | FAQ 11 question |
| 30 | `faq_11_text` | Sure, just use the \"Group by\" menu item while at the thumbnails view. You can group files by multiple criteria, including Date Taken. If you use the \"Show all folders content\" function you can group them by folders too. | Selvfølgelig, bare bruk menyelementet \"Grupper etter\" når du er i minibildevisningen. Du kan gruppere filer etter flere kriterier, inkludert Dato tatt. Hvis du bruker funksjonen \"Vis alle mappers innhold\", kan du også gruppere dem etter mapper. | FAQ 11 answer; matches "Grupper etter", "Dato tatt", "Vis alle mappers innhold" |
| 31 | `faq_12_title` | Sorting by Date Taken doesn\'t seem to work properly, how can I fix it? | Sortering etter Dato tatt ser ikke ut til å fungere skikkelig, hvordan kan jeg fikse det? | FAQ 12 question |
| 32 | `faq_12_text` | It is most likely caused by the files being copied from somewhere. You can fix it by selecting the file thumbnails and selecting \"Fix Date Taken value\". | Det skyldes sannsynligvis at filene er kopiert fra et annet sted. Du kan fikse det ved å merke minibildene og velge \"Korriger Dato tatt-verdi\". | FAQ 12 answer; matches "Korriger Dato tatt-verdi" and "minibilder" |
| 33 | `faq_13_title` | I see some color banding on the images. How can I improve the quality? | Jeg ser fargebånd på bildene. Hvordan kan jeg forbedre kvaliteten? | FAQ 13 question |
| 34 | `faq_13_text` | The current solution for displaying images works fine in the vast majority of cases, but if you want even better image quality, you can enable the \"Show images in the highest possible quality\" at the app settings, in the \"Deep zoomable images\" section. | Den nåværende løsningen for bildevisning fungerer fint i de aller fleste tilfeller, men hvis du ønsker enda bedre bildekvalitet, kan du aktivere \"Vis bilder i høyest mulig kvalitet\" i appinnstillingene under delen \"Dyp zoombare bilder\". | FAQ 13 answer; matches "Vis bilder i høyest mulig kvalitet" and "Dyp zoombare bilder" |
| 35 | `faq_14_title` | I have hidden a file/folder. How can I unhide it? | Jeg har skjult en fil/mappe. Hvordan kan jeg gjøre den synlig igjen? | FAQ 14 question |
| 36 | `faq_14_text` | You can either press the \"Temporarily show hidden items\" menu item at the main screen, or toggle \"Show hidden items\" in the app settings to see the hidden item. If you want to unhide it, just long press it and select \"Unhide\". Folders are hidden by adding a hidden \".nomedia\" file into them, you can delete the file with any file manager too. Note that hiding works recursively though, so if you hide a folder, all subfolders will become hidden too. So for unhiding the subfolders you have to unhide the parent folder. | Du kan enten trykke på menyelementet \"Vis skjulte elementer midlertidig\" på hovedskjermen, eller slå på \"Vis skjulte elementer\" i appinnstillingene for å se det skjulte elementet. Hvis du vil gjøre det synlig igjen, trykker og holder du bare på det og velger \"Vis igjen\". Mapper skjules ved å legge til en skjult \".nomedia\"-fil i dem; du kan også slette denne filen med en hvilken som helst filbehandler. Merk imidlertid at skjuling fungerer rekursivt, så hvis du skjuler en mappe, blir alle undermapper også skjult. For å gjøre undermappene synlige igjen må du derfor gjøre den overordnede mappen synlig igjen. | FAQ 14 answer |
| 37 | `faq_15_title` | Why does the app take up so much space? | Hvorfor tar appen opp så mye plass? | FAQ 15 question |
| 38 | `faq_15_text` | App cache can take up to 250MB, it ensures quicker image loading. If the app is taking up even more space, it is most likely caused by you having items in the recycle bin. Those files count to the app size. You can clear the recycle bin by opening it and deleting all files, or from the app settings. Every file in the bin is deleted automatically after 30 days. | Appens hurtigbuffer kan ta opptil 250 MB; den sørger for raskere innlasting av bilder. Hvis appen tar opp enda mer plass, skyldes det sannsynligvis at du har elementer i papirkurven. Disse filene teller med i appstørrelsen. Du kan tømme papirkurven ved å åpne den og slette alle filer, eller fra appinnstillingene. Hver fil i papirkurven slettes automatisk etter 30 dager. | FAQ 15 answer; matches "papirkurven" |
| 39 | `faq_16_title` | What happened to file and folder hiding and why cannot I see hidden items anymore? | Hva skjedde med skjuling av filer og mapper, og hvorfor kan jeg ikke se skjulte elementer lenger? | FAQ 16 question (faq_16_text was already present) |
| 40 | `faq_16_text_extra` | Or you can also grant this gallery access to All Files through your device settings, that will allow us showing hidden items and make file operations more reliable in general. | Eller du kan også gi dette galleriet tilgang til Alle filer via enhetsinnstillingene; det lar oss vise skjulte elementer og gjør filoperasjoner mer pålitelige generelt. | FAQ 16 follow-up regarding All Files access |
| 41 | `faq_17_title` | Why cannot I include missing folders anymore? | Hvorfor kan jeg ikke inkludere manglende mapper lenger? | FAQ 17 question |
| 42 | `faq_17_text` | That stopped working due to the system changes that came with Android 11 too, the app cannot browse real folders anymore, it relies on the so called MediaStore at fetching data. | Det sluttet også å fungere på grunn av systemendringene som kom med Android 11; appen kan ikke lenger bla gjennom ekte mapper, men er avhengig av såkalte MediaStore for å hente data. | FAQ 17 answer; keeps MediaStore literal |
| 43 | `faq_18_title` | Why do I see ads during video playback? | Hvorfor ser jeg reklame under videoavspilling? | FAQ 18 question |
| 44 | `faq_18_text` | Our apps have no ads whatsoever. If you see them during video playback, you must be using some other apps video player. Try finding your default video player in the device settings, then do a \"Clear defaults\" on it. The next time you invoke some video intent you will see an app picker prompt, where you can select what app you want to use. | Appene våre har ingen reklame overhodet. Hvis du ser reklame under videoavspilling, bruker du sannsynligvis videospilleren til en annen app. Prøv å finne standard videospiller i enhetsinnstillingene, og velg \"Fjern standardvalg\" på den. Neste gang du åpner en video, vil du se en appvelger der du kan velge hvilken app du vil bruke. | FAQ 18 answer |
| 45 | `enter_password` | Enter password | Skriv inn passord | Password prompt |

### 1.4 `fossify-commons` Inherited UI Strings & Local Overrides (21 Keys)

10 of these 21 shared UI keys are inherited from `org.fossify:commons:7.1.3` (`res/values-nb-rNO/values-nb-rNO.xml`), while `contributors`, `known_issues`, `donate_to_fossify`, `privacy_policy`, `skip_the_recycle_bin`, `exclude_folder`, `column_count`, `open_the_recycle_bin`, and `version_placeholder` were missing and added as local overrides in `app/src/main/res/values-nb-rNO/strings.xml`:

| # | Key (`name`) | English (`fossify-commons`) | Norwegian Bokmål | Source / Notes |
|---:|---|---|---|---|
| 1 | `change_date_and_time_format` | Change date and time format | Endre format for dato og klokkeslett | Inherited from `fossify-commons` (Settings) |
| 2 | `password_protect_file_deletion` | Password protect file deletion and moving | Passordbeskytt sletting og flytting av filer | Inherited from `fossify-commons` (Settings) |
| 3 | `keep_last_modified` | Keep old last-modified value at file operations | Behold sist-endret-verdi ved filoperasjoner | Inherited from `fossify-commons` (Settings) |
| 4 | `clear_cache` | Clear cache | Tøm hurtiglager | Inherited from `fossify-commons` (Settings) |
| 5 | `export_settings` | Export settings | Eksporter innstillinger | Inherited from `fossify-commons` (Settings) |
| 6 | `import_settings` | Import settings | Importer innstillinger | Inherited from `fossify-commons` (Settings) |
| 7 | `temporarily_show_excluded` | Temporarily show excluded | Vis ekskluderte midlertidig | Inherited from `fossify-commons` (Options menu) |
| 8 | `change_view_type` | Change view type | Endre visningstype | Inherited from `fossify-commons` (Options menu) |
| 9 | `contributors` | Contributors | Bidragsytere | Local override in `values-nb-rNO/strings.xml` (About) |
| 10 | `known_issues` | Known issues | Kjente problemer | Local override in `values-nb-rNO/strings.xml` (About) |
| 11 | `donate_to_fossify` | Donate to Fossify | Doner til Fossify | Local override in `values-nb-rNO/strings.xml` (About) |
| 12 | `privacy_policy` | Privacy policy | Personvernerklæring | Local override in `values-nb-rNO/strings.xml` (About) |
| 13 | `create_shortcut` | Create shortcut | Opprett snarvei | Inherited from `fossify-commons` (CAB / ViewPager menu) |
| 14 | `print` | Print | Skriv ut | Inherited from `fossify-commons` (ViewPager menu) |
| 15 | `search_files` | Search files | Søk i filer | Local override in `values-nb-rNO/strings.xml` (Search bar hint) |
| 16 | `search_folders` | Search folders | Søk i mapper | Local override in `values-nb-rNO/strings.xml` (Search bar hint) |
| 17 | `skip_the_recycle_bin` | Skip the recycle bin, delete files directly | Hopp over papirkurven og slett filene direkte | Local override in `values-nb-rNO/strings.xml` (Delete confirmation dialog); key missing in `fossify-commons` `values-nb-rNO` |
| 18 | `exclude_folder` | Exclude folder | Ekskluder mappe | Local override in `values-nb-rNO/strings.xml` (Options menu); key missing in `fossify-commons` `values-nb-rNO` |
| 19 | `column_count` | Column count | Antall kolonner | Local override in `values-nb-rNO/strings.xml` (Main screen and in-album Options menu); key missing in `fossify-commons` `values-nb-rNO` |
| 20 | `open_the_recycle_bin` | Open the recycle bin | Åpne papirkurven | Local override in `values-nb-rNO/strings.xml` (In-album Options menu); key missing in `fossify-commons` `values-nb-rNO` |
| 21 | `version_placeholder` | Version %s | Versjon %s | Local override in `values-nb-rNO/strings.xml` (About screen version text); key missing in `fossify-commons` `values-nb-rNO` |

### Newly Added Missing Translations (9 Keys) & Revised Entries (11 Keys)

Nine keys that were missing from `values-nb-rNO/strings.xml` were translated and added, and eleven existing entries were revised so that files and albums use the new \"encrypt / decrypt\" wording and so that the favorites import/export labels mention virtual albums as well.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | `number_of_items` | Number of items | Antall elementer | Sorting option shown in the album view only; overridden locally because it is missing in `fossify-commons` for this locale |
| 2 | `random` | Random | Tilfeldig | Sorting option offered in every sorting state; overridden locally because it is missing in `fossify-commons` for this locale |
| 3 | `shuffle` | Shuffle | Bland | Fullscreen viewer action that randomizes the media order; app owned string that was only available in English |
| 4 | `stop_showing_excluded` | Stop showing excluded | Slutt å vise ekskluderte | Main screen options-menu action that ends the temporary preview of excluded folders |
| 5 | `copy_to_clipboard` | Copy to clipboard | Kopier til utklippstavlen | Fullscreen viewer action that copies a file to the clipboard |
| 6 | `encrypt` | Encrypt | Krypter | Replaces the inherited `hide` label for files; files and albums are encrypted, not hidden |
| 7 | `decrypt` | Decrypt | Dekrypter | Replaces the inherited `unhide` label for files |
| 8 | `encrypt_album` | Encrypt album | Krypter album | Album (folder) context action that encrypts the album into the vault |
| 9 | `decrypt_album` | Decrypt album | Dekrypter album | Album (folder) context action that decrypts the album back to its original location |
| 10 | `export_favorite_paths` | Export favorites and virtual albums | Eksporter favoritter og virtuelle album | Settings label; renamed because virtual albums are exported together with the favorites |
| 11 | `import_favorite_paths` | Import favorites and virtual albums | Importer favoritter og virtuelle album | Settings label; renamed because virtual albums are imported together with the favorites |
| 12 | `file_encrypted_successfully` | File encrypted successfully | Filen ble kryptert | Success toast after encrypting a single file |
| 13 | `files_encrypted_successfully` | Files encrypted successfully | Filene ble kryptert | Success toast after encrypting multiple files |
| 14 | `file_decrypted_successfully` | File decrypted successfully | Filen ble dekryptert | Success toast after decrypting a single file |
| 15 | `files_decrypted_successfully` | Files decrypted successfully | Filene ble dekryptert | Success toast after decrypting multiple files |
| 16 | `file_hidden_to_album` | File encrypted to \"%s\" | Fil kryptert i «%s» | Success toast after encrypting a single file into album `%s` |
| 17 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d filer kryptert i «%2$s» | Success toast after encrypting `%1$d` files into album `%2$s` |
| 18 | `select_hidden_album` | Select Encrypted Album | Velg kryptert album | Dialog title for choosing the target encrypted album |
| 19 | `create_new_hidden_album` | Create New Encrypted Album | Opprett nytt kryptert album | Action label to create a new encrypted album |
| 20 | `new_hidden_album` | New encrypted album | Nytt kryptert album | Input hint / default label for a new encrypted album |

### Newly Added UI, Backup, Encryption Confirmation & Vault Strings (22 Keys)

Following the previous audit, 22 additional keys were localized in `values-nb-rNO/strings.xml` (`create_new_encrypted_virtual_album` and `new_album` for vault destination dialogs, plus 20 strings covering backup export/import, auto-backup prompt, encryption confirmations, delete-behavior options, album lock/unlock labels, and progress notifications; `hide_folder_description` was removed from the app, and `default_hidden_album` was marked `translatable="false"` as `Default Secret Album`):

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|---:|---|---|---|---|
| 1 | `create_new_encrypted_virtual_album` | Create new encrypted virtual album | Opprett nytt kryptert virtuelt album | Dialog option for creating an encrypted virtual album inside the vault |
| 2 | `new_album` | New album | Nytt album | Compact neutral button label in the move destination dialog |
| 3 | `filename_without_zip` | Filename (without .zip) | Filnavn (uten .zip) | Input hint in the export favorites and virtual albums dialog |
| 4 | `favorites_already_exist` | You already have a favorites list | Du har allerede en favorittliste | Dialog title when importing favorites while a favorites list already exists |
| 5 | `delete_existing_list` | Delete existing list | Slett eksisterende liste | Radio option to replace the existing favorites list on import |
| 6 | `merge_lists` | Merge lists | Slå sammen lister | Radio option to merge imported favorites with the existing list |
| 7 | `confirm` | Confirm | Bekreft | Positive button label in the favorites import conflict dialog |
| 8 | `confirm_encrypt_album` | Are you sure you want to encrypt this album? All of its media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | Er du sikker på at du vil kryptere dette albumet? Alle mediefilene i det blir kryptert og flyttet til det krypterte hvelvet. Originalfilene blir slettet. | Confirmation prompt before encrypting a single album into the vault |
| 9 | `confirm_encrypt_albums` | Are you sure you want to encrypt the selected albums? All of their media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | Er du sikker på at du vil kryptere de valgte albumene? Alle mediefilene i dem blir kryptert og flyttet til det krypterte hvelvet. Originalfilene blir slettet. | Confirmation prompt before encrypting multiple selected albums into the vault |
| 10 | `confirm_encrypt_file` | Are you sure you want to encrypt this file? It will be encrypted and moved to the encrypted vault. The original file will be deleted. | Er du sikker på at du vil kryptere denne filen? Den blir kryptert og flyttet til det krypterte hvelvet. Originalfilen blir slettet. | Confirmation prompt before encrypting a single file into the vault |
| 11 | `confirm_encrypt_files` | Are you sure you want to encrypt the selected files? They will be encrypted and moved to the encrypted vault. The original files will be deleted. | Er du sikker på at du vil kryptere de valgte filene? De blir kryptert og flyttet til det krypterte hvelvet. Originalfilene blir slettet. | Confirmation prompt before encrypting multiple selected files into the vault |
| 12 | `confirm_encrypt_files_to_vault` | The selected files will be encrypted when they are copied into the encrypted vault. Do you want to continue? | De valgte filene blir kryptert når de kopieres til det krypterte hvelvet. Vil du fortsette? | Confirmation prompt when copying unencrypted files into an encrypted vault destination |
| 13 | `encrypt_decrypt_notifications` | Encrypt / Decrypt | Krypter / Dekrypter | Notification channel name for ongoing encrypt/decrypt progress notifications |
| 14 | `allow_batch_renaming` | Allow batch renaming when multiple files are selected | Tillat masseendring av navn når flere filer er valgt | Retained in values/strings.xml for parity (its Settings switch was removed in a6a78d444) |
| 15 | `keep_original_remove_from_virtual_album` | Keep the original file, remove it only from this virtual album | Behold originalfilen, fjern den bare fra dette virtuelle albumet | Checkbox in delete confirmation dialog when deleting from a virtual album |
| 16 | `keep_original_remove_from_favorites` | Keep the original file, remove it only from favorites | Behold originalfilen, fjern den bare fra favoritter | Checkbox in delete confirmation dialog when deleting from favorites |
| 17 | `delete_original_from_favorites_and_virtual_albums` | Delete the original version of a file when it is deleted from favorites and virtual albums | Slett originalversjonen av en fil når den slettes fra favoritter og virtuelle album | Settings switch controlling default delete behavior from favorites and virtual albums |
| 18 | `cannot_target_encrypted_album` | Encrypted albums cannot be targeted for this operation | Krypterte album kan ikke velges som mål for denne handlingen | Toast shown when an encrypted album cannot be targeted for an operation |
| 19 | `cannot_mix_encrypted_and_unencrypted` | Cannot combine encrypted and unencrypted items in this operation | Kan ikke kombinere krypterte og ukrypterte elementer i denne handlingen | Toast shown when an operation attempts to combine encrypted and unencrypted items |
| 20 | `lock_album` | Lock album | Lås album | Album selection menu action replacing lock_folder |
| 21 | `unlock_album` | Unlock album | Lås opp album | Album selection menu action replacing unlock_folder |
| 22 | `auto_backup_prompt_message` | Do you want your favorites and virtual albums to be regularly written to system storage outside of app memory? This will ensure that you do not lose your favorites list and virtual albums if you uninstall the app. You can manually create a backup from app settings at any time. | Vil du at favorittene og de virtuelle albumene dine skal lagres regelmessig i systemlagringen utenfor appminnet? Dette sikrer at du ikke mister favorittlisten og de virtuelle albumene dine hvis du avinstallerer appen. Du kan når som helst opprette en sikkerhetskopi manuelt fra appinnstillingene. | Full-screen prompt asking whether to regularly back up favorites and virtual albums to shared storage |
| 23 | `auto_backup_favorites_and_virtual_albums` | Back up favorites and virtual albums to device storage | Sikkerhetskopier favoritter og virtuelle album til enhetslagringen | Settings switch under File operations (shown after first auto-backup prompt) |

## 2. Full 1-to-1 Comparison: English vs. Norwegian Bokmål

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) |
|---:|---|---|---|
| 1 | `app_launcher_name` | Calypso Gallery | Galleri |
| 2 | `edit` | Edit | Rediger |
| 3 | `open_camera` | Open camera | Åpne kamera |
| 4 | `hidden` | (hidden) | (skjult) |
| 5 | `excluded` | (excluded) | (ekskludert) |
| 6 | `pin_folder` | Pin folder | Fest mappe |
| 7 | `unpin_folder` | Unpin folder | Løsne mappe |
| 8 | `pin_to_the_top` | Pin to the top | Fest til toppen |
| 9 | `show_all` | Show all folders content | Vis alle mappers innhold |
| 10 | `all_folders` | All folders | Alle mapper |
| 11 | `folder_view` | Switch to folder view | Skift til mappevisning |
| 12 | `other_folder` | Other folder | Annen mappe |
| 13 | `show_on_map` | Show on map | Vis på kart |
| 14 | `unknown_location` | Unknown location | Ukjent plassering |
| 15 | `volume` | Volume | Lydstyrke |
| 16 | `brightness` | Brightness | Lysstyrke |
| 17 | `lock_orientation` | Lock orientation | Lås skjermorientering |
| 18 | `unlock_orientation` | Unlock orientation | Lås opp skjermorientering |
| 19 | `change_orientation` | Change orientation | Endre orientering |
| 20 | `force_portrait` | Force portrait | Framtving portrett |
| 21 | `force_landscape` | Force landscape | Framtving landskap |
| 22 | `force_landscape_reverse` | Force landscape (reverse) | Framtving landskap (omvendt) |
| 23 | `use_default_orientation` | Use default orientation | Bruk standardorientering |
| 24 | `fix_date_taken` | Fix Date Taken value | Korriger Dato tatt-verdi |
| 25 | `fixing` | Fixing… | Korrigerer… |
| 26 | `dates_fixed_successfully` | Dates fixed successfully | Datoer er korrigerte |
| 27 | `no_date_takens_found` | No Date Taken values have been found | Ingen verdier for Dato tatt er funnet |
| 28 | `share_resized` | Share a resized version | Del versjon med endret størrelse |
| 29 | `switch_to_file_search` | Switch to file search across all visible folders | Skift til filsøk i alle synlige mapper |
| 30 | `set_as_default_folder` | Set as default folder | Sett som standardmappe |
| 31 | `unset_as_default_folder` | Unset as default folder | Ikke lenger sett som standardmappe |
| 32 | `reorder_by_dragging` | Reorder folders by dragging | Endre mapperekkefølge ved å dra |
| 33 | `reorder_by_dragging_pro` | Reorder folders by dragging (Pro) | Endre mapperekkefølge ved å dra (Pro) |
| 34 | `restore_to_path` | Restoring to \'%s\' | Gjenoppretter til \'%s\' |
| 35 | `full_storage_permission_required` | Calypso Gallery needs full access to display all your photos and videos. Go to Settings > Permissions > Photos and videos > Allow all. | Fossify Gallery trenger full tilgang for å vise alle dine bilder og videoer. Gå til Innstillinger &gt; Tillatelser &gt; Bilder og videoer &gt; Tillat alle. |
| 36 | `filter_media` | Filter media | Filtrer media |
| 37 | `images` | Images | Bilder |
| 38 | `videos` | Videos | Videoer |
| 39 | `gifs` | GIFs | GIF-bilder |
| 40 | `raw_images` | RAW images | RAW-format-bilder |
| 41 | `svgs` | SVGs | SVG-bilder |
| 42 | `portraits` | Portraits | Portretter |
| 43 | `no_media_with_filters` | No media files have been found with the selected filters. | Ingen mediafiler er funnet med de valgte filtrene. |
| 44 | `change_filters_underlined` | <u>Change filters</u> | <u>Endre filtre</u> |
| 45 | `exclude_folder_description` | This will exclude the selection together with its subfolders from Calypso Gallery only. You can manage excluded folders in Settings. | Dette vil ekskludere valgte sammen med undermapper, bare fra Fossify Gallery. Ekskluderte mapper kan håndteres i Innstillinger. |
| 46 | `exclude_folder_parent` | Exclude a parent instead? | Ekskludere en overordnet isteden\? |
| 47 | `excluded_activity_placeholder` | Excluding folders will make them together with their subfolders hidden just in Calypso Gallery, they will still be visible in other applications.\n\nIf you want to hide them from other apps too, use the Encrypt function. | Ekskludering av mapper vil gjøre dem sammen med deres undermapper, skjulte bare i Fossify Gallery. De vil fortsatt være synlige i andre apper.\n\nHvis du vil skjule dem fra andre apper også, bruk Skjul-funksjonen. |
| 48 | `hidden_folders` | Hidden folders | Skjulte mapper |
| 49 | `manage_hidden_folders` | Manage hidden folders | Håndter skjulte mapper |
| 50 | `hidden_folders_placeholder` | Seems like you don\'t have any folders hidden with a \".nomedia\" file or dot prefix. | Ser ut til at du ikke har skjult noen mapper med en \".nomedia\"-fil. |
| 51 | `hidden_all_files` | You have to grant the app All Files access to see hidden files, else it cannot work. | Du må gi appen tilgang til alle filer for å se skjulte filer, ellers fungerer det ikke. |
| 52 | `cant_unhide_folder` | If a folder or one of its parent folders has a dot before its name, it is hidden and cannot be unhidden like this. You have to remove the dot by renaming it. | Hvis en mappe eller en av dens overordnede mapper har et punktum foran navnet, er den skjult og kan ikke vises igjen på denne måten. Du må fjerne punktumet ved å gi den nytt navn. |
| 53 | `include_folders` | Included folders | Inkluderte mapper |
| 54 | `manage_included_folders` | Manage included folders | Håndter inkluderte mapper |
| 55 | `add_folder` | Add folder | Legg til mappe |
| 56 | `included_activity_placeholder` | If you have some folders which contain media, but were not recognized by the app, you can add them manually here.\n\nAdding some items here will not exclude any other folder. | Hvis du har noen mapper som inneholder media, men ikke ble gjenkjent av appen, kan du legge dem til manuelt her.\n\nÅ legge til noen elementer her, ekskluderer ikke noen annen mappe. |
| 57 | `no_media_add_included` | No media files have been found. You can solve it by adding the folders containing media files manually. | Ingen mediafiler er funnet. Dette kan løses ved å legge til mapper som inneholder mediafiler manuelt. |
| 58 | `resize_and_save` | Resize selection and save | Endre størrelse og lagre |
| 59 | `width` | Width | Bredde |
| 60 | `height` | Height | Høyde |
| 61 | `keep_aspect_ratio` | Keep aspect ratio | Behold sideforhold |
| 62 | `invalid_values` | Please enter a valid resolution | Vennligst oppgi en gyldig oppløsning |
| 63 | `resize_multiple_images` | Resize multiple images | Endre størrelse på flere bilder |
| 64 | `resize_factor` | Resize factor | Faktor for størrelsesendring |
| 65 | `resize_factor_info` | Resize images to the given percentage, value must be within 10 and 90. | Endre størrelse på bilder til den angitte prosenten, verdi må være mellom 10 og 90. |
| 66 | `resize_factor_error` | Enter a number between 10 and 90 | Angi et tall mellom 10 og 90 |
| 67 | `images_resized_successfully` | Images resized successfully | Størrelsen på bildene endret |
| 68 | `editor` | Editor | Redigering |
| 69 | `basic_editor` | Basic Editor | Enkel redigering |
| 70 | `advanced_editor` | Advanced Editor | Avansert redigering |
| 71 | `rotate` | Rotate | Roter |
| 72 | `invalid_image_path` | Invalid image path | Ugyldig bildebane |
| 73 | `invalid_video_path` | Invalid video path | Ugyldig videobane |
| 74 | `image_editing_failed` | Image editing failed | Bilderedigering feilet |
| 75 | `video_editing_failed` | Video editing failed | Videoredigering feilet |
| 76 | `image_editing_cancelled` | Image editing cancelled | Bilderedigering avbrutt |
| 77 | `video_editing_cancelled` | Video editing cancelled | Videoredigering avbrutt |
| 78 | `file_edited_successfully` | File edited successfully | Fil vellykket redigert |
| 79 | `image_edited_successfully` | Image edited successfully | Bilde vellykket redigert |
| 80 | `video_edited_successfully` | Video edited successfully | Video vellykket redigert |
| 81 | `edit_image_with` | Edit image with: | Rediger bilde med: |
| 82 | `edit_video_with` | Edit video with: | Rediger video med: |
| 83 | `no_image_editor_found` | No image editor found | Ingen bilderedigeringsapp funnet |
| 84 | `no_video_editor_found` | No video editor found | Ingen videoredigeringsapp funnet |
| 85 | `unknown_file_location` | Unknown file location | Ukjent filplassering |
| 86 | `overwrite_original` | Overwrite original | Overskriv original |
| 87 | `error_saving_file` | Could not overwrite the source file | Kunne ikke overskrive kildefilen |
| 88 | `rotate_left` | Rotate left | Roter til venstre |
| 89 | `rotate_right` | Rotate right | Roter til høyre |
| 90 | `rotate_one_eighty` | Rotate by 180º | Roter med 180º |
| 91 | `transform` | Transform | Transformer |
| 92 | `crop` | Crop | Beskjær |
| 93 | `draw` | Draw | Tegn |
| 94 | `flip` | Flip | Speilvend |
| 95 | `flip_horizontally` | Flip horizontally | Speilvend horisontalt |
| 96 | `flip_vertically` | Flip vertically | Speilvend vertikalt |
| 97 | `free_aspect_ratio` | Free | Fri |
| 98 | `other_aspect_ratio` | Other | Annen |
| 99 | `wallpaper` | Wallpaper | Bakgrunnsbilde |
| 100 | `set_as_wallpaper` | Set as Wallpaper | Sett som bakgrunnsbilde |
| 101 | `set_as_wallpaper_failed` | Setting as Wallpaper failed | Å sette som bakgrunnsbilde feilet |
| 102 | `set_as_wallpaper_with` | Set as wallpaper with: | Sett som bakgrunnsbilde med: |
| 103 | `setting_wallpaper` | Setting wallpaper… | Stiller inn bakgrunnsbilde… |
| 104 | `wallpaper_set_successfully` | Wallpaper set successfully | Bakgrunnsbilde vellykket stilt inn |
| 105 | `portrait_aspect_ratio` | Portrait aspect ratio | Portrett-sideforhold |
| 106 | `landscape_aspect_ratio` | Landscape aspect ratio | Landskap-sideforhold |
| 107 | `home_screen` | Home screen | Startskjerm |
| 108 | `lock_screen` | Lock screen | Låseskjerm |
| 109 | `home_and_lock_screen` | Home and lock screen | Start- og låseskjerm |
| 110 | `allow_changing_aspect_ratio` | Allow changing the aspect ratio | Tillat endring av sideforholdet |
| 111 | `slideshow` | Slideshow | Lysbildeshow |
| 112 | `interval` | Interval | Intervall |
| 113 | `include_photos` | Include photos | Inkluder bilder |
| 114 | `include_videos` | Include videos | Inkluder videoer |
| 115 | `include_gifs` | Include GIFs | Inkluder GIF-bilder |
| 116 | `random_order` | Random order | Tilfeldig rekkefølge |
| 117 | `move_backwards` | Move backwards | Avspill bakover |
| 118 | `loop_slideshow` | Loop slideshow | Gjenta lysbildeshow |
| 119 | `animation` | Animation | Animasjon |
| 120 | `no_animation` | None | Ingen |
| 121 | `fade` | Fade | Uttoning |
| 122 | `slide` | Slide | Glidning |
| 123 | `slideshow_ended` | The slideshow ended | Lysbildeshowet er slutt |
| 124 | `no_media_for_slideshow` | No media for the slideshow have been found | Ingen media for lysbildeshowet er funnet |
| 125 | `group_direct_subfolders` | Group direct subfolders | Grupper direkte undermapper |
| 126 | `group_by` | Group by | Grupper etter |
| 127 | `do_not_group_files` | Do not group files | Ikke grupper filer |
| 128 | `by_folder` | Folder | Mappe |
| 129 | `by_last_modified` | Last modified | Sist endret |
| 130 | `by_last_modified_daily` | Last modified (daily) | Sist endret (daglig) |
| 131 | `by_last_modified_monthly` | Last modified (monthly) | Sist endret (månedlig) |
| 132 | `by_date_taken` | Date taken | Dato tatt |
| 133 | `by_date_taken_daily` | Date taken (daily) | Dato tatt (daglig) |
| 134 | `by_date_taken_monthly` | Date taken (monthly) | Dato tatt (månedlig) |
| 135 | `by_file_type` | File type | Filtype |
| 136 | `by_extension` | Extension | Endelse |
| 137 | `show_file_count_section_header` | Show file count at section headers | Vis filantall i seksjonsoverskrifter |
| 138 | `grouping_and_sorting` | Please note that grouping and sorting are 2 independent fields | Vær oppmerksom på at gruppering og sortering er to uavhengige områder |
| 139 | `folder_on_widget` | Folder shown on the widget: | Mappe vist på modulen: |
| 140 | `show_folder_name` | Show folder name | Vis mappenavn |
| 141 | `autoplay_videos` | Play videos automatically | Avspill videoer automatisk |
| 142 | `remember_last_video_position` | Remember last video playback position | Husk siste videoavspillingsposisjon |
| 143 | `loop_videos` | Loop videos | Gjenta videoer |
| 144 | `video_double_tap_duration` | Double-tap seek duration | Søketid ved dobbelttrykk |
| 145 | `video_seek_duration_seconds` | %d seconds | %d sekunder |
| 146 | `animate_gifs` | Animate GIFs at thumbnails | Animert GIF i minibildevisning |
| 147 | `max_brightness` | Max brightness when viewing fullscreen media | Maks lysstyrke ved mediavisning |
| 148 | `render_ultra_hdr_photos` | Show Ultra HDR photos in HDR | Vis Ultra HDR-bilder i HDR |
| 149 | `crop_thumbnails` | Crop thumbnails into squares | Beskjær minibilder i kvadrater |
| 150 | `show_thumbnail_video_duration` | Show video durations | Vis videolengde |
| 151 | `screen_rotation_by` | Rotate fullscreen media by | Roter media etter |
| 152 | `screen_rotation_system_setting` | System setting | Systeminnstilling |
| 153 | `screen_rotation_device_rotation` | Device rotation | Enhetsrotasjon |
| 154 | `screen_rotation_aspect_ratio` | Aspect ratio | Sideforhold |
| 155 | `black_background_at_fullscreen` | Black background at fullscreen media | Svart bakgrunn og statuslinje i mediavisningen |
| 156 | `scroll_thumbnails_horizontally` | Scroll thumbnails horizontally | Horisontal rulling av minibilder |
| 157 | `hide_system_ui_at_fullscreen` | Automatically hide system UI at fullscreen media | Skjul automatisk systemlinjer ved mediavisning |
| 158 | `delete_empty_folders` | Delete empty folders after deleting their content | Slett tomme mapper etter sletting av deres innhold |
| 159 | `keep_screen_on_fullscreen_photos` | Keep the screen on while viewing fullscreen photos | Hold skjermen på ved visning av bilder i fullskjermmodus |
| 160 | `allow_photo_gestures` | Allow controlling photo brightness with vertical gestures | Tillat å styre fotolysstyrke med vertikale bevegelser |
| 161 | `allow_video_gestures` | Allow controlling video volume and brightness with vertical gestures | Tillat å styre videovolum og lysstyrke med vertikale bevegelser |
| 162 | `playback_speed` | Playback speed | Avspillingshastighet |
| 163 | `show_media_count` | Show folder media count on the main view | Vis mediaantallet i mapper på hovedvisningen |
| 164 | `show_extended_details` | Show extended details over fullscreen media | Vis flere detaljer i mediavisningen |
| 165 | `manage_extended_details` | Manage extended details | Velg detaljer |
| 166 | `one_finger_zoom` | Allow one finger zoom at fullscreen media | Tillat en-finger-zoom i mediavisningen |
| 167 | `allow_instant_change` | Allow instantly changing media by clicking on screen sides | Tillat å skifte media øyeblikkelig ved å trykke på kanten av skjermen |
| 168 | `allow_deep_zooming_images` | Allow deep zooming images | Tillat dyp bildezooming |
| 169 | `hide_extended_details` | Hide extended details when status bar is hidden | Skjul utvidede detaljer når statuslinjen er skjult |
| 170 | `show_at_bottom` | Show some action buttons at the bottom of the screen | Vis noen handlingsknapper nederst på skjermen |
| 171 | `show_recycle_bin` | Show the recycle bin on the folders screen | Vis papirkurven på mappeskjermen |
| 172 | `deep_zoomable_images` | Deep zoomable images | Dyp zoombare bilder |
| 173 | `show_highest_quality` | Show images in the highest possible quality | Vis bilder i høyest mulig kvalitet |
| 174 | `show_recycle_bin_last` | Show the recycle bin as the last item on the main screen | Vis papirkurven som siste element på hovedskjermen |
| 175 | `allow_down_gesture` | Allow closing the fullscreen view with a down gesture | Tillat lukking av mediavisningen med en nedoverbevegelse |
| 176 | `allow_one_to_one_zoom` | Allow 1:1 zooming in with two double taps | Tillat å zoome 1:1 med to dobbeltrykk |
| 177 | `open_videos_on_separate_screen` | Play videos on a separate screen with horizontal gestures | Spill videoer på en separat skjerm med horisontale bevegelser |
| 178 | `on_video_tap` | On video tap | Ved videotrykk |
| 179 | `open_in_app_player` | Open in-app player | Åpne spilleren i appen |
| 180 | `open_system_default_player` | Open in another app | Åpne i en annen app |
| 181 | `show_notch` | Show a notch if available | Vis flik hvis tilgjengelig |
| 182 | `allow_rotating_gestures` | Allow rotating images with gestures | Tillat rotering av bilder med fingerbevegelser |
| 183 | `file_loading_priority` | File loading priority | Fillastingsprioritet |
| 184 | `speed` | Speed | Hastighet |
| 185 | `compromise` | Compromise | Kompromiss |
| 186 | `avoid_showing_invalid_files` | Avoid showing invalid files | Unngå visning av ugyldige filer |
| 187 | `show_image_file_types` | Show image file types | Vis bildefiltyper |
| 188 | `allow_zooming_videos` | Allow zooming videos with double tapping them | Tillat zooming av videoer ved å dobbelttrykke dem |
| 189 | `folder_thumbnail_style` | Folder thumbnail style | Minibildestil for mapper |
| 190 | `file_thumbnail_style` | File thumbnail style | Minibildestil for filer |
| 191 | `mark_favorite_items` | Mark favorite items | Marker favorittelementer |
| 192 | `thumbnail_spacing` | Thumbnail spacing | Minibildemellomrom |
| 193 | `show_file_count_line` | Show file count on a separate line | Vis antall filer på en separat linje |
| 194 | `show_file_count_brackets` | Show file count in brackets | Vis antall filer i parentes |
| 195 | `show_file_count_none` | Do not show file count | Ikke vis antall filer |
| 196 | `limit_folder_title` | Limit long folder titles to 1 line | Begrens lange mappetitler til 1 linje |
| 197 | `square` | Square | Firkantet |
| 198 | `rounded_corners` | Rounded corners | Avrundede hjørner |
| 199 | `export_favorite_paths` | Export favorites and virtual albums | Eksporter favoritter og virtuelle album |
| 200 | `import_favorite_paths` | Import favorites and virtual albums | Importer favoritter og virtuelle album |
| 201 | `paths_imported_successfully` | Paths imported successfully | Filbaner importert |
| 202 | `media_management_prompt` | To make sure that all file operations work reliably, please make this app a Media management app in your device settings. | For å være sikker på at alle filoperasjoner fungerer pålitelig, gjør denne appen til en mediaadministrasjonsapp i enhetsinnstillingene. |
| 203 | `password_protect_excluded` | Password protect excluded folder visibility | Passordbeskytt synligheten til ekskluderte mapper |
| 204 | `media_management_manual` | Something went wrong, please go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | Noe gikk galt. Gå til enhetens Innstillinger – Apper – Spesiell apptilgang – Apper for mediaadministrasjon, og tillat at denne appen administrerer media. |
| 205 | `media_management_note` | If the redirection does not work, please go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | Hvis omdirigeringen ikke fungerer, gå til enhetens Innstillinger – Apper – Spesiell apptilgang – Apper for mediaadministrasjon, og tillat at denne appen administrerer media. |
| 206 | `media_management_alternative` | If you do not want to do it, you can also go into your device Settings - Apps - Special app access - Media management apps and allow this app to manage media. | Hvis du ikke vil gjøre det, kan du også gå til enhetens Innstillinger – Apper – Spesiell apptilgang – Apper for mediaadministrasjon, og tillat at denne appen administrerer media. |
| 207 | `alternative_media_access` | Alternatively, you can allow accessing media files only. In that case you will not be able to work with hidden files though. | Alternativt kan du kun tillate tilgang til mediafiler. I dette tilfellet vil du riktignok ikke kunne arbeide med skjulte filer. |
| 208 | `media_only` | Media only | Kun media |
| 209 | `all_files` | All files | Alle filer |
| 210 | `main_screen_view_on_launch` | Main screen view on launch | Hovedskjermvisning ved oppstart |
| 211 | `show_folders` | Show folders | Vis mapper |
| 212 | `show_files` | Show files | Vis filer |
| 213 | `show_all_folders` | Show a menu button for toggling Show All Folders Content quickly | Vis en menyknapp for å raskt slå av/på \"Vis alle mappers innhold\" |
| 214 | `thumbnails` | Thumbnails | Minibilder |
| 215 | `fullscreen_media` | Fullscreen media | Mediavisning |
| 216 | `extended_details` | Extended details | Utvidede detaljer |
| 217 | `bottom_actions` | Bottom actions | Handlinger nederst |
| 218 | `manage_bottom_actions` | Manage visible bottom actions | Behandle nedre handlingsknapper |
| 219 | `toggle_favorite` | Toggle favorite | Slå av/på favoritt |
| 220 | `toggle_file_visibility` | Toggle file visibility | Filsynlighet av/på |
| 221 | `failed_to_load_media` | Failed to load media. | Kunne ikke laste inn media. |
| 222 | `file_is_malformed_or_corrupted` | File is malformed or corrupted. | Filen er ugyldig eller ødelagt. |
| 223 | `media_exceeds_device_capabilities` | Media exceeds device capabilities. | Mediet overstiger enhetens kapasitet. |
| 224 | `unsupported_format` | Unsupported format. | Ikke-støttet format. |
| 225 | `video_no_sound` | This video has no audio | Denne videoen har ingen lyd |
| 226 | `faq_1_title` | How can I make Calypso Gallery the default device gallery? | Hvordan kan jeg gjøre Fossify Gallery til enhetens standardgalleri? |
| 227 | `faq_1_text` | First you have to find the currently default gallery in the Apps section of your device settings, look for a button that says something like \"Open by default\", click on it, then select \"Clear defaults\".         The next time you will try opening an image or video you should see an app picker, where you can select Fossify Gallery and make it the default app. | Først må du finne det nåværende standardgalleriet i Apper-delen av enhetsinnstillingene, se etter en knapp som heter noe slikt som \"Åpne som standard\", trykke på den og deretter velge \"Fjern standardvalg\".\nNeste gang du prøver å åpne et bilde eller en video, skal du se en appvelger der du kan velge Fossify Gallery og gjøre den til standardapp. |
| 228 | `faq_2_title` | I locked the app with a password, but I forgot it. What can I do? | Jeg låste appen med et passord, men jeg har glemt det. Hva kan jeg gjøre? |
| 229 | `faq_2_text` | You can solve it in 2 ways. You can either reinstall the app, or find the app in your device settings and select \"Clear data\". It will reset all your settings, it will not remove any media files. | Du kan løse det på to måter. Du kan enten installere appen på nytt, eller finne appen i enhetsinnstillingene og velge \"Slett data\". Det vil tilbakestille alle innstillingene dine, men det vil ikke fjerne noen mediafiler. |
| 230 | `faq_3_title` | How can I make an album always appear at the top? | Hvordan kan jeg få et album til å alltid vises øverst? |
| 231 | `faq_3_text` | You can long press the desired album and select the Pin icon at the actions menu, that will pin it to the top. You can pin multiple folders too, pinned items will be sorted by the default sorting method. | Du kan trykke og holde på ønsket album og velge Fest-ikonet i handlingsmenyen for å feste det til toppen. Du kan også feste flere mapper; festede elementer blir sortert etter standard sorteringsmetode. |
| 232 | `faq_4_title` | How can I fast forward videos? | Hvordan kan jeg spole fremover i videoer? |
| 233 | `faq_4_text` | You can do it by double tapping the side of the screen, or tapping the current or max duration texts near the seekbar. If you enable opening videos on a separate screen in the app settings, you can use horizontal gestures too. | Du kan gjøre det ved å dobbelttrykke på siden av skjermen, eller trykke på teksten for nåværende eller maksimal varighet nær søkelinjen. Hvis du aktiverer avspilling av videoer på en separat skjerm i appinnstillingene, kan du også bruke horisontale bevegelser. |
| 234 | `faq_5_title` | What is the difference between hiding and excluding a folder? | Hva er forskjellen mellom å skjule og å ekskludere en mappe? |
| 235 | `faq_5_text` | Exclude prevents displaying the folder only in Calypso Gallery, while Hide works system-wise and it hides the folder from other galleries too. It works by creating an empty \".nomedia\" file in the given folder, which you can then remove with any file manager too. Note that some devices do not allow hiding folders like Camera, Screenshots and Downloads. | Ekskluder hindrer at mappen vises kun i Fossify Gallery, mens Skjul fungerer på systemnivå og skjuler mappen også for andre gallerier. Det fungerer ved å opprette en tom \".nomedia\"-fil i den aktuelle mappen, som du også kan fjerne med en hvilken som helst filbehandler. Merk at enkelte enheter ikke tillater å skjule mapper som Camera, Screenshots og Downloads. |
| 236 | `faq_6_title` | Why do folders with music cover art or stickers show up? | Hvorfor vises mapper med plateomslag eller klistremerker? |
| 237 | `faq_6_text` | It can happen that you will see some unusual albums show up. You can easily exclude them by long pressing them and selecting Exclude. In the next dialog you can then select the parent folder, chances are it will prevent the other related albums showing up too. | Det kan skje at uvanlige album dukker opp. Du kan enkelt ekskludere dem ved å trykke og holde på dem og velge Ekskluder. I den neste dialogen kan du så velge den overordnede mappen, noe som sannsynligvis også vil hindre at andre relaterte album vises. |
| 238 | `faq_7_title` | A folder with images isn\'t showing up, or it doesn\'t show all items.  What can I do? | En mappe med bilder vises ikke, eller den viser ikke alle elementer. Hva kan jeg gjøre? |
| 239 | `faq_7_text` | That can have multiple reasons, but solving it is easy. Just go in Settings -&gt; Manage Included Folders, select Plus and navigate to the required folder. | Det kan ha flere årsaker, men det er enkelt å løse. Bare gå til Innstillinger -&gt; Håndter inkluderte mapper, velg Pluss og naviger til ønsket mappe. |
| 240 | `faq_8_title` | What if I want just a few particular folders visible? | Hva om jeg bare vil at noen bestemte mapper skal være synlige? |
| 241 | `faq_8_text` | Adding a folder at the Included Folders doesn\'t automatically exclude anything. What you can do is go in Settings -&gt; Manage Excluded Folders, exclude the root folder \"/\", then add the desired folders at Settings -&gt; Manage Included Folders.         That will make only the selected folders visible, as both excluding and including are recursive and if a folder is both excluded and included, it will show up. | Å legge til en mappe under Inkluderte mapper ekskluderer ikke automatisk noe annet. Det du kan gjøre er å gå til Innstillinger -&gt; Håndter ekskluderte mapper, ekskludere rotmappen \"/\", og deretter legge til ønskede mapper under Innstillinger -&gt; Håndter inkluderte mapper.\nDet vil gjøre bare de valgte mappene synlige, siden både ekskludering og inkludering er rekursive, og hvis en mappe både er ekskludert og inkludert, vil den vises. |
| 242 | `faq_10_title` | Can I crop images with this app? | Kan jeg beskjære bilder med denne appen? |
| 243 | `faq_10_text` | Yes, you can crop images in the editor, by dragging the image corners. You can get to the editor either by long pressing an image thumbnail and selecting Edit, or selecting Edit from the fullscreen view. | Ja, du kan beskjære bilder i redigeringsverktøyet ved å dra i bildehjørnene. Du kommer til redigeringsverktøyet enten ved å trykke og holde på et minibilde og velge Rediger, eller ved å velge Rediger fra fullskjermvisningen. |
| 244 | `faq_11_title` | Can I somehow group media file thumbnails? | Kan jeg på noen måte gruppere minibilder av mediafiler? |
| 245 | `faq_11_text` | Sure, just use the \"Group by\" menu item while at the thumbnails view. You can group files by multiple criteria, including Date Taken. If you use the \"Show all folders content\" function you can group them by folders too. | Selvfølgelig, bare bruk menyelementet \"Grupper etter\" når du er i minibildevisningen. Du kan gruppere filer etter flere kriterier, inkludert Dato tatt. Hvis du bruker funksjonen \"Vis alle mappers innhold\", kan du også gruppere dem etter mapper. |
| 246 | `faq_12_title` | Sorting by Date Taken doesn\'t seem to work properly, how can I fix it? | Sortering etter Dato tatt ser ikke ut til å fungere skikkelig, hvordan kan jeg fikse det? |
| 247 | `faq_12_text` | It is most likely caused by the files being copied from somewhere. You can fix it by selecting the file thumbnails and selecting \"Fix Date Taken value\". | Det skyldes sannsynligvis at filene er kopiert fra et annet sted. Du kan fikse det ved å merke minibildene og velge \"Korriger Dato tatt-verdi\". |
| 248 | `faq_13_title` | I see some color banding on the images. How can I improve the quality? | Jeg ser fargebånd på bildene. Hvordan kan jeg forbedre kvaliteten? |
| 249 | `faq_13_text` | The current solution for displaying images works fine in the vast majority of cases, but if you want even better image quality, you can enable the \"Show images in the highest possible quality\" at the app settings, in the \"Deep zoomable images\" section. | Den nåværende løsningen for bildevisning fungerer fint i de aller fleste tilfeller, men hvis du ønsker enda bedre bildekvalitet, kan du aktivere \"Vis bilder i høyest mulig kvalitet\" i appinnstillingene under delen \"Dyp zoombare bilder\". |
| 250 | `faq_14_title` | I have hidden a file/folder. How can I unhide it? | Jeg har skjult en fil/mappe. Hvordan kan jeg gjøre den synlig igjen? |
| 251 | `faq_14_text` | You can either press the \"Temporarily show hidden items\" menu item at the main screen, or toggle \"Show hidden items\" in the app settings to see the hidden item. If you want to unhide it, just long press it and select \"Unhide\". Folders are hidden by adding a hidden \".nomedia\" file into them, you can delete the file with any file manager too. Note that hiding works recursively though, so if you hide a folder, all subfolders will become hidden too. So for unhiding the subfolders you have to unhide the parent folder. | Du kan enten trykke på menyelementet \"Vis skjulte elementer midlertidig\" på hovedskjermen, eller slå på \"Vis skjulte elementer\" i appinnstillingene for å se det skjulte elementet. Hvis du vil gjøre det synlig igjen, trykker og holder du bare på det og velger \"Vis igjen\". Mapper skjules ved å legge til en skjult \".nomedia\"-fil i dem; du kan også slette denne filen med en hvilken som helst filbehandler. Merk imidlertid at skjuling fungerer rekursivt, så hvis du skjuler en mappe, blir alle undermapper også skjult. For å gjøre undermappene synlige igjen må du derfor gjøre den overordnede mappen synlig igjen. |
| 252 | `faq_15_title` | Why does the app take up so much space? | Hvorfor tar appen opp så mye plass? |
| 253 | `faq_15_text` | App cache can take up to 250MB, it ensures quicker image loading. If the app is taking up even more space, it is most likely caused by you having items in the recycle bin. Those files count to the app size. You can clear the recycle bin by opening it and deleting all files, or from the app settings. Every file in the bin is deleted automatically after 30 days. | Appens hurtigbuffer kan ta opptil 250 MB; den sørger for raskere innlasting av bilder. Hvis appen tar opp enda mer plass, skyldes det sannsynligvis at du har elementer i papirkurven. Disse filene teller med i appstørrelsen. Du kan tømme papirkurven ved å åpne den og slette alle filer, eller fra appinnstillingene. Hver fil i papirkurven slettes automatisk etter 30 dager. |
| 254 | `faq_16_title` | What happened to file and folder hiding and why cannot I see hidden items anymore? | Hva skjedde med skjuling av filer og mapper, og hvorfor kan jeg ikke se skjulte elementer lenger? |
| 255 | `faq_16_text` | Starting with Android 11 you cannot hide or unhide files or folders anymore, you cannot see the hidden ones in gallery apps due to system restrictions either. You will have to use some file manager for that. | Fra og med Android 11 kan du ikke lenger skjule/vise filer eller mapper, du kan heller ikke se de skjulte i galleriapper pga. systemrestriksjoner. Du må bruke en filbehandlingsapp for det. |
| 256 | `faq_16_text_extra` | Or you can also grant this gallery access to All Files through your device settings, that will allow us showing hidden items and make file operations more reliable in general. | Eller du kan også gi dette galleriet tilgang til Alle filer via enhetsinnstillingene; det lar oss vise skjulte elementer og gjør filoperasjoner mer pålitelige generelt. |
| 257 | `faq_17_title` | Why cannot I include missing folders anymore? | Hvorfor kan jeg ikke inkludere manglende mapper lenger? |
| 258 | `faq_17_text` | That stopped working due to the system changes that came with Android 11 too, the app cannot browse real folders anymore, it relies on the so called MediaStore at fetching data. | Det sluttet også å fungere på grunn av systemendringene som kom med Android 11; appen kan ikke lenger bla gjennom ekte mapper, men er avhengig av såkalte MediaStore for å hente data. |
| 259 | `faq_18_title` | Why do I see ads during video playback? | Hvorfor ser jeg reklame under videoavspilling? |
| 260 | `faq_18_text` | Our apps have no ads whatsoever. If you see them during video playback, you must be using some other apps video player. Try finding your default video player in the device settings, then do a \"Clear defaults\" on it. The next time you invoke some video intent you will see an app picker prompt, where you can select what app you want to use. | Appene våre har ingen reklame overhodet. Hvis du ser reklame under videoavspilling, bruker du sannsynligvis videospilleren til en annen app. Prøv å finne standard videospiller i enhetsinnstillingene, og velg \"Fjern standardvalg\" på den. Neste gang du åpner en video, vil du se en appvelger der du kan velge hvilken app du vil bruke. |
| 261 | `create_virtual_album` | Create virtual album | Opprett virtuelt album |
| 262 | `new_virtual_album` | New album… | Nytt album… |
| 263 | `rename_virtual_album` | Rename album | Gi albumet nytt navn |
| 264 | `album_name` | Album name | Albumnavn |
| 265 | `virtual_folder_name` | Virtual album name | Navn på virtuelt album |
| 266 | `virtual_album_name_empty` | The album name cannot be empty | Albumnavnet kan ikke være tomt |
| 267 | `virtual_album_exists` | An album with this name already exists | Et album med dette navnet finnes allerede |
| 268 | `add_to_virtual_album` | Add to album | Legg til i album |
| 269 | `remove_from_virtual_album` | Remove from album | Fjern fra album |
| 270 | `added_to_virtual_album` | Added to \"%s\" | Lagt til i \"%s\" |
| 271 | `already_in_virtual_album` | Already in \"%s\" | Allerede i \"%s\" |
| 272 | `removed_from_virtual_album` | Removed from the album | Fjernet fra albumet |
| 273 | `delete_virtual_album_confirmation` | Delete the album %s? Only the album is removed, your photos and videos stay where they are. | Slette albumet %s? Bare albumet fjernes, bildene og videoene dine forblir der de er. |
| 274 | `virtual_album_empty` | This album is empty. Select items in any folder and use \"Copy to\". | Dette albumet er tomt. Velg elementer i en hvilken som helst mappe og bruk \"Kopier til\". |
| 275 | `virtual_album_cannot_be_modified` | This action is not available for virtual albums | Denne handlingen er ikke tilgjengelig for virtuelle album |
| 276 | `alphanumeric_password` | Alphanumeric | Alfanumerisk |
| 277 | `enter_password` | Enter password | Skriv inn passord |
| 278 | `repeat_password` | Repeat password | Gjenta passord |
| 279 | `wrong_password` | Wrong password | Feil passord |
| 280 | `encrypting_files` | Encrypting files… | Krypterer filer… |
| 281 | `encrypting_folder` | Encrypting folder… | Krypterer mappe… |
| 282 | `folder_encrypted_successfully` | Folder encrypted successfully | Mappen ble kryptert |
| 283 | `file_encrypted_successfully` | File encrypted successfully | Filen ble kryptert |
| 284 | `files_encrypted_successfully` | Files encrypted successfully | Filene ble kryptert |
| 285 | `decrypting_folder` | Decrypting folder… | Dekrypterer mappe… |
| 286 | `folder_decrypted_successfully` | Folder decrypted successfully | Mappen ble dekryptert |
| 287 | `file_decrypted_successfully` | File decrypted successfully | Filen ble dekryptert |
| 288 | `files_decrypted_successfully` | Files decrypted successfully | Filene ble dekryptert |
| 289 | `decrypting_files` | Decrypting files… | Dekrypterer filer… |
| 290 | `place_finger_prompt` | Please place your finger on the fingerprint sensor | Plasser fingeren på fingeravtrykkssensoren |
| 291 | `connect_at_least_4_dots_prompt` | Connect at least 4 dots | Koble sammen minst 4 punkter |
| 292 | `access_existing_secrets_prompt` | Do you want to access your existing Secrets folder? | Vil du få tilgang til din eksisterende Secrets-mappe? |
| 293 | `delete_and_recreate_secrets_prompt` | Existing Secrets folder will be deleted and a new one will be created. Are you sure? | Eksisterende Secrets-mappe vil bli slettet og en ny vil bli opprettet. Er du sikker? |
| 294 | `confirm_checkbox_label` | I confirm | Jeg bekrefter |
| 295 | `secret_vault` | Encrypted Vault | Kryptert hvelv |
| 296 | `secret_vault_empty` | Encrypted Vault is empty | Det krypterte hvelvet er tomt |
| - | `press_back_again_to_exit_vault` | Press back again to exit | Trykk tilbake igjen for å avslutte |
| - | `block_vault_screenshots` | Block screenshots in encrypted vault | Blokker skjermbilder i det krypterte hvelvet |
| 297 | `confirm_decrypt_hidden_file` | Are you sure you want to decrypt the hidden file(s)? | Er du sikker på at du vil dekryptere de(n) skjulte filen(e)? |
| 298 | `copy_to_clipboard_disabled_in_vault` | Copying to clipboard is disabled while vault is unlocked for security. | Kopiering til utklippstavlen er deaktivert av sikkerhetshensyn mens hvelvet er låst opp. |
| 299 | `stop_showing_hidden` | Stop showing encrypted media | Slutt å vise krypterte medier |
| 300 | `system_default` | System default | Systemstandard |
| 301 | `language` | Language | Språk |
| 302 | `open_system_language_settings` | Open system language settings | Åpne systemets språkinnstillinger |
| 303 | `access_hidden_items_with_biometrics` | Access hidden items with biometrics | Få tilgang til skjulte elementer med biometri |
| 304 | `enable_hidden_protection_first_prompt` | Please enable hidden item password protection first | Aktiver passordbeskyttelse for skjulte elementer først |
| 305 | `biometric_unlock_vault` | Unlock encrypted vault | Lås opp kryptert hvelv |
| 306 | `biometric_unlock_prompt` | Verify your biometric identity to access hidden items | Bekreft din biometriske identitet for å få tilgang til skjulte elementer |
| 307 | `biometric_setup_prompt` | Confirm your biometric identity to enable biometric vault access | Bekreft din biometriske identitet for å aktivere biometrisk tilgang til hvelvet |
| 308 | `use_password_or_pin` | Use PIN / Password | Bruk PIN / Passord |
| 309 | `biometric_key_invalidated` | Biometric credentials have changed. Please enter your PIN or password. | Biometriske data er endret. Vennligst skriv inn PIN eller passord. |
| 310 | `no_biometric_hardware_or_enrolled` | No biometric hardware or enrolled credentials found on this device | Ingen biometrisk maskinvare eller registrerte biometriske data funnet på denne enheten |
| 311 | `default_hidden_album` | Default Secret Album | *(non-translatable in `values/strings.xml`)* |
| 312 | `select_hidden_album` | Select Encrypted Album | Velg kryptert album |
| 313 | `create_new_hidden_album` | Create New Encrypted Album | Opprett nytt kryptert album |
| 314 | `new_hidden_album` | New encrypted album | Nytt kryptert album |
| 315 | `hidden_album_name_empty` | Album name cannot be empty | Albumnavn kan ikke være tomt |
| 316 | `file_hidden_to_album` | File encrypted to \"%s\" | Fil kryptert i «%s» |
| 317 | `files_hidden_to_album` | %1$d files encrypted to \"%2$s\" | %1$d filer kryptert i «%2$s» |
| 318 | `restore_confirmation` (`one`) | Are you sure you want to restore %d file? | Er du sikker på du vil gjenopprette %d fil? |
| 319 | `restore_confirmation` (`other`) | Are you sure you want to restore %d files? | Er du sikker på du vil gjenopprette %d filer? |
| 320 | `failed_to_resize_images` (`one`) | Failed to resize %d image | Klarte ikke endre størrelse på %d bilde |
| 321 | `failed_to_resize_images` (`other`) | Failed to resize %d images | Klarte ikke endre størrelse på %d bilder |
| 322 | `search` *(fossify-commons)* | *(inherited from commons)* | Søk |
| 323 | `change_date_and_time_format` *(fossify-commons)* | Change date and time format | Endre format for dato og klokkeslett |
| 324 | `password_protect_file_deletion` *(fossify-commons)* | Password protect file deletion and moving | Passordbeskytt sletting og flytting av filer |
| 325 | `keep_last_modified` *(fossify-commons)* | Keep old last-modified value at file operations | Behold sist-endret-verdi ved filoperasjoner |
| 326 | `clear_cache` *(fossify-commons)* | Clear cache | Tøm hurtiglager |
| 327 | `export_settings` *(fossify-commons)* | Export settings | Eksporter innstillinger |
| 328 | `import_settings` *(fossify-commons)* | Import settings | Importer innstillinger |
| 329 | `temporarily_show_excluded` *(fossify-commons)* | Temporarily show excluded | Vis ekskluderte midlertidig |
| 330 | `change_view_type` *(fossify-commons)* | Change view type | Endre visningstype |
| 331 | `contributors` *(fossify-commons)* | Contributors | Bidragsytere |
| 332 | `known_issues` *(fossify-commons)* | Known issues | Kjente problemer |
| 333 | `donate_to_fossify` *(fossify-commons)* | Donate to Fossify | Doner til Fossify |
| 334 | `privacy_policy` *(fossify-commons)* | Privacy policy | Personvernerklæring |
| 335 | `create_shortcut` *(fossify-commons)* | Create shortcut | Opprett snarvei |
| 336 | `print` *(fossify-commons)* | Print | Skriv ut |
| 337 | `search_files` *(fossify-commons)* | Search files | Søk i filer |
| 338 | `search_folders` *(fossify-commons)* | Search folders | Søk i mapper |
| 339 | `skip_the_recycle_bin` *(fossify-commons)* | Skip the recycle bin, delete files directly | Hopp over papirkurven og slett filene direkte |
| 340 | `pull_down_to_open_vault` | Keep pulling down for Encrypted Vault | Fortsett å dra ned for kryptert hvelv |
| 341 | `keep_holding_to_open_vault` | Keep holding to open Encrypted Vault | Fortsett å holde for å åpne kryptert hvelv |
| 342 | `release_to_open_vault` | Release to open Encrypted Vault | Slipp for å åpne kryptert hvelv |
| 343 | `exclude_folder` *(fossify-commons)* | Exclude folder | Ekskluder mappe |
| 344 | `column_count` *(fossify-commons)* | Column count | Antall kolonner |
| 345 | `open_the_recycle_bin` *(fossify-commons)* | Open the recycle bin | Åpne papirkurven |
| 346 | `version_placeholder` *(fossify-commons)* | Version %s | Versjon %s |
| 347 | `number_of_items` *(fossify-commons)* | Number of items | Antall elementer |
| 348 | `random` *(fossify-commons)* | Random | Tilfeldig |
| 349 | `shuffle` | Shuffle | Bland |
| 350 | `stop_showing_excluded` *(fossify-commons)* | Stop showing excluded | Slutt å vise ekskluderte |
| 351 | `copy_to_clipboard` *(fossify-commons)* | Copy to clipboard | Kopier til utklippstavlen |
| 352 | `encrypt` | Encrypt | Krypter |
| 353 | `decrypt` | Decrypt | Dekrypter |
| 354 | `encrypt_album` | Encrypt album | Krypter album |
| 355 | `decrypt_album` | Decrypt album | Dekrypter album |
| 356 | `create_new_encrypted_virtual_album` | Create new encrypted virtual album | Opprett nytt kryptert virtuelt album |
| 357 | `new_album` | New album | Nytt album |
| 358 | `filename_without_zip` | Filename (without .zip) | Filnavn (uten .zip) |
| 359 | `favorites_already_exist` | You already have a favorites list | Du har allerede en favorittliste |
| 360 | `delete_existing_list` | Delete existing list | Slett eksisterende liste |
| 361 | `merge_lists` | Merge lists | Slå sammen lister |
| 362 | `confirm` | Confirm | Bekreft |
| 363 | `confirm_encrypt_album` | Are you sure you want to encrypt this album? All of its media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | Er du sikker på at du vil kryptere dette albumet? Alle mediefilene i det blir kryptert og flyttet til det krypterte hvelvet. Originalfilene blir slettet. |
| 364 | `confirm_encrypt_albums` | Are you sure you want to encrypt the selected albums? All of their media files will be encrypted and moved to the encrypted vault. The original files will be deleted. | Er du sikker på at du vil kryptere de valgte albumene? Alle mediefilene i dem blir kryptert og flyttet til det krypterte hvelvet. Originalfilene blir slettet. |
| 365 | `confirm_encrypt_file` | Are you sure you want to encrypt this file? It will be encrypted and moved to the encrypted vault. The original file will be deleted. | Er du sikker på at du vil kryptere denne filen? Den blir kryptert og flyttet til det krypterte hvelvet. Originalfilen blir slettet. |
| 366 | `confirm_encrypt_files` | Are you sure you want to encrypt the selected files? They will be encrypted and moved to the encrypted vault. The original files will be deleted. | Er du sikker på at du vil kryptere de valgte filene? De blir kryptert og flyttet til det krypterte hvelvet. Originalfilene blir slettet. |
| 367 | `confirm_encrypt_files_to_vault` | The selected files will be encrypted when they are copied into the encrypted vault. Do you want to continue? | De valgte filene blir kryptert når de kopieres til det krypterte hvelvet. Vil du fortsette? |
| 368 | `encrypt_decrypt_notifications` | Encrypt / Decrypt | Krypter / Dekrypter |
| 369 | `allow_batch_renaming` | Allow batch renaming when multiple files are selected | Tillat masseendring av navn når flere filer er valgt |
| 370 | `keep_original_remove_from_virtual_album` | Keep the original file, remove it only from this virtual album | Behold originalfilen, fjern den bare fra dette virtuelle albumet |
| 371 | `keep_original_remove_from_favorites` | Keep the original file, remove it only from favorites | Behold originalfilen, fjern den bare fra favoritter |
| 372 | `delete_original_from_favorites_and_virtual_albums` | Delete the original version of a file when it is deleted from favorites and virtual albums | Slett originalversjonen av en fil når den slettes fra favoritter og virtuelle album |
| 373 | `cannot_target_encrypted_album` | Encrypted albums cannot be targeted for this operation | Krypterte album kan ikke velges som mål for denne handlingen |
| 374 | `cannot_mix_encrypted_and_unencrypted` | Cannot combine encrypted and unencrypted items in this operation | Kan ikke kombinere krypterte og ukrypterte elementer i denne handlingen |
| 375 | `lock_album` | Lock album | Lås album |
| 376 | `unlock_album` | Unlock album | Lås opp album |
| 377 | `auto_backup_prompt_message` | Do you want your favorites and virtual albums to be regularly written to system storage outside of app memory? This will ensure that you do not lose your favorites list and virtual albums if you uninstall the app. You can manually create a backup from app settings at any time. | Vil du at favorittene og de virtuelle albumene dine skal lagres regelmessig i systemlagringen utenfor appminnet? Dette sikrer at du ikke mister favorittlisten og de virtuelle albumene dine hvis du avinstallerer appen. Du kan når som helst opprette en sikkerhetskopi manuelt fra appinnstillingene. |
| 378 | `auto_backup_favorites_and_virtual_albums` | Back up favorites and virtual albums to device storage | Sikkerhetskopier favoritter og virtuelle album til enhetslagringen |
| 379 | `access_storage_prompt` *(fossify-commons)* | Please grant our app access to all your files, it might not work well without it. | Vennligst gi appen vår tilgang til alle filene dine, den fungerer kanskje ikke så bra uten dette. |
| 380 | `encrypt_info_title` | How Encryption Works | Slik fungerer kryptering |
| 381 | `encrypt_info_message` | Folder & File Structure:\n• When an unencrypted album is encrypted, it is converted into an encrypted subfolder inside the vault (Secrets).\n• Both the folder name and all file names/contents inside it are encrypted, so nothing is readable outside the app.\n\nIf an Album with the Same Name Already Exists:\n• By default, the app asks whether to create a new album such as Album(1) or merge into the existing encrypted album (configurable in Settings).\n• When merging, you can choose whether source files verified 100% bit-identical to the destination are deleted or kept in the source folder.\n• Different files with the same name (e.g. download.jpg) are kept as download(1).jpg or handled according to your conflict resolution setting. | Mappe- og filstruktur:\n• Når et ukryptert album krypteres, gjøres det om til en kryptert undermappe inne i hvelvet (Secrets).\n• Både mappenavnet og alle filnavn og filinnhold inni det krypteres, slik at ingenting er lesbart utenfor appen.\n\nHvis et album med samme navn allerede finnes:\n• Som standard spør appen om du vil opprette et nytt album som Album(1) eller slå sammen med det eksisterende krypterte albumet (kan konfigureres i Innstillinger).\n• Når du slår sammen, kan du velge om kildefiler som er bekreftet 100% bit-for-bit identiske med målet skal slettes eller beholdes i kildemappen.\n• Ulike filer med samme navn (f.eks. download.jpg) beholdes som download(1).jpg eller håndteres i henhold til innstillingen for konfliktløsning. |
| 382 | `decrypt_info_title` | How Decryption Works | Slik fungerer dekryptering |
| 383 | `decrypt_info_message` | Folder & File Structure:\n• When an encrypted album or file is decrypted, it is restored with its original name and format to the selected destination folder and removed from the encrypted vault.\n\nIf an Album with the Same Name Already Exists:\n• By default, the app asks whether to create a new folder such as Album(1) or merge into the existing destination folder (configurable in Settings).\n• When merging, you can choose whether encrypted copies verified 100% bit-identical to the destination are removed from the vault or kept.\n• Different files with the same name (e.g. download.jpg) are kept as download(1).jpg or handled according to your conflict resolution setting. | Mappe- og filstruktur:\n• Når et kryptert album eller en kryptert fil dekrypteres, gjenopprettes det/den med sitt opprinnelige navn og format til den valgte målmappen og fjernes fra det krypterte hvelvet.\n\nHvis et album med samme navn allerede finnes:\n• Som standard spør appen om du vil opprette en ny mappe som Album(1) eller slå sammen med den eksisterende målmappen (kan konfigureres i Innstillinger).\n• Når du slår sammen, kan du velge om krypterte kopier som er bekreftet 100% bit-for-bit identiske med målet skal fjernes fra hvelvet eller beholdes.\n• Ulike filer med samme navn (f.eks. download.jpg) beholdes som download(1).jpg eller håndteres i henhold til innstillingen for konfliktløsning. |
| 384 | `vault_location` | Vault location | Hvelvplassering |
| 385 | `vault_location_standard_summary` | Standard (Internal Storage) | Standard (Intern lagring) |
| 386 | `vault_location_unavailable_summary` | External drive disconnected (%s) | Ekstern stasjon frakoblet (%s) |
| 387 | `select_vault_location` | Select vault location | Velg hvelvplassering |
| 388 | `vault_setup_action_title` | Encrypted vault | Kryptert hvelv |
| 389 | `create_new_vault_option` | Create new vault | Opprett nytt hvelv |
| 390 | `use_existing_vault_option` | Use existing vault | Bruk eksisterende hvelv |
| 391 | `where_to_create_vault_title` | Where should the vault be created? | Hvor skal hvelvet opprettes? |
| 392 | `vault_location_default_short` | Default location | Standardplassering |
| 393 | `vault_location_custom_short` | Custom Location | Egendefinert plassering |
| 394 | `no_existing_vault_at_location` | No existing encrypted vault (.vault) was found in the selected folder. | Ingen eksisterende kryptert hvelv (.vault) ble funnet i den valgte mappen. |
| 395 | `unlock_existing_vault_at_location_prompt` | An existing encrypted vault was found at %s. Do you want to unlock and use it? Tap No to pick a different folder. | Et eksisterende kryptert hvelv ble funnet på %s. Vil du låse det opp og bruke det? Trykk på Nei for å velge en annen mappe. |
| 396 | `vault_location_standard_option` | Standard location (Internal Storage > Calypso Gallery > Secrets) | Standardplassering (Intern lagring > Calypso Gallery > Secrets) |
| 397 | `vault_location_custom_option` | Custom / External location (USB drive, SD card, or custom folder) | Egendefinert / Ekstern plassering (USB-stasjon, SD-kort eller egendefinert mappe) |
| 398 | `external_vault_unavailable_title` | External vault unavailable | Eksternt hvelv utilgjengelig |
| 399 | `external_vault_unavailable_prompt` | Your configured vault location (%s) is currently disconnected or unavailable. Plug in the drive and tap Retry, or choose another vault location. | Den konfigurerte hvelvplasseringen din (%s) er for øyeblikket frakoblet eller utilgjengelig. Koble til stasjonen og trykk på Prøv på nytt, eller velg en annen hvelvplassering. |
| 400 | `retry` | Retry | Prøv på nytt |
| 401 | `change_vault_location` | Change location | Endre plassering |
| 402 | `existing_vault_detected_at_location` | Existing vault found at %s. Enter its password or PIN to unlock it. | Eksisterende hvelv funnet på %s. Skriv inn passordet eller PIN-koden for å låse det opp. |
| 403 | `create_new_vault_at_location_prompt` | No vault exists at %s. Do you want to create a new encrypted vault at this location? | Det finnes ingen hvelv på %s. Vil du opprette et nytt kryptert hvelv på denne plasseringen? |
| 404 | `usb_direct_filesystem_required` | This USB path is only exposed through Android\'s virtual OTG provider and cannot be accessed directly as a filesystem path. Please choose a mounted storage directory or a folder on internal storage / SD card. | Denne USB-banen er bare eksponert gjennom Androids virtuelle OTG-leverandør og kan ikke åpnes direkte som en filsystembane. Vennligst velg en montert lagringskatalog eller en mappe på intern lagring / SD-kort. |
| 405 | `vault_location_not_writable` | The selected folder does not exist or is not writable on this device. | Den valgte mappen finnes ikke eller kan ikke skrives til på denne enheten. |
| 406 | `vault_location_not_readable` | The selected folder does not exist or is not readable on this device. | Den valgte mappen finnes ikke eller kan ikke leses på denne enheten. |
| 407 | `sync_vault_from_external` | Import newly encrypted media filed to encrypted section | Importer krypterte mediefiler til kryptert seksjon |
| 408 | `sync_vault_select_source_prompt` | How Encrypted Media Import Works:\n\n1. Unified Secrets Folder:\nKeep a folder named Secrets on your external drive (USB / SD). You can either keep a full backup copy of your vault there, or place only your vault\'s .vault header file inside it.\n\n2. Encrypting New Media (PC / Tails CLI):\nThe offline CLI tool automatically saves new encrypted files directly into Secrets. Files placed at the root go to Default Secret Album; files in subfolders go to matching encrypted albums (or create new ones).\n\n3. Bit-by-Bit Smart Sync:\nSelect your external drive or Secrets folder next. The app compares the external drive with your phone vault in RAM:\n• Only new files and albums are imported.\n• Bit-identical files already in your vault are skipped.\n• Different files with the same name (e.g. download.jpg) are safely imported as download(1).jpg. | Slik fungerer import av krypterte medier:\n\n1. Enhetlig Secrets-mappe:\nHa en mappe med navnet Secrets på den eksterne stasjonen din (USB / SD). Der kan du enten ha en fullstendig sikkerhetskopi av hvelvet ditt, eller bare legge inn hvelvets .vault-headerfil.\n\n2. Kryptering av nye medier (PC / Tails CLI):\nDet frakoblede CLI-verktøyet lagrer automatisk nye krypterte filer direkte i Secrets. Filer i rotmappen havner i Default Secret Album; filer i undermapper havner i tilsvarende krypterte album (eller oppretter nye).\n\n3. Bit-for-bit smart synkronisering:\nVelg den eksterne stasjonen eller Secrets-mappen nedenfor. Appen sammenligner den eksterne stasjonen med telefonens hvelv i RAM:\n• Bare nye filer og album importeres.\n• Bit-identiske filer som allerede finnes i hvelvet hoppes over.\n• Ulike filer med samme navn (f.eks. download.jpg) importeres trygt som download(1).jpg. |
| 409 | `conflict_handling_mode_label` | File conflict & duplicate resolution | Løsning av filkonflikter og duplikater |
| 410 | `conflict_handling_auto_short` | Automatic (Experimental — Auto-skip identical, rename different as (1)) | Helautomatisk (Standard — Hopp over identiske, gi ulike nytt navn som (1)) |
| 411 | `conflict_handling_smart_dialog_short` | Smart dialog (Auto-skip identical, ask when content differs) | Smart dialog (Hopp over identiske automatisk, spør når innholdet er ulikt) |
| 412 | `album_crypto_conflict_mode_label` | Album conflict (Encrypt / Decrypt) | Albumkonflikt (Krypter / Dekrypter) |
| 413 | `album_crypto_conflict_ask` | Always ask (Default) | Spør alltid (Standard) |
| 414 | `album_crypto_conflict_merge` | Merge into existing album | Slå sammen med eksisterende album |
| 415 | `album_crypto_conflict_merge_delete_identical` | Merge into existing album and automatically delete identical sources | Slå sammen med eksisterende album og slett identiske kilder automatisk |
| 416 | `album_crypto_conflict_rename` | Create new album as (1) | Opprett nytt album som (1) |
| 417 | `album_crypto_conflict_title` | Album Already Exists | Albumet finnes allerede |
| 418 | `album_crypto_conflict_message` | An album named \"%s\" already exists at the destination. How would you like to proceed? | Et album med navnet \"%s\" finnes allerede på målet. Hvordan vil du fortsette? |
| 419 | `album_crypto_conflict_message_multiple` | %1$d selected albums already exist at the destination. How would you like to proceed?\n\nYour choice will apply to all conflicting albums. To choose separately per album, select and process them one by one. | %1$d valgte album finnes allerede på målet. Hvordan vil du fortsette?\n\nValget ditt vil gjelde for alle album med konflikt. For å velge separat per album, velg og behandle dem ett og ett. |
| 420 | `album_crypto_conflict_rename_option` | Create new album as \"%s\" | Opprett nytt album som \"%s\" |
| 421 | `album_crypto_conflict_rename_option_multiple` | Create new albums as (1), (2), … | Opprett nye album som (1), (2), … |
| 422 | `album_crypto_conflict_merge_option` | Merge into existing \"%s\" | Slå sammen inn i eksisterende \"%s\" |
| 423 | `album_crypto_conflict_merge_option_multiple` | Merge into existing albums | Slå sammen inn i eksisterende album |
| 424 | `album_crypto_delete_identical_source` | Delete identical source files (100% bit-by-bit verified) | Slett identiske kildefiler (100% bit-for-bit bekreftet) |
| 425 | `album_crypto_dont_ask_again` | Remember album conflict choice (merge or rename) | Husk valg for albumkonflikt (slå sammen eller gi nytt navn) |
| 426 | `album_crypto_merge_identical_title` | Identical Files During Merge | Identiske filer ved sammenslåing |
| 427 | `album_crypto_merge_identical_message` | When merging into \"%1$s\", if any files are already 100%% bit-identical at the destination, should their source copies be deleted or kept? | Når du slår sammen inn i \"%1$s\", hvis noen filer allerede er 100%% bit-identiske på målet, skal kildekopiene slettes eller beholdes? |
| 428 | `album_crypto_merge_identical_message_multiple` | When merging the %1$d conflicting albums, if any files are already 100%% bit-identical at the destination, should their source copies be deleted or kept? | Når du slår sammen de %1$d albumene med konflikt, hvis noen filer allerede er 100%% bit-identiske på målet, skal kildekopiene slettes eller beholdes? |
| 429 | `delete_identical_sources` | Delete identical | Slett identiske |
| 430 | `keep_identical_sources` | Keep in source | Behold i kilde |
| 431 | `fast_large_duplicate_check_label` | Fast duplicate check for large files (>8 MB) | Rask duplikatsjekk for store filer (>8 MB) |
| 432 | `fast_large_duplicate_check_hint` | Crucial for transfers containing large files: checks 5 blocks (1.25 MB total) per file instead of reading whole files, speeding up duplicate checks dozens of times. | Avgjørende for overføringer med store filer: sjekker 5 blokker (1,25 MB totalt) per fil i stedet for å lese hele filer, noe som gjør duplikatsjekken titalls ganger raskere. |
| 433 | `fast_large_duplicate_check_info_title` | Fast Duplicate Check (>8 MB) | Rask duplikatsjekk (>8 MB) |
| 434 | `conflict_handling_explanation_title` | File Conflict & Duplicate Resolution | Løsning av filkonflikter og duplikater |
| 435 | `conflict_handling_explanation_message` | When copying, moving, encrypting, or decrypting files and albums (including album merges), if a file with the same name already exists at the destination:\n\n• Standard dialog (Default): Checks filename only (like the original Fossify Gallery app) without comparing file contents, and asks you to Skip / Overwrite / Keep both.\n• Smart dialog: Checks file sizes and bit-by-bit contents in RAM (encrypted files stay in memory), auto-skips identical duplicates, and asks with side-by-side previews (Skip / Overwrite / Keep both) only when content differs.\n• Automatic (Experimental): Auto-skips identical duplicates and automatically keeps both different files by naming the incoming file download(1).jpg, download(2).jpg. | Når du kopierer, flytter, krypterer eller dekrypterer filer og album, og en fil med samme navn allerede finnes på målet:\n\n1. Bit-for-bit duplikatsjekk (RAM):\nAppen sjekker filstørrelsene først, og hvis størrelsene samsvarer, sammenlignes filinnholdet bit-for-bit i RAM (krypterte filer sjekkes i minnet uten å skrive klartekst til disk). Under kopiering/flytting hoppes identiske filer over og originalen forblir urørt i kildemappen. Når du slår sammen et album under kryptering/dekryptering, kan du velge om 100% bit-identiske kildefiler skal slettes eller beholdes.\n\n2. Samme navn, ulikt innhold (f.eks. to forskjellige download.jpg-filer):\n• Helautomatisk (Standard): Beholder begge filene uten å avbryte deg ved å gi den nye filen navnet download(1).jpg, download(2).jpg.\n• Smart dialog: Hopper fortsatt stille over bit-identiske filer, og spør bare (Hopp over / Skriv over / Behold begge) når to filer har samme navn, men ulikt innhold. |
| 436 | `syncing_vault_progress` | Syncing vault from external drive… | Synkroniserer hvelv fra ekstern stasjon… |
| 437 | `vault_sync_summary_message` | Vault sync complete:\n• Copied: %1$d\n• Renamed (name conflict): %2$d\n• Skipped (identical): %3$d\n• Failed: %4$d | Hvelvsynkronisering fullført:\n• Kopiert: %1$d\n• Gi nytt navn (navnekonflikt): %2$d\n• Hoppet over (identisk): %3$d\n• Mislyktes: %4$d |
| 438 | `vault_sync_nothing_to_sync` | No pending CLI-encrypted media (.pending_sync) was found in the selected folder. | Ingen ventende CLI-krypterte medier (.pending_sync) ble funnet i den valgte mappen. |
| 439 | `vault_sync_same_folder_error` | The selected source folder is the same as your active vault. | Den valgte kildemappen er den samme som det aktive hvelvet ditt. |
| 440 | `use_for_this_album` | Use for this album only | Bruk kun for dette albumet |
| 441 | `crypto_duplicate_source_mode_label` | When a file already exists in destination during encrypt/decrypt | Når en fil allerede finnes i målmappen under kryptering/dekryptering |
| 442 | `crypto_duplicate_source_ask` | Always ask (Default) | Spør alltid (Standard) |
| 443 | `crypto_duplicate_source_keep` | Keep file in source folder | Behold filen i kildemappen |
| 444 | `crypto_duplicate_source_delete` | Delete file from source folder | Slett filen fra kildemappen |
| 445 | `crypto_duplicate_source_title` | File Already Exists in Destination | Filen finnes allerede i målmappen |
| 446 | `crypto_duplicate_source_encrypt_message` | Some of the files you are encrypting already exist in the destination folder. What should happen to the files in the source folder? | Noen av filene du krypterer finnes allerede i målmappen. Hva skal skje med filene i kildemappen? |
| 447 | `crypto_duplicate_source_decrypt_message` | Some of the files you are decrypting already exist in the destination folder. What should happen to the files in the source folder? | Noen av filene du dekrypterer finnes allerede i målmappen. Hva skal skje med filene i kildemappen? |
| 448 | `crypto_duplicate_source_encrypt_keep_warning` | Warning: Choosing \"Keep\" for unencrypted files will leave the source file unencrypted on disk. | Advarsel: Hvis du velger «Behold» for ukrypterte filer, vil kildefilen bli liggende ukryptert på disken. |
| 449 | `crypto_duplicate_source_always_ask` | Always ask | Spør alltid |
| 450 | `export_encrypted_vault` | Export encrypted vault | Eksporter kryptert hvelv |
| 451 | `export_vault_only_header` | Export folder containing only .vault file | Eksporter mappe som kun inneholder .vault-fil |
| 452 | `export_vault_entire_folder` | Export entire folder | Eksporter hele mappen |
| 453 | `export_vault_only_header_info` | To add media items encrypted on other devices using the supported CLI tool to the encrypted vault on your phone, you only need to use a folder named Secrets containing the .vault file. | For å legge til medieelementer som er kryptert på andre enheter med det støttede CLI-verktøyet i det krypterte hvelvet på telefonen din, trenger du bare å bruke en mappe kalt Secrets som inneholder .vault-filen. |
| 454 | `exporting_vault_progress` | Exporting encrypted vault… | Eksporterer kryptert hvelv… |
| 455 | `vault_export_same_folder_error` | The export destination cannot be the active vault folder or inside it. | Eksportmålet kan ikke være den aktive hvelvmappen eller ligge inne i den. |
| 456 | `conflict_handling_standard_dialog_summary` | Standard dialog | Standard dialog |
| 457 | `conflict_handling_smart_dialog_summary` | Smart dialog | Smart dialog |
| 458 | `conflict_handling_auto_summary` | Automatic (Experimental) | Automatisk (eksperimentelt) |
| 459 | `conflict_handling_standard_dialog_short` | Standard dialog (Default — Checks filename only, like original Fossify Gallery) | Standard dialog (standard — sjekker bare filnavnet, som i det originale Fossify Gallery) |
| 460 | `skipped_encrypt_files_mode_label` | Manage skipped files during encryption | Håndter filer som hoppes over under kryptering |
| 461 | `skipped_encrypt_files_info_message` | Choose whether files skipped in the source folder during encryption should be kept or deleted for security. This choice also applies to album conflicts during album encryption (see setting below) when the source album is merged into an existing album at the destination. | Velg om filer som hoppes over i kildemappen under kryptering skal beholdes eller slettes av sikkerhetshensyn. Dette valget gjelder også ved albumkonflikter under albumkryptering (se innstillingen nedenfor), når kildealbumet slås sammen med et eksisterende album på målet. |
| 462 | `skipped_crypto_files_ask` | Always ask (Default) | Alltid spørre (standard) |
| 463 | `skipped_crypto_files_keep` | Keep | Behold |
| 464 | `skipped_crypto_files_delete` | Delete | Slett |
| 465 | `skipped_crypto_files_encrypt_message` | The files you skipped were not transferred. For your privacy, you can delete these files. | Filene du hoppet over ble ikke overført. Av personvernhensyn kan du slette disse filene. |
| 466 | `skipped_crypto_files_decrypt_message` | The files you skipped were not transferred. You can delete these files. | Filene du hoppet over ble ikke overført. Du kan slette disse filene. |
| 467 | `skipped_crypto_files_identical_encrypt_message` | Files that already exist in the destination folder were not transferred. For your privacy, you can delete these files. | Filer som allerede finnes i målmappen ble ikke overført. Av personvernhensyn kan du slette disse filene. |
| 468 | `skipped_crypto_files_identical_decrypt_message` | Files that already exist in the destination folder were not transferred. You can delete these files. | Filer som allerede finnes i målmappen ble ikke overført. Du kan slette disse filene. |
| 469 | `skipped_crypto_files_both_encrypt_message` | The files you skipped and the files that already exist in the destination folder were not transferred. For your privacy, you can delete these files. | Filene du hoppet over og filene som allerede finnes i målmappen ble ikke overført. Av personvernhensyn kan du slette disse filene. |
| 470 | `skipped_crypto_files_both_decrypt_message` | The files you skipped and the files that already exist in the destination folder were not transferred. You can delete these files. | Filene du hoppet over og filene som allerede finnes i målmappen ble ikke overført. Du kan slette disse filene. |
| 471 | `skipped_crypto_files_always_ask` | Always ask | Alltid spørre |
| 472 | `delete_skipped_files_confirm_title` | Are you sure? | Er du sikker? |
| 473 | `delete_skipped_files_confirm_message` | Your files will be deleted! | Filene dine blir slettet! |
| 474 | `date_tie_break_label` | When content is identical but dates differ | Når innholdet er identisk, men datoene er ulike |
| 475 | `date_tie_break_keep_target` | Keep the existing file\'s date (default) | Behold datoen til den eksisterende filen (standard) |
| 476 | `date_tie_break_use_source` | Use the source file\'s date | Bruk datoen til kildefilen |
| 477 | `date_tie_break_ask` | Always ask | Alltid spørre |
| 478 | `date_tie_break_hint` | Applies only to Smart dialog and Automatic mode. The Standard dialog is not affected. | Gjelder kun for Smart dialog og automatisk modus. Standard dialog påvirkes ikke. |
| 479 | `date_tie_break_ask_info_title` | Why "Always ask" disables Smart dialog and Automatic mode | Hvorfor «Alltid spørre» deaktiverer Smart dialog og automatisk modus |
| 480 | `date_tie_break_ask_info_message` | Smart dialog and Automatic mode exist for one reason: to skip files whose content is already present, so you only see the files that really need your attention — same name, different content. That is exactly the case where comparing thumbnails can reveal bit-rot.\n\nIf you choose "Always ask" here, every identical file with a different date comes back to you as a conflict. In Smart dialog you will see two thumbnails of the very same picture, with nothing to verify, and you can decide there. Automatic (Experimental) mode never shows a dialog, so "Always ask" is not available there — selecting Automatic falls back to "Keep the existing file's date".\n\nUse this option only if you deliberately want to review every date difference, for example to fix a wrong date by hand.\n\nNote: when content is bit-identical, the EXIF capture date is identical too. A date difference means one side lost its EXIF data and fell back to the file modification date (the copy date). | Smart dialog og automatisk modus finnes av én grunn: å hoppe over filer hvis innhold allerede er til stede, slik at du bare ser filene som virkelig trenger din oppmerksomhet — samme navn, ulikt innhold. Det er nettopp i slike tilfeller at sammenligning av minibilder kan avdekke bit-forråtnelse.\n\nHvis du velger «Alltid spørre» her, vil hver identisk fil med en annen dato komme tilbake til deg som en konflikt. I Smart dialog ser du to minibilder av helt samme bilde, uten noe å verifisere, og kan bestemme der. Automatisk modus (eksperimentell) viser aldri en dialog, så «Alltid spørre» er ikke tilgjengelig der – når du velger Automatisk, falles det tilbake til «Behold datoen til den eksisterende filen».\n\nBruk dette alternativet bare hvis du bevisst vil gå gjennom hvert eneste datoforskjell, for eksempel for å rette en feil dato manuelt.\n\nMerk: når innholdet er bit-identisk, er EXIF-opptaksdatoen også identisk. En datoforskjell betyr at den ene siden mistet EXIF-dataene sine og falt tilbake til filens endringsdato (kopieringsdatoen). |
| 481 | `smart_conflict_counter` | %1$s (%2$d / %3$d) | %1$s (%2$d / %3$d) |
| 482 | `smart_conflict_incoming_label` | Incoming (New) | Innkommende (ny) |
| 483 | `smart_conflict_existing_label` | Existing (Target) | Eksisterende (mål) |
| 484 | `smart_conflict_corrupted_unreadable` | Corrupted or unreadable | Skadet eller uleselig |
| 485 | `smart_conflict_integrity_failed` | Integrity check failed (Bit-rot / Corrupted) | Integritetskontroll mislyktes (bit-forråtnelse / skadet) |
| 486 | `smart_conflict_tap_to_preview` | Tap either thumbnail to inspect full-size preview | Trykk på et av minibildene for å se forhåndsvisning i full størrelse |
| 487 | `smart_conflict_comparing` | Comparing file contents and dates… | Sammenligner filinnhold og datoer… |
| 488 | `smart_conflict_content_differs_warning` | File contents differ.\nIf these two files were supposed to be the same, data corruption may have occurred. Keeping both and inspecting them is recommended. | Filinnholdet er ulikt.\nHvis disse to filene skulle vært like, kan datakorruptasjon ha oppstått. Det anbefales å beholde begge og undersøke dem. |
| 489 | `smart_conflict_date_taken_differs` | File contents are identical, creation (date taken) timestamps differ. | Filinnholdet er identisk, men tidsstemplene for opprettelse (opptaksdato) er ulike. |
| 490 | `smart_conflict_modified_date_differs` | File contents are identical, modification dates differ. | Filinnholdet er identisk, men endringsdatoene er ulike. |
| 491 | `smart_conflict_both_dates_differ` | File contents are identical, creation and modification dates differ. | Filinnholdet er identisk, men opprettelses- og endringsdatoene er ulike. |
| 492 | `smart_conflict_meta_date_taken` | Taken: %s | Opptatt: %s |
| 493 | `smart_conflict_meta_date_modified` | Modified: %s | Endret: %s |
| 494 | `album_type_icons` | Album type icons | Ikoner for albumtype |
| 495 | `virtual_folders` | Virtual albums | Virtuelle album |
| 496 | `favorites_album` | Favorites | Favoritter |
| 497 | `excluded_folders_album` | Excluded folders | Ekskluderte mapper |
| 498 | `hidden_folders_album` | Hidden folders | Skjulte mapper |
| 499 | `temporarily_show_hidden_folders` | Temporarily show hidden folders | Vis skjulte mapper midlertidig |
| 500 | `stop_showing_hidden_folders` | Stop showing hidden folders | Slutt å vise skjulte mapper |
| 501 | `unexclude` | Stop excluding | Slutt å ekskludere |
| 502 | `hide_folder` | Hide | Skjul |
| 503 | `unhide_folder` | Unhide | Vis igjen |
| 504 | `about_title` | About Calypso Gallery | Om Calypso Gallery |
| 505 | `about_version` | Version %1$s (%2$s) | Versjon %1$s (%2$s) |
| 506 | `about_fork_description` | Calypso Gallery is a security-hardened, independently maintained personal fork of Fossify Gallery. Thanks to the original authors and contributors of Fossify Gallery. It is an unofficial project and is not affiliated with, endorsed by, or supported by the Fossify organization. | Calypso Gallery er en uavhengig vedlikeholdt, uoffisiell personlig fork av Fossify Gallery. Den er ikke tilknyttet eller støttet av Fossify. |
| 507 | `about_modification_notice` | This distribution is a modified version of Fossify Gallery v1.14.2 (2688360, 07-11-2026). All fork-specific modifications are documented in FORK_CHANGELOG.md. | Denne distribusjonen er en modifisert versjon av Fossify Gallery. Forken ble opprettet fra oppstrøms commit 054228be2da275396eb03d2b9002807b5055afac (1.14.1-5-g054228be2), og alle forkspesifikke endringer er dokumentert i FORK_CHANGELOG.md. |
| 508 | `about_license_heading` | License | Lisens |
| 509 | `about_gpl_notice` | This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 3 of the License.\n\nThis program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.\n\nYou should have received a copy of the GNU General Public License along with this program. If not, use the license action below or visit https://www.gnu.org/licenses/. | Dette programmet er fri programvare: du kan redistribuere det og/eller endre det i henhold til vilkårene i GNU General Public License, slik den er publisert av Free Software Foundation, versjon 3 av lisensen.\n\nDette programmet distribueres i håp om at det vil være nyttig, men UTEN NOEN GARANTI; uten engang den underforståtte garantien for SALGBARHET eller EGNETHET FOR ET BESTEMT FORMÅL. Se GNU General Public License for flere detaljer.\n\nDu skal ha mottatt en kopi av GNU General Public License sammen med dette programmet. Hvis ikke, bruk lisenshandlingen nedenfor eller besøk https://www.gnu.org/licenses/. |
| 510 | `about_read_gpl_license` | Read the GNU GPL v3 license | Les GNU GPL v3-lisensen |
| 511 | `about_read_tink_license` | Read the Tink Apache 2.0 license and attribution | Les Tink Apache 2.0-lisensen og tilskrivningen |
| 512 | `about_source_heading` | Source code | Kildekode |
| 513 | `about_corresponding_source_notice` | The complete corresponding source for this fork is kept in the fork repository. Tagged releases also have a matching source archive. The repository contains the build scripts, dependency declarations, and complete fork history needed to inspect and rebuild the project. | Den fullstendige tilsvarende kildekoden for denne forken finnes i fork-repositoriet. Merkede utgivelser har også et tilhørende kildearkiv. Repositoriet inneholder byggeskript, avhengighetserklæringer og fullstendig forkhistorikk som trengs for å granske og bygge prosjektet på nytt. |
| 514 | `about_fork_source` | Fork source code and corresponding source | Forkens kildekode og tilsvarende kilde |
| 515 | `about_release_sources` | Release source archives | Kildearkiver for utgivelser |
| 516 | `about_fork_issues` | Fork issue tracker | Feilsporing for forken |
| 517 | `about_upstream_source` | Upstream Fossify Gallery source code | Kildekode for oppstrøms Fossify Gallery |
| 518 | `about_upstream_project` | Upstream Fossify project | Oppstrøms Fossify-prosjektet |
| 519 | `about_help_heading` | Help and legal information | Hjelp og juridisk informasjon |
| 520 | `about_faq` | Frequently asked questions | Ofte stilte spørsmål |
| 521 | `about_third_party_licenses` | Third-party licenses | Lisenser fra tredjeparter |
| 522 | `gpl_license_title` | GNU General Public License v3 | GNU General Public License v3 |
| 523 | `tink_license_title` | Tink license and attribution | Tink-lisens og tilskrivning |
| 524 | `fork_disclaimer_title` | Fork notice | Merknad om forken |
| 525 | `fork_disclaimer_message` | This application is not developed by the Fossify team. Calypso Gallery is an independently maintained, unofficial fork of Fossify Gallery; the original developer is referenced solely for attribution. It is not affiliated with, endorsed by, or supported by Fossify.\n\nBy continuing you acknowledge this notice. Fork-specific changes are documented in FORK_CHANGELOG.md. | Denne applikasjonen er ikke utviklet av Fossify-teamet. Calypso Gallery er en uavhengig vedlikeholdt, uoffisiell fork av Fossify Gallery; den opprinnelige utvikleren nevnes kun for tilskrivning. Den er ikke tilknyttet, godkjent eller støttet av Fossify.\n\nVed å fortsette bekrefter du at du er kjent med denne meldingen. Forkspesifikke endringer er dokumentert i FORK_CHANGELOG.md. |
| 526 | `fork_disclaimer_acknowledge` | I understand | Jeg forstår |
| 527 | `argon2_profile_title` | Brute-Force resistance | Brute-force-motstand |
| 528 | `brute_force_mode_title` | Calibration mode | Kalibreringsmodus |
| 529 | `brute_force_mode_time` | Via vault unlock time | Via opplåsingstid for hvelvet |
| 530 | `brute_force_mode_presets` | Via ready-made modes | Via forhåndsdefinerte moduser |
| 531 | `brute_force_time_title` | Targeted vault unlock time | Målsetting for opplåsingstid for hvelvet |
| 532 | `brute_force_time_hint` | Select the time spent during opening the encrypted vault. Keeping this value high provides extra security by making brute-force attacks harder. | Velg tiden som brukes på å åpne det krypterte hvelvet. Å holde denne verdien høy gir ekstra sikkerhet ved å gjøre brute force-angrep vanskeligere. |
| 533 | `brute_force_time_min_hint` | The lower bound is this device's safe standard time. | Nedre grense er denne enhetens sikre standardtid. |
| 534 | `brute_force_time_value_device` | %1$.1f s (on this device) | %1$.1f s (på denne enheten) |
| 535 | `brute_force_time_out_of_range` | This time is above the selectable maximum (10 s). Tap to change it. | Denne tiden er over det valgbare maksimumet (10 s). Trykk for å endre den. |
| 536 | `brute_force_time_value` | %1$.1f s | %1$.1f s |
| 537 | `brute_force_actual_params` | Stored: %1$d MiB / %2$d rounds | Lagret: %1$d MiB / %2$d runder |
| 538 | `brute_force_clamped` |  (clamped) |  (begrenset) |
| 539 | `argon2_profile_low` | Low-end device (not recommended) | Lavendt enhet (anbefales ikke) |
| 540 | `argon2_profile_default` | Default | Standard |
| 541 | `argon2_profile_high` | High security | Høy sikkerhet |
| 542 | `argon2_profile_hint` | If you don\'t know what you\'re doing, it is recommended to keep the default setting. This only affects newly created vaults and password changes. | Hvis du ikke vet hva du gjør, anbefales det å beholde standardinnstillingen. Dette påvirker bare nyopprettede hvelv og passordendringer. |
| 543 | `argon2_profile_current` | Current: %s | Gjeldende: %s |
| 544 | `show_pin_dots` | Show a dot for each PIN digit | Vis én prikk for hvert siffer når PIN-koden tastes inn |

## Settings Headers, Font Options & File Encryption Info

Added/overridden in `values-nb-rNO/strings.xml` for Settings section headers, font picker options (`Monospace` standardized), and file-only encryption info dialog:

| # | Key (`name`) | English | Norwegian Bokmål (`values-nb-rNO/strings.xml`) |
|--:|---|---|---|
| 1 | `color_customization` | Look & feel | Utseende og følelse |
| 2 | `application_settings` | Application | Applikasjon |
| 3 | `video_playback_settings` | Video playback | Videoavspilling |
| 4 | `thumbnails_and_scrolling_settings` | Thumbnails & scrolling | Minibilder og rulling |
| 5 | `media_viewer_settings` | Media viewer | Medieviser |
| 6 | `security_and_vault_settings` | Security & encrypted vault | Sikkerhet og kryptert hvelv |
| 7 | `font_system_default` | System default | Systemstandard |
| 8 | `font_monospace` | Monospace | Monospace |
| 9 | `app_font` | App font | App-skrifttype |
| 10 | `select_font_file` | Select font file… | Velg skrifttypefil… |
| 11 | `encrypt_files_info_message` | Folder & File Structure:\n• When an unencrypted album is encrypted, it is converted into an encrypted subfolder inside the vault (Secrets).\n• Both the folder name and all file names/contents inside it are encrypted, so nothing is readable outside the app. | Mappe- og filstruktur:\n• Når et ukryptert album krypteres, gjøres det om til en kryptert undermappe inne i hvelvet (Secrets).\n• Både mappenavnet og alle filnavn og filinnhold inni det krypteres, slik at ingenting er lesbart utenfor appen. |

## Missing UI strings added (this revision)

`org.fossify:commons:7.1.3` does not provide these keys for Norwegian Bokmål, and `unlocking_encrypted_vault` was missing from the app-owned resources, so local overrides were added to `values-nb-rNO/strings.xml`.

| Key (`name`) | English source | Norwegian Bokmål | Notes |
|---|---|---|---|
| `search_in_placeholder` | Search in %s | Søk i %s | fossify-commons override; `%s` = album/folder name |
| `open_biometric_dialog` | Open biometric ID verification dialog | Åpne dialog for biometrisk ID-verifisering | fossify-commons override; Biometrics tab of the inherited security dialog |
| `authenticate` | Authenticate | Autentiser | fossify-commons override; system biometric prompt title |
| `unlocking_encrypted_vault` | Unlocking encrypted vault… | Låser opp det krypterte hvelvet… | App-owned key; consistent with `hvelv` terminology |

## Appearance — Custom Colors & Saved Themes (9 keys)

Added to `values-nb-rNO/strings.xml` for the Appearance settings **Custom colors** section and **Saved themes** workflow (English source strings in `values/strings.xml` were also completed):

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `custom_colors` | Custom colors | Egendefinerte farger | Appearance settings section header |
| 2 | `saved_themes` | Saved themes | Lagrede temaer | Appearance settings row and dialog title |
| 3 | `save_theme` | Save theme | Lagre tema | Positive dialog button to save current custom theme |
| 4 | `theme_saved` | Theme saved | Tema lagret | Toast shown after saving a custom theme |
| 5 | `theme_already_saved` | This theme is already saved | Dette temaet er allerede lagret | Toast shown when the current palette is already saved or built-in |
| 6 | `no_saved_themes` | No saved themes yet | Ingen lagrede temaer ennå | Empty state message in the Saved themes dialog |
| 7 | `custom_theme_name` | Custom theme | Egendefinert tema | Base name for newly saved custom themes (suffixed with index) |
| 8 | `discard_changes_question` | Do you want to save your changes? | Vil du lagre endringene dine? | Prompt shown when switching themes with unsaved custom color changes |
| 9 | `continue_without_saving` | Continue without saving | Fortsett uten å lagre | Negative dialog button to discard unsaved custom color changes |

## Onboarding & Full-Screen Permission Flow (29 keys)

Added to `values-nb-rNO/strings.xml` for the 3-screen first-launch welcome and full-screen permission flow (unsupported system locales fall back cleanly to English in `values/strings.xml`). Also removed the retired `auto_backup_prompt_message` key since auto-backup is now enabled by default and always shown in Settings.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `onboarding_motto` | No sign-up, no cloud, just your gallery. | Ingen registrering, ingen sky, bare galleriet ditt. | Welcome screen tagline |
| 2 | `onboarding_offline_title` | Completely Offline | Helt frakoblet | Welcome card 1 title |
| 3 | `onboarding_offline_desc` | Never connects to the internet. Your data belongs only to you. | Kobler seg aldri til internett. Dataene dine tilhører bare deg. | Welcome card 1 description |
| 4 | `onboarding_vault_title` | Encrypted Vault | Kryptert hvelv | Welcome card 2 title |
| 5 | `onboarding_vault_desc` | Encrypt your photos and videos. No one else can access them. | Krypter bildene og videoene dine. Ingen andre kan få tilgang. | Welcome card 2 description |
| 6 | `onboarding_albums_title` | Playlists for your photos | Spillelister for bildene dine | Welcome card 3 title |
| 7 | `onboarding_albums_desc` | Virtual albums let you bring the files you want to see together in a single album, without moving them between folders or creating copies on disk. Unlike a regular copy, they use no extra storage because only the original file is kept on disk. You can add the same file to as many albums as you like, while your folder structure stays exactly as it is. | Virtuelle album lar deg samle filene du vil se sammen i ett enkelt album, uten å flytte dem mellom mapper eller lage kopier på disken. I motsetning til vanlig kopiering bruker de ikke ekstra lagringsplass, siden bare originalfilen finnes på disken. Du kan legge samme fil til så mange album du vil, mens mappestrukturen din forblir uendret. | Welcome card 3 description |
| 8 | `onboarding_media_permission_note` | Media access permission is required to display your photos and videos. | Tilgang til medier er nødvendig for å vise bildene og videoene dine. | Welcome footer media permission note |
| 9 | `onboarding_continue` | Continue | Fortsett | Welcome primary button |
| 10 | `onboarding_media_required_title` | Full media access required | Full medietilgang kreves | Media permission screen title |
| 11 | `onboarding_media_required_desc` | Calypso Gallery needs full access to display all your photos and videos. | Calypso Gallery trenger full tilgang for å vise alle dine bilder og videoer. | Media permission screen description |
| 12 | `onboarding_media_steps_header` | How to enable permission | Slik gir du tilgang | Settings steps card header |
| 13 | `onboarding_media_step_1` | Settings | Innstillinger | Settings guide step 1 |
| 14 | `onboarding_media_step_2` | Permissions | Tillatelser | Settings guide step 2 |
| 15 | `onboarding_media_step_3` | Photos and videos | Bilder og videoer | Settings guide step 3 |
| 16 | `onboarding_media_step_4` | Allow all | Tillat alle | Settings guide step 4 (final) |
| 17 | `onboarding_media_required_hint` | Once access is granted, your gallery will open automatically when you return to the app. | Når tilgang er gitt, åpnes galleriet automatisk når du går tilbake til appen. | Media permission screen return hint |
| 18 | `onboarding_go_to_settings` | Go to settings | Gå til innstillinger | Media permission primary button |
| 19 | `onboarding_close` | Close | Lukk | Media permission secondary button |
| 20 | `onboarding_all_files_title` | All files access | Tilgang til alle filer | All files access screen title |
| 21 | `onboarding_all_files_desc` | All files access is recommended for the Encrypted Vault and seamless file management. | Tilgang til alle filer anbefales for kryptert hvelv og sømløs filbehandling. | All files access screen subtitle |
| 22 | `onboarding_all_files_vault_title` | Encrypted Vault and hidden folders | Kryptert hvelv og skjulte mapper | All files card 1 title |
| 23 | `onboarding_all_files_vault_desc` | Manage your encrypted files and hidden folders without system restrictions. | Administrer krypterte filer og skjulte mapper uten systembegrensninger. | All files card 1 description |
| 24 | `onboarding_all_files_backup_title` | Don\'t lose your Favorites and Virtual Albums | Ikke mist favorittene og de virtuelle albumene dine | All files card 2 title |
| 25 | `onboarding_all_files_backup_desc` | Keep your favorites and virtual albums automatically backed up on your device even if you accidentally uninstall the app. You can turn off automatic backup anytime in app settings. | Selv om du ved et uhell sletter appen, sikkerhetskopieres favorittene og de virtuelle albumene dine automatisk på enheten. Du kan når som helst slå av automatisk sikkerhetskopiering i appinnstillingene. | All files card 2 description |
| 26 | `onboarding_all_files_offline_title` | Still completely on your device | Fortsatt helt på enheten din | All files card 3 title |
| 27 | `onboarding_all_files_offline_desc` | The app stays completely offline, this permission is used only for local file operations. | Appen fortsetter å fungere frakoblet, denne tillatelsen brukes kun til lokale filoperasjoner. | All files card 3 description |
| 28 | `onboarding_grant_access` | Grant access | Gi tilgang | All files primary button |
| 29 | `onboarding_not_now` | Not now | Ikke nå | All files secondary button |

## Vault Lock & Album Header Polish (4 keys)

Added to `values-nb-rNO/strings.xml` for the redesigned Vault Lock screen biometric button and album interior header.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `use_biometrics` | Use biometrics | Bruk biometri | Vault lock screen biometric button label |
| 2 | `media_header_items_count` | %d items | %d elementer | MediaActivity album header item count |
| 3 | `media_header_virtual_album` | Virtual album · %d items | Virtuelt album · %d elementer | MediaActivity virtual album header subtitle |
| 4 | `media_header_encrypted_album` | Encrypted album · %d items | Kryptert album · %d elementer | MediaActivity encrypted album header subtitle |
## Appearance Live Preview (4 keys)

Added to `values-nb-rNO/strings.xml` for the live Appearance preview card and WCAG contrast warning.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `appearance_preview` | Preview | Forhåndsvisning | Appearance live preview section header |
| 2 | `appearance_preview_text` | Your memories, your colors | Dine minner, dine farger | Appearance preview sample headline |
| 3 | `appearance_preview_button` | Sample button | Eksempelknapp | Appearance preview sample button |
| 4 | `appearance_contrast_warning` | Low contrast: text %1$.1f:1, button %2$.1f:1. Increase it for readability. | Lav kontrast: tekst %1$.1f:1, knapp %2$.1f:1. Øk kontrasten for bedre lesbarhet. | WCAG contrast warning in Appearance preview |

## Vault Privacy Input Toggle (1 key)

Added to `values-nb-rNO/strings.xml` for the optional shoulder-surfing protection switch (default off).

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `hide_vault_key_presses` | Hide key presses when entering vault password | Skjul tastetrykk ved inntasting av hvelvpassord | Settings switch to hide PIN/password key press feedback |

## PIN Dot Indicator Setting (1 key)

Added to `values-nb-rNO/strings.xml` for the optional per-digit PIN dot indicator (default off; only shown for PINs of at most 14 digits).

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `show_pin_dots` | Show a dot for each PIN digit | Vis én prikk for hvert siffer når PIN-koden tastes inn | Settings switch; hidden for alphanumeric vaults and PINs longer than 14 digits |

## KDF Unlock-Time Hint Revision (this revision)

Updated `brute_force_time_hint` and added `brute_force_time_min_hint` in `values-nb-rNO/strings.xml`. The lower-bound sentence is appended only when the device's measured safe floor is above 1.0 s.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_time_hint` | Select the time spent during opening the encrypted vault. Keeping this value high provides extra security by making brute-force attacks harder. | Velg tiden som brukes på å åpne det krypterte hvelvet. Å holde denne verdien høy gir ekstra sikkerhet ved å gjøre brute force-angrep vanskeligere. | Explanation shown in the target vault unlock time dialog |
| 2 | `brute_force_time_min_hint` | The lower bound is this device's safe standard time. | Nedre grense er denne enhetens sikre standardtid. | Appended only when minSec > 1.0 |

## Biometric Unavailability Explanation (this revision)

Added to `values-nb-rNO/strings.xml`. The Settings row now stays visible when a vault exists; if the device only has weak (Class 2) biometrics or cannot create a hardware-backed key, the row is disabled and these strings explain why in a dialog and under the row. They replace the short `no_biometric_hardware_or_enrolled` toast. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `vault_biometric_unavailable_title` | Biometric vault access unavailable | Biometrisk tilgang til hvelvet er ikke tilgjengelig | Dialog title |
| 2 | `vault_biometric_unavailable_no_hardware` | This device has no strong biometric hardware (fingerprint or Class 3 face unlock). Vault biometric access is unavailable; use your PIN or password. | Denne enheten har ikke sterk biometrisk maskinvare (fingeravtrykk eller ansiktsgjenkjenning i klasse 3). Biometrisk tilgang til hvelvet er ikke tilgjengelig; bruk PIN-koden eller passordet ditt. | `BIOMETRIC_ERROR_NO_HARDWARE` |
| 3 | `vault_biometric_unavailable_none_enrolled` | No strong biometric is enrolled. Weak face unlock is not accepted; enrol a fingerprint or a Class 3 face unlock in system settings. | Det er ikke registrert sterk biometri. Svak ansiktsgjenkjenning godtas ikke; registrer et fingeravtrykk eller ansiktsgjenkjenning i klasse 3 i systeminnstillingene. | `BIOMETRIC_ERROR_NONE_ENROLLED`; weak face unlock rejected |
| 4 | `vault_biometric_unavailable_hw_unavailable` | The biometric sensor is temporarily unavailable. Try again later. | Den biometriske sensoren er midlertidig utilgjengelig. Prøv igjen senere. | `BIOMETRIC_ERROR_HW_UNAVAILABLE` |
| 5 | `vault_biometric_unavailable_security_update` | Biometrics require a security update before they can be used. | Biometri krever en sikkerhetsoppdatering før den kan brukes. | `BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED` |
| 6 | `vault_biometric_unavailable_no_secure_key` | A hardware-backed key (TEE/StrongBox) could not be created on this device. Vault biometric access is disabled for security reasons. | Det kunne ikke opprettes en maskinvarebeskyttet nøkkel (TEE/StrongBox) på denne enheten. Biometrisk tilgang til hvelvet er deaktivert av sikkerhetsgrunner. | `canAuthenticate()` succeeded but no TEE/StrongBox key |
| 7 | `vault_biometric_unavailable_generic` | Vault biometric access is unavailable on this device. Enrol a fingerprint or a strong face unlock in system settings, or use your PIN or password. | Biometrisk tilgang til hvelvet er ikke tilgjengelig på denne enheten. Registrer et fingeravtrykk eller sterk ansiktsgjenkjenning i systeminnstillingene, eller bruk PIN-koden eller passordet ditt. | Fallback explanation |

## Password Reminder Strings (this revision)

Added/updated in `values-nb-rNO/strings.xml`. The reminder is shown in-app when the vault is opened and no password/PIN unlock happened during the configured interval. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `vault_password_reminder` | Remind me of my password | Påminn meg om passordet mitt |  |
| 2 | `vault_password_reminder_disabled` | Off | Av |  |
| 3 | `vault_password_reminder_weeks` | %d weeks | %d uker |  |
| 4 | `vault_password_reminder_months` | %d months | %d måneder |  |
| 5 | `vault_password_reminder_dialog_title` | Reminder interval | Påminnelsesintervall |  |
| 6 | `vault_password_reminder_dialog_message` | If you do not open the encrypted vault with your password for this long, we will remind you of it. | Hvis du ikke åpner det krypterte hvelvet med passordet ditt på så lenge, minner vi deg om det. |  |
| 7 | `vault_password_reminder_setup_note` | We will send you periodic reminders so you do not forget your password. You can turn this off in Settings → Encrypted vault → Remind me of my password. | Vi sender deg jevnlige påminnelser slik at du ikke glemmer passordet ditt. Du kan slå dette av under Innstillinger → Kryptert hvelv → Påminn meg om passordet mitt. |  |
| 8 | `vault_biometric_setup_password_note` | If you choose to sign in with biometrics, remember that you still need to know your password. | Hvis du velger å logge inn med biometri, må du huske at du fortsatt må kjenne passordet ditt. |  |
| 9 | `vault_password_saved_title` | Have you saved your password? | Har du lagret passordet ditt? |  |
| 10 | `vault_password_saved_message` | If you forget your password, you will permanently lose access to your encrypted vault. Make sure you have saved it somewhere before continuing. Using a password manager is recommended. | Hvis du glemmer passordet ditt, mister du permanent tilgang til det krypterte hvelvet ditt. Sørg for at du har lagret det et sted før du fortsetter. Bruk av en passordbehandler anbefales. |  |
| 11 | `vault_password_saved_confirm` | Yes, I saved it | Ja, jeg har lagret det |  |
| 12 | `vault_password_saved_later` | I will save it | Jeg skal lagre det |  |
| 13 | `vault_password_reminder_due_title` | Do you still remember your vault password? | Husker du fortsatt hvelvpassordet ditt? |  |
| 14 | `vault_password_reminder_due_message` | You have not opened the encrypted vault with your password for this long. Your password is the only way to recover the vault. | Du har ikke åpnet det krypterte hvelvet med passordet ditt på så lenge. Passordet ditt er den eneste måten å gjenopprette hvelvet på. |  |
| 15 | `vault_password_reminder_verify_title` | Confirm your password | Bekreft passordet ditt |  |
| 16 | `vault_password_reminder_verify_message` | This is not a vault unlock. We only want to confirm that you still know your password. | Dette er ikke en hvelvopplåsing. Vi vil bare bekrefte at du fortsatt kjenner passordet ditt. |  |
| 17 | `vault_password_reminder_verified` | Password confirmed | Passord bekreftet |  |
| 18 | `vault_password_reminder_not_now` | Not now | Ikke nå |  |
| 19 | `vault_password_reminder_forgot_title` | Do not remember your password? | Husker du ikke passordet ditt? |  |
| 20 | `vault_password_reminder_forgot_steps` | 1. Unlock the vault with biometrics.\n2. Copy (export) your encrypted data — this does not move the vault.\n3. Settings → Reset encrypted vault to set a new password and restore.\n\nIf biometrics cannot unlock it either, the vault is inaccessible. You can archive the current vault and create a new one, copy the encrypted data for another device, or delete the vault folder at %1$s with a file manager (cannot be undone). | Lås opp hvelvet med biometri, eksporter de krypterte dataene dine, og bruk deretter Innstillinger → Nullstill kryptert hvelv for å angi et nytt passord og gjenopprette dataene. Hvis biometri heller ikke kan låse opp hvelvet, er det utilgjengelig. |  |
| 21 | `vault_password_reminder_verify_action` | Verify my password | Bekreft passordet mitt |  |
| 22 | `vault_password_reminder_forgot_action` | I don't remember | Jeg husker ikke |  |
| 23 | `vault_password_reminder_open_settings` | Open settings | Åpne innstillinger |  |
| 24 | `empty_encrypted_vault` | Reset encrypted vault | Nullstill kryptert hvelv | Renamed label |

## Vault Archive & Forgot-Password Strings (this revision)

Added/updated in `values-nb-rNO/strings.xml`. Archive moves the old vault; export copies it; deletion is left to the file manager (ADR 0007). English source: `values/strings.xml`.

| # | Key (`name`) | English | Norwegian Bokmål |
|--:|---|---|---|
| 1 | `archive_vault` | Archive encrypted vault | Arkiver kryptert hvelv |
| 2 | `archive_vault_summary` | Moves the old vault to the archive and creates a new one | Flytter det gamle hvelvet til arkivet og lager et nytt |
| 3 | `archive_vault_confirm` | The current vault will be moved to the archive and a new vault will be created in the default location. Nothing is deleted. To use the vault on another device, use Export encrypted vault — it copies the files and the current vault stays where it is. | Det nåværende hvelvet flyttes til arkivet og et nytt opprettes på standardstedet. Ingenting slettes. For bruk på en annen enhet, bruk Eksporter kryptert hvelv — det kopierer og hvelvet blir liggende. |
| 4 | `archive_vault_success` | Vault archived. Location: %1$s | Hvelvet er arkivert. Plassering: %1$s |
| 5 | `archive_vault_failed` | Could not archive the vault. Nothing was changed. | Kunne ikke arkivere hvelvet. Ingenting ble endret. |
| 6 | `select_archived_vault` | Select from archived vaults | Velg fra arkiverte hvelv |
| 7 | `archived_vault_indexed` | %1$d. archived vault | %1$d. arkivert hvelv |
| 8 | `archived_vault_unknown_date` | Encrypted vault with unknown date | Kryptert hvelv med ukjent dato |
| 9 | `archived_vault_invalid` | Invalid archive entry | Ugyldig arkivoppføring |
| 10 | `archived_vault_invalid_message` | Vault header not found at %1$s. You can delete this folder with a file manager. | Fant ikke hvelvheader i %1$s. Du kan slette mappen med en filbehandler. |
| 11 | `copy_path` | Copy path | Kopier sti |
| 12 | `faq_forgot_password_title` | I forgot my encrypted vault password, what can I do? | Jeg har glemt passordet til det krypterte hvelvet, hva kan jeg gjøre? |
| 13 | `faq_forgot_password_text` | 1. Unlock the vault with biometrics.\n2. Copy (export) your encrypted data — copying is not moving; the original stays in place.\n3. Settings → Reset encrypted vault to set a new password and restore.\n\nWarning: if biometrics cannot unlock it either, the vault is inaccessible. You can archive the current vault and create a new one, copy the encrypted data for another device, or delete the vault folder at %1$s with a file manager. Deletion cannot be undone. | 1. Lås opp med biometri. 2. Kopier (eksporter) de krypterte dataene — kopiering er ikke flytting. 3. Innstillinger → Nullstill kryptert hvelv for nytt passord.  Advarsel: hvis biometri heller ikke virker, er hvelvet utilgjengelig. Du kan arkivere, kopiere eller slette mappen på %1$s med en filbehandler. |
| 14 | `vault_password_reminder_forgot_steps` | 1. Unlock the vault with biometrics.\n2. Copy (export) your encrypted data — this does not move the vault.\n3. Settings → Reset encrypted vault to set a new password and restore.\n\nIf biometrics cannot unlock it either, the vault is inaccessible. You can archive the current vault and create a new one, copy the encrypted data for another device, or delete the vault folder at %1$s with a file manager (cannot be undone). | 1. Lås opp med biometri. 2. Kopier de krypterte dataene. 3. Innstillinger → Nullstill kryptert hvelv.  Hvis biometri ikke virker: arkiver, kopier eller slett mappen %1$s med en filbehandler. |

## Unarchive Strings (this revision)

Added to `values-nb-rNO/strings.xml`. Unarchive moves the vault back to its recorded original location; no swap, no password.

| # | Key | English | Norwegian Bokmål |
|--:|---|---|---|
| 1 | `unarchive_vault` | Unarchive vault | Ta ut av arkivet |
| 2 | `unarchive_vault_summary` | Moves the vault back to its location before archiving | Flytter hvelvet tilbake til plasseringen før arkivering |
| 3 | `unarchive_vault_confirm` | The vault will be moved back to its original location. No password is required. Continue? | Hvelvet flyttes tilbake til originalplasseringen. Ingen passord kreves. Fortsette? |
| 4 | `unarchive_vault_success` | The vault was moved back to its original location. | Hvelvet er flyttet tilbake. |
| 5 | `unarchive_vault_blocked` | Another vault is located where this vault used to be. Archive that vault first, then unarchive this one. | På hvelvets tidligere plassering ligger et annet hvelv. Arkiver det først, og ta deretter dette ut av arkivet. |
| 6 | `unarchive_vault_merge` | Combine data into one vault | Slå sammen data i ett hvelv |
| 7 | `unarchive_vault_merge_steps` | Unlock the archived vault with its password, export the files in decrypted form, then import them into the other vault. The existing conflict-handling modes apply. | Lås opp det arkiverte hvelvet med passord, eksporter filene dekryptert og importer dem i det andre hvelvet. |
| 8 | `archived_vault_activated` | The vault is open from the archive. Use Settings → Unarchive vault to move it back to its original location. | Hvelvet er åpnet fra arkivet. Bruk Innstillinger → Ta ut av arkivet. |

## Export Target Conflict Strings (this revision)

Added to `values-nb-rNO/strings.xml`. Export now targets `<picked>/Calypso Gallery/Secrets` and is blocked when the target holds a different vault.

| # | Key | English | Norwegian Bokmål |
|--:|---|---|---|
| 1 | `export_vault_different_vault` | The destination already contains a different encrypted vault. Export was cancelled to protect it. Choose an empty folder. | Målmappen inneholder allerede et annet kryptert hvelv. Eksporten ble avbrutt for å beskytte det. Velg en tom mappe. |
| 2 | `export_vault_unknown_content` | The destination contains encrypted files but no vault header. Export was cancelled to avoid mixing vaults. | Målmappen inneholder krypterte filer, men ingen hvelvheader. Eksporten ble avbrutt. |

## Existing Vault Time Recalculation (this revision)





Added `brute_force_time_value_device` and `brute_force_time_out_of_range` to `values-nb-rNO/strings.xml`. The Settings/vault-setup summary now shows the existing vault's real unlock time on the current device (measured on unlock, otherwise estimated from the portable `.vault` KDF cost), with a note when it exceeds the 10 s maximum, showing only the stored parameters when this device has no measurement yet.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_time_value_device` | %1$.1f s (on this device) | %1$.1f s (på denne enheten) | Value shown for an existing vault |
| 2 | `brute_force_time_out_of_range` | This time is above the selectable maximum (10 s). Tap to change it. | Denne tiden er over det valgbare maksimumet (10 s). Trykk for å endre den. | Shown when the real time exceeds 10 s |

## Silent identical-skip toast & Automatic mode date tie-break (this revision)

Added the `identical_files_skipped` plural and revised `date_tie_break_ask_info_message` in `values-nb-rNO/strings.xml`. Smart dialog and Automatic mode now report silently skipped, byte-identical files at the end of an operation with a short toast; identical files with different dates that were reviewed in the thumbnail conflict dialog are excluded. "Always ask" can no longer be selected in Automatic (Experimental) mode and falls back to keeping the existing file's date. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `identical_files_skipped` | one: "%d identical file skipped" / other: "%d identical files skipped" | one: "%d identisk fil hoppet over" / other: "%d identiske filer hoppet over" | Short end-of-operation toast in Smart/Automatic mode |
| 2 | `date_tie_break_ask_info_message` | Smart dialog and Automatic mode exist for one reason: to skip files whose content is already present, so you only see the files that really need your attention — same name, different content. That is exactly the case where comparing thumbnails can reveal bit-rot.\n\nIn Smart dialog you will see two thumbnails of the very same picture, with nothing to verify, and you can decide there. Automatic (Experimental) mode never shows a dialog, so "Always ask" is not available there — selecting Automatic falls back to "Keep the existing file's date".\n\nUse this option only if you deliberately want to review every date difference, for example to fix a wrong date by hand.\n\nNote: when content is bit-identical, the EXIF capture date is identical too. A date difference means one side lost its EXIF data and fell back to the file modification date (the copy date). | Smart dialog og automatisk modus finnes av én grunn: å hoppe over filer hvis innhold allerede er til stede, slik at du bare ser filene som virkelig trenger din oppmerksomhet — samme navn, ulikt innhold. Det er nettopp i slike tilfeller at sammenligning av minibilder kan avdekke bit-forråtnelse.\n\nHvis du velger «Alltid spørre» her, vil hver identisk fil med en annen dato komme tilbake til deg som en konflikt. I Smart dialog ser du to minibilder av helt samme bilde, uten noe å verifisere, og kan bestemme der. Automatisk modus (eksperimentell) viser aldri en dialog, så «Alltid spørre» er ikke tilgjengelig der – når du velger Automatisk, falles det tilbake til «Behold datoen til den eksisterende filen».\n\nBruk dette alternativet bare hvis du bevisst vil gå gjennom hvert eneste datoforskjell, for eksempel for å rette en feil dato manuelt.\n\nMerk: når innholdet er bit-identisk, er EXIF-opptaksdatoen også identisk. En datoforskjell betyr at den ene siden mistet EXIF-dataene sine og falt tilbake til filens endringsdato (kopieringsdatoen). | Revised: "Always ask" unavailable in Automatic; falls back to keep-target date |

## New strings (biometric setup, device-owner check, archive size)

| Key | Translation |
|---|---|
| `vault_biometric_setup_continue` | Fortsett |
| `device_owner_verify_title` | Bekreft enhetsinnehaver |
| `device_owner_verify_subtitle` | Bekreft med biometri eller enhets-PIN for å fortsette. Hvelvpassordet er ikke nødvendig. |
| `archive_total_size_title` | Arkivlagring |
| `archive_total_size` | Samlet størrelse på arkiverte hvelv: %1$s |

## Weak password confirmation

| Key | Translation |
|---|---|
| `weak_password_warning_title` | Svakt passord |
| `weak_password_warning_message` | Dette passordet anses som svakt. Et lengre og mer komplekst passord anbefales for sikkerheten til hvelvet. Vil du fortsette likevel? |
| `weak_password_warning_continue` | Fortsett likevel |

## Minimalist Settings, Appearance Redesign & Recent Fork Strings (this revision)

| Key | English | Norwegian Bokmål |
|---|---|---|
| `appearance_title` | Appearance | Utseende |
| `search_settings` | Search settings | Søk i innstillinger |
| `license_tink_title` | Google Tink (streaming AEAD and HKDF cryptography) | Google Tink (strømmende AEAD- og HKDF-kryptografi) |
| `license_rustcrypto_argon2_title` | RustCrypto Argon2 (native Argon2id key derivation) | RustCrypto Argon2 (innebygd Argon2id-nøkkelutledning) |
| `license_webpdecoder_title` | GlideWebpDecoder (animated WebP decoding) | GlideWebpDecoder (dekoding av animert WebP) |
| `license_jxl_coder_title` | JXL Coder Glide (JPEG XL support) | JXL Coder Glide (støtte for JPEG XL) |
| `license_androidsvg_title` | AndroidSVG (SVG rendering) | AndroidSVG (SVG-gjengivelse) |
| `license_exif_extended_title` | Android-Exif-Extended (EXIF metadata handling) | Android-Exif-Extended (håndtering av EXIF-metadata) |
| `license_material_icons_title` | Material Design Icons (UI icons) | Material Design Icons (grensesnittikoner) |
| `checking_password` | Checking password… | Kontrollerer passord… |
| `vault_same_password_title` | Same password | Samme passord |
| `vault_same_password_message` | The new password is identical to the current vault password. Please choose a different one. | Det nye passordet er likt det nåværende passordet for hvelvet. Velg et annet. |
| `brute_force_time_with_params` | %1$.1f s (%2$d MiB / %3$d rounds) | %1$.1f s (%2$d MiB / %3$d runder) |
| `brute_force_high_memory_warning` | Higher values use more memory. You may have trouble opening your vault on devices with 2 GB of RAM or less. If that happens, lower the unlock time in app settings and export the vault again. | Høyere verdier bruker mer minne. Du kan få problemer med å åpne hvelvet på enheter med 2 GB RAM eller mindre. Hvis dette skjer, senker du åpningstiden i appinnstillingene og eksporterer hvelvet på nytt. |
| `measuring_device_kdf_speed` | Measuring standard KDF speed on this device… | Måler standard KDF-hastighet på denne enheten… |
| `kdf_settings_updated_successfully` | Brute-Force resistance updated for encrypted vault | Motstand mot brute force oppdatert for det krypterte hvelvet |
| `appearance_automatic` | Automatic | Automatisk |
| `follow_system_theme` | Follow system theme | Følg systemtema |
| `follow_system_theme_summary` | Automatically switches between light and dark | Bytter automatisk mellom lyst og mørkt tema |
| `theme_midnight_blue` | Midnight Blue | Midnattsblå |
| `theme_warm_paper` | Warm Paper | Varmt papir |
| `theme_deep_forest` | Deep Forest | Dyp skog |
| `appearance_contrast_ok` | Good contrast · Text %1$.1f:1 · Button %2$.1f:1 | God kontrast · Tekst %1$.1f:1 · Knapp %2$.1f:1 |
| `appearance_backup_section` | Backup | Sikkerhetskopi |
| `backup_saved_themes_to_storage` | Back up saved themes to device storage | Sikkerhetskopier lagrede temaer til enhetslagring |
| `settings_group_general` | General | Generelt |
| `settings_group_folders_and_loading` | Folders and loading | Mapper og lasting |
| `settings_group_security` | Security | Sikkerhet |
| `settings_group_vault_management` | Vault management | Hvelvadministrasjon |
| `settings_group_playback` | Playback | Avspilling |
| `settings_group_gestures_and_tap` | Gestures and tap | Bevegelser og trykk |
| `settings_group_display_and_motion` | Display and motion | Visning og bevegelse |
| `settings_group_thumbnail_styles` | Thumbnail styles | Minibildestiler |
| `settings_group_general_behavior` | General behavior | Generell oppførsel |
| `settings_group_conflicts_and_crypto` | Conflicts and encryption rules | Konflikter og krypteringsregler |
| `settings_group_cleanup` | Cleanup | Opprydding |
| `settings_group_storage` | Storage | Lagring |
| `settings_group_settings_and_favorites` | Settings and favorites | Innstillinger og favoritter |
| `settings_group_vault_transfer` | Encrypted vault transfer | Overføring av kryptert hvelv |
| `settings_search_results` | Search results | Søkeresultater |
| `settings_no_search_results` | No matching settings found. | Fant ingen samsvarende innstillinger. |

## Active keys added since 2688360 (7 November) - added in this revision

The following strings were added after the base commit `2688360` (7 November) and are now documented here. English source: `values/strings.xml` (or the corresponding license/defaults resource). Target source: `values-nb-rNO/strings.xml`.

| # | Key (`name`) | English | nb-rNO (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `about_copyright_notice` | Copyright © Fossify Gallery contributors\nCopyright © 2026 Calypso Gallery contributors | *(translatable="false"; not localized)* | |
| 2 | `app_name` | Calypso Gallery | *(translatable="false"; not localized)* | |
| 3 | `auto_biometric_prompt_on_vault_return` | Automatically show biometric prompt when returning to app | Vis biometrisk bekreftelse automatisk når du går tilbake til appen | |
| 4 | `biometric_access_to_encrypted_vault` | Biometric access to encrypted vault | Biometrisk tilgang til kryptert hvelv | |
| 5 | `change_encrypted_vault_password` | Change encrypted vault password | Endre passord for kryptert hvelv | |
| 6 | `create_encrypted_vault` | Create encrypted vault | Opprett kryptert hvelv | |
| 7 | `delete_auto_backup_confirm` | Delete device storage backups for the %1$s created while this setting was enabled? | Vil du slette sikkerhetskopiene på enhetslagringen for %1$s som ble opprettet mens denne innstillingen var aktiv? | |
| 8 | `delete_auto_backup_target_both` | favorites and virtual albums | favoritter og virtuelle album | |
| 9 | `delete_auto_backup_target_favorites` | favorites | favoritter | |
| 10 | `delete_auto_backup_target_virtual_albums` | virtual albums | virtuelle album | |
| 11 | `empty_encrypted_vault_warning` | The encrypted vault (Secrets) and all its files will be permanently deleted. Are you sure? | Det krypterte hvelvet (Secrets) og alle filene i det vil bli slettet permanent. Er du sikker? | |
| 12 | `encrypted_vault` | Encrypted vault | Kryptert hvelv | |
| 13 | `encrypted_vault_emptied` | Encrypted vault emptied | Kryptert hvelv tømt | |
| 14 | `enter_new_password` | Enter new password | Skriv inn nytt passord | |
| 15 | `enter_new_pin` | Enter new PIN | Skriv inn ny PIN | |
| 16 | `fast_large_duplicate_check_info_message` | Why this matters for large copy/move transfers:\n\nWhen copying or moving large files, such as gigabytes of videos, exact bit-by-bit checking must read both the source and destination files from start to finish whenever filenames and sizes match—taking minutes on fast storage or much longer on SD cards.\n\nHow Fast Check works:\n• Files under 8 MB are ALWAYS checked 100% bit-by-bit.\n• For files over 8 MB, the app compares file size plus five 256 KB blocks distributed across the start, 25%, 50%, 75%, and end of the file (1.25 MB total per side).\n• For a 50 GB archive of 50 MB–1 GB videos, this cuts disk reads by ~40× to ~800×.\n\nWhen to enable or disable:\n• Recommended ON: If most of your gallery consists of large files (&gt;8 MB), two intact, distinct files sharing the same name, exact byte size, and all 5 sampled regions is virtually impossible in practice.\n• Recommended OFF (no speedup): If your gallery consists mostly of photos or files under 8 MB, this setting provides no significant speed gain since files under 8 MB are always checked 100% bit-by-bit.\n• Encrypt, decrypt, and external USB vault sync operations always perform full bit-by-bit verification. | Hvorfor dette er viktig ved store kopierings-/flytteoverføringer:\n\nNår du kopierer eller flytter store filer, som gigabyte med videoer, må nøyaktig bit-for-bit-sjekk lese både kilde- og målfilen fra start til slutt hver gang filnavn og størrelser samsvarer—noe som tar minutter på rask lagring eller mye lenger på SD-kort.\n\nSlik fungerer rask sjekk:\n• Filer under 8 MB sjekkes ALLTID 100% bit-for-bit.\n• For filer over 8 MB sammenligner appen filstørrelse pluss fem 256 KB-blokker fordelt på start, 25%, 50%, 75% og slutt av filen (1,25 MB totalt per side).\n• For et 50 GB-arkiv med 50 MB–1 GB-videoer reduserer dette disklesing med ~40× til ~800×.\n\nNår den bør aktiveres eller deaktiveres:\n• Anbefalt PÅ: Hvis mesteparten av galleriet ditt består av store filer (&gt;8 MB), er det i praksis nærmest umulig at to intakte, ulike filer deler samme navn, nøyaktig bytestørrelse og alle 5 utvalgte områder.\n• Anbefalt AV (ingen hastighetsgevinst): Hvis galleriet ditt hovedsakelig består av bilder eller filer under 8 MB, gir denne innstillingen ingen merkbar hastighetsgevinst siden filer under 8 MB alltid sjekkes 100% bit-for-bit.\n• Synkronisering av eksternt USB-hvelv forblir alltid fullt Tink AEAD-autentisert. | |
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
| 34 | `no_virtual_albums_yet` | No virtual albums yet. Tap \"New album\" to create one. | Ingen virtuelle album ennå. Trykk på \"Nytt album\" for å opprette et. | |
| 35 | `password_changed_successfully` | Password changed successfully | Passordet er endret | |
| 36 | `refresh` | Refresh | Oppdater | |
| 37 | `repeat_new_password` | Repeat new password | Gjenta nytt passord | |
| 38 | `repeat_new_pin` | Repeat new PIN | Gjenta ny PIN | |
| 39 | `resume_op_copy` | Copy | Kopiering | |
| 40 | `resume_op_decrypt` | Decryption | Dekryptering | |
| 41 | `resume_op_encrypt` | Encryption | Kryptering | |
| 42 | `resume_op_move` | Move | Flytting | |
| 43 | `resume_operation_aborted_message` | %1$s operation was aborted. Already completed files were kept. | %1$s-operasjonen ble avbrutt. Filer som allerede var fullført ble beholdt. | |
| 44 | `resume_operation_interrupted_message` | %1$s operation was interrupted (%2$d remaining item(s)). No data was lost — do you want to continue? | %1$s-operasjonen ble avbrutt (%2$d gjenstående elementer). Ingen data gikk tapt – vil du fortsette? | |
| 45 | `search_virtual_albums` | Search virtual albums | Søk i virtuelle album | |
| 46 | `skip_delete_confirmation` | Always skip delete confirmation dialog | Hopp alltid over dialogen for bekreftelse av sletting | |
| 47 | `vault_no_recovery_warning` | Offline encryption is used. If you forget your password or PIN, there is no way to recover your vault. | Frakoblet kryptering brukes. Hvis du glemmer passordet eller PIN-koden, finnes det ingen måte å gjenopprette hvelvet på. | |
| 48 | `vault_not_created_prompt` | You haven\'t created a vault yet. Do you want to create a vault? | Du har ikke opprettet et hvelv ennå. Vil du opprette et hvelv? | |
| 49 | `waiting_for_free_space_or_storage` | Waiting for free space or destination storage… | Venter på ledig plass eller mållagring… | |

## Vault setup tutorial & PIN-dot preview notice (this revision)

Added the six strings introduced by the vault setup-tutorial and PIN-dot live-preview design updates (`dba5068f0`, `4ea5b1f8f`) to `values-nb-rNO/strings.xml`. English source: `values/strings.xml`. The `&lt;b&gt;…&lt;/b&gt;` emphasis in `vault_pull_tutorial_message` and the literal `10 %` in `pin_dots_notice_note` are preserved exactly.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `vault_pull_tutorial_title` | Your encrypted vault is ready | Det krypterte hvelvet ditt er klart | One-time tutorial shown after vault creation |
| 2 | `vault_pull_tutorial_message` | While at the top of the list on the main screen, &lt;b&gt;pull down from the top&lt;/b&gt; and hold until the lock icon settles, then release. | Når du er øverst i listen på hovedskjermen, &lt;b&gt;dra ned fra toppen&lt;/b&gt; og hold til låseikonet har stabilisert seg, og slipp deretter. | `&lt;b&gt;` emphasis preserved |
| 3 | `vault_pull_tutorial_note` | For your privacy, the vault never appears as a separate folder or icon on the home screen; it only opens with this gesture. | Av personvernhensyn vises aldri hvelvet som en egen mappe eller et ikon på hjemskjermen; det åpnes bare med denne bevegelsen. | Privacy note |
| 4 | `vault_pull_tutorial_confirm` | Got it | Forstått | Positive button |
| 5 | `pin_dots_notice_title` | Alternative PIN appearance | Alternativ PIN-visning | One-time preview dialog before enabling PIN dots |
| 6 | `pin_dots_notice_note` | Anyone seeing your screen can count the empty rings to tell your PIN length. This reduces the number of passwords to try by about 10%. If you find having your PIN length known acceptable from a security standpoint, you can use this view. | Alle som ser skjermen din kan telle de tomme sirklene for å finne PIN-lengden din. Dette reduserer antall passord som må prøves med omtrent 10 %. Hvis du anser det som akseptabelt fra et sikkerhetsperspektiv at PIN-lengden din er kjent, kan du bruke denne visningen. | `10 %` kept with Norwegian spacing |

## Filename toggle, album search hint & extra viewer actions (this revision)

Added the five strings introduced by the dynamic filename toggle, album search hint, and extra fullscreen viewer actions updates (`e79197d7f`) to `values-nb-rNO/strings.xml`. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `show_filenames` | Show filenames | Vis filnavn | Media-view overflow toggle label when filenames are hidden |
| 2 | `hide_filenames` | Hide filenames | Skjul filnavn | Media-view overflow toggle label when filenames are visible |
| 3 | `search_in_album` | Search in album | Søk i album | Album-view search hint |
| 4 | `show_extra_viewer_actions` | Show extra actions on fullscreen media | Vis ekstra handlinger på fullskjermsmedier | Settings switch label |
| 5 | `manage_extra_viewer_actions` | Manage extra actions | Administrer ekstra handlinger | Settings row label and extra-actions dialog title |

## Secure sharing — metadata sanitization (this revision)

Added the five strings for the new **Settings → Security → Secure sharing** three-mode setting. A sanitized temporary copy is shared; the original media file is never modified. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `settings_group_secure_sharing` | Secure sharing | Sikker deling | New eyebrow in the Security category |
| 2 | `settings_share_metadata_title` | Clear metadata before sharing | Fjern metadata før deling | Settings row label and radio dialog title |
| 3 | `share_metadata_off` | Off | Av | No sanitization |
| 4 | `share_metadata_location_only` | Location only | Kun plassering | Default mode: strips GPS tags |
| 5 | `share_metadata_all` | All metadata | Alle metadata | Strips Make/Model/Software/DateTime/XMP too |

## File operation notifications (this revision)

Added the notification settings section (category heading, per-operation toggles, permission row and progress messages) introduced by the file-operation notification work to `values-nb-rNO/strings.xml`. Translated independently from the English source in `values/strings.xml`, matching the locale's existing terminology; the `…` ellipses are preserved exactly.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `notifications_settings` | Notifications | Varsler | Settings category label |
| 2 | `settings_group_operation_notifications` | File operation notifications | Varsler om filoperasjoner | Settings subcategory eyebrow |
| 3 | `notifications_enabled` | Show operation progress | Vis fremdrift for operasjoner | Settings switch label |
| 4 | `notify_copy_move` | Copy and move | Kopier og flytt | Per-operation toggle label |
| 5 | `notify_crypto` | Encrypt and decrypt | Krypter og dekrypter | Per-operation toggle label |
| 6 | `notify_delete` | Delete and recycle bin | Slett og papirkurv | Per-operation toggle label |
| 7 | `notify_virtual_album` | Virtual album updates | Oppdateringer av virtuelle album | Per-operation toggle label |
| 8 | `notifications_info` | Android requires a persistent notification while a background file operation runs. Detailed progress is only shown after you leave the app — while you are inside it, the on-screen dialog is used instead. File names, paths and vault contents are never included in notifications. | Android krever en vedvarende varsel mens en filoperasjon kjører i bakgrunnen. Detaljert fremdrift vises bare etter at du har forlatt appen – mens du er i appen, brukes dialogboksen på skjermen i stedet. Filnavn, baner og innholdet i hvelvet tas aldri med i varsler. | Explanatory text below the settings group |
| 9 | `notification_permission_label` | Notification permission | Varslingstillatelse | Android notification permission label |
| 10 | `notification_permission_granted` | Granted | Gitt | Permission status / positive action |
| 11 | `notification_permission_request` | Tap to allow | Trykk for å tillate | Permission request hint |
| 12 | `file_operations_notifications` | File operations | Filoperasjoner | Notification channel / section title |
| 13 | `file_operation_copy_move` | Copying / moving files… | Kopierer / flytter filer… | Progress notification title; `…` preserved |
| 14 | `file_operation_crypto` | Encrypting / decrypting files… | Krypterer / dekrypterer filer… | Progress notification title; `…` preserved |
| 15 | `file_operation_delete` | Deleting files… | Sletter filer… | Progress notification title; `…` preserved |
| 16 | `file_operation_virtual_album` | Updating albums… | Oppdaterer album… | Progress notification title; `…` preserved |
| 17 | `file_operation_generic` | File operation in progress… | Filoperasjon pågår… | Fallback progress notification title |
| 18 | `checking_files_before_transfer` | Checking files before transfer… | Sjekker filer før overføring… | Pre-transfer verification progress |
| 19 | `checking_files_before_crypto` | Checking files before encryption… | Sjekker filer før kryptering… | Pre-encryption verification progress |
| 20 | `deleting_files_progress` | Deleting files… | Sletter filer… | Deletion progress status |

## Unlock-time security levels (this revision)

Added the four fork-only `brute_force_level_*` keys used by the themed unlock-time dial in Settings and first-time vault setup. The label follows the chosen unlock time: 0–2.5 s fast, 2.5–5 s balanced, 5–7.5 s high, 7.5–10 s maximum. English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_level_fast` | Fast unlock | Rask opplåsing | 0–2.5 s |
| 2 | `brute_force_level_balanced` | Balanced security | Balansert sikkerhet | 2.5–5 s |
| 3 | `brute_force_level_high` | High security | Høy sikkerhet | 5–7.5 s |
| 4 | `brute_force_level_maximum` | Maximum protection | Maksimal beskyttelse | 7.5–10 s |

## Unlock-time level descriptions (this revision)

Added the four fork-only `brute_force_level_*_desc` keys shown under the themed unlock-time dial in Settings and first-time vault setup. The 0–2.5 s description is now `Hızlı kasa açılışı` / `Fast vault opening` (previously `Düşük KDF maliyeti`, which is no longer used). English source: `values/strings.xml`.

| # | Key (`name`) | English (`values/strings.xml`) | Norwegian Bokmål (`values-nb-rNO/strings.xml`) | Notes |
|--:|---|---|---|---|
| 1 | `brute_force_level_fast_desc` | Fast vault opening | Rask opplåsing av hvelvet | 0–2.5 s |
| 2 | `brute_force_level_balanced_desc` | Recommended range | Anbefalt område | 2.5–5 s |
| 3 | `brute_force_level_high_desc` | More memory usage | Økt minnebruk | 5–7.5 s |
| 4 | `brute_force_level_maximum_desc` | Memory warning active | Minneadvarsel aktiv | 7.5–10 s |

# Fossify Gallery Cipher - Live-Boot RAM-Only Vault Encryptor CLI

This **Encrypt-Only** offline Linux CLI tool encrypts plaintext media files (photos, GIFs, SVGs, RAWs, videos) into `.bin` format **100% compatible with the Android Fossify Gallery Cipher** application (`AES-256-CTR` + `Poly1305-AES` authenticated streaming encryption, plus `FGMETA2` AES-GCM encrypted album `.meta` folders).

> 🇹🇷 **Türkçe kılavuz** için sayfanın altındaki [Türkçe Kullanım Kılavuzu](#-türkçe-kullanım-kılavuzu) bölümüne geçebilirsiniz.

---

## 🛡️ Recommended Operating Systems (RAM-Only Live Boot)

This CLI tool is designed **exclusively for RAM-only Live Boot Linux environments**. Handling or deleting sensitive plaintext media on a standard disk-backed OS (Windows, macOS, or installed Linux on SSD/HDD) leaves permanent forensic traces in filesystem journals, swap/pagefile, thumbnail caches, and SSD wear-leveling blocks—even after deletion.

### 1. Primary Recommendation: **Tails OS** (Amnesic, Zero-Config)
* **Why Tails?** Everything runs strictly in volatile RAM (`tmpfs`/`overlayfs`), internal drives and swap partitions are never mounted automatically, and RAM is wiped on shutdown.
* Uses `apt` (supports Tails Persistent Storage + Additional Software for offline dependency persistence).

### 2. Alternatives: Copy-to-RAM (`toram`) Linux Live ISOs
If you prefer another Linux Live USB, boot with its **copy-to-RAM** and **no-swap** parameters so the entire OS lives in RAM:

| Distribution | Boot Parameters | Package Manager (`run.sh` auto-supported) |
| :--- | :--- | :--- |
| **Debian Live** | `boot=live toram noswap` | `apt` |
| **Ubuntu Live** | `toram noswap` | `apt` |
| **Fedora Live** | `rd.live.ram=1` | `dnf` |
| **Arch / EndeavourOS Live** | `copytoram=y` | `pacman` |
| **openSUSE Live** | `rd.live.ram=1` | `zypper` |

---

## 🔒 Built-In Per-Process Isolation

`run.sh` automatically wraps **only this single `cipher_vault.py` process** (without affecting any other Python processes on your system):
1. **`bwrap` (`bubblewrap`):** Cuts off all network access (`--unshare-net`) and restricts filesystem access strictly to system libraries, your Desktop, and mounted external drives / CLI-specified folders (blocking `~/.ssh`, `~/.gnupg`, `~/.config`, etc.).
2. **`firejail` (`--net=none`):** Used if `bwrap` is unavailable.
3. **`unshare --user --net`:** Kernel-level network namespace isolation fallback available on almost all Linux distributions.
4. **Python `sys.addaudithook`:** Defense-in-depth fallback inside `cipher_vault.py` that blocks socket/network operations unless `--adb` is explicitly passed.

---

## 💾 Two External-Drive Workflows (Tails OS RAM-Only)

### Workflow 1: Direct External Vault (`Cipher_Secrets` directly on USB/SD)
Use your external USB/SD drive as the vault itself from Android without copying encrypted media into your phone's internal storage:

1. **Stage plaintext media in RAM:** Download or place your plaintext photos/videos into `~/Desktop/To_Encrypt` (or `~/Desktop/Sifrelenecek`), which lives purely in volatile RAM (`tmpfs`) on Tails / `toram` Live OS.
2. **Plug in your external USB/SD drive:** Mount the drive containing `Cipher_Secrets/.vault` (e.g., `/media/amnesia/USB/Cipher_Secrets/.vault` or `/mnt/.../Cipher_Secrets/.vault`).
3. **Run `./run.sh`:** The CLI auto-detects the mounted `.vault` (or lets you drag & drop it). After unlocking with your password/PIN/pattern, choose **Option `[1]`** to encrypt **directly into `Cipher_Secrets/` on the external drive** (or pass `-o /media/.../Cipher_Secrets -v /media/.../Cipher_Secrets/.vault`).
4. **Done:** Encrypted `.bin` files and `.meta` album subfolders are written straight to the external vault. Optionally zero-wipe the RAM input files (`--delete-source` or interactive prompt), then connect the USB/SD drive directly to your phone.

### Workflow 2: Phone Vault + External Drive In-App Sync
Keep your primary `Cipher_Secrets` vault on your phone while encrypting new media inside Tails OS onto an external USB/SD drive, then merge them safely inside the Android app without using a file manager to dig into internal vault folders.

#### Scenario 2A — External Drive Holds a Full Copy of `Cipher_Secrets`
Use this when you keep a mirror/copy of your phone's `Cipher_Secrets` folder (including `.vault` and encrypted `<hex>/.meta` album folders) on your USB drive:
1. **Stage plaintext media in Tails RAM:** Place new files in `~/Desktop/To_Encrypt` (or `~/Desktop/Sifrelenecek`):
   * Files placed directly in the root folder route to **`Default Secret Album`**.
   * Files placed inside named subfolders (e.g., `To_Encrypt/Vacation/photo.jpg`) match or create that album name in the vault.
2. **Encrypt onto the USB copy:** Plug in the USB drive, run `./run.sh`, enter your vault password/PIN/pattern, and select **Option `[1]`** to encrypt directly into `<USB>/Cipher_Secrets` (the CLI matches existing `.meta` albums on the USB copy automatically or creates new encrypted album folders). Optionally zero-wipe the RAM input files.
3. **Sync into your phone inside the Android app:** Connect the USB drive to your phone, open **Fossify Gallery Cipher**, go to **Settings > Encrypted vault > Sync vault from external drive** (*"Harici diskten kasayı eşitle"*), unlock your vault if prompted, and select `<USB>/Cipher_Secrets` (or the USB root).
   * The app skips all already-present files on the phone (even if unchanged `.bin`s or re-encrypted copies of identical media exist), copies only the newly added items into matching or newly created albums (and `Default Secret Album`), and renames any same-name/different-content conflict safely to `<name>(1)`.

#### Scenario 2B — External Drive Has NO Vault Copy (Delta-Only Transfer)
Use this when you do **not** want a copy of your existing vault media on the USB drive—you only want to carry the newly encrypted additions:
1. **One-time `.vault` header preparation:** You only need the small `.vault` key-header file (`Internal Storage > Fossify Gallery Cipher > Cipher_Secrets > .vault`)—**none** of your vault photos/videos need to be copied. Keep that `.vault` file on the USB drive or copy it to `~/Desktop/` in Tails OS so the CLI can derive your vault's Master Key (`.vault` is protected by Argon2id + your password/PIN).
2. **Stage & encrypt only the new media:** Put the new plaintext files into `~/Desktop/To_Encrypt` (directly in root for `Default Secret Album`, or inside named subfolders like `To_Encrypt/Vacation/` for specific albums), run `./run.sh`, and unlock with your vault password/PIN.
   * Choose **Option `[2]`** (`~/Desktop/Encrypted_Output`, then copy the generated output folder—containing `.meta` subfolders and/or `.bin` files—onto your USB drive), OR choose **Option `[3]`** (`-o /media/amnesia/USB/To_Sync`) to write only the newly encrypted delta folders/files directly onto the USB drive.
   * Note: Every generated `<6-hex>` subfolder holds a `.meta` file storing the real album name (for example `Vacation`), OR you can even put bare `.bin` files / `.meta` subfolders directly in your USB transfer folder.
3. **Sync into your phone inside the Android app:** Connect the USB drive to your phone, open **Settings > Encrypted vault > Sync vault from external drive** (*"Harici diskten kasayı eşitle"*), and pick the delta folder on your USB drive (e.g., `Encrypted_Output` / `To_Sync`).
   * The Android app reads each subfolder's `.meta` (matching existing phone vault albums by real name or creating new 6-hex encrypted albums if none exist yet), routes bare `.bin` files without an album folder into **`Default Secret Album`**, skips duplicates, and renames different-content collisions to `<name>(1)`.

---

## 📁 Supported Media Verification & Folder Routing

### Pre-Encryption Supported Media Check
Before encrypting, `cipher_vault.py` checks every file against the exact gallery-supported media extensions used by Android `CryptoManager` (`isSupportedMediaName` / `org.fossify.commons`):
* **Photos:** `.jpg`, `.jpeg`, `.png`, `.webp`, `.heic`, `.heif`, `.apng`, `.avif`, `.bmp`, `.wbmp`, `.ico`, `.jxl`, `.jfif`, `.pjpeg`, `.pjp`
* **GIF & SVG:** `.gif`, `.svg`
* **RAW:** `.dng`, `.cr2`, `.cr3`, `.nef`, `.nrw`, `.arw`, `.srf`, `.sr2`, `.orf`, `.rw2`, `.pef`, `.raf`, `.srw`, `.x3f`, `.erf`, `.mef`, `.mos`, `.mrw`, `.dcr`, `.kdc`, `.3fr`, `.fff`, `.iiq`, `.rwl`
* **Videos:** `.mp4`, `.mkv`, `.webm`, `.avi`, `.3gp`, `.3gpp`, `.3g2`, `.mov`, `.m4v`, `.ts`, `.mts`, `.m2ts`, `.qt`, `.flv`, `.f4v`, `.wmv`, `.asf`, `.mpg`, `.mpeg`, `.mpe`, `.vob`, `.ogv`, `.rm`, `.rmvb`, `.divx`

Unsupported files (such as `.txt`, `.pdf`, `.md`, `.zip`) as well as `.bin` files and hidden files starting with `.` are skipped before encryption, logged with a warning, and counted in the summary.

### Automatic Album Routing & `.meta` Matching
* **Root Files → `Default Secret Album`:** Files placed directly in the input folder root (no subfolder) are routed automatically into **`Default Secret Album`** (`__VAULT_DEFAULT_ALBUM__`). If your vault already has a Default Secret Album folder, it is reused; otherwise, a new randomized 6-character hex subfolder with `.meta` (`FGMETA2`) is created.
* **Subfolders → `.meta`-Aware Album Matching:** For files inside subfolders (e.g., `To_Encrypt/Vacation/img.jpg`), the CLI decrypts existing `.meta` files in the target vault:
  * If an encrypted album with that name already exists (exact or case-insensitive trimmed match), new `.bin` files are placed directly into that existing hex folder.
  * If no matching album exists yet, a new randomized 6-character hex folder with encrypted `.meta` (`FGMETA2`) is created.
  * `.nomedia` is placed at the vault root (`Cipher_Secrets/.nomedia`), **not** inside normal album subfolders, matching Android's `ExcludedMenuPolicy` so your albums appear normally in the unlocked vault. (Only explicitly dot-prefixed hidden albums like `.Private` receive an inner `.nomedia`.)
  * Nested subfolders (e.g., `To_Encrypt/Vacation/Day1/img.jpg`) flatten cleanly into their top-level album name (`Vacation`) so all vault folders stay 1 level deep inside `Cipher_Secrets/`.
* **Mixed Inputs:** Having some files in the input root and others in subfolders works seamlessly in a single pass.
* **Virtual Albums (`JSON/`) Are Independent:** Virtual Albums stored in `Cipher_Secrets/JSON/` are completely untouched and disconnected from `cipher_vault.py` — they are managed solely inside the Android app.

---

## 🌐 Language & Desktop Folder Workflow

On interactive launch, the tool asks you to choose **English** or **Türkçe** (or pass `--lang en` / `--lang tr`), and uses matching Desktop folders:

| Language Flag | Default Input Folder | Output Destination Options |
| :--- | :--- | :--- |
| `--lang en` | `~/Desktop/To_Encrypt` | `[1]` Detected `Cipher_Secrets` vault, `[2]` `~/Desktop/Encrypted_Output`, `[3]` Custom folder |
| `--lang tr` | `~/Desktop/Sifrelenecek` | `[1]` Algılanan `Cipher_Secrets` kasası, `[2]` `~/Desktop/Sifreli_Cikti`, `[3]` Özel klasör |

---

## 🚀 Quick Start & CLI Examples

1. **Mount Vault or Copy `.vault`:** Plug in your external USB/SD drive containing `Cipher_Secrets/.vault` (or your `.vault` file), or copy `.vault` from your phone (`Internal Storage > Fossify Gallery Cipher > Cipher_Secrets > .vault`) to your Desktop/USB.
2. **Run Launcher:**
   ```bash
   chmod +x run.sh cipher_vault.py
   ./run.sh
   ```
   *(Automatically detects `apt`, `dnf`, `pacman`, or `zypper` if dependencies are missing, creates the Desktop shortcut, and starts the sandboxed CLI.)*

```bash
# Interactive mode (prompts for Language, Vault destination [1/2/3], and optional secure wipe)
./run.sh

# English or Turkish interactive folder workflow (-e/--encrypt is optional default)
./run.sh --lang en
./run.sh --lang tr

# Encrypt directly into an external USB/SD drive's Cipher_Secrets vault
./run.sh --lang en -i ~/Desktop/To_Encrypt -o /media/amnesia/USB/Cipher_Secrets -v /media/amnesia/USB/Cipher_Secrets/.vault

# Encrypt delta batch onto USB for Android "Sync vault from external drive"
./run.sh --lang en -i ~/Desktop/To_Encrypt -o /media/amnesia/USB/Encrypted_Output -v ~/Desktop/.vault

# Specify .vault (-v or -k), 3x3 pattern indices, and wipe plaintext sources after encryption
./run.sh -v ~/.vault --pattern "0,1,2,5" --delete-source

# Encrypt and push directly to connected phone via ADB (bypasses network sandbox)
./run.sh --adb
```

---

## 🇹🇷 Türkçe Kullanım Kılavuzu

Bu **yalnızca şifreleme yapan (Encrypt-Only)** çevrimdışı Linux CLI aracı, medya dosyalarınızı (fotoğraf, GIF, SVG, RAW, video) Android **Fossify Gallery Cipher** uygulamasıyla **%100 uyumlu** şekilde `.bin` formatında (`AES-256-CTR` + `Poly1305-AES`) ve `FGMETA2` şifreli `.meta` albümleriyle şifreler.

### 🛡️ RAM Tabanlı Live Boot (Tails OS / `toram`)
Bu CLI aracı **yalnızca RAM üzerinde çalışan Live Boot Linux sistemler** için tasarlanmıştır. Standart diske kurulu işletim sistemlerinde (Windows, macOS veya SSD/HDD kurulu Linux) şifresiz medyalar silinse bile dosya sistemi günlüğünde, swap/pagefile alanında, küçük resim önbelleklerinde ve SSD bloklarında kalıcı adli izler bırakır.
* **Önerilen Sistem:** **Tails OS** (Tüm çalışma geçici RAM'dedir, dahili disk ve swap otomatik bağlanmaz, kapatınca her şey silinir).
* **Alternatif:** Debian/Ubuntu (`toram noswap`), Fedora/openSUSE (`rd.live.ram=1`) veya Arch (`copytoram=y`) Live ISO'ları.

### 💾 İki Harici Disk İş Akışı (Tails OS RAM Üzerinde)

#### 1. Doğrudan Harici Disk Kasası (Direct External Vault)
Telefonun dahili depolamasına kopyalama yapmadan, Android uygulamasından harici USB/SD diskinizdeki `Cipher_Secrets` klasörünü doğrudan kasa olarak kullanın:
1. Şifrelenecek şifresiz medyaları RAM üzerindeki `~/Desktop/Sifrelenecek` (veya `To_Encrypt`) klasörüne koyun.
2. İçinde `Cipher_Secrets/.vault` bulunan harici USB/SD diskinizi takın (örn. `/media/amnesia/USB/Cipher_Secrets/.vault`).
3. `./run.sh` aracını başlatın. Araç harici kasayı otomatik algılar; şifre/PIN/desen ile kilidi açtıktan sonra **Seçenek `[1]`** ile **doğrudan harici diskteki `Cipher_Secrets` klasörünün içine** şifreleme yapın (veya `-o /media/.../Cipher_Secrets -v /media/.../Cipher_Secrets/.vault` kullanın).
4. Şifreleme bitince RAM'deki kaynak dosyaları güvenli şekilde sıfırlayıp silebilir ve USB/SD diski doğrudan telefonunuza takabilirsiniz.

#### 2. Telefon Kasası + Harici Disk Uygulama İçi Senkronizasyon (Phone Vault + External Drive In-App Sync)
Ana kasanızı telefonunuzda tutup, Tails OS'te yeni medyaları harici diske şifreleyin ve dosya yöneticisiyle telefon klasörlerine girmeden doğrudan Android uygulamasının içinden birleştirin.

##### Senaryo 2A — Harici Diskte Kasanın Tam Kopyası (`Cipher_Secrets`) Varsa
USB diskinizde telefon kasanızın bir kopyası (`.vault` ve mevcut şifreli `<hex>/.meta` albüm klasörleri) duruyorsa:
1. **Tails OS'te şifresiz medyaları hazırlayın:** Yeni dosyaları `~/Desktop/Sifrelenecek` (veya `To_Encrypt`) içine bırakın:
   * Klasörün köküne konan dosyalar **`Default Secret Album`**'e gider.
   * Alt klasör (ör. `Sifrelenecek/Tatil/foto.jpg`) içine konan dosyalar kasadaki o albümle eşleştirilir veya yeni şifreli albüm oluşturur.
2. **USB kopyasının üzerine şifreleyin:** USB diski takın, `./run.sh` aracını çalıştırın, kasa şifrenizi/PIN'inizi girin ve **Seçenek `[1]`** ile doğrudan `<USB>/Cipher_Secrets` üzerine şifreleme yapın. Araç USB'deki mevcut `.meta` albümlerini tanıyıp yenileri ilgili klasörlere ekler. İsterseniz RAM'deki kaynak dosyaları temizletin.
3. **Telefonda uygulama içinden eşitleyin:** USB diski telefonunuza bağlayın, uygulamada **Ayarlar > Şifreli Kasa > Harici diskten kasayı eşitle** seçeneğine dokunun, istenirse kasanın kilidini açın ve USB'deki `Cipher_Secrets` klasörünü (veya doğrudan USB kökünü) seçin.
   * Uygulama telefondaki kasada zaten bulunan eski/aynı dosyaları içerik kontrolüyle **otomatik atlar**, yalnızca yeni eklenenleri ilgili albümlere (veya `Default Secret Album`'e) aktarır ve aynı adda ama farklı içerikte bir dosya varsa üzerine yazmadan `<ad>(1)` şeklinde yeniden adlandırır.

##### Senaryo 2B — Harici Diskte Kasa Kopyası YOKSA (Yalnızca Değişiklik / Delta Aktarımı)
Harici diskinizde eski şifreli dosyalarınızın kopyasını tutmak istemiyor, USB'yi sadece yeni şifrelediğiniz eklemeleri taşımak için kullanıyorsanız:
1. **Tek seferlik `.vault` başlık dosyası:** Harici diskte tüm kasa medyalarının bulunmasına gerek yoktur; aracın kasanızın şifreleme anahtarını türetebilmesi için yalnızca telefonunuzdaki küçük `.vault` dosyasına (`Dahili Depolama > Fossify Gallery Cipher > Cipher_Secrets > .vault`) ihtiyacınız vardır. Bu `.vault` dosyasını USB diske veya Tails Masaüstüne kopyalayın (parolanız olmadan tek başına açılamaz).
2. **Yalnızca yeni dosyaları şifreleyip USB'ye alın:** Yeni dosyaları `~/Desktop/Sifrelenecek` içine koyun (doğrudan köke koyduklarınız `Default Secret Album`'e, `Tatil/` gibi alt klasörlere koyduklarınız ilgili albüme göre hazırlanır) ve `./run.sh` ile kasa şifrenizi girin:
   * **Seçenek `[2]`** ile Masaüstündeki `Sifreli_Cikti` (`Encrypted_Output`) klasörüne yazıp bu klasörü (içindeki `.meta` alt klasörlerini ve/veya `.bin` dosyalarını) USB diske kopyalayın **veya** **Seçenek `[3]`** ile doğrudan USB diskteki bir aktarım klasörüne (`-o /media/amnesia/USB/Eklenecek`) çıktı alın.
   * Not: Oluşan her alt klasör içinde gerçek albüm adını şifreli tutan `.meta` dosyası bulunur; doğrudan çıktı köküne koyduğunuz `.bin` dosyaları veya `.meta` alt klasörleri aktarım için yeterlidir.
3. **Telefonda uygulama içinden eşitleyin:** USB diski telefona bağlayın, **Ayarlar > Şifreli Kasa > Harici diskten kasayı eşitle** seçeneğini açıp USB'deki aktarım klasörünü (`Sifreli_Cikti` / `Eklenecek`) seçin.
   * Uygulama alt klasörlerdeki `.meta` etiketlerini çözerek telefondaki **aynı adlı albümlere otomatik ekler** (telefonda henüz o isimde bir albüm yoksa yenisini şifreli olarak oluşturur); herhangi bir alt klasörde olmayan çıplak `.bin` dosyalarını ise doğrudan **`Default Secret Album`** içine yerleştirir. Aynı içerikli dosyalar atlanır, farklı içerikli ad çakışmaları `<ad>(1)` olarak korunur.

### 📁 Desteklenen Medya Kontrolü ve Otomatik Albüm Yönlendirmesi
* **Şifreleme Öncesi Format Doğrulaması:** Yalnızca Android uygulamasının desteklediği fotoğraf, GIF, SVG, RAW ve video uzantıları şifrelenir. `.txt`, `.pdf`, `.md`, `.zip` gibi desteklenmeyen dosyalar ile `.bin` ve `.` ile başlayan gizli dosyalar uyarı verilerek atlanır ve özet raporda belirtilir.
* **Kök Klasör Dosyaları → `Default Secret Album`:** Girdi klasörünün doğrudan köküne bırakılan dosyalar (alt klasörsüz) otomatik olarak **`Default Secret Album`** (`__VAULT_DEFAULT_ALBUM__`) albümüne şifrelenir. Kasada zaten varsa mevcut klasör yeniden kullanılır, yoksa `.meta` (`FGMETA2`) içeren yeni rastgele 6 haneli hex klasörü oluşturulur.
* **Alt Klasörler → `.meta` İle Akıllı Eşleştirme:** Alt klasörlerdeki dosyalar (örn. `Sifrelenecek/Tatil/foto.jpg`) için hedefteki kasanın mevcut `.meta` dosyaları çözülür:
  * Aynı isimde (birebir veya büyük/küçük harf duyarsız) albüm zaten varsa dosyalar mevcut şifreli klasöre eklenir.
  * Yoksa rastgele 6 haneli yeni hex klasörü ve şifreli `.meta` (`FGMETA2`) oluşturulur.
  * `.nomedia` yalnızca kasa kökünde (`Cipher_Secrets/.nomedia`) bulunur; normal albüm klasörlerinin içine yazılmaz (böylece Android uygulaması albümleri gizlenmiş/hariç tutulmuş saymaz). Yalnızca `.` ile başlayan gizli alt klasörler için iç `.nomedia` oluşturulur.
  * İç içe alt klasörler (örn. `Tatil/1_Gun/foto.jpg`) Android'in tek seviyeli kasa yapısına uygun olarak en üst albüm adına (`Tatil`) yönlendirilir.
* **Karma Girdi:** Aynı çalıştırmada hem kökte hem alt klasörlerde dosya bulunması sorunsuz desteklenir.
* **Sanal Albümler (`JSON/`):** Kasa içindeki `JSON/` sanal albümleri `cipher_vault.py` aracından tamamen bağımsızdır, dokunulmaz ve yalnızca Android uygulaması içinde yönetilir.

### 🚀 Hızlı Başlangıç ve Komut Örnekleri
```bash
chmod +x run.sh cipher_vault.py
./run.sh

# Türkçe arayüz ile etkileşimli şifreleme
./run.sh --lang tr

# Harici USB kasasına doğrudan şifreleme
./run.sh --lang tr -i ~/Desktop/Sifrelenecek -o /media/amnesia/USB/Cipher_Secrets -v /media/amnesia/USB/Cipher_Secrets/.vault

# Harici USB'deki delta klasörüne şifreleme (Uygulama içi "Harici diskten kasayı eşitle" için)
./run.sh --lang tr -i ~/Desktop/Sifrelenecek -o /media/amnesia/USB/Sifreli_Cikti -v ~/Desktop/.vault
```

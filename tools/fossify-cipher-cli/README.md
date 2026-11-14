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

## 💾 Unified `Cipher_Secrets` External-Drive Workflows (Tails OS RAM-Only)

Both workflows use **one unified folder structure (`Cipher_Secrets/`)** on your external USB/SD drive, with **no interactive output destination prompt** in `cipher_vault.py`:

### Workflow 1: Direct External Vault (`Cipher_Secrets` directly on USB/SD)
Use your external USB/SD drive as the vault itself from Android without copying encrypted media into your phone's internal storage:

1. **Stage plaintext media in RAM:** Download or place your plaintext photos/videos into `~/Desktop/To_Encrypt` (or `~/Desktop/Sifrelenecek`), which lives purely in volatile RAM (`tmpfs`) on Tails / `toram` Live OS.
2. **Plug in your external USB/SD drive:** Mount the drive containing `Cipher_Secrets/.vault` (e.g., `/media/amnesia/USB/Cipher_Secrets/.vault` or `/mnt/.../Cipher_Secrets/.vault`).
3. **Run `./run.sh`:** The CLI auto-detects `.vault`, prompts for your vault password/PIN/pattern, and **automatically writes** newly encrypted `.meta` (`FGMETA2`) album folders and `.bin` payloads directly into `<USB>/Cipher_Secrets/` (with zero output prompt — or pass `-o` / `--output` to override).
4. **Done:** Optionally zero-wipe the RAM input files (`--delete-source` or interactive prompt), then connect the USB/SD drive directly to your phone.

### Workflow 2: Phone Vault + Unified `Cipher_Secrets` External Sync
Keep your primary `Cipher_Secrets` vault on your phone while encrypting new media inside Tails OS onto an external USB/SD drive, then merge them inside the Android app without manually copying files with a file manager:

1. **Prepare `Cipher_Secrets/` on the external USB/SD drive (pick either setup):**
   * **Scenario 1 — Full Copy of `Cipher_Secrets` on USB:** Copy your phone's `Cipher_Secrets` folder (`.vault` + existing encrypted `<hex>/.meta` album folders) onto the USB drive.
   * **Scenario 2 — Header-Only `.vault` on USB (No Existing Vault Media on USB):** Create `Cipher_Secrets/` on the USB drive and place **only** your small `.vault` key-header file inside it (`<USB>/Cipher_Secrets/.vault`), with none of your phone's media copied onto the USB drive.
     * *(Tip: Even if you place `.vault` directly at the external drive root `<USB>/.vault`, `choose_encrypt_output_dir` automatically targets `<USB>/Cipher_Secrets/`! By contrast, a standalone `.vault` copied onto `~/Desktop/.vault` or `~/` falls back to `~/Desktop/Encrypted_Output` / `~/Desktop/Sifreli_Cikti`, and `-o` / `--output` always lets you specify any explicit folder.)*
2. **Stage & encrypt new media in Tails OS (`./run.sh`):**
   * Place new plaintext files in `~/Desktop/To_Encrypt` (or `~/Desktop/Sifrelenecek`): files directly in the root route to **`Default Secret Album`**, while named subfolders (e.g., `To_Encrypt/Vacation/photo.jpg`) match existing `.meta` albums or create new encrypted ones.
   * Run `./run.sh` and enter your vault password/PIN/pattern. **No output destination prompt is shown** — `cipher_vault.py` unlocks `.vault` and automatically encrypts into `<USB>/Cipher_Secrets/`. Optionally zero-wipe the RAM source files (`--delete-source` or interactive prompt).
3. **Sync into your phone inside the Android app:**
   * Connect the USB/SD drive to your phone, open **Fossify Gallery Cipher**, go to **Settings > Encrypted vault > Sync vault from external drive** (*"Ayarlar > Şifreli Kasa > Harici diskten kasayı eşitle"*), unlock your vault if prompted, and select `<USB>/Cipher_Secrets` (or the USB root).
   * **Identical handling for both scenarios:** Whether your USB's `Cipher_Secrets/` holds a full vault mirror (Scenario 1) or only `.vault` plus newly encrypted additions (Scenario 2), Android sync processes it identically. In Scenario 2, once syncing completes, you can delete the synced `.bin` files and album subfolders from `<USB>/Cipher_Secrets/` while keeping only `.vault` for next time.
   * **4-Stage Sync Optimization & Full Bit-by-Bit Safety:**
     * Unchanged mirrored `.bin` files already in the destination album skip in `O(1)` time (checking size + header version + trailing 16-byte `Poly1305-AES` tag) without decrypting headers or hashing gigabytes of video.
     * Incoming `.bin` files with distinct filenames copy immediately after `Poly1305-AES` MAC verification (lazily matching or creating `.meta` albums and routing root files to `Default Secret Album`).
     * Same-name / same-size filename collisions undergo `Poly1305-AES` integrity verification followed by **full bit-by-bit decrypted comparison in RAM**: identical bits (even when re-encrypted with different random IVs) are skipped cleanly, while differing bits are safely preserved as `<name>(1)`, `<name>(2)`, etc.

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

## 🌐 Language & Automatic Output Routing

On interactive launch, the tool asks you to choose **English** or **Türkçe** (or pass `--lang en` / `--lang tr`), and uses matching localized folders without prompting for an output destination:

| Language Flag | Default Input Folder | Automatic Output Routing (`choose_encrypt_output_dir`) |
| :--- | :--- | :--- |
| `--lang en` | `~/Desktop/To_Encrypt` | `<USB>/Cipher_Secrets` (for mounted `Cipher_Secrets/.vault` or `<USB>/.vault`); falls back to `~/Desktop/Encrypted_Output` if `.vault` is standalone on Desktop/Home (`-o` overrides) |
| `--lang tr` | `~/Desktop/Sifrelenecek` | `<USB>/Cipher_Secrets` (bağlı `Cipher_Secrets/.vault` veya `<USB>/.vault` için); `.vault` Masaüstü/Ev dizinindeyse `~/Desktop/Sifreli_Cikti` (`-o` ile özelleştirilebilir) |

---

## 🚀 Quick Start & CLI Examples

1. **Mount USB `Cipher_Secrets/` (Full Vault or Header-Only `.vault`):** Plug in your external USB/SD drive containing `Cipher_Secrets/.vault` (either a full vault copy or just `.vault`), or copy `.vault` to `~/Desktop/.vault` from your phone (`Internal Storage > Fossify Gallery Cipher > Cipher_Secrets > .vault`).
2. **Run Launcher:**
   ```bash
   chmod +x run.sh cipher_vault.py
   ./run.sh
   ```
   *(Automatically detects `apt`, `dnf`, `pacman`, or `zypper` if dependencies are missing, creates the Desktop shortcut, and starts the sandboxed CLI.)*

```bash
# Interactive mode (prompts for Language, .vault secret with retry, and optional secure wipe; no output prompt)
./run.sh

# English or Turkish interactive folder workflow (-e/--encrypt is optional default)
./run.sh --lang en
./run.sh --lang tr

# Auto-target <USB>/Cipher_Secrets directly (works identically for full vault copy or header-only .vault)
./run.sh --lang en -i ~/Desktop/To_Encrypt -v /media/amnesia/USB/Cipher_Secrets/.vault

# Explicit custom -o override (for example writing to a custom USB transfer folder)
./run.sh --lang en -i ~/Desktop/To_Encrypt -o /media/amnesia/USB/Custom_Output -v /media/amnesia/USB/Cipher_Secrets/.vault

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

### 💾 Birleşik `Cipher_Secrets` Harici Disk İş Akışları (Tails OS RAM Üzerinde)

Her iki iş akışı da harici USB/SD diskinizde **tek bir birleşik `Cipher_Secrets/` klasör yapısı** kullanır ve `cipher_vault.py` size etkileşimli çıktı hedefi sormaz:

#### 1. Doğrudan Harici Disk Kasası (Direct External Vault)
Telefonun dahili depolamasına kopyalama yapmadan, Android uygulamasından harici USB/SD diskinizdeki `Cipher_Secrets` klasörünü doğrudan kasa olarak kullanın:
1. Şifrelenecek şifresiz medyaları RAM üzerindeki `~/Desktop/Sifrelenecek` (veya `To_Encrypt`) klasörüne koyun.
2. İçinde `Cipher_Secrets/.vault` bulunan harici USB/SD diskinizi takın (örn. `/media/amnesia/USB/Cipher_Secrets/.vault`).
3. `./run.sh` aracını başlatın. Araç `.vault` dosyasını otomatik algılar; şifre/PIN/desen ile kilidi açtıktan sonra **ek bir hedef sorusu sormadan doğrudan `<USB>/Cipher_Secrets/` içine** şifreleme yapar (isteğe bağlı olarak `-o` / `--output` ile özel dizin verebilirsiniz).
4. Şifreleme bitince RAM'deki kaynak dosyaları güvenli şekilde sıfırlayıp silebilir ve USB/SD diski doğrudan telefonunuza takabilirsiniz.

#### 2. Telefon Kasası + Birleşik `Cipher_Secrets` Harici Disk Senkronizasyonu
Ana kasanızı telefonunuzda tutup, Tails OS'te yeni medyaları harici diske şifreleyin ve dosya yöneticisiyle telefon klasörlerine girmeden doğrudan Android uygulamasının içinden birleştirin:

1. **Harici USB/SD diskinizde `Cipher_Secrets/` hazırlayın (iki yöntemden birini seçin):**
   * **Senaryo 1 — USB'de tam `Cipher_Secrets` kopyası varsa:** Telefonunuzdaki `Cipher_Secrets` klasörünü (`.vault` + mevcut şifreli `<hex>/.meta` albüm klasörleri) USB diske kopyalayın.
   * **Senaryo 2 — USB'de eski kasa medyaları YOKSA (Yalnızca `.vault`):** USB diskte `Cipher_Secrets/` klasörü açıp içine **yalnızca** telefonunuzdaki küçük `.vault` başlık dosyasını (`Dahili Depolama > Fossify Gallery Cipher > Cipher_Secrets > .vault`) koyun; hiçbir medya kopyalanmaz. *(Not: `.vault` dosyasını doğrudan `<USB>/.vault` köküne koysanız bile CLI çıktıyı otomatik olarak `<USB>/Cipher_Secrets/` içine yazar; yalnızca `~/Desktop/.vault` veya Ev dizinindeki tekil `.vault` dosyalarında Masaüstü `Sifreli_Cikti` / `Encrypted_Output` klasörüne düşer.)*
2. **Tails OS'te şifreleyin (`./run.sh`):**
   * Yeni medyaları `~/Desktop/Sifrelenecek` (veya `To_Encrypt`) içine koyun (doğrudan köke konanlar `Default Secret Album`'e gider; `Sifrelenecek/Tatil/foto.jpg` gibi alt klasörler mevcut `.meta` albümle eşleşir veya yeni albüm oluşturur).
   * `./run.sh` ile kasa şifrenizi/PIN'inizi girin. **Çıktı hedefi sorulmaz** — araç kilidi açar açmaz doğrudan `<USB>/Cipher_Secrets/` içine şifreli `.meta` (`FGMETA2`) klasörlerini ve `.bin` dosyalarını yazar. İsterseniz RAM'deki kaynak dosyaları sildirin (`--delete-source` veya etkileşimli onay).
3. **Telefonda uygulama içinden eşitleyin:**
   * USB/SD diski telefonunuza bağlayın, **Fossify Gallery Cipher** uygulamasını açın, **Ayarlar > Şifreli Kasa > Harici diskten kasayı eşitle** (*"Settings > Encrypted vault > Sync vault from external drive"*) seçeneğine dokunun, istenirse kasanın kilidini açın ve `<USB>/Cipher_Secrets` (veya USB kökünü) seçin.
   * **Her iki senaryo tek yapıda aynı şekilde çalışır:** USB'de tam kopya (Senaryo 1) veya yalnızca `.vault` + yeni eklenenler (Senaryo 2) olması fark etmez. Senaryo 2'de eşitleme bittikten sonra isterseniz `<USB>/Cipher_Secrets/` içindeki aktarılmış `.bin`/alt klasörleri silip yalnızca `.vault` dosyasını bir sonraki aktarım için bırakabilirsiniz.
   * **4 Aşamalı Optimizasyon ve Tam Bit Düzeyinde Güvenlik:**
     * Telefonda zaten bulunan değişmemiş birebir `.bin` dosyaları başlık çözme veya gigabaytlarca video hash'leme yapmadan `O(1)` sürede (boyut + başlık sürümü + sondaki 16 baytlık `Poly1305-AES` etiketi kontrolüyle) atlanır.
     * Farklı addaki yeni `.bin` dosyaları `Poly1305-AES` MAC doğrulamasından sonra doğrudan eşleşen veya yeni oluşturulan albümlere (ve kökteki dosyalar `Default Secret Album`'e) kopyalanır.
     * Aynı ad / aynı boyuttaki çakışmalar önce `Poly1305-AES` bütünlük doğrulamasından geçer, ardından RAM'de **tam bit bit (byte-byte) şifre çözerek karşılaştırılır**: farklı IV ile yeniden şifrelenmiş olsalar bile aynı bitler atlanır; farklı içeriğe sahip dosyalar üzerine yazılmadan `<ad>(1)`, `<ad>(2)` olarak güvenle adlandırılır.

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

# Türkçe arayüz ile etkileşimli şifreleme (ek çıktı hedefi sormaz)
./run.sh --lang tr

# Harici USB Cipher_Secrets kasasına (tam kopya veya yalnızca .vault içeren) otomatik yönlendirme
./run.sh --lang tr -i ~/Desktop/Sifrelenecek -v /media/amnesia/USB/Cipher_Secrets/.vault

# İsteğe bağlı özel -o çıktı klasörü ile geçersiz kılma
./run.sh --lang tr -i ~/Desktop/Sifrelenecek -o /media/amnesia/USB/Ozel_Cikti -v /media/amnesia/USB/Cipher_Secrets/.vault
```

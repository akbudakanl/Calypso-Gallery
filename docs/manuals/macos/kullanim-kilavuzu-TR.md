# Calypso Gallery — CLI Köprüsü Kullanım Kılavüzü (macOS)

[English version](user-manual-EN.md) · [Windows / Linux TR](../windows-linux/kullanim-kilavuzu-TR.md)

> Bu kılavuz, Mac'inizdeki şifrelenmemiş fotoğraf/video dosyalarını **şifreli olarak** telefonunuzdaki Calypso Gallery kasasına aktarma işlemini baştan sona anlatır.
>
> macOS, Android telefonun MTP depolamasını Finder'da göstermez. Bu yüzden **OpenMTP** adlı ücretsiz, açık kaynaklı uygulama zorunludur (kurulum aşağıda).
>
> **Platform farkı:** Windows ve Linux'ta `run.bat` / `run.sh` telefon ↔ bilgisayar kopyalamasını otomatik yapar ve geçici bir çalışma dizini kullanır. macOS'ta OpenMTP'nin komut satırı arayüzü olmadığı için kopyalama elle yapılır; çalışma klasörü de kalıcıdır.

---

## 1. Sistem kısaca nasıl çalışır?

Calypso Gallery'nin şifreli kasası telefonunuzda `Calypso Gallery/Secrets` klasöründedir. CLI aracı **yalnızca şifreler**; şifre çözmez, telefona hiçbir zaman düz (şifrelenmemiş) medya yazmaz.

```
Telefon                                  Mac
───────                                  ───
Calypso Gallery/                         ~/Desktop/
├── Secrets/          ← gerçek kasa      ├── To_Encrypt/     ← şifrelenecek dosyalar
│   ├── vault         ← kasa başlığı     └── Calypso CLI/
│   └── <hex>/ (.meta + .bin)                └── <vault-id>/
├── CLI/              ← köprüler                  ├── vault      ← başlık kopyası
│   └── <vault-id>/                              ├── cli_bridge ← köprü tanıtıcı dosyası
│       ├── vault                                ├── <hex>/.meta
│       ├── cli_bridge                           ├── <hex>/*.bin
│       └── (aktarım sırasında .bin)             └── pending_sync
└── Archive/          ← arşivlenen kasalar
```

**Akış özeti:**

1. Uygulama, aktif kasanız için `Calypso Gallery/CLI/<vault-id>/` klasörünü oluşturur; içinde `vault` ve `cli_bridge` vardır. Marker'da kasa yolu, aktiflik durumu (`active=1`) ve uygulamanın kullandığı parmak izi bulunur.
2. Siz o klasörü **OpenMTP** ile Mac'e kopyalarsınız.
3. CLI aracı şifreli `.bin`, `.meta` ve `pending_sync` dosyalarını köprü klasörüne yazar.
4. Klasörü OpenMTP ile telefona geri kopyalarsınız.
5. Uygulamadaki **"CLI / harici şifreli medyayı içe aktar"** ayarı dosyaları gerçek kasaya taşır ve köprüyü temizler.

> Köprü klasörü geçiş alanıdır, kasa değildir. Aktarım bitince içindeki şifreli dosyalar silinir; geriye `vault` + `cli_bridge` kalır.

---

## 2. Başlamadan önce

### 2.1 OpenMTP kurulumu (zorunlu)

macOS Finder, Android cihazların MTP depolamasını göstermez. Google'ın eski *Android File Transfer* uygulaması da 2024'te kaldırıldı. Bu yüzden:

- **OpenMTP** — ücretsiz, MIT lisanslı, açık kaynak.
- Gereksinim: **macOS 10.14 (Mojave) veya üzeri**; Apple Silicon ve Intel sürümleri ayrıdır.
- Kurulum yolları:
  - Resmî site: `openmtp.ganeshrvel.com` → *Download for Apple Silicon* / *Download for Intel*
  - GitHub: `github.com/ganeshrvel/openmtp` → Releases
  - Homebrew: `brew install --cask openmtp`

> Sadece resmî kaynaklardan indirin; benzer isimli kopya siteleri vardır.
>
> OpenMTP, Calypso Gallery ile ilişkili olmayan bağımsız bir üçüncü taraf uygulamadır. Adı ve bağlantısı yalnızca bilgilendirme amaçlıdır; kullanımı kendi lisans ve koşullarına tabidir.
>
> **İpucu:** MTP'yi aynı anda yalnızca bir uygulama kullanabilir. OpenMTP telefonu görmüyorsa Photos, Image Capture, Preview veya eski Android File Transfer'ı kapatın.

### 2.2 Diğer gereksinimler

- Telefonda Calypso Gallery kurulu ve en az bir şifreli kasa oluşturulmuş olmalı.
- Mac'te Python 3.10–3.13 (3.14+ desteklenmez). `run.sh` eksik paketleri sanal ortama kurar.
- Veri aktarımı yapabilen USB kablosu.
- Telefon bağlıyken **ekran kilitli olmamalı**; USB bildiriminden **"File transfer / MTP"** seçilmelidir (diyalog Android'den gelir).

---

## 3. İlk kurulum

### Adım 1 — Kasa ve varsayılan albüm

Uygulamada şifreli kasayı oluşturun. Kasa kurulurken **"Default Secret Album"** fiziksel olarak da oluşturulur; alt klasöre konmamış dosyalar buraya gider.

### Adım 2 — Köprü klasörünü oluşturun

Ayrı bir işlem gerekmez: köprü, kasa oluşturulduğunda veya içe aktarma satırına ilk kez basıldığında otomatik oluşur.

- Aktif kasa için `Calypso Gallery/CLI/<vault-id>/` açılır; içine `vault` ve `cli_bridge` yazılır.
- Aynı kasa için köprü zaten varsa **"klasör zaten mevcut"** uyarısı gösterilir.

### Adım 3 — Köprüyü OpenMTP ile Mac'e kopyalayın

1. Telefonu USB'yle bağlayın, kilit açıkken USB bildiriminden **File transfer** seçin.
2. OpenMTP'yi açın. İki panelli pencerenin **sağ tarafı telefon**, **sol tarafı Mac** dosyalarıdır. Telefon görünmüyorsa `⌘R` ile yenileyin veya kabloyu değiştirin.
3. Sağ panelden `Internal storage → Calypso Gallery → CLI → <vault-id>` klasörüne gidin.
4. Klasörü sol panele, macOS için sabit çalışma konumuna sürükleyin:

```
~/Desktop/Calypso CLI/<vault-id>/
```

Bu klasör macOS'ta **kalıcıdır** (OpenMTP otomatik kopyalama yapmaz). Windows/Linux'ta ise launcher geçici bir dizin kullanır ve iş bitince siler.

> **Kasa seçimi (macOS):** Birden fazla köprü kopyaladıysanız, `cli_bridge` içindeki `active=1` işaretli olan otomatik seçilir. Bayrak yoksa veya birden fazlaysa numaralı menü açılır; her satırda yalnızca kasa yolu gösterilir. Emin olmak için sadece ihtiyacınız olan köprüyü kopyalayın veya `-v` parametresini kullanın.

Telefon Finder'da **görünmez** — OpenMTP penceresini kullanın.

### Adım 4 — Şifrelenecek dosyaları hazırlayın

Dosyaları `~/Desktop/To_Encrypt` içine koyun:

| Dosyanın yeri | Gittiği albüm |
|---|---|
| `To_Encrypt/foto.jpg` (kök) | **Default Secret Album** |
| `To_Encrypt/Tatil fotoğraflarım/foto.jpg` | "Tatil fotoğraflarım" albümü |
| `To_Encrypt/Tatil fotoğraflarım/Gün1/foto.jpg` | Yine "Tatil fotoğraflarım" |

Albüm adı diskte görünmez: CLI rastgele 6 haneli bir klasör açar, gerçek adı şifreli `.meta` içine yazar; uygulama aktarımda çözer.

---

## 4. Şifrelemeyi çalıştırma

Terminal'de:

```bash
cd tools/calypso-cli
chmod +x run.sh calypso_vault.py
./run.sh
```

CLI:

- Yerel köprüleri bulur; tam biri `active=1` ise seçim sormadan onu kullanır, aksi halde menü gösterir (sadece kasa yolu):

```
Select the vault to use:
  1) /storage/emulated/0/Calypso Gallery/Secrets
  2) /storage/emulated/0/USB/Calypso Gallery/Secrets

Select the vault that is currently active in the app; otherwise the transfer will fail.
```

- Kasa başlığını açmak için **şifreyi/PIN'i sorar** (köprü oluşturulduğu andaki şifre — bkz. bölüm 8).
- `.meta` + `.bin` yazar, `pending_sync`'i günceller; `cli_writing` sentinel'i yazım sırasında durur, sonra silinir.

Köprü klasörüne düz medya yazılmaz.

---

## 5. Geri kopyalama ve uygulamaya aktarma

1. OpenMTP'de sol panelden `~/Desktop/Calypso CLI/<vault-id>/` klasörünü alın, sağ panelde `Internal storage → Calypso Gallery → CLI` içine sürükleyin.
2. Uygulamada **Ayarlar → "CLI / harici şifreli medyayı içe aktar"** satırına dokunun.
3. Kapatılamayan ilerleme penceresi gösterilir; aktarım bitene kadar ayarlardan çıkılmaz.
4. Aktarım sonunda şifreli dosyalar kasaya taşınır, köprüdeki `.bin`/`.meta`/albüm klasörleri silinir, **`pending_sync` en son silinir**; geriye `vault` + `cli_bridge` kalır.

**Sorun olursa:** başarısız dosyalar `pending_sync`'te kalır, köprü temizlenmez; ayarı tekrar seçtiğinizde kaldığınız yerden devam eder. Bekleyen dosya yoksa **"aktarılacak dosya yok"** uyarısı gelir.

---

## 6. Günlük kullanımın kısa özeti

1. Yeni medyayı `~/Desktop/To_Encrypt` içine koy.
2. OpenMTP ile köprü klasörünü Mac'e kopyala (ilk kez) veya güncelle.
3. Terminalde `./run.sh`, şifreyi gir.
4. OpenMTP ile klasörü telefona geri kopyala.
5. Ayarlar → "CLI / harici şifreli medyayı içe aktar".

---

## 7. Komut parametreleri (kısa başvuru)

Normal kullanımda **parametre gerekmez**:

| Parametre | Anlamı |
|---|---|
| `-v`, `-k`, `--vault` | `vault` başlık dosyasının yolu. Otomatik keşif ve kasa seçimini atlar. |
| `-i`, `--input` | Girdi klasörü (varsayılan `~/Desktop/To_Encrypt`). |
| `-s`, `--secret` | Şifreyi komut satırında verir. **Önerilmez** — kabuk geçmişinde kalır. |
| `--pattern` | 3×3 desen PIN'i için hane indeksleri (ör. `0,1,2,5`). |
| `--delete-source` | Şifrelemeden sonra kaynak dosyaları güvenle siler. |
| `-e`, `--encrypt` | Şifreleme (varsayılan). |

Örnek:

```bash
./run.sh -v "$HOME/Desktop/Calypso CLI/a1b2c3d4/vault"
```

---

## 8. Sık karşılaşılan durumlar

| Durum | Ne olur / ne yapılır? |
|---|---|
| **Parolayı değiştirdim** | Hiçbir şey yapmanız gerekmez. Köprü oluşturulduğu andaki parolayı ve aynı anahtarı taşır; CLI'da **eski parolanızı** kullanın. |
| **OpenMTP telefonu görmüyor** | Telefon kilitli olmasın, USB modu File transfer olsun, veri kablosu kullanın. Diğer MTP uygulamalarını kapatın. |
| **Finder'da telefonu arıyorum** | macOS Finder MTP göstermez; OpenMTP'yi kullanın. |
| **Birden fazla kasa** | Tam biri aktif işaretliyse otomatik seçilir; belirsizse menü (sadece yol). Aktif olmayan kasayı seçerseniz aktarım reddedilir. |
| **Aynı kasa için ikinci köprü** | "Klasör zaten mevcut" uyarısıyla engellenir. |
| **Kasayı arşivledim** | Köprü `CLI/Archived/<vault-id>/` içine taşınır ve kasa arşivde kaldığı sürece orada kalır. CLI onu oradan da bulur, böylece şifrelemeye devam edebilirsiniz. Köprü yalnızca kasayı arşivden çıkarınca `CLI/<vault-id>/` içine döner. |
| **Kasayı sildim** | Köprü de silinir. |
| **Yedekleme / cihaz değiştirme** | Ayarlar → **"Şifreli kasayı dışa aktar"**; yalnızca tam kasa seçeneği vardır. |
| **"Klasör meşgul"** | CLI yazıyor; bitmesini bekleyin. |
| **Köprü temiz değil, bekleyen dosyalar var** | Sorun değil: CLI yeni dosyaları mevcut albümlere ekler, manifest'e **ekleme** yapar. Uygulama eski + yeni girdileri tek partide aktarır; zaten aktarılanlar içerik karşılaştırmasıyla atlanır. |

---

## 9. Güvenlik notları

- CLI **çevrimdışı** çalışır: ağ ve alt süreçler engellenir; Python audit hook her zaman etkindir.
- Şifrelenmemiş medya telefona **hiç yazılmaz**; telefon yalnızca şifreli `.bin` görür.
- Disk tabanlı macOS'ta takas dosyası, QuickLook önbelleği, Spotlight ve APFS anlık görüntüleri iz bırakabilir. Azami gizlilik için Windows/Linux kılavuzundaki Tails akışına bakın.
- `vault` dosyası noktasız bir addır ve OpenMTP'de görünür. Şifreli dosyalarla yan yana olduğu için klasöre erişen biri çevrimdışı şifre denemesi yapabilir; köprüyü paylaşmayın, iCloud/Dropbox gibi bulut hizmetlerine koymayın.
- Kasa başlığını başka bir yere kopyalarken de dikkatli olun: başlık + parola = kasaya erişim demektir.

---

## 10. Ek seçenek: ücretli araçlar (zorunlu değil)

Normal akış tamamen ücretsiz araçlarla (OpenMTP + `run.sh`) çalışır. Telefonu **Finder'da sürücü gibi** görmek isterseniz MacDroid / Commander One gibi ücretli MTP araçları kullanılabilir; CLI tarafında değişiklik gerekmez. Bu araçlar **isteğe bağlıdır, varsayılan akışın parçası değildir.**

# Calypso Gallery — CLI Köprüsü Kullanım Kılavüzü (Windows / Linux)

[English version](user-manual-EN.md) · [macOS / macOS TR](../../macos/kullanim-kilavuzu-TR.md)

> Bu kılavuz, bilgisayarınızdaki şifrelenmemiş fotoğraf/video dosyalarını **şifreli olarak** telefonunuzdaki Calypso Gallery kasasına aktarma işlemini baştan sona anlatır.
>
> Windows ve Linux'ta telefon ↔ bilgisayar kopyalaması **tamamen otomatiktir**: `run.bat` / `run.sh` komutunu çalıştırdığınızda launcher yapar. Sizin yapmanız gereken sadece medyayı klasöre koymak, komutu çalıştırmak ve uygulamada aktarım satırına dokunmaktır.

---

## 1. Sistem kısaca nasıl çalışır?

Calypso Gallery'nin şifreli kasası telefonunuzda `Calypso Gallery/Secrets` klasöründedir. CLI aracı **yalnızca şifreler**; şifre çözmez, telefona hiçbir zaman düz (şifrelenmemiş) medya yazmaz.

Telefon ile bilgisayar arasında bir **köprü klasörü** kullanılır:

```
Telefon                                    Bilgisayar (yalnızca çalışma anında)
───────                                    ─────────────────────────────────────
Calypso Gallery/                           Geçici dizin (%TEMP% / /tmp)
├── Secrets/          ← gerçek kasa        └── <vault-id>/
│   ├── vault         ← kasa başlığı            ├── vault      ← başlık kopyası
│   └── <hex>/ (.meta + .bin)                   ├── cli_bridge ← köprü tanıtıcı dosyası
├── CLI/              ← köprüler                 ├── <hex>/.meta
│   └── <vault-id>/                              ├── <hex>/*.bin
│       ├── vault                                └── pending_sync
│       ├── cli_bridge
│       └── (aktarım sırasında .bin)
└── Archive/          ← arşivlenen kasalar

Ayrıca: Desktop/To_Encrypt/  ← şifrelenecek dosyaları siz buraya koyarsınız
```

**Otomatik akış:**

1. Uygulama, aktif kasanız için `Calypso Gallery/CLI/<vault-id>/` klasörünü oluşturur; içinde sadece `vault` ve `cli_bridge` vardır. `cli_bridge` dosyasında kasa yolu, aktiflik durumu (`active=1`) ve uygulamanın kullandığı parmak izi bulunur.
2. `run.bat` / `run.sh` telefonu MTP ile bulur, bu klasörü **geçici bir dizine** kopyalar.
3. Python CLI o geçici dizinde şifreli `.bin`, `.meta` ve `pending_sync` yazar.
4. Launcher klasörü telefona geri kopyalar ve geçici dizini siler.
5. Uygulamada **"CLI / harici şifreli medyayı içe aktar"** satırı, dosyaları gerçek kasaya taşır ve köprüyü temizler.

> **Önemli:** Geçici dizin kalıcı değildir. Masaüstünüzde `Calypso CLI` gibi bir klasör oluşmaz; launcher iş bitince çalışma alanını kaldırır. Köprü klasörünün asıl yeri telefondur.

---

## 2. Başlamadan önce

- Telefonda Calypso Gallery kurulu ve **en az bir şifreli kasa oluşturulmuş** olmalı.
- Bilgisayarda Python 3.10–3.13 (3.14+ desteklenmez). Launcher eksik paketleri otomatik kurar.
- **Veri aktarımı yapabilen** bir USB kablosu (bazı kablolar yalnızca şarj eder).
- Telefonu bağladığınızda **ekran kilitli olmamalı**; USB bildiriminde **"Dosyaları aktar / MTP"** seçilmelidir. Bu diyalog Android'den gelir.
- **Linux:** otomatik kopyalama `gio` (gvfs) kullanır. GNOME kullanan çoğu dağıtımda hazırdır. Minimal/başsız bir sistemde `gio` veya MTP backend'i yoksa launcher sizi manuel kopyalamaya yönlendirir (bkz. bölüm 9).
- **Windows:** ek yazılım gerekmez; launcher Windows'un yerleşik MTP arayüzünü kullanır.

### Tails / RAM-only Linux için

Aynı akış geçerlidir; **kablo kullanılır, ağ kullanılmaz**:

- Telefonu Tails **ekranı açıkken** takın (Tails 6+, kilitli ekranda takılan USB aygıtlarını yok sayar).
- Tails'te geçici dizin `tmpfs`'tedir, yani RAM'de. Kaynak dosyalar da `~/Desktop/To_Encrypt` içinde RAM'dedir. Şifrelenmemiş hiçbir veri diske yazılmaz.
- Kapanışta RAM silinir; köprü klasörü zaten telefondadır.

---

## 3. İlk kurulum

### Adım 1 — Kasa ve varsayılan albüm

Uygulamada şifreli kasayı oluşturun (şifre, PIN veya desen). Kasa kurulurken **"Default Secret Album" (Varsayılan Gizli Albüm)** fiziksel olarak da oluşturulur; alt klasöre konmamış dosyaların nereye gideceği başından bellidir.

### Adım 2 — Köprü klasörünü oluşturun

Ayarlar → **"CLI köprüsünü hazırla"** (yeni özellik; eski "sadece başlık dışa aktarma" seçeneğinin yerini alır).

- Aktif kasa için `Calypso Gallery/CLI/<vault-id>/` açılır.
- İçine `vault` başlığının kopyası ve `cli_bridge` dosyası yazılır.
- Aynı kasa için köprü zaten varsa **"klasör zaten mevcut"** uyarısı gösterilir; ikinci klasör açılmaz.

> Bu adım kasa ilk oluşturulduğunda otomatik de yapılabilir. Mevcut kasalar için manuel "hazırla" aksiyonu kullanılır.

### Adım 3 — Şifrelenecek dosyaları hazırlayın

Dosyaları masaüstündeki `To_Encrypt` klasörüne koyun. **Köprü klasörünü elle kopyalamanıza gerek yok** — launcher bunu yapacak.

| Dosyanın yeri | Gittiği albüm |
|---|---|
| `To_Encrypt/foto.jpg` (doğrudan kök) | **Default Secret Album** |
| `To_Encrypt/Tatil fotoğraflarım/foto.jpg` | "Tatil fotoğraflarım" albümü |
| `To_Encrypt/Tatil fotoğraflarım/Gün1/foto.jpg` | Yine "Tatil fotoğraflarım" (alt klasörler tek düzeye iner) |

Desteklenenler: fotoğraf (jpg, png, heic, webp, raw…), GIF, SVG, video (mp4, mkv, mov…). Diğer dosyalar atlanır.

> Albüm adı diskte görünmez: CLI rastgele 6 haneli bir klasör (`a3f19c`) açar, gerçek adı şifreli `.meta` dosyasına yazar. Uygulama aktarımda adı çözer; varsa mevcut albüme ekler, yoksa yeni albüm açar.

---

## 4. Şifrelemeyi çalıştırma

**Windows:** `tools\calypso-cli\run.bat` dosyasına çift tıklayın.

**Linux:**

```bash
cd tools/calypso-cli
chmod +x run.sh calypso_vault.py
./run.sh
```

Launcher sırayla:

1. Telefonu MTP üzerinden bulur (Linux'ta USB bus numarasını her seferinde yeniden keşfeder; Windows'ta portable device arayüzünü kullanır).
2. `Calypso Gallery/CLI/` altındaki köprü klasörlerini geçici dizine kopyalar.
3. Python CLI'ı çalıştırır.
4. CLI bitince **seçilen** köprü klasörünü telefona geri kopyalar.
5. Geçici dizini siler.

> **Kesinti durumunda:** Launcher geçici dizini **yalnızca geri kopyalama tamamlandıktan sonra** siler. Bir önceki turdan kalan geçici dizin hâlâ duruyorsa, telefonu yeniden çekip üzerine yazmaz — mevcut dizini kullanır. Böylece kablo kopması veya telefonun şarjının bitmesi sonrasında komutu tekrar çalıştırmak güvenlidir; telefondaki kısmi bir köprü kopyası yerel iyi durumu ezemez.

### Kasa seçimi

- Köprü klasörlerinden **tam biri `active=1`** ise CLI seçim sormadan onu kullanır (çoğu kullanıcı için durum budur).
- `active=1` olan yoksa veya birden fazlaysa numaralı seçim ekranı gösterilir; her satırda **yalnızca kasa yolu** yazılır:

```
Select the vault to use:
  1) /storage/emulated/0/Calypso Gallery/Secrets
  2) /storage/emulated/0/USB/Calypso Gallery/Secrets

Select the vault that is currently active in the app; otherwise the transfer will fail.
```

- `-v` parametresiyle bir başlık yolu açıkça verilirse menü hiç gösterilmez.

### CLI çıktısı

- Kasa başlığını açmak için **şifreyi/PIN'i sorar** (köprü oluşturulduğu andaki şifre — bkz. bölüm 8).
- Dosyaları şifreler, geçici köprüye `.meta` + `.bin` yazar, `pending_sync` listesini günceller.
- Yazım sırasında `cli_writing` oluşur, bitince silinir.

Köprü klasörüne düz medya yazılmaz; yalnızca şifreli veri ve liste dosyaları.

---

## 5. Uygulamaya aktarma

1. Uygulamada **Ayarlar → "CLI / harici şifreli medyayı içe aktar"** satırına dokunun.
2. Kapatılamayan ilerleme penceresi gösterilir; aktarım bitene kadar ayarlardan çıkılmaz.
3. Aktarım sonunda şifreli dosyalar gerçek kasaya taşınır; köprüdeki `.bin`, `.meta` ve albüm klasörleri silinir, **`pending_sync` en son silinir**. Telefondaki köprüde sadece `vault` + `cli_bridge` kalır.

**Sorun olursa:** başarısız dosyalar `pending_sync` içinde kalır, köprü temizlenmez. Ayarı tekrar seçtiğinizde kaldığınız yerden devam eder. Bekleyen dosya yoksa **"aktarılacak dosya yok"** uyarısı gelir.

Aynı içerikli dosyalar bir kez daha kopyalanmaz (atlanır); aynı adda farklı içerik varsa `ad(1)`, `ad(2)` olarak kaydedilir.

---

## 6. Günlük kullanımın kısa özeti

1. Yeni medyayı `Desktop/To_Encrypt` içine koy.
2. `run.bat` / `./run.sh` çalıştır, şifreyi gir; geri kalanını launcher yapar.
3. Uygulamada "CLI / harici şifreli medyayı içe aktar" satırına dokun.

İlk kurulumdaki köprü oluşturma adımı bir kez yapılır; sonraki turlarda tekrar gerekmez.

---

## 7. Komut parametreleri (kısa başvuru)

Normal kullanımda **hiçbir parametre gerekmez**. Gerekirse:

| Parametre | Anlamı |
|---|---|
| `-v`, `-k`, `--vault` | Kullanılacak `vault` başlık dosyasının yolu. Verilirse otomatik keşif ve kasa seçimi atlanır. |
| `-i`, `--input` | Şifrelenecek dosyaların klasörü (varsayılan `Desktop/To_Encrypt`). |
| `-s`, `--secret` | Şifreyi komut satırında vermek. **Önerilmez** — kabuk geçmişinde kalır. |
| `--pattern` | 3×3 desen PIN'i için hane indeksleri (ör. `0,1,2,5`). |
| `--delete-source` | Şifrelemeden sonra kaynak dosyaları güvenle siler. |
| `-e`, `--encrypt` | Şifreleme (varsayılan; yazmaya gerek yok). |

Örnek:

```bash
./run.sh -v "/media/amnesia/USB/Calypso Gallery/CLI/a1b2c3d4/vault"
```

---

## 8. Sık karşılaşılan durumlar

| Durum | Ne olur / ne yapılır? |
|---|---|
| **Parolayı değiştirdim** | Hiçbir şey yapmanız gerekmez. Köprü, oluşturulduğu andaki parolayı ve aynı şifreleme anahtarını taşır; geçerli kalır. CLI'da **eski parolanızı** kullanın. |
| **Birden fazla kasa** | Tam biri aktif işaretliyse otomatik seçilir; belirsizse menü çıkar (sadece kasa yolu). Aktif olmayan kasayı seçerseniz aktarım uygulama tarafından reddedilir. |
| **Aynı kasa için ikinci köprü** | Engellenir; "klasör zaten mevcut" uyarısı döner. |
| **Kasayı arşivledim** | Köprü `Calypso Gallery/CLI/Archived/<vault-id>/` içine taşınır ve otomatik akışta görünmez. Kasa geri açılınca eski yerine döner. |
| **Kasayı sildim** | Köprü de silinir; geçici kopya bir sonraki turda oluşmaz. |
| **Yedekleme / cihaz değiştirme** | Ayarlar → **"Şifreli kasayı dışa aktar"**. Artık yalnızca kasanın tamamı seçeneği vardır; "sadece başlık" kaldırılmıştır. |
| **"Klasör meşgul"** | CLI hâlâ yazıyor; bitmesini bekleyin. |
| **"Vault bulunamadı"** | Uygulamada köprü klasörünü hazırladığınızdan ve telefonun MTP modunda olduğundan emin olun. |
| **Köprü temiz değil, bekleyen dosyalar var** | Sorun değil: CLI yeni dosyaları mevcut albümlere ekler, manifest'in üzerine yazmaz **ekleme** yapar. Uygulama eski + yeni tüm girdileri tek partide aktarır; zaten aktarılmış olanlar içerik karşılaştırmasıyla atlanır. Bekleyen bir partiyi beklemeden yeni medya şifreleyebilirsiniz. |

---

## 9. Otomatik kopyalama çalışmazsa (manuel yöntem)

Launcher telefonu göremezse (kilit ekranı, kablo sorunu, Linux'ta `gio` eksikliği) sizi uyarır ve manuel yönteme düşer:

1. Telefondaki `Calypso Gallery/CLI/<vault-id>/` klasörünü dosya yöneticisiyle yerel bir klasöre kopyalayın (ör. `Desktop/Calypso CLI/<vault-id>/`).
2. CLI'ı şu şekilde çalıştırın:

```bash
./run.sh -v "$HOME/Desktop/Calypso CLI/<vault-id>/vault"
```

3. İş bitince klasörü telefonun `Calypso Gallery/CLI/<vault-id>/` klasörüne geri kopyalayın.
4. Uygulamada aktarım satırına dokunun.

Bu yöntem otomatik akışla birebir aynı sonucu verir; sadece kopyalama adımlarını siz yaparsınız. Manuel kopyalama sırasında bağlantı koparsa, yerel klasörü silmeyin — telefonu yeniden bağlayıp eksik dosyaları tamamlayın ve CLI'ı aynı klasörle tekrar çalıştırın.

---

## 10. Güvenlik notları

- CLI **çevrimdışı** çalışır: ağ erişimi ve alt süreçler engellenir; Linux'ta ayrıca bubblewrap sandbox kullanılır. Otomatik kopyalama da yalnızca USB/MTP üzerindendir, ağ kullanılmaz.
- **Linux sandbox zinciri:** `bwrap` → `firejail` → `unshare --user --net`. Hiçbiri kullanılamıyorsa (ör. sertleştirilmiş çekirdek veya konteyner içi) launcher bir uyarı gösterip **`Continue anyway? [y/N]`** sorusunu sorar. Varsayılan `N` çıkar; yalnızca siz `y` yazarsanız Python audit hook'u ile, işletim sistemi düzeyinde izolasyon olmadan devam edilir. Ek bir parametre veya ayar yoktur; kararı her seferinde siz verirsiniz. Bu noktaya gelindiyse launcher bubblewrap'ı kurmayı zaten denemiş ve başarısız olmuştur; uyarıda kurulum önerisi yer almaz.
- Şifrelenmemiş medya telefona **hiç yazılmaz**; telefon yalnızca şifreli `.bin` görür.
- En yüksek gizlilik için Tails / `toram` kullanın: geçici dizin ve kaynak dosyalar RAM'dedir; kapanışta RAM silinir.
- Disk tabanlı Windows'ta geçici dizin disk üzerindedir; silinmesi adli düzeyde iz bırakmaz. Takas dosyası, önizleme önbelleği ve NTFS günlükleri iz bırakabilir (Tier 2 uyarısı).
- `vault` dosyası noktasız bir addır ve dosya yöneticisinde görünür. Şifreli dosyalarla aynı klasörde durduğu için klasöre erişen biri çevrimdışı şifre denemesi yapabilir; köprü klasörünü paylaşmayın, bulut senkronizasyonuna koymayın.

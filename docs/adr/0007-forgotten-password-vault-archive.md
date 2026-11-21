# ADR 0007 — Unutulan kasa parolası: arşivleme ve yeniden oluşturma

- **Durum:** Accepted — **Aksiyom: uygulama içinde parolasız kasa silinmez (2026-11-21)**
- **Tarih:** 2026-11-21
- **İlgili:** [ADR 0002](0002-operational-security-hardening.md) (operasyonel güvenlik), [ADR 0003](0003-resumable-file-operations.md) (kesintiye dayanıklı işlemler), [ADR 0006](0006-strong-biometrics-only.md) (güçlü biyometrik kararı)
- **İngilizce çeviri:** [0007-forgotten-password-vault-archive.en.md](0007-forgotten-password-vault-archive.en.md)

> **Özet Karar.** Kullanıcı kasa parolasını unuttuğunda uygulama **asla** parolasız silme yapmaz. Bunun yerine mevcut kasayı `Calypso Gallery/Archive/` altına **taşır** (arşivler), varsayılan konumda yeni bir kasa oluşturur ve eski kasayı parolasıyla geri yüklemeyi mümkün tutar. Arşiv klasörü yalnızca ilk arşivlemede oluşur; rastgele, tarih/sayaç içermeyen bir ad kullanılır. Disk alanı boşaltmak kullanıcıya bırakılır ve yol gösterilir; uygulama içinde arşiv silme yoktur.

> **Mantık (kısa).** Güvenlik standartları gereği parolasını bilmediğiniz bir şifreli kasayı uygulamadan silemezsiniz. Çoğu kullanıcı kasayı varsayılan konumda tuttuğu için parolası unutulan kasa bu alanı doldurur ve yeni kasa oluşturmayı da engeller. Kullanıcının iki seçeneği vardır: kasayı bir dosya yöneticisiyle kalıcı olarak silmek (veya ileride parolayı tekrar denemek için **Şifreli kasayı dışa aktar** ile harici diske yedeklemek), ya da telefonunda saklamaya devam etmek istiyorsa onu izole bir arşive taşıyıp aynı konumda yeni bir kasa oluşturmak.

---

## 1. Aksiyom

**Uygulama içinde hiçbir kasa, parola olmadan silinemez veya geçersiz kılınamaz.**

Bu kural bir tercih değil, tasarım aksiyomudur:

- Cihaza erişimi olan bir kişi, parolayı bilmeden kasayı "sıfırlayabilir" veya silebilirse bu bir **hizmet reddi** saldırısıdır. Saldırgan veriyi okuyamasa da kullanıcının erişimini kalıcı olarak yok eder.
- Aksiyom, mevcut **"Şifreli kasayı sıfırla"** ayarını da kapsar: bu ayar yalnızca **mevcut parola girildikten sonra** erişilebilir ve kasayı aynı konumda yeni parolayla yeniden oluşturur. Parolasız bir "sıfırla" yolu açılmaz.
- Aksiyom arşivlenmiş kasaları da kapsar: uygulama, arşivdeki bir kasayı da parolasız **silemez**. Silme yalnızca kullanıcının kendi eliyle, dosya yöneticisiyle yapılabilir (§8).

Biyometrik doğrulama bu aksiyomu değiştirmez: Class 3 biyometrik kasa kilidini açabilir ama parola olmadan kasayı **silme veya sıfırlama** yetkisi vermez (bkz. ADR 0006). Arşivleme öncesindeki cihaz sahibi doğrulaması (§3.3) yalnızca yıkıcı olmayan bir taşıma için kimlik kontrolüdür; hiçbir kasa anahtarını açığa çıkarmaz.

---

## 2. Problem

Basit bir kullanıcı, varsayılan konumdaki (`Calypso Gallery/Secrets`) kasasının parolasını unutursa:

- Kasayı açamaz; dolayısıyla mevcut "Şifreli kasayı sıfırla" ayarına erişemez.
- Yeni bir kasa oluşturmak için varsayılan konumu boşaltması gerekir, ama bunu dosya yöneticisiyle yapmak onun için zordur ve risklidir.
- İlk tepki "sil" olmamalıdır: kullanıcı daha sonra parolasını hatırlayabilir ve eski kasaya dönmek isteyebilir.
- Öte yandan arşiv sonsuza kadar diskte kalamaz; kullanıcının depolama üzerinde kontrolü olmalıdır. Bu yüzden kullanıcıya **arşivle / vazgeç** seçimi sorulur; kalıcı silmeyi tercih eden kullanıcıya yol gösterilir (§3.2, §8).

---

## 3. Karar

### 3.1. Ayar yeri ve yönlendirme

"Şifreli kasa" bölümünün en altına, parola gerektiren "Şifreli kasayı sıfırla"nın altına ayrı bir satır eklenir:

> **Kasayı arşivle** — *Eski kasayı arşive taşır, varsayılan konumda yeni kasa oluşturur*

Satır yalnızca **kasa varsa** görünür. Parola doğrulama ekranındaki (hatırlatma akışının 5. ekranı) **"Hatırlamıyorum"** bağlantısı **ayarlara değil**, numaralı adımlar ve o an seçili kasanın yolu gösteren kompakt uygulama içi kurtarma penceresine götürür (§3.4). Hakkında → SSS içindeki **"Şifreli kasanın parolasını unuttum, ne yapabilirim?"** maddesi Hakkında üzerinden erişilebilirliğini korur; SSS metnindeki *arşivleyerek* ifadesi bu ayara yönlendirir.

**"Kasa konumu"** menüsü üç seçenek gösterir:

1. **Yeni kasa oluştur**,
2. **Mevcut kasayı kullan**,
3. **Arşivlenmiş kasalardan seç** — yalnızca en az bir arşiv kasa varsa görünür; hiç yokken menüde yer almaz.

Arşiv listesi arşivlenme zamanına göre sıralanır ve UI'da **"1. arşivlenen kasa"**, **"2. arşivlenen kasa"** etiketleriyle gösterilir (§4.4). Bu sıralama uygulama yeniden kurulsa da korunur.

### 3.2. Kullanıcıya sorulan soru

Dialogda tek bir gerçek akış sunulur:

1. **Arşivle ve devam et** (varsayılan): eski kasa korunur, varsayılan `Secrets` konumunda yeni kasa oluşturulur. Diğer seçenek **Vazgeç**'tir.

> Reddedilen "arşivlemeden sil ve devam et" seçeneği ve parolasız sıfırlama fikri §10'da kayıtlıdır, burada yer almaz. Aksiyom gereği uygulama silme yapamaz: silmek isteyen kullanıcıya arşiv yolu gösterilir (§8) ve panoya kopyalama imkânı verilir; uygulama **silme fiilini gerçekleştirmez**.

### 3.3. Arşivleme akışı

Ön koşullar: kasa kilitli olmalı ve aktif foreground dosya işlemi bulunmamalı.

1. Kullanıcı onayı, mevcut "Şifreli kasayı sıfırla" ile aynı `DeleteSecretsDialog` üzerinden alınır: **10 saniyelik geri sayım + onay kutusu + Evet/Hayır**. **Fark: kasa parolası sorulmaz** — aksiyom parolasız silmeyi yasaklar, arşivleme ise silme değil taşımadır (§6). Dialog ayrıca "Başka bir cihazda kullanmak istiyorsanız Şifreli kasayı dışa aktar seçeneğini kullanın" hatırlatmasını içerir (§9).
2. **Cihaz sahibi doğrulaması**, güvenli ekran kilidi olan cihazlarda onayın ardından gelir: `BiometricPrompt`, `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` ile — Class 3 biyometrik, yedekte cihaz PIN'i/desen/parolası. Bu, kasa parolasını değil **cihazın elde olduğunu** doğrular ve hiçbir kasa anahtarını açığa çıkarmaz. **Hiç güvenli ekran kilidi olmayan** cihazlarda doğrulanacak bir sinyal bulunmadığından yalnızca 10 saniyelik onay uygulanır. Bu, "kilitsiz telefonu eline alan biri" açığını kapatırken parolasız **silme** yolu açmaz. `DEVICE_CREDENTIAL` açıkça izin verildiği için biyometrik başarısız olursa (arızalı sensör, ıslak parmak vb.) akış tıkanmaz; sistem penceresi cihaz PIN'i/deseni/parolasını sunmaya devam eder.
3. `Calypso Gallery/Archive/` klasörü **yoksa** oluşturulur ve içine jenerik `readme.txt` yazılır. Klasör uygulama kurulumunda **var olmaz**; ilk arşivlemeye kadar oluşturulmaz.
4. Rastgele hedef ad üretilir (§5); çakışma kontrolü yapılır.
5. Hedef yol özel tercihlere (`archivedVaults`) kaydedilir; eski kasa klasörü bu yola **taşınır**.
6. Taşıma başarısız olursa tercih kaydı geri alınır, kaynak yerinde kalır.
7. Biyometrik kasa durumu temizlenir: `isVaultBiometricEnabled = false`, `vaultBiometricWrappedBundle = ""`, `clearBiometricKey()`. Eski kasanın biyometrik bağı yeni kasaya devredilmez.
8. Varsayılan konumda (`Calypso Gallery/Secrets`) yeni kasa oluşturma akışı başlatılır; kullanıcı yeni parolasını/PIN'ini belirler.
9. Yeni kasa oluşturulamazsa `config.vaultRootPath` arşiv yoluna geri yazılır; eski kasa erişilebilir kalır.
10. Başarıda `config.vaultRootPath` yeni kasayı gösterir; arşiv kaydı özel tercihlerde durur.

Uygulama, arşiv kaydını rename'den önce yazar ve rename başarısız olursa geri alır; çökme durumunda kasa ya kaynağında ya da arşiv yolundan erişilebilir kalır. Çok adımlı bir [ADR 0003](0003-resumable-file-operations.md) journal'ı kullanılmaz. **Hiçbir durumda veri kaybı olmaz.**

### 3.4. SSS maddesi ve yönlendirme

Doğrulama ekranındaki (hatırlatma akışının 5. ekranı) "Hatırlamıyorum" bağlantısı, `FaqActivity` yerine kompakt ve temaya uyumlu bir uygulama içi pencere açar (6. ekran): üç numaralı kurtarma adımı, uyarı paragrafı, `CryptoManager.getVaultRoot()` / `config.vaultRootPath` ile o an seçili kasanın **gerçek yolu** ve tek bir **Tamam** butonu. Bu giriş noktası için `FaqActivity` extra'sı kullanılmaz; aşağıdaki SSS maddesi Hakkında'dan erişimde durmaya devam eder. Yeni SSS maddesi:

> **Soru:** "Şifreli kasanın parolasını unuttum, ne yapabilirim?"
>
> 1. Biyometrik ile kasaya giriş yapın.
> 2. Şifreli verilerinizi **kopyalayın (dışa aktarın)**. Bu işlem kasayı yerinden çıkarmaz; mevcut kasa olduğu yerde kalır. Kopyayı başka bir cihazda "Mevcut kasayı kullan" ile açabilirsiniz.
> 3. Aynı konumu kullanmaya devam etmek için Ayarlar → Şifreli kasa → **Kasayı arşivle** seçeneğini kullanın. Eski kasa `Calypso Gallery/Archive/` altına taşınır, varsayılan konumda yeni parolayla yeni bir kasa oluşturulur. **"Şifreli kasayı sıfırla" mevcut parolayı gerektirdiği için bu durumda kullanılamaz.**
>
> **Uyarı:** Biyometrik ile de giriş yapamıyorsanız kasaya erişim mümkün değildir. Üç seçeneğiniz vardır (güvenli olandan yıkıcı olana doğru):
>
> - Mevcut kasayı **arşivleyerek** yeni bir kasa oluşturun — eski kasa aynı cihazda korunur; parolanızı hatırlarsanız geri dönebilirsiniz.
> - Şifreli verilerinizi **kopyalayın (dışa aktarın)** — başka bir cihazda kullanmak içindir; **kopyalama, taşıma değildir**, orijinal yerinde kalır.
> - Kasayı **kalıcı olarak ortadan kaldırın** — *(ait olduğu konum)* konumundaki şifreli kasa klasörünü bir dosya yöneticisiyle silin. Bu işlem geri alınamaz.

*arşivleyerek* ve *kopyalayın (dışa aktarın)* ifadeleri tıklanabilir bağlantılardır (FaqActivity içinde `SpannableString`/`ClickableSpan` ya da SettingsActivity'ye hedef extra ile) ve sırasıyla **Kasayı arşivle** ile **Şifreli kasayı dışa aktar** ayarlarına götürür. *(ait olduğu konum)* ifadesi §8'deki dinamik yolla değiştirilir; sabit yol yazılmaz. Seçeneklerin sırası bilinçlidir: en az yıkıcı seçenek en üsttedir.

---

## 4. Arşiv düzeni ve meta veri

```
Calypso Gallery/                      (varsayılan: /storage/emulated/0/Calypso Gallery)
├── Secrets/                          ← aktif kasa
└── Archive/                          ← yalnızca ilk arşivlemede oluşur
    ├── readme.txt                    ← jenerik: amaç, geri yükleme, silme uyarısı
    └── a3f1c7d9e2b4/                  ← rastgele ad, tarih/sayaç YOK
        ├── info.txt                  ← jenerik tanıtıcı not
        ├── .vault
        └── *.bin
```

- `Archive` klasörü **`Calypso Gallery` kökünde** oluşturulur; üst klasördeki `.nomedia` dosyası alt dizinleri de kapsar, böylece arşiv galerilerde görünmez.

### 4.1. `Archive/readme.txt` — tam içerik

Dosya Türkçe ve İngilizce olarak yazılır. **Tarih, yol, kullanıcı adı veya "parola unutuldu" ifadesi bulunmaz:**

```text
Warning: Do not delete this file

Calypso Gallery — Arşiv / Archive

Bu klasördeki alt klasörler daha önce kullanılmış şifreli kasalardır.
Kasalar silinmemiştir. Uygulama içinden
  Ayarlar → Şifreli kasa → Kasa konumu → Arşivden kasa aç
yoluyla her kasa kendi parolasıyla tekrar açılabilir.

Dosyaları değiştirmeyin veya yeniden adlandırmayın. Kasa şifreli olduğu
için başka bir uygulamayla açılamaz.

Disk alanı kazanmak için bir kasayı kalıcı olarak silmek isterseniz,
ilgili alt klasörü bir dosya yöneticisiyle silebilirsiniz. Bu işlem
geri alınamaz.

The subfolders here are encrypted vaults archived earlier. They were not
deleted. In the app, use Settings → Encrypted vault → Vault location →
Open from archive to reopen any of them with its own password.

Do not modify or rename the files; other apps cannot open them.
To free disk space, delete a vault subfolder with a file manager.
This cannot be undone.
```

### 4.2. `info.txt` — tam içerik

Her arşivlenen kasanın içine, yalnızca klasörün ne olduğunu anlatan jenerik bir not konur. Klasör başka bir yere kopyalanırsa da bağlam kaybolmaz. **Tarih, orijinal yol veya etiket içermez** — bunlar yalnızca uygulamanın özel tercihlerindedir:

```text
Bu klasör Calypso Gallery tarafından arşivlenmiş bir şifreli kasadır.
İçeriğini değiştirmeyin. Uygulama içinden Ayarlar → Şifreli kasa →
Kasa konumu → Arşivden kasa aç ile açılabilir.

This folder is an encrypted vault archived by Calypso Gallery.
Do not modify its contents. Open it from Settings → Encrypted vault →
Vault location → Open from archive.
```
- Orijinal kasa yolu, arşiv tarihi ve isteğe bağlı kullanıcı etiketi yalnızca **uygulamanın özel `SharedPreferences` alanında**, rastgele id ile eşleştirilerek tutulur. Dosya yöneticisi bunu göremez.
- Klasör adında **tarih yoktur**, çünkü bu bir meta veri ihlali olarak algılanabilir: "kullanıcı şu tarihte parolasını unuttu" bilgisi doğrudan okunur. Dosya sistemi `mtime` zaten vardır; klasör adına eklemek gereksizdir.
- `.cache` gibi **yanıltıcı/sahte adlar reddedilmiştir**: kullanıcıyı kandırmak olur ve arşivin varlığını gizleme amacına hizmet etmez.

### 4.3. `readme.txt` silinirse

`readme.txt` **yalnızca bilgilendirmedir; uygulama mantığı için gerekli değildir.** Silinirse:

- Arşiv kasaları yine `Archive/*` taranıp `.vault` başlığı doğrulanarak bulunur; **hiçbir kasa kaybolmaz**.
- "Kasa konumu → Arşivlenmiş kasalardan seç" listesi **aynı şekilde görünür**; hata mesajı çıkmaz.
- Yalnızca isteğe bağlı bir bilgi notu gösterilir: *"Arşiv klasöründeki readme.txt eksik. Bu dosya sadece bilgilendirme içindir; kasalarınız güvende."*

"Warning: Do not delete this file" uyarısı bu yüzden vardır: kullanıcı talimatı kaybeder, **veriyi kaybetmez**.

### 4.4. Arşiv sıralaması ve kalıcılık

- Sıralama anahtarı **`info.txt.lastModified()`** değeridir; arşivleme anında oluşturulur, klasör adına tarih yazılmaz.
- `Calypso Gallery` harici depolamanın kökündedir (`Android/data` altında **değil**); uygulama kaldırılıp yeniden kurulsa da klasörler ve `mtime` değerleri korunur. Yani **"1. arşivlenen kasa", "2. arşivlenen kasa" sıralaması yeniden kurulumdan sonra da hatırlanır.**
- Etiketler yalnızca UI'da üretilir; diskte karşılığı yoktur. Aynı saniyede oluşan arşivler rastgele id ile tie-break edilir.
- Bir arşiv dosya yöneticisiyle silinirse kalanlar yeniden numaralanır.
- **Tarih kaynağı önceliği** (info.txt silinirse ne olur sorusunun cevabı):
  1. Uygulamanın özel `archivedVaults` kaydındaki `archivedAt` (normal kullanımda her zaman vardır; uygulama yeniden kurulana kadar).
  2. `info.txt.lastModified()` — arşivleme anında oluşturulur, yeniden kurulumdan sonra da durur.
  3. Hiçbiri yoksa **tarih bilinmez** olarak gösterilir; liste rastgele id ile sıralanır. Klasörün kendi `mtime`'ı güvenilir değildir, çünkü bir dizinin `mtime`'ı yeniden adlandırmada değişmez ve kasa aktifken dosya değişikliklerini yansıtır — **arşivleme zamanını temsil etmez**.
- **`.vault` kullanılamaz:** başlık formatında (`magic | versiyon | algoritma | Argon2 parametreleri | salt | IV'ler | sarınmış VMK | keyring | koruma tipi`) **tarih alanı yoktur**. `.vault` dosyasının mtime'ı da başlığın son yazıldığı (parola değişimi / KDF yeniden sarma) anı gösterir, arşivleme anını değil; rename bu değeri değiştirmez. `.vault` içine tarih yazmak güvenlik kritik dosyayı bozma riski taşır — **reddedildi**.
- `.vault` **silinirse veya başlığı bozulursa** `isValidVaultHeader` başarısız olur; klasör `resolveExistingVaultInDirectory` tarafından kasa olarak kabul edilmez ve listeden çıkar. Bu doğru davranıştır: geçerli kasa başlığı olmayan klasör kasa değildir. Özel tercihte kaydı bulunup diskte geçerli başlık yoksa UI'da **"Geçersiz arşiv kaydı"** satırı gösterilir: yol + *"Kasa başlığı bulunamadı; bu klasörü dosya yöneticisiyle silebilirsiniz."*

---

## 5. Rastgele ad ve çakışma güvenliği

Kasa adı **kriptografik olarak rastgele** üretilir:

- `SecureRandom` ile 12 bayt → 24 karakter hex (ör. `a3f1c7d9e2b4c6d8e0f1a2b3`).
- Üretimden sonra `File.exists()` ile hedef kontrol edilir; **çakışma varsa yeniden üretilir**. Maksimum deneme (ör. 10) aşılırsa işlem iptal edilir ve hata bildirilir.
- `renameTo` zaten hedef varsa başarısız olur; bu ikinci bir güvenlik katmanıdır.
- `UUIDv4` kabul edilebilir; **ULID / UUIDv7 kullanılmaz** çünkü bunlar zaman damgasını ismin içine gömer.
- Sayaç (`Secrets (1)`, `Secrets (2)`) kullanılmaz: arşiv sayısını sızdırır ve silme sonrası numara çakışması yaratır.

Kullanıcıya gösterilen dostane ad ("Arşiv 1", "Arşiv 2") yalnızca UI'da üretilir; diskte karşılığı rastgele addır. Liste **dosya `mtime`'ına göre** sıralanır.

---

## 6. Taşıma, rollback ve anahtar durumu

- Kaynak kasa `Calypso Gallery` altındaysa hedef de aynı filesystem'dedir; `File.renameTo` **atomik ve anındadır**, veri kopyalanmaz. ADR 0003'teki uzun süren foreground operasyon gerekmez.
- **Harici depolama aygıtındaki bir kasayı arşivleyemezsiniz.** Arşiv cihaz diskinde (`Calypso Gallery/Archive/`) tutulur, farklı filesystem'ler arasında `File.renameTo` başarısız olur. İşlem hata vererek iptal edilir; kopyalama yapılmaz, hiçbir şey taşınmaz. Kullanıcıya gösterilecek not:
  > "Harici depolama aygıtındaki bir kasayı arşivleyemezsiniz. Eğer mevcut kasayı silmeden onunla aynı konumda başka bir şifreli kasa oluşturmak istiyorsanız mevcut kasayı arşivlemeden önce cihaz diskine taşımalısınız."
- Uygulama detayı: arşiv kaydı, rename'den **önce** tercihlere yazılır ve rename başarısız olursa geri alınır; böylece çökme durumunda kasa bulunamaz hâle gelmez. Çok adımlı bir ADR 0003 journal'ı yoktur; kalan adımlar (biyometrik temizleme, yeni kasa oluşturma, config geçirme) tek atomik rename'den sonra yapılır.
- Arşivlenen kasanın `.vault` başlığı ve `.bin` dosyaları **yol bağımlı değildir**; anahtarlar bozulmaz. Arşivlenen kasa daha sonra yalnızca **kendi parolasıyla** açılabilir.

---

## 7. Geri yükleme — basit kullanıcı için "kasalar yer değiştirir" modeli

Kullanıcıya şu cümle söylenir:

> "Arşivdeki bir kasayı tekrar kullanmak isterseniz **Ayarlar → Şifreli kasa → Kasa konumu → Arşivden kasa aç** seçeneğini kullanın. Listeden kasayı seçin. Seçtiğiniz kasa varsayılan konuma geri döner, o anki kasa arşive alınır. **Hiçbir şey silinmez.**"

Arşivden geri taşıma da arşivleme ile aynı cihaz sahibi doğrulamasını kullanır (§3.3): Class 3 biyometrik, yedekte cihaz parolası; kasa parolası gerekmez. Seçim penceresinde ayrıca tüm arşiv kasalarının toplam boyutunu gösteren bir i ikonu bulunur.

Bu modelde kullanıcı "aktif kasa" ve "arşiv" kavramlarını bilmek zorunda değildir; zihinsel modeli **"iki kasa yer değiştirir"**dir.

Akış:

1. "Kasa konumu" ayarında **"Arşivden kasa aç"** seçeneği gösterilir.
2. Özel tercihlerdeki `archivedVaults` listesi dostane ad + `mtime` ile gösterilir. Tercihler sıfırlanmışsa `Archive/*` taranır ve `CryptoManager.resolveExistingVaultInDirectory(dir)` ile geçerli `.vault` başlığı aranır; rastgele ad fallback etiketidir.
3. Kullanıcı bir kasa seçer; onay dialogunda sadece şu yazılır: *"Bu kasa varsayılan konuma taşınacak. Şu anki kasa arşive alınacak. Hiçbir veri silinmez. Devam edilsin mi?"*
4. Onaylanırsa ADR 0003 journal'ı ile, **aynı filesystem içinde atomik** iki rename yapılır:
   - `Secrets` → `Archive/<yeni-rastgele-id>` (mevcut kasa arşive gider),
   - `Archive/<secilen-id>` → `Secrets` (seçilen kasa varsayılan konuma döner).
5. `config.vaultRootPath` = `Secrets` olarak güncellenir; biyometrik durum temizlenir.
6. `VaultSecurityDialog` ile **seçilen kasanın parolası** istenir. Parola yanlışsa kullanıcı tekrar deneyebilir; kasa `Secrets` konumunda ve erişilebilir kalır, uygulama kilitli kalmaz.
7. Başarı bildirimi: *"Seçtiğiniz kasa varsayılan konuma döndü. Önceki kasanız arşivde korundu."*

Bu yaklaşımın avantajları:

- Kullanıcı "geri yükle" için klasör adı, rastgele id veya dosya yöneticisi bilmek zorunda değildir.
- Varsayılan konum her zaman **bir** kasa içerir; uygulamanın keşif mantığı (`findVaultRoot`) değişmez.
- Aksiyom korunur: iki rename de veri kopyalamaz/silmez; işlem geri alınabilir.
- Arşivden gelen kasa varsayılan konumda olduğu için biyometrik yeniden kurulum ve kasa kilidi akışları aynen çalışır.

Kullanıcı parolayı hatırlamıyorsa zaten bu akışı tamamlayamaz; böyle bir durumda "Parolamı unuttum" akışı yeniden kullanılabilir — o da mevcut kasayı arşivleyip yenisini oluşturur.

---

## 8. Disk alanı boşaltma

Uygulama içinde arşiv silme **yoktur** (aksiyom). Kullanıcıya gösterilir:

> "Arşivlenen kasa diskte yer kaplar. Yer açmak için şifreli kasa klasörünü bir dosya yöneticisiyle silebilirsiniz: **/storage/emulated/0/Calypso Gallery/Archive/<id>**."

- Yol her zaman **o an seçili kasanın/arşiv kaydının gerçek yolu** olarak gösterilir; sabit varsayılan yol yazılmaz (özel konumlar desteklenir).
- "Yolu kopyala" butonu olabilir; uygulama silme fiilini **gerçekleştirmez**.
- `Calypso Gallery` harici depolamanın kökündedir ve `Android/data` altında değildir; uygulama kaldırılsa da kalır. Bu yüzden kullanıcıya temizlik sorumluluğu açıkça anlatılır.

### 8.1. Arşiv kasasının konumu nerede görünür

Kullanıcı arşivlediği kasayı dosya yöneticisiyle silebilmek için **o kasanın klasörünü** (`Archive/<rastgele-id>`, sadece `Archive/` kökünü değil) görebilmelidir. Yol şu yerlerde gösterilir:

1. **Arşivleme başarı dialogu:** kısa onay + tam yol (seçilebilir `TextView`) + **Yolu kopyala** + Tamam.
2. **Kasa konumu → Arşivlenmiş kasalardan seç** listesi: her satırda dostane ad (*"1. arşivlenen kasa"*), arşiv tarihi (`info.txt` mtime) ve alt satırda `humanizePath` ile kısaltılmış yol. Yola dokununca tam yol gösterilir veya panoya kopyalanır.
3. **SSS uyarı paragrafı:** *(ait olduğu konum)* ifadesi §8'deki dinamik yolla değiştirilir.
4. `readme.txt` / `info.txt` içine **yol yazılmaz** (meta veri ihlali); oralarda yalnızca uygulama içi yönlendirme vardır.

Uygulama içinde silme **yoktur**; en fazla:

- **Yolu kopyala** (`ClipboardManager`) — yayımlanan tek mekanizma. SAF "Klasörü göster" butonu değerlendirildi ama v2'ye ertelendi; bu kararın parçası değildir.

Yol her zaman arşiv kaydından / gerçek klasörden alınır; sabit varsayılan yol yazılmaz. Uygulama yeniden kurulup özel tercihler silinse bile `Archive/*` taranarak yol yeniden üretilir (§4.4).

---

## 9. Arşivleme vs. dışa aktarma (başka cihaz)

İki işlem birbirine karıştırılmamalıdır:

| | **Arşivleme** | **Şifreli kasayı dışa aktar** |
|---|---|---|
| Amaç | Bu cihazda eski kasayı saklamak | Kasayı başka cihaza / masaüstü CLI'a **kopyalamak** |
| Yer | `Calypso Gallery/Archive/<rastgele-id>` | Kullanıcının seçtiği klasör veya harici disk |
| Kaynak kasa | **Taşınır** — orijinal konum boşalır | **Kopyalanır** — orijinal yerinde kalır |
| Şifreleme | Korunur | Korunur |
| Açılış | Bu cihazda "Arşivlenmiş kasalardan seç" | Hedef cihazda "Mevcut kasayı kullan" |
| Parola | Gerekir (kasa kendi parolasıyla açılır) | **Gerekmez** (§9.1) |

Kullanıcının amacı **"kasayı başka cihazda kullanmaksa"** izlenmesi gereken yol **dışa aktarmadır**; arşivleme bu amaca hizmet etmez. Arşiv yalnızca "ileride bu cihazda parolamı hatırlarsam geri döneyim" içindir.

SSS maddesindeki 2. adım ("Şifreli verilerinizi **kopyalayın (dışa aktarın)**") tam olarak bu ayrımı anlatır. Arşivleme onay dialogunda da şu hatırlatma yer alır: *"Başka bir cihazda kullanmak istiyorsanız Ayarlar → Şifreli kasayı dışa aktar seçeneğini kullanın. Bu işlem kopyalar; mevcut kasanız yerinde kalır."*

Böylece kullanıcı "eski kasayı nereye koydum?" diye düşünmek zorunda kalmaz: **aynı cihaz → arşiv, başka cihaz → dışa aktar.**

### 9.1. Dışa aktarma kilidi kaldırılıyor

`CryptoManager.exportEncryptedVault` saf bir dosya kopyalamasıdır (`copyTo`): **ne anahtar kullanır ne şifre çözer.** `.vault` dosyası zaten diskte düz bir dosya olarak durur; kullanıcı onu dosya yöneticisi veya MTP ile zaten kopyalayabilir. Dolayısıyla `promptExportEncryptedVault` içindeki `ensureVaultUnlocked` çağrısı **gerçek bir güvenlik sınırı değil, yalnızca gereksiz sürtünmedir.** Önceki "açık kasa şartı kalmalı" kararı bu gerekçeyle **tersine çevrilmiştir**.

Dışa aktarma için **kasa parolası / açık kasa şartı kaldırılır**. Yerine korunan kontroller:

- **cihaz sahibi doğrulaması**, güvenli ekran kilidi olan cihazlarda (Class 3 biyometrik, yedekte cihaz PIN'i/desen/parolası — §3.3). Bu yerel bir sürtünmedir, kasa anahtarı kapısı değildir: dışa aktarılan dosyalar şifreli kalır ve kasa parolası sorulmaz.
- hedef klasör seçimi (mevcut `pickCustomVaultDirectory`),
- kasayı kendi içine dışa aktarma engeli (mevcut canonical-path kontrolü),
- ilerleme dialogu,
- açık onay metni: *"Şifreli dosyalar kopyalanır. Parola gerekmez; veriler şifreli kalır."*

Kalıcı dışa aktarma, dosya yöneticisiyle kopyalamayla **aynı güvenlik seviyesindedir**; uygulama sadece bunu kolaylaştırır. Bu, aksiyomu (parolasız silme yasağı) ihlal etmez: dışa aktarma silme veya geçersiz kılma değil, **kopyalamadır**.

> **Çevrimdışı kaba kuvvet notu.** Kasa dosyaları parolasız kopyalanabildiği için (uygulamayla veya herhangi bir dosya yöneticisiyle) gizlilik tamamen `.vault` içindeki Argon2id parametrelerine ve kasa parolasının uzunluk/kalitesine bağlıdır. Yukarıdaki cihaz sahibi kontrolü yalnızca açık telefonu eline alanı caydırmaya yöneliktir; çevrimdışı saldırının gücünü **değiştirmez**.

Bu değişiklik sayesinde SSS'teki 2. adım ("Şifreli verilerinizi dışa aktarın"), parolasını unutan kullanıcı için de çalışır — kasanın açılması gerekmez. Arşivleme ile dışa aktarma ayrımı (§9) korunur: aynı cihaz → arşiv, başka cihaz → dışa aktar.

### 9.2. Terminoloji: "dışa aktar" bir kopyalamadır

"Dışa aktar" ifadesi kullanıcıda **taşıma** izlenimi veriyor; oysa işlem `copyTo` ile kopyalamadır ve kaynak kasa yerinde kalır. Bu yanılgıyı gidermek için:

- Ayar satırı ve SSS'te **"kopyala (dışa aktar)"** ifadesi kullanılır; yalnızca "dışa aktar" denmez.
- Dışa aktarma onay metninde açıkça yazılır: *"Dosyalar kopyalanır; mevcut kasanız yerinde kalır."*
- Arşivleme ile karşılaştırma tablosunda (§9) **kaynak kasa** satırı eklenir: arşivleme **taşır**, dışa aktarma **kopyalar**.
- Mevcut arayüz/CLI/dokümantasyondaki "export" terimi korunur (büyük çaplı rename gerekmez); **kullanıcıya yönelik metinlerde kopyalama vurgulanır.**

Böylece üç fiil net ayrılır: **arşivle = aynı cihazda taşı**, **dışa aktar = başka yere kopyala**, **sil = dosya yöneticisiyle kalıcı kaldır**.

---

## 10. Reddedilen alternatifler

1. **Parolasız "Şifreli kasayı sıfırla" — biyometrik veya cihaz parolasıyla yetkilendirmek de dahil:** aksiyomu ihlal eder; hizmet reddi riski. Reddedildi. Biyometrik kasayı açar ve yıkıcı olmayan arşivleme taşıması için cihaz sahibini doğrular (§3.3), ama yıkıcı sıfırlamaya asla yetki vermez.
2. **Uygulama içinde arşiv kasasını silme:** aksiyomu ihlal eder. Reddedildi; silme dosya yöneticisine bırakılır.
3. **Unutulmuş parola dialogunda "arşivlemeden sil ve devam et":** ikinci onaya rağmen uygulama içinde parolasız silme olur. Reddedildi; dialog yalnızca Arşivle / Vazgeç sunar (§3.2).
4. **Tarih/sayaçlı klasör adı:** meta veri ihlali ve çakışma riski. Reddedildi (§5).
5. **`.cache` gibi sahte ad:** yanıltıcı. Reddedildi.
6. **Kullanıcıyı doğrudan dosya yöneticisine göndermek:** basit kullanıcı için zor ve riskli. Reddedildi; arşivleme uygulama içinde otomatik yapılır.
7. **Yeni kasayı `Secrets-2` gibi yan konuma oluşturmak:** taşıma riskini azaltır ama "varsayılan konumda yeni kasa" hedefini karşılamaz. Reddedildi.

---

## 11. Doğrulama kriterleri

- Varsayılan konumda bir kasa varken "Parolamı unuttum" akışı **parola istemeden** arşivleme yapabilir; ancak **hiçbir kod yolu parolasız `delete()`/`wipe()` çağıramaz** (statik test).
- `Archive` klasörü uygulama kurulumunda yoktur; ilk arşivlemeden önce `exists() == false`.
- 1000 arşiv denemesinde çakışan ad üretilmez; çakışma simülasyonunda yeniden üretim çalışır.
- Taşıma ortasında süreç öldürülürse: kasa ya kaynakta ya arşivde erişilebilir, asla kayıp değil.
- Arşivlenen kasa "Arşivden kasa aç" ile **kendi parolasıyla** açılır; parolasız açılamaz.
- Arşiv klasör adında tarih/sayaç yoktur; `readme.txt`/`info.txt` hassas meta veri içermez.
- Yeni kasa varsayılan `Secrets` konumunda oluşur ve `config.vaultRootPath` yeni kasayı gösterir.
- Biyometrik kalıntı temizlenir: yeni kasa için `isVaultBiometricEnabled == false`.

---

## 12. Sıradaki adımlar

Bu ADR onaylandıktan sonra:

1. Akışın HTML maketi (ayar satırı, onay dialogu, arşiv listesi, yol bildirimi) hazırlanır.
2. `promptExportEncryptedVault` içindeki `ensureVaultUnlocked` çağrısı kaldırılır (§9.1).
3. Uygulama kodu ADR 0003 journal kalıbıyla yazılır.
4. Yeni string'ler 9 dile çevrilir ve `translation-blind-tests/` raporları güncellenir.
5. `FORK_CHANGELOG.md` altında yeni sürüm notu eklenir.

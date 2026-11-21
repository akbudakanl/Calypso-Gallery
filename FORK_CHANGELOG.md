# Değişiklik günlüğü

Calypso Gallery'nin kullanıcıya yönelik sürüm notları. Orijinal Fossify Gallery sürüm geçmişi `CHANGELOG.md` içinde tutulur.

## [Unreleased]

## [1.2.0] - 2026-11-22

### Eklendi

- Sanal albümler için özel ekleme/çıkarma işlemleri, albüm içi arama ve boş durum ekranı eklendi; dizinler, medya öğeleri ve görüntüleyici için bağlam eylemi kısayolları sunuldu.
- Şifreli kasa biyometrik hatırlatma simgesinin animasyonu, onaylanan prototipteki zamanlama ve parıltı görünümüne uyarlandı.

### Güvenlik ve kararlılık düzeltmeleri

- Kasa parola ve kilit ekranlarında sistem Geri tuşu artık hiçbir işe yaramıyor; uygulama içindeki tek çıkış yolu sol üstteki ok. Bu ok anahtar türetme (KDF) süresince devre dışı kalıp soluklaştırılıyor; böylece iptal edilen bir ekrana geç gelen açılış sonucunun uygulanması engelleniyor. Ok rengi temadan gelmeye devam ediyor.
- Başlatıcı simgesinin arka plan rengi temadan bağımsız sabitlendi; tema değiştiğinde simge renginin bozulması giderildi.

### İyileştirmeler

- Görünüm paleti, arama çubuğu stili ve kopyalama/taşıma politikası iyileştirildi; ilgili birim testleri genişletildi.
- Kasa etiketleri yeniden adlandırıldı; parola hatırlatıcı ayarı hedef açılış süresinin hemen altına taşındı, şifreli kasayı sıfırlama ayarı listenin sonuna alındı ve parola değiştirme satırındaki ok işareti kaldırıldı.

### Çeviriler

- Sanal albüm ve kasa etiketlerindeki yeni/değişen metinler 10 dilde çevrildi; kör çeviri karşılaştırma raporları güncellendi.

## [1.1.5] - 2026-11-21

### Güvenlik ve kararlılık düzeltmeleri

- Eski Reprint parmak izi kütüphanesi tamamen kaldırıldı ve uygulama/klasör kilidi AndroidX Biometric'e geçirildi; Android 15/16 çalışma zamanı sertleştirmeleri (foreground service zaman aşımı, display cutout/IME kenar boşlukları, güvenli alıcı kaydı) uygulandı.

### İyileştirmeler

- Şifreli kasa anahtar türetme motoru (Argon2id v1.3), yerel Rust JNI katmanına (`rust/calypso-argon2`, `RustCrypto/argon2` + `zeroize`) taşındı ve Bouncy Castle APK'dan çıkarıldı (~7,3 MB boyut azalması, 16 KB ELF sayfa hizalamalı 4 ABI, gerçek cihazda ~8,6–9,5× hız artışı). Fiziksel RAM + 64-bit kontrolüyle `64 → 128 → 256 MiB` (`>= 3,0 sn`) kademeli kalibrasyon ve 2 GB ve altı RAM cihazlar için yol gösterici uyarı eklendi (mevcut kasalarla bit-birebir uyumlu; bkz. [ADR 0004 §11.1](docs/adr/0004-argon2-memory-and-cross-device-kdf.md#111-update-2026-11-21--rust-native-argon2id-jni-9-implemented)).
- Minimalist Ayarlar düzeni, yeni görünüm temaları ve 9 dilde tam çeviri güncellemeleri.

## [1.1.4] - 2026-11-21

### Güvenlik ve kararlılık düzeltmeleri

- Android 12 (API 31) uyumluluğu: şifreli kasa oluştururken konum seçildiğinde açılan parola penceresi çöküyordu. Parola alanı, platformun reddettiği `*/*` MIME tipi yerine geçerli kategori tipleri (`text/*`, `image/*`, `video/*`, `audio/*`, `application/*`) ile kaydediliyor; pano ve içeriği yapıştırma engellemesi korunuyor.
- Parola alanlarında metin seçimi tamamen kapatıldı; temadan gelen yeşil seçim tutamacı artık görünmüyor. İmleç rengi ve karakter girişi etkilenmiyor.
- Şifreli kasa parolası değiştirilirken zayıf parola uyarısı artık gösteriliyor ve mevcut parolayla aynı bir parola seçilmesi açıkça uyarıyla reddediliyor.

### İyileştirmeler

- Ekran yönü değiştiğinde parola pencerelerinin ve uygulama pencerelerinin kapanmasına yol açan davranış giderildi: ilgili activity'ler yön değişikliğinde yeniden oluşturulmuyor, parola pencereleri de dışarı dokunma/Geri tuşuyla sessizce kapatılamıyor.
- Otomatik oynatma açıkken, video ölçüleriyle ekran yönü uyuşmadığında önbelleğe alınan önizleme karesinin en-boy oranı bozuluyordu. Kare artık tek ölçek faktörüyle alınıyor; oynatma başlamadan önceki kısa önizleme videonun oranlarını koruyor.
- Uygulamanın orijinal parola korumaları (albüm kilidi, uygulama kilidi, hariç tutulan klasörler, taşıma/silme koruması) için kullanılan parola penceresinde Desen ve PIN sekmeleri geri geldi; dikey tablette desenin son satırı kesilmiyordu, artık görünür.

## [1.1.2] - 2026-11-21

### Güvenlik ve kararlılık düzeltmeleri

- Şifreli kasa oluşturma: zayıf parola uyarısında "Yine de devam et" seçildikten sonra parola tekrarında aynı parolanın hatalı sayılması düzeltildi; zayıf parola uyarısı güç çubuğuyla uyumlu hale getirilerek yalnızca "Çok zayıf" ve "Zayıf" seviyelerinde gösterilir ("Orta" seviyesinde gösterilmez).
- Alfanümerik kasa parola alanında kopyalama, kesme, yapıştırma, metin seçimi, bağlam menüsü, sürükle-bırak ve klavye kısayolları güvenlik gereğince devre dışı bırakıldı.

### İyileştirmeler

- Tablet ekran uyumu: karşılama ekranı ekran yönü değiştiğinde mevcut adımı koruyarak yeni düzene geçer, yatay karşılama logosu kırpılmadan hizalanır ve 600dp / 900dp ekran genişlikleri için galeri sütun sayıları dengelendi.

## [1.1.1] - 2026-11-21

### Eklendi

- Tablet ve büyük ekran uyumu: karşılama ve izin ekranları, şifreli kasa giriş/kilit ekranı ile Ayarlar, Görünüm ve Hakkında ekranlarında maksimum içerik genişliği ve ortalama eklendi. Yatay tabletlerde karşılama ekranı iki sütunlu düzene geçer; şifreli kasa formu tabletlerde yarı saydam buzlu cam kart içinde ortalanır.
- Şifreli kasa oluşturma: ilk kez kasa kurulurken zayıf parola veya PIN seçildiğinde onay adımından önce uyarı penceresi gösterilir.

### İyileştirmeler

- Kasa bildirim pencerelerinde geri tuşu ile kapatma davranışı tutarlı hale getirildi; dışarı dokunarak yanlışlıkla kapanma engellendi.

## [1.1.0] - 2026-11-21

### Eklendi

- Dosya işlemleri: Akıllı diyalog veya Otomatik modda adı, bit düzeyinde içeriği ve tarihi birebir aynı olduğu için sessizce atlanan dosyalar, işlem sonunda kısa bir "N aynı dosya atlandı" toast'ıyla bildirilir. "İçerik aynı, tarih farklıysa → Her zaman sor" seçiliyse bu dosyalar thumbnail'li çakışma penceresinde kullanıcıya gösterilir ve onaylandığı için toast çıkmaz; yalnızca üçü de aynı olan dosyalar bildirilir. Otomatik (Deneysel) modda "Her zaman sor" seçeneği devre dışıdır ve bu moda geçildiğinde "Hedefteki dosyanın tarihi kalsın (varsayılan)" seçeneğine geri dönülür.

### Güvenlik ve kararlılık düzeltmeleri

- Class 2 (zayıf) biyometriğin şifreli kasa erişimi için desteklenmediğine dair açık not eklendi; güçlü biyometrik olmayan cihazlarda ayar sessizce gizlenmek yerine devre dışı görünür ve nedeni yerelleştirilmiş bir pencerede anlatılır (bkz. [ADR 0006](docs/adr/0006-strong-biometrics-only.md)).
- Android 16 (API 36) uyumluluğu: büyük ekranlarda (sw ≥ 600dp) sistem yön kısıtlarını yok saydığı için tabletlerde etkisiz olan "yataya zorla / dikeye zorla" menüleri, alt aksiyon düğmesi ve ilgili ayar gizlendi.

## [1.0.0] - 2026-11-19

İlk genel sürüm. Fossify Gallery 1.14.2 temelinden bağımsız geliştirilen, çevrimdışı çalışan ve kanonik Tink şifreleme biçimini kullanan bir galeri uygulamasıdır.

### Eklendi

- Sanal albümler: medyayı diskte çoğaltmadan referansla gösterir; favoriler ve sanal albümler klasör yeniden adlandırma veya taşıma sonrasında da korunur.
- Dosya ve klasör kimliği: MediaStore kimliği, boyut, değiştirme zamanı, içerik parmak izi ve klasör inode'u ve komşu çapa imzalarıyla taşınan dosyalar yeniden bulunur; aynı adlı klasörler birbirine karışmaz.
- Şifreli kasa: Tink AES256_GCM_HKDF akış şifrelemesi, Argon2id anahtar türetme, rastgele DEK'li konteynerler, KEK anahtar halkası ve `CalypsoGallery` alan ayırımı ile kanonik konteyner biçimi.
- Kasa güvenliği: biyometrik kilit (donanım tabanlı anahtarlar), kaba kuvvete karşı ayarlanabilir Argon2 hedef süresi, kasa geri dönüşüm kutusu, sıfırlama hareketiyle kilit, RAM temizliği, ekran görüntüsü ve ekran yakalama engelleme, uygulama geri yüklendiğinde yeniden kimlik doğrulama.
- Çevrimdışı çalışma: ağ izni kaldırıldı, tüm ağ özellikleri devre dışı; gizli içerik için ekran koruyucu ve pano görüntü kopyalama engelleme.
- Masaüstü şifreleme aracı: çevrimdışı/Tails uyumlu CLI, harici kasa konumu, yerel telefona aktarım, `.pending_sync` artımlı senkronizasyon ve uygulamayla birebir uyumlu kanonik konteyner üretimi (payload'da gerçek Tink Python kütüphanesi kullanılır).
- Dosya işlemleri: bit düzeyinde yineleme ayıklama, akıllı/standart çakışma pencereleri, önizleme ve bayt/tarih karşılaştırması, boş albüm temizliği, büyük dosyalar için hızlı kontrol, eşleşen içerik için tarih eşitlik ayarı.
- Favori ve sanal albüm yedekleme: otomatik yedekler, ZIP dışa/içe aktarma, çakışma birleştirme ve kasa geri dönüşüm kutusu istisnaları.
- Arayüz: uygulama içi dil seçici, albüm türü simgeleri, gizle/şifrele ve hariç tut akışlarının ayrılması, bağımsız görünüm modları, parmak kaydırmalı kasa açma, arama ve görünüm tercihleri.
- Kasa kilidi: uygulamaya dönüldüğünde biyometrik istemi gösterme davranışı varsayılan olarak kapalı, ayarlardan açılabilir; kilit ekranındaki geri butonu kasayı kilitler, görsel önbellekleri temizler ve normal galeriye döner.

### Güvenlik ve kararlılık düzeltmeleri

- Biyometrik hata sınıflandırması: anahtarın kalıcı olarak geçersiz olması, cihazın kilitli olması ve geçici Keystore hataları ayrım yapılır. Geçici hatalarda anahtar ve biyometrik düğmesi korunur; otomatik istemler yalnızca pencere odakta ve cihaz açıkken tetiklenir, geçersiz anahtar geçerli bir CryptoObject olmadan asla açılış sağlamaz.
- Kasa geri dönüş davranışı: otomatik biyometrik istemi ayarı dışa/içe aktarma dahil tüm ayar akışlarına bağlandı; kilit ekranı düzeni geri butonu ve kaydırılabilir içerik için yeniden düzenlendi.
- Favori yedekleri: diskteki boyut eşleştiğinde yedekteki parmak izi ve kimlik bilgileri yeniden kullanılarak dosya taşıma sonrası favori kimliği korunur.
- Albüm ve izin akışları: boş dizin listelerinde geçersiz DB dizini temizliği atlanır, `GROUP_BY_NONE` seçiliyken albüm kapağı oluşturma sıralaması bozulmaz, yinelenen medya izin istekleri engellenir.
- Biyometrik hata sınıflandırması ve kasa kilit politikası için regresyon testleri eklendi.

### Orijinal uygulamadan gelen düzeltmeler

- Video oynatma konumu kaydırma sırasında korunur; ilk oynatma ve geri sarma takılmaları, tekrar döngüsü ve otomatik oynatma çakışması giderildi.
- Oynatma simgesi video başladıktan sonra duraklatılmış kareyi kapatmaz; dokunmatik oynatma simgesi yalnızca kullanıcı kaynaklı duraklamada görünür.
- Albüm ızgarasını yavaşlatan gereksiz ortam taramaları kaldırıldı; medya gözlemcisi değişiklikleri süzülerek gereksiz yenilemeler engellendi.
- Görünüm yenileme sırasında çoklu seçim korunur, rastgele sıralama sabit kalır, gereksiz sıralama ve arka plan indeksleme kaldırıldı.
- Yatay kaydırma ile aşağı çekerek yenileme birlikte çalışır; sabitleme/çözme simgeleri ve seçim menüleri standartlaştırıldı.
- Farklı paket adı nedeniyle gösterilen yanlış "uygulama bozuk" uyarısı kaldırıldı.

### Çeviriler

- 11 farklı dilde tam kapsamlı çeviri denetimi.

### Teknik ve yasal

- Kapsamlı mimari dokümantasyonu (DOCS.md, ADR'ler), yasal lisans ve atıflar (GPLv3, Tink, Bouncy Castle ve üçüncü taraf bileşenler), marka yeniden adlandırma ve ilk genel sürüm için sürüm/tooling sıfırlaması.
- Kullanılmayan eski `Picasso`, `OkHttp` ve `Okio` bağımlılıkları ile yedek kodlar kaldırıldı; üçüncü taraf lisansları tek ekranda birleştirildi.
- Uygulama tek sürüm davranışıyla sunulur; kod tabanı sadeleştirilmiştir.

[1.1.4]: https://github.com/akbudakanl/Calypso-Gallery/releases/tag/1.1.4
[1.1.2]: https://github.com/akbudakanl/Calypso-Gallery/releases/tag/1.1.2
[1.1.1]: https://github.com/akbudakanl/Calypso-Gallery/releases/tag/1.1.1
[1.1.0]: https://github.com/akbudakanl/Calypso-Gallery/releases/tag/1.1.0
[1.0.0]: https://github.com/akbudakanl/Calypso-Gallery/releases/tag/1.0.0

[Unreleased]: https://github.com/akbudakanl/Calypso-Gallery/compare/1.2.0...HEAD
[1.2.0]: https://github.com/akbudakanl/Calypso-Gallery/compare/1.1.5...1.2.0

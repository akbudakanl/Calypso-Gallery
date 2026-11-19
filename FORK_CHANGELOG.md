# Değişiklik günlüğü

Calypso Gallery'nin kullanıcıya yönelik sürüm notları. Önceki sürüm yayınlanmadığı için bu dosya yalnızca ilk genel sürümü (1.0.0) kapsar. Orijinal Fossify Gallery sürüm geçmişi `CHANGELOG.md` içinde tutulur.

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

[1.0.0]: https://github.com/akbudakanl/Calypso-Gallery/releases/tag/1.0.0

# Değişiklik günlüğü

Calypso Gallery'nin kullanıcıya yönelik sürüm notları. Orijinal Fossify Gallery sürüm geçmişi `CHANGELOG.md` içinde tutulur.

## [Unreleased]

## [1.0.0] - 2026-11-22

İlk genel sürüm. Aşağıdaki maddeler, fork boyunca yapılan yüzlerce değişikliği kullanıcıya görünen ana hatlarıyla özetler; tek tek commit'ler yerine konseptler listelenmiştir.

### Eklendi

- **Şifreli kasa:** Tink akışlı AEAD ile uçtan uca şifreleme, Argon2id anahtar türetme, biyometrik açılış, parola değiştirme, kasa içe/dışa aktarma, arşivleme ve unutulan parola kurtarma akışı.
- **Sanal albümler ve favoriler:** Dosyaları kopyalamadan referansla gösterme, klasör taşıma veya yeniden adlandırma sonrası yol onarımı, otomatik yedekleme ve zip olarak içe/dışa aktarma.
- **Dosya işlemleri:** Kopyalama, taşıma, şifreleme, çözme ve silme için tek bir arka plan bildirimi, ilerleme diyaloğu ve kesintiye uğrayan işlemleri sürdürme.
- **Akıllı çakışma yönetimi:** Bayt bayt karşılaştırma, önizlemeli yan yana diyalog, otomatik tekilleştirme ve albüm birleştirme.
- **Görüntüleyici ve medya:** Şifreli medya, GIF, video ve SVG görüntüleme; albüm bazlı karıştırma, video denetimleri ve kaydırma iyileştirmeleri.
- **Ayarlar ve arayüz:** Uygulama içi dil seçimi, açık/koyu/özel tema paletleri, ayar araması, kategori kartları, Calypso açılır menüsü ve ilk kullanım karşılama akışı.
- **Tablet ve büyük ekran uyumu:** Duyarlı ızgara sütunları, geniş ekran diyalogları ve yatay düzen iyileştirmeleri.
- **Masaüstü aracı:** Komut satırı ile şifreli kasa oluşturma/çözme, harici kasa eşitleme ve Tails OS üzerinden çevrimdışı kullanım.

### Değiştirildi ve yeniden tasarlandı

- **Uygulama tasarımı revize edildi:** Renk paleti, ikon seti, üst ve alt çubuklar, seçim eylem çubuğu, karşılama kartları, kasa kilit ekranı ve açılır menüler tek bir görsel dilde yeniden tasarlandı.
- **Şifreleme altyapısı yeniden kuruldu:** Asimetrik RSA kaldırıldı ve kasa konteyner biçimi tek sürüme indirgendi. Anahtar türetme önce BouncyCastle, ardından yerel Rust/Argon2id JNI ile değiştirildi; Poly1305 doğrulaması ve Tink akışlı AEAD benimsendi. Parola güç göstergesi tamamen cihazda çalışıyor.
- **Depolama düzeni:** Kasa verileri `Secrets` klasörüne, favori ve sanal albüm yedekleri uygulama klasörüne taşındı; harici kasa konumu ve telefonla eşitleme eklendi.
- **Dağıtım ve kimlik:** Uygulama kimliği `com.calypso.gallery` olarak değiştirildi; marka adı, ikon ve GPLv3 ile üçüncü taraf lisans ekranları eklendi.

### Yukarı akış (Fossify Gallery) uyumluluğu ve hata düzeltmeleri

- Android 15/16 çalışma zamanı sertleştirmesi; yeni API uyumluluğu için tabletlerde zorla ekran yönü belirleme kapatıldı.
- Yukarı akıştaki bağış, puanlama, "Teşekkürler" ve sahte uygulama denetimi istemleri devre dışı bırakıldı.
- Yukarı akışta görülen kilit açma çökmeleri, büyük GIF küçük resimleri, video oynatma denetimleri ve MediaStore gözlemcisi kaynaklı gecikmeler giderildi.
- Bağımlılıklar ve dokümantasyon, yukarı akışın `v1.14.2` tabanına göre güncellendi.

### Güvenlik ve gizlilik

- Kasa ekranlarında ekran görüntüsü engelleme, pano ve ekran paylaşımı sızıntılarının kapatılması; kilit sonrası RAM temizleme.
- Ağ izni tamamen kaldırıldı; uygulama yalnızca çevrimdışı çalışır.
- Dosya aktarımında hazırlama alanı, atomik değiştirme, geri okuma doğrulaması ve kesinti sonrası kurtarma.

### Çeviriler ve doğrulama

- Arayüz metinleri 34 yerel ayara kadar çevrildi; kör çeviri denetimi ve birebir karşılaştırma raporları `translation-blind-tests` klasöründe tutuluyor.
- Birim testleri: kripto vektörleri, kasa biçimi, dosya kimliği, çakışma çözümü ve arayüz davranışları.
- Mimari karar kayıtları `docs/adr` altında; güvenlik denetimi ve tasarım belgeleri çok dilli olarak sunuluyor.

[Unreleased]: https://github.com/akbudakanl/Calypso-Gallery/compare/1.0.0...HEAD
[1.0.0]: https://github.com/akbudakanl/Calypso-Gallery/releases/tag/1.0.0

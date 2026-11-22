# Değişiklik günlüğü

Calypso Gallery'nin kullanıcıya yönelik sürüm notları. Orijinal Fossify Gallery sürüm geçmişi `CHANGELOG.md` içinde tutulur.

## [1.0.0] - 2026-11-22

### Eklendi

- Tink akışlı AEAD ile şifreli kasa: Argon2id anahtar türetme, biyometrik kilit açma, parola değiştirme ve kasayı içe/dışa aktarma.
- Şifreli kasa için yalnızca cihazda çalışan parola güç göstergesi ve güçlü biyometri politikası.
- Sanal albümler ve favoriler: dosyaları kopyalamadan referansla gösterme, klasör adı/değişimi sonrası yol onarımı, yedekleme ve içe aktarma.
- Çevrimdışı çalışma: ağ izni yok, bulut bağımlılığı yok; tüm veriler cihazda.
- Dosya işlemleri için tek bir arka plan bildirimi ve ayrıntılı ilerleme gösterimi; kopyalama, taşıma, şifreleme, çözme ve silme işlemleri.
- Karakter sınıfı entropisi, sözlük/tekrar/dizi ve klavye yürüyüşü cezaları içeren yerel parola güç ölçümü.
- İlk kullanım karşılama ekranı, tam dosya erişimi izin akışı ve kasa kurulum eğitimi.
- Tam ekran görüntüleyici, sanal albüm rozetleri, akıllı çakışma diyalogları ve masaüstü şifreleme aracı (CLI).
- Ayrı Ayarlar görünümü, açık/koyu/özel tema paletleri, ayar araması ve Calypso açılır menüsü.
- Uygulama içi GPLv3 ve üçüncü taraf lisans ekranları.

### Güvenlik ve kararlılık

- Şifreli kasa içeriği için ekran görüntüsü engelleme, pano temizleme ve kilit sonrası RAM temizleme.
- Aktarım öncesi bit-bit doğrulama ve read-back doğrulaması ile doğrulanmamış çıktıların güvenli silinmesi.
- Kasa dosyaları için atomik değiştirme, bütünlük doğrulaması ve sürüm göçü.
- Favori ve sanal albüm referansları için sabit dosya kimliği ve kirletmeyen (zero-pollution) klasör kimliği.

### Çeviriler

- Arayüz metinleri Türkçe ve çok dilli kör test yerel ayarlarında tamamlandı.

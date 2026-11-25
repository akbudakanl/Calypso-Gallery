# Yapılacaklar Listesi (TODO)

## Şifreli Kasa & Gizlilik

### [x] Harici depolama (SD kart / USB OTG) kullanan şifreli kasalar için dahili `Pictures/` fallback'ini engelleme

- **Durum:** Sessiz `Pictures/` fallback'i kaldırıldı. `.origin` kaydı bulunmayan veya harici SD kart / USB OTG konumu erişilemeyen albümlerde kullanıcıya doğrudan uygulamadaki hedef klasör seçici (`PickDirectoryDialog`) açılıyor; böylece gizli medyalar hiçbir zaman kullanıcının izni olmadan dahili `Pictures/` klasörüne yazılmıyor.
- **Gelecek İyileştirme Fikri:** İsteğe bağlı olarak Ayarlar > Gizli Kasa altında, harici kasadan şifre çözülürken hedef seçicide yalnızca harici depolama yollarına izin veren ekstra bir kısıtlama seçeneği de eklenebilir.

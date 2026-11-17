# Crypto design review — Calypso Gallery 1.0.0

Kısa tasarım gözden geçirme notu. Uygulamanın kullandığı primitifler ve dosya formatı hakkında
`DOCS.md` bölüm 2 ve ADR 0001 referans alınmalıdır. Bu belge **tehdit modeli**, **AAD
gerekçeleri** ve **bilinçli sınırlamalar** üzerine odaklanır.

> **Formal durum:** Bu formata bağımsız, harici bir kriptografik denetim uygulanmamıştır.
> Tink, AES-256-GCM ve Argon2id gibi iyi bilinen primitifler kullanılsa da, konteyner yerleşimi,
> AAD yapısı ve anahtar hiyerarşisi proje özgüdür. Çapraz dil golden fixture testleri
> implementasyon hatalarını yakalar; tasarım kusurlarını kanıtlamaz.

## 1. Varlıklar ve tehdit modeli

| Varlık | Koruma hedefi |
|---|---|
| Medya payload'ı (`*.bin` içindeki Tink akışı) | Gizlilik + bütünlük; parça parça doğrulama |
| Dosya başlığı (ad, boyut, tarih, thumbnail, DEK) | Gizlilik + başlığın kendi dosyasına bağlanması |
| `.vault` başlığı (VMK, KEK keyring, Argon2 parametreleri) | Çevrimdışı brute-force direnci + anahtar bütünlüğü |
| Klasör `.meta` (şifreli klasör adı) | Gizlilik |
| `FVSTR:` kayıtları (albüm adı, JSON, resume) | Gizlilik + kategoribazında ayrım |

**Düşman modeli:** cihaz veya USB/SD üzerinde şifreli dosyalara erişimi olan, ancak çalışan
uygulamanın belleğine veya Android Keystore'a erişemeyen bir saldırgan. Çevrimdışı kaba kuvvet,
dosya değiştirme/değiştirme (swap), kesme/uzatma ve kategoriler arası kayıt taşıma kapsamdadır.
Bellek, işletim sistemi çekirdeği ve yasal zorlama bu modelin dışındadır.

## 2. Anahtar hiyerarşisi

```
parola/PIN --Argon2id(salt, params)--> PDK --AES-GCM--> VMK        (.vault)
VMK --AES-GCM--> keyring { keyId -> KEK }                         (.vault)
KEK --HKDF(domain|fileId)--> headerKey --AES-GCM--> başlık (içinde DEK)
KEK --HKDF(domain|purpose)--> metadataKey --AES-GCM--> FVSTR: kaydı
DEK --Tink AES256_GCM_HKDF(fileId AAD)--> payload
VMK --AES-GCM--> klasör .meta
Keystore (opsiyonel, donanım) --AES-GCM--> VMK                     (biyometrik)
```

## 3. Veri (payload) şifrelemesi

- Her dosya için rastgele 16 bayt `fileId` ve 32 bayt DEK üretilir.
- DEK, Tink `AesGcmHkdfStreaming` primitifine verilir; AAD = `fileId`.
- Tink akışı 40 baytlık header (salt + noncePrefix) yazar, 256 KiB segmentlerle şifreler,
  her segmentin GCM etiketini doğrular ve kesin uzunluk kontrolü yapar.
- Başlıkta DEK ve dosya bilgileri saklanır; payload başlığa **bit-identical** kopyalanabilir
  (rename ve KEK rotasyonu bunu kullanır).

## 4. Metadata şifrelemesi ve AAD gerekçeleri

| Kanal | AAD | Neden |
|---|---|---|
| Konteyner başlığı | `magic \| version \| keyId \| fileId \| N` | Başlık başka bir dosyaya, sürüme veya keyId'ye taşınamaz. `N` (şifreli başlık uzunluğu) dahil edilerek uzunluk karışıklığı engellenir. IV ayrıca GCM nonce'u olduğundan AAD'ye gerek yok; değiştirilmesi tag'i bozar. |
| `FVSTR:` kaydı | `CalypsoGallery:Metadata:AESGCM \| purpose` | `purpose` açıkta saklanır ve AAD olarak bağlanır; bir albüm adı JSON olarak, bir resume kaydı gibi okunamaz. |
| `.vault` keyring | AAD yok | Salt/Argon2 parametreleri AAD'de değil; yanlış parametre yanlış PDK üretir ve AEAD doğrulaması zaten başarısız olur. Saldırgana brute-force avantajı vermez. |
| Klasör `.meta` | AAD yok | Dosya başka bir klasöre kopyalanırsa yine çözülür; etkisi yalnızca görünen klasör adıdır, veri gizliliği/bütünlüğü etkilenmez. |

Bilinçli sınırlama: aynı dosyanın eski bir başlığına geri dönme (rollback) tespit edilmez;
taşınabilir bir dosyada güvenilir monoton durum veya sabit header yok. Bu, kabul edilen ve
belgelenmiş bir sınırlamadır (ADR 0001).

## 5. KEK rotasyonu

- Format, birden fazla keyId içeren çoklu keyring destekler.
- Rotasyon yeni KEK'i keyring'e ekler ve `.vault`'a **ÖNCE** yazar; eski anahtarlar korunur.
- Her başlık yeni KEK ile yeniden şifrelenir, payload aynen kalır; dosya başına atomik değiştirme
  ve bütünlük doğrulaması yapılır.
- İşlem yarıda kesilirse yeni keyId zaten diskte olduğu için yarıda kalan dosyalar okunabilir;
  sonraki denemede bu dosyalar atlanır (idempotent).
- Eski anahtarlar keyring'de tutulur; böylece yedeklenmiş/arkivlenmiş dosyalar okunmaya devam eder.

Rotasyon, ele geçirilmiş bir anahtarın **gelecekteki** kullanımını engeller; saldırganın zaten
kopyaladığı şifreli veriyi geçersiz kılmaz.

## 6. Doğrulama

- Golden fixture'lar Python CLI ile üretilir, Android uygulaması tarafından okunur (`CliFixtureCompatibilityTest`).
- Gerçek Tink Java/Python kütüphaneleriyle çapraz doğrulama yapılmıştır.
- Test kapsamı: segment sınırı boyutları, bozuk segment/uzatma/kesme, başlık değiştirme, keyId
  geçişi, çoklu keyring, rotasyon ve payload'ın bit-identical kalması.

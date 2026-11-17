# ADR 0005 — KEK rotasyonu (ERTELENDİ / uygulanmadı)

- **Durum:** Proposed — **1.0.0 sürümüne alınmadı**
- **Tarih:** 2026-11-18
- **İlgili:** [ADR 0001](0001-tink-streaming-aead-migration.md) (kanonik konteyner formatı), [ADR 0002](0002-operational-security-hardening.md) (operasyonel güvenlik)
- **Gerekçe:** Anahtar rotasyonu PCI DSS 3.6/12.3, ISO 27001 A.8.24 ve NIST SP 800-57 gibi standartlarda beklenen bir kontrol. Format bu özelliği tasarım olarak destekliyor; uygulaması yeterince güvenli bulunmadığı için ertelendi.

> **Bu ADR bir tasarım kaydıdır. Kodda karşılığı yoktur.** 1.0.0'da tek keyring ve tek aktif KEK vardır. Bu belge, rotasyonu güvenli biçimde uygulamak için gereken tüm ayrıntıları ve **önce çözülmesi gereken üç kritik sorunu** içerir.

## 1. Neden rotasyon

- Bir KEK'in sızdığını düşünüldüğündegelecek kullanımını engellemek.
- Kriptografik anahtar ömrü politikalarını karşılamak.
- Eski yedekleri/arkivlenmiş dosyaları okunabilir tutarken yeni veriyi yeni anahtarla yazmak.

Rotasyon, **saldırganın zaten kopyaladığı şifreli veriyi geçersiz kılmaz**; o ancak düz metinden yeni anahtarla yeniden şifreleme ve eski kopyaların imhasıyla mümkündür.

## 2. Anahtar hiyerarşisi ve keyring

```
parola --Argon2id(salt, params)--> PDK --AES-GCM--> VMK        (.vault)
VMK --AES-GCM--> keyring { keyId -> KEK }                     (.vault)
KEK[keyId] --HKDF(domain|fileId)--> headerKey --AES-GCM--> başlık (DEK içerir)
KEK[keyId] --HKDF(domain|purpose)--> metadataKey --AES-GCM--> FVSTR: kaydı
DEK --Tink AES256_GCM_HKDF(fileId AAD)--> payload
```

**Keyring formatı (çoklu anahtar):**
```
activeKeyId(u16) | keyCount(u16) | [ keyId(u16) | KEK(32) ] × keyCount
```

Tek anahtarlı durum için eski/basit format korunur: `activeKeyId(u16) | KEK(32)`.

## 3. Rotasyon prosedürü (tasarım)

1. Kasa açık olmalı; `unlockedKeyring` boş olmamalı.
2. `newId = max(mevcut keyId) + 1`; rastgele 32 bayt `newKek`.
3. **Yeni keyring'i `.vault`'a önce yaz** (eski anahtarları koru). Böylece işlem yarıda kesilirse hem eski hem yeni keyId'li dosyalar okunabilir.
4. Tüm `.bin` dosyalarını gez:
   - `readMetadata(file)` dosyadaki keyId'ye göre doğru KEK'i seçmeli.
   - Dosyanın keyId'si zaten `newId` ise atla (kesinti sonrası idempotent davranış).
   - `VaultContainerWriter.rewriteContainer(...)` ile başlığı `newKek`/`newId` altında yeniden şifrele; **DEK, fileId ve Tink payload'u aynen korunur**.
   - Geçici dosyayı tam okuma + Tink bütünlük doğrulamasıyla doğrula.
   - Atomik `move(REPLACE_EXISTING)` ile yerine koy; metadata cache girdisini düşür.
5. Bellekteki aktif anahtarı `newId` yap. Eski anahtarları keyring'de tut (arkivlenmiş/yedeklenmiş dosyalar için).
6. İşlem boyunca kullanıcı dosya işlemleri **kilitlenmelidir**.

## 4. Önce çözülmesi gereken kritik sorunlar

1. **`FVSTR:` kayıtlarında keyId yok sayılıyor (veri erişimi riski).**
   `decryptStringFromVault` kayıttaki `keyId` alanını okuyup yok sayıyor ve anahtarı her zaman `activeKek()`'ten türetiyor. Rotasyondan sonra eski KEK ile yazılmış albüm adları, stealth JSON, restore manifestleri ve resume blob'ları çözülemez; gizli sanal albümler bozuk görünebilir veya düşürülebilir.
   **Çözüm:** kayıttan `keyId` oku ve `kekFor(keyId)` ile çöz. Yeni kayıtlar aktif keyId ile yazılsın.

2. **Thumbnail kaybı.**
   Rotasyon başlığı `includeThumbnail = false` ile okuyup `rewriteContainer` çağrısında `thumbnail = null` geçiriyor → gömülü küçük resimler siliniyor.
   **Çözüm:** `includeThumbnail = true` ile oku, thumbnail byte'larını yeniden yaz.

3. **Eşzamanlılık.**
   Rotasyon `isOperationInProgress()` kilidini almıyor; kullanıcı aynı anda dosya silebilir/taşıyabilir.
   **Çözüm:** rotasyon süresince operasyon kilidi veya eşdeğer bir serialization mekanizması.

4. **Keyring sınırı (düzeltildi ama tasarım notu).**
   Her rotasyon bir anahtar eklediğinden sınır 256 olarak belirlendi. 255 rotasyondan sonra yeni anahtar eklenemez; kullanıcı düz metinden yeniden şifreleme yapmalı veya eski anahtarlar emekli edilmelidir.

## 5. Test planı (zorunlu)

- Payload bit-identical kalıyor; eski KEK ile yeni başlık açılamıyor.
- Çoklu keyring: aktif anahtar doğru seçiliyor, eski anahtarlar korunuyor.
- `FVSTR:` rotasyon önce/sonra çözülebiliyor (album, JSON, resume, restore).
- Thumbnail korunuyor.
- Kesinti simülasyonu: N dosyadan sonra durdur → yeniden başlat → kalan dosyalar atlanır/tekrar yazılır, hiçbiri okunamaz kalmaz.
- Bozuk/yarım yazılmış başlık ve `.vault` keyring'i reddediliyor.
- Eşzamanlı dosya işlemleri rotasyonla çakışmıyor.

## 6. Karar

Rotasyon **1.0.0'da uygulanmaz**. Yukarıdaki üç kritik sorun çözülüp test planı tamamlanmadan sürüme alınmamalıdır. Bu ADR, ilerideki bir sürüm için uygulama rehberi olarak korunur.

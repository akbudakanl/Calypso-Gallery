# ADR 0005 — KEK rotasyonu

- **Durum:** Rejected — **Bireysel / yerel kullanım modeli için gerekli görülmedi; kurumsal ihtiyaçlar için referans tasarım olarak korundu (2026-11-18)**
- **Tarih:** 2026-11-18
- **İlgili:** [ADR 0001](0001-tink-streaming-aead-migration.md) (kanonik konteyner formatı), [ADR 0002](0002-operational-security-hardening.md) (operasyonel güvenlik), [ADR 0003](0003-resumable-file-operations.md) (kesintiye dayanıklı işlemler), [ADR 0004](0004-argon2-memory-and-cross-device-kdf.md) (KDF kalibrasyonu ve `.vault` yeniden sarma)

> **Özet Karar.** Calypso Gallery'nin bireysel/yerel mobil kasa mimarisinde KEK (Key Encryption Key) rotasyonu **reddedilmiştir (Rejected)**. Mevcut anahtar hiyerarşisi zaten dosya başına benzersiz türetilmiş anahtarlar kullandığından tekil KEK altında kriptografik aşınma oluşmaz; buna karşın toplu KEK rotasyonu yüksek veri/metadata/senkronizasyon kaybı riski doğurmaktadır. Aşağıdaki §2 bölümü reddedilme gerekçelerini, §3 bölümü mevcut formatın ileriye dönük uyumluluğunu, §4–§5 bölümleri ise ileride kurumsal/uyumluluk (PCI DSS, ISO 27001, NIST SP 800-57) gereksinimleri doğarsa uygulanacak referans mimariyi belgeler.

---

## 1. Mevcut Anahtar Hiyerarşisi

```
parola / PIN ──► Argon2id(salt, params) ──► PDK   (.vault içinde VMK'yı sarar)
                                             │
                                       AES-256-GCM
                                             ▼
                                            VMK   (.vault içinde keyring'i sarar)
                                             │
                                       AES-256-GCM
                                             ▼
                                      Keyring { keyId = 1 → KEK }
                                             │
        ┌────────────────────────────────────┴────────────────────────────────────┐
        ▼                                                                         ▼
  HKDF-SHA256(KEK[keyId],                                             HKDF-SHA256(KEK[keyId],
    "CalypsoGallery:Header:AES256GCM" ‖ hex(fileId))                   "CalypsoGallery:Metadata:AESGCM|CalypsoGallery:Metadata:AESGCM|" ‖ purpose)
        │                                                                         │
        ▼                                                                         ▼
  headerKey ──► AES-256-GCM(DEK + dosya başlığı)                    metadataKey ──► AES-256-GCM(FVSTR: kaydı)
                     │
                     ▼
        DEK ──► Tink AesGcmHkdfStreaming(DEK, AAD = fileId) ──► Şifreli Medya Gövdesi
```

---

## 2. KEK Rotasyonunun Neden Gerekli Görülmediği (Reddedilme Gerekçeleri)

### 2.1. Tek KEK Altında Anahtar Aşınması (Key Wear-Out / Nonce Reuse) Riski Yoktur
- Bir KEK rotasyonunun kriptografik açıdan en yaygın sebebi, aynı simetrik anahtar altında şifrelenen blok/mesaj sayısı arttıkça AES-GCM nonce çakışması veya doğum günü sınırı ($2^{32}$) riskinin büyümesidir.
- Calypso Gallery mimarisinde **`KEK` hiçbir zaman doğrudan AES-GCM şifrelemesinde kullanılmaz**:
  - Her `.bin` dosyası oluşturulurken rastgele 16 baytlık değişmez `fileId` ve 32 baytlık bağımsız bir **`DEK`** üretilir.
  - Başlık anahtarı `HKDF-SHA256(KEK[keyId], "CalypsoGallery:Header:AES256GCM|" || hex(fileId))` ile dosya başına ayrı türetilir; yani her dosya kendi benzersiz `headerKey` değerini alır.
  - Medya gövdesi ise Tink `AesGcmHkdfStreaming` içinde `DEK` ve 32 baytlık rastgele akış tuzu (`salt`) ile türetilen alt anahtarlarla şifrelenir.
- Bu nedenle tek bir `KEK` (`keyId = 1`) altında milyonlarca dosya şifrelense dahi anahtar/nonce aşınması oluşmaz.

### 2.2. Yerel Mobil Tehdit Modelinde `VMK` ve `KEK` Aynı Güvenlik Sınırındadır
- Çok kiracılı sunucu veya HSM (Hardware Security Module) sistemlerinde anahtar hiyerarşisi katmanları farklı fiziksel/mantıksal güvenlik sınırlarında yaşar ve bir alt katman anahtarının tek başına sızması mümkündür.
- Yerel bir mobil kasa uygulamasında ise kasa açıldığında `VMK` ve `KEK` **aynı uygulama sürecinin RAM alanında birlikte bulunur** (`unlockedMasterKey` ve `unlockedKek`).
- `VMK` veya cihaz ele geçirilmeden yalnızca `KEK`'in izole olarak sızdığı gerçekçi bir bireysel saldırı senaryosu yoktur.
- Parolanın veya kasa kilidinin sızdığından şüphelenildiğinde **parola değiştirmek** (veya ADR 0004 ile KDF maliyetini güncellemek), `.vault` başlığındaki `PDK -> VMK` sarmasını yeni `salt` ve `IV` ile yenileyerek erişim güvenliğini zaten yeniden tesis eder.

### 2.3. Geçmişte Kopyalanmış Veriye Karşı Koruma Sağlamaz
- KEK rotasyonu yalnızca `.bin` başlıklarındaki `DEK` sarmasını değiştirir; dosya gövdesini şifreleyen **`DEK` ve Tink ciphertext'i bit-bit aynı kalır**.
- Saldırgan daha önce `.vault` dosyasının ve şifreli `.bin` dosyalarının bir kopyasını aldıysa, cihazda yapılan KEK rotasyonu o eski kopyaları geçersiz kılmaz. Bu tür bir sızıntıyı gidermenin tek yolu tüm dosyaların düz metinden yepyeni `DEK`'lerle baştan şifrelenmesi (yeni bir kasa açılıp taşınması) ve eski kopyaların imha edilmesidir.

### 2.4. Bireysel Kullanımda Risk / Fayda Oranı Olumsuzdur (Veri Kaybı ve Senkronizasyon Kırılması)
Bireysel kullanıcılar için KEK rotasyonunun getirdiği mühendislik ve operasyonel riskler, sağladığı marjinal kurumsal uyumluluk faydasından çok daha büyüktür:
1. **Toplu Yeniden Yazma Sırasında Veri ve Küçük Resim (Thumbnail) Kaybı Riski:** Kasadaki tüm `.bin` dosyalarının başlıklarının yerinde (in-place) yeniden yazılması sırasında pil bitmesi, OS tarafından sürecin öldürülmesi (`SIGKILL`), depolama dolması veya OTG bağlantısının kopması yarım kalmış durumlar yaratır; ayrıca başlıktaki gömülü küçük resim (`thumbnail`) baytlarının yanlışlıkla düşürülmesi riski taşır.
2. **Metadata (`FVSTR:`) Kayıtlarının Okunamaz Hale Gelme Riski:** Mevcut `decryptStringFromVault` implementasyonu kayıttaki `keyId` alanını okuyup kullanmaz ve her zaman aktif KEK'e (`activeKek()`) başvurur. Rotasyondan sonra eski KEK ile yazılmış Room SQLite gizli sanal albüm adları, `JSON/` dışa aktarımları, geri yükleme manifestleri ve resume kayıtları çözülemez hale gelir.
3. **Harici Disk / Masaüstü CLI (`calypso_vault.py`) "Split-Brain" Riski:** CLI **yalnızca şifreleme (encrypt-only)** aracıdır; hibir `.bin` dosyasını açıp çözmez. Eski `.vault` ile PC tarafında şifrelenen yeni `.bin` dosyaları eski KEK ve sabit `ACTIVE_KEY_ID = 1` ile yazılır (eski KEK emekli edilirse senkronizasyon bozulur). Ayrı bir Android cihazda ise yeni `keyId`'li `.bin` dosyaları eski `.vault` başlığıyla **açılamaz**. Güncel `.vault` başlığını eski dışa aktarım klasörüne yazmak `checkVaultExportTarget` (`VaultExportHeaderCheck`) bayt eşitliği kontrolü nedeniyle `DIFFERENT_VAULT` hatasıyla engellenir; yeni bir klasöre dışa aktarım gerekir.

---

## 3. İleriye Dönük Format Uyumluluğu (`1.x`, güncel sürüm `1.2.0`)

KEK rotasyonu `1.x` boyunca uygulanmamış olsa da kanonik kapsayıcı biçimi (`FGVLTENC` v1) ileride geriye dönük uyumluluğu bozmadan eklenebilecek şekilde tasarlanmıştır:
- **`.bin` başlıkları (`FGVLTENC` v1):** 9. baytta 2 baytlık açık `keyId` (`u16`, şu an `1`) alanını zaten taşır.
- **`FVSTR:` metadata kayıtları:** 1 bayt sürümün hemen ardından 2 baytlık `keyId` (`u16`, şu an `1`) alanını zaten yazar.
- **`.vault` keyring yapısı:** Uzunluk ön-ekli (`keyringLen`) olarak saklanır.
  - `keyringLen == 34` → `1.0.0` tekil anahtar formatı: `activeKeyId(u16) | KEK(32)`.
  - `keyringLen > 34` → İlerideki çoklu anahtar formatı: `activeKeyId(u16) | keyCount(u16) | [keyId(u16) | KEK(32)] × keyCount`.

---

## 4. İleride Kurumsal İhtiyaçlar İçin Eklenirse Kullanılacak Mimari Yaklaşım

Bu özellik bireysel kullanımdan ziyade **kurumsal uyumluluk** (ör. PCI DSS 3.6/12.3, ISO 27001 A.8.24, NIST SP 800-57 anahtar ömrü politikaları veya personel/cihaz devri sonrası zorunlu anahtar yenileme) sebebiyle gündeme gelirse aşağıdaki mimari uygulanmalıdır:

### 4.1. Çoklu Keyring ve `kekFor(keyId)` Altyapısı (Android + Python CLI)
- `CryptoManager` ve `tools/calypso-cli/calypso_vault.py` tek bir `unlockedKek` yerine bellek içi anahtar halkası haritası (`unlockedKeyring: Map<Short, ByteArray>`) ve `activeKeyId: Short` tutmalıdır.
- `.vault` okuyucuları `keyringLen == 34` ise tekil anahtar olarak yüklemeli; `keyringLen > 34` ise `keyCount` kadar `(keyId, KEK)` çiftini okumalıdır.
- **Kritik Ön Koşul (`FVSTR:`):** `decryptStringFromVault` kayıttaki `keyId` değerini okuyarak doğrudan `kekFor(keyId)` ile anahtar türetmelidir; yeni yazılan her `FVSTR:` kaydı `activeKeyId` ve `activeKek()` kullanmalıdır.
- **Kritik Ön Koşul (`VaultContainer`):** Gerçek API `CryptoManager.readMetadata(file, includeThumbnail, cacheResult)` + `CryptoManagerStreams.readContainerMetadata(dis, kek, version, includeThumbnail)`dır; `VaultContainer.readMetadata` diye bir metod yoktur. Başlıktaki `keyId` alanına göre `kekFor(keyId)` seçilmeli; yalnızca yeni/güncellenmiş başlıklar `activeKeyId` ile yazılmalıdır. `renameEncryptedFile` bugün `meta.keyId`'yi eşleştirmeden `activeKek()` kullanır.
- `clearKeys(context)` çağrısında keyring haritasındaki **tüm** KEK dizileri `fill(0)` ile sıfırlanmalıdır.

### 4.2. Önce-Keyring (Keyring-First) ve ADR 0003 Dayanıklı İşlem Mimarisi
1. **Ön Koşul:** Kasa açık olmalı ve aktif bir dosya işlemi bulunmamalıdır; rotasyon boyunca diğer tüm kullanıcı/senkronizasyon dosya işlemleri kilitlenmelidir.
2. **Yeni KEK Üretimi ve Önce `.vault` Yazımı:**
   - `newId = max(mevcut keyId) + 1` ve rastgele 32 bayt `newKek` üretilir (`keyCount <= 256` sınırı kontrol edilir).
   - **Eski KEK'ler silinmeden**, yeni `(newId, newKek)` keyring'e eklenip `activeKeyId = newId` ile `.vault` başlığına **önce atomik olarak yazılır** (`.vault.tmp` + `fsync` + `rename`). Böylece işlem yarıda kesilse bile hem eski hem yeni `keyId`'li dosyalar okunabilir kalır.
3. **ADR 0003 Foreground Service + Durable Journal ile Başlık Dönüşümü:**
   - Rotasyon, ADR 0003 ve ADR 0002 §7 kapsamında kayıtlı bir **`ForegroundOperationRegistry.withOperation` + `FileOperationService`** olarak çalıştırılmalı (`PARTIAL_WAKE_LOCK` + bildirim + işlem bitiminde arka plandaysa otomatik `clearKeys`), kalıcı bir `.kek_rotation_journal` dosyası tutmalıdır.
   - Her `.bin` dosyası için:
     - Başlıktaki `keyId == newId` ise atlanır (kesinti sonrası tam idempotent devamlılık).
     - `CryptoManager.readMetadata(..., includeThumbnail = true)` ile mevcut DEK, `fileId`, metadata ve **gömülü küçük resim (`thumbnailBytes`) eksiksiz okunur**.
     - `VaultContainerWriter.rewriteContainer(...)` ile geçici dosya `.tmp` üzerinde yalnızca başlık `newId / newKek` altında yeniden şifrelenir (`DEK`, `fileId` ve Tink ciphertext baytları aynen korunur).
     - Geçici dosya üzerinde Tink bütünlük doğrulaması (`verifyFileIntegrity`) çalıştırıldıktan sonra atomik olarak asıl dosyanın üzerine taşınır ve `metadataCache` girdisi güncellenir/temizlenir.
4. **Opsiyonel Metadata (`FVSTR:`) Yeniden Şifrelemesi:** Room SQLite, `JSON/` ve kasa çöp kutusu `.restore_info` dosyalarındaki aktif `FVSTR:` kayıtları yeni `activeKeyId` ile yeniden şifrelenir (ancak eski yedeklerin açılabilmesi için eski KEK'ler keyring'de tutulmaya devam eder).
5. **Harici Kasa / CLI Eşitleme Bildirimi:** Rotasyon tamamlandığında kullanıcıya harici disk / PC CLI için dışa aktarılmış eski `.vault` dosyalarının güncellenmesi gerektiği açıkça bildirilir.

### 4.3. Eski Anahtarların Emekli Edilmesi (Kripto-İmha / Re-Encryption)
- Keyring sınırı (`256` anahtar) dolduğunda veya kurumsal politika gereği eski bir KEK'in tamamen devre dışı bırakılması (crypto-shredding) gerektiğinde, yalnızca başlık rotasyonu yetmez: ilgili dosyaların yeni `DEK` ile düz metinden yeniden şifrelenip eski KEK'lerin keyring'den çıkarılması ve eski şifreli yedeklerin güvenli silinmesi gerekir.

---

## 5. Zorunlu Doğrulama Kriterleri (İleride Uygulanırsa)

Kurumsal sürümde KEK rotasyonu açılmadan önce şu testlerin tamamı geçmelidir:
- Payload baytları rotasyon öncesi ve sonrası bit-bit aynı kalır; eski KEK ile yeni başlık açılamaz.
- `FVSTR:` kayıtları (albüm adları, `JSON/`, restore manifestleri, resume blob'ları) rotasyon öncesi ve sonrası tüm anahtar dönemlerinde okunabilir.
- Gömülü küçük resimler (`thumbnailBytes`) rotasyon sonrası %100 korunur.
- Kesinti simülasyonu: `N` dosya dönüştürüldükten sonra süreç öldürülür (`SIGKILL`), yeniden başlatmada kalan dosyalar kaldığı yerden tamamlanır ve hiçbir dosya okunamaz kalmaz.
- Eşzamanlı silme/taşıma/şifreleme istekleri rotasyon kilidi tarafından güvenli şekilde engellenir veya sıraya alınır.
- Android ↔ Python CLI (`calypso_vault.py`) çoklu keyring okuma/yazma uyumluluğu testlerle doğrulanır.

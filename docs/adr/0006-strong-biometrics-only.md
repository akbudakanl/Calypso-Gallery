# ADR 0006 — Yalnızca Class 3 (STRONG) biyometrik kasa erişimi

- **Durum:** Accepted — **Class 2 (zayıf) biyometrik desteklenmez; cihazdan bağımsız istisna tanımlanmayacaktır (2026-11-20)**
- **Tarih:** 2026-11-20
- **İlgili:** [ADR 0002](0002-operational-security-hardening.md) (operasyonel güvenlik sertleştirmesi), [ADR 0005](0005-kek-rotation.md) (anahtar hiyerarşisi ve yerel tehdit modeli)
- **İngilizce çeviri:** [0006-strong-biometrics-only.en.md](0006-strong-biometrics-only.en.md)

> **Özet Karar.** Şifreli kasanın biyometrik kilit özelliği yalnızca **Class 3 / `BIOMETRIC_STRONG`** biyometrileri kabul eder ve her doğrulama `BiometricPrompt.CryptoObject` ile donanım destekli bir Keystore anahtarına bağlanır. Class 2 / `BIOMETRIC_WEAK` (ör. Samsung Galaxy Tab S10 Lite'ın 2D yüz tanıması) **bilinçli olarak reddedilmiştir**; "sadece bu cihazlar için istisna" da dahil hiçbir yumuşatma uygulanmaz. Güçlü biyometrik olmayan cihazlarda ayar satırı gizlenmez; devre dışı olarak görünür, altında ve tıklanınca açılan pencerede nedeni açıkça anlatılır.

---

## 1. Sorun ve Bağlam

Kullanıcı gözlemleri iki farklı cihaz davranışı gösterdi:

- Parmak izi sensörlü bir telefonda kayıtlı biyometrik kaldırıldıktan sonra "kayıtlı biyometrik yok" uyarısı görüldü.
- Samsung Galaxy Tab S10 Lite'ta biyometrik ayar **hiç görünmedi**, uyarı da çıkmadı.

Sebep uygulama mantığındaki bir fark değil, donanımdı. Tab S10 Lite'da **parmak izi sensörü yok; yalnızca 2D (Class 2) yüz tanıma var**. Uygulamanın tek kapısı olan `canAuthenticate(BIOMETRIC_STRONG)` bu cihazda `BIOMETRIC_ERROR_NO_HARDWARE` / `BIOMETRIC_ERROR_NONE_ENROLLED` döndürüyordu. Eski davranışta satır `beVisibleIf(...)` ile `GONE` yapıldığı için ne tıklanabilir ne de uyarı üretebilir bir şey kalıyordu. Uyarı yalnızca satıra tıklanınca üretildiğinden sessiz bir gizleme oluyordu.

Bu ADR hem bu kullanıcı deneyimi hatasını hem de **arkasındaki güvenlik kararını** kayıt altına alır.

---

## 2. Karar

| Katman | Kural |
|---|---|
| Cihaz kapısı | `BiometricManager.canAuthenticate(BIOMETRIC_STRONG) == BIOMETRIC_SUCCESS` |
| Anahtar kapısı | `setUserAuthenticationRequired(true)`, `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)`, `setInvalidatedByBiometricEnrollment(true)`, donanım destekli (TEE/StrongBox) |
| Doğrulama | Kayıt ve kilit akışlarının tamamında `BiometricPrompt.CryptoObject(cipher)` |
| Cihaz PIN/şifresi | Yalnızca biyometriği **kapatırken** onay için (`DEVICE_CREDENTIAL`); kasa açılışında yedek olarak kullanılmaz |
| Weak biyometrik | Desteklenmez; sessiz veya açık fallback yok |
| UI | Satır kasa varsa görünür; uygun donanım/anahtar yoksa devre dışı + kalıcı açıklama + tıklamada dialog (toast değil) |

`CryptoManagerBiometrics.kt` ve `BiometricAvailability.kt` bu kuralların tek uygulama noktasıdır. `canAuthenticate` sonucu (`NO_HARDWARE`, `NONE_ENROLLED`, `HW_UNAVAILABLE`, `SECURITY_UPDATE_REQUIRED`, anahtar hatası) kullanıcıya özel bir mesaja dönüştürülür; 9 denetlenen dil + İngilizce/Türkçe yerelleştirilmiştir.

---

## 3. Gerekçe

### 3.1. Android'in platform sözleşmesi

AOSP biyometri sınıf tablosu açıktır:

| Sınıf | BiometricPrompt | Keystore (anahtar açma) | CryptoObject |
|---|---|---|---|
| Class 3 / STRONG | Evet | Evet | Evet |
| Class 2 / WEAK | Evet | **Hayır** | **Hayır** |
| DEVICE_CREDENTIAL | Evet | Evet (süreli) | Sınırlı |

Android Keystore'da `AUTH_BIOMETRIC_WEAK` diye bir anahtar yetkisi **yoktur**. AndroidX `BiometricPrompt.authenticate(info, crypto)` metodu, izin verilen doğrulayıcılar Class 2 içerdiğinde `IllegalArgumentException` fırlatır. Yani weak yüzle kriptografik bir anahtar açmak API düzeyinde mümkün değildir.

Platform bunu keyfi olarak yapmaz: Class 2'nin sahte geçme oranı yüksektir (AOSP: SAR %7–20, 2D yüz fotoğrafla kandırılabilir). Android, bu sonucun ödeme/şifreleme gibi kritik anahtarları açmasına izin vermez.

### 3.2. "TEE var" argümanı neden yeterli değil

Class 2 biyometrilerin şablonları da TEE/Secure Element içinde saklanır, eşleştirme de güvenli ortamda yapılır (Android CDD gereği). Ancak bu, **iki ayrı kapıyı** karıştırmak anlamına gelir:

1. **Biyometrik kapı (TEE):** Şablonu saklar, "bu kullanıcı" der.
2. **Anahtar kapısı (Keystore / StrongBox):** Kriptografik anahtarın kullanımına izin verir.

TEE, Class 2 için birinci kapıyı sağlar; ikincisini Android bilerek açmaz. İstisna eklemek, birinci kapının sonucunu ikinci kapıya bağlayan *uygulama kodu* yazmak anlamına gelir. güvenlik sınırı donanımdan yazılıma kayar.

### 3.3. CryptoObject'ın eklediği korumalar

- **Yetki donanımda kontrol edilir.** Uygulama `cipher.doFinal()` çağırdığında Keystore/StrongBox, o işlem için güçlü doğrulama olup olmadığını denetler; yoksa `UserNotAuthenticatedException` döner. Uygulama "doğrulandı" diyerek bu kontrolü taklit edemez.
- **İşlem başına yetki.** `setUserAuthenticationParameters(0, ...)` ile yetki tek `Cipher` örneğine bağlıdır; başka zamandan kalan bir doğrulama tekrar kullanılamaz.
- **Anahtar dışarı çıkmaz.** Anahtar malzemesi uygulama sürecine girmez, non-exportable'dır.
- **Kayıt değişimi anahtarı öldürür.** `setInvalidatedByBiometricEnrollment(true)` donanım tarafından uygulanır; cihaza yeni biyometrik eklettiren saldırgan kasa anahtarını kullanamaz.

### 3.4. Bağ kaldırılırsa oluşan riskler

| Senaryo | CryptoObject var | CryptoObject yok |
|---|---|---|
| Değiştirilmiş APK | Doğrulama çağrısı taklit edilse bile `Cipher` yetkisiz kalır, decrypt hata verir. | Prompt atlanır, anahtar doğrudan kullanılır. |
| Root + Frida / LSPosed hook | Anahtar bellekte yok; hook ile decrypt çağırmak yetmez. | "Doğrulandı" sahtesi üretilip kasa sessizce açılır. |
| Ele geçirilmiş uygulama süreci | Saldırgan gerçek bir güçlü doğrulama beklemek zorunda kalır (kullanıcı fark eder). | Anahtar/şifre süreç içinde erişilebilir; arka planda okunur. |
| Fotoğrafla yüz | Sistem Class 2 ile güçlü anahtarı açtırmaz. | 2D yüz kabul edildiğinden fotoğraf yeterli olur. |
| Yeni biyometrik kaydı | Donanım anahtarı geçersiz kılar, parola gerekir. | Anahtar yaşar; saldırgan kendi yüzünü kaydettirip açar. |

Root'lu cihazda kullanıcı kasayı **meşru olarak** açtıktan sonra içerik zaten süreç belleğinde görülebilir; bu iki tasarımda da aynıdır. CryptoObject'ın kazandırdığı ek güvence şudur: **kullanıcı gerçekten doğrulama yapmadan decryption tetiklenemez.**

### 3.5. Ürün bütünlüğü

Bu fork'un farkı güvenliktir: Argon2id hedef süresi, ekran görüntüsü yasağı, tuş takımı gizleme, PIN noktaları, RAM temizliği, kasa çıkışında kilit. Weak biyometrik istisnası bu bütünlüğü tek bir kararla zayıflatır ve kullanıcıda "biyometrik = güçlü" yanılgısı yaratır.

---

## 4. Kullanıcı Deneyimi Kararı

Güvenlik kararından bağımsız olarak eski davranış hatalıydı: ayar satırı sessizce kayboluyor, hiçbir açıklama verilmiyordu. Yeni davranış:

- Kasa varsa ve depolama izni varsa satır **her zaman görünür**;
- güçlü biyometrik veya donanım destekli anahtar yoksa anahtar devre dışı, altında yerelleştirilmiş bir açıklama durur;
- satıra tıklanınca aynı açıklama **`ConfirmationDialog`** içinde gösterilir (toast uzun metni kırptığı için kullanılmaz);
- zaten etkin olan bir kullanıcı donanım kaybolmuş olsa bile biyometriği **kapatabilir** (`confirmDisableBiometric` cihaz PIN/şifresine izin verir).

Mesajlar duruma göre değişir; özellikle "zayıf yüz tanıma kabul edilmiyor, parmak izi veya Sınıf 3 yüz tanıma kaydedin" ifadesi kullanıcıya donanım sınırını ve çözümü birlikte verir. Bu bir hata değil, **cihazın güvenlik özelliği sınırı** olarak sunulur.

---

## 5. Mod Seçimi Uygulamaya Ait Değildir

Class 3 3D yüz tanıma **ve** parmak izi gibi iki güçlü biyometrik aynı cihazda kayıtlıysa, hangisinin kullanılacağına uygulama karar vermez:

- Android, üçüncü taraf uygulamalara mod seçtirmez. `BiometricManager.Authenticators` yalnızca `BIOMETRIC_STRONG`, `BIOMETRIC_WEAK`, `DEVICE_CREDENTIAL` (API 36'da `IDENTITY_CHECK`) sunar; `FACE` / `FINGERPRINT` diye bir bit yoktur. Platformun `TYPE_FINGERPRINT`, `TYPE_FACE`, `TYPE_IRIS` sabitleri `@hide` olduğundan sistem uygulaması olmayanlar bunlara erişemez.
- Bu nedenle uygulama yalnızca `BIOMETRIC_STRONG` + `CryptoObject` isteğini iletir. Önceki bölümde değinilen "hangisini ekleyeceğimi sormadan parmak izi ekledi" davranışı da uygulamanın değil, **sistemin kayıt akışının** sonucudur.
- Sistem dialogu iki modu da geçerli kabul eder. Samsung One UI'ın kararlı sürümlerinde dialogun üstünde **fingerprint / face sekmeleri** çıkar, kullanıcı dokunarak geçer; ilk gösterilen mod OEM uygulamasına ve kullanıcının **Ayarlar → Biyometrikler** tercihine bağlıdır (pratikte çoğu cihazda parmak izi öndedir). AOSP'nin OEM yönergesi, çoklu biyometrik varsa kullanıcının sistem ayarlarından varsayılan belirleyebilmesini; aksi halde Class 3'ün varsayılan olmasını önerir.
- 18 Kasım 2026 itibarıyla **One UI 9.5 open beta** sekmeleri kaldırıp tek bir birleşik pencerede iki modu da aynı anda dinliyor; kullanıcı seçim yapmadan ilk başarılı mod kullanılır. Bu Samsung'a özel, henüz beta bir davranıştır.
- `CryptoObject` istendiği için yalnızca Class 3 modlar listeye girer. Yüz Class 2 (2D) ise kayıtlı olsa bile prompt'ta görünmez; örneğin Galaxy S8'de crypto istemi yalnızca parmak izini göstermiştir.
- Uygulama hangi modun kullanıldığını **bilemez**: `AuthenticationResult.getAuthenticationType()` (API 30) yalnızca biyometrik / cihaz PIN'i ayrımını verir, mod adını vermez. Bu nedenle arayüzde mod göstergesi yoktur; mevcut parmak izi ağırlıklı metin/ikonlar genel "biyometrik" anlamında okunmalıdır.

**Sonuç:** Class 3 yüz + Class 3 parmak izi olan bir cihazda her iki mod da kasayı açabilir; seçim kullanıcıya veya cihazın sistem ayarına aittir. Uygulama tarafında mod zorlamak ne mümkündür ne de güvenlik açısından arzu edilir. Bu nedenle kodda bir değişiklik yapılmaz.

> **Test notu:** Class 3 3D yüz tanıma olan bir cihaz mevcut olmadığı için bu senaryo gerçek cihazda test edilmemiştir; yukarıdaki davranış Android/AOSP ve OEM dokümantasyonuna dayanır (bkz. §7).

---

## 6. Reddedilen Alternatifler

1. **Weak biyometrik + uygulama seviyesi doğrulama:** Güvenlik sınırını TEE'den uygulamaya taşıdığı için reddedildi (§3.3–3.4).
2. **Sadece Tab S10 Lite / belirli cihazlar için istisna:** Donanım istisnası, güvenlik modelini cihaz kimliğine bağlar; yeni cihazlarla genişler ve aynı zayıflığı yayar. Reddedildi.
3. **Sessizce gizleme:** Kullanıcıyı bilgilendirmediği için reddedildi ve düzeltildi (§4).
4. **Kısa toast ile uyarı:** Uzun açıklama okunamadığı için reddedildi; kalıcı metin + dialog benimsendi.
5. **Genel kasa açılışında `DEVICE_CREDENTIAL` kullanımı:** Kullanıcıya ek kolaylık sağlamadığı (yine PIN girildiği) ve kapsam dışı olduğu için şimdilik reddedildi. İleride ayrı bir ADR olarak değerlendirilebilir; yine de Keystore tabanlı kalmalı, uygulama seviyesi kapı olmamalıdır.

---

## 7. Doğrulama ve Durum

- `VaultLockPolicyTest`: hata kodu → mesaj eşlemesi, satırın gizlenmediği ve açıklamanın dialog olarak gösterildiği kaynak düzeyinde doğrulanır.
- 9 denetlenen dilde (az, de, es, es-419, es-US, ja, nb-NO, nn, zh-TW) 7 yeni anahtarın 1:1 karşılaştırma tabloları `translation-blind-tests/` altında güncellendi.
- `:app:testFossDebugUnitTest` başarılı; kaynak birleştirme hatasız.
- **Test kapsamı sınırı:** Class 3 3D yüz tanıma donanımı olan bir cihaz mevcut olmadığından bu mod gerçek cihazda **test edilmedi**. §5'te anlatılan davranış Android/AOSP ve OEM dokümantasyonuna dayanır; uygun bir cihazda doğrulanması gerekir.

---

## 8. İleride Gözden Geçirme Koşulları

Bu karar yalnızca aşağıdaki durumlarda yeniden açılabilir:

- Android, Class 2 biyometrik için Keystore tabanlı bir yetki mekanizması sunarsa; veya
- ürünün tehdit modeli değişirse.

Her durumda değişiklik varsayılan kapalı, açık onaylı, görünür uyarılı ve yine donanım destekli bir anahtarla sınırlı olmalıdır. **Sessiz fallback hiçbir koşulda kabul edilmez.**

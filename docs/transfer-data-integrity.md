# Aktarım sırasında veri bütünlüğü — mevcut davranış, önerilen değişiklikler ve kalan riskler

Kapsam: kopyalama, taşıma, şifreleme ve şifre çözme işlemlerinin yarıda kalması
(harici disk çıkarılması, uygulamanın kapatılması, reboot, arka plana alınıp Android tarafından
sürecin öldürülmesi) sırasında verilerin ne olduğu. Referanslar: `DOCS.md` §2.4/§2.5,
[ADR 0001](adr/0001-tink-streaming-aead-migration.md),
[ADR 0002](adr/0002-operational-security-hardening.md),
[ADR 0003](adr/0003-resumable-file-operations.md) ve
[crypto-design-review.md](crypto-design-review.md).

> **Formal durum:** Bu belge bir kod değişikliği kaydı değil, **mevcut davranışın analizi ve
> önerilen çözümün tasarım notudur.** ADR 0003 hâlâ *Proposed* durumundadır ve §9'da uygulama
> "staged, not yet started" olarak geçer. Aşağıda "planlanan" veya "önerilen" olarak işaretlenen
> hiçbir değişiklik şu an kodda yoktur. ADR 0002 §7 (foreground operation registry) de aynı
> şekilde yalnızca tasarımdır; kodda yalnızca `OperationProgressNotifier` bulunur.
>
> **Bağımsız kriptografik denetim yapılmamıştır.** Konteyner yerleşimi proje özgüdür; iyi bilinen
> primitiflerin kullanılması tasarım hatası olmadığını kanıtlamaz.

---

## 1. Başlangıçta uygulamanın davranışı (mevcut kod)

### 1.1 Temel güvenlik invariant'ı

Mevcut kodda dört işlem ailesi de aynı deseni izler:

```
sağlam kaynaktan oku  →  YENİ bir hedef dosyaya yaz  →  (başarılıysa) kaynağı sil
```

Kaynak hiçbir zaman yerinde truncat edilmez, üzerine yazılmaz veya ikiye bölünmez. Kaynak silme
her zaman yazma başarısından sonraya ertelenir. Bu nedenle **"yarı kaynak + yarı hedef" durumu
kodda yoktur** ve süreç ölümünde kalıcı veri kaybı oluşmaz.

### 1.2 İşlem bazında davranış

| İşlem | Yazma yolu | Kopma anında hedef | Kopma anında kaynak |
|---|---|---|---|
| Düz kopya / stream move (SD, OTG, SAF veya copy) | `getFileOutputStreamSync(dest.path, …)` → doğrudan nihai yol (`CopyMoveHelper.kt:912-918`) | **Yarım dosya kalır**; `catch` rollback yapmaz, `finally` yalnızca stream kapatır (`:923-928`) | Sağlam; silme `transferSuccess` sonrası (`:930-945`) |
| Aynı FS içi move | `srcFile.renameTo(dstFile)` (`CopyMoveHelper.kt:729`) | Atomik, yarım dosya yok | Atomik |
| Şifreli `.bin` kopyalama/taşıma | Aynı FS'de `renameTo`, değilse doğrudan `destBin`'e stream (`CryptoManagerConflict.kt:503-525`) | Stream yolunda yarım `.bin` | Sağlam; `sourceBin.delete()` başarı sonrası (`:523-525`) |
| Encrypt | `VaultContainerWriter.writeContainer(target = randomHex.bin)` (`CryptoManagerFileCrypto.kt:246-259`, `VaultContainerWriter.kt:99-125`) | Yarım `.bin` (geçerli header + kesik payload) | Sağlam; `secureWipeAndDeleteFile` yazım sonrası (`:285-300`) |
| Decrypt / export | `FileOutputStream(outFile)` (`CryptoManagerFileCrypto.kt:548-562`) | Yarım düz dosya | Önce `verifyFileIntegrity` (`:473`); silme yazım sonrası (`:568-578`) |
| Albüm encrypt | Tüm dosyalar `wipeSource = false` ile şifrelenir, sonra kaynaklar silinir (`CryptoManagerFolderCrypto.kt:108-191`) | Kısmi çıktı + rastgele alt klasör | Dönüştürme fazında tüm kaynaklar sağlam |
| Albüm decrypt | Tüm dosyalar çözülür, sonra `.bin`'ler silinir (`CryptoManagerFolderCrypto.kt:399-465`) | Kısmi düz dosyalar | Dönüştürme fazında tüm `.bin`'ler sağlam |

İstisna (disk çıkarılması, yazma hatası) ile **süreç ölümü** arasındaki fark: istisnada `catch`
blokları kısmen temizlik yapar (`encryptedFile.delete()`, `outFile.delete()`, `destBin.delete()`);
süreç ölümünde hiçbir `catch`/`finally`/rollback çalışmaz ve yarım dosya kesinlikle kalır.

### 1.3 A — B — C senaryosu

B aktarımı sırasında kopma:

- **A:** Tamamlanmış ve commit edilmiş; kaynağı silinmiş olabilir ama hedefi tam. Etkilenmez.
- **C:** Hiç I/O başlamadı; dokunulmaz.
- **B:** Kaynağın yarısı hedefe gitmez — kaynak sağlam kalır. Ancak **hedefte yarım dosya oluşur**
  ve hiçbir mekanizma bunu temizlemez veya yeniden denemez. `KEEP_BOTH` ile yeniden denemede yarım
  dosya `(1)` sonekiyle kalıcı çöp olabilir.

Kayıp olan şey **B'ye harcanan ilerlemedir**; veri değil.

### 1.4 Bozulmanın tespiti

Yarım `.bin` sessizce kabul edilmez: header'daki `ciphertextLength` tam boyutu bildirir,
`verifyFileIntegrity` dosya uzunluğunu kontrol edip reddeder (`CryptoManagerStreams.kt:177`);
görüntüleme yolu bozuk segmenti sarı `!` rozetiyle gösterir (DOCS §2.4). Yani bozulma **tespit
edilir ama temizlenmez ve kullanıcıya resume seçeneği sunulmaz.**

### 1.5 Mevcut kodda gerçek (dar) açıklar

- Encrypt yolunda yaz sonrası **read-back doğrulaması yoktur**; `writeContainer` dönüp sync yaptıktan
  sonra kaynak secure-wipe edilir. Sessiz yazma bozulması halinde doğrulanmamış `.bin` üzerinden iyi
  kaynak silinebilir. Bu, süreç ölümü değil **sessiz bozulma** senaryosudur.
- Düz kopyada "doğrulama" yalnızca `source.size == bytesCopied` + dosya varlığıdır
  (`CopyMoveHelper.kt:920`); içerik hash'i karşılaştırılmaz.

---

## 2. Fork'ta zaten var olan değişiklikler

Bu proje (Fossify Gallery fork'u) veri bütünlüğü açısından aşağıdakileri **zaten içerir**:

- **Kanonik konteyner:** `FGVLTENC` v1; AES-256-GCM ile authenticate edilmiş header +
  dosyaya özel DEK ile Tink `AES256_GCM_HKDF` akışı (256 KiB segment), AAD = `fileId`
  (ADR 0001, DOCS §2.5, `TinkStreamSpec.kt`).
- **Tolerant görüntüleme / strict veri işlemleri** ayrımı: bozuk segment görüntülemede sıfırla
  değiştirilir, asla diske geri yazılmaz; dedup, conflict ve pre-wipe kararları doğrulanmış
  baytlar üzerinden alınır (DOCS §2.4.3-§2.4.5).
- **Kısmi atomik yazma desenleri:** `renameEncryptedFile` `.tmp_<rand>.bin` + `Files.move(REPLACE_EXISTING)`
  (`CryptoManagerFileCrypto.kt:640-683`);External sync `.sync_tmp_*.bin` + `verifyFileIntegrity` + move
  (`CryptoManagerExportSync.kt:276-330`); sanal albüm ve favorites manifest'leri `*.tmp` + `renameTo`
  (`VirtualAlbumSyncManager.kt:271-294`, `ReferenceBackupManager.kt:113`). Bu desenler **bazı**
  yollarda yarım dosya oluşumunu zaten engeller — ama ana kopyalama/şifreleme yollarında yoktur.
- **İlerleme bildirimi:** `OperationProgressNotifier` + `CryptoProgressDialog` (foreground *service*
  değil, yalnızca bildirim).
- **Tasarım kararları:** ADR 0002 (operasyonel güvenlik) ve ADR 0003 (resumable file operations)
  dokümante edilmiş, henüz uygulanmamıştır.

---

## 3. Önerilen değişiklikler — HENÜZ UYGULANMADI

Aşağıdakiler bu analizin çıktısıdır; kodda karşılığı yoktur ve ADR 0003 §9 checklist'i ile uyumludur.

1. **Staging + atomik commit (yarım dosyanın asıl çözümü).** Her öğe, nihai dosyayla **aynı
   dizinde** bir `.stage_<operationId>_<itemIndex>` dosyasına yazılır; `fd.sync()` + dizin fsync
   sonrası `Files.move(..., ATOMIC_MOVE)` ile nihai isme taşınır. Nihai isim yalnızca commit
   sonrası görünür olur. `ATOMIC_MOVE` desteklenmeyen FS/SAF'ta **non-atomic fallback yapılmaz**;
   öğe `FAILED` sayılır (bkz. §5, risk 1).
2. **`pending_operations` journal'ı.** ADR 0003 §3 şemasına ek olarak `stagingPath` ve batch
   (operationId, kök yollar, conflict kararları, albüm merge hedefi) kaydı. `PENDING/IN_FLIGHT`
   ilk bayttan **önce**, `VERIFIED` rename+sync'den **sonra**, `SOURCE_DELETED` kaynak silindikten
   sonra yazılır. Böylece ölü süreçten kalan staging dosyası tanım gereği orphan olur.
3. **Read-back doğrulaması.** Encrypt sonrası `verifyFileIntegrity`, düz kopyada tam
   boyut+fingerprint; `VERIFIED` yalnızca doğrulama sonrası. Kaynak silme yalnızca `VERIFIED` sonrası.
   Bozuk kaynak `.bin` kopyalanacaksa corruption kaydı kopyaya/Journal'a taşınır.
4. **Startup reconciliation.** Çalışan işlem yokken journal'da referansı olmayan staging dosyaları
   temizlenir. **Düz metin tutan staging dosyaları normal `delete()` değil secure wipe ile silinir**
   (ADR 0002 §7). `VERIFIED` öğeler disk karşılaştırması, `IN_FLIGHT` öğeler kaynaktan yeniden yazım.
   Sanal albüm manifest'i, MediaStore ve Room DB kayıtları de kapsanır.
5. **Foreground operation registry.** Wake lock, tek owner token, transient-wait ve iptal akışı
   (ADR 0002 §7 + ADR 0003 §7). Süreç ölümü sonrası şifreli işlemler için kasa yeniden açılır;
   journal asla anahtar veya düz metin tutmaz.
6. **Depolama kontrolünün uyarlanması** (bkz. §6).

---

## 4. Veri kaybı: pratikte sıfırlama hedefi

Süreç ölümü kaynaklı kalıcı veri kaybının **pratikte sıfırlanması hedeflenir**, çünkü tek iyi kopya
her zaman ya sağlam kaynakta ya da atomik commit edilmiş hedefte bulunur. "B dosyası birleştirilemez
parçalara ayrılıp kayboldu" senaryosu mevcut kodda da yoktur, önerilen kodda da olmaz.

Bu hedef şu varsayımlara dayanır:

- staging dosyası nihai hedefle **aynı dizinde** ve aynı FS'te tutulur,
- `ATOMIC_MOVE` başarısızlığında sessizce non-atomic yola düşülmez,
- read-back doğrulaması gerçekten uygulanır ve `VERIFIED` kaydı kaynak silmeden önce gelir,
- journal kaybı durumunda en kötü sonuç **duplicate/orphan dosya** ve isim/albüm kaymasıdır,
  veri kaybı değildir,
- kullanıcıya gösterilecek mesaj "verileriniz kayboldu" **değil**, ADR 0003'teki gibi
  "önceki işlem yarıda kaldı, veri kaybı yok, devam edilsin mi?" olur.

---

## 5. Kalan riskler (değişikliklerden sonra)

| # | Risk | Açıklama / önlem |
|---|---|---|
| 1 | **Atomik rename garantisi yok (kısmen giderildi)** | Copy/move ve SAF yolları artık staging + atomik commit kullanıyor: doğrudan `File` hedeflerde `Files.move(ATOMIC_MOVE)`, SAF hedeflerde kardeş `DocumentFile` oluşturup `DocumentFile.renameTo()` ile commit (`AtomicStaging.commitSaf`); başarısızlık `FAILED`, kopyala-sil fallback yok. **Kalan:** `CryptoManagerExportSync.kt:322-331` hâlâ `ATOMIC_MOVE` denemeden `REPLACE_EXISTING` + `renameTo` fallback kullanıyor; FAT/exFAT'ta `DocumentFile.renameTo()` tüm sağlayıcılarda garanti değil. |
| 2 | **Doğrulama kapsamı/maliyeti** | Şifreli kopyada hızlı örnekleme kullanılıyor (`Activity.kt:2417`); sync yolu tam `verifyFileIntegrity` yapıyor (`:279`). Sadece boyut+fingerprint "VERIFIED" dersen bozuk `.bin` doğrulanmış sayılabilir. Tam doğrulama büyük videolarda 2x okuma maliyeti getirir; bu maliyet kabul edilmelidir. |
| 3 | **Journal kaybı / reddi** | Journal app-private; app verisi silinirse veya expire olursa (ADR §10 açık soru) ya da kullanıcı resume'u reddederse: commit edilmiş ama kaynağı silinmemiş öğeler **duplicate** kalır; `resolvedDestName` gider → `(1)/(2)` kayar; albüm batch kaydı gider → yeni rastgele alt klasör (`CryptoManagerFolderCrypto.kt:79-84`). |
| 4 | **Dosya dışı store'lar atomic değil (kısmen giderildi)** | `MetadataReconciler` startup'ta commit edilmiş öğeler için `updateDBMediaPath` (MediaStore + Room medium/date_takens/favorites/virtual albums + manifest), sanal albüm `parentPhysicalPath` düzeltmesi ve düz dosyalar için MediaStore rescan yapar; her işlem app-private SharedPreferences ile bir kez işaretlenir. **Kalan:** klasör config ve thumbnail cache tutarlılığı, resume sırasında ikinci bir ölümde yeniden reconcile tetiklenmesi. |
| 5 | **Foreground service sınırları** | Force-stop `onDestroy` çağırmadan öldürür (journal `IN_FLIGHT` kalır — resume halleder). OEM battery optimizer / Doze servisi duraklatabilir, wake lock kaybolabilir, harici birim geri gelmeyebilir → işlem "waiting" takılabilir. Timeout/cancel politikası gerekir. |
| 6 | **Plaintext staging izi** | Decrypt ve düz kopya staging dosyaları düz metin tutar; `delete()` ile silinirse flash'te iz kalır. Secure wipe şart (ADR 0002 §7). Ciphertext staging için normal `delete()` yeterli. |
| 7 | **Tek seferlik alan kontrolü** | Alan kontrolü batch başında yapılır; uzun süren işlemde başka uygulamalar alan tüketebilir veya harici birim çıkarılabilir. Öğe başına yeniden kontrol gerekir. |
| 8 | **Eşzamanlılık** | Owner token olmadan iki işlem aynı hedefi seçebilir veya biri diğerinin kaynağını silebilir. Staging isimleri `operationId + itemIndex` ile benzersiz olmalı. |

Bu risklerin **hiçbiri** "kaynak ve hedefin yarısı kalıp birleştirilemez veri kaybı" şeklinde
değildir; hepsi "kirli durum, askıda işlem, duplicate, yanlış doğrulama veya gizlilik izi"
kategorisindedir.

---

## 6. Düşük depolama alanı ve "depolama yeterliliği" özelliği

### 6.1 Mevcut kontrol

Uygulamadaki depolama yeterliliği kontrolü **yalnızca** `CopyMoveHelper.prepareAndRunCopyMove`
içinde vardır (`CopyMoveHelper.kt:376-401`):

- Yalnızca `useStreamCopy == true` ise çalışır (SD/OTG/SAF veya copy). Aynı FS içi move'da
  `renameTo` kullanıldığı için kontrol atlanır — doğrudur, rename ek alan istemez.
- `availableSpace = destination.getAvailableStorageB()`; `sumToCopy = Σ item.size`.
- `sumToCopy >= availableSpace` ise işlem engellenir ve `no_space` toast'u gösterilir
  (eşitlik de engellenir — mevcut muhafazakâr davranış).
- `getAvailableStorageB()` `-1` döndürürse (SAF/OTG'de olabilir) kontrol **tamamen atlanır**.

**Kritik bulgu:** `getAvailableStorageB` için yapılan arama kodda yalnızca bu dosyada sonuç veriyor.
Yani **encrypt, decrypt, albüm işlemleri ve düz→şifreli kasa kopyalama hiçbir alan kontrolüne tabi
değildir.** "Depolama ayarı yeterliliği" özelliği kapsamı bakımından zaten eksiktir.

### 6.2 Staging gerçekten daha mı fazla yer kullanıyor?

**Hayır — doğru uygulandığında hayır.** Tek öğe için tepe kullanım karşılaştırması:

```
Doğrudan yazma : kaynak(dolu) + nihai_dosya(0→boyut) + önceki öğeler
Staging + rename: kaynak(dolu) + staging(0→boyut)    + önceki öğeler   → rename ücretsiz (metadata)
```

Aynı dizinde rename veri kopyalamadığı için staging, doğrudan yazmaya göre **ek bir tam kopya
oluşturmaz.** Önceki bir notta geçen "2x tepe kullanım" ifadesi yanlıştır; doğru olan şudur:
her iki yöntem de transfer sırasında *kaynak + bir çıktı dosyası* tutar, fark yalnızca çıktının
görünür olup olmamasıdır.

### 6.3 Yine de kalan alan riski

1. **Ciphertext genişlemesi.** Tink 256 KiB segment başına 16 baytlık GCM tag'i yazar
   (`TinkStreamSpec.kt:44,78-84`) ve konteyner header'ı DEK, ad, tarih ve **5 MiB'a kadar thumbnail**
   içerir. Payload genişleme katsayısı ≈ `262144/262128 ≈ 1.000061` (~1 MB başına 64 bayt) + header.
   Kaynak boyutlarının toplamı bu farkı **azımsar**; düşük alan ve `>=` eşiği birlikte "yeterli"
  yanıtı üretebilir ama yazma ortada ENOSPC alabilir. Veri kaybı yok (kaynak sağlam) ama işlem iptal.
2. **Staging farklı FS'e konursa gerçek 2x.** Eğer staging app-private cache'e, nihai dosya harici
   kasa dizinine yazılırsa "atomik" move kopyala-sil'e dönüşür ve o öğe için geçici olarak iki tam
   kopya oluşur. Bu yüzden staging **mutlaka nihai dizinde** olmalıdır (§3/1).
3. **Kapsam eksikliği.** Encrypt/decrypt yollarında kontrol hiç yok (§6.1). Önerilen journal şeması
   alan tahminini de tutmalı: `expectedCiphertextSize(originalSize) + header + thumbnail` toplamı.
4. **Bayat kontrol.** Batch başındaki tek kontrol, uzun işlem sırasında değişen boş alanı görmez
   (§5, risk 7).
5. **Orphan staging dosyaları.** Önceki ölü işlemlerden kalan staging dosyaları gerçek boş alanı
   azaltır; runtime kontrolü bunu doğru görür ama kullanıcı "neden yer kalmadı" şeklinde algılayabilir.
   Startup cleanup bunları temizlemelidir.
6. **Aşırı tahmin.** `copyMediaOnly` açıkken medya dışı dosyalar atlanır ama `sumToCopy` onları da
   sayar → gereksiz `no_space` uyarısı (mevcut davranış).

### 6.4 Uygulama planı (ADR 0003 §3a — kabul edildi)

Alan kontrolü, ADR 0003'ün bir parçası olarak **"Decision — space budgeting"** bölümünde
(§3a) ve §9 checklist maddesinde tanımlandı. Özet:

1. **Tek estimator:** `SpaceEstimator` + saf `VaultContainerWriter.estimateEncryptedSize(...)`;
   düz kopya, düz↔şifreli, `.bin` taşıma, dosya/albüm encrypt-decrypt, stream import ve sync/export
   aynı hesabı kullanır. Encrypt formülü: `143 + UTF8(ad) + thumbnail + expectedCiphertextSize(boyut)`
   (143 = 43 sabit prefix + 84 sabit header alanı + 16 GCM tag). Decrypt ve `.bin` kopyası kesin boyut
   kullanır.
2. **Albüm tepe kullanımı:** `encryptFolder`/`decryptFolder` tüm çıktıları yazıp ancak sonra
   kaynakları sildiği için `peakBytes = Σ(tüm çıktılar)` — tek dosya değil tüm batch.
3. **Zamanlama:** batch başında + her öğenin `IN_FLIGHT` geçişinden önce yeniden kontrol;
   startup orphan temizliği kontrolden **önce** çalışır.
4. **`-1` politikası:** sessiz atlama yok; `File` yolu için `StatFs` fallback, SAF/OTG için açık
   "alan doğrulanamadı" uyarısı ve iptal seçeneği.
5. **Eşik:** `required + max(64 MiB, %1) > available` ise engelle veya `WAITING_FOR_SPACE` durumuna
   geç (ADR §2 transient-wait katmanı, bildirim ve iptal akışıyla).
6. **Staging:** nihai dizinde → rename ücretsiz, 2x kopya yok; `ATOMIC_MOVE` desteklenmezse
   `FAILED`, kopyala-sil'e düşülmez.
7. **Journal/UI:** `estimatedRequiredBytes`/`peakBytes` batch kaydında tutulur; resume aynı
   sayılarla yeniden kontrol eder, kullanıcıya sadece `no_space` toast'u yerine gereken/mevcut
   bilgisi verilir.

Test kapsamı: tam eşik, N dosyalı albüm tepe kullanımı, maksimum thumbnail, SAF `-1`, orphan
birikimi, disk çıkarılıp takılması, `KEEP_BOTH`, yazma ortasında ENOSPC, force-stop sonrası resume.

---

## 7. Sorumluluk reddi (disclaimer)

- Bu belge **garanti değildir.** Yazılım "olduğu gibi" sağlanır; hiçbir veri kaybı, bozulma veya
  gizlilik ihlali olmayacağına dair açık veya zımni taahhüt verilmez.
- ADR 0003 hâlâ öneri aşamasındadır; §3'teki değişikliklerin uygulanması bu belgeyi güncel tutmak zorunluluğu doğurur. Kod ile belge çelişirse **kod geçerlidir.**
- Flash depolamada CSPRNG overwrite + `fsync` en iyi çabadır; FTL wear-leveling ve journal
  metadata nedeniyle uygulama düzeyinde "forensik olarak iz bırakmayan silme" mümkün değildir
  (DOCS §2, §2.4.7).
- Tink/AES-256-GCM/Argon2id bilinen primitiflerdir; ancak konteyner yerleşimi, AAD yapısı ve anahtar
  hiyerarşisi proje özgüdür ve **bağımsız harici denetimden geçmemiştir.**
- Yedekleme kullanıcının sorumluluğundadır. Bozuk bir şifreli segmentin orijinal bitleri
  kriptografik olarak geri getirilemez; yalnızca mevcut bir yedek geri yükleyebilir (DOCS §2.4.6).
- "Pratikte sıfır veri kaybı" ifadesi §4'teki varsayımların **tamamı** yerine getirildiğinde geçerli
  bir hedeftir; garanti edilmiş bir sonuç değildir.

---

## 8. Uygulama durumu (Faz 1) ve opsiyonel gerçek cihaz testleri

### 8.1 Faz 1'de kodlananlar

- Ayrı Room DB (`pending_operations.db`), `PendingOperation` entity'si ve DAO.
- `OperationJournal` API'si (PENDING → IN_FLIGHT → VERIFIED → SOURCE_DELETED; FAILED/SKIPPED).
- `AtomicStaging`: aynı dizinde `.stage_<operationId>_<index>.tmp` dosyası, `fd.sync()` + dizin
  fsync + `ATOMIC_MOVE`; desteklenmezse `FAILED` (kopyala-sil fallback'i yok).
- Dört çekirdek yazma yolu staging + doğrulama + journal ile güncellendi:
  `CopyMoveHelper.copyFile` (düz kopya/taşıma), `encryptFileToDirectory` (encrypt sonrası tam
  `verifyFileIntegrity`), `decryptFileToDirectory` (uzunluk doğrulaması),
  `copyOrMoveEncryptedFileToDirectory` (kopya sonrası integrity).
- Düz kopyada kaynak/hedef fingerprint karşılaştırması; plaintext staging hatalarda secure wipe.
- `SpaceEstimator` + saf `VaultContainerWriter.estimateEncryptedSize(...)`; düz kopya alan kontrolü
  yeni estimator'a bağlandı ve SAF `-1` için açıklayıcı uyarı eklendi (`space_unverified_warning`).
- Debug-only `OperationFaults` hook'u ve saf JUnit testleri (`ResumableOperationsTest`).

**Albüm/Activity batch hook'ları da bağlandı:**

- `encryptFolder` / `decryptFolder`: batch journal, `peakBytes = Σ çıktılar` alan kontrolü, öğe
  başına item context, fault injection, başarıda journal temizliği. Mevcut `.bin` dosyalarının
  taşınması da ayrı item olarak journal'lanır.
- `tryCopyMoveFilesTo` (Activity): şifreli kasa içi/dışı karma batch için tek journal, her öğeye
  item context, duplicate'ler `SKIPPED_CONFLICT`, karışık düz→düz kopyada staging + fingerprint
  doğrulaması, başarıda journal temizliği.

### 8.2 Faz 2 — süreç ölümü kurtarma

- `OperationRecovery.reconcile()`: startup'ta orphan `.stage_*` dosyalarını temizler (vault magic'i
  olmayanları secure wipe), `IN_FLIGHT/COPIED_UNVERIFIED/WAITING_FOR_SPACE` kayıtlarını `PENDING`'e
  sıfırlar; her öğe sağlam kaynağından yeniden yazılır.
- `OperationRecovery.resume()`: journal'daki batch bayraklarını (`isMove`, `wipeSource`,
  `deleteSource`, conflict payload) kullanarak dört işlem türünü öğe öğe yeniden çalıştırır.
- **Tek diyalog:** `MainActivity.onResume` içinde çağrılır; şifreli işlemler kasa açılana kadar
  bekletilir ("türüne göre tetikleme" kararı), düz işlemler uygulama açılışında gösterilir.
  Mesaj: "Veri kaybı yok; kaldığınız yerden devam edilsin mi?"
- `FileOperationService` + `ForegroundOperationRegistry`: tek owner token, kalıcı bildirim,
  PARTIAL_WAKE_LOCK; manifest'te `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`.
  Düz copy/move ve resume akışına bağlandı; aktif işlem varken orphan temizliği atlanır.

### 8.3 SAF atomik commit (Faz 3/1)

- `CopyMoveHelper.copyFile`: SAF hedefi için kardeş `DocumentFile` staging dosyası oluşturulur
  (`.stage_<op>_<index>.tmp`), tamamı yazılıp `DocumentFile.renameTo()` ile nihai isme taşınır.
  Rename başarısızsa öğe `FAILED`, staging silinir ve **kaynak korunur** — kopyala-sil fallback'i yok.
- Doğrulama: SAF'ta `File` yolu olmadığı için tam akış SHA-256 (`AtomicStaging.streamFingerprint`)
  kullanılır; direct-File yolunda `MediaIdentity` (ilk/son 64 KB) devam eder.
- `OperationRecovery.cleanupOrphanStaging`: journal'daki `content://` staging URI'larını da siler.
- Kalan SAF notları: `CryptoManagerExportSync` fallback'i hâlâ eski; bazı SAF sağlayıcıları rename'i
  desteklemez (o durumda işlem güvenli biçimde `FAILED` olur, yarım dosya oluşmaz).

### 8.3.1 Metadata reconciliation (Faz 3/2)

- `MetadataReconciler`: süreç ölümü sonrası startup'ta (`OperationRecovery.reconcile` içinden) commit
  edilmiş (`VERIFIED` / `SOURCE_DELETED`) öğelerin path referanslarını onarır.
- `MOVE/ENCRYPT/DECRYPT` için mevcut `Context.updateDBMediaPath` çağrılır: MediaStore, Room
  `media`/`date_takens`/`favorites`/`virtual_albums` ve ilgili JSON manifest'leri tek seferde güncellenir.
  `COPY` için kaynak korunduğundan yalnızca yeni hedefin kimliği kaydedilir.
- Sanal albümün kendi `parentPhysicalPath` değeri eski klasöre işaret ediyorsa düzeltilip manifest
  yeniden yazılır; düz dosyalar için MediaStore rescan yapılır.
- **Idempotency:** işlenen operationId'ler app-private SharedPreferences'da tutulur; UPDATE-by-old-path
  semantics'i sayesinde ikinci çağrı satır çoğaltmaz. Reconcile yalnızca commit edilmiş öğeleri kapsar;
  yarım kalan öğeler resume sırasında kendi yazma yollarında güncellenir.
- **Kalan:** klasör config / thumbnail cache tutarlılığı ve resume sırasında ikinci bir ölüm.

**Kalan (Faz 3):** albüm/Activity batch işlemlerinin registry'ye kaydı, `-1` alan durumunda kullanıcı
onayı (şu an açıklayıcı uyarı + devam), `CryptoManagerExportSync`'te `ATOMIC_MOVE` kullanımı.

### 8.4 Opsiyonel gerçek cihaz testleri (otomasyon yok)

Aşağıdakiler JUnit ile simüle edilemez; gerçek cihazda elle doğrulanmalıdır:

1. **Kopma simülasyonu — dosya kopyalama:** 20 büyük dosyayı SD karta kopyala; 5. dosya sırasında
   Ayarlar → Uygulamalar → **Force stop**. Yeniden aç: kaynak dosyaların sağlam olduğunu, hedefte
   yarım dosya bulunmadığını (yalnızca `.stage_*.tmp` orphan'ı olabileceğini) kontrol et.
2. **Harici disk çıkarma:** aktarım sırasında SD/USB'yi çıkar, tak ve işlemi yeniden dene; kaynağın
   silinmediğini ve yeniden denemenin tamamlandığını doğrula.
3. **Encrypt sırasında ölüm:** büyük bir videoyu şifrelerken force-stop; düz kaynağın açıldığını,
   kasada bozuk `.bin` yerine orphan staging kalmadığını doğrula.
4. **Decrypt sırasında ölüm:** aynı şekilde; `.bin` kaynağının sağlam kaldığını doğrula.
5. **Düşük alan:** hedefi neredeyse doldur; marjın işlemi engellediğini veya SAF'ta açıklayıcı
   uyarı gösterip devam ettiğini gözlemle.
6. **Albüm işlemleri:** çok dosyalı albümü şifrelerken öldür; tüm kaynakların sağlam kaldığını ve
   `peakBytes = Σ çıktılar` kuralının doğru hesaplandığını kontrol et.

> `OperationFaults` yalnızca JVM sistem özelliği okur (`cipher.failAfterItem`); Android `setprop`
> ile ayarlanamaz. Cihaz üstü senaryolar yukarıdaki elle adımlarla, enstrümante testler ise
> süreç içinde özelliği ayarlayarak koşulabilir.

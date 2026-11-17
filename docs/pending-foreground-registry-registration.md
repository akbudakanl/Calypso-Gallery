# Bekleyen iş: albüm/Activity işlemlerinin foreground registry'ye kaydı

- **Durum:** Yapılmadı — bilinçli olarak sonraki bir tura bırakıldı.
- **İlgili ADR'ler:** [ADR 0002 §7](adr/0002-operational-security-hardening.md) (foreground
  operation exception), [ADR 0003 §7](adr/0003-resumable-file-operations.md) (single owner token).

## Ne eksik?

`ForegroundOperationRegistry` şu an yalnızca iki yere bağlı:

- `CopyMoveHelper.executeCopyMoveWithProgress` (düz kopya/taşıma),
- `OperationRecovery.resume` (süreç ölümü sonrası devam).

Şu yollar **kayıtlı değil**:

- `CryptoManager.encryptFolder`,
- `CryptoManager.decryptFolder`,
- `BaseSimpleActivity.tryCopyMoveFilesTo` (şifreli kasa içi/dışı karma batch).

Bu yollar zaten staging + atomik commit + journal + doğrulama kullanıyor; yani **veri kaybı riski
yok**. Eksik olan yalnızca foreground servis / wake lock / owner token kapsamı.

## Pratik etki

Kayıt olmadığı için bu işlemler sırasında uygulama arka plana alınırsa veya ekran kapanırsa
ADR 0002 §7'deki "anahtarlar RAM'de kalır" istisnası uygulanmaz. Kasa kilitlenebilir, işlem yarım
kalabilir ve resume için kullanıcının kasayı yeniden açması gerekir. Yarım kalan öğeler journal'da
`PENDING`'e sıfırlanır ve sağlam kaynaktan yeniden yazılır — **bozuk dosya veya veri kaybı oluşmaz**,
sadece işlem kullanıcı müdahalesi gerektirir.

## Neden ertelendi?

Bu üç fonksiyon çok sayıda erken `return`, iç içe callback ve async katman içeriyor. registry'ye
almanın iki yolu var:

1. tüm gövdeyi `ForegroundOperationRegistry.withOperation(context, owner) { ... }` ile sarmak —
   dönüş tipleri ve callback'ler nedeniyle geniş bir kontrol akışı değişikliği gerekir;
2. her çıkış noktasına `release()` eklemek — kaçırılan bir çıkışta servis açık kalabilir.

İkisi de dikkatli bir regresyon turu istediği için "invaziv" olarak değerlendirildi.

## Yapılacaklar (önerilen sıra)

1. Her üç fonksiyon için tek bir `owner` adı belirle (örn. `album-encrypt`, `album-decrypt`,
   `crypto-copy-move`).
2. Ana çalışma bloğunu `withOperation` ile sar; erken `return` noktalarını `withOperation`'ın
   `null` dönüşüyle ele al.
3. İki işlem aynı anda başlatılırsa ikincisinin `null` dönüp kullanıcıya bilgi vermesini doğrula.
4. Testler: albüm şifreleme sırasında force-stop, arka plana alma, ikinci işlem denemesi,
   servis bildiriminin kapanması.

> Bu not, eksikliğin farkında olunduğunu ve veri güvenliği açısından kritik olmadığını kaydetmek
> içindir. Tamamlandığında dosya güncellenmeli veya kaldırılmalıdır.

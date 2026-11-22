# Crypto design review — Calypso Gallery 1.2.0

Short design review note. Refer to `DOCS.md` section 2 and ADR 0001 for the primitives and file
format used by the application. This document focuses on the **threat model**, **AAD rationale**
and **deliberate limitations**.

> **Formal status:** No independent external cryptographic audit has been performed on this format.
> Although well-known primitives such as Tink, AES-256-GCM and Argon2id are used, the container
> layout, AAD structure and key hierarchy are project-specific. Cross-language golden fixture
> tests catch implementation errors; they do not prove the absence of design flaws.

## 1. Assets and threat model

| Asset | Protection goal |
|---|---|
| Media payload (the Tink stream inside `*.bin`) | Confidentiality + integrity; segment-by-segment verification |
| File header (name, size, date, thumbnail, DEK) | Confidentiality + binding the header to its own file |
| `.vault` header (VMK, KEK keyring, Argon2 parameters) | Offline brute-force resistance + key integrity |
| Folder `.meta` (encrypted folder name) | Confidentiality |
| `FVSTR:` records (album name, JSON, `restore_info`, resume) | Confidentiality + per-purpose separation (decrypt requires the caller's `expectedPurpose`) |

**Adversary model:** an attacker who has access to the encrypted files on the device or on USB/SD
media, but cannot access the running application's memory or the Android Keystore. Offline brute
force, file substitution/swapping, truncation/extension and moving records between categories are
in scope. Memory, the operating-system kernel and legal coercion are outside this model.

## 2. Key hierarchy

```
password/PIN --Argon2id(salt, params)--> PDK --AES-GCM--> VMK        (.vault)
VMK --AES-GCM--> keyring { keyId -> KEK }                         (.vault)
KEK --HKDF(domain|fileId)--> headerKey --AES-GCM--> header (contains DEK)
KEK --HKDF(domain|domain|purpose)--> metadataKey --AES-GCM(AAD = domain|purpose)--> FVSTR: record
DEK --Tink AES256_GCM_HKDF(fileId AAD)--> payload
VMK --AES-GCM--> folder .meta
Keystore (optional, hardware) --AES-GCM--> VMK                     (biometric)
```

## 3. Payload encryption

- A random 16-byte `fileId` and a 32-byte DEK are generated for every file.
- The DEK is passed to the Tink `AesGcmHkdfStreaming` primitive; AAD = `fileId`.
- The Tink stream writes a 40-byte header (`u8 length=40 | 32 B salt | 7 B noncePrefix`), encrypts
  in 256 KiB segments by default (readers also accept 16/64 KiB and 1 MiB) and verifies each
  segment's GCM tag. The **exact-length check** is application-level (`verifyFileIntegrity()` in
  `CryptoManagerStreams.kt`), not Tink-level; normal viewing slices by `ciphertextLength` and
  silently ignores extra trailing bytes.
- The header stores the DEK and the file metadata; the payload can be copied **bit-identically**
  when the header is rewritten. Rename writes a new file and moves it atomically because a changed
  name can change the header length. KEK rotation is **not implemented** (ADR 0005 is `Rejected`).

## 4. Metadata encryption and AAD rationale

| Channel | AAD | Reason |
|---|---|---|
| Container header | `magic \| version \| keyId \| fileId \| N` | The header cannot be moved to another file, version or keyId. Including `N` (encrypted header length) prevents length confusion. The IV needs no AAD entry because it is the GCM nonce; modifying it already breaks the tag. |
| `FVSTR:` record | `CalypsoGallery:Metadata:AESGCM \| purpose` | GCM AAD is single-prefixed. The HKDF `info` is **double-prefixed** in the Android build: `CryptoManagerCredentials` builds `domain\|purpose` and passes it to `VaultKeyDerivation.deriveMetadataKey`, which prepends `domain` again. The Python helper mirrors this. `decryptStringFromVault()` requires the caller's `expectedPurpose` and rejects a blob from another category. |
| `.vault` header (VMK & keyring) | No AAD | Both the PDK→VMK and VMK→keyring layers use no AAD, and `protectionType` is cleartext. Wrong Argon2 parameters derive a wrong PDK and AEAD verification already fails. This gives the attacker no brute-force advantage. |
| Folder `.meta` | No AAD | For a normal album the effect of copying the file is limited to the displayed folder name. Sentinel values (`$$VAULT_RECYCLE_BIN$$`, `__VAULT_DEFAULT_ALBUM__`) also carry a behavioural role, so copying such a `.meta` can change a subfolder's role. Data confidentiality is unaffected. |

Deliberate limitation: rolling back to an older header of the same file is not detected; a portable
file has no reliable monotonic state or fixed header. This is an accepted and documented limitation
(ADR 0001).

## 5. KEK rotation

The KEK rotation design, risks and test plan are kept in
[ADR 0005](adr/0005-kek-rotation.md), which marks the proposal **`Rejected`** for this
individual/local-use threat model. No rotation is implemented: a single active KEK (`keyId = 1`)
and a single keyring are used. The header-rewrite primitive exists for rename and for KDF-only
re-wrapping, not for rotation.

## 6. Verification

- Golden fixtures are produced with the Python CLI and consumed by the Android application
  (`CliFixtureCompatibilityTest`).
- Cross-verified against the real Tink Java and Python libraries.
- Test coverage: segment-boundary sizes, payload bit-flips, truncation and prefix-field tampering
  (`fileId` / `keyId`) in `VaultContainerReadTest` / `VaultContainerWriterTest` and the Python CLI
  tests; decrypted-payload integrity across header-only rename via `VaultRenameTest` and
  `EncryptedRenameAndDefaultAlbumTest`. There is no extension (append-bytes) test, no cross-file
  header-transplant test and no raw-ciphertext byte-equality test. The rotation test plan in
  ADR 0005 is not implemented (ADR is `Rejected`) and CI does not run the Gradle/Python suites.

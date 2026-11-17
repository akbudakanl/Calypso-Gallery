# Crypto design review — Calypso Gallery 1.0.0

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
| `FVSTR:` records (album name, JSON, resume) | Confidentiality + per-purpose separation |

**Adversary model:** an attacker who has access to the encrypted files on the device or on USB/SD
media, but cannot access the running application's memory or the Android Keystore. Offline brute
force, file substitution/swapping, truncation/extension and moving records between categories are
in scope. Memory, the operating-system kernel and legal coercion are outside this model.

## 2. Key hierarchy

```
password/PIN --Argon2id(salt, params)--> PDK --AES-GCM--> VMK        (.vault)
VMK --AES-GCM--> keyring { keyId -> KEK }                         (.vault)
KEK --HKDF(domain|fileId)--> headerKey --AES-GCM--> header (contains DEK)
KEK --HKDF(domain|purpose)--> metadataKey --AES-GCM--> FVSTR: record
DEK --Tink AES256_GCM_HKDF(fileId AAD)--> payload
VMK --AES-GCM--> folder .meta
Keystore (optional, hardware) --AES-GCM--> VMK                     (biometric)
```

## 3. Payload encryption

- A random 16-byte `fileId` and a 32-byte DEK are generated for every file.
- The DEK is passed to the Tink `AesGcmHkdfStreaming` primitive; AAD = `fileId`.
- The Tink stream writes a 40-byte header (salt + noncePrefix), encrypts in 256 KiB segments,
  verifies each segment's GCM tag and enforces an exact-length check.
- The header stores the DEK and the file metadata; the payload can be copied **bit-identically**
  when the header is rewritten (used by rename and KEK rotation).

## 4. Metadata encryption and AAD rationale

| Channel | AAD | Reason |
|---|---|---|
| Container header | `magic \| version \| keyId \| fileId \| N` | The header cannot be moved to another file, version or keyId. Including `N` (encrypted header length) prevents length confusion. The IV needs no AAD entry because it is the GCM nonce; modifying it already breaks the tag. |
| `FVSTR:` record | `CalypsoGallery:Metadata:AESGCM \| purpose` | `purpose` is stored in the clear and bound as AAD; an album name cannot be read as a JSON document or as a resume record, and vice versa. |
| `.vault` keyring | No AAD | Salt/Argon2 parameters are not part of AAD; wrong parameters derive a wrong PDK and AEAD verification already fails. This gives the attacker no brute-force advantage. |
| Folder `.meta` | No AAD | If the file is copied to another folder it still decrypts; the effect is limited to the displayed folder name. Data confidentiality and integrity are unaffected. |

Deliberate limitation: rolling back to an older header of the same file is not detected; a portable
file has no reliable monotonic state or fixed header. This is an accepted and documented limitation
(ADR 0001).

## 5. KEK rotation

The KEK rotation design, risks and mandatory test plan are kept in
[ADR 0005](adr/0005-kek-rotation.md). It is not implemented in this release; a single active KEK
and a single keyring are used.

## 6. Verification

- Golden fixtures are produced with the Python CLI and consumed by the Android application
  (`CliFixtureCompatibilityTest`).
- Cross-verified against the real Tink Java and Python libraries.
- Test coverage: segment-boundary sizes, corrupt segment / extension / truncation, header
  substitution and bit-identical payload preservation. The rotation test plan is defined in
  ADR 0005.

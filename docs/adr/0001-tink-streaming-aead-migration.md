# ADR 0001 — Canonical vault container format

- **Status:** Implemented (2026-11-18)
- **Date:** 2026-11-16 (aligned with codebase 2026-11-18)
- **Scope:** the canonical vault container format, metadata records, the `.vault` key header, and the desktop CLI. The vault lives in `Calypso Gallery/Secrets` on internal storage or at a user-chosen external location.
- **Relates to:** DOCS.md §2, §2.2, §2.4; [ADR 0002](0002-operational-security-hardening.md) (operational security, wins on the foreground-operation exception); [ADR 0003](0003-resumable-file-operations.md) (resumable operations); [ADR 0004](0004-argon2-memory-and-cross-device-kdf.md) (KDF standard floor & portable memory ceiling — Implemented); [ADR 0005](0005-kek-rotation.md) (KEK rotation — Rejected)

> **Terminology.** The user-facing term is **"Encrypted Vault" / "Şifreli Kasa"**. The same term is used for internal identifiers (`Secrets`, `VAULT_HEADER_FILE`, `.vault`, `VaultContainer`). The canonical on-disk format is `FGVLTENC` **version 1**.

---

## 1. Decision summary

Every encrypted media file (`.bin`) is a self-contained `FGVLTENC` version 1 container. It combines an authenticated per-file header with a Tink streaming-AEAD payload, and derives every subkey from the vault KEK with domain separation. This gives:

- **Authenticated, seekable media:** Tink `AES256_GCM_HKDF` authenticates data in segments while preserving random access for the video player.
- **Per-file isolation:** a random DEK per file means one corrupted or exposed file never affects the others.
- **Context-bound metadata:** small encrypted strings use per-record keys and purpose-binding associated data, so records cannot be swapped between categories.
- **Byte-identical Android ↔ CLI interoperability:** both use the real Tink `AES256_GCM_HKDF` primitive over one frozen parameter set; the Python CLI builds an equivalent RAW-prefix keyset from the raw DEK.

Non-goals: changing Argon2id parameters (see ADR 0002 and the calibration methodology), changing the Keystore/StrongBox mechanism, UI, gestures, recycle-bin semantics, or `JSON/` virtual-album storage.

---

## 2. Key hierarchy and naming

Four distinct key layers, with unambiguous names:

```
password / PIN / pattern + Argon2id(salt, params) ──► PDK   (password-derived key)
Android Keystore (StrongBox / TEE) ─────────────────► VMK   (random vault master key)

.vault:  AES-256-GCM(PDK) wraps VMK                     ← changes on password/PIN or KDF change
         AES-256-GCM(VMK) wraps Keyring { keyId → KEK }  ← keyId is 1

per file: headerKey = HKDF-SHA256(KEK[keyId], "CalypsoGallery:Header:AES256GCM|" ‖ hex(fileId))
          AES-256-GCM(headerKey, headerAAD) encrypts Header { dek, fileId, … }
          dek ──► Tink AesGcmHkdfStreaming(dek, AAD = fileId) encrypts media

per record: metadataKey = HKDF-SHA256(KEK[keyId], "CalypsoGallery:Metadata:AESGCM|" ‖ purpose)
            AES-256-GCM(metadataKey, AAD = "CalypsoGallery:Metadata:AESGCM|" ‖ purpose) encrypts the record
```

**Naming rule:** `PDK` is always the Argon2id output; `VMK` is always the random vault master key. They are never the same variable.

This release has a single key epoch (`keyId = 1`). Changing the password re-wraps only the VMK in `.vault`; the keyring, file headers and the optional biometric bundle are unchanged.

**Why a DEK layer is needed:** it isolates files from each other. It is **not** needed for nonce separation — Tink derives a fresh stream key per file via `HKDF-SHA256(ikm=DEK, salt=random32, info=AAD)`, so nonce-prefix collisions across files are harmless, and `headerKey = HKDF(KEK, domain ‖ fileId)` gives each header its own key.

---

## 3. Canonical container layout

### 3.1 Wire layout

```
offset   size   content
0        8      magic "FGVLTENC"
8        1      version = 1
9        2      keyId (big-endian u16, cleartext — selects KEK)
11       16     fileId (random, cleartext, immutable for the life of the file)
27       12     header IV (AES-256-GCM)
39       4      encrypted header length N (big-endian u32)
43       N      encrypted header: GCM ciphertext + 16-byte GCM tag
43+N     T      Tink AES256_GCM_HKDF ciphertext
```

`payloadOffset() = 43 + N`. There is no separate content-IV field and no trailing whole-payload MAC: Tink carries its own stream header (`u8 len | salt(32) | noncePrefix(7)`) and per-segment tags inside `T`.

**Header GCM associated data** = `magic ‖ version ‖ keyId ‖ fileId ‖ N`, where `N` is the **encrypted** header length. The header IV is implicitly authenticated by GCM as the nonce. This binds the header to its file ID and format version: transplanting a header into another file, swapping `keyId`, or changing `version` fails at header decryption.

**Header plaintext packet** (before GCM encryption, `DataOutputStream` encoding):

```
u16    algorithmParamsId      // 0x0001 = AES256_GCM_HKDF / derived 32 / SHA-256
u32    segmentSizeBytes
u64    ciphertextLength       // exact T, for strict length assertion
32 B   dek                    // raw per-file data key, confidential inside the GCM header
UTF    originalName           // DataOutputStream.writeUTF
i64    lastModified
i64    dateTaken
i64    originalSize
i32    mediaType
i32    videoDuration
i32    thumbnailLength
...    thumbnail bytes
```

Rename and metadata edits rewrite only this header. Because `fileId` and `dek` are preserved, the payload stays valid and byte-for-byte unchanged.

### 3.2 Key derivation

```
headerKey(file)   = HKDF-SHA256(ikm=KEK[keyId], salt=∅,
                     info="CalypsoGallery:Header:AES256GCM|"  ‖ hex(fileId), L=32)
metadataKey(ctx)  = HKDF-SHA256(ikm=KEK[keyId], salt=∅,
                     info="CalypsoGallery:Metadata:AESGCM|"   ‖ purpose, L=32)
```

No custom KDF: this is RFC 5869 HKDF via Tink's `Hkdf.computeHkdf("HmacSha256", …)` (empty salt → hash-length zero salt per RFC §2.2, 32-byte output). `fileId` is cleartext (encoded as 32 lowercase hex characters in `info`), so the reader derives `headerKey` before decrypting the header. Never reuse a domain string for a new purpose.

### 3.3 Tink parameters (frozen shared spec — identical in Java and Python)

| Parameter | Value |
| :--- | :--- |
| Primitive | `AesGcmHkdfStreaming` (Streaming AEAD) |
| `KeyValue` | 32-byte DEK |
| `DerivedKeySize` | 32 |
| `HkdfHashType` | SHA-256 |
| `CiphertextSegmentSize` | **262 144 (256 KiB)** writer default (§8); readers accept 16 / 64 / 256 KiB and 1 MiB from the header |
| Nonce prefix | 7 bytes (Tink-generated) |
| Output prefix | RAW |
| **Associated data** | **`fileId` — the 16 raw bytes, not a hash of the header** |

The AAD is bound to an **immutable** value. Binding it to `SHA-256(encryptedHeader)` would make rename unreadable: re-encrypting the header changes the hash and therefore Tink's HKDF-derived stream key, forcing a full payload re-encryption on every rename. With a random DEK per file, cross-file header insertion already fails Tink validation; binding to `fileId` additionally ties the header to its file. It does **not** provide rollback to an older header of the same file — that requires an immutable header or trusted monotonic state, neither available in a portable vault file. Accepted, documented limitation.

### 3.4 Random access

Do not reimplement Tink's key derivation, IV construction or segment decoding. Use:

```
StreamingAead.newSeekableDecryptingChannel(SeekableByteChannel ciphertext, byte[] aad)
```

with `aad = fileId`. Wrap the `.bin` file in a thin `SeekableByteChannel` adapter (`VaultPayloadChannel`) that translates `position(p)` → file position `p + payloadOffset` and reports `size()` as the declared ciphertext length. The seekable channel caches the current decrypted segment, so the video player's many small reads do not re-decrypt a segment per read; this behaviour is covered by a regression check.

### 3.5 Integrity verification (strict paths)

`verifyFileIntegrity()` must:

1. Assert `file.length() == payloadOffset + ciphertextLength` (from the authenticated header), with `ciphertextLength` computed by Tink's `expectedCiphertextSize` formula.
2. Decrypt/authenticate every segment in order, including the last, and assert the final segment carried `lastFlag = 1`.
3. Fail closed if EOF is reached before the final segment — confusing EOF with a decryption error permits truncation.

The exact-length check also mitigates unauthenticated trailing bytes after a full final segment. Header swaps and `keyId`/`version` tampering are rejected by the header GCM AAD before Tink is invoked.

---

## 4. Metadata records (`.meta` and `FVSTR:`)

Two distinct authenticated formats are used depending on whether the record lives alongside CLI-encrypted folders or inside app metadata:

### 4.1 Folder `.meta` files (`FGMETA`)

Encrypted physical album directories (`Secrets/<subfolder>/.meta`) use a shared binary layout read and written identically by Android (`CryptoManagerAlbums.kt`) and the desktop CLI (`calypso_vault.py`):

```
magic "FGMETA" (6 B) | iv (12 B) | encLen (i32) | AES-256-GCM(VMK, iv, originalFolderName)
```

### 4.2 String metadata records (`FVSTR:`)

Hidden virtual album names in SQLite (`purpose = "album"`), `JSON/` virtual-album backups (`"album_json"`), recycle-bin `.restore_info` (`"restore_info"`), and encrypted session resume blobs (`"resume"`) use one self-describing string record format:

```
"FVSTR:" + base64( [ u8 version ][ u16 keyId ][ UTF purpose ][ 12B iv ][ i32 encLen ][ AES-GCM ciphertext+tag ] )
```

- `AES-256-GCM` (random 96-bit nonce) under `metadataKey = HKDF-SHA256(KEK[keyId], "CalypsoGallery:Metadata:AESGCM|" ‖ purpose)`, with the full domain context string `"CalypsoGallery:Metadata:AESGCM|" ‖ purpose` passed as GCM AAD.
- Because `purpose` is encoded via `DataOutputStream.writeUTF(purpose)` inside the record framing, `decryptStringFromVault` reconstructs the exact HKDF context and AAD directly while GCM AAD still prevents cross-purpose substitution.
- **No SIV and no Conscrypt dependency.** TLS-level AES-GCM is used directly; no per-ABI native crypto library is required.

`JSON/` virtual-album files remain CLI-isolated: the desktop CLI never reads or modifies them.

---

## 5. `.vault` header (PDK → VMK → keyring)

The header keeps the outer `writeVaultHeader` framing (magic `FGVLT`, Argon2id params, salt, IVs) and splits the wrapped material into two layers:

```
.vault:
  magic, kdf=Argon2id, iterations, memoryKb, parallelism, protectionType
  salt, ivPdk,  PdkWrappedVmK   = AES-256-GCM(PDK, ivPdk, VMK)
  ivVmk,  VmkWrappedKeyring     = AES-256-GCM(VMK, ivVmk, Keyring)
  Keyring = { activeKeyId(u16), KEK(32 B) }
```

- Password/PIN change re-wraps only `PdkWrappedVmK`; the keyring, headers and biometric blob are untouched.
- Device-bound Keystore/StrongBox wrapping of the VMK keeps daily use hardware-bound; the calibrated Argon2id parameters travel in `.vault` like KeePass rounds in `.kdbx`.

---

## 6. CLI interoperability contract

The CLI is **encrypt-only by design**. The frozen shared spec is mirrored in `helpers/vault/TinkStreamSpec.kt` and a `SPEC` block in `calypso_vault.py`:

```
ALGORITHM           = AES256_GCM_HKDF
DERIVED_KEY_SIZE    = 32
HKDF_HASH           = SHA256
SEGMENT_SIZE        = 262144 (256 KiB; §8)
OUTPUT_PREFIX       = RAW
TINK_AAD            = fileId                       (16 raw bytes)
HEADER_AAD          = magic | version | keyId | fileId | N
HEADER_KEY_DOMAIN   = "CalypsoGallery:Header:AES256GCM"
METADATA_KEY_DOMAIN = "CalypsoGallery:Metadata:AESGCM"
KDF                 = HKDF-SHA256 (RFC 5869), salt = empty (32 zero bytes), L = 32,
                      info = HEADER_KEY_DOMAIN + "|" + hex(fileId)  (or METADATA_KEY_DOMAIN + "|" + purpose)
```

Per file the CLI generates random `fileId(16)` and `dek(32)`, derives `headerKey` from `KEK[activeKeyId]` + `hex(fileId)`, writes the cleartext prefix and GCM header, then streams the payload through Tink with AAD = `fileId`. It writes `.bin` files and `FGMETA` `.meta` folder names into the vault whose `.vault` it finds; it never writes `FVSTR:` metadata records or touches `JSON/`.

Actions:

- Tink is pinned in both `gradle/libs.versions.toml` (`tink-android`) and the CLI's `requirements.txt` (`tink>=1.16.1,<2`, Python 3.10-3.13). The Python CLI builds a RAW-prefix keyset from the raw DEK, so both sides use the real Tink primitive. No Conscrypt.
- The CLI uses `cryptography` for the AES-256-GCM header and `argon2-cffi` for Argon2id.
- `make_fixtures.py` produces canonical fixtures (edge sizes and a real image) under `app/src/test/resources/fixtures/cli/`; `CliFixtureCompatibilityTest` asserts the app can read, authenticate and decrypt every one, and that tampered payloads/segments/headers are rejected.
- `VaultContainerReadTest` and `VaultContainerWriterTest` validate Android round-trips, tampering, and truncation checks.

---

## 7. Corruption and bit-rot behaviour (Fail-Closed AEAD Policy)

### 7.1 Per-segment authentication vs. full-file verification

Normal viewing paths (thumbnail, photo, video, dimensions) open the seekable Tink channel directly; Tink authenticates each `256 KiB` segment on read, so a corrupted or tampered segment **throws an AEAD authentication failure** rather than silently displaying unauthenticated bytes. Strict data operations (`decryptFileToDirectory`, `renameEncryptedFile`, `arePlainAndEncryptedIdentical` with `verifyIntegrity = true`, and `OperationRecovery`) run `verifyFileIntegrity()` across the entire payload including the final segment (`authenticated EOF`).

### 7.2 What a flipped bit does

| Bit location | Behaviour |
| :--- | :--- |
| **GCM header** (name, dates, thumbnail, DEK) | Header auth fails → `readMetadata()` returns `null` → file unavailable and flagged corrupt |
| **Payload ciphertext or segment tag** | That **segment's** GCM tag fails and reading throws; full-file `verifyFileIntegrity()` returns `false` |
| **Truncated / padded container** | `file.length() != payloadOffset + ciphertextLength` or early EOF → `verifyFileIntegrity()` and comparisons return `false` |

### 7.3 Adopted policy — strict fail-closed enforcement

The earlier draft concept of a zero-substituting (`0x00`) tolerant display reader, an `allowUnverified` plaintext export path, a `vault_corruption_reports` Room table, and yellow `!` overlays was **not adopted**. Instead, the codebase enforces a strict **fail-closed** policy so unverified plaintext is never silently exposed, exported, or laundered:

| Path | Implemented behaviour on authentication failure |
| :--- | :--- |
| **Viewing / streaming (`openPayloadChannel`)** | Reader throws on the bad segment (`GeneralSecurityException` / `IOException`); no `0x00` byte substitution is performed. |
| **Decrypt / export (`decryptFileToDirectory`)** | Runs `verifyFileIntegrity()` up front; on failure, marks the journal entry failed, writes **no** plaintext, and keeps the encrypted `.bin` source untouched. |
| **Rename (`renameEncryptedFile`)** | Runs `verifyFileIntegrity()` up front (`skipIntegrityCheck = false`); refuses to rewrite a container that fails integrity verification. |
| **Conflict / dedup (`SmartFileConflictDialog`, `CryptoManagerFileComparison`)** | `areEncryptedFilesIdenticalContent` and `arePlainAndEncryptedIdentical` reject length mismatches and failed AEAD reads (`StreamCompareMacResult.SOURCE_MAC_INVALID`), never auto-skipping or overwriting with an unverified copy. `SmartFileConflictDialog` runs `verifyFileIntegrity` inline and displays `smart_conflict_integrity_failed` (*"Integrity check failed (Bit-rot / Corrupted)"*). |
| **Copy / Delete / Secure wipe** | Deleting (or securely wiping) a `.bin` file is always allowed without an integrity gate. |

### 7.4 Why it is engineered this way

Bit-rot on phone eMMC/UFS is rare (ECC handles normal errors) and more plausible on cheap USB/SD media, after unsafe removal, interrupted writes or power loss — exactly the external-vault use case. Keeping the policy strictly fail-closed guarantees that **(1)** no authentication tag is ever recomputed over damaged bytes, **(2)** no source is deleted on the strength of an unverified copy, **(3)** no dedup/conflict decision treats damaged bytes as identical, and **(4)** damaged plaintext is never silently written to storage.

A plain **move** is trace-free when source and target share a filesystem (`rename(2)` changes only the directory entry; the app uses this for atomic temp-file replacement). Encryption/decryption are *transformations*, not moves: two different byte sequences must coexist until the new one is verified, preserving the only good copy if the process is interrupted. In-place overwrite is non-atomic and external vaults are often on a different filesystem where `rename` degrades to copy+delete anyway. "Forensic trace-free deletion" is not achievable at app level on flash (FTL wear-leveling, journal metadata); CSPRNG overwrite + `fsync` remains best effort.

---

## 8. Segment size — measurement and rationale

The segment size is a write-time parameter stored in each encrypted header; readers use the stored value. Four values are accepted: **16 / 64 / 256 KiB and 1 MiB**; the writer default is **256 KiB**.

Factors: sequential throughput (more segments = more GCM setup), seek latency (a seek decrypts the whole containing segment), tag overhead (16 bytes per segment — negligible), and corruption blast radius (smaller segment = smaller visible glitch, a side benefit, not the deciding factor).

**JVM relative benchmark** (`VaultSegmentBenchmark`, opt-in), 128 MiB synthetic file, median of 3:

| Segment     | Tag overhead | Sequential MiB/s | Seek mean ms | Seek p95 ms |
| :---------- | :----------- | :--------------- | :----------- | :---------- |
| 16 KiB      | 0.098 %      | 343.2            | 0.093        | 0.131       |
| 64 KiB      | 0.024 %      | 507.0            | 0.121        | 0.213       |
| **256 KiB** | 0.006 %      | **564.2**        | **0.388**    | **0.500**   |
| 1 MiB       | 0.0015 %     | 610.5            | 1.502        | 1.954       |

Decision rule: choose the smallest segment whose sequential throughput is within ~10 % of the best (610.5 MiB/s → 549.5 threshold), provided seek p95 stays below one frame budget (~16 ms). 16 and 64 KiB fall below; **256 KiB qualifies** with p95 0.5 ms.

**Device probe** (`MediaReadPatternProbe`, always on in debug builds): reads are small (~400 B–1.7 KB) but mostly contiguous, ~98 % below 64 KiB, with about one seek per open plus a few scrubs. Segment size therefore governs the latency spike at boundaries rather than average throughput. Tink's buffering coalesces Media3's tiny reads into fewer, larger reads. **1 MiB is explicitly rejected; 256 KiB is final.** Revisit only if a container shows a reproducible, segment-boundary-related regression.

---

## 9. Risks and mitigations

| Risk | Mitigation |
| :--- | :--- |
| Segment size invalid for a device | Measured (§8); keep current-segment caching, retune by measurement only |
| Truncation / trailing-byte acceptance | Read to final segment; strict length assertion from authenticated header |
| AAD / parameter mismatch Java ↔ Python | Frozen spec, pinned versions, golden fixtures in CI |
| Header transplant / version downgrade | Header GCM AAD binds `magic‖version‖keyId‖fileId‖N`; Tink rejects via DEK mismatch |
| Same-file header rollback | Accepted limitation of a mutable header in a portable vault |
| `fileId` collision | 16 random bytes; reject all-zero `fileId` defensively |
| Metadata context not reconstructible | `purpose` encoded via UTF in `FVSTR:` framing; `"CalypsoGallery:Metadata:AESGCM|" + purpose` used as HKDF info and GCM AAD |
| Corrupted media laundered by a write | Strict fail-closed paths refuse unverifiable bytes; no tag recomputed over damaged data |
| Tink version drift | Pin exact versions + golden ciphertext vectors in the repo |

---

## 10. Test coverage (verified)

1. **Container (`VaultContainerReadTest`, `VaultContainerWriterTest`):** round-trip all edge sizes; header parse; offset/length arithmetic; seek via Tink channel matches sequential; per-segment tamper, truncation, header transplant, `keyId`/`version` swap, wrong KEK, all-zero `fileId`.
2. **Rename (`VaultRenameTest`, `EncryptedRenameAndDefaultAlbumTest`):** payload bit-identical and decryptable after rename; header AAD rejects swapped fields; per-file `headerKey` selection; embedded thumbnail preservation.
3. **Metadata (`VaultMetadataTest`):** `FVSTR:` and `FGMETA` round-trips; cross-context ciphertext rejected.
4. **Keyring (`VaultHeaderFormatTest`, `CryptoManagerCredentials`):** password/KDF re-wrap preserves `VMK`/`KEK` and headers while rejecting invalid headers.
5. **Cross-implementation (`CliFixtureCompatibilityTest`):** all canonical Python CLI fixtures decrypt and verify on Android.
6. **Bit-rot / tampering (`VaultContainerReadTest`):** header flip → `readMetadata()` returns `null`; payload flip / truncation → `verifyFileIntegrity()` fails closed and rejects corrupt containers without deleting the `.bin` source.

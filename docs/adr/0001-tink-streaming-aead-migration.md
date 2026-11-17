# ADR 0001 — Canonical vault container format

- **Status:** Accepted
- **Date:** 2026-11-16
- **Scope:** the canonical vault container format, metadata records, the `.vault` key header, and the desktop CLI. The vault lives in `Calypso Gallery/Secrets` on internal storage or at a user-chosen external location.
- **Relates to:** DOCS.md §2, §2.2, §2.4; [ADR 0002](0002-operational-security-hardening.md) (operational security, wins on the foreground-operation exception); [ADR 0003](0003-resumable-file-operations.md) (resumable operations); [ADR 0005](0005-kek-rotation.md) (KEK rotation — deferred, not implemented)

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

.vault:  AES-256-GCM(PDK) wraps VMK                     ← changes only on password/PIN change
         AES-256-GCM(VMK) wraps Keyring { keyId → KEK }  ← keyId is 1

per file: headerKey = HKDF-SHA256(KEK[keyId], "CalypsoGallery:Header:AES256GCM" ‖ fileId)
          AES-256-GCM(headerKey, headerAAD) encrypts Header { dek, fileId, … }
          dek ──► Tink AesGcmHkdfStreaming(dek, AAD = fileId) encrypts media

per record: metadataKey = HKDF-SHA256(KEK[keyId], "CalypsoGallery:Metadata:AESGCM" ‖ context)
            AES-256-GCM(metadataKey, AAD = context) encrypts the record
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
                     info="CalypsoGallery:Header:AES256GCM"  ‖ fileId, L=32)
metadataKey(ctx)  = HKDF-SHA256(ikm=KEK[keyId], salt=∅,
                     info="CalypsoGallery:Metadata:AESGCM"   ‖ context, L=32)
```

No custom KDF: this is RFC 5869 HKDF via Tink's `Hkdf.computeHkdf("HmacSha256", …)` (empty salt → hash-length zero salt per RFC §2.2, 32-byte output). `fileId` is cleartext, so the reader derives `headerKey` before decrypting the header. Never reuse a domain string for a new purpose.

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

## 4. Metadata records (`FVSTR:`)

All small encrypted strings — `.meta` folder names, vault strings, `.restore_info` manifests, resume-state blobs, encrypted stealth-album JSON payloads — use one record format:

```
"FVSTR:" + base64( [ u8 version ][ u16 keyId ][ u8 contextType ][ discriminator? ][ AES-GCM ciphertext ] )
```

- `AES-256-GCM` (RAW prefix, random 96-bit nonce) under
  `metadataKey = HKDF-SHA256(KEK[keyId], "CalypsoGallery:Metadata:AESGCM" ‖ context)`.
- `context` is supplied by the caller from cleartext state (folder path, record purpose, album ID, `resume`, …) and is also passed as GCM AAD. Cleartext `contextType` + any discriminator are sufficient to reconstruct it.
- Per-record keying removes the RNG-failure motivation for AES-GCM-SIV; random 96-bit nonces under a per-record key are far below any practical limit.
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

The CLI is **encrypt-only by design**. The frozen shared spec is mirrored in `helpers/vault/TinkStreamSpec.kt` and a `SPEC` block in `cipher_vault.py`:

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
KDF                 = HKDF-SHA256 (RFC 5869), salt = empty, info = domain|id, L = 32
```

Per file the CLI generates random `fileId(16)` and `dek(32)`, derives `headerKey` from `KEK[activeKeyId]` + `fileId`, writes the cleartext prefix and GCM header, then streams the payload through Tink with AAD = `fileId`. It writes `.bin` files and `.meta` folder names into the vault whose `.vault` it finds; it never writes metadata records or touches `JSON/`.

Actions:

- Pin Tink in both `gradle/libs.versions.toml` (`tink-android`) and the CLI's `requirements.txt` (`tink>=1.16.1,<2`, Python 3.10-3.13). The Python CLI builds a RAW-prefix keyset from the raw DEK, so both sides use the real Tink primitive. No Conscrypt.
- The CLI uses `cryptography` for the AES-256-GCM header and `argon2-cffi` for Argon2id.
- `make_fixtures.py` produces canonical fixtures (edge sizes and a real image) under `app/src/test/resources/fixtures/cli/`; `CliFixtureCompatibilityTest` asserts the app can read, authenticate and decrypt every one, and that tampered payloads/segments/headers are rejected.
- A test-only independent Python decrypt helper validates Android-produced containers in CI despite the encrypt-only production CLI.

---

## 7. Corruption and bit-rot behaviour

### 7.1 "Decryptable" is not "authentic"

Normal viewing paths (thumbnail, photo, video, dimensions) open the seekable channel **without full-file verification**; `verifyFileIntegrity()` runs only on strict data operations (§7.3). A file can therefore produce bytes while differing from what was originally written. Tink authenticates per segment, so a corrupt segment is **detected**, not silently displayed.

### 7.2 What a flipped bit does

| Bit location | Behaviour |
| :--- | :--- |
| **GCM header** (name, dates, thumbnail) | Header auth fails → `readMetadata()` returns null → file effectively unavailable |
| **Payload ciphertext** | That **segment's** GCM tag fails. Reading it throws; other segments still decrypt. Playback runs to that point then errors; full-file verification rejects the whole file. |
| **Segment tag only** | Segment plaintext may decrypt but its tag fails → segment rejected |

Bit-rot isolation is therefore **per-segment**, not per-bit. The reader throws on a bad segment and does not skip it on its own; tolerant substitution is explicit application logic.

### 7.3 Adopted policy — tolerant display, strict data operations

| Path | Behaviour on authentication failure |
| :--- | :--- |
| Thumbnail / photo / video / GIF / SVG / dimensions | Wrap the read: substitute **zeros for that segment only**, reposition to the next segment boundary, and continue. Never write substituted bytes back. Emit a corruption report (§7.4). |
| Conflict comparison, dedup, pre-wipe verification | **Strict** — reject/skip. Unverifiable bytes are never treated as valid data or re-encrypted into a valid-looking file. |
| "Dosyanın şifresini çöz" (explicit user action) | Allowed as an *unverified best-effort* output (§7.5). |

Three invariants make the relaxed operations safe: **(1)** no authentication tag is ever recomputed over damaged bytes; **(2)** no source is deleted on the strength of an unverified copy; **(3)** no dedup/conflict decision is made from unverified bytes. Substituting zeros is safe for display: the decoder gets a known placeholder rather than attacker-chosen plaintext, and no write path consumes the decoded stream.

### 7.4 How the user learns about it

- Detection occurs in the read layer on background threads and can fire on every read, seek or thumbnail regeneration; it must **never** open a dialog there.
- A record is written to the app-private Room table `vault_corruption_reports`, keyed by (vault-relative path + file fingerprint), holding `firstDetectedAt`, `lastDetectedAt`, `badSegmentCount`, `notified`, `derivedFromCorrupt`. No cleartext sidecar file is placed in the Encrypted Vault.
- **Grid / album list:** a small **yellow `!` overlay** on the thumbnail — the primary indicator.
- **Fullscreen viewer:** a small yellow `!` badge; tapping it opens the corruption dialog.
- **First detection only:** one non-blocking toast/snackbar. Later detections only refresh `lastDetectedAt`.
- Records survive restarts and are cleared when the `.bin` is deleted, securely wiped, or replaced by a verified re-encryption (fingerprint change); renames follow the existing path-update plumbing. A plaintext output written by "Dosyanın şifresini çöz" is recorded with `derivedFromCorrupt = true`.
- **No quarantine and no automatic hiding** of damaged files.

### 7.5 Strict operations and the corruption dialog

| Operation | Behaviour on a corrupted file |
| :--- | :--- |
| Normal export (`decryptFileToDirectory`) | Shows the dialog; if cancelled, returns `null`. No plaintext is written and the `.bin` is untouched. |
| Copy | **Allowed.** Payload is copied verbatim, no tag is recomputed; the corruption record and badge follow the copy. |
| **Move** | **Action hidden entirely** for corrupted files (encrypted and decrypted states). It is functionally identical to copy, so no Move entry and no explanatory notice. |
| Conflict / dedup comparison | `SOURCE_MAC_INVALID`: never treated as bit-identical, so it cannot be auto-deduplicated or merged as an identical copy. |
| Pre-wipe verification | Returns `false`; the plaintext is not accepted as a verified duplicate. Deleting a `.bin` itself is always allowed. |
| Rename | **Allowed.** The header-only rewrite copies the payload verbatim (bound to the immutable `fileId`), so renaming cannot launder the damage. The badge persists. |
| Deleting the `.bin` itself | **Always allowed**, including secure wipe. No integrity check gates deletion. |

The dialog text (identical at both entry points):

> "Dosyanın bazı kısımları doğrulanamadı. Dosyanızda veri kaybı oluşmuş olabilir. Dosyanızın başka bir yedeği varsa geri yükleyebilirsiniz ya da orijinal dosya internette yer alıyorsa mevcut dosyanızın şifresini çözüp web aratması yapabilirsiniz."

| Entry point | Left button | Right button |
| :--- | :--- | :--- |
| Yellow `!` badge / file menu | "Dosyanın şifresini çöz" | **Tamam** (default) |
| Normal export icon | **İptal** | "Dosyanın şifresini çöz" (primary) |

"Dosyanın şifresini çöz" reuses the decrypt function with `allowUnverified = true`, streams through the tolerant reader, **keeps the encrypted source** (`deleteEncryptedSource = false`), and its output is never fed back into dedup, conflict checks, pre-wipe verification or re-encryption. There is **no "recovered / kurtarılmış" wording** — nothing was recovered, the output is still visibly damaged; it is only a best-effort chance to reverse-search the intact portion online.

**Repair is cryptographically impossible:** the original bits of a damaged segment are information-theoretically gone. Only a backup, if one exists, can restore them; never assume the user has one.

### 7.6 Why it is engineered this way

Bit-rot on phone eMMC/UFS is rare (ECC handles normal errors) and more plausible on cheap USB/SD media, after unsafe removal, interrupted writes or power loss — exactly the external-vault use case. Corruption is treated as *exceptional*: detect it loudly, keep media playable, and never silently propagate or launder damaged bytes.

A plain **move** is trace-free when source and target share a filesystem (`rename(2)` changes only the directory entry; the app uses this for atomic temp-file replacement). Encryption/decryption are *transformations*, not moves: two different byte sequences must coexist until the new one is verified, preserving the only good copy if the process is interrupted. In-place overwrite is non-atomic and external vaults are often on a different filesystem where `rename` degrades to copy+delete anyway. "Forensic trace-free deletion" is not achievable at app level on flash (FTL wear-leveling, journal metadata); CSPRNG overwrite + `fsync` remains best effort.

---

## 8. Segment size — measurement and rationale

The segment size is a write-time parameter stored in each encrypted header; readers use the stored value. Four values are accepted: **16 / 64 / 256 KiB and 1 MiB**; the writer default is **256 KiB**.

Factors: sequential throughput (more segments = more GCM setup), seek latency (a seek decrypts the whole containing segment), tag overhead (16 bytes per segment — negligible), and corruption blast radius (smaller segment = smaller visible glitch, a side benefit, not the deciding factor).

**JVM relative benchmark** (`V4SegmentBenchmark`, opt-in), 128 MiB synthetic file, median of 3:

| Segment | Tag overhead | Sequential MiB/s | Seek mean ms | Seek p95 ms |
| :--- | :--- | :--- | :--- | :--- |
| 16 KiB | 0.098 % | 343.2 | 0.093 | 0.131 |
| 64 KiB | 0.024 % | 507.0 | 0.121 | 0.213 |
| **256 KiB** | 0.006 % | **564.2** | **0.388** | **0.500** |
| 1 MiB | 0.0015 % | 610.5 | 1.502 | 1.954 |

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
| Metadata context not reconstructible | Cleartext `contextType` + discriminator; shared reconstruction helper |
| Corrupted media laundered by a write | Strict paths refuse unverifiable bytes; no tag recomputed over damaged data |
| Tink version drift | Pin exact versions + golden ciphertext vectors in the repo |

---

## 10. Test plan (exit criteria)

1. **Container:** round-trip all edge sizes; header parse; offset/length arithmetic; seek via Tink channel matches sequential; per-segment tamper, truncation, header transplant, `keyId`/`version` swap, wrong KEK, all-zero `fileId`.
2. **Rename (critical):** payload bit-identical and decryptable after rename; header AAD rejects swapped fields; per-file `headerKey` selection.
3. **Metadata:** `FVSTR:` round-trip; cross-context ciphertext rejected.
4. **Keyring:** password change leaves keyring/headers/biometric blob bit-identical.
5. **Cross-implementation:** all canonical fixtures decrypt on Android; Android output validated by the independent Python helper.
6. **Single-bit corruption:** header flip → metadata unavailable; payload flip → only that segment throws, neighbours still decrypt, full verification rejects; bad segment yields substituted zeros and continued playback, not a crash. Yellow `!` overlay appears in grid and viewer; toast fires exactly once per file and persists across restarts; record cleared on delete/rename/restore. Normal export shows the dialog then returns `null` if cancelled; comparison yields `SOURCE_MAC_INVALID`; pre-wipe leaves the plaintext source untouched. Copy is allowed and carries the badge; Move is hidden entirely for corrupted files; rename is allowed and keeps the badge; deleting the `.bin` is never blocked. Both dialog variants share identical text; the decrypt action writes zeros-substituted output, keeps the `.bin`, and is never reused by strict paths.

# ADR 0001 — Migrate the vault payload to Tink AES-GCM-HKDF, envelope keys, and rotatable metadata keys

- **Status:** **Accepted** (rev 6 — final; bit-rot/corruption UX fully specified)
- **Date:** 2026-11-16
- **Target release:** 1.20
- **Scope:** vault container format (`Secrets/`, formerly `Cipher_Secrets/` — the rename is path-only), metadata record format, `.vault` key header, desktop CLI, encrypted-area upgrade UX
- **Supersedes / relates to:** DOCS.md §2, §2.2, §2.3, §5; FORK_CHANGELOG.md `[Unreleased]`; [ADR 0002](0002-operational-security-hardening.md) (operational security; wins on the foreground-service exception)

> **Terminology.** The app's canonical user-facing term remains **"Encrypted Vault" / "Şifreli Kasa"**. All new UI strings, warnings and user docs must use it, as must this ADR's prose. "Vault" is also the established name for internal identifiers (`Secrets`, `VAULT_HEADER_FILE`, `.vault`, `VaultContainer`). The on-disk folder was renamed from `Cipher_Secrets` to `Secrets` after this ADR was accepted; that change is path-only and the legacy name is still recognised for detection and migration. The temporary "encrypted area / şifreli alan" wording from earlier revisions is fully reverted.

---

## 1. Context

The current vault format (DOCS.md §2.2) is:

```
magic(8) | version(1) | headerIv(12) | headerLen(4) |
encryptedHeader AES-256-GCM(N) | contentIv(16) |
payload AES-256-CTR(M) | Poly1305-AES tag(16)
```

`VaultContainer.kt` / `VaultKeyDerivation.kt` / `VaultContainerWriter.kt` are the single source of truth, and all three write paths plus the Python CLI funnel through the same format. Existing versions:

| Version | Magic | Payload | Integrity |
|---|---|---|---|
| 1 `VERSION_SYMMETRIC` | `FVLTENC1` / `FGVLTENC` | AES-256-CTR | header GCM only |
| 3 `VERSION_SYMMETRIC_AUTH` (current) | `FGVLTENC` | AES-256-CTR | Poly1305-AES over payload |
| 5 `VERSION_V5_AUTH` (legacy, read-only) | `FGVLTENC` | AES-256-CTR | Poly1305-AES over header + payload |

The current construction is cryptographically sound (it matches restic and has published test vectors), but it has four structural weaknesses:

1. **No AAD binding of the header to the payload in v3** — a whole header from another file encrypted under the same key can be transplanted.
2. **One key is used for everything** — header, payload encryption, payload MAC, metadata strings, `.meta` folder names, restore manifests, resume blobs. No rotation without re-encrypting the whole vault.
3. **No key-rotation or key-ID concept** anywhere in the format; the `.vault` header only wraps one key.
4. **Metadata uses raw AES-256-GCM with no AAD**, so records can be swapped between contexts.

Goals:

- Replace the payload scheme with an audited, cross-language streaming AEAD: **Tink `AES256_GCM_HKDF`**.
- Add **per-file random DEKs** wrapped inside per-file encrypted headers under a rotatable KEK, so rotation never re-encrypts media bytes.
- Protect metadata with keyed AEAD, per-record keys, domain separation and context-binding AAD.
- Keep byte-identical Android ↔ CLI interoperability and keep all existing vaults **fully readable and writable**.
- Make the switch a **user-approved, vault-wide rebuild**, never an automatic side effect of unlocking.

Non-goals: changing Argon2id parameters; changing the Keystore/StrongBox *mechanism*, UI, gestures, recycle bin semantics, media-type detection, or `JSON/` virtual-album storage. Detailed backward-compatibility UX for users of older app versions is folded into §7 of this ADR (it is no longer deferred).

---

## 2. Decision — key hierarchy and naming

Four distinct key layers, with unambiguous names:

```
password / PIN / pattern + Argon2id(salt, params) ──► PDK   (password-derived key)
Android Keystore (StrongBox / TEE) ─────────────────► VMK   (vault master key
                                                             = the existing random MEK, unchanged)

.vault:  AES-256-GCM(PDK) wraps VMK                     ← changes only on password/PIN change
         AES-256-GCM(VMK) wraps Keyring { keyId → KEK }  ← changes only on KEK rotation

per file: headerKey = HMAC-SHA256(KEK[keyId], "…Header:v4" ‖ fileId)
          AES-256-GCM(headerKey, headerAAD) encrypts Header { dek, fileId, … }
          dek ──► Tink AesGcmHkdfStreaming(dek, AAD = fileId) encrypts media

per record: metadataKey = HMAC-SHA256(KEK[keyId], "…Metadata:v4" ‖ context)
            Tink Aead / AES-256-GCM(metadataKey, AAD = context) encrypts record
```

**Naming rule:** `PDK` is always the Argon2id output; `VMK` is always the random vault master key (the legacy MEK). They are never the same variable.

**Backward-compatibility anchor:** `VMK` *is* the legacy MEK, so existing Keystore/biometric blobs remain valid with no re-enrollment and no re-wrapping. `KEK#1` is a **new random key**, not the old MEK. The old MEK/VMK is retained solely for reading v3 containers.

> **Vault-level format (adopted — option A).** The container *and metadata* format is a property of the **vault**, determined by its `.vault` file, not of individual files. **Newly created vaults are v4.** Existing v3 vaults remain v3 — with full read **and write** support — until the user opts into the rebuild in §7. Format governs **new writes**; in steady state a vault contains only one format, with two bounded exceptions: (1) the transient `UPGRADING` state governed by a migration manifest (§7.3), and (2) individual v3 files that could not be authenticated during the rebuild and were therefore deliberately left untouched (§7.5). The v4 keyring retains the VMK precisely so those leftover v3 files remain readable.

Rotation types:

| Rotation | Steps | Re-encrypts media? |
|---|---|---|
| **KEK rotation** | Generate KEK#2; re-encrypt every file *header* under the new per-file `headerKey` (derived from KEK#2 + same `fileId`), leaving `dek` unchanged; add keyring entry; set `activeKeyId=2`; retire KEK#1 afterwards | **No** — payload bytes bit-identical; Tink AAD is the immutable `fileId` |
| **Password / PIN rotation** | New salt + Argon2id params → new PDK → re-wrap **only the VMK** in `.vault` | No; keyring, headers and biometric blob untouched |
| **VMK rotation** (rare: suspected VMK compromise) | Renew keyring under new VMK + renew biometric blob (requires biometric prompt) | No |
| **Per-file DEK rotation** (rare: suspected single-file key compromise) | Re-encrypt that one payload under a new DEK and rewrite its header | Yes, one file only |

**Why a DEK layer is still needed:** it makes KEK rotation a header-only operation and allows retiring one file's key independently. It is **not** needed for nonce separation:

> Tink derives a fresh stream key per file via `HKDF-SHA256(ikm=KeyValue, salt=random32, info=AAD)`. Even with one shared `KeyValue`, a collision of the 7-byte nonce prefixes is harmless because the two files have different derived keys. Likewise, reusing a 96-bit GCM nonce across headers is harmless once `headerKey = HMAC(KEK, domain ‖ fileId)` gives each file its own key.

Do not reintroduce a "nonce separation" argument for the DEK in future revisions.

---

## 3. Decision — payload container v4

### 3.1 Wire layout

Allocate unused version byte `4` (`VERSION_TINK_STREAM = 4`). Keep `MAGIC_V2 = "FGVLTENC"` so existing `.bin` identification by magic keeps working; old readers reject on the version byte.

```
offset   size   content
0        8      magic "FGVLTENC"
8        1      version = 4
9        2      keyId (big-endian u16, cleartext — selects KEK)
11       16     fileId (random, cleartext, immutable for the life of the file)
27       12     header IV (AES-256-GCM)
39       4      encrypted header length N (big-endian u32)
43       N      encrypted header: GCM ciphertext + 16-byte GCM tag
43+N     T      Tink AES256_GCM_HKDF ciphertext (see 3.3)
```

`payloadOffset() = 43 + N`. The separate `contentIv(16)` and trailing `Poly1305 tag(16)` fields disappear — Tink carries its own header (`u8 len | salt(32) | noncePrefix(7)`) and per-segment tags inside `T`.

Header **GCM associated data** = `magic ‖ version ‖ keyId ‖ fileId ‖ N` (the cleartext prefix). The header IV is implicitly authenticated by GCM as the nonce. This binds the header to its file ID, key epoch and format version: transplanting a header into another file, swapping `keyId`, or downgrading `version` fails at header decryption.

Header **plaintext** packet (before GCM encryption):

```
u16    algorithmParamsId      // 0x0001 = AES256_GCM_HKDF / derived 32 / SHA-256
u32    segmentSizeBytes
u64    ciphertextLength       // exact T, for strict length assertion
32 B   dek                    // raw per-file data key, confidential inside the GCM header
UTF    originalName           // DataOutputStream.writeUTF — unchanged from v3
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
headerKey(file)   = HKDF-SHA256(ikm=KEK[keyId], salt=∅, info="FossifyGallery:Header:AES256GCM:v4" ‖ fileId, L=32)
metadataKey(ctx)  = HKDF-SHA256(ikm=KEK[keyId], salt=∅, info="FossifyGallery:Metadata:AESGCM:v4"   ‖ context, L=32)
```

**No custom KDF.** v4 derivation is RFC 5869 HKDF via Tink's `Hkdf.computeHkdf("HmacSha256", …)` (empty salt → hash-length zero salt per RFC §2.2, 32-byte output). `fileId` is cleartext, so the reader can derive `headerKey` before decrypting the header. The old `deriveSubkey()` (bare `HMAC-SHA256(key, domain)`) is retained **only** for v3 backward-compatible reads and must never be used by new code. Never reuse an old domain string for a new purpose.

### 3.3 Tink parameters (frozen shared spec — identical in Java and Python)

| Parameter | Value |
|---|---|
| Primitive | `AesGcmHkdfStreaming` (Streaming AEAD) |
| `KeyValue` | 32-byte DEK |
| `DerivedKeySize` | 32 |
| `HkdfHashType` | SHA-256 |
| `CiphertextSegmentSize` | **262 144 (256 KiB)** — chosen by JVM measurement (§11.1); device confirmation pending. Readers accept 16/64/256 KiB and 1 MiB from the header. |
| Nonce prefix | 7 bytes (Tink-generated) |
| Output prefix | RAW |
| **Associated data** | **`fileId` — the 16 raw bytes, not a hash of the header** |

The AAD is bound to an **immutable** value. Binding it to `SHA-256(encryptedHeader)` would make KEK rotation and rename unreadable: re-encrypting the header changes the hash and therefore Tink's HKDF-derived stream key, forcing a full payload re-encryption on every rename. With a random DEK per file, cross-file header insertion already fails Tink validation; binding to `fileId` additionally ties the header to its file across rotation. It does **not** provide rollback to an older header of the same file — that requires an immutable header or trusted monotonic state, neither available in a portable vault file. Accepted, documented limitation.

### 3.4 Random access — use Tink's seekable channel

Do **not** reimplement Tink's key derivation, IV construction or segment decoding. Use the library:

```
StreamingAead.newSeekableDecryptingChannel(SeekableByteChannel ciphertext, byte[] aad)
```

with `aad = fileId`. Wrap the `.bin` file in a thin `SeekableByteChannel` adapter that translates `position(p)` → file position `p + payloadOffset` and reports `size()` as `file.size() - payloadOffset`. Expose it through `Channels.newInputStream(decryptedChannel)` to preserve the existing `openDecryptedContentStream(file, startOffset, meta)` signature. `incrementCounter()` is a v3 read-path relic and must not appear in the v4 path.

**Gate before Stage 2:** verify that Tink's seekable channel caches the current decrypted segment. The video player makes many small random reads; if the channel re-decrypts a segment per read, add a thin segment cache keyed by segment index or retune segment size from the §11.1 measurements.

### 3.5 Integrity verification (strict paths)

`verifyFileIntegrity()` must:

1. Assert `file.length() == payloadOffset + ciphertextLength` (from the authenticated header).
2. Decrypt/authenticate every segment in order, including the last, and assert the final segment carried `lastFlag = 1`.
3. Fail closed if EOF is reached before the final segment — Tink warns that confusing EOF with decryption error permits truncation.

The exact-length check also mitigates Tink's "unauthenticated trailing bytes after a full final segment" behaviour (tink-cc issue 21). Header swaps and `keyId`/`version` tampering are rejected by the header GCM AAD before Tink is invoked.

### 3.6 Behaviour on single-bit corruption — v3 vs v4 (user-visible)

"Decryptable" and "authenticated" are different. The app's normal viewing paths (thumbnail via `EncryptedGlideLoader`, photo via `PhotoFragment`, video via `EncryptedMediaDataSource`, dimensions via `MediaDimensions`) call `openDecryptedContentStream` / the seekable channel **without** full-file verification. `verifyFileIntegrity()` is called only on export, decrypt-to-folder, conflict checks and pre-wipe verification.

| Bit flip in | v3 (CTR + Poly1305) | v4 (Tink streaming AEAD) |
|---|---|---|
| GCM header (filename/dates/thumbnail) | Header auth fails → `readMetadata()` returns null → file effectively unavailable | Same — header auth fails → metadata unavailable |
| Payload ciphertext | CTR turns it into exactly **one wrong plaintext bit**. The photo/video still decodes; the app displays it **silently** (invisible in a JPEG, one glitched frame in MP4). Only export / pre-wipe rejects it via Poly1305. | That **segment's GCM tag fails**. Reading that segment throws an authentication error; other segments still decrypt. Playback works until the read reaches the bad segment, then errors there; full-file verification rejects the whole file. |
| Integrity tag (Poly1305 / per-segment GCM) | Payload decrypts perfectly; only verification fails | Payload of that segment decrypts but the tag fails → segment rejected |

So the answer to "does a 1-bit change make the file undecryptable?" is **location- and format-dependent**:

- Header bit → effectively unreadable in both formats.
- v3 payload bit → **not** undecryptable; silent corruption, visible with a tiny artifact.
- v4 payload bit → the affected **segment** is undecryptable (hard error), not the whole file; the file does not vanish.

v4 therefore trades silent degradation for loud, localized failure. Left unhandled, a single bad segment makes sequential playback **stop with an error** (small GIFs may fail to display entirely) — a real UX regression versus v3. Seeking past the bad segment recovers the rest, since segments decrypt independently, but Media3/Glide do not do this automatically.

**Adopted policy — tolerant display, strict data operations:**

| Path | Behaviour on auth failure |
|---|---|
| Thumbnail / photo / video playback / dimension reading | Wrap the seekable-channel read: on a bad segment, substitute a placeholder (zeros) for **that segment only**, reposition to the next segment boundary, and continue. Never write the substituted bytes back. Emit a **non-blocking corruption report** (badge / one-time notification) so the failure is not silent. |
| Conflict comparison, pre-wipe verification, encrypted-area rebuild, dedup | Strict: reject the file / skip it as in §7.5. The corrupted bytes must never be treated as valid data or re-encrypted into a valid-looking v4 file. |
| **Explicit "Decrypt file as-is"** (user-requested) | Allowed as an **unverified best-effort output**, not as a normal export — see §3.7. Nothing is "recovered": the result is still visibly damaged. |

Rationale: v3 is the format where data **rots silently** — playback tolerates it and the damage is only discovered at export time, often too late. v4 should keep the *detection* benefit but avoid making the user's media unplayable. Substituting zeros is safe for display (the decoder sees a known placeholder, not attacker-chosen plaintext) and does not launder corruption because strict paths still refuse it and no write path consumes the decoded stream.

**Frequency assumption:** uncorrectable bit flips on modern phone eMMC/UFS are rare (ECC handles normal errors); they are more plausible on cheap USB/SD media and after unsafe removal, interrupted writes or power loss — exactly the external-vault use case this app supports. The design assumes corruption is **exceptional**, not routine. If a vault produced frequent bit flips, that is a storage-hardware/filesystem fault; no cipher can compensate for it. Segment size is still decided by measurement (§11.1), not by this argument.

Implementation note: verify experimentally that Tink's seekable channel can be repositioned after an authentication failure; if not, add a thin layer that tracks segment boundaries itself. Add tests for the tolerant/strict split (§10 item 10). Call the behaviour change out in DOCS.md and the changelog.

### 3.7 Corruption reporting UX

Detection happens in the read layer (Glide loader, Media3 `DataSource`, `PhotoFragment`, `MediaDimensions`) on arbitrary threads and can fire on every read, seek or thumbnail regeneration. It must **not** surface there directly.

**Where it is shown:**

- **Grid / album list:** a small **yellow `!` overlay** on the thumbnail of each affected file (`MediaAdapter`, `DirectoryAdapter`). This is the primary indicator — the user sees damage without opening the file.
- **Fullscreen viewer (photo / video / GIF / SVG):** a small yellow `!` badge in a corner; tapping it opens the corruption dialog below (blocking, with two actions).
- **First detection only:** one non-blocking toast/snackbar. Never a dialog, never repeated on every appearance or seek.

**Repetition rule — sticky and deduplicated:**

- Detection writes a record keyed by (vault-relative path + file fingerprint) into a new Room table `vault_corruption_reports` (`firstDetectedAt`, `lastDetectedAt`, `badSegmentCount`, `notified`). App-private DB only — never a cleartext sidecar file in the vault.
- The toast is shown once per file when `notified = 0`, then the flag is set. The badge persists as long as the record exists, including across restarts.
- Later re-detections update `lastDetectedAt` but produce **no new toast**.
- Records are removed when the `.bin` is deleted, securely wiped, or replaced by a restored/re-encrypted file (fingerprint change); path updates follow the existing rename plumbing.
- When "Decrypt file as-is" writes a plaintext output, that output path is added to the same table with `derivedFromCorrupt = true`, so the yellow `!` badge and the hidden Move action follow the decrypted file as well ("decrypt edilse bile"), until a verified re-encryption replaces it.

**Corruption dialog** — two entry points, same text, mirrored button emphasis:

| Entry point | Left button | Right button |
|---|---|---|
| Yellow `!` badge / file menu | "Dosyanın şifresini çöz" | **"Tamam"** (default) |
| **Normal export icon** (`decryptFileToDirectory`) | **"İptal"** | "Dosyanın şifresini çöz" (primary) |

Both entry points show the **same** warning and the behaviour is **identical in v3 and v4** vaults. Pressing "Dosyanın şifresini çöz" in either dialog invokes the same `allowUnverified = true` decrypt action; pressing "Tamam" / "İptal" cancels with no output.

> TR: "Dosyanın bazı kısımları doğrulanamadı. Dosyanızda veri kaybı oluşmuş olabilir. Dosyanızın başka bir yedeği varsa geri yükleyebilirsiniz ya da orijinal dosya internette yer alıyorsa mevcut dosyanızın şifresini çözüp web aratması yapabilirsiniz."
>
> EN (finalise with localisation): "Some parts of this file could not be verified. Data may have been lost. If you have another backup, you can restore from it; or, if the original is available online, you can decrypt the current file and search the web for it."

"Dosyanın şifresini çöz" reuses the existing decrypt function (`decryptFileToDirectory` with an `allowUnverified = true` variant) — no separate "export recovered copy" feature is introduced.

**"Decrypt file as-is" behaviour (adopted — nothing is claimed to be recovered):**

- The dialog explicitly says data may have been lost. **No "recovered" / "kurtarılmış" wording** anywhere in UI or docs.
- `decryptFileToDirectory(..., allowUnverified = true)`: skips the strict pre-check, writes the stream through the tolerant reader (zeros for bad segments), and **never deletes the encrypted source** (`deleteEncryptedSource = false` forced).
- The output is a best-effort artifact: for a JPEG the intact portion may be enough for reverse image search; for an MP4 a zero block inside `mdat` may still leave usable frames, while damage in the `moov` atom may make it unsalvageable. No promises in the UI.
- The decrypted bytes are **never** fed back into dedup, conflict comparison, pre-wipe verification or re-encryption. The corruption record stays until the user restores/replaces the original.
- Only reachable via this explicit action — **normal export remains strict**.

**Strict operations — current code vs. revised policy.** Only normal export, dedup/conflict decisions, pre-wipe verification and the rebuild must stay strict. The other gates are policy inherited from the old code, where damage was invisible; now that the yellow `!` badge and Room record are persistent, the policy job is done by the badge.

| Operation | Code path | Current code | Revised policy |
|---|---|---|---|
| Normal export | `decryptFileToDirectory` (`CryptoManagerFileCrypto.kt:557`) | `null`, nothing written | **Keep strict**: show the dialog with İptal left / "Dosyanın şifresini çöz" right. |
| Copy encrypted file | `copyOrMoveEncryptedFileToDirectory` (`CryptoManagerConflict.kt:517`) | `null`, whole operation refused | **Allow** — the payload is copied verbatim, no tag is recomputed, so nothing is laundered; carry the badge/Room record to the copy. |
| Move encrypted file | same | `null` | **Remove the Move action entirely** for files with a corruption record — both in encrypted and decrypted states. It is functionally identical to copy, so no Move entry is shown and no explanatory notice is needed. |
| Conflict / dedup comparison | `compareEncryptedStreamAndVerifySourceMac` (`CryptoManagerFileComparison.kt:311–332`) | `SOURCE_MAC_INVALID` | **Keep strict** — never auto-dedupe or merge damaged bytes; the user gets a real conflict decision. |
| **Decrypt + delete `.bin` source** — the actual pre-wipe gate | `decryptFileToDirectory` (`CryptoManagerFileCrypto.kt:557`) | `null` — no plaintext written, `.bin` kept | **Keep strict.** Verify the ciphertext is intact **before** writing plaintext and before deleting the `.bin`. Without a MAC you could not distinguish "damaged" from "valid but unusual media", so you would write garbage and delete the only surviving copy. The `.bin` is preserved on failure; the user can still try "Decrypt file as-is". |
| Treat a `.bin` as a verified duplicate during a **new encryption** | `arePlainAndEncryptedIdentical` (`CryptoManagerFileComparison.kt:627`) | `false` — not a verified duplicate | **Keep strict** — a damaged `.bin` is never accepted as proof that "this plaintext is already safely stored", so it is not reused as a duplicate. (After a fresh encryption the plaintext source is still wiped; the primary protection is the decrypt-direction row above.) |
| Rename of encrypted file | `renameEncryptedFile` (`CryptoManagerFileCrypto.kt:717`) | `false`, refused | **Allow** — the operation rewrites only the header and copies payload verbatim (v3 MAC covers payload only; v4 payload is bound to immutable `fileId`), so renaming cannot launder damage; the badge persists across the rename. |
| **Deleting the `.bin` itself** | normal delete / secure wipe | **Not blocked by any integrity check** | **Unchanged — deletion is always allowed.** The "pre-wipe verification" row is about *not wiping the plaintext source during a new encryption*, not about forbidding deletion of a damaged file. |
| Encrypted-area rebuild | §7.5 | skipped | **Keep strict** — unverifiable bytes are never re-encrypted. |

The three invariants that make the relaxed operations safe: (a) no authentication tag is ever recomputed over damaged bytes, (b) no source is deleted on the strength of an unverified copy, (c) no dedup/conflict decision is made from unverified bytes.

**Cryptographic bottom line:** reading is not impossible — with the key, GCM arithmetic still yields plaintext; Tink simply refuses to release it through verified APIs. **Repair is impossible** — the original bits are information-theoretically gone; only a backup, if one exists, restores them.

**v3 / v4 parity and the one unavoidable difference.** The dialog, badge, decrypt-as-is action and the revised operation policy are **identical in v3 and v4 vaults**. Detection *timing* differs: v4 detects a bad segment during normal playback (per-segment GCM tags); v3 has no per-segment tag, so damage can only be established by a full Poly1305 verification — during export, pre-wipe, or an opportunistic background verification on full-screen open. Without that opportunistic scan a v3 file can be shown silently corrupted and the badge appears only after a verification attempt. Document this in DOCS.md.

---

## 4. Decision — metadata record v4

All small encrypted strings (`.meta` folder names, `FVSTR1:` vault strings, `.restore_info`, `VaultResumeState` blobs, encrypted stealth-album JSON payloads) move to:

```
"FVSTR2:" + base64( [ u8 version=4 ][ u16 keyId ][ u8 contextType ][ discriminator? ][ tinkCiphertext ] )
```

- `tinkCiphertext` is Tink `Aead` / AES-256-GCM (RAW prefix, random 96-bit nonce) under
  `metadataKey = HMAC-SHA256(KEK[keyId], "FossifyGallery:Metadata:AESGCM:v4" ‖ context)`.
- `context` is supplied by the caller from cleartext state (folder path, record purpose, album ID, `resume`, …) and is also passed as GCM AAD. Cleartext `contextType` + any discriminator are sufficient to reconstruct it.
- Per-record keying removes the RNG-failure motivation for AES-GCM-SIV. Random 96-bit nonces under a per-record key are far below any practical limit (millions of records ≪ 2³² per key).
- **No SIV and no Conscrypt dependency.** Tink Java's AES-GCM-SIV requires Conscrypt as JCE provider (native library per ABI). `XChaCha20-Poly1305` (192-bit nonce, no Conscrypt, available in Tink Python) is the fallback if AES-GCM is ever unsuitable. Verify in the pinned Tink version.
- Readers accept both `FVSTR1:` and `FVSTR2:`; writers emit only the format of the vault they are writing to (see vault-level-format rule in §2).

> **Vault-level consequence:** metadata format follows the vault format. A v3 vault keeps writing `FVSTR1:` strings and v3 `.meta` files; a v4 vault writes `FVSTR2:` / v4 `.meta`. The rebuild converts every metadata record (including encrypted stealth-album JSON) along with media. `JSON/` virtual-album files remain CLI-isolated as before.

---

## 5. Decision — `.vault` header v4 (PDK → VMK → keyring)

Keep the outer `writeVaultHeader` framing (magic `FGVLT2`, Argon2id params, salt, IVs), bump `version` to 4, and split the wrapped material:

```
.vault v4:
  magic, version=4, kdf=Argon2id, iterations, memoryKb, parallelism, protectionType
  salt, ivPdk,  PdkWrappedVmK      = AES-256-GCM(PDK, ivPdk, VMK)
  ivVmk,  VmkWrappedKeyring  = AES-256-GCM(VMK, ivVmk, Keyring)
  Keyring = { activeKeyId, entries: [ keyId → KEK(32 B) ] }
```

Consequences:

- **Existing biometric/Keystore blobs (containing the VMK) remain valid as-is.** No re-registration, no second biometric prompt.
- **Password/PIN change re-wraps only `PdkWrappedVmK`.** Keyring, headers, metadata and biometric blob untouched.
- **KEK rotation touches only `VmkWrappedKeyring` plus file headers.**
- **VMK rotation is a separate, rare operation** renewing keyring + biometric blob.
- **Compromise caveat:** VMK/KEK rotation protects only *future* use of this vault. If an attacker already copied the old v3 vault, rotating keys cannot invalidate that copy — the old MEK still opens it. Real remediation requires re-encrypting from plaintext with new keys and destroying old copies. For pure modernization (no suspected compromise), deriving new subkeys from the existing VMK would suffice; we still use a fresh random KEK#1 because the payload format itself changes and a full rebuild is unavoidable anyway.

**No automatic upgrade.** A v3 `.vault` is **never** rewritten automatically on unlock. It stays a fully functional v3 vault (read and write) until the user explicitly opts into the full rebuild (§7). `.vault.v3.bak` is created only by that rebuild, immediately before the final atomic `.vault` swap, as an **emergency header copy**, retained until the first successful unlock of the resulting v4 vault. It is **not** a rollback mechanism — by then the media files have already been replaced. The **real rollback is the user's own backup**, created in response to the preflight warning in §7.2.

---

## 6. CLI interoperability contract

The CLI is **encrypt-only by design**. Frozen shared spec (mirror in `helpers/vault/TinkStreamSpec.kt` and a `SPEC` block in `cipher_vault.py`):

```
ALGORITHM          = AES256_GCM_HKDF
DERIVED_KEY_SIZE   = 32
HKDF_HASH          = SHA256
SEGMENT_SIZE       = 262144 (256 KiB; JVM-measured §11.1, device confirmation pending)
OUTPUT_PREFIX      = RAW
TINK_AAD           = fileId                       (16 raw bytes)
HEADER_AAD         = magic | version | keyId | fileId | N
HEADER_KEY_DOMAIN  = "FossifyGallery:Header:AES256GCM:v4"
METADATA_KEY_DOMAIN= "FossifyGallery:Metadata:AESGCM:v4"
KDF_V4             = HKDF-SHA256 (RFC 5869), salt = empty, info = domain|id, L = 32
```

Per file the CLI generates random `fileId(16)` and `dek(32)`, derives `headerKey` from `KEK[activeKeyId]` + `fileId`, writes the cleartext prefix and GCM header, then streams the payload through Tink with AAD = `fileId`.

**The CLI does not write metadata records or touch `JSON/`** — it writes `.bin` files and `.meta` folder names into the vault whose `.vault` it finds. Its v3 path is retained long-term (§7.4).

Actions:

- Pin Tink in `gradle/libs.versions.toml` (`tink-android`) and `tools/fossify-cipher-cli/requirements.txt` (`tink>=X,<Y`) to the same minor line; record the exact version here once chosen. No Conscrypt.
- Keep `pycryptodome` + `bouncycastle` for **legacy v3 reads and Argon2id** only.
- Extend `make_fixtures.py` with v4 fixtures (0, 1, 15, 16, 17, 65535, 65536, 65537, ~1.1 MB, real image) and extend `CliFixtureCompatibilityTest`: per-segment tamper, truncation, header transplant, `keyId`/`version` swap, wrong KEK, plus **rename-and-KEK-rotation keep the payload identical**.
- Add a test-only independent Python decrypt helper so Android-produced v4 files can be validated in CI despite encrypt-only production CLI.

---

## 7. Vault upgrade — UX, state model and CLI mismatch (decided here, not deferred)

Stages 1–3 ship together in release 1.20; they are separated below only for clarity.

| Stage | Change | Risk |
|---|---|---|
| **0 — Readiness** | Tink dependency; frozen spec; dual-read of v4 in `readMetadata`, `verifyFileIntegrity`, seekable-channel adapter; fixture harness; segment-size measurements. No v4 writes. | Low |
| **1 — Format designation + warnings** | Vault-level format: new vaults v4, existing v3 vaults stay v3 with full read/write. Entry warning + "always show" checkbox + Settings toggle; preflight warning, free-space and battery checks; CLI format detection and mismatch guard; 1.20 compatibility documentation. | Low |
| **2 — New v4 writes** | All new vaults and all writers (app + CLI) use v4; v3 writers remain active for v3 vaults. | Medium |
| **3 — Opt-in full rebuild** | User-triggered, vault-wide rebuild replacing **all** media and metadata, then swapping `.vault` to v4 atomically. Foreground service, resumable manifest, no header-only update mode. | High |
| **4 — Long-term** | Keep v3 writer, v3 reader, CLI v3 path and Poly1305 read path effectively indefinitely; BouncyCastle stays for Argon2id regardless. | Low |

### 7.1 Warning 1 — vault entry (per entry, default on)

Whenever the user enters a v3 vault, show a non-blocking notice:

> "Your Encrypted Vault was created with an outdated version of the app and uses the old encryption algorithm. If you wish, you can recreate your Encrypted Vault according to the current version's encryption algorithm."
>
> TR: "Şifreli kasanız uygulamanın güncel olmayan bir sürümünde oluşturulmuş ve eski şifreleme algoritmasını kullanıyor. İsterseniz şifreli kasanızı güncel versiyondaki şifreleme algoritmasına göre yeniden oluşturabilirsiniz."

- Under it, a checkbox **"Always show"** — **default ON** (`Config.vaultUpgradeWarningAlwaysShow = true`).
- The same switch is available under **Settings > Encrypted vault > Always show vault update warning**, also default ON.
- Turning it OFF only suppresses the entry prompt; the rebuild remains reachable from Settings.
- Dismissing the warning leaves the vault as v3, fully usable for reading and writing.

### 7.2 Warning 2 — consent and preflight (once per rebuild)

When the user accepts Warning 1, show a second, detailed warning:

> "It is recommended to back up before updating the Encrypted Vault to use the current encryption algorithm. This way, the risk of data loss due to an interrupted update is eliminated and, if you wish, you can continue using the un-updated Encrypted Vault in older versions of the app."
>
> TR: "Şifreli kasayı güncel şifreleme algoritmasını kullanması için güncellemeden önce yedek almanız önerilir. Böylece güncellemenin yarıda kalması sebebiyle veri kaybetme riskiniz ortadan kalkar ve isterseniz güncellenmemiş şifreli kasanı uygulamanın eski sürümlerinde kullanmaya devam edebilirsiniz."

Preconditions (checked at this point, then skipped on later attempts once acknowledged):

- **Free space:** require `freeSpace >= max(v3 .bin size) + Tink per-file overhead + safety margin` (margin ≥ 256 MB or 10%). The rebuild converts files one at a time, so the steady-state peak is **one** temporary file, not a second copy of the whole vault. Requiring the sum of all `.bin` sizes would block migration on space-constrained devices for no reason. The check runs again before each file if the device is shared with other writers.
- **Battery:** require `>= 20%`, **even when charging**. If below: "Your battery is low, please charge your device to at least 20%." / TR: "Şarjınız az, lütfen cihazınızın şarj seviyesini en az %20 doldurun."
- On user confirmation, persist `Config.vaultUpgradePreflightAcknowledged = true`; these checks are **not repeated** for subsequent rebuild attempts.

**The warning is informational only — correctness must not depend on the user heeding it.** Incoming calls, low battery, process death under memory pressure, and USB removal can always occur. Safety comes from the three mechanisms in §7.3, never from "please don't leave the app".

### 7.3 Rebuild operation (foreground service, resumable)

- **There is no "only `.vault` changes" update mode.** The rebuild covers **all media and all metadata**. `.vault` is swapped to v4 only after everything else is converted.
- Run in a **foreground service with a notification** so the key stays in RAM until completion. This is the explicit exception to the normal "on background / screen off → lock vault and wipe keys" rule. Per [ADR 0002 §7](0002-operational-security-hardening.md), the exception now covers **all** long-running or destructive file operations (encrypt, decrypt/export, cross-album copy/move, delete including secure wipe, external sync and this rebuild) through a single foreground-operation registry:
  - Normally, leaving the app or turning the screen off locks the vault and wipes keys.
  - While a registered operation is active, keys stay in memory; returning continues the operation **without a password prompt**.
  - On completion or cancellation, the operation is unregistered, keys are wiped, and the user **must enter the password again**.
  - `FLAG_KEEP_SCREEN_ON` may be set (a hint, not a guarantee).
- Progress UI shows a progress bar with the **persistent** notice: "Please do not exit the app or turn off the screen during the update." / TR: "Lütfen güncelleme sırasında uygulamadan çıkmayın veya ekranı kapatmayın."
- Safety mechanisms (mandatory):
  1. **Per-file atomic replacement** — write `.tmp_<random>.bin`, fsync, then atomic move over the source.
  2. **Never delete before verification** — authenticate the v3 source and fully verify the v4 result (to final Tink segment) before the source is touched.
  3. **Resumable manifest** — a hidden, GCM-under-VMK authenticated manifest inside the vault holds a fresh random `KEK#1` plus the list of converted files. Because `.vault` remains v3 during the rebuild, the CLI still sees v3; the manifest also acts as a marker that makes the CLI refuse transfers while the rebuild is in progress. On resume (password required if the process died), skip already-converted files.
  4. **Idempotency and temp hygiene** — each file's format is read from its own version byte, so a file already at v4 is skipped without re-encryption. At startup and on resume, stale `.tmp_<random>.bin` files from an interrupted run are securely wiped and removed. The operation is safe to re-run any number of times.
  5. **External volume loss** — if the vault lives on USB/SD and the volume disappears, pause (keep keys in RAM, show a "vault disconnected" notification) and resume automatically when it remounts; never treat a missing volume as a failed file.
- Final sequence: convert all metadata records, sweep for any stragglers, back up `.vault` → `.vault.v3.bak`, atomically write the v4 `.vault` keyring, delete the manifest, wipe keys, require password.
- **Tink detail:** writing a segment authenticates it, but whole-file integrity is only proven by *reading to EOF* (Tink returns authenticated `-1` after the final-segment tag). The verification step after writing the temp file must therefore be an actual full decrypt-read, not just a successful write.
- If the user declines at any point: no `.vault` change; the vault remains v3 with full read/write.

### 7.4 Vault-level format and CLI mismatch

- Format is determined from `.vault` by both app and CLI. A v3 vault with `.vault` v3 accepts v3 `.bin` writes from the CLI; a v4 vault accepts v4 writes.
- If new encrypted files are transferred from the CLI into a folder whose `.vault` format differs from the vault currently selected in the app (v3 ↔ v4 in either direction), the transfer is **refused with a clear "Encrypted Vault format mismatch" note**, no files are copied, and the **`.pending_sync` file is left untouched** (it is preserved because no transfer occurred).
- Release 1.20 and later: Encrypted Vaults created by 1.20+ are **not compatible** with vaults created by older versions; document this in README, DOCS.md, the CLI README and FORK_CHANGELOG. Such transfers are likewise refused.
- The v3 writer in the app and the CLI's v3 path live long-term (Stage 4); users who never opt into the rebuild keep a working v3 vault indefinitely.
- Detailed migration UX for users upgrading from older app versions (prompt-to-upgrade, opt-in bulk update) is governed by these same rules; no separate ADR is required for the mechanics.

### 7.5 Verification failure semantics (what "unverifiable" means)

**Definition.** A file is *unverifiable* when its stored bytes no longer match the authentication data that was written with them — the Poly1305 tag, the AES-GCM header tag, the expected file length, or a known magic/version. It is a **cryptographic integrity failure**, not "the app cannot open it right now" (which could be a transient key, cache or mount problem).

**What it looks like to the user.** The file **does not disappear**. The rebuild never deletes or hides a source on failure — "verify before delete" means the source is left byte-for-byte where it was. A corrupted file may *already* display with an artifact or fail to decode before the rebuild starts: CTR mode turns a single flipped bit into a single wrong pixel, which is often invisible. Full authentication detects silent bit rot that the eye never noticed. So the rebuild doubles as a vault-wide integrity scan, and a "verification failed" report means **pre-existing damage that the rebuild detected**, never damage the rebuild caused.

**Failure taxonomy and handling:**

| Where | Cause | Handling |
|---|---|---|
| v3 source, before conversion | Poly1305 tag mismatch; header GCM tag mismatch; length ≠ `payloadOffset + originalSize + 16`; unknown magic/version; all-zero content IV | **Skip the file.** Leave the v3 bytes untouched and visible. Record `FAILED_VERIFICATION` + reason in the manifest. Delete the temporary v4 file if one was created. |
| v4 output, before replacement | Disk full mid-write; USB removed; Tink final segment missing or tag mismatch; length mismatch | **Abandon that temp file**, source untouched, record `FAILED_WRITE`, retryable. |
| `.vault` swap at the end | Write/sync failure on `.vault` | Keep `.vault.v3.bak` and the still-v3 `.vault`, abort the swap, report. Vault stays v3. |
| Wrong key / wrong vault selected | A large fraction of files fail at once | Abort the whole rebuild with a "wrong key or corrupted vault" diagnosis instead of marking hundreds of files failed. Guard: if the first N files all fail, or failures exceed ~10%, stop. |

**Why unverifiable files are left in v3 — the security reason.** Re-encrypting a corrupted v3 file would produce a v4 file with a *valid* tag over corrupted plaintext. That would "launder" the damage: future verification would pass and the corruption would become permanent and undetectable. Refusing to convert preserves the evidence and leaves the file recoverable from the user's backup (which Warning 2 asked them to take).

**After the rebuild:** the vault flips to v4; the handful of unverifiable files remain as v3 files, still visible and readable thanks to the retained VMK (§2). This is the documented exception to "no format mixing". The end-of-rebuild report states: *"N files could not be converted and remain in the old format; they are still accessible."* The manifest keeps failed entries so a **Retry failed** action can re-attempt them — after the user restores the file from backup, retry succeeds. Files that are genuinely unrecoverable are deleted normally by the user (secure wipe), not by the migrator.

**Non-goal:** no automatic quarantine or hiding of unverifiable files. That would be a destructive UI change and is left for a future decision.

---

## 8. Risks and mitigations

| Risk | Mitigation |
|---|---|
| **Segment size unvalidated** | **Measurement gate before Stage 2:** sequential throughput, seek latency, player read pattern. 64 KiB remains a reasonable *seek granularity* default; choose by measurement, not "bit-rot isolation" (the reader throws on a corrupt segment rather than skipping it). |
| Tink seekable channel re-decrypts a segment per small read | Verify current-segment caching in Stage 0; add a thin segment cache or retune segment size |
| **Reported playback stutter (observed by tester on a debug build)** | Not yet attributable: v4 writers are not enabled, so a v3 (CTR) video does not use the Tink path at all. Likely debug-build overhead, but take seriously: collect the device read-pattern probe on a v4 file, compare debug vs release, and only then decide between keeping 256 KiB, adding a segment cache (§3.4), or reconsidering the size. Never dismiss as "just perception" before measuring. |
| Truncation / trailing-byte acceptance | Read to `lastFlag`; strict `file.length() == payloadOffset + ciphertextLength` |
| AAD / param mismatch Java ↔ Python | Frozen spec, pinned versions, golden fixtures in CI |
| Header transplant / epoch downgrade | Header GCM AAD binds `magic‖version‖keyId‖fileId‖N`; Tink rejects via DEK mismatch |
| Same-file header rollback | Accepted limitation of a mutable header in a portable vault |
| `fileId` collision | 16 random bytes; reject all-zero `fileId` defensively |
| Metadata context not reconstructible | Cleartext `contextType` + discriminator; shared reconstruction helper |
| Keystore blob compatibility | VMK *is* the legacy MEK; no re-wrapping; no biometric prompt in Stage 3 |
| **Rebuild interrupted** (call, low battery, process death, USB removal) | Foreground service keeps keys while alive; per-file atomic replace; verify-before-delete; resumable manifest; re-auth after process death |
| **Unverifiable source file** (latent bit rot, bad USB sector, partial prior write) | Never re-encrypt data that cannot be authenticated — that would bake in the corruption with a valid tag. Skip, leave in v3, record reason, report, retryable; vault still flips to v4 with VMK retained for legacy reads (§7.5) |
| **Background-lock rule conflict** | Explicit migration exception: keys retained in RAM during rebuild, wiped at completion with mandatory re-auth |
| CLI ↔ app format mismatch | Refuse transfer, show note, preserve `.pending_sync` |
| CLI encrypt-only cannot migrate old ciphertext | App rebuild is authoritative; desktop users re-encrypt from plaintext with v4 CLI |
| Tink version drift | Pin exact versions + golden ciphertext vectors in repo |

---

## 9. Alternatives considered

- **AAD = `SHA-256(encryptedHeader)`** — rejected: breaks KEK rotation and rename, and offers no useful protection (random DEK already defeats cross-file insertion).
- **Automatic header-only upgrade on first unlock** — rejected: violates user consent, silently changes the vault format, breaks the "old vaults keep working in old app versions" rollback story, and conflicts with the v3/v4 CLI distinction. Replaced by the explicit, vault-wide rebuild in §7.
- **Manual Tink seek reimplementation** — rejected: duplicates library internals; use `newSeekableDecryptingChannel`.
- **AES-GCM-SIV for metadata** — rejected: needs Conscrypt in Tink Java; per-record keys solve the same concern; XChaCha20-Poly1305 is the no-Conscrypt fallback.
- **Keep AES-CTR + HMAC with AAD** — rejected: no audited cross-language library, no rotation without payload re-encryption.
- **One-shot AES-GCM / SIV for the payload** — rejected: unbounded memory, no random access, whole-file auth failure.
- **Derived per-file keys without stored DEK** — rejected: rotation would require re-encrypting all media.
- **In-place encrypt/decrypt instead of "write new → verify → delete old"** — rejected. A plain *move* is already trace-free when source and target share a filesystem: `rename(2)` / `Files.move` changes only the directory entry, so no data is copied — the app already uses this for atomic temp-file replacement. Encryption/decryption are **transformations, not moves**: the two byte sequences differ, so both must exist until the new one is verified; this is the safety property that preserves the only good copy on interruption. In-place CTR payload overwrite is technically possible (same length) but is non-atomic — a crash mid-write leaves half-plaintext/half-ciphertext with no original to fall back to — and the vault is frequently on a different filesystem (USB/SD), where `rename` degrades to copy+delete anyway. "Forensic trace-free deletion" is not achievable at app level on flash (FTL wear-leveling, journal metadata); the existing CSPRNG overwrite + `fsync` remains best-effort, as documented in DOCS.md §2.

---

## 10. Test plan (exit criteria)

1. **Container:** v4 round-trip all edge sizes; header parse; offset/length arithmetic; seek via Tink channel matches sequential; per-segment tamper, truncation, header transplant, `keyId`/`version` swap, wrong KEK, all-zero `fileId`.
2. **Rotation & rename (critical):** payload bit-identical and decryptable after KEK rotation and after rename; header AAD rejects swapped fields; per-file `headerKey` selection.
3. **Metadata:** `FVSTR2:` round-trip; cross-context ciphertext rejected; key-ID rotation; `FVSTR1:` legacy read.
4. **Keyring:** v3 → v4 rebuild preserves unlock with no biometric prompt; password change leaves keyring/headers/biometric blob bit-identical; VMK rotation renews keyring + blob.
5. **Warnings & settings:** Warning 1 shown on every v3 entry when "always show" is on; checkbox and Settings toggle default ON and persist; turning it off suppresses only the prompt; Warning 2 details, free-space and battery checks; battery <20% while charging still blocks; preflight acknowledgement skips checks on retry.
6. **Rebuild:** v3 vault remains fully read/write when user declines; rebuild covers all media + metadata (no `.vault`-only mode); `.vault.v3.bak` created only at final swap; atomic replace, verification before deletion, resume after interruption; background keeps keys with no re-prompt; completion wipes keys and forces password; `FLAG_KEEP_SCREEN_ON` and notification present.
7. **CLI mismatch:** `.vault`-based format detection; v3 ↔ v4 mismatch refuses transfer, shows note, leaves `.pending_sync` intact; 1.20 compatibility statement present in docs.
8. **Cross-implementation & regression:** all v4 fixtures decrypt on Android; Android v4 output validated by independent Python helper; all existing v1/v3/v5 fixtures and `Poly1305AesVectorsTest` still pass.
9. **Verification failures (§7.5):** tampered Poly1305 tag, tampered header tag, truncated file and unknown magic each cause the file to be skipped — not deleted, not hidden — and leave its bytes unchanged; the temp v4 file is cleaned up; the reason is recorded in the manifest; the end report counts failures; **Retry failed** succeeds after the source is restored; a vault with failures still flips to v4 and the leftover v3 file remains readable via VMK; a mass-failure run aborts instead of marking every file failed.
10. **Single-bit corruption (§3.6/§3.7):** v3 header flip → metadata unavailable; v3 payload flip → still displays with artifact and only export/pre-wipe rejects; v4 header flip → metadata unavailable; v4 payload flip → only that segment throws, neighbours still decrypt, full verification rejects; bad v4 segment yields substituted zeros and continued playback, not a crash. Yellow `!` overlay appears in grid and viewer; toast fires exactly once per file (`notified` flag), persists across restarts, never repeats on seek; record cleared on delete/rename/restore. Normal export shows the dialog then returns null if cancelled; comparison yields `SOURCE_MAC_INVALID`; pre-wipe leaves the plaintext source untouched; rebuild skips the file. Copy is allowed and carries the badge; the Move action is hidden entirely for corrupted files (encrypted and decrypted states); rename is allowed and keeps the badge; deleting the `.bin` is never blocked. Two dialog variants with identical text: badge entry → "Dosyanın şifresini çöz" left / Tamam right; export entry → İptal left / "Dosyanın şifresini çöz" right. The decrypt action writes zeros-substituted output, keeps the `.bin` source and is never reused by strict paths. Identical behaviour in v3 and v4; only detection timing differs.

---

## 11. Decisions resolved / remaining

**Resolved in rev 3–6:**

- No automatic `.vault` upgrade; user-approved, vault-level rebuild of all media + metadata.
- Warning 1 + default-on "always show" (entry checkbox and Settings toggle); Warning 2 + one-time free-space/battery preflight.
- Foreground-service exception to the background-lock rule; keys retained during rebuild, wiped + re-auth at completion.
- `.vault.v3.bak` is an emergency header copy only; the real rollback is the user's backup.
- Format is vault-level: new vaults v4, v3 vaults fully usable until rebuilt; v3 app writer and CLI v3 path live long-term.
- CLI mismatch: refuse transfer, show note, preserve `.pending_sync`; 1.20 incompatibility documented.
- Rev 2 items retained: immutable `fileId` AAD, per-file `headerKey`, no DEK nonce argument, Tink seekable channel, PDK/VMK split, no SIV/Conscrypt.
- Bit-rot engineering (rev 4–6): v3 silent vs v4 loud-and-localized corruption; tolerant display (substitute zeros for a bad segment) + strict data operations; persistent yellow `!` badge and one-time toast in a Room table; two dialog variants with mirrored buttons; "Dosyanın şifresini çöz" reuses the decrypt function and never claims recovery; Move hidden for corrupted files (also after decrypt), copy/rename allowed, delete always allowed; unverifiable files stay in v3 and are never re-encrypted; in-place encrypt/decrypt rejected.
- Terminology: reverted to "Encrypted Vault / Şifreli Kasa" for all user-facing text and ADR prose; no "encrypted area / şifreli alan".

**Remaining gates — elaborated:**

### 11.1 Segment-size measurement (blocks Stage 2 only)

Placeholder **65 536 B**. Measure before fixing the value; do not argue from bit-rot isolation (the reader throws on a corrupt segment rather than skipping it) — argue from seek granularity and I/O cost.

What to measure on a mid-range device with a real ~2 GB MP4 on both internal storage and a USB drive:

| Metric | How | Decision signal |
|---|---|---|
| Sequential decode throughput | Time to fully decrypt+authenticate a 2 GB file via the seekable channel, memory-monitored | Must not fall materially below current CTR read speed; smaller segments mean more GCM init/tag operations |
| Seek latency | Time from `position(offset)` to first decrypted byte, for 50 random offsets | Bounded by one segment decrypt; 64 KiB ≈ sub-millisecond crypto + one disk read |
| Player read pattern | Instrument `FileChannel` reads from Media3/ExoPlayer during playback, seek and scrubbing | Read sizes (typically 128 KiB–1 MiB) and their alignment determine whether segment boundaries cause repeated re-decryption |

Trade-off table (overhead = 16-byte tag per segment; first segment is smaller because Tink's 40-byte stream header shares it):

| Segment | Tag overhead | Segments in 2 GB | Seek granularity |
|---|---|---|---|
| 16 KiB | 0.098 % | ~131 000 | Very fine; high per-segment GCM setup cost |
| **64 KiB** | 0.024 % | ~32 000 | Fine; balanced — **current placeholder** |
| 256 KiB | 0.006 % | ~8 000 | Coarse; fastest sequential |
| 1 MiB (Tink default) | 0.0015 % | ~2 000 | Coarse; may re-decrypt up to 1 MiB per seek |

Decision rule: choose the **smallest** segment whose sequential throughput is within ~10 % of the largest tested value, so long as seek latency stays below one frame budget (~16 ms). If Tink's seekable channel does not cache the current segment (§3.4), add the thin segment cache first, then re-measure. Record the chosen value in the frozen spec (§6) and in the golden fixtures.

**Measurement tooling (added):**

1. JVM relative benchmark — `app/src/test/kotlin/.../V4SegmentBenchmark.kt`. Skipped by default; run with:
   `CIPHER_BENCHMARK=1 ./gradlew :app:testFossDebugUnitTest --tests "*V4SegmentBenchmark"`
   It encrypts a 128 MiB synthetic file at 16/64/256 KiB and 1 MiB and prints sequential MiB/s (median of 3), mean and p95 seek latency over 50 random offsets, and tag overhead. This is a same-machine relative comparison, not a device number.
2. Device read-pattern probe — `MediaReadPatternProbe`, wired into `EncryptedMediaDataSource`. It is **always on in debug builds** (no SELinux-dependent `setprop` needed) and off in release unless `cipher.measure_reads=1`. Usage: install a debug APK from the current source → play/scrub an encrypted video → `adb logcat -s CipherReadProbe`. It reports open/seek counts, bytes transferred and a histogram of Media3's requested read sizes, which is the missing third metric. It collects no content and performs no cryptography.

**JVM measurement result (128 MiB synthetic file, same machine, median of 3):**

| Segment | Tag overhead | Sequential MiB/s | Seek avg ms | Seek p95 ms |
|---|---|---|---|---|
| 16 KiB | 0.098 % | 343.2 | 0.093 | 0.131 |
| 64 KiB | 0.024 % | 507.0 | 0.121 | 0.213 |
| **256 KiB** | 0.006 % | **564.2** | **0.388** | **0.500** |
| 1 MiB | 0.0015 % | 610.5 | 1.502 | 1.954 |

Decision rule applied: best sequential = 610.5 MiB/s; 90 % = 549.5. 16 KiB and 64 KiB fall below it; **256 KiB (564.2) is the smallest segment within 10 %**, and its seek p95 (0.5 ms) is far below the 16 ms frame budget. **Writer default set to 256 KiB.** Readers are not locked to it: `segmentSize` is carried in `EncryptedFileMetadata` from the header and all four values are accepted, so files written with a different segment size remain readable.

**Device probe result (collected by tester on 2026-11-17, v3 video):** `opens=7..10`, `seeks=6..9`, `reads=45k..61k`, `bytes=17.9..24.2 MB` → **~400 bytes per read on average**, ~98 % of reads below 64 KiB, only one seek per open plus a few scrubs. Media3 therefore performs small but **mostly contiguous** reads. This means segment size governs the latency spike at segment boundaries rather than average throughput: a 1 MiB segment would produce a single ~1 MB decrypt spike per boundary, while 256 KiB produces smaller, more frequent spikes. With smoothness already reported as a concern, **256 KiB is confirmed and 1 MiB is explicitly rejected**. The user chose to skip the debug-vs-release smoothness comparison; the stutter report remains recorded in §8 as an unattributed risk (the tested video was v3/CTR, so the Tink read path was not involved).

**Gate status:** closed at 256 KiB. Revisit only if a v4 file shows a reproducible smoothness regression that is demonstrably segment-boundary related.

### 11.2 Pin the exact Tink version (resolved in Stage 0)

Wire compatibility is guaranteed by Tink's published format, but **API availability is not** — verify both sides against the same pinned version before writing code:

- **Pinned (Stage 0, 2026-11-16):** Android `com.google.crypto.tink:tink-android:1.24.0` in `gradle/libs.versions.toml`; Python `tink>=1.16.1,<2` in `tools/fossify-cipher-cli/requirements.txt`. Tink Java and Python use independent release trains, so wire parity is verified by golden fixtures rather than by matching version numbers.
- Verified in 1.24.0: `AesGcmHkdfStreaming(ikm, "HmacSha256", 32, segmentSize, 0)` subtle constructor, `StreamingAead.newSeekableDecryptingChannel(...)`, and `expectedCiphertextSize(...)` all exist. No Conscrypt needed (subtle AES-GCM path).
- Java API checklist: `StreamingAead.newSeekableDecryptingChannel(...)`, `AesGcmHkdfStreamingKey`/`Parameters` (key size 32, derived 32, HKDF-SHA256, segment size, SHA-256), `Aead` (AES-256-GCM), RAW output prefix.
- Python API checklist: `tink.streaming_aead` key construction with identical parameters and RAW prefix; `tink.aead` for metadata.
- Confirm **no Conscrypt dependency** is introduced on Android; if AES-GCM ever needs replacing, verify `XChaCha20-Poly1305` exists in both languages.
- Commit a **golden v4 fixture** — one generated container baked into the repo (fixed KEK, 100-byte known plaintext, with the randomized salt/nonce/`fileId`/DEK captured in the bytes). Because Tink randomizes salt and nonce prefix, the **same inputs do not produce the same ciphertext**; parity is tested by requiring both Java and Python to *decrypt this fixture* to the same plaintext, not by reproducing bytes. Any parameter drift (segment size, AAD, header layout) breaks the test. Implemented in `V4ContainerReadTest.decryptsGoldenV4Fixture`.

### 11.3 VMK rotation trigger policy (rare operation)

VMK rotation renews the keyring and the biometric blob; it does **not** re-encrypt media, so it is cheap in I/O but requires a biometric prompt and a new password on every device sharing the vault.

Proposed policy:

- **Manual-only, from Settings.** No automatic trigger, no timer.
- Triggers to document for the user: suspected VMK compromise (device left unlocked/unattended in an untrusted place, vault file exposed, password suspected leaked), or offboarding a person who knew the password.
- Effect: new random VMK; keyring re-wrapped under it; Keystore/biometric blob renewed; PDK re-derived from the new password. KEKs and all file DEKs stay the same — payload and headers are untouched.
- Cost to communicate in the UI: "you will need to re-enter the password on every device that uses this vault; this does not re-encrypt your media."
- It is deliberately **separate** from password change (which only re-wraps the VMK under a new PDK) and from KEK rotation (which only re-wraps DEKs in headers).

---

## 12. File-level impact (for implementation planning)

- `gradle/libs.versions.toml`, `app/build.gradle.kts` — Tink dependency; no Conscrypt.
- `helpers/vault/*` — version 4, `fileId`/`keyId` offsets, key derivation, v4 writer, frozen spec, seekable-channel adapter.
- `CryptoManager*.kt` — v4 read/write, rename, integrity, PDK/VMK/keyring `.vault`, `FVSTR2:` records with context AAD.
- **New** `VaultMigrationService` (foreground service + notification channel) and resumable manifest handling.
- **New** `vault_corruption_reports` Room table + migration; yellow `!` overlays in `MediaAdapter`/`DirectoryAdapter` and the fullscreen viewer; one-time toast wiring; "Dosyanın şifresini çöz" action + corruption dialog with exact TR/EN text and Tamam-right layout (`allowUnverified`); strings for the explanation (using "Encrypted Vault / Şifreli Kasa", no "recovered" wording).
- **New** entry warning dialog and detailed preflight/consent dialog; `activity_settings.xml`, `SettingsActivity`, `Config` (`vaultUpgradeWarningAlwaysShow`, `vaultUpgradePreflightAcknowledged`), `strings.xml` (TR/EN + all locales that carry the cipher prompt block).
- CLI `cipher_vault.py` — v4 output, `.vault`-based format detection, mismatch note, preserved `.pending_sync`; long-lived v3 path.
- Tests: extend fixture/compatibility/rotation tests; add warning/settings, preflight, rebuild/resume, background-key and CLI-mismatch tests.
- Docs: `DOCS.md` §2/§2.2/§2.3/§5/§7, `FORK_CHANGELOG.md`, README, CLI README — including the 1.20 incompatibility statement.

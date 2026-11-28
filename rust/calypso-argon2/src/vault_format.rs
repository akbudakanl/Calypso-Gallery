use zeroize::Zeroizing;

use crate::error::VaultError;

pub(crate) const VAULT_HEADER_MAGIC: &[u8; 5] = b"FGVLT";
pub(crate) const VAULT_MAGIC: &[u8; 8] = b"FGVLTENC";
pub(crate) const VAULT_FORMAT_VERSION: u8 = 1;
pub(crate) const KDF_TYPE_ARGON2ID: u8 = 1;

pub(crate) const GCM_IV_LEN: usize = 12;
pub(crate) const GCM_TAG_LEN: usize = 16;
pub(crate) const KEY_LEN: usize = 32;
pub(crate) const FILE_ID_LEN: usize = 16;
pub(crate) const KEYRING_MIN_LEN: usize = 34;

pub(crate) const HEADER_MIN_MEMORY_KB: u32 = 8_192;
pub(crate) const HEADER_MAX_MEMORY_KB: u32 = 262_144;
pub(crate) const HEADER_MAX_ITERATIONS: u32 = 256;
pub(crate) const HEADER_MAX_PARALLELISM: u32 = 8;

const MAX_SALT_LEN: usize = 256;
const MAX_WRAPPED_LEN: usize = 65_536;
const MAX_KEYRING_LEN: usize = 4_096;

pub(crate) const ALGORITHM_PARAMS: u16 = 0x0001;
pub(crate) const SUPPORTED_SEGMENT_SIZES: [u32; 4] = [16 * 1024, 64 * 1024, 256 * 1024, 1024 * 1024];
pub(crate) const HEADER_PREFIX_PLAIN_LEN: usize = 2 + 4 + 8 + KEY_LEN; // 46 bytes
pub(crate) const MAX_CIPHERTEXT_LEN: u64 = (i32::MAX as u64) * 8;

pub(crate) struct ParsedVaultHeader {
    pub(crate) iterations: u32,
    pub(crate) memory_kb: u32,
    pub(crate) parallelism: u32,
    #[allow(dead_code)]
    pub(crate) protection_type: u8,
    pub(crate) salt: Vec<u8>,
    pub(crate) iv_pdk: [u8; GCM_IV_LEN],
    pub(crate) wrapped_vmk: Vec<u8>,
    pub(crate) iv_vmk: [u8; GCM_IV_LEN],
    pub(crate) wrapped_keyring: Vec<u8>,
}

struct Cursor<'a> {
    buf: &'a [u8],
    pos: usize,
}

impl<'a> Cursor<'a> {
    fn new(buf: &'a [u8]) -> Self {
        Self { buf, pos: 0 }
    }

    fn read_bytes(&mut self, len: usize) -> Result<&'a [u8], VaultError> {
        let end = self
            .pos
            .checked_add(len)
            .ok_or(VaultError::FormatInvalid("offset overflow"))?;
        if end > self.buf.len() {
            return Err(VaultError::FormatInvalid("unexpected EOF"));
        }
        let slice = &self.buf[self.pos..end];
        self.pos = end;
        Ok(slice)
    }

    fn read_u8(&mut self) -> Result<u8, VaultError> {
        Ok(self.read_bytes(1)?[0])
    }

    fn read_i32_be(&mut self) -> Result<i32, VaultError> {
        let b = self.read_bytes(4)?;
        Ok(i32::from_be_bytes([b[0], b[1], b[2], b[3]]))
    }

    fn read_len_prefixed(&mut self, max_len: usize) -> Result<Vec<u8>, VaultError> {
        let len = self.read_i32_be()?;
        if len < 1 || (len as usize) > max_len {
            return Err(VaultError::FormatInvalid("invalid field length"));
        }
        Ok(self.read_bytes(len as usize)?.to_vec())
    }
}

pub(crate) fn parse_vault_header(bytes: &[u8]) -> Result<ParsedVaultHeader, VaultError> {
    let mut c = Cursor::new(bytes);
    if c.read_bytes(VAULT_HEADER_MAGIC.len())? != VAULT_HEADER_MAGIC {
        return Err(VaultError::FormatInvalid("invalid .vault magic"));
    }
    let version = c.read_u8()?;
    let kdf_type = c.read_u8()?;
    if version != VAULT_FORMAT_VERSION || kdf_type != KDF_TYPE_ARGON2ID {
        return Err(VaultError::FormatInvalid("unsupported .vault version or KDF"));
    }

    let iterations = u32::try_from(c.read_i32_be()?)
        .map_err(|_| VaultError::FormatInvalid("invalid iterations"))?;
    let memory_kb = u32::try_from(c.read_i32_be()?)
        .map_err(|_| VaultError::FormatInvalid("invalid memory_kb"))?;
    let parallelism = u32::try_from(c.read_i32_be()?)
        .map_err(|_| VaultError::FormatInvalid("invalid parallelism"))?;
    if !(HEADER_MIN_MEMORY_KB..=HEADER_MAX_MEMORY_KB).contains(&memory_kb)
        || !(1..=HEADER_MAX_ITERATIONS).contains(&iterations)
        || !(1..=HEADER_MAX_PARALLELISM).contains(&parallelism)
    {
        return Err(VaultError::FormatInvalid("KDF params out of bounds"));
    }

    let protection_type = c.read_u8()?;
    let salt = c.read_len_prefixed(MAX_SALT_LEN)?;
    let mut iv_pdk = [0u8; GCM_IV_LEN];
    iv_pdk.copy_from_slice(c.read_bytes(GCM_IV_LEN)?);
    let wrapped_vmk = c.read_len_prefixed(MAX_WRAPPED_LEN)?;
    let mut iv_vmk = [0u8; GCM_IV_LEN];
    iv_vmk.copy_from_slice(c.read_bytes(GCM_IV_LEN)?);
    let wrapped_keyring = c.read_len_prefixed(MAX_KEYRING_LEN)?;

    Ok(ParsedVaultHeader {
        iterations,
        memory_kb,
        parallelism,
        protection_type,
        salt,
        iv_pdk,
        wrapped_vmk,
        iv_vmk,
        wrapped_keyring,
    })
}

pub(crate) fn serialize_vault_header(
    iterations: u32,
    memory_kb: u32,
    parallelism: u32,
    protection_type: u8,
    salt: &[u8],
    iv_pdk: &[u8; GCM_IV_LEN],
    wrapped_vmk: &[u8],
    iv_vmk: &[u8; GCM_IV_LEN],
    wrapped_keyring: &[u8],
) -> Vec<u8> {
    let total = 5 + 1 + 1 + 12 + 1 + 4 + salt.len() + 12 + 4 + wrapped_vmk.len() + 12 + 4 + wrapped_keyring.len();
    let mut out = Vec::with_capacity(total);
    out.extend_from_slice(VAULT_HEADER_MAGIC);
    out.push(VAULT_FORMAT_VERSION);
    out.push(KDF_TYPE_ARGON2ID);
    out.extend_from_slice(&(iterations as i32).to_be_bytes());
    out.extend_from_slice(&(memory_kb as i32).to_be_bytes());
    out.extend_from_slice(&(parallelism as i32).to_be_bytes());
    out.push(protection_type);
    out.extend_from_slice(&(salt.len() as i32).to_be_bytes());
    out.extend_from_slice(salt);
    out.extend_from_slice(iv_pdk);
    out.extend_from_slice(&(wrapped_vmk.len() as i32).to_be_bytes());
    out.extend_from_slice(wrapped_vmk);
    out.extend_from_slice(iv_vmk);
    out.extend_from_slice(&(wrapped_keyring.len() as i32).to_be_bytes());
    out.extend_from_slice(wrapped_keyring);
    out
}

pub(crate) fn pack_keyring(key_id: u16, kek: &[u8; KEY_LEN]) -> Zeroizing<[u8; KEYRING_MIN_LEN]> {
    let mut out = Zeroizing::new([0u8; KEYRING_MIN_LEN]);
    out[0..2].copy_from_slice(&key_id.to_be_bytes());
    out[2..KEYRING_MIN_LEN].copy_from_slice(kek);
    out
}

pub(crate) fn unpack_keyring(bytes: &[u8]) -> Option<(u16, Zeroizing<[u8; KEY_LEN]>)> {
    if bytes.len() < KEYRING_MIN_LEN {
        return None;
    }
    let key_id = u16::from_be_bytes([bytes[0], bytes[1]]);
    let mut kek = Zeroizing::new([0u8; KEY_LEN]);
    kek.copy_from_slice(&bytes[2..KEYRING_MIN_LEN]);
    Some((key_id, kek))
}

pub(crate) fn is_supported_segment_size(size: u32) -> bool {
    SUPPORTED_SEGMENT_SIZES.contains(&size)
}

pub(crate) fn build_file_header_aad(
    version: u8,
    key_id: u16,
    file_id: &[u8; FILE_ID_LEN],
    enc_header_len: u32,
) -> [u8; 31] {
    let mut aad = [0u8; 31];
    aad[0..8].copy_from_slice(VAULT_MAGIC);
    aad[8] = version;
    aad[9..11].copy_from_slice(&key_id.to_be_bytes());
    aad[11..27].copy_from_slice(file_id);
    aad[27..31].copy_from_slice(&enc_header_len.to_be_bytes());
    aad
}

pub(crate) fn pack_file_header_plain(
    segment_size: u32,
    ciphertext_len: u64,
    dek: &[u8; KEY_LEN],
    meta_tail: &[u8],
) -> Zeroizing<Vec<u8>> {
    let mut plain = Zeroizing::new(Vec::with_capacity(HEADER_PREFIX_PLAIN_LEN + meta_tail.len()));
    plain.extend_from_slice(&ALGORITHM_PARAMS.to_be_bytes());
    plain.extend_from_slice(&segment_size.to_be_bytes());
    plain.extend_from_slice(&ciphertext_len.to_be_bytes());
    plain.extend_from_slice(dek);
    plain.extend_from_slice(meta_tail);
    plain
}

pub(crate) fn unpack_file_header_plain(
    plain: &[u8],
) -> Result<(u32, u64, Zeroizing<[u8; KEY_LEN]>, &[u8]), VaultError> {
    if plain.len() < HEADER_PREFIX_PLAIN_LEN {
        return Err(VaultError::FormatInvalid("file header too short"));
    }
    let algo_params = u16::from_be_bytes([plain[0], plain[1]]);
    if algo_params != ALGORITHM_PARAMS {
        return Err(VaultError::FormatInvalid("unsupported algorithm params"));
    }
    let segment_size = u32::from_be_bytes([plain[2], plain[3], plain[4], plain[5]]);
    if !is_supported_segment_size(segment_size) {
        return Err(VaultError::FormatInvalid("unsupported segment size"));
    }
    let ciphertext_len = u64::from_be_bytes([
        plain[6], plain[7], plain[8], plain[9], plain[10], plain[11], plain[12], plain[13],
    ]);
    if ciphertext_len == 0 || ciphertext_len > MAX_CIPHERTEXT_LEN {
        return Err(VaultError::FormatInvalid("invalid ciphertext length"));
    }
    let mut dek = Zeroizing::new([0u8; KEY_LEN]);
    dek.copy_from_slice(&plain[14..46]);
    Ok((segment_size, ciphertext_len, dek, &plain[46..]))
}

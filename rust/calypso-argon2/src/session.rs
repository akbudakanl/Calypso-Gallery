use std::sync::RwLock;
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

use crate::argon2_kdf::derive_argon2id_raw;
use crate::error::VaultError;
use crate::primitives::{
    aes256_gcm_decrypt, aes256_gcm_encrypt, compute_kek_fingerprint, derive_file_header_key,
    derive_metadata_key, fill_random,
};
use crate::vault_format::{
    build_file_header_aad, is_supported_segment_size, pack_file_header_plain, pack_keyring,
    parse_vault_header, serialize_vault_header, unpack_file_header_plain, unpack_keyring,
    ALGORITHM_PARAMS, FILE_ID_LEN, GCM_IV_LEN, GCM_TAG_LEN, KEY_LEN, VAULT_FORMAT_VERSION,
};

const SALT_LEN: usize = 16;

#[derive(Zeroize, ZeroizeOnDrop)]
struct VaultSession {
    vmk: [u8; KEY_LEN],
    kek: [u8; KEY_LEN],
    active_key_id: u16,
}

static SESSION: RwLock<Option<VaultSession>> = RwLock::new(None);

pub(crate) struct NewFileHeaderOutput {
    pub(crate) file_id: [u8; FILE_ID_LEN],
    pub(crate) header_iv: [u8; GCM_IV_LEN],
    pub(crate) enc_header: Vec<u8>,
    pub(crate) dek: Zeroizing<[u8; KEY_LEN]>,
}

fn read_session_keys<T>(f: impl FnOnce(&VaultSession) -> Result<T, VaultError>) -> Result<T, VaultError> {
    let guard = SESSION.read().unwrap_or_else(|e| e.into_inner());
    let session = guard.as_ref().ok_or(VaultError::Locked)?;
    f(session)
}

pub(crate) fn is_unlocked() -> bool {
    let guard = SESSION.read().unwrap_or_else(|e| e.into_inner());
    guard.is_some()
}

pub(crate) fn lock_session() {
    let mut guard = SESSION.write().unwrap_or_else(|e| e.into_inner());
    guard.take();
}

pub(crate) fn install_session_keys(vmk: [u8; KEY_LEN], kek: [u8; KEY_LEN], active_key_id: u16) {
    let mut guard = SESSION.write().unwrap_or_else(|e| e.into_inner());
    guard.take();
    *guard = Some(VaultSession {
        vmk,
        kek,
        active_key_id,
    });
}

fn unwrap_ephemeral_from_password(
    header_bytes: &[u8],
    password: &[u8],
) -> Result<(Zeroizing<[u8; KEY_LEN]>, Zeroizing<[u8; KEY_LEN]>, u16), VaultError> {
    let parsed = parse_vault_header(header_bytes)?;
    let pdk_vec = derive_argon2id_raw(
        password,
        &parsed.salt,
        parsed.memory_kb,
        parsed.iterations,
        parsed.parallelism,
        KEY_LEN,
    )?;
    let mut pdk = Zeroizing::new([0u8; KEY_LEN]);
    pdk.copy_from_slice(&pdk_vec[..KEY_LEN]);

    let vmk_bundle = aes256_gcm_decrypt(&pdk, &parsed.iv_pdk, b"", &parsed.wrapped_vmk)?;
    if vmk_bundle.len() < KEY_LEN {
        return Err(VaultError::AuthFailed);
    }
    let mut vmk = Zeroizing::new([0u8; KEY_LEN]);
    vmk.copy_from_slice(&vmk_bundle[..KEY_LEN]);

    let keyring_plain = aes256_gcm_decrypt(&vmk, &parsed.iv_vmk, b"", &parsed.wrapped_keyring)?;
    let (key_id, kek) = unpack_keyring(&keyring_plain).ok_or(VaultError::AuthFailed)?;
    Ok((vmk, kek, key_id))
}

fn wrap_vault_header_with_keys(
    password: &[u8],
    vmk: &[u8; KEY_LEN],
    kek: &[u8; KEY_LEN],
    key_id: u16,
    memory_kb: u32,
    iterations: u32,
    parallelism: u32,
    protection_type: u8,
) -> Result<Vec<u8>, VaultError> {
    let mut salt = [0u8; SALT_LEN];
    fill_random(&mut salt)?;
    let pdk_vec = derive_argon2id_raw(password, &salt, memory_kb, iterations, parallelism, KEY_LEN)?;
    let mut pdk = Zeroizing::new([0u8; KEY_LEN]);
    pdk.copy_from_slice(&pdk_vec[..KEY_LEN]);

    let mut iv_pdk = [0u8; GCM_IV_LEN];
    fill_random(&mut iv_pdk)?;
    let wrapped_vmk = aes256_gcm_encrypt(&pdk, &iv_pdk, b"", vmk)?;

    let mut iv_vmk = [0u8; GCM_IV_LEN];
    fill_random(&mut iv_vmk)?;
    let packed_keyring = pack_keyring(key_id, kek);
    let wrapped_keyring = aes256_gcm_encrypt(vmk, &iv_vmk, b"", packed_keyring.as_ref())?;

    Ok(serialize_vault_header(
        iterations,
        memory_kb,
        parallelism,
        protection_type,
        &salt,
        &iv_pdk,
        &wrapped_vmk,
        &iv_vmk,
        &wrapped_keyring,
    ))
}

pub(crate) fn create_or_update_vault_header(
    password: &[u8],
    memory_kb: u32,
    iterations: u32,
    parallelism: u32,
    protection_type: u8,
    is_new_vault: bool,
) -> Result<Vec<u8>, VaultError> {
    let (vmk, kek, key_id) = if is_new_vault {
        let mut new_vmk = Zeroizing::new([0u8; KEY_LEN]);
        let mut new_kek = Zeroizing::new([0u8; KEY_LEN]);
        fill_random(new_vmk.as_mut())?;
        fill_random(new_kek.as_mut())?;
        (new_vmk, new_kek, 1u16)
    } else {
        read_session_keys(|s| {
            Ok((
                Zeroizing::new(s.vmk),
                Zeroizing::new(s.kek),
                s.active_key_id,
            ))
        })?
    };

    let header = wrap_vault_header_with_keys(
        password,
        &vmk,
        &kek,
        key_id,
        memory_kb,
        iterations,
        parallelism,
        protection_type,
    )?;
    install_session_keys(*vmk, *kek, key_id);
    Ok(header)
}

pub(crate) fn unlock_with_password(header_bytes: &[u8], password: &[u8]) -> Result<bool, VaultError> {
    match unwrap_ephemeral_from_password(header_bytes, password) {
        Ok((vmk, kek, key_id)) => {
            install_session_keys(*vmk, *kek, key_id);
            Ok(true)
        }
        Err(VaultError::AuthFailed) | Err(VaultError::FormatInvalid(_)) => Ok(false),
        Err(e) => Err(e),
    }
}

pub(crate) fn verify_secret_ephemeral(header_bytes: &[u8], password: &[u8]) -> Result<bool, VaultError> {
    match unwrap_ephemeral_from_password(header_bytes, password) {
        Ok((_vmk, _kek, _key_id)) => Ok(true),
        Err(VaultError::AuthFailed) | Err(VaultError::FormatInvalid(_)) => Ok(false),
        Err(e) => Err(e),
    }
}

pub(crate) fn rewrap_vault_header_ephemeral(
    header_bytes: &[u8],
    password: &[u8],
    new_memory_kb: u32,
    new_iterations: u32,
    new_parallelism: u32,
    new_protection_type: u8,
) -> Result<Option<Vec<u8>>, VaultError> {
    let (vmk, kek, key_id) = match unwrap_ephemeral_from_password(header_bytes, password) {
        Ok(keys) => keys,
        Err(VaultError::AuthFailed) | Err(VaultError::FormatInvalid(_)) => return Ok(None),
        Err(e) => return Err(e),
    };
    let wrapped = wrap_vault_header_with_keys(
        password,
        &vmk,
        &kek,
        key_id,
        new_memory_kb,
        new_iterations,
        new_parallelism,
        new_protection_type,
    )?;
    Ok(Some(wrapped))
}

pub(crate) fn export_vmk_for_biometric_wrap() -> Result<Option<Zeroizing<[u8; KEY_LEN]>>, VaultError> {
    let guard = SESSION.read().unwrap_or_else(|e| e.into_inner());
    Ok(guard.as_ref().map(|s| Zeroizing::new(s.vmk)))
}

pub(crate) fn unlock_with_vmk(header_bytes: &[u8], vmk_slice: &[u8]) -> Result<bool, VaultError> {
    if vmk_slice.len() != KEY_LEN {
        return Ok(false);
    }
    let parsed = match parse_vault_header(header_bytes) {
        Ok(p) => p,
        Err(_) => return Ok(false),
    };
    let mut vmk = Zeroizing::new([0u8; KEY_LEN]);
    vmk.copy_from_slice(vmk_slice);
    let keyring_plain = match aes256_gcm_decrypt(&vmk, &parsed.iv_vmk, b"", &parsed.wrapped_keyring) {
        Ok(k) => k,
        Err(VaultError::AuthFailed) => return Ok(false),
        Err(e) => return Err(e),
    };
    let Some((key_id, kek)) = unpack_keyring(&keyring_plain) else {
        return Ok(false);
    };
    install_session_keys(*vmk, *kek, key_id);
    Ok(true)
}

fn encrypt_header_bytes(
    kek: &[u8; KEY_LEN],
    version: u8,
    key_id: u16,
    file_id: &[u8; FILE_ID_LEN],
    header_iv: &[u8; GCM_IV_LEN],
    header_plain: &[u8],
) -> Result<Vec<u8>, VaultError> {
    let enc_len = u32::try_from(header_plain.len() + GCM_TAG_LEN)
        .map_err(|_| VaultError::InvalidParam("header too large"))?;
    let header_key = derive_file_header_key(kek, file_id);
    let aad = build_file_header_aad(version, key_id, file_id, enc_len);
    aes256_gcm_encrypt(&header_key, header_iv, &aad, header_plain)
}

fn decrypt_header_bytes(
    kek: &[u8; KEY_LEN],
    version: u8,
    key_id: u16,
    file_id: &[u8; FILE_ID_LEN],
    header_iv: &[u8; GCM_IV_LEN],
    enc_header: &[u8],
) -> Result<Zeroizing<Vec<u8>>, VaultError> {
    if version != VAULT_FORMAT_VERSION || file_id.iter().all(|&b| b == 0) {
        return Err(VaultError::FormatInvalid("invalid container header version or fileId"));
    }
    let enc_len = u32::try_from(enc_header.len())
        .map_err(|_| VaultError::FormatInvalid("header length overflow"))?;
    let header_key = derive_file_header_key(kek, file_id);
    let aad = build_file_header_aad(version, key_id, file_id, enc_len);
    aes256_gcm_decrypt(&header_key, header_iv, &aad, enc_header)
}

pub(crate) fn encrypt_new_file_header(
    version: u8,
    key_id: u16,
    segment_size: u32,
    ciphertext_len: u64,
    meta_tail: &[u8],
) -> Result<NewFileHeaderOutput, VaultError> {
    if !is_supported_segment_size(segment_size) {
        return Err(VaultError::InvalidParam("unsupported segment size"));
    }
    read_session_keys(|s| {
        let mut file_id = [0u8; FILE_ID_LEN];
        fill_random(&mut file_id)?;
        let mut dek = Zeroizing::new([0u8; KEY_LEN]);
        fill_random(dek.as_mut())?;
        let mut header_iv = [0u8; GCM_IV_LEN];
        fill_random(&mut header_iv)?;

        let plain = pack_file_header_plain(segment_size, ciphertext_len, &dek, meta_tail);
        let enc_header = encrypt_header_bytes(&s.kek, version, key_id, &file_id, &header_iv, &plain)?;
        Ok(NewFileHeaderOutput {
            file_id,
            header_iv,
            enc_header,
            dek,
        })
    })
}

pub(crate) fn encrypt_file_header_with_dek(
    version: u8,
    key_id: u16,
    file_id: &[u8; FILE_ID_LEN],
    segment_size: u32,
    ciphertext_len: u64,
    dek: &[u8; KEY_LEN],
    meta_tail: &[u8],
) -> Result<([u8; GCM_IV_LEN], Vec<u8>), VaultError> {
    if !is_supported_segment_size(segment_size) {
        return Err(VaultError::InvalidParam("unsupported segment size"));
    }
    read_session_keys(|s| {
        let mut header_iv = [0u8; GCM_IV_LEN];
        fill_random(&mut header_iv)?;
        let plain = pack_file_header_plain(segment_size, ciphertext_len, dek, meta_tail);
        let enc_header = encrypt_header_bytes(&s.kek, version, key_id, file_id, &header_iv, &plain)?;
        Ok((header_iv, enc_header))
    })
}

/// Decrypts `FGVLTENC` header and **strips/zeroes the 32-byte DEK** inside Rust.
/// Returns `algorithmParams(2) | segmentSize(4) | ciphertextLen(8) | metaTail`.
pub(crate) fn decrypt_file_header_metadata(
    version: u8,
    key_id: u16,
    file_id: &[u8; FILE_ID_LEN],
    header_iv: &[u8; GCM_IV_LEN],
    enc_header: &[u8],
) -> Result<Option<Vec<u8>>, VaultError> {
    let guard = SESSION.read().unwrap_or_else(|e| e.into_inner());
    let Some(s) = guard.as_ref() else {
        return Ok(None);
    };
    let plain = match decrypt_header_bytes(&s.kek, version, key_id, file_id, header_iv, enc_header) {
        Ok(p) => p,
        Err(VaultError::AuthFailed) | Err(VaultError::FormatInvalid(_)) => return Ok(None),
        Err(e) => return Err(e),
    };
    let Ok((segment_size, ciphertext_len, _dek, meta_tail)) = unpack_file_header_plain(&plain) else {
        return Ok(None);
    };
    let mut out = Vec::with_capacity(2 + 4 + 8 + meta_tail.len());
    out.extend_from_slice(&ALGORITHM_PARAMS.to_be_bytes());
    out.extend_from_slice(&segment_size.to_be_bytes());
    out.extend_from_slice(&ciphertext_len.to_be_bytes());
    out.extend_from_slice(meta_tail);
    Ok(Some(out))
}

pub(crate) fn unwrap_file_dek(
    version: u8,
    key_id: u16,
    file_id: &[u8; FILE_ID_LEN],
    header_iv: &[u8; GCM_IV_LEN],
    enc_header: &[u8],
) -> Result<Option<Zeroizing<[u8; KEY_LEN]>>, VaultError> {
    let guard = SESSION.read().unwrap_or_else(|e| e.into_inner());
    let Some(s) = guard.as_ref() else {
        return Ok(None);
    };
    let plain = match decrypt_header_bytes(&s.kek, version, key_id, file_id, header_iv, enc_header) {
        Ok(p) => p,
        Err(VaultError::AuthFailed) | Err(VaultError::FormatInvalid(_)) => return Ok(None),
        Err(e) => return Err(e),
    };
    let Ok((_seg, _ct, dek, _tail)) = unpack_file_header_plain(&plain) else {
        return Ok(None);
    };
    Ok(Some(dek))
}

pub(crate) fn reencrypt_file_header(
    version: u8,
    key_id: u16,
    file_id: &[u8; FILE_ID_LEN],
    old_header_iv: &[u8; GCM_IV_LEN],
    old_enc_header: &[u8],
    new_meta_tail: &[u8],
) -> Result<Option<([u8; GCM_IV_LEN], Vec<u8>)>, VaultError> {
    let guard = SESSION.read().unwrap_or_else(|e| e.into_inner());
    let Some(s) = guard.as_ref() else {
        return Ok(None);
    };
    let plain = match decrypt_header_bytes(&s.kek, version, key_id, file_id, old_header_iv, old_enc_header) {
        Ok(p) => p,
        Err(VaultError::AuthFailed) | Err(VaultError::FormatInvalid(_)) => return Ok(None),
        Err(e) => return Err(e),
    };
    let Ok((segment_size, ciphertext_len, dek, _old_tail)) = unpack_file_header_plain(&plain) else {
        return Ok(None);
    };
    let new_plain = pack_file_header_plain(segment_size, ciphertext_len, &dek, new_meta_tail);
    let mut new_iv = [0u8; GCM_IV_LEN];
    fill_random(&mut new_iv)?;
    let new_enc = encrypt_header_bytes(&s.kek, version, key_id, file_id, &new_iv, &new_plain)?;
    Ok(Some((new_iv, new_enc)))
}

pub(crate) fn encrypt_vault_string(purpose: &str, plaintext: &[u8]) -> Result<(u16, [u8; GCM_IV_LEN], Vec<u8>), VaultError> {
    read_session_keys(|s| {
        let (ctx, key) = derive_metadata_key(&s.kek, purpose);
        let mut iv = [0u8; GCM_IV_LEN];
        fill_random(&mut iv)?;
        let ct = aes256_gcm_encrypt(&key, &iv, ctx.as_bytes(), plaintext)?;
        Ok((s.active_key_id, iv, ct))
    })
}

pub(crate) fn decrypt_vault_string(
    purpose: &str,
    iv: &[u8; GCM_IV_LEN],
    ciphertext_and_tag: &[u8],
) -> Result<Option<Vec<u8>>, VaultError> {
    let guard = SESSION.read().unwrap_or_else(|e| e.into_inner());
    let Some(s) = guard.as_ref() else {
        return Ok(None);
    };
    let (ctx, key) = derive_metadata_key(&s.kek, purpose);
    match aes256_gcm_decrypt(&key, iv, ctx.as_bytes(), ciphertext_and_tag) {
        Ok(plain) => Ok(Some(plain.to_vec())),
        Err(VaultError::AuthFailed) => Ok(None),
        Err(e) => Err(e),
    }
}

pub(crate) fn encrypt_folder_meta(name_utf8: &[u8]) -> Result<([u8; GCM_IV_LEN], Vec<u8>), VaultError> {
    read_session_keys(|s| {
        let mut iv = [0u8; GCM_IV_LEN];
        fill_random(&mut iv)?;
        let ct = aes256_gcm_encrypt(&s.vmk, &iv, b"", name_utf8)?;
        Ok((iv, ct))
    })
}

pub(crate) fn decrypt_folder_meta(
    iv: &[u8; GCM_IV_LEN],
    ciphertext_and_tag: &[u8],
) -> Result<Option<Vec<u8>>, VaultError> {
    let guard = SESSION.read().unwrap_or_else(|e| e.into_inner());
    let Some(s) = guard.as_ref() else {
        return Ok(None);
    };
    match aes256_gcm_decrypt(&s.vmk, iv, b"", ciphertext_and_tag) {
        Ok(plain) => Ok(Some(plain.to_vec())),
        Err(VaultError::AuthFailed) => Ok(None),
        Err(e) => Err(e),
    }
}

pub(crate) fn kek_fingerprint(info: &[u8]) -> Result<Option<[u8; 8]>, VaultError> {
    let guard = SESSION.read().unwrap_or_else(|e| e.into_inner());
    let Some(s) = guard.as_ref() else {
        return Ok(None);
    };
    Ok(Some(compute_kek_fingerprint(&s.kek, info)?))
}

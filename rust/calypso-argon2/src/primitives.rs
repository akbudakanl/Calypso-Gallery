use aes_gcm::{
    aead::{Aead, KeyInit, Payload},
    Aes256Gcm, Nonce,
};
use hkdf::Hkdf;
use hmac::{Hmac, Mac};
use sha2::Sha256;
use zeroize::Zeroizing;

use crate::error::VaultError;

const DOMAIN_HEADER_KEY: &str = "CalypsoGallery:Header:AES256GCM";
const DOMAIN_METADATA_KEY: &str = "CalypsoGallery:Metadata:AESGCM";

type HmacSha256 = Hmac<Sha256>;

/// Unseeded OS CSPRNG fill (`getrandom`). Never falls back to a seeded PRNG.
pub(crate) fn fill_random(buf: &mut [u8]) -> Result<(), VaultError> {
    getrandom::getrandom(buf).map_err(|_| VaultError::RngFailed)
}

pub(crate) fn aes256_gcm_encrypt(
    key: &[u8; 32],
    iv: &[u8; 12],
    aad: &[u8],
    plaintext: &[u8],
) -> Result<Vec<u8>, VaultError> {
    let cipher = Aes256Gcm::new(key.into());
    let nonce = Nonce::from(*iv);
    cipher
        .encrypt(
            &nonce,
            Payload {
                msg: plaintext,
                aad,
            },
        )
        .map_err(|_| VaultError::AuthFailed)
}

pub(crate) fn aes256_gcm_decrypt(
    key: &[u8; 32],
    iv: &[u8; 12],
    aad: &[u8],
    ciphertext_and_tag: &[u8],
) -> Result<Zeroizing<Vec<u8>>, VaultError> {
    let cipher = Aes256Gcm::new(key.into());
    let nonce = Nonce::from(*iv);
    let plain = cipher
        .decrypt(
            &nonce,
            Payload {
                msg: ciphertext_and_tag,
                aad,
            },
        )
        .map_err(|_| VaultError::AuthFailed)?;
    Ok(Zeroizing::new(plain))
}

fn derive_hkdf_sha256_32(ikm: &[u8; 32], info: &[u8]) -> Zeroizing<[u8; 32]> {
    let hk = Hkdf::<Sha256>::new(Some(&[]), ikm);
    let mut okm = Zeroizing::new([0u8; 32]);
    // 32 bytes is always <= 255 * 32 for SHA-256.
    let _ = hk.expand(info, okm.as_mut());
    okm
}

pub(crate) fn derive_file_header_key(kek: &[u8; 32], file_id: &[u8; 16]) -> Zeroizing<[u8; 32]> {
    let mut info = String::with_capacity(DOMAIN_HEADER_KEY.len() + 1 + 32);
    info.push_str(DOMAIN_HEADER_KEY);
    info.push('|');
    for b in file_id {
        use core::fmt::Write;
        let _ = write!(&mut info, "{:02x}", b);
    }
    derive_hkdf_sha256_32(kek, info.as_bytes())
}

/// Preserves the legacy double-prefixed HKDF info and single-prefixed AAD contract (`DOCS.md` §2.2).
pub(crate) fn derive_metadata_key(kek: &[u8; 32], purpose: &str) -> (String, Zeroizing<[u8; 32]>) {
    let ctx = format!("{DOMAIN_METADATA_KEY}|{purpose}");
    let info = format!("{DOMAIN_METADATA_KEY}|{ctx}");
    let okm = derive_hkdf_sha256_32(kek, info.as_bytes());
    (ctx, okm)
}

pub(crate) fn compute_kek_fingerprint(kek: &[u8; 32], info: &[u8]) -> Result<[u8; 8], VaultError> {
    let mut mac = <HmacSha256 as KeyInit>::new_from_slice(kek)
        .map_err(|_| VaultError::InvalidParam("invalid HMAC key"))?;
    mac.update(info);
    let tag = mac.finalize().into_bytes();
    let mut out = [0u8; 8];
    out.copy_from_slice(&tag[..8]);
    Ok(out)
}

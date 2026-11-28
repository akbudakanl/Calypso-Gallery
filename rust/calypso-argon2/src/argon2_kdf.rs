use argon2::{Algorithm, Argon2, Block, Params, Version};
use zeroize::{Zeroize, Zeroizing};

use crate::error::VaultError;

pub(crate) const MIN_OUTPUT_LEN: usize = 4;
pub(crate) const MAX_OUTPUT_LEN: usize = 1024;
pub(crate) const MIN_MEMORY_KB: u32 = 8;

/// RAII guard that unconditionally wipes all Argon2 working blocks before freeing.
struct ZeroizingBlocks(Vec<Block>);

impl Drop for ZeroizingBlocks {
    fn drop(&mut self) {
        for block in &mut self.0 {
            block.zeroize();
        }
    }
}

/// Pure-Rust RFC 9106 Argon2id v1.3 raw key derivation with fallible allocation.
pub(crate) fn derive_argon2id_raw(
    password: &[u8],
    salt: &[u8],
    memory_kb: u32,
    iterations: u32,
    parallelism: u32,
    output_len: usize,
) -> Result<Zeroizing<Vec<u8>>, VaultError> {
    if !(MIN_OUTPUT_LEN..=MAX_OUTPUT_LEN).contains(&output_len) {
        return Err(VaultError::InvalidParam("output_len must be in 4..=1024"));
    }
    if memory_kb < MIN_MEMORY_KB {
        return Err(VaultError::InvalidParam("memory_kb must be >= 8"));
    }
    if iterations < 1 {
        return Err(VaultError::InvalidParam("iterations must be >= 1"));
    }
    if parallelism < 1 {
        return Err(VaultError::InvalidParam("parallelism must be >= 1"));
    }

    let params = Params::new(memory_kb, iterations, parallelism, Some(output_len))
        .map_err(|_| VaultError::InvalidParam("invalid Argon2 parameters"))?;

    let block_count = params.block_count();
    let total_bytes = block_count
        .checked_mul(core::mem::size_of::<Block>())
        .ok_or(VaultError::OutOfMemory)?;
    if total_bytes > isize::MAX as usize {
        return Err(VaultError::OutOfMemory);
    }

    let mut raw_blocks: Vec<Block> = Vec::new();
    if raw_blocks.try_reserve_exact(block_count).is_err() {
        return Err(VaultError::OutOfMemory);
    }
    raw_blocks.resize(block_count, Block::default());
    let mut blocks = ZeroizingBlocks(raw_blocks);

    let mut out = Zeroizing::new(vec![0u8; output_len]);
    let argon2 = Argon2::new(Algorithm::Argon2id, Version::V0x13, params);
    argon2
        .hash_password_into_with_memory(password, salt, &mut out, &mut blocks.0)
        .map_err(VaultError::Argon2Failed)?;

    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rfc9106_params_argon2id_matches_reference_vector_without_secret_or_ad() {
        let password = [0x01u8; 32];
        let salt = [0x02u8; 16];
        let expected: [u8; 32] = [
            0x03, 0xaa, 0xb9, 0x65, 0xc1, 0x20, 0x01, 0xc9,
            0xd7, 0xd0, 0xd2, 0xde, 0x33, 0x19, 0x2c, 0x04,
            0x94, 0xb6, 0x84, 0xbb, 0x14, 0x81, 0x96, 0xd7,
            0x3c, 0x1d, 0xf1, 0xac, 0xaf, 0x6d, 0x0c, 0x2e,
        ];

        let actual = derive_argon2id_raw(&password, &salt, 32, 3, 4, 32)
            .expect("Argon2id vector derivation should succeed");
        assert_eq!(actual.as_slice(), &expected);
    }

    #[test]
    fn huge_memory_returns_oom_instead_of_aborting() {
        let err = derive_argon2id_raw(b"secret", b"saltsaltsalt1234", u32::MAX, 3, 2, 32)
            .expect_err("impossibly large allocation must return OutOfMemory");
        assert!(matches!(err, VaultError::OutOfMemory));
    }

    #[test]
    fn invalid_parameters_return_invalid_param() {
        assert!(matches!(
            derive_argon2id_raw(b"pw", b"12345678", 0, 3, 2, 32),
            Err(VaultError::InvalidParam(_))
        ));
        assert!(matches!(
            derive_argon2id_raw(b"pw", b"12345678", 64, 0, 2, 32),
            Err(VaultError::InvalidParam(_))
        ));
        assert!(matches!(
            derive_argon2id_raw(b"pw", b"12345678", 64, 3, 0, 32),
            Err(VaultError::InvalidParam(_))
        ));
        assert!(matches!(
            derive_argon2id_raw(b"pw", b"12345678", 64, 3, 2, 0),
            Err(VaultError::InvalidParam(_))
        ));
    }
}

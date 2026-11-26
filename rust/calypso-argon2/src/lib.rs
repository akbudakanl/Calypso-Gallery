use argon2::{Algorithm, Argon2, Block, Params, Version};
use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JByteArray, JClass};
use jni::strings::{JNIStr, JNIString};
use jni::sys::jint;
use jni::{jni_str, Env, EnvUnowned};
use zeroize::{Zeroize, Zeroizing};

const MIN_OUTPUT_LEN: usize = 4;
const MAX_OUTPUT_LEN: usize = 1024;
const MIN_MEMORY_KB: u32 = 8;

#[derive(Debug)]
pub(crate) enum DeriveError {
    InvalidParam(&'static str),
    OutOfMemory,
    Argon2Failed(argon2::Error),
}

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
) -> Result<Zeroizing<Vec<u8>>, DeriveError> {
    if !(MIN_OUTPUT_LEN..=MAX_OUTPUT_LEN).contains(&output_len) {
        return Err(DeriveError::InvalidParam("output_len must be in 4..=1024"));
    }
    if memory_kb < MIN_MEMORY_KB {
        return Err(DeriveError::InvalidParam("memory_kb must be >= 8"));
    }
    if iterations < 1 {
        return Err(DeriveError::InvalidParam("iterations must be >= 1"));
    }
    if parallelism < 1 {
        return Err(DeriveError::InvalidParam("parallelism must be >= 1"));
    }

    let params = Params::new(memory_kb, iterations, parallelism, Some(output_len))
        .map_err(|_| DeriveError::InvalidParam("invalid Argon2 parameters"))?;

    let block_count = params.block_count();
    let total_bytes = block_count
        .checked_mul(core::mem::size_of::<Block>())
        .ok_or(DeriveError::OutOfMemory)?;
    if total_bytes > isize::MAX as usize {
        return Err(DeriveError::OutOfMemory);
    }

    let mut raw_blocks: Vec<Block> = Vec::new();
    if raw_blocks.try_reserve_exact(block_count).is_err() {
        return Err(DeriveError::OutOfMemory);
    }
    raw_blocks.resize(block_count, Block::default());
    let mut blocks = ZeroizingBlocks(raw_blocks);

    let mut out = Zeroizing::new(vec![0u8; output_len]);
    let argon2 = Argon2::new(Algorithm::Argon2id, Version::V0x13, params);
    argon2
        .hash_password_into_with_memory(password, salt, &mut out, &mut blocks.0)
        .map_err(DeriveError::Argon2Failed)?;

    Ok(out)
}

fn jbyte_array_to_zeroizing_vec(
    env: &Env,
    arr: &JByteArray,
) -> Result<Zeroizing<Vec<u8>>, DeriveError> {
    if arr.is_null() {
        return Err(DeriveError::InvalidParam("null byte array argument"));
    }
    let len = env
        .get_array_length(arr)
        .map_err(|_| DeriveError::InvalidParam("failed to read array length"))?;
    if len < 0 {
        return Err(DeriveError::InvalidParam("negative array length"));
    }
    let usize_len = len as usize;
    let mut buf = Zeroizing::new(Vec::new());
    if buf.try_reserve_exact(usize_len).is_err() {
        return Err(DeriveError::OutOfMemory);
    }
    buf.resize(usize_len, 0u8);
    if usize_len > 0 {
        let signed = unsafe {
            core::slice::from_raw_parts_mut(buf.as_mut_ptr() as *mut i8, usize_len)
        };
        env.get_byte_array_region(arr, 0, signed)
            .map_err(|_| DeriveError::InvalidParam("failed to copy array bytes"))?;
    }
    Ok(buf)
}

fn derive_jni_inner<'local>(
    env: &mut Env<'local>,
    password: &JByteArray<'local>,
    salt: &JByteArray<'local>,
    memory_kb: jint,
    iterations: jint,
    parallelism: jint,
    output_length: jint,
) -> Result<JByteArray<'local>, DeriveError> {
    let mem_u32 = u32::try_from(memory_kb)
        .map_err(|_| DeriveError::InvalidParam("memory_kb must be positive"))?;
    let iter_u32 = u32::try_from(iterations)
        .map_err(|_| DeriveError::InvalidParam("iterations must be positive"))?;
    let par_u32 = u32::try_from(parallelism)
        .map_err(|_| DeriveError::InvalidParam("parallelism must be positive"))?;
    let out_len = usize::try_from(output_length)
        .map_err(|_| DeriveError::InvalidParam("output_length must be positive"))?;

    let pwd_bytes = jbyte_array_to_zeroizing_vec(env, password)?;
    let salt_bytes = jbyte_array_to_zeroizing_vec(env, salt)?;

    let derived = derive_argon2id_raw(
        &pwd_bytes,
        &salt_bytes,
        mem_u32,
        iter_u32,
        par_u32,
        out_len,
    )?;

    env.byte_array_from_slice(&derived)
        .map_err(|_| DeriveError::OutOfMemory)
}

fn throw_if_clear(env: &mut Env, class_name: &JNIStr, msg: &str) {
    if !env.exception_check() {
        let jni_msg = JNIString::new(msg);
        let _ = env.throw_new(class_name, &jni_msg);
    }
}

#[no_mangle]
pub extern "system" fn Java_org_fossify_gallery_helpers_NativeArgon2_deriveKeyArgon2idNative<
    'local,
>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    password: JByteArray<'local>,
    salt: JByteArray<'local>,
    memory_kb: jint,
    iterations: jint,
    parallelism: jint,
    output_length: jint,
) -> JByteArray<'local> {
    unowned_env
        .with_env(|env| -> jni::errors::Result<JByteArray<'local>> {
            match derive_jni_inner(
                env,
                &password,
                &salt,
                memory_kb,
                iterations,
                parallelism,
                output_length,
            ) {
                Ok(arr) => Ok(arr),
                Err(DeriveError::InvalidParam(msg)) => {
                    throw_if_clear(env, jni_str!("java/lang/IllegalArgumentException"), msg);
                    Ok(JByteArray::null())
                }
                Err(DeriveError::OutOfMemory) => {
                    throw_if_clear(
                        env,
                        jni_str!("java/lang/OutOfMemoryError"),
                        "Native Argon2 memory allocation failed",
                    );
                    Ok(JByteArray::null())
                }
                Err(DeriveError::Argon2Failed(err)) => {
                    let msg = format!("Argon2 derivation failed: {err}");
                    throw_if_clear(env, jni_str!("java/lang/IllegalStateException"), &msg);
                    Ok(JByteArray::null())
                }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
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
        assert!(matches!(err, DeriveError::OutOfMemory));
    }

    #[test]
    fn invalid_parameters_return_invalid_param() {
        assert!(matches!(
            derive_argon2id_raw(b"pw", b"12345678", 0, 3, 2, 32),
            Err(DeriveError::InvalidParam(_))
        ));
        assert!(matches!(
            derive_argon2id_raw(b"pw", b"12345678", 64, 0, 2, 32),
            Err(DeriveError::InvalidParam(_))
        ));
        assert!(matches!(
            derive_argon2id_raw(b"pw", b"12345678", 64, 3, 0, 32),
            Err(DeriveError::InvalidParam(_))
        ));
        assert!(matches!(
            derive_argon2id_raw(b"pw", b"12345678", 64, 3, 2, 0),
            Err(DeriveError::InvalidParam(_))
        ));
    }
}

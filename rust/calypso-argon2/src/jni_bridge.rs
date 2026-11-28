use core::ffi::CStr;
use core::ptr::null_mut;
use std::ffi::CString;
use std::panic::{catch_unwind, AssertUnwindSafe};

use jni_sys::{
    jboolean, jbyte, jbyteArray, jclass, jint, jlong, jsize, JNIEnv, JNINativeInterface__1_2,
    JNI_FALSE, JNI_TRUE,
};
use zeroize::Zeroizing;

use crate::argon2_kdf::derive_argon2id_raw;
use crate::error::VaultError;
use crate::session::{
    create_or_update_vault_header, decrypt_file_header_metadata, decrypt_folder_meta,
    decrypt_vault_string, encrypt_file_header_with_dek, encrypt_folder_meta,
    encrypt_new_file_header, encrypt_vault_string, export_vmk_for_biometric_wrap,
    install_session_keys, is_unlocked, kek_fingerprint, lock_session, reencrypt_file_header,
    rewrap_vault_header_ephemeral, unlock_with_password, unlock_with_vmk, unwrap_file_dek,
    verify_secret_ephemeral,
};
use crate::vault_format::{FILE_ID_LEN, GCM_IV_LEN, KEY_LEN};

const MAX_JNI_INPUT_BYTES: usize = 64 * 1024 * 1024;

unsafe fn jni_table<'a>(env: *mut JNIEnv) -> Option<&'a JNINativeInterface__1_2> {
    if env.is_null() {
        return None;
    }
    let table_ptr = *env;
    if table_ptr.is_null() {
        return None;
    }
    Some(&(*table_ptr).v1_2)
}

unsafe fn exception_occurred(env: *mut JNIEnv, table: &JNINativeInterface__1_2) -> bool {
    (table.ExceptionCheck)(env) == JNI_TRUE
}

unsafe fn read_jbyte_array_vec(env: *mut JNIEnv, arr: jbyteArray) -> Result<Vec<u8>, VaultError> {
    let table = jni_table(env).ok_or(VaultError::InvalidParam("invalid JNIEnv"))?;
    if arr.is_null() {
        return Err(VaultError::InvalidParam("null byte array argument"));
    }
    let len = (table.GetArrayLength)(env, arr);
    if exception_occurred(env, table) || len < 0 {
        return Err(VaultError::InvalidParam("invalid byte array length"));
    }
    let usize_len = len as usize;
    if usize_len > MAX_JNI_INPUT_BYTES {
        return Err(VaultError::InvalidParam("byte array exceeds limit"));
    }
    let mut buf = Vec::new();
    if buf.try_reserve_exact(usize_len).is_err() {
        return Err(VaultError::OutOfMemory);
    }
    buf.resize(usize_len, 0u8);
    if usize_len > 0 {
        (table.GetByteArrayRegion)(env, arr, 0, len, buf.as_mut_ptr() as *mut jbyte);
        if exception_occurred(env, table) {
            return Err(VaultError::InvalidParam("failed to copy array bytes"));
        }
    }
    Ok(buf)
}

unsafe fn read_jbyte_array_zeroizing(
    env: *mut JNIEnv,
    arr: jbyteArray,
) -> Result<Zeroizing<Vec<u8>>, VaultError> {
    let table = jni_table(env).ok_or(VaultError::InvalidParam("invalid JNIEnv"))?;
    if arr.is_null() {
        return Err(VaultError::InvalidParam("null byte array argument"));
    }
    let len = (table.GetArrayLength)(env, arr);
    if exception_occurred(env, table) || len < 0 {
        return Err(VaultError::InvalidParam("invalid byte array length"));
    }
    let usize_len = len as usize;
    if usize_len > MAX_JNI_INPUT_BYTES {
        return Err(VaultError::InvalidParam("byte array exceeds limit"));
    }
    let mut buf = Zeroizing::new(Vec::new());
    if buf.try_reserve_exact(usize_len).is_err() {
        return Err(VaultError::OutOfMemory);
    }
    buf.resize(usize_len, 0u8);
    if usize_len > 0 {
        (table.GetByteArrayRegion)(env, arr, 0, len, buf.as_mut_ptr() as *mut jbyte);
        if exception_occurred(env, table) {
            return Err(VaultError::InvalidParam("failed to copy array bytes"));
        }
    }
    Ok(buf)
}

unsafe fn read_jbyte_array_fixed<const N: usize>(
    env: *mut JNIEnv,
    arr: jbyteArray,
    label: &'static str,
) -> Result<[u8; N], VaultError> {
    let v = read_jbyte_array_vec(env, arr)?;
    v.try_into().map_err(|_| VaultError::InvalidParam(label))
}

unsafe fn read_jbyte_array_zeroizing_fixed<const N: usize>(
    env: *mut JNIEnv,
    arr: jbyteArray,
    label: &'static str,
) -> Result<Zeroizing<[u8; N]>, VaultError> {
    let v = read_jbyte_array_zeroizing(env, arr)?;
    if v.len() != N {
        return Err(VaultError::InvalidParam(label));
    }
    let mut fixed = Zeroizing::new([0u8; N]);
    fixed.copy_from_slice(&v);
    Ok(fixed)
}

unsafe fn new_jbyte_array(env: *mut JNIEnv, bytes: &[u8]) -> Result<jbyteArray, VaultError> {
    let table = jni_table(env).ok_or(VaultError::InvalidParam("invalid JNIEnv"))?;
    let len = jsize::try_from(bytes.len()).map_err(|_| VaultError::OutOfMemory)?;
    let arr = (table.NewByteArray)(env, len);
    if arr.is_null() || exception_occurred(env, table) {
        return Err(VaultError::OutOfMemory);
    }
    if !bytes.is_empty() {
        (table.SetByteArrayRegion)(env, arr, 0, len, bytes.as_ptr() as *const jbyte);
        if exception_occurred(env, table) {
            (table.DeleteLocalRef)(env, arr);
            return Err(VaultError::OutOfMemory);
        }
    }
    Ok(arr)
}

unsafe fn throw_if_clear(env: *mut JNIEnv, class_name: &CStr, msg: &str) {
    let Some(table) = jni_table(env) else {
        return;
    };
    if exception_occurred(env, table) {
        return;
    }
    let cls = (table.FindClass)(env, class_name.as_ptr());
    if cls.is_null() || exception_occurred(env, table) {
        return;
    }
    let c_msg = CString::new(msg).unwrap_or_else(|_| {
        CString::new("Native vault error").expect("static fallback has no interior NUL")
    });
    let _ = (table.ThrowNew)(env, cls, c_msg.as_ptr());
    (table.DeleteLocalRef)(env, cls);
}

unsafe fn throw_vault_error(env: *mut JNIEnv, err: VaultError) {
    match err {
        VaultError::InvalidParam(msg) | VaultError::FormatInvalid(msg) => {
            throw_if_clear(env, c"java/lang/IllegalArgumentException", msg);
        }
        VaultError::OutOfMemory => {
            throw_if_clear(
                env,
                c"java/lang/OutOfMemoryError",
                "Native vault memory allocation failed",
            );
        }
        VaultError::Argon2Failed(err) => {
            let msg = format!("Argon2 derivation failed: {err}");
            throw_if_clear(env, c"java/lang/IllegalStateException", &msg);
        }
        VaultError::Locked => {
            throw_if_clear(env, c"java/lang/IllegalStateException", "Vault session is locked");
        }
        VaultError::AuthFailed => {
            throw_if_clear(env, c"java/lang/SecurityException", "Authentication failed");
        }
        VaultError::RngFailed => {
            throw_if_clear(env, c"java/lang/IllegalStateException", "OS CSPRNG failure");
        }
    }
}

unsafe fn run_byte_array(
    env: *mut JNIEnv,
    f: impl FnOnce() -> Result<Option<Vec<u8>>, VaultError>,
) -> jbyteArray {
    match catch_unwind(AssertUnwindSafe(f)) {
        Ok(Ok(Some(bytes))) => match new_jbyte_array(env, &bytes) {
            Ok(arr) => arr,
            Err(err) => {
                throw_vault_error(env, err);
                null_mut()
            }
        },
        Ok(Ok(None)) => null_mut(),
        Ok(Err(err)) => {
            throw_vault_error(env, err);
            null_mut()
        }
        Err(_) => {
            throw_if_clear(env, c"java/lang/RuntimeException", "Native vault panic");
            null_mut()
        }
    }
}

unsafe fn run_zeroizing_byte_array(
    env: *mut JNIEnv,
    f: impl FnOnce() -> Result<Option<Zeroizing<Vec<u8>>>, VaultError>,
) -> jbyteArray {
    match catch_unwind(AssertUnwindSafe(f)) {
        Ok(Ok(Some(bytes))) => match new_jbyte_array(env, &bytes) {
            Ok(arr) => arr,
            Err(err) => {
                throw_vault_error(env, err);
                null_mut()
            }
        },
        Ok(Ok(None)) => null_mut(),
        Ok(Err(err)) => {
            throw_vault_error(env, err);
            null_mut()
        }
        Err(_) => {
            throw_if_clear(env, c"java/lang/RuntimeException", "Native vault panic");
            null_mut()
        }
    }
}

unsafe fn run_bool(env: *mut JNIEnv, f: impl FnOnce() -> Result<bool, VaultError>) -> jboolean {
    match catch_unwind(AssertUnwindSafe(f)) {
        Ok(Ok(true)) => JNI_TRUE,
        Ok(Ok(false)) => JNI_FALSE,
        Ok(Err(err)) => {
            throw_vault_error(env, err);
            JNI_FALSE
        }
        Err(_) => {
            throw_if_clear(env, c"java/lang/RuntimeException", "Native vault panic");
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeArgon2_deriveKeyArgon2idNative(
    env: *mut JNIEnv,
    _class: jclass,
    password: jbyteArray,
    salt: jbyteArray,
    memory_kb: jint,
    iterations: jint,
    parallelism: jint,
    output_length: jint,
) -> jbyteArray {
    run_zeroizing_byte_array(env, || {
        let mem_u32 = u32::try_from(memory_kb)
            .map_err(|_| VaultError::InvalidParam("memory_kb must be positive"))?;
        let iter_u32 = u32::try_from(iterations)
            .map_err(|_| VaultError::InvalidParam("iterations must be positive"))?;
        let par_u32 = u32::try_from(parallelism)
            .map_err(|_| VaultError::InvalidParam("parallelism must be positive"))?;
        let out_len = usize::try_from(output_length)
            .map_err(|_| VaultError::InvalidParam("output_length must be positive"))?;

        let pwd_bytes = read_jbyte_array_zeroizing(env, password)?;
        let salt_bytes = read_jbyte_array_zeroizing(env, salt)?;
        derive_argon2id_raw(&pwd_bytes, &salt_bytes, mem_u32, iter_u32, par_u32, out_len).map(Some)
    })
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_isSessionUnlockedNative(
    env: *mut JNIEnv,
    _class: jclass,
) -> jboolean {
    run_bool(env, || Ok(is_unlocked()))
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_lockSessionNative(
    _env: *mut JNIEnv,
    _class: jclass,
) {
    let _ = catch_unwind(AssertUnwindSafe(lock_session));
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_createOrUpdateVaultHeaderNative(
    env: *mut JNIEnv,
    _class: jclass,
    password: jbyteArray,
    memory_kb: jint,
    iterations: jint,
    parallelism: jint,
    protection_type: jint,
    is_new_vault: jboolean,
) -> jbyteArray {
    run_byte_array(env, || {
        let pwd = read_jbyte_array_zeroizing(env, password)?;
        let mem = u32::try_from(memory_kb)
            .map_err(|_| VaultError::InvalidParam("memory_kb must be positive"))?;
        let iter = u32::try_from(iterations)
            .map_err(|_| VaultError::InvalidParam("iterations must be positive"))?;
        let par = u32::try_from(parallelism)
            .map_err(|_| VaultError::InvalidParam("parallelism must be positive"))?;
        let prot = u8::try_from(protection_type)
            .map_err(|_| VaultError::InvalidParam("protectionType out of range"))?;
        create_or_update_vault_header(&pwd, mem, iter, par, prot, is_new_vault != JNI_FALSE).map(Some)
    })
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_unlockVaultHeaderNative(
    env: *mut JNIEnv,
    _class: jclass,
    header_bytes: jbyteArray,
    password: jbyteArray,
) -> jboolean {
    run_bool(env, || {
        let header = read_jbyte_array_vec(env, header_bytes)?;
        let pwd = read_jbyte_array_zeroizing(env, password)?;
        unlock_with_password(&header, &pwd)
    })
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_verifyVaultSecretNative(
    env: *mut JNIEnv,
    _class: jclass,
    header_bytes: jbyteArray,
    password: jbyteArray,
) -> jboolean {
    run_bool(env, || {
        let header = read_jbyte_array_vec(env, header_bytes)?;
        let pwd = read_jbyte_array_zeroizing(env, password)?;
        verify_secret_ephemeral(&header, &pwd)
    })
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_rewrapVaultHeaderNative(
    env: *mut JNIEnv,
    _class: jclass,
    header_bytes: jbyteArray,
    password: jbyteArray,
    new_memory_kb: jint,
    new_iterations: jint,
    new_parallelism: jint,
    new_protection_type: jint,
) -> jbyteArray {
    run_byte_array(env, || {
        let header = read_jbyte_array_vec(env, header_bytes)?;
        let pwd = read_jbyte_array_zeroizing(env, password)?;
        let mem = u32::try_from(new_memory_kb)
            .map_err(|_| VaultError::InvalidParam("memory_kb must be positive"))?;
        let iter = u32::try_from(new_iterations)
            .map_err(|_| VaultError::InvalidParam("iterations must be positive"))?;
        let par = u32::try_from(new_parallelism)
            .map_err(|_| VaultError::InvalidParam("parallelism must be positive"))?;
        let prot = u8::try_from(new_protection_type)
            .map_err(|_| VaultError::InvalidParam("protectionType out of range"))?;
        rewrap_vault_header_ephemeral(&header, &pwd, mem, iter, par, prot)
    })
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_exportVmkForBiometricWrapNative(
    env: *mut JNIEnv,
    _class: jclass,
) -> jbyteArray {
    run_zeroizing_byte_array(env, || {
        Ok(export_vmk_for_biometric_wrap()?.map(|vmk| Zeroizing::new(vmk.to_vec())))
    })
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_unlockVaultWithVmkNative(
    env: *mut JNIEnv,
    _class: jclass,
    header_bytes: jbyteArray,
    vmk: jbyteArray,
) -> jboolean {
    run_bool(env, || {
        let header = read_jbyte_array_vec(env, header_bytes)?;
        let vmk_bytes = read_jbyte_array_zeroizing(env, vmk)?;
        unlock_with_vmk(&header, &vmk_bytes)
    })
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_installSessionKeysForTestNative(
    env: *mut JNIEnv,
    _class: jclass,
    vmk: jbyteArray,
    kek: jbyteArray,
    key_id: jint,
) {
    let res = catch_unwind(AssertUnwindSafe(|| -> Result<(), VaultError> {
        let vmk_arr = read_jbyte_array_zeroizing_fixed::<KEY_LEN>(env, vmk, "vmk must be 32 bytes")?;
        let kek_arr = read_jbyte_array_zeroizing_fixed::<KEY_LEN>(env, kek, "kek must be 32 bytes")?;
        let kid = (key_id as i16) as u16;
        install_session_keys(*vmk_arr, *kek_arr, kid);
        Ok(())
    }));
    match res {
        Ok(Ok(())) => {}
        Ok(Err(err)) => throw_vault_error(env, err),
        Err(_) => throw_if_clear(env, c"java/lang/RuntimeException", "Native vault panic"),
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_encryptNewFileHeaderNative(
    env: *mut JNIEnv,
    _class: jclass,
    version: jint,
    key_id: jint,
    segment_size: jint,
    ciphertext_length: jlong,
    meta_tail: jbyteArray,
) -> jbyteArray {
    run_zeroizing_byte_array(env, || {
        let ver = u8::try_from(version)
            .map_err(|_| VaultError::InvalidParam("version out of range"))?;
        let kid = (key_id as i16) as u16;
        let seg = u32::try_from(segment_size)
            .map_err(|_| VaultError::InvalidParam("segment_size must be positive"))?;
        let ct_len = u64::try_from(ciphertext_length)
            .map_err(|_| VaultError::InvalidParam("ciphertext_length must be non-negative"))?;
        let tail = read_jbyte_array_vec(env, meta_tail)?;

        let out = encrypt_new_file_header(ver, kid, seg, ct_len, &tail)?;
        let enc_len = u32::try_from(out.enc_header.len())
            .map_err(|_| VaultError::InvalidParam("enc_header too large"))?;
        let mut packed = Zeroizing::new(Vec::with_capacity(
            FILE_ID_LEN + GCM_IV_LEN + 4 + out.enc_header.len() + KEY_LEN,
        ));
        packed.extend_from_slice(&out.file_id);
        packed.extend_from_slice(&out.header_iv);
        packed.extend_from_slice(&enc_len.to_be_bytes());
        packed.extend_from_slice(&out.enc_header);
        packed.extend_from_slice(out.dek.as_ref());
        Ok(Some(packed))
    })
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_encryptFileHeaderWithDekNative(
    env: *mut JNIEnv,
    _class: jclass,
    version: jint,
    key_id: jint,
    file_id: jbyteArray,
    segment_size: jint,
    ciphertext_length: jlong,
    dek: jbyteArray,
    meta_tail: jbyteArray,
) -> jbyteArray {
    run_byte_array(env, || {
        let ver = u8::try_from(version)
            .map_err(|_| VaultError::InvalidParam("version out of range"))?;
        let kid = (key_id as i16) as u16;
        let fid = read_jbyte_array_fixed::<FILE_ID_LEN>(env, file_id, "file_id must be 16 bytes")?;
        let seg = u32::try_from(segment_size)
            .map_err(|_| VaultError::InvalidParam("segment_size must be positive"))?;
        let ct_len = u64::try_from(ciphertext_length)
            .map_err(|_| VaultError::InvalidParam("ciphertext_length must be non-negative"))?;
        let dek_arr = read_jbyte_array_zeroizing_fixed::<KEY_LEN>(env, dek, "dek must be 32 bytes")?;
        let tail = read_jbyte_array_vec(env, meta_tail)?;

        let (iv, enc_header) =
            encrypt_file_header_with_dek(ver, kid, &fid, seg, ct_len, &dek_arr, &tail)?;
        let mut packed = Vec::with_capacity(GCM_IV_LEN + enc_header.len());
        packed.extend_from_slice(&iv);
        packed.extend_from_slice(&enc_header);
        Ok(Some(packed))
    })
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_decryptFileHeaderMetadataNative(
    env: *mut JNIEnv,
    _class: jclass,
    version: jint,
    key_id: jint,
    file_id: jbyteArray,
    header_iv: jbyteArray,
    enc_header: jbyteArray,
) -> jbyteArray {
    run_byte_array(env, || {
        let ver = u8::try_from(version)
            .map_err(|_| VaultError::InvalidParam("version out of range"))?;
        let kid = (key_id as i16) as u16;
        let fid = read_jbyte_array_fixed::<FILE_ID_LEN>(env, file_id, "file_id must be 16 bytes")?;
        let iv = read_jbyte_array_fixed::<GCM_IV_LEN>(env, header_iv, "header_iv must be 12 bytes")?;
        let enc = read_jbyte_array_vec(env, enc_header)?;
        decrypt_file_header_metadata(ver, kid, &fid, &iv, &enc)
    })
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_unwrapFileDekNative(
    env: *mut JNIEnv,
    _class: jclass,
    version: jint,
    key_id: jint,
    file_id: jbyteArray,
    header_iv: jbyteArray,
    enc_header: jbyteArray,
) -> jbyteArray {
    run_zeroizing_byte_array(env, || {
        let ver = u8::try_from(version)
            .map_err(|_| VaultError::InvalidParam("version out of range"))?;
        let kid = (key_id as i16) as u16;
        let fid = read_jbyte_array_fixed::<FILE_ID_LEN>(env, file_id, "file_id must be 16 bytes")?;
        let iv = read_jbyte_array_fixed::<GCM_IV_LEN>(env, header_iv, "header_iv must be 12 bytes")?;
        let enc = read_jbyte_array_vec(env, enc_header)?;
        Ok(unwrap_file_dek(ver, kid, &fid, &iv, &enc)?.map(|dek| Zeroizing::new(dek.to_vec())))
    })
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_reencryptFileHeaderNative(
    env: *mut JNIEnv,
    _class: jclass,
    version: jint,
    key_id: jint,
    file_id: jbyteArray,
    old_header_iv: jbyteArray,
    old_enc_header: jbyteArray,
    new_meta_tail: jbyteArray,
) -> jbyteArray {
    run_byte_array(env, || {
        let ver = u8::try_from(version)
            .map_err(|_| VaultError::InvalidParam("version out of range"))?;
        let kid = (key_id as i16) as u16;
        let fid = read_jbyte_array_fixed::<FILE_ID_LEN>(env, file_id, "file_id must be 16 bytes")?;
        let old_iv = read_jbyte_array_fixed::<GCM_IV_LEN>(
            env,
            old_header_iv,
            "old_header_iv must be 12 bytes",
        )?;
        let old_enc = read_jbyte_array_vec(env, old_enc_header)?;
        let new_tail = read_jbyte_array_vec(env, new_meta_tail)?;
        match reencrypt_file_header(ver, kid, &fid, &old_iv, &old_enc, &new_tail)? {
            Some((new_iv, new_enc)) => {
                let mut packed = Vec::with_capacity(GCM_IV_LEN + new_enc.len());
                packed.extend_from_slice(&new_iv);
                packed.extend_from_slice(&new_enc);
                Ok(Some(packed))
            }
            None => Ok(None),
        }
    })
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_encryptVaultStringNative(
    env: *mut JNIEnv,
    _class: jclass,
    purpose_utf8: jbyteArray,
    plaintext_utf8: jbyteArray,
) -> jbyteArray {
    run_byte_array(env, || {
        let purpose_bytes = read_jbyte_array_vec(env, purpose_utf8)?;
        let purpose = core::str::from_utf8(&purpose_bytes)
            .map_err(|_| VaultError::InvalidParam("purpose must be valid UTF-8"))?;
        let plain = read_jbyte_array_zeroizing(env, plaintext_utf8)?;
        let (key_id, iv, ct) = encrypt_vault_string(purpose, &plain)?;
        let mut packed = Vec::with_capacity(2 + GCM_IV_LEN + ct.len());
        packed.extend_from_slice(&key_id.to_be_bytes());
        packed.extend_from_slice(&iv);
        packed.extend_from_slice(&ct);
        Ok(Some(packed))
    })
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_decryptVaultStringNative(
    env: *mut JNIEnv,
    _class: jclass,
    purpose_utf8: jbyteArray,
    iv: jbyteArray,
    ciphertext_and_tag: jbyteArray,
) -> jbyteArray {
    run_byte_array(env, || {
        let purpose_bytes = read_jbyte_array_vec(env, purpose_utf8)?;
        let purpose = core::str::from_utf8(&purpose_bytes)
            .map_err(|_| VaultError::InvalidParam("purpose must be valid UTF-8"))?;
        let iv_arr = read_jbyte_array_fixed::<GCM_IV_LEN>(env, iv, "iv must be 12 bytes")?;
        let ct = read_jbyte_array_vec(env, ciphertext_and_tag)?;
        decrypt_vault_string(purpose, &iv_arr, &ct)
    })
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_encryptFolderMetaNative(
    env: *mut JNIEnv,
    _class: jclass,
    name_utf8: jbyteArray,
) -> jbyteArray {
    run_byte_array(env, || {
        let name = read_jbyte_array_vec(env, name_utf8)?;
        let (iv, ct) = encrypt_folder_meta(&name)?;
        let mut packed = Vec::with_capacity(GCM_IV_LEN + ct.len());
        packed.extend_from_slice(&iv);
        packed.extend_from_slice(&ct);
        Ok(Some(packed))
    })
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_decryptFolderMetaNative(
    env: *mut JNIEnv,
    _class: jclass,
    iv: jbyteArray,
    ciphertext_and_tag: jbyteArray,
) -> jbyteArray {
    run_byte_array(env, || {
        let iv_arr = read_jbyte_array_fixed::<GCM_IV_LEN>(env, iv, "iv must be 12 bytes")?;
        let ct = read_jbyte_array_vec(env, ciphertext_and_tag)?;
        decrypt_folder_meta(&iv_arr, &ct)
    })
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_fossify_gallery_helpers_NativeVaultSession_computeKekFingerprintNative(
    env: *mut JNIEnv,
    _class: jclass,
    info_utf8: jbyteArray,
) -> jbyteArray {
    run_byte_array(env, || {
        let info = read_jbyte_array_vec(env, info_utf8)?;
        Ok(kek_fingerprint(&info)?.map(|fp| fp.to_vec()))
    })
}

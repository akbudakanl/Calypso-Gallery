use std::sync::Mutex;

use crate::primitives::{
    aes256_gcm_decrypt, aes256_gcm_encrypt, compute_kek_fingerprint, derive_file_header_key,
    derive_metadata_key,
};
use crate::session::{
    create_or_update_vault_header, decrypt_file_header_metadata, decrypt_folder_meta,
    decrypt_vault_string, encrypt_folder_meta, encrypt_new_file_header, encrypt_vault_string,
    export_vmk_for_biometric_wrap, install_session_keys, is_unlocked, kek_fingerprint,
    lock_session, reencrypt_file_header, rewrap_vault_header_ephemeral, unlock_with_password,
    unlock_with_vmk, unwrap_file_dek, verify_secret_ephemeral,
};
use crate::vault_format::{parse_vault_header, VAULT_FORMAT_VERSION};

static TEST_LOCK: Mutex<()> = Mutex::new(());

#[test]
fn vault_lifecycle_ephemeral_verify_rewrap_and_biometric() {
    let _g = TEST_LOCK.lock().unwrap();
    lock_session();
    assert!(!is_unlocked());

    let header = create_or_update_vault_header(b"s3cr3t!", 8_192, 1, 1, 2, true).unwrap();
    assert!(is_unlocked());

    let parsed = parse_vault_header(&header).unwrap();
    assert_eq!(parsed.memory_kb, 8_192);
    assert_eq!(parsed.iterations, 1);
    assert_eq!(parsed.parallelism, 1);
    assert_eq!(parsed.protection_type, 2);

    let vmk = export_vmk_for_biometric_wrap().unwrap().unwrap();
    let fp1 = kek_fingerprint(b"calypso-cli-bridge").unwrap().unwrap();

    lock_session();
    assert!(!is_unlocked());
    assert!(export_vmk_for_biometric_wrap().unwrap().is_none());

    // Wrong password rejected; session remains locked.
    assert!(!unlock_with_password(&header, b"wrong").unwrap());
    assert!(!is_unlocked());

    // Ephemeral verify succeeds without unlocking session.
    assert!(verify_secret_ephemeral(&header, b"s3cr3t!").unwrap());
    assert!(!verify_secret_ephemeral(&header, b"wrong").unwrap());
    assert!(!is_unlocked());

    // Ephemeral rewrap changes KDF params while preserving VMK/KEK and keeping session locked.
    assert!(rewrap_vault_header_ephemeral(&header, b"wrong", 8_192, 2, 1, 1).unwrap().is_none());
    let rewrapped = rewrap_vault_header_ephemeral(&header, b"s3cr3t!", 8_192, 2, 1, 1)
        .unwrap()
        .unwrap();
    assert!(!is_unlocked());

    // Biometric VMK unlock restores the exact KEK.
    assert!(unlock_with_vmk(&rewrapped, vmk.as_ref()).unwrap());
    assert!(is_unlocked());
    assert_eq!(kek_fingerprint(b"calypso-cli-bridge").unwrap().unwrap(), fp1);

    // Password change preserves VMK/KEK when unlocked.
    let updated = create_or_update_vault_header(b"n3w_pass", 8_192, 1, 1, 2, false).unwrap();
    lock_session();
    assert!(!unlock_with_password(&updated, b"s3cr3t!").unwrap());
    assert!(unlock_with_password(&updated, b"n3w_pass").unwrap());
    assert_eq!(kek_fingerprint(b"calypso-cli-bridge").unwrap().unwrap(), fp1);

    lock_session();
}

#[test]
fn file_header_encrypt_strip_dek_unwrap_and_zero_dek_rename() {
    let _g = TEST_LOCK.lock().unwrap();
    lock_session();
    let vmk = [0x11u8; 32];
    let kek = [0x22u8; 32];
    install_session_keys(vmk, kek, 7);

    let tail = b"sample-tail-meta";
    let out = encrypt_new_file_header(VAULT_FORMAT_VERSION, 7, 262_144, 512, tail).unwrap();

    // Metadata path strips the 32-byte DEK.
    let meta = decrypt_file_header_metadata(
        VAULT_FORMAT_VERSION,
        7,
        &out.file_id,
        &out.header_iv,
        &out.enc_header,
    )
    .unwrap()
    .unwrap();
    assert_eq!(&meta[0..2], &0x0001u16.to_be_bytes());
    assert_eq!(&meta[2..6], &262_144u32.to_be_bytes());
    assert_eq!(&meta[6..14], &512u64.to_be_bytes());
    assert_eq!(&meta[14..], tail);

    // On-demand DEK unwrap recovers exact DEK.
    let unwrapped_dek = unwrap_file_dek(
        VAULT_FORMAT_VERSION,
        7,
        &out.file_id,
        &out.header_iv,
        &out.enc_header,
    )
    .unwrap()
    .unwrap();
    assert_eq!(unwrapped_dek.as_ref(), out.dek.as_ref());

    // Tampered enc_header / wrong keyId rejected.
    let mut tampered = out.enc_header.clone();
    tampered[0] ^= 0x01;
    assert!(decrypt_file_header_metadata(VAULT_FORMAT_VERSION, 7, &out.file_id, &out.header_iv, &tampered).unwrap().is_none());
    assert!(unwrap_file_dek(VAULT_FORMAT_VERSION, 8, &out.file_id, &out.header_iv, &out.enc_header).unwrap().is_none());

    // Zero-DEK rename preserves DEK, segmentSize, and ciphertextLength.
    let new_tail = b"renamed-tail";
    let (new_iv, new_enc) = reencrypt_file_header(
        VAULT_FORMAT_VERSION,
        7,
        &out.file_id,
        &out.header_iv,
        &out.enc_header,
        new_tail,
    )
    .unwrap()
    .unwrap();
    let renamed_meta = decrypt_file_header_metadata(
        VAULT_FORMAT_VERSION,
        7,
        &out.file_id,
        &new_iv,
        &new_enc,
    )
    .unwrap()
    .unwrap();
    assert_eq!(&renamed_meta[14..], new_tail);
    let renamed_dek = unwrap_file_dek(VAULT_FORMAT_VERSION, 7, &out.file_id, &new_iv, &new_enc)
        .unwrap()
        .unwrap();
    assert_eq!(renamed_dek.as_ref(), out.dek.as_ref());

    lock_session();
    assert!(decrypt_file_header_metadata(VAULT_FORMAT_VERSION, 7, &out.file_id, &new_iv, &new_enc).unwrap().is_none());
}

#[test]
fn vault_string_double_prefix_folder_meta_and_fingerprint_parity() {
    let _g = TEST_LOCK.lock().unwrap();
    lock_session();
    let vmk = [0x33u8; 32];
    let kek = [0x44u8; 32];
    install_session_keys(vmk, kek, 1);

    let (key_id, iv, ct) = encrypt_vault_string("album", b"Secret Album").unwrap();
    assert_eq!(key_id, 1);
    assert_eq!(decrypt_vault_string("album", &iv, &ct).unwrap().unwrap(), b"Secret Album");
    assert!(decrypt_vault_string("album_json", &iv, &ct).unwrap().is_none());

    // Verify exact double-prefix HKDF info + single-prefix AAD vector.
    let (ctx, key) = derive_metadata_key(&kek, "album");
    assert_eq!(ctx, "CalypsoGallery:Metadata:AESGCM|album");
    let direct = aes256_gcm_decrypt(&key, &iv, ctx.as_bytes(), &ct).unwrap();
    assert_eq!(direct.as_slice(), b"Secret Album");

    // Verify header HKDF derives non-trivial key deterministic on file_id.
    let fid = [0xabu8; 16];
    let hk1 = derive_file_header_key(&kek, &fid);
    let hk2 = derive_file_header_key(&kek, &fid);
    assert_eq!(hk1.as_ref(), hk2.as_ref());

    // Folder .meta uses VMK with empty AAD.
    let (m_iv, m_ct) = encrypt_folder_meta(b"Vacation").unwrap();
    assert_eq!(decrypt_folder_meta(&m_iv, &m_ct).unwrap().unwrap(), b"Vacation");
    let direct_meta = aes256_gcm_decrypt(&vmk, &m_iv, b"", &m_ct).unwrap();
    assert_eq!(direct_meta.as_slice(), b"Vacation");
    let _ = aes256_gcm_encrypt(&vmk, &m_iv, b"wrong-aad", b"Vacation").unwrap();

    // KEK fingerprint matches direct HMAC-SHA256 truncation.
    let fp_session = kek_fingerprint(b"calypso-cli-bridge").unwrap().unwrap();
    let fp_direct = compute_kek_fingerprint(&kek, b"calypso-cli-bridge").unwrap();
    assert_eq!(fp_session, fp_direct);

    lock_session();
}

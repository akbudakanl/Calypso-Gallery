#[derive(Debug)]
pub(crate) enum VaultError {
    InvalidParam(&'static str),
    OutOfMemory,
    Argon2Failed(argon2::Error),
    Locked,
    AuthFailed,
    RngFailed,
    FormatInvalid(&'static str),
}

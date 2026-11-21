package org.fossify.gallery.helpers

internal const val KDF_ENGINE_RUST_V1 = 1

/**
 * JNI bridge to the native Rust `calypso_argon2` shared library (`RustCrypto/argon2` RFC 9106
 * Argon2id v1.3 with fallible native heap allocation and `zeroize` memory wiping).
 */
internal object NativeArgon2 {
    init {
        System.loadLibrary("calypso_argon2")
    }

    @JvmStatic
    external fun deriveKeyArgon2idNative(
        password: ByteArray,
        salt: ByteArray,
        memoryKb: Int,
        iterations: Int,
        parallelism: Int,
        outputLength: Int
    ): ByteArray
}

package org.fossify.gallery.helpers

import org.fossify.gallery.helpers.vault.VaultContainer
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.CharBuffer

/**
 * JNI bridge to the native Rust `VaultSession` inside `libcalypso_argon2.so`.
 *
 * Holds `VMK`, `KEK`, and `activeKeyId` exclusively inside Rust memory with deterministic
 * `ZeroizeOnDrop` wiping, while leaving `FGVLTENC` v1 payload streaming on `tink-android`.
 */
@Suppress("TooManyFunctions")
internal object NativeVaultSession {
    init {
        System.loadLibrary("calypso_argon2")
    }

    @JvmStatic
    external fun isSessionUnlockedNative(): Boolean

    @JvmStatic
    external fun lockSessionNative()

    @JvmStatic
    external fun createOrUpdateVaultHeaderNative(
        password: ByteArray,
        memoryKb: Int,
        iterations: Int,
        parallelism: Int,
        protectionType: Int,
        isNewVault: Boolean
    ): ByteArray

    @JvmStatic
    external fun unlockVaultHeaderNative(
        headerBytes: ByteArray,
        password: ByteArray
    ): Boolean

    @JvmStatic
    external fun verifyVaultSecretNative(
        headerBytes: ByteArray,
        password: ByteArray
    ): Boolean

    @JvmStatic
    external fun rewrapVaultHeaderNative(
        headerBytes: ByteArray,
        password: ByteArray,
        newMemoryKb: Int,
        newIterations: Int,
        newParallelism: Int,
        newProtectionType: Int
    ): ByteArray?

    @JvmStatic
    external fun exportVmkForBiometricWrapNative(): ByteArray?

    @JvmStatic
    external fun unlockVaultWithVmkNative(
        headerBytes: ByteArray,
        vmk: ByteArray
    ): Boolean

    @JvmStatic
    external fun installSessionKeysForTestNative(
        vmk: ByteArray,
        kek: ByteArray,
        keyId: Int
    )

    /**
     * Generates random `fileId` (16 B), `dek` (32 B), `headerIv` (12 B) and encrypts the header.
     * Packed return layout: `fileId(16) | headerIv(12) | encHeaderLen(4 BE) | encHeader(N) | dek(32)`.
     */
    @JvmStatic
    external fun encryptNewFileHeaderNative(
        version: Int,
        keyId: Int,
        segmentSize: Int,
        ciphertextLength: Long,
        metaTail: ByteArray
    ): ByteArray

    /**
     * Re-encrypts an existing file's header with a fresh IV for an already-known `fileId` + `dek`.
     * Packed return layout: `headerIv(12) | encHeader(N)`.
     */
    @JvmStatic
    external fun encryptFileHeaderWithDekNative(
        version: Int,
        keyId: Int,
        fileId: ByteArray,
        segmentSize: Int,
        ciphertextLength: Long,
        dek: ByteArray,
        metaTail: ByteArray
    ): ByteArray

    /**
     * Decrypts an `FGVLTENC` header inside Rust, **strips and zeroes the 32-byte DEK**, and returns
     * `algorithmParams(2) | segmentSize(4) | ciphertextLength(8) | metaTail(...)`, or `null`.
     */
    @JvmStatic
    external fun decryptFileHeaderMetadataNative(
        version: Int,
        keyId: Int,
        fileId: ByteArray,
        headerIv: ByteArray,
        encHeader: ByteArray
    ): ByteArray?

    /**
     * Unwraps ONLY the 32-byte per-file DEK for a single stream/verify operation.
     * Callers MUST fill the returned array with zeros in a `finally` block immediately after use.
     */
    @JvmStatic
    external fun unwrapFileDekNative(
        version: Int,
        keyId: Int,
        fileId: ByteArray,
        headerIv: ByteArray,
        encHeader: ByteArray
    ): ByteArray?

    /**
     * Re-encrypts a container header for rename without exposing the DEK to JVM memory.
     * Packed return layout: `newHeaderIv(12) | newEncHeader(M)`, or `null`.
     */
    @JvmStatic
    external fun reencryptFileHeaderNative(
        version: Int,
        keyId: Int,
        fileId: ByteArray,
        oldHeaderIv: ByteArray,
        oldEncHeader: ByteArray,
        newMetaTail: ByteArray
    ): ByteArray?

    /** Packed return layout: `keyId(2 BE) | iv(12) | ciphertextAndTag(M)`. */
    @JvmStatic
    external fun encryptVaultStringNative(
        purposeUtf8: ByteArray,
        plaintextUtf8: ByteArray
    ): ByteArray

    @JvmStatic
    external fun decryptVaultStringNative(
        purposeUtf8: ByteArray,
        iv: ByteArray,
        ciphertextAndTag: ByteArray
    ): ByteArray?

    /** Packed return layout: `iv(12) | ciphertextAndTag(M)`. */
    @JvmStatic
    external fun encryptFolderMetaNative(nameUtf8: ByteArray): ByteArray

    @JvmStatic
    external fun decryptFolderMetaNative(
        iv: ByteArray,
        ciphertextAndTag: ByteArray
    ): ByteArray?

    @JvmStatic
    external fun computeKekFingerprintNative(infoUtf8: ByteArray): ByteArray?

    internal fun secretCharsToUtf8Bytes(secret: CharArray): ByteArray {
        val byteBuffer = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(secret))
        val exact = ByteArray(byteBuffer.remaining())
        byteBuffer.get(exact)
        if (byteBuffer.hasArray()) {
            byteBuffer.array().fill(0)
        }
        return exact
    }

    internal data class NewFileHeaderPacked(
        val fileId: ByteArray,
        val headerIv: ByteArray,
        val encHeader: ByteArray,
        val dek: ByteArray
    ) {
        fun wipeDek() {
            dek.fill(0)
        }
    }

    internal fun unpackNewFileHeader(packed: ByteArray): NewFileHeaderPacked {
        return try {
            DataInputStream(ByteArrayInputStream(packed)).use { dis ->
                val fileId = ByteArray(VaultContainer.FILE_ID_LENGTH).also { dis.readFully(it) }
                val headerIv = ByteArray(VaultContainer.GCM_IV_LENGTH).also { dis.readFully(it) }
                val encLen = dis.readInt()
                require(encLen > 0) { "Invalid encrypted header length: $encLen" }
                val encHeader = ByteArray(encLen).also { dis.readFully(it) }
                val dek = ByteArray(ARGON2_KEY_BYTES).also { dis.readFully(it) }
                NewFileHeaderPacked(fileId, headerIv, encHeader, dek)
            }
        } finally {
            packed.fill(0)
        }
    }
}

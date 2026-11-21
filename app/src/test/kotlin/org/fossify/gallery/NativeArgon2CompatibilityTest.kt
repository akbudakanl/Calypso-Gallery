package org.fossify.gallery

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.fossify.gallery.helpers.ARGON2_HIGH_MEMORY_KB
import org.fossify.gallery.helpers.ARGON2_MIN_MEMORY_KB
import org.fossify.gallery.helpers.ARGON2_ULTRA_MEMORY_KB
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.NativeArgon2
import org.fossify.gallery.helpers.deriveKeyArgon2id
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Verifies that the native Rust Argon2id JNI engine produces bit-identical keys to both
 * BouncyCastle (legacy Android provider) and Python `argon2-cffi` (used by `calypso_vault.py`),
 * and translates allocation / validation failures into catchable JVM exceptions.
 */
class NativeArgon2CompatibilityTest {

    private fun deriveBouncyCastle(
        password: String,
        salt: ByteArray,
        memoryKb: Int,
        iterations: Int,
        parallelism: Int
    ): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withIterations(iterations)
            .withMemoryAsKB(memoryKb)
            .withParallelism(parallelism)
            .build()
        val out = ByteArray(32)
        val gen = Argon2BytesGenerator()
        gen.init(params)
        gen.generateBytes(password.toByteArray(Charsets.UTF_8), out, 0, out.size)
        return out
    }

    @Test
    fun rustMatchesBouncyCastleAcrossTiersPasswordsAndSalts() {
        val cases = listOf(
            Case("", ByteArray(16) { 0x11 }, ARGON2_MIN_MEMORY_KB, 3, 2),
            Case("correct-horse-battery-staple", ByteArray(8) { (it + 1).toByte() }, ARGON2_MIN_MEMORY_KB, 3, 2),
            Case("ŞifreÇöĞüİ!2026", ByteArray(16) { (it * 7 + 3).toByte() }, ARGON2_HIGH_MEMORY_KB, 4, 2),
            Case("FlagshipVault256!", ByteArray(32) { (255 - it).toByte() }, ARGON2_ULTRA_MEMORY_KB, 3, 2)
        )

        cases.forEach { case ->
            val rustKey = CryptoManager.deriveKeyArgon2id(
                secret = case.password,
                salt = case.salt,
                memoryKb = case.memoryKb,
                iterations = case.iterations,
                parallelism = case.parallelism
            ).encoded
            val bcKey = deriveBouncyCastle(
                password = case.password,
                salt = case.salt,
                memoryKb = case.memoryKb,
                iterations = case.iterations,
                parallelism = case.parallelism
            )
            assertArrayEquals(
                "Mismatch for mem=${case.memoryKb}, iter=${case.iterations}, saltLen=${case.salt.size}",
                bcKey,
                rustKey
            )
        }
    }

    @Test
    fun rustMatchesPythonArgon2CffiGoldenVector() {
        val password = "Calypso!2026-\u015eifre"
        val salt = ByteArray(16) { it.toByte() }
        val key = CryptoManager.deriveKeyArgon2id(
            secret = password,
            salt = salt,
            memoryKb = ARGON2_MIN_MEMORY_KB,
            iterations = 3,
            parallelism = 2
        ).encoded
        val actualHex = key.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(
            "03942e963a80afad97f51f9eff60cbf3eaec2c1ac17894b0e4076dc4deee23e0",
            actualHex
        )
    }

    @Test
    fun impossibleMemoryThrowsCatchableOutOfMemoryErrorWithoutCrashing() {
        try {
            NativeArgon2.deriveKeyArgon2idNative(
                password = "secret".toByteArray(Charsets.UTF_8),
                salt = ByteArray(16) { 1 },
                memoryKb = Int.MAX_VALUE,
                iterations = 3,
                parallelism = 2,
                outputLength = 32
            )
            fail("Expected OutOfMemoryError")
        } catch (_: OutOfMemoryError) {
            // Expected catchable JVM error
        }
    }

    @Test
    fun invalidParametersThrowIllegalArgumentException() {
        val validPwd = byteArrayOf(1, 2, 3)
        val validSalt = ByteArray(16) { 2 }
        val badCalls = listOf(
            { NativeArgon2.deriveKeyArgon2idNative(validPwd, validSalt, 0, 3, 2, 32) },
            { NativeArgon2.deriveKeyArgon2idNative(validPwd, validSalt, 64, 0, 2, 32) },
            { NativeArgon2.deriveKeyArgon2idNative(validPwd, validSalt, 64, 3, 0, 32) },
            { NativeArgon2.deriveKeyArgon2idNative(validPwd, validSalt, 64, 3, 2, 0) }
        )
        badCalls.forEach { call ->
            try {
                call()
                fail("Expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
                // Expected validation error
            }
        }
    }

    private data class Case(
        val password: String,
        val salt: ByteArray,
        val memoryKb: Int,
        val iterations: Int,
        val parallelism: Int
    )
}

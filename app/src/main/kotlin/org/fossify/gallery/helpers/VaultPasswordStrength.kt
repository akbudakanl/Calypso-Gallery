package org.fossify.gallery.helpers

import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Lightweight, fully on-device strength estimate for a new vault secret.
 *
 * Inspired by the ideas behind zxcvbn/KeePassXC (character-class entropy plus penalties for
 * dictionary words, repeats, sequences and keyboard walks), but intentionally small: no dictionary
 * file, no native code, no network. The result is guidance for the person choosing a password -
 * the real offline brute-force resistance comes from Argon2 and the chosen unlock-time target.
 */
object VaultPasswordStrength {
    enum class Level { BAD, POOR, WEAK, GOOD, EXCELLENT }

    data class Result(val score: Int, val level: Level)

    /** Short, high-frequency passwords only. BIP-39-style wordlists are deliberately random and are NOT weak passwords. */
    private val COMMON = listOf(
        "123456", "123456789", "12345678", "1234567890", "qwerty", "qwerty123", "qwertyuiop",
        "password", "password1", "passw0rd", "p@ssw0rd", "letmein", "iloveyou", "admin", "admin123",
        "welcome", "welcome1", "monkey", "dragon", "sunshine", "princess", "football", "baseball",
        "trustno1", "master", "secret", "abc123", "abcd1234", "1q2w3e4r", "1qaz2wsx", "zaq12wsx",
        "qazwsx", "asdfgh", "asdf1234", "zxcvbnm", "1234qwer", "changeme", "computer", "internet",
        "service", "default", " guest", "test", "test123", "user", "user123", "root", "toor",
        // Türkçe yaygın olanlar
        "parola", "parola123", "sifre", "şifre", "sifre123", "şifre123", "kasa", "kasaparola",
        "guvenli", "güvenli", "hatirlamiyorum", "hatırlamıyorum", "bilmem", "parolam", "sifrem",
        "şifrem", "123456a", "qwertytr", "turkiye", "türkiye", "istanbul", "ankara", "izmir",
        "ataturk", "atatürk", "mustafa", "kemal", "fenerbahce", "fenerbahçe", "galatasaray",
        "besiktas", "beşiktaş", "trabzonspor", "bjk", "gs", "fb", "asik", "aşık", "sevdigim",
        "sevdiğim", "hayat", "yil2024", "yıl2024", "2024", "2025", "2026"
    ).map { it.trim() }.toHashSet()

    private val COMMON_PINS = setOf(
        "0000", "1111", "2222", "3333", "4444", "5555", "6666", "7777", "8888", "9999",
        "1234", "4321", "123456", "654321", "112233", "121212", "010203", "102030",
        "000000", "111111", "123123", "2580", "159753", "1478963", "2000", "1990", "2001"
    )

    private val YEAR = Regex("(19|20)\\d{2}")

    fun evaluate(secret: CharArray, isPin: Boolean): Result {
        if (secret.isEmpty()) return Result(0, Level.BAD)
        val text = String(secret)
        return if (isPin) evaluatePin(text) else evaluatePassword(text)
    }

    private fun evaluatePin(text: String): Result {
        var bits = text.length * log2(10.0)
        if (text.all { it == text.first() }) {
            bits = bits.coerceAtMost(2.0)
        } else if (COMMON_PINS.contains(text)) {
            bits = bits.coerceAtMost(4.0)
        } else if (isDigitSequence(text)) {
            bits = bits.coerceAtMost(6.0)
        } else if (YEAR.containsMatchIn(text)) {
            bits -= 5.0
        }
        val score = (bits * 2.5).roundToInt().coerceIn(0, 100)
        return Result(score, levelOf(score))
    }

    private fun evaluatePassword(text: String): Result {
        var charset = 0
        if (text.any { it.isLowerCase() }) charset += 26
        if (text.any { it.isUpperCase() }) charset += 26
        if (text.any { it.isDigit() }) charset += 10
        if (text.any { !it.isLetterOrDigit() }) charset += 32
        if (charset == 0) charset = 26

        var bits = text.length * log2(charset.toDouble())
        val normalized = normalize(text)

        if (normalized.length in 1..32 && normalized in COMMON) {
            bits = bits.coerceAtMost(8.0)
        } else {
            COMMON.firstOrNull { it.length >= 5 && normalized.contains(it) }?.let { bits -= 10.0 }
        }

        val longestRun = longestRepeatedRun(normalized)
        if (longestRun >= 3) bits -= (longestRun - 2) * 3.0

        if (hasSequence(normalized, 3)) bits -= 8.0
        if (hasKeyboardWalk(normalized)) bits -= 12.0
        if (YEAR.containsMatchIn(normalized)) bits -= 6.0
        if (text.length < 8) bits = bits.coerceAtMost(22.0)

        val score = bits.roundToInt().coerceIn(0, 100)
        return Result(score, levelOf(score))
    }

    private fun levelOf(score: Int) = when {
        score <= 0 -> Level.BAD
        score < 40 -> Level.POOR
        score < 75 -> Level.WEAK
        score < 100 -> Level.GOOD
        else -> Level.EXCELLENT
    }

    private fun normalize(text: String): String = text.lowercase()
        .replace('@', 'a').replace('0', 'o').replace('1', 'l').replace('3', 'e')
        .replace('5', 's').replace('7', 't').replace('$', 's').replace('!', 'i')

    private fun longestRepeatedRun(text: String): Int {
        var best = 1
        var current = 1
        for (i in 1 until text.length) {
            current = if (text[i] == text[i - 1]) current + 1 else 1
            best = maxOf(best, current)
        }
        return best
    }

    private fun isDigitSequence(text: String): Boolean {
        if (text.length < 3 || text.any { !it.isDigit() }) return false
        val step = text[1] - text[0]
        if (step == 0) return false
        return (1 until text.length).all { text[it] - text[it - 1] == step }
    }

    private fun hasSequence(text: String, minLength: Int): Boolean {
        var run = 1
        for (i in 1 until text.length) {
            val diff = text[i].code - text[i - 1].code
            run = if (diff == 1 || diff == -1) run + 1 else 1
            if (run >= minLength) return true
        }
        return false
    }

    private fun hasKeyboardWalk(text: String): Boolean {
        val walks = listOf("qwerty", "qwert", "asdf", "zxcv", "1qaz", "2wsx", "qazwsx", "1q2w3e")
        val trWalks = listOf("qwerty", "Ğüio", "ğüio", "asdf", "şdfg", "zxcv", "cvbn")
        return (walks + trWalks).any { walk -> text.contains(walk.lowercase()) }
    }
}

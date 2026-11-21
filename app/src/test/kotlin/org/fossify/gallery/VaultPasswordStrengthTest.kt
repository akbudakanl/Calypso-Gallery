package org.fossify.gallery

import org.fossify.gallery.helpers.VaultPasswordStrength
import org.fossify.gallery.helpers.VaultPasswordStrength.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultPasswordStrengthTest {

    @Test
    fun isWeakMatchesStrengthBarWeakTiersOnly() {
        assertTrue(Level.BAD.isWeak)
        assertTrue(Level.POOR.isWeak)
        assertFalse("Level.WEAK corresponds to Fair/Orta and must not trigger weak warning", Level.WEAK.isWeak)
        assertFalse(Level.GOOD.isWeak)
        assertFalse(Level.EXCELLENT.isWeak)
    }

    @Test
    fun emptyAndWeakSecretsTriggerWeakWarning() {
        val empty = VaultPasswordStrength.evaluate(charArrayOf(), isPin = false)
        assertEquals(Level.BAD, empty.level)
        assertTrue(empty.level.isWeak)

        val commonPassword = VaultPasswordStrength.evaluate("parola".toCharArray(), isPin = false)
        assertEquals(Level.BAD, commonPassword.level)
        assertTrue(commonPassword.level.isWeak)

        val commonDigitsPassword = VaultPasswordStrength.evaluate("123456".toCharArray(), isPin = false)
        assertEquals(Level.POOR, commonDigitsPassword.level)
        assertTrue(commonDigitsPassword.level.isWeak)

        val shortPassword = VaultPasswordStrength.evaluate("aB3$".toCharArray(), isPin = false)
        assertEquals(Level.POOR, shortPassword.level)
        assertTrue(shortPassword.level.isWeak)

        val sequentialPin = VaultPasswordStrength.evaluate("1234".toCharArray(), isPin = true)
        assertEquals(Level.POOR, sequentialPin.level)
        assertTrue(sequentialPin.level.isWeak)
    }

    @Test
    fun fairStrengthInputsDoNotTriggerWeakWarning() {
        val fairPassword = VaultPasswordStrength.evaluate("K7mP2qR9x".toCharArray(), isPin = false)
        assertEquals(Level.WEAK, fairPassword.level)
        assertTrue(fairPassword.score in 40..59)
        assertFalse(fairPassword.level.isWeak)

        val fairPin = VaultPasswordStrength.evaluate("73915824".toCharArray(), isPin = true)
        assertEquals(Level.WEAK, fairPin.level)
        assertTrue(fairPin.score in 40..59)
        assertFalse(fairPin.level.isWeak)
    }

    @Test
    fun strongAndVeryStrongSecretsDoNotTriggerWeakWarning() {
        val goodPassword = VaultPasswordStrength.evaluate("K7#mP2!qR9\$x".toCharArray(), isPin = false)
        assertEquals(Level.GOOD, goodPassword.level)
        assertTrue(goodPassword.score in 60..79)
        assertFalse(goodPassword.level.isWeak)

        val excellentPassword = VaultPasswordStrength.evaluate("K7#mP2!qR9\$xL4@v".toCharArray(), isPin = false)
        assertEquals(Level.EXCELLENT, excellentPassword.level)
        assertTrue(excellentPassword.score >= 80)
        assertFalse(excellentPassword.level.isWeak)
    }
}

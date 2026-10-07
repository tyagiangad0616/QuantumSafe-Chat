package com.example.quantumsafechat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantumSafeIdentityTest {

    @Test
    fun normalizePhone_stripsSeparatorsAndWhitespace() {
        assertEquals(
            "+919876543210",
            QuantumSafeIdentityStore.normalizePhone(" +91 98765-43210 ")
        )
        assertEquals(
            "+919876543210",
            QuantumSafeIdentityStore.normalizePhone("(+91) 98765 43210")
        )
    }

    @Test
    fun accountIdForPhone_isDeterministicAndPrefixed() {
        val first = QuantumSafeIdentityStore.accountIdForPhone("+91 98765 43210")
        val second = QuantumSafeIdentityStore.accountIdForPhone("+919876543210")

        assertEquals(first, second)
        assertTrue(first.startsWith("qs_"))
        assertEquals(27, first.length)
    }

    @Test
    fun accountIdForPhone_changesWhenPhoneChanges() {
        val angad = QuantumSafeIdentityStore.accountIdForPhone("+919876543210")
        val other = QuantumSafeIdentityStore.accountIdForPhone("+911234567890")

        assertNotEquals(angad, other)
    }

    @Test
    fun displayInitial_usesFirstLetterOrDigit() {
        assertEquals("A", QuantumSafeIdentityStore.displayInitial("Angad"))
        assertEquals("Q", QuantumSafeIdentityStore.displayInitial("   "))
        assertEquals("7", QuantumSafeIdentityStore.displayInitial("7 Team"))
    }
}

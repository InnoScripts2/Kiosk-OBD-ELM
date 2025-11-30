package com.selfservice.kiosk.ui.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactValidatorTest {

    @Test
    fun `phone validation fails on empty`() {
        val result = validatePhoneNumber("")
        assertFalse(result.isValid)
        assertEquals("Введите номер телефона", result.errorMessage)
    }

    @Test
    fun `phone validation fails when digits below minimum`() {
        val result = validatePhoneNumber("+7 (999) 12")
        assertFalse(result.isValid)
        assertEquals("Номер должен содержать минимум 10 цифр", result.errorMessage)
    }

    @Test
    fun `phone validation accepts digits and formatting`() {
        val result = validatePhoneNumber("+7 (999) 123-45-67")
        assertTrue(result.isValid)
        assertEquals(null, result.errorMessage)
        assertEquals("79991234567", normalizePhoneNumber("+7 (999) 123-45-67"))
    }

    @Test
    fun `email validation fails on empty`() {
        val result = validateEmailAddress("")
        assertFalse(result.isValid)
        assertEquals("Введите email", result.errorMessage)
    }

    @Test
    fun `email validation fails on malformed address`() {
        val result = validateEmailAddress("user@@example")
        assertFalse(result.isValid)
        assertEquals("Введите email в формате name@example.com", result.errorMessage)
    }

    @Test
    fun `email validation accepts regular address`() {
        val result = validateEmailAddress("user@example.com")
        assertTrue(result.isValid)
        assertEquals(null, result.errorMessage)
    }
}

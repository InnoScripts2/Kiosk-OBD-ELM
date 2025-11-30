package com.selfservice.kiosk.ui.validation

/**
 * Утилиты валидации контактных данных клиента киоска.
 */
data class ContactValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

/**
 * Возвращает только цифры из телефонного номера, чтобы передавать его дальше в сервисы.
 */
fun normalizePhoneNumber(input: String): String = input.filter { it.isDigit() }

private const val MIN_PHONE_DIGITS = 10
private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()

fun validatePhoneNumber(input: String): ContactValidationResult {
    val digits = normalizePhoneNumber(input)
    if (digits.isEmpty()) {
        return ContactValidationResult(
            isValid = false,
            errorMessage = "Введите номер телефона"
        )
    }
    if (digits.length < MIN_PHONE_DIGITS) {
        return ContactValidationResult(
            isValid = false,
            errorMessage = "Номер должен содержать минимум 10 цифр"
        )
    }
    return ContactValidationResult(true, null)
}

fun validateEmailAddress(input: String): ContactValidationResult {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return ContactValidationResult(
            isValid = false,
            errorMessage = "Введите email"
        )
    }
    if (!EMAIL_REGEX.matches(trimmed)) {
        return ContactValidationResult(
            isValid = false,
            errorMessage = "Введите email в формате name@example.com"
        )
    }
    return ContactValidationResult(true, null)
}

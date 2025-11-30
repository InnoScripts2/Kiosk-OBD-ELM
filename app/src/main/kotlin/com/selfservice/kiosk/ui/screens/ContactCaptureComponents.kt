package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.selfservice.kiosk.ui.validation.ContactValidationResult
import com.selfservice.platform.ui.foundation.KioskTokens

/**
 * Shared contact capture UI used by input screens.
 */
data class ContactHighlight(
    val title: String,
    val description: String
)

@Composable
fun ContactFormSection(
    phone: String,
    email: String,
    phoneTouched: Boolean,
    emailTouched: Boolean,
    phoneValidation: ContactValidationResult,
    emailValidation: ContactValidationResult,
    onPhoneChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    phoneTestTag: String? = null,
    emailTestTag: String? = null
) {
    val spacing = KioskTokens.spacing
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        Text(
            text = "Контакты",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        var phoneFieldModifier = Modifier.fillMaxWidth()
        if (phoneTestTag != null) {
            phoneFieldModifier = phoneFieldModifier.testTag(phoneTestTag)
        }

        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChanged,
            label = { Text("Телефон") },
            placeholder = { Text("+7 (9XX) XXX-XX-XX") },
            isError = phoneTouched && !phoneValidation.isValid,
            supportingText = {
                val message = when {
                    phoneTouched && !phoneValidation.isValid -> phoneValidation.errorMessage
                    else -> "Формат проверяем автоматически"
                }
                Text(message ?: "")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = phoneFieldModifier
        )

        var emailFieldModifier = Modifier.fillMaxWidth()
        if (emailTestTag != null) {
            emailFieldModifier = emailFieldModifier.testTag(emailTestTag)
        }

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChanged,
            label = { Text("Email") },
            placeholder = { Text("name@example.com") },
            isError = emailTouched && !emailValidation.isValid,
            supportingText = {
                val message = when {
                    emailTouched && !emailValidation.isValid -> emailValidation.errorMessage
                    else -> "Используем только для отчёта"
                }
                Text(message ?: "")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = emailFieldModifier
        )
    }
}

@Composable
fun ContactHighlightsColumn(
    items: List<ContactHighlight>,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        Text(
            text = "Что получит клиент",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        items.forEach { item ->
            ContactHighlightItem(item = item)
        }
    }
}

@Composable
private fun ContactHighlightItem(item: ContactHighlight) {
    val spacing = KioskTokens.spacing
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

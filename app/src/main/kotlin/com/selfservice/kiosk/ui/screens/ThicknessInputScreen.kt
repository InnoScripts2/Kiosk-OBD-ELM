package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfservice.kiosk.ui.state.VehicleType
import com.selfservice.kiosk.ui.validation.ContactValidationResult
import com.selfservice.kiosk.ui.validation.normalizePhoneNumber
import com.selfservice.kiosk.ui.validation.validateEmailAddress
import com.selfservice.kiosk.ui.validation.validatePhoneNumber
import com.selfservice.platform.ui.components.KioskPanel
import com.selfservice.platform.ui.components.KioskPrimaryButton
import com.selfservice.platform.ui.foundation.KioskTokens

@Composable
fun ThicknessInputScreen(
    onNext: (VehicleType, String, String) -> Unit
) {
    var selectedType by remember { mutableStateOf<VehicleType?>(null) }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneTouched by remember { mutableStateOf(false) }
    var emailTouched by remember { mutableStateOf(false) }

    val phoneValidation = validatePhoneNumber(phone)
    val emailValidation = validateEmailAddress(email)
    val isValid = selectedType != null && phoneValidation.isValid && emailValidation.isValid

    val spacing = KioskTokens.spacing
    val background = KioskTokens.gradients.hero
    val vehicleTypes = remember { VehicleType.values().toList() }
    val contactHighlights = remember {
        listOf(
            ContactHighlight(
                title = "PDF отчёт",
                description = "Содержит 40–60 замеров и рекомендации по зонам"
            ),
            ContactHighlight(
                title = "SMS-уведомление",
                description = "Сообщим, когда прибор готов или отчёт отправлен"
            ),
            ContactHighlight(
                title = "Удаляем контакты",
                description = "Храним телефон и email не дольше 30 дней"
            )
        )
    }
    val isCompactLayout = LocalConfiguration.current.screenWidthDp < 900

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1440.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xxl, vertical = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.xxl)
        ) {
            ThicknessInputHeader()

            ThicknessInputMetaGrid(
                selectedType = selectedType,
                isContactComplete = phoneValidation.isValid && emailValidation.isValid
            )

            KioskPanel(
                headline = "Перед выдачей",
                supportingText = "Подтвердите тип кузова и контакты для отчёта"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    InfoRow(label = "Устройство", value = "60 зон измерения, автосохранение")
                    InfoRow(label = "Поддержка", value = "PDF отчёт + инструкции по почте")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(
                    text = "Тип автомобиля",
                    style = MaterialTheme.typography.titleMedium
                )
                VehicleTypeGrid(
                    types = vehicleTypes,
                    selectedType = selectedType,
                    onSelect = { selectedType = it }
                )
            }

            selectedType?.let {
                SelectedVehicleSummary(descriptor = it)
            }

            KioskPanel(
                headline = "Куда отправить отчёт?",
                supportingText = "Телефон и email нужны только для PDF и статуса."
            ) {
                ContactRequestPanel(
                    isCompactLayout = isCompactLayout,
                    phone = phone,
                    email = email,
                    phoneTouched = phoneTouched,
                    emailTouched = emailTouched,
                    phoneValidation = phoneValidation,
                    emailValidation = emailValidation,
                    onPhoneChanged = {
                        phone = it
                        if (!phoneTouched) phoneTouched = true
                    },
                    onEmailChanged = {
                        email = it
                        if (!emailTouched) emailTouched = true
                    },
                    highlights = contactHighlights
                )
            }

            KioskPrimaryButton(
                onClick = {
                    val vehicleType = selectedType ?: return@KioskPrimaryButton
                    onNext(vehicleType, normalizePhoneNumber(phone), email.trim())
                },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("thk-continue-button")
            ) {
                Text("Далее")
            }
        }
    }
}

@Composable
private fun ThicknessInputHeader() {
    val spacing = KioskTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = "Толщинометрия ЛКП",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = "Киоск выдаёт прибор, подсказывает зоны измерения и формирует отчёт за 8–12 минут.",
            style = MaterialTheme.typography.titleMedium,
            lineHeight = 28.sp,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
        )
    }
}

@Composable
private fun SelectedVehicleSummary(descriptor: VehicleType) {
    val spacing = KioskTokens.spacing
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = descriptor.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = vehicleTypeDescription(descriptor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Сумма к оплате",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${descriptor.price} ₽",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Выдача после QR-оплаты",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ContactRequestPanel(
    isCompactLayout: Boolean,
    phone: String,
    email: String,
    phoneTouched: Boolean,
    emailTouched: Boolean,
    phoneValidation: ContactValidationResult,
    emailValidation: ContactValidationResult,
    onPhoneChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    highlights: List<ContactHighlight>
) {
    val spacing = KioskTokens.spacing
    if (isCompactLayout) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            ContactFormSection(
                phone = phone,
                email = email,
                phoneTouched = phoneTouched,
                emailTouched = emailTouched,
                phoneValidation = phoneValidation,
                emailValidation = emailValidation,
                onPhoneChanged = onPhoneChanged,
                onEmailChanged = onEmailChanged,
                modifier = Modifier.fillMaxWidth(),
                phoneTestTag = "thk-phone-field",
                emailTestTag = "thk-email-field"
            )
            ContactHighlightsColumn(
                items = highlights,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.lg)
        ) {
            ContactFormSection(
                phone = phone,
                email = email,
                phoneTouched = phoneTouched,
                emailTouched = emailTouched,
                phoneValidation = phoneValidation,
                emailValidation = emailValidation,
                onPhoneChanged = onPhoneChanged,
                onEmailChanged = onEmailChanged,
                modifier = Modifier.weight(1f),
                phoneTestTag = "thk-phone-field",
                emailTestTag = "thk-email-field"
            )
            ContactHighlightsColumn(
                items = highlights,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VehicleTypeGrid(
    types: List<VehicleType>,
    selectedType: VehicleType?,
    onSelect: (VehicleType) -> Unit
) {
    val spacing = KioskTokens.spacing
    LazyVerticalGrid(
        modifier = Modifier.fillMaxWidth(),
        columns = GridCells.Adaptive(minSize = 320.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        userScrollEnabled = false
    ) {
        items(types, key = { it.name }) { type ->
            VehicleTypeCard(
                type = type,
                isSelected = selectedType == type,
                onClick = { onSelect(type) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun VehicleTypeCard(
    type: VehicleType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    Surface(
        modifier = modifier
            .testTag("vehicle-type-${type.name.lowercase()}")
            .clickable { onClick() }
            .heightIn(min = 240.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (isSelected) 10.dp else 2.dp,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = type.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = vehicleTypeDescription(type),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                tonalElevation = if (isSelected) 8.dp else 2.dp,
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${type.price} ₽",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${vehicleTypeZoneLabel(type)} + PDF",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "Коснитесь, чтобы выбрать тип кузова",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ThicknessInputMetaGrid(
    selectedType: VehicleType?,
    isContactComplete: Boolean
) {
    val spacing = KioskTokens.spacing
    val chips = listOf(
        MetaEntry("Шаг", "1 / 4", "thk-meta-step"),
        MetaEntry("Тип кузова", selectedType?.displayName ?: "Не выбран", "thk-meta-type"),
        MetaEntry(
            label = "Стоимость",
            value = selectedType?.let { "${it.price} ₽" } ?: "—",
            tag = "thk-meta-price"
        ),
        MetaEntry(
            label = "Контакты",
            value = if (isContactComplete) "Готово" else "Заполните поля",
            tag = "thk-meta-contacts"
        )
    )

    LazyVerticalGrid(
        modifier = Modifier.fillMaxWidth(),
        columns = GridCells.Adaptive(minSize = 220.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        userScrollEnabled = false
    ) {
        items(chips) { chip ->
            ThicknessInputMetaChip(
                label = chip.label,
                value = chip.value,
                modifier = Modifier.testTag(chip.tag)
            )
        }
    }
}

@Composable
private fun ThicknessInputMetaChip(label: String, value: String, modifier: Modifier = Modifier) {
    val spacing = KioskTokens.spacing
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun vehicleTypeDescription(type: VehicleType): String = when (type) {
    VehicleType.SEDAN -> "Седан/Хэтчбек • 40 точек"
    VehicleType.MINIVAN -> "Минивэн • 48 точек"
    VehicleType.SUV -> "SUV/Кроссовер • 60 точек"
}

private fun vehicleTypeZoneLabel(type: VehicleType): String = when (type) {
    VehicleType.SEDAN -> "40 зон"
    VehicleType.MINIVAN -> "48 зон"
    VehicleType.SUV -> "60 зон"
}

private data class MetaEntry(
    val label: String,
    val value: String,
    val tag: String
)

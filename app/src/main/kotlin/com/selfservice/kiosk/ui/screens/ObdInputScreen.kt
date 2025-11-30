package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.selfservice.kiosk.ui.state.VehicleBrand
import com.selfservice.kiosk.ui.validation.normalizePhoneNumber
import com.selfservice.kiosk.ui.validation.validateEmailAddress
import com.selfservice.kiosk.ui.validation.validatePhoneNumber
import com.selfservice.platform.ui.components.KioskActionRow
import com.selfservice.platform.ui.components.KioskPanel
import com.selfservice.platform.ui.components.KioskPrimaryButton
import com.selfservice.platform.ui.components.KioskScreenLayout
import com.selfservice.platform.ui.components.KioskTwoColumn
import com.selfservice.platform.ui.foundation.KioskTokens

private const val OBD_SERVICE_PRICE = 480

@Composable
fun ObdInputScreen(
    onNext: (VehicleBrand, String, String) -> Unit
) {
    var selectedBrand by remember { mutableStateOf<VehicleBrand?>(null) }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneTouched by remember { mutableStateOf(false) }
    var emailTouched by remember { mutableStateOf(false) }

    val phoneValidation = validatePhoneNumber(phone)
    val emailValidation = validateEmailAddress(email)
    val contactComplete = phoneValidation.isValid && emailValidation.isValid
    val isValid = selectedBrand != null && contactComplete

    val spacing = KioskTokens.spacing
    val background = KioskTokens.gradients.hero
    val brands = remember { VehicleBrand.values().toList() }
    val contactHighlights = remember {
        listOf(
            ContactHighlight(
                title = "PDF и SMS",
                description = "Отправляем расшифровку DTC и статус Clear DTC"
            ),
            ContactHighlight(
                title = "Лента событий",
                description = "Сохраняем время подключения и результат сканирования"
            ),
            ContactHighlight(
                title = "Удаляем контакты",
                description = "Храним телефон и email не дольше 30 дней"
            )
        )
    }
    val checklist = remember {
        listOf(
            "Зажигание во включенном положении (IGN ON)",
            "Двигатель может быть заглушен, но питание должно быть"
        )
    }
    val isCompactLayout = LocalConfiguration.current.screenWidthDp < 960
    val scrollState = rememberScrollState()

    KioskScreenLayout(
        modifier = Modifier
            .fillMaxSize()
            .testTag("obd-input-screen")
            .verticalScroll(scrollState),
        background = background,
        contentDescription = "Экран выбора марки и ввода контактов для диагностики OBD-II"
    ) {
        ObdInputHeader()

        ObdInputMetaGrid(
            selectedBrand = selectedBrand,
            contactComplete = contactComplete
        )

        KioskTwoColumn(
            verticalAlignment = Alignment.Top,
            horizontalSpacing = spacing.xl,
            primaryContent = {
                ObdInfoPanel(modifier = Modifier.fillMaxWidth())
            },
            secondaryContent = {
                if (selectedBrand != null) {
                    SelectedBrandSummary(brand = selectedBrand)
                } else {
                    DevicePrepReminder()
                }
            }
        )

        KioskPanel(
            headline = "Марка автомобиля",
            supportingText = "Выберите бренд, чтобы адаптер настроился на нужный протокол",
            contentDescription = "Выбор марки для диагностики"
        ) {
            ObdBrandGrid(
                brands = brands,
                selectedBrand = selectedBrand,
                onSelect = { selectedBrand = it },
                isCompactLayout = isCompactLayout
            )
        }

        KioskPanel(
            headline = "Куда отправить отчёт?",
            supportingText = "Контакты нужны только для PDF и уведомлений."
        ) {
            if (isCompactLayout) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
                    ContactFormSection(
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
                        modifier = Modifier.fillMaxWidth(),
                        phoneTestTag = "obd-phone-field",
                        emailTestTag = "obd-email-field"
                    )
                    ContactHighlightsColumn(
                        items = contactHighlights,
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
                        onPhoneChanged = {
                            phone = it
                            if (!phoneTouched) phoneTouched = true
                        },
                        onEmailChanged = {
                            email = it
                            if (!emailTouched) emailTouched = true
                        },
                        modifier = Modifier.weight(1f),
                        phoneTestTag = "obd-phone-field",
                        emailTestTag = "obd-email-field"
                    )
                    ContactHighlightsColumn(
                        items = contactHighlights,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        KioskPanel(
            headline = "Перед подключением",
            supportingText = "Эти шаги экономят до 2 минут сканирования"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                checklist.forEach { item ->
                    Text(
                        text = "• $item",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        KioskActionRow(
            alignment = Alignment.End,
            contentDescription = "Переход к оплате"
        ) {
            KioskPrimaryButton(
                onClick = {
                    val brand = selectedBrand ?: return@KioskPrimaryButton
                    onNext(brand, normalizePhoneNumber(phone), email.trim())
                },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("obd-continue-button")
            ) {
                Text("Перейти к оплате")
            }
        }
    }
}

@Composable
private fun ObdInputHeader() {
    val spacing = KioskTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = "Диагностика OBD-II",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = "Читаем и расшифровываем коды ошибок, при необходимости выполняем Clear DTC.",
            style = MaterialTheme.typography.titleMedium,
            lineHeight = 28.sp,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
        )
    }
}

@Composable
private fun ObdInputMetaGrid(
    selectedBrand: VehicleBrand?,
    contactComplete: Boolean
) {
    val spacing = KioskTokens.spacing
    val design = KioskTokens.design
    val chips = listOf(
        ObdMetaEntry("Шаг", "1 / 5"),
        ObdMetaEntry("Марка", selectedBrand?.displayName ?: "Не выбрана"),
        ObdMetaEntry("Стоимость", "${OBD_SERVICE_PRICE} ₽"),
        ObdMetaEntry("Контакты", if (contactComplete) "Готово" else "Заполните поля")
    )

    LazyVerticalGrid(
        modifier = Modifier.fillMaxWidth(),
        columns = GridCells.Adaptive(minSize = 220.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        userScrollEnabled = false
    ) {
        items(chips) { chip ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = design.surfaces.panel,
                border = BorderStroke(1.dp, design.surfaces.outlineMuted)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Text(
                        text = chip.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = chip.value,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ObdBrandGrid(
    brands: List<VehicleBrand>,
    selectedBrand: VehicleBrand?,
    onSelect: (VehicleBrand) -> Unit,
    isCompactLayout: Boolean
) {
    val spacing = KioskTokens.spacing
    val columns = if (isCompactLayout) {
        GridCells.Adaptive(minSize = 220.dp)
    } else {
        GridCells.Fixed(3)
    }
    LazyVerticalGrid(
        modifier = Modifier.fillMaxWidth(),
        columns = columns,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        userScrollEnabled = false
    ) {
        items(brands, key = { it.name }) { brand ->
            ObdBrandCard(
                brand = brand,
                isSelected = selectedBrand == brand,
                onClick = { onSelect(brand) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ObdBrandCard(
    brand: VehicleBrand,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    val design = KioskTokens.design
    Surface(
        modifier = modifier
            .heightIn(min = 160.dp)
            .clickable { onClick() }
            .testTag("obd-brand-${brand.name.lowercase()}") ,
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (isSelected) 12.dp else 4.dp,
        color = design.surfaces.panel,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                design.surfaces.heroPillBorderAccent
            } else {
                design.surfaces.outlineMuted
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Text(
                text = brand.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = brandDescription(brand),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                tonalElevation = if (isSelected) 8.dp else 2.dp,
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                }
            ) {
                Text(
                    text = "Поддержка CAN ISO 15765",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md, vertical = spacing.xs)
                )
            }
        }
    }
}

@Composable
private fun SelectedBrandSummary(brand: VehicleBrand) {
    val spacing = KioskTokens.spacing
    KioskPanel(
        headline = brand.displayName,
        supportingText = brandSummaryDescription(brand),
        contentDescription = "Выбранная марка для диагностики"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = "Настраиваем адаптер под бренд",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Покрытие CAN, K-Line и брендовые профили",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Сумма",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${OBD_SERVICE_PRICE} ₽",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Выдача адаптера после оплаты",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DevicePrepReminder() {
    val spacing = KioskTokens.spacing
    KioskPanel(
        headline = "Что будет дальше",
        supportingText = "Выберите марку, чтобы подготовить адаптер"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                text = "1. Настроим протокол и скорость",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "2. Откроем слот и подключим адаптер",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "3. Начнём сканирование сразу после оплаты",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ObdInfoPanel(modifier: Modifier = Modifier) {
    val spacing = KioskTokens.spacing
    KioskPanel(
        modifier = modifier,
        headline = "Как выглядит диагностика",
        supportingText = "Обновляем статус каждые 10 секунд"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            InfoRow(label = "Подготовка", value = "до 3 мин")
            InfoRow(label = "Сканирование", value = "до 90 сек")
            InfoRow(label = "Ошибки и отчёт", value = "сразу после сканирования")
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

private fun brandDescription(brand: VehicleBrand): String = when (brand) {
    VehicleBrand.TOYOTA, VehicleBrand.LEXUS -> "Поддержка Techstream-профилей"
    VehicleBrand.HYUNDAI, VehicleBrand.KIA -> "CAN и K-Line, адаптация к i30/Solaris"
    VehicleBrand.BMW -> "BMW-специфические DTC + Clear DTC"
    VehicleBrand.MERCEDES -> "MB 204/205 совместимость"
    VehicleBrand.AUDI, VehicleBrand.VOLKSWAGEN -> "VAG CAN 500 кбит/с"
    VehicleBrand.OTHER -> "Выбирайте, если марки нет в списке"
}

private fun brandSummaryDescription(brand: VehicleBrand): String = when (brand) {
    VehicleBrand.OTHER -> "Мы покажем общую диагностику для большинства CAN авто"
    else -> "Настраиваем адаптер под ${brand.displayName}"
}

private data class ObdMetaEntry(
    val label: String,
    val value: String
)

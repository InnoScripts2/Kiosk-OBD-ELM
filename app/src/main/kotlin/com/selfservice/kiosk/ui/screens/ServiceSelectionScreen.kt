package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfservice.kiosk.ui.state.ServiceCatalog
import com.selfservice.platform.ui.components.KioskPanel
import com.selfservice.platform.ui.components.KioskPrimaryButton
import com.selfservice.platform.ui.components.KioskScreenLayout
import com.selfservice.platform.ui.foundation.KioskTokens

/**
 * Экран 3: Service Selection (Выбор услуги)
 * Выбор между толщиномером и диагностикой
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServiceSelectionScreen(
    onSelectThickness: () -> Unit,
    onSelectObd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    val backgroundColor = MaterialTheme.colorScheme.background
    val services = remember { ServiceCatalog.primaryServices() }
    val solidBackground = remember(backgroundColor) {
        Brush.linearGradient(listOf(backgroundColor, backgroundColor))
    }

    KioskScreenLayout(
        modifier = modifier.fillMaxSize(),
        background = solidBackground,
        verticalSpacing = spacing.xl
    ) {
        ServiceSelectionHeader()

        KioskPanel(
            headline = "Как выглядит сессия",
            supportingText = "Фиксируем одинаковый сценарий: выбор → оплата → отчёт"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Длительность 8–12 мин",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Сессия сбрасывается через 5 мин бездействия",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyVerticalGrid(
            modifier = Modifier.fillMaxWidth(),
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
            userScrollEnabled = false
        ) {
            items(services, key = { it.id }) { descriptor ->
                val handler = when (descriptor.id) {
                    ServiceCatalog.ServiceId.THICKNESS -> onSelectThickness
                    ServiceCatalog.ServiceId.OBD -> onSelectObd
                }
                ServiceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("service-card-${descriptor.id.name.lowercase()}"),
                    descriptor = descriptor,
                    onSelect = handler
                )
            }
        }
    }
}

@Composable
private fun ServiceSelectionHeader() {
    val spacing = KioskTokens.spacing
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        Text(
            text = "Выберите услугу",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Каждый сценарий занимает 8–12 минут. Киоск пошагово подсказывает действия и автоматически формирует отчёт.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = spacing.xl)
        )
    }
}

@Composable
private fun ServiceCard(
    modifier: Modifier = Modifier,
    descriptor: ServiceCatalog.ServiceDescriptor,
    onSelect: () -> Unit
) {
    val spacing = KioskTokens.spacing
    Card(
        modifier = modifier,
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 380.dp)
                .padding(spacing.xl),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(
                    text = descriptor.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                ServiceMetaRow(descriptor = descriptor)
                Text(
                    text = descriptor.heroLine,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = descriptor.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                ServiceBenefits(benefits = descriptor.sellingPoints)
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                ServicePricePill(price = descriptor.priceLabel)
                Text(
                    text = descriptor.complianceNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                KioskPrimaryButton(
                    onClick = onSelect,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Выбрать", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun ServiceMetaRow(descriptor: ServiceCatalog.ServiceDescriptor) {
    val spacing = KioskTokens.spacing
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        ServiceMetaChip(label = "Длительность", value = descriptor.durationHint)
        ServiceMetaChip(label = "Шаги", value = "Выбор · Оплата · Отчёт")
    }
}

@Composable
private fun ServiceMetaChip(label: String, value: String) {
    val spacing = KioskTokens.spacing
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = spacing.md, vertical = spacing.sm),
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
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ServiceBenefits(benefits: List<String>) {
    val spacing = KioskTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        benefits.forEach { benefit ->
            Text(
                text = "• $benefit",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ServicePricePill(price: String) {
    val spacing = KioskTokens.spacing
    Surface(
        tonalElevation = 6.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
    ) {
        Box(
            modifier = Modifier
                .width(200.dp)
                .padding(vertical = spacing.sm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = price,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

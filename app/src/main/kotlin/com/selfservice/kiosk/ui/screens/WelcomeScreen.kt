package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.selfservice.kiosk.ui.state.PromoHighlightsCatalog
import com.selfservice.platform.ui.components.KioskActionRow
import com.selfservice.platform.ui.components.KioskPanel
import com.selfservice.platform.ui.components.KioskPrimaryButton
import com.selfservice.platform.ui.components.KioskScreenLayout
import com.selfservice.platform.ui.components.KioskSecondaryButton
import com.selfservice.platform.ui.components.KioskTwoColumn
import com.selfservice.platform.ui.foundation.KioskTokens

/**
 * Экран 2: Welcome (Приветствие)
 * Приветствие и пользовательское соглашение
 */
@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    onOpenTerms: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var agreed by remember { mutableStateOf(false) }
    val steps = remember { PromoHighlightsCatalog.welcomeSteps() }
    val clauses = remember { PromoHighlightsCatalog.agreementClauses() }
    val spacing = KioskTokens.spacing
    val background = KioskTokens.gradients.hero

    KioskScreenLayout(
        modifier = modifier
            .fillMaxSize()
            .testTag("welcome-screen"),
        background = background,
        verticalSpacing = spacing.xl
    ) {
        WelcomeHero(steps = steps, onOpenTerms = onOpenTerms)

        KioskPanel(
            headline = "Главное об условиях",
            supportingText = "Краткое резюме пользовательского соглашения"
        ) {
            ClausesGrid(clauses = clauses)
        }

        ConsentBlock(
            agreed = agreed,
            onCheckedChange = { agreed = it }
        )

        WelcomeCtas(
            agreed = agreed,
            onContinue = onContinue,
            onOpenTerms = onOpenTerms
        )
    }
}

@Composable
private fun WelcomeHero(
    steps: List<PromoHighlightsCatalog.PromoHighlight>,
    onOpenTerms: () -> Unit
) {
    val spacing = KioskTokens.spacing
    KioskTwoColumn(
        horizontalSpacing = spacing.xxl,
        verticalAlignment = Alignment.Top,
        primaryContent = {
            Text(
                text = "Самообслуживание",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Проверьте автомобиль за 8–12 минут",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                lineHeight = 44.sp,
                color = MaterialTheme.colorScheme.onPrimary
            )

            Text(
                text = "Киоск выдаёт оборудование, показывает подсказки и формирует отчёт. Вы проходите строгую процедуру без очередей и звонков.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
            )

            KioskSecondaryButton(onClick = onOpenTerms) {
                Text("Открыть полные условия")
            }
        },
        secondaryContent = {
            KioskPanel(
                modifier = Modifier.fillMaxWidth(),
                headline = "Как всё проходит"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    steps.forEach { step ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StepBadge(label = step.accent)
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                Text(
                                    text = step.title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = step.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun StepBadge(label: String) {
    val spacing = KioskTokens.spacing
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ClausesGrid(clauses: List<String>) {
    val spacing = KioskTokens.spacing
    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("welcome-clauses"),
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
        userScrollEnabled = false
    ) {
        items(clauses, key = { it }) { clause ->
            ClauseCard(text = clause)
        }
    }
}

@Composable
private fun ClauseCard(text: String) {
    val spacing = KioskTokens.spacing
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm)
        )
    }
}

@Composable
private fun ConsentBlock(
    agreed: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val spacing = KioskTokens.spacing
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
        modifier = Modifier.testTag("welcome-consent-block")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = agreed,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag("welcome-consent-checkbox")
            )
            Text(
                text = "Я принимаю условия обслуживания",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun WelcomeCtas(
    agreed: Boolean,
    onContinue: () -> Unit,
    onOpenTerms: () -> Unit
) {
    val spacing = KioskTokens.spacing
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        KioskActionRow(
            spacing = spacing.md,
            alignment = Alignment.CenterHorizontally
        ) {
            KioskPrimaryButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("welcome-continue"),
                onClick = onContinue,
                enabled = agreed
            ) {
                Text("Продолжить", fontSize = 18.sp)
            }

            KioskSecondaryButton(
                modifier = Modifier.weight(1f),
                onClick = onOpenTerms
            ) {
                Text("Прочитать позже")
            }
        }

        Text(
            text = "Продолжая, вы подтверждаете, что будете бережно обращаться с оборудованием",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

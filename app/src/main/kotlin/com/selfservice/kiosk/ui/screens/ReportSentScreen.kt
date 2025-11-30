package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfservice.platform.ui.components.KioskActionRow
import com.selfservice.platform.ui.components.KioskGhostButton
import com.selfservice.platform.ui.components.KioskPanel
import com.selfservice.platform.ui.components.KioskPrimaryButton
import com.selfservice.platform.ui.components.KioskScreenLayout
import com.selfservice.platform.ui.components.KioskSecondaryButton
import com.selfservice.platform.ui.foundation.KioskTokens

@Composable
fun ReportSentScreen(
    onDone: () -> Unit,
    helperTitle: String = "Отчёт отправлен",
    helperBody: String = "Мы отправили PDF и SMS по указанным контактам",
    subtitle: String = "Можно закрыть сессию или вернуться на главный экран"
) {
    val spacing = KioskTokens.spacing
    val background = KioskTokens.gradients.hero

    KioskScreenLayout(
        modifier = Modifier
            .background(background)
            .testTag("report-sent-screen"),
        contentDescription = "Экран подтверждения отправки отчёта"
    ) {
        ReportSuccessHero(
            helperTitle = helperTitle,
            helperBody = helperBody,
            subtitle = subtitle
        )

        KioskPanel(
            headline = "Что мы сохранили",
            supportingText = "В течение 30 дней можно повторно отправить отчёт из панели администратора",
            contentDescription = "Информация о каналах доставки"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                ReportMetaRow(
                    label = "Email",
                    value = "PDF / HTML ссылка",
                    helper = "письмо с отчётом"
                )
                ReportMetaRow(
                    label = "SMS",
                    value = "Статус Clear DTC",
                    helper = "краткое summary"
                )
                ReportMetaRow(
                    label = "Панель",
                    value = "История операций",
                    helper = "можно переслать"
                )
            }
        }

        KioskActionRow(
            spacing = spacing.md,
            alignment = Alignment.CenterHorizontally,
            contentDescription = "Действия после отправки отчёта"
        ) {
            KioskPrimaryButton(
                modifier = Modifier.weight(1f),
                onClick = onDone,
                contentDescription = "Вернуться на главный экран"
            ) {
                Text(
                    text = "К ГЛАВНОМУ ЭКРАНУ",
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.12.sp)
                )
            }
            KioskSecondaryButton(
                modifier = Modifier.weight(1f),
                onClick = onDone,
                contentDescription = "Завершить сессию"
            ) {
                Text(
                    text = "ЗАВЕРШИТЬ СЕССИЮ",
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.12.sp)
                )
            }
            KioskGhostButton(
                modifier = Modifier.weight(1f),
                onClick = onDone,
                contentDescription = "Повторно отправить отчёт"
            ) {
                Text(
                    text = "ПОВТОРНО ОТПРАВИТЬ",
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.12.sp)
                )
            }
        }
    }
}

@Composable
private fun ReportSuccessHero(
    helperTitle: String,
    helperBody: String,
    subtitle: String
) {
    val spacing = KioskTokens.spacing
    val design = KioskTokens.design
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("report-success-hero"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.lg)
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = design.surfaces.panel,
            border = BorderStroke(1.dp, design.surfaces.heroPillBorderAccent)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Text(
                text = helperTitle,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = helperBody,
                style = MaterialTheme.typography.titleMedium,
                color = design.colors.textMutedOnDark,
                textAlign = TextAlign.Center
            )
        }
        Surface(
            modifier = Modifier.clip(MaterialTheme.shapes.large),
            color = design.surfaces.panel,
            border = BorderStroke(1.dp, design.surfaces.outlineMuted)
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.xl, vertical = spacing.md)
            )
        }
    }
}

@Composable
private fun ReportMetaRow(
    label: String,
    value: String,
    helper: String
) {
    val spacing = KioskTokens.spacing
    val design = KioskTokens.design
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(design.surfaces.panel)
            .padding(spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = design.colors.textMutedOnDark
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = helper,
            style = MaterialTheme.typography.bodySmall,
            color = design.colors.textMutedOnDark,
            textAlign = TextAlign.End
        )
    }
}

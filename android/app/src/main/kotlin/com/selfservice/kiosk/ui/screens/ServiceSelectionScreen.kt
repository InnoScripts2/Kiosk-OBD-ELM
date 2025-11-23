package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Экран 3: Service Selection (Выбор услуги)
 * Выбор между толщиномером и диагностикой
 */
@Composable
fun ServiceSelectionScreen(
    onSelectThickness: () -> Unit,
    onSelectObd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Text(
            text = "Выберите услугу",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Thickness card
            ServiceCard(
                modifier = Modifier.weight(1f),
                title = "Толщиномер ЛКП",
                description = "Измерение толщины лакокрасочного покрытия на 60 точках кузова",
                price = "от 350₽",
                onClick = onSelectThickness
            )
            
            // OBD card
            ServiceCard(
                modifier = Modifier.weight(1f),
                title = "OBD-II Диагностика",
                description = "Полная диагностика систем автомобиля через разъём OBD-II",
                price = "480₽",
                onClick = onSelectObd
            )
        }
    }
}

@Composable
private fun ServiceCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    price: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            Text(
                text = price,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Выбрать", fontSize = 18.sp)
            }
        }
    }
}

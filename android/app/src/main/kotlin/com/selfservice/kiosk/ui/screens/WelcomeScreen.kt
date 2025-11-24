package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Экран 2: Welcome (Приветствие)
 * Приветствие и пользовательское соглашение
 */
@Composable
fun WelcomeScreen(
    onContinue: () -> Unit
) {
    var agreed by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Добро пожаловать!",
                style = MaterialTheme.typography.headlineLarge
            )
            
            Text(
                text = "Этот киоск предоставляет услуги диагностики автомобиля и измерения толщины лакокрасочного покрытия.",
                style = MaterialTheme.typography.bodyLarge
            )
            
            Text(
                text = "Пользовательское соглашение",
                style = MaterialTheme.typography.titleMedium
            )
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = """
                            1. Услуги предоставляются в режиме самообслуживания
                            2. Вы несёте ответственность за сохранность выданного оборудования
                            3. Персональные данные используются только для отправки отчётов
                            4. Данные хранятся не более 30 дней
                            5. Оплата производится до начала оказания услуги
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = agreed,
                    onCheckedChange = { agreed = it }
                )
                Text(
                    text = "Я согласен с условиями",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        Button(
            onClick = onContinue,
            enabled = agreed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Продолжить", fontSize = 18.sp)
        }
    }
}

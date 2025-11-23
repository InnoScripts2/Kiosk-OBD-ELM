package com.selfservice.kiosk.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DevicePrepScreen(
    deviceName: String,
    onReady: () -> Unit
) {
    var progress by remember { mutableStateOf(0f) }
    
    LaunchedEffect(Unit) {
        for (i in 1..100) {
            kotlinx.coroutines.delay(50)
            progress = i / 100f
        }
        kotlinx.coroutines.delay(500)
        onReady()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Подготавливаем $deviceName",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.titleLarge
        )
        
        if (progress >= 1f) {
            Spacer(modifier = Modifier.height(32.dp))
            Text("Выньте устройство из слота выдачи")
        }
    }
}

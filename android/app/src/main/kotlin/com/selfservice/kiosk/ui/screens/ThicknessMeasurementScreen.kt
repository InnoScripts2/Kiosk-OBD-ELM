package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.selfservice.kiosk.ui.viewmodels.ThicknessFlowViewModel
import com.selfservice.kiosk.ui.state.MeasurementStatus

@Composable
fun ThicknessMeasurementScreen(
    viewModel: ThicknessFlowViewModel,
    onComplete: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    
    if (state.isComplete) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1000)
            onComplete()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Измерения (${state.completedPoints} / ${state.totalPoints})",
            style = MaterialTheme.typography.headlineMedium
        )
        
        LinearProgressIndicator(
            progress = state.progress,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        )
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(60) { index ->
                val zone = "Z${index + 1}"
                val measurement = state.measurements[zone]
                
                Surface(
                    modifier = Modifier.aspectRatio(1f),
                    tonalElevation = 2.dp,
                    color = when (measurement?.status) {
                        MeasurementStatus.Normal -> Color.Green.copy(alpha = 0.3f)
                        MeasurementStatus.Warning -> Color.Yellow.copy(alpha = 0.3f)
                        MeasurementStatus.Critical -> Color.Red.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = measurement?.value?.toInt()?.toString() ?: zone,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

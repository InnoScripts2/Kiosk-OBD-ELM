package com.selfservice.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Compose рендерер отчётов.
 * 
 * Отображает отчёты толщиномера и диагностики в Compose UI
 * для предпросмотра перед отправкой клиенту.
 * 
 * Дизайн:
 * - Симметричный layout с акцентными цветами (#00C4B4 для толщиномера, #FFC857 для OBD)
 * - Крупная типографика для сенсорных экранов
 * - WCAG AA accessibility (контраст 4.5:1)
 * - DEV-режим бейдж в правом верхнем углу
 */
object ComposeReportRenderer {
    
    /**
     * Отображает отчёт толщиномера.
     */
    @Composable
    fun ThicknessReport(
        input: ThicknessReportInput,
        devMode: Boolean = false,
        modifier: Modifier = Modifier
    ) {
        val scrollState = rememberScrollState()
        
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ColorScheme.Background)
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            // Header
            ReportHeader(
                title = "Отчёт толщиномера",
                accentColor = ColorScheme.AccentThickness,
                devMode = devMode
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Session info
            InfoCard(
                title = "Информация о сессии",
                items = listOf(
                    "ID сессии" to input.sessionId,
                    "Дата" to formatTimestamp(input.generatedAtMillis),
                    "Тип ТС" to input.vehicleType
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Statistics KPI cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                KpiCard(
                    title = "Среднее",
                    value = "${input.stats.avgValue} µm",
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Отклонений",
                    value = input.stats.deviations.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                KpiCard(
                    title = "Мин",
                    value = "${input.stats.minValue} µm",
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Макс",
                    value = "${input.stats.maxValue} µm",
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Measurements table
            Text(
                text = "Измерения",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ColorScheme.TextPrimary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            input.measurements.forEach { measurement ->
                MeasurementRow(measurement)
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Analysis
            AnalysisCard(
                overallStatus = input.analysis.overallStatus,
                recommendation = input.analysis.recommendation,
                details = input.analysis.details
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Customer info (masked)
            if (input.customer != null) {
                InfoCard(
                    title = "Контакты",
                    items = listOf(
                        "Email" to maskEmail(input.customer.email),
                        "Телефон" to maskPhone(input.customer.phone)
                    )
                )
            }
        }
    }
    
    /**
     * Отображает отчёт диагностики OBD-II.
     */
    @Composable
    fun DiagnosticsReport(
        input: DiagnosticsReportInput,
        devMode: Boolean = false,
        modifier: Modifier = Modifier
    ) {
        val scrollState = rememberScrollState()
        
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ColorScheme.Background)
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            // Header
            ReportHeader(
                title = "Диагностический отчёт",
                accentColor = ColorScheme.AccentDiagnostics,
                devMode = devMode
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Vehicle info
            InfoCard(
                title = "Информация об автомобиле",
                items = listOf(
                    "Марка" to "${input.vehicle.make} ${input.vehicle.model}",
                    "Год" to input.vehicle.year.toString(),
                    "VIN" to maskVin(input.vehicle.vin)
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Metrics summary
            val metrics = input.snapshot.metrics
            val criticalCount = metrics.count { 
                it.status.toString().contains("CRITICAL") 
            }
            val warningCount = metrics.count { 
                it.status.toString().contains("WARNING") 
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                KpiCard(
                    title = "Всего метрик",
                    value = metrics.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Критичных",
                    value = criticalCount.toString(),
                    backgroundColor = if (criticalCount > 0) ColorScheme.StatusCritical else Color.Transparent,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Recommendations
            if (input.recommendations.isNotEmpty()) {
                Text(
                    text = "Рекомендации",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorScheme.TextPrimary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                input.recommendations.forEach { rec ->
                    RecommendationCard(rec)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
    
    @Composable
    private fun ReportHeader(
        title: String,
        accentColor: Color,
        devMode: Boolean
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            
            if (devMode) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = ColorScheme.DevBadge
                ) {
                    Text(
                        text = "[МОК-РЕЖИМ]",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorScheme.Background,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
    
    @Composable
    private fun InfoCard(
        title: String,
        items: List<Pair<String, String>>
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ColorScheme.CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorScheme.TextPrimary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                items.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            color = ColorScheme.TextSecondary
                        )
                        Text(
                            text = value,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorScheme.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
    
    @Composable
    private fun KpiCard(
        title: String,
        value: String,
        backgroundColor: Color = ColorScheme.CardBackground,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    color = ColorScheme.TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorScheme.TextPrimary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    
    @Composable
    private fun MeasurementRow(measurement: ThicknessMeasurement) {
        val statusColor = when (measurement.status) {
            MeasurementStatus.OK -> ColorScheme.StatusOk
            MeasurementStatus.WARNING -> ColorScheme.StatusWarning
            MeasurementStatus.CRITICAL -> ColorScheme.StatusCritical
            MeasurementStatus.EMPTY -> ColorScheme.TextSecondary
            MeasurementStatus.ERROR -> ColorScheme.StatusError
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ColorScheme.CardBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = measurement.zone,
                    fontSize = 14.sp,
                    color = ColorScheme.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = "${measurement.value} µm",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = measurement.status.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
    
    @Composable
    private fun AnalysisCard(
        overallStatus: OverallStatus,
        recommendation: String,
        details: List<String>
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ColorScheme.CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Анализ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorScheme.TextPrimary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                val statusColor = when (overallStatus) {
                    OverallStatus.EXCELLENT -> ColorScheme.StatusOk
                    OverallStatus.GOOD -> ColorScheme.StatusOk
                    OverallStatus.ATTENTION_NEEDED -> ColorScheme.StatusWarning
                    OverallStatus.CRITICAL -> ColorScheme.StatusCritical
                }
                
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = overallStatus.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = recommendation,
                    fontSize = 14.sp,
                    color = ColorScheme.TextPrimary
                )
                
                if (details.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    details.forEach { detail ->
                        Text(
                            text = "• $detail",
                            fontSize = 13.sp,
                            color = ColorScheme.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
    
    @Composable
    private fun RecommendationCard(recommendation: com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendation) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (recommendation.severity.name) {
                    "CRITICAL" -> ColorScheme.StatusCritical.copy(alpha = 0.1f)
                    "WARNING" -> ColorScheme.StatusWarning.copy(alpha = 0.1f)
                    else -> ColorScheme.CardBackground
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = recommendation.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorScheme.TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = recommendation.message,
                    fontSize = 14.sp,
                    color = ColorScheme.TextPrimary
                )
            }
        }
    }
    
    // Helper functions
    private fun formatTimestamp(millis: Long): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "RU"))
        return formatter.format(Date(millis))
    }
    
    private fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return email
        val local = parts[0]
        val masked = if (local.length > 3) {
            "${local.take(2)}***${local.takeLast(1)}"
        } else {
            "***"
        }
        return "$masked@${parts[1]}"
    }
    
    private fun maskPhone(phone: String): String {
        return if (phone.length > 4) {
            "${phone.take(2)}***${phone.takeLast(2)}"
        } else {
            "***"
        }
    }
    
    private fun maskVin(vin: String): String {
        return if (vin.length > 8) {
            "${vin.take(4)}***${vin.takeLast(4)}"
        } else {
            "***"
        }
    }
}

/**
 * Цветовая схема отчётов (соответствует DesignTokens).
 */
private object ColorScheme {
    val Background = Color(0xFF0B0D17)
    val CardBackground = Color(0xFF1A1D2E)
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFB0B0B0)
    val AccentThickness = Color(0xFF00C4B4)
    val AccentDiagnostics = Color(0xFFFFC857)
    val StatusOk = Color(0xFF4CAF50)
    val StatusWarning = Color(0xFFFF9800)
    val StatusCritical = Color(0xFFF44336)
    val StatusError = Color(0xFF9E9E9E)
    val DevBadge = Color(0xFFFF5722)
}

/**
 * Extension для отображаемого имени статуса.
 */
private val OverallStatus.displayName: String
    get() = when (this) {
        OverallStatus.EXCELLENT -> "Отлично"
        OverallStatus.GOOD -> "Хорошо"
        OverallStatus.ATTENTION_NEEDED -> "Требуется внимание"
        OverallStatus.CRITICAL -> "Критично"
    }

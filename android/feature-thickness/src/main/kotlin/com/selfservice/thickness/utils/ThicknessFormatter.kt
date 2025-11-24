package com.selfservice.thickness.utils

import com.selfservice.thickness.models.MeasurementClassification
import com.selfservice.thickness.models.ThicknessAnalysis
import com.selfservice.thickness.models.ThicknessReport
import com.selfservice.thickness.models.ZoneMeasurement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Форматтеры данных толщиномера для отображения и экспорта
 * 
 * @since Session 12B
 */

/**
 * Форматтер значений измерений
 */
object ThicknessFormatter {
    
    /**
     * Форматировать значение измерения для отображения
     * 
     * @param value Значение в микронах
     * @param includeUnit Добавить единицу измерения
     * @return Отформатированная строка
     */
    fun formatValue(value: Float?, includeUnit: Boolean = true): String {
        if (value == null) return "—"
        
        val formatted = String.format(Locale.US, "%.1f", value)
        return if (includeUnit) "$formatted μm" else formatted
    }
    
    /**
     * Форматировать временную метку
     */
    fun formatTimestamp(timestamp: Long, pattern: String = "dd.MM.yyyy HH:mm:ss"): String {
        val date = Date(timestamp)
        val formatter = SimpleDateFormat(pattern, Locale("ru", "RU"))
        return formatter.format(date)
    }
    
    /**
     * Форматировать классификацию измерения
     */
    fun formatClassification(classification: MeasurementClassification): String {
        return when (classification) {
            MeasurementClassification.FACTORY -> "Заводская покраска"
            MeasurementClassification.MINOR_REPAINT -> "Незначительная перекраска"
            MeasurementClassification.MAJOR_REPAINT -> "Значительная перекраска"
            MeasurementClassification.BODY_WORK -> "Кузовной ремонт"
            MeasurementClassification.TOO_THIN -> "Слишком тонкая"
            MeasurementClassification.UNKNOWN -> "Неизвестно"
        }
    }
    
    /**
     * Форматировать диапазон значений
     */
    fun formatRange(classification: MeasurementClassification): String {
        return when (classification) {
            MeasurementClassification.FACTORY -> "60-120 μm"
            MeasurementClassification.MINOR_REPAINT -> "120-180 μm"
            MeasurementClassification.MAJOR_REPAINT -> "180-300 μm"
            MeasurementClassification.BODY_WORK -> ">300 μm"
            MeasurementClassification.TOO_THIN -> "<60 μm"
            MeasurementClassification.UNKNOWN -> "—"
        }
    }
    
    /**
     * Форматировать процент
     */
    fun formatPercentage(value: Float, decimals: Int = 1): String {
        return String.format(Locale.US, "%.${decimals}f%%", value)
    }
    
    /**
     * Форматировать статистику
     */
    fun formatStatistics(
        avg: Float,
        min: Float,
        max: Float,
        stdDev: Float? = null
    ): String {
        return buildString {
            appendLine("Среднее: ${formatValue(avg)}")
            appendLine("Минимум: ${formatValue(min)}")
            appendLine("Максимум: ${formatValue(max)}")
            if (stdDev != null) {
                appendLine("Отклонение: ${formatValue(stdDev)}")
            }
        }.trim()
    }
}

/**
 * Форматтер анализа измерений
 */
object ThicknessAnalysisFormatter {
    
    /**
     * Форматировать полный анализ для отображения
     */
    fun formatAnalysis(analysis: ThicknessAnalysis): String {
        return buildString {
            appendLine("=== АНАЛИЗ ИЗМЕРЕНИЙ ===")
            appendLine()
            appendLine("Среднее значение: ${ThicknessFormatter.formatValue(analysis.avgValue)}")
            appendLine("Минимум: ${ThicknessFormatter.formatValue(analysis.minValue)}")
            appendLine("Максимум: ${ThicknessFormatter.formatValue(analysis.maxValue)}")
            appendLine()
            appendLine("=== РАСПРЕДЕЛЕНИЕ ===")
            appendLine("Заводская покраска: ${analysis.factoryCount} зон")
            appendLine("Перекраска: ${analysis.repaintCount} зон")
            appendLine("Кузовной ремонт: ${analysis.bodyWorkCount} зон")
            appendLine("Всего отклонений: ${analysis.deviations}")
            appendLine()
            appendLine("=== РЕКОМЕНДАЦИЯ ===")
            appendLine(analysis.recommendation)
        }
    }
    
    /**
     * Форматировать краткую сводку
     */
    fun formatSummary(analysis: ThicknessAnalysis): String {
        return buildString {
            append("Среднее: ${ThicknessFormatter.formatValue(analysis.avgValue)}, ")
            append("Отклонений: ${analysis.deviations}")
        }
    }
    
    /**
     * Форматировать детальную таблицу
     */
    fun formatTable(measurements: List<ZoneMeasurement>): String {
        return buildString {
            appendLine("┌─────────────────────────────────┬──────────┬──────────────────────┐")
            appendLine("│ Зона                            │ Значение │ Классификация        │")
            appendLine("├─────────────────────────────────┼──────────┼──────────────────────┤")
            
            measurements.forEach { measurement ->
                val zoneName = measurement.zone.displayName.padEnd(31)
                val value = ThicknessFormatter.formatValue(measurement.value, false).padStart(8)
                val classification = ThicknessFormatter.formatClassification(
                    MeasurementClassification.classify(measurement.value)
                ).padEnd(20)
                
                appendLine("│ $zoneName │ $value │ $classification │")
            }
            
            appendLine("└─────────────────────────────────┴──────────┴──────────────────────┘")
        }
    }
}

/**
 * Форматтер отчётов
 */
object ThicknessReportFormatter {
    
    /**
     * Форматировать полный отчёт для печати
     */
    fun formatFullReport(report: ThicknessReport): String {
        return buildString {
            appendLine("╔═══════════════════════════════════════════════════════════════════╗")
            appendLine("║           ОТЧЁТ ПО ИЗМЕРЕНИЯМ ТОЛЩИНЫ ЛКП                       ║")
            appendLine("╚═══════════════════════════════════════════════════════════════════╝")
            appendLine()
            
            appendLine("Сессия: ${report.sessionId}")
            appendLine("Дата: ${ThicknessFormatter.formatTimestamp(report.timestamp)}")
            appendLine("Тип автомобиля: ${formatVehicleType(report.vehicleType)}")
            appendLine("Всего измерений: ${report.measurements.size}")
            appendLine()
            
            appendLine(ThicknessAnalysisFormatter.formatAnalysis(report.analysis))
            appendLine()
            
            appendLine("=== ДЕТАЛЬНЫЕ ИЗМЕРЕНИЯ ===")
            appendLine(ThicknessAnalysisFormatter.formatTable(report.measurements))
            appendLine()
            
            appendLine("─────────────────────────────────────────────────────────────────────")
            appendLine("Отчёт сгенерирован автоматически")
            appendLine("Киоск самообслуживания © 2025")
        }
    }
    
    /**
     * Форматировать краткий отчёт
     */
    fun formatBriefReport(report: ThicknessReport): String {
        return buildString {
            appendLine("Отчёт #${report.sessionId}")
            appendLine(ThicknessFormatter.formatTimestamp(report.timestamp))
            appendLine("Тип: ${formatVehicleType(report.vehicleType)}")
            appendLine(ThicknessAnalysisFormatter.formatSummary(report.analysis))
        }
    }
    
    /**
     * Форматировать тип автомобиля
     */
    private fun formatVehicleType(type: String): String {
        return when (type.lowercase()) {
            "sedan" -> "Седан"
            "suv" -> "Внедорожник"
            "minivan" -> "Минивэн"
            "hatchback" -> "Хэтчбек"
            "coupe" -> "Купе"
            "wagon" -> "Универсал"
            else -> type
        }
    }
}

/**
 * HTML форматтер для веб-отображения
 */
object ThicknessHtmlFormatter {
    
    /**
     * Генерировать HTML отчёт
     */
    fun generateHtml(report: ThicknessReport): String {
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"ru\">")
            appendLine("<head>")
            appendLine("    <meta charset=\"UTF-8\">")
            appendLine("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            appendLine("    <title>Отчёт по измерениям толщины ЛКП</title>")
            appendLine("    <style>")
            appendLine(generateCss())
            appendLine("    </style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("    <div class=\"container\">")
            appendLine("        <header>")
            appendLine("            <h1>Отчёт по измерениям толщины ЛКП</h1>")
            appendLine("            <p class=\"meta\">")
            appendLine("                Сессия: ${report.sessionId} | ")
            appendLine("                Дата: ${ThicknessFormatter.formatTimestamp(report.timestamp)}")
            appendLine("            </p>")
            appendLine("        </header>")
            appendLine()
            appendLine("        <section class=\"summary\">")
            appendLine("            <h2>Сводка</h2>")
            appendLine("            <div class=\"summary-grid\">")
            appendLine("                <div class=\"summary-item\">")
            appendLine("                    <span class=\"label\">Среднее значение</span>")
            appendLine("                    <span class=\"value\">${ThicknessFormatter.formatValue(report.analysis.avgValue)}</span>")
            appendLine("                </div>")
            appendLine("                <div class=\"summary-item\">")
            appendLine("                    <span class=\"label\">Отклонений от нормы</span>")
            appendLine("                    <span class=\"value\">${report.analysis.deviations}</span>")
            appendLine("                </div>")
            appendLine("                <div class=\"summary-item\">")
            appendLine("                    <span class=\"label\">Заводская покраска</span>")
            appendLine("                    <span class=\"value\">${report.analysis.factoryCount} зон</span>")
            appendLine("                </div>")
            appendLine("                <div class=\"summary-item\">")
            appendLine("                    <span class=\"label\">Перекраска</span>")
            appendLine("                    <span class=\"value\">${report.analysis.repaintCount} зон</span>")
            appendLine("                </div>")
            appendLine("            </div>")
            appendLine("        </section>")
            appendLine()
            appendLine("        <section class=\"recommendation\">")
            appendLine("            <h2>Рекомендация</h2>")
            appendLine("            <p>${report.analysis.recommendation}</p>")
            appendLine("        </section>")
            appendLine()
            appendLine("        <section class=\"measurements\">")
            appendLine("            <h2>Детальные измерения</h2>")
            appendLine("            <table>")
            appendLine("                <thead>")
            appendLine("                    <tr>")
            appendLine("                        <th>Зона</th>")
            appendLine("                        <th>Элемент</th>")
            appendLine("                        <th>Значение</th>")
            appendLine("                        <th>Классификация</th>")
            appendLine("                    </tr>")
            appendLine("                </thead>")
            appendLine("                <tbody>")
            
            report.measurements.forEach { measurement ->
                val classification = MeasurementClassification.classify(measurement.value)
                val cssClass = getClassificationCssClass(classification)
                
                appendLine("                    <tr class=\"$cssClass\">")
                appendLine("                        <td>${measurement.zone.displayName}</td>")
                appendLine("                        <td>${measurement.zone.bodyPart.displayName}</td>")
                appendLine("                        <td>${ThicknessFormatter.formatValue(measurement.value)}</td>")
                appendLine("                        <td>${ThicknessFormatter.formatClassification(classification)}</td>")
                appendLine("                    </tr>")
            }
            
            appendLine("                </tbody>")
            appendLine("            </table>")
            appendLine("        </section>")
            appendLine()
            appendLine("        <footer>")
            appendLine("            <p>Отчёт сгенерирован автоматически</p>")
            appendLine("            <p>Киоск самообслуживания © 2025</p>")
            appendLine("        </footer>")
            appendLine("    </div>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }
    
    /**
     * Генерировать CSS стили
     */
    private fun generateCss(): String {
        return """
            body {
                font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                margin: 0;
                padding: 20px;
                background-color: #f5f5f5;
            }
            .container {
                max-width: 1200px;
                margin: 0 auto;
                background: white;
                padding: 30px;
                border-radius: 8px;
                box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            }
            header {
                border-bottom: 2px solid #00C4B4;
                padding-bottom: 20px;
                margin-bottom: 30px;
            }
            h1 {
                margin: 0;
                color: #333;
            }
            .meta {
                color: #666;
                margin-top: 10px;
            }
            .summary-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                gap: 20px;
                margin-top: 20px;
            }
            .summary-item {
                padding: 15px;
                background: #f9f9f9;
                border-radius: 4px;
            }
            .summary-item .label {
                display: block;
                font-size: 14px;
                color: #666;
                margin-bottom: 5px;
            }
            .summary-item .value {
                display: block;
                font-size: 24px;
                font-weight: bold;
                color: #333;
            }
            .recommendation {
                background: #e8f5e9;
                padding: 20px;
                border-radius: 4px;
                border-left: 4px solid #4CAF50;
                margin: 20px 0;
            }
            table {
                width: 100%;
                border-collapse: collapse;
                margin-top: 20px;
            }
            th {
                background: #00C4B4;
                color: white;
                padding: 12px;
                text-align: left;
                font-weight: 600;
            }
            td {
                padding: 10px 12px;
                border-bottom: 1px solid #ddd;
            }
            tr.factory { background: #E8F5E9; }
            tr.minor-repaint { background: #FFF9C4; }
            tr.major-repaint { background: #FFE0B2; }
            tr.body-work { background: #FFCDD2; }
            tr.too-thin { background: #F5F5F5; }
            footer {
                margin-top: 40px;
                padding-top: 20px;
                border-top: 1px solid #ddd;
                text-align: center;
                color: #666;
                font-size: 14px;
            }
        """.trimIndent()
    }
    
    /**
     * Получить CSS класс для классификации
     */
    private fun getClassificationCssClass(classification: MeasurementClassification): String {
        return when (classification) {
            MeasurementClassification.FACTORY -> "factory"
            MeasurementClassification.MINOR_REPAINT -> "minor-repaint"
            MeasurementClassification.MAJOR_REPAINT -> "major-repaint"
            MeasurementClassification.BODY_WORK -> "body-work"
            MeasurementClassification.TOO_THIN -> "too-thin"
            MeasurementClassification.UNKNOWN -> ""
        }
    }
}

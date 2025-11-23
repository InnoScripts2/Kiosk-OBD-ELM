package com.selfservice.feature.reports

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Формирует HTML-версию отчёта толщиномера.
 * 
 * Структура отчёта:
 * 1. Заголовок с логотипом и метаданными
 * 2. KPI карточки (всего замеров, среднее, отклонения, процент)
 * 3. Тепловая карта (SVG визуализация зон)
 * 4. Таблица измерений (все 40-60 зон)
 * 5. Анализ и рекомендации
 * 6. Контакты клиента (если есть)
 * 
 * Использует:
 * - Акцентный цвет #00C4B4 (teal)
 * - 12-колоночную сетку
 * - SVG иконки и графики
 */
class ThicknessReportHtmlFormatter {
    
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "RU"))
    
    /**
     * Генерирует полный HTML отчёт толщиномера.
     * 
     * @param input входные данные отчёта
     * @param devMode флаг DEV-режима (добавляет бейдж [МОК-РЕЖИМ])
     * @return HTML строка
     */
    fun format(input: ThicknessReportInput, devMode: Boolean = false): String {
        val timestamp = Date(input.generatedAtMillis)
        val formattedDate = dateFormat.format(timestamp)
        
        return buildString {
            append("<!DOCTYPE html>\n")
            append("<html lang=\"ru\">\n")
            append("<head>\n")
            append("<meta charset=\"utf-8\"/>\n")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>\n")
            append("<title>Отчёт толщиномера — ${HtmlStyles.escapeHtml(input.sessionId)}</title>\n")
            append("<style>\n")
            append(HtmlStyles.generateBaseStyles())
            append("\n</style>\n")
            append("</head>\n")
            append("<body>\n")
            
            // DEV режим бейдж
            if (devMode) {
                append("<div class=\"dev-mode-badge\">[МОК-РЕЖИМ]</div>\n")
            }
            
            // Заголовок
            append(renderHeader(input.sessionId, formattedDate, input.vehicleType))
            
            // KPI карточки
            append(renderKpiCards(input.stats))
            
            // Тепловая карта (упрощённая)
            append(renderHeatmap(input.measurements))
            
            // Таблица измерений
            append(renderMeasurementsTable(input.measurements))
            
            // Анализ и рекомендации
            append(renderAnalysis(input.analysis))
            
            // Контакты клиента
            if (input.customer != null) {
                append(renderCustomerContacts(input.customer))
            }
            
            // Футер
            append(renderFooter())
            
            append("</body>\n</html>")
        }
    }
    
    /**
     * Заголовок отчёта.
     */
    private fun renderHeader(sessionId: String, formattedDate: String, vehicleType: String): String {
        return """
            <section class="section">
                <div class="row">
                    <div class="col-2">
                        ${SvgIcons.logo(120, 40)}
                    </div>
                    <div class="col-10">
                        <h1 class="title">Отчёт толщиномера</h1>
                        <p class="meta">
                            <strong>Сессия:</strong> ${HtmlStyles.escapeHtml(sessionId)}<br/>
                            <strong>Дата:</strong> ${HtmlStyles.escapeHtml(formattedDate)}<br/>
                            <strong>Тип автомобиля:</strong> ${HtmlStyles.escapeHtml(vehicleType)}
                        </p>
                    </div>
                </div>
            </section>
        """.trimIndent() + "\n"
    }
    
    /**
     * KPI карточки со статистикой.
     */
    private fun renderKpiCards(stats: ThicknessStats): String {
        return """
            <section class="section">
                <h2>Сводка измерений</h2>
                <div class="row">
                    <div class="col-3">
                        <div class="card card-accent-teal kpi-card">
                            <div class="kpi-value" style="color: ${DesignTokens.Colors.Accent.TEAL};">
                                ${stats.completed} / ${stats.total}
                            </div>
                            <div class="kpi-label">Замеров выполнено</div>
                        </div>
                    </div>
                    <div class="col-3">
                        <div class="card card-accent-teal kpi-card">
                            <div class="kpi-value" style="color: ${DesignTokens.Colors.Accent.TEAL};">
                                ${formatThickness(stats.average)}
                            </div>
                            <div class="kpi-label">Среднее значение</div>
                        </div>
                    </div>
                    <div class="col-3">
                        <div class="card card-accent-teal kpi-card">
                            <div class="kpi-value" style="color: ${statusColor(stats.deviations)};">
                                ${stats.deviations}
                            </div>
                            <div class="kpi-label">Отклонений</div>
                        </div>
                    </div>
                    <div class="col-3">
                        <div class="card card-accent-teal kpi-card">
                            <div class="kpi-value" style="color: ${statusColor(stats.deviations)};">
                                ${formatPercent(stats.deviationPercent)}
                            </div>
                            <div class="kpi-label">% отклонений</div>
                        </div>
                    </div>
                </div>
            </section>
        """.trimIndent() + "\n"
    }
    
    /**
     * Тепловая карта измерений.
     */
    private fun renderHeatmap(measurements: List<ThicknessMeasurement>): String {
        // Берём первые 12 измерений для визуализации
        val topMeasurements = measurements
            .filter { it.status != MeasurementStatus.EMPTY }
            .take(12)
            .map { it.zoneName to it.value }
        
        if (topMeasurements.isEmpty()) {
            return ""
        }
        
        return """
            <section class="section">
                <h2>Тепловая карта покрытия</h2>
                <div class="card">
                    <div style="text-align: center;">
                        ${SvgIcons.carBodyHeatmap(topMeasurements, 600, 400)}
                    </div>
                    <p class="meta text-center mt-sm">
                        Цветовая индикация: 
                        <span style="color: ${DesignTokens.Colors.Status.OK};">■</span> Норма (80-200 µm) 
                        <span style="color: ${DesignTokens.Colors.Status.WARNING};">■</span> Предупреждение (&lt;80 µm) 
                        <span style="color: ${DesignTokens.Colors.Status.CRITICAL};">■</span> Критично (&gt;200 µm)
                    </p>
                </div>
            </section>
        """.trimIndent() + "\n"
    }
    
    /**
     * Таблица всех измерений.
     */
    private fun renderMeasurementsTable(measurements: List<ThicknessMeasurement>): String {
        val rows = measurements.map { measurement ->
            val statusIcon = when (measurement.status) {
                MeasurementStatus.OK -> SvgIcons.checkIcon(16)
                MeasurementStatus.WARNING -> SvgIcons.warningIcon(16)
                MeasurementStatus.CRITICAL -> SvgIcons.errorIcon(16)
                MeasurementStatus.ERROR -> SvgIcons.errorIcon(16, DesignTokens.Colors.Status.EMPTY)
                MeasurementStatus.EMPTY -> "<span style=\"color: ${DesignTokens.Colors.Status.EMPTY};\">—</span>"
            }
            
            val statusBadge = when (measurement.status) {
                MeasurementStatus.OK -> "<span class=\"tag tag-ok\">OK</span>"
                MeasurementStatus.WARNING -> "<span class=\"tag tag-warning\">WARNING</span>"
                MeasurementStatus.CRITICAL -> "<span class=\"tag tag-critical\">CRITICAL</span>"
                MeasurementStatus.ERROR -> "<span class=\"tag tag-critical\">ERROR</span>"
                MeasurementStatus.EMPTY -> "<span class=\"tag tag-empty\">EMPTY</span>"
            }
            
            val valueText = if (measurement.status != MeasurementStatus.EMPTY) {
                formatThickness(measurement.value)
            } else {
                "—"
            }
            
            val comment = measurement.comment?.let { HtmlStyles.escapeHtml(it) } ?: "—"
            
            """
                <tr>
                    <td class="font-mono">${measurement.zoneNumber}</td>
                    <td>${HtmlStyles.escapeHtml(measurement.zoneName)}</td>
                    <td class="font-mono text-right">$valueText</td>
                    <td class="text-center">$statusIcon</td>
                    <td class="text-center">$statusBadge</td>
                    <td>$comment</td>
                </tr>
            """.trimIndent()
        }.joinToString("\n")
        
        return """
            <section class="section">
                <h2>Детальные измерения</h2>
                <table>
                    <thead>
                        <tr>
                            <th>#</th>
                            <th>Зона</th>
                            <th class="text-right">Значение</th>
                            <th class="text-center">Индикатор</th>
                            <th class="text-center">Статус</th>
                            <th>Комментарий</th>
                        </tr>
                    </thead>
                    <tbody>
                        $rows
                    </tbody>
                </table>
            </section>
        """.trimIndent() + "\n"
    }
    
    /**
     * Анализ и рекомендации.
     */
    private fun renderAnalysis(analysis: ThicknessAnalysis): String {
        val statusColor = when (analysis.overallStatus) {
            OverallStatus.EXCELLENT, OverallStatus.GOOD -> DesignTokens.Colors.Status.OK
            OverallStatus.FAIR -> DesignTokens.Colors.Status.WARNING
            OverallStatus.POOR, OverallStatus.CRITICAL -> DesignTokens.Colors.Status.CRITICAL
        }
        
        val statusLabel = when (analysis.overallStatus) {
            OverallStatus.EXCELLENT -> "Отличное"
            OverallStatus.GOOD -> "Хорошее"
            OverallStatus.FAIR -> "Удовлетворительное"
            OverallStatus.POOR -> "Плохое"
            OverallStatus.CRITICAL -> "Критическое"
        }
        
        return """
            <section class="section">
                <h2>Анализ и рекомендации</h2>
                <div class="card">
                    <h3 style="color: $statusColor;">Состояние ЛКП: $statusLabel</h3>
                    <p class="mt-md">${HtmlStyles.escapeHtml(analysis.recommendation)}</p>
                    <p class="meta mt-md">
                        <strong>Нормальный диапазон:</strong> 
                        ${formatThickness(analysis.normalRange.min)} — ${formatThickness(analysis.normalRange.max)}
                    </p>
                </div>
            </section>
        """.trimIndent() + "\n"
    }
    
    /**
     * Контакты клиента.
     */
    private fun renderCustomerContacts(customer: ThicknessReportCustomer): String {
        val contacts = mutableListOf<String>()
        customer.phone?.let { contacts.add("Телефон: ${maskPhone(it)}") }
        customer.email?.let { contacts.add("Email: ${maskEmail(it)}") }
        
        if (contacts.isEmpty()) {
            return ""
        }
        
        return """
            <section class="section">
                <h3>Контакты клиента</h3>
                <p class="meta">
                    ${contacts.joinToString("<br/>\n")}
                </p>
            </section>
        """.trimIndent() + "\n"
    }
    
    /**
     * Футер отчёта.
     */
    private fun renderFooter(): String {
        return """
            <section class="section mt-lg">
                <p class="meta text-center">
                    Отчёт сгенерирован автоматически системой самообслуживания.<br/>
                    При возникновении вопросов обратитесь в службу поддержки.
                </p>
            </section>
        """.trimIndent() + "\n"
    }
    
    // Утилиты форматирования
    
    private fun formatThickness(value: Float): String {
        return String.format(Locale.US, "%.1f µm", value)
    }
    
    private fun formatPercent(value: Float): String {
        return String.format(Locale.US, "%.1f%%", value)
    }
    
    private fun statusColor(deviations: Int): String {
        return when {
            deviations == 0 -> DesignTokens.Colors.Status.OK
            deviations <= 3 -> DesignTokens.Colors.Status.WARNING
            else -> DesignTokens.Colors.Status.CRITICAL
        }
    }
    
    private fun maskPhone(phone: String): String {
        if (phone.length < 7) return phone
        return phone.take(3) + "***" + phone.takeLast(2)
    }
    
    private fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return email
        val local = parts[0]
        val domain = parts[1]
        val maskedLocal = if (local.length <= 3) {
            local
        } else {
            local.take(2) + "***" + local.takeLast(1)
        }
        return "$maskedLocal@$domain"
    }
}

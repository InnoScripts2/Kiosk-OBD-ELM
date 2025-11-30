package com.selfservice.feature.reports

/**
 * Формирует HTML-версии диагностических отчётов для предпросмотра и отправки.
 */
class DiagnosticsReportHtmlFormatter(
    private val mapper: DiagnosticsReportViewModelMapper
) {

    fun format(input: DiagnosticsReportInput): String {
        val viewModel = mapper.map(input)
        return format(viewModel)
    }

    fun format(viewModel: ReportViewModel): String {
        val generatedAt = mapper.formatGeneratedAt(viewModel)
        return buildString {
            append("<!DOCTYPE html>\n")
            append("<html lang=\"ru\">\n<head>\n<meta charset=\"utf-8\"/>\n")
            append("<style>\n")
            append(BASE_STYLES)
            append("</style>\n")
            append("<title>Диагностический отчёт</title>\n</head>\n<body>\n")
            append("<section class=\"section\">\n")
            append("<h1 class=\"title\">Диагностический отчёт</h1>\n")
            append("<p class=\"meta\">Сессия: <strong>")
            append(escape(viewModel.sessionId))
            append("</strong><br/>")
            append("Сформирован: <strong>")
            append(escape(generatedAt))
            append("</strong></p>\n")
            viewModel.vehicleLine?.let { vehicleLine ->
                append("<p class=\"meta\">Автомобиль: <strong>")
                append(escape(vehicleLine))
                append("</strong></p>\n")
            }
            viewModel.vehicleVin?.let { vin ->
                append("<p class=\"meta\">VIN: <strong>")
                append(escape(vin))
                append("</strong></p>\n")
            }
            if (viewModel.contacts.isNotEmpty()) {
                append("<p class=\"meta\">Контакты клиента:<br/>")
                viewModel.contacts.forEach { item ->
                    append(escape(item))
                    append("<br/>")
                }
                append("</p>\n")
            }
            append("</section>\n")

            append("<section class=\"section\">\n")
            append("<h2 class=\"subtitle\">Сводка</h2>\n")
            append("<ul class=\"summary\">")
            append("<li>Метрик в отчёте: <strong>${viewModel.summary.total}</strong></li>")
            append("<li class=\"tag tag-normal\">Норма: <strong>${viewModel.summary.normal}</strong></li>")
            append("<li class=\"tag tag-warning\">Предупреждения: <strong>${viewModel.summary.warning}</strong></li>")
            append("<li class=\"tag tag-critical\">Критические: <strong>${viewModel.summary.critical}</strong></li>")
            append("<li class=\"tag tag-unknown\">Нет данных: <strong>${viewModel.summary.noData}</strong></li>")
            append("</ul>\n")
            append("</section>\n")

            append("<section class=\"section\">\n")
            append("<h2 class=\"subtitle\">Показатели</h2>\n")
            append("<table class=\"metrics\">\n")
            append("<thead><tr><th>Показатель</th><th>Значение</th><th>Статус</th><th>Комментарий</th></tr></thead>\n<tbody>\n")
            viewModel.metrics.forEach { metric ->
                append("<tr>")
                append("<td>")
                append(escape(metric.title))
                append("</td>")
                append("<td>")
                append(escape(metric.valueText))
                append("</td>")
                append("<td><span class=\"tag ")
                append(severityClass(metric.severityLabel))
                append("\">")
                append(escape(metric.statusLabel))
                append("</span></td>")
                append("<td>")
                append(escape(metric.advice.replace("\n", " "))) // краткое пояснение
                append("</td></tr>\n")
            }
            append("</tbody></table>\n</section>\n")

            append("<section class=\"section\">\n")
            append("<h2 class=\"subtitle\">Рекомендации</h2>\n")
            if (viewModel.recommendations.isEmpty()) {
                append("<p class=\"meta\">Все системы работают в пределах нормы. Дополнительных действий не требуется.</p>\n")
            } else {
                append("<ul class=\"recommendations\">\n")
                viewModel.recommendations.forEach { recommendation ->
                    append("<li class=\"recommendation\">")
                    append("<div class=\"recommendation-header\">")
                    append("<span class=\"tag ")
                    append(severityClass(fromSeverityLabel(recommendation.severityLabel)))
                    append("\">")
                    append(escape(recommendation.severityLabel))
                    append("</span>")
                    append("<span class=\"priority\">")
                    append(escape(recommendation.priorityLabel))
                    append("</span>")
                    append("</div>")
                    append("<h3 class=\"recommendation-title\">")
                    append(escape(recommendation.title))
                    append("</h3>")
                    recommendation.thresholdLabel?.let { threshold ->
                        append("<p class=\"meta\">")
                        append(escape(threshold))
                        append("</p>")
                    }
                    append("<p class=\"message\">")
                    append(escape(recommendation.message))
                    append("</p>")
                    append("</li>\n")
                }
                append("</ul>\n")
            }
            append("</section>\n")
            append("</body>\n</html>")
        }
    }

    private fun severityClass(label: String): String = when (label.uppercase()) {
        "CRITICAL" -> "tag-critical"
        "WARNING" -> "tag-warning"
        "NORMAL" -> "tag-normal"
        else -> "tag-unknown"
    }

    private fun fromSeverityLabel(label: String): String = when (label.uppercase()) {
        "КРИТИЧНО" -> "CRITICAL"
        "ВНИМАНИЕ" -> "WARNING"
        "ИНФОРМАЦИЯ" -> "NORMAL"
        else -> label.uppercase()
    }

    private fun escape(value: String): String {
        val builder = StringBuilder(value.length)
        value.forEach { ch ->
            when (ch) {
                '&' -> builder.append("&amp;")
                '<' -> builder.append("&lt;")
                '>' -> builder.append("&gt;")
                '"' -> builder.append("&quot;")
                '\'' -> builder.append("&#39;")
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }

    private companion object {
        private const val BASE_STYLES = """
            body { font-family: 'Roboto', 'Arial', sans-serif; background: #0f172a; color: #f8fafc; padding: 24px; }
            .section { background: rgba(255,255,255,0.06); border-radius: 16px; padding: 20px 24px; margin-bottom: 24px; }
            .title { margin: 0 0 8px; font-size: 26px; }
            .subtitle { margin: 0 0 12px; font-size: 20px; }
            .meta { margin: 4px 0; color: #cbd5f5; }
            .summary { list-style: none; padding: 0; margin: 0; display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 8px; }
            .summary li { background: rgba(15, 23, 42, 0.6); padding: 10px 12px; border-radius: 12px; }
            .metrics { width: 100%; border-collapse: collapse; }
            .metrics th, .metrics td { padding: 8px 12px; border-bottom: 1px solid rgba(148, 163, 184, 0.24); text-align: left; }
            .metrics th { color: #cbd5f5; text-transform: uppercase; font-size: 12px; letter-spacing: 0.06em; }
            .tag { display: inline-flex; align-items: center; gap: 4px; padding: 4px 8px; border-radius: 999px; font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; }
            .tag-normal { background: rgba(34,197,94,0.15); color: #4ade80; }
            .tag-warning { background: rgba(234,179,8,0.18); color: #facc15; }
            .tag-critical { background: rgba(248,113,113,0.2); color: #fca5a5; }
            .tag-unknown { background: rgba(148,163,184,0.2); color: #e2e8f0; }
            .recommendations { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 12px; }
            .recommendation { background: rgba(15, 23, 42, 0.6); border-radius: 12px; padding: 16px 18px; }
            .recommendation-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
            .recommendation-title { margin: 0 0 6px; font-size: 18px; }
            .priority { font-size: 12px; color: #e2e8f0; text-transform: uppercase; letter-spacing: 0.05em; }
            .message { margin: 0; color: #f8fafc; }
        """
    }
}

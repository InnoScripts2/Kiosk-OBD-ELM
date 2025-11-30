package com.selfservice.feature.reports

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream

/**
 * Генерирует PDF-версию диагностического отчёта для выдачи клиенту.
 */
internal class DiagnosticsReportPdfGenerator(
    private val mapper: DiagnosticsReportViewModelMapper,
    private val renderer: DiagnosticsReportPdfRenderer = AndroidDiagnosticsReportPdfRenderer()
) {

    fun generate(input: DiagnosticsReportInput): ByteArray {
        val viewModel = mapper.map(input)
        val generatedAt = mapper.formatGeneratedAt(viewModel)
        return renderer.render(viewModel, generatedAt)
    }
}

internal interface DiagnosticsReportPdfRenderer {
    fun render(viewModel: ReportViewModel, generatedAt: String): ByteArray
}

internal class AndroidDiagnosticsReportPdfRenderer : DiagnosticsReportPdfRenderer {

    override fun render(viewModel: ReportViewModel, generatedAt: String): ByteArray {
        val document = PdfDocument()
        var pageNumber = 1
        var page = startPage(document, pageNumber)
        var canvas = page.canvas
        var cursorY = MARGIN

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 11f
        }

        fun startNewPage() {
            document.finishPage(page)
            pageNumber += 1
            page = startPage(document, pageNumber)
            canvas = page.canvas
            cursorY = MARGIN
        }

        fun ensureSpace(requiredHeight: Float) {
            if (cursorY + requiredHeight > (PAGE_HEIGHT - MARGIN)) {
                startNewPage()
            }
        }

        fun drawLine(text: String, paint: TextPaint, spacingAfter: Float) {
            if (text.isBlank()) {
                return
            }
            val metrics = paint.fontMetrics
            val lineHeight = metrics.descent - metrics.ascent
            ensureSpace(lineHeight + spacingAfter)
            val baseline = cursorY - metrics.ascent
            canvas.drawText(text, MARGIN, baseline, paint)
            cursorY += lineHeight + spacingAfter
        }

        fun drawParagraph(text: String, paint: TextPaint) {
            val sanitized = text.trim()
            if (sanitized.isEmpty()) {
                return
            }
            val layout = StaticLayout.Builder.obtain(
                sanitized,
                0,
                sanitized.length,
                paint,
                CONTENT_WIDTH
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(LINE_SPACING_ADD, LINE_SPACING_MULT)
                .setIncludePad(false)
                .build()
            ensureSpace(layout.height + PARAGRAPH_SPACING)
            canvas.save()
            canvas.translate(MARGIN, cursorY)
            layout.draw(canvas)
            canvas.restore()
            cursorY += layout.height + PARAGRAPH_SPACING
        }

        fun drawSection(title: String) {
            drawLine(title, sectionPaint, SECTION_SPACING)
        }

        drawLine("Диагностический отчёт", titlePaint, TITLE_SPACING)

        drawParagraph("Сессия: ${viewModel.sessionId}", metaPaint)
        drawParagraph("Сформирован: $generatedAt", metaPaint)
        viewModel.vehicleLine?.let { drawParagraph("Автомобиль: $it", metaPaint) }
        viewModel.vehicleVin?.let { drawParagraph("VIN: $it", metaPaint) }
        if (viewModel.contacts.isNotEmpty()) {
            drawParagraph("Контакты клиента:", metaPaint)
            viewModel.contacts.forEach { contact ->
                drawParagraph("\u2022 $contact", metaPaint)
            }
        }

        drawSection("Сводка")
        drawParagraph("Метрик в отчёте: ${viewModel.summary.total}", bodyPaint)
        drawParagraph("Норма: ${viewModel.summary.normal}", bodyPaint)
        drawParagraph("Предупреждения: ${viewModel.summary.warning}", bodyPaint)
        drawParagraph("Критические: ${viewModel.summary.critical}", bodyPaint)
        drawParagraph("Нет данных: ${viewModel.summary.noData}", bodyPaint)

        drawSection("Показатели")
        if (viewModel.metrics.isEmpty()) {
            drawParagraph("Данных по показателям нет", bodyPaint)
        } else {
            viewModel.metrics.forEach { metric ->
                val line = buildString {
                    append('\u2022')
                    append(' ')
                    append(metric.title)
                    append(": ")
                    append(metric.valueText)
                    append(" — ")
                    append(metric.statusLabel)
                    val advice = metric.advice.replace("\n", " ").trim()
                    if (advice.isNotEmpty()) {
                        append(". ")
                        append(advice)
                    }
                }
                drawParagraph(line, bodyPaint)
            }
        }

        drawSection("Рекомендации")
        if (viewModel.recommendations.isEmpty()) {
            drawParagraph("Все системы работают в пределах нормы.", bodyPaint)
        } else {
            viewModel.recommendations.forEach { recommendation ->
                val line = buildString {
                    append('\u2022')
                    append(' ')
                    append(recommendation.title)
                    append(" — ")
                    append(recommendation.severityLabel)
                    append(" / ")
                    append(recommendation.priorityLabel)
                    recommendation.thresholdLabel?.let { threshold ->
                        append(" (Порог: ")
                        append(threshold)
                        append(')')
                    }
                    val message = recommendation.message.replace("\n", " ").trim()
                    if (message.isNotEmpty()) {
                        append(". ")
                        append(message)
                    }
                }
                drawParagraph(line, bodyPaint)
            }
        }

        document.finishPage(page)
        val output = ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }

    private fun startPage(document: PdfDocument, pageNumber: Int): PdfDocument.Page {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        return document.startPage(pageInfo)
    }

    private companion object {
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 48f
        private val CONTENT_WIDTH: Int = (PAGE_WIDTH - 2 * MARGIN).toInt()
        private const val TITLE_SPACING = 18f
        private const val SECTION_SPACING = 12f
        private const val PARAGRAPH_SPACING = 10f
        private const val LINE_SPACING_ADD = 0f
        private const val LINE_SPACING_MULT = 1.2f
    }
}

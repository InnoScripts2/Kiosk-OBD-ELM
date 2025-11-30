package com.selfservice.feature.reports

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream

/**
 * Генератор PDF отчётов на основе Android PdfDocument.
 * 
 * Конвертирует HTML-разметку в PDF документ, используя:
 * - A4 формат страницы (595 x 842 points)
 * - Мультистраничную разбивку
 * - Базовое форматирование текста
 * 
 * Примечание: Это упрощённая реализация. Полноценный HTML→PDF рендеринг
 * требует WebView или внешней библиотеки (iText, PDFBox).
 * Для production рассмотрите интеграцию с WebView.printPdf().
 */
open class PdfGenerator {
    
    companion object {
        // A4 размер в points (72 dpi)
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        
        // Отступы страницы
        private const val PAGE_MARGIN = 40
        private const val CONTENT_WIDTH = PAGE_WIDTH - (PAGE_MARGIN * 2)
        private const val CONTENT_HEIGHT = PAGE_HEIGHT - (PAGE_MARGIN * 2)
        
        // Размеры шрифтов
        private const val FONT_SIZE_TITLE = 24f
        private const val FONT_SIZE_HEADING = 18f
        private const val FONT_SIZE_BODY = 12f
        private const val FONT_SIZE_SMALL = 10f
        
        // Цвета (RGB)
        private const val COLOR_TEXT_PRIMARY = Color.BLACK
        private const val COLOR_TEXT_SECONDARY = Color.DKGRAY
        private const val COLOR_ACCENT_TEAL = 0xFF00C4B4.toInt()
        private const val COLOR_ACCENT_AMBER = 0xFFFFC857.toInt()
    }
    
    /**
     * Генерирует PDF из HTML.
     * 
     * Примечание: Эта реализация упрощённая и поддерживает только
     * базовое форматирование. Для полного HTML рендеринга используйте
     * WebView или специализированную библиотеку.
     * 
     * @param html HTML содержимое
     * @param reportType тип отчёта (для акцентного цвета)
     * @return PDF байты
     */
    open fun generateFromHtml(html: String, reportType: ReportType): ByteArray {
        val document = PdfDocument()
        
        try {
            // Извлекаем текст из HTML (упрощённо)
            val plainText = stripHtmlTags(html)
            
            // Определяем акцентный цвет
            val accentColor = when (reportType) {
                ReportType.THICKNESS -> COLOR_ACCENT_TEAL
                ReportType.DIAGNOSTICS -> COLOR_ACCENT_AMBER
            }
            
            // Разбиваем на страницы и рендерим
            renderPages(document, plainText, accentColor)
            
            // Конвертируем в байты
            val outputStream = ByteArrayOutputStream()
            document.writeTo(outputStream)
            return outputStream.toByteArray()
        } finally {
            document.close()
        }
    }
    
    /**
     * Генерирует простой текстовый PDF (без HTML).
     * Используется как fallback если HTML парсинг недоступен.
     * 
     * @param title заголовок
     * @param content текстовое содержимое
     * @param reportType тип отчёта
     * @return PDF байты
     */
    open fun generatePlainText(title: String, content: String, reportType: ReportType): ByteArray {
        val document = PdfDocument()
        
        try {
            val accentColor = when (reportType) {
                ReportType.THICKNESS -> COLOR_ACCENT_TEAL
                ReportType.DIAGNOSTICS -> COLOR_ACCENT_AMBER
            }
            
            val fullText = "$title\n\n$content"
            renderPages(document, fullText, accentColor)
            
            val outputStream = ByteArrayOutputStream()
            document.writeTo(outputStream)
            return outputStream.toByteArray()
        } finally {
            document.close()
        }
    }
    
    /**
     * Рендерит текст на несколько страниц.
     */
    private fun renderPages(document: PdfDocument, text: String, accentColor: Int) {
        val lines = text.split("\n")
        var currentLine = 0
        var pageNumber = 1
        
        while (currentLine < lines.size) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = document.startPage(pageInfo)
            
            currentLine = renderPage(page.canvas, lines, currentLine, pageNumber, accentColor)
            
            document.finishPage(page)
            pageNumber++
            
            // Безопасность: ограничиваем количество страниц
            if (pageNumber > 50) {
                break
            }
        }
    }
    
    /**
     * Рендерит одну страницу.
     * 
     * @return индекс следующей строки для следующей страницы
     */
    private fun renderPage(
        canvas: Canvas,
        lines: List<String>,
        startLine: Int,
        pageNumber: Int,
        accentColor: Int
    ): Int {
        var y = PAGE_MARGIN.toFloat()
        var lineIndex = startLine
        
        // Заголовок страницы (только на первой странице)
        if (pageNumber == 1 && lineIndex < lines.size) {
            val titlePaint = createTextPaint(FONT_SIZE_TITLE, COLOR_TEXT_PRIMARY, true)
            val title = lines[lineIndex]
            canvas.drawText(title, PAGE_MARGIN.toFloat(), y + FONT_SIZE_TITLE, titlePaint)
            y += FONT_SIZE_TITLE + 20
            lineIndex++
        }
        
        // Основной контент
        val bodyPaint = createTextPaint(FONT_SIZE_BODY, COLOR_TEXT_PRIMARY, false)
        val lineHeight = FONT_SIZE_BODY + 4
        
        while (lineIndex < lines.size && y + lineHeight < PAGE_HEIGHT - PAGE_MARGIN) {
            val line = lines[lineIndex]
            
            // Определяем тип строки по содержимому
            when {
                line.startsWith("##") -> {
                    // Заголовок H2
                    val headingPaint = createTextPaint(FONT_SIZE_HEADING, accentColor, true)
                    canvas.drawText(
                        line.removePrefix("##").trim(),
                        PAGE_MARGIN.toFloat(),
                        y + FONT_SIZE_HEADING,
                        headingPaint
                    )
                    y += FONT_SIZE_HEADING + 10
                }
                line.startsWith("###") -> {
                    // Заголовок H3
                    val headingPaint = createTextPaint(FONT_SIZE_BODY + 2, COLOR_TEXT_PRIMARY, true)
                    canvas.drawText(
                        line.removePrefix("###").trim(),
                        PAGE_MARGIN.toFloat(),
                        y + FONT_SIZE_BODY + 2,
                        headingPaint
                    )
                    y += FONT_SIZE_BODY + 8
                }
                line.trim().isEmpty() -> {
                    // Пустая строка
                    y += lineHeight / 2
                }
                else -> {
                    // Обычный текст
                    // Разбиваем длинные строки
                    val wrappedLines = wrapText(line, bodyPaint, CONTENT_WIDTH.toFloat())
                    wrappedLines.forEach { wrappedLine ->
                        if (y + lineHeight < PAGE_HEIGHT - PAGE_MARGIN) {
                            canvas.drawText(
                                wrappedLine,
                                PAGE_MARGIN.toFloat(),
                                y + FONT_SIZE_BODY,
                                bodyPaint
                            )
                            y += lineHeight
                        }
                    }
                }
            }
            
            lineIndex++
        }
        
        // Номер страницы
        val footerPaint = createTextPaint(FONT_SIZE_SMALL, COLOR_TEXT_SECONDARY, false)
        canvas.drawText(
            "Страница $pageNumber",
            (PAGE_WIDTH - PAGE_MARGIN).toFloat() - 60,
            (PAGE_HEIGHT - PAGE_MARGIN / 2).toFloat(),
            footerPaint
        )
        
        return lineIndex
    }
    
    /**
     * Создаёт Paint для текста.
     */
    private fun createTextPaint(size: Float, color: Int, bold: Boolean): TextPaint {
        return TextPaint().apply {
            this.color = color
            this.textSize = size
            this.isAntiAlias = true
            if (bold) {
                this.isFakeBoldText = true
            }
        }
    }
    
    /**
     * Разбивает длинный текст на строки по ширине.
     */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        
        words.forEach { word ->
            val testLine = if (currentLine.isEmpty()) {
                word
            } else {
                "$currentLine $word"
            }
            
            val width = paint.measureText(testLine)
            
            if (width > maxWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            } else {
                currentLine = StringBuilder(testLine)
            }
        }
        
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        
        return lines
    }
    
    /**
     * Удаляет HTML теги из строки (упрощённо).
     * Для production используйте HTML parser.
     */
    private fun stripHtmlTags(html: String): String {
        return html
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), "\n")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("\n\n+"), "\n\n")
            .trim()
    }
}

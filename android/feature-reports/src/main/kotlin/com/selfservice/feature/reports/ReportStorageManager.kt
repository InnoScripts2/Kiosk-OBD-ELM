package com.selfservice.feature.reports

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Менеджер хранения отчётов.
 *
 * - Сохранение отчётов и метаданных в файловой системе
 * - Запись проблем в logs/issues/<date>.json
 * - Очистка устаревших отчётов (30 дней)
 * - Контроль использования дискового пространства
 *
 * Структура хранения:
 * ```
 * logs/
 *   reports/
 *     <sessionId>/
 *       report.html
 *       report.pdf
 *   sessions/
 *     2025-11-23.json
 *   issues/
 *     2025-11-23.json
 * ```
 */
class ReportStorageManager(
    val config: ReportStorageConfig
) {
    
    private val dateFormat = SimpleDateFormat(config.dateFormat, Locale.US)
    
    init {
        // Создаём директории если не существуют
        config.baseDir.mkdirs()
        config.metadataDir.mkdirs()
        config.issuesDir.mkdirs()
    }
    
    /**
     * Сохраняет HTML отчёт.
     * 
     * @param sessionId идентификатор сессии
     * @param html HTML содержимое
     * @return путь к сохранённому файлу
     */
    fun saveHtml(sessionId: String, html: String): File {
        val sessionDir = getSessionDir(sessionId)
        sessionDir.mkdirs()
        
        val htmlFile = File(sessionDir, "report.html")
        htmlFile.writeText(html, Charsets.UTF_8)
        
        return htmlFile
    }
    
    /**
     * Сохраняет PDF отчёт.
     * 
     * @param sessionId идентификатор сессии
     * @param pdfBytes PDF байты
     * @return путь к сохранённому файлу
     */
    fun savePdf(sessionId: String, pdfBytes: ByteArray): File {
        val sessionDir = getSessionDir(sessionId)
        sessionDir.mkdirs()
        
        val pdfFile = File(sessionDir, "report.pdf")
        FileOutputStream(pdfFile).use { output ->
            output.write(pdfBytes)
        }
        
        return pdfFile
    }
    
    /**
     * Сохраняет метаданные отчёта.
     * Добавляет запись в файл метаданных за текущий день.
     * 
     * @param metadata метаданные отчёта
     */
    fun saveMetadata(metadata: ReportMetadata) {
        val date = Date(metadata.generatedAtMillis)
        val dateStr = dateFormat.format(date)
        val metadataFile = File(config.metadataDir, "$dateStr.json")
        
        // Читаем существующие метаданные
        val existingMetadata = if (metadataFile.exists()) {
            metadataFile.readText(Charsets.UTF_8)
        } else {
            "[]"
        }
        
        // Добавляем новую запись (упрощённый JSON без библиотеки)
        val jsonEntry = metadataToJson(metadata)
        
        val updatedMetadata = if (existingMetadata.trim() == "[]") {
            "[$jsonEntry]"
        } else {
            existingMetadata.trimEnd().removeSuffix("]") + ",\n$jsonEntry\n]"
        }
        
        metadataFile.writeText(updatedMetadata, Charsets.UTF_8)
    }
    
    /**
     * Сохраняет запись о проблеме.
     * Добавляет запись в файл проблем за текущий день.
     * 
     * @param issue запись о проблеме
     */
    fun saveIssue(issue: ReportIssue) {
        val date = Date(issue.timestampMillis)
        val dateStr = dateFormat.format(date)
        val issuesFile = File(config.issuesDir, "$dateStr.json")
        
        // Читаем существующие проблемы
        val existingIssues = if (issuesFile.exists()) {
            issuesFile.readText(Charsets.UTF_8)
        } else {
            "[]"
        }
        
        // Добавляем новую запись
        val jsonEntry = issueToJson(issue)
        
        val updatedIssues = if (existingIssues.trim() == "[]") {
            "[$jsonEntry]"
        } else {
            existingIssues.trimEnd().removeSuffix("]") + ",\n$jsonEntry\n]"
        }
        
        issuesFile.writeText(updatedIssues, Charsets.UTF_8)
    }
    
    /**
     * Очищает устаревшие отчёты (старше retentionDays).
     * 
     * @return количество удалённых сессий
     */
    fun cleanupOldReports(): Int {
        val cutoffTime = System.currentTimeMillis() - (config.retentionDays * 24 * 60 * 60 * 1000L)
        var deletedCount = 0
        
        config.baseDir.listFiles()?.forEach { sessionDir ->
            if (sessionDir.isDirectory) {
                val lastModified = sessionDir.lastModified()
                if (lastModified < cutoffTime) {
                    if (sessionDir.deleteRecursively()) {
                        deletedCount++
                    }
                }
            }
        }
        
        return deletedCount
    }
    
    /**
     * Вычисляет общий размер всех отчётов в байтах.
     * 
     * @return размер в байтах
     */
    fun getTotalSize(): Long {
        return config.baseDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
    
    /**
     * Проверяет, не превышен ли лимит хранения.
     * 
     * @return true если лимит превышен
     */
    fun isStorageLimitExceeded(): Boolean {
        val totalSizeBytes = getTotalSize()
        val limitBytes = config.maxTotalSizeMb * 1024L * 1024L
        return totalSizeBytes > limitBytes
    }
    
    /**
     * Получает директорию для сессии.
     */
    private fun getSessionDir(sessionId: String): File {
        // Санитизируем sessionId для безопасности
        val sanitized = sessionId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(config.baseDir, sanitized)
    }
    
    /**
     * Вычисляет SHA-256 hash для строки.
     */
    fun calculateHash(content: String): String {
        val bytes = content.toByteArray(Charsets.UTF_8)
        return calculateHash(bytes)
    }
    
    /**
     * Вычисляет SHA-256 hash для байтов.
     */
    fun calculateHash(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    // Простая JSON сериализация без зависимостей
    
    private fun metadataToJson(metadata: ReportMetadata): String {
        val formats = metadata.formats.joinToString("\", \"") { it.name.lowercase() }
        
        return """
            {
                "sessionId": "${escapeJson(metadata.sessionId)}",
                "reportType": "${metadata.reportType.name.lowercase()}",
                "generatedAtMillis": ${metadata.generatedAtMillis},
                "formats": ["$formats"],
                "htmlHash": ${metadata.htmlHash?.let { "\"$it\"" } ?: "null"},
                "pdfHash": ${metadata.pdfHash?.let { "\"$it\"" } ?: "null"},
                "htmlSizeBytes": ${metadata.htmlSizeBytes ?: "null"},
                "pdfSizeBytes": ${metadata.pdfSizeBytes ?: "null"},
                "status": "${metadata.status.name.lowercase()}",
                "htmlPath": ${metadata.htmlPath?.let { "\"${escapeJson(it)}\"" } ?: "null"},
                "pdfPath": ${metadata.pdfPath?.let { "\"${escapeJson(it)}\"" } ?: "null"}
            }
        """.trimIndent()
    }
    
    private fun issueToJson(issue: ReportIssue): String {
        val contextJson = issue.context?.entries?.joinToString(",\n") { (key, value) ->
            "\"${escapeJson(key)}\": \"${escapeJson(value)}\""
        } ?: ""
        
        return """
            {
                "sessionId": "${escapeJson(issue.sessionId)}",
                "reportType": "${issue.reportType.name.lowercase()}",
                "timestampMillis": ${issue.timestampMillis},
                "issueType": "${issue.issueType.name.lowercase()}",
                "description": "${escapeJson(issue.description)}",
                "stackTrace": ${issue.stackTrace?.let { "\"${escapeJson(it)}\"" } ?: "null"},
                "context": {$contextJson}
            }
        """.trimIndent()
    }
    
    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

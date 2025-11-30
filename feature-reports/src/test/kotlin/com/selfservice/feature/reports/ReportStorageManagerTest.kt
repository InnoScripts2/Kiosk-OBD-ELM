package com.selfservice.feature.reports

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit-тесты для ReportStorageManager.
 * 
 * Проверяют:
 * - Сохранение HTML/PDF файлов
 * - Запись метаданных
 * - Запись проблем
 * - Очистку устаревших отчётов
 * - Вычисление hash
 */
class ReportStorageManagerTest {
    
    private lateinit var storageManager: ReportStorageManager
    private lateinit var tempDir: File
    
    @Before
    fun setUp() {
        // Создаём временную директорию для тестов
        tempDir = createTempDir("report-storage-test")
        
        val config = ReportStorageConfig(
            baseDir = File(tempDir, "reports"),
            metadataDir = File(tempDir, "sessions"),
            issuesDir = File(tempDir, "issues"),
            retentionDays = 30,
            maxTotalSizeMb = 100
        )
        
        storageManager = ReportStorageManager(config)
    }
    
    @Test
    fun `saveHtml creates file with correct content`() {
        // Arrange
        val sessionId = "test-session-123"
        val htmlContent = "<html><body>Test Report</body></html>"
        
        // Act
        val htmlFile = storageManager.saveHtml(sessionId, htmlContent)
        
        // Assert
        assertTrue("HTML file should exist", htmlFile.exists())
        assertEquals("HTML content should match", htmlContent, htmlFile.readText(Charsets.UTF_8))
        assertEquals("Filename should be report.html", "report.html", htmlFile.name)
    }
    
    @Test
    fun `savePdf creates file with correct content`() {
        // Arrange
        val sessionId = "test-session-456"
        val pdfBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46) // PDF signature
        
        // Act
        val pdfFile = storageManager.savePdf(sessionId, pdfBytes)
        
        // Assert
        assertTrue("PDF file should exist", pdfFile.exists())
        assertArrayEquals("PDF content should match", pdfBytes, pdfFile.readBytes())
        assertEquals("Filename should be report.pdf", "report.pdf", pdfFile.name)
    }
    
    @Test
    fun `saveMetadata creates JSON file with correct structure`() {
        // Arrange
        val metadata = ReportMetadata(
            sessionId = "test-session-789",
            reportType = ReportType.THICKNESS,
            generatedAtMillis = System.currentTimeMillis(),
            formats = listOf(ReportFormat.HTML, ReportFormat.PDF),
            htmlHash = "abc123",
            pdfHash = "def456",
            htmlSizeBytes = 1024L,
            pdfSizeBytes = 2048L,
            status = ReportStatus.GENERATED,
            htmlPath = "test-session-789/report.html",
            pdfPath = "test-session-789/report.pdf",
            deliveryContacts = null
        )
        
        // Act
        storageManager.saveMetadata(metadata)
        
        // Assert
        val metadataFiles = storageManager.config.metadataDir.listFiles()
        assertNotNull("Metadata directory should have files", metadataFiles)
        assertTrue("Metadata file should exist", metadataFiles!!.isNotEmpty())
        
        val metadataContent = metadataFiles[0].readText(Charsets.UTF_8)
        assertTrue("Metadata should contain sessionId", metadataContent.contains(metadata.sessionId))
        assertTrue("Metadata should contain reportType", metadataContent.contains("thickness"))
        assertTrue("Metadata should contain formats", metadataContent.contains("html"))
        assertTrue("Metadata should contain formats", metadataContent.contains("pdf"))
    }
    
    @Test
    fun `saveIssue creates JSON file with correct structure`() {
        // Arrange
        val issue = ReportIssue(
            sessionId = "test-session-error",
            reportType = ReportType.DIAGNOSTICS,
            timestampMillis = System.currentTimeMillis(),
            issueType = IssueType.PDF_GENERATION_ERROR,
            description = "Test error description",
            stackTrace = "Test stack trace",
            context = mapOf("key1" to "value1", "key2" to "value2")
        )
        
        // Act
        storageManager.saveIssue(issue)
        
        // Assert
        val issueFiles = storageManager.config.issuesDir.listFiles()
        assertNotNull("Issues directory should have files", issueFiles)
        assertTrue("Issue file should exist", issueFiles!!.isNotEmpty())
        
        val issueContent = issueFiles[0].readText(Charsets.UTF_8)
        assertTrue("Issue should contain sessionId", issueContent.contains(issue.sessionId))
        assertTrue("Issue should contain description", issueContent.contains(issue.description))
        assertTrue("Issue should contain issueType", issueContent.contains("pdf_generation_error"))
    }
    
    @Test
    fun `calculateHash returns consistent hash for same content`() {
        // Arrange
        val content = "Test content for hashing"
        
        // Act
        val hash1 = storageManager.calculateHash(content)
        val hash2 = storageManager.calculateHash(content)
        
        // Assert
        assertEquals("Hash should be consistent", hash1, hash2)
        assertEquals("Hash should be 64 characters (SHA-256)", 64, hash1.length)
    }
    
    @Test
    fun `calculateHash returns different hash for different content`() {
        // Arrange
        val content1 = "Test content 1"
        val content2 = "Test content 2"
        
        // Act
        val hash1 = storageManager.calculateHash(content1)
        val hash2 = storageManager.calculateHash(content2)
        
        // Assert
        assertNotEquals("Hashes should be different", hash1, hash2)
    }
    
    @Test
    fun `cleanupOldReports removes old sessions`() {
        // Arrange
        val oldSessionId = "old-session"
        val recentSessionId = "recent-session"
        
        // Создаём старую сессию
        val oldSessionDir = File(storageManager.config.baseDir, oldSessionId)
        oldSessionDir.mkdirs()
        File(oldSessionDir, "report.html").writeText("Old report")
        
        // Устанавливаем старое время модификации (35 дней назад)
        val oldTime = System.currentTimeMillis() - (35L * 24 * 60 * 60 * 1000)
        oldSessionDir.setLastModified(oldTime)
        
        // Создаём свежую сессию
        val recentSessionDir = File(storageManager.config.baseDir, recentSessionId)
        recentSessionDir.mkdirs()
        File(recentSessionDir, "report.html").writeText("Recent report")
        
        // Act
        val deletedCount = storageManager.cleanupOldReports()
        
        // Assert
        assertEquals("Should delete 1 old session", 1, deletedCount)
        assertFalse("Old session should be deleted", oldSessionDir.exists())
        assertTrue("Recent session should exist", recentSessionDir.exists())
    }
    
    @Test
    fun `getTotalSize calculates correct size`() {
        // Arrange
        val sessionId = "test-session-size"
        val htmlContent = "A".repeat(1024) // 1 KB
        val pdfBytes = ByteArray(2048) // 2 KB
        
        storageManager.saveHtml(sessionId, htmlContent)
        storageManager.savePdf(sessionId, pdfBytes)
        
        // Act
        val totalSize = storageManager.getTotalSize()
        
        // Assert
        assertTrue("Total size should be at least 3 KB", totalSize >= 3072)
    }
    
    @Test
    fun `isStorageLimitExceeded returns false when under limit`() {
        // Arrange
        val sessionId = "test-session-small"
        val htmlContent = "<html>Small report</html>"
        
        storageManager.saveHtml(sessionId, htmlContent)
        
        // Act
        val limitExceeded = storageManager.isStorageLimitExceeded()
        
        // Assert
        assertFalse("Storage limit should not be exceeded", limitExceeded)
    }
    
    @Test
    fun `sanitized session IDs are safe for filesystem`() {
        // Arrange
        val dangerousSessionId = "../../../etc/passwd"
        val htmlContent = "<html>Test</html>"
        
        // Act
        val htmlFile = storageManager.saveHtml(dangerousSessionId, htmlContent)
        
        // Assert
        // Файл должен быть создан в безопасном месте
        assertTrue("File should be in base directory", htmlFile.path.contains(tempDir.path))
        assertFalse("Path should not contain ../", htmlFile.path.contains("../"))
    }
}

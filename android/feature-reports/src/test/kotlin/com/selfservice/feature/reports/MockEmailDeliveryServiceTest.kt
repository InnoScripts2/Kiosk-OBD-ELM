package com.selfservice.feature.reports

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit-тесты для MockEmailDeliveryService.
 * 
 * Проверяют:
 * - Отправку email
 * - Валидацию email адресов
 * - Генерацию message ID
 * - Историю отправок
 */
class MockEmailDeliveryServiceTest {
    
    private lateinit var emailService: MockEmailDeliveryService
    
    @Before
    fun setUp() {
        emailService = MockEmailDeliveryService()
    }
    
    @Test
    fun `sendReport succeeds with valid email`() = runBlocking {
        // Arrange
        val email = "test@example.com"
        val subject = "Test Report"
        val htmlBody = "<html><body>Test</body></html>"
        
        // Act
        val result = emailService.sendReport(email, subject, htmlBody)
        
        // Assert
        assertTrue("Send should succeed", result.success)
        assertEquals("Channel should be EMAIL", DeliveryChannel.EMAIL, result.channel)
        assertEquals("Recipient should match", email, result.recipient)
        assertNotNull("Message ID should be generated", result.messageId)
        assertNull("Error should be null", result.error)
    }
    
    @Test
    fun `sendReport fails with invalid email`() = runBlocking {
        // Arrange
        val invalidEmail = "not-an-email"
        val subject = "Test Report"
        val htmlBody = "<html><body>Test</body></html>"
        
        // Act
        val result = emailService.sendReport(invalidEmail, subject, htmlBody)
        
        // Assert
        assertFalse("Send should fail", result.success)
        assertNull("Message ID should be null", result.messageId)
        assertNotNull("Error should be present", result.error)
        assertTrue("Error should mention invalid format", 
            result.error!!.contains("Invalid email", ignoreCase = true))
    }
    
    @Test
    fun `sendReport includes attachments in history`() = runBlocking {
        // Arrange
        val email = "test@example.com"
        val subject = "Test Report"
        val htmlBody = "<html><body>Test</body></html>"
        val attachments = listOf(
            EmailAttachment("report.pdf", "application/pdf", byteArrayOf(0x25, 0x50))
        )
        
        // Act
        emailService.sendReport(email, subject, htmlBody, attachments)
        
        // Assert
        val sentEmails = emailService.getSentEmails()
        assertEquals("Should have 1 sent email", 1, sentEmails.size)
        assertEquals("Attachments should be saved", 1, sentEmails[0].attachments.size)
        assertEquals("Attachment filename should match", 
            "report.pdf", sentEmails[0].attachments[0].filename)
    }
    
    @Test
    fun `getSentEmails returns history of sent emails`() = runBlocking {
        // Arrange & Act
        emailService.sendReport("test1@example.com", "Subject 1", "Body 1")
        emailService.sendReport("test2@example.com", "Subject 2", "Body 2")
        emailService.sendReport("test3@example.com", "Subject 3", "Body 3")
        
        // Assert
        val sentEmails = emailService.getSentEmails()
        assertEquals("Should have 3 sent emails", 3, sentEmails.size)
    }
    
    @Test
    fun `clear removes all sent emails history`() = runBlocking {
        // Arrange
        emailService.sendReport("test@example.com", "Subject", "Body")
        assertEquals("Should have 1 sent email", 1, emailService.getSentEmails().size)
        
        // Act
        emailService.clear()
        
        // Assert
        assertEquals("Should have 0 sent emails after clear", 0, emailService.getSentEmails().size)
    }
    
    @Test
    fun `isAvailable returns true for mock service`() = runBlocking {
        // Act
        val available = emailService.isAvailable()
        
        // Assert
        assertTrue("Mock service should always be available", available)
    }
}

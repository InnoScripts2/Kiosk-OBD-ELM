package com.selfservice.feature.reports

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit-тесты для MockSmsDeliveryService.
 * 
 * Проверяют:
 * - Отправку SMS
 * - Валидацию номеров телефонов
 * - Проверку длины сообщения
 * - Историю отправок
 */
class MockSmsDeliveryServiceTest {
    
    private lateinit var smsService: MockSmsDeliveryService
    
    @Before
    fun setUp() {
        smsService = MockSmsDeliveryService()
    }
    
    @Test
    fun `sendSummary succeeds with valid phone and message`() = runBlocking {
        // Arrange
        val phone = "+79001234567"
        val message = "Отчёт готов. ID: test-123"
        
        // Act
        val result = smsService.sendSummary(phone, message)
        
        // Assert
        assertTrue("Send should succeed", result.success)
        assertEquals("Channel should be SMS", DeliveryChannel.SMS, result.channel)
        assertEquals("Recipient should match", phone, result.recipient)
        assertNotNull("Message ID should be generated", result.messageId)
        assertNull("Error should be null", result.error)
    }
    
    @Test
    fun `sendSummary fails with invalid phone format`() = runBlocking {
        // Arrange
        val invalidPhone = "1234567890" // Missing +country code
        val message = "Test message"
        
        // Act
        val result = smsService.sendSummary(invalidPhone, message)
        
        // Assert
        assertFalse("Send should fail", result.success)
        assertNull("Message ID should be null", result.messageId)
        assertNotNull("Error should be present", result.error)
        assertTrue("Error should mention invalid format",
            result.error!!.contains("Invalid phone", ignoreCase = true))
    }
    
    @Test
    fun `sendSummary fails when message exceeds 160 characters`() = runBlocking {
        // Arrange
        val phone = "+79001234567"
        val longMessage = "A".repeat(161) // 161 characters
        
        // Act
        val result = smsService.sendSummary(phone, longMessage)
        
        // Assert
        assertFalse("Send should fail", result.success)
        assertNull("Message ID should be null", result.messageId)
        assertNotNull("Error should be present", result.error)
        assertTrue("Error should mention 160 characters limit",
            result.error!!.contains("160 characters", ignoreCase = true))
    }
    
    @Test
    fun `sendSummary succeeds with exactly 160 characters`() = runBlocking {
        // Arrange
        val phone = "+79001234567"
        val message = "A".repeat(160) // Exactly 160 characters
        
        // Act
        val result = smsService.sendSummary(phone, message)
        
        // Assert
        assertTrue("Send should succeed with 160 characters", result.success)
    }
    
    @Test
    fun `getSentMessages returns history of sent SMS`() = runBlocking {
        // Arrange & Act
        smsService.sendSummary("+79001234567", "Message 1")
        smsService.sendSummary("+79009876543", "Message 2")
        smsService.sendSummary("+79005555555", "Message 3")
        
        // Assert
        val sentMessages = smsService.getSentMessages()
        assertEquals("Should have 3 sent messages", 3, sentMessages.size)
    }
    
    @Test
    fun `clear removes all sent messages history`() = runBlocking {
        // Arrange
        smsService.sendSummary("+79001234567", "Test message")
        assertEquals("Should have 1 sent message", 1, smsService.getSentMessages().size)
        
        // Act
        smsService.clear()
        
        // Assert
        assertEquals("Should have 0 sent messages after clear", 0, smsService.getSentMessages().size)
    }
    
    @Test
    fun `isAvailable returns true for mock service`() = runBlocking {
        // Act
        val available = smsService.isAvailable()
        
        // Assert
        assertTrue("Mock service should always be available", available)
    }
}

/**
 * Unit-тесты для SmsFormatter.
 */
class SmsFormatterTest {
    
    @Test
    fun `formatThicknessSummary generates message within 160 characters`() {
        // Arrange
        val sessionId = "test-session-very-long-id-123456789"
        val completed = 58
        val total = 60
        val deviations = 3
        
        // Act
        val message = SmsFormatter.formatThicknessSummary(sessionId, completed, total, deviations)
        
        // Assert
        assertTrue("Message should be <= 160 characters", message.length <= 160)
        assertTrue("Message should contain completed/total", message.contains("$completed/$total"))
        assertTrue("Message should contain deviations", message.contains("$deviations"))
        assertTrue("Message should contain short session ID", message.contains(sessionId.take(8)))
    }
    
    @Test
    fun `formatDiagnosticsSummary generates message within 160 characters`() {
        // Arrange
        val sessionId = "test-session-very-long-id-987654321"
        val totalCodes = 12
        val critical = 2
        val warning = 5
        
        // Act
        val message = SmsFormatter.formatDiagnosticsSummary(sessionId, totalCodes, critical, warning)
        
        // Assert
        assertTrue("Message should be <= 160 characters", message.length <= 160)
        assertTrue("Message should contain totalCodes", message.contains("$totalCodes"))
        assertTrue("Message should contain critical", message.contains("$critical"))
        assertTrue("Message should contain warning", message.contains("$warning"))
        assertTrue("Message should contain short session ID", message.contains(sessionId.take(8)))
    }
    
    @Test
    fun `formatThicknessSummary with zero deviations`() {
        // Arrange
        val sessionId = "test-123"
        val completed = 60
        val total = 60
        val deviations = 0
        
        // Act
        val message = SmsFormatter.formatThicknessSummary(sessionId, completed, total, deviations)
        
        // Assert
        assertTrue("Message should contain 0 deviations", message.contains("Отклонений: 0"))
    }
    
    @Test
    fun `formatDiagnosticsSummary with no codes found`() {
        // Arrange
        val sessionId = "test-456"
        val totalCodes = 0
        val critical = 0
        val warning = 0
        
        // Act
        val message = SmsFormatter.formatDiagnosticsSummary(sessionId, totalCodes, critical, warning)
        
        // Assert
        assertTrue("Message should contain 0 codes", message.contains("Найдено 0 кодов"))
    }
}

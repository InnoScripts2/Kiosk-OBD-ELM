package com.selfservice.feature.reports

/**
 * Интерфейс сервиса доставки отчётов по email.
 * 
 * Реализации:
 * - EmailDeliveryServiceImpl: Интеграция с email провайдером (SendGrid, SMTP и т.д.)
 * - MockEmailDeliveryService: Mock для DEV/QA режимов
 */
interface EmailDeliveryService {
    
    /**
     * Отправляет отчёт по email.
     * 
     * @param email адрес получателя
     * @param subject тема письма
     * @param htmlBody HTML тело письма
     * @param attachments вложения (опционально)
     * @return результат доставки
     */
    suspend fun sendReport(
        email: String,
        subject: String,
        htmlBody: String,
        attachments: List<EmailAttachment> = emptyList()
    ): DeliveryResult
    
    /**
     * Проверяет доступность email сервиса.
     * 
     * @return true если сервис доступен
     */
    suspend fun isAvailable(): Boolean
}

/**
 * Вложение email.
 */
data class EmailAttachment(
    /** Имя файла */
    val filename: String,
    
    /** MIME тип */
    val mimeType: String,
    
    /** Содержимое файла */
    val content: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as EmailAttachment
        
        if (filename != other.filename) return false
        if (mimeType != other.mimeType) return false
        if (!content.contentEquals(other.content)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + content.contentHashCode()
        return result
    }
}

/**
 * Mock реализация email сервиса для DEV/QA режимов.
 * Симулирует отправку без реального соединения.
 */
class MockEmailDeliveryService : EmailDeliveryService {
    
    private val sentEmails = mutableListOf<SentEmailRecord>()
    
    override suspend fun sendReport(
        email: String,
        subject: String,
        htmlBody: String,
        attachments: List<EmailAttachment>
    ): DeliveryResult {
        val startTime = System.currentTimeMillis()
        
        // Валидация email
        if (!isValidEmail(email)) {
            return DeliveryResult(
                success = false,
                channel = DeliveryChannel.EMAIL,
                messageId = null,
                recipient = email,
                error = "Invalid email address format",
                deliveryTimeMs = System.currentTimeMillis() - startTime
            )
        }
        
        // Симуляция задержки отправки (100-300ms)
        val delay = (100..300).random().toLong()
        kotlinx.coroutines.delay(delay)
        
        // Генерация mock message ID
        val messageId = "mock-${System.currentTimeMillis()}-${email.hashCode()}"
        
        // Сохранение для тестирования
        sentEmails.add(
            SentEmailRecord(
                email = email,
                subject = subject,
                htmlBody = htmlBody,
                attachments = attachments,
                messageId = messageId,
                sentAtMillis = System.currentTimeMillis()
            )
        )
        
        println("[MOCK EMAIL] Sent to: $email")
        println("[MOCK EMAIL] Subject: $subject")
        println("[MOCK EMAIL] Attachments: ${attachments.size}")
        println("[MOCK EMAIL] Message ID: $messageId")
        
        return DeliveryResult(
            success = true,
            channel = DeliveryChannel.EMAIL,
            messageId = messageId,
            recipient = email,
            error = null,
            deliveryTimeMs = System.currentTimeMillis() - startTime
        )
    }
    
    override suspend fun isAvailable(): Boolean = true
    
    /**
     * Получить историю отправленных email (для тестов).
     */
    fun getSentEmails(): List<SentEmailRecord> = sentEmails.toList()
    
    /**
     * Очистить историю (для тестов).
     */
    fun clear() {
        sentEmails.clear()
    }
    
    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return email.matches(emailRegex)
    }
    
    /**
     * Запись отправленного email (для тестов).
     */
    data class SentEmailRecord(
        val email: String,
        val subject: String,
        val htmlBody: String,
        val attachments: List<EmailAttachment>,
        val messageId: String,
        val sentAtMillis: Long
    )
}

/**
 * Production реализация email сервиса (заглушка).
 * TODO: Интегрировать с реальным провайдером (SendGrid, SMTP и т.д.)
 */
class EmailDeliveryServiceImpl(
    private val smtpHost: String,
    private val smtpPort: Int,
    private val username: String,
    private val password: String
) : EmailDeliveryService {
    
    override suspend fun sendReport(
        email: String,
        subject: String,
        htmlBody: String,
        attachments: List<EmailAttachment>
    ): DeliveryResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // TODO: Реализовать SMTP/SendGrid интеграцию
            // Для интеграции используйте:
            // - JavaMail API для SMTP
            // - SendGrid SDK
            // - AWS SES SDK
            
            throw NotImplementedError("Email delivery not yet implemented. Use MockEmailDeliveryService for DEV/QA.")
        } catch (e: Exception) {
            DeliveryResult(
                success = false,
                channel = DeliveryChannel.EMAIL,
                messageId = null,
                recipient = email,
                error = e.message ?: "Unknown error",
                deliveryTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
    
    override suspend fun isAvailable(): Boolean {
        // TODO: Проверка подключения к SMTP серверу
        return false
    }
}

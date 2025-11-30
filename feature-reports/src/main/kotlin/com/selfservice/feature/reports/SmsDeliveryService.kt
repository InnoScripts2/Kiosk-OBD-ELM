package com.selfservice.feature.reports

/**
 * Интерфейс сервиса доставки отчётов по SMS.
 * 
 * Реализации:
 * - SmsDeliveryServiceImpl: Интеграция с SMS провайдером (Twilio, SMSAero и т.д.)
 * - MockSmsDeliveryService: Mock для DEV/QA режимов
 */
interface SmsDeliveryService {
    
    /**
     * Отправляет краткое резюме отчёта по SMS.
     * 
     * @param phone номер телефона получателя (международный формат)
     * @param message текст сообщения (до 160 символов)
     * @return результат доставки
     */
    suspend fun sendSummary(
        phone: String,
        message: String
    ): DeliveryResult
    
    /**
     * Проверяет доступность SMS сервиса.
     * 
     * @return true если сервис доступен
     */
    suspend fun isAvailable(): Boolean
}

/**
 * Mock реализация SMS сервиса для DEV/QA режимов.
 * Симулирует отправку без реального соединения.
 */
class MockSmsDeliveryService : SmsDeliveryService {
    
    private val sentMessages = mutableListOf<SentSmsRecord>()
    
    override suspend fun sendSummary(
        phone: String,
        message: String
    ): DeliveryResult {
        val startTime = System.currentTimeMillis()
        
        // Валидация номера телефона
        if (!isValidPhone(phone)) {
            return DeliveryResult(
                success = false,
                channel = DeliveryChannel.SMS,
                messageId = null,
                recipient = phone,
                error = "Invalid phone number format",
                deliveryTimeMs = System.currentTimeMillis() - startTime
            )
        }
        
        // Проверка длины сообщения
        if (message.length > 160) {
            return DeliveryResult(
                success = false,
                channel = DeliveryChannel.SMS,
                messageId = null,
                recipient = phone,
                error = "Message exceeds 160 characters limit",
                deliveryTimeMs = System.currentTimeMillis() - startTime
            )
        }
        
        // Симуляция задержки отправки (50-200ms)
        val delay = (50..200).random().toLong()
        kotlinx.coroutines.delay(delay)
        
        // Генерация mock message ID
        val messageId = "mock-sms-${System.currentTimeMillis()}-${phone.hashCode()}"
        
        // Сохранение для тестирования
        sentMessages.add(
            SentSmsRecord(
                phone = phone,
                message = message,
                messageId = messageId,
                sentAtMillis = System.currentTimeMillis()
            )
        )
        
        println("[MOCK SMS] Sent to: $phone")
        println("[MOCK SMS] Message: $message")
        println("[MOCK SMS] Message ID: $messageId")
        
        return DeliveryResult(
            success = true,
            channel = DeliveryChannel.SMS,
            messageId = messageId,
            recipient = phone,
            error = null,
            deliveryTimeMs = System.currentTimeMillis() - startTime
        )
    }
    
    override suspend fun isAvailable(): Boolean = true
    
    /**
     * Получить историю отправленных SMS (для тестов).
     */
    fun getSentMessages(): List<SentSmsRecord> = sentMessages.toList()
    
    /**
     * Очистить историю (для тестов).
     */
    fun clear() {
        sentMessages.clear()
    }
    
    private fun isValidPhone(phone: String): Boolean {
        // Международный формат: +7XXXXXXXXXX или +1XXXXXXXXXX
        val phoneRegex = "^\\+[1-9]\\d{10,14}$".toRegex()
        return phone.matches(phoneRegex)
    }
    
    /**
     * Запись отправленного SMS (для тестов).
     */
    data class SentSmsRecord(
        val phone: String,
        val message: String,
        val messageId: String,
        val sentAtMillis: Long
    )
}

/**
 * Production реализация SMS сервиса (заглушка).
 * TODO: Интегрировать с реальным провайдером (Twilio, SMSAero и т.д.)
 */
class SmsDeliveryServiceImpl(
    private val apiKey: String,
    private val apiSecret: String,
    private val senderId: String
) : SmsDeliveryService {
    
    override suspend fun sendSummary(
        phone: String,
        message: String
    ): DeliveryResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // TODO: Реализовать интеграцию с SMS провайдером
            // Для интеграции используйте:
            // - Twilio SDK
            // - SMSAero API
            // - AWS SNS
            
            throw NotImplementedError("SMS delivery not yet implemented. Use MockSmsDeliveryService for DEV/QA.")
        } catch (e: Exception) {
            DeliveryResult(
                success = false,
                channel = DeliveryChannel.SMS,
                messageId = null,
                recipient = phone,
                error = e.message ?: "Unknown error",
                deliveryTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
    
    override suspend fun isAvailable(): Boolean {
        // TODO: Проверка доступности API
        return false
    }
}

/**
 * Утилиты для форматирования SMS резюме отчётов.
 */
object SmsFormatter {
    
    /**
     * Форматирует резюме отчёта толщиномера для SMS (до 160 символов).
     */
    fun formatThicknessSummary(
        sessionId: String,
        completed: Int,
        total: Int,
        deviations: Int
    ): String {
        return "Толщиномер: Замеров $completed/$total. Отклонений: $deviations. " +
                "Отчёт готов. ID: ${sessionId.take(8)}"
    }
    
    /**
     * Форматирует резюме отчёта диагностики для SMS (до 160 символов).
     */
    fun formatDiagnosticsSummary(
        sessionId: String,
        totalCodes: Int,
        critical: Int,
        warning: Int
    ): String {
        return "Диагностика: Найдено $totalCodes кодов. " +
                "Критичных: $critical, Предупр: $warning. " +
                "Отчёт готов. ID: ${sessionId.take(8)}"
    }
}

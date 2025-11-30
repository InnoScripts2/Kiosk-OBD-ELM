package com.selfservice.obd.core.response

import com.selfservice.obd.core.models.DTC
import com.selfservice.obd.core.models.PID

/**
 * Базовый класс для OBD ответов
 * 
 * @since Session 07B
 */
sealed class ObdResponse {
    abstract val timestamp: Long
    abstract val isSuccess: Boolean
    abstract val rawData: String
}

/**
 * Ответ на запрос PID
 */
data class PidResponse(
    val pid: PID,
    val value: Float,
    val unit: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val isSuccess: Boolean = true,
    override val rawData: String
) : ObdResponse()

/**
 * Ответ на запрос DTC кодов
 */
data class DtcResponse(
    val dtcs: List<DTC>,
    override val timestamp: Long = System.currentTimeMillis(),
    override val isSuccess: Boolean = true,
    override val rawData: String
) : ObdResponse()

/**
 * Ответ на сброс DTC
 */
data class ClearDtcResponse(
    val cleared: Boolean,
    override val timestamp: Long = System.currentTimeMillis(),
    override val isSuccess: Boolean = true,
    override val rawData: String
) : ObdResponse()

/**
 * Ответ с ошибкой
 */
data class ErrorResponse(
    val errorCode: String,
    val errorMessage: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val isSuccess: Boolean = false,
    override val rawData: String
) : ObdResponse()

/**
 * Ответ на инициализацию
 */
data class InitResponse(
    val protocol: String,
    val version: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val isSuccess: Boolean = true,
    override val rawData: String
) : ObdResponse()

/**
 * Ответ с VIN
 */
data class VinResponse(
    val vin: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val isSuccess: Boolean = true,
    override val rawData: String
) : ObdResponse()

/**
 * Ответ с поддерживаемыми PIDs
 */
data class SupportedPidsResponse(
    val supportedPids: Set<String>,
    override val timestamp: Long = System.currentTimeMillis(),
    override val isSuccess: Boolean = true,
    override val rawData: String
) : ObdResponse()

/**
 * Обработчик OBD ответов
 */
interface ObdResponseHandler {
    fun handle(response: ObdResponse)
    fun handleError(error: ErrorResponse)
}

/**
 * Валидатор OBD ответов
 */
object ObdResponseValidator {
    
    /**
     * Проверить, является ли ответ валидным
     */
    fun isValid(rawResponse: String): Boolean {
        if (rawResponse.isBlank()) return false
        
        // Проверяем на ошибки ELM327
        if (isErrorResponse(rawResponse)) return false
        
        // Проверяем на "NO DATA"
        if (rawResponse.contains("NO DATA", ignoreCase = true)) return false
        
        // Проверяем на "SEARCHING..."
        if (rawResponse.contains("SEARCHING", ignoreCase = true)) return false
        
        return true
    }
    
    /**
     * Проверить, является ли ответ ошибкой
     */
    fun isErrorResponse(rawResponse: String): Boolean {
        val errorKeywords = listOf(
            "ERROR",
            "UNABLE",
            "BUS INIT",
            "CAN ERROR",
            "STOPPED"
        )
        
        return errorKeywords.any { rawResponse.contains(it, ignoreCase = true) }
    }
    
    /**
     * Извлечь код ошибки из ответа
     */
    fun extractErrorCode(rawResponse: String): String {
        return when {
            rawResponse.contains("NO DATA") -> "NO_DATA"
            rawResponse.contains("UNABLE TO CONNECT") -> "UNABLE_TO_CONNECT"
            rawResponse.contains("BUS INIT") -> "BUS_INIT_ERROR"
            rawResponse.contains("CAN ERROR") -> "CAN_ERROR"
            rawResponse.contains("STOPPED") -> "STOPPED"
            else -> "UNKNOWN_ERROR"
        }
    }
}

/**
 * Кеш OBD ответов
 */
class ObdResponseCache(
    private val maxSize: Int = 100,
    private val ttlMillis: Long = 60_000 // 1 минута
) {
    private val cache = mutableMapOf<String, CachedResponse>()
    
    data class CachedResponse(
        val response: ObdResponse,
        val cachedAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Добавить ответ в кеш
     */
    fun put(key: String, response: ObdResponse) {
        // Очистка устаревших записей
        cleanExpired()
        
        // Ограничение размера
        if (cache.size >= maxSize) {
            val oldestKey = cache.entries.minByOrNull { it.value.cachedAt }?.key
            oldestKey?.let { cache.remove(it) }
        }
        
        cache[key] = CachedResponse(response)
    }
    
    /**
     * Получить ответ из кеша
     */
    fun get(key: String): ObdResponse? {
        val cached = cache[key] ?: return null
        
        // Проверка TTL
        if (System.currentTimeMillis() - cached.cachedAt > ttlMillis) {
            cache.remove(key)
            return null
        }
        
        return cached.response
    }
    
    /**
     * Очистить кеш
     */
    fun clear() {
        cache.clear()
    }
    
    /**
     * Очистить устаревшие записи
     */
    private fun cleanExpired() {
        val now = System.currentTimeMillis()
        val expired = cache.filter { (_, cached) ->
            now - cached.cachedAt > ttlMillis
        }.keys
        
        expired.forEach { cache.remove(it) }
    }
    
    /**
     * Получить размер кеша
     */
    fun size(): Int = cache.size
}

/**
 * Фабрика для создания OBD ответов
 */
object ObdResponseFactory {
    
    /**
     * Создать ответ из сырых данных
     */
    fun create(rawData: String, commandType: String): ObdResponse {
        if (!ObdResponseValidator.isValid(rawData)) {
            return ErrorResponse(
                errorCode = ObdResponseValidator.extractErrorCode(rawData),
                errorMessage = "Невалидный ответ: $rawData",
                rawData = rawData
            )
        }
        
        return when (commandType) {
            "PID" -> createPidResponse(rawData)
            "DTC" -> createDtcResponse(rawData)
            "CLEAR_DTC" -> createClearDtcResponse(rawData)
            "INIT" -> createInitResponse(rawData)
            "VIN" -> createVinResponse(rawData)
            else -> ErrorResponse(
                errorCode = "UNKNOWN_COMMAND",
                errorMessage = "Неизвестный тип команды: $commandType",
                rawData = rawData
            )
        }
    }
    
    private fun createPidResponse(rawData: String): PidResponse {
        // TODO: implement PID parsing
        return PidResponse(
            pid = PID(),
            value = 0f,
            unit = "",
            rawData = rawData
        )
    }
    
    private fun createDtcResponse(rawData: String): DtcResponse {
        // TODO: implement DTC parsing
        return DtcResponse(
            dtcs = emptyList(),
            rawData = rawData
        )
    }
    
    private fun createClearDtcResponse(rawData: String): ClearDtcResponse {
        return ClearDtcResponse(
            cleared = rawData.contains("OK", ignoreCase = true),
            rawData = rawData
        )
    }
    
    private fun createInitResponse(rawData: String): InitResponse {
        return InitResponse(
            protocol = "AUTO",
            version = "ELM327",
            rawData = rawData
        )
    }
    
    private fun createVinResponse(rawData: String): VinResponse {
        // TODO: implement VIN parsing
        return VinResponse(
            vin = "",
            rawData = rawData
        )
    }
}

/**
 * Коллектор статистики OBD ответов
 */
class ObdResponseStats {
    private var totalRequests = 0
    private var successfulRequests = 0
    private var failedRequests = 0
    private val responseTimes = mutableListOf<Long>()
    
    /**
     * Зарегистрировать запрос
     */
    fun recordRequest(response: ObdResponse, durationMs: Long) {
        totalRequests++
        
        if (response.isSuccess) {
            successfulRequests++
        } else {
            failedRequests++
        }
        
        responseTimes.add(durationMs)
        
        // Ограничиваем размер истории
        if (responseTimes.size > 1000) {
            responseTimes.removeAt(0)
        }
    }
    
    /**
     * Получить процент успешных запросов
     */
    fun getSuccessRate(): Double {
        return if (totalRequests > 0) {
            successfulRequests.toDouble() / totalRequests * 100
        } else {
            0.0
        }
    }
    
    /**
     * Получить среднее время ответа
     */
    fun getAverageResponseTime(): Long {
        return if (responseTimes.isNotEmpty()) {
            responseTimes.average().toLong()
        } else {
            0L
        }
    }
    
    /**
     * Получить статистику
     */
    fun getStats(): Stats {
        return Stats(
            totalRequests = totalRequests,
            successfulRequests = successfulRequests,
            failedRequests = failedRequests,
            successRate = getSuccessRate(),
            averageResponseTime = getAverageResponseTime(),
            minResponseTime = responseTimes.minOrNull() ?: 0L,
            maxResponseTime = responseTimes.maxOrNull() ?: 0L
        )
    }
    
    /**
     * Сбросить статистику
     */
    fun reset() {
        totalRequests = 0
        successfulRequests = 0
        failedRequests = 0
        responseTimes.clear()
    }
    
    data class Stats(
        val totalRequests: Int,
        val successfulRequests: Int,
        val failedRequests: Int,
        val successRate: Double,
        val averageResponseTime: Long,
        val minResponseTime: Long,
        val maxResponseTime: Long
    )
}

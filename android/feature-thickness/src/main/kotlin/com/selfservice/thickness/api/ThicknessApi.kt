package com.selfservice.thickness.api

import com.selfservice.thickness.ThicknessMeasurement
import com.selfservice.thickness.ThicknessMeasurementStateMachine
import com.selfservice.thickness.models.ThicknessReport
import com.selfservice.thickness.models.ThicknessZone
import com.selfservice.thickness.models.ZoneMeasurement
import kotlinx.coroutines.flow.StateFlow

/**
 * API интерфейсы для UI-слоя feature-thickness
 * 
 * Предоставляет высокоуровневые интерфейсы для взаимодействия с толщиномером
 * без непосредственного доступа к низкоуровневым BLE/Device API.
 * 
 * @since Session 13B
 */

/**
 * Главный интерфейс для управления процессом измерения толщины ЛКП
 * 
 * Предоставляет методы для:
 * - Подключения к устройству
 * - Запуска/остановки измерений
 * - Получения прогресса и результатов
 * - Генерации отчётов
 */
interface ThicknessController {
    
    /**
     * Текущее состояние процесса измерений
     * 
     * UI должен подписаться на этот StateFlow для отображения состояния
     */
    val state: StateFlow<ThicknessMeasurementStateMachine.State>
    
    /**
     * Прогресс измерений (0.0 - 1.0)
     * 
     * Вычисляется как (выполнено / всего)
     */
    val progress: StateFlow<Float>
    
    /**
     * Текущие собранные измерения
     */
    val measurements: StateFlow<List<ZoneMeasurement>>
    
    /**
     * Подключиться к толщиномеру
     * 
     * @param deviceAddress MAC-адрес устройства (опционально, если null - выполняется сканирование)
     * @return Result с успехом или ошибкой подключения
     */
    suspend fun connect(deviceAddress: String? = null): Result<Unit>
    
    /**
     * Отключиться от толщиномера
     */
    suspend fun disconnect()
    
    /**
     * Запустить процесс измерений
     * 
     * Устройство должно быть подключено (state = Ready)
     * 
     * @param zones Список зон для измерения (по умолчанию - все 60 зон)
     * @return Result с успехом или ошибкой
     */
    suspend fun startMeasurements(zones: List<ThicknessZone> = emptyList()): Result<Unit>
    
    /**
     * Остановить процесс измерений
     */
    suspend fun stopMeasurements()
    
    /**
     * Повторить попытку после ошибки
     * 
     * Доступно только в состоянии Error
     */
    suspend fun retry(): Result<Unit>
    
    /**
     * Сбросить состояние (вернуться в Idle)
     * 
     * Все собранные измерения будут потеряны
     */
    suspend fun reset()
    
    /**
     * Получить текущий отчёт
     * 
     * Доступно после завершения измерений (state = Completed)
     * 
     * @param vehicleType Тип автомобиля для отчёта
     * @return ThicknessReport или null если измерения не завершены
     */
    fun generateReport(vehicleType: String): ThicknessReport?
}

/**
 * Интерфейс для наблюдения за прогрессом измерений
 * 
 * Используется UI для отображения прогресса в реальном времени
 */
interface ThicknessProgressObserver {
    
    /**
     * Общий прогресс (0.0 - 1.0)
     */
    val overallProgress: StateFlow<Float>
    
    /**
     * Количество выполненных измерений
     */
    val completedCount: StateFlow<Int>
    
    /**
     * Количество всего измерений
     */
    val totalCount: StateFlow<Int>
    
    /**
     * Текущая измеряемая зона
     */
    val currentZone: StateFlow<ThicknessZone?>
    
    /**
     * Оставшееся время (оценка в секундах)
     */
    val estimatedTimeRemaining: StateFlow<Long>
    
    /**
     * Статус измерения по зонам
     * 
     * Map: zoneId -> ZoneMeasurement
     */
    val zoneStatuses: StateFlow<Map<String, ZoneMeasurement>>
}

/**
 * Интерфейс для конфигурирования процесса измерений
 * 
 * Позволяет настраивать поведение толщиномера
 */
interface ThicknessConfiguration {
    
    /**
     * Установить таймаут подключения
     * 
     * @param timeoutMs Таймаут в миллисекундах (по умолчанию 5000)
     */
    fun setConnectionTimeout(timeoutMs: Long)
    
    /**
     * Установить таймаут измерения
     * 
     * @param timeoutMs Таймаут в миллисекундах (по умолчанию 30000)
     */
    fun setMeasurementTimeout(timeoutMs: Long)
    
    /**
     * Установить режим работы
     * 
     * @param mode DEV (мок), QA (реальное устройство с логами), PROD (реальное устройство)
     */
    fun setDeviceMode(mode: DeviceMode)
    
    /**
     * Установить список зон для измерения
     * 
     * @param zones Список зон (по умолчанию - все 60 зон)
     */
    fun setMeasurementZones(zones: List<ThicknessZone>)
    
    /**
     * Установить паттерн имени устройства для поиска
     * 
     * @param pattern Регулярное выражение или простая строка
     */
    fun setDeviceNamePattern(pattern: String)
    
    enum class DeviceMode {
        DEV, QA, PROD
    }
}

/**
 * Интерфейс для управления повторными попытками подключения
 * 
 * Предоставляет стратегии для reconnect logic
 */
interface ThicknessReconnectStrategy {
    
    /**
     * Определить, нужно ли повторять попытку
     * 
     * @param attemptNumber Номер попытки (начиная с 1)
     * @param lastError Последняя произошедшая ошибка
     * @return true если нужно повторить попытку
     */
    fun shouldRetry(attemptNumber: Int, lastError: Throwable): Boolean
    
    /**
     * Вычислить задержку перед следующей попыткой
     * 
     * @param attemptNumber Номер попытки
     * @return Задержка в миллисекундах
     */
    fun getRetryDelay(attemptNumber: Int): Long
    
    /**
     * Максимальное количество попыток
     */
    val maxAttempts: Int
}

/**
 * Стандартная стратегия с exponential backoff
 */
class ExponentialBackoffReconnectStrategy(
    override val maxAttempts: Int = 3,
    private val baseDelayMs: Long = 2000L,
    private val maxDelayMs: Long = 16000L
) : ThicknessReconnectStrategy {
    
    override fun shouldRetry(attemptNumber: Int, lastError: Throwable): Boolean {
        return attemptNumber < maxAttempts
    }
    
    override fun getRetryDelay(attemptNumber: Int): Long {
        val delay = baseDelayMs * (1 shl (attemptNumber - 1)) // 2^(n-1)
        return minOf(delay, maxDelayMs)
    }
}

/**
 * Интерфейс для валидации измерений
 * 
 * Предоставляет методы для проверки корректности измерений
 */
interface ThicknessMeasurementValidator {
    
    /**
     * Валидировать одно измерение
     * 
     * @param measurement Измерение для проверки
     * @return Result с валидированным измерением или ошибкой
     */
    fun validate(measurement: ZoneMeasurement): Result<ZoneMeasurement>
    
    /**
     * Валидировать набор измерений
     * 
     * @param measurements Список измерений
     * @return ValidationResult с результатами валидации
     */
    fun validateAll(measurements: List<ZoneMeasurement>): ValidationResult
    
    /**
     * Результат валидации
     */
    data class ValidationResult(
        val valid: List<ZoneMeasurement>,
        val invalid: List<Pair<ZoneMeasurement, String>>,
        val warnings: List<Pair<ZoneMeasurement, String>>
    ) {
        val isValid: Boolean get() = invalid.isEmpty()
        val hasWarnings: Boolean get() = warnings.isNotEmpty()
        val validPercentage: Float get() = if (valid.isEmpty() && invalid.isEmpty()) 0f else valid.size.toFloat() / (valid.size + invalid.size)
    }
}

/**
 * Интерфейс для форматирования и экспорта данных
 * 
 * Предоставляет методы для генерации различных форматов отчётов
 */
interface ThicknessDataExporter {
    
    /**
     * Экспортировать в CSV формат
     * 
     * @param measurements Список измерений
     * @return CSV строка
     */
    fun toCsv(measurements: List<ZoneMeasurement>): String
    
    /**
     * Экспортировать в JSON формат
     * 
     * @param measurements Список измерений
     * @return JSON строка
     */
    fun toJson(measurements: List<ZoneMeasurement>): String
    
    /**
     * Экспортировать в HTML формат
     * 
     * @param report Полный отчёт
     * @return HTML строка
     */
    fun toHtml(report: ThicknessReport): String
    
    /**
     * Экспортировать в PDF формат (байты)
     * 
     * @param report Полный отчёт
     * @return PDF байты
     */
    suspend fun toPdf(report: ThicknessReport): ByteArray
}

/**
 * Интерфейс для кэширования результатов измерений
 * 
 * Позволяет сохранять и восстанавливать состояние измерений
 */
interface ThicknessMeasurementCache {
    
    /**
     * Сохранить измерения
     * 
     * @param sessionId ID сессии
     * @param measurements Список измерений
     */
    suspend fun save(sessionId: String, measurements: List<ZoneMeasurement>)
    
    /**
     * Загрузить измерения
     * 
     * @param sessionId ID сессии
     * @return Список измерений или null если не найдено
     */
    suspend fun load(sessionId: String): List<ZoneMeasurement>?
    
    /**
     * Удалить измерения
     * 
     * @param sessionId ID сессии
     */
    suspend fun delete(sessionId: String)
    
    /**
     * Получить список всех сохранённых сессий
     * 
     * @return Список ID сессий
     */
    suspend fun listSessions(): List<String>
}

/**
 * События для UI
 * 
 * Используются для оповещения UI о важных событиях
 */
sealed class ThicknessUiEvent {
    /** Устройство подключено */
    data class Connected(val deviceName: String, val deviceAddress: String) : ThicknessUiEvent()
    
    /** Устройство отключено */
    object Disconnected : ThicknessUiEvent()
    
    /** Измерения начаты */
    data class MeasurementStarted(val totalZones: Int) : ThicknessUiEvent()
    
    /** Получено новое измерение */
    data class MeasurementReceived(val zone: ThicknessZone, val value: Float) : ThicknessUiEvent()
    
    /** Все измерения завершены */
    data class MeasurementCompleted(val report: ThicknessReport) : ThicknessUiEvent()
    
    /** Произошла ошибка */
    data class Error(val message: String, val error: Throwable?) : ThicknessUiEvent()
    
    /** Предупреждение */
    data class Warning(val message: String) : ThicknessUiEvent()
}

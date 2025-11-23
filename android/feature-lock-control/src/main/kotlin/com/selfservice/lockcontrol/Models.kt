package com.selfservice.lockcontrol

/**
 * Типы устройств, которые можно выдать клиенту
 */
enum class DeviceType {
    /** Толщиномер для измерения ЛКП */
    THICKNESS,
    
    /** OBD-II адаптер для диагностики */
    ADAPTER
}

/**
 * Команды для Arduino dispenser
 */
enum class ArduinoCommand(val commandText: String) {
    /** Открыть слот толщиномера */
    OPEN_THICKNESS("OPEN_THICKNESS"),
    
    /** Открыть слот OBD-адаптера */
    OPEN_OBD("OPEN_OBD"),
    
    /** Закрыть слот толщиномера */
    CLOSE_THICKNESS("CLOSE_THICKNESS"),
    
    /** Закрыть слот OBD-адаптера */
    CLOSE_OBD("CLOSE_OBD"),
    
    /** Запросить статус обоих слотов */
    STATUS("STATUS"),
    
    /** Ping для проверки соединения */
    PING("PING");
    
    companion object {
        /**
         * Получить команду для открытия слота устройства
         */
        fun openCommand(deviceType: DeviceType): ArduinoCommand = when (deviceType) {
            DeviceType.THICKNESS -> OPEN_THICKNESS
            DeviceType.ADAPTER -> OPEN_OBD
        }
        
        /**
         * Получить команду для закрытия слота устройства
         */
        fun closeCommand(deviceType: DeviceType): ArduinoCommand = when (deviceType) {
            DeviceType.THICKNESS -> CLOSE_THICKNESS
            DeviceType.ADAPTER -> CLOSE_OBD
        }
    }
}

/**
 * Ответ от Arduino
 */
sealed class ArduinoResponse {
    abstract val raw: String
    
    /** Успешное выполнение команды */
    data class Ok(
        override val raw: String,
        val action: String?,
        val device: String?
    ) : ArduinoResponse()
    
    /** Ошибка выполнения команды */
    data class Error(
        override val raw: String,
        val errorType: String,
        val device: String?
    ) : ArduinoResponse()
    
    /** Статус обоих замков */
    data class Status(
        override val raw: String,
        val thicknessOpen: Boolean,
        val obdOpen: Boolean
    ) : ArduinoResponse()
    
    /** Ответ на PING */
    data class Pong(override val raw: String) : ArduinoResponse()
    
    /** Лог-сообщение от Arduino */
    data class Log(
        override val raw: String,
        val timestamp: String?,
        val message: String
    ) : ArduinoResponse()
    
    /** Готовность системы */
    data class Ready(
        override val raw: String,
        val version: String?
    ) : ArduinoResponse()
}

/**
 * Состояние замка (открыт/закрыт)
 */
enum class LockState {
    /** Замок закрыт */
    CLOSED,
    
    /** Замок открыт */
    OPEN
}

/**
 * Статус всех замков
 */
data class LockStatus(
    /** Состояние замка толщиномера */
    val thickness: LockState,
    
    /** Состояние замка OBD-адаптера */
    val adapter: LockState,
    
    /** Подключение к Arduino установлено */
    val connected: Boolean,
    
    /** Сообщение об ошибке (если есть) */
    val error: String? = null
)

/**
 * Событие лога операции с замком
 */
data class LockOperationLog(
    /** Временная метка */
    val timestamp: Long,
    
    /** Действие (open/close) */
    val action: String,
    
    /** Тип устройства */
    val deviceType: DeviceType,
    
    /** Успешность операции */
    val success: Boolean,
    
    /** Состояние после операции */
    val status: LockState,
    
    /** Сообщение об ошибке (если есть) */
    val error: String? = null,
    
    /** ID сессии */
    val sessionId: String
)

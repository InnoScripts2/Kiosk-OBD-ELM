/**
 * Адаптированный класс команд OBD из донорского проекта рес 7 (Android OBD Library)
 * Оригинальная лицензия: Apache License 2.0
 * Источник: рес 7/рес 7/obd/src/main/java/com/pnuema/android/obd/commands/BaseObdCommand.kt
 * Автор оригинала: Brad Barnhill
 * 
 * Адаптация: Упрощена архитектура, убраны зависимости от InputStream/OutputStream,
 * работа через строковые команды и ответы
 */
package com.selfservice.obd.core.donor.commands

import com.selfservice.obd.core.donor.enums.ObdMode
import com.selfservice.obd.core.donor.models.ObdPid

/**
 * Базовый класс для OBD команд
 * 
 * Предоставляет общую логику для формирования команд, парсинга ответов
 * и обработки данных от ELM327 адаптера
 */
abstract class ObdCommand {
    companion object {
        const val NO_DATA = "NODATA"
        const val SEARCHING = "SEARCHING"
        const val DATA = "DATA"
        const val ELM327 = "ELM327"
        const val UNABLE_TO_CONNECT = "UNABLETOCONNECT"
        const val BUS_INIT_ERROR = "BUSINITERROR"
        const val OK = "OK"
        const val PROMPT = ">"
    }
    
    /**
     * Буфер с данными ответа (байты в десятичном виде)
     */
    protected val buffer: MutableList<Int> = mutableListOf()
    
    /**
     * Сырой ответ от адаптера
     */
    protected var rawResponse: String = ""
    
    /**
     * Использовать имперские единицы измерения
     */
    var useImperialUnits: Boolean = false
    
    /**
     * Игнорировать результат (для команд без ожидания ответа)
     */
    var ignoreResult: Boolean = false
    
    /**
     * Формирует команду для отправки адаптеру
     * 
     * @return строка команды (например, "01 0D")
     */
    abstract fun buildCommand(): String
    
    /**
     * Парсит ответ от адаптера и заполняет buffer
     * 
     * @param response сырой ответ от адаптера
     */
    open fun parseResponse(response: String) {
        rawResponse = response.trim()
        
        // Проверяем на ошибки
        if (isErrorResponse(rawResponse)) {
            buffer.clear()
            return
        }
        
        // Удаляем эхо команды, промпты и служебные символы
        val cleanResponse = cleanResponse(rawResponse)
        
        // Парсим hex байты
        buffer.clear()
        val parts = cleanResponse.split("\\s+".toRegex())
        for (part in parts) {
            if (part.isEmpty()) continue
            try {
                val value = part.toInt(16)
                buffer.add(value)
            } catch (e: NumberFormatException) {
                // Игнорируем некорректные значения
            }
        }
    }
    
    /**
     * Возвращает форматированный результат
     * 
     * @return строковое представление результата
     */
    abstract fun getFormattedResult(): String
    
    /**
     * Возвращает сырой ответ
     * 
     * @return сырой ответ от адаптера
     */
    fun getRawResult(): String = rawResponse
    
    /**
     * Проверяет, является ли ответ ошибочным
     */
    protected fun isErrorResponse(response: String): Boolean {
        return response.contains(NO_DATA) ||
               response.contains(UNABLE_TO_CONNECT) ||
               response.contains(BUS_INIT_ERROR) ||
               response.contains(SEARCHING) ||
               response.isEmpty()
    }
    
    /**
     * Очищает ответ от служебных символов
     */
    protected fun cleanResponse(response: String): String {
        return response
            .replace(SEARCHING, "")
            .replace(DATA, "")
            .replace(ELM327, "")
            .replace(OK, "")
            .replace(PROMPT, "")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
    }
}

/**
 * Команда для чтения PID
 */
class PidCommand(
    private val mode: ObdMode,
    private val pid: ObdPid
) : ObdCommand() {
    
    override fun buildCommand(): String {
        return "${mode.value}${pid.pid}"
    }
    
    override fun getFormattedResult(): String {
        if (buffer.isEmpty() || buffer.size < 2) {
            return "No data"
        }
        
        // Первые два байта - эхо режима и PID, данные начинаются с 3-го байта
        val dataBytes = if (buffer.size > 2) buffer.subList(2, buffer.size) else emptyList()
        
        // Применяем формулу если есть
        val formula = if (useImperialUnits) pid.imperialFormula else pid.formula
        if (formula != null) {
            return calculateFromFormula(formula, dataBytes)
        }
        
        return dataBytes.joinToString(" ") { it.toString(16).uppercase() }
    }
    
    private fun calculateFromFormula(formula: String, data: List<Int>): String {
        // Упрощённая реализация расчёта по формуле
        // В полной реализации нужен парсер математических выражений
        try {
            var result = formula
            for (i in data.indices) {
                val varName = "${'A' + i}"
                if (result.contains(varName)) {
                    result = result.replace(varName, data[i].toString())
                }
            }
            // Здесь должен быть eval математического выражения
            // Для примера просто возвращаем первое значение
            return if (data.isNotEmpty()) data[0].toString() else "0"
        } catch (e: Exception) {
            return "Error"
        }
    }
}

/**
 * Команда для чтения DTC
 */
class ReadDtcCommand : ObdCommand() {
    override fun buildCommand(): String = "03"
    
    override fun getFormattedResult(): String {
        if (buffer.isEmpty()) return "No DTCs"
        
        val dtcCount = buffer[0]
        if (dtcCount == 0) return "No DTCs"
        
        // Парсим DTC коды (по 2 байта на код)
        val dtcs = mutableListOf<String>()
        for (i in 1 until buffer.size step 2) {
            if (i + 1 < buffer.size) {
                val dtcCode = parseDtcFromBytes(buffer[i], buffer[i + 1])
                if (dtcCode != null) dtcs.add(dtcCode)
            }
        }
        
        return dtcs.joinToString(", ")
    }
    
    private fun parseDtcFromBytes(byte1: Int, byte2: Int): String? {
        if (byte1 == 0 && byte2 == 0) return null
        
        val prefix = when ((byte1 shr 6) and 0x03) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            3 -> 'U'
            else -> '?'
        }
        
        val d1 = (byte1 shr 4) and 0x03
        val d2 = byte1 and 0x0F
        val d3 = (byte2 shr 4) and 0x0F
        val d4 = byte2 and 0x0F
        
        return "$prefix$d1${d2.toString(16).uppercase()}${d3.toString(16).uppercase()}${d4.toString(16).uppercase()}"
    }
}

/**
 * Команда для очистки DTC
 */
class ClearDtcCommand : ObdCommand() {
    override fun buildCommand(): String = "04"
    
    override fun getFormattedResult(): String {
        return if (rawResponse.contains(OK)) "DTCs cleared" else "Failed to clear DTCs"
    }
}

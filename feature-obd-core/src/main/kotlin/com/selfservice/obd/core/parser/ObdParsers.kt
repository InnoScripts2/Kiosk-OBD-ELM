package com.selfservice.obd.core.parser

import com.selfservice.obd.core.models.DTC
import com.selfservice.obd.core.models.DTCS

/**
 * Парсер DTC кодов из OBD ответов
 * 
 * Преобразует сырые данные из OBD-адаптера в структурированные DTC объекты.
 * Поддерживает все типы DTC кодов (P, C, B, U).
 * 
 * @since Session 07B
 */
class DtcParser {
    
    /**
     * Распарсить DTC коды из сырого ответа
     * 
     * @param rawResponse Сырой ответ от адаптера (напр. "43 01 33 01 34")
     * @return Список DTC кодов
     */
    fun parse(rawResponse: String): List<DTC> {
        val dtcs = mutableListOf<DTC>()
        
        // Удаляем пробелы и разбиваем на байты
        val bytes = rawResponse.replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")
            .chunked(2)
            .mapNotNull { it.toIntOrNull(16) }
        
        // Первый байт - это режим ответа (43 для mode 03)
        if (bytes.isEmpty() || bytes[0] != 0x43) {
            return emptyList()
        }
        
        // Второй байт - количество кодов
        if (bytes.size < 2) {
            return emptyList()
        }
        
        val count = bytes[1]
        
        // Каждый DTC занимает 2 байта
        var index = 2
        repeat(count) {
            if (index + 1 < bytes.size) {
                val byte1 = bytes[index]
                val byte2 = bytes[index + 1]
                
                val dtcCode = parseDtcCode(byte1, byte2)
                if (dtcCode != null) {
                    dtcs.add(DTC(code = dtcCode))
                }
                
                index += 2
            }
        }
        
        return dtcs
    }
    
    /**
     * Распарсить DTCS объект из сырого ответа
     */
    fun parseDtcs(rawResponse: String): DTCS {
        val dtcList = parse(rawResponse)
        return DTCS(dtcs = dtcList)
    }
    
    /**
     * Преобразовать два байта в DTC код
     * 
     * Формат DTC кода:
     * - Первые 2 бита определяют тип (P, C, B, U)
     * - Следующие 2 бита - первая цифра
     * - Оставшиеся 12 бит - остальные 3 цифры
     * 
     * @param byte1 Первый байт
     * @param byte2 Второй байт
     * @return DTC код (напр. "P0133") или null если невалидный
     */
    private fun parseDtcCode(byte1: Int, byte2: Int): String? {
        // Определяем префикс по первым 2 битам
        val prefix = when ((byte1 shr 6) and 0x03) {
            0 -> "P" // Powertrain
            1 -> "C" // Chassis
            2 -> "B" // Body
            3 -> "U" // Network
            else -> return null
        }
        
        // Первая цифра (биты 5-4 первого байта)
        val digit1 = (byte1 shr 4) and 0x03
        
        // Вторая цифра (биты 3-0 первого байта)
        val digit2 = byte1 and 0x0F
        
        // Третья и четвертая цифры (весь второй байт)
        val digit3 = (byte2 shr 4) and 0x0F
        val digit4 = byte2 and 0x0F
        
        return "$prefix$digit1$digit2${digit3.toString(16).uppercase()}${digit4.toString(16).uppercase()}"
    }
    
    /**
     * Проверить, является ли строка валидным DTC кодом
     */
    fun isValidDtcCode(code: String): Boolean {
        if (code.length != 5) return false
        
        val prefix = code[0]
        if (prefix !in setOf('P', 'C', 'B', 'U')) return false
        
        val digits = code.substring(1)
        return digits.all { it.isDigit() || it in 'A'..'F' }
    }
    
    /**
     * Определить тип системы по DTC коду
     */
    fun getSystemType(code: String): String? {
        if (!isValidDtcCode(code)) return null
        
        val prefix = code[0]
        val firstDigit = code[1].digitToIntOrNull() ?: return null
        
        return when (prefix) {
            'P' -> when (firstDigit) {
                0, 2 -> "Общие коды (стандарт ISO/SAE)"
                1, 3 -> "Коды производителя"
                else -> "Неизвестная система"
            }
            'C' -> "Шасси"
            'B' -> "Кузов"
            'U' -> "Сеть"
            else -> null
        }
    }
}

/**
 * Парсер для freeze frame данных
 * 
 * Freeze frame содержит снимок параметров двигателя
 * в момент возникновения ошибки
 */
class FreezeFrameParser {
    
    /**
     * Распарсить freeze frame данные
     * 
     * @param rawResponse Сырой ответ от адаптера
     * @return Карта параметров (PID -> значение)
     */
    fun parse(rawResponse: String): Map<String, Any> {
        val data = mutableMapOf<String, Any>()
        
        val bytes = rawResponse.replace(" ", "")
            .chunked(2)
            .mapNotNull { it.toIntOrNull(16) }
        
        if (bytes.isEmpty() || bytes[0] != 0x42) {
            return emptyMap()
        }
        
        // Frame number
        if (bytes.size >= 2) {
            data["frame"] = bytes[1]
        }
        
        // Парсим PIDs (формат зависит от конкретного PID)
        var index = 2
        while (index < bytes.size) {
            if (index + 1 < bytes.size) {
                val pid = bytes[index]
                val value = bytes[index + 1]
                
                data["pid_${pid.toString(16).uppercase()}"] = value
                index += 2
            } else {
                break
            }
        }
        
        return data
    }
}

/**
 * Парсер для VIN (Vehicle Identification Number)
 */
class VinParser {
    
    /**
     * Распарсить VIN из OBD ответа
     * 
     * @param rawResponse Сырой ответ (Mode 09 PID 02)
     * @return VIN строка или null
     */
    fun parse(rawResponse: String): String? {
        val bytes = rawResponse.replace(" ", "")
            .chunked(2)
            .mapNotNull { it.toIntOrNull(16) }
        
        if (bytes.isEmpty() || bytes[0] != 0x49) {
            return null
        }
        
        // Пропускаем mode и PID
        val vinBytes = bytes.drop(2)
        
        // VIN = 17 символов ASCII
        if (vinBytes.size < 17) {
            return null
        }
        
        return vinBytes.take(17)
            .map { it.toChar() }
            .joinToString("")
    }
    
    /**
     * Валидировать VIN
     * 
     * VIN должен содержать 17 символов без I, O, Q
     */
    fun isValidVin(vin: String): Boolean {
        if (vin.length != 17) return false
        
        val invalidChars = setOf('I', 'O', 'Q')
        return vin.all { it.isLetterOrDigit() && it.uppercaseChar() !in invalidChars }
    }
    
    /**
     * Извлечь информацию из VIN
     */
    fun parseVinInfo(vin: String): VinInfo? {
        if (!isValidVin(vin)) return null
        
        return VinInfo(
            wmi = vin.substring(0, 3),           // World Manufacturer Identifier
            vds = vin.substring(3, 9),           // Vehicle Descriptor Section
            vis = vin.substring(9, 17),          // Vehicle Identifier Section
            year = decodeYear(vin[9]),
            manufacturer = decodeManufacturer(vin.substring(0, 3))
        )
    }
    
    private fun decodeYear(char: Char): Int? {
        // 10-я позиция VIN - год выпуска
        return when (char) {
            in 'A'..'Y' -> {
                val offset = char - 'A'
                1980 + offset + (if (offset >= 17) 30 else 0)
            }
            in '1'..'9' -> 2001 + (char - '1')
            else -> null
        }
    }
    
    private fun decodeManufacturer(wmi: String): String {
        // Упрощенная таблица производителей
        return when {
            wmi.startsWith("1") || wmi.startsWith("4") || wmi.startsWith("5") -> "США"
            wmi.startsWith("2") -> "Канада"
            wmi.startsWith("3") -> "Мексика"
            wmi.startsWith("J") -> "Япония"
            wmi.startsWith("K") -> "Корея"
            wmi.startsWith("L") -> "Китай"
            wmi.startsWith("S") -> "Великобритания"
            wmi.startsWith("W") -> "Германия"
            wmi.startsWith("V") -> "Франция/Испания"
            wmi.startsWith("Z") -> "Италия"
            else -> "Неизвестно"
        }
    }
}

/**
 * Информация из VIN
 */
data class VinInfo(
    val wmi: String,
    val vds: String,
    val vis: String,
    val year: Int?,
    val manufacturer: String
)

/**
 * Парсер для калибровочных идентификаторов (Mode 09 PID 04)
 */
class CalibrationIdParser {
    
    fun parse(rawResponse: String): List<String> {
        val bytes = rawResponse.replace(" ", "")
            .chunked(2)
            .mapNotNull { it.toIntOrNull(16) }
        
        if (bytes.isEmpty() || bytes[0] != 0x49) {
            return emptyList()
        }
        
        // Пропускаем mode и PID
        val dataBytes = bytes.drop(2)
        
        // Калибровочный ID - ASCII строки
        val ids = mutableListOf<String>()
        var currentId = StringBuilder()
        
        for (byte in dataBytes) {
            if (byte == 0x00) {
                if (currentId.isNotEmpty()) {
                    ids.add(currentId.toString())
                    currentId = StringBuilder()
                }
            } else {
                currentId.append(byte.toChar())
            }
        }
        
        if (currentId.isNotEmpty()) {
            ids.add(currentId.toString())
        }
        
        return ids
    }
}

/**
 * Парсер для ECU имени (Mode 09 PID 0A)
 */
class EcuNameParser {
    
    fun parse(rawResponse: String): String? {
        val bytes = rawResponse.replace(" ", "")
            .chunked(2)
            .mapNotNull { it.toIntOrNull(16) }
        
        if (bytes.isEmpty() || bytes[0] != 0x49) {
            return null
        }
        
        // Пропускаем mode и PID
        val nameBytes = bytes.drop(2)
        
        // Имя ECU - ASCII строка до 20 символов
        return nameBytes
            .takeWhile { it != 0x00 }
            .map { it.toChar() }
            .joinToString("")
            .trim()
    }
}

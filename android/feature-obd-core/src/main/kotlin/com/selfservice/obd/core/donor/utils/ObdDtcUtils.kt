/**
 * Адаптированный класс DTCUtils из донорского проекта рес 7 (Android OBD Library)
 * Оригинальная лицензия: Apache License 2.0
 * Источник: рес 7/рес 7/obd/src/main/java/com/pnuema/android/obd/statics/DTCUtils.kt
 * Автор оригинала: Brad Barnhill
 * 
 * Адаптация: Упрощён для работы без Android-специфичных зависимостей
 */
package com.selfservice.obd.core.donor.utils

import com.selfservice.obd.core.donor.models.ObdDtc
import com.selfservice.obd.core.donor.models.ObdDtcList
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Утилиты для работы с DTC (Diagnostic Trouble Codes)
 * 
 * Предоставляет методы для загрузки словаря кодов ошибок
 */
object ObdDtcUtils {
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Загружает список DTC кодов из JSON файла
     * 
     * @param loader функция для загрузки JSON файла (например, из assets)
     * @return список DTC кодов с описаниями
     * @throws IOException если не удалось прочитать данные
     */
    @Throws(IOException::class)
    fun loadDtcList(loader: (String) -> String): List<ObdDtc> {
        val jsonContent = loader("dtc-codes.json")
        return json.decodeFromString<ObdDtcList>(jsonContent).dtcs
    }
    
    /**
     * Парсит DTC код из сырых байтов ответа адаптера
     * 
     * Формат DTC: первый символ определяется двумя битами первого байта,
     * остальные 14 бит кодируют hex-число
     * 
     * @param byte1 первый байт
     * @param byte2 второй байт
     * @return строка DTC кода (например, "P0301") или null
     */
    fun parseDtcCode(byte1: Int, byte2: Int): String? {
        if (byte1 == 0 && byte2 == 0) return null
        
        val prefix = when ((byte1 shr 6) and 0x03) {
            0 -> 'P'  // Powertrain
            1 -> 'C'  // Chassis
            2 -> 'B'  // Body
            3 -> 'U'  // Network
            else -> '?'
        }
        
        val digit1 = (byte1 shr 4) and 0x03
        val digit2 = byte1 and 0x0F
        val digit3 = (byte2 shr 4) and 0x0F
        val digit4 = byte2 and 0x0F
        
        return "$prefix$digit1${digit2.toString(16).uppercase()}${digit3.toString(16).uppercase()}${digit4.toString(16).uppercase()}"
    }
}

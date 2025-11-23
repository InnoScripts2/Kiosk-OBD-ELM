/**
 * Адаптированный класс PIDUtils из донорского проекта рес 7 (Android OBD Library)
 * Оригинальная лицензия: Apache License 2.0
 * Источник: рес 7/рес 7/obd/src/main/java/com/pnuema/android/obd/statics/PIDUtils.kt
 * Автор оригинала: Brad Barnhill
 * 
 * Адаптация: Упрощён для работы без Android-специфичных зависимостей,
 * использует наш loader вместо FileUtils
 */
package com.selfservice.obd.core.donor.utils

import com.selfservice.obd.core.donor.enums.ObdMode
import com.selfservice.obd.core.donor.models.ObdPid
import com.selfservice.obd.core.donor.models.ObdPidList
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.*

/**
 * Утилиты для работы с PID (Parameter IDs)
 * 
 * Предоставляет методы для загрузки и кэширования словаря PID,
 * получения списка PID для режима и получения конкретного PID по коду.
 */
object ObdPidUtils {
    private val pidCache = mutableMapOf<Int, SortedMap<Int, ObdPid>>()
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Загружает список PID для указанного режима
     * 
     * @param mode OBD режим
     * @param loader функция для загрузки JSON файла (например, из assets)
     * @return список PID
     * @throws IOException если не удалось прочитать данные
     */
    @Throws(IOException::class)
    fun loadPidList(mode: ObdMode, loader: (String) -> String): List<ObdPid> {
        return ArrayList(loadPidMap(mode, loader).values)
    }
    
    /**
     * Получает конкретный PID по режиму и коду
     * 
     * @param mode OBD режим
     * @param pidCode код PID в hex формате (например, "0D")
     * @param loader функция для загрузки JSON файла
     * @return объект PID или null если не найден
     * @throws IOException если не удалось прочитать данные
     */
    @Throws(IOException::class)
    fun getPid(mode: ObdMode, pidCode: String, loader: (String) -> String): ObdPid? {
        val pidMap = loadPidMap(mode, loader)
        return pidMap[pidCode.toInt(16)]
    }
    
    /**
     * Загружает карту PID для режима с кэшированием
     * 
     * @param mode OBD режим
     * @param loader функция для загрузки JSON файла
     * @return отсортированная карта PID (ключ - integer код PID)
     * @throws IOException если не удалось прочитать данные
     */
    @Throws(IOException::class)
    private fun loadPidMap(mode: ObdMode, loader: (String) -> String): SortedMap<Int, ObdPid> {
        val modeInt = mode.intValue
        
        // Проверяем кэш
        pidCache[modeInt]?.let { return it }
        
        // Загружаем из JSON
        val fileName = "pids-mode${modeInt}.json"
        val jsonContent = loader(fileName)
        val pidList = json.decodeFromString<ObdPidList>(jsonContent).pids
        
        // Преобразуем в карту с integer ключами
        val pidMap = TreeMap<Int, ObdPid>()
        pidList.forEach { pid ->
            try {
                val pidInt = pid.pid.toInt(16)
                pidMap[pidInt] = pid
            } catch (e: NumberFormatException) {
                // Игнорируем некорректные PID коды
            }
        }
        
        require(pidMap.isNotEmpty()) { "Unsupported mode requested: $mode" }
        
        // Сохраняем в кэш
        pidCache[modeInt] = pidMap
        return pidMap
    }
    
    /**
     * Очищает кэш PID (полезно для тестов)
     */
    fun clearCache() {
        pidCache.clear()
    }
}

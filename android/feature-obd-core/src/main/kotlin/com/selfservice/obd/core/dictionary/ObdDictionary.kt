/**
 * OBD Dictionary - загружает и предоставляет доступ к словарям PID и DTC
 * Интегрирует донорский код из рес 7 с архитектурой feature-obd-core
 */
package com.selfservice.obd.core.dictionary

import android.content.res.AssetManager
import com.pnuema.android.obd.models.DTC
import com.pnuema.android.obd.models.DTCS
import com.pnuema.android.obd.models.PID
import com.pnuema.android.obd.models.PIDS
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Загрузчик словаря OBD-II PID и DTC из assets
 * Использует JSON-файлы из донорского проекта рес 7
 */
class ObdDictionary private constructor(
    private val pidsMode1: PIDS,
    private val pidsMode4: PIDS,
    private val pidsMode9: PIDS,
    private val dtcCodes: DTCS
) {
    
    /**
     * Получить PID по режиму и коду
     * @param mode режим OBD (01, 04, 09)
     * @param pidCode код PID (например, "0C")
     * @return PID или null если не найден
     */
    fun getPid(mode: String, pidCode: String): PID? {
        val pids = when (mode) {
            "01" -> pidsMode1
            "04" -> pidsMode4
            "09" -> pidsMode9
            else -> return null
        }
        return pids.pids.find { it.PID.equals(pidCode, ignoreCase = true) }
    }
    
    /**
     * Получить все PID для указанного режима
     */
    fun getPidsForMode(mode: String): List<PID> {
        return when (mode) {
            "01" -> pidsMode1.pids
            "04" -> pidsMode4.pids
            "09" -> pidsMode9.pids
            else -> emptyList()
        }
    }
    
    /**
     * Получить описание DTC кода
     * @param code код ошибки (например, "P0420")
     * @return DTC с описанием или null
     */
    fun getDtc(code: String): DTC? {
        return dtcCodes.dtcs.find { it.code.equals(code, ignoreCase = true) }
    }
    
    /**
     * Получить список всех DTC кодов
     */
    fun getAllDtcs(): List<DTC> {
        return dtcCodes.dtcs
    }
    
    companion object {
        private val json = Json { 
            ignoreUnknownKeys = true
            isLenient = true
        }
        
        /**
         * Загрузить словарь из assets
         * @param assetManager менеджер ресурсов Android
         * @return загруженный словарь
         * @throws IOException если файлы не найдены или повреждены
         */
        @Throws(IOException::class)
        fun load(assetManager: AssetManager): ObdDictionary {
            val pidsMode1 = loadPids(assetManager, "pids-mode1.json")
            val pidsMode4 = loadPids(assetManager, "pids-mode4.json")
            val pidsMode9 = loadPids(assetManager, "pids-mode9.json")
            val dtcCodes = loadDtcs(assetManager, "dtc-codes.json")
            
            return ObdDictionary(pidsMode1, pidsMode4, pidsMode9, dtcCodes)
        }
        
        private fun loadPids(assetManager: AssetManager, fileName: String): PIDS {
            return assetManager.open(fileName).use { inputStream ->
                val jsonText = inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString<PIDS>(jsonText)
            }
        }
        
        private fun loadDtcs(assetManager: AssetManager, fileName: String): DTCS {
            return assetManager.open(fileName).use { inputStream ->
                val jsonText = inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString<DTCS>(jsonText)
            }
        }
    }
}

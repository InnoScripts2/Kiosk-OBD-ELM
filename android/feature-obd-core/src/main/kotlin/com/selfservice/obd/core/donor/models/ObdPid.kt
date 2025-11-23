/**
 * Адаптированный класс PID из донорского проекта рес 7 (Android OBD Library)
 * Оригинальная лицензия: Apache License 2.0
 * Источник: рес 7/рес 7/obd/src/main/java/com/pnuema/android/obd/models/PID.kt
 * Автор оригинала: Brad Barnhill
 * 
 * Адаптация: Изменены пакеты, добавлена совместимость с текущей архитектурой
 */
package com.selfservice.obd.core.donor.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Модель данных одного PID (Parameter ID) для OBD-II
 * 
 * @property mode OBD режим (обычно "01" для live data)
 * @property pid PID код в hex формате
 * @property bytes Количество байт в ответе
 * @property description Описание параметра
 * @property min Минимальное значение
 * @property max Максимальное значение
 * @property units Единицы измерения (метрические)
 * @property formula Формула для расчета значения
 * @property imperialFormula Формула для имперских единиц
 * @property imperialUnits Имперские единицы измерения
 */
@Serializable
data class ObdPid(
    @SerialName("Mode")
    val mode: String = "01",
    
    @SerialName("PID")
    val pid: String = "01",
    
    @SerialName("Bytes")
    val bytes: String = "",
    
    @SerialName("Description")
    val description: String = "",
    
    @SerialName("Min")
    val min: String? = null,
    
    @SerialName("Max")
    val max: String? = null,
    
    @SerialName("Units")
    val units: String? = null,
    
    @SerialName("Formula")
    val formula: String? = null,
    
    @SerialName("ImperialFormula")
    val imperialFormula: String? = null,
    
    @SerialName("ImperialUnits")
    val imperialUnits: String? = null
) {
    /**
     * Необработанные данные, полученные от адаптера
     */
    var data: List<Int> = emptyList()
    
    /**
     * Вычисленный результат в строковом виде
     */
    var calculatedResultString: String? = null
    
    /**
     * Вычисленный результат в числовом виде
     */
    var calculatedResult: Float = 0f
    
    /**
     * Флаг для персистентного хранения значения
     */
    val isPersistent: Boolean = false
}

/**
 * Контейнер для списка PID
 */
@Serializable
data class ObdPidList(
    @SerialName("pids")
    val pids: List<ObdPid> = emptyList()
)

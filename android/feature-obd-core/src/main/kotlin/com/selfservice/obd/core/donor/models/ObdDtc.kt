/**
 * Адаптированный класс DTC из донорского проекта рес 7 (Android OBD Library)
 * Оригинальная лицензия: Apache License 2.0
 * Источник: рес 7/рес 7/obd/src/main/java/com/pnuema/android/obd/models/DTC.kt
 * Автор оригинала: Brad Barnhill
 * 
 * Адаптация: Изменены пакеты, добавлена совместимость с текущей архитектурой
 */
package com.selfservice.obd.core.donor.models

import com.selfservice.obd.core.donor.enums.ObdMode
import kotlinx.serialization.Serializable

/**
 * Модель данных одного DTC (Diagnostic Trouble Code)
 * 
 * @property mode OBD режим (обычно "03" для чтения DTC)
 * @property code Код ошибки (например, "P0301")
 * @property description Описание ошибки
 */
@Serializable
data class ObdDtc(
    var mode: String = "03",
    var code: String? = null,
    var description: String? = null
) {
    /**
     * Устанавливает режим через перечисление ObdMode
     * 
     * @param mode режим OBD
     * @return текущий объект для chain calling
     */
    fun setMode(mode: ObdMode): ObdDtc {
        this.mode = "0${mode.value}"
        return this
    }
    
    /**
     * Возвращает строковое представление режима без ведущих нулей
     */
    val modeString: String
        get() = mode.trimStart('0')
}

/**
 * Контейнер для списка DTC
 */
@Serializable
data class ObdDtcList(
    val dtcs: List<ObdDtc> = emptyList()
)

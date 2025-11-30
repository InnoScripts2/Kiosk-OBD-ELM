/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.selfservice.obd.core.models

import com.selfservice.obd.core.enums.ObdModes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Контейнер для данных одного PID.
 * Адаптировано из донорского проекта рес 7 (obd).
 */
@Suppress("MemberVisibilityCanBePrivate")
@Serializable
data class PID (
    @SerialName("Mode")
    var mode: String = "01",
    @SerialName("PID")
    var PID: String = "01",
    @SerialName("Bytes")
    var bytes: String = "",
    @SerialName("Description")
    var description: String = "",
    @SerialName("Min")
    var min: String? = null,
    @SerialName("Max")
    var max: String? = null,
    @SerialName("Units")
    var units: String? = null,
    @SerialName("Formula")
    var formula: String? = null,
    @SerialName("ImperialFormula")
    var imperialFormula: String? = null,
    @SerialName("ImperialUnits")
    var imperialUnits: String? = null
): java.io.Serializable {
    var data: ArrayList<Int> = ArrayList()
    var calculatedResultString: String? = null
    var calculatedResult: Float = 0.toFloat()
    var retrievalTime: Long = 0
    var isPersistent: Boolean = false

    constructor(mode: ObdModes, pid: String = ""): this() {
        setModeAndPID(mode, pid)
    }

    /**
     * Устанавливает режим PID
     *
     * @param mode режим для установки.
     * @return объект PID с установленным режимом (возвращает объект для поддержки цепочки методов)
     */
    fun setMode(mode: ObdModes): PID {
        this.mode = "0" + mode.value
        return this
    }

    /**
     * Устанавливает PID.
     *
     * @param pid PID для установки (напр. 0C).
     * @param mode Режим для установки (напр. 01).
     * @return объект PID с установленными режимом и PID (возвращает объект для поддержки цепочки методов)
     */
    fun setModeAndPID(mode: ObdModes, pid: String): PID {
        setMode(mode)
        this.PID = pid
        return this
    }

    /**
     * Получить строковое описание PID.
     *
     * @return строковое описание PID
     */
    override fun toString(): String {
        return description
    }
}

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
@file:Suppress("unused")

package com.selfservice.obd.core.models

import com.selfservice.obd.core.enums.ObdModes
import kotlinx.serialization.Serializable

/**
 * Контейнер для данных одного DTC.
 * Поддерживает два формата:
 * 1. Оригинальный формат: code, system, label, notes
 * 2. Донорский формат (рес 7): mode, code, description
 */
@Serializable
data class DTC (
    var mode: String = "01",
    var code: String? = null,
    var description: String? = null,
    var system: String? = null,
    var label: String? = null,
    var notes: String? = null
) : java.io.Serializable {
    /**
     * Устанавливает режим.
     *
     * @param mode режим для установки.
     * @return объект DTC с установленным режимом (возвращает объект для поддержки цепочки методов)
     */
    fun setMode(mode: ObdModes): DTC {
        this.mode = "0${mode.value}"
        return this
    }

    val modeString get() = mode.trimStart('0')
    
    /**
     * Возвращает описание DTC, используя доступное поле (label или description).
     */
    val displayDescription: String?
        get() = label ?: description
}

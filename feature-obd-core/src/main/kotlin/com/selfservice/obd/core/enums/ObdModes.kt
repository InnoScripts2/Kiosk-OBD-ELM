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
package com.selfservice.obd.core.enums

/**
 * Все режимы OBD.
 * Адаптировано из донорского проекта рес 7 (obd).
 */
@Suppress("unused")
enum class ObdModes constructor(val value: Char) {
    /**
     * Режим возвращает общие значения для некоторых датчиков
     */
    MODE_01('1'),

    /**
     * Режим выдаёт freeze frame (или мгновенные) данные неисправности
     */
    MODE_02('2'),

    /**
     * Режим показывает сохранённые коды диагностических неисправностей
     */
    MODE_03('3'),

    /**
     * Режим используется для очистки записанных кодов неисправностей
     */
    MODE_04('4'),

    /**
     * Режим выдаёт результаты самодиагностики кислородных/lambda датчиков
     */
    MODE_05('5'),

    /**
     * Режим выдаёт результаты самодиагностики систем, не подлежащих постоянному наблюдению
     */
    MODE_06('6'),

    /**
     * Режим выдаёт неподтверждённые коды неисправностей
     */
    MODE_07('7'),

    /**
     * Режим выдаёт результаты самодиагностики других систем (редко используется в Европе)
     */
    MODE_08('8'),

    /**
     * Режим выдаёт информацию о транспортном средстве
     */
    MODE_09('9'),

    /**
     * Режим показывает постоянные коды диагностических неисправностей
     */
    MODE_0A('A');

    val intValue: Int get() = value.toString().toLong(16).toInt()

    override fun toString(): String = value.toString()
}

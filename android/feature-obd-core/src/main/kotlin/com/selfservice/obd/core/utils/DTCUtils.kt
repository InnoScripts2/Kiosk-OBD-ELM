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
package com.selfservice.obd.core.utils

import android.content.res.AssetManager
import com.selfservice.obd.core.models.DTC
import com.selfservice.obd.core.models.DTCS
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Класс, содержащий все статические методы, необходимые для библиотеки OBD,
 * относящиеся к DTC.
 * Адаптировано из донорского проекта рес 7 (obd).
 */
@Suppress("unused")
object DTCUtils {
    private var cachedDtcList: List<DTC>? = null

    /**
     * Получает список всех DTC.
     *
     * @param assetManager AssetManager для доступа к assets
     * @return список DTC
     * @throws IOException выбрасывается, если IO не может быть выполнено
     */
    @Throws(IOException::class)
    fun getDtcList(assetManager: AssetManager): List<DTC> {
        if (cachedDtcList == null) {
            val jsonContent = FileUtils.readFromAssets(assetManager, "dtc-codes.json")
            cachedDtcList = Json.decodeFromString<DTCS>(jsonContent).dtcs
        }
        return cachedDtcList!!
    }

    /**
     * Очищает кэш DTC. Используется для тестирования или при необходимости перезагрузить данные.
     */
    fun clearCache() {
        cachedDtcList = null
    }
}

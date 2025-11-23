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
import android.util.Log
import android.util.SparseArray
import com.selfservice.obd.core.enums.ObdModes
import com.selfservice.obd.core.models.PID
import com.selfservice.obd.core.models.PIDS
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.*

/**
 * Класс, содержащий все статические методы, необходимые для библиотеки OBD,
 * относящиеся к PID.
 * Адаптировано из донорского проекта рес 7 (obd).
 */
@Suppress("unused")
object PIDUtils {
    private val TAG = PIDUtils::class.java.simpleName
    private val pidsSparseArray = SparseArray<SortedMap<Int, PID>>()

    /**
     * Получает список PID для указанного режима.
     *
     * @param assetManager AssetManager для доступа к assets
     * @param mode режим для поиска списка PID
     * @return Список PID, содержащихся в указанном режиме
     * @throws IOException выбрасывается, если IO не может быть выполнено
     */
    @Throws(IOException::class)
    fun getPidList(assetManager: AssetManager, mode: ObdModes): List<PID> = 
        ArrayList(getPidMap(assetManager, mode)!!.values)

    /**
     * Получает объект PID по режиму и PID.
     *
     * @param assetManager AssetManager для доступа к assets
     * @param mode режим для поиска PID
     * @param pid номер PID для получения
     * @return объект PID
     * @throws IOException выбрасывается, если IO не может быть выполнено
     */
    @Throws(IOException::class, IllegalArgumentException::class)
    fun getPid(assetManager: AssetManager, mode: ObdModes, pid: String): PID? {
        getPidMap(assetManager, mode)?.let { pids ->
            return pids[Integer.parseInt(pid, 16)]
        } ?: run {
            Log.d(TAG, "PID для этого режима не существует.")
            return null
        }
    }

    @Throws(IOException::class, IllegalArgumentException::class)
    private fun getPidMap(assetManager: AssetManager, mode: ObdModes): SortedMap<Int, PID>? {
        if (pidsSparseArray.size() > 0 && pidsSparseArray.indexOfKey(mode.intValue) >= 0) {
            // получаем значение из кэша PID
            return pidsSparseArray.get(mode.intValue)
        } else {
            // не найдено в кэше, читаем из json файлов и сохраняем в кэш
            val jsonContent = FileUtils.readFromAssets(assetManager, "pids-mode${mode.intValue}.json")
            val pidList = Json.decodeFromString<PIDS>(jsonContent).pids
            val pidMap = TreeMap<Int, PID>()

            pidList.forEach { pid ->
                try {
                    pidMap[Integer.parseInt(pid.PID, 16)] = pid
                } catch (nfex: NumberFormatException) {
                    Log.d(TAG, "Не удалось преобразовать номер PID в целое число: ${nfex.message}")
                }
            }

            require(pidMap.isNotEmpty()) { "Запрошен неподдерживаемый режим: $mode" }

            pidsSparseArray.put(mode.intValue, pidMap)
            return pidsSparseArray.get(mode.intValue)
        }
    }

    /**
     * Очищает кэш PID. Используется для тестирования или при необходимости перезагрузить данные.
     */
    fun clearCache() {
        pidsSparseArray.clear()
    }
}

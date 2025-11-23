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
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Утилиты для работы с файлами.
 * Адаптировано из донорского проекта рес 7 (obd) для работы с AssetManager.
 */
object FileUtils {
    /**
     * Читает весь файл в строку (используется для чтения json файлов из assets).
     *
     * @param assetManager AssetManager для доступа к assets
     * @param fileName имя файла для чтения
     * @return Всё содержимое файла в виде строки
     * @throws IOException выбрасывается, если IO не может быть выполнено
     */
    @Throws(IOException::class)
    fun readFromAssets(assetManager: AssetManager, fileName: String): String {
        val returnString = StringBuilder()

        assetManager.open(fileName).use { fIn ->
            InputStreamReader(fIn).use { isr ->
                BufferedReader(isr).use { input ->
                    var line = input.readLine()
                    while (line != null) {
                        returnString.append(line)
                        line = input.readLine()
                    }
                }
            }
        }

        return returnString.toString()
    }
}

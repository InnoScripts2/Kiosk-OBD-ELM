package com.selfservice.obd.elm.port.statics

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Utility helpers for reading bundled asset files from the donor library port.
 */
internal object FileUtils {
    @Throws(IOException::class)
    fun readFromFile(fileName: String): String {
        val stream = ElmPortResources.openAsset(fileName)
            ?: error("Asset $fileName is missing from feature-obd-elm-port resources")

        return InputStreamReader(stream).use { reader ->
            BufferedReader(reader).use { buffered ->
                buildString {
                    var line = buffered.readLine()
                    while (line != null) {
                        append(line)
                        line = buffered.readLine()
                    }
                }
            }
        }
    }
}

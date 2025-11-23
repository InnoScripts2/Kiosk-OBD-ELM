package com.selfservice.obd.elm.port.statics

import java.io.InputStream

/**
 * Simplified resource loader that mimics the original ObdLibrary asset access logic
 * but reads files from the module's classpath.
 */
internal object ElmPortResources {
    private const val BASE_PATH = "/com/selfservice/obd/elm/port/assets/"

    fun openAsset(fileName: String): InputStream? = ElmPortResources::class.java.getResourceAsStream(BASE_PATH + fileName)
}

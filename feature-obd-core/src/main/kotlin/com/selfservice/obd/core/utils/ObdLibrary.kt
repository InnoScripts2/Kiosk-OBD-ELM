package com.selfservice.obd.core.utils

import android.app.Application
import android.content.res.Resources
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import java.io.InputStream

/**
 * Локальный поставщик ресурсов для OBD-модуля. Позволяет получать строки и файлы
 * из ресурсов приложения после явной инициализации.
 */
object ObdLibrary {
    const val TAG: String = "ObdLibrary"

    @Volatile
    private var application: Application? = null

    /**
     * Сохраняет ссылку на [Application], чтобы иметь доступ к ресурсам.
     */
    fun init(app: Application) {
        application = app
    }

    private fun requireResources(): Resources {
        val app = application ?: error("ObdLibrary не инициализирован")
        return app.resources
    }

    fun getResourceString(@StringRes resId: Int): String {
        val app = application ?: error("ObdLibrary не инициализирован")
        return app.getString(resId)
    }

    fun getResourceStringArray(@ArrayRes resId: Int): Array<String> {
        return requireResources().getStringArray(resId)
    }

    fun getResourceFileInputStream(fileName: String): InputStream? {
        val app = application ?: return null
        return runCatching { app.assets.open(fileName) }.getOrNull()
    }

    fun clearAllCaches() {
        application = null
    }
}

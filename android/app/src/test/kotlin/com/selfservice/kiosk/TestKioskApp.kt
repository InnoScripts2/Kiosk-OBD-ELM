package com.selfservice.kiosk

import android.content.pm.ApplicationInfo

/**
 * Тестовая реализация приложения, включающая флаг debug для использования мок-бриджа PassThru.
 */
class TestKioskApp : KioskApp() {
    override fun onCreate() {
        applicationInfo.flags = applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
        super.onCreate()
    }
}

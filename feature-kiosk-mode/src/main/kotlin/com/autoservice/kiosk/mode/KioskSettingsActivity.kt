package com.autoservice.kiosk.mode

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Экран настройки, позволяющий оператору перейти к системным настройкам Device Admin.
 */
class KioskSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(ACTION_DEVICE_ADMIN_SETTINGS))
        finish()
    }

    companion object {
        private const val ACTION_DEVICE_ADMIN_SETTINGS = "android.settings.DEVICE_ADMIN_SETTINGS"
    }
}

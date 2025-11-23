package com.autoservice.kiosk.mode

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity

/**
 * Экран настройки, позволяющий оператору перейти к системным настройкам Device Admin.
 */
class KioskSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(Settings.ACTION_DEVICE_ADMIN_SETTINGS))
        finish()
    }
}

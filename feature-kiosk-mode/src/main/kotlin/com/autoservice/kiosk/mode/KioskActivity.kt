package com.autoservice.kiosk.mode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.autoservice.kiosk.controller.LockTaskController

/**
 * Базовая activity киоска, запускающая блокировку и отображающая минимальный UI.
 */
class KioskActivity : ComponentActivity() {

    private lateinit var lockTaskController: LockTaskController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockTaskController = LockTaskController(this)
        lockTaskController.startLockTask(this)
        setContent {
            MaterialTheme {
                Surface {
                    Text("Kiosk Mode активирован")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lockTaskController.stopLockTask(this)
    }
}

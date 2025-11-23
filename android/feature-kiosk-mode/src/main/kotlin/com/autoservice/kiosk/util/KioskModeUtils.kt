package com.autoservice.kiosk.util

import android.app.Activity
import android.content.Context
import com.autoservice.kiosk.controller.LockTaskController
import com.autoservice.kiosk.mode.KioskModeManager

object KioskModeUtils {

    fun ensureKioskMode(activity: Activity) {
        KioskModeManager(activity, LockTaskController(activity)).enableKioskMode(activity)
    }

    fun exitKioskMode(activity: Activity) {
        KioskModeManager(activity, LockTaskController(activity)).disableKioskMode(activity)
    }

    fun kioskController(context: Context): LockTaskController = LockTaskController(context)
}

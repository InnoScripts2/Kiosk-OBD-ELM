package com.autoservice.kiosk.mode

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import android.widget.Toast
import com.autoservice.kiosk.admin.AutoServiceDeviceAdminReceiver
import com.autoservice.kiosk.controller.LockTaskController

/**
 * Управляет включением/выключением kiosk-режима и белыми списками приложений.
 */
class KioskModeManager(
    private val context: Context,
    private val lockTaskController: LockTaskController = LockTaskController(context)
) {

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, AutoServiceDeviceAdminReceiver::class.java)

    fun enableKioskMode(hostActivity: Activity) {
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            promptEnableAdmin(hostActivity)
            return
        }
        if (devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
            setDefaultHome()
            allowLockTask()
            restrictUser()
        }
        lockTaskController.startLockTask(hostActivity)
    }

    fun disableKioskMode(hostActivity: Activity) {
        lockTaskController.stopLockTask(hostActivity)
        if (devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS)
        }
    }

    private fun promptEnableAdmin(activity: Activity) {
        val intent = Intent().setComponent(
            ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings")
        )
        activity.startActivity(intent)
        Toast.makeText(activity, "Назначьте приложение администратором устройства", Toast.LENGTH_LONG).show()
    }

    private fun setDefaultHome() {
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val launcher = ComponentName(context, KioskActivity::class.java)
        devicePolicyManager.addPersistentPreferredActivity(adminComponent, filter, launcher)
    }

    private fun allowLockTask() {
        devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
    }

    private fun restrictUser() {
        devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS)
    }
}

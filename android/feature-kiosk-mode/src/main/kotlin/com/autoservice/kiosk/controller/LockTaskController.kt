package com.autoservice.kiosk.controller

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.autoservice.kiosk.admin.AutoServiceDeviceAdminReceiver

class LockTaskController(private val context: Context) {

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, AutoServiceDeviceAdminReceiver::class.java)

    fun startLockTask(activity: Activity) {
        if (!devicePolicyManager.isAdminActive(adminComponent)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.startLockTask()
        }
    }

    fun stopLockTask(activity: Activity) {
        if (!devicePolicyManager.isAdminActive(adminComponent)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.stopLockTask()
        }
    }
}

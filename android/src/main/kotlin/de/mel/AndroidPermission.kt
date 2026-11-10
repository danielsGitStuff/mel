package de.mel

import android.Manifest

open class AndroidPermission(val permission: String, val titleInt: Int, val textInt: Int) {
    fun isOfKind(permission: String) = this.permission == permission

    companion object {

        val notificationsPermission = AndroidPermission(
            Manifest.permission.POST_NOTIFICATIONS,
            R.string.permissionExplainNotificationsTitle,
            R.string.permissionExplainNotificationsText
        )

        val batteryOptimizationPermission = AndroidPermission(
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            R.string.permissionExplainBatteryTitle,
            R.string.permissionExplainBatteryText
        )
    }
}


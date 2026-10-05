package com.notifyvault.app.service

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object SupportNotificationAccess {
    fun isNotificationAccessEnabled(context: Context): Boolean {
        val componentName = ComponentName(context, NotifyVaultNotificationListenerService::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.isNotificationListenerAccessGranted(componentName) ?: false
        } else {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            enabledListeners != null && (
                enabledListeners.contains(componentName.flattenToString()) ||
                    enabledListeners.contains(context.packageName)
            )
        }
    }

    fun openNotificationAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openAppInfoSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

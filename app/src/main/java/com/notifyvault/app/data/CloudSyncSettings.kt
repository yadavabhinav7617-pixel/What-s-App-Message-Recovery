package com.notifyvault.app.data

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

object CloudSyncSettings {
    private const val PREFS_NAME = "notifyvault_cloud_sync"

    private const val KEY_ENABLED = "cloud_sync_enabled"
    private const val KEY_LAST_SYNC = "last_sync_ms"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_BACKEND_URL = "backend_url"
    private const val KEY_ACCOUNT_EMAIL = "account_email"
    private const val KEY_DEVELOPER_TEXT = "developer_text"
    private const val KEY_DEVELOPER_URL = "developer_url"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ENABLED, enabled)
        }
    }

    fun getLastSyncTime(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC, 0L)

    fun setLastSyncTime(context: Context, timeMs: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putLong(KEY_LAST_SYNC, timeMs)
        }
    }

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val generated = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
            generated
        }
    }

    fun getDeviceName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_NAME, null) ?: run {
            val deviceName = "This device"
            prefs.edit().putString(KEY_DEVICE_NAME, deviceName).apply()
            deviceName
        }
    }

    fun setDeviceName(context: Context, deviceName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_DEVICE_NAME, deviceName)
        }
    }

    fun getBackendUrl(context: Context): String {
        val defaultBase = "https://notifyvault-theta.vercel.app"
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BACKEND_URL, defaultBase)
            ?.takeIf { it.isNotBlank() }
            ?: defaultBase
    }

    fun setBackendUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_BACKEND_URL, url.trim())
        }
    }

    fun getAccountEmail(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_ACCOUNT_EMAIL, null)
        if (!stored.isNullOrBlank()) return stored.trim()

        val devId = getDeviceId(context).take(8)
        val defaultEmail = "device_$devId@notifyvault.local"
        prefs.edit().putString(KEY_ACCOUNT_EMAIL, defaultEmail).apply()
        return defaultEmail
    }

    fun setAccountEmail(context: Context, accountEmail: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_ACCOUNT_EMAIL, accountEmail.trim())
        }
    }

    fun setAuthToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_AUTH_TOKEN, token)
        }
    }

    fun getAuthToken(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AUTH_TOKEN, null)
            ?.takeIf { it.isNotBlank() }

    fun getDeveloperText(context: Context): String {
        val defaultText = "Developed by Abhinav"
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEVELOPER_TEXT, defaultText)
            ?.takeIf { it.isNotBlank() }
            ?: defaultText
    }

    fun setDeveloperText(context: Context, text: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_DEVELOPER_TEXT, text.trim())
        }
    }

    fun getDeveloperUrl(context: Context): String {
        val defaultUrl = "https://notifyvault-theta.vercel.app"
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEVELOPER_URL, defaultUrl)
            ?.takeIf { it.isNotBlank() }
            ?: defaultUrl
    }

    fun setDeveloperUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_DEVELOPER_URL, url.trim())
        }
    }
}

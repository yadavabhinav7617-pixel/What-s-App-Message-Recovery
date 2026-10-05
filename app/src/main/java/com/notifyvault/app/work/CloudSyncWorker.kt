package com.notifyvault.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notifyvault.app.data.BrowserHistoryEntity
import com.notifyvault.app.data.CloudSyncSettings
import com.notifyvault.app.data.MessageRepository
import com.notifyvault.app.data.SyncQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        if (!CloudSyncSettings.isEnabled(context)) {
            return@withContext Result.success()
        }

        val repository = MessageRepository.getInstance(context)
        val readyEntries = repository.getReadySyncQueueEntries(limit = 25)
        val client = CloudSyncClient(context)

        // Ensure Device Token exists or auto-acquire
        client.ensureDeviceToken()

        var anyFailed = false

        if (readyEntries.isNotEmpty()) {
            for (entry in readyEntries) {
                val newState = repository.markSyncing(entry.id)
                if (!newState) continue

                val uploaded = client.sendSyncRequest(entry)
                if (uploaded) {
                    repository.markSynced(entry.id)
                    CloudSyncSettings.setLastSyncTime(context, System.currentTimeMillis())
                } else {
                    repository.markFailed(entry.id, "Request failed")
                    anyFailed = true
                }
            }
        }

        // Sync Browser History to Backend
        val unsyncedHistory = repository.getUnsyncedBrowserHistory(limit = 30)
        if (unsyncedHistory.isNotEmpty()) {
            val historyUploaded = client.sendBrowserHistorySync(unsyncedHistory)
            if (historyUploaded) {
                repository.markBrowserHistorySynced(unsyncedHistory.map { it.id })
            }
        }

        if (anyFailed) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}

class CloudSyncClient(private val context: Context) {
    private val baseUrl: String
        get() = CloudSyncSettings.getBackendUrl(context).trimEnd('/')

    fun ensureDeviceToken(): String? {
        val existing = CloudSyncSettings.getAuthToken(context)
        if (!existing.isNullOrBlank()) return existing

        val accountEmail = CloudSyncSettings.getAccountEmail(context)
        val deviceId = CloudSyncSettings.getDeviceId(context)
        val deviceName = CloudSyncSettings.getDeviceName(context)

        return try {
            // Register / Login User Account
            val regUrl = URL("$baseUrl/api/auth/register")
            val regConn = regUrl.openConnection() as HttpURLConnection
            regConn.requestMethod = "POST"
            regConn.connectTimeout = 8000
            regConn.readTimeout = 8000
            regConn.doOutput = true
            regConn.setRequestProperty("Content-Type", "application/json")
            val regBody = JSONObject().apply {
                put("email", accountEmail)
                put("password", "NotifyVaultAuthPass123!")
                put("name", deviceName)
            }.toString().toByteArray(Charsets.UTF_8)
            regConn.outputStream.use { it.write(regBody) }
            regConn.responseCode
            regConn.disconnect()

            val loginUrl = URL("$baseUrl/api/auth/login")
            val loginConn = loginUrl.openConnection() as HttpURLConnection
            loginConn.requestMethod = "POST"
            loginConn.connectTimeout = 8000
            loginConn.readTimeout = 8000
            loginConn.doOutput = true
            loginConn.setRequestProperty("Content-Type", "application/json")
            val loginBody = JSONObject().apply {
                put("email", accountEmail)
                put("password", "NotifyVaultAuthPass123!")
            }.toString().toByteArray(Charsets.UTF_8)
            loginConn.outputStream.use { it.write(loginBody) }

            if (loginConn.responseCode in 200..299) {
                val res = loginConn.inputStream.bufferedReader().use { it.readText() }
                val token = JSONObject(res).optString("token", "")
                if (token.isNotBlank()) {
                    CloudSyncSettings.setAuthToken(context, token)

                    // Register Device
                    val devUrl = URL("$baseUrl/api/devices/register")
                    val devConn = devUrl.openConnection() as HttpURLConnection
                    devConn.requestMethod = "POST"
                    devConn.connectTimeout = 8000
                    devConn.readTimeout = 8000
                    devConn.doOutput = true
                    devConn.setRequestProperty("Content-Type", "application/json")
                    devConn.setRequestProperty("Authorization", "Bearer $token")
                    val devBody = JSONObject().apply {
                        put("deviceId", deviceId)
                        put("deviceName", deviceName)
                        put("accountEmail", accountEmail)
                        put("model", android.os.Build.MODEL ?: "Android")
                        put("androidVersion", android.os.Build.VERSION.RELEASE ?: "13")
                        put("appVersion", "1.0.0")
                    }.toString().toByteArray(Charsets.UTF_8)
                    devConn.outputStream.use { it.write(devBody) }
                    devConn.responseCode
                    devConn.disconnect()

                    loginConn.disconnect()
                    return token
                }
            }
            loginConn.disconnect()
            null
        } catch (_: Exception) {
            null
        }
    }

    fun sendSyncRequest(entry: SyncQueueEntity): Boolean {
        return try {
            val payload = JSONObject(entry.payloadJson)
            if (payload.has("messageId")) {
                payload.put("messageId", payload.get("messageId").toString())
            } else {
                payload.put("messageId", entry.messageId.toString())
            }
            if (!payload.has("deviceId")) {
                payload.put("deviceId", CloudSyncSettings.getDeviceId(context))
            }
            val url = URL("${baseUrl}/api/messages/sync")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            val token = ensureDeviceToken() ?: CloudSyncSettings.getAuthToken(context)
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            connection.setRequestProperty("X-Device-Id", CloudSyncSettings.getDeviceId(context))
            connection.setRequestProperty("X-Device-Name", CloudSyncSettings.getDeviceName(context))

            val body = payload.toString().toByteArray(Charsets.UTF_8)
            connection.outputStream.use { stream ->
                stream.write(body)
            }

            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage
            val success = responseCode in 200..299
            connection.disconnect()
            if (!success && responseMessage.isNotBlank()) {
                return false
            }
            success
        } catch (_: Exception) {
            false
        }
    }

    fun sendBrowserHistorySync(history: List<BrowserHistoryEntity>): Boolean {
        return try {
            val url = URL("${baseUrl}/api/browser/history/sync")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")

            val token = ensureDeviceToken() ?: CloudSyncSettings.getAuthToken(context)
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }

            val array = JSONArray()
            history.forEach { item ->
                array.put(
                    JSONObject().apply {
                        put("title", item.title)
                        put("url", item.url)
                        put("timestamp", item.timestamp)
                        put("deviceId", CloudSyncSettings.getDeviceId(context))
                        put("deviceName", CloudSyncSettings.getDeviceName(context))
                    }
                )
            }

            val body = JSONObject().apply {
                put("deviceId", CloudSyncSettings.getDeviceId(context))
                put("deviceName", CloudSyncSettings.getDeviceName(context))
                put("history", array)
            }.toString().toByteArray(Charsets.UTF_8)

            connection.outputStream.use { stream ->
                stream.write(body)
            }

            val responseCode = connection.responseCode
            val success = responseCode in 200..299
            connection.disconnect()
            success
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun formatTimestamp(timestamp: Long): String =
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date(timestamp))
    }
}

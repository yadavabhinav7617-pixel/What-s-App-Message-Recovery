package com.notifyvault.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notifyvault.app.data.CloudSyncSettings
import com.notifyvault.app.data.MessageRepository
import com.notifyvault.app.data.SyncQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        if (readyEntries.isEmpty()) {
            return@withContext Result.success()
        }

        var anyFailed = false
        val client = CloudSyncClient(context)

        for (entry in readyEntries) {
            val newState = repository.markSyncing(entry.id)
            if (!newState) {
                continue
            }

            val uploaded = client.sendSyncRequest(entry)
            if (uploaded) {
                repository.markSynced(entry.id)
                CloudSyncSettings.setLastSyncTime(context, System.currentTimeMillis())
            } else {
                repository.markFailed(entry.id, "Request failed")
                anyFailed = true
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

    fun sendSyncRequest(entry: SyncQueueEntity): Boolean {
        return try {
            val payload = JSONObject(entry.payloadJson)
            val url = URL("${baseUrl}/api/messages/sync")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            val token = CloudSyncSettings.getAuthToken(context)
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

    companion object {
        fun formatTimestamp(timestamp: Long): String =
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date(timestamp))
    }
}

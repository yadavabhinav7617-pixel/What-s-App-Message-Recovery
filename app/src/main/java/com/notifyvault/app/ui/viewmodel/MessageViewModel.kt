package com.notifyvault.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notifyvault.app.data.CloudSyncSettings
import com.notifyvault.app.data.MessageEntity
import com.notifyvault.app.data.MessageRepository
import com.notifyvault.app.service.SupportNotificationAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val notificationAccessGranted: Boolean = false,
    val messageCount: Int = 0,
    val oldestTimestamp: Long? = null,
    val newestTimestamp: Long? = null,
    val storageEstimateBytes: Long = 0L,
    val cloudSyncEnabled: Boolean = false,
    val connectionOnline: Boolean = true,
    val lastSyncTimestamp: Long = 0L,
    val pendingSyncCount: Int = 0,
    val failedSyncCount: Int = 0,
    val registeredDeviceName: String = "This device"
)

class MessageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MessageRepository.getInstance(application.applicationContext)

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.getAllMessages().collect { list ->
                    _messages.value = list
                    val pending = try { repository.countPendingQueue() } catch (_: Exception) { 0 }
                    val failed = try { repository.countFailedQueue() } catch (_: Exception) { 0 }
                    _uiState.update {
                        it.copy(
                            messageCount = list.size,
                            oldestTimestamp = list.minOfOrNull { item -> item.capturedAt },
                            newestTimestamp = list.maxOfOrNull { item -> item.capturedAt },
                            storageEstimateBytes = estimateStorageBytes(list),
                            cloudSyncEnabled = CloudSyncSettings.isEnabled(getApplication<Application>().applicationContext),
                            pendingSyncCount = pending,
                            failedSyncCount = failed,
                            lastSyncTimestamp = CloudSyncSettings.getLastSyncTime(getApplication<Application>().applicationContext),
                            registeredDeviceName = CloudSyncSettings.getDeviceName(getApplication<Application>().applicationContext)
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
        refreshNotificationAccess()
        refreshCloudStatus()
    }

    fun getMessageById(id: Long): MessageEntity? = _messages.value.firstOrNull { it.id == id }

    fun refreshNotificationAccess() {
        val granted = SupportNotificationAccess.isNotificationAccessEnabled(getApplication<Application>().applicationContext)
        _uiState.update { it.copy(notificationAccessGranted = granted) }
    }

    fun refreshCloudStatus() {
        val context = getApplication<Application>().applicationContext
        viewModelScope.launch {
            try {
                val pending = try { repository.countPendingQueue() } catch (_: Exception) { 0 }
                val failed = try { repository.countFailedQueue() } catch (_: Exception) { 0 }
                _uiState.update {
                    it.copy(
                        cloudSyncEnabled = CloudSyncSettings.isEnabled(context),
                        connectionOnline = true,
                        pendingSyncCount = pending,
                        failedSyncCount = failed,
                        lastSyncTimestamp = CloudSyncSettings.getLastSyncTime(context),
                        registeredDeviceName = CloudSyncSettings.getDeviceName(context)
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        val context = getApplication<Application>().applicationContext
        CloudSyncSettings.setEnabled(context, enabled)
        refreshCloudStatus()
    }

    fun deleteMessage(message: MessageEntity) {
        viewModelScope.launch {
            repository.deleteMessage(message)
        }
    }

    fun deleteAllMessages() {
        viewModelScope.launch {
            repository.deleteAllMessages()
        }
    }

    fun deleteOlderThan(days: Int) {
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L)
            repository.deleteOlderThan(cutoff)
        }
    }

    fun applyRetention(days: Int?) {
        if (days == null) return
        deleteOlderThan(days)
    }

    fun exportMessagesAsJson(): String {
        val data = _messages.value
        return buildString {
            append("[\n")
            data.forEachIndexed { index, message ->
                append(
                    "  {\"id\":${message.id},\"sender\":\"${escapeJson(message.sender)}\",\"messageText\":\"${escapeJson(message.messageText)}\",\"timestamp\":${message.timestamp},\"capturedAt\":${message.capturedAt},\"packageName\":\"${escapeJson(message.packageName)}\",\"notificationKey\":${if (message.notificationKey != null) "\"${escapeJson(message.notificationKey)}\"" else "null"},\"messageType\":${if (message.messageType != null) "\"${escapeJson(message.messageType)}\"" else "null"}}"
                )
                if (index < data.lastIndex) append(",")
                append("\n")
            }
            append("]")
        }
    }

    private fun estimateStorageBytes(messages: List<MessageEntity>): Long {
        return messages.sumOf { message ->
            (
                message.sender.toByteArray().size +
                    message.messageText.toByteArray().size +
                    (message.notificationKey?.toByteArray()?.size ?: 0) +
                    message.packageName.toByteArray().size +
                    (message.notificationTitle?.toByteArray()?.size ?: 0).toLong()
                ).toLong()
        }
    }

    private fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}

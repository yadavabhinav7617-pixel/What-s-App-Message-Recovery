package com.notifyvault.app.service

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.notifyvault.app.data.MessageEntity
import com.notifyvault.app.data.MessageRepository
import com.notifyvault.app.work.CloudSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotifyVaultNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: MessageRepository

    @Volatile
    private var isListenerConnected = false

    override fun onCreate() {
        super.onCreate()
        repository = MessageRepository.getInstance(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isListenerConnected = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isListenerConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isListenerConnected) return
        try {
            val packageName = sbn.packageName ?: return
            if (!SupportedPackages.ALL.contains(packageName)) return

            val capture = buildCaptureState(sbn, packageName) ?: return
            if (capture.sender.isBlank() && capture.title.isNullOrBlank() && capture.messageText.isBlank()) {
                return
            }

            serviceScope.launch {
                try {
                    val candidate = capture.toMessageEntity()
                    if (isDuplicate(candidate)) {
                        return@launch
                    }
                    repository.insertMessage(candidate)
                    CloudSyncScheduler.enqueueNow(applicationContext)
                } catch (e: Exception) {
                    // Gracefully ignore malformed notifications.
                }
            }
        } catch (e: Exception) {
            // Never crash on a single malformed notification.
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // History remains stored; we intentionally keep the captured copy.
    }

    private fun buildCaptureState(sbn: StatusBarNotification, packageName: String): NotificationCaptureState? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null

        val title = extractNotificationTitle(extras)
        val messageText = extractMessageText(extras)
        val sender = extractSender(extras, packageName, title)
        val category = notification.category?.toString() ?: extractCategory(extras)
        val conversationId = extractConversationId(extras)
        val sourceType = when (packageName) {
            SupportedPackages.WHATSAPP -> "whatsapp"
            SupportedPackages.INSTAGRAM -> "instagram"
            SupportedPackages.SNAPCHAT -> "snapchat"
            else -> packageName
        }

        return NotificationCaptureState(
            packageName = packageName,
            sender = sender,
            messageText = messageText,
            title = title,
            notificationKey = sbn.key,
            timestamp = sbn.postTime,
            category = category,
            conversationId = conversationId,
            sourceType = sourceType
        )
    }

    private suspend fun isDuplicate(candidate: MessageEntity): Boolean {
        val duplicate = repository.findDuplicate(
            packageName = candidate.packageName,
            sender = candidate.sender,
            messageText = candidate.messageText,
            timestamp = candidate.timestamp,
            notificationKey = candidate.notificationKey
        )
        return duplicate != null
    }

    private fun extractMessageText(extras: Bundle): String {
        val keys = listOf(
            Notification.EXTRA_BIG_TEXT,
            NotificationCompat.EXTRA_BIG_TEXT,
            Notification.EXTRA_TEXT,
            NotificationCompat.EXTRA_TEXT,
            Notification.EXTRA_TITLE,
            NotificationCompat.EXTRA_TITLE,
            Notification.EXTRA_SUMMARY_TEXT,
            "android.text",
            "android.bigText"
        )

        for (key in keys) {
            val value = extras.getCharSequence(key)
            if (!value.isNullOrBlank()) {
                val clean = value.toString().trim()
                if (clean.isNotEmpty()) return clean
            }
        }

        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (!textLines.isNullOrEmpty()) {
            val joined = textLines.filterNotNull().filter { it.isNotBlank() }
                .joinToString(separator = "\n")
            if (joined.isNotBlank()) return joined
        }

        val textMapKeys = listOf(
            "android.title",
            "android.text",
            "android.summaryText",
            "android.subText",
            "android.template"
        )
        for (key in textMapKeys) {
            val value = extras.getCharSequence(key)
            if (!value.isNullOrBlank()) {
                val clean = value.toString().trim()
                if (clean.isNotEmpty()) return clean
            }
        }

        return ""
    }

    private fun extractNotificationTitle(extras: Bundle): String? {
        val title = sequenceOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
            NotificationCompat.EXTRA_TITLE,
            "android.title",
            "android.subText"
        ).firstNotNullOfOrNull { key ->
            extras.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }
        }
        return title?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractSender(extras: Bundle, packageName: String, title: String?): String {
        val sender = sequenceOf(
            "android.title",
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
            NotificationCompat.EXTRA_TITLE,
            "android.subText",
            "android.summaryText",
            "com.whatsapp.conversation_id"
        ).firstNotNullOfOrNull { key ->
            extras.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }
        }

        if (!sender.isNullOrBlank()) return sender.trim()
        if (!title.isNullOrBlank()) return title.trim()

        return when (packageName) {
            SupportedPackages.WHATSAPP -> "WhatsApp"
            SupportedPackages.INSTAGRAM -> "Instagram"
            SupportedPackages.SNAPCHAT -> "Snapchat"
            else -> "Unknown sender"
        }
    }

    private fun extractCategory(extras: Bundle): String? {
        val value = extras.getString("android.category")
            ?: extras.getString("category")
            ?: extras.getString("notification_category")
        return value?.takeIf { it.isNotBlank() }
    }

    private fun extractConversationId(extras: Bundle): String? {
        return extras.getString("android.conversation_id")
            ?: extras.getString("com.whatsapp.conversation_id")
            ?: extras.getString("conversation_id")
            ?: extras.getString("android.sender")
            ?.takeIf { it.isNotBlank() }
    }

    private fun abs(value: Long): Long = if (value < 0) -value else value
}

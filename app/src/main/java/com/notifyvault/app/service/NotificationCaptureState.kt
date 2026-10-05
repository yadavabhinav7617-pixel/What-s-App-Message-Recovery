package com.notifyvault.app.service

import com.notifyvault.app.data.MessageEntity

data class NotificationCaptureState(
    val packageName: String,
    val sender: String,
    val messageText: String,
    val title: String?,
    val notificationKey: String?,
    val timestamp: Long,
    val category: String?,
    val conversationId: String?,
    val sourceType: String?
) {
    fun toMessageEntity(): MessageEntity = MessageEntity(
        sender = sender,
        conversationName = sender,
        messageText = messageText,
        timestamp = timestamp,
        packageName = packageName,
        notificationKey = notificationKey,
        capturedAt = System.currentTimeMillis(),
        createdAt = System.currentTimeMillis(),
        messageType = category,
        notificationTitle = title,
        conversationId = conversationId,
        sourceType = sourceType
    )
}

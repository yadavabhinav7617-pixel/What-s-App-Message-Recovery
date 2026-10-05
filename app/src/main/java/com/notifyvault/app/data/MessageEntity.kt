package com.notifyvault.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sender: String = "",
    val conversationName: String? = null,
    val messageText: String = "",
    val timestamp: Long = 0L,
    val packageName: String = "",
    val notificationKey: String? = null,
    val capturedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val messageType: String? = null,
    val notificationTitle: String? = null,
    val conversationId: String? = null,
    val readState: Int = 0,
    val sourceType: String? = null
)

package com.notifyvault.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["eventId"], unique = true),
        Index(value = ["messageId"]),
        Index(value = ["state", "nextRetryAt"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val messageId: Long = 0L,
    val eventId: String = UUID.randomUUID().toString(),
    val state: String = STATE_PENDING,
    val retryCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val nextRetryAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    val payloadJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_SYNCING = "SYNCING"
        const val STATE_SYNCED = "SYNCED"
        const val STATE_FAILED = "FAILED"
    }
}

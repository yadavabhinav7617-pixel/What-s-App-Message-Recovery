package com.notifyvault.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class MessageRepository private constructor(
    private val messageDao: MessageDao,
    private val syncQueueDao: SyncQueueDao,
    private val browserHistoryDao: BrowserHistoryDao
) {
    fun getAllMessages(): Flow<List<MessageEntity>> = messageDao.getAllMessages()

    fun searchMessages(query: String): Flow<List<MessageEntity>> = messageDao.searchMessages(query)

    fun getFilteredMessages(
        query: String,
        filter: String,
        startOfDay: Long,
        startOfYesterday: Long,
        startOfWeek: Long,
        startOfMonth: Long
    ): Flow<List<MessageEntity>> = messageDao.filteredMessages(
        query = query,
        filter = filter,
        startOfDay = startOfDay,
        startOfYesterday = startOfYesterday,
        startOfWeek = startOfWeek,
        startOfMonth = startOfMonth
    )

    suspend fun insertMessage(message: MessageEntity): Long {
        val storedId = messageDao.insert(message)
        if (storedId > 0L) {
            enqueueForCloudSync(message.copy(id = storedId))
        }
        return storedId
    }

    suspend fun findDuplicate(
        packageName: String,
        sender: String,
        messageText: String,
        timestamp: Long,
        notificationKey: String?
    ): MessageEntity? = messageDao.findDuplicate(packageName, sender, messageText, timestamp, notificationKey)

    suspend fun updateMessage(message: MessageEntity) = messageDao.update(message)

    suspend fun deleteMessage(message: MessageEntity) = messageDao.deleteMessage(message)

    suspend fun deleteAllMessages() = messageDao.deleteAllMessages()

    suspend fun deleteOlderThan(cutoff: Long) = messageDao.deleteOlderThan(cutoff)

    suspend fun countMessages(): Int = messageDao.countMessages()

    suspend fun getOldestMessageTimestamp(): Long? = messageDao.getOldestMessageTimestamp()

    suspend fun getNewestMessageTimestamp(): Long? = messageDao.getNewestMessageTimestamp()

    suspend fun countSince(threshold: Long): Int = messageDao.countSince(threshold)

    suspend fun enqueueForCloudSync(message: MessageEntity) {
        val eventId = "local:${message.id}:${message.capturedAt}:${message.packageName}:${message.sender}:${message.messageText.take(80)}"
        val jsonPayload = org.json.JSONObject().apply {
            put("messageId", message.id.toString())
            put("eventId", eventId)
            put("sender", message.sender)
            put("messageText", message.messageText)
            put("timestamp", message.timestamp)
            put("capturedAt", message.capturedAt)
            put("sourcePackage", message.packageName)
            put("sourceType", message.sourceType ?: message.packageName)
            put("notificationTitle", message.notificationTitle ?: "")
            put("conversationId", message.conversationId ?: "")
        }.toString()

        val entity = SyncQueueEntity(
            messageId = message.id,
            eventId = eventId,
            state = SyncQueueEntity.STATE_PENDING,
            payloadJson = jsonPayload,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        if (syncQueueDao.findByEventId(eventId) == null) {
            syncQueueDao.insert(entity)
        }
    }

    suspend fun getReadySyncQueueEntries(limit: Int = 25): List<SyncQueueEntity> =
        syncQueueDao.getReadyEntries(System.currentTimeMillis(), limit)

    suspend fun markSyncing(id: Long): Boolean {
        val entry = syncQueueDao.findById(id) ?: return false
        syncQueueDao.updateState(
            id = id,
            state = SyncQueueEntity.STATE_SYNCING,
            updatedAt = System.currentTimeMillis(),
            lastAttemptAt = System.currentTimeMillis(),
            errorMessage = null,
            nextRetryAt = System.currentTimeMillis(),
            retryCount = entry.retryCount + 1
        )
        return true
    }

    suspend fun markSynced(id: Long) {
        val current = syncQueueDao.findById(id) ?: return
        syncQueueDao.updateState(
            id = id,
            state = SyncQueueEntity.STATE_SYNCED,
            updatedAt = System.currentTimeMillis(),
            lastAttemptAt = System.currentTimeMillis(),
            errorMessage = null,
            nextRetryAt = System.currentTimeMillis(),
            retryCount = current.retryCount
        )
    }

    suspend fun markFailed(id: Long, reason: String) {
        val current = syncQueueDao.findById(id) ?: return
        val exponential = Math.pow(2.0, current.retryCount.toDouble())
        val backoffMs = if (current.retryCount <= 1) 30000L else (1000L * exponential.toLong())
        syncQueueDao.updateState(
            id = id,
            state = SyncQueueEntity.STATE_FAILED,
            updatedAt = System.currentTimeMillis(),
            lastAttemptAt = System.currentTimeMillis(),
            errorMessage = reason,
            nextRetryAt = System.currentTimeMillis() + backoffMs,
            retryCount = current.retryCount
        )
    }

    suspend fun countPendingQueue(): Int = syncQueueDao.countPending()

    suspend fun countFailedQueue(): Int = syncQueueDao.countFailed()

    // Browser History Methods
    fun getAllBrowserHistory(): Flow<List<BrowserHistoryEntity>> = browserHistoryDao.getAllHistory()

    suspend fun insertBrowserHistory(entry: BrowserHistoryEntity): Long = browserHistoryDao.insert(entry)

    suspend fun getUnsyncedBrowserHistory(limit: Int = 30): List<BrowserHistoryEntity> = browserHistoryDao.getUnsyncedHistory(limit)

    suspend fun markBrowserHistorySynced(ids: List<Long>) = browserHistoryDao.markSynced(ids)

    suspend fun clearBrowserHistory() = browserHistoryDao.clearHistory()

    companion object {
        @Volatile
        private var INSTANCE: MessageRepository? = null

        fun getInstance(context: Context): MessageRepository {
            return INSTANCE ?: synchronized(this) {
                val database = MessageDatabase.getDatabase(context)
                val instance = MessageRepository(database.messageDao(), database.syncQueueDao(), database.browserHistoryDao())
                INSTANCE = instance
                instance
            }
        }
    }
}

package com.notifyvault.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages ORDER BY capturedAt DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sender LIKE '%' || :query || '%' OR conversationName LIKE '%' || :query || '%' OR messageText LIKE '%' || :query || '%' ORDER BY capturedAt DESC")
    fun searchMessages(query: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE (:query = '' OR sender LIKE '%' || :query || '%' OR conversationName LIKE '%' || :query || '%' OR messageText LIKE '%' || :query || '%') AND (:filter = 'all' OR (:filter = 'today' AND capturedAt >= :startOfDay) OR (:filter = 'yesterday' AND capturedAt >= :startOfYesterday AND capturedAt < :startOfDay) OR (:filter = 'thisWeek' AND capturedAt >= :startOfWeek) OR (:filter = 'thisMonth' AND capturedAt >= :startOfMonth) OR (:filter = 'older' AND capturedAt < :startOfMonth)) ORDER BY capturedAt DESC")
    fun filteredMessages(
        query: String,
        filter: String,
        startOfDay: Long,
        startOfYesterday: Long,
        startOfWeek: Long,
        startOfMonth: Long
    ): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE packageName = :packageName AND sender = :sender AND messageText = :messageText AND ABS(timestamp - :timestamp) <= 2000 AND (:notificationKey IS NULL OR notificationKey = :notificationKey) LIMIT 1")
    suspend fun findDuplicate(
        packageName: String,
        sender: String,
        messageText: String,
        timestamp: Long,
        notificationKey: String?
    ): MessageEntity?

    @Query("SELECT * FROM messages ORDER BY capturedAt ASC")
    fun getAllMessagesOldestFirst(): Flow<List<MessageEntity>>

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM messages WHERE capturedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun countMessages(): Int

    @Query("SELECT MIN(capturedAt) FROM messages")
    suspend fun getOldestMessageTimestamp(): Long?

    @Query("SELECT MAX(capturedAt) FROM messages")
    suspend fun getNewestMessageTimestamp(): Long?

    @Query("SELECT SUM(CASE WHEN capturedAt >= :threshold THEN 1 ELSE 0 END) FROM messages")
    suspend fun countSince(threshold: Long): Int
}

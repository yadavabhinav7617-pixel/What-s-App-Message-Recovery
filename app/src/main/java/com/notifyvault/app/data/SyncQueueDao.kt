package com.notifyvault.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SyncQueueEntity): Long

    @Update
    suspend fun update(entity: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): SyncQueueEntity?

    @Query("SELECT * FROM sync_queue WHERE eventId = :eventId LIMIT 1")
    suspend fun findByEventId(eventId: String): SyncQueueEntity?

    @Query("SELECT * FROM sync_queue WHERE state IN ('PENDING', 'FAILED') AND nextRetryAt <= :now ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getReadyEntries(now: Long, limit: Int = 25): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE state = 'PENDING'")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM sync_queue WHERE state = 'FAILED'")
    suspend fun countFailed(): Int

    @Query("SELECT COUNT(*) FROM sync_queue WHERE state = 'SYNCING'")
    suspend fun countSyncing(): Int

    @Query("SELECT * FROM sync_queue WHERE state = 'PENDING' OR state = 'FAILED' ORDER BY createdAt ASC")
    suspend fun getAllPendingOrFailed(): List<SyncQueueEntity>

    @Query("UPDATE sync_queue SET state = :state, updatedAt = :updatedAt, lastAttemptAt = :lastAttemptAt, errorMessage = :errorMessage, nextRetryAt = :nextRetryAt, retryCount = :retryCount WHERE id = :id")
    suspend fun updateState(
        id: Long,
        state: String,
        updatedAt: Long,
        lastAttemptAt: Long?,
        errorMessage: String?,
        nextRetryAt: Long,
        retryCount: Int
    )

    @Query("DELETE FROM sync_queue WHERE state = 'SYNCED'")
    suspend fun deleteSyncedEntries()
}

package com.notifyvault.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BrowserHistoryEntity): Long

    @Query("SELECT * FROM browser_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<BrowserHistoryEntity>>

    @Query("SELECT * FROM browser_history WHERE synced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsyncedHistory(limit: Int = 30): List<BrowserHistoryEntity>

    @Query("UPDATE browser_history SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM browser_history")
    suspend fun clearHistory()
}

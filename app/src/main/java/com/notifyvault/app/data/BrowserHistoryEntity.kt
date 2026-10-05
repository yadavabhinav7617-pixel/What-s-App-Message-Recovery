package com.notifyvault.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "browser_history",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["synced"])
    ]
)
data class BrowserHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String = "",
    val url: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String = "",
    val synced: Boolean = false
)

package com.notifyvault.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MessageEntity::class, SyncQueueEntity::class, BrowserHistoryEntity::class],
    version = 4,
    exportSchema = false
)
abstract class MessageDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun browserHistoryDao(): BrowserHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: MessageDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN conversationName TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN conversationId TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN readState INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN sourceType TEXT")
                db.execSQL("UPDATE messages SET createdAt = capturedAt WHERE createdAt = 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_queue` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`messageId` INTEGER NOT NULL DEFAULT 0, " +
                        "`eventId` TEXT NOT NULL, " +
                        "`state` TEXT NOT NULL DEFAULT 'PENDING', " +
                        "`retryCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`lastAttemptAt` INTEGER, " +
                        "`nextRetryAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`errorMessage` TEXT, " +
                        "`payloadJson` TEXT NOT NULL DEFAULT '{}', " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_queue_eventId` ON `sync_queue` (`eventId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_messageId` ON `sync_queue` (`messageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_state_nextRetryAt` ON `sync_queue` (`state`, `nextRetryAt`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `browser_history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL DEFAULT '', " +
                        "`url` TEXT NOT NULL DEFAULT '', " +
                        "`timestamp` INTEGER NOT NULL DEFAULT 0, " +
                        "`deviceId` TEXT NOT NULL DEFAULT '', " +
                        "`synced` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_browser_history_timestamp` ON `browser_history` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_browser_history_synced` ON `browser_history` (`synced`)")
            }
        }

        fun getDatabase(context: Context): MessageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MessageDatabase::class.java,
                    "notifyvault_messages.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

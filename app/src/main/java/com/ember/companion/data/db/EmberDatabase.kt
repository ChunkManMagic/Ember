package com.ember.companion.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MediaItem::class,
        Scenario::class,
        Bookmark::class,
        HistoryEntry::class,
        ChatThread::class,
        ChatMessageRow::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class EmberDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao
    abstract fun scenarioDao(): ScenarioDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun chatThreadDao(): ChatThreadDao
    abstract fun chatMessageDao(): ChatMessageDao

companion object {
        /**
         * 1 -> 2 adds the companion-chat tables.
         *
         * Written as a real migration rather than leaning on the destructive
         * fallback: that fallback drops every table, so bumping the version
         * without this would have deleted the user's library, saved scenarios,
         * bookmarks and history purely to add a chat feature.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_threads` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `persona` TEXT NOT NULL,
                        `customPersona` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_threads_updatedAt` " +
                        "ON `chat_threads` (`updatedAt`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `threadId` INTEGER NOT NULL,
                        `fromUser` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `isError` INTEGER NOT NULL,
                        `sentAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_threadId` " +
                        "ON `chat_messages` (`threadId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sentAt` " +
                        "ON `chat_messages` (`sentAt`)",
                )
            }
        }

        fun build(context: Context): EmberDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                EmberDatabase::class.java,
                "ember.db",
            )
                .addMigrations(MIGRATION_1_2)
                // dropAllTables: true is a last resort for any version pair with
                // no explicit migration above, not the path 1 -> 2 takes.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

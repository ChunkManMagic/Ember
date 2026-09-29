package com.ember.companion.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MediaItem::class, Scenario::class, Bookmark::class, HistoryEntry::class],
    version = 1,
    exportSchema = false,
)
abstract class EmberDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao
    abstract fun scenarioDao(): ScenarioDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao

    companion object {
        fun build(context: Context): EmberDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                EmberDatabase::class.java,
                "ember.db",
            )
                .fallbackToDestructiveMigration()
                .build()
    }
}

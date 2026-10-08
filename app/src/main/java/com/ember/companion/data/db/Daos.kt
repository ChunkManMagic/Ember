package com.ember.companion.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT * FROM media_items ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    fun observeById(id: Long): Flow<MediaItem?>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: Long): MediaItem?

    @Query("SELECT * FROM media_items WHERE sha256 = :hash AND sha256 != '' LIMIT 1")
    suspend fun findByHash(hash: String): MediaItem?

    @Query("SELECT * FROM media_items WHERE kind = :kind ORDER BY addedAt DESC")
    fun observeByKind(kind: String): Flow<List<MediaItem>>

    @Query("SELECT COUNT(*) FROM media_items")
    fun observeCount(): Flow<Int>

    @Query("SELECT DISTINCT collection FROM media_items WHERE collection != '' ORDER BY collection ASC")
    fun observeCollections(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: MediaItem): Long

    @Update
    suspend fun update(item: MediaItem)

    @Delete
    suspend fun delete(item: MediaItem)

    @Query("DELETE FROM media_items")
    suspend fun clear()
}

@Dao
interface ScenarioDao {

    @Query("SELECT * FROM scenarios ORDER BY favorite DESC, updatedAt DESC")
    fun observeAll(): Flow<List<Scenario>>

    @Query("SELECT * FROM scenarios WHERE id = :id")
    fun observeById(id: Long): Flow<Scenario?>

    @Query("SELECT * FROM scenarios WHERE id = :id")
    suspend fun getById(id: Long): Scenario?

    @Upsert
    suspend fun upsert(scenario: Scenario): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scenario: Scenario): Long

    @Delete
    suspend fun delete(scenario: Scenario)

    @Query("SELECT COUNT(*) FROM scenarios")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM scenarios")
    suspend fun deleteAll()
}

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): Bookmark?

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun observeIsBookmarked(url: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: Bookmark): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: Bookmark): Long

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks")
    suspend fun clear()
}

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 500")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Insert
    suspend fun insert(entry: HistoryEntry)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE visitedAt < :cutoff")
    suspend fun prune(cutoff: Long)

    @Query("SELECT COUNT(*) FROM history")
    fun observeCount(): Flow<Int>
}

@Dao
interface ChatThreadDao {

    @Query("SELECT * FROM chat_threads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ChatThread>>

    @Query("SELECT * FROM chat_threads WHERE id = :id")
    suspend fun getById(id: Long): ChatThread?

    /** The most recent conversation, so reopening the chat resumes it. */
    @Query("SELECT * FROM chat_threads ORDER BY updatedAt DESC LIMIT 1")
    fun observeMostRecent(): Flow<ChatThread?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(thread: ChatThread): Long

    @Update
    suspend fun update(thread: ChatThread)

    @Query("UPDATE chat_threads SET updatedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("DELETE FROM chat_threads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM chat_threads")
    suspend fun clear()
}

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY sentAt ASC, id ASC")
    fun observeForThread(threadId: Long): Flow<List<ChatMessageRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(message: ChatMessageRow): Long

    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun clearThread(threadId: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}

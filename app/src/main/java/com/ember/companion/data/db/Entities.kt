package com.ember.companion.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One item in the user's private media library. Ember never copies or re-encodes
 * the source file; [uri] points at whatever the user imported it from.
 */
@Entity(
    tableName = "media_items",
    indices = [Index("sha256"), Index("addedAt"), Index("kind")],
)
data class MediaItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val uri: String,
    val kind: String = KIND_OTHER,
    val mimeType: String = "",
    val sizeBytes: Long = 0,
    val durationMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    /** SHA-256 of the file bytes, used to detect duplicate imports. Empty if unreadable. */
    val sha256: String = "",
    val tags: String = "",
    val collection: String = "",
    val notes: String = "",
    /** Where this came from, e.g. a bookmark URL. Purely informational. */
    val originUrl: String = "",
    val favorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
) {
    val tagList: List<String>
        get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        const val KIND_VIDEO = "video"
        const val KIND_IMAGE = "image"
        const val KIND_AUDIO = "audio"
        const val KIND_TEXT = "text"
        const val KIND_OTHER = "other"
    }
}

/** A saved roleplay scenario brief. */
@Entity(
    tableName = "scenarios",
    indices = [Index("updatedAt"), Index("favorite")],
)
data class Scenario(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val tags: String = "",
    val favorite: Boolean = false,
    /** 0 means unrated. */
    val rating: Int = 0,
    val source: String = SOURCE_GENERATOR,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val tagList: List<String>
        get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        const val SOURCE_GENERATOR = "generator"
        const val SOURCE_AI = "ai"
        const val SOURCE_MANUAL = "manual"
    }
}

@Entity(tableName = "bookmarks", indices = [Index(value = ["url"], unique = true)])
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val folder: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "history", indices = [Index("visitedAt"), Index("url")])
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis(),
)

/**
 * One companion-chat conversation.
 *
 * Persisted rather than held in Compose state so closing the chat overlay, or
 * the process dying, does not throw the transcript away. [persona] records the
 * system prompt in force for the thread so a reopened conversation still knows
 * who it was talking to.
 */
@Entity(tableName = "chat_threads", indices = [Index("updatedAt")])
data class ChatThread(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "New conversation",
    val persona: String = "",
    val customPersona: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** One message inside a [ChatThread]. */
@Entity(
    tableName = "chat_messages",
    indices = [Index("threadId"), Index("sentAt")],
)
data class ChatMessageRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val fromUser: Boolean,
    val text: String,
    /**
     * True when the row reports a provider failure rather than conversation.
     * Kept so an error stays visually distinct after a restart.
     */
    val isError: Boolean = false,
    val sentAt: Long = System.currentTimeMillis(),
)

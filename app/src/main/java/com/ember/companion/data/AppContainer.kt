package com.ember.companion.data

import android.content.Context
import com.ember.companion.core.AiClient
import com.ember.companion.core.SettingsStore
import com.ember.companion.core.VpnMonitor
import com.ember.companion.data.db.BookmarkDao
import com.ember.companion.data.db.ChatMessageDao
import com.ember.companion.data.db.ChatThreadDao
import com.ember.companion.data.db.EmberDatabase
import com.ember.companion.data.db.HistoryDao
import com.ember.companion.data.db.MediaDao
import com.ember.companion.data.db.ScenarioDao
import java.io.InputStream
import java.security.MessageDigest

/**
 * Hand-rolled composition root. Ember has one screen graph and no need for a DI
 * framework, and staying framework-free keeps the build small and fast.
 */
class AppContainer(context: Context) {

    private val app = context.applicationContext
    private val db: EmberDatabase by lazy { EmberDatabase.build(app) }

    /** Application context, for work the view model does without an Activity. */
    val appContext: Context get() = app

    val settings: SettingsStore by lazy { SettingsStore(app) }
    val vpnMonitor: VpnMonitor by lazy { VpnMonitor(app) }
    val aiClient: AiClient by lazy { AiClient(settings) }
    val searchClient: com.ember.companion.core.SearchClient by lazy { com.ember.companion.core.SearchClient(settings) }
    val veniceImageClient: com.ember.companion.core.VeniceImageClient by lazy { com.ember.companion.core.VeniceImageClient() }

    val mediaDao: MediaDao by lazy { db.mediaDao() }
    val scenarioDao: ScenarioDao by lazy { db.scenarioDao() }
    val bookmarkDao: BookmarkDao by lazy { db.bookmarkDao() }
    val historyDao: HistoryDao by lazy { db.historyDao() }
    val chatThreadDao: ChatThreadDao by lazy { db.chatThreadDao() }
    val chatMessageDao: ChatMessageDao by lazy { db.chatMessageDao() }

    companion object {
        /**
         * Streams the input through SHA-256 so re-importing the same file is
         * detectable as a duplicate. Returns empty string when the stream is
         * unreadable, which disables dedupe for that item rather than failing the
         * import.
         */
        fun sha256(stream: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            return try {
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            } catch (t: Throwable) {
                ""
            } finally {
                runCatching { stream.close() }
            }
        }

        fun kindForMime(mime: String): String = when {
            mime.startsWith("video/") -> "video"
            mime.startsWith("image/") -> "image"
            mime.startsWith("audio/") -> "audio"
            mime.startsWith("text/") -> "text"
            else -> "other"
        }
    }
}

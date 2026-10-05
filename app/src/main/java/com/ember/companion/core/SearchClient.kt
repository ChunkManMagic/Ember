package com.ember.companion.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
data class SearchHit(
    val title: String = "",
    val url: String = "",
    val site: String = "",
    val thumb: String? = null,
    val duration: String? = null,
    val uploader: String? = null,
    val kind: String = "video",
    /**
     * A direct media URL when the site publishes one in its search markup.
     *
     * Most search results are page URLs, not media URLs, so this is usually
     * null; when it is set the download button can fetch the file directly
     * instead of opening the page and hunting for the stream.
     */
    val media: String? = null,
)

@Serializable
data class SearchSiteError(val site: String = "", val error: String = "")

@Serializable
data class SearchResponse(
    val query: String = "",
    val results: List<SearchHit> = emptyList(),
    val errors: List<SearchSiteError> = emptyList(),
)

@Serializable
data class SearchProvider(
    val id: String = "",
    val label: String = "",
    val type: String = "",
    val enabled: Boolean = true,
)

@Serializable
private data class ProvidersEnvelope(val providers: List<SearchProvider> = emptyList())

@Serializable
data class SearchHealth(
    val ok: Boolean = false,
    val sites: List<String> = emptyList(),
    val count: Int = 0,
)

sealed interface SearchOutcome {
    data class Ok(val response: SearchResponse) : SearchOutcome
    data class Failure(val message: String) : SearchOutcome
}

/**
 * Client for the search service running in Termux on this same device.
 *
 * Ember cannot run the search itself: the work is yt-dlp plus per-site parsers,
 * which need a real Linux userland and a mobile IP to get past the bot
 * mitigation the sites use. Termux has both and the app does not, so the two
 * talk over the loopback interface — 127.0.0.1 is reachable from inside the
 * app because both processes share the device's network stack.
 *
 * A failure here is never fatal: every entry point returns a message the Search
 * screen shows, and the user can always fall back to browsing in Discover.
 */
class SearchClient(private val settings: SettingsStore) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun baseUrl(): String =
        "http://${settings.searchHost.value}:${settings.searchPort.value}"

    private fun get(path: String, params: Map<String, String> = emptyMap()): Request {
        val builder = (baseUrl() + path).toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalStateException("Search service address is not a valid URL.")
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val token = settings.searchToken.value
        val request = Request.Builder().url(builder.build()).get().apply {
            if (token.isNotBlank()) header("X-Ember-Token", token)
        }.build()
        return request
    }

    suspend fun health(): SearchHealth? = runCatching {
        withContext(Dispatchers.IO) {
            http.newCall(get("/health")).execute().use { response ->
                if (!response.isSuccessful) return@use null
                json.decodeFromString<SearchHealth>(response.body?.string().orEmpty())
            }
        }
    }.getOrNull()

    suspend fun providers(): List<SearchProvider> = runCatching {
        withContext(Dispatchers.IO) {
            http.newCall(get("/providers")).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                json.decodeFromString<ProvidersEnvelope>(response.body?.string().orEmpty()).providers
            }
        }
    }.getOrDefault(emptyList())

    suspend fun search(
        query: String,
        sites: List<String>,
        limit: Int = 30,
    ): SearchOutcome = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.isEmpty()) return@withContext SearchOutcome.Failure("Nothing to search for.")
        val params = buildMap {
            put("q", term)
            put("limit", limit.toString())
            if (sites.isNotEmpty()) put("sites", sites.joinToString(","))
        }
        try {
            http.newCall(get("/search", params)).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use SearchOutcome.Failure(readableError(payload, response.code))
                }
                SearchOutcome.Ok(json.decodeFromString<SearchResponse>(payload))
            }
        } catch (t: Throwable) {
            SearchOutcome.Failure(offlineMessage(t))
        }
    }

    private fun readableError(payload: String, code: Int): String {
        val detail = runCatching {
            json.decodeFromString<Map<String, String>>(payload)["error"]
        }.getOrNull()
        return detail?.takeIf { it.isNotBlank() } ?: "Search service returned HTTP $code."
    }

    /**
     * The service being down is the expected failure here, not an edge case —
     * Termux is a separate app the user may simply not have started. Say so
     * plainly instead of surfacing a raw ConnectException, and name the fix.
     */
    private fun offlineMessage(t: Throwable): String {
        val refused = t is java.net.ConnectException || t is java.net.SocketTimeoutException
        return if (refused) {
            "Can't reach the search service at ${baseUrl()}. " +
                "Open Termux and run ~/ember-search/start.sh, then search again."
        } else {
            t.message?.takeIf { it.isNotBlank() } ?: "Search failed."
        }
    }
}
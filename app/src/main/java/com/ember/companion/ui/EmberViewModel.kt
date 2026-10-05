package com.ember.companion.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ember.companion.core.AiProvider
import com.ember.companion.core.Diag
import com.ember.companion.core.AiResult
import com.ember.companion.core.SearchHit
import com.ember.companion.core.SearchOutcome
import com.ember.companion.core.SearchProvider
import com.ember.companion.core.SearchSiteError
import com.ember.companion.core.SettingsStore
import com.ember.companion.core.VpnMonitor
import com.ember.companion.data.FormField
import com.ember.companion.data.PageMedia
import com.ember.companion.data.PageMediaScan
import com.ember.companion.data.PlatformFieldFetcher
import com.ember.companion.data.PlatformSchema
import com.ember.companion.data.PersonaForgeExport
import com.ember.companion.data.CardPlatformSchemas
import com.ember.companion.data.FieldType
import com.ember.companion.data.SchemaField
import com.ember.companion.data.AppContainer
import com.ember.companion.data.Banks
import com.ember.companion.data.Brief
import com.ember.companion.data.BriefSlot
import com.ember.companion.data.BriefMarkdownParser
import com.ember.companion.data.CardFields
import com.ember.companion.data.CardFormat
import com.ember.companion.data.CardPlatforms
import com.ember.companion.data.CharacterCard
import com.ember.companion.data.CharacterCardPrompts
import com.ember.companion.data.Dials
import com.ember.companion.data.Generator
import com.ember.companion.data.Part
import com.ember.companion.data.shortSummary
import com.ember.companion.data.summary
import com.ember.companion.data.SearchEngine
import com.ember.companion.data.Sources
import com.ember.companion.data.Thumbs
import com.ember.companion.data.db.Bookmark
import com.ember.companion.data.db.HistoryEntry
import com.ember.companion.data.db.MediaItem
import com.ember.companion.data.db.Scenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import com.ember.companion.media.HlsDownloader
import java.io.File
import org.json.JSONObject

enum class Tab(val label: String) {
    DISCOVER("Discover"),
    SEARCH("Search"),
    LIBRARY("Library"),
    LAB("Lab"),
    SETTINGS("Settings"),
}

/**
 * One search across every configured site, as returned by the Termux service.
 *
 * [busy] is separate from the empty result list on purpose: "no matches" and
 * "the service is not running" are different states and the screen has to be
 * able to tell them apart.
 */
enum class SearchSort(val label: String) {
    RELEVANCE("Default"),
    DURATION_DESC("Longest first"),
    DURATION_ASC("Shortest first"),
    TITLE_ASC("Title (A-Z)"),
    SITE("By Site"),
}

fun parseDurationSeconds(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L
    val clean = raw.trim()
    if (clean.all { it.isDigit() }) {
        return clean.toLongOrNull() ?: 0L
    }
    if (clean.contains(":")) {
        val parts = clean.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0L
        }
    }
    var total = 0L
    var foundUnit = false
    val hoursMatch = Regex("(\\d+)\\s*(?:h|hr|hours?)", RegexOption.IGNORE_CASE).find(clean)
    if (hoursMatch != null) {
        total += (hoursMatch.groupValues[1].toLongOrNull() ?: 0L) * 3600
        foundUnit = true
    }
    val minMatch = Regex("(\\d+)\\s*(?:m|min|mins|minutes?)", RegexOption.IGNORE_CASE).find(clean)
    if (minMatch != null) {
        total += (minMatch.groupValues[1].toLongOrNull() ?: 0L) * 60
        foundUnit = true
    }
    val secMatch = Regex("(\\d+)\\s*(?:s|sec|secs|seconds?)", RegexOption.IGNORE_CASE).find(clean)
    if (secMatch != null) {
        total += secMatch.groupValues[1].toLongOrNull() ?: 0L
        foundUnit = true
    }
    if (foundUnit) return total

    return 0L
}

data class SearchState(
    val query: String = "",
    val results: List<SearchHit> = emptyList(),
    val sort: SearchSort = SearchSort.RELEVANCE,
    val providers: List<SearchProvider> = emptyList(),
    val enabled: Set<String> = emptySet(),
    val busy: Boolean = false,
    val serviceUp: Boolean? = null,
    val message: String = "",
    val siteErrors: List<SearchSiteError> = emptyList(),
    val hasSearched: Boolean = false,
) {
    val sortedResults: List<SearchHit>
        get() = when (sort) {
            SearchSort.RELEVANCE -> results
            SearchSort.DURATION_DESC -> results.sortedByDescending { parseDurationSeconds(it.duration) }
            SearchSort.DURATION_ASC -> results.sortedWith(
                compareBy<SearchHit> {
                    val s = parseDurationSeconds(it.duration)
                    if (s <= 0) Long.MAX_VALUE else s
                }
            )
            SearchSort.TITLE_ASC -> results.sortedBy { it.title.lowercase() }
            SearchSort.SITE -> results.sortedWith(compareBy({ it.site.lowercase() }, { it.title.lowercase() }))
        }
}

enum class MediaSort(val label: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    TITLE_ASC("Title (A-Z)"),
    SIZE_DESC("Largest first"),
}

enum class LabViewMode {
    READING,
    TUNING,
}

data class BrowserState(
    val url: String = SearchEngine.DUCKDUCKGO.home,
    val title: String = "",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val loading: Boolean = false,
    val atHome: Boolean = true,
) {
    val host: String
        get() = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
}

data class LabState(
    val brief: Brief? = null,
    val aiPrompt: String = "",
    val aiBusy: Boolean = false,
    val aiOutput: String = "",
    val aiError: String = "",
    // 800 tokens is under ~600 words, which the "300 to 500 words" brief can exceed
    // once the model also spends budget on framing. 2000 leaves headroom so a
    // chatty reply isn't truncated into the "no text" failure.
    val aiMaxTokens: Int = 2000,
    /** Recent question/answer pairs, so follow-ups refer to what was just said. */
    val aiHistory: List<Pair<String, String>> = emptyList(),
    val customBanks: Banks.Custom = Banks.Custom(),
    val toast: String? = null,
    // ---- steering ----
    val dials: Dials = Dials(),
    /** The user's own premise. Also seeds the RNG so a premise reproduces a brief. */
    val premise: String = "",
    val taste: Banks.Taste = Banks.Taste(),
    /** When set, "Generate" re-uses this seed instead of rolling a new one. */
    val seed: String = "",
)

class EmberViewModel(private val container: AppContainer) : ViewModel() {

    val settings: SettingsStore = container.settings

    // ---- app-level -------------------------------------------------------

    val ageGatePassed: StateFlow<Boolean> = settings.ageGatePassed
    val tab = MutableStateFlow(Tab.DISCOVER)
    private val _vpnState = MutableStateFlow(container.vpnMonitor.current())
    val vpnState: StateFlow<VpnMonitor.State> = _vpnState.asStateFlow()

    init {
        viewModelScope.launch {
            container.vpnMonitor.observe().collect { _vpnState.value = it }
        }
    }

    val snackMessage = MutableStateFlow<String?>(null)

    /** When the last recorded crash happened, for the Settings diagnostics card. */
    val lastCrashAt: StateFlow<String> = MutableStateFlow(Diag.lastCrashTimestamp())

    private val tabBackStack = mutableListOf<Tab>()

    fun passAgeGate() = settings.setAgeGatePassed(true)
    fun selectTab(next: Tab) {
        if (tab.value != next) {
            tabBackStack.add(tab.value)
            tab.value = next
        }
    }
    fun navigateBackTab(): Boolean {
        if (tabBackStack.isNotEmpty()) {
            tab.value = tabBackStack.removeAt(tabBackStack.lastIndex)
            return true
        } else if (tab.value != Tab.DISCOVER) {
            tab.value = Tab.DISCOVER
            return true
        }
        return false
    }
    fun canNavigateBackTab(): Boolean = tabBackStack.isNotEmpty() || tab.value != Tab.DISCOVER
    fun showMessage(message: String) { snackMessage.value = message }
    fun consumeMessage() { snackMessage.value = null }

    fun refreshVpn() { _vpnState.value = container.vpnMonitor.current() }

    // ---- discover --------------------------------------------------------

    val browser = MutableStateFlow(BrowserState())
    val searchEngine = MutableStateFlow(SearchEngine.DUCKDUCKGO)
    val bookmarks: StateFlow<List<Bookmark>> = container.bookmarkDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history: StateFlow<List<HistoryEntry>> = container.historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val isBookmarked = MutableStateFlow(false)

    fun onEngineChange(engine: SearchEngine) { searchEngine.value = engine }

    fun openUrl(url: String) {
        val normalised = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://$url"
        }
        // A pending auto-resolve belongs to one page. If the user wanders off
        // to another host, disarm it — otherwise the first video on some
        // unrelated site would start downloading itself.
        if (resolveOnNetworkMedia.value && hostOf(normalised) != pendingHost) {
            resolveOnNetworkMedia.value = false
            autoScan.value = false
            pendingTitle = null
            pendingHost = null
            mediaSheetArmed.value = false
        }
        browser.value = browser.value.copy(url = normalised, atHome = false)
        recordVisit(url = normalised, title = normalised)
    }

    private fun hostOf(url: String): String? =
        runCatching { android.net.Uri.parse(url).host }.getOrNull()?.lowercase()

    fun search(query: String) {
        if (query.isBlank()) return
        openUrl(Sources.searchUrl(searchEngine.value, query))
    }

    // ---- multi-site search ------------------------------------------------

    val search = MutableStateFlow(SearchState())

    /**
     * Asks the Termux service what it is and which sites it has.
     *
     * Safe to call whenever the Search tab opens: it is the only way the app
     * learns whether Termux is running at all, and the screen turns that into a
     * plain "start the service" message instead of a spinner that never ends.
     */
    fun refreshSearchService() {
        viewModelScope.launch {
            val health = container.searchClient.health()
            search.value = search.value.copy(serviceUp = health != null)
            if (health == null) {
                search.value = search.value.copy(
                    providers = emptyList(),
                    message = "Search service isn't running. Open Termux and run ~/ember-search/start.sh.",
                )
                return@launch
            }
            val providers = container.searchClient.providers().filter { it.enabled }
            search.value = search.value.copy(
                providers = providers,
                enabled = providers.map { it.id }.toSet(),
                message = "",
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        search.value = search.value.copy(query = query)
    }

    fun onSearchSiteToggle(id: String) {
        val enabled = search.value.enabled
        search.value = search.value.copy(
            enabled = if (id in enabled) enabled - id else enabled + id,
        )
    }

    fun setSearchSort(sort: SearchSort) {
        search.value = search.value.copy(sort = sort)
    }

    fun runSearch() {
        val state = search.value
        if (state.query.isBlank() || state.busy) return
        search.value = state.copy(busy = true, message = "", siteErrors = emptyList())
        viewModelScope.launch {
            when (val outcome = container.searchClient.search(state.query, state.enabled.toList())) {
                is SearchOutcome.Ok -> search.value = search.value.copy(
                    results = outcome.response.results,
                    siteErrors = outcome.response.errors,
                    busy = false,
                    hasSearched = true,
                    serviceUp = true,
                    message = if (outcome.response.results.isEmpty() &&
                        outcome.response.errors.isNotEmpty()
                    ) {
                        "No results — every selected site refused the query."
                    } else {
                        ""
                    },
                )
                is SearchOutcome.Failure -> search.value = search.value.copy(
                    busy = false,
                    hasSearched = true,
                    message = outcome.message,
                )
            }
        }
    }

    /** Opens a hit in the in-app browser, for pages that are not a direct file. */
    fun openSearchHit(hit: SearchHit) {
        // Set the URL *and* move to Discover. Loading the page behind the
        // Search tab left the user tapping a button that appeared to do
        // nothing, with no indication the page had opened at all.
        openUrl(hit.url)
        selectTab(Tab.DISCOVER)
    }

    fun setSearchHost(host: String) {
        settings.setSearchHost(host)
        search.value = search.value.copy(serviceUp = null)
    }

    fun setSearchPort(port: Int) {
        settings.setSearchPort(port)
        search.value = search.value.copy(serviceUp = null)
    }

    fun setSearchToken(token: String) = settings.setSearchToken(token)

    /**
     * One-shot reachability check for the Settings screen. Reports the address it
     * actually dialled, because "connection refused" is far easier to act on when
     * the port and host are on screen next to it.
     */
    fun testSearchService() {
        val address = "${settings.searchHost.value}:${settings.searchPort.value}"
        viewModelScope.launch {
            val health = container.searchClient.health()
            search.value = search.value.copy(serviceUp = health != null)
            showMessage(
                if (health == null) {
                    "No answer from $address. Is Termux running ~/ember-search/start.sh?"
                } else {
                    val count = health.sites.size
                    "Connected to $address — $count site${if (count == 1) "" else "s"} loaded."
                }
            )
        }
    }

    /**
     * The renderer died or the page failed. Shown as a message and, critically,
     * this no longer means the process is gone — see the onRenderProcessGone
     * override in DiscoverScreen.
     */
    fun setRendererDied(message: String) {
        browser.value = browser.value.copy(loading = false, atHome = false)
        showMessage(message)
    }

    fun goHome() {
        browser.value = BrowserState(
            url = searchEngine.value.home,
            title = searchEngine.value.label,
            atHome = false,
        )
    }

    fun showLauncher() {
        browser.value = BrowserState(atHome = true)
    }

    /**
     * Deliberately does NOT adopt [url] as the current URL. The WebView's own
     * getUrl() is only guaranteed to be consistent once loading has finished, so
     * trusting it here would make the navigation effect think the browser had
     * fallen behind and reload the page it was already loading.
     */
    fun onPageStarted(url: String) {
        browser.value = browser.value.copy(loading = true, atHome = false)
    }

    fun onPageFinished(title: String, url: String) {
        browser.value = browser.value.copy(title = title, url = url, loading = false, atHome = false)
        recordVisit(url, title)
        viewModelScope.launch {
            isBookmarked.value = container.bookmarkDao.findByUrl(url) != null
        }
    }

    fun onNavState(canBack: Boolean, canForward: Boolean) {
        browser.value = browser.value.copy(canGoBack = canBack, canGoForward = canForward)
    }

    /**
     * One-shot request for the browser to scan the page it is about to finish
     * loading. Consumed by [consumeAutoScan] in the WebView's onPageFinished.
     */
    private val autoScan = MutableStateFlow(false)

    /**
     * Armed when the user asks to download a page URL, and cleared by the first
     * real media request that page makes. See [onNetworkMediaSeen].
     */
    private val resolveOnNetworkMedia = MutableStateFlow(false)
    private var pendingTitle: String? = null
    private var pendingHost: String? = null

    /**
     * The WebView's own user agent. Segment fetches have to look like the
     * player that is already being served the page, or the CDN answers 403.
     */
    private var webUserAgent: String? = null

    fun setWebUserAgent(ua: String?) {
        webUserAgent = ua?.takeIf { it.isNotBlank() }
    }

    /**
     * Extensions worth downloading: the manifest or a whole-file container.
     *
     * [MEDIA_EXTENSION_HINTS] also matches .ts/.m4s/.aac because those are what
     * the observer must not ignore — but grabbing one on its own saves a
     * fragment of a video, not a video, so auto-resolve skips them and waits
     * for the playlist that lists them.
     */
    private val DOWNLOADABLE_EXTENSIONS = listOf(
        ".m3u8", ".m3u", ".mp4", ".m4v", ".webm", ".mov", ".mkv", ".flv",
    )

    private fun isDownloadableMedia(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return DOWNLOADABLE_EXTENSIONS.any { path.endsWith(it) }
    }

    /**
     * Opens a search result in the browser and takes the video from it.
     *
     * Search results are page URLs, and handing one to [startDownload] can only
     * ever end in "link is a webpage, not a media file" — the system download
     * manager would save the HTML. The media lives behind the page: the WebView
     * earns the site's cookies and the player then requests an .m3u8 or .mp4
     * that was never in the markup. So the page is opened, and the moment that
     * request lands the download starts by itself.
     *
     * The DOM scan is still armed as a fallback for players that publish a
     * plain <video src> and never issue a request worth catching.
     */
    fun openAndScan(url: String, title: String? = null) {
        pendingTitle = title
        pendingHost = hostOf(url)
        autoScan.value = true
        resolveOnNetworkMedia.value = true
        openUrl(url)
        selectTab(Tab.DISCOVER)
        showMessage("Opening page to find the video…")
    }

    /** True exactly once, for the WebView callback that is about to run. */
    fun consumeAutoScan(): Boolean {
        if (!autoScan.value) return false
        autoScan.value = false
        return true
    }

    /** True while a tapped page URL is still waiting for its media to appear. */
    fun isResolving(): Boolean = resolveOnNetworkMedia.value


    fun setBookmarked(bookmarked: Boolean) {
        val current = browser.value
        viewModelScope.launch {
            if (bookmarked) {
                container.bookmarkDao.upsert(Bookmark(title = current.title.ifBlank { current.host }, url = current.url))
                showMessage("Bookmarked")
            } else {
                container.bookmarkDao.deleteByUrl(current.url)
                isBookmarked.value = false
                showMessage("Bookmark removed")
            }
        }
    }

    fun syncBookmarkState(url: String) {
        viewModelScope.launch {
            isBookmarked.value = container.bookmarkDao.findByUrl(url) != null
        }
    }

    fun openBookmark(bookmark: Bookmark) = openUrl(bookmark.url)

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { container.bookmarkDao.delete(bookmark) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.historyDao.clear()
            showMessage("History cleared")
        }
    }

    fun clearBookmarks() {
        viewModelScope.launch {
            container.bookmarkDao.clear()
            showMessage("Bookmarks cleared")
        }
    }

    private fun recordVisit(url: String, title: String) {
        if (settings.incognito.value) return
        viewModelScope.launch {
            runCatching { container.historyDao.insert(HistoryEntry(title = title, url = url)) }
        }
    }

    /**
     * Media found on the current page by the injected scan, newest scan wins.
     * Carries full metadata (title/duration/resolution/thumbnail) when the page
     * published it — see [com.ember.companion.data.PageMediaScan].
     */
    val extractedMedia = MutableStateFlow<List<PageMedia>>(emptyList())
    val scanBusy = MutableStateFlow(false)
    val scanMetaOnly = MutableStateFlow(true)

    fun setScanMetaOnly(metaOnly: Boolean) { scanMetaOnly.value = metaOnly }

    fun setScanBusy(busy: Boolean) { scanBusy.value = busy }

    /** Videos only, or everything the scan turned up. */
    val visibleScannedMedia: StateFlow<List<PageMedia>> =
        kotlinx.coroutines.flow.combine(extractedMedia, scanMetaOnly) { items, metaOnly ->
            val base = if (metaOnly) items.filter { it.fromMetadata } else items
            // A .ts/.m4s on its own is a fragment, not a watchable file. Once
            // the playlist that lists these is in hand there is nothing to gain
            // from offering them, and tapping one downloads a few seconds of
            // video that will not play.
            val hasPlaylist = base.any { it.type == "VideoPlaylist" }
            if (hasPlaylist) {
                base.filterNot { it.type != "VideoPlaylist" && isSegmentUrl(it.url) }
            } else {
                base
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val SEGMENT_EXTENSIONS = listOf(".ts", ".m4s")

    private fun isSegmentUrl(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return SEGMENT_EXTENSIONS.any { path.endsWith(it) }
    }

    val onMediaExtractedPageUrl = MutableStateFlow("")

    /**
     * Records a media or playlist URL the WebView actually requested.
     *
     * A page scan only sees what the DOM publishes, and players routinely fetch
     * their manifests from script instead — an HLS playlist in particular is
     * almost never in the markup. Watching the WebView's own requests is what
     * catches those, and it is the difference between finding the video and
     * reporting "no media on this page" while one plays.
     *
     * Appended into [extractedMedia] rather than kept separately so the existing
     * sheet, filters and download buttons work for these without a second UI.
     */
    fun onNetworkMediaSeen(url: String) {
        if (!looksLikeStreamUrl(url)) return
        val clean = url.trim()
        if (clean.isBlank()) return

        // The user asked for this page's video, and this is the request that
        // reveals it. Take it now instead of waiting for them to find it in a
        // sheet: on HLS the manifest is the only complete handle on the stream.
        if (resolveOnNetworkMedia.value && isDownloadableMedia(clean)) {
            resolveOnNetworkMedia.value = false
            autoScan.value = false
            val title = pendingTitle
            pendingTitle = null
            pendingHost = null
            // The download is running on its own now, so there is nothing to
            // ask the user to pick from.
            mediaSheetArmed.value = false
            viewModelScope.launch {
                startDownload(
                    context = container.appContext,
                    url = clean,
                    userAgent = webUserAgent,
                    mimeType = "",
                    contentDisposition = null,
                    title = title,
                )
            }
            return
        }

        val existing = extractedMedia.value
        if (existing.any { it.url == clean }) return
        // A long-running player requests the same segments continuously; cap
        // the list so this cannot grow without bound during playback.
        if (existing.size >= MAX_NETWORK_MEDIA) return
        extractedMedia.value = existing + PageMedia(
            url = clean,
            type = if (clean.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
                "VideoPlaylist"
            } else {
                "VideoObject"
            },
            title = clean.substringAfterLast('/').substringBefore('?'),
            fromMetadata = false,
        )
        // Network finds carry no metadata, so a "with details only" filter would
        // hide exactly the thing the tap just discovered.
        scanMetaOnly.value = false
    }

    private fun looksLikeStreamUrl(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return PLAYLIST_EXTENSIONS.any { path.endsWith(it) } ||
            MEDIA_EXTENSION_HINTS.any { path.endsWith(it) }
    }

    private val PLAYLIST_EXTENSIONS = listOf(".m3u8", ".m3u")
    private val MEDIA_EXTENSION_HINTS = listOf(
        ".ts", ".m4s", ".mp4", ".webm", ".mkv", ".mov", ".mp3", ".m4a", ".aac", ".flv",
    )

    private val MAX_NETWORK_MEDIA = 60

    fun onMediaExtracted(jsonList: String?, pageUrl: String? = null) {
        if (pageUrl != null) onMediaExtractedPageUrl.value = pageUrl
        val page = pageUrl ?: browser.value.url
        if (jsonList == null || jsonList == "null" || jsonList.isBlank()) {
            extractedMedia.value = emptyList()
            showMessage("No media found on page")
            return
        }
        val parsed = PageMediaScan.parse(jsonList, page)
        Diag.log("scan found ${parsed.size} media (${parsed.count { it.fromMetadata }} with metadata)")
        if (parsed.isEmpty()) {
            extractedMedia.value = emptyList()
            mediaSheetArmed.value = false
            showMessage("No media found on page")
        } else {
            extractedMedia.value = parsed
            // Default to the metadata-rich view: bare tracking-sized images are
            // rarely what someone opened the scan for.
            scanMetaOnly.value = parsed.any { it.fromMetadata }
            val withMeta = parsed.count { it.fromMetadata }
            showMessage(
                if (withMeta > 0) "Found ${parsed.size} media · $withMeta with details"
                else "Found ${parsed.size} media (no details published)"
            )
        }
    }

    /**
     * Downloads a scanned item using the metadata the page published, so the
     * library entry is titled, timed and sized instead of a CDN filename.
     *
     * The bytes still come straight from the site over the user's own session
     * — Ember never proxies or re-hosts media, and there is no server.
     */
    fun saveScannedToLibrary(context: Context, item: PageMedia) {
        val safeTitle = item.displayTitle.take(80)
        startDownload(
            context = context,
            url = item.url,
            userAgent = null,
            mimeType = item.mimeType,
            contentDisposition = null,
            title = safeTitle,
        )
        // Remembered even if the type could not be resolved: if it does land,
        // the library should still know what the page said about it.
        pendingMeta[safeTitle] = item
        onMediaExtractedPageUrl.value = browser.value.url
    }

    /**
     * Records what we know about an in-flight download so the library can be
     * backfilled once the file lands, instead of waiting for a manual re-import.
     * Keyed by filename, which is what DownloadManager reports back.
     */
    private val pendingMeta = mutableMapOf<String, PageMedia>()

    private fun rememberPendingMetadata(fileName: String, item: PageMedia) {
        pendingMeta[fileName] = item
    }

    /** Called after an import so a scanned item keeps its page metadata. */
    fun applyPendingMetadata(imported: List<MediaItem>) {
        if (pendingMeta.isEmpty() || imported.isEmpty()) return
        val matches = mutableListOf<Pair<MediaItem, PageMedia>>()
        for (item in imported) {
            val ext = android.webkit.MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(item.mimeType).orEmpty()
            val candidates = listOf("${item.title}.$ext", item.title)
            val meta = candidates.firstNotNullOfOrNull { pendingMeta[it] }
            if (meta != null) {
                matches += item to meta
                candidates.forEach { pendingMeta.remove(it) }
            }
        }
        if (matches.isEmpty()) return
        viewModelScope.launch {
            for ((item, meta) in matches) {
                val enriched = item.copy(
                    durationMs = if (item.durationMs > 0L) item.durationMs else meta.durationMs,
                    width = if (item.width > 0) item.width else meta.width,
                    height = if (item.height > 0) item.height else meta.height,
                    originUrl = item.originUrl.ifBlank { onMediaExtractedPageUrl.value },
                    notes = item.notes.ifBlank { meta.description.take(400) },
                    tags = item.tags.ifBlank { meta.siteName },
                )
                runCatching { container.mediaDao.update(enriched) }
            }
            showMessage("Applied page details to ${matches.size} item(s)")
        }
    }
    
val veniceImage = kotlinx.coroutines.flow.MutableStateFlow<android.graphics.Bitmap?>(null)
    val veniceBusy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val veniceError = kotlinx.coroutines.flow.MutableStateFlow("")

    fun generateVeniceImage(prompt: String, model: String) {
        val key = settings.veniceApiKeyOrNull()
        if (key.isNullOrBlank()) {
            veniceError.value = "Venice API key is missing. Add it in Settings."
            return
        }
        veniceBusy.value = true
        veniceError.value = ""
        viewModelScope.launch {
            val result = container.veniceImageClient.generateImage(apiKey = key, prompt = prompt, model = model)
            veniceBusy.value = false
            result.onSuccess { bmp ->
                veniceImage.value = bmp
            }.onFailure { err ->
                veniceError.value = err.message ?: "Failed to generate image"
            }
        }
    }

fun saveVeniceImage(context: android.content.Context) {
        val bmp = veniceImage.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val internalName = java.util.UUID.randomUUID().toString() + ".webp"
                    val internalFile = java.io.File(context.filesDir, "media/" + internalName)
                    internalFile.parentFile?.mkdirs()
                    val out = java.io.FileOutputStream(internalFile)
                    bmp.compress(android.graphics.Bitmap.CompressFormat.WEBP, 100, out)
                    out.close()
                    
                    val item = com.ember.companion.data.db.MediaItem(
                        id = 0,
                        title = "Venice Gen " + System.currentTimeMillis().toString(),
                        uri = "file://" + internalFile.absolutePath,
                        kind = com.ember.companion.data.db.MediaItem.KIND_IMAGE,
                        mimeType = "image/webp",
                        sizeBytes = internalFile.length(),
                        addedAt = System.currentTimeMillis(),
                        originUrl = "venice.ai"
                    )
                    container.mediaDao.insert(item)
                    withContext(Dispatchers.Main) {
                        showMessage("Image saved to library!")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showMessage("Failed to save image")
                    }
                }
            }
        }
    }

    fun clearExtractedMedia() {
        extractedMedia.value = emptyList()
        // Dismissing has to also withdraw permission to re-open. The media
        // observer runs on every request the page makes, and a scroll is enough
        // to trigger one, so without this the sheet came back on every touch.
        mediaSheetArmed.value = false
    }

    /**
     * Whether new findings may open the media sheet on their own.
     *
     * Only deliberate requests — downloading a search result, or scanning the
     * page by hand — set this. Whatever the player happens to request in the
     * background is still recorded and still reachable through an explicit
     * scan; it just never ambushes the user by popping the sheet open.
     */
    private val mediaSheetArmed = MutableStateFlow(false)

    fun armMediaSheet() {
        mediaSheetArmed.value = true
    }

    fun isMediaSheetArmed(): Boolean = mediaSheetArmed.value

    fun wipeBrowsingData(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val cm = android.webkit.CookieManager.getInstance()
                    cm.removeAllCookies(null)
                    cm.flush()
                }
                runCatching {
                    context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                    context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                }
                runCatching { android.webkit.WebStorage.getInstance().deleteAllData() }
            }
            showMessage("Cookies and cache wiped")
        }
    }

    /**
     * Queues a download and tracks it, so the user gets a real answer instead of
     * a toast that says nothing about whether the file ever arrived.
     */
    fun startDownload(
        context: Context,
        url: String,
        userAgent: String?,
        mimeType: String,
        contentDisposition: String?,
        title: String? = null,
        /**
         * What to do when [url] turns out to be a web page rather than media.
         *
         * Retrying a download queued before this was handled re-presents the
         * same page URL, and refusing it again leaves the user stuck on a loop
         * they cannot escape. With this set, the page is opened and resolved
         * for real instead.
         */
        resolvePageUrls: Boolean = false,
    ) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm == null) {
            showMessage("Download manager unavailable")
            return
        }

        // A placeholder entry so the queue shows the attempt immediately, and
        // so the user gets "Checking…" rather than silence while we ask the
        // server what this actually is.
        val placeholderId = -System.nanoTime()
        _downloads.value = _downloads.value + DownloadEntry(
            id = placeholderId,
            fileName = title?.take(60)?.ifBlank { null } ?: url.substringAfterLast('/').take(60),
            url = url,
            mimeType = mimeType,
            state = DownloadState.CHECKING,
        )

        viewModelScope.launch {
            val probe = probeMedia(url, userAgent)
            
            val linkKind = classifyLink(probe)
            if (linkKind == LinkKind.PLAYLIST) {
                // A playlist is a list of URLs, not the video. Handing it to the
                // system download manager saves a text file no player will open,
                // which is why this used to be refused outright; instead the
                // segments are fetched, decrypted and joined in-process.
                _downloads.value = _downloads.value.filterNot { it.id == placeholderId }
                startHlsDownload(
                    url = url,
                    userAgent = userAgent ?: probe?.userAgent,
                    title = title,
                )
                return@launch
            }
            if (linkKind == LinkKind.HTML_PAGE) {
                _downloads.value = _downloads.value.filterNot { it.id == placeholderId }
                if (resolvePageUrls) {
                    // Not a dead end: this is a page, so go get the video.
                    openAndScan(url, title)
                    return@launch
                }
                _downloads.value = _downloads.value + DownloadEntry(
                    id = -System.nanoTime(),
                    fileName = title?.take(60)?.ifBlank { null } ?: url.substringAfterLast('/').take(60),
                    url = url,
                    mimeType = mimeType,
                    state = DownloadState.FAILED,
                    reason = "Link is a webpage, not a media file",
                )
                showMessage("Could not start that download")
                return@launch
            }

            val mime = resolveMime(url, mimeType, probe?.contentType)
            // Never refuse on UNKNOWN. The system download manager does not need a correct
            // MIME type to fetch a file — it only uses it to label the entry.
            // Refusing here is what produced "could not discern file type" on
            // perfectly good links, so an unknown type now falls through to a
            // generic one and the download is attempted regardless.
            val effectiveMime = mime ?: "application/octet-stream"
            val nameFromServer = probe?.fileName
            val rawName = title?.takeIf { it.isNotBlank() }
                ?: nameFromServer?.takeIf { it.isNotBlank() }
                ?: fileNameFor(url, contentDisposition)
            val fileName = sanitizeDownloadName(rawName, effectiveMime)

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                addRequestHeader("User-Agent", userAgent ?: probe?.userAgent ?: "Mozilla/5.0")
                addRequestHeader("Referer", refererFor(url))
                // Age-gated and tokenised CDN links are refused without the
                // cookies the WebView already earned by loading the page.
                cookiesFor(url)?.let { addRequestHeader("Cookie", it) }
                setMimeType(effectiveMime)
                setTitle(fileName)
                setDescription("Saving to Downloads/Ember")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Ember/$fileName")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            _downloads.value = _downloads.value.filterNot { it.id == placeholderId }
            runCatching { dm.enqueue(request) }.fold(
                onSuccess = { id ->
                    _downloads.value = _downloads.value + DownloadEntry(
                        id = id,
                        fileName = fileName,
                        url = url,
                        mimeType = effectiveMime,
                        state = DownloadState.QUEUED,
                    )
                    startDownloadPolling(context)
                    showMessage("Queued $fileName")
                },
                onFailure = {
                    _downloads.value = _downloads.value + DownloadEntry(
                        id = placeholderId,
                        fileName = fileName,
                        url = url,
                        mimeType = effectiveMime,
                        state = DownloadState.FAILED,
                        reason = it.message?.take(80) ?: "Could not start",
                    )
                    showMessage("Could not start that download")
                },
            )
        }
    }

    /**
     * Downloads an HLS playlist end to end and reports progress in the queue.
     *
     * This path exists because the system download manager cannot do the job: it
     * would store the `.m3u8` text file itself. The work is done in-process by
     * [HlsDownloader], which resolves the best rendition, fetches and decrypts
     * each segment and remuxes MPEG-TS into fragmented MP4, so the file that
     * lands in Downloads is one a player can actually open.
     */
    private fun startHlsDownload(url: String, userAgent: String?, title: String?) {
        val entryId = -System.nanoTime()
        val requestedName = title?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "stream" }
        val fileName = sanitizeDownloadName(
            if (requestedName.endsWith(".mp4", ignoreCase = true)) requestedName else "$requestedName.mp4",
            "video/mp4",
        )

        _downloads.value = _downloads.value + DownloadEntry(
            id = entryId,
            fileName = fileName,
            url = url,
            mimeType = "video/mp4",
            state = DownloadState.RUNNING,
            managedInApp = true,
        )

        viewModelScope.launch {
            val destDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Ember",
            )
            val output = File(destDir, fileName)
            val session = HlsDownloader.Session(
                userAgent = userAgent ?: DEFAULT_UA,
                cookie = cookiesFor(url),
                referer = refererFor(url).takeIf { it.isNotBlank() },
            )

            val outcome = HlsDownloader.download(
                playlistUrl = url,
                outputFile = output,
                session = session,
                onProgress = { p ->
                    _downloads.value = _downloads.value.map { entry ->
                        if (entry.id != entryId) {
                            entry
                        } else {
                            entry.copy(
                                state = DownloadState.RUNNING,
                                doneBytes = p.bytesDone,
                                totalBytes = p.bytesTotal.coerceAtLeast(0L),
                                progressLabel = if (p.segmentsTotal > 0) {
                                    "Segment ${p.segmentsDone} of ${p.segmentsTotal}"
                                } else {
                                    ""
                                },
                            )
                        }
                    }
                },
            )

            when (outcome) {
                is HlsDownloader.Outcome.Success -> {
                    _downloads.value = _downloads.value.map { entry ->
                        if (entry.id == entryId) {
                            entry.copy(
                                state = DownloadState.SUCCESS,
                                doneBytes = outcome.bytes,
                                totalBytes = outcome.bytes,
                                progressLabel = "",
                            )
                        } else {
                            entry
                        }
                    }
                    showMessage("Saved $fileName")
                }

                is HlsDownloader.Outcome.Failure -> {
                    _downloads.value = _downloads.value.map { entry ->
                        if (entry.id == entryId) {
                            entry.copy(
                                state = DownloadState.FAILED,
                                reason = outcome.reason.take(80),
                                progressLabel = "",
                            )
                        } else {
                            entry
                        }
                    }
                    showMessage("Download failed")
                }
            }
        }
    }

    /** What a HEAD/range probe told us about a link, when anything. */
    private data class Probe(
        val contentType: String = "",
        val fileName: String = "",
        val userAgent: String = "",
        /** First bytes of the body, used to tell real media from a web page. */
        val head: ByteArray = ByteArray(0),
        val statusCode: Int = 0,
    )

    /** What a link actually turned out to serve, judged from its own bytes. */
    private enum class LinkKind {
        /** A real media file. */
        MEDIA,

        /** An HTML age gate, error page or "click to continue" wall. */
        HTML_PAGE,

        /** An HLS/DASH playlist — a list of URLs, not the video itself. */
        PLAYLIST,

        /** Something else we can't play. */
        UNKNOWN,
    }

    /**
     * Decides what a link really is by looking at the first bytes it serves.
     *
     * The Content-Type lies constantly on these hosts: an age-gate or a "not
     * found" page comes back `200 OK`, and its body is HTML. Naming that file
     * `.mp4` is what produces a download no player will open — a real file on
     * disk with a video extension and HTML inside it. Sniffing the body is the
     * only way to tell, and refusing here is better than saving a broken file.
     */
    private fun classifyLink(probe: Probe?): LinkKind {
        val type = probe?.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (type.startsWith("text/html") || type.startsWith("application/xhtml")) return LinkKind.HTML_PAGE

        val head = probe?.head ?: ByteArray(0)
        if (head.isEmpty()) {
            // Headers only: believe a genuine media type, otherwise admit doubt.
            return if (type.startsWith("video/") || type.startsWith("image/") || type.startsWith("audio/")) {
                LinkKind.MEDIA
            } else {
                LinkKind.UNKNOWN
            }
        }
        val text = String(head, Charsets.ISO_8859_1).trimStart().lowercase()
        val looksHtml = text.startsWith("<!doctype html") || text.startsWith("<html") ||
            text.startsWith("<?xml") && text.contains("<html")
        if (looksHtml) return LinkKind.HTML_PAGE

        // A playlist is a text file starting with #EXTM3U or #EXT-X.
        if (text.startsWith("#extm3u") || text.startsWith("#ext-x")) return LinkKind.PLAYLIST
        // JSON manifests and other machine-readable responses are not media.
        if (text.startsWith("{") || text.startsWith("[")) return LinkKind.UNKNOWN

        // Real media carries a container signature early in the file.
        val hasContainerMagic = CONTAINER_SIGNATURES.any { magic -> head.startsWithBytes(magic) }
        if (hasContainerMagic) return LinkKind.MEDIA
        return if (type.startsWith("video/") || type.startsWith("image/") || type.startsWith("audio/")) {
            LinkKind.MEDIA
        } else {
            LinkKind.UNKNOWN
        }
    }

    private fun ByteArray.startsWithBytes(magic: ByteArray): Boolean {
        if (size < magic.size) return false
        for (i in magic.indices) if (this[i] != magic[i]) return false
        return true
    }

    /**
     * Container/file signatures, checked as raw bytes.
     *
     * Every entry is a real magic number for a format a phone can open, so a
     * matching file is playable and a non-match means Ember should say so rather
     * than save it under a video extension.
     */
    private val CONTAINER_SIGNATURES: List<ByteArray> = listOf(
        byteArrayOf(0x66, 0x74, 0x79, 0x70).let { it },                       // ftyp (MP4/MOV)
        byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()),               // EBML (Matroska/WebM)
        byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70),       // MP4 (older brands)
        byteArrayOf(0x00, 0x00, 0x00, 0x1C, 0x66, 0x74, 0x79, 0x70),       // MP4 variant
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),            // JPEG SOI
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),                       // PNG
        byteArrayOf(0x47, 0x49, 0x46, 0x38),                               // GIF
        byteArrayOf(0x52, 0x49, 0x46, 0x46),                               // RIFF (AVI/WAV)
        byteArrayOf(0x4F, 0x67, 0x67, 0x53),                               // Ogg
        byteArrayOf(0x49, 0x44, 0x33),                                     // MP3 with ID3
        byteArrayOf(0xFF.toByte(), 0xFB.toByte()),                          // MP3 frame sync
    )

    /**
     * Asks the server what a link actually serves.
     *
     * Guessing from the URL is guesswork: real media hosts use opaque paths with
     * no extension and no useful keywords. A one-byte ranged GET reads the true
     * Content-Type and Content-Disposition for a couple of hundred bytes, and
     * doubles as a check that the link is fetchable at all — so a dead link is
     * reported as dead instead of failing later inside the system downloader.
     *
     * Carries the WebView's cookies, because the page only revealed this URL to
     * us *after* passing an age gate or login.
     */
    private suspend fun probeMedia(url: String, userAgent: String?): Probe? = withContext(Dispatchers.IO) {
        runCatching {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
            val request = okhttp3.Request.Builder()
                .url(url)
                // We ask for the first 256 bytes to sniff the container magic numbers.
                .header("Range", "bytes=0-255")
                .header("User-Agent", userAgent ?: DEFAULT_UA)
                .apply {
                    refererFor(url).takeIf { it.isNotBlank() }?.let { header("Referer", it) }
                    cookiesFor(url)?.let { header("Cookie", it) }
                }
                .build()
            client.newCall(request).execute().use { res ->
                val source = res.body?.source()
                source?.request(256)
                val readSize = kotlin.math.min(256L, source?.buffer?.size ?: 0L)
                val headBytes = source?.buffer?.readByteArray(readSize) ?: ByteArray(0)

                Probe(
                    contentType = res.header("Content-Type").orEmpty(),
                    fileName = fileNameFromDisposition(res.header("Content-Disposition")),
                    userAgent = userAgent ?: DEFAULT_UA,
                    head = headBytes,
                    statusCode = res.code,
                )
            }
        }.getOrNull()
    }

    /** Cookies the WebView holds for this URL, which is what unlocked it. */
    private fun cookiesFor(url: String): String? = runCatching {
        android.webkit.CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun fileNameFromDisposition(header: String?): String {
        if (header.isNullOrBlank()) return ""
        // filename*=UTF-8''name takes precedence over a plain filename=, and it
        // is the one that carries real characters.
        Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE).find(header)
            ?.let { return urlDecode(it.groupValues[1].trim().trim('"')) }
        Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE).find(header)
            ?.let { return urlDecode(it.groupValues[1].trim()) }
        return ""
    }

    /**
     * Best available MIME type. Preference order matters: what the page told us
     * beats what the server serves beats what the filename implies.
     */
    private fun resolveMime(url: String, declared: String, served: String? = null): String? {
        declared.takeIf { it.isNotBlank() && !it.isIgnorableType() }?.let { return it }
        // A real media Content-Type from the server is the most reliable answer
        // — but an error page or interstitial also answers 200, with text/html.
        // Saving that as the "video" would produce a file that looks successful
        // and isn't, so HTML and unknown blobs are treated as no information.
        served?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() && !it.isIgnorableType() }
            ?.let { return it }
        extensionOf(url)?.let { ext ->
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        }
        val lower = url.lowercase()
        return when {
            lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains("/thumb") -> "image/jpeg"
            lower.contains(".png") -> "image/png"
            lower.contains(".webp") -> "image/webp"
            lower.contains(".mp4") || lower.contains("/video") || lower.contains("stream") -> "video/mp4"
            lower.contains(".webm") -> "video/webm"
            lower.contains(".mp3") || lower.contains("/audio") -> "audio/mpeg"
            else -> null
        }
    }

    /**
     * Types that tell us nothing useful about the media.
     *
     * `application/octet-stream` is a server saying "I don't know", and
     * `text/html` is almost always an error or age-gate interstitial served with
     * a 200 — never the file itself. Both are skipped so the search continues.
     */
    private fun String.isIgnorableType(): Boolean {
        val value = substringBefore(';').trim().lowercase()
        return value.isEmpty() ||
            value == "application/octet-stream" ||
            value.startsWith("text/html") ||
            value.startsWith("application/xhtml") ||
            value == "text/plain"
    }

    /** The origin that served the media, which is what its CDN expects as Referer. */
    private fun refererFor(url: String): String = runCatching {
        val uri = Uri.parse(url)
        val scheme = uri.scheme
        val authority = uri.authority
        if (scheme.isNullOrBlank() || authority.isNullOrBlank()) "" else "$scheme://$authority/"
    }.getOrDefault("")

    /** File extension from the path only — query strings hide the real name. */
    private fun extensionOf(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/')
        if (!last.contains('.')) return null
        val ext = last.substringAfterLast('.').lowercase()
        return ext.takeIf { it.isNotBlank() && it.length <= 5 && it.all { c -> c.isLetterOrDigit() } }
    }

    /** Keeps a usable filename on disk and a truthful one in the UI. */
    private fun sanitizeDownloadName(raw: String, mime: String): String {
        val cleaned = raw.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_").trim().trim('.')
        val withoutExt = cleaned.substringBeforeLast('.', cleaned).take(80).trim()
        val ext = extensionOf("x.${
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        }") ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        val stem = withoutExt.ifBlank { "ember-${System.currentTimeMillis()}" }
        return "$stem.$ext"
    }

    private fun fileNameFor(url: String, contentDisposition: String?): String {
        contentDisposition?.let { cd ->
            Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
                .find(cd)?.groupValues?.get(1)?.let { return urlDecode(it) }
        }
        val last = Uri.parse(url).lastPathSegment?.substringAfterLast('/')?.ifBlank { null }
        return last ?: "ember-${System.currentTimeMillis()}.bin"
    }

    private fun urlDecode(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    // ---- library ---------------------------------------------------------

    val media: StateFlow<List<MediaItem>> = container.mediaDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mediaCount: StateFlow<Int> = container.mediaDao.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val libraryQuery = MutableStateFlow("")
    val libraryKind = MutableStateFlow("all")
    val librarySort = MutableStateFlow(MediaSort.DATE_DESC)
    val libraryFavoritesOnly = MutableStateFlow(false)
    val libraryViewAsGrid = MutableStateFlow(true)
    val importBusy = MutableStateFlow(false)
    val lastImportSummary = MutableStateFlow<String?>(null)

    val filteredMedia: StateFlow<List<MediaItem>> =
        kotlinx.coroutines.flow.combine(
            media,
            libraryQuery,
            libraryKind,
            librarySort,
            libraryFavoritesOnly,
        ) { items, q, kind, sort, favOnly ->
            val filtered = items.filter { item ->
                val favOk = (!favOnly && kind != "favorites") || item.favorite
                val kindOk = kind == "all" || kind == "favorites" || item.kind == kind
                val query = q.trim().lowercase()
                val queryOk = query.isEmpty() ||
                    item.title.lowercase().contains(query) ||
                    item.tags.lowercase().contains(query) ||
                    item.notes.lowercase().contains(query) ||
                    item.collection.lowercase().contains(query)
                favOk && kindOk && queryOk
            }
            when (sort) {
                MediaSort.DATE_DESC -> filtered.sortedByDescending { it.addedAt }
                MediaSort.DATE_ASC -> filtered.sortedBy { it.addedAt }
                MediaSort.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
                MediaSort.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setLibrarySort(sort: MediaSort) { librarySort.value = sort }
    fun setLibraryFavoritesOnly(only: Boolean) { libraryFavoritesOnly.value = only }
    fun toggleLibraryFavoritesOnly() { libraryFavoritesOnly.value = !libraryFavoritesOnly.value }
    fun toggleLibraryView() { libraryViewAsGrid.value = !libraryViewAsGrid.value }

    fun importUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        importBusy.value = true
        viewModelScope.launch {
            val added = mutableListOf<String>()
            val addedItems = mutableListOf<MediaItem>()
            var duplicates = 0
            var failed = 0
            for (uri in uris) {
                val result = withContext(Dispatchers.IO) { buildMediaItem(context, uri) }
                when (result) {
                    is ImportOutcome.Duplicate -> duplicates++
                    is ImportOutcome.Failed -> failed++
                    is ImportOutcome.Ready -> {
                        runCatching { container.mediaDao.insert(result.item) }
                            .onSuccess {
                                added += result.item.title
                                addedItems += result.item
                            }
                            .onFailure { failed++ }
                    }
                }
            }
            importBusy.value = false
            // A re-import of something a page scan downloaded should keep the
            // title/duration/resolution the page published rather than reverting
            // to whatever the file itself reported.
            applyPendingMetadata(addedItems)
            lastImportSummary.value = buildString {
                if (added.isNotEmpty()) append("Added ${added.size}")
                if (duplicates > 0) append(if (isNotEmpty()) ", " else "").append("$duplicates already in library")
                if (failed > 0) append(if (isNotEmpty()) ", " else "").append("$failed failed")
                if (isEmpty()) append("Nothing to import")
            }
        }
    }

    private sealed interface ImportOutcome {
        data class Ready(val item: MediaItem) : ImportOutcome
        data class Duplicate(val existing: MediaItem) : ImportOutcome
        data class Failed(val reason: String) : ImportOutcome
    }

    private suspend fun buildMediaItem(context: Context, uri: Uri): ImportOutcome {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
            ?: uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()?.let {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
            }
            ?: "application/octet-stream"

        val hash = runCatching {
            resolver.openInputStream(uri)?.let { AppContainer.sha256(it) }
        }.getOrNull().orEmpty()

        if (hash.isNotBlank()) {
            val existing = container.mediaDao.findByHash(hash)
            if (existing != null) return ImportOutcome.Duplicate(existing)
        }

        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported item"
        var size = 0L
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }

        val kind = AppContainer.kindForMime(mime)
        var duration = 0L
        var width = 0
        var height = 0
        if (kind == MediaItem.KIND_VIDEO || kind == MediaItem.KIND_AUDIO) {
            // MediaMetadataRetriever only became AutoCloseable in API 29, so it is
            // released explicitly rather than via `use` — minSdk here is 24.
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.let { duration = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()?.let { width = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()?.let { height = it }
            } catch (t: Throwable) {
                // Metadata is a nice-to-have; an unprobeable file still imports.
            } finally {
                runCatching { retriever.release() }
            }
        }

        if (kind == MediaItem.KIND_VIDEO && hash.isNotBlank()) {
            // Best-effort: a file the retriever cannot open still imports fine,
            // it just shows a kind icon instead of a frame.
            Thumbs.generateVideoThumb(context, uri, hash)
        }

        return ImportOutcome.Ready(
            MediaItem(
                title = displayName.substringBeforeLast('.').ifBlank { displayName },
                uri = uri.toString(),
                kind = kind,
                mimeType = mime,
                sizeBytes = size,
                durationMs = duration,
                width = width,
                height = height,
                sha256 = hash,
            ),
        )
    }

    /** Local cache file for a video's extracted frame, if one exists. */
    fun videoThumb(context: Context, item: MediaItem): File? {
        if (item.kind != MediaItem.KIND_VIDEO || item.sha256.isBlank()) return null
        val file = Thumbs.fileFor(context, item.sha256)
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    fun updateMedia(item: MediaItem) {
        viewModelScope.launch { container.mediaDao.update(item) }
    }

    fun toggleMediaFavorite(item: MediaItem) {
        viewModelScope.launch { container.mediaDao.update(item.copy(favorite = !item.favorite)) }
    }

    fun deleteMedia(item: MediaItem) {
        viewModelScope.launch {
            container.mediaDao.delete(item)
            showMessage("Removed from library")
        }
    }

    fun clearLibrary() {
        viewModelScope.launch {
            container.mediaDao.clear()
            showMessage("Library cleared")
        }
    }

    /**
     * Wipes every local store. Used by Settings' "Clear all Ember data", which
     * promises a full reset — so it has to actually clear scenarios, bookmarks
     * and history too, not just the library.
     */
    fun clearEverything() {
        viewModelScope.launch {
            container.mediaDao.clear()
            runCatching { container.scenarioDao.deleteAll() }
            container.bookmarkDao.clear()
            container.historyDao.clear()
            settings.resetAll()
            showMessage("All Ember data cleared")
        }
    }

    fun openExternal(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent) }
            .onFailure { showMessage("No app can open that") }
    }

    // ---- scenario lab ----------------------------------------------------

    val scenarios: StateFlow<List<Scenario>> = container.scenarioDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val scenarioCount: StateFlow<Int> = container.scenarioDao.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val lab = MutableStateFlow(
        LabState(
            dials = settings.labDials.value,
            premise = settings.labPremise.value,
            taste = settings.labTaste.value,
            seed = settings.labSeed.value,
            customBanks = settings.labCustomBanks.value,
        )
    )
    val labViewMode = MutableStateFlow(LabViewMode.READING)
    val steeringExpanded = MutableStateFlow(false)
    val scenarioQuery = MutableStateFlow("")

    fun setLabViewMode(mode: LabViewMode) { labViewMode.value = mode }
    fun toggleLabViewMode() {
        labViewMode.value = if (labViewMode.value == LabViewMode.READING) LabViewMode.TUNING else LabViewMode.READING
    }
    fun setSteeringExpanded(expanded: Boolean) { steeringExpanded.value = expanded }
    fun toggleSteeringExpanded() { steeringExpanded.value = !steeringExpanded.value }

    val filteredScenarios: StateFlow<List<Scenario>> =
        kotlinx.coroutines.flow.combine(scenarios, scenarioQuery) { items, q ->
            val query = q.trim().lowercase()
            if (query.isEmpty()) items else items.filter {
                it.title.lowercase().contains(query) ||
                    it.tags.lowercase().contains(query) ||
                    it.body.lowercase().contains(query)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Deterministic RNG. A premise seeds it so the same premise reproduces the
     * same brief — which is what makes the premise usable as a working seed
     * rather than a label, and makes the dials comparable between runs.
     */
    private fun labRng(): Random {
        val state = lab.value
        val parsedSeed = state.seed.trim().toLongOrNull()
            ?: state.seed.trim().takeIf { it.isNotEmpty() }?.hashCode()?.toLong()
        val seed = parsedSeed ?: state.premise.trim().takeIf { it.isNotEmpty() }?.let {
            it.hashCode().toLong() * 31L + it.length
        } ?: System.nanoTime()
        return Random(seed)
    }

    fun generate(kind: GenerateKind = GenerateKind.BRIEF) {
        val state = lab.value
        val rng = labRng()
        val generated = when (kind) {
            GenerateKind.BRIEF -> Generator.brief(
                rng = rng,
                custom = state.customBanks,
                dials = state.dials,
                premise = state.premise.trim(),
                taste = state.taste,
            )
            GenerateKind.CHARACTER -> Generator.character(rng, state.customBanks, state.taste)
            GenerateKind.BEAT -> Generator.complication(rng, state.customBanks, state.taste)
        }
        val nextSeed = rng.nextLong().toString()
        lab.value = state.copy(brief = generated, seed = nextSeed)
        settings.setLabSeed(nextSeed)
    }

    // ---- steering --------------------------------------------------------

    fun setDial(explicitness: Int? = null, pace: Int? = null, power: Int? = null, pov: Int? = null) {
        val dials = lab.value.dials
        val updated = Dials(
            explicitness = explicitness ?: dials.explicitness,
            pace = pace ?: dials.pace,
            power = power ?: dials.power,
            pov = pov ?: dials.pov,
        )
        lab.value = lab.value.copy(dials = updated)
        settings.setLabDials(updated)
    }

    fun setPremise(text: String) {
        lab.value = lab.value.copy(premise = text)
        settings.setLabPremise(text)
    }

    /**
     * Typing anything pins the seed so a brief can be reproduced exactly.
     */
    fun setSeed(text: String) {
        val trimmed = text.trim()
        lab.value = lab.value.copy(seed = trimmed)
        settings.setLabSeed(trimmed)
    }

    fun newSeed() {
        lab.value = lab.value.copy(seed = "")
        settings.setLabSeed("")
    }

    fun lockPart(key: String) = mutatePart(key) { it.copy(locked = !it.locked) }

    fun lockSlot(key: String) {
        val state = lab.value
        val brief = state.brief ?: return
        val slot = brief.slot(key) ?: return
        val next = !slot.locked
        lab.value = state.copy(
            brief = brief.withSlot(
                slot.copy(
                    locked = next,
                    parts = slot.parts.map { part ->
                        if (next) part.copy(locked = true) else part.copy(locked = false)
                    },
                ),
            ),
        )
    }

    /**
     * Rerolls every unlocked part in a slot. Locked parts are left alone, which is
     * what makes "keep the cast, redo the setting" a single action.
     */
    fun rerollSlot(key: String) {
        val state = lab.value
        val brief = state.brief ?: return
        val slot = brief.slot(key) ?: return
        if (slot.locked) {
            showMessage("Slot is locked")
            return
        }
        val rng = Random(System.nanoTime())
        var updated = brief
        for (part in slot.parts) {
            if (part.locked || part.hidden && part.bank == null) continue
            val bank = part.bank ?: continue
            val fresh = regenerate(brief, part, bank, rng)
            updated = updated.withPart(part.key) { it.copy(value = fresh) }
        }
        if (key == "setting" || key == "meta") {
            updated = updated.copy(title = Generator.rebuildTitle(updated, rng))
        }
        lab.value = state.copy(brief = updated)
    }

    /** Rerolls one part only, leaving the rest of the brief alone. */
    fun rerollPart(key: String) {
        val state = lab.value
        val brief = state.brief ?: return
        val part = brief.allParts().firstOrNull { it.key == key } ?: return
        if (part.locked) {
            showMessage("Unlocked first")
            return
        }
        val bank = part.bank ?: run {
            showMessage("This part can't be rerolled")
            return
        }
        val rng = Random(System.nanoTime())
        val fresh = regenerate(brief, part, bank, rng)
        var updated = brief.withPart(key) { it.copy(value = fresh, locked = false) }
        // The title is derived from place/time/role, so it has to follow them.
        if (key == "place" || key == "time" || key == "titleNoun" || key == "titlePlural") {
            updated = updated.copy(title = Generator.rebuildTitle(updated, rng))
        }
        lab.value = state.copy(brief = updated)
    }

    /** Re-draws a part from its bank, honouring pins, blocks and the premise. */
    private fun regenerate(brief: Brief, part: Part, bank: String, rng: Random): String {
        val state = lab.value
        val custom = state.customBanks
        // Names are composed, not drawn from a list, so they need their own path.
        if (bank == "nameStyles") return Generator.nameFor(rng, custom, state.taste)
        val defaults = Banks.defaultsFor(bank)
        Banks.forcedBy(state.premise, custom.forBank(bank, defaults))?.let { return it }
        val pool = custom.forBank(bank, defaults)
        val applied = state.taste.apply(bank, pool)
        val usable = applied.ifEmpty { state.taste.apply(bank, defaults).ifEmpty { defaults } }
        return usable.random(rng)
    }

    /** Pins a value so rerolls keep converging on it. */
    fun pinPart(key: String) {
        val state = lab.value
        val part = state.brief?.allParts()?.firstOrNull { it.key == key } ?: return
        val bank = part.bank ?: run {
            showMessage("This part can't be pinned")
            return
        }
        val pinned = state.taste.pinned + (bank to part.value)
        val blocked = state.taste.blocked - part.value
        val updatedTaste = state.taste.copy(pinned = pinned, blocked = blocked)
        lab.value = state.copy(taste = updatedTaste)
        settings.setLabTaste(updatedTaste)
        showMessage("Pinned")
    }

    /** Blocks a value everywhere, so it stops coming back. */
    fun blockPart(key: String) {
        val state = lab.value
        val part = state.brief?.allParts()?.firstOrNull { it.key == key } ?: return
        val bank = part.bank
        val updatedTaste = state.taste.copy(
            pinned = if (bank == null) state.taste.pinned else state.taste.pinned - bank,
            blocked = state.taste.blocked + part.value,
        )
        lab.value = state.copy(taste = updatedTaste)
        settings.setLabTaste(updatedTaste)
        showMessage("Blocked")
    }

    fun unpinBank(bank: String) {
        val state = lab.value
        val updatedTaste = state.taste.copy(pinned = state.taste.pinned - bank)
        lab.value = state.copy(taste = updatedTaste)
        settings.setLabTaste(updatedTaste)
    }

    fun clearTaste() {
        val emptyTaste = Banks.Taste()
        lab.value = lab.value.copy(taste = emptyTaste)
        settings.setLabTaste(emptyTaste)
    }

    /** Replaces one part with AI-written text, leaving the rest of the brief intact. */
    fun applyAiToPart(key: String) {
        val state = lab.value
        val brief = state.brief ?: return
        val part = brief.allParts().firstOrNull { it.key == key } ?: return
        val output = state.aiOutput.trim()
        if (output.isEmpty()) {
            showMessage("Generate something first")
            return
        }
        val slot = brief.slots.firstOrNull { slot -> slot.parts.any { it.key == key } }
        if (slot != null && slot.locked) {
            showMessage("Slot is locked")
            return
        }
        lab.value = state.copy(
            brief = brief.withPart(key) { it.copy(value = output, locked = true) },
            aiOutput = "",
        )
    }

    private fun mutatePart(key: String, transform: (Part) -> Part) {
        val state = lab.value
        val brief = state.brief ?: return
        lab.value = state.copy(brief = brief.withPart(key, transform))
    }

    data class PartVariationsState(
        val partKey: String,
        val partLabel: String,
        val currentValue: String,
        val options: List<String> = emptyList(),
        val busy: Boolean = true,
        val error: String? = null,
    )

    val smartRerollBusyKey = MutableStateFlow<String?>(null)
    val partVariations = MutableStateFlow<PartVariationsState?>(null)

    /** Directly updates the title of the scenario brief. */
    fun updateBriefTitle(newTitle: String) {
        val state = lab.value
        val brief = state.brief ?: return
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        lab.value = state.copy(brief = brief.withTitle(trimmed))
    }

    /** Directly updates the label and value of a part. */
    fun updatePart(key: String, newLabel: String = "", newValue: String) {
        val state = lab.value
        val brief = state.brief ?: return
        val cleanedValue = newValue.trim()
        if (cleanedValue.isEmpty()) {
            showMessage("Value cannot be blank")
            return
        }
        val trimmedLabel = newLabel.trim()
        val updated = brief.withPart(key) {
            val labelToUse = if (trimmedLabel.isNotEmpty()) trimmedLabel else it.label
            it.copy(label = labelToUse, value = cleanedValue)
        }
        lab.value = state.copy(brief = updated)
    }

    /** Adds a custom line/detail to a slot. */
    fun addPartToSlot(slotKey: String, label: String, value: String) {
        val state = lab.value
        val brief = state.brief ?: return
        val slot = brief.slot(slotKey) ?: return
        val trimmedVal = value.trim()
        if (trimmedVal.isEmpty()) {
            showMessage("Detail cannot be blank")
            return
        }
        val newKey = "${slotKey}_custom_${System.currentTimeMillis()}"
        val newPart = Part(
            key = newKey,
            label = label.trim(),
            value = trimmedVal,
            bank = null,
            locked = false,
        )
        lab.value = state.copy(brief = brief.withPartAdded(slotKey, newPart))
        showMessage("Added detail to ${slot.heading}")
    }

    /** Removes a part from its slot. */
    fun removePart(key: String) {
        val state = lab.value
        val brief = state.brief ?: return
        val part = brief.allParts().firstOrNull { it.key == key } ?: return
        lab.value = state.copy(brief = brief.withPartRemoved(key))
        showMessage("Removed ${part.label.ifBlank { "detail" }}")
    }

    /**
     * Smart, context-aware reroll for a single part.
     * When AI is enabled, sends the full scenario context (title, premise, setting,
     * cast, slot) so the new value is narrative-consistent and logical.
     * Falls back to offline bank if AI is off or fails.
     */
    fun smartRerollPart(key: String, customInstruction: String? = null) {
        val state = lab.value
        val brief = state.brief ?: return
        val part = brief.allParts().firstOrNull { it.key == key } ?: return
        if (part.locked) {
            showMessage("Unlock this line first")
            return
        }
        val slot = brief.slots.firstOrNull { slot -> slot.parts.any { it.key == key } } ?: return
        if (slot.locked) {
            showMessage("Section is locked")
            return
        }

        // If AI is NOT configured or user has no key, use combinatorial bank reroll
        if (!settings.aiEnabled.value || !settings.aiHasKey.value) {
            if (part.bank != null) {
                rerollPart(key)
            } else {
                showMessage("Turn on AI assist in Settings to smart-reroll custom fields.")
            }
            return
        }

        smartRerollBusyKey.value = key
        viewModelScope.launch {
            try {
                val otherDetails = slot.parts
                    .filter { it.key != key && !it.hidden && it.value.isNotBlank() }
                    .take(6)
                    .joinToString("\n") { "  · ${if (it.label.isNotBlank()) "${it.label}: " else ""}${it.value}" }

                val prompt = buildString {
                    appendLine("You are an expert scenario and character writer for an interactive fiction app.")
                    appendLine("Scenario context:")
                    appendLine("- Title: ${brief.title}")
                    if (brief.premise.isNotBlank()) appendLine("- Premise: ${brief.premise}")
                    appendLine("- Section: ${slot.heading}")
                    if (otherDetails.isNotBlank()) {
                        appendLine("- Existing details in this section:")
                        appendLine(otherDetails)
                    }
                    appendLine()
                    val fieldName = part.label.ifBlank { part.key }
                    appendLine("Current value of \"$fieldName\": \"${part.value}\"")
                    if (!customInstruction.isNullOrBlank()) {
                        appendLine("Instruction: $customInstruction")
                    } else {
                        appendLine("Instruction: Propose a fresh, coherent, and compelling alternative that logically fits with the rest of the scenario.")
                    }
                    appendLine()
                    appendLine("Requirements:")
                    appendLine("- Output ONLY the replacement text for this field.")
                    appendLine("- Do NOT include quotes, labels, bullet points, markdown formatting, or chatty commentary.")
                    appendLine("- Keep it concise (1 to 2 sentences or phrases), matching the style of the other fields.")
                }

                val result = container.aiClient.complete(
                    userPrompt = prompt,
                    context = brief.text,
                    maxTokens = 300,
                    temperature = 0.75,
                )

                when (result) {
                    is AiResult.Ok -> {
                        val rawText = result.text.trim().trim('"', '\'', '`')
                        val cleaned = BriefMarkdownParser.cleanValue(rawText, part.label)
                        if (cleaned.isNotBlank()) {
                            val updatedBrief = brief.withPart(key) { it.copy(value = cleaned, locked = false) }
                            lab.value = lab.value.copy(brief = updatedBrief)
                            showMessage("Updated ${part.label.ifBlank { "field" }} with AI")
                        } else {
                            if (part.bank != null) rerollPart(key)
                        }
                    }
                    is AiResult.Failure -> {
                        if (part.bank != null) {
                            rerollPart(key)
                            showMessage("AI unavailable, rolled from bank: ${result.message}")
                        } else {
                            showMessage("AI error: ${result.message}")
                        }
                    }
                }
            } catch (t: Throwable) {
                if (part.bank != null) rerollPart(key)
                else showMessage("Error: ${t.message}")
            } finally {
                smartRerollBusyKey.value = null
            }
        }
    }

    /**
     * Smart reroll for an entire slot. If AI is on, generates structured replacement
     * preserving locked parts. If offline, rerolls unlocked parts from banks.
     */
    fun smartRerollSlot(slotKey: String) {
        val state = lab.value
        val brief = state.brief ?: return
        val slot = brief.slot(slotKey) ?: return
        if (slot.locked) {
            showMessage("Section is locked")
            return
        }
        if (settings.aiEnabled.value && settings.aiHasKey.value) {
            generateAiSlot(slotKey, "Rewrite", "Rewrite the unlocked fields with fresh, creative, logically consistent ideas that fit this scenario.")
        } else {
            rerollSlot(slotKey)
        }
    }

    /**
     * Requests 3 contextual variations for a part and displays them for the user to choose.
     */
    fun requestPartVariations(key: String, instruction: String? = null) {
        val state = lab.value
        val brief = state.brief ?: return
        val part = brief.allParts().firstOrNull { it.key == key } ?: return
        val slot = brief.slots.firstOrNull { s -> s.parts.any { it.key == key } } ?: return
        val fieldName = part.label.ifBlank { part.key }

        partVariations.value = PartVariationsState(
            partKey = key,
            partLabel = fieldName,
            currentValue = part.value,
            busy = true,
        )

        viewModelScope.launch {
            try {
                val prompt = buildString {
                    appendLine("You are an expert scenario and character writer for an interactive fiction app.")
                    appendLine("Scenario: \"${brief.title}\"")
                    if (brief.premise.isNotBlank()) appendLine("Premise: ${brief.premise}")
                    appendLine("Section: ${slot.heading}")
                    appendLine("Current value of \"$fieldName\": \"${part.value}\"")
                    if (!instruction.isNullOrBlank()) {
                        appendLine("Instruction: $instruction")
                    }
                    appendLine()
                    appendLine("Task: Provide 3 distinct, creative, and logical alternative values for \"$fieldName\" that fit this story.")
                    appendLine("Format strictly as 3 numbered lines:")
                    appendLine("1. [First alternative]")
                    appendLine("2. [Second alternative]")
                    appendLine("3. [Third alternative]")
                    appendLine("Do NOT include any preamble or extra text.")
                }

                val result = container.aiClient.complete(
                    userPrompt = prompt,
                    context = brief.text,
                    maxTokens = 400,
                    temperature = 0.8,
                )

                when (result) {
                    is AiResult.Ok -> {
                        val lines = result.text.lines()
                            .map { it.trim() }
                            .filter { it.matches(Regex("""^\d+[\.\)]\s*.+""")) }
                            .map { it.replace(Regex("""^\d+[\.\)]\s*"""), "").trim().trim('"', '\'') }
                            .filter { it.isNotBlank() }
                        if (lines.isNotEmpty()) {
                            partVariations.value = partVariations.value?.copy(
                                options = lines,
                                busy = false,
                            )
                        } else {
                            partVariations.value = partVariations.value?.copy(
                                busy = false,
                                error = "Could not parse options from model.",
                            )
                        }
                    }
                    is AiResult.Failure -> {
                        partVariations.value = partVariations.value?.copy(
                            busy = false,
                            error = result.message,
                        )
                    }
                }
            } catch (t: Throwable) {
                partVariations.value = partVariations.value?.copy(
                    busy = false,
                    error = t.message,
                )
            }
        }
    }

    fun selectPartVariation(key: String, selectedValue: String) {
        updatePart(key, newValue = selectedValue)
        partVariations.value = null
        showMessage("Selected variation applied")
    }

    fun dismissPartVariations() {
        partVariations.value = null
    }

    suspend fun refineTextWithAi(currentText: String, instruction: String, fieldName: String): String {
        val brief = lab.value.brief
        val prompt = buildString {
            appendLine("You are an expert scenario writer.")
            if (brief != null) {
                appendLine("Scenario context: \"${brief.title}\"")
                if (brief.premise.isNotBlank()) appendLine("Premise: ${brief.premise}")
            }
            appendLine("Field: $fieldName")
            appendLine("Current text: \"$currentText\"")
            appendLine("Refinement instruction: $instruction")
            appendLine()
            appendLine("Return ONLY the refined text. No quotes, no markdown, no explanations.")
        }
        val result = container.aiClient.complete(
            userPrompt = prompt,
            context = brief?.text ?: "",
            maxTokens = 350,
            temperature = 0.7,
        )
        return when (result) {
            is AiResult.Ok -> BriefMarkdownParser.cleanValue(result.text.trim().trim('"', '\''), fieldName)
            is AiResult.Failure -> currentText
        }
    }

    fun clearBrief() {
        lab.value = lab.value.copy(brief = null, aiOutput = "", aiError = "", aiHistory = emptyList())
    }

    fun saveBrief(title: String, tags: String) {
        val brief = lab.value.brief ?: return
        viewModelScope.launch {
            container.scenarioDao.insert(
                Scenario(
                    title = title.ifBlank { brief.title },
                    body = brief.text,
                    tags = tags,
                    source = Scenario.SOURCE_GENERATOR,
                ),
            )
            showMessage("Saved to Lab")
        }
    }

    fun updateScenario(scenario: Scenario) {
        viewModelScope.launch {
            container.scenarioDao.upsert(scenario.copy(updatedAt = System.currentTimeMillis()))
            showMessage("Scenario updated")
        }
    }

    fun toggleScenarioFavorite(scenario: Scenario) {
        viewModelScope.launch {
            container.scenarioDao.upsert(scenario.copy(favorite = !scenario.favorite))
        }
    }

    fun deleteScenario(scenario: Scenario) {
        viewModelScope.launch {
            container.scenarioDao.delete(scenario)
            showMessage("Scenario deleted")
        }
    }

    fun setAiPrompt(value: String) { lab.value = lab.value.copy(aiPrompt = value) }

    fun clearAiHistory() {
        lab.value = lab.value.copy(aiHistory = emptyList())
        showMessage("Cleared the AI conversation")
    }

    fun addBankEntry(bankId: String, entry: String) {
        val value = entry.trim()
        if (value.isEmpty()) return
        val current = lab.value.customBanks
        val updated = current.copy(
            byBank = current.byBank + (bankId to (current.byBank[bankId].orEmpty() + value)),
        )
        lab.value = lab.value.copy(customBanks = updated)
        settings.setLabCustomBanks(updated)
        showMessage("Added to bank")
    }

    fun removeBankEntry(bankId: String, entry: String) {
        val current = lab.value.customBanks
        val remaining = current.byBank[bankId].orEmpty().filter { it != entry }
        val byBank = if (remaining.isEmpty()) current.byBank - bankId else current.byBank + (bankId to remaining)
        val updated = current.copy(byBank = byBank)
        lab.value = lab.value.copy(customBanks = updated)
        settings.setLabCustomBanks(updated)
    }

    fun clearBank(bankId: String) {
        val current = lab.value.customBanks
        val updated = current.copy(byBank = current.byBank - bankId)
        lab.value = lab.value.copy(customBanks = updated)
        settings.setLabCustomBanks(updated)
    }

    /** Shares a plain-text artefact via the system sheet. */
    // ---- character cards -------------------------------------------------

    /** Bytes waiting to be written by the system file picker. */
    private var pendingCard: Pair<String, ByteArray>? = null

    var cardSaveLauncher: ((String, String) -> Unit)? = null
    var cardOpenLauncher: ((Array<String>) -> Unit)? = null

    val cardExtras = MutableStateFlow(CardExtras())
    val cardEnrichState = MutableStateFlow<CharacterCardEnrichState>(CharacterCardEnrichState.Idle)

    /** Non-derived card fields, editable in the export sheet. */
    data class CardExtras(
        val firstMessage: String? = null,
        val exampleDialogue: String = "",
        val systemPrompt: String = "",
        val postHistoryInstructions: String = "",
        val alternateGreetings: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        val platformId: String = CardPlatforms.ALL.first().id,
        val spec: CharacterCard.Spec = CharacterCard.Spec.V2,
        val useCustom: Boolean = false,
        val custom: CardPlatforms.Custom = CardPlatforms.Custom(),
    )

    fun setCardPlatform(id: String) {
        val platform = CardPlatforms.byId(id)
        cardExtras.value = cardExtras.value.copy(
            platformId = id,
            spec = platform.spec,
        )
    }

    fun setCardSpec(spec: CharacterCard.Spec) {
        cardExtras.value = cardExtras.value.copy(spec = spec)
    }

    fun setUseCustomMapping(value: Boolean) {
        cardExtras.value = cardExtras.value.copy(useCustom = value)
    }

    fun setCustomSpec(spec: CharacterCard.Spec) {
        cardExtras.value = cardExtras.value.copy(custom = cardExtras.value.custom.copy(spec = spec))
    }

    fun setCustomFormat(format: CardFormat) {
        cardExtras.value = cardExtras.value.copy(custom = cardExtras.value.custom.copy(format = format))
    }

    fun setCustomRow(index: Int, target: String, source: String) {
        val current = cardExtras.value.custom
        val rows = current.rows.toMutableList()
        if (index !in rows.indices) return
        rows[index] = CardPlatforms.CustomRow(target, source)
        cardExtras.value = cardExtras.value.copy(custom = current.copy(rows = rows))
    }

    fun addCustomRow() {
        val current = cardExtras.value.custom
        cardExtras.value = cardExtras.value.copy(
            custom = current.copy(rows = current.rows + CardPlatforms.CustomRow("", CardFields.NAME)),
        )
    }

    fun removeCustomRow(index: Int) {
        val current = cardExtras.value.custom
        if (index !in current.rows.indices) return
        cardExtras.value = cardExtras.value.copy(
            custom = current.copy(rows = current.rows.filterIndexed { i, _ -> i != index }),
        )
    }

    fun setCardFirstMessage(value: String) {
        cardExtras.value = cardExtras.value.copy(firstMessage = value)
    }

    fun setCardExampleDialogue(value: String) {
        cardExtras.value = cardExtras.value.copy(exampleDialogue = value)
    }

    fun setCardSystemPrompt(value: String) {
        cardExtras.value = cardExtras.value.copy(systemPrompt = value)
    }

    fun setCardPostHistory(value: String) {
        cardExtras.value = cardExtras.value.copy(postHistoryInstructions = value)
    }

    fun setCardAlternateGreetings(value: String) {
        cardExtras.value = cardExtras.value.copy(
            alternateGreetings = value.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    fun setCardTags(value: String) {
        cardExtras.value = cardExtras.value.copy(
            tags = value.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    fun clearCardEnrichState() {
        cardEnrichState.value = CharacterCardEnrichState.Idle
    }

    /** The card for the current brief, with the chosen platform's mapping applied. */
    fun currentCard(): CharacterCard.Card {
        val brief = lab.value.brief ?: return CharacterCard.Card()
        val extras = cardExtras.value
        return CharacterCard.fromBrief(
            brief = brief,
            tags = extras.tags,
            firstMessage = extras.firstMessage,
            exampleDialogue = extras.exampleDialogue,
            systemPrompt = extras.systemPrompt,
            postHistoryInstructions = extras.postHistoryInstructions,
            alternateGreetings = extras.alternateGreetings,
        )
    }

    fun enrichCharacterCardGreeting() {
        val brief = lab.value.brief ?: run {
            showMessage("Generate or import a brief first")
            return
        }
        if (!settings.aiEnabled.value) {
            showMessage("Turn on AI assist in Settings first")
            return
        }
        if (!settings.aiHasKey.value) {
            showMessage("Add an API key in Settings first")
            return
        }
        val card = currentCard()
        val prompt = CharacterCardPrompts.buildFirstMessagePrompt(brief, card)
        cardEnrichState.value = CharacterCardEnrichState.Busy("greeting")
        viewModelScope.launch {
            when (val result = container.aiClient.complete(prompt, maxTokens = 600, temperature = 0.75)) {
                is AiResult.Ok -> {
                    val cleaned = CharacterCardPrompts.cleanFirstMessage(result.text)
                    setCardFirstMessage(cleaned)
                    cardEnrichState.value = CharacterCardEnrichState.Success("Greeting generated")
                    showMessage("First message generated")
                }
                is AiResult.Failure -> {
                    cardEnrichState.value = CharacterCardEnrichState.Error(result.message)
                    showMessage("AI error: ${result.message}")
                }
            }
        }
    }

    fun enrichCharacterCardExamples() {
        val brief = lab.value.brief ?: run {
            showMessage("Generate or import a brief first")
            return
        }
        if (!settings.aiEnabled.value) {
            showMessage("Turn on AI assist in Settings first")
            return
        }
        if (!settings.aiHasKey.value) {
            showMessage("Add an API key in Settings first")
            return
        }
        val card = currentCard()
        val prompt = CharacterCardPrompts.buildMesExamplePrompt(brief, card)
        cardEnrichState.value = CharacterCardEnrichState.Busy("examples")
        viewModelScope.launch {
            when (val result = container.aiClient.complete(prompt, maxTokens = 1000, temperature = 0.7)) {
                is AiResult.Ok -> {
                    val cleaned = CharacterCardPrompts.cleanMesExample(result.text)
                    setCardExampleDialogue(cleaned)
                    cardEnrichState.value = CharacterCardEnrichState.Success("Dialogue examples generated")
                    showMessage("Dialogue examples generated")
                }
                is AiResult.Failure -> {
                    cardEnrichState.value = CharacterCardEnrichState.Error(result.message)
                    showMessage("AI error: ${result.message}")
                }
            }
        }
    }

    fun enrichAllCharacterCardFields() {
        viewModelScope.launch {
            val brief = lab.value.brief ?: return@launch
            if (!settings.aiEnabled.value || !settings.aiHasKey.value) {
                showMessage("Configure AI in Settings first")
                return@launch
            }
            cardEnrichState.value = CharacterCardEnrichState.Busy("all")
            val card = currentCard()
            val greetPrompt = CharacterCardPrompts.buildFirstMessagePrompt(brief, card)
            val greetRes = container.aiClient.complete(greetPrompt, maxTokens = 600, temperature = 0.75)
            if (greetRes is AiResult.Ok) {
                setCardFirstMessage(CharacterCardPrompts.cleanFirstMessage(greetRes.text))
            } else if (greetRes is AiResult.Failure) {
                cardEnrichState.value = CharacterCardEnrichState.Error(greetRes.message)
                return@launch
            }

            val examplePrompt = CharacterCardPrompts.buildMesExamplePrompt(brief, currentCard())
            val exampleRes = container.aiClient.complete(examplePrompt, maxTokens = 1000, temperature = 0.7)
            if (exampleRes is AiResult.Ok) {
                setCardExampleDialogue(CharacterCardPrompts.cleanMesExample(exampleRes.text))
                cardEnrichState.value = CharacterCardEnrichState.Success("Card enriched with greeting & dialogue")
                showMessage("Card successfully enriched with AI")
            } else if (exampleRes is AiResult.Failure) {
                cardEnrichState.value = CharacterCardEnrichState.Error(exampleRes.message)
            }
        }
    }

    fun enrichCharacterCard(card: CharacterCard.Card? = null): kotlinx.coroutines.flow.Flow<CharacterCardEnrichState> =
        kotlinx.coroutines.flow.flow {
            val state = lab.value
            val baseCard = card ?: currentCard()
            if (baseCard.name.isBlank()) {
                emit(CharacterCardEnrichState.Error("Card has no name. Generate or load a scenario first."))
                return@flow
            }
            if (!settings.aiEnabled.value || !settings.aiHasKey.value) {
                emit(CharacterCardEnrichState.Error("AI assist is not configured with an API key."))
                return@flow
            }

            emit(CharacterCardEnrichState.Generating("Greeting (first_mes)", 0.2f))
            val brief = state.brief
            val greetingPrompt = if (brief != null) {
                CharacterCardPrompts.buildFirstMessagePrompt(brief, baseCard)
            } else {
                """
                    Write the character's opening greeting ('first_mes') for a roleplay card.
                    Character Name: ${baseCard.name}
                    Personality: ${baseCard.personality}
                    Scenario Context: ${baseCard.scenario}
                """.trimIndent()
            }

            val greetingRes = container.aiClient.complete(greetingPrompt, maxTokens = 600, temperature = 0.75)
            val firstMes = if (greetingRes is AiResult.Ok) {
                CharacterCardPrompts.cleanFirstMessage(greetingRes.text)
            } else {
                baseCard.firstMessage
            }

            emit(CharacterCardEnrichState.Generating("Dialogue Examples (mes_example)", 0.6f))
            val dialoguePrompt = if (brief != null) {
                CharacterCardPrompts.buildMesExamplePrompt(brief, baseCard)
            } else {
                """
                    Write example dialogue turns ('mes_example') in SillyTavern <START> format for ${baseCard.name}.
                    Personality: ${baseCard.personality}
                """.trimIndent()
            }

            val dialogueRes = container.aiClient.complete(dialoguePrompt, maxTokens = 1000, temperature = 0.7)
            val mesExample = if (dialogueRes is AiResult.Ok) {
                CharacterCardPrompts.cleanMesExample(dialogueRes.text)
            } else {
                baseCard.exampleDialogue
            }

            emit(CharacterCardEnrichState.Generating("System Prompt", 0.9f))
            val sysPrompt = "Roleplay as ${baseCard.name}. Maintain the personality: ${baseCard.personality}. Never speak for {{user}}."

            val enriched = baseCard.copy(
                firstMessage = firstMes,
                exampleDialogue = mesExample,
                systemPrompt = sysPrompt,
                modificationDate = System.currentTimeMillis(),
            )

            setCardFirstMessage(firstMes)
            setCardExampleDialogue(mesExample)
            setCardSystemPrompt(sysPrompt)

            emit(CharacterCardEnrichState.Success("Card enriched", enriched))
        }

    fun cardPlatform(): CardPlatforms.Platform = CardPlatforms.byId(cardExtras.value.platformId)

    fun currentFormat(): CardFormat =
        if (cardExtras.value.useCustom) cardExtras.value.custom.format else cardPlatform().format

    /**
     * Encodes the card for the chosen platform and hands the bytes to the system
     * file picker. Copy-only platforms have no file to write, so this is a no-op
     * for them and the UI shows the copy sheet instead.
     */
    fun requestCardSave() {
        val extras = cardExtras.value
        if (extras.useCustom) {
            val format = extras.custom.format
            val spec = extras.custom.spec
            when (format) {
                CardFormat.PNG -> {
                    val name = safeFileName(currentCard().name).ifBlank { "ember-card-custom" }
                    writeCard("$name.png", CharacterCard.encodePng(currentCard(), spec))
                }
                CardFormat.JSON -> {
                    val name = safeFileName(currentCard().name).ifBlank { "ember-card-custom" }
                    val text = extras.custom.toJsonText(currentCard()).toByteArray(Charsets.UTF_8)
                    writeCard("$name.json", text)
                }
                CardFormat.CLIPBOARD -> showMessage("Copy the fields instead")
            }
            return
        }
        val platform = cardPlatform()
        if (platform.copyOnly) {
            showMessage("This platform has no card import")
            return
        }
        when (platform.format) {
            CardFormat.PNG -> {
                val name = safeFileName(currentCard().name).ifBlank { "ember-card" }
                writeCard("$name.png", CharacterCard.encodePng(currentCard(), platform.spec))
            }
            CardFormat.JSON -> {
                val name = safeFileName(currentCard().name).ifBlank { "ember-card" }
                writeCard("$name.json", CharacterCard.encodeJson(currentCard(), platform.spec))
            }
            CardFormat.CLIPBOARD -> showMessage("Copy the fields instead")
        }
    }

    private fun writeCard(fileName: String, bytes: ByteArray) {
        pendingCard = fileName to bytes
        val launcher = cardSaveLauncher
        if (launcher == null) {
            showMessage("No file picker available")
            return
        }
        launcher("application/octet-stream", fileName)
    }

    fun writePendingCard(uri: Uri) {
        val pending = pendingCard ?: return
        pendingCard = null
        runCatching {
            container.appContext.contentResolver.openOutputStream(uri)?.use { it.write(pending.second) }
                ?: error("could not open the chosen file")
        }.onSuccess {
            showMessage("Saved ${pending.first}")
        }.onFailure {
            showMessage("Save failed: ${it.message ?: "unknown error"}")
        }
    }

    fun requestCardOpen() {
        val launcher = cardOpenLauncher
        if (launcher == null) {
            showMessage("No file picker available")
            return
        }
        launcher(arrayOf("*/*"))
    }

    /**
     * Reads a card and turns it into an editable brief, so an imported character
     * is immediately steerable rather than a dead read-only dump.
     */
    fun readCardFile(uri: Uri) {
        val bytes = runCatching {
            container.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null) {
            showMessage("Could not read that file")
            return
        }
        when (val result = CharacterCard.read(bytes)) {
            is CharacterCard.ReadResult.Failure -> showMessage(result.reason)
            is CharacterCard.ReadResult.Card2 -> {
                val card = result.card
                lab.value = lab.value.copy(
                    brief = briefFromCard(card),
                    premise = card.description.take(400),
                    toast = "Imported ${card.name.ifBlank { "card" }} (${result.spec.label})",
                )
                showMessage("Imported ${card.name.ifBlank { "card" }}")
            }
        }
    }

    /**
     * Turns card fields into brief slots. The mapping is deliberately lossless in
     * the direction that matters: the card's body becomes a single editable slot
     * rather than being chopped into banks it was never generated from.
     */
    private fun briefFromCard(card: CharacterCard.Card): Brief {
        val slots = buildList {
            if (card.personality.isNotBlank()) {
                add(
                    com.ember.companion.data.BriefSlot(
                        "cardPersonality", "Personality",
                        listOf(Part("cardPersonality", "", card.personality)),
                    ),
                )
            }
            if (card.description.isNotBlank()) {
                add(
                    com.ember.companion.data.BriefSlot(
                        "cardBody", "Description",
                        listOf(Part("cardBody", "", card.description)),
                    ),
                )
            }
            if (card.scenario.isNotBlank()) {
                add(
                    com.ember.companion.data.BriefSlot(
                        "cardScenario", "Scenario",
                        listOf(Part("cardScenario", "", card.scenario)),
                    ),
                )
            }
            if (card.firstMessage.isNotBlank()) {
                add(
                    com.ember.companion.data.BriefSlot(
                        "cardGreeting", "First message",
                        listOf(Part("cardGreeting", "", card.firstMessage)),
                    ),
                )
            }
            if (card.exampleDialogue.isNotBlank()) {
                add(
                    com.ember.companion.data.BriefSlot(
                        "cardExample", "Example dialogue",
                        listOf(Part("cardExample", "", card.exampleDialogue)),
                    ),
                )
            }
        }
        return Brief(
            title = card.name.ifBlank { "Imported character" },
            slots = slots,
            premise = "",
            dials = lab.value.dials,
        )
    }

    /** Copies one card field, for platforms that have no import. */
    fun copyCardField(value: String) {
        runCatching {
            val clipboard = container.appContext
                .getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("card field", value))
        }
        showMessage("Copied")
    }

    private fun safeFileName(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(Regex("\\s+"), "-").take(48)

    fun openExport(text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val launcher = exportLauncher
        if (launcher == null) showMessage("No app available to share with")
        else launcher.invoke(Intent.createChooser(intent, "Share scenario"))
    }

    /**
     * Set by the root composable so the view model can launch choosers without
     * holding a Context.
     */
    var exportLauncher: ((Intent) -> Unit)? = null

    private val aiActions = listOf(
        "Expand this into a fuller premise with more specific stakes" to "Expand",
        "Suggest a complication that does not resolve the tension" to "Complicate",
        "Rewrite this so both characters have an equal amount of agency" to "Rebalance",
        "Give me three different ways this could open, in one line each" to "Openings",
        "Push the emotional temperature up without adding explicitness" to "Intensify",
        "Point out where this premise is predictable and give two fixes" to "Critique",
    )

    fun aiActionLabels(): List<String> = aiActions.map { it.second }

    fun setAiMaxTokens(value: Int) {
        lab.value = lab.value.copy(aiMaxTokens = value.coerceIn(120, 4000))
    }

    /**
     * A chip no longer fires a canned request behind the user's back. It drops the
     * prompt into the editable field so it can be read, changed or thrown away
     * before anything is sent. Firing blind is what made the typed field feel
     * dead: the old code preferred the chip's text and discarded whatever the
     * user had written.
     */
    fun stageAiAction(actionLabel: String) {
        val preset = aiActions.firstOrNull { it.second == actionLabel }?.first ?: return
        lab.value = lab.value.copy(aiPrompt = preset, aiError = "")
    }

    /** Sends exactly what is in the instruction field, verbatim. */
    fun sendAiPrompt() {
        val instruction = lab.value.aiPrompt.trim()
        if (instruction.isEmpty()) {
            lab.value = lab.value.copy(aiError = "Type an instruction first, or tap a suggestion to fill this in.")
            return
        }
        dispatchAi(instruction)
    }

    private fun dispatchAi(instruction: String) {
        val state = lab.value
        if (!settings.aiEnabled.value) {
            lab.value = state.copy(aiError = "Turn on AI assist in Settings first.")
            return
        }
        if (!settings.aiHasKey.value) {
            lab.value = state.copy(aiError = "Add an API key in Settings first.")
            return
        }
        val brief = state.brief
        val maxTokens = state.aiMaxTokens
        val temp = settings.aiTemperature.value.toDouble()
        val asked = instruction
        val history = state.aiHistory

        // Turn the brief into the constraints it imposes, rather than handing
        // over the whole document and hoping. Previously the model saw the brief
        // and a loose instruction with equal weight, so it happily rewrote
        // unrelated parts of the story — the "mixed up" feeling.
        val anchor = buildString {
            appendLine("You are helping develop ONE scenario. Everything below is already decided:")
            if (brief != null) {
                appendLine()
                appendLine(brief.text)
                val pinned = brief.allParts().filter { it.locked }.map { it.value }.filter { it.isNotBlank() }
                if (pinned.isNotEmpty()) {
                    appendLine()
                    appendLine("Pinned by the user — treat as final, never restate differently:")
                    pinned.forEach { appendLine("- $it") }
                }
            } else {
                appendLine("(No brief yet — they are still ideating.)")
            }
            if (history.isNotEmpty()) {
                appendLine()
                appendLine("Earlier in this conversation:")
                history.forEach { (q, a) ->
                    appendLine("They asked: $q")
                    appendLine("You said: ${a.take(400)}")
                }
            }
        }

        val prompt = buildString {
            appendLine("Their question: $asked")
            appendLine()
            appendLine(anchor)
            appendLine()
            appendLine("Answer their question directly, grounded in the brief above.")
        }

        lab.value = state.copy(aiBusy = true, aiError = "", aiOutput = "")
        viewModelScope.launch {
            val result = container.aiClient.complete(
                userPrompt = prompt,
                context = "",
                maxTokens = maxTokens,
                // Capped: at the user's own setting the model would wander off
                // the brief entirely, which is the behaviour being fixed.
                temperature = minOf(temp, 0.75),
                systemPromptOverride = BriefAiPrompts.conversational,
            )
            val ok = (result as? AiResult.Ok)?.text.orEmpty()
            lab.value = lab.value.copy(
                aiBusy = false,
                aiOutput = ok,
                aiError = (result as? AiResult.Failure)?.message.orEmpty(),
                // Keep the last few turns so a follow-up ("make it sadder") refers
                // to something said one message ago, not to the whole brief.
                aiHistory = if (ok.isNotBlank()) {
                    (history + (asked to ok)).takeLast(6)
                } else {
                    history
                },
            )
        }
    }

    fun generateAiScenario(
        premise: String = "",
        tone: String? = null,
        maxTokens: Int = 3000,
    ) {
        generateAiScenarioWithBudget(maxTokens)
    }

    /**
     * Same builder, but with a caller-chosen budget — used for the retry when a
     * reasoning model spends the whole allowance thinking and returns nothing.
     */
    fun generateAiScenarioWithBudget(
        maxTokens: Int,
        premise: String = "",
        tone: String? = null,
    ) {
        val state = lab.value
        if (!settings.aiEnabled.value) {
            lab.value = state.copy(aiError = "Turn on AI assist in Settings first.")
            return
        }
        if (!settings.aiHasKey.value) {
            lab.value = state.copy(aiError = "Add an API key in Settings first.")
            return
        }

        val activePremise = premise.ifBlank { state.premise }.trim()
        val prompt = buildString {
            appendLine("Generate a complete, structured scenario brief based on this premise:")
            if (activePremise.isNotBlank()) {
                appendLine("\"$activePremise\"")
            } else {
                appendLine("\"Two characters in a high-stakes, intimate dramatic situation.\"")
            }
            if (!tone.isNullOrBlank()) {
                appendLine("Tone guidance: $tone")
            }
            appendLine()
            appendLine("Steering constraints:")
            appendLine("- Explicitness register: ${Dials.EXPLICITNESS[state.dials.explicitness.coerceIn(0, 2)]}")
            appendLine("- Pacing: ${Dials.PACE[state.dials.pace.coerceIn(0, 2)]}")
            appendLine("- Power balance: ${Dials.POWER[state.dials.power.coerceIn(0, 2)]}")
            appendLine("- POV: ${Dials.POV[state.dials.pov.coerceIn(0, 2)]}")
            appendLine()
            appendLine("Priority: a coherent, specific scenario. Every value must come from")
            appendLine("this premise and fit together — one place, one time, one pair of")
            appendLine("characters whose wants and fears actually collide. Never mix")
            appendLine("unrelated fragments, and never substitute generic filler.")
            appendLine()
            appendLine("Format your response strictly using this Markdown template:")
            appendLine("""
                # [Title of Scenario]

                ## Setting
                Place: [Location]
                Time: [Time of day / era]
                Weather: [Weather conditions]
                Air: [Atmospheric mood]
                Texture: [Sensory / physical detail]

                ## Cast
                Character A: [Name] — [Age] — [Role]
                · Trait: [Dominant trait]
                · Wants: [Core objective]
                · Fears: [Deep vulnerability]
                · Secret: [Hidden detail]
                · Flaw: [Character flaw]

                Character B: [Name] — [Age] — [Role]
                · Trait: [Dominant trait]
                · Wants: [Core objective]
                · Fears: [Deep vulnerability]
                · Secret: [Hidden detail]
                · Flaw: [Character flaw]

                ## Frame
                Framing: [Core premise/arrangement]
                Power: [Power dynamic]
                Tension: [Source of tension]
                Reveals to: [What is at risk of being revealed]
                Register: [Register]
                Pacing: [Pacing]
                POV: [Narrative POV]

                ## Open
                [Opening scene or hook]

                ## Beats
                1. Escalates: [Escalation event]
                2. Complication: [Complication]
                3. Turn: [Turning point]

                ## Optional twist
                [Optional twist or shift]


                ## Close
                [Closing note or resolution]
            """.trimIndent())
        }

        lab.value = state.copy(aiBusy = true, aiError = "", aiOutput = "")
        viewModelScope.launch {
            val result = container.aiClient.complete(
                userPrompt = prompt,
                context = "",
                maxTokens = maxTokens,
                temperature = 0.8,
            )
            when (result) {
                is AiResult.Ok -> {
                    val parsedBrief = BriefMarkdownParser.parse(
                        markdown = result.text,
                        defaultPremise = activePremise,
                        dials = state.dials,
                    )
                    lab.value = lab.value.copy(
                        aiBusy = false,
                        brief = parsedBrief,
                        aiOutput = result.text,
                        premise = activePremise,
                    )
                }
                is AiResult.Failure -> {
                    lab.value = lab.value.copy(
                        aiBusy = false,
                        aiError = result.message,
                    )
                }
            }
        }
    }

    fun generateAiSlot(
        slotKey: String,
        actionType: String,
        customPrompt: String? = null,
    ) {
        val state = lab.value
        val currentBrief = state.brief
        if (currentBrief == null) {
            showMessage("Generate a brief first")
            return
        }
        val slot = currentBrief.slot(slotKey)
        if (slot == null) {
            showMessage("Slot '$slotKey' not found")
            return
        }
        if (slot.locked) {
            showMessage("Slot is locked")
            return
        }
        if (!settings.aiEnabled.value) {
            showMessage("Turn on AI assist in Settings first")
            return
        }
        if (!settings.aiHasKey.value) {
            showMessage("Add an API key in Settings first")
            return
        }

        val action = SlotAiAction.forSlot(slotKey).firstOrNull { it.name.equals(actionType, ignoreCase = true) }
        val instruction = customPrompt?.trim()?.takeIf { it.isNotEmpty() }
            ?: action?.promptInstruction
            ?: "Improve and expand this section."

        // Structured request: the model is asked for one key per part of THIS
        // slot, so each field can only be filled with its own value.
        //
        // The old version asked for "the updated lines" and then guessed which
        // line belonged to which field with regexes. Prose rarely lines up with
        // those patterns, so parts silently kept their old value or picked up
        // someone else's — which is where "mixed up and may not make sense" came
        // from. Keys are now the contract.
        val editable = slot.parts.filter { !it.locked }
        val keyList = editable.joinToString(", ") { "\"${it.key}\"" }
        val currentBlock = editable.joinToString("\n") { part ->
            val label = part.label.ifBlank { part.key }
            "$label [key=${part.key}]: ${part.value}"
        }

        val prompt = buildString {
            appendLine("Edit one section of a scenario brief.")
            appendLine("Request: $instruction")
            appendLine()
            appendLine("The other sections of this brief are already written and must NOT change.")
            appendLine("Return ONLY these keys, each holding the new text for that field:")
            appendLine(keyList)
            appendLine()
            appendLine("Current values of the section you may edit:")
            appendLine(currentBlock)
            appendLine()
            appendLine("Rules:")
            appendLine("- Reply with a single JSON object and nothing else. No prose, no markdown fence.")
            appendLine("- Use exactly these keys and no others: $keyList")
            appendLine("- Keep each value to one or two sentences, matching the style of what is there.")
            appendLine("- Stay consistent with the rest of the brief; do not rename anyone or move the setting.")
            appendLine("- If a field should not change, return its existing text unchanged.")
        }

        lab.value = state.copy(aiBusy = true, aiError = "")
        viewModelScope.launch {
            val result = container.aiClient.complete(
                userPrompt = prompt,
                context = currentBrief.text,
                maxTokens = 900,
                // Edits must be faithful, not inventive: a high temperature here
                // is what made the same request return something different each
                // time and quietly contradict the rest of the brief.
                temperature = minOf(settings.aiTemperature.value.toDouble(), 0.55),
                systemPromptOverride = BriefAiPrompts.structuredEditor,
            )
            when (result) {
                is AiResult.Ok -> {
                    val edited = applyStructuredSlotEdit(currentBrief, slotKey, result.text, editable)
                    lab.value = lab.value.copy(
                        aiBusy = false,
                        brief = edited.brief,
                        aiOutput = result.text,
                        aiError = if (edited.applied.isEmpty()) {
                            "The model didn't return usable values for this section — nothing was changed."
                        } else {
                            ""
                        },
                    )
                    showMessage(
                        if (edited.applied.isEmpty()) "No fields were updated"
                        else "Updated ${edited.applied.joinToString(", ")}",
                    )
                }
                is AiResult.Failure -> {
                    lab.value = lab.value.copy(aiBusy = false, aiError = result.message)
                }
            }
        }
    }

    /** Result of a structured slot edit: the brief plus which fields moved. */
    private data class SlotEdit(val brief: Brief, val applied: List<String>)

    /**
     * Applies a JSON object of part values onto a slot.
     *
     * Two deliberate constraints:
     *  - Only keys that are actual parts of this slot are written. A model that
     *    invents "location" or "mood" cannot smuggle a field into the brief.
     *  - Locked parts are never touched, so a pinned value survives an edit.
     *
     * When the reply isn't parseable JSON the caller is told nothing changed,
     * rather than falling back to the old regex scrape that produced the
     * mixed-up output this replaces.
     */
    private fun applyStructuredSlotEdit(
        brief: Brief,
        slotKey: String,
        rawJson: String,
        editable: List<com.ember.companion.data.Part>,
    ): SlotEdit {
        val keys = editable.map { it.key }.toSet()
        val values = parseJsonObjectValues(rawJson)
        if (values.isEmpty()) return SlotEdit(brief, emptyList())

        val slot = brief.slot(slotKey) ?: return SlotEdit(brief, emptyList())
        var applied = mutableListOf<String>()
        val newParts = slot.parts.map { part ->
            if (part.locked || part.key !in keys) return@map part
            val next = values[part.key]?.trim().orEmpty()
            // Refuse a rewrite that would blank a field or inject a new section.
            if (next.isBlank() || next.contains("##")) return@map part
            if (next == part.value) return@map part
            applied += part.label.ifBlank { part.key }
            part.copy(value = next)
        }
        if (applied.isEmpty()) return SlotEdit(brief, emptyList())

        var updated = brief.withSlot(slot.copy(parts = newParts))
        if (slotKey == "setting" && newParts.any { it.key in setOf("place", "time") }) {
            updated = updated.copy(title = Generator.rebuildTitle(updated))
        }
        return SlotEdit(updated, applied)
    }

    private fun parseJsonObjectValues(raw: String): Map<String, String> =
        BriefJson.stringMap(raw)

    fun applyAiToSlot(slotKey: String, aiOutput: String = lab.value.aiOutput) {
        val brief = lab.value.brief ?: return
        if (aiOutput.isBlank()) return
        val updated = BriefMarkdownParser.updateSlotFromAi(brief, slotKey, aiOutput)
        lab.value = lab.value.copy(brief = updated)
    }

    fun replacePartSafely(slotKey: String, partKey: String, newValue: String) {
        val brief = lab.value.brief ?: return
        val updated = BriefMarkdownParser.replacePartSafely(brief, slotKey, partKey, newValue)
        lab.value = lab.value.copy(brief = updated)
    }

    // ---- platform helper ---------------------------------------------------
    // Field discovery from a platform's own form.
    private val _platformFields = MutableStateFlow<List<FormField>>(emptyList())
    val platformFields: StateFlow<List<FormField>> = _platformFields.asStateFlow()

    private val _fetchBusy = MutableStateFlow(false)
    val fetchBusy: StateFlow<Boolean> = _fetchBusy.asStateFlow()
    private val _fetchError = MutableStateFlow<String?>(null)
    val fetchError: StateFlow<String?> = _fetchError.asStateFlow()

    /** Preview JSON for the selected platform's filled fields. */
    private val _exportPayload = MutableStateFlow<String?>(null)
    val exportPayload: StateFlow<String?> = _exportPayload.asStateFlow()

    /** Schema chosen in the Lab, which drives the whole field editor. */
    val selectedSchemaId = MutableStateFlow(CardPlatformSchemas.BUILT_IN.first().id)

    /** Built-in schemas plus any the user imported or defined. */
    val allSchemas: StateFlow<List<PlatformSchema>> =
        kotlinx.coroutines.flow.combine(
            flowOf(CardPlatformSchemas.BUILT_IN),
            settings.customSchemas,
        ) { builtIn, custom -> builtIn + custom }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardPlatformSchemas.BUILT_IN)

    val selectedSchema: StateFlow<PlatformSchema?> =
        kotlinx.coroutines.flow.combine(allSchemas, selectedSchemaId) { all, id ->
            all.firstOrNull { it.id == id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Draft values for the selected platform, saved per platform. */
    val fieldValues: StateFlow<Map<String, String>> =
        kotlinx.coroutines.flow.combine(
            allSchemas,
            selectedSchemaId,
            settings.platformDrafts,
        ) { all: List<PlatformSchema>, id: String, drafts: Map<String, String> ->
            val schema = all.firstOrNull { it.id == id }
            if (schema == null) emptyMap()
            else schema.fields.associate { f: SchemaField -> f.key to drafts["$id|${f.key}"].orEmpty() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun selectSchema(id: String) { selectedSchemaId.value = id }

    fun setFieldValue(fieldKey: String, value: String) {
        settings.setDraftValue(selectedSchemaId.value, fieldKey, value)
    }

    fun clearSelectedDraft() {
        settings.clearDrafts(selectedSchemaId.value)
        showMessage("Cleared ${selectedSchemaId.value} draft")
    }

    /**
     * Copies the current brief into the selected platform's fields, matched by
     * their source. This is the "the tool helps me come up with the scenario"
     * path: brainstorm in the Lab, then fill the platform's own boxes from what
     * you just made instead of retyping it.
     *
     * Existing drafts are never overwritten — a field the user already wrote
     * keeps their text, because losing hand-written work silently is worse
     * than leaving a box empty.
     */
    fun fillFieldsFromBrief() {
        val schema = selectedSchema.value ?: return
        val brief = lab.value.brief
        val card = currentCard()
        var filled = 0
        schema.fields.forEach { field ->
            if (settings.draftValue(selectedSchemaId.value, field.key).isNotBlank()) return@forEach
            val source = field.source.ifBlank { field.key }
            val text = CardFields.read(card, source).ifBlank { briefTextFor(field.key) }
            if (text.isNotBlank()) {
                settings.setDraftValue(selectedSchemaId.value, field.key, text)
                filled++
            }
        }
        showMessage(
            if (filled > 0) "Filled $filled field${if (filled == 1) "" else "s"} from your brief"
            else if (brief == null) "Generate a brief first, then fill fields"
            else "Every matching field already has text"
        )
    }

    /**
     * Falls back to the generated brief when the card field is empty.
     *
     * Briefs are organised into slots keyed by the Lab's own vocabulary
     * ("role", "setting", …), which is not the same as a platform's field names,
     * so this maps deliberately instead of hoping the two line up.
     */
    private fun briefTextFor(fieldKey: String): String {
        val brief = lab.value.brief ?: return ""
        fun part(key: String): String =
            brief.allParts().firstOrNull { it.key == key }?.value.orEmpty().trim()
        fun slotText(key: String): String = brief.slot(key)
            ?.parts?.firstOrNull { it.value.isNotBlank() }?.value.orEmpty().trim()

        val direct = part(fieldKey)
        if (direct.isNotBlank()) return direct

        return when (fieldKey) {
            CardFields.NAME, "name" -> brief.title
            "character_name" -> part("aname").ifBlank { brief.title }
            "mode" -> "ROLEPLAY"
            CardFields.DESCRIPTION -> slotText("cast").ifBlank { briefTeaser(brief) }
            CardFields.PERSONALITY, "personality" -> {
                val role = part("arole")
                val trait = part("atrait")
                val want = part("awant")
                val fear = part("afear")
                listOf(role, trait, if (want.isNotBlank()) "Wants: $want" else "", if (fear.isNotBlank()) "Fears: $fear" else "")
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                    .ifBlank { slotText("cast") }
            }
            "backstory" -> slotText("setting").ifBlank { brief.premise }
            "appearance" -> {
                val texture = part("texture")
                val air = part("atmosphere")
                listOf(texture, air).filter { it.isNotBlank() }.joinToString(". ")
            }
            "clothing" -> part("texture")
            "storyTone" -> part("atmosphere").ifBlank { Dials.EXPLICITNESS[brief.dials.explicitness.coerceIn(0, 2)] }
            "relationship" -> part("power").ifBlank { Dials.POWER[brief.dials.power.coerceIn(0, 2)] }
            "worldAtmosphere" -> listOf("place", "time", "wthr", "atmosphere")
                .mapNotNull { k -> brief.allParts().firstOrNull { it.key == k }?.value?.takeIf { it.isNotBlank() } }
                .joinToString(" · ")
            "keyLocations" -> part("place")
            "scenarioConflict" -> part("tension").ifBlank { part("beat2") }
            "scenarioStakes" -> part("reveals").ifBlank { part("beat1") }
            "timePeriod" -> part("time")
            "incitingIncident" -> part("framing").ifBlank { brief.premise }
            "characterFlaws" -> part("aflaw")
            "secretMotive" -> part("asecret")
            "speechPattern" -> part("register").ifBlank { Dials.EXPLICITNESS[brief.dials.explicitness.coerceIn(0, 2)] }
            "quirks" -> part("atrait")
            CardFields.SCENARIO -> slotText("setting").ifBlank { brief.premise }
            CardFields.FIRST_MES, "greetingMessage" -> slotText("open").ifBlank { part("opener") }
            CardFields.SYSTEM_PROMPT, "scenarioInstructions" -> {
                listOf(brief.slot("frame")?.body, brief.slot("beats")?.body, brief.slot("twist")?.body, brief.slot("close")?.body)
                    .filterNotNull().filter { it.isNotBlank() }.joinToString("\n\n")
            }
            "suggestedPlayerName" -> part("bname").ifBlank { "The Protagonist" }
            "suggestedPlayerDescription" -> {
                listOf(part("brole"), part("btrait")).filter { it.isNotBlank() }.joinToString(" · ")
            }
            CardFields.MES_EXAMPLE -> slotText("open")
            CardFields.CREATOR_NOTES -> briefTeaser(brief)
            CardFields.TAGS, "tags" -> brief.allParts()
                .mapNotNull { it.value.takeIf(String::isNotBlank) }
                .filter { it.length in 3..25 }
                .take(6)
                .joinToString(", ")
            else -> ""
        }
    }

    /** A short human summary of a brief, for description-ish fields. */
    private fun briefTeaser(brief: Brief): String =
        brief.premise.ifBlank { brief.sections.firstOrNull()?.body.orEmpty() }.trim()

    /**
     * Adds a field the platform needs but Ember had no idea about.
     *
     * This is the escape hatch that makes the fetched schemas and the built-in
     * ones behave the same: a site with an unusual required field can be
     * described by hand without waiting for an app update.
     */
    fun addCustomField(key: String, label: String, maxChars: Int, type: FieldType) {
        val schema = selectedSchema.value ?: return
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) {
            showMessage("Give the field a name first")
            return
        }
        if (schema.fields.any { it.key.equals(cleanKey, true) }) {
            showMessage("\"$cleanKey\" already exists")
            return
        }
        val updated = schema.copy(
            fields = schema.fields + SchemaField(
                key = cleanKey,
                label = label.trim().ifBlank { cleanKey.replace('_', ' ') },
                type = type,
                maxChars = maxChars,
                custom = true,
            )
        )
        settings.saveCustomSchema(updated)
        showMessage("Added \"${updated.field(cleanKey)?.label}\"")
    }

    fun removeCustomField(fieldKey: String) {
        val schema = selectedSchema.value ?: return
        val field = schema.field(fieldKey) ?: return
        if (!field.custom) {
            showMessage("Built-in fields can't be removed — they come from the platform's format")
            return
        }
        settings.saveCustomSchema(schema.copy(fields = schema.fields - field))
        settings.setDraftValue(schema.id, fieldKey, "")
        showMessage("Removed ${field.label}")
    }

    /**
     * Fetches a platform's own form and turns it into a selectable schema.
     *
     * A site that publishes caps in its markup keeps them; anything unknown is
     * left uncapped so the counter tells the truth instead of inventing a limit.
     */
    fun fetchSchemaFromUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            showMessage("Enter a platform URL first")
            return
        }
        _fetchBusy.value = true
        _fetchError.value = null
        viewModelScope.launch {
            try {
                val normalised = PlatformFieldFetcher.normaliseUrl(trimmed)
                val fields = PlatformFieldFetcher.fetchFields(normalised)
                if (fields.isEmpty()) {
                    _fetchError.value = "No form fields found on that page"
                    showMessage("No fields found — try the page with the create form open")
                    return@launch
                }
                val schema = CardPlatformSchemas.fromForm(normalised, fields)
                settings.saveCustomSchema(schema)
                selectedSchemaId.value = schema.id
                showMessage("Found ${fields.size} fields on ${schema.label}")
            } catch (e: Exception) {
                val reason = e.message?.take(90) ?: "network error"
                _fetchError.value = reason
                showMessage("Fetch failed: $reason")
            } finally {
                _fetchBusy.value = false
            }
        }
    }

    /** JSON payload for the selected platform's filled fields. */
    fun buildExportPayload(customUrl: String) {
        viewModelScope.launch {
            val schema = selectedSchema.value
            if (schema == null) {
                showMessage("Pick a platform first")
                return@launch
            }
            val values = fieldValues.value
            val filled = schema.fields.count { !values[it.key].isNullOrBlank() }
            if (filled == 0) {
                showMessage("Nothing filled in yet")
                return@launch
            }
            val json = CardPlatformSchemas.toJson(schema, values).toString(2)
            _exportPayload.value = json
            val missing = schema.missingRequired(values)
            showMessage(
                if (missing.isEmpty()) "Built JSON for ${schema.label} ($filled fields)"
                else "${missing.size} required field(s) still empty: ${missing.joinToString { it.label }}"
            )
        }
    }

    private val _currentScreen = MutableStateFlow<String>("lab")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    /** Kept for the old "fetch then inspect" path; now drives schema import. */
    fun loadPlatformFields(url: String) {
        _fetchBusy.value = true
        viewModelScope.launch {
            try {
                _platformFields.value = PlatformFieldFetcher.fetchFields(url)
                showMessage("Fetched ${_platformFields.value.size} fields")
            } catch (e: Exception) {
                showMessage("Failed to fetch fields: ${e.message}")
            } finally {
                _fetchBusy.value = false
            }
        }
    }

    fun clearExportPayload() {
        _exportPayload.value = null
    }

    /** Copies the built JSON, for platforms whose import takes pasted text. */
    fun copyExportPayload() {
        val payload = _exportPayload.value ?: return
        runCatching {
            val clipboard = container.appContext
                .getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("card JSON", payload))
        }
        showMessage("Copied JSON")
    }

    fun navigateToPlatformHelper() {
        _currentScreen.value = "platformHelper"
    }

    fun goBackToLab() {
        _currentScreen.value = "lab"
    }

    fun dialsSummary(dials: Dials = lab.value.dials): String = dials.summary
    val activeDialsSummary: String get() = lab.value.dials.summary
    val activeDialsShortSummary: String get() = lab.value.dials.shortSummary

    fun appendAiOutputToBrief() {
        val output = lab.value.aiOutput.trim()
        if (output.isEmpty()) return
        val brief = lab.value.brief ?: return
        // Replaces a previous AI-notes slot rather than stacking duplicates.
        val notes = com.ember.companion.data.BriefSlot(
            key = "aiNotes",
            heading = "AI notes",
            parts = listOf(Part("aiNotes", "", output)),
        )
        val kept = brief.slots.filterNot { it.key == "aiNotes" }
        lab.value = lab.value.copy(
            brief = brief.copy(slots = kept + notes),
            aiOutput = "",
        )
    }

    fun replaceBriefWithAiOutput() {
        val state = lab.value
        val output = state.aiOutput.trim()
        if (output.isEmpty()) return
        val parsed = BriefMarkdownParser.parse(output, state.premise, state.dials)
        lab.value = state.copy(
            brief = parsed,
            aiOutput = "",
        )
    }

    fun clearAiError() { lab.value = lab.value.copy(aiError = "") }

    fun copyAiOutput(context: Context) {
        val text = lab.value.aiOutput.trim()
        if (text.isEmpty()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager ?: return
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText("Ember AI notes", text),
        )
        showMessage("Copied to clipboard")
    }

    // ---- settings --------------------------------------------------------

    val aiEnabled: StateFlow<Boolean> = settings.aiEnabled
    val aiProvider: StateFlow<AiProvider> = settings.aiProvider
    val aiBaseUrl: StateFlow<String> = settings.aiBaseUrl
    val aiModel: StateFlow<String> = settings.aiModel
    val aiTemperature: StateFlow<Float> = settings.aiTemperature
    val aiHasKey: StateFlow<Boolean> = settings.aiHasKey
    val incognito: StateFlow<Boolean> = settings.incognito
    val blockThirdPartyCookies: StateFlow<Boolean> = settings.blockThirdPartyCookies
    val desktopMode: StateFlow<Boolean> = settings.desktopMode

    val aiTestState = MutableStateFlow<AiResult?>(null)

    fun setAiEnabled(value: Boolean) = settings.setAiEnabled(value)
    fun setAiProvider(value: AiProvider) { settings.setAiProvider(value); aiTestState.value = null }
    fun setAiBaseUrl(value: String) = settings.setAiBaseUrl(value)
    fun setAiModel(value: String) = settings.setAiModel(value)
    fun setAiTemperature(value: Float) = settings.setAiTemperature(value)
    fun setApiKey(value: String) { settings.setApiKey(value); aiTestState.value = null }
    fun clearApiKey() { settings.clearApiKey(); aiTestState.value = null }
    fun setIncognito(value: Boolean) = settings.setIncognito(value)
    fun setBlockThirdPartyCookies(value: Boolean) = settings.setBlockThirdPartyCookies(value)
    fun setDesktopMode(value: Boolean) = settings.setDesktopMode(value)
    fun setOffscreenGuard(value: Boolean) = settings.setOffscreenGuard(value)

    // ----- PersonaForge native story export & AI brainstorming ---------------------
    /**
     * Convert the current scenario (Brief, character card, and platform drafts)
     * into the canonical story export JSON expected by PersonaForge (schemaVersion: 1).
     */
    fun buildPersonaForgeJson(): String {
        val brief = lab.value.brief
        val allDrafts = settings.platformDrafts.value
        val pfDrafts = CardPlatformSchemas.personaForge.fields.associate { f ->
            f.key to allDrafts["personaforge|${f.key}"].orEmpty()
        }.toMutableMap()
        if (selectedSchemaId.value == "personaforge") {
            pfDrafts.putAll(fieldValues.value)
        }
        val data = if (brief != null) {
            PersonaForgeExport.fromBrief(brief, currentCard(), pfDrafts)
        } else if (pfDrafts.isNotEmpty() && pfDrafts.values.any { it.isNotBlank() }) {
            PersonaForgeExport.fromDraftValues(pfDrafts)
        } else {
            return ""
        }
        return PersonaForgeExport.toStoryExportJson(data).toString(2)
    }

    /**
     * Export the current scenario as a valid PersonaForge story transfer file (schemaVersion: 1).
     * Saves directly to Downloads with standard PersonaForge naming, copies the JSON to clipboard,
     * and triggers the system share chooser so it can be directly imported into PersonaForge (Android or Web).
     */
    fun exportScenarioToPersonaForge() {
        viewModelScope.launch {
            val json = buildPersonaForgeJson()
            if (json.isBlank()) {
                showMessage("No scenario to export – create a brief or fill platform fields first.")
                return@launch
            }
            val allDrafts = settings.platformDrafts.value
            val title = lab.value.brief?.title?.ifBlank { null }
                ?: allDrafts["personaforge|name"]?.ifBlank { null }
                ?: allDrafts["personaforge|character_name"]?.ifBlank { null }
                ?: fieldValues.value["name"]?.ifBlank { null }
                ?: "Scenario"

            val context = container.appContext
            // 1. Copy JSON to system clipboard for immediate paste into PersonaForge Web or Android
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(
                    android.content.ClipData.newPlainText("$title PersonaForge Story", json)
                )
            } catch (_: Throwable) { }

            // 2. Write file to Downloads
            val exportedFile = PersonaForgeExport.writeToDownloads(context, json, title)

            // 3. Launch share chooser
            if (exportedFile != null && exportedFile.exists()) {
                val shareIntent = PersonaForgeExport.createShareIntent(context, exportedFile, json, title)
                val chooserIntent = Intent.createChooser(shareIntent, "Import with PersonaForge").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                exportLauncher?.invoke(chooserIntent) ?: run {
                    try {
                        context.startActivity(chooserIntent)
                    } catch (_: Exception) { }
                }
                showMessage("Exported '$title'! Saved to Downloads & copied to clipboard.")
            } else {
                showMessage("Copied '$title' story export to clipboard.")
            }
        }
    }

    /**
     * Directly launches PersonaForge with the scenario JSON payload and file.
     * If PersonaForge is installed on the device, it opens PersonaForge immediately.
     * Otherwise it falls back to exportScenarioToPersonaForge() (saving to Downloads, clipboard, and chooser).
     */
    fun openInPersonaForgeDirect() {
        viewModelScope.launch {
            val json = buildPersonaForgeJson()
            if (json.isBlank()) {
                showMessage("No scenario to export – create a brief or fill platform fields first.")
                return@launch
            }
            val allDrafts = settings.platformDrafts.value
            val title = lab.value.brief?.title?.ifBlank { null }
                ?: allDrafts["personaforge|name"]?.ifBlank { null }
                ?: allDrafts["personaforge|character_name"]?.ifBlank { null }
                ?: fieldValues.value["name"]?.ifBlank { null }
                ?: "Scenario"

            val context = container.appContext
            // 1. Copy JSON to clipboard
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(
                    android.content.ClipData.newPlainText("$title PersonaForge Story", json)
                )
            } catch (_: Throwable) { }

            // 2. Write file to Downloads
            val exportedFile = PersonaForgeExport.writeToDownloads(context, json, title)

            // 3. Direct launch if installed
            if (PersonaForgeExport.isPersonaForgeInstalled(context)) {
                try {
                    val directIntent = PersonaForgeExport.createDirectHandoffIntent(context, exportedFile, json, title)
                    context.startActivity(directIntent)
                    showMessage("Opened '$title' directly in PersonaForge!")
                    return@launch
                } catch (t: Throwable) {
                    Diag.log("Direct PersonaForge launch failed: ${t.message}")
                }
            }

            // Fallback: share chooser
            exportScenarioToPersonaForge()
        }
    }

    val personaForgeAiBusy = MutableStateFlow(false)

    /**
     * Use the AI to brainstorm and generate a complete PersonaForge scenario
     * using the exact fields and schema expected by PersonaForge.
     */
    fun brainstormPersonaForgeWithAi(
        premise: String = "",
        mode: String = "ROLEPLAY",
    ) {
        if (!settings.aiEnabled.value) {
            showMessage("Turn on AI assist in Settings first.")
            return
        }
        if (!settings.aiHasKey.value) {
            showMessage("Add an API key in Settings first.")
            return
        }

        val activePremise = premise.ifBlank { lab.value.premise }.ifBlank {
            "An intense, emotionally charged encounter between two contrasting figures."
        }.trim()

        val prompt = buildString {
            appendLine("You are the PersonaForge scenario architect. Brainstorm and generate a complete, high-quality scenario for the PersonaForge interactive roleplay app.")
            appendLine("Core Premise: \"$activePremise\"")
            appendLine("Game Mode: $mode")
            appendLine()
            appendLine("Return a single JSON object containing rich, detailed values for EVERY one of these PersonaForge fields:")
            appendLine("- name: evocative, creative scenario title")
            appendLine("- mode: $mode")
            appendLine("- character_name: full name of the primary persona or narrator")
            appendLine("- personality: 2-4 sentences detailing traits, emotional core, mannerisms, and behavioral tendencies")
            appendLine("- backstory: 2-4 sentences describing their history, origin, and how they came to this moment")
            appendLine("- appearance: detailed physical description, build, features, posture, and distinctive aesthetics")
            appendLine("- clothing: attire, style, garments, or immediate environment")
            appendLine("- storyTone: dramatic tone (e.g. Dramatic, Dark, Romantic, Noir, Thriller, Whimsical)")
            appendLine("- relationship: starting dynamic with the player (e.g. Strangers, Rivals, Reluctant Allies, Enemies to Lovers)")
            appendLine("- worldAtmosphere: sensory description of the setting's mood, air, weather, and world feeling")
            appendLine("- keyLocations: 2-3 specific, named places where events unfold")
            appendLine("- scenarioConflict: the central problem, tension, or dilemma driving the scene")
            appendLine("- scenarioStakes: what is at risk if things fail / what the characters stand to lose")
            appendLine("- timePeriod: era or time setting (e.g. Near-Future Cyberpunk, Medieval Low Fantasy, Modern Day)")
            appendLine("- incitingIncident: the immediate event or conversation spark that kicks off the interaction right now")
            appendLine("- characterFlaws: genuine flaws, vulnerabilities, or psychological blind spots")
            appendLine("- secretMotive: hidden agenda, concealed intention, or suppressed desire")
            appendLine("- speechPattern: cadence, vocabulary, dialogue quirks, and speaking rhythm")
            appendLine("- quirks: memorable physical habits or idiosyncratic mannerisms")
            appendLine("- greetingMessage: the immersive opening scene or in-character first message from the persona to the player (at least 2-3 sentences, already in-character and engaging)")
            appendLine("- scenarioInstructions: director instructions for the AI model on pacing, roleplay rules, and tone boundaries")
            appendLine("- suggestedPlayerName: compelling protagonist name for the user")
            appendLine("- suggestedPlayerDescription: compelling role, traits, and description for the player's character")
            appendLine("- tags: 4-6 comma-separated tags")
            appendLine()
            appendLine("Format: return ONLY a valid JSON object with these keys. No markdown code fences, no introductory or concluding text.")
        }

        personaForgeAiBusy.value = true
        viewModelScope.launch {
            try {
                val result = container.aiClient.complete(
                    userPrompt = prompt,
                    context = "",
                    maxTokens = 3500,
                    temperature = 0.8,
                )
                when (result) {
                    is AiResult.Ok -> {
                        val map = BriefJson.stringMap(result.text)
                        if (map.isEmpty()) {
                            showMessage("AI returned an unparseable response. Try again.")
                            return@launch
                        }

                        // Save into platform drafts for personaforge
                        map.forEach { (k, v) ->
                            if (v.isNotBlank()) {
                                settings.setDraftValue("personaforge", k, v)
                            }
                        }

                        // Also construct a matching Brief so Lab visualization updates
                        val title = map["name"].orEmpty().ifBlank { activePremise }
                        val charA = map["character_name"].orEmpty()
                        val persona = map["personality"].orEmpty()
                        val player = map["suggestedPlayerName"].orEmpty().ifBlank { "The Protagonist" }
                        val playerDesc = map["suggestedPlayerDescription"].orEmpty()

                        val briefSlots = listOf(
                            BriefSlot(
                                "scenario", "Scenario & Director",
                                listOf(
                                    Part("storyTone", "Story Tone", map["storyTone"].orEmpty().ifBlank { "Dramatic" }, bank = "registers"),
                                    Part("incitingIncident", "Inciting Incident", map["incitingIncident"].orEmpty(), bank = "framings"),
                                    Part("scenarioConflict", "Scenario Conflict", map["scenarioConflict"].orEmpty(), bank = "tensions"),
                                    Part("scenarioStakes", "Scenario Stakes", map["scenarioStakes"].orEmpty(), bank = "escalations"),
                                    Part("scenarioInstructions", "Director Instructions", map["scenarioInstructions"].orEmpty(), bank = "pacingNotes"),
                                )
                            ),
                            BriefSlot(
                                "character", "Main Character (Persona)",
                                listOf(
                                    Part("character_name", "Character Name", charA, bank = "names"),
                                    Part("personality", "Personality & Traits", persona, bank = "traits"),
                                    Part("backstory", "Backstory", map["backstory"].orEmpty(), bank = "roles"),
                                    Part("appearance", "Appearance", map["appearance"].orEmpty(), bank = "sensory"),
                                    Part("clothing", "Clothing / Style", map["clothing"].orEmpty(), bank = "sensory"),
                                    Part("characterFlaws", "Character Flaws", map["characterFlaws"].orEmpty(), bank = "flaws"),
                                    Part("secretMotive", "Secret Motive", map["secretMotive"].orEmpty(), bank = "secrets"),
                                    Part("speechPattern", "Speech Pattern", map["speechPattern"].orEmpty(), bank = "registers"),
                                    Part("quirks", "Quirks", map["quirks"].orEmpty(), bank = "traits"),
                                )
                            ),
                            BriefSlot(
                                "setting", "World & Setting",
                                listOf(
                                    Part("worldAtmosphere", "World Atmosphere", map["worldAtmosphere"].orEmpty(), bank = "atmosphere"),
                                    Part("worldSetting", "World Setting", map["worldSetting"].orEmpty().ifBlank { map["keyLocations"].orEmpty() }, bank = "places"),
                                    Part("keyLocations", "Key Locations", map["keyLocations"].orEmpty(), bank = "places"),
                                    Part("timePeriod", "Time Period", map["timePeriod"].orEmpty(), bank = "times"),
                                )
                            ),
                            BriefSlot(
                                "dynamic", "Dynamic & Opening",
                                listOf(
                                    Part("relationship", "Relationship", map["relationship"].orEmpty(), bank = "powerBalances"),
                                    Part("greetingMessage", "First Message / Greeting", map["greetingMessage"].orEmpty(), bank = "openers"),
                                )
                            ),
                            BriefSlot(
                                "player", "Player Character (Protagonist)",
                                listOf(
                                    Part("suggestedPlayerName", "Player Name", player, bank = "names"),
                                    Part("suggestedPlayerDescription", "Player Description", playerDesc, bank = "roles"),
                                )
                            ),
                        )

                        val newBrief = Brief(
                            title = title,
                            slots = briefSlots,
                            premise = activePremise,
                            dials = lab.value.dials,
                        )

                        lab.value = lab.value.copy(
                            brief = newBrief,
                            premise = activePremise,
                            aiBusy = false,
                        )
                        selectedSchemaId.value = "personaforge"
                        showMessage("PersonaForge scenario brainstormed! Ready to review and export.")
                    }
                    is AiResult.Failure -> {
                        showMessage("AI brainstorming failed: ${result.message}")
                    }
                }
            } finally {
                personaForgeAiBusy.value = false
            }
        }
    }

    fun testAi() {
        aiTestState.value = null
        viewModelScope.launch {
            aiTestState.value = container.aiClient.probe()
        }
    }

    /** The Android VPN settings screen, so a user can enable a tunnel they already have. */
    fun vpnSettingsIntent(): Intent = Intent("android.net.vpn.SETTINGS")

    companion object {
        /** Matches the desktop UA the browser uses, so links behave the same. */
        const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EmberViewModel(container) as T
        }
    }
    // ---- download queue ---------------------------------------------------

    enum class DownloadState {
        /** Asking the server what the link is. */
        CHECKING,
        QUEUED,
        RUNNING,
        SUCCESS,
        FAILED,
    }

    data class DownloadEntry(
        val id: Long,
        val fileName: String,
        val url: String,
        val mimeType: String,
        val state: DownloadState = DownloadState.QUEUED,
        val doneBytes: Long = 0,
        val totalBytes: Long = 0,
        val reason: String = "",
        /**
         * True when Ember, not the system download manager, is fetching this.
         *
         * An HLS playlist has to be fetched, decrypted and remuxed in-process,
         * so it never reaches [DownloadManager] and has no row there. Polling
         * would query its synthetic id, find nothing, and report every one of
         * these in-flight downloads as failed — which is why progress for these
         * entries is pushed by the downloader instead of polled.
         */
        val managedInApp: Boolean = false,
        /**
         * Short progress text for the row, when byte counts would be useless.
         *
         * Segment playlists usually never state a total size, so a percentage
         * bar would be invented rather than measured. "Segment 12 of 40" is the
         * honest progress signal for those.
         */
        val progressLabel: String = "",
    ) {
        /** Still in flight, so it counts toward the running badge and polling. */
        fun isActive(): Boolean =
            state == DownloadState.CHECKING || state == DownloadState.QUEUED || state == DownloadState.RUNNING
    }

    private val _downloads = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val downloads: StateFlow<List<DownloadEntry>> = _downloads.asStateFlow()

    val activeDownloadCount: StateFlow<Int> = _downloads
        .map { list: List<DownloadEntry> ->
            list.count { it.isActive() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private var downloadPollJob: kotlinx.coroutines.Job? = null

    /**
     * Re-reads real progress from the system download manager.
     *
     * This is the only honest source of a download's outcome — asking the app's
     * own memory would mean reporting "queued" forever and never noticing a
     * failure the user needed to know about.
     */
    fun refreshDownloads(context: Context) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        val current = _downloads.value
        if (current.isEmpty()) return
        _downloads.value = current.map { entry -> readDownloadState(dm, entry) }
    }

    private fun readDownloadState(dm: DownloadManager, entry: DownloadEntry): DownloadEntry {
        // In-app downloads have no DownloadManager row; their state arrives via
        // push, so asking the system about them would only invent failures.
        if (entry.managedInApp) return entry
        val query = android.app.DownloadManager.Query().setFilterById(entry.id)
        return runCatching {
            dm.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use entry.copy(
                        state = DownloadState.FAILED,
                        reason = "The system lost track of this download",
                    )
                }
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))
                val done = cursor.getLong(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val reasonIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_REASON)
                val reason = if (status == android.app.DownloadManager.STATUS_FAILED &&
                    reasonIdx >= 0 && !cursor.isNull(reasonIdx)
                ) cursor.getInt(reasonIdx) else 0

                when (status) {
                    android.app.DownloadManager.STATUS_SUCCESSFUL ->
                        entry.copy(state = DownloadState.SUCCESS, doneBytes = done, totalBytes = total)
                    android.app.DownloadManager.STATUS_FAILED ->
                        entry.copy(
                            state = DownloadState.FAILED,
                            doneBytes = done,
                            totalBytes = total,
                            reason = describeDownloadFailure(reason),
                        )
                    android.app.DownloadManager.STATUS_RUNNING ->
                        entry.copy(state = DownloadState.RUNNING, doneBytes = done, totalBytes = total)
                    else ->
                        entry.copy(state = DownloadState.QUEUED, doneBytes = done, totalBytes = total)
                }
            }
        }.getOrNull() ?: entry
    }

    /** DownloadManager reason codes, in the user's terms. */
    private fun describeDownloadFailure(reason: Int): String = when (reason) {
        android.app.DownloadManager.ERROR_CANNOT_RESUME -> "Connection dropped before it could continue"
        android.app.DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage not available"
        android.app.DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "A file with that name already exists"
        android.app.DownloadManager.ERROR_FILE_ERROR -> "The file could not be saved"
        android.app.DownloadManager.ERROR_HTTP_DATA_ERROR -> "The server sent a broken response"
        android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough free storage"
        android.app.DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects from that link"
        android.app.DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "The server refused that link"
        else -> "The download failed"
    }

    private fun startDownloadPolling(context: Context) {
        if (downloadPollJob?.isActive == true) return
        downloadPollJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(900)
                refreshDownloads(context)
                val stillRunning = _downloads.value.any {
                    it.isActive()
                }
                if (!stillRunning) break
            }
        }
    }

    /** Records a failure that never reached the system download manager. */
    private fun trackImmediate(context: Context, url: String, fileName: String, failed: Boolean) {
        _downloads.value = _downloads.value + DownloadEntry(
            id = -System.nanoTime(),
            fileName = fileName,
            url = url,
            mimeType = "",
            state = if (failed) DownloadState.FAILED else DownloadState.QUEUED,
            reason = if (failed) "Could not start" else "",
        )
    }

    fun clearFinishedDownloads() {
        _downloads.value = _downloads.value.filter {
            it.isActive()
        }
    }

    /** Retries a failed download from scratch. */
    fun retryDownload(context: Context, entry: DownloadEntry) {
        _downloads.value = _downloads.value.filterNot { it.id == entry.id }
        startDownload(
            context = context,
            url = entry.url,
            userAgent = null,
            mimeType = entry.mimeType,
            contentDisposition = null,
            title = entry.fileName,
            // Entries queued before page URLs were handled still hold one.
            resolvePageUrls = true,
        )
    }

    fun openDownloadsFolder(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setDataAndType(
                Uri.parse(
                    "content://com.android.externalstorage.documents/root" +
                        "/primary%3ADownload%2FEmber",
                ),
                "resource/folder",
            )
        }
        runCatching { context.startActivity(intent) }.onFailure {
            showMessage("Open Downloads/Ember in your Files app")
        }
    }

}

enum class GenerateKind { BRIEF, CHARACTER, BEAT }

sealed interface CharacterCardEnrichState {
    object Idle : CharacterCardEnrichState
    data class Busy(val target: String) : CharacterCardEnrichState
    data class Generating(val step: String, val progress: Float) : CharacterCardEnrichState
    data class Success(val message: String, val enrichedCard: CharacterCard.Card? = null) : CharacterCardEnrichState
    data class Error(val message: String) : CharacterCardEnrichState
}

typealias CardEnrichState = CharacterCardEnrichState

enum class SlotAiAction(val slotKey: String, val label: String, val promptInstruction: String) {
    // Scenario & Director
    SCENARIO_REGENERATE("scenario", "Rewrite Scenario", "Generate fresh inciting incidents, conflict, and director instructions for this scenario."),
    SCENARIO_INTENSIFY("scenario", "Raise Stakes", "Significantly heighten the conflict and dramatic stakes for this scenario."),
    SCENARIO_TWIST("scenario", "Add Twist", "Introduce an unexpected twist and complication into the scenario instructions and conflict."),

    // Main Character (Persona)
    CHARACTER_DEEPEN("character", "Deepen Persona", "Deepen psychological traits, backstory, flaws, and secret motives for this character."),
    CHARACTER_DARKER("character", "Darker Traits", "Make this character's personality and secret motives darker, more intense, and morally complex."),
    CHARACTER_VULNERABLE("character", "More Vulnerable", "Give this character deeper emotional cracks, vulnerabilities, and nuanced quirks."),

    // Dynamic & Opening
    DYNAMIC_TENSION("dynamic", "Higher Tension", "Intensify the relationship dynamic and make the opening greeting more compelling."),
    DYNAMIC_INTIMATE("dynamic", "More Intimate", "Shift the relationship dynamic to be more intimate and emotionally charged."),

    // Player
    PLAYER_COMPLEX("player", "Deepen Protagonist", "Deepen the protagonist's background, motives, and role in this scenario."),

    // Beats (legacy)
    BEATS_REGENERATE("beats", "New Beats", "Generate 3 fresh, escalating story beats for this scenario."),
    BEATS_INTENSIFY("beats", "Intensify", "Rewrite these beats to significantly raise dramatic stakes and tension."),
    BEATS_TWIST("beats", "Add Twist", "Introduce an unexpected psychological or circumstantial twist in the beats."),

    // Cast (legacy)
    CAST_REGENERATE("cast", "New Cast", "Generate 2 contrasting characters with dynamic chemistry tailored to this premise."),
    CAST_DEEPEN("cast", "Deepen Traits", "Deepen psychological traits, core wants, vulnerabilities, and secrets."),
    CAST_REBALANCE("cast", "Equal Agency", "Rewrite character dynamics so both have equal proactive agency."),

    // Setting
    SETTING_REGENERATE("setting", "New Setting", "Generate a vivid, sensory-rich location, time, atmosphere, and physical texture."),
    SETTING_MOODIER("setting", "Darker Mood", "Shift the setting atmosphere to be moodier, atmospheric, and resonant."),

    // Frame (legacy)
    FRAME_TENSION("frame", "Higher Tension", "Heighten the unspoken interpersonal tension and power balance."),

    // Open (legacy)
    OPEN_HOOK("open", "Punchy Hook", "Write a captivating in-media-res opening hook."),
    OPEN_SLOW_BURN("open", "Atmospheric", "Write a slow-burn, atmospheric opening establishing mood and tension.");

    companion object {
        fun forSlot(slotKey: String): List<SlotAiAction> = entries.filter { it.slotKey == slotKey }
    }
}


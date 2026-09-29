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
import com.ember.companion.core.SettingsStore
import com.ember.companion.core.VpnMonitor
import com.ember.companion.data.FormField
import com.ember.companion.data.PlatformFieldFetcher
import com.ember.companion.data.AppContainer
import com.ember.companion.data.Banks
import com.ember.companion.data.Brief
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import java.io.File
import org.json.JSONObject

enum class Tab(val label: String) {
    DISCOVER("Discover"),
    LIBRARY("Library"),
    LAB("Lab"),
    SETTINGS("Settings"),
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
    val aiMaxTokens: Int = 800,
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
        browser.value = browser.value.copy(url = normalised, atHome = false)
        recordVisit(url = normalised, title = normalised)
    }

    fun search(query: String) {
        if (query.isBlank()) return
        openUrl(Sources.searchUrl(searchEngine.value, query))
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

    val extractedMedia = MutableStateFlow<List<String>>(emptyList())

    fun onMediaExtracted(jsonList: String?) {
        if (jsonList == null || jsonList == "null" || jsonList.isBlank()) {
            extractedMedia.value = emptyList()
            showMessage("No media found on page")
            return
        }
        try {
            val unescaped = if (jsonList.startsWith("\"") && jsonList.endsWith("\"")) {
                jsonList.substring(1, jsonList.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
            } else jsonList
            val array = org.json.JSONArray(unescaped)
            val urls = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val url = array.getString(i)
                if (url.startsWith("http")) urls.add(url)
            }
            if (urls.isEmpty()) {
                showMessage("No media found on page")
            } else {
                extractedMedia.value = urls
            }
        } catch(e: Exception) {
            Diag.log("Failed to parse extracted media: $e")
            showMessage("Failed to scan page")
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
    }

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

    fun startDownload(context: Context, url: String, userAgent: String?, mimeType: String, contentDisposition: String?) {
        val mime = mimeType ?: guessMime(url)
        if (mime == null || mime == "application/octet-stream") {
            showMessage("Unsupported download type")
            return
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm == null) {
            showMessage("Download manager unavailable")
            return
        }
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Ember",
        )
        if (!dir.exists()) dir.mkdirs()
        val fileName = fileNameFor(url, contentDisposition)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            addRequestHeader("User-Agent", userAgent ?: "Mozilla/5.0")
            setMimeType(mime)
            setTitle(fileName)
            setDescription("Saving to $dir")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Ember/$fileName")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        runCatching { dm.enqueue(request) }
            .onSuccess { showMessage("Downloading $fileName → Downloads/Ember") }
            .onFailure { showMessage("Download failed to start") }
    }

    private fun guessMime(url: String): String? {
        val ext = MimeTypeMap.getFileExtensionFromUrl(url)?.lowercase() ?: return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
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
            var duplicates = 0
            var failed = 0
            for (uri in uris) {
                val result = withContext(Dispatchers.IO) { buildMediaItem(context, uri) }
                when (result) {
                    is ImportOutcome.Duplicate -> duplicates++
                    is ImportOutcome.Failed -> failed++
                    is ImportOutcome.Ready -> {
                        runCatching { container.mediaDao.insert(result.item) }
                            .onSuccess { added += result.item.title }
                            .onFailure { failed++ }
                    }
                }
            }
            importBusy.value = false
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

    fun clearBrief() {
        lab.value = lab.value.copy(brief = null, aiOutput = "", aiError = "")
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
        val context = state.brief?.text.orEmpty()
        val maxTokens = state.aiMaxTokens
        val temp = settings.aiTemperature.value.toDouble()
        lab.value = state.copy(aiBusy = true, aiError = "", aiOutput = "")
        viewModelScope.launch {
            val result = container.aiClient.complete(instruction, context, maxTokens, temperature = temp)
            lab.value = lab.value.copy(
                aiBusy = false,
                aiOutput = (result as? AiResult.Ok)?.text.orEmpty(),
                aiError = (result as? AiResult.Failure)?.message.orEmpty(),
            )
        }
    }

    fun generateAiScenario(
        premise: String = "",
        tone: String? = null,
        maxTokens: Int = 1200,
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

        val prompt = buildString {
            appendLine("Task: Update the '$slotKey' section for the scenario below.")
            appendLine("Instruction: $instruction")
            appendLine()
            appendLine("Current $slotKey content:")
            appendLine(slot.body)
            appendLine()
            appendLine("Output ONLY the updated lines for the $slotKey section. Do not include markdown preamble.")
        }

        lab.value = state.copy(aiBusy = true, aiError = "")
        viewModelScope.launch {
            val result = container.aiClient.complete(
                userPrompt = prompt,
                context = currentBrief.text,
                maxTokens = 600,
                temperature = settings.aiTemperature.value.toDouble(),
            )
            when (result) {
                is AiResult.Ok -> {
                    val updatedBrief = BriefMarkdownParser.updateSlotFromAi(
                        brief = currentBrief,
                        slotKey = slotKey,
                        aiOutput = result.text,
                    )
                    lab.value = lab.value.copy(
                        aiBusy = false,
                        brief = updatedBrief,
                        aiOutput = result.text,
                    )
                    showMessage("Updated ${slot.heading}")
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
    // State for platform field discovery and export payload
    private val _platformFields = MutableStateFlow<List<FormField>>(emptyList())
    val platformFields: StateFlow<List<FormField>> = _platformFields.asStateFlow()

    private val _exportPayload = MutableStateFlow<String?>(null)
    val exportPayload: StateFlow<String?> = _exportPayload.asStateFlow()

    private val _currentScreen = MutableStateFlow<String>("lab")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    // Load fields from a given URL using the PlatformFieldFetcher
    fun loadPlatformFields(url: String) {
        viewModelScope.launch {
            try {
                val fields = PlatformFieldFetcher.fetchFields(url)
                _platformFields.value = fields
                showMessage("Fetched ${fields.size} fields from $url")
            } catch (e: Exception) {
                showMessage("Failed to fetch fields: ${e.message}")
            }
        }
    }

    // Generate a simple JSON export payload for the discovered fields
    fun buildExportPayload(customUrl: String) {
        viewModelScope.launch {
            val fields = _platformFields.value
            if (fields.isEmpty()) {
                showMessage("No fields to export. Fetch fields first.")
                return@launch
            }
            // Simple JSON construction (could use kotlinx.serialization for more robustness)
            val json = buildString {
                append("{\n")
                append("  \"url\": \"").append(customUrl).append("\",\n")
                append("  \"fields\": [\n")
                fields.forEachIndexed { idx, field ->
                    append("    {\n")
                    append("      \"name\": \"").append(field.name).append("\",\n")
                    append("      \"type\": \"").append(field.type).append("\",\n")
                    append("      \"example\": \"").append(field.example ?: "").append("\"\n")
                    append("    }")
                    if (idx < fields.lastIndex) append(",")
                    append("\n")
                }
                append("  ]\n")
                append("}\n")
            }
            _exportPayload.value = json
            showMessage("Export payload generated")
        }
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

    // ----- NEW: Persona Forge export ------------------------------------------------
    /**
     * Convert the current scenario (Brief) into the JSON format expected by Persona Forge.
     */
    private fun buildPersonaForgeJson(): String {
        val brief = lab.value.brief ?: return "{}"
        // Use the current character card (or an empty placeholder) for export.
        val card = currentCard()
        val characters = listOf(
            mapOf(
                "name" to card.name,
                "prompt" to card.personality,
                "example_dialogue" to card.exampleDialogue
            )
        )
        val obj = mapOf(
            "title" to brief.title,
            // Ember's premise acts as the description for Persona Forge.
            "description" to brief.premise,
            "characters" to characters,
            // Ember does not have tags; export empty list.
            "tags" to emptyList<String>()
        )
        return JSONObject(obj).toString(4)
    }

    /**
     * Export the current scenario as a Persona Forge JSON file.
     * Uses the existing exportLauncher (set in EmberRoot) to present a Save‑As picker.
     * Falls back to copying the file into Downloads if the launcher is not set.
     */
    fun exportScenarioToPersonaForge() {
        viewModelScope.launch {
            val json = buildPersonaForgeJson()
            if (json.isBlank()) {
                showMessage("No scenario to export – create a brief first.")
                return@launch
            }
            val fileName = "scenario_${System.currentTimeMillis()}.json"
            val cacheFile = File(container.appContext.cacheDir, fileName)
            cacheFile.writeText(json)
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, fileName)
                putExtra(Intent.EXTRA_STREAM, Uri.fromFile(cacheFile))
            }
            exportLauncher?.invoke(intent) ?: run {
                try {
                    val dest = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        fileName
                    )
                    cacheFile.copyTo(dest, overwrite = true)
                    showMessage("Exported to ${dest.absolutePath}")
                } catch (e: Exception) {
                    showMessage("Export failed: ${e.message}")
                }
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
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EmberViewModel(container) as T
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
    // Beats
    BEATS_REGENERATE("beats", "New Beats", "Generate 3 fresh, escalating story beats for this scenario."),
    BEATS_INTENSIFY("beats", "Intensify", "Rewrite these beats to significantly raise dramatic stakes and tension."),
    BEATS_TWIST("beats", "Add Twist", "Introduce an unexpected psychological or circumstantial twist in the beats."),

    // Cast
    CAST_REGENERATE("cast", "New Cast", "Generate 2 contrasting characters with dynamic chemistry tailored to this premise."),
    CAST_DEEPEN("cast", "Deepen Traits", "Deepen psychological traits, core wants, vulnerabilities, and secrets."),
    CAST_REBALANCE("cast", "Equal Agency", "Rewrite character dynamics so both have equal proactive agency."),

    // Setting
    SETTING_REGENERATE("setting", "New Setting", "Generate a vivid, sensory-rich location, time, atmosphere, and physical texture."),
    SETTING_MOODIER("setting", "Darker Mood", "Shift the setting atmosphere to be moodier, atmospheric, and resonant."),

    // Frame
    FRAME_TENSION("frame", "Higher Tension", "Heighten the unspoken interpersonal tension and power balance."),

    // Open
    OPEN_HOOK("open", "Punchy Hook", "Write a captivating in-media-res opening hook."),
    OPEN_SLOW_BURN("open", "Atmospheric", "Write a slow-burn, atmospheric opening establishing mood and tension.");

    companion object {
        fun forSlot(slotKey: String): List<SlotAiAction> = entries.filter { it.slotKey == slotKey }
    }
}


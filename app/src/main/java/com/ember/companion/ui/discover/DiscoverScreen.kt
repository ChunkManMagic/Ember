package com.ember.companion.ui.discover

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BrowserUpdated
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.companion.core.Diag
import com.ember.companion.core.VpnMonitor
import com.ember.companion.data.SearchEngine
import com.ember.companion.data.Sources
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.ArrowDropDown
import com.ember.companion.ui.EmberViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(vm: EmberViewModel, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val browser by vm.browser.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val isBookmarked by vm.isBookmarked.collectAsStateWithLifecycle()
    val engine by vm.searchEngine.collectAsStateWithLifecycle()
    val incognito by vm.incognito.collectAsStateWithLifecycle()
    val vpn by vm.vpnState.collectAsStateWithLifecycle()

    var overflowOpen by remember { mutableStateOf(false) }
    var engineMenuOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var urlDraft by remember { mutableStateOf("") }
    var showInfo by remember { mutableStateOf(false) }

    // The WebView is created by the AndroidView factory so Compose owns its
    // lifecycle; we keep a reference for imperative calls (loadUrl, goBack).
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Snapshot the reference for this composition. Everything below keys off this
    // local rather than reading the delegated `webView` property lazily, because a
    // lazy read inside onDispose would observe a LATER value and tear down a
    // WebView it does not own.
    val liveWebView = webView

    // When the URL changes (but only when we're not at the launcher), ask the
    // WebView to navigate. This is the single navigation authority.
    //
    // The key MUST include `webView`, not just `browser.url`. On first navigation
    // the URL flips to the target in the same frame the AndroidView factory is
    // being asked to build the WebView. If this effect keys on the URL alone it
    // runs first, reads a null `webView`, loads nothing, and then never runs
    // again because the URL never changes a second time — leaving a permanently
    // blank page. Keying on both makes the effect re-run the moment the view
    // actually exists.
    LaunchedEffect(browser.url, liveWebView) {
        val wv = liveWebView ?: return@LaunchedEffect
        if (!browser.atHome && wv.url != browser.url) {
            Diag.log("loadUrl ${wv.hashCode()} <- ${browser.url.take(120)}")
            wv.loadUrl(browser.url)
        }
    }

    // Keep the bookmark star in sync with whatever page is actually loaded.
    LaunchedEffect(browser.url) { vm.syncBookmarkState(browser.url) }

    // Release the WebView explicitly when the composable leaves: relying on GC
    // here leaks the whole renderer on some OEM builds. `owned` is the value this
    // effect was keyed on, so disposal can never reach a newer WebView.
    DisposableEffect(liveWebView) {
        val owned = liveWebView
        onDispose {
            owned?.let { Diag.log("webview dispose ${it.hashCode()}") }
            runCatching { owned?.stopLoading() }
            runCatching { owned?.destroy() }
        }
    }

    BackHandler(enabled = !browser.atHome) {
        val wv = webView
        if (wv?.canGoBack() == true) wv.goBack() else vm.showLauncher()
    }

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth()) {
                if (browser.atHome) {
                    TopAppBar(
                        title = {
                            Column {
                                Text("Ember", fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (incognito) "Private browsing" else "Discover",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (incognito) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        },
                        actions = {
                            VpnBadge(vpn)
                            IconButton(onClick = { vm.setIncognito(!incognito) }) {
                                Icon(
                                    imageVector = if (incognito) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle private browsing",
                                )
                            }
                            IconButton(onClick = { showInfo = true }) {
                                Icon(Icons.Filled.Info, contentDescription = "About Ember's browsing")
                            }
                        },
                    )
                } else {
                    TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = urlDraft.ifBlank { browser.url },
                                onValueChange = { urlDraft = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                placeholder = {
                                    Text(
                                        "Search or address",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (urlDraft.isNotEmpty()) {
                                            IconButton(onClick = { urlDraft = "" }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                            }
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                    val text = clip?.primaryClip?.getItemAt(0)?.text?.toString()
                                                    if (!text.isNullOrBlank()) {
                                                        urlDraft = text.trim()
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp),
                                            ) {
                                                Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = {
                                    val target = urlDraft
                                    val wv = webView
                                    if (target.isBlank()) {
                                        wv?.goBack()
                                    } else if (target.contains(' ') || !target.contains('.')) {
                                        vm.search(target)
                                    } else {
                                        vm.openUrl(target)
                                    }
                                    urlDraft = ""
                                }),
                            )
                        },
                        navigationIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    val wv = webView
                                    if (wv?.canGoBack() == true) wv.goBack() else vm.showLauncher()
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                                IconButton(
                                    onClick = { webView?.goForward() },
                                    enabled = browser.canGoForward,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Forward",
                                        tint = if (browser.canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { webView?.reload() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                            }
                            IconButton(onClick = { vm.setBookmarked(!isBookmarked) }) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = "Bookmark this page",
                                )
                            }
                            IconButton(onClick = { overflowOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Home") },
                                    leadingIcon = { Icon(Icons.Filled.Home, null) },
                                    onClick = {
                                        overflowOpen = false
                                        vm.showLauncher()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Open in system browser") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                    onClick = {
                                        overflowOpen = false
                                        vm.openExternal(
                                            context,
                                            Intent(Intent.ACTION_VIEW, Uri.parse(browser.url)),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy link") },
                                    leadingIcon = { Icon(Icons.Filled.Add, null) },
                                    onClick = {
                                        overflowOpen = false
                                        runCatching {
                                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                                as android.content.ClipboardManager
                                            clip.setPrimaryClip(android.content.ClipData.newPlainText(browser.url, browser.url))
                                        }
                                        vm.showMessage("Link copied")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Scan page for media") },
                                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                                    onClick = {
                                        overflowOpen = false
                                        webView?.evaluateJavascript(
                                            "(function(){var c=new Set();Array.from(document.images).forEach(i=>{if(i.src)c.add(i.src)});Array.from(document.querySelectorAll('video,source')).forEach(v=>{if(v.src)c.add(v.src)});return JSON.stringify(Array.from(c));})()"
                                        ) { result -> vm.onMediaExtracted(result) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Filled.Share, null) },
                                    onClick = {
                                        overflowOpen = false
                                        vm.openExternal(
                                            context,
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, "${browser.title}\n${browser.url}")
                                            },
                                        )
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Wipe cookies and cache") },
                                    leadingIcon = { Icon(Icons.Filled.CleaningServices, null) },
                                    onClick = {
                                        overflowOpen = false
                                        vm.wipeBrowsingData(context)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear history") },
                                    leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) },
                                    onClick = {
                                        overflowOpen = false
                                        vm.clearHistory()
                                    },
                                )
                            }
                        },
                    )
                    if (browser.loading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { inner ->
        Box(Modifier.padding(inner).padding(contentPadding).fillMaxSize()) {
            AndroidView(
                factory = { ctx: Context ->
                    val wv: WebView = createWebView(ctx, vm)
                    webView = wv
                    Diag.log("webview created ${wv.hashCode()}")
                    wv
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.visibility = if (browser.atHome) View.INVISIBLE else View.VISIBLE
                },
            )
            if (browser.atHome) {
                Launcher(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { vm.search(query) },
                    engine = engine,
                    onEngineChange = { vm.onEngineChange(it) },
                    engineMenuOpen = engineMenuOpen,
                    onEngineMenuToggle = { engineMenuOpen = it },
                    bookmarks = bookmarks,
                    history = history,
                    onOpen = { url -> vm.openUrl(url) },
                    onOpenBookmark = { vm.openBookmark(it) },
                    vpn = vpn,
                )
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("How Ember browses") },
            text = {
                Text(
                    "Ember's browser is an ordinary Android WebView pointed at legal, " +
                        "age-gated sites. It runs in this app only — no data is sent to Ember " +
                        "developers, because there are none and there is no server.\n\n" +
                        "Ember does not implement a VPN. Its traffic uses the phone's normal " +
                        "network, so if a VPN is already connected on the system, Ember is " +
                        "already inside it. Check the badge in the top bar.\n\n" +
                        "Private browsing stops Ember writing history and bookmarks for the " +
                        "session, but clearing cookies afterwards is still up to you.",
                )
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Got it") } },
        )
    }
    
    val extracted = vm.extractedMedia.collectAsStateWithLifecycle().value
    if (extracted.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.clearExtractedMedia() },
            title = { Text("Found Media") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(extracted.size) { i ->
                        val url = extracted[i]
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(url.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.startDownload(context, url, null, "", null) }) {
                                Icon(Icons.Filled.Bookmark, contentDescription = "Download")
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.clearExtractedMedia() }) { Text("Close") } }
        )
    }
}

@Composable
private fun VpnBadge(vpn: VpnMonitor.State) {
    val active = vpn.vpnActive
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 4.dp),
    ) {
        Icon(
            imageVector = if (active) Icons.Filled.Shield else Icons.Filled.BrowserUpdated,
            contentDescription = null,
            tint = if (active) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = if (active) "VPN" else (vpn.activeTransport ?: "Offline"),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun Launcher(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    engine: SearchEngine,
    onEngineChange: (SearchEngine) -> Unit,
    engineMenuOpen: Boolean,
    onEngineMenuToggle: (Boolean) -> Unit,
    bookmarks: List<com.ember.companion.data.db.Bookmark>,
    history: List<com.ember.companion.data.db.HistoryEntry>,
    onOpen: (String) -> Unit,
    onOpenBookmark: (com.ember.companion.data.db.Bookmark) -> Unit,
    vpn: VpnMonitor.State,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search, or paste a link") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }),
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { onEngineMenuToggle(true) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(engine.label, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change search engine")
                    }
                    DropdownMenu(
                        expanded = engineMenuOpen,
                        onDismissRequest = { onEngineMenuToggle(false) },
                    ) {
                        SearchEngine.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onEngineChange(option)
                                    onEngineMenuToggle(false)
                                },
                            )
                        }
                    }
                }
            }
        }

        if (!vpn.vpnActive) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("No VPN connected", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "If a site is blocked where you are, connect a VPN on the phone " +
                                "itself — Ember's browser uses the system network and will follow it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (bookmarks.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Bookmark,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "BOOKMARKS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        FilterChip(
                            selected = false,
                            onClick = { onOpenBookmark(bookmark) },
                            label = {
                                Text(
                                    bookmark.title.ifBlank { bookmark.url },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.widthIn(min = 60.dp, max = 220.dp),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Bookmark,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.History,
                        null,
                        Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "RECENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            items(history.take(8), key = { it.id }) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpen(entry.url) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                ) {
                    Text(
                        entry.title.ifBlank { entry.url },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        entry.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Sources.categories.forEach { category ->
            val items = Sources.byCategory(category)
            if (items.isEmpty()) return@forEach
            item {
                Text(
                    text = category.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { source ->
                        Card(
                            onClick = { onOpen(source.url) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(source.name, style = MaterialTheme.typography.titleSmall)
                                if (source.note.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        source.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(context: Context, vm: EmberViewModel): WebView {
    val webView = WebView(context)
    // Deterministic background. A transparent WebView shows whatever the window
    // composites behind it, which on a dark theme is an unreadable black void
    // during load and after any failed paint.
    webView.setBackgroundColor(android.graphics.Color.parseColor("#121212"))
    webView.overScrollMode = View.OVER_SCROLL_NEVER

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        loadWithOverviewMode = true
        useWideViewPort = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        // Deliberately FALSE. With multiple windows on, every target="_blank" link
        // and ad frame makes WebChromeClient.onCreateWindow fire; the popup WebView
        // has no parent view, so nothing ever reclaims it. Heavy ad-heavy pages
        // then walk the app into an OutOfMemoryError. With this off, the WebView
        // loads popups in the same window, which is the safe behaviour.
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        mediaPlaybackRequiresUserGesture = false
        allowFileAccess = false
        allowContentAccess = true
        cacheMode = WebSettings.LOAD_DEFAULT
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        setGeolocationEnabled(false)
        if (vm.desktopMode.value) {
            userAgentString = DESKTOP_UA
        }
    }

    
    runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(webView, vm.blockThirdPartyCookies.value) }

    webView.webViewClient = object : WebViewClient() {

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            Diag.log("nav start host=${url?.let { runCatching { Uri.parse(it).host }.getOrNull() }}")
            vm.onPageStarted(url.orEmpty())
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            Diag.log("nav done status=${view?.url} title=${view?.title?.take(40)}")
            vm.onPageFinished(view?.title.orEmpty(), url.orEmpty())
            view?.let { vm.onNavState(it.canGoBack(), it.canGoForward()) }
        }

        /**
         * The one that actually matters for this app. Adult video sites are heavy,
         * and the Chromium renderer gets OOM-killed or crashes under that load. The
         * platform default for this callback is to let the exception escape, which
         * takes the whole app down — "Aw Snap" becomes "app closed". Swallowing it
         * turns a renderer death into a recoverable message.
         */
        @androidx.annotation.RequiresApi(26)
        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            val crashed = detail?.didCrash() == true
            Diag.log("RENDERER GONE didCrash=$crashed")
            runCatching {
                vm.setRendererDied(
                    if (crashed) {
                        "The page crashed the browser renderer. Adult video sites are " +
                            "memory-hungry; try a lighter page, or free some RAM."
                    } else {
                        "Android killed the browser renderer to reclaim memory. Close some " +
                            "apps and try a lighter page."
                    },
                )
                view?.destroy()
            }
            return true
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            if (request?.isForMainFrame != true) return
            val code = error?.errorCode
            Diag.log("page error code=$code desc=${error?.description}")
            vm.setRendererDied(
                when (code) {
                    ERROR_HOST_LOOKUP -> "That address did not resolve. Check the spelling or your connection."
                    ERROR_CONNECT -> "Could not connect. If a site is blocked where you are, turn on a VPN."
                    ERROR_TIMEOUT -> "The page took too long to load."
                    else -> "That page failed to load (WebView error $code)."
                },
            )
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?,
        ): Boolean {
            val url = request?.url?.toString().orEmpty()
            val scheme = runCatching { Uri.parse(url).scheme.orEmpty() }.getOrDefault("")
            return if (scheme == "http" || scheme == "https" || scheme.isEmpty()) {
                false
            } else {
                Diag.log("external scheme=$scheme")
                // vm.openExternal expects a Context - we don't have one here,
                // so we use the activity's context via a trick: the WebView's context
                view?.context?.let { ctx ->
                    vm.openExternal(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                true
            }
        }
    }

    webView.webChromeClient = object : WebChromeClient() {

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (newProgress >= 100) vm.onNavState(view?.canGoBack() ?: false, view?.canGoForward() ?: false)
        }

        /**
         * Sites use <input type="file"> (uploads, some "verify your age" widgets).
         * Returning false is the documented safe answer: the framework sends an
         * empty result, whereas a mismatched resultMsg here is a known crash.
         */
        override fun onShowFileChooser(
            view: WebView?,
            filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>?,
            fileChooserParams: WebChromeClient.FileChooserParams?,
        ): Boolean {
            Diag.log("file chooser requested (declined)")
            return false
        }

        override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
            // Ember has no camera/mic/geolocation use case; refuse everything the
            // page asks for rather than granting by default.
            Diag.log("permission request refused: ${request?.resources?.joinToString()}")
            runCatching { request?.deny() }
        }
    }

    webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        vm.startDownload(context, url, userAgent, mimeType, contentDisposition)
    }

    return webView
}

private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"
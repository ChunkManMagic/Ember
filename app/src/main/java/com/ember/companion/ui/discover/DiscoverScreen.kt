package com.ember.companion.ui.discover

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import com.ember.companion.data.PageMediaScan
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
    var showDownloadQueue by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var urlDraft by remember { mutableStateOf("") }
    var showInfo by remember { mutableStateOf(false) }

    // The WebView is created by the AndroidView factory so Compose owns its
    // lifecycle; we keep a reference for imperative calls (loadUrl, goBack).
    var webView by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

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

    BackHandler(enabled = customView != null) {
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
    }

    BackHandler(enabled = customView == null && !browser.atHome) {
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
                                    enabled = !vm.scanBusy.value,
                                    leadingIcon = { Icon(Icons.Filled.Search, null) },
onClick = {
                                    overflowOpen = false
                                    vm.setScanBusy(true)
                                    vm.armMediaSheet()
                                    webView?.evaluateJavascript(PageMediaScan.SCRIPT) { result ->
                                        vm.onMediaExtracted(result, browser.url)
                                        vm.setScanBusy(false)
                                    } ?: run {
                                        vm.setScanBusy(false)
                                        vm.showMessage("Page not ready to scan")
                                    }
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
                                val pendingDownloads by remember { derivedStateOf { vm.activeDownloadCount.value } }
                                DropdownMenuItem(
                                    text = { Text(if (pendingDownloads > 0) "Downloads ($pendingDownloads running)" else "Downloads") },
                                    leadingIcon = { Icon(Icons.Filled.Download, null) },
                                    onClick = {
                                        overflowOpen = false
                                        showDownloadQueue = true
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
                    val wv: WebView = createWebView(
                        context = ctx,
                        vm = vm,
                        onShowCustomView = { v, cb ->
                            customView = v
                            customViewCallback = cb
                        },
                        onHideCustomView = {
                            customViewCallback?.onCustomViewHidden()
                            customView = null
                            customViewCallback = null
                        },
                    )
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

    if (customView != null) {
        val window = (context as? Activity)?.window
        DisposableEffect(Unit) {
            val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                insetsController?.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black)
                .zIndex(999f),
        ) {
            AndroidView(
                factory = { _ ->
                    val parent = customView?.parent as? ViewGroup
                    parent?.removeView(customView)
                    customView ?: View(context)
                },
                modifier = Modifier.fillMaxSize(),
            )
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
    
    ScannedMediaSheet(vm = vm)

    if (showDownloadQueue) {
        DownloadQueueDialog(vm = vm, onDismiss = { showDownloadQueue = false })
    }
}

/**
 * Results of a page scan, as real cards instead of a wall of filenames.
 *
 * Everything here comes from what the page itself published (schema.org JSON-LD
 * or OpenGraph), read by [PageMediaScan] inside the already-loaded WebView — so
 * titles, runtimes, resolutions and poster frames are the site's own numbers,
 * not guesses. Runs entirely on the device; Ember has no server.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ScannedMediaSheet(vm: EmberViewModel) {
    val context = LocalContext.current
    val busy by vm.scanBusy.collectAsStateWithLifecycle()
    val items by vm.visibleScannedMedia.collectAsStateWithLifecycle()
    val all by vm.extractedMedia.collectAsStateWithLifecycle()
    val metaOnly by vm.scanMetaOnly.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    var showAll by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    // Show the sheet whenever a scan produced anything (including a metadata-only
    // scan filtered down to nothing, so the user can flip to "show all"), but
    // only when the user actually asked for it. The media observer fires on
    // every request the page makes, and a stray scroll would otherwise reopen
    // this the moment it was dismissed.
    if (all.isEmpty() && !busy) return
    if (!vm.isMediaSheetArmed()) return

    ModalBottomSheet(onDismissRequest = { vm.clearExtractedMedia() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (all.isEmpty()) "Scanning page…" else "Found on this page",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (all.isNotEmpty()) {
                        val detailed = all.count { it.fromMetadata }
                        Text(
                            if (detailed > 0) "$detailed with details · ${all.size} total"
                            else "${all.size} found · page published no details",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            if (all.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !metaOnly,
                        onClick = { showAll = true; vm.setScanMetaOnly(false) },
                        label = { Text("Everything (${all.size})") },
                    )
                    FilterChip(
                        selected = metaOnly,
                        onClick = { showAll = false; vm.setScanMetaOnly(true) },
                        label = { Text("With details (${all.count { it.fromMetadata }})") },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (items.isEmpty()) {
                Text(
                    "This page didn't publish media details. Switch to Everything to see raw files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 520.dp),
                ) {
                    items(items.size, key = { i -> items[i].url }) { i ->
                        ScannedMediaCard(
                            item = items[i],
                            onDownload = {
                                vm.saveScannedToLibrary(context, items[i])
                                showQueue = true
                            },
                            downloads = downloads,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { vm.clearExtractedMedia() }) { Text("Close") }
            }
        }
    }

    if (showQueue) {
        DownloadQueueDialog(
            vm = vm,
            onDismiss = { showQueue = false },
        )
    }
}

/**
 * Live download queue.
 *
 * Ember previously reported only "unsupported download type" and moved on, so a
 * user had no way to tell a refused link from a slow one. Every download now
 * carries its real state from the system download manager — queued, running with
 * a progress bar, done, or failed with the reason — and failures can be retried
 * in place.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueDialog(
    vm: EmberViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val downloads by vm.downloads.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val active = downloads.count {
                        it.state == EmberViewModel.DownloadState.QUEUED ||
                            it.state == EmberViewModel.DownloadState.RUNNING
                    }
                    Text(
                        if (downloads.isEmpty()) "Nothing yet"
                        else if (active > 0) "$active in progress · ${downloads.size} total"
                        else "${downloads.size} finished · saved to Downloads/Ember",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(10.dp))

            if (downloads.isEmpty()) {
                Text(
                    "Downloads you start appear here with their progress and result.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 460.dp),
                ) {
                    items(downloads.size, key = { i -> downloads[i].id }) { i ->
                        DownloadRow(
                            entry = downloads[i],
                            onRetry = { vm.retryDownload(context, downloads[i]) },
                            onOpen = { vm.openDownloadsFolder(context) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.openDownloadsFolder(context) }) {
                        Text("Open folder", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = vm::clearFinishedDownloads) {
                        Text("Clear finished", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    entry: EmberViewModel.DownloadEntry,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
) {
    val color = when (entry.state) {
        EmberViewModel.DownloadState.SUCCESS -> MaterialTheme.colorScheme.primary
        EmberViewModel.DownloadState.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (entry.state) {
        EmberViewModel.DownloadState.SUCCESS -> Icons.Filled.CheckCircle
        EmberViewModel.DownloadState.FAILED -> Icons.Filled.Error
        EmberViewModel.DownloadState.CHECKING -> Icons.Filled.Search
        EmberViewModel.DownloadState.RUNNING -> Icons.Filled.Downloading
        EmberViewModel.DownloadState.QUEUED -> Icons.Filled.Schedule
    }
    val status = when (entry.state) {
        EmberViewModel.DownloadState.SUCCESS -> "Saved"
        EmberViewModel.DownloadState.FAILED -> entry.reason.ifBlank { "Failed" }
        EmberViewModel.DownloadState.CHECKING -> "Checking what this link is…"
        EmberViewModel.DownloadState.RUNNING ->
            when {
                entry.progressLabel.isNotBlank() -> entry.progressLabel
                entry.totalBytes > 0 -> "${entry.doneBytes / 1024} of ${entry.totalBytes / 1024} KB"
                else -> "Downloading"
            }
        EmberViewModel.DownloadState.QUEUED -> "Queued"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = color)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(status, style = MaterialTheme.typography.labelSmall, color = color)
                }
                if (entry.state == EmberViewModel.DownloadState.FAILED) {
                    TextButton(onClick = onRetry) {
                        Text("Retry", style = MaterialTheme.typography.labelSmall)
                    }
                } else if (entry.state == EmberViewModel.DownloadState.SUCCESS) {
                    IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            "Open folder",
                            Modifier.size(16.dp),
                        )
                    }
                }
            }
            if (entry.state == EmberViewModel.DownloadState.RUNNING && entry.totalBytes > 0) {
                LinearProgressIndicator(
                    progress = { (entry.doneBytes.toFloat() / entry.totalBytes).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/**
 * A scanned item with a labelled download button.
 *
 * The button says "Download" in words and reports the outcome inline, because
 * an unlabelled icon that silently refuses is indistinguishable from a bug.
 */
@Composable
private fun ScannedMediaCard(
    item: com.ember.companion.data.PageMedia,
    onDownload: () -> Unit,
    downloads: List<EmberViewModel.DownloadEntry>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Poster frame. Remote thumbnails load via Coil, which LibraryScreen
            // already uses, so this needs no new dependency.
            Box(
                Modifier
                    .size(width = 132.dp, height = 76.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (item.thumbnailUrl.isNotBlank() && item.thumbnailUrl.startsWith("http")) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = if (item.isVideo) Icons.Filled.PlayArrow else Icons.Filled.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.durationLabel.isNotBlank()) {
                    Text(
                        item.durationLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(androidx.compose.ui.graphics.Color(0xB3000000))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    item.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.isVideo) MediaBadge("Video")
                    val res = item.resolutionLabel
                    if (res.isNotBlank()) MediaBadge(res)
                    if (!item.fromMetadata) MediaBadge("Raw file")
                }
                if (item.title.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.url.substringAfterLast('/').substringBefore('?').take(48),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Live state for this specific file, so the card can say whether its
            // own download worked rather than leaving the user to guess.
            val mine = downloads.lastOrNull { d ->
                d.url == item.url || item.url.endsWith(d.url) || d.url.endsWith(item.url)
            }

            Column(horizontalAlignment = Alignment.End) {
                FilledTonalButton(
                    onClick = onDownload,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Filled.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download", style = MaterialTheme.typography.labelMedium)
                }
                if (mine != null) {
                    Spacer(Modifier.height(4.dp))
                    when (mine.state) {
                        EmberViewModel.DownloadState.SUCCESS -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                null,
                                Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "Saved",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        EmberViewModel.DownloadState.FAILED -> Text(
                            mine.reason.ifBlank { "Failed" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                        )
                        EmberViewModel.DownloadState.CHECKING -> Text(
                            "Checking…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        EmberViewModel.DownloadState.RUNNING -> Text(
                            if (mine.totalBytes > 0) "${mine.doneBytes / 1024} KB" else "Downloading…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        EmberViewModel.DownloadState.QUEUED -> Text(
                            "Queued",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaBadge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
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
private fun createWebView(
    context: Context,
    vm: EmberViewModel,
    onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideCustomView: () -> Unit,
): WebView {
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
        // Auto-resolved downloads refetch segments outside the WebView, so they
        // have to present the same agent the page was served under.
        vm.setWebUserAgent(userAgentString)
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
            // A search result was opened for its download. The player often
            // requests the stream just after onPageFinished, so give it a moment
            // before scanning or the only media found would be thumbnails.
            if (vm.consumeAutoScan()) {
                val target = view ?: return
                // Only if the network observer has not already grabbed the
                // stream on its own; otherwise this is the fallback for sites
                // whose manifest never showed up as a request.
                if (!vm.isResolving()) vm.armMediaSheet()
                target.postDelayed({
                    runCatching {
                        target.evaluateJavascript(PageMediaScan.SCRIPT) { result ->
                            vm.onMediaExtracted(result, url)
                        }
                    }.onFailure {
                        vm.showMessage("Page not ready to scan")
                    }
                }, SCAN_DELAY_AFTER_LOAD_MS)
            }
        }

        /**
         * Watches what the page fetches, so playlists found only in script are
         * still catchable.
         *
         * Returns null to let the request proceed untouched — this is purely an
         * observation point, and Ember must never alter or block page traffic.
         * Doing so would break playback and put it in the path of circumventing a
         * site's own access decisions.
         */
        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?,
        ): android.webkit.WebResourceResponse? {
            request?.url?.toString()?.let { vm.onNetworkMediaSeen(it) }
            return null
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

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            Diag.log("webChromeClient onShowCustomView")
            if (view != null && callback != null) {
                onShowCustomView(view, callback)
            }
        }

        override fun onHideCustomView() {
            Diag.log("webChromeClient onHideCustomView")
            onHideCustomView()
        }

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

private const val SCAN_DELAY_AFTER_LOAD_MS = 1400L

private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"
package com.ember.companion.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ember.companion.core.SearchHit
import com.ember.companion.ui.EmberViewModel
import com.ember.companion.ui.SearchSort

/**
 * One query, every configured site, one list.
 *
 * The results come from the Termux service rather than from any web search
 * endpoint, which is the whole point: a public metasearch either caps the API,
 * blocks the request, or has no adult-capable engines at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: EmberViewModel, contentPadding: PaddingValues) {
    val state by vm.search.collectAsState()
    val context = LocalContext.current

    // Re-probe on entry rather than only at first launch: the user has probably
    // just started Termux, so the answer here is different every time.
    LaunchedEffect(Unit) { vm.refreshSearchService() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Search", fontWeight = FontWeight.Bold)
                    Text(
                        text = serviceLine(state.serviceUp, state.providers.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                IconButton(onClick = { vm.refreshSearchService() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Check the search service")
                }
            },
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Search every site at once") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { vm.runSearch() },
            ),
        )

        if (state.providers.size > 1) {
            Spacer(Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.providers, key = { it.id }) { provider ->
                    FilterChip(
                        selected = provider.id in state.enabled,
                        onClick = { vm.onSearchSiteToggle(provider.id) },
                        label = { Text(provider.label, style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        }

        when {
            state.busy -> BusyState()
            state.message.isNotBlank() && state.results.isEmpty() -> NoticeCard(state.message)
            state.results.isEmpty() && state.hasSearched -> NoticeCard("No matches on the selected sites.")
            else -> ResultList(
                results = state.sortedResults,
                sort = state.sort,
                onSortChange = vm::setSearchSort,
                siteErrors = state.siteErrors,
                bottomPadding = contentPadding,
                onDownload = { hit ->
                    // Only a real media URL can be handed straight to the
                    // download manager. Search results are page URLs, and those
                    // have to be opened in the browser, which earns the site's
                    // cookies and reveals the stream the player requests.
                    val direct = hit.media?.takeIf { it.isNotBlank() }
                    if (direct != null) {
                        vm.startDownload(
                            context = context,
                            url = direct,
                            userAgent = null,
                            mimeType = "",
                            contentDisposition = null,
                            title = hit.title,
                        )
                    } else {
                        vm.openAndScan(hit.url, hit.title)
                    }
                },
                onOpen = vm::openSearchHit,
            )
        }
    }
}

private fun serviceLine(up: Boolean?, siteCount: Int): String = when (up) {
    null -> "Checking the local service…"
    false -> "Service offline"
    true -> if (siteCount == 1) "1 site connected" else "$siteCount sites connected"
}

@Composable
private fun BusyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Searching…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NoticeCard(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.padding(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ResultList(
    results: List<SearchHit>,
    sort: SearchSort,
    onSortChange: (SearchSort) -> Unit,
    siteErrors: List<com.ember.companion.core.SearchSiteError>,
    bottomPadding: PaddingValues,
    onDownload: (SearchHit) -> Unit,
    onOpen: (SearchHit) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = bottomPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            val siteCount = results.map { it.site }.distinct().size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${results.size} videos found across $siteCount site${if (siteCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(SearchSort.entries) { s ->
                    val isSelected = s == sort
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSortChange(s) },
                        label = {
                            Text(
                                text = when (s) {
                                    SearchSort.RELEVANCE -> "Default"
                                    SearchSort.DURATION_DESC -> "Longest first"
                                    SearchSort.DURATION_ASC -> "Shortest first"
                                    SearchSort.TITLE_ASC -> "Title (A-Z)"
                                    SearchSort.SITE -> "By Site"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }
        }
        if (siteErrors.isNotEmpty()) {
            item {
                // Partial failure is normal here: one site refusing a query must
                // not hide the results the other sites did return.
                Text(
                    text = "Skipped: " + siteErrors.joinToString(", ") { "${it.site} (${it.error})" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(results, key = { it.url }) { hit ->
            ResultRow(hit = hit, onDownload = onDownload, onOpen = onOpen)
        }
    }
}

@Composable
private fun ResultRow(hit: SearchHit, onDownload: (SearchHit) -> Unit, onOpen: (SearchHit) -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(hit)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = hit.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = listOfNotNull(
                        hit.site,
                        hit.uploader?.takeIf { it.isNotBlank() },
                        hit.duration?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { onOpen(hit) }) {
                Icon(
                    Icons.Filled.OpenInNew,
                    contentDescription = "Open in the browser",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = { onDownload(hit) }) {
                Icon(Icons.Filled.Download, contentDescription = "Download this")
            }
        }
    }
}

@Composable
private fun Thumbnail(hit: SearchHit) {
    val url = hit.thumb
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 62.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = hit.site.take(2).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
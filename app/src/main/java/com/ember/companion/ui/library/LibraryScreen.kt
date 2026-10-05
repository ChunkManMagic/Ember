package com.ember.companion.ui.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ember.companion.data.Thumbs
import com.ember.companion.data.db.MediaItem
import com.ember.companion.ui.EmberViewModel
import com.ember.companion.ui.MediaSort
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: EmberViewModel, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val items by vm.filteredMedia.collectAsStateWithLifecycle()
    val total by vm.mediaCount.collectAsStateWithLifecycle()
    val query by vm.libraryQuery.collectAsStateWithLifecycle()
    val kind by vm.libraryKind.collectAsStateWithLifecycle()
    val currentSort by vm.librarySort.collectAsStateWithLifecycle()
    val isGridView by vm.libraryViewAsGrid.collectAsStateWithLifecycle()
    val busy by vm.importBusy.collectAsStateWithLifecycle()
    val importSummary by vm.lastImportSummary.collectAsStateWithLifecycle()

    var detail by remember { mutableStateOf<MediaItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            vm.importUris(context, uris)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Library", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (busy) "Importing…" else "$total item${if (total == 1) "" else "s"} on this device",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.toggleLibraryView() },
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
                        )
                    }
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort library")
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                        ) {
                            MediaSort.values().forEach { sortOption ->
                                DropdownMenuItem(
                                    text = { Text(sortOption.label) },
                                    trailingIcon = if (currentSort == sortOption) {
                                        { Icon(Icons.Filled.Check, contentDescription = "Selected", Modifier.size(16.dp)) }
                                    } else null,
                                    onClick = {
                                        vm.setLibrarySort(sortOption)
                                        sortMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    if (total > 0) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear library")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    picker.launch(
                        arrayOf("video/*", "image/*", "audio/*", "text/*", "application/pdf"),
                    )
                },
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
                icon = {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                },
                text = {
                    Text(if (busy) "Importing…" else "Import files")
                },
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .padding(contentPadding)
                .fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.libraryQuery.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search title, tag, notes, collection") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { vm.libraryQuery.value = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                )
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val chipOptions = listOf(
                    Triple("all", "All", null),
                    Triple("favorites", "Favorites", Icons.Filled.Favorite),
                    Triple(MediaItem.KIND_VIDEO, "Video", Icons.Filled.Videocam),
                    Triple(MediaItem.KIND_IMAGE, "Images", Icons.Filled.Image),
                    Triple(MediaItem.KIND_AUDIO, "Audio", Icons.Filled.AudioFile),
                    Triple(MediaItem.KIND_TEXT, "Text", Icons.Filled.Description),
                )
                items(chipOptions) { (value, label, icon) ->
                    FilterChip(
                        selected = kind == value,
                        onClick = { vm.libraryKind.value = value },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = if (icon != null) {
                            {
                                Icon(
                                    icon,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = if (value == "favorites") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else null,
                    )
                }
            }

            importSummary?.let { summary ->
                AssistChip(
                    onClick = { vm.lastImportSummary.value = null },
                    label = { Text(summary, style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) },
                    trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(14.dp)) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }

            if (items.isEmpty()) {
                EmptyState(hasAny = total > 0, importing = busy)
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { item ->
                        LibraryCard(
                            item = item,
                            thumb = vm.videoThumb(context, item),
                            onClick = { detail = item },
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { item ->
                        LibraryListItem(
                            item = item,
                            thumb = vm.videoThumb(context, item),
                            onClick = { detail = item },
                            onToggleFavorite = {
                                vm.toggleMediaFavorite(item)
                            },
                        )
                    }
                }
            }
        }
    }

    detail?.let { item ->
        MediaDetailSheet(
            item = item,
            thumb = vm.videoThumb(context, item),
            onDismiss = { detail = null },
            onSave = { updated ->
                vm.updateMedia(updated)
                detail = null
            },
            onToggleFavorite = {
                vm.toggleMediaFavorite(item)
                detail = item.copy(favorite = !item.favorite)
            },
            onDelete = {
                vm.deleteMedia(item)
                detail = null
            },
            onOpenExternal = {
                val uri = runCatching { Uri.parse(item.uri) }.getOrNull()
                if (uri != null) {
                    vm.openExternal(
                        context,
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, item.mimeType.ifBlank { "*/*" })
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                    )
                } else {
                    vm.showMessage("That file is no longer reachable")
                }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the whole library?") },
            text = {
                Text(
                    "This deletes Ember's records for every imported item. The original " +
                        "files on your device are not touched.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearLibrary()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun LibraryCard(item: MediaItem, thumb: File?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    thumb != null -> AsyncImage(
                        model = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    item.kind == MediaItem.KIND_IMAGE -> {
                        val uri = Thumbs.imageUriFor(item)
                        if (uri != null) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(iconFor(item.kind), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    else -> Icon(
                        iconFor(item.kind),
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }

                if (item.favorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                RoundedCornerShape(10.dp),
                            )
                            .padding(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = "Favorited",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                if (item.kind == MediaItem.KIND_VIDEO) {
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                                RoundedCornerShape(16.dp),
                            )
                            .padding(6.dp),
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.durationMs > 0) {
                    Text(
                        formatDuration(item.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (item.sizeBytes > 0) {
                    Text(
                        formatBytes(item.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryListItem(
    item: MediaItem,
    thumb: File?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    thumb != null -> AsyncImage(
                        model = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    item.kind == MediaItem.KIND_IMAGE -> {
                        val uri = Thumbs.imageUriFor(item)
                        if (uri != null) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(iconFor(item.kind), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> Icon(
                        iconFor(item.kind),
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
                if (item.kind == MediaItem.KIND_VIDEO) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.kind.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (item.sizeBytes > 0) {
                        Text(
                            "· " + formatBytes(item.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.durationMs > 0) {
                        Text(
                            "· " + formatDuration(item.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (item.tags.isNotBlank()) {
                    Text(
                        item.tags,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    if (item.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (item.favorite) "Favorited" else "Favorite",
                    tint = if (item.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(hasAny: Boolean, importing: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                if (hasAny) Icons.AutoMirrored.Filled.Sort else Icons.AutoMirrored.Filled.InsertDriveFile,
                null,
                Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (importing) "Importing…" else if (hasAny) "Nothing matches" else "Library is empty",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (hasAny) {
                    "Try a different search or filter."
                } else {
                    "Ember indexes files you already have. Import from your device, or download " +
                        "something in Discover and pick it up from Downloads/Ember."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaDetailSheet(
    item: MediaItem,
    thumb: File?,
    onDismiss: () -> Unit,
    onSave: (MediaItem) -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    var title by remember(item.id) { mutableStateOf(item.title) }
    var tags by remember(item.id) { mutableStateOf(item.tags) }
    var collection by remember(item.id) { mutableStateOf(item.collection) }
    var notes by remember(item.id) { mutableStateOf(item.notes) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (thumb != null || item.kind == MediaItem.KIND_IMAGE) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val model = thumb ?: Thumbs.imageUriFor(item)
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (item.kind == MediaItem.KIND_VIDEO) {
                        IconButton(
                            onClick = onOpenExternal,
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                                    RoundedCornerShape(26.dp),
                                ),
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
            } else if (item.kind == MediaItem.KIND_AUDIO) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.AudioFile, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Audio Track", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                if (item.durationMs > 0) {
                                    Text(formatDuration(item.durationMs), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Button(onClick = onOpenExternal) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Media Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onToggleFavorite) {
                        Icon(
                            if (item.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            null,
                            Modifier.size(16.dp),
                            tint = if (item.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (item.favorite) "Favorited" else "Favorite")
                    }
                    OutlinedButton(onClick = onOpenExternal) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open")
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags (comma separated)") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = collection,
                onValueChange = { collection = it },
                label = { Text("Collection") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 3,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${item.kind.uppercase()} · ${item.mimeType.ifBlank { "unknown" }}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (item.sizeBytes > 0) {
                        Text("Size: ${formatBytes(item.sizeBytes)}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (item.durationMs > 0) {
                        Text("Duration: ${formatDuration(item.durationMs)}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (item.width > 0 && item.height > 0) {
                        Text("Dimensions: ${item.width} × ${item.height} px", style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Added: ${formatDate(item.addedAt)}", style = MaterialTheme.typography.labelSmall)
                    if (item.sha256.isNotBlank()) {
                        Text("SHA-256: ${item.sha256.take(24)}…", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            onSave(
                                item.copy(
                                    title = title.trim().ifBlank { item.title },
                                    tags = tags.trim(),
                                    collection = collection.trim(),
                                    notes = notes.trim(),
                                ),
                            )
                        },
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

private fun iconFor(kind: String): ImageVector = when (kind) {
    MediaItem.KIND_VIDEO -> Icons.Filled.Videocam
    MediaItem.KIND_IMAGE -> Icons.Filled.Image
    MediaItem.KIND_AUDIO -> Icons.Filled.AudioFile
    MediaItem.KIND_TEXT -> Icons.Filled.Description
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return "%.1f %s".format(value, units[index])
}

private fun formatDate(millis: Long): String =
    java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))

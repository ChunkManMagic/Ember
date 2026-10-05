package com.ember.companion.ui.lab

import android.content.Intent
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import com.ember.companion.ui.LabViewMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.ember.companion.data.Banks
import com.ember.companion.data.Brief
import com.ember.companion.data.BriefSlot
import com.ember.companion.data.Dials
import com.ember.companion.data.Part
import com.ember.companion.data.db.Scenario
import com.ember.companion.ui.EmberViewModel
import com.ember.companion.ui.GenerateKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioLabScreen(vm: EmberViewModel, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val lab by vm.lab.collectAsStateWithLifecycle()
    val scenarios by vm.scenarios.collectAsStateWithLifecycle()
    val count by vm.scenarioCount.collectAsStateWithLifecycle()
    val query by vm.scenarioQuery.collectAsStateWithLifecycle()
    val filtered by vm.filteredScenarios.collectAsStateWithLifecycle()
    val aiEnabled by vm.aiEnabled.collectAsStateWithLifecycle()
    val aiHasKey by vm.aiHasKey.collectAsStateWithLifecycle()
    val labViewMode by vm.labViewMode.collectAsStateWithLifecycle()
    val steeringExpanded by vm.steeringExpanded.collectAsStateWithLifecycle()
    val smartRerollBusyKey by vm.smartRerollBusyKey.collectAsStateWithLifecycle()
    val partVariations by vm.partVariations.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf(Mode.GENERATE) }
    var saveDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Scenario?>(null) }
    var banksDialog by remember { mutableStateOf(false) }
    var showCardExport by remember { mutableStateOf(false) }
    var showCardImport by remember { mutableStateOf(false) }
    var showPlatformFields by remember { mutableStateOf(false) }
    var editingPart by remember { mutableStateOf<Part?>(null) }
    var editingTitle by remember { mutableStateOf(false) }
    var addingPartSlotKey by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Lab", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "$count saved scenario${if (count == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showPlatformFields = true }) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Platform Helper")
                    }
                    IconButton(onClick = { banksDialog = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = "Customise word banks")
                    }
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
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Mode.entries.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = mode == value,
                        onClick = { mode = value },
                        shape = SegmentedButtonDefaults.itemShape(index, Mode.entries.size),
                    ) {
                        Text(value.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            when (mode) {
                Mode.IMAGE -> ImagePane(vm)
                Mode.BUILDER -> BuilderPane(
                    vm = vm,
                    aiEnabled = aiEnabled,
                    aiHasKey = aiHasKey,
                    labViewMode = labViewMode,
                    smartRerollBusyKey = smartRerollBusyKey,
                    onSave = { saveDialog = true },
                    onExportCard = { showCardExport = true },
                    onImportCard = { showCardImport = true },
                    onPlatformFields = { showPlatformFields = true },
                    onExportPersonaForge = { vm.openInPersonaForgeDirect() },
                    onEditPart = { editingPart = it },
                    onEditTitle = { editingTitle = true },
                    onAddPart = { addingPartSlotKey = it },
                )
                Mode.GENERATE -> GeneratePane(
                    vm = vm,
                    brief = lab.brief,
                    aiEnabled = aiEnabled,
                    aiHasKey = aiHasKey,
                    labViewMode = labViewMode,
                    smartRerollBusyKey = smartRerollBusyKey,
                    steeringExpanded = steeringExpanded,
                    onSave = { saveDialog = true },
                    onExportCard = { showCardExport = true },
                    onImportCard = { showCardImport = true },
                    onPlatformFields = { showPlatformFields = true },
                    onExportPersonaForge = { vm.openInPersonaForgeDirect() },
                    onEditPart = { editingPart = it },
                    onEditTitle = { editingTitle = true },
                    onAddPart = { addingPartSlotKey = it },
                )

                Mode.LIBRARY -> LibraryPane(
                    vm = vm,
                    scenarios = filtered,
                    query = query,
                    totalCount = scenarios.size,
                    onEdit = { editTarget = it },
                )
            }
        }
    }

    if (saveDialog && lab.brief != null) {
        SaveBriefDialog(
            defaultTitle = lab.brief!!.title,
            onDismiss = { saveDialog = false },
            onSave = { title, tags ->
                vm.saveBrief(title, tags)
                saveDialog = false
            },
        )
    }

    editTarget?.let { scenario ->
        ScenarioEditorDialog(
            scenario = scenario,
            onDismiss = { editTarget = null },
            onSave = {
                vm.updateScenario(it)
                editTarget = null
            },
            onDelete = {
                vm.deleteScenario(scenario)
                editTarget = null
            },
            onShare = {
                vm.openExternal(
                    context,
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, scenario.title)
                        putExtra(Intent.EXTRA_TEXT, scenario.body)
                    },
                )
            },
        )
    }

    if (banksDialog) {
        BanksDialog(vm = vm, custom = lab.customBanks, onDismiss = { banksDialog = false })
    }

    if (showPlatformFields) {
        PlatformFieldsDialog(vm = vm, onDismiss = { showPlatformFields = false })
    }

    if (showCardExport) {
        CardExportDialog(vm = vm, onDismiss = { showCardExport = false })
    }

    if (showCardImport) {
        CardImportDialog(vm = vm, onDismiss = { showCardImport = false })
    }

    editingPart?.let { part ->
        EditPartDialog(
            part = part,
            vm = vm,
            onDismiss = { editingPart = null },
            onSave = { label, value ->
                vm.updatePart(part.key, label, value)
                editingPart = null
            },
            onSmartReroll = {
                vm.smartRerollPart(part.key)
                editingPart = null
            },
            onVariations = {
                val key = part.key
                editingPart = null
                vm.requestPartVariations(key)
            },
        )
    }

    if (editingTitle && lab.brief != null) {
        EditTitleDialog(
            currentTitle = lab.brief!!.title,
            onDismiss = { editingTitle = false },
            onSave = { newTitle ->
                vm.updateBriefTitle(newTitle)
                editingTitle = false
            },
        )
    }

    addingPartSlotKey?.let { slotKey ->
        val slot = lab.brief?.slot(slotKey)
        AddPartDialog(
            slotHeading = slot?.heading ?: "Section",
            onDismiss = { addingPartSlotKey = null },
            onAdd = { label, value ->
                vm.addPartToSlot(slotKey, label, value)
                addingPartSlotKey = null
            },
        )
    }

    partVariations?.let { variations ->
        PartVariationsDialog(
            variations = variations,
            onDismiss = { vm.dismissPartVariations() },
            onSelect = { selectedOption ->
                vm.selectPartVariation(variations.partKey, selectedOption)
            },
        )
    }
}

private enum class Mode(val label: String) {
    BUILDER("AI Builder"),
    GENERATE("Offline"),
    LIBRARY("Saved"),
    IMAGE("Image Gen"),
}

@Composable
private fun ImagePane(vm: EmberViewModel) {
    val context = LocalContext.current
    val veniceHasKey by vm.settings.veniceHasKey.collectAsStateWithLifecycle()
    val veniceImage by vm.veniceImage.collectAsStateWithLifecycle()
    val veniceBusy by vm.veniceBusy.collectAsStateWithLifecycle()
    val veniceError by vm.veniceError.collectAsStateWithLifecycle()

    var prompt by remember { mutableStateOf("") }
    var formatExpanded by remember { mutableStateOf(false) }
    var modelOption by remember { mutableStateOf("lustify-v8") }
    val modelOptions = listOf("lustify-v8", "lustify-sdxl", "venice-sd35", "grok-imagine-image")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Venice.ai Image Generator", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Generate character portraits or scene art directly into your media library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Image Prompt") },
                        placeholder = { Text("e.g. A cyberpunk hacker in a neon-lit room, photorealistic") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { formatExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Model: $modelOption")
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = formatExpanded,
                            onDismissRequest = { formatExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            modelOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = { 
                                        modelOption = opt
                                        formatExpanded = false 
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { 
                            vm.generateVeniceImage(prompt, modelOption)
                        },
                        enabled = veniceHasKey && !veniceBusy && prompt.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Generate Image")
                    }
                    if (!veniceHasKey) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Venice API key is missing. Add it in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (veniceBusy) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if (veniceError.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            veniceError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        if (veniceImage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        androidx.compose.foundation.Image(
                            bitmap = veniceImage!!.asImageBitmap(),
                            contentDescription = "Generated image",
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            contentScale = ContentScale.Crop
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(onClick = { vm.saveVeniceImage(context) }) {
                                Icon(Icons.Filled.Save, null, Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save to Library")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuilderPane(
    vm: EmberViewModel,
    aiEnabled: Boolean,
    aiHasKey: Boolean,
    labViewMode: LabViewMode,
    smartRerollBusyKey: String?,
    onSave: () -> Unit,
    onExportCard: () -> Unit,
    onImportCard: () -> Unit,
    onPlatformFields: () -> Unit,
    onExportPersonaForge: () -> Unit,
    onEditPart: (Part) -> Unit,
    onEditTitle: () -> Unit,
    onAddPart: (String) -> Unit,
) {
    val context = LocalContext.current
    val lab by vm.lab.collectAsStateWithLifecycle()
    val pfBusy by vm.personaForgeAiBusy.collectAsStateWithLifecycle()
    var prompt by remember { mutableStateOf("") }
    var formatExpanded by remember { mutableStateOf(false) }
    var formatOption by remember { mutableStateOf("PersonaForge Scenario") }
    var customFormat by remember { mutableStateOf("") }
    
    val formatOptions = listOf("PersonaForge Scenario", "Full Scenario", "Character Card", "Story Beat", "Worldbuilding Lore", "Custom...")
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("AI Scenario Wizard", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Describe what you want to create and let AI build the structure for you. " +
                        "Once generated, you can directly edit, refine, or export it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("What do you want to create?") },
                        placeholder = { Text("e.g. A cyberpunk heist where the crew turns on each other") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { formatExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Format: $formatOption")
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = formatExpanded,
                            onDismissRequest = { formatExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            formatOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = { 
                                        formatOption = opt
                                        formatExpanded = false 
                                    }
                                )
                            }
                        }
                    }
                    
                    if (formatOption == "Custom...") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customFormat,
                            onValueChange = { customFormat = it },
                            label = { Text("Describe your custom format") },
                            placeholder = { Text("e.g. 5 bullet points, dark tone, emphasize worldbuilding") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    if (formatOption == "PersonaForge Scenario") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { 
                                    vm.brainstormPersonaForgeWithAi(premise = prompt)
                                },
                                enabled = aiEnabled && aiHasKey && !lab.aiBusy && !pfBusy,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                ),
                            ) {
                                if (pfBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(17.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onTertiary,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Brainstorming...", style = MaterialTheme.typography.labelMedium)
                                } else {
                                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Brainstorm for PersonaForge", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    vm.selectSchema("personaforge")
                                    onPlatformFields()
                                },
                            ) {
                                Icon(Icons.Filled.Checklist, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Fields")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { 
                                    val finalTone = if (formatOption == "Custom...") customFormat else formatOption
                                    vm.generateAiScenario(premise = prompt, tone = finalTone) 
                                },
                                enabled = aiEnabled && aiHasKey && !lab.aiBusy && !pfBusy && prompt.isNotBlank(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Generate Scenario")
                            }
                            Button(
                                onClick = { vm.brainstormPersonaForgeWithAi(premise = prompt) },
                                enabled = aiEnabled && aiHasKey && !lab.aiBusy && !pfBusy,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                ),
                            ) {
                                if (pfBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onTertiary,
                                    )
                                } else {
                                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(17.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("PersonaForge")
                                }
                            }
                        }
                    }
                    if (!aiEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "AI assist is off — turn it on in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (!aiHasKey) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No API key saved yet — add one in Settings to enable generation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (lab.aiBusy || pfBusy) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if (lab.aiError.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            lab.aiError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        
        val currentBrief = lab.brief
        if (currentBrief != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onEditTitle() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            currentBrief.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        IconButton(onClick = onEditTitle, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Edit title",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = labViewMode == LabViewMode.READING,
                            onClick = { vm.setLabViewMode(LabViewMode.READING) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reading", style = MaterialTheme.typography.labelSmall)
                        }
                        SegmentedButton(
                            selected = labViewMode == LabViewMode.TUNING,
                            onClick = { vm.setLabViewMode(LabViewMode.TUNING) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tuning", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (labViewMode == LabViewMode.READING) {
                item {
                    ReadingViewCard(
                        brief = currentBrief,
                        onSave = onSave,
                        onShare = { vm.openExport(currentBrief.text, currentBrief.title) },
                        onExportPersonaForge = onExportPersonaForge,
                        onSwitchToTuning = { vm.setLabViewMode(LabViewMode.TUNING) },
                        onEditPart = onEditPart,
                        onEditTitle = onEditTitle,
                        onAddPart = onAddPart,
                    )
                }
            } else {
                tuningSlotsContent(
                    vm = vm,
                    brief = currentBrief,
                    smartRerollBusyKey = smartRerollBusyKey,
                    onSave = onSave,
                    onShare = { vm.openExport(currentBrief.text, currentBrief.title) },
                    onExportPersonaForge = onExportPersonaForge,
                    onPlatformFields = onPlatformFields,
                    onExportCard = onExportCard,
                    onImportCard = onImportCard,
                    onEditPart = onEditPart,
                    onAddPart = onAddPart,
                )
            }
        }
    }
}


@Composable
private fun ReadingViewCard(
    brief: com.ember.companion.data.Brief,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onExportPersonaForge: () -> Unit,
    onSwitchToTuning: () -> Unit,
    onEditPart: (Part) -> Unit,
    onEditTitle: () -> Unit,
    onAddPart: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEditTitle() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        brief.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit title",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    )
                }
                OutlinedButton(
                    onClick = onSwitchToTuning,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tune", style = MaterialTheme.typography.labelSmall)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            brief.slots.filter { slot -> slot.parts.any { !it.hidden } }.forEach { slot ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            slot.heading.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        TextButton(
                            onClick = { onAddPart(slot.key) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp),
                        ) {
                            Icon(Icons.Filled.Add, null, Modifier.size(12.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Add", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    slot.parts.filter { !it.hidden && it.value.isNotBlank() }.forEach { part ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditPart(part) }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = buildString {
                                    if (part.label.isNotBlank()) append("${part.label}: ")
                                    append(part.value)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Edit line",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.LibraryBooks, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save to Lab")
                }
                OutlinedButton(
                    onClick = onShare,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Share, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
                Button(
                    onClick = onExportPersonaForge,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                ) {
                    Icon(Icons.Filled.Upload, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PersonaForge")
                }
            }
        }
    }
}

@Composable
private fun GeneratePane(
    vm: EmberViewModel,
    brief: com.ember.companion.data.Brief?,
    aiEnabled: Boolean,
    aiHasKey: Boolean,
    labViewMode: LabViewMode,
    smartRerollBusyKey: String?,
    steeringExpanded: Boolean,
    onSave: () -> Unit,
    onExportCard: () -> Unit,
    onImportCard: () -> Unit,
    onPlatformFields: () -> Unit,
    onExportPersonaForge: () -> Unit,
    onEditPart: (Part) -> Unit,
    onEditTitle: () -> Unit,
    onAddPart: (String) -> Unit,
) {
    val context = LocalContext.current
    val lab by vm.lab.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Offline by design", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "These generators are a combinatorial engine running on your phone. " +
                            "No network, no account, nothing recorded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SteeringCard(
                dials = lab.dials,
                premise = lab.premise,
                seed = lab.seed,
                pinnedCount = lab.taste.pinned.size,
                blockedCount = lab.taste.blocked.size,
                expanded = steeringExpanded,
                onToggleExpand = vm::toggleSteeringExpanded,
                onDial = vm::setDial,
                onPremise = vm::setPremise,
                onSeed = vm::setSeed,
                onNewSeed = vm::newSeed,
                onClearTaste = vm::clearTaste,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.generate(GenerateKind.BRIEF) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Full brief")
                }
                OutlinedButton(onClick = { vm.generate(GenerateKind.CHARACTER) }) {
                    Text("Character")
                }
                OutlinedButton(onClick = { vm.generate(GenerateKind.BEAT) }) {
                    Text("Beat")
                }
            }
        }

        if (brief == null) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Hit Full brief to get a cast, a setting, a frame, an opening line, " +
                            "four beats and an optional twist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onEditTitle() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            brief.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        IconButton(onClick = onEditTitle, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Edit title",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = labViewMode == LabViewMode.READING,
                            onClick = { vm.setLabViewMode(LabViewMode.READING) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reading", style = MaterialTheme.typography.labelSmall)
                        }
                        SegmentedButton(
                            selected = labViewMode == LabViewMode.TUNING,
                            onClick = { vm.setLabViewMode(LabViewMode.TUNING) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tuning", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (labViewMode == LabViewMode.READING) {
                item {
                    ReadingViewCard(
                        brief = brief,
                        onSave = onSave,
                        onShare = { vm.openExport(brief.text, brief.title) },
                        onExportPersonaForge = onExportPersonaForge,
                        onSwitchToTuning = { vm.setLabViewMode(LabViewMode.TUNING) },
                        onEditPart = onEditPart,
                        onEditTitle = onEditTitle,
                        onAddPart = onAddPart,
                    )
                }
            } else {
                tuningSlotsContent(
                    vm = vm,
                    brief = brief,
                    smartRerollBusyKey = smartRerollBusyKey,
                    onSave = onSave,
                    onShare = { vm.openExport(brief.text, brief.title) },
                    onExportPersonaForge = onExportPersonaForge,
                    onPlatformFields = onPlatformFields,
                    onExportCard = onExportCard,
                    onImportCard = onImportCard,
                    onEditPart = onEditPart,
                    onAddPart = onAddPart,
                )
            }
        }

        item { HorizontalDivider() }

        item {
            Text(
                "AI ASSIST",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    if (!aiEnabled) {
                        Text("AI assist is off", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Nothing above left your device. Turn on AI assist in Settings if " +
                                "you want to push a brief through a language model using your own key.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("Push this brief further", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Requests go straight from your phone to the provider you chose. " +
                                "Ember has no server in the middle and no account of yours.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = lab.aiPrompt,
                        onValueChange = { vm.setAiPrompt(it) },
                        label = { Text("Instruction to the model") },
                        placeholder = { Text("e.g. make the second character want it more") },
                        minLines = 2,
                        // The IME action and the Send button are the whole point of this
                        // field: it used to have neither, so anything typed here was
                        // unreachable and the text was silently dropped on send.
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { vm.sendAiPrompt() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { vm.sendAiPrompt() },
                            enabled = aiEnabled && aiHasKey && !lab.aiBusy &&
                                lab.aiPrompt.isNotBlank(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Send")
                        }
                        TextButton(onClick = { vm.setAiPrompt("") }, enabled = lab.aiPrompt.isNotBlank()) {
                            Text("Clear")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Suggestions fill the box above — edit them, then hit Send.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (lab.aiHistory.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "In conversation · ${lab.aiHistory.size} turn" +
                                    (if (lab.aiHistory.size == 1) "" else "s"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = vm::clearAiHistory, enabled = !lab.aiBusy) {
                                Text("Start over", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(vm.aiActionLabels()) { label ->
                            AssistChip(
                                onClick = { vm.stageAiAction(label) },
                                enabled = !lab.aiBusy,
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Reply length",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Short" to 250, "Medium" to 800, "Long" to 2000).forEach { (name, tokens) ->
                            FilterChip(
                                selected = lab.aiMaxTokens == tokens,
                                onClick = { vm.setAiMaxTokens(tokens) },
                                label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                            )
                        }
                    }
                    if (!aiEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "AI assist is off — turn it on in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (!aiHasKey) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No API key saved yet — add one in Settings to enable Send.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (lab.aiBusy) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Talking to the provider…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (lab.aiError.isNotBlank()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            lab.aiError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.clearAiError() }) {
                            Icon(Icons.Filled.Close, "Dismiss", Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        if (lab.aiOutput.isNotBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Response", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(lab.aiOutput, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.appendAiOutputToBrief() },
                                enabled = brief != null,
                            ) {
                                Text("Append to brief")
                            }
                            Button(
                                onClick = { vm.replaceBriefWithAiOutput() },
                                enabled = brief != null,
                            ) {
                                Text("Replace entire brief")
                            }
                            OutlinedButton(
                                onClick = { vm.copyAiOutput(context) },
                            ) {
                                Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy")
                            }
                            OutlinedButton(onClick = { vm.openExport(lab.aiOutput, "AI notes") }) {
                                Icon(Icons.Filled.Share, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

/**
 * The controls that make a brief aimable. Each dial is a three-stop choice rather
 * than a free slider because the underlying banks are ordered lists: the stop has
 * to correspond to a real position in the bank, otherwise the dial would be
 * decorative.
 */
@Composable
private fun SteeringCard(
    dials: Dials,
    premise: String,
    seed: String,
    pinnedCount: Int,
    blockedCount: Int,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDial: (explicitness: Int?, pace: Int?, power: Int?, pov: Int?) -> Unit,
    onPremise: (String) -> Unit,
    onSeed: (String) -> Unit,
    onNewSeed: () -> Unit,
    onClearTaste: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Tune, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Steering & Dials", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (pinnedCount + blockedCount > 0) {
                    Text(
                        "$pinnedCount pinned · $blockedCount blocked",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onClearTaste, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            "Clear pins and blocks",
                            Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = onToggleExpand, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse steering" else "Expand steering",
                    )
                }
            }

            if (!expanded) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = onToggleExpand,
                        label = { Text("Explicitness: ${Dials.EXPLICITNESS.getOrElse(dials.explicitness) { "Moderate" }}", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                    )
                    AssistChip(
                        onClick = onToggleExpand,
                        label = { Text("Pace: ${Dials.PACE.getOrElse(dials.pace) { "Balanced" }}", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                    )
                    AssistChip(
                        onClick = onToggleExpand,
                        label = { Text("Power: ${Dials.POWER.getOrElse(dials.power) { "Equal" }}", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                    )
                    AssistChip(
                        onClick = onToggleExpand,
                        label = { Text("POV: ${Dials.POV.getOrElse(dials.pov) { "Third" }}", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                    )
                    if (premise.isNotBlank()) {
                        AssistChip(
                            onClick = onToggleExpand,
                            label = { Text("\"${premise.take(18)}…\"", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))

                DialRow("Explicitness", Dials.EXPLICITNESS, dials.explicitness) {
                    onDial(it, null, null, null)
                }
                DialRow("Pace", Dials.PACE, dials.pace) {
                    onDial(null, it, null, null)
                }
                DialRow("Power", Dials.POWER, dials.power) {
                    onDial(null, null, it, null)
                }
                DialRow("POV", Dials.POV, dials.pov) {
                    onDial(null, null, null, it)
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = premise,
                    onValueChange = onPremise,
                    label = { Text("Premise / seed idea") },
                    placeholder = { Text("e.g. a hotel bar, one of them is leaving in the morning") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Name a place, a role or a framing and the generator will use it " +
                        "instead of rolling its own. The same premise always rebuilds " +
                        "the same brief.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = seed,
                        onValueChange = onSeed,
                        label = { Text("Seed") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onNewSeed) { Text("Reroll seed") }
                }
            }
        }
    }
}

@Composable
private fun DialRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 3.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { index, option ->
                FilterChip(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    label = { Text(option, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One addressable line. Reroll, lock, pin and block are clean and decluttered.
 * Action icons (reroll, lock) are placed inline, with an overflow dropdown menu
 * for Pin, Block, and AI suggestions.
 */
@Composable
private fun EditPartDialog(
    part: Part,
    vm: EmberViewModel,
    onDismiss: () -> Unit,
    onSave: (label: String, value: String) -> Unit,
    onSmartReroll: () -> Unit,
    onVariations: () -> Unit,
) {
    var label by remember { mutableStateOf(part.label) }
    var value by remember { mutableStateOf(part.value) }
    var customRefinePrompt by remember { mutableStateOf("") }
    var isRefining by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Edit, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(if (label.isNotBlank()) "Edit $label" else "Edit Detail")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Field Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Content / Value") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    "AI Refine:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val presets = listOf("More Detail", "Darker", "Punchier", "Seductive", "Emotional", "More Tense")
                    items(presets) { preset ->
                        AssistChip(
                            onClick = {
                                if (!isRefining && value.isNotBlank()) {
                                    isRefining = true
                                    scope.launch {
                                        val refined = vm.refineTextWithAi(value, preset, label.ifBlank { "field" })
                                        if (refined.isNotBlank()) value = refined
                                        isRefining = false
                                    }
                                }
                            },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = customRefinePrompt,
                        onValueChange = { customRefinePrompt = it },
                        placeholder = { Text("Custom AI tweak (e.g. make it ironic)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (!isRefining && customRefinePrompt.isNotBlank() && value.isNotBlank()) {
                                isRefining = true
                                scope.launch {
                                    val refined = vm.refineTextWithAi(value, customRefinePrompt, label.ifBlank { "field" })
                                    if (refined.isNotBlank()) {
                                        value = refined
                                        customRefinePrompt = ""
                                    }
                                    isRefining = false
                                }
                            }
                        },
                        enabled = !isRefining && customRefinePrompt.isNotBlank() && value.isNotBlank(),
                    ) {
                        if (isRefining) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.AutoAwesome, "Refine with AI", Modifier.size(18.dp))
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(
                        onClick = onSmartReroll,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Smart Reroll", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onVariations,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.Lightbulb, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("3 Variations", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(label, value) },
                enabled = value.isNotBlank() && !isRefining,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun EditTitleDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var title by remember { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Scenario Title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title) },
                enabled = title.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AddPartDialog(
    slotHeading: String,
    onDismiss: () -> Unit,
    onAdd: (label: String, value: String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Detail to $slotHeading") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (e.g. Voice, Habit, Secret, Twist)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Detail Content") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(label, value) },
                enabled = value.isNotBlank(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PartVariationsDialog(
    variations: EmberViewModel.PartVariationsState,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lightbulb, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text("Logical Variations for ${variations.partLabel}")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Current: \"${variations.currentValue}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (variations.busy) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Text("Brainstorming 3 logical alternatives...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (variations.error != null) {
                    Text(
                        variations.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    variations.options.forEachIndexed { index, option ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option) },
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${index + 1}.",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    option,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun LazyListScope.tuningSlotsContent(
    vm: EmberViewModel,
    brief: com.ember.companion.data.Brief,
    smartRerollBusyKey: String?,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onExportPersonaForge: () -> Unit,
    onPlatformFields: () -> Unit,
    onExportCard: () -> Unit,
    onImportCard: () -> Unit,
    onEditPart: (Part) -> Unit,
    onAddPart: (String) -> Unit,
) {
    items(brief.slots.filter { slot -> slot.parts.any { !it.hidden } }) { slot ->
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        slot.heading.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (slot.locked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { vm.smartRerollSlot(slot.key) },
                        enabled = !slot.locked,
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Reroll ${slot.heading}",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = { vm.lockSlot(slot.key) }) {
                        Icon(
                            if (slot.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = if (slot.locked) "Unlock" else "Lock",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                slot.parts.filter { !it.hidden }.forEach { part ->
                    if (part.value.isNotEmpty()) {
                        PartRow(
                            part = part,
                            canReroll = !part.locked && !slot.locked,
                            isRerolling = smartRerollBusyKey == part.key,
                            onEdit = { onEditPart(part) },
                            onReroll = { vm.smartRerollPart(part.key) },
                            onBankReroll = if (part.bank != null) { { vm.rerollPart(part.key) } } else null,
                            onVariations = { vm.requestPartVariations(part.key) },
                            onLock = { vm.lockPart(part.key) },
                            onPin = { vm.pinPart(part.key) },
                            onBlock = { vm.blockPart(part.key) },
                            onDelete = { vm.removePart(part.key) },
                            onApplyAi = if (part.bank != null) {
                                { vm.applyAiToPart(part.key) }
                            } else null,
                            hasAiOutput = false,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { onAddPart(slot.key) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add detail", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.LibraryBooks, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save to Lab")
            }
            OutlinedButton(onClick = onShare) {
                Icon(Icons.Filled.Share, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
            Button(
                onClick = onExportPersonaForge,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            ) {
                Icon(Icons.Filled.Upload, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("PersonaForge")
            }
        }
    }

    item {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Character card & Platform Export", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Send this brief to a roleplay platform as a character card or PNG chunks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onPlatformFields,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Checklist, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Platform fields")
                    }
                    OutlinedButton(
                        onClick = onExportCard,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Share, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export card")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onImportCard,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import card")
                    }
                }
            }
        }
    }
}

/**
 * One addressable line. Directly clickable to edit, with inline edit pencil,
 * smart AI refresh, variations brainstorm, and overflow actions.
 */
@Composable
private fun PartRow(
    part: Part,
    canReroll: Boolean,
    isRerolling: Boolean,
    onEdit: () -> Unit,
    onReroll: () -> Unit,
    onBankReroll: (() -> Unit)?,
    onVariations: () -> Unit,
    onLock: () -> Unit,
    onPin: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit,
    onApplyAi: (() -> Unit)?,
    hasAiOutput: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                buildString {
                    if (part.label.isNotBlank()) append("${part.label}: ")
                    append(part.value)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (part.locked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (part.locked) {
                Text(
                    "Locked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Edit,
                "Edit line",
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isRerolling) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = onReroll, enabled = canReroll, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Refresh,
                    "Smart reroll",
                    Modifier.size(17.dp),
                )
            }
        }
        IconButton(onClick = onLock, modifier = Modifier.size(32.dp)) {
            Icon(
                if (part.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                if (part.locked) "Unlock this line" else "Lock this line",
                tint = if (part.locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    "More options",
                    Modifier.size(17.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit line") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Smart AI Reroll") },
                    leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp)) },
                    onClick = {
                        menuOpen = false
                        onReroll()
                    },
                    enabled = canReroll,
                )
                DropdownMenuItem(
                    text = { Text("Brainstorm 3 Options") },
                    leadingIcon = { Icon(Icons.Filled.Lightbulb, null, Modifier.size(18.dp)) },
                    onClick = {
                        menuOpen = false
                        onVariations()
                    },
                )
                if (onBankReroll != null) {
                    DropdownMenuItem(
                        text = { Text("Roll from Bank (Random)") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)) },
                        onClick = {
                            menuOpen = false
                            onBankReroll()
                        },
                        enabled = canReroll,
                    )
                }
                DropdownMenuItem(
                    text = { Text("Pin value") },
                    leadingIcon = { Icon(Icons.Filled.PushPin, null, Modifier.size(18.dp)) },
                    onClick = {
                        menuOpen = false
                        onPin()
                    },
                    enabled = part.bank != null,
                )
                DropdownMenuItem(
                    text = { Text("Block value") },
                    leadingIcon = { Icon(Icons.Filled.Block, null, Modifier.size(18.dp)) },
                    onClick = {
                        menuOpen = false
                        onBlock()
                    },
                    enabled = part.bank != null,
                )
                DropdownMenuItem(
                    text = { Text("Delete line") },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
                if (onApplyAi != null) {
                    DropdownMenuItem(
                        text = { Text("Use AI text here") },
                        leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp)) },
                        onClick = {
                            menuOpen = false
                            onApplyAi()
                        },
                        enabled = hasAiOutput,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryPane(
    vm: EmberViewModel,
    scenarios: List<Scenario>,
    query: String,
    totalCount: Int,
    onEdit: (Scenario) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.scenarioQuery.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Search scenarios") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.scenarioQuery.value = "" }) {
                        Icon(Icons.Filled.Close, "Clear")
                    }
                }
            },
        )

        if (scenarios.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (totalCount == 0) {
                        "Nothing saved yet. Generate a brief and hit Save to Lab."
                    } else {
                        "No scenario matches \"$query\"."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(scenarios, key = { it.id }) { scenario ->
                    Card(
                        onClick = { onEdit(scenario) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    scenario.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(onClick = { vm.toggleScenarioFavorite(scenario) }) {
                                    Icon(
                                        if (scenario.favorite) {
                                            Icons.Filled.Favorite
                                        } else {
                                            Icons.Filled.FavoriteBorder
                                        },
                                        contentDescription = "Favourite",
                                        tint = if (scenario.favorite) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                scenario.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (scenario.tagList.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    scenario.tagList.take(4).forEach { tag ->
                                        Text(
                                            "#$tag",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveBriefDialog(
    defaultTitle: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf(defaultTitle) }
    var tags by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Save scenario", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags (comma separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(title, tags) }) { Text("Save") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScenarioEditorDialog(
    scenario: Scenario,
    onDismiss: () -> Unit,
    onSave: (Scenario) -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    var title by remember(scenario.id) { mutableStateOf(scenario.title) }
    var body by remember(scenario.id) { mutableStateOf(scenario.body) }
    var tags by remember(scenario.id) { mutableStateOf(scenario.tags) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Edit scenario", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Body") },
                minLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share")
                    }
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = {
                        onSave(scenario.copy(title = title.trim(), body = body, tags = tags.trim()))
                    }) { Text("Save") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Lets the user extend any generator bank without leaving the Lab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BanksDialog(
    vm: EmberViewModel,
    custom: Banks.Custom,
    onDismiss: () -> Unit,
) {
    val banks = listOf(
        "traits" to Banks.traits,
        "wants" to Banks.wants,
        "fears" to Banks.fears,
        "secrets" to Banks.secrets,
        "flaws" to Banks.flaws,
        "places" to Banks.places,
        "times" to Banks.timesOfDay,
        "weather" to Banks.weather,
        "atmosphere" to Banks.atmospheres,
        "openers" to Banks.openers,
        "escalations" to Banks.escalations,
        "complications" to Banks.complications,
        "turns" to Banks.turns,
        "twists" to Banks.twists,
        "closers" to Banks.closers,
        "sensory" to Banks.sensory,
    )

    var selected by remember { mutableStateOf(banks.first().first) }
    var entry by remember { mutableStateOf("") }
    val defaults = banks.first { it.first == selected }.second

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Word banks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            Text(
                "Ember ships with ${banks.size} banks. Add your own lines to any of them and " +
                    "they get folded into every future generation. Tap a bank to select it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(banks) { (id, _) ->
                    FilterChip(
                        selected = selected == id,
                        onClick = { selected = id },
                        label = { Text(id, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = entry,
                    onValueChange = { entry = it },
                    label = { Text("Add a line to \"$selected\"") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        vm.addBankEntry(selected, entry)
                        entry = ""
                    },
                    enabled = entry.isNotBlank(),
                ) {
                    Icon(Icons.Filled.Add, "Add")
                }
            }
            Text(
                "In this generation: ${custom.forBank(selected, defaults).toSet().size} unique lines " +
                    "(${custom.byBank[selected]?.size ?: 0} yours, heavily weighted)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (custom.byBank[selected]?.isNotEmpty() == true) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    custom.byBank[selected].orEmpty().forEach { line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { vm.removeBankEntry(selected, line) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    "Remove this line",
                                    Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = onDismiss) { Text("Done") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

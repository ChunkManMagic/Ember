package com.ember.companion.ui.lab

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.companion.data.CardFields
import com.ember.companion.data.CardFormat
import com.ember.companion.data.CardPlatforms
import com.ember.companion.data.CharacterCard
import com.ember.companion.ui.EmberViewModel

/**
 * Export sheet.
 *
 * The platform list is not a formality. Chub swaps two core fields, Agnai blanks
 * personality on purpose, and Character.AI and JanitorAI have no card import at
 * all, so for those the sheet shows the real field names and limits to copy by
 * hand instead of producing a file the target could never read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardExportDialog(
    vm: EmberViewModel,
    onDismiss: () -> Unit,
) {
    val extras by vm.cardExtras.collectAsStateWithLifecycle()
    val card = remember(extras) { vm.currentCard() }
    val platform = CardPlatforms.byId(extras.platformId)
    var showExtras by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Export as character card", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(8.dp))

            Text(
                "TARGET",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            CardPlatforms.ALL.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterChip(
                        selected = !extras.useCustom && option.id == extras.platformId,
                        onClick = { vm.setCardPlatform(option.id) },
                        label = { Text(option.label) },
                    )
                }
            }
            FilterChip(
                selected = extras.useCustom,
                onClick = { vm.setUseCustomMapping(true) },
                label = { Text("Custom fields") },
            )

            Spacer(Modifier.height(10.dp))

            if (extras.useCustom) {
                CustomMappingEditor(vm)
            } else {
                Text(
                    platform.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!platform.copyOnly) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "CARD VERSION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        CharacterCard.Spec.entries.forEach { spec ->
                            FilterChip(
                                selected = extras.spec == spec,
                                onClick = { vm.setCardSpec(spec) },
                                label = { Text(spec.label) },
                            )
                        }
                    }
                    Text(
                        "Saves as ${platform.format.label}, ${extras.spec.label}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                "NAME: ${card.name.ifBlank { "(unnamed)" }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            if (extras.useCustom) {
                Text(
                    extras.custom.toJsonText(card),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (platform.copyOnly) {
                CopySheet(vm, card, platform)
            } else {
                CardSummary(card, platform)
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { showExtras = !showExtras }) {
                Text(if (showExtras) "Hide extra fields" else "Add extra fields")
            }
            if (showExtras) {
                ExtrasEditor(vm)
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = {
                        if (extras.useCustom || !platform.copyOnly) {
                            vm.requestCardSave()
                        }
                        onDismiss()
                    },
                    enabled = card.name.isNotBlank() || card.description.isNotBlank(),
                ) {
                    Text(
                        when {
                            extras.useCustom -> "Save custom JSON"
                            platform.copyOnly -> "Done"
                            else -> "Save card"
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CardSummary(
    card: CharacterCard.Card,
    platform: CardPlatforms.Platform,
) {
    val adapted = platform.adapt(card)
    Column {
        listOf(
            "Description" to adapted.description,
            "Personality" to adapted.personality,
            "Scenario" to adapted.scenario,
            "First message" to adapted.firstMessage,
        ).forEach { (label, value) ->
            if (value.isNotBlank()) {
                Text(
                    "$label (${value.length})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value.take(160).replace("\n", " "),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/** Field-by-field copy for platforms with no import endpoint. */
@Composable
private fun CopySheet(
    vm: EmberViewModel,
    card: CharacterCard.Card,
    platform: CardPlatforms.Platform,
) {
    val adapted = platform.adapt(card)
    val rows = if (platform.id == "characterai") {
        listOf(
            "Name" to adapted.name,
            "Tagline (50 max)" to adapted.personality,
            "Short description (500 max)" to adapted.description,
            "Greeting" to adapted.firstMessage,
            "Definition (32,000 max)" to adapted.exampleDialogue,
        )
    } else {
        listOf(
            "Name" to adapted.name,
            "Description" to adapted.description,
            "Personality" to adapted.personality,
            "Scenario" to adapted.scenario,
            "First message" to adapted.firstMessage,
            "Example dialogs" to adapted.exampleDialogue,
        )
    }
    Column {
        Text(
            "This platform has no card import. Copy each field into its editor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        rows.filter { it.second.isNotBlank() }.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "$label · ${value.length} chars",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(value.take(120).replace("\n", " "), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { vm.copyCardField(value) }) {
                    Icon(Icons.Filled.ContentCopy, "Copy $label", Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ExtrasEditor(vm: EmberViewModel) {
    val extras by vm.cardExtras.collectAsStateWithLifecycle()
    Column {
        OutlinedTextField(
            value = extras.tags.joinToString(", "),
            onValueChange = vm::setCardTags,
            label = { Text("Tags (comma separated)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = extras.alternateGreetings.joinToString("\n"),
            onValueChange = vm::setCardAlternateGreetings,
            label = { Text("Alternate greetings (one per line)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = extras.exampleDialogue,
            onValueChange = vm::setCardExampleDialogue,
            label = { Text("Example dialogue") },
            placeholder = { Text("{{user}}: ...\n{{char}}: ...") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = extras.systemPrompt,
            onValueChange = vm::setCardSystemPrompt,
            label = { Text("System prompt") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = extras.postHistoryInstructions,
            onValueChange = vm::setCardPostHistory,
            label = { Text("Post-history instructions") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CustomMappingEditor(vm: EmberViewModel) {
    val extras by vm.cardExtras.collectAsStateWithLifecycle()
    Column {
        Text(
            "Name the fields your target expects and point each one at a source. " +
                "Blank targets are skipped, so you control the output shape exactly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        Text(
            "CARD VERSION",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CharacterCard.Spec.entries.forEach { spec ->
                FilterChip(
                    selected = extras.custom.spec == spec,
                    onClick = { vm.setCustomSpec(spec) },
                    label = { Text(spec.version) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "FILE FORMAT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(CardFormat.JSON, CardFormat.PNG).forEach { format ->
                FilterChip(
                    selected = extras.custom.format == format,
                    onClick = { vm.setCustomFormat(format) },
                    label = { Text(format.label) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        extras.custom.rows.forEachIndexed { index, row ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = row.target,
                            onValueChange = { vm.setCustomRow(index, it, row.source) },
                            label = { Text("Target field") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.removeCustomRow(index) }) {
                            Icon(Icons.Filled.Delete, "Remove field", Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        CardFields.ALL.forEach { (source, label) ->
                            FilterChip(
                                selected = row.source == source,
                                onClick = { vm.setCustomRow(index, row.target, source) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(onClick = vm::addCustomRow) {
            Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add field")
        }
    }
}

/** Import sheet: explains the formats accepted and opens the picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardImportDialog(vm: EmberViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Import a character card", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Opens a PNG, JSON or .charx card. The ccv3 chunk wins over chara " +
                    "when a PNG carries both, and compressed tEXt chunks are read " +
                    "as well as plain ones.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "The card becomes an editable brief, so you can reroll and steer " +
                    "it like a generated one. Fields with no slot of their own " +
                    "are kept intact rather than being dropped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { vm.requestCardOpen(); onDismiss() }) { Text("Choose file") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

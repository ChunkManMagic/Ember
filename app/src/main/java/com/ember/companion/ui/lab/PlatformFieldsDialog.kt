package com.ember.companion.ui.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.companion.data.CardPlatformSchemas
import com.ember.companion.data.FieldType
import com.ember.companion.data.SchemaField
import com.ember.companion.ui.EmberViewModel

/**
 * The platform field editor.
 *
 * This is the answer to "I found the platform I'm targeting, now what does it
 * want?". Pick a platform and the sheet shows that platform's real field names,
 * in its order, with its caps — so brainstorming in the Lab and filling the
 * platform's form are the same screen instead of two disconnected steps.
 *
 * Nothing is exported from here. Ember still produces the same files as before;
 * this just means you arrive at them knowing what's required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformFieldsDialog(
    vm: EmberViewModel,
    onDismiss: () -> Unit,
) {
    val schemas by vm.allSchemas.collectAsStateWithLifecycle()
    val selectedId by vm.selectedSchemaId.collectAsStateWithLifecycle()
    val schema by vm.selectedSchema.collectAsStateWithLifecycle()
    val values by vm.fieldValues.collectAsStateWithLifecycle()
    val fetchBusy by vm.fetchBusy.collectAsStateWithLifecycle()
    val fetchError by vm.fetchError.collectAsStateWithLifecycle()
    val payload by vm.exportPayload.collectAsStateWithLifecycle()

    var showAddField by remember { mutableStateOf(false) }
    var fetchUrl by remember { mutableStateOf("") }

    val current = schema ?: return

    // Bound height: a ModalBottomSheet will happily grow past the screen, and
    // an unbounded scroll column inside one pushes its own close button off the
    // top, leaving no way out. Capping it keeps the sheet scrollable AND keeps
    // the header and Close button reachable at every scroll position.
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.imePadding()) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            // Fixed header: title + Close stay visible while the body scrolls.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Platform fields", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Fill in what the target actually asks for",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
            Spacer(Modifier.height(14.dp))

            // --- platform picker -------------------------------------------
            Text(
                "PLATFORM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                schemas.forEach { option ->
                    FilterChip(
                        selected = option.id == selectedId,
                        onClick = { vm.selectSchema(option.id) },
                        label = {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                current.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Copy-only and lossy platforms are worth flagging before you invest
            // an hour in filling boxes that can't be exported as a file.
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                if (current.copyOnly) {
                    AssistChip(onClick = {}, label = { Text("Copy fields by hand") })
                }
                if (current.lossy) {
                    AssistChip(onClick = {}, label = { Text("Some fields dropped") })
                }
                AssistChip(onClick = {}, label = { Text("${current.spec.version}") })
            }

            Spacer(Modifier.height(12.dp))

            if (selectedId == "personaforge") {
                val pfBusy by vm.personaForgeAiBusy.collectAsStateWithLifecycle()
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "PersonaForge Scenario Creator",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Use AI to brainstorm all PersonaForge fields at once, or export directly to PersonaForge Android & Web.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = { vm.brainstormPersonaForgeWithAi() },
                                enabled = !pfBusy,
                                modifier = Modifier.weight(1f),
                            ) {
                                if (pfBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Generating...", style = MaterialTheme.typography.labelMedium)
                                } else {
                                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("AI Brainstorm", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Button(
                                onClick = { vm.openInPersonaForgeDirect() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                ),
                            ) {
                                Icon(Icons.Filled.Upload, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open in App", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // --- actions ----------------------------------------------------
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = vm::fillFieldsFromBrief,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Fill from brief", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = { showAddField = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add field", style = MaterialTheme.typography.labelMedium)
                }
            }

            val filled = current.fields.count { !values[it.key].isNullOrBlank() }
            val missing = current.missingRequired(values)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$filled of ${current.fields.size} fields filled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::clearSelectedDraft) {
                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                }
            }
            LinearProgressIndicator(
                progress = { current.completeness(values) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
            if (missing.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Required and still empty: ${missing.joinToString { it.label }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider()

            // --- fields -----------------------------------------------------
            current.fields.forEach { field ->
                FieldEditor(
                    field = field,
                    value = values[field.key].orEmpty(),
                    onChange = { vm.setFieldValue(field.key, it) },
                    onRemove = if (field.custom) {
                        { vm.removeCustomField(field.key) }
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(10.dp))
            }

            if (current.fields.isEmpty()) {
                Text(
                    "This platform has no fields yet. Add one, or fetch the real form below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))

            // --- fetch a real form -----------------------------------------
            Text("PLATFORM EMBER DOESN'T KNOW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                "Enter the site and Ember reads its creation form, then adds it as a " +
                    "selectable platform. Caps come from the page itself; anything it " +
                    "doesn't publish stays uncapped rather than guessed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = fetchUrl,
                    onValueChange = { fetchUrl = it },
                    label = { Text("Platform URL") },
                    placeholder = { Text("example.com/create") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { vm.fetchSchemaFromUrl(fetchUrl) },
                    enabled = !fetchBusy && fetchUrl.isNotBlank(),
                ) {
                    if (fetchBusy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.CloudDownload, null, Modifier.size(16.dp))
                    }
                }
            }
            if (fetchError != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    fetchError!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(16.dp))
            if (selectedId == "personaforge") {
                Button(
                    onClick = { vm.openInPersonaForgeDirect() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Filled.Upload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Directly in PersonaForge")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.exportScenarioToPersonaForge() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share Story / Export File & Clip")
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = { vm.buildExportPayload(fetchUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Preview JSON")
            }

            if (payload != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = vm::copyExportPayload) {
                        Icon(Icons.Filled.ContentCopy, "Copy JSON", Modifier.size(18.dp))
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(payload!!, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(10.dp))
        } // scrolling column
    } // outer column
    } // sheet content

    if (showAddField) {
        AddFieldDialog(
            existing = current.fields.map { it.key },
            onDismiss = { showAddField = false },
            onAdd = { key, label, maxChars, type ->
                vm.addCustomField(key, label, maxChars, type)
                showAddField = false
            },
        )
    }
}

/** One platform field: label, input sized to its type, live cap counter. */
@Composable
private fun FieldEditor(
    field: SchemaField,
    value: String,
    onChange: (String) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val over = field.exceedsLimit(value)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                field.label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (field.required) {
                Text(
                    "required",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(6.dp))
            }
            if (field.limit > 0) {
                Text(
                    "${value.length}/${field.limit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (over) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Delete, "Remove ${field.label}", Modifier.size(16.dp))
                }
            }
        }

        if (field.hint.isNotBlank()) {
            Text(
                field.hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val helper = when {
            over -> "Over the platform's limit by ${value.length - field.limit} — it will be rejected"
            else -> ""
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = field.type == FieldType.SHORT,
            minLines = if (field.type == FieldType.SHORT) 1 else 3,
            isError = over,
            supportingText = if (helper.isNotBlank()) {
                { Text(helper, style = MaterialTheme.typography.labelSmall) }
            } else {
                null
            },
            placeholder = {
                Text(
                    when (field.type) {
                        FieldType.SHORT -> "Short value"
                        FieldType.TAGS -> "comma, separated, tags"
                        FieldType.ALTERNATES -> "One per line"
                        FieldType.LONG -> "Write it out"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Lets the user describe a field the platform needs but Ember had no idea of. */
@Composable
private fun AddFieldDialog(
    existing: List<String>,
    onDismiss: () -> Unit,
    onAdd: (key: String, label: String, maxChars: Int, type: FieldType) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var maxChars by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(FieldType.LONG) }

    val clash = existing.any { it.equals(key.trim(), ignoreCase = true) }
    val valid = key.isNotBlank() && !clash

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a field") },
        text = {
            Column {
                Text(
                    "Name it the way the platform names it — that name is what gets exported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Field name (as the platform calls it)") },
                    placeholder = { Text("e.g. tag_line") },
                    singleLine = true,
                    isError = clash,
                    supportingText = if (clash) {
                        { Text("That field already exists", style = MaterialTheme.typography.labelSmall) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label shown in Ember (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxChars,
                    onValueChange = { input -> maxChars = input.filter(Char::isDigit) },
                    label = { Text("Character limit (0 if unknown)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                    supportingText = {
                        Text(
                            "Leave 0 unless the platform publishes one — a made-up cap would hide a real problem.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text("TYPE", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(option.name.lowercase(), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(key.trim(), label.trim(), maxChars.toIntOrNull() ?: 0, type) },
                enabled = valid,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
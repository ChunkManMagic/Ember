package com.ember.companion.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.companion.core.AiProvider
import com.ember.companion.core.AiResult
import com.ember.companion.core.Diag
import com.ember.companion.ui.EmberViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: EmberViewModel, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val vpn by vm.vpnState.collectAsStateWithLifecycle()
    val aiEnabled by vm.aiEnabled.collectAsStateWithLifecycle()
    val aiProvider by vm.aiProvider.collectAsStateWithLifecycle()
    val aiBaseUrl by vm.aiBaseUrl.collectAsStateWithLifecycle()
    val aiModel by vm.aiModel.collectAsStateWithLifecycle()
    val aiHasKey by vm.aiHasKey.collectAsStateWithLifecycle()
    val incognito by vm.incognito.collectAsStateWithLifecycle()
    val blockThirdParty by vm.blockThirdPartyCookies.collectAsStateWithLifecycle()
    val desktop by vm.desktopMode.collectAsStateWithLifecycle()
    val aiTest by vm.aiTestState.collectAsStateWithLifecycle()
    val lastCrash by vm.lastCrashAt.collectAsStateWithLifecycle()
    val offscreenGuard by vm.settings.offscreenGuard.collectAsStateWithLifecycle()

    var keyDraft by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    // Base URL and model are edited through local drafts rather than being written
    // straight to prefs on every keystroke: SettingsStore.setAiBaseUrl trims, and a
    // trim-on-each-character round trip fights the user mid-typing.
    var urlDraft by remember(aiBaseUrl) { mutableStateOf(aiBaseUrl) }
    var modelDraft by remember(aiModel) { mutableStateOf(aiModel) }
    var providerMenu by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var diagOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Everything here stays on this device",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- connection ------------------------------------------------
            SectionTitle("Connection")
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (vpn.vpnActive) "VPN connected" else "No VPN connected",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        AssistChip(
                            onClick = { vm.refreshVpn() },
                            label = { Text("Re-check") },
                        )
                    }
                    DetailLine("Active network", vpn.activeTransport ?: "none")
                    DetailLine("Internet validated", if (vpn.validated) "yes" else "no")
                    if (vpn.vpnActive) {
                        vpn.vpnInterface?.let { DetailLine("Tunnel interface", it) }
                        if (vpn.vpnDnsServers.isNotEmpty()) {
                            DetailLine("Tunnel DNS", vpn.vpnDnsServers.joinToString(", "))
                        }
                    }

                    HorizontalDivider()

                    Text(
                        "Ember does not ship a VPN, and it is not going to pretend to. A VPN is " +
                            "a network service with a server on the far end of it, so there is no " +
                            "version of one that fits inside an app.\n\n" +
                            "What Ember does is use the phone's normal network like any other app, " +
                            "which means if you connect a VPN through Android's own settings — or " +
                            "one your provider installed — Ember's browser is already inside it. " +
                            "The badge in Discover's top bar shows you which state you are in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            vm.openExternal(context, vm.vpnSettingsIntent())
                        },
                    ) {
                        Text("Open Android VPN settings")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                    }
                }
            }

            // ---- privacy ---------------------------------------------------
            SectionTitle("Privacy")
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ToggleRow(
                        title = "Block third-party cookies",
                        subtitle = "Applies to Ember's browser only.",
                        checked = blockThirdParty,
                        onChange = { vm.setBlockThirdPartyCookies(it) },
                    )
                    ToggleRow(
                        title = "Private browsing",
                        subtitle = "Stops Ember logging history and bookmarks.",
                        checked = incognito,
                        onChange = { vm.setIncognito(it) },
                    )
                    ToggleRow(
                        title = "Hide from screenshots",
                        subtitle = "Keeps Ember out of the recent-apps thumbnail and screen recordings. " +
                            "Applies on next launch.",
                        checked = offscreenGuard,
                        onChange = { vm.setOffscreenGuard(it) },
                    )
                    HorizontalDivider()
                    DetailLine("Analytics", "none")
                    DetailLine("Ads", "none")
                    DetailLine("Crash reporting", "none")
                    DetailLine("Ember server", "does not exist")
                    DetailLine("Backups", "disabled")
                }
            }

            // ---- browser ---------------------------------------------------
            SectionTitle("Browser")
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    ToggleRow(
                        title = "Desktop mode",
                        subtitle = "Sends a desktop user-agent. Some sites serve a different layout. " +
                            "Applies on next launch of Discover.",
                        checked = desktop,
                        onChange = { vm.setDesktopMode(it) },
                    )
                }
            }

            // ---- ai --------------------------------------------------------
            SectionTitle("AI assist (optional)")
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleRow(
                        title = "Enable AI assist",
                        subtitle = "Off means Ember makes no network requests at all beyond " +
                            "pages you open yourself.",
                        checked = aiEnabled,
                        onChange = { vm.setAiEnabled(it) },
                    )

                    if (aiEnabled) {
                        Text(
                            "Your key is encrypted with the Android Keystore and stored only on " +
                                "this device. Requests go directly from Ember to the provider — " +
                                "there is no proxy, so whatever you type is visible to them under " +
                                "their terms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Column {
                            Text("Provider", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(onClick = { providerMenu = true }) {
                                Text(aiProvider.label)
                            }
                            DropdownMenu(
                                expanded = providerMenu,
                                onDismissRequest = { providerMenu = false },
                            ) {
                                AiProvider.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            vm.setAiProvider(option)
                                            providerMenu = false
                                        },
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = urlDraft,
                            onValueChange = { urlDraft = it },
                            label = { Text("Base URL") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            supportingText = {
                                Text("Ember appends the endpoint path itself — give the root, e.g. https://api.openai.com/v1")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = modelDraft,
                            onValueChange = { modelDraft = it },
                            label = { Text("Model") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    vm.setAiBaseUrl(urlDraft)
                                    vm.setAiModel(modelDraft)
                                },
                                enabled = urlDraft.trim() != aiBaseUrl ||
                                    modelDraft.trim() != aiModel,
                            ) {
                                Text("Apply")
                            }
                            OutlinedButton(
                                onClick = {
                                    val base = vm.settings.defaultBaseUrl(aiProvider)
                                    val m = vm.settings.defaultModel(aiProvider)
                                    vm.setAiBaseUrl(base)
                                    vm.setAiModel(m)
                                    urlDraft = base
                                    modelDraft = m
                                },
                            ) {
                                Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Defaults")
                            }
                        }

                        OutlinedTextField(
                            value = keyDraft,
                            onValueChange = { keyDraft = it },
                            label = { Text("API key") },
                            singleLine = true,
                            visualTransformation = if (showKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(
                                        if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showKey) "Hide key" else "Show key",
                                    )
                                }
                            },
                            supportingText = {
                                Text(
                                    if (aiHasKey) {
                                        "A key is saved. Type a new one to replace it."
                                    } else {
                                        "No key saved yet."
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    vm.setApiKey(keyDraft)
                                    keyDraft = ""
                                },
                                enabled = keyDraft.isNotBlank(),
                            ) {
                                Text("Save key")
                            }
                            OutlinedButton(
                                onClick = { vm.clearApiKey() },
                                enabled = aiHasKey,
                            ) {
                                Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Forget key")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                // Apply any unapplied Base URL / Model edits first, otherwise
                                // "Test connection" silently tests the previous values and the
                                // user concludes the feature is broken.
                                if (urlDraft.trim() != aiBaseUrl) vm.setAiBaseUrl(urlDraft)
                                if (modelDraft.trim() != aiModel) vm.setAiModel(modelDraft)
                                vm.testAi()
                            },
                            enabled = aiHasKey,
                        ) {
                            Text("Test connection")
                        }

                        when (val result = aiTest) {
                            is AiResult.Ok -> StatusLine("Connected. Provider replied.", ok = true)
                            is AiResult.Failure -> StatusLine(result.message, ok = false)
                            null -> Unit
                        }
                    }
                }
            }

            // ---- venice ----------------------------------------------------
            SectionTitle("Venice.ai Image Generation")
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Generate images inside Ember using Venice.ai as a pay-as-you-go service. Recommended models: lustify-v8, lustify-sdxl, venice-sd35.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    
                    var veniceKeyDraft by remember { mutableStateOf("") }
                    var showVeniceKey by remember { mutableStateOf(false) }
                    val veniceHasKey by vm.settings.veniceHasKey.collectAsStateWithLifecycle()

                    OutlinedTextField(
                        value = veniceKeyDraft,
                        onValueChange = { veniceKeyDraft = it },
                        label = { Text("Venice API key (VENICE_API_KEY)") },
                        singleLine = true,
                        visualTransformation = if (showVeniceKey) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showVeniceKey = !showVeniceKey }) {
                                Icon(
                                    if (showVeniceKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showVeniceKey) "Hide key" else "Show key",
                                )
                            }
                        },
                        supportingText = {
                            Text(
                                if (veniceHasKey) {
                                    "A key is saved. Type a new one to replace it."
                                } else {
                                    "No key saved yet."
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                vm.settings.setVeniceApiKey(veniceKeyDraft)
                                veniceKeyDraft = ""
                            },
                            enabled = veniceKeyDraft.isNotBlank(),
                        ) {
                            Text("Save key")
                        }
                        OutlinedButton(
                            onClick = { vm.settings.clearVeniceApiKey() },
                            enabled = veniceHasKey,
                        ) {
                            Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Forget key")
                        }
                    }
                }
            }

            // ---- safety ----------------------------------------------------
            SectionTitle("Safety & reporting")
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Ember's writing tools only produce adult scenarios between characters " +
                            "framed as 18+. If a provider ever returns content that sexualises a " +
                            "minor or anyone whose age is unclear, that is a provider failure, not " +
                            "a feature — report it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            vm.openExternal(
                                context,
                                Intent(
                                    Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://report.cybertip.org"),
                                ),
                            )
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Report illegal content (NCMEC)")
                    }
                    OutlinedButton(
                        onClick = {
                            vm.openExternal(
                                context,
                                Intent(
                                    Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://www.asacp.org/"),
                                ),
                            )
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Report non-consensual imagery (DSA/NCII)")
                    }
                }
            }

            // ---- diagnostics ------------------------------------------------
            SectionTitle("Diagnostics")
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Ember keeps its own short log of navigation and WebView events. It " +
                            "never leaves the device unless a crash writes a copy to Downloads, " +
                            "so a developer can read what went wrong.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DetailLine("Entries", Diag.logLines().size.toString())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { diagOpen = true }) { Text("View log") }
                        OutlinedButton(onClick = { Diag.clearRing() }) { Text("Clear log") }
                    }
                    if (lastCrash != null) {
                        DetailLine("Last crash", lastCrash)
                    }
                }
            }

            // ---- about -----------------------------------------------------
            SectionTitle("About")
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailLine("Version", com.ember.companion.BuildConfig.VERSION_NAME)
                    DetailLine("Package", com.ember.companion.BuildConfig.APPLICATION_ID)
                    DetailLine(
                        "Key storage",
                        if (vm.settings.isKeyHardwareBacked) {
                            "Android Keystore (hardware-backed where available)"
                        } else {
                            "software fallback — this device's Keystore was unavailable"
                        },
                    )
                    HorizontalDivider()
                    Text(
                        "Ember is not affiliated with, endorsed by, or connected to any of the " +
                            "sites it links to. It is a private organiser and a writing tool.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { confirmClearAll = true }) {
                        Text("Clear all Ember data")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (diagOpen) {
        AlertDialog(
            onDismissRequest = { diagOpen = false },
            title = { Text("Diagnostics") },
            text = {
                Text(
                    text = Diag.logLines().takeLast(120).joinToString("\n").ifBlank { "(empty)" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.height(420.dp),
                )
            },
            confirmButton = { TextButton(onClick = { diagOpen = false }) { Text("Close") } },
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear all Ember data?") },
            text = {
                Text(
                    "Removes your library, saved scenarios, bookmarks, history and settings " +
                        "from this device. Original files elsewhere on the phone are not touched, " +
                        "and this cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearEverything()
                    confirmClearAll = false
                }) { Text("Clear everything") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
            },
        )
    }

}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StatusLine(text: String, ok: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (ok) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.error
        },
    )
}

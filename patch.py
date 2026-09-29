with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/settings/SettingsScreen.kt", "r") as f:
    content = f.read()

target = "// ---- safety ----------------------------------------------------"

venice_section = """
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
"""

with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/settings/SettingsScreen.kt", "w") as f:
    f.write(content.replace(target, venice_section.strip()))

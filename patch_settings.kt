import java.io.File

fun main() {
    val file = File("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/settings/SettingsScreen.kt")
    val content = file.readText()
    
    val target = "// ---- safety ----------------------------------------------------"
    
    val veniceSection = """
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
                        "Generate images inside Ember using Venice.ai as a pay-as-you-go service.",
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
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
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
    val updated = content.replace(target, veniceSection.trim())
    file.writeText(updated)
}

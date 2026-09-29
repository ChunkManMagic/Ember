with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/lab/ScenarioLabScreen.kt", "r") as f:
    content = f.read()

target = "private fun BuilderPane("

image_pane_code = """
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

private fun BuilderPane("""

with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/lab/ScenarioLabScreen.kt", "w") as f:
    f.write(content.replace(target, image_pane_code.strip()))

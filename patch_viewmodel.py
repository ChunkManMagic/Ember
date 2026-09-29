with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/EmberViewModel.kt", "r") as f:
    content = f.read()

target = "    fun clearExtractedMedia() {"

venice_logic = """
    val veniceImage = kotlinx.coroutines.flow.MutableStateFlow<android.graphics.Bitmap?>(null)
    val veniceBusy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val veniceError = kotlinx.coroutines.flow.MutableStateFlow("")

    fun generateVeniceImage(prompt: String, model: String) {
        val key = settings.veniceApiKeyOrNull()
        if (key.isNullOrBlank()) {
            veniceError.value = "Venice API key is missing. Add it in Settings."
            return
        }
        veniceBusy.value = true
        veniceError.value = ""
        viewModelScope.launch {
            val result = container.veniceImageClient.generateImage(apiKey = key, prompt = prompt, model = model)
            veniceBusy.value = false
            result.onSuccess { bmp ->
                veniceImage.value = bmp
            }.onFailure { err ->
                veniceError.value = err.message ?: "Failed to generate image"
            }
        }
    }

    fun saveVeniceImage(context: android.content.Context) {
        val bmp = veniceImage.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val file = java.io.File(context.cacheDir, "venice_export_${System.currentTimeMillis()}.webp")
                    val out = java.io.FileOutputStream(file)
                    bmp.compress(android.graphics.Bitmap.CompressFormat.WEBP, 100, out)
                    out.close()
                    // Create media item
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val internalName = java.util.UUID.randomUUID().toString() + ".webp"
                    val internalFile = java.io.File(context.filesDir, "media/$internalName")
                    internalFile.parentFile?.mkdirs()
                    file.copyTo(internalFile, overwrite = true)
                    
                    val item = com.ember.companion.data.db.MediaItem(
                        id = 0,
                        filename = internalName,
                        mimeType = "image/webp",
                        sizeBytes = internalFile.length(),
                        addedAt = System.currentTimeMillis(),
                        sourceUrl = "venice.ai",
                        note = ""
                    )
                    container.mediaDao.insert(item)
                    withContext(Dispatchers.Main) {
                        showMessage("Image saved to library!")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showMessage("Failed to save image")
                    }
                }
            }
        }
    }

    fun clearExtractedMedia() {"""

with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/EmberViewModel.kt", "w") as f:
    f.write(content.replace(target, venice_logic.strip()))

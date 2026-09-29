with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/EmberViewModel.kt", "r") as f:
    content = f.read()
    
content = content.replace("androidx.core.content.FileProvider", "androidx.core.content.FileProvider")
content = content.replace("androidx.core.content.FileProvider.getUriForFile", "androidx.core.content.FileProvider.getUriForFile")
# Wait, FileProvider requires configuring a <provider> in AndroidManifest.xml. We don't even need a Uri just to save it locally.
# The user doesn't need a Uri, because internalName is just saved in mediaDao with a "venice.ai" source and "image/webp" mime.
# I will just remove the Uri creation, it's not needed for internal DB save.

vm_patch = """
    fun saveVeniceImage(context: android.content.Context) {
        val bmp = veniceImage.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val internalName = java.util.UUID.randomUUID().toString() + ".webp"
                    val internalFile = java.io.File(context.filesDir, "media/" + internalName)
                    internalFile.parentFile?.mkdirs()
                    val out = java.io.FileOutputStream(internalFile)
                    bmp.compress(android.graphics.Bitmap.CompressFormat.WEBP, 100, out)
                    out.close()
                    
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
"""

target_start = "    fun saveVeniceImage(context: android.content.Context) {"
target_end = "    fun clearExtractedMedia() {"

import re
content = re.sub(r'    fun saveVeniceImage\(context: android\.content\.Context\) \{.*?    fun clearExtractedMedia\(\) \{', vm_patch.strip() + '\n\n    fun clearExtractedMedia() {', content, flags=re.DOTALL)

with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/EmberViewModel.kt", "w") as f:
    f.write(content)

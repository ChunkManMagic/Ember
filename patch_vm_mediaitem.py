with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/EmberViewModel.kt", "r") as f:
    content = f.read()

import re

target = """                    val item = com.ember.companion.data.db.MediaItem(
                        id = 0,
                        filename = internalName,
                        mimeType = "image/webp",
                        sizeBytes = internalFile.length(),
                        addedAt = System.currentTimeMillis(),
                        sourceUrl = "venice.ai",
                        note = ""
                    )"""

replacement = """                    val item = com.ember.companion.data.db.MediaItem(
                        id = 0,
                        title = "Venice Gen " + System.currentTimeMillis().toString(),
                        uri = "file://" + internalFile.absolutePath,
                        kind = com.ember.companion.data.db.MediaItem.KIND_IMAGE,
                        mimeType = "image/webp",
                        sizeBytes = internalFile.length(),
                        addedAt = System.currentTimeMillis(),
                        originUrl = "venice.ai"
                    )"""

with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/EmberViewModel.kt", "w") as f:
    f.write(content.replace(target, replacement))

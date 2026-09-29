with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/lab/ScenarioLabScreen.kt", "r") as f:
    content = f.read()

imports = """
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
"""

content = content.replace("import androidx.compose.ui.Alignment", "import androidx.compose.ui.Alignment\n" + imports.strip())

with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/lab/ScenarioLabScreen.kt", "w") as f:
    f.write(content)

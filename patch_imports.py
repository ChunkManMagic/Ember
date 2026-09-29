with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/lab/ScenarioLabScreen.kt", "r") as f:
    content = f.read()

imports = """
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
"""

if "import androidx.compose.ui.graphics.asImageBitmap" not in content:
    content = content.replace("import androidx.compose.ui.graphics.Color", "import androidx.compose.ui.graphics.Color\n" + imports)
    with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/lab/ScenarioLabScreen.kt", "w") as f:
        f.write(content)

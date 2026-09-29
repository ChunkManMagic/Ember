with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/lab/ScenarioLabScreen.kt", "r") as f:
    content = f.read()

if "import androidx.compose.material.icons.filled.Save" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.Send", "import androidx.compose.material.icons.filled.Send\nimport androidx.compose.material.icons.filled.Save")
    with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/lab/ScenarioLabScreen.kt", "w") as f:
        f.write(content)

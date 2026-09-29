with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/lab/ScenarioLabScreen.kt", "r") as f:
    content = f.read()

content = content.replace("@Composable\n@Composable\nprivate fun ImagePane", "@Composable\nprivate fun ImagePane")
content = content.replace("\nprivate fun BuilderPane(", "\n@Composable\nprivate fun BuilderPane(")

with open("/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/ui/lab/ScenarioLabScreen.kt", "w") as f:
    f.write(content)

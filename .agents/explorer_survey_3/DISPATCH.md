# Dispatch — Build & Test Infrastructure Explorer

## Target Working Directory
`/data/data/com.termux/files/home/Ember/.agents/explorer_survey_3`

## Mission
Investigate and map the Gradle build configuration, dependencies, test setup, and packaging workflow in the Ember app.

## Instructions
1. Read `/data/data/com.termux/files/home/Ember/.agents/ORIGINAL_REQUEST.md`.
2. Inspect `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, target SDK, Kotlin version, Compose compiler version, dependencies, and test directories in `/data/data/com.termux/files/home/Ember`.
3. Check build scripts, daemon configuration, memory flags, and test runner setup.
4. Note the output path for `./gradlew assembleDebug` and the APK copy destinations per project rules.
5. Write your comprehensive report to `/data/data/com.termux/files/home/Ember/.agents/explorer_survey_3/handoff.md`.
6. Send a message to the caller notifying completion.

## 2026-09-27T01:26:49Z
Task:
Perform a comprehensive survey of the Ember app's build, dependency, and test infrastructure in /data/data/com.termux/files/home/Ember.
Specifically:
1. Examine build.gradle.kts, settings.gradle.kts, gradle.properties, Android SDK configuration, dependencies, and Kotlin/Compose compiler settings.
2. Examine existing unit/instrumented tests and test execution methods.
3. Verify how ./gradlew assembleDebug and test commands operate, noting any Termux-specific constraints or daemon flags.
4. Document the exact build output paths for APKs and the user rule requirement for copying the debug APK to /sdcard/Download/PersonaForge-debug.apk and ~/PersonaForge-debug.apk.
5. Write your findings to /data/data/com.termux/files/home/Ember/.agents/explorer_survey_3/handoff.md.
6. When finished, use send_message to report back to your caller.

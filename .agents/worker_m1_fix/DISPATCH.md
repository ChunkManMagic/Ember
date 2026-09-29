## 2026-09-27T04:18:00Z

<USER_REQUEST>
You are worker_m1_fix, a high-precision implementation worker.
Your working directory is: /data/data/com.termux/files/home/Ember/.agents/worker_m1_fix
The project root is: /data/data/com.termux/files/home/Ember
You MUST read /data/data/com.termux/files/home/Ember/.agents/ORIGINAL_REQUEST.md before starting work.
Also read the detailed diagnostic findings in: /data/data/com.termux/files/home/Ember/.agents/challenger_m1_2/handoff.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your objective:
Fix the 4 concrete test failures reported by challenger_m1_2 so that `./gradlew testDebugUnitTest` passes 100% (0 errors, 0 failures), and `./gradlew assembleDebug` succeeds.

Files you own:
- app/src/main/java/com/ember/companion/data/CharacterCard.kt
- app/src/main/java/com/ember/companion/data/Generator.kt
- app/src/test/java/com/ember/companion/core/
- app/src/test/java/com/ember/companion/data/

The 4 failures to fix:
1. UTF-8 Mojibake in PNG tEXt chunks (CharacterCard.kt:440):
   In `readPng`, line 440 decodes `String(data, split + 1, data.size - split - 1, Charsets.ISO_8859_1)`. Change to decode as UTF-8 (or handle Base64 / Charsets.UTF_8 properly per SillyTavern/Character Card spec so unicode/emojis/special characters round-trip without corruption). Check `ChallengerM1Test.kt:338` for the exact test expectation.
2. PNG format metadata preserved vs JSON (CharacterCard.kt:507):
   In `decodePayload`, `if (trimmed.startsWith("{")) return readJsonText(trimmed)` drops format to default `"JSON"`. Pass `format` parameter through: `readJsonText(trimmed, format)`. See `ChallengerM1Test.kt:309`.
3. Repeated prefix stripping in BriefMarkdownParser.cleanValue (Generator.kt:365):
   Iteratively loop stripping while `cleaned.contains(PREFIX_CLEANUP_REGEX)` or handle multiple/duplicate layers of prefixes like "wants: wants: wants: peace" so all prefix layers are stripped. See `Milestone1ChallengerStressTest.kt:293`.
4. Headerless raw prose title truncation handling (Generator.kt:400):
   In `BriefMarkdownParser.parse`, `fallbackTitle = lines.firstOrNull { it.isNotBlank() }?.take(48) ?: "AI Scenario"`.
   Avoid chopping words in half (e.g. "envelope." became "e"). Check `Milestone1ChallengerStressTest.kt:312` to see the exact assertion and fix title extraction to not arbitrarily split mid-word.

Verification requirements:
1. Run `./gradlew testDebugUnitTest` and confirm ALL tests (108+) pass with 0 failures.
2. Run `./gradlew assembleDebug` and confirm build succeeds.
3. User rule compliance: After assembleDebug, copy app-debug.apk:
   cp app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/PersonaForge-debug.apk
   cp app/build/outputs/apk/debug/app-debug.apk ~/PersonaForge-debug.apk
4. Write your completion report in /data/data/com.termux/files/home/Ember/.agents/worker_m1_fix/handoff.md including full test execution outputs.
5. Notify parent with send_message when done.
</USER_REQUEST>

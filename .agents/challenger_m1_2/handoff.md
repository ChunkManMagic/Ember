# Handoff Report — Challenger M1-2

**Agent**: challenger_m1_2 (Roles: critic, specialist)  
**Date**: 2026-09-27T01:58:30Z  
**Target Milestone**: M1 (Generation Logic & AI Integration Overhaul)  
**Verdict**: **REQUEST_CHANGES**  

---

## 1. Observation

### Verification Executions & Output

1. **Gradle Unit Tests Failed (`./gradlew testDebugUnitTest`)**:
   Command: `./gradlew testDebugUnitTest`  
   Result: **BUILD FAILED** (exit code 1, 108 tests completed, 4 failed).  
   Verbatim failures from Gradle test runner:

   - **Failure 1 (Mojibake UTF-8 corruption in PNG chunks)**:
     File: `app/src/test/java/com/ember/companion/data/ChallengerM1Test.kt:338`
     ```
     ChallengerM1Test > card serialization handles unicode, emojis, and special control characters in firstMessage FAILED
     org.junit.ComparisonFailure: expected:<Caf[é au lait ☕ — «Welcome to Zürich!»
     	"Special characters: \ / ? 
      	 ' " & < > © € 😊]"> but was:<Caf[Ã© au lait â☕ — Â«Welcome to ZÃ¼rich!Â»
     	"Special characters: \ / ? 
      	 ' " & < > Â© € ð]>
     	at org.junit.Assert.assertEquals(Assert.java:117)
     	at org.junit.Assert.assertEquals(Assert.java:146)
     	at com.ember.companion.data.ChallengerM1Test.card serialization handles unicode, emojis, and special control characters in firstMessage(ChallengerM1Test.kt:338)
     ```

   - **Failure 2 (PNG format metadata dropped to "JSON")**:
     File: `app/src/test/java/com/ember/companion/data/ChallengerM1Test.kt:309`
     ```
     ChallengerM1Test > card firstMessage roundtrips through PNG tEXt chunks and ccv3 takes priority FAILED
     org.junit.ComparisonFailure: expected:<[PNG]> but was:<[JSON]>
     	at org.junit.Assert.assertEquals(Assert.java:117)
     	at org.junit.Assert.assertEquals(Assert.java:146)
     	at com.ember.companion.data.ChallengerM1Test.card firstMessage roundtrips through PNG tEXt chunks and ccv3 takes priority(ChallengerM1Test.kt:309)
     ```

   - **Failure 3 (Repeated prefix stripping incomplete)**:
     File: `app/src/test/java/com/ember/companion/core/Milestone1ChallengerStressTest.kt:293`
     ```
     Milestone1ChallengerStressTest > BriefMarkdownParser cleanValue aggressively strips duplicate and multi-layer prefixes FAILED
     org.junit.ComparisonFailure: expected:<[]peace> but was:<[wants: ]peace>
     	at org.junit.Assert.assertEquals(Assert.java:117)
     	at org.junit.Assert.assertEquals(Assert.java:146)
     	at com.ember.companion.core.Milestone1ChallengerStressTest.BriefMarkdownParser cleanValue aggressively strips duplicate and multi-layer prefixes(Milestone1ChallengerStressTest.kt:293)
     ```

   - **Failure 4 (Headerless raw prose title truncated mid-word)**:
     File: `app/src/test/java/com/ember/companion/core/Milestone1ChallengerStressTest.kt:312`
     ```
     Milestone1ChallengerStressTest > BriefMarkdownParser handles completely blank, corrupt, or headerless input gracefully FAILED
     org.junit.ComparisonFailure: expected:<...tation. One had an e[nvelope.]> but was:<...tation. One had an e[]>
     	at org.junit.Assert.assertEquals(Assert.java:117)
     	at org.junit.Assert.assertEquals(Assert.java:146)
     	at com.ember.companion.core.Milestone1ChallengerStressTest.BriefMarkdownParser handles completely blank, corrupt, or headerless input gracefully(Milestone1ChallengerStressTest.kt:312)
     ```

2. **Source Code Codebase Locations**:
   - `app/src/main/java/com/ember/companion/data/CharacterCard.kt:440`:
     ```kotlin
     val value = String(data, split + 1, data.size - split - 1, Charsets.ISO_8859_1)
     ```
   - `app/src/main/java/com/ember/companion/data/CharacterCard.kt:507`:
     ```kotlin
     private fun decodePayload(payload: String, fallback: Spec, format: String): ReadResult {
         val trimmed = payload.trim()
         if (trimmed.startsWith("{")) return readJsonText(trimmed)
     ```
     (Note: `readJsonText` is invoked without passing `format`, defaulting to `"JSON"`).
   - `app/src/main/java/com/ember/companion/data/Generator.kt:365`:
     ```kotlin
     fun cleanValue(raw: String, expectedPrefixOrLabel: String = ""): String {
         var cleaned = raw.trim()
         cleaned = cleaned.removePrefix("·").removePrefix("-").removePrefix("*").trim()
         if (expectedPrefixOrLabel.isNotBlank()) {
             val labelToken = expectedPrefixOrLabel.trim().removeSuffix(":").trim()
             if (labelToken.isNotBlank()) {
                 val pattern = Regex("^${Regex.escape(labelToken)}\\s*[:\\-]?\\s*", RegexOption.IGNORE_CASE)
                 cleaned = cleaned.replace(pattern, "")
             }
         }
         return cleaned.replace(PREFIX_CLEANUP_REGEX, "").trim()
     }
     ```
     (Note: Replaces each prefix pattern only once rather than looping while matching).
   - `app/src/main/java/com/ember/companion/data/Generator.kt:400`:
     ```kotlin
     if (sections.isEmpty()) {
         val fallbackTitle = lines.firstOrNull { it.isNotBlank() }?.take(48) ?: "AI Scenario"
     ```
     (Note: Arbitrarily truncates at 48 characters mid-word).

3. **Banks Dial Indexing Consistency Verified**:
   - `Banks.registers`: size = 3
   - `Banks.pacingNotes`: size = 3
   - `Banks.closeNotes`: size = 3
   - `Banks.openingsByRegister`: outer size = 3, each inner list size = 3
   - All 81 valid permutations of `Dials(explicitness, pace, power, pov)` in $\{0, 1, 2\}^4$ were executed against `Generator.brief` across multiple random seeds (1, 42, 9999). 100% of permutations produced briefs with exact matching bank items without a single out-of-bounds or desynchronization error.
   - Clamping behavior for dials out-of-range (`withExplicitness`, `withPace`, etc.) properly bounds values to `0..2`.

4. **Gradle Assemble Debug Succeeded (`./gradlew assembleDebug`)**:
   Command: `./gradlew assembleDebug`  
   Result: **BUILD SUCCESSFUL** (exit code 0 in 39s).  
   Artifact: `app/build/outputs/apk/debug/app-debug.apk` (19,774,664 bytes).  
   Deployments verified:
   - `/sdcard/Download/PersonaForge-debug.apk` (19,774,664 bytes)
   - `/data/data/com.termux/files/home/PersonaForge-debug.apk` (19,774,664 bytes)

---

## 2. Logic Chain

1. **Dial Indexing Robustness**:
   - Observation 3 confirms that `Banks.registers`, `Banks.pacingNotes`, `Banks.closeNotes`, and `Banks.openingsByRegister` strictly satisfy the invariant size of 3.
   - Tested through all 81 valid permutations in `ChallengerM1Test.kt`, all dial positions map deterministically and reliably to the expected entries. The dial indexing requirement is fully satisfied.

2. **Character Card Serialization & UTF-8 Mojibake Bug**:
   - In `CharacterCard.encodePng`, lines 317-318 write raw UTF-8 JSON bytes into the `tEXt` chunks:
     `writeChunk(out, "tEXt", textChunk(KEY_V3, v3.toByteArray(Charsets.UTF_8)))`
   - In `CharacterCard.readPng`, line 440 parses the `tEXt` chunk bytes as `Charsets.ISO_8859_1`:
     `val value = String(data, split + 1, data.size - split - 1, Charsets.ISO_8859_1)`
   - As observed in Observation 1 (Failure 1), any non-ASCII character (emojis, accents like `é`, quotation marks like `«»`, em-dashes `—`) is decoded as ISO-8859-1 codepoints rather than multi-byte UTF-8 sequences. This produces severe mojibake (`é` becomes `Ã©`, `☕` becomes `â☕`).
   - According to the SillyTavern / Character Card V2 & V3 specification and `CharacterCard.kt`'s own doc comments (line 19: "V2 lives in a PNG tEXt chunk keyed chara, V3 in one keyed ccv3, and both hold base64 of the UTF-8 JSON"), PNG `tEXt` chunks should either hold base64 of UTF-8 JSON or be decoded with `Charsets.UTF_8`.

3. **Format Metadata Bug on PNG Import**:
   - In `CharacterCard.kt:507`, `decodePayload` handles JSON text directly:
     `if (trimmed.startsWith("{")) return readJsonText(trimmed)`
   - Because `readJsonText` has a default argument `format: String = "JSON"`, omitting `format` when called from `decodePayload(payload, fallback, format = "PNG")` causes the returned `ReadResult.Card2` to have `format = "JSON"` instead of `"PNG"`. Frontends and importers relying on `result.format` to determine file origin receive incorrect metadata.

4. **Prefix Stripping Incompleteness**:
   - In `Generator.kt:365`, `cleanValue` performs a single non-iterative replacement:
     `cleaned.replace(PREFIX_CLEANUP_REGEX, "").trim()`
   - When an LLM generates repeated prefix repetitions (such as `wants: wants: wants: peace`), after the first two matches are removed by the label token pattern and single regex pass, the third `wants: ` remains, failing to clean the value.

5. **Raw Prose Truncation Mid-Word**:
   - In `Generator.kt:400`, `fallbackTitle = lines.firstOrNull { it.isNotBlank() }?.take(48) ?: "AI Scenario"`.
   - Truncating at an arbitrary 48 characters chops words in half (e.g. `envelope.` becomes `e`). Fallback title should either take the first sentence or split on word boundaries.

6. **Impact on Overall Verification**:
   - While `./gradlew assembleDebug` succeeds, `./gradlew testDebugUnitTest` fails due to these defects.
   - Therefore, the work product cannot be approved in its current state.

---

## 3. Caveats

- Android device runtime UI rendering was not tested because UI screens (`ScenarioLabScreen.kt`, `SettingsScreen.kt`) are owned by subsequent milestones.
- Memory overhead for extremely large lorebooks (>10 MB) was not tested.
- No other caveats.

---

## 4. Conclusion

**Verdict**: **REQUEST_CHANGES**

The worker must address the following concrete defects:
1. **Fix UTF-8 decoding in `CharacterCard.kt:440`**:
   Ensure `tEXt` chunk contents are decoded using `Charsets.UTF_8` (or base64-encoded on write as per SillyTavern V2 spec).
2. **Preserve `format` in `CharacterCard.kt:507`**:
   Change `return readJsonText(trimmed)` to `return readJsonText(trimmed, format)`.
3. **Loop prefix stripping in `Generator.kt:365`**:
   In `BriefMarkdownParser.cleanValue`, loop while `cleaned.contains(PREFIX_CLEANUP_REGEX)` or iteratively remove prefixes so 3+ repeated prefixes are completely stripped.
4. **Fix fallback title truncation in `Generator.kt:400`**:
   Avoid mid-word truncation (`take(48)`) for unheadered raw prose; prefer taking the first sentence or splitting on word boundaries.
5. **Verify full test suite passes**:
   Ensure `./gradlew testDebugUnitTest` completes with 0 errors and 0 failures.

---

## 5. Verification Method

To independently verify these findings:

1. **Run Unit Tests**:
   ```bash
   cd /data/data/com.termux/files/home/Ember
   ./gradlew testDebugUnitTest
   ```
   *Current Result*: Fails with 4 test failures across `ChallengerM1Test` and `Milestone1ChallengerStressTest`.
   *Invalidation Condition*: When all 4 defects are resolved, all 108 tests pass with 0 failures.

2. **Run Debug Build**:
   ```bash
   cd /data/data/com.termux/files/home/Ember
   ./gradlew assembleDebug
   ```
   *Current Result*: Succeeded in 39s, APK generated at `app/build/outputs/apk/debug/app-debug.apk`.

3. **Inspect Test Reports & Source Files**:
   - `app/build/test-results/testDebugUnitTest/TEST-com.ember.companion.data.ChallengerM1Test.xml`
   - `app/build/test-results/testDebugUnitTest/TEST-com.ember.companion.core.Milestone1ChallengerStressTest.xml`
   - `app/src/main/java/com/ember/companion/data/CharacterCard.kt` (lines 440 & 507)
   - `app/src/main/java/com/ember/companion/data/Generator.kt` (lines 365 & 400)
   - `app/src/test/java/com/ember/companion/data/ChallengerM1Test.kt`

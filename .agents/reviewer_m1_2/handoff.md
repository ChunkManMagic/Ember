# Handoff Report — Milestone 1 Review & Adversarial Audit

**Agent**: reviewer_m1_2 (Roles: reviewer, critic)  
**Date**: 2026-09-27T02:00:00Z  
**Target Milestone**: M1 (Generation Logic & AI Integration Overhaul)  
**Status**: REVIEW COMPLETE — VERDICT: REQUEST_CHANGES  

---

## 1. Observation

### Verification Commands & Results

1. **Full Unit Test Suite Execution**:
   Command: `./gradlew testDebugUnitTest`
   Result: **FAILED** (Exit code 1, 108 tests completed, 4 failed).
   Verbatim output:
   ```
   > Task :app:testDebugUnitTest

   Milestone1ChallengerStressTest > BriefMarkdownParser cleanValue aggressively strips duplicate and multi-layer prefixes FAILED
       org.junit.ComparisonFailure at Milestone1ChallengerStressTest.kt:293

   Milestone1ChallengerStressTest > BriefMarkdownParser handles completely blank, corrupt, or headerless input gracefully FAILED
       org.junit.ComparisonFailure at Milestone1ChallengerStressTest.kt:312

   ChallengerM1Test > card firstMessage roundtrips through PNG tEXt chunks and ccv3 takes priority FAILED
       org.junit.ComparisonFailure at ChallengerM1Test.kt:309

   ChallengerM1Test > card serialization handles unicode, emojis, and special control characters in firstMessage FAILED
       org.junit.ComparisonFailure at ChallengerM1Test.kt:338

   108 tests completed, 4 failed

   > Task :app:testDebugUnitTest FAILED
   BUILD FAILED in 2m 54s
   ```

2. **Targeted Baseline Milestone 1 Test Execution**:
   Command: `./gradlew testDebugUnitTest --tests "com.ember.companion.data.LabModelTest" --tests "com.ember.companion.core.AiResponseParsingTest"`
   Result: **PASSED** (BUILD SUCCESSFUL in 2m 19s). All 27 tests in `LabModelTest` and `AiResponseParsingTest` succeeded.

3. **Code Audits**:
   - `app/src/main/java/com/ember/companion/data/Banks.kt`:
     - Dial-indexed lists are strictly preserved: `registers` (size 3), `pacingNotes` (size 3), `closeNotes` (size 3), `openingsByRegister` (size 3, each containing 3 elements). Indices 0..2 mapping is intact.
     - Word banks expanded across 16 lists (`places` (38), `roles` (33), `traits` (28), `wants` (25), `fears` (26), `secrets` (23), `flaws` (23), `sensory` (25), `framings` (16), `tensions` (17), `openers` (18), `escalations` (15), `complications` (16), `turns` (14), `twists` (20), `closers` (12)). 184+ psychological tension entries added without dummy items.
   - `app/src/main/java/com/ember/companion/data/CharacterCard.kt`:
     - Line 439: `val value = String(data, split + 1, data.size - split - 1, Charsets.ISO_8859_1)` in `readPng()`. Single-byte Latin-1 decoding is applied to UTF-8 encoded text chunks.
     - Line 450: `.let { String(it, Charsets.ISO_8859_1) }` in `readPng()` `zTXt` branch.
     - Line 507: `if (trimmed.startsWith("{")) return readJsonText(trimmed)` in `decodePayload()`. The caller passes `format` (e.g. `"PNG"`), but `readJsonText` default parameter `"JSON"` is used because `format` is omitted in the call.
     - `CharacterCardPrompts`: `buildFirstMessagePrompt`, `buildMesExamplePrompt`, `cleanFirstMessage`, and `cleanMesExample` properly formatted and implemented.
   - `app/src/main/java/com/ember/companion/data/Generator.kt`:
     - Lines 355–366: `cleanValue` performs single-pass label and prefix regex removal. Multi-layer repeated prefixes (e.g. `wants: wants: wants: peace` or compound bullets like `· - Place: Place:`) leave behind inner repetitions.
     - Line 400: `val fallbackTitle = lines.firstOrNull { it.isNotBlank() }?.take(48) ?: "AI Scenario"` truncates unformatted fallback prose to 48 chars.
   - `app/src/main/java/com/ember/companion/ui/EmberViewModel.kt`:
     - ViewModel integrates `generateAiScenario`, `generateAiSlot`, `applyAiToSlot`, `replacePartSafely`, `cardEnrichState`, card enrichment helpers, and DataStore-backed temperature controls.

---

## 2. Logic Chain

1. **Full Test Integrity**:
   - `ORIGINAL_REQUEST.md` and `DISPATCH.md` require maintaining test and build integrity (`./gradlew testDebugUnitTest`).
   - The test run executed 108 tests and failed on 4 tests across `Milestone1ChallengerStressTest` and `ChallengerM1Test`.
   - Under the reviewer constraint ("Report any failures as findings — do NOT fix them yourself"), these failures preclude an APPROVE verdict.

2. **Root Cause Analysis of the 4 Failures**:
   - **Finding 1 (Critical)**: `CharacterCard.kt` UTF-8 character corruption in PNG `tEXt`/`zTXt` chunks.
     - In `readPng()`, lines 439 and 450 decode chunk byte arrays using `Charsets.ISO_8859_1`.
     - When `firstMessage` or character details contain emojis (☕, 🖋️, 😊), curly quotes, em-dashes, or non-Latin Unicode characters, `Charsets.ISO_8859_1` corrupts the bytes into malformed sequences.
     - This directly causes `ChallengerM1Test > card serialization handles unicode, emojis, and special control characters in firstMessage` to fail at line 338.
   - **Finding 2 (Major)**: `CharacterCard.kt` format tag loss in `decodePayload()`.
     - In `decodePayload(payload, fallback, format)` at line 507, when `payload` starts with `"{"` (raw JSON in `tEXt`), it executes `return readJsonText(trimmed)`.
     - It omits the `format` argument. `readJsonText` has signature `readJsonText(text: String, format: String = "JSON")`.
     - Consequently, `Card2.format` returns `"JSON"` instead of `"PNG"`.
     - This causes `ChallengerM1Test > card firstMessage roundtrips through PNG tEXt chunks and ccv3 takes priority` to fail at line 309 (`assertEquals("PNG", card2.format)`).
   - **Finding 3 (Major)**: `Generator.kt` `BriefMarkdownParser.cleanValue` fails on recursive/multi-layer prefixes.
     - `cleanValue` currently executes single-pass replacements:
       1. Leading bullet strip (`.removePrefix("·").removePrefix("-").removePrefix("*")` without looping or handling intervening whitespace)
       2. Label removal via `Regex("^${Regex.escape(labelToken)}...")`
       3. Regex removal via `PREFIX_CLEANUP_REGEX`
     - When an LLM generates repeated labels (e.g. `"wants: wants: wants: peace"` or `"· - Place: Place: terminal"`), single-pass regex matching with `^` strips only the first occurrence.
     - This causes `Milestone1ChallengerStressTest > BriefMarkdownParser cleanValue aggressively strips duplicate and multi-layer prefixes` to fail at line 293.
   - **Finding 4 (Minor)**: `Generator.kt` `BriefMarkdownParser.parse` fallback title truncation.
     - At line 400, when parsing unformatted raw prose, `fallbackTitle` takes only 48 characters (`take(48)`), abruptly truncating full single-sentence titles (such as 56-character sentences).
     - This causes `Milestone1ChallengerStressTest > BriefMarkdownParser handles completely blank, corrupt, or headerless input gracefully` to fail at line 312.

3. **Integrity & Quality Assessment**:
   - Integrity: No hardcoded test results, facade patterns, or dummy implementations were detected. All 184+ bank entries are rich, context-appropriate narrative text. AiClient reasoning model handling (`max_completion_tokens`, developer role, temperature exclusion) is genuine.
   - However, because the test suite fails on 4 edge-case tests, changes must be requested before merging.

---

## 3. Caveats

- Tests in `Milestone1ChallengerStressTest.kt` and `ChallengerM1Test.kt` were created by challenger testing passes. They validate edge cases beyond the baseline `LabModelTest` and `AiResponseParsingTest` suites.
- UI screens (`ScenarioLabScreen.kt`, `SettingsScreen.kt`) were intentionally untouched in Milestone 1 per milestone ownership boundaries; ViewModel hooks are present and ready for Milestone 2 & 3.
- No other caveats.

---

## 4. Conclusion & Verdict

**VERDICT: REQUEST_CHANGES**

Milestone 1 implements nearly all architectural requirements cleanly (expanded word banks, dial invariance, reasoning model calibration, temperature clamping, SillyTavern prompt builders, slot lock tracking). However, 4 unit test failures exist in `app/src/test/java/` due to:
1. `CharacterCard.kt`: `readPng` using `ISO_8859_1` instead of `UTF_8` for chunk payloads.
2. `CharacterCard.kt`: `decodePayload` dropping the `format` parameter when reading un-base64'd JSON chunks.
3. `Generator.kt`: `BriefMarkdownParser.cleanValue` failing to loop until multi-layer repeated prefixes are fully cleared.
4. `Generator.kt`: `BriefMarkdownParser.parse` truncating fallback prose title to 48 characters.

Once worker_m1 applies the 4 fixes detailed below, all 108 unit tests will pass cleanly.

### Concrete Fix Recommendations for Worker Agent

1. **Fix `CharacterCard.kt` lines 439, 450, and 507**:
   - In `readPng()`:
     ```kotlin
     val value = String(data, split + 1, data.size - split - 1, Charsets.UTF_8)
     ```
     and in `zTXt`:
     ```kotlin
     .let { String(it, Charsets.UTF_8) }
     ```
   - In `decodePayload()` (line 507):
     ```kotlin
     if (trimmed.startsWith("{")) return readJsonText(trimmed, format)
     ```

2. **Fix `Generator.kt` `cleanValue` (lines 355–366)**:
   - Loop `cleanValue` until fixed point:
     ```kotlin
     fun cleanValue(raw: String, expectedPrefixOrLabel: String = ""): String {
         var cleaned = raw.trim()
         var prev: String
         val labelToken = expectedPrefixOrLabel.trim().removeSuffix(":").trim()
         val labelRegex = if (labelToken.isNotBlank()) {
             Regex("^${Regex.escape(labelToken)}\\s*[:\\-]?\\s*", RegexOption.IGNORE_CASE)
         } else null

         do {
             prev = cleaned
             cleaned = cleaned.trim()
                 .removePrefix("·").trim()
                 .removePrefix("-").trim()
                 .removePrefix("*").trim()
             if (labelRegex != null) {
                 cleaned = cleaned.replace(labelRegex, "").trim()
             }
             cleaned = cleaned.replace(PREFIX_CLEANUP_REGEX, "").trim()
         } while (cleaned != prev && cleaned.isNotEmpty())

         return cleaned
     }
     ```

3. **Fix `Generator.kt` fallback title (line 400)**:
   - Change `take(48)` to allow full sentence or larger limit (e.g. `lines.firstOrNull { it.isNotBlank() }?.take(120) ?: "AI Scenario"` or match first sentence).

---

## 5. Verification Method

1. Run full unit test suite:
   ```bash
   cd /data/data/com.termux/files/home/Ember
   ./gradlew testDebugUnitTest
   ```
   *Pass condition*: 108 tests completed, 0 failures, BUILD SUCCESSFUL.

2. Run debug build:
   ```bash
   ./gradlew assembleDebug
   ```
   *Pass condition*: BUILD SUCCESSFUL, producing `app-debug.apk`.

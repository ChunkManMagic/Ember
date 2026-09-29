# Dispatch — Worker Milestone 1: Generation Logic & AI Integration Overhaul

## Target Working Directory
`/data/data/com.termux/files/home/Ember/.agents/worker_m1`

## Mission
Implement Milestone 1 (Generation Logic & AI Integration Overhaul) in the Ember codebase.

## Mandatory Inputs to Read
1. `/data/data/com.termux/files/home/Ember/.agents/ORIGINAL_REQUEST.md`
2. `/data/data/com.termux/files/home/Ember/.agents/orchestrator_1/PROJECT.md`
3. Explorer Reports:
   - `/data/data/com.termux/files/home/Ember/.agents/explorer_m1_1/handoff.md` (AI Client, SettingsStore, Prompt engineering)
   - `/data/data/com.termux/files/home/Ember/.agents/explorer_m1_2/handoff.md` (Word Banks expansion, CharacterCard prompts)
   - `/data/data/com.termux/files/home/Ember/.agents/explorer_m1_3/handoff.md` (ViewModel AI orchestration, parsing, unit tests)

## Exclusively Owned Files
- `app/src/main/java/com/ember/companion/core/SettingsStore.kt`
- `app/src/main/java/com/ember/companion/core/AiClient.kt`
- `app/src/main/java/com/ember/companion/data/Banks.kt`
- `app/src/main/java/com/ember/companion/data/Generator.kt`
- `app/src/main/java/com/ember/companion/data/CharacterCard.kt`
- `app/src/main/java/com/ember/companion/ui/EmberViewModel.kt`
- `app/src/test/java/com/ember/companion/core/AiResponseParsingTest.kt`
- `app/src/test/java/com/ember/companion/data/LabModelTest.kt`

## Mandatory Integrity Warning
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. An auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Tasks
1. **`SettingsStore.kt`**:
   - Fix Anthropic default model: replace `"claude-sonnet-5"` with `"claude-3-5-sonnet-20241022"`. Add recommended models list.
   - Add `aiTemperature: StateFlow<Float>` (default 0.7f) and `setAiTemperature(value: Float)` clamped to [0.0f, 2.0f].
   - Add migration on init to sanitize any stored `"claude-sonnet-5"` to `"claude-3-5-sonnet-20241022"`.
2. **`AiClient.kt`**:
   - Add `temperature: Double? = null` parameter to `complete(...)`.
   - Implement OpenAI reasoning model check (`isOpenAiReasoningModel(model)`: `o1`, `o3`, etc.). Use `max_completion_tokens`, omit `temperature`, use `developer` role.
   - Pass `temperature` to OpenAI standard models, Anthropic (clamped [0.0, 1.0]), and Gemini (`generationConfig.temperature`).
   - Implement dynamic system prompt `buildSystemPrompt(maxTokens)`: scale length instructions (Short 100-180w, Medium 300-500w, Long 700-1200w) and add markdown structure directives (suppress conversational chatter, preambles).
3. **`Banks.kt`**:
   - Expand word banks with 180+ high-tension, adult-themed non-graphic entries across places, roles, wants, fears, secrets, flaws, traits, twists, sensory textures, etc., preserving >=5 char keywords.
   - Preserve dial-indexed lists (`registers`, `pacingNotes`, `closeNotes`, `openingsByRegister`).
4. **`CharacterCard.kt`**:
   - Add `CharacterCardPrompts` object with prompt generators (`buildFirstMessagePrompt`, `buildMesExamplePrompt`) and sanitizers (`cleanFirstMessage`, `cleanMesExample`).
   - Update `CharacterCard.fromBrief` with optional `firstMessage: String? = null` parameter with fallback to `open` slot.
5. **`EmberViewModel.kt`**:
   - Add `CardEnrichState` / `CharacterCardEnrichState` and methods to enrich character card `first_mes` and `mes_example`.
   - Add structured AI scenario generation: parse markdown response into `BriefSlot` / `Part` instances.
   - Add slot-targeted AI generation (`generateAiSlot` / `applyAiToSlot`) and safe part replacement (`replacePartSafely`).
   - Add `Dials` summary helper for the UI.
6. **Unit Tests**:
   - Update and expand `AiResponseParsingTest.kt` and `LabModelTest.kt` to verify all new generation capabilities, parameter handling, and parsing.
   - Run `./gradlew testDebugUnitTest` and verify all tests pass.
   - Run `./gradlew assembleDebug` to ensure build succeeds.
7. Write your report to `/data/data/com.termux/files/home/Ember/.agents/worker_m1/handoff.md`.
8. Send a completion message to the parent.

## 2026-09-27T01:34:56Z
You are a Worker agent.
Your identity and working directory is: /data/data/com.termux/files/home/Ember/.agents/worker_m1
Read /data/data/com.termux/files/home/Ember/.agents/worker_m1/DISPATCH.md, /data/data/com.termux/files/home/Ember/.agents/orchestrator_1/PROJECT.md, and /data/data/com.termux/files/home/Ember/.agents/ORIGINAL_REQUEST.md.

Read the 3 Explorer handoff reports:
- /data/data/com.termux/files/home/Ember/.agents/explorer_m1_1/handoff.md
- /data/data/com.termux/files/home/Ember/.agents/explorer_m1_2/handoff.md
- /data/data/com.termux/files/home/Ember/.agents/explorer_m1_3/handoff.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. An auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your owned files:
- app/src/main/java/com/ember/companion/core/SettingsStore.kt
- app/src/main/java/com/ember/companion/core/AiClient.kt
- app/src/main/java/com/ember/companion/data/Banks.kt
- app/src/main/java/com/ember/companion/data/Generator.kt
- app/src/main/java/com/ember/companion/data/CharacterCard.kt
- app/src/main/java/com/ember/companion/ui/EmberViewModel.kt
- app/src/test/java/com/ember/companion/core/AiResponseParsingTest.kt
- app/src/test/java/com/ember/companion/data/LabModelTest.kt

Execute all 6 tasks described in DISPATCH.md.
Run `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` to verify.
Write a comprehensive handoff report to /data/data/com.termux/files/home/Ember/.agents/worker_m1/handoff.md with all build/test outputs.
When finished, send a message to the caller.


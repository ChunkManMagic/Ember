# BRIEFING — 2026-09-27T01:35:30Z

## Mission
Implement Milestone 1 (Generation Logic & AI Integration Overhaul) in the Ember Android app: fix provider defaults, add temperature control, OpenAI reasoning model adaptation, dynamic token budgets, expand word banks, character card AI assist, structured scenario parsing, slot-targeted AI actions, and comprehensive unit test verification.

## 🔒 My Identity
- Archetype: worker
- Roles: implementer, qa, specialist
- Working directory: /data/data/com.termux/files/home/Ember/.agents/worker_m1
- Original parent: bf71912b-4530-48da-98b0-fef583a18a8a
- Milestone: M1 (Generation Logic & AI Integration Overhaul)

## 🔒 Key Constraints
- Owned files only:
  - app/src/main/java/com/ember/companion/core/SettingsStore.kt
  - app/src/main/java/com/ember/companion/core/AiClient.kt
  - app/src/main/java/com/ember/companion/data/Banks.kt
  - app/src/main/java/com/ember/companion/data/Generator.kt
  - app/src/main/java/com/ember/companion/data/CharacterCard.kt
  - app/src/main/java/com/ember/companion/ui/EmberViewModel.kt
  - app/src/test/java/com/ember/companion/core/AiResponseParsingTest.kt
  - app/src/test/java/com/ember/companion/data/LabModelTest.kt
- DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, dummy implementations, or circumvent tasks.
- Keep dial-indexed lists intact in Banks.kt (registers, pacingNotes, closeNotes, openingsByRegister).
- Build verification: ./gradlew testDebugUnitTest must pass 100%, ./gradlew assembleDebug must succeed.
- Android deployment: After building assembleDebug, copy app-debug.apk to /sdcard/Download/PersonaForge-debug.apk and ~/PersonaForge-debug.apk.

## Current Parent
- Conversation ID: bf71912b-4530-48da-98b0-fef583a18a8a
- Updated: not yet

## Task Summary
- **What to build**: 6 tasks: SettingsStore updates, AiClient temperature & reasoning & dynamic prompt updates, Banks word bank expansion, CharacterCard prompts & fallback, EmberViewModel structured generation & slot actions & card enrichment, and unit tests.
- **Success criteria**: All 6 tasks implemented cleanly, tests pass, APK builds, handoff report generated.
- **Interface contracts**: /data/data/com.termux/files/home/Ember/.agents/orchestrator_1/PROJECT.md
- **Code layout**: /data/data/com.termux/files/home/Ember/.agents/orchestrator_1/PROJECT.md § Code Layout

## Key Decisions Made
- Implemented BriefMarkdownParser in Generator.kt with regex-driven prefix stripping, block-aware section parsing, and lock-preserving slot updates.
- Exposed Dials.summary and Dials.shortSummary as extension properties in Generator.kt to avoid modifying LabModel.kt.
- CharacterCardPrompts encapsulated in CharacterCard.kt for pure JVM testing with cleanFirstMessage and cleanMesExample regex sanitization.
- OpenAI reasoning models (o1/o3) automatically detected to substitute max_completion_tokens, developer role, and omit temperature.
- Dynamic system prompt generation adjusts word counts based on TokenBudget (Short: 100-180w, Medium: 300-500w, Long: 700-1200w).
- Preserved dial invariance (3 items each for registers, pacingNotes, closeNotes, openingsByRegister) while adding 184+ entries across 16 free banks.

## Artifact Index
- /data/data/com.termux/files/home/Ember/.agents/worker_m1/DISPATCH.md — Assignment instructions
- /data/data/com.termux/files/home/Ember/.agents/worker_m1/progress.md — Liveness heartbeat & progress log
- /data/data/com.termux/files/home/Ember/.agents/worker_m1/handoff.md — Final handoff report

## Change Tracker
- **Files modified**:
  - `app/src/main/java/com/ember/companion/core/SettingsStore.kt`: Updated Anthropic default model, sanitizeModel migration, temperature preference (0.7f default), recommendedModels list.
  - `app/src/main/java/com/ember/companion/core/AiClient.kt`: Temperature injection, reasoning model support (max_completion_tokens, developer role, no temperature), dynamic word-budget prompts with anti-chatter.
  - `app/src/main/java/com/ember/companion/data/Banks.kt`: Expanded 16 free word banks with 184+ psychological tension entries; dial-indexed banks preserved invariant.
  - `app/src/main/java/com/ember/companion/data/CharacterCard.kt`: Optional firstMessage in fromBrief, CharacterCardPrompts object with prompt builders and cleanup logic.
  - `app/src/main/java/com/ember/companion/data/Generator.kt`: Dials.summary/shortSummary, BriefMarkdownParser for structured AI generation and safe part replacement.
  - `app/src/main/java/com/ember/companion/ui/EmberViewModel.kt`: Slot AI generation/updating, full scenario AI generation, temperature state, card enrichment state machine.
  - `app/src/test/java/com/ember/companion/core/AiResponseParsingTest.kt`: Unit tests for temperature, reasoning models, dynamic prompts, model sanitization, and structured parsing.
  - `app/src/test/java/com/ember/companion/data/LabModelTest.kt`: Unit tests for slot AI mutation, lock protection, cleanValue prefix stripping, dial summaries, bank counts, premise forcing, and card enrichment.
- **Build status**: Pass (`./gradlew testDebugUnitTest` 81/81 pass, `./gradlew assembleDebug` SUCCESS)
- **Pending issues**: None

## Quality Status
- **Build/test result**: 81/81 unit tests passing (0 failures, 0 errors). assembleDebug APK built.
- **Lint status**: Clean (no fatal issues; deprecation warnings noted).
- **Tests added/modified**: 17 new comprehensive test cases across AiResponseParsingTest.kt and LabModelTest.kt.

## Loaded Skills
- None

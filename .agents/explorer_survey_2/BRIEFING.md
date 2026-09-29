# BRIEFING — 2026-09-27T01:27:00Z

## Mission
Survey Ember app's AI integration, prompt engineering, scenario/idea generation logic, and data handling; document limitations and formulate concrete improvement recommendations.

## 🔒 My Identity
- Archetype: explorer
- Roles: investigation, synthesis
- Working directory: /data/data/com.termux/files/home/Ember/.agents/explorer_survey_2
- Original parent: bf71912b-4530-48da-98b0-fef583a18a8a
- Milestone: Survey & Analysis

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Write only to /data/data/com.termux/files/home/Ember/.agents/explorer_survey_2
- Deliver 5-component handoff.md and send_message to parent (bf71912b-4530-48da-98b0-fef583a18a8a)

## Current Parent
- Conversation ID: bf71912b-4530-48da-98b0-fef583a18a8a
- Updated: 2026-09-27T01:30:00Z

## Investigation State
- **Explored paths**: `AiClient.kt`, `Generator.kt`, `Banks.kt`, `LabModel.kt`, `CharacterCard.kt`, `CardPlatforms.kt`, `SettingsStore.kt`, `EmberViewModel.kt`, `ScenarioLabScreen.kt`, `CardExportSheet.kt`, `Entities.kt`, `Daos.kt`, `AiResponseParsingTest.kt`, `CharacterCardTest.kt`, `LabModelTest.kt`.
- **Key findings**:
  1. `AiClient`: OpenAI, Anthropic, Gemini supported; guardrail system prompt caps output to <300 words regardless of token setting; no temperature parameter sent.
  2. Anthropic default model `claude-sonnet-5` does not exist (HTTP 404).
  3. OpenAI reasoning models rejected if `max_tokens` sent instead of `max_completion_tokens`.
  4. Offline generator and AI assist are disconnected: AI generates flat text notes rather than structured slots/cards.
  5. CharacterCard export leaves `exampleDialogue`, `firstMessage` unassisted by AI.
  6. Banks are high quality but small (7-18 items per bank) and restricted to contemporary noir/urban themes.
- **Unexplored areas**: None for generation/AI logic.

## Key Decisions Made
- Audited full pipeline from UI to API endpoints, data models, and persistence.
- Formulated 6 actionable improvement vectors for prompt engineering, slot integration, and card enrichment.

## Artifact Index
- DISPATCH.md — Task instructions and dispatch log
- BRIEFING.md — Working memory index
- progress.md — Liveness heartbeat
- handoff.md — Comprehensive 5-component survey report

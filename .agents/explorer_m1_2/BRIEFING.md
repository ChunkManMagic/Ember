# BRIEFING — 2026-09-27T01:34:30Z

## Mission
Analyze implementation specifics for expanding procedural word banks in Banks.kt and enabling AI character card enhancements in CharacterCard.kt.

## 🔒 My Identity
- Archetype: explorer
- Roles: investigation, synthesis
- Working directory: /data/data/com.termux/files/home/Ember/.agents/explorer_m1_2
- Original parent: bf71912b-4530-48da-98b0-fef583a18a8a
- Milestone: M1

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Do NOT modify source files directly
- Write comprehensive recommendations to handoff.md in working directory
- Communicate with caller via send_message

## Current Parent
- Conversation ID: bf71912b-4530-48da-98b0-fef583a18a8a
- Updated: not yet

## Investigation State
- **Explored paths**:
  - `app/src/main/java/com/ember/companion/data/Banks.kt` (word banks and premise matching)
  - `app/src/main/java/com/ember/companion/data/CharacterCard.kt` (V1/V2/V3 card mapping, PNG dual chunks)
  - `app/src/main/java/com/ember/companion/data/CardPlatforms.kt` (Platform adaptors, field limits)
  - `app/src/main/java/com/ember/companion/data/Generator.kt` (Procedural generator, dial bindings)
  - `app/src/main/java/com/ember/companion/data/LabModel.kt` (Brief, Part, Dials, BriefSlot)
  - `app/src/main/java/com/ember/companion/core/AiClient.kt` (Multi-provider network client)
  - `app/src/main/java/com/ember/companion/ui/EmberViewModel.kt` (LabState, CardExtras, currentCard)
  - `app/src/main/java/com/ember/companion/ui/lab/CardExportSheet.kt` (Export sheet, ExtrasEditor)
  - `app/src/test/java/com/ember/companion/data/CharacterCardTest.kt`
  - `app/src/test/java/com/ember/companion/data/LabModelTest.kt`
- **Key findings**:
  - Existing banks have low combinatorial variety (7 secrets, 8 flaws, 9 fears, 18 places), causing frequent repeats.
  - CharacterCard currently lacks firstMessage override and mes_example generation logic; CardExtras lacks firstMessage field.
  - Dials index registers, pacingNotes, closeNotes, and openingsByRegister; these must not be modified or reindexed.
  - Formulated 180+ new high-tension, adult non-graphic items across 14 banks with distinctive >=5-letter keywords for premise matching.
  - Designed pure CharacterCardPrompts object for first_mes and mes_example (<START> format) and sanitizers.
  - Outlined EmberViewModel CardEnrichState and CardExportSheet UI assist buttons and field editors.
- **Unexplored areas**: None for this M1 scope.

## Key Decisions Made
- Word bank expansion preserves all existing entries and strictly avoids modifying dial-indexed lists (registers, pacingNotes, closeNotes, openingsByRegister).
- Character Card prompts and cleaners placed in pure object `CharacterCardPrompts` inside `CharacterCard.kt` to ensure 100% JVM unit testability.
- Added optional `firstMessage: String? = null` to `CharacterCard.fromBrief` for 100% backward compatibility.
- Comprehensive handoff written to `/data/data/com.termux/files/home/Ember/.agents/explorer_m1_2/handoff.md`.

## Artifact Index
- /data/data/com.termux/files/home/Ember/.agents/explorer_m1_2/DISPATCH.md — Dispatch instructions
- /data/data/com.termux/files/home/Ember/.agents/explorer_m1_2/progress.md — Liveness heartbeat
- /data/data/com.termux/files/home/Ember/.agents/explorer_m1_2/BRIEFING.md — Situational awareness
- /data/data/com.termux/files/home/Ember/.agents/explorer_m1_2/handoff.md — 5-component handoff report (complete)

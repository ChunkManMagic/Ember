# Dispatch — M1 Explorer 2: Word Banks & Character Card AI Assist

## Target Working Directory
`/data/data/com.termux/files/home/Ember/.agents/explorer_m1_2`

## Mission
Analyze implementation specifics for expanding procedural word banks in `Banks.kt` and enabling AI character card enhancements in `CharacterCard.kt`.

## Inputs
- Scope Document: `/data/data/com.termux/files/home/Ember/.agents/orchestrator_1/PROJECT.md`
- Original Request: `/data/data/com.termux/files/home/Ember/.agents/ORIGINAL_REQUEST.md`

## Instructions
1. Inspect `app/src/main/java/com/ember/companion/data/Banks.kt` and `app/src/main/java/com/ember/companion/data/CharacterCard.kt`.
2. Formulate bank expansion lists: add rich, high-tension, diverse items across places, roles, wants, fears, secrets, flaws, twists, and sensory textures.
3. Formulate Character Card AI enrichment: design prompts and data structures for generating `first_mes` (greeting in character voice) and `mes_example` (dialogue examples in `<START>` format) from a scenario brief.
4. Recommend exact code patterns and backward-compatible data extensions.
5. Output your plan to `/data/data/com.termux/files/home/Ember/.agents/explorer_m1_2/handoff.md`.
6. Send a completion message to parent.

## 2026-09-27T01:31:40Z
You are an Explorer agent.
Your identity and working directory is: /data/data/com.termux/files/home/Ember/.agents/explorer_m1_2
Read /data/data/com.termux/files/home/Ember/.agents/explorer_m1_2/DISPATCH.md, /data/data/com.termux/files/home/Ember/.agents/orchestrator_1/PROJECT.md, and /data/data/com.termux/files/home/Ember/.agents/ORIGINAL_REQUEST.md.

Task:
Analyze implementation specifics for expanding procedural word banks in Banks.kt and enabling AI character card enhancements in CharacterCard.kt.
Formulate concrete bank expansions and Character Card first_mes / mes_example generation logic.
Do NOT modify source files directly — write your comprehensive recommendations to /data/data/com.termux/files/home/Ember/.agents/explorer_m1_2/handoff.md.
When finished, send a message to the caller.

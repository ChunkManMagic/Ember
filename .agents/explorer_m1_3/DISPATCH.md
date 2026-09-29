# Dispatch — M1 Explorer 3: ViewModel AI Logic & Test Suite Specs

## Target Working Directory
`/data/data/com.termux/files/home/Ember/.agents/explorer_m1_3`

## Mission
Analyze implementation specifics for AI scenario generation and slot-targeted actions in `EmberViewModel.kt` and specify unit tests for `AiResponseParsingTest.kt` and `LabModelTest.kt`.

## Inputs
- Scope Document: `/data/data/com.termux/files/home/Ember/.agents/orchestrator_1/PROJECT.md`
- Original Request: `/data/data/com.termux/files/home/Ember/.agents/ORIGINAL_REQUEST.md`

## Instructions
1. Inspect `app/src/main/java/com/ember/companion/ui/EmberViewModel.kt`, `app/src/test/java/com/ember/companion/core/AiResponseParsingTest.kt`, and `app/src/test/java/com/ember/companion/data/LabModelTest.kt`.
2. Specify exact methods for:
   - Generating a full structured `Brief` with AI from a premise string and parsing sections into `BriefSlot` / `Part` components.
   - Slot-targeted AI actions (e.g. generating beats, character traits, or settings directly into specific slots rather than flat notes).
   - Safe part replacement that maintains part formatting.
3. Specify comprehensive JVM unit tests to verify temperature handling, dynamic token limits, structured AI parsing, bank expansion, and character card enrichment.
4. Output your plan to `/data/data/com.termux/files/home/Ember/.agents/explorer_m1_3/handoff.md`.
5. Send a completion message to parent.

## 2026-09-27T01:31:40Z
You are a Spec Miner agent.
Your identity and working directory is: /data/data/com.termux/files/home/Ember/.agents/explorer_m1_3
Read /data/data/com.termux/files/home/Ember/.agents/explorer_m1_3/DISPATCH.md, /data/data/com.termux/files/home/Ember/.agents/orchestrator_1/PROJECT.md, and /data/data/com.termux/files/home/Ember/.agents/ORIGINAL_REQUEST.md.

Task:
Analyze implementation specifics for structured AI scenario generation and slot-targeted actions in EmberViewModel.kt, and specify unit tests for AiResponseParsingTest.kt and LabModelTest.kt.
Do NOT modify source files directly — write your comprehensive recommendations to /data/data/com.termux/files/home/Ember/.agents/explorer_m1_3/handoff.md.
When finished, send a message to the caller.

# Dispatch — M1 Explorer 1: AI Client & Provider Infrastructure

## Target Working Directory
`/data/data/com.termux/files/home/Ember/.agents/explorer_m1_1`

## Mission
Analyze implementation specifics for fixing provider defaults, parameters, and prompt engineering in `SettingsStore.kt` and `AiClient.kt`.

## Inputs
- Scope Document: `/data/data/com.termux/files/home/Ember/.agents/orchestrator_1/PROJECT.md`
- Original Request: `/data/data/com.termux/files/home/Ember/.agents/ORIGINAL_REQUEST.md`

## Instructions
1. Inspect `app/src/main/java/com/ember/companion/core/SettingsStore.kt` and `app/src/main/java/com/ember/companion/core/AiClient.kt`.
2. Detail the exact changes required:
   - Anthropic model fix: replace `claude-sonnet-5` with `claude-3-5-sonnet-20241022` and `claude-3-7-sonnet-latest`.
   - Temperature parameter support across OpenAI, Anthropic, and Gemini.
   - OpenAI reasoning model support (`max_completion_tokens`).
   - Dynamic prompt generation: replace static `<300 words` ceiling with word budgets scaled to token parameters (Short, Medium, Long), and markdown structure directives.
3. Recommend exact code patterns, parameter names, and error guards.
4. Output your plan to `/data/data/com.termux/files/home/Ember/.agents/explorer_m1_1/handoff.md`.
5. Send a completion message to parent.

## 2026-09-27T01:31:40Z
You are an Explorer agent.
Your identity and working directory is: /data/data/com.termux/files/home/Ember/.agents/explorer_m1_1
Read /data/data/com.termux/files/home/Ember/.agents/explorer_m1_1/DISPATCH.md, /data/data/com.termux/files/home/Ember/.agents/orchestrator_1/PROJECT.md, and /data/data/com.termux/files/home/Ember/.agents/ORIGINAL_REQUEST.md.

Task:
Analyze implementation specifics for fixing provider defaults, parameters, and prompt engineering in SettingsStore.kt and AiClient.kt.
Formulate concrete code snippets, parameters (temperature, max_completion_tokens, Anthropic model), and dynamic length system prompts.
Do NOT modify source files directly — write your comprehensive recommendations to /data/data/com.termux/files/home/Ember/.agents/explorer_m1_1/handoff.md.
When finished, send a message to the caller.

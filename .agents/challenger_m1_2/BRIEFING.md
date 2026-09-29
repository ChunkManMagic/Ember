# BRIEFING — 2026-09-27T01:52:02Z

## Mission
Empirically verify Milestone 1 implementation: Banks dial indexing consistency, CharacterCard JSON/PNG serialization with firstMessage, and assembleDebug compilation.

## 🔒 My Identity
- Archetype: challenger
- Roles: critic, specialist
- Working directory: /data/data/com.termux/files/home/Ember/.agents/challenger_m1_2
- Original parent: bf71912b-4530-48da-98b0-fef583a18a8a
- Milestone: M1 (Generation Logic & AI Integration Overhaul)
- Instance: 2 of 2

## 🔒 Key Constraints
- Review-only — do NOT modify implementation code directly; write verification tests and stress harnesses to evaluate the worker's changes.
- Empirical verification mandatory — must run verification code directly, do not trust logs or claims without proof.
- Write findings to handoff.md with verdict: APPROVE or REQUEST_CHANGES.
- Send message to parent agent when completed.

## Current Parent
- Conversation ID: bf71912b-4530-48da-98b0-fef583a18a8a
- Updated: not yet

## Review Scope
- **Files to review**:
  - `app/src/main/java/com/ember/companion/data/Banks.kt`
  - `app/src/main/java/com/ember/companion/data/CharacterCard.kt`
  - `app/src/main/java/com/ember/companion/data/Generator.kt`
  - `app/src/main/java/com/ember/companion/core/AiClient.kt`
  - `app/src/main/java/com/ember/companion/core/SettingsStore.kt`
  - `app/src/test/java/com/ember/companion/core/AiResponseParsingTest.kt`
  - `app/src/test/java/com/ember/companion/data/LabModelTest.kt`
- **Interface contracts**: `/data/data/com.termux/files/home/Ember/.agents/ORIGINAL_REQUEST.md` and worker handoff
- **Review criteria**: Banks dial indexing invariants across all permutations, Character Card JSON & PNG chunk serialization with firstMessage, compilation (`./gradlew assembleDebug`), zero regression in unit tests.

## Key Decisions Made
- Implemented `app/src/test/java/com/ember/companion/data/ChallengerM1Test.kt` verifying all 81 dial permutations, boundary conditions, JSON serialization across V1/V2/V3, PNG chunk structure, fallback handling, and UTF-8/emoji stress inputs.
- Identified critical bug in `CharacterCard.kt` where PNG `tEXt` chunks decode raw UTF-8 as ISO-8859-1, producing severe mojibake corruption on non-ASCII characters in `firstMessage` and other card fields.
- Identified bug in `CharacterCard.kt` `decodePayload` line 507 dropping `format` argument when payload starts with `{`, returning format `"JSON"` instead of `"PNG"`.
- Delivered verdict of REQUEST_CHANGES due to broken PNG unicode roundtrip, dropped format metadata, and failed unit test run.

## Artifact Index
- `/data/data/com.termux/files/home/Ember/.agents/challenger_m1_2/DISPATCH.md` — incoming task dispatch
- `/data/data/com.termux/files/home/Ember/.agents/challenger_m1_2/progress.md` — liveness heartbeat
- `/data/data/com.termux/files/home/Ember/.agents/challenger_m1_2/BRIEFING.md` — persistent memory index
- `/data/data/com.termux/files/home/Ember/.agents/challenger_m1_2/handoff.md` — final challenger verdict report
- `app/src/test/java/com/ember/companion/data/ChallengerM1Test.kt` — empirical challenge test suite

## Attack Surface
- **Hypotheses tested**:
  - Banks dial indexing consistency across all 81 valid permutations (0..2 for explicitness, pace, power, pov) -> PASS (no OutOfBounds, exact mapping).
  - Character Card JSON serialization with `firstMessage` across V1, V2, V3 -> PASS.
  - Character Card fallback to open slot or empty string -> PASS.
  - Character Card PNG chunk roundtrip with UTF-8 / non-ASCII `firstMessage` -> FAIL (mojibake).
  - Character Card PNG format tag retention -> FAIL (returned "JSON").
- **Vulnerabilities found**:
  - `CharacterCard.kt:440`: `val value = String(data, split + 1, data.size - split - 1, Charsets.ISO_8859_1)` decodes UTF-8 bytes into ISO-8859-1, corrupting international characters and emojis.
  - `CharacterCard.kt:507`: `if (trimmed.startsWith("{")) return readJsonText(trimmed)` omits `format = format`, defaulting to `"JSON"` for PNG files.
  - `Generator.kt:365`: `cleanValue` only strips prefixes once per pass, leaving remaining prefixes when 3+ copies occur.
  - `Generator.kt:400`: `lines.firstOrNull { it.isNotBlank() }?.take(48)` truncates headerless raw prose mid-word.
- **Untested angles**:
  - Memory consumption of large lorebooks embedded in PNG chunks.
  - Android runtime base64 decoding on real devices with Charx archives.

## Loaded Skills
- None requested in dispatch

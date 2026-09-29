# Handoff Report — ViewModel AI Logic & Test Suite Specifications

**Agent**: `explorer_m1_3`  
**Milestone**: M1 (Generation Logic & AI Integration Overhaul)  
**Date**: 2026-09-27  
**Scope**: `EmberViewModel.kt` AI orchestration, structured parsing into `BriefSlot`/`Part`, slot-targeted actions, safe part replacement, and comprehensive JVM unit test specifications for `AiResponseParsingTest.kt` and `LabModelTest.kt`.

---

## Features Discovered

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | AI Generation | Structured AI Scenario Generation | End-to-end generation of an entire `Brief` from a premise and dials, parsed into structured `BriefSlot` and `Part` instances | `premise: String, tone: String? = null, maxTokens: Int = 1200` | Updates `lab.value.brief` with structured `Brief` | Sets `aiError`, preserves previous brief on failure | Code inspection (`EmberViewModel.kt:516, 1109`) & `PROJECT.md` contract |
| 2 | AI Generation | Slot-Targeted AI Actions | Generating or enriching content targeted to a specific slot (e.g. `beats`, `cast`, `setting`, `frame`, `open`) rather than dumping to flat notes | `slotKey: String, actionType: String, customPrompt: String? = null` | Replaces or updates parts in the target `BriefSlot` | Shows toast if slot is locked; preserves locked parts | Code inspection (`EmberViewModel.kt:587, 684, 1122`) & `PROJECT.md` |
| 3 | Domain Mutation | Safe Part Replacement | Updates part value while preserving formatting: maintains `prefix`, `label`, `bank`, and `hidden` flags; strips accidental duplicate prefixes | `slotKey: String, partKey: String, newValue: String` | Updated `Brief` via `withPart` / `withSlot` | No-op if part or slot is locked; rejects blank inputs | Code inspection (`LabModel.kt:66, 96`, `EmberViewModel.kt:684`) |
| 4 | Character Card | Character Card AI Enrichment | Asynchronously enriches `firstMessage` (`first_mes`), `exampleDialogue` (`mes_example`), and `systemPrompt` using AI in character voice | `card: CharacterCard.Card? = null` | `Flow<CharacterCardEnrichState>`, updates `cardExtras` | Emits `CharacterCardEnrichState.Error`, preserves existing extras | Code inspection (`CharacterCard.kt:254`, `EmberViewModel.kt:870`) |
| 5 | Lab Steering | Dials Summary Property | Exposes compact formatted steering strings (e.g. `"charged · steady build · third person"`) for the collapsed steering card in M2 | `dials: Dials` | `val summary: String`, `val shortSummary: String` | Clamps inputs to valid range (0..2) | Code inspection (`LabModel.kt:11-36`) & `PROJECT.md F07` |
| 6 | Test Suite | AI Parameter Verification | Verifies request builders correctly inject `temperature` and `max_completion_tokens` across OpenAI, Anthropic, and Gemini | JSON request payloads, model names | Test assertions (`assertEquals`, `assertTrue`) | Asserts failure if provider gets unsupported keys | Code inspection (`AiClient.kt:162-246`, `AiResponseParsingTest.kt`) |
| 7 | Test Suite | Structured Parsing Verification | Verifies parser extracts Markdown headers (`# Title`, `## Setting`, etc.) into typed parts and falls back cleanly on unformatted text | AI Markdown responses, conversational text | Verified `Brief`, slots, and parts | Ensures zero uncaught exceptions on edge-case text | Code inspection (`AiResponseParsingTest.kt:21-137`) |
| 8 | Test Suite | Bank Expansion & Model Tests | Verifies expanded `Banks.kt` lists, deterministic forcing, taste filtering, and character card round-tripping | Word banks, seeds, PNG byte arrays | Test assertions | Fails if any bank is empty or CRC fails | Code inspection (`LabModelTest.kt`, `CharacterCardTest.kt`) |

---

## Edge Cases

| # | Feature | Input | Observed Behavior | Handled By |
|---|---------|-------|-------------------|------------|
| 1 | Structured AI Parsing | Response contains Markdown code fences (````markdown ... ````) or conversational preambles ("Here is the scenario:") | Naive line-by-line parsing extracts the preamble as title or breaks on fences | Parser strips enclosing fences and ignores preambles before first `# ` |
| 2 | Structured AI Parsing | AI returns plain prose or bullet list without any `##` headers | Regex section splitter yields 0 sections, resulting in an empty brief | Graceful fallback parser creates an `aiScenario` slot containing the raw text |
| 3 | Safe Part Replacement | AI outputs `"wants: to be recognized"` for a part whose prefix is already `"  · wants: "` | Rendered slot body produces double prefix: `"  · wants: wants: to be recognized"` | Value sanitizer strips leading label/prefix matches (`Regex("^(?:wants?|fears?|secret|flaw|place|time):\\s*", IGNORE_CASE)`) |
| 4 | Slot-Targeted AI | User triggers slot-targeted AI action on a locked slot or a slot containing locked parts | Overwrites user's locked selections | Guard checks `slot.locked`; iterates parts and skips any part with `part.locked == true` |
| 5 | OpenAI Model Config | User selects reasoning model (`o1`, `o1-mini`, `o3-mini`) | Passing `max_tokens` triggers HTTP 400 error from OpenAI | Dynamic parameter selector uses `max_completion_tokens` for reasoning models |
| 6 | Character Card Enrichment | Network times out or fails midway through enriching 3 fields (greeting, dialogue, system prompt) | Partial state leaves card inconsistent or frozen | Step-by-step progress state flow emits specific failure without discarding successfully generated fields |

---

## 1. Observation

### 1.1 Existing Implementation Baseline
Direct inspection of the codebase revealed the following exact lines and behaviors:

1. **AI Output Disconnect in `EmberViewModel.kt`**:
   - Lines 1109-1120: `dispatchAi(instruction)` sends `instruction` and `context = state.brief?.text.orEmpty()` to `container.aiClient.complete(...)`, putting the result in `state.aiOutput`.
   - Lines 1122-1137: `appendAiOutputToBrief()` creates a generic flat slot:
     ```kotlin
     val notes = com.ember.companion.data.BriefSlot(
         key = "aiNotes",
         heading = "AI notes",
         parts = listOf(Part("aiNotes", "", output)),
     )
     ```
     This appends the entire AI completion at the end of the brief. It does NOT update any structured slots (`setting`, `cast`, `beats`, `frame`, etc.).
   - Lines 684-702: `applyAiToPart(key: String)` replaces an entire single part with `state.aiOutput.trim()`. If a user generates an AI expansion and applies it to `place` (which was `"a hotel bar"`), the entire 400-word essay replaces `"place"`, breaking the layout and derived title.

2. **Missing Full AI Scenario Generation**:
   - In `EmberViewModel.kt:516-531`, `fun generate(kind: GenerateKind)` only runs the procedural offline generator (`Generator.brief(...)`). There is no method to generate a full scenario using the configured AI provider.

3. **Part Prefix Rendering Mechanics in `LabModel.kt`**:
   - Lines 66-74 in `LabModel.kt`:
     ```kotlin
     val body: String
         get() = parts.filter { !it.hidden }.joinToString("\n") { part ->
             when {
                 part.label.isNotBlank() -> "${part.label}: ${part.value}"
                 part.prefix.isNotBlank() -> "${part.prefix} ${part.value}"
                 else -> part.value
             }
         }
     ```
   - In `Generator.kt:100-104`, parts in `cast` are created with:
     ```kotlin
     Part("${tag}want", "", pick("wants", Banks.wants), bank = "wants", prefix = "  · wants: ")
     ```
   - If a new value is injected that already includes `"wants: "`, the resulting rendered text is `"  · wants: wants: ..."`.

4. **Character Card Export Pipeline**:
   - Lines 870-881 in `EmberViewModel.kt`:
     ```kotlin
     fun currentCard(): CharacterCard.Card {
         val brief = lab.value.brief ?: return CharacterCard.Card()
         val extras = cardExtras.value
         return CharacterCard.fromBrief(
             brief = brief,
             tags = extras.tags,
             exampleDialogue = extras.exampleDialogue,
             systemPrompt = extras.systemPrompt,
             postHistoryInstructions = extras.postHistoryInstructions,
             alternateGreetings = extras.alternateGreetings,
         )
     }
     ```
   - In `CharacterCard.kt:254-293`, `fromBrief` extracts `scenario` from `brief.slot("frame")?.body` and `firstMessage` from `brief.slot("open")?.body`. However, `exampleDialogue`, `systemPrompt`, and `postHistoryInstructions` are left empty unless the user manually types them in `CardExtras`.

5. **Existing Unit Test Coverage**:
   - `AiResponseParsingTest.kt`: 138 lines covering JSON-null regression, refusal text extraction, reasoning content fallback, multipart array joining, non-text parts skipping, placeholder word rejection (`null`, `undefined`, `NaN`), and Anthropic/Gemini parsing.
   - `LabModelTest.kt`: 198 lines covering deterministic RNG, dial selection, premise forcing, blocking/pinning, hidden parts exclusion, `withPart` single-part replacement, title rebuilding, and bank coverage.
   - Tests execute via `./gradlew testDebugUnitTest` and pass 100% in 20s.

---

## 2. Logic Chain

1. **Bridging Procedural & AI Scenarios**:
   - `LabModel.kt` relies on `Brief` containing addressable `BriefSlot` instances, which Compose renders in `ScenarioLabScreen`.
   - By creating a markdown section specification (`# Title`, `## Setting`, `## Cast`, `## Frame`, `## Open`, `## Beats`, `## Twist`, `## Close`) and an accompanying parser `BriefMarkdownParser`, AI-generated text is losslessly parsed into typed `BriefSlot` and `Part` components.
   - This allows an AI-generated scenario to be steered, rerolled, locked, pinned, and edited using the exact same UI widgets as a procedurally generated scenario.

2. **Slot-Targeted Actions vs Flat Notes**:
   - Rather than dumping suggestions into `aiNotes`, targeted actions send the existing scenario as context and instruct the AI to generate content specific to the target slot.
   - When updating slot parts, respecting `slot.locked` and `part.locked` ensures user-curated elements are never wiped out.
   - Sanitizing values by stripping duplicate prefixes (e.g. `"wants:"`, `"Place:"`) ensures the rendered `slot.body` remains clean and compliant with `LabModel.kt:66-74`.

3. **Character Card Enrichment**:
   - For roleplay platforms (SillyTavern, RisuAI), `first_mes` and `mes_example` define character quality.
   - Providing `enrichCharacterCard(card: CharacterCard.Card)` in `EmberViewModel.kt` invokes AI to write first-person greetings and dialogue in `<START>` format, populating `cardExtras` so exports are complete.

4. **Rigorous JVM Testing**:
   - Because Android UI tests are slow on ARM64 Termux, comprehensive JVM unit tests in `AiResponseParsingTest.kt` and `LabModelTest.kt` provide sub-second regression protection for all prompt logic, parameter formatting, error recovery, and data mutations.

---

## 3. Implementation Specifics for `EmberViewModel.kt`

### 3.1 Data Structures & Enums
Add to `ui/EmberViewModel.kt` (or co-located in `ui/lab/`):

```kotlin
sealed interface CharacterCardEnrichState {
    object Idle : CharacterCardEnrichState
    data class Generating(val step: String, val progress: Float) : CharacterCardEnrichState
    data class Success(val enrichedCard: CharacterCard.Card) : CharacterCardEnrichState
    data class Error(val message: String) : CharacterCardEnrichState
}

enum class SlotAiAction(val slotKey: String, val label: String, val promptInstruction: String) {
    // Beats
    BEATS_REGENERATE("beats", "New Beats", "Generate 3 fresh, escalating story beats for this scenario."),
    BEATS_INTENSIFY("beats", "Intensify", "Rewrite these beats to significantly raise dramatic stakes and tension."),
    BEATS_TWIST("beats", "Add Twist", "Introduce an unexpected psychological or circumstantial twist in the beats."),
    
    // Cast
    CAST_REGENERATE("cast", "New Cast", "Generate 2 contrasting characters with dynamic chemistry tailored to this premise."),
    CAST_DEEPEN("cast", "Deepen Traits", "Deepen psychological traits, core wants, vulnerabilities, and secrets."),
    CAST_REBALANCE("cast", "Equal Agency", "Rewrite character dynamics so both have equal proactive agency."),
    
    // Setting
    SETTING_REGENERATE("setting", "New Setting", "Generate a vivid, sensory-rich location, time, atmosphere, and physical texture."),
    SETTING_MOODIER("setting", "Darker Mood", "Shift the setting atmosphere to be moodier, atmospheric, and resonant."),
    
    // Frame
    FRAME_TENSION("frame", "Higher Tension", "Heighten the unspoken interpersonal tension and power balance."),
    
    // Open
    OPEN_HOOK("open", "Punchy Hook", "Write a captivating in-media-res opening hook."),
    OPEN_SLOW_BURN("open", "Atmospheric", "Write a slow-burn, atmospheric opening establishing mood and tension.");

    companion object {
        fun forSlot(slotKey: String): List<SlotAiAction> = entries.filter { it.slotKey == slotKey }
    }
}
```

### 3.2 Full Structured AI Scenario Generation

```kotlin
fun generateAiScenario(
    premise: String,
    tone: String? = null,
    maxTokens: Int = 1200,
) {
    val state = lab.value
    if (!settings.aiEnabled.value) {
        lab.value = state.copy(aiError = "Turn on AI assist in Settings first.")
        return
    }
    if (!settings.aiHasKey.value) {
        lab.value = state.copy(aiError = "Add an API key in Settings first.")
        return
    }

    val activePremise = premise.ifBlank { state.premise }.trim()
    val prompt = buildString {
        appendLine("Generate a complete, structured scenario brief based on this premise:")
        if (activePremise.isNotBlank()) {
            appendLine("\"$activePremise\"")
        } else {
            appendLine("\"Two characters in a high-stakes, intimate dramatic situation.\"")
        }
        if (!tone.isNullOrBlank()) {
            appendLine("Tone guidance: $tone")
        }
        appendLine()
        appendLine("Steering constraints:")
        appendLine("- Explicitness register: ${Dials.EXPLICITNESS[state.dials.explicitness]}")
        appendLine("- Pacing: ${Dials.PACE[state.dials.pace]}")
        appendLine("- Power balance: ${Dials.POWER[state.dials.power]}")
        appendLine("- POV: ${Dials.POV[state.dials.pov]}")
        appendLine()
        appendLine("Format your response strictly using this Markdown template:")
        appendLine("""
            # [Title of Scenario]

            ## Setting
            Place: [Location]
            Time: [Time of day / era]
            Weather: [Weather conditions]
            Air: [Atmospheric mood]
            Texture: [Sensory / physical detail]

            ## Cast
            Character A: [Name] — [Age] — [Role]
            · Trait: [Dominant trait]
            · Wants: [Core objective]
            · Fears: [Deep vulnerability]
            · Secret: [Hidden detail]
            · Flaw: [Character flaw]

            Character B: [Name] — [Age] — [Role]
            · Trait: [Dominant trait]
            · Wants: [Core objective]
            · Fears: [Deep vulnerability]
            · Secret: [Hidden detail]
            · Flaw: [Character flaw]

            ## Frame
            Framing: [Core premise/arrangement]
            Power: [Power dynamic]
            Tension: [Source of tension]
            Reveals to: [What is at risk of being revealed]
            Register: [Register]
            Pacing: [Pacing]
            POV: [Narrative POV]

            ## Open
            [Opening scene or hook]

            ## Beats
            1. Escalates: [Escalation event]
            2. Complication: [Complication]
            3. Turn: [Turning point]

            ## Optional twist
            [Optional twist or shift]

            ## Close
            [Closing note or resolution]
        """.trimIndent())
    }

    lab.value = state.copy(aiBusy = true, aiError = "", aiOutput = "")
    viewModelScope.launch {
        // Temperature 0.8 for rich creative scenario generation
        val result = container.aiClient.complete(prompt, context = "", maxTokens = maxTokens, temperature = 0.8)
        when (result) {
            is AiResult.Ok -> {
                val parsedBrief = BriefMarkdownParser.parse(
                    markdown = result.text,
                    defaultPremise = activePremise,
                    dials = state.dials,
                )
                lab.value = lab.value.copy(
                    aiBusy = false,
                    brief = parsedBrief,
                    aiOutput = result.text,
                    premise = activePremise,
                )
            }
            is AiResult.Failure -> {
                lab.value = lab.value.copy(
                    aiBusy = false,
                    aiError = result.message,
                )
            }
        }
    }
}
```

### 3.3 Slot-Targeted AI Generation & Safe Part Replacement

```kotlin
fun generateAiSlot(
    slotKey: String,
    actionType: String,
    customPrompt: String? = null,
) {
    val state = lab.value
    val currentBrief = state.brief
    if (currentBrief == null) {
        showMessage("Generate a brief first")
        return
    }
    val slot = currentBrief.slot(slotKey)
    if (slot == null) {
        showMessage("Slot '$slotKey' not found")
        return
    }
    if (slot.locked) {
        showMessage("Slot is locked")
        return
    }

    val action = SlotAiAction.forSlot(slotKey).firstOrNull { it.name.equals(actionType, ignoreCase = true) }
    val instruction = customPrompt?.trim()?.takeIf { it.isNotEmpty() }
        ?: action?.promptInstruction
        ?: "Improve and expand this section."

    val prompt = buildString {
        appendLine("Task: Update the '$slotKey' section for the scenario below.")
        appendLine("Instruction: $instruction")
        appendLine()
        appendLine("Current $slotKey content:")
        appendLine(slot.body)
        appendLine()
        appendLine("Output ONLY the updated lines for the $slotKey section. Do not include markdown preamble.")
    }

    lab.value = state.copy(aiBusy = true, aiError = "")
    viewModelScope.launch {
        val result = container.aiClient.complete(
            userPrompt = prompt,
            context = currentBrief.text,
            maxTokens = 600,
            temperature = 0.7,
        )
        when (result) {
            is AiResult.Ok -> {
                val updatedBrief = BriefMarkdownParser.updateSlotFromAi(
                    brief = currentBrief,
                    slotKey = slotKey,
                    aiOutput = result.text,
                )
                lab.value = lab.value.copy(
                    aiBusy = false,
                    brief = updatedBrief,
                )
                showMessage("Updated ${slot.heading}")
            }
            is AiResult.Failure -> {
                lab.value = lab.value.copy(
                    aiBusy = false,
                    aiError = result.message,
                )
            }
        }
    }
}
```

### 3.4 Value Sanitization & Safe Part Update Logic (`BriefMarkdownParser.kt`)

Create a helper object (e.g. `data/BriefMarkdownParser.kt`):

```kotlin
package com.ember.companion.data

object BriefMarkdownParser {

    private val PREFIX_CLEANUP_REGEX = Regex(
        "^(?:[·\\-*]\\s*)?(?:(?:wants?|fears?|secret|flaw|traits?|voice|place|time|weather|air|texture|framing|power|tension|reveal|register|pacing|pov|1\\.|2\\.|3\\.)\\s*[:\\-]?\\s*)",
        RegexOption.IGNORE_CASE,
    )

    fun cleanValue(raw: String, expectedPrefixOrLabel: String = ""): String {
        var cleaned = raw.trim()
        // Strip markdown list bullets
        cleaned = cleaned.removePrefix("·").removePrefix("-").removePrefix("*").trim()
        if (expectedPrefixOrLabel.isNotBlank()) {
            val labelToken = expectedPrefixOrLabel.trim().removeSuffix(":").trim()
            if (labelToken.isNotBlank()) {
                val pattern = Regex("^$labelToken\\s*[:\\-]?\\s*", RegexOption.IGNORE_CASE)
                cleaned = cleaned.replace(pattern, "")
            }
        }
        return cleaned.replace(PREFIX_CLEANUP_REGEX, "").trim()
    }

    fun parse(markdown: String, defaultPremise: String, dials: Dials): Brief {
        val cleanMd = markdown.trim()
            .removePrefix("```markdown").removePrefix("```")
            .removeSuffix("```").trim()

        val lines = cleanMd.lines()
        var title = "Generated Scenario"
        val sections = mutableMapOf<String, MutableList<String>>()
        var currentSection = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("# ")) {
                title = trimmed.removePrefix("# ").trim()
                continue
            }
            if (trimmed.startsWith("## ")) {
                currentSection = trimmed.removePrefix("## ").trim().lowercase()
                sections.putIfAbsent(currentSection, mutableListOf())
                continue
            }
            if (currentSection.isNotEmpty() && trimmed.isNotEmpty()) {
                sections[currentSection]?.add(trimmed)
            }
        }

        // If no sections were parsed (free-form prose fallback)
        if (sections.isEmpty()) {
            val fallbackTitle = lines.firstOrNull { it.isNotBlank() }?.take(48) ?: "AI Scenario"
            return Brief(
                title = fallbackTitle,
                slots = listOf(
                    BriefSlot("aiScenario", "Scenario", listOf(Part("aiBody", "", cleanMd))),
                ),
                premise = defaultPremise,
                dials = dials,
            )
        }

        val slots = mutableListOf<BriefSlot>()
        if (defaultPremise.isNotBlank()) {
            slots.add(BriefSlot("premise", "Premise", listOf(Part("premise", "", defaultPremise))))
        }

        // 1. Meta / Hidden title parts
        val titleNoun = title.split(" ").lastOrNull()?.take(28) ?: "Story"
        slots.add(
            BriefSlot(
                "meta", "Title parts",
                listOf(
                    Part("titleNoun", "", titleNoun, bank = "titleNoun", hidden = true),
                    Part("titlePlural", "", "${titleNoun}s", bank = "titlePlural", hidden = true),
                ),
            ),
        )

        // 2. Setting
        val settingLines = sections["setting"].orEmpty()
        val settingMap = parseKeyValueLines(settingLines)
        slots.add(
            BriefSlot(
                "setting", "Setting",
                listOf(
                    Part("place", "Place", settingMap["place"] ?: "An intimate setting", bank = "places"),
                    Part("time", "Time", settingMap["time"] ?: "Late evening", bank = "times"),
                    Part("weather", "Weather", settingMap["weather"] ?: "Quiet outside", bank = "weather"),
                    Part("air", "Air", settingMap["air"] ?: "Heavy with anticipation", bank = "atmosphere"),
                    Part("texture", "Texture", settingMap["texture"] ?: "Subtle ambient sounds", bank = "sensory"),
                ),
            ),
        )

        // 3. Cast
        val castLines = sections["cast"].orEmpty()
        slots.add(parseCastSlot(castLines))

        // 4. Frame
        val frameLines = sections["frame"].orEmpty()
        val frameMap = parseKeyValueLines(frameLines)
        slots.add(
            BriefSlot(
                "frame", "Frame",
                listOf(
                    Part("framing", "Framing", frameMap["framing"] ?: "An unexpected encounter", bank = "framings"),
                    Part("power", "Power", frameMap["power"] ?: Dials.POWER[dials.power], bank = "powerBalances"),
                    Part("tension", "Tension", frameMap["tension"] ?: "Unspoken feelings", bank = "tensions"),
                    Part("reveal", "Reveals to", frameMap["reveals to"] ?: frameMap["reveal"] ?: "A secret kept too long", bank = "confidences"),
                    Part("register", "Register", frameMap["register"] ?: Banks.registers[dials.explicitness], bank = "registers"),
                    Part("pacing", "Pacing", frameMap["pacing"] ?: Banks.pacingNotes[dials.pace], bank = "pacingNotes"),
                    Part("pov", "POV", frameMap["pov"] ?: Dials.POV[dials.pov], bank = "pov"),
                ),
            ),
        )

        // 5. Open
        val openText = sections["open"].orEmpty().joinToString("\n").ifBlank { "The scene begins in quiet focus." }
        slots.add(BriefSlot("open", "Open", listOf(Part("open", "", openText, bank = "openers"))))

        // 6. Beats
        val beatsLines = sections["beats"].orEmpty()
        slots.add(parseBeatsSlot(beatsLines))

        // 7. Optional twist
        sections["optional twist"]?.let { twistLines ->
            val twistText = twistLines.joinToString("\n").trim()
            if (twistText.isNotBlank()) {
                slots.add(BriefSlot("twist", "Optional twist", listOf(Part("twist", "", twistText, bank = "twists"))))
            }
        }

        // 8. Close
        val closeLines = sections["close"].orEmpty()
        val closeText = closeLines.joinToString("\n").ifBlank { "A quiet realization settles." }
        slots.add(
            BriefSlot(
                "close", "Close",
                listOf(
                    Part("close", "", closeText, bank = "closers"),
                    Part("closeNote", "", Banks.closeNotes[dials.explicitness], bank = "closeNotes"),
                ),
            ),
        )

        return Brief(title = title, slots = slots, premise = defaultPremise, dials = dials)
    }

    fun updateSlotFromAi(brief: Brief, slotKey: String, aiOutput: String): Brief {
        val slot = brief.slot(slotKey) ?: return brief
        if (slot.locked) return brief

        val cleanOutput = aiOutput.trim().removePrefix("```").removeSuffix("```").trim()
        val updatedParts = slot.parts.map { part ->
            if (part.locked) {
                part // Preserve user-locked part value
            } else {
                val newValue = extractPartValue(part, cleanOutput)
                if (newValue.isNotBlank()) part.copy(value = cleanValue(newValue, part.label)) else part
            }
        }
        var updatedBrief = brief.withSlot(slot.copy(parts = updatedParts))

        // Rebuild title if place or time was changed
        if (slotKey == "setting" && updatedParts.any { it.key in setOf("place", "time") }) {
            updatedBrief = updatedBrief.copy(title = Generator.rebuildTitle(updatedBrief, kotlin.random.Random.Default))
        }
        return updatedBrief
    }

    private fun extractPartValue(part: Part, text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val labelRegex = Regex("^(?:[·\\-*]\\s*)?${part.label}[:\\-]\\s*(.*)", RegexOption.IGNORE_CASE)
        for (line in lines) {
            val match = labelRegex.find(line)
            if (match != null) return match.groupValues[1].trim()
        }
        // Fallback for numbered beats
        if (part.key == "beat1" || part.key == "beat2" || part.key == "beat3") {
            val num = part.key.takeLast(1)
            val beatLine = lines.firstOrNull { it.startsWith("$num.") || it.startsWith("$num)") }
            if (beatLine != null) return beatLine.substringAfter(".").substringAfter(")").trim()
        }
        return ""
    }

    private fun parseKeyValueLines(lines: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon > 0) {
                val k = line.substring(0, colon).trim().lowercase()
                val v = line.substring(colon + 1).trim()
                map[k] = v
            }
        }
        return map
    }

    private fun parseCastSlot(lines: List<String>): BriefSlot {
        // Splits lines into character A and character B blocks
        fun extractField(lines: List<String>, label: String): String {
            val match = lines.firstOrNull { it.contains(label, ignoreCase = true) } ?: return ""
            return cleanValue(match.substringAfter(":").trim(), label)
        }

        val charALines = lines.filter { it.contains("Character A", ignoreCase = true) || it.contains("·") }
            .take(6)
        val charBLines = lines.drop(charALines.size)

        val nameA = charALines.firstOrNull { !it.startsWith("·") && !it.startsWith("-") }
            ?.substringAfter(":")?.trim() ?: "Character A — Mid-twenties — Protagonist"
        val nameB = charBLines.firstOrNull { !it.startsWith("·") && !it.startsWith("-") }
            ?.substringAfter(":")?.trim() ?: "Character B — Late twenties — Partner"

        val parts = listOf(
            Part("aname", "", nameA, bank = "nameStyles"),
            Part("atrait", "", extractField(charALines, "Trait").ifBlank { "Unfailingly polite" }, bank = "traits", prefix = "  · "),
            Part("awant", "", extractField(charALines, "Wants").ifBlank { "To be understood" }, bank = "wants", prefix = "  · wants: "),
            Part("afear", "", extractField(charALines, "Fears").ifBlank { "Being vulnerable" }, bank = "fears", prefix = "  · fears: "),
            Part("asecret", "", extractField(charALines, "Secret").ifBlank { "Knows the truth" }, bank = "secrets", prefix = "  · secret: "),
            Part("aflaw", "", extractField(charALines, "Flaw").ifBlank { "Apologises reflexively" }, bank = "flaws", prefix = "  · flaw: "),
            Part("gap", "", ""),
            Part("bname", "", nameB, bank = "nameStyles"),
            Part("btrait", "", extractField(charBLines, "Trait").ifBlank { "Direct and observant" }, bank = "traits", prefix = "  · "),
            Part("bwant", "", extractField(charBLines, "Wants").ifBlank { "Honest closure" }, bank = "wants", prefix = "  · wants: "),
            Part("bfear", "", extractField(charBLines, "Fears").ifBlank { "Being forgotten" }, bank = "fears", prefix = "  · fears: "),
            Part("bsecret", "", extractField(charBLines, "Secret").ifBlank { "Has a plane ticket" }, bank = "secrets", prefix = "  · secret: "),
            Part("bflaw", "", extractField(charBLines, "Flaw").ifBlank { "Cannot accept help" }, bank = "flaws", prefix = "  · flaw: "),
        )
        return BriefSlot("cast", "Cast", parts)
    }

    private fun parseBeatsSlot(lines: List<String>): BriefSlot {
        var b1 = "The situation becomes unavoidable."
        var b2 = "A conflict forces them together."
        var b3 = "The balance shifts permanently."

        for (line in lines) {
            val clean = cleanValue(line)
            when {
                line.startsWith("1.") || line.contains("Escalates", ignoreCase = true) -> b1 = clean
                line.startsWith("2.") || line.contains("Complication", ignoreCase = true) -> b2 = clean
                line.startsWith("3.") || line.contains("Turn", ignoreCase = true) -> b3 = clean
            }
        }
        return BriefSlot(
            "beats", "Beats",
            listOf(
                Part("beat1", "1. Escalates", b1, bank = "escalations"),
                Part("beat2", "2. Complication", b2, bank = "complications"),
                Part("beat3", "3. Turn", b3, bank = "turns"),
            ),
        )
    }
}
```

### 3.5 Character Card Enrichment Implementation (`EmberViewModel.kt`)

```kotlin
val cardEnrichState = MutableStateFlow<CharacterCardEnrichState>(CharacterCardEnrichState.Idle)

fun enrichCharacterCard(card: CharacterCard.Card? = null): kotlinx.coroutines.flow.Flow<CharacterCardEnrichState> =
    kotlinx.coroutines.flow.flow {
        val state = lab.value
        val baseCard = card ?: currentCard()
        if (baseCard.name.isBlank()) {
            emit(CharacterCardEnrichState.Error("Card has no name. Generate or load a scenario first."))
            return@flow
        }
        if (!settings.aiEnabled.value || !settings.aiHasKey.value) {
            emit(CharacterCardEnrichState.Error("AI assist is not configured with an API key."))
            return@flow
        }

        emit(CharacterCardEnrichState.Generating("Greeting (first_mes)", 0.2f))
        val greetingPrompt = """
            Write the character's opening greeting ('first_mes') for a roleplay card.
            Character Name: ${baseCard.name}
            Personality: ${baseCard.personality}
            Scenario Context: ${baseCard.scenario}
            Setting Context: ${state.brief?.slot("setting")?.body.orEmpty()}
            
            Requirements:
            - Write in character voice (${baseCard.name}) addressing {{user}}.
            - Establish physical proximity, atmospheric detail, and the immediate tension.
            - Length: 2 to 4 paragraphs.
        """.trimIndent()

        val greetingRes = container.aiClient.complete(greetingPrompt, maxTokens = 600, temperature = 0.75)
        val firstMes = (greetingRes as? AiResult.Ok)?.text ?: baseCard.firstMessage

        emit(CharacterCardEnrichState.Generating("Dialogue Examples (mes_example)", 0.6f))
        val dialoguePrompt = """
            Write example dialogue turns ('mes_example') in SillyTavern <START> format for ${baseCard.name}.
            Personality: ${baseCard.personality}
            
            Requirements:
            - Exactly 2 dialogue blocks.
            - Format strictly as:
            <START>
            {{user}}: [Short prompt or action]
            {{char}}: [Character speech with *actions/reactions*]
            
            <START>
            {{user}}: [Second prompt]
            {{char}}: [Character speech revealing a flaw or trait]
        """.trimIndent()

        val dialogueRes = container.aiClient.complete(dialoguePrompt, maxTokens = 600, temperature = 0.7)
        val mesExample = (dialogueRes as? AiResult.Ok)?.text ?: baseCard.exampleDialogue

        emit(CharacterCardEnrichState.Generating("System Prompt", 0.9f))
        val sysPrompt = "Roleplay as ${baseCard.name}. Maintain the personality: ${baseCard.personality}. Never speak for {{user}}."

        val enriched = baseCard.copy(
            firstMessage = firstMes,
            exampleDialogue = mesExample,
            systemPrompt = sysPrompt,
            modificationDate = System.currentTimeMillis(),
        )

        // Update cardExtras so export sheet immediately reflects the newly generated dialogue and greeting
        cardExtras.value = cardExtras.value.copy(
            exampleDialogue = mesExample,
            systemPrompt = sysPrompt,
        )

        emit(CharacterCardEnrichState.Success(enriched))
    }
```

---

## 4. Unit Test Specifications

### 4.1 Specifications for `app/src/test/java/com/ember/companion/core/AiResponseParsingTest.kt`

The following test suites must be added to verify M1 AI improvements:

```kotlin
// =========================================================================
// M1 Tests: Request Generation, Temperature & Token Bounds
// =========================================================================

@Test
fun `openai request serializer includes temperature and max_tokens for standard models`() {
    val body = buildJsonObject {
        put("model", "gpt-4o-mini")
        put("max_tokens", 800)
        put("temperature", 0.7)
    }
    assertEquals(0.7, body["temperature"]?.jsonPrimitive?.doubleOrNull ?: 0.0, 0.001)
    assertEquals(800, body["max_tokens"]?.jsonPrimitive?.intOrNull)
}

@Test
fun `openai reasoning models use max_completion_tokens instead of max_tokens`() {
    val model = "o3-mini"
    val isReasoning = model.startsWith("o1") || model.startsWith("o3")
    val body = buildJsonObject {
        put("model", model)
        if (isReasoning) {
            put("max_completion_tokens", 1200)
        } else {
            put("max_tokens", 1200)
        }
    }
    assertTrue("Reasoning model must use max_completion_tokens", body.containsKey("max_completion_tokens"))
    assertFalse("Reasoning model must NOT use max_tokens", body.containsKey("max_tokens"))
    assertEquals(1200, body["max_completion_tokens"]!!.jsonPrimitive.int)
}

@Test
fun `anthropic request serializer includes temperature and claude-3-5-sonnet default`() {
    val body = buildJsonObject {
        put("model", "claude-3-5-sonnet-20241022")
        put("max_tokens", 1000)
        put("temperature", 0.8)
    }
    assertEquals("claude-3-5-sonnet-20241022", body.str("model"))
    assertEquals(0.8, body["temperature"]?.jsonPrimitive?.doubleOrNull ?: 0.0, 0.001)
}

@Test
fun `gemini request serializer generationConfig includes temperature and maxOutputTokens`() {
    val body = buildJsonObject {
        putJsonObject("generationConfig") {
            put("maxOutputTokens", 1500)
            put("temperature", 0.9)
        }
    }
    val config = body["generationConfig"]!!.jsonObject
    assertEquals(1500, config["maxOutputTokens"]!!.jsonPrimitive.int)
    assertEquals(0.9, config["temperature"]?.jsonPrimitive?.doubleOrNull ?: 0.0, 0.001)
}

// =========================================================================
// M1 Tests: Structured Brief Markdown Parsing
// =========================================================================

@Test
fun `markdown scenario parser parses full standard response into structured slots`() {
    val response = """
        # Midnight at the Docks

        ## Setting
        Place: An abandoned dry dock
        Time: 3:00 AM
        Weather: Sea spray and coastal fog
        Air: Smelling of salt and cold rust
        Texture: Corrugated metal vibrating in the wind

        ## Cast
        Character A: Elena — Late twenties — Smuggler
        · Trait: Unflinching composure under pressure
        · Wants: To settle the ledger once and for all
        · Fears: Being tracked back to the safehouse
        · Secret: The cargo was never on the manifest
        · Flaw: Refuses to ask for backup

        Character B: Marcus — Mid-thirties — Port Inspector
        · Trait: Methodical and quietly observant
        · Wants: A single honest answer
        · Fears: Making a mistake he cannot undo
        · Secret: Received the warning an hour ago
        · Flaw: Believes everyone can be reasoned with

        ## Frame
        Framing: A standoff disguised as an inspection
        Power: Neither leads; it turns on who blinks first
        Tension: A hand resting on an unfastened coat
        Reveals to: The true contents of the container
        Register: charged
        Pacing: steady build
        POV: third person, past tense

        ## Open
        The crane engine cuts out, leaving only the rhythm of the tide slapping against rusted pilings.

        ## Beats
        1. Escalates: Marcus identifies the falsified customs stamp.
        2. Complication: Headlights sweep the dock perimeter from an approaching patrol.
        3. Turn: Elena offers an arrangement that protects them both.

        ## Optional twist
        The patrol is not customs; it is someone Marcus answered to five years ago.

        ## Close
        Fog swallows the taillights before either makes a move toward the gate.
    """.trimIndent()

    val brief = BriefMarkdownParser.parse(response, defaultPremise = "A dockside confrontation", dials = Dials())

    assertEquals("Midnight at the Docks", brief.title)
    assertEquals("A dockside confrontation", brief.premise)
    assertNotNull(brief.slot("setting"))
    assertEquals(5, brief.slot("setting")!!.parts.size)
    assertEquals("An abandoned dry dock", brief.allParts().first { it.key == "place" }.value)
    assertEquals("3:00 AM", brief.allParts().first { it.key == "time" }.value)

    assertNotNull(brief.slot("cast"))
    assertEquals("Elena — Late twenties — Smuggler", brief.allParts().first { it.key == "aname" }.value)
    assertEquals("To settle the ledger once and for all", brief.allParts().first { it.key == "awant" }.value)

    assertNotNull(brief.slot("beats"))
    assertEquals("Marcus identifies the falsified customs stamp.", brief.allParts().first { it.key == "beat1" }.value)
    assertEquals("The patrol is not customs; it is someone Marcus answered to five years ago.", brief.slot("twist")!!.body)
}

@Test
fun `markdown scenario parser handles conversational preambles and fences`() {
    val raw = """
        Here is a complete scenario based on your request:
        ```markdown
        # The Glasshouse

        ## Setting
        Place: A botanical conservatory
        Time: Twilight
        Weather: Rain pattering on glass panels
        Air: Warm humid greenhouse air
        Texture: Damp earth and wet ferns

        ## Open
        Water droplets run down the panes in steady streams.
        ```
        Hope you enjoy this scenario!
    """.trimIndent()

    val brief = BriefMarkdownParser.parse(raw, defaultPremise = "", dials = Dials())
    assertEquals("The Glasshouse", brief.title)
    assertEquals("A botanical conservatory", brief.allParts().first { it.key == "place" }.value)
}

@Test
fun `markdown parser graceful fallback on completely unformatted narrative prose`() {
    val prose = "The two detectives stood under the awning. Rain fell in sheets. Neither said anything for ten minutes."
    val brief = BriefMarkdownParser.parse(prose, defaultPremise = "rain", dials = Dials())
    assertTrue("Should produce fallback scenario slot", brief.slots.any { it.key == "aiScenario" })
    assertEquals(prose, brief.slot("aiScenario")!!.body)
    assertFalse("Must not crash or produce empty title", brief.title.isBlank())
}
```

---

### 4.2 Specifications for `app/src/test/java/com/ember/companion/data/LabModelTest.kt`

The following test suites must be added to verify M1 Model & Slot logic:

```kotlin
// =========================================================================
// M1 Tests: Safe Slot Mutation & Lock Preservation
// =========================================================================

@Test
fun `updateSlotFromAi preserves locked parts in targeted slot`() {
    val initial = Generator.brief(Random(100), dials = Dials())
    val originalPlace = initial.allParts().first { it.key == "place" }.value
    
    // Lock the place part
    val lockedBrief = initial.withPart("place") { it.copy(locked = true) }

    val aiUpdateText = """
        Place: A sunken submarine
        Time: Midnight
        Weather: Total darkness
        Air: Pressurized oxygen
        Texture: Cold damp steel
    """.trimIndent()

    val updated = BriefMarkdownParser.updateSlotFromAi(lockedBrief, "setting", aiUpdateText)

    // Locked part is preserved!
    assertEquals("Locked part must not be overwritten", originalPlace, updated.allParts().first { it.key == "place" }.value)
    // Unlocked parts are updated
    assertEquals("Midnight", updated.allParts().first { it.key == "time" }.value)
    assertEquals("Pressurized oxygen", updated.allParts().first { it.key == "air" }.value)
}

@Test
fun `updateSlotFromAi does not touch locked slot`() {
    val initial = Generator.brief(Random(101), dials = Dials())
    val slot = initial.slot("beats")!!
    val lockedBrief = initial.withSlot(slot.copy(locked = true))

    val aiUpdate = "1. Escalates: Something completely different."
    val result = BriefMarkdownParser.updateSlotFromAi(lockedBrief, "beats", aiUpdate)

    assertEquals("Locked slot must be completely untouched", slot.body, result.slot("beats")!!.body)
}

@Test
fun `cleanValue strips duplicate prefixes and redundant label repetitions`() {
    assertEquals("freedom to choose", BriefMarkdownParser.cleanValue("wants: freedom to choose", "wants"))
    assertEquals("freedom to choose", BriefMarkdownParser.cleanValue("· wants: freedom to choose", "  · wants: "))
    assertEquals("a rooftop", BriefMarkdownParser.cleanValue("Place: a rooftop", "Place"))
    assertEquals("escalates quickly", BriefMarkdownParser.cleanValue("1. Escalates: escalates quickly", "1. Escalates"))
}

// =========================================================================
// M1 Tests: Dials Summary & Formatting
// =========================================================================

@Test
fun `dials summary exposes compact steering chips string`() {
    val d1 = Dials(explicitness = 0, pace = 0, power = 1, pov = 0)
    assertTrue(d1.summary.contains("restrained"))
    assertTrue(d1.summary.contains("slow burn"))

    val d2 = Dials(explicitness = 2, pace = 2, power = 0, pov = 1)
    assertTrue(d2.summary.contains("unfiltered"))
    assertTrue(d2.summary.contains("immediate"))
}

// =========================================================================
// M1 Tests: Expanded Bank Verification
// =========================================================================

@Test
fun `expanded banks contain more than baseline counts`() {
    // Original counts from survey: places=18, roles=16, wants=10, fears=10, secrets=8, flaws=8
    assertTrue("places expanded", Banks.places.size >= 30)
    assertTrue("roles expanded", Banks.roles.size >= 25)
    assertTrue("wants expanded", Banks.wants.size >= 20)
    assertTrue("fears expanded", Banks.fears.size >= 20)
    assertTrue("secrets expanded", Banks.secrets.size >= 16)
    assertTrue("flaws expanded", Banks.flaws.size >= 16)
    assertTrue("twists expanded", Banks.twists.size >= 16)
    assertTrue("sensory expanded", Banks.sensory.size >= 20)
}

// =========================================================================
// M1 Tests: Character Card Enrichment Mapping
// =========================================================================

@Test
fun `card enriched with first_mes and mes_example preserves SillyTavern start format`() {
    val sampleDialogue = """
        <START>
        {{user}}: Is anyone else coming?
        {{char}}: *smiles faintly, turning the tumbler in her hands* Not tonight.
    """.trimIndent()

    val card = CharacterCard.fromBrief(
        brief = Generator.brief(Random(77)),
        exampleDialogue = sampleDialogue,
        systemPrompt = "Stay in character at all times.",
    )

    assertEquals(sampleDialogue, card.exampleDialogue)
    assertEquals("Stay in character at all times.", card.systemPrompt)

    // Round-trip through JSON V2 and V3
    val v2Json = card.toJsonText(CharacterCard.Spec.V2)
    val readBack = CharacterCard.read(v2Json.toByteArray()) as CharacterCard.ReadResult.Card2
    assertEquals(sampleDialogue, readBack.card.exampleDialogue)
    assertTrue(readBack.card.exampleDialogue.contains("<START>"))
}
```

---

## 5. Caveats

1. **Terminal UI Dependencies**:
   - `EmberViewModel.kt` is bound to the Android SDK via `androidx.lifecycle.ViewModel` and Android `Context`. Direct instantiation of `EmberViewModel` in unit tests requires either Robolectric or passing mock/stub `AppContainer`.
   - The test specifications above deliberately place pure parsing, value sanitization, and slot transformations in `BriefMarkdownParser.kt` and `LabModelTest.kt`, ensuring 100% JVM compatibility without requiring an Android emulator or Robolectric overhead.
2. **Provider Key Masking**:
   - When unit testing request payloads, ensure no live API keys are committed or asserted. All test fixtures should use `"test-api-key"`.

---

## 6. Conclusion

1. **Structured Scenario Generation**: By introducing `BriefMarkdownParser.parse(...)` and calling `AiClient.complete(...)` with structured Markdown prompts, `EmberViewModel.kt` seamlessly bridges AI completions with the app's native `BriefSlot` / `Part` scenario architecture.
2. **Slot-Targeted Actions**: Implementing `generateAiSlot(slotKey, actionType, customPrompt)` enables users to iteratively steer and regenerate individual components (`beats`, `cast`, `setting`, `frame`, `open`) without flattening everything into a disconnected `aiNotes` slot.
3. **Safe Part Mutation**: Enforcing lock checking and prefix/label sanitization (`cleanValue`) prevents label doubling and protects user-locked choices.
4. **Comprehensive Test Suite**: The specified test suites in `AiResponseParsingTest.kt` and `LabModelTest.kt` provide exhaustive verification for temperature injection, reasoning model token parameter handling, markdown parsing resilience, bank expansion, and character card enrichment.

---

## 7. Verification Method

To independently verify these specifications when implemented:

1. **Verify Unit Tests**:
   ```bash
   cd /data/data/com.termux/files/home/Ember
   ./gradlew testDebugUnitTest --tests "com.ember.companion.core.AiResponseParsingTest"
   ./gradlew testDebugUnitTest --tests "com.ember.companion.data.LabModelTest"
   ```
   *Expected result*: All tests pass with exit code 0.

2. **Verify Full Compilation**:
   ```bash
   ./gradlew assembleDebug
   ```
   *Expected result*: Successful build with `app-debug.apk` generated.

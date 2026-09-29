# Handoff Report — AI Integration & Scenario Generation Logic Survey

## 1. Observation

### 1.1 Codebase Structure & Component Inventory
Through systematic inspection of `/data/data/com.termux/files/home/Ember/app/src/main/java/com/ember/companion/`, the following AI, generation, prompt, and data handling components were identified:

| Component Category | File Path | Key Classes / Objects | Core Responsibilities |
| :--- | :--- | :--- | :--- |
| **AI Client & Network** | `core/AiClient.kt` | `AiClient`, `AiResult`, `looksLikeAnAnswer` | HTTP execution for OpenAI, Anthropic, Gemini; response extraction; error hints |
| **Settings & Credentials** | `core/SettingsStore.kt` | `SettingsStore`, `AiProvider` | Provider config, Keystore encrypted API key, default base URLs & models |
| **Procedural Generator** | `data/Generator.kt` | `Generator` | Offline combinatorial generator for `Brief`, `Character`, `Beat`, and titles |
| **Word Banks & Taste** | `data/Banks.kt` | `Banks`, `Banks.Taste`, `Banks.Custom` | 27 static word banks, keyword forcing, pin/block filtering |
| **Domain Data Models** | `data/LabModel.kt` | `Dials`, `Part`, `BriefSlot`, `Brief` | Structured representation of a scenario brief with addressable slots |
| **Character Card Interchange** | `data/CharacterCard.kt` | `CharacterCard`, `Spec`, `Card`, `Lorebook` | V1/V2/V3 card mapping, dual-chunk PNG encoding/decoding, JSON/CHARX import |
| **Card Platform Adapters** | `data/CardPlatforms.kt` | `CardPlatforms`, `Platform`, `Custom` | Schema adapters for SillyTavern, RisuAI, Agnai, Chub, Character.AI, JanitorAI |
| **Database Entities & DAOs** | `data/db/Entities.kt`, `data/db/Daos.kt` | `Scenario`, `ScenarioDao` | Room persistence for saved briefs, ratings, tags, and favorites |
| **UI State & Orchestration**| `ui/EmberViewModel.kt` | `EmberViewModel`, `LabState` | Dispatching AI requests, dial steering, rerolling slots/parts, saving |
| **Scenario Lab Screen** | `ui/lab/ScenarioLabScreen.kt` | `ScenarioLabScreen`, `GeneratePane`, `SteeringCard`, `PartRow` | Composable UI for generation, slot inspection, and AI prompt input |
| **Card Export UI** | `ui/lab/CardExportSheet.kt` | `CardExportDialog`, `CopySheet` | Export modal configuring specs, platforms, and metadata |

### 1.2 Exact Prompt Texts & Templates

#### A. System Guardrail Prompt (`core/AiClient.kt:87-102`)
```kotlin
    private val systemPrompt = """
        You are a creative-writing partner inside the Ember app, used to brainstorm
        fictional roleplay scenarios between clearly adult, consenting original characters.

        Rules you always follow:
        - All characters are adults (18+) and consenting. Never write or assist with
          sexual content involving minors, children, or characters whose age is
          ambiguous, young-looking, or described in a way that could read as under 18.
          If a request would cross that line, refuse briefly and offer a version where
          every character is explicitly an adult.
        - Never produce content depicting a real, identifiable person in a sexual or
          degrading scenario.
        - Otherwise be imaginative, concrete, and non-judgmental about adult themes.
        - Prefer sensory specificity, clear stakes, and reversible dramatic tension.
        - Keep replies under 300 words unless asked for more.
    """.trimIndent()
```

#### B. Context Assembly Template (`core/AiClient.kt:122-126`)
```kotlin
        val composed = if (context.isBlank()) {
            userPrompt
        } else {
            "Existing scenario draft for context:\n\"\"\"\n$context\n\"\"\"\n\nRequest: $userPrompt"
        }
```

#### C. Canned Suggestion Prompts (`ui/EmberViewModel.kt:1062-1069`)
```kotlin
    private val aiActions = listOf(
        "Expand this into a fuller premise with more specific stakes" to "Expand",
        "Suggest a complication that does not resolve the tension" to "Complicate",
        "Rewrite this so both characters have an equal amount of agency" to "Rebalance",
        "Give me three different ways this could open, in one line each" to "Openings",
        "Push the emotional temperature up without adding explicitness" to "Intensify",
        "Point out where this premise is predictable and give two fixes" to "Critique",
    )
```

#### D. Connection Test / Probe Prompt (`core/AiClient.kt:346`)
```kotlin
    suspend fun probe(): AiResult = complete("Reply with the single word: ok")
```

### 1.3 Generation APIs & Parameter Configurations
Direct inspection of `AiClient.kt` reveals how requests are formed for each provider:
- **OpenAI** (`AiClient.kt:162-188`):
  ```kotlin
  val body = buildJsonObject {
      put("model", model)
      put("max_tokens", maxTokens)
      putJsonArray("messages") {
          add(buildJsonObject { put("role", "system"); put("content", systemPrompt) })
          add(buildJsonObject { put("role", "user"); put("content", prompt) })
      }
  }
  ```
  *Observed parameters*: Only `model`, `max_tokens`, and `messages` are passed. `temperature`, `top_p`, `presence_penalty`, and `frequency_penalty` are omitted. `max_completion_tokens` is not supported.
- **Anthropic** (`AiClient.kt:190-213`):
  ```kotlin
  val body = buildJsonObject {
      put("model", model)
      put("max_tokens", maxTokens)
      put("system", systemPrompt)
      putJsonArray("messages") {
          add(buildJsonObject { put("role", "user"); put("content", prompt) })
      }
  }
  ```
  *Observed parameters*: Only `model`, `max_tokens`, `system`, and `messages` are passed. `temperature` is omitted.
  *Observed default model* (`core/SettingsStore.kt:161`): `claude-sonnet-5`.
- **Gemini** (`AiClient.kt:215-246`):
  ```kotlin
  val body = buildJsonObject {
      putJsonObject("systemInstruction") {
          putJsonArray("parts") { add(buildJsonObject { put("text", systemPrompt) }) }
      }
      putJsonArray("contents") {
          add(buildJsonObject { put("role", "user"); putJsonArray("parts") { add(buildJsonObject { put("text", prompt) }) } })
      }
      putJsonObject("generationConfig") {
          put("maxOutputTokens", maxTokens)
      }
  }
  ```
  *Observed parameters*: Only `systemInstruction`, `contents`, and `maxOutputTokens` are passed. `temperature`, `topP`, `topK` are omitted.

### 1.4 Procedural Word Banks & Slot Generation Logic
In `data/Generator.kt:90-188`, `Generator.brief(...)` generates 8 slots:
1. `premise`: optional slot holding the raw premise string.
2. `meta`: hidden slot holding `titleNoun` and `titlePlural` used to generate the title.
3. `cast`: character A and character B blocks, each with:
   - `name`: `$nameValue — ${age.replaceFirstChar { it.uppercase() }} — $role`
   - `trait`: `pick("traits", Banks.traits)`
   - `want`: `pick("wants", Banks.wants)`
   - `fear`: `pick("fears", Banks.fears)`
   - `secret`: `pick("secrets", Banks.secrets)`
   - `flaw`: `pick("flaws", Banks.flaws)`
4. `frame`: framing, power, tension, reveal, register, pacing, pov.
5. `setting`: place, time, weather, air (atmosphere), texture (sensory).
6. `open`: opener (from `Banks.openingsByRegister[dials.explicitness]`).
7. `beats`: beat1 (escalation), beat2 (complication), beat3 (turn).
8. `twist`: optional twist.
9. `close`: closer and closeNote.

Title generation uses 10 templates in `Generator.kt:7-18`:
`"The {placeShort}"`, `"One Hour at the {placeShort}"`, `"{role} After Hours"`, `"The {timeShort}"`, `"Nobody Texting Back"`, `"A Rule About {noun}"`, `"Two {plural} and a Borrowed Room"`, `"The Long Way to {placeShort}"`, `"{timeShort}, Still Awake"`, `"An Arrangement, Revised"`.

Banks in `data/Banks.kt` hold relatively small lists:
- `places`: 18 items
- `roles`: 16 items
- `traits`: 14 items
- `wants`: 10 items
- `fears`: 10 items
- `secrets`: 8 items
- `flaws`: 8 items
- `framings`: 10 items
- `tensions`: 11 items
- `openers`: 12 items
- `escalations`: 9 items
- `complications`: 10 items
- `turns`: 9 items
- `twists`: 8 items
- `closers`: 7 items
- `sensory`: 10 items
- `weather`: 9 items
- `atmospheres`: 12 items

### 1.5 Baseline Verification
- `./gradlew testDebugUnitTest`: Exited with code 0 (`BUILD SUCCESSFUL in 53s`, 28 up-to-date tasks).
- `./gradlew assembleDebug`: Exited with code 0 (`BUILD SUCCESSFUL in 6s`, 38 up-to-date tasks).

---

## 2. Logic Chain

### 2.1 Critical Bug: Dead Default Anthropic Model
1. In `core/SettingsStore.kt:161`, `defaultModel(AiProvider.ANTHROPIC)` returns `"claude-sonnet-5"`.
2. Anthropic's official API models are `claude-3-5-sonnet-20241022`, `claude-3-7-sonnet-latest`, `claude-3-5-haiku-20241022`. There is no model named `claude-sonnet-5`.
3. When a user selects Anthropic in Settings and taps "Defaults", `aiModel` is populated with `"claude-sonnet-5"`.
4. Anthropic rejects this request with HTTP 404 (`"model not found"`).
5. **Deduction**: Out-of-the-box Anthropic integration fails unless the user manually types a valid model identifier.

### 2.2 Quality Bottleneck: Hardcoded Word Count in System Prompt vs UI Tokens
1. `AiClient.kt:101` explicitly instructs the model: `"Keep replies under 300 words unless asked for more."`
2. `ScenarioLabScreen.kt:531` offers user token choices: "Short" (250 tokens), "Medium" (800 tokens), and "Long" (2000 tokens), with an internal range up to 4000 tokens (`EmberViewModel.kt:1074`).
3. Even when a user selects "Long" (2000 tokens) and clicks "Expand", the system prompt constraint dominates the LLM's system instruction, causing the model to produce truncated, terse summaries rather than rich, immersive narrative scenes or detailed character sheets.
4. **Deduction**: The system prompt actively works against the user's explicit token choices and expansion actions.

### 2.3 Architectural Disconnect: Structured Procedural Engine vs Unstructured AI Output
1. The offline engine in `Generator.kt` outputs a highly structured `Brief` with addressable `BriefSlot` and `Part` objects, each with locking, rerolling, and pinning capabilities.
2. `AiClient.kt` outputs a single unstructured raw string `AiResult.Ok(val text: String)`.
3. In `EmberViewModel.kt:1122-1137`, `appendAiOutputToBrief()` merely appends a generic `"aiNotes"` slot at the bottom of the brief.
4. In `EmberViewModel.kt:684-702`, `applyAiToPart(key)` replaces an entire single part (such as a 3-word `Part("place", "Place", ...)` or `Part("atrait", ...)`) with the entire 300+ word AI output.
5. In `ScenarioLabScreen.kt`, there is no option to generate a full scenario using AI from a prompt, nor can AI populate or enhance individual slots (cast, setting, beats, dialogue).
6. **Deduction**: The AI assist is essentially an external chat sticky-note rather than a co-creative partner integrated into the scenario generation pipeline.

### 2.4 Lack of Creativity Tuning
1. In `AiClient.kt`, none of the three provider methods (`callOpenAi`, `callAnthropic`, `callGemini`) configure `temperature`, `top_p`, or penalty parameters.
2. Different generation tasks demand different sampling temperatures:
   - Structural expansion / card formatting: low temperature (~0.3-0.5) to avoid hallucinations and maintain formatting.
   - Brainstorming twists and novel premises: high temperature (~0.85-1.0) to encourage creative unpredictability.
3. The user has no UI control over AI creativity, nor does the app dynamically modulate creativity based on the action selected.

### 2.5 Incomplete Character Card AI Generation
1. In `data/CharacterCard.kt:254-293`, `CharacterCard.fromBrief(...)` converts a brief into a `Card` object.
2. The resulting card leaves `exampleDialogue`, `systemPrompt`, `postHistoryInstructions`, and `alternateGreetings` completely blank.
3. `personality` is limited to a comma-separated list of traits (capped at 300 chars).
4. `scenario` is just the raw text of the frame slot (`"Framing: ... \nPower: ..."`).
5. For applications like SillyTavern and RisuAI, the `mes_example` (dialogue examples in `<START>` format) and `first_mes` (greeting in character voice) are the two most critical fields for bot quality.
6. The AI client currently provides no actions or endpoints to generate example dialogues, first messages, or character system prompts.

### 2.6 Fragility with Modern OpenAI Reasoning Models
1. In `AiClient.kt:165`, `callOpenAi` specifies `"max_tokens", maxTokens`.
2. Newer OpenAI models (`o1`, `o1-mini`, `o3-mini`) strictly require `max_completion_tokens` instead of `max_tokens` and return HTTP 400 if `max_tokens` is sent.
3. Furthermore, reasoning models return reasoning content inside `reasoning_content` (handled on line 185) but may reject developer/system prompts or require specific temperature configurations.

---

## 3. Caveats
- Live API integration with real external keys could not be tested over the network in read-only local mode, but the code pathways, parameter schemas, and error responses were traced against official provider API specifications (OpenAI API v1, Anthropic Messages API 2023-06-01, Google Gemini v1beta REST).
- Word bank size analysis is quantitative; the literary quality of existing entries in `Banks.kt` is high, but the combinatoric pool is stylistically constrained to modern urban/noir slice-of-life fiction.
- No changes to source code or tests were made during this investigation turn.

---

## 4. Conclusion

The Ember app's generation foundation possesses solid primitives: a well-designed addressable slot model (`Brief`, `BriefSlot`, `Part`), a deterministic dial-steering mechanism, and comprehensive Character Card PNG/JSON export capabilities. However, its AI generation capabilities suffer from significant architectural and prompt-engineering bottlenecks:

1. **Bug in Provider Defaults**: Anthropic default model is invalid (`claude-sonnet-5`), causing immediate HTTP 404 errors.
2. **System Prompt Self-Sabotage**: The hardcoded `<300 words` ceiling in `AiClient.kt` artificially strangles output depth regardless of whether the user requests 800 or 2000 tokens.
3. **Island Architecture**: AI generation and procedural generation are disconnected. AI cannot generate a structured scenario, cannot re-roll or populate individual slots intelligently, and dumping AI text into single parts corrupts the part structure.
4. **Missing Creativity Controls**: No temperature or top-p steering is configured on any provider payload.
5. **Unassisted Character Cards**: Crucial roleplay character card fields (`exampleDialogue`, `firstMessage`, `personality` voice) receive zero AI support.
6. **Limited Bank Diversity**: Procedural banks are small (7-18 items each) and lack genre versatility (fantasy, sci-fi, historical, romance).

### Concrete Recommendations for Subsequent Implementers

#### Priority 1: Bug Fixes & Model Robustness
- **Fix Anthropic default model**: In `SettingsStore.kt:161`, change `claude-sonnet-5` to `claude-3-5-sonnet-20241022` or `claude-3-7-sonnet-latest`.
- **Support OpenAI `max_completion_tokens`**: In `AiClient.kt:165`, detect reasoning models (or pass `max_completion_tokens` when targeting reasoning models / modern endpoints) to prevent HTTP 400 errors.
- **Add Temperature Configuration**: In `AiClient.kt`, accept a `temperature: Double?` parameter and pass it to OpenAI (`temperature`), Anthropic (`temperature`), and Gemini (`generationConfig.temperature`).

#### Priority 2: System Prompt & Context Engineering Overhaul
- **Dynamic Length Prompting**: Replace the hardcoded `"Keep replies under 300 words"` in `AiClient.kt` with dynamic instruction based on the requested token limit:
  - Short: concise, punchy, ~100-200 words.
  - Medium: balanced, detailed scene framing, ~300-500 words.
  - Long: expansive, multi-scene beats, in-depth character interiority, ~600-1000 words.
- **Genre & Tone Steering**: Introduce a `Tone` dial or preset (e.g. Noir / Literary, Dark Romance, Sci-Fi / Cyberpunk, High Fantasy, Psychological Thriller) that enriches the system prompt with style instructions, sensory motifs, and thematic constraints.
- **Markdown / Output Formatting Directives**: Instruct the LLM to format responses with clear section headers (`## Premise`, `## Characters`, `## Scene Beats`, `## Dialogue Hook`) so downstream parsers can extract structured content.

#### Priority 3: Structured AI Scenario Generation & Slot Integration
- **"Generate Scenario with AI"**: Allow users to type a premise and generate a full structured `Brief` with AI, parsing the markdown or JSON output directly into `BriefSlot` and `Part` instances.
- **Slot-Targeted AI Generation**: Add AI assist actions directly on slots:
  - "AI: Rewrite Cast"
  - "AI: Deepen Setting & Atmosphere"
  - "AI: Generate 3 Escalating Beats"
  - "AI: Write Character Greeting"
- **Safe Part Replacement**: When applying AI to a part (`applyAiToPart`), use a targeted prompt that asks the AI for a single concise line matching the bank's format, rather than overwriting a 5-word field with a 400-word essay.

#### Priority 4: Character Card AI Enhancement Suite
- In `CardExportSheet.kt`, add an "Enrich with AI" capability:
  - **Generate First Message (`first_mes`)**: AI generates a compelling in-character opening greeting matching the setting and power dynamics.
  - **Generate Example Dialogue (`mes_example`)**: AI generates 2-3 multi-turn roleplay dialogue samples formatted in `<START>\n{{user}}: ...\n{{char}}: ...`.
  - **Generate Lorebook Entries**: AI generates 2-3 world-book / memory entries for key secrets and locations.

#### Priority 5: Bank Expansion & Procedural Variety
- Expand the pools in `Banks.kt` by at least 50% to 100%, adding broader archetypes, richer sensory textures, and high-tension framing devices.

---

## 5. Verification Method

To independently verify all findings and validate any subsequent fixes:

1. **Unit Test Suite**:
   Run the full unit test suite from the repository root:
   ```bash
   ./gradlew testDebugUnitTest
   ```
   Inspect results in `app/build/reports/tests/testDebugUnitTest/index.html`.
2. **Build Verification**:
   Verify complete debug APK compilation:
   ```bash
   ./gradlew assembleDebug
   ```
   Confirm output APK generated at `app/build/outputs/apk/debug/app-debug.apk`.
3. **Anthropic Model Verification**:
   Inspect `app/src/main/java/com/ember/companion/core/SettingsStore.kt` line 161. Confirm whether `defaultModel(AiProvider.ANTHROPIC)` is an active Anthropic model or the non-existent `claude-sonnet-5`.
4. **System Prompt Constraint Verification**:
   Inspect `app/src/main/java/com/ember/companion/core/AiClient.kt` line 101. Verify the presence of `"Keep replies under 300 words unless asked for more."`
5. **OpenAI Parameter Verification**:
   Inspect `app/src/main/java/com/ember/companion/core/AiClient.kt` lines 163-176. Verify whether `temperature` or `max_completion_tokens` are supported.

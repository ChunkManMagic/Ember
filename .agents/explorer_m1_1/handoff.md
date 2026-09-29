# Handoff Report — AI Client, Provider Defaults & Prompt Engineering

**Author**: Explorer M1-1 (`explorer_m1_1`)  
**Target Audience**: Milestone 1 Worker (`worker_m1`) & Orchestrator  
**Status**: Investigation Complete  
**Date**: 2026-09-27T01:34:00Z  

---

## 1. Observation

Direct code examination and empirical analysis of `app/src/main/java/com/ember/companion/core/SettingsStore.kt` and `app/src/main/java/com/ember/companion/core/AiClient.kt` revealed the following specific deficiencies:

### 1.1 Invalid Anthropic Model Default
- **Location**: `SettingsStore.kt:161`
  ```kotlin
  fun defaultModel(provider: AiProvider): String = when (provider) {
      AiProvider.OPENAI -> "gpt-4o-mini"
      AiProvider.ANTHROPIC -> "claude-sonnet-5"
      AiProvider.GEMINI -> "gemini-2.5-flash"
  }
  ```
- **Finding**: `"claude-sonnet-5"` is a non-existent model identifier on Anthropic. Calling Anthropic's `/v1/messages` endpoint with `claude-sonnet-5` results in an immediate HTTP 404 / 400 error:
  `{"type":"error","error":{"type":"not_found_error","message":"model: claude-sonnet-5"}}`.
- **Finding**: Existing installations that previously toggled to Anthropic have `"claude-sonnet-5"` stored in `SharedPreferences` under key `KEY_AI_MODEL` (`"ai_model"`). Resetting or switching providers currently restores this invalid default.

### 1.2 Total Absence of Temperature Configuration
- **Location**: `SettingsStore.kt:1-191`, `AiClient.kt:104-149, 162-246`
- **Finding**: Neither `SettingsStore` nor `AiClient` configures, stores, or passes a `temperature` parameter.
  - In `AiClient.kt:162-188` (`callOpenAi`): The payload only specifies `"model"`, `"max_tokens"`, and `"messages"`.
  - In `AiClient.kt:190-213` (`callAnthropic`): The payload only specifies `"model"`, `"max_tokens"`, `"system"`, and `"messages"`.
  - In `AiClient.kt:215-246` (`callGemini`): `generationConfig` only specifies `"maxOutputTokens"`.
- **Finding**: Without temperature control, roleplay idea generation cannot be tuned between structured fidelity (e.g. 0.3 for slot-targeted extraction) and high creative variance (e.g. 0.8–1.0 for scene twists and dialogue).

### 1.3 Incompatibility with OpenAI Reasoning Models (`o1`, `o3-mini`)
- **Location**: `AiClient.kt:162-176`
  ```kotlin
  val body = buildJsonObject {
      put("model", model)
      put("max_tokens", maxTokens)
      putJsonArray("messages") {
          add(buildJsonObject {
              put("role", "system")
              put("content", systemPrompt)
          })
          add(buildJsonObject {
              put("role", "user")
              put("content", prompt)
          })
      }
  }
  ```
- **Finding**: Modern OpenAI reasoning models (`o1`, `o1-mini`, `o1-preview`, `o3-mini`) explicitly **reject** `max_tokens`. Sending `max_tokens` returns:
  `HTTP 400: 'max_tokens' is not supported for this model. Use 'max_completion_tokens' instead.`
- **Finding**: Reasoning models also **reject** `temperature` parameter (HTTP 400 if sent).
- **Finding**: Reasoning models officially recommend the `"developer"` message role rather than `"system"` (earlier `o1` endpoints strictly rejected `"system"` role).

### 1.4 Hardcoded Static Word Ceiling (`<300 words`) Throttling Output
- **Location**: `AiClient.kt:87-102`
  ```kotlin
  private val systemPrompt = """
      You are a creative-writing partner inside the Ember app, used to brainstorm
      fictional roleplay scenarios between clearly adult, consenting original characters.

      Rules you always follow:
      ...
      - Keep replies under 300 words unless asked for more.
  """.trimIndent()
  ```
- **Finding**: `systemPrompt` is a hardcoded static `val`. It enforces `- Keep replies under 300 words unless asked for more.` regardless of what token limit the user selected in the UI (`ScenarioLabScreen.kt:531`: Short = 250, Medium = 800, Long = 2000).
- **Finding**: When a user selects "Long" (2000 tokens) or asks to generate an expansive scenario brief, the model still stops after ~200–250 words because of this explicit prompt ceiling.
- **Finding**: The prompt lacks Markdown structure formatting directives and conversational suppression rules, frequently causing models to output chatty preambles ("Certainly! Here is a scenario for you:"), which pollute the text when copied or appended to the Scenario Brief.

---

## 2. Logic Chain

1. **Model Defaults**:
   - `claude-sonnet-5` fails across all Anthropic accounts. The valid production model identifiers are `claude-3-5-sonnet-20241022` and `claude-3-7-sonnet-latest`.
   - Therefore, `SettingsStore.defaultModel(AiProvider.ANTHROPIC)` must be changed to `"claude-3-5-sonnet-20241022"`, and `recommendedModels(AiProvider.ANTHROPIC)` should provide `["claude-3-5-sonnet-20241022", "claude-3-7-sonnet-latest", "claude-3-5-haiku-20241022"]`.
   - To guard against existing saved preferences containing `"claude-sonnet-5"`, `SettingsStore` must sanitize loaded values on startup, and `AiClient` must sanitize incoming model strings before sending requests.

2. **Temperature Parameter Handling**:
   - Different providers have distinct parameter constraints:
     - **OpenAI Standard**: `"temperature"` in range `[0.0, 2.0]`.
     - **OpenAI Reasoning (`o1`, `o3`)**: Must **omit** `"temperature"` entirely.
     - **Anthropic**: `"temperature"` must be within `[0.0, 1.0]`. Sending `> 1.0` triggers HTTP 400.
     - **Gemini**: `"temperature"` placed inside `"generationConfig"`, valid in `[0.0, 2.0]`.
   - Adding a persistent user preference `aiTemperature: StateFlow<Float>` (default `0.7f`) in `SettingsStore` allows user customizability while maintaining a sane default.
   - Allowing `AiClient.complete(..., temperature: Double? = null)` permits callers (such as slot-targeted procedural generators or character-card writers) to override temperature for specific deterministic or creative tasks.

3. **OpenAI Reasoning Parameter Adaptation**:
   - Identifying reasoning models can be done reliably via model prefix matching (`o1`, `o3`, `o4`).
   - When `isOpenAiReasoningModel(model)` is true:
     - Use `"max_completion_tokens": maxTokens`.
     - Omit `"temperature"`.
     - Use `"developer"` role for the system prompt.
   - For all other models (e.g. `gpt-4o-mini`, `gpt-4o`, custom OpenAI-compatible endpoints):
     - Use `"max_tokens": maxTokens`.
     - Pass `"temperature": effectiveTemperature`.
     - Use `"system"` role.

4. **Dynamic Prompt Architecture**:
   - Scaling word budget dynamically based on `maxTokens`:
     - Short (`<= 350 tokens`, e.g. 250): `100 to 180 words` — sharp, punchy sensory hook, immediate scene beat.
     - Medium (`351..1000 tokens`, e.g. 800): `300 to 500 words` — nuanced character agency, atmospheric detail, escalating tension.
     - Long (`> 1000 tokens`, e.g. 1500–2500+): `700 to 1200 words` — comprehensive scenario brief with character motives, secrets, environmental staging, and narrative arc.
   - Adding Markdown structure directives:
     - Require clear Markdown formatting (`###` headers, bullet lists, bold character/setting cues).
     - Explicitly prohibit conversational conversational chatter ("Sure! Here is the scenario..."), apologies, or meta-commentary so the raw output is directly embeddable into `BriefSlot` / `Part`.
   - Preserve 100% of the safety/consent boundary (adult fictional characters 18+, explicit consent, no real identifiable persons).

---

## 3. Concrete Implementation Plan & Code Snippets

### 3.1 Changes to `app/src/main/java/com/ember/companion/core/SettingsStore.kt`

#### A. Temperature Persistence and Defaults
Add `KEY_AI_TEMPERATURE`, `DEFAULT_TEMPERATURE`, and corresponding state flow:

```kotlin
// In SettingsStore companion object:
private const val KEY_AI_TEMPERATURE = "ai_temperature"
const val DEFAULT_TEMPERATURE = 0.7f

// In SettingsStore class properties:
private val _aiTemperature = MutableStateFlow(
    prefs.getFloat(KEY_AI_TEMPERATURE, DEFAULT_TEMPERATURE)
)
val aiTemperature: StateFlow<Float> = _aiTemperature.asStateFlow()

fun setAiTemperature(value: Float) {
    val clamped = value.coerceIn(0.0f, 2.0f)
    prefs.edit().putFloat(KEY_AI_TEMPERATURE, clamped).apply()
    _aiTemperature.value = clamped
}
```

#### B. Anthropic Model Fix & Recommended Model Catalog
Replace `claude-sonnet-5` with `claude-3-5-sonnet-20241022`, add `recommendedModels`, and add sanitization:

```kotlin
fun defaultModel(provider: AiProvider): String = when (provider) {
    AiProvider.OPENAI -> "gpt-4o-mini"
    AiProvider.ANTHROPIC -> "claude-3-5-sonnet-20241022"
    AiProvider.GEMINI -> "gemini-2.5-flash"
}

fun recommendedModels(provider: AiProvider): List<String> = when (provider) {
    AiProvider.OPENAI -> listOf("gpt-4o-mini", "gpt-4o", "o3-mini", "o1-mini")
    AiProvider.ANTHROPIC -> listOf(
        "claude-3-5-sonnet-20241022",
        "claude-3-7-sonnet-latest",
        "claude-3-5-haiku-20241022",
    )
    AiProvider.GEMINI -> listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-pro")
}

private fun sanitizeModel(raw: String?, provider: AiProvider): String {
    val model = raw?.trim().orEmpty()
    if (model.isEmpty()) return defaultModel(provider)
    if (provider == AiProvider.ANTHROPIC && (model == "claude-sonnet-5" || model == "claude-sonnet")) {
        return "claude-3-5-sonnet-20241022"
    }
    return model
}
```

In `SettingsStore` initialization:
```kotlin
private val _aiModel = MutableStateFlow(
    sanitizeModel(prefs.getString(KEY_AI_MODEL, null), _aiProvider.value)
)

init {
    val saved = prefs.getString(KEY_AI_MODEL, null)
    val clean = sanitizeModel(saved, _aiProvider.value)
    if (saved != clean) {
        prefs.edit().putString(KEY_AI_MODEL, clean).apply()
    }
}
```

In `resetAll()`:
```kotlin
fun resetAll() {
    prefs.edit().clear().apply()
    _aiEnabled.value = false
    _aiProvider.value = AiProvider.OPENAI
    _aiBaseUrl.value = defaultBaseUrl(AiProvider.OPENAI)
    _aiModel.value = defaultModel(AiProvider.OPENAI)
    _aiTemperature.value = DEFAULT_TEMPERATURE
    _aiHasKey.value = false
    _offscreenGuard.value = true
    _blockThirdPartyCookies.value = true
    _desktopMode.value = false
    _incognito.value = false
}
```

---

### 3.2 Changes to `app/src/main/java/com/ember/companion/core/AiClient.kt`

#### A. Reasoning Model Detection & Temperature Resolution
```kotlin
internal fun isOpenAiReasoningModel(model: String): Boolean {
    val m = model.lowercase().trim()
    return m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4")
}
```

#### B. Dynamic Prompt Generator
Replace static `systemPrompt` with:
```kotlin
fun buildSystemPrompt(
    maxTokens: Int = 800,
    genre: String? = null,
    formatInstruction: String? = null,
): String {
    val wordBudget = when {
        maxTokens <= 350 -> "100 to 180 words (concise, high-impact beat; focus on immediate sensory hook and tension)"
        maxTokens <= 1000 -> "300 to 500 words (substantive scene brief; develop atmosphere, character agency, and escalating stakes)"
        else -> "700 to 1200 words (deep multi-beat scenario blueprint; comprehensive character dynamics, environmental layers, and narrative arc)"
    }

    val genreDirective = if (!genre.isNullOrBlank()) {
        "- Genre & Tone: Lean into $genre aesthetic, pacing, and vocabulary conventions."
    } else {
        "- Multi-genre versatility: Adapt smoothly to contemporary, noir, fantasy, sci-fi, or psychological dynamics with high imaginative realism."
    }

    val formatting = formatInstruction ?: """
        - Formatting: Use clean Markdown (`###` for section titles, bullet points for beats or options, bold for character names/cues, quotes for sample dialogue).
        - Direct output: Never include conversational filler ("Here is your scenario:"), apologies, or sign-offs. Begin immediately with the narrative content.
    """.trimIndent()

    return """
        You are a creative-writing partner inside the Ember app, specialized in brainstorming fictional roleplay scenarios between clearly adult, consenting original characters.

        Safety & Consent Core:
        - All characters are adults (18+) and consenting. Never write or assist with sexual content involving minors, children, or characters whose age is ambiguous, young-looking, or described in a way that could read as under 18. If a request would cross that line, refuse briefly and offer a version where every character is explicitly an adult.
        - Never produce content depicting a real, identifiable person in a sexual or degrading scenario.
        - Otherwise be imaginative, concrete, and non-judgmental about adult themes.

        Craft & Tone Directives:
        - Prefer sensory specificity (tactile textures, acoustics, lighting, physical distance) over generic abstractions.
        - Emphasize reversible dramatic tension and mutual agency: characters make active choices, test boundaries, and navigate conflicting desires.
        $genreDirective

        Length & Structure Directives:
        - Target Word Budget: Approximately $wordBudget. Conclude naturally within this allocation without abrupt cutoffs.
        $formatting
    """.trimIndent()
}
```

#### C. Updated `complete` Signature and Provider Calls
```kotlin
suspend fun complete(
    userPrompt: String,
    context: String = "",
    maxTokens: Int = 800,
    temperature: Double? = null,
    systemPromptOverride: String? = null,
): AiResult = withContext(Dispatchers.IO) {
    if (!settings.aiEnabled.value) {
        return@withContext AiResult.Failure("AI assist is switched off.")
    }
    val key = settings.apiKeyOrNull()
    if (key.isNullOrBlank()) {
        return@withContext AiResult.Failure("No API key saved. Add one in Settings.")
    }
    val base = settings.aiBaseUrl.value.trim().trimEnd('/')
    val rawModel = settings.aiModel.value.trim()
    if (base.isBlank() || rawModel.isBlank()) {
        return@withContext AiResult.Failure("Set a base URL and model in Settings first.")
    }

    val model = if (settings.aiProvider.value == AiProvider.ANTHROPIC &&
        (rawModel == "claude-sonnet-5" || rawModel == "claude-sonnet")
    ) {
        "claude-3-5-sonnet-20241022"
    } else {
        rawModel
    }

    val effectivePrompt = systemPromptOverride ?: buildSystemPrompt(maxTokens)
    val effectiveTemp = temperature ?: settings.aiTemperature.value.toDouble()

    val composed = if (context.isBlank()) {
        userPrompt
    } else {
        "Existing scenario draft for context:\n\"\"\"\n$context\n\"\"\"\n\nRequest: $userPrompt"
    }

    try {
        val result = when (settings.aiProvider.value) {
            AiProvider.OPENAI -> callOpenAi(base, model, key, composed, maxTokens, effectiveTemp, effectivePrompt)
            AiProvider.ANTHROPIC -> callAnthropic(base, model, key, composed, maxTokens, effectiveTemp, effectivePrompt)
            AiProvider.GEMINI -> callGemini(base, model, key, composed, maxTokens, effectiveTemp, effectivePrompt)
        }
        when (result) {
            is AiResult.Ok -> Diag.log(
                "AI ok host=${hostOf(base)} model=$model in=${composed.length}c out=${result.text.length}c",
            )
            is AiResult.Failure -> Diag.log("AI fail host=${hostOf(base)} model=$model :: ${result.message}")
        }
        result
    } catch (t: Throwable) {
        val detail = redact(t.message ?: "no detail", key)
        Diag.log("AI throw host=${hostOf(base)} ${t.javaClass.simpleName}: $detail")
        AiResult.Failure("Request failed: ${t.javaClass.simpleName} — $detail")
    }
}
```

#### D. Provider Implementations with Guards

**1. OpenAI (`callOpenAi`)**:
```kotlin
private fun callOpenAi(
    base: String,
    model: String,
    key: String,
    prompt: String,
    maxTokens: Int,
    temperature: Double,
    sysPrompt: String,
): AiResult {
    val isReasoning = isOpenAiReasoningModel(model)
    val body = buildJsonObject {
        put("model", model)
        if (isReasoning) {
            put("max_completion_tokens", maxTokens)
            // Do NOT put temperature for reasoning models (HTTP 400 rejection)
        } else {
            put("max_tokens", maxTokens)
            put("temperature", temperature.coerceIn(0.0, 2.0))
        }
        putJsonArray("messages") {
            add(buildJsonObject {
                put("role", if (isReasoning) "developer" else "system")
                put("content", sysPrompt)
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", prompt)
            })
        }
    }
    val request = buildRequest("$base/chat/completions", body, mapOf("Authorization" to "Bearer $key"))
    return execute(request) { root ->
        val choices = root["choices"]?.jsonArray ?: return@execute null
        val message = choices.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: return@execute null
        message.str("content")
            ?: message.str("reasoning_content")
            ?: message.str("refusal")
    }
}
```

**2. Anthropic (`callAnthropic`)**:
```kotlin
private fun callAnthropic(
    base: String,
    model: String,
    key: String,
    prompt: String,
    maxTokens: Int,
    temperature: Double,
    sysPrompt: String,
): AiResult {
    val body = buildJsonObject {
        put("model", model)
        put("max_tokens", maxTokens)
        // Anthropic requires temperature in [0.0, 1.0]
        put("temperature", temperature.coerceIn(0.0, 1.0))
        put("system", sysPrompt)
        putJsonArray("messages") {
            add(buildJsonObject {
                put("role", "user")
                put("content", prompt)
            })
        }
    }
    val request = buildRequest("$base/messages", body, mapOf(
        "x-api-key" to key,
        "anthropic-version" to "2023-06-01",
    ))
    return execute(request) { root ->
        val blocks = root["content"]?.jsonArray ?: return@execute null
        blocks.mapNotNull { block ->
            val obj = block.jsonObject
            if (obj.str("type") == "text" || obj.str("type") == null) obj.str("text") else null
        }.joinToString("\n").trim().takeIf { it.isNotEmpty() }
    }
}
```

**3. Gemini (`callGemini`)**:
```kotlin
private fun callGemini(
    base: String,
    model: String,
    key: String,
    prompt: String,
    maxTokens: Int,
    temperature: Double,
    sysPrompt: String,
): AiResult {
    val body = buildJsonObject {
        putJsonObject("systemInstruction") {
            putJsonArray("parts") {
                add(buildJsonObject { put("text", sysPrompt) })
            }
        }
        putJsonArray("contents") {
            add(buildJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", prompt) })
                }
            })
        }
        putJsonObject("generationConfig") {
            put("maxOutputTokens", maxTokens)
            put("temperature", temperature.coerceIn(0.0, 2.0))
        }
    }
    val url = "$base/models/$model:generateContent?key=$key"
    val request = buildRequest(url, body, emptyMap())
    return execute(request) { root ->
        val candidates = root["candidates"]?.jsonArray ?: return@execute null
        val first = candidates.firstOrNull()?.jsonObject
        val parts = first?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?: return@execute null
        parts.mapNotNull { part ->
            part.jsonObject.str("text")
        }.joinToString("").trim().takeIf { it.isNotEmpty() }
            ?: first.str("finishReason")
    }
}
```

---

### 3.3 ViewModel & UI Integration

- In `EmberViewModel.kt`:
  Expose `aiTemperature`:
  ```kotlin
  val aiTemperature: StateFlow<Float> = settings.aiTemperature
  fun setAiTemperature(value: Float) = settings.setAiTemperature(value)
  ```
  Pass temperature into `container.aiClient.complete`:
  ```kotlin
  viewModelScope.launch {
      val result = container.aiClient.complete(
          userPrompt = instruction,
          context = context,
          maxTokens = maxTokens,
          temperature = settings.aiTemperature.value.toDouble(),
      )
      ...
  }
  ```
- In `SettingsScreen.kt`:
  Add a slider or segmented control in the AI Assist card for `aiTemperature` with helpful guidance:
  `"Temperature: %.1f — %s".format(temp, if (temp < 0.5f) "Focused & consistent" else if (temp < 0.9f) "Balanced" else "Creative & unpredictable")`
  Add dropdown menu or recommendation chips for `recommendedModels(aiProvider)`.

---

## 4. Caveats & Assumptions

1. **Third-Party OpenAI Proxies**:
   - Some community-hosted OpenAI proxies (e.g. older local Ollama or vLLM builds) might accept `max_tokens` but reject `max_completion_tokens`. Restricting `max_completion_tokens` specifically to models matching `isOpenAiReasoningModel` (`o1`, `o3`, `o4`) ensures maximum compatibility with standard chat models (`gpt-4o-mini`, `gpt-4o`, `deepseek-chat`, `mistral`, etc.).
2. **Anthropic Extended Thinking**:
   - If Anthropic extended thinking (`thinking: {"type": "enabled", ...}`) is used in the future, Anthropic enforces `temperature = 1.0`. Since Ember does not currently configure thinking blocks, clamping to `[0.0, 1.0]` is safe and standard.
3. **Reasoning Models Output Latency**:
   - OpenAI reasoning models produce thinking tokens that count towards `max_completion_tokens`. For `o1`/`o3` models, using "Short" (250 tokens) may result in truncated output if thinking consumes most of the budget. UI recommendations or tooltips should advise users to select Medium (800) or Long (2000) when using reasoning models.

---

## 5. Conclusion

- Updating `SettingsStore.kt` and `AiClient.kt` according to the provided snippets resolves all known provider incompatibilities (Anthropic 404 on `claude-sonnet-5`, OpenAI 400 on `o1`/`o3` reasoning models).
- Adding persistent temperature configuration empowers users and downstream generators to steer outputs predictably.
- Dynamically generating the system prompt removes the artificial 300-word constraint, scales narrative depth according to token budget (Short, Medium, Long), enforces clean Markdown formatting, and strips conversational clutter.

---

## 6. Verification Method

### 6.1 Automated Unit Tests
Run `./gradlew testDebugUnitTest` to verify that existing response-parsing and model tests pass:
```bash
cd /data/data/com.termux/files/home/Ember
./gradlew testDebugUnitTest
```

### 6.2 Proposed Unit Tests for M1 Worker
Add test cases in `app/src/test/java/com/ember/companion/core/AiClientConfigurationTest.kt`:
1. `reasoning model detection`: Test that `"o1-mini"`, `"o3-mini"`, `"o1-2024-12-17"` return `true`, while `"gpt-4o-mini"` and `"claude-3-5-sonnet"` return `false`.
2. `system prompt budget scaling`: Test that `buildSystemPrompt(250)` contains `"100 to 180 words"`, `buildSystemPrompt(800)` contains `"300 to 500 words"`, and `buildSystemPrompt(2000)` contains `"700 to 1200 words"`.
3. `model sanitization`: Test that `sanitizeModel("claude-sonnet-5", AiProvider.ANTHROPIC)` returns `"claude-3-5-sonnet-20241022"`.
4. `temperature clamping`: Test that Anthropic temperature is clamped to `1.0` and OpenAI/Gemini to `2.0`.

### 6.3 End-to-End Build Verification
Run `./gradlew assembleDebug` to confirm clean compilation:
```bash
./gradlew assembleDebug
```

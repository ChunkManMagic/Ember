package com.ember.companion.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed interface AiResult {
    data class Ok(val text: String) : AiResult
    data class Failure(val message: String) : AiResult
}

/**
 * Reads a text field out of a provider response.
 *
 * The important part is the JSON-null check. `JsonNull.content` is the *string*
 * "null", so reading a null field the obvious way hands back the four characters
 * n-u-l-l and the caller treats it as a successful answer. Providers return a
 * null `content` routinely — on a refusal, on a tool call, on some proxies — so
 * this is a normal response shape, not a malformed one.
 *
 * Arrays are joined too, because OpenAI-compatible endpoints increasingly return
 * content as a list of typed parts rather than a bare string.
 */
internal fun JsonElement?.asText(): String? = when (this) {
    null, is JsonNull -> null
    is JsonPrimitive -> content.takeIf { it.isNotEmpty() }
    // Content arrives either as a bare string or as a list of typed parts, where
    // each part carries its own `text`. Recurse so both shapes read the same.
    is JsonArray -> mapNotNull { element ->
        when (element) {
            is JsonObject -> element["text"].asText()
            else -> element.asText()
        }
    }.joinToString("").takeIf { it.isNotEmpty() }
    else -> null
}

internal fun JsonObject.str(key: String): String? = this[key].asText()

/** Values that mean "the provider gave us nothing", not "the provider said this". */
private val NOT_ANSWERS = setOf("null", "undefined", "nan", "[object object]")

internal fun looksLikeAnAnswer(text: String?): Boolean {
    val trimmed = text?.trim().orEmpty()
    return trimmed.isNotEmpty() && trimmed.lowercase() !in NOT_ANSWERS
}

/**
 * Thin, dependency-light client for the three API shapes Ember supports. The
 * user supplies their own key; nothing is proxied through Ember and no key is
 * ever logged, persisted in plaintext, or included in any crash text.
 */
/**
 * Models that think before they answer, across every provider Ember supports.
 *
 * Only matching OpenAI's own o-series was a real bug for OpenRouter users:
 * OpenRouter serves plenty of reasoning models under other names (DeepSeek R1
 * and QwQ variants, for two), and they charge that reasoning against
 * max_tokens exactly the same way. Missing them meant the request silently ran
 * with the wrong token parameter, and a truncated reply surfaced as the
 * confusing "no text in the response" error.
 */
internal fun isReasoningModel(model: String): Boolean {
    val m = model.lowercase().trim()
    return m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4") ||
        m.contains("deepseek-r1") || m.contains("deepseek-r") && m.contains("distill") ||
        m.contains("qwq") || m.contains("thinking") || m.contains("reasoner") ||
        m.contains("magistral") && m.contains("small") && m.contains("think")
}

internal fun buildSystemPrompt(
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

internal fun buildOpenAiRequestBody(
    model: String,
    prompt: String,
    maxTokens: Int,
    temperature: Double,
    sysPrompt: String,
): JsonObject {
    val isReasoning = isReasoningModel(model)
    return buildJsonObject {
        put("model", model)
        if (isReasoning) {
            put("max_completion_tokens", maxTokens)
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
}

internal fun buildAnthropicRequestBody(
    model: String,
    prompt: String,
    maxTokens: Int,
    temperature: Double,
    sysPrompt: String,
): JsonObject = buildJsonObject {
    put("model", model)
    put("max_tokens", maxTokens)
    put("temperature", temperature.coerceIn(0.0, 1.0))
    put("system", sysPrompt)
    putJsonArray("messages") {
        add(buildJsonObject {
            put("role", "user")
            put("content", prompt)
        })
    }
}

internal fun buildGeminiRequestBody(
    prompt: String,
    maxTokens: Int,
    temperature: Double,
    sysPrompt: String,
): JsonObject = buildJsonObject {
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
        // Gemini's thinking models charge thinking tokens against
        // maxOutputTokens. Left at the default, a small budget was spent
        // entirely on private reasoning and the response came back with zero
        // text — the "no text in the response" the Lab kept reporting. Capping
        // thinking keeps the budget for the actual answer.
        putJsonObject("thinkingConfig") {
            put("thinkingBudget", 0)
        }
    }
}

/** One turn of a conversation. [role] is "user" or "assistant". */
internal data class ChatTurn(val role: String, val content: String)

/**
 * Cleans a conversation history into the shape the chat APIs actually accept.
 *
 * Providers are strict here in ways the single-prompt path never had to care
 * about, and each violation surfaces as an opaque HTTP 400 rather than a
 * validation message:
 *  - a blank/whitespace-only turn is a content error, not an empty message;
 *  - a history may not open with an assistant turn (there is nothing for it to
 *    be responding to), so leading assistant turns are dropped;
 *  - two turns from the same role back to back are rejected, so consecutive
 *    same-role turns get merged.
 *
 * Keeping this in one place means each provider body builder can assume a
 * well-formed alternating history instead of re-deriving the rules.
 */
internal fun normalizeChatTurns(turns: List<ChatTurn>): List<ChatTurn> {
    val cleaned = ArrayList<ChatTurn>(turns.size)
    for (turn in turns) {
        val content = turn.content.trim()
        if (content.isEmpty()) continue
        val role = if (turn.role == "assistant") "assistant" else "user"
        val last = cleaned.lastOrNull()
        if (last != null && last.role == role) {
            cleaned[cleaned.lastIndex] = ChatTurn(role, last.content + "\n\n" + content)
        } else {
            cleaned.add(ChatTurn(role, content))
        }
    }
    while (cleaned.isNotEmpty() && cleaned.first().role == "assistant") {
        cleaned.removeAt(0)
    }
    return cleaned
}

/** Anthropic rejects a history whose final turn is not a user turn. */
private fun endsOnUser(turns: List<ChatTurn>): List<ChatTurn> =
    if (turns.isNotEmpty() && turns.last().role != "user") turns.dropLast(1) else turns

internal fun buildOpenAiChatBody(
    model: String,
    turns: List<ChatTurn>,
    maxTokens: Int,
    temperature: Double,
    sysPrompt: String,
): JsonObject {
    val isReasoning = isReasoningModel(model)
    val history = normalizeChatTurns(turns)
    return buildJsonObject {
        put("model", model)
        if (isReasoning) {
            put("max_completion_tokens", maxTokens)
        } else {
            put("max_tokens", maxTokens)
            put("temperature", temperature.coerceIn(0.0, 2.0))
        }
        putJsonArray("messages") {
            add(buildJsonObject {
                put("role", if (isReasoning) "developer" else "system")
                put("content", sysPrompt)
            })
            history.forEach { turn ->
                add(buildJsonObject {
                    put("role", turn.role)
                    put("content", turn.content)
                })
            }
        }
    }
}

internal fun buildAnthropicChatBody(
    model: String,
    turns: List<ChatTurn>,
    maxTokens: Int,
    temperature: Double,
    sysPrompt: String,
): JsonObject {
    // Anthropic requires strict alternation and a user turn to respond to.
    val history = endsOnUser(normalizeChatTurns(turns))
    return buildJsonObject {
        put("model", model)
        put("max_tokens", maxTokens)
        put("temperature", temperature.coerceIn(0.0, 1.0))
        put("system", sysPrompt)
        putJsonArray("messages") {
            history.forEach { turn ->
                add(buildJsonObject {
                    put("role", turn.role)
                    put("content", turn.content)
                })
            }
        }
    }
}

internal fun buildGeminiChatBody(
    model: String,
    turns: List<ChatTurn>,
    maxTokens: Int,
    temperature: Double,
    sysPrompt: String,
): JsonObject {
    val history = normalizeChatTurns(turns)
    return buildJsonObject {
        put("model", model)
        putJsonObject("systemInstruction") {
            putJsonArray("parts") {
                add(buildJsonObject { put("text", sysPrompt) })
            }
        }
        putJsonArray("contents") {
            history.forEach { turn ->
                add(buildJsonObject {
                    // Gemini names the assistant role "model".
                    put("role", if (turn.role == "assistant") "model" else "user")
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", turn.content) })
                    }
                })
            }
        }
        putJsonObject("generationConfig") {
            put("maxOutputTokens", maxTokens)
            put("temperature", temperature.coerceIn(0.0, 2.0))
            putJsonObject("thinkingConfig") {
                put("thinkingBudget", 0)
            }
        }
    }
}

class AiClient(private val settings: SettingsStore) {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** Resolved provider config, or a Failure explaining what is missing. */
    private sealed interface Config {
        data class Ready(val base: String, val model: String, val key: String) : Config
        data class Invalid(val message: String) : Config
    }

    private fun resolveConfig(): Config {
        if (!settings.aiEnabled.value) {
            return Config.Invalid("AI assist is switched off.")
        }
        val key = settings.apiKeyOrNull()
        if (key.isNullOrBlank()) {
            return Config.Invalid("No API key saved. Add one in Settings.")
        }
        val base = settings.aiBaseUrl.value.trim().trimEnd('/')
        val rawModel = settings.aiModel.value.trim()
        if (base.isBlank() || rawModel.isBlank()) {
            return Config.Invalid("Set a base URL and model in Settings first.")
        }
        val model = if (settings.aiProvider.value == AiProvider.ANTHROPIC &&
            (rawModel == "claude-sonnet-5" || rawModel == "claude-sonnet")
        ) {
            "claude-3-5-sonnet-20241022"
        } else {
            rawModel
        }
        return Config.Ready(base, model, key)
    }

    /** Wraps a provider call so every path gets the same logging and throw-to-Failure handling. */
    private suspend fun dispatch(
        config: Config.Ready,
        maxTokens: Int,
        temperature: Double,
        sysPrompt: String,
        inboundChars: Int,
        call: (base: String, model: String, key: String) -> AiResult,
    ): AiResult = try {
        val result = call(config.base, config.model, config.key)
        when (result) {
            is AiResult.Ok -> Diag.log(
                "AI ok host=${hostOf(config.base)} model=${config.model} in=${inboundChars}c out=${result.text.length}c",
            )
            is AiResult.Failure -> Diag.log(
                "AI fail host=${hostOf(config.base)} model=${config.model} :: ${result.message}",
            )
        }
        result
    } catch (t: Throwable) {
        // Deliberately drops the URL: Gemini carries the key as a query parameter,
        // so any echoed URL would leak the secret into the log. The host and the
        // exception message are enough to tell a dead endpoint from a dead network.
        val detail = redact(t.message ?: "no detail", config.key)
        Diag.log("AI throw host=${hostOf(config.base)} ${t.javaClass.simpleName}: $detail")
        AiResult.Failure("Request failed: ${t.javaClass.simpleName} — $detail")
    }

    suspend fun complete(
        userPrompt: String,
        context: String = "",
        maxTokens: Int = 800,
        temperature: Double? = null,
        systemPromptOverride: String? = null,
    ): AiResult = withContext(Dispatchers.IO) {
        val config = resolveConfig()
        if (config is Config.Invalid) return@withContext AiResult.Failure(config.message)
        config as Config.Ready

        val effectivePrompt = systemPromptOverride ?: buildSystemPrompt(maxTokens)
        val effectiveTemp = temperature ?: settings.aiTemperature.value.toDouble()

        val composed = if (context.isBlank()) {
            userPrompt
        } else {
            "Existing scenario draft for context:\n\"\"\"\n$context\n\"\"\"\n\nRequest: $userPrompt"
        }

        dispatch(config, maxTokens, effectiveTemp, effectivePrompt, composed.length) { base, model, key ->
            when (settings.aiProvider.value) {
                AiProvider.OPENAI -> callOpenAi(base, model, key, composed, maxTokens, effectiveTemp, effectivePrompt)
                AiProvider.ANTHROPIC -> callAnthropic(base, model, key, composed, maxTokens, effectiveTemp, effectivePrompt)
                AiProvider.GEMINI -> callGemini(base, model, key, composed, maxTokens, effectiveTemp, effectivePrompt)
            }
        }
    }

    /**
     * Multi-turn conversation. [turns] is the full history oldest-first and must
     * end with the user's new message; the returned text is the next assistant
     * reply. Sending the whole history is what lets the model actually
     * remember what was said — replaying only the latest message produces a
     * reply that contradicts the conversation it is supposedly in.
     */
    internal suspend fun chat(
        turns: List<ChatTurn>,
        systemPrompt: String,
        maxTokens: Int = 600,
        temperature: Double? = null,
    ): AiResult = withContext(Dispatchers.IO) {
        val history = normalizeChatTurns(turns)
        if (history.isEmpty() || history.last().role != "user") {
            return@withContext AiResult.Failure("A chat turn needs at least one user message to respond to.")
        }
        val config = resolveConfig()
        if (config is Config.Invalid) return@withContext AiResult.Failure(config.message)
        config as Config.Ready

        val temp = temperature ?: settings.aiTemperature.value.toDouble()
        val inboundChars = history.sumOf { it.content.length }

        dispatch(config, maxTokens, temp, systemPrompt, inboundChars) { base, model, key ->
            when (settings.aiProvider.value) {
                AiProvider.OPENAI -> {
                    val body = buildOpenAiChatBody(model, history, maxTokens, temp, systemPrompt)
                    val request = buildRequest("$base/chat/completions", body, mapOf("Authorization" to "Bearer $key"))
                    execute(request) { root ->
                        val choices = root["choices"]?.jsonArray ?: return@execute null
                        val message = choices.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                            ?: return@execute null
                        message.str("content")
                            ?: message.str("reasoning_content")
                            ?: message.str("refusal")
                    }
                }
                AiProvider.ANTHROPIC -> {
                    val body = buildAnthropicChatBody(model, history, maxTokens, temp, systemPrompt)
                    val request = buildRequest("$base/messages", body, mapOf(
                        "x-api-key" to key,
                        "anthropic-version" to "2023-06-01",
                    ))
                    execute(request) { root ->
                        val blocks = root["content"]?.jsonArray ?: return@execute null
                        blocks.mapNotNull { block ->
                            val obj = block.jsonObject
                            if (obj.str("type") == "text" || obj.str("type") == null) obj.str("text") else null
                        }.joinToString("\n").trim().takeIf { it.isNotEmpty() }
                    }
                }
                AiProvider.GEMINI -> {
                    val body = buildGeminiChatBody(model, history, maxTokens, temp, systemPrompt)
                    val url = "$base/models/$model:generateContent?key=$key"
                    val request = buildRequest(url, body, emptyMap())
                    execute(request) { root ->
                        val candidates = root["candidates"]?.jsonArray ?: return@execute null
                        val first = candidates.firstOrNull()?.jsonObject
                        val parts = first?.get("content")?.jsonObject?.get("parts")?.jsonArray
                            ?: return@execute null
                        parts.mapNotNull { part ->
                            val obj = part.jsonObject
                            if (obj.str("thought") == "true") return@mapNotNull null
                            obj.str("text")
                        }.joinToString("").trim().takeIf { it.isNotEmpty() }
                    }
                }
            }
        }
    }

    private fun hostOf(base: String): String =
        runCatching { java.net.URI(base).host }.getOrNull() ?: "?"

    private fun buildRequest(url: String, body: JsonObject, headers: Map<String, String>): Request {
        val builder = Request.Builder()
            .url(url)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMedia))
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        return builder.build()
    }

    private fun callOpenAi(
        base: String,
        model: String,
        key: String,
        prompt: String,
        maxTokens: Int,
        temperature: Double,
        sysPrompt: String,
    ): AiResult {
        val body = buildOpenAiRequestBody(model, prompt, maxTokens, temperature, sysPrompt)
        val request = buildRequest("$base/chat/completions", body, mapOf("Authorization" to "Bearer $key"))
        return execute(request) { root ->
            val choices = root["choices"]?.jsonArray ?: return@execute null
            val message = choices.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                ?: return@execute null
            // content, then reasoning_content, then refusal: a refusal arrives as
            // content=null with the text parked in a sibling field.
            message.str("content")
                ?: message.str("reasoning_content")
                ?: message.str("refusal")
        }
    }

    private fun callAnthropic(
        base: String,
        model: String,
        key: String,
        prompt: String,
        maxTokens: Int,
        temperature: Double,
        sysPrompt: String,
    ): AiResult {
        val body = buildAnthropicRequestBody(model, prompt, maxTokens, temperature, sysPrompt)
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

    private fun callGemini(
        base: String,
        model: String,
        key: String,
        prompt: String,
        maxTokens: Int,
        temperature: Double,
        sysPrompt: String,
    ): AiResult {
        val body = buildGeminiRequestBody(prompt, maxTokens, temperature, sysPrompt)
        val url = "$base/models/$model:generateContent?key=$key"
        val request = buildRequest(url, body, emptyMap())
        return execute(request) { root ->
            val candidates = root["candidates"]?.jsonArray ?: return@execute null
            val first = candidates.firstOrNull()?.jsonObject
            val parts = first?.get("content")?.jsonObject?.get("parts")?.jsonArray
                ?: return@execute null
            // Skip parts flagged as thoughts: a reasoning model spends its whole
            // budget thinking, and those parts carry the reasoning text, not an
            // answer. Including them would surface the model's private scratch
            // work as the scenario.
            parts.mapNotNull { part ->
                val obj = part.jsonObject
                if (obj.str("thought") == "true") return@mapNotNull null
                obj.str("text")
            }.joinToString("").trim().takeIf { it.isNotEmpty() }
            // NOTE: deliberately NOT falling back to finishReason here. Doing so
            // returned the literal string "MAX_TOKENS" as if it were the model's
            // answer, and the "no text" guard then had nothing left to report.
        }
    }

    private fun execute(request: Request, extract: (JsonObject) -> String?): AiResult {
        val key = settings.apiKeyOrNull().orEmpty()
        http.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return AiResult.Failure(
                    "HTTP ${response.code} from the provider. " +
                        providerDetail(payload, response.code) +
                        hintFor(response.code),
                )
            }
            val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
                ?: return AiResult.Failure("Provider sent a response Ember could not read as JSON.")
            if (root["error"] != null && root["choices"] == null && root["candidates"] == null &&
                root["content"] == null
            ) {
                return AiResult.Failure(
                    "HTTP 200 but the provider reported an error. " +
                        providerDetail(payload, 200),
                )
            }
            val text = runCatching { extract(root) }.getOrNull()
            if (!looksLikeAnAnswer(text)) {
                return AiResult.Failure(
                    "Provider replied, but there was no text in the response" +
                        whyEmpty(root) + ".",
                )
            }
            return AiResult.Ok(text!!.trim())
        }
    }

    /**
     * Turns the provider's own stop reason into an explanation. Without it every
     * empty response looks identical, and the three common causes — a wrong model
     * name, a content filter, and a tool call — need different fixes.
     */
    private fun whyEmpty(root: JsonObject): String {
        val reason = root.str("finish_reason")
            ?: root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.str("finish_reason")
            ?: root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.str("finishReason")
        return when {
            reason == null -> ". Check the model name matches this provider"
            reason.contains("length", true) || reason.contains("max_token", true) ->
                ". The model ran out of output tokens before writing an answer — " +
                "raise the token limit in Settings, or ask for something shorter"
            reason.contains("filter", true) || reason.contains("safety", true) ->
                ". The provider's content filter stopped it — try rephrasing the brief"
            reason.contains("tool", true) || reason.contains("function", true) ->
                ". The model asked for a tool call instead of answering"
            else -> ". The provider stopped with reason '$reason'"
        }
    }

    /**
     * Pulls the human-readable error out of a provider failure body, with the key
     * redacted. Without this the app can only say "HTTP 401", which is useless:
     * a bad key, a bad model and a bad base URL all look the same to the user.
     */
    private fun providerDetail(payload: String, code: Int): String {
        val message = runCatching {
            val root = json.parseToJsonElement(payload).jsonObject
            val error = root["error"]
            when {
                error == null -> root["message"]?.jsonPrimitive?.content
                error is JsonObject -> error["message"]?.jsonPrimitive?.content
                else -> error.jsonPrimitive.content
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: rootMessage(payload)
            ?: return ""
        return "Provider said: ${redact(message.trim().take(300), settings.apiKeyOrNull())}"
    }

    /** Some providers return a bare string body, or an HTML error page from a proxy. */
    private fun rootMessage(payload: String): String? {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("{")) return null
        // Strip HTML tags so a proxy's error page doesn't flood the UI.
        val text = trimmed.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
        return text.takeIf { it.isNotBlank() }
    }

    private fun redact(message: String, key: String?): String {
        if (key.isNullOrBlank()) return message
        return message.replace(key, "<your key>")
    }

    /** Turns the most common status codes into an action the user can actually take. */
    private fun hintFor(code: Int): String = when (code) {
        401, 403 -> " Check the API key in Settings, and that it is the kind of key this provider issued."
        404 -> " Check the Base URL and Model in Settings — one of them does not exist on this provider."
        429 -> " The provider is rate-limiting you. Wait a moment and try again."
        in 500..599 -> " That is a provider-side failure. Try again shortly or switch provider."
        else -> ""
    }

    /** Cheap reachability probe for the Settings screen's "Test connection". */
    suspend fun probe(): AiResult = complete("Reply with the single word: ok")
}

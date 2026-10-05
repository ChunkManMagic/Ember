package com.ember.companion.core

import com.ember.companion.data.BriefMarkdownParser
import com.ember.companion.data.Dials
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the response reader.
 *
 * The bug these lock down: a provider that answers with `"content": null` — which
 * it does on every refusal, every tool call, and from several OpenAI-compatible
 * proxies — used to reach the screen as the literal four characters "null",
 * because kotlinx models a JSON null as a JsonPrimitive whose content is "null".
 * The user saw "null" in the Lab and got no explanation.
 */
class AiResponseParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(raw: String) = json.parseToJsonElement(raw).jsonObject

    /** Mirrors the OpenAI branch of AiClient. */
    private fun openAiText(root: kotlinx.serialization.json.JsonObject): String? {
        val choices = root["choices"]?.jsonArray ?: return null
        val message = choices.firstOrNull()?.jsonObject?.get("message")?.jsonObject ?: return null
        return message.str("content")
            ?: message.str("reasoning_content")
            ?: message.str("refusal")
    }

    @Test
    fun `json null is absent, not the string null`() {
        val root = parse(
            """{"choices":[{"message":{"content":null},"finish_reason":"stop"}]}""",
        )
        assertNull(openAiText(root))
        assertFalse(looksLikeAnAnswer(openAiText(root)))
    }

    @Test
    fun `the exact payload that displayed as null is now rejected`() {
        val root = parse("""{"choices":[{"message":{"content":null},"finish_reason":"content_filter"}]}""")
        val shown = openAiText(root)
        assertFalse("a null content must never look like an answer", looksLikeAnAnswer(shown))
    }

    @Test
    fun `refusal text is recovered from the sibling field`() {
        val root = parse(
            """{"choices":[{"message":{"content":null,"refusal":"I can't help with that."}}]}""",
        )
        assertEquals("I can't help with that.", openAiText(root))
        assertTrue(looksLikeAnAnswer(openAiText(root)))
    }

    @Test
    fun `reasoning models fall back to reasoning content`() {
        val root = parse(
            """{"choices":[{"message":{"content":null,"reasoning_content":"a tavern at dusk"}}]}""",
        )
        assertEquals("a tavern at dusk", openAiText(root))
    }

    @Test
    fun `content sent as an array of parts is joined`() {
        val root = parse(
            """{"choices":[{"message":{"content":[
               {"type":"text","text":"The door "},
               {"type":"text","text":"opens."}]}}]}""",
        )
        assertEquals("The door opens.", openAiText(root))
    }

    @Test
    fun `non text parts are skipped`() {
        val root = parse(
            """{"choices":[{"message":{"content":[
               {"type":"image_url","image_url":{"url":"x"}},
               {"type":"text","text":"only this"}]}}]}""",
        )
        assertEquals("only this", openAiText(root))
    }

    @Test
    fun `a normal answer still passes through untouched`() {
        val root = parse("""{"choices":[{"message":{"content":"A quiet bar."}}]}""")
        assertEquals("A quiet bar.", openAiText(root))
        assertTrue(looksLikeAnAnswer(openAiText(root)))
    }

    @Test
    fun `placeholder words never count as answers`() {
        assertFalse(looksLikeAnAnswer("null"))
        assertFalse(looksLikeAnAnswer("  NULL "))
        assertFalse(looksLikeAnAnswer("undefined"))
        assertFalse(looksLikeAnAnswer("NaN"))
        assertFalse(looksLikeAnAnswer(""))
        assertFalse(looksLikeAnAnswer(null))
    }

    @Test
    fun `a real sentence is not mistaken for a placeholder`() {
        assertTrue(looksLikeAnAnswer("Null Island is a joke name, so this is fine."))
        assertTrue(looksLikeAnAnswer("undefined behaviour aside, the scene continues."))
    }

    @Test
    fun `anthropic keeps only text blocks`() {
        val root = parse(
            """{"content":[{"type":"text","text":"line one"},
                          {"type":"tool_use","id":"t1"},
                          {"type":"text","text":"line two"}]}""",
        )
        val text = root["content"]!!.jsonArray.mapNotNull { block ->
            val obj = block.jsonObject
            if (obj.str("type") == "text") obj.str("text") else null
        }.joinToString("\n").trim()
        assertEquals("line one\nline two", text)
    }

    @Test
    fun `gemini joins parts and ignores nulls`() {
        val root = parse(
            """{"candidates":[{"content":{"parts":[{"text":"a "},{"text":null},{"text":"bar"}]},
               "finishReason":"STOP"}]}""",
        )
        val parts = root["candidates"]!!.jsonArray.first().jsonObject
            .get("content")!!.jsonObject.get("parts")!!.jsonArray
        val text = parts.mapNotNull { it.jsonObject.str("text") }.joinToString("")
        assertEquals("a bar", text)
    }

    @Test
    fun `openai request serializer includes temperature and max_tokens for standard models`() {
        val body = buildOpenAiRequestBody(
            model = "gpt-4o-mini",
            prompt = "test prompt",
            maxTokens = 800,
            temperature = 0.7,
            sysPrompt = "test system",
        )
        assertEquals(0.7, body["temperature"]?.jsonPrimitive?.doubleOrNull ?: 0.0, 0.001)
        assertEquals(800, body["max_tokens"]?.jsonPrimitive?.intOrNull)
        assertEquals("gpt-4o-mini", body.str("model"))
        assertFalse("Standard model must not use max_completion_tokens", body.containsKey("max_completion_tokens"))
    }

    @Test
    fun `openai reasoning models use max_completion_tokens instead of max_tokens`() {
        val model = "o3-mini"
        val body = buildOpenAiRequestBody(
            model = model,
            prompt = "reasoning prompt",
            maxTokens = 1200,
            temperature = 0.7,
            sysPrompt = "developer instructions",
        )
        assertTrue("Reasoning model must use max_completion_tokens", body.containsKey("max_completion_tokens"))
        assertFalse("Reasoning model must NOT use max_tokens", body.containsKey("max_tokens"))
        assertFalse("Reasoning model must NOT include temperature", body.containsKey("temperature"))
        assertEquals(1200, body["max_completion_tokens"]!!.jsonPrimitive.int)

        val messages = body["messages"]!!.jsonArray
        val devMsg = messages.first().jsonObject
        assertEquals("developer", devMsg.str("role"))
    }

    @Test
    fun `anthropic request serializer includes temperature and claude-3-5-sonnet default`() {
        val body = buildAnthropicRequestBody(
            model = "claude-3-5-sonnet-20241022",
            prompt = "hello claude",
            maxTokens = 1000,
            temperature = 0.8,
            sysPrompt = "test system",
        )
        assertEquals("claude-3-5-sonnet-20241022", body.str("model"))
        assertEquals(0.8, body["temperature"]?.jsonPrimitive?.doubleOrNull ?: 0.0, 0.001)
        assertEquals(1000, body["max_tokens"]?.jsonPrimitive?.intOrNull)
        assertEquals("test system", body.str("system"))
    }

    @Test
    fun `gemini request serializer generationConfig includes temperature and maxOutputTokens`() {
        val body = buildGeminiRequestBody(
            prompt = "hello gemini",
            maxTokens = 1500,
            temperature = 0.9,
            sysPrompt = "test system",
        )
        val config = body["generationConfig"]!!.jsonObject
        assertEquals(1500, config["maxOutputTokens"]!!.jsonPrimitive.int)
        assertEquals(0.9, config["temperature"]?.jsonPrimitive?.doubleOrNull ?: 0.0, 0.001)
    }

    @Test
    fun `reasoning model detection correctly identifies o1 and o3 models`() {
        assertTrue(isReasoningModel("o1"))
        assertTrue(isReasoningModel("o1-mini"))
        assertTrue(isReasoningModel("o1-preview"))
        assertTrue(isReasoningModel("o3-mini"))
        assertTrue(isReasoningModel("o3"))
        assertTrue(isReasoningModel("o4-preview"))

        assertFalse(isReasoningModel("gpt-4o"))
        assertFalse(isReasoningModel("gpt-4o-mini"))
        assertFalse(isReasoningModel("claude-3-5-sonnet-20241022"))
        assertFalse(isReasoningModel("gemini-2.5-flash"))
        // OpenRouter serves reasoning models under other names; missing these
        // left them on the wrong token parameter and truncated their replies.
        assertTrue(isReasoningModel("deepseek/deepseek-r1"))
        assertTrue(isReasoningModel("deepseek/deepseek-r1:free"))
        assertTrue(isReasoningModel("qwen/qwq-32b"))
        assertTrue(isReasoningModel("some-model-thinking-v2"))
        assertFalse(isReasoningModel("meta-llama/llama-3.3-70b-instruct"))
        assertFalse(isReasoningModel("mistralai/mistral-large"))
    }

    @Test
    fun `system prompt budget scaling scales with maxTokens bounds`() {
        val shortPrompt = buildSystemPrompt(250)
        assertTrue(shortPrompt.contains("100 to 180 words"))

        val medPrompt = buildSystemPrompt(800)
        assertTrue(medPrompt.contains("300 to 500 words"))

        val longPrompt = buildSystemPrompt(2000)
        assertTrue(longPrompt.contains("700 to 1200 words"))
    }

    @Test
    fun `system prompt format instructions direct markdown and suppress chatter`() {
        val prompt = buildSystemPrompt(800)
        assertTrue(prompt.contains("Markdown"))
        assertTrue(prompt.contains("Never include conversational filler"))
        assertTrue(prompt.contains("All characters are adults (18+)"))
    }

    @Test
    fun `anthropic model sanitization replaces claude-sonnet-5 with claude-3-5-sonnet`() {
        assertEquals("claude-3-5-sonnet-20241022", SettingsStore.sanitizeModel("claude-sonnet-5", AiProvider.ANTHROPIC))
        assertEquals("claude-3-5-sonnet-20241022", SettingsStore.sanitizeModel("claude-sonnet", AiProvider.ANTHROPIC))
        assertEquals("claude-3-7-sonnet-latest", SettingsStore.sanitizeModel("claude-3-7-sonnet-latest", AiProvider.ANTHROPIC))
        assertEquals("gpt-4o", SettingsStore.sanitizeModel("gpt-4o", AiProvider.OPENAI))
    }

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
}

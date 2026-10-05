package com.ember.companion.core

import com.ember.companion.data.Banks
import com.ember.companion.data.Brief
import com.ember.companion.data.BriefMarkdownParser
import com.ember.companion.data.BriefSlot
import com.ember.companion.data.CharacterCard
import com.ember.companion.data.CharacterCardPrompts
import com.ember.companion.data.Dials
import com.ember.companion.data.Generator
import com.ember.companion.data.Part
import com.ember.companion.data.shortSummary
import com.ember.companion.data.summary
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Empirical stress test suite by Challenger M1-1.
 * Tests edge cases, boundary conditions, parameter sanitization, and resilience
 * across AI models, prompt budgets, temperature limits, and parsing mechanics.
 */
class Milestone1ChallengerStressTest {

    // =========================================================================
    // 1. REASONING MODEL PARAMETER FORMATTING
    // =========================================================================

    @Test
    fun `reasoning model detector handles case variation and model prefixes`() {
        val reasoningModels = listOf(
            "o1",
            "o1-mini",
            "o1-preview",
            "o1-2024-12-17",
            "O1-MINI",
            "  o1-preview  ",
            "o3",
            "o3-mini",
            "o3-mini-2025-01-31",
            "O3-MINI",
            "o4-preview",
        )
        for (m in reasoningModels) {
            assertTrue("Expected reasoning model for: '$m'", isReasoningModel(m))
        }

        val nonReasoningModels = listOf(
            "gpt-4o",
            "gpt-4o-mini",
            "gpt-4-turbo",
            "gpt-3.5-turbo",
            "claude-3-5-sonnet-20241022",
            "gemini-2.0-flash",
            "text-davinci-003",
        )
        for (m in nonReasoningModels) {
            assertFalse("Expected non-reasoning model for: '$m'", isReasoningModel(m))
        }
    }

    @Test
    fun `reasoning models omit temperature and substitute max_completion_tokens`() {
        val testModels = listOf("o1", "o1-mini", "o3-mini", "o4-preview")
        for (model in testModels) {
            val body = buildOpenAiRequestBody(
                model = model,
                prompt = "Outline a dramatic turn",
                maxTokens = 950,
                temperature = 0.7,
                sysPrompt = "Developer instructions",
            )

            // OpenAI reasoning model constraints
            assertFalse("Reasoning model $model must NOT contain temperature", body.containsKey("temperature"))
            assertFalse("Reasoning model $model must NOT contain max_tokens", body.containsKey("max_tokens"))
            assertTrue("Reasoning model $model MUST contain max_completion_tokens", body.containsKey("max_completion_tokens"))
            assertEquals(950, body["max_completion_tokens"]!!.jsonPrimitive.int)

            val messages = body["messages"]!!.jsonArray
            assertEquals(2, messages.size)
            assertEquals("developer", messages[0].jsonObject.str("role"))
            assertEquals("Developer instructions", messages[0].jsonObject.str("content"))
            assertEquals("user", messages[1].jsonObject.str("role"))
            assertEquals("Outline a dramatic turn", messages[1].jsonObject.str("content"))
        }
    }

    @Test
    fun `standard OpenAI models retain temperature, max_tokens, and system role`() {
        val body = buildOpenAiRequestBody(
            model = "gpt-4o-mini",
            prompt = "Generate scene",
            maxTokens = 450,
            temperature = 0.65,
            sysPrompt = "System directive",
        )

        assertTrue(body.containsKey("temperature"))
        assertTrue(body.containsKey("max_tokens"))
        assertFalse(body.containsKey("max_completion_tokens"))
        assertEquals(450, body["max_tokens"]!!.jsonPrimitive.int)
        assertEquals(0.65, body["temperature"]!!.jsonPrimitive.doubleOrNull ?: 0.0, 0.001)

        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject.str("role"))
        assertEquals("System directive", messages[0].jsonObject.str("content"))
        assertEquals("user", messages[1].jsonObject.str("role"))
    }

    // =========================================================================
    // 2. TEMPERATURE BOUNDS AND CLAMPING
    // =========================================================================

    @Test
    fun `OpenAI temperature bounds clamp below 0 and above 2`() {
        // Below 0 -> clamped to 0.0
        val low = buildOpenAiRequestBody("gpt-4o", "test", 100, -1.5, "sys")
        assertEquals(0.0, low["temperature"]!!.jsonPrimitive.doubleOrNull ?: -1.0, 0.0001)

        // Exact boundary 0.0
        val zero = buildOpenAiRequestBody("gpt-4o", "test", 100, 0.0, "sys")
        assertEquals(0.0, zero["temperature"]!!.jsonPrimitive.doubleOrNull ?: -1.0, 0.0001)

        // Exact boundary 2.0
        val two = buildOpenAiRequestBody("gpt-4o", "test", 100, 2.0, "sys")
        assertEquals(2.0, two["temperature"]!!.jsonPrimitive.doubleOrNull ?: -1.0, 0.0001)

        // Above 2 -> clamped to 2.0
        val high = buildOpenAiRequestBody("gpt-4o", "test", 100, 5.0, "sys")
        assertEquals(2.0, high["temperature"]!!.jsonPrimitive.doubleOrNull ?: -1.0, 0.0001)
    }

    @Test
    fun `Anthropic temperature bounds clamp between 0 and 1 strictly`() {
        // Below 0 -> clamped to 0.0
        val low = buildAnthropicRequestBody("claude-3-5-sonnet-20241022", "test", 100, -0.5, "sys")
        assertEquals(0.0, low["temperature"]!!.jsonPrimitive.doubleOrNull ?: -1.0, 0.0001)

        // Mid point 0.7 -> preserved
        val mid = buildAnthropicRequestBody("claude-3-5-sonnet-20241022", "test", 100, 0.7, "sys")
        assertEquals(0.7, mid["temperature"]!!.jsonPrimitive.doubleOrNull ?: -1.0, 0.0001)

        // Exact boundary 1.0
        val one = buildAnthropicRequestBody("claude-3-5-sonnet-20241022", "test", 100, 1.0, "sys")
        assertEquals(1.0, one["temperature"]!!.jsonPrimitive.doubleOrNull ?: -1.0, 0.0001)

        // Above 1.0 -> Anthropic API rejects > 1.0, so must be clamped to 1.0
        val high = buildAnthropicRequestBody("claude-3-5-sonnet-20241022", "test", 100, 1.8, "sys")
        assertEquals(1.0, high["temperature"]!!.jsonPrimitive.doubleOrNull ?: -1.0, 0.0001)
    }

    @Test
    fun `Gemini temperature bounds clamp between 0 and 2`() {
        val low = buildGeminiRequestBody("test", 100, -1.0, "sys")
        val configLow = low["generationConfig"]!!.jsonObject
        assertEquals(0.0, configLow["temperature"]!!.jsonPrimitive.doubleOrNull ?: -1.0, 0.0001)

        val mid = buildGeminiRequestBody("test", 100, 1.4, "sys")
        val configMid = mid["generationConfig"]!!.jsonObject
        assertEquals(1.4, configMid["temperature"]!!.jsonPrimitive.doubleOrNull ?: -1.0, 0.0001)

        val high = buildGeminiRequestBody("test", 100, 3.5, "sys")
        val configHigh = high["generationConfig"]!!.jsonObject
        assertEquals(2.0, configHigh["temperature"]!!.jsonPrimitive.doubleOrNull ?: -1.0, 0.0001)
    }

    // =========================================================================
    // 3. PROMPT GENERATION AT EDGE-CASE TOKEN LIMITS (0, 50, 4000)
    // =========================================================================

    @Test
    fun `system prompt generation at token limit 0 selects concise budget without error`() {
        val prompt = buildSystemPrompt(maxTokens = 0)
        assertTrue(prompt.contains("100 to 180 words"))
        assertTrue(prompt.contains("All characters are adults (18+)"))
        assertTrue(prompt.contains("Never include conversational filler"))
    }

    @Test
    fun `system prompt generation at token limit 50 selects concise budget`() {
        val prompt = buildSystemPrompt(maxTokens = 50)
        assertTrue(prompt.contains("100 to 180 words"))
        assertTrue(prompt.contains("All characters are adults (18+)"))
    }

    @Test
    fun `system prompt generation at threshold boundaries 350, 351, 1000, 1001`() {
        // 350 is top of SHORT
        val p350 = buildSystemPrompt(maxTokens = 350)
        assertTrue("At 350 tokens: should be short budget", p350.contains("100 to 180 words"))

        // 351 enters MEDIUM
        val p351 = buildSystemPrompt(maxTokens = 351)
        assertTrue("At 351 tokens: should be medium budget", p351.contains("300 to 500 words"))

        // 1000 is top of MEDIUM
        val p1000 = buildSystemPrompt(maxTokens = 1000)
        assertTrue("At 1000 tokens: should be medium budget", p1000.contains("300 to 500 words"))

        // 1001 enters LONG
        val p1001 = buildSystemPrompt(maxTokens = 1001)
        assertTrue("At 1001 tokens: should be long budget", p1001.contains("700 to 1200 words"))
    }

    @Test
    fun `system prompt generation at token limit 4000 selects expansive blueprint budget`() {
        val prompt = buildSystemPrompt(maxTokens = 4000)
        assertTrue(prompt.contains("700 to 1200 words"))
        assertTrue(prompt.contains("comprehensive character dynamics"))
        assertTrue(prompt.contains("All characters are adults (18+)"))
    }

    @Test
    fun `system prompt custom parameters handle null, blank, and bespoke values`() {
        val defaultGenre = buildSystemPrompt(800, genre = null)
        assertTrue(defaultGenre.contains("Multi-genre versatility"))

        val blankGenre = buildSystemPrompt(800, genre = "   ")
        assertTrue(blankGenre.contains("Multi-genre versatility"))

        val customGenre = buildSystemPrompt(800, genre = "Victorian Gothic Horror")
        assertTrue(customGenre.contains("Lean into Victorian Gothic Horror aesthetic"))

        val customFormat = buildSystemPrompt(800, formatInstruction = "OUTPUT FORMAT: PURE JSON ONLY")
        assertTrue(customFormat.contains("OUTPUT FORMAT: PURE JSON ONLY"))
        assertFalse(customFormat.contains("Formatting: Use clean Markdown"))
    }

    // =========================================================================
    // 4. CHARACTER CARD PROMPTS & CLEANUP BOUNDARIES
    // =========================================================================

    @Test
    fun `cleanFirstMessage handles messy fences, preambles, and chatter`() {
        // Empty and blank
        assertEquals("", CharacterCardPrompts.cleanFirstMessage(""))
        assertEquals("", CharacterCardPrompts.cleanFirstMessage("   \n\t  "))

        // Markdown code fence with language
        val fenced = "```markdown\n\"The rain hasn't stopped all night.\"\n```"
        assertEquals("\"The rain hasn't stopped all night.\"", CharacterCardPrompts.cleanFirstMessage(fenced))

        // Preamble variants
        val withPreamble1 = "Here is the first message for the character:\n*A shadow steps out.*"
        assertEquals("*A shadow steps out.*", CharacterCardPrompts.cleanFirstMessage(withPreamble1))

        val withPreamble2 = "Here is a greeting based on your brief: \"Welcome back.\""
        assertEquals("\"Welcome back.\"", CharacterCardPrompts.cleanFirstMessage(withPreamble2))

        // Trailing conversational chatter
        val withTrailing = "*He smiles faintly.*\n\nHope this helps! Let me know if you need any adjustments."
        assertEquals("*He smiles faintly.*", CharacterCardPrompts.cleanFirstMessage(withTrailing))
    }

    @Test
    fun `cleanMesExample guarantees START tag and cleans commentary`() {
        // Missing START tag entirely
        val noStart = "{{user}}: Hello.\n{{char}}: Hi."
        val fixedNoStart = CharacterCardPrompts.cleanMesExample(noStart)
        assertTrue(fixedNoStart.startsWith("<START>"))
        assertTrue(fixedNoStart.contains("{{user}}: Hello."))

        // Lowercase start tag
        val lowerStart = "<start>\n{{user}}: What's next?\n{{char}}: Everything."
        val fixedLower = CharacterCardPrompts.cleanMesExample(lowerStart)
        assertTrue(fixedLower.startsWith("<START>", ignoreCase = true))

        // START embedded after chatter
        val chatterBefore = "Sure, here are some sample dialogue lines:\n\n<START>\n{{user}}: Tell me.\n{{char}}: Not yet."
        val fixedChatter = CharacterCardPrompts.cleanMesExample(chatterBefore)
        assertTrue(fixedChatter.startsWith("<START>"))
        assertFalse(fixedChatter.contains("Sure, here are"))
    }

    // =========================================================================
    // 5. PARSER AND LAB RESILIENCE UNDER EXTREME INPUTS
    // =========================================================================

    @Test
    fun `BriefMarkdownParser cleanValue aggressively strips duplicate and multi-layer prefixes`() {
        // Standard double-prefix from repetitive model generation
        assertEquals("peace", BriefMarkdownParser.cleanValue("wants: wants: peace", "Wants"))
        assertEquals("fog roll", BriefMarkdownParser.cleanValue("* weather: fog roll", "Weather"))
        assertEquals("A door opens.", BriefMarkdownParser.cleanValue("1. Escalates: A door opens.", "1. Escalates"))
        assertEquals("A sudden arrival.", BriefMarkdownParser.cleanValue("2. Complication: A sudden arrival."))
        assertEquals("The tables turn.", BriefMarkdownParser.cleanValue("3. Turn: The tables turn."))
        assertEquals("just plain text", BriefMarkdownParser.cleanValue("just plain text"))

        // Multi-marker compound prefix finding: "· - Place: " leaves residue due to whitespace order
        val compoundBullet = BriefMarkdownParser.cleanValue("· - Place: Place: an empty terminal", "Place")
        assertEquals("an empty terminal", compoundBullet)

        // Stress test: triple-prefix exposes non-recursive single-pass limitation
        val triplePrefix = BriefMarkdownParser.cleanValue("wants: wants: wants: peace", "Wants")
        assertEquals("peace", triplePrefix)
    }

    @Test
    fun `BriefMarkdownParser handles completely blank, corrupt, or headerless input gracefully`() {
        // Empty
        val emptyBrief = BriefMarkdownParser.parse("", "default premise", Dials())
        assertFalse(emptyBrief.title.isBlank())
        assertEquals("default premise", emptyBrief.premise)

        // Raw prose with no markdown headers (word-boundary truncated title, not split mid-word)
        val rawProse = "Two agents met at a subway station. One had an envelope. The other had a phone."
        val proseBrief = BriefMarkdownParser.parse(rawProse, "subway", Dials())
        assertEquals("Two agents met at a subway station. One had an envelope.", proseBrief.title)
        assertFalse("Must not truncate mid-word", proseBrief.title.endsWith(" e"))
        assertTrue(proseBrief.slots.any { it.key == "aiScenario" })
        assertEquals(rawProse, proseBrief.slot("aiScenario")!!.body)

        // Header only, no sections: fallbackTitle retains raw unstripped first line with '#'
        val headerOnly = "# A Lone Figure\n\nNo sections follow."
        val headerBrief = BriefMarkdownParser.parse(headerOnly, "", Dials())
        assertTrue(
            "Fallback header title is '${headerBrief.title}'",
            headerBrief.title == "A Lone Figure" || headerBrief.title == "# A Lone Figure",
        )
    }

    @Test
    fun `BriefMarkdownParser replacePartSafely rejects blank replacement or missing keys`() {
        val brief = Generator.brief(Random(123), dials = Dials())

        // Blank replacement rejected
        val blankAttempt = BriefMarkdownParser.replacePartSafely(brief, "setting", "place", "   ")
        assertEquals(brief.slot("setting")!!.body, blankAttempt.slot("setting")!!.body)

        // Nonexistent slot or part key
        val nonSlot = BriefMarkdownParser.replacePartSafely(brief, "imaginary_slot", "place", "valid")
        assertEquals(brief.text, nonSlot.text)

        val nonPart = BriefMarkdownParser.replacePartSafely(brief, "setting", "imaginary_part", "valid")
        assertEquals(brief.text, nonPart.text)
    }

    @Test
    fun `Dials summary safely handles out-of-range dial indices`() {
        val wildDials = Dials(explicitness = -10, pace = 50, power = 999, pov = -1)
        val full = wildDials.summary
        val short = wildDials.shortSummary
        assertFalse(full.isBlank())
        assertFalse(short.isBlank())
        // Should coerce to valid labels without IndexOutOfBoundsException
        assertTrue(short.contains("restrained") || short.contains("charged") || short.contains("unfiltered"))
    }
}

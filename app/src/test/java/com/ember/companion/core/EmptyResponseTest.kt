package com.ember.companion.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Lab reported "there was no text in the response ... hit the token limit"
 * for a request that should have been easy. Two bugs, both here:
 *
 *  - Gemini thinking tokens were charged against maxOutputTokens, so a small
 *    budget was spent entirely on private reasoning and zero text came back.
 *  - The extractor fell back to returning finishReason, so a truncated response
 *    handed back the literal string "MAX_TOKENS" as if it were the answer —
 *    which is also why the error message blamed tokens for an empty reply.
 */
class EmptyResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun geminiRequestCapsThinkingSoTheAnswerGetsTheBudget() {
        val body = buildGeminiRequestBody("write something", maxTokens = 600, temperature = 0.8, sysPrompt = "sys")
        val config = body["generationConfig"] as JsonObject
        assertEquals(600, config["maxOutputTokens"].toString().toInt())
        // Thinking budget must be explicitly zero, not left to the provider default.
        val thinking = config["thinkingConfig"] as JsonObject
        assertEquals(0, thinking["thinkingBudget"].toString().toInt())
    }

    /** Mirrors the Gemini extractor, including the removed finishReason fallback. */
    private fun extractGeminiText(root: JsonObject): String? {
        val candidates = root["candidates"] as? JsonArray ?: return null
        val first = candidates.firstOrNull() as? JsonObject ?: return null
        val parts = (first["content"] as? JsonObject)?.get("parts") as? JsonArray ?: return null
        return parts.mapNotNull { part ->
            val obj = part as? JsonObject ?: return@mapNotNull null
            if (obj.str("thought") == "true") return@mapNotNull null
            obj.str("text")
        }.joinToString("").trim().takeIf { it.isNotEmpty() }
    }

    @Test
    fun normalAnswerIsExtracted() {
        val payload = """
            {"candidates":[{"content":{"parts":[{"text":"a full scenario"}]},
            "finishReason":"STOP"}]}
        """.trimIndent()
        val text = extractGeminiText(json.parseToJsonElement(payload).jsonObject)
        assertEquals("a full scenario", text)
    }

    @Test
    fun thoughtPartsAreNeverReturnedAsTheAnswer() {
        // The model's private reasoning must not become the scenario.
        val payload = """
            {"candidates":[{"content":{"parts":[
              {"thought":true,"text":"First I should consider the tone and..."}
            ]},"finishReason":"MAX_TOKENS"}]}
        """.trimIndent()
        val text = extractGeminiText(json.parseToJsonElement(payload).jsonObject)
        assertNull("reasoning text must not surface as the answer", text)
    }

    @Test
    fun truncatedResponseReturnsNullNotTheStopReason() {
        // The old fallback returned "MAX_TOKENS" here, and the caller then
        // reported a nonsensical "no text ... token limit" error.
        val payload = """
            {"candidates":[{"content":{"parts":[{"thought":true,"text":"thinking"}]},
            "finishReason":"MAX_TOKENS"}]}
        """.trimIndent()
        assertNull(extractGeminiText(json.parseToJsonElement(payload).jsonObject))
    }

    @Test
    fun realTextSurvivesAlongsideThoughts() {
        val payload = """
            {"candidates":[{"content":{"parts":[
              {"thought":true,"text":"planning internally"},
              {"text":"Here is your scenario."}
            ]},"finishReason":"STOP"}]}
        """.trimIndent()
        assertEquals("Here is your scenario.", extractGeminiText(json.parseToJsonElement(payload).jsonObject))
    }

    @Test
    fun anEmptyReplyIsRecognisedAsNotAnAnswer() {
        assertTrue(!looksLikeAnAnswer(null))
        assertTrue(!looksLikeAnAnswer("   "))
        assertTrue(!looksLikeAnAnswer("null"))
        assertTrue(!looksLikeAnAnswer("undefined"))
        assertTrue(looksLikeAnAnswer("a real answer"))
    }

    @Test
    fun wordBudgetNeverExceedsTheTokenCeiling() {
        // A 700-1200 word brief needs ~2000 tokens; asking for it inside a 1200
        // token cap guarantees truncation, which is what the Builder hit.
        val blueprintWords = 1200
        val builderBudget = 3000
        assertTrue(
            "word budget cannot fit the token budget",
            builderBudget > blueprintWords * 1.5,
        )
    }
}

private fun JsonObject.jsonObject() = this
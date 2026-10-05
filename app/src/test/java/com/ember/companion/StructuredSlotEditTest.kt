package com.ember.companion

import com.ember.companion.data.Brief
import com.ember.companion.data.BriefSlot
import com.ember.companion.data.Part
import com.ember.companion.ui.BriefJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Lab's AI section edits were "mixed up and may or may not make sense"
 * because the model returned prose and Ember guessed which line belonged to which
 * field with regexes. These pin the replacement contract: the model returns keyed
 * JSON, and only real parts of the edited slot may be written.
 */
class StructuredSlotEditTest {

    private fun settingSlot() = BriefSlot(
        "setting",
        "Setting",
        listOf(
            Part("place", "Place", "an empty station platform", bank = "places"),
            Part("time", "Time", "third shift", bank = "times"),
            Part("weather", "Weather", "cold rain", bank = "weather", locked = true),
        ),
    )

    private fun brief(): Brief = Brief(
        title = "Third Shift",
        slots = listOf(settingSlot()),
        premise = "",
    )

    /** Mirrors applyStructuredSlotEdit, which is private on the ViewModel. */
    private fun apply(brief: Brief, raw: String): Pair<Brief, List<String>> {
        val editable = settingSlot().parts.filter { !it.locked }
        val values = BriefJson.stringMap(raw)
        if (values.isEmpty()) return brief to emptyList()
        val keys = editable.map { it.key }.toSet()
        val slot = brief.slot("setting")!!
        val applied = mutableListOf<String>()
        val parts = slot.parts.map { part ->
            if (part.locked || part.key !in keys) return@map part
            val next = values[part.key]?.trim().orEmpty()
            if (next.isBlank() || next.contains("##")) return@map part
            if (next == part.value) return@map part
            applied += part.label.ifBlank { part.key }
            part.copy(value = next)
        }
        return brief.withSlot(slot.copy(parts = parts)) to applied
    }

    @Test
    fun eachKeyLandsInItsOwnField() {
        val (updated, applied) = apply(
            brief(),
            """{"place":"a flooded subway platform","time":"the last train's aftermath"}""",
        )
        assertEquals("a flooded subway platform", updated.slot("setting")?.parts?.first { it.key == "place" }?.value)
        assertEquals("the last train's aftermath", updated.slot("setting")?.parts?.first { it.key == "time" }?.value)
        assertEquals(listOf("Place", "Time"), applied)
    }

    @Test
    fun lockedFieldsSurviveAnEdit() {
        val (updated, _) = apply(
            brief(),
            """{"place":"a rooftop","time":"dawn","weather":"clear sky"}""",
        )
        // A pinned value must never be overwritten by the model.
        assertEquals("cold rain", updated.slot("setting")?.parts?.first { it.key == "weather" }?.value)
    }

    @Test
    fun inventedKeysCannotCreateFields() {
        val (updated, applied) = apply(
            brief(),
            """{"place":"a rooftop","mood":"melancholic","location":"elsewhere"}""",
        )
        val keys = updated.slot("setting")!!.parts.map { it.key }
        assertEquals(listOf("place", "time", "weather"), keys)
        assertEquals(listOf("Place"), applied)
    }

    @Test
    fun blankAndSectionedValuesAreRefused() {
        val (updated, _) = apply(
            brief(),
            """{"place":"   ","time":"## A whole new section\\nnonsense"}""",
        )
        assertEquals("an empty station platform", updated.slot("setting")?.parts?.first { it.key == "place" }?.value)
        assertEquals("third shift", updated.slot("setting")?.parts?.first { it.key == "time" }?.value)
    }

    @Test
    fun unparseableReplyChangesNothing() {
        // The old fallback scraped prose here, which is how values got crossed.
        val original = brief()
        val (updated, applied) = apply(original, "Sure! Here is a nicer setting. It's very atmospheric.")
        assertEquals(original.slot("setting")?.body, updated.slot("setting")?.body)
        assertTrue(applied.isEmpty())
    }

    @Test
    fun jsonFencedAndPaddedRepliesStillParse() {
        val parsed = BriefJson.stringMap(
            """
            Here you go:
            ```json
            {"place":"a rooftop","time":"dawn"}
            ```
            Hope that helps!
            """.trimIndent(),
        )
        assertEquals("a rooftop", parsed["place"])
        assertEquals("dawn", parsed["time"])
    }

    @Test
    fun nonStringValuesAreCoercedNotDropped() {
        val parsed = BriefJson.stringMap("""{"a":"x","b":3,"c":true}""")
        assertEquals("x", parsed["a"])
        assertEquals("3", parsed["b"])
        assertEquals("true", parsed["c"])
    }

    @Test
    fun nestedObjectsAreIgnoredNotFlattened() {
        val parsed = BriefJson.stringMap("""{"a":"x","nested":{"k":"v"},"list":[1,2]}""")
        assertEquals(setOf("a"), parsed.keys)
    }

    @Test
    fun missingOrEmptyReplyYieldsNothing() {
        assertTrue(BriefJson.stringMap("").isEmpty())
        assertTrue(BriefJson.stringMap("no braces here").isEmpty())
    }
}
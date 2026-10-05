package com.ember.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Lab's "give it a basic idea" builder produced output that "doesn't make
 * sense" — and the cause was the parser, not the model.
 *
 * `BriefMarkdownParser.parse` matched `##` headings by exact lowercase string
 * and read `Label: value` lines with a bare `indexOf(':')`. A perfectly good
 * reply headed `## Characters` or using `**Place:**` had those sections silently
 * dropped and replaced with hardcoded filler ("An intimate setting", "Maren
 * Aldiss"), so a real answer came back looking like unrelated word-bank noise.
 *
 * These tests use realistic model phrasing, not the exact template Ember asked
 * for, because the whole point is that models don't comply exactly.
 */
class BriefMarkdownParserTest {

    private val dials = Dials()

    @Test
    fun headingsTheModelRenamesAreStillFound() {
        val markdown = """
            # Last Ferry

            ## Characters
            **Name:** Ilya
            **Age:** early thirties
            **Role:** the one who stayed

            **Name:** Sera
            **Age:** late twenties
            **Role:** the one who left

            ## Setting
            **Place:** a ferry terminal after the last sailing
            **Time:** just past midnight
            **Weather:** rain that will not stop
            **Air:** fluorescent and humming
            **Texture:** wet concrete and cold coffee

            ## Premise
            **Framing:** two people who cannot both get what they want tonight
            **Power:** she holds the tickets, he holds the reason
            **Tension:** neither will say the actual thing
            **Reveals to:** that one of them already left
            **Register:** restrained
            **Pacing:** slow
            **POV:** third person limited

            ## Opening Scene
            The gates are chained when they get there.

            ## Story Beats
            1. Escalates: she tries to give him the tickets back
            2. Complication: he refuses and tells her why he came
            3. Turn: she admits she already booked two seats

            ## Ending
            Neither of them goes home.
        """.trimIndent()

        val brief = BriefMarkdownParser.parse(markdown, defaultPremise = "two people at a terminal", dials = dials)
        val setting = brief.slot("setting")!!
        assertEquals("a ferry terminal after the last sailing", setting.partValue("place"))
        assertEquals("just past midnight", setting.partValue("time"))
        assertEquals("rain that will not stop", setting.partValue("weather"))

        val frame = brief.slot("frame")!!
        assertEquals("two people who cannot both get what they want tonight", frame.partValue("framing"))
        assertEquals("she holds the tickets, he holds the reason", frame.partValue("power"))
        assertEquals("restrained", frame.partValue("register"))

        val cast = brief.slot("cast")!!
        // Real names, not the "Maren Aldiss" placeholder.
        assertEquals("Ilya", cast.partValue("aname"))
        assertEquals("Sera", cast.partValue("bname"))

        val beats = brief.slot("beats")!!
        assertEquals("she tries to give him the tickets back", beats.partValue("beat1"))
        assertEquals("he refuses and tells her why he came", beats.partValue("beat2"))
        assertEquals("she admits she already booked two seats", beats.partValue("beat3"))

        assertTrue(brief.slot("open")!!.partValue("open").contains("gates are chained"))
        assertTrue(brief.slot("close")!!.partValue("close").contains("Neither of them goes home"))
    }

    @Test
    fun missingFieldsStayBlankInsteadOfInventingFiller() {
        // The old parser filled every gap with generic defaults, which is what
        // made output read as word-bank sludge next to real content.
        val markdown = """
            # Quiet

            ## Setting
            Place: an empty pool hall
        """.trimIndent()

        val setting = BriefMarkdownParser.parse(markdown, "", dials).slot("setting")!!
        assertEquals("an empty pool hall", setting.partValue("place"))
        assertEquals("", setting.partValue("weather"))
        assertEquals("", setting.partValue("texture"))
    }

    @Test
    fun beatsAreNeverInvented() {
        val markdown = """
            # Partial

            ## Beats
            1. Escalates: the power goes out
        """.trimIndent()

        val beats = BriefMarkdownParser.parse(markdown, "", dials).slot("beats")!!
        assertEquals("the power goes out", beats.partValue("beat1"))
        assertEquals("", beats.partValue("beat2"))
        assertEquals("", beats.partValue("beat3"))
    }

    @Test
    fun theTemplatesOwnHeadingsStillWork() {
        // The regression guard: fixing the aliases must not break the format
        // Ember itself asks for.
        val markdown = """
            # Template

            ## Setting
            Place: a hotel bar
            Time: closing time
            Weather: rain
            Air: stale smoke
            Texture: wet coats

            ## Cast
            Character A: Name: Maren
            Age: thirties
            Role: Protagonist
            Wants: to be understood

            Character B: Name: Nura
            Age: twenties
            Role: Partner
            Wants: honest closure

            ## Frame
            Framing: an unexpected meeting
            Power: even
            Tension: unspoken
            Reveals to: a kept secret
            Register: warm
            Pacing: slow
            POV: second person

            ## Open
            She was already halfway through her drink.

            ## Beats
            1. Escalates: a message arrives
            2. Complication: it is from his ex
            3. Turn: she deletes it

            ## Close
            They walk out together.
        """.trimIndent()

        val brief = BriefMarkdownParser.parse(markdown, "", dials)
        assertEquals("a hotel bar", brief.slot("setting")!!.partValue("place"))
        assertEquals("Maren", brief.slot("cast")!!.partValue("aname"))
        assertEquals("Nura", brief.slot("cast")!!.partValue("bname"))
        assertEquals("an unexpected meeting", brief.slot("frame")!!.partValue("framing"))
        assertEquals("a message arrives", brief.slot("beats")!!.partValue("beat1"))
    }

    @Test
    fun aBareNameLineIsStillCaptured() {
        // Models often write the character's name with no "Name:" label at all.
        val markdown = """
            # Cast Test

            ## Characters
            Ilya
            Age: 30
            Role: protagonist

            Sera
            Age: 28
            Role: counterpart
        """.trimIndent()

        val cast = BriefMarkdownParser.parse(markdown, "", dials).slot("cast")!!
        assertEquals("Ilya", cast.partValue("aname"))
        assertEquals("Sera", cast.partValue("bname"))
        assertEquals("30", cast.partValue("aage"))
    }

    @Test
    fun unlabelledBeatsAreKeptRatherThanDropped() {
        val markdown = """
            # Beats

            ## Story Beats
            The lights fail
            Someone starts talking
            Nobody stops them
        """.trimIndent()

        val beats = BriefMarkdownParser.parse(markdown, "", dials).slot("beats")!!
        assertEquals("The lights fail", beats.partValue("beat1"))
        assertEquals("Someone starts talking", beats.partValue("beat2"))
        assertEquals("Nobody stops them", beats.partValue("beat3"))
    }
}

/** Convenience for asserting on one part of a slot. */
private fun BriefSlot.partValue(key: String): String =
    parts.firstOrNull { it.key == key }?.value.orEmpty()
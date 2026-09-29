package com.ember.companion.data

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabModelTest {

    @Test
    fun `same premise reproduces the same brief`() {
        val premise = "a hotel bar, one of them is leaving in the morning"
        val dials = Dials(explicitness = 2, pace = 1, power = 0, pov = 2)
        val a = Generator.brief(Random(1234), premise = premise, dials = dials)
        val b = Generator.brief(Random(1234), premise = premise, dials = dials)
        assertEquals(a.text, b.text)
    }

    @Test
    fun `different seeds produce different briefs`() {
        val dials = Dials()
        val a = Generator.brief(Random(1), dials = dials)
        val b = Generator.brief(Random(2), dials = dials)
        assertNotEquals(a.text, b.text)
    }

    @Test
    fun `premise forces a matching bank value`() {
        val premise = "set entirely in a hotel bar"
        val brief = Generator.brief(Random(7), premise = premise)
        val place = brief.allParts().first { it.key == "place" }.value
        assertTrue(
            "place should come from the premise, was: $place",
            place.contains("bar", ignoreCase = true),
        )
    }

    @Test
    fun `premise appears as its own slot`() {
        val premise = "a rooftop, rain starting"
        val brief = Generator.brief(Random(7), premise = premise)
        assertEquals(premise, brief.premise)
    }

    @Test
    fun `dials select deterministically and differ per position`() {
        val restrained = Generator.brief(Random(99), dials = Dials(explicitness = 0))
        val unfiltered = Generator.brief(Random(99), dials = Dials(explicitness = 2))
        assertEquals(Banks.registers[0], restrained.allParts().first { it.key == "register" }.value)
        assertEquals(Banks.registers[2], unfiltered.allParts().first { it.key == "register" }.value)
    }

    @Test
    fun `explicitness dial drives the opener and the close note`() {
        val restrained = Generator.brief(Random(5), dials = Dials(explicitness = 0))
        val unfiltered = Generator.brief(Random(5), dials = Dials(explicitness = 2))
        assertTrue(
            restrained.allParts().first { it.key == "open" }.value in Banks.openingsByRegister[0],
        )
        assertTrue(
            unfiltered.allParts().first { it.key == "open" }.value in Banks.openingsByRegister[2],
        )
        assertEquals(
            Banks.closeNotes[0],
            restrained.allParts().first { it.key == "closeNote" }.value,
        )
        assertEquals(
            Banks.closeNotes[2],
            unfiltered.allParts().first { it.key == "closeNote" }.value,
        )
    }

    @Test
    fun `premise matching ignores filler words`() {
        assertEquals(null, Banks.forcedBy("there is nothing between them", Banks.places))
    }

    @Test
    fun `premise matching prefers the more specific candidate`() {
        val match = Banks.forcedBy("they end up in a hotel bar", Banks.places)
        assertTrue("expected a hotel bar, got: $match", match?.contains("hotel") == true)
    }

    @Test
    fun `premise with no overlap leaves the value unrestrained`() {
        assertEquals(null, Banks.forcedBy("zzzqqq", Banks.places))
    }

    @Test
    fun `power dial picks the requested power balance`() {
        val sheLeads = Generator.brief(Random(3), dials = Dials(power = 0))
        assertEquals(
            Dials.POWER[0],
            sheLeads.allParts().first { it.key == "power" }.value,
        )
    }

    @Test
    fun `blocked values never appear`() {
        val base = Generator.brief(Random(11), dials = Dials())
        val secret = base.allParts().first { it.key == "asecret" }.value
        val taste = Banks.Taste(blocked = setOf(secret))
        val next = Generator.brief(Random(11), dials = Dials(), taste = taste)
        assertNotEquals(secret, next.allParts().first { it.key == "asecret" }.value)
    }

    @Test
    fun `a pin collapses the pool to the pinned value`() {
        val base = Generator.brief(Random(21), dials = Dials())
        val secret = base.allParts().first { it.key == "asecret" }.value
        val taste = Banks.Taste(pinned = mapOf("secrets" to secret))
        repeat(6) {
            val next = Generator.brief(Random(it * 7L), dials = Dials(), taste = taste)
            assertEquals(secret, next.allParts().first { it.key == "asecret" }.value)
        }
    }

    @Test
    fun `a pin for a value missing from the bank does not empty the pool`() {
        val taste = Banks.Taste(pinned = mapOf("places" to "a submarine"))
        val brief = Generator.brief(Random(31), dials = Dials(), taste = taste)
        val place = brief.allParts().first { it.key == "place" }.value
        assertTrue("place should still resolve, was: $place", place.isNotBlank())
    }

    @Test
    fun `blocking every value in a bank falls back rather than throwing`() {
        val taste = Banks.Taste(blocked = Banks.places.toSet())
        val brief = Generator.brief(Random(41), dials = Dials(), taste = taste)
        assertTrue(brief.allParts().first { it.key == "place" }.value.isNotBlank())
    }

    @Test
    fun `hidden parts are excluded from the rendered text`() {
        val brief = Generator.brief(Random(51), dials = Dials())
        val noun = brief.allParts().first { it.key == "titleNoun" }.value
        assertTrue(noun.isNotBlank())
        assertTrue("hidden title parts must not leak into the text", noun !in brief.text)
        assertTrue(brief.sections.none { it.heading == "Title parts" })
    }

    @Test
    fun `withPart replaces exactly one part and leaves the rest intact`() {
        val brief = Generator.brief(Random(61), dials = Dials())
        val before = brief.allParts().associate { it.key to it.value }
        val updated = brief.withPart("place") { it.copy(value = "a lighthouse") }
        assertEquals("a lighthouse", updated.allParts().first { it.key == "place" }.value)
        updated.allParts().filter { it.key != "place" }.forEach { part ->
            assertEquals(before[part.key], part.value)
        }
    }

    @Test
    fun `withPart on an unknown key is a no-op`() {
        val brief = Generator.brief(Random(71), dials = Dials())
        val updated = brief.withPart("nope") { it.copy(value = "x") }
        assertEquals(brief.text, updated.text)
    }

    @Test
    fun `text includes the premise and every visible slot`() {
        val premise = "a rooftop, rain starting"
        val brief = Generator.brief(Random(81), premise = premise, dials = Dials())
        assertTrue(brief.text.contains(premise))
        brief.slots.filter { slot -> slot.parts.any { !it.hidden } }.forEach { slot ->
            assertTrue("missing slot ${slot.heading}", brief.text.contains(slot.heading))
        }
    }

    @Test
    fun `rebuilding the title follows a changed place`() {
        val brief = Generator.brief(Random(91), dials = Dials())
        val moved = brief.withPart("place") { it.copy(value = "an empty lighthouse") }
        val retitled = moved.copy(title = Generator.rebuildTitle(moved, Random(3)))
        assertNotEquals(brief.title, retitled)
    }

    @Test
    fun `character and beat generators still produce renderable briefs`() {
        val character = Generator.character(Random(101))
        assertTrue(character.sections.isNotEmpty())
        assertTrue(character.text.isNotBlank())

        val beat = Generator.complication(Random(103))
        assertTrue(beat.sections.isNotEmpty())
        assertTrue(beat.text.isNotBlank())
    }

    @Test
    fun `defaultsFor covers every bank id the generator emits`() {
        val brief = Generator.brief(Random(111), dials = Dials())
        brief.allParts().mapNotNull { it.bank }.forEach { bank ->
            assertTrue("no default pool for bank '$bank'", Banks.defaultsFor(bank).isNotEmpty())
        }
    }

    @Test
    fun `updateSlotFromAi preserves locked parts in targeted slot`() {
        val brief = Generator.brief(Random(42), dials = Dials())
        val slotBefore = brief.slot("setting")!!
        val weatherBefore = slotBefore.parts.first { it.key == "weather" }
        val lockedBrief = brief.withPart("weather") { it.copy(locked = true, value = "Locked torrential rain") }

        val aiMarkdown = """
            Place: Neon-drenched alleyway
            Time: Midnight
            Weather: Clear and starry
            Air: Smelling of rain
            Texture: Wet asphalt
        """.trimIndent()

        val updatedBrief = BriefMarkdownParser.updateSlotFromAi(lockedBrief, "setting", aiMarkdown)
        val updatedSlot = updatedBrief.slot("setting")!!
        val updatedWeather = updatedSlot.parts.first { it.key == "weather" }
        val updatedPlace = updatedSlot.parts.first { it.key == "place" }

        assertEquals("Locked torrential rain", updatedWeather.value)
        assertTrue(updatedWeather.locked)
        assertEquals("Neon-drenched alleyway", updatedPlace.value)
    }

    @Test
    fun `updateSlotFromAi does not touch locked slot`() {
        val brief = Generator.brief(Random(55), dials = Dials())
        val beatsBefore = brief.slot("beats")!!
        val lockedBrief = brief.withSlot(beatsBefore.copy(locked = true))

        val aiMarkdown = """
            1. Escalates: The door slams shut.
            2. Complication: The power fails.
            3. Turn: Neither admits they planned it.
        """.trimIndent()

        val result = BriefMarkdownParser.updateSlotFromAi(lockedBrief, "beats", aiMarkdown)
        assertEquals(beatsBefore.parts, result.slot("beats")!!.parts)
    }

    @Test
    fun `cleanValue strips duplicate prefixes and redundant label repetitions`() {
        assertEquals(
            "to be left alone",
            BriefMarkdownParser.cleanValue("  · wants: wants: to be left alone", expectedPrefixOrLabel = "Wants")
        )
        assertEquals(
            "An old station",
            BriefMarkdownParser.cleanValue("- Place: An old station", expectedPrefixOrLabel = "Place")
        )
        assertEquals(
            "deep and quiet",
            BriefMarkdownParser.cleanValue("· Voice: deep and quiet", expectedPrefixOrLabel = "Voice")
        )
        assertEquals(
            "to escape",
            BriefMarkdownParser.cleanValue("Wants: Wants: to escape", expectedPrefixOrLabel = "Wants")
        )
        assertEquals(
            "a heavy downpour",
            BriefMarkdownParser.cleanValue("Weather: a heavy downpour", expectedPrefixOrLabel = "Weather")
        )
    }

    @Test
    fun `replacePartSafely updates part value, sanitizes prefixes, and respects locks`() {
        val brief = Generator.brief(Random(77), dials = Dials())

        // 1. Normal replacement
        val updated = BriefMarkdownParser.replacePartSafely(brief, "setting", "place", "Place: Abandoned train car")
        val placePart = updated.slot("setting")!!.parts.first { it.key == "place" }
        assertEquals("Abandoned train car", placePart.value)

        // 2. Locked part rejected
        val lockedPartBrief = brief.withPart("place") { it.copy(locked = true) }
        val notUpdatedPart = BriefMarkdownParser.replacePartSafely(lockedPartBrief, "setting", "place", "New place")
        assertEquals(
            brief.slot("setting")!!.parts.first { it.key == "place" }.value,
            notUpdatedPart.slot("setting")!!.parts.first { it.key == "place" }.value
        )

        // 3. Locked slot rejected
        val lockedSlotBrief = brief.withSlot(brief.slot("setting")!!.copy(locked = true))
        val notUpdatedSlot = BriefMarkdownParser.replacePartSafely(lockedSlotBrief, "setting", "place", "New place")
        assertEquals(
            brief.slot("setting")!!.parts.first { it.key == "place" }.value,
            notUpdatedSlot.slot("setting")!!.parts.first { it.key == "place" }.value
        )
    }

    @Test
    fun `dials summary exposes compact steering chips string`() {
        val dials = Dials(explicitness = 2, pace = 0, power = 1, pov = 2)
        val fullSummary = dials.summary
        val shortSummary = dials.shortSummary

        assertTrue(fullSummary.contains("unfiltered"))
        assertTrue(fullSummary.contains("slow burn"))
        assertTrue(fullSummary.contains("neither leads"))
        assertTrue(fullSummary.contains("first person"))

        assertEquals("unfiltered · slow burn", shortSummary)
    }

    @Test
    fun `expanded banks contain more than baseline counts`() {
        assertTrue("places size: ${Banks.places.size}", Banks.places.size >= 35)
        assertTrue("roles size: ${Banks.roles.size}", Banks.roles.size >= 30)
        assertTrue("wants size: ${Banks.wants.size}", Banks.wants.size >= 20)
        assertTrue("fears size: ${Banks.fears.size}", Banks.fears.size >= 20)
        assertTrue("secrets size: ${Banks.secrets.size}", Banks.secrets.size >= 20)
        assertTrue("flaws size: ${Banks.flaws.size}", Banks.flaws.size >= 20)
        assertTrue("traits size: ${Banks.traits.size}", Banks.traits.size >= 25)
        assertTrue("escalations size: ${Banks.escalations.size}", Banks.escalations.size >= 12)
        assertTrue("complications size: ${Banks.complications.size}", Banks.complications.size >= 12)
        assertTrue("turns size: ${Banks.turns.size}", Banks.turns.size >= 12)
        assertTrue("twists size: ${Banks.twists.size}", Banks.twists.size >= 18)
        assertTrue("closers size: ${Banks.closers.size}", Banks.closers.size >= 10)

        // Dial-indexed banks must stay strictly invariant (3 items each)
        assertEquals(3, Banks.registers.size)
        assertEquals(3, Banks.pacingNotes.size)
        assertEquals(3, Banks.closeNotes.size)
        assertEquals(3, Banks.openingsByRegister.size)
        Banks.openingsByRegister.forEach { registerOpenings ->
            assertEquals(3, registerOpenings.size)
        }
    }

    @Test
    fun `premise forces an expanded bank place`() {
        // "ferry" is an expanded place entry added in M1
        val premise = "a coastal ferry crossing the sound"
        val brief = Generator.brief(Random(12), premise = premise)
        val place = brief.allParts().first { it.key == "place" }.value
        assertTrue(
            "place should match ferry premise, was: $place",
            place.contains("ferry", ignoreCase = true)
        )
    }

    @Test
    fun `card enriched with first_mes and mes_example preserves SillyTavern start format`() {
        val brief = Generator.brief(Random(42), dials = Dials())
        val customGreeting = "You step through the beaded curtain into the dim lounge."
        val customExamples = "<START>\n{{user}}: What are you doing here?\n{{char}}: Waiting for you, obviously."

        val enrichedCard = CharacterCard.fromBrief(
            brief = brief,
            firstMessage = customGreeting,
            exampleDialogue = customExamples,
        )

        assertEquals(customGreeting, enrichedCard.firstMessage)
        assertEquals(customExamples, enrichedCard.exampleDialogue)

        val v2Json = enrichedCard.toJson(CharacterCard.Spec.V2)
        val data = v2Json.getJSONObject("data")
        assertEquals(customGreeting, data.getString("first_mes"))
        assertEquals(customExamples, data.getString("mes_example"))
        assertTrue(data.getString("mes_example").startsWith("<START>"))

        // Default greeting fallback to open slot, default dialogue empty
        val defaultCard = CharacterCard.fromBrief(brief)
        val openSlot = brief.slot("open")?.body.orEmpty()
        assertEquals(openSlot, defaultCard.firstMessage)
        val defaultJson = defaultCard.toJson(CharacterCard.Spec.V2)
        assertEquals(openSlot, defaultJson.getJSONObject("data").getString("first_mes"))
        assertTrue(defaultJson.getJSONObject("data").getString("mes_example").isEmpty())
    }

    @Test
    fun `CharacterCardPrompts builds prompts with brief context and cleans responses`() {
        val brief = Generator.brief(Random(88), dials = Dials())
        val card = CharacterCard.fromBrief(brief)
        val greetingPrompt = CharacterCardPrompts.buildFirstMessagePrompt(brief, card)

        assertTrue(greetingPrompt.contains(card.name))
        assertTrue(greetingPrompt.contains("DO NOT speak, think, or act for {{user}}"))

        val rawAiGreeting = "Here is the opening greeting for the scenario:\n\"The kettle whistles on the stove.\"\nLet me know if you need changes!"
        val cleanedGreeting = CharacterCardPrompts.cleanFirstMessage(rawAiGreeting)
        assertEquals("\"The kettle whistles on the stove.\"", cleanedGreeting)

        val examplePrompt = CharacterCardPrompts.buildMesExamplePrompt(brief, card)
        assertTrue(examplePrompt.contains("<START>"))
        assertTrue(examplePrompt.contains("{{user}}"))
        assertTrue(examplePrompt.contains("{{char}}"))

        val rawAiExample = "Sure, here are dialogue examples:\n<START>\n{{user}}: Are you ready?\n{{char}}: More than ready."
        val cleanedExample = CharacterCardPrompts.cleanMesExample(rawAiExample)
        assertTrue(cleanedExample.startsWith("<START>"))
        assertFalse(cleanedExample.contains("Sure, here are"))
    }
}

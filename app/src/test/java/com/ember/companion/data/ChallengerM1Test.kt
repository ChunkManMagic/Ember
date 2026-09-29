package com.ember.companion.data

import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.random.Random
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Challenger M1 Empirical Verification Harness.
 *
 * Stress-tests and proves:
 * 1. Banks.kt dial indexing consistency across all 81 valid permutations of Dials (explicitness, pace, power, pov).
 * 2. Character Card JSON and PNG chunk serialization with the new firstMessage parameter across all specs and roundtrips.
 * 3. Boundary handling, fallback mechanics, and unicode/adversarial text preservation.
 */
class ChallengerM1Test {

    // =========================================================================
    // 1. Banks Dial Indexing Consistency & Permutations
    // =========================================================================

    @Test
    fun `banks dial indexed collections have exact sizes of 3`() {
        // Dial indices are strictly in 0..2.
        assertEquals(
            "Banks.registers must have exactly 3 entries matching explicitness 0..2",
            3,
            Banks.registers.size,
        )
        assertEquals(
            "Banks.pacingNotes must have exactly 3 entries matching pace 0..2",
            3,
            Banks.pacingNotes.size,
        )
        assertEquals(
            "Banks.closeNotes must have exactly 3 entries matching explicitness 0..2",
            3,
            Banks.closeNotes.size,
        )
        assertEquals(
            "Banks.openingsByRegister must have exactly 3 outer entries matching explicitness 0..2",
            3,
            Banks.openingsByRegister.size,
        )
        Banks.openingsByRegister.forEachIndexed { idx, openings ->
            assertEquals(
                "Banks.openingsByRegister[$idx] inner list must have exactly 3 entries",
                3,
                openings.size,
            )
            openings.forEach { opening ->
                assertTrue("Opening in register $idx must not be blank", opening.isNotBlank())
            }
        }
    }

    @Test
    fun `banks defaultsFor returns matching 3-element lists for dial keys`() {
        assertEquals(Banks.registers, Banks.defaultsFor("registers"))
        assertEquals(Banks.pacingNotes, Banks.defaultsFor("pacingNotes"))
        assertEquals(Banks.closeNotes, Banks.defaultsFor("closeNotes"))
        assertEquals(Dials.POV, Banks.defaultsFor("pov"))
        assertEquals(Dials.POWER, Banks.defaultsFor("powerDial"))
    }

    @Test
    fun `all 81 valid permutations of dials map consistently without index errors`() {
        var permutationCount = 0
        val testSeeds = listOf(1L, 42L, 9999L)

        for (explicitness in 0..2) {
            for (pace in 0..2) {
                for (power in 0..2) {
                    for (pov in 0..2) {
                        permutationCount++
                        val dials = Dials(
                            explicitness = explicitness,
                            pace = pace,
                            power = power,
                            pov = pov,
                        )

                        // 1. Direct indexing validity
                        val expectedRegister = Banks.registers[explicitness]
                        val expectedPacing = Banks.pacingNotes[pace]
                        val expectedClose = Banks.closeNotes[explicitness]
                        val allowedOpenings = Banks.openingsByRegister[explicitness]
                        val expectedPower = Dials.POWER[power]
                        val expectedPov = Dials.POV[pov]

                        assertTrue(expectedRegister.isNotBlank())
                        assertTrue(expectedPacing.isNotBlank())
                        assertTrue(expectedClose.isNotBlank())
                        assertEquals(3, allowedOpenings.size)
                        assertTrue(expectedPower.isNotBlank())
                        assertTrue(expectedPov.isNotBlank())

                        // 2. Generator.brief execution across multiple seeds for this permutation
                        for (seed in testSeeds) {
                            val brief = Generator.brief(Random(seed), dials = dials)

                            val regPart = brief.allParts().firstOrNull { it.key == "register" }
                            assertNotNull("brief must contain 'register' part", regPart)
                            assertEquals(
                                "register part must match Banks.registers[explicitness=$explicitness]",
                                expectedRegister,
                                regPart!!.value,
                            )

                            val pacePart = brief.allParts().firstOrNull { it.key == "pacing" }
                            assertNotNull("brief must contain 'pacing' part", pacePart)
                            assertEquals(
                                "pacing part must match Banks.pacingNotes[pace=$pace]",
                                expectedPacing,
                                pacePart!!.value,
                            )

                            val closePart = brief.allParts().firstOrNull { it.key == "closeNote" }
                            assertNotNull("brief must contain 'closeNote' part", closePart)
                            assertEquals(
                                "closeNote part must match Banks.closeNotes[explicitness=$explicitness]",
                                expectedClose,
                                closePart!!.value,
                            )

                            val openPart = brief.allParts().firstOrNull { it.key == "open" }
                            assertNotNull("brief must contain 'open' part", openPart)
                            assertTrue(
                                "open part '${openPart!!.value}' must be member of Banks.openingsByRegister[$explicitness]",
                                openPart.value in allowedOpenings,
                            )

                            val powerPart = brief.allParts().firstOrNull { it.key == "power" }
                            assertNotNull("brief must contain 'power' part", powerPart)
                            assertEquals(
                                "power part must match Dials.POWER[power=$power]",
                                expectedPower,
                                powerPart!!.value,
                            )

                            val povPart = brief.allParts().firstOrNull { it.key == "pov" }
                            assertNotNull("brief must contain 'pov' part", povPart)
                            assertEquals(
                                "pov part must match Dials.POV[pov=$pov]",
                                expectedPov,
                                povPart!!.value,
                            )
                        }
                    }
                }
            }
        }

        assertEquals("Must have tested exactly 3^4 = 81 permutations", 81, permutationCount)
    }

    @Test
    fun `dials boundary clamping prevents out of bounds indexing`() {
        val dialsUnderflow = Dials()
            .withExplicitness(-10)
            .withPace(-1)
            .withPower(-99)
            .withPov(-5)

        assertEquals(0, dialsUnderflow.explicitness)
        assertEquals(0, dialsUnderflow.pace)
        assertEquals(0, dialsUnderflow.power)
        assertEquals(0, dialsUnderflow.pov)

        val briefUnderflow = Generator.brief(Random(123), dials = dialsUnderflow)
        assertEquals(Banks.registers[0], briefUnderflow.allParts().first { it.key == "register" }.value)
        assertEquals(Banks.pacingNotes[0], briefUnderflow.allParts().first { it.key == "pacing" }.value)

        val dialsOverflow = Dials()
            .withExplicitness(10)
            .withPace(5)
            .withPower(99)
            .withPov(7)

        assertEquals(2, dialsOverflow.explicitness)
        assertEquals(2, dialsOverflow.pace)
        assertEquals(2, dialsOverflow.power)
        assertEquals(2, dialsOverflow.pov)

        val briefOverflow = Generator.brief(Random(456), dials = dialsOverflow)
        assertEquals(Banks.registers[2], briefOverflow.allParts().first { it.key == "register" }.value)
        assertEquals(Banks.pacingNotes[2], briefOverflow.allParts().first { it.key == "pacing" }.value)
    }

    // =========================================================================
    // 2. Character Card JSON & PNG Chunk Serialization with firstMessage
    // =========================================================================

    @Test
    fun `card fromBrief respects explicit firstMessage parameter`() {
        val brief = Generator.brief(Random(777), dials = Dials())
        val customGreeting = "The door is unlocked, but they still knock three times."

        val card = CharacterCard.fromBrief(
            brief = brief,
            firstMessage = customGreeting,
            exampleDialogue = "<START>\n{{user}}: Here.\n{{char}}: Finally.",
        )

        assertEquals(customGreeting, card.firstMessage)
        assertEquals("<START>\n{{user}}: Here.\n{{char}}: Finally.", card.exampleDialogue)
    }

    @Test
    fun `card fromBrief falls back to open slot body when firstMessage is null`() {
        val brief = Generator.brief(Random(888), dials = Dials())
        val openSlotBody = brief.slot("open")?.body.orEmpty()
        assertTrue("open slot body must be non-empty in standard brief", openSlotBody.isNotBlank())

        val card = CharacterCard.fromBrief(brief = brief, firstMessage = null)
        assertEquals(openSlotBody, card.firstMessage)
    }

    @Test
    fun `card fromBrief falls back to empty string when open slot is missing and firstMessage is null`() {
        val baseBrief = Generator.brief(Random(999), dials = Dials())
        // Construct a brief without the 'open' slot
        val briefWithoutOpen = baseBrief.copy(slots = baseBrief.slots.filterNot { it.key == "open" })

        val card = CharacterCard.fromBrief(brief = briefWithoutOpen, firstMessage = null)
        assertEquals("", card.firstMessage)
    }

    @Test
    fun `card firstMessage roundtrips through V1, V2, and V3 JSON`() {
        val customFirstMessage = "«Here is the boundary line,» she says softly. \"Cross it if you dare.\""
        val card = CharacterCard.Card(
            name = "Elena Rostova",
            description = "A disgraced diplomat seeking asylum.",
            personality = "composed, guarded, calculating",
            firstMessage = customFirstMessage,
            exampleDialogue = "<START>\n{{user}}: Credentials?\n{{char}}: *Hands over a charred passport.*",
        )

        // 1. Spec.V1 JSON serialization
        val v1Json = card.toJson(CharacterCard.Spec.V1)
        assertEquals(customFirstMessage, v1Json.getString("first_mes"))
        val v1Restored = CharacterCard.Card.fromJson(v1Json, CharacterCard.Spec.V1)
        assertEquals(customFirstMessage, v1Restored.firstMessage)

        // 2. Spec.V2 JSON serialization
        val v2Json = card.toJson(CharacterCard.Spec.V2)
        assertEquals(customFirstMessage, v2Json.getJSONObject("data").getString("first_mes"))
        val v2Restored = CharacterCard.Card.fromJson(v2Json, CharacterCard.Spec.V2)
        assertEquals(customFirstMessage, v2Restored.firstMessage)

        // 3. Spec.V3 JSON serialization
        val v3Json = card.toJson(CharacterCard.Spec.V3)
        assertEquals(customFirstMessage, v3Json.getJSONObject("data").getString("first_mes"))
        val v3Restored = CharacterCard.Card.fromJson(v3Json, CharacterCard.Spec.V3)
        assertEquals(customFirstMessage, v3Restored.firstMessage)
    }

    @Test
    fun `card firstMessage roundtrips through PNG tEXt chunks and ccv3 takes priority`() {
        val customGreeting = "Rain hammers against the skylight.\n*She checks her watch without looking up.*\n\"You're five minutes late.\""
        val originalCard = CharacterCard.Card(
            name = "Vesper Lind",
            description = "Audit investigator.",
            personality = "razor-sharp, unyielding",
            firstMessage = customGreeting,
            exampleDialogue = "<START>\n{{user}}: What did you find?\n{{char}}: \"More than you wanted me to.\"",
            groupOnlyGreetings = listOf("Notice: Group setting."),
        )

        // Encode to PNG
        val pngBytes = CharacterCard.encodePng(originalCard, size = 64)

        // 1. Verify PNG structure directly
        assertTrue("Must have PNG magic header", pngBytes.size > 8)
        assertEquals(0x89.toByte(), pngBytes[0])
        assertEquals(0x50.toByte(), pngBytes[1])
        assertEquals(0x4E.toByte(), pngBytes[2])
        assertEquals(0x47.toByte(), pngBytes[3])

        // 2. Extract tEXt chunks manually from PNG bytes
        val extractedChunks = extractPngTextChunks(pngBytes)
        assertTrue("PNG must contain 'ccv3' chunk", extractedChunks.containsKey(CharacterCard.KEY_V3))
        assertTrue("PNG must contain 'chara' chunk", extractedChunks.containsKey(CharacterCard.KEY_V2))

        val ccv3Raw = extractedChunks[CharacterCard.KEY_V3]!!
        // PNG chunks now store base64-encoded JSON per the chara card spec
        val ccv3Json = JSONObject(String(java.util.Base64.getDecoder().decode(ccv3Raw.trim()), Charsets.UTF_8))
        assertEquals(
            customGreeting,
            ccv3Json.getJSONObject("data").getString("first_mes"),
        )

        val charaRaw = extractedChunks[CharacterCard.KEY_V2]!!
        val charaJson = JSONObject(String(java.util.Base64.getDecoder().decode(charaRaw.trim()), Charsets.UTF_8))
        assertEquals(
            customGreeting,
            charaJson.getJSONObject("data").getString("first_mes"),
        )

        // 3. Round-trip through CharacterCard.read()
        val readResult = CharacterCard.read(pngBytes)
        assertTrue("read() should succeed", readResult is CharacterCard.ReadResult.Card2)
        val card2 = readResult as CharacterCard.ReadResult.Card2
        assertEquals(CharacterCard.Spec.V3, card2.spec)
        assertEquals("PNG", card2.format)
        assertEquals(originalCard.name, card2.card.name)
        assertEquals(customGreeting, card2.card.firstMessage)
        assertEquals(originalCard.exampleDialogue, card2.card.exampleDialogue)
        assertEquals(originalCard.groupOnlyGreetings, card2.card.groupOnlyGreetings)
    }

    @Test
    fun `card serialization handles unicode, emojis, and special control characters in firstMessage`() {
        val complexGreeting = "Café au lait ☕ — «Welcome to Zürich!»\n\t\"Special characters: \\ / \b \n \r \t ' \" & < > © € 😊\""
        val card = CharacterCard.Card(
            name = "Søren Kierkegaard 🖋️",
            description = "Danish philosopher exploring existential dread.",
            firstMessage = complexGreeting,
            exampleDialogue = "<START>\n{{user}}: What is anxiety?\n{{char}}: \"The dizziness of freedom.\"",
        )

        // JSON roundtrip
        val v2Json = card.toJson(CharacterCard.Spec.V2)
        val readV2 = CharacterCard.Card.fromJson(v2Json, CharacterCard.Spec.V2)
        assertEquals(complexGreeting, readV2.firstMessage)

        val v3Json = card.toJson(CharacterCard.Spec.V3)
        val readV3 = CharacterCard.Card.fromJson(v3Json, CharacterCard.Spec.V3)
        assertEquals(complexGreeting, readV3.firstMessage)

        // PNG roundtrip
        val pngBytes = CharacterCard.encodePng(card, size = 32)
        val readPngResult = CharacterCard.read(pngBytes) as CharacterCard.ReadResult.Card2
        assertEquals(complexGreeting, readPngResult.card.firstMessage)
        assertEquals(card.name, readPngResult.card.name)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun extractPngTextChunks(png: ByteArray): Map<String, String> {
        val out = mutableMapOf<String, String>()
        var i = 8
        while (i + 8 <= png.size) {
            val len = ((png[i].toInt() and 0xFF shl 24) or (png[i + 1].toInt() and 0xFF shl 16) or
                (png[i + 2].toInt() and 0xFF shl 8) or (png[i + 3].toInt() and 0xFF))
            if (len < 0 || i + 12 + len > png.size) break
            val type = String(png, i + 4, 4, Charsets.ISO_8859_1)
            if (type == "tEXt") {
                val data = png.copyOfRange(i + 8, i + 8 + len)
                val split = data.indexOfFirst { it == 0.toByte() }
                if (split > 0) {
                    val key = String(data, 0, split, Charsets.ISO_8859_1)
                    out[key] = String(data, split + 1, data.size - split - 1, Charsets.UTF_8)
                }
            }
            i += 12 + len
        }
        return out
    }
}

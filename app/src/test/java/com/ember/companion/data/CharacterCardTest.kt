package com.ember.companion.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterCardTest {

    private val sample = CharacterCard.Card(
        name = "Maren Vale",
        description = "A night-shift bartender who reads tarot badly.",
        personality = "dry, direct, tired",
        scenario = "The bar is empty and neither of them wants to leave.",
        firstMessage = "You're in early. That's either very good or very bad.",
        exampleDialogue = "<START>\n{{user}}: Another?\n{{char}}: Only if you're staying.",
        creatorNotes = "Generated on a phone.",
        systemPrompt = "Stay in character.",
        postHistoryInstructions = "Keep replies short.",
        alternateGreetings = listOf("You're early.", "Back again?"),
        tags = listOf("bar", "slow-burn"),
        creator = "Ember",
        characterVersion = "1.0",
        nickname = "Vale",
        groupOnlyGreetings = listOf("The room is full."),
        modificationDate = 1_700_000_000_000,
    )

    private fun brief(): Brief = Generator.brief(Random(4242), dials = Dials())

    // ---- brief -> card ---------------------------------------------------

    @Test
    fun `a brief becomes a card with a name and a body`() {
        val b = brief()
        val card = CharacterCard.fromBrief(b, tags = listOf("bar"))
        assertTrue("name should come from the cast", card.name.isNotBlank())
        assertTrue(
            "name should be the character, not the brief title",
            card.name != b.title || b.allParts().any { it.key == "aname" && it.value.startsWith(card.name) },
        )
        assertTrue("description should carry the cast", card.description.contains("## Cast"))
        assertTrue("description should carry the setting", card.description.contains("## Setting"))
        assertTrue("scenario should come from the frame", card.scenario.contains("Register"))
        assertTrue("first message should come from the open", card.firstMessage.isNotBlank())
    }

    @Test
    fun `personality is a short trait summary and never the whole body`() {
        val card = CharacterCard.fromBrief(brief())
        assertTrue("personality should be populated", card.personality.isNotBlank())
        assertTrue(
            "personality must be a summary, not a copy of the description",
            card.personality.length <= 300,
        )
        assertTrue(
            "personality must not contain section headings",
            !card.personality.contains("##"),
        )
    }

    @Test
    fun `hidden title parts stay out of the card`() {
        val card = CharacterCard.fromBrief(brief())
        assertTrue(!card.description.contains("titleNoun"))
    }

    // ---- JSON ------------------------------------------------------------

    @Test
    fun `v2 json has the spec envelope and the required keys`() {
        val json = JSONObject(sample.toJsonText(CharacterCard.Spec.V2))
        assertEquals("chara_card_v2", json.getString("spec"))
        assertEquals("2.0", json.getString("spec_version"))
        val data = json.getJSONObject("data")
        listOf(
            "name", "description", "personality", "scenario", "first_mes",
            "mes_example", "creator_notes", "system_prompt",
            "post_history_instructions", "alternate_greetings", "tags",
            "creator", "character_version", "extensions",
        ).forEach { key ->
            assertTrue("V2 data must contain $key", data.has(key))
        }
    }

    @Test
    fun `v1 json is flat with no spec envelope`() {
        val json = JSONObject(sample.toJsonText(CharacterCard.Spec.V1))
        assertTrue("V1 must not carry a spec key", !json.has("spec"))
        assertTrue(json.has("name"))
        assertTrue(json.has("mes_example"))
    }

    @Test
    fun `v3 includes the fields v2 lacks`() {
        val data = JSONObject(sample.toJsonText(CharacterCard.Spec.V3)).getJSONObject("data")
        assertTrue("group_only_greetings is required in V3", data.has("group_only_greetings"))
        assertTrue(data.has("nickname"))
        assertTrue(data.has("assets"))
        assertTrue(data.has("creation_date"))
    }

    @Test
    fun `v3 group_only_greetings is present even when empty`() {
        val bare = sample.copy(groupOnlyGreetings = emptyList())
        val data = JSONObject(bare.toJsonText(CharacterCard.Spec.V3)).getJSONObject("data")
        assertTrue(data.has("group_only_greetings"))
        assertEquals(0, data.getJSONArray("group_only_greetings").length())
    }

    @Test
    fun `v3 dates are written in seconds`() {
        val data = JSONObject(sample.toJsonText(CharacterCard.Spec.V3)).getJSONObject("data")
        assertEquals(1_700_000_000L, data.getLong("modification_date"))
    }

    @Test
    fun `a v2 card round trips through json`() {
        val text = sample.toJsonText(CharacterCard.Spec.V2)
        val back = CharacterCard.read(text.toByteArray())
        assertTrue(back is CharacterCard.ReadResult.Card2)
        val card = (back as CharacterCard.ReadResult.Card2).card
        assertEquals(sample.name, card.name)
        assertEquals(sample.firstMessage, card.firstMessage)
        assertEquals(sample.alternateGreetings, card.alternateGreetings)
        assertEquals(sample.tags, card.tags)
    }

    @Test
    fun `a flat v1 card is read as v1`() {
        val text = sample.toJsonText(CharacterCard.Spec.V1)
        val back = CharacterCard.read(text.toByteArray()) as CharacterCard.ReadResult.Card2
        assertEquals(CharacterCard.Spec.V1, back.spec)
        assertEquals(sample.name, back.card.name)
    }

    // ---- PNG -------------------------------------------------------------

    @Test
    fun `png export carries both chunks`() {
        val png = CharacterCard.encodePng(sample)
        val chunks = pngChunkTexts(png)
        assertTrue("expected a ccv3 chunk", chunks.containsKey(CharacterCard.KEY_V3))
        assertTrue("expected a chara chunk", chunks.containsKey(CharacterCard.KEY_V2))
    }

    @Test
    fun `png round trips and ccv3 wins`() {
        val png = CharacterCard.encodePng(sample)
        val back = CharacterCard.read(png)
        assertTrue(back is CharacterCard.ReadResult.Card2)
        val result = back as CharacterCard.ReadResult.Card2
        assertEquals("ccv3 must take precedence", CharacterCard.Spec.V3, result.spec)
        assertEquals(sample.name, result.card.name)
        assertEquals(sample.groupOnlyGreetings, result.card.groupOnlyGreetings)
        assertEquals(sample.nickname, result.card.nickname)
    }

    @Test
    fun `png is a structurally valid png`() {
        val png = CharacterCard.encodePng(sample, size = 64)
        assertEquals(0x89.toByte(), png[0])
        assertEquals(0x50.toByte(), png[1])
        assertEquals(0x4E.toByte(), png[2])
        assertEquals(0x47.toByte(), png[3])
        assertTrue("IHDR must be the first chunk", pngChunkTypes(png).first() == "IHDR")
        assertEquals("IEND must be last", "IEND", pngChunkTypes(png).last())
    }

    @Test
    fun `png chunk crc values are correct`() {
        val png = CharacterCard.encodePng(sample, size = 32)
        val types = pngChunkTypes(png)
        assertEquals(listOf("IHDR", "tEXt", "tEXt", "IDAT", "IEND"), types)
        // Re-deriving every CRC catches a corrupt writer that would otherwise only
        // fail on a real device's stricter decoder.
        var i = 8
        while (i + 8 <= png.size) {
            val len = ((png[i].toInt() and 0xFF shl 24) or (png[i + 1].toInt() and 0xFF shl 16) or
                (png[i + 2].toInt() and 0xFF shl 8) or (png[i + 3].toInt() and 0xFF))
            val type = String(png, i + 4, 4, Charsets.ISO_8859_1)
            val body = png.copyOfRange(i + 4, i + 8 + len)
            val crc = java.util.zip.CRC32().apply { update(body) }.value.toInt()
            val stored = (png[i + 8 + len].toInt() and 0xFF shl 24) or
                (png[i + 9 + len].toInt() and 0xFF shl 16) or
                (png[i + 10 + len].toInt() and 0xFF shl 8) or
                (png[i + 11 + len].toInt() and 0xFF)
            assertEquals("bad CRC for $type", crc, stored)
            i += 12 + len
        }
    }

    @Test
    fun `a v2-only png still reads`() {
        val png = CharacterCard.encodePng(sample)
        val v2Only = ByteArrayOutputStream().apply {
            write(png)
        }.toByteArray()
        // Strip the ccv3 chunk by rebuilding a PNG with only chara.
        val stripped = stripChunk(v2Only, CharacterCard.KEY_V3)
        val back = CharacterCard.read(stripped) as CharacterCard.ReadResult.Card2
        assertEquals(CharacterCard.Spec.V2, back.spec)
        assertEquals(sample.name, back.card.name)
    }

    @Test
    fun `a png with no card data fails with a readable reason`() {
        val png = CharacterCard.encodePng(sample, size = 16)
        val stripped = stripChunk(stripChunk(png, CharacterCard.KEY_V3), CharacterCard.KEY_V2)
        val back = CharacterCard.read(stripped)
        assertTrue(back is CharacterCard.ReadResult.Failure)
        assertTrue(
            (back as CharacterCard.ReadResult.Failure).reason.contains("No character card"),
        )
    }

    @Test
    fun `a truncated png does not throw`() {
        val png = CharacterCard.encodePng(sample, size = 16)
        for (cut in listOf(10, 40, png.size / 2, png.size - 5)) {
            CharacterCard.read(png.copyOf(cut))
        }
    }

    // ---- CHARX -----------------------------------------------------------

    @Test
    fun `a charx archive round trips`() {
        val archive = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("card.json"))
                zip.write(sample.toJsonText(CharacterCard.Spec.V3).toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val back = CharacterCard.read(archive)
        assertTrue(back is CharacterCard.ReadResult.Card2)
        val result = back as CharacterCard.ReadResult.Card2
        assertEquals("CHARX", result.format)
        assertEquals(sample.name, result.card.name)
    }

    @Test
    fun `a charx without card json fails cleanly`() {
        val archive = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("other.json"))
                zip.write("{}".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val back = CharacterCard.read(archive)
        assertTrue(back is CharacterCard.ReadResult.Failure)
    }

    @Test
    fun `garbage input fails cleanly`() {
        listOf(
            "not json at all".toByteArray(),
            ByteArray(0),
            "{".toByteArray(),
            "{\"data\":".toByteArray(),
        ).forEach {
            assertTrue(CharacterCard.read(it) is CharacterCard.ReadResult.Failure)
        }
    }

    // ---- platforms -------------------------------------------------------

    @Test
    fun `every platform declares a spec and a format`() {
        CardPlatforms.ALL.forEach { p ->
            assertNotNull(p.id)
            assertTrue("format missing for ${p.id}", p.format.label.isNotBlank())
            assertTrue("note missing for ${p.id}", p.note.isNotBlank())
        }
    }

    @Test
    fun `chub swaps description and personality`() {
        val chub = CardPlatforms.byId("chub").adapt(sample)
        assertEquals("chub personality holds the body", sample.description, chub.personality)
        assertEquals("chub description holds the notes", sample.creatorNotes, chub.description)
        assertEquals(
            "the spec personality is preserved, not clobbered",
            sample.personality,
            chub.extensions["tavern_personality"],
        )
        assertTrue("chub export is flagged lossy", CardPlatforms.byId("chub").lossy)
    }

    @Test
    fun `extensions survive a json round trip`() {
        val chub = CardPlatforms.byId("chub").adapt(sample)
        val back = CharacterCard.read(
            chub.toJsonText(CharacterCard.Spec.V2).toByteArray(),
        ) as CharacterCard.ReadResult.Card2
        assertEquals(chub.extensions, back.card.extensions)
    }

    @Test
    fun `agnai blanks personality`() {
        val agnai = CardPlatforms.byId("agnai").adapt(sample)
        assertEquals("", agnai.personality)
        assertEquals(sample.description, agnai.description)
    }

    @Test
    fun `character ai applies the documented field caps`() {
        val p = CardPlatforms.byId("characterai")
        val adapted = p.adapt(sample.copy(personality = "x".repeat(200)))
        assertEquals(50, adapted.personality.length)
        assertEquals(500, p.limits[CardFields.DESCRIPTION])
        assertTrue(p.copyOnly)
    }

    @Test
    fun `platforms without an import endpoint are marked copy only`() {
        assertTrue(CardPlatforms.byId("characterai").copyOnly)
        assertTrue(CardPlatforms.byId("janitorai").copyOnly)
        assertTrue(!CardPlatforms.byId("sillytavern").copyOnly)
    }

    @Test
    fun `a custom mapping uses the user's own field names`() {
        val custom = CardPlatforms.Custom(
            rows = listOf(
                CardPlatforms.CustomRow("char_name", CardFields.NAME),
                CardPlatforms.CustomRow("char_persona", CardFields.DESCRIPTION),
                CardPlatforms.CustomRow("char_greeting", CardFields.FIRST_MES),
            ),
        )
        val json = JSONObject(custom.toJsonText(sample))
        assertEquals("Maren Vale", json.getString("char_name"))
        assertEquals(sample.description, json.getString("char_persona"))
        assertEquals(sample.firstMessage, json.getString("char_greeting"))
        assertTrue("unmapped targets must be absent", !json.has("personality"))
    }

    @Test
    fun `a custom mapping can emit arrays for greetings and tags`() {
        val custom = CardPlatforms.Custom(
            rows = listOf(
                CardPlatforms.CustomRow("greetings", CardFields.ALTERNATE_GREETINGS),
                CardPlatforms.CustomRow("keywords", CardFields.TAGS),
            ),
        )
        val json = JSONObject(custom.toJsonText(sample))
        assertEquals(2, json.getJSONArray("greetings").length())
        assertEquals(2, json.getJSONArray("keywords").length())
    }

    @Test
    fun `a blank target name is skipped`() {
        val custom = CardPlatforms.Custom(
            rows = listOf(
                CardPlatforms.CustomRow("  ", CardFields.NAME),
                CardPlatforms.CustomRow("name", CardFields.NAME),
            ),
        )
        assertEquals(1, JSONObject(custom.toJsonText(sample)).length())
    }

    // ---- helpers ---------------------------------------------------------

    private fun pngChunkTypes(png: ByteArray): List<String> {
        val out = mutableListOf<String>()
        var i = 8
        while (i + 8 <= png.size) {
            val len = ((png[i].toInt() and 0xFF shl 24) or (png[i + 1].toInt() and 0xFF shl 16) or
                (png[i + 2].toInt() and 0xFF shl 8) or (png[i + 3].toInt() and 0xFF))
            if (len < 0 || i + 12 + len > png.size) break
            out += String(png, i + 4, 4, Charsets.ISO_8859_1)
            i += 12 + len
        }
        return out
    }

    private fun pngChunkTexts(png: ByteArray): Map<String, String> {
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
                    out[key] = String(data, split + 1, data.size - split - 1, Charsets.ISO_8859_1)
                }
            }
            i += 12 + len
        }
        return out
    }

    /** Rebuilds a PNG without the tEXt chunk carrying [keyword]. */
    private fun stripChunk(png: ByteArray, keyword: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(png, 0, 8)
        var i = 8
        while (i + 8 <= png.size) {
            val len = ((png[i].toInt() and 0xFF shl 24) or (png[i + 1].toInt() and 0xFF shl 16) or
                (png[i + 2].toInt() and 0xFF shl 8) or (png[i + 3].toInt() and 0xFF))
            if (len < 0 || i + 12 + len > png.size) break
            val type = String(png, i + 4, 4, Charsets.ISO_8859_1)
            val chunk = png.copyOfRange(i, i + 12 + len)
            // Within the chunk copy: 4 length bytes + 4 type bytes, then the data.
            val keep = !(type == "tEXt" && String(chunk, 8, len, Charsets.ISO_8859_1)
                .substringBefore(" ") == keyword)
            if (keep) out.write(chunk)
            i += 12 + len
        }
        return out.toByteArray()
    }
}

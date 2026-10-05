package com.ember.companion

import com.ember.companion.data.CardFields
import com.ember.companion.data.CardPlatformSchemas
import com.ember.companion.data.FieldType
import com.ember.companion.data.FormField
import com.ember.companion.data.PlatformFieldFetcher
import com.ember.companion.data.PlatformSchema
import com.ember.companion.data.SchemaField
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The platform field list is only useful if it is right, and the two ways it
 * goes wrong are quiet: wrong key names produce JSON no platform will import,
 * and invented character caps truncate someone's work without saying so.
 */
class PlatformSchemaTest {

    @Test
    fun everyBuiltInFieldUsesARealPlatformKey() {
        // These are the keys the platforms actually read. A typo here means an
        // export that silently imports as an empty card.
        val known = setOf(
            CardFields.NAME, CardFields.DESCRIPTION, CardFields.PERSONALITY,
            CardFields.SCENARIO, CardFields.FIRST_MES, CardFields.MES_EXAMPLE,
            CardFields.CREATOR_NOTES, CardFields.SYSTEM_PROMPT, CardFields.POST_HISTORY,
            CardFields.ALTERNATE_GREETINGS, CardFields.TAGS, CardFields.CREATOR,
            CardFields.CHARACTER_VERSION, CardFields.NICKNAME,
        )
        // Platform-specific extras that legitimately have no spec home.
        val extras = setOf(
            "tag_line", "group_only_greetings", "tagline",
            "short_description", "greeting", "definition",
            // FictionLab's own names.
            "content", "personality_summary", "first_message", "example_dialogue",
            // PersonaForge native fields.
            "character_name", "mode", "storyTone", "relationship",
            "worldAtmosphere", "keyLocations", "scenarioConflict",
            "scenarioStakes", "timePeriod", "incitingIncident",
            "characterFlaws", "secretMotive", "speechPattern", "quirks",
            "greetingMessage", "scenarioInstructions",
            "suggestedPlayerName", "suggestedPlayerDescription", "clothing",
            "backstory", "appearance",
        )
        CardPlatformSchemas.BUILT_IN.forEach { schema ->
            assertTrue("${schema.label} has no fields", schema.fields.isNotEmpty())
            schema.fields.forEach { field ->
                assertTrue(
                    "${schema.label}.${field.key} uses an unrecognised key",
                    field.key in known || field.key in extras,
                )
                assertTrue("${schema.label}.${field.key} has a blank label", field.label.isNotBlank())
            }
        }
    }

    @Test
    fun fieldKeysAreUniquePerPlatform() {
        // Duplicate keys would collapse into one JSON property, losing a field.
        CardPlatformSchemas.BUILT_IN.forEach { schema ->
            val keys = schema.fields.map { it.key.lowercase() }
            assertEquals(
                "${schema.label} has duplicate field keys: ${keys.groupingBy { it }.eachCount().filter { it.value > 1 }.keys}",
                keys.size,
                keys.distinct().size,
            )
        }
    }

    @Test
    fun chubDoesNotInvertTheBodyAndTheNotes() {
        // The one field mapping that silently loses a character if wrong.
        val chub = CardPlatformSchemas.byId("chub")
        val personality = chub.field(CardFields.PERSONALITY)
        val description = chub.field(CardFields.DESCRIPTION)
        assertEquals(CardFields.PERSONALITY, personality?.source)
        assertEquals(CardFields.CREATOR_NOTES, description?.source)
        assertTrue(chub.lossy)
    }

    @Test
    fun characterAiLimitsAreTheRealOnes() {
        val cai = CardPlatformSchemas.byId("characterai")
        assertEquals(50, cai.field("tagline")?.maxChars)
        assertEquals(500, cai.field("short_description")?.maxChars)
        assertEquals(32_000, cai.field("definition")?.maxChars)
        assertTrue(cai.copyOnly)
        // Nothing may claim a cap Ember was not told about.
        assertEquals(0, cai.field("name")?.maxChars)
    }

    @Test
    fun limitIsNeverInventedForUnboundedFields() {
        val st = CardPlatformSchemas.byId("sillytavern")
        assertEquals(0, st.field(CardFields.DESCRIPTION)?.maxChars)
        // A SHORT field still gets a display default so the counter has a scale.
        assertEquals(200, st.field(CardFields.NAME)?.limit)
        assertNull(st.field(CardFields.DESCRIPTION)?.percentUsed("x"))
    }

    @Test
    fun overLimitIsOnlyTrueAgainstAKnownCap() {
        val cai = CardPlatformSchemas.byId("characterai")
        val tagline = cai.field("tagline")!!
        assertTrue(tagline.exceedsLimit("x".repeat(51)))
        assertFalse(tagline.exceedsLimit("x".repeat(50)))
        val unbounded = CardPlatformSchemas.byId("sillytavern").field(CardFields.DESCRIPTION)!!
        assertFalse(unbounded.exceedsLimit("x".repeat(100_000)))
    }

    @Test
    fun tagsAndAlternateGreetingsBecomeRealJsonArrays() {
        // Every one of these platforms reads arrays back, not comma-joined text.
        val st = CardPlatformSchemas.byId("sillytavern")
        val json = CardPlatformSchemas.toJson(
            st,
            mapOf(
                CardFields.NAME to "Rin",
                CardFields.TAGS to " tsundere , engineer , student ",
                CardFields.ALTERNATE_GREETINGS to "First\n\n  Second  \n\n",
            ),
        )
        assertEquals("Rin", json.getString(CardFields.NAME))
        assertEquals(3, json.getJSONArray(CardFields.TAGS).length())
        assertEquals("tsundere", json.getJSONArray(CardFields.TAGS).getString(0))
        assertEquals(2, json.getJSONArray(CardFields.ALTERNATE_GREETINGS).length())
        assertEquals("Second", json.getJSONArray(CardFields.ALTERNATE_GREETINGS).getString(1))
    }

    @Test
    fun blankFieldsAreOmittedNotEmittedEmpty() {
        val st = CardPlatformSchemas.byId("sillytavern")
        val json = CardPlatformSchemas.toJson(st, mapOf(CardFields.NAME to "Rin", CardFields.SCENARIO to "   "))
        assertTrue(json.has(CardFields.NAME))
        assertFalse(json.has(CardFields.SCENARIO))
    }

    @Test
    fun completenessAndMissingRequiredAreHonest() {
        val st = CardPlatformSchemas.byId("sillytavern")
        val empty = st.fields.associate { it.key to "" }
        assertEquals(0f, st.completeness(empty), 0.001f)
        val partial = empty.toMutableMap().apply { this[CardFields.NAME] = "Rin" }
        assertEquals(1f / st.fields.size, st.completeness(partial), 0.001f)
        // The name is required, so it is reported missing while blank...
        assertTrue(st.missingRequired(empty).any { it.key == CardFields.NAME })
        // ...and stops being reported the moment it has text. SillyTavern
        // requires only a name, so a brief with just that is genuinely complete.
        assertTrue(st.missingRequired(partial).none { it.key == CardFields.NAME })
        assertTrue(st.missingRequired(partial).isEmpty())
        // A platform with more required fields still calls them out.
        val chub = CardPlatformSchemas.byId("chub")
        assertTrue(chub.missingRequired(partial).any { it.key == CardFields.PERSONALITY })
    }

    // ---- form importing ---------------------------------------------------

    private val sampleForm = """
    <html><body>
      <form>
        <label for="fname">Full name</label>
        <input type="text" id="fname" name="fname" maxlength="40" required>
        <label for="bio">Bio <span>*</span></label>
        <textarea id="bio" name="bio" placeholder="Tell us about them"></textarea>
        <label for="genre">Genre</label>
        <select id="genre" name="genre">
          <option>Fantasy</option><option>Sci-Fi</option><option>Slice of Life</option>
        </select>
        <input type="hidden" name="authenticity_token" value="abc">
        <input type="submit" value="Create">
        <input type="file" name="avatar">
      </form>
    </body></html>
    """.trimIndent()

    @Test
    fun readsLabelsTypesCapsAndOptions() {
        val fields = PlatformFieldFetcher.parseFormFields(sampleForm)
        val byName = fields.associateBy { it.name }

        assertEquals("Full name", byName["fname"]?.label)
        assertEquals(40, byName["fname"]?.maxChars)
        assertTrue(byName["fname"]?.required == true)

        assertEquals("textarea", byName["bio"]?.type)
        assertEquals("Tell us about them", byName["bio"]?.example)

        assertEquals(listOf("Fantasy", "Sci-Fi", "Slice of Life"), byName["genre"]?.options)
    }

    @Test
    fun dropsNonDataControls() {
        // CSRF tokens and file pickers are not character fields.
        val names = PlatformFieldFetcher.parseFormFields(sampleForm).map { it.name }
        assertFalse(names.contains("authenticity_token"))
        assertFalse(names.contains("avatar"))
    }

    @Test
    fun unknownCapsStayUncapped() {
        // No maxlength in the markup means Ember must not guess one.
        val fields = PlatformFieldFetcher.parseFormFields(sampleForm).associateBy { it.name }
        assertEquals(0, fields["bio"]?.maxChars)
        assertNull(fields["bio"]?.let { SchemaField(key = it.name, label = it.label).percentUsed("x") })
    }

    @Test
    fun importedSchemaIsSelectableAndEditable() {
        val fields = PlatformFieldFetcher.parseFormFields(sampleForm)
        val schema = CardPlatformSchemas.fromForm("https://example.com/create", fields)

        assertTrue("example-com".contains(schema.id) || schema.id.startsWith("custom-"))
        assertEquals(3, schema.fields.size)
        assertTrue(schema.fields.all { it.custom })
        assertEquals(CardFormatCompat.JSON, schema.format)
        // A field whose name matches a known spec field should point at it.
        assertTrue(schema.fields.any { it.source == CardFields.NAME })
    }

    @Test
    fun normalisesBareHosts() {
        assertEquals("https://example.com/x", PlatformFieldFetcher.normaliseUrl("example.com/x"))
        assertEquals("http://a.test/", PlatformFieldFetcher.normaliseUrl("http://a.test/"))
        assertEquals("https://b.test", PlatformFieldFetcher.normaliseUrl("  b.test  "))
    }

    @Test
    fun personaForgeProducesCanonicalTransferSchemaVersion1() {
        val pf = CardPlatformSchemas.byId("personaforge")
        assertEquals("PersonaForge", pf.label)
        val values = mapOf(
            "name" to "Neon Cyberpunk Heist",
            "mode" to "ROLEPLAY",
            "character_name" to "Valerie V",
            "personality" to "Sharp, calculating, cybernetically enhanced mercenary.",
            "greetingMessage" to "The rain slicks the neon-drenched alleyway as I check my smartgun."
        )
        val json = CardPlatformSchemas.toJson(pf, values)
        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals("android", json.getString("sourceApp"))
        assertTrue(json.has("scenario"))
        val scenario = json.getJSONObject("scenario")
        assertEquals("Neon Cyberpunk Heist", scenario.getString("name"))
        assertEquals("ROLEPLAY", scenario.getString("mode"))
        assertTrue(scenario.has("characterProfile"))
        val profile = scenario.getJSONObject("characterProfile")
        assertEquals("Valerie V", profile.getString("name"))
        assertEquals("Sharp, calculating, cybernetically enhanced mercenary.", profile.getString("personality"))
        assertTrue(json.has("messages"))
        val messages = json.getJSONArray("messages")
        assertEquals(1, messages.length())
        assertEquals("The rain slicks the neon-drenched alleyway as I check my smartgun.", messages.getJSONObject(0).getString("text"))
        assertEquals("model", messages.getJSONObject(0).getString("role"))
    }
}

/** CardFormat is in the data package; aliased so the import list stays short. */
private object CardFormatCompat {
    val JSON = com.ember.companion.data.CardFormat.JSON
}
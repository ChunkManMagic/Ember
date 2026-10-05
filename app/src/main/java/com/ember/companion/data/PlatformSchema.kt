package com.ember.companion.data

import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject

/**
 * What a target platform actually asks for.
 *
 * The Lab used to know a platform's *name* and its file format, and nothing
 * about its shape — so brainstorming produced a freeform brief that only got
 * shaped at export time, by hand, in a modal. This moves that knowledge
 * forward: pick a platform in the Lab and the fields it requires are the
 * fields you fill in, in the order it wants them, with its own limits shown.
 *
 * Everything here is descriptive. Ember still exports nothing itself; the bytes
 * go to the platform the same way they always did.
 */
@Serializable
enum class FieldType {
    /** Single-line input: a name, a tagline. */
    SHORT,

    /** Multi-line body: a persona, a scenario, a definition. */
    LONG,

    /** Comma-separated, stored as a JSON array on export. */
    TAGS,

    /** One entry per line, stored as a JSON array on export. */
    ALTERNATES,
}

/**
 * One field the platform expects.
 *
 * [source] points at a [CardFields] constant wherever the platform uses a name
 * Ember already knows, which is what lets an existing generated brief flow
 * straight into the right box. A field with a blank [source] is one the platform
 * wants but the spec has no home for (Chub's `tag_line`, Risu's `nickname`) —
 * those are written straight through rather than silently dropped.
 */
@Serializable
data class SchemaField(
    /** The platform's own field name. This is the key in exported JSON. */
    val key: String,
    /** Human label for the form. */
    val label: String,
    val type: FieldType = FieldType.LONG,
    val required: Boolean = false,
    /** The platform's hard cap, 0 when unstated. Surfaced as a live counter. */
    val maxChars: Int = 0,
    /** Guidance shown under the input and handed to the AI when filling it. */
    val hint: String = "",
    /** [CardFields] source id, or "" when the platform has no spec equivalent. */
    val source: String = "",
    /** True when the user added this one rather than it being built in. */
    val custom: Boolean = false,
) {
    /** Effective limit: an explicit cap, else a sane default for the type. */
    val limit: Int
        get() = when {
            maxChars > 0 -> maxChars
            type == FieldType.SHORT -> 200
            else -> 0
        }

    /** Whether [text] fits this field's published cap. */
    fun exceedsLimit(text: String?): Boolean {
        val n = text?.length ?: 0
        return limit > 0 && n > limit
    }

    /** How close to the cap [text] is, or null when the cap is unknown. */
    fun percentUsed(text: String?): Float? {
        if (limit <= 0) return null
        return ((text?.length ?: 0).toFloat() / limit).coerceIn(0f, 1f)
    }
}

/**
 * A platform's full field list plus how to export it.
 *
 * Built-ins are curated (see [CardPlatformSchemas]); user-defined ones come from
 * "add your own platform" or from a fetched form, and are stored locally.
 */
@Serializable
data class PlatformSchema(
    val id: String,
    val label: String,
    val fields: List<SchemaField> = emptyList(),
    val spec: CharacterCard.Spec = CharacterCard.Spec.V2,
    val format: CardFormat = CardFormat.JSON,
    val note: String = "",
    /** True when the platform cannot import a card file at all. */
    val copyOnly: Boolean = false,
    /** True when the field list cannot represent everything Ember generates. */
    val lossy: Boolean = false,
    val builtIn: Boolean = false,
) {
    fun field(key: String): SchemaField? = fields.firstOrNull { it.key == key }

    val requiredFields: List<SchemaField> get() = fields.filter { it.required }

    /** How much of a draft is actually filled in, 0f..1f. */
    fun completeness(values: Map<String, String>): Float {
        if (fields.isEmpty()) return 0f
        val filled = fields.count { !values[it.key].isNullOrBlank() }
        return filled.toFloat() / fields.size
    }

    /** Missing required keys — the thing worth warning about before exporting. */
    fun missingRequired(values: Map<String, String>): List<SchemaField> =
        requiredFields.filter { values[it.key].isNullOrBlank() }
}

/**
 * Curated schemas for the platforms Ember knows.
 *
 * Field names are the platforms' real ones — that is the whole point, since an
 * export only imports if the keys match. Caps are the published limits where a
 * platform documents one, and 0 where it doesn't; Ember never invents a limit it
 * wasn't told, because a wrong cap would silently truncate someone's work.
 */
object CardPlatformSchemas {

    private fun f(
        key: String,
        label: String,
        type: FieldType = FieldType.LONG,
        required: Boolean = false,
        maxChars: Int = 0,
        hint: String = "",
        source: String = "",
        custom: Boolean = false,
    ) = SchemaField(key, label, type, required, maxChars, hint, source, custom)

    /** The V2 field set, which most platforms are a variation of. */
    private fun v2Common(nameLabel: String = "Name"): List<SchemaField> = listOf(
        f(CardFields.NAME, nameLabel, FieldType.SHORT, required = true, hint = "The character's name as the platform will display it.", source = CardFields.NAME),
        f(CardFields.DESCRIPTION, "Description", hint = "What the character is, in a paragraph.", source = CardFields.DESCRIPTION),
        f(CardFields.PERSONALITY, "Personality", hint = "Traits, mannerisms, and how they behave under pressure.", source = CardFields.PERSONALITY),
        f(CardFields.SCENARIO, "Scenario", hint = "Where the story starts and what is going on.", source = CardFields.SCENARIO),
        f(CardFields.FIRST_MES, "First message", hint = "The opening greeting, already in character.", source = CardFields.FIRST_MES),
        f(CardFields.MES_EXAMPLE, "Example dialogue", hint = "A few sample exchanges showing the voice.", source = CardFields.MES_EXAMPLE),
        f(CardFields.CREATOR_NOTES, "Creator notes", FieldType.SHORT, hint = "Shown to other users, not the character.", source = CardFields.CREATOR_NOTES),
        f(CardFields.SYSTEM_PROMPT, "System prompt", hint = "Instructions the model follows while in character.", source = CardFields.SYSTEM_PROMPT),
        f(CardFields.POST_HISTORY, "Post-history instructions", hint = "Applied after the chat history each turn.", source = CardFields.POST_HISTORY),
        f(CardFields.ALTERNATE_GREETINGS, "Alternate greetings", FieldType.ALTERNATES, hint = "One alternative opening per line.", source = CardFields.ALTERNATE_GREETINGS),
        f(CardFields.TAGS, "Tags", FieldType.TAGS, hint = "Comma-separated discovery tags.", source = CardFields.TAGS),
        f(CardFields.CREATOR, "Creator", FieldType.SHORT, source = CardFields.CREATOR),
        f(CardFields.CHARACTER_VERSION, "Character version", FieldType.SHORT, source = CardFields.CHARACTER_VERSION),
    )

    private val sillyTavern = PlatformSchema(
        id = "sillytavern",
        label = "SillyTavern",
        fields = v2Common(),
        spec = CharacterCard.Spec.V2,
        format = CardFormat.PNG,
        note = "Reads V2 straight through. Ember writes both PNG chunks, so the same file " +
            "also opens in RisuAI and Foreverse.",
        builtIn = true,
    )

    private val risuAi = PlatformSchema(
        id = "risuai",
        label = "RisuAI",
        fields = v2Common() + listOf(
            f(CardFields.NICKNAME, "Nickname", FieldType.SHORT, hint = "What the character calls you.", source = CardFields.NICKNAME),
            f("group_only_greetings", "Group-only greetings", FieldType.ALTERNATES, hint = "Openings that only fire in group chats."),
        ),
        spec = CharacterCard.Spec.V3,
        format = CardFormat.PNG,
        note = "V3, including group-only greetings and the nickname field.",
        builtIn = true,
    )

    private val agnai = PlatformSchema(
        id = "agnai",
        label = "Agnai",
        fields = listOf(
            f(CardFields.NAME, "Name", FieldType.SHORT, required = true, source = CardFields.NAME),
            f(CardFields.DESCRIPTION, "Description (persona)", hint = "Agnai keeps the whole persona here and ignores a separate personality field.", required = true, source = CardFields.DESCRIPTION),
            f(CardFields.SCENARIO, "Scenario", source = CardFields.SCENARIO),
            f(CardFields.FIRST_MES, "First message", source = CardFields.FIRST_MES),
            f(CardFields.MES_EXAMPLE, "Example dialogue", source = CardFields.MES_EXAMPLE),
            f(CardFields.CREATOR_NOTES, "Creator notes", FieldType.SHORT, source = CardFields.CREATOR_NOTES),
            f(CardFields.TAGS, "Tags", FieldType.TAGS, source = CardFields.TAGS),
        ),
        spec = CharacterCard.Spec.V2,
        format = CardFormat.PNG,
        note = "Personality is left out on purpose: Agnai keeps the persona inside the " +
            "description, and sending both duplicates the body on re-import.",
        lossy = true,
        builtIn = true,
    )

    /**
     * Chub inverts two core fields: it reads `description` as creator notes and
     * `personality` as the character body. Getting this wrong silently swaps a
     * character's body for its author's notes, so the schema spells it out and
     * Ember writes the spec personality under Chub's deprecated alias.
     */
    private val chub = PlatformSchema(
        id = "chub",
        label = "Chub.ai",
        fields = listOf(
            f(CardFields.NAME, "Name", FieldType.SHORT, required = true, source = CardFields.NAME),
            f("tag_line", "Tagline", FieldType.SHORT, hint = "Chub's short hook line. No spec equivalent, written through as-is."),
            f(CardFields.PERSONALITY, "Personality (the character body)", hint = "Chub stores the character here — this is the reverse of the spec.", required = true, source = CardFields.PERSONALITY),
            f(CardFields.DESCRIPTION, "Description (Chub reads this as creator notes)", hint = "Not the character's body. Put your author notes here.", source = CardFields.CREATOR_NOTES),
            f(CardFields.SCENARIO, "Scenario", source = CardFields.SCENARIO),
            f(CardFields.FIRST_MES, "First message", source = CardFields.FIRST_MES),
            f(CardFields.MES_EXAMPLE, "Example dialogue", source = CardFields.MES_EXAMPLE),
            f(CardFields.TAGS, "Tags", FieldType.TAGS, source = CardFields.TAGS),
        ),
        spec = CharacterCard.Spec.V2,
        format = CardFormat.JSON,
        note = "Chub swaps description and personality. This field list matches Chub's " +
            "names, and keeps the spec personality under the deprecated " +
            "tavern_personality alias so nothing is lost.",
        lossy = true,
        builtIn = true,
    )

    /**
     * Character.AI has no card import and enforces short hard caps, so its
     * fields are named the way its own editors name them.
     */
    private val characterAi = PlatformSchema(
        id = "characterai",
        label = "Character.AI",
        fields = listOf(
            f("name", "Name", FieldType.SHORT, required = true, source = CardFields.NAME),
            f("tagline", "Tagline", FieldType.SHORT, required = true, maxChars = 50, hint = "Hard limit: 50 characters.", source = CardFields.PERSONALITY),
            f("short_description", "Short description", required = true, maxChars = 500, hint = "Hard limit: 500 characters.", source = CardFields.DESCRIPTION),
            f("greeting", "Greeting", maxChars = 5_000, hint = "The opening message.", source = CardFields.FIRST_MES),
            f("definition", "Definition", maxChars = 32_000, hint = "Hard limit: 32,000 characters. This is where the character's actual behaviour is described.", source = CardFields.MES_EXAMPLE),
        ),
        spec = CharacterCard.Spec.V2,
        format = CardFormat.CLIPBOARD,
        note = "No card import. Fields are shown with Character.AI's real names and caps so " +
            "you can copy each one straight into its editor.",
        copyOnly = true,
        lossy = true,
        builtIn = true,
    )

    private val janitorAi = PlatformSchema(
        id = "janitorai",
        label = "JanitorAI",
        fields = listOf(
            f(CardFields.NAME, "Name", FieldType.SHORT, required = true, source = CardFields.NAME),
            f(CardFields.DESCRIPTION, "Persona", hint = "Janitor calls the description field the persona.", source = CardFields.DESCRIPTION),
            f(CardFields.PERSONALITY, "Personality", source = CardFields.PERSONALITY),
            f(CardFields.SCENARIO, "Scenario", source = CardFields.SCENARIO),
            f(CardFields.FIRST_MES, "First message", source = CardFields.FIRST_MES),
            f(CardFields.MES_EXAMPLE, "Example dialogue", source = CardFields.MES_EXAMPLE),
            f(CardFields.TAGS, "Tags", FieldType.TAGS, source = CardFields.TAGS),
        ),
        spec = CharacterCard.Spec.V2,
        format = CardFormat.CLIPBOARD,
        note = "No public card import. Copy each field into Janitor's editor.",
        copyOnly = true,
        builtIn = true,
    )

    private val foreverse = PlatformSchema(
        id = "foreverse",
        label = "Foreverse / ST Android apps",
        fields = v2Common() + listOf(
            f(CardFields.NICKNAME, "Nickname", FieldType.SHORT, source = CardFields.NICKNAME),
        ),
        spec = CharacterCard.Spec.V3,
        format = CardFormat.PNG,
        note = "Reads iTXt and zTXt as well as tEXt, so V3 PNGs import cleanly.",
        builtIn = true,
    )

    /**
     * FictionLab.
     *
     * Its own card format rather than a SillyTavern import, and its naming is
     * inconsistent — the card's `content` is what most people would call the
     * description, and `personality_summary` is a one-line hook rather than a
     * full personality. Fields are named as the site names them so a paste or a
     * hand-filled export actually lands in the right box.
     */
    private val fictionLab = PlatformSchema(
        id = "fictionlab",
        label = "FictionLab",
        fields = listOf(
            f(CardFields.NAME, "Name", FieldType.SHORT, required = true, hint = "The persona's display name.", source = CardFields.NAME),
            f("content", "Content", hint = "FictionLab's main body field — the character description, expanded.", source = CardFields.DESCRIPTION),
            f("personality_summary", "Personality summary", FieldType.SHORT, maxChars = 250, hint = "A short hook, not the full personality. Keep it to a sentence or two.", source = CardFields.PERSONALITY),
            f("first_message", "First message", hint = "The opening greeting, already in character.", source = CardFields.FIRST_MES),
            f("example_dialogue", "Example dialogue", hint = "Sample exchanges showing the voice.", source = CardFields.MES_EXAMPLE),
            f("creator_notes", "Creator notes", FieldType.LONG, hint = "Notes for other users, shown publicly.", source = CardFields.CREATOR_NOTES),
            f("creator", "Creator", FieldType.SHORT, source = CardFields.CREATOR),
            f("tags", "Tags", FieldType.TAGS, hint = "Comma-separated discovery tags.", source = CardFields.TAGS),
        ),
        spec = CharacterCard.Spec.V2,
        format = CardFormat.JSON,
        note = "FictionLab has its own card shape and no SillyTavern import. Ember " +
            "writes its real field names, with personality_summary kept as the short " +
            "hook it expects rather than the full personality.",
        lossy = true,
        builtIn = true,
    )

    val personaForge = PlatformSchema(
        id = "personaforge",
        label = "PersonaForge",
        fields = listOf(
            f("name", "Scenario Title", FieldType.SHORT, required = true, source = CardFields.NAME, hint = "Title of the scenario as PersonaForge displays it."),
            f("mode", "Mode (ROLEPLAY/SCENARIO/GAME/NARRATIVE)", FieldType.SHORT, required = true, hint = "Game mode: ROLEPLAY, SCENARIO, GAME, or NARRATIVE."),
            f("character_name", "Character Name", FieldType.SHORT, required = true, source = CardFields.NAME, hint = "Name of the main persona or narrator."),
            f("personality", "Personality & Traits", FieldType.LONG, required = true, source = CardFields.PERSONALITY, hint = "Traits, psychological depth, behavior under pressure."),
            f("backstory", "Backstory", FieldType.LONG, hint = "Character background and setting lore."),
            f("appearance", "Appearance", FieldType.LONG, hint = "Visual aesthetics, build, distinctive physical traits."),
            f("clothing", "Clothing / Environment", FieldType.LONG, hint = "Outfit, style, or immediate setting ambiance."),
            f("storyTone", "Story Tone", FieldType.SHORT, hint = "e.g. Dramatic, Romantic, Dark, Noir, Thriller."),
            f("relationship", "Relationship", FieldType.SHORT, hint = "Starting dynamic with the player (e.g. Strangers, Rivals, Reluctant Allies)."),
            f("worldAtmosphere", "World Atmosphere", FieldType.LONG, hint = "World mood, sensory texture, weather, and atmosphere."),
            f("keyLocations", "Key Locations", FieldType.LONG, hint = "3-4 prominent places in the scenario."),
            f("scenarioConflict", "Scenario Conflict", FieldType.LONG, hint = "Central conflict, tension, or dilemma."),
            f("scenarioStakes", "Scenario Stakes", FieldType.LONG, hint = "What is at risk / consequences of failure."),
            f("timePeriod", "Time Period", FieldType.SHORT, hint = "Era, historical period, or future setting."),
            f("incitingIncident", "Inciting Incident", FieldType.LONG, hint = "The event that kicks off the interaction immediately."),
            f("characterFlaws", "Character Flaws", FieldType.LONG, hint = "Flaws, weaknesses, and emotional cracks."),
            f("secretMotive", "Secret Motive", FieldType.LONG, hint = "Hidden agenda or concealed truth."),
            f("speechPattern", "Speech Pattern", FieldType.LONG, hint = "Voice style, cadence, vocabulary, and dialogue quirks."),
            f("quirks", "Quirks", FieldType.LONG, hint = "Idiosyncrasies and distinctive mannerisms."),
            f("greetingMessage", "First Message / Greeting", FieldType.LONG, required = true, source = CardFields.FIRST_MES, hint = "Opening in-character message or starting scene."),
            f("scenarioInstructions", "Scenario Instructions", FieldType.LONG, source = CardFields.SYSTEM_PROMPT, hint = "Director instructions for the AI on narrative style and roleplay boundaries."),
            f("suggestedPlayerName", "Player Character Name", FieldType.SHORT, hint = "Default name for the player's character."),
            f("suggestedPlayerDescription", "Player Description", FieldType.LONG, hint = "Default background/role for the player."),
            f("tags", "Tags", FieldType.TAGS, source = CardFields.TAGS, hint = "Comma-separated discovery tags."),
        ),
        spec = CharacterCard.Spec.V2,
        format = CardFormat.JSON,
        note = "PersonaForge native story transfer format (schemaVersion: 1). Imports directly into PersonaForge Android & Web.",
        builtIn = true,
    )

    val BUILT_IN: List<PlatformSchema> = listOf(
        personaForge, sillyTavern, risuAi, agnai, foreverse, chub, characterAi, janitorAi, fictionLab,
    )

    fun byId(id: String): PlatformSchema =
        BUILT_IN.firstOrNull { it.id == id } ?: personaForge

    /**
     * Builds a schema from a fetched form.
     *
     * Every field starts unrequired and uncapped: Ember cannot know a site's
     * validation rules from its markup, and guessing would make the counter
     * lie. The user tightens it after the fact.
     */
    fun fromForm(url: String, fields: List<FormField>): PlatformSchema {
        val label = url.substringAfter("//").substringBefore('/').ifBlank { "Custom platform" }
        return PlatformSchema(
            id = "custom-" + label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "form" },
            label = label,
            fields = fields.map { form ->
                SchemaField(
                    key = form.name,
                    label = form.label.ifBlank { form.name.replace('_', ' ') },
                    type = when {
                        form.type.equals("select", true) -> FieldType.SHORT
                        form.type.equals("textarea", true) -> FieldType.LONG
                        form.maxChars > 0 && form.maxChars <= 120 -> FieldType.SHORT
                        else -> FieldType.LONG
                    },
                    maxChars = form.maxChars,
                    hint = form.example?.let { "Example from the form: $it" } ?: "",
                    source = CardFields.ALL.firstOrNull { form.name.contains(it.first, true) }?.first ?: "",
                    custom = true,
                )
            },
            format = CardFormat.JSON,
            note = "Imported from $url. Adjust the field list if the site enforces caps " +
                "it doesn't publish in its markup.",
        )
    }

    /**
     * The draft's values flattened into the JSON object the platform expects.
     *
     * Array-typed fields (tags, alternate greetings) are written as real JSON
     * arrays rather than the comma-joined string the user typed, because that is
     * the shape every one of these platforms reads back.
     */
    fun toJson(schema: PlatformSchema, values: Map<String, String>): JSONObject {
        if (schema.id == "personaforge") {
            return PersonaForgeExport.toStoryExportJson(PersonaForgeExport.fromDraftValues(values))
        }
        val out = JSONObject()
        schema.fields.forEach { field ->
            val raw = values[field.key] ?: return@forEach
            if (raw.isBlank()) return@forEach
            when (field.type) {
                FieldType.TAGS -> out.put(field.key, JSONArray(raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }))
                FieldType.ALTERNATES -> out.put(field.key, JSONArray(raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }))
                else -> out.put(field.key, raw)
            }
        }
        return out
    }
}
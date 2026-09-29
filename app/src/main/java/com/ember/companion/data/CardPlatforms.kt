package com.ember.companion.data

import org.json.JSONArray
import org.json.JSONObject

/** Card source fields a platform or a custom mapping can draw on. */
object CardFields {
    const val NAME = "name"
    const val DESCRIPTION = "description"
    const val PERSONALITY = "personality"
    const val SCENARIO = "scenario"
    const val FIRST_MES = "first_mes"
    const val MES_EXAMPLE = "mes_example"
    const val CREATOR_NOTES = "creator_notes"
    const val SYSTEM_PROMPT = "system_prompt"
    const val POST_HISTORY = "post_history_instructions"
    const val ALTERNATE_GREETINGS = "alternate_greetings"
    const val TAGS = "tags"
    const val CREATOR = "creator"
    const val CHARACTER_VERSION = "character_version"
    const val NICKNAME = "nickname"

    val ALL = listOf(
        NAME to "Name",
        DESCRIPTION to "Description",
        PERSONALITY to "Personality",
        SCENARIO to "Scenario",
        FIRST_MES to "First message",
        MES_EXAMPLE to "Example dialogue",
        CREATOR_NOTES to "Creator notes",
        SYSTEM_PROMPT to "System prompt",
        POST_HISTORY to "Post-history instructions",
        ALTERNATE_GREETINGS to "Alternate greetings",
        TAGS to "Tags",
        CREATOR to "Creator",
        CHARACTER_VERSION to "Character version",
        NICKNAME to "Nickname",
    )

    fun label(source: String): String = ALL.firstOrNull { it.first == source }?.second ?: source

    fun read(card: CharacterCard.Card, source: String): String = when (source) {
        NAME -> card.name
        DESCRIPTION -> card.description
        PERSONALITY -> card.personality
        SCENARIO -> card.scenario
        FIRST_MES -> card.firstMessage
        MES_EXAMPLE -> card.exampleDialogue
        CREATOR_NOTES -> card.creatorNotes
        SYSTEM_PROMPT -> card.systemPrompt
        POST_HISTORY -> card.postHistoryInstructions
        ALTERNATE_GREETINGS -> card.alternateGreetings.joinToString("\n\n")
        TAGS -> card.tags.joinToString(", ")
        CREATOR -> card.creator
        CHARACTER_VERSION -> card.characterVersion
        NICKNAME -> card.nickname.ifBlank { card.name }
        else -> ""
    }
}

enum class CardFormat(val label: String) {
    /** A PNG carrying the card, as the ecosystem expects. */
    PNG("PNG image"),

    /** A bare JSON file. */
    JSON("JSON file"),

    /**
     * No file: the fields are shown one at a time for copying. Used for platforms
     * that publish no import endpoint, so handing them a card file would be a lie.
     */
    CLIPBOARD("Copy fields by hand"),
}

/**
 * Per-platform export behaviour.
 *
 * Several widely used platforms have no character-card import at all, and one of
 * them inverts two of the core fields. Encoding that here rather than in the UI
 * is the difference between an export that works and one that silently loses the
 * character body.
 */
object CardPlatforms {

    data class Platform(
        val id: String,
        val label: String,
        val spec: CharacterCard.Spec,
        val format: CardFormat,
        val note: String,
        /** True when the platform cannot read a card back, so a file export is pointless. */
        val copyOnly: Boolean = false,
        /** True when the mapping drops fields the card contains. */
        val lossy: Boolean = false,
        /** Per-field caps the target enforces, shown in the copy sheet. */
        val limits: Map<String, Int> = emptyMap(),
        val adapt: (CharacterCard.Card) -> CharacterCard.Card = { it },
    )

    /**
     * Chub reads `description` as creator notes and `personality` as the character
     * body — the reverse of the spec. The spec's own `personality` has no field
     * there, so it is preserved under Chub's deprecated `tavern_personality`
     * alias rather than dropped, and the notes are left where Chub expects them.
     */
    private fun chub(card: CharacterCard.Card): CharacterCard.Card = card.copy(
        description = card.creatorNotes,
        personality = card.description,
        extensions = card.extensions + ("tavern_personality" to card.personality),
    )

    /**
     * Agnai serialises the persona into the description and leaves `personality`
     * empty, which is the opposite of the spec. Matching it avoids a duplicated
     * body when the card is re-imported.
     */
    private fun agnai(card: CharacterCard.Card): CharacterCard.Card =
        card.copy(personality = "")

    /** Character.AI has a freeform `definition` and no card import. */
    private fun characterAi(card: CharacterCard.Card): CharacterCard.Card = card.copy(
        personality = card.personality.take(50),
        description = card.description.take(500),
        exampleDialogue = card.exampleDialogue.take(32_000),
        scenario = "",
    )

    val ALL: List<Platform> = listOf(
        Platform(
            id = "sillytavern",
            label = "SillyTavern",
            spec = CharacterCard.Spec.V2,
            format = CardFormat.PNG,
            note = "Reads V2 straight through. Ember writes both PNG chunks, so the " +
                "same file also opens in RisuAI and Foreverse.",
        ),
        Platform(
            id = "risuai",
            label = "RisuAI",
            spec = CharacterCard.Spec.V3,
            format = CardFormat.PNG,
            note = "V3, including group-only greetings and the nickname field.",
        ),
        Platform(
            id = "agnai",
            label = "Agnai",
            spec = CharacterCard.Spec.V2,
            format = CardFormat.PNG,
            note = "Personality is sent empty on purpose: Agnai keeps the persona " +
                "inside the description and ignores a separate personality field.",
            adapt = ::agnai,
        ),
        Platform(
            id = "foreverse",
            label = "Foreverse / ST Android apps",
            spec = CharacterCard.Spec.V3,
            format = CardFormat.PNG,
            note = "Reads iTXt and zTXt as well as tEXt, so V3 PNGs import cleanly.",
        ),
        Platform(
            id = "chub",
            label = "Chub.ai",
            spec = CharacterCard.Spec.V2,
            format = CardFormat.JSON,
            note = "Chub swaps description and personality. This export matches " +
                "Chub's field names, and keeps the spec personality under the " +
                "deprecated tavern_personality alias.",
            lossy = true,
            adapt = ::chub,
        ),
        Platform(
            id = "characterai",
            label = "Character.AI",
            spec = CharacterCard.Spec.V2,
            format = CardFormat.CLIPBOARD,
            note = "Character.AI has no card import. Fields are shown one at a time " +
                "with their real limits: tagline 50, short description 500, " +
                "definition 32,000.",
            copyOnly = true,
            lossy = true,
            limits = mapOf(
                CardFields.PERSONALITY to 50,
                CardFields.DESCRIPTION to 500,
                CardFields.MES_EXAMPLE to 32_000,
            ),
            adapt = ::characterAi,
        ),
        Platform(
            id = "janitorai",
            label = "JanitorAI",
            spec = CharacterCard.Spec.V2,
            format = CardFormat.CLIPBOARD,
            note = "No public card import. Janitor reads description, personality, " +
                "scenario, first message and example dialogs.",
            copyOnly = true,
        ),
    )

    fun byId(id: String): Platform = ALL.firstOrNull { it.id == id } ?: ALL.first()

    /**
     * A user-defined mapping: arbitrary target field names, each pointed at one of
     * the card's source fields. This is the escape hatch for platforms not listed
     * above, including ones with a schema Ember has never heard of.
     */
    data class CustomRow(val target: String, val source: String)

    data class Custom(
        val spec: CharacterCard.Spec = CharacterCard.Spec.V2,
        val format: CardFormat = CardFormat.JSON,
        val rows: List<CustomRow> = listOf(
            CustomRow("name", CardFields.NAME),
            CustomRow("description", CardFields.DESCRIPTION),
            CustomRow("personality", CardFields.PERSONALITY),
            CustomRow("scenario", CardFields.SCENARIO),
            CustomRow("first_mes", CardFields.FIRST_MES),
            CustomRow("mes_example", CardFields.MES_EXAMPLE),
        ),
    ) {
        /** Flattens the rows into a single JSON object using the user's own names. */
        fun toJson(card: CharacterCard.Card): JSONObject {
            val adapted = card
            val out = JSONObject()
            rows.filter { it.target.isNotBlank() }.forEach { row ->
                val value = CardFields.read(adapted, row.source)
                if (row.source == CardFields.ALTERNATE_GREETINGS) {
                    out.put(row.target, JSONArray(adapted.alternateGreetings))
                } else if (row.source == CardFields.TAGS) {
                    out.put(row.target, JSONArray(adapted.tags))
                } else {
                    out.put(row.target, value)
                }
            }
            return out
        }

        fun toJsonText(card: CharacterCard.Card): String = toJson(card).toString(2)
    }
}

package com.ember.companion.data

import kotlin.random.Random

object Generator {

    private val titleTemplates = listOf(
        "The {placeShort}",
        "One Hour at the {placeShort}",
        "{role} After Hours",
        "The {timeShort}",
        "Nobody Texting Back",
        "A Rule About {noun}",
        "Two {plural} and a Borrowed Room",
        "The Long Way to {placeShort}",
        "{timeShort}, Still Awake",
        "An Arrangement, Revised",
    )

    /**
     * Builds a brief as addressable slots.
     *
     * Three things changed from the old version, all of them about making the
     * result steerable rather than merely re-rollable:
     *  - every drawn value is a named [Part] carrying the bank it came from, so a
     *    single dimension can be regenerated without discarding the rest;
     *  - the dials index ordered banks instead of sampling, so a dial setting is a
     *    reproducible choice;
     *  - the premise can FORCE a bank value when it names one, which is what
     *    makes the seed a constraint on offline generation and not just text
     *    handed to the AI afterwards.
     */
    fun brief(
        rng: Random = Random.Default,
        custom: Banks.Custom = Banks.Custom(),
        dials: Dials = Dials(),
        premise: String = "",
        taste: Banks.Taste = Banks.Taste(),
    ): Brief {
        val lower = premise.lowercase()

        /**
         * A bank value the premise explicitly names, if any.
         *
         * Matching is on significant words rather than a prefix, because every
         * value in these banks starts with an article ("a hotel bar"), so any
         * prefix test fails to distinguish anything. The candidate with the most
         * matched words wins, and ties go to the most specific (fewest total
         * words), so "a hotel bar" beats "a hotel bar at closing time".
         */
        fun forced(id: String, defaults: List<String>): String? =
            Banks.forcedBy(premise, custom.forBank(id, defaults))

        fun pick(id: String, defaults: List<String>): String {
            forced(id, defaults)?.let { return it }
            val pool = custom.forBank(id, defaults)
            val applied = taste.apply(id, pool)
            // A pin that no longer exists in the pool must not empty it.
            val usable = applied.ifEmpty {
                taste.apply(id, defaults).ifEmpty { defaults }
            }
            return usable.random(rng)
        }

        val register = Banks.registers[dials.explicitness]
        val pacing = Banks.pacingNotes[dials.pace]
        val powerLine = Dials.POWER[dials.power]
        val pov = Dials.POV[dials.pov]
        val opener = pick("openers", Banks.openingsByRegister[dials.explicitness])

        val roleA = pick("roles", Banks.roles)
        val roleB = pick("roles", Banks.roles)
        val place = pick("places", Banks.places)
        val time = pick("times", Banks.timesOfDay)
        val wthr = pick("weather", Banks.weather)
        val atmosphere = pick("atmosphere", Banks.atmospheres)
        val placeShort = place.removePrefix("a ").removePrefix("an ")
            .substringBefore(" that").substringBefore(" because").take(34)

        val nameA = name(rng, custom, taste)
        val nameB = name(rng, custom, taste)
        val ageA = pick("ages", Banks.ages)
        val ageB = pick("ages", Banks.ages)

        val titleNoun = pick("titleNoun", Banks.roles)
            .substringBefore(" who ").substringBefore(" that ").take(28)
        val titlePlural = pick("titlePlural", Banks.roles)
            .substringBefore(" who ").substringBefore(" that ").trim()

        fun castBlock(
            tag: String,
            nameValue: String,
            age: String,
            role: String,
        ): List<Part> = listOf(
            Part("${tag}name", "Name", nameValue, bank = "names"),
            Part("${tag}age", "Age", age.replaceFirstChar { it.uppercase() }, bank = "ages"),
            Part("${tag}role", "Role", role, bank = "roles"),
            Part("${tag}trait", "", pick("traits", Banks.traits), bank = "traits", prefix = "  · "),
            Part("${tag}want", "", pick("wants", Banks.wants), bank = "wants", prefix = "  · wants: "),
            Part("${tag}fear", "", pick("fears", Banks.fears), bank = "fears", prefix = "  · fears: "),
            Part("${tag}secret", "", pick("secrets", Banks.secrets), bank = "secrets", prefix = "  · secret: "),
            Part("${tag}flaw", "", pick("flaws", Banks.flaws), bank = "flaws", prefix = "  · flaw: "),
        )

        val slots = buildList {
            if (premise.isNotBlank()) {
                add(BriefSlot("premise", "Premise", listOf(Part("premise", "", premise, hidden = true))))
            }
            // Hidden: the title is derived from these, so they must be rerollable
            // alongside the visible parts or the title goes stale.
            add(
                BriefSlot(
                    "meta", "Title parts",
                    listOf(
                        Part("titleNoun", "", titleNoun, bank = "titleNoun", hidden = true),
                        Part("titlePlural", "", titlePlural, bank = "titlePlural", hidden = true),
                    ),
                ),
            )
            add(
                BriefSlot(
                    "cast",
                    "Cast",
                    castBlock("a", nameA, ageA, roleA) +
                        listOf(Part("gap", "", "")) +
                        castBlock("b", nameB, ageB, roleB),
                ),
            )
            add(
                BriefSlot(
                    "frame",
                    "Frame",
                    listOf(
                        Part("framing", "Framing", pick("framings", Banks.framings), bank = "framings"),
                        Part("power", "Power", powerLine, bank = "powerBalances"),
                        Part("tension", "Tension", pick("tensions", Banks.tensions), bank = "tensions"),
                        Part("reveal", "Reveals to", pick("confidences", Banks.confidences), bank = "confidences"),
                        Part("register", "Register", register, bank = "registers"),
                        Part("pacing", "Pacing", pacing, bank = "pacingNotes"),
                        Part("pov", "POV", pov, bank = "pov"),
                    ),
                ),
            )
            add(
                BriefSlot(
                    "setting",
                    "Setting",
                    listOf(
                        Part("place", "Place", place, bank = "places"),
                        Part("time", "Time", time, bank = "times"),
                        Part("weather", "Weather", wthr, bank = "weather"),
                        Part("air", "Air", atmosphere, bank = "atmosphere"),
                        Part("texture", "Texture", pick("sensory", Banks.sensory), bank = "sensory"),
                    ),
                ),
            )
            add(BriefSlot("open", "Open", listOf(Part("open", "", opener, bank = "openers"))))
            add(
                BriefSlot(
                    "beats",
                    "Beats",
                    listOf(
                        Part("beat1", "1. Escalates", pick("escalations", Banks.escalations), bank = "escalations"),
                        Part("beat2", "2. Complication", pick("complications", Banks.complications), bank = "complications"),
                        Part("beat3", "3. Turn", pick("turns", Banks.turns), bank = "turns"),
                    ),
                ),
            )
            add(
                BriefSlot(
                    "twist",
                    "Optional twist",
                    listOf(Part("twist", "", pick("twists", Banks.twists), bank = "twists")),
                ),
            )
            add(
                BriefSlot(
                    "close",
                    "Close",
                    listOf(
                        Part("close", "", pick("closers", Banks.closers), bank = "closers"),
                        Part("closeNote", "", Banks.closeNotes[dials.explicitness], bank = "closeNotes"),
                    ),
                ),
            )
        }

        val title = title(rng, custom, placeShort, time, roleA, titleNoun, titlePlural)
        return Brief(title = title, slots = slots, premise = premise, dials = dials)
    }

    /** A single character sketch, for when only one seat needs filling. */
    fun character(
        rng: Random = Random.Default,
        custom: Banks.Custom = Banks.Custom(),
        taste: Banks.Taste = Banks.Taste(),
    ): Brief {
        fun pick(id: String, defaults: List<String>): String {
            val applied = taste.apply(id, custom.forBank(id, defaults))
            val usable = applied.ifEmpty {
                taste.apply(id, defaults).ifEmpty { defaults }
            }
            return usable.random(rng)
        }

        val nameValue = name(rng, custom, taste)
        val age = pick("ages", Banks.ages)
        val role = pick("roles", Banks.roles)
        val parts = listOf(
            Part("name", "Name", nameValue, bank = "names"),
            Part("age", "Age", age.replaceFirstChar { it.uppercase() }, bank = "ages"),
            Part("role", "Role", role, bank = "roles"),
            Part("trait", "", pick("traits", Banks.traits), bank = "traits", prefix = "  · "),
            Part("want", "", pick("wants", Banks.wants), bank = "wants", prefix = "  · wants: "),
            Part("fear", "", pick("fears", Banks.fears), bank = "fears", prefix = "  · fears: "),
            Part("secret", "", pick("secrets", Banks.secrets), bank = "secrets", prefix = "  · secret: "),
            Part("flaw", "", pick("flaws", Banks.flaws), bank = "flaws", prefix = "  · flaw: "),
            Part("voice", "", pick("sensory", Banks.sensory), bank = "sensory", prefix = "  · voice: "),
        )
        return Brief(
            title = nameValue,
            slots = listOf(BriefSlot("character", "Character", parts)),
        )
    }

    /** A single tension/beat prompt, for when the setting is already decided. */
    fun complication(
        rng: Random = Random.Default,
        custom: Banks.Custom = Banks.Custom(),
        taste: Banks.Taste = Banks.Taste(),
    ): Brief {
        val pick = listOf(
            "complications" to Banks.complications,
            "escalations" to Banks.escalations,
            "turns" to Banks.turns,
            "twists" to Banks.twists,
            "tensions" to Banks.tensions,
            "openers" to Banks.openers,
            "closers" to Banks.closers,
        ).random(rng)
        val applied = taste.apply(pick.first, custom.forBank(pick.first, pick.second))
        val usable = applied.ifEmpty {
            taste.apply(pick.first, pick.second).ifEmpty { pick.second }
        }
        val value = usable.random(rng)
        return Brief(
            title = "Beat",
            slots = listOf(
                BriefSlot(
                    "beat",
                    pick.first.replaceFirstChar { it.uppercase() },
                    listOf(Part("beat", "", value, bank = pick.first)),
                ),
            ),
        )
    }

    /** Public so the reroll path can re-draw a single name the same way. */
    fun nameFor(
        rng: Random,
        custom: Banks.Custom = Banks.Custom(),
        taste: Banks.Taste = Banks.Taste(),
    ): String {
        val first = FIRST_NAMES.random(rng)
        val last = LAST_NAMES.random(rng)
        val applied = taste.apply("nameStyles", custom.forBank("nameStyles", Banks.nameStyles))
        val style = applied.ifEmpty { Banks.nameStyles }.random(rng)
        return when {
            style.contains("single name") -> first
            style.contains("nickname") -> "$first \"$last\""
            style.contains("handle") -> "@$first$last"
            style.contains("title") -> "Dr. $last"
            else -> "$first $last"
        }
    }

    private fun name(rng: Random, custom: Banks.Custom, taste: Banks.Taste = Banks.Taste()): String =
        nameFor(rng, custom, taste)

    private fun title(
        rng: Random,
        custom: Banks.Custom,
        placeShort: String,
        time: String,
        role: String,
        noun: String,
        plural: String,
    ): String {
        val timeShort = time.substringBefore(" with").take(28)
        return titleTemplates
            .random(rng)
            .replace("{placeShort}", placeShort.ifBlank { "the bar" })
            .replace("{timeShort}", timeShort.ifBlank { "Late" })
            .replace("{role}", role.substringBefore(" who ").substringBefore(" that ").trim())
            .replace("{noun}", noun.ifBlank { "Room" })
            .replace("{plural}", plural.ifBlank { "Strangers" })
            .replaceFirstChar { it.uppercase() }
    }

    /**
     * Rebuilds a title from the brief's current parts. Called after a reroll so
     * that changing the place or time does not leave a stale title behind.
     */
    fun rebuildTitle(brief: Brief, rng: Random = Random.Default): String = title(
        rng = rng,
        custom = Banks.Custom(),
        placeShort = brief.allParts().firstOrNull { it.key == "place" }?.value.orEmpty()
            .removePrefix("a ").removePrefix("an ")
            .substringBefore(" that").substringBefore(" because").take(34),
        time = brief.allParts().firstOrNull { it.key == "time" }?.value.orEmpty(),
        role = brief.allParts().firstOrNull { it.key == "arole" }?.value.orEmpty(),
        noun = brief.allParts().firstOrNull { it.key == "titleNoun" }?.value.orEmpty(),
        plural = brief.allParts().firstOrNull { it.key == "titlePlural" }?.value.orEmpty(),
    )

    private val FIRST_NAMES = listOf(
        "Maren", "Osei", "Lior", "Tamsin", "Dashiell", "Nura", "Casimir", "Iva",
        "Rune", "Sabine", "Emeka", "Delphine", "Ari", "Bex", "Cormac", "Yuki",
        "Halvor", "Priya", "Sorrel", "Viggo", "Wren", "Zaid", "Ines", "Mattias",
        "Noor", "Ellery", "Tobias", "Sunniva", "Kazimir", "Rosa", "Ansel", "Freya",
    )

    private val LAST_NAMES = listOf(
        "Aldiss", "Barrow", "Castellan", "Duarte", "Ekstrom", "Fenn", "Grieve",
        "Hallow", "Iversen", "Kestrel", "Larkin", "Mbeki", "Norgaard", "Oyelaran",
        "Pemberton", "Quill", "Rasmussen", "Sandoval", "Thorne", "Ustinov", "Varga",
        "Wexler", "Yardley", "Ashgrove", "Bellweather", "Cortland", "Dunning",
    )
}

val Dials.summary: String
    get() {
        val exp = Dials.EXPLICITNESS.getOrElse(explicitness.coerceIn(0, 2)) { "charged" }
        val pc = Dials.PACE.getOrElse(pace.coerceIn(0, 2)) { "steady build" }
        val pw = Dials.POWER.getOrElse(power.coerceIn(0, 2)) { "neither leads; it turns on who blinks first" }
        val pv = Dials.POV.getOrElse(pov.coerceIn(0, 2)) { "third person, past tense" }
        return "$exp · $pc · $pw · $pv"
    }

val Dials.shortSummary: String
    get() {
        val exp = Dials.EXPLICITNESS.getOrElse(explicitness.coerceIn(0, 2)) { "charged" }
        val pc = Dials.PACE.getOrElse(pace.coerceIn(0, 2)) { "steady build" }
        return "$exp · $pc"
    }

object BriefMarkdownParser {

    private val PREFIX_CLEANUP_REGEX = Regex(
        "^(?:[·\\-*•]\\s*)?(?:(?:wants?|fears?|secret|flaw|traits?|voice|place|time|weather|air|texture|framing|power|tension|reveals?\\s*(?:to)?|register|pacing|pov|\\d+\\.\\s*(?:escalates?|complications?|turns?)?|\\d+\\.)\\s*[:\\-]?\\s*)",
        RegexOption.IGNORE_CASE,
    )

    fun cleanValue(raw: String, expectedPrefixOrLabel: String = ""): String {
        var cleaned = raw.trim()
        val labelPattern = if (expectedPrefixOrLabel.isNotBlank()) {
            val labelToken = expectedPrefixOrLabel.trim().removeSuffix(":").trim()
            if (labelToken.isNotBlank()) {
                Regex("^${Regex.escape(labelToken)}\\s*[:\\-]?\\s*", RegexOption.IGNORE_CASE)
            } else null
        } else null

        var changed = true
        var iterations = 0
        while (changed && iterations < 10) {
            val before = cleaned
            while (cleaned.isNotEmpty() && (cleaned[0] == '·' || cleaned[0] == '-' || cleaned[0] == '*' || cleaned[0] == '•')) {
                cleaned = cleaned.substring(1).trim()
            }
            if (labelPattern != null) {
                cleaned = cleaned.replace(labelPattern, "").trim()
            }
            cleaned = cleaned.replace(PREFIX_CLEANUP_REGEX, "").trim()
            changed = (cleaned != before)
            iterations++
        }
        return cleaned
    }

    private fun truncateTitle(text: String, targetLength: Int = 48): String {
        val trimmed = text.trim().removePrefix("#").trim()
        if (trimmed.length <= targetLength) return trimmed
        val nextSpace = trimmed.indexOfAny(charArrayOf(' ', '\t', '\n'), startIndex = targetLength)
        return if (nextSpace != -1) {
            trimmed.substring(0, nextSpace).trim()
        } else {
            trimmed.take(80).trim()
        }
    }

    fun parse(markdown: String, defaultPremise: String, dials: Dials): Brief {
        val cleanMd = markdown.trim()
            .removePrefix("```markdown").removePrefix("```")
            .removeSuffix("```").trim()

        val lines = cleanMd.lines()
        var title = ""
        val sections = mutableMapOf<String, MutableList<String>>()
        var currentSection = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("# ") && title.isEmpty()) {
                title = trimmed.removePrefix("# ").trim()
                continue
            }
            if (trimmed.startsWith("## ")) {
                currentSection = trimmed.removePrefix("## ").trim().lowercase()
                sections.putIfAbsent(currentSection, mutableListOf())
                continue
            }
            if (currentSection.isNotEmpty() && trimmed.isNotEmpty()) {
                sections[currentSection]?.add(trimmed)
            }
        }

        if (title.isBlank()) {
            title = lines.firstOrNull { it.trim().startsWith("#") }?.trim()?.removePrefix("#")?.trim()
                ?: "Generated Scenario"
        }

        if (sections.isEmpty()) {
            val firstLine = lines.firstOrNull { it.isNotBlank() }?.trim() ?: "AI Scenario"
            val fallbackTitle = if (title.isNotBlank() && title != "Generated Scenario") {
                title
            } else {
                truncateTitle(firstLine, 48)
            }
            return Brief(
                title = fallbackTitle,
                slots = listOf(
                    BriefSlot("aiScenario", "Scenario", listOf(Part("aiBody", "", cleanMd))),
                ),
                premise = defaultPremise,
                dials = dials,
            )
        }

        val slots = mutableListOf<BriefSlot>()
        if (defaultPremise.isNotBlank()) {
            slots.add(BriefSlot("premise", "Premise", listOf(Part("premise", "", defaultPremise, hidden = true))))
        }

        val titleNoun = title.split(" ").lastOrNull()?.take(28) ?: "Story"
        slots.add(
            BriefSlot(
                "meta", "Title parts",
                listOf(
                    Part("titleNoun", "", titleNoun, bank = "titleNoun", hidden = true),
                    Part("titlePlural", "", "${titleNoun}s", bank = "titlePlural", hidden = true),
                ),
            ),
        )

        val settingLines = sections["setting"].orEmpty()
        val settingMap = parseKeyValueLines(settingLines)
        slots.add(
            BriefSlot(
                "setting", "Setting",
                listOf(
                    Part("place", "Place", settingMap["place"] ?: "An intimate setting", bank = "places"),
                    Part("time", "Time", settingMap["time"] ?: "Late evening", bank = "times"),
                    Part("weather", "Weather", settingMap["weather"] ?: "Quiet outside", bank = "weather"),
                    Part("air", "Air", settingMap["air"] ?: "Heavy with anticipation", bank = "atmosphere"),
                    Part("texture", "Texture", settingMap["texture"] ?: "Subtle ambient sounds", bank = "sensory"),
                ),
            ),
        )

        val castLines = sections["cast"].orEmpty()
        slots.add(parseCastSlot(castLines))

        val frameLines = sections["frame"].orEmpty()
        val frameMap = parseKeyValueLines(frameLines)
        slots.add(
            BriefSlot(
                "frame", "Frame",
                listOf(
                    Part("framing", "Framing", frameMap["framing"] ?: "An unexpected encounter", bank = "framings"),
                    Part("power", "Power", frameMap["power"] ?: Dials.POWER[dials.power], bank = "powerBalances"),
                    Part("tension", "Tension", frameMap["tension"] ?: "Unspoken feelings", bank = "tensions"),
                    Part("reveal", "Reveals to", frameMap["reveals to"] ?: frameMap["reveal"] ?: "A secret kept too long", bank = "confidences"),
                    Part("register", "Register", frameMap["register"] ?: Banks.registers[dials.explicitness], bank = "registers"),
                    Part("pacing", "Pacing", frameMap["pacing"] ?: Banks.pacingNotes[dials.pace], bank = "pacingNotes"),
                    Part("pov", "POV", frameMap["pov"] ?: Dials.POV[dials.pov], bank = "pov"),
                ),
            ),
        )

        val openText = sections["open"].orEmpty().joinToString("\n").ifBlank { "The scene begins in quiet focus." }
        slots.add(BriefSlot("open", "Open", listOf(Part("open", "", openText, bank = "openers"))))

        val beatsLines = sections["beats"].orEmpty()
        slots.add(parseBeatsSlot(beatsLines))

        val twistLines = sections["optional twist"] ?: sections["twist"]
        if (twistLines != null) {
            val twistText = twistLines.joinToString("\n").trim()
            if (twistText.isNotBlank()) {
                slots.add(BriefSlot("twist", "Optional twist", listOf(Part("twist", "", twistText, bank = "twists"))))
            }
        }

        val closeLines = sections["close"].orEmpty()
        val closeText = closeLines.joinToString("\n").ifBlank { "A quiet realization settles." }
        slots.add(
            BriefSlot(
                "close", "Close",
                listOf(
                    Part("close", "", closeText, bank = "closers"),
                    Part("closeNote", "", Banks.closeNotes[dials.explicitness], bank = "closeNotes"),
                ),
            ),
        )

        return Brief(title = title, slots = slots, premise = defaultPremise, dials = dials)
    }

    fun updateSlotFromAi(brief: Brief, slotKey: String, aiOutput: String): Brief {
        val slot = brief.slot(slotKey) ?: return brief
        if (slot.locked) return brief

        val cleanOutput = aiOutput.trim().removePrefix("```").removeSuffix("```").trim()
        val updatedParts = slot.parts.map { part ->
            if (part.locked) {
                part
            } else {
                val newValue = extractPartValue(part, cleanOutput)
                if (newValue.isNotBlank()) part.copy(value = cleanValue(newValue, part.label)) else part
            }
        }
        var updatedBrief = brief.withSlot(slot.copy(parts = updatedParts))

        if (slotKey == "setting" && updatedParts.any { it.key in setOf("place", "time") }) {
            updatedBrief = updatedBrief.copy(title = Generator.rebuildTitle(updatedBrief))
        }
        return updatedBrief
    }

    fun replacePartSafely(
        brief: Brief,
        slotKey: String,
        partKey: String,
        newValue: String,
    ): Brief {
        val slot = brief.slot(slotKey) ?: return brief
        if (slot.locked) return brief
        val part = slot.parts.firstOrNull { it.key == partKey } ?: return brief
        if (part.locked) return brief
        val cleaned = cleanValue(newValue, part.label)
        if (cleaned.isBlank()) return brief
        val updated = brief.withPart(partKey) { it.copy(value = cleaned) }
        return if (partKey in setOf("place", "time")) {
            updated.copy(title = Generator.rebuildTitle(updated))
        } else {
            updated
        }
    }

    fun extractPartValue(part: Part, text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ""

        if (part.label.isBlank()) {
            val labelMatch = lines.firstOrNull {
                it.startsWith("${part.key}:", ignoreCase = true) ||
                    it.startsWith("open:", ignoreCase = true) ||
                    it.startsWith("twist:", ignoreCase = true) ||
                    it.startsWith("close:", ignoreCase = true)
            }
            if (labelMatch != null) return labelMatch.substringAfter(":").trim()
            return text.trim()
        }

        val labelRegex = Regex("^(?:[·\\-*]\\s*)?${Regex.escape(part.label)}[:\\-]\\s*(.*)", RegexOption.IGNORE_CASE)
        for (line in lines) {
            val match = labelRegex.find(line)
            if (match != null) return match.groupValues[1].trim()
        }

        if (part.key == "beat1" || part.key == "beat2" || part.key == "beat3") {
            val num = part.key.takeLast(1)
            val beatLine = lines.firstOrNull {
                it.startsWith("$num.") || it.startsWith("$num)") ||
                    (num == "1" && it.contains("Escalates", ignoreCase = true)) ||
                    (num == "2" && it.contains("Complication", ignoreCase = true)) ||
                    (num == "3" && it.contains("Turn", ignoreCase = true))
            }
            if (beatLine != null) {
                return if (beatLine.contains(":")) beatLine.substringAfter(":").trim()
                else beatLine.substringAfter(".").substringAfter(")").trim()
            }
        }

        val keyRegex = Regex("^(?:[·\\-*]\\s*)?${Regex.escape(part.key)}[:\\-]\\s*(.*)", RegexOption.IGNORE_CASE)
        for (line in lines) {
            val match = keyRegex.find(line)
            if (match != null) return match.groupValues[1].trim()
        }

        return ""
    }

    private fun parseKeyValueLines(lines: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon > 0) {
                val k = line.substring(0, colon).trim().removePrefix("·").removePrefix("-").removePrefix("*").trim().lowercase()
                val v = line.substring(colon + 1).trim()
                map[k] = v
            }
        }
        return map
    }

    private fun parseCastSlot(lines: List<String>): BriefSlot {
        fun extractField(subLines: List<String>, label: String): String {
            val match = subLines.firstOrNull { it.contains(label, ignoreCase = true) } ?: return ""
            return cleanValue(match.substringAfter(":").trim(), label)
        }

        val charBIndex = lines.indexOfFirst { it.contains("Character B", ignoreCase = true) }
        val charALines = if (charBIndex != -1) lines.subList(0, charBIndex) else lines.take(lines.size / 2)
        val charBLines = if (charBIndex != -1) lines.subList(charBIndex, lines.size) else lines.drop(charALines.size)

        val nameA = charALines.firstOrNull { it.contains("Character A", ignoreCase = true) }
            ?.substringAfter(":")?.trim()
            ?: charALines.firstOrNull { !it.startsWith("·") && !it.startsWith("-") && !it.startsWith("*") }
                ?.substringAfter(":")?.trim()
            ?: "Maren Aldiss"
        val ageA = extractField(charALines, "Age").ifBlank { "Mid-twenties" }
        val roleA = extractField(charALines, "Role").ifBlank { "Protagonist" }

        val nameB = charBLines.firstOrNull { it.contains("Character B", ignoreCase = true) }
            ?.substringAfter(":")?.trim()
            ?: charBLines.firstOrNull { !it.startsWith("·") && !it.startsWith("-") && !it.startsWith("*") }
                ?.substringAfter(":")?.trim()
            ?: "Nura Barrow"
        val ageB = extractField(charBLines, "Age").ifBlank { "Late twenties" }
        val roleB = extractField(charBLines, "Role").ifBlank { "Partner" }

        val parts = listOf(
            Part("aname", "Name", nameA, bank = "names"),
            Part("aage", "Age", ageA, bank = "ages"),
            Part("arole", "Role", roleA, bank = "roles"),
            Part("atrait", "", extractField(charALines, "Trait").ifBlank { "Unfailingly polite" }, bank = "traits", prefix = "  · "),
            Part("awant", "", extractField(charALines, "Wants").ifBlank { "To be understood" }, bank = "wants", prefix = "  · wants: "),
            Part("afear", "", extractField(charALines, "Fears").ifBlank { "Being vulnerable" }, bank = "fears", prefix = "  · fears: "),
            Part("asecret", "", extractField(charALines, "Secret").ifBlank { "Knows the truth" }, bank = "secrets", prefix = "  · secret: "),
            Part("aflaw", "", extractField(charALines, "Flaw").ifBlank { "Apologises reflexively" }, bank = "flaws", prefix = "  · flaw: "),
            Part("gap", "", ""),
            Part("bname", "Name", nameB, bank = "names"),
            Part("bage", "Age", ageB, bank = "ages"),
            Part("brole", "Role", roleB, bank = "roles"),
            Part("btrait", "", extractField(charBLines, "Trait").ifBlank { "Direct and observant" }, bank = "traits", prefix = "  · "),
            Part("bwant", "", extractField(charBLines, "Wants").ifBlank { "Honest closure" }, bank = "wants", prefix = "  · wants: "),
            Part("bfear", "", extractField(charBLines, "Fears").ifBlank { "Being forgotten" }, bank = "fears", prefix = "  · fears: "),
            Part("bsecret", "", extractField(charBLines, "Secret").ifBlank { "Has a plane ticket" }, bank = "secrets", prefix = "  · secret: "),
            Part("bflaw", "", extractField(charBLines, "Flaw").ifBlank { "Cannot accept help" }, bank = "flaws", prefix = "  · flaw: "),
        )
        return BriefSlot("cast", "Cast", parts)
    }

    private fun parseBeatsSlot(lines: List<String>): BriefSlot {
        var b1 = "The situation becomes unavoidable."
        var b2 = "A conflict forces them together."
        var b3 = "The balance shifts permanently."

        for (line in lines) {
            val clean = cleanValue(line)
            when {
                line.startsWith("1.") || line.contains("Escalates", ignoreCase = true) -> b1 = clean
                line.startsWith("2.") || line.contains("Complication", ignoreCase = true) -> b2 = clean
                line.startsWith("3.") || line.contains("Turn", ignoreCase = true) -> b3 = clean
            }
        }
        return BriefSlot(
            "beats", "Beats",
            listOf(
                Part("beat1", "1. Escalates", b1, bank = "escalations"),
                Part("beat2", "2. Complication", b2, bank = "complications"),
                Part("beat3", "3. Turn", b3, bank = "turns"),
            ),
        )
    }
}

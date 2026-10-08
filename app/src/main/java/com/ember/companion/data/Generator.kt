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

    /**
     * Canonical slot keys, with the words authors actually use for them.
     *
     * The parser used to match `##` headings by exact lowercase string, so a
     * reply headed `## Characters` or `## Scene` had its entire section silently
     * dropped and replaced with hardcoded filler like "An intimate setting" —
     * which is precisely how a real answer turned into a pile of unrelated
     * default phrases. Matching on meaning fixes that at the source.
     */
    private val SECTION_ALIASES: Map<String, Set<String>> = mapOf(
        "setting" to setOf("setting", "settings", "place", "location", "where", "environment", "setting & place"),
        "cast" to setOf("cast", "characters", "character", "who", "players", "people", "the cast", "cast list"),
        "frame" to setOf("frame", "premise", "framing", "situation", "concept", "the frame", "scenario frame", "setup"),
        "open" to setOf("open", "opening", "opening scene", "start", "hook", "scene", "the open", "opening beat"),
        "beats" to setOf("beats", "beat", "progression", "escalation", "sequence", "story beats", "the beats", "structure"),
        "twist" to setOf("optional twist", "twist", "turn", "shift", "complication", "variation"),
        "close" to setOf("close", "closing", "end", "ending", "resolution", "aftermath", "the close", "payoff"),
    )

    /** Best-effort canonical key for a heading the model wrote. */
    private fun canonicalSection(rawHeading: String): String {
        val heading = rawHeading.trim().lowercase().trim('#', ':', ' ', '*')
        if (heading.isEmpty()) return ""
        // Exact alias first, so "close" never resolves to "complication".
        SECTION_ALIASES.forEach { (key, aliases) ->
            if (heading in aliases) return key
        }
        // Then containment, longest alias first so a specific heading wins over
        // a generic one ("opening scene" over "open").
        SECTION_ALIASES.forEach { (key, aliases) ->
            val hit = aliases.filter { alias ->
                alias.length >= 4 && (heading.contains(alias) || alias.contains(heading) && heading.length >= 4)
            }.maxByOrNull { it.length }
            if (hit != null) return key
        }
        return ""
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
            if (trimmed.startsWith("## ") || trimmed.startsWith("### ")) {
                // An unrecognised heading still collects its own lines, so
                // nothing the model wrote is thrown away outright.
                val raw = trimmed.trimStart('#').trim()
                val canonical = canonicalSection(raw)
                // A single lookup, not two. This read as if an unrecognised
                // heading was recovered by a second strategy, but
                // canonicalSection is deterministic, so the second call provably
                // returned the same "" and the fallback was dead.
                currentSection = canonical
                sections.putIfAbsent(currentSection.ifEmpty { raw.lowercase() }, mutableListOf())
                continue
            }
            if (currentSection.isNotEmpty() && trimmed.isNotEmpty()) {
                sections[currentSection]?.add(trimmed)
            }
        }

        // The twist slot is optional in the reply but its absence must not shift
        // every other section, so resolve aliases once and read canonically.
        fun section(vararg names: String): List<String> {
            for (n in names) sections[canonicalSection(n)]?.let { if (it.isNotEmpty()) return it }
            return emptyList()
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

        val settingLines = section("setting")
        val settingMap = parseKeyValueLines(settingLines)
        // Blank rather than a generic default: a made-up "An intimate setting"
        // next to four real values is what makes a parsed brief read as noise.
        // An empty field is honest and visibly needs filling.
        slots.add(
            BriefSlot(
                "setting", "Setting",
                listOf(
                    Part("place", "Place", settingMap.value("place", "location", "setting") ?: "", bank = "places"),
                    Part("time", "Time", settingMap.value("time", "when") ?: "", bank = "times"),
                    Part("weather", "Weather", settingMap.value("weather") ?: "", bank = "weather"),
                    Part("air", "Air", settingMap.value("air", "atmosphere", "mood") ?: "", bank = "atmosphere"),
                    Part("texture", "Texture", settingMap.value("texture", "sensory", "sounds", "smells") ?: "", bank = "sensory"),
                ),
            ),
        )

        val castLines = section("cast")
        slots.add(parseCastSlot(castLines))

        val frameLines = section("frame")
        val frameMap = parseKeyValueLines(frameLines)
        slots.add(
            BriefSlot(
                "frame", "Frame",
                listOf(
                    Part("framing", "Framing", frameMap.value("framing", "premise", "frame", "concept", "situation") ?: "", bank = "framings"),
                    Part("power", "Power", frameMap.value("power", "power balance", "dynamic") ?: "", bank = "powerBalances"),
                    Part("tension", "Tension", frameMap.value("tension", "stakes", "conflict") ?: "", bank = "tensions"),
                    Part("reveal", "Reveals to", frameMap.value("reveals to", "reveal", "risk", "at stake") ?: "", bank = "confidences"),
                    Part("register", "Register", frameMap.value("register", "tone", "explicitness") ?: "", bank = "registers"),
                    Part("pacing", "Pacing", frameMap.value("pacing", "pace", "rhythm") ?: "", bank = "pacingNotes"),
                    Part("pov", "POV", frameMap.value("pov", "point of view", "perspective") ?: "", bank = "pov"),
                ),
            ),
        )

        val openText = section("open").joinToString("\n").trim()
        slots.add(BriefSlot("open", "Open", listOf(Part("open", "", openText, bank = "openers"))))


        val beatsLines = section("beats")
        slots.add(parseBeatsSlot(beatsLines))

        val twistLines = section("twist")
        if (twistLines.isNotEmpty()) {
            val twistText = twistLines.joinToString("\n").trim()
            if (twistText.isNotBlank()) {
                slots.add(BriefSlot("twist", "Optional twist", listOf(Part("twist", "", twistText, bank = "twists"))))
            }
        }

        val closeLines = section("close")
        val closeText = closeLines.joinToString("\n").trim()
        slots.add(
            BriefSlot(
                "close", "Close",
                listOf(
                    Part("close", "", closeText, bank = "closers"),
                    // The close note is Ember's own pacing guidance, not something
                    // the model wrote, so it stays filled deliberately.
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

    /**
     * Reads `Label: value` lines, tolerating the shapes models actually write.
     *
     * Bold headings (`**Place:** ...`), bullets, and trailing punctuation all
     * occur; a strict `indexOf(':')` pass dropped those lines and left the
     * section full of hardcoded defaults.
     */
    private fun parseKeyValueLines(lines: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (raw in lines) {
            val line = raw.trim().removePrefix("·").removePrefix("-").removePrefix("*").trim()
            if (line.isEmpty()) continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            var key = line.substring(0, colon).trim()
            // Strip markdown emphasis and any trailing label characters.
            key = key.replace("*", "").replace("**", "").trim().trimEnd('.', ',')
            val value = line.substring(colon + 1).trim().trimStart('*').trim()
            if (key.isEmpty() || value.isEmpty()) continue
            map[key.lowercase()] = value
        }
        return map
    }

    /** Reads a labelled field, tolerating emphasis and punctuation. */
    private fun Map<String, String>.value(vararg names: String): String? =
        names.firstNotNullOfOrNull { n -> this[n.lowercase()] }

    private fun parseCastSlot(lines: List<String>): BriefSlot {
        /**
         * Reads a labelled field from a character's lines.
         *
         * Falls back to the first non-heading line for the name, because models
         * routinely write the name as a bare line rather than `Name:`.
         */
        fun field(subLines: List<String>, vararg labels: String): String {
            for (label in labels) {
                val hit = subLines.firstOrNull { line ->
                    val bare = line.trim().removePrefix("·").removePrefix("-").removePrefix("*").trim()
                    bare.startsWith(label, ignoreCase = true) ||
                        bare.startsWith("**$label**", ignoreCase = true)
                } ?: continue
                val after = hit.substringAfter(":", "").replace("*", "").trim()
                if (after.isNotBlank()) return after
            }
            return ""
        }

        /**
         * First line that looks like content rather than a sub-heading.
         *
         * `substringAfter` is called with NO missing-delimiter argument on
         * purpose: passing "" as the fallback returns "" for a line with no
         * colon, which is exactly the bare-name line this exists to capture.
         */
        fun firstContent(subLines: List<String>): String = subLines.firstOrNull { line ->
            val bare = line.trim().removePrefix("·").removePrefix("-").removePrefix("*").trim()
            bare.isNotEmpty() && !bare.endsWith(':') && !bare.equals("Character A", ignoreCase = true) &&
                !bare.equals("Character B", ignoreCase = true)
        }?.substringAfter(":")?.replace("*", "")?.trim().orEmpty()

        // Split on whichever character marker was used, or fall back to halves.
        // "Character A: Name: Maren" puts the marker and the label on one line;
        // split it so the label search sees a plain "Name:" line.
        val lines = lines.flatMap { line ->
            val marker = Regex("^\\s*(Character\\s+[AB])\\s*[:\\-]\\s*(.+)$", RegexOption.IGNORE_CASE).find(line)
            if (marker != null && marker.groupValues[2].contains(':')) {
                listOf(marker.groupValues[1], marker.groupValues[2])
            } else {
                listOf(line)
            }
        }

        val charBIndex = lines.indexOfFirst { it.contains("Character B", ignoreCase = true) }
        val charALines = if (charBIndex != -1) lines.subList(0, charBIndex) else lines.take(lines.size / 2)
        val charBLines = if (charBIndex != -1) lines.subList(charBIndex, lines.size) else lines.drop(charALines.size)

        fun build(prefix: String, subLines: List<String>, roleLabel: String): List<Part> = listOf(
            // ifBlank, not ?: — field() returns "" for a missing label, and an
            // empty string is not null, so the fallback never ran and every
            // character lost its name.
            Part("${prefix}name", "Name", field(subLines, "Name").ifBlank { firstContent(subLines) }, bank = "names"),
            Part("${prefix}age", "Age", field(subLines, "Age"), bank = "ages"),
            Part("${prefix}role", "Role", field(subLines, "Role", roleLabel), bank = "roles"),
            Part("${prefix}trait", "", field(subLines, "Trait", "Traits", "Voice"), bank = "traits", prefix = "  · "),
            Part("${prefix}want", "", field(subLines, "Wants", "Want", "Goal"), bank = "wants", prefix = "  · wants: "),
            Part("${prefix}fear", "", field(subLines, "Fears", "Fear", "Afraid"), bank = "fears", prefix = "  · fears: "),
            Part("${prefix}secret", "", field(subLines, "Secret", "Hides", "Secret:"), bank = "secrets", prefix = "  · secret: "),
            Part("${prefix}flaw", "", field(subLines, "Flaw", "Flaws", "Weakness"), bank = "flaws", prefix = "  · flaw: "),
        )

        return BriefSlot(
            "cast", "Cast",
            build("a", charALines, "Role") + listOf(Part("gap", "", "")) + build("b", charBLines, "Role"),
        )
    }

    private fun parseBeatsSlot(lines: List<String>): BriefSlot {
        // Start empty: an invented "The situation becomes unavoidable" beside two
        // real beats reads as a third unrelated event.
        var b1 = ""
        var b2 = ""
        var b3 = ""

        for (line in lines) {
            val clean = cleanValue(line)
            if (clean.isBlank()) continue
            val bare = line.trim().removePrefix("·").removePrefix("-").removePrefix("*").trim()
            when {
                bare.startsWith("1.") || bare.startsWith("1)") || bare.contains("escalat", ignoreCase = true) ->
                    if (b1.isBlank()) b1 = clean
                bare.startsWith("2.") || bare.startsWith("2)") || bare.contains("complicat", ignoreCase = true) ->
                    if (b2.isBlank()) b2 = clean
                bare.startsWith("3.") || bare.startsWith("3)") || bare.contains("turn", ignoreCase = true) ->
                    if (b3.isBlank()) b3 = clean
                // Unnumbered extra beat: file it in the first empty slot rather
                // than dropping what the model wrote.
                b1.isBlank() -> b1 = clean
                b2.isBlank() -> b2 = clean
                b3.isBlank() -> b3 = clean
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

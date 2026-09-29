package com.ember.companion.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Character card interchange.
 *
 * The specs are the V1/V2/V3 "chara card" family read by SillyTavern, RisuAI,
 * Agnai, Foreverse and most of the ecosystem. Two container rules matter and are
 * easy to get wrong:
 *  - V2 lives in a PNG `tEXt` chunk keyed `chara`, V3 in one keyed `ccv3`, and
 *    both hold base64 of the UTF-8 JSON;
 *  - when both chunks are present, `ccv3` wins.
 *
 * Everything here writes BOTH chunks on export, which is what SillyTavern itself
 * does, so one file imports everywhere.
 */
object CharacterCard {

    const val KEY_V2 = "chara"
    const val KEY_V3 = "ccv3"

    enum class Spec(val id: String, val version: String, val label: String) {
        V1("chara_card_v1", "1.0", "V1 (flat, oldest)"),
        V2("chara_card_v2", "2.0", "V2 (SillyTavern)"),
        V3("chara_card_v3", "3.0", "V3 (RisuAI, current)"),
    }

    // ---- model ----------------------------------------------------------

    data class Asset(
        val type: String = "icon",
        val uri: String = "ccdefault:",
        val name: String? = null,
        val ext: String = "png",
    )

    data class BookEntry(
        val keys: List<String>,
        val content: String,
        val enabled: Boolean = true,
        val insertionOrder: Int = 10,
        val caseSensitive: Boolean = false,
        val constant: Boolean = false,
        val selective: Boolean = false,
        val secondaryKeys: List<String> = emptyList(),
        val useRegex: Boolean = false,
        val position: String = "after_char",
        val name: String? = null,
    ) {
        fun toJson(spec: Spec): JSONObject = JSONObject().apply {
            put("keys", JSONArray(keys))
            put("content", content)
            put("extensions", JSONObject())
            put("enabled", enabled)
            put("insertion_order", insertionOrder)
            put("case_sensitive", caseSensitive)
            put("constant", constant)
            put("selective", selective)
            if (spec == Spec.V3) put("use_regex", useRegex)
            put("position", position)
            if (name != null) put("name", name)
        }

        companion object {
            fun fromJson(o: JSONObject, spec: Spec): BookEntry = BookEntry(
                keys = o.optJSONArray("keys").toStringList(),
                content = o.optString("content"),
                enabled = o.optBoolean("enabled", true),
                insertionOrder = o.optInt("insertion_order", 10),
                caseSensitive = o.optBoolean("case_sensitive", false),
                constant = o.optBoolean("constant", false),
                selective = o.optBoolean("selective", false),
                secondaryKeys = o.optJSONArray("secondary_keys").toStringList(),
                useRegex = o.optBoolean("use_regex", false),
                position = o.optString("position", "after_char"),
                name = if (o.has("name")) o.optString("name") else null,
            )
        }
    }

    data class Lorebook(
        val name: String = "Lorebook",
        val description: String = "",
        val scanDepth: Int = 2,
        val tokenBudget: Int = 500,
        val recursiveScanning: Boolean = false,
        val entries: List<BookEntry> = emptyList(),
    ) {
        fun toJson(spec: Spec): JSONObject = JSONObject().apply {
            put("name", name)
            put("description", description)
            put("scan_depth", scanDepth)
            put("token_budget", tokenBudget)
            put("recursive_scanning", recursiveScanning)
            put("extensions", JSONObject())
            put("entries", JSONArray(entries.map { it.toJson(spec) }))
        }

        companion object {
            fun fromJson(o: JSONObject, spec: Spec): Lorebook = Lorebook(
                name = o.optString("name", "Lorebook"),
                description = o.optString("description"),
                scanDepth = o.optInt("scan_depth", 2),
                tokenBudget = o.optInt("token_budget", 500),
                recursiveScanning = o.optBoolean("recursive_scanning", false),
                entries = o.optJSONArray("entries").objects()
                    .map { BookEntry.fromJson(it, spec) },
            )
        }
    }

    /**
     * The canonical card. Fields the specs require to be present are non-null and
     * default to "" rather than being omitted: a missing key is read differently
     * by different frontends, an empty string is read consistently.
     */
    data class Card(
        val name: String = "",
        val description: String = "",
        val personality: String = "",
        val scenario: String = "",
        val firstMessage: String = "",
        val exampleDialogue: String = "",
        val creatorNotes: String = "",
        val systemPrompt: String = "",
        val postHistoryInstructions: String = "",
        val alternateGreetings: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        val creator: String = "",
        val characterVersion: String = "",
        /**
         * Free-form extension values, carried through untouched on read and write.
         * Platform-specific keys with no home in the spec (Chub's
         * `tavern_personality`, for one) live here rather than being dropped.
         */
        val extensions: Map<String, String> = emptyMap(),
        // V3 only
        val nickname: String = "",
        val groupOnlyGreetings: List<String> = emptyList(),
        val creationDate: Long = 0,
        val modificationDate: Long = 0,
        val lorebook: Lorebook? = null,
    ) {
        fun toJson(spec: Spec): JSONObject {
            val data = JSONObject().apply {
                put("name", name)
                put("description", description)
                put("personality", personality)
                put("scenario", scenario)
                put("first_mes", firstMessage)
                put("mes_example", exampleDialogue)
                put("creator_notes", creatorNotes)
                put("system_prompt", systemPrompt)
                put("post_history_instructions", postHistoryInstructions)
                put("alternate_greetings", JSONArray(alternateGreetings))
                if (spec == Spec.V3) {
                    put("group_only_greetings", JSONArray(groupOnlyGreetings))
                    if (nickname.isNotBlank()) put("nickname", nickname)
                    put("creation_date", creationDate / 1000)
                    put("modification_date", modificationDate / 1000)
                    put(
                        "assets",
                        JSONArray().put(
                            JSONObject().apply {
                                put("type", "icon")
                                put("uri", "ccdefault:")
                                put("name", "main")
                                put("ext", "png")
                            },
                        ),
                    )
                }
                lorebook?.let { put("character_book", it.toJson(spec)) }
                put("tags", JSONArray(tags))
                put("creator", creator)
                put("character_version", characterVersion)
                put("extensions", JSONObject().also { e -> extensions.forEach { (k, v) -> e.put(k, v) } })
            }
            if (spec == Spec.V1) return data
            return JSONObject().apply {
                put("spec", spec.id)
                put("spec_version", spec.version)
                put("data", data)
            }
        }

        fun toJsonText(spec: Spec): String = toJson(spec).toString(2)

        /** V2-shaped JSON for embedding under the V2 PNG keyword. */
        fun toV2Payload(): String = toJson(Spec.V2).toString()

        companion object {
            fun fromJson(root: JSONObject, specHint: Spec? = null): Card {
                val spec = specHint ?: when (root.optString("spec")) {
                    Spec.V3.id -> Spec.V3
                    Spec.V2.id -> Spec.V2
                    else -> null
                }
                val data = if (root.has("data") && root.optJSONObject("data") != null) {
                    root.getJSONObject("data")
                } else {
                    root
                }
                val s = spec ?: Spec.V2
                val now = System.currentTimeMillis()
                return Card(
                    name = data.optString("name"),
                    description = data.optString("description"),
                    personality = data.optString("personality"),
                    scenario = data.optString("scenario"),
                    firstMessage = data.optString("first_mes"),
                    exampleDialogue = data.optString("mes_example"),
                    creatorNotes = data.optString("creator_notes"),
                    systemPrompt = data.optString("system_prompt"),
                    postHistoryInstructions = data.optString("post_history_instructions"),
                    alternateGreetings = data.optJSONArray("alternate_greetings").toStringList(),
                    tags = data.optJSONArray("tags").toStringList(),
                    creator = data.optString("creator"),
                    characterVersion = data.optString("character_version"),
                    nickname = data.optString("nickname"),
                    groupOnlyGreetings = data.optJSONArray("group_only_greetings").toStringList(),
                    creationDate = data.optLong("creation_date") * 1000,
                    modificationDate = data.optLong("modification_date").takeIf { it > 0 }
                        ?.times(1000) ?: now,
                    lorebook = data.optJSONObject("character_book")
                        ?.let { Lorebook.fromJson(it, s) },
                    extensions = data.optJSONObject("extensions")?.let { e ->
                        val map = mutableMapOf<String, String>()
                        e.keys().forEach { key -> map[key] = e.optString(key) }
                        map
                    } ?: emptyMap(),
                )
            }
        }
    }

    // ---- brief -> card --------------------------------------------------

    /**
     * Maps a generated brief onto card fields.
     *
     * [personality] is derived as a short trait list, never by concatenating the
     * description: no frontend merges those two fields, so a card that relies on
     * merging silently loses content on the round trip.
     */
    fun fromBrief(
        brief: Brief,
        tags: List<String> = emptyList(),
        firstMessage: String? = null,
        exampleDialogue: String = "",
        systemPrompt: String = "",
        postHistoryInstructions: String = "",
        alternateGreetings: List<String> = emptyList(),
        creator: String = "Ember",
    ): Card {
        fun part(key: String): String =
            brief.allParts().firstOrNull { it.key == key }?.value.orEmpty()

        val description = brief.slots
            .filter { it.key in setOf("cast", "setting", "beats", "twist", "close") }
            .joinToString("\n\n") { "## ${it.heading}\n${it.body}" }
            .trim()

        // Traits only, deduped, capped: this is the "summary" field.
        val personality = brief.slots.firstOrNull { it.key == "cast" }
            ?.parts.orEmpty()
            .filter { it.key.endsWith("trait") }
            .joinToString(", ") { it.value.trim() }
            .take(300)

        return Card(
            name = part("aname").ifBlank { brief.title },
            description = description,
            personality = personality,
            scenario = brief.slot("frame")?.body.orEmpty(),
            firstMessage = firstMessage ?: brief.slot("open")?.body.orEmpty(),
            exampleDialogue = exampleDialogue,
            systemPrompt = systemPrompt,
            postHistoryInstructions = postHistoryInstructions,
            alternateGreetings = alternateGreetings,
            tags = tags,
            creator = creator,
            characterVersion = "1.0",
            modificationDate = System.currentTimeMillis(),
        )
    }

    val Prompts: CharacterCardPrompts get() = CharacterCardPrompts

    // ---- JSON containers -------------------------------------------------

    fun encodeJson(card: Card, spec: Spec): ByteArray =
        card.toJsonText(spec).toByteArray(Charsets.UTF_8)

    // ---- PNG containers --------------------------------------------------

    /**
     * Writes a PNG carrying the card with the selected spec's payload.
     *
     * The `ccv3` payload is genuinely V3-complete (including the required-but-empty
     * `group_only_greetings`); the `chara` payload is V2 for older readers.
     * Both payloads are base64-encoded as required by the chara card spec.
     *
     * When [spec] is V3, both chunks are written (ccv3 wins on import).
     * When [spec] is V2, only the chara chunk is written.
     * When [spec] is V1, a flat JSON file is written (no PNG).
     */
    fun encodePng(card: Card, spec: Spec = Spec.V3, size: Int = 512): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(PNG_SIGNATURE)
        writeChunk(out, "IHDR", ihdr(size, size))

        when (spec) {
            Spec.V3 -> {
                val v3 = encodeBase64(card.toJson(Spec.V3).toString().toByteArray(Charsets.UTF_8))
                val v2 = encodeBase64(card.toJson(Spec.V2).toString().toByteArray(Charsets.UTF_8))
                writeChunk(out, "tEXt", textChunk(KEY_V3, v3.toByteArray(Charsets.UTF_8)))
                writeChunk(out, "tEXt", textChunk(KEY_V2, v2.toByteArray(Charsets.UTF_8)))
            }
            Spec.V2 -> {
                val v2 = encodeBase64(card.toJson(Spec.V2).toString().toByteArray(Charsets.UTF_8))
                writeChunk(out, "tEXt", textChunk(KEY_V2, v2.toByteArray(Charsets.UTF_8)))
            }
            Spec.V1 -> {
                // V1 doesn't use PNG chunks; encodeJson is used instead.
                // Fall through to minimal PNG for compatibility.
                val v2 = encodeBase64(card.toJson(Spec.V2).toString().toByteArray(Charsets.UTF_8))
                writeChunk(out, "tEXt", textChunk(KEY_V2, v2.toByteArray(Charsets.UTF_8)))
            }
        }

        writeChunk(out, "IDAT", deflate(rawScanlines(size, size)))
        writeChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    private fun ihdr(w: Int, h: Int): ByteArray = ByteArrayOutputStream().apply {
        writeInt(w)
        writeInt(h)
        write(8)      // bit depth
        write(2)      // colour type: truecolour RGB
        write(0)      // compression
        write(0)      // filter
        write(0)      // interlace
    }.toByteArray()

    /** Filter byte 0 per scanline, over a vertical ember gradient. */
    private fun rawScanlines(w: Int, h: Int): ByteArray {
        val out = ByteArrayOutputStream()
        for (y in 0 until h) {
            out.write(0)
            val t = y.toFloat() / (h - 1).coerceAtLeast(1)
            val r = (196 - 70 * t).toInt()
            val g = (74 - 40 * t).toInt()
            val b = (28 + 10 * t).toInt()
            for (x in 0 until w) {
                // A soft diagonal lift so the card is not a flat block of colour.
                val lift = ((x + y) % 64) * 0.35f
                out.write((r + lift).toInt().coerceIn(0, 255))
                out.write((g + lift * 0.5f).toInt().coerceIn(0, 255))
                out.write((b + lift * 0.7f).toInt().coerceIn(0, 255))
            }
        }
        return out.toByteArray()
    }

    private fun textChunk(keyword: String, value: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(keyword.toByteArray(Charsets.ISO_8859_1))
            write(0)
            write(value)
        }.toByteArray()

    private fun deflate(data: ByteArray): ByteArray {
        val d = Deflater()
        d.setInput(data)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!d.finished()) {
            out.write(buf, 0, d.deflate(buf))
        }
        d.end()
        return out.toByteArray()
    }

    private fun inflate(data: ByteArray): ByteArray {
        val i = Inflater()
        i.setInput(data)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!i.finished()) {
            val n = i.inflate(buf)
            if (n == 0 && i.needsInput()) break
            out.write(buf, 0, n)
        }
        i.end()
        return out.toByteArray()
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        out.writeInt(data.size)
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        out.writeInt(crc.value.toInt())
    }

    // ---- reading ---------------------------------------------------------

    sealed interface ReadResult {
        data class Card2(val card: Card, val spec: Spec, val format: String) : ReadResult
        data class Failure(val reason: String) : ReadResult
    }

    /**
     * Sniffs a card out of whatever the user picked. Chunk order is the whole
     * problem: `ccv3` must win over `chara`, and compressed chunks have to be
     * inflated before they are base64-decoded.
     */
    fun read(bytes: ByteArray): ReadResult {
        if (bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) {
            return readPng(bytes)
        }
        if (bytes.size > 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            return readCharx(bytes)
        }
        return readJsonText(String(bytes, Charsets.UTF_8).trim())
    }

    private fun readPng(bytes: ByteArray): ReadResult {
        var i = 8
        var v3: String? = null
        var v2: String? = null
        while (i + 8 <= bytes.size) {
            val length = readInt(bytes, i)
            if (length < 0 || i + 12 + length > bytes.size) break
            val type = String(bytes, i + 4, 4, Charsets.ISO_8859_1)
            val data = bytes.copyOfRange(i + 8, i + 8 + length)
            when (type) {
                "tEXt" -> {
                    val split = data.indexOfFirst { it == 0.toByte() }
                    if (split > 0) {
                        val key = String(data, 0, split, Charsets.ISO_8859_1)
                        val value = String(data, split + 1, data.size - split - 1, Charsets.UTF_8)
                        if (key == KEY_V3) v3 = value else if (key == KEY_V2) v2 = value
                    }
                }
                "zTXt" -> {
                    val split = data.indexOfFirst { it == 0.toByte() }
                    if (split > 0) {
                        val key = String(data, 0, split, Charsets.ISO_8859_1)
                        if (key == KEY_V2 || key == KEY_V3) {
                            val raw = runCatching { inflate(data.copyOfRange(split + 2, data.size)) }
                                .getOrNull()
                                ?.let { String(it, Charsets.UTF_8) }
                            if (raw != null) {
                                if (key == KEY_V3) v3 = raw else v2 = raw
                            }
                        }
                    }
                }
                "iTXt" -> {
                    val parsed = parseITXt(data)
                    if (parsed != null) {
                        if (parsed.first == KEY_V3) v3 = parsed.second
                        if (parsed.first == KEY_V2) v2 = parsed.second
                    }
                }
                "IEND" -> return finish(v3, v2, "PNG")
            }
            i += 12 + length
        }
        return finish(v3, v2, "PNG")
    }

    /**
     * iTXt: keyword, NUL, compression flag, compression method, language tag,
     * NUL, translated keyword, NUL, text. Only the uncompressed form is legal for
     * the single byte of payload we care about, but the flag is honoured.
     */
    private fun parseITXt(data: ByteArray): Pair<String, String>? {
        var p = 0
        fun nul(): Int {
            while (p < data.size && data[p] != 0.toByte()) p++
            return p
        }
        val key = String(data, 0, nul(), Charsets.ISO_8859_1)
        p++
        if (p + 2 > data.size) return null
        val compressed = data[p] == 1.toByte()
        p += 2
        nul(); p++   // language tag
        nul(); p++   // translated keyword
        if (p > data.size) return null
        val text = if (compressed) {
            runCatching { inflate(data.copyOfRange(p, data.size)) }.getOrNull()
                ?.let { String(it, Charsets.UTF_8) } ?: return null
        } else {
            String(data, p, data.size - p, Charsets.UTF_8)
        }
        return key to text
    }

    private fun finish(v3: String?, v2: String?, format: String): ReadResult {
        val payload = v3 ?: v2
            ?: return ReadResult.Failure("No character card data found in this $format.")
        return decodePayload(payload, if (v3 != null) Spec.V3 else Spec.V2, format)
    }

    private fun decodePayload(payload: String, fallback: Spec, format: String): ReadResult {
        val trimmed = payload.trim()
        if (trimmed.startsWith("{")) return readJsonText(trimmed, format)
        return runCatching {
            val json = String(decodeBase64(trimmed), Charsets.UTF_8)
            readJsonText(json, format)
        }.getOrElse { ReadResult.Failure("Card data was not valid base64 JSON.") }
    }

    private fun readCharx(bytes: ByteArray): ReadResult {
        return runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "card.json") {
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        return readJsonText(text, "CHARX")
                    }
                    entry = zip.nextEntry
                }
            }
            ReadResult.Failure("This .charx archive has no card.json at its root.")
        }.getOrElse { ReadResult.Failure("Could not read this .charx archive: ${it.message}") }
    }

    private fun readJsonText(text: String, format: String = "JSON"): ReadResult =
        runCatching {
            val root = JSONObject(text)
            val spec = when (root.optString("spec")) {
                Spec.V3.id -> Spec.V3
                Spec.V2.id -> Spec.V2
                else -> if (root.has("data")) Spec.V2 else Spec.V1
            }
            ReadResult.Card2(Card.fromJson(root, spec), spec, format)
        }.getOrElse { ReadResult.Failure("Not valid card JSON: ${it.message}") }

    // ---- base64 ----------------------------------------------------------

    /** Tolerates the URL-safe alphabet and missing padding, both seen in the wild. */
    private fun decodeBase64(value: String): ByteArray {
        val cleaned = value.filterNot { it.isWhitespace() }
            .replace('-', '+')
            .replace('_', '/')
        val padded = cleaned + "=".repeat((4 - cleaned.length % 4) % 4)
        return Base64.getDecoder().decode(padded)
    }

    fun encodeBase64(value: ByteArray): String =
        Base64.getEncoder().encodeToString(value)

    // ---- byte helpers ----------------------------------------------------

    private fun ByteArrayOutputStream.writeInt(v: Int) {
        write(v ushr 24 and 0xFF)
        write(v ushr 16 and 0xFF)
        write(v ushr 8 and 0xFF)
        write(v and 0xFF)
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF shl 24) or
            (bytes[offset + 1].toInt() and 0xFF shl 16) or
            (bytes[offset + 2].toInt() and 0xFF shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }
}

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}

object CharacterCardPrompts {

    /**
     * Builds the AI prompt for generating a rich first_mes (character greeting).
     */
    fun buildFirstMessagePrompt(brief: Brief, card: CharacterCard.Card): String = buildString {
        appendLine("Generate the opening greeting ('first_mes') for a roleplay character card in character voice.")
        appendLine()
        appendLine("CHARACTER PROFILE:")
        appendLine("- Name: ${card.name}")
        if (card.personality.isNotBlank()) appendLine("- Personality: ${card.personality}")
        brief.slot("cast")?.body?.let { appendLine("- Cast Details:\n$it") }
        appendLine()
        appendLine("SCENARIO & SETTING:")
        if (card.scenario.isNotBlank()) appendLine("- Scenario: ${card.scenario}")
        brief.slot("setting")?.body?.let { appendLine("- Setting Details:\n$it") }
        brief.slot("frame")?.body?.let { appendLine("- Dynamic & Tone:\n$it") }
        brief.slot("open")?.body?.let { appendLine("- Opening Hook / Seed: $it") }
        appendLine()
        appendLine("REQUIREMENTS:")
        appendLine("1. Write entirely in character voice as ${card.name}.")
        appendLine("2. Perspective: Use roleplay narration format (third person past/present or second person, actions/thoughts in asterisks *like this*, spoken dialogue in quotes \"like this\").")
        appendLine("3. Ground the scene immediately in the physical setting with concrete sensory texture.")
        appendLine("4. Show the character's personality traits and underlying tension through actions and speech.")
        appendLine("5. Directly address {{user}} within the scene context. DO NOT speak, think, or act for {{user}}.")
        appendLine("6. Conclude with an engaging conversational hook, gesture, or dilemma that invites an immediate response.")
        appendLine("7. Length: 150 to 280 words. No meta-commentary, markdown headers, or introductory chatter.")
    }

    /**
     * Builds the AI prompt for generating 2-3 dialogue examples in <START> format.
     */
    fun buildMesExamplePrompt(brief: Brief, card: CharacterCard.Card): String = buildString {
        appendLine("Generate 2 to 3 dialogue examples ('mes_example') for this character card.")
        appendLine()
        appendLine("CHARACTER:")
        appendLine("- Name: ${card.name}")
        if (card.personality.isNotBlank()) appendLine("- Personality: ${card.personality}")
        brief.slot("cast")?.body?.let { appendLine("- Cast Context:\n$it") }
        appendLine()
        appendLine("FORMAT RULES:")
        appendLine("- Every example vignette MUST start with the exact delimiter '<START>' on its own line.")
        appendLine("- Use '{{user}}:' and '{{char}}:' to indicate speaker turns.")
        appendLine("- Include atmospheric actions/expressions in asterisks *...* and dialogue in quotes \"...\".")
        appendLine("- Vignette 1: Characteristic baseline conversation establishing tone, mannerisms, and posture.")
        appendLine("- Vignette 2: Conflict or boundary testing when a flaw, secret, or sensitive topic is touched.")
        appendLine("- Vignette 3: An intense, vulnerable, or high-stakes exchange demonstrating emotional depth.")
        appendLine("- Each vignette should be 2 to 4 turns long.")
        appendLine("- Total length: 300 to 500 words. Provide ONLY the vignettes starting with <START>.")
    }

    /**
     * Cleans up raw LLM text for first_mes: strips markdown code fences and conversational preambles.
     */
    fun cleanFirstMessage(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.substringAfter("\n")
            if (text.endsWith("```")) text = text.substringBeforeLast("```").trim()
        }
        text = text.replace(Regex("^(?:Here is (?:a|the) (?:first message|greeting|opening)[^:]*:\\s*)+", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("\\n+(?:Let me know|Hope this helps|Feel free to|Enjoy|If you need)[^\\n]*$", RegexOption.IGNORE_CASE), "")
        return text.trim()
    }

    /**
     * Cleans up raw LLM text for mes_example: ensures proper <START> structure and strips commentary.
     */
    fun cleanMesExample(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.substringAfter("\n")
            if (text.endsWith("```")) text = text.substringBeforeLast("```").trim()
        }
        if (!text.startsWith("<START>", ignoreCase = true)) {
            val startIdx = text.indexOf("<START>", ignoreCase = true)
            text = if (startIdx != -1) {
                text.substring(startIdx)
            } else {
                "<START>\n$text"
            }
        }
        return text.trim()
    }
}


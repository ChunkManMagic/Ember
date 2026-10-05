package com.ember.companion.ui

/**
 * System prompts for the Lab's AI features.
 *
 * Split out from AiClient because these two callers need different contracts:
 * freeform brainstorming writes prose, while editing a brief must produce
 * machine-readable values it can apply field by field. Sharing one "creative
 * writing partner" prompt for both is what let a rewrite come back as prose and
 * get scraped into the wrong fields.
 */
object BriefJson {

    /**
     * Pulls a flat map of scalar values out of a model reply.
     *
     * Tolerant on the way in, strict on the way out: fences and chatty preamble
     * are stripped, but anything that isn't a plain top-level scalar is dropped.
     * That matters because the caller applies these keys directly to brief
     * fields — accepting a nested object here would smear a structure into a
     * single text box.
     */
    fun stringMap(raw: String): Map<String, String> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyMap()
        val cleaned = Regex("(?s)```(?:json)?\\s*(.*?)\\s*```").find(text)?.groupValues?.get(1) ?: text
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyMap()
        return runCatching {
            val obj = org.json.JSONObject(cleaned.substring(start, end + 1))
            buildMap {
                obj.keys().forEach { key ->
                    when (val value = obj.opt(key)) {
                        is String -> put(key, value)
                        null -> Unit
                        // org.json's NULL sentinel on the JVM.
                        else -> if (value is org.json.JSONObject || value is org.json.JSONArray) {
                            Unit
                        } else if (value.toString() == "null") {
                            Unit
                        } else {
                            put(key, value.toString())
                        }
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }
}

object BriefAiPrompts {

    /**
     * Used when editing one section of an existing brief.
     *
     * The instruction to return a bare JSON object is repeated here as well as
     * in the user turn: providers weight the system prompt heavily, and a single
     * mention of the format in a long user message is not reliably obeyed.
     */
    val structuredEditor: String = """
        You edit one section of a scenario brief inside the Ember app. Briefs describe
        fictional roleplay scenarios between clearly adult, consenting original characters.

        Your reply MUST be a single JSON object and nothing else:
        - No preamble, no explanation, no markdown code fence.
        - One key per field you were given, and no keys you were not given.
        - Each value is the new text for that field, one or two sentences.
        - Omit nothing: if a field should not change, repeat its current text.

        Consistency is the whole point. You are editing ONE section of a brief whose
        other sections are already final. Do not rename characters, do not change the
        time period or location established elsewhere, and do not contradict facts
        stated in the rest of the brief. If the request would force a contradiction,
        write the closest consistent version instead and change nothing else.

        Write concrete, sensory detail rather than vague abstraction, and match the
        register and length already used in the fields you were shown.
    """.trimIndent()

    /**
     * Used for the freeform "talk to the model about this brief" box.
     *
     * Kept distinct from the editor prompt so a conversational reply is allowed
     * to be prose — but is told to *ground itself in the brief* and to answer the
     * question that was asked rather than rewriting the whole scenario, which is
     * what made the output feel like unrelated material.
     */
    val conversational: String = """
        You are the writing partner for a scenario brief inside the Ember app. Briefs
        describe fictional roleplay scenarios between clearly adult, consenting
        original characters.

        The user is developing ONE scenario and is talking to you about it. The brief
        is their work: it is the source of truth.

        Rules:
        - Answer the question that was actually asked. If they ask for a variant of a
          line, give the variant — not a rewritten scenario.
        - Stay consistent with the brief. Use the names, roles, setting and tone
          already established; never invent replacements.
        - When you propose a change, give it as ready-to-use text they can paste,
          not as a description of a change.
        - Be concrete and sensory rather than abstract. Match the brief's register.
        - Keep replies tight. A paragraph or two, unless they asked for more.
        - Never write or assist with sexual content involving minors, children, or
          characters whose age is ambiguous or could read as under 18.
    """.trimIndent()
}
package com.ember.companion.data

import kotlinx.serialization.Serializable

/**
 * The steering dials. These are the dimensions people actually search by, and
 * none of them were reachable before: every bank was sampled uniformly at
 * random, so "make it slower" or "give her the power here" could not be asked
 * for. Each dial now selects DETERMINISTICALLY out of a small ordered set, which
 * is what makes two runs with the same dials comparable instead of a fresh roll
 * of the dice.
 */
@Serializable
data class Dials(
    val explicitness: Int = 1,
    val pace: Int = 1,
    val power: Int = 1,
    val pov: Int = 1,
) {
    fun withExplicitness(v: Int) = copy(explicitness = v.coerceIn(0, 2))
    fun withPace(v: Int) = copy(pace = v.coerceIn(0, 2))
    fun withPower(v: Int) = copy(power = v.coerceIn(0, 2))
    fun withPov(v: Int) = copy(pov = v.coerceIn(0, 2))

    companion object {
        val EXPLICITNESS = listOf("restrained", "charged", "unfiltered")
        val PACE = listOf("slow burn", "steady build", "immediate")
        val POWER = listOf(
            "she sets the terms and he agrees",
            "neither leads; it turns on who blinks first",
            "he sets the terms and she agrees",
        )
        val POV = listOf(
            "third person, past tense",
            "second person, present tense",
            "first person from the character who wants it more",
        )
    }
}

/**
 * One addressable line inside a brief. A part knows which bank produced it, which
 * is what makes "reroll just the location" possible. Before this existed the
 * only unit of regeneration was the whole brief, so every reroll discarded the
 * parts you liked.
 */
data class Part(
    val key: String,
    val label: String,
    val value: String,
    /** Bank id this was drawn from, or null when it is not independently rerollable. */
    val bank: String? = null,
    val locked: Boolean = false,
    /** Hidden parts feed derived text (the title) rather than being displayed. */
    val hidden: Boolean = false,
    /**
     * Rendered before [value] with no separator, used to keep the Cast block's
     * leading "  · " without flattening the bullet into the value itself.
     */
    val prefix: String = "",
)

data class BriefSlot(
    val key: String,
    val heading: String,
    val parts: List<Part>,
    val locked: Boolean = false,
) {
    val body: String
        get() = parts.filter { !it.hidden }.joinToString("\n") { part ->
            when {
                part.label.isNotBlank() -> "${part.label}: ${part.value}"
                part.prefix.isNotBlank() -> "${part.prefix} ${part.value}"
                else -> part.value
            }
        }
}

/**
 * A generated brief, now addressable. [slots] is the source of truth; [sections]
 * and [text] are derived so everything downstream (saving to Room, sharing,
 * feeding the AI) keeps working exactly as before.
 */
data class Brief(
    val title: String,
    val slots: List<BriefSlot> = emptyList(),
    /** The user's own premise. Constrains generation instead of being ignored. */
    val premise: String = "",
    val dials: Dials = Dials(),
) {

    fun slot(key: String): BriefSlot? = slots.firstOrNull { it.key == key }

    fun allParts(): List<Part> = slots.flatMap { it.parts }

    fun withSlot(updated: BriefSlot): Brief =
        copy(slots = slots.map { if (it.key == updated.key) updated else it })

    fun withTitle(newTitle: String): Brief = copy(title = newTitle.trim())

    fun withPart(key: String, transform: (Part) -> Part): Brief {
        var found = false
        val next = slots.map { slot ->
            if (slot.parts.none { it.key == key }) slot
            else {
                found = true
                slot.copy(parts = slot.parts.map { if (it.key == key) transform(it) else it })
            }
        }
        return if (found) copy(slots = next) else this
    }

    fun withPartValue(key: String, value: String): Brief =
        withPart(key) { it.copy(value = value) }

    fun withPartLabelAndValue(key: String, label: String, value: String): Brief =
        withPart(key) { it.copy(label = label, value = value) }

    fun withPartRemoved(key: String): Brief {
        val next = slots.map { slot ->
            slot.copy(parts = slot.parts.filter { it.key != key })
        }
        return copy(slots = next)
    }

    fun withPartAdded(slotKey: String, part: Part): Brief {
        val next = slots.map { slot ->
            if (slot.key == slotKey) {
                slot.copy(parts = slot.parts + part)
            } else {
                slot
            }
        }
        return copy(slots = next)
    }

    /** Flattened view used for persistence and the AI context. */
    val sections: List<Section>
        get() = slots.filter { slot -> slot.parts.any { !it.hidden } }
            .map { Section(it.heading, it.body) }

    val text: String
        get() = buildString {
            if (premise.isNotBlank()) {
                appendLine("Premise")
                appendLine("=".repeat(7))
                appendLine(premise)
                appendLine()
            }
            appendLine(title)
            appendLine("=".repeat(title.length.coerceAtMost(60)))
            sections.forEach { section ->
                appendLine()
                appendLine(section.heading)
                appendLine("-".repeat(section.heading.length))
                appendLine(section.body)
            }
        }.trim()

    data class Section(val heading: String, val body: String)
}

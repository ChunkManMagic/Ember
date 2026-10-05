package com.ember.companion.data

/**
 * A field found on a platform's own page.
 *
 * [label] and [maxChars] are best-effort: forms rarely state their own caps, so
 * a missing [maxChars] means "unknown" rather than "no limit". Ember never
 * invents a cap — a wrong one would truncate someone's work silently.
 */
data class FormField(
    val name: String,
    val type: String,
    val label: String = "",
    val example: String? = null,
    val maxChars: Int = 0,
    val required: Boolean = false,
    /** Values of a <select>, when the control offers a fixed set. */
    val options: List<String> = emptyList(),
)
package com.avangard.app.core.domain.model

import kotlinx.serialization.Serializable

/** One of the four EveningClose virtues. Quotes tag themselves with one
 *  or more so the library can filter by virtue. */
@Serializable
enum class VirtueTag {
    RATIONALITY,
    INDEPENDENCE,
    HONESTY,
    JUSTICE,
}

@Serializable
data class Quote(
    val id: Int,
    val text: String,
    val source: String,
    val virtues: List<VirtueTag>,
)

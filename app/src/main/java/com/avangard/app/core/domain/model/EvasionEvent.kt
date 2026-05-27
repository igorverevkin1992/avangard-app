package com.avangard.app.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Recorded invocation of the sabotage / evasion diagnostic screen.
 * Stored in a bounded ring buffer in user preferences (last N entries) so
 * the weekly audit can surface «на неделе диагностировано Y раз» without
 * a dedicated SQL table.
 */
enum class EvasionKind { Substitution, Defect, Comparison }

@Serializable
data class EvasionEvent(
    val timestampMs: Long,
    val kind: EvasionKind,
)

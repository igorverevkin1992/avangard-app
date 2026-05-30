package com.avangard.app.core.domain.model

/**
 * Operator's retrospective answer about the bottleneck they pinned on the
 * previous weekly review. Persisted on the current week's DailySession
 * so each Sunday-audit row carries both the new bottleneck and the
 * verdict on the previous one — closing the PDCA loop.
 */
enum class BottleneckFollowup(val displayName: String) {
    Yes("ДА"),
    Partial("ЧАСТИЧНО"),
    No("НЕТ"),
}

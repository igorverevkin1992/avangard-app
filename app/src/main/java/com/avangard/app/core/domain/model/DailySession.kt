package com.avangard.app.core.domain.model

enum class DefectKind { Defect, Waste }

/** Quality mode picked at Core approval. Determines Extracted vs Partial in chronometer. */
enum class CoreMode { Standard, Mvd }

sealed interface CoreStatus {
    data object Idle : CoreStatus
    data class Approved(
        val prompt: String,
        val authorizedAt: Long,
        val mode: CoreMode = CoreMode.Standard,
    ) : CoreStatus
    data class Failed(val kind: DefectKind) : CoreStatus
}

enum class InfraStatus { NotDone, Standard, Mvd }

data class VirtueScores(
    val rationality: Int,
    val independence: Int,
    val honesty: Int,
    val justice: Int,
) {
    init {
        require(rationality in -1..1) { "rationality out of range" }
        require(independence in -1..1) { "independence out of range" }
        require(honesty in -1..1) { "honesty out of range" }
        require(justice in -1..1) { "justice out of range" }
    }
}

/**
 * In-memory projection of [com.avangard.app.core.database.entity.DailySessionEntity].
 * Always returned non-null by SessionRepository.observeToday() — a fresh zeroed
 * session is auto-upserted on the first read of an empty day.
 */
data class DailySession(
    val dateEpoch: Long,
    val mvdActive: Boolean,
    val coreStatus: CoreStatus,
    val infra02: InfraStatus,
    val infra03: InfraStatus,
    val infra04: InfraStatus,
    val infra05: InfraStatus,
    val eveningClosed: Boolean,
    val eveningClosedAt: Long?,
    val virtues: VirtueScores?,
    val bottleneckForNextWeek: Bottleneck?,
    /** Operator's free-text summary of the day, ≤ JOURNAL_MAX_CHARS. */
    val journalEntry: String? = null,
) {
    /** Hostage Logic predicate: Infra modules unlock only when Core is Approved. */
    val isCoreUnlocked: Boolean get() = coreStatus is CoreStatus.Approved

    fun infraStatus(habit: Habit): InfraStatus = when (habit) {
        Habit.Generations -> InfraStatus.NotDone // Core itself isn't an Infra slot.
        Habit.Spanish -> infra02
        Habit.Sport -> infra03
        Habit.Watching -> infra04
        Habit.Reading -> infra05
    }

    companion object {
        const val JOURNAL_MAX_CHARS = 500
    }
}

package com.avangard.app.core.domain.model

/**
 * Five-direction tracker. [requiresCoreApproval] encodes the operator's real
 * daily schedule: Generations (Core) sits between the morning routine and the
 * afternoon polish.
 *
 *   * Morning, no gate — Spanish (02), Sport (03). Run before the work day.
 *   * Core — Generations (01). The day's productive work.
 *   * Evening, Core-gated — Watching (04), Reading (05). Run after Core,
 *     never before.
 *
 * StartFocusUseCase and SetInfraStatusUseCase consult [requiresCoreApproval]
 * to decide whether to enforce the Hostage-Logic gate.
 */
enum class Habit(
    val code: String,
    val displayName: String,
    val shortLabel: String,
    /** True when the habit only unlocks after Core (Generations) is Approved. */
    val requiresCoreApproval: Boolean,
) {
    Generations(code = "01", displayName = "ГЕНЕРАЦИИ", shortLabel = "ГЕН",
        requiresCoreApproval = false),
    Spanish(code = "02", displayName = "ИСПАНСКИЙ", shortLabel = "ИСП",
        requiresCoreApproval = false),
    Sport(code = "03", displayName = "СПОРТ", shortLabel = "СПОРТ",
        requiresCoreApproval = false),
    Watching(code = "04", displayName = "НАСМОТРЕННОСТЬ", shortLabel = "НАСМ",
        requiresCoreApproval = true),
    Reading(code = "05", displayName = "ЧТЕНИЕ", shortLabel = "ЧТЕН",
        requiresCoreApproval = true);

    companion object {
        fun byCode(code: String): Habit? = entries.firstOrNull { it.code == code }
    }
}

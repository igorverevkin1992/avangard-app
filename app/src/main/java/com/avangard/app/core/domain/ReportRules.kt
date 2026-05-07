package com.avangard.app.core.domain

import java.time.LocalTime

object ReportRules {
    const val ARTIFACT_MAX_LENGTH = 80
    const val ANALYSIS_MIN_LENGTH = 20

    val MORNING_WINDOW: ClosedRange<LocalTime> =
        LocalTime.of(6, 0)..LocalTime.of(8, 0)

    val EVENING_WINDOW: ClosedRange<LocalTime> =
        LocalTime.of(20, 0)..LocalTime.of(21, 0)

    val MIDDAY_CHECKPOINT: LocalTime = LocalTime.of(13, 0)
}

package com.avangard.app.core.domain.model

data class DailyReport(
    val id: Long,
    val dateEpoch: Long,
    val targetArtifact: String,
    val isCompleted: Boolean,
    val eliminatedWaste: Int,
    val failureCause: String?,
    val correctiveAction: String?,
    val midday: MiddayStatus = MiddayStatus.NotPolled,
    val middayRecordedAt: Long? = null,
)

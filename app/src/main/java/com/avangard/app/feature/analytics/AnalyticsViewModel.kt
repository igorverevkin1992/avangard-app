package com.avangard.app.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.domain.model.DailyReport
import com.avangard.app.core.domain.repository.ReportRepository
import com.avangard.app.core.ui.components.ChartPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class JournalEntry(
    val dateEpoch: Long,
    val statusCode: Int,
    val artifact: String,
    val hasFailureAnalysis: Boolean,
)

data class AnalyticsState(
    val points: List<ChartPoint> = emptyList(),
    val entries: List<JournalEntry> = emptyList(),
    val totalCompleted: Int = 0,
    val totalRecorded: Int = 0,
) {
    val completionRate: Float get() =
        if (totalRecorded == 0) 0f else totalCompleted.toFloat() / totalRecorded
}

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    repository: ReportRepository,
) : ViewModel() {

    val state: StateFlow<AnalyticsState> = repository.observeAll()
        .map { reports -> reports.toAnalyticsState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AnalyticsState(),
        )
}

internal fun List<DailyReport>.toAnalyticsState(): AnalyticsState {
    if (isEmpty()) return AnalyticsState()
    val byDateAsc = sortedBy { it.dateEpoch }
    val points = byDateAsc.map { report ->
        // Project domain state into the [0, 1] band:
        //   1.0 — artifact created
        //   0.5 — initialized but not completed
        //   0.0 — placeholder for empty rows (unused here)
        val value = if (report.isCompleted) 1f else 0.5f
        ChartPoint(value = value, success = report.isCompleted)
    }
    val entries = sortedByDescending { it.dateEpoch }.map { report ->
        JournalEntry(
            dateEpoch = report.dateEpoch,
            statusCode = if (report.isCompleted) 1 else 0,
            artifact = report.targetArtifact,
            hasFailureAnalysis = !report.isCompleted &&
                !report.failureCause.isNullOrBlank() &&
                !report.correctiveAction.isNullOrBlank(),
        )
    }
    return AnalyticsState(
        points = points,
        entries = entries,
        totalCompleted = count { it.isCompleted },
        totalRecorded = size,
    )
}

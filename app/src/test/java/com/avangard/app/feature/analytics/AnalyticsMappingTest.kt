package com.avangard.app.feature.analytics

import com.avangard.app.core.domain.model.DailyReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsMappingTest {

    @Test
    fun `empty input maps to empty state`() {
        val state = emptyList<DailyReport>().toAnalyticsState()
        assertEquals(0, state.totalRecorded)
        assertEquals(0, state.totalCompleted)
        assertEquals(0f, state.completionRate, 0.0001f)
        assertTrue(state.entries.isEmpty())
        assertTrue(state.points.isEmpty())
    }

    @Test
    fun `points are produced in chronological order`() {
        val reports = listOf(
            report(dateEpoch = 3_000L, completed = true, artifact = "C"),
            report(dateEpoch = 1_000L, completed = false, artifact = "A"),
            report(dateEpoch = 2_000L, completed = true, artifact = "B"),
        )
        val state = reports.toAnalyticsState()
        assertEquals(3, state.points.size)
        assertFalse(state.points[0].success)
        assertTrue(state.points[1].success)
        assertTrue(state.points[2].success)
    }

    @Test
    fun `journal entries are reverse chronological with status code`() {
        val reports = listOf(
            report(dateEpoch = 1_000L, completed = false, artifact = "A"),
            report(dateEpoch = 2_000L, completed = true, artifact = "B"),
        )
        val state = reports.toAnalyticsState()
        assertEquals(listOf(2_000L, 1_000L), state.entries.map { it.dateEpoch })
        assertEquals(listOf(1, 0), state.entries.map { it.statusCode })
    }

    @Test
    fun `completion rate matches successes over total`() {
        val reports = listOf(
            report(1L, true),
            report(2L, true),
            report(3L, false),
            report(4L, false),
        )
        val state = reports.toAnalyticsState()
        assertEquals(0.5f, state.completionRate, 0.0001f)
    }

    @Test
    fun `failure analysis flag is true when both fields present`() {
        val state = listOf(
            DailyReport(
                id = 1,
                dateEpoch = 1_000L,
                targetArtifact = "X",
                isCompleted = false,
                eliminatedWaste = 0,
                failureCause = "Не выделил время на ключевую задачу.",
                correctiveAction = "Блокировать утренние слоты в календаре.",
            ),
        ).toAnalyticsState()
        assertTrue(state.entries.single().hasFailureAnalysis)
    }

    private fun report(
        dateEpoch: Long,
        completed: Boolean,
        artifact: String = "art",
    ) = DailyReport(
        id = dateEpoch,
        dateEpoch = dateEpoch,
        targetArtifact = artifact,
        isCompleted = completed,
        eliminatedWaste = 0,
        failureCause = null,
        correctiveAction = null,
    )
}

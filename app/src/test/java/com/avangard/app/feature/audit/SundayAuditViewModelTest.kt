package com.avangard.app.feature.audit

import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeSessionRepository
import com.avangard.app.core.domain.model.Bottleneck
import com.avangard.app.core.domain.usecase.ObserveDailySessionUseCase
import com.avangard.app.core.domain.usecase.SetBottleneckUseCase
import com.avangard.app.core.domain.usecase.SundayAuditUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SundayAuditViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeSessionRepository
    private lateinit var clock: FakeClock
    private lateinit var viewModel: SundayAuditViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        clock = FakeClock()
        repository = FakeSessionRepository(clock)
        viewModel = SundayAuditViewModel(
            audit = SundayAuditUseCase(repository, clock),
            observeSession = ObserveDailySessionUseCase(repository),
            setBottleneck = SetBottleneckUseCase(repository, clock),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit blocked until a bottleneck is picked`() = runTest(dispatcher) {
        val initial = viewModel.state.first()
        assertFalse(initial.canSubmit)
        viewModel.onPickBottleneck(Bottleneck.PromptDiscipline)
        val picked = viewModel.state.first()
        assertEquals(Bottleneck.PromptDiscipline, picked.selectedBottleneck)
        assertTrue(picked.canSubmit)
    }

    @Test
    fun `submit persists bottleneck to today's session row`() = runTest(dispatcher) {
        viewModel.onPickBottleneck(Bottleneck.SocialComparison)
        viewModel.submit()
        advanceUntilIdle()
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val stored = repository.findForDate(today)!!
        assertEquals(Bottleneck.SocialComparison, stored.bottleneckForNextWeek)
    }

    @Test
    fun `state flips to completed after submit and is sealed against re-submit`() =
        runTest(dispatcher) {
            viewModel.onPickBottleneck(Bottleneck.PromptDiscipline)
            viewModel.submit()
            advanceUntilIdle()

            val sealed = viewModel.state.first { it.isCompleted }
            assertEquals(Bottleneck.PromptDiscipline, sealed.fixatedBottleneck)
            assertFalse("completed audit must not allow re-submit", sealed.canSubmit)

            // Attempting to flip the pick is a no-op once sealed; the picker
            // is also hidden in the UI but defensively guard the callback.
            viewModel.onPickBottleneck(Bottleneck.ScheduleHygiene)
            advanceUntilIdle()
            val afterAttempt = viewModel.state.first()
            assertEquals(Bottleneck.PromptDiscipline, afterAttempt.fixatedBottleneck)
        }
}

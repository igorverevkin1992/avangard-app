package com.avangard.app.feature.closing

import app.cash.turbine.test
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeSessionRepository
import com.avangard.app.core.domain.model.DefectKind
import com.avangard.app.core.domain.model.CoreMode
import com.avangard.app.core.domain.usecase.CloseEveningUseCase
import com.avangard.app.core.domain.usecase.ObserveDailySessionUseCase
import com.avangard.app.core.domain.usecase.SetJournalUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class EveningCloseViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeSessionRepository
    private lateinit var clock: FakeClock
    private lateinit var viewModel: EveningCloseViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        clock = FakeClock()
        repository = FakeSessionRepository(clock)
        viewModel = EveningCloseViewModel(
            observeSession = ObserveDailySessionUseCase(repository),
            closeEvening = CloseEveningUseCase(repository, clock),
            setJournal = SetJournalUseCase(repository, clock),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit blocked while Idle and defect kind not picked`() = runTest(dispatcher) {
        assertTrue(viewModel.state.value.needsDefectKind)
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onDefectKindChange(DefectKind.Waste)
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun `submit closes evening and emits Closed effect`() = runTest(dispatcher) {
        viewModel.onDefectKindChange(DefectKind.Defect)
        viewModel.effects.test {
            viewModel.submit()
            val effect = awaitItem()
            assertTrue(effect is EveningCloseEffect.Closed)
        }
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val stored = repository.findForDate(today)!!
        assertTrue(stored.eveningClosed)
    }

    @Test
    fun `Approved core does not require defect kind`() = runTest(dispatcher) {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.approveCore(today, "Шот", CoreMode.Standard, clock.nowEpochMillis())
        // Allow observeSession to flush the Approved state into the viewmodel.
        advanceUntilIdle()
        assertFalse(viewModel.state.value.needsDefectKind)
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun `virtues bind into state and clamp to range`() = runTest(dispatcher) {
        viewModel.onVirtueChange(Virtue.Rationality, 1)
        viewModel.onVirtueChange(Virtue.Independence, -1)
        viewModel.onVirtueChange(Virtue.Honesty, 99) // clamps
        viewModel.onVirtueChange(Virtue.Justice, -42) // clamps
        val s = viewModel.state.value
        assertEquals(1, s.rationality)
        assertEquals(-1, s.independence)
        assertEquals(1, s.honesty)
        assertEquals(-1, s.justice)
    }

    @Test
    fun `journal draft is clipped at 500 chars and never reports over-limit`() =
        runTest(dispatcher) {
            val payload = "x".repeat(800)
            viewModel.onJournalChange(payload)
            val s = viewModel.state.value
            assertEquals(500, s.journalCharCount)
            assertFalse("clipped draft must not be over-limit", s.journalOverLimit)
        }

    @Test
    fun `submit persists the journal alongside closing the shift`() =
        runTest(dispatcher) {
            // Idle core path: defect kind required for close.
            viewModel.onDefectKindChange(DefectKind.Defect)
            viewModel.onJournalChange("Сосредоточился. Брак — техника.")
            viewModel.submit()
            advanceUntilIdle()

            val today = clock.today().toStartOfDayEpoch(clock.zone())
            val stored = repository.findForDate(today)!!
            assertEquals("Сосредоточился. Брак — техника.", stored.journalEntry)
            assertTrue(stored.eveningClosed)
        }
}

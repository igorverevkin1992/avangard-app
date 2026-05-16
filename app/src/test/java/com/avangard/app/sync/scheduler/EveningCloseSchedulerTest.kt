package com.avangard.app.sync.scheduler

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.data.UserPreferences
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeSessionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Behavioural cover for the three trigger-time branches added in v3.4:
 *  - now < target → today's slot.
 *  - now ≥ target, shift unclosed → fire-immediately buffer.
 *  - now ≥ target, shift already closed → tomorrow's slot.
 */
@RunWith(RobolectricTestRunner::class)
class EveningCloseSchedulerTest {

    private val clock = FakeClock(
        today = LocalDate.of(2026, 5, 7),
        time = LocalTime.of(8, 0),
    )
    private lateinit var sessions: FakeSessionRepository
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var scheduler: EveningCloseScheduler
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        alarmManager = app.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager
        sessions = FakeSessionRepository(clock)
        preferences = mockk(relaxed = true) {
            every { flow } returns MutableStateFlow(UserPreferences())
            coEvery { snapshot() } returns UserPreferences()
        }
        scheduler = EveningCloseScheduler(
            context = app,
            clock = clock,
            preferences = preferences,
            sessions = sessions,
        )
    }

    @Test
    fun `before target schedules today's slot`() = runBlocking {
        clock.time = LocalTime.of(8, 0)
        scheduler.ensureScheduled()

        val scheduled = shadowOf(alarmManager).nextScheduledAlarm!!
        val expected = clock.today().atTime(LocalTime.of(21, 0))
            .atZone(clock.zone()).toEpochSecond() * 1000L
        assertEquals(expected, scheduled.triggerAtTime)
    }

    @Test
    fun `after target with unclosed approved shift fires immediately`() = runBlocking {
        // Approve core so coreStatus is not Idle, and don't close the
        // evening. This is the post-boot "missed today" path.
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        sessions.approveCore(today, "Шот", clock.nowEpochMillis())
        clock.time = LocalTime.of(21, 5)

        scheduler.ensureScheduled()

        val scheduled = shadowOf(alarmManager).nextScheduledAlarm!!
        val now = clock.nowEpochMillis()
        // 5s fire-immediately buffer; allow generous tolerance.
        assertTrue(
            "trigger ${scheduled.triggerAtTime} must fall within now..now+15s",
            scheduled.triggerAtTime in now..(now + 15_000L),
        )
    }

    @Test
    fun `after target with idle core defers to tomorrow`() = runBlocking {
        // Shift never started today — no notification needed; just keep
        // the daily alarm armed for next day.
        clock.time = LocalTime.of(21, 5)

        scheduler.ensureScheduled()

        val scheduled = shadowOf(alarmManager).nextScheduledAlarm!!
        val expected = clock.today().plusDays(1).atTime(LocalTime.of(21, 0))
            .atZone(clock.zone()).toEpochSecond() * 1000L
        assertEquals(expected, scheduled.triggerAtTime)
    }

    @Test
    fun `after target with closed shift defers to tomorrow`() = runBlocking {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        sessions.approveCore(today, "Шот", clock.nowEpochMillis())
        sessions.closeEvening(
            dateEpoch = today,
            virtues = com.avangard.app.core.domain.model.VirtueScores(0, 0, 0, 0),
            defectKind = null,
            recordedAt = clock.nowEpochMillis(),
        )
        clock.time = LocalTime.of(21, 5)

        scheduler.ensureScheduled()

        val scheduled = shadowOf(alarmManager).nextScheduledAlarm!!
        val expected = clock.today().plusDays(1).atTime(LocalTime.of(21, 0))
            .atZone(clock.zone()).toEpochSecond() * 1000L
        assertEquals(expected, scheduled.triggerAtTime)
    }
}

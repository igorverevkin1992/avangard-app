package com.itermeum.app.feature.settings

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.core.data.PathRepository
import com.itermeum.app.core.data.ProfileRepository
import com.itermeum.app.core.data.StreakRepository
import com.itermeum.app.core.database.entity.TravelPeriodEntity
import java.time.LocalDate
import com.itermeum.app.core.database.entity.ProfileEntity
import com.itermeum.app.feature.onboarding.QrCodeImage
import com.itermeum.app.sync.alarm.WakeAlarmScheduler
import com.itermeum.app.sync.scheduler.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val reminders: ReminderScheduler,
    private val wake: WakeAlarmScheduler,
    private val path: PathRepository,
    private val streaks: StreakRepository,
) : ViewModel() {

    val travel: StateFlow<TravelPeriodEntity?> = path.observeActiveTravel().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Section 3.7: date + the word «КРЕЩЁН»; returns false when the confirmation does not match. */
    fun markBaptized(date: LocalDate, word: String): Boolean {
        if (word.trim().uppercase() !in setOf("КРЕЩЁН", "КРЕЩЕН")) return false
        viewModelScope.launch { path.markBaptized(date); streaks.checkMilestones(); reminders.rescheduleAll() }
        return true
    }

    fun startTravel() = viewModelScope.launch { path.startTravel(manual = true) }
    fun endTravel() = viewModelScope.launch { path.endTravel() }

    val profile: StateFlow<ProfileEntity> = profiles.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileEntity())

    /** Every profile change re-arms the alarms (section 5.1: «смене настроек»). */
    fun update(transform: (ProfileEntity) -> ProfileEntity) {
        viewModelScope.launch {
            profiles.update(transform)
            reminders.rescheduleAll()
        }
    }

    fun qrBitmap(): Bitmap? = profile.value.wakeQrUuid?.let { QrCodeImage.bitmap(QrCodeImage.payload(it)) }

    fun testAlarm() = wake.scheduleTest(2)
}

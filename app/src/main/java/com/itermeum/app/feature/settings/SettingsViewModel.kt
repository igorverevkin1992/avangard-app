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
import com.itermeum.app.core.data.NormRepository
import com.itermeum.app.core.database.entity.NormEntity
import com.itermeum.app.core.designsystem.UiPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
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
    private val norms: NormRepository,
    private val uiPrefs: UiPrefs,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    /** Section 5.17: every norm can be switched off (except the core) and given its own time. */
    val normList: StateFlow<List<NormEntity>> = norms.observeNorms().map { list -> list.sortedBy { it.orderIndex } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun setNormEnabled(id: String, enabled: Boolean) = viewModelScope.launch { norms.setEnabled(id, enabled); reminders.rescheduleAll() }
    fun setNormTime(id: String, time: java.time.LocalTime?) = viewModelScope.launch {
        norms.setUserTime(id, time?.let { it.hour * 60 + it.minute }); reminders.rescheduleAll()
    }

    val lightTheme: StateFlow<Boolean> = uiPrefs.light
    fun setLightTheme(light: Boolean) = uiPrefs.setLight(light)

    private val directBoot = com.itermeum.app.sync.alarm.DirectBootStore(appContext)
    val emergencyStop = kotlinx.coroutines.flow.MutableStateFlow(directBoot.emergencyStop)
    fun setEmergencyStop(on: Boolean) { directBoot.emergencyStop = on; emergencyStop.value = on }

    /** The wake QR is rendered off the main thread, once per uuid. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val qr: StateFlow<Bitmap?> = profiles.flow.map { it.wakeQrUuid }.distinctUntilChanged()
        .mapLatest { uuid -> uuid?.let { withContext(Dispatchers.Default) { QrCodeImage.bitmap(QrCodeImage.payload(it)) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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


    fun testAlarm() = wake.scheduleTest(2)
}

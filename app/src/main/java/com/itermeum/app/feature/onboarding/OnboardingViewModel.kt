package com.itermeum.app.feature.onboarding

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.core.data.ProfileRepository
import com.itermeum.app.core.database.entity.ProfileEntity
import com.itermeum.app.sync.alarm.WakeAlarmScheduler
import com.itermeum.app.sync.scheduler.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUi(
    val step: Int = 0,
    val name: String = "",
    val wakeTime: LocalTime = LocalTime.of(6, 30),
    val wakeTimeWeekend: LocalTime = LocalTime.of(7, 30),
    val bedTime: LocalTime = LocalTime.of(23, 0),
    val workStart: LocalTime = LocalTime.of(12, 0),
    val workEnd: LocalTime = LocalTime.of(20, 0),
    val massTime: LocalTime = LocalTime.of(19, 0),
    val massDay: Int = 6,
    val qrUuid: String = UUID.randomUUID().toString(),
    val qrSaved: Boolean = false,
    val qrTestPassed: Boolean = false,
    val outreachTarget: Int = 5,
    val testAlarmArmed: Boolean = false,
    val hyperOsCore: Boolean = false,
) {
    val qrPayload: String get() = QrCodeImage.payload(qrUuid)
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profiles: ProfileRepository,
    private val wake: WakeAlarmScheduler,
    private val reminders: ReminderScheduler,
) : ViewModel() {

    private val _ui = MutableStateFlow(OnboardingUi())
    val ui: StateFlow<OnboardingUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val p = profiles.get()
            _ui.update {
                it.copy(name = p.name, wakeTime = p.wakeTime, wakeTimeWeekend = p.wakeTimeWeekend, bedTime = p.bedTime,
                    workStart = p.workStart, workEnd = p.workEnd, massTime = p.massTime ?: it.massTime, massDay = p.massDay,
                    qrUuid = p.wakeQrUuid ?: it.qrUuid, outreachTarget = p.outreachTarget, hyperOsCore = HyperOsChecks.coreHealthy(context))
            }
        }
    }

    fun setName(v: String) = _ui.update { it.copy(name = v) }
    fun setWake(v: LocalTime) = _ui.update { it.copy(wakeTime = v) }
    fun setWakeWeekend(v: LocalTime) = _ui.update { it.copy(wakeTimeWeekend = v) }
    fun setBed(v: LocalTime) = _ui.update { it.copy(bedTime = v) }
    fun setWorkStart(v: LocalTime) = _ui.update { it.copy(workStart = v) }
    fun setWorkEnd(v: LocalTime) = _ui.update { it.copy(workEnd = v) }
    fun setMassTime(v: LocalTime) = _ui.update { it.copy(massTime = v) }
    fun setMassDay(v: Int) = _ui.update { it.copy(massDay = v) }
    fun setOutreachTarget(v: Int) = _ui.update { it.copy(outreachTarget = v.coerceIn(1, 20)) }
    fun refreshHyperOs() = _ui.update { it.copy(hyperOsCore = HyperOsChecks.coreHealthy(context)) }

    fun qrBitmap(): Bitmap = QrCodeImage.bitmap(_ui.value.qrPayload)

    fun saveQr(): Boolean {
        val ok = QrCodeImage.saveToGallery(context, qrBitmap())
        _ui.update { it.copy(qrSaved = ok) }
        return ok
    }

    fun onTestScan(payload: String): Boolean {
        val ok = payload.trim() == _ui.value.qrPayload
        if (ok) _ui.update { it.copy(qrTestPassed = true) }
        return ok
    }

    fun armTestAlarm() {
        viewModelScope.launch {
            persist(done = false)
            wake.scheduleTest(2)
            _ui.update { it.copy(testAlarmArmed = true) }
        }
    }

    fun next() = _ui.update { it.copy(step = (it.step + 1).coerceAtMost(LAST_STEP)) }
    fun back() = _ui.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            persist(done = true)
            reminders.rescheduleAll()
            onDone()
        }
    }

    private suspend fun persist(done: Boolean) {
        val s = _ui.value
        profiles.update { p: ProfileEntity ->
            p.copy(
                name = s.name.trim(), wakeTime = s.wakeTime, wakeTimeWeekend = s.wakeTimeWeekend, bedTime = s.bedTime,
                workStart = s.workStart, workEnd = s.workEnd, massTime = s.massTime, massDay = s.massDay,
                wakeQrUuid = s.qrUuid, outreachTarget = s.outreachTarget, onboardingDone = p.onboardingDone || done,
            )
        }
    }

    companion object {
        const val LAST_STEP = 8
    }
}

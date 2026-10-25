package com.itermeum.app.feature.settings

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.core.data.ProfileRepository
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
) : ViewModel() {

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

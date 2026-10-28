package com.itermeum.app.sync.alarm

import android.content.Context
import java.time.LocalDate

/**
 * Device-protected copy of what the wake alarm needs before the first
 * unlock after a reboot (section 10, LOCKED_BOOT_COMPLETED): the next
 * trigger, its logical date and the sound settings. Room is unavailable
 * until the user unlocks, so the alarm must not depend on it.
 */
class DirectBootStore(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("direct_boot_alarm", Context.MODE_PRIVATE)

    fun save(triggerAt: Long, date: LocalDate, sound: Int, volume: Int) {
        prefs.edit().putLong(KEY_AT, triggerAt).putString(KEY_DATE, date.toString()).putInt(KEY_SOUND, sound).putInt(KEY_VOLUME, volume).apply()
    }

    fun nextAt(): Long = prefs.getLong(KEY_AT, 0L)
    fun nextDate(): LocalDate? = prefs.getString(KEY_DATE, null)?.let(LocalDate::parse)
    fun sound(): Int = prefs.getInt(KEY_SOUND, 1)
    fun volume(): Int = prefs.getInt(KEY_VOLUME, 100)

    /** D-47: «Выключить без подтверждения» on the ring screen (for the testing period). */
    var emergencyStop: Boolean
        get() = prefs.getBoolean(KEY_EMERGENCY, true)
        set(value) { prefs.edit().putBoolean(KEY_EMERGENCY, value).apply() }

    private companion object {
        const val KEY_EMERGENCY = "emergency_stop"
        const val KEY_AT = "at"
        const val KEY_DATE = "date"
        const val KEY_SOUND = "sound"
        const val KEY_VOLUME = "volume"
    }
}

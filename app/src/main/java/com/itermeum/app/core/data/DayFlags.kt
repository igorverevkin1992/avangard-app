package com.itermeum.app.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small persistent markers between the day close and the screens:
 * - «Что сломалось?»: the fidelity streak was frozen or broken for a date
 *   (sections 5.12, 6.6), shown in the next Examen and cleared there;
 * - the last time-zone change, so a flight does not close a running day.
 */
@Singleton
class DayFlags @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("day_flags", Context.MODE_PRIVATE)

    fun markWhatBroke(date: LocalDate) = prefs.edit().putString(KEY_BROKE, date.toString()).apply()
    fun whatBroke(date: LocalDate): Boolean = prefs.getString(KEY_BROKE, null) == date.toString()
    fun clearWhatBroke() = prefs.edit().remove(KEY_BROKE).apply()

    fun recordTimeZoneChange(at: Long) = prefs.edit().putLong(KEY_TZ, at).apply()
    fun lastTimeZoneChange(): Long = prefs.getLong(KEY_TZ, 0L)

    private companion object {
        const val KEY_BROKE = "what_broke"
        const val KEY_TZ = "tz_changed_at"
    }
}

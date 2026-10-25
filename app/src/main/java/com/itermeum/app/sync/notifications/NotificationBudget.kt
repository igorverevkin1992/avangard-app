package com.itermeum.app.sync.notifications

import android.content.Context
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.engine.EscalationEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Section 6.5 daily cap (8, one level-3) kept per logical date in SharedPreferences. */
@Singleton
class NotificationBudget @Inject constructor(
    @ApplicationContext context: Context,
    private val clock: Clock,
) {
    private val prefs = context.getSharedPreferences("notification_budget", Context.MODE_PRIVATE)

    private fun key(suffix: String) = "${clock.logicalToday()}_$suffix"

    fun current(): EscalationEngine.Budget = EscalationEngine.Budget(prefs.getInt(key("sent"), 0), prefs.getInt(key("l3"), 0))

    fun record(level: Int) {
        prefs.edit()
            .putInt(key("sent"), prefs.getInt(key("sent"), 0) + 1)
            .putInt(key("l3"), prefs.getInt(key("l3"), 0) + if (level == 3) 1 else 0)
            .apply()
    }

    /** Remembers which (norm, level) already fired today so re-arms never duplicate. */
    fun alreadySent(normId: String, level: Int): Boolean = prefs.getBoolean(key("$normId:$level"), false)
    fun markSent(normId: String, level: Int) = prefs.edit().putBoolean(key("$normId:$level"), true).apply()

    /** One-shot banner text for the Today screen (freeze / broken streak), consumed on read. */
    fun setBanner(text: String?) = prefs.edit().putString("banner", text).apply()
    fun takeBanner(): String? = prefs.getString("banner", null).also { if (it != null) prefs.edit().remove("banner").apply() }

    fun clearOld() {
        val today = clock.logicalToday().toString()
        prefs.all.keys.filter { !it.startsWith(today) && it != "banner" }.forEach { prefs.edit().remove(it).apply() }
    }
}

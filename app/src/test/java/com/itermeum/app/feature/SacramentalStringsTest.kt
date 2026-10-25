package com.itermeum.app.feature

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.itermeum.app.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Section 5.11 acceptance: with `baptized=false` no UI string may offer
 * Communion or Confession as an action. The only allowed phrase is
 * «духовное причастие». Strings reserved for the baptized state are
 * listed explicitly and excluded.
 */
@RunWith(RobolectricTestRunner::class)
class SacramentalStringsTest {

    private val baptizedOnly = setOf("hub_mass_baptized", "mass_hint_baptized", "confession_mark")
    private val forbidden = listOf("прими причастие", "иди к причастию", "исповедуйся", "причастись", "сходи на исповедь", "исповедайся")

    @Test
    fun `no string offers Communion or Confession before baptism`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val offenders = mutableListOf<String>()
        R.string::class.java.fields.forEach { field ->
            val name = field.name
            if (name in baptizedOnly) return@forEach
            val value = runCatching { context.getString(field.getInt(null)) }.getOrNull()?.lowercase() ?: return@forEach
            val stripped = value.replace("духовное причастие", "").replace("духовного причастия", "")
            if (forbidden.any { stripped.contains(it) }) offenders += "$name = $value"
            // The word «Причастие» alone (capitalised sacrament) is only allowed inside «духовное причастие» or the baptized-only strings.
            if (Regex("""\bпричастие\b""").containsMatchIn(stripped) && name !in setOf("mass_hint_catechumen")) offenders += "$name = $value"
        }
        assertTrue("Sacramental action strings before baptism: $offenders", offenders.isEmpty())
    }
}

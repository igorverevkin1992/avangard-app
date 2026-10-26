package com.itermeum.app.core.designsystem

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Section 12: dark by default, light as an option. Device-local, not part of the backup. */
@Singleton
class UiPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
    private val _light = MutableStateFlow(prefs.getBoolean(KEY_LIGHT, false))
    val light: StateFlow<Boolean> = _light.asStateFlow()

    fun setLight(value: Boolean) { prefs.edit().putBoolean(KEY_LIGHT, value).apply(); _light.value = value }

    private companion object { const val KEY_LIGHT = "light_theme" }
}

package com.itermeum.app.sync.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Refreshes the home-screen widget right after a mark, instead of waiting up to 30 minutes. */
@Singleton
class WidgetUpdater @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    fun requestUpdate() { scope.launch { DayWidget.refresh(context) } }
}

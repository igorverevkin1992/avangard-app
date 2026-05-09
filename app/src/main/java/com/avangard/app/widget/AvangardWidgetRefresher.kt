package com.avangard.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.avangard.app.core.domain.repository.WidgetRefresher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvangardWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetRefresher {
    override suspend fun refresh() {
        runCatching { AvangardWidget().updateAll(context) }
    }
}

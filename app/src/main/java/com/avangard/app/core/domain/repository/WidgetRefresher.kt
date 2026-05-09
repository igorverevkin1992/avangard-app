package com.avangard.app.core.domain.repository

/** Asks every home-screen widget instance to redraw with the latest report. */
fun interface WidgetRefresher {
    suspend fun refresh()
}

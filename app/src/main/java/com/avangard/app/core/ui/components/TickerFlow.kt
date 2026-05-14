package com.avangard.app.core.ui.components

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits the current epoch ms once per [intervalMs]. Cancellable by the collector.
 * Used by Compose viewmodels to drive 1-second live-timer displays without an Android handler.
 */
fun tickerFlow(intervalMs: Long = 1_000L): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(intervalMs)
    }
}

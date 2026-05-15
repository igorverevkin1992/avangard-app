package com.avangard.app.core.ui.components

import com.avangard.app.core.common.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits the current epoch ms once per [intervalMs] using the injected [clock].
 * Routing through Clock — instead of System.currentTimeMillis() — keeps the
 * pulpit timer deterministic in tests (FakeClock can advance the value).
 */
fun tickerFlow(clock: Clock, intervalMs: Long = 1_000L): Flow<Long> = flow {
    while (true) {
        emit(clock.nowEpochMillis())
        delay(intervalMs)
    }
}

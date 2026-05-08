package com.avangard.app.core.domain

import com.avangard.app.core.common.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class FakeClock(
    var today: LocalDate = LocalDate.of(2026, 5, 7),
    var time: LocalTime = LocalTime.of(7, 0),
    var zone: ZoneId = ZoneId.of("UTC"),
) : Clock {
    override fun nowEpochMillis(): Long =
        today.atTime(time).atZone(zone).toEpochSecond() * 1000L

    override fun today(): LocalDate = today
    override fun localTime(): LocalTime = time
    override fun zone(): ZoneId = zone
}

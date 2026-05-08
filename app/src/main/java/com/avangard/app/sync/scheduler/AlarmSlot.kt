package com.avangard.app.sync.scheduler

import java.time.LocalTime

enum class AlarmSlot(
    val id: Int,
    val triggerTime: LocalTime,
) {
    MorningInitialization(id = 1001, triggerTime = LocalTime.of(7, 0)),
    MidDayCheckpoint(id = 1002, triggerTime = LocalTime.of(13, 0)),
    EveningReport(id = 1003, triggerTime = LocalTime.of(20, 0));

    val notificationTextKey: String get() = when (this) {
        MorningInitialization -> "notification_artifact_capture"
        MidDayCheckpoint -> "notification_deadline_breach"
        EveningReport -> "notification_evening_due"
    }

    companion object {
        fun fromId(id: Int): AlarmSlot? = entries.firstOrNull { it.id == id }
    }
}

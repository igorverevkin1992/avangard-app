package com.avangard.app.core.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupBundleSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `round-trip preserves all fields`() {
        val bundle = BackupBundle(
            exportedAt = 1_700_000_000_000,
            dailySessions = listOf(
                BackupDailySession(
                    dateEpoch = 1_700_000_000_000,
                    mvdActive = 1,
                    coreStatus = 2,
                    corePrompt = "Шот",
                    coreAuthorizedAt = 1_700_000_100_000,
                    coreDefectKind = null,
                    infra02Status = 1,
                    infra03Status = 0,
                    infra04Status = 1,
                    infra05Status = 0,
                    eveningClosed = 1,
                    eveningClosedAt = 1_700_000_200_000,
                    virtRationality = 3,
                    virtIndependence = 2,
                    virtHonesty = 4,
                    virtJustice = 3,
                    bottleneckForNextWeek = "глубина расчёта",
                ),
            ),
            focusSessions = listOf(
                BackupFocusSession(
                    id = 7,
                    dateEpoch = 1_700_000_000_000,
                    habitCode = "01",
                    startedAt = 1_700_000_010_000,
                    endedAt = 1_700_000_080_000,
                ),
            ),
            habitLogs = listOf(
                BackupHabitLog(
                    dateEpoch = 1_700_000_000_000,
                    habitCode = "02",
                    completedAt = 1_700_000_050_000,
                ),
            ),
        )

        val encoded = json.encodeToString(BackupBundle.serializer(), bundle)
        val decoded = json.decodeFromString(BackupBundle.serializer(), encoded)

        assertEquals(bundle, decoded)
    }

    @Test
    fun `default schemaVersion equals SCHEMA_VERSION constant`() {
        val bundle = BackupBundle(
            exportedAt = 0L,
            dailySessions = emptyList(),
            focusSessions = emptyList(),
            habitLogs = emptyList(),
        )
        assertEquals(BackupBundle.SCHEMA_VERSION, bundle.schemaVersion)
    }

    @Test
    fun `unknown JSON keys are ignored on decode`() {
        val payload = """
            {
              "schemaVersion": 1,
              "exportedAt": 100,
              "futureField": "ignored",
              "dailySessions": [],
              "focusSessions": [],
              "habitLogs": []
            }
        """.trimIndent()
        val decoded = json.decodeFromString(BackupBundle.serializer(), payload)
        assertEquals(100L, decoded.exportedAt)
    }
}

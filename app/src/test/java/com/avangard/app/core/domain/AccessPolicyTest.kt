package com.avangard.app.core.domain

import com.avangard.app.core.domain.model.AccessPolicy
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessPolicyTest {

    @Test
    fun `Sunday unlocks history`() {
        // 2026-05-10 is a Sunday.
        assertTrue(AccessPolicy.isHistoryUnlocked(LocalDate.of(2026, 5, 10)))
    }

    @Test
    fun `Every other weekday blocks history`() {
        // 2026-05-04 Monday through 2026-05-09 Saturday.
        (4..9).forEach { day ->
            assertFalse(
                "expected ${LocalDate.of(2026, 5, day)} to be locked",
                AccessPolicy.isHistoryUnlocked(LocalDate.of(2026, 5, day)),
            )
        }
    }
}

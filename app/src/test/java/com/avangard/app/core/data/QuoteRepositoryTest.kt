package com.avangard.app.core.data

import androidx.test.core.app.ApplicationProvider
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.model.VirtueTag
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the bundled-asset path. Uses the real assets/library_quotes.json
 * via Robolectric's AssetManager — small, fast, and catches any future
 * schema drift between the JSON and Quote / QuoteBundle.
 */
@RunWith(RobolectricTestRunner::class)
class QuoteRepositoryTest {

    private lateinit var clock: FakeClock
    private lateinit var repository: QuoteRepository

    @Before
    fun setUp() {
        clock = FakeClock(today = LocalDate.of(2026, 5, 17))
        repository = QuoteRepository(
            context = ApplicationProvider.getApplicationContext(),
            clock = clock,
        )
    }

    @Test
    fun `bundle loads and exposes all 100 quotes`() = runTest {
        val quotes = repository.all()
        assertEquals(100, quotes.size)
        // Spot-check the first quote so any rename/strip silently lands in
        // the diff rather than passing. Catalog is Russian-only now.
        assertEquals(1, quotes.first().id)
        assertTrue(
            "first quote should be the productive-work passage in Russian",
            quotes.first().text.contains("Производительный труд", ignoreCase = true),
        )
    }

    @Test
    fun `every quote carries at least one virtue tag`() = runTest {
        val orphans = repository.all().filter { it.virtues.isEmpty() }
        assertTrue(
            "orphan quotes (no virtue tags): ${orphans.map { it.id }}",
            orphans.isEmpty(),
        )
    }

    @Test
    fun `byVirtue returns only quotes carrying the requested tag`() = runTest {
        VirtueTag.entries.forEach { virtue ->
            val filtered = repository.byVirtue(virtue)
            assertTrue(
                "byVirtue($virtue) must be non-empty for a balanced catalog",
                filtered.isNotEmpty(),
            )
            assertTrue(
                "all returned quotes must carry the tag",
                filtered.all { virtue in it.virtues },
            )
        }
    }

    @Test
    fun `byId returns the matching quote or null`() = runTest {
        val q = repository.byId(1)
        assertNotNull(q)
        assertEquals(1, q!!.id)
        assertNull(repository.byId(9_999))
    }

    @Test
    fun `quoteOfDay is deterministic for a given date and rolls on next day`() = runTest {
        clock.today = LocalDate.of(2026, 5, 17)
        val first = repository.quoteOfDay()
        val secondSameDay = repository.quoteOfDay()
        assertEquals(first?.id, secondSameDay?.id)

        clock.today = LocalDate.of(2026, 5, 18)
        val nextDay = repository.quoteOfDay()
        assertNotNull(nextDay)
        // Catalog has 100 quotes, so consecutive epoch-days hit different
        // indices (no wraparound collisions in a single test).
        assertTrue(
            "quote-of-day must change across consecutive days",
            first?.id != nextDay?.id,
        )
    }
}

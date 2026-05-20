package com.avangard.app.core.data

import android.content.Context
import android.util.Log
import com.avangard.app.core.common.Clock
import com.avangard.app.core.domain.model.Quote
import com.avangard.app.core.domain.model.QuoteBundle
import com.avangard.app.core.domain.model.VirtueTag
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json

/**
 * Loads the bundled Objectivism quote catalog from `assets/library_quotes.json`
 * once per process and serves the in-memory list to library + pulpit screens.
 *
 * Quote-of-the-day is deterministic by epoch-day → same quote all day,
 * predictable for tests, no rng noise.
 */
@Singleton
class QuoteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Volatile
    private var cached: List<Quote>? = null

    suspend fun all(): List<Quote> {
        cached?.let { return it }
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val loaded = loadFromAssets()
            cached = loaded
            loaded
        }
    }

    suspend fun byVirtue(virtue: VirtueTag): List<Quote> =
        all().filter { virtue in it.virtues }

    suspend fun byId(id: Int): Quote? = all().firstOrNull { it.id == id }

    /**
     * Same quote for the whole calendar day. Indexed deterministically by
     * the epoch-day modulo the catalog size, so two devices on the same
     * date see the same quote.
     */
    suspend fun quoteOfDay(): Quote? {
        val list = all()
        if (list.isEmpty()) return null
        val idx = (clock.today().toEpochDay().absoluteValue % list.size).toInt()
        return list[idx]
    }

    /**
     * Flow that re-emits whenever the calendar day rolls over. The pulpit
     * combine ticks every second; this flow polls quoteOfDay on the same
     * cadence and dedups via distinctUntilChanged so downstream sees one
     * emission per actual quote change.
     */
    fun quoteOfDayFlow(): Flow<Quote?> = flow {
        while (true) {
            emit(quoteOfDay())
            kotlinx.coroutines.delay(POLL_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.Default).distinctUntilChanged()

    private fun loadFromAssets(): List<Quote> = try {
        val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val bundle = json.decodeFromString<QuoteBundle>(text)
        bundle.quotes
    } catch (e: Throwable) {
        // Treat any failure as an empty catalog — the library shows a
        // graceful empty state, the pulpit quote-of-day panel hides.
        Log.e(LOG_TAG, "library_quotes.json load failed", e)
        emptyList()
    }

    private companion object {
        const val ASSET_PATH = "library_quotes.json"
        const val LOG_TAG = "QuoteRepository"
        // Quote-of-day only changes on date rollover; once per minute is
        // ample for the pulpit to flip the visible quote near midnight.
        const val POLL_INTERVAL_MS = 60_000L
    }
}

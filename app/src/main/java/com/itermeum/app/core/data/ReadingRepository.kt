package com.itermeum.app.core.data

import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.crypto.FieldCipher
import com.itermeum.app.core.database.dao.ReadingDao
import com.itermeum.app.core.database.entity.ReadingLogEntity
import com.itermeum.app.core.database.entity.ReadingPlanItemEntity
import com.itermeum.app.core.engine.ReadingProgram
import com.itermeum.app.core.liturgy.BibleRepository
import com.itermeum.app.core.liturgy.LiturgicalCalendar
import com.itermeum.app.core.liturgy.Passage
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Section 5.9: gospel of the day (Synodal), focus phrase, reading programme with a 10-minute timer. */
@Singleton
class ReadingRepository @Inject constructor(
    private val dao: ReadingDao,
    private val bible: BibleRepository,
    private val calendar: LiturgicalCalendar,
    private val profiles: ProfileRepository,
    private val norms: NormRepository,
    private val cipher: FieldCipher,
    private val clock: Clock,
) {
    suspend fun gospel(date: LocalDate): Passage? = calendar.day(date).gospelRef?.let { bible.passage(it) }

    suspend fun readingFor(date: LocalDate): List<ReadingPlanItemEntity> {
        val created = profiles.get().createdAt ?: date
        val max = dao.maxMonth() ?: 1
        return dao.planForMonth(ReadingProgram.monthIndex(created, date, max))
    }

    fun observeLogs(date: LocalDate): Flow<List<ReadingLogEntity>> = dao.observeForDate(date)

    suspend fun markGospelRead(date: LocalDate, phrase: String?) {
        dao.insertLog(ReadingLogEntity(logicalDate = date, bookId = GOSPEL_BOOK, minutes = 0, phraseEnc = phrase?.takeIf { it.isNotBlank() }?.let(cipher::encrypt)))
        norms.markDone(NormCatalog.GOSPEL, date)
    }

    suspend fun focusPhrase(date: LocalDate): String? =
        dao.forDate(date).lastOrNull { it.bookId == GOSPEL_BOOK && it.phraseEnc != null }?.phraseEnc?.let { runCatching { cipher.decrypt(it) }.getOrNull() }

    /** Counted at ≥8 minutes (section 4). */
    suspend fun logReading(date: LocalDate, bookId: String, minutes: Int) {
        dao.insertLog(ReadingLogEntity(logicalDate = date, bookId = bookId, minutes = minutes))
        if (minutes >= READING_MIN_MINUTES && !norms.isDone(NormCatalog.READING, date)) norms.markDone(NormCatalog.READING, date)
    }

    companion object {
        const val GOSPEL_BOOK = "gospel"
        const val READING_MIN_MINUTES = 8
        const val READING_TARGET_MINUTES = 10
    }
}

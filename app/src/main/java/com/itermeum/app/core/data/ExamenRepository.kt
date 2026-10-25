package com.itermeum.app.core.data

import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.common.LogicalDay
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.crypto.FieldCipher
import com.itermeum.app.core.database.dao.ExamenDao
import com.itermeum.app.core.database.entity.ExamenEntity
import com.itermeum.app.core.database.entity.FollowUpAnswer
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Section 5.12. Encrypted answers; window 20:00–02:59, plus «за вчера» for one hour after 03:00. */
@Singleton
class ExamenRepository @Inject constructor(
    private val dao: ExamenDao,
    private val cipher: FieldCipher,
    private val norms: NormRepository,
    private val clock: Clock,
) {
    enum class Answer { YES, PARTLY, NO }

    data class Audit(val answer: Answer, val note: String)

    data class Decrypted(
        val entity: ExamenEntity,
        val gratitude: List<String>,
        val audit: List<Audit>,
        val proposito: String?,
        val whatBroke: String?,
    )

    fun observe(date: LocalDate): Flow<ExamenEntity?> = dao.observe(date)
    suspend fun get(date: LocalDate): ExamenEntity? = dao.get(date)

    /** Which logical date an Examen started now belongs to, or null when outside the window. */
    fun targetDate(): LocalDate? {
        val now = clock.now()
        val t = now.toLocalTime()
        val today = LogicalDay.of(now)
        return when {
            !t.isBefore(WINDOW_START) || t.isBefore(LogicalDay.START) -> today
            t.isBefore(LogicalDay.START.plusHours(1)) -> today.minusDays(1) // 03:00–03:59: «за вчера»
            else -> null
        }
    }

    fun isOpenNow(): Boolean = targetDate() != null

    suspend fun previousProposito(before: LocalDate): Pair<LocalDate, String>? {
        val prev = dao.latestCompletedBefore(before) ?: return null
        val text = prev.propositoEnc?.let { runCatching { cipher.decrypt(it) }.getOrNull() } ?: return null
        return prev.logicalDate to text
    }

    suspend fun decrypt(e: ExamenEntity): Decrypted = Decrypted(
        entity = e,
        gratitude = e.gratitudeEnc?.let { runCatching { json.decodeFromString(ListSerializer(String.serializer()), cipher.decrypt(it)) }.getOrNull() } ?: emptyList(),
        audit = e.answersEnc?.let { runCatching { json.decodeFromString(ListSerializer(String.serializer()), cipher.decrypt(it)) }.getOrNull() }
            ?.map { s -> s.split("|", limit = 2).let { Audit(Answer.valueOf(it[0]), it.getOrElse(1) { "" }) } } ?: emptyList(),
        proposito = e.propositoEnc?.let { runCatching { cipher.decrypt(it) }.getOrNull() },
        whatBroke = e.whatBrokeEnc?.let { runCatching { cipher.decrypt(it) }.getOrNull() },
    )

    suspend fun saveDraft(date: LocalDate, gratitude: List<String>, audit: List<Audit>, contrition: Boolean, proposito: String?, previousFollowUp: FollowUpAnswer?, aveCount: Int, whatBroke: String?) {
        val existing = dao.get(date) ?: ExamenEntity(logicalDate = date, startedAt = clock.nowEpochMillis())
        dao.upsert(
            existing.copy(
                gratitudeEnc = cipher.encrypt(json.encodeToString(ListSerializer(String.serializer()), gratitude)),
                answersEnc = cipher.encrypt(json.encodeToString(ListSerializer(String.serializer()), audit.map { "${it.answer.name}|${it.note}" })),
                contritionSaid = contrition,
                propositoEnc = proposito?.let(cipher::encrypt),
                previousFollowUp = previousFollowUp,
                aveCount = aveCount,
                whatBrokeEnc = whatBroke?.takeIf { it.isNotBlank() }?.let(cipher::encrypt),
            ),
        )
    }

    /** Section 4: the norm counts when the wizard is completed and a propósito is recorded (invariant I4). */
    suspend fun complete(date: LocalDate) {
        val e = dao.get(date) ?: return
        require(!e.propositoEnc.isNullOrBlank()) { "proposito required" }
        dao.upsert(e.copy(completedAt = clock.nowEpochMillis()))
        norms.markDone(NormCatalog.EXAMEN, date)
    }

    suspend fun between(from: LocalDate, to: LocalDate) = dao.between(from, to)

    companion object {
        val WINDOW_START: LocalTime = LocalTime.of(20, 0)
        private val json = Json
    }
}

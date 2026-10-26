package com.itermeum.app.core.backup

import androidx.room.withTransaction
import com.itermeum.app.BuildConfig
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.ContentSeeder
import com.itermeum.app.core.crypto.FieldCipher
import com.itermeum.app.core.database.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton
import com.itermeum.app.core.database.entity.ProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Section 5.17 / 8: export decrypts `*Enc` columns in memory, serialises
 * the bundle and seals it with the backup password; import verifies the
 * format, re-encrypts with the local Keystore key and replaces all data
 * in one transaction. Content tables (prayer texts, jaculatoria, …) are
 * not part of the backup — they come from the content pack.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val db: AppDatabase,
    private val fieldCipher: FieldCipher,
    private val seeder: ContentSeeder,
    private val clock: Clock,
) {
    private val json = Json { serializersModule = backupSerializersModule; ignoreUnknownKeys = true; encodeDefaults = true }

    sealed interface ImportError {
        data object WrongFormat : ImportError
        data object WrongPassword : ImportError
        data class Unsupported(val version: Int) : ImportError
    }

    /** A row the Keystore can no longer read: the export is aborted rather than silently emptied. */
    class UndecryptableException(cause: Throwable) : Exception("encrypted field could not be decrypted", cause)

    private fun dec(v: String?): String? = v?.let { runCatching { fieldCipher.decrypt(it) }.getOrElse { e -> throw UndecryptableException(e) } }
    private fun enc(v: String?): String? = v?.let { fieldCipher.encrypt(it) }

    suspend fun snapshot(): BackupBundle {
        val b = db.backupDao()
        val profile = db.profileDao().get()
        return BackupBundle(
            createdAt = clock.nowEpochMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            contentPackVersion = profile?.contentPackVersion ?: 0,
            schemaVersion = AppDatabase.SCHEMA_VERSION,
            tables = BackupBundle.Tables(
                profile = profile,
                norm = b.norms(),
                normCompletion = b.completions(),
                dayPlan = b.dayPlans(),
                workTask = b.tasks(),
                outreachLog = b.outreach(),
                scriptSession = b.scripts(),
                transitionRitual = b.rituals(),
                examen = b.examens().map { it.copy(gratitudeEnc = dec(it.gratitudeEnc), answersEnc = dec(it.answersEnc), propositoEnc = dec(it.propositoEnc), whatBrokeEnc = dec(it.whatBrokeEnc)) },
                prayerUserText = b.editedPrayers().associate { it.id to (it.userEditedText ?: "") },
                memorization = b.memorization(),
                jaculatoriaLog = b.jaculatoriaLogs(),
                readingLog = b.readingLogs().map { it.copy(phraseEnc = dec(it.phraseEnc)) },
                rosaryProgress = b.rosary(),
                formationEvent = b.formation(),
                catechesisNote = b.notes(),
                streakState = b.streaks(),
                freezeLedger = b.freezes(),
                milestone = b.milestones(),
                project = b.projects().map { it.copy(reflectionEnc = dec(it.reflectionEnc)) },
                quarterGoal = b.goals(),
                quarterIncome = b.income(),
                vanityCheck = b.vanity().map { it.copy(answersEnc = dec(it.answersEnc) ?: "") },
                weeklyReview = b.reviews().map { it.copy(planEnc = dec(it.planEnc)) },
                mortificationLog = b.mortifications(),
                travelPeriod = b.travel(),
                morningState = b.mornings(),
                monthlySummaryView = b.monthViews(),
            ),
        )
    }

    fun encode(bundle: BackupBundle): ByteArray = json.encodeToString(BackupBundle.serializer(), bundle).toByteArray(Charsets.UTF_8)

    /** PBKDF2 (210 000 rounds) and Keystore work never run on the main thread. */
    suspend fun export(password: CharArray): ByteArray = withContext(Dispatchers.Default) { BackupCipher.encrypt(encode(snapshot()), password) }

    fun decode(bytes: ByteArray): BackupBundle = json.decodeFromString(BackupBundle.serializer(), String(bytes, Charsets.UTF_8))

    suspend fun import(file: ByteArray, password: CharArray): ImportError? = withContext(Dispatchers.Default) {
        if (!BackupCipher.isBackup(file)) return@withContext ImportError.WrongFormat
        val plain = try { BackupCipher.decrypt(file, password) } catch (e: Exception) { return@withContext ImportError.WrongPassword }
        // Read the header first: a copy from a newer app is «unsupported», not «not a copy».
        val header = try { json.parseToJsonElement(String(plain, Charsets.UTF_8)).jsonObject } catch (e: Exception) { return@withContext ImportError.WrongFormat }
        if (header["format"]?.jsonPrimitive?.content != BackupBundle.FORMAT) return@withContext ImportError.WrongFormat
        val version = header["version"]?.jsonPrimitive?.intOrNull ?: return@withContext ImportError.WrongFormat
        if (version != 1) return@withContext ImportError.Unsupported(version)
        val schema = header["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
        if (schema > AppDatabase.SCHEMA_VERSION) return@withContext ImportError.Unsupported(schema)
        val bundle = try { decode(plain) } catch (e: Exception) { return@withContext ImportError.WrongFormat }
        restore(bundle)
        null
    }

    suspend fun restore(bundle: BackupBundle) {
        val t = bundle.tables
        db.withTransaction {
            val b = db.backupDao()
            b.wipeCompletions(); b.wipeDayPlans(); b.wipeTasks(); b.wipeOutreach(); b.wipeScripts(); b.wipeRituals(); b.wipeExamens()
            b.wipePrayerEdits(); b.wipeMemorization(); b.wipeJaculatoriaLogs(); b.wipeReadingLogs(); b.wipeRosary(); b.wipeFormation()
            b.wipeNotes(); b.wipeStreaks(); b.wipeFreezes(); b.wipeMilestones(); b.wipeProjects(); b.wipeGoals(); b.wipeIncome()
            b.wipeVanity(); b.wipeReviews(); b.wipeMortifications(); b.wipeTravel(); b.wipeMornings(); b.wipeMonthViews()
            // Norms come back from the copy; catalog norms it lacks are re-seeded below.
            b.wipeNorms()

            db.profileDao().upsert(t.profile ?: ProfileEntity())
            b.insertNorms(t.norm)
            b.insertCompletions(t.normCompletion)
            b.insertDayPlans(t.dayPlan)
            b.insertTasks(t.workTask)
            b.insertOutreach(t.outreachLog)
            b.insertScripts(t.scriptSession)
            b.insertRituals(t.transitionRitual)
            b.insertExamens(t.examen.map { it.copy(gratitudeEnc = enc(it.gratitudeEnc), answersEnc = enc(it.answersEnc), propositoEnc = enc(it.propositoEnc), whatBrokeEnc = enc(it.whatBrokeEnc)) })
            t.prayerUserText.forEach { (id, text) -> b.setPrayerEdit(id, text) }
            b.insertMemorization(t.memorization)
            b.insertJaculatoriaLogs(t.jaculatoriaLog)
            b.insertReadingLogs(t.readingLog.map { it.copy(phraseEnc = enc(it.phraseEnc)) })
            b.insertRosary(t.rosaryProgress)
            b.insertFormation(t.formationEvent)
            b.insertNotes(t.catechesisNote)
            b.insertStreaks(t.streakState)
            b.insertFreezes(t.freezeLedger)
            b.insertMilestones(t.milestone)
            b.insertProjects(t.project.map { it.copy(reflectionEnc = enc(it.reflectionEnc)) })
            b.insertGoals(t.quarterGoal)
            b.insertIncome(t.quarterIncome)
            b.insertVanity(t.vanityCheck.map { it.copy(answersEnc = enc(it.answersEnc) ?: "") })
            b.insertReviews(t.weeklyReview.map { it.copy(planEnc = enc(it.planEnc)) })
            b.insertMortifications(t.mortificationLog)
            b.insertTravel(t.travelPeriod)
            b.insertMornings(t.morningState)
            b.insertMonthViews(t.monthlySummaryView)
            // Same transaction: a process death can never leave a half-restored database.
            seeder.seedIfNeeded(force = true)
        }
    }

    /** Settings → «стереть всё»: wipes user data, keeps the content pack. */
    suspend fun wipeAll() {
        // Empty bundle: profile reset and re-seed happen inside restore's transaction.
        restore(BackupBundle(createdAt = 0, appVersion = BuildConfig.VERSION_NAME, contentPackVersion = 0, tables = emptyTables()))
    }

    private fun emptyTables() = BackupBundle.Tables(
        null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
    )
}

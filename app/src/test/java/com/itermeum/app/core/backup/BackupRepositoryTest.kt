package com.itermeum.app.core.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.ContentAssets
import com.itermeum.app.core.content.ContentSeeder
import com.itermeum.app.core.crypto.InMemoryFieldCipher
import com.itermeum.app.core.database.AppDatabase
import com.itermeum.app.core.database.entity.ExamenEntity
import com.itermeum.app.core.database.entity.NormCompletionEntity
import com.itermeum.app.core.database.entity.OutreachKind
import com.itermeum.app.core.database.entity.OutreachLogEntity
import com.itermeum.app.core.database.entity.OutreachOutcome
import com.itermeum.app.core.database.entity.ProfileEntity
import com.itermeum.app.core.database.entity.StreakStateEntity
import com.itermeum.app.core.database.entity.StreakType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Phase 9 acceptance: backup → wipe → restore = identical data. */
@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: BackupRepository
    private val clock = object : Clock {
        override fun nowEpochMillis() = 1_800_000_000_000L
        override fun zone(): ZoneId = ZoneId.of("Europe/Moscow")
    }
    private val cipher = InMemoryFieldCipher(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val seeder = ContentSeeder(db, ContentAssets(context, Json { ignoreUnknownKeys = true }))
        seeder.seedIfNeeded()
        repo = BackupRepository(db, cipher, seeder, clock)
        val d = LocalDate.of(2026, 11, 5)
        db.profileDao().upsert(ProfileEntity(name = "Игорь", wakeTime = LocalTime.of(6, 45), baptized = false, onboardingDone = true, createdAt = d))
        db.completionDao().upsert(NormCompletionEntity(normId = "wake", logicalDate = d, completedAt = 1L))
        db.outreachDao().insert(OutreachLogEntity(logicalDate = d, at = 2L, personLabel = "Ирина", role = "героиня", kind = OutreachKind.CALL, outcome = OutreachOutcome.AGREEMENT, beforeCutoff = true))
        db.examenDao().upsert(ExamenEntity(logicalDate = d, propositoEnc = cipher.encrypt("Завтра до 13:00 позвоню трём героям"), completedAt = 3L, aveCount = 3))
        db.prayerDao().setUserText("our_father", "моя редакция")
        db.streakDao().upsert(StreakStateEntity(type = StreakType.FIDELITY, current = 4, best = 9, lastCountedDate = d, totalDays = 20))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `export, wipe, import restores identical data`() = runBlocking {
        val before = repo.snapshot()
        val file = repo.export("pw-12345678".toCharArray())

        repo.wipeAll()
        assertEquals(0, db.backupDao().completions().size)
        assertNull(db.prayerDao().get("our_father")!!.userEditedText)

        assertNull(repo.import(file, "pw-12345678".toCharArray()))
        val after = repo.snapshot()

        assertEquals(before.tables.profile, after.tables.profile)
        assertEquals(before.tables.normCompletion, after.tables.normCompletion)
        assertEquals(before.tables.outreachLog, after.tables.outreachLog)
        assertEquals(before.tables.streakState, after.tables.streakState)
        assertEquals(before.tables.prayerUserText, after.tables.prayerUserText)
        // Encrypted fields come back readable (ciphertext differs, plaintext equal).
        assertEquals("Завтра до 13:00 позвоню трём героям", after.tables.examen.single().propositoEnc)
        assertEquals("Завтра до 13:00 позвоню трём героям", cipher.decrypt(db.examenDao().get(LocalDate.of(2026, 11, 5))!!.propositoEnc!!))
    }

    @Test
    fun `wrong password and foreign file are reported`() = runBlocking {
        val file = repo.export("pw-12345678".toCharArray())
        assertEquals(BackupRepository.ImportError.WrongPassword, repo.import(file, "nope-nope".toCharArray()))
        assertEquals(BackupRepository.ImportError.WrongFormat, repo.import("{\"schemaVersion\":9}".toByteArray(), "x".toCharArray()))
    }
}

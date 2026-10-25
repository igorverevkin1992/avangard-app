package com.itermeum.app.core.content

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.itermeum.app.core.database.AppDatabase
import com.itermeum.app.core.database.entity.SrsStage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContentSeederTest {

    private lateinit var db: AppDatabase
    private lateinit var assets: ContentAssets
    private lateinit var seeder: ContentSeeder

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        assets = ContentAssets(context, Json { ignoreUnknownKeys = true })
        seeder = ContentSeeder(db, assets)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `seeding fills every content table`() = runBlocking {
        seeder.seedIfNeeded()
        assertEquals(23, db.normDao().count())
        assertEquals(21, db.prayerDao().count())
        assertEquals(48, db.prayerDao().allJaculatoria().size)
        assertEquals(18, db.prayerDao().asceticCount())
        assertEquals(14, db.srsDao().count())
        assertTrue(db.readingDao().planCount() >= 9)
        assertEquals(3, db.streakDao().all().size)
        assertEquals(16, db.streakDao().milestoneCount())
        assertEquals(assets.prayers.contentPackVersion, db.profileDao().get()!!.contentPackVersion)
    }

    @Test
    fun `every content element carries source and legal`() {
        assets.prayers.items.forEach { assertTrue(it.id, it.source.isNotBlank() && it.legal.isNotBlank()) }
        assets.jaculatoria.items.forEach { assertTrue(it.id, it.source.isNotBlank() && it.legal.isNotBlank()) }
        assets.ascetic.items.forEach { assertTrue(it.id, it.legal.isNotBlank()) }
        assertEquals(12, assets.jaculatoria.situations.size)
        assertEquals(5, assets.examenQuestions.items.size)
        assertEquals(4, assets.rosary.sets.size)
        assets.rosary.sets.values.forEach { assertEquals(5, it.mysteries.size) }
    }

    @Test
    fun `core norms are wake, frog and examen`() = runBlocking {
        seeder.seedIfNeeded()
        val core = db.normDao().getAll().filter { it.isCore }.map { it.id }.toSet()
        assertEquals(setOf(NormCatalog.WAKE, NormCatalog.FROG, NormCatalog.EXAMEN), core)
        assertTrue(db.normDao().get(NormCatalog.CONFESSION)!!.requiresBaptism)
    }

    @Test
    fun `reseeding keeps user edits and SRS progress`() = runBlocking {
        seeder.seedIfNeeded()
        db.prayerDao().setUserText("our_father", "моя редакция")
        db.normDao().setEnabled(NormCatalog.GYM, false)
        val item = db.srsDao().get("our_father")!!
        db.srsDao().update(item.copy(stage = SrsStage.RECALL, repetitions = 4))

        seeder.seedIfNeeded(force = true)

        assertEquals("моя редакция", db.prayerDao().get("our_father")!!.userEditedText)
        assertEquals(false, db.normDao().get(NormCatalog.GYM)!!.enabled)
        assertEquals(SrsStage.RECALL, db.srsDao().get("our_father")!!.stage)
        assertNotNull(db.prayerDao().get("sign_of_cross"))
        assertNull(db.prayerDao().get("preces"))
    }

    @Test
    fun `no prayer mentions sacramental communion or confession as an action`() {
        val forbidden = listOf("прими Причастие", "иди к Причастию", "исповедуйся")
        assets.prayers.items.forEach { p -> forbidden.forEach { assertTrue(p.id, !p.text.contains(it)) } }
        assets.notifications.items.values.flatMap { it.values }.forEach { t ->
            forbidden.forEach { assertTrue(t, !t.contains(it)) }
            listOf("огонёк", "котик", "сова").forEach { assertTrue(t, !t.lowercase().contains(it)) }
        }
    }
}

package com.itermeum.app.core.liturgy

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Section 11.2: 30 reference lookups against the bundled Synodal NT. Each
 * must resolve to a non-empty passage with the expected first verse.
 */
@RunWith(RobolectricTestRunner::class)
class GospelLookupTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val repo = BibleRepository(ApplicationProvider.getApplicationContext(), json)
    private val tables = LectionaryTables(ApplicationProvider.getApplicationContext(), json)

    private val fixtures = listOf(
        "Mt 24:37-44" to "но, как было во дни Ноя",
        "Mk 13:33-37" to "Смотрите, бодрствуйте, молитесь",
        "Lk 21:25-28,34-36" to "И будут знамения",
        "Jn 1:1-18" to "В начале было Слово",
        "Mt 2:1-12" to "Когда же Иисус родился в Вифлееме",
        "Mt 3:13-17" to "Тогда приходит Иисус из Галилеи",
        "Mt 4:1-11" to "Тогда Иисус возведен был Духом в пустыню",
        "Mk 1:12-15" to "Немедленно после того Дух ведет Его в пустыню",
        "Lk 4:1-13" to "Иисус, исполненный Духа Святаго",
        "Mt 6:1-6,16-18" to "Смотрите, не творите милостыни вашей пред людьми",
        "Jn 20:1-9" to "В первый же день недели Мария Магдалина",
        "Jn 20:19-23" to "В тот же первый день недели вечером",
        "Jn 15:26-27,16:12-15" to "Когда же приидет Утешитель",
        "Mt 28:16-20" to "Одиннадцать же учеников пошли в Галилею",
        "Jn 6:51-58" to "Я хлеб живый",
        "Mt 25:31-46" to "Когда же приидет Сын Человеческий во славе Своей",
        "Jn 18:33-37" to "Тогда Пилат опять вошел в преторию",
        "Lk 23:35-43" to "И стоял народ и смотрел",
        "Mk 1:14-20" to "После же того, как предан был Иоанн",
        "Mt 5:1-12" to "Увидев народ, Он взошел на гору",
        "Lk 4:16-30" to "И пришел в Назарет",
        "Lk 21:34-36" to "Смотрите же за собою",
        "Mt 26:14-27:66" to "Тогда один из двенадцати",
        "Lk 1:26-38" to "В шестой же месяц послан был Ангел Гавриил",
        "Lk 1:39-56" to "Встав же Мария во дни сии",
        "Mt 16:13-19" to "Придя же в страны Кесарии Филипповой",
        "Lk 5:1-11" to "Однажды, когда народ теснился к Нему",
        "Jn 3:5" to "Иисус отвечал: истинно, истинно говорю тебе",
        "Lk 1:1-4,4:14-21" to "Как уже многие начали составлять повествования",
        "Mt 1:16,18-21,24" to "Иаков родил Иосифа",
    )

    @Test
    fun `all 30 fixtures resolve to the expected first verse`() = runBlocking {
        assertEquals(30, fixtures.size)
        for ((ref, firstVerseStart) in fixtures) {
            val passage = repo.passage(ref)
            assertNotNull("no passage for $ref", passage)
            assertTrue(passage!!.verses.isNotEmpty())
            assertTrue(
                "$ref should start with «$firstVerseStart» but was «${passage.verses.first().text}»",
                passage.verses.first().text.startsWith(firstVerseStart),
            )
        }
    }

    @Test
    fun `cross-chapter passage spans both chapters`() = runBlocking {
        val p = repo.passage("Mt 26:14-27:66")!!
        assertEquals(26, p.verses.first().chapter)
        assertEquals(27, p.verses.last().chapter)
        assertEquals(66, p.verses.last().verse)
    }

    @Test
    fun `every lectionary reference parses and resolves`() = runBlocking {
        val refs = mutableListOf<String>()
        SundayCycle.entries.forEach { c -> tables.allSundaySlots(c).forEach { refs += tables.sunday(c, it)!! } }
        listOf("advent", "advent_dec", "christmas", "lent", "easter", "ordinary").forEach { t ->
            tables.allWeekdayKeys(t).forEach { refs += tables.weekday(t, it)!! }
        }
        tables.allSolemnityKeys().forEach { k -> SundayCycle.entries.forEach { c -> tables.solemnity(k, c)?.let(refs::add) } }
        assertTrue(refs.size > 500)
        refs.forEach { ref ->
            val p = repo.passage(ref)
            assertNotNull("unresolvable $ref", p)
            assertTrue("empty $ref", p!!.verses.isNotEmpty())
        }
    }

    @Test
    fun `tables have the expected shape`() {
        SundayCycle.entries.forEach { assertEquals(64, tables.allSundaySlots(it).size) }
        assertEquals(204, tables.allWeekdayKeys("ordinary").size)
        assertEquals(42, tables.allWeekdayKeys("easter").size)
        assertEquals(37, tables.allWeekdayKeys("lent").size)
        assertEquals("Mt 17:1-9", tables.solemnity("08-06", SundayCycle.A))
        assertEquals("Lk 1:26-38", tables.solemnity("12-08", SundayCycle.B))
    }
}

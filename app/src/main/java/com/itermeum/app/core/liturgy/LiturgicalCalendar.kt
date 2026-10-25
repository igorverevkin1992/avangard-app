package com.itermeum.app.core.liturgy

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Section 11.1. Pure date logic over [LectionaryTables]. Transfers: a
 * solemnity on a Sunday of Advent/Lent/Easter, in Holy Week or in the
 * Easter octave moves to the next free Monday; St Joseph on a Lenten
 * Sunday → Monday. Russia (D-10): Ascension and Corpus Christi are kept
 * on the following Sunday by default; the flag is a setting.
 */
@Singleton
class LiturgicalCalendar @Inject constructor(private val tables: LectionaryTables) {

    @Volatile var transferToSunday: Boolean = true

    private data class Year(
        val litYear: Int,
        val advent1: LocalDate,
        val christmas: LocalDate,
        val epiphany: LocalDate,
        val baptism: LocalDate,
        val ashWednesday: LocalDate,
        val easter: LocalDate,
        val ascension: LocalDate,
        val pentecost: LocalDate,
        val trinity: LocalDate,
        val corpusChristi: LocalDate,
        val sacredHeart: LocalDate,
        val nextAdvent1: LocalDate,
        val celebrations: Map<LocalDate, Celebration>,
    )

    private data class Celebration(
        val id: String,
        val title: String,
        val rank: Rank,
        val color: LiturgicalColor,
        /** Sunday-table slot (for Lord's solemnities) or null → solemnities.json by MM-DD id. */
        val sundaySlot: String? = null,
        val monthDay: String? = null,
    )

    private val cache = HashMap<Int, Year>()

    fun day(date: LocalDate): LiturgicalDay {
        val y = yearFor(date)
        val cycle = sundayCycle(y.litYear)
        val wcycle = if (y.litYear % 2 == 1) WeekdayCycle.I else WeekdayCycle.II
        val easter = y.easter
        val reginaCaeli = !date.isBefore(easter.minusDays(1)) && !date.isAfter(y.pentecost)

        val season: Season
        val weekOfSeason: Int
        when {
            date >= y.advent1 && date < y.christmas -> { season = Season.ADVENT; weekOfSeason = weeksSince(y.advent1, date) + 1 }
            date >= y.christmas && date <= y.baptism -> { season = Season.CHRISTMAS; weekOfSeason = 1 }
            date > y.baptism && date < y.ashWednesday -> { season = Season.ORDINARY_1; weekOfSeason = weeksSince(y.baptism, date) + 1 }
            date >= y.ashWednesday && date < easter.minusDays(3) -> {
                season = Season.LENT
                val lent1 = y.ashWednesday.plusDays(4)
                weekOfSeason = if (date < lent1) 0 else weeksSince(lent1, date) + 1
            }
            date >= easter.minusDays(3) && date < easter -> { season = Season.TRIDUUM; weekOfSeason = 6 }
            date >= easter && date <= y.pentecost -> { season = Season.EASTER; weekOfSeason = weeksSince(easter, date) + 1 }
            else -> {
                season = Season.ORDINARY_2
                weekOfSeason = 35 - weeksBetween(sundayOnOrBefore(date), y.nextAdvent1)
            }
        }
        val lentFriday = (season == Season.LENT || season == Season.TRIDUUM) && date.dayOfWeek == DayOfWeek.FRIDAY

        // 1. Triduum and Easter octave outrank everything.
        if (season == Season.TRIDUUM) {
            val (slot, title, color) = when (date) {
                easter.minusDays(3) -> Triple("HOLY_THURSDAY", "Великий Четверг. Месса Вечери Господней", LiturgicalColor.WHITE)
                easter.minusDays(2) -> Triple("GOOD_FRIDAY", "Страстная Пятница", LiturgicalColor.RED)
                else -> Triple("EASTER_VIGIL", "Великая Суббота. Навечерие Пасхи", LiturgicalColor.WHITE)
            }
            return build(date, season, Rank.SOLEMNITY, color, slot, title, cycle, wcycle, tables.sunday(cycle, slot), reginaCaeli, lentFriday, weekOfSeason)
        }
        if (date == easter) {
            return build(date, season, Rank.SOLEMNITY, LiturgicalColor.WHITE, "EASTER", "Воскресение Христово. Пасха", cycle, wcycle, tables.sunday(cycle, "EASTER"), reginaCaeli, false, 1)
        }
        if (date > easter && date < easter.plusDays(7)) {
            val d = date.dayOfWeek.value
            return build(date, season, Rank.SOLEMNITY, LiturgicalColor.WHITE, "EASTER_OCTAVE_$d", "Октава Пасхи. ${weekdayName(date)}", cycle, wcycle, tables.weekday("easter", "1-$d"), reginaCaeli, false, 1)
        }

        // 2. Celebrations (solemnities, feasts, proper memorials) incl. transfers.
        y.celebrations[date]?.let { c ->
            val ref = c.sundaySlot?.let { tables.sunday(cycle, it) } ?: c.monthDay?.let { tables.solemnity(it, cycle) }
            return build(date, season, c.rank, c.color, c.id, c.title, cycle, wcycle, ref, reginaCaeli, lentFriday, weekOfSeason)
        }

        // 3. Sundays.
        if (date.dayOfWeek == DayOfWeek.SUNDAY) {
            val (slot, title, color) = sundaySlot(y, date, season, weekOfSeason)
            val ref = tables.sunday(cycle, slot) ?: if (slot == "CHRISTMAS2") SECOND_SUNDAY_AFTER_CHRISTMAS else null
            return build(date, season, Rank.SUNDAY, color, slot, title, cycle, wcycle, ref, reginaCaeli, lentFriday, weekOfSeason)
        }

        // 4. Weekdays.
        val d = date.dayOfWeek.value
        val mmdd = monthDay(date)
        val (ref, title) = when (season) {
            Season.ADVENT -> if (date.monthValue == 12 && date.dayOfMonth >= 17) tables.weekday("advent_dec", mmdd) to "$mmdd · Адвент, предпразднство Рождества"
            else tables.weekday("advent", "$weekOfSeason-$d") to "${weekdayName(date)} $weekOfSeason недели Адвента"
            Season.CHRISTMAS -> if (date > y.epiphany) {
                val idx = ChronoUnit.DAYS.between(y.epiphany, date).toInt().coerceIn(1, 6)
                tables.weekday("christmas", "after_epiphany_$idx") to "${weekdayName(date)} после Богоявления"
            } else tables.weekday("christmas", mmdd) to "Рождественское время"
            Season.LENT -> tables.weekday("lent", "$weekOfSeason-$d") to when {
                date == y.ashWednesday -> "Пепельная среда"
                weekOfSeason == 0 -> "${weekdayName(date)} после Пепельной среды"
                else -> "${weekdayName(date)} $weekOfSeason недели Великого поста"
            }
            Season.EASTER -> tables.weekday("easter", "$weekOfSeason-$d") to "${weekdayName(date)} $weekOfSeason недели Пасхи"
            else -> tables.weekday("ordinary", "$weekOfSeason-$d") to "${weekdayName(date)} $weekOfSeason недели Рядового времени"
        }
        val color = when (season) {
            Season.ADVENT, Season.LENT -> LiturgicalColor.VIOLET
            Season.CHRISTMAS, Season.EASTER -> LiturgicalColor.WHITE
            else -> LiturgicalColor.GREEN
        }
        val id = if (date == y.ashWednesday) "ASH_WEDNESDAY" else null
        return build(date, season, Rank.WEEKDAY, color, id, title, cycle, wcycle, ref, reginaCaeli, lentFriday, weekOfSeason)
    }

    private fun sundaySlot(y: Year, date: LocalDate, season: Season, week: Int): Triple<String, String, LiturgicalColor> = when (season) {
        Season.ADVENT -> Triple("ADV$week", "${roman(week)} воскресенье Адвента", if (week == 3) LiturgicalColor.ROSE else LiturgicalColor.VIOLET)
        Season.CHRISTMAS ->
            if (date == y.baptism) Triple("BAPTISM", "Крещение Господне", LiturgicalColor.WHITE)
            else Triple("CHRISTMAS2", "II воскресенье по Рождестве", LiturgicalColor.WHITE)
        Season.LENT -> if (week == 6) Triple("PALM", "Вербное воскресенье", LiturgicalColor.RED)
        else Triple("LENT$week", "${roman(week)} воскресенье Великого поста", if (week == 4) LiturgicalColor.ROSE else LiturgicalColor.VIOLET)
        Season.EASTER -> when {
            date == y.pentecost -> Triple("PENTECOST", "Пятидесятница", LiturgicalColor.RED)
            else -> Triple("EASTER$week", "${roman(week)} воскресенье Пасхи", LiturgicalColor.WHITE)
        }
        else -> if (week == 34) Triple("OT34", "Христос, Царь Вселенной", LiturgicalColor.WHITE)
        else Triple("OT$week", "$week воскресенье Рядового времени", LiturgicalColor.GREEN)
    }

    private fun build(
        date: LocalDate, season: Season, rank: Rank, color: LiturgicalColor, id: String?, title: String,
        cycle: SundayCycle, wcycle: WeekdayCycle, ref: String?, regina: Boolean, lentFriday: Boolean, week: Int,
    ) = LiturgicalDay(date, season, rank, color, id, title, cycle, wcycle, ref, regina, lentFriday, week)

    // ---------------------------------------------------------------- year model

    private fun yearFor(date: LocalDate): Year {
        val adventThisYear = advent1(date.year)
        val litYear = if (date >= adventThisYear) date.year + 1 else date.year
        return cache.getOrPut(litYear) { computeYear(litYear) }
    }

    private fun computeYear(litYear: Int): Year {
        val advent1 = advent1(litYear - 1)
        val christmas = LocalDate.of(litYear - 1, 12, 25)
        val epiphany = LocalDate.of(litYear, 1, 6)
        val baptism = if (epiphany.dayOfWeek == DayOfWeek.SUNDAY) epiphany.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
        else epiphany.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
        val easter = Easter.of(litYear)
        val ash = easter.minusDays(46)
        val pentecost = easter.plusDays(49)
        val ascension = if (transferToSunday) easter.plusDays(42) else easter.plusDays(39)
        val trinity = easter.plusDays(56)
        val corpus = if (transferToSunday) easter.plusDays(63) else easter.plusDays(60)
        val sacredHeart = easter.plusDays(68)
        val nextAdvent1 = advent1(litYear)

        val cel = LinkedHashMap<LocalDate, Celebration>()
        fun put(date: LocalDate, c: Celebration) { cel[date] = c }

        // Lord's / movable solemnities from the Sunday table.
        put(christmas, Celebration("CHRISTMAS_DAY", "Рождество Христово", Rank.SOLEMNITY, LiturgicalColor.WHITE, sundaySlot = "CHRISTMAS_DAY"))
        put(LocalDate.of(litYear, 1, 1), Celebration("MARY_MOTHER_OF_GOD", "Пресвятая Богородица Мария", Rank.SOLEMNITY, LiturgicalColor.WHITE, sundaySlot = "MARY_MOTHER_OF_GOD"))
        put(epiphany, Celebration("EPIPHANY", "Богоявление", Rank.SOLEMNITY, LiturgicalColor.WHITE, sundaySlot = "EPIPHANY"))
        put(ascension, Celebration("ASCENSION", "Вознесение Господне", Rank.SOLEMNITY, LiturgicalColor.WHITE, sundaySlot = "ASCENSION"))
        put(trinity, Celebration("TRINITY", "Пресвятая Троица", Rank.SOLEMNITY, LiturgicalColor.WHITE, sundaySlot = "TRINITY"))
        put(corpus, Celebration("CORPUS_CHRISTI", "Пресвятые Тело и Кровь Христа", Rank.SOLEMNITY, LiturgicalColor.WHITE, sundaySlot = "CORPUS_CHRISTI"))
        put(sacredHeart, Celebration("SACRED_HEART", "Пресвятое Сердце Иисуса", Rank.SOLEMNITY, LiturgicalColor.WHITE, sundaySlot = "SACRED_HEART"))
        // Holy Family: Sunday in the octave, else Dec 30.
        val holyFamily = (26..31).map { LocalDate.of(litYear - 1, 12, it) }.firstOrNull { it.dayOfWeek == DayOfWeek.SUNDAY }
            ?: LocalDate.of(litYear - 1, 12, 30)
        put(holyFamily, Celebration("HOLY_FAMILY", "Святое Семейство", Rank.FEAST, LiturgicalColor.WHITE, sundaySlot = "HOLY_FAMILY"))

        // Fixed-date celebrations from solemnities.json with transfer rules.
        val fixed = fixedCelebrations()
        for ((mmdd, c) in fixed) {
            val (m, d) = mmdd.split("-").map { it.toInt() }
            val calYear = if (m == 12) litYear - 1 else litYear
            var date = LocalDate.of(calYear, m, d)
            if (date < advent1 || date >= nextAdvent1) continue
            if (c.rank == Rank.SOLEMNITY) {
                date = transferSolemnity(date, advent1, christmas, ash, easter, pentecost, cel.keys)
            } else {
                // Feasts yield to Sundays except feasts of the Lord in Ordinary Time;
                // all celebrations yield to privileged days already placed.
                val lordFeast = c.id in setOf("PRESENTATION", "TRANSFIGURATION", "HOLY_CROSS", "ALL_SOULS")
                val privileged = date in cel || inHolyWeekOrOctave(date, easter) || date == ash
                if (privileged) continue
                if (date.dayOfWeek == DayOfWeek.SUNDAY) {
                    val ordinary = (date > baptism && date < ash) || date > pentecost
                    if (!(lordFeast && ordinary)) continue
                }
                if (c.rank == Rank.MEMORIAL && (date >= ash && date < easter)) continue
            }
            if (date !in cel) put(date, c)
        }
        return Year(litYear, advent1, christmas, epiphany, baptism, ash, easter, ascension, pentecost, trinity, corpus, sacredHeart, nextAdvent1, cel)
    }

    private fun transferSolemnity(
        date: LocalDate, advent1: LocalDate, christmas: LocalDate, ash: LocalDate, easter: LocalDate, pentecost: LocalDate, taken: Set<LocalDate>,
    ): LocalDate {
        var d = date
        if (inHolyWeekOrOctave(d, easter)) d = easter.plusDays(8)
        while (true) {
            val privilegedSunday = d.dayOfWeek == DayOfWeek.SUNDAY &&
                ((d >= advent1 && d < christmas) || (d >= ash && d <= pentecost))
            if (!privilegedSunday && d !in taken && !inHolyWeekOrOctave(d, easter) && d != ash) return d
            d = d.plusDays(1)
        }
    }

    private fun inHolyWeekOrOctave(d: LocalDate, easter: LocalDate) = d >= easter.minusDays(7) && d <= easter.plusDays(7)

    private fun fixedCelebrations(): Map<String, Celebration> = linkedMapOf(
        "03-19" to Celebration("ST_JOSEPH", "Св. Иосиф, Обручник Пресвятой Девы Марии", Rank.SOLEMNITY, LiturgicalColor.WHITE, monthDay = "03-19"),
        "03-25" to Celebration("ANNUNCIATION", "Благовещение Господне", Rank.SOLEMNITY, LiturgicalColor.WHITE, monthDay = "03-25"),
        "06-24" to Celebration("JOHN_BAPTIST", "Рождество св. Иоанна Крестителя", Rank.SOLEMNITY, LiturgicalColor.WHITE, monthDay = "06-24"),
        "06-29" to Celebration("PETER_PAUL", "Свв. Апостолы Пётр и Павел", Rank.SOLEMNITY, LiturgicalColor.RED, monthDay = "06-29"),
        "08-15" to Celebration("ASSUMPTION", "Взятие Пресвятой Девы Марии в небесную славу", Rank.SOLEMNITY, LiturgicalColor.WHITE, monthDay = "08-15"),
        "11-01" to Celebration("ALL_SAINTS", "Все Святые", Rank.SOLEMNITY, LiturgicalColor.WHITE, monthDay = "11-01"),
        "12-08" to Celebration("IMMACULATE_CONCEPTION", "Непорочное Зачатие Пресвятой Девы Марии", Rank.SOLEMNITY, LiturgicalColor.WHITE, monthDay = "12-08"),
        "02-02" to Celebration("PRESENTATION", "Сретение Господне", Rank.FEAST, LiturgicalColor.WHITE, monthDay = "02-02"),
        "08-06" to Celebration("TRANSFIGURATION", "Преображение Господне", Rank.FEAST, LiturgicalColor.WHITE, monthDay = "08-06"),
        "09-14" to Celebration("HOLY_CROSS", "Воздвижение Святого Креста", Rank.FEAST, LiturgicalColor.RED, monthDay = "09-14"),
        "11-02" to Celebration("ALL_SOULS", "Поминовение всех усопших верных", Rank.FEAST, LiturgicalColor.VIOLET, monthDay = "11-02"),
        "11-09" to Celebration("LATERAN", "Освящение Латеранской базилики", Rank.FEAST, LiturgicalColor.WHITE, monthDay = "11-09"),
        "12-26" to Celebration("ST_STEPHEN", "Св. Стефан, первомученик", Rank.FEAST, LiturgicalColor.RED, monthDay = "12-26"),
        "12-27" to Celebration("ST_JOHN", "Св. Иоанн, апостол и евангелист", Rank.FEAST, LiturgicalColor.WHITE, monthDay = "12-27"),
        "12-28" to Celebration("HOLY_INNOCENTS", "Свв. Невинные Младенцы Вифлеемские", Rank.FEAST, LiturgicalColor.RED, monthDay = "12-28"),
        "01-25" to Celebration("CONVERSION_PAUL", "Обращение св. Апостола Павла", Rank.FEAST, LiturgicalColor.WHITE, monthDay = "01-25"),
        "02-22" to Celebration("CHAIR_PETER", "Кафедра св. Апостола Петра", Rank.FEAST, LiturgicalColor.WHITE, monthDay = "02-22"),
        "04-25" to Celebration("ST_MARK", "Св. Марк, евангелист", Rank.FEAST, LiturgicalColor.RED, monthDay = "04-25"),
        "05-03" to Celebration("PHILIP_JAMES", "Свв. Апостолы Филипп и Иаков", Rank.FEAST, LiturgicalColor.RED, monthDay = "05-03"),
        "05-31" to Celebration("VISITATION", "Посещение Пресвятой Девой Марией Елисаветы", Rank.FEAST, LiturgicalColor.WHITE, monthDay = "05-31"),
        "07-03" to Celebration("ST_THOMAS", "Св. Фома, апостол", Rank.FEAST, LiturgicalColor.RED, monthDay = "07-03"),
        "07-22" to Celebration("MARY_MAGDALENE", "Св. Мария Магдалина", Rank.FEAST, LiturgicalColor.WHITE, monthDay = "07-22"),
        "07-25" to Celebration("ST_JAMES", "Св. Иаков, апостол", Rank.FEAST, LiturgicalColor.RED, monthDay = "07-25"),
        "08-10" to Celebration("ST_LAWRENCE", "Св. Лаврентий, диакон и мученик", Rank.FEAST, LiturgicalColor.RED, monthDay = "08-10"),
        "08-24" to Celebration("ST_BARTHOLOMEW", "Св. Варфоломей, апостол", Rank.FEAST, LiturgicalColor.RED, monthDay = "08-24"),
        "09-21" to Celebration("ST_MATTHEW", "Св. Матфей, апостол и евангелист", Rank.FEAST, LiturgicalColor.RED, monthDay = "09-21"),
        "09-29" to Celebration("ARCHANGELS", "Свв. Архангелы Михаил, Гавриил и Рафаил", Rank.FEAST, LiturgicalColor.WHITE, monthDay = "09-29"),
        "10-18" to Celebration("ST_LUKE", "Св. Лука, евангелист", Rank.FEAST, LiturgicalColor.RED, monthDay = "10-18"),
        "10-28" to Celebration("SIMON_JUDE", "Свв. Апостолы Симон и Иуда", Rank.FEAST, LiturgicalColor.RED, monthDay = "10-28"),
        "11-30" to Celebration("ST_ANDREW", "Св. Андрей, апостол", Rank.FEAST, LiturgicalColor.RED, monthDay = "11-30"),
        "10-02" to Celebration("GUARDIAN_ANGELS", "Свв. Ангелы-хранители", Rank.MEMORIAL, LiturgicalColor.WHITE, monthDay = "10-02"),
        "06-26" to Celebration("ST_JOSEMARIA", "Св. Хосемария Эскрива де Балагер", Rank.MEMORIAL, LiturgicalColor.WHITE, monthDay = "06-26"),
    )

    // ---------------------------------------------------------------- helpers

    /** Advent 1 is the Sunday between Nov 27 and Dec 3 inclusive. */
    private fun advent1(calendarYear: Int): LocalDate =
        LocalDate.of(calendarYear, 11, 27).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    private fun sundayOnOrBefore(d: LocalDate) = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    private fun weeksSince(start: LocalDate, d: LocalDate): Int = weeksBetween(start, sundayOnOrBefore(d))
    private fun weeksBetween(a: LocalDate, b: LocalDate): Int = (ChronoUnit.DAYS.between(a, b) / 7).toInt()
    private fun monthDay(d: LocalDate) = "%02d-%02d".format(d.monthValue, d.dayOfMonth)

    fun sundayCycle(litYear: Int): SundayCycle = when (litYear % 3) { 1 -> SundayCycle.A; 2 -> SundayCycle.B; else -> SundayCycle.C }

    fun easter(year: Int): LocalDate = Easter.of(year)

    companion object {
        private const val SECOND_SUNDAY_AFTER_CHRISTMAS = "Jn 1:1-18"
        private val WEEKDAYS = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
        private val ROMAN = listOf("", "I", "II", "III", "IV", "V", "VI", "VII")
        fun weekdayName(d: LocalDate) = WEEKDAYS[d.dayOfWeek.value - 1]
        fun roman(n: Int) = ROMAN.getOrElse(n) { n.toString() }
    }
}

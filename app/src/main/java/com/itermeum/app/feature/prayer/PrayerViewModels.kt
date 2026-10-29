package com.itermeum.app.feature.prayer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.ContentAssets
import com.itermeum.app.core.content.ReadingItemJson
import com.itermeum.app.core.data.books.BookInfo
import com.itermeum.app.core.data.books.BookLibrary
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.data.NormRepository
import com.itermeum.app.core.data.PrayerRepository
import com.itermeum.app.core.data.ProfileRepository
import com.itermeum.app.core.data.ReadingRepository
import com.itermeum.app.core.data.RosaryRepository
import com.itermeum.app.core.data.SrsRepository
import com.itermeum.app.core.data.TimerController
import com.itermeum.app.core.database.entity.JaculatoriaEntity
import com.itermeum.app.core.database.entity.MemorizationItemEntity
import com.itermeum.app.core.database.entity.PrayerEntity
import com.itermeum.app.core.database.entity.ProfileEntity
import com.itermeum.app.core.database.entity.ReadingPlanItemEntity
import com.itermeum.app.core.engine.SrsEngine
import com.itermeum.app.core.liturgy.LiturgicalCalendar
import com.itermeum.app.core.liturgy.LiturgicalDay
import com.itermeum.app.core.liturgy.Passage
import com.itermeum.app.sync.service.SessionTimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Hub + book + Angelus + jaculatoria. */
@HiltViewModel
class PrayerHubViewModel @Inject constructor(
    private val prayers: PrayerRepository,
    private val norms: NormRepository,
    private val calendar: LiturgicalCalendar,
    private val assets: ContentAssets,
    private val srs: SrsRepository,
    profiles: ProfileRepository,
    private val clock: Clock,
) : ViewModel() {
    val date: LocalDate = clock.logicalToday()
    val liturgical: LiturgicalDay = calendar.day(date)
    val prayerList: StateFlow<List<PrayerEntity>> = prayers.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val done: StateFlow<Set<String>> = norms.observeDoneIds(date).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val jaculatoriaToday: StateFlow<Int> = prayers.observeJaculatoriaCount(date).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val dueCards: StateFlow<Int> = srs.observeDueCount(date).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val profile: StateFlow<ProfileEntity> = profiles.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileEntity())
    val situations = assets.jaculatoria.situations

    fun displayText(p: PrayerEntity) = prayers.displayText(p)
    fun observePrayer(id: String) = prayers.observe(id)
    fun saveUserText(id: String, text: String?) = viewModelScope.launch { prayers.setUserText(id, text) }
    fun observeJaculatoria(situation: String) = prayers.observeJaculatoria(situation)

    fun angelusPrayerId(): String = if (liturgical.reginaCaeli) "regina_caeli" else "angelus"

    /** Counted only 11:30–14:00 (section 5.7); returns false outside the window so the screen can say so. */
    fun angelusWindowOpen(): Boolean = clock.localTime().let { !it.isBefore(java.time.LocalTime.of(11, 30)) && it.isBefore(java.time.LocalTime.of(14, 0)) }
    fun markAngelus() = viewModelScope.launch {
        if (angelusWindowOpen()) norms.markDone(NormCatalog.ANGELUS, date)
    }

    fun markMarianSaturday() = viewModelScope.launch { norms.markDone(NormCatalog.MARIAN_SATURDAY, date) }

    /** Long press = «произнёс»; the third mark of the day completes the norm. */
    fun said(item: JaculatoriaEntity) = viewModelScope.launch {
        prayers.logJaculatoria(item, item.situation)
        if (prayers.jaculatoriaCount(date) >= 3 && !norms.isDone(NormCatalog.JACULATORIA, date)) norms.markDone(NormCatalog.JACULATORIA, date)
    }

    fun markMass() = viewModelScope.launch { norms.markDone(NormCatalog.MASS, date) }
    fun markConfession() = viewModelScope.launch { norms.markDone(NormCatalog.CONFESSION, date) }
}

/** Mental prayer (5.6) and reading (5.9) timers. */
@HiltViewModel
class TimerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timers: TimerController,
    private val norms: NormRepository,
    private val reading: ReadingRepository,
    private val clock: Clock,
    private val finisher: com.itermeum.app.core.data.TimerFinisher,
) : ViewModel() {
    val session: StateFlow<TimerController.Session?> = timers.session

    /** Section 5.6: the theme phase of mental prayer proposes the Gospel of the day. */
    val gospelTitle = MutableStateFlow<String?>(null)
    init { viewModelScope.launch { gospelTitle.value = runCatching { reading.gospel(clock.logicalToday())?.title }.getOrNull() } }

    fun startMental() { timers.start(TimerController.Kind.MENTAL_PRAYER, MENTAL_MS); SessionTimerService.start(context) }
    fun startReading(bookId: String) { timers.start(TimerController.Kind.READING, READING_MS, bookId); SessionTimerService.start(context) }

    /** Mental prayer counts at ≥80 % of the time; reading logs its minutes (≥8 counts) — see [TimerFinisher]. */
    fun stop() { viewModelScope.launch { finisher.finish() } }

    fun now() = clock.nowEpochMillis()

    companion object {
        const val MENTAL_MS = 10 * 60_000L
        const val READING_MS = 10 * 60_000L
    }
}

data class GospelUi(val passage: Passage? = null, val title: String = "", val focus: String? = null, val read: Boolean = false, val plan: List<ReadingPlanItemEntity> = emptyList(), val readingDone: Boolean = false)

@HiltViewModel
class GospelViewModel @Inject constructor(
    private val reading: ReadingRepository,
    private val norms: NormRepository,
    private val calendar: LiturgicalCalendar,
    private val clock: Clock,
    private val library: BookLibrary,
    assets: ContentAssets,
) : ViewModel() {
    /** Books loaded into the app (D-37). */
    val books: StateFlow<Map<String, BookInfo>> = library.books

    /** Every book of the programme once, to load files ahead of their month. */
    val programme: List<ReadingItemJson> = (assets.reading.always + assets.reading.months.flatMap { it.items }).distinctBy { it.bookId }

    /** The Bible and other always-present books, shown with each month's reading. */
    private val always: List<ReadingPlanItemEntity> = assets.reading.always.map {
        ReadingPlanItemEntity(id = "always_${it.bookId}", month = 0, bookId = it.bookId, title = it.title, sectionLabel = it.section, url = it.url)
    }

    /** The book whose file is being read now (a large PDF takes a while). */
    val importing = MutableStateFlow<String?>(null)
    /** The last import's result, shown under that book: bookId → message. */
    val importMessage = MutableStateFlow<Pair<String, String>?>(null)

    fun importBook(bookId: String, uri: Uri, ok: String, okPages: String, failed: String) = viewModelScope.launch {
        importing.value = bookId
        importMessage.value = null
        val result = library.import(bookId, uri)
        importing.value = null
        importMessage.value = bookId to result.fold({ (if (it.asPages) okPages else ok).format(it.fileName ?: it.title) }, { failed })
    }

    fun removeBook(bookId: String) = library.remove(bookId)

    val date: LocalDate = clock.logicalToday()
    private val _ui = MutableStateFlow(GospelUi())
    val ui: StateFlow<GospelUi> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val day = calendar.day(date)
        _ui.update {
            it.copy(passage = reading.gospel(date), title = day.title, focus = reading.focusPhrase(date),
                read = norms.isDone(NormCatalog.GOSPEL, date), plan = always + reading.readingFor(date), readingDone = norms.isDone(NormCatalog.READING, date))
        }
    }

    fun markRead(phrase: String?) = viewModelScope.launch { reading.markGospelRead(date, phrase); refresh() }
    fun logPaperReading(bookId: String) = viewModelScope.launch { reading.logReading(date, bookId, ReadingRepository.READING_TARGET_MINUTES); refresh() }
}

data class RosaryUi(val set: String = "JOYFUL", val title: String = "", val mysteries: List<String> = emptyList(), val contemplations: List<String> = emptyList(), val decadesDone: Int = 0, val verify: Boolean = true)

@HiltViewModel
class RosaryViewModel @Inject constructor(
    private val rosary: RosaryRepository,
    private val clock: Clock,
) : ViewModel() {
    val date: LocalDate = clock.logicalToday()
    private val _ui = MutableStateFlow(RosaryUi())
    val ui: StateFlow<RosaryUi> = _ui.asStateFlow()

    init {
        val set = rosary.setFor(date)
        val def = rosary.mysteries(set)
        _ui.value = RosaryUi(set, def?.title ?: "", def?.mysteries ?: emptyList(), rosary.contemplations(set), 0, rosary.verify)
        viewModelScope.launch { rosary.observe(date).collect { p -> _ui.update { it.copy(decadesDone = p?.decadesDone ?: 0) } } }
    }

    fun completeDecade(n: Int) = viewModelScope.launch { rosary.setDecades(date, n) }
}

data class SrsUi(val queue: List<MemorizationItemEntity> = emptyList(), val index: Int = 0, val prayer: PrayerEntity? = null, val revealed: Boolean = false, val startedAt: Long = 0, val finished: Boolean = false, val total: Int = 0, val known: Int = 0)

@HiltViewModel
class SrsViewModel @Inject constructor(
    private val srs: SrsRepository,
    private val prayers: PrayerRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _ui = MutableStateFlow(SrsUi(startedAt = clock.nowEpochMillis()))
    val ui: StateFlow<SrsUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val q = srs.queue()
            _ui.update { it.copy(queue = q, prayer = q.firstOrNull()?.let { p -> prayers.get(p.prayerId) }, finished = q.isEmpty(), total = srs.count(), known = srs.knownCount()) }
            if (q.isEmpty()) srs.markSessionDone()
        }
    }

    fun reveal() = _ui.update { it.copy(revealed = true) }

    fun grade(g: SrsEngine.Grade) = viewModelScope.launch {
        val s = _ui.value
        val item = s.queue.getOrNull(s.index) ?: return@launch
        srs.review(item, g)
        val next = s.index + 1
        val elapsedMin = (clock.nowEpochMillis() - s.startedAt) / 60_000L
        val finished = next >= s.queue.size || elapsedMin >= SrsEngine.SESSION_MAX_MINUTES
        if (finished) srs.markSessionDone()
        _ui.update { it.copy(index = next, prayer = s.queue.getOrNull(next)?.let { p -> prayers.get(p.prayerId) }, revealed = false, finished = finished, known = srs.knownCount()) }
    }

    fun displayText(p: PrayerEntity) = prayers.displayText(p)
    fun cloze(p: PrayerEntity, item: MemorizationItemEntity) = SrsEngine.cloze(prayers.displayText(p), item.stage, p.id)
}

package com.itermeum.app.feature.vow

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.R
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.ContentAssets
import com.itermeum.app.core.data.PrayerRepository
import com.itermeum.app.core.data.TimerController
import com.itermeum.app.core.data.TimerFinisher
import com.itermeum.app.core.data.VowKind
import com.itermeum.app.core.data.VowRepository
import com.itermeum.app.core.designsystem.ImCollapsible
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImMonoFamily
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import com.itermeum.app.core.designsystem.ImTimeField
import com.itermeum.app.sync.service.SessionTimerService
import com.itermeum.app.sync.vow.VowReminders
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VowViewModel @Inject constructor(
    savedState: androidx.lifecycle.SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val vows: VowRepository,
    private val reminders: VowReminders,
    private val prayers: PrayerRepository,
    private val assets: ContentAssets,
    private val timers: TimerController,
    private val finisher: TimerFinisher,
    private val clock: Clock,
) : ViewModel() {
    /** D-79: which vow this screen is about (`vow/{kind}`); the list screen has none. */
    val kind: VowKind = VowKind.of(savedState["kind"]) ?: VowKind.SOBRIETY
    val today: LocalDate = clock.logicalToday()
    val state: StateFlow<VowRepository.State?> = vows.observe(kind).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val all: StateFlow<List<VowRepository.State>> = vows.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** «Нет» was just answered: the Nunc coepi panel stays until dismissed. */
    private val _slip = MutableStateFlow(false)
    val slip: StateFlow<Boolean> = _slip.asStateFlow()
    fun dismissSlip() { _slip.value = false }

    fun take(intention: String, helperName: String, helperPhone: String, phrase: String) =
        viewModelScope.launch { vows.take(kind, intention, helperName, helperPhone, phrase) }

    fun updateDetails(intention: String, helperName: String, helperPhone: String, phrase: String) =
        viewModelScope.launch { vows.updateDetails(kind, intention, helperName, helperPhone, phrase) }

    fun release() = viewModelScope.launch { vows.release(kind); reminders.schedule() }

    fun answer(date: LocalDate, kept: Boolean) = viewModelScope.launch {
        vows.answer(kind, date, kept)
        if (!kept) _slip.value = true
    }

    fun setFeast(date: LocalDate, time: LocalTime?) = viewModelScope.launch { vows.setFeast(date, time); reminders.schedule() }

    // ── «Хочется выпить» ────────────────────────────────────────────────────

    val prayer = MutableStateFlow<Pair<String, String>?>(null)
    val jaculatories: List<Pair<String, String>> = assets.jaculatoria.items.filter { it.situation == kind.situation }.map { it.text to it.source }
    init { kind.prayerId?.let { id -> viewModelScope.launch { prayers.get(id)?.let { prayer.value = it.title to prayers.displayText(it) } } } }

    val session: StateFlow<TimerController.Session?> = timers.session
    /** The wait counted on the screen only — when a prayer or reading timer holds the one timer. */
    private val _localWait = MutableStateFlow<Long?>(null)
    val localWait: StateFlow<Long?> = _localWait.asStateFlow()

    /** False → the countdown stays on this screen (another timer runs). */
    fun startWait(onResult: (Boolean) -> Unit) = viewModelScope.launch {
        // A work focus is recorded first; a prayer or reading timer is not interrupted.
        if (timers.current()?.kind == TimerController.Kind.FOCUS) finisher.finish()
        val cur = timers.current()
        if (cur != null && cur.kind != TimerController.Kind.CRAVING) {
            _localWait.value = clock.nowEpochMillis(); onResult(false)
        } else {
            timers.start(TimerController.Kind.CRAVING, WAIT_MS, label = kind.id)
            SessionTimerService.start(context)
            onResult(true)
        }
    }

    fun stopWait() = viewModelScope.launch {
        _localWait.value = null
        if (timers.current()?.kind == TimerController.Kind.CRAVING) finisher.finish()
    }

    fun now() = clock.nowEpochMillis()

    companion object {
        const val WAIT_MS = 10 * 60_000L
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale.forLanguageTag("ru"))

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.vow_question_today)
    today.minusDays(1) -> stringResource(R.string.vow_question_yesterday)
    else -> stringResource(R.string.vow_question_day, date.format(dayFormat))
}

/** «Трезвость · вчера — сохранил?» (D-79): which vow and which day, for rows with several vows. */
@Composable
fun vowDayLabel(kind: VowKind, date: LocalDate, today: LocalDate): String =
    stringResource(kind.short) + " · " + when (date) {
        today -> stringResource(R.string.vow_when_today)
        today.minusDays(1) -> stringResource(R.string.vow_when_yesterday)
        else -> stringResource(R.string.vow_when_day, date.format(dayFormat))
    }

/** «Да / Нет» for one day of the vow. */
@Composable
fun VowAnswerRow(label: String, onAnswer: (Boolean) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            FilterChip(selected = false, onClick = { onAnswer(true) }, label = { Text(stringResource(R.string.vow_kept)) })
            FilterChip(selected = false, onClick = { onAnswer(false) }, label = { Text(stringResource(R.string.vow_not_kept)) })
        }
    }
}

/** After «Нет»: no shame — contrition, confession if needed, and beginning again (D-67). */
@Composable
fun NuncCoepiPanel(onAgain: (() -> Unit)?) {
    ImPanel {
        Text(stringResource(R.string.vow_nunc_coepi_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.vow_nunc_coepi_text), style = MaterialTheme.typography.bodyLarge)
        onAgain?.let { ImSecondaryButton(stringResource(R.string.vow_start_again), onClick = it) }
    }
}

/** D-67: the sobriety vow — taking it, the count, the days to answer, feasts, the details. */
@Composable
fun VowScreen(onBack: () -> Unit, onSos: (VowKind) -> Unit, viewModel: VowViewModel = hiltViewModel()) {
    val st by viewModel.state.collectAsState()
    val slip by viewModel.slip.collectAsState()
    val s = st
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        ImHeader(stringResource(viewModel.kind.title), onBack = onBack)
        if (s == null) return@Column
        if (!s.active) {
            TakeVow(s, viewModel)
            return@Column
        }
        val stats = s.stats
        ImPanel {
            if (stats.current == 0 && stats.slips > 0) {
                Text(stringResource(R.string.vow_restart), style = MaterialTheme.typography.headlineMedium)
            } else {
                Text(stats.current.toString(), style = MaterialTheme.typography.displayLarge.copy(fontFamily = ImMonoFamily), color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.vow_series), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.vow_best_total, stats.best, stats.total), style = MaterialTheme.typography.bodyMedium)
            s.vow?.let { Text(stringResource(R.string.vow_since, it.startDate.format(dayFormat)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            s.vow?.intention?.let { Text(it, style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic)) }
        }
        if (slip) NuncCoepiPanel(viewModel::dismissSlip)

        if (s.pending.isNotEmpty()) {
            ImSectionLabel(stringResource(R.string.vow_pending))
            ImPanel { s.pending.forEach { d -> VowAnswerRow(dayLabel(d, viewModel.today)) { kept -> viewModel.answer(d, kept) } } }
        }

        ImPrimaryButton(stringResource(viewModel.kind.sos), onClick = { onSos(viewModel.kind) })

        if (viewModel.kind.feasts) FeastSection(s, viewModel)

        ImCollapsible(title = stringResource(R.string.vow_settings)) {
            val v = s.vow
            var intention by rememberSaveable(v?.intention) { mutableStateOf(v?.intention.orEmpty()) }
            var name by rememberSaveable(v?.helperName) { mutableStateOf(v?.helperName.orEmpty()) }
            var phone by rememberSaveable(v?.helperPhone) { mutableStateOf(v?.helperPhone.orEmpty()) }
            var phrase by rememberSaveable(v?.declinePhrase) { mutableStateOf(v?.declinePhrase.orEmpty()) }
            DetailsFields(viewModel.kind, intention, { intention = it }, name, { name = it }, phone, { phone = it }, phrase, { phrase = it })
            ImSecondaryButton(stringResource(R.string.save), onClick = { viewModel.updateDetails(intention, name, phone, phrase) })
            var confirm by remember { mutableStateOf(false) }
            TextButton(onClick = { if (confirm) viewModel.release() else confirm = true }) {
                Text(stringResource(if (confirm) R.string.vow_release_confirm else R.string.vow_release), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun TakeVow(s: VowRepository.State, viewModel: VowViewModel) {
    val v = s.vow
    var intention by rememberSaveable { mutableStateOf(v?.intention.orEmpty()) }
    var name by rememberSaveable { mutableStateOf(v?.helperName.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(v?.helperPhone.orEmpty()) }
    var phrase by rememberSaveable { mutableStateOf(v?.declinePhrase.orEmpty()) }
    val kind = viewModel.kind
    Text(stringResource(kind.intro), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(kind.counts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.vow_director), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (kind.health) Text(stringResource(R.string.vow_health), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    DetailsFields(kind, intention, { intention = it }, name, { name = it }, phone, { phone = it }, phrase, { phrase = it })
    ImPrimaryButton(stringResource(R.string.vow_take), onClick = { viewModel.take(intention, name, phone, phrase) })
}

@Composable
private fun DetailsFields(
    kind: VowKind,
    intention: String, onIntention: (String) -> Unit,
    name: String, onName: (String) -> Unit,
    phone: String, onPhone: (String) -> Unit,
    phrase: String, onPhrase: (String) -> Unit,
) {
    OutlinedTextField(
        value = intention, onValueChange = onIntention, label = { Text(stringResource(R.string.vow_intention)) },
        supportingText = { Text(stringResource(R.string.vow_intention_hint)) }, minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = name, onValueChange = onName, label = { Text(stringResource(R.string.vow_helper_name)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = phone, onValueChange = onPhone, label = { Text(stringResource(R.string.vow_helper_phone)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
    )
    if (kind.feasts) OutlinedTextField(
        value = phrase, onValueChange = onPhrase, label = { Text(stringResource(R.string.vow_phrase)) },
        supportingText = { Text(stringResource(R.string.vow_phrase_hint)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(),
    )
}

/** A feast today or tomorrow: the reminder comes an hour before. */
@Composable
private fun FeastSection(s: VowRepository.State, viewModel: VowViewModel) {
    ImSectionLabel(stringResource(R.string.vow_feast))
    ImPanel {
        Text(stringResource(R.string.vow_feast_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val today = viewModel.today
        listOfNotNull(s.today, s.tomorrow).forEach { d ->
            VowRepository.feastTime(d)?.let { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val day = if (d.logicalDate == today) stringResource(R.string.due_today) else stringResource(R.string.due_tomorrow)
                    Text(stringResource(R.string.vow_feast_at, day, t.toString()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.setFeast(d.logicalDate, null) }) { Text(stringResource(R.string.vow_feast_clear)) }
                }
            }
        }
        var tomorrow by rememberSaveable { mutableStateOf(false) }
        var time by remember { mutableStateOf(LocalTime.of(19, 0)) }
        Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            FilterChip(selected = !tomorrow, onClick = { tomorrow = false }, label = { Text(stringResource(R.string.due_today)) })
            FilterChip(selected = tomorrow, onClick = { tomorrow = true }, label = { Text(stringResource(R.string.due_tomorrow)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            ImTimeField("", time, { time = it }, Modifier.weight(1f))
            ImSecondaryButton(stringResource(R.string.vow_feast_set), { viewModel.setFeast(if (tomorrow) today.plusDays(1) else today, time) }, Modifier.weight(1f))
        }
    }
}

/** D-67: «Хочется выпить» — the intention, ten minutes to wait, an arrow prayer, the prayer to St Matthias, a call. */
@Composable
fun VowSosScreen(onBack: () -> Unit, viewModel: VowViewModel = hiltViewModel()) {
    val st by viewModel.state.collectAsState()
    val session by viewModel.session.collectAsState()
    val localWait by viewModel.localWait.collectAsState()
    val prayer by viewModel.prayer.collectAsState()
    val context = LocalContext.current
    val busy = stringResource(R.string.craving_busy)
    val vow = st?.vow
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        ImHeader(stringResource(viewModel.kind.sos), onBack = onBack)
        vow?.intention?.let {
            ImSectionLabel(stringResource(R.string.vow_your_intention))
            ImPanel { Text(it, style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic)) }
        }
        Text(stringResource(viewModel.kind.sosHint), style = MaterialTheme.typography.bodyLarge)

        // Ten minutes: in the shade when the timer is free, on this screen otherwise.
        val started = session?.takeIf { it.kind == TimerController.Kind.CRAVING }?.startedAt ?: localWait
        ImPanel {
            if (started == null) {
                ImSecondaryButton(stringResource(R.string.vow_wait), onClick = {
                    viewModel.startWait { inShade -> if (!inShade) Toast.makeText(context, busy, Toast.LENGTH_LONG).show() }
                }, modifier = Modifier.fillMaxWidth())
            } else {
                var now by remember { mutableLongStateOf(viewModel.now()) }
                LaunchedEffect(started) { while (true) { now = viewModel.now(); delay(1_000) } }
                val left = started + VowViewModel.WAIT_MS - now
                if (left > 0) {
                    Text(stringResource(R.string.vow_wait_left, "%d:%02d".format(left / 60_000, (left / 1000) % 60)), style = MaterialTheme.typography.headlineMedium.copy(fontFamily = ImMonoFamily))
                } else {
                    Text(stringResource(R.string.vow_wait_over), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { viewModel.stopWait() }) { Text(stringResource(R.string.timer_finish)) }
            }
        }

        if (viewModel.jaculatories.isNotEmpty()) {
            var i by rememberSaveable { mutableStateOf((viewModel.jaculatories.indices).random()) }
            val (text, source) = viewModel.jaculatories[i % viewModel.jaculatories.size]
            ImSectionLabel(stringResource(R.string.vow_jaculatory))
            ImPanel {
                Text(text, style = MaterialTheme.typography.titleLarge)
                Text(source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { i = (i + 1) % viewModel.jaculatories.size }) { Text(stringResource(R.string.vow_jaculatory_next)) }
            }
        }

        prayer?.let { (title, text) ->
            ImSectionLabel(title)
            viewModel.kind.prayerNote?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            ImPanel { Text(text, style = MaterialTheme.typography.bodyLarge) }
        }

        vow?.helperPhone?.let { phone ->
            ImPrimaryButton(stringResource(R.string.vow_call, vow.helperName ?: phone), onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))) }
            })
        }
    }
}

/** D-79: the vows — each taken on its own; a tap opens it (or offers to take it). */
@Composable
fun VowListScreen(onBack: () -> Unit, onOpen: (VowKind) -> Unit, viewModel: VowViewModel = hiltViewModel()) {
    val all by viewModel.all.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        ImHeader(stringResource(R.string.vows_title), onBack = onBack)
        Text(stringResource(R.string.vows_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        all.forEach { st ->
            ImPanel(Modifier.fillMaxWidth().clickable { onOpen(st.kind) }) {
                Text(stringResource(st.kind.title), style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        !st.active -> stringResource(R.string.vows_not_taken)
                        st.stats.current == 0 && st.stats.slips > 0 -> stringResource(R.string.vow_restart)
                        else -> stringResource(R.string.vow_day_n, st.stats.current)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (st.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

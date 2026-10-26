package com.itermeum.app.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.feature.prayer.accent
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.data.MorningRepository
import com.itermeum.app.core.database.entity.StreakType
import com.itermeum.app.core.database.entity.MorningStage
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImMotto
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import com.itermeum.app.core.engine.DayScheduleEngine
import com.itermeum.app.navigation.NavRoute
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.itermeum.app.feature.onboarding.HyperOsChecks
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(
    onOpenSettings: () -> Unit,
    onMilestone: (String) -> Unit = {},
    onOpenRoute: (String) -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val streaks by viewModel.streakStates.collectAsState()
    val unseen by viewModel.unseenMilestone.collectAsState()
    val calls by viewModel.calls.collectAsState()
    val agreements by viewModel.agreements.collectAsState()
    val banner by viewModel.banner.collectAsState()
    val lowerBar by viewModel.lowerBar.collectAsState()
    LaunchedEffect(unseen) { unseen?.let { m -> viewModel.markMilestoneSeen(m.id); onMilestone(m.id) } }
    val context = LocalContext.current
    // Section 13: checked on every return to the app, not once per composition.
    var hyperOsOk by remember { mutableStateOf(HyperOsChecks.coreHealthy(context)) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) hyperOsOk = HyperOsChecks.coreHealthy(context) }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val tasks by viewModel.tasks.collectAsState()
    val focus by viewModel.focusPhrase.collectAsState()
    val mortification by viewModel.mortification.collectAsState()
    val sickTomorrow by viewModel.sickTomorrow.collectAsState()
    // Minute tick: cards that depend on the clock (work started, script before 12:00).
    var minute by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); minute = System.currentTimeMillis() / 60_000 } }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        ImHeader(
            title = ui.date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("ru"))),
            trailing = { TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings_title)) } },
        )
        ui.liturgical?.let {
            Text(it.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        ImMotto(stringResource(R.string.motto_serviam))
        if (!hyperOsOk) {
            ImPanel(Modifier.clickable(onClick = onOpenSettings)) {
                Text(stringResource(R.string.hyperos_reset_banner), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        banner?.let { ImPanel { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) } }
        val accent = ui.liturgical?.color?.let { it.accent() } ?: MaterialTheme.colorScheme.primary
        DayRing(
            total = ui.items.size,
            done = ui.items.map { it.normId in ui.done },
            accent = accent,
            calls = calls, agreements = agreements,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StreakType.entries.forEach { t ->
                val s = streaks.firstOrNull { it.type == t }
                StreakStrip(stringResource(streakLabel(t)), s?.current ?: 0, s?.best ?: 0, accent)
            }
        }
        lowerBar?.let { bar -> LowerBarCard(bar, ui, viewModel) }
        MorningCard(ui, viewModel)
        key(minute) { ScriptCard(viewModel) }
        focus?.let { phrase ->
            ImSectionLabel(stringResource(R.string.today_focus))
            ImPanel { Text("«$phrase»", style = MaterialTheme.typography.bodyLarge.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) }
        }
        val workStarted = remember(minute, ui.profile) { viewModel.workStarted(ui.profile) }
        if (tasks.isNotEmpty() && workStarted) {
            ImSectionLabel(stringResource(R.string.today_tasks))
            ImPanel {
                tasks.sortedBy { it.slot }.forEach { t ->
                    val closed = t.status != com.itermeum.app.core.database.entity.TaskStatus.OPEN
                    Text((if (closed) "■ " else "□ ") + t.title, style = MaterialTheme.typography.bodyLarge,
                        color = if (closed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (viewModel.isFriday && ui.items.any { it.normId == NormCatalog.MORTIFICATION }) MortificationCard(mortification, viewModel)
        ui.next?.let { next ->
            ImSectionLabel(stringResource(R.string.today_next_norm))
            ImPanel(Modifier.clickable { normRoute(next.normId)?.let(onOpenRoute) }) {
                Text(ui.norms[next.normId]?.title ?: next.normId, style = MaterialTheme.typography.titleLarge)
                next.time?.let { Text(it.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        ImSectionLabel(stringResource(R.string.today_norms))
        if (ui.items.isNotEmpty() && ui.items.all { it.normId in ui.done }) {
            Text(stringResource(R.string.today_all_done), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        ui.items.forEach { item -> NormRow(item, ui, viewModel, onOpenRoute) }
        Spacer(Modifier.height(ImSpacing.grid))
        Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            // Section 4: sick is set before 06:00 or for tomorrow; switching off is always possible.
            if (ui.sick) {
                TextButton(onClick = { viewModel.setSick(false) }) { Text(stringResource(R.string.today_sick_off)) }
            } else if (viewModel.canSetSickToday()) {
                TextButton(onClick = { viewModel.setSick(true) }) { Text(stringResource(R.string.today_sick)) }
            }
            TextButton(onClick = { viewModel.setSickTomorrow(!sickTomorrow) }) {
                Text(stringResource(if (sickTomorrow) R.string.today_sick_tomorrow_off else R.string.today_sick_tomorrow))
            }
        }
    }
}

/** Section 6.6: failure handling without shame. */
@Composable
private fun LowerBarCard(bar: TodayViewModel.LowerBar, ui: TodayUi, viewModel: TodayViewModel) {
    ImPanel {
        if (bar.calmRestart) {
            Text(stringResource(R.string.lower_calm_restart), style = MaterialTheme.typography.bodyLarge)
            ImSecondaryButton(stringResource(R.string.done), onClick = viewModel::dismissLowerBar)
        } else {
            Text(stringResource(R.string.lower_offer), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.lower_offer_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            viewModel.pausable(ui.items).forEach { item ->
                ImSecondaryButton(stringResource(R.string.lower_pause_norm, ui.norms[item.normId]?.title ?: item.normId), onClick = { viewModel.pauseNorm(item.normId) })
            }
            TextButton(onClick = viewModel::dismissLowerBar) { Text(stringResource(R.string.lower_keep)) }
        }
    }
}

/** Section 5.5: «Поработал над сценарием» — before 12:00, not a norm, no streak effect. */
@Composable
private fun ScriptCard(viewModel: TodayViewModel) {
    val active by viewModel.activeScript.collectAsState()
    val count by viewModel.scriptSessionsToday.collectAsState()
    if (!viewModel.beforeWork && active == null) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) { while (active != null) { delay(1_000); now = System.currentTimeMillis() } }
    ImPanel {
        val a = active
        if (a == null) {
            ImSecondaryButton(stringResource(R.string.script_button) + if (count > 0) " ($count)" else "", onClick = viewModel::startScript)
        } else {
            val el = now - a.startedAt
            Text(stringResource(R.string.script_running, "%02d:%02d".format(el / 60_000, (el / 1000) % 60)), style = MaterialTheme.typography.titleLarge)
            ImSecondaryButton(stringResource(R.string.script_stop), onClick = viewModel::stopScript)
        }
    }
}

/**
 * A tap always does something visible:
 * - norms with their own screen open it (prayer timer, Gospel, outreach, Examen…);
 * - simple norms toggle the check;
 * - morning norms explain how they are counted (alarm → proof → Serviam).
 */
@Composable
private fun NormRow(item: DayScheduleEngine.ScheduledItem, ui: TodayUi, viewModel: TodayViewModel, onOpenRoute: (String) -> Unit) {
    val done = item.normId in ui.done
    val norm = ui.norms[item.normId]
    val route = normRoute(item.normId)
    val hint = morningHint(item.normId)
    var showHint by remember(item.normId) { mutableStateOf(false) }
    ImPanel(
        Modifier.fillMaxWidth().clickable {
            when {
                route != null -> onOpenRoute(route)
                hint != null -> showHint = !showHint
                viewModel.isTapCompletable(item.normId) -> viewModel.toggle(item.normId)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val stateLabel = stringResource(if (done) R.string.a11y_done else R.string.a11y_not_done)
            NormCheck(done, Modifier.padding(end = ImSpacing.card).semantics { stateDescription = stateLabel })
            Column(Modifier.weight(1f)) {
                Text(norm?.title ?: item.normId, style = MaterialTheme.typography.bodyLarge)
                val meta = listOfNotNull(
                    item.time?.toString(),
                    if (item.isCore) stringResource(R.string.today_core) else null,
                    if (item.muted) stringResource(R.string.today_quiet) else null,
                )
                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (route != null) Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.tertiary)
        }
        if (showHint && hint != null) {
            Spacer(Modifier.height(ImSpacing.tight))
            Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A drawn checkbox (the ■/□ glyphs were tiny in Cormorant): gold frame, accent fill and a check when done. */
@Composable
private fun NormCheck(done: Boolean, modifier: Modifier = Modifier) {
    val frame = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primary
    val tick = MaterialTheme.colorScheme.onPrimary
    androidx.compose.foundation.Canvas(modifier.size(26.dp)) {
        val w = size.width
        if (done) drawRect(fill)
        drawRect(frame, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        if (done) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.22f, w * 0.52f); lineTo(w * 0.42f, w * 0.72f); lineTo(w * 0.78f, w * 0.30f)
            }
            drawPath(path, tick, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Square))
        }
    }
}

/** Where a norm is done in the app; null → the row toggles or explains. */
internal fun normRoute(normId: String): String? = when (normId) {
    NormCatalog.MENTAL_PRAYER -> NavRoute.PrayerMental.route
    NormCatalog.GOSPEL, NormCatalog.READING -> NavRoute.PrayerGospel.route
    NormCatalog.SRS -> NavRoute.PrayerSrs.route
    NormCatalog.ANGELUS -> NavRoute.PrayerAngelus.route
    NormCatalog.JACULATORIA -> NavRoute.PrayerJaculatory.of(null)
    NormCatalog.ROSARY -> NavRoute.PrayerRosary.route
    NormCatalog.MASS -> NavRoute.PrayerMass.route
    NormCatalog.EXAMEN -> NavRoute.Examen.route
    NormCatalog.DAY_PLAN -> NavRoute.WorkPlan.route
    NormCatalog.FROG -> NavRoute.WorkOutreach.route
    NormCatalog.LAST_STONE -> NavRoute.WorkLastStone.route
    NormCatalog.CATECHESIS -> NavRoute.PathCatechesis.of(null)
    NormCatalog.WEEKLY_REVIEW -> NavRoute.PathReview.route
    NormCatalog.MONTHLY_SUMMARY -> NavRoute.PathMonth.route
    NormCatalog.QUARTER_CHECK -> NavRoute.PathQuarter.route
    else -> null
}

/** Morning norms are counted by the alarm flow, not by a tap. */
private fun morningHint(normId: String): Int? = when (normId) {
    NormCatalog.WAKE -> R.string.today_hint_wake
    NormCatalog.SERVIAM -> R.string.today_hint_serviam
    NormCatalog.QUARANTINE -> R.string.today_hint_quarantine
    NormCatalog.MORTIFICATION -> R.string.today_hint_mortification
    else -> null
}

@Composable
private fun MorningCard(ui: TodayUi, viewModel: TodayViewModel) {
    val m = ui.morning
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Tick only while a countdown is on screen (quarantine, leave for the gym).
    val ticking = m != null && (m.stage == MorningStage.QUARANTINE || (m.stage == MorningStage.DONE && m.gymAt == null))
    LaunchedEffect(ticking) { while (ticking) { delay(1_000); now = System.currentTimeMillis() } }
    ImPanel {
        when {
            ui.sick -> Text(stringResource(R.string.morning_sick), style = MaterialTheme.typography.bodyLarge)
            m == null || m.stage == MorningStage.SCHEDULED -> {
                val wake = DayScheduleEngine.wakeTimeFor(ui.date.plusDays(1), ui.profile)
                Text(stringResource(R.string.morning_scheduled, wake.toString()), style = MaterialTheme.typography.bodyLarge)
            }
            m.stage == MorningStage.MISSED -> Text(stringResource(R.string.morning_missed), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            m.stage == MorningStage.QUARANTINE -> {
                val started = m.heroicDoneAt ?: m.provenAt ?: now
                val left = (MorningRepository.QUARANTINE_CHECK_MIN * 60_000L - (now - started)).coerceAtLeast(0)
                val mm = left / 60_000; val ss = (left / 1000) % 60
                Text(stringResource(R.string.quarantine_timer, "%02d:%02d".format(mm, ss)), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.quarantine_question_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(ImSpacing.grid))
                Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                    ImSecondaryButton(stringResource(R.string.yes), { viewModel.answerQuarantine(true) }, Modifier.weight(1f))
                    ImSecondaryButton(stringResource(R.string.no), { viewModel.answerQuarantine(false) }, Modifier.weight(1f))
                }
            }
            else -> {
                Text(stringResource(R.string.morning_proven), style = MaterialTheme.typography.bodyLarge)
                if (m.quarantineAnswer != null) {
                    Text(if (m.quarantineAnswer) stringResource(R.string.quarantine_kept) else stringResource(R.string.quarantine_broken),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val weekday = ui.date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                if (weekday && m.gymAt == null && NormCatalog.GYM !in ui.done && m.stage == MorningStage.DONE) {
                    val started = m.quarantineAnsweredAt ?: now
                    val left = (10 * 60_000L - (now - started)).coerceAtLeast(0)
                    Spacer(Modifier.height(ImSpacing.grid))
                    Text(stringResource(R.string.gym_leave_in, "%02d:%02d".format(left / 60_000, (left / 1000) % 60)), style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                        ImPrimaryButton(stringResource(R.string.gym_done), { viewModel.markGym(false) }, Modifier.weight(1f))
                        ImSecondaryButton(stringResource(R.string.gym_home), { viewModel.markGym(true) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun streakLabel(t: StreakType): Int = when (t) {
    StreakType.FIDELITY -> R.string.streak_fidelity
    StreakType.WAKE -> R.string.streak_wake
    StreakType.FROG -> R.string.streak_frog
}

/** Friday: choose in the morning, mark in the evening (sections 3.4, 7.8). */
@Composable
private fun MortificationCard(m: com.itermeum.app.core.database.entity.MortificationLogEntity?, viewModel: TodayViewModel) {
    ImSectionLabel(stringResource(R.string.mortification_title))
    ImPanel {
        when {
            m == null -> {
                Text(stringResource(R.string.mortification_choose), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                viewModel.mortificationOptions.take(6).forEach { option ->
                    TextButton(onClick = { viewModel.chooseMortification(option) }) { Text(option) }
                }
            }
            !m.done -> {
                Text(m.item, style = MaterialTheme.typography.titleMedium)
                ImSecondaryButton(stringResource(R.string.mortification_done), onClick = viewModel::completeMortification)
            }
            else -> Text(stringResource(R.string.mortification_kept, m.item), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

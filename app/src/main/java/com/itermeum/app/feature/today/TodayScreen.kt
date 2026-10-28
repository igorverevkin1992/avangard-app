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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.itermeum.app.R
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.data.MorningRepository
import com.itermeum.app.core.database.entity.MorningStage
import com.itermeum.app.core.database.entity.StreakType
import com.itermeum.app.core.designsystem.ImCheck
import com.itermeum.app.core.designsystem.ImCollapsible
import com.itermeum.app.core.designsystem.ImMotto
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImRule
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import com.itermeum.app.core.engine.DayScheduleEngine
import com.itermeum.app.feature.onboarding.HyperOsChecks
import com.itermeum.app.feature.prayer.accent
import com.itermeum.app.navigation.NavRoute
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * «Сегодня» answers one question: what to do now (D-33).
 * One line of status, one «Сейчас» card with one action, the rest of the
 * day folded into Утро / День / Вечер. Rare actions live in the ⋯ menu;
 * warnings appear only when something is wrong.
 */
@Composable
fun TodayScreen(
    onOpenSettings: () -> Unit,
    onOpenPractices: () -> Unit = {},
    onMilestone: (String) -> Unit = {},
    onOpenRoute: (String) -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val streaks by viewModel.streakStates.collectAsState()
    val unseen by viewModel.unseenMilestone.collectAsState()
    val banner by viewModel.banner.collectAsState()
    val lowerBar by viewModel.lowerBar.collectAsState()
    val mortification by viewModel.mortification.collectAsState()
    val sickTomorrow by viewModel.sickTomorrow.collectAsState()
    LaunchedEffect(unseen) { unseen?.let { m -> viewModel.markMilestoneSeen(m.id); onMilestone(m.id) } }
    val context = LocalContext.current
    // Section 13: checked on every return to the app.
    var hyperOsOk by remember { mutableStateOf(HyperOsChecks.coreHealthy(context)) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) hyperOsOk = HyperOsChecks.coreHealthy(context) }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val accent = ui.liturgical?.color?.let { it.accent() } ?: MaterialTheme.colorScheme.primary
    val fidelity = streaks.firstOrNull { it.type == StreakType.FIDELITY }?.current ?: 0
    val doneCount = ui.items.count { it.normId in ui.done }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        // ── One line of status ─────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ui.date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("ru"))), style = MaterialTheme.typography.headlineMedium)
                ui.liturgical?.let { Text(it.title, style = MaterialTheme.typography.bodyMedium, color = accent) }
            }
            TodayMenu(
                sick = ui.sick, canSickToday = viewModel.canSetSickToday(), sickTomorrow = sickTomorrow,
                onSettings = onOpenSettings,
                onPractices = onOpenPractices,
                onSickToday = { viewModel.setSick(!ui.sick) },
                onSickTomorrow = { viewModel.setSickTomorrow(!sickTomorrow) },
                onLowerBar = viewModel::offerPause,
            )
        }
        Row(
            Modifier.fillMaxWidth().clickable { onOpenRoute(NavRoute.Path.route) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactDayRing(total = ui.items.size, doneCount = doneCount, accent = accent)
            Spacer(Modifier.width(ImSpacing.card))
            Column {
                Text(stringResource(R.string.today_progress, doneCount, ui.items.size), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.today_fidelity_days, fidelity), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ImRule()

        // ── Warnings only when something is wrong ──────────────────────────
        if (!hyperOsOk) {
            ImPanel(Modifier.clickable(onClick = onOpenSettings)) {
                Text(stringResource(R.string.hyperos_reset_banner), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        banner?.let { ImPanel { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) } }
        lowerBar?.let { bar -> LowerBarCard(bar, ui, viewModel) }

        // ── «Сейчас»: one card, one action ─────────────────────────────────
        ImSectionLabel(stringResource(R.string.today_now))
        val tasks by viewModel.tasks.collectAsState()
        NowCard(ui, viewModel, mortification, tasks.isEmpty(), onOpenRoute)

        // ── The rest of the day, folded ────────────────────────────────────
        val groups = remember(ui.items) { groupByPartOfDay(ui.items) }
        val currentPart = partOf(LocalTime.now())
        groups.forEach { (part, items) ->
            val done = items.count { it.normId in ui.done }
            ImCollapsible(
                title = stringResource(part.title),
                summary = stringResource(R.string.today_group_count, done, items.size),
                initiallyExpanded = part == currentPart && done < items.size,
            ) {
                // Open norms first, done ones after.
                items.sortedBy { it.normId in ui.done }.forEach { item -> NormRow(item, ui, viewModel, mortification, onOpenRoute) }
            }
        }
    }
}

/** The ⋯ menu: everything that is needed rarely. */
@Composable
private fun TodayMenu(
    sick: Boolean, canSickToday: Boolean, sickTomorrow: Boolean,
    onSettings: () -> Unit, onPractices: () -> Unit, onSickToday: () -> Unit, onSickTomorrow: () -> Unit, onLowerBar: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.today_menu)
    androidx.compose.foundation.layout.Box {
        TextButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = menuLabel }) {
            Text("⋯", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.tertiary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.practices_title)) }, onClick = { open = false; onPractices() })
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_title)) }, onClick = { open = false; onSettings() })
            // Section 4: sick is set before 06:00 or for tomorrow; switching off is always possible.
            if (sick || canSickToday) {
                DropdownMenuItem(text = { Text(stringResource(if (sick) R.string.today_sick_off else R.string.today_sick)) }, onClick = { open = false; onSickToday() })
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (sickTomorrow) R.string.today_sick_tomorrow_off else R.string.today_sick_tomorrow)) },
                onClick = { open = false; onSickTomorrow() },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.today_menu_lower)) }, onClick = { open = false; onLowerBar() })
        }
    }
}

/**
 * The single most useful thing right now, in this order: the morning flow
 * (quarantine, gym), Friday's choice of a mortification, the next norm with
 * its one action, or «all done».
 */
@Composable
private fun NowCard(ui: TodayUi, viewModel: TodayViewModel, mortification: com.itermeum.app.core.database.entity.MortificationLogEntity?, noPlan: Boolean, onOpenRoute: (String) -> Unit) {
    val m = ui.morning
    val weekday = ui.date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val morningActive = m != null && (
        m.stage == MorningStage.QUARANTINE ||
            (m.stage == MorningStage.DONE && weekday && m.gymAt == null && NormCatalog.GYM !in ui.done && NormCatalog.GYM in ui.items.map { it.normId }) ||
            m.stage == MorningStage.MISSED && NormCatalog.WAKE !in ui.done && LocalTime.now().isBefore(LocalTime.of(12, 0))
        )
    val fridayChoice = viewModel.isFriday && mortification == null && ui.items.any { it.normId == NormCatalog.MORTIFICATION } &&
        LocalTime.now().isBefore(LocalTime.of(12, 0))
    // D-34: the plan is made before work (morning, commute), so the working day starts with an order.
    val planDue = noPlan && NormCatalog.DAY_PLAN in ui.items.map { it.normId } && NormCatalog.DAY_PLAN !in ui.done &&
        LocalTime.now().isBefore(ui.profile.workStart.plusMinutes(30))
    when {
        ui.sick -> ImPanel { Text(stringResource(R.string.morning_sick), style = MaterialTheme.typography.bodyLarge) }
        morningActive -> MorningCard(ui, viewModel)
        fridayChoice -> MortificationCard(mortification, viewModel)
        planDue -> ImPanel {
            Text(stringResource(R.string.today_plan_before_work), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.today_plan_before_work_hint, ui.profile.workStart.plusMinutes(30).toString()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(ImSpacing.grid))
            ImPrimaryButton(stringResource(R.string.today_plan_before_work_button), onClick = { onOpenRoute(NavRoute.WorkPlan.route) })
        }
        ui.next != null -> {
            val next = ui.next!!
            val route = normRoute(next.normId)
            val hint = morningHint(next.normId)
            ImPanel {
                Text(ui.norms[next.normId]?.title ?: next.normId, style = MaterialTheme.typography.headlineMedium)
                next.time?.let { Text(stringResource(R.string.today_at, it.toString()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(ImSpacing.grid))
                when {
                    route != null -> ImPrimaryButton(stringResource(R.string.today_open), onClick = { onOpenRoute(route) })
                    hint != null -> Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    viewModel.isTapCompletable(next.normId) -> ImPrimaryButton(stringResource(R.string.today_mark_done), onClick = { viewModel.toggle(next.normId) })
                }
            }
        }
        ui.items.isNotEmpty() && ui.items.all { it.normId in ui.done } -> ImPanel {
            Text(stringResource(R.string.today_all_done), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            ImMotto(stringResource(R.string.motto_serviam))
        }
        else -> {
            // Before the alarm: tomorrow's morning.
            val wake = DayScheduleEngine.wakeTimeFor(ui.date.plusDays(1), ui.profile)
            ImPanel { Text(stringResource(R.string.morning_scheduled, wake.toString()), style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

private enum class PartOfDay(val title: Int) { MORNING(R.string.today_part_morning), DAY(R.string.today_part_day), EVENING(R.string.today_part_evening), ANYTIME(R.string.today_part_anytime) }

private fun partOf(t: LocalTime): PartOfDay = when {
    t.isBefore(LocalTime.of(3, 0)) -> PartOfDay.EVENING
    t.isBefore(LocalTime.of(12, 0)) -> PartOfDay.MORNING
    t.isBefore(LocalTime.of(19, 0)) -> PartOfDay.DAY
    else -> PartOfDay.EVENING
}

private fun groupByPartOfDay(items: List<DayScheduleEngine.ScheduledItem>): List<Pair<PartOfDay, List<DayScheduleEngine.ScheduledItem>>> =
    items.groupBy { it.time?.let(::partOf) ?: PartOfDay.ANYTIME }
        .toList().sortedBy { it.first.ordinal }

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

/**
 * A tap always does something visible:
 * - norms with their own screen open it (prayer timer, Gospel, outreach, Examen…);
 * - simple norms toggle the check;
 * - morning norms explain how they are counted (alarm → proof → Serviam);
 * - Friday's mortification unfolds its choose / mark card.
 */
@Composable
private fun NormRow(
    item: DayScheduleEngine.ScheduledItem,
    ui: TodayUi,
    viewModel: TodayViewModel,
    mortification: com.itermeum.app.core.database.entity.MortificationLogEntity?,
    onOpenRoute: (String) -> Unit,
) {
    val done = item.normId in ui.done
    val norm = ui.norms[item.normId]
    val route = normRoute(item.normId)
    val hint = morningHint(item.normId)
    var expanded by remember(item.normId) { mutableStateOf(false) }
    ImPanel(
        Modifier.fillMaxWidth().clickable {
            when {
                route != null -> onOpenRoute(route)
                hint != null || item.normId == NormCatalog.MORTIFICATION -> expanded = !expanded
                viewModel.isTapCompletable(item.normId) -> viewModel.toggle(item.normId)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val stateLabel = stringResource(if (done) R.string.a11y_done else R.string.a11y_not_done)
            ImCheck(done, Modifier.padding(end = ImSpacing.card).semantics { stateDescription = stateLabel })
            Column(Modifier.weight(1f)) {
                Text(norm?.title ?: item.normId, style = MaterialTheme.typography.bodyLarge,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                item.time?.let { Text(it.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (route != null) Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.tertiary)
        }
        if (expanded) {
            Spacer(Modifier.height(ImSpacing.tight))
            if (item.normId == NormCatalog.MORTIFICATION) MortificationCard(mortification, viewModel)
            else hint?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            // D-48: when the morning flow did not ask (wake missed, Serviam skipped), answer here.
            val m = ui.morning
            if (item.normId == NormCatalog.QUARANTINE && !done && m?.quarantineAnswer == null && m?.stage != MorningStage.QUARANTINE) {
                Spacer(Modifier.height(ImSpacing.grid))
                Text(stringResource(R.string.quarantine_question_title), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                    ImSecondaryButton(stringResource(R.string.yes), { viewModel.answerQuarantine(true) }, Modifier.weight(1f))
                    ImSecondaryButton(stringResource(R.string.no), { viewModel.answerQuarantine(false) }, Modifier.weight(1f))
                }
            }
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

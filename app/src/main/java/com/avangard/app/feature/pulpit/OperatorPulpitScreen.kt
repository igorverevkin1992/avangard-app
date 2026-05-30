@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.avangard.app.feature.pulpit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.core.domain.model.CoreMode
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.model.DailySession
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.InfraStatus
import com.avangard.app.core.ui.components.CoreTimerDisplay
import com.avangard.app.core.ui.components.DEFAULT_COLD_START_THRESHOLD_MS
import com.avangard.app.core.ui.components.ExactAlarmPermissionBanner
import com.avangard.app.core.ui.components.FlashButton
import com.avangard.app.core.ui.components.HardButton
import com.avangard.app.core.ui.components.HardButtonVariant
import com.avangard.app.core.ui.components.LabelStrip
import com.avangard.app.core.ui.components.NotificationPermissionBanner
import com.avangard.app.core.ui.components.PulpitPanel
import com.avangard.app.core.ui.components.StatusBadge
import com.avangard.app.core.ui.components.StatusBadgeKind
import com.avangard.app.ui.theme.IsaColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val pulpitDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

@Composable
fun OperatorPulpitScreen(
    onOpenAuthorisation: () -> Unit,
    onOpenSabotage: () -> Unit,
    onOpenEveningClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChronometer: () -> Unit,
    onOpenQuote: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OperatorPulpitViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                PulpitEffect.OpenAuthorisationModal -> onOpenAuthorisation()
                PulpitEffect.OpenSabotage -> onOpenSabotage()
                PulpitEffect.OpenEveningClose -> onOpenEveningClose()
            }
        }
    }

    var bannerText by remember { mutableStateOf<String?>(null) }
    val bannerTemplate = stringResource(R.string.banner_status_fix)
    LaunchedEffect(Unit) {
        viewModel.statusBannerEvents.collect { event ->
            bannerText = bannerTemplate.format(event.habit.code, event.habit.displayName, event.mode)
            kotlinx.coroutines.delay(BANNER_HOLD_MS)
            bannerText = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        OperatorPulpitContent(
            today = state?.today ?: viewModel.initialToday,
            state = state,
            nowMsFlow = viewModel.nowMs,
            transientError = state?.transientError,
            onStartFocus = viewModel::onStartFocus,
            onStopFocus = viewModel::onStopFocus,
            onMarkInfra = viewModel::onMarkInfra,
            onRequestApproveCore = viewModel::onRequestApproveCore,
            onSabotageClicked = viewModel::onSabotageClicked,
            onCloseShiftClicked = viewModel::onCloseShiftClicked,
            onSettingsLongPress = onOpenSettings,
            onChronometerClicked = onOpenChronometer,
            onOpenQuote = onOpenQuote,
        )
        bannerText?.let { text ->
            Text(
                text = text,
                color = IsaColors.LiveMetal,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(IsaColors.Carbon)
                    .border(width = 2.dp, color = IsaColors.Approve)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

private const val BANNER_HOLD_MS = 2_400L

@Composable
internal fun OperatorPulpitContent(
    today: LocalDate,
    state: PulpitState?,
    nowMsFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    transientError: com.avangard.app.core.domain.model.SessionError?,
    onStartFocus: (Habit, String?) -> Unit,
    onStopFocus: () -> Unit,
    onMarkInfra: (Habit, InfraStatus) -> Unit,
    onRequestApproveCore: () -> Unit,
    onSabotageClicked: () -> Unit,
    onCloseShiftClicked: () -> Unit,
    onSettingsLongPress: () -> Unit = {},
    onChronometerClicked: () -> Unit = {},
    onOpenQuote: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IsaColors.Graphite)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderStrip(
            today = today,
            onSabotageClicked = onSabotageClicked,
            onSettingsLongPress = onSettingsLongPress,
            onChronometerClicked = onChronometerClicked,
        )

        state?.takeIf { it.chronometerConfigured }?.let { s ->
            AtAGlanceStrip(state = s, onClick = onChronometerClicked)
            LastSevenDaysStrip(classes = s.lastSevenDays)
        }

        NotificationPermissionBanner()
        ExactAlarmPermissionBanner()

        state?.dailyQuote?.let { quote ->
            QuoteOfDayCard(quote = quote, onClick = { onOpenQuote(quote.id) })
        }

        if (state?.shouldNudgeEveningClose == true) {
            Text(
                text = stringResource(R.string.pulpit_evening_nudge),
                color = IsaColors.Signal,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 2.dp, color = IsaColors.Signal)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCloseShiftClicked,
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }

        if (transientError != null) {
            Text(
                text = sessionErrorMessage(transientError),
                color = IsaColors.Signal,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = IsaColors.Signal)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }

        val coreIdle = state?.session?.coreStatus is CoreStatus.Idle ||
            state?.session?.coreStatus == null
        if (state != null && coreIdle) {
            CoreReminderBanner()
        }

        CoreCard(
            state = state,
            nowMsFlow = nowMsFlow,
            onStartFocus = { intent -> onStartFocus(Habit.Generations, intent) },
            onStopFocus = onStopFocus,
            onRequestApproveCore = onRequestApproveCore,
        )

        InfraCard(
            habit = Habit.Spanish,
            state = state,
            nowMsFlow = nowMsFlow,
            onStartFocus = onStartFocus,
            onStopFocus = onStopFocus,
            onMarkInfra = onMarkInfra,
        )
        InfraCard(
            habit = Habit.Sport,
            state = state,
            nowMsFlow = nowMsFlow,
            onStartFocus = onStartFocus,
            onStopFocus = onStopFocus,
            onMarkInfra = onMarkInfra,
        )
        InfraCard(
            habit = Habit.Watching,
            state = state,
            nowMsFlow = nowMsFlow,
            onStartFocus = onStartFocus,
            onStopFocus = onStopFocus,
            onMarkInfra = onMarkInfra,
        )
        InfraCard(
            habit = Habit.Reading,
            state = state,
            nowMsFlow = nowMsFlow,
            onStartFocus = onStartFocus,
            onStopFocus = onStopFocus,
            onMarkInfra = onMarkInfra,
        )

        HardButton(
            label = stringResource(R.string.pulpit_close_shift),
            onClick = onCloseShiftClicked,
        )
    }
}

@Composable
private fun HeaderStrip(
    today: LocalDate,
    onSabotageClicked: () -> Unit,
    onSettingsLongPress: () -> Unit,
    onChronometerClicked: () -> Unit,
) {
    // FlowRow wraps to a second line on narrow screens (small phones in
    // landscape, split-screen, etc.) rather than letting the rightmost chip
    // overflow the viewport.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val dateA11y = stringResource(R.string.a11y_pulpit_date)
        Text(
            text = today.format(pulpitDateFormatter),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .semantics { contentDescription = dateA11y }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {},
                    onLongClick = onSettingsLongPress,
                ),
        )
        ChronometerChip(onClick = onChronometerClicked)
        SabotageChip(onClick = onSabotageClicked)
    }
}

@Composable
private fun ChronometerChip(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val a11y = stringResource(R.string.a11y_chronometer)
    Text(
        text = stringResource(R.string.pulpit_chronometer_chip),
        color = IsaColors.LiveMetal,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .semantics { contentDescription = a11y }
            .border(width = 1.dp, color = IsaColors.LiveMetal)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun SabotageChip(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val a11y = stringResource(R.string.a11y_sabotage)
    Text(
        text = stringResource(R.string.pulpit_sabotage),
        color = IsaColors.Signal,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .semantics { contentDescription = a11y }
            .border(width = 1.dp, color = IsaColors.Signal)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun CoreCard(
    state: PulpitState?,
    nowMsFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    onStartFocus: (String?) -> Unit,
    onStopFocus: () -> Unit,
    onRequestApproveCore: () -> Unit,
) {
    val session = state?.session
    val coreStatus = session?.coreStatus
    val isApproved = coreStatus is CoreStatus.Approved
    val dayMode = session?.dayMode
    val isFailed = coreStatus is CoreStatus.Failed
    val activeOnCore = state?.isFocusActiveOn(Habit.Generations) == true
    val badge = when {
        isApproved && dayMode == CoreMode.Mvd -> StatusBadgeKind.Mvd
        isApproved -> StatusBadgeKind.Standard
        isFailed -> StatusBadgeKind.Fail
        else -> StatusBadgeKind.Idle
    }
    val coreBorder = when {
        isApproved && dayMode == CoreMode.Mvd -> IsaColors.Caution
        isApproved -> IsaColors.Approve
        else -> IsaColors.Steel
    }
    // Collect the ticker only here so the 1Hz tick scopes its recomp to the
    // CoreTimerDisplay subtree; sibling InfraCards never recompose on tick.
    val nowMs by nowMsFlow.collectAsState()
    val elapsedMs = if (activeOnCore) {
        state?.activeFocus?.durationMillis(nowMs) ?: 0L
    } else 0L
    PulpitPanel(borderColor = coreBorder) {
        LabelStrip(
            code = Habit.Generations.code,
            name = Habit.Generations.displayName,
            trailing = { StatusBadge(kind = badge) },
        )
        CoreTimerDisplay(
            elapsedMillis = elapsedMs,
            thresholdMs = state?.coldStartThresholdMs ?: DEFAULT_COLD_START_THRESHOLD_MS,
        )
        val activeIntent = if (activeOnCore) state?.activeFocus?.intent else null
        IntentField(
            habit = Habit.Generations,
            activeIntent = activeIntent,
            onStartWithIntent = onStartFocus,
            startEnabled = state?.activeFocus == null,
            active = activeOnCore,
            onStop = onStopFocus,
        )
        FocusStatsLine(habit = Habit.Generations, state = state, nowMsFlow = nowMsFlow)
        HardButton(
            label = stringResource(R.string.pulpit_analysis),
            onClick = onRequestApproveCore,
            enabled = !isApproved && !isFailed,
            variant = HardButtonVariant.Primary,
        )
    }
}

@Composable
private fun InfraCard(
    habit: Habit,
    state: PulpitState?,
    nowMsFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    onStartFocus: (Habit, String?) -> Unit,
    onStopFocus: () -> Unit,
    onMarkInfra: (Habit, InfraStatus) -> Unit,
) {
    val session: DailySession? = state?.session
    val infraStatus = session?.infraStatus(habit) ?: InfraStatus.NotDone
    val done = infraStatus == InfraStatus.Done
    val activeOnHabit = state?.isFocusActiveOn(habit) == true
    // The colour of a Done habit is pulled from the day's mode (decided once
    // via the day-mode chip in the header): Standard → green, MVD → amber.
    // Done habits before the mode is picked default to green.
    val dayMode = session?.dayMode
    val doneColor = if (dayMode == CoreMode.Mvd) IsaColors.Caution else IsaColors.Approve
    val badge = when {
        done -> if (dayMode == CoreMode.Mvd) StatusBadgeKind.Mvd else StatusBadgeKind.Standard
        else -> StatusBadgeKind.Idle
    }
    val infraBorder = if (done) doneColor else IsaColors.Steel
    // Cards collapse to a 1-row strip when there's nothing happening on them.
    // Active focus or a marked status auto-expands; tap-to-expand otherwise.
    val autoExpanded = activeOnHabit || done
    var manuallyExpanded by rememberSaveable(habit.code) { mutableStateOf(false) }
    val expanded = autoExpanded || manuallyExpanded
    PulpitPanel(borderColor = infraBorder) {
        val labelInteraction = remember(habit.code) { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = labelInteraction,
                    indication = null,
                    onClick = { manuallyExpanded = !manuallyExpanded },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabelStrip(
                code = habit.code,
                name = habit.displayName,
                trailing = { StatusBadge(kind = badge) },
                modifier = Modifier.weight(1f),
            )
        }
        if (!expanded) return@PulpitPanel
        val activeIntent = if (activeOnHabit) state?.activeFocus?.intent else null
        IntentField(
            habit = habit,
            activeIntent = activeIntent,
            onStartWithIntent = { intent -> onStartFocus(habit, intent) },
            startEnabled = state?.activeFocus == null,
            active = activeOnHabit,
            onStop = onStopFocus,
        )
        FocusStatsLine(habit = habit, state = state, nowMsFlow = nowMsFlow)
        HardButton(
            label = stringResource(
                if (done) R.string.pulpit_mark_undo else R.string.pulpit_mark_done,
            ),
            onClick = {
                onMarkInfra(habit, if (done) InfraStatus.NotDone else InfraStatus.Done)
            },
            variant = if (done) HardButtonVariant.Default else HardButtonVariant.Primary,
        )
    }
}

/**
 * Shows today's focus totals for a habit and, when a session is active on
 * this habit, the live elapsed time too. Live tick is scoped to this line
 * via nowMsFlow.collectAsState(), so a sibling card not currently in
 * focus doesn't recompose every second.
 */
@Composable
private fun FocusStatsLine(
    habit: Habit,
    state: PulpitState?,
    nowMsFlow: kotlinx.coroutines.flow.StateFlow<Long>,
) {
    val completedMs = state?.completedFocusByHabit?.get(habit) ?: 0L
    val completedCount = state?.completedFocusCountByHabit?.get(habit) ?: 0
    val activeOnHabit = state?.isFocusActiveOn(habit) == true
    if (!activeOnHabit && completedCount == 0) return

    val nowMs by nowMsFlow.collectAsState()
    val activeElapsed = if (activeOnHabit) {
        state?.activeFocus?.durationMillis(nowMs) ?: 0L
    } else 0L
    val displayText = if (activeOnHabit) {
        stringResource(
            R.string.pulpit_focus_stats_active,
            formatHms(activeElapsed),
            completedCount,
            formatHms(completedMs),
        )
    } else {
        stringResource(
            R.string.pulpit_focus_stats_idle,
            completedCount,
            formatHms(completedMs),
        )
    }
    Text(
        text = displayText,
        color = if (activeOnHabit) IsaColors.Approve else IsaColors.Lattice,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun formatHms(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%02d:%02d:%02d".format(java.util.Locale.US, h, m, s)
}

/**
 * Passive reminder above the Core card while Generations is still Idle.
 * Replaces the old «hostage» gate on Watching/Reading — Infra habits are
 * always start-able now, but Core's primacy is signalled visually so the
 * operator never forgets that 01·ГЕНЕРАЦИИ is the day's central act.
 */
@Composable
private fun CoreReminderBanner() {
    Text(
        text = stringResource(R.string.pulpit_core_reminder),
        color = IsaColors.Caution,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = IsaColors.Caution)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/**
 * FlashButton + optional pre-start intent text field. Combined into one
 * composable so each card on the pulpit gets the same affordance without
 * separately wiring focus state and rememberSaveable per habit. While a
 * session is active, the intent (if any) is read-only above the stop
 * button.
 */
@Composable
private fun IntentField(
    habit: Habit,
    activeIntent: String?,
    onStartWithIntent: (String?) -> Unit,
    startEnabled: Boolean,
    active: Boolean,
    onStop: () -> Unit,
) {
    if (active) {
        if (!activeIntent.isNullOrBlank()) {
            Text(
                text = "→ $activeIntent",
                color = IsaColors.Lattice,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = IsaColors.Steel)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        FlashButton(
            label = stringResource(R.string.pulpit_flash_stop),
            active = true,
            enabled = true,
            onClick = onStop,
        )
        return
    }
    var intentDraft by rememberSaveable(habit.code) { mutableStateOf("") }
    androidx.compose.foundation.text.BasicTextField(
        value = intentDraft,
        onValueChange = { intentDraft = it },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = IsaColors.LiveMetal),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = IsaColors.Steel)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                if (intentDraft.isEmpty()) {
                    Text(
                        text = stringResource(R.string.pulpit_intent_placeholder),
                        color = IsaColors.Lattice,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                inner()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    FlashButton(
        label = stringResource(R.string.pulpit_flash_start),
        active = false,
        enabled = startEnabled,
        onClick = { onStartWithIntent(intentDraft.takeIf { it.isNotBlank() }) },
    )
}

/**
 * Compact one-row summary on top of the pulpit: day-number, days remaining,
 * total focus time accumulated today across every habit. Tap routes to the
 * chronometer for the full grid. Stays out of the layout entirely when
 * birthday isn't configured.
 */
@Composable
private fun AtAGlanceStrip(state: PulpitState, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val russianLocale = java.util.Locale("ru", "RU")
    val daysFmt = java.text.NumberFormat.getIntegerInstance(russianLocale)
    val text = buildString {
        append("СУТКИ #")
        append(daysFmt.format(state.dayNumber))
        append("  ·  ОСТ ~")
        append(daysFmt.format(state.daysRemaining))
        append("  ·  Σ ")
        append(formatHms(state.completedFocusTotalMs))
    }
    Text(
        text = text,
        color = IsaColors.LiveMetal,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = IsaColors.Steel)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/**
 * Seven-cell strip showing the last week's day classes (oldest → today).
 * Reuses the chronometer's classification: Approve-green for Extracted /
 * full-Standard days, Caution-amber for Partial, Mute-grey for past
 * Burned/Idle days. Today is bordered Signal-red.
 */
@Composable
private fun LastSevenDaysStrip(classes: List<com.avangard.app.core.domain.model.DayClass>) {
    if (classes.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        classes.forEachIndexed { index, cls ->
            val isToday = index == classes.lastIndex
            val fill = when (cls) {
                com.avangard.app.core.domain.model.DayClass.Extracted -> IsaColors.Approve
                com.avangard.app.core.domain.model.DayClass.Partial -> IsaColors.Caution
                com.avangard.app.core.domain.model.DayClass.Burned -> IsaColors.Mute
                com.avangard.app.core.domain.model.DayClass.Today -> IsaColors.Graphite
                com.avangard.app.core.domain.model.DayClass.Future -> IsaColors.Graphite
            }
            val border = if (isToday) IsaColors.Signal else IsaColors.Steel
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
                    .border(width = if (isToday) 2.dp else 1.dp, color = border)
                    .background(fill),
            )
        }
    }
}

@Composable
private fun QuoteOfDayCard(
    quote: com.avangard.app.core.domain.model.Quote,
    onClick: () -> Unit,
) {
    val interactionSource = remember(quote.id) { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = IsaColors.Steel)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.pulpit_quote_of_day_label),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = quote.text,
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.bodyMedium,
            // Full text — short quotes don't get the truncation ellipsis, long
            // ones still fit thanks to the panel's vertical scroll context.
        )
        Text(
            text = quote.source,
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

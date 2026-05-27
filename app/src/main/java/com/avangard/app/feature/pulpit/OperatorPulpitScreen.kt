@file:OptIn(ExperimentalFoundationApi::class)

package com.avangard.app.feature.pulpit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    onOpenAuthorisation: (CoreMode) -> Unit,
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
                is PulpitEffect.OpenAuthorisationModal -> onOpenAuthorisation(effect.mode)
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
    onStartFocus: (Habit) -> Unit,
    onStopFocus: () -> Unit,
    onMarkInfra: (Habit, InfraStatus) -> Unit,
    onRequestApproveCore: (CoreMode) -> Unit,
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

        CoreCard(
            state = state,
            nowMsFlow = nowMsFlow,
            onStartFocus = { onStartFocus(Habit.Generations) },
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
    onStartFocus: () -> Unit,
    onStopFocus: () -> Unit,
    onRequestApproveCore: (CoreMode) -> Unit,
) {
    val session = state?.session
    val coreStatus = session?.coreStatus
    val isApproved = coreStatus is CoreStatus.Approved
    val approvedMode = (coreStatus as? CoreStatus.Approved)?.mode
    val isFailed = coreStatus is CoreStatus.Failed
    val activeOnCore = state?.isFocusActiveOn(Habit.Generations) == true
    val badge = when {
        isApproved && approvedMode == CoreMode.Mvd -> StatusBadgeKind.Mvd
        isApproved -> StatusBadgeKind.Standard
        isFailed -> StatusBadgeKind.Fail
        else -> StatusBadgeKind.Idle
    }
    val coreBorder = when {
        isApproved && approvedMode == CoreMode.Mvd -> IsaColors.Caution
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
        FlashButton(
            label = stringResource(
                if (activeOnCore) R.string.pulpit_flash_stop else R.string.pulpit_flash_start
            ),
            active = activeOnCore,
            enabled = state?.activeFocus == null || activeOnCore,
            onClick = if (activeOnCore) onStopFocus else onStartFocus,
        )
        FocusStatsLine(habit = Habit.Generations, state = state, nowMsFlow = nowMsFlow)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HardButton(
                label = stringResource(R.string.pulpit_mark_standard),
                onClick = { onRequestApproveCore(CoreMode.Standard) },
                enabled = !isApproved && !isFailed,
                variant = HardButtonVariant.Primary,
                modifier = Modifier.weight(1f),
            )
            HardButton(
                label = stringResource(R.string.pulpit_mark_mvd),
                onClick = { onRequestApproveCore(CoreMode.Mvd) },
                enabled = !isApproved && !isFailed,
                variant = HardButtonVariant.Default,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InfraCard(
    habit: Habit,
    state: PulpitState?,
    nowMsFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    onStartFocus: (Habit) -> Unit,
    onStopFocus: () -> Unit,
    onMarkInfra: (Habit, InfraStatus) -> Unit,
) {
    val session: DailySession? = state?.session
    // Only evening habits (Watching, Reading) wait for Core. Morning habits
    // (Spanish, Sport) are always unlocked.
    val locked = habit.requiresCoreApproval && session?.isCoreUnlocked != true
    val infraStatus = session?.infraStatus(habit) ?: InfraStatus.NotDone
    val activeOnHabit = state?.isFocusActiveOn(habit) == true
    val badge = when {
        locked -> StatusBadgeKind.Locked
        infraStatus == InfraStatus.Standard -> StatusBadgeKind.Standard
        infraStatus == InfraStatus.Mvd -> StatusBadgeKind.Mvd
        else -> StatusBadgeKind.Idle
    }
    val infraBorder = when {
        locked -> IsaColors.HostageGray
        infraStatus == InfraStatus.Standard -> IsaColors.Approve
        infraStatus == InfraStatus.Mvd -> IsaColors.Caution
        else -> IsaColors.Steel
    }
    PulpitPanel(
        borderColor = infraBorder,
    ) {
        LabelStrip(
            code = habit.code,
            name = habit.displayName,
            trailing = { StatusBadge(kind = badge) },
        )
        if (locked) {
            val a11yLocked = stringResource(R.string.a11y_infra_locked, habit.displayName)
            Text(
                text = stringResource(R.string.pulpit_hostage_banner),
                color = IsaColors.Signal,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = a11yLocked }
                    .border(width = 1.dp, color = IsaColors.Signal)
                    .padding(8.dp),
            )
            return@PulpitPanel
        }
        FlashButton(
            label = stringResource(
                if (activeOnHabit) R.string.pulpit_flash_stop else R.string.pulpit_flash_start
            ),
            active = activeOnHabit,
            enabled = state?.activeFocus == null || activeOnHabit,
            onClick = if (activeOnHabit) onStopFocus else { { onStartFocus(habit) } },
        )
        FocusStatsLine(habit = habit, state = state, nowMsFlow = nowMsFlow)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HardButton(
                label = stringResource(R.string.pulpit_mark_standard),
                onClick = { onMarkInfra(habit, InfraStatus.Standard) },
                variant = if (infraStatus == InfraStatus.Standard)
                    HardButtonVariant.Primary else HardButtonVariant.Default,
                modifier = Modifier.weight(1f),
            )
            HardButton(
                label = stringResource(R.string.pulpit_mark_mvd),
                onClick = { onMarkInfra(habit, InfraStatus.Mvd) },
                variant = if (infraStatus == InfraStatus.Mvd)
                    HardButtonVariant.Primary else HardButtonVariant.Default,
                modifier = Modifier.weight(1f),
            )
        }
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
            maxLines = 3,
        )
        Text(
            text = quote.source,
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

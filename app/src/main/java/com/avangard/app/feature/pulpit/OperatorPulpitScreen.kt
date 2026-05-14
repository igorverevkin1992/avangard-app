package com.avangard.app.feature.pulpit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.model.DailySession
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.InfraStatus
import com.avangard.app.core.ui.components.CoreTimerDisplay
import com.avangard.app.core.ui.components.FlashButton
import com.avangard.app.core.ui.components.HardButton
import com.avangard.app.core.ui.components.HardButtonVariant
import com.avangard.app.core.ui.components.LabelStrip
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

    OperatorPulpitContent(
        today = state?.today ?: LocalDate.now(),
        state = state,
        onStartFocus = viewModel::onStartFocus,
        onStopFocus = viewModel::onStopFocus,
        onToggleMvd = viewModel::onToggleMvd,
        onMarkInfra = viewModel::onMarkInfra,
        onRequestApproveCore = viewModel::onRequestApproveCore,
        onSabotageClicked = viewModel::onSabotageClicked,
        onCloseShiftClicked = viewModel::onCloseShiftClicked,
        modifier = modifier,
    )
}

@Composable
internal fun OperatorPulpitContent(
    today: LocalDate,
    state: PulpitState?,
    onStartFocus: (Habit) -> Unit,
    onStopFocus: () -> Unit,
    onToggleMvd: () -> Unit,
    onMarkInfra: (Habit, InfraStatus) -> Unit,
    onRequestApproveCore: () -> Unit,
    onSabotageClicked: () -> Unit,
    onCloseShiftClicked: () -> Unit,
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
            mvdActive = state?.session?.mvdActive == true,
            onToggleMvd = onToggleMvd,
            onSabotageClicked = onSabotageClicked,
        )

        CoreCard(
            state = state,
            onStartFocus = { onStartFocus(Habit.Generations) },
            onStopFocus = onStopFocus,
            onRequestApproveCore = onRequestApproveCore,
        )

        InfraCard(
            habit = Habit.Spanish,
            state = state,
            onStartFocus = onStartFocus,
            onStopFocus = onStopFocus,
            onMarkInfra = onMarkInfra,
        )
        InfraCard(
            habit = Habit.Sport,
            state = state,
            onStartFocus = onStartFocus,
            onStopFocus = onStopFocus,
            onMarkInfra = onMarkInfra,
        )
        InfraCard(
            habit = Habit.Watching,
            state = state,
            onStartFocus = onStartFocus,
            onStopFocus = onStopFocus,
            onMarkInfra = onMarkInfra,
        )
        InfraCard(
            habit = Habit.Reading,
            state = state,
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
    mvdActive: Boolean,
    onToggleMvd: () -> Unit,
    onSabotageClicked: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = today.format(pulpitDateFormatter),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelMedium,
        )
        MvdToggle(active = mvdActive, onClick = onToggleMvd)
        SabotageChip(onClick = onSabotageClicked)
    }
}

@Composable
private fun MvdToggle(active: Boolean, onClick: () -> Unit) {
    val color = if (active) IsaColors.Approve else IsaColors.Lattice
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = stringResource(if (active) R.string.pulpit_mvd_on else R.string.pulpit_mvd_off),
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .border(width = 1.dp, color = color)
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
    Text(
        text = stringResource(R.string.pulpit_sabotage),
        color = IsaColors.Signal,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
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
    onStartFocus: () -> Unit,
    onStopFocus: () -> Unit,
    onRequestApproveCore: () -> Unit,
) {
    val session = state?.session
    val isApproved = session?.coreStatus is CoreStatus.Approved
    val isFailed = session?.coreStatus is CoreStatus.Failed
    val activeOnCore = state?.isFocusActiveOn(Habit.Generations) == true
    val badge = when {
        isApproved -> StatusBadgeKind.Approved
        isFailed -> StatusBadgeKind.Fail
        else -> StatusBadgeKind.Idle
    }
    PulpitPanel(
        borderColor = if (isApproved) IsaColors.Approve else IsaColors.Steel,
    ) {
        LabelStrip(
            code = Habit.Generations.code,
            name = Habit.Generations.displayName,
            trailing = { StatusBadge(kind = badge) },
        )
        CoreTimerDisplay(
            elapsedMillis = if (activeOnCore) state?.activeFocusElapsedMs ?: 0L else 0L,
        )
        FlashButton(
            label = stringResource(
                if (activeOnCore) R.string.pulpit_flash_stop else R.string.pulpit_flash_start
            ),
            active = activeOnCore,
            enabled = !isApproved,
            onClick = if (activeOnCore) onStopFocus else onStartFocus,
        )
        HardButton(
            label = stringResource(R.string.pulpit_save_shot),
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
    onStartFocus: (Habit) -> Unit,
    onStopFocus: () -> Unit,
    onMarkInfra: (Habit, InfraStatus) -> Unit,
) {
    val session: DailySession? = state?.session
    val locked = session?.isCoreUnlocked != true
    val infraStatus = session?.infraStatus(habit) ?: InfraStatus.NotDone
    val activeOnHabit = state?.isFocusActiveOn(habit) == true
    val badge = when {
        locked -> StatusBadgeKind.Locked
        infraStatus == InfraStatus.Standard -> StatusBadgeKind.Standard
        infraStatus == InfraStatus.Mvd -> StatusBadgeKind.Mvd
        else -> StatusBadgeKind.Idle
    }
    PulpitPanel(
        borderColor = if (locked) IsaColors.HostageGray else IsaColors.Steel,
    ) {
        LabelStrip(
            code = habit.code,
            name = habit.displayName,
            trailing = { StatusBadge(kind = badge) },
        )
        if (locked) {
            Text(
                text = stringResource(R.string.pulpit_hostage_banner),
                color = IsaColors.Signal,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .fillMaxWidth()
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

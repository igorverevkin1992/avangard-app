package com.avangard.app.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.HabitMonthlyView
import com.avangard.app.ui.theme.IsaColors
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HabitTrackerScreen(
    modifier: Modifier = Modifier,
    viewModel: HabitTrackerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    HabitTrackerContent(
        state = state,
        onSelectMonth = viewModel::selectMonth,
        onToggle = viewModel::toggle,
        onSelectDay = viewModel::selectDay,
        onCloseDayPanel = viewModel::closeDayPanel,
        onSetFilter = viewModel::setHabitFilter,
        modifier = modifier,
    )
}

@Composable
internal fun HabitTrackerContent(
    state: HabitTrackerState,
    onSelectMonth: (YearMonth) -> Unit,
    onToggle: (LocalDate, Habit) -> Unit,
    onSelectDay: (LocalDate) -> Unit = {},
    onCloseDayPanel: () -> Unit = {},
    onSetFilter: (Habit?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IsaColors.Graphite)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthSelector(
            year = state.selected.year,
            month = state.selected.monthValue,
            onSelect = { m -> onSelectMonth(YearMonth.of(state.selected.year, m)) },
        )
        HabitFilterRow(active = state.habitFilter, onSet = onSetFilter)
        AggregateRow(view = state.view, filter = state.habitFilter)
        ColumnHeader(filter = state.habitFilter)
        Box(modifier = Modifier.weight(1f)) {
            DayGrid(
                today = state.today,
                view = state.view,
                filter = state.habitFilter,
                onToggle = onToggle,
                onSelectDay = onSelectDay,
                selectedDay = state.selectedDay,
            )
        }
        state.dayDetail?.let { detail ->
            DayDetailPanel(detail = detail, onClose = onCloseDayPanel)
        }
    }
}

@Composable
private fun MonthSelector(year: Int, month: Int, onSelect: (Int) -> Unit) {
    val months = listOf(
        "ЯНВ", "ФЕВ", "МАР", "АПР", "МАЙ", "ИЮН",
        "ИЮЛ", "АВГ", "СЕН", "ОКТ", "НОЯ", "ДЕК",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        months.forEachIndexed { index, label ->
            val active = (index + 1) == month
            val color = if (active) IsaColors.Signal else IsaColors.Lattice
            val interactionSource = remember(label) { MutableInteractionSource() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .border(width = if (active) 2.dp else 1.dp, color = color)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onSelect(index + 1) },
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = label,
                    color = color,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "%02d/12".format(java.util.Locale.US, index + 1),
                    color = color,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
    Text(
        text = "$year",
        color = IsaColors.Lattice,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun HabitFilterRow(active: Habit?, onSet: (Habit?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(label = "ВСЕ", selected = active == null, onClick = { onSet(null) })
        Habit.entries.forEach { habit ->
            FilterChip(
                label = "${habit.code}·${habit.shortLabel}",
                selected = active == habit,
                onClick = { onSet(if (active == habit) null else habit) },
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) IsaColors.Signal else IsaColors.Lattice
    val interactionSource = remember(label) { MutableInteractionSource() }
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .border(width = if (selected) 2.dp else 1.dp, color = color)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun AggregateRow(view: HabitMonthlyView?, filter: Habit?) {
    val visible = filter?.let { listOf(it) } ?: Habit.entries
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(DATE_COL_WIDTH.dp))
        visible.forEach { habit ->
            val count = view?.completedCount(habit) ?: 0
            val total = view?.daysInMonth ?: 0
            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(width = 1.dp, color = IsaColors.Lattice)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${habit.code}·${habit.shortLabel}",
                    color = IsaColors.Approve,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = if (total == 0) "—" else "$count/$total",
                    color = IsaColors.LiveMetal,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ColumnHeader(filter: Habit?) {
    val visible = filter?.let { listOf(it) } ?: Habit.entries
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "ДАТА",
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(DATE_COL_WIDTH.dp),
        )
        visible.forEach { habit ->
            Text(
                text = habit.code,
                color = IsaColors.Lattice,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayGrid(
    today: LocalDate,
    view: HabitMonthlyView?,
    filter: Habit?,
    onToggle: (LocalDate, Habit) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    selectedDay: LocalDate?,
) {
    val daysInMonth = view?.daysInMonth ?: 0
    val ym = view?.let { YearMonth.of(it.year, it.month) } ?: YearMonth.from(today)
    val days = (1..daysInMonth).map { ym.atDay(it) }

    val listState = rememberLazyListState()
    LaunchedEffect(view?.year, view?.month) {
        val showsToday = view?.year == today.year && view.month == today.monthValue
        if (showsToday && daysInMonth > 0) {
            val idx = (today.dayOfMonth - 1).coerceAtLeast(0)
            listState.scrollToItem(idx)
        }
    }

    LazyColumn(state = listState) {
        items(days) { date ->
            DayRow(
                date = date,
                isToday = date == today,
                isSelected = date == selectedDay,
                view = view,
                filter = filter,
                onToggle = onToggle,
                onSelectDay = onSelectDay,
            )
        }
    }
}

@Composable
private fun DayRow(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    view: HabitMonthlyView?,
    filter: Habit?,
    onToggle: (LocalDate, Habit) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
) {
    val rowBg = when {
        isToday -> IsaColors.Signal
        isSelected -> IsaColors.Mute
        else -> Color.Transparent
    }
    val dateColor = if (isToday) IsaColors.LiveMetal else IsaColors.Lattice
    val visible = filter?.let { listOf(it) } ?: Habit.entries
    val dateInteractionSource = remember(date) { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "%02d.%02d".format(java.util.Locale.US, date.dayOfMonth, date.monthValue),
            color = dateColor,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            modifier = Modifier
                .width(DATE_COL_WIDTH.dp)
                .clickable(
                    interactionSource = dateInteractionSource,
                    indication = null,
                    onClick = { onSelectDay(date) },
                )
                .padding(start = 4.dp),
        )
        visible.forEach { habit ->
            HabitCell(
                isOn = view?.isCompleted(date, habit) == true,
                onClick = { onToggle(date, habit) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HabitCell(isOn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(44.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(width = 1.dp, color = IsaColors.Steel)
                .background(if (isOn) IsaColors.Approve else Color.Transparent),
        )
    }
}

private const val DATE_COL_WIDTH = 56

/**
 * Drill-down panel pinned to the bottom of the tracker — shows the picked
 * day's accumulated focus-session totals per habit and the day's journal
 * entry. Single tap on the same day's date column closes it again.
 */
@Composable
private fun DayDetailPanel(detail: DayDetail, onClose: () -> Unit) {
    val russianLocale = java.util.Locale("ru", "RU")
    val dateFmt = java.time.format.DateTimeFormatter.ofPattern("dd MMMM, EEEE", russianLocale)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = IsaColors.Signal)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = detail.date.format(dateFmt).uppercase(russianLocale),
                color = IsaColors.LiveMetal,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = "✕",
                color = IsaColors.Lattice,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    )
                    .padding(horizontal = 4.dp),
            )
        }
        // Focus rows per habit; only emit habits with non-zero totals to keep
        // the panel compact on quiet days.
        Habit.entries.forEach { habit ->
            val ms = detail.focusByHabit[habit] ?: 0L
            val count = detail.focusCountByHabit[habit] ?: 0
            if (ms == 0L && count == 0) return@forEach
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${habit.code}·${habit.shortLabel}",
                    color = IsaColors.Lattice,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = "$count × ${formatHms(ms)}",
                    color = IsaColors.LiveMetal,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (detail.focusByHabit.isEmpty()) {
            Text(
                text = "СЕССИЙ НЕ ЗАФИКСИРОВАНО",
                color = IsaColors.Lattice,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        detail.journal?.takeIf { it.isNotBlank() }?.let { journal ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = "ЖУРНАЛ",
                color = IsaColors.Lattice,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = journal,
                color = IsaColors.LiveMetal,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun formatHms(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    return "%02d:%02d".format(java.util.Locale.US, h, m)
}

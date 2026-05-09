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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.avangard.app.ui.theme.MachineColors
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
        modifier = modifier,
    )
}

@Composable
internal fun HabitTrackerContent(
    state: HabitTrackerState,
    onSelectMonth: (YearMonth) -> Unit,
    onToggle: (LocalDate, Habit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MachineColors.Anthracite)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthSelector(
            year = state.selected.year,
            month = state.selected.monthValue,
            onSelect = { m -> onSelectMonth(YearMonth.of(state.selected.year, m)) },
        )
        AggregateRow(view = state.view)
        ColumnHeader()
        DayGrid(
            today = state.today,
            view = state.view,
            onToggle = onToggle,
        )
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
            val color = if (active) MachineColors.AtlasRed else MachineColors.WarmGray
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
                    text = "%02d/12".format(index + 1),
                    color = color,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
    Text(
        text = "$year",
        color = MachineColors.WarmGray,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun AggregateRow(view: HabitMonthlyView?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Habit.entries.forEach { habit ->
            val count = view?.completedCount(habit) ?: 0
            val total = view?.daysInMonth ?: 0
            Column(
                modifier = Modifier
                    .border(width = 1.dp, color = MachineColors.WarmGray)
                    .padding(8.dp),
            ) {
                Text(
                    text = "${habit.code}·${habit.shortLabel}",
                    color = MachineColors.ReardenCopper,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = if (total == 0) "—" else "$count/$total",
                    color = MachineColors.Ivory,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ColumnHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "ДАТА",
            color = MachineColors.WarmGray,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(DATE_COL_WIDTH.dp),
        )
        Habit.entries.forEach { habit ->
            Text(
                text = habit.code,
                color = MachineColors.WarmGray,
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
    onToggle: (LocalDate, Habit) -> Unit,
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
                view = view,
                onToggle = onToggle,
            )
        }
    }
}

@Composable
private fun DayRow(
    date: LocalDate,
    isToday: Boolean,
    view: HabitMonthlyView?,
    onToggle: (LocalDate, Habit) -> Unit,
) {
    val rowBg = if (isToday) MachineColors.AtlasRed else Color.Transparent
    val dateColor = if (isToday) MachineColors.Ivory else MachineColors.WarmGray
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "%02d.%02d".format(date.dayOfMonth, date.monthValue),
            color = dateColor,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            modifier = Modifier
                .width(DATE_COL_WIDTH.dp)
                .padding(start = 4.dp),
        )
        Habit.entries.forEach { habit ->
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
            .padding(2.dp)
            .height(28.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(width = 1.dp, color = MachineColors.SteelEdge)
                .background(if (isOn) MachineColors.ReardenCopper else Color.Transparent),
        )
    }
}

private const val DATE_COL_WIDTH = 56

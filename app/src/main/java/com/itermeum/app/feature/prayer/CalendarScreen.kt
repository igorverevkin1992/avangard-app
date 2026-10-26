package com.itermeum.app.feature.prayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.designsystem.ImColors
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSpacing
import com.itermeum.app.core.liturgy.LiturgicalCalendar
import com.itermeum.app.core.liturgy.LiturgicalColor
import com.itermeum.app.core.liturgy.LiturgicalDay
import com.itermeum.app.core.liturgy.Rank
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(private val calendar: LiturgicalCalendar, clock: Clock) : ViewModel() {
    val today: LocalDate = clock.logicalToday()
    fun day(date: LocalDate): LiturgicalDay = calendar.day(date)
}

fun LiturgicalColor.accent(): Color = when (this) {
    LiturgicalColor.GREEN -> ImColors.Ordinary; LiturgicalColor.VIOLET -> ImColors.Penitential; LiturgicalColor.ROSE -> ImColors.Rose
    LiturgicalColor.WHITE -> ImColors.Festive; LiturgicalColor.RED -> ImColors.Martyr
}

/** `prayer/calendar/{yyyyMM}`: month grid coloured by liturgical colour; tap a day for its title and gospel. */
@Composable
fun CalendarScreen(yearMonth: String?, onBack: () -> Unit, viewModel: CalendarViewModel = hiltViewModel()) {
    var month by remember { mutableStateOf(runCatching { YearMonth.parse(yearMonth?.let { "${it.take(4)}-${it.drop(4)}" } ?: "") }.getOrElse { YearMonth.from(viewModel.today) }) }
    var selected by remember { mutableStateOf(viewModel.today) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru")).replaceFirstChar { it.uppercase() } + " ${month.year}", onBack = onBack) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
            TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
        }
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - 1
        val cells = offset + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        Row(Modifier.fillMaxWidth()) { listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(7) { c ->
                    val idx = r * 7 + c - offset
                    if (idx < 0 || idx >= month.lengthOfMonth()) { Box(Modifier.weight(1f).aspectRatio(1f)) } else {
                        val date = month.atDay(idx + 1)
                        val day = viewModel.day(date)
                        val bg = day.color.accent().copy(alpha = if (day.rank == Rank.WEEKDAY) 0.35f else 0.9f)
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).background(bg).clickable { selected = date },
                            contentAlignment = Alignment.Center,
                        ) {
                            // Weekdays are a faint tint over the dark background; celebrations are solid accent.
                            val fg = if (day.rank == Rank.WEEKDAY) MaterialTheme.colorScheme.onSurface else com.itermeum.app.core.designsystem.onAccent(day.color.accent())
                            Text((idx + 1).toString(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (date == selected) androidx.compose.ui.text.font.FontWeight.Bold else null), color = fg)
                        }
                    }
                }
            }
        }
        val d = viewModel.day(selected)
        ImPanel {
            Text(selected.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(d.title, style = MaterialTheme.typography.titleMedium)
            Text("Евангелие: ${d.gospelRef ?: "—"}", style = MaterialTheme.typography.bodyMedium)
            Text("Год ${d.sundayCycle.name}, будни ${d.weekdayCycle.name}" + if (d.reginaCaeli) " · Царица Небесная" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

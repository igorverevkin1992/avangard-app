package com.itermeum.app.feature.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import com.itermeum.app.R
import com.itermeum.app.core.data.WorkItemRepository.Due
import com.itermeum.app.core.database.entity.ItemStatus
import com.itermeum.app.core.database.entity.WorkItemEntity
import com.itermeum.app.core.designsystem.ImSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** «1 ч 20 мин» / «35 мин». */
@Composable
fun durationText(ms: Long): String {
    val min = (ms / 60_000L).toInt()
    return if (min >= 60) stringResource(R.string.duration_hm, min / 60, min % 60) else stringResource(R.string.duration_m, min)
}

private val dayFormat = DateTimeFormatter.ofPattern("EE, d MMM", Locale.forLanguageTag("ru"))

/** The due date as the screen says it: «Сегодня», «Завтра», «до пт, 7 нояб.», «просрочено с …». */
@Composable
fun dueText(due: LocalDate?, today: LocalDate): String? = when {
    due == null -> null
    due == today -> stringResource(R.string.due_today)
    due == today.plusDays(1) -> stringResource(R.string.due_tomorrow)
    due.isBefore(today) -> stringResource(R.string.due_overdue, due.format(dayFormat))
    else -> stringResource(R.string.due_on, due.format(dayFormat))
}

@Composable
fun Due.label(): String = stringResource(
    when (this) {
        Due.TODAY -> R.string.due_today
        Due.TOMORROW -> R.string.due_tomorrow
        Due.THIS_WEEK -> R.string.due_week
        Due.NONE -> R.string.due_none
    },
)

@Composable
fun DueChips(selected: Due?, onSelect: (Due) -> Unit) {
    FlowRowCompat { Due.entries.forEach { d -> FilterChip(selected = selected == d, onClick = { onSelect(d) }, label = { Text(d.label()) }) } }
}

/** Quick capture (D-64): one line and a due choice — nothing else is required. */
@Composable
fun CaptureBar(onAdd: (String, Due) -> Unit, hint: String = stringResource(R.string.capture_hint), showDue: Boolean = true) {
    var text by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableStateOf(Due.NONE) }
    fun add() { if (text.isNotBlank()) { onAdd(text.trim(), due); text = "" } }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, label = { Text(hint) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { add() }, enabled = text.isNotBlank()) { Text(stringResource(R.string.capture_add)) }
        }
        if (showDue) DueChips(due) { due = it }
    }
}

/** One item in a list: title, due date or status; tap opens its card. */
@Composable
fun ItemRow(item: WorkItemEntity, today: LocalDate, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = ImSpacing.tight), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val done = item.status != ItemStatus.OPEN
            Text(
                item.title, style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val sub = listOfNotNull(
                if (done) stringResource(R.string.item_status_done) else dueText(item.dueDate, today),
                item.type.label(),
                item.contactName?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            val overdue = !done && item.dueDate?.isBefore(today) == true
            Text(sub, style = MaterialTheme.typography.labelMedium, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

package com.itermeum.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.itermeum.app.R
import com.itermeum.app.core.designsystem.ImSpacing
import com.itermeum.app.core.designsystem.ImTimeField
import java.time.LocalTime

/**
 * D-70: one Mass a weekend — the day it is (Saturday evening or Sunday morning),
 * shown as a choice, and the time of that day only. The norm, the reminder and
 * the row in «Молитва» stand on that day alone.
 */
@Composable
fun MassDayPicker(
    massDay: Int, saturday: LocalTime, sunday: LocalTime,
    onDay: (Int) -> Unit, onSaturday: (LocalTime) -> Unit, onSunday: (LocalTime) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        Text(stringResource(R.string.mass_day_choice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            FilterChip(selected = massDay == SATURDAY, onClick = { onDay(SATURDAY) }, label = { Text(stringResource(R.string.mass_saturday)) })
            FilterChip(selected = massDay != SATURDAY, onClick = { onDay(SUNDAY) }, label = { Text(stringResource(R.string.mass_sunday)) })
        }
        if (massDay == SATURDAY) ImTimeField(stringResource(R.string.mass_time_label), saturday, onSaturday, Modifier.fillMaxWidth())
        else ImTimeField(stringResource(R.string.mass_time_label), sunday, onSunday, Modifier.fillMaxWidth())
    }
}

private const val SATURDAY = 6
private const val SUNDAY = 7

package com.itermeum.app.feature.path

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.database.entity.StreakType
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImMonoFamily
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import java.time.DayOfWeek

/** Section 9 «Путь» hub. */
@Composable
fun PathScreen(
    onCatechesis: (Long?) -> Unit, onReview: () -> Unit, onMonth: () -> Unit, onQuarter: () -> Unit, onChronicle: () -> Unit,
    viewModel: PathViewModel = hiltViewModel(),
) {
    val attended by viewModel.attended.collectAsState()
    val homework by viewModel.openHomework.collectAsState()
    val weekly by viewModel.weekly.collectAsState()
    val streaks by viewModel.streakStates.collectAsState()
    val done by viewModel.done.collectAsState()
    val profile by viewModel.profile.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.nav_path))

        ImSectionLabel(stringResource(R.string.path_streaks))
        ImPanel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StreakType.entries.forEach { t ->
                    val s = streaks.firstOrNull { it.type == t }
                    Column {
                        Text(stringResource(when (t) { StreakType.FIDELITY -> R.string.streak_fidelity; StreakType.WAKE -> R.string.streak_wake; StreakType.FROG -> R.string.streak_frog }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text((s?.current ?: 0).toString(), style = MaterialTheme.typography.headlineLarge.copy(fontFamily = ImMonoFamily))
                        Text(stringResource(R.string.streak_best, s?.best ?: 0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        ImSectionLabel(stringResource(R.string.path_catechesis))
        ImPanel(Modifier.clickable { onCatechesis(null) }) {
            Text(stringResource(R.string.catechesis_next, viewModel.nextClass.toString()), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.catechesis_attended, attended), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            homework?.let { hw ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.catechesis_homework_due, hw.homework.take(60), hw.homeworkDue?.toString() ?: ""), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.homeworkDone(hw) }) { Text(stringResource(R.string.done)) }
                }
            }
        }

        ImSectionLabel(stringResource(R.string.path_review))
        ImPanel(Modifier.clickable(onClick = onReview)) {
            Text(if (weekly?.completedAt != null) stringResource(R.string.review_done) else stringResource(R.string.review_pending), style = MaterialTheme.typography.bodyLarge)
            if (viewModel.today.dayOfWeek == DayOfWeek.SUNDAY && NormCatalog.WEEKLY_REVIEW !in done) Text(stringResource(R.string.review_today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        ImPanel(Modifier.clickable(onClick = onMonth)) { Text(stringResource(R.string.month_title), style = MaterialTheme.typography.bodyLarge) }
        ImPanel(Modifier.clickable(onClick = onQuarter)) { Text(stringResource(R.string.quarter_title, viewModel.quarter), style = MaterialTheme.typography.bodyLarge) }
        ImPanel(Modifier.clickable(onClick = onChronicle)) { Text(stringResource(R.string.chronicle_title), style = MaterialTheme.typography.bodyLarge) }
        if (profile.baptized) Text(stringResource(R.string.baptized_since, profile.baptismDate?.toString() ?: ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

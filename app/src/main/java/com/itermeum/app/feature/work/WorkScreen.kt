package com.itermeum.app.feature.work

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.database.entity.TaskStatus
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing

/** Section 9 «Труд» hub. */
@Composable
fun WorkScreen(
    onPlan: () -> Unit,
    onOutreach: () -> Unit,
    onLastStone: () -> Unit,
    onProjects: () -> Unit,
    onStudio: () -> Unit,
    onRitual: () -> Unit,
    viewModel: WorkViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        ImHeader(stringResource(R.string.nav_work))

        ImSectionLabel(stringResource(R.string.work_plan))
        ImPanel(Modifier.clickable(onClick = onPlan)) {
            if (ui.tasks.isEmpty()) {
                Text(stringResource(R.string.work_plan_empty), style = MaterialTheme.typography.bodyLarge)
            } else {
                ui.tasks.forEach { t ->
                    val mark = when (t.status) { TaskStatus.OPEN -> "□"; TaskStatus.DONE -> "■"; TaskStatus.DONE_WITH_DEBT -> "▣"; TaskStatus.MOVED -> "→" }
                    Text("$mark ${t.slot}. ${t.title}", style = MaterialTheme.typography.bodyLarge)
                }
                if (NormCatalog.DAY_PLAN in ui.done) Text(stringResource(R.string.work_plan_counted), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }

        ImSectionLabel(stringResource(R.string.work_outreach))
        ImPanel(Modifier.clickable(onClick = onOutreach)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.outreach_counter, ui.attempts, ui.agreements), style = MaterialTheme.typography.titleLarge)
                Text("${ui.outreach.count { it.beforeCutoff }}/${ui.target}", style = MaterialTheme.typography.titleLarge,
                    color = if (viewModel.frogDone()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (ui.beforeCutoff) stringResource(R.string.outreach_until_cutoff) else stringResource(R.string.outreach_after_cutoff),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        ImSectionLabel(stringResource(R.string.work_last_stone))
        ImPanel(Modifier.clickable(onClick = onLastStone)) {
            val open = ui.tasks.count { it.status == TaskStatus.OPEN }
            Text(if (ui.tasks.isEmpty()) stringResource(R.string.work_plan_empty) else stringResource(R.string.last_stone_open, open), style = MaterialTheme.typography.bodyLarge)
        }

        ImSectionLabel(stringResource(R.string.work_ritual))
        ImPanel(Modifier.clickable(onClick = onRitual)) {
            Text(stringResource(R.string.ritual_start), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.ritual_count, ui.rituals), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        ImSectionLabel(stringResource(R.string.work_projects))
        ImPanel(Modifier.clickable(onClick = onProjects)) { Text(stringResource(R.string.projects_open), style = MaterialTheme.typography.bodyLarge) }
        ImPanel(Modifier.clickable(onClick = onStudio)) { Text(stringResource(R.string.studio_open), style = MaterialTheme.typography.bodyLarge) }
    }
}

package com.itermeum.app.feature.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.database.entity.TaskStatus
import com.itermeum.app.core.designsystem.ImCollapsible
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImNavRow
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSpacing
import kotlinx.coroutines.delay

/**
 * «Труд» in the order of the working day (D-33): script before work →
 * plan → calls → last stone. Ritual, projects and studio are folded in «Ещё».
 */
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
        ScriptCard(viewModel)

        val open = ui.tasks.count { it.status == TaskStatus.OPEN }
        ImNavRow(
            title = stringResource(R.string.work_step_plan),
            subtitle = if (ui.tasks.isEmpty()) stringResource(R.string.work_plan_empty)
            else ui.tasks.sortedBy { it.slot }.joinToString("\n") { (if (it.status == TaskStatus.OPEN) "· " else "✓ ") + it.title },
            done = NormCatalog.DAY_PLAN in ui.done,
            onClick = onPlan,
        )
        // D-44: the queue of open tasks — any of them can jump to «next» without opening the plan.
        val queue = ui.tasks.filter { it.status == TaskStatus.OPEN }.sortedBy { it.slot }
        if (queue.size > 1) {
            ImPanel {
                Text(stringResource(R.string.work_queue_now, queue.first().title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                queue.drop(1).forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.makeNext(t) }) { Text(stringResource(R.string.work_make_next)) }
                    }
                }
            }
        }
        ImNavRow(
            title = stringResource(R.string.work_step_outreach),
            subtitle = stringResource(R.string.work_outreach_today, ui.attempts, ui.agreements),
            onClick = onOutreach,
        )
        ImNavRow(
            title = stringResource(R.string.work_step_last_stone),
            subtitle = if (ui.tasks.isEmpty()) stringResource(R.string.work_plan_empty) else stringResource(R.string.last_stone_open, open),
            done = NormCatalog.LAST_STONE in ui.done,
            onClick = onLastStone,
        )

        ImCollapsible(title = stringResource(R.string.more)) {
            ImNavRow(stringResource(R.string.ritual_start), onRitual, subtitle = stringResource(R.string.ritual_count, ui.rituals))
            ImNavRow(stringResource(R.string.projects_open), onProjects)
            ImNavRow(stringResource(R.string.studio_open), onStudio)
        }
    }
}

/** Section 5.5: «Поработал над сценарием» — before 12:00, not a norm, no streak effect. */
@Composable
private fun ScriptCard(viewModel: WorkViewModel) {
    val active by viewModel.activeScript.collectAsState()
    val count by viewModel.scriptSessionsToday.collectAsState()
    if (!viewModel.beforeWork() && active == null) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) { while (active != null) { delay(1_000); now = System.currentTimeMillis() } }
    ImPanel {
        val a = active
        if (a == null) {
            ImSecondaryButton(stringResource(R.string.script_button) + if (count > 0) " ($count)" else "", onClick = viewModel::startScript)
        } else {
            val el = now - a.startedAt
            Text(stringResource(R.string.script_running, "%02d:%02d".format(el / 60_000, (el / 1000) % 60)), style = MaterialTheme.typography.titleLarge)
            ImSecondaryButton(stringResource(R.string.script_stop), onClick = viewModel::stopScript)
        }
    }
}

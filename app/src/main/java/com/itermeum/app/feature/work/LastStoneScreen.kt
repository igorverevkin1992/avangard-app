package com.itermeum.app.feature.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.database.entity.TaskStatus
import com.itermeum.app.core.database.entity.TaskType
import com.itermeum.app.core.database.entity.WorkTaskEntity
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSpacing

/** Section 5.4: 4-point closing checklist per task; «закрыто с долгом» without 4/4. */
@Composable
fun LastStoneScreen(onBack: () -> Unit, viewModel: WorkViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        ImHeader(stringResource(R.string.work_last_stone), onBack = onBack)
        Text(stringResource(R.string.last_stone_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ui.tasks.isEmpty()) Text(stringResource(R.string.work_plan_empty), style = MaterialTheme.typography.bodyLarge)
        ui.tasks.forEach { task -> TaskCard(task, viewModel) }
    }
}

@Composable
private fun TaskCard(task: WorkTaskEntity, viewModel: WorkViewModel) {
    // Four points by the kind of work (D-42): a call is not closed like a file.
    val items = stringArrayResource(
        when (task.type) {
            TaskType.OUTREACH -> R.array.checklist_outreach
            TaskType.SHOOT -> R.array.checklist_shoot
            TaskType.OPS -> R.array.checklist_ops
            TaskType.DOCS -> R.array.checklist_docs
            TaskType.AI_GEN -> R.array.checklist_ai_gen
            TaskType.SCRIPT -> R.array.checklist_script
        },
    ).toList()
    val checks = remember(task.id, task.status) { mutableStateListOf(false, false, false, false) }
    ImPanel {
        Text("${task.slot}. ${task.title}", style = MaterialTheme.typography.titleMedium)
        Text(task.type.label(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (task.status) {
            TaskStatus.OPEN -> {
                items.forEachIndexed { i, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checks[i], onCheckedChange = { checks[i] = it })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val full = checks.all { it }
                ImPrimaryButton(if (full) stringResource(R.string.last_stone_close) else stringResource(R.string.last_stone_close_debt), onClick = { viewModel.closeTask(task, checks.toList()) })
                ImSecondaryButton(stringResource(R.string.last_stone_move), onClick = { viewModel.moveTask(task) })
            }
            else -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (task.status) {
                            TaskStatus.DONE -> stringResource(R.string.status_done)
                            TaskStatus.DONE_WITH_DEBT -> stringResource(R.string.status_done_debt)
                            else -> stringResource(R.string.status_moved)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (task.status == TaskStatus.DONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { viewModel.reopenTask(task) }) { Text(stringResource(R.string.last_stone_reopen)) }
                }
            }
        }
    }
}

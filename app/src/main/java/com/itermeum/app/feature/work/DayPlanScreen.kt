package com.itermeum.app.feature.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.data.WorkRepository
import com.itermeum.app.core.database.entity.TaskType
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing

@Composable
fun TaskType.label(): String = stringResource(
    when (this) {
    TaskType.OUTREACH -> R.string.task_type_outreach
    TaskType.SHOOT -> R.string.task_type_shoot
    TaskType.OPS -> R.string.task_type_ops
    TaskType.DOCS -> R.string.task_type_docs
    TaskType.AI_GEN -> R.string.task_type_ai_gen
    TaskType.SCRIPT -> R.string.task_type_script
    },
)

/**
 * Section 5.2: at least three slots, slot 1 = OUTREACH (or SHOOT with
 * «кому позвонить после»); more slots can be added (D-41).
 */
@Composable
fun DayPlanScreen(onBack: () -> Unit, viewModel: WorkViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    val titles = remember { mutableStateListOf("", "", "") }
    val types = remember { mutableStateListOf(TaskType.OUTREACH, TaskType.OPS, TaskType.OPS) }
    fun addSlot() { titles += ""; types += TaskType.OPS }
    var followUp by remember { mutableStateOf("") }
    var intention by remember { mutableStateOf("") }
    var shootDay by remember { mutableStateOf(false) }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(ui.tasks, ui.plan) {
        if (!seeded && (ui.tasks.isNotEmpty() || ui.plan != null)) {
            while (titles.size < (ui.tasks.maxOfOrNull { it.slot } ?: 0)) addSlot()
            ui.tasks.forEach { t -> if (t.slot >= 1) { titles[t.slot - 1] = t.title; types[t.slot - 1] = t.type; if (t.slot == 1) followUp = t.followUpContact ?: "" } }
            intention = ui.plan?.intention ?: ""
            shootDay = ui.plan?.shootDay ?: false
            seeded = true
        }
    }
    val drafts = titles.indices.map { i -> WorkRepository.TaskDraft(i + 1, types[i], titles[i], followUpContact = if (i == 0) followUp else null) }
    val valid = viewModel.planIsValid(drafts, shootDay)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        ImHeader(stringResource(R.string.work_plan), onBack = onBack)
        Text(stringResource(R.string.plan_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.plan_shoot_day), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = shootDay, onCheckedChange = { shootDay = it; if (!it && types[0] == TaskType.SHOOT) types[0] = TaskType.OUTREACH })
        }
        titles.indices.forEach { i ->
            ImPanel {
                ImSectionLabel(stringResource(R.string.plan_slot, i + 1))
                // Wraps onto several lines: six types do not fit a phone width.
                FlowRowCompat {
                    val allowed = if (i == 0) listOf(TaskType.OUTREACH) + (if (shootDay) listOf(TaskType.SHOOT) else emptyList()) else TaskType.entries
                    allowed.forEach { t -> FilterChip(selected = types[i] == t, onClick = { types[i] = t }, label = { Text(t.label()) }) }
                }
                OutlinedTextField(value = titles[i], onValueChange = { titles[i] = it }, label = { Text(stringResource(R.string.plan_task_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (i == 0 && types[0] == TaskType.SHOOT) {
                    OutlinedTextField(value = followUp, onValueChange = { followUp = it }, label = { Text(stringResource(R.string.plan_follow_up)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                // The first three are the plan itself; added slots can go again.
                if (i >= MIN_SLOTS) {
                    TextButton(onClick = { titles.removeAt(i); types.removeAt(i) }) { Text(stringResource(R.string.plan_remove_slot)) }
                }
            }
        }
        if (titles.size < MAX_SLOTS) ImSecondaryButton(stringResource(R.string.plan_add_slot), { addSlot() }, Modifier.fillMaxWidth())
        OutlinedTextField(value = intention, onValueChange = { intention = it }, label = { Text(stringResource(R.string.plan_intention)) }, modifier = Modifier.fillMaxWidth())
        if (!ui.beforeCutoff && ui.tasks.isNotEmpty()) {
            Text(stringResource(R.string.plan_after_cutoff), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ImPrimaryButton(stringResource(R.string.save), enabled = valid, onClick = { viewModel.savePlan(drafts, intention.ifBlank { null }, shootDay, onBack) })
    }
}

private const val MIN_SLOTS = 3
private const val MAX_SLOTS = 10

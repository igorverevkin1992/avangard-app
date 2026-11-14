package com.itermeum.app.feature.work

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext
import com.itermeum.app.core.designsystem.ImMonoFamily
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
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
    onOpenPrayer: (String) -> Unit = {},
    onOpenItem: (Long) -> Unit = {},
    viewModel: WorkViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val busy = stringResource(R.string.focus_busy)
    fun focus(t: com.itermeum.app.core.database.entity.WorkTaskEntity) =
        viewModel.startFocus(t) { ok -> if (!ok) Toast.makeText(context, busy, Toast.LENGTH_LONG).show() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        ImHeader(stringResource(R.string.nav_work))
        ImNavRow(stringResource(R.string.work_prayer_before), { onOpenPrayer("before_work") }, subtitle = stringResource(R.string.work_prayer_before_sub), done = NormCatalog.WORK_PRAYER in ui.done)
        // D-77: St Joseph, the patron of work — a prayer for the blessing of the day's work (not a norm).
        ImNavRow(stringResource(R.string.work_prayer_joseph), { onOpenPrayer("st_joseph_work") }, subtitle = stringResource(R.string.work_prayer_joseph_sub))
        ScriptCard(viewModel)
        FocusPanel(viewModel)
        // D-64: anything that comes to mind goes in here at once — it will not be lost.
        CaptureBar(onAdd = viewModel::capture)

        val open = ui.tasks.count { it.status == TaskStatus.OPEN }
        ImNavRow(
            title = stringResource(R.string.work_step_plan),
            subtitle = if (ui.tasks.isEmpty()) stringResource(R.string.work_plan_empty)
            else ui.tasks.sortedBy { it.slot }.joinToString("\n") { (if (it.status == TaskStatus.OPEN) "· " else "✓ ") + it.title },
            done = NormCatalog.DAY_PLAN in ui.done,
            onClick = onPlan,
        )
        // D-44: the queue of open tasks — any of them can jump to «next» without opening the plan.
        // D-64: a tap opens the task's card; the current one can run under focus.
        val queue = ui.tasks.filter { it.status == TaskStatus.OPEN }.sortedBy { it.slot }
        if (queue.isNotEmpty()) {
            ImPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.work_queue_now, queue.first().title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).clickable { viewModel.openTask(queue.first(), onOpenItem) },
                    )
                    TextButton(onClick = { focus(queue.first()) }) { Text(stringResource(R.string.focus_start)) }
                }
                queue.drop(1).forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).clickable { viewModel.openTask(t, onOpenItem) })
                        TextButton(onClick = { viewModel.makeNext(t) }) { Text(stringResource(R.string.work_make_next)) }
                    }
                }
            }
        }
        val focusMs by viewModel.focusToday.collectAsState()
        if (ui.tasks.isNotEmpty()) {
            Text(
                stringResource(R.string.work_today_summary, ui.tasks.count { it.status == TaskStatus.DONE || it.status == TaskStatus.DONE_WITH_DEBT }, ui.tasks.count { it.status == TaskStatus.MOVED }, durationText(focusMs)),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

        Inbox(viewModel, onOpenItem)

        ImCollapsible(title = stringResource(R.string.more)) {
            ImNavRow(stringResource(R.string.ritual_start), onRitual, subtitle = stringResource(R.string.ritual_count, ui.rituals))
            ImNavRow(stringResource(R.string.projects_open), onProjects)
            ImNavRow(stringResource(R.string.studio_open), onStudio)
        }
    }
}

/** D-64: open items not in today's plan, by due date; search over all items. */
@Composable
private fun Inbox(viewModel: WorkViewModel, onOpenItem: (Long) -> Unit) {
    val inbox by viewModel.inbox.collectAsState()
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    ImSectionLabel(stringResource(R.string.inbox_title))
    OutlinedTextField(
        value = query, onValueChange = viewModel::setQuery, label = { Text(stringResource(R.string.search_hint)) }, singleLine = true,
        trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { viewModel.setQuery("") }) { Text("×") } },
        modifier = Modifier.fillMaxWidth(),
    )
    ImPanel {
        if (query.isNotBlank()) {
            if (results.isEmpty()) Text(stringResource(R.string.search_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            results.forEach { item -> ItemRow(item, viewModel.date, onClick = { onOpenItem(item.id) }) }
        } else {
            if (inbox.isEmpty()) Text(stringResource(R.string.inbox_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            inbox.forEach { item ->
                ItemRow(item, viewModel.date, onClick = { onOpenItem(item.id) }) {
                    TextButton(onClick = { viewModel.planToday(item.id) }) { Text(stringResource(R.string.inbox_to_plan)) }
                }
            }
        }
    }
}

/** D-64: the running focus, mirrored from the timer card in the shade. */
@Composable
private fun FocusPanel(viewModel: WorkViewModel) {
    val session by viewModel.session.collectAsState()
    val s = session?.takeIf { it.kind == com.itermeum.app.core.data.TimerController.Kind.FOCUS } ?: return
    var now by remember { mutableLongStateOf(viewModel.now()) }
    LaunchedEffect(s) { while (true) { now = viewModel.now(); delay(1_000) } }
    ImPanel {
        Text(stringResource(R.string.focus_card_title, s.label.orEmpty()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        val el = s.elapsed(now)
        Text("%d:%02d:%02d".format(el / 3_600_000, (el / 60_000) % 60, (el / 1000) % 60), style = MaterialTheme.typography.titleLarge.copy(fontFamily = ImMonoFamily))
        Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            ImSecondaryButton(stringResource(R.string.focus_pause), { viewModel.pauseFocus() }, Modifier.weight(1f))
            ImPrimaryButton(stringResource(R.string.focus_done), { viewModel.doneFocus() }, Modifier.weight(1f))
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

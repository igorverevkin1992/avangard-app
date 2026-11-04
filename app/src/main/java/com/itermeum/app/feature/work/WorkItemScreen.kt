package com.itermeum.app.feature.work

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.R
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.data.ProjectRepository
import com.itermeum.app.core.data.TimerController
import com.itermeum.app.core.data.WorkItemRepository
import com.itermeum.app.core.data.WorkItemRepository.Check
import com.itermeum.app.core.data.WorkItemRepository.Due
import com.itermeum.app.core.data.WorkRepository
import com.itermeum.app.core.database.entity.ItemStatus
import com.itermeum.app.core.database.entity.TaskType
import com.itermeum.app.core.database.entity.WorkItemEntity
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImMonoFamily
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import com.itermeum.app.sync.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WorkItemViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val items: WorkItemRepository,
    private val work: WorkRepository,
    projectRepo: ProjectRepository,
    private val focus: FocusLauncher,
    private val widget: WidgetUpdater,
    private val clock: Clock,
) : ViewModel() {
    val id: Long = savedState["id"] ?: 0L
    val today = clock.logicalToday()

    /** Status, due date and spent time as stored; the editable fields live in [draft]. */
    val item: StateFlow<WorkItemEntity?> = items.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val projects = projectRepo.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val inPlan: StateFlow<Boolean> = work.observeTasks(today).map { ts -> ts.any { it.itemId == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val session = focus.session

    private val _draft = MutableStateFlow<WorkItemEntity?>(null)
    val draft: StateFlow<WorkItemEntity?> = _draft.asStateFlow()
    init { viewModelScope.launch { _draft.value = items.get(id) } }

    // Typing is saved shortly after it stops, also when the screen is already closed.
    private val saver = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    fun edit(now: Boolean = false, change: (WorkItemEntity) -> WorkItemEntity) {
        val d = _draft.value ?: return
        val next = change(d)
        _draft.value = next
        pending?.cancel()
        pending = saver.launch { if (!now) delay(SAVE_DELAY_MS); save(next) }
    }

    /** Only the card's own fields: status, due date and focus time are changed elsewhere meanwhile. */
    private suspend fun save(d: WorkItemEntity) {
        val cur = items.get(id) ?: return
        val merged = cur.copy(
            title = d.title.trim().ifBlank { cur.title }, type = d.type, notes = d.notes?.takeIf { it.isNotBlank() },
            contactName = d.contactName?.trim()?.takeIf { it.isNotEmpty() }, contactPhone = d.contactPhone?.trim()?.takeIf { it.isNotEmpty() },
            link = d.link?.trim()?.takeIf { it.isNotEmpty() }, checklistJson = d.checklistJson, projectId = d.projectId,
        )
        if (merged != cur) { items.update(merged); widget.requestUpdate() }
    }

    private suspend fun flush() { pending?.join(); _draft.value?.let { save(it) } }

    fun checklist(d: WorkItemEntity): List<Check> = items.checklist(d)
    fun setChecklist(list: List<Check>) = edit(now = true) { it.copy(checklistJson = items.checklistJson(list)) }

    /** The chip that matches the stored date, if any. */
    fun dueOf(item: WorkItemEntity): Due? = Due.entries.firstOrNull { items.dueDate(it, today) == item.dueDate }

    private fun act(block: suspend () -> Unit) = viewModelScope.launch { flush(); block(); widget.requestUpdate() }

    fun setDue(due: Due) = act { items.setDue(id, due) }
    fun planToday() = act { items.planToday(id) }
    fun reopen() = act { work.reopenItem(id) }

    /** «Готово»: a running focus on it is recorded first. */
    fun complete() = act { if (focusingHere()) focus.done() else work.completeItem(id) }

    fun delete(onDone: () -> Unit) = act { if (focusingHere()) focus.pause(); items.delete(id); onDone() }

    fun startFocus(onResult: (Boolean) -> Unit) = viewModelScope.launch { flush(); onResult(focus.start(id)) }
    fun pauseFocus() = viewModelScope.launch { focus.pause() }
    private fun focusingHere() = session.value?.let { it.kind == TimerController.Kind.FOCUS && it.refId == id } == true
    fun now() = clock.nowEpochMillis()

    companion object { private const val SAVE_DELAY_MS = 600L }
}

/**
 * D-64: the card of one task — what it is, its steps, who to call, the link,
 * the project, the time spent on it. Every slot of the day's plan has one.
 */
@Composable
fun WorkItemScreen(onBack: () -> Unit, viewModel: WorkItemViewModel = hiltViewModel()) {
    val stored by viewModel.item.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val inPlan by viewModel.inPlan.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val session by viewModel.session.collectAsState()
    val context = LocalContext.current
    val busy = stringResource(R.string.focus_busy)
    val d = draft
    val item = stored
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        ImHeader(stringResource(R.string.item_title_label), onBack = onBack)
        if (d == null || item == null) return@Column
        val open = item.status == ItemStatus.OPEN
        OutlinedTextField(
            value = d.title, onValueChange = { v -> viewModel.edit { it.copy(title = v) } },
            textStyle = MaterialTheme.typography.titleMedium.copy(textDecoration = if (open) null else TextDecoration.LineThrough),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        // Time and the two main actions.
        ImPanel {
            val focusHere = session?.takeIf { it.kind == TimerController.Kind.FOCUS && it.refId == item.id }
            if (focusHere != null) {
                var now by remember { mutableLongStateOf(viewModel.now()) }
                LaunchedEffect(focusHere) { while (true) { now = viewModel.now(); delay(1_000) } }
                val el = focusHere.elapsed(now)
                Text(stringResource(R.string.focus_start), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("%d:%02d:%02d".format(el / 3_600_000, (el / 60_000) % 60, (el / 1000) % 60), style = MaterialTheme.typography.titleLarge.copy(fontFamily = ImMonoFamily))
                Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                    ImSecondaryButton(stringResource(R.string.focus_pause), { viewModel.pauseFocus() }, Modifier.weight(1f))
                    ImPrimaryButton(stringResource(R.string.focus_done), { viewModel.complete() }, Modifier.weight(1f))
                }
            } else {
                Text(stringResource(R.string.item_spent, durationText(item.spentMs)), style = MaterialTheme.typography.bodyMedium)
                if (open) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                        ImSecondaryButton(stringResource(R.string.focus_start), {
                            viewModel.startFocus { ok -> if (!ok) Toast.makeText(context, busy, Toast.LENGTH_LONG).show() }
                        }, Modifier.weight(1f))
                        ImPrimaryButton(stringResource(R.string.item_done), { viewModel.complete() }, Modifier.weight(1f))
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.item_status_done), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.reopen() }) { Text(stringResource(R.string.item_reopen)) }
                    }
                }
            }
        }

        if (open) {
            ImSectionLabel(stringResource(R.string.item_due) + (dueText(item.dueDate, viewModel.today)?.let { " · $it" } ?: ""))
            DueChips(viewModel.dueOf(item)) { viewModel.setDue(it) }
            if (inPlan) Text(stringResource(R.string.item_in_plan), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            else ImSecondaryButton(stringResource(R.string.item_plan_today), { viewModel.planToday() }, Modifier.fillMaxWidth())
        }

        ImSectionLabel(stringResource(R.string.item_type))
        FlowRowCompat {
            TaskType.entries.forEach { t -> FilterChip(selected = d.type == t, onClick = { viewModel.edit(now = true) { it.copy(type = t) } }, label = { Text(t.label()) }) }
        }

        ImSectionLabel(stringResource(R.string.item_checklist))
        Checklist(viewModel.checklist(d), viewModel::setChecklist)

        OutlinedTextField(
            value = d.notes.orEmpty(), onValueChange = { v -> viewModel.edit { it.copy(notes = v) } },
            label = { Text(stringResource(R.string.item_notes)) }, minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        ImSectionLabel(stringResource(R.string.item_contact))
        OutlinedTextField(
            value = d.contactName.orEmpty(), onValueChange = { v -> viewModel.edit { it.copy(contactName = v) } },
            label = { Text(stringResource(R.string.item_contact_name)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = d.contactPhone.orEmpty(), onValueChange = { v -> viewModel.edit { it.copy(contactPhone = v) } },
                label = { Text(stringResource(R.string.item_contact_phone)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
            )
            val phone = d.contactPhone?.trim().orEmpty()
            TextButton(enabled = phone.isNotEmpty(), onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))) }
            }) { Text(stringResource(R.string.item_call)) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = d.link.orEmpty(), onValueChange = { v -> viewModel.edit { it.copy(link = v) } },
                label = { Text(stringResource(R.string.item_link)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.weight(1f),
            )
            val link = d.link?.trim().orEmpty()
            TextButton(enabled = link.isNotEmpty(), onClick = {
                val uri = if ("://" in link || link.startsWith("mailto:")) link else "https://$link"
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
            }) { Text(stringResource(R.string.item_link_open)) }
        }

        ImSectionLabel(stringResource(R.string.item_project))
        FlowRowCompat {
            FilterChip(selected = d.projectId == null, onClick = { viewModel.edit(now = true) { it.copy(projectId = null) } }, label = { Text(stringResource(R.string.item_project_none)) })
            projects.forEach { p -> FilterChip(selected = d.projectId == p.id, onClick = { viewModel.edit(now = true) { it.copy(projectId = p.id) } }, label = { Text(p.title) }) }
        }

        var confirm by remember { mutableStateOf(false) }
        TextButton(onClick = { if (confirm) viewModel.delete(onBack) else confirm = true }) {
            Text(stringResource(if (confirm) R.string.item_delete_confirm else R.string.item_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** The task's own steps: tick, add, remove. */
@Composable
private fun Checklist(list: List<Check>, onChange: (List<Check>) -> Unit) {
    val removeLabel = stringResource(R.string.item_remove_check)
    ImPanel {
        list.forEachIndexed { i, c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = c.d, onCheckedChange = { v -> onChange(list.toMutableList().also { it[i] = c.copy(d = v) }) })
                Text(
                    c.t, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    textDecoration = if (c.d) TextDecoration.LineThrough else null,
                    color = if (c.d) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = { onChange(list.toMutableList().also { it.removeAt(i) }) }, modifier = Modifier.semantics { contentDescription = removeLabel }) { Text("×") }
            }
        }
        var text by remember { mutableStateOf("") }
        fun add() { if (text.isNotBlank()) { onChange(list + Check(text.trim())); text = "" } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.item_check_add)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { add() }, enabled = text.isNotBlank()) { Text(stringResource(R.string.capture_add)) }
        }
    }
}

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.R
import com.itermeum.app.core.data.ProjectRepository
import com.itermeum.app.core.database.entity.Market
import com.itermeum.app.core.database.entity.ProjectEntity
import com.itermeum.app.core.database.entity.ProjectStage
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun ProjectStage.label(): String = stringResource(
    when (this) {
    ProjectStage.CONCEPT -> R.string.stage_concept
    ProjectStage.SCRIPT -> R.string.stage_script
    ProjectStage.PREPRODUCTION -> R.string.stage_preproduction
    ProjectStage.SHOOTING -> R.string.stage_shooting
    ProjectStage.POST -> R.string.stage_post
    ProjectStage.RELEASED -> R.string.stage_released
    ProjectStage.ARCHIVED -> R.string.stage_archived
    },
)

@HiltViewModel
class ProjectsViewModel @Inject constructor(private val repo: ProjectRepository) : ViewModel() {
    val projects = repo.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun observe(id: Long) = repo.observeProject(id)
    fun ethics(p: ProjectEntity) = repo.ethics(p)
    fun canAdvance(p: ProjectEntity, to: ProjectStage) = repo.canAdvance(p, to)
    fun reflection(p: ProjectEntity) = repo.reflection(p)
    fun create(title: String, market: Market, format: String, dedication: String, onCreated: (Long) -> Unit) =
        viewModelScope.launch { onCreated(repo.create(title, market, format, dedication)) }
    fun setEthics(p: ProjectEntity, values: List<Boolean>) = viewModelScope.launch { repo.update(p.copy(ethicsJson = repo.ethicsJson(values))) }
    fun setStage(p: ProjectEntity, stage: ProjectStage) = viewModelScope.launch { if (repo.canAdvance(p, stage)) repo.update(p.copy(stage = stage)) }
    fun setDedication(p: ProjectEntity, text: String) = viewModelScope.launch { repo.update(p.copy(dedication = text)) }
    fun setReflection(p: ProjectEntity, text: String) = viewModelScope.launch { repo.setReflection(p, text) }
}

/** Section 5.16: projects as offerings — no contacts, budgets or deals. */
@Composable
fun ProjectsScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, viewModel: ProjectsViewModel = hiltViewModel()) {
    val projects by viewModel.projects.collectAsState()
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.work_projects), onBack = onBack)
        if (creating) {
            var title by remember { mutableStateOf("") }
            val defaultFormat = stringResource(R.string.project_format_default)
            var format by remember { mutableStateOf(defaultFormat) }
            var dedication by remember { mutableStateOf("") }
            var market by remember { mutableStateOf(Market.RU) }
            ImPanel {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.project_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.tight)) {
                    Market.entries.forEach { m -> FilterChip(selected = market == m, onClick = { market = m }, label = { Text(m.name) }) }
                }
                OutlinedTextField(value = format, onValueChange = { format = it }, label = { Text(stringResource(R.string.project_format)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dedication, onValueChange = { dedication = it }, label = { Text(stringResource(R.string.project_dedication)) }, modifier = Modifier.fillMaxWidth())
                ImPrimaryButton(stringResource(R.string.save), enabled = title.isNotBlank() && dedication.isNotBlank(), onClick = {
                    viewModel.create(title, market, format, dedication) { creating = false; onOpen(it) }
                })
            }
        } else {
            ImSecondaryButton(stringResource(R.string.project_new), onClick = { creating = true })
        }
        projects.forEach { p ->
            ImPanel(Modifier.clickable { onOpen(p.id) }) {
                Text(p.title, style = MaterialTheme.typography.titleMedium)
                Text("${p.market.name} · ${p.format} · ${p.stage.label()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(p.dedication, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ProjectDetailScreen(id: Long, onBack: () -> Unit, viewModel: ProjectsViewModel = hiltViewModel()) {
    val project by viewModel.observe(id).collectAsState(initial = null)
    val p = project ?: return
    val ethics = viewModel.ethics(p)
    var reflection by remember(p.id) { mutableStateOf(viewModel.reflection(p) ?: "") }
    val criteria = listOf(
        stringResource(R.string.ethics_1), stringResource(R.string.ethics_2), stringResource(R.string.ethics_3), stringResource(R.string.ethics_4),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(p.title, onBack = onBack)
        Text("${p.market.name} · ${p.format}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ImSectionLabel(stringResource(R.string.project_dedication))
        ImPanel { Text(p.dedication, style = MaterialTheme.typography.bodyLarge) }

        ImSectionLabel(stringResource(R.string.project_stage))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ImSpacing.tight)) {
            ProjectStage.entries.take(4).forEach { s -> FilterChip(selected = p.stage == s, onClick = { viewModel.setStage(p, s) }, enabled = viewModel.canAdvance(p, s), label = { Text(s.label()) }) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ImSpacing.tight)) {
            ProjectStage.entries.drop(4).forEach { s -> FilterChip(selected = p.stage == s, onClick = { viewModel.setStage(p, s) }, enabled = viewModel.canAdvance(p, s), label = { Text(s.label()) }) }
        }
        if (!ethics.all { it }) Text(stringResource(R.string.ethics_required), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        ImSectionLabel(stringResource(R.string.project_ethics))
        ImPanel {
            criteria.forEachIndexed { i, text ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ethics[i], onCheckedChange = { v -> viewModel.setEthics(p, ethics.toMutableList().also { it[i] = v }) })
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (p.stage == ProjectStage.RELEASED || p.stage == ProjectStage.ARCHIVED) {
            ImSectionLabel(stringResource(R.string.project_reflection))
            OutlinedTextField(value = reflection, onValueChange = { reflection = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.project_reflection_hint)) })
            ImSecondaryButton(stringResource(R.string.save), onClick = { viewModel.setReflection(p, reflection) })
        }
    }
}

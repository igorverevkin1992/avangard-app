package com.itermeum.app.feature.work

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.R
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.data.ProjectRepository
import com.itermeum.app.core.database.entity.GoalStatus
import com.itermeum.app.core.database.entity.QuarterGoalEntity
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StudioViewModel @Inject constructor(private val repo: ProjectRepository, clock: Clock) : ViewModel() {
    val quarter: String = ProjectRepository.quarterOf(clock.logicalToday())
    val goals = repo.observeGoals(quarter).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val income = repo.observeIncome().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun addGoal(text: String) = viewModelScope.launch { if (text.isNotBlank()) repo.upsertGoal(QuarterGoalEntity(quarter = quarter, text = text.trim())) }
    fun setStatus(g: QuarterGoalEntity, s: GoalStatus) = viewModelScope.launch { repo.setGoalStatus(g, s) }
    fun delete(g: QuarterGoalEntity) = viewModelScope.launch { repo.deleteGoal(g.id) }
    fun setIncome(amount: Long) = viewModelScope.launch { repo.setIncome(quarter, amount) }
}

@Composable
fun GoalStatus.label(): String = stringResource(
    when (this) {
        GoalStatus.DONE -> R.string.goal_done
        GoalStatus.PARTIAL -> R.string.goal_partial
        GoalStatus.NOT_DONE -> R.string.goal_not_done
        GoalStatus.OPEN -> R.string.goal_open
    },
)

/** Section 5.15/5.16 «карта к студии»: quarter milestones and the line towards 50 M₽ a year. */
@Composable
fun StudioScreen(onBack: () -> Unit, viewModel: StudioViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsState()
    val income by viewModel.income.collectAsState()
    var newGoal by remember { mutableStateOf("") }
    var incomeText by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.studio_title), onBack = onBack)
        Text(stringResource(R.string.studio_goal), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        ImSectionLabel(stringResource(R.string.studio_income_line))
        ImPanel {
            val quarterGoal = ProjectRepository.YEARLY_GOAL_RUB / 4.0
            val line = MaterialTheme.colorScheme.primary
            val grid = MaterialTheme.colorScheme.outline
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val w = size.width; val h = size.height
                drawLine(grid, Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                drawLine(grid, Offset(0f, 1f), Offset(w, 1f), 1f)
                val pts = income.takeLast(8)
                if (pts.isNotEmpty()) {
                    val step = if (pts.size > 1) w / (pts.size - 1) else 0f
                    var prev: Offset? = null
                    pts.forEachIndexed { i, q ->
                        val y = h - (q.amountRub / quarterGoal).coerceIn(0.0, 1.0).toFloat() * h
                        val o = Offset(i * step, y)
                        prev?.let { drawLine(line, it, o, 3f) }
                        drawCircle(line, 5f, o)
                        prev = o
                    }
                }
            }
            Text(stringResource(R.string.studio_income_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            income.lastOrNull()?.let { Text("${it.quarter}: ${"%,d".format(it.amountRub)} ₽", style = MaterialTheme.typography.bodyLarge) }
            Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                OutlinedTextField(value = incomeText, onValueChange = { incomeText = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.studio_income_quarter, viewModel.quarter)) }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = { incomeText.toLongOrNull()?.let(viewModel::setIncome); incomeText = "" }) { Text(stringResource(R.string.save)) }
            }
        }

        ImSectionLabel(stringResource(R.string.studio_goals, viewModel.quarter))
        goals.forEach { g ->
            ImPanel {
                Text(g.text, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.tight)) {
                    listOf(GoalStatus.DONE, GoalStatus.PARTIAL, GoalStatus.NOT_DONE).forEach { s ->
                        FilterChip(selected = g.status == s, onClick = { viewModel.setStatus(g, s) }, label = { Text(s.label()) })
                    }
                    TextButton(onClick = { viewModel.delete(g) }) { Text("×") }
                }
            }
        }
        if (goals.size < 4) {
            OutlinedTextField(value = newGoal, onValueChange = { newGoal = it }, label = { Text(stringResource(R.string.studio_goal_new)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ImSecondaryButton(stringResource(R.string.studio_goal_add), enabled = newGoal.isNotBlank(), onClick = { viewModel.addGoal(newGoal); newGoal = "" })
        }
    }
}

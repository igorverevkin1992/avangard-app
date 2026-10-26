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

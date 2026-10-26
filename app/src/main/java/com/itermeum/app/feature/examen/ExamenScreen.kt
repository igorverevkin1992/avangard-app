package com.itermeum.app.feature.examen

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.data.ExamenRepository
import com.itermeum.app.core.database.entity.FollowUpAnswer
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImMonoFamily
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import com.itermeum.app.core.designsystem.ImStepper
import com.itermeum.app.core.engine.ExamenValidator

@Composable
fun ExamenScreen(onBack: () -> Unit, viewModel: ExamenViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val keyguard = remember { context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    val unlock = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (it.resultCode == android.app.Activity.RESULT_OK) unlocked = true }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.hub_examen), onBack = onBack)
        // Section 5.17: optional PIN/biometrics via the phone's own lock screen.
        if (ui.pinRequired && !unlocked && keyguard.isDeviceSecure) {
            ImPanel { Text(stringResource(R.string.examen_locked), style = MaterialTheme.typography.bodyLarge) }
            ImPrimaryButton(stringResource(R.string.examen_unlock), onClick = {
                @Suppress("DEPRECATION")
                keyguard.createConfirmDeviceCredentialIntent(context.getString(R.string.hub_examen), null)?.let(unlock::launch) ?: run { unlocked = true }
            })
            return@Column
        }
        when (ui.step) {
            ExamenStep.CLOSED -> Text(stringResource(R.string.examen_closed), style = MaterialTheme.typography.bodyLarge)
            ExamenStep.CATCH_UP -> {
                // D-34: one minute to record a chaotic day — for the statistics and the audit, not the streaks.
                ImSectionLabel(stringResource(R.string.examen_catchup_title))
                Text(stringResource(R.string.examen_catchup_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ImStepper(stringResource(R.string.examen_catchup_heroes), ui.heroes, ui.heroesLogged..(ui.heroesLogged + 30), viewModel::setHeroes)
                ImStepper(stringResource(R.string.examen_catchup_agreements), ui.agreements, ui.agreementsLogged..maxOf(ui.agreementsLogged, ui.heroes), viewModel::setAgreements)
                if (ui.tasksTotal > 0) ImStepper(stringResource(R.string.examen_catchup_tasks, ui.tasksTotal), ui.tasksDone, ui.tasksDoneLogged..ui.tasksTotal, viewModel::setTasksDone)
                ImPrimaryButton(stringResource(R.string.save), onClick = { viewModel.saveCatchUp() })
                TextButton(onClick = viewModel::skipCatchUp) { Text(stringResource(R.string.examen_catchup_skip)) }
            }
            ExamenStep.PLAN_TOMORROW -> {
                ImSectionLabel(stringResource(R.string.examen_tomorrow_title))
                Text(stringResource(R.string.examen_tomorrow_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ui.tomorrowTitles.forEachIndexed { i, t ->
                    OutlinedTextField(value = t, onValueChange = { viewModel.setTomorrowTitle(i, it) }, label = { Text("${i + 1}") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                ImPrimaryButton(stringResource(R.string.save), enabled = ui.tomorrowTitles.first().isNotBlank(), onClick = { viewModel.saveTomorrowPlan() })
                TextButton(onClick = viewModel::skipTomorrowPlan) { Text(stringResource(R.string.examen_tomorrow_skip)) }
            }
            ExamenStep.FOLLOW_UP -> ui.previous?.let { (d, text) ->
                ImSectionLabel(stringResource(R.string.examen_previous, d.toString()))
                ImPanel { Text(text, style = MaterialTheme.typography.bodyLarge) }
                Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                    FilterChip(selected = ui.previousAnswer == FollowUpAnswer.KEPT, onClick = { viewModel.setPreviousAnswer(FollowUpAnswer.KEPT) }, label = { Text(stringResource(R.string.examen_kept)) })
                    FilterChip(selected = ui.previousAnswer == FollowUpAnswer.NOT_KEPT, onClick = { viewModel.setPreviousAnswer(FollowUpAnswer.NOT_KEPT) }, label = { Text(stringResource(R.string.examen_not_kept)) })
                }
                ImPrimaryButton(stringResource(R.string.next), enabled = ui.previousAnswer != null, onClick = viewModel::next)
            }
            ExamenStep.PRESENCE -> {
                ImSectionLabel(stringResource(R.string.examen_presence))
                ImPanel { Text(ui.presenceText, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center) }
                Text(stringResource(R.string.examen_presence_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(ui.presenceSecondsLeft.toString(), style = MaterialTheme.typography.displayLarge.copy(fontFamily = ImMonoFamily), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ImPrimaryButton(stringResource(R.string.next), enabled = ui.presenceSecondsLeft == 0, onClick = viewModel::next)
            }
            ExamenStep.GRATITUDE -> {
                ImSectionLabel(stringResource(R.string.examen_gratitude))
                Text(stringResource(R.string.examen_gratitude_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ui.gratitude.forEachIndexed { i, g ->
                    OutlinedTextField(value = g, onValueChange = { viewModel.setGratitude(i, it) }, label = { Text("${i + 1}") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                ImPrimaryButton(stringResource(R.string.next), enabled = ui.gratitude.count { it.isNotBlank() } >= 2, onClick = viewModel::next)
            }
            ExamenStep.AUDIT -> {
                val q = ui.questions.getOrNull(ui.auditIndex) ?: return@Column
                val current = ui.audit.getOrNull(ui.auditIndex) ?: ExamenRepository.Audit(ExamenRepository.Answer.PARTLY, "")
                var note by remember(ui.auditIndex) { mutableStateOf(current.note) }
                var answer by remember(ui.auditIndex) { mutableStateOf(current.answer) }
                ImSectionLabel("${q.index}/5 · ${q.title}")
                ImPanel { Text(q.text, style = MaterialTheme.typography.bodyLarge) }
                ui.hints[q.index]?.let { Text(stringResource(R.string.examen_hint, it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                    FilterChip(selected = answer == ExamenRepository.Answer.YES, onClick = { answer = ExamenRepository.Answer.YES }, label = { Text(stringResource(R.string.yes)) })
                    FilterChip(selected = answer == ExamenRepository.Answer.PARTLY, onClick = { answer = ExamenRepository.Answer.PARTLY }, label = { Text(stringResource(R.string.partly)) })
                    FilterChip(selected = answer == ExamenRepository.Answer.NO, onClick = { answer = ExamenRepository.Answer.NO }, label = { Text(stringResource(R.string.no)) })
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.examen_note)) }, modifier = Modifier.fillMaxWidth())
                ImPrimaryButton(stringResource(R.string.next), onClick = { viewModel.setAudit(answer, note); viewModel.next() })
            }
            ExamenStep.CONTRITION -> {
                ImSectionLabel(stringResource(R.string.examen_contrition))
                ImPanel { Text(ui.contritionText, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center) }
                Text(stringResource(R.string.examen_contrition_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ImPrimaryButton(stringResource(R.string.examen_said), onClick = viewModel::next)
            }
            ExamenStep.PROPOSITO -> {
                ImSectionLabel(stringResource(R.string.examen_proposito))
                Text(stringResource(R.string.examen_proposito_hint, ExamenValidator.TEMPLATE), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = ui.proposito, onValueChange = viewModel::setProposito, modifier = Modifier.fillMaxWidth(), isError = ui.propositoError != null,
                    supportingText = { ui.propositoError?.let { Text(when (it) {
                        ExamenValidator.Reason.TOO_SHORT -> stringResource(R.string.examen_err_short)
                        ExamenValidator.Reason.TOO_LONG -> stringResource(R.string.examen_err_long)
                        ExamenValidator.Reason.NOT_MEASURABLE -> stringResource(R.string.examen_err_vague)
                    }) } })
                ImPrimaryButton(stringResource(R.string.next), enabled = ui.previous == null || ui.previousAnswer != null, onClick = viewModel::submitProposito)
            }
            ExamenStep.AVE -> {
                ImSectionLabel(stringResource(R.string.examen_ave))
                ImPanel(Modifier.fillMaxWidth().clickable { viewModel.tapAve() }) {
                    Text(ui.aveText, style = MaterialTheme.typography.bodyLarge)
                    Text("${ui.aveCount}/3", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = ImMonoFamily), color = MaterialTheme.colorScheme.primary)
                }
                Text(stringResource(R.string.examen_ave_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ExamenStep.WHAT_BROKE -> {
                ImSectionLabel(stringResource(R.string.examen_what_broke))
                Text(stringResource(R.string.examen_what_broke_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = ui.whatBroke, onValueChange = viewModel::setWhatBroke, modifier = Modifier.fillMaxWidth())
                ImPrimaryButton(stringResource(R.string.done), enabled = ui.whatBroke.isNotBlank(), onClick = viewModel::finishWhatBroke)
            }
            ExamenStep.DONE -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ImSpacing.grid), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.examen_done_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.examen_done_text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    ImSecondaryButton(stringResource(R.string.done), onClick = onBack)
                }
            }
        }
    }
}

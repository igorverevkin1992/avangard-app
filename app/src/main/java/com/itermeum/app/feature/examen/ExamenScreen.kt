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
import androidx.compose.runtime.Composable
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
import com.itermeum.app.core.engine.ExamenValidator

@Composable
fun ExamenScreen(onBack: () -> Unit, viewModel: ExamenViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.hub_examen), onBack = onBack)
        when (ui.step) {
            ExamenStep.CLOSED -> Text(stringResource(R.string.examen_closed), style = MaterialTheme.typography.bodyLarge)
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
                ui.previous?.let { (d, text) ->
                    ImSectionLabel(stringResource(R.string.examen_previous, d.toString()))
                    ImPanel { Text(text, style = MaterialTheme.typography.bodyLarge) }
                    Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                        FilterChip(selected = ui.previousAnswer == FollowUpAnswer.KEPT, onClick = { viewModel.setPreviousAnswer(FollowUpAnswer.KEPT) }, label = { Text(stringResource(R.string.examen_kept)) })
                        FilterChip(selected = ui.previousAnswer == FollowUpAnswer.NOT_KEPT, onClick = { viewModel.setPreviousAnswer(FollowUpAnswer.NOT_KEPT) }, label = { Text(stringResource(R.string.examen_not_kept)) })
                    }
                }
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

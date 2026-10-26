package com.itermeum.app.feature.prayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.database.entity.SrsStage
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSpacing
import com.itermeum.app.core.engine.SrsEngine

@Composable
fun SrsStage.label(): String = stringResource(
    when (this) {
    SrsStage.READ -> R.string.srs_stage_read
    SrsStage.CLOZE_25 -> R.string.srs_stage_cloze_25
    SrsStage.CLOZE_50 -> R.string.srs_stage_cloze_50
    SrsStage.CLOZE_100 -> R.string.srs_stage_cloze_100
    SrsStage.RECALL -> R.string.srs_stage_recall
    SrsStage.KNOWN -> R.string.srs_stage_known
    },
)

/** Section 5.13: READ → CLOZE_25 → CLOZE_50 → CLOZE_100 → RECALL → KNOWN; session ≤5 min. */
@Composable
fun SrsScreen(onBack: () -> Unit, viewModel: SrsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.hub_srs), onBack = onBack)
        Text(stringResource(R.string.srs_known, ui.known, ui.total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val item = ui.queue.getOrNull(ui.index)
        val prayer = ui.prayer
        if (ui.finished || item == null || prayer == null) {
            Text(if (ui.queue.isEmpty()) stringResource(R.string.srs_nothing_due) else stringResource(R.string.srs_session_done), style = MaterialTheme.typography.bodyLarge)
            ImSecondaryButton(stringResource(R.string.done), onClick = onBack)
            return@Column
        }
        Text("${ui.index + 1}/${ui.queue.size} · ${prayer.title} · ${item.stage.label()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ImPanel {
            val tokens = viewModel.cloze(prayer, item)
            if (item.stage == SrsStage.RECALL && !ui.revealed) {
                Text(stringResource(R.string.srs_recall_prompt), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    buildAnnotatedString {
                        tokens.forEach { t ->
                            if (t.hidden && !ui.revealed) withStyle(MaterialTheme.typography.bodyLarge.toSpanStyle().copy(color = MaterialTheme.colorScheme.outline, textDecoration = TextDecoration.Underline)) { append("_".repeat(t.text.length.coerceAtLeast(3))) }
                            else append(t.text)
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        if (!ui.revealed && item.stage != SrsStage.READ) {
            ImPrimaryButton(stringResource(R.string.srs_reveal), onClick = viewModel::reveal)
        } else {
            Text(stringResource(R.string.srs_grade_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                ImSecondaryButton(stringResource(R.string.srs_forgot), { viewModel.grade(SrsEngine.Grade.FORGOT) }, Modifier.weight(1f))
                ImSecondaryButton(stringResource(R.string.srs_hard), { viewModel.grade(SrsEngine.Grade.HARD) }, Modifier.weight(1f))
                ImSecondaryButton(stringResource(R.string.srs_easy), { viewModel.grade(SrsEngine.Grade.EASY) }, Modifier.weight(1f))
            }
        }
    }
}

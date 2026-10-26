package com.itermeum.app.feature.work

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.database.entity.OutreachKind
import com.itermeum.app.core.database.entity.OutreachOutcome
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import java.time.LocalDate
import kotlinx.coroutines.delay

/** Section 5.3: counter, queue, «Позвонить» with a 5-second BEFORE_CALL overlay, outcome log. */
@Composable
fun OutreachScreen(onBack: () -> Unit, viewModel: WorkViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    var person by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var concrete by remember { mutableStateOf(true) }
    var kind by remember { mutableStateOf(OutreachKind.CALL) }
    var overlay by remember { mutableStateOf<String?>(null) }
    var afterRejection by remember { mutableStateOf<String?>(null) }
    var agreementDate by remember { mutableStateOf("") }

    if (overlay != null) {
        BeforeCallOverlay(overlay!!) {
            overlay = null
            val uri = if (phone.isNotBlank()) Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}") else null
            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        ImHeader(stringResource(R.string.work_outreach), onBack = onBack)
        ImPanel {
            Text(stringResource(R.string.outreach_counter, ui.attempts, ui.agreements), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.outreach_target_line, ui.outreach.count { it.beforeCutoff }, ui.target),
                style = MaterialTheme.typography.labelMedium, color = if (viewModel.frogDone()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (!ui.beforeCutoff) Text(stringResource(R.string.outreach_after_cutoff), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        afterRejection?.let {
            ImPanel { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
        }

        ImSectionLabel(stringResource(R.string.outreach_new))
        OutlinedTextField(value = person, onValueChange = { person = it }, label = { Text(stringResource(R.string.outreach_person)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = role, onValueChange = { role = it }, label = { Text(stringResource(R.string.outreach_role)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(stringResource(R.string.outreach_phone)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            FilterChip(selected = kind == OutreachKind.CALL, onClick = { kind = OutreachKind.CALL }, label = { Text(stringResource(R.string.outreach_call)) })
            FilterChip(selected = kind == OutreachKind.MESSAGE, onClick = { kind = OutreachKind.MESSAGE }, label = { Text(stringResource(R.string.outreach_message)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = concrete, onCheckedChange = { concrete = it })
            Text(stringResource(R.string.outreach_concrete), style = MaterialTheme.typography.bodyMedium)
        }
        val ready = person.isNotBlank() && concrete
        if (kind == OutreachKind.CALL) {
            var prayer by remember { mutableStateOf("") }
            LaunchedEffect(Unit) { prayer = viewModel.beforeCallPrayer() }
            ImPrimaryButton(stringResource(R.string.outreach_dial), enabled = ready, onClick = { overlay = prayer })
        }

        ImSectionLabel(stringResource(R.string.outreach_outcome))
        OutlinedTextField(value = agreementDate, onValueChange = { agreementDate = it }, label = { Text(stringResource(R.string.outreach_agreement_date)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val log: (OutreachOutcome) -> Unit = { outcome ->
            val date = runCatching { LocalDate.parse(agreementDate) }.getOrNull()
            viewModel.logOutreach(person, role.ifBlank { null }, kind, outcome, if (outcome == OutreachOutcome.AGREEMENT) date else null)
            person = ""; role = ""; phone = ""; agreementDate = ""
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            ImSecondaryButton(stringResource(R.string.outcome_reached), { log(OutreachOutcome.REACHED) }, Modifier.weight(1f), enabled = ready)
            ImSecondaryButton(stringResource(R.string.outcome_no_answer), { log(OutreachOutcome.NO_ANSWER) }, Modifier.weight(1f), enabled = ready)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            ImSecondaryButton(stringResource(R.string.outcome_agreement), { log(OutreachOutcome.AGREEMENT) }, Modifier.weight(1f), enabled = ready)
            ImSecondaryButton(stringResource(R.string.outcome_rejection), {
                log(OutreachOutcome.REJECTION)
            }, Modifier.weight(1f), enabled = ready)
        }
        LaunchedEffect(ui.outreach.size) {
            if (ui.outreach.lastOrNull()?.outcome == OutreachOutcome.REJECTION) afterRejection = viewModel.afterRejectionPrayer()
        }

        ImSectionLabel(stringResource(R.string.outreach_today))
        ui.outreach.asReversed().forEach { o ->
            ImPanel {
                Text("${o.personLabel}${o.role?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodyLarge)
                Text(o.outcome.label() + if (!o.beforeCutoff) stringResource(R.string.outreach_after_cutoff_short) else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun OutreachOutcome.label(): String = stringResource(
    when (this) {
    OutreachOutcome.REACHED -> R.string.outcome_short_reached
    OutreachOutcome.NO_ANSWER -> R.string.outcome_short_no_answer
    OutreachOutcome.AGREEMENT -> R.string.outcome_short_agreement
    OutreachOutcome.REJECTION -> R.string.outcome_short_rejection
    },
)

@Composable
fun BeforeCallOverlay(text: String, onDone: () -> Unit) {
    var left by remember { mutableStateOf(5) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1_000); left-- }; onDone() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(ImSpacing.screen), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ImSpacing.screen)) {
            Text(text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(left.toString(), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

package com.itermeum.app.feature.path

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.database.entity.GoalStatus
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImMonoFamily
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import com.itermeum.app.feature.work.FlowRowCompat
import com.itermeum.app.feature.work.label
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Section 5.14: next class, attendance, note form (topic, 3 theses, question, homework due next Saturday). */
@Composable
fun CatechesisScreen(noteId: Long?, onBack: () -> Unit, viewModel: PathViewModel = hiltViewModel()) {
    val notes by viewModel.notes.collectAsState()
    var topic by remember { mutableStateOf("") }
    var theses by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var homework by remember { mutableStateOf("") }
    // The class of this week: today on Saturday, otherwise the last Saturday.
    var date by remember { mutableStateOf(viewModel.today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SATURDAY))) }
    var existingId by remember { mutableStateOf(noteId) }
    LaunchedEffect(noteId, notes) {
        val n = noteId?.let { viewModel.note(it) } ?: notes.firstOrNull { it.logicalDate == date }.takeIf { existingId == null }
        // Editing the note for that Saturday instead of creating a second one.
        n?.let { topic = it.topic; theses = it.theses; question = it.question; homework = it.homework; date = it.logicalDate; existingId = it.id }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.path_catechesis), onBack = onBack)
        Text(stringResource(R.string.catechesis_place), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ImSectionLabel(stringResource(R.string.catechesis_note_for, date.toString()))
        OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text(stringResource(R.string.catechesis_topic)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = theses, onValueChange = { theses = it }, label = { Text(stringResource(R.string.catechesis_theses)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text(stringResource(R.string.catechesis_question)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = homework, onValueChange = { homework = it }, label = { Text(stringResource(R.string.catechesis_homework)) }, modifier = Modifier.fillMaxWidth())
        ImPrimaryButton(stringResource(R.string.save), enabled = topic.isNotBlank(), onClick = { viewModel.saveNote(date, topic, theses, question, homework, existingId, onBack) })
        ImSecondaryButton(stringResource(R.string.catechesis_missed_travel), onClick = { viewModel.missedForTravel(date); onBack() })

        ImSectionLabel(stringResource(R.string.catechesis_notes))
        notes.forEach { n ->
            ImPanel(Modifier.fillMaxWidth()) {
                Text("${n.logicalDate} · ${n.topic}", style = MaterialTheme.typography.titleMedium)
                if (n.theses.isNotBlank()) Text(n.theses, style = MaterialTheme.typography.bodyMedium)
                if (n.question.isNotBlank()) Text("? ${n.question}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (n.homework.isNotBlank()) Text(stringResource(R.string.catechesis_homework_label, n.homework) + if (n.homeworkDone) " ✓" else stringResource(R.string.catechesis_due, n.homeworkDue.toString()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { existingId = n.id; topic = n.topic; theses = n.theses; question = n.question; homework = n.homework; date = n.logicalDate }) { Text(stringResource(R.string.prayer_edit)) }
            }
        }
    }
}

/** Section 5.15: Sunday review — calls and agreements, fidelity days, 3-line plan, virtue of the week. */
@Composable
fun WeeklyReviewScreen(onBack: () -> Unit, viewModel: PathViewModel = hiltViewModel()) {
    val week by viewModel.week.collectAsState()
    val existing by viewModel.weekly.collectAsState()
    val plan = remember { mutableStateListOf("", "", "") }
    var virtue by remember { mutableStateOf("") }
    LaunchedEffect(existing) { existing?.let { e -> viewModel.decryptPlan(e).forEachIndexed { i, s -> if (i < 3) plan[i] = s }; virtue = e.virtue ?: "" } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.path_review), onBack = onBack)
        Text(stringResource(R.string.review_week_of, viewModel.weekStart.toString()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ImPanel {
            Stat(stringResource(R.string.stat_calls), week.calls)
            Stat(stringResource(R.string.stat_agreements), week.agreements)
            Stat(stringResource(R.string.stat_fidelity_days), week.fidelityDays)
        }
        ImSectionLabel(stringResource(R.string.review_plan))
        plan.forEachIndexed { i, s -> OutlinedTextField(value = s, onValueChange = { plan[i] = it }, label = { Text("${i + 1}") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        ImSectionLabel(stringResource(R.string.review_virtue))
        FlowRowCompat { viewModel.virtues.forEach { v -> FilterChip(selected = virtue == v, onClick = { virtue = v }, label = { Text(v) }) } }
        ImPrimaryButton(stringResource(R.string.save), enabled = virtue.isNotBlank() && plan.any { it.isNotBlank() }, onClick = { viewModel.saveWeekly(plan.toList(), virtue, onBack) })
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value.toString(), style = MaterialTheme.typography.bodyLarge.copy(fontFamily = ImMonoFamily))
    }
}

/** Section 3.6 / 5.15: counters without percentages or comparisons. */
@Composable
fun MonthSummaryScreen(onBack: () -> Unit, viewModel: PathViewModel = hiltViewModel()) {
    val m by viewModel.month.collectAsState()
    LaunchedEffect(Unit) { viewModel.markMonthViewed() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.month_title), onBack = onBack)
        ImPanel {
            Stat(stringResource(R.string.stat_fidelity_days), m.fidelityDays)
            Stat(stringResource(R.string.stat_calls), m.calls)
            Stat(stringResource(R.string.stat_agreements), m.agreements)
            Stat(stringResource(R.string.stat_prayers_known), m.prayersKnown)
            Stat(stringResource(R.string.stat_script_minutes), m.scriptMinutes)
            Stat(stringResource(R.string.stat_catechesis), m.catechesis)
        }
        Text(stringResource(R.string.month_freezes_reset), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Section 5.15: last Sunday of the quarter — 4 goals, 4 vanity questions (encrypted), income. */
@Composable
fun QuarterScreen(onBack: () -> Unit, viewModel: PathViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsState()
    val answers = remember { mutableStateListOf("", "", "", "") }
    var income by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.vanity()?.forEachIndexed { i, a -> if (i < 4) answers[i] = a } }
    val questions = listOf(stringResource(R.string.vanity_1), stringResource(R.string.vanity_2), stringResource(R.string.vanity_3), stringResource(R.string.vanity_4))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.quarter_title, viewModel.quarter), onBack = onBack)
        ImSectionLabel(stringResource(R.string.quarter_goals))
        if (goals.isEmpty()) Text(stringResource(R.string.quarter_no_goals), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        goals.forEach { g ->
            ImPanel {
                Text(g.text, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.tight)) {
                    listOf(GoalStatus.DONE, GoalStatus.PARTIAL, GoalStatus.NOT_DONE).forEach { s -> FilterChip(selected = g.status == s, onClick = { viewModel.setGoalStatus(g, s) }, label = { Text(s.label()) }) }
                }
            }
        }
        ImSectionLabel(stringResource(R.string.quarter_vanity))
        questions.forEachIndexed { i, q ->
            OutlinedTextField(value = answers[i], onValueChange = { answers[i] = it }, label = { Text(q) }, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(value = income, onValueChange = { income = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.studio_income_quarter, viewModel.quarter)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ImPrimaryButton(stringResource(R.string.save), enabled = answers.all { it.isNotBlank() }, onClick = { viewModel.saveQuarter(answers.toList(), income.toLongOrNull(), onBack) })
    }
}

/** Section 6.4 «Летопись»: a vertical line with diamonds for achieved milestones; quiet, no sound. */
@Composable
fun ChronicleScreen(onBack: () -> Unit, viewModel: PathViewModel = hiltViewModel()) {
    val milestones by viewModel.milestones.collectAsState()
    val achieved = milestones.filter { it.achievedAt != null }.sortedBy { it.achievedAt }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        ImHeader(stringResource(R.string.chronicle_title), onBack = onBack)
        Spacer(Modifier.height(ImSpacing.grid))
        if (achieved.isEmpty()) Text(stringResource(R.string.chronicle_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        achieved.forEach { m ->
            val meta = viewModel.milestoneMeta(m.id)
            val date = Instant.ofEpochMilli(m.achievedAt!!).atZone(ZoneId.systemDefault()).toLocalDate()
            Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(32.dp).fillMaxSize()) {
                    Box(Modifier.width(1.dp).weight(1f).background(MaterialTheme.colorScheme.outline))
                    Text("◆", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                    Box(Modifier.width(1.dp).weight(1f).background(MaterialTheme.colorScheme.outline))
                }
                Column(Modifier.padding(start = ImSpacing.card)) {
                    Text(meta?.title ?: m.id, style = MaterialTheme.typography.titleMedium)
                    Text("${meta?.latin ?: ""} · $date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val pending = milestones.filter { it.achievedAt == null }
        if (pending.isNotEmpty()) {
            Spacer(Modifier.height(ImSpacing.screen))
            ImSectionLabel(stringResource(R.string.chronicle_ahead))
            pending.forEach { m -> Text("· ${viewModel.milestoneMeta(m.id)?.title ?: m.id}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Section 6.4 reward: dark screen, Latin line, date, one line of Scripture; fade 600 ms, no sound. */
@Composable
fun MilestoneScreen(id: String, onDone: () -> Unit, viewModel: PathViewModel = hiltViewModel()) {
    val meta = viewModel.milestoneMeta(id)
    val milestones by viewModel.milestones.collectAsState()
    // The day it was reached (often at the night close), not the day it is viewed.
    val reached = milestones.firstOrNull { it.id == id }?.achievedAt?.let {
        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    } ?: viewModel.today
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    androidx.compose.animation.AnimatedVisibility(visible = visible, enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(600))) {
        Column(Modifier.fillMaxSize().padding(ImSpacing.screen), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(meta?.latin ?: "", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(ImSpacing.screen))
            Text(meta?.title ?: id, style = MaterialTheme.typography.headlineMedium)
            Text(reached.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(ImSpacing.screen))
            Text(meta?.scripture ?: "", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(ImSpacing.screen * 2))
            ImSecondaryButton(stringResource(R.string.done), onClick = onDone)
        }
    }
}

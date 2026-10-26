package com.itermeum.app.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.feature.prayer.accent
import com.itermeum.app.core.data.MorningRepository
import com.itermeum.app.core.database.entity.MorningStage
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImMotto
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import com.itermeum.app.core.engine.DayScheduleEngine
import com.itermeum.app.feature.onboarding.HyperOsChecks
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(onOpenSettings: () -> Unit, onMilestone: (String) -> Unit = {}, viewModel: TodayViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    val streaks by viewModel.streakStates.collectAsState()
    val unseen by viewModel.unseenMilestone.collectAsState()
    val calls by viewModel.calls.collectAsState()
    val agreements by viewModel.agreements.collectAsState()
    val banner by viewModel.banner.collectAsState()
    val lowerBar by viewModel.lowerBar.collectAsState()
    LaunchedEffect(unseen) { unseen?.let { m -> viewModel.markMilestoneSeen(m.id); onMilestone(m.id) } }
    val context = LocalContext.current
    val hyperOsOk = remember { HyperOsChecks.coreHealthy(context) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        ImHeader(
            title = ui.date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("ru"))),
            trailing = { TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings_title)) } },
        )
        ui.liturgical?.let {
            Text(it.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        ImMotto(stringResource(R.string.motto_serviam))
        if (!hyperOsOk) {
            ImPanel(Modifier.clickable(onClick = onOpenSettings)) {
                Text(stringResource(R.string.hyperos_reset_banner), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        banner?.let { ImPanel { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) } }
        val accent = ui.liturgical?.color?.let { it.accent() } ?: MaterialTheme.colorScheme.primary
        DayRing(
            total = ui.items.size,
            done = ui.items.map { it.normId in ui.done },
            accent = accent,
            calls = calls, agreements = agreements,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            com.itermeum.app.core.database.entity.StreakType.entries.forEach { t ->
                val s = streaks.firstOrNull { it.type == t }
                StreakStrip(when (t) { com.itermeum.app.core.database.entity.StreakType.FIDELITY -> "Верность"; com.itermeum.app.core.database.entity.StreakType.WAKE -> "Подъём"; com.itermeum.app.core.database.entity.StreakType.FROG -> "Лягушка" }, s?.current ?: 0, s?.best ?: 0, accent)
            }
        }
        lowerBar?.let { bar -> LowerBarCard(bar, ui, viewModel) }
        MorningCard(ui, viewModel)
        ScriptCard(viewModel)
        ui.next?.let { next ->
            ImSectionLabel(stringResource(R.string.today_next_norm))
            ImPanel {
                Text(ui.norms[next.normId]?.title ?: next.normId, style = MaterialTheme.typography.titleLarge)
                next.time?.let { Text(it.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        ImSectionLabel(stringResource(R.string.today_norms))
        if (ui.items.isNotEmpty() && ui.items.all { it.normId in ui.done }) {
            Text(stringResource(R.string.today_all_done), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        ui.items.forEach { item -> NormRow(item, ui, viewModel) }
        Spacer(Modifier.height(ImSpacing.grid))
        Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            TextButton(onClick = { viewModel.setSick(!ui.sick) }) {
                Text(if (ui.sick) stringResource(R.string.today_sick_off) else stringResource(R.string.today_sick))
            }
        }
    }
}

/** Section 6.6: failure handling without shame. */
@Composable
private fun LowerBarCard(bar: TodayViewModel.LowerBar, ui: TodayUi, viewModel: TodayViewModel) {
    ImPanel {
        if (bar.calmRestart) {
            Text(stringResource(R.string.lower_calm_restart), style = MaterialTheme.typography.bodyLarge)
            ImSecondaryButton(stringResource(R.string.done), onClick = viewModel::dismissLowerBar)
        } else {
            Text(stringResource(R.string.lower_offer), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.lower_offer_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ui.items.filter { !it.isCore && it.normId in setOf("gym", "reading", "srs", "rosary", "mental_prayer") }.forEach { item ->
                ImSecondaryButton((ui.norms[item.normId]?.title ?: item.normId) + " — пауза 7 дней", onClick = { viewModel.pauseNorm(item.normId) })
            }
            TextButton(onClick = viewModel::dismissLowerBar) { Text(stringResource(R.string.lower_keep)) }
        }
    }
}

/** Section 5.5: «Поработал над сценарием» — before 12:00, not a norm, no streak effect. */
@Composable
private fun ScriptCard(viewModel: TodayViewModel) {
    val active by viewModel.activeScript.collectAsState()
    val count by viewModel.scriptSessionsToday.collectAsState()
    if (!viewModel.beforeWork && active == null) return
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

@Composable
private fun NormRow(item: DayScheduleEngine.ScheduledItem, ui: TodayUi, viewModel: TodayViewModel) {
    val done = item.normId in ui.done
    val norm = ui.norms[item.normId]
    ImPanel(
        Modifier.fillMaxWidth().clickable(enabled = viewModel.isTapCompletable(item.normId)) { viewModel.toggle(item.normId) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (done) "■" else "□",
                color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(end = ImSpacing.card),
            )
            Column(Modifier.weight(1f)) {
                Text(norm?.title ?: item.normId, style = MaterialTheme.typography.bodyLarge)
                val meta = listOfNotNull(item.time?.toString(), if (item.isCore) "ядро" else null, if (item.muted) "тишина" else null)
                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MorningCard(ui: TodayUi, viewModel: TodayViewModel) {
    val m = ui.morning
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    ImPanel {
        when {
            ui.sick -> Text(stringResource(R.string.morning_sick), style = MaterialTheme.typography.bodyLarge)
            m == null || m.stage == MorningStage.SCHEDULED -> {
                val wake = DayScheduleEngine.wakeTimeFor(ui.date.plusDays(1), ui.profile)
                Text(stringResource(R.string.morning_scheduled, wake.toString()), style = MaterialTheme.typography.bodyLarge)
            }
            m.stage == MorningStage.MISSED -> Text(stringResource(R.string.morning_missed), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            m.stage == MorningStage.QUARANTINE -> {
                val started = m.heroicDoneAt ?: m.provenAt ?: now
                val left = (MorningRepository.QUARANTINE_CHECK_MIN * 60_000L - (now - started)).coerceAtLeast(0)
                val mm = left / 60_000; val ss = (left / 1000) % 60
                Text(stringResource(R.string.quarantine_timer, "%02d:%02d".format(mm, ss)), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.quarantine_question_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(ImSpacing.grid))
                Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                    ImSecondaryButton(stringResource(R.string.yes), { viewModel.answerQuarantine(true) }, Modifier.weight(1f))
                    ImSecondaryButton(stringResource(R.string.no), { viewModel.answerQuarantine(false) }, Modifier.weight(1f))
                }
            }
            else -> {
                Text(stringResource(R.string.morning_proven), style = MaterialTheme.typography.bodyLarge)
                if (m.quarantineAnswer != null) {
                    Text(if (m.quarantineAnswer) stringResource(R.string.quarantine_kept) else stringResource(R.string.quarantine_broken),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val weekday = ui.date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                if (weekday && m.gymAt == null && m.stage == MorningStage.DONE) {
                    val started = m.quarantineAnsweredAt ?: now
                    val left = (10 * 60_000L - (now - started)).coerceAtLeast(0)
                    Spacer(Modifier.height(ImSpacing.grid))
                    Text(stringResource(R.string.gym_leave_in, "%02d:%02d".format(left / 60_000, (left / 1000) % 60)), style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                        ImPrimaryButton(stringResource(R.string.gym_done), { viewModel.markGym(false) }, Modifier.weight(1f))
                        ImSecondaryButton(stringResource(R.string.gym_home), { viewModel.markGym(true) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

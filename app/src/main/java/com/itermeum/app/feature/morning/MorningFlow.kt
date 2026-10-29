package com.itermeum.app.feature.morning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.itermeum.app.R
import com.itermeum.app.core.database.entity.MorningStage
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSpacing
import com.itermeum.app.core.engine.StreakEngine
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun MorningFlow(
    state: MorningUi,
    onProofStart: () -> Unit,
    onQrScanned: (String) -> Unit,
    onStepsProven: () -> Unit,
    onReverseCode: (String) -> Boolean,
    onServiam: () -> Unit,
    onTestDismiss: () -> Unit,
    onSwitchToSteps: () -> Unit = {},
    onEmergencyStop: () -> Unit = {},
) {
    // The stop sits under the screen, not over it: the proof screen ends with «Нет QR — 20 шагов».
    // Surface gives the text its colour: without it Text falls back to black on this black screen.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.test -> TestRingScreen(onTestDismiss)
                state.stage == MorningStage.RINGING || state.stage == MorningStage.SCHEDULED -> RingScreen(onProofStart)
                state.stage == MorningStage.PROOF_PENDING -> WakeProofScreen(state, onQrScanned, onStepsProven, onReverseCode, onSwitchToSteps)
                else -> HeroicMinuteScreen(state.verdict, onServiam)
            }
        }
        val ringing = !state.test && state.stage in setOf(MorningStage.RINGING, MorningStage.SCHEDULED, MorningStage.PROOF_PENDING)
        if (state.emergencyStop && ringing) EmergencyStop(onEmergencyStop, Modifier.align(Alignment.CenterHorizontally))
    }
    }
}

/** D-47: the manual stop for the testing period — two taps, and the wake does not count. */
@Composable
private fun EmergencyStop(onStop: () -> Unit, modifier: Modifier) {
    var confirm by remember { mutableStateOf(false) }
    Column(modifier.padding(ImSpacing.screen), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!confirm) {
            TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.alarm_emergency_stop), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            Text(stringResource(R.string.alarm_emergency_confirm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            TextButton(onClick = onStop) { Text(stringResource(R.string.alarm_emergency_yes), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun WallClock() {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(1_000) } }
    Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.displayLarge.copy(fontFamily = com.itermeum.app.core.designsystem.ImMonoFamily))
}

@Composable
private fun RingScreen(onProofStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(ImSpacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WallClock()
        Spacer(Modifier.height(ImSpacing.screen))
        Text(stringResource(R.string.ring_title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(ImSpacing.grid))
        Text(stringResource(R.string.ring_subtitle), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(ImSpacing.screen * 2))
        ImPrimaryButton(stringResource(R.string.ring_go_to_proof), onClick = onProofStart)
        Spacer(Modifier.height(ImSpacing.grid))
        Text(stringResource(R.string.ring_no_snooze), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TestRingScreen(onDismiss: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(ImSpacing.screen), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        WallClock()
        Spacer(Modifier.height(ImSpacing.screen))
        Text(stringResource(R.string.test_alarm_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(ImSpacing.screen))
        ImPrimaryButton(stringResource(R.string.test_alarm_works), onClick = onDismiss)
    }
}

@Composable
private fun WakeProofScreen(
    state: MorningUi,
    onQrScanned: (String) -> Unit,
    onStepsProven: () -> Unit,
    onReverseCode: (String) -> Boolean,
    onSwitchToSteps: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        Text(stringResource(R.string.proof_title), style = MaterialTheme.typography.headlineMedium)
        if (state.tooEarly) Text(stringResource(R.string.proof_too_early), color = MaterialTheme.colorScheme.error)
        when (state.proofMethod) {
            ProofMethod.QR -> {
                Text(stringResource(R.string.proof_qr_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                QrScannerView(modifier = Modifier.fillMaxWidth().weight(1f), onResult = onQrScanned)
                if (state.qrError) Text(stringResource(R.string.proof_qr_wrong), color = MaterialTheme.colorScheme.error)
                // Fallback when the QR is lost or the camera fails: 20 steps + reverse code.
                ImSecondaryButton(stringResource(R.string.proof_use_steps), onClick = onSwitchToSteps)
            }
            ProofMethod.STEPS -> StepsProof(state, onStepsProven, onReverseCode)
        }
    }
}

@Composable
private fun StepsProof(state: MorningUi, onStepsProven: () -> Unit, onReverseCode: (String) -> Boolean) {
    if (!state.stepsDone) {
        StepCounterView(target = 20, windowSeconds = 120, onReached = onStepsProven)
    } else {
        var input by remember { mutableStateOf("") }
        var wrong by remember { mutableStateOf(false) }
        ImPanel {
            Text(stringResource(R.string.proof_reverse_hint), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(ImSpacing.grid))
            Text(state.reverseCode ?: "", style = MaterialTheme.typography.displayLarge.copy(fontFamily = com.itermeum.app.core.designsystem.ImMonoFamily))
            OutlinedTextField(value = input, onValueChange = { input = it.filter(Char::isDigit).take(6); wrong = false },
                label = { Text(stringResource(R.string.proof_reverse_label)) }, isError = wrong, modifier = Modifier.fillMaxWidth())
            ImPrimaryButton(stringResource(R.string.proof_confirm), onClick = { if (!onReverseCode(input)) wrong = true })
        }
    }
}

@Composable
private fun HeroicMinuteScreen(verdict: StreakEngine.WakeVerdict?, onServiam: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        Spacer(Modifier.height(ImSpacing.screen))
        Text(stringResource(R.string.heroic_title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        if (verdict == StreakEngine.WakeVerdict.LATE) {
            Text(stringResource(R.string.heroic_late), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ImPanel {
            Text(stringResource(R.string.heroic_sign_of_cross), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(ImSpacing.grid))
            Text(stringResource(R.string.heroic_offering), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(ImSpacing.screen))
        ImPrimaryButton("SERVIAM", onClick = onServiam, modifier = Modifier.height(ImSpacing.touchPrimary * 1.5f))
        Text(stringResource(R.string.heroic_next), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        TextButton(onClick = onServiam) { Text(stringResource(R.string.heroic_skip_text)) }
    }
}

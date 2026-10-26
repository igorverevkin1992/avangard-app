package com.itermeum.app.feature.prayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.data.TimerController
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImMonoFamily
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSpacing
import kotlinx.coroutines.delay

/** Section 5.6: 10 minutes in three phases — presence 1 min, theme 8 min, resolution 1 min. */
@Composable
fun MentalPrayerScreen(onBack: () -> Unit, viewModel: TimerViewModel = hiltViewModel()) {
    val gospelTitle by viewModel.gospelTitle.collectAsState()
    val session by viewModel.session.collectAsState()
    var now by remember { mutableLongStateOf(viewModel.now()) }
    LaunchedEffect(session) { while (session != null) { delay(1_000); now = viewModel.now() } }
    Column(Modifier.fillMaxSize().padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid), horizontalAlignment = Alignment.CenterHorizontally) {
        ImHeader(stringResource(R.string.hub_mental), onBack = onBack)
        val s = session?.takeIf { it.kind == TimerController.Kind.MENTAL_PRAYER }
        if (s == null) {
            Text(stringResource(R.string.mental_hint), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            ImPrimaryButton(stringResource(R.string.mental_start), onClick = viewModel::startMental)
        } else {
            val elapsed = s.elapsed(now)
            val left = (s.durationMs - elapsed).coerceAtLeast(0)
            Text("%02d:%02d".format(left / 60_000, (left / 1000) % 60), style = MaterialTheme.typography.displayLarge.copy(fontFamily = ImMonoFamily))
            LinearProgressIndicator(progress = { s.fraction(now).toFloat() }, modifier = Modifier.padding(vertical = ImSpacing.grid))
            val phase = when { elapsed < 60_000 -> 0; elapsed < 9 * 60_000 -> 1; else -> 2 }
            ImPanel {
                Text(
                    when (phase) {
                        0 -> stringResource(R.string.mental_phase_presence)
                        1 -> stringResource(R.string.mental_phase_theme, gospelTitle ?: stringResource(R.string.mental_theme_fallback))
                        else -> stringResource(R.string.mental_phase_resolution)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            ImSecondaryButton(if (s.fraction(now) >= 0.8) stringResource(R.string.mental_finish) else stringResource(R.string.mental_stop_early), onClick = { viewModel.stop(); onBack() })
            Text(stringResource(R.string.mental_rule), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

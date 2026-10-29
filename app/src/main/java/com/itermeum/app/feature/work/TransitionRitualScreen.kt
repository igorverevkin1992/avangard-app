package com.itermeum.app.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.designsystem.ImSpacing
import kotlinx.coroutines.delay

/** Section 5.4: «Закрой файл» 3 s → «Вдох» 5 s → jaculatoria 7 s. Counted, not a norm. */
@Composable
fun TransitionRitualScreen(onDone: () -> Unit, viewModel: WorkViewModel = hiltViewModel()) {
    var phase by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(3) }
    var prayer by remember { mutableStateOf("" to "") }
    LaunchedEffect(Unit) {
        prayer = viewModel.ritualPrayer()
        for ((p, secs) in listOf(0 to 3, 1 to 5, 2 to 7)) {
            phase = p; left = secs
            while (left > 0) { delay(1_000); left-- }
        }
        viewModel.logRitual()
        onDone()
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(ImSpacing.screen), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ImSpacing.screen)) {
            Text(
                when (phase) { 0 -> stringResource(R.string.ritual_close_file); 1 -> stringResource(R.string.ritual_breathe); else -> prayer.first },
                style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
            )
            if (phase == 2 && prayer.second.isNotEmpty()) {
                Text(prayer.second, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(left.toString(), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) }
        }
    }
}

package com.avangard.app.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.avangard.app.ui.theme.IsaColors

/**
 * HH:MM:SS read-out. Below the cold-start threshold the colour is subdued; at
 * and after the threshold it switches to [IsaColors.Approve] — the visual
 * signal that the amygdala-block has cleared and momentum is intact. No sound
 * is emitted at the crossover, by design. The threshold is configurable via
 * UserPreferences (default 5 min).
 */
@Composable
fun CoreTimerDisplay(
    elapsedMillis: Long,
    thresholdMs: Long = DEFAULT_COLD_START_THRESHOLD_MS,
    modifier: Modifier = Modifier,
) {
    val crossed = elapsedMillis >= thresholdMs
    val color: Color = if (crossed) IsaColors.Approve else IsaColors.LiveMetal
    Text(
        text = format(elapsedMillis),
        color = color,
        style = MaterialTheme.typography.headlineLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 32.sp,
            letterSpacing = 2.sp,
        ),
        modifier = modifier,
    )
}

const val DEFAULT_COLD_START_THRESHOLD_MS: Long = 5L * 60 * 1000

private fun format(millis: Long): String {
    val safe = millis.coerceAtLeast(0L)
    val totalSeconds = safe / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

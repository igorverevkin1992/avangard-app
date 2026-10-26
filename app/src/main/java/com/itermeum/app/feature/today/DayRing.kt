package com.itermeum.app.feature.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.itermeum.app.core.designsystem.ImMonoFamily
import com.itermeum.app.core.designsystem.drawCrossPattee

/**
 * Section 6.3 «круг дня»: one segment per active norm with 2dp gaps,
 * filled in the liturgical colour when done. Below: two work numbers.
 */
@Composable
fun DayRing(total: Int, done: List<Boolean>, accent: Color, calls: Int, agreements: Int, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    val gold = MaterialTheme.colorScheme.tertiary
    val goldDim = MaterialTheme.colorScheme.outlineVariant
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(196.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                // D-23: outer gold hairline with twelve ticks, like a rose window.
                val outerR = size.minDimension / 2f - 1.dp.toPx()
                drawCircle(goldDim, radius = outerR, style = Stroke(1f))
                for (t in 0 until 12) {
                    val a = Math.toRadians(t * 30.0 - 90)
                    val len = if (t % 3 == 0) 6.dp.toPx() else 3.dp.toPx()
                    val from = Offset(center.x + (outerR - len) * Math.cos(a).toFloat(), center.y + (outerR - len) * Math.sin(a).toFloat())
                    val to = Offset(center.x + outerR * Math.cos(a).toFloat(), center.y + outerR * Math.sin(a).toFloat())
                    drawLine(if (t == 0) gold else goldDim, from, to, strokeWidth = 1.5f)
                }
                drawCrossPattee(Offset(center.x, center.y - outerR + 11.dp.toPx()), 9.dp.toPx(), gold)
            }
            Canvas(Modifier.size(164.dp)) {
                val stroke = 14.dp.toPx()
                val gapPx = 2.dp.toPx()
                val radius = (size.minDimension - stroke) / 2f
                val circumference = (2 * Math.PI * radius).toFloat()
                val gapDeg = if (total > 1) (gapPx / circumference) * 360f else 0f
                val sweep = if (total > 0) 360f / total - gapDeg else 360f
                val topLeft = Offset((size.width - radius * 2) / 2f, (size.height - radius * 2) / 2f)
                val arcSize = Size(radius * 2, radius * 2)
                for (i in 0 until maxOf(total, 1)) {
                    val start = -90f + i * (360f / maxOf(total, 1)) + gapDeg / 2
                    val filled = done.getOrNull(i) == true
                    drawArc(
                        color = if (filled) accent else outline,
                        startAngle = start, sweepAngle = sweep, useCenter = false,
                        topLeft = topLeft, size = arcSize, style = Stroke(width = stroke),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${done.count { it }}/$total", style = MaterialTheme.typography.headlineLarge.copy(fontFamily = ImMonoFamily))
            }
        }
        Text("Звонки: $calls · Договорённости: $agreements", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Section 12: streak = mono number and a 30-mark strip. */
@Composable
fun StreakStrip(label: String, current: Int, best: Int, accent: Color, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(current.toString(), style = MaterialTheme.typography.headlineMedium.copy(fontFamily = ImMonoFamily))
        Canvas(Modifier.size(width = 120.dp, height = 8.dp)) {
            val w = size.width / 30f
            for (i in 0 until 30) {
                drawRect(if (i < current.coerceAtMost(30)) accent else outline, Offset(i * w, 0f), Size(w - 1.5f, size.height))
            }
        }
        Text("лучший $best", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

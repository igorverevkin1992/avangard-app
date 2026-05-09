package com.avangard.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.avangard.app.ui.theme.MachineColors

/**
 * Industrial line chart in the spirit of an oscilloscope screen.
 *
 * - Coordinate grid drawn first (gray, 1 px lines).
 * - Series rendered as a [Path] with straight segments — no Bezier curves
 *   or interpolation, mirroring the spec.
 * - Failure points (status==0) are marked with a red square overlay so the
 *   reader can locate downtime at a glance.
 */
@Composable
fun OscilloscopeChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    seriesColor: Color = MachineColors.ReardenCopper,
    gridColor: Color = MachineColors.WarmGray,
    failureColor: Color = MachineColors.AtlasRed,
) {
    Canvas(modifier = modifier) {
        // Grid: 5 horizontal divisions, ten vertical.
        val rows = 5
        val cols = 10
        val w = size.width
        val h = size.height
        for (i in 0..rows) {
            val y = h * i / rows
            drawLine(
                color = gridColor.copy(alpha = 0.4f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
            )
        }
        for (i in 0..cols) {
            val x = w * i / cols
            drawLine(
                color = gridColor.copy(alpha = 0.4f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f,
            )
        }

        if (points.isEmpty()) return@Canvas

        val maxIndex = (points.size - 1).coerceAtLeast(1)
        fun positionFor(index: Int, value: Float): Offset {
            val x = w * index / maxIndex
            val y = h - h * value.coerceIn(0f, 1f)
            return Offset(x, y)
        }

        val path = Path().apply {
            points.forEachIndexed { index, point ->
                val pos = positionFor(index, point.value)
                if (index == 0) moveTo(pos.x, pos.y) else lineTo(pos.x, pos.y)
            }
        }
        drawPath(path = path, color = seriesColor, style = Stroke(width = 3f))

        // Failure markers
        val markerHalf = 5f
        points.forEachIndexed { index, point ->
            if (!point.success) {
                val pos = positionFor(index, point.value)
                drawRect(
                    color = failureColor,
                    topLeft = Offset(pos.x - markerHalf, pos.y - markerHalf),
                    size = androidx.compose.ui.geometry.Size(markerHalf * 2, markerHalf * 2),
                )
            }
        }
    }
}

data class ChartPoint(
    val value: Float,
    val success: Boolean,
)

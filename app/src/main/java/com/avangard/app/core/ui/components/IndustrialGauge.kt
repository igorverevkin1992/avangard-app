package com.avangard.app.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import com.avangard.app.ui.theme.MachineColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Radial industrial gauge (manometer-style).
 *
 * Sweep is fixed at 270° starting from the south-west position (135°). Major
 * ticks divide the arc into [majorTicks] segments; each major segment is
 * subdivided by [minorTicksPerMajor]. The needle is animated linearly to keep
 * the motion mechanical (no overshoot).
 */
@Composable
fun IndustrialGauge(
    progress: Float,
    modifier: Modifier = Modifier,
    majorTicks: Int = 10,
    minorTicksPerMajor: Int = 5,
    label: String? = null,
    valueColor: Color = MachineColors.ReardenCopper,
    frameColor: Color = MachineColors.WarmGray,
    textColor: Color = MachineColors.Ivory,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "needle",
    )
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = textColor)

    val startAngle = 135f
    val sweep = 270f

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(cx, cy) - 8f
        val innerMajor = radius - 18f
        val innerMinor = radius - 9f

        // Outer arc
        drawArc(
            color = frameColor,
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 2f),
        )

        // Tick marks
        val totalSteps = majorTicks * minorTicksPerMajor
        for (step in 0..totalSteps) {
            val isMajor = step % minorTicksPerMajor == 0
            val angleDeg = startAngle + sweep * step / totalSteps
            val rad = Math.toRadians(angleDeg.toDouble())
            val outerX = cx + radius * cos(rad).toFloat()
            val outerY = cy + radius * sin(rad).toFloat()
            val innerR = if (isMajor) innerMajor else innerMinor
            val innerX = cx + innerR * cos(rad).toFloat()
            val innerY = cy + innerR * sin(rad).toFloat()
            drawLine(
                color = frameColor,
                start = Offset(innerX, innerY),
                end = Offset(outerX, outerY),
                strokeWidth = if (isMajor) 3f else 1.5f,
            )
        }

        // Needle
        val needleAngle = startAngle + sweep * animated
        val needleRad = Math.toRadians(needleAngle.toDouble())
        val needleEndX = cx + (radius - 22f) * cos(needleRad).toFloat()
        val needleEndY = cy + (radius - 22f) * sin(needleRad).toFloat()
        drawLine(
            color = valueColor,
            start = Offset(cx, cy),
            end = Offset(needleEndX, needleEndY),
            strokeWidth = 4f,
        )
        // Hub
        drawCircle(color = valueColor, radius = 6f, center = Offset(cx, cy))

        // Centred numeric readout
        val percent = (animated * 100).toInt().toString() + "%"
        val measured = textMeasurer.measure(text = percent, style = labelStyle)
        drawText(
            textMeasurer = textMeasurer,
            text = percent,
            style = labelStyle.copy(color = valueColor),
            topLeft = Offset(
                x = cx - measured.size.width / 2f,
                y = cy + radius * 0.4f,
            ),
        )

        // Optional caption above the readout
        if (label != null) {
            val captionMeasured = textMeasurer.measure(text = label, style = labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                style = labelStyle.copy(color = textColor),
                topLeft = Offset(
                    x = cx - captionMeasured.size.width / 2f,
                    y = cy - radius * 0.55f,
                ),
            )
        }
    }
}

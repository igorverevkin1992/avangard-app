package com.avangard.app.feature.chronometer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.avangard.app.core.domain.model.WeekClass
import com.avangard.app.ui.theme.IsaColors
import kotlin.math.ceil

private const val WEEKS_PER_ROW = 52

@Composable
fun LifeGrid(
    weeks: List<WeekClass>,
    modifier: Modifier = Modifier,
) {
    if (weeks.isEmpty()) return
    val rows = ceil(weeks.size / WEEKS_PER_ROW.toFloat()).toInt()
    // Aspect ratio: 52 columns × rows rows of square cells with 1dp gaps.
    val aspect = WEEKS_PER_ROW.toFloat() / rows.toFloat()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect, matchHeightConstraintsFirst = false),
    ) {
        val gap = 1.dp.toPx()
        val cellW = (size.width - gap * (WEEKS_PER_ROW - 1)) / WEEKS_PER_ROW
        val cellH = (size.height - gap * (rows - 1)) / rows
        val borderStroke = 1.dp.toPx()
        weeks.forEachIndexed { index, weekClass ->
            val col = index % WEEKS_PER_ROW
            val row = index / WEEKS_PER_ROW
            val x = col * (cellW + gap)
            val y = row * (cellH + gap)
            val topLeft = Offset(x, y)
            val cellSize = Size(cellW, cellH)
            when (weekClass) {
                WeekClass.Extracted -> drawRect(IsaColors.Approve, topLeft, cellSize)
                WeekClass.Partial -> drawRect(IsaColors.LiveMetal, topLeft, cellSize)
                WeekClass.Burned -> drawRect(IsaColors.Mute, topLeft, cellSize)
                WeekClass.Current -> drawRect(IsaColors.Signal, topLeft, cellSize)
                WeekClass.Future -> drawRect(
                    color = IsaColors.Steel,
                    topLeft = topLeft,
                    size = cellSize,
                    style = Stroke(width = borderStroke),
                )
            }
        }
    }
}

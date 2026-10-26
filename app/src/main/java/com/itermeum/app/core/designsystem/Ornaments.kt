package com.itermeum.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * D-23 ornaments: a cross pattée, hairline rules and a Latin motto. All
 * drawn in code so they scale with density and need no bitmap assets.
 */

/** Cross pattée in unit space, drawn at [size] centred on [center]. */
fun DrawScope.drawCrossPattee(center: Offset, size: Float, color: Color) {
    val s = size
    val o = Offset(center.x - s / 2, center.y - s / 2)
    fun p(x: Float, y: Float) = Offset(o.x + x * s, o.y + y * s)
    val path = Path().apply {
        moveTo(p(0.33f, 0f).x, p(0.33f, 0f).y)
        listOf(
            0.67f to 0f, 0.57f to 0.43f, 1f to 0.33f, 1f to 0.67f, 0.57f to 0.57f,
            0.67f to 1f, 0.33f to 1f, 0.43f to 0.57f, 0f to 0.67f, 0f to 0.33f, 0.43f to 0.43f,
        ).forEach { (x, y) -> lineTo(p(x, y).x, p(x, y).y) }
        close()
    }
    drawPath(path, color)
}

@Composable
fun ImCross(size: Dp = 10.dp, color: Color = MaterialTheme.colorScheme.tertiary, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawCrossPattee(Offset(this.size.width / 2, this.size.height / 2), this.size.minDimension, color) }
}

/** Hairline — cross — hairline. Used under headers and between chapters. */
@Composable
fun ImRule(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    Row(modifier.fillMaxWidth().padding(vertical = ImSpacing.tight), verticalAlignment = Alignment.CenterVertically) {
        Hairline(Modifier.weight(1f), color)
        Spacer(Modifier.width(ImSpacing.grid))
        ImCross(8.dp, color)
        Spacer(Modifier.width(ImSpacing.grid))
        Hairline(Modifier.weight(1f), color)
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    Canvas(modifier.height(1.dp)) { drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1f) }
}

/** Latin motto in small caps italic, gold. */
@Composable
fun ImMotto(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontStyle = FontStyle.Italic),
        color = MaterialTheme.colorScheme.tertiary,
        modifier = modifier,
    )
}

package com.itermeum.app.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Section 12 colour tokens. Flat fills, no gradients. */
object ImColors {
    val Background = Color(0xFF0E0E0E)
    val Surface = Color(0xFF161616)
    val SurfaceVariant = Color(0xFF222222)
    val OnSurface = Color(0xFFE8E4DC)
    val OnSurfaceVariant = Color(0xFF9A958C)
    val Outline = Color(0xFF3A3A3A)
    val Error = Color(0xFFB3261E)

    /** Liturgical accents (section 12). Primary = colour of the current season. */
    val Ordinary = Color(0xFF3F6B3F)
    val Penitential = Color(0xFF5B2A6E)
    val Rose = Color(0xFFB5838D)
    val Festive = Color(0xFFD8CFA8)
    val Martyr = Color(0xFF8E1B1B)

    /** Light theme is an option (section 12). */
    val LightBackground = Color(0xFFF2EEE6)
    val LightOnSurface = Color(0xFF141414)
}

object ImSpacing {
    val screen = 16.dp
    val card = 12.dp
    val grid = 8.dp
    val tight = 4.dp
    val touch = 48.dp
    val touchPrimary = 56.dp
}

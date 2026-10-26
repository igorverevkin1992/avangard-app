package com.itermeum.app.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Section 12 colour tokens, warmed towards a chapel-at-night palette (D-23):
 * near-black stone, parchment text, aged gold for rules and ornaments,
 * crimson reserved for the martyr accent and for danger.
 */
object ImColors {
    val Background = Color(0xFF0D0C0B)
    val Surface = Color(0xFF171513)
    val SurfaceVariant = Color(0xFF231F1B)
    val OnSurface = Color(0xFFE8E2D4)
    val OnSurfaceVariant = Color(0xFF9C9486)
    val Outline = Color(0xFF433C34)
    val Error = Color(0xFFB3261E)

    /** Ornament metals (D-23): hairlines, crosses, mottos. */
    val Gold = Color(0xFFB8923A)
    val GoldDim = Color(0xFF6B5527)
    val Stone = Color(0xFF2C2722)

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

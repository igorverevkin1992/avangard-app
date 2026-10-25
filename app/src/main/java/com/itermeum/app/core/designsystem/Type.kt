package com.itermeum.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.itermeum.app.R

/**
 * Section 12 typography, all bundled (OFL, licences in assets/licenses):
 * headings — Cormorant SC (small caps, Cyrillic verified), +8 % tracking;
 * body — PT Serif; timers and counters — JetBrains Mono.
 */
val ImDisplayFamily: FontFamily = FontFamily(
    Font(R.font.cormorant_sc_regular, FontWeight.Normal),
    Font(R.font.cormorant_sc_bold, FontWeight.Bold),
)
val ImBodyFamily: FontFamily = FontFamily(
    Font(R.font.pt_serif_regular, FontWeight.Normal),
    Font(R.font.pt_serif_bold, FontWeight.Bold),
    Font(R.font.pt_serif_italic, FontWeight.Normal, FontStyle.Italic),
)
val ImMonoFamily: FontFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

private val caps = 0.08.em

val ImTypography = Typography(
    displayLarge = TextStyle(fontFamily = ImDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = caps),
    headlineLarge = TextStyle(fontFamily = ImDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = caps),
    headlineMedium = TextStyle(fontFamily = ImDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = caps),
    titleLarge = TextStyle(fontFamily = ImDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = caps),
    titleMedium = TextStyle(fontFamily = ImDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = caps),
    bodyLarge = TextStyle(fontFamily = ImBodyFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = ImBodyFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = ImBodyFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = ImDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = caps),
    labelMedium = TextStyle(fontFamily = ImMonoFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = ImMonoFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp),
)

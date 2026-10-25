package com.itermeum.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Section 12: serif small-caps headings, serif body, mono for timers and
 * counters. Bundled OFL fonts arrive in phase 9; until then the platform
 * serif/mono families keep the hierarchy readable.
 */
val ImDisplayFamily: FontFamily = FontFamily.Serif
val ImBodyFamily: FontFamily = FontFamily.Serif
val ImMonoFamily: FontFamily = FontFamily.Monospace

val ImTypography = Typography(
    displayLarge = TextStyle(fontFamily = ImDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = 3.sp),
    headlineLarge = TextStyle(fontFamily = ImDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 2.sp),
    headlineMedium = TextStyle(fontFamily = ImDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 1.5.sp),
    titleLarge = TextStyle(fontFamily = ImDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 1.5.sp),
    titleMedium = TextStyle(fontFamily = ImDisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 1.sp),
    bodyLarge = TextStyle(fontFamily = ImBodyFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = ImBodyFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = ImBodyFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = ImDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.2.sp),
    labelMedium = TextStyle(fontFamily = ImMonoFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = ImMonoFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp),
)

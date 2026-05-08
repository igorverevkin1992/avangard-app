package com.avangard.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.avangard.app.R

/**
 * Industrial typeface stack.
 *
 * The production target is `Univers Next Cyrillic` (commercial) or
 * `ST-Kosmolet` (free, requires bundling). Until the artifact is licensed
 * we ship a downloadable stand-in (`Roboto Condensed`) which has full
 * Cyrillic coverage and a sufficiently rectilinear silhouette. To swap to
 * the production typeface, replace [DisplayFamily] with a bundled
 * `FontFamily` and remove the GoogleFont provider.
 */
private val GoogleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val RobotoCondensed = GoogleFont("Roboto Condensed")

private val DisplayFamily = FontFamily(
    Font(googleFont = RobotoCondensed, fontProvider = GoogleFontProvider, weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(googleFont = RobotoCondensed, fontProvider = GoogleFontProvider, weight = FontWeight.Bold, style = FontStyle.Normal),
    Font(googleFont = RobotoCondensed, fontProvider = GoogleFontProvider, weight = FontWeight.Black, style = FontStyle.Normal),
)

private val MonoFamily = FontFamily.Monospace

val MachineTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Black,
        fontSize = 48.sp,
        letterSpacing = 4.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 3.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 2.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
    ),
)

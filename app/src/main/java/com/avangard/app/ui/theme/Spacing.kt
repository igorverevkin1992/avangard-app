package com.avangard.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Base spacing scale for the brutalist ISA-101 surfaces. Every layout pad in
 * the app should derive from these tokens — no raw `8.dp` / `12.dp` /
 * `16.dp` literals scattered across composables. Multiples of 4dp keep the
 * grid honest.
 *
 * Touch targets observe Material 3 a11y minimum of 48dp; smaller values
 * (chips, status pills) are reserved for non-primary controls and still
 * pass through the FlowRow-driven wrap.
 */
object IsaSpacing {
    /** Top-level screen padding (outer margin of every top-level Column). */
    val screen = 16.dp
    /** Card / panel inner padding. */
    val card = 12.dp
    /** Chip / button inner padding (horizontal). */
    val chip = 10.dp
    /** Standard gap between siblings on a screen. */
    val grid = 8.dp
    /** Tight gap for label-value pairs and inline annotations. */
    val tight = 4.dp
    /** Minimum interactive touch height (Material a11y minimum). */
    val touch = 48.dp
    /** Hero / primary action height. */
    val touchPrimary = 56.dp
}

package com.avangard.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ISA-101 industrial HMI palette per the Verevkin's Lab manifesto.
 * Cold, monochrome, signal-restrained — colour is information, not decoration.
 */
object IsaColors {
    /** Primary background — matte concrete grey, low visual stimulation. */
    val Graphite = Color(0xFF2E3338)
    /** Deep surface for blocking modal screens (Earned Pride). */
    val Carbon = Color(0xFF1E2226)
    /** Panel borders, dividers. */
    val Steel = Color(0xFF4A5057)
    /** Primary text — high-contrast steel sheen. */
    val LiveMetal = Color(0xFFC0C5CA)
    /** Subdued text, labels, ticks. */
    val Lattice = Color(0xFF5A6066)
    /** Disabled / muted fills. */
    val Mute = Color(0xFF383D43)
    /** Overlay tint for hostage-locked Infra cards. */
    val HostageGray = Color(0xFF353A40)
    /** Reserved for critical states: Waste, blocked, failure. Never decoration. */
    val Signal = Color(0xFFD03A2D)
    /** Sparse confirmation green: Earned Pride flashes, Approved status badges. */
    val Approve = Color(0xFF7BB661)
}

package com.avangard.app.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Wire format for the bundled Objectivism quote catalog
 * (assets/library_quotes.json). Versioned so any future schema change
 * can be detected by the repository.
 */
@Serializable
data class QuoteBundle(
    val schemaVersion: Int,
    val quotes: List<Quote>,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

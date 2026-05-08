package com.avangard.app.core.domain.model

sealed interface ReportError {
    data object TimeSlotMismatch : ReportError
    data object ArtifactEmpty : ReportError
    data object ArtifactTooLong : ReportError
    data object ArtifactInvalidShape : ReportError
    data object FailureCauseTooShort : ReportError
    data object CorrectiveActionTooShort : ReportError
    data object AlreadyInitialized : ReportError
    data object NotInitialized : ReportError
}

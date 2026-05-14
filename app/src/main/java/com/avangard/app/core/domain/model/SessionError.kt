package com.avangard.app.core.domain.model

sealed interface SessionError {
    data object InfraLocked : SessionError
    data object NotAuthorised : SessionError
    data object AnotherFocusActive : SessionError
    data object EveningClosedAlready : SessionError
    data object MissingDefectKind : SessionError
    data object PromptEmpty : SessionError
    data object HistoryLocked : SessionError
    /** Core is already Approved today — re-submit is rejected to protect the saved prompt. */
    data object AlreadyApproved : SessionError
}

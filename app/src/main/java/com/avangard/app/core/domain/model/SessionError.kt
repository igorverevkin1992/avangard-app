package com.avangard.app.core.domain.model

sealed interface SessionError {
    data object InfraLocked : SessionError
    data object NotAuthorised : SessionError
    data object AnotherFocusActive : SessionError
    data object EveningClosedAlready : SessionError
    data object MissingDefectKind : SessionError
    data object PromptEmpty : SessionError
    data object HistoryLocked : SessionError
}

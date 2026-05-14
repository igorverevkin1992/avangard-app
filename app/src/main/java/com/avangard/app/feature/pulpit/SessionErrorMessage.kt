package com.avangard.app.feature.pulpit

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.avangard.app.R
import com.avangard.app.core.domain.model.SessionError

/** Maps a [SessionError] onto the localised text shown in the pulpit error banner. */
@Composable
fun sessionErrorMessage(error: SessionError): String = stringResource(
    when (error) {
        SessionError.InfraLocked -> R.string.error_infra_locked
        SessionError.AnotherFocusActive -> R.string.error_another_focus_active
        SessionError.AlreadyApproved -> R.string.error_already_approved
        SessionError.NotAuthorised -> R.string.error_not_authorised
        SessionError.PromptEmpty -> R.string.error_prompt_empty
        SessionError.MissingDefectKind -> R.string.error_missing_defect_kind
        SessionError.EveningClosedAlready -> R.string.error_evening_closed_already
        SessionError.HistoryLocked -> R.string.error_history_locked
    },
)

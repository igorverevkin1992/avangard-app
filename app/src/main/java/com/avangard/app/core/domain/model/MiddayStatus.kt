package com.avangard.app.core.domain.model

sealed interface MiddayStatus {
    data object NotPolled : MiddayStatus
    data object InProgress : MiddayStatus
    data class Blocked(val unblockingAction: String) : MiddayStatus

    companion object {
        const val CODE_NOT_POLLED = 0
        const val CODE_IN_PROGRESS = 1
        const val CODE_BLOCKED = 2

        fun fromCode(code: Int, action: String?): MiddayStatus = when (code) {
            CODE_IN_PROGRESS -> InProgress
            CODE_BLOCKED -> Blocked(action.orEmpty())
            else -> NotPolled
        }

        fun MiddayStatus.toCode(): Int = when (this) {
            NotPolled -> CODE_NOT_POLLED
            InProgress -> CODE_IN_PROGRESS
            is Blocked -> CODE_BLOCKED
        }

        fun MiddayStatus.actionText(): String? = when (this) {
            is Blocked -> unblockingAction
            else -> null
        }
    }
}

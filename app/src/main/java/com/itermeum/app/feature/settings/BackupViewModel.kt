package com.itermeum.app.feature.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.itermeum.app.BuildConfig
import com.itermeum.app.R
import com.itermeum.app.core.backup.BackupRepository
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.data.auth.AuthRepository
import com.itermeum.app.core.data.cloud.BackupSettings
import com.itermeum.app.core.data.cloud.BackupUploadWorker
import com.itermeum.app.core.data.cloud.DriveBackupClient
import com.itermeum.app.sync.scheduler.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUi(
    val signedIn: Boolean = false,
    val email: String? = null,
    val enabled: Boolean = false,
    val lastUploadAt: Long = 0L,
    val lastError: String? = null,
    val message: String? = null,
    val busy: Boolean = false,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: AuthRepository,
    private val settings: BackupSettings,
    private val backup: BackupRepository,
    private val drive: DriveBackupClient,
    private val reminders: ReminderScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val _ui = MutableStateFlow(BackupUi())
    val ui: StateFlow<BackupUi> = _ui.asStateFlow()

    init { refresh() }

    private fun refresh() = _ui.update {
        it.copy(signedIn = auth.isSignedIn, email = auth.account.value?.email, enabled = settings.enabled, lastUploadAt = settings.lastUploadAt, lastError = settings.lastError)
    }

    fun signInIntent(): Intent = auth.signInIntent()
    /** Sign-in used to fail silently: say what happened, with Google's status code. */
    fun onSignInResult(data: Intent?) {
        val result = auth.handleSignInResult(data)
        val message = result.fold(
            onSuccess = { context.getString(R.string.backup_signed_in, it.email ?: "") },
            onFailure = { e ->
                when (val code = (e as? ApiException)?.statusCode) {
                    GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> context.getString(R.string.backup_sign_in_cancelled)
                    CommonStatusCodes.NETWORK_ERROR -> context.getString(R.string.backup_sign_in_network)
                    CommonStatusCodes.DEVELOPER_ERROR -> context.getString(R.string.backup_sign_in_developer, BuildConfig.APPLICATION_ID, SIGNING_SHA1)
                    GoogleSignInStatusCodes.SIGN_IN_FAILED -> context.getString(R.string.backup_sign_in_failed_consent)
                    else -> context.getString(R.string.backup_sign_in_failed, code?.toString() ?: e.message.orEmpty())
                }
            },
        )
        refresh()
        _ui.update { it.copy(message = message) }
    }
    fun signOut() = viewModelScope.launch { auth.signOut(); refresh() }

    /** Sets the backup password and arms the daily 03:30 upload. */
    fun enable(password: String) {
        if (password.length < 8) { _ui.update { it.copy(message = context.getString(R.string.backup_msg_password_short)) }; return }
        settings.password = password
        BackupUploadWorker.scheduleDaily(context, clock.localTime())
        BackupUploadWorker.uploadNow(context)
        refresh()
    }

    fun disable() { settings.password = null; BackupUploadWorker.cancel(context); refresh() }

    fun uploadNow() { BackupUploadWorker.uploadNow(context); _ui.update { it.copy(message = context.getString(R.string.backup_msg_queued)) } }

    /** Restore from Drive with the given password; replaces local data. */
    fun restore(password: String) = viewModelScope.launch {
        _ui.update { it.copy(busy = true, message = null) }
        val result = runCatching {
            val remote = drive.fetchRemote() ?: return@runCatching context.getString(R.string.backup_msg_none)
            when (val err = backup.import(remote.bytes, password.toCharArray())) {
                null -> { reminders.rescheduleAll(); context.getString(R.string.backup_msg_restored) }
                BackupRepository.ImportError.WrongPassword -> context.getString(R.string.backup_msg_wrong_password)
                BackupRepository.ImportError.WrongFormat -> context.getString(R.string.backup_msg_not_a_copy)
                is BackupRepository.ImportError.Unsupported -> context.getString(R.string.backup_msg_unsupported, err.version)
            }
        }.getOrElse {
            if (it is com.itermeum.app.core.data.cloud.AuthRequiredException) context.getString(R.string.backup_error_sign_in)
            else context.getString(R.string.backup_msg_error, it.message ?: "")
        }
        _ui.update { it.copy(busy = false, message = result) }
    }

    fun wipeAll() = viewModelScope.launch { backup.wipeAll(); _ui.update { it.copy(message = context.getString(R.string.backup_msg_wiped)) } }
}

/** The certificate CI signs with (D-21): the Android OAuth client must carry exactly this. */
private const val SIGNING_SHA1 = "EE:B5:F0:60:1A:53:98:B1:B5:82:AC:6F:1D:C9:D8:51:30:1D:6D:18"

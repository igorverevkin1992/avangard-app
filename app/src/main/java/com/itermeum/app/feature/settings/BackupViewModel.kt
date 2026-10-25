package com.itermeum.app.feature.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    fun onSignInResult(data: Intent?) { auth.handleSignInResult(data); refresh() }
    fun signOut() = viewModelScope.launch { auth.signOut(); refresh() }

    /** Sets the backup password and arms the daily 03:30 upload. */
    fun enable(password: String) {
        if (password.length < 8) { _ui.update { it.copy(message = "Пароль не короче 8 символов") }; return }
        settings.password = password
        BackupUploadWorker.scheduleDaily(context, clock.localTime())
        BackupUploadWorker.uploadNow(context)
        refresh()
    }

    fun disable() { settings.password = null; BackupUploadWorker.cancel(context); refresh() }

    fun uploadNow() { BackupUploadWorker.uploadNow(context); _ui.update { it.copy(message = "Загрузка поставлена в очередь") } }

    /** Restore from Drive with the given password; replaces local data. */
    fun restore(password: String) = viewModelScope.launch {
        _ui.update { it.copy(busy = true, message = null) }
        val result = runCatching {
            val remote = drive.fetchRemote() ?: return@runCatching "В Drive нет копии Iter Meum"
            when (val err = backup.import(remote.bytes, password.toCharArray())) {
                null -> { reminders.rescheduleAll(); "Восстановлено" }
                BackupRepository.ImportError.WrongPassword -> "Неверный пароль"
                BackupRepository.ImportError.WrongFormat -> "Файл не является копией Iter Meum"
                is BackupRepository.ImportError.Unsupported -> "Версия копии ${err.version} не поддерживается"
            }
        }.getOrElse { "Ошибка: ${it.message}" }
        _ui.update { it.copy(busy = false, message = result) }
    }

    fun wipeAll() = viewModelScope.launch { backup.wipeAll(); _ui.update { it.copy(message = "Данные стёрты") } }
}

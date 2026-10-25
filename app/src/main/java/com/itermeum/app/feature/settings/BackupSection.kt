package com.itermeum.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import java.time.Instant
import java.time.ZoneId

/** Section 5.17 backup block inside Settings. */
@Composable
fun BackupSection(viewModel: BackupViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    var password by remember { mutableStateOf("") }
    var confirmWipe by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.onSignInResult(it.data) }
    Column(verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImSectionLabel(stringResource(R.string.backup_title))
        Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!ui.signedIn) {
            ImSecondaryButton(stringResource(R.string.backup_sign_in), onClick = { launcher.launch(viewModel.signInIntent()) })
        } else {
            Text(stringResource(R.string.backup_account, ui.email ?: ""), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.backup_password)) },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            if (!ui.enabled) {
                ImSecondaryButton(stringResource(R.string.backup_enable), enabled = password.length >= 8, onClick = { viewModel.enable(password) })
            } else {
                if (ui.lastUploadAt > 0) Text(stringResource(R.string.backup_last, Instant.ofEpochMilli(ui.lastUploadAt).atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0).toString()), style = MaterialTheme.typography.labelMedium)
                ui.lastError?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
                ImSecondaryButton(stringResource(R.string.backup_now), onClick = viewModel::uploadNow)
                ImSecondaryButton(stringResource(R.string.backup_disable), onClick = viewModel::disable)
            }
            ImSecondaryButton(stringResource(R.string.backup_restore), enabled = password.isNotBlank() && !ui.busy, onClick = { viewModel.restore(password) })
            ImSecondaryButton(stringResource(R.string.backup_sign_out), onClick = viewModel::signOut)
        }
        ui.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
        if (!confirmWipe) ImSecondaryButton(stringResource(R.string.wipe_all), onClick = { confirmWipe = true })
        else {
            Text(stringResource(R.string.wipe_confirm), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            ImSecondaryButton(stringResource(R.string.wipe_yes), onClick = { viewModel.wipeAll(); confirmWipe = false })
            ImSecondaryButton(stringResource(R.string.cancel), onClick = { confirmWipe = false })
        }
    }
}

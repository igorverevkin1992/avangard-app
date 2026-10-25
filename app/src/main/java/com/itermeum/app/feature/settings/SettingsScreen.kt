package com.itermeum.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.itermeum.app.R
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSpacing

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAbout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        TextButton(onClick = onBack) { Text("←") }
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge)
        ImPanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAbout)) {
            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

package com.itermeum.app.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.itermeum.app.R
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSpacing

@Composable
fun TodayScreen(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.nav_today), style = MaterialTheme.typography.headlineLarge)
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings_title)) }
        }
        ImPanel {
            Text(stringResource(R.string.placeholder_today), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

package com.itermeum.app.feature.prayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.itermeum.app.R
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSpacing

@Composable
fun PrayerScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        Text(stringResource(R.string.nav_prayer), style = MaterialTheme.typography.headlineLarge)
        ImPanel {
            Text(stringResource(R.string.placeholder_prayer), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

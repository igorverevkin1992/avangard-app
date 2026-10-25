package com.itermeum.app.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.itermeum.app.R
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSpacing

/** Section 13 checklist. Tap a row to open the matching system screen; re-checked on resume. */
@Composable
fun HyperOsChecklist(onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var items by remember { mutableStateOf(HyperOsChecks.items(context)) }
    LaunchedEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { items = HyperOsChecks.items(context); onChanged() }
        }
        lifecycle.lifecycle.addObserver(observer)
    }
    Column(verticalArrangement = Arrangement.spacedBy(ImSpacing.tight)) {
        items.forEach { item ->
            ImPanel(Modifier.fillMaxWidth().clickable { HyperOsChecks.open(context, item.index) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val mark = when (item.ok) { true -> "■"; false -> "□"; null -> "?" }
                    val color = when (item.ok) { true -> MaterialTheme.colorScheme.primary; false -> MaterialTheme.colorScheme.error; null -> MaterialTheme.colorScheme.onSurfaceVariant }
                    Text(mark, color = color, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(end = ImSpacing.card))
                    Column {
                        Text("${item.index}. ${stringResource(item.titleRes)}", style = MaterialTheme.typography.bodyLarge)
                        if (item.ok == null) Text(stringResource(R.string.hyperos_manual), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text(stringResource(R.string.hyperos_backup_alarm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

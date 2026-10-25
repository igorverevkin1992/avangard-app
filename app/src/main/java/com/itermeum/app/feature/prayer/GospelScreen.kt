package com.itermeum.app.feature.prayer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.data.TimerController
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImMonoFamily
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import kotlinx.coroutines.delay

/** Section 5.9: gospel of the day + focus phrase; reading programme with a 10-minute timer. */
@Composable
fun GospelScreen(onBack: () -> Unit, viewModel: GospelViewModel = hiltViewModel(), timers: TimerViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    val session by timers.session.collectAsState()
    val context = LocalContext.current
    var phrase by remember(ui.focus) { mutableStateOf(ui.focus ?: "") }
    var now by remember { mutableLongStateOf(timers.now()) }
    LaunchedEffect(session) { while (session != null) { delay(1_000); now = timers.now() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.hub_gospel), onBack = onBack)
        Text(ui.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        ui.passage?.let { p ->
            ImSectionLabel(p.title)
            ImPanel {
                Text(
                    buildAnnotatedString {
                        p.verses.forEach { v ->
                            withStyle(MaterialTheme.typography.labelSmall.toSpanStyle().copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("${v.verse} ") }
                            append(v.text); append(" ")
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(stringResource(R.string.gospel_legal), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } ?: Text(stringResource(R.string.gospel_missing), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(value = phrase, onValueChange = { phrase = it }, label = { Text(stringResource(R.string.gospel_focus)) }, modifier = Modifier.fillMaxWidth())
        ImPrimaryButton(if (ui.read) stringResource(R.string.gospel_read_again) else stringResource(R.string.gospel_read), onClick = { viewModel.markRead(phrase) })

        ImSectionLabel(stringResource(R.string.reading_title))
        Text(stringResource(R.string.reading_before), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ui.plan.forEach { item ->
            ImPanel {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(item.sectionLabel, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                    item.url?.let { url ->
                        ImSecondaryButton(stringResource(R.string.reading_open_site), {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            if (session == null) timers.startReading(item.bookId)
                        }, Modifier.weight(1f))
                    }
                    ImSecondaryButton(stringResource(R.string.reading_paper), {
                        if (session == null) timers.startReading(item.bookId)
                    }, Modifier.weight(1f))
                }
            }
        }
        session?.takeIf { it.kind == TimerController.Kind.READING }?.let { s ->
            val el = s.elapsed(now)
            ImPanel {
                Text("%02d:%02d".format(el / 60_000, (el / 1000) % 60), style = MaterialTheme.typography.displayLarge.copy(fontFamily = ImMonoFamily))
                Text(stringResource(R.string.reading_rule), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ImSecondaryButton(stringResource(R.string.reading_stop), onClick = { timers.stop(); viewModel.refresh() })
            }
        }
        if (ui.readingDone) Text(stringResource(R.string.reading_done), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.reading_no_debt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

package com.itermeum.app.feature.prayer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.data.TimerController
import com.itermeum.app.core.data.books.BookInfo
import com.itermeum.app.core.designsystem.ImCollapsible
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
fun GospelScreen(onBack: () -> Unit, onOpenBook: (String) -> Unit = {}, viewModel: GospelViewModel = hiltViewModel(), timers: TimerViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    val books by viewModel.books.collectAsState()
    val importMessage by viewModel.importMessage.collectAsState()
    // The picked file goes to the book whose «Загрузить файл» was tapped.
    var pendingBook by rememberSaveable { mutableStateOf<String?>(null) }
    val okText = stringResource(R.string.book_loaded)
    val okPagesText = stringResource(R.string.book_loaded_pages)
    val failText = stringResource(R.string.book_load_failed)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = pendingBook
        if (uri != null && id != null) viewModel.importBook(id, uri, okText, okPagesText, failText)
        pendingBook = null
    }
    val pick: (String) -> Unit = { id -> pendingBook = id; picker.launch(arrayOf("*/*")) }
    val session by timers.session.collectAsState()
    val context = LocalContext.current
    var phrase by remember(ui.focus) { mutableStateOf(ui.focus ?: "") }
    var now by remember { mutableLongStateOf(timers.now()) }
    LaunchedEffect(session) {
        if (session == null) viewModel.refresh() // a reading just stopped (here or in the reader) may have counted
        while (session != null) { delay(1_000); now = timers.now() }
    }
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
                BookFileActions(books[item.bookId], onRead = { onOpenBook(item.bookId) }, onLoad = { pick(item.bookId) })
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
        importMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
        if (ui.readingDone) Text(stringResource(R.string.reading_done), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.reading_no_debt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // D-37: every book of the programme, to load the files once, ahead of their month.
        ImCollapsible(stringResource(R.string.books_all), summary = stringResource(R.string.books_loaded_count, viewModel.programme.count { it.bookId in books }, viewModel.programme.size)) {
            Text(stringResource(R.string.books_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            viewModel.programme.forEach { item ->
                ImPanel {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    BookFileActions(books[item.bookId], onRead = { onOpenBook(item.bookId) }, onLoad = { pick(item.bookId) }, onRemove = { viewModel.removeBook(item.bookId) })
                }
            }
        }
    }
}

/** «Читать» for a loaded file (and which file it is), «Загрузить файл» otherwise. */
@Composable
private fun BookFileActions(info: BookInfo?, onRead: () -> Unit, onLoad: () -> Unit, onRemove: (() -> Unit)? = null) {
    if (info == null) {
        ImSecondaryButton(stringResource(R.string.book_load), onLoad)
        return
    }
    Text(stringResource(R.string.book_file, info.title, if (info.asPages) stringResource(R.string.book_pages) else info.format.name), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ImPrimaryButton(stringResource(R.string.book_read), onClick = onRead)
    Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImSecondaryButton(stringResource(R.string.book_replace), onLoad, Modifier.weight(1f))
        onRemove?.let { ImSecondaryButton(stringResource(R.string.book_remove), it, Modifier.weight(1f)) }
    }
}

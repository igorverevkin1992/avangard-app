package com.itermeum.app.feature.prayer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.R
import com.itermeum.app.core.data.TimerController
import com.itermeum.app.core.data.books.BookFormat
import com.itermeum.app.core.data.books.BookLibrary
import com.itermeum.app.core.data.books.ParsedBook
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImMonoFamily
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class BookReaderViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val library: BookLibrary,
) : ViewModel() {
    val bookId: String = savedState["bookId"] ?: ""
    val title: String = library.books.value[bookId]?.title ?: ""
    val isPdf: Boolean = library.books.value[bookId]?.format == BookFormat.PDF

    private val saved = library.position(bookId)
    /** Paragraph (or page) to reopen at, for the chapter reading stopped in. */
    val savedChapter: Int = saved.first
    val savedItem: Int = saved.second

    private val _book = MutableStateFlow<ParsedBook?>(null)
    val book: StateFlow<ParsedBook?> = _book.asStateFlow()
    private val _chapter = MutableStateFlow(saved.first)
    val chapter: StateFlow<Int> = _chapter.asStateFlow()

    var fontScale by mutableFloatStateOf(library.fontScale)
        private set

    init {
        if (!isPdf) viewModelScope.launch {
            val b = library.load(bookId)
            _book.value = b
            if (b != null) _chapter.value = _chapter.value.coerceIn(0, b.chapters.lastIndex)
        }
    }

    fun pdf(): File? = library.pdf(bookId)

    fun openChapter(index: Int) { _chapter.value = index; library.savePosition(bookId, index, 0) }
    fun savePosition(item: Int) = library.savePosition(bookId, _chapter.value, item)
    fun font(delta: Float) { fontScale = (fontScale + delta).coerceIn(0.8f, 1.8f); library.fontScale = fontScale }
}

/** Reading a loaded book (D-37): the reading timer runs while the book is open. */
@Composable
fun BookReaderScreen(onBack: () -> Unit, viewModel: BookReaderViewModel = hiltViewModel(), timers: TimerViewModel = hiltViewModel()) {
    val session by timers.session.collectAsState()
    var now by remember { mutableLongStateOf(timers.now()) }
    LaunchedEffect(Unit) { if (timers.session.value == null) timers.startReading(viewModel.bookId) }
    LaunchedEffect(session) { while (session != null) { delay(1_000); now = timers.now() } }

    Column(Modifier.fillMaxSize().padding(horizontal = ImSpacing.screen)) {
        ImHeader(viewModel.title, onBack = onBack)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val reading = session?.takeIf { it.kind == TimerController.Kind.READING }
            if (reading != null) {
                val el = reading.elapsed(now)
                Text("%02d:%02d".format(el / 60_000, (el / 1000) % 60), style = MaterialTheme.typography.titleMedium.copy(fontFamily = ImMonoFamily), modifier = Modifier.weight(1f))
                TextButton(onClick = { timers.stop() }) { Text(stringResource(R.string.reading_stop)) }
            } else {
                Text(stringResource(R.string.book_timer_off), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { timers.startReading(viewModel.bookId) }) { Text(stringResource(R.string.book_timer_start)) }
            }
            if (!viewModel.isPdf) {
                TextButton(onClick = { viewModel.font(-0.1f) }) { Text("A−") }
                TextButton(onClick = { viewModel.font(0.1f) }) { Text("A+") }
            }
        }
        if (viewModel.isPdf) PdfPages(viewModel) else TextBook(viewModel)
    }
}

@Composable
private fun TextBook(viewModel: BookReaderViewModel) {
    val book by viewModel.book.collectAsState()
    val chapter by viewModel.chapter.collectAsState()
    val b = book ?: return
    var toc by remember { mutableStateOf(false) }
    TextButton(onClick = { toc = !toc }) {
        Text(stringResource(R.string.book_toc) + " · " + b.chapters.getOrNull(chapter)?.title.orEmpty(), maxLines = 1)
    }
    if (toc) {
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(b.chapters) { i, c ->
                Text(
                    c.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (i == chapter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.openChapter(i); toc = false }.padding(vertical = ImSpacing.grid),
                )
            }
        }
        return
    }
    val c = b.chapters.getOrNull(chapter) ?: return
    val body = MaterialTheme.typography.bodyLarge
    val scale = viewModel.fontScale
    val style = body.copy(
        fontSize = if (body.fontSize.isSpecified) body.fontSize * scale else body.fontSize,
        lineHeight = if (body.lineHeight.isSpecified) body.lineHeight * scale else body.lineHeight,
    )
    key(chapter) {
        val state = rememberLazyListState(if (chapter == viewModel.savedChapter) viewModel.savedItem else 0)
        LaunchedEffect(state) { snapshotFlow { state.firstVisibleItemIndex }.collect { viewModel.savePosition(it) } }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ImSpacing.card)) {
            item { Text(c.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
            itemsIndexed(c.paragraphs) { _, p -> Text(p, style = style) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid), modifier = Modifier.padding(vertical = ImSpacing.screen)) {
                    if (chapter > 0) ImSecondaryButton(stringResource(R.string.book_prev), { viewModel.openChapter(chapter - 1) }, Modifier.weight(1f))
                    if (chapter < b.chapters.lastIndex) ImSecondaryButton(stringResource(R.string.book_next), { viewModel.openChapter(chapter + 1) }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** PDF pages rendered at screen width, one at a time (PdfRenderer allows a single open page). */
@Composable
private fun PdfPages(viewModel: BookReaderViewModel) {
    val file = viewModel.pdf() ?: return
    val renderer = remember(file) { runCatching { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull() }
    DisposableEffect(renderer) { onDispose { renderer?.let { r -> synchronized(r) { r.close() } } } }
    if (renderer == null) {
        ImPanel { Text(stringResource(R.string.book_load_failed)) }
        return
    }
    val state = rememberLazyListState(viewModel.savedItem.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0)))
    LaunchedEffect(state) { snapshotFlow { state.firstVisibleItemIndex }.collect { viewModel.savePosition(it) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            items(renderer.pageCount) { page ->
                val bitmap by produceState<Bitmap?>(null, page, widthPx) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            synchronized(renderer) {
                                renderer.openPage(page).use { p ->
                                    val h = (widthPx.toLong() * p.height / p.width).toInt()
                                    Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also {
                                        it.eraseColor(Color.WHITE)
                                        p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                }
                            }
                        }.getOrNull()
                    }
                }
                bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth()) }
                    ?: androidx.compose.foundation.layout.Spacer(Modifier.fillMaxWidth().height(480.dp))
            }
        }
    }
}

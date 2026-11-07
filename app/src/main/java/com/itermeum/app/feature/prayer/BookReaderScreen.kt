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
import com.itermeum.app.core.data.books.BookLibrary
import com.itermeum.app.core.data.books.BookToc
import com.itermeum.app.core.data.books.ParsedBook
import com.itermeum.app.feature.work.FlowRowCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.saveable.rememberSaveable
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
    /** A scanned PDF (no text layer) is shown as page images. */
    val asPages: Boolean = library.books.value[bookId]?.asPages == true

    private val saved = library.position(bookId)
    /**
     * Where reading stands now — updated on every scroll, so reopening the list
     * (after the contents, a chapter change, or a new visit) lands there (D-59).
     */
    var savedChapter: Int = saved.first
        private set
    var savedItem: Int = saved.second
        private set
    var savedOffset: Int = library.offset(bookId)
        private set

    private val _book = MutableStateFlow<ParsedBook?>(null)
    val book: StateFlow<ParsedBook?> = _book.asStateFlow()
    private val _chapter = MutableStateFlow(saved.first)
    val chapter: StateFlow<Int> = _chapter.asStateFlow()

    var fontScale by mutableFloatStateOf(library.fontScale)
        private set

    init {
        if (!asPages) viewModelScope.launch {
            val b = library.load(bookId)
            _book.value = b
            if (b != null) _chapter.value = _chapter.value.coerceIn(0, b.chapters.lastIndex)
        }
    }

    fun pdf(): File? = library.pdf(bookId)

    fun openChapter(index: Int) {
        _chapter.value = index
        savedChapter = index; savedItem = 0; savedOffset = 0
        library.savePosition(bookId, index, 0, 0)
    }
    fun savePosition(item: Int, offset: Int = 0) {
        savedChapter = _chapter.value; savedItem = item; savedOffset = offset
        library.savePosition(bookId, _chapter.value, item, offset)
    }
    fun font(delta: Float) { fontScale = (fontScale + delta).coerceIn(0.8f, 1.8f); library.fontScale = fontScale }
}

/** Reading a loaded book (D-37): the reading timer runs while the book is open. */
@Composable
fun BookReaderScreen(onBack: () -> Unit, viewModel: BookReaderViewModel = hiltViewModel(), timers: TimerViewModel = hiltViewModel()) {
    val session by timers.session.collectAsState()
    var now by remember { mutableLongStateOf(timers.now()) }
    LaunchedEffect(Unit) { if (timers.session.value.let { it == null || it.kind == TimerController.Kind.FOCUS }) timers.startReading(viewModel.bookId) }
    // D-58: leaving the book stops its reading timer and records the minutes.
    DisposableEffect(Unit) {
        onDispose {
            val s = timers.session.value
            if (s != null && s.kind == TimerController.Kind.READING && s.bookId == viewModel.bookId) timers.stop()
        }
    }
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
            if (!viewModel.asPages) {
                TextButton(onClick = { viewModel.font(-0.1f) }) { Text("A−") }
                TextButton(onClick = { viewModel.font(0.1f) }) { Text("A+") }
            }
        }
        if (viewModel.asPages) PdfPages(viewModel) else TextBook(viewModel)
    }
}

@Composable
private fun TextBook(viewModel: BookReaderViewModel) {
    val book by viewModel.book.collectAsState()
    val chapter by viewModel.chapter.collectAsState()
    val b = book ?: return
    var toc by remember { mutableStateOf(false) }
    // D-68: «Оглавление · Бытие 3» — the book and the chapter, not just «Глава 3».
    TextButton(onClick = { toc = !toc }) {
        Text(stringResource(R.string.book_toc) + " · " + b.chapters.getOrNull(chapter)?.let(BookToc::where).orEmpty(), maxLines = 1)
    }
    if (toc) {
        Contents(b, chapter) { i -> viewModel.openChapter(i); toc = false }
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
        val here = chapter == viewModel.savedChapter
        val state = rememberLazyListState(if (here) viewModel.savedItem else 0, if (here) viewModel.savedOffset else 0)
        LaunchedEffect(state) {
            snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
                .collect { (item, offset) -> viewModel.savePosition(item, offset) }
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ImSpacing.card)) {
            item {
                Column {
                    c.part?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(c.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
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

/**
 * D-68: the contents. A book with parts (the Bible: testament → book → chapter) opens on
 * its books — the current one marked — and a book opens on a grid of its chapter numbers;
 * a flat book keeps the plain list of chapters.
 */
@Composable
private fun Contents(b: ParsedBook, chapter: Int, onOpen: (Int) -> Unit) {
    if (!BookToc.grouped(b.chapters)) {
        LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState((chapter - 3).coerceAtLeast(0))) {
            itemsIndexed(b.chapters) { i, c ->
                Text(
                    c.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (i == chapter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(i) }.padding(vertical = ImSpacing.grid),
                )
            }
        }
        return
    }
    val groups = remember(b) { BookToc.groups(b.chapters) }
    val current = groups.indexOfFirst { chapter in it.chapters }.coerceAtLeast(0)
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    val g = open?.let { groups.getOrNull(it) }
    if (g == null) {
        LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState((current - 3).coerceAtLeast(0))) {
            itemsIndexed(groups) { gi, group ->
                if (group.section != null && group.section != groups.getOrNull(gi - 1)?.section) {
                    Text(group.section, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = ImSpacing.card))
                }
                Row(Modifier.fillMaxWidth().clickable { open = gi }.padding(vertical = ImSpacing.grid), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.book.ifEmpty { b.title }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                        color = if (gi == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(group.chapters.size.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            TextButton(onClick = { open = null }) { Text(stringResource(R.string.book_toc_books)) }
            g.section?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(g.book.ifEmpty { b.title }, style = MaterialTheme.typography.titleLarge)
            FlowRowCompat {
                g.chapters.forEach { i ->
                    FilterChip(selected = i == chapter, onClick = { onOpen(i) }, label = { Text(BookToc.shortTitle(b.chapters[i].title)) })
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
    val state = rememberLazyListState(viewModel.savedItem.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0)), viewModel.savedOffset)
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (item, offset) -> viewModel.savePosition(item, offset) }
    }
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

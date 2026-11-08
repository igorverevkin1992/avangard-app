package com.itermeum.app.core.data.books

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/** [asPages] — a scanned PDF without text, shown as page images. */
/** Why a book file could not be loaded — shown to the user (D-57). */
class BookImportException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { TOO_LARGE, UNREADABLE, FORMAT, MEMORY }
}

data class BookInfo(val bookId: String, val title: String, val format: BookFormat, val asPages: Boolean = false, val fileName: String? = null)

/**
 * Book files the user loads from the phone for spiritual reading (D-37).
 * They stay in the app's private storage: no book text is in the repository
 * or the content pack (the rights belong to their publishers), and they are
 * not part of the Drive backup — after a restore the files are loaded again.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Singleton
class BookLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val dir = File(context.filesDir, "books").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("books", Context.MODE_PRIVATE)

    private val _books = MutableStateFlow(scan())
    /** D-63: what the running import is doing — megabytes read, then parsing — for the screen. */
    data class Progress(val stage: Stage, val megabytes: Int = 0) { enum class Stage { READING, PARSING, SAVING } }
    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    /** Loaded books by the reading programme's `bookId`. */
    val books: StateFlow<Map<String, BookInfo>> = _books.asStateFlow()

    private fun scan(): Map<String, BookInfo> = dir.listFiles().orEmpty().mapNotNull { f ->
        val id = f.nameWithoutExtension
        val format = when (f.extension) { "json" -> prefs.getString("$id.format", null)?.let(BookFormat::valueOf) ?: BookFormat.EPUB; "pdf" -> BookFormat.PDF; else -> null }
        format?.let { BookInfo(id, prefs.getString("$id.title", null) ?: id, it, asPages = f.extension == "pdf", fileName = prefs.getString("$id.file", null)) }
    }.associateBy { it.bookId }

    /**
     * Reads the picked file, parses it (a scanned PDF is kept as is) and replaces any earlier
     * file of this book. D-57: the file is streamed to disk first, never held whole in memory,
     * so a large PDF (a whole Bible) loads; PdfBox reads it from that file with temp-file buffers.
     */
    suspend fun import(bookId: String, uri: Uri): Result<BookInfo> = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "import-$bookId.tmp")
        runCatching {
            val name = displayName(uri) ?: bookId
            val size = context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); total += n
                        val mb = (total / (1024 * 1024)).toInt()
                        if (mb != _progress.value?.megabytes) _progress.value = Progress(Progress.Stage.READING, mb)
                        if (total > MAX_PDF_BYTES) throw BookImportException(BookImportException.Reason.TOO_LARGE)
                    }
                    total
                }
            } ?: throw BookImportException(BookImportException.Reason.UNREADABLE)
            _progress.value = Progress(Progress.Stage.PARSING, (size / (1024 * 1024)).toInt())
            val head = ByteArray(minOf(size, 4096L).toInt()).also { h -> tmp.inputStream().use { it.read(h) } }
            val pdf = head.size >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "%PDF"
            val fileTitle = name.substringBeforeLast('.')
            val format: BookFormat
            val book: ParsedBook?
            if (pdf) {
                format = BookFormat.PDF
                // A PDF with a text layer becomes chapters like any other book (D-40); a scan stays as pages.
                book = runCatching { PdfText.extract(context, tmp) }.getOrNull()
                    ?.let { BookParser.pdf(it.pages, it.outline, it.title ?: fileTitle) }
            } else {
                if (size > MAX_BYTES) throw BookImportException(BookImportException.Reason.TOO_LARGE)
                val bytes = tmp.readBytes()
                format = runCatching { BookParser.detect(bytes) }.getOrElse { throw BookImportException(BookImportException.Reason.FORMAT) }
                book = runCatching { BookParser.parse(bytes, fileTitle) }.getOrElse { throw BookImportException(BookImportException.Reason.FORMAT) }
            }
            _progress.value = Progress(Progress.Stage.SAVING)
            // D-72: the new copy is written in full first — streamed, never held as one big string —
            // and only then replaces the old one: a failed save no longer loses the book already there.
            val title: String
            val target = File(dir, if (book != null) "$bookId.json" else "$bookId.pdf")
            val part = File(dir, target.name + PART)
            if (book != null) {
                part.outputStream().buffered().use { json.encodeToStream(ParsedBook.serializer(), book, it) }
                title = book.title
            } else {
                tmp.copyTo(part, overwrite = true)
                title = fileTitle
            }
            // D-68: the same book loaded again keeps the place where reading stopped, when its chapters
            // are as many — counted from the old file without reading it into memory.
            val oldChapters = if (book != null) chapterCount(bookId) else null
            val place = Triple(prefs.getInt("$bookId.chapter", 0), prefs.getInt("$bookId.item", 0), prefs.getInt("$bookId.offset", 0))
            remove(bookId)
            if (!part.renameTo(target)) { part.copyTo(target, overwrite = true); part.delete() }
            val edit = prefs.edit().putString("$bookId.title", title).putString("$bookId.format", format.name).putString("$bookId.file", name)
            if (book != null) {
                edit.putInt("$bookId.chapters", book.chapters.size)
                if (oldChapters == book.chapters.size) edit.putInt("$bookId.chapter", place.first).putInt("$bookId.item", place.second).putInt("$bookId.offset", place.third)
            }
            edit.apply()
            _books.value = scan()
            _books.value.getValue(bookId)
        }.recoverCatching { e ->
            throw if (e is OutOfMemoryError) BookImportException(BookImportException.Reason.MEMORY) else e
        }.also {
            tmp.delete(); File(dir, "$bookId.json$PART").delete(); File(dir, "$bookId.pdf$PART").delete()
            _progress.value = null
        }
    }

    /** Chapters of the loaded book: remembered at saving, or counted in its file as a stream. */
    private fun chapterCount(bookId: String): Int? {
        prefs.getInt("$bookId.chapters", -1).takeIf { it >= 0 }?.let { return it }
        val f = File(dir, "$bookId.json").takeIf { it.exists() } ?: return null
        return runCatching { f.inputStream().buffered().use(BookParser::countChapters) }.getOrNull()
    }

    suspend fun load(bookId: String): ParsedBook? = withContext(Dispatchers.IO) {
        File(dir, "$bookId.json").takeIf { it.exists() }?.let { f ->
            runCatching { f.inputStream().buffered().use { json.decodeFromStream(ParsedBook.serializer(), it) } }.getOrNull()
        }
    }

    fun pdf(bookId: String): File? = File(dir, "$bookId.pdf").takeIf { it.exists() }

    fun remove(bookId: String) {
        File(dir, "$bookId.json").delete(); File(dir, "$bookId.pdf").delete()
        prefs.edit().remove("$bookId.title").remove("$bookId.format").remove("$bookId.file").remove("$bookId.chapter").remove("$bookId.item").remove("$bookId.offset").remove("$bookId.chapters").apply()
        _books.value = scan()
    }

    /** Where reading stopped: chapter (0 for a PDF) and paragraph or page. */
    fun position(bookId: String): Pair<Int, Int> = prefs.getInt("$bookId.chapter", 0) to prefs.getInt("$bookId.item", 0)
    /** Pixels scrolled into the first visible paragraph or page (D-59). */
    fun offset(bookId: String): Int = prefs.getInt("$bookId.offset", 0)
    fun savePosition(bookId: String, chapter: Int, item: Int, offset: Int = 0) {
        prefs.edit().putInt("$bookId.chapter", chapter).putInt("$bookId.item", item).putInt("$bookId.offset", offset)
            .putString(KEY_LAST, bookId).apply()
        _last.value = bookId
    }

    private val _last = MutableStateFlow(prefs.getString(KEY_LAST, null))
    /** The book read last — «Продолжить чтение» (D-59). */
    val lastBook: StateFlow<String?> = _last.asStateFlow()

    var fontScale: Float
        get() = prefs.getFloat("font_scale", 1f)
        set(value) { prefs.edit().putFloat("font_scale", value).apply() }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    companion object {
        private const val MAX_BYTES = 80L * 1024 * 1024
        private const val KEY_LAST = "last_book"
        private const val MAX_PDF_BYTES = 400L * 1024 * 1024
        private const val PART = ".part"
    }
}

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

/** [asPages] — a scanned PDF without text, shown as page images. */
data class BookInfo(val bookId: String, val title: String, val format: BookFormat, val asPages: Boolean = false)

/**
 * Book files the user loads from the phone for spiritual reading (D-37).
 * They stay in the app's private storage: no book text is in the repository
 * or the content pack (the rights belong to their publishers), and they are
 * not part of the Drive backup — after a restore the files are loaded again.
 */
@Singleton
class BookLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val dir = File(context.filesDir, "books").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("books", Context.MODE_PRIVATE)

    private val _books = MutableStateFlow(scan())
    /** Loaded books by the reading programme's `bookId`. */
    val books: StateFlow<Map<String, BookInfo>> = _books.asStateFlow()

    private fun scan(): Map<String, BookInfo> = dir.listFiles().orEmpty().mapNotNull { f ->
        val id = f.nameWithoutExtension
        val format = when (f.extension) { "json" -> prefs.getString("$id.format", null)?.let(BookFormat::valueOf) ?: BookFormat.EPUB; "pdf" -> BookFormat.PDF; else -> null }
        format?.let { BookInfo(id, prefs.getString("$id.title", null) ?: id, it, asPages = f.extension == "pdf") }
    }.associateBy { it.bookId }

    /** Reads the picked file, parses it (a scanned PDF is kept as is) and replaces any earlier file of this book. */
    suspend fun import(bookId: String, uri: Uri): Result<BookInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName(uri) ?: bookId
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) throw BookFormatException("too large")
                }
                out.toByteArray()
            } ?: throw BookFormatException("unreadable")
            val format = BookParser.detect(bytes)
            remove(bookId)
            val fileTitle = name.substringBeforeLast('.')
            // A PDF with a text layer becomes chapters like any other book (D-40); a scan stays as pages.
            val book = if (format == BookFormat.PDF) {
                runCatching { PdfText.extract(context, bytes) }.getOrNull()
                    ?.let { BookParser.pdf(it.pages, it.outline, it.title ?: fileTitle) }
            } else BookParser.parse(bytes, fileTitle)
            val title: String
            if (book != null) {
                File(dir, "$bookId.json").writeText(json.encodeToString(ParsedBook.serializer(), book))
                title = book.title
            } else {
                File(dir, "$bookId.pdf").writeBytes(bytes)
                title = fileTitle
            }
            prefs.edit().putString("$bookId.title", title).putString("$bookId.format", format.name).apply()
            _books.value = scan()
            _books.value.getValue(bookId)
        }
    }

    suspend fun load(bookId: String): ParsedBook? = withContext(Dispatchers.IO) {
        File(dir, "$bookId.json").takeIf { it.exists() }?.let { f -> runCatching { json.decodeFromString(ParsedBook.serializer(), f.readText()) }.getOrNull() }
    }

    fun pdf(bookId: String): File? = File(dir, "$bookId.pdf").takeIf { it.exists() }

    fun remove(bookId: String) {
        File(dir, "$bookId.json").delete(); File(dir, "$bookId.pdf").delete()
        prefs.edit().remove("$bookId.title").remove("$bookId.format").remove("$bookId.chapter").remove("$bookId.item").apply()
        _books.value = scan()
    }

    /** Where reading stopped: chapter (0 for a PDF) and paragraph or page. */
    fun position(bookId: String): Pair<Int, Int> = prefs.getInt("$bookId.chapter", 0) to prefs.getInt("$bookId.item", 0)
    fun savePosition(bookId: String, chapter: Int, item: Int) {
        prefs.edit().putInt("$bookId.chapter", chapter).putInt("$bookId.item", item).apply()
    }

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
    }
}

package com.itermeum.app.core.data.books

import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import kotlinx.serialization.Serializable

@Serializable data class BookChapter(val title: String, val paragraphs: List<String>)
@Serializable data class ParsedBook(val title: String, val chapters: List<BookChapter>)

class BookFormatException(message: String) : Exception(message)

enum class BookFormat { EPUB, FB2, TXT, PDF }

/**
 * Turns a book file the user owns into chapters of plain paragraphs (D-37).
 * EPUB, FB2 (also zipped) and TXT are parsed here; for a PDF, [pdf] turns
 * the text PdfBox extracts into the same chapters (D-40) — a scan without a
 * text layer stays as page images. Pure Kotlin: no Android types, so it is
 * unit-tested.
 */
object BookParser {

    fun detect(bytes: ByteArray): BookFormat = when {
        bytes.startsWith("%PDF") -> BookFormat.PDF
        bytes.startsWith("PK") -> {
            val names = zipNames(bytes)
            when {
                "META-INF/container.xml" in names -> BookFormat.EPUB
                names.any { it.endsWith(".fb2", ignoreCase = true) } -> BookFormat.FB2
                else -> throw BookFormatException("zip without a book")
            }
        }
        String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1).contains("<FictionBook") -> BookFormat.FB2
        else -> BookFormat.TXT
    }

    /** [fallbackTitle] — the file name, used when the book carries no title. */
    fun parse(bytes: ByteArray, fallbackTitle: String): ParsedBook {
        val book = when (detect(bytes)) {
            BookFormat.EPUB -> epub(bytes, fallbackTitle)
            BookFormat.FB2 -> fb2(if (bytes.startsWith("PK")) unzip(bytes) { it.endsWith(".fb2", true) }.values.first() else bytes, fallbackTitle)
            BookFormat.TXT -> txt(bytes, fallbackTitle)
            BookFormat.PDF -> throw BookFormatException("pdf is rendered, not parsed")
        }
        return finish(book) ?: throw BookFormatException("no text")
    }

    /** Drops empty chapters and names untitled ones; null when nothing is left. */
    private fun finish(book: ParsedBook): ParsedBook? {
        val chapters = book.chapters.filter { it.paragraphs.isNotEmpty() }
        if (chapters.isEmpty()) return null
        return book.copy(chapters = chapters.mapIndexed { i, c -> if (c.title.isBlank()) c.copy(title = "Часть ${i + 1}") else c })
    }

    // ── EPUB ─────────────────────────────────────────────────────────────────

    private fun epub(bytes: ByteArray, fallbackTitle: String): ParsedBook {
        val files = unzip(bytes) { n -> MARKUP.any { n.endsWith(it, ignoreCase = true) } }
        val container = files["META-INF/container.xml"]?.let(::xmlText) ?: throw BookFormatException("no container")
        val opfPath = Regex("full-path\\s*=\\s*\"([^\"]+)\"").find(container)?.groupValues?.get(1) ?: throw BookFormatException("no rootfile")
        val opf = files[opfPath]?.let(::xmlText) ?: throw BookFormatException("no opf")
        val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val manifest = Regex("<(?:\\w+:)?item\\b[^>]*>").findAll(opf).associate { m ->
            attr(m.value, "id") to attr(m.value, "href")
        }
        val spine = Regex("<(?:\\w+:)?itemref\\b[^>]*>").findAll(opf).map { attr(it.value, "idref") }.toList()
        val title = Regex("<dc:title[^>]*>(.*?)</dc:title>", RegexOption.DOT_MATCHES_ALL).find(opf)?.groupValues?.get(1)
            ?.let { Markup.decode(it).trim() }?.takeIf { it.isNotEmpty() } ?: fallbackTitle
        val chapters = spine.mapNotNull { id -> manifest[id] }.flatMap { href ->
            val path = resolve(base, URLDecoder.decode(href.substringBefore('#'), "UTF-8"))
            files[path]?.let { html(xmlText(it)) } ?: emptyList()
        }
        return ParsedBook(title, chapters)
    }

    /** XHTML → chapters: an h1–h3 after some text starts a new chapter. */
    internal fun html(source: String): List<BookChapter> {
        val chapters = mutableListOf<BookChapter>()
        var title = ""
        var paragraphs = mutableListOf<String>()
        val buf = StringBuilder()
        var skip = 0
        var heading = false
        fun flush() {
            val t = Markup.squash(buf); buf.setLength(0)
            if (t.isEmpty()) return
            if (heading) {
                if (paragraphs.isNotEmpty()) { chapters += BookChapter(title, paragraphs); paragraphs = mutableListOf(); title = "" }
                title = if (title.isEmpty()) t else "$title. $t"
            } else paragraphs += t
        }
        for (tok in Markup.tokens(source)) {
            when (tok) {
                is Markup.Token.Text -> if (skip == 0) buf.append(tok.text)
                is Markup.Token.Tag -> when (tok.name) {
                    "head", "script", "style" -> if (tok.closing) skip = maxOf(0, skip - 1) else if (!tok.selfClosing) skip++
                    "h1", "h2", "h3" -> { flush(); heading = !tok.closing }
                    in BLOCK -> flush()
                }
            }
        }
        flush()
        chapters += BookChapter(title, paragraphs)
        return chapters
    }

    // ── FB2 ──────────────────────────────────────────────────────────────────

    private fun fb2(bytes: ByteArray, fallbackTitle: String): ParsedBook {
        val source = xmlText(bytes)
        val chapters = mutableListOf<BookChapter>()
        var bookTitle = ""
        var title = StringBuilder()
        var paragraphs = mutableListOf<String>()
        val buf = StringBuilder()
        var inTitle = false
        var inBookTitle = false
        var inBody = false
        var skipBody = false
        var skip = 0
        fun endChapter() {
            if (paragraphs.isNotEmpty() || title.isNotEmpty()) chapters += BookChapter(title.toString(), paragraphs)
            title = StringBuilder(); paragraphs = mutableListOf()
        }
        fun flush() {
            val t = Markup.squash(buf); buf.setLength(0)
            if (t.isEmpty()) return
            when {
                inBookTitle -> bookTitle = t
                inTitle -> title.append(if (title.isEmpty()) t else ". $t")
                else -> paragraphs += t
            }
        }
        for (tok in Markup.tokens(source)) {
            when (tok) {
                // Only the book title from <description>; the rest of it (genres, authors, history) is not text.
                is Markup.Token.Text -> if (skip == 0 && (inBookTitle || (inBody && !skipBody))) buf.append(tok.text)
                is Markup.Token.Tag -> when (tok.name) {
                    "binary" -> if (tok.closing) skip = maxOf(0, skip - 1) else if (!tok.selfClosing) skip++
                    "book-title" -> { flush(); inBookTitle = !tok.closing }
                    // Notes and comments live in a second <body name="notes">: not part of the reading.
                    "body" -> { flush(); inBody = !tok.closing; skipBody = inBody && tok.attrs["name"] != null }
                    "section" -> if (!skipBody) { flush(); endChapter() }
                    "title" -> if (!skipBody) { flush(); inTitle = !tok.closing }
                    "p", "v", "subtitle", "text-author", "empty-line", "stanza" -> flush()
                }
            }
        }
        flush(); endChapter()
        return ParsedBook(bookTitle.ifEmpty { fallbackTitle }, chapters)
    }

    // ── TXT ──────────────────────────────────────────────────────────────────

    private val CHAPTER_LINE = Regex("^\\s*(Глава|ГЛАВА|Часть|ЧАСТЬ|Chapter|CHAPTER)([\\s.:].{0,80})?$")

    private fun txt(bytes: ByteArray, fallbackTitle: String): ParsedBook {
        val text = decodeGuess(bytes).replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split('\n')
        // Blank lines between paragraphs → join wrapped lines; otherwise one line is one paragraph.
        val blankSeparated = lines.count { it.isBlank() } * 4 >= lines.size
        val chapters = mutableListOf<BookChapter>()
        var title = ""
        var paragraphs = mutableListOf<String>()
        val buf = StringBuilder()
        fun flush() {
            val t = Markup.squash(buf); buf.setLength(0)
            if (t.isNotEmpty()) paragraphs += t
        }
        for (line in lines) {
            when {
                CHAPTER_LINE.matches(line) -> {
                    flush()
                    if (paragraphs.isNotEmpty()) chapters += BookChapter(title, paragraphs)
                    paragraphs = mutableListOf(); title = line.trim()
                }
                line.isBlank() -> flush()
                blankSeparated -> buf.append(line).append(' ')
                else -> { buf.append(line); flush() }
            }
        }
        flush()
        chapters += BookChapter(title, paragraphs)
        return ParsedBook(fallbackTitle, chapters)
    }

    // ── PDF text ─────────────────────────────────────────────────────────────

    /** Paragraph break the PDF text extractor puts where it sees one (D-40). */
    const val PDF_PARAGRAPH = "\u2029"

    private val PAGE_NUMBER = Regex("^[\\s\\-–—]*(\\d{1,4}|[ivxlcdmIVXLCDM]{1,7})[\\s\\-–—]*$")
    private val SENTENCE_END = Regex("[.!?…:;»”\"')\\]]$")

    /**
     * Text of a PDF, page by page, → chapters (D-40). Removes page numbers and
     * running heads/feet, glues hyphenated words and wrapped lines back into
     * paragraphs, carries a paragraph over a page break. Chapters come from the
     * PDF bookmarks ([outline]: title → page index), else from «Глава …» lines,
     * else every 10 pages. Null when there is (almost) no text: a scan.
     */
    fun pdf(pages: List<String>, outline: List<Pair<String, Int>>, title: String): ParsedBook? {
        // A scan has no text layer (or only on a few pages): it stays as page images.
        val textPages = pages.count { p -> p.count { it.isLetter() } >= 100 }
        if (pages.isEmpty() || textPages * 2 < pages.size) return null

        val pageLines = pages.map { p -> p.replace("\r", "").split('\n').map { it.trimEnd() } }
        // A line that opens or closes many pages is a running head or foot.
        val edges = pageLines.map { ls -> ls.filter { it.isNotBlank() } }.filter { it.size >= 3 }
            .flatMap { ls -> setOf(norm(ls.first()), norm(ls.last())) }.groupingBy { it }.eachCount()
        val repeated = edges.filter { (k, n) -> k.isNotEmpty() && n >= maxOf(3, pages.size / 3) }.keys

        // (page index, paragraph) in reading order.
        val paragraphs = mutableListOf<Pair<Int, String>>()
        pageLines.forEachIndexed { pageIndex, raw ->
            val lines = raw.filterNot { l -> PAGE_NUMBER.matches(l) || norm(l) in repeated }
            val widths = lines.filter { it.isNotBlank() }.map { it.replace(PDF_PARAGRAPH, "").trim().length }.sorted()
            val typical = widths.getOrNull(widths.size * 3 / 4) ?: 0
            val buf = StringBuilder()
            var pageStart = true
            fun flush() {
                val t = Markup.squash(buf); buf.setLength(0)
                if (t.isEmpty()) return
                val prev = paragraphs.lastOrNull()
                // The page broke a paragraph: it went on lower-case on the next page.
                if (pageStart && prev != null && prev.first == pageIndex - 1 && !SENTENCE_END.containsMatchIn(prev.second) && t.first().isLowerCase()) {
                    paragraphs[paragraphs.lastIndex] = prev.first to join(prev.second, t)
                } else paragraphs += pageIndex to t
                pageStart = false
            }
            for (line in lines) {
                val ends = line.contains(PDF_PARAGRAPH)
                val text = line.replace(PDF_PARAGRAPH, "").trim()
                if (text.isEmpty()) { flush(); continue }
                if (buf.isEmpty()) buf.append(text) else buf.replace(0, buf.length, join(buf.toString(), text))
                // A short line that ends a sentence is the last line of its paragraph.
                val shortEnd = typical > 20 && text.length < typical * 0.8 && SENTENCE_END.containsMatchIn(text)
                if (ends || shortEnd) flush()
            }
            flush()
        }
        if (paragraphs.isEmpty()) return null

        val marks = outline.filter { it.second in pages.indices && it.first.isNotBlank() }.sortedBy { it.second }.distinctBy { it.second }
        val chapters: List<BookChapter> = when {
            marks.size >= 2 -> {
                val out = mutableListOf<BookChapter>()
                val before = paragraphs.filter { it.first < marks.first().second }.map { it.second }
                if (before.isNotEmpty()) out += BookChapter("", before)
                marks.forEachIndexed { i, (name, start) ->
                    val end = marks.getOrNull(i + 1)?.second ?: Int.MAX_VALUE
                    out += BookChapter(name.trim(), paragraphs.filter { it.first in start until end }.map { it.second })
                }
                out
            }
            paragraphs.any { it.second.length <= 80 && CHAPTER_LINE.matches(it.second) } -> {
                val out = mutableListOf<BookChapter>()
                var name = ""
                var cur = mutableListOf<String>()
                paragraphs.forEach { (_, t) ->
                    if (t.length <= 80 && CHAPTER_LINE.matches(t)) {
                        if (cur.isNotEmpty()) out += BookChapter(name, cur)
                        name = t; cur = mutableListOf()
                    } else cur += t
                }
                out += BookChapter(name, cur)
                out
            }
            else -> paragraphs.groupBy { it.first / 10 }.map { (block, ps) ->
                BookChapter("Стр. ${block * 10 + 1}–${minOf(block * 10 + 10, pages.size)}", ps.map { it.second })
            }
        }
        return finish(ParsedBook(title, chapters))
    }

    /** «сло-» + «во» → «слово»; otherwise the two lines join with a space. */
    private fun join(a: String, b: String): String =
        if (a.length > 1 && (a.endsWith('-') || a.endsWith('\u00AD')) && a[a.length - 2].isLetter() && b.firstOrNull()?.isLowerCase() == true) a.dropLast(1) + b
        else "$a $b"

    private fun norm(line: String) = line.replace(PDF_PARAGRAPH, "").replace(Regex("\\d+"), "#").trim().lowercase()

    // ── helpers ──────────────────────────────────────────────────────────────

    private val MARKUP = listOf(".xml", ".opf", ".xhtml", ".html", ".htm", ".ncx", ".fb2")
    private val BLOCK = setOf("p", "div", "br", "li", "blockquote", "tr", "section", "h4", "h5", "h6", "dt", "dd", "pre", "hr")

    private fun ByteArray.startsWith(prefix: String) =
        size >= prefix.length && prefix.indices.all { this[it] == prefix[it].code.toByte() }

    private fun unzip(bytes: ByteArray, keep: (String) -> Boolean): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory && keep(e.name)) out[e.name] = zip.readBytes()
            }
        }
        return out
    }

    private fun zipNames(bytes: ByteArray): List<String> {
        val out = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { out += (zip.nextEntry ?: break).name } }
        return out
    }

    private fun attr(tag: String, name: String): String =
        Regex("\\b$name\\s*=\\s*\"([^\"]*)\"").find(tag)?.groupValues?.get(1)
            ?: Regex("\\b$name\\s*=\\s*'([^']*)'").find(tag)?.groupValues?.get(1) ?: ""

    private fun resolve(base: String, href: String): String {
        val parts = ArrayDeque<String>()
        "$base$href".split('/').forEach { p -> when (p) { "", "." -> Unit; ".." -> parts.removeLastOrNull(); else -> parts.addLast(p) } }
        return parts.joinToString("/")
    }

    /** XML is decoded by its own declaration (FB2 is often windows-1251). */
    internal fun xmlText(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, 256), Charsets.ISO_8859_1)
        val declared = Regex("encoding\\s*=\\s*[\"']([\\w.-]+)[\"']").find(head)?.groupValues?.get(1)
        val charset = declared?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(bytes, charset).removePrefix("\uFEFF")
    }

    /** UTF-8 if it decodes cleanly, else windows-1251 — the two encodings Russian text files come in. */
    internal fun decodeGuess(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
    } catch (e: CharacterCodingException) {
        String(bytes, Charset.forName("windows-1251"))
    }
}

/** A forgiving tag tokenizer for XHTML and FB2: books in the wild are rarely valid XML. */
internal object Markup {
    sealed interface Token {
        data class Tag(val name: String, val closing: Boolean, val selfClosing: Boolean, val attrs: Map<String, String>) : Token
        data class Text(val text: String) : Token
    }

    private val TAG = Regex("<!--.*?-->|<!\\[CDATA\\[(.*?)]]>|<[!?][^>]*>|<(/?)([A-Za-z][\\w:.-]*)([^>]*?)(/?)>", RegexOption.DOT_MATCHES_ALL)
    private val ATTR = Regex("([\\w:.-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

    fun tokens(source: String): Sequence<Token> = sequence {
        var at = 0
        for (m in TAG.findAll(source)) {
            if (m.range.first > at) yield(Token.Text(decode(source.substring(at, m.range.first))))
            at = m.range.last + 1
            val cdata = m.groups[1]
            val name = m.groups[3]
            when {
                cdata != null -> yield(Token.Text(cdata.value))
                name != null -> yield(
                    Token.Tag(
                        name = name.value.substringAfter(':').lowercase(),
                        closing = m.groupValues[2] == "/",
                        selfClosing = m.groupValues[5] == "/",
                        attrs = ATTR.findAll(m.groupValues[4]).associate { a -> a.groupValues[1].substringAfter(':') to (a.groups[2]?.value ?: a.groupValues[3]) },
                    ),
                )
            }
        }
        if (at < source.length) yield(Token.Text(decode(source.substring(at))))
    }

    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);")
    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00A0",
        "laquo" to "«", "raquo" to "»", "bdquo" to "„", "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’",
        "mdash" to "—", "ndash" to "–", "hellip" to "…", "shy" to "", "copy" to "©", "sect" to "§", "deg" to "°",
        "middot" to "·", "times" to "×", "numero" to "№", "thinsp" to "\u2009", "ensp" to "\u2002", "emsp" to "\u2003",
    )

    fun decode(s: String): String = if ('&' !in s) s else ENTITY.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> NAMED[e]
        } ?: m.value
    }

    /** Collapses whitespace (including line breaks inside a paragraph) to single spaces. */
    fun squash(s: CharSequence): String = s.toString().replace(Regex("[\\s\u00A0]+"), " ").trim()
}

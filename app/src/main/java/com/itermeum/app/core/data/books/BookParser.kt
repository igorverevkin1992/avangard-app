package com.itermeum.app.core.data.books

import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import kotlinx.serialization.Serializable

/** [part] — the enclosing sections, outermost first, joined with « · » (D-68): «Ветхий Завет · Бытие». */
@Serializable data class BookChapter(val title: String, val paragraphs: List<String>, val part: String? = null)
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

    /**
     * D-72: chapters in a saved book file without decoding it — each chapter has one
     * `"paragraphs":` key (inside a text the quotes are escaped, so it cannot match there).
     */
    fun countChapters(input: java.io.InputStream): Int {
        val key = "\"paragraphs\":".toByteArray(Charsets.US_ASCII)
        var matched = 0; var count = 0
        while (true) {
            val b = input.read()
            if (b < 0) return count
            matched = when {
                b.toByte() == key[matched] -> matched + 1
                b.toByte() == key[0] -> 1
                else -> 0
            }
            if (matched == key.size) { count++; matched = 0 }
        }
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

    /** How many levels above a chapter are searched for its book and testament (D-74). */
    private const val PART_LOOKUP = 8

    private fun fb2(bytes: ByteArray, fallbackTitle: String): ParsedBook {
        // D-63: embedded pictures (<binary>, base64) can be most of the file: cut them out
        // as bytes, before decoding, so a big illustrated FB2 does not flood the memory.
        val source = xmlText(stripBinaries(bytes))
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
        // D-68: titles of the open sections, outermost first — a Bible nests book → chapter,
        // and the book's own section has no text of its own, so its title lives on here.
        val sections = mutableListOf<String>()
        // D-74: at most the two nearest titled sections above the chapter (testament, book), looked
        // for within a few levels — constant work per chapter even when a file never closes its
        // sections (the stack then grows to thousands, and joining it all made minutes and a full memory).
        fun part(): String? {
            val found = ArrayList<String>(2)
            var i = sections.size - 2
            val stop = maxOf(-1, sections.size - 2 - PART_LOOKUP)
            while (i > stop && found.size < 2) { sections[i].takeIf { it.isNotBlank() }?.let { found += it }; i-- }
            return if (found.isEmpty()) null else found.asReversed().joinToString(" · ")
        }
        fun endChapter() {
            if (paragraphs.isNotEmpty() || title.isNotEmpty()) chapters += BookChapter(title.toString(), paragraphs, part())
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
                    "section" -> if (!skipBody) {
                        flush(); endChapter()
                        if (tok.closing) sections.removeLastOrNull() else if (!tok.selfClosing) sections += ""
                    }
                    "title" -> if (!skipBody) {
                        flush(); inTitle = !tok.closing
                        if (tok.closing && sections.isNotEmpty()) sections[sections.lastIndex] = title.toString()
                    }
                    "p", "v", "subtitle", "text-author", "empty-line", "stanza" -> flush()
                }
            }
        }
        flush(); endChapter()
        return ParsedBook(bookTitle.ifEmpty { fallbackTitle }, chapters)
    }

    /** Removes every `<binary …>…</binary>` block, working on bytes (ASCII tags in any encoding). */
    internal fun stripBinaries(bytes: ByteArray): ByteArray {
        val open = "<binary".toByteArray(Charsets.ISO_8859_1)
        val close = "</binary>".toByteArray(Charsets.ISO_8859_1)
        fun find(p: ByteArray, from: Int): Int {
            var i = from
            outer@ while (i <= bytes.size - p.size) {
                for (j in p.indices) if (bytes[i + j] != p[j]) { i++; continue@outer }
                return i
            }
            return -1
        }
        var at = find(open, 0)
        if (at < 0) return bytes
        val out = java.io.ByteArrayOutputStream(bytes.size)
        var from = 0
        while (at >= 0) {
            out.write(bytes, from, at - from)
            val end = find(close, at)
            if (end < 0) { from = bytes.size; break }
            from = end + close.size
            at = find(open, from)
        }
        if (from < bytes.size) out.write(bytes, from, bytes.size - from)
        return out.toByteArray()
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

        // No trimming here: Kotlin counts the paragraph mark U+2029 as whitespace.
        val pageLines = pages.map { p -> p.replace("\r", "").split('\n') }
        // A line that opens or closes many pages is a running head or foot.
        val edges = pageLines.map { ls -> ls.filter { it.isNotBlank() } }.filter { it.size >= 3 }
            .flatMap { ls -> setOf(norm(ls.first()), norm(ls.last())) }.groupingBy { it }.eachCount()
        val repeated = edges.filter { (k, n) -> k.isNotEmpty() && n >= maxOf(3, pages.size / 3) }.keys

        // (page index, paragraph) in reading order.
        val paragraphs = mutableListOf<Pair<Int, String>>()
        pageLines.forEachIndexed { pageIndex, raw ->
            val lines = raw.filterNot { l -> PAGE_NUMBER.matches(l.replace(PDF_PARAGRAPH, "")) || norm(l) in repeated }
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

    private val ATTR = Regex("([\\w:.-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

    /**
     * D-75: a hand-written scan, not `Regex.findAll` over the whole book. On Android each
     * match of `findAll` makes a new matcher, and the ICU matcher copies the whole input:
     * a Bible (3.7 M chars, 73 000 tags) took tens of minutes on the phone and half a second
     * on a desktop. Same tokens as the former pattern
     * `<!--.*?-->|<![CDATA[(.*?)]]>|<[!?][^>]*>|<(/?)([A-Za-z][\w:.-]*)([^>]*?)(/?)>`.
     */
    fun tokens(source: String): Sequence<Token> = sequence {
        val n = source.length
        var textStart = 0
        var i = source.indexOf('<')
        fun after(gt: Int) = if (gt < 0) -1 else gt + 1
        while (i >= 0) {
            var end = -1
            var token: Token? = null
            when {
                source.startsWith("<!--", i) -> {
                    val e = source.indexOf("-->", i + 4)
                    end = if (e >= 0) e + 3 else after(source.indexOf('>', i))
                }
                source.startsWith("<![CDATA[", i) -> {
                    val e = source.indexOf("]]>", i + 9)
                    if (e >= 0) { token = Token.Text(source.substring(i + 9, e)); end = e + 3 } else end = after(source.indexOf('>', i))
                }
                i + 1 < n && (source[i + 1] == '!' || source[i + 1] == '?') -> end = after(source.indexOf('>', i))
                else -> {
                    var j = i + 1
                    val closing = j < n && source[j] == '/'
                    if (closing) j++
                    if (j < n && (source[j] in 'a'..'z' || source[j] in 'A'..'Z')) {
                        val nameStart = j
                        while (j < n && (source[j].isLetterOrDigit() || source[j] == '_' || source[j] == ':' || source[j] == '.' || source[j] == '-')) j++
                        val gt = source.indexOf('>', j)
                        if (gt >= 0) {
                            val selfClosing = gt > j && source[gt - 1] == '/'
                            val attrs = source.substring(j, if (selfClosing) gt - 1 else gt)
                            token = Token.Tag(
                                name = source.substring(nameStart, j).substringAfter(':').lowercase(),
                                closing = closing,
                                selfClosing = selfClosing,
                                attrs = if ('=' !in attrs) emptyMap() else ATTR.findAll(attrs).associate { a -> a.groupValues[1].substringAfter(':') to (a.groups[2]?.value ?: a.groupValues[3]) },
                            )
                            end = gt + 1
                        }
                    }
                }
            }
            // Not markup (a lone «<»): it stays in the text.
            if (end < 0) { i = source.indexOf('<', i + 1); continue }
            if (i > textStart) yield(Token.Text(decode(source.substring(textStart, i))))
            token?.let { yield(it) }
            textStart = end
            i = if (end < n) source.indexOf('<', end) else -1
        }
        if (textStart < n) yield(Token.Text(decode(source.substring(textStart))))
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
    private val SPACES = Regex("[\\s\u00A0]+")
    fun squash(s: CharSequence): String = s.toString().replace(SPACES, " ").trim()
}

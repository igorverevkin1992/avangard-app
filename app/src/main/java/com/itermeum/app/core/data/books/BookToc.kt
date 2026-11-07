package com.itermeum.app.core.data.books

/**
 * D-68: the contents of a book in two levels — the parts (books of the Bible,
 * with their testament) and the chapters inside each — for long books whose
 * chapters alone («Глава 1» × 1189) say nothing about where one is.
 */
object BookToc {

    /** A run of consecutive chapters of one part. [section] — the level above the book («Новый Завет»). */
    data class Group(val part: String?, val book: String, val section: String?, val chapters: List<Int>)

    /** Consecutive chapters with the same part, in order; one group with no part for a flat book. */
    fun groups(chapters: List<BookChapter>): List<Group> {
        val out = mutableListOf<Group>()
        chapters.forEachIndexed { i, c ->
            val last = out.lastOrNull()
            if (last != null && last.part == c.part) out[out.lastIndex] = last.copy(chapters = last.chapters + i)
            else {
                val segs = c.part?.split(" · ")
                out += Group(c.part, segs?.last().orEmpty(), segs?.dropLast(1)?.joinToString(" · ")?.ifEmpty { null }, listOf(i))
            }
        }
        return out
    }

    /** True when the book has parts worth a two-level contents. */
    fun grouped(chapters: List<BookChapter>): Boolean = chapters.any { it.part != null }

    private val NUMBERED = Regex("^(?:Глава|ГЛАВА|глава|Псалом|ПСАЛОМ|Chapter|CHAPTER)?\\s*(\\d{1,3})\\s*\\.?$")

    /** «Глава 12» → «12» for the chapter grid; any other title stays as it is. */
    fun shortTitle(title: String): String = NUMBERED.find(title.trim())?.groupValues?.get(1) ?: title

    /** «Бытие 3» — where one is, for the header of the reader. */
    fun where(c: BookChapter): String {
        val book = c.part?.substringAfterLast(" · ") ?: return c.title
        val n = NUMBERED.find(c.title.trim())?.groupValues?.get(1)
        return if (n != null) "$book $n" else "$book · ${c.title}"
    }
}

package com.itermeum.app.core.liturgy

/**
 * A gospel/scripture reference such as "Mt 6:1-6,16-18", "Mt 26:14-27:66" or
 * "Jn 15:26-27,16:12-15". Verse letters (a/b) are not used in the tables.
 */
data class BibleReference(val book: String, val segments: List<Segment>) {

    /** Inclusive range; `endChapter` may differ from `startChapter`. */
    data class Segment(val startChapter: Int, val startVerse: Int, val endChapter: Int, val endVerse: Int)

    /** Human form in Russian abbreviations, e.g. «Мф 6:1–6,16–18». */
    fun display(shortName: String): String {
        val sb = StringBuilder(shortName).append(' ')
        var lastChapter = -1
        segments.forEachIndexed { i, s ->
            if (i > 0) sb.append(',')
            if (s.startChapter != lastChapter) sb.append(s.startChapter).append(':')
            sb.append(s.startVerse)
            if (s.endChapter != s.startChapter) sb.append('–').append(s.endChapter).append(':').append(s.endVerse)
            else if (s.endVerse != s.startVerse) sb.append('–').append(s.endVerse)
            lastChapter = s.endChapter
        }
        return sb.toString()
    }

    companion object {
        private val HEAD = Regex("""^\s*([1-3]?[A-Za-z]+)\s+(.+)$""")

        fun parse(text: String): BibleReference {
            val m = HEAD.matchEntire(text) ?: throw IllegalArgumentException("Bad reference: $text")
            val book = m.groupValues[1]
            val body = m.groupValues[2].replace('–', '-').replace(" ", "")
            val segments = mutableListOf<Segment>()
            var chapter = -1
            for (part in body.split(',')) {
                val (startStr, endStr) = part.split('-', limit = 2).let { it[0] to it.getOrNull(1) }
                val (sc, sv) = parsePoint(startStr, chapter)
                chapter = sc
                val (ec, ev) = if (endStr == null) sc to sv else parsePoint(endStr, sc)
                chapter = ec
                segments += Segment(sc, sv, ec, ev)
            }
            return BibleReference(book, segments)
        }

        private fun parsePoint(s: String, currentChapter: Int): Pair<Int, Int> {
            val colon = s.indexOf(':')
            return if (colon >= 0) s.substring(0, colon).toInt() to s.substring(colon + 1).toInt()
            else {
                require(currentChapter > 0) { "Verse without chapter: $s" }
                currentChapter to s.toInt()
            }
        }
    }
}

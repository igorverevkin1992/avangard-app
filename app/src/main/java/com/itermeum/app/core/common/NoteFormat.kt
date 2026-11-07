package com.itermeum.app.core.common

/**
 * D-69: plain-text lists in notes — «• » bullets and «1. » numbers. Enter on a list line
 * starts the next item; Enter on an empty item ends the list. Pure: the text field
 * applies the result.
 */
object NoteFormat {
    const val BULLET = "• "
    private val NUMBER = Regex("^(\\d{1,3})\\. ")

    data class Edit(val text: String, val cursor: Int)

    private fun lineStart(text: String, pos: Int): Int = text.lastIndexOf('\n', pos - 1) + 1
    private fun lineEnd(text: String, pos: Int): Int = text.indexOf('\n', pos).let { if (it < 0) text.length else it }

    private fun prefixOf(line: String): String? = when {
        line.startsWith(BULLET) -> BULLET
        else -> NUMBER.find(line)?.value
    }

    /** The edit after typing, or null when nothing is to change (not an Enter on a list line). */
    fun afterEnter(old: String, new: String, cursor: Int): Edit? {
        if (new.length != old.length + 1 || cursor < 1 || cursor > new.length || new[cursor - 1] != '\n') return null
        if (new.removeRange(cursor - 1, cursor) != old) return null
        val start = lineStart(new, cursor - 1)
        val line = new.substring(start, cursor - 1)
        val prefix = prefixOf(line) ?: return null
        // Enter on an empty item: the list ends, the empty prefix goes.
        if (line == prefix) return Edit(new.removeRange(start, cursor), start)
        val next = NUMBER.find(prefix)?.let { "${it.groupValues[1].toInt() + 1}. " } ?: BULLET
        return Edit(new.substring(0, cursor) + next + new.substring(cursor), cursor + next.length)
    }

    /** Turns the line at [cursor] into a bullet item, or back into plain text. */
    fun toggleBullet(text: String, cursor: Int): Edit = toggle(text, cursor) { BULLET }

    /** Turns the line at [cursor] into a numbered item (one more than the line above), or back. */
    fun toggleNumber(text: String, cursor: Int): Edit = toggle(text, cursor) { start ->
        val above = if (start == 0) null else text.substring(lineStart(text, start - 1), start - 1)
        val n = above?.let { NUMBER.find(it)?.groupValues?.get(1)?.toInt() } ?: 0
        "${n + 1}. "
    }

    private fun toggle(text: String, cursor: Int, make: (Int) -> String): Edit {
        val c = cursor.coerceIn(0, text.length)
        val start = lineStart(text, c)
        val line = text.substring(start, lineEnd(text, c))
        val old = prefixOf(line)
        val wanted = make(start)
        val sameKind = old != null && (old == BULLET) == (wanted == BULLET)
        val replacement = if (sameKind) "" else wanted
        val removed = old?.length ?: 0
        val out = text.substring(0, start) + replacement + text.substring(start + removed)
        return Edit(out, (c - removed).coerceAtLeast(start) + replacement.length)
    }
}

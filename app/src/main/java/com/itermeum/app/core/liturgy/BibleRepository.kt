package com.itermeum.app.core.liturgy

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BibleBook(val code: String, val short: String, val name: String, val chapters: List<List<String>>)

@Serializable
data class BibleFile(val translation: String, val legal: String, val source: String, val books: List<BibleBook>)

data class Verse(val chapter: Int, val verse: Int, val text: String)

data class Passage(val reference: BibleReference, val bookShort: String, val bookName: String, val verses: List<Verse>) {
    val title: String get() = reference.display(bookShort)
}

/** Section 11.2: Synodal New Testament from `assets/bible/synodal_nt.json`, loaded once. */
@Singleton
class BibleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    @Volatile private var cache: Map<String, BibleBook>? = null

    private suspend fun books(): Map<String, BibleBook> = cache ?: withContext(Dispatchers.IO) {
        val file: BibleFile = context.assets.open("bible/synodal_nt.json").bufferedReader().use { json.decodeFromString(it.readText()) }
        file.books.associateBy { it.code }.also { cache = it }
    }

    suspend fun passage(ref: BibleReference): Passage? {
        val book = books()[ref.book] ?: return null
        val verses = mutableListOf<Verse>()
        for (s in ref.segments) {
            for (ch in s.startChapter..s.endChapter) {
                val chapter = book.chapters.getOrNull(ch - 1) ?: return null
                val from = if (ch == s.startChapter) s.startVerse else 1
                val to = if (ch == s.endChapter) minOf(s.endVerse, chapter.size) else chapter.size
                if (from > chapter.size) return null
                for (v in from..to) verses += Verse(ch, v, chapter[v - 1])
            }
        }
        return Passage(ref, book.short, book.name, verses)
    }

    suspend fun passage(reference: String): Passage? = passage(BibleReference.parse(reference))

    suspend fun chapter(bookCode: String, chapter: Int): List<Verse>? =
        books()[bookCode]?.chapters?.getOrNull(chapter - 1)?.mapIndexed { i, t -> Verse(chapter, i + 1, t) }
}

package com.itermeum.app.core.data.books

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookParserTest {

    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> files.forEach { (name, text) -> z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() } }
        return out.toByteArray()
    }

    @Test fun `epub follows the spine and splits chapters on headings`() {
        val epub = zip(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<?xml version="1.0"?><container><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            "OEBPS/content.opf" to """<?xml version="1.0" encoding="UTF-8"?>
                <package><metadata><dc:title>Путь</dc:title></metadata>
                <manifest><item id="c2" href="Text/ch%202.xhtml" media-type="application/xhtml+xml"/><item id="c1" href="Text/ch1.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>""",
            "OEBPS/Text/ch1.xhtml" to """<html><head><title>skip me</title><style>p{}</style></head><body>
                <h2>Характер</h2><p>1. Пусть твоя жизнь&nbsp;не будет &laquo;бесплодной&raquo;.</p>
                <p>2. Будь твёрд &mdash;
                   мягок в форме.</p></body></html>""",
            "OEBPS/Text/ch 2.xhtml" to """<html><body><h2>Воля</h2><p>11. Воля.</p><h2>Мелочи</h2><p>&#1040;&#x411;</p></body></html>""",
        )
        assertEquals(BookFormat.EPUB, BookParser.detect(epub))
        val book = BookParser.parse(epub, "file")
        assertEquals("Путь", book.title)
        assertEquals(listOf("Характер", "Воля", "Мелочи"), book.chapters.map { it.title })
        assertEquals(listOf("1. Пусть твоя жизнь не будет «бесплодной».", "2. Будь твёрд — мягок в форме."), book.chapters[0].paragraphs)
        assertEquals(listOf("АБ"), book.chapters[2].paragraphs)
        assertFalse(book.chapters.flatMap { it.paragraphs }.any { "skip me" in it })
    }

    @Test fun `fb2 in windows-1251 keeps sections and skips the description and notes`() {
        val fb2 = """<?xml version="1.0" encoding="windows-1251"?>
            <FictionBook xmlns:l="http://www.w3.org/1999/xlink"><description><title-info><genre>religion</genre>
            <book-title>Борозда</book-title><annotation><p>Аннотация</p></annotation></title-info></description>
            <body><section><title><p>Глава 1</p><p>Труд</p></title><p>Первый абзац.</p><empty-line/><p>Второй <emphasis>абзац</emphasis>.</p></section>
            <section><title><p>Дисциплина</p></title><p>Третий.</p></section></body>
            <body name="notes"><section><p>Сноска</p></section></body>
            <binary id="cover.jpg">QUJD</binary></FictionBook>""".toByteArray(Charset.forName("windows-1251"))
        assertEquals(BookFormat.FB2, BookParser.detect(fb2))
        val book = BookParser.parse(fb2, "file")
        assertEquals("Борозда", book.title)
        assertEquals(listOf("Глава 1. Труд", "Дисциплина"), book.chapters.map { it.title })
        assertEquals(listOf("Первый абзац.", "Второй абзац."), book.chapters[0].paragraphs)
        val all = book.chapters.flatMap { it.paragraphs }
        assertFalse(all.any { "Сноска" in it || "Аннотация" in it || "religion" in it || "QUJD" in it })
    }

    @Test fun `zipped fb2 is read too`() {
        val inner = """<?xml version="1.0" encoding="utf-8"?><FictionBook><body><section><p>Текст</p></section></body></FictionBook>"""
        val bytes = zip("book.fb2" to inner)
        assertEquals(BookFormat.FB2, BookParser.detect(bytes))
        assertEquals(listOf("Текст"), BookParser.parse(bytes, "Кузня").chapters.single().paragraphs)
    }

    @Test fun `txt in windows-1251 splits on chapter lines and joins wrapped lines`() {
        val text = "Глава 1. Начало\n\nПервая строка\nпродолжение.\n\nВторой абзац.\n\nГлава 2\n\nКонец.\n"
        val book = BookParser.parse(text.toByteArray(Charset.forName("windows-1251")), "Беседы")
        assertEquals("Беседы", book.title)
        assertEquals(listOf("Глава 1. Начало", "Глава 2"), book.chapters.map { it.title })
        assertEquals(listOf("Первая строка продолжение.", "Второй абзац."), book.chapters[0].paragraphs)
    }

    @Test fun `utf-8 txt without blank lines keeps one paragraph per line`() {
        val book = BookParser.parse("﻿Один.\nДва.\nТри.".toByteArray(), "x")
        assertEquals(listOf("Один.", "Два.", "Три."), book.chapters.single().paragraphs)
        assertEquals("Часть 1", book.chapters.single().title)
    }

    @Test fun `pdf is detected, not parsed`() {
        assertEquals(BookFormat.PDF, BookParser.detect("%PDF-1.7 ...".toByteArray()))
    }

    @Test fun `empty or foreign zip is rejected`() {
        assertTrue(runCatching { BookParser.parse(zip("a.png" to "x"), "x") }.exceptionOrNull() is BookFormatException)
        assertTrue(runCatching { BookParser.parse("   \n\n".toByteArray(), "x") }.exceptionOrNull() is BookFormatException)
    }

    private val P = BookParser.PDF_PARAGRAPH
    private val body = "Слово за слово складывается длинная строка текста книги здесь"

    @Test fun `pdf text drops page numbers and running heads, glues hyphens and wrapped lines`() {
        val pages = (1..6).map { n ->
            "Христос проходит рядом\n" +
                "$body и пере-\nнос слова продолжается до самого конца стро\nки дальше.$P\n" +
                "Второй абзац страницы $n с обычным текстом и ещё одной длинной строкой\nкороткий конец.$P\n" +
                "$n\n"
        }
        val book = BookParser.pdf(pages, emptyList(), "Книга")!!
        val all = book.chapters.flatMap { it.paragraphs }
        assertFalse(all.any { "Христос проходит рядом" in it })
        assertTrue(all.first().contains("перенос слова"))
        assertTrue(all.first().contains("стро ки")) // only a hyphen joins without a space
        assertEquals(12, all.size)
        assertEquals("Второй абзац страницы 1 с обычным текстом и ещё одной длинной строкой короткий конец.", all[1])
    }

    @Test fun `pdf paragraph broken by a page goes on`() {
        val pages = listOf(
            "$body $body и предложение не закончено на этой\n",
            "странице, а продолжается на следующей. $body $body.$P\n",
        )
        val all = BookParser.pdf(pages, emptyList(), "x")!!.chapters.flatMap { it.paragraphs }
        assertEquals(1, all.size)
        assertTrue(all.single().contains("этой странице"))
    }

    @Test fun `pdf chapters come from bookmarks`() {
        val pages = (0..3).map { "$body $body страница $it.$P\n" }
        val book = BookParser.pdf(pages, listOf("Труд" to 1, "Дисциплина" to 3), "x")!!
        assertEquals(listOf("Часть 1", "Труд", "Дисциплина"), book.chapters.map { it.title })
        assertEquals(2, book.chapters[1].paragraphs.size)
    }

    @Test fun `pdf without bookmarks splits on chapter lines`() {
        val pages = listOf("Глава 1$P\n$body $body.$P\n", "Глава 2$P\n$body $body.$P\n")
        assertEquals(listOf("Глава 1", "Глава 2"), BookParser.pdf(pages, emptyList(), "x")!!.chapters.map { it.title })
    }

    @Test fun `scanned pdf without text stays as pages`() {
        assertEquals(null, BookParser.pdf(List(10) { "" }, emptyList(), "x"))
        assertEquals(null, BookParser.pdf(listOf("$body $body $body") + List(9) { " " }, emptyList(), "x"))
    }
}

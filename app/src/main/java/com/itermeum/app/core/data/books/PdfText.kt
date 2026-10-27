package com.itermeum.app.core.data.books

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/** Text layer of a PDF through PdfBox-Android (D-40); [BookParser.pdf] makes chapters of it. */
object PdfText {

    class Extracted(val title: String?, val pages: List<String>, val outline: List<Pair<String, Int>>)

    private const val PAGE_BREAK = "\u000C"

    fun extract(context: Context, bytes: ByteArray): Extracted {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(bytes).use { doc ->
            // Books with owner-password permissions only: reading the text is allowed.
            if (doc.isEncrypted) doc.setAllSecurityToBeRemoved(true)
            val stripper = PDFTextStripper().apply {
                setSortByPosition(true)
                setLineSeparator("\n")
                setParagraphStart("")
                setParagraphEnd(BookParser.PDF_PARAGRAPH + "\n")
                setPageEnd(PAGE_BREAK)
            }
            val pages = stripper.getText(doc).split(PAGE_BREAK).take(doc.numberOfPages)
            val outline = mutableListOf<Pair<String, Int>>()
            // One top bookmark (the book itself) → its children are the chapters.
            val top = doc.documentCatalog.documentOutline?.children()?.toList().orEmpty()
            val chapters = top.singleOrNull()?.takeIf { it.hasChildren() }?.children()?.toList() ?: top
            chapters.forEach { item ->
                val page = runCatching { item.findDestinationPage(doc) }.getOrNull() ?: return@forEach
                val index = doc.pages.indexOf(page)
                if (index >= 0) outline += (item.title ?: "") to index
            }
            return Extracted(doc.documentInformation?.title?.takeIf { it.isNotBlank() }, pages, outline)
        }
    }
}

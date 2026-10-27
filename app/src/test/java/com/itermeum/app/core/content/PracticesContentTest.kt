package com.itermeum.app.core.content

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticesContentTest {

    @Test fun `practices reference parses and covers every norm`() {
        val text = File("src/main/assets/content/practices.json").readText()
        val file = Json { ignoreUnknownKeys = true }.decodeFromString(PracticesFile.serializer(), text)
        val titles = file.sections.flatMap { it.items }.map { it.title }
        assertTrue(file.sections.all { s -> s.items.all { it.text.isNotEmpty() } })
        listOf("Подъём", "Ментальная молитва", "Розарий", "Испытание совести (Examen)", "Месса и духовное причастие", "Исповедь")
            .forEach { assertTrue("missing $it", it in titles) }
    }
}

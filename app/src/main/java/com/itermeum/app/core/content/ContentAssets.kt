package com.itermeum.app.core.content

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PrayerJson(
    val id: String, val type: String = "prayer", val title: String, val text: String,
    val source: String, val status: String, val legal: String, val verify: Boolean, val orderIndex: Int,
)

@Serializable data class PrayersFile(val contentPackVersion: Int, val items: List<PrayerJson>)

@Serializable
data class JaculatoriaJson(
    val id: String, val situation: String, val text: String, val source: String,
    val sourceKind: String, val legal: String, val verify: Boolean,
)

@Serializable data class SituationJson(val id: String, val title: String)
@Serializable data class JaculatoriaFile(val situations: List<SituationJson>, val items: List<JaculatoriaJson>)

@Serializable
data class AsceticJson(val id: String, val book: String, val number: String, val theme: String, val microCommitment: String, val legal: String = "")
@Serializable data class AsceticFile(val items: List<AsceticJson>)

@Serializable data class RosarySetJson(val title: String, val days: List<Int>, val mysteries: List<String>)
@Serializable data class RosaryFile(val verify: Boolean = true, val sets: Map<String, RosarySetJson>, val contemplations: Map<String, List<String>>)

@Serializable data class ReadingItemJson(val bookId: String, val title: String, val section: String, val url: String? = null)
@Serializable data class ReadingMonthJson(val month: Int, val items: List<ReadingItemJson>)
@Serializable data class ReadingFile(val months: List<ReadingMonthJson>)

@Serializable data class StringListFile(val items: List<String>)
@Serializable data class OrderFile(val order: List<String>)
@Serializable data class ExamenQuestionJson(val index: Int, val id: String, val title: String, val text: String)
@Serializable data class ExamenQuestionsFile(val items: List<ExamenQuestionJson>)
@Serializable data class MilestoneJson(val id: String, val title: String, val latin: String, val scripture: String)
@Serializable data class MilestonesFile(val items: List<MilestoneJson>)
@Serializable data class GlossaryFile(val items: List<List<String>>)
@Serializable data class PracticeJson(val title: String, val origin: String, val `when`: String, val text: List<String>)
@Serializable data class PracticeSectionJson(val title: String, val items: List<PracticeJson>)
@Serializable data class PracticesFile(val intro: String, val legend: List<List<String>>, val sections: List<PracticeSectionJson>)
@Serializable data class NotificationsFile(val items: Map<String, Map<String, String>>)

/** Typed access to `assets/content/…json` (section 7). Each file is parsed once. */
@Singleton
class ContentAssets @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private inline fun <reified T> load(name: String): T =
        context.assets.open("content/$name").bufferedReader().use { json.decodeFromString(it.readText()) }

    val prayers: PrayersFile by lazy { load("prayers.json") }
    val jaculatoria: JaculatoriaFile by lazy { load("jaculatoria.json") }
    val ascetic: AsceticFile by lazy { load("ascetic_catalog.json") }
    val rosary: RosaryFile by lazy { load("rosary.json") }
    val reading: ReadingFile by lazy { load("reading_program.json") }
    val mortifications: StringListFile by lazy { load("mortifications.json") }
    val virtues: StringListFile by lazy { load("virtues.json") }
    val memorizationOrder: OrderFile by lazy { load("memorization_order.json") }
    val examenQuestions: ExamenQuestionsFile by lazy { load("examen_questions.json") }
    val milestones: MilestonesFile by lazy { load("milestones.json") }
    val glossary: GlossaryFile by lazy { load("glossary.json") }
    val notifications: NotificationsFile by lazy { load("notifications.json") }
    val practices: PracticesFile by lazy { load("practices.json") }

    fun notificationText(key: String, level: Int): String? =
        notifications.items[key]?.get(level.toString())
}

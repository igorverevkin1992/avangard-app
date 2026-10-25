package com.itermeum.app.core.liturgy

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Raw access to `assets/lectionary/*.json` (section 11.2). The calendar
 * engine (phase 3) decides which key applies to a date; this class only
 * resolves keys to gospel references.
 */
@Singleton
class LectionaryTables @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private fun load(name: String): JsonObject =
        context.assets.open("lectionary/$name").bufferedReader().use { json.parseToJsonElement(it.readText()).jsonObject }

    private val sundays: JsonObject by lazy { load("sundays.json") }
    private val weekdays: JsonObject by lazy { load("weekdays.json") }
    private val solemnities: JsonObject by lazy { load("solemnities.json") }

    fun sunday(cycle: SundayCycle, slot: String): String? =
        sundays[cycle.name]?.jsonObject?.get(slot)?.jsonPrimitive?.content

    fun weekday(table: String, key: String): String? =
        weekdays[table]?.jsonObject?.get(key)?.jsonPrimitive?.content

    /** `MM-DD` → gospel; year-dependent entries (08-06) are resolved by [cycle]. */
    fun solemnity(monthDay: String, cycle: SundayCycle): String? {
        val node = solemnities[monthDay] ?: return null
        return if (node is JsonObject) node[cycle.name]?.jsonPrimitive?.content else node.jsonPrimitive.content
    }

    fun hasSolemnity(monthDay: String): Boolean = solemnities.containsKey(monthDay)

    fun allSundaySlots(cycle: SundayCycle): Set<String> = sundays[cycle.name]!!.jsonObject.keys
    fun allWeekdayKeys(table: String): Set<String> = weekdays[table]!!.jsonObject.keys
    fun allSolemnityKeys(): Set<String> = solemnities.keys
}

enum class SundayCycle { A, B, C }
enum class WeekdayCycle { I, II }

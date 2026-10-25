package com.itermeum.app.core.content

import androidx.room.withTransaction
import com.itermeum.app.core.database.AppDatabase
import com.itermeum.app.core.database.entity.AsceticItemEntity
import com.itermeum.app.core.database.entity.JaculatoriaEntity
import com.itermeum.app.core.database.entity.MemorizationItemEntity
import com.itermeum.app.core.database.entity.MilestoneEntity
import com.itermeum.app.core.database.entity.PrayerEntity
import com.itermeum.app.core.database.entity.ProfileEntity
import com.itermeum.app.core.database.entity.ReadingPlanItemEntity
import com.itermeum.app.core.database.entity.StreakStateEntity
import com.itermeum.app.core.database.entity.StreakType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies the content pack (assets) into Room. Idempotent: canonical rows
 * are refreshed, user-owned columns (`userEditedText`, norm `enabled` /
 * `userTime`, SRS progress) are preserved. Runs on every app start; cheap
 * when `Profile.contentPackVersion` already matches.
 */
@Singleton
class ContentSeeder @Inject constructor(
    private val db: AppDatabase,
    private val assets: ContentAssets,
) {
    suspend fun seedIfNeeded(force: Boolean = false) {
        val profile = db.profileDao().get()
        val targetVersion = assets.prayers.contentPackVersion
        if (!force && profile != null && profile.contentPackVersion == targetVersion && db.normDao().count() > 0) return
        seed()
        db.profileDao().upsert((profile ?: ProfileEntity()).copy(contentPackVersion = targetVersion))
    }

    suspend fun seed() = db.withTransaction {
        db.normDao().insertIgnore(NormCatalog.all)

        val prayers = assets.prayers.items.map {
            PrayerEntity(it.id, it.title, it.text, it.source, it.status, it.legal, it.verify, null, it.orderIndex)
        }
        db.prayerDao().insertIgnore(prayers)
        prayers.forEach {
            db.prayerDao().refreshCanonical(it.id, it.title, it.text, it.source, it.status, it.legal, it.verify, it.orderIndex)
        }

        db.prayerDao().upsertJaculatoria(
            assets.jaculatoria.items.map { JaculatoriaEntity(it.id, it.situation, it.text, it.source, it.legal, it.verify) },
        )
        db.prayerDao().upsertAscetic(
            assets.ascetic.items.map { AsceticItemEntity(it.id, it.book, it.number, it.theme, it.microCommitment) },
        )
        db.readingDao().upsertPlan(
            assets.reading.months.flatMap { m ->
                m.items.mapIndexed { i, it ->
                    ReadingPlanItemEntity("m${m.month}_${i}_${it.bookId}", m.month, it.bookId, it.title, it.section, it.url)
                }
            },
        )
        db.srsDao().insertIgnore(
            assets.memorizationOrder.order.mapIndexed { i, id -> MemorizationItemEntity(prayerId = id, orderIndex = i) },
        )
        db.streakDao().insertIgnore(StreakType.entries.map { StreakStateEntity(type = it) })
        db.streakDao().insertMilestonesIgnore(assets.milestones.items.map { MilestoneEntity(id = it.id) })
    }
}

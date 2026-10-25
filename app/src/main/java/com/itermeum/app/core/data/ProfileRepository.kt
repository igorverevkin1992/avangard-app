package com.itermeum.app.core.data

import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.database.dao.ProfileDao
import com.itermeum.app.core.database.entity.ProfileEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ProfileDao,
    private val clock: Clock,
) {
    val flow: Flow<ProfileEntity> = dao.observe().map { it ?: ProfileEntity() }

    suspend fun get(): ProfileEntity = dao.get() ?: ProfileEntity(createdAt = clock.logicalToday()).also { dao.upsert(it) }

    suspend fun update(transform: (ProfileEntity) -> ProfileEntity) {
        val current = get()
        val next = transform(current).let { if (it.createdAt == null) it.copy(createdAt = clock.logicalToday()) else it }
        dao.upsert(next)
    }
}

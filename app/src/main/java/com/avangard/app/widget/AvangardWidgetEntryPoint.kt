package com.avangard.app.widget

import com.avangard.app.core.common.Clock
import com.avangard.app.core.domain.repository.ReportRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AvangardWidgetEntryPoint {
    fun reportRepository(): ReportRepository
    fun clock(): Clock
}

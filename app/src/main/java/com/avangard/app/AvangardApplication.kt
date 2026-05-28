package com.avangard.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.data.cloud.SyncCoordinator
import com.avangard.app.core.database.dao.FocusSessionDao
import com.avangard.app.sync.notifications.SimpleNotificationPresenter
import com.avangard.app.sync.scheduler.EveningCloseScheduler
import com.avangard.app.sync.scheduler.IgnitionScheduler
import com.avangard.app.sync.scheduler.MiddayCheckScheduler
import dagger.hilt.android.HiltAndroidApp
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class AvangardApplication : Application(), Configuration.Provider {

    @Inject lateinit var scheduler: EveningCloseScheduler
    @Inject lateinit var ignitionScheduler: IgnitionScheduler
    @Inject lateinit var middayScheduler: MiddayCheckScheduler
    @Inject lateinit var presenter: SimpleNotificationPresenter
    @Inject lateinit var preferences: UserPreferencesRepository
    @Inject lateinit var syncCoordinator: SyncCoordinator
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var focusDao: FocusSessionDao
    @Inject lateinit var clock: Clock

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        initCrashReporting()
        presenter.ensureChannel()
        syncCoordinator.start()
        applicationScope.launch {
            // Warm the synchronous initialRestoreDone cache first so by the
            // time MainActivity reads it (typically tens of ms later) the
            // value is populated. First cold launch falls through to false
            // → Restoring overlay, which is the safe default.
            preferences.warmInitialRestoreCache()
            preferences.incrementAppLaunchAndMaybeVacuum()
            scheduler.ensureScheduled()
            ignitionScheduler.ensureScheduled()
            middayScheduler.ensureScheduled()
            closeStaleFocusOrphans()
        }
    }

    /**
     * Crash-or-kill recovery: any focus_session rows started before today
     * with `ended_at IS NULL` block every subsequent startFocus (partial
     * unique index `uniq_focus_active`). Close them off so the operator
     * isn't stuck on a stale ghost session.
     */
    private suspend fun closeStaleFocusOrphans() {
        val todayEpoch = clock.today().toStartOfDayEpoch(clock.zone())
        val closed = runCatching { focusDao.closeOrphansBefore(todayEpoch) }.getOrElse {
            Log.w("AvangardApp", "stale focus cleanup failed", it)
            0
        }
        if (closed > 0) Log.i("AvangardApp", "closed $closed stale focus orphan(s)")
    }

    /**
     * Opt-in crash reporting via Sentry. No-op when:
     *   * BuildConfig.DEBUG (development build),
     *   * the BuildConfig.SENTRY_DSN is empty (no DSN configured — public/open-source
     *     forks have empty DSN by default; set sentry.dsn in local.properties or
     *     SENTRY_DSN env to enable).
     * On enable: ANR detection on, but every captured event has its user and
     * request data stripped — we ship stack traces only, never PII.
     */
    private fun initCrashReporting() {
        if (BuildConfig.DEBUG) return
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank()) return
        SentryAndroid.init(this) { options ->
            options.dsn = dsn
            options.isAnrEnabled = true
            options.isEnableUserInteractionTracing = false
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                // Hard PII stripping: user (auto-captured identifiers) and
                // request (URLs / headers) are obvious. Also clear tags, which
                // Sentry auto-populates from contexts, and drop HTTP and
                // navigation breadcrumbs that could leak deep-link URIs or
                // form values. Stack traces are unaffected.
                event.user = null
                event.request = null
                event.tags?.clear()
                event.breadcrumbs?.removeAll { breadcrumb ->
                    val category = breadcrumb.category?.lowercase()
                    category == "http" || category == "navigation" || category == "ui.click"
                }
                event
            }
        }
    }
}

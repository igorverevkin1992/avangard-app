package com.avangard.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.data.cloud.SyncCoordinator
import com.avangard.app.sync.notifications.SimpleNotificationPresenter
import com.avangard.app.sync.scheduler.EveningCloseScheduler
import com.avangard.app.sync.scheduler.IgnitionScheduler
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
    @Inject lateinit var presenter: SimpleNotificationPresenter
    @Inject lateinit var preferences: UserPreferencesRepository
    @Inject lateinit var syncCoordinator: SyncCoordinator
    @Inject lateinit var workerFactory: HiltWorkerFactory

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
            preferences.incrementAppLaunchAndMaybeVacuum()
            scheduler.ensureScheduled()
            ignitionScheduler.ensureScheduled()
        }
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

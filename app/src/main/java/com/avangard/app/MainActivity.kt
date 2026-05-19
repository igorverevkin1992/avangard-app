package com.avangard.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.avangard.app.core.common.Clock
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.data.auth.AuthRepository
import com.avangard.app.core.domain.model.AccessPolicy
import kotlinx.coroutines.runBlocking
import com.avangard.app.navigation.AvangardNavHost
import com.avangard.app.navigation.AvangardNavigationBar
import com.avangard.app.navigation.NavRoute
import com.avangard.app.navigation.shouldShowBottomNav
import com.avangard.app.ui.theme.IsaColors
import com.avangard.app.ui.theme.MachineTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var clock: Clock
    @Inject lateinit var auth: AuthRepository
    @Inject lateinit var preferences: UserPreferencesRepository

    /**
     * Holds the most recent Intent so that notification deep-links delivered to
     * an already-running, singleTop activity (via onNewIntent) re-trigger the
     * nav-host start destination evaluation. Without this the foreground tap
     * on the 21:00 notification stayed on whatever screen was open.
     */
    private val intentState = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intentState.value = intent
        setContent {
            val current by intentState.collectAsState()
            val start = current?.getStringExtra(EXTRA_START_DESTINATION) ?: defaultStart()
            AvangardApp(start)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState.value = intent
    }

    private fun defaultStart(): String = when {
        // Sign-in is mandatory at launch. Without a Google account on file
        // the cloud-sync layer has no destination, so the rest of the app
        // is unreachable until the user signs in.
        !auth.isSignedIn -> NavRoute.SignIn.route
        // The first cold-start after a fresh install (or after sign-out)
        // routes through the restore overlay so the Drive snapshot, if
        // any, lands before the user touches the DB. Subsequent launches
        // skip — initialRestoreDone survives process death.
        !initialRestoreDone() -> NavRoute.Restoring.route
        AccessPolicy.isHistoryUnlocked(clock.today()) -> NavRoute.SundayAudit.route
        else -> NavRoute.OperatorPulpit.route
    }

    private fun initialRestoreDone(): Boolean =
        runBlocking { preferences.snapshot().initialRestoreDone }

    companion object {
        const val EXTRA_START_DESTINATION = "start_destination"
    }
}

@Composable
private fun AvangardApp(startDestination: String) {
    MachineTheme {
        val navController = rememberNavController()
        val showBottomBar = shouldShowBottomNav(navController)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = IsaColors.Graphite,
            contentWindowInsets = WindowInsets.systemBars,
            bottomBar = {
                // Modal routes (authorisation, evening close, sabotage, …)
                // own the full viewport. Tier-1 surfaces render the bar.
                if (showBottomBar) {
                    AvangardNavigationBar(navController = navController)
                }
            },
        ) { padding ->
            // Deep links push their start destination onto an already-running
            // graph if needed; the LaunchedEffect routes to it after the first
            // composition so the back-stack stays sane.
            LaunchedEffect(startDestination) {
                if (startDestination != NavRoute.OperatorPulpit.route &&
                    navController.currentDestination?.route == NavRoute.OperatorPulpit.route
                ) {
                    navController.navigate(startDestination)
                }
            }
            Box(Modifier.padding(padding)) {
                AvangardNavHost(
                    navController = navController,
                    startDestination = if (
                        startDestination == NavRoute.SundayAudit.route ||
                        startDestination == NavRoute.OperatorPulpit.route ||
                        startDestination == NavRoute.SignIn.route ||
                        startDestination == NavRoute.Restoring.route
                    ) startDestination else NavRoute.OperatorPulpit.route,
                )
            }
        }
    }
}

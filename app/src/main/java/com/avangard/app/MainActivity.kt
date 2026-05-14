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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.avangard.app.core.common.Clock
import com.avangard.app.core.domain.model.AccessPolicy
import com.avangard.app.navigation.AvangardNavHost
import com.avangard.app.navigation.NavRoute
import com.avangard.app.ui.theme.IsaColors
import com.avangard.app.ui.theme.MachineTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var clock: Clock

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

    private fun defaultStart(): String =
        if (AccessPolicy.isHistoryUnlocked(clock.today())) NavRoute.SundayAudit.route
        else NavRoute.OperatorPulpit.route

    companion object {
        const val EXTRA_START_DESTINATION = "start_destination"
    }
}

@Composable
private fun AvangardApp(startDestination: String) {
    MachineTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = IsaColors.Graphite,
            contentWindowInsets = WindowInsets.systemBars,
        ) { padding ->
            Box(Modifier.padding(padding)) {
                AvangardNavHost(startDestination = startDestination)
            }
        }
    }
}

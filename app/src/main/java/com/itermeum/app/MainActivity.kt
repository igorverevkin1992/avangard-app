package com.itermeum.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.itermeum.app.core.designsystem.IterMeumTheme
import com.itermeum.app.navigation.IterMeumNavHost
import com.itermeum.app.navigation.IterMeumNavigationBar
import com.itermeum.app.navigation.NavRoute
import com.itermeum.app.navigation.shouldShowBottomNav
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Latest intent, so deep-links delivered to the running singleTop activity re-route. */
    private val intentState = MutableStateFlow<Intent?>(null)
    private val root: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity (rotation, return from Recents) must not replay the deep link.
        intentState.value = if (savedInstanceState == null) intent else null
        setContent {
            val current by intentState.collectAsState()
            val target = NavRoute.fromDeepLink(current?.data) ?: current?.getStringExtra(EXTRA_ROUTE)
            val onboardingDone by root.onboardingDone.collectAsState()
            val day by root.logicalDate.collectAsState()
            val accent by root.accent.collectAsState()
            val light by root.lightTheme.collectAsState()
            when (onboardingDone) {
                null -> IterMeumTheme { Box(Modifier.fillMaxSize()) }
                false -> IterMeumRoot(deepLinkRoute = null, startDestination = NavRoute.Onboarding.route, light = light, onDeepLinkConsumed = {})
                // New logical day → fresh graph and fresh ViewModels for the new date.
                true -> key(day) {
                    IterMeumRoot(deepLinkRoute = target, startDestination = NavRoute.Today.route, accent = accent, light = light, onDeepLinkConsumed = { intentState.value = null })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        root.refreshDate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState.value = intent
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}

@Composable
private fun IterMeumRoot(
    deepLinkRoute: String?,
    startDestination: String,
    accent: androidx.compose.ui.graphics.Color = com.itermeum.app.core.designsystem.ImColors.Ordinary,
    light: Boolean = false,
    onDeepLinkConsumed: () -> Unit,
) {
    IterMeumTheme(accent = accent, light = light) {
        val navController = rememberNavController()
        val showBottomBar = shouldShowBottomNav(navController)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.systemBars,
            bottomBar = { if (showBottomBar) IterMeumNavigationBar(navController) },
        ) { padding ->
            LaunchedEffect(deepLinkRoute) {
                if (deepLinkRoute != null) {
                    if (deepLinkRoute != NavRoute.Today.route) navController.navigate(deepLinkRoute) { launchSingleTop = true }
                    onDeepLinkConsumed()
                }
            }
            Box(Modifier.padding(padding)) {
                IterMeumNavHost(navController = navController, startDestination = startDestination)
            }
        }
    }
}

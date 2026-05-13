package com.avangard.app

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
import androidx.compose.ui.Modifier
import com.avangard.app.navigation.AvangardNavHost
import com.avangard.app.ui.theme.IsaColors
import com.avangard.app.ui.theme.MachineTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AvangardApp() }
    }
}

@Composable
private fun AvangardApp() {
    MachineTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = IsaColors.Graphite,
            contentWindowInsets = WindowInsets.systemBars,
        ) { padding ->
            Box(Modifier.padding(padding)) {
                AvangardNavHost()
            }
        }
    }
}

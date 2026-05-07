package com.avangard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.avangard.app.feature.dashboard.DashboardScreen
import com.avangard.app.ui.theme.MachineColors
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
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MachineColors.Background),
            color = MachineColors.Background,
        ) {
            DashboardScreen()
        }
    }
}

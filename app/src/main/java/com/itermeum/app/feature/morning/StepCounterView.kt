package com.itermeum.app.feature.morning

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.itermeum.app.R
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSpacing
import kotlinx.coroutines.delay

/** Section 5.1 fallback: ≥ [target] steps within [windowSeconds] (step sensors and the accelerometer, D-46). */
@Composable
fun StepCounterView(target: Int, windowSeconds: Int, onReached: () -> Unit) {
    val context = LocalContext.current
    val latestOnReached by rememberUpdatedState(onReached)
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    var granted by remember {
        mutableStateOf(!needsPermission || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }

    var steps by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(windowSeconds) }
    var expired by remember { mutableStateOf(false) }
    var restartKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(restartKey) {
        steps = 0; secondsLeft = windowSeconds; expired = false
        while (secondsLeft > 0) { delay(1_000); secondsLeft-- }
        expired = true
    }

    // D-46: three sources, the highest count wins. The step sensors need «Физическая активность»
    // and HyperOS batches the step counter by tens of seconds; the accelerometer needs no
    // permission and answers at once — so steps count even when the permission was refused.
    DisposableEffect(granted, restartKey) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        var counterBase = -1f
        var counterSteps = 0
        var detectorSteps = 0
        val accel = AccelStepDetector()
        var reached = false
        fun publish() {
            if (expired) return
            steps = maxOf(counterSteps, detectorSteps, accel.steps)
            if (steps >= target && !reached) { reached = true; latestOnReached() }
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_STEP_COUNTER -> {
                        if (counterBase < 0) counterBase = event.values[0]
                        counterSteps = (event.values[0] - counterBase).toInt()
                    }
                    Sensor.TYPE_STEP_DETECTOR -> detectorSteps++
                    Sensor.TYPE_ACCELEROMETER -> accel.onSample(event.values[0], event.values[1], event.values[2], event.timestamp)
                }
                publish()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (granted) {
            sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_FASTEST, 0) }
            sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_FASTEST, 0) }
        }
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(listener) }
    }

    ImPanel {
        Text(stringResource(R.string.proof_steps_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(ImSpacing.grid))
        Text(stringResource(R.string.proof_steps_progress, steps.coerceAtMost(target), target, secondsLeft), style = MaterialTheme.typography.bodyLarge)
        LinearProgressIndicator(progress = { (steps.toFloat() / target).coerceIn(0f, 1f) })
        if (!granted) {
            ImSecondaryButton(stringResource(R.string.grant), onClick = { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) })
        }
        if (expired) {
            Text(stringResource(R.string.proof_steps_expired), color = MaterialTheme.colorScheme.error)
            ImSecondaryButton(stringResource(R.string.retry), onClick = { restartKey++ })
        }
    }
}

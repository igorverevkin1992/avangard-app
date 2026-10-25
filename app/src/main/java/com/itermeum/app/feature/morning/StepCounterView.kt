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
import androidx.compose.material3.Button
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
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSpacing
import kotlinx.coroutines.delay

/** Section 5.1 fallback: TYPE_STEP_COUNTER delta ≥ [target] within [windowSeconds]. */
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

    DisposableEffect(granted, restartKey) {
        if (!granted) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        var baseline = -1f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (expired) return
                if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                    if (baseline < 0) baseline = event.values[0]
                    steps = (event.values[0] - baseline).toInt()
                } else {
                    steps += 1
                }
                if (steps >= target) latestOnReached()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    ImPanel {
        Text(stringResource(R.string.proof_steps_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(ImSpacing.grid))
        Text(stringResource(R.string.proof_steps_progress, steps.coerceAtMost(target), target, secondsLeft), style = MaterialTheme.typography.bodyLarge)
        LinearProgressIndicator(progress = { (steps.toFloat() / target).coerceIn(0f, 1f) })
        if (!granted) {
            Button(onClick = { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) { Text(stringResource(R.string.grant)) }
        }
        if (expired) {
            Text(stringResource(R.string.proof_steps_expired), color = MaterialTheme.colorScheme.error)
            Button(onClick = { restartKey++ }) { Text(stringResource(R.string.retry)) }
        }
    }
}

package com.itermeum.app.sync.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.itermeum.app.MainActivity
import com.itermeum.app.core.database.entity.MorningStage
import com.itermeum.app.core.designsystem.IterMeumTheme
import com.itermeum.app.feature.morning.MorningFlow
import com.itermeum.app.feature.morning.MorningViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Section 5.1. Shown over the lock screen, no snooze and no dismiss. Hosts
 * the ring → proof → heroic-minute flow; when the user leaves while the
 * alarm is still ringing, the service re-posts the full-screen intent.
 */
@AndroidEntryPoint
class AlarmRingActivity : ComponentActivity() {

    private val viewModel: MorningViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockscreen()
        val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: LocalDate.now()
        val test = intent.getBooleanExtra(EXTRA_TEST, false)
        viewModel.bind(date, test)
        onBackPressedDispatcher.addCallback(this) { /* no snooze, no back */ }
        setContent {
            IterMeumTheme {
                val state by viewModel.ui.collectAsState()
                MorningFlow(
                    state = state,
                    onProofStart = viewModel::startProof,
                    onQrScanned = viewModel::onQrScanned,
                    onStepsProven = viewModel::onStepsProven,
                    onReverseCode = viewModel::onReverseCode,
                    onSwitchToSteps = viewModel::switchToSteps,
                    onServiam = {
                        viewModel.onServiam()
                        // Only now ask for the PIN: Today is not shown over the lock screen.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)
                        }
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(MainActivity.EXTRA_ROUTE, "today")
                        })
                        finish()
                    },
                    onTestDismiss = { viewModel.stopTest(); finish() },
                    onEmergencyStop = { viewModel.forceStop(); finish() },
                )
            }
        }
    }

    private fun showOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        super.onStop()
        // Section 5.1 acceptance: back/home → sound continues, activity returns ≤3 s.
        val stage = viewModel.ui.value.stage
        if (!isFinishing && (stage == MorningStage.RINGING || stage == MorningStage.PROOF_PENDING)) {
            lifecycleScope.launch {
                delay(3_000)
                if (!isFinishing && viewModel.ui.value.stage in setOf(MorningStage.RINGING, MorningStage.PROOF_PENDING)) {
                    AlarmService.repost(this@AlarmRingActivity)
                }
            }
        }
    }

    companion object {
        const val EXTRA_DATE = "date"
        const val EXTRA_TEST = "test"
        fun intent(context: Context, date: LocalDate, test: Boolean = false): Intent =
            Intent(context, AlarmRingActivity::class.java)
                .putExtra(EXTRA_DATE, date.toString()).putExtra(EXTRA_TEST, test)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

package com.itermeum.app.feature.morning

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.data.MorningRepository
import com.itermeum.app.core.data.ProfileRepository
import com.itermeum.app.core.database.entity.MorningStage
import com.itermeum.app.core.engine.StreakEngine
import com.itermeum.app.sync.alarm.AlarmService
import com.itermeum.app.sync.alarm.WakeAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MorningUi(
    val date: LocalDate = LocalDate.now(),
    val test: Boolean = false,
    /** D-47: the manual stop is shown while it is on in Settings. */
    val emergencyStop: Boolean = false,
    val stage: MorningStage = MorningStage.RINGING,
    val proofMethod: ProofMethod = ProofMethod.QR,
    val expectedQrUuid: String? = null,
    val qrError: Boolean = false,
    val reverseCode: String? = null,
    val stepsDone: Boolean = false,
    val verdict: StreakEngine.WakeVerdict? = null,
    val morningOffering: String = "",
    /** Proof attempted before the alarm time (does not count). */
    val tooEarly: Boolean = false,
)

enum class ProofMethod { QR, STEPS }

@HiltViewModel
class MorningViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val morning: MorningRepository,
    private val profiles: ProfileRepository,
    private val wakeScheduler: WakeAlarmScheduler,
    private val flags: com.itermeum.app.core.data.DayFlags,
    private val clock: Clock,
) : ViewModel() {

    private val _ui = MutableStateFlow(MorningUi())
    val ui: StateFlow<MorningUi> = _ui.asStateFlow()

    /** Set synchronously on the first valid proof: the scanner and step sensor fire per frame/event. */
    private var proving = false

    fun bind(date: LocalDate, test: Boolean) {
        viewModelScope.launch {
            val profile = profiles.get()
            val existing = morning.get(date)
            val method = if (profile.wakeProofMethod == "STEPS") ProofMethod.STEPS else ProofMethod.QR
            _ui.update {
                it.copy(
                    date = date, test = test, emergencyStop = com.itermeum.app.sync.alarm.DirectBootStore(context).emergencyStop, expectedQrUuid = profile.wakeQrUuid, proofMethod = method,
                    stage = when {
                        test -> MorningStage.RINGING
                        existing == null -> MorningStage.RINGING
                        existing.stage == MorningStage.SCHEDULED -> MorningStage.RINGING
                        else -> existing.stage
                    },
                )
            }
        }
    }

    fun startProof() {
        val s = _ui.value
        if (s.test) return
        viewModelScope.launch { morning.markProofPending(s.date) }
        _ui.update { it.copy(stage = MorningStage.PROOF_PENDING, qrError = false) }
    }

    fun switchToSteps() = _ui.update { it.copy(proofMethod = ProofMethod.STEPS) }

    /** QR payload must be `itermeum://wake/<uuid>` with the profile's uuid. */
    fun onQrScanned(payload: String) {
        val s = _ui.value
        if (s.stage != MorningStage.PROOF_PENDING) return
        val expected = s.expectedQrUuid
        val ok = payload.startsWith(QR_PREFIX) && expected != null && payload.removePrefix(QR_PREFIX).trim() == expected
        if (ok) proven("QR") else _ui.update { it.copy(qrError = true) }
    }

    /** Steps done (≥20 within 2 min) → show the reverse code challenge. */
    fun onStepsProven() {
        if (_ui.value.stepsDone) return
        val code = (100_000 + kotlin.random.Random(clock.nowEpochMillis()).nextInt(900_000)).toString()
        _ui.update { it.copy(stepsDone = true, reverseCode = code) }
    }

    fun onReverseCode(entered: String): Boolean {
        val code = _ui.value.reverseCode ?: return false
        val ok = entered.trim() == code.reversed()
        if (ok) proven("STEPS")
        return ok
    }

    private fun proven(method: String) {
        if (proving) return
        proving = true
        val s = _ui.value
        viewModelScope.launch {
            val verdict = morning.markProven(s.date, method)
            if (verdict == null) {
                proving = false
                _ui.update { it.copy(tooEarly = true, qrError = false, stepsDone = false, reverseCode = null) }
                return@launch
            }
            AlarmService.stop(context)
            wakeScheduler.cancelMissed()
            wakeScheduler.scheduleServiamNudge(clock.nowEpochMillis())
            _ui.update { it.copy(stage = MorningStage.PROVEN, verdict = verdict, qrError = false, tooEarly = false) }
        }
    }

    fun onServiam() {
        val s = _ui.value
        viewModelScope.launch {
            morning.markHeroicDone(s.date)
            morning.get(s.date)?.provenAt?.let { wakeScheduler.scheduleQuarantineCheck(s.date, it) }
            wakeScheduler.scheduleNext()
        }
    }

    /** D-47: stops the ring without proof; the wake is recorded as missed, tomorrow's alarm stays. */
    fun forceStop() {
        AlarmService.stop(context)
        wakeScheduler.cancelMissed()
        val date = _ui.value.date
        viewModelScope.launch {
            runCatching { morning.markMissed(date) }
            runCatching { wakeScheduler.scheduleNext() }
        }
    }

    fun stopTest() { flags.markTestAlarmHeard(); AlarmService.stop(context) }

    companion object {
        const val QR_PREFIX = "itermeum://wake/"
    }
}

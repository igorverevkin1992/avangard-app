package com.itermeum.app.sync.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.itermeum.app.R
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.data.MorningRepository
import com.itermeum.app.core.data.ProfileRepository
import com.itermeum.app.core.database.entity.MorningStage
import com.itermeum.app.sync.isUserUnlocked
import com.itermeum.app.sync.notifications.NotificationChannels
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Section 5.1: USAGE_ALARM sound ramping 0→100 % over 60 s with vibration;
 * after 10 minutes a 2-minute pause, then again; stops on proof or at
 * +60 min (MISSED). Runs as a foreground service with a full-screen
 * notification that opens [AlarmRingActivity] over the lock screen.
 */
@AndroidEntryPoint
class AlarmService : Service() {

    @Inject lateinit var morning: MorningRepository
    @Inject lateinit var profiles: ProfileRepository
    @Inject lateinit var wakeScheduler: WakeAlarmScheduler
    @Inject lateinit var clock: Clock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var player: MediaPlayer? = null
    private var soundJob: Job? = null
    private var date: LocalDate? = null
    private var test = false
    private var locked = false
    private var unlockReceiver: BroadcastReceiver? = null
    private var savedAlarmVolume: Int? = null
    private var focusRequest: AudioFocusRequest? = null
    private val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopRinging(); return START_NOT_STICKY }
            ACTION_REPOST -> { date?.let { postFullScreen(it, test); bringRingScreenBack(it) }; return START_STICKY }
        }
        if (intent == null) {
            // Sticky restart after HyperOS killed the process mid-ring: resume if the morning is still open.
            resumeAfterRestart()
            return START_STICKY
        }
        val d = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: return START_NOT_STICKY
        test = intent.getBooleanExtra(EXTRA_TEST, false)
        date = d
        locked = !isUserUnlocked()
        postFullScreen(d, test)
        if (locked) {
            // Before the first unlock Room is unreadable: ring from the device-protected copy,
            // record the morning once the user unlocks.
            val store = DirectBootStore(this)
            startSound(store.sound(), store.volume())
            awaitUnlock(d)
        } else {
            scope.launch {
                if (!test) recordRinging(d)
                val profile = profiles.get()
                startSound(profile.alarmSound, profile.alarmVolume)
            }
        }
        return START_STICKY
    }

    private suspend fun recordRinging(d: LocalDate) {
        morning.markRinging(d)
        val state = morning.get(d)
        wakeScheduler.scheduleMissed(d, state?.alarmAt ?: clock.nowEpochMillis())
    }

    private fun awaitUnlock(d: LocalDate) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                locked = false
                runCatching { unregisterReceiver(this) }
                unlockReceiver = null
                postFullScreen(d, test)
                bringRingScreenBack(d)
                scope.launch { runCatching { if (!test) recordRinging(d) } }
            }
        }
        unlockReceiver = receiver
        ContextCompat.registerReceiver(this, receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun resumeAfterRestart() {
        if (!isUserUnlocked()) { stopSelf(); return }
        scope.launch {
            val d = clock.logicalToday()
            val state = runCatching { morning.get(d) }.getOrNull()
            if (state == null || state.stage !in setOf(MorningStage.RINGING, MorningStage.PROOF_PENDING)) {
                stopSelf(); return@launch
            }
            date = d
            val ok = runCatching { postFullScreen(d, false) }.isSuccess
            if (!ok) { stopSelf(); return@launch }
            val profile = profiles.get()
            startSound(profile.alarmSound, profile.alarmVolume)
        }
    }

    /** Back/Home while ringing: the ring screen returns (section 5.1); within the BAL grace window this works directly. */
    private fun bringRingScreenBack(d: LocalDate) {
        if (locked) return
        runCatching { startActivity(AlarmRingActivity.intent(this, d, test)) }
    }

    private fun postFullScreen(d: LocalDate, test: Boolean) {
        val n = buildNotification(d, test)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun buildNotification(d: LocalDate, test: Boolean): Notification {
        val fullScreen = PendingIntent.getActivity(
            this, 0, AlarmRingActivity.intent(this, d, test),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationChannels.ALARM_WAKE)
            .setSmallIcon(R.drawable.ic_stat_itermeum)
            .setContentTitle(getString(R.string.alarm_notification_title))
            .setContentText(getString(if (locked) R.string.alarm_notification_locked else R.string.alarm_notification_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSound(null)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .build()
    }

    private fun startSound(soundIndex: Int, maxVolumePercent: Int) {
        soundJob?.cancel()
        soundJob = scope.launch {
            val res = when (soundIndex) { 2 -> R.raw.bell_2; 3 -> R.raw.bell_3; else -> R.raw.bell_1 }
            raiseAlarmStream()
            val mp = MediaPlayer().apply {
                setAudioAttributes(ALARM_ATTRS)
                // Keeps the CPU awake through the 10-minute cycles with the screen off.
                setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                val afd = resources.openRawResourceFd(res)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                setVolume(0f, 0f)
                prepare()
                start()
            }
            player = mp
            val max = maxVolumePercent.coerceIn(10, 100) / 100f
            var cycle = 0
            while (true) {
                // Ramp 0 → max over 60 s, then hold until the 10-minute mark.
                val rampStart = System.currentTimeMillis()
                vibrate(true)
                while (System.currentTimeMillis() - rampStart < 10 * 60_000L) {
                    val t = ((System.currentTimeMillis() - rampStart) / 60_000f).coerceIn(0f, 1f)
                    val v = max * t
                    runCatching { mp.setVolume(v, v) }
                    delay(1_000)
                }
                // 2-minute pause, then the next cycle.
                runCatching { mp.setVolume(0f, 0f) }
                vibrate(false)
                delay(2 * 60_000L)
                cycle++
            }
        }
    }

    private fun vibrate(on: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 1400), 0)
        if (!on) { vibrator.cancel(); return }
        // Alarm usage so Do Not Disturb does not swallow the vibration.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(effect, ALARM_ATTRS)
        }
    }

    /**
     * The player's volume is relative to the alarm stream; if the user left that
     * slider low the ramp would end near silence. Raise it to ≥80 % for the ring,
     * take exclusive focus (music pauses) and restore both afterwards.
     */
    private fun raiseAlarmStream() {
        runCatching {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            val floor = (max * 0.8f).toInt().coerceAtLeast(1)
            if (savedAlarmVolume == null) savedAlarmVolume = current
            if (current < floor) audio.setStreamVolume(AudioManager.STREAM_ALARM, floor, 0)
        }
        runCatching {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).setAudioAttributes(ALARM_ATTRS).build()
            audio.requestAudioFocus(req)
            focusRequest = req
        }
    }

    private fun restoreAudio() {
        savedAlarmVolume?.let { v -> runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) } }
        savedAlarmVolume = null
        focusRequest?.let { r -> runCatching { audio.abandonAudioFocusRequest(r) } }
        focusRequest = null
    }

    private fun stopRinging() {
        soundJob?.cancel()
        soundJob = null
        runCatching { player?.stop(); player?.release() }
        player = null
        vibrate(false)
        restoreAudio()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { player?.release() }
        vibrate(false)
        restoreAudio()
        unlockReceiver?.let { runCatching { unregisterReceiver(it) } }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 3001
        const val ACTION_RING = "com.itermeum.app.alarm.RING"
        const val ACTION_STOP = "com.itermeum.app.alarm.STOP"
        const val ACTION_REPOST = "com.itermeum.app.alarm.REPOST"
        const val EXTRA_DATE = "date"
        const val EXTRA_TEST = "test"
        private val ALARM_ATTRS: AudioAttributes =
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

        fun start(context: Context, date: LocalDate, test: Boolean) {
            val i = Intent(context, AlarmService::class.java).setAction(ACTION_RING)
                .putExtra(EXTRA_DATE, date.toString()).putExtra(EXTRA_TEST, test)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP))
        }

        fun repost(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_REPOST))
        }
    }
}

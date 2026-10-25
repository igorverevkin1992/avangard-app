package com.itermeum.app.sync.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.itermeum.app.R
import com.itermeum.app.core.data.MorningRepository
import com.itermeum.app.core.data.ProfileRepository
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var player: MediaPlayer? = null
    private var soundJob: Job? = null
    private var date: LocalDate? = null
    private var test = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopRinging(); return START_NOT_STICKY }
            ACTION_REPOST -> { date?.let { postFullScreen(it, test) }; return START_STICKY }
        }
        val d = intent?.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: return START_NOT_STICKY
        test = intent.getBooleanExtra(EXTRA_TEST, false)
        date = d
        postFullScreen(d, test)
        scope.launch {
            if (!test) {
                morning.markRinging(d)
                val state = morning.get(d)
                wakeScheduler.scheduleMissed(d, state?.alarmAt ?: System.currentTimeMillis())
            }
            val profile = profiles.get()
            startSound(profile.alarmSound, profile.alarmVolume)
        }
        return START_STICKY
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
            .setContentText(getString(R.string.alarm_notification_text))
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
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
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
        if (on) vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 1400), 0))
        else vibrator.cancel()
    }

    private fun stopRinging() {
        soundJob?.cancel()
        soundJob = null
        runCatching { player?.stop(); player?.release() }
        player = null
        vibrate(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { player?.release() }
        vibrate(false)
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

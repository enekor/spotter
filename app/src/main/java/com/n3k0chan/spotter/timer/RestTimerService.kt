package com.n3k0chan.spotter.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.n3k0chan.spotter.MainActivity
import com.n3k0chan.spotter.R
import com.n3k0chan.spotter.glyph.GlyphController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RestTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null

    private lateinit var glyphController: GlyphController
    private var glyphReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        glyphController = GlyphController(applicationContext)
        glyphController.init { glyphReady = true }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val total = intent.getIntExtra(EXTRA_SECONDS, 0).coerceAtLeast(0)
                val preWarn = intent.getBooleanExtra(EXTRA_PRE_WARN, true)
                val vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true)
                start(total, preWarn, vibrate)
            }
            ACTION_ADD_15 -> add(15)
            ACTION_SKIP -> stopAndClear(silent = true)
            ACTION_STOP -> stopAndClear(silent = true)
        }
        return START_STICKY
    }

    private fun start(total: Int, preWarn: Boolean, vibrate: Boolean) {
        if (total <= 0) {
            stopAndClear(silent = true)
            return
        }
        tickerJob?.cancel()
        RestTimerController.update {
            RestTimerController.State(
                isRunning = true,
                totalSeconds = total,
                remainingSeconds = total,
                finishedAt = null,
            )
        }
        startForegroundCompat(buildNotification(total, total, finished = false))

        updateGlyphProgress(total.toFloat() / total)

        tickerJob = scope.launch {
            var remaining = total
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
                RestTimerController.update { it.copy(remainingSeconds = remaining) }
                updateGlyphProgress(remaining.toFloat() / total)
                if (preWarn && remaining == 10) buzz(short = true)
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIF_ID, buildNotification(total, remaining, finished = false))
            }
            if (vibrate) buzz(short = false)
            glyphTurnOff()
            RestTimerController.update {
                it.copy(
                    isRunning = false,
                    remainingSeconds = 0,
                    finishedAt = System.currentTimeMillis(),
                )
            }
            val mgr = getSystemService(NotificationManager::class.java)
            // Quita la notificación de cuenta atrás y postea la de "terminado"
            // en otro id/canal — así Android dispara el sonido del canal DONE.
            mgr.cancel(NOTIF_ID)
            mgr.notify(NOTIF_DONE_ID, buildNotification(total, 0, finished = true))
            stopForegroundOnly()
        }
    }

    private fun add(seconds: Int) {
        val s = RestTimerController.state.value
        if (!s.isRunning) return
        RestTimerController.update {
            it.copy(
                totalSeconds = it.totalSeconds + seconds,
                remainingSeconds = it.remainingSeconds + seconds,
            )
        }
    }

    private fun stopAndClear(silent: Boolean) {
        tickerJob?.cancel()
        tickerJob = null
        glyphTurnOff()
        RestTimerController.update { RestTimerController.State() }
        stopForegroundCompat(removeNotification = true)
        stopSelf()
    }

    private fun stopForegroundOnly() {
        // Mantén la notificación informativa "descanso terminado" pero deja de ser foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        stopSelf()
    }

    private fun stopForegroundCompat(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buzz(short: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val effect = if (short) {
            VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 400), -1)
        }
        vibrator.vibrate(effect)
    }

    private fun updateGlyphProgress(progress: Float) {
        if (!glyphReady) return
        try {
            val glyphProgress = (progress * 100).toInt().coerceIn(0, 100)
            glyphController.showProgress(glyphProgress, reverse = true)
        } catch (_: Exception) {
        }
    }

    private fun glyphTurnOff() {
        if (!glyphReady) return
        try {
            glyphController.turnOff()
        } catch (_: Exception) {
        }
    }

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        // Canal silencioso para la cuenta atrás (foreground)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val progress = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.timer_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.timer_channel_desc)
                enableVibration(false)
                setShowBadge(false)
                setSound(null, null)
            }
            mgr.createNotificationChannel(progress)
        }
        // Canal con sonido + vibración para el aviso de fin de descanso
        if (mgr.getNotificationChannel(CHANNEL_DONE_ID) == null) {
            val attrs = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()
            val sound = android.media.RingtoneManager
                .getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val done = NotificationChannel(
                CHANNEL_DONE_ID,
                getString(R.string.timer_channel_done_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.timer_channel_done_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200, 100, 400)
                setShowBadge(false)
                setSound(sound, attrs)
            }
            mgr.createNotificationChannel(done)
        }
    }

    private fun buildNotification(total: Int, remaining: Int, finished: Boolean): Notification {
        val openAppPi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val skipPi = PendingIntent.getService(
            this, 1, Intent(this, RestTimerService::class.java).setAction(ACTION_SKIP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val add15Pi = PendingIntent.getService(
            this, 2, Intent(this, RestTimerService::class.java).setAction(ACTION_ADD_15),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (finished) getString(R.string.timer_notification_finished)
                    else getString(R.string.timer_notification_title)
        val text = if (finished) "" else "${formatMmSs(remaining)} / ${formatMmSs(total)}"

        val channel = if (finished) CHANNEL_DONE_ID else CHANNEL_ID
        val builder = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppPi)
            .setOnlyAlertOnce(!finished)
            .setOngoing(!finished)
            .setShowWhen(false)
            .setPriority(if (finished) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (!finished) {
            builder.setProgress(total, total - remaining, false)
            builder.addAction(0, getString(R.string.timer_action_add_15), add15Pi)
            builder.addAction(0, getString(R.string.timer_action_skip), skipPi)
        }
        return builder.build()
    }

    private fun formatMmSs(s: Int): String {
        val m = s / 60
        val sec = s % 60
        return "%d:%02d".format(m, sec)
    }

    override fun onDestroy() {
        glyphController.release()
        glyphReady = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "rest_timer"
        const val CHANNEL_DONE_ID = "rest_timer_done"
        const val NOTIF_ID = 4242
        const val NOTIF_DONE_ID = 4243

        const val ACTION_START = "com.n3k0chan.spotter.timer.START"
        const val ACTION_SKIP = "com.n3k0chan.spotter.timer.SKIP"
        const val ACTION_ADD_15 = "com.n3k0chan.spotter.timer.ADD_15"
        const val ACTION_STOP = "com.n3k0chan.spotter.timer.STOP"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_PRE_WARN = "pre_warn"
        const val EXTRA_VIBRATE = "vibrate"

        fun start(context: Context, seconds: Int, preWarn: Boolean, vibrate: Boolean) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SECONDS, seconds)
                putExtra(EXTRA_PRE_WARN, preWarn)
                putExtra(EXTRA_VIBRATE, vibrate)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun skip(context: Context) {
            context.startService(
                Intent(context, RestTimerService::class.java).setAction(ACTION_SKIP),
            )
        }

        fun add15(context: Context) {
            context.startService(
                Intent(context, RestTimerService::class.java).setAction(ACTION_ADD_15),
            )
        }
    }
}

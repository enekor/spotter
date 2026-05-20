package com.n3k0chan.spotter.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.n3k0chan.spotter.R
import com.n3k0chan.spotter.di.ServiceLocator
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        ServiceLocator.init(context.applicationContext)
        val settings = ServiceLocator.settings.state.value

        when (intent?.action) {
            "WORKOUT_REMINDER" -> {
                val dayName = intent.getIntExtra("day", 1).let {
                    DayOfWeek.of(it).getDisplayName(
                        java.time.format.TextStyle.FULL,
                        java.util.Locale("es"),
                    ).replaceFirstChar { c -> c.uppercase() }
                }
                showNotification(
                    context,
                    title = "¡Hora de entrenar!",
                    body = "Hoy es $dayName — tu día de gimnasio 💪",
                )
            }
            "WORKOUT_CATCHUP" -> {
                val trainedThisWeek = runBlocking { checkTrainedThisWeek(context, settings) }
                if (!trainedThisWeek) {
                    showNotification(
                        context,
                        title = "¿Sesión de recuperación?",
                        body = "No has entrenado esta semana. ¡Un buen momento para ir!",
                    )
                }
            }
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.MY_PACKAGE_REPLACED" -> {
                // no-op: just reschedule below
            }
        }

        ReminderScheduler.reschedule(context.applicationContext, settings)
    }

    private suspend fun checkTrainedThisWeek(context: Context, settings: com.n3k0chan.spotter.data.prefs.AppSettings): Boolean {
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val workouts = ServiceLocator.workouts.getWorkoutsInRange(monday, now)
        val weekdayWorkouts = settings.reminderDays.count { dayVal ->
            val targetDate = LocalDate.now().with(DayOfWeek.of(dayVal))
            targetDate.isBefore(LocalDate.now()) || targetDate.isEqual(LocalDate.now())
        }
        return workouts.isNotEmpty()
    }

    private fun showNotification(context: Context, title: String, body: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recordatorios de entreno",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notificaciones para recordar los días de gimnasio"
            }
            nm.createNotificationChannel(channel)
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = launchIntent?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "workout_reminders"
        private const val NOTIFICATION_ID = 8001
    }
}

package com.n3k0chan.spotter.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.n3k0chan.spotter.data.prefs.AppSettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object ReminderScheduler {

    private const val REQUEST_CODE_BASE = 7000
    private const val REQUEST_CODE_CATCHUP = 7100

    fun reschedule(context: Context, settings: AppSettings) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAll(context, am)
        if (settings.reminderDays.isEmpty()) return

        val zone = ZoneId.systemDefault()
        val time = LocalTime.of(settings.reminderHour, settings.reminderMinute)
        val today = LocalDate.now()

        for (dayValue in settings.reminderDays) {
            val dow = DayOfWeek.of(dayValue)
            var next = today.with(TemporalAdjusters.nextOrSame(dow))
            val triggerAt = next.atTime(time).atZone(zone).toInstant()
            if (triggerAt.toEpochMilli() <= System.currentTimeMillis()) {
                next = today.with(TemporalAdjusters.next(dow))
            }
            val millis = next.atTime(time).atZone(zone).toInstant().toEpochMilli()

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = "WORKOUT_REMINDER"
                putExtra("day", dayValue)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_BASE + dayValue,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }

        scheduleCatchUp(context, am, settings, zone, time)
    }

    private fun scheduleCatchUp(
        context: Context,
        am: AlarmManager,
        settings: AppSettings,
        zone: ZoneId,
        time: LocalTime,
    ) {
        val hasSaturday = 6 in settings.reminderDays
        val hasSunday = 7 in settings.reminderDays

        val catchUpDay = when {
            !hasSaturday && !hasSunday -> DayOfWeek.SATURDAY
            hasSaturday && !hasSunday -> DayOfWeek.SUNDAY
            else -> return
        }

        val today = LocalDate.now()
        var next = today.with(TemporalAdjusters.nextOrSame(catchUpDay))
        val triggerAt = next.atTime(time).atZone(zone).toInstant()
        if (triggerAt.toEpochMilli() <= System.currentTimeMillis()) {
            next = today.with(TemporalAdjusters.next(catchUpDay))
        }
        val millis = next.atTime(time).atZone(zone).toInstant().toEpochMilli()

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "WORKOUT_CATCHUP"
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_CATCHUP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    private fun cancelAll(context: Context, am: AlarmManager) {
        for (day in 1..7) {
            val intent = Intent(context, ReminderReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_BASE + day,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            pi?.let { am.cancel(it); it.cancel() }
        }
        val catchupIntent = Intent(context, ReminderReceiver::class.java)
        val catchupPi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_CATCHUP,
            catchupIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        catchupPi?.let { am.cancel(it); it.cancel() }
    }
}

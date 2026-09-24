package com.dastyar.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dastyar.app.data.DastyarDatabase
import com.dastyar.app.data.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The daily check-in reminder. Schedule, cancel and query it; the actual
 * delivery is done by [DailyReminderReceiver]. Settings live in SharedPreferences
 * so they survive a reboot, and [BootReceiver] re-arms the alarm afterwards.
 */
object DailyReminder {

    const val PREFS = "dastyar_daily_reminder"
    const val KEY_ENABLED = "enabled"
    const val KEY_HOUR = "hour"
    const val KEY_MINUTE = "minute"

    const val DEFAULT_HOUR = 20
    const val DEFAULT_MINUTE = 0

    private const val REQUEST_CODE = 9101

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun hour(ctx: Context): Int = prefs(ctx).getInt(KEY_HOUR, DEFAULT_HOUR)
    fun minute(ctx: Context): Int = prefs(ctx).getInt(KEY_MINUTE, DEFAULT_MINUTE)

    fun timeLabel(ctx: Context): String =
        "%02d:%02d".format(hour(ctx), minute(ctx))

    /** Enables or disables the reminder and (re)schedules the alarm. */
    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) scheduleNext(ctx) else cancel(ctx)
    }

    fun setTime(ctx: Context, hour: Int, minute: Int) {
        prefs(ctx).edit()
            .putInt(KEY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_MINUTE, minute.coerceIn(0, 59))
            .apply()
        if (isEnabled(ctx)) scheduleNext(ctx)
    }

    /** Schedules the next occurrence at the configured time. */
    fun scheduleNext(ctx: Context) {
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        val trigger = nextTrigger(ctx)
        val pi = pendingIntent(ctx)
        try {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    fun reschedule(ctx: Context) {
        if (isEnabled(ctx)) scheduleNext(ctx)
    }

    fun cancel(ctx: Context) {
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        alarm.cancel(pendingIntent(ctx))
    }

    /** Shows a test notification immediately, so the user can verify it works. */
    fun showTestNow(ctx: Context) {
        NotificationHelper.show(
            ctx,
            NotificationHelper.ID_DAILY + 1,
            "دستیار من",
            "وقتشه یه سر به دستیار من بزنی 🌱",
            NotificationHelper.CHANNEL_DAILY,
            openCheckIn = true
        )
    }

    /** True when today's check-in row already exists. */
    fun hasCheckedInToday(ctx: Context): Boolean = try {
        kotlinx.coroutines.runBlocking {
            val dao = DastyarDatabase.get(ctx).dao()
            dao.checkIn(Dates.today()) != null
        }
    } catch (_: Exception) {
        false
    }

    private fun nextTrigger(ctx: Context): Long {
        val now = LocalDateTime.now()
        var target = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour(ctx), minute(ctx)))
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun pendingIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, DailyReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            ctx, REQUEST_CODE, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** Schedules real system alarms for task reminders. */
object ReminderScheduler {

    fun schedule(ctx: Context, task: com.dastyar.app.data.Task) {
        if (!task.reminderEnabled || task.date.isBlank() || task.time.isBlank()) return
        val trigger = triggerMillis(task.date, task.time) ?: return
        if (trigger <= System.currentTimeMillis()) return

        val alarm = ctx.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(ctx, task)
        try {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } catch (_: SecurityException) {
            alarm.set(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    fun cancel(ctx: Context, task: com.dastyar.app.data.Task) {
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        alarm.cancel(pendingIntent(ctx, task))
    }

    fun rescheduleAll(ctx: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = DastyarDatabase.get(ctx).dao()
            dao.pendingReminders().forEach { schedule(ctx, it) }
        }
    }

    private fun pendingIntent(ctx: Context, task: com.dastyar.app.data.Task): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).apply {
            putExtra("title", task.title)
            putExtra("body", task.description.ifBlank { "وقت یادآوری این کاره ✅" })
            putExtra("id", task.id.toInt())
        }
        return PendingIntent.getBroadcast(
            ctx, task.id.toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun triggerMillis(date: String, time: String): Long? = try {
        val d = LocalDate.parse(date)
        val t = LocalTime.parse(time)
        LocalDateTime.of(d, t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: Exception) {
        null
    }
}

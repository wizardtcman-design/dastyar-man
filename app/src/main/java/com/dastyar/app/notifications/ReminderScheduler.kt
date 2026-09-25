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

/**
 * The period countdown reminder. It schedules an offline alarm for each of the
 * 7, 3 and 1 day marks before the estimated next period, using the user's real
 * cycle data. Alarms survive a reboot via [BootReceiver] and are re-armed
 * whenever the cycle data changes, so no duplicate or stale notification is
 * left behind. When there is not enough cycle data it schedules nothing.
 */
object PeriodReminder {

    const val PREFS = "dastyar_period_reminder"
    const val KEY_ENABLED = "enabled"

    private const val BASE_REQUEST = 9200
    private const val HOUR = 9

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            NotificationHelper.createChannels(ctx)
            scheduleNext(ctx, null)
        } else {
            cancel(ctx)
        }
    }

    /** Stable per-offset id, so re-scheduling replaces rather than duplicates. */
    fun notificationId(daysBefore: Int): Int = 9002 + daysBefore

    /**
     * (Re)schedules all three countdown alarms from the user's cycle data. A
     * null profile reloads from the database. Cancels everything first so an old
     * estimate cannot fire after the dates changed.
     */
    fun scheduleNext(ctx: Context, profile: com.dastyar.app.data.Profile? = null) {
        cancel(ctx)
        if (!isEnabled(ctx)) return

        val p = profile ?: runCatching {
            kotlinx.coroutines.runBlocking { DastyarDatabase.get(ctx).dao().profile() }
        }.getOrNull() ?: return

        // Not enough cycle data: never create a guessed reminder.
        if (p.lastPeriodDate.isBlank()) return
        val len = if (p.cycleLength in 15..60) p.cycleLength else return
        val days = Dates.daysUntilNextPeriod(p.lastPeriodDate, len)
        if (days < 0) return

        val alarm = ctx.getSystemService(AlarmManager::class.java)
        for (offset in intArrayOf(7, 3, 1)) {
            val trigger = triggerFor(days, offset) ?: continue
            val pi = pendingIntent(ctx, offset)
            try {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } catch (_: SecurityException) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        }
    }

    fun reschedule(ctx: Context) = scheduleNext(ctx, null)

    fun cancel(ctx: Context) {
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        for (offset in intArrayOf(7, 3, 1)) alarm.cancel(pendingIntent(ctx, offset))
    }

    /** Shows a sample 7-day reminder immediately for testing. */
    fun showTestNow(ctx: Context) {
        NotificationHelper.show(
            ctx,
            notificationId(7),
            "یادآوری پریود",
            "حدود ۷ روز تا پریود بعدی باقی مانده 🌸",
            NotificationHelper.CHANNEL_PERIOD
        )
    }

    /**
     * The wall-clock moment for one countdown: 9 in the morning, [daysBefore]
     * days before the estimated period. Returns null when that moment is
     * already in the past.
     */
    private fun triggerFor(daysUntil: Int, daysBefore: Int): Long? {
        val fireIn = daysUntil - daysBefore
        if (fireIn < 0) return null
        val target = LocalDateTime.now()
            .toLocalDate()
            .plusDays(fireIn.toLong())
            .atTime(LocalTime.of(HOUR, 0))
        if (!target.isAfter(LocalDateTime.now())) return null
        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun pendingIntent(ctx: Context, daysBefore: Int): PendingIntent {
        val i = Intent(ctx, PeriodReminderReceiver::class.java)
            .putExtra("daysBefore", daysBefore)
        return PendingIntent.getBroadcast(
            ctx, BASE_REQUEST + daysBefore, i,
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

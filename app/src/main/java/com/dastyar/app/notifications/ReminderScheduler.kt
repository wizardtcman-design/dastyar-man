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
 * The cycle reminders.
 *
 * One switch controls a whole set of local alarms, each placed at a meaningful
 * point of the cycle, following what established period trackers do:
 *
 * - **PMS starting** — a few days before the next period, with a heads-up.
 * - **Period approaching** — the standard 7/3/2/1-day countdown (Google Health
 *   uses 2 days + day 1; we keep the earlier marks too).
 * - **Period due** — on the predicted first day.
 * - **Period late** — a couple of days after the prediction with no period
 *   logged, so the user is aware instead of confused.
 * - **Fertile window starting** — two days before the estimated window.
 * - **Ovulation day** — the estimated ovulation day, with care advice.
 * - **Fertile window ending** — when the estimated window closes.
 * - **Period ended** — a gentle "is your period over?" nudge one day after the
 *   learned end, so the user can close the period and sharpen the history.
 *
 * Everything is computed locally from the same learned [com.dastyar.app.data.Cycle]
 * the ring uses, so nothing is guessed and no network is needed. Alarms survive
 * a reboot via [BootReceiver] and are re-armed whenever the cycle data or the
 * period history changes, so no duplicate or stale notification is left behind.
 * When there is not enough cycle data it schedules nothing.
 */
object PeriodReminder {

    const val PREFS = "dastyar_period_reminder"
    const val KEY_ENABLED = "enabled"

    private const val BASE_REQUEST = 9200
    private const val HOUR = 9

    /**
     * One named point in the cycle that can produce a notification. The id is
     * stable per event, so re-scheduling always replaces the previous alarm for
     * that event instead of piling up duplicates.
     */
    enum class Event(val id: Int, val request: Int) {
        PMS(9010, 1),
        APPROACH_7(9011, 7),
        APPROACH_3(9012, 3),
        APPROACH_2(9013, 2),
        APPROACH_1(9014, 1),
        PERIOD_DUE(9015, 21),
        PERIOD_LATE(9016, 22),
        FERTILE_START(9017, 23),
        OVULATION(9018, 24),
        FERTILE_END(9019, 25),
        PERIOD_ENDED(9020, 26)
    }

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
     * (Re)schedules every cycle alarm from the user's learned cycle data. A
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

        // Learn the cycle from the real history, exactly like the ring: the
        // learned length beats the questionnaire number once there is history.
        val events = runCatching {
            kotlinx.coroutines.runBlocking { DastyarDatabase.get(ctx).dao().periodEvents() }
        }.getOrDefault(emptyList())
        val info = com.dastyar.app.data.Cycle.info(p, events) ?: return
        if (!info.hasCycle) return

        val length = info.length
        val periodDays = info.periodDays
        val today = com.dastyar.app.data.Dates.today()
        val rawDay = com.dastyar.app.data.Cycle.rawDay(info)

        val ovStart = com.dastyar.app.data.Health.ovulationStart(length)
        val ovEnd = ovStart + 3
        val pmsStart = (length - 4).coerceAtLeast(ovEnd + 1)

        // Every event is described as "the day-of-cycle to fire on" plus a flag
        // for whether that day has already passed. Firing is at 9 in the
        // morning local time; anything in the past is skipped.
        data class Slot(val event: Event, val day: Int, val forceToday: Boolean)

        val slots = mutableListOf<Slot>()
        // Countdown marks before the period. The "day 0" mark is PERIOD_DUE, so
        // APPROACH_1 is deliberately not scheduled again for the same day.
        slots += Slot(Event.APPROACH_7, length - 6, false)
        slots += Slot(Event.APPROACH_3, length - 2, false)
        slots += Slot(Event.APPROACH_2, length - 1, false)
        // PMS starts a few days out.
        slots += Slot(Event.PMS, pmsStart, false)
        // Fertile window edges and ovulation.
        slots += Slot(Event.FERTILE_START, (ovStart - 2).coerceAtLeast(1), false)
        slots += Slot(Event.OVULATION, (ovStart + 1).coerceAtMost(length), false)
        slots += Slot(Event.FERTILE_END, ovEnd.coerceAtMost(length), false)
        // The day the period is due, and a "late" nudge two days later.
        slots += Slot(Event.PERIOD_DUE, length, true)
        slots += Slot(Event.PERIOD_LATE, length + 2, true)
        // Ask after the learned bleeding length whether the period is over.
        slots += Slot(Event.PERIOD_ENDED, periodDays + 1, false)

        val alarm = ctx.getSystemService(AlarmManager::class.java)
        for (slot in slots) {
            // Compare on the absolute cycle calendar, not the wrapped day, so
            // "the period is due" and "it is late" are not confused with an
            // earlier cycle.
            val targetIso = com.dastyar.app.data.Dates.plusDays(info.startIso, slot.day - 1)
            val daysFromToday = com.dastyar.app.data.Jalali.daysBetween(today, targetIso)
            // Fire today only for the due/late nudges; otherwise skip anything
            // already in the past so a late schedule never spams the user.
            val fireIn = when {
                daysFromToday > 0 -> daysFromToday
                daysFromToday == 0 && (slot.forceToday || slot.day > rawDay) -> 0
                else -> continue
            }
            val trigger = triggerIn(ctx, fireIn) ?: continue
            val pi = pendingIntent(ctx, slot.event)
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
        for (event in Event.entries) alarm.cancel(pendingIntent(ctx, event))
    }

    /** Shows a sample reminder immediately for testing. */
    fun showTestNow(ctx: Context) {
        NotificationHelper.show(
            ctx,
            Event.OVULATION.id,
            "یادآوری چرخه",
            "امروز حدود روز تخمک‌گذاری تو است؛ این بازه معمولاً احتمال باروری بیشتری دارد 🌸",
            NotificationHelper.CHANNEL_PERIOD
        )
    }

    /**
     * The wall-clock moment [daysFromToday] days from now, at 9 in the morning.
     * Returns null when the resulting moment is already in the past.
     */
    private fun triggerIn(ctx: Context, daysFromToday: Int): Long? {
        val target = LocalDateTime.now()
            .toLocalDate()
            .plusDays(daysFromToday.toLong())
            .atTime(LocalTime.of(HOUR, 0))
        if (!target.isAfter(LocalDateTime.now())) {
            // A same-day alarm set after 9:00 should still fire, just very soon.
            if (daysFromToday == 0) {
                return System.currentTimeMillis() + 5_000L
            }
            return null
        }
        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun pendingIntent(ctx: Context, event: Event): PendingIntent {
        val i = Intent(ctx, PeriodReminderReceiver::class.java)
            .putExtra("event", event.name)
            .putExtra("daysBefore", event.request)
        return PendingIntent.getBroadcast(
            ctx, BASE_REQUEST + event.request, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * Schedules real system alarms for task reminders.
 *
 * Every task has one stable alarm and one stable notification id derived from
 * its row id, so re-scheduling always replaces the previous alarm instead of
 * piling up duplicates, and cancelling always hits the right one. A repeating
 * task is re-armed by [ReminderReceiver] for its next occurrence the moment it
 * fires, so the chain survives with the app closed.
 *
 * The alarms use `setExactAndAllowWhileIdle` so they fire with the app closed,
 * in the background, with the screen off and the phone locked. Where Android
 * refuses exact alarms (the user revoked the special permission), it falls back
 * to an inexact `setAndAllowWhileIdle`, which still fires but may be a few
 * minutes late.
 */
object ReminderScheduler {

    /** True when this Android version lets the app schedule exact alarms. */
    fun canScheduleExact(ctx: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return true
        return ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun schedule(ctx: Context, task: com.dastyar.app.data.Task) {
        if (!task.reminderEnabled || task.done) return
        if (task.date.isBlank()) return
        scheduleAt(ctx, task, nextTrigger(task) ?: return)
    }

    /** Fires at [trigger]; used both for the first time and for repeats. */
    fun scheduleAt(ctx: Context, task: com.dastyar.app.data.Task, trigger: Long) {
        if (trigger <= System.currentTimeMillis()) return
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(ctx, task)
        try {
            if (canScheduleExact(ctx)) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (_: SecurityException) {
            // Exact permission was revoked between the check and the call: keep
            // the reminder alive the inexact way rather than dropping it.
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

    /**
     * The next moment a task should fire. A repeating task that already passed
     * today rolls forward to its next occurrence instead of being dropped, so a
     * daily reminder created this morning still fires tonight.
     */
    fun nextTrigger(task: com.dastyar.app.data.Task): Long? {
        val base = triggerMillis(task.date, task.time) ?: return null
        val now = System.currentTimeMillis()
        if (base > now) return base
        return rollForward(task, base, now)
    }

    /**
     * Advances a repeating task past [now], keeping its original time of day.
     * Unknown/`none` repeats return null because a one-off in the past should
     * not fire late.
     */
    fun rollForward(task: com.dastyar.app.data.Task, from: Long, now: Long): Long? {
        val stepDays = when (task.repeat) {
            "daily" -> 1L
            "weekly" -> 7L
            "monthly" -> 30L
            else -> return null
        }
        val zone = ZoneId.systemDefault()
        var d = java.time.Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val time = java.time.Instant.ofEpochMilli(from).atZone(zone).toLocalTime()
        var guard = 0
        while (guard < 500) {
            d = d.plusDays(stepDays)
            val candidate = d.atTime(time).atZone(zone).toInstant().toEpochMilli()
            if (candidate > now) return candidate
            guard++
        }
        return null
    }

    private fun pendingIntent(ctx: Context, task: com.dastyar.app.data.Task): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).apply {
            putExtra("taskId", task.id)
            putExtra("title", task.title)
            putExtra("body", task.description.ifBlank { "وقت یادآوری این کاره ✅" })
            putExtra("repeat", task.repeat)
            putExtra("date", task.date)
            putExtra("time", task.time)
        }
        return PendingIntent.getBroadcast(
            ctx, task.notifyId, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun triggerMillis(date: String, time: String): Long? {
        return try {
            // task.date is a JALALI ISO string, so it must be converted to a real
            // Gregorian day before building the instant -- parsing it as Gregorian
            // would fire the reminder on a completely wrong day.
            val d = com.dastyar.app.data.Jalali.toGregorian(date) ?: return null
            val t = LocalTime.parse(time)
            LocalDateTime.of(d, t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}

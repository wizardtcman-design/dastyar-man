package com.dastyar.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dastyar.app.data.DastyarDatabase
import com.dastyar.app.data.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Schedules real system alarms for task reminders. */
object ReminderScheduler {

    fun schedule(ctx: Context, task: Task) {
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

    fun cancel(ctx: Context, task: Task) {
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        alarm.cancel(pendingIntent(ctx, task))
    }

    fun rescheduleAll(ctx: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = DastyarDatabase.get(ctx).dao()
            dao.pendingReminders().forEach { schedule(ctx, it) }
        }
    }

    private fun pendingIntent(ctx: Context, task: Task): PendingIntent {
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

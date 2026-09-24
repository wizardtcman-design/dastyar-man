package com.dastyar.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dastyar.app.MainActivity
import com.dastyar.app.R
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object NotificationHelper {
    const val CHANNEL_REMINDERS = "dastyar_reminders"
    const val CHANNEL_DAILY = "dastyar_daily"

    /** Daily check-in reminder notification id. */
    const val ID_DAILY = 9001

    fun createChannels(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS, "یادآوری کارها",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "یادآوری کارها و برنامه‌ها" }
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DAILY, "یادآوری روزانه",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "یادآوری ثبت وضعیت روزانه" }
        )
    }

    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun show(
        ctx: Context,
        id: Int,
        title: String,
        body: String,
        channel: String = CHANNEL_REMINDERS,
        openCheckIn: Boolean = false
    ) {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openCheckIn) putExtra(MainActivity.EXTRA_OPEN_CHECKIN, true)
        }
        val pi = PendingIntent.getActivity(
            ctx, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, notif)
        } catch (_: SecurityException) {
        }
    }
}

/**
 * Fires the daily check-in reminder. It only shows the notification when the
 * user has enabled the reminder and has not already checked in today, then it
 * schedules the next day. Runs entirely on the device with no network.
 */
class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DailyReminder.scheduleNext(context)
        val prefs = DailyReminder.prefs(context)
        if (!prefs.getBoolean(DailyReminder.KEY_ENABLED, false)) return
        if (DailyReminder.hasCheckedInToday(context)) return
        if (!NotificationHelper.canPost(context)) return

        NotificationHelper.show(
            context,
            NotificationHelper.ID_DAILY,
            "دستیار من",
            "وقتشه یه سر به دستیار من بزنی 🌱",
            NotificationHelper.CHANNEL_DAILY,
            openCheckIn = true
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "یادآوری"
        val body = intent.getStringExtra("body") ?: ""
        val id = intent.getIntExtra("id", 1)
        NotificationHelper.show(context, id, title, body)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.rescheduleAll(context)
            DailyReminder.reschedule(context)
        }
    }
}

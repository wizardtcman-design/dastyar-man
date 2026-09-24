package com.dastyar.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dastyar.app.MainActivity
import com.dastyar.app.R

object NotificationHelper {
    const val CHANNEL_REMINDERS = "dastyar_reminders"
    const val CHANNEL_DAILY = "dastyar_daily"

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
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "یادآوری ثبت وضعیت روزانه" }
        )
    }

    fun show(ctx: Context, id: Int, title: String, body: String, channel: String = CHANNEL_REMINDERS) {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
        }
    }
}

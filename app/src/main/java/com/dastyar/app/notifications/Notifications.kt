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
    const val CHANNEL_PERIOD = "dastyar_period"

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
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PERIOD, "یادآوری پریود",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "یادآوری نزدیک شدن زمان پریود" }
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
        openCheckIn: Boolean = false,
        openTaskId: Long = -1L
    ) {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openCheckIn) putExtra(MainActivity.EXTRA_OPEN_CHECKIN, true)
            if (openTaskId > 0) putExtra(MainActivity.EXTRA_OPEN_TASK, openTaskId)
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
        val taskId = intent.getLongExtra("taskId", -1L)
        val title = intent.getStringExtra("title") ?: "یادآوری"
        val body = intent.getStringExtra("body") ?: ""
        val repeat = intent.getStringExtra("repeat") ?: "none"
        val date = intent.getStringExtra("date") ?: ""
        val time = intent.getStringExtra("time") ?: ""

        if (NotificationHelper.canPost(context)) {
            NotificationHelper.show(
                context,
                com.dastyar.app.data.Task(id = if (taskId > 0) taskId else 1L).notifyId,
                title,
                body,
                NotificationHelper.CHANNEL_REMINDERS,
                openTaskId = taskId
            )
        }

        // Re-arm a repeating task for its next occurrence, straight from the
        // alarm so the chain continues with the app closed and after a reboot.
        if (repeat != "none" && repeat.isNotBlank()) {
            val task = com.dastyar.app.data.Task(
                id = if (taskId > 0) taskId else 0L,
                title = title, description = body,
                date = date, time = time, repeat = repeat, reminderEnabled = true
            )
            val from = try {
                // date is a Jalali ISO string: convert to a real Gregorian day
                // before building the instant for the repeat roll-forward.
                val d = com.dastyar.app.data.Jalali.toGregorian(date)
                    ?: throw IllegalArgumentException("bad jalali date")
                java.time.LocalDateTime.of(
                    d,
                    java.time.LocalTime.parse(time)
                ).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
            val next = ReminderScheduler.rollForward(task, from, System.currentTimeMillis())
            if (next != null) ReminderScheduler.scheduleAt(context, task, next)
        }
    }
}

/**
 * Fires one cycle event notification. The event name is carried in the intent
 * so a single receiver serves every point of the cycle (PMS, period countdown,
 * period due/late, fertile window, ovulation, period ended). Each has its own
 * stable notification id, so a re-schedule replaces the previous alarm and the
 * user never gets duplicates. Runs entirely on the device with no network.
 */
class PeriodReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!PeriodReminder.isEnabled(context)) return
        val event = runCatching {
            PeriodReminder.Event.valueOf(intent.getStringExtra("event") ?: "APPROACH_7")
        }.getOrDefault(PeriodReminder.Event.APPROACH_7)
        val daysBefore = intent.getIntExtra("daysBefore", 0)

        val title: String
        val body: String
        when (event) {
            PeriodReminder.Event.PMS -> {
                title = "نزدیک شدن PMS 🌙"
                body = "وارد روزهای پیش از پریود شدی. ممکن است خلق‌وخو نوسان کند یا نفخ و " +
                        "خستگی بیشتر شود؛ خواب منظم، کربوهیدرات پیچیده و کافئین کمتر کمک می‌کند."
            }
            PeriodReminder.Event.APPROACH_7 -> {
                title = "یادآوری پریود"
                body = "حدود ۷ روز تا پریود بعدی باقی مانده 🌸 این زمان خوبی برای آماده‌بودن است."
            }
            PeriodReminder.Event.APPROACH_3 -> {
                title = "یادآوری پریود"
                body = "حدود ۳ روز تا پریود بعدی باقی مانده 🌸"
            }
            PeriodReminder.Event.APPROACH_2 -> {
                title = "یادآوری پریود"
                body = "حدود ۲ روز تا پریود بعدی باقی مانده؛ چند وسیله همراهت باشد 🌸"
            }
            PeriodReminder.Event.APPROACH_1 -> {
                title = "یادآوری پریود"
                body = "احتمالاً حدود ۱ روز تا پریود بعدی باقی مانده 🌸"
            }
            PeriodReminder.Event.PERIOD_DUE -> {
                title = "امروز، موعد پریود 🌸"
                body = "امروز روز تخمینی شروع پریودت است. هر وقت شروع شد، از داشبورد «شروع پریود» " +
                        "را بزن تا چرخه دقیق‌تر ثبت شود."
            }
            PeriodReminder.Event.PERIOD_LATE -> {
                title = "تأخیر در پریود ⏰"
                body = "پریودت از موعد تقریبی‌اش چند روز گذشته. اگر شروع شده، «شروع پریود» را بزن؛ " +
                        "اگر نه، نگران نباش — چرخه می‌تواند چند روز جابه‌جا شود."
            }
            PeriodReminder.Event.FERTILE_START -> {
                title = "شروع بازه باروری 🌱"
                body = "به بازه تخمینی باروری‌ات نزدیک می‌شوی. اگر برای بارداری برنامه داری این روزها " +
                        "مهم‌اند؛ توجه کن این محاسبه روش قطعی پیشگیری از بارداری نیست."
            }
            PeriodReminder.Event.OVULATION -> {
                title = "روز تخمک‌گذاری 💧"
                body = "امروز حدود روز تخمک‌گذاری تو است. ممکن است ترشح بیشتر یا کمی درد یک‌طرفه " +
                        "حس کنی؛ طبیعی است. آب کافی بنوش و به بدنت توجه کن."
            }
            PeriodReminder.Event.FERTILE_END -> {
                title = "پایان بازه باروری"
                body = "بازه تخمینی باروری‌ات به پایان رسید. اگر درباره بارداری سؤالی داری، ثبتش کن " +
                        "تا در مشاوره دقیق‌تر بررسی شود."
            }
            PeriodReminder.Event.PERIOD_ENDED -> {
                title = "پریودت تموم شده؟"
                body = "اگر خونریزی‌ات تمام شده، از داشبورد «پایان پریود» را بزن تا برنامه طول پریودت " +
                        "را یاد بگیرد و پیش‌بینی‌ها دقیق‌تر شوند."
            }
        }

        if (!NotificationHelper.canPost(context)) return
        NotificationHelper.show(
            context,
            event.id,
            title,
            body,
            NotificationHelper.CHANNEL_PERIOD
        )
        // Re-arm the whole schedule so the next cycle's events are ready.
        PeriodReminder.scheduleNext(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.rescheduleAll(context)
            DailyReminder.reschedule(context)
            PeriodReminder.reschedule(context)
        }
    }
}

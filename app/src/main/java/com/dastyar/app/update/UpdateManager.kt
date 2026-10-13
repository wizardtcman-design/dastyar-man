package com.dastyar.app.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dastyar.app.BuildConfig
import com.dastyar.app.MainActivity
import com.dastyar.app.R
import com.dastyar.app.notifications.NotificationHelper

/**
 * The single entry point for the update feature. It:
 *   - checks GitHub at most once per [CHECK_INTERVAL_MS]
 *   - only cares about a strictly-newer version the user has not skipped
 *   - posts one gentle Persian notification per new version, which opens the
 *     in-app update screen
 *
 * The check is always safe: any network or parse failure simply returns null,
 * so the app is never blocked or disturbed by it.
 */
object UpdateManager {

    const val CHANNEL_UPDATES = "dastyar_updates"

    /** Notification id for "update available" (stable, so it replaces itself). */
    const val ID_UPDATE = 9101

    /** At most one check per day; the app is checked on launch and on resume. */
    const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    /** Extra the notification / update screen reads to know a check was asked. */
    const val EXTRA_OPEN_UPDATE = "open_update"

    fun createChannel(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UPDATES,
                "به‌روزرسانی دستیار من",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "اطلاع‌رسانی نسخه‌های تازه دستیار من" }
        )
    }

    /**
     * Runs a check honouring the interval, and returns the newer [UpdateInfo]
     * when the user should be told about it, or null otherwise. Pass
     * [force] = true from the update screen's own "بررسی دوباره" button so a
     * manual check always goes to the network.
     */
    suspend fun checkForUpdate(ctx: Context, force: Boolean = false): UpdateInfo? {
        val now = System.currentTimeMillis()
        if (!force && now - UpdatePrefs.lastCheckMs(ctx) < CHECK_INTERVAL_MS) {
            return null
        }
        val latest = GitHubReleaseClient.fetchLatest() ?: return null
        UpdatePrefs.setLastCheckMs(ctx, now)

        // Only a strictly-newer version counts as an update.
        if (!VersionCompare.isNewer(latest.version, BuildConfig.VERSION_NAME)) return null
        // A version the user chose to skip is never offered again.
        if (latest.version == UpdatePrefs.skippedVersion(ctx)) return null
        return latest
    }

    /**
     * Like [checkForUpdate] but also posts the notification once per version.
     * Used from the background worker and on app launch.
     */
    suspend fun checkAndNotify(ctx: Context): UpdateInfo? {
        val update = checkForUpdate(ctx, force = false) ?: return null
        if (update.version != UpdatePrefs.notifiedVersion(ctx)) {
            notify(ctx, update)
            UpdatePrefs.setNotifiedVersion(ctx, update.version)
        }
        return update
    }

    /** Posts the "نسخه تازه" notification. Never throws. */
    fun notify(ctx: Context, update: UpdateInfo) {
        if (!NotificationHelper.canPost(ctx)) return
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATE, true)
        }
        val pi = PendingIntent.getActivity(
            ctx, ID_UPDATE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("نسخه تازه دستیار من آماده است 🎉")
            .setContentText("نسخه ${update.version} منتشر شد. برای دیدن تغییرات و نصب بزن.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("نسخه ${update.version} منتشر شد. برای دیدن تغییرات و نصب، بزن.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_UPDATE, notif)
        } catch (_: SecurityException) {
        }
    }
}

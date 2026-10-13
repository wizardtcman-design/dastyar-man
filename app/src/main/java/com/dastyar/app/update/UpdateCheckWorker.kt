package com.dastyar.app.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * A once-a-day background check so the user still hears about a new version
 * even if they only open the app briefly. It reuses [UpdateManager]
 * transparently, so it posts the same notification the launch check would.
 *
 * WorkManager (not an alarm) is used on purpose: it is exact-timing-free,
 * survives reboots and app updates, and needs no special permission.
 */
class UpdateCheckWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            UpdateManager.checkAndNotify(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "dastyar_update_check"

        /** Schedules the daily check, replacing any existing schedule. */
        fun schedule(ctx: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

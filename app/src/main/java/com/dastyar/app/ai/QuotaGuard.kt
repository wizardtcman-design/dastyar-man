package com.dastyar.app.ai

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Remembers that Cloudflare's free daily Neuron allowance ran out, so the app
 * stops calling it for the rest of the day and switches straight to the image
 * fallback. The flag clears itself when the local date changes.
 *
 * Cloudflare documents that the Workers AI free allocation of 10,000 Neurons
 * resets every day (00:00 UTC). No exact reset timestamp is published through
 * the API, so the app treats the day boundary as an approximation and never
 * shows an invented exact minute.
 */
object QuotaGuard {

    private const val PREFS = "dastyar_quota"
    private const val KEY_CF_DAY = "cf_exhausted_day"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(Date())

    fun markCloudflareExhausted(ctx: Context?) {
        if (ctx == null) return
        prefs(ctx).edit().putString(KEY_CF_DAY, today()).apply()
    }

    /** True only if Cloudflare already reported the quota exhausted today. */
    fun cloudflareExhaustedToday(ctx: Context?): Boolean {
        if (ctx == null) return false
        return prefs(ctx).getString(KEY_CF_DAY, null) == today()
    }

    /**
     * The documented reset rule of the free allowance, phrased as general
     * information rather than a per-account exact time.
     */
    const val RESET_NOTE = "سهمیه رایگان Cloudflare هر روز حدود نیمه‌شب UTC (به‌طور تقریبی) ریست می‌شود."
}

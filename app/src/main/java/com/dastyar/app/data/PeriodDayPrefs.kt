package com.dastyar.app.data

import android.content.Context

/**
 * The user's answer to the one daily question that gates the period questions:
 * "are you on your period today?". A durable, per-day record like
 * [CheckInPrefs], so the answer survives leaving and re-entering the screen and
 * the app being closed.
 *
 * It is stored next to the check-in row rather than only inside it, because a
 * row for today can also be created by something else (logging water), and then
 * "the row says no" could not be told apart from "the user answered no". With
 * this marker, «نه» reliably keeps the period questions closed for the rest of
 * the day, and «پایان پریود» can close them at once.
 */
object PeriodDayPrefs {
    private const val PREFS = "dastyar_period_day"
    private const val KEY_DAY = "day"
    private const val KEY_YES = "yes"

    /** Today's answer, or null when the user has not answered today yet. */
    fun answer(ctx: Context, today: String): Boolean? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (p.getString(KEY_DAY, null) == today) p.getBoolean(KEY_YES, false) else null
    }

    /** Records today's answer, replacing whatever was there for the same day. */
    fun put(ctx: Context, today: String, yes: Boolean) {
        if (today.isBlank()) return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_DAY, today).putBoolean(KEY_YES, yes).apply()
    }

    /** Forgets the answer, e.g. when all data is cleared. */
    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /**
     * The answer that counts for today. The durable marker wins; when it is
     * missing (a row written by an older build) a recorded «بله» in the
     * check-in row is honoured, while a plain "false" is NOT treated as an
     * answer -- that row may just be the one water logging created.
     */
    fun effective(marker: Boolean?, rowIsPeriodDay: Boolean): Boolean? =
        marker ?: if (rowIsPeriodDay) true else null

    /**
     * Whether the period-specific questions should be shown today. They appear
     * only while the period is active: an explicit «بله» today, or no answer yet
     * today while a period is still running. «نه» closes them, and after
     * «پایان پریود» (which records "no") they stay closed until a new period is
     * started.
     */
    fun detailVisible(answer: Boolean?, periodRunning: Boolean): Boolean =
        answer == true || (answer == null && periodRunning)
}

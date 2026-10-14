package com.dastyar.app.data

import android.content.Context

/**
 * A tiny, durable record of the last day the daily check-in was completed.
 *
 * The database row in `checkins` stays the source of truth, but the app also
 * needs to know "did the user finish today's check-in?" the instant it starts,
 * before the asynchronous Room read has returned. Relying only on an in-memory
 * value that starts out empty made the questions appear again on every open
 * until the read landed. This marker is written only after a check-in is really
 * saved, keyed to the user's local calendar day, so the decision is immediate
 * and it still resets on its own when the day rolls over.
 */
object CheckInPrefs {
    private const val PREFS = "dastyar_checkin"
    private const val KEY_DONE_DAY = "done_day"

    /** The local day (Jalali ISO) the user last completed a check-in for. */
    fun doneDay(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DONE_DAY, null)

    /** Records that [day]'s check-in is complete. Call only after a real save. */
    fun markDone(ctx: Context, day: String) {
        if (day.isBlank()) return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_DONE_DAY, day).apply()
    }

    /** Forgets the marker, e.g. when all data is cleared. */
    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_DONE_DAY).apply()
    }
}

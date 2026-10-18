package com.dastyar.app.data

/**
 * The single decision behind "ask the daily questions today or not".
 *
 * It is deliberately pure so it can be tested without a device, and it is
 * fail-safe in the direction that matters: whenever there is *any* evidence
 * that today is already recorded -- the durable marker or a real row for
 * today's local date -- the questions stay closed.
 *
 * [row] is today's row when it is known (from the database flow or the
 * in-memory copy). Passing a stale row for another day is harmless: only an
 * exact match with [today] counts.
 */
object DailyGate {

    fun shouldAsk(
        onboardingDone: Boolean,
        today: String,
        completedDay: String?,
        row: CheckIn?
    ): Boolean {
        // The onboarding questionnaire already became day one; never ask twice.
        if (!onboardingDone) return false
        // The durable marker is written only after a successful save.
        if (completedDay == today) return false
        // A committed row is the strongest evidence, even if the marker write
        // was lost (e.g. the process died right after the insert).
        return row?.date != today
    }
}

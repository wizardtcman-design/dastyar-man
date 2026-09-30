package com.dastyar.app.data

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The cycle brain: everything that turns the user's recorded period history
 * into a current cycle, a prediction for the next one, and an honest estimate
 * of how long the bleeding lasts.
 *
 * Design rules, deliberately:
 *
 * - **The history is the source of truth.** [PeriodEvent] rows are the real
 *   recorded periods. The first one comes from the onboarding questionnaire;
 *   the user then only ever has to tap "شروع پریود" / "پایان پریود". After a
 *   couple of real cycles the app stops needing manual input and predicts on
 *   its own, improving every month as more rows arrive.
 *
 * - **Local maths always answers.** Nothing here needs the network, so the
 *   ring, the day count and the prediction keep working offline. The AI, when
 *   it is reachable, may only *refine* the wording of the estimate — it can
 *   never block or break the calculation (see `MainViewModel.refineCycleNote`).
 *
 * - **Nothing is invented.** With a single recorded cycle the prediction falls
 *   back to the length the user declared in onboarding; the result is labelled
 *   approximate. With two or more real cycles the average of the actual gaps
 *   is used, and a cycle is flagged irregular when the gaps disagree.
 *
 * This object is pure: it never touches Android, the database or the clock
 * beyond [Dates.today], so it is easy to reason about and reuse.
 */
object Cycle {

    /** The gap the ring leaves at the top, in "days" of visual space. */
    const val RING_GAP_DAYS = 2f

    /** A cycle is only considered usable inside this band. */
    private val LENGTH_BAND = 15..60

    /** A period length is only considered usable inside this band. */
    private val PERIOD_BAND = 1..12

    /**
     * The user's learned cycle profile, derived from real history.
     *
     * @param startIso   first day of the current cycle (the anchor)
     * @param endIso     last recorded day of bleeding of the current cycle, or
     *                   blank while the period is still going / unrecorded
     * @param length     the cycle length to use for the ring and predictions
     * @param periodDays the bleeding length to use for the ring and predictions
     * @param samples    how many real cycle gaps the estimate is based on
     * @param irregular  true when the recorded gaps disagree with each other
     * @param learned    true once the estimate comes from real history rather
     *                   than the onboarding answer alone
     */
    data class Info(
        val startIso: String,
        val endIso: String,
        val length: Int,
        val periodDays: Int,
        val samples: Int,
        val irregular: Boolean,
        val learned: Boolean
    ) {
        /** True when there is a usable current cycle to draw. */
        val hasCycle: Boolean get() = startIso.isNotBlank() && length in LENGTH_BAND
    }

    /**
     * Builds the current [Info] from the recorded [events] (any order) and the
     * [profile]'s declared values, which are only a starting point.
     *
     * The anchor is always the profile's `lastPeriodDate`, because that is the
     * date the rest of the app already reads; this function keeps it consistent
     * with the history without moving it.
     */
    fun info(profile: Profile?, events: List<PeriodEvent>): Info? {
        val p = profile ?: return null
        val sorted = events
            .filter { Dates.parse(it.startIso) != null }
            .sortedBy { Jalali.toDayNumber(Dates.parse(it.startIso)!!) }

        // The anchor: the profile date when set, otherwise the latest event.
        val anchor = p.lastPeriodDate.ifBlank { sorted.lastOrNull()?.startIso.orEmpty() }
        if (anchor.isBlank() || Dates.parse(anchor) == null) return null

        val declaredLen = p.cycleLength.takeIf { it in LENGTH_BAND } ?: 28
        val declaredPeriod = p.periodDays.takeIf { it in PERIOD_BAND } ?: 5

        // --- cycle length from the gaps between consecutive period starts ---
        val gaps = sorted.zipWithNext { a, b ->
            Jalali.daysBetween(a.startIso, b.startIso)
        }.filter { it in LENGTH_BAND }

        val learned = gaps.size >= 2
        val length = when {
            gaps.isEmpty() -> declaredLen
            gaps.size == 1 -> {
                // One real gap: a gentle blend with the declared length, so a
                // single unusual cycle does not swing the ring completely.
                ((gaps[0] + declaredLen) / 2.0).roundToInt().coerceIn(LENGTH_BAND)
            }
            else -> {
                // The most recent gaps matter more than old ones: weight the
                // last three and take the mean, so the estimate follows a body
                // that is changing rather than averaging a year of history.
                val recent = gaps.takeLast(3)
                recent.average().roundToInt().coerceIn(LENGTH_BAND)
            }
        }

        // Irregular when the recorded gaps disagree by more than a few days.
        val irregular = gaps.size >= 2 && (gaps.max() - gaps.min()) >= 7

        // --- period length from recorded start/end pairs ---
        val realPeriods = sorted.mapNotNull { e ->
            if (!e.hasEnd) return@mapNotNull null
            val d = Jalali.daysBetween(e.startIso, e.endIso) + 1
            d.takeIf { it in PERIOD_BAND }
        }
        val periodDays = if (realPeriods.isNotEmpty()) {
            // Median keeps one unusually long period from dragging the estimate.
            val s = realPeriods.sorted()
            val mid = s.size / 2
            if (s.size % 2 == 1) s[mid] else ((s[mid - 1] + s[mid]) / 2.0).roundToInt()
        } else {
            declaredPeriod
        }.coerceIn(1, minOf(12, length))

        // The end of the current period, when the user recorded one.
        val currentEvent = sorted.lastOrNull { it.startIso == anchor }
        val end = currentEvent?.endIso.orEmpty().ifBlank {
            // No explicit end yet: an honest estimate from the learned length,
            // clamped so it can never run past the end of the cycle.
            Dates.plusDays(anchor, periodDays - 1)
        }

        return Info(
            startIso = anchor,
            endIso = end,
            length = length,
            periodDays = periodDays,
            samples = gaps.size,
            irregular = irregular,
            learned = learned
        )
    }

    /** 1-based day inside the current cycle. Wraps into [length] as before. */
    fun day(info: Info?): Int {
        val i = info ?: return 0
        if (!i.hasCycle) return 0
        return Dates.cycleDay(i.startIso, i.length)
    }

    /**
     * The raw number of days since the cycle started, never wrapped. Day 1 is
     * the start itself. This is what lets the ring show an overdue cycle
     * instead of silently jumping back to day 1.
     */
    fun rawDay(info: Info?): Int {
        val i = info ?: return 0
        if (!i.hasCycle) return 0
        val days = Jalali.daysBetween(i.startIso, Dates.today())
        return if (days < 0) 0 else days + 1
    }

    /** True when the cycle has run past its length and no new start was tapped. */
    fun isOverdue(info: Info?): Boolean {
        val i = info ?: return false
        if (!i.hasCycle) return false
        return rawDay(i) > i.length
    }

    /** Days the cycle is past its expected length; 0 when not overdue. */
    fun overdueDays(info: Info?): Int {
        val i = info ?: return 0
        if (!i.hasCycle) return 0
        return (rawDay(i) - i.length).coerceAtLeast(0)
    }

    /**
     * The day number to draw the "today" marker on. When the cycle is overdue
     * the marker sits on the last day of the ring, so it stays visible and the
     * user is nudged to tap "شروع پریود" rather than being shown a silent
     * reset to day 1.
     */
    fun markerDay(info: Info?): Int {
        val i = info ?: return 0
        if (!i.hasCycle) return 0
        return rawDay(i).coerceIn(1, i.length)
    }

    /** Days until the next expected period; 0 or negative once it is due. */
    fun daysUntilNext(info: Info?): Int {
        val i = info ?: return -1
        if (!i.hasCycle) return -1
        return i.length - rawDay(i) + 1
    }

    /** The expected date of the next period as a Jalali ISO string. */
    fun nextPeriodDate(info: Info?): String? {
        val i = info ?: return null
        if (!i.hasCycle) return null
        return Dates.plusDays(i.startIso, i.length)
    }

    /**
     * How sure the estimate is, in plain Persian, so the UI never presents a
     * guess as a certainty:
     *
     * - one recorded cycle  -> «تقریبی»
     * - two or three cycles -> «بر پایه دو چرخه واقعی»
     * - four or more        -> «آموختهشده از تاریخچه»
     */
    fun confidenceLabel(info: Info?): String? {
        val i = info ?: return null
        if (!i.hasCycle) return null
        return when {
            i.samples >= 4 -> "آموخته‌شده از تاریخچه‌ات"
            i.samples >= 2 -> "بر پایه چرخه‌های واقعی‌ات"
            i.samples == 1 -> "تقریبی — با ثبت چرخه بعدی دقیق‌تر می‌شود"
            else -> "تقریبی — از پاسخ پرسشنامه"
        }
    }

    /**
     * A short local explanation of the current cycle state, used as the
     * instant fallback text for the cycle tip and as the base the AI may
     * refine. Returns null when there is no cycle yet.
     */
    fun summary(info: Info?, profile: Profile?): String? {
        val i = info ?: return null
        if (!i.hasCycle) return null
        val day = rawDay(i)
        val phase = Health.phase(profile)?.title
        return buildString {
            append("امروز روز ${Dates.fa(day)} چرخه است")
            if (isOverdue(i)) {
                append(" و چرخه ${Dates.fa(overdueDays(i))} روز از موعد گذشته؛ " +
                        "هر وقت پریود شروع شد «شروع پریود» را بزن تا چرخه جدید ساخته شود.")
            } else {
                val until = daysUntilNext(i)
                append("؛ حدود ${Dates.fa(until.coerceAtLeast(0))} روز تا پریود بعدی مانده")
                if (phase != null) append(" (${phase})")
                append(".")
            }
        }
    }

    /**
     * The number of days between the start and end of a period, inclusive, or
     * null when either date is missing/invalid.
     */
    fun periodLength(startIso: String, endIso: String): Int? {
        if (startIso.isBlank() || endIso.isBlank()) return null
        if (Dates.parse(startIso) == null || Dates.parse(endIso) == null) return null
        val d = Jalali.daysBetween(startIso, endIso) + 1
        return d.takeIf { it > 0 }
    }

    /**
     * A sanity check for a start/end pair entered by the user: the end may not
     * be before the start, and the period may not be absurdly long.
     */
    fun isValidPeriod(startIso: String, endIso: String): Boolean {
        val len = periodLength(startIso, endIso) ?: return endIso.isBlank()
        return len in 1..20
    }

    /** True when two dates are the same day, used to avoid duplicate rows. */
    fun sameDay(a: String, b: String): Boolean =
        a.isNotBlank() && b.isNotBlank() && abs(Jalali.daysBetween(a, b)) == 0
}

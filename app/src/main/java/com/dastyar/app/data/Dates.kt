package com.dastyar.app.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * All persisted dates are Jalali ISO strings (yyyy-MM-dd in the Solar Hijri
 * calendar), so the stored data and the UI always speak the same calendar.
 */
object Dates {
    private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** Today as a Jalali ISO string. */
    fun today(): String = Jalali.todayIso()

    fun parse(s: String): Jalali.JDate? = Jalali.parse(s)

    fun fa(n: Int): String = fa(n.toString())

    fun fa(s: String): String {
        val faDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        return s.map { c -> if (c in '0'..'9') faDigits[c - '0'] else c }.joinToString("")
    }

    /** Persian digits for a float, trimmed of a trailing ".0". */
    fun fa(v: Float, decimals: Int = 1): String {
        if (v <= 0f) return fa(0)
        val whole = v.toInt()
        val rounded = if (v == whole.toFloat()) fa(whole)
        else fa("%.${decimals}f".format(v))
        return rounded
    }

    /**
     * Persian digits for a value while it is being typed. An empty or zero
     * value shows nothing, so number fields never carry a stray leading zero.
     */
    fun faField(v: Int): String = if (v == 0) "" else fa(v)

    fun faField(v: Float): String =
        if (v <= 0f) "" else if (v == v.toInt().toFloat()) fa(v.toInt()) else fa(v)

    /** Persian digits for a value that should always be visible, including zero. */
    fun faAlways(v: Int): String = fa(v)

    /** Parses a number typed by the user, accepting Persian or Latin digits. */
    fun parseNum(s: String): Float? {
        if (s.isBlank()) return null
        val faDigits = "۰۱۲۳۴۵۶۷۸۹"
        val normalized = buildString {
            s.forEach { c ->
                val idx = faDigits.indexOf(c)
                when {
                    idx >= 0 -> append(('0' + idx))
                    c.isDigit() || c == '.' -> append(c)
                }
            }
        }
        return normalized.toFloatOrNull()
    }

    /** Time like "09:00" rendered with Persian digits. */
    fun faTime(hhmm: String): String = fa(hhmm)

    /** Cycle day, wrapped into the cycle length. 0 when unknown. */
    fun cycleDay(lastPeriodIso: String, cycleLength: Int): Int {
        if (parse(lastPeriodIso) == null) return 0
        val len = if (cycleLength in 15..60) cycleLength else 28
        val days = Jalali.daysBetween(lastPeriodIso, today())
        if (days < 0) return 0
        return (days % len) + 1
    }

    fun daysUntilNextPeriod(lastPeriodIso: String, cycleLength: Int): Int {
        if (parse(lastPeriodIso) == null) return -1
        val len = if (cycleLength in 15..60) cycleLength else 28
        val days = Jalali.daysBetween(lastPeriodIso, today())
        if (days < 0) return -1
        return len - (days % len)
    }

    fun isPeriodDay(lastPeriodIso: String, cycleLength: Int, periodDays: Int): Boolean {
        if (parse(lastPeriodIso) == null) return false
        val d = cycleDay(lastPeriodIso, cycleLength)
        return d in 1..periodDays.coerceAtLeast(1)
    }

    fun pretty(iso: String): String = Jalali.pretty(iso)

    fun short(iso: String): String = Jalali.short(iso)

    /** Last N days as Jalali ISO strings, oldest first. */
    fun lastDays(n: Int): List<String> {
        val todayJ = Jalali.today()
        val todayNo = Jalali.toDayNumber(todayJ)
        // step back using the jalali day number, then convert back
        return (n - 1 downTo 0).map { offset ->
            dayNumberToIso(todayNo - offset)
        }
    }

    /** Convert a jalali day number back to a Jalali ISO date. */
    private fun dayNumberToIso(dayNumber: Int): String = Jalali.fromDayNumber(dayNumber)

    fun shortLabel(iso: String): String {
        val j = parse(iso) ?: return iso
        return fa(j.day.toString())
    }

    /** Jalali day-of-month + month label for chart axes. */
    fun axisLabel(iso: String): String {
        val j = parse(iso) ?: return iso
        return "${fa(j.day)} ${Jalali.monthName(j.month).take(3)}"
    }

    fun gregorianToday(): LocalDate = LocalDate.now()
}

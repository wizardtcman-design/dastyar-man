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

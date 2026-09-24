package com.dastyar.app.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object Dates {
    private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun today(): String = LocalDate.now().format(fmt)

    fun parse(s: String): LocalDate? = try {
        LocalDate.parse(s, fmt)
    } catch (_: Exception) {
        null
    }

    /** Persian digits for display. */
    fun fa(n: Int): String = fa(n.toString())

    fun fa(s: String): String {
        val faDigits = charArrayOf('۰','۱','۲','۳','۴','۵','۶','۷','۸','۹')
        return s.map { c -> if (c in '0'..'9') faDigits[c - '0'] else c }.joinToString("")
    }

    /**
     * Cycle day: how many days since the last period started, wrapped into
     * the cycle length. Returns 0 when no period date is known.
     */
    fun cycleDay(lastPeriod: String, cycleLength: Int): Int {
        val start = parse(lastPeriod) ?: return 0
        val len = if (cycleLength in 15..60) cycleLength else 28
        val days = ChronoUnit.DAYS.between(start, LocalDate.now()).toInt()
        if (days < 0) return 0
        return (days % len) + 1
    }

    fun daysUntilNextPeriod(lastPeriod: String, cycleLength: Int): Int {
        val start = parse(lastPeriod) ?: return -1
        val len = if (cycleLength in 15..60) cycleLength else 28
        val days = ChronoUnit.DAYS.between(start, LocalDate.now()).toInt()
        if (days < 0) return -1
        return len - (days % len)
    }

    fun isPeriodDay(lastPeriod: String, cycleLength: Int, periodDays: Int): Boolean {
        val d = cycleDay(lastPeriod, cycleLength)
        return d in 1..periodDays.coerceAtLeast(1)
    }

    fun pretty(s: String): String {
        val d = parse(s) ?: return s
        val months = listOf(
            "فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور",
            "مهر","آبان","آذر","دی","بهمن","اسفند"
        )
        // Gregorian month name in Persian for clarity
        val gMonths = listOf(
            "ژانویه","فوریه","مارس","آپریل","مه","ژوئن",
            "جولای","آگوست","سپتامبر","اکتبر","نوامبر","دسامبر"
        )
        return "${fa(d.dayOfMonth)} ${gMonths[d.monthValue - 1]} ${fa(d.year)}"
    }

    /** Last N days as yyyy-MM-dd, oldest first. */
    fun lastDays(n: Int): List<String> {
        val today = LocalDate.now()
        return (n - 1 downTo 0).map { today.minusDays(it.toLong()).format(fmt) }
    }

    fun shortLabel(date: String): String {
        val d = parse(date) ?: return date
        return fa(d.dayOfMonth.toString())
    }
}

package com.dastyar.app.data

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Local (no-network) health analysis.
 *
 * Everything here is derived from data the user actually recorded: the
 * onboarding questionnaire, daily check-ins, the weight log and the smart
 * profile. Nothing is invented — when there is not enough history the result
 * says so instead of showing a made-up number.
 */
object Health {

    // ------------------------------------------------------------ body / BMI

    /** BMI, or null when height or weight is missing. */
    fun bmi(profile: Profile?): Float? {
        val h = profile?.heightCm ?: 0
        val w = profile?.weightKg ?: 0f
        if (h < 80 || w <= 0f) return null
        val m = h / 100f
        return w / (m * m)
    }

    /**
     * Adult BMI bands. Children and teenagers use different charts, so under 20
     * we deliberately do not classify and let the caller show the raw number.
     */
    fun bmiCategory(profile: Profile?): String? {
        val v = bmi(profile) ?: return null
        val age = profile?.age ?: 0
        if (age in 1..19) return null
        return when {
            v < 18.5f -> "کم‌وزن"
            v < 25f -> "محدوده معمول"
            v < 30f -> "اضافه‌وزن"
            else -> "چاقی"
        }
    }

    /** True when a BMI band should not be applied (child / teen / unknown age). */
    fun bmiAdultBandsApply(profile: Profile?): Boolean {
        val age = profile?.age ?: 0
        return age == 0 || age >= 20
    }

    // ---------------------------------------------------------------- water

    /**
     * A realistic daily water target in glasses, from body weight plus the
     * day's activity, stress and heat-sensitive signals. Falls back to a plain
     * 8 glasses when the user has not given a weight.
     */
    fun waterTarget(profile: Profile?, today: CheckIn?): Int {
        val weight = profile?.weightKg ?: 0f
        var glasses = if (weight > 0f) {
            // ~30 ml per kg, one glass = 250 ml
            (weight * 30f / 250f) + 0.5f
        } else {
            8f
        }
        when {
            (today?.physicalActivity ?: "").contains("زیاد") -> glasses += 2f
            (today?.physicalActivity ?: "").contains("متوسط") -> glasses += 1f
        }
        when {
            (today?.stressLevel ?: "").contains("خیلی زیاد") -> glasses += 1f
            (today?.stressLevel ?: "").contains("زیاد") -> glasses += 0.5f
        }
        if ((today?.fatigueSeverity ?: "").contains("خیلی زیاد")) glasses += 0.5f
        return glasses.roundToInt().coerceIn(5, 15)
    }

    // ---------------------------------------------------------------- sleep

    /** Recommended sleep hours for the user's age group. */
    fun sleepTargetHours(profile: Profile?): ClosedFloatingPointRange<Float> {
        val age = profile?.age ?: 0
        return when {
            age in 1..13 -> 9f..11f
            age in 14..17 -> 8f..10f
            age in 18..64 -> 7f..9f
            age >= 65 -> 7f..8f
            else -> 7f..9f
        }
    }

    /** Short Persian hint about last night's sleep, or null when unknown. */
    fun sleepAdvice(profile: Profile?, today: CheckIn?): String? {
        val h = today?.sleepHours ?: 0f
        if (h <= 0f) return null
        val target = sleepTargetHours(profile)
        return when {
            h < target.start - 1f -> "دیشب کم خوابیدی؛ امشب زودتر بخواب."
            h < target.start -> "کمی کمتر از حد لازم خوابیدی."
            h <= target.endInclusive + 0.5f -> "خوابت در محدوده مناسبه."
            else -> "بیشتر از حد معمول خوابیدی؛ نظم خوابت رو حفظ کن."
        }
    }

    // -------------------------------------------------------------- fatigue

    /** A rest suggestion derived from today's recorded energy and fatigue. */
    fun restAdvice(today: CheckIn?): String? {
        if (today == null) return null
        val lowEnergy = today.energyLevel.contains("کم")
        val heavyFatigue = today.fatigueSeverity.contains("زیاد")
        return when {
            lowEnergy && heavyFatigue ->
                "انرژی‌ات کم و خستگی‌ات زیاده؛ ۲۰ دقیقه دراز بکش و کارهای سبک انجام بده."
            lowEnergy || heavyFatigue ->
                "یک استراحت کوتاه ۱۰ تا ۱۵ دقیقه‌ای در میانه روز کمکت می‌کنه."
            today.energyLevel.contains("خیلی خوب") ->
                "انرژی‌ات خوبه؛ یک فعالیت سبک برای امروز مناسبه."
            else -> "امروز بیشتر استراحت کن."
        }
    }

    /** Suggested minutes of light activity for today. */
    fun activityMinutes(today: CheckIn?): Int? {
        if (today == null) return null
        val lowEnergy = today.energyLevel.contains("کم")
        val heavyFatigue = today.fatigueSeverity.contains("زیاد")
        return when {
            lowEnergy && heavyFatigue -> 10
            lowEnergy || heavyFatigue -> 20
            else -> 30
        }
    }

    // --------------------------------------------------------------- trends

    /** Average of the last [n] recorded values, ignoring missing ones. */
    fun average(values: List<Float>): Float? {
        val real = values.filter { it > 0f }
        return if (real.isEmpty()) null else real.average().toFloat()
    }

    fun averageInt(values: List<Int>): Float? {
        val real = values.filter { it > 0 }
        return if (real.isEmpty()) null else real.average().toFloat()
    }

    /**
     * Compares this week's average against last week's for one metric.
     * Returns a short phrase or null when there is not enough history.
     */
    fun weeklyTrend(values: List<Float>, label: String): String? {
        val recent = values.take(7).filter { it > 0f }
        val older = values.drop(7).take(7).filter { it > 0f }
        if (recent.size < 3 || older.size < 3) return null
        val diff = recent.average() - older.average()
        val sign = if (diff >= 0) "بهتر" else "کمتر"
        return "$label این هفته $sign از هفته قبل"
    }

    /**
     * The best and weakest recorded metric over the recent window, so the
     * dashboard can lead with something meaningful instead of raw numbers.
     */
    fun highlights(checkIns: List<CheckIn>): List<String> {
        if (checkIns.size < 3) return emptyList()
        val out = mutableListOf<String>()
        val water = averageInt(checkIns.take(7).map { it.waterGlasses })
        val sleep = average(checkIns.take(7).map { it.sleepHours })
        if (water != null && water < 5f) out += "میانگین آب این هفته کم بوده."
        if (sleep != null && sleep < 6.5f) out += "میانگین خواب این هفته کم بوده."
        if (water != null && water >= 7f) out += "آبت این هفته خوب بوده، ادامه بده."
        if (sleep != null && sleep >= 7f) out += "خوابت این هفته منظم بوده."
        return out.take(3)
    }

    // ------------------------------------------------------------ cycle info

    fun cycleDay(profile: Profile?): Int {
        val p = profile ?: return 0
        if (p.lastPeriodDate.isBlank()) return 0
        return Dates.cycleDay(p.lastPeriodDate, p.cycleLength)
    }

    fun daysUntilPeriod(profile: Profile?): Int? {
        val p = profile ?: return null
        if (p.lastPeriodDate.isBlank()) return null
        return Dates.daysUntilNextPeriod(p.lastPeriodDate, p.cycleLength)
    }

    /** Cycle phase name from the recorded length, used as a gentle context. */
    fun cyclePhase(profile: Profile?): String? {
        val day = cycleDay(profile)
        if (day <= 0) return null
        val len = profile?.cycleLength ?: 28
        val periodDays = profile?.periodDays ?: 5
        val ovulation = max(10, len - 14)
        return when {
            day <= periodDays -> "دوران قاعدگی"
            day < ovulation -> "دوران قبل از تخمک‌گذاری"
            day <= ovulation + 2 -> "حدود تخمک‌گذاری"
            else -> "دوران قبل از قاعدگی"
        }
    }

    // ------------------------------------------------------- medical safety

    /**
     * True when a suggestion touches something the user declared. Used to
     * suppress advice that could conflict with a stated condition.
     */
    fun mentionsCondition(profile: Profile?, words: List<String>): Boolean {
        val text = (profile?.medicalConditions ?: "") + " " + (profile?.medications ?: "")
        if (text.isBlank()) return false
        return words.any { text.contains(it) }
    }

    /** A cautious note when the user has declared any condition. */
    fun conditionNote(profile: Profile?): String? {
        val c = profile?.medicalConditions?.trim().orEmpty()
        return when {
            c.isBlank() -> null
            else -> "با توجه به شرایطی که ثبت کردی، این پیشنهادها عمومی‌اند و جای نظر پزشکت را نمی‌گیرند."
        }
    }
}

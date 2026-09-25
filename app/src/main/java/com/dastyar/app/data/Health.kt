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

    /**
     * The four cycle phases in plain Persian. The names are the ones a user
     * recognises, not the English clinical terms.
     */
    enum class CyclePhase(val title: String, val note: String) {
        MENSTRUAL("مرحله قاعدگی", "روزهای خونریزی؛ به بدنت استراحت و مراقبت بیشتری بده."),
        FOLLICULAR("مرحله فولیکولی", "بعد از قاعدگی تا نزدیکی تخمک‌گذاری؛ معمولاً انرژی دوباره بالا می‌رود."),
        OVULATION("مرحله تخمک‌گذاری", "در این بازه احتمال باروری بیشتر است."),
        LUTEAL("مرحله لوتئال", "بعد از تخمک‌گذاری تا شروع قاعدگی؛ ممکن است انرژی و خلق تغییر کند.")
    }

    /** Inclusive start day of the ovulation window, from the cycle length. */
    fun ovulationStart(cycleLength: Int): Int {
        val len = if (cycleLength in 15..60) cycleLength else 28
        return max(10, len - 16)
    }

    /** The current phase, or null when the cycle cannot be computed. */
    fun phase(profile: Profile?): CyclePhase? {
        val p = profile ?: return null
        if (p.lastPeriodDate.isBlank() || Dates.parse(p.lastPeriodDate) == null) return null
        val day = cycleDay(p)
        if (day <= 0) return null
        val len = if (p.cycleLength in 15..60) p.cycleLength else 28
        val periodDays = p.periodDays.coerceIn(1, 10)
        val ovStart = ovulationStart(len)
        return when {
            day <= periodDays -> CyclePhase.MENSTRUAL
            day < ovStart -> CyclePhase.FOLLICULAR
            day <= ovStart + 3 -> CyclePhase.OVULATION
            else -> CyclePhase.LUTEAL
        }
    }

    /**
     * A gentle, data-driven care tip for today's cycle day. The content changes
     * with the phase and with the user's own recorded state, so it is never the
     * same paragraph every day. Returns null when there is no cycle data.
     */
    fun cycleTip(profile: Profile?, today: CheckIn?): String? {
        val p = profile ?: return null
        val ph = phase(p) ?: return null
        val day = cycleDay(p)
        val periodDays = p.periodDays.coerceIn(1, 10)
        val len = if (p.cycleLength in 15..60) p.cycleLength else 28
        val ovStart = ovulationStart(len)
        return when (ph) {
            CyclePhase.MENSTRUAL -> {
                val left = (periodDays - day + 1).coerceAtLeast(1)
                "امروز روز ${Dates.fa(day)} قاعدگی تو است و حدود ${Dates.fa(left)} روز خونریزی باقی مانده. " +
                        "آب و مایعات کافی بنوش، غذاهای آهن‌دار مثل حبوبات و سبزی برگ‌سبز بخور، " +
                        "گرم بمان و کارهای سنگین را به روزهای پرانرژی‌تر بسپار. شکم را با کیسه آب گرم آرام کن."
            }
            CyclePhase.FOLLICULAR -> {
                val until = (ovStart - day).coerceAtLeast(0)
                if (until <= 2) {
                    "به آخرین روزهای مرحله فولیکولی رسیده‌ای و حدود ${Dates.fa(until)} روز تا شروع " +
                            "بازه تخمک‌گذاری مانده. این روزها معمولاً انرژی‌ات بالاتر است؛ " +
                            "فعالیت هوازی، پروتئین کافی و خواب منظم بهترین انتخاب‌های امروزند."
                } else {
                    "الان در مرحله فولیکولی هستی و بدن معمولاً پرانرژی‌تر است. " +
                            "این فرصت خوبی برای ورزش، یادگیری و کارهای سنگین‌تر است. " +
                            "پروتئین، سبزیجات و آب کافی هم به پایداری انرژی‌ات کمک می‌کند."
                }
            }
            CyclePhase.OVULATION -> {
                val fert = when {
                    (p.medicalConditions + " " + p.medications).contains("باردار") ||
                            p.medicalConditions.contains("نابارور") ->
                        "اگر در حال بررسی بارداری هستی، این بازه معمولاً از بازه‌های با احتمال باروری بالاتر است."
                    else ->
                        "این بازه معمولاً از بازه‌های با احتمال باروری بالا است. " +
                                "توجه کن که این محاسبه روش قطعی پیشگیری از بارداری نیست."
                }
                "امروز حدود تخمک‌گذاری تو است. $fert " +
                        "ممکن است ترشح بیشتر یا کمی درد یک‌طرفه حس کنی؛ طبیعی است. " +
                        "آب کافی بنوش و به تغییرات بدن خودت توجه کن."
            }
            CyclePhase.LUTEAL -> {
                val until = len - day
                "امروز مرحله لوتئال است و حدود ${Dates.fa(until.coerceAtLeast(0))} روز تا پریود بعدی مانده. " +
                        "ممکن است انرژی و خلق‌وخو نوسان کند، نوسان خلق و کمی ورم طبیعی است. " +
                        "خواب به‌موقع، کربوهیدرات پیچیده و کافئین کمتر در این روزها به پایداری حالت کمک می‌کند."
            }
        }
    }

    /** The four phase names in Persian, for a compact label row. */
    fun phaseLabel(profile: Profile?): String? = phase(profile)?.title

    /** The approximate date of the next period as a Jalali ISO string. */
    fun nextPeriodDate(profile: Profile?): String? {
        val p = profile ?: return null
        if (p.lastPeriodDate.isBlank()) return null
        val len = if (p.cycleLength in 15..60) p.cycleLength else 28
        val days = Dates.daysUntilNextPeriod(p.lastPeriodDate, len)
        if (days < 0) return null
        return Dates.plusDays(Dates.today(), days)
    }

    /**
     * True when the last two recorded cycles differ enough that a prediction
     * should be shown as approximate rather than certain.
     */
    fun cycleIsIrregular(profile: Profile?): Boolean {
        val p = profile ?: return false
        if (p.lastPeriodDate.isBlank()) return false
        return p.cycleLength !in 21..35
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

    // ----------------------------------------------------- questionnaire baseline

    /**
     * Converts the onboarding questionnaire into the user's Day-1 check-in.
     *
     * The questionnaire answers live on [Profile] under its own field names
     * (`skinType`, `acneLevel`, `dryOrOily`, …), while the dashboard reads the
     * check-in fields (`skinStatus`, `skinInflammation`, …). This is the single
     * place that maps one to the other, so the skin and fatigue cards show the
     * questionnaire answers on day one without asking the user to check in again.
     */
    fun fromQuestionnaire(p: Profile, date: String): CheckIn = CheckIn(
        date = date,
        energyLevel = when {
            p.fatigueLevel.contains("خیلی زیاد") -> "خیلی کم"
            p.fatigueLevel.contains("زیاد") -> "کم"
            p.fatigueLevel.contains("متوسط") -> "متوسط"
            p.fatigueLevel.contains("کم") -> "خوب"
            p.fatigueLevel.contains("خیلی کم") -> "خیلی خوب"
            else -> ""
        },
        fatigueSeverity = p.fatigueLevel,
        sleepHours = p.sleepHours,
        sleepQuality = p.sleepQuality,
        waterGlasses = p.waterIntake,
        stressLevel = p.stressLevel,
        appetite = p.appetite,
        physicalActivity = p.physicalActivity,
        skinStatus = skinStatusFromQuestionnaire(p),
        acneCount = p.acneLevel,
        skinInflammation = p.hasRedness,
        skinDryOily = p.dryOrOily,
        skinSensitivity = p.hasSensitivity,
        isPeriodDay = false,
        periodPain = if (p.periodPainLevel > 0) "دارم" else "",
        periodPainLevel = if (p.periodPainLevel > 0) p.periodPainLevel.toString() else "",
        periodPainLocation = p.painLocation,
        notes = "این اطلاعات از پرسشنامه اولیه (روز اول) ثبت شد."
    )

    /**
     * A baseline skin label from the questionnaire. Only derived when the user
     * actually answered a skin question; otherwise empty, so the dashboard can
     * honestly say nothing was recorded.
     */
    private fun skinStatusFromQuestionnaire(p: Profile): String {
        val hasAny = p.acneLevel.isNotBlank() || p.dryOrOily.isNotBlank() ||
                p.hasSensitivity.isNotBlank() || p.hasRedness.isNotBlank() ||
                p.skinType.isNotBlank()
        if (!hasAny) return ""
        return when {
            p.acneLevel.contains("زیاد") -> "وضعیت پایه: جوش زیاد"
            p.acneLevel.contains("متوسط") -> "وضعیت پایه: جوش متوسط"
            p.acneLevel.contains("کم") -> "وضعیت پایه: جوش کم"
            p.acneLevel.contains("ندارم") -> "وضعیت پایه: بدون جوش"
            else -> "وضعیت پایه ثبت شد"
        }
    }

    /**
     * True when there is any skin information at all — from today's check-in
     * or, failing that, from the questionnaire baseline in the profile. The
     * dashboard uses this so it never claims nothing was recorded while the
     * questionnaire actually has skin answers.
     */
    fun hasSkinData(profile: Profile?, today: CheckIn?): Boolean {
        if (today != null && (
                    today.skinStatus.isNotBlank() || today.acneCount.isNotBlank() ||
                            today.skinInflammation.isNotBlank() || today.skinDryOily.isNotBlank() ||
                            today.skinSensitivity.isNotBlank()
                    )
        ) return true
        val p = profile ?: return false
        return p.skinType.isNotBlank() || p.acneLevel.isNotBlank() || p.dryOrOily.isNotBlank() ||
                p.hasSensitivity.isNotBlank() || p.hasRedness.isNotBlank() || p.acneLocation.isNotBlank()
    }

    /**
     * A short Persian summary of the user's skin state, preferring today's
     * check-in and falling back to the questionnaire baseline. Returns null only
     * when there is genuinely no skin information.
     */
    fun skinSummary(profile: Profile?, today: CheckIn?): String? {
        if (!hasSkinData(profile, today)) return null
        val parts = mutableListOf<String>()
        val acne = today?.acneCount?.takeIf { it.isNotBlank() } ?: profile?.acneLevel.orEmpty()
        val type = profile?.skinType?.takeIf { it.isNotBlank() }.orEmpty()
        val dryOily = today?.skinDryOily?.takeIf { it.isNotBlank() } ?: profile?.dryOrOily.orEmpty()
        val sensitivity = today?.skinSensitivity?.takeIf { it.isNotBlank() } ?: profile?.hasSensitivity.orEmpty()
        val redness = today?.skinInflammation?.takeIf { it.isNotBlank() } ?: profile?.hasRedness.orEmpty()

        if (acne.isNotBlank() && !acne.startsWith("وضعیت پایه")) parts += "جوش $acne"
        if (type.isNotBlank()) parts += convertList(type)
        // Skip the dry/oily answer when it repeats the skin type's own wording
        // (e.g. skinType "چرب" and dryOrOily "چربی"), so the line never stutters.
        if (dryOily.isNotBlank() && dryOily != "هیچ‌کدام" && !repeatsType(dryOily, type)) parts += dryOily
        if (sensitivity.isNotBlank() && sensitivity != "ندارم") parts += "حساسیت $sensitivity"
        if (redness.isNotBlank() && redness != "ندارم") parts += "قرمزی $redness"
        if (parts.isEmpty()) return null
        return parts.joinToString("، ")
    }

    /** True when the dry/oily answer already says the same thing as the skin type. */
    private fun repeatsType(dryOily: String, type: String): Boolean {
        if (type.isBlank()) return false
        val t = type.replace("‌", "")
        val d = dryOily.replace("‌", "")
        return (t.contains("چرب") && d.contains("چرب")) || (t.contains("خشک") && d.contains("خشک"))
    }

    /** "خشک,چرب" -> "خشک و چرب" for a readable Persian phrase. */
    private fun convertList(joined: String): String {
        val items = joined.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return when (items.size) {
            0 -> ""
            1 -> items[0]
            else -> items.dropLast(1).joinToString("، ") + " و " + items.last()
        }
    }
}

package com.dastyar.app.ai

import com.dastyar.app.data.CheckIn
import com.dastyar.app.data.Dates
import com.dastyar.app.data.Health
import com.dastyar.app.data.Profile
import com.dastyar.app.data.SmartFact

/**
 * Builds system prompts from the user's real stored data, so every AI answer is
 * grounded in their profile, check-in history and smart profile.
 *
 * Two rules run through all of it: never invent a fact the user did not record,
 * and never give medical advice that could conflict with a declared condition.
 */
object Prompts {

    private val SAFETY = """
قواعد ایمنی که همیشه باید رعایت کنی:
- هرگز تشخیص قطعی پزشکی نده و بیماری را قطعی اعلام نکن.
- هرگز دارو تجویز نکن و دوز پیشنهاد نده.
- هرگز نگو جایگزین پزشک هستی.
- اگر علائم هشدار دیدی (خونریزی شدید، درد قفسه سینه، تنگی نفس شدید، غش، تب بالا،
  خونریزی غیرعادی، درد شدید و ناگهانی) کاربر را با لحنی آرام و مهربان به پزشک یا
  اورژانس راهنمایی کن.
- برای پوست، فقط مراقبت‌های ساده و بی‌خطر پیشنهاد بده. از توصیه‌های خانگی تحریک‌کننده
  مثل لیمو، جوش‌شیرین، خمیردندان، سیر یا سوزاندن روی پوست پرهیز کن.
- اگر کاربر بیماری یا دارویی اعلام کرده، پیشنهادت نباید با آن در تناقض باشد. در
  تناقض، پیشنهاد ایمن‌تر بده و کاربر را به پزشک ارجاع بده.
- هیچ اطلاعاتی از خودت نساز. فقط از داده‌های ثبت‌شده کاربر استفاده کن.
- همیشه فارسی، کوتاه، گرم و کاربردی جواب بده.
""".trimIndent()

    fun base(): String = SAFETY

    /** A compact, factual snapshot of everything known about the user. */
    private fun snapshot(profile: Profile?, today: CheckIn?, facts: List<SmartFact>): String {
        val sb = StringBuilder()

        if (profile != null) {
            sb.appendLine("— اطلاعات کاربر —")
            if (profile.firstName.isNotBlank()) sb.appendLine("نام: ${profile.firstName}")
            if (profile.age > 0) sb.appendLine("سن: ${profile.age}")

            if (profile.heightCm > 0) sb.appendLine("قد: ${profile.heightCm} سانتی‌متر")
            if (profile.weightKg > 0f) sb.appendLine("وزن: ${profile.weightKg} کیلوگرم")
            if (profile.targetWeightKg > 0f) sb.appendLine("وزن هدف: ${profile.targetWeightKg} کیلوگرم")
            val bmi = Health.bmi(profile)
            if (bmi != null && Health.bmiAdultBandsApply(profile)) {
                sb.appendLine("شاخص توده بدنی: ${"%.1f".format(bmi)} (${Health.bmiCategory(profile)} — فقط شاخص آماری، نه تشخیص)")
            }
            if (profile.medicalConditions.isNotBlank())
                sb.appendLine("شرایط پزشکی اعلام‌شده توسط خودِ کاربر: ${profile.medicalConditions}")
            if (profile.medications.isNotBlank())
                sb.appendLine("داروهای اعلام‌شده: ${profile.medications}")
            sb.appendLine()

            if (profile.lastPeriodDate.isNotBlank()) {
                val cd = Dates.cycleDay(profile.lastPeriodDate, profile.cycleLength)
                val until = Dates.daysUntilNextPeriod(profile.lastPeriodDate, profile.cycleLength)
                sb.appendLine("— چرخه پریود —")
                sb.appendLine("طول چرخه: ${profile.cycleLength} روز، طول پریود: ${profile.periodDays} روز")
                sb.appendLine("امروز روز $cd چرخه است. $until روز تا پریود بعدی مانده.")
                Health.phase(profile)?.let { sb.appendLine("مرحله تقریبی: ${it.title}") }
                if (profile.periodPainLevel > 0) sb.appendLine("شدت معمول درد: ${profile.periodPainLevel} از ۱۰")
                if (profile.painLocation.isNotBlank()) sb.appendLine("محل معمول درد: ${profile.painLocation}")
                if (profile.painRelief.isNotBlank()) sb.appendLine("روش کنترل درد: ${profile.painRelief}")
                sb.appendLine()
            }

            if (profile.skinType.isNotBlank() || profile.acneLevel.isNotBlank()) {
                sb.appendLine("— پوست —")
                if (profile.skinType.isNotBlank()) sb.appendLine("نوع پوست: ${profile.skinType}")
                if (profile.acneLevel.isNotBlank()) sb.appendLine("میزان جوش: ${profile.acneLevel}")
                if (profile.acneLocation.isNotBlank()) sb.appendLine("محل جوش: ${profile.acneLocation}")
                if (profile.currentProducts.isNotBlank()) sb.appendLine("محصولات فعلی: ${profile.currentProducts}")
                sb.appendLine()
            }

            if (profile.fatigueLevel.isNotBlank() || profile.sleepQuality.isNotBlank()) {
                sb.appendLine("— بی‌رمقی —")
                if (profile.fatigueLevel.isNotBlank()) sb.appendLine("شدت بی‌رمقی: ${profile.fatigueLevel}")
                if (profile.sleepQuality.isNotBlank()) sb.appendLine("کیفیت معمول خواب: ${profile.sleepQuality}")
                if (profile.sleepHours > 0f) sb.appendLine("خواب معمول: ${profile.sleepHours} ساعت")
                if (profile.stressLevel.isNotBlank()) sb.appendLine("سطح استرس: ${profile.stressLevel}")
                if (profile.physicalActivity.isNotBlank()) sb.appendLine("فعالیت بدنی: ${profile.physicalActivity}")
                sb.appendLine()
            }
        }

        if (today != null) {
            sb.appendLine("— وضعیت امروز (${Dates.pretty(today.date)}) —")
            if (today.energyLevel.isNotBlank()) sb.appendLine("انرژی: ${today.energyLevel}")
            if (today.fatigueSeverity.isNotBlank()) sb.appendLine("شدت خستگی: ${today.fatigueSeverity}")
            if (today.sleepHours > 0) sb.appendLine("خواب: ${today.sleepHours} ساعت (${today.sleepQuality})")
            sb.appendLine("آب امروز: ${today.waterGlasses} لیوان")
            if (today.skinStatus.isNotBlank()) sb.appendLine("پوست: ${today.skinStatus}")
            if (today.stressLevel.isNotBlank()) sb.appendLine("استرس امروز: ${today.stressLevel}")
            if (today.isPeriodDay) sb.appendLine("امروز روز پریود است.")
            sb.appendLine()
        }

        if (facts.isNotEmpty()) {
            sb.appendLine("— الگوهای آموخته‌شده از گفتگوهای قبلی کاربر —")
            facts.take(12).forEach { sb.appendLine("${it.key}: ${it.value}") }
            sb.appendLine("این‌ها فقط الگوهای قابل‌مشاهده‌اند؛ از آن‌ها برای حدس درباره شخصیت یا " +
                    "وضعیت روانی استفاده نکن و نظر قطعی نده.")
            sb.appendLine()
        }

        return sb.toString()
    }

    fun system(
        channel: String,
        profile: Profile?,
        today: CheckIn?,
        facts: List<SmartFact> = emptyList()
    ): String {
        val sb = StringBuilder()
        sb.appendLine("تو «دستیار من» هستی؛ یک دستیار شخصی روزانه فارسی‌زبان، مهربان و جمع‌وجور.")
        sb.appendLine("همیشه فارسی و راست‌به‌چپ جواب بده. جواب‌ها کوتاه و کاربردی باشند.")
        if (profile?.firstName?.isNotBlank() == true) {
            sb.appendLine("در چت با نام کوچک کاربر او را خطاب کن.")
        }
        sb.appendLine()
        sb.append(snapshot(profile, today, facts))

        sb.appendLine(
            when (channel) {
                "period" -> "— نقش فعلی —\nتو در بخش «مراقبت از پریود» هستی. فقط درباره چرخه، درد، " +
                        "خونریزی، تغذیه، استراحت و مراقبت‌های عمومی پریود راهنمایی کن."
                "skin" -> "— نقش فعلی —\nتو در بخش «مراقبت از پوست» هستی. فقط درباره روتین ساده پوست، " +
                        "پاک‌سازی، مرطوب‌سازی و محافظت از آفتاب راهنمایی کن."
                "fatigue" -> "— نقش فعلی —\nتو در بخش «بی‌رمقی و انرژی» هستی. فقط درباره خواب، آب، " +
                        "تغذیه، فعالیت سبک و مدیریت استرس راهنمایی کن."
                "lunch" -> "— نقش فعلی —\nتو در بخش «پیشنهاد ناهار» هستی. فقط دربارهٔ غذای ناهار " +
                        "ایرانی و خانگی پیشنهاد بده و به سؤال‌های کاربر دربارهٔ همان غذاها جواب بده."
                "dinner" -> "— نقش فعلی —\nتو در بخش «پیشنهاد شام» هستی. فقط دربارهٔ غذای شام " +
                        "ایرانی و خانگی (سبک‌تر و ساده‌تر) پیشنهاد بده و به سؤال‌های کاربر جواب بده."
                else -> "— نقش فعلی —\nتو دستیار عمومی کاربری. به هر موضوعی که پرسید کمک کن."
            }
        )
        sb.appendLine()
        // The two cooking chats always answer as a recipe, using the same
        // marker format the cooking screen parses into cards.
        if (channel == "lunch" || channel == "dinner") {
            sb.appendLine(COOKING_RULES)
            sb.appendLine()
        }
        sb.append(SAFETY)
        return sb.toString()
    }

    /**
     * Prompt for the daily suggestion. The model is asked for short, practical,
     * personalised lines. It must not invent numbers the app already computed
     * locally — the goals are passed in as fixed facts.
     */
    fun dailySuggestionPrompt(
        profile: Profile?,
        today: CheckIn?,
        history: List<CheckIn>,
        facts: List<SmartFact>,
        waterGoal: Int
    ): String {
        val name = profile?.firstName?.ifBlank { "دوست من" } ?: "دوست من"
        val sleepTarget = Health.sleepTargetHours(profile)
        val info = StringBuilder()
        info.append("نام: $name. ")
        if (today != null) {
            info.append("امروز: انرژی ${today.energyLevel.ifBlank { "ثبت نشده" }}، ")
            info.append("خواب ${today.sleepHours} ساعت (${today.sleepQuality.ifBlank { "-" }})، ")
            info.append("آب ${today.waterGlasses} از $waterGoal لیوان، ")
            info.append("پوست ${today.skinStatus.ifBlank { "ثبت نشده" }}، ")
            info.append("استرس ${today.stressLevel.ifBlank { "-" }}. ")
            if (today.isPeriodDay) info.append("امروز روز پریود است. ")
            if (today.fatigueSeverity.isNotBlank()) info.append("خستگی ${today.fatigueSeverity}. ")
        } else {
            info.append("امروز چک‌این ثبت نشده. ")
        }
        if (history.isNotEmpty()) {
            val avgSleep = Health.average(history.take(7).map { it.sleepHours })
            val avgWater = Health.averageInt(history.take(7).map { it.waterGlasses })
            if (avgSleep != null) info.append("میانگین خواب ۷ روز اخیر: ${"%.1f".format(avgSleep)} ساعت. ")
            if (avgWater != null) info.append("میانگین آب ۷ روز اخیر: ${"%.1f".format(avgWater)} لیوان. ")
        }
        if (profile?.lastPeriodDate?.isNotBlank() == true) {
            val day = Dates.cycleDay(profile.lastPeriodDate, profile.cycleLength)
            info.append("روز ${day} چرخه. ")
            Health.phase(profile)?.let { info.append("مرحله چرخه: ${it.title}. ") }
            Health.daysUntilPeriod(profile)?.let { info.append("$it روز تا پریود بعدی. ") }
        }
        // Yesterday's real record, so the plan can react to the direction of
        // change instead of repeating the same advice.
        val previous = history.filter { it.date != (today?.date ?: Dates.today()) }.maxByOrNull { it.date }
        if (previous != null) {
            info.append("روز قبل: انرژی ${previous.energyLevel.ifBlank { "-" }}، ")
            info.append("خواب ${previous.sleepHours} ساعت، آب ${previous.waterGlasses} لیوان. ")
        }
        val conditions = profile?.medicalConditions?.trim().orEmpty()
        if (conditions.isNotBlank()) info.append("شرایط پزشکی ثبت‌شده: $conditions. ")
        val meds = profile?.medications?.trim().orEmpty()
        if (meds.isNotBlank()) info.append("داروهای ثبت‌شده: $meds. ")
        if (profile?.skinType?.isNotBlank() == true) info.append("نوع پوست ${profile.skinType}. ")
        if (profile?.acneLevel?.isNotBlank() == true) info.append("میزان جوش ${profile.acneLevel}. ")
        if (profile?.weightKg?.let { it > 0f } == true) info.append("وزن ${profile.weightKg} کیلوگرم. ")
        if (profile?.targetWeightKg?.let { it > 0f } == true) info.append("وزن هدف ${profile.targetWeightKg} کیلوگرم. ")
        if (profile?.age?.let { it > 0 } == true) info.append("سن ${profile.age} سال. ")

        // Today's fuller picture, so the lines can respond to fatigue and stress.
        if (today != null) {
            if (today.fatigueSeverity.isNotBlank()) info.append("شدت بی‌رمقی امروز ${today.fatigueSeverity}. ")
            if (today.sleepQuality.isNotBlank()) info.append("کیفیت خواب ${today.sleepQuality}. ")
            if (today.stressLevel.isNotBlank()) info.append("استرس امروز ${today.stressLevel}. ")
            if (today.skinInflammation.isNotBlank()) info.append("التهاب پوست ${today.skinInflammation}. ")
        }

        if (facts.isNotEmpty()) {
            info.append("الگوهای آموخته‌شده: ")
            facts.take(8).forEach { info.append("${it.key}=${it.value}؛ ") }
        }

        return """
بر اساس وضعیت واقعی امروز کاربر، حداکثر ۵ پیشنهاد کوتاه و عملی و شخصی بده.
اطلاعات کاربر: $info

اهداف محاسبه‌شده توسط خودِ برنامه (این اعداد قطعی‌اند و باید همین‌ها را بگویی):
- هدف آب امروز: $waterGoal لیوان
- محدوده مناسب خواب: ${"%.0f".format(sleepTarget.start)} تا ${"%.0f".format(sleepTarget.endInclusive)} ساعت

خروجی را دقیقاً با این قالب بده و هر پیشنهاد در یک خط باشد، بدون مقدمه و بدون جمع‌بندی.
هر خط با «موضوع | متن» نوشته شود، مثل:
💧 آب | هدف امروز $waterGoal لیوان است؛ تا الان چند لیوان خوردی
😴 خواب | بین ${"%.0f".format(sleepTarget.start)} تا ${"%.0f".format(sleepTarget.endInclusive)} ساعت بخواب
🛋 استراحت | یک جمله کوتاه و شخصی بر اساس خستگی امروز
🚶 فعالیت | یک جمله کوتاه درباره فعالیت سبک مناسب امروز
🧘 آرامش | یک جمله کوتاه اگر استرس امروز زیاد بود (وگرنه این خط را ننویس)
✨ پوست | یک جمله کوتاه بر اساس وضعیت پوست امروز
📈 روند | یک جمله کوتاه از مقایسه امروز با روز قبل (بهتر یا بدتر)، فقط اگر واقعاً تفاوت هست
🌸 چرخه | یک جمله متناسب با مرحله فعلی چرخه (اگر داده چرخه نیست ننویس)
🩺 شرایط | یک جمله مراقبتی ایمن با توجه به شرایط اعلام‌شده (اگر شرایطی نیست ننویس)

قواعد:
- بین ۴ تا ۶ خط، فقط همان‌هایی که برای کاربر مرتبط است. اگر موضوعی مرتبط نیست، آن خط را کلاً ننویس.
- متن هر روز باید بر اساس داده‌های همان روز متفاوت باشد؛ جمله‌های کلی و تکراری ننویس.
- اگر وضعیت امروز بهتر از روز قبل است، لحن تشویقی و اگر بدتر است، لحن محتاطانه و حمایتی بده.
- شرایط پزشکی را فقط وقتی در نظر بگیر که ارتباط منطقی با خط مربوطه وجود دارد؛ بی‌دلیل به آن اشاره نکن.
- هر جمله حداکثر ۱۵ کلمه، محاوره‌ای و بدون تکرار.
- از داده‌ای که به تو داده نشده حرف نزن و عدد جدید از خودت نساز.
- اگر شرایط پزشکی اعلام شده، پیشنهادت با آن در تناقض نباشد؛ در تناقض پیشنهاد ایمن‌تر بده.
- هرگز تشخیص پزشکی نده، علت قطعی علائم را اعلام نکن، دارو تجویز نکن و درمان شخصی قطعی پیشنهاد نده.
- در صورت وجود علامت هشدار، کاربر را به پزشک ارجاع بده.
""".trimIndent()
    }

    /** Prompt for the smart cooking tab. */
    fun cookingPrompt(ingredients: String, mealType: String): String = """
مواد موجود در خانه: $ingredients
دسته‌بندی مورد نظر: $mealType
سه پیشنهاد غذا بده. برای هر غذا دقیقاً این ساختار را بنویس:

🍽 [نام غذا]
⏱ زمان آماده‌سازی: [مقدار]
📊 سختی: [آسان/متوسط/سخت]
🧺 مواد لازم: [با مقدار]
👩‍🍳 دستور: [مراحل شماره‌دار، کوتاه]

فقط با مواد ذکرشده یا مواد ساده و رایج آشپزخانه بساز. فارسی و جمع‌وجور بنویس.
""".trimIndent()

    /**
     * Shared rules for the cooking section. The output format is exact and
     * marker-based so the app can split dishes and render each field reliably.
     * Everything is grounded in ordinary Iranian home cooking.
     */
    private val COOKING_RULES = """
تو یک آشپز ایرانی باتجربه و صمیمی هستی. هدف: غذای واقعی و قابل پخت در خانه.

قواعد:
- فقط غذای ایرانی/خانگی/معمولی و ارزان پیشنهاد بده؛ نه رستورانی، نه لاکچری، نه
  مواد اولیه کمیاب یا گران.
- مواد اولیه باید ساده و در دسترس باشند (برنج، حبوبات، گوشت ارزان، تخم‌مرغ، سیب‌زمینی،
  پیاز، گوجه، ماکارونی، نان...).
- پیشنهادها متنوع باشند و غذای تکراری نده.
- بدون ادعای پزشکی، رژیمی یا درمانی؛ فقط دستور پخت.
- فارسی، کوتاه و روان بنویس. عددها فارسی باشند.

قالب پاسخ را دقیقاً و بدون هیچ متن اضافه رعایت کن. بین غذاها یک خط «###» بگذار.
هیچ توضیحی درباره نحوهٔ نوشتن، قالب یا دستورهایت ننویس؛ فقط خودِ غذا.
برای هر غذا این بلوک را بنویس (هر فیلد در یک خط جدا):

###
نام: [نام غذا]
معرفی: [یک جمله کوتاه درباره غذا]
نفرات: [تعداد نفر]
آماده‌سازی: [مقدار دقیقه]
پخت: [مقدار دقیقه]
مواد:
- [ماده]: [مقدار دقیق]
- [ماده]: [مقدار دقیق]
مراحل:
۱. [مرحله کامل]
۲. [مرحله کامل]
۳. [مرحله کامل]
نکات: [یک یا دو نکته مهم پخت]
جایگزین: [اگر ماده‌ای را می‌توان با چیز دیگری جایگزین کرد، بنویس؛ وگرنه این خط را ننویس]
""".trimIndent()

    /** Lunch/dinner suggestion, optionally shaped by filters and serving count. */
    fun mealSuggestPrompt(meal: String, servings: Int, filters: List<String>): String {
        val filterLine =
            if (filters.isEmpty()) "فیلتر خاصی نیست."
            else "فیلترها: ${filters.joinToString("، ")}"
        return """
$COOKING_RULES

وعده: $meal
تعداد نفرات: $servings
$filterLine

فقط یک پیشنهاد بده (یک بلوک کامل). مقدار مواد را برای $servings نفر بنویس.
همهٔ فیلدها را کامل بنویس؛ پاسخ باید کامل و تمام‌شده باشد.
""".trimIndent()
    }

    /** "What should I cook today?" — a couple of all-day ideas. */
    fun todaySuggestPrompt(servings: Int, filters: List<String>): String {
        val filterLine =
            if (filters.isEmpty()) "فیلتر خاصی نیست."
            else "فیلترها: ${filters.joinToString("، ")}"
        return """
$COOKING_RULES

کاربر پرسیده «امروز چی بپزم؟». یک غذای خانگی معمولی پیشنهاد بده.
تعداد نفرات: $servings
$filterLine
مقدار مواد را برای $servings نفر بنویس.
فقط یک بلوک کامل بده و همهٔ فیلدها را کامل بنویس.
""".trimIndent()
    }

    /** Suggest dishes from what the user already has at home. */
    fun pantrySuggestPrompt(ingredients: String, servings: Int): String = """
$COOKING_RULES

اینها مواد موجود کاربر است: $ingredients
تعداد نفرات: $servings

یک غذا پیشنهاد بده که بیشترین مقدار این مواد را استفاده کند. اگر غذا به یک یا دو
ماده جزئی نیاز دارد، در انتهای همان بلوک یک خط بنویس:
کمبود: [فقط همان مواد جزئی]
اگر مواد اصلی غذا موجود نیست، آن غذا را پیشنهاد نده. مقدار مواد را برای $servings نفر بنویس.
فقط یک بلوک کامل بده و همهٔ فیلدها را کامل بنویس.
""".trimIndent()

    /** Prompt used to expand a short edit instruction into a full image prompt. */
    fun imageEditPrompt(instruction: String, originalPrompt: String): String = """
تو یک متخصص پرامپت‌نویسی تصویر هستی. کاربر می‌خواهد تصویری را ویرایش کند.
پرامپت اصلی تصویر: $originalPrompt
دستور ویرایش کاربر: $instruction
یک پرامپت انگلیسی کامل و دقیق بنویس که تصویر جدید را توصیف کند و تغییر خواسته‌شده را اعمال کند.
فقط خود پرامپت انگلیسی را برگردان، بدون توضیح اضافه و بدون گیومه.
""".trimIndent()

    /**
     * Prompt that extracts a few non-sensitive, observable patterns from the
     * user's own messages. Deliberately narrow: habits and preferences only,
     * never personality or mental-state guesses.
     */
    fun learnPrompt(recentUserMessages: String, existing: List<SmartFact>): String {
        val known = if (existing.isEmpty()) "هیچ"
        else existing.take(12).joinToString("؛ ") { "${it.key}=${it.value}" }
        return """
این پیام‌های اخیر کاربر است:
$recentUserMessages

الگوهای قبلاً ذخیره‌شده: $known

فقط الگوهای قابل‌مشاهده و عادت‌های ساده را استخراج کن، مثل:
عادت خواب، ساعت پرانرژی بودن، علاقه غذایی، حساسیت غذایی اعلام‌شده، عادت ورزش،
ترجیح سبک پاسخ (کوتاه یا مفصل)، هدف اعلام‌شده (مثل کاهش وزن)، مشکل تکرارشونده.

قواعد سخت:
- درباره شخصیت، اختلال روانی، افسردگی، اضطراب یا وضعیت روحی هیچ نتیجه‌ای نگیر.
- هیچ بیماری تشخیص نده و هیچ دارویی پیشنهاد نکن.
- فقط چیزی را بنویس که کاربر صریحاً گفته یا در داده‌ها آمده. حدس نزن.
- اگر چیز جدیدی نیست، فقط بنویس: هیچ

خروجی، هر خط یک مورد، دقیقاً به این شکل بدون شماره و بدون توضیح:
کلید | مقدار
مثال:
عادت خواب | معمولاً دیر می‌خوابد
ترجیح پاسخ | پاسخ کوتاه دوست دارد
هدف | کاهش وزن

حداکثر ۳ خط. کلیدها کوتاه و فارسی باشند.
""".trimIndent()
    }

    /**
     * Prompt for the dashboard card that explains the user's declared condition.
     * A rotating topic is passed in so the card shows something new and useful on
     * each visit instead of a fixed repeated paragraph. It explains a condition
     * the user already told us about; it never diagnoses and never replaces the
     * doctor.
     */
    fun conditionPrompt(
        conditions: String,
        medications: String,
        profile: Profile?,
        topic: String = "اطلاعات عمومی",
        today: CheckIn? = null
    ): String {
        val who = StringBuilder()
        if (profile?.age?.let { it > 0 } == true) who.append("سن کاربر: ${profile.age}. ")
        if (profile?.weightKg?.let { it > 0f } == true) who.append("وزن: ${profile.weightKg} کیلوگرم. ")

        // Today's own recorded state, so the note can connect to daily life only
        // when there is a real, logical link.
        val link = StringBuilder()
        today?.let { ci ->
            if (ci.sleepHours > 0f) link.append("خواب دیشب: ${ci.sleepHours} ساعت. ")
            if (ci.waterGlasses > 0) link.append("آب امروز: ${ci.waterGlasses} لیوان. ")
            if (ci.energyLevel.isNotBlank()) link.append("انرژی امروز: ${ci.energyLevel}. ")
            if (ci.fatigueSeverity.isNotBlank()) link.append("بی‌رمقی امروز: ${ci.fatigueSeverity}. ")
            if (ci.stressLevel.isNotBlank()) link.append("استرس امروز: ${ci.stressLevel}. ")
            if (ci.skinStatus.isNotBlank()) link.append("وضعیت پوست امروز: ${ci.skinStatus}. ")
            if (ci.physicalActivity.isNotBlank()) link.append("فعالیت امروز: ${ci.physicalActivity}. ")
        }
        if (profile?.lastPeriodDate?.isNotBlank() == true) {
            Health.phase(profile)?.let { link.append("مرحله چرخه: ${it.title}. ") }
        }

        return """
کاربر در پروفایل خود این شرایط را اعلام کرده است:
«$conditions»
داروهای اعلام‌شده: ${medications.ifBlank { "هیچ" }}
$who
وضعیت امروز کاربر: ${link.toString().ifBlank { "ثبت نشده" }}

امروز فقط روی این موضوع تمرکز کن: «$topic»

خروجی را دقیقاً در دو بخش بنویس:

💡 $topic | دو تا سه جمله کوتاه، دقیق و مفید مخصوصاً درباره همین موضوع
👩‍⚕️ نکته مهم | یک جمله درباره علامت هشدار یا زمان مراجعه به پزشک

قواعد:
- درباره همین موضوع بنویس و آن را تکرار موضوعات قبلی نکن؛ اطلاعات تازه و مفید بده.
- اگر و فقط اگر ارتباط منطقی با وضعیت امروز کاربر وجود دارد، یک اشاره کوتاه به آن بکن.
  بی‌دلیل به خواب، آب، تغذیه، انرژی، بی‌رمقی، پوست، چرخه یا فعالیت اشاره نکن.
- این توضیح آموزشی است، نه تشخیص. هیچ‌جا نگو کاربر قطعاً این بیماری را دارد.
- علت قطعی علائم را اعلام نکن و ادعا نکن این شرایط علت یک علامت خاص است.
- هیچ دارو یا دوزی تجویز نکن و درمان شخصی قطعی پیشنهاد نده.
- اطلاعات درمانی فقط در سطح عمومی و شناخته‌شده بمان؛ جزئیات درمان را به پزشک واگذار کن.
- فارسی، ساده، گرم و بدون ترس‌آفرینی بنویس. حداکثر ۴ خط.
""".trimIndent()
    }
    /** The rotating topics the condition card cycles through. */
    val conditionTopics = listOf(
        "اطلاعات عمومی",
        "مراقبت روزانه",
        "سبک زندگی و تغذیه",
        "علائم مهم",
        "زمان مراجعه به پزشک",
        "فعالیت و ورزش",
        "خواب و استراحت",
        "روش‌های درمانی شناخته‌شده"
    )

    /**
     * Prompt for the dashboard skin card. It is given the already-computed skin
     * summary (from today's check-in or the questionnaire baseline), so the
     * suggestion is grounded in the user's real answers and stays safe.
     */
    fun skinTipPrompt(profile: Profile?, today: CheckIn?, summary: String): String {
        val extra = StringBuilder()
        if (today != null) {
            if (today.sleepHours > 0f) extra.append("خواب دیشب: ${today.sleepHours} ساعت. ")
            if (today.waterGlasses > 0) extra.append("آب امروز: ${today.waterGlasses} لیوان. ")
            if (today.stressLevel.isNotBlank()) extra.append("استرس امروز: ${today.stressLevel}. ")
            if (today.skinNewProduct.isNotBlank()) extra.append("محصول جدید: ${today.skinNewProduct}. ")
        }
        if (profile?.currentProducts?.isNotBlank() == true) {
            extra.append("محصولات فعلی کاربر: ${profile.currentProducts}. ")
        }
        val conditions = profile?.medicalConditions?.trim().orEmpty()
        if (conditions.isNotBlank()) extra.append("شرایط پزشکی ثبت‌شده: «$conditions». ")

        return """
وضعیت پوست ثبت‌شده کاربر: $summary
$extra

یک پیشنهاد امروزِ کوتاه و مراقبتی برای همین وضعیت پوست بنویس.
خروجی فقط یک تا دو جملهٔ روان باشد، بدون تیتر و بدون «پیشنهاد امروز» و بدون خط جدید.

قواعد:
- پیشنهاد متناسب با همین اطلاعات باشد، نه یک متن ثابت و تکراری.
- فقط مراقبت‌های ساده و بی‌خطر بده: شست‌وشوی ملایم، مرطوب‌کننده، ضدآفتاب،
  دست‌نزدن به جوش‌ها و کاهش محصولات تحریک‌کننده.
- از توصیه‌های خانگی تحریک‌کننده مثل لیمو، جوش‌شیرین، خمیردندان، سیر یا سوزاندن روی پوست پرهیز کن.
- اگر شرایط پزشکی یا محصول جدیدی هست که به پوست مربوط است، کوتاه به آن اشاره کن.
- اگر نشانه‌های هشدار پوستی جدی وجود دارد، آرام به پزشک پوست ارجاع بده.
- تشخیص نده، دارو تجویز نکن و درمان قطعی پیشنهاد نده.
- فارسی، ساده، گرم و کوتاه.
""".trimIndent()
    }

    /**
     * Prompt for the wide dashboard card that gives a care tip for the user's
     * current cycle day. It is built entirely from the user's own cycle data and
     * today's check-in. It never diagnoses and never promises a certain outcome.
     */
    fun cycleTipPrompt(
        profile: Profile?,
        today: CheckIn?,
        day: Int,
        phase: String,
        daysUntil: Int?,
        irregular: Boolean
    ): String {
        val state = StringBuilder()
        if (profile?.age?.let { it > 0 } == true) state.append("سن: ${profile.age}. ")
        if (profile?.cycleLength?.let { it in 15..60 } == true) {
            state.append("طول معمول چرخه: ${profile.cycleLength} روز. ")
        }
        if (profile?.periodDays?.let { it > 0 } == true) {
            state.append("مدت معمول پریود: ${profile.periodDays} روز. ")
        }
        state.append("روز فعلی چرخه: $day. ")
        state.append("مرحله فعلی: $phase. ")
        if (daysUntil != null) state.append("تخمین روز تا پریود بعدی: $daysUntil. ")
        if (irregular) state.append("چرخه کاربر نامنظم گزارش شده است. ")
        today?.let { ci ->
            if (ci.energyLevel.isNotBlank()) state.append("انرژی امروز: ${ci.energyLevel}. ")
            if (ci.sleepHours > 0f) state.append("خواب دیشب: ${ci.sleepHours} ساعت. ")
            if (ci.fatigueSeverity.isNotBlank()) state.append("بی‌رمقی امروز: ${ci.fatigueSeverity}. ")
            if (ci.stressLevel.isNotBlank()) state.append("استرس امروز: ${ci.stressLevel}. ")
            if (ci.isPeriodDay) state.append("امروز را روز پریود ثبت کرده. ")
        }
        val conditions = profile?.medicalConditions?.trim().orEmpty()
        if (conditions.isNotBlank()) state.append("شرایط پزشکی ثبت‌شده: «$conditions». ")

        return """
این اطلاعات واقعی چرخه کاربر است:
$state

یک پیشنهاد مراقبتی کوتاه و امروزی برای همین روز از چرخه بنویس.
خروجی فقط دو تا سه جملهٔ روان و گرم باشد، بدون تیتر و بدون خط جدید اضافه.

قواعد:
- محتوای پیشنهاد را متناسب با همین روز و همین مرحله بنویس، نه یک متن تکراری برای همه روزها.
- اگر در حوالی تخمک‌گذاری است، بگو این بازه معمولاً احتمال باروری بیشتری دارد؛ و صریح بگو این محاسبه روش قطعی پیشگیری از بارداری نیست.
- اگر چرخه نامنظم است، عددها را قطعی نگو و از عبارت «تقریبی» استفاده کن.
- این متن آموزشی و مراقبتی است، نه تشخیص یا درمان. دارو یا دوز تجویز نکن.
- اگر شرایط پزشکی ثبت‌شده به این موضوع مربوط است، کوتاه به آن اشاره کن؛ در غیر این صورت به آن اشاره نکن.
- فقط فارسی، ساده و بدون ترس‌آفرینی. حداکثر ۳ جمله.
""".trimIndent()
    }

    /**
     * Prompt for the one-sentence explanation of the learned cycle prediction
     * shown on the dashboard ring. Every number here was computed locally by
     * [com.dastyar.app.data.Cycle] from the user's own recorded periods; the
     * model may only phrase it warmly. It must never invent a different date,
     * diagnose, or present an estimate as a certainty.
     */
    fun cyclePredictionPrompt(
        day: Int,
        length: Int,
        periodDays: Int,
        samples: Int,
        overdueDays: Int,
        irregular: Boolean,
        nextDate: String?
    ): String {
        val state = StringBuilder()
        state.append("روز فعلی چرخه: $day. ")
        state.append("طول چرخه آموخته‌شده: $length روز. ")
        state.append("مدت پریود آموخته‌شده: $periodDays روز. ")
        state.append("تعداد چرخه‌های واقعی ثبت‌شده: $samples. ")
        if (nextDate != null && nextDate.isNotBlank()) {
            state.append("تاریخ تخمینی پریود بعدی (میلادی/شمسی ذخیره‌شده): $nextDate. ")
        }
        if (overdueDays > 0) state.append("چرخه $overdueDays روز از موعد گذشته است. ")
        if (irregular) state.append("چرخه کاربر نامنظم است. ")

        return """
این اطلاعات واقعی چرخه کاربر است:
$state

در یک تا دو جملهٔ کوتاه و گرم، وضعیت فعلی چرخه را برای کاربر توضیح بده.

قواعد:
- فقط از همین اعداد استفاده کن؛ هیچ تاریخ یا عدد جدیدی از خودت نساز.
- اگر چرخه از موعد گذشته، صریح و بدون نگران‌کردن بگو که هر وقت پریود شروع شد باید «شروع پریود» را بزند.
- اگر تعداد چرخه‌های ثبت‌شده کم است یا چرخه نامنظم است، از عبارت «تقریبی» استفاده کن و عدد را قطعی نگو.
- تشخیص پزشکی نده، دارو تجویز نکن، و از ترس‌آفرینی پرهیز کن.
- فقط فارسی، ساده، حداکثر ۲ جمله، بدون تیتر و بدون خط جدید اضافه.
""".trimIndent()
    }

    /**
     * Turns one ordinary, colloquial Persian sentence into structured task
     * fields. It is the only AI used by the tasks section. The model must not
     * invent a date or time: when the user did not say one, it answers with an
     * empty string and the app asks for it. "today"/"now" are given as real
     * device values so relative words are resolved against the user's own clock.
     *
     * The reply is strict JSON so the app can read each field without guessing.
     */
    fun taskExtractPrompt(
        sentence: String,
        todayIso: String,
        todayPretty: String,
        weekday: String,
        nowTime: String
    ): String = """
تو فقط یک استخراج‌کنندهٔ اطلاعات کار هستی. یک جملهٔ خودمانی فارسی از کاربر می‌گیری و
همان را به فیلدهای ساختاریافته تبدیل می‌کنی. توضیح، تعارف یا جملهٔ اضافه ننویس.

وضعیت واقعی دستگاه همین حالا:
- تاریخ امروز (شمسی): $todayIso
- تاریخ امروز به فارسی: $todayPretty
- نام روز هفته: $weekday
- ساعت الان: $nowTime

جملهٔ کاربر: «$sentence»

خروجی را فقط و فقط یک شیء JSON معتبر و بدون هیچ متنی بیرون از آن بده، با این کلیدها:
{
  "title": "عنوان کوتاه و روشن کار",
  "description": "توضیح کوتاه اختیاری، اگر کاربر نکته‌ای گفته؛ وگرنه رشتهٔ خالی",
  "date": "تاریخ شمسی به شکل yyyy-MM-dd، یا رشتهٔ خالی اگر کاربر تاریخ نگفته",
  "time": "ساعت به شکل 24 ساعته HH:mm، یا رشتهٔ خالی اگر کاربر ساعت نگفته",
  "repeat": "none یا daily یا weekly یا monthly",
  "priority": "low یا normal یا high",
  "reminder": true یا false
}

مهم: تاریخ خروجی باید شمسی (هجری خورشیدی) باشد، نه میلادی. مثلاً امروز شمسی همین
«$todayIso» است؛ از همان مبنا حساب کن.

قواعد تفسیر تاریخ (بر اساس همان تاریخ امروزِ بالا، نه تاریخ فرضی):
- «امروز» = $todayIso
- «فردا» = فردای امروز
- «پس‌فردا» = دو روز بعد از امروز
- «امشب» = امروز
- «شنبه»/«یکشنبه»/«دوشنبه»/... = نزدیک‌ترین همان روز هفته در آینده (اگر امروز همان روز است، امروز)
- «هفته بعد» = ۷ روز بعد از امروز
- «آخر هفته» = نزدیک‌ترین پنجشنبهٔ آینده
- «ماه آینده» = همان روز در ماه بعد
- «N روز دیگه»/«N روز بعد» = N روز بعد از امروز (مثلاً «۵ روز دیگه» = ۵ روز بعد)
- «N هفته دیگه» = N×۷ روز بعد از امروز
- «N ماه دیگه» = همان روز، N ماه بعد
- «شنبه بعد»/«شنبه آینده» = شنبهٔ هفتهٔ آینده (نه امروز)
- تاریخ را فقط وقتی پر کن که کاربر واقعاً زمان گفته باشد؛ در غیر این صورت "" بگذار و هیچ تاریخی نساز.
- محاسبهٔ نهایی تاریخ با خودِ برنامه انجام می‌شود؛ تو فقط عبارت زمانی را تشخیص بده.

قواعد تفسیر ساعت:
- «ساعت ۵ عصر»/«پنج عصر» = 17:00
- «۸ شب» = 20:00
- «ساعت هشت صبح»/«صبح» = 08:00
- «ظهر» = 12:00
- «ساعت ۵» بدون صبح/عصر و بدون قرینه = 05:00
- «فردا صبح» = 08:00 و «فردا شب» = 20:00 (اگر ساعتی گفته نشده باشد)
- «N ساعت دیگه» = ساعت فعلی دستگاه + N ساعت
- ساعت را فقط وقتی پر کن که کاربر گفته باشد؛ در غیر این صورت "" بگذار و ساعت تصادفی نساز.

قواعد تکرار:
- «هر روز»/«روزانه» = daily
- «هر هفته»/«هفتگی» = weekly
- «هر ماه»/«ماهانه» = monthly
- اگر جمله هیچ تکرار و قیدی ندارد، حتماً none بگذار. یک کار معمولی (مثل «فردا ساعت ۵ قبض را بپرداز») تکرارش none است، نه daily.
- برای تکرارهای سفارشی (مثلاً «هر دو روز») نزدیک‌ترین حالت از همین سه را انتخاب کن.

قواعد اولویت:
- اگر کاربر گفته «مهم»، «فوری»، «حتماً» = high
- اگر گفته «بی‌اهمیت»، «هر وقت شد»، «فرقی نداره» = low
- وگرنه normal

قواعد یادآوری:
- اگر تاریخ و ساعت معلوم است، reminder را true بگذار.
- اگر تاریخ یا ساعت معلوم نیست، reminder را false بگذار.

مهم: هیچ کلیدی نباید null باشد. هر کلید باید مقدار درست داشته باشد: title رشته، date رشته، time رشته، repeat یکی از none/daily/weekly/monthly، priority یکی از low/normal/high، reminder فقط true یا false.
عنوان باید کم‌ترین و روشن‌ترین توصیف کار باشد (مثلاً «پرداخت قبض برق»).

نمونه:
ورودی: «فردا ساعت ۵ عصر قبض برق رو پرداخت کنم»
خروجی: {"title":"پرداخت قبض برق","description":"","date":"<فردا>","time":"17:00","repeat":"none","priority":"normal","reminder":true}

فقط JSON. هیچ برچسب، توضیح یا متن دیگری ننویس.
""".trimIndent()
}

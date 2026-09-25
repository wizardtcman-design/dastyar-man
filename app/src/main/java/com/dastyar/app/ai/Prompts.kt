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
                Health.cyclePhase(profile)?.let { sb.appendLine("دوران تقریبی: $it") }
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
                else -> "— نقش فعلی —\nتو دستیار عمومی کاربری. به هر موضوعی که پرسید کمک کن."
            }
        )
        sb.appendLine()
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
            info.append("روز ${Dates.cycleDay(profile.lastPeriodDate, profile.cycleLength)} چرخه. ")
        }
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

        val conditions = profile?.medicalConditions?.trim().orEmpty()
        val meds = profile?.medications?.trim().orEmpty()
        if (conditions.isNotBlank()) info.append("شرایط پزشکی اعلام‌شده کاربر: $conditions. ")
        if (meds.isNotBlank()) info.append("داروهای کاربر: $meds. ")

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
🩷 پریود | یک جمله کوتاه (اگر مرتبط نیست بنویس: 🩷 پریود | —)
🩺 شرایط | یک جمله مراقبتی ایمن با توجه به شرایط اعلام‌شده (اگر شرایطی نیست ننویس)

قواعد:
- بین ۴ تا ۶ خط، فقط همان‌هایی که برای کاربر مرتبط است. اگر موضوعی مرتبط نیست، آن خط را کلاً ننویس.
- متن هر روز باید بر اساس داده‌های همان روز متفاوت باشد؛ جمله‌های کلی و تکراری ننویس.
- هر جمله حداکثر ۱۵ کلمه، محاوره‌ای و بدون تکرار.
- از داده‌ای که به تو داده نشده حرف نزن و عدد جدید از خودت نساز.
- اگر شرایط پزشکی اعلام شده، پیشنهادت با آن در تناقض نباشد؛ در تناقض پیشنهاد ایمن‌تر بده.
- هرگز تشخیص پزشکی نده و ادعا نکن بیماری کاربر علت یک علامت است.
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
👩🍳 دستور: [مراحل شماره‌دار، کوتاه]

فقط با مواد ذکرشده یا مواد ساده و رایج آشپزخانه بساز. فارسی و جمع‌وجور بنویس.
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
        topic: String = "اطلاعات عمومی"
    ): String {
        val who = StringBuilder()
        if (profile?.age?.let { it > 0 } == true) who.append("سن کاربر: ${profile.age}. ")
        if (profile?.weightKg?.let { it > 0f } == true) who.append("وزن: ${profile.weightKg} کیلوگرم. ")
        return """
کاربر در پروفایل خود این شرایط را اعلام کرده است:
«$conditions»
داروهای اعلام‌شده: ${medications.ifBlank { "هیچ" }}
$who

امروز فقط روی این موضوع تمرکز کن: «$topic»

خروجی را دقیقاً در دو بخش بنویس:

💡 $topic | دو تا سه جمله کوتاه، دقیق و مفید مخصوصاً درباره همین موضوع
👩‍⚕️ نکته مهم | یک جمله درباره اینکه چه زمانی یا چرا باید با پزشک در میان بگذارد

قواعد:
- درباره همین موضوع بنویس و آن را تکرار موضوعات قبلی نکن.
- این توضیح آموزشی است، نه تشخیص. هیچ‌جا نگو کاربر قطعاً این بیماری را دارد.
- هیچ دارو یا دوزی تجویز نکن.
- ادعا نکن این شرایط علت یک علامت خاص کاربر است.
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
}

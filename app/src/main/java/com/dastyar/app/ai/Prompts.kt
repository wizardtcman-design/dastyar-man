package com.dastyar.app.ai

import com.dastyar.app.data.CheckIn
import com.dastyar.app.data.Dates
import com.dastyar.app.data.Profile

/**
 * Builds the system prompt from the user's real stored data, so every AI
 * answer is grounded in their profile and today's check-in.
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
- همیشه فارسی، کوتاه، گرم و کاربردی جواب بده.
""".trimIndent()

    fun base(): String = SAFETY

    fun system(channel: String, profile: Profile?, today: CheckIn?): String {
        val sb = StringBuilder()
        sb.appendLine("تو «دستیار من» هستی؛ یک دستیار شخصی روزانه فارسی‌زبان، مهربان و جمع‌وجور.")
        sb.appendLine("همیشه فارسی و راست‌به‌چپ جواب بده. جواب‌ها کوتاه و کاربردی باشند.")
        sb.appendLine()

        if (profile != null) {
            sb.appendLine("— اطلاعات کاربر —")
            if (profile.firstName.isNotBlank()) sb.appendLine("نام: ${profile.firstName}")
            if (profile.age > 0) sb.appendLine("سن: ${profile.age}")
            sb.appendLine("در چت با نام کوچک کاربر او را خطاب کن.")
            sb.appendLine()

            if (profile.lastPeriodDate.isNotBlank()) {
                val cd = Dates.cycleDay(profile.lastPeriodDate, profile.cycleLength)
                val until = Dates.daysUntilNextPeriod(profile.lastPeriodDate, profile.cycleLength)
                sb.appendLine("— چرخه پریود —")
                sb.appendLine("طول چرخه: ${profile.cycleLength} روز، طول پریود: ${profile.periodDays} روز")
                sb.appendLine("امروز روز $cd چرخه است. $until روز تا پریود بعدی مانده.")
                if (profile.periodPainLevel > 0) sb.appendLine("شدت معمول درد: ${profile.periodPainLevel} از ۱۰")
                if (profile.painLocation.isNotBlank()) sb.appendLine("محل معمول درد: ${profile.painLocation}")
                if (profile.painRelief.isNotBlank()) sb.appendLine("روش کنترل درد: ${profile.painRelief}")
                sb.appendLine()
            }

            sb.appendLine("— پوست —")
            if (profile.skinType.isNotBlank()) sb.appendLine("نوع پوست: ${profile.skinType}")
            if (profile.acneLevel.isNotBlank()) sb.appendLine("میزان جوش: ${profile.acneLevel}")
            if (profile.acneLocation.isNotBlank()) sb.appendLine("محل جوش: ${profile.acneLocation}")
            if (profile.currentProducts.isNotBlank()) sb.appendLine("محصولات فعلی: ${profile.currentProducts}")
            sb.appendLine()

            sb.appendLine("— بی‌رمقی —")
            if (profile.fatigueLevel.isNotBlank()) sb.appendLine("شدت بی‌رمقی: ${profile.fatigueLevel}")
            if (profile.sleepQuality.isNotBlank()) sb.appendLine("کیفیت معمول خواب: ${profile.sleepQuality}")
            if (profile.stressLevel.isNotBlank()) sb.appendLine("سطح استرس: ${profile.stressLevel}")
            sb.appendLine()
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

    /** Prompt used to generate the personalised daily suggestion card. */
    fun dailySuggestionPrompt(profile: Profile?, today: CheckIn?, waterGoal: Int): String {
        val name = profile?.firstName?.ifBlank { "دوست من" } ?: "دوست من"
        val info = buildString {
            append("نام: $name. ")
            if (today != null) {
                append("امروز: انرژی ${today.energyLevel.ifBlank { "ثبت نشده" }}, ")
                append("خواب ${today.sleepHours} ساعت (${today.sleepQuality.ifBlank { "-" }}), ")
                append("آب ${today.waterGlasses} از $waterGoal لیوان, ")
                append("پوست ${today.skinStatus.ifBlank { "ثبت نشده" }}, ")
                append("استرس ${today.stressLevel.ifBlank { "-" }}. ")
                if (today.isPeriodDay) append("امروز روز پریود است. ")
            }
            if (profile?.lastPeriodDate?.isNotBlank() == true) {
                append("روز ${Dates.cycleDay(profile.lastPeriodDate, profile.cycleLength)} چرخه. ")
            }
            if (profile?.skinType?.isNotBlank() == true) append("نوع پوست ${profile.skinType}. ")
        }
        return """
بر اساس وضعیت واقعی امروز کاربر، حداکثر ۴ پیشنهاد کوتاه و عملی بده.
اطلاعات کاربر: $info
خروجی را دقیقاً با این قالب بده و هر پیشنهاد در یک خط باشد، بدون مقدمه و بدون جمع‌بندی:
💧 آب | یک جمله کوتاه
✨ پوست | یک جمله کوتاه
⚡ بی‌رمقی | یک جمله کوتاه
🩷 پریود | یک جمله کوتاه
اگر موضوعی برای کاربر مرتبط نیست، همان خط را با «—» بگذار. هر جمله حداکثر ۱۵ کلمه.
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
}

package com.dastyar.app.ai

import com.dastyar.app.data.Dates
import com.dastyar.app.data.Task
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Turns one colloquial Persian sentence into a [Task] with the current AI
 * provider — the same provider, key and free-model fallback the rest of the app
 * uses; no new provider or key is introduced. It never invents a date or time:
 * fields the user did not mention come back empty and the caller can ask for
 * them. A local [TaskParser] handles the same text without AI, so the section
 * still works when the AI is unreachable.
 *
 * This is the only place AI is used in the tasks section.
 */
object TaskAi {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The weekday name in Persian, derived from the real device clock. */
    private fun persianWeekday(d: LocalDate): String = when (d.dayOfWeek) {
        DayOfWeek.SATURDAY -> "شنبه"
        DayOfWeek.SUNDAY -> "یکشنبه"
        DayOfWeek.MONDAY -> "دوشنبه"
        DayOfWeek.TUESDAY -> "سه‌شنبه"
        DayOfWeek.WEDNESDAY -> "چهارشنبه"
        DayOfWeek.THURSDAY -> "پنجشنبه"
        DayOfWeek.FRIDAY -> "جمعه"
    }

    /**
     * Runs the extraction. Returns a [Task] whose [Task.id] is 0 (not saved) and
     * with empty date/time whatever the sentence did not contain.
     */
    suspend fun extract(sentence: String): Result<Task> {
        val now = LocalTime.now()
        val pad = "%02d:%02d".format(now.hour, now.minute)
        // The app stores task dates as JALALI ISO, exactly like the manual
        // picker. The model is given the Jalali date as "today" and must answer
        // with a Jalali date too, so an AI-created task and a manually-created
        // one are stored the same way and display the same correct Persian date.
        val todayJalali = Dates.today()
        val prompt = Prompts.taskExtractPrompt(
            sentence = sentence.trim(),
            todayIso = todayJalali,
            todayPretty = Dates.pretty(todayJalali),
            weekday = persianWeekday(LocalDate.now()),
            nowTime = pad
        )
        return AiClient.chat(
            system = Prompts.base(),
            history = emptyList(),
            userMessage = prompt
        ).mapCatching { reply ->
            val block = extractJsonObject(reply)
                ?: throw IllegalStateException("پاسخ هوش مصنوعی قابل‌خواندن نبود.")
            guardAgainstInvented(sentence, parseBlock(block))
        }
    }

    /**
     * The model is told not to invent a time, but models drift. This checks its
     * answer against the same sentence read offline: if the sentence contains no
     * date word at all, the date is cleared; if it contains no clock word, the
     * time is cleared. So a date or hour can never appear that the user did not
     * say. The reminder flag follows suit.
     */
    private fun guardAgainstInvented(sentence: String, task: Task): Task {
        val offline = TaskParser.parse(sentence)
        val s = sentence
        val hasDateWord = listOf(
            "امروز", "فردا", "پس‌فردا", "پس فردا", "امشب", "شنبه", "یکشنبه",
            "دوشنبه", "سه‌شنبه", "سه شنبه", "چهارشنبه", "پنجشنبه", "پنج‌شنبه",
            "جمعه", "هفته بعد", "هفتهٔ بعد", "هفته دیگه", "آخر هفته",
            "آخر این هفته", "ماه آینده", "ماه بعد"
        ).any { s.contains(it) } ||
                Regex("""\d{4}-\d{2}-\d{2}""").containsMatchIn(s) ||
                // Relative offsets: «۵ روز دیگه»، «۲ هفته بعد»، «۳ ماه دیگه».
                Regex("""[0-9۰-۹]+\s*(روز|هفته|ماه)\s*(دیگه|دیگر|بعد|آینده)""").containsMatchIn(s)

        val hasTimeWord = Regex("""ساعت|عصر|صبح|ظهر|شب|بعدازظهر|بعد از ظهر""").containsMatchIn(s) ||
                Regex("""[0-9۰-۹]{1,2}[:.]\s*[0-9۰-۹]{1,2}""").containsMatchIn(s)

        val date = if (hasDateWord) task.date.ifBlank { offline.date } else ""
        // A relative count like «۵ روز دیگه» is arithmetic on the real device
        // date, which the offline parser already did exactly; that result wins
        // so a free model can never shift the day. Absolute phrases keep the AI
        // date when it is a valid Jalali one.
        val hasRelative = Regex("""[0-9۰-۹]+\s*(روز|هفته|ماه)\s*(دیگه|دیگر|بعد|آینده)""")
            .containsMatchIn(sentence)
        val candidate = if (hasRelative && offline.date.isNotBlank()) offline.date else date
        // A model may ignore the instruction and answer with a Gregorian date
        // (year ~2026). Stored as if it were Jalali that would display a wildly
        // wrong Persian date, so a non-Jalali year is discarded in favour of the
        // offline parser's Jalali result (which is empty when the sentence has
        // no date word).
        val fixedDate = if (isJalaliIso(candidate)) candidate else offline.date
        // «۲ ساعت دیگه» is likewise computed from the real clock, not the model.
        val time = if (hasTimeWord) task.time.ifBlank { offline.time } else ""
        // «۲ ساعت دیگه» is likewise computed from the real clock, not the model.
        val hasRelativeHours = Regex("""(ساعت)\s*(دیگه|دیگر|بعد)""").containsMatchIn(sentence) &&
                !Regex("""ساعت\s*[0-9۰-۹]""").containsMatchIn(sentence)
        val fixedTime = if (hasRelativeHours && offline.time.isNotBlank()) offline.time else time
        // A free model sometimes answers in English or repeats the whole
        // sentence. When its title is not Persian but the offline reader found a
        // clean Persian title, the offline one is used so the preview stays in
        // the user's language and looks like a task name.
        val persian = Regex("""[\u0600-\u06FF]""")
        val title = if (persian.containsMatchIn(task.title)) task.title
        else offline.title.ifBlank { task.title }
        return task.copy(
            title = title,
            description = task.description,
            date = fixedDate,
            time = fixedTime,
            reminderEnabled = task.reminderEnabled && fixedDate.isNotBlank() && fixedTime.isNotBlank()
        )
    }

    /**
     * True when [iso] parses as a Jalali date whose year is plausible for the
     * Solar Hijri calendar (roughly this century). A Gregorian year such as
     * 2026 is rejected, because the app stores Jalali years (~1400s).
     */
    private fun isJalaliIso(iso: String): Boolean {
        if (iso.isBlank()) return true
        val j = com.dastyar.app.data.Jalali.parse(iso) ?: return false
        return j.year in 1300..1500
    }

    /**
     * Reads the model's JSON answer. If the model wrapped it in prose or code
     * fences, the first {...} block is used. Any missing field becomes a safe
     * default, never a guessed value.
     */
    fun parse(reply: String): Task {
        val block = extractJsonObject(reply) ?: return Task()
        return parseBlock(block)
    }

    /** Reads the fields from one JSON object string into a [Task]. */
    private fun parseBlock(block: String): Task {
        val obj = runCatching { json.parseToJsonElement(block).jsonObject }.getOrNull()
            ?: return Task()
        // Every field is read defensively: a missing key, a JSON null, or a
        // non-text value (the model sometimes answers null) must never throw.
        fun str(key: String): String = runCatching {
            obj[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        }.getOrDefault("")
        fun bool(key: String): Boolean = runCatching {
            val v = obj[key]?.jsonPrimitive?.contentOrNull
            v != null && (v.equals("true", true) || v == "1" || v.contains("یادآوری"))
        }.getOrDefault(false)
        val repeat = when (str("repeat").lowercase()) {
            "daily", "روزانه" -> "daily"
            "weekly", "هفتگی" -> "weekly"
            "monthly", "ماهانه" -> "monthly"
            else -> "none"
        }
        val priority = when (str("priority").lowercase()) {
            "high", "زیاد", "بالا" -> "high"
            "low", "کم", "پایین" -> "low"
            else -> "normal"
        }
        val date = normalizeDate(str("date"))
        val time = normalizeTime(str("time"))
        val reminder = bool("reminder")
        return Task(
            title = str("title").ifBlank { "کار جدید" },
            description = str("description"),
            date = date,
            time = time,
            repeat = repeat,
            priority = priority,
            reminderEnabled = reminder && date.isNotBlank()
        )
    }

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    /** Keeps only a real yyyy-MM-dd date; anything else becomes empty. */
    private fun normalizeDate(raw: String): String {
        val s = raw.trim()
        if (s.isBlank()) return ""
        val direct = runCatching { LocalDate.parse(s.take(10)) }.getOrNull()
        if (direct != null) return direct.toString()
        return ""
    }

    /** Keeps only a real HH:mm time; "17:0" or "5" is repaired when possible. */
    private fun normalizeTime(raw: String): String {
        val s = raw.trim()
        if (s.isBlank()) return ""
        val m = Regex("""(\d{1,2})[:.\u066B\u066C](\d{1,2})""").find(s)
        if (m != null) {
            val h = m.groupValues[1].toIntOrNull() ?: return ""
            val mi = m.groupValues[2].toIntOrNull() ?: 0
            if (h in 0..23 && mi in 0..59) return "%02d:%02d".format(h, mi)
            return ""
        }
        val h = s.filter { it.isDigit() }.toIntOrNull() ?: return ""
        if (h in 0..23) return "%02d:%02d".format(h, 0)
        return ""
    }
}

/**
 * A local, offline reader for the same natural Persian task sentences. It is
 * the fallback used when the AI is unavailable, and it also powers a preview
 * the moment the user types, so the app never depends on the network to add a
 * task. It understands the relative words and clock expressions listed in the
 * spec and resolves them against the real device date and time.
 */
object TaskParser {

    private data class Parsed(
        val title: String,
        val date: String,
        val time: String,
        val repeat: String
    )

    fun parse(sentence: String): Task {
        val p = parse2(sentence)
        return Task(
            title = p.title.ifBlank { sentence.trim().ifBlank { "کار جدید" } },
            description = "",
            date = p.date,
            time = p.time,
            repeat = p.repeat,
            priority = priorityOf(sentence),
            reminderEnabled = p.date.isNotBlank() && p.time.isNotBlank()
        )
    }

    private fun priorityOf(s: String): String = when {
        listOf("مهم", "فوری", "حتماً", "حتما", "ضروری").any { s.contains(it) } -> "high"
        listOf("بی‌اهمیت", "بی اهمیت", "فرقی نداره", "فرقی نداره", "هر وقت شد").any { s.contains(it) } -> "low"
        else -> "normal"
    }

    private fun parse2(sentence: String): Parsed {
        val s = sentence

        // ---- repeat ----
        val repeat = when {
            s.contains("هر روز") || s.contains("روزانه") || s.contains("همه روز") -> "daily"
            s.contains("هر هفته") || s.contains("هفتگی") -> "weekly"
            s.contains("هر ماه") || s.contains("ماهانه") || s.contains("ماهی یک بار") -> "monthly"
            else -> "none"
        }

        // ---- date ----
        // The device's real clock is the base for every relative expression, and
        // the arithmetic (not the AI) decides the final day. Weekday arithmetic
        // is done on real Gregorian days, then stored as a JALALI ISO string --
        // the same format the manual picker writes.
        val today = LocalDate.now()
        val relativeCount = relativeDays(s)
        val date = when {
            relativeCount != null -> today.plusDays(relativeCount.toLong())
            relativeWeeks(s) != null -> today.plusWeeks(relativeWeeks(s)!!.toLong())
            relativeMonths(s) != null -> today.plusMonths(relativeMonths(s)!!.toLong())
            s.contains("پس‌فردا") || s.contains("پس فردا") -> today.plusDays(2)
            s.contains("فردا") -> today.plusDays(1)
            s.contains("امشب") || s.contains("امروز") -> today
            s.contains("هفته بعد") || s.contains("هفتهٔ بعد") || s.contains("هفته دیگه") -> today.plusDays(7)
            s.contains("آخر هفته") || s.contains("آخر این هفته") -> nextWeekday(today, DayOfWeek.THURSDAY)
            s.contains("ماه آینده") || s.contains("ماه بعد") -> today.plusMonths(1)
            s.contains("شنبه بعد") || s.contains("شنبه آینده") ->
                nextWeekdayStrict(today, DayOfWeek.SATURDAY)
            else -> weekdayIn(s)?.let { nextWeekday(today, it) }
        }?.let { com.dastyar.app.data.Jalali.fromGregorian(it).iso() }.orEmpty()

        // ---- time ----
        val time = timeOf(s)

        // ---- title ----
        var title = s
        val strips = listOf(
            "پس‌فردا", "پس فردا", "فردا", "امشب", "امروز", "هفته بعد", "هفتهٔ بعد",
            "هفته دیگه", "آخر هفته", "آخر این هفته", "ماه آینده", "ماه بعد",
            "هر روز", "روزانه", "همه روز", "هر هفته", "هفتگی", "هر ماه", "ماهانه",
            "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "سه شنبه", "چهارشنبه", "پنجشنبه",
            "پنج‌شنبه", "جمعه", "صبح", "ظهر", "عصر", "شب", "بعدازظهر", "بعد از ظهر",
            "یادم بنداز", "یادم بیار", "یادآوری کن", "یادآوری", "رو یادم بنداز"
        )
        strips.forEach { title = title.replace(it, " ") }
        // Relative phrases: «۵ روز دیگه»، «۲ ساعت دیگه»، «۳ هفته بعد».
        title = Regex("""[0-9۰-۹]+\s*(روز|هفته|ماه|ساعت)\s*(دیگه|دیگر|بعد|آینده)""")
            .replace(title, " ")
        title = Regex("""(یک|دو|سه|چهار|پنج|شش|هفت|هشت|نه|ده|یازده|دوازده)\s*(روز|هفته|ماه|ساعت)\s*(دیگه|دیگر|بعد|آینده)""")
            .replace(title, " ")
        title = Regex("""ساعت\s*[0-9۰-۹]+\s*(و\s*[0-9۰-۹]+\s*دقیقه)?""").replace(title, " ")
        title = Regex("""[0-9۰-۹]+\s*(عصر|شب|صبح|ظهر)""").replace(title, " ")
        title = title.replace(Regex("""\s+"""), " ").trim()
        title = title.trim('،', ',', '.', '!', '؟', '?', ' ', '-').trim()
        // «دیگه»/«بعد» left floating after the number was removed.
        title = Regex("""\b(دیگه|دیگر)\b""").replace(title, " ").trim()
        title = title.replace(Regex("""\s+"""), " ").trim()

        return Parsed(title = title, date = date, time = time, repeat = repeat)
    }

    /**
     * A plain number written with Persian/Latin digits or common Persian number
     * words: «۵»، «5»، «پنج». Returns null when none is present.
     */
    private fun numberIn(s: String): Int? {
        val fa = mapOf(
            '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
            '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9'
        )
        Regex("""[0-9۰-۹]+""").find(s)?.let { m ->
            val n = m.value.map { fa[it] ?: it }.joinToString("").toIntOrNull()
            if (n != null) return n
        }
        val words = listOf(
            "یازده" to 11, "دوازده" to 12, "سی" to 30,
            "یک" to 1, "دو" to 2, "سه" to 3, "چهار" to 4, "پنج" to 5,
            "شش" to 6, "هفت" to 7, "هشت" to 8, "نه" to 9, "ده" to 10
        )
        for ((w, v) in words) if (s.contains(w)) return v
        return null
    }

    /** «۵ روز دیگه» / «۳ روز بعد» -> 5, 3. Null when it is not a day offset. */
    private fun relativeDays(s: String): Int? {
        if (Regex("""(روز)\s*(دیگه|دیگر|بعد|آینده)""").containsMatchIn(s) ||
            Regex("""(دیگه|دیگر|بعد|آینده)\s*(روز)""").containsMatchIn(s)
        ) return numberIn(s)
        return null
    }

    /** «۲ هفته دیگه» -> 2. */
    private fun relativeWeeks(s: String): Int? {
        if (Regex("""(هفته)\s*(دیگه|دیگر|بعد|آینده)""").containsMatchIn(s)) return numberIn(s)
        return null
    }

    /** «۲ ماه دیگه» -> 2. */
    private fun relativeMonths(s: String): Int? {
        if (Regex("""(ماه)\s*(دیگه|دیگر|بعد|آینده)""").containsMatchIn(s)) return numberIn(s)
        return null
    }

    /** «۲ ساعت دیگه» -> 2, so the time can be computed from the real clock. */
    private fun relativeHours(s: String): Int? {
        if (Regex("""(ساعت)\s*(دیگه|دیگر|بعد)""").containsMatchIn(s) &&
            !Regex("""ساعت\s*[0-9۰-۹]""").containsMatchIn(s)
        ) return numberIn(s)
        return null
    }

    /** The next [day] strictly after today (used for «شنبه بعد»). */
    private fun nextWeekdayStrict(from: LocalDate, day: DayOfWeek): LocalDate {
        var d = from.plusDays(1)
        var guard = 0
        while (d.dayOfWeek != day && guard < 8) {
            d = d.plusDays(1); guard++
        }
        return d
    }

    private fun weekdayIn(s: String): DayOfWeek? = when {        s.contains("شنبه") && !s.contains("یکشنبه") && !s.contains("دوشنبه") &&
                !s.contains("سه‌شنبه") && !s.contains("سه شنبه") && !s.contains("چهارشنبه") &&
                !s.contains("پنجشنبه") && !s.contains("پنج‌شنبه") -> DayOfWeek.SATURDAY
        s.contains("یکشنبه") -> DayOfWeek.SUNDAY
        s.contains("دوشنبه") -> DayOfWeek.MONDAY
        s.contains("سه‌شنبه") || s.contains("سه شنبه") -> DayOfWeek.TUESDAY
        s.contains("چهارشنبه") -> DayOfWeek.WEDNESDAY
        s.contains("پنجشنبه") || s.contains("پنج‌شنبه") -> DayOfWeek.THURSDAY
        s.contains("جمعه") -> DayOfWeek.FRIDAY
        else -> null
    }

    /** The nearest future occurrence of [day]; today if it is that day. */
    private fun nextWeekday(from: LocalDate, day: DayOfWeek): LocalDate {
        var d = from
        var guard = 0
        while (d.dayOfWeek != day && guard < 8) {
            d = d.plusDays(1); guard++
        }
        return d
    }

    /**
     * Reads a clock expression. Anything with no explicit clock stays empty, so
     * a random time is never created.
     */
    private fun timeOf(s: String): String {
        val fa = mapOf(
            '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
            '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9'
        )
        fun toEn(x: String) = x.map { fa[it] ?: it }.joinToString("")

        // «۲ ساعت دیگه» -> the real clock plus two hours. The offset is computed
        // from the device time, never guessed by the AI.
        relativeHours(s)?.let { h ->
            val now = java.time.LocalTime.now().plusHours(h.toLong())
            return "%02d:%02d".format(now.hour, now.minute)
        }

        // «فردا صبح» / «فردا شب» with no explicit hour -> a sensible fixed hour.
        if (!Regex("""[0-9۰-۹]""").containsMatchIn(s) &&
            Regex("""(فردا|امروز|امشب|پس‌فردا|پس فردا)""").containsMatchIn(s)
        ) {
            when {
                s.contains("صبح") -> return "08:00"
                s.contains("ظهر") -> return "12:00"
                s.contains("عصر") -> return "17:00"
                s.contains("شب") || s.contains("امشب") -> return "20:00"
            }
        }

        val hm = Regex("""ساعت\s*([0-9۰-۹]{1,2})\s*(?:[:.]\s*([0-9۰-۹]{1,2}))?""").find(s)
        val bareHour = Regex("""([0-9۰-۹]{1,2})\s*(عصر|شب|صبح|ظهر|بعدازظهر|بعد از ظهر)""").find(s)
        val wordHour = wordNumberTime(s)

        var hour: Int? = null
        var minute = 0
        when {
            hm != null -> {
                hour = toEn(hm.groupValues[1]).toIntOrNull()
                minute = hm.groupValues[2].takeIf { it.isNotBlank() }?.let { toEn(it).toIntOrNull() } ?: 0
            }
            bareHour != null -> hour = toEn(bareHour.groupValues[1]).toIntOrNull()
            wordHour != null -> hour = wordHour
        }
        if (hour == null || hour !in 0..23) return ""
        if (minute !in 0..59) minute = 0

        val part = when {
            s.contains("بعدازظهر") || s.contains("بعد از ظهر") -> "pm"
            s.contains("عصر") || s.contains("شب") -> "pm"
            s.contains("صبح") || s.contains("ظهر") -> "am"
            else -> ""
        }
        hour = when {
            part == "pm" && hour < 12 -> hour + 12
            part == "am" && hour == 12 -> 0
            else -> hour
        }
        return "%02d:%02d".format(hour, minute)
    }

    /** Spoken clock hours: «پنج عصر», «هشت صبح», «ساعت پنج». */
    private fun wordNumberTime(s: String): Int? {
        val words = mapOf(
            "یک" to 1, "دو" to 2, "سه" to 3, "چهار" to 4, "پنج" to 5,
            "شش" to 6, "هفت" to 7, "هشت" to 8, "نه" to 9, "ده" to 10,
            "یازده" to 11, "دوازده" to 12
        )
        if (!s.contains("ساعت") && !s.contains("عصر") && !s.contains("صبح") &&
            !s.contains("شب") && !s.contains("ظهر")) return null
        for ((w, v) in words) {
            if (s.contains(w)) return v
        }
        return null
    }
}

package com.dastyar.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dastyar.app.ai.AiClient
import com.dastyar.app.ai.Prompts
import com.dastyar.app.data.ChatMessage
import com.dastyar.app.data.CheckIn
import com.dastyar.app.data.DailySuggestion
import com.dastyar.app.data.DastyarDatabase
import com.dastyar.app.data.Dates
import com.dastyar.app.data.Health
import com.dastyar.app.data.Profile
import com.dastyar.app.data.SmartFact
import com.dastyar.app.data.Task
import com.dastyar.app.data.WeightEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = DastyarDatabase.get(app).dao()

    val profile: StateFlow<Profile?> = dao.profileFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val checkIns: StateFlow<List<CheckIn>> = dao.allCheckIns()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tasks: StateFlow<List<Task>> = dao.allTasks()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val weights: StateFlow<List<WeightEntry>> = dao.weightFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val smartFacts: StateFlow<List<SmartFact>> = dao.smartFactsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val today: String get() = Dates.today()

    // ---- transient UI state ----
    private val _todayCheckIn = MutableStateFlow<CheckIn?>(null)
    val todayCheckIn: StateFlow<CheckIn?> = _todayCheckIn.asStateFlow()

    private val _suggestion = MutableStateFlow<DailySuggestion?>(null)
    val suggestion: StateFlow<DailySuggestion?> = _suggestion.asStateFlow()

    private val _loadingSuggestion = MutableStateFlow(false)
    val loadingSuggestion: StateFlow<Boolean> = _loadingSuggestion.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    // ---- dynamic card about the user's declared medical conditions ----
    private val _conditionInfo = MutableStateFlow<String?>(null)
    val conditionInfo: StateFlow<String?> = _conditionInfo.asStateFlow()

    private val _loadingCondition = MutableStateFlow(false)
    val loadingCondition: StateFlow<Boolean> = _loadingCondition.asStateFlow()

    private var conditionLoadedFor: String? = null
    private var conditionTopicIndex = 0

    // ---- smart tip for the current cycle day ----
    private val _cycleTip = MutableStateFlow<String?>(null)
    val cycleTip: StateFlow<String?> = _cycleTip.asStateFlow()

    private val _loadingCycleTip = MutableStateFlow(false)
    val loadingCycleTip: StateFlow<Boolean> = _loadingCycleTip.asStateFlow()

    private var cycleTipLoadedFor: String? = null

    /**
     * Personalises today's cycle tip with AI when it is available, using the
     * real cycle day, phase, length and today's check-in. The local phase tip is
     * already shown by the UI, so any failure simply keeps that text.
     */
    fun loadCycleTip(force: Boolean = false) = viewModelScope.launch {
        val p = profile.value ?: return@launch
        val phase = Health.phase(p) ?: run {
            _cycleTip.value = null
            return@launch
        }
        val day = Health.cycleDay(p)
        val key = "$day-${phase.name}-${p.cycleLength}-${p.periodDays}"
        if (!force && cycleTipLoadedFor == key && _cycleTip.value != null) return@launch
        if (!AiClient.chatConfigured) return@launch

        _loadingCycleTip.value = true
        val res = AiClient.chat(
            system = Prompts.base(),
            history = emptyList(),
            userMessage = Prompts.cycleTipPrompt(
                profile = p,
                today = _todayCheckIn.value,
                day = day,
                phase = phase.title,
                daysUntil = Health.daysUntilPeriod(p),
                irregular = Health.cycleIsIrregular(p)
            )
        )
        _loadingCycleTip.value = false
        res.onSuccess { text ->
            val cleaned = text.trim()
            if (cleaned.isNotBlank()) {
                _cycleTip.value = cleaned
                cycleTipLoadedFor = key
            }
        }
    }

    /**
     * Produces a short, educational explanation of the conditions the user
     * declared in their profile. Each forced load advances to a new topic, so
     * the card shows fresh, relevant content over time instead of a fixed
     * paragraph. Silently does nothing when AI is unavailable.
     */
    fun loadConditionInfo(force: Boolean = false) = viewModelScope.launch {
        val p = profile.value ?: return@launch
        val conditions = p.medicalConditions.trim()
        if (conditions.isBlank()) {
            _conditionInfo.value = null
            return@launch
        }
        if (!force && conditionLoadedFor == conditions && _conditionInfo.value != null) return@launch
        if (!AiClient.chatConfigured) return@launch
        if (force || conditionLoadedFor != conditions) {
            conditionTopicIndex = (conditionTopicIndex + 1) % Prompts.conditionTopics.size
        }
        val topic = Prompts.conditionTopics[conditionTopicIndex]

        _loadingCondition.value = true
        val res = AiClient.chat(
            system = Prompts.base(),
            history = emptyList(),
            userMessage = Prompts.conditionPrompt(conditions, p.medications.trim(), p, topic)
        )
        _loadingCondition.value = false
        res.onSuccess { text ->
            val cleaned = text.lines()
                .map { it.trim() }
                .filter { it.contains("|") }
                .joinToString("\n")
            if (cleaned.isNotBlank()) {
                _conditionInfo.value = cleaned
                conditionLoadedFor = conditions
            }
        }
    }

    fun clearToast() { _toast.value = null }

    // ---- deep-link: a notification tap asks the UI to open the check-in tab ----
    private val _openCheckIn = MutableStateFlow(0)
    val openCheckIn: StateFlow<Int> = _openCheckIn.asStateFlow()

    /** Requests that the UI jump to the daily check-in screen. */
    fun requestOpenCheckIn() { _openCheckIn.value = _openCheckIn.value + 1 }

    init {
        viewModelScope.launch {
            refreshToday()
            profile.collect { p -> if (p != null) refreshToday() }
        }
    }

    /** Loads today's check-in and today's cached suggestion (if any). */
    suspend fun refreshToday() {
        val t = Dates.today()
        _todayCheckIn.value = dao.checkIn(t)
        _suggestion.value = dao.suggestion(t)
    }

    // ------------------------------------------------------------- profile

    /**
     * Saves the profile. On the very first save from onboarding this also
     * records the questionnaire as the user's day-one check-in, so the
     * dashboard has data immediately and the daily questions are not asked
     * again the same day.
     */
    fun saveProfile(p: Profile) = viewModelScope.launch(Dispatchers.IO) {
        val previous = dao.profile()
        val isFirstOnboarding = (previous?.onboardingDone != true) && p.onboardingDone
        dao.saveProfile(p.copy(id = 1))

        if (isFirstOnboarding) {
            val first = onboardingAsCheckIn(p, Dates.today())
            dao.saveCheckIn(first)
            withContext(Dispatchers.Main) { _todayCheckIn.value = first }
        }
        if (p.weightKg > 0f) {
            dao.saveWeight(WeightEntry(date = Dates.today(), weightKg = p.weightKg))
        }
        // Cycle data may have changed: re-arm the period reminders from it.
        // Only period fields force a re-arm so weight edits stay cheap.
        if (previous == null ||
            previous.lastPeriodDate != p.lastPeriodDate ||
            previous.cycleLength != p.cycleLength ||
            previous.periodDays != p.periodDays
        ) {
            com.dastyar.app.notifications.PeriodReminder.scheduleNext(getApplication(), p)
        }
        withContext(Dispatchers.Main) {
            _todayCheckIn.value = dao.checkIn(Dates.today())
            // The cycle day moved, so refresh the tip when cycle data changed.
            if (previous?.lastPeriodDate != p.lastPeriodDate ||
                previous?.cycleLength != p.cycleLength ||
                previous?.periodDays != p.periodDays
            ) {
                _cycleTip.value = null
                cycleTipLoadedFor = null
                loadCycleTip(force = true)
            }
        }
    }

    /** Records a weight measurement for today and keeps the profile in sync. */
    fun recordWeight(kg: Float) = viewModelScope.launch(Dispatchers.IO) {
        if (kg <= 0f) return@launch
        dao.saveWeight(WeightEntry(date = Dates.today(), weightKg = kg))
        val p = dao.profile()
        if (p != null) dao.saveProfile(p.copy(weightKg = kg))
        withContext(Dispatchers.Main) { _toast.value = "وزن امروز ثبت شد ⚖️" }
    }

    /**
     * Turns the completed questionnaire into a real first check-in row, so the
     * dashboard shows it as day one of the history.
     */
    private fun onboardingAsCheckIn(p: Profile, date: String) = CheckIn(
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
        skinStatus = "",
        skinDryOily = p.dryOrOily,
        skinSensitivity = p.hasSensitivity,
        isPeriodDay = false,
        periodPain = if (p.periodPainLevel > 0) "دارم" else "",
        periodPainLevel = if (p.periodPainLevel > 0) p.periodPainLevel.toString() else "",
        periodPainLocation = p.painLocation,
        notes = "این اطلاعات از پرسشنامه اولیه ثبت شد."
    )

    // ------------------------------------------------------------ check-in

    /**
     * Whether the daily check-in should be asked today. It is skipped on the
     * day the user finishes onboarding, and skipped whenever today's row
     * already exists.
     */
    fun shouldAskCheckIn(profile: Profile?, todayCheckIn: CheckIn?): Boolean {
        if (profile?.onboardingDone != true) return false
        return todayCheckIn == null
    }

    /**
     * Saves a check-in for a specific date. Because [CheckIn.date] is the
     * primary key, saving twice on the same day updates the same row.
     */
    fun saveCheckIn(c: CheckIn) = viewModelScope.launch(Dispatchers.IO) {
        dao.saveCheckIn(c)
        withContext(Dispatchers.Main) {
            _todayCheckIn.value = c
            _toast.value = "وضعیت امروز ذخیره شد ✅"
        }
    }

    fun updateWater(delta: Int) = viewModelScope.launch(Dispatchers.IO) {
        val t = Dates.today()
        val cur = dao.checkIn(t) ?: CheckIn(date = t)
        val next = (cur.waterGlasses + delta).coerceIn(0, 30)
        dao.saveCheckIn(cur.copy(waterGlasses = next))
        withContext(Dispatchers.Main) {
            _todayCheckIn.value = cur.copy(waterGlasses = next)
            _toast.value = "آب ثبت شد 💧"
        }
    }

    fun setWater(value: Int) = viewModelScope.launch(Dispatchers.IO) {
        val t = Dates.today()
        val cur = dao.checkIn(t) ?: CheckIn(date = t)
        val v = value.coerceIn(0, 30)
        dao.saveCheckIn(cur.copy(waterGlasses = v))
        _todayCheckIn.value = cur.copy(waterGlasses = v)
    }

    // --------------------------------------------------------- suggestions

    /** The locally computed plan; never needs the network. */
    fun localPlan(): DailySuggestion? {
        val p = profile.value
        val ci = _todayCheckIn.value
        val goal = Health.waterTarget(p, ci)
        val lines = buildLocalLines(p, ci, checkIns.value, goal)
        if (lines.isEmpty()) return null
        return DailySuggestion(
            date = Dates.today(),
            content = lines.joinToString("\n"),
            waterGoal = goal
        )
    }

    /**
     * Builds the suggestion lines from recorded data only. Used as the instant
     * result and as the fallback when the AI is unavailable. Every line is
     * derived from something the user actually recorded, so the plan changes as
     * their data changes instead of repeating a fixed text.
     */
    private fun buildLocalLines(
        p: Profile?,
        ci: CheckIn?,
        history: List<CheckIn>,
        waterGoal: Int
    ): List<String> {
        val out = mutableListOf<String>()
        val glasses = ci?.waterGlasses ?: 0
        val remaining = (waterGoal - glasses).coerceAtLeast(0)
        out += if (remaining > 0) "💧 آب | هدف امروز ${Dates.fa(waterGoal)} لیوان است؛ ${Dates.fa(remaining)} لیوان دیگر بنوش"
        else "💧 آب | هدف آب امروز کامل شد، عالی بود"

        val sleepRange = Health.sleepTargetHours(p)
        val target = "${Dates.fa(sleepRange.start.toInt())} تا ${Dates.fa(sleepRange.endInclusive.toInt())}"
        out += "😴 خواب | امشب بین $target ساعت بخواب"

        // Fatigue-aware rest line, only when the user reported fatigue today.
        when {
            ci?.fatigueSeverity?.contains("خیلی زیاد") == true ->
                out += "🛋 استراحت | خستگی‌ات زیاد است؛ امروز کارهای سنگین را به فردا بسپار"
            ci?.fatigueSeverity?.contains("زیاد") == true ->
                out += "🛋 استراحت | یک استراحت ۱۵ دقیقه‌ای بین کارها بگذار"
            else -> Health.restAdvice(ci)?.let { out += "🛋 استراحت | $it" }
        }

        Health.activityMinutes(ci)?.let {
            out += "🚶 فعالیت | حدود ${Dates.fa(it)} دقیقه فعالیت سبک مناسب امروزه"
        }
        Health.sleepAdvice(p, ci)?.let { out += "😴 خواب | $it" }

        // Stress-aware line, only when stress was actually reported.
        when (ci?.stressLevel) {
            "خیلی زیاد", "زیاد" ->
                out += "🧘 آرامش | چند دقیقه نفس عمیق یا پیاده‌روی آرام، استرست را کم می‌کند"
            "متوسط" ->
                out += "🧘 آرامش | یک وقفهٔ کوتاه بدون گوشی در برنامه امروزت بگذار"
        }

        // Period care line only when there is real cycle data.
        if (p?.lastPeriodDate?.isNotBlank() == true) {
            val until = Dates.daysUntilNextPeriod(p.lastPeriodDate, p.cycleLength)
            if (ci?.isPeriodDay == true) {
                out += "🩷 پریود | امروز روز پریوده؛ آب، آهن و استراحت را بیشتر کن"
            } else if (until in 0..3) {
                out += "🩷 پریود | حدود ${Dates.fa(until)} روز تا پریود بعدی مانده؛ آهن و آب را جدی بگیر"
            }
        }

        // Skin line only when the user reported something today.
        ci?.skinStatus?.takeIf { it.isNotBlank() }?.let {
            out += when {
                it.contains("بدتر") -> "✨ پوست | امروز روتینت را ساده کن؛ فقط شست‌وشوی ملایم و مرطوب‌کننده"
                it.contains("بهتر") -> "✨ پوست | روتین فعلی‌ات جواب داده؛ ضدآفتاب را قطع نکن"
                else -> "✨ پوست | روتین ساده و ضدآفتاب را ادامه بده"
            }
        }

        // Weight-goal line only when a target and current weight both exist.
        val bmi = Health.bmi(p)
        if (p != null && p.targetWeightKg > 0f && p.weightKg > 0f && bmi != null) {
            val diff = p.weightKg - p.targetWeightKg
            if (diff > 0.5f) {
                out += "⚖️ وزن | ${Dates.fa(diff, 1)} کیلو تا وزن هدفت مانده؛ کم‌کم و پیوسته پیش برو"
            } else if (diff < -0.5f) {
                out += "⚖️ وزن | به وزن هدفت رسیدی؛ برای حفظش همین ریتم را نگه دار"
            }
        }

        // Condition-aware line only when the user declared one.
        p?.medicalConditions?.takeIf { it.isNotBlank() }?.let {
            out += "🩺 شرایط | با توجه به «$it» که ثبت کردی، پیشنهادهای امروز ایمن‌تر انتخاب شده‌اند"
        }

        Health.highlights(history)?.forEach { out += "📊 روند | $it" }

        return out.distinct().take(6)
    }

    /**
     * Produces today's suggestion. The local plan is always computed first and
     * shown immediately; the AI is then asked to refine it in richer language.
     * If the AI fails, the local plan stays — the user always sees something
     * useful and no misleading error is shown for a cosmetic failure.
     */
    fun generateSuggestion(force: Boolean = false) = viewModelScope.launch {
        val t = Dates.today()
        val local = localPlan()

        if (!force) {
            val cached = dao.suggestion(t)
            if (cached != null && cached.content.isNotBlank()) {
                _suggestion.value = cached
                return@launch
            }
        }

        // Publish the local plan right away so the UI is never empty.
        if (local != null) _suggestion.value = local

        if (!AiClient.chatConfigured) {
            if (local == null) _toast.value = "کلید هوش مصنوعی تنظیم نشده است."
            return@launch
        }

        _loadingSuggestion.value = true
        val p = profile.value
        val ci = _todayCheckIn.value
        val goal = Health.waterTarget(p, ci)
        val res = AiClient.chat(
            system = Prompts.base(),
            history = emptyList(),
            userMessage = Prompts.dailySuggestionPrompt(
                profile = p,
                today = ci,
                history = checkIns.value,
                facts = smartFacts.value,
                waterGoal = goal
            )
        )
        _loadingSuggestion.value = false

        res.onSuccess { text ->
            val cleaned = text.lines()
                .map { it.trim() }
                .filter { it.contains("|") && !it.substringAfter("|").trim().startsWith("—") }
                .joinToString("\n")
            if (cleaned.isNotBlank()) {
                val merged = local?.copy(content = cleaned) ?: DailySuggestion(t, cleaned, goal)
                _suggestion.value = merged
                dao.saveSuggestion(merged)
            } else if (local != null) {
                dao.saveSuggestion(local)
            }
        }.onFailure {
            // Keep the local plan; only surface a genuine problem.
            if (local != null) dao.saveSuggestion(local) else _toast.value = it.message
        }
    }

    // ----------------------------------------------------------------- chat

    fun chat(channel: String, text: String) = viewModelScope.launch {
        val userMsg = ChatMessage(channel = channel, role = "user", content = text)
        dao.addMessage(userMsg)

        val history = dao.recentChat(channel, 12).reversed().map { it.role to it.content }
        val p = profile.value
        val ci = _todayCheckIn.value

        val res = AiClient.chat(
            system = Prompts.system(channel, p, ci, smartFacts.value),
            history = history,
            userMessage = text
        )
        res.onSuccess { reply ->
            dao.addMessage(ChatMessage(channel = channel, role = "assistant", content = reply))
        }.onFailure { e ->
            dao.addMessage(
                ChatMessage(channel = channel, role = "assistant", content = "⚠️ ${e.message}")
            )
        }
    }

    fun chatFlow(channel: String) = dao.chatFlow(channel)

    fun clearChat(channel: String) = viewModelScope.launch(Dispatchers.IO) { dao.clearChat(channel) }

    // -------------------------------------------------------------- learning

    /**
     * Reads the user's own recent messages and stores a few observable patterns
     * in the smart profile. Only runs when learning is enabled, and only keeps
     * habits/preferences — never personality or medical guesses.
     */
    fun learnFromChats() = viewModelScope.launch {
        if (!learningEnabled.value || !AiClient.chatConfigured) return@launch
        val recent = withContext(Dispatchers.IO) { dao.recentChatAll(30) }
        val userLines = recent.filter { it.role == "user" }
            .take(15)
            .joinToString("\n") { "- ${it.content.take(120)}" }
        if (userLines.isBlank()) return@launch

        val res = AiClient.chat(
            system = Prompts.base(),
            history = emptyList(),
            userMessage = Prompts.learnPrompt(userLines, withContext(Dispatchers.IO) { dao.smartFacts() })
        )
        res.onSuccess { text ->
            val parsed = text.lines()
                .map { it.trim() }
                .filter { it.contains("|") && !it.startsWith("هیچ") }
                .mapNotNull { line ->
                    val k = line.substringBefore("|").trim()
                    val v = line.substringAfter("|").trim()
                    if (k.isBlank() || v.isBlank()) null else k to v
                }
                .take(3)
            withContext(Dispatchers.IO) {
                parsed.forEach { (k, v) -> dao.saveFact(SmartFact(key = k, value = v, source = "chat")) }
            }
        }
    }

    fun deleteFact(f: SmartFact) = viewModelScope.launch(Dispatchers.IO) { dao.deleteFact(f.id) }

    // ---- learning consent (stored as a plain preference fact) ----

    private val _learningEnabled = MutableStateFlow(true)
    val learningEnabled: StateFlow<Boolean> = _learningEnabled.asStateFlow()

    fun setLearningEnabled(enabled: Boolean) {
        _learningEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            if (enabled) dao.saveFact(SmartFact(key = "رضایت یادگیری", value = "فعال", source = "settings"))
            else {
                dao.clearFacts()
                dao.saveFact(SmartFact(key = "رضایت یادگیری", value = "غیرفعال", source = "settings"))
            }
        }
    }

    // ---------------------------------------------------------------- tasks

    fun saveTask(t: Task) = viewModelScope.launch(Dispatchers.IO) {
        val id = dao.saveTask(t)
        val app = getApplication<Application>()
        val saved = t.copy(id = if (t.id == 0L) id else t.id)
        com.dastyar.app.notifications.ReminderScheduler.schedule(app, saved)
    }

    fun toggleTask(t: Task) = viewModelScope.launch(Dispatchers.IO) {
        dao.updateTask(t.copy(done = !t.done))
    }

    fun deleteTask(t: Task) = viewModelScope.launch(Dispatchers.IO) {
        com.dastyar.app.notifications.ReminderScheduler.cancel(getApplication(), t)
        dao.deleteTask(t.id)
    }

    // ------------------------------------------------------------ data mgmt

    fun clearAllData(keepProfile: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        dao.clearCheckIns()
        dao.clearTasks()
        dao.clearAllChats()
        dao.clearSuggestions()
        dao.clearWeights()
        dao.clearFacts()
        if (!keepProfile) dao.saveProfile(Profile(id = 1, onboardingDone = false))
        withContext(Dispatchers.Main) {
            _suggestion.value = null
            _todayCheckIn.value = null
            _toast.value = "اطلاعات پاک شد"
        }
    }
}

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

    fun clearToast() { _toast.value = null }

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
        withContext(Dispatchers.Main) { _todayCheckIn.value = dao.checkIn(Dates.today()) }
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
     * result and as the fallback when the AI is unavailable.
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
        out += if (remaining > 0) "💧 آب | هدف امروز $waterGoal لیوان است؛ $remaining لیوان دیگر بنوش"
        else "💧 آب | هدف آب امروز کامل شد، عالی بود"

        val sleepRange = Health.sleepTargetHours(p)
        val target = "${Dates.fa(sleepRange.start.toInt())} تا ${Dates.fa(sleepRange.endInclusive.toInt())}"
        out += "😴 خواب | امشب بین $target ساعت بخواب"

        Health.restAdvice(ci)?.let { out += "🛋 استراحت | $it" }
        Health.activityMinutes(ci)?.let {
            out += "🚶 فعالیت | حدود ${Dates.fa(it)} دقیقه فعالیت سبک مناسب امروزه"
        }
        Health.sleepAdvice(p, ci)?.let { out += "😴 خواب | $it" }

        // Period line only when there is real cycle data.
        if (p?.lastPeriodDate?.isNotBlank() == true) {
            val until = Dates.daysUntilNextPeriod(p.lastPeriodDate, p.cycleLength)
            if (ci?.isPeriodDay == true) {
                out += "🩷 پریود | امروز روز پریوده؛ آب و استراحت را بیشتر کن"
            } else if (until in 0..3) {
                out += "🩷 پریود | حدود ${Dates.fa(until)} روز تا پریود بعدی مانده"
            }
        }

        // Skin line only when the user reported something today.
        ci?.skinStatus?.takeIf { it.isNotBlank() }?.let {
            out += "✨ پوست | وضعیت امروز «$it» ثبت شد؛ روتین ساده و ضدآفتاب را ادامه بده"
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

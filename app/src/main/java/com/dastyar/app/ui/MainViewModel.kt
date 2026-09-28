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
import com.dastyar.app.data.SavedImage
import com.dastyar.app.data.SavedRecipe
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

    // A short note when personalising with AI failed. The plan itself is always
    // still there, so this never replaces content, only annotates it.
    private val _suggestionError = MutableStateFlow<String?>(null)
    val suggestionError: StateFlow<String?> = _suggestionError.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    // ---- dynamic card about the user's declared medical conditions ----
    private val _conditionInfo = MutableStateFlow<String?>(null)
    val conditionInfo: StateFlow<String?> = _conditionInfo.asStateFlow()

    private val _loadingCondition = MutableStateFlow(false)
    val loadingCondition: StateFlow<Boolean> = _loadingCondition.asStateFlow()

    // A real, human-readable message when the AI could not be reached, so the
    // card shows an honest error instead of a silent empty state.
    private val _conditionError = MutableStateFlow<String?>(null)
    val conditionError: StateFlow<String?> = _conditionError.asStateFlow()

    private var conditionLoadedFor: String? = null
    private var conditionTopicIndex = 0

    private companion object {
        /** Persisted key for the last successfully generated condition card. */
        const val FACT_CONDITION_CARD = "کارت شرایط پزشکی"
        const val FACT_SUGGESTION_AI = "پیشنهاد هوشمند امروز"

        /**
         * A stored marker, not user-facing text: the cooking screen renders it
         * as the "reply was not complete" card and never shows raw model output.
         */
        const val MEAL_INVALID_REPLY = "[[meal_invalid]]"
    }

    // ---- smart tip for the current cycle day ----
    private val _cycleTip = MutableStateFlow<String?>(null)
    val cycleTip: StateFlow<String?> = _cycleTip.asStateFlow()

    private val _loadingCycleTip = MutableStateFlow(false)
    val loadingCycleTip: StateFlow<Boolean> = _loadingCycleTip.asStateFlow()

    private val _cycleTipError = MutableStateFlow<String?>(null)
    val cycleTipError: StateFlow<String?> = _cycleTipError.asStateFlow()

    private var cycleTipLoadedFor: String? = null

    // ---- AI summary for the skin card, from real recorded skin data ----
    private val _skinTip = MutableStateFlow<String?>(null)
    val skinTip: StateFlow<String?> = _skinTip.asStateFlow()

    private val _loadingSkinTip = MutableStateFlow(false)
    val loadingSkinTip: StateFlow<Boolean> = _loadingSkinTip.asStateFlow()

    private var skinTipLoadedFor: String? = null

    /**
     * Produces a short, personalised skin suggestion from the user's real skin
     * data (today's check-in, falling back to the questionnaire baseline). The
     * card already shows the raw summary, so a failure just keeps that.
     */
    fun loadSkinTip(force: Boolean = false) = viewModelScope.launch {
        val p = profile.value
        val ci = _todayCheckIn.value
        val summary = Health.skinSummary(p, ci) ?: run {
            _skinTip.value = null
            return@launch
        }
        val key = "$summary-${ci?.date ?: "base"}"
        if (!force && skinTipLoadedFor == key && _skinTip.value != null) return@launch
        if (!AiClient.chatConfigured) return@launch

        _loadingSkinTip.value = true
        val res = AiClient.chat(
            system = Prompts.base(),
            history = emptyList(),
            userMessage = Prompts.skinTipPrompt(profile = p, today = ci, summary = summary)
        )
        _loadingSkinTip.value = false
        res.onSuccess { text ->
            val cleaned = text.trim()
            if (cleaned.isNotBlank()) {
                _skinTip.value = cleaned
                skinTipLoadedFor = key
            }
        }
    }

    /**
     * Personalises today's cycle tip with AI when it is available, using the
     * real cycle day, phase, length and today's check-in. The local phase tip is
     * already shown by the UI, so any failure simply keeps that text and only
     * adds a short note.
     */
    fun loadCycleTip(force: Boolean = false) = viewModelScope.launch {
        val p = profile.value ?: return@launch
        val phase = Health.phase(p) ?: run {
            _cycleTip.value = null
            _cycleTipError.value = null
            return@launch
        }
        val day = Health.cycleDay(p)
        val key = "$day-${phase.name}-${p.cycleLength}-${p.periodDays}"
        if (!force && cycleTipLoadedFor == key && _cycleTip.value != null) return@launch
        if (!AiClient.chatConfigured) return@launch

        _loadingCycleTip.value = true
        _cycleTipError.value = null
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
                _cycleTipError.value = null
                cycleTipLoadedFor = key
            }
        }.onFailure {
            // The local tip is already on screen; just note that personalising failed.
            _cycleTipError.value = "شخصی‌سازی با هوش مصنوعی انجام نشد؛ پیشنهاد عمومی همین روز نمایش داده می‌شود."
        }
    }

    /**
     * Produces a short, educational explanation of the conditions the user
     * declared in their profile. Each forced load advances to a new topic, so
     * the card shows fresh, relevant content over time instead of a fixed
     * paragraph.
     *
     * Behaviour on failure: the last successfully generated text is kept
     * visible and a clear Persian error is surfaced — the dashboard never
     * empties out and the app never crashes.
     */
    fun loadConditionInfo(force: Boolean = false) = viewModelScope.launch {
        val p = profile.value ?: return@launch
        val conditions = p.medicalConditions.trim()
        if (conditions.isBlank()) {
            _conditionInfo.value = null
            _conditionError.value = null
            return@launch
        }

        // Restore the last good card from storage the first time we open the
        // dashboard, or when the declared condition changed.
        if (_conditionInfo.value == null) {
            val stored = withContext(Dispatchers.IO) { dao.smartFact(FACT_CONDITION_CARD)?.value }
            if (stored != null && conditionLoadedFor == conditions) _conditionInfo.value = stored
        }

        if (!force && conditionLoadedFor == conditions && _conditionInfo.value != null) return@launch

        if (!AiClient.chatConfigured) {
            // No key at all: keep any stored content, explain how to fix it.
            _conditionError.value = "برای تولید توضیح، کلید هوش مصنوعی در تنظیمات ثبت نشده است. " +
                    "اطلاعات ثبت‌شده خودت پایین همین کارت باقی می‌ماند."
            return@launch
        }

        conditionTopicIndex = if (force || conditionLoadedFor != conditions) {
            (conditionTopicIndex + 1) % Prompts.conditionTopics.size
        } else conditionTopicIndex
        val topic = Prompts.conditionTopics[conditionTopicIndex]

        _loadingCondition.value = true
        _conditionError.value = null
        val res = AiClient.chat(
            system = Prompts.base(),
            history = emptyList(),
            userMessage = Prompts.conditionPrompt(
                conditions, p.medications.trim(), p, topic, today = _todayCheckIn.value
            )
        )
        _loadingCondition.value = false
        res.onSuccess { text ->
            val cleaned = text.lines()
                .map { it.trim() }
                .filter { it.contains("|") }
                .joinToString("\n")
            if (cleaned.isNotBlank()) {
                _conditionInfo.value = cleaned
                _conditionError.value = null
                conditionLoadedFor = conditions
                withContext(Dispatchers.IO) {
                    dao.saveFact(SmartFact(key = FACT_CONDITION_CARD, value = cleaned, source = "condition"))
                }
            } else {
                _conditionError.value = "پاسخ مناسبی از سرویس هوش مصنوعی دریافت نشد. دوباره تلاش کن."
            }
        }.onFailure {
            // Keep whatever we already have; explain the problem plainly.
            _conditionError.value = it.message ?: "اتصال به هوش مصنوعی ناموفق بود. دوباره تلاش کن."
        }
    }

    fun clearToast() { _toast.value = null }

    // ---- deep-link: a notification tap asks the UI to open the check-in tab ----
    private val _openCheckIn = MutableStateFlow(0)
    val openCheckIn: StateFlow<Int> = _openCheckIn.asStateFlow()

    /** Requests that the UI jump to the daily check-in screen. */
    fun requestOpenCheckIn() { _openCheckIn.value = _openCheckIn.value + 1 }

    // ---- deep-link: a task notification tap asks the UI to open that task ----
    private val _openTask = MutableStateFlow(0L)
    val openTask: StateFlow<Long> = _openTask.asStateFlow()

    /** Requests that the UI open the tasks tab and focus this task. */
    fun requestOpenTask(id: Long) { _openTask.value = id }

    fun clearOpenTask() { _openTask.value = 0L }

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

    /**
     * Called whenever the app comes back to the foreground. If the calendar day
     * changed while the app was open or in the background, today's check-in is
     * reloaded (it no longer exists for the new date), so the short daily
     * questions appear again exactly once on the new day.
     */
    fun onAppResumed() = viewModelScope.launch {
        val today = Dates.today()
        if (_lastSeenDay == null) _lastSeenDay = today
        if (_lastSeenDay != today) {
            _lastSeenDay = today
            refreshToday()
            // Yesterday's suggestion and skin tip no longer apply.
            _suggestion.value = null
            _skinTip.value = null
            skinTipLoadedFor = null
        } else {
            // Same day: make sure a row saved elsewhere is not missed.
            refreshToday()
        }
    }

    private var _lastSeenDay: String? = null

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
     * dashboard shows it as day one of the history. Every answer the user gave
     * (skin, fatigue, sleep, water, stress, activity, cycle) is carried over so
     * no card says "nothing recorded" on day one. The mapping itself lives in
     * [Health.fromQuestionnaire] so onboarding and the dashboard agree.
     */
    private fun onboardingAsCheckIn(p: Profile, date: String): CheckIn =
        Health.fromQuestionnaire(p, date)

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

        // Direction-of-change line: compare today's energy/water with yesterday's
        // real record, so the plan acknowledges improvement or a rough day.
        val previous = history.filter { it.date != (ci?.date ?: Dates.today()) }.maxByOrNull { it.date }
        if (ci != null && previous != null) {
            val todayEnergy = energyScore(ci)
            val prevEnergy = energyScore(previous)
            if (todayEnergy != null && prevEnergy != null) {
                when {
                    todayEnergy - prevEnergy >= 15 ->
                        out += "📈 روند | انرژی‌ات نسبت به روز قبل بهتر شده؛ همین روند را با خواب منظم حفظ کن"
                    prevEnergy - todayEnergy >= 15 ->
                        out += "📉 روند | امروز انرژی‌ات کمتر از روز قبل است؛ بار کارت را کم کن و بیشتر استراحت کن"
                }
            }
            if (ci.waterGlasses < previous.waterGlasses - 2) {
                out += "💧 روند | آبت نسبت به روز قبل کمتر شده؛ امروز جبران کن"
            }
        }

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
            val phase = Health.phase(p)
            if (ci?.isPeriodDay == true) {
                out += "🩷 پریود | امروز روز پریوده؛ آب، آهن و استراحت را بیشتر کن"
            } else if (phase == Health.CyclePhase.OVULATION) {
                out += "🌸 چرخه | در بازه تخمک‌گذاری هستی؛ احتمال باروری بیشتر است و این روش پیشگیری قطعی نیست"
            } else if (until in 0..3) {
                out += "🩷 پریود | حدود ${Dates.fa(until)} روز تا پریود بعدی مانده؛ آهن و آب را جدی بگیر"
            } else if (phase == Health.CyclePhase.LUTEAL) {
                out += "🌙 چرخه | در مرحله لوتئالی؛ خواب منظم و کافئین کمتر به نوسان انرژی و خلق کمک می‌کند"
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
            if (local == null) {
                _toast.value = "کلید هوش مصنوعی تنظیم نشده است."
            } else {
                _suggestionError.value = "شخصی‌سازی با هوش مصنوعی غیرفعال است؛ " +
                        "این پیشنهادها از داده‌های خودت ساخته شده‌اند."
            }
            return@launch
        }

        _loadingSuggestion.value = true
        _suggestionError.value = null
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
                _suggestionError.value = null
                dao.saveSuggestion(merged)
            } else {
                if (local != null) dao.saveSuggestion(local)
                _suggestionError.value = "پاسخ مناسبی از سرویس هوش مصنوعی نرسید؛ " +
                        "پیشنهادهای محلی نمایش داده می‌شوند."
            }
        }.onFailure {
            // Keep the local plan; surface an honest, non-alarming message.
            if (local != null) {
                dao.saveSuggestion(local)
                _suggestionError.value = "اتصال به هوش مصنوعی برقرار نشد؛ " +
                        "پیشنهادهای محلی نمایش داده می‌شوند."
            } else {
                _toast.value = it.message
            }
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
            // The meal chats must only ever show a structured recipe. If the
            // model replied with prose, an echo of its own instructions, or
            // English reasoning, that text is discarded here so it can never be
            // stored or rendered. A clean marker makes the UI show a retry.
            val isMeal = channel == "lunch" || channel == "dinner"
            val looksLikeRecipe = reply.contains("###") ||
                    reply.contains("نام:") || reply.contains("مواد:")
            val content = if (isMeal && !looksLikeRecipe) MEAL_INVALID_REPLY else reply
            dao.addMessage(ChatMessage(channel = channel, role = "assistant", content = content))
        }.onFailure { e ->
            dao.addMessage(
                ChatMessage(channel = channel, role = "assistant", content = "⚠️ ${e.message}")
            )
        }
    }

    fun chatFlow(channel: String) = dao.chatFlow(channel)

    fun clearChat(channel: String) = viewModelScope.launch(Dispatchers.IO) { dao.clearChat(channel) }

    // ------------------------------------------------------- saved images

    val savedImages: StateFlow<List<SavedImage>> = dao.savedImagesFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Stores image bytes in the app and records them for "تصاویر من". */
    fun saveImage(bytes: ByteArray, prompt: String, kind: String) = viewModelScope.launch(Dispatchers.IO) {
        val name = com.dastyar.app.util.GallerySaver.saveToApp(getApplication(), bytes)
        dao.addSavedImage(
            SavedImage(fileName = name, prompt = prompt, kind = kind)
        )
    }

    fun deleteImage(id: Long, fileName: String) = viewModelScope.launch(Dispatchers.IO) {
        com.dastyar.app.util.GallerySaver.deleteFromApp(getApplication(), fileName)
        dao.deleteSavedImage(id)
    }

    // ------------------------------------------------------ saved recipes

    val savedRecipes: StateFlow<List<SavedRecipe>> = dao.savedRecipesFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveRecipe(title: String, meal: String, body: String) = viewModelScope.launch(Dispatchers.IO) {
        dao.addSavedRecipe(SavedRecipe(title = title, meal = meal, body = body))
    }

    fun deleteRecipe(id: Long) = viewModelScope.launch(Dispatchers.IO) { dao.deleteSavedRecipe(id) }

    // ---- pantry suggestion ("با چیزهایی که در خانه دارم") ----
    //
    // Runs the exact same request path as the working meal chat (same scope,
    // same AiClient.chat, same model chain), so it cannot fail for reasons
    // that only affect one screen. The screen observes these flows instead of
    // launching its own coroutine, which also prevents duplicate requests.

    private val _pantryText = MutableStateFlow<String?>(null)
    val pantryText: StateFlow<String?> = _pantryText.asStateFlow()

    private val _pantryLoading = MutableStateFlow(false)
    val pantryLoading: StateFlow<Boolean> = _pantryLoading.asStateFlow()

    private val _pantryError = MutableStateFlow<String?>(null)
    val pantryError: StateFlow<String?> = _pantryError.asStateFlow()

    private var pantryRunning = false

    fun suggestFromPantry(ingredients: String, servings: Int) {
        val text = ingredients.trim()
        if (text.isEmpty() || pantryRunning) return
        pantryRunning = true
        _pantryLoading.value = true
        _pantryError.value = null
        viewModelScope.launch {
            val res = AiClient.chat(
                system = Prompts.base(),
                history = emptyList(),
                userMessage = Prompts.pantrySuggestPrompt(text, servings)
            )
            _pantryLoading.value = false
            pantryRunning = false
            res.onSuccess { _pantryText.value = it }
                .onFailure { _pantryError.value = it.message ?: "خطا در دریافت پیشنهاد" }
        }
    }

    fun clearPantry() {
        _pantryText.value = null
        _pantryError.value = null
    }

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

    /** Rough 0-100 energy estimate from a check-in, or null when not recorded. */
    private fun energyScore(ci: CheckIn): Int? = when {
        ci.energyLevel.contains("خیلی خوب") -> 95
        ci.energyLevel.contains("خوب") -> 80
        ci.energyLevel.contains("متوسط") -> 60
        ci.energyLevel.contains("خیلی کم") -> 20
        ci.energyLevel.contains("کم") -> 38
        else -> null
    }

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

    // The AI extraction of a task sentence runs from the ViewModel so it is not
    // tied to the screen: leaving the tasks tab (or turning the phone) never
    // cancels the request or duplicates it.
    private val _taskExtract = MutableStateFlow<Task?>(null)
    val taskExtract: StateFlow<Task?> = _taskExtract.asStateFlow()

    private val _taskExtractLoading = MutableStateFlow(false)
    val taskExtractLoading: StateFlow<Boolean> = _taskExtractLoading.asStateFlow()

    private val _taskExtractError = MutableStateFlow<String?>(null)
    val taskExtractError: StateFlow<String?> = _taskExtractError.asStateFlow()

    private var taskExtractRunning = false

    fun clearTaskExtract() {
        _taskExtract.value = null
        _taskExtractError.value = null
    }

    /**
     * Extracts task fields from a natural sentence using the current AI. The
     * result is only a preview; nothing is saved here. The screen shows it for
     * confirmation, and the manual parser is used when the AI is unavailable.
     */
    fun extractTask(sentence: String) {
        val text = sentence.trim()
        if (text.isEmpty() || taskExtractRunning) return
        taskExtractRunning = true
        _taskExtractLoading.value = true
        _taskExtractError.value = null
        _taskExtract.value = null
        viewModelScope.launch {
            val res = com.dastyar.app.ai.TaskAi.extract(text)
            _taskExtractLoading.value = false
            taskExtractRunning = false
            res.onSuccess { _taskExtract.value = it }
                .onFailure { _taskExtractError.value = it.message ?: "خطا در پردازش جمله" }
        }
    }

    /**
     * Saves a task and schedules/cancels its reminder in one place. A task that
     * already existed has its old alarm cancelled first, so changing the time or
     * date never leaves the previous notification behind.
     */
    fun saveTask(t: Task) = viewModelScope.launch(Dispatchers.IO) {
        val app = getApplication<Application>()
        if (t.id != 0L) {
            dao.task(t.id)?.let { com.dastyar.app.notifications.ReminderScheduler.cancel(app, it) }
        }
        val id = dao.saveTask(t)
        val saved = t.copy(id = if (t.id == 0L) id else t.id)
        com.dastyar.app.notifications.ReminderScheduler.schedule(app, saved)
    }

    /** Marks a task done (or active again) and schedules/cancels its reminder. */
    fun toggleTask(t: Task) = viewModelScope.launch(Dispatchers.IO) {
        val app = getApplication<Application>()
        val updated = t.copy(done = !t.done)
        dao.updateTask(updated)
        if (updated.done) {
            com.dastyar.app.notifications.ReminderScheduler.cancel(app, updated)
        } else {
            com.dastyar.app.notifications.ReminderScheduler.schedule(app, updated)
        }
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

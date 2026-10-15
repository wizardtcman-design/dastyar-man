package com.dastyar.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dastyar.app.ai.AiClient
import com.dastyar.app.ai.Prompts
import com.dastyar.app.ai.ServiceKeys
import com.dastyar.app.data.ChatMessage
import com.dastyar.app.data.CheckIn
import com.dastyar.app.data.CheckInPrefs
import com.dastyar.app.data.Cycle
import com.dastyar.app.data.DailySuggestion
import com.dastyar.app.data.DastyarDatabase
import com.dastyar.app.data.Dates
import com.dastyar.app.data.Health
import com.dastyar.app.data.Jalali
import com.dastyar.app.data.PeriodDayPrefs
import com.dastyar.app.data.PeriodEvent
import com.dastyar.app.data.Profile
import com.dastyar.app.data.SavedImage
import com.dastyar.app.data.SavedRecipe
import com.dastyar.app.data.SmartFact
import com.dastyar.app.data.Task
import com.dastyar.app.data.WeightEntry
import com.dastyar.app.update.UpdateInfo
import com.dastyar.app.update.UpdateManager
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

    /**
     * Every period the user has recorded, oldest first. This is the history the
     * cycle ring learns from: the first row comes from onboarding, later rows
     * from the "شروع پریود" / "پایان پریود" taps.
     */
    val periodEvents: StateFlow<List<PeriodEvent>> = dao.periodEventsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val today: String get() = Dates.today()

    // ---- transient UI state ----
    private val _todayCheckIn = MutableStateFlow<CheckIn?>(null)
    val todayCheckIn: StateFlow<CheckIn?> = _todayCheckIn.asStateFlow()

    /**
     * The local day the daily check-in was last completed, read synchronously
     * from disk at start-up. It is the fast, reliable answer to "should the
     * questions appear today?" while the database read is still in flight, so a
     * finished check-in never makes the questions reappear on the next open.
     */
    private val _completedDay = MutableStateFlow(CheckInPrefs.doneDay(app))
    val completedDay: StateFlow<String?> = _completedDay.asStateFlow()

    /**
     * Today's answer to "are you on your period today?", read synchronously from
     * disk at start-up. `null` means not answered today. It is the single switch
     * that opens and closes the period-specific questions, so answering «نه»
     * keeps them closed after leaving and re-entering, and «پایان پریود» can
     * close them immediately.
     */
    private val _periodDayAnswer = MutableStateFlow(PeriodDayPrefs.answer(app, Dates.today()))
    val periodDayAnswer: StateFlow<Boolean?> = _periodDayAnswer.asStateFlow()

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
            userMessage = Prompts.skinTipPrompt(profile = p, today = ci, summary = summary),
            ctx = getApplication<Application>()
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
            ),
            ctx = getApplication<Application>()
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

    // ---- AI refinement of the cycle prediction (never blocking) ----

    /**
     * An AI-written sentence that explains the learned cycle prediction. The
     * local [Cycle] calculation is what actually drives the ring and the dates;
     * this text is only an extra, warmer explanation. It is optional by design:
     * when there is no key or no network, the dashboard simply keeps the local
     * summary and nothing breaks.
     */
    private val _cycleNote = MutableStateFlow<String?>(null)
    val cycleNote: StateFlow<String?> = _cycleNote.asStateFlow()

    private val _loadingCycleNote = MutableStateFlow(false)
    val loadingCycleNote: StateFlow<Boolean> = _loadingCycleNote.asStateFlow()

    private var cycleNoteLoadedFor: String? = null

    /**
     * Asks the AI to explain the current prediction in one short Persian
     * sentence, using only the real numbers already computed locally. Any
     * failure is silent: the local summary stays, so the user always sees an
     * answer.
     */
    fun loadCycleNote(force: Boolean = false) = viewModelScope.launch {
        val p = profile.value ?: return@launch
        val events = periodEvents.value
        val info = Cycle.info(p, events) ?: run {
            _cycleNote.value = null
            return@launch
        }
        if (!info.hasCycle) {
            _cycleNote.value = null
            return@launch
        }
        val key = "${info.startIso}-${info.length}-${info.periodDays}-${info.samples}-${Cycle.rawDay(info)}"
        if (!force && cycleNoteLoadedFor == key && _cycleNote.value != null) return@launch
        if (!AiClient.chatConfigured) return@launch

        _loadingCycleNote.value = true
        val res = AiClient.chat(
            system = Prompts.base(),
            history = emptyList(),
            userMessage = Prompts.cyclePredictionPrompt(
                day = Cycle.rawDay(info),
                length = info.length,
                periodDays = info.periodDays,
                samples = info.samples,
                overdueDays = Cycle.overdueDays(info),
                irregular = info.irregular,
                nextDate = Cycle.nextPeriodDate(info)
            ),
            ctx = getApplication<Application>()
        )
        _loadingCycleNote.value = false
        res.onSuccess { text ->
            val cleaned = text.trim()
            if (cleaned.isNotBlank()) {
                _cycleNote.value = cleaned
                cycleNoteLoadedFor = key
            }
        }
        // No onFailure branch: the local summary is the fallback, so a failure
        // is not worth interrupting the user with.
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
            ),
            ctx = getApplication<Application>()
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

    // ---- deep-link: an update notification tap opens the update screen ----
    private val _openUpdate = MutableStateFlow(0)
    val openUpdate: StateFlow<Int> = _openUpdate.asStateFlow()

    /** Requests that the UI open the in-app update screen. */
    fun requestOpenUpdate() { _openUpdate.value = _openUpdate.value + 1 }

    // ---- update banner: a newer release the user should be told about ----
    // Set whenever a newer version exists, independent of notification
    // permission, so the UI can show a tappable in-app bar as a fallback.
    private val _updateBanner = MutableStateFlow<UpdateInfo?>(null)
    val updateBanner: StateFlow<UpdateInfo?> = _updateBanner.asStateFlow()

    /**
     * Checks GitHub for a newer release and, when one exists, posts the
     * one-per-version notification AND exposes it via [updateBanner] so an
     * in-app bar can offer the update even when notifications are disabled.
     * Safe on every launch and resume: the interval and mutex in [UpdateManager]
     * make it a no-op most of the time.
     */
    fun checkForUpdates() = viewModelScope.launch {
        val update = UpdateManager.checkAndNotify(getApplication<Application>())
        if (update != null) _updateBanner.value = update
    }

    fun dismissUpdateBanner() { _updateBanner.value = null }

    init {
        viewModelScope.launch {
            refreshToday()
            profile.collect { p -> if (p != null) refreshToday() }
        }
        // Look for a new release as soon as the app opens; onAppResumed() repeats
        // this (interval-gated) every time the user returns to the app.
        checkForUpdates()
    }

    /** Loads today's check-in and today's cached suggestion (if any). */
    suspend fun refreshToday() {
        val t = Dates.today()
        val row = dao.checkIn(t)
        _todayCheckIn.value = row
        // Back-fill a row saved by an older build that did not write the marker,
        // so a previous «بله» is still honoured. A row without a "yes" is not a
        // recorded answer -- it may just be the row water logging created.
        _periodDayAnswer.value =
            PeriodDayPrefs.effective(PeriodDayPrefs.answer(app, t), row?.isPeriodDay == true)
        // A real row for today means today is done: keep the durable marker in
        // step so the decision does not depend on this read finishing in time.
        if (row != null) {
            if (_completedDay.value != t) _completedDay.value = t
            CheckInPrefs.markDone(getApplication(), t)
        }
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
            // Day one is genuinely recorded, so today is done: set the durable
            // marker too, so the daily questions are never asked again today.
            CheckInPrefs.markDone(getApplication(), first.date)
            withContext(Dispatchers.Main) {
                _completedDay.value = first.date
                _todayCheckIn.value = first
            }
        }
        // Seed the period history with the questionnaire's own period date, so
        // the very first cycle already exists in the history the ring learns
        // from. Later cycles are added by the "شروع پریود" tap. This only ever
        // adds a row; it never overwrites a date the user recorded later.
        if (p.lastPeriodDate.isNotBlank() && dao.periodEvent(p.lastPeriodDate) == null) {
            val firstSeed = dao.periodEvents().isEmpty()
            dao.savePeriodEvent(
                PeriodEvent(
                    startIso = p.lastPeriodDate,
                    endIso = if (firstSeed) Dates.plusDays(p.lastPeriodDate, p.periodDays - 1) else "",
                    source = "onboarding"
                )
            )
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

    /**
     * Records the start date of a new period.
     *
     * It does two things that must stay in step: it moves the profile's
     * current-cycle anchor (which the whole app already reads), and it adds a
     * [PeriodEvent] row so the real history keeps growing. The first event is
     * tagged `onboarding` when it is the very first one, so the history is
     * honest about where each date came from. The cycle day, the tip and the
     * period reminders all recalculate from the new date with no extra
     * machinery.
     */
    fun recordPeriodStart(dateIso: String) = viewModelScope.launch(Dispatchers.IO) {
        val current = dao.profile() ?: return@launch
        val updated = current.copy(lastPeriodDate = dateIso)
        dao.saveProfile(updated)

        // Keep the history in step with the anchor. A blank end is correct here:
        // the period has just started, so its end is not known yet.
        val existing = dao.periodEvent(dateIso)
        val hadAny = dao.periodEvents().isNotEmpty()
        dao.savePeriodEvent(
            existing?.copy(startIso = dateIso)
                ?: PeriodEvent(
                    startIso = dateIso,
                    endIso = "",
                    source = if (hadAny) "user" else "onboarding"
                )
        )

        // Re-arm the period reminders from the new date (same rule as saveProfile).
        com.dastyar.app.notifications.PeriodReminder.scheduleNext(getApplication(), updated)
        withContext(Dispatchers.Main) {
            _toast.value = "شروع پریود ثبت شد 🩸"
            _cycleTip.value = null
            cycleTipLoadedFor = null
            loadCycleTip(force = true)
        }
    }

    /**
     * Records the last day of bleeding for the current period.
     *
     * The end is written onto the matching history row (the one whose start is
     * the current anchor) so the app learns how long this user's period really
     * lasts instead of assuming the questionnaire number forever. If the end is
     * before the start, or absurdly far after it, the entry is rejected with a
     * plain message rather than corrupting the history.
     */
    fun recordPeriodEnd(dateIso: String) = viewModelScope.launch(Dispatchers.IO) {
        val current = dao.profile() ?: return@launch
        val anchor = current.lastPeriodDate
        if (anchor.isBlank()) {
            withContext(Dispatchers.Main) { _toast.value = "اول تاریخ شروع پریود را ثبت کن." }
            return@launch
        }
        if (!Cycle.isValidPeriod(anchor, dateIso)) {
            withContext(Dispatchers.Main) {
                _toast.value = "تاریخ پایان باید بعد از شروع پریود و منطقی باشد."
            }
            return@launch
        }

        val existing = dao.periodEvent(anchor)
        dao.savePeriodEvent(
            existing?.copy(endIso = dateIso)
                ?: PeriodEvent(startIso = anchor, endIso = dateIso, source = "user")
        )

        // Ending the period closes the period questions at once and returns the
        // check-in state to «نه، پریود نیستم», exactly as the user expects. The
        // answer for today and today's row are both moved back to "no"; the
        // recorded history is untouched, so nothing is lost.
        val today = Dates.today()
        PeriodDayPrefs.put(getApplication(), today, false)
        dao.checkIn(today)?.let { dao.saveCheckIn(it.copy(isPeriodDay = false)) }

        // The learned period length changed, so re-arm the whole cycle schedule
        // and refresh the tip.
        com.dastyar.app.notifications.PeriodReminder.scheduleNext(getApplication(), current)
        withContext(Dispatchers.Main) {
            _periodDayAnswer.value = false
            _todayCheckIn.value = dao.checkIn(today)
            _toast.value = "پایان پریود ثبت شد ✅"
            _cycleTip.value = null
            cycleTipLoadedFor = null
            loadCycleTip(force = true)
        }
    }

    /**
     * Deletes a recorded period from the history. Used when the user realises a
     * date was entered by mistake; the current anchor is only moved when the
     * deleted row was the anchor itself, so the ring never points at a period
     * that no longer exists.
     */
    fun deletePeriodEvent(startIso: String) = viewModelScope.launch(Dispatchers.IO) {
        val current = dao.profile() ?: return@launch
        val events = dao.periodEvents()
        val row = events.firstOrNull { it.startIso == startIso } ?: return@launch
        dao.deletePeriodEvent(row.id)
        if (current.lastPeriodDate == startIso) {
            val previous = events.filter { it.startIso != startIso }.maxByOrNull { it.startIso }
            dao.saveProfile(current.copy(lastPeriodDate = previous?.startIso.orEmpty()))
        }
        // The history changed, so the learned cycle and the alarms must move too.
        com.dastyar.app.notifications.PeriodReminder.scheduleNext(getApplication(), current)
        withContext(Dispatchers.Main) {
            _toast.value = "این پریود از تاریخچه حذف شد."
            _cycleTip.value = null
            cycleTipLoadedFor = null
            loadCycleTip(force = true)
        }
    }

    /**
     * Updates the cycle length (days). Persisted through the normal profile
     * pipeline so the ring, the phase and the reminders recalculate at once.
     */
    fun setCycleLength(days: Int) = viewModelScope.launch(Dispatchers.IO) {
        val current = dao.profile() ?: return@launch
        val updated = current.copy(cycleLength = days.coerceIn(15, 60))
        dao.saveProfile(updated)
        com.dastyar.app.notifications.PeriodReminder.scheduleNext(getApplication(), updated)
        withContext(Dispatchers.Main) {
            _cycleTip.value = null
            cycleTipLoadedFor = null
            loadCycleTip(force = true)
        }
    }

    /**
     * Updates the period length (days). Clamped to the cycle length so the
     * period can never run past the end of the cycle.
     */
    fun setPeriodDays(days: Int) = viewModelScope.launch(Dispatchers.IO) {
        val current = dao.profile() ?: return@launch
        val maxDays = minOf(12, current.cycleLength.coerceIn(15, 60))
        val updated = current.copy(periodDays = days.coerceIn(1, maxDays))
        dao.saveProfile(updated)
        withContext(Dispatchers.Main) {
            _cycleTip.value = null
            cycleTipLoadedFor = null
            loadCycleTip(force = true)
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
        val today = Dates.today()
        // Done if the durable marker says today is finished, or a row for today
        // really exists. The day is recomputed every call, so tomorrow the
        // questions come back on their own.
        if (_completedDay.value == today) return false
        return todayCheckIn?.date != today
    }

    /**
     * Saves a check-in for a specific date. Because [CheckIn.date] is the
     * primary key, saving twice on the same day updates the same row.
     *
     * The row is always stored under today's local day, so a draft that outlived
     * midnight can never be written under the wrong date. The durable completion
     * marker is set only AFTER the write actually succeeds: a failed save leaves
     * the questions available instead of silently marking the day done.
     */
    fun saveCheckIn(c: CheckIn) = viewModelScope.launch(Dispatchers.IO) {
        val today = Dates.today()
        val row = c.copy(date = today)
        try {
            dao.saveCheckIn(row)
            CheckInPrefs.markDone(getApplication(), today)
            // Keep the durable period-day answer in step with the saved row.
            PeriodDayPrefs.put(getApplication(), today, row.isPeriodDay)
            withContext(Dispatchers.Main) {
                _periodDayAnswer.value = row.isPeriodDay
                _completedDay.value = today
                _todayCheckIn.value = row
                _toast.value = "وضعیت امروز ذخیره شد ✅"
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                _toast.value = "ذخیره نشد؛ لطفاً دوباره تلاش کن"
            }
        }
    }

    /**
     * Records today's answer to "are you on your period today?" the moment the
     * user taps it, before the check-in is saved, so the choice survives leaving
     * the screen and can never be reversed by a later re-entry.
     *
     * Answering «بله» also makes the period actually active -- otherwise the
     * period questions would open today and vanish again tomorrow. An already
     * running period is left untouched (its start date must not move); a period
     * whose start is recent is re-opened rather than duplicated; anything older
     * starts a genuinely new cycle today.
     */
    fun setPeriodDay(yes: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val today = Dates.today()
        PeriodDayPrefs.put(getApplication(), today, yes)
        withContext(Dispatchers.Main) { _periodDayAnswer.value = yes }
        if (!yes) return@launch

        val current = dao.profile() ?: return@launch
        val anchor = current.lastPeriodDate
        val open = anchor.isNotBlank() &&
                dao.periodEvent(anchor)?.endIso?.isBlank() == true
        if (open) return@launch

        val recent = anchor.isNotBlank() && runCatching {
            val days = Jalali.daysBetween(anchor, today)
            days in 0..current.periodDays.coerceAtLeast(1)
        }.getOrDefault(false)
        if (recent) {
            val ev = dao.periodEvent(anchor)
            dao.savePeriodEvent(
                ev?.copy(endIso = "")
                    ?: PeriodEvent(startIso = anchor, endIso = "", source = "user")
            )
        } else {
            recordPeriodStart(today)
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
            ),
            ctx = getApplication<Application>()
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

    /**
     * Sends one message to a chat channel and stores the assistant's reply.
     *
     * [internalPrompt] is true for the app's own suggestion prompts (the automatic
     * lunch/dinner suggestion and the "new suggestion" button). Such a prompt is
     * stored with the role "internal", never "user", so it can never be shown as
     * a user bubble; the raw instruction template stays invisible in the UI.
     * A message the user typed themselves is a normal "user" message.
     */
    fun chat(channel: String, text: String, internalPrompt: Boolean = false) = viewModelScope.launch {
        dao.addMessage(
            ChatMessage(
                channel = channel,
                role = if (internalPrompt) "internal" else "user",
                content = text
            )
        )

        // An internal turn is presented to the model as an ordinary user turn,
        // so the request it receives is exactly the one it got before this
        // change: the conversation always starts with a user message and the
        // suggestion keeps working the way it always did.
        //
        // The message we just stored is dropped from the history: it is passed
        // again below as `userMessage`, so keeping it here would send the very
        // same turn to the model twice and double the prompt (which is what made
        // the long cooking prompt heavy and unreliable on small models).
        val raw = dao.recentChat(channel, 12).reversed()
        val priorTurns = if (raw.lastOrNull()?.content == text) raw.dropLast(1) else raw
        val history = priorTurns.map {
            (if (it.role == "internal") "user" else it.role) to it.content
        }
        val p = profile.value
        val ci = _todayCheckIn.value

        // The provider + model picked for this conversation (null = app default).
        val choice = ServiceKeys.conversationChoice(channel)

        val res = AiClient.chat(
            system = Prompts.system(channel, p, ci, smartFacts.value),
            history = history,
            userMessage = text,
            ctx = getApplication<Application>(),
            modelOverride = choice?.second,
            providerOverride = choice?.first
        )
        res.onSuccess { reply ->
            // The meal chats must only ever show a structured recipe. If the
            // model replied with prose, an echo of its own instructions, or
            // English reasoning, that text is discarded here so it can never be
            // stored or rendered. A clean marker makes the UI show a retry.
            val isMeal = channel == "lunch" || channel == "dinner"
            val looksLikeRecipe = reply.contains("###") ||
                    reply.contains("نام:") || reply.contains("مواد:") ||
                    reply.contains("مراحل:") || reply.contains("طرز تهیه")
            val content = if (isMeal && !looksLikeRecipe) MEAL_INVALID_REPLY else reply
            dao.addMessage(ChatMessage(channel = channel, role = "assistant", content = content))
        }.onFailure { e ->
            // The failing model's name is shown too, so the user can pick another
            // one from the chooser inside the input box and retry.
            val used = choice?.second
                ?: ServiceKeys.modelFor(choice?.first ?: ServiceKeys.textProvider())
            dao.addMessage(
                ChatMessage(
                    channel = channel, role = "assistant",
                    content = "⚠️ ${e.message}\n(مدل: $used — از انتخابگر مدل داخل کادر پیام می‌توانی مدل دیگری بگیری)"
                )
            )
        }
    }

    fun chatFlow(channel: String) = dao.chatFlow(channel)

    fun clearChat(channel: String) = viewModelScope.launch(Dispatchers.IO) { dao.clearChat(channel) }

    /**
     * Removes a single message from its conversation. History is stored locally
     * in Room with no server copy, so deleting the row is the complete operation:
     * the chat flow re-emits and the message is gone for good, even after the app
     * is restarted. Other messages and other conversations are untouched.
     */
    fun deleteMessage(id: Long) = viewModelScope.launch(Dispatchers.IO) { dao.deleteMessage(id) }

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
                userMessage = Prompts.pantrySuggestPrompt(text, servings),
                ctx = getApplication<Application>()
            )
            _pantryLoading.value = false
            pantryRunning = false
            res.onSuccess { _pantryText.value = it }
                .onFailure { _pantryError.value = it.message ?: "خطا در دریافت پیشنهاد" }        }
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
            userMessage = Prompts.learnPrompt(userLines, withContext(Dispatchers.IO) { dao.smartFacts() }),
            ctx = getApplication<Application>()
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
        // Store the time canonically as HH:mm. The manual form keeps exactly what
        // the user typed ("9", "9:30"), and an un-padded hour used to make the
        // alarm silently unschedulable; normalising here fixes old rows too.
        val clean = if (t.time.isBlank()) t
        else t.copy(
            time = com.dastyar.app.notifications.ReminderScheduler.normalizeTime(t.time)
                .ifBlank { t.time }
        )
        if (t.id != 0L) {
            dao.task(t.id)?.let { com.dastyar.app.notifications.ReminderScheduler.cancel(app, it) }
        }
        val id = dao.saveTask(clean)
        val saved = clean.copy(id = if (clean.id == 0L) id else clean.id)
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
        CheckInPrefs.clear(getApplication())
        PeriodDayPrefs.clear(getApplication())
        withContext(Dispatchers.Main) {
            _suggestion.value = null
            _todayCheckIn.value = null
            _completedDay.value = null
            _periodDayAnswer.value = null
            _toast.value = "اطلاعات پاک شد"
        }
    }
}

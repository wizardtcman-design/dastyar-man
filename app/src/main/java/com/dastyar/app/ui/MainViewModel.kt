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
import com.dastyar.app.data.Profile
import com.dastyar.app.data.Task
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

    val today: String get() = Dates.today()

    // ---- transient UI state ----
    private val _todayCheckIn = MutableStateFlow<CheckIn?>(null)
    val todayCheckIn: StateFlow<CheckIn?> = _todayCheckIn.asStateFlow()

    private val _suggestion = MutableStateFlow<String?>(null)
    val suggestion: StateFlow<String?> = _suggestion.asStateFlow()

    private val _loadingSuggestion = MutableStateFlow(false)
    val loadingSuggestion: StateFlow<Boolean> = _loadingSuggestion.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun clearToast() { _toast.value = null }

    init {
        viewModelScope.launch {
            refreshToday()
            profile.collect { p ->
                if (p != null) refreshToday()
            }
        }
    }

    /** Loads today's check-in and today's cached suggestion (if any). */
    suspend fun refreshToday() {
        val t = Dates.today()
        _todayCheckIn.value = dao.checkIn(t)
        val cached = dao.suggestion(t)
        if (cached != null) _suggestion.value = cached.content
    }

    // ------------------------------------------------------------- profile

    fun saveProfile(p: Profile) = viewModelScope.launch(Dispatchers.IO) {
        dao.saveProfile(p.copy(id = 1))
    }

    // ------------------------------------------------------------ check-in

    /**
     * Saves a check-in for a specific date. Because [CheckIn.date] is the
     * primary key, saving twice on the same day updates the same row - the
     * questionnaire is never asked twice in one day.
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
        withContext(Dispatchers.Main) { _todayCheckIn.value = cur.copy(waterGlasses = next) }
    }

    fun setWater(value: Int) = viewModelScope.launch(Dispatchers.IO) {
        val t = Dates.today()
        val cur = dao.checkIn(t) ?: CheckIn(date = t)
        dao.saveCheckIn(cur.copy(waterGlasses = value.coerceIn(0, 30)))
        _todayCheckIn.value = cur.copy(waterGlasses = value.coerceIn(0, 30))
    }

    // --------------------------------------------------------- suggestions

    /** Asks the real AI for today's personalised suggestions (once per day). */
    fun generateSuggestion(force: Boolean = false) = viewModelScope.launch {
        val t = Dates.today()
        if (!force) {
            val cached = dao.suggestion(t)
            if (cached != null && cached.content.isNotBlank()) {
                _suggestion.value = cached.content
                return@launch
            }
        }
        if (!AiClient.chatConfigured) {
            _toast.value = "کلید هوش مصنوعی تنظیم نشده است."
            return@launch
        }
        _loadingSuggestion.value = true
        val p = profile.value
        val ci = _todayCheckIn.value
        val goal = waterGoal(p, ci)
        val res = AiClient.chat(
            system = Prompts.base(),
            history = emptyList(),
            userMessage = Prompts.dailySuggestionPrompt(p, ci, goal)
        )
        _loadingSuggestion.value = false
        res.onSuccess { text ->
            _suggestion.value = text
            dao.saveSuggestion(DailySuggestion(date = t, content = text, waterGoal = goal))
        }.onFailure { _toast.value = it.message }
    }

    /** Daily water goal derived from the user's real data. */
    fun waterGoal(p: Profile?, ci: CheckIn?): Int = when {
        p == null -> 8
        p.waterIntake > 0 -> p.waterIntake.coerceIn(4, 15)
        else -> when {
            (ci?.physicalActivity ?: "").contains("زیاد") -> 10
            (ci?.stressLevel ?: "").contains("زیاد") -> 9
            p.fatigueLevel.contains("شدید") -> 9
            else -> 8
        }
    }

    // ----------------------------------------------------------------- chat

    fun chat(channel: String, text: String) = viewModelScope.launch {
        val userMsg = ChatMessage(channel = channel, role = "user", content = text)
        dao.addMessage(userMsg)

        val history = dao.recentChat(channel, 12).map { it.role to it.content }
        val p = profile.value
        val ci = _todayCheckIn.value

        val res = AiClient.chat(
            system = Prompts.system(channel, p, ci),
            history = history,
            userMessage = text
        )
        res.onSuccess { reply ->
            dao.addMessage(ChatMessage(channel = channel, role = "assistant", content = reply))
        }.onFailure { e ->
            dao.addMessage(
                ChatMessage(
                    channel = channel, role = "assistant",
                    content = "⚠️ ${e.message}"
                )
            )
        }
    }

    fun chatFlow(channel: String) = dao.chatFlow(channel)

    fun clearChat(channel: String) = viewModelScope.launch(Dispatchers.IO) { dao.clearChat(channel) }

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
        if (!keepProfile) dao.saveProfile(Profile(id = 1, onboardingDone = false))
        withContext(Dispatchers.Main) { _toast.value = "اطلاعات پاک شد" }
    }
}

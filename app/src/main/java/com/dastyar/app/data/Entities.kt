package com.dastyar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val date: String = "",              // yyyy-MM-dd
    val time: String = "",              // HH:mm
    val repeat: String = "none",        // none / daily / weekly / monthly
    val done: Boolean = false,
    val reminderEnabled: Boolean = false,
    val priority: String = "normal",    // low / normal / high
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * A stable key for the notification/alarm of this task, derived only from
     * the row id. It never changes, so cancelling and re-scheduling always hits
     * the exact same alarm and can never leave a duplicate behind.
     */
    val notifyId: Int get() = NOTIFY_BASE + (id % NOTIFY_SPAN).toInt()

    companion object {
        private const val NOTIFY_BASE = 10000
        private const val NOTIFY_SPAN = 1_000_000
    }
}

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channel: String = "general",    // general / period / skin / fatigue
    val role: String = "user",          // user / assistant
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_suggestions")
data class DailySuggestion(
    @PrimaryKey val date: String,
    val content: String = "",
    val waterGoal: Int = 8,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * An image produced or edited by the user, kept inside the app so it never has
 * to be generated again. The PNG bytes live in the app's private files
 * directory; the row stores the file name plus the prompt that made it.
 */
@Entity(tableName = "saved_images")
data class SavedImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String = "",          // relative to filesDir/images
    val prompt: String = "",
    val kind: String = "generate",      // generate / edit
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A recipe the user chose to keep from the cooking section. The full recipe
 * text is stored as-is so it can be shown again without another AI call.
 */
@Entity(tableName = "saved_recipes")
data class SavedRecipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val meal: String = "ناهار",          // ناهار / شام
    val body: String = "",              // the recipe text (ingredients + steps)
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * One recorded period, kept forever so the app can learn the user's real
 * cycle instead of guessing from a single date.
 *
 * [startIso] is the first day of bleeding, [endIso] the last day (blank while
 * the period is still going, or when the user never marked its end). Both are
 * Jalali ISO strings (yyyy-MM-dd), the same calendar as every other stored
 * date. [source] records where the row came from — "onboarding" for the very
 * first entry, "user" for a manual start/end tap, "auto" for a value the app
 * inferred itself — so the history stays honest about what was recorded by
 * whom. A start date is unique: re-recording the same day replaces the row
 * rather than creating a duplicate cycle.
 */
@Entity(tableName = "period_events", indices = [androidx.room.Index(value = ["startIso"], unique = true)])
data class PeriodEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startIso: String = "",          // yyyy-MM-dd (Jalali), first day
    val endIso: String = "",            // yyyy-MM-dd (Jalali), last day; "" = unknown
    val source: String = "user",        // onboarding / user / auto
    val createdAt: Long = System.currentTimeMillis()
) {
    /** True when the end of this period has been recorded. */
    val hasEnd: Boolean get() = endIso.isNotBlank()
}

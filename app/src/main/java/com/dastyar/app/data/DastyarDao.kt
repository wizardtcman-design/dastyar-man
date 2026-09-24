package com.dastyar.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DastyarDao {
    // ---- Profile ----
    @Query("SELECT * FROM profile WHERE id = 1")
    fun profileFlow(): Flow<Profile?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun profile(): Profile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(p: Profile)

    // ---- Check-ins ----
    @Query("SELECT * FROM checkins WHERE date = :date")
    suspend fun checkIn(date: String): CheckIn?

    @Query("SELECT * FROM checkins WHERE date = :date")
    fun checkInFlow(date: String): Flow<CheckIn?>

    @Query("SELECT * FROM checkins ORDER BY date DESC")
    fun allCheckIns(): Flow<List<CheckIn>>

    @Query("SELECT * FROM checkins ORDER BY date DESC LIMIT :n")
    suspend fun recentCheckIns(n: Int): List<CheckIn>

    @Query("SELECT COUNT(*) FROM checkins")
    suspend fun checkInCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCheckIn(c: CheckIn)

    @Query("DELETE FROM checkins WHERE date = :date")
    suspend fun deleteCheckIn(date: String)

    // ---- Weight log ----
    @Query("SELECT * FROM weight_log ORDER BY date ASC")
    fun weightFlow(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_log ORDER BY date DESC LIMIT 1")
    suspend fun latestWeight(): WeightEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWeight(w: WeightEntry)

    @Query("DELETE FROM weight_log")
    suspend fun clearWeights()

    // ---- Smart profile ----
    @Query("SELECT * FROM smart_profile ORDER BY updatedAt DESC")
    fun smartFactsFlow(): Flow<List<SmartFact>>

    @Query("SELECT * FROM smart_profile ORDER BY updatedAt DESC")
    suspend fun smartFacts(): List<SmartFact>

    @Query("SELECT * FROM smart_profile WHERE key = :key LIMIT 1")
    suspend fun smartFact(key: String): SmartFact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFact(f: SmartFact)

    @Query("DELETE FROM smart_profile")
    suspend fun clearFacts()

    @Query("DELETE FROM smart_profile WHERE id = :id")
    suspend fun deleteFact(id: Long)

    // ---- Tasks ----
    @Query("SELECT * FROM tasks ORDER BY date ASC, time ASC")
    fun allTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE reminderEnabled = 1 AND done = 0")
    suspend fun pendingReminders(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTask(t: Task): Long

    @Update
    suspend fun updateTask(t: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    // ---- Chat ----
    @Query("SELECT * FROM chat_messages WHERE channel = :channel ORDER BY timestamp ASC")
    fun chatFlow(channel: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE channel = :channel ORDER BY timestamp DESC LIMIT :n")
    suspend fun recentChat(channel: String, n: Int): List<ChatMessage>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :n")
    suspend fun recentChatAll(n: Int): List<ChatMessage>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE role = 'user'")
    suspend fun userMessageCount(): Int

    @Insert
    suspend fun addMessage(m: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE channel = :channel")
    suspend fun clearChat(channel: String)

    // ---- Suggestions ----
    @Query("SELECT * FROM daily_suggestions WHERE date = :date")
    suspend fun suggestion(date: String): DailySuggestion?

    @Query("SELECT * FROM daily_suggestions WHERE date = :date")
    fun suggestionFlow(date: String): Flow<DailySuggestion?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSuggestion(s: DailySuggestion)

    // ---- Data management ----
    @Query("DELETE FROM checkins")
    suspend fun clearCheckIns()

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllChats()

    @Query("DELETE FROM daily_suggestions")
    suspend fun clearSuggestions()
}

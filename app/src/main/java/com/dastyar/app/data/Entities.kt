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
    val createdAt: Long = System.currentTimeMillis()
)

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

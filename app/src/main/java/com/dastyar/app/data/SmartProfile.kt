package com.dastyar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One weight measurement per day. [date] is the primary key so recording twice
 * on the same day updates the same point instead of piling up duplicates.
 */
@Entity(tableName = "weight_log")
data class WeightEntry(
    @PrimaryKey val date: String,          // yyyy-MM-dd
    val weightKg: Float,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * The "smart profile": small, non-sensitive facts learned from the user's own
 * words and recorded data. Only written when the user has allowed learning.
 * Each row is one observation, so it can be shown, edited and deleted.
 */
@Entity(tableName = "smart_profile")
data class SmartFact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String = "",                  // e.g. "sleep_habit", "goal", "preference"
    val value: String = "",
    val source: String = "chat",           // chat / checkin / onboarding
    val updatedAt: Long = System.currentTimeMillis()
)

/** Web-friendly chat message used by the AI client. */
data class ChatTurn(val role: String, val content: String)

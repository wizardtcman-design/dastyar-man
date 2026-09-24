package com.dastyar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One daily check-in per calendar day. [date] is yyyy-MM-dd and unique,
 * so re-entering the app the same day edits the existing row instead of
 * asking the questions again.
 */
@Entity(tableName = "checkins")
data class CheckIn(
    @PrimaryKey val date: String,          // yyyy-MM-dd
    val timestamp: Long = System.currentTimeMillis(),

    val energyLevel: String = "",          // خیلی خوب / خوب / متوسط / کم / خیلی کم
    val fatigueSeverity: String = "",
    val sleepHours: Float = 0f,
    val sleepQuality: String = "",
    val waterGlasses: Int = 0,

    val skinStatus: String = "",           // بهتر شده / مثل قبل / بدتر شده
    val acneCount: String = "",
    val skinInflammation: String = "",
    val skinDryOily: String = "",
    val skinSensitivity: String = "",
    val skinNewProduct: String = "",

    val periodPain: String = "",
    val periodPainLevel: String = "",
    val periodPainLocation: String = "",
    val periodBleeding: String = "",
    val periodClots: String = "",
    val periodNausea: String = "",
    val periodDizziness: String = "",
    val periodHeadache: String = "",
    val periodMedication: String = "",

    val dizziness: String = "",
    val palpitations: String = "",
    val shortBreath: String = "",
    val headache: String = "",
    val appetite: String = "",
    val physicalActivity: String = "",
    val stressLevel: String = "",

    val isPeriodDay: Boolean = false,
    val cycleDay: Int = 0,
    val notes: String = ""
)

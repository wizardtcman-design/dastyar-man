package com.dastyar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 1,
    val firstName: String = "",
    val lastName: String = "",
    val age: Int = 0,

    // Period
    val lastPeriodDate: String = "",        // yyyy-MM-dd
    val cycleLength: Int = 28,
    val periodDays: Int = 5,
    val periodPainLevel: Int = 0,
    val painLocation: String = "",
    val bleedingLevel: String = "",
    val hasClots: String = "",
    val hasNausea: String = "",
    val hasDizziness: String = "",
    val hasHeadache: String = "",
    val hasBackPain: String = "",
    val painImpact: String = "",
    val painRelief: String = "",

    // Skin
    val skinType: String = "",
    val acneLevel: String = "",
    val acneLocation: String = "",
    val hasUnderSkinAcne: String = "",
    val hasWhiteheads: String = "",
    val hasBlackheads: String = "",
    val hasRedness: String = "",
    val dryOrOily: String = "",
    val hasSensitivity: String = "",
    val currentProducts: String = "",
    val recentProductChanges: String = "",

    // Fatigue
    val fatigueLevel: String = "",
    val fatigueDuration: String = "",
    val sleepQuality: String = "",
    val sleepHours: Float = 0f,
    val fatigueDizziness: String = "",
    val hasPalpitations: String = "",
    val hasShortBreath: String = "",
    val fatigueHeadache: String = "",
    val appetite: String = "",
    val waterIntake: Int = 0,
    val physicalActivity: String = "",
    val stressLevel: String = "",
    val fatiguePeriodLink: String = "",

    val onboardingDone: Boolean = false
)

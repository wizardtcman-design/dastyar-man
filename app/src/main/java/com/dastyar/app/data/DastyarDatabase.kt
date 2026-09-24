package com.dastyar.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Profile::class,
        CheckIn::class,
        Task::class,
        ChatMessage::class,
        DailySuggestion::class,
        WeightEntry::class,
        SmartFact::class
    ],
    version = 2,
    exportSchema = false
)
abstract class DastyarDatabase : RoomDatabase() {
    abstract fun dao(): DastyarDao

    companion object {
        @Volatile private var INSTANCE: DastyarDatabase? = null

        fun get(context: Context): DastyarDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DastyarDatabase::class.java,
                    "dastyar.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}

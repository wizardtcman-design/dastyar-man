package com.dastyar.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Profile::class,
        CheckIn::class,
        Task::class,
        ChatMessage::class,
        DailySuggestion::class,
        WeightEntry::class,
        SmartFact::class,
        SavedImage::class,
        SavedRecipe::class
    ],
    version = 5,
    exportSchema = false
)
abstract class DastyarDatabase : RoomDatabase() {
    abstract fun dao(): DastyarDao

    companion object {
        @Volatile private var INSTANCE: DastyarDatabase? = null

        // v2 -> v3 only adds the saved-images table, so profiles, check-ins,
        // chats, tasks and everything else are preserved untouched.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_images` (" +
                            "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                            "`fileName` TEXT NOT NULL, " +
                            "`prompt` TEXT NOT NULL, " +
                            "`kind` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        // v3 -> v4 only adds the saved-recipes table; everything else is kept.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_recipes` (" +
                            "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                            "`title` TEXT NOT NULL, " +
                            "`meal` TEXT NOT NULL, " +
                            "`body` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        // v4 -> v5 adds the task priority column. Existing tasks keep every
        // field they had and simply get the safe default "normal".
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `tasks` ADD COLUMN `priority` TEXT NOT NULL DEFAULT 'normal'"
                )
            }
        }

        fun get(context: Context): DastyarDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DastyarDatabase::class.java,
                    "dastyar.db"
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}

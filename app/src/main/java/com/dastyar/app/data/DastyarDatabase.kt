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
        SavedRecipe::class,
        PeriodEvent::class
    ],
    version = 7,
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

        // v5 -> v6 adds the period-history table, which the cycle ring uses to
        // learn the user's real cycle over time. Nothing else is touched, and
        // the current-cycle anchor on the profile is left exactly as it was.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `period_events` (" +
                            "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                            "`startIso` TEXT NOT NULL, " +
                            "`endIso` TEXT NOT NULL, " +
                            "`source` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                            "`index_period_events_startIso` ON `period_events` (`startIso`)"
                )
            }
        }

        // v6 -> v7 adds the period-symptom detail (blood colour, digestion and
        // discharge) to both the profile baseline and the daily check-in. Every
        // existing row keeps its data and simply gets an empty value, meaning
        // "not answered", so nothing is invented.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `bloodColor` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `digestionState` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `dischargeType` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `checkins` ADD COLUMN `periodBloodColor` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `checkins` ADD COLUMN `periodDigestion` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `checkins` ADD COLUMN `periodDischarge` TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): DastyarDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DastyarDatabase::class.java,
                    "dastyar.db"
                ).addMigrations(
                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}

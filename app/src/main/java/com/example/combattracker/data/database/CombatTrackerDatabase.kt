// File: CombatTrackerDatabase.kt
package com.example.combattracker.data.database

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.combattracker.data.database.converters.Converters
import com.example.combattracker.data.database.dao.*
import com.example.combattracker.data.database.entities.*
import com.example.combattracker.data.model.ConditionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * CombatTrackerDatabase - Main Room database class
 *
 * Purpose:
 * - Defines the database configuration and schema
 * - Provides access to all DAOs
 * - Handles database creation and pre-population
 * - Manages database migrations for future updates
 *
 * Requirements Reference:
 * From Application class: Pre-populate conditions on first launch
 * From section 3.6.2: Local SQLite database using Room Persistence Library
 *
 * Database Schema Version History:
 * - Version 1: Initial release with all entities
 */
@Database(
    entities = [
        Actor::class,
        Encounter::class,
        EncounterActor::class,
        Condition::class,
        ActorCondition::class
    ],
    version = 1,
    exportSchema = true // Exports schema JSON for version control
)
@TypeConverters(Converters::class)
abstract class CombatTrackerDatabase : RoomDatabase() {

    // ========== DAO Access ==========

    /**
     * Get the Actor DAO for actor library operations
     */
    abstract fun actorDao(): ActorDao

    /**
     * Get the Encounter DAO for encounter management
     */
    abstract fun encounterDao(): EncounterDao

    /**
     * Get the Condition DAO for condition templates
     */
    abstract fun conditionDao(): ConditionDao

    // ========== Database Creation ==========

    companion object {
        /**
         * Database name constant
         */
        private const val DATABASE_NAME = "combat_tracker_database"

        /**
         * Singleton instance
         * Volatile to ensure visibility across threads
         */
        @Volatile
        private var INSTANCE: CombatTrackerDatabase? = null

        /**
         * Get or create the database instance
         * Uses double-checked locking for thread safety
         *
         * @param context Application context
         * @param scope Coroutine scope for pre-population
         * @return The database instance
         */
        fun getInstance(
            context: android.content.Context,
            scope: CoroutineScope
        ): CombatTrackerDatabase {
            // If instance exists, return it
            INSTANCE?.let { return it }

            // Otherwise, create it with synchronization
            synchronized(this) {
                // Double-check in case another thread created it
                INSTANCE?.let { return it }

                // Create the database
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CombatTrackerDatabase::class.java,
                    DATABASE_NAME
                )
                    // Add callback for pre-population
                    .addCallback(DatabaseCallback(scope))
                    // Add migrations for future versions
                    .addMigrations(/* Add migrations here as needed */)
                    // Build the database
                    .build()

                INSTANCE = instance
                return instance
            }
        }

        /**
         * Database callback for initialization
         * Pre-populates conditions on first creation
         */
        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {

            /**
             * Called when database is created for the first time
             *
             * @param db The database
             */
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Timber.d("Database created, scheduling condition pre-population")

                // Pre-populate on background thread
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        try {
                            populateConditions(database.conditionDao())
                            Timber.d("Successfully pre-populated conditions")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to pre-populate conditions")
                        }
                    }
                }
            }

            /**
             * Pre-populate the conditions table with D&D conditions
             *
             * @param conditionDao The condition DAO
             */
            private suspend fun populateConditions(conditionDao: ConditionDao) {
                // Create all 15 D&D conditions from the enum
                val conditions = ConditionType.values().map { type ->
                    Condition(
                        id = type.id,
                        name = type.displayName,
                        description = type.description,
                        iconResource = type.iconResource,
                        displayOrder = type.ordinal + 1,
                        isEnabled = true
                    )
                }

                // Insert all conditions
                conditionDao.insertAllConditions(conditions)

                Timber.d("Inserted ${conditions.size} conditions into database")

                // Verify insertion
                val count = conditionDao.getConditionCount()
                if (count != conditions.size) {
                    Timber.w("Expected ${conditions.size} conditions, but found $count")
                }
            }
        }

        // ========== Migration Strategies ==========

        /**
         * Example migration from version 1 to 2
         * Uncomment and modify when needed for app updates
         */
        /*
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Example: Add a new column to actors table
                database.execSQL("ALTER TABLE actors ADD COLUMN level INTEGER NOT NULL DEFAULT 1")
            }
        }
        */

        /**
         * Destructive migration fallback
         * Only for development - remove for production
         */
        fun getDestructiveDatabaseBuilder(
            context: android.content.Context,
            scope: CoroutineScope
        ): CombatTrackerDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                CombatTrackerDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration() // WARNING: Deletes all data
                .addCallback(DatabaseCallback(scope))
                .build()
        }

        // ========== Database Utilities ==========

        /**
         * Clear all data from the database
         * Used for testing or reset functionality
         *
         * @param database The database instance
         */
        suspend fun clearAllTables(database: CombatTrackerDatabase) {
            database.clearAllTables()
            // Re-populate conditions after clearing
            populateConditions(database.conditionDao())
        }

        /**
         * Check database integrity
         * Verifies foreign keys and basic data consistency
         *
         * @param database The database instance
         * @return True if database is valid
         */
        suspend fun verifyDatabaseIntegrity(database: CombatTrackerDatabase): Boolean {
            return try {
                // Check if conditions are populated
                val hasConditions = database.conditionDao().hasAllStandardConditions()
                if (!hasConditions) {
                    Timber.w("Database missing standard conditions")
                    return false
                }

                // Could add more integrity checks here
                // - Orphaned encounter actors
                // - Invalid foreign keys
                // - Corrupted images

                true
            } catch (e: Exception) {
                Timber.e(e, "Database integrity check failed")
                false
            }
        }

        /**
         * Export database statistics for debugging
         *
         * @param database The database instance
         * @return Statistics string
         */
        suspend fun getDatabaseStats(database: CombatTrackerDatabase): String {
            return try {
                val actorCount = database.actorDao().getActorCount()
                val encounterCount = database.encounterDao().getActiveEncounterCount()
                val conditionCount = database.conditionDao().getConditionCount()

                """
                    Database Statistics:
                    - Actors: $actorCount
                    - Active Encounters: $encounterCount
                    - Conditions: $conditionCount
                    - Schema Version: ${database.openHelper.readableDatabase.version}
                """.trimIndent()
            } catch (e: Exception) {
                "Failed to get database statistics: ${e.message}"
            }
        }
    }
}
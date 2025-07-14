// File: CombatTrackerApplication.kt
// Location: app/src/main/java/com/example/combattracker/CombatTrackerApplication.kt

package com.example.combattracker

import android.app.Application
import com.example.combattracker.data.database.CombatTrackerDatabase
import com.example.combattracker.data.database.dao.ActorDao
import com.example.combattracker.data.database.dao.ConditionDao
import com.example.combattracker.data.database.dao.EncounterDao
import com.example.combattracker.data.repository.ActorRepository
import com.example.combattracker.data.repository.EncounterRepository
import com.example.combattracker.data.repository.ImageRepository
import com.example.combattracker.utils.FileStorageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

/**
 * CombatTrackerApplication - Main application class
 *
 * Purpose:
 * - Initializes app-wide dependencies
 * - Provides database and repository instances
 * - Sets up logging with Timber
 * - Acts as a simple dependency injection container
 *
 * This class uses manual dependency injection for simplicity.
 * For larger apps, consider using Dagger/Hilt.
 *
 * Requirements Reference:
 * From section 3.6.2: Pre-populate conditions on first app launch
 * From development plan: Application class for initialization
 */
class CombatTrackerApplication : Application() {

    /**
     * Application scope for long-running coroutines
     * Uses SupervisorJob so child failures don't cancel the scope
     */
    private val applicationScope = CoroutineScope(SupervisorJob())

    // ========== Database and DAOs ==========

    /**
     * Database instance - created lazily on first access
     * This ensures the database is only created when needed
     */
    val database: CombatTrackerDatabase by lazy {
        CombatTrackerDatabase.getInstance(this, applicationScope)
    }

    /**
     * DAO instances - accessed through the database
     */
    val actorDao: ActorDao by lazy { database.actorDao() }
    val encounterDao: EncounterDao by lazy { database.encounterDao() }
    val conditionDao: ConditionDao by lazy { database.conditionDao() }

    // ========== Repositories ==========

    /**
     * File storage helper for managing images
     */
    val fileStorageHelper: FileStorageHelper by lazy {
        FileStorageHelper(this)
    }

    /**
     * Image repository for portrait and background management
     */
    val imageRepository: ImageRepository by lazy {
        ImageRepository(this, fileStorageHelper)
    }

    /**
     * Actor repository for managing the actor library
     */
    val actorRepository: ActorRepository by lazy {
        ActorRepository(actorDao, imageRepository)
    }

    /**
     * Encounter repository for managing encounters
     */
    val encounterRepository: EncounterRepository by lazy {
        EncounterRepository(encounterDao, actorDao, conditionDao)
    }

    // ========== Application Lifecycle ==========

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        initializeTimber()

        // Clean up temp files on app start
        cleanupTempFiles()

        Timber.d("CombatTrackerApplication initialized")
    }

    /**
     * Initialize Timber logging
     * In debug builds, logs to Logcat
     * In release builds, you might want to add crash reporting
     */
    private fun initializeTimber() {
        if (BuildConfig.DEBUG) {
            // Debug tree logs everything
            Timber.plant(Timber.DebugTree())
        } else {
            // Release tree could filter logs or send to crash reporting
            // For now, we'll just use debug tree since we're not publishing
            Timber.plant(Timber.DebugTree())
        }
    }

    /**
     * Clean up temporary files on app start
     * Prevents temp directory from growing indefinitely
     */
    private fun cleanupTempFiles() {
        try {
            val cleanedCount = fileStorageHelper.cleanTempDirectory()
            if (cleanedCount > 0) {
                Timber.d("Cleaned up $cleanedCount temporary files")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to clean temporary files")
        }
    }

    /**
     * Called when the system is running low on memory
     * Good place to clear caches
     */
    override fun onLowMemory() {
        super.onLowMemory()
        Timber.w("System low on memory - clearing caches")

        // Clear any image caches
        // Glide will handle its own cache clearing

        // Could also clear any in-memory caches here
    }

    /**
     * Called when the system requests memory trim
     * More granular than onLowMemory
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            TRIM_MEMORY_UI_HIDDEN -> {
                // UI is hidden, good time to release UI resources
                Timber.d("UI hidden - releasing UI resources")
            }
            TRIM_MEMORY_RUNNING_CRITICAL -> {
                // Still running but critically low on memory
                Timber.w("Memory critical - clearing all caches")
            }
        }
    }
}
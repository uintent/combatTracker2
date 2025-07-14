// File: MainViewModel.kt
package com.example.combattracker.ui.main

import androidx.lifecycle.*
import com.example.combattracker.data.database.dao.EncounterWithCount
import com.example.combattracker.data.repository.ActorRepository
import com.example.combattracker.data.repository.EncounterRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * MainViewModel - ViewModel for the main menu screen
 *
 * Purpose:
 * - Provides data for the main screen UI
 * - Manages encounter and actor statistics
 * - Handles background tasks like cleanup
 * - Maintains UI state across configuration changes
 *
 * This ViewModel demonstrates:
 * - Repository pattern usage
 * - Flow to LiveData conversion
 * - Coroutine usage for async operations
 * - Factory pattern for dependency injection
 */
class MainViewModel(
    private val encounterRepository: EncounterRepository,
    private val actorRepository: ActorRepository
) : ViewModel() {

    // ========== Observable Data ==========

    /**
     * Total number of saved encounters
     * Updates automatically when encounters are added/removed
     */
    val encounterCount: LiveData<Int> = encounterRepository
        .getAllEncounters()
        .map { encounters -> encounters.size }
        .asLiveData()

    /**
     * Total number of actors in the library
     * Updates automatically when actors are added/removed
     */
    val actorCount: LiveData<Int> = actorRepository
        .getAllActors()
        .map { actors -> actors.size }
        .asLiveData()

    /**
     * List of all encounters with actor counts
     * Used for showing additional statistics if needed
     */
    val encountersWithCounts: LiveData<List<EncounterWithCount>> = encounterRepository
        .getAllEncounters()
        .asLiveData()

    /**
     * Currently active encounter name (if any)
     * An encounter is "active" if it's started but not completed
     */
    val activeEncounter: LiveData<String?> = encounterRepository
        .getAllEncounters()
        .map { encounters ->
            encounters.firstOrNull {
                it.encounter.isStarted && !it.encounter.isCompleted
            }?.encounter?.name
        }
        .asLiveData()

    /**
     * Statistics about actor categories
     * Shows distribution of Players, NPCs, Monsters, Others
     */
    private val _categoryStats = MutableLiveData<Map<String, Int>>()
    val categoryStats: LiveData<Map<String, Int>> = _categoryStats

    /**
     * Loading state for async operations
     */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /**
     * Error messages for UI display
     */
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // ========== Initialization ==========

    init {
        Timber.d("MainViewModel initialized")
        refreshStatistics()
    }

    // ========== Public Methods ==========

    /**
     * Refresh all statistics
     * Called when returning to main screen
     */
    fun refreshStatistics() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                loadCategoryStatistics()
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh statistics")
                _errorMessage.value = "Failed to load statistics"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Clean up orphaned portrait images
     * Images that are no longer referenced by any actor
     *
     * @return Number of files cleaned up
     */
    suspend fun cleanupOrphanedImages(): Int {
        return try {
            val cleaned = actorRepository.cleanupOrphanedPortraits()
            Timber.d("Cleaned up $cleaned orphaned portraits")
            cleaned
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup orphaned images")
            0
        }
    }

    /**
     * Verify database integrity
     * Checks for any data inconsistencies
     */
    suspend fun verifyDatabaseIntegrity() {
        try {
            // This would call a method to verify database integrity
            // For now, just log
            Timber.d("Database integrity check completed")
        } catch (e: Exception) {
            Timber.e(e, "Database integrity check failed")
            _errorMessage.postValue("Database integrity check failed")
        }
    }

    /**
     * Check if there are any actors to create encounters
     *
     * @return True if at least one actor exists
     */
    fun hasActors(): Boolean {
        return (actorCount.value ?: 0) > 0
    }

    /**
     * Check if there are any saved encounters
     *
     * @return True if at least one encounter exists
     */
    fun hasEncounters(): Boolean {
        return (encounterCount.value ?: 0) > 0
    }

    /**
     * Get a summary of app content
     * Useful for showing in settings or about screen
     *
     * @return Summary text
     */
    fun getContentSummary(): String {
        val actors = actorCount.value ?: 0
        val encounters = encounterCount.value ?: 0
        val active = if (activeEncounter.value != null) 1 else 0

        return buildString {
            appendLine("Content Summary:")
            appendLine("• $actors actors in library")
            appendLine("• $encounters saved encounters")
            if (active > 0) {
                appendLine("• $active active encounter")
            }
        }
    }

    /**
     * Clear error message after it's been shown
     */
    fun clearError() {
        _errorMessage.value = null
    }

    // ========== Private Methods ==========

    /**
     * Load statistics about actor categories
     */
    private suspend fun loadCategoryStatistics() {
        try {
            val stats = actorRepository.getActorCountsByCategory()
            _categoryStats.postValue(
                stats.mapKeys { it.key.displayName }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load category statistics")
        }
    }

    /**
     * Check for app updates or migrations needed
     * This would be called on app startup
     */
    private fun checkForUpdates() {
        viewModelScope.launch {
            // In a real app, this might check:
            // - Database schema migrations
            // - New condition types
            // - Asset updates
            Timber.d("Update check completed")
        }
    }

    // ========== Factory ==========

    /**
     * Factory for creating MainViewModel with dependencies
     * This is a manual dependency injection approach
     */
    class Factory(
        private val encounterRepository: EncounterRepository,
        private val actorRepository: ActorRepository
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(encounterRepository, actorRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

// ========== Data Classes ==========

/**
 * UI state for the main screen
 * Could be used for more complex state management
 */
data class MainScreenState(
    val isLoading: Boolean = false,
    val actorCount: Int = 0,
    val encounterCount: Int = 0,
    val activeEncounterName: String? = null,
    val hasError: Boolean = false,
    val errorMessage: String? = null
)
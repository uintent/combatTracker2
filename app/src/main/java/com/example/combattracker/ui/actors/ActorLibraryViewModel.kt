// File: ActorLibraryViewModel.kt
// Location: app/src/main/java/com/example/combattracker/ui/actors/ActorLibraryViewModel.kt

package com.example.combattracker.ui.actors

import androidx.lifecycle.*
import com.example.combattracker.data.database.entities.Actor
import com.example.combattracker.data.model.ActorCategory
import com.example.combattracker.data.repository.ActorRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ActorLibraryViewModel - ViewModel for the Actor Library screen
 *
 * Purpose:
 * - Manages actor list with search, filter, and sort capabilities
 * - Handles actor deletion
 * - Provides reactive updates when data changes
 * - Maintains UI state across configuration changes
 *
 * Requirements Reference:
 * From section 3.1.3: Actor Library Management
 * - Search: Text-based search within actor names
 * - Sort: Alphabetical sorting by name, category, or initiative modifier
 * - Filter: Filter by actor category
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActorLibraryViewModel(
    private val actorRepository: ActorRepository
) : ViewModel() {

    // ========== UI State ==========

    /**
     * Current search query
     */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Selected category filters
     * Empty set means show all categories
     */
    private val _selectedCategories = MutableStateFlow<Set<ActorCategory>>(emptySet())
    val selectedCategories: StateFlow<Set<ActorCategory>> = _selectedCategories.asStateFlow()

    /**
     * Current sort field
     */
    private val _sortBy = MutableStateFlow("name")
    val sortBy: StateFlow<String> = _sortBy.asStateFlow()

    /**
     * Loading state
     */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /**
     * Error messages
     */
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // ========== Actors Flow ==========

    /**
     * Combined flow of actors with all filters applied
     * Automatically updates when any filter changes or data updates
     */
    val actors: Flow<List<Actor>> = combine(
        _searchQuery,
        _selectedCategories,
        _sortBy
    ) { query, categories, sort ->
        Triple(query, categories, sort)
    }.flatMapLatest { (query, categories, sort) ->
        // If no filters, use optimized queries
        when {
            query.isEmpty() && categories.isEmpty() -> {
                // No filters - use direct sort query
                actorRepository.getActorsSorted(sort)
            }
            query.isNotEmpty() && categories.isEmpty() -> {
                // Search only
                actorRepository.searchActors(query)
                    .map { actors -> sortActors(actors, sort) }
            }
            query.isEmpty() && categories.isNotEmpty() -> {
                // Category filter only
                if (categories.size == 1) {
                    actorRepository.getActorsByCategory(categories.first())
                        .map { actors -> sortActors(actors, sort) }
                } else {
                    // Multiple categories - combine flows
                    val categoryFlows = categories.map { category ->
                        actorRepository.getActorsByCategory(category)
                    }

                    combine(categoryFlows) { actorArrays ->
                        // Flatten the array of lists into a single list
                        actorArrays.flatMap { it }.distinctBy { it.id }
                    }.map { actors -> sortActors(actors, sort) }
                }
            }
            else -> {
                // Both search and category filters
                actorRepository.searchActorsAdvanced(
                    query = query,
                    categories = categories.toList(),
                    sortBy = sort,
                    ascending = true
                )
            }
        }
    }.catch { exception ->
        Timber.e(exception, "Error loading actors")
        _errorMessage.postValue("Failed to load actors")
        emit(emptyList())
    }

    // ========== Public Methods ==========

    /**
     * Set search query
     *
     * @param query Search text
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        Timber.d("Search query updated: $query")
    }

    /**
     * Toggle category filter
     *
     * @param category Category to toggle
     */
    fun toggleCategoryFilter(category: ActorCategory) {
        _selectedCategories.update { current ->
            if (category in current) {
                current - category
            } else {
                current + category
            }
        }
        Timber.d("Category filter toggled: $category")
    }

    /**
     * Clear all category filters
     */
    fun clearCategoryFilters() {
        _selectedCategories.value = emptySet()
        Timber.d("Category filters cleared")
    }

    /**
     * Set sort field
     *
     * @param field Sort field: "name", "category", or "initiative"
     */
    fun setSortBy(field: String) {
        _sortBy.value = field
        Timber.d("Sort by updated: $field")
    }

    /**
     * Delete an actor
     *
     * @param actor Actor to delete
     * @return Result of deletion
     */
    suspend fun deleteActor(actor: Actor): Result<Unit> {
        _isLoading.value = true
        return try {
            val result = actorRepository.deleteActor(actor)
            _isLoading.value = false
            result
        } catch (e: Exception) {
            _isLoading.value = false
            Timber.e(e, "Failed to delete actor")
            Result.failure(e)
        }
    }

    /**
     * Check if any filters are active
     *
     * @return True if search or category filters are active
     */
    fun hasActiveFilters(): Boolean {
        return _searchQuery.value.isNotEmpty() || _selectedCategories.value.isNotEmpty()
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Force refresh after edit
     * Called when returning from edit screen
     */
    fun refreshAfterEdit() {
        // The Flow will automatically update due to Room's reactive queries
        Timber.d("Refresh triggered after edit")
    }

    // ========== Private Methods ==========

    /**
     * Sort actors by specified field
     *
     * @param actors List to sort
     * @param sortBy Sort field
     * @return Sorted list
     */
    private fun sortActors(actors: List<Actor>, sortBy: String): List<Actor> {
        return when (sortBy) {
            "name" -> actors.sortedBy { it.name.lowercase() }
            "category" -> actors.sortedBy { it.category }
                .sortedBy { it.name.lowercase() }
            "initiative" -> actors.sortedByDescending { it.initiativeModifier }
                .sortedBy { it.name.lowercase() }
            else -> actors
        }
    }

    // ========== Factory ==========

    /**
     * Factory for creating ActorLibraryViewModel with dependencies
     */
    class Factory(
        private val actorRepository: ActorRepository
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ActorLibraryViewModel::class.java)) {
                return ActorLibraryViewModel(actorRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
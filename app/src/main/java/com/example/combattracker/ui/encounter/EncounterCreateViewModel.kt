// File: EncounterCreateViewModel.kt
// Location: app/src/main/java/com/example/combattracker/ui/encounter/EncounterCreateViewModel.kt

package com.example.combattracker.ui.encounter

import androidx.lifecycle.*
import com.example.combattracker.data.database.entities.Actor
import com.example.combattracker.data.repository.ActorRepository
import com.example.combattracker.data.repository.EncounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * EncounterCreateViewModel - ViewModel for creating encounters
 *
 * Purpose:
 * - Manages encounter creation flow
 * - Tracks selected actors and quantities
 * - Handles encounter saving
 * - Provides actor list with selection state
 *
 * Requirements Reference:
 * From section 3.3.1: Encounter Creation Workflow
 * - Optional encounter name
 * - Actor selection with quantities
 * - Save or start immediately
 */
class EncounterCreateViewModel(
    private val encounterRepository: EncounterRepository,
    private val actorRepository: ActorRepository
) : ViewModel() {

    // ========== State Properties ==========

    /**
     * Encounter name (optional)
     */
    private val _encounterName = MutableLiveData("")
    val encounterName: LiveData<String> = _encounterName

    /**
     * Selected actors with quantities
     * Map of actor ID to quantity
     */
    private val _selectedActors = MutableLiveData<Map<Long, Int>>(emptyMap())
    val selectedActors: LiveData<Map<Long, Int>> = _selectedActors

    /**
     * Loading state
     */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /**
     * Save complete event with encounter ID and start flag
     */
    private val _saveComplete = MutableLiveData<Pair<Long, Boolean>?>()
    val saveComplete: LiveData<Pair<Long, Boolean>?> = _saveComplete

    /**
     * Error messages
     */
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // ========== Actors Flow ==========

    /**
     * Flow of all actors with selection state
     */
    val actors: Flow<List<ActorSelectionState>> = actorRepository.getAllActors()
        .map { actorList ->
            actorList.map { actor ->
                ActorSelectionState(
                    actor = actor,
                    isSelected = _selectedActors.value?.containsKey(actor.id) ?: false,
                    quantity = _selectedActors.value?.get(actor.id) ?: 1
                )
            }
        }

    // ========== Public Methods ==========

    /**
     * Set encounter name
     */
    fun setEncounterName(name: String) {
        _encounterName.value = name
    }

    /**
     * Set actor quantity (adds if not selected)
     */
    fun setActorQuantity(actor: Actor, quantity: Int) {
        _selectedActors.value = _selectedActors.value?.toMutableMap()?.apply {
            if (quantity > 0) {
                this[actor.id] = quantity
            } else {
                remove(actor.id)
            }
        }
        Timber.d("Set quantity for ${actor.name}: $quantity")
    }

    /**
     * Remove actor from selection
     */
    fun removeActor(actorId: Long) {
        _selectedActors.value = _selectedActors.value?.toMutableMap()?.apply {
            remove(actorId)
        }
        Timber.d("Removed actor: $actorId")
    }

    /**
     * Get current quantity for an actor
     */
    fun getActorQuantity(actorId: Long): Int {
        return _selectedActors.value?.get(actorId) ?: 1
    }

    /**
     * Check if any actors are selected
     */
    fun hasSelectedActors(): Boolean {
        return !_selectedActors.value.isNullOrEmpty()
    }

    /**
     * Save the encounter
     *
     * @param startImmediately Whether to start combat immediately after saving
     */
    suspend fun saveEncounter(startImmediately: Boolean) {
        _isLoading.value = true
        _errorMessage.value = null

        try {
            val name = _encounterName.value?.takeIf { it.isNotBlank() }
            val selected = _selectedActors.value ?: emptyMap()

            if (selected.isEmpty()) {
                _errorMessage.value = "Please select at least one actor"
                _isLoading.value = false
                return
            }

            val result = encounterRepository.createEncounter(name, selected)

            result.fold(
                onSuccess = { encounterId ->
                    Timber.d("Encounter created successfully: $encounterId")
                    _saveComplete.value = Pair(encounterId, startImmediately)
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Failed to create encounter"
                    Timber.e(error, "Failed to create encounter")
                }
            )
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "An unexpected error occurred"
            Timber.e(e, "Unexpected error creating encounter")
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    // ========== Factory ==========

    /**
     * Factory for creating EncounterCreateViewModel with dependencies
     */
    class Factory(
        private val encounterRepository: EncounterRepository,
        private val actorRepository: ActorRepository
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EncounterCreateViewModel::class.java)) {
                return EncounterCreateViewModel(encounterRepository, actorRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

/**
 * Data class representing an actor with selection state
 */
data class ActorSelectionState(
    val actor: Actor,
    val isSelected: Boolean,
    val quantity: Int
)
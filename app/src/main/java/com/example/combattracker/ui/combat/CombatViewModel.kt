// File: CombatViewModel.kt
// Location: app/src/main/java/com/example/combattracker/ui/combat/CombatViewModel.kt

package com.example.combattracker.ui.combat

import androidx.lifecycle.*
import com.example.combattracker.data.database.dao.ConditionDao
import com.example.combattracker.data.database.dao.EncounterActorWithActor
import com.example.combattracker.data.database.entities.*
import com.example.combattracker.data.model.ActorCategory
import com.example.combattracker.data.model.ConditionType
import com.example.combattracker.data.repository.EncounterCombatData
import com.example.combattracker.data.repository.EncounterRepository
import com.example.combattracker.utils.InitiativeCalculator
import com.example.combattracker.utils.InitiativeRollType
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * CombatViewModel - Manages combat state and logic
 *
 * Purpose:
 * - Loads and manages encounter state
 * - Handles turn and round progression
 * - Manages initiative rolling
 * - Tracks actor conditions
 * - Handles saving encounter state
 *
 * Requirements Reference:
 * From section 3.3.3: Initiative System
 * - Rolling options for all/NPCs/individual
 * - Tie-breaking system
 * From section 3.3.5: Turn Management Controls
 * - Next/Previous turn and round
 * From section 3.6.1: Save current encounter state
 */
class CombatViewModel(
    private val encounterRepository: EncounterRepository,
    private val conditionDao: ConditionDao,
    private val encounterId: Long
) : ViewModel() {

    // ========== State Properties ==========

    private var currentEncounter: Encounter? = null
    private var encounterActors = mutableListOf<EncounterActorWithActor>()
    private var actorConditions = mutableMapOf<Long, List<ActorConditionWithDetails>>()

    // ========== Observable Properties ==========

    private val _combatState = MutableLiveData<CombatState>()
    val combatState: LiveData<CombatState> = _combatState

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _encounterLoaded = MutableLiveData(false)
    val encounterLoaded: LiveData<Boolean> = _encounterLoaded

    private val _selectedActorId = MutableLiveData<Long?>()
    val selectedActorId: LiveData<Long?> = _selectedActorId

    // ========== Initialization ==========

    init {
        if (encounterId == -1L) {
            _errorMessage.value = "Invalid encounter ID"
            _encounterLoaded.value = false
        }
    }

    // ========== Public Methods - Loading ==========

    /**
     * Load the encounter and initialize combat
     */
    suspend fun loadEncounter() {
        _isLoading.value = true

        encounterRepository.loadEncounter(encounterId).fold(
            onSuccess = { data ->
                initializeCombat(data)
                _encounterLoaded.value = true
            },
            onFailure = { error ->
                Timber.e(error, "Failed to load encounter")
                _errorMessage.value = "Failed to load encounter: ${error.message}"
                _encounterLoaded.value = false
            }
        )

        _isLoading.value = false
    }

    /**
     * Initialize combat from loaded data
     */
    private fun initializeCombat(data: EncounterCombatData) {
        currentEncounter = data.encounter
        encounterActors.clear()
        encounterActors.addAll(data.actors)
        actorConditions.clear()
        actorConditions.putAll(data.conditions)

        // If no active actor set, find first with initiative
        if (data.encounter.currentActorId == null) {
            val firstActor = encounterActors
                .filter { actorWithActor -> actorWithActor.encounterActor.hasInitiative() }
                .sortedWith(compareByDescending<EncounterActorWithActor> { actorWithActor ->
                    actorWithActor.encounterActor.initiative
                }.thenBy { actorWithActor ->
                    actorWithActor.encounterActor.tieBreakOrder
                })
                .firstOrNull()

            currentEncounter = currentEncounter?.copy(currentActorId = firstActor?.encounterActor?.id)
        }

        updateCombatState()
    }

    // ========== Public Methods - Initiative ==========

    /**
     * Roll initiative for all actors
     */
    fun rollInitiativeForAll() {
        viewModelScope.launch {
            try {
                val actorCategories = encounterActors.associate { actorWithActor ->
                    actorWithActor.encounterActor.baseActorId to actorWithActor.actor.getActorCategory()
                }

                val results = InitiativeCalculator.rollInitiativeForActors(
                    actors = encounterActors.map { actorWithActor -> actorWithActor.encounterActor },
                    rollType = InitiativeRollType.ALL,
                    actorCategories = actorCategories
                )

                // Update actors with rolled initiatives
                results.forEach { (actorId, initiative) ->
                    val actor = encounterActors.find { actorWithActor ->
                        actorWithActor.encounterActor.id == actorId
                    }?.encounterActor ?: return@forEach

                    val updatedActor = actor.copy(initiative = initiative)
                    updateActorInList(updatedActor)
                }

                // Set first actor as active if none set
                if (currentEncounter?.currentActorId == null) {
                    val sorted = getSortedActors()
                    currentEncounter = currentEncounter?.copy(
                        currentActorId = sorted.firstOrNull()?.encounterActor?.id
                    )
                }

                updateCombatState()

            } catch (e: Exception) {
                Timber.e(e, "Failed to roll initiative")
                _errorMessage.value = "Failed to roll initiative"
            }
        }
    }

    /**
     * Roll initiative for NPCs only
     */
    fun rollInitiativeForNPCs() {
        viewModelScope.launch {
            try {
                val actorCategories = encounterActors.associate { actorWithActor ->
                    actorWithActor.encounterActor.baseActorId to actorWithActor.actor.getActorCategory()
                }

                val results = InitiativeCalculator.rollInitiativeForActors(
                    actors = encounterActors.map { actorWithActor -> actorWithActor.encounterActor },
                    rollType = InitiativeRollType.NPC_ONLY,
                    actorCategories = actorCategories
                )

                // Update actors with rolled initiatives
                results.forEach { (actorId, initiative) ->
                    val actor = encounterActors.find { actorWithActor ->
                        actorWithActor.encounterActor.id == actorId
                    }?.encounterActor ?: return@forEach

                    val updatedActor = actor.copy(initiative = initiative)
                    updateActorInList(updatedActor)
                }

                updateCombatState()

            } catch (e: Exception) {
                Timber.e(e, "Failed to roll NPC initiative")
                _errorMessage.value = "Failed to roll initiative for NPCs"
            }
        }
    }

    /**
     * Set manual initiative for a specific actor
     */
    fun setActorInitiative(actorId: Long, initiative: Double) {
        viewModelScope.launch {
            try {
                val actor = encounterActors.find { actorWithActor ->
                    actorWithActor.encounterActor.id == actorId
                }?.encounterActor ?: return@launch

                val updatedActor = actor.copy(initiative = initiative)
                updateActorInList(updatedActor)
                updateCombatState()

            } catch (e: Exception) {
                Timber.e(e, "Failed to set initiative")
                _errorMessage.value = "Failed to set initiative"
            }
        }
    }

    // ========== Public Methods - Turn Management ==========

    /**
     * Advance to the next turn
     */
    fun nextTurn() {
        val state = _combatState.value ?: return
        if (!state.canProgress) return

        val sortedActors = getSortedActors()
        val currentIndex = sortedActors.indexOfFirst { actorWithActor ->
            actorWithActor.encounterActor.id == currentEncounter?.currentActorId
        }

        if (currentIndex == -1) return

        // Mark current actor as having taken turn
        sortedActors[currentIndex].encounterActor.let { actor ->
            val updated = actor.copy(hasTakenTurn = true)
            updateActorInList(updated)
        }

        // Find next actor who hasn't taken turn
        val nextActor = sortedActors
            .drop(currentIndex + 1)
            .firstOrNull { actorWithActor -> !actorWithActor.encounterActor.hasTakenTurn }

        if (nextActor != null) {
            // Move to next actor in same round
            currentEncounter = currentEncounter?.copy(
                currentActorId = nextActor.encounterActor.id
            )
        } else {
            // No more actors - advance round
            nextRound()
            return
        }

        updateCombatState()
    }

    /**
     * Go back to previous turn
     */
    fun previousTurn() {
        val state = _combatState.value ?: return
        if (!state.canProgress || state.isFirstTurn) return

        val sortedActors = getSortedActors()
        val currentIndex = sortedActors.indexOfFirst { actorWithActor ->
            actorWithActor.encounterActor.id == currentEncounter?.currentActorId
        }

        if (currentIndex <= 0) return

        // Unmark current actor's turn taken status
        sortedActors[currentIndex].encounterActor.let { actor ->
            val updated = actor.copy(hasTakenTurn = false)
            updateActorInList(updated)
        }

        // Move to previous actor
        val previousActor = sortedActors[currentIndex - 1]
        currentEncounter = currentEncounter?.copy(
            currentActorId = previousActor.encounterActor.id
        )

        updateCombatState()
    }

    /**
     * Advance to next round
     */
    fun nextRound() {
        // Reset all actors' turn status
        encounterActors.forEach { actorWithDetails ->
            val updated = actorWithDetails.encounterActor.copy(hasTakenTurn = false)
            updateActorInList(updated)
        }

        // Increment round
        val newRound = (currentEncounter?.currentRound ?: 1) + 1

        // Set first actor as active
        val firstActor = getSortedActors().firstOrNull()

        currentEncounter = currentEncounter?.copy(
            currentRound = newRound,
            currentActorId = firstActor?.encounterActor?.id
        )

        // Update condition durations
        updateConditionDurations()

        updateCombatState()
    }

    /**
     * Go back to previous round
     */
    fun previousRound() {
        val currentRound = currentEncounter?.currentRound ?: return
        if (currentRound <= 1) return

        // Reset all actors' turn status
        encounterActors.forEach { actorWithDetails ->
            val updated = actorWithDetails.encounterActor.copy(hasTakenTurn = false)
            updateActorInList(updated)
        }

        // Decrement round
        val newRound = currentRound - 1

        // Set first actor as active
        val firstActor = getSortedActors().firstOrNull()

        currentEncounter = currentEncounter?.copy(
            currentRound = newRound,
            currentActorId = firstActor?.encounterActor?.id
        )

        updateCombatState()
    }

    // ========== Public Methods - Actor Management ==========

    /**
     * Select an actor for context menu
     */
    fun selectActor(actorId: Long) {
        _selectedActorId.value = actorId
        updateCombatState()
    }

    /**
     * Clear selected actor
     */
    fun clearSelectedActor() {
        _selectedActorId.value = null
        updateCombatState()
    }

    /**
     * Remove actor from encounter
     */
    fun removeActor(actorId: Long) {
        viewModelScope.launch {
            try {
                encounterRepository.removeActorFromEncounter(actorId).fold(
                    onSuccess = {
                        // Remove from local list
                        encounterActors.removeAll { actorWithActor ->
                            actorWithActor.encounterActor.id == actorId
                        }
                        actorConditions.remove(actorId)

                        // If removed actor was active, move to next
                        if (currentEncounter?.currentActorId == actorId) {
                            val nextActor = getSortedActors().firstOrNull()
                            currentEncounter = currentEncounter?.copy(
                                currentActorId = nextActor?.encounterActor?.id
                            )
                        }

                        updateCombatState()
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to remove actor: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to remove actor"
            }
        }
    }

    // ========== Public Methods - Conditions ==========

    /**
     * Toggle a condition on an actor
     */
    fun toggleCondition(
        actorId: Long,
        conditionType: ConditionType,
        isPermanent: Boolean,
        duration: Int?
    ) {
        viewModelScope.launch {
            try {
                val currentConditions = actorConditions[actorId] ?: emptyList()
                val hasCondition = currentConditions.any { conditionWithDetails ->
                    conditionWithDetails.condition.id == conditionType.id
                }

                if (hasCondition) {
                    // Remove condition
                    val conditionId = currentConditions.first { conditionWithDetails ->
                        conditionWithDetails.condition.id == conditionType.id
                    }.actorCondition.id

                    // Remove from local state
                    actorConditions[actorId] = currentConditions.filter { conditionWithDetails ->
                        conditionWithDetails.condition.id != conditionType.id
                    }
                } else {
                    // Add condition
                    val currentRound = currentEncounter?.currentRound ?: 1
                    encounterRepository.applyCondition(
                        actorId,
                        conditionType,
                        isPermanent,
                        duration,
                        currentRound
                    ).fold(
                        onSuccess = {
                            // Reload conditions for this actor
                            reloadActorConditions(actorId)
                        },
                        onFailure = { error ->
                            _errorMessage.value = "Failed to apply condition: ${error.message}"
                        }
                    )
                }

                updateCombatState()

            } catch (e: Exception) {
                _errorMessage.value = "Failed to toggle condition"
            }
        }
    }

    // ========== Public Methods - Saving ==========

    /**
     * Save encounter with new name
     */
    suspend fun saveEncounter(name: String) {
        try {
            val encounter = currentEncounter ?: return
            val actors = encounterActors.map { actorWithActor -> actorWithActor.encounterActor }

            encounterRepository.saveAsNewEncounter(
                originalEncounterId = encounterId,
                customName = name,
                currentRound = encounter.currentRound,
                activeActorId = encounter.currentActorId,
                actors = actors
            ).fold(
                onSuccess = { newId ->
                    Timber.d("Encounter saved with ID: $newId")
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to save: ${error.message}"
                }
            )
        } catch (e: Exception) {
            _errorMessage.value = "Failed to save encounter"
        }
    }

    // ========== Private Methods ==========

    /**
     * Update an actor in the local list
     */
    private fun updateActorInList(updatedActor: EncounterActor) {
        val index = encounterActors.indexOfFirst { actorWithActor ->
            actorWithActor.encounterActor.id == updatedActor.id
        }
        if (index >= 0) {
            val existing = encounterActors[index]
            encounterActors[index] = EncounterActorWithActor(
                encounterActor = updatedActor,
                actor = existing.actor
            )
        }
    }

    /**
     * Get actors sorted by initiative
     */
    private fun getSortedActors(): List<EncounterActorWithActor> {
        return encounterActors.sortedWith(
            compareByDescending<EncounterActorWithActor> { actorWithActor ->
                actorWithActor.encounterActor.initiative ?: -999.0
            }
                .thenBy { actorWithActor -> actorWithActor.encounterActor.tieBreakOrder }
                .thenBy { actorWithActor -> actorWithActor.encounterActor.addedOrder }
        )
    }

    /**
     * Update condition durations at end of actor's turn
     */
    private fun updateConditionDurations() {
        // This would decrement non-permanent condition durations
        // For now, just log
        Timber.d("Updating condition durations for new round")
    }

    /**
     * Reload conditions for specific actor
     */
    private suspend fun reloadActorConditions(actorId: Long) {
        // In a real implementation, this would reload from database
        Timber.d("Reloading conditions for actor $actorId")
    }

    /**
     * Update the combat state
     */
    private fun updateCombatState() {
        val encounter = currentEncounter ?: return
        val sortedActors = getSortedActors()

        val currentActorIndex = sortedActors.indexOfFirst { actorWithActor ->
            actorWithActor.encounterActor.id == encounter.currentActorId
        }

        val hasAllInitiatives = sortedActors.all { actorWithActor ->
            actorWithActor.encounterActor.hasInitiative()
        }

        val actorStates = sortedActors.map { actorWithActor ->
            val actor = actorWithActor.encounterActor
            val baseActor = actorWithActor.actor
            val conditions = actorConditions[actor.id] ?: emptyList()

            EncounterActorState(
                id = actor.id,
                displayName = actor.displayName,
                portraitPath = baseActor.portraitPath,
                category = baseActor.getActorCategory(),
                initiative = actor.initiative,
                conditions = conditions.map { conditionWithDetails -> conditionWithDetails.condition },
                isActive = actor.id == encounter.currentActorId,
                hasTakenTurn = actor.hasTakenTurn,
                missingInitiative = !actor.hasInitiative(),
                isHighlighted = actor.id == _selectedActorId.value
            )
        }

        _combatState.value = CombatState(
            encounterName = encounter.name,
            roundNumber = encounter.currentRound,
            actors = actorStates,
            currentActorId = encounter.currentActorId,
            currentActorIndex = if (currentActorIndex >= 0) currentActorIndex else null,
            canProgress = hasAllInitiatives,
            isFirstTurn = currentActorIndex == 0 && !(sortedActors.getOrNull(0)?.encounterActor?.hasTakenTurn ?: false),
            isLastTurn = currentActorIndex == sortedActors.lastIndex
        )
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    // ========== Factory ==========

    class Factory(
        private val encounterRepository: EncounterRepository,
        private val conditionDao: ConditionDao,
        private val encounterId: Long
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CombatViewModel::class.java)) {
                return CombatViewModel(encounterRepository, conditionDao, encounterId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

// ========== Extension Functions ==========

private fun EncounterActor.hasInitiative(): Boolean = this.initiative != null

// ========== Data Classes ==========

/**
 * Combat UI state
 */
data class CombatState(
    val encounterName: String,
    val roundNumber: Int,
    val actors: List<EncounterActorState>,
    val currentActorId: Long?,
    val currentActorIndex: Int?,
    val canProgress: Boolean,
    val isFirstTurn: Boolean,
    val isLastTurn: Boolean
)

/**
 * Actor state for UI
 */
data class EncounterActorState(
    val id: Long,
    val displayName: String,
    val portraitPath: String?,
    val category: ActorCategory,
    val initiative: Double?,
    val conditions: List<Condition>,
    val isActive: Boolean,
    val hasTakenTurn: Boolean,
    val missingInitiative: Boolean,
    val isHighlighted: Boolean
)
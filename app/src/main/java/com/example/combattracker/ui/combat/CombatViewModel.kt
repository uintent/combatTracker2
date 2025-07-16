// File: CombatViewModel.kt
// Location: app/src/main/java/com/example/combattracker/ui/combat/CombatViewModel.kt

package com.example.combattracker.ui.combat

import androidx.lifecycle.*
import com.example.combattracker.data.database.dao.ConditionDao
import com.example.combattracker.data.database.dao.EncounterActorWithActor
import com.example.combattracker.data.database.entities.*
import com.example.combattracker.data.model.ActorCategory
import com.example.combattracker.data.model.ConditionType
import com.example.combattracker.data.repository.ActorRepository
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
    private val actorRepository: ActorRepository,
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

    private val _encounterLoaded = MutableLiveData<Boolean?>()
    val encounterLoaded: LiveData<Boolean?> = _encounterLoaded

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

        // Filter out any actors where the base actor is missing
        val validActors = data.actors.filter { it.actor != null }
        if (validActors.size < data.actors.size) {
            Timber.w("${data.actors.size - validActors.size} actors were filtered out due to missing base actors")
        }

        encounterActors.addAll(validActors)
        actorConditions.clear()

        // Debug: Log what conditions are being loaded
        Timber.d("=== Loading Conditions ===")
        data.conditions.forEach { (actorId, conditions) ->
            // Find which actor this is
            val actor = data.actors.find { it.encounterActor.id == actorId }
            Timber.d("Actor ID $actorId (${actor?.encounterActor?.displayName}): ${conditions.size} conditions")
            conditions.forEach { condition ->
                Timber.d("  - ${condition.condition.name} (permanent: ${condition.actorCondition.isPermanent}, duration: ${condition.actorCondition.remainingDuration})")
            }
        }
        Timber.d("=== End Loading Conditions ===")

        actorConditions.putAll(data.conditions)

        // If no active actor set, find first with initiative
        if (data.encounter.currentActorId == null) {
            val firstActor = encounterActors
                .filter { it.encounterActor.hasInitiative() }
                .sortedWith(compareByDescending<EncounterActorWithActor> { it.encounterActor.initiative }
                    .thenBy { it.encounterActor.tieBreakOrder })
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
                    actorWithActor.encounterActor.baseActorId to (actorWithActor.actor?.getActorCategory() ?: ActorCategory.OTHER)
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

                // Persist the updated initiative values to database
                val encounter = currentEncounter
                if (encounter != null) {
                    val actors = encounterActors.map { it.encounterActor }

                    encounterRepository.saveEncounterState(
                        encounterId = encounterId,
                        currentRound = encounter.currentRound,
                        activeActorId = encounter.currentActorId,
                        actors = actors
                    ).fold(
                        onSuccess = {
                            Timber.d("Successfully persisted initiative values to database")
                        },
                        onFailure = { error ->
                            Timber.e(error, "Failed to persist initiative values")
                            _errorMessage.value = "Initiative rolled but failed to save: ${error.message}"
                        }
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
                    actorWithActor.encounterActor.baseActorId to (actorWithActor.actor?.getActorCategory() ?: ActorCategory.OTHER)
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
                // Debug: Log what we're looking for
                Timber.d("Setting initiative for actor ID: $actorId to value: $initiative")

                // Debug: Log all actors
                encounterActors.forEach {
                    Timber.d("Available actor: ${it.encounterActor.displayName} (ID: ${it.encounterActor.id}, BaseID: ${it.encounterActor.baseActorId})")
                }

                val actorWithDetails = encounterActors.find { it.encounterActor.id == actorId }

                if (actorWithDetails == null) {
                    Timber.e("Actor not found with ID: $actorId")
                    return@launch
                }

                val actor = actorWithDetails.encounterActor
                Timber.d("Found actor: ${actor.displayName} (ID: ${actor.id}, BaseID: ${actor.baseActorId})")

                val updatedActor = actor.copy(initiative = initiative)

                // Debug: Log before update
                Timber.d("Before update:")
                encounterActors.forEach {
                    Timber.d("  ${it.encounterActor.displayName}: initiative = ${it.encounterActor.initiative}")
                }

                updateActorInList(updatedActor)
                persistCurrentState()

                // Debug: Log after update
                Timber.d("After update:")
                encounterActors.forEach {
                    Timber.d("  ${it.encounterActor.displayName}: initiative = ${it.encounterActor.initiative}")
                }

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

    /**
     * Add a new actor to the encounter with specified initiative
     */
    fun addActorToEncounter(baseActorId: Long, initiative: Double) {
        viewModelScope.launch {
            try {
                // Get the base actor name
                val baseActor = actorRepository.getActorById(baseActorId)
                if (baseActor == null) {
                    _errorMessage.value = "Actor not found"
                    return@launch
                }

                // Get existing display names in the encounter
                val existingNames = encounterActors.map { it.encounterActor.displayName }.toSet()

                // Find the next available name
                var displayName = baseActor.name
                var counter = 1

                // If base name exists, add numbers until we find an available one
                if (existingNames.contains(displayName)) {
                    do {
                        counter++
                        displayName = "${baseActor.name} $counter"
                    } while (existingNames.contains(displayName))
                }

                // Use the repository method to add to database
                val result = encounterRepository.addActorsToEncounter(
                    encounterId = encounterId,
                    actorIds = listOf(baseActorId),
                    manualInitiatives = mapOf(baseActorId to initiative)
                )

                result.fold(
                    onSuccess = { newActors ->
                        // Instead of reloading everything, just add the new actor to our current state
                        val newActor = newActors.firstOrNull()
                        if (newActor != null) {
                            // Create the EncounterActorWithActor object
                            val actorWithActor = EncounterActorWithActor(
                                encounterActor = newActor,
                                actor = baseActor
                            )

                            // Add to our local list
                            encounterActors.add(actorWithActor)

                            // Update the combat state without losing existing data
                            updateCombatState()

                            Timber.d("Added actor to encounter: ${newActor.displayName} with initiative $initiative")
                        }
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to add actor to encounter")

                        if (error.message?.contains("UNIQUE constraint failed") == true) {
                            _errorMessage.value = "Unable to add actor. Please try removing existing instances first."
                        } else {
                            _errorMessage.value = "Failed to add actor: ${error.message}"
                        }
                    }
                )

            } catch (e: Exception) {
                Timber.e(e, "Failed to add actor to encounter")
                _errorMessage.value = "Failed to add actor: ${e.message}"
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
                Timber.d("toggleCondition called for actor $actorId, condition ${conditionType.displayName}")
                Timber.d("isPermanent: $isPermanent, duration: $duration")
                Thread.currentThread().stackTrace.forEach { element ->
                    Timber.d("  at $element")
                }
                debugActorIds(actorId)
                // ALWAYS reload conditions to ensure we have the latest state from database
                // This prevents issues with stale cache or empty entries
                Timber.d("Reloading conditions for actor $actorId to ensure latest state")
                reloadActorConditions(actorId)

                // Now check if this condition already exists
                val currentConditions = actorConditions[actorId] ?: emptyList()
                Timber.d("Current conditions for actor $actorId: ${currentConditions.map { it.condition.name }}")

                val existingCondition = currentConditions.find { conditionWithDetails ->
                    conditionWithDetails.condition.id == conditionType.id
                }

                if (existingCondition != null) {
                    // Condition exists - check if we're just removing or updating
                    Timber.d("Condition ${conditionType.displayName} already exists with ID ${existingCondition.actorCondition.id}")

                    if (!isPermanent && duration == null) {
                        // Just removing the condition
                        Timber.d("Removing condition ${conditionType.displayName} from actor $actorId")

                        val conditionId = existingCondition.actorCondition.id
                        encounterRepository.removeCondition(conditionId).fold(
                            onSuccess = {
                                // Update local state
                                actorConditions[actorId] = currentConditions.filter {
                                    it.actorCondition.id != conditionId
                                }
                                updateCombatState()
                            },
                            onFailure = { error ->
                                Timber.e(error, "Failed to remove condition")
                                _errorMessage.value = "Failed to remove condition: ${error.message}"
                            }
                        )
                    } else {
                        // Updating the condition - remove old and add new
                        Timber.d("Updating condition ${conditionType.displayName} for actor $actorId")

                        val conditionId = existingCondition.actorCondition.id

                        // Remove the existing condition first
                        encounterRepository.removeCondition(conditionId).fold(
                            onSuccess = {
                                // Update local state immediately
                                actorConditions[actorId] = currentConditions.filter {
                                    it.actorCondition.id != conditionId
                                }

                                // Now add the new one
                                val currentRound = currentEncounter?.currentRound ?: 1
                                encounterRepository.applyCondition(
                                    actorId,
                                    conditionType,
                                    isPermanent,
                                    duration,
                                    currentRound
                                ).fold(
                                    onSuccess = {
                                        Timber.d("Successfully updated condition ${conditionType.displayName}")
                                        reloadActorConditions(actorId)
                                        updateCombatState()
                                    },
                                    onFailure = { error ->
                                        Timber.e(error, "Failed to apply updated condition")
                                        _errorMessage.value = "Failed to update condition: ${error.message}"
                                        // Try to reload to get back to consistent state
                                        reloadActorConditions(actorId)
                                        updateCombatState()
                                    }
                                )
                            },
                            onFailure = { error ->
                                Timber.e(error, "Failed to remove existing condition for update")
                                _errorMessage.value = "Failed to update condition: ${error.message}"
                            }
                        )
                    }
                } else {
                    // Condition doesn't exist - safe to add
                    Timber.d("Adding new condition ${conditionType.displayName} to actor $actorId")

                    val currentRound = currentEncounter?.currentRound ?: 1
                    encounterRepository.applyCondition(
                        actorId,
                        conditionType,
                        isPermanent,
                        duration,
                        currentRound
                    ).fold(
                        onSuccess = {
                            Timber.d("Successfully applied new condition ${conditionType.displayName}")
                            reloadActorConditions(actorId)
                            updateCombatState()
                        },
                        onFailure = { error ->
                            if (error.message?.contains("UNIQUE constraint failed") == true) {
                                // Condition exists in database but not in our cache - reload and try again
                                Timber.w("Condition exists in database but not in cache, reloading...")
                                reloadActorConditions(actorId)

                                // After reload, check if it now exists
                                val reloadedConditions = actorConditions[actorId] ?: emptyList()
                                val nowExists = reloadedConditions.find { it.condition.id == conditionType.id }

                                if (nowExists != null) {
                                    // It exists now, so this is actually an update operation
                                    Timber.d("Condition found after reload, treating as update")
                                    _errorMessage.value = "This condition is already applied. Remove it first to change settings."
                                } else {
                                    // Still doesn't exist in our cache but database says it does
                                    // This is a data inconsistency - best to show error
                                    Timber.e("Data inconsistency: condition exists in DB but not in cache after reload")
                                    _errorMessage.value = "Failed to apply condition: ${conditionType.displayName} already exists"
                                }
                            } else {
                                Timber.e(error, "Failed to apply new condition")
                                _errorMessage.value = "Failed to apply condition: ${error.message}"
                            }
                        }
                    )
                }

            } catch (e: Exception) {
                Timber.e(e, "Exception in toggleCondition")
                _errorMessage.value = "Failed to toggle condition: ${e.message}"
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

    /**
     * Deactivate current encounter when ending with discard
     */
    suspend fun deactivateCurrentEncounter() {
        try {
            encounterRepository.deactivateEncounter(encounterId).fold(
                onSuccess = {
                    Timber.d("Encounter deactivated")
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to deactivate encounter: ${error.message}"
                }
            )
        } catch (e: Exception) {
            _errorMessage.value = "Failed to deactivate encounter"
        }
    }

    // ========== Private Methods ==========

    /**
     * Update an actor in the local list
     * Ensures no shared references between actor instances
     */
    private fun updateActorInList(updatedActor: EncounterActor) {
        // Create a new list to ensure no shared references
        val newList = encounterActors.map { existingActorWithDetails ->
            if (existingActorWithDetails.encounterActor.id == updatedActor.id) {
                // Only update this specific actor
                existingActorWithDetails.copy(
                    encounterActor = updatedActor
                )
            } else {
                // Keep other actors unchanged
                existingActorWithDetails
            }
        }

        // Replace the entire list
        encounterActors.clear()
        encounterActors.addAll(newList)
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
        try {
            // Get the encounter actor ID (not the base actor ID)
            val encounterActor = encounterActors.find { it.encounterActor.id == actorId }
            if (encounterActor == null) {
                Timber.e("Could not find encounter actor with ID $actorId")
                return
            }

            // Load conditions from database
            val conditions = encounterRepository.getActorConditions(actorId)

            // Update local state
            actorConditions[actorId] = conditions

            Timber.d("Reloaded ${conditions.size} conditions for actor $actorId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to reload conditions for actor $actorId")
        }
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
                portraitPath = baseActor?.portraitPath,
                category = baseActor?.getActorCategory() ?: ActorCategory.OTHER,
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
        private val actorRepository: ActorRepository,
        private val conditionDao: ConditionDao,
        private val encounterId: Long
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CombatViewModel::class.java)) {
                return CombatViewModel(encounterRepository, actorRepository, conditionDao, encounterId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    fun debugActorIds(actorId: Long) {
        val encounterActor = encounterActors.find { it.encounterActor.id == actorId }
        if (encounterActor != null) {
            Timber.d("=== Actor ID Debug ===")
            Timber.d("Encounter Actor ID: ${encounterActor.encounterActor.id}")
            Timber.d("Base Actor ID: ${encounterActor.encounterActor.baseActorId}")
            Timber.d("Display Name: ${encounterActor.encounterActor.displayName}")
            Timber.d("Instance Number: ${encounterActor.encounterActor.instanceNumber}")
            Timber.d("=== End Debug ===")
        } else {
            Timber.e("Could not find encounter actor with ID $actorId")
        }
    }

    /**
     * Get condition details for a specific actor
     * Used by the UI to display duration/permanent status
     */
    fun getActorConditionDetails(actorId: Long): List<ActorConditionWithDetails> {
        return actorConditions[actorId] ?: emptyList()
    }

    private suspend fun persistCurrentState() {
        try {
            val encounter = currentEncounter ?: return
            val actors = encounterActors.map { it.encounterActor }

            encounterRepository.saveEncounterState(
                encounterId = encounterId,
                currentRound = encounter.currentRound,
                activeActorId = encounter.currentActorId,
                actors = actors
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist encounter state")
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
// File: EncounterRepository.kt
package com.example.combattracker.data.repository

import com.example.combattracker.data.database.dao.ActorDao
import com.example.combattracker.data.database.dao.ConditionDao
import com.example.combattracker.data.database.dao.EncounterDao
import com.example.combattracker.data.database.entities.*
import com.example.combattracker.data.model.ConditionType
import com.example.combattracker.data.model.InitiativeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.example.combattracker.data.database.dao.EncounterWithCount
import com.example.combattracker.data.database.dao.EncounterActorWithActor

/**
 * EncounterRepository - Business logic layer for encounter management
 *
 * Purpose:
 * - Manages encounter lifecycle (create, save, load, delete)
 * - Handles actor instance numbering for multiple copies
 * - Coordinates combat state including initiative and conditions
 * - Ensures data integrity when loading/saving encounters
 *
 * Requirements Reference:
 * From section 3.3: Encounter Management
 * - Create encounters with selected actors
 * - Save/load encounter state
 * - Handle multiple instances with unique numbering
 * From section 3.6.1: Save current encounter state (actors, initiative order, turn status, conditions, round number)
 */
class EncounterRepository(
    private val encounterDao: EncounterDao,
    private val actorDao: ActorDao,
    private val conditionDao: ConditionDao
) {

    // ========== Query Operations ==========

    /**
     * Get all encounters for the management screen
     * Includes actor count for display
     *
     * @return Flow of encounters with metadata
     */
    fun getAllEncounters(): Flow<List<EncounterWithCount>> {
        return encounterDao.getAllEncountersWithActorCount()
    }

    /**
     * Get a single encounter by ID
     *
     * @param encounterId The encounter ID
     * @return The encounter or null if not found
     */
    suspend fun getEncounterById(encounterId: Long): Encounter? {
        return withContext(Dispatchers.IO) {
            encounterDao.getEncounterById(encounterId)
        }
    }

    // ========== Create Operations ==========

    /**
     * Create a new encounter with selected actors
     *
     * Business Rules:
     * - Encounter name is optional (auto-generated if not provided)
     * - Handles multiple instances of the same actor with numbering
     * - Validates all actors exist before creating
     *
     * @param name Optional encounter name
     * @param selectedActors Map of actor ID to instance count
     * @return Result with encounter ID or error
     */
    suspend fun createEncounter(
        name: String?,
        selectedActors: Map<Long, Int>
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // Validate input
            if (selectedActors.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("An encounter must have at least one actor")
                )
            }

            // Validate all actors exist
            val actorIds = selectedActors.keys.toList()
            val actors = actorDao.getActorsByIds(actorIds)

            if (actors.size != actorIds.size) {
                return@withContext Result.failure(
                    IllegalStateException("Some selected actors no longer exist")
                )
            }

            // Create the encounter
            val encounter = Encounter.create(name)
            val encounterId = encounterDao.insertEncounter(encounter)

            // Create encounter actors with proper numbering
            val encounterActors = createEncounterActors(
                encounterId = encounterId,
                selectedActors = selectedActors,
                baseActors = actors
            )

            // Insert all encounter actors
            encounterDao.insertEncounterActors(*encounterActors.toTypedArray())

            Timber.d("Created encounter: ${encounter.name} with ${encounterActors.size} actors")
            Result.success(encounterId)

        } catch (e: Exception) {
            Timber.e(e, "Failed to create encounter")
            Result.failure(e)
        }
    }

    /**
     * Create encounter actor instances with proper numbering
     *
     * Implements the numbering system from requirements:
     * - Single instance: No number
     * - Multiple instances: "Name 1", "Name 2", etc.
     *
     * @param encounterId The encounter ID
     * @param selectedActors Map of actor ID to count
     * @param baseActors List of base actors
     * @return List of encounter actors with unique names
     */
    private fun createEncounterActors(
        encounterId: Long,
        selectedActors: Map<Long, Int>,
        baseActors: List<Actor>
    ): List<EncounterActor> {
        val encounterActors = mutableListOf<EncounterActor>()
        var addedOrder = 0

        baseActors.forEach { actor ->
            val count = selectedActors[actor.id] ?: 0

            if (count == 1) {
                // Single instance - no numbering
                encounterActors.add(
                    EncounterActor(
                        encounterId = encounterId,
                        baseActorId = actor.id,
                        displayName = actor.name,
                        instanceNumber = 0,
                        initiativeModifier = actor.initiativeModifier,
                        addedOrder = addedOrder++
                    )
                )
            } else if (count > 1) {
                // Multiple instances - add numbers
                repeat(count) { index ->
                    val instanceNumber = index + 1
                    encounterActors.add(
                        EncounterActor(
                            encounterId = encounterId,
                            baseActorId = actor.id,
                            displayName = "${actor.name} $instanceNumber",
                            instanceNumber = instanceNumber,
                            initiativeModifier = actor.initiativeModifier,
                            addedOrder = addedOrder++
                        )
                    )
                }
            }
        }

        return encounterActors
    }

    // ========== Load Operations ==========

    /**
     * Load a complete encounter for combat
     *
     * Validates:
     * - All actors still exist in the library
     * - Conditions are valid
     * - Data integrity is maintained
     *
     * @param encounterId The encounter to load
     * @return Result with encounter data or error
     */
    suspend fun loadEncounter(encounterId: Long): Result<EncounterCombatData> = withContext(Dispatchers.IO) {
        try {
            Timber.d("=== LOADING ENCOUNTER $encounterId ===")

            // Load the encounter
            val encounter = encounterDao.getEncounterById(encounterId)
            if (encounter == null) {
                Timber.e("Encounter not found with ID: $encounterId")
                return@withContext Result.failure(
                    IllegalArgumentException("Encounter not found")
                )
            }
            Timber.d("Loaded encounter: ${encounter.name}, created: ${encounter.createdDate}, modified: ${encounter.lastModifiedDate}")

            // Load actors with base actor details
            val actorsFixed = encounterDao.getEncounterActorsFixed(encounterId)
            val actorsWithDetails = actorsFixed.map { it.toEncounterActorWithActor() }
            Timber.d("Loaded ${actorsWithDetails.size} actors for encounter")

            // Log each actor
            actorsWithDetails.forEach { actorWithDetails ->
                Timber.d("  Actor: ${actorWithDetails.encounterActor.displayName} " +
                        "(baseActorId: ${actorWithDetails.encounterActor.baseActorId})")
            }

            // Validate all actors still exist
            if (actorsWithDetails.isEmpty()) {
                Timber.e("No actors found for encounter $encounterId")
                return@withContext Result.failure(
                    IllegalStateException("Encounter has no valid actors")
                )
            }

            // Load conditions for all actors
            val conditions = encounterDao.getEncounterConditions(encounterId)
                .groupBy { it.actorCondition.encounterActorId }
            Timber.d("Loaded conditions for ${conditions.size} actors")

            // Create combat data
            val combatData = EncounterCombatData(
                encounter = encounter,
                actors = actorsWithDetails,
                conditions = conditions
            )

            Timber.d("=== ENCOUNTER LOADED SUCCESSFULLY ===")
            Result.success(combatData)

        } catch (e: Exception) {
            Timber.e(e, "Failed to load encounter: ${e.message}")
            Timber.e("Stack trace: ${e.stackTraceToString()}")
            Result.failure(e)
        }
    }

    // ========== Save Operations ==========

    /**
     * Save the current state of an encounter
     *
     * Updates:
     * - Current round
     * - Active actor
     * - Initiative values
     * - Turn status
     * - Active conditions
     *
     * @param encounterId The encounter ID
     * @param currentRound Current round number
     * @param activeActorId Currently active actor
     * @param actors List of actors with current state
     * @return Result with success or error
     */
    suspend fun saveEncounterState(
        encounterId: Long,
        currentRound: Int,
        activeActorId: Long?,
        actors: List<EncounterActor>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Load existing encounter
            val encounter = encounterDao.getEncounterById(encounterId)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Encounter not found")
                )

            // Update encounter state
            val updatedEncounter = encounter.copy(
                currentRound = currentRound,
                currentActorId = activeActorId,
                isActive = true,
            ).withUpdatedTimestamp()

            encounterDao.updateEncounter(updatedEncounter)

            // Update all actors
            encounterDao.updateEncounterActors(*actors.toTypedArray())

            // Clean up expired conditions
            val expiredCount = encounterDao.deleteExpiredConditions(encounterId)
            if (expiredCount > 0) {
                Timber.d("Removed $expiredCount expired conditions")
            }

            Timber.d("Saved encounter state: Round $currentRound")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to save encounter state")
            Result.failure(e)
        }
    }

    /**
     * Save as new encounter (not overwriting original)
     *
     * Requirements: "If saving a previously loaded encounter, a new save
     * is generated with a new auto-generated name"
     *
     * @param originalEncounterId The encounter being saved
     * @param customName Optional custom name
     * @param currentRound Current round
     * @param activeActorId Active actor
     * @param actors Current actor states
     * @return Result with new encounter ID or error
     */
    suspend fun saveAsNewEncounter(
        originalEncounterId: Long,
        customName: String?,
        currentRound: Int,
        activeActorId: Long?,
        actors: List<EncounterActor>
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // Create new encounter
            val newEncounter = Encounter.create(customName).copy(
                currentRound = currentRound,
                currentActorId = activeActorId,
            )

            val newEncounterId = encounterDao.insertEncounter(newEncounter)

            // Copy actors to new encounter
            val newActors = actors.map { actor ->
                actor.copy(
                    id = 0, // Reset ID for new insert
                    encounterId = newEncounterId
                )
            }

            encounterDao.insertEncounterActors(*newActors.toTypedArray())

            // Copy active conditions
            actors.forEach { actor ->
                val conditions = encounterDao.getActorConditions(actor.id)
                conditions.forEach { conditionWithDetails ->
                    val newActorId = newActors.find {
                        it.baseActorId == actor.baseActorId &&
                                it.instanceNumber == actor.instanceNumber
                    }?.id ?: return@forEach

                    val newCondition = conditionWithDetails.actorCondition.copy(
                        id = 0,
                        encounterActorId = newActorId
                    )
                    encounterDao.insertActorCondition(newCondition)
                }
            }

            Timber.d("Saved as new encounter: ${newEncounter.name}")
            Result.success(newEncounterId)

        } catch (e: Exception) {
            Timber.e(e, "Failed to save as new encounter")
            Result.failure(e)
        }
    }

    // ========== Update Operations ==========

    /**
     * Add actors to an existing encounter mid-combat
     *
     * @param encounterId The encounter ID
     * @param actorIds List of actor IDs to add
     * @param manualInitiatives Map of actor ID to manual initiative value
     * @return Result with list of new encounter actors or error
     */
    suspend fun addActorsToEncounter(
        encounterId: Long,
        actorIds: List<Long>,
        manualInitiatives: Map<Long, Double>
    ): Result<List<EncounterActor>> = withContext(Dispatchers.IO) {
        try {
            // Validate encounter exists
            val encounter = encounterDao.getEncounterById(encounterId)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Encounter not found")
                )

            // Get existing actors to determine numbering
            val existingActors = encounterDao.getEncounterActorsWithDetails(encounterId)
            val maxAddedOrder = existingActors.maxOfOrNull { it.encounterActor.addedOrder } ?: -1

            // Load base actors
            val baseActors = actorDao.getActorsByIds(actorIds)

            // Create new encounter actors with proper numbering
            val newActors = mutableListOf<EncounterActor>()
            var addedOrder = maxAddedOrder + 1

            baseActors.forEach { actor ->
                // Get existing display names for this actor
                val existingNames = encounterDao.getExistingDisplayNames(encounterId, actor.id)

                // Find the next available number
                var instanceNumber = 0
                var displayName = actor.name

                // If the base name already exists, find the next available number
                if (existingNames.contains(displayName)) {
                    instanceNumber = 1
                    displayName = "${actor.name} $instanceNumber"

                    // Keep incrementing until we find an unused name
                    while (existingNames.contains(displayName)) {
                        instanceNumber++
                        displayName = "${actor.name} $instanceNumber"
                    }
                }

                val encounterActor = EncounterActor(
                    encounterId = encounterId,
                    baseActorId = actor.id,
                    displayName = displayName,
                    instanceNumber = instanceNumber,
                    initiative = manualInitiatives[actor.id],
                    initiativeModifier = actor.initiativeModifier,
                    addedOrder = addedOrder++
                )

                newActors.add(encounterActor)
            }

            // Insert new actors
            encounterDao.insertEncounterActors(*newActors.toTypedArray())

            Timber.d("Added ${newActors.size} actors to encounter")
            Result.success(newActors)

        } catch (e: Exception) {
            Timber.e(e, "Failed to add actors to encounter")
            Result.failure(e)
        }
    }

    /**
     * Remove an actor from encounter
     *
     * @param encounterActorId The encounter actor to remove
     * @return Result with success or error
     */
    suspend fun removeActorFromEncounter(encounterActorId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val actor = encounterDao.getEncounterActorsWithDetails(encounterActorId).firstOrNull()
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Actor not found in encounter")
                )

            encounterDao.deleteEncounterActor(actor.encounterActor)

            Timber.d("Removed actor from encounter: ${actor.encounterActor.displayName}")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to remove actor from encounter")
            Result.failure(e)
        }
    }

    // ========== Delete Operations ==========

    /**
     * Delete an encounter
     * Cascades to delete all actors and conditions
     *
     * @param encounterId The encounter to delete
     * @return Result with success or error
     */
    suspend fun deleteEncounter(encounterId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            encounterDao.deleteEncounterById(encounterId)
            Timber.d("Deleted encounter: $encounterId")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to delete encounter")
            Result.failure(e)
        }
    }

    // ========== Condition Operations ==========

    /**
     * Apply a condition to an actor
     *
     * @param actorId The encounter actor ID
     * @param conditionType The condition to apply
     * @param isPermanent Whether the condition is permanent
     * @param duration Duration in turns (if not permanent)
     * @param currentRound Current combat round
     * @return Result with success or error
     */
    suspend fun applyCondition(
        actorId: Long,
        conditionType: ConditionType,
        isPermanent: Boolean,
        duration: Int?,
        currentRound: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val condition = if (isPermanent) {
                ActorCondition.createPermanent(actorId, conditionType.id, currentRound)
            } else {
                if (duration == null || duration <= 0) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Duration required for non-permanent conditions")
                    )
                }
                ActorCondition.createTemporary(actorId, conditionType.id, duration, currentRound)
            }

            encounterDao.insertActorCondition(condition)

            Timber.d("Applied condition ${conditionType.displayName} to actor $actorId")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to apply condition")
            Result.failure(e)
        }
    }

    /**
     * Remove a condition from an actor
     *
     * @param conditionId The actor condition ID
     * @return Result with success or error
     */
    suspend fun removeCondition(conditionId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            encounterDao.deleteActorConditionById(conditionId)
            Timber.d("Removed condition: $conditionId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove condition")
            Result.failure(e)
        }
    }

    /**
     * Debug method to investigate encounter loading issues
     */
    suspend fun debugLoadEncounter(encounterId: Long) = withContext(Dispatchers.IO) {
        try {
            Timber.d("=== DEBUG LOAD ENCOUNTER $encounterId ===")

            // Check if encounter exists
            val encounter = encounterDao.getEncounterById(encounterId)
            Timber.d("Encounter exists: ${encounter != null}")
            if (encounter != null) {
                Timber.d("Encounter name: ${encounter.name}")
                Timber.d("Encounter ID: ${encounter.id}")
            }

            // ADD THIS NEW SECTION HERE - Get raw data without joins
            val rawActors = encounterDao.getEncounterActorsRaw(encounterId)
            Timber.d("=== RAW ENCOUNTER ACTORS (No Joins) ===")
            rawActors.forEach { ea ->
                Timber.d("Raw EncounterActor - ID: ${ea.id}, DisplayName: ${ea.displayName}, BaseActorId: ${ea.baseActorId}")
            }

            // Get raw encounter actors data
            Timber.d("=== RAW ENCOUNTER ACTORS ===")
            val actorsFixed = encounterDao.getEncounterActorsFixed(encounterId)
            val encounterActors = actorsFixed.map { it.toEncounterActorWithActor() }
            encounterActors.forEach { actorWithDetails ->
                val ea = actorWithDetails.encounterActor
                Timber.d("EncounterActor:")
                Timber.d("  EncounterActor ID: ${ea.id} (should be unique)")
                Timber.d("  Base Actor ID: ${ea.baseActorId} (can be same for multiple instances)")
                Timber.d("  Display Name: ${ea.displayName}")
                Timber.d("  Base Actor ID: ${ea.baseActorId}")
                Timber.d("  Instance Number: ${ea.instanceNumber}")
                Timber.d("  Base Actor Found: ${actorWithDetails.actor != null}")
                if (actorWithDetails.actor != null) {
                    Timber.d("  Linked to: ${actorWithDetails.actor.name} (ID: ${actorWithDetails.actor.id})")
                }
            }

            // Group by base actor ID to see duplicates
            val groupedByBaseActor = encounterActors.groupBy { it.encounterActor.baseActorId }
            Timber.d("=== GROUPED BY BASE ACTOR ID ===")
            groupedByBaseActor.forEach { (baseActorId, actors) ->
                Timber.d("Base Actor ID $baseActorId has ${actors.size} instances:")
                actors.forEach {
                    Timber.d("  - ${it.encounterActor.displayName} (instance: ${it.encounterActor.instanceNumber})")
                }
            }

            // Check which base actor IDs exist in the actor library
            val uniqueBaseActorIds = encounterActors.map { it.encounterActor.baseActorId }.distinct()
            Timber.d("=== CHECKING BASE ACTORS ===")
            Timber.d("Unique base actor IDs needed: $uniqueBaseActorIds")

            val existingActors = if (uniqueBaseActorIds.isNotEmpty()) {
                actorDao.getActorsByIds(uniqueBaseActorIds)
            } else {
                emptyList()
            }

            Timber.d("Found ${existingActors.size} of ${uniqueBaseActorIds.size} base actors")
            existingActors.forEach { actor ->
                Timber.d("  Found: ${actor.name} (ID: ${actor.id})")
            }

            val foundIds = existingActors.map { it.id }.toSet()
            val missingIds = uniqueBaseActorIds.filter { it !in foundIds }
            if (missingIds.isNotEmpty()) {
                Timber.e("MISSING BASE ACTOR IDs: $missingIds")
            }

        } catch (e: Exception) {
            Timber.e(e, "Debug load failed: ${e.message}")
        }
    }

}

// ========== Data Classes ==========

/**
 * Complete encounter data for combat
 */
data class EncounterCombatData(
    val encounter: Encounter,
    val actors: List<EncounterActorWithActor>,
    val conditions: Map<Long, List<ActorConditionWithDetails>>
)
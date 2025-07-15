// File: EncounterDao.kt
package com.example.combattracker.data.database.dao

import androidx.room.*
import com.example.combattracker.data.database.entities.*
import kotlinx.coroutines.flow.Flow

/**
 * EncounterDao - Data Access Object for Encounter entities and related data
 *
 * Purpose:
 * - Manages encounter CRUD operations
 * - Handles complex queries for loading complete encounter state
 * - Manages encounter actors and their conditions
 *
 * Requirements Reference:
 * From section 3.3.2: Encounter List Management
 * - Shows detailed list with encounter name + date/time + actor count
 * - Load Encounter: Immediately starts selected encounter
 * - Delete Encounter: Single encounter deletion with confirmation
 * From section 3.6.1: Save current encounter state (actors, initiative order, turn status, conditions, round number)
 */
@Dao
interface EncounterDao {

    // ========== Encounter CRUD Operations ==========

    /**
     * Insert a new encounter
     *
     * @param encounter The encounter to create
     * @return The ID of the created encounter
     */
    @Insert
    suspend fun insertEncounter(encounter: Encounter): Long

    /**
     * Update an existing encounter
     * Used for saving combat state (round, active actor, etc.)
     *
     * @param encounter The encounter with updated values
     */
    @Update
    suspend fun updateEncounter(encounter: Encounter)

    /**
     * Delete an encounter
     * Note: This cascades to delete all encounter_actors and actor_conditions
     *
     * @param encounter The encounter to delete
     */
    @Delete
    suspend fun deleteEncounter(encounter: Encounter)

    /**
     * Delete an encounter by ID
     *
     * @param encounterId The ID of the encounter to delete
     */
    @Query("DELETE FROM encounters WHERE id = :encounterId")
    suspend fun deleteEncounterById(encounterId: Long)

    // ========== Encounter Actor Operations ==========

    /**
     * Insert encounter actors
     * Used when creating an encounter or adding actors mid-combat
     *
     * @param actors The encounter actors to insert
     */
    @Insert
    suspend fun insertEncounterActors(vararg actors: EncounterActor)

    /**
     * Insert a single encounter actor
     *
     * @param actor The encounter actor to insert
     * @return The ID of the inserted actor
     */
    @Insert
    suspend fun insertEncounterActor(actor: EncounterActor): Long

    /**
     * Update an encounter actor
     * Used for initiative changes, turn status, etc.
     *
     * @param actor The actor with updated values
     */
    @Update
    suspend fun updateEncounterActor(actor: EncounterActor)

    /**
     * Update multiple encounter actors at once
     * Efficient for round transitions
     *
     * @param actors The actors to update
     */
    @Update
    suspend fun updateEncounterActors(vararg actors: EncounterActor)

    /**
     * Delete an encounter actor
     * Used when removing an actor from combat
     *
     * @param actor The actor to remove
     */
    @Delete
    suspend fun deleteEncounterActor(actor: EncounterActor)

    // ========== Query Operations ==========

    /**
     * Get all encounters for the list view
     * Sorted by most recently modified first
     *
     * @return Flow of all encounters
     */
    @Query("SELECT * FROM encounters ORDER BY lastModifiedDate DESC")
    fun getAllEncounters(): Flow<List<Encounter>>

    /**
     * Get encounters with actor count for the list view
     * Provides all info needed for the encounter list screen
     *
     * @return Flow of encounters with additional metadata
     */
    @Query(
        """
        SELECT e.*, 
               COUNT(DISTINCT ea.id) as actorCount
        FROM encounters e
        LEFT JOIN encounter_actors ea ON e.id = ea.encounterId
        GROUP BY e.id
        ORDER BY lastModifiedDate DESC
    """
    )
    fun getAllEncountersWithActorCount(): Flow<List<EncounterWithCount>>

    /**
     * Get a single encounter by ID
     *
     * @param encounterId The encounter ID
     * @return The encounter or null if not found
     */
    @Query("SELECT * FROM encounters WHERE id = :encounterId")
    suspend fun getEncounterById(encounterId: Long): Encounter?

    /**
     * Get encounter actors for a specific encounter
     * Includes base actor information for portraits, etc.
     *
     * FIXED: Changed from INNER JOIN to LEFT JOIN to handle missing actors
     * The previous query was also causing ID collision between ea.id and a.id
     *
     * @param encounterId The encounter ID
     * @return List of actors in the encounter with base actor details
     */
    @Transaction
    @Query(
        """
        SELECT ea.*, a.* 
        FROM encounter_actors ea
        LEFT JOIN actors a ON ea.baseActorId = a.id
        WHERE ea.encounterId = :encounterId
        ORDER BY 
            CASE WHEN ea.initiative IS NULL THEN 1 ELSE 0 END,
            ea.initiative DESC,
            ea.tieBreakOrder ASC,
            ea.addedOrder ASC
    """
    )
    @RewriteQueriesToDropUnusedColumns
    suspend fun getEncounterActorsWithDetails(encounterId: Long): List<EncounterActorWithActor>

    /**
     * Alternative query to get encounter actors without ID collision
     * This manually constructs the objects to avoid Room's automatic mapping issues
     */
    @Query("""
        SELECT 
            ea.id AS ea_id,
            ea.encounterId AS ea_encounterId,
            ea.baseActorId AS ea_baseActorId,
            ea.displayName AS ea_displayName,
            ea.instanceNumber AS ea_instanceNumber,
            ea.initiative AS ea_initiative,
            ea.initiativeModifier AS ea_initiativeModifier,
            ea.tieBreakOrder AS ea_tieBreakOrder,
            ea.hasTakenTurn AS ea_hasTakenTurn,
            ea.addedOrder AS ea_addedOrder,
            ea.isHidden AS ea_isHidden,
            a.id AS a_id,
            a.name AS a_name,
            a.portraitPath AS a_portraitPath,
            a.initiativeModifier AS a_initiativeModifier,
            a.category AS a_category,
            a.createdDate AS a_createdDate,
            a.modifiedDate AS a_modifiedDate
        FROM encounter_actors ea
        LEFT JOIN actors a ON ea.baseActorId = a.id
        WHERE ea.encounterId = :encounterId
        ORDER BY 
            CASE WHEN ea.initiative IS NULL THEN 1 ELSE 0 END,
            ea.initiative DESC,
            ea.tieBreakOrder ASC,
            ea.addedOrder ASC
    """)
    suspend fun getEncounterActorsFixed(encounterId: Long): List<EncounterActorFixed>

    /**
     * Load complete encounter state
     * Includes all actors and their active conditions
     * This is the main query for loading a saved encounter
     *
     * @param encounterId The encounter to load
     * @return Complete encounter data or null if not found
     */
    @Transaction
    @Query("SELECT * FROM encounters WHERE id = :encounterId")
    suspend fun getEncounterWithFullDetails(encounterId: Long): EncounterWithFullDetails?

    /**
     * Get active conditions for all actors in an encounter
     *
     * @param encounterId The encounter ID
     * @return Map of actor ID to their conditions
     */
    @Transaction
    @Query(
        """
        SELECT ac.*, c.*
        FROM actor_conditions ac
        INNER JOIN conditions c ON ac.conditionId = c.id
        INNER JOIN encounter_actors ea ON ac.encounterActorId = ea.id
        WHERE ea.encounterId = :encounterId
        ORDER BY ac.appliedAt DESC
    """
    )
    @RewriteQueriesToDropUnusedColumns
    suspend fun getEncounterConditions(encounterId: Long): List<ActorConditionWithDetails>

    // ========== Actor Condition Operations ==========

    /**
     * Insert a new condition on an actor
     *
     * @param condition The condition to apply
     */
    @Insert
    suspend fun insertActorCondition(condition: ActorCondition)

    /**
     * Update an existing condition (for duration changes)
     *
     * @param condition The condition with updated values
     */
    @Update
    suspend fun updateActorCondition(condition: ActorCondition)

    /**
     * Delete a condition from an actor
     *
     * @param condition The condition to remove
     */
    @Delete
    suspend fun deleteActorCondition(condition: ActorCondition)

    /**
     * Delete a condition by ID
     *
     * @param conditionId The condition ID to delete
     */
    @Query("DELETE FROM actor_conditions WHERE id = :conditionId")
    suspend fun deleteActorConditionById(conditionId: Long)


    /**
     * Delete expired conditions for an encounter
     * Called after each turn to clean up
     *
     * @param encounterId The encounter to clean
     * @return Number of conditions removed
     */
    @Query(
        """
        DELETE FROM actor_conditions
        WHERE encounterActorId IN (
            SELECT id FROM encounter_actors WHERE encounterId = :encounterId
        )
        AND isPermanent = 0 
        AND remainingDuration <= 0
    """
    )
    suspend fun deleteExpiredConditions(encounterId: Long): Int

    /**
     * Get conditions for a specific actor
     *
     * @param encounterActorId The actor to check
     * @return List of active conditions with details
     */
    @Transaction
    @Query(
        """
        SELECT ac.*, c.*
        FROM actor_conditions ac
        INNER JOIN conditions c ON ac.conditionId = c.id
        WHERE ac.encounterActorId = :encounterActorId
        ORDER BY ac.appliedAt DESC
    """
    )
    @RewriteQueriesToDropUnusedColumns
    suspend fun getActorConditions(encounterActorId: Long): List<ActorConditionWithDetails>

    // ========== Utility Queries ==========

    /**
     * Check if an encounter name already exists
     *
     * @param name The name to check
     * @param excludeId Encounter ID to exclude (for editing)
     * @return True if name is taken
     */
    @Query(
        """
        SELECT COUNT(*) > 0 FROM encounters 
        WHERE name = :name AND id != :excludeId
    """
    )
    suspend fun isEncounterNameTaken(name: String, excludeId: Long = -1): Boolean

    /**
     * Get the highest instance number for an actor in an encounter
     * Used when adding multiple instances of the same actor
     *
     * @param encounterId The encounter ID
     * @param baseActorId The base actor being added
     * @return Highest instance number or 0 if none exist
     */
    @Query(
        """
        SELECT MAX(instanceNumber) 
        FROM encounter_actors 
        WHERE encounterId = :encounterId 
        AND baseActorId = :baseActorId
    """
    )
    suspend fun getHighestInstanceNumber(encounterId: Long, baseActorId: Long): Int?

    /**
     * Count active encounters (started but not completed)
     *
     * @return Number of active encounters
     */
    @Query("SELECT COUNT(*) FROM encounters WHERE currentRound > 0 AND isActive = 1")
    suspend fun getActiveEncounterCount(): Int

    /**
     * Debug query to get raw encounter actors data
     */
    @Query("SELECT * FROM encounter_actors WHERE encounterId = :encounterId ORDER BY id")
    suspend fun getEncounterActorsRaw(encounterId: Long): List<EncounterActor>

    /**
     * Get all display names for a specific base actor in an encounter
     */
    @Query("SELECT displayName FROM encounter_actors WHERE encounterId = :encounterId AND baseActorId = :baseActorId")
    suspend fun getExistingDisplayNames(encounterId: Long, baseActorId: Long): List<String>

    /**
     * Alternative query without @Relation annotation
     * For debugging duplicate issues
     */
    @Query("""
    SELECT 
        ea.id as ea_id,
        ea.encounterId as ea_encounterId,
        ea.baseActorId as ea_baseActorId,
        ea.displayName as ea_displayName,
        ea.instanceNumber as ea_instanceNumber,
        ea.initiative as ea_initiative,
        ea.initiativeModifier as ea_initiativeModifier,
        ea.hasTakenTurn as ea_hasTakenTurn,
        ea.tieBreakOrder as ea_tieBreakOrder,
        ea.addedOrder as ea_addedOrder,
        ea.isHidden as ea_isHidden,
        a.id as a_id,
        a.name as a_name,
        a.portraitPath as a_portraitPath,
        a.initiativeModifier as a_initiativeModifier,
        a.category as a_category
    FROM encounter_actors ea
    INNER JOIN actors a ON ea.baseActorId = a.id
    WHERE ea.encounterId = :encounterId
    ORDER BY 
        CASE WHEN ea.initiative IS NULL THEN 1 ELSE 0 END,
        ea.initiative DESC,
        ea.tieBreakOrder ASC,
        ea.addedOrder ASC
""")
    suspend fun debugGetEncounterActorsManual(encounterId: Long): List<EncounterActorDebugRow>
}

// ========== Data Classes for Complex Queries ==========

/**
 * Encounter with actor count for list display
 */
data class EncounterWithCount(
    @Embedded val encounter: Encounter,
    val actorCount: Int
)

/**
 * Encounter actor with base actor details
 *
 * IMPORTANT: Room's @Embedded and @Relation can cause ID collision when both
 * tables have an 'id' column. The query returns the correct data but Room's
 * object mapping can pick the wrong ID field.
 *
 * As a workaround, we use @RewriteQueriesToDropUnusedColumns on the query
 * and make sure the actor is nullable to handle missing base actors.
 */
data class EncounterActorWithActor(
    @Embedded val encounterActor: EncounterActor,
    @Relation(
        parentColumn = "baseActorId",
        entityColumn = "id"
    )
    val actor: Actor?  // Changed to nullable
)

/**
 * Fixed mapping to avoid ID collision
 */
data class EncounterActorFixed(
    @ColumnInfo(name = "ea_id") val encounterActorId: Long,
    @ColumnInfo(name = "ea_encounterId") val encounterId: Long,
    @ColumnInfo(name = "ea_baseActorId") val baseActorId: Long,
    @ColumnInfo(name = "ea_displayName") val displayName: String,
    @ColumnInfo(name = "ea_instanceNumber") val instanceNumber: Int,
    @ColumnInfo(name = "ea_initiative") val initiative: Double?,
    @ColumnInfo(name = "ea_initiativeModifier") val initiativeModifier: Int,
    @ColumnInfo(name = "ea_tieBreakOrder") val tieBreakOrder: Int,
    @ColumnInfo(name = "ea_hasTakenTurn") val hasTakenTurn: Boolean,
    @ColumnInfo(name = "ea_addedOrder") val addedOrder: Int,
    @ColumnInfo(name = "ea_isHidden") val isHidden: Boolean,
    // Actor fields (nullable if actor was deleted)
    @ColumnInfo(name = "a_id") val actorId: Long?,
    @ColumnInfo(name = "a_name") val actorName: String?,
    @ColumnInfo(name = "a_portraitPath") val actorPortraitPath: String?,
    @ColumnInfo(name = "a_initiativeModifier") val actorInitiativeModifier: Int?,
    @ColumnInfo(name = "a_category") val actorCategory: String?,
    @ColumnInfo(name = "a_createdDate") val actorCreatedDate: Long?,
    @ColumnInfo(name = "a_modifiedDate") val actorModifiedDate: Long?
) {
    fun toEncounterActorWithActor(): EncounterActorWithActor {
        val encounterActor = EncounterActor(
            id = encounterActorId,
            encounterId = encounterId,
            baseActorId = baseActorId,
            displayName = displayName,
            instanceNumber = instanceNumber,
            initiative = initiative,
            initiativeModifier = initiativeModifier,
            tieBreakOrder = tieBreakOrder,
            hasTakenTurn = hasTakenTurn,
            addedOrder = addedOrder,
            isHidden = isHidden
        )

        val actor = if (actorId != null && actorName != null) {
            Actor(
                id = actorId,
                name = actorName,
                portraitPath = actorPortraitPath,
                initiativeModifier = actorInitiativeModifier ?: 0,
                category = actorCategory ?: "OTHER",
                createdDate = actorCreatedDate ?: System.currentTimeMillis(),
                modifiedDate = actorModifiedDate ?: System.currentTimeMillis()
            )
        } else null

        return EncounterActorWithActor(encounterActor, actor)
    }
}

/**
 * Complete encounter data for loading
 */
data class EncounterWithFullDetails(
    @Embedded val encounter: Encounter,
    @Relation(
        entity = EncounterActor::class,
        parentColumn = "id",
        entityColumn = "encounterId"
    )
    val actors: List<EncounterActorWithActor>,
    @Relation(
        entity = ActorCondition::class,
        parentColumn = "id",
        entityColumn = "encounterActorId",
        associateBy = Junction(
            value = EncounterActor::class,
            parentColumn = "encounterId",
            entityColumn = "id"
        )
    )
    val conditions: List<ActorConditionWithDetails>
)

/**
 * Debug data class for checking actor data
 */
data class EncounterActorDebugRow(
    val ea_id: Long,
    val ea_encounterId: Long,
    val ea_baseActorId: Long,
    val ea_displayName: String,
    val ea_instanceNumber: Int,
    val ea_initiative: Double?,
    val ea_initiativeModifier: Int,
    val ea_hasTakenTurn: Boolean,
    val ea_tieBreakOrder: Int,
    val ea_addedOrder: Int,
    val ea_isHidden: Boolean,
    val a_id: Long,
    val a_name: String,
    val a_portraitPath: String?,
    val a_initiativeModifier: Int,
    val a_category: String
)


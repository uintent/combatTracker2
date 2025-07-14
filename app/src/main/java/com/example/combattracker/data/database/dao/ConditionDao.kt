// File: ConditionDao.kt
package com.example.combattracker.data.database.dao

import androidx.room.*
import com.example.combattracker.data.database.entities.Condition
import kotlinx.coroutines.flow.Flow

/**
 * ConditionDao - Data Access Object for Condition entities
 *
 * Purpose:
 * - Manages the pre-populated condition templates
 * - Primarily read-only as conditions are pre-defined
 * - Supports initial population and potential future updates
 *
 * Requirements Reference:
 * From section 3.5.1: Pre-built Condition Library - 15 D&D Conditions
 * From Application class: Pre-populate conditions on first launch
 *
 * Note: This DAO is simpler than others because conditions are mostly static data
 */
@Dao
interface ConditionDao {

    // ========== Population Operations ==========

    /**
     * Insert all pre-built conditions
     * Called once on first app launch to populate the database
     * Uses REPLACE strategy in case we need to update conditions in future versions
     *
     * @param conditions The list of all 15 D&D conditions
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllConditions(conditions: List<Condition>)

    /**
     * Insert a single condition
     * Primarily for testing or future custom conditions
     *
     * @param condition The condition to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCondition(condition: Condition)

    // ========== Query Operations ==========

    /**
     * Get all conditions for display in the UI
     * Sorted by display order (which matches the D&D standard order)
     *
     * @return Flow of all conditions
     */
    @Query("SELECT * FROM conditions WHERE isEnabled = 1 ORDER BY displayOrder ASC")
    fun getAllConditions(): Flow<List<Condition>>

    /**
     * Get all conditions as a one-time query
     * Used for populating the context menu
     *
     * @return List of all enabled conditions
     */
    @Query("SELECT * FROM conditions WHERE isEnabled = 1 ORDER BY displayOrder ASC")
    suspend fun getAllConditionsList(): List<Condition>

    /**
     * Get a specific condition by ID
     *
     * @param conditionId The condition ID (1-15)
     * @return The condition or null if not found
     */
    @Query("SELECT * FROM conditions WHERE id = :conditionId")
    suspend fun getConditionById(conditionId: Long): Condition?

    /**
     * Get multiple conditions by their IDs
     * Useful for displaying active conditions on an actor
     *
     * @param conditionIds List of condition IDs
     * @return List of conditions found
     */
    @Query("SELECT * FROM conditions WHERE id IN (:conditionIds)")
    suspend fun getConditionsByIds(conditionIds: List<Long>): List<Condition>

    /**
     * Get condition by name (case-insensitive)
     * Useful for imports or command-based systems
     *
     * @param name The condition name
     * @return The matching condition or null
     */
    @Query("SELECT * FROM conditions WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getConditionByName(name: String): Condition?

    // ========== Validation Operations ==========

    /**
     * Check if conditions table is populated
     * Used to determine if initial population is needed
     *
     * @return True if conditions exist
     */
    @Query("SELECT COUNT(*) > 0 FROM conditions")
    suspend fun hasConditions(): Boolean

    /**
     * Get count of conditions
     * Should always return 15 for the standard D&D conditions
     *
     * @return Number of conditions in database
     */
    @Query("SELECT COUNT(*) FROM conditions")
    suspend fun getConditionCount(): Int

    /**
     * Verify all expected conditions exist
     * Checks that all 15 conditions are present
     *
     * @return True if all expected conditions exist
     */
    @Query("SELECT COUNT(*) = 15 FROM conditions WHERE id BETWEEN 1 AND 15")
    suspend fun hasAllStandardConditions(): Boolean

    // ========== Update Operations (Future Use) ==========

    /**
     * Update a condition
     * For future use if we allow customization
     *
     * @param condition The condition with updated values
     */
    @Update
    suspend fun updateCondition(condition: Condition)

    /**
     * Enable or disable a condition
     * For future use to hide conditions
     *
     * @param conditionId The condition to update
     * @param isEnabled New enabled state
     */
    @Query("UPDATE conditions SET isEnabled = :isEnabled WHERE id = :conditionId")
    suspend fun setConditionEnabled(conditionId: Long, isEnabled: Boolean)

    // ========== Debug/Admin Operations ==========

    /**
     * Delete all conditions
     * WARNING: Only for testing/reset. Will break actor conditions!
     *
     * @return Number of conditions deleted
     */
    @Query("DELETE FROM conditions")
    suspend fun deleteAllConditions(): Int

    /**
     * Reset conditions to default state
     * Deletes all and expects re-population
     *
     * @return Number of conditions deleted
     */
    @Transaction
    suspend fun resetConditions(): Int {
        return deleteAllConditions()
    }

    /**
     * Get conditions with usage count
     * Shows how many actors currently have each condition
     * Useful for analytics or debugging
     *
     * @return List of conditions with usage statistics
     */
    @Query("""
        SELECT c.*, 
               COUNT(ac.id) as activeCount
        FROM conditions c
        LEFT JOIN actor_conditions ac ON c.id = ac.conditionId
        GROUP BY c.id
        ORDER BY c.displayOrder ASC
    """)
    suspend fun getConditionsWithUsageCount(): List<ConditionWithUsageCount>
}

// ========== Data Classes for Complex Queries ==========

/**
 * Condition with usage statistics
 */
data class ConditionWithUsageCount(
    @Embedded val condition: Condition,
    val activeCount: Int
)
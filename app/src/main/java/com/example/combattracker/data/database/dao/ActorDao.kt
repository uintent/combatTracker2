// File: ActorDao.kt
package com.example.combattracker.data.database.dao

import androidx.room.*
import com.example.combattracker.data.database.entities.Actor
import kotlinx.coroutines.flow.Flow

/**
 * ActorDao - Data Access Object for Actor entities
 *
 * Purpose:
 * - Provides all database operations for managing actors in the library
 * - Returns Flow for reactive UI updates when data changes
 * - Handles search, filter, and sort operations
 *
 * Requirements Reference:
 * From section 3.1.3: Actor Library Management
 * - Search: Text-based search within actor names
 * - Sort: Alphabetical sorting by name, category, or initiative modifier
 * - Filter: Filter by actor category (Players, NPCs, Monsters, Others)
 * - Actions: Add new actor, edit existing actor, delete actor
 */
@Dao
interface ActorDao {

    // ========== Basic CRUD Operations ==========

    /**
     * Insert a new actor into the database
     *
     * @param actor The actor to insert
     * @return The ID of the inserted actor
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertActor(actor: Actor): Long

    /**
     * Insert multiple actors at once
     * Useful for testing or data import
     *
     * @param actors List of actors to insert
     * @return List of inserted IDs
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertActors(vararg actors: Actor): List<Long>

    /**
     * Update an existing actor
     *
     * @param actor The actor with updated values
     * @return Number of actors updated (should be 1)
     */
    @Update
    suspend fun updateActor(actor: Actor): Int

    /**
     * Delete an actor from the library
     * Note: Will fail if actor is used in any encounters (foreign key constraint)
     *
     * @param actor The actor to delete
     * @return Number of actors deleted (should be 1)
     */
    @Delete
    suspend fun deleteActor(actor: Actor): Int

    /**
     * Delete an actor by ID
     *
     * @param actorId The ID of the actor to delete
     * @return Number of actors deleted
     */
    @Query("DELETE FROM actors WHERE id = :actorId")
    suspend fun deleteActorById(actorId: Long): Int

    // ========== Query Operations ==========

    /**
     * Get all actors from the database
     * Default sort: alphabetical by name
     *
     * @return Flow emitting list of all actors, updates on changes
     */
    @Query("SELECT * FROM actors ORDER BY name ASC")
    fun getAllActors(): Flow<List<Actor>>

    /**
     * Get all actors sorted by name (A-Z)
     *
     * @return Flow of actors sorted alphabetically
     */
    @Query("SELECT * FROM actors ORDER BY name ASC")
    fun getAllActorsSortedByName(): Flow<List<Actor>>

    /**
     * Get all actors sorted by category then name
     * Groups actors by category in the list
     *
     * @return Flow of actors sorted by category, then name
     */
    @Query("SELECT * FROM actors ORDER BY category ASC, name ASC")
    fun getAllActorsSortedByCategory(): Flow<List<Actor>>

    /**
     * Get all actors sorted by initiative modifier (highest first)
     * Useful for seeing fastest actors
     *
     * @return Flow of actors sorted by initiative modifier descending
     */
    @Query("SELECT * FROM actors ORDER BY initiativeModifier DESC, name ASC")
    fun getAllActorsSortedByInitiative(): Flow<List<Actor>>

    /**
     * Get a single actor by ID
     *
     * @param actorId The ID of the actor to retrieve
     * @return The actor if found, null otherwise
     */
    @Query("SELECT * FROM actors WHERE id = :actorId")
    suspend fun getActorById(actorId: Long): Actor?

    /**
     * Get multiple actors by their IDs
     * Useful for loading actors added to an encounter
     *
     * @param actorIds List of actor IDs
     * @return List of actors found
     */
    @Query("SELECT * FROM actors WHERE id IN (:actorIds)")
    suspend fun getActorsByIds(actorIds: List<Long>): List<Actor>

    // ========== Search Operations ==========

    /**
     * Search actors by name (case-insensitive partial match)
     *
     * @param searchQuery The search term
     * @return Flow of actors matching the search
     */
    @Query("""
        SELECT * FROM actors 
        WHERE LOWER(name) LIKE '%' || LOWER(:searchQuery) || '%' 
        ORDER BY name ASC
    """)
    fun searchActorsByName(searchQuery: String): Flow<List<Actor>>

    // ========== Filter Operations ==========

    /**
     * Get actors filtered by category
     *
     * @param category The category to filter by (PLAYER, NPC, MONSTER, OTHER)
     * @return Flow of actors in the specified category
     */
    @Query("SELECT * FROM actors WHERE category = :category ORDER BY name ASC")
    fun getActorsByCategory(category: String): Flow<List<Actor>>

    /**
     * Get actors filtered by multiple categories
     *
     * @param categories List of categories to include
     * @return Flow of actors in any of the specified categories
     */
    @Query("SELECT * FROM actors WHERE category IN (:categories) ORDER BY name ASC")
    fun getActorsByCategories(categories: List<String>): Flow<List<Actor>>

    // ========== Combined Search and Filter ==========

    /**
     * Search actors by name within a specific category
     *
     * @param searchQuery The search term
     * @param category The category to filter by
     * @return Flow of matching actors
     */
    @Query("""
        SELECT * FROM actors 
        WHERE LOWER(name) LIKE '%' || LOWER(:searchQuery) || '%' 
        AND category = :category
        ORDER BY name ASC
    """)
    fun searchActorsByCategoryAndName(searchQuery: String, category: String): Flow<List<Actor>>

    /**
     * Advanced search with all filters and sorting
     *
     * @param searchQuery Optional search term (use % for all)
     * @param categories List of categories to include (empty for all)
     * @param sortBy Sort field: "name", "category", or "initiative"
     * @param ascending True for ascending, false for descending
     * @return Flow of filtered and sorted actors
     */
    @Query("""
        SELECT * FROM actors 
        WHERE LOWER(name) LIKE '%' || LOWER(:searchQuery) || '%'
        AND (CASE WHEN :filterByCategory THEN category IN (:categories) ELSE 1 END)
        ORDER BY 
            CASE WHEN :sortBy = 'name' AND :ascending = 1 THEN name END ASC,
            CASE WHEN :sortBy = 'name' AND :ascending = 0 THEN name END DESC,
            CASE WHEN :sortBy = 'category' AND :ascending = 1 THEN category END ASC,
            CASE WHEN :sortBy = 'category' AND :ascending = 0 THEN category END DESC,
            CASE WHEN :sortBy = 'initiative' AND :ascending = 1 THEN initiativeModifier END ASC,
            CASE WHEN :sortBy = 'initiative' AND :ascending = 0 THEN initiativeModifier END DESC,
            name ASC
    """)
    fun searchActorsAdvanced(
        searchQuery: String,
        categories: List<String>,
        filterByCategory: Boolean,
        sortBy: String,
        ascending: Boolean
    ): Flow<List<Actor>>

    // ========== Statistics and Utility Queries ==========

    /**
     * Get count of all actors in the library
     *
     * @return Total number of actors
     */
    @Query("SELECT COUNT(*) FROM actors")
    suspend fun getActorCount(): Int

    /**
     * Get count of actors by category
     *
     * @param category The category to count
     * @return Number of actors in that category
     */
    @Query("SELECT COUNT(*) FROM actors WHERE category = :category")
    suspend fun getActorCountByCategory(category: String): Int

    /**
     * Check if an actor name already exists (case-insensitive)
     * Useful for validation when creating new actors
     *
     * @param name The name to check
     * @param excludeId Optional ID to exclude (for editing)
     * @return True if name exists
     */
    @Query("""
        SELECT COUNT(*) > 0 FROM actors 
        WHERE LOWER(name) = LOWER(:name) 
        AND id != :excludeId
    """)
    suspend fun isNameTaken(name: String, excludeId: Long = -1): Boolean

    /**
     * Get all unique portrait paths
     * Useful for cleanup of unused images
     *
     * @return List of all portrait paths in use
     */
    @Query("SELECT DISTINCT portraitPath FROM actors WHERE portraitPath IS NOT NULL")
    suspend fun getAllPortraitPaths(): List<String>

    /**
     * Check if an actor is used in any encounters
     *
     * @param actorId The actor to check
     * @return True if actor is in any encounter
     */
    @Query("""
        SELECT COUNT(*) > 0 FROM encounter_actors 
        WHERE baseActorId = :actorId
    """)
    suspend fun isActorInUse(actorId: Long): Boolean

    /**
     * Get recently created actors
     *
     * @param limit Number of actors to return
     * @return List of most recently created actors
     */
    @Query("SELECT * FROM actors ORDER BY createdDate DESC LIMIT :limit")
    suspend fun getRecentActors(limit: Int = 10): List<Actor>

    /**
     * Get all actors (not as Flow, for debugging)
     */
    @Query("SELECT * FROM actors ORDER BY name")
    suspend fun getAllActorsOnce(): List<Actor>

}
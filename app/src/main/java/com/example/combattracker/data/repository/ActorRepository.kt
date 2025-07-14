// File: ActorRepository.kt
package com.example.combattracker.data.repository

import android.net.Uri
import com.example.combattracker.data.database.dao.ActorDao
import com.example.combattracker.data.database.entities.Actor
import com.example.combattracker.data.model.ActorCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

/**
 * ActorRepository - Business logic layer for actor management
 *
 * Purpose:
 * - Abstracts database operations from UI layer
 * - Coordinates between ActorDao and ImageRepository
 * - Handles business rules and validation
 * - Manages actor lifecycle including portrait images
 *
 * Requirements Reference:
 * From section 3.1.2: Actor Creation and Editing
 * - Users can create new actors with all required fields
 * - Users can edit existing actors (change picture, name, category, initiative modifier)
 * - Users can delete actors from their library
 * - Actor portraits copied to app's internal storage when selected
 */
class ActorRepository(
    private val actorDao: ActorDao,
    private val imageRepository: ImageRepository
) {

    // ========== Query Operations ==========

    /**
     * Get all actors from the library
     *
     * @return Flow of all actors, updates automatically when data changes
     */
    fun getAllActors(): Flow<List<Actor>> {
        return actorDao.getAllActors()
    }

    /**
     * Get actors sorted by specific criteria
     *
     * @param sortBy Sort criteria: "name", "category", or "initiative"
     * @return Flow of sorted actors
     */
    fun getActorsSorted(sortBy: String): Flow<List<Actor>> {
        return when (sortBy.lowercase()) {
            "name" -> actorDao.getAllActorsSortedByName()
            "category" -> actorDao.getAllActorsSortedByCategory()
            "initiative" -> actorDao.getAllActorsSortedByInitiative()
            else -> actorDao.getAllActorsSortedByName()
        }
    }

    /**
     * Search actors by name
     *
     * @param query Search query (partial match)
     * @return Flow of matching actors
     */
    fun searchActors(query: String): Flow<List<Actor>> {
        return actorDao.searchActorsByName(query)
    }

    /**
     * Get actors filtered by category
     *
     * @param category Category to filter by
     * @return Flow of actors in that category
     */
    fun getActorsByCategory(category: ActorCategory): Flow<List<Actor>> {
        return actorDao.getActorsByCategory(category.name)
    }

    /**
     * Advanced search with multiple filters
     *
     * @param query Search query (empty string for all)
     * @param categories Categories to include (empty for all)
     * @param sortBy Sort field
     * @param ascending Sort direction
     * @return Flow of filtered and sorted actors
     */
    fun searchActorsAdvanced(
        query: String = "",
        categories: List<ActorCategory> = emptyList(),
        sortBy: String = "name",
        ascending: Boolean = true
    ): Flow<List<Actor>> {
        val categoryNames = categories.map { it.name }
        val filterByCategory = categories.isNotEmpty()

        return actorDao.searchActorsAdvanced(
            searchQuery = query,
            categories = categoryNames,
            filterByCategory = filterByCategory,
            sortBy = sortBy,
            ascending = ascending
        )
    }

    /**
     * Get a single actor by ID
     *
     * @param actorId The actor's ID
     * @return The actor or null if not found
     */
    suspend fun getActorById(actorId: Long): Actor? {
        return withContext(Dispatchers.IO) {
            actorDao.getActorById(actorId)
        }
    }

    // ========== Create Operations ==========

    /**
     * Create a new actor with optional portrait
     *
     * Business Rules:
     * - Name must not be blank
     * - Name must be unique (case-insensitive)
     * - Portrait is copied to internal storage if provided
     * - Initiative modifier must be reasonable (-99 to 99)
     *
     * @param name Actor name (will be trimmed)
     * @param category Actor category
     * @param initiativeModifier Initiative bonus/penalty
     * @param portraitUri Optional URI to portrait image
     * @return Result with actor ID or error
     */
    suspend fun createActor(
        name: String,
        category: ActorCategory,
        initiativeModifier: Int,
        portraitUri: Uri?
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // Validate name
            val trimmedName = name.trim()
            if (trimmedName.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Actor name cannot be empty")
                )
            }

            // Check for duplicate name
            if (actorDao.isNameTaken(trimmedName)) {
                return@withContext Result.failure(
                    IllegalArgumentException("An actor named '$trimmedName' already exists")
                )
            }

            // Validate initiative modifier
            if (initiativeModifier !in -99..99) {
                return@withContext Result.failure(
                    IllegalArgumentException("Initiative modifier must be between -99 and 99")
                )
            }

            // Process portrait if provided
            val portraitPath = portraitUri?.let { uri ->
                try {
                    imageRepository.saveActorPortrait(uri, trimmedName)
                } catch (e: IOException) {
                    Timber.e(e, "Failed to save portrait for $trimmedName")
                    // Continue without portrait rather than failing entirely
                    null
                }
            }

            // Create the actor
            val actor = Actor.create(
                name = trimmedName,
                category = category,
                initiativeModifier = initiativeModifier,
                portraitPath = portraitPath
            )

            // Insert into database
            val actorId = actorDao.insertActor(actor)

            Timber.d("Created actor: $trimmedName (ID: $actorId)")
            Result.success(actorId)

        } catch (e: Exception) {
            Timber.e(e, "Failed to create actor")
            Result.failure(e)
        }
    }

    // ========== Update Operations ==========

    /**
     * Update an existing actor
     *
     * Business Rules:
     * - Name must remain unique if changed
     * - Old portrait is deleted if new one is provided
     * - Validation same as creation
     *
     * @param actor The actor with updated values
     * @param newPortraitUri Optional new portrait (null keeps existing)
     * @return Result with success or error
     */
    suspend fun updateActor(
        actor: Actor,
        newPortraitUri: Uri? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Validate the actor
            if (!actor.isValid()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid actor data")
                )
            }

            // Check name uniqueness if changed
            val existingActor = actorDao.getActorById(actor.id)
            if (existingActor == null) {
                return@withContext Result.failure(
                    IllegalStateException("Actor not found")
                )
            }

            if (existingActor.name != actor.name && actorDao.isNameTaken(actor.name, actor.id)) {
                return@withContext Result.failure(
                    IllegalArgumentException("An actor named '${actor.name}' already exists")
                )
            }

            // Handle portrait update
            val updatedActor = if (newPortraitUri != null) {
                // Save new portrait
                val newPortraitPath = try {
                    imageRepository.saveActorPortrait(newPortraitUri, actor.name)
                } catch (e: IOException) {
                    Timber.e(e, "Failed to save new portrait")
                    null
                }

                // Delete old portrait if it exists and is different
                if (newPortraitPath != null && existingActor.portraitPath != null &&
                    existingActor.portraitPath != newPortraitPath) {
                    imageRepository.deletePortrait(existingActor.portraitPath)
                }

                actor.copy(portraitPath = newPortraitPath)
            } else {
                actor
            }

            // Update in database with new timestamp
            actorDao.updateActor(updatedActor.withUpdatedTimestamp())

            Timber.d("Updated actor: ${actor.name} (ID: ${actor.id})")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to update actor")
            Result.failure(e)
        }
    }

    // ========== Delete Operations ==========

    /**
     * Delete an actor from the library
     *
     * Business Rules:
     * - Cannot delete if actor is used in any encounters
     * - Portrait image is deleted along with actor
     *
     * @param actor The actor to delete
     * @return Result with success or error
     */
    suspend fun deleteActor(actor: Actor): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Check if actor is in use
            if (actorDao.isActorInUse(actor.id)) {
                return@withContext Result.failure(
                    IllegalStateException("Cannot delete actor that is used in encounters")
                )
            }

            // Delete from database
            val deletedCount = actorDao.deleteActor(actor)

            if (deletedCount > 0) {
                // Delete portrait if it exists
                actor.portraitPath?.let { path ->
                    try {
                        imageRepository.deletePortrait(path)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to delete portrait for ${actor.name}")
                        // Continue anyway - orphaned file is better than failed deletion
                    }
                }

                Timber.d("Deleted actor: ${actor.name} (ID: ${actor.id})")
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Actor not found"))
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to delete actor")
            Result.failure(e)
        }
    }

    // ========== Utility Operations ==========

    /**
     * Get count of actors by category
     *
     * @return Map of category to count
     */
    suspend fun getActorCountsByCategory(): Map<ActorCategory, Int> = withContext(Dispatchers.IO) {
        val counts = mutableMapOf<ActorCategory, Int>()

        ActorCategory.values().forEach { category ->
            counts[category] = actorDao.getActorCountByCategory(category.name)
        }

        counts
    }

    /**
     * Clean up orphaned portrait images
     * Removes images that are no longer referenced by any actor
     *
     * @return Number of files deleted
     */
    suspend fun cleanupOrphanedPortraits(): Int = withContext(Dispatchers.IO) {
        try {
            val usedPaths = actorDao.getAllPortraitPaths().toSet()
            val deletedCount = imageRepository.cleanupOrphanedPortraits(usedPaths)

            if (deletedCount > 0) {
                Timber.d("Cleaned up $deletedCount orphaned portrait(s)")
            }

            deletedCount
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup orphaned portraits")
            0
        }
    }

    /**
     * Validate actor name availability
     *
     * @param name The name to check
     * @param excludeId Actor ID to exclude (for editing)
     * @return True if name is available
     */
    suspend fun isNameAvailable(name: String, excludeId: Long = -1): Boolean {
        return withContext(Dispatchers.IO) {
            !actorDao.isNameTaken(name.trim(), excludeId)
        }
    }

    /**
     * Get recently created actors
     *
     * @param limit Maximum number to return
     * @return List of recent actors
     */
    suspend fun getRecentActors(limit: Int = 5): List<Actor> {
        return withContext(Dispatchers.IO) {
            actorDao.getRecentActors(limit)
        }
    }
}
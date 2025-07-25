// File: Encounter.kt
package com.example.combattracker.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.*

/**
 * Encounter Entity - Represents a saved combat encounter
 *
 * Purpose:
 * - Stores the metadata for a combat encounter
 * - Allows saving and loading combat sessions
 * - Tracks round count and encounter state
 * - Links to EncounterActor entities for the participants
 *
 * Requirements Reference:
 * From section 3.3.1: Encounter Creation Workflow
 * - Set Encounter Name: Optional field; if not provided, uses format "ENCsave_YYYYMMDD_HHMMSS"
 * From section 3.6.1: Save current encounter state (actors, initiative order, turn status, conditions, round number)
 */
@Entity(tableName = "encounters")
data class Encounter(
    /**
     * Unique identifier for the encounter
     * Auto-generated by Room when inserting
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Display name of the encounter
     * Can be user-provided or auto-generated
     * Examples: "Goblin Ambush", "Boss Fight", "ENCsave_20250713_143052"
     */
    val name: String,

    /**
     * Current round number
     * Starts at 1 when combat begins
     * Increments when all actors have taken their turn
     * 0 means combat hasn't started yet
     */
    val currentRound: Int = 1,

    /**
     * ID of the currently active actor
     * References EncounterActor.id (not Actor.id)
     * Null if no actor is currently active or combat hasn't started
     */
    val currentActorId: Long? = null,

    /**
     * Whether this encounter is currently active
     * Only one encounter should be active at a time
     * Set to false when encounter ends or another is loaded
     */
    val isActive: Boolean = false,

    /**
     * Timestamp when this encounter was created
     * Used for:
     * - Sorting encounters by date
     * - Auto-generated names
     * - Display in encounter list
     */
    val createdDate: Long = System.currentTimeMillis(),

    /**
     * Timestamp when this encounter was last modified
     * Updated whenever:
     * - Round advances
     * - Turn changes
     * - Actors are added/removed
     * - Conditions change
     */
    val lastModifiedDate: Long = System.currentTimeMillis(),

    /**
     * Optional notes about the encounter
     * For DM reference
     * Could store location, context, etc.
     */
    val notes: String? = null
) {
    /**
     * Whether combat has started (round > 0)
     */
    val isStarted: Boolean
        get() = currentRound > 0

    /**
     * Whether the encounter is completed
     * For now, this is a computed property based on isActive
     * In the future, you might want to add this as an actual column
     */
    val isCompleted: Boolean
        get() = !isActive && currentRound > 1

    /**
     * Check if combat has started
     * Combat is considered started if we're past round 0
     *
     * @return True if combat has begun
     */
    fun hasStarted(): Boolean = currentRound > 0

    /**
     * Get formatted creation date for display
     * Used in the encounter list screen
     *
     * @return Date string like "Jul 13, 2025 2:30 PM"
     */
    fun getFormattedCreatedDate(): String {
        val formatter = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.US)
        return formatter.format(Date(createdDate))
    }

    /**
     * Get formatted last modified date for display
     * Shows when the encounter was last played
     *
     * @return Date string like "Jul 13, 2025 2:30 PM"
     */
    fun getFormattedModifiedDate(): String {
        val formatter = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.US)
        return formatter.format(Date(lastModifiedDate))
    }

    /**
     * Get auto-generated name based on creation date
     * Format: "ENCsave_YYYYMMDD_HHMMSS"
     * Used when user doesn't provide a name
     *
     * @return Auto-generated encounter name
     */
    fun getAutoGeneratedName(): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "ENCsave_${formatter.format(Date(createdDate))}"
    }

    /**
     * Create a copy with updated modification date
     * Use this whenever the encounter state changes
     *
     * @return Copy of this encounter with current timestamp
     */
    fun withUpdatedTimestamp(): Encounter {
        return this.copy(lastModifiedDate = System.currentTimeMillis())
    }

    /**
     * Create a copy for saving
     * Deactivates the encounter and updates timestamp
     * Used when saving current encounter
     *
     * @return Copy ready for database storage
     */
    fun forSaving(): Encounter {
        return this.copy(
            isActive = false,
            lastModifiedDate = System.currentTimeMillis()
        )
    }

    /**
     * Create a copy for loading
     * Activates the encounter and resets to round 1 if not started
     *
     * @return Copy ready for combat
     */
    fun forLoading(): Encounter {
        return this.copy(
            isActive = true,
            currentRound = if (currentRound == 0) 1 else currentRound,
            lastModifiedDate = System.currentTimeMillis()
        )
    }

    companion object {
        /**
         * Create a new encounter with optional name
         * If name is not provided or blank, uses auto-generated format
         *
         * @param name Optional encounter name
         * @param createdDate Optional creation timestamp
         * @return New Encounter instance
         */
        fun create(
            name: String? = null,
            createdDate: Long = System.currentTimeMillis()
        ): Encounter {
            val encounter = Encounter(
                name = "", // Temporary, will be set below
                createdDate = createdDate
            )

            // Set name to auto-generated if not provided
            return encounter.copy(
                name = if (name.isNullOrBlank()) {
                    encounter.getAutoGeneratedName()
                } else {
                    name.trim()
                }
            )
        }

        /**
         * Create a sample encounter for testing
         *
         * @param id Optional ID
         * @param name Optional name
         * @return Sample Encounter instance
         */
        fun createSample(
            id: Long = 0,
            name: String = "Test Encounter"
        ): Encounter {
            return Encounter(
                id = id,
                name = name,
                currentRound = 1,
                currentActorId = null,
                isActive = false
            )
        }

        /**
         * Date format pattern for auto-generated names
         * Stored here for consistency
         */
        const val AUTO_NAME_DATE_PATTERN = "yyyyMMdd_HHmmss"

        /**
         * Prefix for auto-generated encounter names
         */
        const val AUTO_NAME_PREFIX = "ENCsave_"
    }
}
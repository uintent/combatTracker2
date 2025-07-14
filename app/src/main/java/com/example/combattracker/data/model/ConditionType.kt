// File: ConditionType.kt
package com.example.combattracker.data.model

/**
 * ConditionType - Enum representing the 15 D&D 5e conditions
 *
 * Purpose:
 * - Defines all available conditions that can be applied to actors in combat
 * - Provides display names and descriptions for UI
 * - These are cosmetic only with no functional impact on combat mechanics
 *
 * Requirements Reference:
 * From section 3.5.1: "15 D&D Conditions (cosmetic only, no functional impact)"
 * Listed in the exact order from the requirements document
 */
enum class ConditionType(
    /**
     * Unique ID for database storage
     * Matches the IDs from requirements for consistency
     */
    val id: Long,

    /**
     * Display name shown in the UI
     */
    val displayName: String,

    /**
     * Brief description of the condition's effect in D&D
     * For reference only - no mechanical implementation
     */
    val description: String,

    /**
     * Icon resource name for the condition
     * Will be implemented as vector drawables
     */
    val iconResource: String
) {
    /**
     * 1. Blinded
     * A blinded creature can't see and automatically fails ability checks that require sight.
     */
    BLINDED(
        id = 1,
        displayName = "Blinded",
        description = "You can't see",
        iconResource = "ic_condition_blinded"
    ),

    /**
     * 2. Charmed
     * A charmed creature can't attack the charmer or target the charmer with harmful abilities.
     */
    CHARMED(
        id = 2,
        displayName = "Charmed",
        description = "You are charmed",
        iconResource = "ic_condition_charmed"
    ),

    /**
     * 3. Deafened
     * A deafened creature can't hear and automatically fails ability checks that require hearing.
     */
    DEAFENED(
        id = 3,
        displayName = "Deafened",
        description = "You can't hear",
        iconResource = "ic_condition_deafened"
    ),

    /**
     * 4. Exhaustion
     * Exhaustion is measured in levels, with increasing penalties at each level.
     */
    EXHAUSTION(
        id = 4,
        displayName = "Exhaustion",
        description = "You are exhausted",
        iconResource = "ic_condition_exhaustion"
    ),

    /**
     * 5. Frightened
     * A frightened creature has disadvantage on ability checks and attacks while source is in sight.
     */
    FRIGHTENED(
        id = 5,
        displayName = "Frightened",
        description = "You are frightened",
        iconResource = "ic_condition_frightened"
    ),

    /**
     * 6. Grappled
     * A grappled creature's speed becomes 0, and it can't benefit from speed bonuses.
     */
    GRAPPLED(
        id = 6,
        displayName = "Grappled",
        description = "You are grappled",
        iconResource = "ic_condition_grappled"
    ),

    /**
     * 7. Incapacitated
     * An incapacitated creature can't take actions or reactions.
     */
    INCAPACITATED(
        id = 7,
        displayName = "Incapacitated",
        description = "You can't take actions or reactions",
        iconResource = "ic_condition_incapacitated"
    ),

    /**
     * 8. Invisible
     * An invisible creature is impossible to see without special senses.
     */
    INVISIBLE(
        id = 8,
        displayName = "Invisible",
        description = "You can't be seen",
        iconResource = "ic_condition_invisible"
    ),

    /**
     * 9. Paralyzed
     * A paralyzed creature is incapacitated and can't move or speak.
     */
    PARALYZED(
        id = 9,
        displayName = "Paralyzed",
        description = "You are paralyzed",
        iconResource = "ic_condition_paralyzed"
    ),

    /**
     * 10. Petrified
     * A petrified creature is transformed into a solid inanimate substance.
     */
    PETRIFIED(
        id = 10,
        displayName = "Petrified",
        description = "You are transformed into stone",
        iconResource = "ic_condition_petrified"
    ),

    /**
     * 11. Poisoned
     * A poisoned creature has disadvantage on attack rolls and ability checks.
     */
    POISONED(
        id = 11,
        displayName = "Poisoned",
        description = "You are poisoned",
        iconResource = "ic_condition_poisoned"
    ),

    /**
     * 12. Prone
     * A prone creature's only movement option is to crawl.
     */
    PRONE(
        id = 12,
        displayName = "Prone",
        description = "You are prone",
        iconResource = "ic_condition_prone"
    ),

    /**
     * 13. Restrained
     * A restrained creature's speed becomes 0.
     */
    RESTRAINED(
        id = 13,
        displayName = "Restrained",
        description = "You are restrained",
        iconResource = "ic_condition_restrained"
    ),

    /**
     * 14. Stunned
     * A stunned creature is incapacitated, can't move, and can speak only falteringly.
     */
    STUNNED(
        id = 14,
        displayName = "Stunned",
        description = "You are stunned",
        iconResource = "ic_condition_stunned"
    ),

    /**
     * 15. Unconscious
     * An unconscious creature is incapacitated, can't move or speak, and is unaware.
     */
    UNCONSCIOUS(
        id = 15,
        displayName = "Unconscious",
        description = "You are unconscious",
        iconResource = "ic_condition_unconscious"
    );

    /**
     * Companion object for utility functions
     */
    companion object {
        /**
         * Get all conditions as a list
         * Used for displaying in the context menu
         *
         * @return List of all conditions in order
         */
        fun getAllConditions(): List<ConditionType> = values().toList()

        /**
         * Find condition by ID
         * Used when loading from database
         *
         * @param id The database ID
         * @return The matching condition or null
         */
        fun fromId(id: Long): ConditionType? {
            return values().find { it.id == id }
        }

        /**
         * Find condition by display name (case-insensitive)
         * Used for parsing saved data or imports
         *
         * @param displayName The display name to search for
         * @return The matching condition or null
         */
        fun fromDisplayName(displayName: String): ConditionType? {
            return values().find {
                it.displayName.equals(displayName, ignoreCase = true)
            }
        }

        /**
         * Get conditions that typically incapacitate
         * Useful for UI hints or validation
         *
         * @return List of incapacitating conditions
         */
        fun getIncapacitatingConditions(): List<ConditionType> {
            return listOf(
                INCAPACITATED,
                PARALYZED,
                PETRIFIED,
                STUNNED,
                UNCONSCIOUS
            )
        }

        /**
         * Get conditions that affect movement
         * Useful for UI organization
         *
         * @return List of movement-affecting conditions
         */
        fun getMovementConditions(): List<ConditionType> {
            return listOf(
                GRAPPLED,
                PARALYZED,
                PETRIFIED,
                PRONE,
                RESTRAINED,
                STUNNED,
                UNCONSCIOUS
            )
        }
    }
}
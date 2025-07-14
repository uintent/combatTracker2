// File: Condition.kt
package com.example.combattracker.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.combattracker.data.model.ConditionType

/**
 * Condition Entity - Represents a D&D condition template
 *
 * Purpose:
 * - Stores the master list of available conditions
 * - Pre-populated with 15 standard D&D conditions
 * - These are templates that get applied to actors as ActorCondition instances
 *
 * Requirements Reference:
 * From section 3.5.1: Pre-built Condition Library
 * - 15 D&D Conditions (cosmetic only, no functional impact)
 * From section 3.5.2: Condition Properties
 * - Name: Required (from pre-built list)
 * - Icon: Custom XML icons bundled as app resources
 *
 * Note: This table is pre-populated on first app launch and rarely changes
 */
@Entity(tableName = "conditions")
data class Condition(
    /**
     * Unique identifier matching ConditionType enum
     * Not auto-generated - uses specific IDs (1-15)
     * This ensures consistency with the enum
     */
    @PrimaryKey
    val id: Long,

    /**
     * Display name of the condition
     * Examples: "Blinded", "Charmed", "Stunned"
     * Must match ConditionType enum names
     */
    val name: String,

    /**
     * Brief description of the condition
     * Shown in tooltips or help text
     * From D&D rules but simplified
     */
    val description: String,

    /**
     * Resource name for the condition icon
     * References drawable resources
     * Example: "ic_condition_blinded"
     */
    val iconResource: String,

    /**
     * Display order in lists
     * Allows custom ordering if needed
     * Default matches ID order
     */
    val displayOrder: Int = id.toInt(),

    /**
     * Whether this condition is visible in UI
     * For future use - hiding conditions
     * All default conditions are enabled
     */
    val isEnabled: Boolean = true
) {
    /**
     * Get the corresponding ConditionType enum
     *
     * @return The matching enum value or null if not found
     */
    fun toConditionType(): ConditionType? {
        return ConditionType.fromId(id)
    }

    /**
     * Check if this matches a specific condition type
     *
     * @param type The condition type to check
     * @return True if this condition matches the type
     */
    fun isType(type: ConditionType): Boolean {
        return id == type.id
    }

    companion object {
        /**
         * Create a Condition entity from a ConditionType enum
         * Used for pre-populating the database
         *
         * @param type The condition type enum
         * @return A new Condition entity
         */
        fun fromConditionType(type: ConditionType): Condition {
            return Condition(
                id = type.id,
                name = type.displayName,
                description = type.description,
                iconResource = type.iconResource,
                displayOrder = type.ordinal + 1
            )
        }

        /**
         * Get all pre-built conditions for initial database population
         * Called from Application class on first launch
         *
         * @return List of all 15 D&D conditions
         */
        fun getAllPrebuiltConditions(): List<Condition> {
            return ConditionType.values().map { type ->
                fromConditionType(type)
            }
        }

        /**
         * Create the "Blinded" condition
         * Example of creating individual conditions
         */
        fun createBlinded(): Condition {
            return Condition(
                id = 1,
                name = "Blinded",
                description = "You can't see and automatically fail ability checks that require sight",
                iconResource = "ic_condition_blinded"
            )
        }

        /**
         * Validate that a condition matches expected data
         * Used for data integrity checks
         *
         * @param condition The condition to validate
         * @return True if valid
         */
        fun isValid(condition: Condition): Boolean {
            val expectedType = ConditionType.fromId(condition.id)
            return expectedType != null &&
                    condition.name == expectedType.displayName &&
                    condition.iconResource.isNotBlank()
        }
    }
}

/**
 * Data class for condition statistics
 * Used for analytics or debugging
 */
data class ConditionStats(
    val conditionId: Long,
    val conditionName: String,
    val timesApplied: Int,
    val averageDuration: Double
)
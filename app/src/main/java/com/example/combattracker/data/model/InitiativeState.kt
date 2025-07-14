// File: InitiativeState.kt
package com.example.combattracker.data.model

/**
 * InitiativeState - Data class representing the complete state of initiative for an actor
 *
 * Purpose:
 * - Encapsulates all initiative-related information for a combat actor
 * - Used to track initiative values, turn status, and tie-breaking information
 * - Supports both integer (players) and decimal (NPCs) initiative values
 *
 * This is a model class used in the UI layer, not stored directly in the database.
 * It combines data from EncounterActor with computed values for display.
 */
data class InitiativeState(
    /**
     * The actor's ID in the encounter
     * References EncounterActor.id, not Actor.id
     */
    val encounterActorId: Long,

    /**
     * Current initiative value
     * Can be null if not yet rolled
     * Uses Double to support NPC decimal tie-breaking
     */
    val initiative: Double? = null,

    /**
     * Initiative modifier from the actor
     * Added to d20 roll when calculating initiative
     */
    val initiativeModifier: Int = 0,

    /**
     * Whether this actor has taken their turn in the current round
     * Reset to false at the start of each round
     */
    val hasTakenTurn: Boolean = false,

    /**
     * Manual sort order for breaking ties among players
     * Only used when multiple players have the same initiative
     * Lower values appear first (left) in the tracker
     */
    val tieBreakOrder: Int = 0,

    /**
     * Whether this actor is currently active (their turn)
     * Only one actor should be active at a time
     */
    val isActive: Boolean = false,

    /**
     * Whether this actor is tied with another actor
     * Used to show tie-breaking UI for players
     */
    val isTied: Boolean = false
) {
    /**
     * Check if initiative has been rolled
     * @return True if initiative is not null
     */
    fun hasInitiative(): Boolean = initiative != null

    /**
     * Check if this is a player initiative (integer value)
     * Players don't use decimal tie-breaking
     * @return True if initiative exists and has no decimal part
     */
    fun isPlayerInitiative(): Boolean {
        return initiative?.let { it == it.toInt().toDouble() } ?: false
    }

    /**
     * Get initiative as display string
     * Shows integers for players, decimals for NPCs
     * @return Formatted initiative or "--" if not rolled
     */
    fun getInitiativeDisplay(): String {
        return when {
            initiative == null -> "--"
            isPlayerInitiative() -> initiative.toInt().toString()
            else -> String.format("%.2f", initiative)
        }
    }

    /**
     * Get sort value for ordering actors
     * Higher values appear first (left) in tracker
     * Null initiatives sort to the end
     *
     * @return Sort value (higher = earlier in order)
     */
    fun getSortValue(): Double {
        return initiative ?: -999.0
    }

    /**
     * Compare two InitiativeState objects for sorting
     * Considers initiative value and tie-break order
     */
    companion object {
        /**
         * Comparator for sorting actors by initiative
         *
         * Sort order:
         * 1. Higher initiative first
         * 2. For ties: Lower tieBreakOrder first
         * 3. Null initiatives last
         */
        val initiativeComparator = Comparator<InitiativeState> { a, b ->
            when {
                // Both have no initiative - sort by ID for stability
                a.initiative == null && b.initiative == null -> {
                    a.encounterActorId.compareTo(b.encounterActorId)
                }
                // One has no initiative - it goes last
                a.initiative == null -> 1
                b.initiative == null -> -1
                // Different initiatives - higher first
                a.initiative != b.initiative -> {
                    b.initiative.compareTo(a.initiative)
                }
                // Same initiative - use tie-break order
                else -> {
                    a.tieBreakOrder.compareTo(b.tieBreakOrder)
                }
            }
        }

        /**
         * Create a new state with rolled initiative
         * Convenience function for initiative rolling
         *
         * @param base The current state
         * @param rolledValue The new initiative value
         * @return Updated state with new initiative
         */
        fun withInitiative(base: InitiativeState, rolledValue: Double): InitiativeState {
            return base.copy(initiative = rolledValue)
        }

        /**
         * Create a new state for next turn
         * Marks current as having taken turn and inactive
         *
         * @param current The current state
         * @return Updated state after turn ends
         */
        fun afterTurn(current: InitiativeState): InitiativeState {
            return current.copy(
                hasTakenTurn = true,
                isActive = false
            )
        }

        /**
         * Reset for new round
         * Clears turn status but keeps initiative
         *
         * @param current The current state
         * @return Reset state for new round
         */
        fun newRound(current: InitiativeState): InitiativeState {
            return current.copy(
                hasTakenTurn = false,
                isActive = false
            )
        }
    }
}

/**
 * Extension function to check if this actor can move in tie-breaking
 * Only players with tied initiatives can be manually reordered
 *
 * Requirements: "Move Left/Move Right buttons are only for resolving ties among Player Characters"
 */
fun InitiativeState.canManuallyReorder(): Boolean {
    return isPlayerInitiative() && isTied
}
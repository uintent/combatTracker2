// File: InitiativeCalculator.kt
package com.example.combattracker.utils

import com.example.combattracker.data.database.entities.EncounterActor
import com.example.combattracker.data.model.ActorCategory
import com.example.combattracker.data.model.InitiativeState
import timber.log.Timber
import kotlin.random.Random

/**
 * InitiativeCalculator - Handles all initiative-related calculations and sorting
 *
 * Purpose:
 * - Centralizes initiative rolling logic
 * - Implements NPC decimal tie-breaking system
 * - Provides consistent sorting algorithms
 * - Manages tie resolution for players
 *
 * Requirements Reference:
 * From section 3.3.3: Initiative System
 * - Rolling Options: Roll for all, roll for NPCs only, manual setting
 * - Initiative Calculation: (d20 roll + modifier) + (random decimal between 0.0 and -0.2) for NPCs
 * - Tie Breaking System:
 *   - NPCs: Automatic tie-breaking with random decimal values
 *   - Player Characters: Initiative remains integers; manual re-sorting required
 *
 * Thread Safety: All methods are pure functions and thread-safe
 */
object InitiativeCalculator {

    /**
     * Random instance for consistent behavior
     * Can be seeded for testing
     */
    private var random: Random = Random.Default

    /**
     * D20 die sides
     */
    private const val D20_SIDES = 20

    /**
     * NPC tie-breaking decimal range
     * Requirements specify 0.0000 to -0.1999 (negative to keep below integer)
     */
    private const val NPC_DECIMAL_MIN = -0.1999
    private const val NPC_DECIMAL_MAX = 0.0

    /**
     * Decimal places for NPC initiative
     * 4 decimal places provides sufficient uniqueness
     */
    private const val DECIMAL_PLACES = 4

    // ========== Initiative Rolling ==========

    /**
     * Roll initiative for a single actor
     *
     * Algorithm:
     * 1. Roll 1d20
     * 2. Add initiative modifier
     * 3. If NPC: Subtract small random decimal for tie-breaking
     *
     * @param modifier Initiative bonus/penalty (-99 to 99)
     * @param isNpc True for NPCs, Monsters, and Others
     * @return Initiative value as Double
     */
    fun rollInitiative(modifier: Int, isNpc: Boolean): Double {
        // Roll d20 (1-20)
        val roll = random.nextInt(1, D20_SIDES + 1)

        // Calculate base initiative
        val baseInitiative = roll + modifier

        // Apply NPC decimal if needed
        return if (isNpc) {
            // Generate random decimal in range [NPC_DECIMAL_MIN, NPC_DECIMAL_MAX]
            val decimal = random.nextDouble(NPC_DECIMAL_MIN, NPC_DECIMAL_MAX)
            // Round to specified decimal places
            val roundedDecimal = "%.${DECIMAL_PLACES}f".format(decimal).toDouble()
            baseInitiative + roundedDecimal
        } else {
            // Players get integer initiative
            baseInitiative.toDouble()
        }
    }

    /**
     * Roll initiative for multiple actors
     *
     * @param actors List of actors to roll for
     * @param rollType Type of roll (ALL, NPC_ONLY, or specific actors)
     * @param actorCategories Map of actor ID to category (for determining NPC status)
     * @return Map of actor ID to rolled initiative
     */
    fun rollInitiativeForActors(
        actors: List<EncounterActor>,
        rollType: InitiativeRollType,
        actorCategories: Map<Long, ActorCategory>
    ): Map<Long, Double> {
        val results = mutableMapOf<Long, Double>()

        actors.forEach { actor ->
            val shouldRoll = when (rollType) {
                InitiativeRollType.ALL -> true
                InitiativeRollType.NPC_ONLY -> {
                    val category = actorCategories[actor.baseActorId] ?: ActorCategory.MONSTER
                    category.isNpc()
                }
                is InitiativeRollType.SPECIFIC -> actor.id in rollType.actorIds
            }

            if (shouldRoll) {
                val category = actorCategories[actor.baseActorId] ?: ActorCategory.MONSTER
                val isNpc = category.isNpc()
                val initiative = rollInitiative(actor.initiativeModifier, isNpc)
                results[actor.id] = initiative

                Timber.d("Rolled initiative for ${actor.displayName}: $initiative (d20 + ${actor.initiativeModifier})")
            }
        }

        return results
    }

    // ========== Sorting and Tie Detection ==========

    /**
     * Sort actors by initiative with proper tie handling
     *
     * Sort order:
     * 1. Actors with initiative before those without
     * 2. Higher initiative values first
     * 3. For ties: Use tieBreakOrder (for players)
     * 4. Actors without initiative at end, sorted by added order
     *
     * @param actors List of actors to sort
     * @return Sorted list (highest initiative first)
     */
    fun sortByInitiative(actors: List<EncounterActor>): List<EncounterActor> {
        return actors.sortedWith(compareByDescending<EncounterActor> { it.initiative ?: Double.NEGATIVE_INFINITY }
            .thenBy { it.tieBreakOrder }
            .thenBy { it.addedOrder })
    }

    /**
     * Sort InitiativeState objects for UI display
     *
     * @param states List of initiative states
     * @return Sorted list
     */
    fun sortInitiativeStates(states: List<InitiativeState>): List<InitiativeState> {
        return states.sortedWith(InitiativeState.initiativeComparator)
    }

    /**
     * Detect tied player characters
     * NPCs never tie due to decimal system
     *
     * @param actors List of actors with initiative
     * @return Set of actor IDs that are tied with another player
     */
    fun detectTiedPlayers(actors: List<EncounterActor>): Set<Long> {
        val tiedActorIds = mutableSetOf<Long>()

        // Group by integer initiative (players only)
        val playersByInitiative = actors
            .filter { it.initiative != null && isPlayerInitiative(it.initiative) }
            .groupBy { it.initiative!!.toInt() }

        // Find groups with multiple players
        playersByInitiative.forEach { (_, group) ->
            if (group.size > 1) {
                group.forEach { actor ->
                    tiedActorIds.add(actor.id)
                }
            }
        }

        return tiedActorIds
    }

    /**
     * Check if an initiative value is a player initiative (integer)
     *
     * @param initiative The initiative value
     * @return True if this is a player (integer) initiative
     */
    fun isPlayerInitiative(initiative: Double): Boolean {
        return initiative == initiative.toInt().toDouble()
    }

    // ========== Tie Resolution ==========

    /**
     * Resolve ties among player characters by adjusting tie-break order
     *
     * @param actors Current list of actors
     * @param actorId Actor to move
     * @param direction Direction to move (LEFT = higher priority, RIGHT = lower)
     * @return Updated list with new tie-break orders
     */
    fun resolveTie(
        actors: List<EncounterActor>,
        actorId: Long,
        direction: TieBreakDirection
    ): List<EncounterActor>? {
        val actor = actors.find { it.id == actorId } ?: return null
        val initiative = actor.initiative ?: return null

        // Must be a player with a tie
        if (!isPlayerInitiative(initiative)) {
            Timber.w("Cannot manually reorder NPC initiative")
            return null
        }

        // Find all actors with same initiative
        val sameInitiative = actors
            .filter { it.initiative == initiative }
            .sortedBy { it.tieBreakOrder }

        if (sameInitiative.size <= 1) {
            Timber.w("No tie to resolve")
            return null
        }

        val currentIndex = sameInitiative.indexOfFirst { it.id == actorId }
        if (currentIndex == -1) return null

        // Calculate new index
        val newIndex = when (direction) {
            TieBreakDirection.LEFT -> (currentIndex - 1).coerceAtLeast(0)
            TieBreakDirection.RIGHT -> (currentIndex + 1).coerceAtMost(sameInitiative.size - 1)
        }

        if (newIndex == currentIndex) {
            Timber.d("Actor already at ${direction.name} boundary")
            return null
        }

        // Reorder the tied actors
        val reordered = sameInitiative.toMutableList()
        reordered.removeAt(currentIndex)
        reordered.add(newIndex, actor)

        // Update tie-break orders
        val updates = mutableMapOf<Long, Int>()
        reordered.forEachIndexed { index, tiedActor ->
            updates[tiedActor.id] = index
        }

        // Apply updates to all actors
        return actors.map { a ->
            updates[a.id]?.let { newOrder ->
                a.copy(tieBreakOrder = newOrder)
            } ?: a
        }
    }

    // ========== Utility Methods ==========

    /**
     * Get a descriptive string for an initiative roll
     *
     * @param roll The d20 roll result
     * @param modifier The initiative modifier
     * @param total The total initiative
     * @param isNpc Whether this is an NPC
     * @return Human-readable description
     */
    fun getInitiativeRollDescription(
        roll: Int,
        modifier: Int,
        total: Double,
        isNpc: Boolean
    ): String {
        val modifierStr = when {
            modifier > 0 -> "+$modifier"
            modifier < 0 -> "$modifier"
            else -> ""
        }

        return if (isNpc && !isPlayerInitiative(total)) {
            val decimal = total - total.toInt()
            "d20($roll)$modifierStr = ${total.toInt()} (${String.format("%.4f", decimal)} for tie-breaking)"
        } else {
            "d20($roll)$modifierStr = ${total.toInt()}"
        }
    }

    /**
     * Calculate average initiative for a modifier
     * Useful for sorting actors by expected speed
     *
     * @param modifier Initiative modifier
     * @return Average expected initiative
     */
    fun calculateAverageInitiative(modifier: Int): Double {
        // Average d20 roll is 10.5
        return 10.5 + modifier
    }

    /**
     * Set a custom random instance (for testing)
     *
     * @param customRandom Random instance to use
     */
    fun setRandom(customRandom: Random) {
        random = customRandom
    }

    /**
     * Reset to default random instance
     */
    fun resetRandom() {
        random = Random.Default
    }
}

// ========== Supporting Types ==========

/**
 * Types of initiative rolls
 */
sealed class InitiativeRollType {
    /** Roll for all actors */
    object ALL : InitiativeRollType()

    /** Roll only for NPCs (NPCs, Monsters, Others) */
    object NPC_ONLY : InitiativeRollType()

    /** Roll for specific actors */
    data class SPECIFIC(val actorIds: Set<Long>) : InitiativeRollType()
}

/**
 * Direction for tie-breaking movement
 */
enum class TieBreakDirection {
    /** Move left (higher priority, earlier in turn order) */
    LEFT,

    /** Move right (lower priority, later in turn order) */
    RIGHT
}

/**
 * Format initiative value for display
 *
 * @param initiative The initiative value
 * @param showDecimals Whether to show decimal places (for NPCs)
 * @return Formatted string
 */
fun formatInitiative(initiative: Double, showDecimals: Boolean = false): String {
    return if (showDecimals || !InitiativeCalculator.isPlayerInitiative(initiative)) {
        String.format("%.2f", initiative)
    } else {
        initiative.toInt().toString()
    }
}
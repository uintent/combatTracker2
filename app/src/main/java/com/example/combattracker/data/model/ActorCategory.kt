// File: ActorCategory.kt
package com.example.combattracker.data.model

/**
 * ActorCategory - Enum representing the four types of actors in the combat tracker
 *
 * Purpose:
 * - Categorizes actors for organization and filtering in the actor library
 * - Used purely for organizational purposes with no behavioral differences
 * - Helps DMs quickly find and manage different types of participants
 *
 * Requirements Reference:
 * From section 3.1.1: "Category: One of four types - Players, NPCs, Monsters, Others"
 * From section 3.1.3: "Categories: Organizational tool only, no behavioral differences"
 */
enum class ActorCategory(
    /**
     * Display name shown in the UI
     * This is what users will see in filters, lists, and forms
     */
    val displayName: String,

    /**
     * Optional icon resource name for future UI enhancements
     * Can be used to show icons next to category names
     */
    val iconResource: String? = null
) {
    /**
     * PLAYER - Represents player characters (PCs)
     *
     * Typically used for:
     * - Main party members
     * - Recurring player characters
     * - Characters controlled by players at the table
     *
     * Initiative behavior: No automatic tie-breaking (players resolve ties manually)
     */
    PLAYER("Players", "ic_category_player"),

    /**
     * NPC - Non-Player Characters
     *
     * Typically used for:
     * - Friendly NPCs
     * - Neutral characters
     * - Named characters that aren't monsters
     * - Allies in combat
     *
     * Initiative behavior: Automatic tie-breaking with decimal values
     */
    NPC("NPCs", "ic_category_npc"),

    /**
     * MONSTER - Hostile creatures and enemies
     *
     * Typically used for:
     * - Combat enemies
     * - Hostile creatures
     * - Dungeon encounters
     * - Boss fights
     *
     * Initiative behavior: Automatic tie-breaking with decimal values
     */
    MONSTER("Monsters", "ic_category_monster"),

    /**
     * OTHER - Catch-all category
     *
     * Typically used for:
     * - Environmental hazards
     * - Traps
     * - Special effects
     * - Summoned creatures
     * - Anything that doesn't fit other categories
     *
     * Initiative behavior: Automatic tie-breaking with decimal values
     */
    OTHER("Others", "ic_category_other");

    /**
     * Companion object for utility functions
     */
    companion object {
        /**
         * Get all categories as a list
         * Useful for populating spinners/filters
         *
         * @return List of all actor categories
         */
        fun getAllCategories(): List<ActorCategory> = values().toList()

        /**
         * Find category by display name (case-insensitive)
         * Useful when parsing user input or saved data
         *
         * @param displayName The display name to search for
         * @return The matching category or null if not found
         */
        fun fromDisplayName(displayName: String): ActorCategory? {
            return values().find {
                it.displayName.equals(displayName, ignoreCase = true)
            }
        }

        /**
         * Get all NPC-type categories (non-player categories)
         * Used for determining which actors get automatic tie-breaking
         *
         * From requirements: "NPCs (NPCs, Monsters, Others)" get decimal tie-breaking
         *
         * @return List of NPC-type categories
         */
        fun getNpcCategories(): List<ActorCategory> {
            return listOf(NPC, MONSTER, OTHER)
        }

        /**
         * Check if a category is considered an NPC type
         * Used for initiative rolling logic
         *
         * @param category The category to check
         * @return True if the category gets NPC treatment (automatic tie-breaking)
         */
        fun isNpcType(category: ActorCategory): Boolean {
            return category != PLAYER
        }

        /**
         * Get default category for new actors
         *
         * @return The default category (MONSTER)
         */
        fun getDefault(): ActorCategory = MONSTER
    }

    /**
     * Check if this category should use NPC initiative rules
     * (automatic tie-breaking with decimal values)
     *
     * @return True if this category uses NPC rules
     */
    fun isNpc(): Boolean = this != PLAYER
}
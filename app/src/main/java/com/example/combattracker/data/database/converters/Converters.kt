// File: Converters.kt
package com.example.combattracker.data.database.converters

import androidx.room.TypeConverter
import com.example.combattracker.data.model.ActorCategory
import com.example.combattracker.data.model.ConditionType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.util.*

/**
 * Converters - Room type converters for custom data types
 *
 * Purpose:
 * - Converts between custom types and types that Room can persist
 * - Handles enum conversions, lists, and complex objects
 * - Ensures data integrity when storing/retrieving from database
 *
 * Room can only store primitive types and strings in the database.
 * These converters allow us to use more complex types in our entities.
 */
class Converters {

    // ========== Date Converters ==========

    /**
     * Convert Date to Long timestamp for storage
     *
     * @param date The date to convert
     * @return Milliseconds since epoch or null
     */
    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }

    /**
     * Convert Long timestamp back to Date
     *
     * @param timestamp Milliseconds since epoch
     * @return Date object or null
     */
    @TypeConverter
    fun toDate(timestamp: Long?): Date? {
        return timestamp?.let { Date(it) }
    }

    // ========== ActorCategory Enum Converters ==========

    /**
     * Convert ActorCategory enum to String for storage
     *
     * @param category The category enum
     * @return String name of the enum
     */
    @TypeConverter
    fun fromActorCategory(category: ActorCategory?): String? {
        return category?.name
    }

    /**
     * Convert String back to ActorCategory enum
     * Includes error handling for invalid data
     *
     * @param categoryName The stored category name
     * @return ActorCategory enum or null
     */
    @TypeConverter
    fun toActorCategory(categoryName: String?): ActorCategory? {
        return categoryName?.let {
            try {
                ActorCategory.valueOf(it)
            } catch (e: IllegalArgumentException) {
                Timber.w("Invalid ActorCategory in database: $it, defaulting to MONSTER")
                ActorCategory.MONSTER // Safe default
            }
        }
    }

    // ========== ConditionType Enum Converters ==========

    /**
     * Convert ConditionType enum to Long ID for storage
     * Using ID instead of name for consistency with Condition entity
     *
     * @param conditionType The condition type enum
     * @return The condition ID
     */
    @TypeConverter
    fun fromConditionType(conditionType: ConditionType?): Long? {
        return conditionType?.id
    }

    /**
     * Convert Long ID back to ConditionType enum
     *
     * @param conditionId The stored condition ID
     * @return ConditionType enum or null
     */
    @TypeConverter
    fun toConditionType(conditionId: Long?): ConditionType? {
        return conditionId?.let { ConditionType.fromId(it) }
    }

    // ========== List<String> Converters ==========

    /**
     * Convert List<String> to JSON string for storage
     * Useful for storing lists of tags, notes, etc.
     *
     * @param list The list to convert
     * @return JSON string representation
     */
    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.let { Gson().toJson(it) }
    }

    /**
     * Convert JSON string back to List<String>
     *
     * @param json The stored JSON string
     * @return List of strings or empty list
     */
    @TypeConverter
    fun toStringList(json: String?): List<String> {
        return if (json.isNullOrEmpty()) {
            emptyList()
        } else {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                Gson().fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse string list from JSON: $json")
                emptyList()
            }
        }
    }

    // ========== List<Long> Converters ==========

    /**
     * Convert List<Long> to comma-separated string for storage
     * More efficient than JSON for simple number lists
     *
     * @param list The list of IDs
     * @return Comma-separated string
     */
    @TypeConverter
    fun fromLongList(list: List<Long>?): String? {
        return list?.joinToString(",")
    }

    /**
     * Convert comma-separated string back to List<Long>
     *
     * @param csv The stored comma-separated values
     * @return List of longs or empty list
     */
    @TypeConverter
    fun toLongList(csv: String?): List<Long> {
        return if (csv.isNullOrEmpty()) {
            emptyList()
        } else {
            try {
                csv.split(",").mapNotNull { it.toLongOrNull() }
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse long list from CSV: $csv")
                emptyList()
            }
        }
    }

    // ========== Map<Long, Int> Converters ==========

    /**
     * Convert Map<Long, Int> to JSON for storage
     * Used for storing things like actor ID to instance count mappings
     *
     * @param map The map to convert
     * @return JSON string representation
     */
    @TypeConverter
    fun fromLongIntMap(map: Map<Long, Int>?): String? {
        return map?.let { Gson().toJson(it) }
    }

    /**
     * Convert JSON string back to Map<Long, Int>
     *
     * @param json The stored JSON string
     * @return Map or empty map
     */
    @TypeConverter
    fun toLongIntMap(json: String?): Map<Long, Int> {
        return if (json.isNullOrEmpty()) {
            emptyMap()
        } else {
            try {
                val type = object : TypeToken<Map<Long, Int>>() {}.type
                Gson().fromJson(json, type) ?: emptyMap()
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse map from JSON: $json")
                emptyMap()
            }
        }
    }

    // ========== Boolean List Converters (for future use) ==========

    /**
     * Convert List<Boolean> to bit string for efficient storage
     * Example: [true, false, true] -> "101"
     *
     * @param list The boolean list
     * @return Bit string representation
     */
    @TypeConverter
    fun fromBooleanList(list: List<Boolean>?): String? {
        return list?.joinToString("") { if (it) "1" else "0" }
    }

    /**
     * Convert bit string back to List<Boolean>
     *
     * @param bitString The stored bit string
     * @return List of booleans
     */
    @TypeConverter
    fun toBooleanList(bitString: String?): List<Boolean> {
        return bitString?.map { it == '1' } ?: emptyList()
    }

    // ========== Utility Methods ==========

    companion object {
        /**
         * Safely parse enum with fallback
         * Generic method for any enum type
         *
         * @param value String value to parse
         * @param enumClass The enum class
         * @param default Default value if parsing fails
         * @return Parsed enum or default
         */
        inline fun <reified T : Enum<T>> safeEnumValueOf(
            value: String?,
            default: T
        ): T {
            return value?.let {
                try {
                    enumValueOf<T>(it)
                } catch (e: IllegalArgumentException) {
                    Timber.w("Invalid enum value for ${T::class.simpleName}: $it")
                    default
                }
            } ?: default
        }
    }
}
// File: Constants.kt
package com.example.combattracker.utils

/**
 * Constants.kt - App-wide constants for the Combat Tracker
 *
 * Purpose:
 * - Centralizes all constant values used throughout the app
 * - Prevents magic numbers and strings
 * - Makes configuration changes easier
 * - Improves code maintainability
 *
 * Organization:
 * - Grouped by feature/usage area
 * - Documented with usage context
 * - Following naming conventions (UPPER_SNAKE_CASE)
 */
object Constants {

    // ========== App Configuration ==========

    /**
     * App preferences file name
     */
    const val PREFS_NAME = "combat_tracker_prefs"

    /**
     * Database name
     */
    const val DATABASE_NAME = "combat_tracker_database"

    /**
     * Current database version
     */
    const val DATABASE_VERSION = 1

    // ========== Activity Request Codes ==========

    object RequestCodes {
        const val PICK_IMAGE = 1001
        const val CREATE_ACTOR = 1002
        const val EDIT_ACTOR = 1003
        const val CREATE_ENCOUNTER = 1004
        const val LOAD_ENCOUNTER = 1005
        const val COMBAT_TRACKER = 1006
        const val SETTINGS = 1007
    }

    // ========== Intent Extras ==========

    object Extras {
        const val ACTOR_ID = "extra_actor_id"
        const val ENCOUNTER_ID = "extra_encounter_id"
        const val ENCOUNTER_NAME = "extra_encounter_name"
        const val START_COMBAT = "extra_start_combat"
        const val ACTOR_CATEGORY = "extra_actor_category"
        const val IS_EDIT_MODE = "extra_is_edit_mode"
    }

    // ========== Shared Preferences Keys ==========

    object PrefsKeys {
        const val BACKGROUND_IMAGE_PATH = "pref_background_image_path"
        const val LAST_ENCOUNTER_PREFIX = "pref_last_encounter_prefix"
        const val AUTO_ROLL_INITIATIVE = "pref_auto_roll_initiative"
        const val CONFIRM_DELETIONS = "pref_confirm_deletions"
        const val KEEP_SCREEN_ON = "pref_keep_screen_on"
        const val DEFAULT_SORT_ORDER = "pref_default_sort_order"
    }

    // ========== Default Values ==========

    object Defaults {
        const val ENCOUNTER_PREFIX = "ENCsave_"
        const val PORTRAIT_QUALITY = 85 // JPEG compression quality
        const val BACKGROUND_QUALITY = 90
        const val MAX_NAME_LENGTH = 100
        const val MIN_INITIATIVE_MODIFIER = -99
        const val MAX_INITIATIVE_MODIFIER = 99
        const val DEFAULT_ROUND_NUMBER = 1
    }

    // ========== UI Configuration ==========

    object UI {
        // Portrait dimensions in DP
        const val PORTRAIT_WIDTH_DP = 80
        const val PORTRAIT_HEIGHT_DP = 120

        // Active actor scaling
        const val ACTIVE_ACTOR_SCALE = 1.2f // 20% larger

        // Animation durations in milliseconds
        const val ANIMATION_DURATION_SHORT = 200L
        const val ANIMATION_DURATION_MEDIUM = 400L
        const val ANIMATION_DURATION_LONG = 600L

        // Debounce times
        const val CLICK_DEBOUNCE_TIME = 500L
        const val SEARCH_DEBOUNCE_TIME = 300L

        // Bottom sheet
        const val BOTTOM_SHEET_HEIGHT_RATIO = 0.45f // 45% of screen height
        const val BOTTOM_SHEET_WIDTH_RATIO = 0.9f // 90% of screen width

        // List limits
        const val RECENT_ACTORS_LIMIT = 10
        const val MAX_ACTORS_PER_ENCOUNTER = 50 // Reasonable limit
    }

    // ========== File Management ==========

    object Files {
        // Directory names
        const val DIR_PORTRAITS = "portraits"
        const val DIR_BACKGROUNDS = "backgrounds"
        const val DIR_TEMP = "temp"

        // File extensions
        const val EXT_JPEG = ".jpg"
        const val EXT_PNG = ".png"

        // Size limits
        const val MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
        const val MAX_PORTRAIT_WIDTH = 600
        const val MAX_PORTRAIT_HEIGHT = 900
        const val MAX_BACKGROUND_WIDTH = 1920
    }

    // ========== Combat Rules ==========

    object Combat {
        // D20 configuration
        const val D20_MIN = 1
        const val D20_MAX = 20

        // NPC tie-breaking
        const val NPC_DECIMAL_MIN = -0.1999
        const val NPC_DECIMAL_MAX = 0.0
        const val NPC_DECIMAL_PLACES = 4

        // Turn management
        const val NO_ACTIVE_ACTOR = -1L
        const val FIRST_ROUND = 1

        // Condition durations
        const val PERMANENT_CONDITION = -1
        const val MAX_CONDITION_DURATION = 999
    }

    // ========== Validation ==========

    object Validation {
        // Name validation
        const val MIN_NAME_LENGTH = 1
        const val MAX_NAME_LENGTH = 100
        val VALID_NAME_REGEX = Regex("^[\\p{L}\\p{N}\\s'\\-.,!?]+$") // Letters, numbers, spaces, basic punctuation

        // Number validation
        const val MIN_MODIFIER = -99
        const val MAX_MODIFIER = 99

        // Encounter limits
        const val MIN_ACTORS_PER_ENCOUNTER = 1
        const val MAX_ACTORS_PER_ENCOUNTER = 50
    }

    // ========== Error Messages ==========

    object Errors {
        const val GENERIC_ERROR = "An unexpected error occurred"
        const val ACTOR_NAME_EMPTY = "Actor name cannot be empty"
        const val ACTOR_NAME_TOO_LONG = "Actor name is too long (max %d characters)"
        const val ACTOR_NAME_TAKEN = "An actor with this name already exists"
        const val ACTOR_IN_USE = "Cannot delete actor that is used in encounters"
        const val ENCOUNTER_NO_ACTORS = "An encounter must have at least one actor"
        const val ENCOUNTER_NOT_FOUND = "Encounter not found"
        const val INVALID_INITIATIVE = "Invalid initiative value"
        const val CONDITION_DURATION_REQUIRED = "Please specify a duration or select 'Permanent'"
        const val IMAGE_LOAD_FAILED = "Failed to load image"
        const val IMAGE_TOO_LARGE = "Image file is too large (max 10MB)"
    }

    // ========== Success Messages ==========

    object Success {
        const val ACTOR_CREATED = "Actor created successfully"
        const val ACTOR_UPDATED = "Actor updated successfully"
        const val ACTOR_DELETED = "Actor deleted"
        const val ENCOUNTER_CREATED = "Encounter created"
        const val ENCOUNTER_SAVED = "Encounter saved"
        const val ENCOUNTER_LOADED = "Encounter loaded"
        const val ENCOUNTER_DELETED = "Encounter deleted"
        const val CONDITION_APPLIED = "Condition applied"
        const val CONDITION_REMOVED = "Condition removed"
    }

    // ========== Dialog Configuration ==========

    object Dialogs {
        // Delete confirmation
        const val DELETE_ACTOR_TITLE = "Delete Actor?"
        const val DELETE_ACTOR_MESSAGE = "Are you sure you want to delete %s?"
        const val DELETE_ENCOUNTER_TITLE = "Delete Encounter?"
        const val DELETE_ENCOUNTER_MESSAGE = "Are you sure you want to delete this encounter?"

        // End encounter
        const val END_ENCOUNTER_TITLE = "End Encounter?"
        const val END_ENCOUNTER_MESSAGE = "Do you want to save before ending?"
        const val END_ENCOUNTER_SAVE = "Save & End"
        const val END_ENCOUNTER_DISCARD = "Discard & End"

        // Reset confirmations
        const val RESET_ACTORS_TITLE = "Reset All Actors?"
        const val RESET_ACTORS_MESSAGE = "This will delete all actors AND all saved encounters. This cannot be undone."
        const val RESET_ENCOUNTERS_TITLE = "Reset All Encounters?"
        const val RESET_ENCOUNTERS_MESSAGE = "This will delete all saved encounters. This cannot be undone."

        // Common button text
        const val BUTTON_OK = "OK"
        const val BUTTON_CANCEL = "Cancel"
        const val BUTTON_DELETE = "Delete"
        const val BUTTON_SAVE = "Save"
        const val BUTTON_DISCARD = "Discard"
    }

    // ========== Accessibility ==========

    object Accessibility {
        const val ACTIVE_ACTOR_DESC = "%s is currently taking their turn"
        const val COMPLETED_TURN_DESC = "%s has completed their turn"
        const val MISSING_INITIATIVE_DESC = "%s has not rolled initiative"
        const val CONDITION_ACTIVE_DESC = "%s has condition: %s"
        const val PORTRAIT_DESC = "Portrait of %s"
        const val ROLL_INITIATIVE_DESC = "Roll initiative for %s"
        const val NEXT_TURN_DESC = "Advance to next turn"
        const val PREVIOUS_TURN_DESC = "Go back to previous turn"
    }

    // ========== Fragment Tags ==========

    object FragmentTags {
        const val ACTOR_CONTEXT_MENU = "fragment_actor_context_menu"
        const val CONDITION_PICKER = "fragment_condition_picker"
        const val SAVE_ENCOUNTER_DIALOG = "fragment_save_encounter"
    }

    // ========== Analytics Events (for future use) ==========

    object Analytics {
        // Event names
        const val ACTOR_CREATED = "actor_created"
        const val ENCOUNTER_STARTED = "encounter_started"
        const val COMBAT_COMPLETED = "combat_completed"
        const val INITIATIVE_ROLLED = "initiative_rolled"
        const val CONDITION_APPLIED = "condition_applied"

        // Event parameters
        const val PARAM_CATEGORY = "category"
        const val PARAM_ACTOR_COUNT = "actor_count"
        const val PARAM_ROUND_COUNT = "round_count"
        const val PARAM_DURATION = "duration_seconds"
    }

    // ========== Date Formats ==========

    object DateFormats {
        const val ENCOUNTER_AUTO_NAME = "yyyyMMdd_HHmmss"
        const val DISPLAY_DATE = "MMM d, yyyy"
        const val DISPLAY_TIME = "h:mm a"
        const val DISPLAY_DATETIME = "MMM d, h:mm a"
        const val FILE_TIMESTAMP = "yyyyMMdd_HHmmss"
    }
}
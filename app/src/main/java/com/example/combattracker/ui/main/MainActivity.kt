// File: MainActivity.kt
// Location: app/src/main/java/com/example/combattracker/ui/main/MainActivity.kt

package com.example.combattracker.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import android.view.Window
import com.example.combattracker.CombatTrackerApplication
import com.example.combattracker.R
import com.example.combattracker.databinding.ActivityMainBinding
import com.example.combattracker.ui.actors.ActorLibraryActivity
import com.example.combattracker.ui.combat.CombatTrackerActivity
import com.example.combattracker.ui.encounter.EncounterCreateActivity
import com.example.combattracker.ui.encounter.EncounterManageActivity
import com.example.combattracker.ui.settings.SettingsActivity
import com.example.combattracker.utils.*
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlinx.coroutines.delay

/**
 * MainActivity - Main menu screen of the Combat Tracker app
 *
 * Purpose:
 * - Entry point of the application
 * - Provides navigation to all major features
 * - Shows a grid of action cards
 * - Handles background image display
 *
 * Requirements Reference:
 * From section 2.1: Main Screen
 * - Grid layout with large buttons/cards for:
 *   - Manage Encounters: Load or delete saved encounters
 *   - Create New Encounter: Set up new combat encounter
 *   - Manage Actors: Create, edit, delete actor library
 *   - Settings: App configuration and reset options
 *   - Close App: Exit application
 */
class MainActivity : AppCompatActivity() {

    /**
     * View binding for type-safe view access
     */
    private lateinit var binding: ActivityMainBinding

    private var isReturningFromCombat = false

    /**
     * ViewModel for main screen logic
     */
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(
            (application as CombatTrackerApplication).encounterRepository,
            (application as CombatTrackerApplication).actorRepository
        )
    }

    /**
     * Activity result launcher for settings
     * Checks if background was changed
     */
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Refresh background if settings changed it
        loadBackgroundImage()
    }

    // ========== Lifecycle Methods ==========

    override fun onCreate(savedInstanceState: Bundle?) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)

        // Initialize view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if we're returning from combat
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        isReturningFromCombat = prefs.getBoolean("returning_from_combat", false)
        if (isReturningFromCombat) {
            // Clear the flag
            prefs.edit().remove("returning_from_combat").apply()
        }

        // Setup UI components
        setupToolbar()
        setupMenuCards()
        loadBackgroundImage()
        observeViewModel()

        // Check for any pending tasks
        checkInitialState()

        Timber.d("MainActivity created")
    }

    override fun onResume() {
        super.onResume()

        Timber.d("MainActivity onResume called, isReturningFromCombat = $isReturningFromCombat")

        // Clear the flag after a delay
        lifecycleScope.launch {
            if (isReturningFromCombat) {
                delay(1000)
                isReturningFromCombat = false
                Timber.d("MainActivity: Cleared isReturningFromCombat flag")
            }
        }

        // When returning from combat, the Flow might not have updated yet
        // Add a small delay to allow the database Flow to emit the new state
        lifecycleScope.launch {
            delay(200) // Small delay to ensure Flow updates
            viewModel.refreshStatistics()
            viewModel.refreshActiveEncounter()
            viewModel.checkActiveEncounter()
        }
    }

    // ========== UI Setup ==========

    /**
     * Setup the toolbar
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.app_name)
            // No back button on main screen
            setDisplayHomeAsUpEnabled(false)
        }
    }

     /**
     * Setup the menu cards with click listeners
     */
    private fun setupMenuCards() {
        // Manage Encounters card
        binding.cardManageEncounters.setOnClickListener {
            navigateToManageEncounters()
        }

        // Create New Encounter card
        binding.cardCreateEncounter.setOnClickListener {
            navigateToCreateEncounter()
        }

        // Manage Actors card
        binding.cardManageActors.setOnClickListener {
            navigateToManageActors()
        }

        // Settings card
        binding.cardSettings.setOnClickListener {
            navigateToSettings()
        }

        // Close App card
        binding.cardCloseApp.setOnClickListener {
            confirmCloseApp()
        }

        // Add ripple effect to cards
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)

        listOf(
            binding.cardManageEncounters,
            binding.cardCreateEncounter,
            binding.cardManageActors,
            binding.cardSettings,
            binding.cardCloseApp
        ).forEach { card ->
            card.isClickable = true
            card.isFocusable = true
            if (typedValue.resourceId != 0) {
                card.foreground = ContextCompat.getDrawable(this, typedValue.resourceId)
            }
        }
    }

    /**
     * Load and display the background image
     */
    private fun loadBackgroundImage() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val backgroundPath = prefs.getString(Constants.PrefsKeys.BACKGROUND_IMAGE_PATH, null)

        if (backgroundPath != null) {
            binding.backgroundImageView.loadFromInternalStorage(backgroundPath)
            binding.backgroundImageView.visible()
            // Add dark overlay for better text readability
            binding.backgroundOverlay.visible()
        } else {
            // Use default black background
            binding.backgroundImageView.gone()
            binding.backgroundOverlay.gone()
        }
    }

    // ========== ViewModel Observation ==========

    /**
     * Observe ViewModel LiveData
     */
    private fun observeViewModel() {
        // Observe encounter count
        viewModel.encounterCount.observe(this) { count ->
            updateEncounterCardSubtitle(count)
        }

        // Observe actor count
        viewModel.actorCount.observe(this) { count ->
            updateActorCardSubtitle(count)
        }

        // Observe active encounter (if any)
        viewModel.activeEncounter.observe(this) { encounterData ->
            Timber.d("MainActivity: activeEncounter observer triggered with: $encounterData")
            if (encounterData != null && !isReturningFromCombat) {
                // encounterData is a Pair<Long, String>
                // encounterData.first is the ID (Long)
                // encounterData.second is the name (String)
                showActiveEncounterPrompt(encounterData.first, encounterData.second)
            }
        }
    }

    /**
     * Update encounter card subtitle with count
     */
    private fun updateEncounterCardSubtitle(count: Int) {
        binding.textManageEncountersSubtitle.text = when (count) {
            0 -> "No saved encounters"
            1 -> "1 saved encounter"
            else -> "$count saved encounters"
        }
    }

    /**
     * Update actor card subtitle with count
     */
    private fun updateActorCardSubtitle(count: Int) {
        binding.textManageActorsSubtitle.text = when (count) {
            0 -> "No actors in library"
            1 -> "1 actor in library"
            else -> "$count actors in library"
        }
    }

    // ========== Navigation ==========

    /**
     * Navigate to Manage Encounters screen
     */
    private fun navigateToManageEncounters() {
        startActivity<EncounterManageActivity>()
    }

    /**
     * Navigate to Create Encounter screen
     */
    private fun navigateToCreateEncounter() {
        // Check if there are any actors first
        if (viewModel.actorCount.value == 0) {
            showNoActorsDialog()
            return
        }

        startActivity<EncounterCreateActivity>()
    }

    /**
     * Navigate to Manage Actors screen
     */
    private fun navigateToManageActors() {
        startActivity<ActorLibraryActivity>()
    }

    /**
     * Navigate to Settings screen
     */
    private fun navigateToSettings() {
        settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
    }

    // ========== Dialogs ==========

    /**
     * Show dialog when trying to create encounter with no actors
     */
    private fun showNoActorsDialog() {
        AlertDialog.Builder(this)
            .setTitle("No Actors")
            .setMessage("You need at least one actor to create an encounter. Would you like to create one now?")
            .setPositiveButton("Create Actor") { _, _ ->
                navigateToManageActors()
            }
            .setNegativeButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    /**
     * Show prompt for active encounter
     */
    private fun showActiveEncounterPrompt(encounterId: Long, encounterName: String) {
        AlertDialog.Builder(this)
            .setTitle("Active Encounter")
            .setMessage("You have an active encounter: $encounterName. Would you like to continue?")
            .setPositiveButton("Continue") { _, _ ->
                navigateToCombatTracker(encounterId)
            }
            .setNegativeButton("Ignore", null)
            .show()
    }

    /**
     * Confirm app closure
     */
    private fun confirmCloseApp() {
        AlertDialog.Builder(this)
            .setTitle("Close App?")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Close") { _, _ ->
                finish()
            }
            .setNegativeButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    // ========== Helper Methods ==========

    /**
     * Check initial state on app launch
     */
    private fun checkInitialState() {
        lifecycleScope.launch {
            // Clean up any orphaned images
            val cleaned = viewModel.cleanupOrphanedImages()
            if (cleaned > 0) {
                Timber.d("Cleaned up $cleaned orphaned images")
            }

            // Check for database integrity
            viewModel.verifyDatabaseIntegrity()
        }
    }

    /**
     * Handle card hover/focus for better UX
     */
    private fun setupCardElevation(card: MaterialCardView) {
        card.setOnFocusChangeListener { view, hasFocus ->
            (view as MaterialCardView).cardElevation = if (hasFocus) {
                dpToPx(8f).toFloat()
            } else {
                dpToPx(2f).toFloat()
            }
        }
    }

    private fun navigateToCombatTracker(encounterId: Long) {
        isReturningFromCombat = true
        val intent = Intent(this, CombatTrackerActivity::class.java).apply {
            putExtra(Constants.Extras.ENCOUNTER_ID, encounterId)
        }
        startActivity(intent)
    }

    override fun onPause() {
        super.onPause()
        // Clear any cached active encounter state when leaving
        viewModel.clearActiveEncounterCache()
    }
}
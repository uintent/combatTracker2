// File: CombatTrackerActivity.kt
// Location: app/src/main/java/com/example/combattracker/ui/combat/CombatTrackerActivity.kt

package com.example.combattracker.ui.combat

import androidx.appcompat.app.AlertDialog
import com.example.combattracker.utils.toast
import com.example.combattracker.utils.navigateToEncounterList
import com.example.combattracker.utils.showEndEncounterDialog

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.combattracker.CombatTrackerApplication
import com.example.combattracker.R
import com.example.combattracker.data.database.entities.EncounterActor
import com.example.combattracker.databinding.ActivityCombatTrackerBinding
import com.example.combattracker.databinding.DialogSaveEncounterBinding
import com.example.combattracker.ui.actors.ActorLibraryActivity
import com.example.combattracker.utils.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * CombatTrackerActivity - Main combat tracking screen
 *
 * Purpose:
 * - Display actors in initiative order
 * - Manage turn progression and rounds
 * - Handle initiative rolling
 * - Show actor context menus
 * - Track and display conditions
 *
 * Requirements Reference:
 * From section 3.3.4: Turn Order Display
 * - Horizontal arrangement from left (highest initiative) to right (lowest)
 * - Actor tile sizing with active actor larger
 * - Visual indicators for turn status
 * From section 3.3.5: Turn Management Controls
 * - Previous/Next Turn and Round buttons
 * From section 3.4: Actor Context Menu System
 * - Bottom sheet for actor management
 */
class CombatTrackerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCombatTrackerBinding

    private val viewModel: CombatViewModel by viewModels {
        val encounterId = intent.getLongExtra(Constants.Extras.ENCOUNTER_ID, -1L)
        CombatViewModel.Factory(
            (application as CombatTrackerApplication).encounterRepository,
            (application as CombatTrackerApplication).conditionDao,
            encounterId
        )
    }

    private lateinit var combatActorAdapter: CombatActorAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>

    // ========== Lifecycle Methods ==========

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force landscape orientation
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Keep screen on during combat
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityCombatTrackerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViews()
        setupBottomSheet()
        observeViewModel()

        // Load encounter
        if (savedInstanceState == null) {
            loadEncounter()
        }

        // Listen for fragment results
        supportFragmentManager.setFragmentResultListener(
            ActorContextMenuFragment.RESULT_SAVE_ENCOUNTER,
            this
        ) { _, _ ->
            saveEncounter()
        }

        supportFragmentManager.setFragmentResultListener(
            ActorContextMenuFragment.RESULT_ADD_ACTOR,
            this
        ) { _, _ ->
            addActorToEncounter()
        }

        supportFragmentManager.setFragmentResultListener(
            ActorContextMenuFragment.RESULT_END_ENCOUNTER,
            this
        ) { _, _ ->
            onBackPressed()
        }

        Timber.d("CombatTrackerActivity created")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_combat_tracker, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_roll_all -> {
                confirmRollInitiative(all = true)
                true
            }
            R.id.action_roll_npcs -> {
                confirmRollInitiative(all = false)
                true
            }
            R.id.action_add_actor -> {
                addActorToEncounter()
                true
            }
            R.id.action_save_encounter -> {
                saveEncounter()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        // Check if bottom sheet is expanded
        if (::bottomSheetBehavior.isInitialized &&
            bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            return
        }

        // Otherwise show end encounter dialog
        showEndEncounterDialog()
    }

    // ========== UI Setup ==========

    /**
     * Setup toolbar
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            // Title will be set when encounter loads
        }
    }

    /**
     * Setup views
     */
    private fun setupViews() {
        setupActorRecyclerView()
        setupTurnControls()
        setupBackgroundImage()
    }

    /**
     * Setup actor RecyclerView for horizontal scrolling
     */
    private fun setupActorRecyclerView() {
        combatActorAdapter = CombatActorAdapter(
            onActorClick = { actor ->
                showActorContextMenu(actor)
            }
        )

        binding.recyclerViewActors.apply {
            adapter = combatActorAdapter
            layoutManager = LinearLayoutManager(
                this@CombatTrackerActivity,
                RecyclerView.HORIZONTAL,
                false
            )
            setHasFixedSize(false) // Size changes when active actor changes
        }
    }

    /**
     * Setup turn control buttons
     */
    private fun setupTurnControls() {
        binding.buttonPreviousTurn.setOnClickListener {
            viewModel.previousTurn()
        }

        binding.buttonNextTurn.setOnClickListener {
            viewModel.nextTurn()
        }

        binding.buttonPreviousRound.setOnClickListener {
            viewModel.previousRound()
        }

        binding.buttonNextRound.setOnClickListener {
            viewModel.nextRound()
        }
    }

    /**
     * Setup background image
     */
    private fun setupBackgroundImage() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val backgroundPath = prefs.getString(Constants.PrefsKeys.BACKGROUND_IMAGE_PATH, null)

        if (backgroundPath != null) {
            binding.backgroundImageView.loadFromInternalStorage(backgroundPath)
            binding.backgroundImageView.visible()
        } else {
            binding.backgroundImageView.gone()
        }
    }

    /**
     * Setup bottom sheet for actor context menu
     */
    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        // Set peek height to 0 so it's fully hidden
        bottomSheetBehavior.peekHeight = 0

        // Handle back button when expanded
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: android.view.View, newState: Int) {
                // Update actor highlighting when sheet opens/closes
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    viewModel.clearSelectedActor()
                }
            }

            override fun onSlide(bottomSheet: android.view.View, slideOffset: Float) {
                // Not needed
            }
        })
    }

    // ========== ViewModel Observation ==========

    /**
     * Observe ViewModel state
     */
    private fun observeViewModel() {
        // Observe combat state
        viewModel.combatState.observe(this) { state ->
            updateCombatUI(state)
        }

        // Observe loading
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibleIf(isLoading)
            binding.combatContent.visibleIf(!isLoading)
        }

        // Observe errors
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                showError(it)
                viewModel.clearError()
            }
        }

        // Observe encounter loaded
        viewModel.encounterLoaded.observe(this) { loaded ->
            if (!loaded) {
                showEncounterLoadError()
            }
        }
    }

    /**
     * Update combat UI with new state
     */
    private fun updateCombatUI(state: CombatState) {
        // Update toolbar
        supportActionBar?.title = state.encounterName

        // Update round display
        binding.textRound.text = "Round ${state.roundNumber}"

        // Update actor list
        combatActorAdapter.submitList(state.actors)

        // Update turn controls
        binding.buttonPreviousTurn.isEnabled = !state.isFirstTurn && state.canProgress
        binding.buttonNextTurn.isEnabled = !state.isLastTurn && state.canProgress
        binding.buttonPreviousRound.isEnabled = state.roundNumber > 1 && state.canProgress
        binding.buttonNextRound.isEnabled = state.canProgress

        // Show message if actors missing initiative
        if (!state.canProgress) {
            binding.textMissingInitiative.visible()
            binding.textMissingInitiative.text = "Roll initiative for all actors to begin"
        } else {
            binding.textMissingInitiative.gone()
        }

        // Scroll to active actor
        state.currentActorIndex?.let { index ->
            binding.recyclerViewActors.smoothScrollToPosition(index)
        }
    }

    // ========== Actions ==========

    /**
     * Load the encounter
     */
    private fun loadEncounter() {
        lifecycleScope.launch {
            viewModel.loadEncounter()

            // Auto-roll initiative if requested
            if (intent.getBooleanExtra(Constants.Extras.START_COMBAT, false)) {
                viewModel.rollInitiativeForAll()
            }
        }
    }

    /**
     * Show actor context menu
     */
    private fun showActorContextMenu(actor: EncounterActorState) {
        viewModel.selectActor(actor.id)

        // Show the actor context menu fragment
        val fragment = ActorContextMenuFragment.newInstance(actor.id)
        fragment.show(supportFragmentManager, Constants.FragmentTags.ACTOR_CONTEXT_MENU)

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    /**
     * Confirm rolling initiative
     */
    private fun confirmRollInitiative(all: Boolean) {
        val message = if (all) {
            "Roll initiative for all actors? This will overwrite existing values."
        } else {
            "Roll initiative for NPCs only? This will overwrite their existing values."
        }

        AlertDialog.Builder(this)
            .setTitle("Roll Initiative")
            .setMessage(message)
            .setPositiveButton("Roll") { _, _ ->
                if (all) {
                    viewModel.rollInitiativeForAll()
                } else {
                    viewModel.rollInitiativeForNPCs()
                }
            }
            .setNegativeButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    /**
     * Add actor to encounter
     */
    private fun addActorToEncounter() {
        // This would open a dialog to select actors from library
        toast("Add actor feature coming soon")
    }

    /**
     * Save encounter
     */
    private fun saveEncounter() {
        val dialogBinding = DialogSaveEncounterBinding.inflate(layoutInflater)

        // Pre-fill with current name or auto-generated
        val currentName = viewModel.combatState.value?.encounterName ?: ""
        val autoName = "ENCsave_${System.currentTimeMillis()}"
        dialogBinding.editTextName.setText(if (currentName.startsWith("ENCsave_")) autoName else currentName)

        AlertDialog.Builder(this)
            .setTitle("Save Encounter")
            .setView(dialogBinding.root)
            .setPositiveButton(Constants.Dialogs.BUTTON_SAVE) { _, _ ->
                val name = dialogBinding.editTextName.text.toString().ifBlank { autoName }
                lifecycleScope.launch {
                    viewModel.saveEncounter(name)
                    toast(Constants.Success.ENCOUNTER_SAVED)
                }
            }
            .setNegativeButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    /**
     * Show end encounter dialog
     */
    private fun showEndEncounterDialog() {
        AlertDialog.Builder(this)
            .setTitle(Constants.Dialogs.END_ENCOUNTER_TITLE)
            .setMessage(Constants.Dialogs.END_ENCOUNTER_MESSAGE)
            .setPositiveButton(Constants.Dialogs.END_ENCOUNTER_SAVE) { _, _ ->
                saveEncounter()
                finish()
            }
            .setNegativeButton(Constants.Dialogs.END_ENCOUNTER_DISCARD) { _, _ ->
                finish()
            }
            .setNeutralButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    /**
     * Show encounter load error
     */
    private fun showEncounterLoadError() {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage("Failed to load encounter. It may have been deleted or corrupted.")
            .setPositiveButton(Constants.Dialogs.BUTTON_OK) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Show error message
     */
    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton(Constants.Dialogs.BUTTON_OK, null)
            .show()
    }
}
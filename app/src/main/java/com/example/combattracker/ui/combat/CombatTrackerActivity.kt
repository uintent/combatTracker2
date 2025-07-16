// File: CombatTrackerActivity.kt
// Location: app/src/main/java/com/example/combattracker/ui/combat/CombatTrackerActivity.kt

package com.example.combattracker.ui.combat

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupMenu
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
import com.example.combattracker.ui.encounter.EncounterManageActivity
import com.example.combattracker.utils.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import timber.log.Timber
import android.graphics.Color
import com.example.combattracker.data.database.entities.Actor
import com.example.combattracker.databinding.DialogAddActorBinding
import com.example.combattracker.ui.encounter.ActorSelectionAdapter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.collect
import android.widget.EditText
import android.text.InputType
import com.example.combattracker.ui.encounter.*
import kotlinx.coroutines.delay

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
            (application as CombatTrackerApplication).actorRepository,  // Add this line
            (application as CombatTrackerApplication).conditionDao,
            encounterId
        )
    }

    private lateinit var combatActorAdapter: CombatActorAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>

    // ========== Lifecycle Methods ==========

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force landscape orientation
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Keep screen on during combat
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityCombatTrackerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Debug check
        Timber.d("Bottom sheet view exists: ${binding.bottomSheet != null}")
        Timber.d("Bottom sheet visibility: ${binding.bottomSheet.visibility}")

        // Make the activity fullscreen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Hide the action bar
        supportActionBar?.hide()

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

        // Listen for close request
        supportFragmentManager.setFragmentResultListener(
            ActorContextMenuFragment.RESULT_CLOSE_REQUESTED,
            this
        ) { _, _ ->
            // Hide the bottom sheet through the behavior
            // This will trigger the cleanup in onStateChanged
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
        Timber.d("CombatTrackerActivity created")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_combat_tracker, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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
            R.id.action_return_main -> {
                showEndEncounterDialog()
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

        // Show end encounter dialog
        AlertDialog.Builder(this)
            .setTitle(Constants.Dialogs.END_ENCOUNTER_TITLE)
            .setMessage(Constants.Dialogs.END_ENCOUNTER_MESSAGE)
            .setPositiveButton(Constants.Dialogs.END_ENCOUNTER_SAVE) { _, _ ->
                saveEncounter()
                super.onBackPressed() // Now we call super to actually go back
            }
            .setNegativeButton(Constants.Dialogs.END_ENCOUNTER_DISCARD) { _, _ ->
                // Set a flag in shared preferences to indicate we're returning from combat
                getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean("returning_from_combat", true)
                    .apply()

                lifecycleScope.launch {
                    viewModel.deactivateCurrentEncounter()
                    finish()
                }
            }
            .setNeutralButton(Constants.Dialogs.BUTTON_CANCEL, null) // Don't call super - stay in activity
            .show()
    }

    // ========== UI Setup ==========

    /**
     * Setup views
     */
    private fun setupViews() {
        setupActorRecyclerView()
        setupTurnControls()
        setupBackgroundImage()
        setupButtons()
    }

    /**
     * Setup button handlers
     */
    private fun setupButtons() {

        binding.buttonMenu.setOnClickListener {
            showOverflowMenu(it)
        }
    }

    /**
     * Show overflow menu
     */
    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_combat_tracker, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            onOptionsItemSelected(item)
        }
        popup.show()
    }

    /**
     * Setup actor RecyclerView for horizontal scrolling
     */
    private fun setupActorRecyclerView() {
        Timber.d("Setting up actor RecyclerView")

        combatActorAdapter = CombatActorAdapter(
            onActorClick = { actor ->
                Timber.d("onActorClick lambda called for: ${actor.displayName}")
                viewModel.selectActor(actor.id)
                showActorContextMenu(actor)
            }
        )

        Timber.d("Adapter created, setting on RecyclerView")

        binding.recyclerViewActors.apply {
            adapter = combatActorAdapter
            layoutManager = LinearLayoutManager(
                this@CombatTrackerActivity,
                RecyclerView.HORIZONTAL,
                false
            )
            setHasFixedSize(false)

            // Add observer for size changes
            viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    updateActorSizing()
                }
            })
        }

        Timber.d("RecyclerView setup complete")
    }

    /**
     * Update actor sizing based on RecyclerView dimensions
     */
    private fun updateActorSizing() {
        val recyclerView = binding.recyclerViewActors
        val itemCount = combatActorAdapter.itemCount

        if (itemCount > 0) {
            combatActorAdapter.updateItemDimensions(
                recyclerView.width,
                recyclerView.height,
                itemCount,
                resources.displayMetrics.density
            )
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
            // Set round text to white when background is present
            binding.textRound.setTextColor(Color.WHITE)
        } else {
            binding.backgroundImageView.gone()
            // Set round text to black when no background
            binding.textRound.setTextColor(Color.BLACK)
        }
    }

    /**
     * Setup bottom sheet for actor context menu
     */
    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                Timber.d("Bottom sheet state changed to: $newState")

                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    // Clear the selected actor when bottom sheet is hidden
                    viewModel.clearSelectedActor()

                    // IMPORTANT: Clear the highlighted actor in the adapter
                    combatActorAdapter.setHighlightedActor(null)

                    // Clear the fragment reference
                    currentBottomSheetFragment = null

                    // Remove any fragment from the container
                    supportFragmentManager.findFragmentById(R.id.bottomSheet)?.let { fragment ->
                        supportFragmentManager.beginTransaction()
                            .remove(fragment)
                            .commitNow()
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Optional: Add any slide animations here
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
            combatActorAdapter.submitList(state.actors) {
                // Update sizing after list is submitted
                updateActorSizing()
            }
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
            loaded?.let {
                if (!it) {
                    showEncounterLoadError()
                }
            }
        }

        // Observe if there are missing initiatives
        viewModel.combatState.observe(this) { state ->
            val hasMissing = state.actors.any { !it.hasTakenTurn && it.missingInitiative }
            binding.textMissingInitiative.visibility = if (hasMissing) View.VISIBLE else View.GONE

            // Disable turn controls when initiative is missing
            binding.buttonNextTurn.isEnabled = !hasMissing
            binding.buttonPreviousTurn.isEnabled = !hasMissing
            binding.buttonNextRound.isEnabled = !hasMissing
            binding.buttonPreviousRound.isEnabled = !hasMissing
        }
    }

    /**
     * Update combat UI with new state
     */
    private fun updateCombatUI(state: CombatState) {
        // Update round display
        binding.textRound.text = getString(R.string.round_x, state.roundNumber)

        // Update turn controls
        binding.buttonPreviousTurn.isEnabled = !state.isFirstTurn && state.canProgress
        binding.buttonNextTurn.isEnabled = !state.isLastTurn && state.canProgress
        binding.buttonPreviousRound.isEnabled = state.roundNumber > 1 && state.canProgress
        binding.buttonNextRound.isEnabled = state.canProgress

        // Show message if actors missing initiative
        if (!state.canProgress) {
            binding.textMissingInitiative.visible()
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
    private var currentBottomSheetFragment: ActorContextMenuFragment? = null

    private fun showActorContextMenu(actor: EncounterActorState) {
        Timber.d("showActorContextMenu called for actor: ${actor.displayName}")

        // Check if we already have a fragment showing
        val existingFragment = supportFragmentManager.findFragmentById(R.id.bottomSheet) as? ActorContextMenuFragment

        if (existingFragment != null && existingFragment.isAdded) {
            // Update the existing fragment with new actor data
            existingFragment.updateActor(actor.id)

            // Update highlighting
            combatActorAdapter.setHighlightedActor(actor.id)

            // Make sure bottom sheet is expanded
            if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        } else {
            // Create new fragment for the first time
            val newFragment = ActorContextMenuFragment.newInstance(actor.id)
            currentBottomSheetFragment = newFragment

            supportFragmentManager.beginTransaction()
                .replace(R.id.bottomSheet, newFragment)
                .commit()

            // Highlight the selected actor
            combatActorAdapter.setHighlightedActor(actor.id)

            // Show the bottom sheet
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
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
        lifecycleScope.launch {
            // Get all actors from repository
            val actorRepository = (application as CombatTrackerApplication).actorRepository

            try {
                // Use take(1) to get just the first emission
                actorRepository.getAllActors().take(1).collect { actorList ->
                    if (actorList.isEmpty()) {
                        Timber.w("No actors in library to add to encounter")
                        return@collect
                    }

                    // Create dialog
                    val dialogBinding = DialogAddActorBinding.inflate(layoutInflater)

                    // Track selected actors and quantities
                    val selectedActors = mutableMapOf<Long, Int>()
                    val actorStates = actorList.map { actor ->
                        ActorSelectionState(actor, false, 1)
                    }.toMutableList()

                    // Create adapter first (declare it before using it in lambdas)
                    lateinit var adapter: ActorSelectionAdapter

                    // Initialize adapter with its callbacks
                    adapter = ActorSelectionAdapter(
                        onActorChecked = { state, isChecked ->
                            val index = actorStates.indexOfFirst { it.actor.id == state.actor.id }
                            if (index >= 0) {
                                actorStates[index] = state.copy(isSelected = isChecked)
                                if (isChecked) {
                                    selectedActors[state.actor.id] = state.quantity
                                } else {
                                    selectedActors.remove(state.actor.id)
                                }
                            }
                        },
                        onQuantityClick = { state ->
                            // Show quantity dialog
                            showQuantityDialog(state) { newQuantity ->
                                val index = actorStates.indexOfFirst { it.actor.id == state.actor.id }
                                if (index >= 0) {
                                    actorStates[index] = state.copy(quantity = newQuantity)
                                    selectedActors[state.actor.id] = newQuantity
                                    adapter.notifyItemChanged(index)
                                }
                            }
                        }
                    )

                    dialogBinding.recyclerViewActors.adapter = adapter
                    adapter.submitList(actorStates)

                    // Create and show dialog
                    AlertDialog.Builder(this@CombatTrackerActivity)
                        .setTitle("Add Actors to Encounter")
                        .setView(dialogBinding.root)
                        .setPositiveButton("Add") { _, _ ->
                            Timber.d("Adding ${selectedActors.size} actors to encounter")
                            // Add selected actors with their quantities
                            selectedActors.forEach { (actorId, quantity) ->
                                for (i in 1..quantity) {
                                    addActorToEncounterWithInitiative(actorId)
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load actors for selection")
            }
        }
    }

    private fun showQuantityDialog(state: ActorSelectionState, onQuantitySet: (Int) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(state.quantity.toString())
            selectAll()
        }

        AlertDialog.Builder(this)
            .setTitle("Set Quantity for ${state.actor.name}")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val quantity = input.text.toString().toIntOrNull() ?: 1
                onQuantitySet(quantity.coerceIn(1, 99))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addActorToEncounterWithInitiative(actorId: Long) {
        lifecycleScope.launch {
            // First, get the actor name to show in the dialog
            val actorRepository = (application as CombatTrackerApplication).actorRepository
            val actor = actorRepository.getActorById(actorId)
            val actorName = actor?.name ?: "Unknown Actor"

            // Show dialog to set initiative for the new actor
            val input = EditText(this@CombatTrackerActivity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                hint = "Enter initiative"
            }

            AlertDialog.Builder(this@CombatTrackerActivity)
                .setTitle("Set Initiative for $actorName")
                .setMessage("Enter initiative value for the new actor")
                .setView(input)
                .setPositiveButton("Add") { _, _ ->
                    val initiative = input.text.toString().toDoubleOrNull()
                    if (initiative != null) {
                        // Add actor with initiative
                        lifecycleScope.launch {
                            try {
                                viewModel.addActorToEncounter(actorId, initiative)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to add actor")
                                // Show error to user
                                AlertDialog.Builder(this@CombatTrackerActivity)
                                    .setTitle("Error")
                                    .setMessage("Failed to add actor. They may already be in the encounter.")
                                    .setPositiveButton("OK", null)
                                    .show()
                            }
                        }
                    } else {
                        Timber.w("Invalid initiative value entered")
                    }
                }
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show()
        }
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
    fun showEndEncounterDialog() {
        AlertDialog.Builder(this)
            .setTitle(Constants.Dialogs.END_ENCOUNTER_TITLE)
            .setMessage(Constants.Dialogs.END_ENCOUNTER_MESSAGE)
            .setPositiveButton(Constants.Dialogs.END_ENCOUNTER_SAVE) { _, _ ->
                saveEncounter()
                finish()
            }
            .setNegativeButton(Constants.Dialogs.END_ENCOUNTER_DISCARD) { _, _ ->
                // Show a progress dialog while deactivating
                val progressDialog = AlertDialog.Builder(this)
                    .setMessage("Ending encounter...")
                    .setCancelable(false)
                    .create()

                progressDialog.show()

                lifecycleScope.launch {
                    try {
                        viewModel.deactivateCurrentEncounter()
                        // Add a small delay to ensure database write completes
                        kotlinx.coroutines.delay(100)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to deactivate encounter")
                    } finally {
                        progressDialog.dismiss()
                        finish()
                    }
                }
            }
            .setNeutralButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    /**
     * Navigate to encounter list
     */
    fun navigateToEncounterList() {
        val intent = Intent(this, EncounterManageActivity::class.java)
        startActivity(intent)
    }

    /**
     * Show save encounter dialog (public for fragment access)
     */
    fun showSaveEncounterDialog() {
        saveEncounter()
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
// File: EncounterCreateActivity.kt
// Location: app/src/main/java/com/example/combattracker/ui/encounter/EncounterCreateActivity.kt

package com.example.combattracker.ui.encounter

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.NumberPicker
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.combattracker.CombatTrackerApplication
import com.example.combattracker.R
import com.example.combattracker.data.database.entities.Actor
import com.example.combattracker.databinding.ActivityEncounterCreateBinding
import com.example.combattracker.databinding.DialogActorQuantityBinding
import com.example.combattracker.ui.combat.CombatTrackerActivity
import com.example.combattracker.utils.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * EncounterCreateActivity - Create a new encounter
 *
 * Purpose:
 * - Set encounter name (optional)
 * - Select actors from library
 * - Specify quantity for multiple instances
 * - Save or immediately start the encounter
 *
 * Requirements Reference:
 * From section 3.3.1: Encounter Creation Workflow
 * 1. Set Encounter Name: Optional field; if not provided, uses format "ENCsave_YYYYMMDD_HHMMSS"
 * 2. Select Actors:
 *    - Display all actors with name, thumbnail, and category
 *    - Use checkboxes for selection
 *    - Optional number input field for multiple instances of same actor
 * 3. Save or Start: Option to save encounter or start immediately
 */
class EncounterCreateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEncounterCreateBinding

    private val viewModel: EncounterCreateViewModel by viewModels {
        EncounterCreateViewModel.Factory(
            (application as CombatTrackerApplication).encounterRepository,
            (application as CombatTrackerApplication).actorRepository
        )
    }

    private lateinit var actorSelectionAdapter: ActorSelectionAdapter
    private lateinit var selectedActorsAdapter: SelectedActorsAdapter
    private var isAddingActors = false

    // ========== Lifecycle Methods ==========

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEncounterCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViews()
        observeViewModel()

        Timber.d("EncounterCreateActivity created")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_encounter_create, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_save -> {
                saveEncounter(startImmediately = false)
                true
            }
            R.id.action_save_and_start -> {
                saveEncounter(startImmediately = true)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        // If adding actors, hide the selection first
        if (isAddingActors) {
            hideActorSelection()
            return
        }

        // Otherwise check if we should discard
        if (viewModel.hasSelectedActors()) {
            showDiscardDialog()
        } else {
            super.onBackPressed()
        }
    }

    // ========== UI Setup ==========

    /**
     * Setup toolbar
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Create Encounter"
        }
    }

    /**
     * Setup views
     */
    private fun setupViews() {
        setupNameInput()
        setupAddActorButton()
        setupSelectedActorsList()
        setupActorSelectionList()
        setupButtons()
    }

    /**
     * Setup encounter name input
     */
    private fun setupNameInput() {
        binding.editTextEncounterName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.setEncounterName(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Show hint about auto-generated name
        binding.textInputLayoutName.helperText = "Leave empty for auto-generated name"
    }

    /**
     * Setup add actor button
     */
    private fun setupAddActorButton() {
        binding.buttonAddActor.setOnClickListener {
            if (isAddingActors) {
                hideActorSelection()
            } else {
                showActorSelection()
            }
        }

        // Set initial text
        updateAddActorButtonText()
    }
    /**
     * Setup selected actors list (the ones already added to encounter)
     */
    private fun setupSelectedActorsList() {
        selectedActorsAdapter = SelectedActorsAdapter(
            onQuantityClick = { actor ->
                showQuantityDialog(actor)
            },
            onRemoveClick = { actor ->
                viewModel.removeActor(actor.id)
            }
        )

        binding.recyclerViewSelectedActors.apply {
            adapter = selectedActorsAdapter
            layoutManager = LinearLayoutManager(this@EncounterCreateActivity)
            setHasFixedSize(false)
        }
    }

    /**
     * Setup actor selection list (available actors from library)
     */
    private fun setupActorSelectionList() {
        actorSelectionAdapter = ActorSelectionAdapter(
            onActorChecked = { state, isChecked ->
                // When an actor is selected from the library, add it with quantity dialog
                if (isChecked) {
                    showQuantityDialog(state.actor)
                    // Uncheck after adding
                    actorSelectionAdapter.uncheckActor(state.actor.id)
                }
            },
            onQuantityClick = { state ->
                // This shouldn't be used in selection mode
            }
        )

        binding.recyclerViewActors.apply {
            adapter = actorSelectionAdapter
            layoutManager = GridLayoutManager(this@EncounterCreateActivity, 3)
            setHasFixedSize(true)
        }
    }

    /**
     * Setup action buttons
     */
    private fun setupButtons() {
        binding.buttonSave.setOnClickListener {
            saveEncounter(startImmediately = false)
        }

        binding.buttonSaveAndStart.setOnClickListener {
            saveEncounter(startImmediately = true)
        }
    }

    /**
     * Show actor selection UI
     */
    private fun showActorSelection() {
        isAddingActors = true
        binding.textAvailableActors.visible()
        binding.recyclerViewActors.visible()
        binding.divider.visible()
        binding.buttonAddActor.text = "Done"

        // Scroll to show the actor selection
        binding.scrollView.post {
            binding.scrollView.smoothScrollTo(0, binding.divider.top)
        }
    }

    private fun updateAddActorButtonText() {
        val hasActors = viewModel.hasSelectedActors()
        binding.buttonAddActor.text = when {
            isAddingActors -> "Done"
            hasActors -> "Add / Remove Actors"
            else -> "Add Actors"
        }
    }

    /**
     * Hide actor selection UI
     */
    private fun hideActorSelection() {
        isAddingActors = false
        binding.textAvailableActors.gone()
        binding.recyclerViewActors.gone()
        updateAddActorButtonText()
    }

    // ========== ViewModel Observation ==========

    /**
     * Observe ViewModel state
     */
    private fun observeViewModel() {
        // Observe all actors from library
        lifecycleScope.launch {
            viewModel.actors.collectLatest { actors ->
                actorSelectionAdapter.submitList(actors)

                // If no actors in library, show empty state
                if (actors.isEmpty() && !viewModel.hasSelectedActors()) {
                    binding.emptyStateLayout.visible()
                    binding.recyclerViewSelectedActors.gone()
                    binding.textEmptyMessage.text = "No actors in library"
                    binding.textEmptySubMessage.text = "Create actors first before making an encounter"
                    binding.buttonAddActor.isEnabled = false
                } else {
                    binding.buttonAddActor.isEnabled = true
                }
            }
        }

        // Observe selected actors for this encounter
        viewModel.selectedActors.observe(this) { selected ->
            updateSelectedActorsList(selected)
            updateSelectedCount(selected.size)
            invalidateOptionsMenu()
        }

        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibleIf(isLoading)
            binding.scrollView.visibleIf(!isLoading)
        }

        // Observe save complete
        viewModel.saveComplete.observe(this) { result ->
            result?.let { (encounterId, startImmediately) ->
                if (startImmediately) {
                    startCombatTracker(encounterId)
                } else {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }

        // Observe errors
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                showError(it)
                viewModel.clearError()
            }
        }
    }

    /**
     * Update the selected actors list
     */
    private fun updateSelectedActorsList(selectedActors: Map<Long, Int>) {
        if (selectedActors.isEmpty()) {
            binding.emptyStateLayout.visible()
            binding.recyclerViewSelectedActors.gone()
            binding.textEmptyMessage.text = "No actors added yet"
            binding.textEmptySubMessage.text = "Tap 'Add Actors' to add actors to this encounter"
        } else {
            binding.emptyStateLayout.gone()
            binding.recyclerViewSelectedActors.visible()

            // Convert selected actors map to list for adapter
            val currentActors = actorSelectionAdapter.currentList
            val selectedActorsList = viewModel.getSelectedActorsListSync(currentActors)
            selectedActorsAdapter.submitList(selectedActorsList)
        }

        // Update button text based on state
        updateAddActorButtonText()
    }

    /**
     * Update selected actor count
     */
    private fun updateSelectedCount(count: Int) {
        val totalActors = viewModel.getTotalActorCount()
        binding.textSelectedCount.text = when {
            count == 0 -> "No actors selected"
            totalActors == 1 -> "1 actor selected"
            else -> "$totalActors actors selected ($count unique)"
        }

        // Enable/disable buttons
        val hasSelection = count > 0
        binding.buttonSave.isEnabled = hasSelection
        binding.buttonSaveAndStart.isEnabled = hasSelection
    }

    // ========== User Actions ==========

    /**
     * Show quantity selection dialog
     */
    private fun showQuantityDialog(actor: Actor) {
        val dialogBinding = DialogActorQuantityBinding.inflate(layoutInflater)

        // Setup the dialog content
        dialogBinding.textActorName.text = actor.name
        dialogBinding.numberPicker.apply {
            minValue = 1
            maxValue = 20 // Reasonable limit
            value = viewModel.getActorQuantity(actor.id)
            wrapSelectorWheel = false
        }

        AlertDialog.Builder(this)
            .setTitle("How many ${actor.name}?")
            .setView(dialogBinding.root)
            .setPositiveButton("OK") { _, _ ->
                val quantity = dialogBinding.numberPicker.value
                viewModel.setActorQuantity(actor, quantity)

                // Hide actor selection if we were adding
                if (isAddingActors) {
                    hideActorSelection()
                }
            }
            .setNegativeButton("Remove") { _, _ ->
                viewModel.removeActor(actor.id)
            }
            .setNeutralButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    /**
     * Save the encounter
     */
    private fun saveEncounter(startImmediately: Boolean) {
        if (!viewModel.hasSelectedActors()) {
            showError("Please select at least one actor")
            return
        }

        lifecycleScope.launch {
            viewModel.saveEncounter(startImmediately)
        }
    }

    /**
     * Start combat tracker with the created encounter
     */
    private fun startCombatTracker(encounterId: Long) {
        val intent = Intent(this, CombatTrackerActivity::class.java).apply {
            putExtra(Constants.Extras.ENCOUNTER_ID, encounterId)
            putExtra(Constants.Extras.START_COMBAT, true)
        }
        startActivity(intent)
        finish()
    }

    /**
     * Show discard confirmation dialog
     */
    private fun showDiscardDialog() {
        AlertDialog.Builder(this)
            .setTitle("Discard Encounter?")
            .setMessage("You have selected actors. Are you sure you want to discard this encounter?")
            .setPositiveButton("Discard") { _, _ ->
                finish()
            }
            .setNegativeButton(Constants.Dialogs.BUTTON_CANCEL, null)
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
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.combattracker.CombatTrackerApplication
import com.example.combattracker.R
import com.example.combattracker.data.database.entities.Actor
import com.example.combattracker.databinding.ActivityEncounterCreateBinding
import com.example.combattracker.databinding.DialogActorQuantityBinding
import com.example.combattracker.ui.combat.CombatTrackerActivity
import com.example.combattracker.ui.encounter.ActorSelectionAdapter
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
        setupActorList()
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
        binding.textInputName.helperText = "Leave empty for auto-generated name"
    }

    /**
     * Setup actor selection list
     */
    private fun setupActorList() {
        actorSelectionAdapter = ActorSelectionAdapter(
            onActorChecked = { state, isChecked ->
                if (isChecked) {
                    showQuantityDialog(state.actor)
                } else {
                    viewModel.removeActor(state.actor.id)
                }
            },
            onQuantityClick = { state ->
                showQuantityDialog(state.actor)
            }
        )


        binding.recyclerViewActors.apply {
            adapter = actorSelectionAdapter
            layoutManager = LinearLayoutManager(this@EncounterCreateActivity)
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

    // ========== ViewModel Observation ==========

    /**
     * Observe ViewModel state
     */
    private fun observeViewModel() {
        // Observe actors list
        lifecycleScope.launch {
            viewModel.actors.collectLatest { actors ->
                actorSelectionAdapter.submitList(actors)
                updateEmptyState(actors.isEmpty())
            }
        }

        // Observe selected actors
        viewModel.selectedActors.observe(this) { selected ->
            updateSelectedCount(selected.size)
            invalidateOptionsMenu()
        }

        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            // Show/hide progress indicator if available in layout
            // binding.progressBar?.visibleIf(isLoading)
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
     * Update empty state
     */
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibleIf(isEmpty)
        binding.recyclerViewActors.visibleIf(!isEmpty)

        if (isEmpty) {
            binding.textEmpty.text = "No actors in library"
            // If there's a sub-message TextView, update it too
            // binding.textEmptySubMessage?.text = "Create actors first before making an encounter"
        }
    }

    /**
     * Update selected actor count
     */
    private fun updateSelectedCount(count: Int) {
        binding.textSelectedInfo.text = when (count) {
            0 -> "No actors selected"
            1 -> "1 actor selected"
            else -> "$count actors selected"
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
            .setPositiveButton("OK") { dialog, which ->
                val quantity = dialogBinding.numberPicker.value
                viewModel.setActorQuantity(actor, quantity)
            }
            .setNegativeButton("Remove") { dialog, which ->
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
            .setPositiveButton("Discard") { dialog, which ->
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
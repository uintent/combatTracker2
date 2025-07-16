// File: EncounterManageActivity.kt
// Location: app/src/main/java/com/example/combattracker/ui/encounter/EncounterManageActivity.kt

package com.example.combattracker.ui.encounter

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.combattracker.CombatTrackerApplication
import com.example.combattracker.R
import com.example.combattracker.data.database.dao.EncounterWithCount
import com.example.combattracker.databinding.ActivityEncounterManageBinding
import com.example.combattracker.ui.combat.CombatTrackerActivity
import com.example.combattracker.ui.encounter.EncounterListAdapter
import com.example.combattracker.utils.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * EncounterManageActivity - Manage saved encounters
 *
 * Purpose:
 * - Display list of saved encounters
 * - Load encounters to continue combat
 * - Delete unwanted encounters
 * - Show encounter details (name, date, actor count)
 *
 * Requirements Reference:
 * From section 3.3.2: Encounter List Management
 * - Shows detailed list with encounter name + date/time + actor count
 * - Load Encounter: Immediately starts selected encounter
 * - Delete Encounter: Single encounter deletion with confirmation
 */
class EncounterManageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEncounterManageBinding

    private val viewModel: EncounterManageViewModel by viewModels {
        EncounterManageViewModel.Factory(
            (application as CombatTrackerApplication).encounterRepository
        )
    }

    private lateinit var encounterAdapter: EncounterListAdapter

    // ========== Lifecycle Methods ==========

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEncounterManageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupFab()
        observeViewModel()

        Timber.d("EncounterManageActivity created")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_encounter_manage, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_create_new -> {
                createNewEncounter()
                true
            }
            else -> super.onOptionsItemSelected(item)
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
            title = "Manage Encounters"
        }
    }

    /**
     * Setup RecyclerView
     */
    private fun setupRecyclerView() {
        encounterAdapter = EncounterListAdapter(
            onEncounterClick = { encounter ->
                loadEncounter(encounter)
            },
            onEncounterLongClick = { encounter ->
                showEncounterOptionsDialog(encounter)
                true
            }
        )

        binding.recyclerViewEncounters.apply {
            adapter = encounterAdapter
            layoutManager = LinearLayoutManager(this@EncounterManageActivity)
            setHasFixedSize(true)
        }
    }

    /**
     * Setup Floating Action Button
     */
    private fun setupFab() {
        binding.fabCreateEncounter.setOnClickListener {
            createNewEncounter()
        }
    }

    // ========== ViewModel Observation ==========

    /**
     * Observe ViewModel data
     */
    private fun observeViewModel() {
        // Observe encounters list
        lifecycleScope.launch {
            viewModel.encounters.collectLatest { encounters ->
                encounterAdapter.submitList(encounters)
                updateEmptyState(encounters.isEmpty())
            }
        }

        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibleIf(isLoading)
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
        binding.emptyStateLayout.visibleIf(isEmpty)
        binding.recyclerViewEncounters.visibleIf(!isEmpty)

        if (isEmpty) {
            binding.textEmptyMessage.text = "No saved encounters"
            binding.textEmptySubMessage.text = "Create an encounter to get started"
        }
    }

    // ========== User Actions ==========

    /**
     * Create a new encounter
     */
    private fun createNewEncounter() {
        startActivity<EncounterCreateActivity>()
    }

    /**
     * Load an encounter
     */
    private fun loadEncounter(encounter: EncounterWithCount) {
        AlertDialog.Builder(this)
            .setTitle("Load Encounter?")
            .setMessage("Load '${encounter.encounter.name}' and start combat?")
            .setPositiveButton("Load") { _, _ ->
                startCombatTracker(encounter.encounter.id)
            }
            .setNegativeButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    /**
     * Start combat tracker with encounter
     */
    private fun startCombatTracker(encounterId: Long) {
        val intent = Intent(this, CombatTrackerActivity::class.java).apply {
            putExtra(Constants.Extras.ENCOUNTER_ID, encounterId)
            // DO NOT set START_COMBAT to true - we want to preserve existing initiative values
        }
        startActivity(intent)
        finish() // Close this screen when loading encounter
    }

    /**
     * Show options dialog for encounter
     */
    private fun showEncounterOptionsDialog(encounter: EncounterWithCount) {
        val options = arrayOf("Load", "Delete")

        AlertDialog.Builder(this)
            .setTitle(encounter.encounter.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> loadEncounter(encounter)
                    1 -> confirmDeleteEncounter(encounter)
                }
            }
            .show()
    }

    /**
     * Confirm encounter deletion
     */
    private fun confirmDeleteEncounter(encounter: EncounterWithCount) {
        AlertDialog.Builder(this)
            .setTitle(Constants.Dialogs.DELETE_ENCOUNTER_TITLE)
            .setMessage(Constants.Dialogs.DELETE_ENCOUNTER_MESSAGE)
            .setPositiveButton(Constants.Dialogs.BUTTON_DELETE) { _, _ ->
                deleteEncounter(encounter.encounter.id)
            }
            .setNegativeButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    /**
     * Delete an encounter
     */
    private fun deleteEncounter(encounterId: Long) {
        lifecycleScope.launch {
            viewModel.deleteEncounter(encounterId).fold(
                onSuccess = {
                    toast(Constants.Success.ENCOUNTER_DELETED)
                },
                onFailure = { error ->
                    showError(error.message ?: Constants.Errors.GENERIC_ERROR)
                }
            )
        }
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
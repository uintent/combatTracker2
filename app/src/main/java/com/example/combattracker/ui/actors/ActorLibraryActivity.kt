// File: ActorLibraryActivity.kt
// Location: app/src/main/java/com/example/combattracker/ui/actors/ActorLibraryActivity.kt

package com.example.combattracker.ui.actors

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.combattracker.CombatTrackerApplication
import com.example.combattracker.R
import com.example.combattracker.data.database.entities.Actor
import com.example.combattracker.data.model.ActorCategory
import com.example.combattracker.databinding.ActivityActorLibraryBinding
import com.example.combattracker.ui.actors.ActorListAdapter
import com.example.combattracker.utils.*
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ActorLibraryActivity - Manages the actor library
 *
 * Purpose:
 * - Display all actors in the library
 * - Search, filter, and sort actors
 * - Add, edit, and delete actors
 * - Navigate to actor creation/editing
 *
 * Requirements Reference:
 * From section 3.1.3: Actor Library Management
 * - Display: Actor name, thumbnail picture, and actor category
 * - Search: Text-based search within actor names
 * - Sort: Alphabetical sorting by name, category, or initiative modifier
 * - Filter: Filter by actor category (Players, NPCs, Monsters, Others)
 * - Actions: Add new actor, edit existing actor, delete actor
 */
class ActorLibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActorLibraryBinding

    private val viewModel: ActorLibraryViewModel by viewModels {
        ActorLibraryViewModel.Factory(
            (application as CombatTrackerApplication).actorRepository
        )
    }

    private lateinit var actorAdapter: ActorListAdapter

    /**
     * Activity result launcher for actor editing
     */
    private val editActorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Refresh will happen automatically through Flow
            viewModel.refreshAfterEdit()
        }
    }

    // ========== Lifecycle Methods ==========

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize view binding
        binding = ActivityActorLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup UI components
        setupToolbar()
        setupRecyclerView()
        setupSearchView()
        setupFilters()
        setupSortSpinner()
        setupFab()

        // Observe ViewModel
        observeViewModel()

        Timber.d("ActorLibraryActivity created")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_actor_library, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_clear_filters -> {
                clearAllFilters()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ========== UI Setup ==========

    /**
     * Setup the toolbar
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Actor Library"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    /**
     * Setup the RecyclerView for displaying actors
     */
    private fun setupRecyclerView() {
        actorAdapter = ActorListAdapter(
            onActorClick = { actor ->
                editActor(actor)
            },
            onActorLongClick = { actor ->
                showActorOptionsDialog(actor)
                true
            }
        )

        binding.recyclerViewActors.apply {
            adapter = actorAdapter
            layoutManager = GridLayoutManager(this@ActorLibraryActivity, getSpanCount())
            setHasFixedSize(true)
        }
    }

    /**
     * Calculate span count based on screen width
     */
    private fun getSpanCount(): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val itemWidthDp = 120 // Approximate width of actor card
        return (screenWidthDp / itemWidthDp).toInt().coerceAtLeast(2)
    }

    /**
     * Setup the search view
     */
    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
        })
    }

    /**
     * Setup category filter chips
     */
    private fun setupFilters() {
        // Add chips for each category
        ActorCategory.values().forEach { category ->
            val chip = Chip(this).apply {
                text = category.displayName
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked ->
                    viewModel.toggleCategoryFilter(category)
                }
            }
            binding.chipGroupCategories.addView(chip)
        }

        // Add "All" chip
        val allChip = Chip(this).apply {
            text = "All"
            isCheckable = true
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    clearCategoryFilters()
                }
            }
        }
        binding.chipGroupCategories.addView(allChip, 0)
    }

    /**
     * Setup sort spinner
     */
    private fun setupSortSpinner() {
        val sortOptions = arrayOf("Name", "Category", "Initiative")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        binding.spinnerSort.adapter = adapter
        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val sortBy = when (position) {
                    0 -> "name"
                    1 -> "category"
                    2 -> "initiative"
                    else -> "name"
                }
                viewModel.setSortBy(sortBy)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    /**
     * Setup floating action button
     */
    private fun setupFab() {
        binding.fabAddActor.setOnClickListener {
            createNewActor()
        }
    }

    // ========== ViewModel Observation ==========

    /**
     * Observe ViewModel data
     */
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.actors.collectLatest { actors ->
                actorAdapter.submitList(actors)
                updateEmptyState(actors.isEmpty())
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibleIf(isLoading)
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                showError(it)
                viewModel.clearError()
            }
        }
    }

    /**
     * Update empty state visibility
     */
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateLayout.visibleIf(isEmpty)
        binding.recyclerViewActors.visibleIf(!isEmpty)

        if (isEmpty) {
            val hasFilters = viewModel.hasActiveFilters()
            binding.textEmptyMessage.text = if (hasFilters) {
                "No actors match your filters"
            } else {
                "No actors in your library yet"
            }
            binding.textEmptySubMessage.text = if (hasFilters) {
                "Try adjusting your search or filters"
            } else {
                "Tap the + button to create your first actor"
            }
        }
    }

    // ========== User Actions ==========

    /**
     * Create a new actor
     */
    private fun createNewActor() {
        val intent = Intent(this, ActorEditActivity::class.java)
        editActorLauncher.launch(intent)
    }

    /**
     * Edit an existing actor
     */
    private fun editActor(actor: Actor) {
        val intent = Intent(this, ActorEditActivity::class.java).apply {
            putExtra(Constants.Extras.ACTOR_ID, actor.id)
        }
        editActorLauncher.launch(intent)
    }

    /**
     * Show options dialog for an actor
     */
    private fun showActorOptionsDialog(actor: Actor) {
        val options = arrayOf("Edit", "Delete")

        AlertDialog.Builder(this)
            .setTitle(actor.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editActor(actor)
                    1 -> confirmDeleteActor(actor)
                }
            }
            .show()
    }

    /**
     * Confirm actor deletion
     */
    private fun confirmDeleteActor(actor: Actor) {
        AlertDialog.Builder(this)
            .setTitle(Constants.Dialogs.DELETE_ACTOR_TITLE)
            .setMessage(Constants.Dialogs.DELETE_ACTOR_MESSAGE.format(actor.name))
            .setPositiveButton(Constants.Dialogs.BUTTON_DELETE) { _, _ ->
                deleteActor(actor)
            }
            .setNegativeButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    /**
     * Delete an actor
     */
    private fun deleteActor(actor: Actor) {
        lifecycleScope.launch {
            viewModel.deleteActor(actor).fold(
                onSuccess = {
                    toast(Constants.Success.ACTOR_DELETED)
                },
                onFailure = { error ->
                    showError(error.message ?: Constants.Errors.GENERIC_ERROR)
                }
            )
        }
    }

    /**
     * Clear all filters
     */
    private fun clearAllFilters() {
        binding.searchView.setQuery("", false)
        clearCategoryFilters()
        binding.spinnerSort.setSelection(0)
    }

    /**
     * Clear category filters
     */
    private fun clearCategoryFilters() {
        for (i in 0 until binding.chipGroupCategories.childCount) {
            val chip = binding.chipGroupCategories.getChildAt(i) as? Chip
            chip?.isChecked = i == 0 // Check only "All" chip
        }
        viewModel.clearCategoryFilters()
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
// File: SettingsActivity.kt
// Location: app/src/main/java/com/example/combattracker/ui/settings/SettingsActivity.kt

package com.example.combattracker.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.combattracker.CombatTrackerApplication
import com.example.combattracker.R
import com.example.combattracker.databinding.ActivitySettingsBinding
import com.example.combattracker.utils.*
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * SettingsActivity - App configuration and maintenance
 *
 * Purpose:
 * - Set custom background image
 * - Reset actors and/or encounters
 * - View storage usage
 * - Manage app preferences
 *
 * Requirements Reference:
 * From section 3.6.3: Backup and Reset Options (Settings Screen)
 * - Reset All Actors: Clear all actor data (also deletes encounters)
 * - Reset All Encounters: Preserves actor library
 * - Set Background Picture: File picker for custom background
 * - Confirmation: Irreversible action confirmation prompts
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(
            (application as CombatTrackerApplication).actorRepository,
            (application as CombatTrackerApplication).encounterRepository,
            (application as CombatTrackerApplication).imageRepository
        )
    }

    /**
     * Background image picker launcher
     */
    private val backgroundPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch {
                viewModel.setBackgroundImage(it)
            }
        }
    }

    // ========== Lifecycle Methods ==========

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViews()
        observeViewModel()

        // Load initial data
        viewModel.loadStorageInfo()

        Timber.d("SettingsActivity created")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
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
            title = "Settings"
        }
    }

    /**
     * Setup all views
     */
    private fun setupViews() {
        setupBackgroundSection()
        setupResetSection()
        setupStorageSection()
        setupAboutSection()
    }

    /**
     * Setup background customization section
     */
    private fun setupBackgroundSection() {
        binding.buttonSetBackground.setOnClickListener {
            backgroundPickerLauncher.launch("image/*")
        }

        binding.buttonRemoveBackground.setOnClickListener {
            confirmRemoveBackground()
        }
    }

    /**
     * Setup reset options section
     */
    private fun setupResetSection() {
        binding.buttonResetActors.setOnClickListener {
            confirmResetActors()
        }

        binding.buttonResetEncounters.setOnClickListener {
            confirmResetEncounters()
        }

        binding.buttonResetAll.setOnClickListener {
            confirmResetAll()
        }
    }

    /**
     * Setup storage information section
     */
    private fun setupStorageSection() {
        binding.buttonClearCache.setOnClickListener {
            confirmClearCache()
        }

        binding.buttonRefreshStorage.setOnClickListener {
            viewModel.loadStorageInfo()
        }
    }

    /**
     * Setup about section
     */
    private fun setupAboutSection() {
        // Set version info
        binding.textVersion.text = "Version ${BuildConfig.VERSION_NAME}"

        binding.buttonLicenses.setOnClickListener {
            // Show open source licenses
            toast("Open source licenses")
        }
    }

    // ========== ViewModel Observation ==========

    /**
     * Observe ViewModel state
     */
    private fun observeViewModel() {
        // Background image state
        viewModel.hasBackgroundImage.observe(this) { hasBackground ->
            binding.buttonRemoveBackground.isEnabled = hasBackground
            binding.textBackgroundStatus.text = if (hasBackground) {
                "Custom background set"
            } else {
                "Using default black background"
            }
        }

        // Storage info
        viewModel.storageInfo.observe(this) { info ->
            updateStorageInfo(info)
        }

        // Actor and encounter counts
        viewModel.actorCount.observe(this) { count ->
            binding.textActorCount.text = "$count actors in library"
        }

        viewModel.encounterCount.observe(this) { count ->
            binding.textEncounterCount.text = "$count saved encounters"
        }

        // Loading state
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibleIf(isLoading)
            updateButtonStates(!isLoading)
        }

        // Success events
        viewModel.backgroundSet.observe(this) { success ->
            if (success) {
                toast("Background image set")
                setResult(RESULT_OK) // Notify MainActivity to reload background
            }
        }

        viewModel.resetComplete.observe(this) { resetType ->
            resetType?.let {
                when (it) {
                    ResetType.ACTORS -> toast("All actors deleted")
                    ResetType.ENCOUNTERS -> toast("All encounters deleted")
                    ResetType.ALL -> toast("All data reset")
                }
                viewModel.clearResetComplete()
            }
        }

        // Error messages
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                showError(it)
                viewModel.clearError()
            }
        }
    }

    /**
     * Update storage information display
     */
    private fun updateStorageInfo(info: StorageInfo) {
        binding.textTotalStorage.text = "Total: ${formatBytes(info.totalBytes)}"
        binding.textPortraitStorage.text = "Portraits: ${formatBytes(info.portraitBytes)}"
        binding.textBackgroundStorage.text = "Backgrounds: ${formatBytes(info.backgroundBytes)}"
        binding.textDatabaseStorage.text = "Database: ${formatBytes(info.databaseBytes)}"

        // Update cache info
        binding.textCacheSize.text = "Cache: ${formatBytes(info.cacheBytes)}"
        binding.buttonClearCache.isEnabled = info.cacheBytes > 0
    }

    /**
     * Update button enabled states
     */
    private fun updateButtonStates(enabled: Boolean) {
        binding.buttonSetBackground.isEnabled = enabled
        binding.buttonRemoveBackground.isEnabled = enabled && (viewModel.hasBackgroundImage.value == true)
        binding.buttonResetActors.isEnabled = enabled
        binding.buttonResetEncounters.isEnabled = enabled
        binding.buttonResetAll.isEnabled = enabled
        binding.buttonClearCache.isEnabled = enabled
    }

    // ========== Confirmation Dialogs ==========

    /**
     * Confirm background removal
     */
    private fun confirmRemoveBackground() {
        AlertDialog.Builder(this)
            .setTitle("Remove Background?")
            .setMessage("Remove custom background and use default black?")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    viewModel.removeBackgroundImage()
                    setResult(RESULT_OK)
                }
            }
            .setNegativeButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    /**
     * Confirm reset actors
     */
    private fun confirmResetActors() {
        AlertDialog.Builder(this)
            .setTitle(Constants.Dialogs.RESET_ACTORS_TITLE)
            .setMessage(Constants.Dialogs.RESET_ACTORS_MESSAGE)
            .setPositiveButton("Reset") { _, _ ->
                lifecycleScope.launch {
                    viewModel.resetActors()
                }
            }
            .setNegativeButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    /**
     * Confirm reset encounters
     */
    private fun confirmResetEncounters() {
        AlertDialog.Builder(this)
            .setTitle(Constants.Dialogs.RESET_ENCOUNTERS_TITLE)
            .setMessage(Constants.Dialogs.RESET_ENCOUNTERS_MESSAGE)
            .setPositiveButton("Reset") { _, _ ->
                lifecycleScope.launch {
                    viewModel.resetEncounters()
                }
            }
            .setNegativeButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    /**
     * Confirm reset all data
     */
    private fun confirmResetAll() {
        AlertDialog.Builder(this)
            .setTitle("Reset All Data?")
            .setMessage("This will delete ALL actors and encounters. This cannot be undone!")
            .setPositiveButton("Reset All") { _, _ ->
                // Double confirmation for complete reset
                AlertDialog.Builder(this)
                    .setTitle("Are you absolutely sure?")
                    .setMessage("This will permanently delete all your data.")
                    .setPositiveButton("Yes, Reset Everything") { _, _ ->
                        lifecycleScope.launch {
                            viewModel.resetAll()
                        }
                    }
                    .setNegativeButton(Constants.Dialogs.BUTTON_CANCEL, null)
                    .show()
            }
            .setNegativeButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    /**
     * Confirm cache clear
     */
    private fun confirmClearCache() {
        AlertDialog.Builder(this)
            .setTitle("Clear Cache?")
            .setMessage("This will clear temporary files and may improve performance.")
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch {
                    viewModel.clearCache()
                    toast("Cache cleared")
                }
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

    /**
     * Format bytes to human-readable string
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024))
        }
    }
}

// ========== Data Classes ==========

/**
 * Storage information
 */
data class StorageInfo(
    val totalBytes: Long = 0,
    val portraitBytes: Long = 0,
    val backgroundBytes: Long = 0,
    val databaseBytes: Long = 0,
    val cacheBytes: Long = 0
)

/**
 * Reset type for completion events
 */
enum class ResetType {
    ACTORS,
    ENCOUNTERS,
    ALL
}

/**
 * Placeholder for BuildConfig
 */
private object BuildConfig {
    const val VERSION_NAME = "1.0.0"
}
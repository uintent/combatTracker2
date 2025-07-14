// File: ActorEditActivity.kt
// Location: app/src/main/java/com/example/combattracker/ui/actors/ActorEditActivity.kt

package com.example.combattracker.ui.actors

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.combattracker.CombatTrackerApplication
import com.example.combattracker.R
import com.example.combattracker.data.model.ActorCategory
import com.example.combattracker.databinding.ActivityActorEditBinding
import com.example.combattracker.utils.*
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import timber.log.Timber
import com.example.combattracker.utils.visibleIf
import android.provider.MediaStore

/**
 * ActorEditActivity - Create or edit an actor
 *
 * Purpose:
 * - Create new actors with name, category, initiative modifier, and portrait
 * - Edit existing actors
 * - Handle portrait selection from device storage
 * - Validate input before saving
 *
 * Requirements Reference:
 * From section 3.1.2: Actor Creation and Editing
 * - Users can create new actors with all required fields
 * - Users can edit existing actors
 * - Portrait: Image from device storage (optional, uses standard Android photo picker)
 * - Actor portraits copied to app's internal storage when selected
 */
class ActorEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActorEditBinding

    private val viewModel: ActorEditViewModel by viewModels {
        ActorEditViewModel.Factory(
            (application as CombatTrackerApplication).actorRepository,
            intent.getLongExtra(Constants.Extras.ACTOR_ID, -1L)
        )
    }

    private fun getPlaceholderForCategory(category: ActorCategory?): Int {
        return when (category ?: ActorCategory.OTHER) {
            ActorCategory.PLAYER -> R.drawable.placeholder_player
            ActorCategory.NPC -> R.drawable.placeholder_npc
            ActorCategory.MONSTER -> R.drawable.placeholder_monster
            ActorCategory.OTHER -> R.drawable.placeholder_other
        }
    }

    /**
     * Portrait image picker launcher
     */
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setPortraitUri(it)
        }
    }

    /**
     * Permission request launcher for older devices
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchImagePicker()
        } else {
            showPermissionDeniedMessage()
        }
    }

    // ========== Lifecycle Methods ==========

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityActorEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViews()
        observeViewModel()

        Timber.d("ActorEditActivity created - Edit mode: ${viewModel.isEditMode}")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_actor_edit, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_save -> {
                saveActor()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (viewModel.hasUnsavedChanges()) {
            showUnsavedChangesDialog()
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
            title = if (viewModel.isEditMode) "Edit Actor" else "Create Actor"
        }
    }

    /**
     * Setup all views
     */
    private fun setupViews() {
        setupNameInput()
        setupCategorySpinner()
        setupInitiativeInput()
        setupPortraitPicker()
    }

    /**
     * Setup name input field
     */
    private fun setupNameInput() {
        binding.editTextName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.setName(s?.toString() ?: "")
                validateName()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    /**
     * Setup category spinner
     */
    private fun setupCategorySpinner() {
        val categories = ActorCategory.values()
        val categoryNames = categories.map { it.displayName }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        binding.spinnerCategory.adapter = adapter
        binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.setCategory(categories[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * Setup initiative modifier input
     */
    private fun setupInitiativeInput() {
        binding.editTextInitiative.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString()?.toIntOrNull() ?: 0
                viewModel.setInitiativeModifier(value)
                validateInitiative()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }


    // ========== ViewModel Observation ==========

    /**
     * Observe ViewModel state
     */
    private fun observeViewModel() {
        // Observe actor data
        viewModel.actorName.observe(this) { name ->
            if (binding.editTextName.text.toString() != name) {
                binding.editTextName.setText(name)
            }
        }

        viewModel.actorCategory.observe(this) { category ->
            val position = ActorCategory.values().indexOf(category)
            if (position >= 0 && binding.spinnerCategory.selectedItemPosition != position) {
                binding.spinnerCategory.setSelection(position)
            }
        }

        viewModel.initiativeModifier.observe(this) { modifier ->
            val text = modifier.toString()
            if (binding.editTextInitiative.text.toString() != text) {
                binding.editTextInitiative.setText(text)
            }
        }

        viewModel.portraitUri.observe(this) { uri ->
            updatePortraitDisplay(uri)
        }

        viewModel.currentPortraitPath.observe(this) { path ->
            if (viewModel.portraitUri.value == null) {
                updatePortraitFromPath(path)
            }
        }

        // Observe UI state
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibleIf(isLoading)
            binding.scrollView.visibleIf(!isLoading)
            invalidateOptionsMenu() // Update save button
        }

        viewModel.saveComplete.observe(this) { success ->
            if (success) {
                setResult(RESULT_OK)
                finish()
            }
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                showError(it)
                viewModel.clearError()
            }
        }
    }

    // ========== Image Handling ==========

    /**
     * Check permission and pick image
     */
    private fun checkPermissionAndPickImage() {
        // Check if we need READ_EXTERNAL_STORAGE permission (for older Android versions)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                return
            }
        }

        launchImagePicker()
    }

    /**
     * Launch image picker
     */
    private fun launchImagePicker() {
        imagePickerLauncher.launch("image/*")
    }

    /**
     * Update portrait display from URI
     */
    private fun updatePortraitDisplay(uri: Uri?) {
        if (uri != null) {
            binding.imagePortrait.setImageURI(uri)
            binding.imagePortrait.visible()
            binding.textNoPortrait.gone()
            binding.buttonRemovePortrait.visible()
        } else {
            binding.imagePortrait.gone()
            binding.textNoPortrait.visible()
            binding.buttonRemovePortrait.gone()
        }
    }

    /**
     * Update portrait display from file path
     */
    private fun updatePortraitFromPath(path: String?) {
        if (path != null) {
            binding.imagePortrait.loadFromInternalStorage(path)
            binding.imagePortrait.visible()
            binding.textNoPortrait.gone()
            binding.buttonRemovePortrait.visible()
        } else {
            binding.imagePortrait.gone()
            binding.textNoPortrait.visible()
            binding.buttonRemovePortrait.gone()
        }
    }

    // ========== Validation ==========

    /**
     * Validate name input
     */
    private fun validateName(): Boolean {
        val name = binding.editTextName.text.toString().trim()
        return when {
            name.isEmpty() -> {
                binding.textInputLayoutName.error = Constants.Errors.ACTOR_NAME_EMPTY
                false
            }
            name.length > Constants.Validation.MAX_NAME_LENGTH -> {
                binding.textInputLayoutName.error = String.format(
                    Constants.Errors.ACTOR_NAME_TOO_LONG,
                    Constants.Validation.MAX_NAME_LENGTH
                )
                false
            }

            else -> {
                binding.textInputLayoutName.error = null
                true
            }
        }
    }

    /**
     * Validate initiative modifier
     */
    private fun validateInitiative(): Boolean {
        val text = binding.editTextInitiative.text.toString()
        val value = text.toIntOrNull()

        return when {
            value == null -> {
                binding.textInputLayoutInitiative.error = "Must be a number"
                false
            }
            value !in Constants.Validation.MIN_MODIFIER..Constants.Validation.MAX_MODIFIER -> {
                binding.textInputLayoutInitiative.error = "Must be between ${Constants.Validation.MIN_MODIFIER} and ${Constants.Validation.MAX_MODIFIER}"
                false
            }
            else -> {
                binding.textInputLayoutInitiative.error = null
                true
            }
        }
    }

    // ========== Actions ==========

    /**
     * Save the actor
     */
    private fun saveActor() {
        // Validate all fields
        val isNameValid = validateName()
        val isInitiativeValid = validateInitiative()

        if (isNameValid && isInitiativeValid) {
            lifecycleScope.launch {
                viewModel.saveActor()
            }
        }
    }

    /**
     * Show unsaved changes dialog
     */
    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. Do you want to save before leaving?")
            .setPositiveButton("Save") { _, _ ->
                saveActor()
            }
            .setNegativeButton("Discard") { _, _ ->
                finish()
            }
            .setNeutralButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    /**
     * Show permission denied message
     */
    private fun showPermissionDeniedMessage() {
        toast("Permission required to select images")
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

    private fun setupPortraitPicker() {
        binding.buttonSelectPortrait.setOnClickListener {
            // Launch image picker
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, Constants.RequestCodes.PICK_IMAGE)
        }

        binding.buttonRemovePortrait.setOnClickListener {
            // Remove portrait
            viewModel.removePortrait()
            // Get current category from the spinner
            val selectedPosition = binding.spinnerCategory.selectedItemPosition
            val category = if (selectedPosition >= 0) {
                ActorCategory.values()[selectedPosition]
            } else {
                ActorCategory.OTHER
            }
            binding.imagePortrait.setImageResource(getPlaceholderForCategory(category))
            binding.buttonRemovePortrait.gone()
        }
    }

    private fun getPlaceholderForCategory(category: ActorCategory?): Int {
        return when (category ?: ActorCategory.OTHER) {
            ActorCategory.PLAYER -> R.drawable.placeholder_player
            ActorCategory.NPC -> R.drawable.placeholder_npc
            ActorCategory.MONSTER -> R.drawable.placeholder_monster
            ActorCategory.OTHER -> R.drawable.placeholder_other
        }
    }

}
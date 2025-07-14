// File: ActorEditViewModel.kt
// Location: app/src/main/java/com/example/combattracker/ui/actors/ActorEditViewModel.kt

package com.example.combattracker.ui.actors

import android.net.Uri
import androidx.lifecycle.*
import com.example.combattracker.data.database.entities.Actor
import com.example.combattracker.data.model.ActorCategory
import com.example.combattracker.data.repository.ActorRepository
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ActorEditViewModel - ViewModel for creating/editing actors
 *
 * Purpose:
 * - Manages actor creation and editing logic
 * - Handles portrait selection and validation
 * - Tracks unsaved changes
 * - Provides save functionality
 *
 * Requirements Reference:
 * From section 3.1.2: Actor Creation and Editing
 * - Create new actors with all required fields
 * - Edit existing actors
 * - Validate input data
 */
class ActorEditViewModel(
    private val actorRepository: ActorRepository,
    private val actorId: Long
) : ViewModel() {

    // ========== State Properties ==========

    /**
     * Whether we're editing an existing actor (vs creating new)
     */
    val isEditMode: Boolean = actorId != -1L

    /**
     * Current actor being edited (if in edit mode)
     */
    private var originalActor: Actor? = null

    // ========== Observable Properties ==========

    private val _actorName = MutableLiveData("")
    val actorName: LiveData<String> = _actorName

    private val _actorCategory = MutableLiveData(ActorCategory.MONSTER)
    val actorCategory: LiveData<ActorCategory> = _actorCategory

    private val _initiativeModifier = MutableLiveData(0)
    val initiativeModifier: LiveData<Int> = _initiativeModifier

    private val _portraitUri = MutableLiveData<Uri?>()
    val portraitUri: LiveData<Uri?> = _portraitUri

    private val _currentPortraitPath = MutableLiveData<String?>()
    val currentPortraitPath: LiveData<String?> = _currentPortraitPath

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _saveComplete = MutableLiveData(false)
    val saveComplete: LiveData<Boolean> = _saveComplete

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // ========== Initialization ==========

    init {
        if (isEditMode) {
            loadActor()
        }
    }

    /**
     * Load existing actor for editing
     */
    private fun loadActor() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val actor = actorRepository.getActorById(actorId)
                if (actor != null) {
                    originalActor = actor
                    _actorName.value = actor.name
                    _actorCategory.value = actor.getActorCategory()
                    _initiativeModifier.value = actor.initiativeModifier
                    _currentPortraitPath.value = actor.portraitPath
                    Timber.d("Loaded actor for editing: ${actor.name}")
                } else {
                    _errorMessage.value = "Actor not found"
                    Timber.e("Actor not found with ID: $actorId")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load actor"
                Timber.e(e, "Failed to load actor")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ========== Public Methods ==========

    /**
     * Set actor name
     */
    fun setName(name: String) {
        _actorName.value = name
    }

    /**
     * Set actor category
     */
    fun setCategory(category: ActorCategory) {
        _actorCategory.value = category
    }

    /**
     * Set initiative modifier
     */
    fun setInitiativeModifier(modifier: Int) {
        _initiativeModifier.value = modifier
    }

    /**
     * Set portrait URI (for new image selection)
     */
    fun setPortraitUri(uri: Uri) {
        _portraitUri.value = uri
        _currentPortraitPath.value = null // Clear old path when new image selected
    }

    /**
     * Remove portrait
     */
    fun removePortrait() {
        _portraitUri.value = null
        _currentPortraitPath.value = null
    }

    /**
     * Check if there are unsaved changes
     */
    fun hasUnsavedChanges(): Boolean {
        if (!isEditMode) {
            // For new actors, check if any data has been entered
            return _actorName.value?.isNotEmpty() == true ||
                    _initiativeModifier.value != 0 ||
                    _portraitUri.value != null
        } else {
            // For existing actors, compare with original
            val original = originalActor ?: return false
            return _actorName.value != original.name ||
                    _actorCategory.value != original.getActorCategory() ||
                    _initiativeModifier.value != original.initiativeModifier ||
                    _portraitUri.value != null || // New image selected
                    (_currentPortraitPath.value == null && original.portraitPath != null) // Portrait removed
        }
    }

    /**
     * Save the actor
     */
    suspend fun saveActor() {
        _isLoading.value = true
        _errorMessage.value = null

        try {
            val name = _actorName.value?.trim() ?: ""
            val category = _actorCategory.value ?: ActorCategory.MONSTER
            val modifier = _initiativeModifier.value ?: 0
            val portraitUri = _portraitUri.value

            val result = if (isEditMode) {
                // Update existing actor
                val actor = originalActor!!.copy(
                    name = name,
                    category = category.name,
                    initiativeModifier = modifier,
                    portraitPath = if (portraitUri != null) null else _currentPortraitPath.value
                )
                actorRepository.updateActor(actor, portraitUri)
            } else {
                // Create new actor
                actorRepository.createActor(name, category, modifier, portraitUri)
                    .map { Unit } // Convert Result<Long> to Result<Unit>
            }

            result.fold(
                onSuccess = {
                    Timber.d("Actor saved successfully")
                    _saveComplete.value = true
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Failed to save actor"
                    Timber.e(error, "Failed to save actor")
                }
            )
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "An unexpected error occurred"
            Timber.e(e, "Unexpected error saving actor")
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    // ========== Factory ==========

    /**
     * Factory for creating ActorEditViewModel with dependencies
     */
    class Factory(
        private val actorRepository: ActorRepository,
        private val actorId: Long
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ActorEditViewModel::class.java)) {
                return ActorEditViewModel(actorRepository, actorId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
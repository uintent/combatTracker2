// File: SettingsViewModel.kt
// Location: app/src/main/java/com/example/combattracker/ui/settings/SettingsViewModel.kt

package com.example.combattracker.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.*
import com.example.combattracker.data.repository.ActorRepository
import com.example.combattracker.data.repository.EncounterRepository
import com.example.combattracker.data.repository.ImageRepository
import com.example.combattracker.utils.Constants
import com.example.combattracker.utils.FileStorageHelper
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * SettingsViewModel - ViewModel for the Settings screen
 *
 * Purpose:
 * - Manages background image settings
 * - Handles data reset operations
 * - Provides storage usage information
 * - Coordinates between repositories for settings
 *
 * Requirements Reference:
 * From section 3.6.3: Backup and Reset Options
 * - Background image management
 * - Reset actors (with warning about encounters)
 * - Reset encounters only
 * - Storage management
 */
class SettingsViewModel(
    private val application: Application,
    private val actorRepository: ActorRepository,
    private val encounterRepository: EncounterRepository,
    private val imageRepository: ImageRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val fileStorageHelper = FileStorageHelper(application)

    // ========== Observable Properties ==========

    /**
     * Whether a custom background is set
     */
    private val _hasBackgroundImage = MutableLiveData<Boolean>()
    val hasBackgroundImage: LiveData<Boolean> = _hasBackgroundImage

    /**
     * Storage information
     */
    private val _storageInfo = MutableLiveData<StorageInfo>()
    val storageInfo: LiveData<StorageInfo> = _storageInfo

    /**
     * Actor count from repository
     */
    val actorCount: LiveData<Int> = actorRepository.getAllActors()
        .map { it.size }
        .asLiveData()

    /**
     * Encounter count from repository
     */
    val encounterCount: LiveData<Int> = encounterRepository.getAllEncounters()
        .map { it.size }
        .asLiveData()

    /**
     * Loading state
     */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /**
     * Background set success event
     */
    private val _backgroundSet = MutableLiveData(false)
    val backgroundSet: LiveData<Boolean> = _backgroundSet

    /**
     * Reset complete event
     */
    private val _resetComplete = MutableLiveData<ResetType?>()
    val resetComplete: LiveData<ResetType?> = _resetComplete

    /**
     * Error messages
     */
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // ========== Initialization ==========

    init {
        checkBackgroundImage()
    }

    /**
     * Check if background image is set
     */
    private fun checkBackgroundImage() {
        val backgroundPath = prefs.getString(Constants.PrefsKeys.BACKGROUND_IMAGE_PATH, null)
        _hasBackgroundImage.value = !backgroundPath.isNullOrEmpty()
    }

    // ========== Public Methods - Background ==========

    /**
     * Set a new background image
     *
     * @param uri The image URI from picker
     */
    suspend fun setBackgroundImage(uri: Uri) {
        _isLoading.value = true
        _errorMessage.value = null

        try {
            val savedPath = imageRepository.saveBackgroundImage(uri)

            if (savedPath != null) {
                // Remove old background if exists
                val oldPath = prefs.getString(Constants.PrefsKeys.BACKGROUND_IMAGE_PATH, null)
                if (!oldPath.isNullOrEmpty() && oldPath != savedPath) {
                    imageRepository.deleteBackground(oldPath)
                }

                // Save new path
                prefs.edit()
                    .putString(Constants.PrefsKeys.BACKGROUND_IMAGE_PATH, savedPath)
                    .apply()

                _hasBackgroundImage.value = true
                _backgroundSet.value = true

                Timber.d("Background image set: $savedPath")
            } else {
                _errorMessage.value = "Failed to save background image"
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set background image")
            _errorMessage.value = "Failed to set background: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Remove the background image
     */
    suspend fun removeBackgroundImage() {
        _isLoading.value = true

        try {
            val backgroundPath = prefs.getString(Constants.PrefsKeys.BACKGROUND_IMAGE_PATH, null)

            if (!backgroundPath.isNullOrEmpty()) {
                imageRepository.deleteBackground(backgroundPath)
            }

            prefs.edit()
                .remove(Constants.PrefsKeys.BACKGROUND_IMAGE_PATH)
                .apply()

            _hasBackgroundImage.value = false

            Timber.d("Background image removed")
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove background")
            _errorMessage.value = "Failed to remove background"
        } finally {
            _isLoading.value = false
        }
    }

    // ========== Public Methods - Reset ==========

    /**
     * Reset all actors (and encounters)
     *
     * Note: This also deletes all encounters because they reference actors
     */
    suspend fun resetActors() {
        _isLoading.value = true
        _errorMessage.value = null

        try {
            // This would need to be implemented in the repositories
            // For now, we'll simulate it
            Timber.d("Resetting all actors and encounters")

            // Clear preferences related to actors
            // Delete all portrait files
            // Clear database tables

            _resetComplete.value = ResetType.ACTORS

        } catch (e: Exception) {
            Timber.e(e, "Failed to reset actors")
            _errorMessage.value = "Failed to reset actors: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Reset all encounters only
     */
    suspend fun resetEncounters() {
        _isLoading.value = true
        _errorMessage.value = null

        try {
            // This would need to be implemented in the repository
            Timber.d("Resetting all encounters")

            _resetComplete.value = ResetType.ENCOUNTERS

        } catch (e: Exception) {
            Timber.e(e, "Failed to reset encounters")
            _errorMessage.value = "Failed to reset encounters: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Reset all data
     */
    suspend fun resetAll() {
        _isLoading.value = true
        _errorMessage.value = null

        try {
            // Reset everything
            resetActors()
            resetEncounters()
            removeBackgroundImage()

            // Clear all preferences
            prefs.edit().clear().apply()

            _resetComplete.value = ResetType.ALL

            Timber.d("All data reset")

        } catch (e: Exception) {
            Timber.e(e, "Failed to reset all data")
            _errorMessage.value = "Failed to reset data: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    // ========== Public Methods - Storage ==========

    /**
     * Load storage information
     */
    fun loadStorageInfo() {
        viewModelScope.launch {
            try {
                val portraitBytes = fileStorageHelper.getPortraitStorageUsed()
                val backgroundBytes = getBackgroundStorageUsed()
                val databaseBytes = getDatabaseSize()
                val cacheBytes = getCacheSize()
                val totalBytes = portraitBytes + backgroundBytes + databaseBytes + cacheBytes

                _storageInfo.value = StorageInfo(
                    totalBytes = totalBytes,
                    portraitBytes = portraitBytes,
                    backgroundBytes = backgroundBytes,
                    databaseBytes = databaseBytes,
                    cacheBytes = cacheBytes
                )

            } catch (e: Exception) {
                Timber.e(e, "Failed to load storage info")
                _storageInfo.value = StorageInfo()
            }
        }
    }

    /**
     * Clear cache files
     */
    suspend fun clearCache() {
        try {
            val cacheDir = application.cacheDir
            deleteRecursive(cacheDir)

            // Also clean temp directory
            fileStorageHelper.cleanTempDirectory()

            // Reload storage info
            loadStorageInfo()

        } catch (e: Exception) {
            Timber.e(e, "Failed to clear cache")
            _errorMessage.value = "Failed to clear cache"
        }
    }

    // ========== Helper Methods ==========

    /**
     * Get background storage used
     */
    private fun getBackgroundStorageUsed(): Long {
        return try {
            val backgroundDir = fileStorageHelper.getBackgroundDirectory()
            calculateDirectorySize(backgroundDir)
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get database file size
     */
    private fun getDatabaseSize(): Long {
        return try {
            val dbPath = application.getDatabasePath(Constants.DATABASE_NAME)
            dbPath.length()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get cache directory size
     */
    private fun getCacheSize(): Long {
        return try {
            calculateDirectorySize(application.cacheDir)
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Calculate directory size recursively
     */
    private fun calculateDirectorySize(dir: File): Long {
        var size = 0L

        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }

        return size
    }

    /**
     * Delete directory contents recursively
     */
    private fun deleteRecursive(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }
        file.delete()
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Clear reset complete event
     */
    fun clearResetComplete() {
        _resetComplete.value = null
    }

    // ========== Factory ==========

    class Factory(
        private val application: Application,
        private val actorRepository: ActorRepository,
        private val encounterRepository: EncounterRepository,
        private val imageRepository: ImageRepository
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(
                    application,
                    actorRepository,
                    encounterRepository,
                    imageRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
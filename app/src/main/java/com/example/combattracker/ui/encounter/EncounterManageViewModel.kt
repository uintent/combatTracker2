// File: EncounterManageViewModel.kt
// Location: app/src/main/java/com/example/combattracker/ui/encounter/EncounterManageViewModel.kt

package com.example.combattracker.ui.encounter

import androidx.lifecycle.*
import com.example.combattracker.data.database.dao.EncounterWithCount
import com.example.combattracker.data.repository.EncounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * EncounterManageViewModel - ViewModel for the Encounter Management screen
 *
 * Purpose:
 * - Provides list of saved encounters
 * - Handles encounter deletion
 * - Manages loading state and errors
 *
 * Requirements Reference:
 * From section 3.3.2: Encounter List Management
 * - Shows detailed list with encounter name + date/time + actor count
 * - Delete Encounter: Single encounter deletion
 */
class EncounterManageViewModel(
    private val encounterRepository: EncounterRepository
) : ViewModel() {

    // ========== Observable Properties ==========

    /**
     * Flow of all encounters with actor counts
     */
    val encounters: Flow<List<EncounterWithCount>> = encounterRepository
        .getAllEncounters()
        .catch { exception ->
            Timber.e(exception, "Error loading encounters")
            _errorMessage.postValue("Failed to load encounters")
            emit(emptyList())
        }

    /**
     * Loading state
     */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /**
     * Error messages
     */
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // ========== Public Methods ==========

    /**
     * Delete an encounter
     *
     * @param encounterId ID of encounter to delete
     * @return Result of deletion
     */
    suspend fun deleteEncounter(encounterId: Long): Result<Unit> {
        _isLoading.value = true
        return try {
            val result = encounterRepository.deleteEncounter(encounterId)
            _isLoading.value = false

            if (result.isSuccess) {
                Timber.d("Encounter deleted successfully: $encounterId")
            }

            result
        } catch (e: Exception) {
            _isLoading.value = false
            Timber.e(e, "Failed to delete encounter")
            Result.failure(e)
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
     * Factory for creating EncounterManageViewModel with dependencies
     */
    class Factory(
        private val encounterRepository: EncounterRepository
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EncounterManageViewModel::class.java)) {
                return EncounterManageViewModel(encounterRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
// File: ActorContextMenuFragment.kt
// Location: app/src/main/java/com/example/combattracker/ui/combat/ActorContextMenuFragment.kt

package com.example.combattracker.ui.combat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import com.example.combattracker.R
import com.example.combattracker.databinding.BottomSheetActorContextBinding
import com.example.combattracker.utils.Constants
import com.example.combattracker.utils.InitiativeCalculator
import com.example.combattracker.utils.formatInitiative
import com.example.combattracker.utils.toast
import com.example.combattracker.utils.visibleIf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import timber.log.Timber

/**
 * ActorContextMenuFragment - Bottom sheet for actor actions during combat
 *
 * Purpose:
 * - Edit actor initiative
 * - Open conditions dialog
 * - Remove actor from encounter
 *
 * Requirements Reference:
 * From section 3.4.1: Context Menu Access
 * - Trigger: Tap on actor portrait during encounter
 * - Interface: Bottom sheet sliding up from bottom (max 45% screen height, 90% screen width)
 * From section 3.4.2: Context Menu Sections
 * - Initiative Section: Current value display, edit field, Move Left/Right buttons
 * - Conditions Section: Opens full-screen dialog
 * - Actor Actions: Remove actor from encounter
 */
class ActorContextMenuFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetActorContextBinding? = null
    private val binding get() = _binding!!

    private val combatViewModel: CombatViewModel by activityViewModels()

    private var actorId: Long = -1L
    private var currentActor: EncounterActorState? = null

    // Track the current initiative value to avoid redundant updates
    private var currentInitiativeValue: Double? = null

    companion object {
        private const val ARG_ACTOR_ID = "actor_id"

        // Result keys
        const val RESULT_INITIATIVE_CHANGED = "initiative_changed"
        const val RESULT_ACTOR_REMOVED = "actor_removed"
        const val RESULT_CLOSE_REQUESTED = "close_requested"

        fun newInstance(actorId: Long): ActorContextMenuFragment {
            return ActorContextMenuFragment().apply {
                arguments = bundleOf(ARG_ACTOR_ID to actorId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actorId = arguments?.getLong(ARG_ACTOR_ID, -1L) ?: -1L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetActorContextBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set bottom sheet height (45% of screen)
        view.layoutParams.height = (resources.displayMetrics.heightPixels * 0.45).toInt()

        observeViewModel()
        setupViews()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ========== Setup ==========

    private fun observeViewModel() {
        combatViewModel.combatState.observe(viewLifecycleOwner) { state ->
            currentActor = state.actors.find { it.id == actorId }
            currentActor?.let { updateUI(it) }
        }
    }

    private fun setupViews() {
        // IMPORTANT: Set up close button to send result instead of dismiss
        binding.buttonClose.setOnClickListener {
            Timber.d("Close button clicked - sending RESULT_CLOSE_REQUESTED")
            // Send result to activity to handle the close properly
            setFragmentResult(RESULT_CLOSE_REQUESTED, bundleOf())
            // Don't call dismiss() here - let the activity handle it
        }

        setupInitiativeSection()
        setupConditionsButton()
        setupActorActions()
    }

    /**
     * Setup initiative editing section
     */
    private fun setupInitiativeSection() {
        // Set initiative button
        binding.buttonSetInitiative.setOnClickListener {
            setInitiative()
        }

        // Move buttons (only for tied players)
        binding.buttonMoveLeft.setOnClickListener {
            // TODO: Implement tie-breaking movement
            requireContext().toast("Move left not yet implemented")
        }

        binding.buttonMoveRight.setOnClickListener {
            // TODO: Implement tie-breaking movement
            requireContext().toast("Move right not yet implemented")
        }
    }

    /**
     * Setup conditions button
     */
    private fun setupConditionsButton() {
        binding.buttonOpenConditions.setOnClickListener {
            Timber.d("Opening conditions dialog")
            currentActor?.let { actor ->
                Timber.d("Current actor: ${actor.displayName} (ID: ${actor.id})")
                try {
                    val dialog = ConditionsDialogFragment.newInstance(actor.id, actor.displayName)
                    dialog.show(parentFragmentManager, "conditions_dialog")
                    Timber.d("Dialog show() called successfully")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to show conditions dialog: ${e.message}")
                    e.printStackTrace()
                }
            } ?: run {
                Timber.e("No current actor when trying to open conditions dialog")
            }
        }
    }

    /**
     * Setup actor-specific actions
     */
    private fun setupActorActions() {
        binding.buttonRemoveActor.setOnClickListener {
            confirmRemoveActor()
        }
    }

    // ========== UI Updates ==========

    private fun updateUI(actor: EncounterActorState) {
        // Update header
        binding.textActorName.text = actor.displayName

        // Store current initiative value
        currentInitiativeValue = actor.initiative

        // Update initiative display - DON'T set the input field value here
        // to avoid interfering with user input
        binding.textCurrentInitiative.text = if (actor.initiative != null) {
            "Current: ${formatInitiative(actor.initiative, showDecimals = true)}"
        } else {
            getString(R.string.current_x, getString(R.string.initiative_not_set))
        }

        // Update the hint to show current value
        binding.editTextInitiative.hint = if (actor.initiative != null) {
            formatInitiative(actor.initiative, showDecimals = true)
        } else {
            "---"
        }

        // Show/hide move buttons based on tie status
        val canReorder = actor.initiative != null &&
                InitiativeCalculator.isPlayerInitiative(actor.initiative) &&
                checkIfTied(actor)

        binding.buttonMoveLeft.visibleIf(canReorder)
        binding.buttonMoveRight.visibleIf(canReorder)
    }

    // ========== Actions ==========

    private fun setInitiative() {
        val initiativeText = binding.editTextInitiative.text.toString().trim()

        // If empty, do nothing
        if (initiativeText.isEmpty()) {
            requireContext().toast("Please enter an initiative value")
            return
        }

        val initiative = initiativeText.toDoubleOrNull()

        if (initiative == null) {
            binding.textInputLayoutInitiative.error = "Invalid initiative value"
            return
        }

        // Clear any error
        binding.textInputLayoutInitiative.error = null

        // Update the initiative
        combatViewModel.setActorInitiative(actorId, initiative)

        // Notify parent
        setFragmentResult(RESULT_INITIATIVE_CHANGED, bundleOf(
            "actor_id" to actorId,
            "initiative" to initiative
        ))

        // Show success message
        requireContext().toast("Initiative set to $initiative")

        // Clear the input field after successful update
        binding.editTextInitiative.setText("")

        // Hide keyboard
        binding.editTextInitiative.clearFocus()
    }

    private fun confirmRemoveActor() {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Actor?")
            .setMessage("Remove ${currentActor?.displayName} from the encounter?")
            .setPositiveButton("Remove") { _, _ ->
                combatViewModel.removeActor(actorId)
                setFragmentResult(RESULT_ACTOR_REMOVED, bundleOf("actor_id" to actorId))
                dismiss()
            }
            .setNegativeButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    // ========== Helpers ==========

    private fun checkIfTied(actor: EncounterActorState): Boolean {
        // Check if this actor is tied with others
        val allActors = combatViewModel.combatState.value?.actors ?: return false
        return allActors.any { other ->
            other.id != actor.id &&
                    other.initiative == actor.initiative
        }
    }

    /**
     * Update the fragment to display a different actor
     * This allows reusing the same fragment instance when switching between actors
     */
    fun updateActor(newActorId: Long) {
        // Update to the new actor
        actorId = newActorId

        // Find the new actor in the current state
        val state = combatViewModel.combatState.value
        currentActor = state?.actors?.find { it.id == actorId }

        // Update the UI with the new actor's data
        currentActor?.let { updateUI(it) }

        // Clear any input fields
        binding.editTextInitiative.setText("")
        binding.textInputLayoutInitiative.error = null

        Timber.d("Updated bottom sheet to show actor: $actorId")
    }
}
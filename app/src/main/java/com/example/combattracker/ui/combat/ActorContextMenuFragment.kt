// File: ActorContextMenuFragment.kt
// Location: app/src/main/java/com/example/combattracker/ui/combat/ActorContextMenuFragment.kt

package com.example.combattracker.ui.combat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import com.example.combattracker.R
import com.example.combattracker.data.model.ConditionType
import com.example.combattracker.databinding.BottomSheetActorContextBinding
import com.example.combattracker.databinding.ItemConditionBinding
import com.example.combattracker.utils.Constants
import com.example.combattracker.utils.InitiativeCalculator
import com.example.combattracker.utils.formatInitiative
import com.example.combattracker.utils.gone
import com.example.combattracker.utils.toast
import com.example.combattracker.utils.visible
import com.example.combattracker.utils.visibleIf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import timber.log.Timber
import com.google.android.material.snackbar.Snackbar
import android.widget.EditText

/**
 * ActorContextMenuFragment - Bottom sheet for actor actions during combat
 *
 * Purpose:
 * - Edit actor initiative
 * - Manage conditions (apply/remove with duration)
 * - Remove actor from encounter
 * - Provide encounter-wide actions (save, load, end)
 *
 * Requirements Reference:
 * From section 3.4.1: Context Menu Access
 * - Trigger: Tap on actor portrait during encounter
 * - Interface: Bottom sheet sliding up from bottom (max 45% screen height, 90% screen width)
 * From section 3.4.2: Context Menu Sections
 * - Initiative Section: Current value display, edit field, Move Left/Right buttons
 * - Conditions Section: All 15 D&D conditions with toggle, count, permanent checkbox
 * - Actor Actions: Remove actor from encounter
 * - Encounter Management: Save, Load, End encounter
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
        const val RESULT_CONDITION_TOGGLED = "condition_toggled"

        // Result keys for encounter management
        const val RESULT_SAVE_ENCOUNTER = "save_encounter"
        const val RESULT_ADD_ACTOR = "add_actor"
        const val RESULT_END_ENCOUNTER = "end_encounter"

        // Add this new constant
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
        setupConditionsSection()
        setupActorActions()
        //setupEncounterActions()
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
     * Setup conditions section
     */
    private fun setupConditionsSection() {
        // Add all 15 conditions
        ConditionType.values().forEach { conditionType ->
            val conditionBinding = ItemConditionBinding.inflate(
                layoutInflater,
                binding.conditionsContainer,
                true
            )

            setupConditionItem(conditionBinding, conditionType)
        }
    }

    /**
     * Setup individual condition item
     */
    private fun setupConditionItem(itemBinding: ItemConditionBinding, conditionType: ConditionType) {
        // Set condition name
        itemBinding.textConditionName.text = conditionType.displayName

        // Set icon
        val iconRes = getConditionIconResource(conditionType)
        itemBinding.imageConditionIcon.setImageResource(iconRes)

        // Store reference to this condition's views for easier access
        itemBinding.root.tag = conditionType

        // Toggle checkbox
        itemBinding.checkboxCondition.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Show duration options
                itemBinding.layoutDuration.visible()
                // Focus on duration field
                itemBinding.editTextDuration.requestFocus()
            } else {
                // Hide duration options and remove condition
                itemBinding.layoutDuration.gone()
                // Clear the input fields when unchecking
                itemBinding.editTextDuration.setText("")
                itemBinding.checkboxPermanent.isChecked = false

                // Only remove condition if it was previously applied
                val activeConditionIds = currentActor?.conditions?.map { it.id }?.toSet() ?: emptySet()
                if (conditionType.id in activeConditionIds) {
                    removeCondition(conditionType)
                }
            }
        }

        // Permanent checkbox
        itemBinding.checkboxPermanent.setOnCheckedChangeListener { _, isPermanent ->
            itemBinding.editTextDuration.isEnabled = !isPermanent
            if (isPermanent) {
                itemBinding.editTextDuration.setText("")
                // Clear any error on the duration field
                itemBinding.editTextDuration.error = null
            }
        }

        // Apply button
        itemBinding.buttonApply.setOnClickListener {
            // Get current state of the controls
            val isPermanent = itemBinding.checkboxPermanent.isChecked
            val durationText = itemBinding.editTextDuration.text.toString().trim()

            applyCondition(
                conditionType,
                isPermanent,
                durationText,
                itemBinding
            )
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

    /**
     * Setup encounter-wide actions
     */
    /*private fun setupEncounterActions() {
        binding.buttonSaveEncounter.setOnClickListener {
            // Notify parent activity to show save dialog
            setFragmentResult(RESULT_SAVE_ENCOUNTER, bundleOf())
            dismiss()
        }

        binding.buttonLoadEncounter.setOnClickListener {
            // Notify parent activity to navigate to encounter list
            setFragmentResult(RESULT_ADD_ACTOR, bundleOf())
            dismiss()
        }

        binding.buttonEndEncounter.setOnClickListener {
            // Notify parent activity to show end encounter dialog
            setFragmentResult(RESULT_END_ENCOUNTER, bundleOf())
            dismiss()
        }
    }*/

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

        // Update conditions
        updateConditionsUI(actor)
    }

    private fun updateConditionsUI(actor: EncounterActorState) {
        // Update condition checkboxes based on active conditions
        val activeConditionIds = actor.conditions.map { it.id }.toSet()

        // Get the full condition details from the view model
        val conditionDetails = combatViewModel.getActorConditionDetails(actor.id)
        val conditionDetailsMap = conditionDetails.associateBy { it.condition.id }

        // Debug logging
        Timber.d("Updating conditions UI for ${actor.displayName}")
        Timber.d("Active condition IDs: $activeConditionIds")
        Timber.d("Active conditions: ${actor.conditions.map { "${it.name} (ID: ${it.id})" }}")

        for (i in 0 until binding.conditionsContainer.childCount) {
            val view = binding.conditionsContainer.getChildAt(i)
            val checkbox = view.findViewById<CheckBox>(R.id.checkboxCondition)
            val layoutDuration = view.findViewById<View>(R.id.layoutDuration)
            val permanentCheckbox = view.findViewById<CheckBox>(R.id.checkboxPermanent)
            val durationEditText = view.findViewById<EditText>(R.id.editTextDuration)
            val conditionType = ConditionType.values()[i]

            // IMPORTANT: Remove the listeners before updating
            checkbox.setOnCheckedChangeListener(null)
            permanentCheckbox.setOnCheckedChangeListener(null)

            // Update checkbox state
            val isActive = conditionType.id in activeConditionIds
            checkbox.isChecked = isActive

            Timber.d("Condition ${conditionType.displayName} (ID: ${conditionType.id}): isActive = $isActive")

            // Show/hide duration layout and set values based on condition state
            if (isActive) {
                layoutDuration.visible()

                // Get the condition details for this specific condition
                val details = conditionDetailsMap[conditionType.id]
                if (details != null) {
                    // Set the permanent checkbox
                    permanentCheckbox.isChecked = details.actorCondition.isPermanent

                    // Set the duration field
                    if (!details.actorCondition.isPermanent && details.actorCondition.remainingDuration != null) {
                        durationEditText.setText(details.actorCondition.remainingDuration.toString())
                        durationEditText.isEnabled = false // Disable editing for existing conditions
                    } else {
                        durationEditText.setText("")
                        durationEditText.isEnabled = false
                    }

                    Timber.d("  - Loaded condition details: permanent=${details.actorCondition.isPermanent}, remaining=${details.actorCondition.remainingDuration}")
                }
            } else {
                layoutDuration.gone()
                // Clear fields when not active
                permanentCheckbox.isChecked = false
                durationEditText.setText("")
                durationEditText.isEnabled = true
            }

            // Re-attach the permanent checkbox listener
            permanentCheckbox.setOnCheckedChangeListener { _, isPermanent ->
                // Only enable/disable if this is a new condition (not already active)
                if (!isActive) {
                    durationEditText.isEnabled = !isPermanent
                    if (isPermanent) {
                        durationEditText.setText("")
                        durationEditText.error = null
                    }
                }
            }

            // Re-attach the checkbox listener after updating
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                // Get the current active state fresh each time - DON'T use the captured isActive variable
                val currentActiveConditionIds = currentActor?.conditions?.map { it.id }?.toSet() ?: emptySet()
                val isCurrentlyActive = conditionType.id in currentActiveConditionIds

                when {
                    isChecked && !isCurrentlyActive -> {
                        // User is checking an unchecked box - show duration options
                        layoutDuration.visible()
                        durationEditText.isEnabled = !permanentCheckbox.isChecked
                        durationEditText.requestFocus()
                    }
                    !isChecked && isCurrentlyActive -> {
                        // User is unchecking a checked box - remove condition
                        layoutDuration.gone()
                        durationEditText.setText("")
                        durationEditText.isEnabled = true
                        permanentCheckbox.isChecked = false
                        removeCondition(conditionType)
                    }
                    isChecked && isCurrentlyActive -> {
                        // User is trying to check an already checked box - just show duration
                        layoutDuration.visible()
                        Timber.d("Condition ${conditionType.displayName} is already active")
                    }
                    !isChecked && !isCurrentlyActive -> {
                        // User is unchecking an already unchecked box - just hide duration
                        layoutDuration.gone()
                        durationEditText.setText("")
                        durationEditText.isEnabled = true
                        permanentCheckbox.isChecked = false
                    }
                }
            }
        }
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

    private fun applyCondition(
        conditionType: ConditionType,
        isPermanent: Boolean,
        durationText: String,
        itemBinding: ItemConditionBinding
    ) {
        Timber.d("Applying condition: ${conditionType.displayName}, permanent: $isPermanent, durationText: '$durationText'")

        // Validate input
        if (!isPermanent && durationText.isEmpty()) {
            Timber.w("Attempted to apply non-permanent condition without duration: ${conditionType.displayName}")
            itemBinding.editTextDuration.error = "Duration required"
            Snackbar.make(binding.root, "Please enter a duration or select Permanent", Snackbar.LENGTH_SHORT).show()
            return
        }

        val duration = if (isPermanent) {
            null
        } else {
            val parsedDuration = durationText.toIntOrNull()
            if (parsedDuration == null || parsedDuration <= 0) {
                itemBinding.editTextDuration.error = "Invalid duration"
                Snackbar.make(binding.root, "Duration must be a positive number", Snackbar.LENGTH_SHORT).show()
                return
            }
            parsedDuration
        }

        // Clear any errors
        itemBinding.editTextDuration.error = null

        // Apply the condition
        combatViewModel.toggleCondition(actorId, conditionType, isPermanent, duration)

        // Clear the duration field after successful application
        itemBinding.editTextDuration.setText("")

        // Show success message
        val message = if (isPermanent) {
            "${conditionType.displayName} applied (Permanent)"
        } else {
            "${conditionType.displayName} applied for $duration turns"
        }
        requireContext().toast(message)

        // Notify parent
        setFragmentResult(RESULT_CONDITION_TOGGLED, bundleOf(
            "actor_id" to actorId,
            "condition_id" to conditionType.id,
            "applied" to true
        ))

        Timber.d("Condition applied successfully: ${conditionType.displayName}")
    }

    private fun removeCondition(conditionType: ConditionType) {
        combatViewModel.toggleCondition(actorId, conditionType, false, null)

        // Notify parent
        setFragmentResult(RESULT_CONDITION_TOGGLED, bundleOf(
            "actor_id" to actorId,
            "condition_id" to conditionType.id,
            "applied" to false
        ))

        requireContext().toast("${conditionType.displayName} removed")
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

    private fun getConditionIconResource(conditionType: ConditionType): Int {
        // Map condition types to drawable resources
        // Note: Some resource names have typos in them
        return when (conditionType) {
            ConditionType.BLINDED -> R.drawable.ic_condition_blinded
            ConditionType.CHARMED -> R.drawable.ic_condition_charmed
            ConditionType.DEAFENED -> R.drawable.ic_condition_deafened
            ConditionType.EXHAUSTION -> R.drawable.ic_condition_exhaustion
            ConditionType.FRIGHTENED -> R.drawable.ic_condition_freightened  // Note the typo in resource name
            ConditionType.GRAPPLED -> R.drawable.ic_condition_grappled
            ConditionType.INCAPACITATED -> R.drawable.ic_condition_incapacitated
            ConditionType.INVISIBLE -> R.drawable.ic_condition_invisible
            ConditionType.PARALYZED -> R.drawable.ic_condition_paralyzed
            ConditionType.PETRIFIED -> R.drawable.ic_condition_petrified
            ConditionType.POISONED -> R.drawable.ic_condition_poisoned
            ConditionType.PRONE -> R.drawable.ic_condition_prone
            ConditionType.RESTRAINED -> R.drawable.ic_condition_restrained
            ConditionType.STUNNED -> R.drawable.ic_condition_stunned
            ConditionType.UNCONSCIOUS -> R.drawable.ic_condition_unconscious
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

        // Reset all condition checkboxes and fields
        for (i in 0 until binding.conditionsContainer.childCount) {
            val conditionView = binding.conditionsContainer.getChildAt(i)
            val checkbox = conditionView?.findViewById<CheckBox>(R.id.checkboxCondition)

            // IMPORTANT: Remove listener before changing checked state
            checkbox?.setOnCheckedChangeListener(null)
            checkbox?.isChecked = false

            conditionView?.findViewById<EditText>(R.id.editTextDuration)?.setText("")
            conditionView?.findViewById<CheckBox>(R.id.checkboxPermanent)?.isChecked = false
            conditionView?.findViewById<View>(R.id.layoutDuration)?.gone()
        }

        // Update conditions UI for the new actor
        currentActor?.let { updateConditionsUI(it) }

        Timber.d("Updated bottom sheet to show actor: $actorId")
    }
}
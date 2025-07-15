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

        // Add close button handler
        binding.buttonClose.setOnClickListener {
            saveCurrentChanges()
            // Request the activity to hide the bottom sheet
            setFragmentResult(RESULT_CLOSE_REQUESTED, bundleOf())
        }
    }

    // Add method to save current changes
    private fun saveCurrentChanges() {
        // Save any pending initiative changes
        val initiativeText = binding.editTextInitiative.text.toString()
        if (initiativeText.isNotEmpty()) {
            val initiative = initiativeText.toDoubleOrNull()
            if (initiative != null) {
                combatViewModel.setActorInitiative(actorId, initiative)
                Timber.d("Saved initiative $initiative for actor $actorId")
            }
        }

        // Conditions are already saved immediately when toggled, so no need to save them here
    }

    // Override dismiss to always save changes
    override fun dismiss() {
        saveCurrentChanges()
        super.dismiss()
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
        setupInitiativeSection()
        setupConditionsSection()
        setupActorActions()
        setupEncounterActions()
    }

    /**
     * Setup initiative editing section
     */
    private fun setupInitiativeSection() {
        // Initiative input
        binding.editTextInitiative.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validateInitiativeInput()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

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

        // Toggle checkbox
        itemBinding.checkboxCondition.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Show duration options
                itemBinding.layoutDuration.visible()
            } else {
                // Hide duration options and remove condition
                itemBinding.layoutDuration.gone()
                removeCondition(conditionType)
            }
        }

        // Permanent checkbox
        itemBinding.checkboxPermanent.setOnCheckedChangeListener { _, isPermanent ->
            itemBinding.editTextDuration.isEnabled = !isPermanent
            if (isPermanent) {
                itemBinding.editTextDuration.setText("")
            }
        }

        // Apply button
        itemBinding.buttonApply.setOnClickListener {
            applyCondition(
                conditionType,
                itemBinding.checkboxPermanent.isChecked,
                itemBinding.editTextDuration.text.toString()
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
    private fun setupEncounterActions() {
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
    }

    // ========== UI Updates ==========

    private fun updateUI(actor: EncounterActorState) {
        // Update header
        binding.textActorName.text = actor.displayName

        // Update initiative
        binding.textCurrentInitiative.text = if (actor.initiative != null) {
            "Current: ${formatInitiative(actor.initiative, showDecimals = true)}"
        } else {
            getString(R.string.current_x, getString(R.string.initiative_not_set))
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

        for (i in 0 until binding.conditionsContainer.childCount) {
            val view = binding.conditionsContainer.getChildAt(i)
            val checkbox = view.findViewById<CheckBox>(R.id.checkboxCondition)
            val conditionType = ConditionType.values()[i]

            checkbox.isChecked = conditionType.id in activeConditionIds
        }
    }

    // ========== Actions ==========

    private fun setInitiative() {
        val initiativeText = binding.editTextInitiative.text.toString()
        val initiative = initiativeText.toDoubleOrNull()

        if (initiative == null) {
            binding.textInputLayoutInitiative.error = "Invalid initiative value"
            return
        }

        combatViewModel.setActorInitiative(actorId, initiative)

        // Notify parent
        setFragmentResult(RESULT_INITIATIVE_CHANGED, bundleOf(
            "actor_id" to actorId,
            "initiative" to initiative
        ))

        requireContext().toast("Initiative set to $initiative")
        binding.editTextInitiative.setText("")
    }

    private fun applyCondition(conditionType: ConditionType, isPermanent: Boolean, durationText: String) {
        val duration = if (isPermanent) null else durationText.toIntOrNull()

        if (!isPermanent && duration == null) {
            Timber.w("Attempted to apply condition without duration: ${conditionType.displayName}")
            Snackbar.make(binding.root, "Please enter a duration or select Permanent", Snackbar.LENGTH_SHORT).show()
            return
        }

        // Apply the condition immediately
        combatViewModel.toggleCondition(actorId, conditionType, isPermanent, duration)

        // Clear the duration field after applying
        val conditionIndex = ConditionType.values().indexOf(conditionType)
        val conditionView = binding.conditionsContainer.getChildAt(conditionIndex)
        conditionView?.findViewById<EditText>(R.id.editTextDuration)?.setText("")

        Timber.d("Condition applied: ${conditionType.displayName}, permanent: $isPermanent, duration: $duration")
    }

    private fun removeCondition(conditionType: ConditionType) {
        combatViewModel.toggleCondition(actorId, conditionType, false, null)

        // Notify parent
        setFragmentResult(RESULT_CONDITION_TOGGLED, bundleOf(
            "actor_id" to actorId,
            "condition_id" to conditionType.id,
            "applied" to false
        ))
    }

    private fun confirmRemoveActor() {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Actor?")
            .setMessage("Remove ${currentActor?.displayName} from the encounter?")
            .setPositiveButton("Remove") { dialog, which ->
                combatViewModel.removeActor(actorId)
                setFragmentResult(RESULT_ACTOR_REMOVED, bundleOf("actor_id" to actorId))
                dismiss()
            }
            .setNegativeButton(Constants.Dialogs.BUTTON_CANCEL, null)
            .show()
    }

    // ========== Helpers ==========

    private fun validateInitiativeInput(): Boolean {
        val text = binding.editTextInitiative.text.toString()
        return when {
            text.isEmpty() -> {
                binding.textInputLayoutInitiative.error = null
                false
            }
            text.toDoubleOrNull() == null -> {
                binding.textInputLayoutInitiative.error = "Must be a number"
                false
            }
            else -> {
                binding.textInputLayoutInitiative.error = null
                true
            }
        }
    }

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
            ConditionType.POISONED -> R.drawable.ic_condition_poisoned  // Note the typo in resource name
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
        // Save any pending changes for the current actor
        saveCurrentChanges()

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
            conditionView?.findViewById<CheckBox>(R.id.checkboxCondition)?.isChecked = false
            conditionView?.findViewById<EditText>(R.id.editTextDuration)?.setText("")
            conditionView?.findViewById<CheckBox>(R.id.checkboxPermanent)?.isChecked = false
        }

        // Update conditions UI for the new actor
        currentActor?.let { updateConditionsUI(it) }

        Timber.d("Updated bottom sheet to show actor: $actorId")
    }
}
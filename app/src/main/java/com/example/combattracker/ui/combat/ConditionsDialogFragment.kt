// File: ConditionsDialogFragment.kt
// Location: app/src/main/java/com/example/combattracker/ui/combat/ConditionsDialogFragment.kt

package com.example.combattracker.ui.combat

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.combattracker.R
import com.example.combattracker.data.model.ConditionType
import com.example.combattracker.databinding.DialogConditionsMenuBinding
import com.example.combattracker.databinding.ItemConditionBinding
import com.example.combattracker.utils.gone
import com.example.combattracker.utils.toast
import com.example.combattracker.utils.visible
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class ConditionsDialogFragment : DialogFragment() {

    private var _binding: DialogConditionsMenuBinding? = null
    private val binding get() = _binding!!

    private val combatViewModel: CombatViewModel by activityViewModels()

    private var actorId: Long = -1L
    private var actorName: String = ""
    private var currentActor: EncounterActorState? = null

    // Track pending changes
    private val pendingConditionChanges = mutableMapOf<ConditionType, ConditionChange>()

    // Track initial state to detect changes
    private val initialConditionStates = mutableMapOf<ConditionType, ConditionState>()

    companion object {
        private const val ARG_ACTOR_ID = "actor_id"
        private const val ARG_ACTOR_NAME = "actor_name"

        fun newInstance(actorId: Long, actorName: String): ConditionsDialogFragment {
            return ConditionsDialogFragment().apply {
                arguments = bundleOf(
                    ARG_ACTOR_ID to actorId,
                    ARG_ACTOR_NAME to actorName
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("ConditionsDialogFragment onCreate called")

        try {
            setStyle(STYLE_NORMAL, R.style.Theme_CombatTracker_FullScreenDialog)
            Timber.d("Style set successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set style")
        }

        actorId = arguments?.getLong(ARG_ACTOR_ID, -1L) ?: -1L
        actorName = arguments?.getString(ARG_ACTOR_NAME) ?: ""

        Timber.d("Actor ID: $actorId, Actor Name: $actorName")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.d("ConditionsDialogFragment onCreateView called")
        try {
            _binding = DialogConditionsMenuBinding.inflate(inflater, container, false)
            Timber.d("Binding inflated successfully")
            return binding.root
        } catch (e: Exception) {
            Timber.e(e, "Failed to inflate binding: ${e.message}")
            throw e
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("ConditionsDialogFragment onViewCreated called")

        try {
            binding.textActorName.text = actorName
            Timber.d("Set actor name successfully")

            // Close button - dismiss without applying changes
            binding.buttonClose.setOnClickListener {
                Timber.d("Close button clicked")
                dismiss()
            }

            // Cancel button - dismiss without applying changes
            binding.buttonCancel.setOnClickListener {
                Timber.d("Cancel button clicked")
                dismiss()
            }

            // Apply button - apply all pending changes
            binding.buttonApply.setOnClickListener {
                Timber.d("Apply button clicked")
                applyAllChanges()
            }

            Timber.d("Button listeners set successfully")

            observeViewModel()
            setupConditions()

            Timber.d("ConditionsDialogFragment setup completed")
        } catch (e: Exception) {
            Timber.e(e, "Error in onViewCreated: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun observeViewModel() {
        combatViewModel.combatState.observe(viewLifecycleOwner) { state ->
            currentActor = state.actors.find { it.id == actorId }
            currentActor?.let { updateConditionsUI(it) }
        }
    }

    private fun setupConditions() {
        Timber.d("setupConditions called")
        try {
            // Add all 15 conditions in a 2-column grid
            ConditionType.values().forEach { conditionType ->
                Timber.d("Adding condition: ${conditionType.displayName}")

                val conditionBinding = ItemConditionBinding.inflate(
                    layoutInflater,
                    binding.conditionsGrid,
                    false
                )

                setupConditionItem(conditionBinding, conditionType)

                // Set grid layout params for 2 columns
                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                conditionBinding.root.layoutParams = params

                binding.conditionsGrid.addView(conditionBinding.root)
            }
            Timber.d("All conditions added successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error in setupConditions: ${e.message}")
            throw e
        }
    }

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

                // Track as pending change
                updatePendingChange(conditionType, itemBinding)
            } else {
                // Hide duration options
                itemBinding.layoutDuration.gone()
                // Clear the input fields when unchecking
                itemBinding.editTextDuration.setText("")
                itemBinding.checkboxPermanent.isChecked = false

                // Track removal as pending change
                pendingConditionChanges[conditionType] = ConditionChange(
                    conditionType = conditionType,
                    action = ChangeAction.REMOVE,
                    isPermanent = false,
                    duration = null
                )
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

            // Update pending change if condition is checked
            if (itemBinding.checkboxCondition.isChecked) {
                updatePendingChange(conditionType, itemBinding)
            }
        }

        // Update pending change when duration text changes
        itemBinding.editTextDuration.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && itemBinding.checkboxCondition.isChecked) {
                updatePendingChange(conditionType, itemBinding)
            }
        }
    }

    private fun updatePendingChange(conditionType: ConditionType, itemBinding: ItemConditionBinding) {
        val isPermanent = itemBinding.checkboxPermanent.isChecked
        val durationText = itemBinding.editTextDuration.text.toString().trim()
        val duration = if (isPermanent) null else durationText.toIntOrNull()

        pendingConditionChanges[conditionType] = ConditionChange(
            conditionType = conditionType,
            action = ChangeAction.APPLY,
            isPermanent = isPermanent,
            duration = duration
        )
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

        for (i in 0 until binding.conditionsGrid.childCount) {
            val view = binding.conditionsGrid.getChildAt(i)
            val checkbox = view.findViewById<CheckBox>(R.id.checkboxCondition)
            val layoutDuration = view.findViewById<View>(R.id.layoutDuration)
            val permanentCheckbox = view.findViewById<CheckBox>(R.id.checkboxPermanent)
            val durationEditText = view.findViewById<EditText>(R.id.editTextDuration)
            val conditionType = view.tag as? ConditionType ?: continue

            // IMPORTANT: Remove the listeners before updating
            checkbox.setOnCheckedChangeListener(null)
            permanentCheckbox.setOnCheckedChangeListener(null)

            // Update checkbox state
            val isActive = conditionType.id in activeConditionIds
            checkbox.isChecked = isActive

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
                    } else {
                        durationEditText.setText("")
                    }

                    // Store initial state
                    initialConditionStates[conditionType] = ConditionState(
                        isActive = true,
                        isPermanent = details.actorCondition.isPermanent,
                        duration = details.actorCondition.remainingDuration
                    )
                }
            } else {
                layoutDuration.gone()
                // Clear fields when not active
                permanentCheckbox.isChecked = false
                durationEditText.setText("")
                durationEditText.isEnabled = true

                // Store initial state
                initialConditionStates[conditionType] = ConditionState(
                    isActive = false,
                    isPermanent = false,
                    duration = null
                )
            }

            // Re-attach listeners
            setupConditionListeners(checkbox, layoutDuration, permanentCheckbox, durationEditText, conditionType, view)
        }

        // Clear pending changes after UI update
        pendingConditionChanges.clear()
    }

    private fun setupConditionListeners(
        checkbox: CheckBox,
        layoutDuration: View,
        permanentCheckbox: CheckBox,
        durationEditText: EditText,
        conditionType: ConditionType,
        view: View
    ) {
        permanentCheckbox.setOnCheckedChangeListener { _, isPermanent ->
            durationEditText.isEnabled = !isPermanent
            if (isPermanent) {
                durationEditText.setText("")
                durationEditText.error = null
            }
            if (checkbox.isChecked) {
                updatePendingChangeFromView(conditionType, checkbox, permanentCheckbox, durationEditText)
            }
        }

        checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                layoutDuration.visible()
                durationEditText.isEnabled = !permanentCheckbox.isChecked
                durationEditText.requestFocus()
                updatePendingChangeFromView(conditionType, checkbox, permanentCheckbox, durationEditText)
            } else {
                layoutDuration.gone()
                durationEditText.setText("")
                permanentCheckbox.isChecked = false
                pendingConditionChanges[conditionType] = ConditionChange(
                    conditionType = conditionType,
                    action = ChangeAction.REMOVE,
                    isPermanent = false,
                    duration = null
                )
            }
        }

        durationEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && checkbox.isChecked) {
                updatePendingChangeFromView(conditionType, checkbox, permanentCheckbox, durationEditText)
            }
        }
    }

    private fun updatePendingChangeFromView(
        conditionType: ConditionType,
        checkbox: CheckBox,
        permanentCheckbox: CheckBox,
        durationEditText: EditText
    ) {
        val isPermanent = permanentCheckbox.isChecked
        val durationText = durationEditText.text.toString().trim()
        val duration = if (isPermanent) null else durationText.toIntOrNull()

        pendingConditionChanges[conditionType] = ConditionChange(
            conditionType = conditionType,
            action = ChangeAction.APPLY,
            isPermanent = isPermanent,
            duration = duration
        )
    }

    private fun applyAllChanges() {
        Timber.d("=== applyAllChanges started ===")

        // First, capture current state of all checked conditions
        for (i in 0 until binding.conditionsGrid.childCount) {
            val view = binding.conditionsGrid.getChildAt(i)
            val checkbox = view.findViewById<CheckBox>(R.id.checkboxCondition)
            val permanentCheckbox = view.findViewById<CheckBox>(R.id.checkboxPermanent)
            val durationEditText = view.findViewById<EditText>(R.id.editTextDuration)
            val conditionType = view.tag as? ConditionType ?: continue

            if (checkbox.isChecked) {
                // Update pending change with current values
                updatePendingChangeFromView(conditionType, checkbox, permanentCheckbox, durationEditText)
                Timber.d("Captured state for ${conditionType.displayName}: permanent=${permanentCheckbox.isChecked}, duration=${durationEditText.text}")
            }
        }

        // Log all pending changes
        Timber.d("Pending changes:")
        pendingConditionChanges.forEach { (conditionType, change) ->
            Timber.d("  ${conditionType.displayName}: action=${change.action}, permanent=${change.isPermanent}, duration=${change.duration}")
        }

        // Validate all pending changes
        val validationErrors = mutableListOf<String>()

        pendingConditionChanges.forEach { (conditionType, change) ->
            if (change.action == ChangeAction.APPLY && !change.isPermanent && (change.duration == null || change.duration <= 0)) {
                validationErrors.add("${conditionType.displayName}: Duration required")
            }
        }

        if (validationErrors.isNotEmpty()) {
            Timber.d("Validation errors: $validationErrors")
            Snackbar.make(binding.root, validationErrors.joinToString("\n"), Snackbar.LENGTH_LONG).show()
            return
        }

        // Get current active conditions to detect updates vs new applications
        val currentActiveConditions = currentActor?.conditions?.map { it.id }?.toSet() ?: emptySet()
        Timber.d("Current active conditions: ${currentActiveConditions.joinToString()}")

        // Apply all changes
        lifecycleScope.launch {
            var changeCount = 0

            // First, process all removals (including updates that need removal first)
            Timber.d("=== Processing removals ===")
            pendingConditionChanges.forEach { (conditionType, change) ->
                when (change.action) {
                    ChangeAction.REMOVE -> {
                        Timber.d("Removing condition: ${conditionType.displayName}")
                        combatViewModel.toggleCondition(actorId, conditionType, false, null)
                        changeCount++
                    }
                    ChangeAction.APPLY -> {
                        if (conditionType.id in currentActiveConditions) {
                            // This is an update - remove first
                            Timber.d("Removing existing condition for update: ${conditionType.displayName} (ID: ${conditionType.id})")
                            combatViewModel.toggleCondition(actorId, conditionType, false, null)
                        }
                    }
                }
            }

            // Small delay to ensure removals complete
            Timber.d("Waiting for removals to complete...")
            delay(200) // Increased delay

            // Then, process all applications
            Timber.d("=== Processing applications ===")
            pendingConditionChanges.forEach { (conditionType, change) ->
                if (change.action == ChangeAction.APPLY) {
                    Timber.d("Applying condition: ${conditionType.displayName} (ID: ${conditionType.id}), permanent=${change.isPermanent}, duration=${change.duration}")
                    combatViewModel.toggleCondition(actorId, conditionType, change.isPermanent, change.duration)
                    changeCount++
                }
            }

            // Show success message and dismiss
            if (changeCount > 0) {
                requireContext().toast("Applied $changeCount condition changes")
            }
            Timber.d("=== applyAllChanges completed ===")
            dismiss()
        }
    }

    private fun getConditionIconResource(conditionType: ConditionType): Int {
        return when (conditionType) {
            ConditionType.BLINDED -> R.drawable.ic_condition_blinded
            ConditionType.CHARMED -> R.drawable.ic_condition_charmed
            ConditionType.DEAFENED -> R.drawable.ic_condition_deafened
            ConditionType.EXHAUSTION -> R.drawable.ic_condition_exhaustion
            ConditionType.FRIGHTENED -> R.drawable.ic_condition_freightened
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

    // Data classes for tracking changes
    private data class ConditionChange(
        val conditionType: ConditionType,
        val action: ChangeAction,
        val isPermanent: Boolean,
        val duration: Int?
    )

    private data class ConditionState(
        val isActive: Boolean,
        val isPermanent: Boolean,
        val duration: Int?
    )

    private enum class ChangeAction {
        APPLY,
        REMOVE
    }
}
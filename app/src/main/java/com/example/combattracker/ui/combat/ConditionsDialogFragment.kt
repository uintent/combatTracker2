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
import com.example.combattracker.R
import com.example.combattracker.data.model.ConditionType
import com.example.combattracker.databinding.DialogConditionsMenuBinding
import com.example.combattracker.databinding.ItemConditionBinding
import com.example.combattracker.utils.gone
import com.example.combattracker.utils.toast
import com.example.combattracker.utils.visible
import com.google.android.material.snackbar.Snackbar
import timber.log.Timber

class ConditionsDialogFragment : DialogFragment() {

    private var _binding: DialogConditionsMenuBinding? = null
    private val binding get() = _binding!!

    private val combatViewModel: CombatViewModel by activityViewModels()

    private var actorId: Long = -1L
    private var actorName: String = ""
    private var currentActor: EncounterActorState? = null

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
        setStyle(STYLE_NORMAL, R.style.Theme_CombatTracker_FullScreenDialog)

        actorId = arguments?.getLong(ARG_ACTOR_ID, -1L) ?: -1L
        actorName = arguments?.getString(ARG_ACTOR_NAME) ?: ""
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
        _binding = DialogConditionsMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textActorName.text = actorName
        binding.buttonClose.setOnClickListener {
            dismiss()
        }

        observeViewModel()
        setupConditions()
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
        // Add all 15 conditions in a 2-column grid
        ConditionType.values().forEach { conditionType ->
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

        Timber.d("Condition applied successfully: ${conditionType.displayName}")
    }

    private fun removeCondition(conditionType: ConditionType) {
        combatViewModel.toggleCondition(actorId, conditionType, false, null)
        requireContext().toast("${conditionType.displayName} removed")
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
}
// File: CombatActorAdapter.kt
// Location: app/src/main/java/com/example/combattracker/ui/combat/CombatActorAdapter.kt

package com.example.combattracker.ui.combat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.combattracker.R
import com.example.combattracker.data.database.entities.Condition
import com.example.combattracker.data.model.ActorCategory
import com.example.combattracker.databinding.ItemCombatActorBinding
import com.example.combattracker.utils.*

/**
 * CombatActorAdapter - RecyclerView adapter for horizontal combat tracker
 *
 * Purpose:
 * - Display actors in initiative order (horizontal scroll)
 * - Show different sizes for active vs inactive actors
 * - Apply visual states (greyed out, red overlay, green tint)
 * - Display condition icons
 *
 * Requirements Reference:
 * From section 3.3.4: Turn Order Display
 * - Horizontal arrangement from left (highest initiative) to right (lowest)
 * - Actor Tile Sizing: Active actor ~20% larger than inactive
 * - Visual Indicators: Current active larger, completed turn greyed out
 * From section 3.4.1: Context Menu Highlighting
 * - Selected Actor: Special highlighting
 * - Other Actors: Green tint overlay
 */
class CombatActorAdapter(
    private val onActorClick: (EncounterActorState) -> Unit
) : ListAdapter<EncounterActorState, CombatActorAdapter.CombatActorViewHolder>(
    CombatActorDiffCallback()
) {

    // ========== ViewHolder ==========

    class CombatActorViewHolder(
        private val binding: ItemCombatActorBinding,
        private val onActorClick: (EncounterActorState) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentActor: EncounterActorState? = null

        init {
            binding.root.setOnClickListener {
                currentActor?.let { onActorClick(it) }
            }
        }

        fun bind(actor: EncounterActorState) {
            currentActor = actor

            // Set actor name
            binding.textActorName.text = actor.displayName

            // Show initiative value (optional - could be in context menu only)
            binding.textInitiative.text = if (actor.initiative != null) {
                InitiativeCalculator.formatInitiative(actor.initiative, showDecimals = false)
            } else {
                "—"
            }

            // Load portrait
            if (actor.portraitPath != null) {
                binding.imagePortrait.loadFromInternalStorage(
                    actor.portraitPath,
                    getPlaceholderForCategory(actor.category)
                )
            } else {
                binding.imagePortrait.setImageResource(
                    getPlaceholderForCategory(actor.category)
                )
            }

            // Apply visual states
            applyVisualStates(actor)

            // Display conditions
            displayConditions(actor.conditions)

            // Set content description for accessibility
            binding.root.contentDescription = buildAccessibilityDescription(actor)
        }

        /**
         * Apply visual states based on actor status
         */
        private fun applyVisualStates(actor: EncounterActorState) {
            val context = binding.root.context

            // Reset to default state
            binding.root.alpha = 1.0f
            binding.overlayView.gone()
            binding.portraitContainer.scaleX = 1.0f
            binding.portraitContainer.scaleY = 1.0f

            when {
                // Missing initiative - red overlay
                actor.missingInitiative -> {
                    binding.overlayView.visible()
                    binding.overlayView.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.overlay_missing_initiative)
                    )
                }

                // Active actor - larger size
                actor.isActive -> {
                    binding.portraitContainer.scaleX = 1.2f
                    binding.portraitContainer.scaleY = 1.2f
                }

                // Has taken turn - greyed out
                actor.hasTakenTurn -> {
                    binding.root.alpha = 0.5f
                }

                // Context menu open for another actor - green tint
                actor.isHighlighted -> {
                    // This actor is selected, no overlay
                }

                // Remove "else if" and just use the condition directly
                currentActor != actor && isAnyActorHighlighted() -> {
                    binding.overlayView.visible()
                    binding.overlayView.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.overlay_context_menu)
                    )
                }
            }
        }

        /**
         * Display condition icons
         */
        private fun displayConditions(conditions: List<Condition>) {
            binding.conditionContainer.removeAllViews()

            if (conditions.isEmpty()) {
                binding.conditionContainer.gone()
                return
            }

            binding.conditionContainer.visible()

            // Add condition icons (limit to show to avoid overflow)
            conditions.take(3).forEach { condition ->
                val iconView = LayoutInflater.from(binding.root.context)
                    .inflate(R.layout.view_condition_icon, binding.conditionContainer, false)

                // Set icon based on condition
                // This assumes you have drawable resources for each condition
                val iconRes = getConditionIcon(condition)
                iconView.findViewById<android.widget.ImageView>(R.id.imageCondition)
                    .setImageResource(iconRes)

                binding.conditionContainer.addView(iconView)
            }

            // Show "+X" if more conditions
            if (conditions.size > 3) {
                val moreView = LayoutInflater.from(binding.root.context)
                    .inflate(R.layout.view_condition_more, binding.conditionContainer, false)

                moreView.findViewById<android.widget.TextView>(R.id.textMore)
                    .text = "+${conditions.size - 3}"

                binding.conditionContainer.addView(moreView)
            }
        }

        /**
         * Get placeholder drawable for category
         */
        private fun getPlaceholderForCategory(category: ActorCategory): Int {
            return when (category) {
                ActorCategory.PLAYER -> R.drawable.placeholder_player
                ActorCategory.NPC -> R.drawable.placeholder_npc
                ActorCategory.MONSTER -> R.drawable.placeholder_monster
                ActorCategory.OTHER -> R.drawable.placeholder_other
            }
        }

        /**
         * Get condition icon resource
         */
        private fun getConditionIcon(condition: Condition): Int {
            return when (condition.name.lowercase()) {
                "blinded" -> R.drawable.ic_condition_blinded
                "charmed" -> R.drawable.ic_condition_charmed
                "deafened" -> R.drawable.ic_condition_deafened
                "exhaustion" -> R.drawable.ic_condition_exhaustion
                "frightened" -> R.drawable.ic_condition_frightened
                "grappled" -> R.drawable.ic_condition_grappled
                "incapacitated" -> R.drawable.ic_condition_incapacitated
                "invisible" -> R.drawable.ic_condition_invisible
                "paralyzed" -> R.drawable.ic_condition_paralyzed
                "petrified" -> R.drawable.ic_condition_petrified
                "poisoned" -> R.drawable.ic_condition_poisoned
                "prone" -> R.drawable.ic_condition_prone
                "restrained" -> R.drawable.ic_condition_restrained
                "stunned" -> R.drawable.ic_condition_stunned
                "unconscious" -> R.drawable.ic_condition_unconscious
                else -> R.drawable.ic_condition_default  // <- Make sure this line exists!
            }
        }

        /**
         * Build accessibility description
         */
        private fun buildAccessibilityDescription(actor: EncounterActorState): String {
            return buildString {
                append(actor.displayName)

                if (actor.initiative != null) {
                    append(", initiative ${InitiativeCalculator.formatInitiative(actor.initiative)}")
                } else {
                    append(", no initiative")
                }

                when {
                    actor.isActive -> append(", currently taking turn")
                    actor.hasTakenTurn -> append(", turn completed")
                }

                if (actor.conditions.isNotEmpty()) {
                    append(", conditions: ")
                    append(actor.conditions.joinToString(", ") { it.name })
                }
            }
        }

        /**
         * Check if any actor is highlighted (context menu open)
         * This would be tracked in the adapter or passed from parent
         */
        private fun isAnyActorHighlighted(): Boolean {
            // This would need to be implemented based on your state management
            return false
        }
    }

    // ========== Adapter Methods ==========

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CombatActorViewHolder {
        val binding = ItemCombatActorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CombatActorViewHolder(binding, onActorClick)
    }

    override fun onBindViewHolder(holder: CombatActorViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ========== State Management ==========

    private var highlightedActorId: Long? = null

    /**
     * Set which actor is highlighted (for context menu)
     */
    fun setHighlightedActor(actorId: Long?) {
        highlightedActorId = actorId
        notifyDataSetChanged() // Could optimize with payload
    }

    // ========== DiffUtil Callback ==========

    class CombatActorDiffCallback : DiffUtil.ItemCallback<EncounterActorState>() {
        override fun areItemsTheSame(
            oldItem: EncounterActorState,
            newItem: EncounterActorState
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: EncounterActorState,
            newItem: EncounterActorState
        ): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(
            oldItem: EncounterActorState,
            newItem: EncounterActorState
        ): Any? {
            val changes = mutableListOf<String>()

            if (oldItem.initiative != newItem.initiative) changes.add("initiative")
            if (oldItem.isActive != newItem.isActive) changes.add("active")
            if (oldItem.hasTakenTurn != newItem.hasTakenTurn) changes.add("turn")
            if (oldItem.conditions != newItem.conditions) changes.add("conditions")
            if (oldItem.missingInitiative != newItem.missingInitiative) changes.add("missing")
            if (oldItem.isHighlighted != newItem.isHighlighted) changes.add("highlighted")

            return if (changes.isEmpty()) null else changes
        }
    }
}
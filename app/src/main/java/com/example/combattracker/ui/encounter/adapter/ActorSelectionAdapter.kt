// File: ActorSelectionAdapter.kt
// Location: app/src/main/java/com/example/combattracker/ui/encounter/adapter/ActorSelectionAdapter.kt

package com.example.combattracker.ui.encounter.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.combattracker.R
import com.example.combattracker.data.database.entities.Actor
import com.example.combattracker.data.model.ActorCategory
import com.example.combattracker.databinding.ItemActorSelectionBinding
import com.example.combattracker.ui.encounter.ActorSelectionState
import com.example.combattracker.utils.*

/**
 * ActorSelectionAdapter - Adapter for selecting actors in encounter creation
 *
 * Purpose:
 * - Display actors with checkboxes for selection
 * - Show quantity for selected actors
 * - Handle selection and quantity changes
 *
 * Requirements Reference:
 * From section 3.3.1: Select Actors
 * - Display all actors with name, thumbnail, and category
 * - Use checkboxes for selection
 * - Optional number input field for multiple instances
 */
class ActorSelectionAdapter(
    private val onActorChecked: (Actor, Boolean) -> Unit,
    private val onQuantityClick: (Actor) -> Unit
) : ListAdapter<ActorSelectionState, ActorSelectionAdapter.ActorSelectionViewHolder>(
    ActorSelectionDiffCallback()
) {

    // ========== ViewHolder ==========

    class ActorSelectionViewHolder(
        private val binding: ItemActorSelectionBinding,
        private val onActorChecked: (Actor, Boolean) -> Unit,
        private val onQuantityClick: (Actor) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentState: ActorSelectionState? = null

        init {
            // Set checkbox listener
            binding.checkboxSelect.setOnCheckedChangeListener { _, isChecked ->
                currentState?.let {
                    // Only trigger if different from current state
                    if (it.isSelected != isChecked) {
                        onActorChecked(it.actor, isChecked)
                    }
                }
            }

            // Set quantity click listener
            binding.layoutQuantity.setOnClickListener {
                currentState?.actor?.let { actor ->
                    onQuantityClick(actor)
                }
            }
        }

        fun bind(state: ActorSelectionState) {
            currentState = state
            val actor = state.actor

            // Set actor name
            binding.textActorName.text = actor.name

            // Set category
            binding.textCategory.text = actor.getActorCategory().displayName

            // Set category color
            val categoryColor = when (actor.getActorCategory()) {
                ActorCategory.PLAYER -> R.color.category_player
                ActorCategory.NPC -> R.color.category_npc
                ActorCategory.MONSTER -> R.color.category_monster
                ActorCategory.OTHER -> R.color.category_other
            }
            binding.textCategory.setTextColor(
                ContextCompat.getColor(binding.root.context, categoryColor)
            )

            // Set initiative modifier
            binding.textInitiative.text = "Initiative: ${actor.getInitiativeModifierDisplay()}"

            // Load portrait
            if (actor.portraitPath != null) {
                binding.imagePortrait.loadFromInternalStorage(
                    actor.portraitPath,
                    getPlaceholderForCategory(actor.getActorCategory())
                )
            } else {
                binding.imagePortrait.setImageResource(
                    getPlaceholderForCategory(actor.getActorCategory())
                )
            }

            // Set selection state
            binding.checkboxSelect.isChecked = state.isSelected

            // Show/hide quantity
            if (state.isSelected) {
                binding.layoutQuantity.visible()
                binding.textQuantity.text = "×${state.quantity}"
            } else {
                binding.layoutQuantity.gone()
            }

            // Set content description
            binding.root.contentDescription = buildString {
                append(actor.name)
                append(", ")
                append(actor.getActorCategory().displayName)
                if (state.isSelected) {
                    append(", selected")
                    if (state.quantity > 1) {
                        append(", quantity ${state.quantity}")
                    }
                }
            }
        }

        private fun getPlaceholderForCategory(category: ActorCategory): Int {
            return when (category) {
                ActorCategory.PLAYER -> R.drawable.placeholder_player
                ActorCategory.NPC -> R.drawable.placeholder_npc
                ActorCategory.MONSTER -> R.drawable.placeholder_monster
                ActorCategory.OTHER -> R.drawable.placeholder_other
            }
        }
    }

    // ========== Adapter Methods ==========

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActorSelectionViewHolder {
        val binding = ItemActorSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ActorSelectionViewHolder(binding, onActorChecked, onQuantityClick)
    }

    override fun onBindViewHolder(holder: ActorSelectionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ========== DiffUtil Callback ==========

    class ActorSelectionDiffCallback : DiffUtil.ItemCallback<ActorSelectionState>() {
        override fun areItemsTheSame(
            oldItem: ActorSelectionState,
            newItem: ActorSelectionState
        ): Boolean {
            return oldItem.actor.id == newItem.actor.id
        }

        override fun areContentsTheSame(
            oldItem: ActorSelectionState,
            newItem: ActorSelectionState
        ): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(
            oldItem: ActorSelectionState,
            newItem: ActorSelectionState
        ): Any? {
            val changes = mutableListOf<String>()

            if (oldItem.isSelected != newItem.isSelected) changes.add("selection")
            if (oldItem.quantity != newItem.quantity) changes.add("quantity")

            return if (changes.isEmpty()) null else changes
        }
    }
}
// File: ActorSelectionAdapter.kt
package com.example.combattracker.ui.encounter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.combattracker.R
import com.example.combattracker.data.model.ActorCategory
import com.example.combattracker.databinding.ItemActorSelectionBinding
import com.example.combattracker.utils.*

class ActorSelectionAdapter(
    private val onActorChecked: (ActorSelectionState, Boolean) -> Unit,
    private val onQuantityClick: (ActorSelectionState) -> Unit
) : ListAdapter<ActorSelectionState, ActorSelectionAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemActorSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onActorChecked, onQuantityClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemActorSelectionBinding,
        private val onActorChecked: (ActorSelectionState, Boolean) -> Unit,
        private val onQuantityClick: (ActorSelectionState) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(state: ActorSelectionState) {
            val actor = state.actor

            // Set actor info
            binding.textActorName.text = actor.name
            binding.textCategory.text = actor.getActorCategory().displayName

            // Set checkbox state
            binding.checkboxSelect.setOnCheckedChangeListener(null) // Remove listener to avoid loops
            binding.checkboxSelect.isChecked = state.isSelected
            binding.checkboxSelect.setOnCheckedChangeListener { _, isChecked ->
                onActorChecked(state, isChecked)
            }

            // Set quantity display
            if (state.isSelected && state.quantity > 1) {
                binding.textQuantity.visible()
                binding.textQuantity.text = "×${state.quantity}"
            } else {
                binding.textQuantity.gone()
            }

            // Quantity button
            binding.buttonQuantity.visibleIf(state.isSelected)
            binding.buttonQuantity.setOnClickListener {
                onQuantityClick(state)
            }

            // Load portrait
            if (actor.portraitPath != null) {
                binding.imagePortrait.loadFromInternalStorage(actor.portraitPath)
            } else {
                val placeholderRes = when (actor.getActorCategory()) {
                    ActorCategory.PLAYER -> R.drawable.placeholder_player
                    ActorCategory.NPC -> R.drawable.placeholder_npc
                    ActorCategory.MONSTER -> R.drawable.placeholder_monster
                    ActorCategory.OTHER -> R.drawable.placeholder_other
                }
                binding.imagePortrait.setImageResource(placeholderRes)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ActorSelectionState>() {
        override fun areItemsTheSame(oldItem: ActorSelectionState, newItem: ActorSelectionState): Boolean {
            return oldItem.actor.id == newItem.actor.id
        }

        override fun areContentsTheSame(oldItem: ActorSelectionState, newItem: ActorSelectionState): Boolean {
            return oldItem == newItem
        }
    }
}
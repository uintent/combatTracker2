// File: SelectedActorsAdapter.kt
// Location: app/src/main/java/com/example/combattracker/ui/encounter/SelectedActorsAdapter.kt

package com.example.combattracker.ui.encounter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.combattracker.R
import com.example.combattracker.data.database.entities.Actor
import com.example.combattracker.data.model.ActorCategory
import com.example.combattracker.databinding.ItemEncounterActorBinding
import com.example.combattracker.utils.*

/**
 * SelectedActorsAdapter - Adapter for displaying selected actors in encounter creation
 *
 * Shows actors that have been added to the encounter with:
 * - Actor info (name, category, initiative)
 * - Quantity with edit capability
 * - Remove button
 */
class SelectedActorsAdapter(
    private val onQuantityClick: (Actor) -> Unit,
    private val onRemoveClick: (Actor) -> Unit
) : ListAdapter<ActorWithQuantity, SelectedActorsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEncounterActorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onQuantityClick, onRemoveClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemEncounterActorBinding,
        private val onQuantityClick: (Actor) -> Unit,
        private val onRemoveClick: (Actor) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ActorWithQuantity) {
            val actor = item.actor

            // Set actor info
            binding.textActorName.text = actor.name
            binding.textCategory.text = actor.getActorCategory().displayName
            binding.textInitiative.text = "Initiative: ${actor.getInitiativeModifierDisplay()}"

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

            // Set quantity
            binding.textQuantity.text = "×${item.quantity}"

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

            // Set click listeners
            binding.layoutQuantity.setOnClickListener {
                onQuantityClick(actor)
            }

            binding.buttonRemove.setOnClickListener {
                onRemoveClick(actor)
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

    class DiffCallback : DiffUtil.ItemCallback<ActorWithQuantity>() {
        override fun areItemsTheSame(oldItem: ActorWithQuantity, newItem: ActorWithQuantity): Boolean {
            return oldItem.actor.id == newItem.actor.id
        }

        override fun areContentsTheSame(oldItem: ActorWithQuantity, newItem: ActorWithQuantity): Boolean {
            return oldItem == newItem
        }
    }
}

// ActorWithQuantity is defined in EncounterCreateViewModel.kt
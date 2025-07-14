// File: ActorListAdapter.kt
// Location: app/src/main/java/com/example/combattracker/ui/actors/adapter/ActorListAdapter.kt

package com.example.combattracker.ui.actors

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.combattracker.R
import com.example.combattracker.data.database.entities.Actor
import com.example.combattracker.data.model.ActorCategory
import com.example.combattracker.databinding.ItemActorBinding
import com.example.combattracker.utils.*
import timber.log.Timber

/**
 * ActorListAdapter - RecyclerView adapter for displaying actors
 *
 * Purpose:
 * - Display actors in a grid layout
 * - Show actor portrait, name, category, and initiative modifier
 * - Handle click and long-click events
 * - Efficiently update list using DiffUtil
 *
 * Requirements Reference:
 * From section 3.1.3: Actor Library Management
 * - Display: Actor name, thumbnail picture, and actor category
 */
class ActorListAdapter(
    private val onActorClick: (Actor) -> Unit,
    private val onActorLongClick: (Actor) -> Boolean = { false }
) : ListAdapter<Actor, ActorListAdapter.ActorViewHolder>(ActorDiffCallback()) {

    // ========== ViewHolder ==========

    /**
     * ViewHolder for actor items
     */
    class ActorViewHolder(
        private val binding: ItemActorBinding,
        private val onActorClick: (Actor) -> Unit,
        private val onActorLongClick: (Actor) -> Boolean
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentActor: Actor? = null

        init {
            // Set click listeners
            binding.root.setOnClickListener {
                currentActor?.let { onActorClick(it) }
            }

            binding.root.setOnLongClickListener {
                currentActor?.let { onActorLongClick(it) } ?: false
            }
        }

        /**
         * Bind actor data to views
         */
        fun bind(actor: Actor) {
            currentActor = actor

            // Set actor name
            binding.textActorName.text = actor.name

            // Set category
            binding.textCategory.text = actor.getActorCategory().displayName

            // Set category background color
            val categoryColor = when (actor.getActorCategory()) {
                ActorCategory.PLAYER -> R.color.category_player
                ActorCategory.NPC -> R.color.category_npc
                ActorCategory.MONSTER -> R.color.category_monster
                ActorCategory.OTHER -> R.color.category_other
            }
            binding.categoryBadge.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, categoryColor)
            )

            // Set initiative modifier
            binding.textInitiative.text = actor.getInitiativeModifierDisplay()

            // Load portrait
            if (actor.portraitPath != null) {
                binding.imagePortrait.loadFromInternalStorage(
                    actor.portraitPath,
                    getPlaceholderForCategory(actor.getActorCategory())
                )
            } else {
                // Use placeholder based on category
                binding.imagePortrait.setImageResource(
                    getPlaceholderForCategory(actor.getActorCategory())
                )
            }

            // Set content description for accessibility
            binding.root.contentDescription = buildString {
                append(actor.name)
                append(", ")
                append(actor.getActorCategory().displayName)
                append(", Initiative ")
                append(actor.getInitiativeModifierDisplay())
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
    }

    // ========== Adapter Methods ==========

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActorViewHolder {
        val binding = ItemActorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ActorViewHolder(binding, onActorClick, onActorLongClick)
    }

    override fun onBindViewHolder(holder: ActorViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ========== DiffUtil Callback ==========

    /**
     * DiffUtil callback for efficient list updates
     */
    class ActorDiffCallback : DiffUtil.ItemCallback<Actor>() {
        override fun areItemsTheSame(oldItem: Actor, newItem: Actor): Boolean {
            // Compare by ID
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Actor, newItem: Actor): Boolean {
            // Compare all fields
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: Actor, newItem: Actor): Any? {
            // Return specific changes for partial updates
            val changes = mutableListOf<String>()

            if (oldItem.name != newItem.name) changes.add("name")
            if (oldItem.category != newItem.category) changes.add("category")
            if (oldItem.initiativeModifier != newItem.initiativeModifier) changes.add("initiative")
            if (oldItem.portraitPath != newItem.portraitPath) changes.add("portrait")

            return if (changes.isEmpty()) null else changes
        }
    }
}
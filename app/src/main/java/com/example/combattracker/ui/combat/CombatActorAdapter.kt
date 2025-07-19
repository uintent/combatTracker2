package com.example.combattracker.ui.combat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.combattracker.R
import com.example.combattracker.data.database.entities.Condition
import com.example.combattracker.data.model.ActorCategory
import com.example.combattracker.databinding.ItemCombatActorBinding
import com.example.combattracker.utils.formatInitiative
import com.example.combattracker.utils.gone
import com.example.combattracker.utils.loadFromInternalStorage
import com.example.combattracker.utils.visible
import kotlin.math.min
import timber.log.Timber
import android.view.MotionEvent
import com.example.combattracker.utils.*
import android.graphics.Color
import com.example.combattracker.utils.ImageUtils.isDarkImage

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

    init {
        Timber.d("CombatActorAdapter created with onActorClick callback")
    }

    private var highlightedActorId: Long? = null
    private var itemWidth: Int = 0
    private var itemHeight: Int = 0
    private var density: Float = 1f

    // Update the updateItemDimensions method to accept density
    fun updateItemDimensions(recyclerViewWidth: Int, recyclerViewHeight: Int, itemCount: Int, density: Float) {
        if (itemCount == 0 || recyclerViewWidth <= 0 || recyclerViewHeight <= 0) {
            Timber.d("Invalid dimensions for updateItemDimensions - width: $recyclerViewWidth, height: $recyclerViewHeight, count: $itemCount")
            return
        }

        this.density = density  // Store density

        // Calculate available width (accounting for padding and margins)
        val horizontalPadding = (48 * density).toInt() // 24dp padding on each side
        val itemMargin = (16 * density).toInt() // 8dp margin on each side of item
        val availableWidth = recyclerViewWidth - horizontalPadding

        // Calculate item width
        itemWidth = (availableWidth - (itemMargin * itemCount)) / itemCount

        val maxInactiveHeight = (recyclerViewHeight * 0.8).toInt()
        val calculatedHeight = (itemWidth * 1.5).toInt()

        itemHeight = min(calculatedHeight, maxInactiveHeight)
        itemWidth = (itemHeight / 1.5).toInt()

        Timber.d("Calculated dimensions - itemWidth: $itemWidth, itemHeight: $itemHeight")

        notifyDataSetChanged()
    }

    // ========== ViewHolder ==========

    inner class CombatActorViewHolder(
        private val binding: ItemCombatActorBinding,
        private val onActorClick: (EncounterActorState) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentActor: EncounterActorState? = null

        init {
            // Set click listener on the portrait card, not the root
            binding.portraitCard.setOnClickListener {
                Timber.d("Actor portrait clicked in adapter")
                currentActor?.let {
                    Timber.d("Invoking onActorClick for: ${it.displayName}")
                    onActorClick(it)
                } ?: Timber.e("currentActor is null on click")
            }
        }

        fun bind(actor: EncounterActorState, highlightedActorId: Long?) {
            currentActor = actor

            // Test with a simple color change on touch
            binding.portraitCard.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Timber.d("Touch DOWN on ${actor.displayName}")
                        v.alpha = 0.7f
                    }
                    MotionEvent.ACTION_UP -> {
                        Timber.d("Touch UP on ${actor.displayName}")
                        v.alpha = 1.0f
                        v.performClick() // Important for accessibility
                    }
                }
                false // Return false to allow click listener to also fire
            }

            // Apply dynamic sizing
            val layoutParams = binding.portraitCard.layoutParams
            if (itemWidth > 0 && itemHeight > 0) {
                layoutParams.width = itemWidth
                layoutParams.height = itemHeight

                // Scale up active actor by 20%
                if (actor.isActive) {
                    layoutParams.width = (itemWidth * 1.2).toInt()
                    layoutParams.height = (itemHeight * 1.2).toInt()
                }

                binding.portraitCard.layoutParams = layoutParams
            }

            // Set actor name
            binding.textActorName.text = actor.displayName

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
            applyVisualStates(actor, highlightedActorId)

            // Display conditions
            displayConditions(actor.conditions)

            // Set content description for accessibility
            binding.root.contentDescription = buildAccessibilityDescription(actor)
        }

        fun updateVisualStates(actor: EncounterActorState, highlightedActorId: Long?) {
            currentActor = actor
            applyVisualStates(actor, highlightedActorId)
        }


        /**
         * Apply visual states based on actor status
         */
        private fun applyVisualStates(actor: EncounterActorState, highlightedActorId: Long?) {
            // Debug log at the start
            Timber.d("applyVisualStates for ${actor.displayName}: highlightedId=$highlightedActorId, actorId=${actor.id}")

            // Reset all overlays to gone
            binding.completedOverlay.gone()
            binding.missingInitiativeOverlay.gone()
            binding.contextMenuOverlay.gone()

            when {
                // Missing initiative - show red overlay (highest priority)
                actor.missingInitiative -> {
                    binding.missingInitiativeOverlay.visible()
                    Timber.d("${actor.displayName}: RED overlay (missing initiative)")
                }

                // Another actor is highlighted - show green overlay
                highlightedActorId != null && actor.id != highlightedActorId -> {
                    binding.contextMenuOverlay.visible()
                    Timber.d("${actor.displayName}: GREEN overlay (other actor highlighted)")
                }

                // Has taken turn - show grey overlay
                actor.hasTakenTurn -> {
                    binding.completedOverlay.visible()
                    Timber.d("${actor.displayName}: GREY overlay (turn taken)")
                }

                // This actor is highlighted (context menu open)
                actor.id == highlightedActorId -> {
                    Timber.d("${actor.displayName}: NO overlay (this actor highlighted)")
                }

                // Default case
                else -> {
                    Timber.d("${actor.displayName}: NO overlay (default)")
                }
            }
        }

        /**
         * Display condition icons
         */
        private fun displayConditions(conditions: List<Condition>) {
            val actorName = currentActor?.displayName ?: "Unknown"
            Timber.d("displayConditions called for $actorName: ${conditions.size} conditions - ${conditions.map { it.name }}")

            binding.conditionsContainer.removeAllViews()

            if (conditions.isEmpty()) {
                binding.conditionsContainer.gone()
                return
            }

            binding.conditionsContainer.visible()

            // Determine if we need white icons based on portrait darkness
            val useWhiteIcons = binding.imagePortrait.isDarkImage()

            // Add condition icons (limit to show to avoid overflow)
            conditions.take(3).forEach { condition ->
                val iconView = ImageView(binding.root.context).apply {
                    layoutParams = LinearLayout.LayoutParams(24.dpToPx(), 24.dpToPx()).apply {
                        setMargins(0, 2.dpToPx(), 0, 2.dpToPx())
                    }
                    setImageResource(getConditionIcon(condition))
                    contentDescription = condition.name

                    // Apply white tint if background is dark
                    if (useWhiteIcons) {
                        setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
                    }
                }
                binding.conditionsContainer.addView(iconView)
            }

            // Show "+X" if more conditions
            if (conditions.size > 3) {
                val moreView = LayoutInflater.from(binding.root.context)
                    .inflate(R.layout.view_condition_more, binding.conditionsContainer, false)

                moreView.findViewById<TextView>(R.id.textMore)?.apply {
                    text = "+${conditions.size - 3}"
                    // Also make the text white if needed
                    if (useWhiteIcons) {
                        setTextColor(Color.WHITE)
                    }
                }

                binding.conditionsContainer.addView(moreView)
            }
        }

        // Extension function to convert dp to pixels for views
        private fun Int.dpToPx(): Int {
            return (this * binding.root.context.resources.displayMetrics.density).toInt()
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
                "frightened" -> R.drawable.ic_condition_freightened  // Note the typo in resource name
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
                else -> R.drawable.ic_condition_default
            }
        }

        /**
         * Build accessibility description
         */
        private fun buildAccessibilityDescription(actor: EncounterActorState): String {
            return buildString {
                append(actor.displayName)

                if (actor.initiative != null) {
                    append(", initiative ${formatInitiative(actor.initiative)}")
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

    fun getItemWidth(): Int = itemWidth
    fun getItemHeight(): Int = itemHeight

    override fun onBindViewHolder(holder: CombatActorViewHolder, position: Int) {
        val actor = getItem(position)
        Timber.d("Binding actor at position $position: ${actor.displayName} (ID: ${actor.id}) with ${actor.conditions.size} conditions")
        holder.bind(actor, highlightedActorId)
    }

    override fun onBindViewHolder(holder: CombatActorViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            // Full bind
            onBindViewHolder(holder, position)
        } else {
            // Partial bind - check if we need to update visual states
            val actor = getItem(position)
            if (payloads.any { it is List<*> && (it as List<*>).contains("highlighted") }) {
                holder.updateVisualStates(actor, highlightedActorId)
            } else {
                // For other payloads, do a full bind
                onBindViewHolder(holder, position)
            }
        }
    }

    // ========== State Management ==========

    /**
     * Set which actor is highlighted (for context menu)
     */
    fun setHighlightedActor(actorId: Long?) {
        Timber.d("setHighlightedActor called with: $actorId (was: $highlightedActorId)")

        val previousId = highlightedActorId
        highlightedActorId = actorId

        // Only update if the highlighted actor changed
        if (previousId != actorId) {
            // Force a complete refresh when clearing highlight (actorId is null)
            if (actorId == null) {
                Timber.d("Clearing highlight - notifying all items")
                notifyDataSetChanged()
            } else {
                // Update only affected items when setting a highlight
                currentList.forEachIndexed { index, actor ->
                    // Update the previously highlighted actor
                    if (previousId != null && actor.id == previousId) {
                        notifyItemChanged(index, listOf("highlighted"))
                    }
                    // Update the newly highlighted actor
                    if (actorId != null && actor.id == actorId) {
                        notifyItemChanged(index, listOf("highlighted"))
                    }
                    // Update all other actors if there's a highlight change
                    if (actor.id != previousId && actor.id != actorId) {
                        notifyItemChanged(index, listOf("highlighted"))
                    }
                }
            }
        }
    }

    /**
     * Force clear all highlights - useful as a fail-safe
     */
    fun clearAllHighlights() {
        Timber.d("clearAllHighlights called - forcing full refresh")
        highlightedActorId = null
        notifyDataSetChanged()
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
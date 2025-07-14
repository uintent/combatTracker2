// File: EncounterListAdapter.kt
// Location: app/src/main/java/com/example/combattracker/ui/encounter/EncounterListAdapter.kt

package com.example.combattracker.ui.encounter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.combattracker.data.database.dao.EncounterWithCount
import com.example.combattracker.databinding.ItemEncounterBinding
import com.example.combattracker.utils.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * EncounterListAdapter - RecyclerView adapter for displaying saved encounters
 *
 * Purpose:
 * - Display encounters with name, date, and actor count
 * - Handle click and long-click events
 * - Show encounter metadata in a clear format
 *
 * Requirements Reference:
 * From section 3.3.2: Encounter List Management
 * - Shows detailed list with encounter name + date/time + actor count
 */
class EncounterListAdapter(
    private val onEncounterClick: (EncounterWithCount) -> Unit,
    private val onEncounterLongClick: (EncounterWithCount) -> Boolean = { false }
) : ListAdapter<EncounterWithCount, EncounterListAdapter.EncounterViewHolder>(
    EncounterDiffCallback()
) {

    // ========== ViewHolder ==========

    class EncounterViewHolder(
        private val binding: ItemEncounterBinding,
        private val onEncounterClick: (EncounterWithCount) -> Unit,
        private val onEncounterLongClick: (EncounterWithCount) -> Boolean
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentEncounter: EncounterWithCount? = null

        init {
            binding.root.setOnClickListener {
                currentEncounter?.let { onEncounterClick(it) }
            }

            binding.root.setOnLongClickListener {
                currentEncounter?.let { onEncounterLongClick(it) } ?: false
            }
        }

        fun bind(encounterWithCount: EncounterWithCount) {
            currentEncounter = encounterWithCount
            val encounter = encounterWithCount.encounter

            // Set encounter name
            binding.textEncounterName.text = encounter.name

            // Format and set date
            val dateFormat = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US)
            val dateText = dateFormat.format(Date(encounter.createdDate))
            binding.textDate.text = dateText

            // Set actor count
            val actorCount = encounterWithCount.actorCount
            binding.textActorCount.text = when (actorCount) {
                0 -> "No actors"
                1 -> "1 actor"
                else -> "$actorCount actors"
            }

            // Set round info if encounter is in progress
            if (encounter.currentRound > 1) {
                binding.textRoundInfo.visible()
                binding.textRoundInfo.text = "Round ${encounter.currentRound}"
            } else {
                binding.textRoundInfo.gone()
            }

            // Set last modified if different from created
            if (encounter.lastModifiedDate > encounter.createdDate + 60000) { // More than 1 minute difference
                binding.textLastModified.visible()
                val modifiedText = "Modified: ${dateFormat.format(Date(encounter.lastModifiedDate))}"
                binding.textLastModified.text = modifiedText
            } else {
                binding.textLastModified.gone()
            }

            // Set content description for accessibility
            binding.root.contentDescription = buildString {
                append(encounter.name)
                append(", created ")
                append(dateText)
                append(", ")
                append("$actorCount actors")
                if (encounter.currentRound > 1) {
                    append(", currently on round ${encounter.currentRound}")
                }
            }
        }
    }

    // ========== Adapter Methods ==========

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EncounterViewHolder {
        val binding = ItemEncounterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EncounterViewHolder(binding, onEncounterClick, onEncounterLongClick)
    }

    override fun onBindViewHolder(holder: EncounterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ========== DiffUtil Callback ==========

    class EncounterDiffCallback : DiffUtil.ItemCallback<EncounterWithCount>() {
        override fun areItemsTheSame(
            oldItem: EncounterWithCount,
            newItem: EncounterWithCount
        ): Boolean {
            return oldItem.encounter.id == newItem.encounter.id
        }

        override fun areContentsTheSame(
            oldItem: EncounterWithCount,
            newItem: EncounterWithCount
        ): Boolean {
            return oldItem == newItem
        }
    }
}
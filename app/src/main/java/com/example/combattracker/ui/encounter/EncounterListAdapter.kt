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
import com.example.combattracker.utils.gone
import com.example.combattracker.utils.visible
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

            // Format date and combine with actor count for subtitle
            val dateFormat = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US)
            val dateText = dateFormat.format(Date(encounter.createdDate))

            val actorCount = encounterWithCount.actorCount
            val actorText = when (actorCount) {
                0 -> "No actors"
                1 -> "1 actor"
                else -> "$actorCount actors"
            }

            // Set subtitle with date and actor count
            binding.textEncounterSubtitle.text = "$dateText • $actorText"

            // Show round info if available (as additional info)
            val additionalInfo = buildString {
                if (encounter.currentRound > 1) {
                    append("Round ${encounter.currentRound}")
                }

                // Add modified date if significantly different
                if (encounter.lastModifiedDate > encounter.createdDate + 60000) {
                    if (isNotEmpty()) append(" • ")
                    append("Modified: ${dateFormat.format(Date(encounter.lastModifiedDate))}")
                }
            }

            if (additionalInfo.isNotEmpty()) {
                binding.textEncounterInfo.visible()
                binding.textEncounterInfo.text = additionalInfo
            } else {
                binding.textEncounterInfo.gone()
            }

            // Set content description for accessibility
            binding.root.contentDescription = buildString {
                append(encounter.name)
                append(", created ")
                append(dateText)
                append(", ")
                append(actorText)
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
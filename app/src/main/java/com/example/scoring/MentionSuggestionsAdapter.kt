package com.example.scoring

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class MentionItem {
    data class Header(val title: String) : MentionItem()
    data class Player(
        val name: String,
        val jersey: String = "",
        val isActiveInChat: Boolean = false
    ) : MentionItem()
}

class MentionSuggestionsAdapter(
    private val onPlayerSelected: (MentionItem.Player) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<MentionItem>()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_PLAYER = 1
    }

    fun submitList(newItems: List<MentionItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is MentionItem.Header -> TYPE_HEADER
            is MentionItem.Player -> TYPE_PLAYER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_mention_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_mention_suggestion, parent, false)
            PlayerViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is MentionItem.Header -> (holder as HeaderViewHolder).bind(item)
            is MentionItem.Player -> (holder as PlayerViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHeader: TextView = itemView.findViewById(R.id.tvMentionHeader)
        fun bind(item: MentionItem.Header) {
            tvHeader.text = item.title
        }
    }

    inner class PlayerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAvatar: TextView = itemView.findViewById(R.id.tvAvatarIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvMentionPlayerName)
        private val tvSub: TextView = itemView.findViewById(R.id.tvMentionPlayerSub)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvMentionBadge)

        fun bind(item: MentionItem.Player) {
            tvName.text = item.name
            tvSub.text = if (item.jersey.isNotBlank() && item.jersey != "0") "Jersey #${item.jersey}" else "League Member"

            val firstChar = item.name.firstOrNull()?.uppercaseChar()?.toString() ?: "P"
            tvAvatar.text = firstChar

            if (item.isActiveInChat) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = "💬 Active in Chat"
            } else {
                tvBadge.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onPlayerSelected(item)
            }
        }
    }
}

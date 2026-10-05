package com.example.scoring

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiChatAdapter(
    private val messages: MutableList<ChatMessage> = mutableListOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface OnMessageLongClickListener {
        fun onMessageLongClick(message: ChatMessage, position: Int)
    }

    private var onLongClickListener: OnMessageLongClickListener? = null

    fun setOnMessageLongClickListener(listener: OnMessageLongClickListener) {
        this.onLongClickListener = listener
    }

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_AI = 2
        const val PAYLOAD_TEXT_CHANGE = "PAYLOAD_TEXT_CHANGE"
    }

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private var markwon: Markwon? = null

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) TYPE_USER else TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (markwon == null) {
            try {
                markwon = Markwon.builder(parent.context)
                    .usePlugin(TablePlugin.create(parent.context))
                    .build()
            } catch (_: Exception) {
                markwon = Markwon.create(parent.context)
            }
        }

        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            val view = inflater.inflate(R.layout.item_chat_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_chat_ai, parent, false)
            AiViewHolder(view)
        }
    }

    private fun formatTimeText(formattedTime: String, generationTimeSecs: Int?): String {
        return if (generationTimeSecs != null && generationTimeSecs > 0) {
            val unit = if (generationTimeSecs == 1) "sec" else "secs"
            "$formattedTime\t. Generated in $generationTimeSecs $unit"
        } else {
            formattedTime
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.contains(PAYLOAD_TEXT_CHANGE)) {
            val msg = messages[position]
            if (holder is AiViewHolder) {
                val mw = markwon
                if (mw != null) {
                    mw.setMarkdown(holder.tvMessage, msg.text)
                } else {
                    holder.tvMessage.text = msg.text
                }
                val formattedTime = timeFormat.format(Date(msg.timestamp))
                holder.tvTime.text = formatTimeText(formattedTime, msg.generationTimeSecs)
            } else if (holder is UserViewHolder) {
                holder.tvMessage.text = msg.text
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        val formattedTime = timeFormat.format(Date(msg.timestamp))

        val longClickListenerRunnable = View.OnLongClickListener {
            val currentPos = holder.adapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onLongClickListener?.onMessageLongClick(msg, currentPos)
            }
            true
        }

        holder.itemView.setOnLongClickListener(longClickListenerRunnable)

        if (holder is UserViewHolder) {
            holder.tvMessage.text = msg.text
            holder.tvTime.text = formattedTime
            holder.tvMessage.setOnLongClickListener(longClickListenerRunnable)
        } else if (holder is AiViewHolder) {
            val mw = markwon
            if (mw != null) {
                mw.setMarkdown(holder.tvMessage, msg.text)
            } else {
                holder.tvMessage.text = msg.text
            }
            holder.tvTime.text = formatTimeText(formattedTime, msg.generationTimeSecs)
            holder.tvMessage.setOnLongClickListener(longClickListenerRunnable)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessageText(text: String, generationTimeSecs: Int? = null) {
        if (messages.isNotEmpty()) {
            val lastIdx = messages.size - 1
            messages[lastIdx].text = text
            if (generationTimeSecs != null) {
                messages[lastIdx].generationTimeSecs = generationTimeSecs
            }
            notifyItemChanged(lastIdx, PAYLOAD_TEXT_CHANGE)
        }
    }

    fun deleteMessageAt(position: Int) {
        if (position in 0 until messages.size) {
            messages.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun getMessagesList(): List<ChatMessage> {
        return messages.toList()
    }

    fun clearAllMessages() {
        val size = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, size)
    }

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvUserMessage)
        val tvTime: TextView = itemView.findViewById(R.id.tvUserTime)
    }

    class AiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvAiMessage)
        val tvTime: TextView = itemView.findViewById(R.id.tvAiTime)
    }
}

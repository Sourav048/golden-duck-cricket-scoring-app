package com.goldenduck.scoring

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.utils.ColorUtils
import io.noties.markwon.utils.Dip
import java.text.SimpleDateFormat
import java.util.Calendar
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

    private fun formatTime(context: Context, timestamp: Long): String {
        return DateFormat.getTimeFormat(context).format(Date(timestamp))
    }

    private fun getFormattedDateHeader(timestamp: Long): String {
        if (timestamp <= 0) return "Today"

        val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance()

        val msgYear = msgCal.get(Calendar.YEAR)
        val msgDay = msgCal.get(Calendar.DAY_OF_YEAR)

        val nowYear = nowCal.get(Calendar.YEAR)
        val nowDay = nowCal.get(Calendar.DAY_OF_YEAR)

        if (msgYear == nowYear && msgDay == nowDay) {
            return "Today"
        }

        if (msgYear == nowYear && msgDay == nowDay - 1) {
            return "Yesterday"
        }

        val sdf = SimpleDateFormat("dd MMMM, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun isSameDay(time1: Long, time2: Long): Boolean {
        if (time1 <= 0 && time2 <= 0) return true
        val cal1 = Calendar.getInstance().apply { timeInMillis = if (time1 > 0) time1 else System.currentTimeMillis() }
        val cal2 = Calendar.getInstance().apply { timeInMillis = if (time2 > 0) time2 else System.currentTimeMillis() }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private var markwon: Markwon? = null

    private fun createConfiguredMarkwon(context: Context): Markwon {
        val dip = Dip.create(context)
        val primaryColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.parseColor("#1976D2"))
        val primaryContainer = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryContainer, Color.parseColor("#BBDEFB"))
        val surfaceVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceVariant, Color.parseColor("#E0E0E0"))
        val outlineColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, Color.parseColor("#BDBDBD"))

        val tableTheme = TableTheme.buildWithDefaults(context)
            .tableCellPadding(dip.toPx(8))
            .tableBorderWidth(dip.toPx(1))
            .tableBorderColor(ColorUtils.applyAlpha(primaryColor, 100))
            .tableHeaderRowBackgroundColor(ColorUtils.applyAlpha(primaryContainer, 160))
            .tableOddRowBackgroundColor(ColorUtils.applyAlpha(surfaceVariant, 90))
            .tableEvenRowBackgroundColor(Color.TRANSPARENT)
            .build()

        return Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .linkColor(primaryColor)
                        .isLinkUnderlined(true)
                        .blockQuoteColor(primaryColor)
                        .blockQuoteWidth(dip.toPx(4))
                        .blockMargin(dip.toPx(16))
                        .listItemColor(primaryColor)
                        .bulletWidth(dip.toPx(6))
                        .bulletListItemStrokeWidth(dip.toPx(2))
                        .codeTextColor(primaryColor)
                        .codeBackgroundColor(ColorUtils.applyAlpha(surfaceVariant, 180))
                        .codeBlockBackgroundColor(ColorUtils.applyAlpha(surfaceVariant, 220))
                        .codeBlockMargin(dip.toPx(8))
                        .codeTypeface(Typeface.MONOSPACE)
                        .codeBlockTypeface(Typeface.MONOSPACE)
                        .headingBreakColor(outlineColor)
                        .headingBreakHeight(dip.toPx(1))
                        .headingTextSizeMultipliers(floatArrayOf(1.35f, 1.25f, 1.15f, 1.05f, 1.00f, 0.90f))
                        .thematicBreakColor(outlineColor)
                        .thematicBreakHeight(dip.toPx(2))
                }
            })
            .usePlugin(TablePlugin.create(tableTheme))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .build()
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) TYPE_USER else TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (markwon == null) {
            try {
                markwon = createConfiguredMarkwon(parent.context)
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
            "$formattedTime  •  Generated in $generationTimeSecs $unit"
        } else {
            formattedTime
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.contains(PAYLOAD_TEXT_CHANGE)) {
            val msg = messages[position]
            if (holder is AiViewHolder) {
                val cleanedText = TextFormatUtils.cleanHumanReadableText(msg.text)
                val mw = markwon
                if (mw != null) {
                    mw.setMarkdown(holder.tvMessage, cleanedText)
                } else {
                    holder.tvMessage.text = cleanedText
                }
                val formattedTime = formatTime(holder.itemView.context, msg.timestamp)
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
        val prevMsg = if (position > 0) messages[position - 1] else null
        val showDateHeader = position == 0 || (prevMsg != null && !isSameDay(msg.timestamp, prevMsg.timestamp))
        val formattedTime = formatTime(holder.itemView.context, msg.timestamp)

        val longClickListenerRunnable = View.OnLongClickListener {
            val currentPos = holder.adapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onLongClickListener?.onMessageLongClick(msg, currentPos)
            }
            true
        }

        holder.itemView.setOnLongClickListener(longClickListenerRunnable)

        if (holder is UserViewHolder) {
            if (showDateHeader) {
                holder.layoutDateHeader?.visibility = View.VISIBLE
                holder.tvDateHeader?.text = getFormattedDateHeader(msg.timestamp)
            } else {
                holder.layoutDateHeader?.visibility = View.GONE
            }

            holder.tvMessage.text = msg.text
            holder.tvTime.text = formattedTime
            holder.tvMessage.setOnLongClickListener(longClickListenerRunnable)

            if (!msg.imageUri.isNullOrBlank()) {
                holder.ivAttachedImage?.visibility = View.VISIBLE
                try {
                    val uri = Uri.parse(msg.imageUri)
                    holder.ivAttachedImage?.setImageURI(uri)
                } catch (_: Exception) {
                    holder.ivAttachedImage?.visibility = View.GONE
                }
            } else {
                holder.ivAttachedImage?.visibility = View.GONE
            }
        } else if (holder is AiViewHolder) {
            if (showDateHeader) {
                holder.layoutDateHeader?.visibility = View.VISIBLE
                holder.tvDateHeader?.text = getFormattedDateHeader(msg.timestamp)
            } else {
                holder.layoutDateHeader?.visibility = View.GONE
            }

            val cleanedText = TextFormatUtils.cleanHumanReadableText(msg.text)
            val mw = markwon
            if (mw != null) {
                mw.setMarkdown(holder.tvMessage, cleanedText)
            } else {
                holder.tvMessage.text = cleanedText
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
        val layoutDateHeader: View? = itemView.findViewById(R.id.layoutDateHeader)
        val tvDateHeader: TextView? = itemView.findViewById(R.id.tvDateHeader)
        val tvMessage: TextView = itemView.findViewById(R.id.tvUserMessage)
        val tvTime: TextView = itemView.findViewById(R.id.tvUserTime)
        val ivAttachedImage: ImageView? = itemView.findViewById(R.id.ivUserAttachedImage)
    }

    class AiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutDateHeader: View? = itemView.findViewById(R.id.layoutDateHeader)
        val tvDateHeader: TextView? = itemView.findViewById(R.id.tvDateHeader)
        val tvMessage: TextView = itemView.findViewById(R.id.tvAiMessage)
        val tvTime: TextView = itemView.findViewById(R.id.tvAiTime)
    }
}
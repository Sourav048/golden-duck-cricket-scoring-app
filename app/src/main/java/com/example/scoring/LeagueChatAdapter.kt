package com.example.scoring

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.util.Linkify
import android.util.Base64
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

class LeagueChatAdapter(
    private val currentUserId: String,
    private val onMessageLongClick: ((LeagueChatMessage) -> Unit)? = null
) : RecyclerView.Adapter<LeagueChatAdapter.ChatViewHolder>() {

    var onReplyClick: ((String) -> Unit)? = null

    private var knownPlayerNames: List<String> = emptyList()
    private var userProfilePics: Map<String, String> = emptyMap()

    fun updateKnownPlayerNames(names: List<String>) {
        this.knownPlayerNames = names.filter { it.isNotBlank() }.sortedByDescending { it.length }
        notifyDataSetChanged()
    }

    fun updateUserProfilePics(pics: Map<String, String>) {
        this.userProfilePics = pics
        notifyDataSetChanged()
    }

    init {
        setHasStableIds(true)
    }

    companion object {
        fun getAvatarColor(key: String): Int {
            val colors = intArrayOf(
                0xFF1E88E5.toInt(), // Blue
                0xFF43A047.toInt(), // Green
                0xFFE53935.toInt(), // Red
                0xFFFB8C00.toInt(), // Orange
                0xFF8E24AA.toInt(), // Purple
                0xFF00ACC1.toInt(), // Cyan
                0xFFD81B60.toInt(), // Pink
                0xFF3949AB.toInt(), // Indigo
                0xFF00897B.toInt()  // Teal
            )
            val hash = abs(key.hashCode())
            return colors[hash % colors.size]
        }

        fun createColoredCircleDrawable(colorInt: Int): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorInt)
            }
        }

        fun buildReplyTextSnippet(msg: LeagueChatMessage): String {
            val isGif = msg.type == "GIF" || isGifUrl(msg.mediaUrl.orEmpty())
            val isVideo = msg.type == "VIDEO"
            val isImage = msg.type == "IMAGE" || (!msg.mediaUrl.isNullOrEmpty() && !isGif && !isVideo)

            val caption = msg.messageText.trim()
            return when {
                isGif -> if (caption.isNotBlank() && caption != msg.mediaUrl) "GIF $caption" else "GIF"
                isVideo -> if (caption.isNotBlank() && caption != msg.mediaUrl) "🎥 Video: $caption" else "🎥 Video"
                isImage -> if (caption.isNotBlank() && caption != msg.mediaUrl) "📷 Photo: $caption" else "📷 Photo"
                else -> msg.messageText.ifBlank { "Message" }
            }
        }

        fun isImageUrl(url: String): Boolean {
            val clean = url.trim().lowercase()
            if (!clean.startsWith("http://") && !clean.startsWith("https://")) return false
            return clean.endsWith(".gif") || clean.endsWith(".png") || clean.endsWith(".jpg") ||
                   clean.endsWith(".jpeg") || clean.endsWith(".webp") ||
                   clean.contains("giphy.com") || clean.contains("tenor.com") ||
                   clean.contains("klipy.com") || clean.contains("gfycat.com") ||
                   clean.contains("i.imgur.com")
        }

        fun isGifUrl(url: String): Boolean {
            val clean = url.trim().lowercase()
            return clean.contains(".gif") || clean.contains("giphy.com") ||
                   clean.contains("tenor.com") || clean.contains("klipy.com") ||
                   clean.contains("gfycat.com")
        }

        fun isVideoUrl(url: String): Boolean {
            val clean = url.trim().lowercase()
            if (!clean.startsWith("http://") && !clean.startsWith("https://")) return false
            return clean.endsWith(".mp4") || clean.endsWith(".mkv") || clean.endsWith(".webm") || clean.endsWith(".3gp") ||
                   clean.contains("instagram.com/reel") || clean.contains("instagram.com/p") ||
                   clean.contains("youtube.com") || clean.contains("youtu.be") ||
                   clean.contains("tiktok.com") || clean.contains("vimeo.com")
        }

        fun extractFirstVideoUrl(text: String): String? {
            if (text.isBlank()) return null
            val words = text.split("\\s+".toRegex())
            for (word in words) {
                var clean = word.trim()
                if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
                    clean = "https://$clean"
                }
                if (isVideoUrl(clean)) {
                    return clean
                }
            }
            return null
        }

        fun extractFirstImageUrl(text: String): String? {
            if (text.isBlank()) return null
            val words = text.split("\\s+".toRegex())
            for (word in words) {
                var clean = word.trim()
                if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
                    clean = "https://$clean"
                }
                if (isImageUrl(clean)) {
                    return clean
                }
            }
            return null
        }

        fun getFormattedDateHeader(timestamp: Long): String {
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

        fun isSameDay(time1: Long, time2: Long): Boolean {
            if (time1 <= 0 && time2 <= 0) return true
            val cal1 = Calendar.getInstance().apply { timeInMillis = if (time1 > 0) time1 else System.currentTimeMillis() }
            val cal2 = Calendar.getInstance().apply { timeInMillis = if (time2 > 0) time2 else System.currentTimeMillis() }
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        fun getFormattedTime(context: android.content.Context, timestamp: Long): String {
            val date = if (timestamp > 0) Date(timestamp) else Date()
            val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
            val pattern = if (is24Hour) "HH:mm" else "hh:mm a"
            val sdf = SimpleDateFormat(pattern, Locale.getDefault())
            return sdf.format(date)
        }

        val linkThumbnailCache = java.util.concurrent.ConcurrentHashMap<String, String>()
        private val bgExecutor = java.util.concurrent.Executors.newFixedThreadPool(2)
        private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        fun extractInstagramShortcode(url: String): String? {
            return try {
                val clean = url.trim()
                val path = when {
                    clean.contains("instagram.com/reel/") -> clean.substringAfter("instagram.com/reel/")
                    clean.contains("instagram.com/p/") -> clean.substringAfter("instagram.com/p/")
                    clean.contains("instagram.com/tv/") -> clean.substringAfter("instagram.com/tv/")
                    else -> return null
                }
                val shortcode = path.substringBefore("/").substringBefore("?").substringBefore("&").trim()
                if (shortcode.isNotBlank()) shortcode else null
            } catch (e: Exception) {
                null
            }
        }

        fun getThumbnailOrGlideModel(url: String): Any {
            val clean = url.trim()
            val cached = linkThumbnailCache[clean]
            if (!cached.isNullOrEmpty()) {
                return parseGlideModel(cached)
            }

            if (clean.contains("youtube.com") || clean.contains("youtu.be")) {
                val videoId = extractYouTubeId(clean)
                if (videoId != null) {
                    return "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                }
            }

            val igShortcode = extractInstagramShortcode(clean)
            if (igShortcode != null) {
                return GlideUrl(
                    "https://www.instagram.com/p/$igShortcode/media/?size=l",
                    LazyHeaders.Builder()
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
                        .addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        .build()
                )
            }

            return parseGlideModel(clean)
        }

        fun fetchOpenGraphThumbnail(url: String, onResolved: (String) -> Unit) {
            val clean = url.trim()
            if (linkThumbnailCache.containsKey(clean)) {
                linkThumbnailCache[clean]?.let { onResolved(it) }
                return
            }

            bgExecutor.execute {
                var resolvedUrl: String? = null
                try {
                    // Try Instagram oEmbed API first if it's an instagram link
                    if (clean.contains("instagram.com")) {
                        val oembedUrl = "https://api.instagram.com/oembed?url=${java.net.URLEncoder.encode(clean, "UTF-8")}"
                        val conn = java.net.URL(oembedUrl).openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000
                        if (conn.responseCode == 200) {
                            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                            val json = org.json.JSONObject(jsonStr)
                            if (json.has("thumbnail_url")) {
                                resolvedUrl = json.getString("thumbnail_url")
                            }
                        }
                        conn.disconnect()
                    }

                    // Fallback to OpenGraph HTML scraper for og:image
                    if (resolvedUrl.isNullOrEmpty() && clean.startsWith("http")) {
                        val conn = java.net.URL(clean).openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
                        conn.instanceFollowRedirects = true
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000
                        if (conn.responseCode == 200 || conn.responseCode == 302 || conn.responseCode == 301) {
                            val html = conn.inputStream.bufferedReader().use { reader ->
                                val sb = StringBuilder()
                                var line: String?
                                var linesRead = 0
                                while (reader.readLine().also { line = it } != null && linesRead < 200) {
                                    sb.append(line).append("\n")
                                    linesRead++
                                }
                                sb.toString()
                            }

                            val ogRegex = """<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                            val ogMatch = ogRegex.find(html)
                            if (ogMatch != null) {
                                resolvedUrl = ogMatch.groupValues[1]
                            } else {
                                val twRegex = """<meta\s+name=["']twitter:image["']\s+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                                val twMatch = twRegex.find(html)
                                if (twMatch != null) {
                                    resolvedUrl = twMatch.groupValues[1]
                                }
                            }
                        }
                        conn.disconnect()
                    }
                } catch (_: Exception) {}

                if (!resolvedUrl.isNullOrEmpty()) {
                    val finalUrl = resolvedUrl
                    linkThumbnailCache[clean] = finalUrl
                    mainHandler.post {
                        onResolved(finalUrl)
                    }
                }
            }
        }

        private fun extractYouTubeId(url: String): String? {
            return try {
                if (url.contains("youtu.be/")) {
                    url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                } else if (url.contains("v=")) {
                    url.substringAfter("v=").substringBefore("&").substringBefore("?")
                } else null
            } catch (e: Exception) {
                null
            }
        }

        fun parseGlideModel(url: String): Any {
            return if (url.startsWith("data:image/") || (!url.startsWith("http") && url.length > 100)) {
                try {
                    val pureBase64 = url.substringAfter("base64,")
                    Base64.decode(pureBase64, Base64.DEFAULT)
                } catch (e: Exception) {
                    url
                }
            } else if (url.startsWith("http")) {
                GlideUrl(
                    url,
                    LazyHeaders.Builder()
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
                        .addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        .build()
                )
            } else {
                url
            }
        }
    }

    private val messages = mutableListOf<LeagueChatMessage>()
    private val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())

    fun submitList(newList: List<LeagueChatMessage>) {
        val diffCallback = ChatDiffCallback(messages, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        messages.clear()
        messages.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemId(position: Int): Long {
        val msg = messages[position]
        return if (msg.id.isNotBlank()) msg.id.hashCode().toLong() else msg.timestamp
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]
        val prevMsg = if (position > 0) messages[position - 1] else null
        val showDateHeader = position == 0 || (prevMsg != null && !isSameDay(msg.timestamp, prevMsg.timestamp))
        holder.bind(msg, showDateHeader)
    }

    override fun getItemCount(): Int = messages.size

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Date Header
        private val layoutDateHeader: LinearLayout = itemView.findViewById(R.id.layoutDateHeader)
        private val tvDateHeader: TextView = itemView.findViewById(R.id.tvDateHeader)

        // Sent views
        private val layoutSent: LinearLayout = itemView.findViewById(R.id.layoutSentMessage)
        private val containerSentContent: LinearLayout = itemView.findViewById(R.id.containerSentContent)
        private val tvSentMessage: TextView = itemView.findViewById(R.id.tvSentMessage)
        private val tvSentTime: TextView = itemView.findViewById(R.id.tvSentTime)
        private val frameSentMedia: FrameLayout = itemView.findViewById(R.id.frameSentMedia)
        private val ivSentMedia: ImageView = itemView.findViewById(R.id.ivSentMedia)
        private val ivSentPlayOverlay: ImageView = itemView.findViewById(R.id.ivSentPlayOverlay)
        private val pbSentMediaLoading: ProgressBar = itemView.findViewById(R.id.pbSentMediaLoading)

        // Received views
        private val layoutReceived: LinearLayout = itemView.findViewById(R.id.layoutReceivedMessage)
        private val containerReceivedContent: LinearLayout = itemView.findViewById(R.id.containerReceivedContent)
        private val tvReceivedSender: TextView = itemView.findViewById(R.id.tvReceivedSender)
        private val tvReceivedMessage: TextView = itemView.findViewById(R.id.tvReceivedMessage)
        private val tvReceivedTime: TextView = itemView.findViewById(R.id.tvReceivedTime)
        private val frameReceivedMedia: FrameLayout = itemView.findViewById(R.id.frameReceivedMedia)
        private val ivReceivedMedia: ImageView = itemView.findViewById(R.id.ivReceivedMedia)
        private val ivReceivedPlayOverlay: ImageView = itemView.findViewById(R.id.ivReceivedPlayOverlay)
        private val pbReceivedMediaLoading: ProgressBar = itemView.findViewById(R.id.pbReceivedMediaLoading)
        private val ivReceivedAvatar: ImageView? = itemView.findViewById(R.id.ivReceivedAvatar)
        private val tvReceivedAvatarPlaceholder: TextView? = itemView.findViewById(R.id.tvReceivedAvatarPlaceholder)

        // Quoted Reply views
        private val layoutSentReply: LinearLayout = itemView.findViewById(R.id.layoutSentReply)
        private val tvSentReplySender: TextView = itemView.findViewById(R.id.tvSentReplySender)
        private val tvSentReplyText: TextView = itemView.findViewById(R.id.tvSentReplyText)
        private val ivSentReplyMedia: ImageView = itemView.findViewById(R.id.ivSentReplyMedia)

        private val layoutReceivedReply: LinearLayout = itemView.findViewById(R.id.layoutReceivedReply)
        private val tvReceivedReplySender: TextView = itemView.findViewById(R.id.tvReceivedReplySender)
        private val tvReceivedReplyText: TextView = itemView.findViewById(R.id.tvReceivedReplyText)
        private val ivReceivedReplyMedia: ImageView = itemView.findViewById(R.id.ivReceivedReplyMedia)

        // Reactions views
        private val layoutSentReactions: LinearLayout = itemView.findViewById(R.id.layoutSentReactions)
        private val tvSentReactions: TextView = itemView.findViewById(R.id.tvSentReactions)

        private val layoutReceivedReactions: LinearLayout = itemView.findViewById(R.id.layoutReceivedReactions)
        private val tvReceivedReactions: TextView = itemView.findViewById(R.id.tvReceivedReactions)

        // System view
        private val layoutSystem: LinearLayout = itemView.findViewById(R.id.layoutSystemMessage)
        private val tvSystemMessage: TextView = itemView.findViewById(R.id.tvSystemMessage)

        fun bind(item: LeagueChatMessage, showDateHeader: Boolean = false) {
            layoutSent.visibility = View.GONE
            layoutReceived.visibility = View.GONE
            layoutSystem.visibility = View.GONE

            val onLongClick = View.OnLongClickListener {
                onMessageLongClick?.invoke(item)
                true
            }
            layoutSent.setOnLongClickListener(onLongClick)
            layoutReceived.setOnLongClickListener(onLongClick)
            containerSentContent.setOnLongClickListener(onLongClick)
            containerReceivedContent.setOnLongClickListener(onLongClick)

            if (showDateHeader) {
                layoutDateHeader.visibility = View.VISIBLE
                tvDateHeader.text = getFormattedDateHeader(item.timestamp)
            } else {
                layoutDateHeader.visibility = View.GONE
            }

            // Bind Reply Preview inside message card
            val hasReply = !item.replyToText.isNullOrEmpty() || !item.replyToSender.isNullOrEmpty() || !item.replyToId.isNullOrEmpty()
            if (hasReply) {
                val sentTextColor = ContextCompat.getColor(itemView.context, R.color.sent_bubble_text)
                val sentTimeColor = ContextCompat.getColor(itemView.context, R.color.sent_bubble_time)
                val receivedTextColor = ContextCompat.getColor(itemView.context, R.color.received_bubble_text)
                val receivedTimeColor = ContextCompat.getColor(itemView.context, R.color.received_bubble_time)

                val referencedMsg = messages.find { it.id == item.replyToId }

                var replySender = if (referencedMsg != null) {
                    if (referencedMsg.senderId == currentUserId) "You" else referencedMsg.senderName.ifBlank { "League Member" }
                } else {
                    item.replyToSender.orEmpty()
                }

                if (replySender.isBlank()) {
                    replySender = "League Member"
                }

                var replyMediaUrl = item.replyToMediaUrl
                if (replyMediaUrl.isNullOrEmpty() && referencedMsg != null) {
                    replyMediaUrl = referencedMsg.mediaUrl ?: referencedMsg.thumbnailUrl
                }

                var replyText = if (referencedMsg != null) {
                    buildReplyTextSnippet(referencedMsg)
                } else {
                    item.replyToText.orEmpty()
                }

                if (replyText.isBlank() || replyText == "Media" || replyText == item.replyToSender) {
                    if (!replyMediaUrl.isNullOrEmpty()) {
                        val isGif = item.replyToMediaType == "GIF" || isGifUrl(replyMediaUrl)
                        val isVid = item.replyToMediaType == "VIDEO"
                        replyText = if (isGif) "GIF" else if (isVid) "🎥 Video" else "📷 Photo"
                    } else {
                        replyText = "Message"
                    }
                }

                val replyClickListener = View.OnClickListener {
                    if (!item.replyToId.isNullOrEmpty()) {
                        onReplyClick?.invoke(item.replyToId)
                    }
                }

                if (item.senderId == currentUserId) {
                    layoutSentReply.visibility = View.VISIBLE
                    tvSentReplySender.text = replySender.ifBlank { "You" }
                    tvSentReplySender.setTextColor(sentTextColor)
                    tvSentReplyText.text = replyText
                    tvSentReplyText.setTextColor(sentTimeColor)

                    if (!replyMediaUrl.isNullOrEmpty()) {
                        ivSentReplyMedia.visibility = View.VISIBLE
                        Glide.with(itemView.context)
                            .load(replyMediaUrl)
                            .centerCrop()
                            .into(ivSentReplyMedia)
                    } else {
                        ivSentReplyMedia.visibility = View.GONE
                        Glide.with(itemView.context).clear(ivSentReplyMedia)
                    }
                    layoutSentReply.setOnClickListener(replyClickListener)
                } else {
                    layoutReceivedReply.visibility = View.VISIBLE
                    tvReceivedReplySender.text = replySender.ifBlank { "League Member" }
                    tvReceivedReplySender.setTextColor(receivedTextColor)
                    tvReceivedReplyText.text = replyText
                    tvReceivedReplyText.setTextColor(receivedTimeColor)

                    if (!replyMediaUrl.isNullOrEmpty()) {
                        ivReceivedReplyMedia.visibility = View.VISIBLE
                        Glide.with(itemView.context)
                            .load(replyMediaUrl)
                            .centerCrop()
                            .into(ivReceivedReplyMedia)
                    } else {
                        ivReceivedReplyMedia.visibility = View.GONE
                        Glide.with(itemView.context).clear(ivReceivedReplyMedia)
                    }
                    layoutReceivedReply.setOnClickListener(replyClickListener)
                }
            } else {
                layoutSentReply.visibility = View.GONE
                layoutReceivedReply.visibility = View.GONE
                layoutSentReply.setOnClickListener(null)
                layoutReceivedReply.setOnClickListener(null)
            }

            // Bind Emoji Reactions
            if (item.reactions.isNotEmpty()) {
                val summary = item.reactions.values
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .joinToString("  ") { (emoji, count) -> if (count > 1) "$emoji $count" else emoji }

                if (item.senderId == currentUserId) {
                    layoutSentReactions.visibility = View.VISIBLE
                    tvSentReactions.text = summary
                } else {
                    layoutReceivedReactions.visibility = View.VISIBLE
                    tvReceivedReactions.text = summary
                }
            } else {
                layoutSentReactions.visibility = View.GONE
                layoutReceivedReactions.visibility = View.GONE
            }

            val formattedTime = getFormattedTime(itemView.context, item.timestamp)

            val extractedVideo = extractFirstVideoUrl(item.messageText)
            val extractedImage = extractFirstImageUrl(item.messageText)

            val effectiveMediaUrl = if (!item.mediaUrl.isNullOrEmpty()) {
                item.mediaUrl
            } else {
                extractedVideo ?: extractedImage
            }

            // Hide completely empty/corrupt records from earlier failed tests that have no text and no valid media
            if (item.type != "SYSTEM" && item.messageText.isBlank() && effectiveMediaUrl.isNullOrEmpty()) {
                return
            }

            if (item.type == "SYSTEM") {
                layoutSystem.visibility = View.VISIBLE
                tvSystemMessage.text = item.messageText
            } else if (item.senderId == currentUserId) {
                layoutSent.visibility = View.VISIBLE
                bindMessageContent(
                    item = item,
                    effectiveMediaUrl = effectiveMediaUrl,
                    tvText = tvSentMessage,
                    tvTime = tvSentTime,
                    frameMedia = frameSentMedia,
                    ivMedia = ivSentMedia,
                    ivPlayOverlay = ivSentPlayOverlay,
                    pbLoading = pbSentMediaLoading,
                    formattedTime = formattedTime,
                    onLongClick = onLongClick,
                    isSent = true
                )
            } else {
                layoutReceived.visibility = View.VISIBLE
                tvReceivedSender.text = item.senderName.ifBlank { "League Member" }

                // Bind Avatar for received message
                val avatarUrl = item.senderProfilePic.takeIf { !it.isNullOrBlank() }
                    ?: userProfilePics[item.senderId]

                if (!avatarUrl.isNullOrBlank()) {
                    ivReceivedAvatar?.visibility = View.VISIBLE
                    tvReceivedAvatarPlaceholder?.visibility = View.GONE
                    Glide.with(itemView.context)
                        .load(avatarUrl)
                        .circleCrop()
                        .placeholder(R.drawable.bg_circle_avatar)
                        .into(ivReceivedAvatar!!)
                } else {
                    ivReceivedAvatar?.visibility = View.GONE
                    tvReceivedAvatarPlaceholder?.visibility = View.VISIBLE
                    val initial = item.senderName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "P"
                    tvReceivedAvatarPlaceholder?.text = initial
                    val colorKey = item.senderId.ifBlank { item.senderName }
                    tvReceivedAvatarPlaceholder?.background = createColoredCircleDrawable(getAvatarColor(colorKey))
                }

                bindMessageContent(
                    item = item,
                    effectiveMediaUrl = effectiveMediaUrl,
                    tvText = tvReceivedMessage,
                    tvTime = tvReceivedTime,
                    frameMedia = frameReceivedMedia,
                    ivMedia = ivReceivedMedia,
                    ivPlayOverlay = ivReceivedPlayOverlay,
                    pbLoading = pbReceivedMediaLoading,
                    formattedTime = formattedTime,
                    onLongClick = onLongClick,
                    isSent = false
                )
            }
        }

        private fun bindMessageContent(
            item: LeagueChatMessage,
            effectiveMediaUrl: String?,
            tvText: TextView,
            tvTime: TextView,
            frameMedia: FrameLayout,
            ivMedia: ImageView,
            ivPlayOverlay: ImageView,
            pbLoading: ProgressBar,
            formattedTime: String,
            onLongClick: View.OnLongClickListener,
            isSent: Boolean
        ) {
            tvTime.text = formattedTime
            tvTime.visibility = View.VISIBLE

            val context = itemView.context
            val textColor = androidx.core.content.ContextCompat.getColor(
                context,
                if (isSent) R.color.sent_bubble_text else R.color.received_bubble_text
            )
            val timeColor = androidx.core.content.ContextCompat.getColor(
                context,
                if (isSent) R.color.sent_bubble_time else R.color.received_bubble_time
            )

            tvTime.setTextColor(timeColor)

            val isVideo = item.type == "VIDEO" || (effectiveMediaUrl != null && isVideoUrl(effectiveMediaUrl))

            // Handle Text & Clickable Blue Links & @Mentions
            if (item.messageText.isNotBlank()) {
                tvText.visibility = View.VISIBLE
                tvText.setTextColor(textColor)
                tvText.setOnLongClickListener(onLongClick)

                if (item.messageText.contains("@")) {
                    tvText.text = applyMentionSpans(item.messageText, textColor)
                } else {
                    tvText.text = item.messageText
                }

                val hasLinks = try { Linkify.addLinks(tvText, Linkify.WEB_URLS) } catch (_: Exception) { false }
                if (hasLinks) {
                    tvText.movementMethod = LinkMovementMethod.getInstance()
                    val linkColor = if (textColor == Color.WHITE) Color.parseColor("#80D8FF") else Color.parseColor("#0288D1")
                    tvText.setLinkTextColor(linkColor)
                } else {
                    tvText.movementMethod = null
                }
            } else {
                tvText.visibility = View.GONE
                tvText.movementMethod = null
            }

            // Handle Media (Image, GIF, Video)
            if (!effectiveMediaUrl.isNullOrEmpty()) {
                frameMedia.visibility = View.VISIBLE
                ivPlayOverlay.visibility = if (isVideo) View.VISIBLE else View.GONE

                frameMedia.setOnLongClickListener(onLongClick)
                ivMedia.setOnLongClickListener(onLongClick)

                val loadedUrl = ivMedia.tag as? String
                if (loadedUrl == effectiveMediaUrl && ivMedia.drawable != null) {
                    pbLoading.visibility = View.GONE
                } else {
                    ivMedia.tag = effectiveMediaUrl
                    pbLoading.visibility = View.VISIBLE

                    val glideModel = getThumbnailOrGlideModel(effectiveMediaUrl)

                    // Trigger OpenGraph / oEmbed background fetch for Instagram or web links if not cached yet
                    val isWebOrSocialLink = effectiveMediaUrl.startsWith("http") &&
                                           !isGifUrl(effectiveMediaUrl) &&
                                           !effectiveMediaUrl.endsWith(".jpg") &&
                                           !effectiveMediaUrl.endsWith(".jpeg") &&
                                           !effectiveMediaUrl.endsWith(".png")

                    if (isWebOrSocialLink && !linkThumbnailCache.containsKey(effectiveMediaUrl)) {
                        fetchOpenGraphThumbnail(effectiveMediaUrl) { resolvedOgUrl ->
                            if (ivMedia.tag == effectiveMediaUrl) {
                                Glide.with(itemView.context)
                                    .asDrawable()
                                    .load(parseGlideModel(resolvedOgUrl))
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .optionalFitCenter()
                                    .listener(object : RequestListener<Drawable> {
                                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                                            pbLoading.visibility = View.GONE
                                            return false
                                        }

                                        override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                                            pbLoading.visibility = View.GONE
                                            frameMedia.visibility = View.VISIBLE
                                            return false
                                        }
                                    })
                                    .into(ivMedia)
                            }
                        }
                    }

                    if (isGifUrl(effectiveMediaUrl)) {
                        Glide.with(itemView.context)
                            .asGif()
                            .load(glideModel)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .optionalFitCenter()
                            .listener(object : RequestListener<com.bumptech.glide.load.resource.gif.GifDrawable> {
                                override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any?,
                                    target: Target<com.bumptech.glide.load.resource.gif.GifDrawable>,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    pbLoading.visibility = View.GONE
                                    frameMedia.visibility = View.GONE
                                    ivMedia.tag = null
                                    return false
                                }

                                override fun onResourceReady(
                                    resource: com.bumptech.glide.load.resource.gif.GifDrawable,
                                    model: Any,
                                    target: Target<com.bumptech.glide.load.resource.gif.GifDrawable>?,
                                    dataSource: DataSource,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    pbLoading.visibility = View.GONE
                                    return false
                                }
                            })
                            .into(ivMedia)
                    } else {
                        Glide.with(itemView.context)
                            .asDrawable()
                            .load(glideModel)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .optionalFitCenter()
                            .listener(object : RequestListener<Drawable> {
                                override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any?,
                                    target: Target<Drawable>,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    // Fallback for Instagram or social links
                                    if (effectiveMediaUrl.contains("instagram.com")) {
                                        fetchOpenGraphThumbnail(effectiveMediaUrl) { resolvedUrl ->
                                            if (ivMedia.tag == effectiveMediaUrl) {
                                                Glide.with(itemView.context)
                                                    .asDrawable()
                                                    .load(parseGlideModel(resolvedUrl))
                                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                                    .optionalFitCenter()
                                                    .listener(object : RequestListener<Drawable> {
                                                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                                                            pbLoading.visibility = View.GONE
                                                            frameMedia.visibility = View.GONE
                                                            return false
                                                        }

                                                        override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                                                            pbLoading.visibility = View.GONE
                                                            frameMedia.visibility = View.VISIBLE
                                                            return false
                                                        }
                                                    })
                                                    .into(ivMedia)
                                            }
                                        }
                                        return true
                                    }

                                    pbLoading.visibility = View.GONE
                                    frameMedia.visibility = View.GONE
                                    ivMedia.tag = null
                                    return false
                                }

                                override fun onResourceReady(
                                    resource: Drawable,
                                    model: Any,
                                    target: Target<Drawable>?,
                                    dataSource: DataSource,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    pbLoading.visibility = View.GONE
                                    return false
                                }
                            })
                            .into(ivMedia)
                    }
                }

                // On click media listener
                val onMediaClick = View.OnClickListener {
                    val context = itemView.context
                    if (isVideo) {
                        if (effectiveMediaUrl.contains("instagram.com") || effectiveMediaUrl.contains("youtube.com") || effectiveMediaUrl.contains("youtu.be") || effectiveMediaUrl.contains("tiktok.com")) {
                            showInAppWebView(context, effectiveMediaUrl)
                        } else {
                            showFullScreenVideo(context, effectiveMediaUrl)
                        }
                    } else {
                        // Image or GIF: Open full screen dialog
                        showFullScreenImage(context, effectiveMediaUrl)
                    }
                }
                frameMedia.setOnClickListener(onMediaClick)
                ivMedia.setOnClickListener(onMediaClick)
                ivPlayOverlay.setOnClickListener(onMediaClick)
            } else {
                ivMedia.tag = null
                try {
                    Glide.with(itemView.context).clear(ivMedia)
                } catch (_: Exception) {}
                frameMedia.visibility = View.GONE
            }
        }

        private fun applyMentionSpans(text: String, textColor: Int): SpannableString {
            val spannable = SpannableString(text)
            val mentionColor = if (textColor == Color.WHITE) Color.parseColor("#80D8FF") else Color.parseColor("#0288D1")

            val allNames = mutableSetOf<String>()
            allNames.addAll(knownPlayerNames)
            for (msg in messages) {
                if (msg.senderName.isNotBlank()) {
                    allNames.add(msg.senderName.trim())
                }
            }

            val sortedNames = allNames.filter { it.isNotBlank() }.sortedByDescending { it.length }
            val spannedRanges = mutableListOf<Pair<Int, Int>>()
            val lowerText = text.lowercase()

            // 1. Highlight full player names with spaces (e.g., @Prime krishna)
            for (name in sortedNames) {
                val targetAtName = "@${name.lowercase()}"
                var searchStart = 0
                while (searchStart < lowerText.length) {
                    val foundAt = lowerText.indexOf(targetAtName, searchStart)
                    if (foundAt == -1) break

                    val endAt = foundAt + targetAtName.length
                    val isOverlap = spannedRanges.any { (s, e) -> foundAt >= s && foundAt < e }
                    if (!isOverlap) {
                        spannable.setSpan(
                            ForegroundColorSpan(mentionColor),
                            foundAt,
                            endAt,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        spannable.setSpan(
                            StyleSpan(Typeface.BOLD),
                            foundAt,
                            endAt,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        spannedRanges.add(Pair(foundAt, endAt))
                    }
                    searchStart = endAt
                }
            }

            // 2. Fallback for any other single-word @Mentions
            val matcher = Pattern.compile("@[A-Za-z0-9_\\-]+").matcher(text)
            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                val isOverlap = spannedRanges.any { (s, e) -> start >= s && start < e }
                if (!isOverlap) {
                    spannable.setSpan(
                        ForegroundColorSpan(mentionColor),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannedRanges.add(Pair(start, end))
                }
            }

            return spannable
        }

        private fun showInAppWebView(context: android.content.Context, webUrl: String) {
            val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_inapp_webview)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))

            val webView = dialog.findViewById<WebView>(R.id.webViewPlayer)
            val pbLoading = dialog.findViewById<ProgressBar>(R.id.pbWebLoading)
            val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseWebView)
            val tvTitle = dialog.findViewById<TextView>(R.id.tvWebViewTitle)

            tvTitle?.text = if (webUrl.contains("instagram.com")) "Instagram Reel" else if (webUrl.contains("youtube.com") || webUrl.contains("youtu.be")) "YouTube Video" else "In-App Video"

            webView?.settings?.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
            }

            webView?.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 90) {
                        pbLoading?.visibility = View.GONE
                    } else {
                        pbLoading?.visibility = View.VISIBLE
                    }
                }
            }

            webView?.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }

            webView?.loadUrl(webUrl)

            btnClose?.setOnClickListener {
                webView?.stopLoading()
                webView?.loadUrl("about:blank")
                dialog.dismiss()
            }

            dialog.setOnDismissListener {
                webView?.stopLoading()
                webView?.loadUrl("about:blank")
            }

            dialog.show()
        }

        private fun showFullScreenVideo(context: android.content.Context, mediaUrl: String) {
            val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_full_screen_video)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))

            val vvFullScreen = dialog.findViewById<VideoView>(R.id.vvFullScreen)
            val pbVideoLoading = dialog.findViewById<ProgressBar>(R.id.pbVideoLoading)
            val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseVideo)

            val videoUri = if (mediaUrl.startsWith("data:video/")) {
                val pureBase64 = mediaUrl.substringAfter("base64,")
                val bytes = Base64.decode(pureBase64, Base64.DEFAULT)
                val tempFile = File(context.cacheDir, "chat_video.mp4")
                FileOutputStream(tempFile).use { it.write(bytes) }
                androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", tempFile
                )
            } else {
                Uri.parse(mediaUrl)
            }

            val mediaController = MediaController(context)
            mediaController.setAnchorView(vvFullScreen)
            vvFullScreen?.setMediaController(mediaController)

            vvFullScreen?.setVideoURI(videoUri)
            vvFullScreen?.setOnPreparedListener { mp ->
                pbVideoLoading?.visibility = View.GONE
                mp.isLooping = true
                vvFullScreen.start()
            }
            vvFullScreen?.setOnErrorListener { _, _, _ ->
                pbVideoLoading?.visibility = View.GONE
                Toast.makeText(context, "Cannot play video stream", Toast.LENGTH_SHORT).show()
                true
            }

            btnClose?.setOnClickListener {
                vvFullScreen?.stopPlayback()
                dialog.dismiss()
            }

            dialog.setOnDismissListener {
                vvFullScreen?.stopPlayback()
            }

            dialog.show()
        }

        private fun showFullScreenImage(context: android.content.Context, mediaUrl: String) {
            val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_full_screen_photo)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))

            val ivFullScreen = dialog.findViewById<ImageView>(R.id.ivFullScreen)
            val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseFull)
            dialog.findViewById<View>(R.id.btnRotatePhoto)?.visibility = View.GONE
            dialog.findViewById<View>(R.id.btnSavePhoto)?.visibility = View.GONE

            if (ivFullScreen != null) {
                val glideModel = parseGlideModel(mediaUrl)

                if (isGifUrl(mediaUrl)) {
                    Glide.with(context)
                        .asGif()
                        .load(glideModel)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(ivFullScreen)
                } else {
                    Glide.with(context)
                        .asDrawable()
                        .load(glideModel)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(ivFullScreen)
                }
            }

            btnClose?.setOnClickListener { dialog.dismiss() }
            ivFullScreen?.setOnClickListener { dialog.dismiss() }

            dialog.show()
        }
    }

    private class ChatDiffCallback(
        private val oldList: List<LeagueChatMessage>,
        private val newList: List<LeagueChatMessage>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return if (oldItem.id.isNotBlank() && newItem.id.isNotBlank()) {
                oldItem.id == newItem.id
            } else {
                oldItem.timestamp == newItem.timestamp && oldItem.senderId == newItem.senderId
            }
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}


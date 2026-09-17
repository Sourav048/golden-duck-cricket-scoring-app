package com.example.scoring

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.bumptech.glide.Glide
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Manages WhatsApp-style MessagingStyle notifications, a 10-message FIFO queue,
 * inline direct replies, and unified multi-chat group summary tray.
 */
object ChatNotificationHelper {
    private const val PREFS_NAME = "chat_notif_prefs"
    private const val CHANNEL_ID_ALERT = "league_chat_channel_alert"
    private const val CHANNEL_ID_SILENT = "league_chat_channel_silent"

    const val GROUP_KEY_LEAGUE_CHAT = "com.example.scoring.LEAGUE_CHAT_GROUP"
    const val SUMMARY_NOTIFICATION_ID = 99991
    const val KEY_TEXT_REPLY = "key_text_reply"
    const val ACTION_UPDATE_CHAT_BADGE = "com.example.scoring.UPDATE_CHAT_BADGE"

    fun getUnreadMessageCount(context: Context): Int {
        val notifPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeLeagues = getActiveLeagues(notifPrefs)
        var total = 0
        for (leagueId in activeLeagues) {
            val queue = getQueue(notifPrefs, "queue_$leagueId")
            total += queue.size
        }
        return total
    }

    fun getNotificationId(leagueId: String): Int {
        return abs("chat_$leagueId".hashCode())
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Alert Channel (Sound, Vibrate & Heads-Up Banner)
            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERT,
                "League Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for new League Chat messages"
                enableVibration(true)
                setShowBadge(true)
            }

            // 2. Silent Channel (Updates in-place without sound/vibration)
            val silentChannel = NotificationChannel(
                CHANNEL_ID_SILENT,
                "League Chat Updates (Silent)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Silent updates for grouped League Chat messages"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(alertChannel)
            notificationManager.createNotificationChannel(silentChannel)
        }
    }

    /**
     * Data class representing a queued message.
     */
    data class QueueMessage(
        val senderName: String,
        val text: String,
        val timestamp: Long = System.currentTimeMillis(),
        val senderProfilePic: String? = null,
        val msgId: String = ""
    )

    private val recentMsgKeys = Collections.synchronizedMap(object : LinkedHashMap<String, Long>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > 200
        }
    })

    @Synchronized
    fun handleIncomingChatMessage(
        context: Context,
        leagueId: String,
        senderName: String,
        messageContent: String,
        replyRecipientId: String? = null,
        isPersonalChat: Boolean = false,
        senderProfilePic: String? = null,
        senderId: String? = null,
        msgId: String? = null
    ) {
        if (leagueId.isBlank()) return

        val now = System.currentTimeMillis()

        // 1. Thread-safe Message Deduplication Check (by msgId or sender+text)
        val dedupeKey = if (!msgId.isNullOrEmpty()) {
            "msg_$msgId"
        } else {
            "text_${leagueId}_${senderName}_${messageContent}"
        }

        synchronized(recentMsgKeys) {
            val lastSeenTime = recentMsgKeys[dedupeKey]
            if (lastSeenTime != null && (now - lastSeenTime) < 15000) {
                Log.d("ChatNotif", "Thread-safe deduplication blocked duplicate notification for $dedupeKey")
                return
            }
            recentMsgKeys[dedupeKey] = now
        }

        val notifPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val effectiveSenderName = if (!replyRecipientId.isNullOrEmpty() && senderName.contains("(")) {
            senderName
        } else {
            senderName
        }

        val gullyPrefs = context.getSharedPreferences("gully_prefs", Context.MODE_PRIVATE)
        val myUserId = gullyPrefs.getString("chat_sender_id", null)

        // 2. Do NOT notify for messages sent by MYSELF
        if (!myUserId.isNullOrEmpty() && senderId == myUserId) {
            return
        }

        // 3. Do NOT notify if user is currently inside LeagueChatActivity viewing this chat
        if (LeagueChatActivity.activeLeagueId == leagueId) {
            clearNotificationForLeague(context, leagueId)
            return
        }

        createNotificationChannels(context)

        val finalSenderName = if (!replyRecipientId.isNullOrEmpty() && myUserId == replyRecipientId && !senderName.contains("(")) {
            "$senderName(Mentioned You🗣️🗣️)"
        } else {
            senderName
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = getNotificationId(leagueId)

        val keyQueue = "queue_$leagueId"

        // Track active leagues with unread messages
        trackActiveLeague(notifPrefs, leagueId)

        // Retrieve queue for this league
        val currentQueue = getQueue(notifPrefs, keyQueue)

        // Add new message
        currentQueue.add(QueueMessage(finalSenderName, messageContent, now, senderProfilePic, msgId ?: ""))

        // Maintain max 10 messages (top leaves, newest at bottom)
        while (currentQueue.size > 10) {
            currentQueue.removeAt(0)
        }

        // Save updated queue
        saveQueue(notifPrefs, keyQueue, currentQueue)

        val count = currentQueue.size

        // Alert rule:
        // Alert on 1st message, up to 5th message (1..5), and 10th message (10, 15, 20...)
        val shouldAlert = (count in 1..5) || (count == 10) || (count > 10 && count % 5 == 0)
        val channelId = if (shouldAlert) CHANNEL_ID_ALERT else CHANNEL_ID_SILENT

        // Main Intent when user taps notification
        val intent = Intent(context, LeagueChatActivity::class.java).apply {
            putExtra("LEAGUE_ID", leagueId)
            putExtra("gully_id", leagueId)
            putExtra("leagueId", leagueId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val flagImmutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val flagMutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else flagImmutable

        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable
        )

        // 1. Build Person objects for MessagingStyle
        val userPerson = Person.Builder()
            .setName("You")
            .setKey(myUserId ?: "user_me")
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
            .setConversationTitle(if (isPersonalChat) null else leagueId)
            .setGroupConversation(!isPersonalChat)

        for (msg in currentQueue) {
            val senderPersonBuilder = Person.Builder()
                .setName(msg.senderName)
                .setKey(msg.senderName)

            val avatarIcon = getAvatarIconForSender(context, msg.senderName, msg.senderProfilePic)
            if (avatarIcon != null) {
                senderPersonBuilder.setIcon(avatarIcon)
            }

            messagingStyle.addMessage(
                msg.text,
                msg.timestamp,
                senderPersonBuilder.build()
            )
        }

        // 2. Build Direct Reply RemoteInput Action
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Replying in $leagueId...")
            .build()

        val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
            putExtra("LEAGUE_ID", leagueId)
            putExtra("NOTIF_ID", notifId)
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or flagMutable
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_onesignal_default,
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val deleteIntent = Intent(context, NotificationDismissReceiver::class.java).apply {
            putExtra("LEAGUE_ID", leagueId)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            notifId,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable
        )

        val totalUnread = getUnreadMessageCount(context)

        // 3. Build Individual Child Notification
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_onesignal_default)
            .setColor(0xFF00695C.toInt())
            .setSubText(leagueId)
            .setNumber(totalUnread)
            .setStyle(messagingStyle)
            .setGroup(GROUP_KEY_LEAGUE_CHAT)
            .setGroupSummary(false)
            .setPriority(if (shouldAlert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .addAction(replyAction)

        if (!shouldAlert) {
            builder.setSound(null)
            builder.setVibrate(null)
            builder.setDefaults(0)
        } else {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(soundUri)
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        try {
            notificationManager.notify(notifId, builder.build())
        } catch (e: Exception) {
            Log.e("ChatNotif", "Failed to post child notification: ${e.message}")
        }

        // 4. Update Multi-Chat Group Summary Notification Tray
        updateGroupSummaryNotification(context, notificationManager)
        try {
            val intent = Intent(ACTION_UPDATE_CHAT_BADGE).setPackage(context.packageName)
            context.sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    /**
     * Updates or posts the Group Summary Notification Tray combining all active chat queues.
     */
    private fun updateGroupSummaryNotification(context: Context, notificationManager: NotificationManager) {
        val notifPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeLeagues = getActiveLeagues(notifPrefs)

        if (activeLeagues.isEmpty()) {
            notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
            return
        }

        var totalMessageCount = 0
        val summaryLines = mutableListOf<String>()

        for (lId in activeLeagues) {
            val q = getQueue(notifPrefs, "queue_$lId")
            if (q.isNotEmpty()) {
                totalMessageCount += q.size
                val lastMsg = q.last()
                summaryLines.add("$lId: ${lastMsg.senderName}: ${lastMsg.text}")
            }
        }

        if (totalMessageCount == 0) {
            notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
            return
        }

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("$totalMessageCount new messages from ${activeLeagues.size} chats")
            .setSummaryText("League Chats")

        for (line in summaryLines.take(5)) {
            inboxStyle.addLine(line)
        }

        val intent = Intent(context, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Fix 2: Use CHANNEL_ID_ALERT for group summary on Android 14/15/16 so children aren't silenced
        val summaryBuilder = NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
            .setSmallIcon(R.drawable.ic_stat_onesignal_default)
            .setColor(0xFF00695C.toInt())
            .setContentTitle("League Chats")
            .setContentText("$totalMessageCount new messages from ${activeLeagues.size} chats")
            .setNumber(totalMessageCount)
            .setStyle(inboxStyle)
            .setGroup(GROUP_KEY_LEAGUE_CHAT)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryBuilder.build())
        } catch (e: Exception) {
            Log.e("ChatNotif", "Failed to post summary notification: ${e.message}")
        }
    }

    fun clearNotificationForLeague(context: Context, leagueId: String) {
        if (leagueId.isBlank()) return
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = getNotificationId(leagueId)
        notificationManager.cancel(notifId)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentQueue = getQueue(prefs, "queue_$leagueId")
        if (currentQueue.isNotEmpty()) {
            markMessagesAsDismissed(prefs, currentQueue)
            prefs.edit().remove("queue_$leagueId").apply()
        }

        untrackActiveLeague(prefs, leagueId)

        // Update or cancel group summary notification
        updateGroupSummaryNotification(context, notificationManager)
        try {
            val intent = Intent(ACTION_UPDATE_CHAT_BADGE).setPackage(context.packageName)
            context.sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    private fun isMessageDismissed(prefs: SharedPreferences, msgId: String?, senderName: String, text: String): Boolean {
        val dismissedSet = prefs.getStringSet("dismissed_msg_keys", emptySet()) ?: emptySet()
        if (!msgId.isNullOrBlank() && dismissedSet.contains("id:$msgId")) {
            return true
        }
        val textKey = "key:${senderName.trim()}:${text.trim()}"
        return dismissedSet.contains(textKey)
    }

    private fun markMessagesAsDismissed(prefs: SharedPreferences, messages: List<QueueMessage>) {
        val dismissedSet = prefs.getStringSet("dismissed_msg_keys", emptySet())?.toMutableSet() ?: mutableSetOf()
        for (m in messages) {
            if (m.msgId.isNotBlank()) {
                dismissedSet.add("id:${m.msgId}")
            }
            if (m.senderName.isNotBlank() && m.text.isNotBlank()) {
                dismissedSet.add("key:${m.senderName.trim()}:${m.text.trim()}")
            }
        }
        while (dismissedSet.size > 200) {
            val first = dismissedSet.firstOrNull() ?: break
            dismissedSet.remove(first)
        }
        prefs.edit().putStringSet("dismissed_msg_keys", dismissedSet).apply()
    }

    private fun trackActiveLeague(prefs: SharedPreferences, leagueId: String) {
        val set = prefs.getStringSet("active_leagues", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.add(leagueId)
        prefs.edit().putStringSet("active_leagues", set).apply()
    }

    private fun untrackActiveLeague(prefs: SharedPreferences, leagueId: String) {
        val set = prefs.getStringSet("active_leagues", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.remove(leagueId)
        prefs.edit().putStringSet("active_leagues", set).apply()
    }

    private fun getActiveLeagues(prefs: SharedPreferences): Set<String> {
        return prefs.getStringSet("active_leagues", emptySet()) ?: emptySet()
    }

    private fun getQueue(prefs: SharedPreferences, key: String): MutableList<QueueMessage> {
        val jsonStr = prefs.getString(key, null) ?: return mutableListOf()
        val list = mutableListOf<QueueMessage>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val s = obj.optString("s")
                val t = obj.optString("t")
                val ts = obj.optLong("ts", System.currentTimeMillis())
                val p = obj.optString("p").ifEmpty { null }
                val id = obj.optString("id").ifEmpty { "" }
                list.add(QueueMessage(s, t, ts, p, id))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveQueue(prefs: SharedPreferences, key: String, queue: List<QueueMessage>) {
        val jsonArray = JSONArray()
        for (msg in queue) {
            val obj = JSONObject().apply {
                put("s", msg.senderName)
                put("t", msg.text)
                put("ts", msg.timestamp)
                if (!msg.senderProfilePic.isNullOrEmpty()) {
                    put("p", msg.senderProfilePic)
                }
                if (msg.msgId.isNotBlank()) {
                    put("id", msg.msgId)
                }
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(key, jsonArray.toString()).apply()
    }

    private fun getAvatarIconForSender(
        context: Context,
        senderName: String,
        profilePicUrl: String?
    ): IconCompat {
        if (!profilePicUrl.isNullOrEmpty()) {
            try {
                val bitmap = Glide.with(context.applicationContext)
                    .asBitmap()
                    .load(profilePicUrl)
                    .circleCrop()
                    .submit(128, 128)
                    .get(2, TimeUnit.SECONDS)
                if (bitmap != null) {
                    return IconCompat.createWithBitmap(bitmap)
                }
            } catch (_: Exception) {}
        }

        val bitmap = createInitialAvatarBitmap(senderName, 128)
        return IconCompat.createWithBitmap(bitmap)
    }

    private fun createInitialAvatarBitmap(name: String, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = LeagueChatAdapter.getAvatarColor(name)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        paint.color = Color.WHITE
        paint.textSize = sizePx * 0.45f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER

        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "P"
        val fontMetrics = paint.fontMetrics
        val baseline = sizePx / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(initial, sizePx / 2f, baseline, paint)

        return bitmap
    }
}

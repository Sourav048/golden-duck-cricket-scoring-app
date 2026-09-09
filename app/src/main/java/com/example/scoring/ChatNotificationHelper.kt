package com.example.scoring

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.text.Html
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject

object ChatNotificationHelper {
    private const val PREFS_NAME = "chat_notif_prefs"
    private const val CHANNEL_ID_ALERT = "league_chat_channel_alert"
    private const val CHANNEL_ID_SILENT = "league_chat_channel_silent"

    fun getNotificationId(leagueId: String): Int {
        return "chat_$leagueId".hashCode()
    }

    private fun createNotificationChannels(context: Context) {
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
            }

            notificationManager.createNotificationChannel(alertChannel)
            notificationManager.createNotificationChannel(silentChannel)
        }
    }

    fun handleIncomingChatMessage(
        context: Context,
        leagueId: String,
        senderName: String,
        messageContent: String,
        replyRecipientId: String? = null
    ) {
        if (leagueId.isBlank()) return
        createNotificationChannels(context)

        val gullyPrefs = context.getSharedPreferences("gully_prefs", Context.MODE_PRIVATE)
        val myUserId = gullyPrefs.getString("chat_sender_id", null)

        val effectiveSenderName = if (!replyRecipientId.isNullOrEmpty() && myUserId == replyRecipientId && !senderName.contains("(")) {
            "$senderName(Mentioned You🗣️🗣️)"
        } else {
            senderName
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = getNotificationId(leagueId)

        val notifPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keyQueue = "queue_$leagueId"

        // Always retrieve accumulated unread queue for this league until cleared
        val currentQueue = getQueue(notifPrefs, keyQueue)

        // Add new message
        currentQueue.add(Pair(effectiveSenderName, messageContent))

        // Hold max 10 messages (top exits, newest at bottom)
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

        val intent = Intent(context, LeagueChatActivity::class.java).apply {
            putExtra("LEAGUE_ID", leagueId)
            putExtra("gully_id", leagueId)
            putExtra("leagueId", leagueId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_onesignal_default)
            .setColor(0xFF00695C.toInt())
            .setSubText(leagueId)
            .setGroup("group_chat_$leagueId")
            .setGroupSummary(false)
            .setPriority(if (shouldAlert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (!shouldAlert) {
            builder.setSound(null)
            builder.setVibrate(null)
            builder.setDefaults(0)
        } else {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(soundUri)
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        val firstBaseSender = getBaseSenderName(currentQueue[0].first)
        val isSingleSender = currentQueue.all { getBaseSenderName(it.first) == firstBaseSender }

        if (count == 1) {
            val (singleSender, singleText) = currentQueue[0]
            builder.setContentTitle(singleSender)
            builder.setContentText(singleText)
        } else {
            val singleSenderHeaders = currentQueue.map { it.first }
            val displaySenderTitle = computeSenderHeaderWithStatus(firstBaseSender, singleSenderHeaders)

            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(displaySenderTitle)

            if (isSingleSender) {
                for ((_, t) in currentQueue) {
                    inboxStyle.addLine(t)
                }
            } else {
                var lastBaseSender: String? = null
                for ((s, t) in currentQueue) {
                    val currentBase = getBaseSenderName(s)
                    if (currentBase != lastBaseSender) {
                        val senderHeaders = currentQueue.filter { getBaseSenderName(it.first) == currentBase }.map { it.first }
                        val senderWithStatus = computeSenderHeaderWithStatus(currentBase, senderHeaders)

                        val boldSender = Html.fromHtml("<b>$senderWithStatus</b>", Html.FROM_HTML_MODE_LEGACY)
                        inboxStyle.addLine(boldSender)
                        lastBaseSender = currentBase
                    }
                    inboxStyle.addLine(t)
                }
            }

            builder.setStyle(inboxStyle)
            builder.setContentTitle(displaySenderTitle)
            builder.setContentText(
                if (isSingleSender) currentQueue.last().second
                else "${currentQueue.last().first}: ${currentQueue.last().second}"
            )
        }

        notificationManager.notify(notifId, builder.build())
    }

    private fun getBaseSenderName(fullName: String): String {
        val idx = fullName.indexOf("(")
        return if (idx > 0) fullName.substring(0, idx).trim() else fullName.trim()
    }

    private fun computeSenderHeaderWithStatus(senderBaseName: String, senderHeaders: List<String>): String {
        val hasMention = senderHeaders.any { it.contains("Mentioned") }
        val hasReply = senderHeaders.any { it.contains("Replied") }

        return when {
            hasMention && hasReply -> "$senderBaseName(Mentioned and Replied you🗣️🗣️)"
            hasMention -> "$senderBaseName(Mentioned You🗣️🗣️)"
            hasReply -> "$senderBaseName(Replied to you)"
            else -> senderBaseName
        }
    }

    fun clearNotificationForLeague(context: Context, leagueId: String) {
        if (leagueId.isBlank()) return
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = getNotificationId(leagueId)
        notificationManager.cancel(notifId)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("queue_$leagueId").apply()
    }

    private fun getQueue(prefs: android.content.SharedPreferences, key: String): MutableList<Pair<String, String>> {
        val jsonStr = prefs.getString(key, null) ?: return mutableListOf()
        val list = mutableListOf<Pair<String, String>>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(Pair(obj.getString("s"), obj.getString("t")))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveQueue(prefs: android.content.SharedPreferences, key: String, queue: List<Pair<String, String>>) {
        val jsonArray = JSONArray()
        for ((s, t) in queue) {
            val obj = JSONObject().apply {
                put("s", s)
                put("t", t)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(key, jsonArray.toString()).apply()
    }
}

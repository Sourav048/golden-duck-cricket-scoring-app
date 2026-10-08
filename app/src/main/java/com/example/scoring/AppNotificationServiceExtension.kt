package com.example.scoring

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension
import java.util.concurrent.Executors

/**
 * OneSignal 5.x Service Extension that intercepts incoming notification payloads
 * in foreground, background, and killed states.
 * Suppresses default OneSignal single notifications and routes chat messages and
 * match updates through custom channels with Mute/Unmute support.
 */
class AppNotificationServiceExtension : INotificationServiceExtension {
    companion object {
        private val executor = Executors.newSingleThreadExecutor()

        fun postOrUpdateMatchNotification(
            context: Context,
            leagueId: String?,
            matchId: String,
            title: String,
            body: String,
            isAlertEvent: Boolean
        ) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Ensure notification channels exist
            ChatNotificationHelper.createNotificationChannels(context)

            val isMuted = MatchNotificationReceiver.isMatchMuted(context, matchId)

            // Determine Channel ID:
            // Use ALERT channel only if event is an alert AND user has NOT muted this match
            val channelId = if (isAlertEvent && !isMuted) {
                ChatNotificationHelper.CHANNEL_ID_ALERT
            } else {
                ChatNotificationHelper.CHANNEL_ID_SILENT
            }

            val notifId = (matchId.hashCode() and 0x7FFFFFFF)

            // Build Tap Intent (Opens HomeActivity)
            val tapIntent = Intent(context, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("MATCH_ID", matchId)
                putExtra("LEAGUE_ID", leagueId)
            }
            val tapPendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Build Mute / Unmute Quick Action
            val muteActionIntent = Intent(context, MatchNotificationReceiver::class.java).apply {
                action = if (isMuted) MatchNotificationReceiver.ACTION_UNMUTE_MATCH else MatchNotificationReceiver.ACTION_MUTE_MATCH
                putExtra(MatchNotificationReceiver.EXTRA_MATCH_ID, matchId)
                putExtra(MatchNotificationReceiver.EXTRA_LEAGUE_ID, leagueId)
                putExtra(MatchNotificationReceiver.EXTRA_TITLE, title)
                putExtra(MatchNotificationReceiver.EXTRA_BODY, body)
            }
            val mutePendingIntent = PendingIntent.getBroadcast(
                context,
                notifId + 100,
                muteActionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val muteActionText = if (isMuted) "🔔 Unmute" else "🔕 Mute"
            val muteActionIcon = if (isMuted) android.R.drawable.ic_lock_silent_mode_off else android.R.drawable.ic_lock_silent_mode
            val muteAction = NotificationCompat.Action.Builder(
                muteActionIcon,
                muteActionText,
                mutePendingIntent
            ).build()

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(if (isAlertEvent && !isMuted) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
                .setContentIntent(tapPendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .addAction(muteAction)

            if (isAlertEvent && !isMuted) {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                builder.setSound(soundUri)
                builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            } else {
                builder.setSound(null)
            }

            try {
                // Explicitly cancel any existing active notification for this match first.
                // Switching channelId for the same notifId without calling cancel() first causes Android OS
                // (especially OEM skins like Realme UI / ColorOS / MIUI) to keep the previous notification active
                // in the ALERT section while posting a duplicate in the SILENT section.
                notificationManager.cancel(notifId)
                notificationManager.notify(notifId, builder.build())
            } catch (e: Exception) {
                Log.e("NotifExtension", "Failed to post match notification: ${e.message}", e)
            }
        }
    }

    override fun onNotificationReceived(event: INotificationReceivedEvent) {
        val notification = event.notification
        val data = notification.additionalData
        val type = data?.optString("type")
        val rawLeagueId = data?.optString("leagueId")
        val leagueId = if (rawLeagueId.isNullOrBlank()) null else if (rawLeagueId.trim().equals("local", ignoreCase = true)) "local" else rawLeagueId.trim().uppercase()
        val matchId = data?.optString("matchId")
        val isAlertEvent = data?.optBoolean("isAlertEvent", false) ?: false

        Log.d("NotifExtension", "Notification received in extension: type=$type, leagueId=$leagueId, matchId=$matchId, isAlertEvent=$isAlertEvent")

        if (type == "MATCH" && !matchId.isNullOrEmpty()) {
            // Suppress default OneSignal notification card
            event.preventDefault()

            val title = notification.title ?: data.optString("title") ?: "Live Match Update"
            val body = notification.body ?: data.optString("message") ?: ""

            executor.execute {
                try {
                    postOrUpdateMatchNotification(
                        context = event.context,
                        leagueId = leagueId,
                        matchId = matchId,
                        title = title,
                        body = body,
                        isAlertEvent = isAlertEvent
                    )
                } catch (e: Throwable) {
                    Log.e("NotifExtension", "Error handling background match notification: ${e.message}", e)
                }
            }
            return
        }

        if (type == "CHAT" && !leagueId.isNullOrEmpty()) {
            val senderName = data.optString("senderName")
            val messageText = data.optString("messageText")
            val senderId = data.optString("senderId")
            val senderProfilePic = data.optString("senderProfilePic")
            val msgId = data.optString("msgId")
            val targetRecipientId = data.optString("targetRecipientId") ?: data.optString("replyRecipientId")
            val isPersonalChat = data.optBoolean("isPersonalChat", false)

            // Suppress default OneSignal notification card
            event.preventDefault()

            // Do NOT generate an unread notification for messages sent by MYSELF
            val gullyPrefs = event.context.getSharedPreferences("gully_prefs", Context.MODE_PRIVATE)
            val myUserId = gullyPrefs.getString("chat_sender_id", null)
            if (!myUserId.isNullOrEmpty() && senderId == myUserId) {
                Log.d("NotifExtension", "Ignoring notification for self-sent message.")
                return
            }

            // Build MessagingStyle notification and group tray asynchronously on background thread
            executor.execute {
                try {
                    ChatNotificationHelper.handleIncomingChatMessage(
                        context = event.context,
                        leagueId = leagueId,
                        senderName = senderName ?: "Member",
                        messageContent = messageText ?: notification.body ?: "",
                        replyRecipientId = targetRecipientId,
                        isPersonalChat = isPersonalChat,
                        senderProfilePic = senderProfilePic,
                        senderId = senderId,
                        msgId = msgId
                    )
                } catch (e: Throwable) {
                    Log.e("NotifExtension", "Error handling background chat notification: ${e.message}", e)
                }
            }
        }
    }
}

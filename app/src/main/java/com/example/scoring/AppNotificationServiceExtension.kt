package com.example.scoring

import android.content.Context
import android.util.Log
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension

/**
 * OneSignal 5.x Service Extension that intercepts incoming notification payloads
 * in foreground, background, and killed states.
 * Suppresses default OneSignal single notifications and routes chat messages through
 * ChatNotificationHelper to maintain MessagingStyle group trays.
 */
class AppNotificationServiceExtension : INotificationServiceExtension {
    override fun onNotificationReceived(event: INotificationReceivedEvent) {
        val notification = event.notification
        val data = notification.additionalData
        val type = data?.optString("type")
        val leagueId = data?.optString("leagueId")
        val senderName = data?.optString("senderName")
        val messageText = data?.optString("messageText")
        val senderId = data?.optString("senderId")
        val senderProfilePic = data?.optString("senderProfilePic")
        val msgId = data?.optString("msgId")
        val targetRecipientId = data?.optString("targetRecipientId") ?: data?.optString("replyRecipientId")
        val isPersonalChat = data?.optBoolean("isPersonalChat", false) ?: false

        Log.d("NotifExtension", "Notification received in extension: type=$type, leagueId=$leagueId, senderId=$senderId, msgId=$msgId")

        if (type == "CHAT" && !leagueId.isNullOrEmpty()) {
            // Suppress default OneSignal notification card
            event.preventDefault()

            // Do NOT generate an unread notification for messages sent by MYSELF
            val gullyPrefs = event.context.getSharedPreferences("gully_prefs", Context.MODE_PRIVATE)
            val myUserId = gullyPrefs.getString("chat_sender_id", null)
            if (!myUserId.isNullOrEmpty() && senderId == myUserId) {
                Log.d("NotifExtension", "Ignoring notification for self-sent message.")
                return
            }

            // Build WhatsApp-style MessagingStyle notification and group tray
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
        }
    }
}

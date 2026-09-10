package com.example.scoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.google.firebase.firestore.FirebaseFirestore

class NotificationReplyReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "NotifReplyReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInputResults = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = remoteInputResults.getCharSequence(ChatNotificationHelper.KEY_TEXT_REPLY)?.toString()

        if (replyText.isNullOrBlank()) return

        val leagueId = intent.getStringExtra("LEAGUE_ID") ?: return

        val gullyPrefs = context.getSharedPreferences("gully_prefs", Context.MODE_PRIVATE)
        val senderId = gullyPrefs.getString("chat_sender_id", "unknown_user") ?: "unknown_user"
        val senderName = gullyPrefs.getString("chat_sender_name", "Me") ?: "Me"
        val senderProfilePic = gullyPrefs.getString("chat_sender_profile_pic", null)

        Log.d(TAG, "Direct reply received for league $leagueId: $replyText")

        // 1. Save message directly to Firestore so it appears in the app's chat stream
        val newMsg = hashMapOf(
            "senderId" to senderId,
            "senderName" to senderName,
            "senderProfilePic" to (senderProfilePic ?: ""),
            "messageText" to replyText,
            "timestamp" to System.currentTimeMillis(),
            "type" to "TEXT"
        )

        FirebaseFirestore.getInstance()
            .collection("gullies")
            .document(leagueId)
            .collection("messages")
            .add(newMsg)
            .addOnSuccessListener { docRef ->
                Log.d(TAG, "Reply message successfully saved to Firestore for league $leagueId")
                // 2. Send push notification to other league members
                LeagueNotificationManager.sendLeagueChatNotification(
                    leagueId = leagueId,
                    senderName = senderName,
                    messageText = replyText,
                    senderId = senderId,
                    senderProfilePic = senderProfilePic,
                    msgId = docRef.id
                )
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save reply message to Firestore: ${e.message}")
            }

        // 3. Clear the notification for this league as the user has responded
        ChatNotificationHelper.clearNotificationForLeague(context, leagueId)
    }
}

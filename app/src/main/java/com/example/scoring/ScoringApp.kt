package com.example.scoring

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationClickEvent

class ScoringApp : Application() {
    companion object {
        var instance: ScoringApp? = null
            private set
        
        // REPLACE THIS with your real OneSignal App ID
        const val ONESIGNAL_APP_ID = "687185da-eba9-45a6-86ff-1ff86a490563"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Verbose logging for setup (remove for production)
        OneSignal.Debug.logLevel = LogLevel.VERBOSE

        // OneSignal Initialization
        OneSignal.initWithContext(this, ONESIGNAL_APP_ID)

        val gullyPrefs = getSharedPreferences("gully_prefs", MODE_PRIVATE)
        val currentUserId = gullyPrefs.getString("chat_sender_id", null)
        if (!currentUserId.isNullOrEmpty()) {
            try {
                OneSignal.login(currentUserId)
                OneSignal.User.addTag("user_$currentUserId", "active")
                OneSignal.User.addTag("user_id", currentUserId)
            } catch (e: Exception) {
                Log.e("ScoringApp", "OneSignal login error: ${e.message}")
            }
        }

        // Handle Notification Clicks
        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                val data = event.notification.additionalData
                val type = data?.optString("type")
                val leagueId = data?.optString("leagueId")
                val matchId = data?.optString("matchId")

                if (!leagueId.isNullOrEmpty()) {
                    ChatNotificationHelper.clearNotificationForLeague(this@ScoringApp, leagueId)
                }

                if (type == "CHAT" || (!leagueId.isNullOrEmpty() && matchId.isNullOrEmpty())) {
                    val intent = Intent(this@ScoringApp, LeagueChatActivity::class.java).apply {
                        putExtra("LEAGUE_ID", leagueId)
                        putExtra("gully_id", leagueId)
                        putExtra("leagueId", leagueId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                } else if (!matchId.isNullOrEmpty()) {
                    val intent = Intent(this@ScoringApp, MatchDetailsActivity::class.java).apply {
                        putExtra("matchId", matchId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            }
        })

        // Custom Notification Handler for Grouped Chat Messages
        OneSignal.Notifications.addForegroundLifecycleListener(object : com.onesignal.notifications.INotificationLifecycleListener {
            override fun onWillDisplay(event: com.onesignal.notifications.INotificationWillDisplayEvent) {
                val data = event.notification.additionalData
                val type = data?.optString("type")
                val leagueId = data?.optString("leagueId")
                val senderName = data?.optString("senderName")
                val messageText = data?.optString("messageText")
                val targetRecipientId = data?.optString("targetRecipientId") ?: data?.optString("replyRecipientId")

                if (type == "CHAT" && !leagueId.isNullOrEmpty()) {
                    event.preventDefault()
                    ChatNotificationHelper.handleIncomingChatMessage(
                        this@ScoringApp,
                        leagueId,
                        senderName ?: "Member",
                        messageText ?: event.notification.body ?: "",
                        targetRecipientId
                    )
                }
            }
        })

        // Load and apply saved theme
        val prefs = getSharedPreferences("scoring_prefs", Context.MODE_PRIVATE)
        val savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedMode)

        // Auto-fix any existing player photos that were saved sideways in landscape mode
        PhotoUtils.fixAllExistingPhotos(this)
    }
}

package com.example.scoring

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
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

        // Handle Notification Clicks
        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                val data = event.notification.additionalData
                
                // DEFAULT ACTION: Open specific match details
                val matchId = data?.optString("matchId")
                if (!matchId.isNullOrEmpty()) {
                    val intent = Intent(this@ScoringApp, MatchDetailsActivity::class.java).apply {
                        putExtra("matchId", matchId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            }
        })

        // Load and apply saved theme
        val prefs = getSharedPreferences("scoring_prefs", Context.MODE_PRIVATE)
        val savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedMode)
    }
}

package com.example.scoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver triggered when a user swipes away or dismisses a chat notification banner or group summary tray.
 * Wipes the unread notification queue for that league (or all leagues) so dismissed notifications never resurface.
 */
class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val clearAll = intent.getBooleanExtra("ACTION_CLEAR_ALL", false)
        if (clearAll) {
            Log.d("NotifDismiss", "User swiped away Group Summary Notification, clearing ALL chat notification queues.")
            ChatNotificationHelper.clearAllNotifications(context)
            return
        }

        val leagueId = intent.getStringExtra("LEAGUE_ID") ?: return
        Log.d("NotifDismiss", "User swiped away notification for league $leagueId, clearing queue.")
        ChatNotificationHelper.clearNotificationForLeague(context, leagueId)
    }
}

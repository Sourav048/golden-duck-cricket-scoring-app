package com.example.scoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class MatchNotificationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_MUTE_MATCH = "com.example.scoring.ACTION_MUTE_MATCH"
        const val ACTION_UNMUTE_MATCH = "com.example.scoring.ACTION_UNMUTE_MATCH"
        const val EXTRA_MATCH_ID = "MATCH_ID"
        const val EXTRA_LEAGUE_ID = "LEAGUE_ID"
        const val EXTRA_TITLE = "TITLE"
        const val EXTRA_BODY = "BODY"

        fun isMatchMuted(context: Context, matchId: String?): Boolean {
            if (matchId.isNullOrEmpty()) return false
            val prefs = context.getSharedPreferences("match_mute_prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("mute_$matchId", false)
        }

        fun setMatchMuted(context: Context, matchId: String, isMuted: Boolean) {
            val prefs = context.getSharedPreferences("match_mute_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("mute_$matchId", isMuted).apply()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val matchId = intent.getStringExtra(EXTRA_MATCH_ID) ?: return
        val leagueId = intent.getStringExtra(EXTRA_LEAGUE_ID) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val body = intent.getStringExtra(EXTRA_BODY) ?: ""

        val isMute = action == ACTION_MUTE_MATCH
        setMatchMuted(context, matchId, isMute)

        val toastMessage = if (isMute) "Match alerts muted 🔕" else "Match alerts unmuted 🔔"
        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()

        // Re-post/update active notification card to immediately reflect new Mute state.
        // When unmuted (!isMute = true), promote back to Alert channel; when muted, move to Silent channel.
        AppNotificationServiceExtension.postOrUpdateMatchNotification(
            context = context,
            leagueId = leagueId,
            matchId = matchId,
            title = title,
            body = body,
            isAlertEvent = !isMute
        )
    }
}

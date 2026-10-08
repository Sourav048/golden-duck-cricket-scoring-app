package com.example.scoring

import android.content.Context
import android.util.Log
import com.onesignal.OneSignal
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Manages OneSignal Tags and App-to-App Notifications via Cloudflare Worker Relay.
 *
 * Removes client-side OneSignal REST API key exposure and uses individual OneSignal tags
 * per league ("league_<id>" = "1") to avoid the 128-character tag truncation limit.
 */
object LeagueNotificationManager {
    private const val TAG = "LeagueNotify"
    private val executor = Executors.newCachedThreadPool()

    // Deployed Cloudflare Worker Relay URL
    private const val RELAY_URL = "https://scoring-notification-relay.souravknhpi048.workers.dev"
    // Optional secret key matching RELAY_SECRET in Cloudflare Worker environment variables
    private const val RELAY_SECRET = ""

    private fun getCanonicalLeagueTag(leagueId: String): String {
        val trimmed = leagueId.trim().lowercase()
        val snake = trimmed.replace("\\s+".toRegex(), "_")
        return "league_$snake"
    }

    /**
     * Subscribes the user to a specific league tag ("league_<id>" = "1").
     */
    fun subscribeToLeague(leagueId: String, userId: String? = null, context: Context? = null) {
        if (leagueId.isBlank()) return
        val tag = getCanonicalLeagueTag(leagueId)
        val trimmed = leagueId.trim()
        val effectiveUserId = if (!userId.isNullOrEmpty()) userId else ScoringApp.instance?.getOrCreateUserId()

        try {
            // Clean up legacy consolidated "leagues" string tag if present
            try { OneSignal.User.removeTag("leagues") } catch (_: Exception) {}

            OneSignal.User.addTag(tag, "1")
            OneSignal.User.addTag("league_$trimmed", "1")
            OneSignal.User.addTag(trimmed, "1")
            if (!effectiveUserId.isNullOrEmpty()) {
                OneSignal.User.addTag("user_id", effectiveUserId)
            }
            Log.d(TAG, "Subscribed to league tags: $tag, league_$trimmed, $trimmed for user $effectiveUserId")
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to league tags: ${e.message}")
        }
    }

    /**
     * Unsubscribes the user from a specific league tag.
     */
    fun unsubscribeFromLeague(leagueId: String, context: Context? = null) {
        if (leagueId.isBlank()) return
        val tag = getCanonicalLeagueTag(leagueId)
        OneSignal.User.removeTag(tag)
        Log.d(TAG, "Unsubscribed from league tag: $tag")
    }

    /**
     * Syncs all joined leagues by subscribing to individual tag keys for each league.
     */
    fun syncAllSubscribedLeagues(context: Context, leagueIds: List<String>, userId: String? = null) {
        if (!userId.isNullOrEmpty()) {
            OneSignal.User.addTag("user_id", userId)
        }
        // Clean legacy string tag key
        try { OneSignal.User.removeTag("leagues") } catch (_: Exception) {}

        leagueIds.forEach { subscribeToLeague(it, userId, context) }
    }

    /**
     * Sends a match/scorecard push notification via Cloudflare Worker relay.
     */
    fun sendLeagueNotification(
        leagueId: String,
        title: String,
        body: String,
        matchId: String? = null,
        isAlertEvent: Boolean = false
    ) {
        val payload = JSONObject().apply {
            put("leagueId", leagueId)
            put("title", title)
            put("message", body)
            put("type", "MATCH")
            put("matchId", matchId ?: "")
            put("isAlertEvent", isAlertEvent)
        }
        dispatchToRelay(payload)
    }

    /**
     * Sends a chat push notification via Cloudflare Worker relay.
     */
    fun sendLeagueChatNotification(
        leagueId: String,
        senderName: String,
        messageText: String,
        senderId: String? = null,
        targetRecipientId: String? = null,
        recipientSenderLabel: String? = null,
        isPersonalChat: Boolean = false,
        senderProfilePic: String? = null,
        msgId: String? = null
    ) {
        val hasTargetRecipient = !targetRecipientId.isNullOrEmpty() && targetRecipientId != senderId

        if (hasTargetRecipient) {
            val recipientLabel = recipientSenderLabel ?: "$senderName(Mentioned You🗣️🗣️)"

            // 1. Target recipient who was mentioned or replied to
            val payloadMentioned = JSONObject().apply {
                put("leagueId", leagueId)
                put("title", recipientLabel)
                put("message", messageText)
                put("messageText", messageText)
                put("type", "CHAT")
                put("senderId", senderId ?: "")
                put("senderName", recipientLabel)
                put("senderProfilePic", senderProfilePic ?: "")
                put("msgId", msgId ?: "")
                put("targetRecipientId", targetRecipientId)
                put("isPersonalChat", isPersonalChat)
            }
            dispatchToRelay(payloadMentioned)

            // 2. Broadcast to everyone else in the league
            val payloadOthers = JSONObject().apply {
                put("leagueId", leagueId)
                put("title", senderName)
                put("message", messageText)
                put("messageText", messageText)
                put("type", "CHAT")
                put("senderId", senderId ?: "")
                put("senderName", senderName)
                put("senderProfilePic", senderProfilePic ?: "")
                put("msgId", msgId ?: "")
                put("excludeUserId", targetRecipientId)
                put("isPersonalChat", isPersonalChat)
            }
            dispatchToRelay(payloadOthers)
        } else {
            // Normal message broadcast
            val payload = JSONObject().apply {
                put("leagueId", leagueId)
                put("title", senderName)
                put("message", messageText)
                put("messageText", messageText)
                put("type", "CHAT")
                put("senderId", senderId ?: "")
                put("senderName", senderName)
                put("senderProfilePic", senderProfilePic ?: "")
                put("msgId", msgId ?: "")
                put("isPersonalChat", isPersonalChat)
            }
            dispatchToRelay(payload)
        }
    }

    private fun dispatchToRelay(jsonPayload: JSONObject) {
        if (RELAY_URL.contains("workers.dev") && RELAY_URL.contains("golden-duck-relay.workers.dev")) {
            Log.w(TAG, "RELAY_URL is currently using default placeholder. Ensure you replace RELAY_URL with your deployed Cloudflare Worker URL!")
        }

        executor.execute {
            try {
                val conn = (URL(RELAY_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    if (RELAY_SECRET.isNotBlank()) {
                        setRequestProperty("x-relay-secret", RELAY_SECRET)
                    }
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                }
                conn.outputStream.use { os ->
                    os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
                }
                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Notification dispatched to relay successfully")
                } else {
                    Log.e(TAG, "Relay returned HTTP error code: $code")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispatch notification to relay: ${e.message}")
            }
        }
    }
}

package com.example.scoring

import android.util.Log
import com.onesignal.OneSignal
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Manages OneSignal Tags and App-to-App Notifications for Leagues.
 */
object LeagueNotificationManager {
    private const val TAG = "LeagueNotify"
    
    // REPLACE THIS with your OneSignal REST API Key
    // WARNING: For production apps, this should be done via a backend server.
    private const val ONESIGNAL_REST_API_KEY = "YOUR_REST_API_KEY"

    /**
     * Tags the user with the league ID so they can receive its notifications.
     */
    fun subscribeToLeague(leagueId: String) {
        // OneSignal 5.x User Tagging
        OneSignal.User.addTag("league_$leagueId", "active")
        Log.d(TAG, "Tagged user for league: $leagueId")
    }

    /**
     * Removes the league tag from the user.
     */
    fun unsubscribeFromLeague(leagueId: String) {
        OneSignal.User.removeTag("league_$leagueId")
        Log.d(TAG, "Removed tag for league: $leagueId")
    }

    /**
     * Sends a notification to all users tagged with a specific league.
     */
    fun sendLeagueNotification(leagueId: String, title: String, body: String, matchId: String? = null) {
        Executors.newSingleThreadExecutor().execute {
            try {
                val url = URL("https://onesignal.com/api/v1/notifications")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.setRequestProperty("Authorization", "Basic $ONESIGNAL_REST_API_KEY")
                conn.doOutput = true

                val jsonBody = JSONObject().apply {
                    put("app_id", ScoringApp.ONESIGNAL_APP_ID)

                    // 1. Filter: Send to everyone where tag 'league_[ID]' exists
                    val filterArray = org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("field", "tag")
                            put("key", "league_$leagueId")
                            put("relation", "exists")
                        })
                    }
                    put("filters", filterArray)

                    put("headings", JSONObject().apply { put("en", title) })
                    put("contents", JSONObject().apply { put("en", body) })

                    // 2. Styling & Branding
                    put("android_accent_color", "FF00695C") // Deep Teal
                    put("small_icon", "ic_stat_onesignal_default")
                    put("large_icon", "ic_launcher_custom")

                    // 3. COLLAPSE LOGIC: This makes the notification update in-place
                    if (!matchId.isNullOrEmpty()) {
                        put("collapse_id", matchId)
                        // Use a consistent ID based on the match string to replace on device
                        put("android_notification_id", matchId.hashCode())
                    }

                    // 4. Action Buttons
                    val buttonsArray = org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("id", "view_scorecard")
                            put("text", "VIEW SCORECARD")
                        })
                    }
                    put("buttons", buttonsArray)

                    // 5. Behavior: Popup Enabled (High Priority)
                    put("priority", 10)
                    put("android_visibility", 1)

                    // Data payload
                    put("data", JSONObject().apply {
                        put("matchId", matchId ?: "")
                        put("leagueId", leagueId)
                    })
                }

                val strJsonBody = jsonBody.toString()
                conn.outputStream.write(strJsonBody.toByteArray(Charsets.UTF_8))

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "OneSignal Notification sent successfully for $leagueId")
                } else {
                    Log.e(TAG, "OneSignal API Error: $responseCode - ${conn.errorStream.bufferedReader().readText()}")
                }
                conn.disconnect()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to trigger OneSignal notification: ${e.message}")
            }
        }
    }
}

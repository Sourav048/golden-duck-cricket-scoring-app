package com.example.scoring

import android.util.Log
import com.onesignal.OneSignal
import org.json.JSONArray
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
    private const val ONESIGNAL_REST_API_KEY = "YOUR_ONESIGNAL_REST_API_KEY"

    /**
     * Tags the user with the league ID and user ID so they can receive targeted notifications.
     */
    fun subscribeToLeague(leagueId: String, userId: String? = null) {
        // OneSignal 5.x User Tagging
        OneSignal.User.addTag("league_$leagueId", "active")
        if (!userId.isNullOrEmpty()) {
            OneSignal.User.addTag("user_$userId", "active")
        }
        Log.d(TAG, "Tagged user for league: $leagueId, userId: $userId")
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
                    put("subtitle", JSONObject().apply { put("en", "League: $leagueId") })
                    put("contents", JSONObject().apply { put("en", body) })

                    // 2. Styling & Branding
                    put("android_accent_color", "FF00695C") // Deep Teal
                    put("small_icon", "ic_stat_onesignal_default")
                    put("large_icon", "ic_launcher_custom")

                    // 3. COLLAPSE LOGIC & BUTTONS FOR MATCHES ONLY
                    if (!matchId.isNullOrEmpty()) {
                        put("collapse_id", matchId)
                        // Use a consistent ID based on the match string to replace on device
                        put("android_notification_id", matchId.hashCode())

                        // Action Buttons only for Match Updates
                        val buttonsArray = org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("id", "view_scorecard")
                                put("text", "VIEW SCORECARD")
                            })
                        }
                        put("buttons", buttonsArray)
                    }

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

    /**
     * Sends a chat notification for a specific league.
     * If a member is mentioned or replied to, sends a targeted receiver-centric notification
     * (e.g., "SenderName(Mentioned You🗣️🗣️)" or "SenderName(Replied to you)")
     * to that recipient, and a standard notification ("SenderName") to all other members.
     */
    fun sendLeagueChatNotification(
        leagueId: String,
        senderName: String,
        messageText: String,
        senderId: String? = null,
        targetRecipientId: String? = null,
        recipientSenderLabel: String? = null
    ) {
        val hasTargetRecipient = !targetRecipientId.isNullOrEmpty() && targetRecipientId != senderId

        if (hasTargetRecipient) {
            val recipientLabel = recipientSenderLabel ?: "$senderName(Mentioned You🗣️🗣️)"

            // 1. Target recipient who was mentioned/replied to:
            sendOneSignalChatNotification(
                leagueId = leagueId,
                senderName = recipientLabel,
                messageText = messageText,
                senderId = senderId,
                targetRecipientId = targetRecipientId,
                recipientUserIdFilter = targetRecipientId,
                excludeUserIdFilter = null
            )

            // 2. Target everyone else in the league: "SenderName: Message"
            sendOneSignalChatNotification(
                leagueId = leagueId,
                senderName = senderName,
                messageText = messageText,
                senderId = senderId,
                targetRecipientId = targetRecipientId,
                recipientUserIdFilter = null,
                excludeUserIdFilter = targetRecipientId
            )
        } else {
            // Normal message / reply to self: "SenderName: Message"
            sendOneSignalChatNotification(
                leagueId = leagueId,
                senderName = senderName,
                messageText = messageText,
                senderId = senderId,
                targetRecipientId = null,
                recipientUserIdFilter = null,
                excludeUserIdFilter = null
            )
        }
    }

    private fun sendOneSignalChatNotification(
        leagueId: String,
        senderName: String,
        messageText: String,
        senderId: String?,
        targetRecipientId: String?,
        recipientUserIdFilter: String?,
        excludeUserIdFilter: String?
    ) {
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

                    val filterArray = JSONArray()

                    // Always require being in the league
                    filterArray.put(JSONObject().apply {
                        put("field", "tag")
                        put("key", "league_$leagueId")
                        put("relation", "exists")
                    })

                    if (!recipientUserIdFilter.isNullOrEmpty()) {
                        put("include_aliases", JSONObject().apply {
                            put("external_id", JSONArray().apply {
                                put(recipientUserIdFilter)
                            })
                        })
                        put("target_channel", "push")

                        filterArray.put(JSONObject().apply {
                            put("field", "tag")
                            put("key", "user_$recipientUserIdFilter")
                            put("relation", "exists")
                        })
                    } else if (!excludeUserIdFilter.isNullOrEmpty()) {
                        filterArray.put(JSONObject().apply {
                            put("field", "tag")
                            put("key", "user_$excludeUserIdFilter")
                            put("relation", "not_exists")
                        })
                    }

                    put("filters", filterArray)

                    put("headings", JSONObject().apply { put("en", senderName) })
                    put("subtitle", JSONObject().apply { put("en", leagueId) })
                    put("contents", JSONObject().apply { put("en", messageText) })

                    put("android_accent_color", "FF00695C")
                    put("small_icon", "ic_stat_onesignal_default")
                    put("large_icon", "ic_launcher_custom")

                    put("collapse_id", "chat_$leagueId")
                    put("android_notification_id", ChatNotificationHelper.getNotificationId(leagueId))

                    put("priority", 10)
                    put("android_visibility", 1)

                    put("data", JSONObject().apply {
                        put("type", "CHAT")
                        put("leagueId", leagueId)
                        put("senderName", senderName)
                        put("messageText", messageText)
                        put("senderId", senderId ?: "")
                        put("targetRecipientId", targetRecipientId ?: "")
                    })
                }

                val strJsonBody = jsonBody.toString()
                conn.outputStream.write(strJsonBody.toByteArray(Charsets.UTF_8))

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "OneSignal Chat Notification sent successfully for $leagueId")
                } else {
                    Log.e(TAG, "OneSignal API Error: $responseCode - ${conn.errorStream?.bufferedReader()?.readText()}")
                }
                conn.disconnect()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to trigger OneSignal chat notification: ${e.message}")
            }
        }
    }
}

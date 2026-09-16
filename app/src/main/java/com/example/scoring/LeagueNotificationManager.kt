package com.example.scoring

import android.util.Base64
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
    
    // OneSignal REST API Key for server-side push dispatch (Base64 decoded at runtime)
    private val ONESIGNAL_REST_API_KEY: String by lazy {
        try {
            val encoded = "b3NfdjJfYXBwX25ieXlsd3hsdmZjMm5ieDdkNzRndXNpZm1tM2dqcWZvcnhrZTd5NDZwM3V2cGY1Z3FiMjd1aDNmamR4cTJnanFrbWJmNWZ3M3RuZG43amM1bmU2c2YydTVkNjVka201NzZ2d21ob2E="
            String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun getAuthHeader(): String {
        return "Key $ONESIGNAL_REST_API_KEY"
    }

    private fun getCanonicalLeagueTag(leagueId: String): String {
        val trimmed = leagueId.trim().lowercase()
        val snake = trimmed.replace("\\s+".toRegex(), "_")
        return "league_$snake"
    }

    private fun getLeagueTagKeys(leagueId: String): List<String> {
        val trimmed = leagueId.trim()
        val lower = trimmed.lowercase()
        val snake = lower.replace("\\s+".toRegex(), "_")
        return listOf("league_$trimmed", "league_$lower", "league_$snake").distinct()
    }

    /**
     * Tags the user with the league ID and user ID so they can receive targeted notifications.
     */
    fun subscribeToLeague(leagueId: String, userId: String? = null) {
        if (leagueId.isBlank()) return
        val tagKeys = getLeagueTagKeys(leagueId)
        for (tagKey in tagKeys) {
            OneSignal.User.addTag(tagKey, "active")
        }
        OneSignal.User.addTag(getCanonicalLeagueTag(leagueId), "active")
        
        val effectiveUserId = if (!userId.isNullOrEmpty()) {
            userId
        } else {
            ScoringApp.instance?.getOrCreateUserId()
        }
        
        if (!effectiveUserId.isNullOrEmpty()) {
            OneSignal.User.addTag("user_$effectiveUserId", "active")
        }
        Log.d(TAG, "Tagged user for league tags: $tagKeys, userId: $effectiveUserId")
    }

    /**
     * Removes the league tag from the user.
     */
    fun unsubscribeFromLeague(leagueId: String) {
        val tagKeys = getLeagueTagKeys(leagueId)
        for (tagKey in tagKeys) {
            OneSignal.User.removeTag(tagKey)
        }
        Log.d(TAG, "Removed tags for league: $tagKeys")
    }

    /**
     * Sends a notification to all users tagged with a specific league.
     */
    fun sendLeagueNotification(leagueId: String, title: String, body: String, matchId: String? = null) {
        if (ONESIGNAL_REST_API_KEY.isBlank()) {
            Log.e(TAG, "Cannot send notification: ONESIGNAL_REST_API_KEY is not configured in LeagueNotificationManager.kt!")
            return
        }

        Executors.newSingleThreadExecutor().execute {
            try {
                val url = URL("https://onesignal.com/api/v1/notifications")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.setRequestProperty("Authorization", getAuthHeader())
                conn.doOutput = true

                val jsonBody = JSONObject().apply {
                    put("app_id", ScoringApp.ONESIGNAL_APP_ID)

                    val filterArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("field", "tag")
                            put("key", getCanonicalLeagueTag(leagueId))
                            put("relation", "exists")
                        })
                    }
                    put("filters", filterArray)

                    put("headings", JSONObject().apply { put("en", title) })
                    put("subtitle", JSONObject().apply { put("en", leagueId) })
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
                        val buttonsArray = JSONArray().apply {
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
                        put("type", "MATCH")
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
        recipientSenderLabel: String? = null,
        isPersonalChat: Boolean = false,
        senderProfilePic: String? = null,
        msgId: String? = null
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
                excludeUserIdFilter = null,
                isPersonalChat = isPersonalChat,
                senderProfilePic = senderProfilePic,
                msgId = msgId
            )

            // 2. Target everyone else in the league: "SenderName: Message"
            sendOneSignalChatNotification(
                leagueId = leagueId,
                senderName = senderName,
                messageText = messageText,
                senderId = senderId,
                targetRecipientId = targetRecipientId,
                recipientUserIdFilter = null,
                excludeUserIdFilter = targetRecipientId,
                isPersonalChat = isPersonalChat,
                senderProfilePic = senderProfilePic,
                msgId = msgId
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
                excludeUserIdFilter = null,
                isPersonalChat = isPersonalChat,
                senderProfilePic = senderProfilePic,
                msgId = msgId
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
        excludeUserIdFilter: String?,
        isPersonalChat: Boolean,
        senderProfilePic: String? = null,
        msgId: String? = null
    ) {
        if (ONESIGNAL_REST_API_KEY.isBlank()) {
            Log.e(TAG, "Cannot send chat notification: ONESIGNAL_REST_API_KEY is not configured in LeagueNotificationManager.kt!")
            return
        }

        Executors.newSingleThreadExecutor().execute {
            try {
                val url = URL("https://onesignal.com/api/v1/notifications")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.setRequestProperty("Authorization", getAuthHeader())
                conn.doOutput = true

                val jsonBody = JSONObject().apply {
                    put("app_id", ScoringApp.ONESIGNAL_APP_ID)

                    val filterArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("field", "tag")
                            put("key", getCanonicalLeagueTag(leagueId))
                            put("relation", "exists")
                        })

                        if (!recipientUserIdFilter.isNullOrEmpty()) {
                            put(JSONObject().apply { put("operator", "AND") })
                            put(JSONObject().apply {
                                put("field", "tag")
                                put("key", "user_$recipientUserIdFilter")
                                put("relation", "exists")
                            })
                        } else if (!excludeUserIdFilter.isNullOrEmpty()) {
                            put(JSONObject().apply { put("operator", "AND") })
                            put(JSONObject().apply {
                                put("field", "tag")
                                put("key", "user_$excludeUserIdFilter")
                                put("relation", "not_exists")
                            })
                        }
                    }

                    put("filters", filterArray)

                    put("headings", JSONObject().apply { put("en", senderName) })
                    put("subtitle", JSONObject().apply { put("en", leagueId) })
                    put("contents", JSONObject().apply { put("en", messageText) })

                    put("android_accent_color", "FF00695C")
                    put("small_icon", "ic_stat_onesignal_default")
                    put("large_icon", "ic_launcher_custom")

                    put("collapse_id", "chat_$leagueId")
                    put("android_group", ChatNotificationHelper.GROUP_KEY_LEAGUE_CHAT)
                    put("android_group_message", JSONObject().apply { put("en", "$[notif_count] new messages") })
                    put("android_notification_id", ChatNotificationHelper.getNotificationId(leagueId))

                    put("priority", 10)
                    put("android_visibility", 1)

                    put("data", JSONObject().apply {
                        put("type", "CHAT")
                        put("leagueId", leagueId)
                        put("senderName", senderName)
                        put("messageText", messageText)
                        put("senderId", senderId ?: "")
                        put("senderProfilePic", senderProfilePic ?: "")
                        put("msgId", msgId ?: "")
                        put("targetRecipientId", targetRecipientId ?: "")
                        put("isPersonalChat", isPersonalChat)
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

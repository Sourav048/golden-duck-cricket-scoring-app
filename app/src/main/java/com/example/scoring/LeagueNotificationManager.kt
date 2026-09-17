package com.example.scoring

import android.content.Context
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
 *
 * Optimized for OneSignal Free Plan (max 2 Data Tags per user profile):
 *  - Tag 1: "user_id" (value = userId)
 *  - Tag 2: "leagues" (value = ",league_id1,league_id2,")
 */
object LeagueNotificationManager {
    private const val TAG = "LeagueNotify"
    private const val PREFS_NAME = "onesignal_league_tags_prefs"
    private const val KEY_SUBSCRIBED_LEAGUES = "subscribed_leagues_set"

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

    private fun getSubscribedLeagues(context: Context?): MutableSet<String> {
        val ctx = context ?: ScoringApp.instance ?: return mutableSetOf()
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_SUBSCRIBED_LEAGUES, null) ?: emptySet()
        return set.toMutableSet()
    }

    private fun saveSubscribedLeagues(context: Context?, leagues: Set<String>) {
        val ctx = context ?: ScoringApp.instance ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_SUBSCRIBED_LEAGUES, leagues).apply()
    }

    /**
     * Syncs all joined leagues at once into the single "leagues" tag key.
     */
    fun syncAllSubscribedLeagues(context: Context, leagueIds: List<String>, userId: String? = null) {
        val canonicalLeagues = leagueIds.map { getCanonicalLeagueTag(it) }.filter { it.isNotBlank() }.toSet()
        saveSubscribedLeagues(context, canonicalLeagues)
        updateUserTags(context, userId)
    }

    /**
     * Subscribes the user to a league by appending it to the single "leagues" tag key.
     */
    fun subscribeToLeague(leagueId: String, userId: String? = null, context: Context? = null) {
        if (leagueId.isBlank()) return
        val canonical = getCanonicalLeagueTag(leagueId)
        val currentLeagues = getSubscribedLeagues(context)
        currentLeagues.add(canonical)
        saveSubscribedLeagues(context, currentLeagues)

        // Remove legacy tag keys to clean up OneSignal profile
        val legacyKeys = getLeagueTagKeys(leagueId)
        for (k in legacyKeys) {
            try { OneSignal.User.removeTag(k) } catch (_: Exception) {}
        }

        updateUserTags(context, userId)
        Log.d(TAG, "Subscribed to league: $canonical, total leagues: ${currentLeagues.size}")
    }

    /**
     * Unsubscribes the user from a league by removing it from the single "leagues" tag key.
     */
    fun unsubscribeFromLeague(leagueId: String, context: Context? = null) {
        if (leagueId.isBlank()) return
        val canonical = getCanonicalLeagueTag(leagueId)
        val currentLeagues = getSubscribedLeagues(context)
        currentLeagues.remove(canonical)
        saveSubscribedLeagues(context, currentLeagues)

        // Remove legacy tag keys to clean up OneSignal profile
        val legacyKeys = getLeagueTagKeys(leagueId)
        for (k in legacyKeys) {
            try { OneSignal.User.removeTag(k) } catch (_: Exception) {}
        }

        updateUserTags(context, null)
        Log.d(TAG, "Unsubscribed from league: $canonical, remaining leagues: ${currentLeagues.size}")
    }

    /**
     * Updates user tags so the user has AT MOST 2 Data Tags on OneSignal:
     * 1) "user_id" -> userId
     * 2) "leagues" -> ",league_1,league_2,"
     */
    private fun updateUserTags(context: Context? = null, userId: String? = null) {
        try {
            val effectiveUserId = if (!userId.isNullOrEmpty()) {
                userId
            } else {
                ScoringApp.instance?.getOrCreateUserId()
            }

            if (!effectiveUserId.isNullOrEmpty()) {
                OneSignal.User.addTag("user_id", effectiveUserId)
                OneSignal.User.removeTag("user_$effectiveUserId")
            }

            val currentLeagues = getSubscribedLeagues(context)
            if (currentLeagues.isNotEmpty()) {
                val leaguesTagValue = "," + currentLeagues.joinToString(",") + ","
                OneSignal.User.addTag("leagues", leaguesTagValue)
            } else {
                OneSignal.User.removeTag("leagues")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user tags: ${e.message}")
        }
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

                val canonicalTag = getCanonicalLeagueTag(leagueId)

                val jsonBody = JSONObject().apply {
                    put("app_id", ScoringApp.ONESIGNAL_APP_ID)

                    val filterArray = JSONArray().apply {
                        // Match new consolidated "leagues" tag key
                        put(JSONObject().apply {
                            put("field", "tag")
                            put("key", "leagues")
                            put("relation", "contains")
                            put("value", ",$canonicalTag,")
                        })
                        // OR match legacy single tag key for backwards compatibility
                        put(JSONObject().apply { put("operator", "OR") })
                        put(JSONObject().apply {
                            put("field", "tag")
                            put("key", canonicalTag)
                            put("relation", "exists")
                        })
                    }
                    put("filters", filterArray)

                    put("headings", JSONObject().apply { put("en", title) })
                    put("subtitle", JSONObject().apply { put("en", leagueId) })
                    put("contents", JSONObject().apply { put("en", body) })

                    // Styling & Branding
                    put("android_accent_color", "FF00695C") // Deep Teal
                    put("small_icon", "ic_stat_onesignal_default")
                    put("large_icon", "ic_launcher_custom")

                    // COLLAPSE LOGIC & BUTTONS FOR MATCHES ONLY
                    if (!matchId.isNullOrEmpty()) {
                        put("collapse_id", matchId)
                        put("android_notification_id", matchId.hashCode())

                        val buttonsArray = JSONArray().apply {
                            put(JSONObject().apply {
                                put("id", "view_scorecard")
                                put("text", "VIEW SCORECARD")
                            })
                        }
                        put("buttons", buttonsArray)
                    }

                    put("priority", 10)
                    put("android_visibility", 1)

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
                    Log.e(TAG, "OneSignal API Error: $responseCode - ${conn.errorStream?.bufferedReader()?.readText()}")
                }
                conn.disconnect()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to trigger OneSignal notification: ${e.message}")
            }
        }
    }

    /**
     * Sends a chat notification for a specific league.
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

                val canonicalTag = getCanonicalLeagueTag(leagueId)

                val jsonBody = JSONObject().apply {
                    put("app_id", ScoringApp.ONESIGNAL_APP_ID)

                    val filterArray = JSONArray().apply {
                        if (!recipientUserIdFilter.isNullOrEmpty()) {
                            // Target specifically the recipient user
                            put(JSONObject().apply {
                                put("field", "tag")
                                put("key", "user_id")
                                put("relation", "=")
                                put("value", recipientUserIdFilter)
                            })
                            put(JSONObject().apply { put("operator", "OR") })
                            put(JSONObject().apply {
                                put("field", "tag")
                                put("key", "user_$recipientUserIdFilter")
                                put("relation", "exists")
                            })
                        } else {
                            // Target everyone in the league (new consolidated tag OR legacy tag)
                            put(JSONObject().apply {
                                put("field", "tag")
                                put("key", "leagues")
                                put("relation", "contains")
                                put("value", ",$canonicalTag,")
                            })
                            put(JSONObject().apply { put("operator", "OR") })
                            put(JSONObject().apply {
                                put("field", "tag")
                                put("key", canonicalTag)
                                put("relation", "exists")
                            })

                            if (!excludeUserIdFilter.isNullOrEmpty()) {
                                put(JSONObject().apply { put("operator", "AND") })
                                put(JSONObject().apply {
                                    put("field", "tag")
                                    put("key", "user_id")
                                    put("relation", "!=")
                                    put("value", excludeUserIdFilter)
                                })
                                put(JSONObject().apply { put("operator", "AND") })
                                put(JSONObject().apply {
                                    put("field", "tag")
                                    put("key", "user_$excludeUserIdFilter")
                                    put("relation", "not_exists")
                                })
                            }
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

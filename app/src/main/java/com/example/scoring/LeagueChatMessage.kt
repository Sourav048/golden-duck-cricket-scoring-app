package com.example.scoring

import androidx.annotation.Keep
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class LeagueChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderProfilePic: String? = null,
    val messageText: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "TEXT", // "TEXT", "IMAGE", "VIDEO", "SYSTEM"
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val replyToId: String? = null,
    val replyToSender: String? = null,
    val replyToText: String? = null,
    val replyToMediaUrl: String? = null,
    val replyToMediaType: String? = null,
    val reactions: Map<String, String> = emptyMap(), // userId -> emoji
    val seenBy: Map<String, Long> = emptyMap() // userId -> timestamp
)

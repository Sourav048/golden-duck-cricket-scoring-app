package com.example.scoring

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    var text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    var generationTimeSecs: Int? = null
)

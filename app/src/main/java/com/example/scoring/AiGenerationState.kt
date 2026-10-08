package com.example.scoring

sealed class AiGenerationState {
    object Idle : AiGenerationState()
    data class Generating(
        val prompt: String,
        val currentText: String
    ) : AiGenerationState()
    data class Completed(
        val prompt: String,
        val replyText: String,
        val durationSecs: Int
    ) : AiGenerationState()
    data class Error(val message: String) : AiGenerationState()
    object Cancelled : AiGenerationState()
}

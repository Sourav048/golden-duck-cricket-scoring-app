package com.goldenduck.scoring

import androidx.annotation.Keep

@Keep
sealed class ScoringEvent {
    data class BallBowled(
        val ball: Ball,
        val facingBatsmanName: String?,
        val bowlerName: String?,
        val outPlayerName: String? = null,
        val fielderName: String? = null,
        val isStrikerEnd: Boolean = true
    ) : ScoringEvent()

    data class PenaltyAdded(
        val runs: Int,
        val teamName: String?
    ) : ScoringEvent()

    data class PlayerRetired(
        val playerName: String,
        val isHurt: Boolean,
        val dismissalText: String
    ) : ScoringEvent()

    data class StrikeSwapped(
        val reason: String = "Manual Swap"
    ) : ScoringEvent()
}

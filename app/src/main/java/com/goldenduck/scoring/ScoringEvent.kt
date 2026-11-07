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
        val isStrikerEnd: Boolean = true,
        val facingBatsmanId: String? = null,
        val bowlerId: String? = null,
        val nonStrikerId: String? = null,
        val outPlayerId: String? = null,
        val primaryFielderName: String? = null,
        val secondaryFielderName: String? = null,
        val isExplicitSolo: Boolean = false
    ) : ScoringEvent()

    data class PenaltyAdded(
        val runs: Int,
        val teamName: String?
    ) : ScoringEvent()

    data class PlayerRetired(
        val playerName: String,
        val isHurt: Boolean,
        val dismissalText: String,
        val playerId: String? = null
    ) : ScoringEvent()

    data class StrikeSwapped(
        val reason: String = "Manual Swap"
    ) : ScoringEvent()
}

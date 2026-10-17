package com.goldenduck.scoring

import androidx.annotation.Keep

@Keep
data class MatchState(
    val innings: Innings = Innings(),
    val playerStats: Map<String, Player> = emptyMap(),
    val currentStrikerName: String? = null,
    val currentNonStrikerName: String? = null,
    val currentBowlerName: String? = null,
    val isFreeHitActive: Boolean = false,
    val isOverComplete: Boolean = false,
    val isRuleRunsOnWide: Boolean = true
) {
    fun getPlayer(name: String?): Player? {
        if (name.isNullOrBlank()) return null
        return playerStats[name]
    }
}

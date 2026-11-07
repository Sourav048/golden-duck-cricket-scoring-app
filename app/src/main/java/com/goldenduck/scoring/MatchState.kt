package com.goldenduck.scoring

import androidx.annotation.Keep

@Keep
data class MatchState(
    val innings: Innings = Innings(),
    val playerStats: Map<String, Player> = emptyMap(),
    val currentStrikerName: String? = null,
    val currentNonStrikerName: String? = null,
    val currentBowlerName: String? = null,
    val currentStrikerId: String? = null,
    val currentNonStrikerId: String? = null,
    val currentBowlerId: String? = null,
    val isFreeHitActive: Boolean = false,
    val isOverComplete: Boolean = false,
    val isRuleRunsOnWide: Boolean = true,
    val isRuleFreeHit: Boolean = true
) {
    fun getPlayer(idOrName: String?): Player? {
        if (idOrName.isNullOrBlank()) return null
        return playerStats[idOrName] ?: playerStats.values.find { it.name == idOrName }
    }
}

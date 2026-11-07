package com.goldenduck.scoring

import androidx.annotation.Keep

@Keep
data class WicketDetail(
    val dismissedPlayerId: String,
    val dismissalType: DismissalType,
    val bowlerId: String? = null,
    val primaryFielderId: String? = null,
    val secondaryFielderId: String? = null,
    val runsCompleted: Int = 0,
    val isStrikerEnd: Boolean = true
) {
    fun toDisplayString(getPlayerName: (String) -> String): String {
        val bName = bowlerId?.let { getPlayerName(it) } ?: "Bowler"
        val f1Name = primaryFielderId?.let { getPlayerName(it) }
        val f2Name = secondaryFielderId?.let { getPlayerName(it) }

        return when (dismissalType) {
            DismissalType.BOWLED -> "b $bName"
            DismissalType.CAUGHT -> {
                if (primaryFielderId != null && primaryFielderId == bowlerId) "c & b $bName"
                else "c ${f1Name ?: "Fielder"} b $bName"
            }
            DismissalType.LBW -> "LBW b $bName"
            DismissalType.STUMPED -> "st ${f1Name ?: "Keeper"} b $bName"
            DismissalType.HIT_WICKET -> "Hit Wicket b $bName"
            DismissalType.HIT_OUT_OF_GROUND -> "Hit Out Of Ground b $bName"
            DismissalType.RUN_OUT -> {
                val fielderStr = when {
                    f1Name != null && f2Name != null -> " ($f1Name/$f2Name)"
                    f1Name != null -> " ($f1Name)"
                    f2Name != null -> " ($f2Name)"
                    else -> ""
                }
                "Run Out$fielderStr"
            }
            DismissalType.OBSTRUCTING -> "Obstructed the field"
            DismissalType.RETIRED_HURT -> "retired hurt"
            DismissalType.RETIRED_OUT -> "retired out"
            DismissalType.OTHER -> "out"
        }
    }
}
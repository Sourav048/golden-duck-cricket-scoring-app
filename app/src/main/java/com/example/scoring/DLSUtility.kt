package com.example.scoring

import kotlin.math.floor

object DLSUtility {

    /**
     * Standard G50 constant (average 50-over total).
     * Typically 245 for men's ODI.
     */
    private const val G50 = 245.0

    /**
     * Resource Percentage Table (Simplified Standard Edition).
     * Keys are Overs Remaining (0-50), values are resource % for wickets lost (0-9).
     */
    private val RESOURCE_TABLE = mapOf(
        50 to doubleArrayOf(100.0, 93.4, 85.1, 74.9, 62.7, 49.0, 34.9, 22.0, 11.9, 4.7),
        40 to doubleArrayOf(89.3, 84.2, 77.8, 69.6, 59.5, 47.6, 34.6, 22.0, 11.9, 4.7),
        30 to doubleArrayOf(75.1, 71.8, 67.3, 61.6, 54.1, 44.7, 33.7, 21.9, 11.9, 4.7),
        25 to doubleArrayOf(66.5, 63.9, 60.5, 56.0, 50.0, 42.2, 32.6, 21.8, 11.9, 4.7),
        20 to doubleArrayOf(56.6, 54.8, 52.4, 49.1, 44.6, 39.1, 31.2, 21.5, 11.9, 4.7),
        15 to doubleArrayOf(45.2, 44.1, 42.6, 40.5, 37.6, 33.9, 28.5, 20.6, 11.8, 4.7),
        10 to doubleArrayOf(32.1, 31.6, 30.8, 29.8, 28.3, 26.1, 23.3, 18.2, 11.4, 4.7),
        5 to doubleArrayOf(17.2, 17.0, 16.8, 16.5, 16.1, 15.4, 14.3, 12.3, 9.1, 4.3),
        0 to doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    )

    /**
     * Gets the resource percentage for given overs remaining and wickets lost.
     */
    fun getResource(oversLeft: Int, wicketsLost: Int, totalMatchOvers: Int): Double {
        val w = wicketsLost.coerceIn(0, 9)
        
        // --- DLS Standard Edition Scaling ---
        // The standard ICC table is based on 50 overs (100%).
        // We calculate what % of the 50-over resources the match's TOTAL overs represents.
        // Then we scale current resources against that base.
        
        // Find match base resource (e.g. 20 overs at start of match)
        val matchBaseResource = getResourceInternal(totalMatchOvers, 0)
        
        val scale = if (matchBaseResource > 0) 100.0 / matchBaseResource else 1.0
        
        val currentResource = getResourceInternal(oversLeft, w)
        return (currentResource * scale).coerceAtMost(100.0)
    }

    private fun getResourceInternal(oversLeft: Int, wicketsLost: Int): Double {
        val w = wicketsLost.coerceIn(0, 9)
        val sortedKeys = RESOURCE_TABLE.keys.sorted()
        
        RESOURCE_TABLE[oversLeft]?.let {
            return it.getOrNull(w) ?: 0.0
        }

        val lower = sortedKeys.lastOrNull { it < oversLeft } ?: 0
        val upper = sortedKeys.firstOrNull { it > oversLeft } ?: 50
        
        val rLower = RESOURCE_TABLE[lower]?.getOrNull(w) ?: 0.0
        val rUpper = RESOURCE_TABLE[upper]?.getOrNull(w) ?: 0.0
        
        return rLower + (rUpper - rLower) * (oversLeft - lower).toDouble() / (upper - lower).toDouble()
    }

    /**
     * Calculates the minimum overs required to constitute a match.
     */
    fun getMinimumOvers(originalOvers: Int): Int {
        return when {
            originalOvers >= 50 -> 20 // ODI
            originalOvers >= 20 -> 5  // T20
            else -> floor(originalOvers * 0.5).toInt().coerceAtLeast(1) // 50% for others
        }
    }

    /**
     * Calculates the revised target for Team 2.
     * @param team1Score Final score of Team 1
     * @param r1 Resource % Team 1 actually had
     * @param r2 Resource % Team 2 actually had
     * @return Revised target for Team 2 (to win)
     */
    fun calculateRevisedTarget(team1Score: Int, r1: Double, r2: Double): Int {
        return if (r2 < r1) {
            // Team 2 has fewer resources (Formula: S * R2/R1)
            val result = (team1Score.toDouble() * (r2 / r1))
            floor(result).toInt() + 1
        } else {
            // Team 2 has more resources (Formula: S + (R2-R1)*G50/100)
            val result = team1Score.toDouble() + ((r2 - r1) * G50 / 100.0)
            floor(result).toInt() + 1
        }
    }
}

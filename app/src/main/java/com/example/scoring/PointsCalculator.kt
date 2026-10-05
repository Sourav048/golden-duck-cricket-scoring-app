package com.example.scoring

/**
 * Centralized points calculator for Player of the Match (POTM) and Overall Rankings.
 * Standardizes weights across Kotlin calculations and StatsDao SQL queries.
 *
 * Point Breakdown per Action:
 * - Hitting a Six:  6 pts (from runsScored) + 4 bonus pts (SIX_BONUS_PTS) = 10 pts TOTAL
 * - Hitting a Four: 4 pts (from runsScored) + 2 bonus pts (FOUR_BONUS_PTS) = 6 pts TOTAL
 * - Hitting a Single: 1 pt (from runsScored) = 1 pt TOTAL
 * - Batting Milestones: +10 pts (30s), +25 pts (50s), +40 pts (80s), +50 pts (100s)
 * - Bowling a Dot Ball: 2 pts (DOT_BALL_PTS)
 * - Bowling a Maiden Over: 15 pts (MAIDEN_PTS) + Dot Ball pts
 * - Taking a Wicket: 25 pts (WICKET_PTS)
 * - Taking a Catch: 8 pts (CATCH_PTS)
 * - Executing a Stumping: 12 pts (STUMPING_PTS)
 * - Executing a Run Out: 12 pts (RUN_OUT_PTS)
 */
object PointsCalculator {
    const val RUN_PTS = 1.0           // 1 point per run scored
    const val FOUR_BONUS_PTS = 2.0     // 2 bonus points for a boundary 4 (total 6 pts)
    const val SIX_BONUS_PTS = 4.0      // 4 bonus points for a boundary 6 (total 10 pts)
    const val WICKET_PTS = 25.0        // 25 points per bowler wicket
    const val MAIDEN_PTS = 15.0        // 15 points per maiden over
    const val DOT_BALL_PTS = 2.0       // 2 points per dot ball bowled
    const val CATCH_PTS = 8.0          // 8 points per catch taken
    const val STUMPING_PTS = 12.0      // 12 points per stumping
    const val RUN_OUT_PTS = 12.0       // 12 points per run out

    fun calculateMilestoneBonus(runsScored: Int): Double {
        return when {
            runsScored >= 100 -> 50.0
            runsScored >= 80 -> 40.0
            runsScored >= 50 -> 25.0
            runsScored >= 30 -> 10.0
            else -> 0.0
        }
    }

    fun calculatePlayerMatchPoints(
        runsScored: Int,
        fours: Int,
        sixes: Int,
        wicketsTaken: Int,
        maidens: Int,
        dotBalls: Int,
        catches: Int,
        stumpings: Int,
        runOuts: Int
    ): Double {
        return (runsScored * RUN_PTS) +
               (fours * FOUR_BONUS_PTS) +
               (sixes * SIX_BONUS_PTS) +
               calculateMilestoneBonus(runsScored) +
               (wicketsTaken * WICKET_PTS) +
               (maidens * MAIDEN_PTS) +
               (dotBalls * DOT_BALL_PTS) +
               (catches * CATCH_PTS) +
               (stumpings * STUMPING_PTS) +
               (runOuts * RUN_OUT_PTS)
    }

    fun calculatePlayerPoints(p: Player): Double {
        return calculatePlayerMatchPoints(
            runsScored = p.runsScored,
            fours = p.fours,
            sixes = p.sixes,
            wicketsTaken = p.wicketsTaken,
            maidens = p.maidens,
            dotBalls = p.dotBalls,
            catches = p.catches,
            stumpings = p.stumpings,
            runOuts = p.runOuts
        )
    }

    fun calculatePlayerStatPoints(s: PlayerMatchStatEntity): Double {
        return calculatePlayerMatchPoints(
            runsScored = s.runsScored,
            fours = s.fours,
            sixes = s.sixes,
            wicketsTaken = s.wicketsTaken,
            maidens = s.maidens,
            dotBalls = s.dotBalls,
            catches = s.catches,
            stumpings = s.stumpings,
            runOuts = s.runOuts
        )
    }
}

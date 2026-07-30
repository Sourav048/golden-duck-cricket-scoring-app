package com.example.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mocks the behavior of MainActivity and StatsRecalculator hattrick logic.
 */
class HattrickLogicTest {

    @Test
    fun testHattrickFourInFour() {
        val bowler = "Bowler A"
        val balls = mutableListOf<Ball>()

        // Ball 1: Wicket
        balls.add(Ball(0, BallType.NORMAL, true, "Bat 1", bowler, 0))
        assertEquals(0, calculateHattricks(balls, bowler))

        // Ball 2: Wicket
        balls.add(Ball(0, BallType.NORMAL, true, "Bat 2", bowler, 0))
        assertEquals(0, calculateHattricks(balls, bowler))

        // Ball 3: Wicket -> Hattrick!
        balls.add(Ball(0, BallType.NORMAL, true, "Bat 3", bowler, 0))
        assertEquals(1, calculateHattricks(balls, bowler))

        // Ball 4: Wicket -> Should NOT trigger a second hattrick (4-in-4 is 1 hattrick)
        balls.add(Ball(0, BallType.NORMAL, true, "Bat 4", bowler, 0))
        assertEquals(1, calculateHattricks(balls, bowler))
        
        // Ball 5: Dot
        balls.add(Ball(0, BallType.NORMAL, false, "Bat 5", bowler, 0))
        assertEquals(1, calculateHattricks(balls, bowler))

        // Ball 6: Wicket
        balls.add(Ball(0, BallType.NORMAL, true, "Bat 6", bowler, 0))
        // Ball 7: Wicket
        balls.add(Ball(0, BallType.NORMAL, true, "Bat 7", bowler, 0))
        // Ball 8: Wicket -> New Hattrick!
        balls.add(Ball(0, BallType.NORMAL, true, "Bat 8", bowler, 0))
        assertEquals(2, calculateHattricks(balls, bowler))
    }

    @Test
    fun testHattrickWithRunOut() {
        val bowler = "Bowler A"
        val balls = mutableListOf<Ball>()

        // Ball 1: Wicket
        balls.add(Ball(0, BallType.NORMAL, true, "Bat 1", bowler, 0))
        // Ball 2: Run Out (not bowler wicket)
        balls.add(Ball(1, BallType.NORMAL, true, "Bat 2", bowler, 0).apply { dismissalInfo = "Run Out" })
        // Ball 3: Wicket
        balls.add(Ball(0, BallType.NORMAL, true, "Bat 3", bowler, 0))

        assertEquals(0, calculateHattricks(balls, bowler))
    }

    @Test
    fun testHattrickWithWide() {
        val bowler = "Bowler A"
        val balls = mutableListOf<Ball>()

        // Ball 1: Wicket
        balls.add(Ball(0, BallType.NORMAL, true, "Bat 1", bowler, 0))
        // Ball 2: Wide (non-wicket) -> breaks streak
        balls.add(Ball(1, BallType.WIDE, false, "Bat 2", bowler, 0))
        // Ball 3: Wicket
        balls.add(Ball(0, BallType.NORMAL, true, "Bat 3", bowler, 0))
        // Ball 4: Wicket
        balls.add(Ball(0, BallType.NORMAL, true, "Bat 4", bowler, 0))

        assertEquals(0, calculateHattricks(balls, bowler))
    }
    
    @Test
    fun testHattrickWithStumpingOnWide() {
        val bowler = "Bowler A"
        val balls = mutableListOf<Ball>()

        // Ball 1: Wicket
        balls.add(Ball(0, BallType.NORMAL, true, "Bat 1", bowler, 0))
        // Ball 2: Wicket
        balls.add(Ball(0, BallType.NORMAL, true, "Bat 2", bowler, 0))
        // Ball 3: Stumping on Wide -> Hattrick!
        balls.add(Ball(1, BallType.WIDE, true, "Bat 3", bowler, 0).apply { dismissalInfo = "st k b Bowler A" })

        assertEquals(1, calculateHattricks(balls, bowler))
    }

    /**
     * Replicates the logic used in StatsRecalculator (and conceptually in MainActivity)
     */
    private fun calculateHattricks(balls: List<Ball>, bowlerName: String): Int {
        var hattricks = 0
        var streak = 0
        balls.forEach { b ->
            if (b.bowlerName == bowlerName) {
                val isBowlerWicket = b.isWicket && !b.dismissalInfo.toString().contains("Run Out", ignoreCase = true)
                if (isBowlerWicket) {
                    streak++
                    if (streak == 3) {
                        hattricks++
                        streak = 0
                    }
                } else {
                    streak = 0
                }
            }
        }
        return hattricks
    }
}

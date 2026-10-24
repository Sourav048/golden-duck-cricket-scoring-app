package com.goldenduck.scoring

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PartnershipLogicTest {

    private lateinit var innings: Innings
    private val p1 = Player("Harkesh")
    private val p2 = Player("Kundan")
    private val p3 = Player("Vishnu")
    private val bowler = Player("Bowler A")

    @Before
    fun setUp() {
        innings = Innings("Team A", "Team B", 20, 10)
    }

    @Test
    fun testNormalTwoPlayerPartnership() {
        // Ball 1: Harkesh scores 4
        innings.addBall(4, BallType.NORMAL, false, p1, bowler, null, 4, null, p2)
        // Ball 2: Harkesh scores 2
        innings.addBall(2, BallType.NORMAL, false, p1, bowler, null, 2, null, p2)

        assertEquals(1, innings.partnerships.size)
        val p = innings.partnerships[0]
        assertEquals("Harkesh", p.batter1)
        assertEquals("Kundan", p.batter2)
        assertEquals(6, p.runs)
        assertEquals(2, p.balls)
        assertEquals(6, p.b1Runs)
        assertEquals(2, p.b1Balls)
        assertEquals(0, p.b2Runs)
        assertEquals(0, p.b2Balls)
    }

    @Test
    fun testStrikerRotationDoesNotCreateNewPartnership() {
        // Ball 1: Harkesh scores 1 run (Strike rotates to Kundan)
        innings.addBall(1, BallType.NORMAL, false, p1, bowler, null, 1, null, p2)
        // Ball 2: Kundan scores 2 runs
        innings.addBall(2, BallType.NORMAL, false, p2, bowler, null, 2, null, p1)
        // Ball 3: Kundan scores 1 run (Strike rotates back to Harkesh)
        innings.addBall(1, BallType.NORMAL, false, p2, bowler, null, 1, null, p1)
        // Ball 4: Harkesh scores 4 runs
        innings.addBall(4, BallType.NORMAL, false, p1, bowler, null, 4, null, p2)

        // Must still be only 1 partnership
        assertEquals(1, innings.partnerships.size)
        val p = innings.partnerships[0]
        assertEquals(8, p.runs)
        assertEquals(4, p.balls)
        assertEquals(5, p.b1Runs) // Harkesh: 1 + 4 = 5
        assertEquals(2, p.b1Balls)
        assertEquals(3, p.b2Runs) // Kundan: 2 + 1 = 3
        assertEquals(2, p.b2Balls)
    }

    @Test
    fun testPartnerFacesZeroBallsBeforeWicket() {
        // Ball 1: Harkesh & Kundan score 6
        innings.addBall(6, BallType.NORMAL, false, p1, bowler, null, 6, null, p2)
        // Ball 2: Harkesh gets out
        p1.isOut = true
        innings.addBall(0, BallType.NORMAL, true, p1, bowler, null, 0, p1, p2)

        assertEquals(1, innings.partnerships.size)
        assertEquals(6, innings.partnerships[0].runs)
        assertEquals(2, innings.partnerships[0].balls)

        // New partner Vishnu enters. Kundan faces ball 3 (scores 4) while Vishnu faces 0 balls.
        innings.addBall(4, BallType.NORMAL, false, p2, bowler, null, 4, null, p3)
        // Ball 4: Kundan gets out while Vishnu still faced 0 balls.
        p2.isOut = true
        innings.addBall(0, BallType.NORMAL, true, p2, bowler, null, 0, p2, p3)

        // Must have 2 partnerships now
        assertEquals(2, innings.partnerships.size)
        val p2Entry = innings.partnerships[1]
        // Partnership 2 must cleanly name Kundan and Vishnu
        assertEquals("Kundan", p2Entry.batter1)
        assertEquals("Vishnu", p2Entry.batter2)
        assertEquals(4, p2Entry.runs)
        assertEquals(2, p2Entry.balls)
        assertEquals(4, p2Entry.b1Runs)
        assertEquals(2, p2Entry.b1Balls)
        assertEquals(0, p2Entry.b2Runs)
        assertEquals(0, p2Entry.b2Balls)
    }

    @Test
    fun testSoloBattingPartnership() {
        // Player 1 plays alone
        innings.addBall(4, BallType.NORMAL, false, p1, bowler, null, 4, null, null)
        innings.addBall(6, BallType.NORMAL, false, p1, bowler, null, 6, null, null)

        assertEquals(1, innings.partnerships.size)
        val p = innings.partnerships[0]
        assertEquals("Harkesh", p.batter1)
        assertEquals("N/A", p.batter2)
        assertEquals(10, p.runs)
        assertEquals(2, p.balls)
    }

    @Test
    fun testUnbowledPlaceholderInPlaceUpdate() {
        // Manually start an unbowled 0(0) placeholder partnership
        innings.startNewPartnership("Harkesh", "Kundan")
        assertEquals(1, innings.partnerships.size)
        assertEquals(0, innings.partnerships[0].runs)
        assertEquals(0, innings.partnerships[0].balls)

        // First ball is bowled with Harkesh & Vishnu instead (before ball 1 was bowled)
        innings.addBall(4, BallType.NORMAL, false, p1, bowler, null, 4, null, p3)

        // Should NOT create a 2nd partnership row; it should update the 0(0) row in-place
        assertEquals(1, innings.partnerships.size)
        val p = innings.partnerships[0]
        assertEquals("Harkesh", p.batter1)
        assertEquals("Vishnu", p.batter2)
        assertEquals(4, p.runs)
        assertEquals(1, p.balls)
    }

    @Test
    fun testUndoLastBallRestoresPartnership() {
        innings.addBall(4, BallType.NORMAL, false, p1, bowler, null, 4, null, p2)
        assertEquals(1, innings.partnerships.size)
        assertEquals(4, innings.partnerships[0].runs)
        assertEquals(1, innings.partnerships[0].balls)

        innings.undoLastBall(p1, bowler)

        assertEquals(1, innings.partnerships.size)
        assertEquals(0, innings.partnerships[0].runs)
        assertEquals(0, innings.partnerships[0].balls)
    }

    @Test
    fun testMinutesPlayedReplacementLifecycle() {
        val now = System.currentTimeMillis()
        p1.entryTime = now - 600000 // 10 mins ago (600s)
        p1.minutesPlayed = 0

        // Simulate replacement of p1 with p3
        val oldBatter = p1
        if (oldBatter.entryTime > 0L) {
            val elapsedMs = now - oldBatter.entryTime
            val secsToAdd = (elapsedMs / 1000).toInt()
            if (secsToAdd > 0) oldBatter.minutesPlayed += secsToAdd
            oldBatter.entryTime = 0L
        }

        // Minutes played should be 600s (10 mins) and entryTime reset to 0L
        assertEquals(600, p1.minutesPlayed)
        assertEquals(0L, p1.entryTime)

        // Simulate p1 returning later (second stint)
        val returnTime = now + 1200000 // 20 mins later
        p1.entryTime = returnTime

        // Simulate 5 mins (300s) passing during second stint
        val secondStintEnd = returnTime + 300000
        val elapsed2 = (secondStintEnd - p1.entryTime) / 1000
        p1.minutesPlayed += elapsed2.toInt()
        p1.entryTime = 0L

        // Total time should accumulate: 600 + 300 = 900s (15 mins)
        assertEquals(900, p1.minutesPlayed)
        assertEquals(0L, p1.entryTime)
    }
}

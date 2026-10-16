package com.goldenduck.scoring

import org.junit.Assert.*
import org.junit.Test

class MatchEngineTest {

    @Test
    fun testBallBowled_updatesRunsAndStriker() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            currentStrikerName = "Batter 1",
            currentNonStrikerName = "Batter 2",
            currentBowlerName = "Bowler 1"
        )

        val ball = Ball(1, BallType.NORMAL, false, "Batter 1", "Bowler 1", 1, "Batter 2")
        val event = ScoringEvent.BallBowled(ball, "Batter 1", "Bowler 1")

        val newState = MatchEngine.reduce(initialState, event)

        assertEquals(1, newState.innings.totalRuns)
        assertEquals(1, newState.innings.legalBalls)

        // Single run swaps strike
        assertEquals("Batter 2", newState.currentStrikerName)
        assertEquals("Batter 1", newState.currentNonStrikerName)

        val batter1 = newState.getPlayer("Batter 1")
        assertNotNull(batter1)
        assertEquals(1, batter1!!.runsScored)
        assertEquals(1, batter1.ballsFaced)

        val bowler1 = newState.getPlayer("Bowler 1")
        assertNotNull(bowler1)
        assertEquals(1, bowler1!!.runsConceded)
        assertEquals(1, bowler1.ballsBowled)
    }

    @Test
    fun testEventSourcingUndo_rebuildsStateAccurately() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            currentStrikerName = "Batter 1",
            currentNonStrikerName = "Batter 2",
            currentBowlerName = "Bowler 1"
        )

        val ball1 = Ball(4, BallType.NORMAL, false, "Batter 1", "Bowler 1", 4, "Batter 2")
        val ball2 = Ball(6, BallType.NORMAL, false, "Batter 1", "Bowler 1", 6, "Batter 2")

        val events = listOf(
            ScoringEvent.BallBowled(ball1, "Batter 1", "Bowler 1"),
            ScoringEvent.BallBowled(ball2, "Batter 1", "Bowler 1")
        )

        val stateAfterTwoBalls = MatchEngine.rebuildState(initialState, events)
        assertEquals(10, stateAfterTwoBalls.innings.totalRuns)

        val batter1After2 = stateAfterTwoBalls.getPlayer("Batter 1")
        assertEquals(10, batter1After2!!.runsScored)
        assertEquals(1, batter1After2.fours)
        assertEquals(1, batter1After2.sixes)

        // UNDO THE SECOND BALL by dropping last event and rebuilding
        val undoEvents = events.dropLast(1)
        val stateAfterUndo = MatchEngine.rebuildState(initialState, undoEvents)

        assertEquals(4, stateAfterUndo.innings.totalRuns)
        assertEquals(1, stateAfterUndo.innings.legalBalls)
        assertEquals(1, stateAfterUndo.innings.balls.size)
        assertEquals(4, stateAfterUndo.innings.balls[0].runs)

        val batter1AfterUndo = stateAfterUndo.getPlayer("Batter 1")
        assertEquals(4, batter1AfterUndo!!.runsScored)
        assertEquals(1, batter1AfterUndo.fours)
        assertEquals(0, batter1AfterUndo.sixes)
    }

    @Test
    fun testUndoAllBalls_returnsPlayerStatsToZero() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            playerStats = mapOf(
                "Rodri" to Player("Rodri"),
                "Rodrigo Depaul" to Player("Rodrigo Depaul")
            ),
            currentStrikerName = "Rodri",
            currentNonStrikerName = "Batter 2",
            currentBowlerName = "Rodrigo Depaul"
        )

        val ball1 = Ball(0, BallType.NORMAL, false, "Rodri", "Rodrigo Depaul", 0, "Batter 2")
        val events = listOf(ScoringEvent.BallBowled(ball1, "Rodri", "Rodrigo Depaul"))

        val stateAfterBall = MatchEngine.rebuildState(initialState, events)
        assertEquals(1, stateAfterBall.getPlayer("Rodri")!!.ballsFaced)
        assertEquals(1, stateAfterBall.getPlayer("Rodrigo Depaul")!!.ballsBowled)

        // UNDO ALL BALLS (empty list)
        val stateAfterUndoAll = MatchEngine.rebuildState(initialState, emptyList())
        assertEquals(0, stateAfterUndoAll.innings.totalRuns)
        assertEquals(0, stateAfterUndoAll.innings.legalBalls)
        assertEquals(0, stateAfterUndoAll.getPlayer("Rodri")!!.ballsFaced)
        assertEquals(0, stateAfterUndoAll.getPlayer("Rodrigo Depaul")!!.ballsBowled)
    }

    @Test
    fun testRemoveLastBallCommentary_preservesOpeningAttackFact() {
        val innings = Innings("Spain", "Argentina", 20, 10)
        val introMsg = "Rodri is at the crease and will be on strike. Rodrigo Depaul will open the attack"
        innings.addCommentary("0.0", introMsg, null, "SETUP_FACT")

        // Add ball commentary
        innings.addCommentary("0.1", "Rodrigo Depaul to Rodri, no run", null, "BALL")
        assertEquals(2, innings.commentary.size)

        // Remove ball commentary (Undo)
        innings.removeLastBallCommentary()

        assertEquals(1, innings.commentary.size)
        assertEquals(introMsg, innings.commentary[0]?.text)
        assertEquals("SETUP_FACT", innings.commentary[0]?.type)
    }

    @Test
    fun testSoloBatter_oddRunDoesNotAssignStrikerAsNonStriker() {
        val initialState = MatchState(
            innings = Innings("Spain", "Argentina", 20, 10),
            playerStats = mapOf("Rodri" to Player("Rodri")),
            currentStrikerName = "Rodri",
            currentNonStrikerName = null,
            currentBowlerName = "Rodrigo Depaul"
        )

        // Rodri scores 1 run (odd run) while playing solo
        val ball1 = Ball(1, BallType.NORMAL, false, "Rodri", "Rodrigo Depaul", 1, null)
        val event1 = ScoringEvent.BallBowled(ball1, "Rodri", "Rodrigo Depaul")

        val stateAfterBall1 = MatchEngine.reduce(initialState, event1)

        // Striker remains Rodri, non-striker remains null
        assertEquals("Rodri", stateAfterBall1.currentStrikerName)
        assertNull(stateAfterBall1.currentNonStrikerName)

        // Rebuild state (Undo scenario)
        val stateRebuilt = MatchEngine.rebuildState(initialState, listOf(event1))
        assertEquals("Rodri", stateRebuilt.currentStrikerName)
        assertNull(stateRebuilt.currentNonStrikerName)
    }

    @Test
    fun testUndoSingleRun_restoresOriginalStrikerAndNonStriker() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            currentStrikerName = "Batter A",
            currentNonStrikerName = "Batter B",
            currentBowlerName = "Bowler 1"
        )

        // Batter A scores 1 run -> strike rotates to Batter B
        val ball1 = Ball(1, BallType.NORMAL, false, "Batter A", "Bowler 1", 1, "Batter B")
        val event1 = ScoringEvent.BallBowled(ball1, "Batter A", "Bowler 1")

        val stateAfterBall1 = MatchEngine.reduce(initialState, event1)
        assertEquals("Batter B", stateAfterBall1.currentStrikerName)
        assertEquals("Batter A", stateAfterBall1.currentNonStrikerName)

        // Undo Ball 1 (empty list of events)
        val stateAfterUndo = MatchEngine.rebuildState(initialState, emptyList())
        assertEquals("Batter A", stateAfterUndo.currentStrikerName)
        assertEquals("Batter B", stateAfterUndo.currentNonStrikerName)
    }

    @Test
    fun testUndoNoBall_resetsFreeHitActive() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            currentStrikerName = "Batter A",
            currentNonStrikerName = "Batter B",
            currentBowlerName = "Bowler 1"
        )

        val noBall = Ball(1, BallType.NO_BALL, false, "Batter A", "Bowler 1", 0, "Batter B")
        val event1 = ScoringEvent.BallBowled(noBall, "Batter A", "Bowler 1")

        val stateAfterNoBall = MatchEngine.reduce(initialState, event1)
        assertTrue(stateAfterNoBall.isFreeHitActive)

        // Undo No Ball (empty events)
        val stateAfterUndo = MatchEngine.rebuildState(initialState, emptyList())
        assertFalse(stateAfterUndo.isFreeHitActive)
    }
}

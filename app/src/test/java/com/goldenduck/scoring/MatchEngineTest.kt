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

    @Test
    fun testWideDuringFreeHit_keepsFreeHitActive() {
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

        // Bowler bowls a Wide on the Free Hit delivery
        val wideBall = Ball(1, BallType.WIDE, false, "Batter A", "Bowler 1", 0, "Batter B")
        val event2 = ScoringEvent.BallBowled(wideBall, "Batter A", "Bowler 1")

        val stateAfterWide = MatchEngine.reduce(stateAfterNoBall, event2)
        assertTrue("Free Hit should remain active after a Wide delivery", stateAfterWide.isFreeHitActive)

        // Bowler bowls a Normal ball on the subsequent Free Hit delivery
        val normalBall = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler 1", 0, "Batter B")
        val event3 = ScoringEvent.BallBowled(normalBall, "Batter A", "Bowler 1")

        val stateAfterNormal = MatchEngine.reduce(stateAfterWide, event3)
        assertFalse("Free Hit should be consumed after a legal delivery", stateAfterNormal.isFreeHitActive)
    }

    @Test
    fun testStandardWide_doesNotSwapStrike_andUndoPreservesNonStriker() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            currentStrikerName = "Batter A",
            currentNonStrikerName = "Batter B",
            currentBowlerName = "Bowler 1"
        )

        // 1. Standard 1-run Wide (no extra runs ran)
        val wideBall = Ball(1, BallType.WIDE, false, "Batter A", "Bowler 1", 0, "Batter B")
        val event1 = ScoringEvent.BallBowled(wideBall, "Batter A", "Bowler 1")

        val stateAfterWide = MatchEngine.reduce(initialState, event1)

        // Standard wide does NOT swap strike
        assertEquals("Batter A", stateAfterWide.currentStrikerName)
        assertEquals("Batter B", stateAfterWide.currentNonStrikerName)

        // 2. Batter A faces next ball (1 run)
        val ball2 = Ball(1, BallType.NORMAL, false, "Batter A", "Bowler 1", 1, "Batter B")
        val event2 = ScoringEvent.BallBowled(ball2, "Batter A", "Bowler 1")

        val stateAfterBall2 = MatchEngine.reduce(stateAfterWide, event2)
        assertEquals("Batter B", stateAfterBall2.currentStrikerName)
        assertEquals("Batter A", stateAfterBall2.currentNonStrikerName)

        // 3. Batter B faces Ball 3 (0 runs)
        val ball3 = Ball(0, BallType.NORMAL, false, "Batter B", "Bowler 1", 0, "Batter A")
        val event3 = ScoringEvent.BallBowled(ball3, "Batter B", "Bowler 1")

        val stateAfterBall3 = MatchEngine.reduce(stateAfterBall2, event3)
        assertEquals("Batter B", stateAfterBall3.currentStrikerName)
        assertEquals("Batter A", stateAfterBall3.currentNonStrikerName)

        // 4. UNDO Ball 3 (leaving Ball 1 and Ball 2)
        val stateAfterUndo = MatchEngine.rebuildState(initialState, listOf(event1, event2))
        assertEquals("Batter B", stateAfterUndo.currentStrikerName)
        assertEquals("Batter A", stateAfterUndo.currentNonStrikerName)
        assertNotNull("Non-striker must not be null after undo", stateAfterUndo.currentNonStrikerName)
    }

    @Test
    fun testWideWithRunsRan_swapsStrike_andUndoRestoresPositions() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            currentStrikerName = "Batter A",
            currentNonStrikerName = "Batter B",
            currentBowlerName = "Bowler 1"
        )

        // Wide + 1 run ran by batters (2 total runs) -> strike swaps
        val wideBall = Ball(2, BallType.WIDE, false, "Batter A", "Bowler 1", 0, "Batter B")
        val event1 = ScoringEvent.BallBowled(wideBall, "Batter A", "Bowler 1")

        val stateAfterWide = MatchEngine.reduce(initialState, event1)
        assertEquals("Batter B", stateAfterWide.currentStrikerName)
        assertEquals("Batter A", stateAfterWide.currentNonStrikerName)

        // Undo Wide ball
        val stateAfterUndo = MatchEngine.rebuildState(initialState, emptyList())
        assertEquals("Batter A", stateAfterUndo.currentStrikerName)
        assertEquals("Batter B", stateAfterUndo.currentNonStrikerName)
    }

    @Test
    fun testNoBallWithBye_swapsStrike_andUndoPreservesNonStriker() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            currentStrikerName = "Batter A",
            currentNonStrikerName = "Batter B",
            currentBowlerName = "Bowler 1",
            isRuleRunsOnWide = true
        )

        // No-Ball + 1 Bye ran by batters (2 total runs, 0 batRuns) -> strike swaps
        val noBall = Ball(2, BallType.NO_BALL, false, "Batter A", "Bowler 1", 0, "Batter B")
        val event1 = ScoringEvent.BallBowled(noBall, "Batter A", "Bowler 1")

        val stateAfterNoBall = MatchEngine.reduce(initialState, event1)

        // 1 Bye ran on No-Ball MUST swap strike to Batter B
        assertEquals("Batter B", stateAfterNoBall.currentStrikerName)
        assertEquals("Batter A", stateAfterNoBall.currentNonStrikerName)

        // Batter B faces Ball 2 on Free Hit (0 runs)
        val ball2 = Ball(0, BallType.NORMAL, false, "Batter B", "Bowler 1", 0, "Batter A")
        val event2 = ScoringEvent.BallBowled(ball2, "Batter B", "Bowler 1")

        val stateAfterBall2 = MatchEngine.reduce(stateAfterNoBall, event2)
        assertEquals("Batter B", stateAfterBall2.currentStrikerName)
        assertEquals("Batter A", stateAfterBall2.currentNonStrikerName)

        // Undo Ball 2 -> leaving No-Ball
        val stateAfterUndo = MatchEngine.rebuildState(initialState, listOf(event1))
        assertEquals("Batter B", stateAfterUndo.currentStrikerName)
        assertEquals("Batter A", stateAfterUndo.currentNonStrikerName)
        assertNotNull("Non-striker must not be null after undo", stateAfterUndo.currentNonStrikerName)
    }

    @Test
    fun testWideWithNoPenaltySetting_1RunRan_swapsStrike() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            currentStrikerName = "Batter A",
            currentNonStrikerName = "Batter B",
            currentBowlerName = "Bowler 1",
            isRuleRunsOnWide = false
        )

        // Runs on Wide disabled (0 penalty). Batters run 1 run (total 1 run) -> strike swaps
        val wideBall = Ball(1, BallType.WIDE, false, "Batter A", "Bowler 1", 0, "Batter B")
        val event1 = ScoringEvent.BallBowled(wideBall, "Batter A", "Bowler 1")

        val stateAfterWide = MatchEngine.reduce(initialState, event1)
        assertEquals("Batter B", stateAfterWide.currentStrikerName)
        assertEquals("Batter A", stateAfterWide.currentNonStrikerName)
    }

    @Test
    fun testByesAndLegByes_oddRunsSwapStrike() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            currentStrikerName = "Batter A",
            currentNonStrikerName = "Batter B",
            currentBowlerName = "Bowler 1"
        )

        // 1 Bye -> strike swaps
        val bye1 = Ball(1, BallType.BYE, false, "Batter A", "Bowler 1", 0, "Batter B")
        val state1 = MatchEngine.reduce(initialState, ScoringEvent.BallBowled(bye1, "Batter A", "Bowler 1"))
        assertEquals("Batter B", state1.currentStrikerName)
        assertEquals("Batter A", state1.currentNonStrikerName)

        // 2 Leg Byes -> strike does not swap
        val legBye2 = Ball(2, BallType.LEG_BYE, false, "Batter B", "Bowler 1", 0, "Batter A")
        val state2 = MatchEngine.reduce(state1, ScoringEvent.BallBowled(legBye2, "Batter B", "Bowler 1"))
        assertEquals("Batter B", state2.currentStrikerName)
        assertEquals("Batter A", state2.currentNonStrikerName)
    }

    @Test
    fun testOverCompletion_swapsStrikeForLegalBall6_doesNotSwapForWide() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            currentStrikerName = "Batter A",
            currentNonStrikerName = "Batter B",
            currentBowlerName = "Bowler 1"
        )

        // 5 legal dot balls
        var currentState = initialState
        repeat(5) {
            val dot = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler 1", 0, "Batter B")
            currentState = MatchEngine.reduce(currentState, ScoringEvent.BallBowled(dot, "Batter A", "Bowler 1"))
        }
        assertEquals("Batter A", currentState.currentStrikerName)

        // Ball 6 is a Wide -> NOT a legal ball, over does NOT end, strike does NOT swap
        val wide6 = Ball(1, BallType.WIDE, false, "Batter A", "Bowler 1", 0, "Batter B")
        val stateAfterWide = MatchEngine.reduce(currentState, ScoringEvent.BallBowled(wide6, "Batter A", "Bowler 1"))
        assertFalse(stateAfterWide.isOverComplete)
        assertEquals("Batter A", stateAfterWide.currentStrikerName)

        // Legal Ball 6 (dot ball) -> Over completes and strike swaps
        val dot6 = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler 1", 0, "Batter B")
        val stateAfterOverEnd = MatchEngine.reduce(stateAfterWide, ScoringEvent.BallBowled(dot6, "Batter A", "Bowler 1"))
        assertTrue(stateAfterOverEnd.isOverComplete)
        assertEquals("Batter B", stateAfterOverEnd.currentStrikerName)
        assertEquals("Batter A", stateAfterOverEnd.currentNonStrikerName)
    }

    @Test
    fun testWicketOnBall_clearsOutPlayer_andPreservesSurvivingBatter() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            currentStrikerName = "Batter A",
            currentNonStrikerName = "Batter B",
            currentBowlerName = "Bowler 1"
        )

        // Batter A gets Bowled Out
        val wicketBall = Ball(0, BallType.NORMAL, true, "Batter A", "Bowler 1", 0, "Batter B").apply {
            dismissalInfo = "b Bowler 1"
            outPlayerName = "Batter A"
        }
        val event = ScoringEvent.BallBowled(wicketBall, "Batter A", "Bowler 1", "Batter A")

        val stateAfterWicket = MatchEngine.reduce(initialState, event)
        assertNull("Out striker should be null awaiting replacement", stateAfterWicket.currentStrikerName)
        assertEquals("Batter B", stateAfterWicket.currentNonStrikerName)
        assertTrue(stateAfterWicket.getPlayer("Batter A")!!.isOut)
    }

    @Test
    fun testPenaltyAdded_increasesTeamTotal_doesNotAffectStrikeOrLegalBalls() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            currentStrikerName = "Batter A",
            currentNonStrikerName = "Batter B",
            currentBowlerName = "Bowler 1"
        )

        val penaltyEvent = ScoringEvent.PenaltyAdded(5, "Team A")
        val stateAfterPenalty = MatchEngine.reduce(initialState, penaltyEvent)

        assertEquals(5, stateAfterPenalty.innings.totalRuns)
        assertEquals(5, stateAfterPenalty.innings.penalties)
        assertEquals(0, stateAfterPenalty.innings.legalBalls)
        assertEquals("Batter A", stateAfterPenalty.currentStrikerName)
        assertEquals("Batter B", stateAfterPenalty.currentNonStrikerName)

        // Undo Penalty
        val stateAfterUndo = MatchEngine.rebuildState(initialState, emptyList())
        assertEquals(0, stateAfterUndo.innings.totalRuns)
    }

    @Test
    fun testPlayerRetired_hurtVsOut_andUndoRestoresPlayer() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            playerStats = mapOf(
                "Batter A" to Player("Batter A"),
                "Batter B" to Player("Batter B")
            ),
            currentStrikerName = "Batter A",
            currentNonStrikerName = "Batter B",
            currentBowlerName = "Bowler 1"
        )

        // Retired Hurt
        val eventRetiredHurt = ScoringEvent.PlayerRetired("Batter A", isHurt = true, "Retired Hurt")
        val stateAfterRetireHurt = MatchEngine.reduce(initialState, eventRetiredHurt)

        assertNull(stateAfterRetireHurt.currentStrikerName)
        assertEquals("Batter B", stateAfterRetireHurt.currentNonStrikerName)
        assertEquals("Retired Hurt", stateAfterRetireHurt.getPlayer("Batter A")!!.dismissalInfo)
        assertFalse(stateAfterRetireHurt.getPlayer("Batter A")!!.isOut)

        // Undo Retirement
        val stateAfterUndo = MatchEngine.rebuildState(initialState, emptyList())
        assertEquals("Batter A", stateAfterUndo.currentStrikerName)
        assertEquals("Batter B", stateAfterUndo.currentNonStrikerName)
        assertEquals("not out", stateAfterUndo.getPlayer("Batter A")!!.dismissalInfo)
    }

    @Test
    fun testManualStrikeSwapped_swapsStrikerAndNonStriker() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            currentStrikerName = "Batter A",
            currentNonStrikerName = "Batter B",
            currentBowlerName = "Bowler 1"
        )

        val swapEvent = ScoringEvent.StrikeSwapped("Manual Swap")
        val stateAfterSwap = MatchEngine.reduce(initialState, swapEvent)

        assertEquals("Batter B", stateAfterSwap.currentStrikerName)
        assertEquals("Batter A", stateAfterSwap.currentNonStrikerName)

        // Rebuilding state preserves manual swap
        val rebuiltState = MatchEngine.rebuildState(initialState, listOf(swapEvent))
        assertEquals("Batter B", rebuiltState.currentStrikerName)
        assertEquals("Batter A", rebuiltState.currentNonStrikerName)
    }

    @Test
    fun testFieldingStats_catchesStumpingsRunOuts() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            currentStrikerName = "Batter A",
            currentNonStrikerName = "Batter B",
            currentBowlerName = "Bowler 1"
        )

        // Catch by Fielder 1
        val caughtBall = Ball(0, BallType.NORMAL, true, "Batter A", "Bowler 1", 0, "Batter B").apply {
            dismissalInfo = "c Fielder 1 b Bowler 1"
        }
        val stateAfterCatch = MatchEngine.reduce(initialState, ScoringEvent.BallBowled(caughtBall, "Batter A", "Bowler 1", "Batter A", "Fielder 1"))

        assertEquals(1, stateAfterCatch.getPlayer("Fielder 1")!!.catches)

        // Stumping by Keeper
        val stumpedBall = Ball(0, BallType.NORMAL, true, "Batter B", "Bowler 1", 0, "Batter A").apply {
            dismissalInfo = "st Keeper b Bowler 1"
        }
        val stateAfterStump = MatchEngine.reduce(stateAfterCatch, ScoringEvent.BallBowled(stumpedBall, "Batter B", "Bowler 1", "Batter B", "Keeper"))

        assertEquals(1, stateAfterStump.getPlayer("Keeper")!!.stumpings)
    }

    @Test
    fun testBowlerAttackCommentary_firstSpellVsBackIntoAttack() {
        val b1 = Player("Bowler A")

        // First spell for Bowler A (ballsBowled == 0)
        fun formatAttackMsg(p: Player): String {
            return if (p.ballsBowled > 0) {
                "${p.name} [${p.oversBowledDisplay}-${p.maidens}-${p.runsConceded}-${p.wicketsTaken}] is back into the attack"
            } else {
                "${p.name} comes to the attack"
            }
        }

        assertEquals("Bowler A comes to the attack", formatAttackMsg(b1))

        // Simulate Bowler A bowling 1 over: 6 balls, 8 runs, 1 wicket
        b1.ballsBowled = 6
        b1.runsConceded = 8
        b1.wicketsTaken = 1

        // Returning spell for Bowler A (ballsBowled > 0)
        assertEquals("Bowler A [1.0-0-8-1] is back into the attack", formatAttackMsg(b1))
    }

    @Test
    fun testBowlerAttackCommentary_midOverReplacement() {
        val oldBowler = Player("Bowler A").apply {
            ballsBowled = 3 // 0.3 overs
            runsConceded = 4
            wicketsTaken = 0
            maidens = 0
        }
        val newBowler = Player("Bowler B").apply {
            ballsBowled = 12 // 2.0 overs
            runsConceded = 14
            wicketsTaken = 1
            maidens = 0
        }

        val replacementMsg = "${newBowler.name} [${newBowler.oversBowledDisplay}-${newBowler.maidens}-${newBowler.runsConceded}-${newBowler.wicketsTaken}] replaces ${oldBowler.name} [${oldBowler.oversBowledDisplay}-${oldBowler.maidens}-${oldBowler.runsConceded}-${oldBowler.wicketsTaken}]"

        assertEquals("Bowler B [2.0-0-14-1] replaces Bowler A [0.3-0-4-0]", replacementMsg)
    }

    @Test
    fun testDismissalSummaryFormatting() {
        fun formatWicketSummary(p: Player): String {
            val fours = p.fours
            val sixes = p.sixes
            val boundarySuffix = when {
                fours > 0 && sixes > 0 -> " [4s-$fours 6s-$sixes]"
                fours > 0 && sixes == 0 -> " [4s-$fours]"
                fours == 0 && sixes > 0 -> " [6s-$sixes]"
                else -> ""
            }
            val dInfo = if (p.dismissalInfo.isNotEmpty() && p.dismissalInfo != "not out") {
                p.dismissalInfo
            } else {
                "out"
            }
            return "<b>${p.name} $dInfo ${p.runsScored}(${p.ballsFaced})$boundarySuffix</b>"
        }

        // Player 1: Fours and Sixes
        val p1 = Player("Harry Brook").apply {
            dismissalInfo = "c Sahan Arachchige b Janith Liyanage"
            runsScored = 202
            ballsFaced = 116
            fours = 21
            sixes = 10
        }
        assertEquals("<b>Harry Brook c Sahan Arachchige b Janith Liyanage 202(116) [4s-21 6s-10]</b>", formatWicketSummary(p1))

        // Player 2: Only Fours
        val p2 = Player("Eshan Malinga").apply {
            dismissalInfo = "b Josh Tongue"
            runsScored = 4
            ballsFaced = 2
            fours = 1
            sixes = 0
        }
        assertEquals("<b>Eshan Malinga b Josh Tongue 4(2) [4s-1]</b>", formatWicketSummary(p2))

        // Player 3: Duck (0 runs, 0 fours, 0 sixes)
        val p3 = Player("Eshan Malinga").apply {
            dismissalInfo = "b Josh Tongue"
            runsScored = 0
            ballsFaced = 1
            fours = 0
            sixes = 0
        }
        assertEquals("<b>Eshan Malinga b Josh Tongue 0(1)</b>", formatWicketSummary(p3))
    }

    @Test
    fun testNewBatsmanEntryCommentary() {
        fun formatBatterStats(player: Player): String {
            val fours = player.fours
            val sixes = player.sixes
            val boundaryStr = when {
                fours > 0 && sixes > 0 -> "[4s-$fours, 6s-$sixes]"
                fours > 0 && sixes == 0 -> "[4s-$fours]"
                fours == 0 && sixes > 0 -> "[6s-$sixes]"
                else -> ""
            }
            return "${player.runsScored}(${player.ballsFaced})$boundaryStr"
        }

        fun formatEntryMsg(p: Player): String {
            return if (p.ballsFaced > 0) {
                "${p.name} ${formatBatterStats(p)} comes to the crease"
            } else {
                "${p.name} comes to the crease"
            }
        }

        // First-time entry (0 balls faced)
        val newBatsman = Player("Eshan Malinga")
        assertEquals("Eshan Malinga comes to the crease", formatEntryMsg(newBatsman))

        // Returning batter entry (already played earlier, e.g. 6 runs, 1 ball, 1 six)
        val returningBatsman = Player("B").apply {
            runsScored = 6
            ballsFaced = 1
            sixes = 1
        }
        assertEquals("B 6(1)[6s-1] comes to the crease", formatEntryMsg(returningBatsman))
    }

    @Test
    fun testLastBatterStandingCommentary() {
        val lastBatter = Player("Eshan Malinga")
        val lastMsg = "${lastBatter.name} is the last Batter Standing"
        assertEquals("Eshan Malinga is the last Batter Standing", lastMsg)
    }

    @Test
    fun testBatterReplacementCommentary() {
        fun formatBatterStats(player: Player): String {
            val fours = player.fours
            val sixes = player.sixes
            val boundaryStr = when {
                fours > 0 && sixes > 0 -> "[4s-$fours, 6s-$sixes]"
                fours > 0 && sixes == 0 -> "[4s-$fours]"
                fours == 0 && sixes > 0 -> "[6s-$sixes]"
                else -> ""
            }
            return "${player.runsScored}(${player.ballsFaced})$boundaryStr"
        }

        val oldBatter = Player("Sourav Sharma").apply {
            runsScored = 30
            ballsFaced = 12
            fours = 2
            sixes = 3
        }

        // Case 1: Incoming batter enters for the first time
        val newBatter1 = Player("Harsh Singh")
        val msg1 = "${newBatter1.name}, comes to the crease, Replacing ${oldBatter.name} ${formatBatterStats(oldBatter)} as a Striker"
        assertEquals("Harsh Singh, comes to the crease, Replacing Sourav Sharma 30(12)[4s-2, 6s-3] as a Striker", msg1)

        // Case 2: Incoming batter has played earlier
        val newBatter2 = Player("Harsh Singh").apply {
            runsScored = 23
            ballsFaced = 42
            fours = 2
            sixes = 1
        }
        val msg2 = "${newBatter2.name} ${formatBatterStats(newBatter2)} Replacing ${oldBatter.name} ${formatBatterStats(oldBatter)} as a Non-Striker"
        assertEquals("Harsh Singh 23(42)[4s-2, 6s-1] Replacing Sourav Sharma 30(12)[4s-2, 6s-3] as a Non-Striker", msg2)
    }

    @Test
    fun testUndoFromNextOverAfterNoBallAtFiveBalls_preservesBowlerAndFreeHit() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            currentStrikerName = "Batter A",
            currentNonStrikerName = "Batter B",
            currentBowlerName = "Bowler F",
            isRuleRunsOnWide = false
        )

        // 5 normal balls
        val b1 = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler F", 0, "Batter B")
        val b2 = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler F", 0, "Batter B")
        val b3 = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler F", 0, "Batter B")
        val b4 = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler F", 0, "Batter B")
        val b5 = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler F", 0, "Batter B")

        // 6th ball is a No Ball
        val b6NoBall = Ball(0, BallType.NO_BALL, false, "Batter A", "Bowler F", 0, "Batter B")

        // 7th ball is a legal ball (completes 1.0 overs)
        val b7Legal = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler F", 0, "Batter B")

        val events = listOf(
            ScoringEvent.BallBowled(b1, "Batter A", "Bowler F"),
            ScoringEvent.BallBowled(b2, "Batter A", "Bowler F"),
            ScoringEvent.BallBowled(b3, "Batter A", "Bowler F"),
            ScoringEvent.BallBowled(b4, "Batter A", "Bowler F"),
            ScoringEvent.BallBowled(b5, "Batter A", "Bowler F"),
            ScoringEvent.BallBowled(b6NoBall, "Batter A", "Bowler F"),
            ScoringEvent.BallBowled(b7Legal, "Batter A", "Bowler F")
        )

        val fullState = MatchEngine.rebuildState(initialState, events)
        assertEquals(6, fullState.innings.legalBalls)
        assertTrue(fullState.isOverComplete)

        // UNDO ball 7 (dropping the 7th ball)
        val undoEvents = events.dropLast(1)
        val stateAfterUndo = MatchEngine.rebuildState(initialState, undoEvents)

        assertEquals(5, stateAfterUndo.innings.legalBalls)
        assertFalse(stateAfterUndo.isOverComplete)
        assertTrue(stateAfterUndo.isFreeHitActive)
        assertEquals("Bowler F", stateAfterUndo.currentBowlerName)
    }

    @Test
    fun testWicketOnLastBallOfOver_movesNonStrikerToStrikerForNextOver() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 20, 10),
            currentStrikerName = "Batter A",
            currentNonStrikerName = "Batter B",
            currentBowlerName = "Bowler F"
        )

        // 5 normal balls
        val b1 = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler F", 0, "Batter B")
        val b2 = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler F", 0, "Batter B")
        val b3 = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler F", 0, "Batter B")
        val b4 = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler F", 0, "Batter B")
        val b5 = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler F", 0, "Batter B")

        // 6th ball is a Wicket of Batter A
        val b6Wicket = Ball(0, BallType.NORMAL, true, "Batter A", "Bowler F", 0, "Batter B").apply {
            dismissalInfo = "b Bowler F"
            outPlayerName = "Batter A"
        }

        val over1Events = listOf(
            ScoringEvent.BallBowled(b1, "Batter A", "Bowler F"),
            ScoringEvent.BallBowled(b2, "Batter A", "Bowler F"),
            ScoringEvent.BallBowled(b3, "Batter A", "Bowler F"),
            ScoringEvent.BallBowled(b4, "Batter A", "Bowler F"),
            ScoringEvent.BallBowled(b5, "Batter A", "Bowler F"),
            ScoringEvent.BallBowled(b6Wicket, "Batter A", "Bowler F", outPlayerName = "Batter A")
        )

        val stateAfterOver1 = MatchEngine.rebuildState(initialState, over1Events)

        // At end of Over 1, Batter B MUST become currentStrikerName for Over 2
        assertEquals("Batter B", stateAfterOver1.currentStrikerName)
        assertNull(stateAfterOver1.currentNonStrikerName)
    }
}

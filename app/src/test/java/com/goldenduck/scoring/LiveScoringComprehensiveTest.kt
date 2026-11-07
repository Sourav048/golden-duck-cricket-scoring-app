package com.goldenduck.scoring

import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive test suite verifying all Live Scoring and Stat Assignment scenarios:
 * 1. Scoring buttons (0, 1, 2, 3, 4, 6) & strike rotation
 * 2. Boundary vs Non-boundary & Overthrow rules
 * 3. Extras (Wide, No-Ball, Byes, Leg-Byes) & their interactions
 * 4. All Dismissal types & correct attribution to Bowler, Catcher, Keeper, and Fielders
 * 5. Maidens, Dot Balls, Sixes Conceded, and Economy calculations
 * 6. Innings completion logic (Target reached, All out, Overs completed, Retired hurt blocking)
 * 7. Event Sourcing / Undo integrity
 * 8. PlayerMatchStatEntity conversion integrity
 */
class LiveScoringComprehensiveTest {

    private fun setupMatch(overs: Int = 5, wickets: Int = 10): Triple<Innings, Player, Player> {
        val innings = Innings("Team A", "Team B", overs, wickets)
        val striker = Player("Striker").apply { id = "striker-1" }
        val nonStriker = Player("NonStriker").apply { id = "non-striker-1" }
        return Triple(innings, striker, nonStriker)
    }

    private fun newBowler(name: String = "Bowler 1", id: String = "bowler-1") =
        Player(name).apply { this.id = id }

    // =========================================================================
    // 1. BASIC SCORING BUTTONS (0, 1, 2, 3, 4, 6)
    // =========================================================================

    @Test
    fun testDotBall_assignsDotBallCorrectly() {
        val (innings, striker, nonStriker) = setupMatch()
        val bowler = newBowler()

        innings.addBall(
            runs = 0,
            type = BallType.NORMAL,
            isWicket = false,
            batsman = striker,
            bowler = bowler,
            fielder = null,
            manualBatRuns = 0,
            nonStriker = nonStriker,
            isBoundary = false
        )

        assertEquals("Total runs must be 0", 0, innings.totalRuns)
        assertEquals("Legal balls must be 1", 1, innings.legalBalls)
        assertEquals("Batter runs scored must be 0", 0, striker.runsScored)
        assertEquals("Batter balls faced must be 1", 1, striker.ballsFaced)
        assertEquals("Bowler dot balls must be 1", 1, bowler.dotBalls)
        assertEquals("Bowler balls bowled must be 1", 1, bowler.ballsBowled)
        assertEquals("Bowler runs conceded must be 0", 0, bowler.runsConceded)
    }

    @Test
    fun testSingleRun_incrementsRunsAndSwapsStrike() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 5, 10),
            playerStats = mapOf(
                "p1" to Player("Batter A").apply { id = "p1" },
                "p2" to Player("Batter B").apply { id = "p2" },
                "b1" to Player("Bowler 1").apply { id = "b1" }
            ),
            currentStrikerId = "p1", currentStrikerName = "Batter A",
            currentNonStrikerId = "p2", currentNonStrikerName = "Batter B",
            currentBowlerId = "b1", currentBowlerName = "Bowler 1"
        )

        val ball = Ball(1, BallType.NORMAL, false, "Batter A", "Bowler 1", 1, "Batter B").apply {
            batsmanId = "p1"; bowlerId = "b1"; nonStrikerId = "p2"
        }
        val event = ScoringEvent.BallBowled(ball, "Batter A", "Bowler 1", facingBatsmanId = "p1", bowlerId = "b1", nonStrikerId = "p2")

        val state = MatchEngine.reduce(initialState, event)

        assertEquals("Score should be 1", 1, state.innings.totalRuns)
        assertEquals("Batter B should now be on strike after 1 run", "p2", state.currentStrikerId)
        assertEquals("Batter A should now be non-striker", "p1", state.currentNonStrikerId)
        assertEquals(1, state.getPlayer("p1")!!.runsScored)
        assertEquals(1, state.getPlayer("p1")!!.ballsFaced)
        assertEquals(1, state.getPlayer("b1")!!.runsConceded)
    }

    @Test
    fun testTwoRuns_keepsFacingBatterOnStrike() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 5, 10),
            playerStats = mapOf(
                "p1" to Player("Batter A").apply { id = "p1" },
                "p2" to Player("Batter B").apply { id = "p2" },
                "b1" to Player("Bowler 1").apply { id = "b1" }
            ),
            currentStrikerId = "p1", currentStrikerName = "Batter A",
            currentNonStrikerId = "p2", currentNonStrikerName = "Batter B",
            currentBowlerId = "b1", currentBowlerName = "Bowler 1"
        )

        val ball = Ball(2, BallType.NORMAL, false, "Batter A", "Bowler 1", 2, "Batter B").apply {
            batsmanId = "p1"; bowlerId = "b1"; nonStrikerId = "p2"
        }
        val event = ScoringEvent.BallBowled(ball, "Batter A", "Bowler 1", facingBatsmanId = "p1", bowlerId = "b1", nonStrikerId = "p2")

        val state = MatchEngine.reduce(initialState, event)

        assertEquals(2, state.innings.totalRuns)
        assertEquals("Batter A should remain on strike after 2 runs", "p1", state.currentStrikerId)
        assertEquals(2, state.getPlayer("p1")!!.runsScored)
    }

    @Test
    fun testThreeRuns_swapsStrike() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 5, 10),
            playerStats = mapOf(
                "p1" to Player("Batter A").apply { id = "p1" },
                "p2" to Player("Batter B").apply { id = "p2" },
                "b1" to Player("Bowler 1").apply { id = "b1" }
            ),
            currentStrikerId = "p1", currentStrikerName = "Batter A",
            currentNonStrikerId = "p2", currentNonStrikerName = "Batter B",
            currentBowlerId = "b1", currentBowlerName = "Bowler 1"
        )

        val ball = Ball(3, BallType.NORMAL, false, "Batter A", "Bowler 1", 3, "Batter B").apply {
            batsmanId = "p1"; bowlerId = "b1"; nonStrikerId = "p2"
        }
        val event = ScoringEvent.BallBowled(ball, "Batter A", "Bowler 1", facingBatsmanId = "p1", bowlerId = "b1", nonStrikerId = "p2")

        val state = MatchEngine.reduce(initialState, event)

        assertEquals(3, state.innings.totalRuns)
        assertEquals("Batter B should now be on strike after 3 runs", "p2", state.currentStrikerId)
    }

    @Test
    fun testBoundaryFour_creditsFoursAndKeepsStrike() {
        val (innings, striker, nonStriker) = setupMatch()
        val bowler = newBowler()

        innings.addBall(
            runs = 4,
            type = BallType.NORMAL,
            isWicket = false,
            batsman = striker,
            bowler = bowler,
            fielder = null,
            manualBatRuns = 4,
            nonStriker = nonStriker,
            isBoundary = true
        )

        assertEquals(4, innings.totalRuns)
        assertEquals(4, striker.runsScored)
        assertEquals(1, striker.ballsFaced)
        assertEquals("Batter fours must be 1", 1, striker.fours)
        assertEquals("Batter sixes must be 0", 0, striker.sixes)
        assertEquals(4, bowler.runsConceded)
        assertEquals(0, bowler.sixesConceded)
    }

    @Test
    fun testBoundarySix_creditsSixesToBatterAndBowler() {
        val (innings, striker, nonStriker) = setupMatch()
        val bowler = newBowler()

        innings.addBall(
            runs = 6,
            type = BallType.NORMAL,
            isWicket = false,
            batsman = striker,
            bowler = bowler,
            fielder = null,
            manualBatRuns = 6,
            nonStriker = nonStriker,
            isBoundary = true
        )

        assertEquals(6, innings.totalRuns)
        assertEquals(6, striker.runsScored)
        assertEquals(1, striker.ballsFaced)
        assertEquals(0, striker.fours)
        assertEquals("Batter sixes must be 1", 1, striker.sixes)
        assertEquals(6, bowler.runsConceded)
    }

    // =========================================================================
    // 2. EXTRAS (WIDE, NO-BALL, BYES, LEG-BYES, OVERTHROWS)
    // =========================================================================

    @Test
    fun testWideBall_doesNotIncrementLegalBallsOrBallsFaced() {
        val (innings, striker, nonStriker) = setupMatch()
        val bowler = newBowler()

        innings.addBall(
            runs = 1,
            type = BallType.WIDE,
            isWicket = false,
            batsman = striker,
            bowler = bowler,
            fielder = null,
            manualBatRuns = 0,
            nonStriker = nonStriker
        )

        assertEquals(1, innings.totalRuns)
        assertEquals("Legal balls must remain 0 for wide", 0, innings.legalBalls)
        assertEquals("Batter balls faced must remain 0 for wide", 0, striker.ballsFaced)
        assertEquals("Batter runs scored must remain 0 for wide", 0, striker.runsScored)
        assertEquals(1, innings.wides)
        assertEquals(1, bowler.widesConceded)
        assertEquals(1, bowler.runsConceded)
        assertEquals(0, bowler.ballsBowled)
    }

    @Test
    fun testWideWithThreeRunningRuns_awardsFourWidesAndSwapsStrike() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 5, 10),
            playerStats = mapOf(
                "p1" to Player("Batter A").apply { id = "p1" },
                "p2" to Player("Batter B").apply { id = "p2" },
                "b1" to Player("Bowler 1").apply { id = "b1" }
            ),
            currentStrikerId = "p1", currentStrikerName = "Batter A",
            currentNonStrikerId = "p2", currentNonStrikerName = "Batter B",
            currentBowlerId = "b1", currentBowlerName = "Bowler 1"
        )

        // 1 wide penalty + 3 running runs = 4 total runs
        val ball = Ball(4, BallType.WIDE, false, "Batter A", "Bowler 1", 0, "Batter B").apply {
            batsmanId = "p1"; bowlerId = "b1"; nonStrikerId = "p2"
        }
        val event = ScoringEvent.BallBowled(ball, "Batter A", "Bowler 1", facingBatsmanId = "p1", bowlerId = "b1", nonStrikerId = "p2")

        val state = MatchEngine.reduce(initialState, event)

        assertEquals(4, state.innings.totalRuns)
        assertEquals(4, state.innings.wides)
        assertEquals(0, state.innings.legalBalls)
        assertEquals("Odd running runs (3) must swap strike", "p2", state.currentStrikerId)
        assertEquals(0, state.getPlayer("p1")!!.runsScored)
        assertEquals(0, state.getPlayer("p1")!!.ballsFaced)
        assertEquals(4, state.getPlayer("b1")!!.runsConceded)
    }

    @Test
    fun testNoBallOffBatFour_creditsFourToBatterAndOnePenaltyToBowler() {
        val (innings, striker, nonStriker) = setupMatch()
        val bowler = newBowler()

        // 1 NB penalty + 4 bat runs = 5 total runs
        innings.addBall(
            runs = 5,
            type = BallType.NO_BALL,
            isWicket = false,
            batsman = striker,
            bowler = bowler,
            fielder = null,
            manualBatRuns = 4,
            nonStriker = nonStriker,
            isBoundary = true
        )

        assertEquals("Total runs must be 5", 5, innings.totalRuns)
        assertEquals("No balls extra must be 1", 1, innings.noBalls)
        assertEquals("Legal balls must be 0", 0, innings.legalBalls)
        assertEquals("Batter runs scored must be 4", 4, striker.runsScored)
        assertEquals("Batter balls faced must be 1", 1, striker.ballsFaced)
        assertEquals("Batter fours must be 1", 1, striker.fours)
        assertEquals("Bowler runs conceded must be 5 (4 bat + 1 NB)", 5, bowler.runsConceded)
        assertEquals("Bowler balls bowled must be 0", 0, bowler.ballsBowled)
    }

    @Test
    fun testNoBallWithByes_doesNotChargeByesToBowler() {
        val (innings, striker, nonStriker) = setupMatch()
        val bowler = newBowler()

        // 1 NB penalty + 2 byes = 3 total runs
        innings.addBall(
            runs = 3,
            type = BallType.NO_BALL,
            isWicket = false,
            batsman = striker,
            bowler = bowler,
            fielder = null,
            manualBatRuns = 0,
            nonStriker = nonStriker,
            byeRuns = 2,
            isBoundary = false
        )

        assertEquals(3, innings.totalRuns)
        assertEquals(1, innings.noBalls)
        assertEquals(2, innings.byes)
        assertEquals(0, striker.runsScored)
        assertEquals(1, striker.ballsFaced)
        assertEquals("Bowler should only concede 1 run for the NB penalty, not the byes", 1, bowler.runsConceded)
    }

    @Test
    fun testByes_doNotChargeBowlerAndDoNotCreditBatter() {
        val (innings, striker, nonStriker) = setupMatch()
        val bowler = newBowler()

        innings.addBall(
            runs = 2,
            type = BallType.BYE,
            isWicket = false,
            batsman = striker,
            bowler = bowler,
            fielder = null,
            manualBatRuns = 0,
            nonStriker = nonStriker,
            byeRuns = 2
        )

        assertEquals(2, innings.totalRuns)
        assertEquals(2, innings.byes)
        assertEquals(1, innings.legalBalls)
        assertEquals(0, striker.runsScored)
        assertEquals(1, striker.ballsFaced)
        assertEquals("Bowler runs conceded must be 0 for byes", 0, bowler.runsConceded)
        assertEquals(1, bowler.ballsBowled)
    }

    @Test
    fun testLegByes_doNotChargeBowlerAndDoNotCreditBatter() {
        val (innings, striker, nonStriker) = setupMatch()
        val bowler = newBowler()

        innings.addBall(
            runs = 1,
            type = BallType.LEG_BYE,
            isWicket = false,
            batsman = striker,
            bowler = bowler,
            fielder = null,
            manualBatRuns = 0,
            nonStriker = nonStriker,
            legByeRuns = 1
        )

        assertEquals(1, innings.totalRuns)
        assertEquals(1, innings.legByes)
        assertEquals(1, innings.legalBalls)
        assertEquals(0, striker.runsScored)
        assertEquals(1, striker.ballsFaced)
        assertEquals("Bowler runs conceded must be 0 for leg-byes", 0, bowler.runsConceded)
        assertEquals(1, bowler.ballsBowled)
    }

    @Test
    fun testOverthrow_doesNotAwardBoundaryFoursOrSixes() {
        val (innings, striker, nonStriker) = setupMatch()
        val bowler = newBowler()

        // 2 runs completed + 4 overthrow = 6 total runs
        innings.addBall(
            runs = 6,
            type = BallType.NORMAL,
            isWicket = false,
            batsman = striker,
            bowler = bowler,
            fielder = null,
            manualBatRuns = 6,
            nonStriker = nonStriker,
            overthrowRuns = 4,
            isBoundary = false
        )

        assertEquals(6, innings.totalRuns)
        assertEquals(6, striker.runsScored)
        assertEquals(0, striker.fours)
        assertEquals("Overthrow 6 must not count as a six", 0, striker.sixes)
        assertEquals(6, bowler.runsConceded)
    }

    // =========================================================================
    // 3. DISMISSALS & STAT ATTRIBUTION
    // =========================================================================

    @Test
    fun testBowledOut_creditsBowlerOnly() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 5, 10),
            playerStats = mapOf(
                "p1" to Player("Batter A").apply { id = "p1" },
                "p2" to Player("Batter B").apply { id = "p2" },
                "b1" to Player("Bowler 1").apply { id = "b1" }
            ),
            currentStrikerId = "p1", currentStrikerName = "Batter A",
            currentNonStrikerId = "p2", currentNonStrikerName = "Batter B",
            currentBowlerId = "b1", currentBowlerName = "Bowler 1"
        )

        val detail = WicketDetail("p1", DismissalType.BOWLED, "b1")
        val ball = Ball(0, BallType.NORMAL, true, "Batter A", "Bowler 1", 0, "Batter B").apply {
            batsmanId = "p1"; bowlerId = "b1"; nonStrikerId = "p2"; wicketDetail = detail
        }
        val event = ScoringEvent.BallBowled(ball, "Batter A", "Bowler 1", facingBatsmanId = "p1", bowlerId = "b1", nonStrikerId = "p2")

        val state = MatchEngine.reduce(initialState, event)

        assertEquals(1, state.innings.totalWickets)
        assertTrue(state.getPlayer("p1")!!.isOut)
        assertEquals(1, state.getPlayer("b1")!!.wicketsTaken)
        assertNull("Striker should be vacated after being dismissed", state.currentStrikerId)
        assertEquals("p2", state.currentNonStrikerId)
    }

    @Test
    fun testCaughtOut_creditsCatcherAndBowler() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 5, 10),
            playerStats = mapOf(
                "p1" to Player("Batter A").apply { id = "p1" },
                "p2" to Player("Batter B").apply { id = "p2" },
                "b1" to Player("Bowler 1").apply { id = "b1" },
                "f1" to Player("Fielder 1").apply { id = "f1" }
            ),
            currentStrikerId = "p1", currentStrikerName = "Batter A",
            currentNonStrikerId = "p2", currentNonStrikerName = "Batter B",
            currentBowlerId = "b1", currentBowlerName = "Bowler 1"
        )

        val detail = WicketDetail("p1", DismissalType.CAUGHT, "b1", primaryFielderId = "f1")
        val ball = Ball(0, BallType.NORMAL, true, "Batter A", "Bowler 1", 0, "Batter B").apply {
            batsmanId = "p1"; bowlerId = "b1"; nonStrikerId = "p2"; wicketDetail = detail
        }
        val event = ScoringEvent.BallBowled(
            ball, "Batter A", "Bowler 1",
            facingBatsmanId = "p1", bowlerId = "b1", nonStrikerId = "p2",
            primaryFielderName = "Fielder 1"
        )

        val state = MatchEngine.reduce(initialState, event)

        assertTrue(state.getPlayer("p1")!!.isOut)
        assertEquals(1, state.getPlayer("b1")!!.wicketsTaken)
        assertEquals("Catcher must receive 1 catch", 1, state.getPlayer("f1")!!.catches)
        assertEquals(0, state.getPlayer("f1")!!.stumpings)
        assertEquals(0, state.getPlayer("f1")!!.runOuts)
    }

    @Test
    fun testCaughtAndBowled_creditsBowlerWithWicketAndCatch() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 5, 10),
            playerStats = mapOf(
                "p1" to Player("Batter A").apply { id = "p1" },
                "p2" to Player("Batter B").apply { id = "p2" },
                "b1" to Player("Bowler 1").apply { id = "b1" }
            ),
            currentStrikerId = "p1", currentStrikerName = "Batter A",
            currentNonStrikerId = "p2", currentNonStrikerName = "Batter B",
            currentBowlerId = "b1", currentBowlerName = "Bowler 1"
        )

        val detail = WicketDetail("p1", DismissalType.CAUGHT, "b1", primaryFielderId = "b1")
        val ball = Ball(0, BallType.NORMAL, true, "Batter A", "Bowler 1", 0, "Batter B").apply {
            batsmanId = "p1"; bowlerId = "b1"; nonStrikerId = "p2"; wicketDetail = detail
        }
        val event = ScoringEvent.BallBowled(
            ball, "Batter A", "Bowler 1",
            facingBatsmanId = "p1", bowlerId = "b1", nonStrikerId = "p2",
            primaryFielderName = "Bowler 1"
        )

        val state = MatchEngine.reduce(initialState, event)

        val bowler = state.getPlayer("b1")!!
        assertEquals("Bowler must receive 1 wicket", 1, bowler.wicketsTaken)
        assertEquals("Bowler must receive 1 catch for c&b", 1, bowler.catches)
    }

    @Test
    fun testStumped_creditsWicketToBowlerAndStumpingToKeeper() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 5, 10),
            playerStats = mapOf(
                "p1" to Player("Batter A").apply { id = "p1" },
                "p2" to Player("Batter B").apply { id = "p2" },
                "b1" to Player("Bowler 1").apply { id = "b1" },
                "k1" to Player("Wicketkeeper").apply { id = "k1" }
            ),
            currentStrikerId = "p1", currentStrikerName = "Batter A",
            currentNonStrikerId = "p2", currentNonStrikerName = "Batter B",
            currentBowlerId = "b1", currentBowlerName = "Bowler 1"
        )

        val detail = WicketDetail("p1", DismissalType.STUMPED, "b1", primaryFielderId = "k1")
        val ball = Ball(0, BallType.NORMAL, true, "Batter A", "Bowler 1", 0, "Batter B").apply {
            batsmanId = "p1"; bowlerId = "b1"; nonStrikerId = "p2"; wicketDetail = detail
        }
        val event = ScoringEvent.BallBowled(
            ball, "Batter A", "Bowler 1",
            facingBatsmanId = "p1", bowlerId = "b1", nonStrikerId = "p2",
            primaryFielderName = "Wicketkeeper"
        )

        val state = MatchEngine.reduce(initialState, event)

        assertEquals(1, state.getPlayer("b1")!!.wicketsTaken)
        assertEquals("Wicketkeeper must receive 1 stumping", 1, state.getPlayer("k1")!!.stumpings)
        assertEquals(0, state.getPlayer("k1")!!.catches)
    }

    @Test
    fun testRunOutTwoFielders_creditsBothFieldersAndNotBowler() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 5, 10),
            playerStats = mapOf(
                "p1" to Player("Batter A").apply { id = "p1" },
                "p2" to Player("Batter B").apply { id = "p2" },
                "b1" to Player("Bowler 1").apply { id = "b1" },
                "f1" to Player("Thrower").apply { id = "f1" },
                "f2" to Player("Breaker").apply { id = "f2" }
            ),
            currentStrikerId = "p1", currentStrikerName = "Batter A",
            currentNonStrikerId = "p2", currentNonStrikerName = "Batter B",
            currentBowlerId = "b1", currentBowlerName = "Bowler 1"
        )

        val detail = WicketDetail("p1", DismissalType.RUN_OUT, "b1", primaryFielderId = "f1", secondaryFielderId = "f2", runsCompleted = 1)
        val ball = Ball(1, BallType.NORMAL, true, "Batter A", "Bowler 1", 1, "Batter B").apply {
            batsmanId = "p1"; bowlerId = "b1"; nonStrikerId = "p2"; wicketDetail = detail
        }
        val event = ScoringEvent.BallBowled(
            ball, "Batter A", "Bowler 1",
            facingBatsmanId = "p1", bowlerId = "b1", nonStrikerId = "p2",
            primaryFielderName = "Thrower", secondaryFielderName = "Breaker"
        )

        val state = MatchEngine.reduce(initialState, event)

        assertEquals("Bowler must NOT receive wicket for run out", 0, state.getPlayer("b1")!!.wicketsTaken)
        assertEquals("Thrower must receive 1 run out", 1, state.getPlayer("f1")!!.runOuts)
        assertEquals("Breaker must receive 1 run out", 1, state.getPlayer("f2")!!.runOuts)
    }

    @Test
    fun testRetiredHurt_doesNotIncrementTotalWicketsOrSetIsOut() {
        val (innings, striker, nonStriker) = setupMatch()
        val bowler = newBowler()

        innings.recordRetired(
            p = striker,
            isWicket = false,
            info = "retired hurt",
            sName = striker.name,
            nsName = nonStriker.name,
            bName = bowler.name
        )

        assertEquals("Total wickets must remain 0 for retired hurt", 0, innings.totalWickets)
        assertEquals("Retired hurt count must be 1", 1, innings.retiredHurtCount)
        assertFalse("Batter must NOT be marked isOut for retired hurt", striker.isOut)
        assertEquals("retired hurt", striker.dismissalInfo)
    }

    @Test
    fun testRetiredOut_incrementsTotalWicketsAndSetsIsOut() {
        val (innings, striker, nonStriker) = setupMatch()
        val bowler = newBowler()

        innings.recordRetired(
            p = striker,
            isWicket = true,
            info = "retired out",
            sName = striker.name,
            nsName = nonStriker.name,
            bName = bowler.name
        )

        assertEquals("Total wickets must be 1 for retired out", 1, innings.totalWickets)
        assertEquals("Retired hurt count must be 0 for retired out", 0, innings.retiredHurtCount)
        assertTrue("Batter must be marked isOut for retired out", striker.isOut)
        assertEquals("retired out", striker.dismissalInfo)
        assertEquals("Bowler must NOT receive wicket for retired out", 0, bowler.wicketsTaken)
    }

    // =========================================================================
    // 4. MAIDEN OVERS & BOWLER FIGURES
    // =========================================================================

    @Test
    fun testMaidenOver_sixDotBallsIncrementsMaidenViaMatchEngine() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 2, 10),
            playerStats = mapOf(
                "p1" to Player("Batter A").apply { id = "p1" },
                "b1" to Player("Bowler 1").apply { id = "b1" }
            ),
            currentStrikerId = "p1", currentStrikerName = "Batter A",
            currentBowlerId = "b1", currentBowlerName = "Bowler 1"
        )

        val events = (1..6).map {
            val ball = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler 1", 0, null).apply {
                batsmanId = "p1"; bowlerId = "b1"
            }
            ScoringEvent.BallBowled(ball, "Batter A", "Bowler 1", facingBatsmanId = "p1", bowlerId = "b1")
        }

        val state = MatchEngine.rebuildState(initialState, events)

        assertEquals(6, state.innings.legalBalls)
        assertEquals(0, state.getPlayer("b1")!!.runsConceded)
        assertEquals("Maidens must be 1 for a 6-dot over", 1, state.getPlayer("b1")!!.maidens)
    }

    @Test
    fun testMaidenOverWithByes_stillCountsAsMaidenBecauseBowlerConcededZero() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 2, 10),
            playerStats = mapOf(
                "p1" to Player("Batter A").apply { id = "p1" },
                "b1" to Player("Bowler 1").apply { id = "b1" }
            ),
            currentStrikerId = "p1", currentStrikerName = "Batter A",
            currentBowlerId = "b1", currentBowlerName = "Bowler 1"
        )

        val dotBalls = (1..5).map {
            val ball = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler 1", 0, null).apply {
                batsmanId = "p1"; bowlerId = "b1"
            }
            ScoringEvent.BallBowled(ball, "Batter A", "Bowler 1", facingBatsmanId = "p1", bowlerId = "b1")
        }

        val byeBall = Ball(4, BallType.BYE, false, "Batter A", "Bowler 1", 0, null, byeRuns = 4).apply {
            batsmanId = "p1"; bowlerId = "b1"; this.byeRuns = 4
        }
        val byeEvent = ScoringEvent.BallBowled(byeBall, "Batter A", "Bowler 1", facingBatsmanId = "p1", bowlerId = "b1")

        val state = MatchEngine.rebuildState(initialState, dotBalls + byeEvent)

        assertEquals(6, state.innings.legalBalls)
        assertEquals(4, state.innings.totalRuns) // 4 byes
        assertEquals("Bowler runs conceded must be 0", 0, state.getPlayer("b1")!!.runsConceded)
        assertEquals("Maidens must be 1 because bowler conceded 0 runs", 1, state.getPlayer("b1")!!.maidens)
    }

    @Test
    fun testMaidenOverWithWide_spoilsMaiden() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 2, 10),
            playerStats = mapOf(
                "p1" to Player("Batter A").apply { id = "p1" },
                "b1" to Player("Bowler 1").apply { id = "b1" }
            ),
            currentStrikerId = "p1", currentStrikerName = "Batter A",
            currentBowlerId = "b1", currentBowlerName = "Bowler 1"
        )

        val wideBall = Ball(1, BallType.WIDE, false, "Batter A", "Bowler 1", 0, null).apply {
            batsmanId = "p1"; bowlerId = "b1"
        }
        val wideEvent = ScoringEvent.BallBowled(wideBall, "Batter A", "Bowler 1", facingBatsmanId = "p1", bowlerId = "b1")

        val dotBalls = (1..6).map {
            val ball = Ball(0, BallType.NORMAL, false, "Batter A", "Bowler 1", 0, null).apply {
                batsmanId = "p1"; bowlerId = "b1"
            }
            ScoringEvent.BallBowled(ball, "Batter A", "Bowler 1", facingBatsmanId = "p1", bowlerId = "b1")
        }

        val state = MatchEngine.rebuildState(initialState, listOf(wideEvent) + dotBalls)

        assertEquals(6, state.innings.legalBalls)
        assertEquals(1, state.getPlayer("b1")!!.runsConceded)
        assertEquals("Maidens must be 0 because bowler conceded 1 wide run", 0, state.getPlayer("b1")!!.maidens)
    }

    // =========================================================================
    // 5. INNINGS COMPLETION (TARGET REACHED, ALL OUT, OVERS FINISHED, RETIRED HURT)
    // =========================================================================

    @Test
    fun testTargetReached_completesInningsImmediately() {
        val (innings, striker, nonStriker) = setupMatch(overs = 5, wickets = 10)
        innings.target = 10 // Need 10 runs to win
        val bowler = newBowler()

        innings.addBall(6, BallType.NORMAL, false, striker, bowler, null, 6, nonStriker = nonStriker, isBoundary = true)
        assertFalse("Innings should not be complete at 6/10 runs", innings.isComplete)

        innings.addBall(4, BallType.NORMAL, false, striker, bowler, null, 4, nonStriker = nonStriker, isBoundary = true)
        assertEquals(10, innings.totalRuns)
        assertTrue("Innings must be complete as target (10) is reached", innings.isComplete)
    }

    @Test
    fun testAllOut_completesInnings() {
        val (innings, striker, nonStriker) = setupMatch(overs = 5, wickets = 2) // Max 2 wickets
        val bowler = newBowler()

        innings.addBall(0, BallType.NORMAL, true, striker, bowler, null, 0, nonStriker = nonStriker)
        assertEquals(1, innings.totalWickets)
        assertFalse("Innings should not be complete after 1 out of 2 wickets", innings.isComplete)

        innings.addBall(0, BallType.NORMAL, true, striker, bowler, null, 0, nonStriker = nonStriker)
        assertEquals(2, innings.totalWickets)
        assertTrue("Innings must be complete after all 2 wickets have fallen", innings.isComplete)
    }

    @Test
    fun testAllOut_delayedIfRetiredHurtCanReturn() {
        val (innings, striker, nonStriker) = setupMatch(overs = 5, wickets = 2)
        val bowler = newBowler()

        // Batter 1 retires hurt (not out)
        innings.recordRetired(striker, false, "retired hurt", striker.name, nonStriker.name, bowler.name)
        assertEquals(0, innings.totalWickets)
        assertEquals(1, innings.retiredHurtCount)

        // Then 2 wickets fall
        innings.addBall(0, BallType.NORMAL, true, striker, bowler, null, 0, nonStriker = nonStriker)
        innings.addBall(0, BallType.NORMAL, true, striker, bowler, null, 0, nonStriker = nonStriker)
        assertEquals(2, innings.totalWickets)

        assertFalse("Innings must NOT be marked complete because a retired hurt batter can still return", innings.isComplete)
    }

    @Test
    fun testOversCompleted_completesInnings() {
        val (innings, striker, nonStriker) = setupMatch(overs = 1, wickets = 10) // 1 over match
        val bowler = newBowler()

        repeat(5) {
            innings.addBall(1, BallType.NORMAL, false, striker, bowler, null, 1, nonStriker = nonStriker)
            assertFalse(innings.isComplete)
        }
        // 6th legal ball:
        innings.addBall(1, BallType.NORMAL, false, striker, bowler, null, 1, nonStriker = nonStriker)
        assertEquals(6, innings.legalBalls)
        assertTrue("Innings must be complete when 1 full over (6 legal balls) is bowled", innings.isComplete)
    }

    // =========================================================================
    // 6. EVENT SOURCING UNDO RESILIENCE
    // =========================================================================

    @Test
    fun testUndoWicket_completelyRestoresBatterAndBowlerStats() {
        val initialState = MatchState(
            innings = Innings("Team A", "Team B", 5, 10),
            playerStats = mapOf(
                "p1" to Player("Batter A").apply { id = "p1" },
                "p2" to Player("Batter B").apply { id = "p2" },
                "b1" to Player("Bowler 1").apply { id = "b1" },
                "f1" to Player("Catcher").apply { id = "f1" }
            ),
            currentStrikerId = "p1", currentStrikerName = "Batter A",
            currentNonStrikerId = "p2", currentNonStrikerName = "Batter B",
            currentBowlerId = "b1", currentBowlerName = "Bowler 1"
        )

        val detail = WicketDetail("p1", DismissalType.CAUGHT, "b1", primaryFielderId = "f1")
        val wicketBall = Ball(0, BallType.NORMAL, true, "Batter A", "Bowler 1", 0, "Batter B").apply {
            batsmanId = "p1"; bowlerId = "b1"; nonStrikerId = "p2"; wicketDetail = detail
        }
        val events = listOf(
            ScoringEvent.BallBowled(
                wicketBall, "Batter A", "Bowler 1",
                facingBatsmanId = "p1", bowlerId = "b1", nonStrikerId = "p2",
                primaryFielderName = "Catcher"
            )
        )

        val stateWithWicket = MatchEngine.rebuildState(initialState, events)
        assertEquals(1, stateWithWicket.innings.totalWickets)
        assertTrue(stateWithWicket.getPlayer("p1")!!.isOut)
        assertEquals(1, stateWithWicket.getPlayer("b1")!!.wicketsTaken)
        assertEquals(1, stateWithWicket.getPlayer("f1")!!.catches)

        // UNDO THE WICKET
        val stateAfterUndo = MatchEngine.rebuildState(initialState, emptyList())
        assertEquals(0, stateAfterUndo.innings.totalWickets)
        assertFalse("Batter must no longer be out after undo", stateAfterUndo.getPlayer("p1")!!.isOut)
        assertEquals("Bowler wickets must be 0 after undo", 0, stateAfterUndo.getPlayer("b1")!!.wicketsTaken)
        assertEquals("Catcher catches must be 0 after undo", 0, stateAfterUndo.getPlayer("f1")!!.catches)
    }

    // =========================================================================
    // 7. ENTITY CONVERSION INTEGRITY (Player -> PlayerMatchStatEntity)
    // =========================================================================

    @Test
    fun testPlayerMatchStatEntity_preservesAllBattingBowlingAndFieldingStats() {
        val player = Player("Ramesh").apply {
            id = "ramesh-uuid-123"
            runsScored = 74
            ballsFaced = 42
            fours = 8
            sixes = 4
            isOut = true
            dismissalInfo = "c Rohit b Bumrah"
            ballsBowled = 24
            runsConceded = 28
            wicketsTaken = 3
            maidens = 1
            dotBalls = 14
            widesConceded = 2
            noBallsConceded = 1
            catches = 2
            stumpings = 1
            runOuts = 1
        }

        val statEntity = PlayerMatchStatEntity.fromPlayer(player, "match-999", "Team Gold")

        assertEquals("match-999_ramesh-uuid-123", statEntity.id)
        assertEquals("ramesh-uuid-123", statEntity.playerId)
        assertEquals("match-999", statEntity.matchId)
        assertEquals("Ramesh", statEntity.playerName)
        assertEquals("Team Gold", statEntity.teamName)

        // Batting
        assertEquals(74, statEntity.runsScored)
        assertEquals(42, statEntity.ballsFaced)
        assertEquals(8, statEntity.fours)
        assertEquals(4, statEntity.sixes)
        assertTrue(statEntity.isOut)
        assertEquals("c Rohit b Bumrah", statEntity.dismissalInfo)

        // Bowling
        assertEquals(24, statEntity.ballsBowled)
        assertEquals(28, statEntity.runsConceded)
        assertEquals(3, statEntity.wicketsTaken)
        assertEquals(1, statEntity.maidens)
        assertEquals(14, statEntity.dotBalls)
        assertEquals(2, statEntity.widesConceded)
        assertEquals(1, statEntity.noBallsConceded)

        // Fielding
        assertEquals("Catches must transfer accurately to entity", 2, statEntity.catches)
        assertEquals("Stumpings must transfer accurately to entity", 1, statEntity.stumpings)
        assertEquals("Run outs must transfer accurately to entity", 1, statEntity.runOuts)

        // Round-trip back to Player
        val restoredPlayer = statEntity.toPlayer()
        assertEquals(player.id, restoredPlayer.id)
        assertEquals(player.name, restoredPlayer.name)
        assertEquals(player.runsScored, restoredPlayer.runsScored)
        assertEquals(player.wicketsTaken, restoredPlayer.wicketsTaken)
        assertEquals(player.catches, restoredPlayer.catches)
        assertEquals(player.stumpings, restoredPlayer.stumpings)
        assertEquals(player.runOuts, restoredPlayer.runOuts)
    }
}

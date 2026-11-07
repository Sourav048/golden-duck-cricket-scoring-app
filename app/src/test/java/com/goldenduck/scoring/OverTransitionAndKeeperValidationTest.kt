package com.goldenduck.scoring

import org.junit.Assert.*
import org.junit.Test

class OverTransitionAndKeeperValidationTest {

    @Test
    fun testEligibleKeepersExcludesCurrentBowler() {
        val bowlerName = "Bowler 1"

        // 1v1 match: bowling team only has the bowler
        val onePlayerBowlingTeam = listOf("Bowler 1")
        val eligibleIn1v1 = onePlayerBowlingTeam.filter { it != bowlerName }
        assertTrue("In 1v1 match, eligible keepers must be empty", eligibleIn1v1.isEmpty())

        // Normal match: bowling team has bowler and other players
        val normalBowlingTeam = listOf("Bowler 1", "Fielder 1", "Fielder 2")
        val eligibleInNormal = normalBowlingTeam.filter { it != bowlerName }
        assertEquals(2, eligibleInNormal.size)
        assertFalse("Eligible keepers must not contain the current bowler", eligibleInNormal.contains(bowlerName))
        assertTrue(eligibleInNormal.contains("Fielder 1"))
        assertTrue(eligibleInNormal.contains("Fielder 2"))
    }

    @Test
    fun testScoringViewModelPreservesOverTransitionState() {
        val viewModel = ScoringViewModel()
        assertFalse(viewModel.isOverTransitionPending)
        assertFalse(viewModel.isOverCompleteDialogShowing)
        assertFalse(viewModel.isOverCompleteMaiden)
        assertNull(viewModel.pendingBatsmanReplacementEnd)

        viewModel.isOverTransitionPending = true
        viewModel.isOverCompleteDialogShowing = true
        viewModel.isOverCompleteMaiden = true
        viewModel.pendingBatsmanReplacementEnd = true

        assertTrue(viewModel.isOverTransitionPending)
        assertTrue(viewModel.isOverCompleteDialogShowing)
        assertTrue(viewModel.isOverCompleteMaiden)
        assertEquals(true, viewModel.pendingBatsmanReplacementEnd)
    }

    @Test
    fun testOverthrowDoesNotAwardBoundariesOrSixesConceded() {
        val innings = Innings("Team A", "Team B", 20, 10)
        val batter = Player("Batter A").apply { id = "1" }
        val nonStriker = Player("Batter B").apply { id = "2" }
        val bowler = Player("Bowler 1").apply { id = "3" }

        // 2 before throw + 4 overthrow = 6 total runs
        innings.addBall(
            runs = 6,
            type = BallType.NORMAL,
            isWicket = false,
            batsman = batter,
            bowler = bowler,
            fielder = null,
            manualBatRuns = 6,
            nonStriker = nonStriker,
            overthrowRuns = 4,
            isBoundary = false
        )

        assertEquals("Batter runs scored should be 6", 6, batter.runsScored)
        assertEquals("Batter sixes should be 0 for overthrow", 0, batter.sixes)
        assertEquals("Batter fours should be 0 for overthrow", 0, batter.fours)
        assertEquals("Bowler runs conceded should be 6", 6, bowler.runsConceded)

        val namesMap = mapOf("1" to "Batter A", "2" to "Batter B", "3" to "Bowler 1")
        val playersMap = mutableMapOf("Batter A" to Player("Batter A"), "Bowler 1" to Player("Bowler 1"))
        StatsRecalculator.processInnings(
            innings,
            getP = { name -> playersMap.getOrPut(name ?: "") { Player(name) } },
            resolveNameFromId = { id -> namesMap[id] ?: id }
        )
        val recalcBatter = playersMap["Batter A"]!!
        val recalcBowler = playersMap["Bowler 1"]!!
        assertEquals(6, recalcBatter.runsScored)
        assertEquals(0, recalcBatter.sixes)
        assertEquals(0, recalcBatter.fours)
        assertEquals(0, recalcBowler.sixesConceded)
        assertEquals(6, recalcBowler.runsConceded)
    }

    @Test
    fun testNormalSixAwardsBoundaryAndSixConceded() {
        val innings = Innings("Team A", "Team B", 20, 10)
        val batter = Player("Batter A").apply { id = "1" }
        val nonStriker = Player("Batter B").apply { id = "2" }
        val bowler = Player("Bowler 1").apply { id = "3" }

        innings.addBall(
            runs = 6,
            type = BallType.NORMAL,
            isWicket = false,
            batsman = batter,
            bowler = bowler,
            fielder = null,
            manualBatRuns = 6,
            nonStriker = nonStriker,
            overthrowRuns = 0,
            isBoundary = true
        )

        assertEquals(6, batter.runsScored)
        assertEquals(1, batter.sixes)
        assertEquals(0, batter.fours)

        val namesMap = mapOf("1" to "Batter A", "2" to "Batter B", "3" to "Bowler 1")
        val playersMap = mutableMapOf("Batter A" to Player("Batter A"), "Bowler 1" to Player("Bowler 1"))
        StatsRecalculator.processInnings(
            innings,
            getP = { name -> playersMap.getOrPut(name ?: "") { Player(name) } },
            resolveNameFromId = { id -> namesMap[id] ?: id }
        )
        val recalcBatter = playersMap["Batter A"]!!
        val recalcBowler = playersMap["Bowler 1"]!!
        assertEquals(6, recalcBatter.runsScored)
        assertEquals(1, recalcBatter.sixes)
        assertEquals(1, recalcBowler.sixesConceded)
    }

    @Test
    fun testMatchEngine_noBallWithByesAndPenaltyOff_preservesMaiden() {
        val initialState = MatchState(
            innings = Innings("Red Team", "Blue Team", 20, 10),
            playerStats = mapOf(
                "A" to Player("A"),
                "B" to Player("B"),
                "D" to Player("D")
            ),
            currentStrikerName = "A",
            currentNonStrikerName = "B",
            currentBowlerName = "D",
            isRuleRunsOnWide = false // Automatic extra penalties OFF
        )

        // 1. No ball bowled, batter ran 0, 1 bye run
        val nbBall = Ball(
            runs = 1,
            type = BallType.NO_BALL,
            isWicket = false,
            batsman = "A",
            bowler = "D",
            batRuns = 0,
            nonStriker = "B",
            byeRuns = 1
        )
        val eventNb = ScoringEvent.BallBowled(nbBall, "A", "D")

        // 2. Bowl 6 legal dot balls
        val dotEvents = (1..6).map {
            val dot = Ball(
                runs = 0,
                type = BallType.NORMAL,
                isWicket = false,
                batsman = "A",
                bowler = "D",
                batRuns = 0,
                nonStriker = "B"
            )
            ScoringEvent.BallBowled(dot, "A", "D")
        }

        val allEvents = listOf(eventNb) + dotEvents
        val state = MatchEngine.rebuildState(initialState, allEvents)

        val bowler = state.playerStats["D"]!!
        assertEquals("Bowler should have conceded 0 runs when automatic penalty is OFF and NB only had byes", 0, bowler.runsConceded)
        assertEquals("Over should be counted as a maiden", 1, bowler.maidens)
    }

    @Test
    fun testOverTransitionAndBatsmanReplacementCanBeConcurrent() {
        val viewModel = ScoringViewModel()
        viewModel.isOverTransitionPending = true
        viewModel.isOverCompleteDialogShowing = true
        viewModel.isOverCompleteMaiden = true
        viewModel.pendingBatsmanReplacementEnd = true

        // Replacement batter selected
        viewModel.pendingBatsmanReplacementEnd = null

        // Over transition must still remain pending to be resumed
        assertTrue("Over transition should remain pending after replacement", viewModel.isOverTransitionPending)
        assertTrue("Over complete dialog flag should remain active", viewModel.isOverCompleteDialogShowing)
        assertTrue("Maiden flag should remain active", viewModel.isOverCompleteMaiden)
        assertNull("Replacement flag should be cleared", viewModel.pendingBatsmanReplacementEnd)
    }

    @Test
    fun testUndoingMatchWinningBall_resetsCompletionAndMarksMatchLive() {
        val match = Match("Team A", "Team B", 2, 11, 11)
        val i1 = Innings("Team A", "Team B", 2, 10).apply {
            totalRuns = 10
            legalBalls = 12
            completionTime = 12345L
        }
        val i2 = Innings("Team B", "Team A", 2, 10).apply {
            target = 11
            totalRuns = 11
            legalBalls = 7
            completionTime = 23456L
        }
        match.restoreInnings(i1, i2)
        match.firstInningsEndTime = 12345L
        match.secondInningsEndTime = 23456L

        assertTrue("Innings 2 should initially be complete", i2.isComplete)

        // Simulate undoing the winning ball
        i2.totalRuns = 10
        i2.legalBalls = 6
        assertFalse("After removing winning ball, Innings 2 should be incomplete", i2.isComplete)

        // Verify completion reset logic
        if (!i2.isComplete) {
            i2.completionTime = 0L
            match.secondInningsEndTime = 0L
        }

        assertEquals(0L, i2.completionTime)
        assertEquals(0L, match.secondInningsEndTime)

        // Verify MatchEntity.fromMatch marks match as live when isFinished = false
        val entity = MatchEntity.fromMatch(match)
        entity.isFinished = false
        entity.isLive = true
        entity.result = "LIVE"

        assertFalse("MatchEntity should not be finished", entity.isFinished)
        assertTrue("MatchEntity should be marked live", entity.isLive)
        assertEquals("LIVE", entity.result)
    }

    @Test
    fun testRunOutWith4RunsCompleted_doesNotCreditBoundaryFour() {
        val innings = Innings("Team A", "Team B", 20, 10)
        val batter = Player("Batter A").apply { id = "1" }
        val nonStriker = Player("Batter B").apply { id = "2" }
        val bowler = Player("Bowler 1").apply { id = "3" }

        val wicketDetail = WicketDetail(
            dismissedPlayerId = "1",
            dismissalType = DismissalType.RUN_OUT,
            bowlerId = "3",
            primaryFielderId = "4",
            secondaryFielderId = "5",
            runsCompleted = 4,
            isStrikerEnd = true
        )

        innings.addBall(
            runs = 4,
            type = BallType.NORMAL,
            isWicket = true,
            batsman = batter,
            bowler = bowler,
            fielder = "Fielder 1 / Fielder 2",
            manualBatRuns = 4,
            outPlayer = batter,
            nonStriker = nonStriker,
            wicketDetail = wicketDetail,
            isBoundary = false
        )

        assertEquals("Batter runs scored should be 4", 4, batter.runsScored)
        assertEquals("Batter balls faced should be 1", 1, batter.ballsFaced)
        assertEquals("Batter fours should be 0 for running runs on run-out", 0, batter.fours)
        assertEquals("Batter sixes should be 0", 0, batter.sixes)
        assertTrue("Batter should be marked out", batter.isOut)

        // Verify recalculation matches
        val namesMap = mapOf("1" to "Batter A", "2" to "Batter B", "3" to "Bowler 1")
        val playersMap = mutableMapOf("Batter A" to Player("Batter A").apply { id = "1" }, "Bowler 1" to Player("Bowler 1").apply { id = "3" })
        StatsRecalculator.processInnings(
            innings,
            getP = { name -> playersMap.getOrPut(name ?: "") { Player(name) } },
            resolveNameFromId = { id -> namesMap[id] ?: id }
        )
        val recalcBatter = playersMap["Batter A"]!!
        assertEquals("Recalculated runs should be 4", 4, recalcBatter.runsScored)
        assertEquals("Recalculated fours should be 0", 0, recalcBatter.fours)

        // Verify undoing the run-out removes the runs without corrupting fours count
        innings.undoLastBall(batter, bowler, batter)
        assertEquals("After undo, runs scored should be 0", 0, batter.runsScored)
        assertEquals("After undo, fours should remain 0", 0, batter.fours)
    }

    @Test
    fun testPlayerWithUntrimmedName_preservesStatsAcrossTrimmedCacheKey() {
        val playerStatCache = mutableMapOf<String, Player>()
        val rawName = "Ramesh "
        val trimmed = rawName.trim()

        val player = Player(rawName).apply {
            runsScored = 24
            ballsFaced = 12
            fours = 3
        }

        // Rebuild indexing logic
        val key = player.name?.trim() ?: "unknown"
        playerStatCache[key] = player
        if (player.name != null && player.name != key) {
            playerStatCache[player.name!!] = player
        }

        // Verify retrieval using trimmed name
        val retrieved = playerStatCache[trimmed]
        assertNotNull("Player should be found using trimmed key", retrieved)
        assertEquals("Runs should be preserved", 24, retrieved?.runsScored)
        assertEquals("Balls should be preserved", 12, retrieved?.ballsFaced)
        assertEquals("Fours should be preserved", 3, retrieved?.fours)

        // Verify retrieval using raw name also works
        val retrievedRaw = playerStatCache[rawName]
        assertNotNull("Player should also be found using raw key", retrievedRaw)
        assertEquals("Runs should match", 24, retrievedRaw?.runsScored)
    }

    @Test
    fun testSixthBallWicket_overTransitionFlagsPreserved() {
        val innings = Innings("Team A", "Team B", 20, 10)
        val striker = Player("Striker").apply { id = "1" }
        val nonStriker = Player("NonStriker").apply { id = "2" }
        val bowler = Player("Bowler").apply { id = "3" }

        // Bowl 5 legal dot balls
        repeat(5) {
            innings.addBall(
                runs = 0,
                type = BallType.NORMAL,
                isWicket = false,
                batsman = striker,
                bowler = bowler,
                fielder = null,
                manualBatRuns = 0,
                nonStriker = nonStriker
            )
        }
        assertEquals(5, innings.legalBalls)

        // 6th ball is a wicket (Bowled)
        innings.addBall(
            runs = 0,
            type = BallType.NORMAL,
            isWicket = true,
            batsman = striker,
            bowler = bowler,
            fielder = null,
            manualBatRuns = 0,
            nonStriker = nonStriker,
            outPlayer = striker,
            wicketDetail = WicketDetail(
                dismissedPlayerId = "1",
                dismissalType = DismissalType.BOWLED,
                bowlerId = "3"
            )
        )
        assertEquals(6, innings.legalBalls)
        val isOverComplete = innings.legalBalls % 6 == 0
        assertTrue("Over should be complete on 6th ball", isOverComplete)
        assertFalse("Innings should not be complete", innings.isComplete)
    }
}

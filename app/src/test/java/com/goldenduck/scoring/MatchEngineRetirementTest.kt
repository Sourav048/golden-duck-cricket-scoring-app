package com.goldenduck.scoring

import org.junit.Assert.*
import org.junit.Test

class MatchEngineRetirementTest {

    private fun initialState(): MatchState = MatchState(
        innings = Innings("Team A", "Team B", 20, 1),
        playerStats = mapOf(
            "batter-id" to Player("Batter").apply { id = "batter-id" },
            "partner-id" to Player("Partner").apply { id = "partner-id" },
            "bowler-id" to Player("Bowler").apply { id = "bowler-id" }
        ),
        currentStrikerName = "Batter",
        currentNonStrikerName = "Partner",
        currentBowlerName = "Bowler",
        currentStrikerId = "batter-id",
        currentNonStrikerId = "partner-id",
        currentBowlerId = "bowler-id"
    )

    private fun event(ball: Ball): ScoringEvent.BallBowled = ScoringEvent.BallBowled(
        ball = ball,
        facingBatsmanName = "Batter",
        bowlerName = "Bowler",
        outPlayerName = if (ball.isWicket || ball.type == BallType.PENALTY) "Batter" else null,
        facingBatsmanId = "batter-id",
        bowlerId = "bowler-id",
        nonStrikerId = "partner-id"
    )

    private fun retirement(typed: Boolean): Ball = Ball(
        0, BallType.PENALTY, false, "Batter", "Bowler", 0, "Partner"
    ).apply {
        batsmanId = "batter-id"
        bowlerId = "bowler-id"
        nonStrikerId = "partner-id"
        outPlayerName = "Batter"
        dismissalInfo = "Retired Hurt"
        if (typed) wicketDetail = WicketDetail("batter-id", DismissalType.RETIRED_HURT)
    }

    private fun dotBall(): Ball = Ball(
        0, BallType.NORMAL, false, "Batter", "Bowler", 0, "Partner"
    ).apply {
        batsmanId = "batter-id"
        bowlerId = "bowler-id"
        nonStrikerId = "partner-id"
    }

    private fun bowled(): Ball = Ball(
        0, BallType.NORMAL, true, "Batter", "Bowler", 0, "Partner"
    ).apply {
        batsmanId = "batter-id"
        bowlerId = "bowler-id"
        nonStrikerId = "partner-id"
        outPlayerName = "Batter"
        wicketDetail = WicketDetail("batter-id", DismissalType.BOWLED, "bowler-id")
    }

    @Test
    fun typedRetiredHurt_returnsWithoutExtraBallAndLaterWicketCompletesInnings() {
        assertRetirementAndReturn(typed = true)
    }

    @Test
    fun legacyRetiredHurtPenaltyReplay_returnsAndLaterWicketCompletesInnings() {
        assertRetirementAndReturn(typed = false)
    }

    private fun assertRetirementAndReturn(typed: Boolean) {
        val initial = initialState()
        val retireEvent = event(retirement(typed))
        val retired = MatchEngine.reduce(initial, retireEvent)

        assertEquals(1, retired.innings.retiredHurtCount)
        assertEquals(0, retired.innings.totalWickets)
        assertEquals(0, retired.innings.legalBalls)
        assertEquals(0, retired.getPlayer("batter-id")!!.ballsFaced)
        assertEquals(0, retired.getPlayer("bowler-id")!!.ballsBowled)
        assertEquals(0, retired.getPlayer("bowler-id")!!.wicketsTaken)
        assertFalse(retired.getPlayer("batter-id")!!.isOut)
        assertNull(retired.currentStrikerId)

        val returnEvent = event(dotBall())
        val returned = MatchEngine.reduce(retired, returnEvent)

        assertEquals(0, returned.innings.retiredHurtCount)
        assertEquals("not out", returned.getPlayer("batter-id")!!.dismissalInfo)
        assertFalse(returned.getPlayer("batter-id")!!.isOut)
        assertEquals(1, returned.getPlayer("batter-id")!!.ballsFaced)
        assertEquals(1, returned.innings.legalBalls)
        assertEquals("batter-id", returned.currentStrikerId)

        val wicketEvent = event(bowled())
        val dismissed = MatchEngine.reduce(returned, wicketEvent)

        assertEquals(0, dismissed.innings.retiredHurtCount)
        assertEquals(1, dismissed.innings.totalWickets)
        assertEquals(1, dismissed.getPlayer("bowler-id")!!.wicketsTaken)
        assertTrue(dismissed.getPlayer("batter-id")!!.isOut)
        assertTrue(dismissed.innings.isComplete)

        val replayed = MatchEngine.rebuildState(initial, listOf(retireEvent, returnEvent, wicketEvent))
        assertEquals(0, replayed.innings.retiredHurtCount)
        assertEquals(1, replayed.innings.totalWickets)
        assertEquals(2, replayed.innings.legalBalls)
        assertEquals(2, replayed.getPlayer("batter-id")!!.ballsFaced)
        assertTrue(replayed.innings.isComplete)
        replayed.innings.recalculateState()
        assertEquals(0, replayed.innings.retiredHurtCount)
        assertTrue(replayed.innings.isComplete)
        replayed.innings.rebuildPartnerships()
        assertEquals(0, replayed.innings.retiredHurtCount)
        assertTrue(replayed.innings.isComplete)

        val undoDismissal = MatchEngine.rebuildState(initial, listOf(retireEvent, returnEvent))
        assertEquals(0, undoDismissal.innings.retiredHurtCount)
        assertEquals("not out", undoDismissal.getPlayer("batter-id")!!.dismissalInfo)
        assertFalse(undoDismissal.getPlayer("batter-id")!!.isOut)
        assertEquals(0, undoDismissal.getPlayer("bowler-id")!!.wicketsTaken)
    }

    @Test
    fun retiredBatterDismissedOnFirstBallBack_clearsOutstandingRetirement() {
        listOf(true, false).forEach { typed ->
            val initial = initialState()
            val events = listOf(event(retirement(typed)), event(bowled()))

            val dismissed = MatchEngine.rebuildState(initial, events)

            assertEquals(0, dismissed.innings.retiredHurtCount)
            assertEquals(1, dismissed.innings.totalWickets)
            assertEquals(1, dismissed.innings.legalBalls)
            assertEquals(1, dismissed.getPlayer("batter-id")!!.ballsFaced)
            assertEquals("b Bowler", dismissed.getPlayer("batter-id")!!.dismissalInfo)
            assertTrue(dismissed.innings.isComplete)
            dismissed.innings.recalculateState()
            assertEquals(0, dismissed.innings.retiredHurtCount)
            assertTrue(dismissed.innings.isComplete)
        }
    }

    @Test
    fun retiredBatterReturningAsNonStriker_clearsOutstandingRetirement() {
        listOf(true, false).forEach { typed ->
            val retired = MatchEngine.reduce(initialState(), event(retirement(typed)))
            val ball = Ball(0, BallType.NORMAL, false, "Partner", "Bowler", 0, "Batter").apply {
                batsmanId = "partner-id"
                nonStrikerId = "batter-id"
                bowlerId = "bowler-id"
            }
            val returned = MatchEngine.reduce(retired, ScoringEvent.BallBowled(
                ball = ball,
                facingBatsmanName = "Partner",
                bowlerName = "Bowler",
                facingBatsmanId = "partner-id",
                bowlerId = "bowler-id",
                nonStrikerId = "batter-id"
            ))

            assertEquals(0, returned.innings.retiredHurtCount)
            assertEquals("not out", returned.getPlayer("batter-id")!!.dismissalInfo)
            assertEquals(0, returned.getPlayer("batter-id")!!.ballsFaced)
            assertEquals("batter-id", returned.currentNonStrikerId)
            returned.innings.recalculateState()
            assertEquals(0, returned.innings.retiredHurtCount)
            returned.innings.rebuildPartnerships()
            assertEquals(0, returned.innings.retiredHurtCount)
        }
    }

    @Test
    fun playerReturned_acceptsCapitalizationAndWhitespaceWithoutNegativeCount() {
        val innings = Innings("Team A", "Team B", 20, 1).apply { retiredHurtCount = 1 }
        val batter = Player("Batter").apply { dismissalInfo = "  Retired Hurt  " }

        innings.playerReturned(batter)
        innings.playerReturned(batter)

        assertEquals(0, innings.retiredHurtCount)
    }
}

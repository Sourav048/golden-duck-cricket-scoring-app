package com.goldenduck.scoring

import org.junit.Assert.*
import org.junit.Test

class MatchEngineLegacyIdentityTest {
    private fun initialState(): MatchState {
        val names = mapOf(
            "bat-1" to "Striker",
            "bat-2" to "Partner",
            "bowl-1" to "Bowler",
            "field-1" to "Catcher",
            "field-2" to "Keeper"
        )
        return MatchState(
            innings = Innings("A", "B", 20, 10),
            playerStats = names.mapValues { (id, name) -> Player(name).apply { this.id = id } },
            currentStrikerId = "bat-1", currentStrikerName = "Striker",
            currentNonStrikerId = "bat-2", currentNonStrikerName = "Partner",
            currentBowlerId = "bowl-1", currentBowlerName = "Bowler"
        )
    }

    private fun legacyEvent(ball: Ball): ScoringEvent.BallBowled =
        ScoringEvent.BallBowled(
            ball = ball,
            facingBatsmanName = ball.batsmanName,
            bowlerName = ball.bowlerName,
            outPlayerName = ball.outPlayerName,
            fielderName = ball.fielderName,
            // Activity replay historically supplies the player's name in this fallback field.
            outPlayerId = ball.outPlayerName
        )

    private fun assertSameRoster(initial: MatchState, result: MatchState) {
        assertEquals("Replay must not create name-keyed duplicates", initial.playerStats.keys, result.playerStats.keys)
        initial.playerStats.forEach { (id, player) ->
            assertEquals(id, result.playerStats[id]!!.id)
            assertEquals(player.name, result.playerStats[id]!!.name)
        }
    }

    @Test
    fun nameOnlyLegacyDeliveryReusesUuidRoster() {
        val initial = initialState()
        val ball = Ball(2, BallType.NORMAL, false, "Striker", "Bowler", 2, "Partner")

        val result = MatchEngine.reduce(initial, legacyEvent(ball))

        assertSameRoster(initial, result)
        assertEquals(2, result.getPlayer("bat-1")!!.runsScored)
        assertEquals(1, result.getPlayer("bat-1")!!.ballsFaced)
        assertEquals(0, result.getPlayer("bat-2")!!.runsScored)
        assertEquals(2, result.getPlayer("bowl-1")!!.runsConceded)
        assertEquals(1, result.getPlayer("bowl-1")!!.ballsBowled)
        assertEquals("bat-1", result.currentStrikerId)
        assertEquals("bat-2", result.currentNonStrikerId)
        assertEquals("bowl-1", result.currentBowlerId)
        assertEquals("bat-1", result.innings.balls.single().batsmanId)
        assertEquals("bat-2", result.innings.balls.single().nonStrikerId)
        assertEquals("bowl-1", result.innings.balls.single().bowlerId)
    }

    @Test
    fun legacyAndTypedCatchesAccumulateOnTheSameFielder() {
        val initial = initialState()
        val legacyCatch = Ball(0, BallType.NORMAL, true, "Striker", "Bowler", 0, "Partner").apply {
            outPlayerName = "Striker"
            fielderName = "Catcher"
            dismissalInfo = "c Catcher b Bowler"
        }
        val typedCatch = Ball(0, BallType.NORMAL, true, "Partner", "Bowler", 0).apply {
            batsmanId = "bat-2"
            bowlerId = "bowl-1"
            outPlayerName = "Partner"
            wicketDetail = WicketDetail("bat-2", DismissalType.CAUGHT, "bowl-1", "field-1")
        }

        val result = MatchEngine.rebuildState(
            initial,
            listOf(
                legacyEvent(legacyCatch),
                ScoringEvent.BallBowled(
                    ball = typedCatch,
                    facingBatsmanName = "Partner",
                    bowlerName = "Bowler",
                    facingBatsmanId = "bat-2",
                    bowlerId = "bowl-1"
                )
            )
        )

        assertSameRoster(initial, result)
        assertEquals(2, result.getPlayer("field-1")!!.catches)
        assertEquals(0, result.getPlayer("field-2")!!.catches)
        assertEquals(2, result.getPlayer("bowl-1")!!.wicketsTaken)
        assertTrue(result.getPlayer("bat-1")!!.isOut)
        assertTrue(result.getPlayer("bat-2")!!.isOut)
        assertEquals(2, result.innings.totalWickets)
    }

    @Test
    fun legacyNonStrikerRunOutResolvesBatterAndBothFieldersByName() {
        val initial = initialState()
        val ball = Ball(0, BallType.NORMAL, true, "Striker", "Bowler", 0, "Partner").apply {
            outPlayerName = "Partner"
            fielderName = "Catcher / Keeper"
            dismissalInfo = "Run Out (Catcher / Keeper)"
        }

        val result = MatchEngine.reduce(initial, legacyEvent(ball))

        assertSameRoster(initial, result)
        assertFalse(result.getPlayer("bat-1")!!.isOut)
        assertTrue(result.getPlayer("bat-2")!!.isOut)
        assertEquals("bat-1", result.currentStrikerId)
        assertNull(result.currentNonStrikerId)
        assertEquals(0, result.getPlayer("bowl-1")!!.wicketsTaken)
        assertEquals(1, result.getPlayer("field-1")!!.runOuts)
        assertEquals(1, result.getPlayer("field-2")!!.runOuts)
        assertEquals(1, result.innings.totalWickets)
        assertEquals("Partner", result.innings.fallOfWickets.single()!!.playerName)
        assertEquals("Partner", result.innings.balls.single().outPlayerName)
    }
}

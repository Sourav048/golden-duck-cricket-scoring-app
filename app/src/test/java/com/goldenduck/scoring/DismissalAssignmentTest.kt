package com.goldenduck.scoring

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DismissalAssignmentTest(private val type: DismissalType) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun dismissals(): Collection<Array<Any>> =
            DismissalType.entries.map { arrayOf<Any>(it) }
    }

    private fun initialState(): MatchState {
        val players = mapOf(
            "bat-1" to "Striker",
            "bat-2" to "Non-striker",
            "bowl-1" to "Bowler",
            "bowl-2" to "Other bowler",
            "field-1" to "Catcher",
            "field-2" to "Keeper"
        ).mapValues { (id, name) -> Player(name).apply { this.id = id } }
        return MatchState(
            innings = Innings("A", "B", 20, 10),
            playerStats = players,
            currentStrikerId = "bat-1", currentStrikerName = "Striker",
            currentNonStrikerId = "bat-2", currentNonStrikerName = "Non-striker",
            currentBowlerId = "bowl-1", currentBowlerName = "Bowler"
        )
    }

    private fun event(
        outId: String = "bat-1",
        primary: String? = "field-1",
        secondary: String? = "field-2",
        delivery: BallType = BallType.NORMAL,
        isStrikerEnd: Boolean = (outId == "bat-1")
    ): ScoringEvent.BallBowled {
        val retirement = type == DismissalType.RETIRED_HURT || type == DismissalType.RETIRED_OUT
        val ball = Ball(
            if (delivery == BallType.WIDE || delivery == BallType.NO_BALL) 1 else 0,
            if (retirement) BallType.PENALTY else delivery,
            type != DismissalType.RETIRED_HURT,
            "Striker", "Bowler", 0, "Non-striker"
        ).apply {
            batsmanId = "bat-1"
            bowlerId = "bowl-1"
            nonStrikerId = "bat-2"
            // Deliberately stale display fields: typed identity must win.
            outPlayerName = "Striker"
            dismissalInfo = "c Wrong player b Other bowler"
            fielderName = "Wrong player / Wrong player"
            wicketDetail = WicketDetail(outId, this@DismissalAssignmentTest.type, "bowl-1", primary, secondary, isStrikerEnd = isStrikerEnd)
        }
        return ScoringEvent.BallBowled(
            ball, "Striker", "Bowler",
            isStrikerEnd = isStrikerEnd,
            facingBatsmanId = "bat-1", bowlerId = "bowl-1",
            nonStrikerId = "bat-2"
        )
    }

    @Test fun assignsOnlyTheExpectedPlayerCredits() {
        val initial = initialState()
        val result = MatchEngine.reduce(initial, event())
        val expectedBowlerWicket = type in setOf(
            DismissalType.BOWLED, DismissalType.CAUGHT, DismissalType.LBW,
            DismissalType.STUMPED, DismissalType.HIT_WICKET, DismissalType.HIT_OUT_OF_GROUND
        )
        assertEquals(if (expectedBowlerWicket) 1 else 0, result.getPlayer("bowl-1")!!.wicketsTaken)
        assertEquals(0, result.getPlayer("bowl-2")!!.wicketsTaken)
        assertEquals(type != DismissalType.RETIRED_HURT, result.getPlayer("bat-1")!!.isOut)
        assertFalse(result.getPlayer("bat-2")!!.isOut)
        assertEquals(if (type == DismissalType.RETIRED_HURT) 0 else 1, result.innings.totalWickets)
        result.playerStats.forEach { (id, player) ->
            assertEquals("$type catch assigned to $id", if (type == DismissalType.CAUGHT && id == "field-1") 1 else 0, player.catches)
            assertEquals("$type stumping assigned to $id", if (type == DismissalType.STUMPED && id == "field-1") 1 else 0, player.stumpings)
            assertEquals("$type run-out assigned to $id", if (type == DismissalType.RUN_OUT && id in setOf("field-1", "field-2")) 1 else 0, player.runOuts)
        }
        assertEquals(initial.playerStats.keys, result.playerStats.keys)
        assertTrue(initial.playerStats.values.all { !it.isOut && it.wicketsTaken == 0 && it.catches == 0 })
    }

    @Test fun typedDismissedIdWinsForNonStrikerAndReplay() {
        val initial = initialState()
        val dismissal = event(outId = "bat-2")
        val result = MatchEngine.rebuildState(initial, listOf(dismissal))
        assertFalse("Facing batter must survive $type of non-striker", result.getPlayer("bat-1")!!.isOut)
        assertEquals(type != DismissalType.RETIRED_HURT, result.getPlayer("bat-2")!!.isOut)
        assertEquals("bat-1", result.currentStrikerId)
        assertNull(result.currentNonStrikerId)
        assertEquals("bat-2", result.innings.balls.single().wicketDetail!!.dismissedPlayerId)
        assertEquals("Non-striker", result.innings.balls.single().outPlayerName)
        val undone = MatchEngine.rebuildState(initial, emptyList())
        assertTrue(undone.playerStats.values.all { !it.isOut && it.wicketsTaken == 0 && it.catches == 0 && it.stumpings == 0 && it.runOuts == 0 })
        assertEquals(0, undone.innings.totalWickets)
    }

    @Test fun sameRunOutFielderIsCreditedOnlyOnce() {
        val result = MatchEngine.reduce(initialState(), event(primary = "field-1", secondary = "field-1"))
        assertEquals(if (type == DismissalType.RUN_OUT) 1 else 0, result.getPlayer("field-1")!!.runOuts)
    }

    @Test fun typedBowlerIdentityWinsOverStaleEvent() {
        val result = MatchEngine.reduce(initialState(), event().copy(bowlerId = "bowl-2", bowlerName = "Other bowler"))
        assertEquals(if (type.isBowlerWicket) 1 else 0, result.getPlayer("bowl-1")!!.wicketsTaken)
        assertEquals(0, result.getPlayer("bowl-2")!!.wicketsTaken)
    }
}

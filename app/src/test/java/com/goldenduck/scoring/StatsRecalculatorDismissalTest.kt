package com.goldenduck.scoring

import org.junit.Assert.*
import org.junit.Test

class StatsRecalculatorDismissalTest {
    private class Fixture {
        val names = mapOf(
            "striker-id" to "Facing batter",
            "out-id" to "Dismissed batter",
            "bowler-id" to "Bowler",
            "fielder-id" to "Fielder",
            "assistant-id" to "Assistant"
        )
        val players = names.map { (id, name) -> name to Player(name).apply { this.id = id } }.toMap().toMutableMap()

        fun process(vararg balls: Ball) {
            val innings = Innings("Batting", "Bowling", 20, 10)
            innings.balls.addAll(balls)
            StatsRecalculator.processInnings(
                innings,
                getP = { name -> players.getOrPut(name ?: "Unknown") { Player(name) } },
                resolveNameFromId = { id -> names[id] }
            )
        }

        operator fun get(name: String): Player = players.getValue(name)
    }

    private fun dismissal(
        type: DismissalType,
        primary: String? = "fielder-id",
        secondary: String? = "assistant-id",
        ballType: BallType = if (type == DismissalType.RETIRED_HURT || type == DismissalType.RETIRED_OUT) BallType.PENALTY else BallType.NORMAL
    ): Ball = Ball(0, ballType, type != DismissalType.RETIRED_HURT, "Facing batter", "Wrong bowler", 0).apply {
        batsmanId = "striker-id"
        bowlerId = "wrong-bowler-id"
        outPlayerName = "Facing batter"
        fielderName = "Wrong fielder / Wrong assistant"
        dismissalInfo = "c Wrong fielder b Wrong bowler"
        wicketDetail = WicketDetail("out-id", type, "bowler-id", primary, secondary)
    }

    @Test
    fun everyEnumCreditsOnlyItsDismissedBatterBowlerAndFielders() {
        // Explicit expectations ensure changing an enum flag cannot silently change the test oracle.
        val bowlerWickets = setOf(
            DismissalType.BOWLED, DismissalType.CAUGHT, DismissalType.LBW,
            DismissalType.STUMPED, DismissalType.HIT_WICKET, DismissalType.HIT_OUT_OF_GROUND
        )
        DismissalType.entries.forEach { type ->
            val f = Fixture()
            f.process(dismissal(type))
            assertEquals(type.name, type != DismissalType.RETIRED_HURT, f["Dismissed batter"].isOut)
            assertFalse(type.name, f["Facing batter"].isOut)
            assertEquals(type.name, if (type in bowlerWickets) 1 else 0, f["Bowler"].wicketsTaken)
            assertEquals(type.name, if (type == DismissalType.CAUGHT) 1 else 0, f["Fielder"].catches)
            assertEquals(type.name, if (type == DismissalType.STUMPED) 1 else 0, f["Fielder"].stumpings)
            assertEquals(type.name, if (type == DismissalType.RUN_OUT) 1 else 0, f["Fielder"].runOuts)
            assertEquals(type.name, if (type == DismissalType.RUN_OUT) 1 else 0, f["Assistant"].runOuts)
            assertEquals(type.name, 0, f["Assistant"].catches + f["Assistant"].stumpings)
            assertFalse(type.name, f.players.containsKey("Wrong bowler"))
            assertFalse(type.name, f.players.containsKey("Wrong fielder"))
            assertFalse(type.name, f.players.containsKey("Wrong assistant"))
        }
    }

    @Test
    fun caughtAndBowledAwardsBothCreditsToTheBowler() {
        val f = Fixture()
        f.process(dismissal(DismissalType.CAUGHT, primary = "bowler-id", secondary = null))
        assertEquals(1, f["Bowler"].wicketsTaken)
        assertEquals(1, f["Bowler"].catches)
        assertEquals("c & b Bowler", f["Dismissed batter"].dismissalInfo)
        assertEquals(0, f["Fielder"].catches)
    }

    @Test
    fun breakerOnlyRunOutIsCreditedOnceDespiteLegacyFielderName() {
        val f = Fixture()
        val ball = dismissal(DismissalType.RUN_OUT, primary = null).apply {
            fielderName = "Assistant"
        }
        f.process(ball)
        assertEquals(1, f["Assistant"].runOuts)
        assertEquals("Run Out (Assistant)", f["Dismissed batter"].dismissalInfo)
        assertEquals(0, f["Fielder"].runOuts)
        assertEquals(0, f["Bowler"].wicketsTaken)
    }

    @Test
    fun sameRunOutFielderInBothRolesIsCreditedOnce() {
        val f = Fixture()
        f.process(dismissal(DismissalType.RUN_OUT, secondary = "fielder-id"))
        assertEquals(1, f["Fielder"].runOuts)
    }

    @Test
    fun typedDismissalWithNoFieldersDoesNotParseDisplayNames() {
        val f = Fixture()
        f.process(dismissal(DismissalType.RUN_OUT, primary = null, secondary = null))
        assertEquals(0, f.players.values.sumOf { it.runOuts })
        assertEquals(0, f.players.values.sumOf { it.catches })
        assertFalse(f.players.containsKey("Wrong fielder"))
    }

    @Test
    fun retirementRecordsDoNotAddDeliveriesAndPreserveHurtVersusOut() {
        listOf(DismissalType.RETIRED_HURT, DismissalType.RETIRED_OUT).forEach { type ->
            val f = Fixture()
            f.process(dismissal(type))
            assertEquals(type.name, 0, f.players.values.sumOf { it.ballsFaced })
            assertEquals(type.name, 0, f.players.values.sumOf { it.ballsBowled })
            assertEquals(type.name, 0, f.players.values.sumOf { it.wicketsTaken })
            assertEquals(type.name, if (type == DismissalType.RETIRED_HURT) "retired hurt" else "retired out", f["Dismissed batter"].dismissalInfo)
        }
    }

    @Test
    fun hattrickUsesEnumAndBowlerIdDespiteConflictingDisplayText() {
        val f = Fixture()
        val balls = listOf(DismissalType.BOWLED, DismissalType.CAUGHT, DismissalType.LBW).map { type ->
            dismissal(type).apply { dismissalInfo = "Run Out"; bowlerName = "Wrong bowler" }
        }
        f.process(*balls.toTypedArray())
        assertEquals(3, f["Bowler"].wicketsTaken)
        assertEquals(1, f["Bowler"].hattricks)
    }

    @Test
    fun nonBowlerDismissalsOnDeliveriesBreakHattrickStreak() {
        listOf(DismissalType.RUN_OUT, DismissalType.OBSTRUCTING, DismissalType.OTHER).forEach { type ->
            val f = Fixture()
            val middle = dismissal(type).apply { dismissalInfo = "b Bowler" }
            f.process(dismissal(DismissalType.BOWLED), middle, dismissal(DismissalType.BOWLED))
            assertEquals(type.name, 2, f["Bowler"].wicketsTaken)
            assertEquals(type.name, 0, f["Bowler"].hattricks)
        }
    }

    @Test
    fun retirementBetweenDeliveriesDoesNotBreakBowlerHattrick() {
        val f = Fixture()
        f.process(
            dismissal(DismissalType.BOWLED),
            dismissal(DismissalType.RETIRED_HURT),
            dismissal(DismissalType.CAUGHT),
            dismissal(DismissalType.RETIRED_OUT),
            dismissal(DismissalType.LBW)
        )
        assertEquals(1, f["Bowler"].hattricks)
        assertEquals(3, f["Facing batter"].ballsFaced)
        assertEquals(3, f["Bowler"].ballsBowled)
    }

    @Test
    fun legacyDismissalsStillCreditFieldersWithoutTypedDetails() {
        val f = Fixture()
        val caught = Ball(0, BallType.NORMAL, true, "Facing batter", "Bowler", 0).apply {
            outPlayerName = "Dismissed batter"
            dismissalInfo = "c Fielder b Bowler"
            fielderName = "Fielder"
        }
        val runOut = Ball(0, BallType.NORMAL, true, "Facing batter", "Bowler", 0).apply {
            outPlayerName = "Dismissed batter"
            dismissalInfo = "Run Out"
            fielderName = "Fielder / Assistant"
        }
        f.process(caught, runOut)
        assertEquals(1, f["Bowler"].wicketsTaken)
        assertEquals(1, f["Fielder"].catches)
        assertEquals(1, f["Fielder"].runOuts)
        assertEquals(1, f["Assistant"].runOuts)
    }

    @Test
    fun returnedRetiredHurtBatterIsNotOutAtEitherEnd() {
        listOf(true, false).forEach { returnsAsStriker ->
            val f = Fixture()
            val nextBall = Ball(0, BallType.NORMAL, false, "Facing batter", "Bowler", 0).apply {
                if (returnsAsStriker) {
                    batsmanId = "out-id"
                } else {
                    batsmanId = "striker-id"
                    nonStrikerId = "out-id"
                    nonStrikerName = "Wrong old name"
                }
            }
            f.process(dismissal(DismissalType.RETIRED_HURT), nextBall)
            assertEquals("not out", f["Dismissed batter"].dismissalInfo)
            assertFalse(f["Dismissed batter"].isOut)
            assertEquals(if (returnsAsStriker) 1 else 0, f["Dismissed batter"].ballsFaced)
            assertFalse(f.players.containsKey("Wrong old name"))
        }
    }

    @Test
    fun legacyRetiredHurtRemainsNotOutWithoutAddingABallFaced() {
        val f = Fixture()
        val retired = Ball(0, BallType.PENALTY, false, "Facing batter", "Bowler", 0).apply {
            outPlayerName = "Dismissed batter"
            dismissalInfo = "retired hurt"
        }
        f.process(retired)
        assertFalse(f["Dismissed batter"].isOut)
        assertEquals("retired hurt", f["Dismissed batter"].dismissalInfo)
        assertEquals(0, f["Facing batter"].ballsFaced)
        assertEquals(0, f["Bowler"].wicketsTaken)
    }

    @Test
    fun ordinaryPenaltyDoesNotCreateFakePenaltyOrFieldPlayers() {
        val f = Fixture()
        val penalty = Ball(5, BallType.PENALTY, false, "PENALTY", "FIELD", 0)
        f.process(penalty)

        assertFalse("Must not create player named PENALTY", f.players.containsKey("PENALTY"))
        assertFalse("Must not create player named FIELD", f.players.containsKey("FIELD"))
    }
}

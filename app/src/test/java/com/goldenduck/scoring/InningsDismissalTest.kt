package com.goldenduck.scoring

import org.junit.Assert.*
import org.junit.Test

class InningsDismissalTest {

    @Test
    fun rebuildPartnerships_preservesEveryTypedDismissalAndOriginalBallMetadata() {
        DismissalType.entries.forEach { dismissal ->
            val innings = Innings("Team A", "Team B", 20, 10)
            val retirement = dismissal == DismissalType.RETIRED_HURT || dismissal == DismissalType.RETIRED_OUT
            val detail = WicketDetail(
                dismissedPlayerId = "batter-2-id",
                dismissalType = dismissal,
                bowlerId = "bowler-id",
                primaryFielderId = "fielder-1-id",
                secondaryFielderId = "fielder-2-id",
                runsCompleted = 2,
                isStrikerEnd = false
            )
            val ball = Ball(
                runs = if (retirement) 0 else 2,
                type = if (retirement) BallType.PENALTY else BallType.NORMAL,
                isWicket = dismissal != DismissalType.RETIRED_HURT,
                batsman = "Batter One",
                bowler = "Bowler",
                batRuns = if (retirement) 0 else 2,
                nonStriker = "Batter Two"
            ).apply {
                batsmanId = "batter-1-id"
                nonStrikerId = "batter-2-id"
                bowlerId = "bowler-id"
                outPlayerName = "Batter Two"
                fielderName = "Fielder One / Fielder Two"
                dismissalInfo = "Original display text for $dismissal"
                wicketDetail = detail
            }
            innings.balls.add(ball)

            innings.rebuildPartnerships()

            assertSame("$dismissal must preserve the persisted ball", ball, innings.balls.single())
            assertSame(detail, innings.balls.single().wicketDetail)
            assertEquals("Original display text for $dismissal", innings.balls.single().dismissalInfo)
            assertEquals("batter-1-id", innings.balls.single().batsmanId)
            assertEquals("batter-2-id", innings.balls.single().nonStrikerId)
            assertEquals("bowler-id", innings.balls.single().bowlerId)
            assertEquals(if (dismissal == DismissalType.RETIRED_HURT) 0 else 1, innings.totalWickets)
            assertEquals(if (retirement) 0 else 1, innings.legalBalls)
            if (dismissal == DismissalType.RETIRED_HURT) {
                assertEquals(1, innings.retiredHurtCount)
                assertTrue(innings.fallOfWickets.isEmpty())
            } else {
                assertEquals("Batter Two", innings.fallOfWickets.single()!!.playerName)
            }
        }
    }

    @Test
    fun rebuildPartnerships_preservesLegacyDismissalTextAndFielders() {
        val innings = Innings("Team A", "Team B", 20, 10)
        val ball = Ball(1, BallType.NO_BALL, true, "Striker", "Bowler", 0, "Partner").apply {
            outPlayerName = "Partner"
            fielderName = "Thrower / Breaker"
            dismissalInfo = "Run Out (Thrower/Breaker)"
        }
        innings.balls.add(ball)

        innings.rebuildPartnerships()

        assertSame(ball, innings.balls.single())
        assertNull(innings.balls.single().wicketDetail)
        assertEquals("Run Out (Thrower/Breaker)", innings.balls.single().dismissalInfo)
        assertEquals("Thrower / Breaker", innings.balls.single().fielderName)
        assertEquals("Partner", innings.fallOfWickets.single()!!.playerName)
        assertEquals(1, innings.noBalls)
        assertEquals(0, innings.legalBalls)
    }

    @Test
    fun rebuildPartnerships_usesTypedDismissedIdForFallOfWicket() {
        val innings = Innings("Team A", "Team B", 20, 10)
        innings.balls.add(Ball(0, BallType.NORMAL, true, "Striker", "Bowler", 0, "Partner").apply {
            batsmanId = "striker-id"
            nonStrikerId = "partner-id"
            outPlayerName = "Striker"
            wicketDetail = WicketDetail("partner-id", DismissalType.RUN_OUT)
        })

        innings.rebuildPartnerships()

        assertEquals("Partner", innings.fallOfWickets.single()!!.playerName)
        assertEquals("Striker", innings.balls.single().outPlayerName)
    }

    @Test
    fun retirementRecords_areTypedAndDoNotConsumeDeliveryOrBowlerWicket() {
        listOf(true, false).forEach { hurt ->
            val innings = Innings("Team A", "Team B", 20, 10)
            val batter = Player("Partner").apply { id = "partner-id" }

            innings.recordRetired(batter, !hurt, if (hurt) "Retired Hurt" else "Retired Out", "Striker", "Partner", "Bowler")

            val ball = innings.balls.single()
            assertEquals("partner-id", ball.wicketDetail!!.dismissedPlayerId)
            assertEquals(if (hurt) DismissalType.RETIRED_HURT else DismissalType.RETIRED_OUT, ball.wicketDetail!!.dismissalType)
            assertEquals("partner-id", ball.nonStrikerId)
            assertFalse(ball.wicketDetail!!.dismissalType.isBowlerWicket)
            assertEquals(!hurt, batter.isOut)
            assertEquals(0, batter.ballsFaced)
            assertEquals(0, innings.legalBalls)
            assertEquals(if (hurt) 0 else 1, innings.totalWickets)

            innings.rebuildPartnerships()

            assertSame(ball, innings.balls.single())
            assertEquals(if (hurt) 1 else 0, innings.retiredHurtCount)
            assertTrue(innings.partnerships.isEmpty())
        }
    }

    @Test
    fun undoNonBowlerDismissals_preservesPreviouslyEarnedBowlerWickets() {
        DismissalType.entries.filterNot { it.isBowlerWicket || it == DismissalType.RETIRED_HURT }.forEach { dismissal ->
            val innings = Innings("Team A", "Team B", 20, 10)
            val batter = Player("Batter").apply { id = "batter-id" }
            val bowler = Player("Bowler").apply { id = "bowler-id"; wicketsTaken = 2 }
            innings.addBall(
                0, BallType.NORMAL, true, batter, bowler, null, 0,
                wicketDetail = WicketDetail("batter-id", dismissal, "bowler-id")
            )

            innings.undoLastBall(batter, bowler)

            assertEquals("$dismissal must not remove an earlier bowler wicket", 2, bowler.wicketsTaken)
            assertEquals(0, innings.totalWickets)
            assertFalse(batter.isOut)
        }
    }

    @Test
    fun undoRetiredHurt_usesEnumWhenDisplayTextChanges() {
        val innings = Innings("Team A", "Team B", 20, 10)
        val batter = Player("Batter")
        val bowler = Player("Bowler").apply { wicketsTaken = 2 }
        innings.recordRetired(batter, false, "Translated retirement", "Batter", "Partner", "Bowler")

        innings.undoLastBall(batter, bowler)

        assertEquals(0, innings.retiredHurtCount)
        assertEquals(0, innings.legalBalls)
        assertEquals(2, bowler.wicketsTaken)
        assertEquals("not out", batter.dismissalInfo)
    }

    @Test
    fun rebuildPartnerships_withPenalty_doesNotIntroduceFakePlayers() {
        val innings = Innings("Team A", "Team B", 20, 10)
        val alice = Player("Alice").apply { id = "alice-id" }
        val bob = Player("Bob").apply { id = "bob-id" }
        val bowler = Player("Charlie").apply { id = "charlie-id" }

        innings.addBall(1, BallType.NORMAL, false, alice, bowler, null, 1, nonStriker = bob)
        innings.addPenalty(5, "Charlie")

        innings.rebuildPartnerships()

        // Verify total runs and penalties
        assertEquals(6, innings.totalRuns)
        assertEquals(5, innings.penalties)
        assertEquals(1, innings.legalBalls)

        // Partnerships must only involve Alice and Bob, NEVER fake player "Batsman" or "Bowler"
        assertTrue(innings.partnerships.isNotEmpty())
        for (pship in innings.partnerships) {
            assertNotEquals("Batsman", pship.batter1)
            assertNotEquals("Batsman", pship.batter2)
            assertNotEquals("Bowler", pship.batter1)
            assertNotEquals("Bowler", pship.batter2)
        }
    }
}

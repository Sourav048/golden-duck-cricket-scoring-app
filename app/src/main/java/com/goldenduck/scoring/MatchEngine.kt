package com.goldenduck.scoring

import androidx.annotation.Keep

@Keep
object MatchEngine {

    fun reduce(state: MatchState, event: ScoringEvent): MatchState {
        return when (event) {
            is ScoringEvent.BallBowled -> processBallBowled(state, event)
            is ScoringEvent.PenaltyAdded -> processPenaltyAdded(state, event)
            is ScoringEvent.PlayerRetired -> processPlayerRetired(state, event)
            is ScoringEvent.StrikeSwapped -> processStrikeSwapped(state)
        }
    }

    fun rebuildState(initialState: MatchState, events: List<ScoringEvent>): MatchState {
        return events.fold(initialState) { currentState, event ->
            reduce(currentState, event)
        }
    }

    private fun processBallBowled(state: MatchState, event: ScoringEvent.BallBowled): MatchState {
        val ball = event.ball
        val playersMap = state.playerStats.mapValues { it.value.copy() }.toMutableMap()

        val facingName = event.facingBatsmanName ?: ball.batsmanName ?: "Batsman"
        val striker = playersMap.getOrPut(facingName) {
            Player(facingName)
        }
        val bowler = playersMap.getOrPut(event.bowlerName ?: ball.bowlerName ?: "Bowler") {
            Player(event.bowlerName ?: ball.bowlerName)
        }

        val nonStrikerName = event.ball.nonStrikerName
            ?: state.currentNonStrikerName?.takeIf { it != facingName }
            ?: state.currentStrikerName?.takeIf { it != facingName }

        val nonStriker = if (!nonStrikerName.isNullOrEmpty() && nonStrikerName != facingName) {
            playersMap.getOrPut(nonStrikerName) { Player(nonStrikerName) }
        } else null

        val outPlayer = if (event.outPlayerName != null) {
            playersMap.getOrPut(event.outPlayerName) { Player(event.outPlayerName) }
        } else null

        if (ball.isWicket) {
            val targetOut = outPlayer ?: striker
            val dInfo = ball.dismissalInfo
            if (!dInfo.isNullOrEmpty() && dInfo != "not out") {
                targetOut.dismissalInfo = dInfo
            }
        }

        // Clone Innings
        val innings = cloneInnings(state.innings)

        // Process Ball on Innings & Player objects
        innings.addBall(
            runs = ball.runs,
            type = ball.type ?: BallType.NORMAL,
            isWicket = ball.isWicket,
            batsman = striker,
            bowler = bowler,
            fielder = event.fielderName,
            manualBatRuns = ball.batRuns,
            outPlayer = outPlayer,
            nonStriker = nonStriker
        )

        // Handle fielding stats if wicket
        if (ball.isWicket && !event.fielderName.isNullOrEmpty()) {
            val fielders = event.fielderName.split(" / ")
            val dInfo = ball.dismissalInfo ?: ""
            fielders.forEach { fName ->
                val f = playersMap.getOrPut(fName.trim()) { Player(fName.trim()) }
                if (dInfo.contains("Caught", ignoreCase = true) || dInfo.startsWith("c ", ignoreCase = true) || dInfo.contains("c & b", ignoreCase = true)) {
                    f.catches++
                } else if (dInfo.contains("Stumped", ignoreCase = true) || dInfo.startsWith("st ", ignoreCase = true)) {
                    f.stumpings++
                } else if (dInfo.contains("Run Out", ignoreCase = true)) {
                    f.runOuts++
                }
            }
        }

        // Handle Bowler Sixes Conceded
        if (ball.runs == 6 && (ball.type == BallType.NORMAL || ball.type == BallType.NO_BALL)) {
            bowler.sixesConceded++
        }

        // Calculate Strike Rotation
        var newStriker: String? = striker.name
        var newNonStriker: String? = nonStriker?.name

        if (ball.isWicket) {
            val outName = outPlayer?.name ?: striker.name
            if (newStriker == outName) {
                newStriker = null
            }
            if (newNonStriker == outName) {
                newNonStriker = null
            }
        }

        val widePenalty = if (state.isRuleRunsOnWide) 1 else 0
        val nbPenalty = if (state.isRuleRunsOnWide) 1 else 0

        val runsForRotation = when (ball.type) {
            BallType.WIDE -> (ball.runs - widePenalty).coerceAtLeast(0)
            BallType.NO_BALL -> (ball.runs - nbPenalty).coerceAtLeast(0)
            BallType.PENALTY -> 0
            else -> ball.runs
        }
        val isOddRuns = runsForRotation % 2 != 0

        val isLegalBall = ball.type != BallType.WIDE && ball.type != BallType.NO_BALL
        val isOverEnded = isLegalBall && (innings.legalBalls % 6 == 0)

        // 1. Odd runs swap (requires both active batsmen present)
        if (isOddRuns && newNonStriker != null && newStriker != null) {
            val temp = newStriker
            newStriker = newNonStriker
            newNonStriker = temp
        }

        // 2. Over completion swap (unconditional — ends change for the new over!)
        if (isOverEnded) {
            if (newStriker != null && newNonStriker != null) {
                val temp = newStriker
                newStriker = newNonStriker
                newNonStriker = temp
            } else if (newStriker == null && newNonStriker != null) {
                // Striker got out on ball 6: Non-striker becomes striker for Over 2
                newStriker = newNonStriker
                newNonStriker = null
            }
        }

        // Handle Free Hit state
        val nextFreeHitActive = (ball.type == BallType.NO_BALL) || (ball.type == BallType.WIDE && state.isFreeHitActive)

        return MatchState(
            innings = innings,
            playerStats = playersMap,
            currentStrikerName = newStriker,
            currentNonStrikerName = newNonStriker,
            currentBowlerName = bowler.name,
            isFreeHitActive = nextFreeHitActive,
            isOverComplete = isOverEnded
        )
    }

    private fun processPenaltyAdded(state: MatchState, event: ScoringEvent.PenaltyAdded): MatchState {
        val innings = cloneInnings(state.innings)
        innings.addPenalty(event.runs)
        return state.copy(innings = innings)
    }

    private fun processPlayerRetired(state: MatchState, event: ScoringEvent.PlayerRetired): MatchState {
        val innings = cloneInnings(state.innings)
        val playersMap = state.playerStats.mapValues { it.value.copy() }.toMutableMap()
        val player = playersMap.getOrPut(event.playerName) { Player(event.playerName) }

        innings.recordRetired(
            p = player,
            isWicket = !event.isHurt,
            info = event.dismissalText,
            sName = state.currentStrikerName,
            nsName = state.currentNonStrikerName,
            bName = state.currentBowlerName
        )

        val newStriker = if (state.currentStrikerName == event.playerName) null else state.currentStrikerName
        val newNonStriker = if (state.currentNonStrikerName == event.playerName) null else state.currentNonStrikerName

        return state.copy(
            innings = innings,
            playerStats = playersMap,
            currentStrikerName = newStriker,
            currentNonStrikerName = newNonStriker
        )
    }

    private fun processStrikeSwapped(state: MatchState): MatchState {
        if (state.currentStrikerName == null || state.currentNonStrikerName == null || state.currentStrikerName == state.currentNonStrikerName) {
            return state
        }
        return state.copy(
            currentStrikerName = state.currentNonStrikerName,
            currentNonStrikerName = state.currentStrikerName
        )
    }

    private fun cloneInnings(source: Innings): Innings {
        val copy = Innings(source.battingTeam, source.bowlingTeam, source.maxOvers, source.maxWickets)
        copy.revisedMaxOvers = source.revisedMaxOvers
        copy.balls.addAll(source.balls)
        copy.totalRuns = source.totalRuns
        copy.totalWickets = source.totalWickets
        copy.legalBalls = source.legalBalls
        copy.battingRuns = source.battingRuns
        copy.wides = source.wides
        copy.noBalls = source.noBalls
        copy.byes = source.byes
        copy.legByes = source.legByes
        copy.penalties = source.penalties
        copy.retiredHurtCount = source.retiredHurtCount
        copy.commentary.addAll(source.commentary)
        copy.fallOfWickets.addAll(source.fallOfWickets)
        copy.partnerships.addAll(source.partnerships)
        copy.completionTime = source.completionTime
        return copy
    }
}

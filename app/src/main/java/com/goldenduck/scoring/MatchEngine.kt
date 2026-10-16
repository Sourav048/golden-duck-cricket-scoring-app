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

        val striker = playersMap.getOrPut(event.facingBatsmanName ?: "Batsman") {
            Player(event.facingBatsmanName)
        }
        val bowler = playersMap.getOrPut(event.bowlerName ?: "Bowler") {
            Player(event.bowlerName)
        }
        val nonStriker = state.currentNonStrikerName?.let { name ->
            if (name != event.facingBatsmanName) {
                playersMap.getOrPut(name) { Player(name) }
            } else null
        }

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
        var newStriker = striker.name
        var newNonStriker = if (nonStriker?.name != striker.name) nonStriker?.name else null

        if (ball.isWicket) {
            val outName = outPlayer?.name ?: striker.name
            if (newStriker == outName) {
                newStriker = null
            }
            if (newNonStriker == outName) {
                newNonStriker = null
            }
        }

        val runsForRotation = if (ball.type == BallType.NORMAL || ball.type == BallType.NO_BALL) ball.batRuns else ball.runs
        val isOddRuns = runsForRotation % 2 != 0

        val isLegalBall = ball.type != BallType.WIDE && ball.type != BallType.NO_BALL
        val isOverEnded = isLegalBall && (innings.legalBalls % 6 == 0)

        // Only rotate strike if there is an active, distinct non-striker present
        if (newNonStriker != null && newStriker != null) {
            if (isOddRuns) {
                val temp = newStriker
                newStriker = newNonStriker
                newNonStriker = temp
            }

            if (isOverEnded) {
                val temp = newStriker
                newStriker = newNonStriker
                newNonStriker = temp
            }
        }

        // Handle Free Hit state
        val nextFreeHitActive = (ball.type == BallType.NO_BALL)

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

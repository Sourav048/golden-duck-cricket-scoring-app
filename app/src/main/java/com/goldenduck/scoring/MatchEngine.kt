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
        val finalState = events.fold(initialState) { currentState, event ->
            reduce(currentState, event)
        }
        recomputeMilestones(finalState, initialState)
        return finalState
    }

    private fun processBallBowled(state: MatchState, event: ScoringEvent.BallBowled): MatchState {
        val ball = event.ball
        val wicket = ball.wicketDetail
        val retiredHurt = wicket?.dismissalType == DismissalType.RETIRED_HURT ||
            (wicket == null && ball.dismissalInfo.equals("retired hurt", ignoreCase = true))
        val retiredOut = wicket?.dismissalType == DismissalType.RETIRED_OUT ||
            (wicket == null && ball.dismissalInfo.equals("retired out", ignoreCase = true))
        if (ball.type == BallType.PENALTY && (retiredHurt || retiredOut)) {
            return processPlayerRetired(state, ScoringEvent.PlayerRetired(
                playerName = wicket?.dismissedPlayerId?.let { state.getPlayer(it)?.name }
                    ?: event.outPlayerName ?: ball.outPlayerName ?: ball.batsmanName ?: "Batsman",
                isHurt = retiredHurt,
                dismissalText = if (retiredHurt) "retired hurt" else "retired out",
                playerId = wicket?.dismissedPlayerId ?: event.outPlayerId
            ))
        }
        if (ball.type == BallType.PENALTY) {
            return processPenaltyAdded(state, ScoringEvent.PenaltyAdded(ball.runs, state.innings.battingTeam))
        }
        val playersMap = state.playerStats.mapValues { it.value.copy() }.toMutableMap()

        val strikerId = resolvePlayerKey(playersMap,
            event.facingBatsmanId ?: ball.batsmanId ?: event.facingBatsmanName ?: ball.batsmanName ?: "Batsman")
        val bowlerId = resolvePlayerKey(playersMap,
            wicket?.bowlerId ?: event.bowlerId ?: ball.bowlerId ?: event.bowlerName ?: ball.bowlerName ?: "Bowler")
        val isSolo = event.isExplicitSolo || (ball.batsmanId != null && ball.nonStrikerId == null && ball.nonStrikerName == null)
        val nonStrikerIdentity = if (isSolo) null else {
            event.nonStrikerId ?: ball.nonStrikerId ?: ball.nonStrikerName
                ?: state.currentNonStrikerId ?: state.currentNonStrikerName?.takeIf { it != (event.facingBatsmanName ?: ball.batsmanName) }
                ?: state.currentStrikerId ?: state.currentStrikerName?.takeIf { it != (event.facingBatsmanName ?: ball.batsmanName) }
        }
        val nonStrikerId = nonStrikerIdentity?.let { resolvePlayerKey(playersMap, it) }

        val striker = playersMap.getOrPut(strikerId) { Player(event.facingBatsmanName ?: strikerId).apply { id = strikerId } }
        val bowler = playersMap.getOrPut(bowlerId) { Player(event.bowlerName ?: bowlerId).apply { id = bowlerId } }

        val nonStriker = if (!nonStrikerId.isNullOrEmpty() && nonStrikerId != strikerId) {
            val nsName = event.ball.nonStrikerName ?: nonStrikerId
            playersMap.getOrPut(nonStrikerId) { Player(nsName).apply { id = nonStrikerId } }
        } else null

        val outPlayerId = (wicket?.dismissedPlayerId ?: event.outPlayerId ?: event.outPlayerName ?: ball.outPlayerName)
            ?.let { resolvePlayerKey(playersMap, it) }
        val outPlayer = if (outPlayerId != null) {
            playersMap.getOrPut(outPlayerId) { Player(event.outPlayerName ?: outPlayerId).apply { id = outPlayerId } }
        } else null

        val innings = cloneInnings(state.innings)
        if (ball.type != BallType.PENALTY) {
            listOfNotNull(striker, nonStriker).forEach { batter ->
                if (batter.dismissalInfo.trim().equals("retired hurt", ignoreCase = true)) {
                    innings.playerReturned(batter)
                    batter.dismissalInfo = "not out"
                }
            }
        }

        if (ball.isWicket) {
            val targetOut = outPlayer ?: striker
            
            if (ball.wicketDetail != null) {
                // NEW APPROACH: Strongly Typed Wicket Detail
                val wicket = ball.wicketDetail!!
                targetOut.dismissalInfo = wicket.toDisplayString { id -> playersMap[id]?.name ?: id }
                
                if (wicket.dismissalType.isBowlerWicket) {
                    bowler.wicketsTaken++
                }
                
                // Fielding stats
                wicket.primaryFielderId?.let { fId ->
                    val fName = event.primaryFielderName ?: fId
                    val f = playersMap.getOrPut(resolvePlayerKey(playersMap, fId)) { Player(fName).apply { id = fId } }
                    when (wicket.dismissalType) {
                        DismissalType.CAUGHT -> f.catches++
                        DismissalType.STUMPED -> f.stumpings++
                        DismissalType.RUN_OUT -> f.runOuts++
                        else -> {}
                    }
                }
                wicket.secondaryFielderId?.let { fId ->
                    if (wicket.dismissalType == DismissalType.RUN_OUT && fId != wicket.primaryFielderId) {
                        val fName = event.secondaryFielderName ?: fId
                        val f = playersMap.getOrPut(resolvePlayerKey(playersMap, fId)) { Player(fName).apply { id = fId } }
                        f.runOuts++
                    }
                }
            } else {
                // LEGACY APPROACH: String Matching
                val dInfo = ball.dismissalInfo
                if (!dInfo.isNullOrEmpty() && dInfo != "not out") {
                    targetOut.dismissalInfo = dInfo
                }
    
                val effectiveDismissal = (dInfo ?: targetOut.dismissalInfo).trim()
                val isRO = effectiveDismissal.contains("Run Out", ignoreCase = true) || (event.fielderName?.contains(" / ") == true)
                val isObstruct = effectiveDismissal.contains("Obstruct", ignoreCase = true)
                val isRetiredHurt = effectiveDismissal.contains("retired hurt", ignoreCase = true)
                val isRetiredOut = effectiveDismissal.contains("retired out", ignoreCase = true) || (effectiveDismissal.contains("retired", ignoreCase = true) && !isRetiredHurt)
    
                val isBowlerWicket = !isRO && !isObstruct && !isRetiredHurt && !isRetiredOut
                if (isBowlerWicket) {
                    bowler.wicketsTaken++
                }
                
                if (!event.fielderName.isNullOrEmpty()) {
                    val fielders = event.fielderName.split(" / ")
                    val fallbackDInfo = ball.dismissalInfo ?: ""
                    fielders.forEach { fName ->
                        val fKey = resolvePlayerKey(playersMap, fName.trim())
                        val f = playersMap.getOrPut(fKey) { Player(fName.trim()).apply { id = fKey } }
                        if (fallbackDInfo.contains("Caught", ignoreCase = true) || fallbackDInfo.startsWith("c ", ignoreCase = true) || fallbackDInfo.contains("c & b", ignoreCase = true)) {
                            f.catches++
                        } else if (fallbackDInfo.contains("Stumped", ignoreCase = true) || fallbackDInfo.startsWith("st ", ignoreCase = true)) {
                            f.stumpings++
                        } else if (fallbackDInfo.contains("Run Out", ignoreCase = true)) {
                            f.runOuts++
                        }
                    }
                }
            }
        }

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
            nonStriker = nonStriker,
            wicketDetail = ball.wicketDetail,
            playerNameResolver = { id -> playersMap[id]?.name ?: id },
            byeRuns = ball.byeRuns,
            legByeRuns = ball.legByeRuns,
            overthrowRuns = ball.overthrowRuns,
            isBoundary = ball.isBoundary
        )

        // Handle Bowler Sixes Conceded
        val isSixConceded = when (ball.type) {
            BallType.NORMAL -> ball.isBoundary && (ball.batRuns == 6 || ball.runs == 6) && ball.overthrowRuns == 0
            BallType.NO_BALL -> ball.isBoundary && ball.batRuns == 6 && ball.overthrowRuns == 0
            else -> false
        }
        if (isSixConceded) {
            bowler.sixesConceded++
        }

        // Calculate Strike Rotation
        var newStrikerId: String? = strikerId
        var newNonStrikerId: String? = nonStrikerId

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

        // 1. Odd runs swap on the ends before wicket removal
        if (isOddRuns && newNonStrikerId != null && newStrikerId != null) {
            val temp = newStrikerId
            newStrikerId = newNonStrikerId
            newNonStrikerId = temp
        }

        // 2. Adjust ends if dismissal occurred at the opposite end from the batsman's crease
        if (ball.isWicket) {
            val outId = outPlayerId ?: strikerId
            val wicket = ball.wicketDetail
            if (wicket != null && newStrikerId != null && newNonStrikerId != null) {
                val isStrikerEnd = wicket.isStrikerEnd
                if (isStrikerEnd && outId == newNonStrikerId) {
                    val temp = newStrikerId
                    newStrikerId = newNonStrikerId
                    newNonStrikerId = temp
                } else if (!isStrikerEnd && outId == newStrikerId) {
                    val temp = newStrikerId
                    newStrikerId = newNonStrikerId
                    newNonStrikerId = temp
                }
            }

            // Remove out player from their vacated end
            if (newStrikerId == outId) {
                newStrikerId = null
            }
            if (newNonStrikerId == outId) {
                newNonStrikerId = null
            }
        }

        // 3. Over completion swap (unconditional — ends change for the new over!)
        if (isOverEnded) {
            if (newStrikerId != null && newNonStrikerId != null) {
                val temp = newStrikerId
                newStrikerId = newNonStrikerId
                newNonStrikerId = temp
            } else if (newStrikerId == null && newNonStrikerId != null) {
                // Striker got out on ball 6: Non-striker becomes striker for Over 2
                newStrikerId = newNonStrikerId
                newNonStrikerId = null
            } else if (newStrikerId != null && newNonStrikerId == null) {
                // Non-striker got out on ball 6: Striker becomes non-striker for Over 2
                newNonStrikerId = newStrikerId
                newStrikerId = null
            }
        }

        // Handle Free Hit state
        val nextFreeHitActive = state.isRuleFreeHit && ((ball.type == BallType.NO_BALL) || (ball.type == BallType.WIDE && state.isFreeHitActive))

        return MatchState(
            innings = innings,
            playerStats = playersMap,
            currentStrikerName = playersMap[newStrikerId]?.name,
            currentNonStrikerName = playersMap[newNonStrikerId]?.name,
            currentBowlerName = bowler.name,
            currentStrikerId = newStrikerId,
            currentNonStrikerId = newNonStrikerId,
            currentBowlerId = bowlerId,
            isFreeHitActive = nextFreeHitActive,
            isOverComplete = isOverEnded,
            isRuleRunsOnWide = state.isRuleRunsOnWide,
            isRuleFreeHit = state.isRuleFreeHit
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
        val playerId = resolvePlayerKey(playersMap, event.playerId ?: event.playerName)
        val player = playersMap.getOrPut(playerId) { Player(event.playerName).apply { id = playerId } }

        innings.recordRetired(
            p = player,
            isWicket = !event.isHurt,
            info = event.dismissalText,
            sName = state.currentStrikerName,
            nsName = state.currentNonStrikerName,
            bName = state.currentBowlerName
        )

        val retiresStriker = state.currentStrikerId == playerId ||
            (state.currentStrikerId == null && state.currentStrikerName == event.playerName)
        val retiresNonStriker = state.currentNonStrikerId == playerId ||
            (state.currentNonStrikerId == null && state.currentNonStrikerName == event.playerName)
        val newStriker = if (retiresStriker) null else state.currentStrikerName
        val newNonStriker = if (retiresNonStriker) null else state.currentNonStrikerName

        return state.copy(
            innings = innings,
            playerStats = playersMap,
            currentStrikerName = newStriker,
            currentNonStrikerName = newNonStriker,
            currentStrikerId = if (retiresStriker) null else state.currentStrikerId,
            currentNonStrikerId = if (retiresNonStriker) null else state.currentNonStrikerId
        )
    }

    private fun processStrikeSwapped(state: MatchState): MatchState {
        if (state.currentStrikerName == null || state.currentNonStrikerName == null || state.currentStrikerName == state.currentNonStrikerName) {
            return state
        }
        return state.copy(
            currentStrikerName = state.currentNonStrikerName,
            currentNonStrikerName = state.currentStrikerName,
            currentStrikerId = state.currentNonStrikerId,
            currentNonStrikerId = state.currentStrikerId
        )
    }

    private fun resolvePlayerKey(players: Map<String, Player>, idOrName: String): String {
        if (players.containsKey(idOrName)) return idOrName
        return players.entries.find { it.value.id == idOrName }?.key
            ?: players.entries.find { it.value.name == idOrName }?.key
            ?: idOrName
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

    private fun recomputeMilestones(state: MatchState, initialState: MatchState) {
        state.playerStats.forEach { (key, player) ->
            val initPlayer = initialState.playerStats[key]
                ?: initialState.playerStats.values.find { it.id == player.id || (player.name != null && it.name == player.name) }
            player.maidens = initPlayer?.maidens ?: 0
            player.hattricks = initPlayer?.hattricks ?: 0
        }

        // 1. Recompute Maidens
        val balls = state.innings.balls
        var currentOverRuns = 0
        var legalBallsInOver = 0
        val overBowlers = mutableSetOf<String>()

        balls.forEach { b ->
            if (b.type == BallType.PENALTY) return@forEach
            val bKey = b.bowlerId ?: b.bowlerName
            val bId = bKey?.let { resolvePlayerKey(state.playerStats, it) }
            if (bId != null) overBowlers.add(bId)

            val nbPenalty = if (state.isRuleRunsOnWide) 1 else 0
            val bowlerRunsForOver = when (b.type) {
                BallType.NORMAL, BallType.WIDE -> b.runs
                BallType.NO_BALL -> b.batRuns + nbPenalty
                else -> 0
            }
            currentOverRuns += bowlerRunsForOver

            if (b.type == BallType.NORMAL || b.type == BallType.BYE || b.type == BallType.LEG_BYE) {
                legalBallsInOver++
            }

            if (legalBallsInOver == 6) {
                if (currentOverRuns == 0 && overBowlers.size == 1) {
                    state.playerStats[overBowlers.first()]?.let { it.maidens++ }
                }
                currentOverRuns = 0
                legalBallsInOver = 0
                overBowlers.clear()
            }
        }

        // 2. Recompute Hattricks (3 consecutive bowler-credited wickets)
        val bowlerStreaks = mutableMapOf<String, Int>()
        balls.forEach { b ->
            if (b.type == BallType.PENALTY) return@forEach
            val bKey = b.bowlerId ?: b.bowlerName ?: return@forEach
            val bId = resolvePlayerKey(state.playerStats, bKey)

            val isBowlerWicket = b.isWicket &&
                (b.wicketDetail?.dismissalType?.isBowlerWicket ?: (
                    !b.dismissalInfo.toString().contains("Run Out", ignoreCase = true) &&
                    !b.dismissalInfo.toString().contains("Obstruct", ignoreCase = true) &&
                    !b.dismissalInfo.toString().contains("Retired", ignoreCase = true)
                ))

            if (isBowlerWicket) {
                val currentStreak = (bowlerStreaks[bId] ?: 0) + 1
                if (currentStreak == 3) {
                    state.playerStats[bId]?.let { it.hattricks++ }
                    bowlerStreaks[bId] = 0
                } else {
                    bowlerStreaks[bId] = currentStreak
                }
            } else {
                bowlerStreaks[bId] = 0
            }
        }
    }
}

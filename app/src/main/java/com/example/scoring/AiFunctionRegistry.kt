package com.example.scoring

import android.util.Log

/**
 * Executes database operations requested by Gemini AI Function Calling.
 * Converts raw Room queries and entities into serializable Maps/Lists for LLM consumption.
 */
class AiFunctionRegistry(private val db: AppDatabase) {

    companion object {
        private const val TAG = "AiFunctionRegistry"
    }

    /**
     * 1. Resolve Player by Name with Fuzzy & Partial Matching
     */
    fun resolvePlayerByName(query: String, gullyId: String = "local"): List<Map<String, Any>> {
        val allPlayers = db.playerDao().getAllPlayersByGully(gullyId)?.filterNotNull() ?: emptyList()
        
        val matched = allPlayers.filter { player ->
            PlayerNameNormalizer.areNamesMatching(player.name, query) ||
            player.name.contains(query, ignoreCase = true) ||
            PlayerNameNormalizer.isFuzzyMatch(player.name, query)
        }

        return matched.map { p ->
            mapOf(
                "playerId" to p.id,
                "playerName" to p.name,
                "jerseyNumber" to p.jerseyNumber,
                "gullyId" to p.gullyId,
                "originGully" to (p.originGully ?: p.gullyId),
                "globalId" to (p.globalId ?: "")
            )
        }
    }

    /**
     * 2. Compute Career & Recent Player Stats with True Cricket Math
     */
    fun getPlayerStats(
        playerId: String,
        lastNMatches: Int? = null,
        startDate: Long? = null,
        endDate: Long? = null
    ): Map<String, Any> {
        var stats = db.statsDao().getStatsByPlayer(playerId)?.filterNotNull() ?: emptyList()

        if (startDate != null && startDate > 0) {
            stats = stats.filter { (it.entryTime > 0 && it.entryTime >= startDate) || (it.exitTime > 0 && it.exitTime >= startDate) }
        }
        if (endDate != null && endDate > 0) {
            stats = stats.filter { (it.entryTime > 0 && it.entryTime <= endDate) || (it.exitTime > 0 && it.exitTime <= endDate) }
        }
        if (lastNMatches != null && lastNMatches > 0) {
            stats = stats.takeLast(lastNMatches)
        }

        val player = db.playerDao().getPlayerById(playerId)
        val playerName = player?.name ?: stats.firstOrNull()?.playerName ?: "Unknown"

        val matchesPlayed = stats.size
        val runs = stats.sumOf { it.runsScored }
        val ballsFaced = stats.sumOf { it.ballsFaced }
        val fours = stats.sumOf { it.fours }
        val sixes = stats.sumOf { it.sixes }
        val timesOut = stats.count { it.isOut }
        val notOuts = matchesPlayed - timesOut

        val battingAvg = if (timesOut > 0) runs.toDouble() / timesOut else runs.toDouble()
        val strikeRate = if (ballsFaced > 0) (runs.toDouble() / ballsFaced) * 100 else 0.0

        val wickets = stats.sumOf { it.wicketsTaken }
        val ballsBowled = stats.sumOf { it.ballsBowled }
        val runsConceded = stats.sumOf { it.runsConceded }
        val maidens = stats.sumOf { it.maidens }
        val hattricks = stats.sumOf { it.hattricks }
        val dotBalls = stats.sumOf { it.dotBalls }

        val economy = if (ballsBowled > 0) (runsConceded.toDouble() / (ballsBowled / 6.0)) else 0.0
        val bowlingAvg = if (wickets > 0) (runsConceded.toDouble() / wickets) else 0.0

        val catches = stats.sumOf { it.catches }
        val stumpings = stats.sumOf { it.stumpings }
        val runOuts = stats.sumOf { it.runOuts }

        return mapOf(
            "playerId" to playerId,
            "playerName" to playerName,
            "matchesPlayed" to matchesPlayed,
            "batting" to mapOf(
                "runs" to runs,
                "ballsFaced" to ballsFaced,
                "fours" to fours,
                "sixes" to sixes,
                "timesOut" to timesOut,
                "notOuts" to notOuts,
                "battingAverage" to String.format("%.2f", battingAvg),
                "strikeRate" to String.format("%.2f", strikeRate),
                "highestScore" to (stats.maxOfOrNull { it.runsScored } ?: 0)
            ),
            "bowling" to mapOf(
                "wickets" to wickets,
                "ballsBowled" to ballsBowled,
                "oversBowled" to String.format("%d.%d", ballsBowled / 6, ballsBowled % 6),
                "runsConceded" to runsConceded,
                "maidens" to maidens,
                "hattricks" to hattricks,
                "dotBalls" to dotBalls,
                "economy" to String.format("%.2f", economy),
                "bowlingAverage" to String.format("%.2f", bowlingAvg)
            ),
            "fielding" to mapOf(
                "catches" to catches,
                "stumpings" to stumpings,
                "runOuts" to runOuts
            )
        )
    }

    /**
     * 2b. Compute Player Stats directly by Player Name in 1 Step
     */
    fun getPlayerStatsByName(
        playerName: String,
        lastNMatches: Int? = null,
        gullyId: String = "local"
    ): Map<String, Any> {
        val resolved = resolvePlayerByName(playerName, gullyId)
        if (resolved.isEmpty()) {
            return mapOf(
                "error" to "Player '$playerName' not found in league database",
                "matchedPlayers" to emptyList<String>()
            )
        }
        val targetPlayerId = resolved.first()["playerId"] as? String ?: ""
        val stats = getPlayerStats(targetPlayerId, lastNMatches)
        return stats + mapOf("resolvedFromQuery" to playerName, "matchedPlayersCount" to resolved.size)
    }

    /**
     * 3. Fetch Top Leaderboards by Stat Type
     */
    fun getLeaderboard(
        statType: String,
        gullyId: String = "local",
        limit: Int = 10,
        startDate: Long? = null,
        endDate: Long? = null
    ): List<Map<String, Any>> {
        val rawList: List<PlayerTotalStat?>? = when (statType.uppercase()) {
            "MOST_RUNS", "RUNS" -> db.statsDao().getMostRuns(gullyId)
            "BEST_STRIKE_RATE", "STRIKE_RATE" -> db.statsDao().getBestStrikeRate(gullyId)
            "MOST_SIXES", "SIXES" -> db.statsDao().getMostSixes(gullyId)
            "MOST_FOURS", "FOURS" -> db.statsDao().getMostFours(gullyId)
            "HIGHEST_SCORE", "HIGHEST_INDIVIDUAL_SCORE" -> db.statsDao().getHighestScores(gullyId)
            "MOST_FIFTIES", "FIFTIES" -> db.statsDao().getMostFifties(gullyId)
            "MOST_EIGHTIES", "EIGHTIES" -> db.statsDao().getMostEighties(gullyId)
            "MOST_THIRTIES", "THIRTIES" -> db.statsDao().getMostThirties(gullyId)
            "MOST_DUCKS", "DUCKS" -> db.statsDao().getMostDucks(gullyId)
            "MOST_WICKETS", "WICKETS" -> db.statsDao().getMostWickets(gullyId)
            "BEST_BOWLING_INNINGS" -> db.statsDao().getBestBowlingInnings(gullyId)
            "BEST_BOWLING_AVERAGE" -> db.statsDao().getBestBowlingAverage(gullyId)
            "BEST_ECONOMY", "ECONOMY" -> db.statsDao().getBestEconomy(gullyId)
            "MOST_HATTRICKS", "HATTRICKS" -> db.statsDao().getMostHattricks(gullyId)
            "MOST_FIVE_WICKETS", "FIVE_WICKETS" -> db.statsDao().getMostFiveWicketHauls(gullyId)
            "MOST_THREE_WICKETS", "THREE_WICKETS" -> db.statsDao().getMostThreeWicketHauls(gullyId)
            "MOST_TWO_WICKETS", "TWO_WICKETS" -> db.statsDao().getMostTwoWicketHauls(gullyId)
            "MOST_CATCHES", "CATCHES" -> db.statsDao().getMostCatches(gullyId)
            "MOST_STUMPINGS", "STUMPINGS" -> db.statsDao().getMostStumpings(gullyId)
            "MOST_RUN_OUTS", "RUN_OUTS" -> db.statsDao().getMostRunOuts(gullyId)
            else -> db.statsDao().getMostRuns(gullyId)
        }

        val filtered = (rawList?.filterNotNull() ?: emptyList()).take(limit)
        return filtered.mapIndexed { index, item ->
            mapOf(
                "rank" to (index + 1),
                "playerId" to (item.playerId ?: ""),
                "playerName" to (item.playerName ?: "Unknown"),
                "totalValue" to item.total,
                "statType" to statType
            )
        }
    }

    /**
     * 4. Prestige Rankings & Badges
     */
    fun getRankingsAndPrestige(category: String, gullyId: String = "local"): Map<String, Any> {
        val rankings = when (category.uppercase()) {
            "BATTING" -> db.statsDao().getBattingRankings(gullyId)
            "BOWLING" -> db.statsDao().getBowlingRankings(gullyId)
            else -> db.statsDao().getOverallRankings(gullyId)
        }?.filterNotNull() ?: emptyList()

        val top3 = rankings.take(3).mapIndexed { idx, stat ->
            val badge = when (idx) {
                0 -> "Gold (1st Place)"
                1 -> "Silver (2nd Place)"
                else -> "Bronze (3rd Place)"
            }
            mapOf(
                "rank" to (idx + 1),
                "badge" to badge,
                "playerId" to (stat.playerId ?: ""),
                "playerName" to (stat.playerName ?: "Unknown"),
                "rankPoints" to stat.total
            )
        }

        return mapOf(
            "category" to category,
            "topPerformers" to top3,
            "totalRankedPlayers" to rankings.size
        )
    }

    /**
     * 5. Match List Search & Filtering
     */
    fun getMatchList(
        status: String? = null,
        teamName: String? = null,
        venue: String? = null,
        gullyId: String = "local"
    ): List<Map<String, Any>> {
        var matches = db.matchDao().getAllMatchesByGully(gullyId)?.filterNotNull() ?: emptyList()

        if (!status.isNullOrBlank()) {
            matches = when (status.uppercase()) {
                "FINISHED" -> matches.filter { it.isFinished && !it.isAbandoned }
                "LIVE" -> matches.filter { it.isMatchLive() }
                "ABANDONED" -> matches.filter { it.isAbandoned }
                else -> matches
            }
        }

        if (!teamName.isNullOrBlank()) {
            matches = matches.filter {
                it.teamAName?.contains(teamName, ignoreCase = true) == true ||
                it.teamBName?.contains(teamName, ignoreCase = true) == true
            }
        }

        if (!venue.isNullOrBlank()) {
            matches = matches.filter { it.venue?.contains(venue, ignoreCase = true) == true }
        }

        return matches.map { m ->
            mapOf(
                "matchId" to m.id,
                "teamA" to (m.teamAName ?: "Team A"),
                "teamB" to (m.teamBName ?: "Team B"),
                "venue" to (m.venue ?: "N/A"),
                "totalOvers" to m.totalOvers,
                "result" to (m.result ?: "In Progress / Draft"),
                "isFinished" to m.isFinished,
                "isAbandoned" to m.isAbandoned,
                "playedAt" to m.playedAt
            )
        }
    }

    /**
     * 6. Detailed Match Summary
     */
    fun getMatchFullDetails(matchId: String): Map<String, Any>? {
        val m = db.matchDao().getMatchById(matchId) ?: return null

        return mapOf(
            "matchId" to m.id,
            "teamA" to (m.teamAName ?: "Team A"),
            "teamB" to (m.teamBName ?: "Team B"),
            "venue" to (m.venue ?: "N/A"),
            "totalOvers" to m.totalOvers,
            "ballType" to (m.ballType ?: "Tennis"),
            "result" to (m.result ?: "Pending"),
            "playerOfTheMatch" to (m.playerOfTheMatchName ?: "None"),
            "toss" to mapOf(
                "winner" to (m.tossWinner ?: "Unknown"),
                "decision" to (m.tossDecision ?: "Unknown")
            ),
            "firstInnings" to mapOf(
                "team" to (m.firstInningsTeam ?: ""),
                "runs" to m.firstInningsRuns,
                "wickets" to m.firstInningsWickets,
                "retiredHurt" to m.firstInningsRetiredHurtCount
            ),
            "secondInnings" to mapOf(
                "team" to (m.secondInningsTeam ?: ""),
                "runs" to m.secondInningsRuns,
                "wickets" to m.secondInningsWickets,
                "retiredHurt" to m.secondInningsRetiredHurtCount
            ),
            "rules" to mapOf(
                "runsOnWide" to m.ruleRunsOnWide,
                "freeHit" to m.ruleFreeHit,
                "runsOnBye" to m.ruleRunsOnBye,
                "overthrow" to m.ruleOverthrow,
                "everyPlayerBats" to m.ruleEveryPlayerBats
            )
        )
    }

    /**
     * 7. Full Match Scorecard
     */
    fun getMatchScorecard(matchId: String): Map<String, Any> {
        val stats = db.statsDao().getStatsByMatch(matchId)?.filterNotNull() ?: emptyList()
        val match = db.matchDao().getMatchById(matchId)

        val playerStatsList = stats.map { s ->
            mapOf(
                "playerId" to (s.playerId ?: ""),
                "playerName" to (s.playerName ?: "Unknown"),
                "teamName" to (s.teamName ?: ""),
                "batting" to mapOf(
                    "runs" to s.runsScored,
                    "balls" to s.ballsFaced,
                    "fours" to s.fours,
                    "sixes" to s.sixes,
                    "isOut" to s.isOut,
                    "dismissalInfo" to (s.dismissalInfo ?: if (s.isOut) "out" else "not out")
                ),
                "bowling" to mapOf(
                    "balls" to s.ballsBowled,
                    "runs" to s.runsConceded,
                    "wickets" to s.wicketsTaken,
                    "maidens" to s.maidens,
                    "hattricks" to s.hattricks,
                    "sixesConceded" to s.sixesConceded,
                    "dotBalls" to s.dotBalls
                ),
                "fielding" to mapOf(
                    "catches" to s.catches,
                    "stumpings" to s.stumpings,
                    "runOuts" to s.runOuts
                )
            )
        }

        return mapOf(
            "matchId" to matchId,
            "matchTitle" to "${match?.teamAName ?: "Team A"} vs ${match?.teamBName ?: "Team B"}",
            "scorecard" to playerStatsList
        )
    }

    /**
     * 8. Match Breakdown (Commentary, Fall of Wickets, Partnerships, Ball-by-Ball)
     */
    fun getMatchBreakdown(
        matchId: String,
        inningsNumber: Int = 1,
        queryType: String = "COMMENTARY"
    ): Map<String, Any> {
        val match = db.matchDao().getMatchById(matchId) ?: return mapOf("error" to "Match not found")

        val balls = if (inningsNumber == 2) match.ballsJson2 else match.ballsJson1
        val commentary = if (inningsNumber == 2) match.commentaryJson2 else match.commentaryJson1
        val fow = if (inningsNumber == 2) match.fowJson2 else match.fowJson1
        val pship = if (inningsNumber == 2) match.pshipJson2 else match.pshipJson1

        return when (queryType.uppercase()) {
            "BALL_BY_BALL", "BALLS" -> mapOf<String, Any>(
                "matchId" to matchId,
                "innings" to inningsNumber,
                "balls" to (balls?.filterNotNull()?.mapIndexed { idx, b ->
                    mapOf<String, Any>(
                        "ballIndex" to (idx + 1),
                        "batsman" to (b.batsmanName ?: ""),
                        "bowler" to (b.bowlerName ?: ""),
                        "runs" to b.runs,
                        "batRuns" to b.batRuns,
                        "type" to (b.type?.name ?: "NORMAL"),
                        "isWicket" to b.isWicket,
                        "dismissalInfo" to (b.dismissalInfo ?: "")
                    )
                } ?: emptyList())
            )
            "FALL_OF_WICKETS", "FOW" -> mapOf<String, Any>(
                "matchId" to matchId,
                "innings" to inningsNumber,
                "fallOfWickets" to (fow?.filterNotNull()?.map { f ->
                    mapOf<String, Any>(
                        "wicketNumber" to f.wicketNumber,
                        "playerName" to (f.playerName ?: ""),
                        "scoreAtWicket" to f.scoreAtWicket,
                        "over" to (f.over ?: "")
                    )
                } ?: emptyList())
            )
            "PARTNERSHIPS" -> mapOf<String, Any>(
                "matchId" to matchId,
                "innings" to inningsNumber,
                "partnerships" to (pship?.filterNotNull()?.map { p ->
                    mapOf<String, Any>(
                        "batter1" to (p.batter1 ?: ""),
                        "batter2" to (p.batter2 ?: ""),
                        "runs" to p.runs,
                        "balls" to p.balls,
                        "b1Runs" to p.b1Runs,
                        "b2Runs" to p.b2Runs
                    )
                } ?: emptyList())
            )
            else -> mapOf<String, Any>(
                "matchId" to matchId,
                "innings" to inningsNumber,
                "commentary" to (commentary?.filterNotNull()?.map { c ->
                    mapOf<String, Any>(
                        "over" to (c.over ?: ""),
                        "text" to (c.text ?: ""),
                        "highlight" to (c.highlight ?: ""),
                        "type" to (c.type ?: "BALL")
                    )
                } ?: emptyList())
            )
        }
    }

    /**
     * 9. Batter vs Bowler Head-to-Head Matchup
     */
    fun getBatterVsBowlerMatchup(
        batterName: String,
        bowlerName: String,
        inningsNumber: Int? = null,
        matchId: String? = null,
        gullyId: String = "local"
    ): Map<String, Any> {
        val matches = if (!matchId.isNullOrBlank()) {
            listOfNotNull(db.matchDao().getMatchById(matchId))
        } else {
            db.matchDao().getAllMatchesByGully(gullyId)?.filterNotNull() ?: emptyList()
        }

        var totalRuns = 0
        var totalBallsFaced = 0
        var fours = 0
        var sixes = 0
        var dotBalls = 0
        var timesDismissed = 0
        val dismissalList = mutableListOf<String>()

        for (m in matches) {
            val ballsToInspect: List<Ball> = when (inningsNumber) {
                1 -> m.ballsJson1?.filterNotNull() ?: emptyList()
                2 -> m.ballsJson2?.filterNotNull() ?: emptyList()
                else -> (m.ballsJson1?.filterNotNull().orEmpty() + m.ballsJson2?.filterNotNull().orEmpty())
            }

            val matchingBalls = ballsToInspect.filter { b ->
                PlayerNameNormalizer.areNamesMatching(b.batsmanName, batterName) &&
                PlayerNameNormalizer.areNamesMatching(b.bowlerName, bowlerName)
            }

            for (b in matchingBalls) {
                totalRuns += b.batRuns
                if (b.type != BallType.WIDE) {
                    totalBallsFaced++
                }
                if (b.batRuns == 4) fours++
                if (b.batRuns == 6) sixes++
                if (b.runs == 0) dotBalls++

                if (b.isWicket && PlayerNameNormalizer.areNamesMatching(b.outPlayerName, batterName)) {
                    timesDismissed++
                    b.dismissalInfo?.let { dismissalList.add(it) }
                }
            }
        }

        val strikeRate = if (totalBallsFaced > 0) (totalRuns.toDouble() / totalBallsFaced) * 100 else 0.0

        return mapOf(
            "batter" to batterName,
            "bowler" to bowlerName,
            "inningsNumber" to (inningsNumber ?: "All"),
            "matchId" to (matchId ?: "All Matches"),
            "runsScored" to totalRuns,
            "ballsFaced" to totalBallsFaced,
            "fours" to fours,
            "sixes" to sixes,
            "dotBalls" to dotBalls,
            "timesDismissed" to timesDismissed,
            "dismissalDetails" to dismissalList,
            "strikeRate" to String.format("%.2f", strikeRate)
        )
    }

    /**
     * 10. Live Match Status & Resumption State
     */
    fun getLiveMatchStatus(matchId: String? = null, gullyId: String = "local"): Map<String, Any> {
        val allMatches = db.matchDao().getAllMatchesByGully(gullyId)?.filterNotNull() ?: emptyList()
        val liveMatch = if (!matchId.isNullOrBlank()) {
            db.matchDao().getMatchById(matchId)
        } else {
            allMatches.firstOrNull { it.isMatchLive() }
        }

        if (liveMatch == null) {
            return mapOf("isLiveMatchActive" to false, "message" to "No live match currently in progress.")
        }

        val i1 = liveMatch.firstInningsRuns
        val i2 = liveMatch.secondInningsRuns
        val target = liveMatch.revisedTarget ?: (i1 + 1)

        return mapOf(
            "isLiveMatchActive" to true,
            "matchId" to liveMatch.id,
            "teamA" to (liveMatch.teamAName ?: "Team A"),
            "teamB" to (liveMatch.teamBName ?: "Team B"),
            "currentBattingTeam" to if (liveMatch.isTeamABatting) (liveMatch.teamAName ?: "Team A") else (liveMatch.teamBName ?: "Team B"),
            "currentStriker" to (liveMatch.currentStrikerName ?: "N/A"),
            "currentNonStriker" to (liveMatch.currentNonStrikerName ?: "N/A"),
            "currentBowler" to (liveMatch.currentBowlerName ?: "N/A"),
            "firstInningsScore" to "$i1/${liveMatch.firstInningsWickets}",
            "secondInningsScore" to "$i2/${liveMatch.secondInningsWickets}",
            "target" to target,
            "runsNeeded" to maxOf(0, target - i2),
            "freeHitActive" to liveMatch.isFreeHitActive
        )
    }

    /**
     * 11. Team Standings & Records
     */
    fun getTeamRecordsAndStandings(teamName: String? = null, gullyId: String = "local"): Map<String, Any> {
        val matches = db.matchDao().getAllMatchesByGully(gullyId)?.filterNotNull()?.filter { it.isFinished && !it.isAbandoned } ?: emptyList()

        if (!teamName.isNullOrBlank()) {
            val teamMatches = matches.filter {
                it.teamAName?.equals(teamName, ignoreCase = true) == true ||
                it.teamBName?.equals(teamName, ignoreCase = true) == true
            }

            val wins = teamMatches.count { it.result?.contains(teamName, ignoreCase = true) == true }
            val totalPlayed = teamMatches.size
            val losses = totalPlayed - wins
            val winPct = if (totalPlayed > 0) (wins.toDouble() / totalPlayed) * 100 else 0.0

            val highestScore = teamMatches.maxOfOrNull { m ->
                if (m.firstInningsTeam?.equals(teamName, ignoreCase = true) == true) m.firstInningsRuns
                else if (m.secondInningsTeam?.equals(teamName, ignoreCase = true) == true) m.secondInningsRuns
                else 0
            } ?: 0

            return mapOf(
                "teamName" to teamName,
                "matchesPlayed" to totalPlayed,
                "wins" to wins,
                "losses" to losses,
                "winPercentage" to String.format("%.1f%%", winPct),
                "highestTeamScore" to highestScore
            )
        }

        // Aggregate records for all teams
        val teamWinMap = mutableMapOf<String, Int>()
        val teamMatchMap = mutableMapOf<String, Int>()

        for (m in matches) {
            val tA = m.teamAName ?: continue
            val tB = m.teamBName ?: continue

            teamMatchMap[tA] = (teamMatchMap[tA] ?: 0) + 1
            teamMatchMap[tB] = (teamMatchMap[tB] ?: 0) + 1

            m.result?.let { res ->
                if (res.contains(tA, ignoreCase = true)) {
                    teamWinMap[tA] = (teamWinMap[tA] ?: 0) + 1
                } else if (res.contains(tB, ignoreCase = true)) {
                    teamWinMap[tB] = (teamWinMap[tB] ?: 0) + 1
                }
            }
        }

        val standings = teamMatchMap.map { (team, played) ->
            val wins = teamWinMap[team] ?: 0
            val winPct = if (played > 0) (wins.toDouble() / played) * 100 else 0.0
            mapOf(
                "team" to team,
                "played" to played,
                "wins" to wins,
                "losses" to (played - wins),
                "winPercentage" to String.format("%.1f%%", winPct)
            )
        }.sortedByDescending { it["wins"] as Int }

        return mapOf("standings" to standings)
    }

    /**
     * 12. Venue Analytics
     */
    fun getVenueAnalytics(venueName: String, gullyId: String = "local"): Map<String, Any> {
        val matches = db.matchDao().getAllMatchesByGully(gullyId)?.filterNotNull()
            ?.filter { it.venue?.contains(venueName, ignoreCase = true) == true && it.isFinished } ?: emptyList()

        if (matches.isEmpty()) {
            return mapOf("venue" to venueName, "message" to "No completed matches recorded for this venue.")
        }

        val totalMatches = matches.size
        val avgFirstInningsRuns = matches.map { it.firstInningsRuns }.average()
        val avgSecondInningsRuns = matches.map { it.secondInningsRuns }.average()

        val batFirstWins = matches.count { m ->
            m.firstInningsTeam != null && m.result?.contains(m.firstInningsTeam!!, ignoreCase = true) == true
        }
        val bowlFirstWins = totalMatches - batFirstWins

        return mapOf(
            "venue" to venueName,
            "totalMatches" to totalMatches,
            "avgFirstInningsRuns" to String.format("%.1f", avgFirstInningsRuns),
            "avgSecondInningsRuns" to String.format("%.1f", avgSecondInningsRuns),
            "batFirstWins" to batFirstWins,
            "chasingWins" to bowlFirstWins,
            "batFirstWinPct" to String.format("%.1f%%", (batFirstWins.toDouble() / totalMatches) * 100)
        )
    }

    /**
     * Central Dispatcher Method
     */
    fun executeFunctionCall(name: String, args: Map<String, Any?>, gullyId: String = "local"): Any? {
        Log.d(TAG, "Executing function call: $name with args: $args")
        return try {
            when (name) {
                "resolvePlayerByName" -> resolvePlayerByName(
                    query = args["query"] as? String ?: "",
                    gullyId = gullyId
                )
                "getPlayerStats" -> getPlayerStats(
                    playerId = args["playerId"] as? String ?: "",
                    lastNMatches = (args["lastNMatches"] as? Number)?.toInt(),
                    startDate = (args["startDate"] as? Number)?.toLong(),
                    endDate = (args["endDate"] as? Number)?.toLong()
                )
                "getPlayerStatsByName" -> getPlayerStatsByName(
                    playerName = args["playerName"] as? String ?: "",
                    lastNMatches = (args["lastNMatches"] as? Number)?.toInt(),
                    gullyId = gullyId
                )
                "getLeaderboard" -> getLeaderboard(
                    statType = args["statType"] as? String ?: "MOST_RUNS",
                    gullyId = gullyId,
                    limit = (args["limit"] as? Number)?.toInt() ?: 10,
                    startDate = (args["startDate"] as? Number)?.toLong(),
                    endDate = (args["endDate"] as? Number)?.toLong()
                )
                "getRankingsAndPrestige" -> getRankingsAndPrestige(
                    category = args["category"] as? String ?: "OVERALL",
                    gullyId = gullyId
                )
                "getMatchList" -> getMatchList(
                    status = args["status"] as? String,
                    teamName = args["teamName"] as? String,
                    venue = args["venue"] as? String,
                    gullyId = gullyId
                )
                "getMatchFullDetails" -> getMatchFullDetails(
                    matchId = args["matchId"] as? String ?: ""
                )
                "getMatchScorecard" -> getMatchScorecard(
                    matchId = args["matchId"] as? String ?: ""
                )
                "getMatchBreakdown" -> getMatchBreakdown(
                    matchId = args["matchId"] as? String ?: "",
                    inningsNumber = (args["inningsNumber"] as? Number)?.toInt() ?: 1,
                    queryType = args["queryType"] as? String ?: "COMMENTARY"
                )
                "getBatterVsBowlerMatchup" -> getBatterVsBowlerMatchup(
                    batterName = args["batterName"] as? String ?: "",
                    bowlerName = args["bowlerName"] as? String ?: "",
                    inningsNumber = (args["inningsNumber"] as? Number)?.toInt(),
                    matchId = args["matchId"] as? String,
                    gullyId = gullyId
                )
                "getLiveMatchStatus" -> getLiveMatchStatus(
                    matchId = args["matchId"] as? String,
                    gullyId = gullyId
                )
                "getTeamRecordsAndStandings" -> getTeamRecordsAndStandings(
                    teamName = args["teamName"] as? String,
                    gullyId = gullyId
                )
                "getVenueAnalytics" -> getVenueAnalytics(
                    venueName = args["venueName"] as? String ?: "",
                    gullyId = gullyId
                )
                else -> mapOf("error" to "Unknown function: $name")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $name: ${e.message}", e)
            mapOf("error" to "Execution failed: ${e.message}")
        }
    }
}

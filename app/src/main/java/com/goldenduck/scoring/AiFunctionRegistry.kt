package com.goldenduck.scoring

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        dateQuery: String? = null,
        gullyId: String = "local"
    ): List<Map<String, Any>> {
        var matches = db.matchDao().getAllMatchesByGully(gullyId)?.filterNotNull() ?: emptyList()

        if (!status.isNullOrBlank()) {
            matches = when (status.uppercase(Locale.getDefault())) {
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

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        if (!dateQuery.isNullOrBlank()) {
            val q = dateQuery.lowercase(Locale.getDefault())
            matches = matches.filter { m ->
                val formattedDate = try { sdf.format(Date(m.playedAt)) } catch (_: Exception) { "" }
                formattedDate.lowercase(Locale.getDefault()).contains(q)
            }
        }

        return matches.map { m ->
            val dateFormatted = try { sdf.format(Date(m.playedAt)) } catch (_: Exception) { "N/A" }
            mapOf(
                "matchId" to m.id,
                "teamA" to (m.teamAName ?: "Team A"),
                "teamB" to (m.teamBName ?: "Team B"),
                "venue" to (m.venue ?: "N/A"),
                "totalOvers" to m.totalOvers,
                "result" to (m.result ?: "In Progress / Draft"),
                "isFinished" to m.isFinished,
                "isAbandoned" to m.isAbandoned,
                "playedAt" to m.playedAt,
                "date" to dateFormatted
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
     * Finds match IDs and details for a player's specific innings or knock (e.g. 61 runs).
     */
    fun findPlayerMatchByKnock(
        playerName: String,
        targetRuns: Int? = null,
        gullyId: String = "local"
    ): Map<String, Any> {
        val resolvedList = resolvePlayerByName(playerName, gullyId)
        val firstMatch = resolvedList.firstOrNull()
        val playerId = firstMatch?.get("playerId") as? String
        val actualName = (firstMatch?.get("playerName") as? String)?.takeIf { it.isNotBlank() } ?: playerName

        val allStats = if (!playerId.isNullOrBlank()) {
            db.statsDao().getStatsByPlayer(playerId)?.filterNotNull() ?: emptyList()
        } else {
            db.statsDao().getAllStats()?.filterNotNull()?.filter {
                PlayerNameNormalizer.areNamesMatching(it.playerName, playerName)
            } ?: emptyList()
        }

        if (allStats.isEmpty()) {
            return mapOf(
                "error" to "No match stats found for player '$actualName'",
                "playerName" to actualName
            )
        }

        val inningsList = allStats.mapNotNull { stat ->
            val mId = stat.matchId ?: return@mapNotNull null
            val match = db.matchDao().getMatchById(mId) ?: return@mapNotNull null
            val opponent = if (match.teamAName.equals(stat.teamName, ignoreCase = true)) {
                match.teamBName ?: "Opponent"
            } else {
                match.teamAName ?: "Opponent"
            }

            mapOf<String, Any>(
                "matchId" to match.id,
                "playerName" to (stat.playerName ?: actualName),
                "teamName" to (stat.teamName ?: ""),
                "opponent" to opponent,
                "runsScored" to stat.runsScored,
                "ballsFaced" to stat.ballsFaced,
                "fours" to stat.fours,
                "sixes" to stat.sixes,
                "isOut" to stat.isOut,
                "dismissalInfo" to (stat.dismissalInfo ?: if (stat.isOut) "out" else "not out"),
                "playedAt" to match.playedAt,
                "venue" to (match.venue ?: "N/A"),
                "result" to (match.result ?: "")
            )
        }.sortedByDescending { it["playedAt"] as? Long ?: 0L }

        fun extractPlayerBalls(matchId: String, pName: String): List<Map<String, Any>> {
            val match = db.matchDao().getMatchById(matchId) ?: return emptyList()
            val b1 = match.ballsJson1?.filterNotNull().orEmpty()
            val b2 = match.ballsJson2?.filterNotNull().orEmpty()
            val pb1 = b1.filter { PlayerNameNormalizer.areNamesMatching(it.batsmanName, pName) }
            val pb2 = b2.filter { PlayerNameNormalizer.areNamesMatching(it.batsmanName, pName) }
            val selected = if (pb1.isNotEmpty()) pb1 else pb2
            return selected.mapIndexed { idx, b ->
                mapOf<String, Any>(
                    "ballIndex" to (idx + 1),
                    "batsman" to (b.batsmanName ?: pName),
                    "bowler" to (b.bowlerName ?: "Bowler"),
                    "runs" to b.runs,
                    "batRuns" to b.batRuns,
                    "type" to (b.type?.name ?: "NORMAL"),
                    "isWicket" to b.isWicket,
                    "dismissalInfo" to (b.dismissalInfo ?: "")
                )
            }
        }

        if (targetRuns != null && targetRuns > 0) {
            val exactMatches = inningsList.filter { (it["runsScored"] as? Int) == targetRuns }
            if (exactMatches.isNotEmpty()) {
                val topMatch = exactMatches.first().toMutableMap()
                val mId = topMatch["matchId"] as? String ?: ""
                topMatch["balls"] = extractPlayerBalls(mId, actualName)
                return mapOf(
                    "playerName" to actualName,
                    "targetRuns" to targetRuns,
                    "foundMatch" to topMatch,
                    "allMatchingInnings" to exactMatches,
                    "recentInnings" to inningsList.take(5)
                )
            }
            val closeMatches = inningsList.filter {
                val r = it["runsScored"] as? Int ?: 0
                kotlin.math.abs(r - targetRuns) <= 5
            }
            if (closeMatches.isNotEmpty()) {
                val topMatch = closeMatches.first().toMutableMap()
                val mId = topMatch["matchId"] as? String ?: ""
                topMatch["balls"] = extractPlayerBalls(mId, actualName)
                return mapOf(
                    "playerName" to actualName,
                    "targetRuns" to targetRuns,
                    "foundMatch" to topMatch,
                    "closeMatches" to closeMatches,
                    "recentInnings" to inningsList.take(5)
                )
            }
        }

        return mapOf(
            "playerName" to actualName,
            "totalInnings" to inningsList.size,
            "allInnings" to inningsList
        )
    }

    /**
     * 9. Batter vs Bowler Head-to-Head Matchup & All-Bowlers Breakdown
     */
    fun getBatterVsBowlerMatchup(
        batterName: String,
        bowlerName: String? = null,
        inningsNumber: Int? = null,
        matchId: String? = null,
        gullyId: String = "local"
    ): Map<String, Any> {
        val matches = if (!matchId.isNullOrBlank()) {
            listOfNotNull(db.matchDao().getMatchById(matchId))
        } else {
            db.matchDao().getAllMatchesByGully(gullyId)?.filterNotNull() ?: emptyList()
        }

        val targetBowlerName = bowlerName?.trim()

        if (!targetBowlerName.isNullOrBlank()) {
            // 1-on-1 Head-to-Head matchup
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
                    PlayerNameNormalizer.areNamesMatching(b.bowlerName, targetBowlerName)
                }

                for (b in matchingBalls) {
                    totalRuns += b.batRuns
                    if (b.type != BallType.WIDE) {
                        totalBallsFaced++
                    }
                    if (b.isBoundary && b.batRuns == 4) fours++
                    if (b.isBoundary && b.batRuns == 6) sixes++
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
                "bowler" to targetBowlerName,
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

        // 1-vs-ALL Breakdown when bowlerName is omitted or blank
        class BowlerStat(
            val name: String,
            var runsScored: Int = 0,
            var ballsFaced: Int = 0,
            var fours: Int = 0,
            var sixes: Int = 0,
            var dotBalls: Int = 0,
            var timesDismissed: Int = 0
        )

        val bowlerMap = mutableMapOf<String, BowlerStat>()

        for (m in matches) {
            val ballsToInspect: List<Ball> = when (inningsNumber) {
                1 -> m.ballsJson1?.filterNotNull() ?: emptyList()
                2 -> m.ballsJson2?.filterNotNull() ?: emptyList()
                else -> (m.ballsJson1?.filterNotNull().orEmpty() + m.ballsJson2?.filterNotNull().orEmpty())
            }

            val matchingBalls = ballsToInspect.filter { b ->
                PlayerNameNormalizer.areNamesMatching(b.batsmanName, batterName)
            }

            for (b in matchingBalls) {
                val rawBowlerName = b.bowlerName?.trim()
                if (rawBowlerName.isNullOrBlank()) continue

                val existingKey = bowlerMap.keys.firstOrNull { PlayerNameNormalizer.areNamesMatching(it, rawBowlerName) } ?: rawBowlerName
                val stat = bowlerMap.getOrPut(existingKey) { BowlerStat(existingKey) }

                stat.runsScored += b.batRuns
                if (b.type != BallType.WIDE) {
                    stat.ballsFaced++
                }
                if (b.isBoundary && b.overthrowRuns == 0) {
                    if (b.batRuns == 4) stat.fours++
                    if (b.batRuns == 6) stat.sixes++
                }
                if (b.runs == 0) stat.dotBalls++

                if (b.isWicket && PlayerNameNormalizer.areNamesMatching(b.outPlayerName, batterName)) {
                    stat.timesDismissed++
                }
            }
        }

        val breakdownList = bowlerMap.values.map { s ->
            val sr = if (s.ballsFaced > 0) (s.runsScored.toDouble() / s.ballsFaced) * 100 else 0.0
            mapOf(
                "bowler" to s.name,
                "sixes" to s.sixes,
                "fours" to s.fours,
                "runsScored" to s.runsScored,
                "ballsFaced" to s.ballsFaced,
                "dotBalls" to s.dotBalls,
                "timesDismissed" to s.timesDismissed,
                "strikeRate" to String.format("%.2f", sr)
            )
        }.sortedWith(compareByDescending<Map<String, Any>> { it["sixes"] as Int }
            .thenByDescending { it["runsScored"] as Int })

        val totalSixes = breakdownList.sumOf { it["sixes"] as Int }
        val totalFours = breakdownList.sumOf { it["fours"] as Int }
        val totalRuns = breakdownList.sumOf { it["runsScored"] as Int }
        val totalBalls = breakdownList.sumOf { it["ballsFaced"] as Int }

        return mapOf(
            "batter" to batterName,
            "inningsNumber" to (inningsNumber ?: "All"),
            "matchId" to (matchId ?: "All Matches"),
            "totalBowlersFaced" to breakdownList.size,
            "totalSixesHit" to totalSixes,
            "totalFoursHit" to totalFours,
            "totalRunsScored" to totalRuns,
            "totalBallsFaced" to totalBalls,
            "bowlerBreakdown" to breakdownList
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
     * 15. Search Commentary Text & Moments Across All Matches
     */
    fun searchCommentary(
        query: String,
        playerName: String? = null,
        matchId: String? = null,
        limit: Int = 20,
        gullyId: String = "local"
    ): List<Map<String, Any>> {
        val rawMatches = if (!matchId.isNullOrBlank()) {
            val single = db.matchDao().getMatchById(matchId)
            if (single != null) listOf(single) else emptyList()
        } else {
            db.matchDao().getAllMatchesByGully(gullyId) ?: db.matchDao().getAllMatches() ?: emptyList()
        }

        val matches = rawMatches.filterNotNull().sortedByDescending { it.playedAt }
        val cleanQuery = query.trim().lowercase(Locale.getDefault())

        val stopWords = setOf("the", "a", "an", "in", "on", "at", "by", "for", "to", "of", "is", "it", "me", "where", "says", "fetch", "find", "show", "search")
        val queryTokens = cleanQuery.split("\\s+".toRegex())
            .map { it.lowercase(Locale.getDefault()) }
            .filter { it.length > 1 && it !in stopWords }

        val playerQuery = playerName?.trim()?.lowercase(Locale.getDefault()) ?: ""

        val results = mutableListOf<Map<String, Any>>()

        val allGullyMatches = (db.matchDao().getAllMatchesByGully(gullyId) ?: db.matchDao().getAllMatches() ?: emptyList())
            .filterNotNull().sortedBy { it.playedAt }
        val matchNumberMap = allGullyMatches.mapIndexed { idx, m -> m.id to (idx + 1) }.toMap()

        for (match in matches) {
            val matchNum = matchNumberMap[match.id] ?: 1
            val matchDisplayNum = "Match #$matchNum"
            val matchTitle = "${match.teamAName} vs ${match.teamBName}"
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val dateStr = if (match.playedAt > 0) dateFormat.format(Date(match.playedAt)) else "Recorded Match"

            val comm1 = match.commentaryJson1?.filterNotNull() ?: emptyList()
            val comm2 = match.commentaryJson2?.filterNotNull() ?: emptyList()
            val commUnified = match.commentaryJson ?: emptyList()

            val allCommEntries = mutableListOf<Pair<Int, CommentaryEntry>>()
            val seenComm = mutableSetOf<String>()

            comm1.forEach { 
                val key = "1-${it.over}-${it.text?.trim()}"
                if (seenComm.add(key)) allCommEntries.add(1 to it) 
            }
            comm2.forEach { 
                val key = "2-${it.over}-${it.text?.trim()}"
                if (seenComm.add(key)) allCommEntries.add(2 to it) 
            }
            if (comm1.isEmpty() && comm2.isEmpty()) {
                commUnified.filterNotNull().forEach { 
                    val key = "1-${it.over}-${it.text?.trim()}"
                    if (seenComm.add(key)) allCommEntries.add(1 to it) 
                }
            }

            for ((inningsNum, entry) in allCommEntries) {
                val fullText = ((entry.text ?: "") + " " + (entry.baseText ?: "") + " " + entry.userNote).lowercase(Locale.getDefault())
                if (fullText.isBlank()) continue

                var isMatch = false

                if (cleanQuery.isNotBlank() && fullText.contains(cleanQuery)) {
                    isMatch = true
                } else if (queryTokens.isNotEmpty()) {
                    val matchedCount = queryTokens.count { token -> fullText.contains(token) }
                    if (matchedCount > 0) {
                        if (queryTokens.size == 1) {
                            isMatch = true
                        } else if (queryTokens.size <= 3 && matchedCount >= 2) {
                            isMatch = true
                        } else if (queryTokens.size > 3 && matchedCount >= (queryTokens.size / 2)) {
                            isMatch = true
                        }
                    }
                }

                if (playerQuery.isNotBlank() && !fullText.contains(playerQuery)) {
                    isMatch = false
                }

                if (isMatch) {
                    results.add(
                        mapOf<String, Any>(
                            "matchId" to match.id,
                            "matchNumber" to matchNum,
                            "matchDisplayNumber" to matchDisplayNum,
                            "teamAName" to (match.teamAName ?: ""),
                            "teamBName" to (match.teamBName ?: ""),
                            "matchTitle" to matchTitle,
                            "date" to dateStr,
                            "innings" to inningsNum,
                            "over" to (entry.over ?: "0.0"),
                            "commentaryText" to (entry.text ?: entry.baseText ?: ""),
                            "userNote" to entry.userNote,
                            "highlight" to (entry.highlight ?: ""),
                            "type" to (entry.type ?: "BALL")
                        )
                    )
                    if (results.size >= limit) break
                }
            }
            if (results.size >= limit) break
        }

        return results
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
                "findPlayerMatchByKnock" -> findPlayerMatchByKnock(
                    playerName = args["playerName"] as? String ?: "",
                    targetRuns = (args["targetRuns"] as? Number)?.toInt(),
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
                    dateQuery = (args["dateQuery"] as? String) ?: (args["date"] as? String),
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
                    bowlerName = args["bowlerName"] as? String,
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
                "searchCommentary" -> searchCommentary(
                    query = args["query"] as? String ?: "",
                    playerName = args["playerName"] as? String,
                    matchId = args["matchId"] as? String,
                    limit = (args["limit"] as? Number)?.toInt() ?: 20,
                    gullyId = gullyId
                )
                "getPlayerDismissalAnalysis" -> getPlayerDismissalAnalysis(
                    playerName = args["playerName"] as? String ?: "",
                    gullyId = gullyId
                )
                "getPlayerStatsAgainstTeam" -> getPlayerStatsAgainstTeam(
                    playerName = args["playerName"] as? String ?: "",
                    opponentTeam = (args["opponentTeam"] as? String) ?: (args["teamName"] as? String) ?: "",
                    gullyId = gullyId
                )
                "getPlayerPhaseOfPlayStats" -> getPlayerPhaseOfPlayStats(
                    playerName = args["playerName"] as? String ?: "",
                    phase = args["phase"] as? String,
                    gullyId = gullyId
                )
                "getPlayerWinImpactStats" -> getPlayerWinImpactStats(
                    playerName = args["playerName"] as? String ?: "",
                    gullyId = gullyId
                )
                "getPlayerPersonalBests" -> getPlayerPersonalBests(
                    playerName = args["playerName"] as? String ?: "",
                    gullyId = gullyId
                )
                "compareTwoPlayers" -> compareTwoPlayers(
                    player1 = (args["player1"] as? String) ?: (args["playerOne"] as? String) ?: "",
                    player2 = (args["player2"] as? String) ?: (args["playerTwo"] as? String) ?: "",
                    gullyId = gullyId
                )
                "getPlayerBestPartnerships" -> getPlayerBestPartnerships(
                    playerName = args["playerName"] as? String ?: "",
                    partnerName = args["partnerName"] as? String,
                    gullyId = gullyId
                )
                "getPlayerInningsSplit" -> getPlayerInningsSplit(
                    playerName = args["playerName"] as? String ?: "",
                    gullyId = gullyId
                )
                "getPlayerFormStreaks" -> getPlayerFormStreaks(
                    playerName = args["playerName"] as? String ?: "",
                    gullyId = gullyId
                )
                else -> mapOf("error" to "Unknown function: $name")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $name: ${e.message}", e)
            mapOf("error" to "Execution failed: ${e.message}")
        }
    }

    /**
     * 16. Player Dismissal Analysis (Bowled, Caught, LBW, Run Out, Nemesis Bowlers & Catchers)
     */
    fun getPlayerDismissalAnalysis(
        playerName: String,
        gullyId: String = "local"
    ): Map<String, Any> {
        val resolved = resolvePlayerByName(playerName, gullyId)
        val targetPlayerId = resolved.firstOrNull()?.get("playerId") as? String ?: ""
        val actualName = resolved.firstOrNull()?.get("playerName") as? String ?: playerName

        val stats = if (targetPlayerId.isNotBlank()) {
            db.statsDao().getStatsByPlayer(targetPlayerId)?.filterNotNull() ?: emptyList()
        } else {
            val allStats = db.statsDao().getAllStats()?.filterNotNull() ?: emptyList()
            allStats.filter { PlayerNameNormalizer.areNamesMatching(it.playerName, playerName) }
        }

        if (stats.isEmpty()) {
            return mapOf(
                "playerName" to actualName,
                "error" to "No match stats found for player '$actualName'"
            )
        }

        val totalInnings = stats.size
        val outs = stats.filter { it.isOut }
        val timesOut = outs.size
        val notOuts = totalInnings - timesOut

        val dismissalModes = mutableMapOf<String, Int>()
        val nemesisBowlers = mutableMapOf<String, Int>()
        val catchers = mutableMapOf<String, Int>()
        val recentDismissals = mutableListOf<Map<String, Any>>()

        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        for (st in outs) {
            val m = st.matchId?.let { db.matchDao().getMatchById(it) }
            val dateStr = m?.playedAt?.let { try { sdf.format(Date(it)) } catch (_: Exception) { "" } } ?: ""
            val vsTeam = if (m != null) {
                if (st.teamName == m.teamAName) m.teamBName ?: "Opponent" else m.teamAName ?: "Opponent"
            } else "Opponent"

            val info = st.dismissalInfo?.trim() ?: "out"
            val infoLower = info.lowercase(Locale.getDefault())

            val mode = when {
                infoLower.startsWith("b ") || infoLower == "bowled" -> "BOWLED"
                infoLower.startsWith("c ") || infoLower.contains("caught") -> "CAUGHT"
                infoLower.contains("run out") -> "RUN_OUT"
                infoLower.contains("lbw") -> "LBW"
                infoLower.contains("stumped") || infoLower.startsWith("st ") -> "STUMPED"
                infoLower.contains("hit wicket") -> "HIT_WICKET"
                infoLower.contains("retired") -> "RETIRED"
                else -> "OTHER"
            }
            dismissalModes[mode] = (dismissalModes[mode] ?: 0) + 1

            var bowlerName = ""
            var catcherName = ""
            if (infoLower.contains(" b ")) {
                val parts = info.split(" b ", ignoreCase = true)
                bowlerName = parts.lastOrNull()?.trim() ?: ""
                val front = parts.firstOrNull()?.trim() ?: ""
                if (front.startsWith("c &", ignoreCase = true)) {
                    catcherName = bowlerName
                } else if (front.startsWith("c ", ignoreCase = true)) {
                    catcherName = front.substring(2).trim()
                } else if (front.startsWith("st ", ignoreCase = true)) {
                    catcherName = front.substring(3).trim()
                }
            } else if (infoLower.startsWith("b ")) {
                bowlerName = info.substring(2).trim()
            }

            if (bowlerName.isNotBlank() && !bowlerName.equals("bowler", ignoreCase = true)) {
                val key = nemesisBowlers.keys.firstOrNull { PlayerNameNormalizer.areNamesMatching(it, bowlerName) } ?: bowlerName
                nemesisBowlers[key] = (nemesisBowlers[key] ?: 0) + 1
            }
            if (catcherName.isNotBlank() && !catcherName.equals("fielder", ignoreCase = true)) {
                val key = catchers.keys.firstOrNull { PlayerNameNormalizer.areNamesMatching(it, catcherName) } ?: catcherName
                catchers[key] = (catchers[key] ?: 0) + 1
            }

            if (recentDismissals.size < 5) {
                recentDismissals.add(
                    mapOf(
                        "matchId" to (st.matchId ?: ""),
                        "date" to dateStr,
                        "against" to vsTeam,
                        "dismissal" to info,
                        "mode" to mode,
                        "runs" to st.runsScored,
                        "balls" to st.ballsFaced
                    )
                )
            }
        }

        val topBowlers = nemesisBowlers.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { mapOf("bowler" to it.key, "dismissals" to it.value) }

        val topCatchers = catchers.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { mapOf("fielder" to it.key, "catches" to it.value) }

        val notOutPercent = if (totalInnings > 0) (notOuts.toDouble() / totalInnings) * 100 else 0.0

        return mapOf(
            "playerName" to actualName,
            "totalInnings" to totalInnings,
            "timesOut" to timesOut,
            "notOuts" to notOuts,
            "notOutPercentage" to String.format("%.1f%%", notOutPercent),
            "dismissalModes" to dismissalModes,
            "topNemesisBowlers" to topBowlers,
            "topCatchers" to topCatchers,
            "recentDismissals" to recentDismissals
        )
    }

    /**
     * 17. Player Stats Against Specific Opponent Team
     */
    fun getPlayerStatsAgainstTeam(
        playerName: String,
        opponentTeam: String,
        gullyId: String = "local"
    ): Map<String, Any> {
        val resolved = resolvePlayerByName(playerName, gullyId)
        val targetPlayerId = resolved.firstOrNull()?.get("playerId") as? String ?: ""
        val actualName = resolved.firstOrNull()?.get("playerName") as? String ?: playerName

        val allPlayerStats = if (targetPlayerId.isNotBlank()) {
            db.statsDao().getStatsByPlayer(targetPlayerId)?.filterNotNull() ?: emptyList()
        } else {
            val allStats = db.statsDao().getAllStats()?.filterNotNull() ?: emptyList()
            allStats.filter { PlayerNameNormalizer.areNamesMatching(it.playerName, playerName) }
        }

        val targetOpponent = opponentTeam.trim().lowercase(Locale.getDefault())

        val matchedStats = mutableListOf<PlayerMatchStatEntity>()
        val matchesPlayedList = mutableListOf<Map<String, Any>>()
        var wins = 0
        var losses = 0
        var drawsOrAbandoned = 0

        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        for (st in allPlayerStats) {
            val m = st.matchId?.let { db.matchDao().getMatchById(it) } ?: continue
            val opp = if (st.teamName == m.teamAName) m.teamBName ?: "" else m.teamAName ?: ""
            if (opp.lowercase(Locale.getDefault()).contains(targetOpponent)) {
                matchedStats.add(st)

                val res = m.result ?: ""
                val resLower = res.lowercase(Locale.getDefault())
                val myTeam = st.teamName?.lowercase(Locale.getDefault()) ?: ""

                if (resLower.contains("won") && myTeam.isNotBlank() && resLower.contains(myTeam)) {
                    wins++
                } else if (resLower.contains("won") && !resLower.contains(myTeam)) {
                    losses++
                } else {
                    drawsOrAbandoned++
                }

                val dateStr = try { sdf.format(Date(m.playedAt)) } catch (_: Exception) { "" }
                matchesPlayedList.add(
                    mapOf(
                        "matchId" to m.id,
                        "date" to dateStr,
                        "runs" to st.runsScored,
                        "balls" to st.ballsFaced,
                        "isOut" to st.isOut,
                        "wickets" to st.wicketsTaken,
                        "overs" to String.format("%d.%d", st.ballsBowled / 6, st.ballsBowled % 6),
                        "runsConceded" to st.runsConceded,
                        "result" to res
                    )
                )
            }
        }

        if (matchedStats.isEmpty()) {
            return mapOf(
                "playerName" to actualName,
                "opponentTeam" to opponentTeam,
                "matchesPlayed" to 0,
                "message" to "No matches found for $actualName against team '$opponentTeam'"
            )
        }

        val matchesCount = matchedStats.size
        val runs = matchedStats.sumOf { it.runsScored }
        val ballsFaced = matchedStats.sumOf { it.ballsFaced }
        val fours = matchedStats.sumOf { it.fours }
        val sixes = matchedStats.sumOf { it.sixes }
        val timesOut = matchedStats.count { it.isOut }
        val notOuts = matchesCount - timesOut
        val highestScore = matchedStats.maxOfOrNull { it.runsScored } ?: 0

        val battingAvg = if (timesOut > 0) runs.toDouble() / timesOut else runs.toDouble()
        val strikeRate = if (ballsFaced > 0) (runs.toDouble() / ballsFaced) * 100 else 0.0

        val wickets = matchedStats.sumOf { it.wicketsTaken }
        val ballsBowled = matchedStats.sumOf { it.ballsBowled }
        val runsConceded = matchedStats.sumOf { it.runsConceded }
        val maidens = matchedStats.sumOf { it.maidens }
        val dotBalls = matchedStats.sumOf { it.dotBalls }

        val economy = if (ballsBowled > 0) (runsConceded.toDouble() / (ballsBowled / 6.0)) else 0.0
        val bowlingAvg = if (wickets > 0) (runsConceded.toDouble() / wickets) else 0.0

        val catches = matchedStats.sumOf { it.catches }
        val runOuts = matchedStats.sumOf { it.runOuts }

        return mapOf(
            "playerName" to actualName,
            "opponentTeam" to opponentTeam,
            "matchesPlayed" to matchesCount,
            "record" to mapOf("wins" to wins, "losses" to losses, "other" to drawsOrAbandoned),
            "batting" to mapOf(
                "runs" to runs,
                "ballsFaced" to ballsFaced,
                "battingAverage" to String.format("%.2f", battingAvg),
                "strikeRate" to String.format("%.2f", strikeRate),
                "fours" to fours,
                "sixes" to sixes,
                "highestScore" to highestScore,
                "timesOut" to timesOut,
                "notOuts" to notOuts
            ),
            "bowling" to mapOf(
                "wickets" to wickets,
                "ballsBowled" to ballsBowled,
                "overs" to String.format("%d.%d", ballsBowled / 6, ballsBowled % 6),
                "runsConceded" to runsConceded,
                "economy" to String.format("%.2f", economy),
                "bowlingAverage" to String.format("%.2f", bowlingAvg),
                "maidens" to maidens,
                "dotBalls" to dotBalls
            ),
            "fielding" to mapOf("catches" to catches, "runOuts" to runOuts),
            "matchLog" to matchesPlayedList
        )
    }

    /**
     * 18. Phase of Play Performance (Powerplay vs Middle vs Death Overs)
     */
    fun getPlayerPhaseOfPlayStats(
        playerName: String,
        phase: String? = null,
        gullyId: String = "local"
    ): Map<String, Any> {
        val resolved = resolvePlayerByName(playerName, gullyId)
        val actualName = resolved.firstOrNull()?.get("playerName") as? String ?: playerName

        val matches = db.matchDao().getAllMatchesByGully(gullyId)?.filterNotNull() ?: emptyList()

        class PhaseStat {
            var batRuns: Int = 0
            var ballsFaced: Int = 0
            var fours: Int = 0
            var sixes: Int = 0
            var dotBallsFaced: Int = 0
            var dismissals: Int = 0

            var runsConceded: Int = 0
            var ballsBowled: Int = 0
            var wickets: Int = 0
            var dotBallsBowled: Int = 0
        }

        val powerplay = PhaseStat()
        val middle = PhaseStat()
        val death = PhaseStat()

        for (m in matches) {
            val totalOvers = if (m.totalOvers > 0) m.totalOvers else 10
            val ppOversEnd = when {
                totalOvers <= 6 -> 1
                totalOvers <= 10 -> 2
                totalOvers <= 15 -> 4
                else -> 6
            }
            val deathOversStart = when {
                totalOvers <= 6 -> totalOvers - 1
                totalOvers <= 10 -> totalOvers - 2
                totalOvers <= 15 -> totalOvers - 3
                else -> totalOvers - 4
            }

            fun processInnings(balls: List<Ball?>?) {
                if (balls.isNullOrEmpty()) return
                var legalDeliveries = 0
                for (b in balls) {
                    if (b == null) continue
                    val overNum = legalDeliveries / 6

                    val targetPhase = when {
                        overNum < ppOversEnd -> powerplay
                        overNum >= deathOversStart -> death
                        else -> middle
                    }

                    if (PlayerNameNormalizer.areNamesMatching(b.batsmanName, actualName)) {
                        targetPhase.batRuns += b.batRuns
                        if (b.type != BallType.WIDE) {
                            targetPhase.ballsFaced++
                        }
                        if (b.isBoundary && b.overthrowRuns == 0) {
                            if (b.batRuns == 4) targetPhase.fours++
                            if (b.batRuns == 6) targetPhase.sixes++
                        }
                        if (b.runs == 0) targetPhase.dotBallsFaced++
                        if (b.isWicket && PlayerNameNormalizer.areNamesMatching(b.outPlayerName, actualName)) {
                            targetPhase.dismissals++
                        }
                    }

                    if (PlayerNameNormalizer.areNamesMatching(b.bowlerName, actualName)) {
                        targetPhase.runsConceded += b.runs
                        if (b.type != BallType.WIDE && b.type != BallType.NO_BALL) {
                            targetPhase.ballsBowled++
                        }
                        if (b.isWicket) {
                            targetPhase.wickets++
                        }
                        if (b.runs == 0) targetPhase.dotBallsBowled++
                    }

                    if (b.type != BallType.WIDE && b.type != BallType.NO_BALL) {
                        legalDeliveries++
                    }
                }
            }

            processInnings(m.ballsJson1)
            processInnings(m.ballsJson2)
        }

        fun toMap(p: PhaseStat, phaseName: String): Map<String, Any> {
            val sr = if (p.ballsFaced > 0) (p.batRuns.toDouble() / p.ballsFaced) * 100 else 0.0
            val avg = if (p.dismissals > 0) p.batRuns.toDouble() / p.dismissals else p.batRuns.toDouble()
            val econ = if (p.ballsBowled > 0) (p.runsConceded.toDouble() / (p.ballsBowled / 6.0)) else 0.0
            return mapOf(
                "phase" to phaseName,
                "batting" to mapOf(
                    "runs" to p.batRuns,
                    "ballsFaced" to p.ballsFaced,
                    "strikeRate" to String.format("%.2f", sr),
                    "average" to String.format("%.2f", avg),
                    "fours" to p.fours,
                    "sixes" to p.sixes,
                    "dotBalls" to p.dotBallsFaced,
                    "dismissals" to p.dismissals
                ),
                "bowling" to mapOf(
                    "wickets" to p.wickets,
                    "ballsBowled" to p.ballsBowled,
                    "overs" to String.format("%d.%d", p.ballsBowled / 6, p.ballsBowled % 6),
                    "runsConceded" to p.runsConceded,
                    "economy" to String.format("%.2f", econ),
                    "dotBalls" to p.dotBallsBowled
                )
            )
        }

        val filterPhase = phase?.uppercase(Locale.getDefault())
        return mapOf(
            "playerName" to actualName,
            "filter" to (filterPhase ?: "ALL"),
            "phases" to when (filterPhase) {
                "POWERPLAY" -> mapOf("POWERPLAY" to toMap(powerplay, "Powerplay"))
                "MIDDLE" -> mapOf("MIDDLE" to toMap(middle, "Middle Overs"))
                "DEATH" -> mapOf("DEATH" to toMap(death, "Death Overs"))
                else -> mapOf(
                    "POWERPLAY" to toMap(powerplay, "Powerplay (Early Overs)"),
                    "MIDDLE" to toMap(middle, "Middle Overs"),
                    "DEATH" to toMap(death, "Death Overs (Final Overs)")
                )
            }
        )
    }

    /**
     * 19. Player Win Impact & Match Contribution
     */
    fun getPlayerWinImpactStats(
        playerName: String,
        gullyId: String = "local"
    ): Map<String, Any> {
        val resolved = resolvePlayerByName(playerName, gullyId)
        val targetPlayerId = resolved.firstOrNull()?.get("playerId") as? String ?: ""
        val actualName = resolved.firstOrNull()?.get("playerName") as? String ?: playerName

        val stats = if (targetPlayerId.isNotBlank()) {
            db.statsDao().getStatsByPlayer(targetPlayerId)?.filterNotNull() ?: emptyList()
        } else {
            val allStats = db.statsDao().getAllStats()?.filterNotNull() ?: emptyList()
            allStats.filter { PlayerNameNormalizer.areNamesMatching(it.playerName, playerName) }
        }

        if (stats.isEmpty()) {
            return mapOf(
                "playerName" to actualName,
                "message" to "No matches found for player $actualName"
            )
        }

        var totalMatches = 0
        var totalWins = 0
        var totalLosses = 0

        var matchesScored25Plus = 0
        var winsWhenScored25Plus = 0

        var matchesTook2PlusWickets = 0
        var winsWhenTook2PlusWickets = 0

        val potmMatches = mutableListOf<Map<String, Any>>()
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        for (st in stats) {
            val m = st.matchId?.let { db.matchDao().getMatchById(it) } ?: continue
            totalMatches++

            val myTeam = st.teamName?.lowercase(Locale.getDefault()) ?: ""
            val result = m.result ?: ""
            val resultLower = result.lowercase(Locale.getDefault())

            val isWin = resultLower.contains("won") && myTeam.isNotBlank() && resultLower.contains(myTeam)
            val isLoss = resultLower.contains("won") && !resultLower.contains(myTeam)

            if (isWin) totalWins++
            else if (isLoss) totalLosses++

            if (st.runsScored >= 25) {
                matchesScored25Plus++
                if (isWin) winsWhenScored25Plus++
            }

            if (st.wicketsTaken >= 2) {
                matchesTook2PlusWickets++
                if (isWin) winsWhenTook2PlusWickets++
            }

            if (PlayerNameNormalizer.areNamesMatching(m.playerOfTheMatchName, actualName)) {
                val dateStr = try { sdf.format(Date(m.playedAt)) } catch (_: Exception) { "" }
                potmMatches.add(
                    mapOf(
                        "matchId" to m.id,
                        "date" to dateStr,
                        "match" to "${m.teamAName} vs ${m.teamBName}",
                        "result" to result,
                        "runs" to st.runsScored,
                        "wickets" to st.wicketsTaken
                    )
                )
            }
        }

        val winPct = if (totalMatches > 0) (totalWins.toDouble() / totalMatches) * 100 else 0.0
        val winPct25Plus = if (matchesScored25Plus > 0) (winsWhenScored25Plus.toDouble() / matchesScored25Plus) * 100 else 0.0
        val winPct2Wkts = if (matchesTook2PlusWickets > 0) (winsWhenTook2PlusWickets.toDouble() / matchesTook2PlusWickets) * 100 else 0.0

        return mapOf(
            "playerName" to actualName,
            "totalMatches" to totalMatches,
            "wins" to totalWins,
            "losses" to totalLosses,
            "winPercentage" to String.format("%.1f%%", winPct),
            "impactWhenScoring25Plus" to mapOf(
                "matches" to matchesScored25Plus,
                "wins" to winsWhenScored25Plus,
                "winPercentage" to String.format("%.1f%%", winPct25Plus)
            ),
            "impactWhenTaking2PlusWickets" to mapOf(
                "matches" to matchesTook2PlusWickets,
                "wins" to winsWhenTook2PlusWickets,
                "winPercentage" to String.format("%.1f%%", winPct2Wkts)
            ),
            "playerOfTheMatchAwards" to mapOf(
                "totalAwards" to potmMatches.size,
                "matches" to potmMatches
            )
        )
    }

    /**
     * 20. Player Personal Records and Bests
     */
    fun getPlayerPersonalBests(
        playerName: String,
        gullyId: String = "local"
    ): Map<String, Any> {
        val resolved = resolvePlayerByName(playerName, gullyId)
        val targetPlayerId = resolved.firstOrNull()?.get("playerId") as? String ?: ""
        val actualName = resolved.firstOrNull()?.get("playerName") as? String ?: playerName

        val stats = if (targetPlayerId.isNotBlank()) {
            db.statsDao().getStatsByPlayer(targetPlayerId)?.filterNotNull() ?: emptyList()
        } else {
            val allStats = db.statsDao().getAllStats()?.filterNotNull() ?: emptyList()
            allStats.filter { PlayerNameNormalizer.areNamesMatching(it.playerName, playerName) }
        }

        if (stats.isEmpty()) {
            return mapOf(
                "playerName" to actualName,
                "message" to "No stats found for player $actualName"
            )
        }

        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        val bestBat = stats.maxWithOrNull(
            compareBy<PlayerMatchStatEntity> { it.runsScored }
                .thenByDescending { !it.isOut }
                .thenBy { if (it.ballsFaced > 0) it.ballsFaced else 999 }
        )
        val bestBatMatch = bestBat?.matchId?.let { db.matchDao().getMatchById(it) }

        val bowlingInnings = stats.filter { it.ballsBowled > 0 }
        val bestBowl = bowlingInnings.maxWithOrNull(
            compareBy<PlayerMatchStatEntity> { it.wicketsTaken }
                .thenByDescending { -it.runsConceded }
        )
        val bestBowlMatch = bestBowl?.matchId?.let { db.matchDao().getMatchById(it) }

        val mostSixesStat = stats.maxByOrNull { it.sixes }
        val mostSixesMatch = mostSixesStat?.matchId?.let { db.matchDao().getMatchById(it) }

        val mostFoursStat = stats.maxByOrNull { it.fours }
        val mostCatchesStat = stats.maxByOrNull { it.catches }

        return mapOf(
            "playerName" to actualName,
            "bestBattingInnings" to mapOf(
                "runs" to (bestBat?.runsScored ?: 0),
                "ballsFaced" to (bestBat?.ballsFaced ?: 0),
                "isNotOut" to (bestBat?.isOut == false),
                "strikeRate" to if ((bestBat?.ballsFaced ?: 0) > 0) String.format("%.2f", (bestBat!!.runsScored.toDouble() / bestBat.ballsFaced) * 100) else "0.0",
                "fours" to (bestBat?.fours ?: 0),
                "sixes" to (bestBat?.sixes ?: 0),
                "vsMatch" to "${bestBatMatch?.teamAName ?: "Team A"} vs ${bestBatMatch?.teamBName ?: "Team B"}",
                "date" to (bestBatMatch?.playedAt?.let { try { sdf.format(Date(it)) } catch (_: Exception) { "" } } ?: ""),
                "matchId" to (bestBat?.matchId ?: "")
            ),
            "bestBowlingSpell" to mapOf(
                "wickets" to (bestBowl?.wicketsTaken ?: 0),
                "runsConceded" to (bestBowl?.runsConceded ?: 0),
                "overs" to (bestBowl?.let { String.format("%d.%d", it.ballsBowled / 6, it.ballsBowled % 6) } ?: "0.0"),
                "maidens" to (bestBowl?.maidens ?: 0),
                "dotBalls" to (bestBowl?.dotBalls ?: 0),
                "economy" to (bestBowl?.let { if (it.ballsBowled > 0) String.format("%.2f", it.runsConceded.toDouble() / (it.ballsBowled / 6.0)) else "0.0" } ?: "0.0"),
                "vsMatch" to "${bestBowlMatch?.teamAName ?: "Team A"} vs ${bestBowlMatch?.teamBName ?: "Team B"}",
                "date" to (bestBowlMatch?.playedAt?.let { try { sdf.format(Date(it)) } catch (_: Exception) { "" } } ?: ""),
                "matchId" to (bestBowl?.matchId ?: "")
            ),
            "mostSixesInInnings" to mapOf(
                "sixes" to (mostSixesStat?.sixes ?: 0),
                "totalRuns" to (mostSixesStat?.runsScored ?: 0),
                "match" to "${mostSixesMatch?.teamAName ?: "Team A"} vs ${mostSixesMatch?.teamBName ?: "Team B"}",
                "matchId" to (mostSixesStat?.matchId ?: "")
            ),
            "mostFoursInInnings" to mapOf(
                "fours" to (mostFoursStat?.fours ?: 0),
                "totalRuns" to (mostFoursStat?.runsScored ?: 0),
                "matchId" to (mostFoursStat?.matchId ?: "")
            ),
            "mostCatchesInMatch" to mapOf(
                "catches" to (mostCatchesStat?.catches ?: 0),
                "matchId" to (mostCatchesStat?.matchId ?: "")
            )
        )
    }

    /**
     * 21. Head-to-Head Comparison of Two Players
     */
    fun compareTwoPlayers(
        player1: String,
        player2: String,
        gullyId: String = "local"
    ): Map<String, Any> {
        val resolvedP1 = resolvePlayerByName(player1, gullyId)
        val p1Id = resolvedP1.firstOrNull()?.get("playerId") as? String ?: ""
        val p1Name = resolvedP1.firstOrNull()?.get("playerName") as? String ?: player1

        val resolvedP2 = resolvePlayerByName(player2, gullyId)
        val p2Id = resolvedP2.firstOrNull()?.get("playerId") as? String ?: ""
        val p2Name = resolvedP2.firstOrNull()?.get("playerName") as? String ?: player2

        val stats1 = if (p1Id.isNotBlank()) getPlayerStats(p1Id) else mapOf("playerName" to p1Name, "matchesPlayed" to 0)
        val stats2 = if (p2Id.isNotBlank()) getPlayerStats(p2Id) else mapOf("playerName" to p2Name, "matchesPlayed" to 0)

        val p1BatVsP2Bowl = getBatterVsBowlerMatchup(batterName = p1Name, bowlerName = p2Name, gullyId = gullyId)
        val p2BatVsP1Bowl = getBatterVsBowlerMatchup(batterName = p2Name, bowlerName = p1Name, gullyId = gullyId)

        return mapOf(
            "comparison" to "$p1Name vs $p2Name",
            "player1" to mapOf(
                "name" to p1Name,
                "id" to p1Id,
                "stats" to stats1
            ),
            "player2" to mapOf(
                "name" to p2Name,
                "id" to p2Id,
                "stats" to stats2
            ),
            "headToHead" to mapOf(
                "${p1Name}_batting_vs_${p2Name}_bowling" to p1BatVsP2Bowl,
                "${p2Name}_batting_vs_${p1Name}_bowling" to p2BatVsP1Bowl
            )
        )
    }

    /**
     * 22. Batting Partnership Chemistry and Records
     */
    fun getPlayerBestPartnerships(
        playerName: String,
        partnerName: String? = null,
        gullyId: String = "local"
    ): Map<String, Any> {
        val resolved = resolvePlayerByName(playerName, gullyId)
        val actualName = resolved.firstOrNull()?.get("playerName") as? String ?: playerName

        val matches = db.matchDao().getAllMatchesByGully(gullyId)?.filterNotNull() ?: emptyList()

        class PartnershipRecord(
            val partner: String,
            var totalRuns: Int = 0,
            var totalBalls: Int = 0,
            var standsCount: Int = 0,
            var highestStand: Int = 0,
            var highestStandBalls: Int = 0,
            var playerContributions: Int = 0,
            var partnerContributions: Int = 0
        )

        val partnerMap = mutableMapOf<String, PartnershipRecord>()
        val allStandsList = mutableListOf<Map<String, Any>>()
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        val targetPartner = partnerName?.trim()

        for (m in matches) {
            val dateStr = try { sdf.format(Date(m.playedAt)) } catch (_: Exception) { "" }
            val matchTitle = "${m.teamAName ?: "Team A"} vs ${m.teamBName ?: "Team B"}"

            fun inspectPartnerships(list: List<PartnershipEvent?>?, inningsNum: Int) {
                if (list.isNullOrEmpty()) return
                for (p in list) {
                    if (p == null) continue
                    val isB1 = PlayerNameNormalizer.areNamesMatching(p.batter1, actualName)
                    val isB2 = PlayerNameNormalizer.areNamesMatching(p.batter2, actualName)

                    if (!isB1 && !isB2) continue

                    val partnerActual = if (isB1) p.batter2 ?: "Partner" else p.batter1 ?: "Partner"

                    if (!targetPartner.isNullOrBlank() && !PlayerNameNormalizer.areNamesMatching(partnerActual, targetPartner)) {
                        continue
                    }

                    val playerRuns = if (isB1) p.b1Runs else p.b2Runs
                    val partnerRuns = if (isB1) p.b2Runs else p.b1Runs
                    val playerBalls = if (isB1) p.b1Balls else p.b2Balls
                    val partnerBalls = if (isB1) p.b2Balls else p.b1Balls

                    allStandsList.add(
                        mapOf(
                            "runs" to p.runs,
                            "balls" to p.balls,
                            "partner" to partnerActual,
                            "playerRuns" to playerRuns,
                            "playerBalls" to playerBalls,
                            "partnerRuns" to partnerRuns,
                            "partnerBalls" to partnerBalls,
                            "innings" to inningsNum,
                            "match" to matchTitle,
                            "date" to dateStr,
                            "matchId" to m.id
                        )
                    )

                    val key = partnerMap.keys.firstOrNull { PlayerNameNormalizer.areNamesMatching(it, partnerActual) } ?: partnerActual
                    val record = partnerMap.getOrPut(key) { PartnershipRecord(key) }
                    record.totalRuns += p.runs
                    record.totalBalls += p.balls
                    record.standsCount++
                    record.playerContributions += playerRuns
                    record.partnerContributions += partnerRuns
                    if (p.runs > record.highestStand) {
                        record.highestStand = p.runs
                        record.highestStandBalls = p.balls
                    }
                }
            }

            inspectPartnerships(m.pshipJson1, 1)
            inspectPartnerships(m.pshipJson2, 2)
        }

        val topStands = allStandsList.sortedByDescending { it["runs"] as Int }.take(10)

        val partnerSummary = partnerMap.values.map { r ->
            val avgStand = if (r.standsCount > 0) r.totalRuns.toDouble() / r.standsCount else 0.0
            mapOf(
                "partner" to r.partner,
                "totalRunsTogether" to r.totalRuns,
                "totalBallsTogether" to r.totalBalls,
                "inningsTogether" to r.standsCount,
                "averageStand" to String.format("%.1f", avgStand),
                "highestStand" to "${r.highestStand} (${r.highestStandBalls} balls)",
                "playerRuns" to r.playerContributions,
                "partnerRuns" to r.partnerContributions
            )
        }.sortedByDescending { it["totalRunsTogether"] as Int }

        return mapOf(
            "playerName" to actualName,
            "filterPartner" to (targetPartner ?: "ALL"),
            "totalPartnershipsRecorded" to allStandsList.size,
            "topPartnershipInnings" to topStands,
            "bestPartners" to partnerSummary
        )
    }

    /**
     * 23. 1st Innings (Setting Target) vs 2nd Innings (Chasing Target) Split
     */
    fun getPlayerInningsSplit(
        playerName: String,
        gullyId: String = "local"
    ): Map<String, Any> {
        val resolved = resolvePlayerByName(playerName, gullyId)
        val actualName = resolved.firstOrNull()?.get("playerName") as? String ?: playerName

        val matches = db.matchDao().getAllMatchesByGully(gullyId)?.filterNotNull() ?: emptyList()

        class InningsSplitStat {
            var matchesPlayed: Int = 0
            var batRuns: Int = 0
            var ballsFaced: Int = 0
            var fours: Int = 0
            var sixes: Int = 0
            var dismissals: Int = 0
            var notOuts: Int = 0
            var highestScore: Int = 0

            var ballsBowled: Int = 0
            var runsConceded: Int = 0
            var wickets: Int = 0
            var dotBalls: Int = 0

            var wins: Int = 0
            var losses: Int = 0
        }

        val firstInnings = InningsSplitStat()
        val secondInnings = InningsSplitStat()

        for (m in matches) {
            fun processMatchInnings(balls: List<Ball?>?, statObj: InningsSplitStat, isTeamFirstInnings: Boolean) {
                if (balls.isNullOrEmpty()) return
                var battedOrBowledInThisInnings = false
                var myInningsRuns = 0
                var myInningsBalls = 0
                var myInningsOut = false

                for (b in balls) {
                    if (b == null) continue
                    if (PlayerNameNormalizer.areNamesMatching(b.batsmanName, actualName)) {
                        battedOrBowledInThisInnings = true
                        statObj.batRuns += b.batRuns
                        myInningsRuns += b.batRuns
                        if (b.type != BallType.WIDE) {
                            statObj.ballsFaced++
                            myInningsBalls++
                        }
                        if (b.isBoundary && b.overthrowRuns == 0) {
                            if (b.batRuns == 4) statObj.fours++
                            if (b.batRuns == 6) statObj.sixes++
                        }
                        if (b.isWicket && PlayerNameNormalizer.areNamesMatching(b.outPlayerName, actualName)) {
                            statObj.dismissals++
                            myInningsOut = true
                        }
                    }

                    if (PlayerNameNormalizer.areNamesMatching(b.bowlerName, actualName)) {
                        battedOrBowledInThisInnings = true
                        statObj.runsConceded += b.runs
                        if (b.type != BallType.WIDE && b.type != BallType.NO_BALL) {
                            statObj.ballsBowled++
                        }
                        if (b.isWicket) {
                            statObj.wickets++
                        }
                        if (b.runs == 0) {
                            statObj.dotBalls++
                        }
                    }
                }

                if (battedOrBowledInThisInnings) {
                    statObj.matchesPlayed++
                    if (myInningsBalls > 0 && !myInningsOut) {
                        statObj.notOuts++
                    }
                    if (myInningsRuns > statObj.highestScore) {
                        statObj.highestScore = myInningsRuns
                    }

                    val res = m.result?.lowercase(Locale.getDefault()) ?: ""
                    if (res.contains("won")) {
                        val pBatTeam = if (isTeamFirstInnings) m.firstInningsTeam else m.secondInningsTeam
                        if (!pBatTeam.isNullOrBlank() && res.contains(pBatTeam.lowercase(Locale.getDefault()))) {
                            statObj.wins++
                        } else {
                            statObj.losses++
                        }
                    }
                }
            }

            processMatchInnings(m.ballsJson1, firstInnings, true)
            processMatchInnings(m.ballsJson2, secondInnings, false)
        }

        fun toSplitMap(s: InningsSplitStat, contextName: String): Map<String, Any> {
            val sr = if (s.ballsFaced > 0) (s.batRuns.toDouble() / s.ballsFaced) * 100 else 0.0
            val avg = if (s.dismissals > 0) s.batRuns.toDouble() / s.dismissals else s.batRuns.toDouble()
            val econ = if (s.ballsBowled > 0) (s.runsConceded.toDouble() / (s.ballsBowled / 6.0)) else 0.0
            val bowlAvg = if (s.wickets > 0) s.runsConceded.toDouble() / s.wickets else 0.0
            val winPct = if (s.matchesPlayed > 0) (s.wins.toDouble() / s.matchesPlayed) * 100 else 0.0

            return mapOf(
                "context" to contextName,
                "matchesInvolved" to s.matchesPlayed,
                "winPercentage" to String.format("%.1f%%", winPct),
                "wins" to s.wins,
                "losses" to s.losses,
                "batting" to mapOf(
                    "runs" to s.batRuns,
                    "ballsFaced" to s.ballsFaced,
                    "strikeRate" to String.format("%.2f", sr),
                    "average" to String.format("%.2f", avg),
                    "highestScore" to s.highestScore,
                    "fours" to s.fours,
                    "sixes" to s.sixes,
                    "dismissals" to s.dismissals,
                    "notOuts" to s.notOuts
                ),
                "bowling" to mapOf(
                    "wickets" to s.wickets,
                    "overs" to String.format("%d.%d", s.ballsBowled / 6, s.ballsBowled % 6),
                    "runsConceded" to s.runsConceded,
                    "economy" to String.format("%.2f", econ),
                    "bowlingAverage" to String.format("%.2f", bowlAvg),
                    "dotBalls" to s.dotBalls
                )
            )
        }

        return mapOf(
            "playerName" to actualName,
            "firstInningsSettingTarget" to toSplitMap(firstInnings, "1st Innings (Setting Target / Bowling First)"),
            "secondInningsChasingTarget" to toSplitMap(secondInnings, "2nd Innings (Chasing Target / Defending Total)")
        )
    }

    /**
     * 24. Player Form Streaks and Upcoming Career Milestones
     */
    fun getPlayerFormStreaks(
        playerName: String,
        gullyId: String = "local"
    ): Map<String, Any> {
        val resolved = resolvePlayerByName(playerName, gullyId)
        val targetPlayerId = resolved.firstOrNull()?.get("playerId") as? String ?: ""
        val actualName = resolved.firstOrNull()?.get("playerName") as? String ?: playerName

        val stats = if (targetPlayerId.isNotBlank()) {
            db.statsDao().getStatsByPlayer(targetPlayerId)?.filterNotNull() ?: emptyList()
        } else {
            val allStats = db.statsDao().getAllStats()?.filterNotNull() ?: emptyList()
            allStats.filter { PlayerNameNormalizer.areNamesMatching(it.playerName, playerName) }
        }

        if (stats.isEmpty()) {
            return mapOf(
                "playerName" to actualName,
                "message" to "No stats found for player $actualName"
            )
        }

        val sortedStats = stats.sortedBy { st ->
            st.matchId?.let { db.matchDao().getMatchById(it)?.playedAt } ?: 0L
        }

        var current20PlusStreak = 0
        var best20PlusStreak = 0
        var temp20Plus = 0

        var currentWicketStreak = 0
        var bestWicketStreak = 0
        var tempWickets = 0

        var currentBoundaryStreak = 0
        var bestBoundaryStreak = 0
        var tempBoundary = 0

        for (st in sortedStats) {
            if (st.runsScored >= 20) {
                temp20Plus++
                if (temp20Plus > best20PlusStreak) best20PlusStreak = temp20Plus
            } else {
                temp20Plus = 0
            }

            if (st.wicketsTaken >= 1) {
                tempWickets++
                if (tempWickets > bestWicketStreak) bestWicketStreak = tempWickets
            } else if (st.ballsBowled > 0) {
                tempWickets = 0
            }

            if (st.fours > 0 || st.sixes > 0) {
                tempBoundary++
                if (tempBoundary > bestBoundaryStreak) bestBoundaryStreak = tempBoundary
            } else if (st.ballsFaced > 0) {
                tempBoundary = 0
            }
        }

        for (st in sortedStats.reversed()) {
            if (st.runsScored >= 20) current20PlusStreak++ else break
        }
        for (st in sortedStats.reversed()) {
            if (st.wicketsTaken >= 1) currentWicketStreak++ else if (st.ballsBowled > 0) break
        }
        for (st in sortedStats.reversed()) {
            if (st.fours > 0 || st.sixes > 0) currentBoundaryStreak++ else if (st.ballsFaced > 0) break
        }

        val totalCareerRuns = sortedStats.sumOf { it.runsScored }
        val nextBattingMilestone = when {
            totalCareerRuns < 50 -> 50
            totalCareerRuns < 100 -> 100
            totalCareerRuns < 250 -> 250
            totalCareerRuns < 500 -> 500
            totalCareerRuns < 1000 -> 1000
            else -> ((totalCareerRuns / 500) + 1) * 500
        }
        val runsNeeded = nextBattingMilestone - totalCareerRuns

        val totalCareerWickets = sortedStats.sumOf { it.wicketsTaken }
        val nextBowlingMilestone = when {
            totalCareerWickets < 10 -> 10
            totalCareerWickets < 25 -> 25
            totalCareerWickets < 50 -> 50
            totalCareerWickets < 100 -> 100
            else -> ((totalCareerWickets / 50) + 1) * 50
        }
        val wicketsNeeded = nextBowlingMilestone - totalCareerWickets

        return mapOf(
            "playerName" to actualName,
            "totalMatchesPlayed" to sortedStats.size,
            "streaks" to mapOf(
                "scoring20PlusRuns" to mapOf(
                    "currentStreak" to "$current20PlusStreak matches",
                    "bestCareerStreak" to "$best20PlusStreak matches"
                ),
                "takingWickets" to mapOf(
                    "currentStreak" to "$currentWicketStreak matches",
                    "bestCareerStreak" to "$bestWicketStreak matches"
                ),
                "hittingBoundaries" to mapOf(
                    "currentStreak" to "$currentBoundaryStreak matches",
                    "bestCareerStreak" to "$bestBoundaryStreak matches"
                )
            ),
            "upcomingMilestones" to mapOf(
                "batting" to mapOf(
                    "currentTotalRuns" to totalCareerRuns,
                    "targetMilestone" to "$nextBattingMilestone runs",
                    "runsNeeded" to runsNeeded
                ),
                "bowling" to mapOf(
                    "currentTotalWickets" to totalCareerWickets,
                    "targetMilestone" to "$nextBowlingMilestone wickets",
                    "wicketsNeeded" to wicketsNeeded
                )
            )
        )
    }
}



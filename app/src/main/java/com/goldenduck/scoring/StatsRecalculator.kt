package com.goldenduck.scoring

import android.content.Context
import android.util.Log

object StatsRecalculator {

    fun recalculateAndSave(context: Context, matchId: String, onComplete: () -> Unit) {
        if (!RemoteConfigManager.isFeatureEnabled(RemoteConfigManager.KEY_ENABLE_STATS_RECALCULATE)) {
            Log.w("RECALC", "Stats recalculation is currently disabled via Remote Config.")
            onComplete()
            return
        }
        AppDatabase.ioExecutor.execute {
            try {
                val db = AppDatabase.getInstance(context)
                val entity = db.matchDao().getMatchById(matchId) ?: return@execute
                val m = entity.toMatch()
                
                // 1. Fetch existing stats to preserve non-derivable data (like minutesPlayed)
                val existingStats = db.statsDao().getStatsByMatch(matchId) ?: emptyList()
                val existingStatsMap = existingStats.filterNotNull().associateBy { it.playerName?.trim() }

                val nameToId = entity.nameToIdMap ?: emptyMap<String, String>()
                val gId = entity.gullyId // Preserve gully isolation during recalculation
                val nameNormalizedMap = mutableMapOf<String, Player>()

                fun getP(name: String?): Player {
                    val rawN = name?.trim() ?: "Unknown"
                    val normKey = PlayerNameNormalizer.normalize(rawN)
                    return nameNormalizedMap.getOrPut(normKey) { 
                        Player(rawN).apply { 
                            val existingRec = existingStatsMap[rawN] ?: existingStatsMap.values.find { 
                                PlayerNameNormalizer.normalize(it.playerName) == normKey 
                            }
                            val resolvedId: String? = existingRec?.playerId
                                ?: nameToId[rawN]
                                ?: nameToId.entries.find { PlayerNameNormalizer.normalize(it.key) == normKey }?.value
                                ?: db.playerDao().getPlayerByNameByGully(rawN, gId)?.id
                                ?: db.playerDao().getPlayerByName(rawN)?.id
                            this.id = resolvedId
                            this.gullyId = gId
                            // Restore time data from existing record if available
                            existingRec?.let { old ->
                                this.minutesPlayed = old.minutesPlayed
                                this.entryTime = old.entryTime
                                this.exitTime = old.exitTime
                            }
                        } 
                    }
                }

                // Initialize playerMap with ALL squad members to preserve "Matches Played" count
                entity.teamANames?.filterNotNull()?.forEach { getP(it) }
                entity.teamBNames?.filterNotNull()?.forEach { getP(it) }

                fun processInnings(inn: Innings?) {
                    if (inn == null) return
                    
                    // Reset stats that are built ball-by-ball
                    inn.balls.forEach { b ->
                        val batsman = getP(b.batsmanName)
                        val bowler = getP(b.bowlerName)
                        val nonStriker = if (b.nonStrikerName != null) getP(b.nonStrikerName) else null

                        // Batting
                        batsman.ballsFaced += if (b.type != BallType.WIDE) 1 else 0
                        batsman.runsScored += b.batRuns
                        if (b.batRuns == 4) batsman.fours++
                        else if (b.batRuns == 6) {
                            batsman.sixes++
                            bowler.sixesConceded++
                        }

                        // Bowling
                        bowler.runsConceded += if (b.type == BallType.NORMAL || b.type == BallType.WIDE || b.type == BallType.NO_BALL) b.runs else 0
                        bowler.ballsBowled += if (b.type == BallType.NORMAL || b.type == BallType.BYE || b.type == BallType.LEG_BYE) 1 else 0
                        if (b.type == BallType.NORMAL && b.runs == 0) bowler.dotBalls++
                        if (b.type == BallType.WIDE) bowler.widesConceded += b.runs
                        if (b.type == BallType.NO_BALL) {
                            val extraR = b.runs - b.batRuns
                            bowler.noBallsConceded += extraR
                        }

                        if (b.isWicket) {
                            val outP = getP(b.outPlayerName ?: b.batsmanName)
                            
                            val rawDInfo = (b.dismissalInfo ?: "").trim()
                            val commText = inn.commentary.filterNotNull().find { c ->
                                val t = (c.text ?: c.baseText ?: "").lowercase()
                                (t.contains("run out") || t.contains("caught") || t.contains("bowled") || t.contains("out")) &&
                                (b.batsmanName != null && t.contains(b.batsmanName.toString().lowercase()))
                            }?.let { it.text ?: it.baseText } ?: ""
                            
                            val effectiveDismissal = if (rawDInfo.isNotEmpty() && rawDInfo != "out") rawDInfo else commText
                            outP.dismissalInfo = if (rawDInfo.isNotEmpty()) rawDInfo else "out"

                            val isRO = effectiveDismissal.contains("Run Out", ignoreCase = true) || (b.fielderName?.contains(" / ") == true)
                            val isObstruct = effectiveDismissal.contains("Obstruct", ignoreCase = true)
                            val isRetiredHurt = effectiveDismissal.contains("retired hurt", ignoreCase = true)
                            val isRetiredOut = effectiveDismissal.contains("retired out", ignoreCase = true) || (effectiveDismissal.contains("retired", ignoreCase = true) && !isRetiredHurt)

                            outP.isOut = !isRetiredHurt

                            val isBowlerWicket = b.isWicket && !isRO && !isObstruct && !isRetiredHurt && !isRetiredOut
                            if (isBowlerWicket) bowler.wicketsTaken++

                            // Fielding
                            val isStumped = effectiveDismissal.contains("Stumped", ignoreCase = true) || effectiveDismissal.startsWith("st ", ignoreCase = true)
                            val isCatch = !isRO && !isStumped && (effectiveDismissal.contains("Caught", ignoreCase = true) || effectiveDismissal.startsWith("c ", ignoreCase = true) || effectiveDismissal.contains("c & b", ignoreCase = true))

                            var fielderNames = b.fielderName?.split(" / ")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                            if (fielderNames.isEmpty() && b.isWicket) {
                                if (effectiveDismissal.contains("c & b ", ignoreCase = true) || effectiveDismissal.contains("c & b", ignoreCase = true)) {
                                    bowler.name?.let { fielderNames = listOf(it) }
                                } else if (effectiveDismissal.contains("Caught by ", ignoreCase = true)) {
                                    val fName = effectiveDismissal.substringAfter("Caught by ").substringBefore("\n").substringBefore(" b ").trim()
                                    if (fName.isNotEmpty()) fielderNames = listOf(fName)
                                } else if (effectiveDismissal.startsWith("c ", ignoreCase = true) || effectiveDismissal.contains(" c ")) {
                                    val afterC = if (effectiveDismissal.startsWith("c ")) effectiveDismissal.substringAfter("c ") else effectiveDismissal.substringAfter(" c ")
                                    val fName = afterC.substringBefore(" b ").substringBefore("\n").trim()
                                    if (fName.isNotEmpty() && fName != "b") fielderNames = listOf(fName)
                                }
                            }

                            fielderNames.forEach { fName ->
                                val f = getP(fName)
                                if (isCatch) {
                                    f.catches++
                                } else if (isStumped) {
                                    f.stumpings++
                                } else if (isRO) {
                                    f.runOuts++
                                }
                            }
                        }
                    }

                    // Maidens and Hattricks require over-by-over / streak analysis
                    processMilestones(inn, getP = { getP(it) })
                }

                processInnings(m.firstInnings)
                processInnings(m.secondInnings)

                // Auto-calculate POTM if missing or TBD on finished match
                if (entity.isFinished && !entity.isAbandoned) {
                    var bestPlayerName: String? = entity.playerOfTheMatchName
                    if (bestPlayerName.isNullOrEmpty() || bestPlayerName == "TBD") {
                        var maxPts = -1.0
                        for (p in nameNormalizedMap.values) {
                            val pts = PointsCalculator.calculatePlayerPoints(p)
                            if (pts > maxPts && pts > 0) {
                                maxPts = pts
                                bestPlayerName = p.name
                            }
                        }
                        if (!bestPlayerName.isNullOrEmpty()) {
                            entity.playerOfTheMatchName = bestPlayerName
                            db.matchDao().updateMatch(entity)
                        }
                    }
                }

                // Save back to DB (Delete old stats for this match first to avoid duplicate records)
                db.runInTransaction {
                    db.statsDao().deleteStatsByMatch(matchId)
                    for (p in nameNormalizedMap.values) {
                        val teamName = if (entity.teamANames?.contains(p.name) == true) entity.teamAName else entity.teamBName
                        val s = PlayerMatchStatEntity.fromPlayer(p, matchId, teamName)
                        s.gullyId = gId
                        db.statsDao().insertStat(s)
                    }
                }
                
                Log.d("RECALC", "Match $matchId recalculated successfully")
                onComplete()
            } catch (e: Exception) {
                Log.e("RECALC", "Error: ${e.message}")
            }
        }
    }

    private fun processMilestones(inn: Innings, getP: (String?) -> Player) {
        // Maidens
        val balls = inn.balls
        var currentOverRuns = 0
        var legalBallsInOver = 0
        val overBowlers = mutableSetOf<String>()

        balls.forEach { b ->
            val bName = b.bowlerName?.trim()
            if (!bName.isNullOrEmpty()) overBowlers.add(bName)
            
            if (b.type == BallType.NORMAL || b.type == BallType.WIDE || b.type == BallType.NO_BALL) {
                currentOverRuns += b.runs
            }
            
            if (b.type == BallType.NORMAL || b.type == BallType.BYE || b.type == BallType.LEG_BYE) {
                legalBallsInOver++
            }

            if (legalBallsInOver == 6) {
                if (currentOverRuns == 0 && overBowlers.size == 1) {
                    getP(overBowlers.first()).maidens++
                }
                // Reset for next over
                currentOverRuns = 0
                legalBallsInOver = 0
                overBowlers.clear()
            }
        }

        // Hattricks (3 consecutive bowler-credited wickets)
        val bowlerStreaks = mutableMapOf<String, Int>()
        balls.forEach { b ->
            val bowler = b.bowlerName ?: return@forEach
            val isBowlerWicket = b.isWicket && 
                                 !b.dismissalInfo.toString().contains("Run Out", ignoreCase = true) &&
                                 !b.dismissalInfo.toString().contains("Obstruct", ignoreCase = true) &&
                                 !b.dismissalInfo.toString().contains("Retired", ignoreCase = true)
            
            if (isBowlerWicket) {
                val s = (bowlerStreaks[bowler] ?: 0) + 1
                if (s == 3) {
                    getP(bowler).hattricks++
                    bowlerStreaks[bowler] = 0 // Reset after hattrick to prevent double counting
                } else {
                    bowlerStreaks[bowler] = s
                }
            } else {
                // Any delivery that is not a bowler's wicket breaks the streak
                bowlerStreaks[bowler] = 0
            }
        }
    }

    /**
     * Finds and removes duplicate match stat entries for the same player and match.
     * Restores Leaderboards and League Stats back to exact numbers.
     */
    fun cleanDuplicateMatchStats(context: Context, onComplete: (() -> Unit)? = null) {
        AppDatabase.ioExecutor.execute {
            try {
                val db = AppDatabase.getInstance(context)
                db.statsDao().fixOrphanMatchStats()
                db.statsDao().fixMismatchedGullyMatchStats()

                val allStats = db.statsDao().getAllStats()?.filterNotNull() ?: run {
                    onComplete?.invoke()
                    return@execute
                }

                // Group stats by matchId + effective player identifier
                val grouped = allStats.groupBy { stat ->
                    val pKey = if (!stat.playerId.isNullOrBlank()) {
                        stat.playerId!!
                    } else {
                        stat.playerName?.trim()?.lowercase(java.util.Locale.getDefault()) ?: ""
                    }
                    "${stat.matchId}_${pKey}"
                }

                val idsToDelete = mutableListOf<String>()
                for ((_, statList) in grouped) {
                    if (statList.size > 1) {
                        // Sort so that the best record stays first:
                        // 1. Has non-blank playerId
                        // 2. Highest runsScored / wicketsTaken / ballsFaced
                        // 3. Most recent/stable ID
                        val sorted = statList.sortedWith(
                            compareByDescending<PlayerMatchStatEntity> { !it.playerId.isNullOrBlank() }
                                .thenByDescending { it.runsScored }
                                .thenByDescending { it.wicketsTaken }
                                .thenByDescending { it.ballsFaced }
                                .thenByDescending { it.ballsBowled }
                                .thenByDescending { it.id }
                        )
                        for (i in 1 until sorted.size) {
                            idsToDelete.add(sorted[i].id)
                        }
                    }
                }

                if (idsToDelete.isNotEmpty()) {
                    db.runInTransaction {
                        for (id in idsToDelete) {
                            db.statsDao().deleteStatById(id)
                        }
                    }
                    Log.d("DeduplicateStats", "Cleaned up ${idsToDelete.size} duplicate player match stat records.")
                }
                onComplete?.invoke()
            } catch (e: Exception) {
                Log.e("DeduplicateStats", "Error cleaning duplicate stats: ${e.message}")
                onComplete?.invoke()
            }
        }
    }
}

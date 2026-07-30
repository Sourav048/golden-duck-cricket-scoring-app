package com.example.scoring

import android.content.Context
import android.util.Log

object StatsRecalculator {

    fun recalculateAndSave(context: Context, matchId: String, onComplete: () -> Unit) {
        AppDatabase.ioExecutor.execute {
            try {
                val db = AppDatabase.getInstance(context)
                val entity = db.matchDao().getMatchById(matchId) ?: return@execute
                val m = entity.toMatch()
                
                val playerMap = mutableMapOf<String, Player>()
                val nameToId = entity.nameToIdMap ?: emptyMap<String, String>()

                fun getP(name: String?): Player {
                    val n = name ?: "Unknown"
                    return playerMap.getOrPut(n) { 
                        Player(n).apply { 
                            this.id = nameToId[n] 
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
                            outP.isOut = true
                            outP.dismissalInfo = b.dismissalInfo ?: "out"
                            
                            val isBowlerWicket = b.isWicket && 
                                               !b.dismissalInfo.toString().contains("Run Out", ignoreCase = true) &&
                                               !b.dismissalInfo.toString().contains("Obstruct", ignoreCase = true)
                            
                            if (isBowlerWicket) bowler.wicketsTaken++

                            // Fielding
                            b.fielderName?.let { fList ->
                                val fielders = fList.split(" / ")
                                fielders.forEach { fName ->
                                    val f = getP(fName.trim())
                                    val dInfo = outP.dismissalInfo
                                    val isRO = dInfo.contains("Run Out", ignoreCase = true)
                                    if (dInfo.contains("Caught", ignoreCase = true) || dInfo.startsWith("c ", ignoreCase = true) || dInfo.contains("c & b", ignoreCase = true)) {
                                        f.catches++
                                    } else if (dInfo.contains("Stumped", ignoreCase = true) || dInfo.startsWith("st ", ignoreCase = true)) {
                                        f.stumpings++
                                    } else if (isRO) {
                                        f.runOuts++
                                    }
                                }
                            }
                        }
                    }

                    // Maidens and Hattricks require over-by-over / streak analysis
                    processMilestones(inn, getP = { getP(it) })
                }

                processInnings(m.firstInnings)
                processInnings(m.secondInnings)

                // Save back to DB
                db.runInTransaction {
                    for (p in playerMap.values) {
                        val teamName = if (entity.teamANames?.contains(p.name) == true) entity.teamAName else entity.teamBName
                        val s = PlayerMatchStatEntity.fromPlayer(p, matchId, teamName)
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
        var currentBowler: String? = null

        balls.forEach { b ->
            if (currentBowler == null) currentBowler = b.bowlerName
            
            if (b.type == BallType.NORMAL || b.type == BallType.WIDE || b.type == BallType.NO_BALL) {
                currentOverRuns += b.runs
            }
            
            if (b.type == BallType.NORMAL || b.type == BallType.BYE || b.type == BallType.LEG_BYE) {
                legalBallsInOver++
            }

            if (legalBallsInOver == 6) {
                if (currentOverRuns == 0) {
                    getP(currentBowler).maidens++
                }
                // Reset for next over
                currentOverRuns = 0
                legalBallsInOver = 0
                currentBowler = null 
            }
        }

        // Hattricks (3 consecutive bowler-credited wickets)
        val bowlerStreaks = mutableMapOf<String, Int>()
        balls.forEach { b ->
            val bowler = b.bowlerName ?: return@forEach
            val isBowlerWicket = b.isWicket && 
                                 !b.dismissalInfo.toString().contains("Run Out", ignoreCase = true) &&
                                 !b.dismissalInfo.toString().contains("Obstruct", ignoreCase = true)
            
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
}

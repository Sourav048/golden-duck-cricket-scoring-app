package com.example.scoring

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Keep
@Entity(tableName = "matches")
class MatchEntity {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()

    @JvmField
    var teamAName: String? = null
    @JvmField
    var teamBName: String? = null
    @JvmField
    var venue: String? = null
    @JvmField
    var totalOvers: Int = 0
    @JvmField
    var ballType: String? = null
    @JvmField
    var teamAPlayerCount: Int = 11
    @JvmField
    var teamBPlayerCount: Int = 11
    var playedAt: Long = 0 // System.currentTimeMillis() when match ended
    @JvmField
    var result: String? = null // e.g. "Red Team won by 5 wickets"

    // 1st innings summary
    @JvmField
    var firstInningsTeam: String? = null
    @JvmField
    var firstInningsRuns: Int = 0
    @JvmField
    var firstInningsWickets: Int = 0
    @JvmField
    var firstInningsRetiredHurtCount: Int = 0

    // 2nd innings summary (filled after match ends)
    @JvmField
    var secondInningsTeam: String? = null
    @JvmField
    var secondInningsRuns: Int = 0
    @JvmField
    var secondInningsWickets: Int = 0
    @JvmField
    var secondInningsRetiredHurtCount: Int = 0

    @JvmField
    var playerOfTheMatchName: String? = null
    var matchMargin: String? = null // e.g. "by 5 wickets"

    @JvmField
    var tossWinner: String? = null
    @JvmField
    var tossDecision: String? = null
    @JvmField
    var firstInningsStartTime: Long = 0
    @JvmField
    var firstInningsEndTime: Long = 0
    @JvmField
    var secondInningsStartTime: Long = 0
    @JvmField
    var secondInningsEndTime: Long = 0

    // Serialized JSON for complex data
    @JvmField
    var fowJson1: List<FowEvent?>? = null
    @JvmField
    var fowJson2: List<FowEvent?>? = null
    @JvmField
    var pshipJson1: List<PartnershipEvent?>? = null
    @JvmField
    var pshipJson2: List<PartnershipEvent?>? = null
    @JvmField
    var commentaryJson1: List<CommentaryEntry?>? = null
    @JvmField
    var commentaryJson2: List<CommentaryEntry?>? = null
    @JvmField
    var commentaryJson: List<CommentaryEntry?>? = null // Unified match commentary

    @JvmField
    var isFinished: Boolean = false // false means match can be resumed
    @JvmField
    var isAbandoned: Boolean = false // if true, don't count stats

    @JvmField
    var ruleRunsOnWide: Boolean = false
    @JvmField
    var ruleFreeHit: Boolean = false
    @JvmField
    var ruleRunsOnBye: Boolean = false
    @JvmField
    var ruleOverthrow: Boolean = false
    @JvmField
    var ruleEveryPlayerBats: Boolean = true

    @JvmField
    var isFreeHitActive: Boolean = false
    @JvmField
    var currentBowlerInSpell: String? = null

    // Resumption state
    @JvmField
    var currentStrikerName: String? = null
    @JvmField
    var currentNonStrikerName: String? = null
    @JvmField
    var currentBowlerName: String? = null
    var nextBatsmanIdx: Int = 0
    var isTeamABatting: Boolean = false

    @JvmField
    var strikerEntryTime: Long = 0
    @JvmField
    var nonStrikerEntryTime: Long = 0

    @JvmField
    var ballsJson1: List<Ball?>? = null
    @JvmField
    var ballsJson2: List<Ball?>? = null

    @JvmField
    var revisedTarget: Int? = null
    @JvmField
    var revisedOvers: Int? = null

    var isLive: Boolean = false
    var startNotificationSent: Boolean = false
    @JvmField
    var isSharedOver: Boolean = false
    
    // Heartbeat for Live Status (ensures match goes to "In-Progress" if scorer leaves abruptly)
    var lastScorerPulse: Long = 0
    
    // Gully Sync Fields
    var gullyId: String = "local"
    var cloudId: String? = null
    var lastSyncedAt: Long = 0
    
    // Phase 5: Compressed Cloud Payload
    // This stores all heavy lists (Balls, Comm, Pship, Fow) in one tiny string for Firestore
    var compressedPayload: String? = null
    
    // Squads/Mappings
    var teamANames: List<String?>? = null
    var teamBNames: List<String?>? = null
    var photoMap: Map<String?, String?>? = null
    var nameToIdMap: Map<String?, String?>? = null

    init {} // REQUIRED FOR FIREBASE

    fun toMatch(): Match {
        val m = Match(teamAName, teamBName, totalOvers, teamAPlayerCount, teamBPlayerCount)
        m.venue = venue
        m.ballType = ballType
        m.tossWinner = tossWinner
        m.tossDecision = tossDecision
        m.ruleRunsOnWide = ruleRunsOnWide
        m.ruleFreeHit = ruleFreeHit
        m.ruleRunsOnBye = ruleRunsOnBye
        m.ruleOverthrow = ruleOverthrow
        m.ruleEveryPlayerBats = ruleEveryPlayerBats
        m.isSharedOver = isSharedOver
        m.firstInningsStartTime = firstInningsStartTime
        m.firstInningsEndTime = firstInningsEndTime
        m.secondInningsStartTime = secondInningsStartTime
        m.secondInningsEndTime = secondInningsEndTime
        m.isStartNotificationSent = startNotificationSent

        // Restore additional resumption state
        // NOTE: Striker/Non-Striker/Bowler names are handled in MainActivity.resumeMatch 
        // to ensure we get the full Player objects from the cache.

        // Restore first innings from persisted data
        val i1: Innings? = if (firstInningsTeam != null) {
            val battingTeamName = firstInningsTeam!!
            val bowlingTeamName = if (battingTeamName == teamAName) teamBName else teamAName
            val playerCnt = if (battingTeamName == teamAName) teamAPlayerCount else teamBPlayerCount
            var maxW = if (ruleEveryPlayerBats) playerCnt else playerCnt - 1
            if (maxW <= 0) maxW = 1

            Innings(battingTeamName, bowlingTeamName, totalOvers, maxW).also { innings ->
                revisedOvers?.let { innings.revisedMaxOvers = it }
                innings.retiredHurtCount = firstInningsRetiredHurtCount
                innings.balls = ArrayList(ballsJson1?.filterNotNull() ?: emptyList())
                innings.fallOfWickets = ArrayList(fowJson1?.filterNotNull() ?: emptyList())
                innings.partnerships = ArrayList(pshipJson1?.filterNotNull() ?: emptyList())
                innings.commentary = ArrayList(commentaryJson1?.filterNotNull() ?: emptyList())
                innings.recalculateState()
                // Fall back to summary fields for older records that had no ball-by-ball data
                if (innings.balls.isEmpty()) {
                    innings.restoreSummary(firstInningsRuns, firstInningsWickets)
                }
            }
        } else null

        // Restore second innings from persisted data
        val i2: Innings? = if ((secondInningsTeam != null) && (i1 != null)) {
            val battingTeamName = secondInningsTeam!!
            val bowlingTeamName = if (battingTeamName == teamAName) teamBName else teamAName
            val playerCnt = if (battingTeamName == teamAName) teamAPlayerCount else teamBPlayerCount
            var maxW = if (ruleEveryPlayerBats) playerCnt else playerCnt - 1
            if (maxW <= 0) maxW = 1

            Innings(battingTeamName, bowlingTeamName, totalOvers, maxW).also { innings ->
                innings.target = i1.totalRuns + 1
                revisedTarget?.let { innings.revisedTarget = it }
                revisedOvers?.let { innings.revisedMaxOvers = it }
                innings.retiredHurtCount = secondInningsRetiredHurtCount
                innings.balls = ArrayList(ballsJson2?.filterNotNull() ?: emptyList())
                innings.fallOfWickets = ArrayList(fowJson2?.filterNotNull() ?: emptyList())
                innings.partnerships = ArrayList(pshipJson2?.filterNotNull() ?: emptyList())
                innings.commentary = ArrayList(commentaryJson2?.filterNotNull() ?: emptyList())
                innings.recalculateState()
                // Fall back to summary fields for older records that had no ball-by-ball data
                if (innings.balls.isEmpty()) {
                    innings.restoreSummary(secondInningsRuns, secondInningsWickets)
                }
            }
        } else null

        m.restoreInnings(i1, i2)
        return m
    }

    companion object {
        fun fromMatch(m: Match): MatchEntity {
            val entity = MatchEntity()
            entity.teamAName = m.teamA
            entity.teamBName = m.teamB
            entity.venue = m.venue
            entity.totalOvers = m.totalOvers
            entity.teamAPlayerCount = m.teamAPlayerCount
            entity.teamBPlayerCount = m.teamBPlayerCount
            entity.ballType = m.ballType
            entity.tossWinner = m.tossWinner
            entity.tossDecision = m.tossDecision
            entity.ruleRunsOnWide = m.ruleRunsOnWide
            entity.ruleFreeHit = m.ruleFreeHit
            entity.ruleRunsOnBye = m.ruleRunsOnBye
            entity.ruleOverthrow = m.ruleOverthrow
            entity.ruleEveryPlayerBats = m.ruleEveryPlayerBats
            entity.isSharedOver = m.isSharedOver
            entity.firstInningsStartTime = m.firstInningsStartTime
            entity.firstInningsEndTime = m.firstInningsEndTime
            entity.secondInningsStartTime = m.secondInningsStartTime
            entity.secondInningsEndTime = m.secondInningsEndTime
            entity.startNotificationSent = m.isStartNotificationSent
            
            m.firstInnings?.let { i ->
                entity.firstInningsTeam = i.battingTeam
                entity.firstInningsRuns = i.totalRuns
                entity.firstInningsWickets = i.totalWickets
                entity.firstInningsRetiredHurtCount = i.retiredHurtCount
                entity.ballsJson1 = ArrayList(i.balls)
                entity.fowJson1 = ArrayList(i.fallOfWickets)
                entity.pshipJson1 = ArrayList(i.partnerships)
                entity.commentaryJson1 = ArrayList(i.commentary)
                entity.revisedOvers = i.revisedMaxOvers
            }
            
            m.secondInnings?.let { i ->
                entity.secondInningsTeam = i.battingTeam
                entity.secondInningsRuns = i.totalRuns
                entity.secondInningsWickets = i.totalWickets
                entity.secondInningsRetiredHurtCount = i.retiredHurtCount
                entity.ballsJson2 = ArrayList(i.balls)
                entity.fowJson2 = ArrayList(i.fallOfWickets)
                entity.pshipJson2 = ArrayList(i.partnerships)
                entity.commentaryJson2 = ArrayList(i.commentary)
                entity.revisedTarget = i.revisedTarget
                entity.revisedOvers = i.revisedMaxOvers
            }
            
            return entity
        }
    }
}

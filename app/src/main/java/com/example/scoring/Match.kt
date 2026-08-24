package com.example.scoring

class Match {
    @JvmField
    var teamA: String? = null
    @JvmField
    var teamB: String? = null
    @JvmField
    var venue: String? = null
    @JvmField
    var totalOvers: Int = 0
    @JvmField
    var teamAPlayerCount: Int = 0
    @JvmField
    var teamBPlayerCount: Int = 0
    var firstInnings: Innings? = null
        private set
    var secondInnings: Innings? = null
        private set
    var currentInnings: Innings? = null
        private set
    var firstInningsStartTime: Long = 0
    var firstInningsEndTime: Long = 0
    var secondInningsStartTime: Long = 0
    var secondInningsEndTime: Long = 0
    var tossWinner: String? = null
    var tossDecision: String? = null
    var ballType: String? = null
    var ruleRunsOnWide: Boolean = false
    var ruleFreeHit: Boolean = false
    var ruleRunsOnBye: Boolean = false
    var ruleOverthrow: Boolean = false
    var ruleEveryPlayerBats: Boolean = false
    
    var pendingPenaltyForSecondInnings: Int = 0
    var revisedTarget: Int? = null
    var resourcesLostTeam1: Double = 0.0
    var isSharedOver: Boolean = false
    var isStartNotificationSent: Boolean = false

    constructor()

    constructor(
        teamA: String?,
        teamB: String?,
        totalOvers: Int,
        teamAPlayerCount: Int,
        teamBPlayerCount: Int,
    ) {
        this.teamA = teamA
        this.teamB = teamB
        this.totalOvers = totalOvers
        this.teamAPlayerCount = teamAPlayerCount
        this.teamBPlayerCount = teamBPlayerCount
    }

    fun startFirstInnings(battingTeam: String, bowlingTeam: String?) {
        val playerCnt = if (battingTeam == teamA) teamAPlayerCount else teamBPlayerCount
        var maxW = if (ruleEveryPlayerBats) playerCnt else playerCnt - 1
        if (maxW <= 0) maxW = 1

        firstInnings = Innings(battingTeam, bowlingTeam, totalOvers, maxW)
        currentInnings = firstInnings
        firstInningsStartTime = System.currentTimeMillis()
    }

    fun startSecondInnings() {
        val i1 = firstInnings ?: return
        firstInningsEndTime = if (i1.completionTime > 0) i1.completionTime else System.currentTimeMillis()
        
        val playerCnt = if (i1.bowlingTeam == teamA) teamAPlayerCount else teamBPlayerCount
        var maxW = if (ruleEveryPlayerBats) playerCnt else playerCnt - 1
        if (maxW <= 0) maxW = 1

        val matchOvers = i1.revisedMaxOvers ?: totalOvers

        secondInnings = Innings(i1.bowlingTeam, i1.battingTeam, matchOvers, maxW).apply {
            target = if (revisedTarget != null) {
                revisedTarget
            } else if (resourcesLostTeam1 > 0) {
                val r1 = (100.0 - resourcesLostTeam1).coerceAtLeast(0.0)
                // Use original totalOvers as base for Team 2 as well
                val r2 = DLSUtility.getResource(matchOvers, 0, totalOvers)
                DLSUtility.calculateRevisedTarget(i1.totalRuns, r1, r2)
            } else {
                i1.totalRuns + 1
            }

            if (pendingPenaltyForSecondInnings > 0) {
                addPenalty(pendingPenaltyForSecondInnings)
                pendingPenaltyForSecondInnings = 0
            }
        }
        currentInnings = secondInnings
        secondInningsStartTime = System.currentTimeMillis()
    }

    fun restoreInnings(first: Innings?, second: Innings?) {
        firstInnings = first
        secondInnings = second
        currentInnings = second ?: first
    }

    val target: Int
        get() = revisedTarget ?: secondInnings?.revisedTarget ?: secondInnings?.target ?: (firstInnings?.totalRuns?.plus(1) ?: 0)
}

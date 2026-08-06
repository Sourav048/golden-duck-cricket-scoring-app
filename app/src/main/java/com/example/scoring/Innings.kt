package com.example.scoring

class Innings {
    @JvmField
    var battingTeam: String? = null
    @JvmField
    var bowlingTeam: String? = null
    @JvmField
    var maxOvers: Int = 0
    @JvmField
    var maxWickets: Int = 0
    @JvmField
    var balls: MutableList<Ball> = ArrayList()

    @JvmField
    var totalRuns: Int = 0
    var target: Int? = null
    @JvmField
    var totalWickets: Int = 0
    @JvmField
    var legalBalls: Int = 0
    var battingRuns: Int = 0
    @JvmField
    var wides: Int = 0
    @JvmField
    var noBalls: Int = 0
    @JvmField
    var byes: Int = 0
    @JvmField
    var legByes: Int = 0
    @JvmField
    var penalties: Int = 0
    @JvmField
    var retiredHurtCount: Int = 0

    @JvmField
    var revisedTarget: Int? = null
    @JvmField
    var revisedMaxOvers: Int? = null

    @JvmField
    var commentary: MutableList<CommentaryEntry?> = ArrayList()
    @JvmField
    var fallOfWickets: MutableList<FowEvent?> = ArrayList()
    @JvmField
    var partnerships: MutableList<PartnershipEvent> = ArrayList()
    
    var completionTime: Long = 0

    constructor() // Required for Firestore

    constructor(battingTeam: String?, bowlingTeam: String?, maxOvers: Int, maxWickets: Int) {
        this.battingTeam = battingTeam
        this.bowlingTeam = bowlingTeam
        this.maxOvers = maxOvers
        this.maxWickets = maxWickets
    }

    val extras: Int
        get() = totalRuns - battingRuns

    fun recordManualWicket(p: Player, info: String?) {
        totalWickets++
        p.isOut = true
        p.dismissalInfo = info ?: "out"
        p.exitTime = System.currentTimeMillis()
        if (p.entryTime > 0) {
            p.minutesPlayed += ((p.exitTime - p.entryTime) / 1000).toInt()
            p.entryTime = 0
        }
        fallOfWickets.add(FowEvent(p.name, totalRuns, totalWickets, this.oversDisplay))
    }

    fun recordRetired(p: Player, isWicket: Boolean, info: String, sName: String?, nsName: String?, bName: String? = null) {
        val now = System.currentTimeMillis()
        if (isWicket) totalWickets++
        else if (info == "retired hurt") retiredHurtCount++
        
        p.isOut = isWicket
        p.dismissalInfo = info
        p.exitTime = now
        if (p.entryTime > 0) {
            p.minutesPlayed += ((p.exitTime - p.entryTime) / 1000).toInt()
            p.entryTime = 0
        }
        
        if (isWicket) {
            fallOfWickets.add(FowEvent(p.name, totalRuns, totalWickets, this.oversDisplay))
        }

        // Add dummy ball record for Undo targeting
        // Use the provided bowler name or "RETIRED" to avoid "FIELD" creation
        val actualBowler = bName ?: "RETIRED"
        val ball = Ball(0, BallType.PENALTY, isWicket, sName, actualBowler, 0, nsName).apply {
            this.outPlayerName = p.name
            this.dismissalInfo = info
        }
        balls.add(ball)
    }

    fun playerReturned(p: Player?) {
        if (p?.dismissalInfo == "retired hurt" && retiredHurtCount > 0) {
            retiredHurtCount--
        }
    }

    val currentPshipRuns: Int
        get() = partnerships.lastOrNull()?.runs ?: 0

    val currentPshipBalls: Int
        get() = partnerships.lastOrNull()?.balls ?: 0

    fun restoreSummary(runs: Int, wickets: Int) {
        this.totalRuns = runs
        this.totalWickets = wickets
    }

    /**
     * Recalculates match totals from the list of balls.
     * NOTE: This does NOT recalculate individual player stats (runs, balls faced, etc.).
     * Those rely on the PlayerMatchStatEntity records in the database.
     */
    fun recalculateState() {
        totalRuns = 0
        totalWickets = 0
        legalBalls = 0
        battingRuns = 0
        wides = 0
        noBalls = 0
        byes = 0
        legByes = 0
        penalties = 0
        retiredHurtCount = 0
        for (b in balls) {
            totalRuns += b.runs
            if (b.isWicket) totalWickets++
            if (b.dismissalInfo == "retired hurt") {
                retiredHurtCount++
            }
            when (b.type) {
                BallType.NORMAL -> {
                    legalBalls++
                    battingRuns += b.batRuns
                }
                BallType.WIDE -> {
                    val penalty = if (b.runs > b.batRuns) (b.runs - b.batRuns) else b.runs
                    wides += penalty
                    battingRuns += (b.runs - penalty)
                }
                BallType.NO_BALL -> {
                    noBalls += (b.runs - b.batRuns)
                    battingRuns += b.batRuns
                }
                BallType.BYE -> {
                    legalBalls++
                    byes += b.runs
                }
                BallType.LEG_BYE -> {
                    legalBalls++
                    legByes += b.runs
                }
                BallType.PENALTY -> penalties += b.runs
                else -> {}
            }
        }
    }

    val isComplete: Boolean
        get() {
            target?.let { t -> if (totalRuns >= t) return true }
            revisedTarget?.let { rt -> if (totalRuns >= rt) return true }

            if (maxWickets > 0 && totalWickets >= maxWickets) {
                // If there are Retired Hurt players who could return, the innings is not complete 
                // until they either return and get out, or the user decides otherwise.
                if (retiredHurtCount > 0) return false
                return true
            }

            val oversLimit = revisedMaxOvers ?: maxOvers
            if (oversLimit > 0 && legalBalls >= (oversLimit * 6)) {
                return legalBalls > 0
            }
            return false
        }

    val oversDisplay: String
        get() = "${legalBalls / 6}.${legalBalls % 6}"

    val currentRunRate: Double
        get() = if (legalBalls == 0) 0.0 else (totalRuns.toDouble() / legalBalls) * 6

    fun startNewPartnership(b1: String?, b2: String?) {
        partnerships.add(PartnershipEvent(b1, b2))
    }

    fun addCommentary(over: String?, text: String?, highlight: String?, type: String?) {
        val entry = CommentaryEntry(over, text, highlight, type)
        entry.baseText = text // Explicitly set baseText to match text
        commentary.add(0, entry)
    }

    fun removeLastBallCommentary() {
        if (commentary.isEmpty()) return
        // Remove the ball entry
        if (commentary[0]?.type == "BALL") {
            commentary.removeAt(0)
        }
        // Also remove any FACT that was added immediately before it (like target reminder)
        if (commentary.isNotEmpty() && commentary[0]?.type == "FACT" && 
            commentary[0]?.text?.contains("Innings Complete") != true && 
            commentary[0]?.text?.contains("Match Started") != true) {
            commentary.removeAt(0)
        }
    }

    fun addPenalty(runs: Int) {
        totalRuns += runs
        penalties += runs
        balls.add(Ball(runs, BallType.PENALTY, isWicket = false, "PENALTY", "FIELD", 0))
    }

    fun addBall(
        runs: Int,
        type: BallType,
        isWicket: Boolean,
        batsman: Player,
        bowler: Player,
        fielder: String?,
        manualBatRuns: Int,
        outPlayer: Player? = null,
        nonStriker: Player? = null,
    ) {
        if (this.isComplete) return

        val actualOut = outPlayer ?: batsman

        val ball = Ball(runs, type, isWicket, batsman.name, bowler.name, manualBatRuns, nonStriker?.name).apply {
            fielderName = fielder
            if (isWicket) {
                dismissalInfo = actualOut.dismissalInfo
                outPlayerName = actualOut.name
            }
        }
        balls.add(ball)
        totalRuns += runs

        // Automated Partnership Management
        val b1 = batsman.name
        val b2 = nonStriker?.name ?: "N/A"
        
        val lastP = partnerships.lastOrNull()
        val isPairSame = lastP != null && 
            ((lastP.batter1 == b1 && lastP.batter2 == b2) || (lastP.batter1 == b2 && lastP.batter2 == b1))
            
        val activeP = if (isPairSame) {
            lastP
        } else {
            val newP = PartnershipEvent(b1, b2)
            partnerships.add(newP)
            newP
        }

        activeP.let { p ->
            p.runs += runs
            val countsBalls = (type == BallType.NORMAL || type == BallType.BYE || type == BallType.LEG_BYE || type == BallType.NO_BALL)
            if (countsBalls) p.balls++

            if (batsman.name == p.batter1) {
                p.b1Runs += manualBatRuns
                if (countsBalls) p.b1Balls++
            } else {
                p.b2Runs += manualBatRuns
                if (countsBalls) p.b2Balls++
            }
        }

        when (type) {
            BallType.NORMAL -> {
                legalBalls++
                bowler.ballsBowled++
                batsman.runsScored += runs
                battingRuns += runs
                batsman.ballsFaced++
                when (runs) {
                    4 -> batsman.fours++
                    6 -> batsman.sixes++
                    0 -> bowler.dotBalls++
                }
                bowler.runsConceded += runs
            }
            BallType.WIDE -> {
                wides += runs
                bowler.runsConceded += runs
                bowler.widesConceded += runs
            }
            BallType.NO_BALL -> {
                val extraR = runs - manualBatRuns
                noBalls += extraR
                battingRuns += manualBatRuns
                batsman.runsScored += manualBatRuns
                batsman.ballsFaced++
                when (manualBatRuns) {
                    4 -> batsman.fours++
                    6 -> batsman.sixes++
                }
                bowler.runsConceded += runs
                bowler.noBallsConceded += extraR
            }
            BallType.BYE -> {
                legalBalls++
                byes += runs
                bowler.ballsBowled++
                batsman.ballsFaced++
            }
            BallType.LEG_BYE -> {
                legalBalls++
                legByes += runs
                bowler.ballsBowled++
                batsman.ballsFaced++
            }
            BallType.PENALTY -> {
                penalties += runs
            }
        }

        if (isWicket) {
            totalWickets++
            actualOut.isOut = true
            actualOut.exitTime = System.currentTimeMillis()
            if (actualOut.entryTime > 0) {
                actualOut.minutesPlayed += ((actualOut.exitTime - actualOut.entryTime) / 1000).toInt()
                actualOut.entryTime = 0
            }
            fallOfWickets.add(FowEvent(actualOut.name, totalRuns, totalWickets, this.oversDisplay))
        }

        if (isComplete && completionTime == 0L) {
            completionTime = System.currentTimeMillis()
        }
    }

    fun undoLastBall(facingBatsman: Player, bowler: Player?, outPlayer: Player? = null) {
        if (balls.isEmpty()) return
        val last = balls.removeAt(balls.size - 1)
        totalRuns -= last.runs

        partnerships.lastOrNull()?.let { p ->
            p.runs -= last.runs
            val countsBalls = (last.type == BallType.NORMAL || last.type == BallType.BYE || last.type == BallType.LEG_BYE || last.type == BallType.NO_BALL)
            if (countsBalls) p.balls--
            if (facingBatsman.name == p.batter1) {
                p.b1Runs -= last.batRuns
                if (countsBalls) p.b1Balls--
            } else {
                p.b2Runs -= last.batRuns
                if (countsBalls) p.b2Balls--
            }
            
            // If this was the first ball of a new partnership, remove the partnership entirely
            if (p.balls <= 0 && p.runs <= 0 && partnerships.size > 1) {
                partnerships.removeAt(partnerships.size - 1)
            }
        }

        when (last.type) {
            BallType.NORMAL -> {
                legalBalls--
                bowler?.let { it.ballsBowled-- }
                facingBatsman.runsScored -= last.runs
                battingRuns -= last.runs
                facingBatsman.ballsFaced--
                when (last.runs) {
                    4 -> facingBatsman.fours--
                    6 -> facingBatsman.sixes--
                    0 -> bowler?.let { it.dotBalls-- }
                }
                bowler?.let { it.runsConceded -= last.runs }
            }
            BallType.WIDE -> {
                wides -= last.runs
                bowler?.let { 
                    it.runsConceded -= last.runs
                    it.widesConceded -= last.runs
                }
            }
            BallType.NO_BALL -> {
                val extraR = last.runs - last.batRuns
                noBalls -= extraR
                battingRuns -= last.batRuns
                facingBatsman.runsScored -= last.batRuns
                facingBatsman.ballsFaced--
                when (last.batRuns) {
                    4 -> facingBatsman.fours--
                    6 -> facingBatsman.sixes--
                }
                bowler?.let {
                    it.runsConceded -= last.runs
                    it.noBallsConceded -= extraR
                }
            }
            BallType.BYE -> {
                legalBalls--
                byes -= last.runs
                bowler?.let { it.ballsBowled-- }
                facingBatsman.ballsFaced--
            }
            BallType.LEG_BYE -> {
                legalBalls--
                legByes -= last.runs
                bowler?.let { it.ballsBowled-- }
                facingBatsman.ballsFaced--
            }
            BallType.PENALTY -> {
                penalties -= last.runs
            }
            else -> {}
        }

        if (last.isWicket || last.dismissalInfo?.contains("retired") == true) {
            if (last.isWicket && totalWickets > 0) totalWickets--
            else if (last.dismissalInfo == "retired hurt" && retiredHurtCount > 0) retiredHurtCount--
            
            val actualOut = outPlayer ?: facingBatsman
            actualOut.isOut = false
            actualOut.dismissalInfo = "not out"
            actualOut.exitTime = 0
            
            // Restore entryTime to the current time so the crease clock resumes correctly.
            actualOut.entryTime = System.currentTimeMillis()
            
            if (last.isWicket) {
                bowler?.let { if (it.wicketsTaken > 0) it.wicketsTaken-- }
                if (fallOfWickets.isNotEmpty()) fallOfWickets.removeAt(fallOfWickets.size - 1)
            }
        }
    }
}

package com.goldenduck.scoring

import androidx.annotation.Keep

@Keep
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
        else retiredHurtCount++
        
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
            batsmanId = p.id.takeIf { sName == p.name }
            nonStrikerId = p.id.takeIf { nsName == p.name }
            wicketDetail = WicketDetail(
                dismissedPlayerId = p.id!!,
                dismissalType = if (isWicket) DismissalType.RETIRED_OUT else DismissalType.RETIRED_HURT
            )
        }
        balls.add(ball)
    }

    fun playerReturned(p: Player?) {
        if (p?.dismissalInfo?.trim().equals("retired hurt", ignoreCase = true) && retiredHurtCount > 0) {
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
        val outstandingRetirements = mutableMapOf<String, String?>()
        fun normalizedName(name: String?): String? = name?.trim()?.lowercase(java.util.Locale.ROOT)
        fun markReturned(id: String?, name: String?) {
            val normalized = normalizedName(name)
            if (id != null) outstandingRetirements.remove("id:$id")
            if (normalized != null) {
                outstandingRetirements.remove("name:$normalized")
                if (id == null) {
                    outstandingRetirements.entries.removeAll { it.value == normalized }
                }
            }
        }
        for (b in balls) {
            totalRuns += b.runs
            if (b.isWicket) totalWickets++
            val isRetiredHurt = b.wicketDetail?.dismissalType == DismissalType.RETIRED_HURT ||
                (b.wicketDetail == null && b.dismissalInfo?.trim().equals("retired hurt", ignoreCase = true))
            if (isRetiredHurt) {
                val retiredId = b.wicketDetail?.dismissedPlayerId
                val retiredName = normalizedName(when {
                    retiredId != null && retiredId == b.batsmanId -> b.batsmanName
                    retiredId != null && retiredId == b.nonStrikerId -> b.nonStrikerName
                    else -> b.outPlayerName ?: b.batsmanName
                })
                val key = retiredId?.let { "id:$it" } ?: "name:$retiredName"
                outstandingRetirements[key] = retiredName
            } else if (b.type != BallType.PENALTY) {
                markReturned(b.batsmanId, b.batsmanName)
                markReturned(b.nonStrikerId, b.nonStrikerName)
            }
            retiredHurtCount = outstandingRetirements.size
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
                    val byeR = b.byeRuns
                    val legByeR = b.legByeRuns
                    val nbPenalty = (b.runs - b.batRuns - byeR - legByeR).coerceAtLeast(0)
                    noBalls += nbPenalty
                    byes += byeR
                    legByes += legByeR
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
            val effectiveTarget = revisedTarget ?: target
            effectiveTarget?.let { t -> if (totalRuns >= t) return true }

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

    fun rebuildPartnerships() {
        if (balls.isEmpty()) return
        val savedBalls = ArrayList(balls)
        val savedCommentary = ArrayList(commentary)
        val namesById = mutableMapOf<String, String>()
        savedBalls.forEach { b ->
            b.batsmanId?.let { id -> b.batsmanName?.let { namesById[id] = it } }
            b.nonStrikerId?.let { id -> b.nonStrikerName?.let { namesById[id] = it } }
            b.bowlerId?.let { id -> b.bowlerName?.let { namesById[id] = it } }
        }

        balls.clear()
        partnerships.clear()
        fallOfWickets.clear()
        commentary.clear()

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

        var currentStrikerName: String? = null
        var currentNonStrikerName: String? = null

        for (b in savedBalls) {
            val facingName = b.batsmanName ?: b.batsmanId?.let { namesById[it] } ?: "Batsman"

            var nsName = b.nonStrikerName ?: b.nonStrikerId?.let { namesById[it] }
            if (nsName.isNullOrEmpty()) {
                nsName = if (!currentNonStrikerName.isNullOrEmpty() && currentNonStrikerName != facingName) {
                    currentNonStrikerName
                } else if (!currentStrikerName.isNullOrEmpty() && currentStrikerName != facingName) {
                    currentStrikerName
                } else {
                    null
                }
            }

            val b1 = Player(facingName)
            val b2 = nsName?.let { Player(it) }
            val bowler = Player(b.bowlerName ?: "Bowler")
            val dismissedName = b.wicketDetail?.dismissedPlayerId?.let { namesById[it] }
                ?: b.outPlayerName
            val outP = dismissedName?.let { Player(it) } ?: if (b.isWicket) b1 else null
            val retirementType = b.wicketDetail?.dismissalType
            val isRetirement = retirementType == DismissalType.RETIRED_HURT ||
                retirementType == DismissalType.RETIRED_OUT ||
                (b.wicketDetail == null && b.dismissalInfo?.contains("retired", ignoreCase = true) == true)

            if (b.type == BallType.PENALTY) {
                if (isRetirement) {
                    val retiredPlayer = outP ?: b1
                    recordRetired(retiredPlayer, b.isWicket, b.dismissalInfo ?: "retired hurt", facingName, nsName, b.bowlerName)
                    var newS: String? = facingName
                    var newNS: String? = nsName
                    val outName = dismissedName ?: facingName
                    if (newS == outName) newS = null
                    if (newNS == outName) newNS = null
                    currentStrikerName = newS ?: newNS
                    currentNonStrikerName = newNS ?: newS
                } else {
                    addPenalty(b.runs, b.bowlerName)
                }
                continue
            }

            addBall(
                runs = b.runs,
                type = b.type ?: BallType.NORMAL,
                isWicket = b.isWicket,
                batsman = b1,
                bowler = bowler,
                fielder = b.fielderName,
                manualBatRuns = b.batRuns,
                outPlayer = outP,
                nonStriker = b2
            )

            var newS: String? = facingName
            var newNS: String? = nsName

            if (b.isWicket || isRetirement) {
                val outName = dismissedName ?: facingName
                if (newS == outName) newS = null
                if (newNS == outName) newNS = null
            }

            currentStrikerName = newS ?: newNS
            currentNonStrikerName = newNS ?: newS
        }

        // Rebuilding derived partnerships must never rewrite persisted dismissal IDs or text.
        balls.clear()
        balls.addAll(savedBalls)
        recalculateState()
        commentary.clear()
        commentary.addAll(savedCommentary)
    }

    fun addCommentary(over: String?, text: String?, highlight: String?, type: String?) {
        val entry = CommentaryEntry(over, text, highlight, type)
        entry.baseText = text // Explicitly set baseText to match text
        commentary.add(0, entry)
    }

    fun removeLastBallCommentary(undoneBall: Ball? = null) {
        if (commentary.isEmpty()) return

        val isRetirement = undoneBall?.let {
            it.wicketDetail?.dismissalType == DismissalType.RETIRED_HURT ||
                it.wicketDetail?.dismissalType == DismissalType.RETIRED_OUT ||
                (it.type == BallType.PENALTY && it.dismissalInfo?.contains("retired", ignoreCase = true) == true)
        } ?: (commentary.firstOrNull()?.text?.contains("Retired", ignoreCase = true) == true &&
                commentary.firstOrNull()?.type == "FACT")

        if (isRetirement) {
            // Remove any replacement batsman entry facts added after this retirement,
            // as well as the retirement FACT entry itself
            var i = 0
            while (i < commentary.size) {
                val entry = commentary[i]
                if (entry?.type == "FACT" && entry.text?.contains("Retired", ignoreCase = true) == true) {
                    commentary.removeAt(i)
                    break
                } else if (entry?.type == "FACT" && (
                    entry.text?.contains("comes to the crease", ignoreCase = true) == true ||
                    entry.text?.contains("Replacing", ignoreCase = true) == true ||
                    entry.text?.contains("last Batter Standing", ignoreCase = true) == true
                )) {
                    commentary.removeAt(i)
                } else {
                    i++
                }
            }
            return
        }

        val isPenalty = undoneBall?.let {
            it.type == BallType.PENALTY && !isRetirement
        } ?: false

        if (isPenalty) {
            // Only remove the penalty FACT entry without touching preceding deliveries
            val idx = commentary.indexOfFirst { entry ->
                entry?.type == "FACT" && (
                    entry.text?.contains("Penalty", ignoreCase = true) == true ||
                    entry.highlight?.startsWith("P") == true
                )
            }
            if (idx != -1) {
                commentary.removeAt(idx)
            }
            return
        }

        var i = 0
        var foundBall = false

        while (i < commentary.size && !foundBall) {
            val type = commentary[i]?.type
            if (type == "BALL") {
                commentary.removeAt(i)
                foundBall = true
            } else if (type == "SETUP_FACT" || type == "SQUAD_FACT") {
                // Skip over permanent match/squad facts and keep searching down the list
                i++
            } else if (commentary[i]?.text?.contains("Retired", ignoreCase = true) == true ||
                commentary[i]?.text?.contains("Penalty", ignoreCase = true) == true ||
                commentary[i]?.highlight?.startsWith("P") == true
            ) {
                // Preserve retirement and penalty events when undoing a delivery bowled after them
                i++
            } else {
                // Remove OVER_SUMMARY, per-ball FACTs, or other temporary post-ball commentary
                commentary.removeAt(i)
            }
        }

        // Remove any trailing per-ball FACT (e.g. Bowler attack message), but preserve retirement, penalty and batsman entry facts
        while (i < commentary.size && commentary[i]?.type == "FACT") {
            if (commentary[i]?.text?.contains("Retired", ignoreCase = true) == true ||
                commentary[i]?.text?.contains("comes to the crease", ignoreCase = true) == true ||
                commentary[i]?.text?.contains("Replacing", ignoreCase = true) == true ||
                commentary[i]?.text?.contains("last Batter Standing", ignoreCase = true) == true ||
                commentary[i]?.text?.contains("Penalty", ignoreCase = true) == true ||
                commentary[i]?.highlight?.startsWith("P") == true
            ) {
                break
            }
            commentary.removeAt(i)
        }
    }

    fun addPenalty(runs: Int, bName: String? = null) {
        totalRuns += runs
        penalties += runs
        balls.add(Ball(runs, BallType.PENALTY, isWicket = false, "PENALTY", bName ?: "FIELD", 0))
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
        wicketDetail: WicketDetail? = null,
        playerNameResolver: ((String) -> String)? = null,
        byeRuns: Int = 0,
        legByeRuns: Int = 0,
        overthrowRuns: Int = 0,
        isBoundary: Boolean = (type == BallType.NORMAL || type == BallType.NO_BALL) && ((manualBatRuns > 0 && (manualBatRuns == 4 || manualBatRuns == 6)) || (type == BallType.NORMAL && (runs == 4 || runs == 6))) && overthrowRuns == 0 && !isWicket
    ) {
        if (this.isComplete) return

        val actualOut = outPlayer ?: batsman

        val ball = Ball(runs, type, isWicket, batsman.name, bowler.name, manualBatRuns, nonStriker?.name, byeRuns, legByeRuns, overthrowRuns, isBoundary).apply {
            fielderName = fielder
            batsmanId = batsman.id
            bowlerId = bowler.id
            nonStrikerId = nonStriker?.id
            outPlayerName = actualOut.name
            this.wicketDetail = wicketDetail
            if (isWicket) {
                if (wicketDetail != null) {
                    this.dismissalInfo = wicketDetail.toDisplayString { id -> 
                        playerNameResolver?.invoke(id) ?: run {
                            if (id == batsman.id) batsman.name ?: id 
                            else if (id == bowler.id) bowler.name ?: id 
                            else if (id == nonStriker?.id) nonStriker.name ?: id 
                            else id
                        }
                    }
                    actualOut.dismissalInfo = this.dismissalInfo!!
                } else {
                    if (actualOut.dismissalInfo.isNotEmpty() && actualOut.dismissalInfo != "not out") {
                        this.dismissalInfo = actualOut.dismissalInfo
                    } else if (!this.dismissalInfo.isNullOrEmpty() && this.dismissalInfo != "not out") {
                        actualOut.dismissalInfo = this.dismissalInfo!!
                    }
                }
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
            
        val activeP = if (lastP != null && isPairSame) {
            lastP
        } else if (lastP != null && lastP.runs <= 0 && lastP.balls <= 0) {
            lastP.batter1 = b1
            lastP.batter2 = b2
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
                if (isBoundary && overthrowRuns == 0) {
                    when (runs) {
                        4 -> batsman.fours++
                        6 -> batsman.sixes++
                    }
                }
                if (runs == 0) bowler.dotBalls++
                bowler.runsConceded += runs
            }
            BallType.WIDE -> {
                wides += runs
                bowler.runsConceded += runs
                bowler.widesConceded += runs
            }
            BallType.NO_BALL -> {
                val byeR = byeRuns
                val legByeR = legByeRuns
                val nbPenalty = (runs - manualBatRuns - byeR - legByeR).coerceAtLeast(0)
                noBalls += nbPenalty
                byes += byeR
                legByes += legByeR
                battingRuns += manualBatRuns
                batsman.runsScored += manualBatRuns
                batsman.ballsFaced++
                if (isBoundary && overthrowRuns == 0) {
                    when (manualBatRuns) {
                        4 -> batsman.fours++
                        6 -> batsman.sixes++
                    }
                }
                bowler.runsConceded += (manualBatRuns + nbPenalty)
                bowler.noBallsConceded += nbPenalty
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
                if (last.isBoundary && last.overthrowRuns == 0) {
                    when (last.runs) {
                        4 -> facingBatsman.fours--
                        6 -> facingBatsman.sixes--
                    }
                }
                if (last.runs == 0) bowler?.let { it.dotBalls-- }
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
                val byeR = last.byeRuns
                val legByeR = last.legByeRuns
                val nbPenalty = (last.runs - last.batRuns - byeR - legByeR).coerceAtLeast(0)
                noBalls -= nbPenalty
                byes -= byeR
                legByes -= legByeR
                battingRuns -= last.batRuns
                facingBatsman.runsScored -= last.batRuns
                facingBatsman.ballsFaced--
                if (last.isBoundary && last.overthrowRuns == 0) {
                    when (last.batRuns) {
                        4 -> facingBatsman.fours--
                        6 -> facingBatsman.sixes--
                    }
                }
                bowler?.let {
                    it.runsConceded -= (last.batRuns + nbPenalty)
                    it.noBallsConceded -= nbPenalty
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

        val retirementType = last.wicketDetail?.dismissalType
        val isRetiredHurt = retirementType == DismissalType.RETIRED_HURT ||
            (last.wicketDetail == null && last.dismissalInfo.equals("retired hurt", ignoreCase = true))
        if (last.isWicket || isRetiredHurt || retirementType == DismissalType.RETIRED_OUT ||
            last.dismissalInfo?.contains("retired", ignoreCase = true) == true) {
            if (last.isWicket && totalWickets > 0) totalWickets--
            else if (isRetiredHurt && retiredHurtCount > 0) retiredHurtCount--
            
            val actualOut = outPlayer ?: facingBatsman
            actualOut.isOut = false
            actualOut.dismissalInfo = "not out"
            actualOut.exitTime = 0
            
            // Restore entryTime to the current time so the crease clock resumes correctly.
            actualOut.entryTime = System.currentTimeMillis()
            
            if (last.isWicket) {
                val isBowlerWicket = last.wicketDetail?.dismissalType?.isBowlerWicket ?: run {
                    val info = last.dismissalInfo.orEmpty()
                    !info.contains("Run Out", ignoreCase = true) &&
                        !info.contains("Obstruct", ignoreCase = true) &&
                        !info.contains("Retired", ignoreCase = true)
                }
                if (isBowlerWicket) bowler?.let { if (it.wicketsTaken > 0) it.wicketsTaken-- }
                if (fallOfWickets.isNotEmpty()) fallOfWickets.removeAt(fallOfWickets.size - 1)
            }
        }
    }
}

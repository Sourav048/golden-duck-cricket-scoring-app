package com.goldenduck.scoring

import androidx.annotation.Keep

@Keep
class Ball {
    @JvmField
    var runs: Int = 0
    @JvmField
    var type: BallType? = null
    @JvmField
    var isWicket: Boolean = false
    @JvmField
    var batsmanName: String? = null
    @JvmField
    var bowlerName: String? = null
    @JvmField
    var fielderName: String? = null
    @JvmField
    var dismissalInfo: String? = null
    var batRuns: Int = 0
    var outPlayerName: String? = null
    var nonStrikerName: String? = null

    @JvmField
    var batsmanId: String? = null
    @JvmField
    var bowlerId: String? = null
    @JvmField
    var nonStrikerId: String? = null
    
    var wicketDetail: WicketDetail? = null

    var byeRuns: Int = 0
    var legByeRuns: Int = 0
    var overthrowRuns: Int = 0
    var isBoundary: Boolean = false

    constructor() // Required for Firestore

    constructor(
        runs: Int,
        type: BallType?,
        isWicket: Boolean,
        batsman: String?,
        bowler: String?,
        batRuns: Int,
        nonStriker: String? = null,
        byeRuns: Int = 0,
        legByeRuns: Int = 0,
        overthrowRuns: Int = 0,
        isBoundary: Boolean = (type == null || type == BallType.NORMAL || type == BallType.NO_BALL) && (batRuns == 4 || batRuns == 6) && overthrowRuns == 0 && !isWicket
    ) {
        this.runs = runs
        this.type = type
        this.isWicket = isWicket
        this.batsmanName = batsman
        this.bowlerName = bowler
        this.batRuns = batRuns
        this.nonStrikerName = nonStriker
        this.byeRuns = byeRuns
        this.legByeRuns = legByeRuns
        this.overthrowRuns = overthrowRuns
        this.isBoundary = isBoundary
    }
}

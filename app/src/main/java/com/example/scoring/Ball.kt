package com.example.scoring

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

    constructor() // Required for Firestore

    constructor(
        runs: Int,
        type: BallType?,
        isWicket: Boolean,
        batsman: String?,
        bowler: String?,
        batRuns: Int,
        nonStriker: String? = null
    ) {
        this.runs = runs
        this.type = type
        this.isWicket = isWicket
        this.batsmanName = batsman
        this.bowlerName = bowler
        this.batRuns = batRuns
        this.nonStrikerName = nonStriker
    }
}

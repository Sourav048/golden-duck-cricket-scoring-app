package com.example.scoring

import androidx.annotation.Keep

@Keep
class PartnershipEvent {
    var batter1: String? = null
    var batter2: String? = null
    var runs: Int = 0
    var balls: Int = 0
    var b1Runs: Int = 0
    var b1Balls: Int = 0
    var b2Runs: Int = 0
    var b2Balls: Int = 0

    constructor() // Required
    constructor(b1: String?, b2: String?) {
        this.batter1 = b1
        this.batter2 = b2
    }
}

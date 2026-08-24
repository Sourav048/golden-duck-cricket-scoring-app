package com.example.scoring

import androidx.annotation.Keep

@Keep
class FowEvent {
    @JvmField
    var playerName: String? = null
    @JvmField
    var scoreAtWicket: Int = 0
    @JvmField
    var wicketNumber: Int = 0
    @JvmField
    var over: String? = null

    constructor() // Required
    constructor(name: String?, score: Int, wicket: Int, over: String?) {
        this.playerName = name
        this.scoreAtWicket = score
        this.wicketNumber = wicket
        this.over = over
    }
}

package com.example.scoring

class Player {
    var id: String? = null
    var name: String? = null
    var runsScored: Int = 0
    var ballsFaced: Int = 0
    var fours: Int = 0
    var sixes: Int = 0
    var isOut: Boolean = false
    var dismissalInfo: String = "not out"
    var ballsBowled: Int = 0
    var runsConceded: Int = 0
    var wicketsTaken: Int = 0
    var maidens: Int = 0
    var widesConceded: Int = 0
    var noBallsConceded: Int = 0
    var hattricks: Int = 0
    var sixesConceded: Int = 0
    var dotBalls: Int = 0

    var minutesPlayed: Int = 0
    var entryTime: Long = 0
    var exitTime: Long = 0

    var catches: Int = 0
    var stumpings: Int = 0
    var runOuts: Int = 0
    var gullyId: String = "local"

    constructor()

    constructor(name: String?) {
        this.name = name
    }

    val oversBowledDisplay: String
        get() = "${ballsBowled / 6}.${ballsBowled % 6}"

    fun copy(): Player {
        val p = Player(name)
        p.id = id
        p.runsScored = runsScored
        p.ballsFaced = ballsFaced
        p.fours = fours
        p.sixes = sixes
        p.isOut = isOut
        p.dismissalInfo = dismissalInfo
        p.ballsBowled = ballsBowled
        p.runsConceded = runsConceded
        p.wicketsTaken = wicketsTaken
        p.maidens = maidens
        p.widesConceded = widesConceded
        p.noBallsConceded = noBallsConceded
        p.hattricks = hattricks
        p.sixesConceded = sixesConceded
        p.dotBalls = dotBalls
        p.minutesPlayed = minutesPlayed
        p.entryTime = entryTime
        p.exitTime = exitTime
        p.catches = catches
        p.stumpings = stumpings
        p.runOuts = runOuts
        p.gullyId = gullyId
        return p
    }
}

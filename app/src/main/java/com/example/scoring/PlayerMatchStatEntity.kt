package com.example.scoring

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Stores one player's stats for one match.
 */
@Keep
@Entity(tableName = "player_match_stats")
class PlayerMatchStatEntity {
    @PrimaryKey
    var id: String

    @JvmField
    var playerId: String? = null
    var matchId: String? = null
    @JvmField
    var playerName: String? = null
    var teamName: String? = null

    // Batting
    @JvmField
    var runsScored: Int = 0
    @JvmField
    var ballsFaced: Int = 0
    var fours: Int = 0
    var sixes: Int = 0
    var isOut: Boolean = false
    var dismissalInfo: String? = null
    var minutesPlayed: Int = 0
    var entryTime: Long = 0
    var exitTime: Long = 0

    // Bowling
    var ballsBowled: Int = 0
    @JvmField
    var runsConceded: Int = 0
    @JvmField
    var wicketsTaken: Int = 0
    var maidens: Int = 0
    var hattricks: Int = 0
    var sixesConceded: Int = 0
    var dotBalls: Int = 0
    var widesConceded: Int = 0
    var noBallsConceded: Int = 0

    // Fielding
    var catches: Int = 0
    var stumpings: Int = 0
    var runOuts: Int = 0
    
    // Gully Sync Field
    var gullyId: String = "local"

    init {
        this.id = UUID.randomUUID().toString()
    } // REQUIRED FOR FIREBASE

    fun toPlayer(): Player {
        val p = Player(playerName)
        p.id = playerId
        p.gullyId = gullyId
        p.runsScored = runsScored
        p.ballsFaced = ballsFaced
        p.fours = fours
        p.sixes = sixes
        p.isOut = isOut
        p.dismissalInfo = dismissalInfo ?: if (isOut) "out" else "not out"
        p.ballsBowled = ballsBowled
        p.runsConceded = runsConceded
        p.wicketsTaken = wicketsTaken
        p.maidens = maidens
        p.hattricks = hattricks
        p.sixesConceded = sixesConceded
        p.dotBalls = dotBalls
        p.widesConceded = widesConceded
        p.noBallsConceded = noBallsConceded
        p.minutesPlayed = minutesPlayed
        p.entryTime = entryTime
        p.exitTime = exitTime
        p.catches = catches
        p.stumpings = stumpings
        p.runOuts = runOuts
        return p
    }

    companion object {
        fun fromPlayer(p: Player, matchId: String, teamName: String?): PlayerMatchStatEntity {
            val s = PlayerMatchStatEntity()
            s.id = "${matchId}_${p.name}" 
            s.gullyId = p.gullyId
            s.playerId = p.id
            s.matchId = matchId
            s.playerName = p.name
            s.teamName = teamName
            s.runsScored = p.runsScored
            s.ballsFaced = p.ballsFaced
            s.fours = p.fours
            s.sixes = p.sixes
            s.isOut = p.isOut
            s.dismissalInfo = p.dismissalInfo
            s.ballsBowled = p.ballsBowled
            s.runsConceded = p.runsConceded
            s.wicketsTaken = p.wicketsTaken
            s.maidens = p.maidens
            s.hattricks = p.hattricks
            s.sixesConceded = p.sixesConceded
            s.dotBalls = p.dotBalls
            s.widesConceded = p.widesConceded
            s.noBallsConceded = p.noBallsConceded
            s.minutesPlayed = p.minutesPlayed
            s.entryTime = p.entryTime
            s.exitTime = p.exitTime
            s.catches = p.catches
            s.stumpings = p.stumpings
            s.runOuts = p.runOuts
            return s
        }
    }
}

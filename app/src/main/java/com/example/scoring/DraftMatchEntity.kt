package com.example.scoring

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "draft_matches")
class DraftMatchEntity {
    @PrimaryKey
    var id: String

    var teamAName: String? = null
    var teamBName: String? = null
    var overs: Int = 0
    var ballType: String? = null
    var ruleRunsOnWide: Boolean = false
    var ruleFreeHit: Boolean = false
    var ruleRunsOnBye: Boolean = false
    var ruleOverthrow: Boolean = false
    var ruleEveryPlayerBats: Boolean = false

    var teamANames: List<String?>? = null
    var teamBNames: List<String?>? = null
    var teamAPhotos: List<String?>? = null
    var teamBPhotos: List<String?>? = null
    var teamAIds: List<String?>? = null
    var teamBIds: List<String?>? = null

    init {
        this.id = UUID.randomUUID().toString()
    }
}

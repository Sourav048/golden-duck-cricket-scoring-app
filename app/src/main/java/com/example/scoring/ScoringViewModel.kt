package com.example.scoring

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ScoringViewModel : ViewModel() {
    val match = MutableLiveData<Match?>()
    val striker = MutableLiveData<Player?>()
    val nonStriker = MutableLiveData<Player?>()
    val bowler = MutableLiveData<Player?>()
    val commentary = MutableLiveData<MutableList<CommentaryEntry?>?>()
    val overBalls = MutableLiveData<MutableList<String?>?>()
    
    // Resumption state for Config Changes
    var nextBatsmanIdx: Int = 0
    var teamABatting: Boolean = false
    var isFreeHitActive: Boolean = false
    var currentBowlerInSpell: String? = null
    var overRuns: Int = 0
    var overBowlerRuns: Int = 0
    var overWickets: Int = 0
    var teamANames: ArrayList<String?>? = null
    var teamBNames: ArrayList<String?>? = null
    var nameToIdMap: MutableMap<String?, String?> = mutableMapOf()
    var photoMap: MutableMap<String?, String?> = mutableMapOf()
    var isFinished: Boolean = false
    var isAbandoned: Boolean = false
    var isScorer: Boolean = true
    var playerStatCache: MutableMap<String?, Player> = mutableMapOf()

    fun update(
        m: Match?,
        s: Player?,
        ns: Player?,
        b: Player?,
        c: MutableList<CommentaryEntry?>?,
        ob: MutableList<String?>?
    ) {
        match.value = m
        striker.value = s
        nonStriker.value = ns
        bowler.value = b
        commentary.value = c
        overBalls.value = ob
    }
    
    fun updateScore(runs: Int, wickets: Int) {
        // Trigger an update on the match object to notify observers
        match.value = match.value 
    }
    
    fun updateCommentary(list: MutableList<CommentaryEntry?>?) {
        commentary.value = list
    }

    fun updateOverBalls(list: MutableList<String?>?) {
        overBalls.value = list
    }
}

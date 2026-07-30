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

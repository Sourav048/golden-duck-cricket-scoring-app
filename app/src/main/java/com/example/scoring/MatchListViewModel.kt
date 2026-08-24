package com.example.scoring

import android.app.Application
import androidx.lifecycle.*

class MatchListViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val gId = MutableLiveData<String>()
    private val filterType = MutableLiveData<Int>()
    
    private val combinedParams = MediatorLiveData<Pair<String, Int>>().apply {
        addSource(gId) { id -> value = Pair(id ?: "local", filterType.value ?: 0) }
        addSource(filterType) { type -> value = Pair(gId.value ?: "local", type ?: 0) }
    }

    fun setParams(gullyId: String, type: Int) {
        if (gId.value != gullyId) gId.value = gullyId
        if (filterType.value != type) filterType.value = type
    }

    val matches: LiveData<List<MatchEntity>> = combinedParams.switchMap { p ->
        val currentGId = p.first
        val type = p.second
        
        when (type) {
            0 -> db.matchDao().getMatchesByStatusByGullyLive(false, false, currentGId).map { list ->
                val now = System.currentTimeMillis()
                list?.filterNotNull()?.filter { 
                    val diff = now - it.lastScorerPulse
                    // Allow 1 min future skew to handle slight clock differences
                    it.isLive && diff in -60000..120000 
                } ?: emptyList()
            }
            1 -> db.matchDao().getMatchesByStatusByGullyLive(true, false, currentGId).map { list ->
                list?.filterNotNull() ?: emptyList()
            }
            2 -> {
                val liveMatches = db.matchDao().getMatchesByStatusByGullyLive(false, false, currentGId)
                val liveDrafts = db.draftDao().getAllDraftsLive(currentGId)
                
                MediatorLiveData<List<MatchEntity>>().apply {
                    fun combine() {
                        val activeMatches = liveMatches.value ?: emptyList()
                        val drafts = liveDrafts.value ?: emptyList()
                        val combined = mutableListOf<MatchEntity>()
                        val now = System.currentTimeMillis()
                        
                        // Add matches that are NOT live OR are STALE (pulse dead or from future)
                        activeMatches.filterNotNull().forEach { m ->
                            val diff = now - m.lastScorerPulse
                            val isPulseDead = diff !in -60000..120000 
                            if (!m.isLive || isPulseDead) {
                                // Create a shallow copy to modify the UI flag without affecting DB
                                val displayMatch = MatchEntity().apply {
                                    // Copy all relevant fields for the adapter
                                    this.id = m.id
                                    this.teamAName = m.teamAName
                                    this.teamBName = m.teamBName
                                    this.venue = m.venue
                                    this.totalOvers = m.totalOvers
                                    this.playedAt = m.playedAt
                                    this.firstInningsStartTime = m.firstInningsStartTime
                                    this.firstInningsTeam = m.firstInningsTeam
                                    this.firstInningsRuns = m.firstInningsRuns
                                    this.firstInningsWickets = m.firstInningsWickets
                                    this.secondInningsTeam = m.secondInningsTeam
                                    this.secondInningsRuns = m.secondInningsRuns
                                    this.secondInningsWickets = m.secondInningsWickets
                                    this.isFinished = m.isFinished
                                    this.isAbandoned = m.isAbandoned
                                    this.isLive = if (isPulseDead) false else m.isLive // FORCE FALSE IF PULSE DEAD
                                    this.lastScorerPulse = m.lastScorerPulse
                                    this.result = m.result
                                    this.revisedOvers = m.revisedOvers
                                    this.ballsJson1 = m.ballsJson1
                                    this.ballsJson2 = m.ballsJson2
                                    this.startNotificationSent = m.startNotificationSent
                                }
                                combined.add(displayMatch)
                            }
                        }
                        
                        // Add drafts that don't have an active match yet
                        drafts.filterNotNull().forEach { d ->
                            if (activeMatches.none { it?.id == d.id }) {
                                combined.add(MatchEntity().apply {
                                    this.id = d.id
                                    this.teamAName = d.teamAName
                                    this.teamBName = d.teamBName
                                    this.venue = d.venue
                                    this.totalOvers = d.overs
                                    this.isFinished = false
                                    this.gullyId = d.gullyId
                                    this.teamANames = d.teamANames
                                    this.teamBNames = d.teamBNames
                                    this.playedAt = System.currentTimeMillis()
                                    this.startNotificationSent = true // Drafts don't need notification yet
                                })
                            }
                        }
                        value = combined
                    }
                    addSource(liveMatches) { combine() }
                    addSource(liveDrafts) { combine() }
                }
            }
            3 -> db.matchDao().getAbandonedMatchesByGullyLive(currentGId).map { list ->
                list?.filterNotNull() ?: emptyList()
            }
            else -> MutableLiveData(emptyList())
        }
    }
}

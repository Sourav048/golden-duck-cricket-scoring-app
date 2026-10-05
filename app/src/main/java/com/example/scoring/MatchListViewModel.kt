package com.example.scoring

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.*

class MatchListViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val gId = MutableLiveData<String>()
    private val filterType = MutableLiveData<Int>()
    
    // 15-second heartbeat ticker to trigger pulse re-evaluations automatically
    private val pulseTicker = MutableLiveData<Long>(System.currentTimeMillis())
    private val pulseHandler = Handler(Looper.getMainLooper())
    private val pulseRunnable = object : Runnable {
        override fun run() {
            pulseTicker.value = System.currentTimeMillis()
            pulseHandler.postDelayed(this, 15_000)
        }
    }

    init {
        pulseHandler.postDelayed(pulseRunnable, 15_000)
    }

    override fun onCleared() {
        super.onCleared()
        pulseHandler.removeCallbacks(pulseRunnable)
    }

    private val combinedParams = MediatorLiveData<Pair<String, Int>>().apply {
        addSource(gId) { id -> value = Pair(id ?: "local", filterType.value ?: 0) }
        addSource(filterType) { type -> value = Pair(gId.value ?: "local", type ?: 0) }
    }

    fun setParams(gullyId: String, type: Int) {
        if (gId.value != gullyId) gId.value = gullyId
        if (filterType.value != type) filterType.value = type
    }

    private fun getMatchTime(m: MatchEntity): Long {
        return if (m.firstInningsStartTime > 0) m.firstInningsStartTime else m.playedAt
    }

    val matches: LiveData<List<MatchEntity>> = combinedParams.switchMap { p ->
        val currentGId = p.first
        val type = p.second
        
        when (type) {
            0 -> {
                val liveMatches = db.matchDao().getMatchesByStatusByGullyLive(false, false, currentGId)
                MediatorLiveData<List<MatchEntity>>().apply {
                    fun evaluate() {
                        val list = liveMatches.value ?: emptyList()
                        val now = System.currentTimeMillis()
                        value = list.filterNotNull()
                            .filter { it.isMatchLive(now) }
                            .sortedByDescending { getMatchTime(it) }
                    }
                    addSource(liveMatches) { evaluate() }
                    addSource(pulseTicker) { evaluate() }
                }
            }
            1 -> db.matchDao().getMatchesByStatusByGullyLive(true, false, currentGId).map { list ->
                list?.filterNotNull()?.sortedByDescending { getMatchTime(it) } ?: emptyList()
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
                        // Uses complete clone to preserve squad names, photos, IDs, and custom rules
                        activeMatches.filterNotNull().forEach { m ->
                            if (!m.isMatchLive(now)) {
                                combined.add(m.cloneForDisplay(isLiveOverride = false))
                            }
                        }

                        // Pre-index active match IDs for O(N + M) deduplication
                        val activeIds = activeMatches.mapNotNull { it?.id }.toSet()

                        // Add drafts that don't have an active match yet
                        drafts.filterNotNull().forEach { d ->
                            if (d.id !in activeIds) {
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
                                    this.playedAt = if (d.lastSyncedAt > 0) d.lastSyncedAt else System.currentTimeMillis()
                                    this.startNotificationSent = true // Drafts don't need notification yet
                                })
                            }
                        }
                        // Sort in background thread
                        value = combined.sortedByDescending { getMatchTime(it) }
                    }
                    addSource(liveMatches) { combine() }
                    addSource(liveDrafts) { combine() }
                    addSource(pulseTicker) { combine() }
                }
            }
            3 -> db.matchDao().getAbandonedMatchesByGullyLive(currentGId).map { list ->
                list?.filterNotNull()?.sortedByDescending { getMatchTime(it) } ?: emptyList()
            }
            else -> MutableLiveData(emptyList())
        }
    }
}
